-- Stars and recents follow the account, like Airtable: GET /me/marks lists them, PUT /me/marks saves one.
-- The app keeps them in its offline cache too; this copy survives sign-out and a new phone.
create table public.ws_marks(
 uid text not null references public.ws_profiles(uid) on delete cascade,
 kind text not null check(kind in ('v2-workspace-star','v2-base-star','v2-recent-base','v2-home')),
 id text not null check(id ~ '^(workspaces|bases|tables)/[0-9a-f-]{36}$'),
 json text not null check(length(json)<=2000),
 updated_at timestamptz not null default now(),
 primary key(uid,kind,id));
alter table public.ws_marks enable row level security;
revoke all on table public.ws_marks from public,anon,authenticated;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_total;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;
begin
 if not(parts[1]='me' and parts[2]='marks' and cardinality(parts)=2 and p_method in ('GET','PUT')) then
  result:=public.workspace_api_total(p_uid,p_email,p_name,p_method,p_path,p_body);
  if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,userMarks}','true');end if;
  return result;
 end if;
 perform public.workspace_api_total(p_uid,p_email,p_name,'GET','/me','{}'::jsonb);
 if p_method='PUT' then
  if jsonb_typeof(p_body->'kind') is distinct from 'string' or jsonb_typeof(p_body->'id') is distinct from 'string' or jsonb_typeof(p_body->'json') is distinct from 'string' then raise exception 'VALIDATION';end if;
  if (select count(*) from public.ws_marks where uid=p_uid)>=500 and not exists(select 1 from public.ws_marks where uid=p_uid and kind=p_body->>'kind' and id=p_body->>'id') then raise exception 'VALIDATION';end if;
  begin
   insert into public.ws_marks(uid,kind,id,json) values(p_uid,p_body->>'kind',p_body->>'id',p_body->>'json')
    on conflict(uid,kind,id) do update set json=excluded.json,updated_at=now();
  exception when check_violation then raise exception 'VALIDATION';end;
 end if;
 return jsonb_build_object('items',coalesce((select jsonb_agg(jsonb_build_object('kind',kind,'id',id,'json',json) order by updated_at desc) from public.ws_marks where uid=p_uid),'[]'));
end $$;
revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_total(text,text,text,text,text,jsonb) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb) to service_role;
