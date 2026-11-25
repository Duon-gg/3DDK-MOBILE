import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@total.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('first query page reports the full match count',async()=>{
 const owner=crypto.randomUUID();
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'B'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 const path=`/tables/${table.id}`;const m=await api(owner,'GET',`${path}/metadata`);const name=m.fields[0].id;
 await api(owner,'POST',`${path}/records/batch`,request({expectedSchemaVersion:m.schemaVersion,records:Array.from({length:120},(_,i)=>({cells:{[name]:i%3?`a${i}`:`web ${i}`}}))}));
 const first=await api(owner,'POST',`${path}/records/query`,{limit:50});
 assert.equal(first.items.length,50);assert.equal(first.total,120);assert.equal(first.hasMore,true);
 assert.equal((await api(owner,'POST',`${path}/records/query`,{limit:50,search:'web'})).total,40);
 assert.equal((await api(owner,'POST',`${path}/records/query`,{limit:50,filter:{op:'and',conditions:[{fieldId:name,operator:'contains',value:'web'}]}})).total,40);
 assert.equal((await api(owner,'POST',`${path}/records/query`,{limit:50,cursor:first.nextCursor})).total,undefined);
 assert.equal((await api(owner,'GET','/capabilities')).features.queryTotal,true);
});
