-- Workspace "ADMIN" (Quản trị không gian): same rights as the workspace owner.
-- Stored as an OWNER row with is_admin=true, so every owner check in the core already lets admins through;
-- the original owner keeps is_admin=false and cannot be demoted or removed by an admin. Responses show ADMIN.
--  PATCH  /workspaces/{wid}/members/{uid} {role:"ADMIN"|"EDITOR"|"VIEWER", expectedVersion}
--  DELETE /workspaces/{wid}/members/{uid} {expectedVersion}   (also works on admins)
alter table public.ws_members add column is_admin boolean not null default false;
-- Still exactly one real owner per workspace; admins are extra OWNER rows.
drop index public.ws_one_owner;
create unique index ws_one_owner on public.ws_members(workspace_id) where role='OWNER' and not is_admin;

create or replace function public.ws_base_people(p_base uuid) returns jsonb language sql stable as $$
 select coalesce(jsonb_agg(jsonb_build_object('uid',p.uid,'displayName',p.display_name,'avatarUrl',p.avatar_url,
  'role',case when public.ws_base_role(p_base,p.uid)='OWNER'
   and (exists(select 1 from public.ws_base_members bm where bm.base_id=p_base and bm.uid=p.uid and bm.role='ADMIN')
     or exists(select 1 from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base and m.uid=p.uid and m.is_admin))
   and not exists(select 1 from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base and m.uid=p.uid and m.role='OWNER' and not m.is_admin)
   then 'ADMIN' else public.ws_base_role(p_base,p.uid) end)
  order by p.display_name,p.uid),'[]')
 from public.ws_profiles p where p.uid in (
  select m.uid from public.ws_members m join public.ws_bases b on b.workspace_id=m.workspace_id where b.id=p_base union select uid from public.ws_base_members where base_id=p_base)
$$;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_trash;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;wid uuid;target public.ws_members;caller public.ws_members;new_role text;
begin
 if parts[1]='workspaces' and parts[3]='members' and cardinality(parts)=4 and p_method in ('PATCH','DELETE') then
  wid:=parts[2]::uuid;new_role:=p_body->>'role';
  select * into target from public.ws_members where workspace_id=wid and uid=parts[4];
  if p_method='PATCH' and new_role='ADMIN' and found and not target.is_admin then
   -- Promote: the core checks owner rights, version and writes the audit as an EDITOR change; then raise it.
   result:=public.workspace_api_trash(p_uid,p_email,p_name,'PATCH',p_path,jsonb_set(p_body,'{role}','"EDITOR"'));
   update public.ws_members set role='OWNER',is_admin=true where workspace_id=wid and uid=parts[4];
   return result||jsonb_build_object('role','ADMIN');
  elsif found and target.is_admin and (p_method='DELETE' or new_role in ('EDITOR','VIEWER')) then
   -- Demote or remove an admin (the core refuses to touch OWNER rows).
   perform public.workspace_api_trash(p_uid,p_email,p_name,'GET','/workspaces/'||wid,'{}'::jsonb);
   select * into caller from public.ws_members where workspace_id=wid and uid=p_uid;
   if caller.role is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
   if target.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
   if p_method='DELETE' then delete from public.ws_members where workspace_id=wid and uid=target.uid;
   else update public.ws_members set role=new_role,is_admin=false,version=version+1 where workspace_id=wid and uid=target.uid;end if;
   insert into public.ws_events(workspace_id,actor_uid,path,method,entity_type,entity_id,action,before_version,after_version,changes)
    values(wid,p_uid,p_path,p_method,'MEMBER',target.uid,p_method,target.version,target.version+1,
     jsonb_build_object('role',jsonb_build_object('before','ADMIN','after',coalesce(new_role,'REMOVED'))));
   return jsonb_build_object('uid',target.uid,'role',coalesce(new_role,'REMOVED'),'version',target.version+1);
  elsif p_method='PATCH' and new_role='ADMIN' and found then
   return jsonb_build_object('uid',target.uid,'role','ADMIN','version',target.version);
  end if;
 end if;
 result:=public.workspace_api_trash(p_uid,p_email,p_name,p_method,p_path,p_body);
 -- Show admins as ADMIN in workspace member lists.
 if parts[1]='workspaces' and cardinality(parts)=2 and p_method='GET' and jsonb_typeof(result->'members')='array' then
  result:=jsonb_set(result,'{members}',coalesce((select jsonb_agg(case when exists(select 1 from public.ws_members m where m.workspace_id=parts[2]::uuid and m.uid=x->>'uid' and m.is_admin)
   then jsonb_set(x,'{role}','"ADMIN"') else x end) from jsonb_array_elements(result->'members') x),'[]'));
 end if;
 if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,workspaceAdmin}','true');end if;
 return result;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_trash(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
