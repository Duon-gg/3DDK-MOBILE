-- Keep field positions contiguous (0..n-1). The core appends new fields at count(*), so a gap left by a deleted
-- field produced two fields with the same position and insert left/right then moved the wrong neighbour.
-- Renumber every table once, then again after each field delete. Wraps the current workspace_api.
-- Also: changing a field to USER now requires every existing cell to be a base member (the change rolls back
-- otherwise, like other type changes), and "contains" on MULTI_SELECT searches the options themselves.
create function public.ws_renumber_fields(p_table uuid) returns void language sql security definer set search_path=public as $$
 update public.ws_fields f set position=r.n from (select id,row_number() over(order by position,id)-1 n from public.ws_fields where table_id=p_table) r
 where f.id=r.id and f.position is distinct from r.n
$$;
select public.ws_renumber_fields(id) from public.ws_tables;

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
  -- MULTI_SELECT matches inside each chosen option, not the raw JSON text (so '"' or ',' no longer match every row).
  if f.type='MULTI_SELECT' then
   return format('exists(select 1 from jsonb_array_elements_text(case when jsonb_typeof(r.cells->%L)=''array'' then r.cells->%L else ''[]''::jsonb end) x where strpos(lower(x),lower(%L))>0)',fid::text,fid::text,v#>>'{}');
  end if;
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

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_layout;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;bid uuid;old_type text;
begin
 if parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method='PATCH' then
  select type into old_type from public.ws_fields where id=parts[4]::uuid and table_id=parts[2]::uuid;
 end if;
 result:=public.workspace_api_layout(p_uid,p_email,p_name,p_method,p_path,p_body);
 -- The inner retype check passes each cell as unchanged, which skips the member check for USER fields.
 if parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method='PATCH' and result->>'type'='USER' and old_type is distinct from 'USER' then
  select base_id into bid from public.ws_tables where id=parts[2]::uuid;
  if exists(select 1 from public.ws_records r where r.table_id=parts[2]::uuid and r.archived_at is null and r.cells ? parts[4]
   and (jsonb_typeof(r.cells->parts[4])<>'string' or public.ws_base_role(bid,r.cells->>parts[4]) is null)) then raise exception 'VALIDATION';end if;
 end if;
 if parts[1]='tables' and parts[3]='fields' and cardinality(parts)=4 and p_method='DELETE' then perform public.ws_renumber_fields(parts[2]::uuid);end if;
 return result;
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_layout(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_renumber_fields(uuid) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
