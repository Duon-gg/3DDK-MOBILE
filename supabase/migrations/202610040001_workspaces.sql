-- Additive v2 schema. Legacy task tables and v1 API remain available.
create table public.ws_profiles(uid text primary key, email text not null, display_name text not null, avatar_url text not null default 'https://api.dicebear.com/9.x/shapes/png?seed=1');
create table public.ws_workspaces(id uuid primary key default gen_random_uuid(),name text not null check(length(trim(name)) between 1 and 100),created_at timestamptz not null default now());
create table public.ws_members(workspace_id uuid references public.ws_workspaces(id),uid text references public.ws_profiles(uid),role text not null check(role in ('OWNER','EDITOR','VIEWER')),primary key(workspace_id,uid));
create unique index ws_one_owner on public.ws_members(workspace_id) where role='OWNER';
create index ws_member_uid on public.ws_members(uid);
create table public.ws_invites(id uuid primary key default gen_random_uuid(),workspace_id uuid not null references public.ws_workspaces(id),email text not null,role text not null check(role in ('EDITOR','VIEWER')),expires_at timestamptz not null default now()+interval '7 days',accepted_at timestamptz,revoked_at timestamptz);
create index ws_invite_email on public.ws_invites(email);
create table public.ws_bases(id uuid primary key default gen_random_uuid(),workspace_id uuid not null references public.ws_workspaces(id),name text not null check(length(trim(name)) between 1 and 100));
create index ws_base_workspace on public.ws_bases(workspace_id);
create table public.ws_tables(id uuid primary key default gen_random_uuid(),base_id uuid not null references public.ws_bases(id),name text not null check(length(trim(name)) between 1 and 100));
create index ws_table_base on public.ws_tables(base_id);
create table public.ws_fields(id uuid primary key default gen_random_uuid(),table_id uuid not null references public.ws_tables(id),name text not null check(length(trim(name)) between 1 and 100),type text not null check(type in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT')),options jsonb not null default '[]',position integer not null,unique(table_id,name));
create table public.ws_records(id uuid primary key default gen_random_uuid(),table_id uuid not null references public.ws_tables(id),cells jsonb not null default '{}',version integer not null default 1,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),archived_at timestamptz);
create index ws_records_table on public.ws_records(table_id,created_at,id) where archived_at is null;
create table public.ws_events(id uuid primary key default gen_random_uuid(),workspace_id uuid not null references public.ws_workspaces(id),actor_uid text not null references public.ws_profiles(uid),path text not null,method text not null,created_at timestamptz not null default now());
create table public.ws_requests(uid text references public.ws_profiles(uid),request_id uuid,method text not null,path text not null,body jsonb not null,response jsonb not null,primary key(uid,request_id));
create table public.ws_limits(uid text primary key references public.ws_profiles(uid),window_at timestamptz not null default now(),count integer not null default 1);

create function public.ws_validate_cells(p_table uuid,p_cells jsonb) returns void language plpgsql set search_path=public,pg_temp as $$
declare k text; v jsonb; f public.ws_fields;
begin
 if jsonb_typeof(p_cells) is distinct from 'object' or pg_column_size(p_cells)>24000 then raise exception 'VALIDATION'; end if;
 for k,v in select * from jsonb_each(p_cells) loop
  select * into f from public.ws_fields where table_id=p_table and id::text=k;
  if not found then raise exception 'VALIDATION'; end if;
  if v='null'::jsonb then continue; end if;
  if f.type='TEXT' and (jsonb_typeof(v)<>'string' or length(v#>>'{}')>5000) then raise exception 'VALIDATION'; end if;
  if f.type='NUMBER' and jsonb_typeof(v)<>'number' then raise exception 'VALIDATION'; end if;
  if f.type='CHECKBOX' and jsonb_typeof(v)<>'boolean' then raise exception 'VALIDATION'; end if;
  if f.type='DATE' then
   if jsonb_typeof(v)<>'string' or (v#>>'{}') !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION'; end if;
   perform (v#>>'{}')::date;
  end if;
  if f.type='SELECT' and (jsonb_typeof(v)<>'string' or not f.options @> jsonb_build_array(v)) then raise exception 'VALIDATION'; end if;
 end loop;
end $$;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');
 wid uuid; bid uuid; tid uuid; role_name text; rid uuid; target_id uuid;
 result jsonb; previous public.ws_requests; invite public.ws_invites; rec public.ws_records;
 name_value text; field_type text; choices jsonb; pos integer; rate integer;
begin
 if nullif(p_uid,'') is null or nullif(p_email,'') is null then raise exception 'UNAUTHORIZED'; end if;
 insert into public.ws_profiles(uid,email,display_name) values(p_uid,lower(trim(p_email)),left(coalesce(nullif(trim(p_name),''),split_part(p_email,'@',1)),100))
 on conflict(uid) do update set email=excluded.email;
 if p_path='/me' and p_method='GET' then
  return (select jsonb_build_object('uid',uid,'email',email,'displayName',display_name,'avatarUrl',avatar_url,'role','USER','active',true) from public.ws_profiles where uid=p_uid);
 end if;
 -- Resolve the resource's workspace on the server, never from a client role claim.
 if parts[1]='workspaces' and cardinality(parts)>=2 then wid:=parts[2]::uuid;
 elsif parts[1]='bases' and cardinality(parts)>=2 then
  bid:=parts[2]::uuid;select workspace_id into wid from public.ws_bases where id=bid;
  if wid is null then raise exception 'NOT_FOUND'; end if;
 elsif parts[1]='tables' and cardinality(parts)>=2 then
  tid:=parts[2]::uuid;select b.workspace_id,t.base_id into wid,bid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;
  if wid is null then raise exception 'NOT_FOUND'; end if;
 end if;
 if p_method<>'GET' then
  -- Account request lock handles retries on two devices; workspace lock protects schema and row limits.
  perform pg_advisory_xact_lock(hashtextextended(p_uid,41004));
  if wid is not null then perform pg_advisory_xact_lock(hashtextextended(wid::text,41005)); end if;
 end if;
 if wid is not null then
  select role into role_name from public.ws_members where workspace_id=wid and uid=p_uid;
  if role_name is null then raise exception 'FORBIDDEN'; end if;
  if p_method<>'GET' and role_name='VIEWER' then raise exception 'FORBIDDEN'; end if;
 end if;
 if p_method<>'GET' then
  rid:=(p_body->>'requestId')::uuid;
  if rid is null then raise exception 'VALIDATION'; end if;
  select * into previous from public.ws_requests where uid=p_uid and request_id=rid;
  if found then
   if previous.path<>p_path or previous.method<>p_method or previous.body<>p_body then raise exception 'CONFLICT'; end if;
   return previous.response;
  end if;
  insert into public.ws_limits(uid) values(p_uid) on conflict(uid) do update set
   count=case when ws_limits.window_at<now()-interval '1 minute' then 1 else ws_limits.count+1 end,
   window_at=case when ws_limits.window_at<now()-interval '1 minute' then now() else ws_limits.window_at end returning count into rate;
  if rate>60 then raise exception 'RATE_LIMIT'; end if;
 end if;
 name_value:=trim(coalesce(p_body->>'name',''));

 if p_path='/me' and p_method='PATCH' then
  if length(trim(coalesce(p_body->>'displayName',''))) not between 1 and 100 or coalesce(p_body->>'avatarUrl','') !~ '^https://api\.dicebear\.com/9\.x/shapes/png\?seed=[1-6]$' then raise exception 'VALIDATION'; end if;
  update public.ws_profiles set display_name=trim(p_body->>'displayName'),avatar_url=p_body->>'avatarUrl' where uid=p_uid;
  result:=public.workspace_api(p_uid,p_email,p_name,'GET','/me');
 elsif p_path='/workspaces' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',w.id,'name',w.name,'role',m.role) order by w.created_at,w.id),'[]') into result from public.ws_workspaces w join public.ws_members m on m.workspace_id=w.id where m.uid=p_uid;
 elsif p_path='/workspaces' and p_method='POST' then
  if (select count(*) from public.ws_members where uid=p_uid and role='OWNER')>=20 then raise exception 'VALIDATION'; end if;
  insert into public.ws_workspaces(name) values(name_value) returning id into wid;
  insert into public.ws_members values(wid,p_uid,'OWNER');
  result:=jsonb_build_object('id',wid,'name',name_value,'role','OWNER');
 elsif parts[1]='workspaces' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',w.id,'name',w.name,'role',role_name,
   'bases',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_bases where workspace_id=wid),
   'members',(select coalesce(jsonb_agg(jsonb_build_object('uid',m.uid,'displayName',p.display_name,'role',m.role) order by p.display_name),'[]') from public.ws_members m join public.ws_profiles p on p.uid=m.uid where m.workspace_id=wid),
   'invites',case when role_name='OWNER' then (select coalesce(jsonb_agg(jsonb_build_object('id',id,'email',email,'role',role)),'[]') from public.ws_invites where workspace_id=wid and accepted_at is null and revoked_at is null and expires_at>now()) else '[]'::jsonb end)
  into result from public.ws_workspaces w where w.id=wid;
 elsif parts[1]='workspaces' and cardinality(parts)=3 and parts[3]='bases' and p_method='POST' then
  if (select count(*) from public.ws_bases where workspace_id=wid)>=50 then raise exception 'VALIDATION'; end if;
  insert into public.ws_bases(workspace_id,name) values(wid,name_value) returning id into bid;
  result:=jsonb_build_object('id',bid,'name',name_value);
 elsif parts[1]='bases' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',b.id,'name',b.name,'role',role_name,'workspaceId',wid,
   'tables',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_tables where base_id=bid)) into result from public.ws_bases b where b.id=bid;
 elsif parts[1]='bases' and cardinality(parts)=3 and parts[3]='tables' and p_method='POST' then
  if (select count(*) from public.ws_tables where base_id=bid)>=50 then raise exception 'VALIDATION'; end if;
  insert into public.ws_tables(base_id,name) values(bid,name_value) returning id into tid;
  insert into public.ws_fields(table_id,name,type,position) values(tid,'Tên','TEXT',0);
  result:=jsonb_build_object('id',tid,'name',name_value);
 elsif parts[1]='tables' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',t.id,'name',t.name,'workspaceId',wid,'role',role_name,
   'fields',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name,'type',type,'options',options) order by position,id),'[]') from public.ws_fields where table_id=tid),
   'records',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'cells',cells,'version',version) order by created_at,id),'[]') from public.ws_records where table_id=tid and archived_at is null)) into result from public.ws_tables t where t.id=tid;
 elsif parts[1]='tables' and cardinality(parts)=3 and parts[3]='fields' and p_method='POST' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN'; end if;
  select count(*) into pos from public.ws_fields where table_id=tid;
  if pos>=30 then raise exception 'VALIDATION'; end if;
  field_type:=p_body->>'type';choices:=coalesce(p_body->'options','[]');
  if jsonb_typeof(choices)<>'array' or jsonb_array_length(choices)>30 then raise exception 'VALIDATION'; end if;
  if field_type='SELECT' and (jsonb_array_length(choices)=0 or exists(select 1 from jsonb_array_elements(choices) v where jsonb_typeof(v)<>'string' or length(trim(v#>>'{}')) not between 1 and 100)) then raise exception 'VALIDATION'; end if;
  insert into public.ws_fields(table_id,name,type,options,position) values(tid,name_value,field_type,choices,pos) returning id into target_id;
  result:=jsonb_build_object('id',target_id,'name',name_value,'type',field_type,'options',choices);
 elsif parts[1]='tables' and parts[3]='records' and ((cardinality(parts)=3 and p_method='POST') or (cardinality(parts)=4 and p_method in ('PATCH','DELETE'))) then
  if p_method<>'DELETE' then perform public.ws_validate_cells(tid,p_body->'cells'); end if;
  if p_method='POST' then
   if (select count(*) from public.ws_records where table_id=tid and archived_at is null)>=500 then raise exception 'VALIDATION'; end if;
   insert into public.ws_records(table_id,cells) values(tid,p_body->'cells') returning * into rec;
  else
   select * into rec from public.ws_records where id=parts[4]::uuid and table_id=tid and archived_at is null for update;
   if not found then raise exception 'NOT_FOUND'; end if;
   if rec.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT'; end if;
   update public.ws_records set cells=case when p_method='PATCH' then p_body->'cells' else cells end,version=version+1,updated_at=now(),archived_at=case when p_method='DELETE' then now() else null end where id=rec.id returning * into rec;
  end if;
  result:=jsonb_build_object('id',rec.id,'cells',rec.cells,'version',rec.version);
 elsif parts[1]='workspaces' and parts[3]='invites' and cardinality(parts)=3 and p_method='POST' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN'; end if;
  if length(coalesce(p_body->>'email',''))>254 or coalesce(p_body->>'email','') !~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then raise exception 'VALIDATION'; end if;
  insert into public.ws_invites(workspace_id,email,role) values(wid,lower(trim(p_body->>'email')),p_body->>'role') returning * into invite;
  result:=jsonb_build_object('id',invite.id,'email',invite.email,'role',invite.role);
 elsif parts[1]='workspaces' and parts[3]='invites' and cardinality(parts)=4 and p_method='DELETE' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN'; end if;
  update public.ws_invites set revoked_at=now() where id=parts[4]::uuid and workspace_id=wid;
  if not found then raise exception 'NOT_FOUND'; end if;
  result:='{}';
 elsif p_path='/invitations' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',i.id,'name',w.name,'role',i.role) order by i.expires_at),'[]') into result from public.ws_invites i join public.ws_workspaces w on w.id=i.workspace_id where i.email=lower(trim(p_email)) and i.revoked_at is null and i.accepted_at is null and i.expires_at>now();
 elsif parts[1]='invitations' and parts[3]='accept' and cardinality(parts)=3 and p_method='POST' then
  select * into invite from public.ws_invites where id=parts[2]::uuid for update;
  if not found then raise exception 'NOT_FOUND'; end if;
  if invite.email<>lower(trim(p_email)) then raise exception 'FORBIDDEN'; end if;
  if invite.revoked_at is not null or invite.expires_at<=now() then raise exception 'INVALID_STATE'; end if;
  insert into public.ws_members values(invite.workspace_id,p_uid,invite.role) on conflict do nothing;
  update public.ws_invites set accepted_at=coalesce(accepted_at,now()) where id=invite.id;
  wid:=invite.workspace_id;result:=jsonb_build_object('id',wid);
 else raise exception 'NOT_FOUND'; end if;
 if p_method<>'GET' then
  insert into public.ws_requests values(p_uid,rid,p_method,p_path,p_body,result);
  if wid is not null then insert into public.ws_events(workspace_id,actor_uid,path,method) values(wid,p_uid,p_path,p_method); end if;
 end if;
 return result;
end $$;

do $$ declare t text; begin
 foreach t in array array['ws_profiles','ws_workspaces','ws_members','ws_invites','ws_bases','ws_tables','ws_fields','ws_records','ws_events','ws_requests','ws_limits'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('revoke all on public.%I from public, anon, authenticated',t);
 end loop;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_validate_cells(uuid,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
