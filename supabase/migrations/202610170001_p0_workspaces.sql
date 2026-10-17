-- P0 is additive: v1, legacy cells and existing drafts are retained.
begin;
alter table public.ws_members add column version integer not null default 1;
alter table public.ws_tables add column version integer not null default 1;
alter table public.ws_tables add column schema_version integer not null default 1;
alter table public.ws_fields add column version integer not null default 1;
alter table public.ws_fields add column system_key text;
alter table public.ws_fields drop constraint ws_fields_type_check;
alter table public.ws_fields add constraint ws_fields_type_check check(type in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER'));
alter table public.ws_requests add column created_at timestamptz not null default now();
alter table public.ws_events add column sequence bigint generated always as identity;
alter table public.ws_events add column entity_type text;
alter table public.ws_events add column entity_id text;
alter table public.ws_events add column action text;
alter table public.ws_events add column request_id uuid;
alter table public.ws_events add column before_version integer;
alter table public.ws_events add column after_version integer;
alter table public.ws_events add column changes jsonb not null default '{}';
create index ws_event_entity on public.ws_events(workspace_id,entity_type,entity_id,sequence desc);
create table public.ws_views(
 id uuid primary key default gen_random_uuid(),table_id uuid not null references public.ws_tables(id),name text not null check(length(trim(name)) between 1 and 100),
 type text not null default 'GRID' check(type='GRID'),visibility text not null check(visibility in ('SHARED','PERSONAL')),owner_uid text not null references public.ws_profiles(uid),
 version integer not null default 1,filter jsonb not null default '{"op":"and","conditions":[]}',sort jsonb not null default '[]',
 field_ids jsonb not null default '[]',field_order jsonb not null default '[]',is_default boolean not null default false,
 check(not is_default or visibility='SHARED')
);
create unique index ws_default_view on public.ws_views(table_id) where is_default;
create index ws_view_table on public.ws_views(table_id,visibility,owner_uid);
insert into public.ws_views(table_id,name,visibility,owner_uid,is_default)
 select t.id,'Tất cả','SHARED',m.uid,true from public.ws_tables t join public.ws_bases b on b.id=t.base_id join public.ws_members m on m.workspace_id=b.workspace_id and m.role='OWNER';
create table public.ws_read_limits(uid text primary key,window_at timestamptz not null default now(),count integer not null default 1);
create table public.ws_query_cursors(
 id uuid primary key default gen_random_uuid(),uid text not null,table_id uuid not null references public.ws_tables(id),query_hash text not null,
 last_cells jsonb not null,last_created_at timestamptz not null,last_id uuid not null,expires_at timestamptz not null default now()+interval '15 minutes'
);
create index ws_cursor_expiry on public.ws_query_cursors(expires_at);

create function public.ws_p0_delta(p_before jsonb,p_after jsonb) returns jsonb language sql immutable as $$
 select coalesce(jsonb_object_agg(k,jsonb_build_object('before',p_before->k,'after',p_after->k)),'{}')
 from (select jsonb_object_keys(coalesce(p_before,'{}')) k union select jsonb_object_keys(coalesce(p_after,'{}')) k) keys
 where (p_before->k) is distinct from (p_after->k)
$$;

create function public.ws_p0_validate(p_table uuid,p_cells jsonb,p_before jsonb default '{}') returns void language plpgsql as $$
declare f public.ws_fields;k text;v jsonb;wid uuid;
begin
 if p_cells is null or jsonb_typeof(p_cells)<>'object' or pg_column_size(p_cells)>24000 then raise exception 'VALIDATION';end if;
 select b.workspace_id into wid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=p_table;
 for k,v in select * from jsonb_each(p_cells) loop
  select * into f from public.ws_fields where table_id=p_table and id::text=k;
  if not found then raise exception 'VALIDATION';end if;
  if v='null'::jsonb then continue;end if;
  if f.type='USER' then
   if jsonb_typeof(v)<>'string' or length(v#>>'{}')>128 then raise exception 'VALIDATION';end if;
   if v is distinct from p_before->k and not exists(select 1 from public.ws_members where workspace_id=wid and uid=v#>>'{}') then raise exception 'VALIDATION';end if;
  elsif f.type='TEXT' then if jsonb_typeof(v)<>'string' or length(v#>>'{}')>5000 then raise exception 'VALIDATION';end if;
  elsif f.type='NUMBER' then if jsonb_typeof(v)<>'number' then raise exception 'VALIDATION';end if;
  elsif f.type='CHECKBOX' then if jsonb_typeof(v)<>'boolean' then raise exception 'VALIDATION';end if;
  elsif f.type='SELECT' then if jsonb_typeof(v)<>'string' or not (f.options @> jsonb_build_array(v)) then raise exception 'VALIDATION';end if;
  elsif f.type='DATE' then
   if jsonb_typeof(v)<>'string' or (v#>>'{}') !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION';end if;
   begin perform (v#>>'{}')::date;exception when others then raise exception 'VALIDATION';end;
  else raise exception 'UNSUPPORTED_FIELD';end if;
 end loop;
 -- Existing generic blank tables remain valid; the new TASKS template requires its title.
 select * into f from public.ws_fields where table_id=p_table and system_key='title';
 if found and (jsonb_typeof(p_cells->f.id::text) is distinct from 'string' or length(trim(coalesce(p_cells->>f.id::text,'')))=0) then raise exception 'VALIDATION';end if;
end $$;

create or replace function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');wid uuid;bid uuid;tid uuid;role_name text;rid uuid;target_id uuid;
 previous public.ws_requests;invite public.ws_invites;rec public.ws_records;old_rec public.ws_records;field_row public.ws_fields;view_row public.ws_views;member_row public.ws_members;
 result jsonb;old_cells jsonb:='{}';new_cells jsonb;changes jsonb:='{}';before_ver integer;after_ver integer;
 name_value text:=trim(coalesce(p_body->>'name',''));field_type text;choices jsonb;pos integer;rate integer;schema_num integer;
 entity_type text;entity_id text;action_name text;query_result jsonb;config jsonb;member_version integer;
 readonly boolean:=p_method='GET' or (p_method='POST' and ((parts[1]='tables' and parts[3]='records' and parts[4]='query' and cardinality(parts)=4) or (parts[5]='history' and parts[6]='query' and cardinality(parts)=6)));
 personal_write boolean:=false;legacy_safe boolean:=true;
begin
 if nullif(p_uid,'') is null or length(p_uid)>128 or nullif(p_email,'') is null then raise exception 'UNAUTHORIZED';end if;
 if p_method not in ('GET','POST','PATCH','PUT','DELETE') or jsonb_typeof(p_body)<>'object' then raise exception 'VALIDATION';end if;
 insert into public.ws_profiles(uid,email,display_name) values(p_uid,lower(trim(p_email)),left(coalesce(nullif(trim(p_name),''),split_part(p_email,'@',1)),100)) on conflict(uid) do update set email=excluded.email;
 if parts[1]='workspaces' and cardinality(parts)>=2 then wid:=parts[2]::uuid;
 elsif parts[1]='bases' and cardinality(parts)>=2 then
  bid:=parts[2]::uuid;select workspace_id into wid from public.ws_bases where id=bid;if wid is null then raise exception 'NOT_FOUND';end if;
 elsif parts[1]='tables' and cardinality(parts)>=2 then
  tid:=parts[2]::uuid;select b.workspace_id,t.base_id,t.schema_version into wid,bid,schema_num from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;if wid is null then raise exception 'NOT_FOUND';end if;
  legacy_safe:=not exists(select 1 from public.ws_fields where table_id=tid and type not in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT'));
 elsif parts[1]='invitations' and parts[3]='accept' and cardinality(parts)=3 then
  select workspace_id into wid from public.ws_invites where id=parts[2]::uuid;
 end if;
 if not readonly then
  perform pg_advisory_xact_lock(hashtextextended(p_uid,41004));
  if wid is not null then perform pg_advisory_xact_lock(hashtextextended(wid::text,41005));end if;
  if tid is not null then
   select schema_version into schema_num from public.ws_tables where id=tid;
   legacy_safe:=not exists(select 1 from public.ws_fields where table_id=tid and type not in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT'));
  end if;
 end if;
 if wid is not null then
  select role into role_name from public.ws_members where workspace_id=wid and uid=p_uid;
  if role_name is null and not (parts[1]='invitations' and parts[3]='accept' and cardinality(parts)=3) then raise exception 'FORBIDDEN';end if;
  personal_write:=parts[1]='tables' and parts[3]='views' and ((p_method='POST' and cardinality(parts)=3 and p_body->>'visibility'='PERSONAL') or (p_method='PATCH' and cardinality(parts)=4 and exists(select 1 from public.ws_views where id=parts[4]::uuid and table_id=tid and visibility='PERSONAL' and owner_uid=p_uid)));
  if not readonly and role_name='VIEWER' and not personal_write then raise exception 'FORBIDDEN';end if;
 end if;
 if readonly then
  insert into public.ws_read_limits(uid) values(p_uid) on conflict(uid) do update set count=case when ws_read_limits.window_at<now()-interval '1 minute' then 1 else ws_read_limits.count+1 end,window_at=case when ws_read_limits.window_at<now()-interval '1 minute' then now() else ws_read_limits.window_at end returning count into rate;
  if rate>600 then raise exception 'RATE_LIMIT';end if;
 else
  rid:=(p_body->>'requestId')::uuid;if rid is null then raise exception 'VALIDATION';end if;
  select * into previous from public.ws_requests where uid=p_uid and request_id=rid;
  if found then
   if previous.path<>p_path or previous.method<>p_method or previous.body<>p_body then raise exception 'REQUEST_REUSED';end if;
   return previous.response;
  end if;
  insert into public.ws_limits(uid) values(p_uid) on conflict(uid) do update set count=case when ws_limits.window_at<now()-interval '1 minute' then 1 else ws_limits.count+1 end,window_at=case when ws_limits.window_at<now()-interval '1 minute' then now() else ws_limits.window_at end returning count into rate;
  if rate>60 then raise exception 'RATE_LIMIT';end if;
 end if;
 if p_path='/capabilities' and p_method='GET' then
  return jsonb_build_object('apiVersion',2,'features',jsonb_build_object('atomicTemplates',true,'savedViews',true,'pagedRecords',true,'userFields',true,'cellPatch',true,'memberAdmin',true,'history',true),'limits',jsonb_build_object('recordsPerTable',500,'fieldsPerTable',30,'pageSize',100));
 elsif p_path='/me' and p_method='GET' then
  return (select jsonb_build_object('uid',uid,'email',email,'displayName',display_name,'avatarUrl',avatar_url,'role','USER','active',true) from public.ws_profiles where uid=p_uid);
 elsif p_path='/me' and p_method='PATCH' then
  if length(trim(coalesce(p_body->>'displayName',''))) not between 1 and 100 or coalesce(p_body->>'avatarUrl','') !~ '^https://api\.dicebear\.com/9\.x/shapes/png\?seed=[1-6]$' then raise exception 'VALIDATION';end if;
  update public.ws_profiles set display_name=trim(p_body->>'displayName'),avatar_url=p_body->>'avatarUrl' where uid=p_uid;
  result:=public.workspace_api(p_uid,p_email,p_name,'GET','/me');
 elsif p_path='/workspaces' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',w.id,'name',w.name,'role',m.role) order by w.created_at,w.id),'[]') into result from public.ws_workspaces w join public.ws_members m on m.workspace_id=w.id where m.uid=p_uid;
 elsif p_path='/workspaces' and p_method='POST' then
  if (select count(*) from public.ws_members where uid=p_uid and role='OWNER')>=20 then raise exception 'VALIDATION';end if;
  insert into public.ws_workspaces(name) values(name_value) returning id into wid;insert into public.ws_members(workspace_id,uid,role) values(wid,p_uid,'OWNER');
  result:=jsonb_build_object('id',wid,'name',name_value,'role','OWNER');entity_type:='WORKSPACE';entity_id:=wid::text;
 elsif parts[1]='workspaces' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',w.id,'name',w.name,'role',role_name,
   'bases',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_bases where workspace_id=wid),
   'members',(select coalesce(jsonb_agg(jsonb_build_object('uid',m.uid,'displayName',p.display_name,'role',m.role,'version',m.version) order by p.display_name,m.uid),'[]') from public.ws_members m join public.ws_profiles p on p.uid=m.uid where m.workspace_id=wid),
   'invites',case when role_name='OWNER' then (select coalesce(jsonb_agg(jsonb_build_object('id',id,'email',email,'role',role)),'[]') from public.ws_invites where workspace_id=wid and accepted_at is null and revoked_at is null and expires_at>now()) else '[]'::jsonb end) into result from public.ws_workspaces w where w.id=wid;
 elsif parts[1]='workspaces' and parts[3]='bases' and cardinality(parts)=3 and p_method='POST' then
  if (select count(*) from public.ws_bases where workspace_id=wid)>=50 then raise exception 'VALIDATION';end if;
  insert into public.ws_bases(workspace_id,name) values(wid,name_value) returning id into bid;result:=jsonb_build_object('id',bid,'name',name_value);entity_type:='BASE';entity_id:=bid::text;
 elsif parts[1]='bases' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',b.id,'name',b.name,'role',role_name,'workspaceId',wid,'tables',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_tables where base_id=bid)) into result from public.ws_bases b where b.id=bid;
 elsif parts[1]='bases' and parts[3]='tables' and cardinality(parts)=3 and p_method='POST' then
  if (select count(*) from public.ws_tables where base_id=bid)>=50 then raise exception 'VALIDATION';end if;
  if p_body ? 'template' and p_body->>'template' is distinct from 'TASKS' then raise exception 'VALIDATION';end if;
  if p_body->>'template'='TASKS' and role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  insert into public.ws_tables(base_id,name) values(bid,name_value) returning id into tid;
  insert into public.ws_fields(table_id,name,type,position,system_key) values(tid,'Tên','TEXT',0,case when p_body->>'template'='TASKS' then 'title' end);
  if p_body->>'template'='TASKS' then
   insert into public.ws_fields(table_id,name,type,options,position,system_key) values
    (tid,'Mô tả','TEXT','[]',1,'description'),(tid,'Trạng thái','SELECT','["Chưa làm","Đang làm","Chờ duyệt","Hoàn thành"]',2,'status'),
    (tid,'Ưu tiên','SELECT','["Thấp","Bình thường","Cao"]',3,'priority'),(tid,'Hạn','DATE','[]',4,'due'),(tid,'Người phụ trách','USER','[]',5,'assignee');
  end if;
  insert into public.ws_views(table_id,name,visibility,owner_uid,is_default) values(tid,'Tất cả','SHARED',p_uid,true) returning id into target_id;
  result:=jsonb_build_object('id',tid,'name',name_value,'defaultViewId',target_id,'schemaVersion',1);entity_type:='TABLE';entity_id:=tid::text;
 elsif parts[1]='tables' and cardinality(parts)=2 and p_method='GET' then
  if not legacy_safe then raise exception 'CLIENT_UPGRADE_REQUIRED';end if;
  select jsonb_build_object('id',t.id,'name',t.name,'workspaceId',wid,'role',role_name,
   'fields',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name,'type',type,'options',options) order by position,id),'[]') from public.ws_fields where table_id=tid),
   'records',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'cells',cells,'version',version) order by created_at,id),'[]') from public.ws_records where table_id=tid and archived_at is null)) into result from public.ws_tables t where t.id=tid;
 elsif parts[1]='tables' and parts[3]='metadata' and cardinality(parts)=3 and p_method='GET' then
  select jsonb_build_object('id',t.id,'name',t.name,'workspaceId',wid,'role',role_name,'version',t.version,'schemaVersion',t.schema_version,
   'fields',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name,'type',type,'options',options,'position',position,'systemKey',system_key,'version',version) order by position,id),'[]') from public.ws_fields where table_id=tid),
   'views',(select coalesce(jsonb_agg(public.ws_p0_view_json(v) order by is_default desc,name,id),'[]') from public.ws_views v where table_id=tid and (visibility='SHARED' or owner_uid=p_uid)),
   'members',(select coalesce(jsonb_agg(jsonb_build_object('uid',m.uid,'displayName',p.display_name,'role',m.role,'version',m.version) order by p.display_name,m.uid),'[]') from public.ws_members m join public.ws_profiles p on p.uid=m.uid where m.workspace_id=wid)) into result from public.ws_tables t where t.id=tid;
 elsif parts[1]='tables' and parts[3]='fields' and cardinality(parts)=3 and p_method='POST' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  if p_body ? 'expectedSchemaVersion' and schema_num is distinct from (p_body->>'expectedSchemaVersion')::integer then raise exception 'SCHEMA_CHANGED';end if;
  select count(*) into pos from public.ws_fields where table_id=tid;if pos>=30 then raise exception 'VALIDATION';end if;
  field_type:=p_body->>'type';choices:=coalesce(p_body->'options','[]');
  if field_type is null or field_type not in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER') then raise exception 'UNSUPPORTED_FIELD';end if;
  if jsonb_typeof(choices)<>'array' or jsonb_array_length(choices)>30 then raise exception 'VALIDATION';end if;
  if field_type='SELECT' and (jsonb_array_length(choices)=0 or exists(select 1 from jsonb_array_elements(choices) x where jsonb_typeof(x)<>'string' or length(trim(x#>>'{}')) not between 1 and 100) or (select count(distinct x) from jsonb_array_elements(choices) x)<>jsonb_array_length(choices)) then raise exception 'VALIDATION';end if;
  insert into public.ws_fields(table_id,name,type,options,position) values(tid,name_value,field_type,choices,pos) returning id into target_id;
  update public.ws_tables set schema_version=schema_version+1,version=version+1 where id=tid returning schema_version into schema_num;
  result:=jsonb_build_object('id',target_id,'name',name_value,'type',field_type,'options',choices,'schemaVersion',schema_num);entity_type:='FIELD';entity_id:=target_id::text;
 elsif parts[1]='tables' and parts[3]='records' and parts[4]='query' and cardinality(parts)=4 and p_method='POST' then
  return public.ws_p0_query(p_uid,tid,p_body);
 elsif parts[1]='tables' and parts[3]='records' and cardinality(parts)=4 and p_method='GET' then
  select jsonb_build_object('id',id,'cells',cells,'version',version,'createdAt',created_at,'updatedAt',updated_at) into result from public.ws_records where table_id=tid and id=parts[4]::uuid and archived_at is null;if result is null then raise exception 'NOT_FOUND';end if;
 elsif parts[1]='tables' and parts[3]='records' and ((cardinality(parts)=3 and p_method='POST') or (cardinality(parts)=4 and p_method in ('PATCH','DELETE')) or (cardinality(parts)=5 and parts[5]='cells' and p_method='PATCH')) then
  if not legacy_safe and (not p_body ? 'expectedSchemaVersion' or (p_method='PATCH' and cardinality(parts)=4)) then raise exception 'CLIENT_UPGRADE_REQUIRED';end if;
  if p_body ? 'expectedSchemaVersion' and schema_num is distinct from (p_body->>'expectedSchemaVersion')::integer then raise exception 'SCHEMA_CHANGED';end if;
  if cardinality(parts)=5 and not p_body ? 'expectedSchemaVersion' then raise exception 'VALIDATION';end if;
  if p_method='POST' then
   if (select count(*) from public.ws_records where table_id=tid and archived_at is null)>=500 then raise exception 'VALIDATION';end if;
   perform public.ws_p0_validate(tid,p_body->'cells');insert into public.ws_records(table_id,cells) values(tid,p_body->'cells') returning * into rec;
  else
   select * into rec from public.ws_records where id=parts[4]::uuid and table_id=tid and archived_at is null for update;if not found then raise exception 'NOT_FOUND';end if;
   if rec.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
   old_cells:=rec.cells;before_ver:=rec.version;
   if p_method='PATCH' then
    if cardinality(parts)=5 then
     if jsonb_typeof(p_body->'changes') is distinct from 'object' then raise exception 'VALIDATION';end if;
     new_cells:=rec.cells||(p_body->'changes');
    else new_cells:=p_body->'cells';end if;
    perform public.ws_p0_validate(tid,new_cells,old_cells);
   else new_cells:=rec.cells;end if;
   update public.ws_records set cells=new_cells,version=version+1,updated_at=now(),archived_at=case when p_method='DELETE' then now() else null end where id=rec.id returning * into rec;
  end if;
  changes:=public.ws_p0_delta(old_cells,rec.cells);after_ver:=rec.version;entity_type:='RECORD';entity_id:=rec.id::text;
  action_name:=case p_method when 'POST' then 'CREATED' when 'DELETE' then 'ARCHIVED' else 'UPDATED' end;
  result:=jsonb_build_object('id',rec.id,'cells',rec.cells,'version',rec.version,'createdAt',rec.created_at,'updatedAt',rec.updated_at);
 elsif parts[1]='tables' and parts[3]='views' and cardinality(parts)=3 and p_method='GET' then
  select jsonb_build_object('items',coalesce(jsonb_agg(public.ws_p0_view_json(v) order by is_default desc,name,id),'[]')) into result from public.ws_views v where table_id=tid and (visibility='SHARED' or owner_uid=p_uid);
 elsif parts[1]='tables' and parts[3]='views' and ((cardinality(parts)=3 and p_method='POST') or (cardinality(parts)=4 and p_method='PATCH')) then
  if p_method='POST' then
   if (select count(*) from public.ws_views where table_id=tid)>=50 then raise exception 'VALIDATION';end if;
   if coalesce(p_body->>'visibility','SHARED')='SHARED' and role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
   insert into public.ws_views(table_id,name,visibility,owner_uid) values(tid,name_value,coalesce(p_body->>'visibility','SHARED'),p_uid) returning * into view_row;
  else
   select * into view_row from public.ws_views where table_id=tid and id=parts[4]::uuid for update;if not found then raise exception 'NOT_FOUND';end if;
   if (view_row.visibility='PERSONAL' and view_row.owner_uid<>p_uid) or (view_row.visibility='SHARED' and role_name<>'OWNER') then raise exception 'FORBIDDEN';end if;
   if view_row.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
   if p_body ? 'visibility' and p_body->>'visibility'<>view_row.visibility then raise exception 'VALIDATION';end if;
   before_ver:=view_row.version;
  end if;
  if p_body ? 'type' and p_body->>'type'<>'GRID' then raise exception 'INVALID_VIEW';end if;
  config:=jsonb_build_object('filter',coalesce(p_body->'filter',view_row.filter),'sort',coalesce(p_body->'sort',view_row.sort),'fieldIds',coalesce(p_body->'fieldIds',view_row.field_ids));
  perform public.ws_p0_query(p_uid,tid,config||jsonb_build_object('limit',1));
  choices:=coalesce(p_body->'fieldOrder',view_row.field_order);
  if jsonb_typeof(choices)<>'array' or jsonb_array_length(choices)>30 or (select count(distinct x) from jsonb_array_elements_text(choices) x)<>jsonb_array_length(choices) or exists(select 1 from jsonb_array_elements_text(choices) x where not exists(select 1 from public.ws_fields where table_id=tid and id::text=x)) then raise exception 'VALIDATION';end if;
  update public.ws_views set name=case when p_body ? 'name' then name_value else name end,filter=config->'filter',sort=config->'sort',field_ids=config->'fieldIds',field_order=choices,version=case when p_method='PATCH' then version+1 else version end where id=view_row.id returning * into view_row;
  result:=public.ws_p0_view_json(view_row);entity_type:='VIEW';entity_id:=view_row.id::text;after_ver:=view_row.version;
 elsif parts[1]='workspaces' and parts[3]='invites' and cardinality(parts)=3 and p_method='GET' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select jsonb_build_object('items',coalesce(jsonb_agg(jsonb_build_object('id',id,'email',email,'role',role)),'[]')) into result from public.ws_invites where workspace_id=wid and accepted_at is null and revoked_at is null and expires_at>now();
 elsif parts[1]='workspaces' and parts[3]='invites' and cardinality(parts)=3 and p_method='POST' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  if length(coalesce(p_body->>'email',''))>254 or coalesce(p_body->>'email','') !~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then raise exception 'VALIDATION';end if;
  update public.ws_invites set revoked_at=now() where workspace_id=wid and email=lower(trim(p_body->>'email')) and accepted_at is null and revoked_at is null;
  insert into public.ws_invites(workspace_id,email,role) values(wid,lower(trim(p_body->>'email')),p_body->>'role') returning * into invite;
  result:=jsonb_build_object('id',invite.id,'email',invite.email,'role',invite.role);entity_type:='INVITE';entity_id:=invite.id::text;
 elsif parts[1]='workspaces' and parts[3]='invites' and cardinality(parts)=4 and p_method='DELETE' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  update public.ws_invites set revoked_at=now() where id=parts[4]::uuid and workspace_id=wid;if not found then raise exception 'NOT_FOUND';end if;
  result:='{}';entity_type:='INVITE';entity_id:=parts[4];
 elsif p_path='/invitations' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',i.id,'name',w.name,'role',i.role) order by i.expires_at),'[]') into result from public.ws_invites i join public.ws_workspaces w on w.id=i.workspace_id where i.email=lower(trim(p_email)) and i.revoked_at is null and i.accepted_at is null and i.expires_at>now();
 elsif parts[1]='invitations' and parts[3]='accept' and cardinality(parts)=3 and p_method='POST' then
  select * into invite from public.ws_invites where id=parts[2]::uuid for update;if not found then raise exception 'NOT_FOUND';end if;
  if invite.email<>lower(trim(p_email)) then raise exception 'FORBIDDEN';end if;
  if invite.revoked_at is not null or invite.expires_at<=now() or invite.accepted_at is not null then raise exception 'INVALID_STATE';end if;
  perform pg_advisory_xact_lock(hashtextextended(invite.workspace_id::text,41005));
  insert into public.ws_members(workspace_id,uid,role) values(invite.workspace_id,p_uid,invite.role) on conflict do nothing;
  update public.ws_invites set accepted_at=now() where id=invite.id;wid:=invite.workspace_id;result:=jsonb_build_object('id',wid);entity_type:='MEMBER';entity_id:=p_uid;
 elsif parts[1]='workspaces' and parts[3]='members' and cardinality(parts)=4 and p_method in ('PATCH','DELETE') then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select * into member_row from public.ws_members where workspace_id=wid and uid=parts[4] for update;if not found then raise exception 'NOT_FOUND';end if;
  if member_row.role='OWNER' then raise exception 'FORBIDDEN';end if;
  if member_row.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
  before_ver:=member_row.version;
  if p_method='DELETE' then delete from public.ws_members where workspace_id=wid and uid=parts[4];result:='{}';
  else
   if p_body->>'role' is null or p_body->>'role' not in ('EDITOR','VIEWER') then raise exception 'VALIDATION';end if;
   update public.ws_members set role=p_body->>'role',version=version+1 where workspace_id=wid and uid=parts[4] returning version into after_ver;result:=jsonb_build_object('uid',parts[4],'role',p_body->>'role','version',after_ver);
  end if;
  changes:=jsonb_build_object('role',jsonb_build_object('before',member_row.role,'after',p_body->>'role'));entity_type:='MEMBER';entity_id:=parts[4];
 elsif parts[1]='workspaces' and parts[3]='transfer-ownership' and cardinality(parts)=3 and p_method='POST' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select version into member_version from public.ws_members where workspace_id=wid and uid=p_uid;
  if member_version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
   if p_body->>'targetUid'=p_uid or not exists(select 1 from public.ws_members where workspace_id=wid and uid=p_body->>'targetUid') then raise exception 'VALIDATION';end if;
  update public.ws_members set role='EDITOR',version=version+1 where workspace_id=wid and uid=p_uid;
  update public.ws_members set role='OWNER',version=version+1 where workspace_id=wid and uid=p_body->>'targetUid';
  result:=jsonb_build_object('ownerUid',p_body->>'targetUid');entity_type:='MEMBER';entity_id:=p_body->>'targetUid';action_name:='OWNERSHIP_TRANSFERRED';changes:=jsonb_build_object('previousOwner',p_uid,'newOwner',p_body->>'targetUid');
 elsif (parts[1]='tables' and parts[3]='records' and parts[5]='history' and (cardinality(parts)=5 and p_method='GET' or cardinality(parts)=6 and parts[6]='query' and p_method='POST')) or (parts[1]='workspaces' and parts[3]='activity' and cardinality(parts)=3 and p_method='GET') then
  if parts[1]='tables' then
   if not exists(select 1 from public.ws_records where id=parts[4]::uuid and table_id=tid) then raise exception 'NOT_FOUND';end if;
  elsif role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select jsonb_build_object('items',coalesce(jsonb_agg(e.payload order by e.seq desc),'[]'),'nextCursor',case when count(*)=50 then min(e.seq)::text end) into result from
   (select ev.sequence seq,jsonb_build_object('id',ev.id,'action',coalesce(ev.action,ev.method),'actorUid',ev.actor_uid,'actorName',coalesce(p.display_name,ev.actor_uid),'createdAt',ev.created_at,'beforeVersion',ev.before_version,'afterVersion',ev.after_version,'changes',ev.changes) payload from public.ws_events ev left join public.ws_profiles p on p.uid=ev.actor_uid where ev.workspace_id=wid and (parts[1]<>'tables' or (ev.entity_type='RECORD' and ev.entity_id=parts[4])) and (p_body->>'cursor' is null or ev.sequence<(p_body->>'cursor')::bigint) order by ev.sequence desc limit 50) e;
 else raise exception 'NOT_FOUND';end if;
 if not readonly then
  insert into public.ws_requests(uid,request_id,method,path,body,response) values(p_uid,rid,p_method,p_path,p_body,result);
  if wid is not null then insert into public.ws_events(workspace_id,actor_uid,path,method,entity_type,entity_id,action,request_id,before_version,after_version,changes) values(wid,p_uid,p_path,p_method,entity_type,entity_id,coalesce(action_name,p_method),rid,before_ver,after_ver,changes);end if;
 end if;
 return result;
end $$;

create function public.ws_p0_filter(p_table uuid,p_spec jsonb,p_uid text,p_depth integer default 0) returns text language plpgsql as $$
declare item jsonb;piece text;result text:='';mode text;fid uuid;f public.ws_fields;op text;v jsonb;expr text;literal text;predicate_count integer;
begin
 if p_spec is null or p_spec='null'::jsonb then return 'true';end if;
 if p_depth>4 or jsonb_typeof(p_spec)<>'object' then raise exception 'VALIDATION';end if;
 if p_depth=0 then
  with recursive nodes(value) as (
   select p_spec union all select child.value from nodes n cross join lateral jsonb_array_elements(case when jsonb_typeof(n.value->'conditions')='array' then n.value->'conditions' else '[]'::jsonb end) child(value)
  ) select count(*) into predicate_count from nodes where not value ? 'conditions';
  if predicate_count>20 then raise exception 'VALIDATION';end if;
 end if;
 if p_spec ? 'conditions' then
  mode:=p_spec->>'op';if mode is null or mode not in ('and','or') or jsonb_typeof(p_spec->'conditions')<>'array' or jsonb_array_length(p_spec->'conditions')>20 then raise exception 'VALIDATION';end if;
  for item in select * from jsonb_array_elements(p_spec->'conditions') loop
   piece:=public.ws_p0_filter(p_table,item,p_uid,p_depth+1);result:=result||case when result='' then '' else ' '||mode||' ' end||'('||piece||')';
  end loop;
  return case when result='' then 'true' else result end;
 end if;
 fid:=(p_spec->>'fieldId')::uuid;select * into f from public.ws_fields where id=fid and table_id=p_table;
 if not found then raise exception 'VALIDATION';end if;
 op:=p_spec->>'operator';v:=p_spec->'value';expr:=format('(r.cells->%L)',fid::text);
 if op is null then raise exception 'VALIDATION';end if;
 if op='isEmpty' then return '('||expr||' is null or '||expr||'=''null''::jsonb or '||expr||'=''""''::jsonb)';end if;
 if op='isMe' then if f.type<>'USER' then raise exception 'VALIDATION';end if;v:=to_jsonb(p_uid);op:='eq';end if;
 if op in ('eq','ne') then
  if v is null then raise exception 'VALIDATION';end if;
  if v<>'null'::jsonb then
   if (f.type in ('TEXT','SELECT','USER','DATE') and jsonb_typeof(v)<>'string') or (f.type='NUMBER' and jsonb_typeof(v)<>'number') or (f.type='CHECKBOX' and jsonb_typeof(v)<>'boolean') then raise exception 'VALIDATION';end if;
   if f.type='SELECT' and not f.options @> jsonb_build_array(v) then raise exception 'VALIDATION';end if;
   if f.type='DATE' then
    if (v#>>'{}') !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION';end if;
    begin perform (v#>>'{}')::date;exception when others then raise exception 'VALIDATION';end;
   end if;
  end if;
  return format('coalesce(%s,''null''::jsonb) %s %L::jsonb',expr,case when op='eq' then '=' else '<>' end,v::text);
 end if;
 if op='contains' then
  if f.type not in ('TEXT','SELECT') or jsonb_typeof(v)<>'string' then raise exception 'VALIDATION';end if;
  return format('strpos(lower(coalesce(r.cells->>%L,'''')),lower(%L))>0',fid::text,v#>>'{}');
 end if;
 if op not in ('lt','lte','gt','gte') or f.type not in ('NUMBER','DATE') then raise exception 'VALIDATION';end if;
 if f.type='NUMBER' then
  if jsonb_typeof(v)<>'number' then raise exception 'VALIDATION';end if;
  expr:=format('(r.cells->>%L)::numeric',fid::text);literal:=format('%L::numeric',v#>>'{}');
 else
  if jsonb_typeof(v)<>'string' or (v#>>'{}') !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION';end if;
  begin perform (v#>>'{}')::date;exception when others then raise exception 'VALIDATION';end;
  expr:=format('(r.cells->>%L)::date',fid::text);literal:=format('%L::date',v#>>'{}');
 end if;
 return expr||case op when 'lt' then '<' when 'lte' then '<=' when 'gt' then '>' else '>=' end||literal;
end $$;

create function public.ws_p0_view_json(v public.ws_views) returns jsonb language sql stable as $$
 select jsonb_build_object('id',v.id,'name',v.name,'type',v.type,'visibility',v.visibility,'ownerUid',v.owner_uid,'version',v.version,'filter',v.filter,'sort',v.sort,'group','[]'::jsonb,'fieldIds',v.field_ids,'fieldOrder',v.field_order,'isDefault',v.is_default)
$$;

create function public.ws_p0_query(p_uid text,p_table uuid,p_body jsonb) returns jsonb language plpgsql as $$
declare t public.ws_tables;v public.ws_views;c public.ws_query_cursors;f public.ws_fields;
 filters jsonb;sorts jsonb;projection jsonb;sort_item jsonb;result jsonb:='[]';rawrows jsonb;rowdata jsonb;lastrow jsonb;cells jsonb;
 where_sql text;order_sql text:='';seek_sql text:='false';equal_sql text:='true';expr text;val text;direction text;cmp text;
 is_null integer;hash text;cursor_id uuid;next_id uuid;page_size integer;schema_num integer;view_num integer:=0;search_value text;
begin
 select * into t from public.ws_tables where id=p_table;
 schema_num:=t.schema_version;
 if p_body ? 'expectedSchemaVersion' and schema_num is distinct from (p_body->>'expectedSchemaVersion')::integer then raise exception 'SCHEMA_CHANGED';end if;
 if p_body->>'viewId' is not null then
  select * into v from public.ws_views where id=(p_body->>'viewId')::uuid and table_id=p_table;
  if not found or (v.visibility='PERSONAL' and v.owner_uid<>p_uid) then raise exception 'FORBIDDEN';end if;
  view_num:=v.version;
  if p_body ? 'viewVersion' and view_num is distinct from (p_body->>'viewVersion')::integer then raise exception 'CONFLICT';end if;
 end if;
 filters:=coalesce(p_body->'filter',v.filter,'{"op":"and","conditions":[]}');sorts:=coalesce(p_body->'sort',v.sort,'[]');projection:=coalesce(p_body->'fieldIds',v.field_ids,'[]');
 if jsonb_typeof(sorts)<>'array' or jsonb_array_length(sorts)>3 or jsonb_typeof(projection)<>'array' or jsonb_array_length(projection)>30 then raise exception 'VALIDATION';end if;
 if exists(select 1 from jsonb_array_elements_text(projection) x where not exists(select 1 from public.ws_fields where table_id=p_table and id::text=x)) then raise exception 'VALIDATION';end if;
 page_size:=coalesce((p_body->>'limit')::integer,50);if page_size not between 1 and 100 then raise exception 'VALIDATION';end if;
 if p_body ? 'limit' and jsonb_typeof(p_body->'limit')<>'number' then raise exception 'VALIDATION';end if;
 if p_body ? 'search' and jsonb_typeof(p_body->'search')<>'string' then raise exception 'VALIDATION';end if;
 search_value:=coalesce(p_body->>'search','');if length(search_value)>200 then raise exception 'VALIDATION';end if;
 where_sql:=public.ws_p0_filter(p_table,filters,p_uid);
 if search_value<>'' then where_sql:=where_sql||format(' and exists(select 1 from jsonb_each_text(r.cells) cell where strpos(lower(cell.value),lower(%L))>0)',search_value);end if;
 hash:=md5(jsonb_build_object('filter',filters,'sort',sorts,'fields',projection,'search',search_value,'schema',schema_num,'view',p_body->>'viewId','viewVersion',view_num,'limit',page_size)::text);
 if p_body->>'cursor' is not null then
  cursor_id:=(p_body->>'cursor')::uuid;select * into c from public.ws_query_cursors where id=cursor_id and uid=p_uid and table_id=p_table and expires_at>now();
  if not found or c.query_hash<>hash then raise exception 'CURSOR_STALE';end if;
 end if;
 for sort_item in select * from jsonb_array_elements(sorts) loop
  select * into f from public.ws_fields where id=(sort_item->>'fieldId')::uuid and table_id=p_table;
  if not found then raise exception 'VALIDATION';end if;
  direction:=coalesce(sort_item->>'direction','asc');if direction not in ('asc','desc') then raise exception 'VALIDATION';end if;
  expr:=format('(r.cells->>%L)',f.id::text);
  if f.type='NUMBER' then expr:=expr||'::numeric';elsif f.type='DATE' then expr:=expr||'::date';elsif f.type='CHECKBOX' then expr:=expr||'::boolean';end if;
  order_sql:=order_sql||case when order_sql='' then '' else ',' end||expr||' '||direction||' nulls last';
  if cursor_id is not null then
   val:=c.last_cells->>f.id::text;is_null:=case when val is null then 1 else 0 end;
   cmp:=case when direction='asc' then '>' else '<' end;
   val:=case when val is null then 'null' else quote_literal(val) end;
   if f.type='NUMBER' then val:=val||'::numeric';elsif f.type='DATE' then val:=val||'::date';elsif f.type='CHECKBOX' then val:=val||'::boolean';end if;
   seek_sql:=seek_sql||' or ('||equal_sql||' and (case when '||expr||' is null then 1 else 0 end > '||is_null||' or ('||is_null||'=0 and '||expr||cmp||val||')))';
   equal_sql:=equal_sql||' and ('||expr||' is not distinct from '||val||')';
  end if;
 end loop;
 if order_sql='' then
  order_sql:='r.created_at asc,r.id asc';
  if cursor_id is not null then seek_sql:=format('(r.created_at,r.id)>(%L::timestamptz,%L::uuid)',c.last_created_at,c.last_id);end if;
 else
  order_sql:=order_sql||',r.id asc';if cursor_id is not null then seek_sql:=seek_sql||format(' or (%s and r.id>%L::uuid)',equal_sql,c.last_id);end if;
 end if;
 if cursor_id is not null then where_sql:='('||where_sql||') and ('||seek_sql||')';end if;
 execute format('select coalesce(jsonb_agg(row_to_json(q)),''[]'') from (select r.id,r.cells,r.version,r.created_at as "createdAt",r.updated_at as "updatedAt" from public.ws_records r where r.table_id=%L::uuid and r.archived_at is null and (%s) order by %s limit %s) q',p_table,where_sql,order_sql,page_size+1) into rawrows;
 for rowdata in select value from jsonb_array_elements(rawrows) with ordinality e(value,n) where n<=page_size loop
  lastrow:=rowdata;cells:=rowdata->'cells';
  if jsonb_array_length(projection)>0 then select coalesce(jsonb_object_agg(k,value),'{}') into cells from jsonb_each(cells) x(k,value) where projection ? k;end if;
  result:=result||jsonb_build_array(jsonb_set(rowdata,'{cells}',cells));
 end loop;
 if jsonb_array_length(rawrows)>page_size then
  -- Opaque random cursor is bound in the database to UID/query/schema and expires; no user-supplied SQL or offsets.
  insert into public.ws_query_cursors(uid,table_id,query_hash,last_cells,last_created_at,last_id) values(p_uid,p_table,hash,lastrow->'cells',(lastrow->>'createdAt')::timestamptz,(lastrow->>'id')::uuid) returning id into next_id;
 end if;
 delete from public.ws_query_cursors where uid=p_uid and expires_at<=now();
 return jsonb_build_object('items',result,'nextCursor',next_id,'hasMore',next_id is not null,'schemaVersion',schema_num,'viewVersion',view_num,'serverTime',now());
end $$;

do $$ declare tab text;func record;begin
 foreach tab in array array['ws_views','ws_read_limits','ws_query_cursors'] loop
  execute format('alter table public.%I enable row level security',tab);execute format('revoke all on public.%I from public,anon,authenticated',tab);
 end loop;
 for func in select p.oid::regprocedure signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname like 'ws_p0_%' loop
  execute format('revoke all on function %s from public,anon,authenticated',func.signature);
 end loop;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
commit;
