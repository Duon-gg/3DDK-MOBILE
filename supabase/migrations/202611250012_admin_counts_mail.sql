-- 1. Base role "ADMIN" (Quản trị): same rights as the owner on that base. ws_base_role reports it as OWNER, so every
--    owner check already lets admins through; the people list and member list still show ADMIN.
--    It is granted by promoting a member (PATCH .../members/{uid} {role:"ADMIN"}) or by an email invite with role ADMIN.
-- 2. POST /tables/{tid}/records/counts {fieldId, filter?, search?, viewId?}: row counts per cell value over the whole
--    table, so group headers and Kanban columns are not limited to the loaded page.
-- 3. A base invite returns "mail" (recipient, base, inviter) so the API function can send the invitation email.
alter table public.ws_base_members drop constraint ws_base_members_role_check;
alter table public.ws_base_members add constraint ws_base_members_role_check check(role in ('ADMIN','EDITOR','VIEWER'));
alter table public.ws_base_invites drop constraint ws_base_invites_role_check;
alter table public.ws_base_invites add constraint ws_base_invites_role_check check(role in ('ADMIN','EDITOR','VIEWER'));

create or replace function public.ws_base_role(p_base uuid,p_uid text) returns text language sql stable as $$
 select case max(rank) when 3 then 'OWNER' when 2 then 'EDITOR' when 1 then 'VIEWER' end from (
 select case m.role when 'OWNER' then 3 when 'EDITOR' then 2 else 1 end rank from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base and m.uid=p_uid
 union all select case role when 'ADMIN' then 3 when 'EDITOR' then 2 else 1 end from public.ws_base_members where base_id=p_base and uid=p_uid) roles
$$;

-- Shown role: ADMIN for a base admin who is not the workspace owner.
create or replace function public.ws_base_people(p_base uuid) returns jsonb language sql stable as $$
 select coalesce(jsonb_agg(jsonb_build_object('uid',p.uid,'displayName',p.display_name,'avatarUrl',p.avatar_url,
  'role',case when public.ws_base_role(p_base,p.uid)='OWNER' and exists(select 1 from public.ws_base_members bm where bm.base_id=p_base and bm.uid=p.uid and bm.role='ADMIN')
   and not exists(select 1 from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base and m.uid=p.uid and m.role='OWNER') then 'ADMIN' else public.ws_base_role(p_base,p.uid) end)
  order by p.display_name,p.uid),'[]')
 from public.ws_profiles p where p.uid in (
  select m.uid from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base union select uid from public.ws_base_members where base_id=p_base)
$$;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_inbox;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;body jsonb:=p_body;admin boolean:=false;
 tid uuid;f public.ws_fields;filters jsonb;where_sql text;expr text;invite public.ws_base_invites;
begin
 -- Counts per value for one field (group by / Kanban), using the same filter and search as the row query.
 if parts[1]='tables' and parts[3]='records' and parts[4]='counts' and cardinality(parts)=4 and p_method='POST' then
  perform public.workspace_api_inbox(p_uid,p_email,p_name,'POST','/tables/'||parts[2]||'/records/query',(p_body-'fieldId')||jsonb_build_object('limit',1));
  tid:=parts[2]::uuid;
  select * into f from public.ws_fields where table_id=tid and id::text=p_body->>'fieldId';if not found then raise exception 'VALIDATION';end if;
  filters:=coalesce(p_body->'filter',(select v.filter from public.ws_views v where v.id=(p_body->>'viewId')::uuid and v.table_id=tid),'{"op":"and","conditions":[]}');
  where_sql:=public.ws_p0_filter(tid,filters,p_uid);
  if coalesce(p_body->>'search','')<>'' then where_sql:=where_sql||' and '||public.ws_search_sql(p_body->>'search');end if;
  expr:=format('coalesce(r.cells->%L,''null''::jsonb)',f.id::text);
  execute format('select coalesce(jsonb_agg(jsonb_build_object(''value'',v,''count'',n)),''[]'') from (select %s v,count(*) n from public.ws_records r where r.table_id=%L::uuid and r.archived_at is null and (%s) group by 1) q',expr,tid,where_sql) into result;
  return jsonb_build_object('fieldId',f.id,'counts',result);
 end if;
 -- ADMIN goes through the core as EDITOR (auth, owner check, version, audit), then the stored role is raised.
 if parts[1]='bases' and parts[3] in ('members','invites') and p_body->>'role'='ADMIN' and p_method in ('PATCH','POST') then
  admin:=true;body:=jsonb_set(p_body,'{role}','"EDITOR"');
 end if;
 result:=public.workspace_api_inbox(p_uid,p_email,p_name,p_method,p_path,body);
 if admin and parts[3]='members' and cardinality(parts)=4 then
  update public.ws_base_members set role='ADMIN' where base_id=parts[2]::uuid and uid=parts[4];
 elsif admin and parts[3]='invites' and cardinality(parts)=3 then
  update public.ws_base_invites set role='ADMIN' where id=(result->>'id')::uuid;
 end if;
 if parts[1]='bases' and parts[3]='invites' and cardinality(parts)=3 and p_method='POST' then
  result:=result||jsonb_build_object('mail',jsonb_build_object('to',lower(trim(p_body->>'email')),'role',coalesce(p_body->>'role','EDITOR'),
   'base',(select name from public.ws_bases where id=parts[2]::uuid),'inviter',(select coalesce(nullif(display_name,''),email) from public.ws_profiles where uid=p_uid)));
 end if;
 -- An ADMIN email invite accepted by someone who already edits the base still makes them admin.
 if parts[1]='base-invitations' and parts[3]='accept' and cardinality(parts)=3 and p_method='POST' then
  select * into invite from public.ws_base_invites where id=parts[2]::uuid;
  if invite.role='ADMIN' then update public.ws_base_members set role='ADMIN' where base_id=invite.base_id and uid=p_uid;end if;
 end if;
 if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(jsonb_set(jsonb_set(result,'{features,adminRole}','true'),'{features,groupCounts}','true'),'{features,httpsInviteLinks}','true');result:=jsonb_set(result,'{features,recordTrash}','true');end if;
 return result;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_inbox(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_base_role(uuid,text),public.ws_base_people(uuid) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
