create table public.members (
 uid text primary key, email text not null unique, display_name text not null check(length(display_name) between 1 and 100),
 role text not null default 'MEMBER' check(role in ('LEADER','MEMBER')), active boolean not null default true,
 avatar_url text not null default 'https://api.dicebear.com/9.x/shapes/png?seed=1', created_at timestamptz not null default now()
);
create unique index one_leader on public.members(role) where role='LEADER';
create table public.invites(email text primary key, created_by text not null references public.members(uid),created_at timestamptz not null default now());
create table public.tasks (
 id uuid primary key default gen_random_uuid(), title text not null check(length(trim(title)) between 1 and 120),
 description text not null check(length(trim(description)) between 1 and 5000), due_at timestamptz not null,
 priority text not null check(priority in ('LOW','NORMAL','HIGH')), status text not null default 'TODO' check(status in ('TODO','IN_PROGRESS','IN_REVIEW','DONE')),
 representative_id text not null references public.members(uid), created_by text not null references public.members(uid),
 version integer not null default 1, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), archived_at timestamptz
);
create table public.task_assignees(task_id uuid references public.tasks(id),uid text references public.members(uid),primary key(task_id,uid));
create table public.submissions (
 id uuid primary key default gen_random_uuid(),task_id uuid not null references public.tasks(id),submitted_by text not null references public.members(uid),
 description text not null check(length(trim(description)) between 1 and 5000),link text not null default '',
 review_status text not null default 'PENDING' check(review_status in ('PENDING','APPROVED','REJECTED')),
 reviewed_by text references public.members(uid),reason text,created_at timestamptz not null default now(),reviewed_at timestamptz
);
create table public.task_events(id uuid primary key default gen_random_uuid(),task_id uuid not null references public.tasks(id),actor_id text not null references public.members(uid),kind text not null,detail jsonb not null default '{}',created_at timestamptz not null default now());
create table public.notifications(id uuid primary key default gen_random_uuid(),uid text not null references public.members(uid),task_id uuid not null references public.tasks(id),kind text not null,event_key text not null,read boolean not null default false,due_at timestamptz,created_at timestamptz not null default now(),unique(uid,event_key));
create table public.device_tokens(installation_id uuid primary key,uid text not null references public.members(uid),token text not null unique,updated_at timestamptz not null default now());
create table public.notification_outbox(notification_id uuid primary key references public.notifications(id),attempts integer not null default 0,next_attempt_at timestamptz not null default now(),claim_token uuid,sent_at timestamptz);
create table public.api_requests(uid text references public.members(uid),request_id uuid,method text not null,path text not null,body jsonb not null,response jsonb not null,created_at timestamptz not null default now(),primary key(uid,request_id));
create table public.api_limits(uid text primary key references public.members(uid),window_at timestamptz not null default now(),count integer not null default 0);
create index tasks_due on public.tasks(due_at) where archived_at is null;
create index submissions_task on public.submissions(task_id,created_at);
create index events_task on public.task_events(task_id,created_at);
create index notifications_user on public.notifications(uid,created_at);

create function public.member_json(m public.members) returns jsonb language sql stable as $$
 select jsonb_build_object('uid',m.uid,'email',m.email,'displayName',m.display_name,'role',m.role,'active',m.active,'avatarUrl',m.avatar_url)
$$;
create function public.task_json(t public.tasks) returns jsonb language sql stable as $$
 select jsonb_build_object('id',t.id,'title',t.title,'description',t.description,'dueAt',t.due_at,'priority',t.priority,'status',t.status,'representativeId',t.representative_id,'createdBy',t.created_by,'version',t.version,'archivedAt',t.archived_at,
 'assigneeIds',coalesce((select jsonb_agg(a.uid order by a.uid) from public.task_assignees a where a.task_id=t.id),'[]'),
 'currentSubmissionId',(select s.id from public.submissions s where s.task_id=t.id order by s.created_at desc,s.id desc limit 1))
$$;
create function public.enqueue_notice(p_task uuid,p_uid text,p_kind text,p_key text,p_due timestamptz default null) returns void language plpgsql as $$
declare n uuid;
begin
 insert into public.notifications(uid,task_id,kind,event_key,due_at) values(p_uid,p_task,p_kind,p_key,p_due) on conflict do nothing returning id into n;
 if n is not null then insert into public.notification_outbox(notification_id) values(n); end if;
