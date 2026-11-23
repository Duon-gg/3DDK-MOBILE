-- Field edit/delete: PATCH and DELETE /tables/{tid}/fields/{fid}.
-- Wraps the existing workspace_api instead of copying its body; every other route is delegated unchanged.
alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_core;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');tid uuid;bid uuid;wid uuid;fid uuid;f public.ws_fields;schema_num integer;
 name_value text;new_type text;choices jsonb;r public.ws_records;result jsonb;
begin
 if not(parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method in ('PATCH','DELETE')) then
  result:=public.workspace_api_core(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,fieldEdit}','true');end if;
  return result;
 end if;
 if nullif(p_uid,'') is null or jsonb_typeof(p_body)<>'object' then raise exception 'UNAUTHORIZED';end if;
 tid:=parts[2]::uuid;fid:=parts[4]::uuid;
 select t.base_id,b.workspace_id into bid,wid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;
 if bid is null then raise exception 'NOT_FOUND';end if;
 perform pg_advisory_xact_lock_shared(hashtextextended(bid::text,41006));
 perform pg_advisory_xact_lock(hashtextextended(wid::text,41005));
 if public.ws_base_role(bid,p_uid) is distinct from 'OWNER' then raise exception 'FORBIDDEN';end if;
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
 if new_type not in ('TEXT','NUMBER','DATE','CHECKBOX','SELECT','USER') then raise exception 'UNSUPPORTED_FIELD';end if;
 if new_type<>f.type and f.system_key is not null then raise exception 'VALIDATION';end if;
 if jsonb_typeof(choices)<>'array' or jsonb_array_length(choices)>30 then raise exception 'VALIDATION';end if;
 if new_type='SELECT' and (jsonb_array_length(choices)=0 or exists(select 1 from jsonb_array_elements(choices) x where jsonb_typeof(x)<>'string' or length(trim(x#>>'{}')) not between 1 and 100) or (select count(distinct x) from jsonb_array_elements(choices) x)<>jsonb_array_length(choices)) then raise exception 'VALIDATION';end if;
 if new_type<>'SELECT' then choices:='[]';end if;
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
