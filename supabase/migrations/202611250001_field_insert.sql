-- Insert left/right like Airtable: POST /tables/{tid}/fields accepts an optional "position" and shifts later fields.
-- Replaces the workspace_api wrapper from 202611240001; everything else is unchanged.
create or replace function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');tid uuid;bid uuid;wid uuid;fid uuid;rid uuid;f public.ws_fields;schema_num integer;
 name_value text;new_type text;choices jsonb;r public.ws_records;result jsonb;role_name text;pos integer;
 new_types constant text[]:=array['LONG_TEXT','MULTI_SELECT','URL','EMAIL','PHONE'];
 all_types constant text[]:=array['TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER','LONG_TEXT','MULTI_SELECT','URL','EMAIL','PHONE'];
begin
 -- New field types: the core only knows the original six, so create as TEXT and retype in the same transaction.
 if parts[1]='tables' and parts[3]='fields' and cardinality(parts)=3 and p_method='POST' then
  if p_body ? 'position' and (jsonb_typeof(p_body->'position')<>'number' or (p_body->>'position')::numeric not between 0 and 1000) then raise exception 'VALIDATION';end if;
  new_type:=p_body->>'type';
  if new_type=any(new_types) then
   choices:=coalesce(p_body->'options','[]');
   if new_type='MULTI_SELECT' then if not public.ws_valid_choices(choices) then raise exception 'VALIDATION';end if;else choices:='[]';end if;
   result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,(p_body-'position')||jsonb_build_object('type','TEXT','options',choices));
   update public.ws_fields set type=new_type where id=(result->>'id')::uuid;
   result:=result||jsonb_build_object('type',new_type);
  else
   result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,p_body-'position');
  end if;
  -- Insert left/right: the core appends; move the new field to the requested slot and push later fields right.
  if p_body ? 'position' then
   tid:=parts[2]::uuid;fid:=(result->>'id')::uuid;pos:=(p_body->>'position')::integer;
   update public.ws_fields set position=position+1 where table_id=tid and position>=pos and id<>fid;
   update public.ws_fields set position=pos where id=fid;
   result:=result||jsonb_build_object('position',pos);
  end if;
  return result;
 end if;
 if not((parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method in ('PATCH','DELETE'))
  or (parts[1]='tables' and parts[3]='records' and parts[5]='comments' and cardinality(parts)=5 and p_method in ('GET','POST'))) then
  result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(jsonb_set(jsonb_set(jsonb_set(result,'{features,fieldEdit}','true'),'{features,richFields}','true'),'{features,comments}','true'),'{features,fieldInsert}','true');end if;
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
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
