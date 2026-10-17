import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const mutation=data=>({requestId:crypto.randomUUID(),...data});
async function api(user,method,path,data={}) {
 return (await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[user,`${user}@security.invalid`,'Security fixture',method,path,JSON.stringify(data)])).rows[0].result;
}
async function fixture() {
 const owner=crypto.randomUUID();
 const workspace=await api(owner,'POST','/workspaces',mutation({name:'Security fixture'}));
 const base=await api(owner,'POST',`/workspaces/${workspace.id}/bases`,mutation({name:'Base'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,mutation({name:'Table'}));
 const number=await api(owner,'POST',`/tables/${table.id}/fields`,mutation({name:'Number',type:'NUMBER'}));
 const meta=await api(owner,'GET',`/tables/${table.id}/metadata`);const title=meta.fields.find(field=>field.type==='TEXT');
 const record=await api(owner,'POST',`/tables/${table.id}/records`,mutation({expectedSchemaVersion:meta.schemaVersion,cells:{[title.id]:'Safe title',[number.id]:5}}));
 return {owner,workspace,table,meta,title,number,record};
}
async function viewer(f) {
 const user=crypto.randomUUID();const invite=await api(f.owner,'POST',`/workspaces/${f.workspace.id}/invites`,mutation({email:`${user}@security.invalid`,role:'VIEWER'}));
 await api(user,'POST',`/invitations/${invite.id}/accept`,mutation());return user;
}
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql']) await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('nested filters enforce 20 total predicates and reject missing/invalid operators and wrong types',async()=>{
 const f=await fixture();const query=filter=>api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{filter});
 const predicate={fieldId:f.number.id,operator:'eq',value:5};
 const group=conditions=>({op:'and',conditions});
 const allowed=group([group(Array(10).fill(predicate)),group(Array(10).fill(predicate))]);
 assert.equal((await query(allowed)).items.length,1);
 await assert.rejects(query(group([allowed,predicate])),/VALIDATION/);
 for(const invalid of [
  {fieldId:f.number.id,value:5},
  {fieldId:f.number.id,operator:'exec',value:5},
  {conditions:[predicate]},
  {op:'xor',conditions:[predicate]},
  {fieldId:f.number.id,operator:'eq',value:'5'},
  {fieldId:f.title.id,operator:'eq',value:5},
  {fieldId:f.number.id,operator:'isMe'},
 ]) await assert.rejects(query(invalid),/VALIDATION/);
});

test('cross-workspace projection, private view and cursor reuse cannot expose records',async()=>{
 const f=await fixture();const other=await fixture();const user=await viewer(f);
 await api(f.owner,'POST',`/tables/${f.table.id}/records`,mutation({expectedSchemaVersion:f.meta.schemaVersion,cells:{[f.title.id]:'Second'}}));
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{fieldIds:[other.title.id]}),/VALIDATION/);
 const view=await api(f.owner,'POST',`/tables/${f.table.id}/views`,mutation({name:'Owner private',visibility:'PERSONAL'}));
 const metadata=await api(user,'GET',`/tables/${f.table.id}/metadata`);
 assert.ok(!metadata.views.some(item=>item.id===view.id));
 await assert.rejects(api(user,'POST',`/tables/${f.table.id}/records/query`,{viewId:view.id}),/FORBIDDEN/);
 const first=await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{limit:1});assert.ok(first.nextCursor);
 await assert.rejects(api(user,'POST',`/tables/${f.table.id}/records/query`,{limit:1,cursor:first.nextCursor}),/CURSOR_STALE/);
 await assert.rejects(api(other.owner,'POST',`/tables/${other.table.id}/records/query`,{limit:1,cursor:first.nextCursor}),/CURSOR_STALE/);
});

test('POST query/history is read-only for viewers and creates no mutation replay or audit entries',async()=>{
 const f=await fixture();const user=await viewer(f);
 const counts=async()=> (await db.query('select (select count(*) from ws_events) events,(select count(*) from ws_requests) requests')).rows[0];
 const before=await counts();
 const query=await api(user,'POST',`/tables/${f.table.id}/records/query`,{limit:50});assert.equal(query.items.length,1);
 const history=await api(user,'POST',`/tables/${f.table.id}/records/${f.record.id}/history/query`,{});assert.equal(history.items.length,1);
 assert.deepEqual(await counts(),before);
 await assert.rejects(api(user,'PATCH',`/tables/${f.table.id}/records/${f.record.id}/cells`,mutation({expectedSchemaVersion:f.meta.schemaVersion,expectedVersion:1,changes:{[f.title.id]:'Forbidden'}})),/FORBIDDEN/);
});

test('SQL-looking values remain literal search/filter text and preserve rows',async()=>{
 const f=await fixture();const attack="x');drop table public.ws_records;--";
 for(const data of [{search:attack},{filter:{fieldId:f.title.id,operator:'contains',value:attack}},{filter:{fieldId:f.title.id,operator:'eq',value:attack}}]) {
  assert.equal((await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,data)).items.length,0);
 }
 assert.equal((await api(f.owner,'GET',`/tables/${f.table.id}/records/${f.record.id}`)).cells[f.title.id],'Safe title');
});

test('client roles cannot access P0 tables or helper RPCs; only dispatcher is service callable',async()=>{
 for(const role of ['anon','authenticated']) {
  for(const table of ['ws_views','ws_read_limits','ws_query_cursors']) {
   const row=(await db.query('select has_table_privilege($1,$2,\'SELECT,INSERT,UPDATE,DELETE\') allowed',[role,table])).rows[0];assert.equal(row.allowed,false);
  }
 }
 const helpers=(await db.query("select p.oid::regprocedure::text signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname like 'ws_p0_%'")).rows;
 assert.ok(helpers.length>=4);
 for(const {signature} of helpers) for(const role of ['anon','authenticated','service_role']) {
  assert.equal((await db.query("select has_function_privilege($1,$2,'EXECUTE') allowed",[role,signature])).rows[0].allowed,false);
 }
 assert.equal((await db.query("select has_function_privilege('service_role','workspace_api(text,text,text,text,text,jsonb)','EXECUTE') allowed")).rows[0].allowed,true);
});
