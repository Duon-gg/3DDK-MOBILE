-- Batch record create for CSV import: POST /tables/{tid}/records/batch {requestId, expectedSchemaVersion, records:[{cells}]}.
-- One batch counts as one write against the 60-writes-per-minute limit, so a 500-row import no longer stops near row 60.
-- The first record goes through the normal route (auth, role, schema version, validation, 500-row limit, rate limit);
-- the rest repeat the same validation and history event in the same transaction, so a bad row rolls back the batch.
alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_positions;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;rid uuid;tid uuid;wid uuid;previous public.ws_requests;
 item jsonb;rec public.ws_records;first jsonb;ids jsonb:='[]';n integer:=0;
begin
 if not(parts[1]='tables' and parts[3]='records' and parts[4]='batch' and cardinality(parts)=4 and p_method='POST') then
  result:=public.workspace_api_positions(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,recordBatch}','true');end if;
  return result;
 end if;
 if nullif(p_uid,'') is null or jsonb_typeof(p_body)<>'object' or jsonb_typeof(p_body->'records')<>'array'
  or jsonb_array_length(p_body->'records') not between 1 and 500 then raise exception 'VALIDATION';end if;
 rid:=(p_body->>'requestId')::uuid;if rid is null then raise exception 'VALIDATION';end if;
 -- A retried batch returns the stored answer instead of inserting again.
 select * into previous from public.ws_requests where uid=p_uid and request_id=rid;
 if found then
  if previous.path<>p_path or previous.method<>p_method or previous.body<>p_body then raise exception 'REQUEST_REUSED';end if;
  return previous.response;
 end if;
 tid:=parts[2]::uuid;
 for item in select value from jsonb_array_elements(p_body->'records') loop
  if jsonb_typeof(item)<>'object' or jsonb_typeof(item->'cells')<>'object' then raise exception 'VALIDATION';end if;
  if n=0 then
   first:=public.workspace_api_positions(p_uid,p_email,p_name,'POST','/tables/'||tid||'/records',
    jsonb_build_object('requestId',md5(rid::text||':first')::uuid,'expectedVersion',1,'expectedSchemaVersion',p_body->'expectedSchemaVersion','cells',item->'cells'));
   ids:=ids||to_jsonb(first->>'id');
   select b.workspace_id into wid from public.ws_tables t join public.ws_bases b on b.id=t.base_id where t.id=tid;
  else
   if (select count(*) from public.ws_records where table_id=tid and archived_at is null)>=500 then raise exception 'VALIDATION';end if;
   perform public.ws_p0_validate(tid,item->'cells');
   insert into public.ws_records(table_id,cells) values(tid,item->'cells') returning * into rec;
   insert into public.ws_events(workspace_id,actor_uid,path,method,entity_type,entity_id,action,request_id,before_version,after_version,changes)
    values(wid,p_uid,'/tables/'||tid||'/records','POST','RECORD',rec.id::text,'CREATED',rid,null,rec.version,public.ws_p0_delta(null,rec.cells));
   ids:=ids||to_jsonb(rec.id::text);
  end if;
  n:=n+1;
 end loop;
 result:=jsonb_build_object('created',n,'ids',ids);
 insert into public.ws_requests(uid,request_id,method,path,body,response) values(p_uid,rid,p_method,p_path,p_body,result);
 return result;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_positions(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
