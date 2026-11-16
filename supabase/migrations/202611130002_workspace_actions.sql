begin;
alter table public.ws_workspaces add column version integer not null default 1;
create or replace function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');wid uuid;bid uuid;tid uuid;role_name text;rid uuid;target_id uuid;
 previous public.ws_requests;invite public.ws_invites;rec public.ws_records;old_rec public.ws_records;field_row public.ws_fields;view_row public.ws_views;member_row public.ws_members;
 result jsonb;old_cells jsonb:='{}';new_cells jsonb;changes jsonb:='{}';before_ver integer;after_ver integer;
 name_value text:=trim(coalesce(p_body->>'name',''));field_type text;choices jsonb;pos integer;rate integer;schema_num integer;
 entity_type text;entity_id text;action_name text;query_result jsonb;config jsonb;member_version integer;
 readonly boolean:=p_method='GET' or (p_method='POST' and ((parts[1]='tables' and parts[3]='records' and parts[4]='query' and cardinality(parts)=4) or (parts[5]='history' and parts[6]='query' and cardinality(parts)=6)));
 base_row public.ws_bases; base_invite public.ws_base_invites; destination uuid; duplicate_id uuid; table_copy uuid; field_map jsonb; copy_table public.ws_tables; copy_field public.ws_fields; copy_view public.ws_views;
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
 elsif parts[1]='base-invitations' and parts[3]='accept' and cardinality(parts)=3 then
  select base_id into bid from public.ws_base_invites where id=parts[2]::uuid;select workspace_id into wid from public.ws_bases where id=bid;
 elsif parts[1]='invitations' and parts[3]='accept' and cardinality(parts)=3 then
  select workspace_id into wid from public.ws_invites where id=parts[2]::uuid;
 end if;
 if bid is not null then
  if parts[1]='bases' and parts[3]='move' and p_method='POST' then perform pg_advisory_xact_lock(hashtextextended(bid::text,41006));
  else perform pg_advisory_xact_lock_shared(hashtextextended(bid::text,41006));end if;
  select * into base_row from public.ws_bases where id=bid;wid:=base_row.workspace_id;
  if not found then raise exception 'NOT_FOUND';end if;
 end if;
 if not readonly and parts[1]='bases' and parts[3]='move' then
  destination:=(p_body->>'workspaceId')::uuid;
  if destination is null then raise exception 'VALIDATION';end if;
 end if;
 if not readonly then
  perform pg_advisory_xact_lock(hashtextextended(p_uid,41004));
  if destination is not null then
   perform pg_advisory_xact_lock(hashtextextended(least(wid::text,destination::text),41005));
   perform pg_advisory_xact_lock(hashtextextended(greatest(wid::text,destination::text),41005));
  elsif wid is not null then perform pg_advisory_xact_lock(hashtextextended(wid::text,41005));end if;
  if tid is not null then
   select schema_version into schema_num from public.ws_tables where id=tid;
   legacy_safe:=not exists(select 1 from public.ws_fields where table_id=tid and type not in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT'));
  end if;
 end if;
 if wid is not null then
  if bid is not null then role_name:=public.ws_base_role(bid,p_uid);else select role into role_name from public.ws_members where workspace_id=wid and uid=p_uid;end if;
  if role_name is null and not (parts[1] in ('invitations','base-invitations') and parts[3]='accept' and cardinality(parts)=3) then raise exception 'FORBIDDEN';end if;
  personal_write:=parts[1]='tables' and parts[3]='views' and ((p_method='POST' and cardinality(parts)=3 and p_body->>'visibility'='PERSONAL') or (p_method='PATCH' and cardinality(parts)=4 and exists(select 1 from public.ws_views where id=parts[4]::uuid and table_id=tid and visibility='PERSONAL' and owner_uid=p_uid)));
  if not readonly and role_name='VIEWER' and not personal_write and not(parts[1]='base-invitations' and parts[3]='accept' and cardinality(parts)=3) then raise exception 'FORBIDDEN';end if;
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
 if base_row.deleted_at is not null and not(parts[1]='bases' and parts[3]='restore' and cardinality(parts)=3 and p_method='POST') then raise exception 'NOT_FOUND';end if;
 if p_path='/capabilities' and p_method='GET' then
  return jsonb_build_object('apiVersion',2,'features',jsonb_build_object('atomicTemplates',true,'savedViews',true,'pagedRecords',true,'userFields',true,'cellPatch',true,'memberAdmin',true,'history',true,'baseActions',true,'workspaceActions',true),'limits',jsonb_build_object('recordsPerTable',500,'fieldsPerTable',30,'pageSize',100));
 elsif p_path='/shared-bases' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',b.id,'name',b.name,'workspaceId',b.workspace_id,'role',public.ws_base_role(b.id,p_uid)) order by b.name,b.id),'[]') into result from public.ws_bases b join public.ws_base_members m on m.base_id=b.id where m.uid=p_uid and b.deleted_at is null;
 elsif p_path='/trash' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',b.id,'name',b.name,'workspaceId',b.workspace_id,'version',b.version,'deletedAt',b.deleted_at) order by b.deleted_at desc),'[]') into result from public.ws_bases b join public.ws_members m on m.workspace_id=b.workspace_id where m.uid=p_uid and m.role='OWNER' and b.deleted_at is not null;
 elsif p_path='/base-invitations' and p_method='GET' then
  select coalesce(jsonb_agg(jsonb_build_object('id',i.id,'name',b.name,'role',i.role) order by i.expires_at),'[]') into result from public.ws_base_invites i join public.ws_bases b on b.id=i.base_id where i.email=lower(trim(p_email)) and i.revoked_at is null and i.accepted_at is null and i.expires_at>now() and b.deleted_at is null;
 elsif parts[1]='base-invitations' and parts[3]='accept' and cardinality(parts)=3 and p_method='POST' then
  select * into base_invite from public.ws_base_invites where id=parts[2]::uuid for update;
  if not found then raise exception 'NOT_FOUND';end if;
  if base_invite.email<>lower(trim(p_email)) then raise exception 'FORBIDDEN';end if;
  if base_invite.revoked_at is not null or base_invite.accepted_at is not null or base_invite.expires_at<=now() then raise exception 'INVALID_STATE';end if;
  insert into public.ws_base_members(base_id,uid,role) values(base_invite.base_id,p_uid,base_invite.role) on conflict(base_id,uid) do update set role=case when ws_base_members.role='EDITOR' then 'EDITOR' else excluded.role end;
  update public.ws_base_invites set accepted_at=now() where id=base_invite.id;
  update public.ws_bases set version=version+1 where id=bid;
  result:=jsonb_build_object('id',bid);entity_type:='BASE_MEMBER';entity_id:=p_uid;
 elsif parts[1]='bases' and parts[3]='sharing' and cardinality(parts)=3 and p_method='GET' then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select jsonb_build_object('version',b.version,'members',(select coalesce(jsonb_agg(jsonb_build_object('uid',m.uid,'name',p.display_name,'email',p.email,'role',m.role)),'[]') from public.ws_base_members m join public.ws_profiles p on p.uid=m.uid where m.base_id=bid),
   'invites',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'email',email,'role',role)),'[]') from public.ws_base_invites where base_id=bid and accepted_at is null and revoked_at is null and expires_at>now())) into result from public.ws_bases b where b.id=bid;
 elsif parts[1]='bases' and ((parts[3] in ('move','duplicate','restore','invites','members') and cardinality(parts) between 3 and 4) or (cardinality(parts)=2 and p_method='DELETE')) then
  if role_name<>'OWNER' then raise exception 'FORBIDDEN';end if;
  select * into base_row from public.ws_bases where id=bid for update;
  if base_row.version is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
  before_ver:=base_row.version;entity_type:='BASE';entity_id:=bid::text;
  if parts[3]='invites' and cardinality(parts)=3 and p_method='POST' then
   if length(coalesce(p_body->>'email',''))>254 or coalesce(p_body->>'email','') !~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' or p_body->>'role' is null or p_body->>'role' not in ('EDITOR','VIEWER') then raise exception 'VALIDATION';end if;
   if (select count(*) from public.ws_base_invites where base_id=bid and accepted_at is null and revoked_at is null and expires_at>now())>=100 then raise exception 'VALIDATION';end if;
   update public.ws_base_invites set revoked_at=now() where base_id=bid and email=lower(trim(p_body->>'email')) and accepted_at is null and revoked_at is null;
   insert into public.ws_base_invites(base_id,email,role) values(bid,lower(trim(p_body->>'email')),p_body->>'role') returning * into base_invite;
   result:=jsonb_build_object('id',base_invite.id);action_name:='BASE_INVITED';
  elsif parts[3]='invites' and cardinality(parts)=4 and p_method='DELETE' then
   update public.ws_base_invites set revoked_at=now() where id=parts[4]::uuid and base_id=bid;if not found then raise exception 'NOT_FOUND';end if;
   result:='{}';action_name:='BASE_INVITE_REVOKED';
  elsif parts[3]='members' and cardinality(parts)=4 and p_method in ('PATCH','DELETE') then
   if p_method='DELETE' then delete from public.ws_base_members where base_id=bid and uid=parts[4];
   else
    if p_body->>'role' is null or p_body->>'role' not in ('EDITOR','VIEWER') then raise exception 'VALIDATION';end if;
    update public.ws_base_members set role=p_body->>'role' where base_id=bid and uid=parts[4];
   end if;
   if not found then raise exception 'NOT_FOUND';end if;result:='{}';action_name:='BASE_ACCESS_CHANGED';
  elsif parts[3]='move' and cardinality(parts)=3 and p_method='POST' then
   if not exists(select 1 from public.ws_members where workspace_id=destination and uid=p_uid and role='OWNER') then raise exception 'FORBIDDEN';end if;
   if destination=wid then raise exception 'VALIDATION';end if;
   if (select count(*) from public.ws_bases where workspace_id=destination)>=50 then raise exception 'VALIDATION';end if;
   update public.ws_bases set workspace_id=destination where id=bid;
   -- Old assignments remain readable; assigning a different person requires current access.
   delete from public.ws_query_cursors where table_id in(select id from public.ws_tables where base_id=bid);
   changes:=jsonb_build_object('fromWorkspace',wid,'toWorkspace',destination,'directSharesPreserved',true);
   result:=jsonb_build_object('id',bid,'workspaceId',destination);action_name:='BASE_MOVED';
  elsif parts[3]='duplicate' and cardinality(parts)=3 and p_method='POST' then
   if length(name_value) not between 1 and 100 or (select count(*) from public.ws_bases where workspace_id=wid)>=50 then raise exception 'VALIDATION';end if;
   insert into public.ws_bases(workspace_id,name) values(wid,name_value) returning id into duplicate_id;
   for copy_table in select * from public.ws_tables where base_id=bid loop
    insert into public.ws_tables(base_id,name) values(duplicate_id,copy_table.name) returning id into table_copy;
    field_map:='{}';
    for copy_field in select * from public.ws_fields where table_id=copy_table.id order by position,id loop
     insert into public.ws_fields(table_id,name,type,options,position,system_key) values(table_copy,copy_field.name,copy_field.type,copy_field.options,copy_field.position,copy_field.system_key) returning id into target_id;
     field_map:=field_map||jsonb_build_object(copy_field.id::text,target_id::text);
    end loop;
    insert into public.ws_records(table_id,cells) select table_copy,public.ws_base_remap(cells,field_map) from public.ws_records where table_id=copy_table.id and archived_at is null;
    for copy_view in select * from public.ws_views where table_id=copy_table.id and (visibility='SHARED' or owner_uid=p_uid) loop
     insert into public.ws_views(table_id,name,type,visibility,owner_uid,filter,sort,field_ids,field_order,is_default) values(table_copy,copy_view.name,copy_view.type,copy_view.visibility,p_uid,public.ws_base_remap_config(copy_view.filter,field_map),public.ws_base_remap_config(copy_view.sort,field_map),public.ws_base_remap_config(copy_view.field_ids,field_map),public.ws_base_remap_config(copy_view.field_order,field_map),copy_view.is_default);
    end loop;
   end loop;
   result:=jsonb_build_object('id',duplicate_id,'name',name_value,'workspaceId',wid,'version',1);action_name:='BASE_DUPLICATED';changes:=jsonb_build_object('copyId',duplicate_id);
  elsif cardinality(parts)=2 and p_method='DELETE' then
   update public.ws_bases set deleted_at=now() where id=bid;result:=jsonb_build_object('id',bid);action_name:='BASE_DELETED';
  elsif parts[3]='restore' and cardinality(parts)=3 and p_method='POST' then
   if base_row.deleted_at is null then raise exception 'INVALID_STATE';end if;
   update public.ws_bases set deleted_at=null where id=bid;result:=jsonb_build_object('id',bid);action_name:='BASE_RESTORED';
  else raise exception 'NOT_FOUND';end if;
  if action_name<>'BASE_DUPLICATED' then
   update public.ws_bases set version=version+1 where id=bid returning version into after_ver;
   result:=result||jsonb_build_object('version',after_ver);
  end if;

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
 elsif parts[1]='workspaces' and cardinality(parts)=2 and p_method='PATCH' then
  if role_name is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
  if length(name_value) not between 1 and 100 then raise exception 'VALIDATION';end if;
  select version into before_ver from public.ws_workspaces where id=wid for update;
  if before_ver is distinct from (p_body->>'expectedVersion')::integer then raise exception 'CONFLICT';end if;
  select jsonb_build_object('name',jsonb_build_object('before',name,'after',name_value)) into changes from public.ws_workspaces where id=wid;
  update public.ws_workspaces set name=name_value,version=version+1 where id=wid returning version into after_ver;
  result:=jsonb_build_object('id',wid,'name',name_value,'version',after_ver);entity_type:='WORKSPACE';entity_id:=wid::text;action_name:='WORKSPACE_RENAMED';
 elsif parts[1]='workspaces' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',w.id,'name',w.name,'role',role_name,'version',w.version,
   'bases',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_bases where workspace_id=wid and deleted_at is null),
   'members',(select coalesce(jsonb_agg(jsonb_build_object('uid',m.uid,'displayName',p.display_name,'role',m.role,'version',m.version) order by p.display_name,m.uid),'[]') from public.ws_members m join public.ws_profiles p on p.uid=m.uid where m.workspace_id=wid),
   'invites',case when role_name='OWNER' then (select coalesce(jsonb_agg(jsonb_build_object('id',id,'email',email,'role',role)),'[]') from public.ws_invites where workspace_id=wid and accepted_at is null and revoked_at is null and expires_at>now()) else '[]'::jsonb end) into result from public.ws_workspaces w where w.id=wid;
 elsif parts[1]='workspaces' and parts[3]='bases' and cardinality(parts)=3 and p_method='POST' then
  if (select count(*) from public.ws_bases where workspace_id=wid and deleted_at is null)>=50 then raise exception 'VALIDATION';end if;
  insert into public.ws_bases(workspace_id,name) values(wid,name_value) returning id into bid;result:=jsonb_build_object('id',bid,'name',name_value);entity_type:='BASE';entity_id:=bid::text;
 elsif parts[1]='bases' and cardinality(parts)=2 and p_method='GET' then
  select jsonb_build_object('id',b.id,'name',b.name,'role',role_name,'workspaceId',wid,'version',b.version,'tables',(select coalesce(jsonb_agg(jsonb_build_object('id',id,'name',name) order by name,id),'[]') from public.ws_tables where base_id=bid)) into result from public.ws_bases b where b.id=bid;
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
   'members',public.ws_base_people(bid)) into result from public.ws_tables t where t.id=tid;
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
   (select ev.sequence seq,jsonb_build_object('id',ev.id,'action',coalesce(ev.action,ev.method),'actorUid',ev.actor_uid,'actorName',coalesce(p.display_name,ev.actor_uid),'createdAt',ev.created_at,'beforeVersion',ev.before_version,'afterVersion',ev.after_version,'changes',ev.changes) payload from public.ws_events ev left join public.ws_profiles p on p.uid=ev.actor_uid where ((parts[1]='tables' and ev.entity_type='RECORD' and ev.entity_id=parts[4]) or (parts[1]='workspaces' and ev.workspace_id=wid)) and (p_body->>'cursor' is null or ev.sequence<(p_body->>'cursor')::bigint) order by ev.sequence desc limit 50) e;
 else raise exception 'NOT_FOUND';end if;
 if not readonly then
  insert into public.ws_requests(uid,request_id,method,path,body,response) values(p_uid,rid,p_method,p_path,p_body,result);
  if wid is not null then insert into public.ws_events(workspace_id,actor_uid,path,method,entity_type,entity_id,action,request_id,before_version,after_version,changes) values(wid,p_uid,p_path,p_method,entity_type,entity_id,coalesce(action_name,p_method),rid,before_ver,after_ver,changes);end if;
 end if;
 return result;
end $$;

commit;
