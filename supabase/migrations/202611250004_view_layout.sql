-- Airtable-style view layout: grid/cards/Kanban, group-by field and Kanban stack field are saved on the view,
-- so everyone who opens the view sees the same layout. POST/PATCH /tables/{tid}/views accept "layout".
-- Wraps the current workspace_api; the core still does auth, permissions and expectedVersion for views.
alter table public.ws_views add column layout jsonb;

create or replace function public.ws_p0_view_json(v public.ws_views) returns jsonb language sql stable as $$
 select jsonb_build_object('id',v.id,'name',v.name,'type',v.type,'visibility',v.visibility,'ownerUid',v.owner_uid,'version',v.version,'filter',v.filter,'sort',v.sort,'group','[]'::jsonb,'fieldIds',v.field_ids,'fieldOrder',v.field_order,'isDefault',v.is_default,'layout',v.layout)
$$;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_comments;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare
 parts text[]:=string_to_array(trim(both '/' from p_path),'/');new_layout jsonb;result jsonb;tid uuid;
begin
 if not(parts[1]='tables' and parts[3]='views' and ((cardinality(parts)=3 and p_method='POST') or (cardinality(parts)=4 and p_method='PATCH'))
  and jsonb_typeof(p_body)='object' and p_body ? 'layout') then
  result:=public.workspace_api_comments(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,viewLayout}','true');end if;
  return result;
 end if;
 tid:=parts[2]::uuid;new_layout:=p_body->'layout';
 -- {"type":"GRID|CARDS|KANBAN","groupFieldId":uuid|null,"kanbanFieldId":uuid|null}; field ids must belong to this table.
 if jsonb_typeof(new_layout)<>'object' or coalesce(new_layout->>'type','') not in ('GRID','CARDS','KANBAN')
  or exists(select 1 from jsonb_object_keys(new_layout) k where k not in ('type','groupFieldId','kanbanFieldId'))
  or exists(select 1 from unnest(array['groupFieldId','kanbanFieldId']) k where jsonb_typeof(new_layout->k) not in ('null','string'))
  or exists(select 1 from unnest(array[new_layout->>'groupFieldId',new_layout->>'kanbanFieldId']) x where x is not null and not exists(select 1 from public.ws_fields f where f.table_id=tid and f.id::text=x))
  or (new_layout->>'type'='KANBAN' and not exists(select 1 from public.ws_fields f where f.table_id=tid and f.id::text=new_layout->>'kanbanFieldId' and f.type='SELECT'))
 then raise exception 'VALIDATION';end if;
 new_layout:=jsonb_build_object('type',new_layout->>'type','groupFieldId',new_layout->'groupFieldId','kanbanFieldId',new_layout->'kanbanFieldId');
 result:=public.workspace_api_comments(p_uid,p_email,p_name,p_method,p_path,p_body-'layout');
 update public.ws_views set layout=jsonb_strip_nulls(new_layout) where id=(result->>'id')::uuid and table_id=tid;
 return result||jsonb_build_object('layout',jsonb_strip_nulls(new_layout));
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_comments(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
