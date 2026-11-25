import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@trash.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('bulk delete goes to the trash and restore brings rows back',async()=>{
 const owner=crypto.randomUUID(),viewer=crypto.randomUUID();
 await api(viewer,'GET','/me');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'VIEWER')",[ws.id,viewer]);
 const path=`/tables/${table.id}`;const m=await api(owner,'GET',`${path}/metadata`);const name=m.fields[0].id;
 const {ids}=await api(owner,'POST',`${path}/records/batch`,request({expectedSchemaVersion:m.schemaVersion,records:[1,2,3,4].map(i=>({cells:{[name]:`r${i}`}}))}));
 const count=async()=>(await api(owner,'POST',`${path}/records/query`,{limit:50})).items.length;
 await assert.rejects(api(viewer,'POST',`${path}/records/delete`,{ids:[ids[0]]}),/FORBIDDEN/);
 assert.equal((await api(owner,'POST',`${path}/records/delete`,{ids:[ids[0],ids[1],crypto.randomUUID()]})).changed,2);
 assert.equal((await api(owner,'POST',`${path}/records/delete`,{ids:[ids[0]]})).changed,0,'deleting twice is a no-op');
 assert.equal(await count(),2);
 const trash=await api(viewer,'GET',`${path}/records/trash`);
 assert.deepEqual(trash.items.map(r=>r.cells[name]).sort(),['r1','r2']);
 assert.equal((await api(owner,'POST',`${path}/records/restore`,{ids:[ids[1]]})).changed,1);
 assert.equal(await count(),3);
 const history=await api(owner,'GET',`${path}/records/${ids[1]}/history`);
 assert.deepEqual(history.items.slice(0,2).map(e=>e.action),['RESTORED','ARCHIVED']);
 await assert.rejects(api(owner,'POST',`${path}/records/delete`,{ids:[]}),/VALIDATION/);
 await assert.rejects(api(crypto.randomUUID(),'GET',`${path}/records/trash`),/FORBIDDEN/);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
