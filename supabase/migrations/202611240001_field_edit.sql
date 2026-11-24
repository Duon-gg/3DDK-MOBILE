-- Airtable parity for tables: field edit/delete, richer field types, filters on them, and record comments.
-- workspace_api is wrapped instead of copied; every other route is delegated to workspace_api_core unchanged.
alter table public.ws_fields drop constraint ws_fields_type_check;
alter table public.ws_fields add constraint ws_fields_type_check check(type in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER','LONG_TEXT','MULTI_SELECT','URL','EMAIL','PHONE'));

create table public.ws_comments(
 id uuid primary key default gen_random_uuid(),
 record_id uuid not null references public.ws_records(id) on delete cascade,
 uid text not null references public.ws_profiles(uid),
 body text not null check(length(trim(body)) between 1 and 2000),
 mentions jsonb not null default '[]',
 created_at timestamptz not null default now());
create index ws_comments_record on public.ws_comments(record_id,created_at,id);
alter table public.ws_comments enable row level security;

-- Options of SELECT/MULTI_SELECT: 1-30 unique non-blank strings up to 100 chars.
create function public.ws_valid_choices(p jsonb) returns boolean language sql immutable as $$
 select jsonb_typeof(p)='array' and jsonb_array_length(p) between 1 and 30
  and not exists(select 1 from jsonb_array_elements(p) x where jsonb_typeof(x)<>'string' or length(trim(x#>>'{}')) not between 1 and 100)
  and (select count(distinct x) from jsonb_array_elements(p) x)=jsonb_array_length(p)
$$;

create or replace function public.ws_p0_validate(p_table uuid,p_cells jsonb,p_before jsonb default '{}') returns void language plpgsql as $$
declare f public.ws_fields;k text;v jsonb;s text;
begin
 if p_cells is null or jsonb_typeof(p_cells)<>'object' or pg_column_size(p_cells)>24000 then raise exception 'VALIDATION';end if;
 for k,v in select * from jsonb_each(p_cells) loop
  select * into f from public.ws_fields where table_id=p_table and id::text=k;
  if not found then raise exception 'VALIDATION';end if;
  if v='null'::jsonb then continue;end if;
  if f.type='MULTI_SELECT' then
   if jsonb_typeof(v)<>'array' or jsonb_array_length(v)>30 or not (f.options @> v) or exists(select 1 from jsonb_array_elements(v) x where jsonb_typeof(x)<>'string') then raise exception 'VALIDATION';end if;
   continue;
  end if;
  if f.type not in ('NUMBER','CHECKBOX') and jsonb_typeof(v)<>'string' then raise exception 'VALIDATION';end if;
  s:=v#>>'{}';
  if f.type='USER' then
   if length(s)>128 then raise exception 'VALIDATION';end if;
   if v is distinct from p_before->k and public.ws_base_role((select base_id from public.ws_tables where id=p_table),s) is null then raise exception 'VALIDATION';end if;
  elsif f.type='TEXT' then if length(s)>5000 then raise exception 'VALIDATION';end if;
  elsif f.type='LONG_TEXT' then if length(s)>20000 then raise exception 'VALIDATION';end if;
  elsif f.type='URL' then if length(s)>2000 or s !~* '^https?://\S+$' then raise exception 'VALIDATION';end if;
  elsif f.type='EMAIL' then if length(s)>254 or s !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'VALIDATION';end if;
  elsif f.type='PHONE' then if s !~ '^[+0-9 ().-]{3,40}$' then raise exception 'VALIDATION';end if;
  elsif f.type='NUMBER' then if jsonb_typeof(v)<>'number' then raise exception 'VALIDATION';end if;
  elsif f.type='CHECKBOX' then if jsonb_typeof(v)<>'boolean' then raise exception 'VALIDATION';end if;
  elsif f.type='SELECT' then if not (f.options @> jsonb_build_array(v)) then raise exception 'VALIDATION';end if;
  elsif f.type='DATE' then
   if s !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION';end if;
   begin perform s::date;exception when others then raise exception 'VALIDATION';end;
  else raise exception 'UNSUPPORTED_FIELD';end if;
 end loop;
 -- Existing generic blank tables remain valid; the TASKS template requires its title.
 select * into f from public.ws_fields where table_id=p_table and system_key='title';
 if found and (jsonb_typeof(p_cells->f.id::text) is distinct from 'string' or length(trim(coalesce(p_cells->>f.id::text,'')))=0) then raise exception 'VALIDATION';end if;
end $$;

-- Filters: text-like new types behave like TEXT; MULTI_SELECT supports contains/isEmpty.
create or replace function public.ws_p0_filter(p_table uuid,p_spec jsonb,p_uid text,p_depth integer default 0) returns text language plpgsql as $$
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
   if (f.type in ('TEXT','SELECT','USER','DATE','LONG_TEXT','URL','EMAIL','PHONE') and jsonb_typeof(v)<>'string') or (f.type='NUMBER' and jsonb_typeof(v)<>'number') or (f.type='CHECKBOX' and jsonb_typeof(v)<>'boolean') then raise exception 'VALIDATION';end if;
   if f.type='SELECT' and not f.options @> jsonb_build_array(v) then raise exception 'VALIDATION';end if;
   if f.type='DATE' then
    if (v#>>'{}') !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'VALIDATION';end if;
    begin perform (v#>>'{}')::date;exception when others then raise exception 'VALIDATION';end;
   end if;
  end if;
  return format('coalesce(%s,''null''::jsonb) %s %L::jsonb',expr,case when op='eq' then '=' else '<>' end,v::text);
 end if;
 if op='contains' then
  if f.type not in ('TEXT','SELECT','LONG_TEXT','URL','EMAIL','PHONE','MULTI_SELECT') or jsonb_typeof(v)<>'string' then raise exception 'VALIDATION';end if;
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

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_core;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');tid uuid;bid uuid;wid uuid;fid uuid;rid uuid;f public.ws_fields;schema_num integer;
 name_value text;new_type text;choices jsonb;r public.ws_records;result jsonb;role_name text;
 new_types constant text[]:=array['LONG_TEXT','MULTI_SELECT','URL','EMAIL','PHONE'];
 all_types constant text[]:=array['TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER','LONG_TEXT','MULTI_SELECT','URL','EMAIL','PHONE'];
begin
 -- New field types: the core only knows the original six, so create as TEXT and retype in the same transaction.
 if parts[1]='tables' and parts[3]='fields' and cardinality(parts)=3 and p_method='POST' and p_body->>'type'=any(new_types) then
  new_type:=p_body->>'type';choices:=coalesce(p_body->'options','[]');
  if new_type='MULTI_SELECT' then if not public.ws_valid_choices(choices) then raise exception 'VALIDATION';end if;else choices:='[]';end if;
  result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,p_body||jsonb_build_object('type','TEXT','options',choices));
  update public.ws_fields set type=new_type where id=(result->>'id')::uuid;
  return result||jsonb_build_object('type',new_type);
 end if;
 if not((parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method in ('PATCH','DELETE'))
  or (parts[1]='tables' and parts[3]='records' and parts[5]='comments' and cardinality(parts)=5 and p_method in ('GET','POST'))) then
  result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(jsonb_set(jsonb_set(result,'{features,fieldEdit}','true'),'{features,richFields}','true'),'{features,comments}','true');end if;
  return result;
 end if;
 if nullif(p_uid,'') is null or jsonb_typeof(p_body)<>'object' then raise exception 'UNAUTHORIZED';end if;
 tid:=parts[2]::uuid;
 select t.base_id,b.workspace_id into bid,wid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;
 if bid is null then raise exception 'NOT_FOUND';end if;
 role_name:=public.ws_base_role(bid,p_uid);
 if role_name is null then raise exception 'FORBIDDEN';end if;

 if parts[3]='records' then
  rid:=parts[4]::uuid;
  if not exists(select 1 from public.ws_records where id=rid and table_id=tid and archived_at is null) then raise exception 'NOT_FOUND';end if;
  if p_method='POST' then
   -- Every base member may comment, like the Airtable commenter level; mentions must be base members too.
   choices:=coalesce(p_body->'mentions','[]');
   if jsonb_typeof(choices)<>'array' or jsonb_array_length(choices)>20
    or exists(select 1 from jsonb_array_elements(choices) x where jsonb_typeof(x)<>'string' or public.ws_base_role(bid,x#>>'{}') is null)
    or length(trim(coalesce(p_body->>'body',''))) not between 1 and 2000 then raise exception 'VALIDATION';end if;
   if (select count(*) from public.ws_comments where record_id=rid)>=500 then raise exception 'VALIDATION';end if;
   insert into public.ws_comments(record_id,uid,body,mentions) values(rid,p_uid,trim(p_body->>'body'),choices);
  end if;
  return jsonb_build_object('comments',(select coalesce(jsonb_agg(jsonb_build_object('id',x.id,'uid',x.uid,'displayName',p.display_name,'body',x.body,'mentions',x.mentions,'createdAt',x.created_at) order by x.created_at,x.id),'[]')
   from public.ws_comments x join public.ws_profiles p on p.uid=x.uid where x.record_id=rid));
 end if;

 fid:=parts[4]::uuid;
 perform pg_advisory_xact_lock_shared(hashtextextended(bid::text,41006));
 perform pg_advisory_xact_lock(hashtextextended(wid::text,41005));
 if role_name is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
 select schema_version into schema_num from public.ws_tables where id=tid for update;
 if p_body ? 'expectedSchemaVersion' and schema_num is distinct from (p_body->>'expectedSchemaVersion')::integer then raise exception 'SCHEMA_CHANGED';end if;
 select * into f from public.ws_fields where id=fid and table_id=tid;if not found then raise exception 'NOT_FOUND';end if;

 if p_method='DELETE' then
  if f.system_key is not null or (select count(*) from public.ws_fields where table_id=tid)<=1 then raise exception 'VALIDATION';end if;
  delete from public.ws_fields where id=fid;
  update public.ws_records set cells=cells-fid::text,version=version+1 where table_id=tid and cells ? fid::text;
  -- Views drop the column and any filter/sort that used it, so saved queries stay valid.
  update public.ws_views set
   field_ids=coalesce((select jsonb_agg(x) from jsonb_array_elements(field_ids) x where x#>>'{}'<>fid::text),'[]'),
   field_order=coalesce((select jsonb_agg(x) from jsonb_array_elements(field_order) x where x#>>'{}'<>fid::text),'[]'),
   sort=coalesce((select jsonb_agg(x) from jsonb_array_elements(sort) x where x->>'fieldId' is distinct from fid::text),'[]'),
   filter=case when filter::text like '%'||fid::text||'%' then '{"op":"and","conditions":[]}'::jsonb else filter end,
   version=version+1
  where table_id=tid;
  update public.ws_tables set schema_version=schema_version+1,version=version+1 where id=tid returning schema_version into schema_num;
  return jsonb_build_object('id',fid,'deleted',true,'schemaVersion',schema_num);
 end if;

 name_value:=coalesce(nullif(trim(p_body->>'name'),''),f.name);
 new_type:=coalesce(p_body->>'type',f.type);
 choices:=coalesce(p_body->'options',f.options);
 if length(name_value)>100 or exists(select 1 from public.ws_fields where table_id=tid and id<>fid and lower(name)=lower(name_value)) then raise exception 'VALIDATION';end if;
 if not new_type=any(all_types) then raise exception 'UNSUPPORTED_FIELD';end if;
 if new_type<>f.type and f.system_key is not null then raise exception 'VALIDATION';end if;
 if new_type in ('SELECT','MULTI_SELECT') then if not public.ws_valid_choices(choices) then raise exception 'VALIDATION';end if;else choices:='[]';end if;
 update public.ws_fields set name=name_value,type=new_type,options=choices,version=version+1 where id=fid;
 -- Existing cells must still be valid under the new type/options; otherwise the whole change rolls back.
 for r in select * from public.ws_records where table_id=tid and archived_at is null and cells ? fid::text loop
  perform public.ws_p0_validate(tid,jsonb_build_object(fid::text,r.cells->fid::text),jsonb_build_object(fid::text,r.cells->fid::text));
 end loop;
 update public.ws_tables set schema_version=schema_version+1,version=version+1 where id=tid returning schema_version into schema_num;
 return jsonb_build_object('id',fid,'name',name_value,'type',new_type,'options',choices,'schemaVersion',schema_num);
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_core(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_valid_choices(jsonb) from public,anon,authenticated;
revoke all on table public.ws_comments from public,anon,authenticated;
