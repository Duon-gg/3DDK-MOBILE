import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@batch.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql','202611250003_comment_edit.sql','202611250004_view_layout.sql','202611250005_field_positions.sql','202611250006_record_batch.sql','202611250007_mention_push.sql'])
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

async function setup(){
 const owner=crypto.randomUUID(),viewer=crypto.randomUUID();
 await api(viewer,'GET','/me');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'VIEWER')",[ws.id,viewer]);
 const path=`/tables/${table.id}`;const m=await api(owner,'GET',`${path}/metadata`);
 return {owner,viewer,path,m,text:m.fields[0].id};
}
const count=async(f)=>(await api(f.owner,'POST',`${f.path}/records/query`,{limit:100})).items.length;

test('a 120-row batch is one write and keeps history',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.recordBatch,true);
 const records=Array.from({length:120},(_,i)=>({cells:{[f.text]:`row ${i}`}}));
 const body=request({expectedSchemaVersion:f.m.schemaVersion,records});
 const out=await api(f.owner,'POST',`${f.path}/records/batch`,body);
 assert.equal(out.created,120);assert.equal(out.ids.length,120);
 // Retrying the same request does not insert again.
 assert.deepEqual(await api(f.owner,'POST',`${f.path}/records/batch`,body),out);
 assert.equal((await db.query('select count(*)::int n from ws_records where table_id=$1',[f.path.split('/')[2]])).rows[0].n,120);
 const history=await api(f.owner,'GET',`${f.path}/records/${out.ids[50]}/history`);
 assert.equal(history.items[0].action,'CREATED');
 // 59 more single writes still fit in the minute: the batch counted once.
 for(let i=0;i<55;i++)await api(f.owner,'POST',`${f.path}/records`,request({expectedVersion:1,expectedSchemaVersion:f.m.schemaVersion,cells:{[f.text]:`x${i}`}}));
});

test('a bad row, a viewer or a stale schema rolls back the whole batch',async()=>{
 const f=await setup();
 const bad=[{cells:{[f.text]:'ok'}},{cells:{[crypto.randomUUID()]:'nope'}}];
 await assert.rejects(api(f.owner,'POST',`${f.path}/records/batch`,request({expectedSchemaVersion:f.m.schemaVersion,records:bad})));
 await assert.rejects(api(f.viewer,'POST',`${f.path}/records/batch`,request({expectedSchemaVersion:f.m.schemaVersion,records:[{cells:{}}]})),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'POST',`${f.path}/records/batch`,request({expectedSchemaVersion:f.m.schemaVersion+5,records:[{cells:{}}]})),/SCHEMA_CHANGED/);
 await assert.rejects(api(f.owner,'POST',`${f.path}/records/batch`,request({expectedSchemaVersion:f.m.schemaVersion,records:[]})),/VALIDATION/);
 assert.equal((await db.query('select count(*)::int n from ws_records where table_id=$1',[f.path.split('/')[2]])).rows[0].n,0);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
