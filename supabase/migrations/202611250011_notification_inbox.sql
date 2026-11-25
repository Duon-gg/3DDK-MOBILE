-- In-app notification inbox, like Airtable's bell: GET /me/notifications lists the latest mentions with read state,
-- POST /me/notifications/read marks some ({ids:[...]}) or all ({all:true}) as read.
-- Push delivery (sent_at) and reading (read_at) are separate: a push can be missed, the inbox keeps it.
alter table public.ws_notifications add column read_at timestamptz;
create index ws_notifications_inbox on public.ws_notifications(uid,created_at desc);

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_marks;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;
begin
 if not(parts[1]='me' and parts[2]='notifications' and ((cardinality(parts)=2 and p_method='GET') or (cardinality(parts)=3 and parts[3]='read' and p_method='POST'))) then
  result:=public.workspace_api_marks(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,notificationInbox}','true');end if;
  return result;
 end if;
 perform public.workspace_api_marks(p_uid,p_email,p_name,'GET','/me','{}'::jsonb);
 if p_method='POST' then
  if jsonb_typeof(p_body)<>'object' then raise exception 'VALIDATION';end if;
  if p_body->>'all'='true' then update public.ws_notifications set read_at=now() where uid=p_uid and read_at is null;
  elsif jsonb_typeof(p_body->'ids')='array' and jsonb_array_length(p_body->'ids') between 1 and 100 then
   update public.ws_notifications set read_at=now() where uid=p_uid and read_at is null
    and id::text in (select jsonb_array_elements_text(p_body->'ids'));
  else raise exception 'VALIDATION';end if;
 end if;
 -- Only rows whose base the user can still open; the newest 50.
 return jsonb_build_object(
  'unread',(select count(*) from public.ws_notifications n join public.ws_tables t on t.id=n.table_id where n.uid=p_uid and n.read_at is null and public.ws_base_role(t.base_id,p_uid) is not null),
  'items',coalesce((select jsonb_agg(x order by x->>'createdAt' desc) from (
   select jsonb_build_object('id',n.id,'tableId',n.table_id,'recordId',n.record_id,'title',n.title,'body',n.body,'createdAt',n.created_at,'read',n.read_at is not null,'tableName',t.name) x
   from public.ws_notifications n join public.ws_tables t on t.id=n.table_id
   where n.uid=p_uid and public.ws_base_role(t.base_id,p_uid) is not null order by n.created_at desc limit 50) q),'[]'));
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_marks(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
