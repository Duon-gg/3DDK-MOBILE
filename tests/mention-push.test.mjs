import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@push.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
const migrations=['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql','202611250003_comment_edit.sql','202611250004_view_layout.sql','202611250005_field_positions.sql','202611250006_record_batch.sql','202611250007_mention_push.sql'];
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of migrations)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});
const token=n=>`fcm-token-${n}-`+'x'.repeat(30);

test('mentions queue pushes for members only, once, and devices register',async()=>{
 const owner=crypto.randomUUID(),guest=crypto.randomUUID(),stranger=crypto.randomUUID();
 await api(guest,'GET','/me');await api(stranger,'GET','/me');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'Việc'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[ws.id,guest]);
 const path=`/tables/${table.id}`;const m=await api(owner,'GET',`${path}/metadata`);
 const rec=await api(owner,'POST',`${path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{}}));
 assert.equal((await api(owner,'GET','/capabilities')).features.mentionPush,true);

 await api(guest,'PUT','/devices/install-guest-1',{token:token(1)});
 await api(guest,'PUT','/devices/install-guest-1',{token:token(2)});
 // The same token moving to another account leaves the first one.
 await api(stranger,'PUT','/devices/install-strange',{token:token(9)});
 await api(guest,'PUT','/devices/install-guest-2',{token:token(9)});
 await assert.rejects(api(guest,'PUT','/devices/x',{token:token(3)}),/VALIDATION/);
 assert.deepEqual((await db.query('select uid,token from ws_devices order by token')).rows.map(r=>[r.uid===guest,r.token]),[[true,token(2)],[true,token(9)]]);

 const url=`${path}/records/${rec.id}/comments`;
 const posted=await api(owner,'POST',url,{body:'@guest xem nhé',mentions:[guest,owner]});
 let rows=(await db.query('select uid,title,body from ws_notifications')).rows;
 assert.equal(rows.length,1);assert.equal(rows[0].uid,guest);assert.match(rows[0].title,/đã nhắc bạn trong Việc/);
 // Editing without new mentions queues nothing; adding one queues only the new person.
 const id=posted.comments[0].id;
 await api(owner,'PATCH',`${url}/${id}`,{body:'@guest xem lại',mentions:[guest]});
 assert.equal((await db.query('select count(*)::int n from ws_notifications')).rows[0].n,1);

 const [job]=(await db.query('select * from claim_ws_notification()')).rows;
 assert.equal(job.uid,guest);assert.deepEqual(job.tokens.sort(),[token(2),token(9)]);
 assert.equal((await db.query('select * from claim_ws_notification()')).rows.length,0,'leased job is not handed out twice');
 await db.query('select finish_ws_notification($1,$2,true,$3)',[job.notification_id,job.claim_token,[token(9)]]);
 assert.equal((await db.query('select count(*)::int n from ws_devices')).rows[0].n,1,'invalid token removed');
 assert.ok((await db.query('select sent_at from ws_notifications')).rows[0].sent_at);

 await api(guest,'DELETE','/devices/install-guest-1');
 assert.equal((await db.query('select count(*)::int n from ws_devices')).rows[0].n,0);
 const priv=(await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') a,has_function_privilege('service_role','public.claim_ws_notification()','execute') b,has_function_privilege('anon','public.claim_ws_notification()','execute') c")).rows[0];
 assert.deepEqual([priv.a,priv.b,priv.c],[true,true,false]);
});
