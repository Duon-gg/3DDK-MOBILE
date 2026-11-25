import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={},name=uid)=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@search.invalid`,name,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('search finds member names and dd/MM dates',async()=>{
 const owner=crypto.randomUUID(),khang=crypto.randomUUID();
 await api(owner,'GET','/me',{},'Dương');await api(khang,'GET','/me',{},'Nguyễn Bảo Khang');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[ws.id,khang]);
 const path=`/tables/${table.id}`;let m=await api(owner,'GET',`${path}/metadata`);
 const who=await api(owner,'POST',`${path}/fields`,request({name:'Who',type:'USER',options:[],expectedSchemaVersion:m.schemaVersion}));
 m=await api(owner,'GET',`${path}/metadata`);
 const due=await api(owner,'POST',`${path}/fields`,request({name:'Due',type:'DATE',options:[],expectedSchemaVersion:m.schemaVersion}));
 m=await api(owner,'GET',`${path}/metadata`);
 const add=cells=>api(owner,'POST',`${path}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells}));
 await add({[who.id]:khang,[due.id]:'2026-11-25'});await add({[who.id]:owner,[due.id]:'2026-12-01'});await add({});
 const q=async search=>{const r=await api(owner,'POST',`${path}/records/query`,{limit:50,search});return [r.items.length,r.total];};
 assert.deepEqual(await q('khang'),[1,1]);
 assert.deepEqual(await q('25/11/2026'),[1,1]);
 assert.deepEqual(await q('1/12'),[1,1]);
 assert.deepEqual(await q('2026-12'),[1,1]);
 assert.deepEqual(await q('32/13'),[0,0]);
});
