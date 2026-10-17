import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
let db;
const uid=()=>crypto.randomUUID();
const body=(values={})=>({requestId:uid(),...values});
async function api(user,method,path,values={}) {
 return (await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[user,user+'@example.com',user,method,path,JSON.stringify(values)])).rows[0].result;
}
async function fixture(template){
 const owner=uid();const w=await api(owner,'POST','/workspaces',body({name:'Công ty'}));
 const base=await api(owner,'POST',`/workspaces/${w.id}/bases`,body({name:'Dự án'}));
 const request=body({name:'Công việc',...(template?{template}: {})});
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request);
 return {owner,w,base,table,request};
}
async function member(f,role='EDITOR'){
 const user=uid();const invite=await api(f.owner,'POST',`/workspaces/${f.w.id}/invites`,body({email:user+'@example.com',role}));
 await api(user,'POST',`/invitations/${invite.id}/accept`,body());return {user,invite};
}
before(async()=>{
 db=new PGlite();await db.exec('create role anon; create role authenticated; create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql'])await db.exec(await readFile(new URL('../supabase/migrations/'+name,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});
test('atomic task template is replayable, typed, and private from old clients',async()=>{
 const f=await fixture('TASKS');const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);
 assert.equal(meta.fields.length,6);assert.equal(meta.views.length,1);assert.ok(meta.fields.some(x=>x.type==='USER'));
 assert.deepEqual(await api(f.owner,'POST',`/bases/${f.base.id}/tables`,f.request),f.table);
 await assert.rejects(api(f.owner,'GET',`/tables/${f.table.id}`),/CLIENT_UPGRADE_REQUIRED/);
 const {user}=await member(f);await assert.rejects(api(user,'POST',`/bases/${f.base.id}/tables`,body({name:'Denied',template:'TASKS'})),/FORBIDDEN/);
 const before=(await api(f.owner,'GET',`/bases/${f.base.id}`)).tables.length;
 await assert.rejects(api(f.owner,'POST',`/bases/${f.base.id}/tables`,body({name:'Bad',template:'UNKNOWN'})),/VALIDATION/);
 assert.equal((await api(f.owner,'GET',`/bases/${f.base.id}`)).tables.length,before);
});
test('membership changes are versioned and accepted invites cannot rejoin after removal',async()=>{
 const f=await fixture();const {user,invite}=await member(f);
 let w=await api(f.owner,'GET',`/workspaces/${f.w.id}`);const m=w.members.find(x=>x.uid===user);
 await api(f.owner,'PATCH',`/workspaces/${f.w.id}/members/${user}`,body({role:'VIEWER',expectedVersion:m.version}));
 await assert.rejects(api(user,'POST',`/tables/${f.table.id}/records`,body({cells:{}})),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'DELETE',`/workspaces/${f.w.id}/members/${user}`,body({expectedVersion:m.version})),/CONFLICT/);
 w=await api(f.owner,'GET',`/workspaces/${f.w.id}`);
 await api(f.owner,'DELETE',`/workspaces/${f.w.id}/members/${user}`,body({expectedVersion:w.members.find(x=>x.uid===user).version}));
 await assert.rejects(api(user,'GET',`/tables/${f.table.id}/metadata`),/FORBIDDEN/);
 await assert.rejects(api(user,'POST',`/invitations/${invite.id}/accept`,body()),/INVALID_STATE/);
});
test('views are configurations of shared records; viewer personal views do not grant writes',async()=>{
 const f=await fixture();const {user}=await member(f,'VIEWER');const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);
 const v=await api(user,'POST',`/tables/${f.table.id}/views`,body({name:'Riêng',visibility:'PERSONAL',type:'GRID'}));
 await assert.rejects(api(user,'POST',`/tables/${f.table.id}/views`,body({name:'Chung',visibility:'SHARED'})),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'PATCH',`/tables/${f.table.id}/views/${v.id}`,body({name:'Other',expectedVersion:v.version})),/FORBIDDEN/);
 await assert.rejects(api(user,'POST',`/tables/${f.table.id}/records`,body({cells:{}})),/FORBIDDEN/);
 const q=await api(user,'POST',`/tables/${f.table.id}/records/query`,{viewId:v.id,limit:10});assert.deepEqual(q.items,[]);
 assert.equal(meta.views.length,1);
});
test('query validates typed filters and cursors and orders null dates last across pages',async()=>{
 const f=await fixture();const date=await api(f.owner,'POST',`/tables/${f.table.id}/fields`,body({name:'Hạn',type:'DATE'}));
 const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);const title=meta.fields.find(x=>x.type==='TEXT');
 for(const [name,value] of [['A',null],['B','2026-10-20'],['C','2026-10-17'],['D','2026-10-17']])await api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[title.id]:name,[date.id]:value}}));
 const query={sort:[{fieldId:date.id,direction:'asc'}],limit:2};const first=await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,query);
 assert.equal(first.items.length,2);assert.ok(first.nextCursor);
 const second=await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{...query,cursor:first.nextCursor});
 assert.equal(second.items.at(-1).cells[title.id],'A');assert.equal(new Set([...first.items,...second.items].map(x=>x.id)).size,4);
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{...query,search:'other',cursor:first.nextCursor}),/CURSOR_STALE/);
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{filter:{op:'and',conditions:[{fieldId:uid(),operator:'eq',value:'x'}]}}),/VALIDATION/);
});
test('cell patch checks schema and record versions and writes only one history event',async()=>{
 const f=await fixture();const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);const field=meta.fields[0];
 const r=await api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[field.id]:'Before'},expectedSchemaVersion:meta.schemaVersion}));
 const update=body({changes:{[field.id]:'After'},expectedVersion:r.version,expectedSchemaVersion:meta.schemaVersion});
 const next=await api(f.owner,'PATCH',`/tables/${f.table.id}/records/${r.id}/cells`,update);
 assert.equal(next.cells[field.id],'After');assert.deepEqual(await api(f.owner,'PATCH',`/tables/${f.table.id}/records/${r.id}/cells`,update),next);
 await assert.rejects(api(f.owner,'PATCH',`/tables/${f.table.id}/records/${r.id}/cells`,body({...update,requestId:uid()})),/CONFLICT/);
 const history=await api(f.owner,'GET',`/tables/${f.table.id}/records/${r.id}/history`);assert.equal(history.items.length,2);
 assert.equal(history.items[0].changes[field.id].after,'After');
 await api(f.owner,'POST',`/tables/${f.table.id}/fields`,body({name:'New',type:'TEXT'}));
 await assert.rejects(api(f.owner,'PATCH',`/tables/${f.table.id}/records/${r.id}/cells`,body({changes:{[field.id]:'Lost'},expectedVersion:next.version,expectedSchemaVersion:meta.schemaVersion})),/SCHEMA_CHANGED/);
});
test('USER assignment rejects other workspaces and accepts active members',async()=>{
 const f=await fixture('TASKS');const {user}=await member(f);const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);
 const assignee=meta.fields.find(x=>x.type==='USER');const title=meta.fields.find(x=>x.systemKey==='title');
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[title.id]:'X',[assignee.id]:uid()},expectedSchemaVersion:meta.schemaVersion})),/VALIDATION/);
 const rec=await api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[title.id]:'X',[assignee.id]:user},expectedSchemaVersion:meta.schemaVersion}));
 assert.equal(rec.cells[assignee.id],user);
 await assert.rejects(api(f.owner,'PATCH',`/tables/${f.table.id}/records/${rec.id}`,body({cells:{},expectedVersion:rec.version})),/CLIENT_UPGRADE_REQUIRED/);
});
test('ownership transfer leaves exactly one owner and is replayable',async()=>{
 const f=await fixture();const {user}=await member(f);const w=await api(f.owner,'GET',`/workspaces/${f.w.id}`);
 const request=body({targetUid:user,expectedVersion:w.members.find(x=>x.uid===f.owner).version});
 await api(f.owner,'POST',`/workspaces/${f.w.id}/transfer-ownership`,request);
 await api(f.owner,'POST',`/workspaces/${f.w.id}/transfer-ownership`,request);
 const next=await api(user,'GET',`/workspaces/${f.w.id}`);assert.equal(next.members.filter(x=>x.role==='OWNER').length,1);assert.equal(next.role,'OWNER');
});
test('descending typed keyset pages match one query, including duplicate and empty keys',async()=>{
 const f=await fixture();const number=await api(f.owner,'POST',`/tables/${f.table.id}/fields`,body({name:'Số',type:'NUMBER'}));
 const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);const title=meta.fields[0];
 for(const [name,value] of [['a',null],['b',3],['c',3],['d',0],['e',-2],['f',null]])await api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[title.id]:name,[number.id]:value}}));
 const sort=[{fieldId:number.id,direction:'desc'},{fieldId:title.id,direction:'desc'}];
 const all=await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{sort,limit:100});
 let cursor=null;const ids=[];
 do{const page=await api(f.owner,'POST',`/tables/${f.table.id}/records/query`,{sort,limit:2,cursor});ids.push(...page.items.map(x=>x.id));cursor=page.nextCursor;}while(cursor);
 assert.deepEqual(ids,all.items.map(x=>x.id));assert.deepEqual(all.items.map(x=>x.cells[number.id]),[3,3,0,-2,null,null]);
});
test('null schema version cannot bypass new field protection and invalid views roll back',async()=>{
 const f=await fixture('TASKS');const meta=await api(f.owner,'GET',`/tables/${f.table.id}/metadata`);const title=meta.fields.find(x=>x.systemKey==='title');
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/records`,body({cells:{[title.id]:'X'},expectedSchemaVersion:null})),/SCHEMA_CHANGED/);
 await assert.rejects(api(f.owner,'POST',`/tables/${f.table.id}/views`,body({name:'Bad',visibility:'SHARED',filter:{op:'and',conditions:[{fieldId:uid(),operator:'eq',value:'x'}]}})),/VALIDATION/);
 assert.equal((await api(f.owner,'GET',`/tables/${f.table.id}/metadata`)).views.length,1);
});
