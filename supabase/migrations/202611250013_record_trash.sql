-- Row trash, like Airtable's record trash: deleting a row only archives it.
--  GET  /tables/{tid}/records/trash             -> the 100 most recently deleted rows of the table
--  POST /tables/{tid}/records/restore {ids}     -> brings rows back (max 100, row limit still applies)
--  POST /tables/{tid}/records/delete  {ids}     -> deletes several rows at once (one write, max 100)
-- Restore and bulk delete need edit rights; both are idempotent and write history events.
alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_admin;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');tid uuid;bid uuid;wid uuid;role_name text;ids text[];n integer;rec public.ws_records;
begin
 if not(parts[1]='tables' and parts[3]='records' and cardinality(parts)=4 and ((parts[4]='trash' and p_method='GET') or (parts[4] in ('restore','delete') and p_method='POST'))) then
  return public.workspace_api_admin(p_uid,p_email,p_name,p_method,p_path,p_body);
 end if;
 -- Auth, membership and table existence come from the normal metadata read.
 perform public.workspace_api_admin(p_uid,p_email,p_name,'GET','/tables/'||parts[2]||'/metadata','{}'::jsonb);
 tid:=parts[2]::uuid;
 select t.base_id,b.workspace_id into bid,wid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;
 role_name:=public.ws_base_role(bid,p_uid);
 if parts[4]='trash' then
  return jsonb_build_object('items',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'cells',r.cells,'version',r.version,'deletedAt',r.archived_at) order by r.archived_at desc)
   from (select * from public.ws_records where table_id=tid and archived_at is not null order by archived_at desc limit 100) r),'[]'));
 end if;
 if role_name is null or role_name not in ('OWNER','EDITOR') then raise exception 'FORBIDDEN';end if;
 if jsonb_typeof(p_body->'ids')<>'array' or jsonb_array_length(p_body->'ids') not between 1 and 100 then raise exception 'VALIDATION';end if;
 select array_agg(x) into ids from jsonb_array_elements_text(p_body->'ids') x;
 perform pg_advisory_xact_lock(hashtextextended(wid::text,41005));
 n:=0;
 for rec in select * from public.ws_records where table_id=tid and id::text=any(ids)
  and (case when parts[4]='delete' then archived_at is null else archived_at is not null end) order by id for update loop
  if parts[4]='restore' and (select count(*) from public.ws_records where table_id=tid and archived_at is null)>=500 then raise exception 'VALIDATION';end if;
  update public.ws_records set archived_at=case when parts[4]='delete' then now() end,version=version+1,updated_at=now() where id=rec.id;
  insert into public.ws_events(workspace_id,actor_uid,path,method,entity_type,entity_id,action,before_version,after_version,changes)
   values(wid,p_uid,'/tables/'||tid||'/records/'||rec.id,case when parts[4]='delete' then 'DELETE' else 'POST' end,'RECORD',rec.id::text,case when parts[4]='delete' then 'ARCHIVED' else 'RESTORED' end,rec.version,rec.version+1,'{}'::jsonb);
  n:=n+1;
 end loop;
 if n>0 then update public.ws_tables set version=version+1 where id=tid;end if;
 return jsonb_build_object('changed',n);
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_admin(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
