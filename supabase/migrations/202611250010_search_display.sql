-- Search matches what the table shows, not only stored values, like Airtable:
--  * a member's name finds rows where a User cell holds that member,
--  * a date typed as 25/11/2026 (or 25/11) finds the stored 2026-11-25.
-- ws_search_sql builds the clause once; the row query and the total count both use it.
create function public.ws_search_sql(p_term text) returns text language plpgsql stable set search_path=public as $$
declare iso text:=null;parts text[];
begin
 parts:=regexp_match(trim(p_term),'^(\d{1,2})/(\d{1,2})(?:/(\d{4}))?$');
 if parts is not null and parts[1]::int between 1 and 31 and parts[2]::int between 1 and 12 then
  iso:=case when parts[3] is null then '-'||lpad(parts[2],2,'0')||'-'||lpad(parts[1],2,'0') else parts[3]||'-'||lpad(parts[2],2,'0')||'-'||lpad(parts[1],2,'0') end;
 end if;
 return format('exists(select 1 from jsonb_each_text(r.cells) cell where strpos(lower(cell.value),lower(%L))>0'
  ||' or cell.value in (select p.uid from public.ws_profiles p where strpos(lower(p.display_name),lower(%L))>0)'
  ||case when iso is null then '' else format(' or strpos(cell.value,%L)>0',iso) end||')',p_term,p_term);
end $$;
revoke all on function public.ws_search_sql(text) from public,anon,authenticated;

create or replace function public.ws_p0_query(p_uid text,p_table uuid,p_body jsonb) returns jsonb language plpgsql as $$
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
 if search_value<>'' then where_sql:=where_sql||' and '||public.ws_search_sql(search_value);end if;
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

create or replace function public.workspace_api_total(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;tid uuid;filters jsonb;where_sql text;total integer;
begin
 result:=public.workspace_api_mentions(p_uid,p_email,p_name,p_method,p_path,p_body);
 if parts[1]='tables' and parts[3]='records' and parts[4]='query' and cardinality(parts)=4 and p_method='POST' and p_body->>'cursor' is null then
  -- The inner query already checked access and validated filter/search; repeat its WHERE for a count.
  tid:=parts[2]::uuid;
  filters:=coalesce(p_body->'filter',(select v.filter from public.ws_views v where v.id=(p_body->>'viewId')::uuid and v.table_id=tid),'{"op":"and","conditions":[]}');
  where_sql:=public.ws_p0_filter(tid,filters,p_uid);
  if coalesce(p_body->>'search','')<>'' then where_sql:=where_sql||' and '||public.ws_search_sql(p_body->>'search');end if;
  execute format('select count(*) from public.ws_records r where r.table_id=%L::uuid and r.archived_at is null and (%s)',tid,where_sql) into total;
  result:=result||jsonb_build_object('total',total);
 end if;
 if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,queryTotal}','true');end if;
 return result;
end $$;
revoke all on function public.workspace_api_total(text,text,text,text,text,jsonb) from public,anon,authenticated;
