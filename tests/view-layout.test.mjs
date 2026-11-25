import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@layout.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql','202611250003_comment_edit.sql','202611250004_view_layout.sql'])
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

async function setup(){
 const owner=crypto.randomUUID(),guest=crypto.randomUUID();
 await api(guest,'GET','/me');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[ws.id,guest]);
 const path=`/tables/${table.id}`;const meta=()=>api(owner,'GET',`${path}/metadata`);
 let m=await meta();
 const status=await api(owner,'POST',`${path}/fields`,request({name:'Status',type:'SELECT',options:['A','B'],expectedSchemaVersion:m.schemaVersion}));
 m=await meta();
 return {owner,guest,path,meta,status:status.id,text:m.fields.find(f=>f.type==='TEXT').id};
}

test('views save layout, group and Kanban field for everyone',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.viewLayout,true);
 const view=await api(f.owner,'POST',`${f.path}/views`,request({name:'Board',visibility:'SHARED',layout:{type:'KANBAN',kanbanFieldId:f.status,groupFieldId:null}}));
 assert.deepEqual(view.layout,{type:'KANBAN',kanbanFieldId:f.status});
 const seen=(await api(f.guest,'GET',`${f.path}/metadata`)).views.find(v=>v.id===view.id);
 assert.deepEqual(seen.layout,{type:'KANBAN',kanbanFieldId:f.status});
 const next=await api(f.owner,'PATCH',`${f.path}/views/${view.id}`,request({expectedVersion:view.version,layout:{type:'GRID',groupFieldId:f.status}}));
 assert.equal(next.version,view.version+1);assert.deepEqual(next.layout,{type:'GRID',groupFieldId:f.status});
 // Shared views stay owner-only; stale versions still conflict.
 await assert.rejects(api(f.guest,'PATCH',`${f.path}/views/${view.id}`,request({expectedVersion:next.version,layout:{type:'CARDS'}})),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'PATCH',`${f.path}/views/${view.id}`,request({expectedVersion:view.version,layout:{type:'CARDS'}})),/CONFLICT/);
 assert.deepEqual((await f.meta()).views.find(v=>v.id===view.id).layout,{type:'GRID',groupFieldId:f.status});
 // Views without layout still work and return null.
 assert.equal((await f.meta()).views.find(v=>v.isDefault).layout,null);
});

test('layout is validated',async()=>{
 const f=await setup();const m=await f.meta();const v=m.views.find(x=>x.isDefault);
 for(const layout of [{type:'GALLERY'},{type:'GRID',extra:1},{type:'GRID',groupFieldId:crypto.randomUUID()},{type:'KANBAN',kanbanFieldId:f.text},{type:'KANBAN'},{type:'GRID',groupFieldId:5},'GRID'])
  await assert.rejects(api(f.owner,'PATCH',`${f.path}/views/${v.id}`,request({expectedVersion:v.version,layout})),/VALIDATION/,JSON.stringify(layout));
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
