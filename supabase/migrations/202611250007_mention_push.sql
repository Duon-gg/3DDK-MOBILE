-- Push notifications for @mentions in record comments.
-- PUT/DELETE /devices/{installation} registers this phone's FCM token for the signed-in user.
-- A comment that mentions someone queues a notification; the dispatch function claims it and sends it to their phones.
-- The older push tables serve only the retired v1 task screens.
create table public.ws_devices(
 uid text not null references public.ws_profiles(uid) on delete cascade,
 installation text not null check(length(installation) between 8 and 100),
 token text not null unique check(length(token) between 20 and 4096),
 updated_at timestamptz not null default now(),
 primary key(uid,installation));
create table public.ws_notifications(
 id uuid primary key default gen_random_uuid(),
 uid text not null references public.ws_profiles(uid) on delete cascade,
 actor_uid text not null,
 table_id uuid not null references public.ws_tables(id) on delete cascade,
 record_id uuid not null references public.ws_records(id) on delete cascade,
 title text not null,
 body text not null,
 created_at timestamptz not null default now(),
 sent_at timestamptz,
 attempts integer not null default 0,
 next_attempt_at timestamptz not null default now(),
 claim_token uuid);
create index ws_notifications_due on public.ws_notifications(next_attempt_at) where sent_at is null;
alter table public.ws_devices enable row level security;
alter table public.ws_notifications enable row level security;
revoke all on table public.ws_devices,public.ws_notifications from public,anon,authenticated;

-- Queue one notification per newly mentioned member (never the author).
create function public.ws_queue_mentions(p_comment uuid,p_already jsonb) returns void language plpgsql security definer set search_path=public as $$
declare c public.ws_comments;tid uuid;bid uuid;tname text;actor text;target text;
begin
 select * into c from public.ws_comments where id=p_comment;if not found then return;end if;
 select r.table_id,t.base_id,t.name into tid,bid,tname from public.ws_records r join public.ws_tables t on t.id=r.table_id where r.id=c.record_id;
 select coalesce(nullif(display_name,''),uid) into actor from public.ws_profiles where uid=c.uid;
 for target in select distinct x from jsonb_array_elements_text(c.mentions) x where x<>c.uid and not coalesce(p_already,'[]') ? x loop
  if public.ws_base_role(bid,target) is not null then
   insert into public.ws_notifications(uid,actor_uid,table_id,record_id,title,body)
   values(target,c.uid,tid,c.record_id,actor||' đã nhắc bạn trong '||tname,left(c.body,200));
  end if;
 end loop;
end $$;

alter function public.workspace_api(text,text,text,text,text,jsonb) rename to workspace_api_batch;

create function public.workspace_api(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path=public as $$
declare parts text[]:=string_to_array(trim(both '/' from p_path),'/');result jsonb;cid uuid;before jsonb;
begin
 if parts[1]='devices' and cardinality(parts)=2 and p_method in ('PUT','DELETE') then
  -- Same auth gate and profile upsert as every other route.
  perform public.workspace_api_batch(p_uid,p_email,p_name,'GET','/me','{}'::jsonb);
  if length(parts[2]) not between 8 and 100 then raise exception 'VALIDATION';end if;
  if p_method='DELETE' then delete from public.ws_devices where uid=p_uid and installation=parts[2];return jsonb_build_object('deleted',true);end if;
  if jsonb_typeof(p_body->'token')<>'string' or length(p_body->>'token') not between 20 and 4096 then raise exception 'VALIDATION';end if;
  -- A token belongs to one phone: it moves to whoever signed in on it last.
  delete from public.ws_devices where token=p_body->>'token' and not(uid=p_uid and installation=parts[2]);
  insert into public.ws_devices(uid,installation,token) values(p_uid,parts[2],p_body->>'token')
   on conflict(uid,installation) do update set token=excluded.token,updated_at=now();
  return jsonb_build_object('registered',true);
 end if;
 if parts[1]='tables' and parts[3]='records' and parts[5]='comments' and cardinality(parts)=6 and p_method='PATCH' then
  select mentions into before from public.ws_comments where id=parts[6]::uuid;
 end if;
 result:=public.workspace_api_batch(p_uid,p_email,p_name,p_method,p_path,p_body);
 if parts[1]='tables' and parts[3]='records' and parts[5]='comments' and p_method in ('POST','PATCH') then
  if cardinality(parts)=5 and p_method='POST' then
   select id into cid from public.ws_comments where record_id=parts[4]::uuid and uid=p_uid order by created_at desc,id desc limit 1;
   perform public.ws_queue_mentions(cid,'[]');
  elsif cardinality(parts)=6 then perform public.ws_queue_mentions(parts[6]::uuid,before);
  end if;
 end if;
 if p_path='/capabilities' and p_method='GET' then result:=jsonb_set(result,'{features,mentionPush}','true');end if;
 return result;
end $$;

-- Dispatcher side: lease one due notification with the recipient's tokens; mark it sent or back off.
create function public.claim_ws_notification() returns table(notification_id uuid,uid text,table_id uuid,record_id uuid,title text,body text,claim_token uuid,tokens jsonb)
language plpgsql security definer set search_path=public as $$
#variable_conflict use_column
begin
 return query with picked as (
  select n.id from public.ws_notifications n where n.sent_at is null and n.next_attempt_at<=now() and n.attempts<8 order by n.next_attempt_at limit 1 for update skip locked
 ), claimed as (
  update public.ws_notifications n set claim_token=gen_random_uuid(),attempts=n.attempts+1,next_attempt_at=now()+interval '2 minutes' from picked p where n.id=p.id returning n.*
 ) select c.id,c.uid,c.table_id,c.record_id,c.title,c.body,c.claim_token,coalesce((select jsonb_agg(d.token) from public.ws_devices d where d.uid=c.uid),'[]') from claimed c;
end $$;
create function public.finish_ws_notification(p_id uuid,p_claim uuid,p_success boolean,p_invalid_tokens text[] default '{}') returns void
language plpgsql security definer set search_path=public as $$
begin
 if not exists(select 1 from public.ws_notifications where id=p_id and claim_token=p_claim and sent_at is null) then return;end if;
 delete from public.ws_devices where token=any(p_invalid_tokens);
 update public.ws_notifications set sent_at=case when p_success then now() end,
  next_attempt_at=now()+make_interval(secs=>least(3600,(power(2,attempts)*30)::integer)) where id=p_id and claim_token=p_claim;
end $$;

revoke all on function public.workspace_api(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.workspace_api_batch(text,text,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ws_queue_mentions(uuid,jsonb),public.claim_ws_notification(),public.finish_ws_notification(uuid,uuid,boolean,text[]) from public,anon,authenticated;
grant execute on function public.workspace_api(text,text,text,text,text,jsonb),public.claim_ws_notification(),public.finish_ws_notification(uuid,uuid,boolean,text[]) to service_role;
