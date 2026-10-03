import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
let db;
const id = () => crypto.randomUUID();
const leader = ['leader', 'leader@example.com', 'Nhóm trưởng'];
const member = ['member', 'member@example.com', 'Thành viên'];
const other = ['other', 'other@example.com', 'Thành viên 2'];
async function api(actor, method, path, body = {}) {
  const r = await db.query('select public.api_dispatch($1,$2,$3,$4,$5,$6::jsonb) as result', [...actor, method, path, JSON.stringify(body)]);
  return r.rows[0].result;
}
const mutation = (extra = {}) => ({requestId:id(), ...extra});
async function create(extra = {}) {
  return api(leader, 'POST', '/tasks', mutation({title:'Viết ứng dụng',description:'APK chạy được',dueAt:'2030-01-02T12:00:00Z',priority:'NORMAL',assigneeIds:['member','other'],representativeId:'member',...extra}));
}
before(async () => {
 db = new PGlite();
 await db.exec("create role anon; create role authenticated; create role service_role;");
 await db.exec(await readFile(new URL('../supabase/migrations/202610030001_core.sql', import.meta.url), 'utf8'));
});
after(async () => { await db?.close(); });
test('bootstrap is explicit, outsiders cannot enroll, invited members can enroll', async () => {
 await assert.rejects(api(leader,'GET','/me'), /NOT_INVITED/);
 await db.query("insert into public.members(uid,email,display_name,role) values ($1,$2,$3,'LEADER')",leader);
 await assert.rejects(api(member,'GET','/me'), /NOT_INVITED/);
 await api(leader,'POST','/invites',mutation({email:member[1]}));
 await api(leader,'POST','/invites',mutation({email:other[1]}));
 assert.equal((await api(member,'GET','/me')).role,'MEMBER');
 await api(other,'GET','/me');
 await assert.rejects(api(member,'POST','/invites',mutation({email:'x@example.com'})),/FORBIDDEN/);
});
test('assignment validates active members and representative; member cannot create',async()=>{
 await assert.rejects(create({representativeId:'leader'}),/VALIDATION/);
 await assert.rejects(create({assigneeIds:['missing'],representativeId:'missing'}),/VALIDATION/);
 await assert.rejects(create({title:' '}),/VALIDATION/);
 await assert.rejects(api(member,'POST','/tasks',mutation({})),/FORBIDDEN/);
 const t=await create(); assert.equal(t.status,'TODO'); assert.equal(t.assigneeIds.length,2);
 assert.ok((await api(other,'GET','/tasks')).some(x=>x.id===t.id));
});
test('versioned review cycle enforces representative, current submission and history',async()=>{
 let t=await create();
 await assert.rejects(api(other,'POST',`/tasks/${t.id}/start`,mutation({expectedVersion:t.version})),/FORBIDDEN/);
 t=await api(member,'POST',`/tasks/${t.id}/start`,mutation({expectedVersion:t.version}));
 const submit=mutation({expectedVersion:t.version,description:'Đã làm',link:'https://github.com/Duon-gg/3DDK-MOBILE'});
 t=await api(member,'POST',`/tasks/${t.id}/submissions`,submit);
 const replay=await api(member,'POST',`/tasks/${t.id}/submissions`,submit);
 assert.deepEqual(replay,t); assert.equal(t.status,'IN_REVIEW');
 await assert.rejects(api(leader,'PATCH',`/tasks/${t.id}`,mutation({expectedVersion:t.version,title:'Sửa'})),/INVALID_STATE/);
 await assert.rejects(api(member,'POST',`/tasks/${t.id}/review`,mutation({expectedVersion:t.version,submissionId:t.currentSubmissionId,approve:true})),/FORBIDDEN/);
 await assert.rejects(api(leader,'POST',`/tasks/${t.id}/review`,mutation({expectedVersion:t.version,submissionId:id(),approve:true})),/CONFLICT/);
 await assert.rejects(api(leader,'POST',`/tasks/${t.id}/review`,mutation({expectedVersion:t.version,submissionId:t.currentSubmissionId,approve:false,reason:''})),/VALIDATION/);
 t=await api(leader,'POST',`/tasks/${t.id}/review`,mutation({expectedVersion:t.version,submissionId:t.currentSubmissionId,approve:false,reason:'Bổ sung kiểm thử'}));
 assert.equal(t.status,'IN_PROGRESS');
 t=await api(member,'POST',`/tasks/${t.id}/submissions`,mutation({expectedVersion:t.version,description:'Đã bổ sung',link:''}));
 t=await api(leader,'POST',`/tasks/${t.id}/review`,mutation({expectedVersion:t.version,submissionId:t.currentSubmissionId,approve:true}));
 assert.equal(t.status,'DONE');
 const history=await api(member,'GET',`/tasks/${t.id}/history`);
 assert.equal(history.submissions.length,2); assert.equal(history.submissions[0].reviewStatus,'REJECTED');
 await assert.rejects(api(leader,'POST',`/tasks/${t.id}/reopen`,mutation({expectedVersion:t.version,reason:''})),/VALIDATION/);
 t=await api(leader,'POST',`/tasks/${t.id}/reopen`,mutation({expectedVersion:t.version,reason:'Thêm kiểm tra'}));
 assert.equal(t.status,'IN_PROGRESS');
});
test('stale version and changed idempotency payload cannot overwrite data',async()=>{
 const t=await create(); const body=mutation({expectedVersion:t.version,title:'Tên mới'});
 const changed=await api(leader,'PATCH',`/tasks/${t.id}`,body);
 assert.deepEqual(await api(leader,'PATCH',`/tasks/${t.id}`,body),changed);
 await assert.rejects(api(leader,'PATCH',`/tasks/${t.id}`,mutation({expectedVersion:t.version,title:'Cũ'})),/CONFLICT/);
 await assert.rejects(api(leader,'PATCH',`/tasks/${t.id}`,{...body,title:'Khác'}),/CONFLICT/);
 assert.equal((await api(member,'GET',`/tasks/${t.id}`)).title,changed.title);
});
test('pending assignees block deactivation; archived tasks stop mutation and notification delivery',async()=>{
 let t=await create();
 await assert.rejects(api(leader,'PATCH','/members/member',mutation({active:false})),/ASSIGNED_WORK/);
 t=await api(leader,'POST',`/tasks/${t.id}/archive`,mutation({expectedVersion:t.version}));
 assert.ok(t.archivedAt);
 await assert.rejects(api(member,'POST',`/tasks/${t.id}/start`,mutation({expectedVersion:t.version})),/INVALID_STATE/);
 const pending=await db.query('select * from public.claim_notifications()');
 assert.ok(pending.rows.every(x=>x.task_id!==t.id));
});
test('notifications are private and device tokens cannot be rebound to another member',async()=>{
 await create();
 const notes=await api(member,'GET','/notifications'); assert.ok(notes.length>0);
 await assert.rejects(api(other,'PATCH',`/notifications/${notes[0].id}`,mutation({})),/NOT_FOUND/);
 const device=id(); await api(member,'PUT',`/devices/${device}`,mutation({token:'token-example'}));
 await assert.rejects(api(other,'PUT',`/devices/${device}`,mutation({token:'token-example'})),/FORBIDDEN/);
});
test('deadline scheduler deduplicates and ignores completed or archived work',async()=>{
 const t=await create({dueAt:new Date(Date.now()+55*60*1000).toISOString()});
 await db.query('select public.enqueue_reminders()'); await db.query('select public.enqueue_reminders()');
 const r=await db.query("select count(*)::int as n from public.notifications where task_id=$1 and kind='REMINDER_1H'",[t.id]);
 assert.equal(r.rows[0].n,2);
});
test('client database roles have no direct data or privileged function access',async()=>{
 const r=await db.query("select has_table_privilege('anon','public.tasks','SELECT') as readable, has_function_privilege('authenticated','public.api_dispatch(text,text,text,text,text,jsonb)','EXECUTE') as callable");
 assert.deepEqual(r.rows[0],{readable:false,callable:false});
});
test('worker only leases the next notification, leaving the tail attempts untouched',async()=>{
 await db.query('update public.notification_outbox set sent_at=now()');
 await create();
 const claimed=await db.query('select * from public.claim_notifications()');
 assert.equal(claimed.rows.length,1);
 const tail=await db.query('select attempts from public.notification_outbox where sent_at is null and notification_id<>$1',[claimed.rows[0].notification_id]);
 assert.ok(tail.rows.length>0);assert.ok(tail.rows.every(x=>x.attempts===0));
});
