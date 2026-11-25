-- The first page of POST /tables/{tid}/records/query also returns "total": how many rows match the filter and search,
-- so the table can show "50 / 300 dòng" like Airtable instead of counting only the loaded page.
alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_mentions;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;tid uuid;filters jsonb;where_sql text;total integer;
begin
 result:=public.workspace_api_mentions(p_uid,p_email,p_name,p_method,p_path,p_body);
 if parts[1]='tables' and parts[3]='records' and parts[4]='query' and cardinality(parts)=4 and p_method='POST' and p_body->>'cursor' is null then
  -- The inner query already checked access and validated filter/search; repeat its WHERE for a count.
  tid:=parts[2]::uuid;
  filters:=coalesce(p_body->'filter',(select v.filter from public.ws_views v where v.id=(p_body->>'viewId')::uuid and v.table_id=tid),'{"op":"and","conditions":[]}');
  where_sql:=public.ws_p0_filter(tid,filters,p_uid);
  if coalesce(p_body->>'search','')<>'' then where_sql:=where_sql||format(' and exists(select 1 from jsonb_each_text(r.cells) cell where strpos(lower(cell.value),lower(%L))>0)',p_body->>'search');end if;
  execute format('select count(*) from public.ws_records r where r.table_id=%L::uuid and r.archived_at is null and (%s)',tid,where_sql) into total;
  result:=result||jsonb_build_object('total',total);
 end if;
 if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,queryTotal}','true');end if;
 return result;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_mentions(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
