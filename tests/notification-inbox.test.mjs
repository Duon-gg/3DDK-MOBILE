import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={},name=uid)=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@inbox.invalid`,name,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('mentions appear in the inbox, can be read one by one or all, and vanish with access',async()=>{
 const owner=crypto.randomUUID(),guest=crypto.randomUUID();
 await api(guest,'GET','/me',{},'Khang');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}),'Dương');
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'Việc'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[ws.id,guest]);
 const path=`/tables/${table.id}`;const m=await api(owner,'GET',`${path}/metadata`);
 const rec=await api(owner,'POST',`${path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{}}));
 const url=`${path}/records/${rec.id}/comments`;
 await api(owner,'POST',url,{body:'@Khang một',mentions:[guest]});
 await api(owner,'POST',url,{body:'@Khang hai',mentions:[guest]});
 assert.equal((await api(owner,'GET','/capabilities')).features.notificationInbox,true);
 let inbox=await api(guest,'GET','/me/notifications');
 assert.equal(inbox.unread,2);assert.deepEqual(inbox.items.map(i=>i.body),['@Khang hai','@Khang một']);
 assert.equal(inbox.items[0].recordId,rec.id);assert.equal(inbox.items[0].tableName,'Việc');assert.equal(inbox.items[0].read,false);
 assert.equal((await api(owner,'GET','/me/notifications')).items.length,0,'only the recipient sees them');
 inbox=await api(guest,'POST','/me/notifications/read',{ids:[inbox.items[0].id]});
 assert.equal(inbox.unread,1);assert.equal(inbox.items[0].read,true);
 // Someone else cannot mark my notifications.
 await api(owner,'POST','/me/notifications/read',{all:true});
 assert.equal((await api(guest,'GET','/me/notifications')).unread,1);
 assert.equal((await api(guest,'POST','/me/notifications/read',{all:true})).unread,0);
 await assert.rejects(api(guest,'POST','/me/notifications/read',{}),/VALIDATION/);
 await db.query('delete from ws_members where uid=$1',[guest]);
 assert.equal((await api(guest,'GET','/me/notifications')).items.length,0,'lost access hides them');
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