end $$;

create function public.api_dispatch(p_uid text,p_email text,p_name text,p_method text,p_path text,p_body jsonb default '{}') returns jsonb
language plpgsql security definer set search_path = public,pg_temp as $$
declare
 actor public.members; target public.members; t public.tasks; old_t public.tasks; req public.api_requests;
 parts text[]:=string_to_array(trim(both '/' from p_path),'/'); rid uuid; tid uuid; sid uuid;
 result jsonb; ids text[]; old_ids text[]; recipient text; event_id uuid; action text; review_reason text; approved boolean; rate integer;
 original_body jsonb := p_body;
begin
 -- ponytail: one-group write lock; use per-task locks if multiple groups or write throughput grows.
 if p_method<>'GET' then perform pg_advisory_xact_lock(33003); end if;
 select * into actor from public.members where uid=p_uid;
 if not found then
   if not exists(select 1 from public.invites where email=lower(trim(p_email))) then raise exception 'NOT_INVITED'; end if;
   insert into public.members(uid,email,display_name) values(p_uid,lower(trim(p_email)),left(coalesce(nullif(trim(p_name),''),split_part(p_email,'@',1)),100)) on conflict(uid) do nothing;
   select * into actor from public.members where uid=p_uid;
 end if;
 if not actor.active then raise exception 'FORBIDDEN'; end if;
 if p_method<>'GET' then
   rid:=(p_body->>'requestId')::uuid;
   if rid is null then raise exception 'VALIDATION'; end if;
   select * into req from public.api_requests where uid=p_uid and request_id=rid;
   if found then
     if req.method<>p_method or req.path<>p_path or req.body<>p_body then raise exception 'CONFLICT'; end if;
     return req.response;
   end if;
   insert into public.api_limits(uid,count) values(p_uid,1) on conflict(uid) do update
     set count=case when api_limits.window_at < now()-interval '1 minute' then 1 else api_limits.count+1 end,
         window_at=case when api_limits.window_at < now()-interval '1 minute' then now() else api_limits.window_at end returning count into rate;
   if rate>60 then raise exception 'RATE_LIMIT'; end if;
 end if;

 if p_path='/me' and p_method='GET' then return public.member_json(actor);
 elsif p_path='/me' and p_method='PATCH' then
   if length(trim(coalesce(p_body->>'displayName',''))) not between 1 and 100 or coalesce(p_body->>'avatarUrl','') !~ '^https://api\.dicebear\.com/9\.x/shapes/png\?seed=[1-6]$' then raise exception 'VALIDATION'; end if;
   update public.members set display_name=trim(p_body->>'displayName'),avatar_url=p_body->>'avatarUrl' where uid=p_uid returning * into actor;
   result:=public.member_json(actor);
 elsif p_path='/members' and p_method='GET' then
   select coalesce(jsonb_agg(case when actor.role='LEADER' then public.member_json(m) else public.member_json(m)-'email' end order by m.display_name),'[]') into result from public.members m;
 elsif parts[1]='members' and array_length(parts,1)=2 and p_method='PATCH' then
   if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
   select * into target from public.members where uid=parts[2];
   if not found then raise exception 'NOT_FOUND'; end if;
   if target.role='LEADER' then raise exception 'FORBIDDEN'; end if;
   if jsonb_typeof(p_body->'active') is distinct from 'boolean' then raise exception 'VALIDATION'; end if;
   if not (p_body->>'active')::boolean and exists(select 1 from public.tasks x join public.task_assignees a on a.task_id=x.id where a.uid=target.uid and x.archived_at is null and x.status<>'DONE') then raise exception 'ASSIGNED_WORK'; end if;
   update public.members set active=(p_body->>'active')::boolean where uid=target.uid returning * into target;
   if not target.active then delete from public.device_tokens where uid=target.uid; end if;
   result:=public.member_json(target);
 elsif p_path='/invites' then
   if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
   if p_method='GET' then select coalesce(jsonb_agg(jsonb_build_object('email',email,'createdAt',created_at) order by created_at),'[]') into result from public.invites;
   elsif p_method in ('POST','DELETE') then
     if length(coalesce(p_body->>'email',''))>254 or coalesce(p_body->>'email','') !~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then raise exception 'VALIDATION'; end if;
     if p_method='POST' then insert into public.invites(email,created_by) values(lower(trim(p_body->>'email')),p_uid) on conflict do nothing;
     else delete from public.invites where email=lower(trim(p_body->>'email')); end if;
     result:='{}';
   else raise exception 'NOT_FOUND'; end if;
 elsif parts[1]='devices' and array_length(parts,1)=2 and p_method in ('PUT','DELETE') then
   if exists(select 1 from public.device_tokens where installation_id=parts[2]::uuid and uid<>p_uid) then raise exception 'FORBIDDEN'; end if;
   if p_method='DELETE' then delete from public.device_tokens where installation_id=parts[2]::uuid and uid=p_uid;
   else
     if length(coalesce(p_body->>'token','')) not between 1 and 4096 then raise exception 'VALIDATION'; end if;
     if exists(select 1 from public.device_tokens where token=p_body->>'token' and uid<>p_uid) then raise exception 'FORBIDDEN'; end if;
     delete from public.device_tokens where token=p_body->>'token' and installation_id<>parts[2]::uuid and uid=p_uid;
     insert into public.device_tokens(installation_id,uid,token) values(parts[2]::uuid,p_uid,p_body->>'token') on conflict(installation_id) do update set token=excluded.token,updated_at=now();
   end if; result:='{}';
 elsif p_path='/notifications' and p_method='GET' then
   select coalesce(jsonb_agg(jsonb_build_object('id',id,'taskId',task_id,'kind',kind,'read',read,'createdAt',created_at) order by created_at desc),'[]') into result from public.notifications where uid=p_uid;
 elsif parts[1]='notifications' and array_length(parts,1)=2 and p_method='PATCH' then
   update public.notifications set read=true where id=parts[2]::uuid and uid=p_uid;
   if not found then raise exception 'NOT_FOUND'; end if; result:='{}';
 elsif p_path='/tasks' and p_method='GET' then
   select coalesce(jsonb_agg(public.task_json(x) order by x.due_at),'[]') into result from public.tasks x where x.archived_at is null;
 elsif parts[1]='tasks' then
   if p_path='/tasks' and p_method='POST' then
     if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
     action:='CREATED';
   else
     if array_length(parts,1) not between 2 and 3 then raise exception 'NOT_FOUND'; end if;
     tid:=parts[2]::uuid; select * into t from public.tasks where id=tid;
     if not found then raise exception 'NOT_FOUND'; end if;
     if array_length(parts,1)=2 and p_method='GET' then return public.task_json(t); end if;
     if parts[3]='history' and p_method='GET' then
       return jsonb_build_object('events',coalesce((select jsonb_agg(jsonb_build_object('id',e.id,'actorId',e.actor_id,'kind',e.kind,'detail',e.detail,'createdAt',e.created_at) order by e.created_at,e.id) from public.task_events e where task_id=tid),'[]'),
        'submissions',coalesce((select jsonb_agg(jsonb_build_object('id',s.id,'submittedBy',s.submitted_by,'description',s.description,'link',s.link,'reviewStatus',s.review_status,'reviewedBy',s.reviewed_by,'reason',s.reason,'createdAt',s.created_at) order by s.created_at,s.id) from public.submissions s where task_id=tid),'[]'));
     end if;
     if t.archived_at is not null then raise exception 'INVALID_STATE'; end if;
     if coalesce((p_body->>'expectedVersion')::integer,-1)<>t.version then raise exception 'CONFLICT'; end if;
     old_t:=t;
     select array_agg(uid) into old_ids from public.task_assignees where task_id=tid;
     if array_length(parts,1)=2 and p_method='PATCH' then
       if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
       if t.status not in ('TODO','IN_PROGRESS') then raise exception 'INVALID_STATE'; end if;
       action:='UPDATED';
     elsif p_method='POST' and parts[3]='start' then
       if p_uid<>t.representative_id then raise exception 'FORBIDDEN'; end if;
       if t.status<>'TODO' then raise exception 'INVALID_STATE'; end if;
       update public.tasks set status='IN_PROGRESS' where id=tid; action:='STARTED';
     elsif p_method='POST' and parts[3]='submissions' then
       if p_uid<>t.representative_id then raise exception 'FORBIDDEN'; end if;
       if t.status<>'IN_PROGRESS' then raise exception 'INVALID_STATE'; end if;
       if length(trim(coalesce(p_body->>'description',''))) not between 1 and 5000 or length(coalesce(p_body->>'link',''))>2048 or (coalesce(p_body->>'link','')<>'' and (p_body->>'link') !~ '^https://[^[:space:]]+$') then raise exception 'VALIDATION'; end if;
       insert into public.submissions(task_id,submitted_by,description,link) values(tid,p_uid,trim(p_body->>'description'),coalesce(p_body->>'link',''));
       update public.tasks set status='IN_REVIEW' where id=tid; action:='SUBMITTED';
     elsif p_method='POST' and parts[3]='review' then
       if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
       if t.status<>'IN_REVIEW' then raise exception 'INVALID_STATE'; end if;
       select id into sid from public.submissions where task_id=tid and review_status='PENDING' order by created_at desc,id desc limit 1;
       if sid is distinct from (p_body->>'submissionId')::uuid then raise exception 'CONFLICT'; end if;
       if jsonb_typeof(p_body->'approve') is distinct from 'boolean' then raise exception 'VALIDATION'; end if;
       approved:=(p_body->>'approve')::boolean; review_reason:=trim(coalesce(p_body->>'reason',''));
       if length(review_reason)>5000 or (not approved and review_reason='') then raise exception 'VALIDATION'; end if;
       update public.submissions set review_status=case when approved then 'APPROVED' else 'REJECTED' end,reviewed_by=p_uid,reason=review_reason,reviewed_at=now() where id=sid;
       update public.tasks set status=case when approved then 'DONE' else 'IN_PROGRESS' end where id=tid;
       action:=case when approved then 'APPROVED' else 'REJECTED' end;
     elsif p_method='POST' and parts[3]='reopen' then
       if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
       if t.status<>'DONE' then raise exception 'INVALID_STATE'; end if;
       if length(trim(coalesce(p_body->>'reason',''))) not between 1 and 5000 then raise exception 'VALIDATION'; end if;
       update public.tasks set status='IN_PROGRESS' where id=tid; action:='REOPENED';
     elsif p_method='POST' and parts[3]='archive' then
       if actor.role<>'LEADER' then raise exception 'FORBIDDEN'; end if;
       update public.tasks set archived_at=now() where id=tid; action:='ARCHIVED';
     else raise exception 'NOT_FOUND'; end if;
   end if;
   if action in ('CREATED','UPDATED') then
     if action='UPDATED' then p_body:=public.task_json(t)||p_body; end if;
     if length(trim(coalesce(p_body->>'title',''))) not between 1 and 120 or length(trim(coalesce(p_body->>'description',''))) not between 1 and 5000 or coalesce(p_body->>'priority','') not in ('LOW','NORMAL','HIGH') or p_body->>'dueAt' is null then raise exception 'VALIDATION'; end if;
     if jsonb_typeof(p_body->'assigneeIds') is distinct from 'array' then raise exception 'VALIDATION'; end if;
     select array_agg(distinct value) into ids from jsonb_array_elements_text(p_body->'assigneeIds');
     if coalesce(array_length(ids,1),0)=0 or coalesce(array_length(ids,1),0)>100 or not coalesce((p_body->>'representativeId')=any(ids),false) or (select count(*) from public.members where uid=any(ids) and active)<>array_length(ids,1) then raise exception 'VALIDATION'; end if;
     if action='CREATED' then
       insert into public.tasks(title,description,due_at,priority,representative_id,created_by) values(trim(p_body->>'title'),trim(p_body->>'description'),(p_body->>'dueAt')::timestamptz,p_body->>'priority',p_body->>'representativeId',p_uid) returning id into tid;
     else
       update public.tasks set title=trim(p_body->>'title'),description=trim(p_body->>'description'),due_at=(p_body->>'dueAt')::timestamptz,priority=p_body->>'priority',representative_id=p_body->>'representativeId' where id=tid;
       delete from public.task_assignees where task_id=tid;
     end if;
     insert into public.task_assignees(task_id,uid) select tid,unnest(ids);
   end if;
   if action<>'CREATED' then update public.tasks set version=version+1,updated_at=now() where id=tid; end if;
   select * into t from public.tasks where id=tid;
   insert into public.task_events(task_id,actor_id,kind,detail) values(tid,p_uid,action,jsonb_build_object('reason',coalesce(p_body->>'reason',''),'version',t.version,'before',case when old_t.id is not null then public.task_json(old_t)||jsonb_build_object('assigneeIds',old_ids) else null end,'after',public.task_json(t))) returning id into event_id;
   if action='SUBMITTED' then
     for recipient in select uid from public.members where role='LEADER' and active loop perform public.enqueue_notice(tid,recipient,action,event_id::text); end loop;
   elsif action in ('CREATED','UPDATED','APPROVED','REJECTED','REOPENED') then
     for recipient in select distinct uid from (select a.uid from public.task_assignees a where task_id=tid union select unnest(coalesce(old_ids,'{}'))) recipients
       loop perform public.enqueue_notice(tid,recipient,action,event_id::text); end loop;
   end if;
   result:=public.task_json(t);
 else raise exception 'NOT_FOUND'; end if;
 if p_method<>'GET' then
   -- Store the original request, not the merged PATCH document.
   insert into public.api_requests(uid,request_id,method,path,body,response) values(p_uid,rid,p_method,p_path,original_body,result);
 end if;
 return result;
