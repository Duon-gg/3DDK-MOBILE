import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@pos.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql','202611250003_comment_edit.sql','202611250004_view_layout.sql','202611250005_field_positions.sql'])
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('insert left after a delete lands next to the chosen field',async()=>{
 const owner=crypto.randomUUID();
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 const path=`/tables/${table.id}`;const meta=()=>api(owner,'GET',`${path}/metadata`);
 const add=async(name,extra={})=>{const m=await meta();return api(owner,'POST',`${path}/fields`,request({name,type:'TEXT',options:[],expectedSchemaVersion:m.schemaVersion,...extra}));};
 await add('A');const b=await add('B');await add('C');
 let m=await meta();await api(owner,'DELETE',`${path}/fields/${b.id}`,request({expectedSchemaVersion:m.schemaVersion}));
 await add('D');
 m=await meta();
 const positions=m.fields.map(f=>f.position);
 assert.deepEqual(positions,[...positions.keys()],'positions stay 0..n-1');
 const d=m.fields.find(f=>f.name==='D');
 await add('L',{position:d.position});
 m=await meta();
 assert.deepEqual(m.fields.map(f=>f.name).slice(-4),['A','C','L','D']);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});

test('retyping to USER needs member cells; multi-select contains matches options',async()=>{
 const owner=crypto.randomUUID();
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 const path=`/tables/${table.id}`;const meta=()=>api(owner,'GET',`${path}/metadata`);
 let m=await meta();
 const who=await api(owner,'POST',`${path}/fields`,request({name:'Who',type:'TEXT',options:[],expectedSchemaVersion:m.schemaVersion}));
 const tags=await api(owner,'POST',`${path}/fields`,request({name:'Tags',type:'MULTI_SELECT',options:['Red','Blue'],expectedSchemaVersion:(await meta()).schemaVersion}));
 m=await meta();
 const rec=await api(owner,'POST',`${path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{[who.id]:'Khang',[tags.id]:['Red']}}));
 await api(owner,'POST',`${path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{[tags.id]:['Blue']}}));
 await assert.rejects(api(owner,'PATCH',`${path}/fields/${who.id}`,request({type:'USER',expectedSchemaVersion:m.schemaVersion})),/VALIDATION/);
 assert.equal((await meta()).fields.find(f=>f.id===who.id).type,'TEXT');
 await api(owner,'PATCH',`${path}/records/${rec.id}/cells`,request({expectedVersion:rec.version,expectedSchemaVersion:m.schemaVersion,changes:{[who.id]:owner}}));
 assert.equal((await api(owner,'PATCH',`${path}/fields/${who.id}`,request({type:'USER',expectedSchemaVersion:m.schemaVersion}))).type,'USER');
 m=await meta();
 const q=async value=>(await api(owner,'POST',`${path}/records/query`,{filter:{op:'and',conditions:[{fieldId:tags.id,operator:'contains',value}]}})).items.length;
 assert.equal(await q('re'),1);assert.equal(await q('"'),0);assert.equal(await q(','),0);
});
