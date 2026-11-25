import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@marks.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('stars and recents are saved per account and validated',async()=>{
 const a=crypto.randomUUID(),b=crypto.randomUUID(),base=`bases/${crypto.randomUUID()}`;
 assert.deepEqual((await api(a,'GET','/me/marks')).items,[]);
 await api(a,'PUT','/me/marks',{kind:'v2-base-star',id:base,json:'{"starred":true}'});
 const out=await api(a,'PUT','/me/marks',{kind:'v2-base-star',id:base,json:'{"starred":false}'});
 assert.deepEqual(out.items,[{kind:'v2-base-star',id:base,json:'{"starred":false}'}]);
 assert.deepEqual((await api(b,'GET','/me/marks')).items,[]);
 for(const bad of [{kind:'x',id:base,json:'{}'},{kind:'v2-home',id:'tables/../x',json:'{}'},{kind:'v2-home',id:base,json:'x'.repeat(2001)},{kind:'v2-home',id:base}])
  await assert.rejects(api(a,'PUT','/me/marks',bad),/VALIDATION/,JSON.stringify(bad).slice(0,40));
 assert.equal((await api(a,'GET','/capabilities')).features.userMarks,true);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