end $$;

create function public.enqueue_reminders() returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare item record;
begin
 for item in select t.id,t.due_at,a.uid,case when t.due_at<=now()+interval '1 hour' then 'REMINDER_1H' else 'REMINDER_24H' end kind
  from public.tasks t join public.task_assignees a on a.task_id=t.id join public.members m on m.uid=a.uid
  where t.archived_at is null and t.status<>'DONE' and m.active and t.due_at>now() and t.due_at<=now()+interval '24 hours'
 loop perform public.enqueue_notice(item.id,item.uid,item.kind,item.id::text||':'||item.kind||':'||item.due_at::text,item.due_at); end loop;
end $$;
create function public.claim_notifications() returns table(notification_id uuid,task_id uuid,uid text,kind text,claim_token uuid,tokens jsonb)
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 update public.notification_outbox o set sent_at=now() from public.notifications n,public.tasks t,public.members m
 where o.notification_id=n.id and n.task_id=t.id and n.uid=m.uid and o.sent_at is null
 and (t.archived_at is not null or not m.active or (n.due_at is not null and (t.status='DONE' or n.due_at<>t.due_at or t.due_at<now() or not exists(select 1 from public.task_assignees a where a.task_id=t.id and a.uid=m.uid))));
 return query with picked as (
  select o.notification_id from public.notification_outbox o where o.sent_at is null and o.next_attempt_at<=now() and o.attempts<8 order by o.next_attempt_at limit 50 for update skip locked
 ), claimed as (
  update public.notification_outbox o set claim_token=gen_random_uuid(),attempts=attempts+1,next_attempt_at=now()+interval '2 minutes' from picked p where o.notification_id=p.notification_id returning o.*
 ) select n.id,n.task_id,n.uid,n.kind,c.claim_token,coalesce((select jsonb_agg(d.token) from public.device_tokens d where d.uid=n.uid),'[]') from claimed c join public.notifications n on n.id=c.notification_id;
