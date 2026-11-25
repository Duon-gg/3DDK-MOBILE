-- Airtable-style base invite links: the owner creates a link with a role; anyone signed in who opens it joins the base.
-- Routes: POST/DELETE /bases/{bid}/invite-link, GET /invite-links/{token}, POST /invite-links/{token}/accept.
-- GET /bases/{bid}/sharing also returns the active link. Wraps the current workspace_api; all other routes are unchanged.
create table public.ws_base_invite_links(
 id uuid primary key default gen_random_uuid(),
 base_id uuid not null references public.ws_bases(id),
 token text not null unique,
 role text not null check(role in ('EDITOR','VIEWER')),
 created_by text not null references public.ws_profiles(uid),
 created_at timestamptz not null default now(),
 revoked_at timestamptz);
create unique index ws_base_invite_link_active on public.ws_base_invite_links(base_id) where revoked_at is null;
alter table public.ws_base_invite_links enable row level security;
revoke all on table public.ws_base_invite_links from public,anon,authenticated;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_fields;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');bid uuid;link public.ws_base_invite_links;result jsonb;role_name text;
begin
 if not((parts[1]='bases' and parts[3]='invite-link' and cardinality(parts)=3 and p_method in ('POST','DELETE'))
  or (parts[1]='invite-links' and ((cardinality(parts)=2 and p_method='GET') or (cardinality(parts)=3 and parts[3]='accept' and p_method='POST')))) then
  result:=public.workspace_api_fields(p_uid,p_email,p_name,p_method,p_path,p_body);
  if parts[1]='bases' and parts[3]='sharing' and cardinality(parts)=3 and p_method='GET' then
   select * into link from public.ws_base_invite_links where base_id=parts[2]::uuid and revoked_at is null;
   result:=result||jsonb_build_object('inviteLink',case when link.id is null then null else jsonb_build_object('token',link.token,'role',link.role) end,'people',public.ws_base_people(parts[2]::uuid));
  elsif p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,inviteLinks}','true');
  end if;
  return result;
 end if;
 -- Same auth gate and profile upsert as every other route.
 perform public.workspace_api_fields(p_uid,p_email,p_name,'GET','/me','{}'::jsonb);

 if parts[1]='bases' then
  bid:=parts[2]::uuid;
  if not exists(select 1 from public.ws_bases where id=bid and deleted_at is null) then raise exception 'NOT_FOUND';end if;
  if public.ws_base_role(bid,p_uid) is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
  update public.ws_base_invite_links set revoked_at=now() where base_id=bid and revoked_at is null;
  if p_method='DELETE' then return jsonb_build_object('inviteLink',null);end if;
  if coalesce(p_body->>'role','') not in ('EDITOR','VIEWER') then raise exception 'VALIDATION';end if;
  -- 128 random bits; a new link always replaces the old one, like Airtable's "regenerate".
  insert into public.ws_base_invite_links(base_id,token,role,created_by) values(bid,replace(gen_random_uuid()::text,'-',''),p_body->>'role',p_uid) returning * into link;
  return jsonb_build_object('inviteLink',jsonb_build_object('token',link.token,'role',link.role));
 end if;

 select l.* into link from public.ws_base_invite_links l join public.ws_bases b on b.id=l.base_id where l.token=parts[2] and l.revoked_at is null and b.deleted_at is null;
 if not found then raise exception 'NOT_FOUND';end if;
 role_name:=public.ws_base_role(link.base_id,p_uid);
 if p_method='GET' then
  return jsonb_build_object('baseId',link.base_id,'baseName',(select name from public.ws_bases where id=link.base_id),'role',link.role,'currentRole',role_name);
 end if;
 -- Joining never lowers access someone already has.
 if role_name is null or (role_name='VIEWER' and link.role='EDITOR') then
  insert into public.ws_base_members(base_id,uid,role) values(link.base_id,p_uid,link.role)
   on conflict(base_id,uid) do update set role=case when ws_base_members.role='EDITOR' then 'EDITOR' else excluded.role end;
  update public.ws_bases set version=version+1 where id=link.base_id;
 end if;
 return jsonb_build_object('id',link.base_id,'name',(select name from public.ws_bases where id=link.base_id),'role',public.ws_base_role(link.base_id,p_uid));
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_fields(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
