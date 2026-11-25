-- Airtable-style comment edit/delete: the author edits their own comment; the author or a base owner deletes it.
-- Routes: PATCH/DELETE /tables/{tid}/records/{rid}/comments/{cid}. Comment lists gain editedAt.
-- Wraps the current workspace_api; all other routes are unchanged.
alter table public.ws_comments add column edited_at timestamptz;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_links;

-- Adds editedAt to a comment list built by the inner API, keeping its order.
create function public.ws_comments_with_edits(p jsonb) returns jsonb language sql stable set search_path=public as $$
 select jsonb_build_object('comments',coalesce((select jsonb_agg(e.c||jsonb_build_object('editedAt',x.edited_at) order by e.n)
  from jsonb_array_elements(p->'comments') with ordinality e(c,n) left join public.ws_comments x on x.id=(e.c->>'id')::uuid),'[]'))
$$;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');list_path text;result jsonb;bid uuid;cid uuid;author text;new_mentions jsonb;
begin
 if not(parts[1]='tables' and parts[3]='records' and parts[5]='comments' and cardinality(parts)=6 and p_method in ('PATCH','DELETE')) then
  result:=public.workspace_api_links(p_uid,p_email,p_name,p_method,p_path,p_body);
  if parts[1]='tables' and parts[3]='records' and parts[5]='comments' and cardinality(parts)=5 then return public.ws_comments_with_edits(result);end if;
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,commentEdit}','true');end if;
  return result;
 end if;
 -- The list route does the auth, membership and record checks.
 list_path:='/'||array_to_string(parts[1:5],'/');
 perform public.workspace_api_links(p_uid,p_email,p_name,'GET',list_path,'{}'::jsonb);
 if jsonb_typeof(p_body)<>'object' then raise exception 'VALIDATION';end if;
 cid:=parts[6]::uuid;
 select t.base_id into bid from public.ws_tables t where t.id=parts[2]::uuid;
 select uid into author from public.ws_comments where id=cid and record_id=parts[4]::uuid for update;
 if author is null then raise exception 'NOT_FOUND';end if;
 if p_method='DELETE' then
  if author<>p_uid and public.ws_base_role(bid,p_uid) is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
  delete from public.ws_comments where id=cid;
 else
  if author<>p_uid then raise exception 'FORBIDDEN';end if;
  new_mentions:=coalesce(p_body->'mentions','[]');
  if jsonb_typeof(new_mentions)<>'array' or jsonb_array_length(new_mentions)>20
   or exists(select 1 from jsonb_array_elements(new_mentions) x where jsonb_typeof(x)<>'string' or public.ws_base_role(bid,x#>>'{}') is null)
   or length(trim(coalesce(p_body->>'body',''))) not between 1 and 2000 then raise exception 'VALIDATION';end if;
  update public.ws_comments set body=trim(p_body->>'body'),mentions=new_mentions,edited_at=now() where id=cid;
 end if;
 return public.ws_comments_with_edits(public.workspace_api_links(p_uid,p_email,p_name,'GET',list_path,'{}'::jsonb));
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_links(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_comments_with_edits(jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