end $$;
create function public.finish_notification(p_id uuid,p_claim uuid,p_success boolean,p_invalid_tokens text[] default '{}') returns void
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not exists(select 1 from public.notification_outbox where notification_id=p_id and claim_token=p_claim and sent_at is null) then return; end if;
 delete from public.device_tokens where token=any(p_invalid_tokens);
 update public.notification_outbox set sent_at=case when p_success then now() else null end,next_attempt_at=now()+make_interval(secs=>least(3600,(power(2,attempts)*30)::integer)) where notification_id=p_id and claim_token=p_claim;
end $$;

do $$ declare item text; begin
 foreach item in array array['members','invites','tasks','task_assignees','submissions','task_events','notifications','device_tokens','notification_outbox','api_requests','api_limits'] loop
  execute format('alter table public.%I enable row level security',item);
  execute format('revoke all on public.%I from anon,authenticated',item);
 end loop;
end $$;
revoke all on function public.member_json(public.members),public.task_json(public.tasks),public.enqueue_notice(uuid,text,text,text,timestamptz),public.api_dispatch(text,text,text,text,text,jsonb),public.enqueue_reminders(),public.claim_notifications(),public.finish_notification(uuid,uuid,boolean,text[]) from public,anon,authenticated;
grant execute on function public.api_dispatch(text,text,text,text,text,jsonb),public.enqueue_reminders(),public.claim_notifications(),public.finish_notification(uuid,uuid,boolean,text[]) to service_role;
