import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
async function api(uid,method,path,body={}){
 return (await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',
  [uid,`${uid}@field-tests.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
}
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql'])
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
 const path=`/tables/${table.id}`;
 const meta=()=>api(owner,'GET',`${path}/metadata`);
 return {owner,guest,path,meta};
}

test('capabilities advertise fieldEdit and other routes still work',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.fieldEdit,true);
 assert.ok((await f.meta()).fields.length>=1);
});

test('owner renames, changes type and options; invalid cells roll back',async()=>{
 const f=await setup();let m=await f.meta();
 const status=await api(f.owner,'POST',`${f.path}/fields`,request({name:'Status',type:'SELECT',options:['A','B'],expectedSchemaVersion:m.schemaVersion}));
 m=await f.meta();
 await api(f.owner,'POST',`${f.path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{[status.id]:'A'}}));
 await assert.rejects(api(f.guest,'PATCH',`${f.path}/fields/${status.id}`,{name:'X'}),/FORBIDDEN/);
 const added=await api(f.owner,'PATCH',`${f.path}/fields/${status.id}`,{name:'Trạng thái',options:['A','B','C'],expectedSchemaVersion:m.schemaVersion});
 assert.deepEqual(added.options,['A','B','C']);
 await assert.rejects(api(f.owner,'PATCH',`${f.path}/fields/${status.id}`,{options:['B']}),/VALIDATION/);
 await assert.rejects(api(f.owner,'PATCH',`${f.path}/fields/${status.id}`,{type:'NUMBER'}),/VALIDATION/);
 const text=await api(f.owner,'PATCH',`${f.path}/fields/${status.id}`,{type:'TEXT'});
 assert.equal(text.type,'TEXT');assert.deepEqual(text.options,[]);
 m=await f.meta();assert.equal(m.fields.find(x=>x.id===status.id).name,'Trạng thái');
});

test('delete removes the field from records and views',async()=>{
 const f=await setup();let m=await f.meta();
 const note=await api(f.owner,'POST',`${f.path}/fields`,request({name:'Note',type:'TEXT',options:[],expectedSchemaVersion:m.schemaVersion}));
 m=await f.meta();
 const rec=await api(f.owner,'POST',`${f.path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{[note.id]:'hi'}}));
 await db.query("update ws_views set field_ids=$1::jsonb,sort=$2::jsonb where table_id=$3",[JSON.stringify([note.id]),JSON.stringify([{fieldId:note.id,direction:'asc'}]),f.path.split('/')[2]]);
 await api(f.owner,'DELETE',`${f.path}/fields/${note.id}`,{expectedSchemaVersion:m.schemaVersion});
 const row=(await db.query('select cells from ws_records where id=$1',[rec.id])).rows[0];
 assert.equal(note.id in row.cells,false);
 const view=(await db.query('select field_ids,sort from ws_views where table_id=$1',[f.path.split('/')[2]])).rows[0];
 assert.deepEqual([view.field_ids,view.sort],[[],[]]);
 m=await f.meta();assert.equal(m.fields.some(x=>x.id===note.id),false);
});
