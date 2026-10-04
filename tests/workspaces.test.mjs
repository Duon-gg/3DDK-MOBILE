import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
let db;
const uid=()=>crypto.randomUUID();
const body=(x={})=>({requestId:uid(),...x});
async function api(user,method,path,data={}) {
 return (await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[user,user+'@example.com',user,method,path,JSON.stringify(data)])).rows[0].result;
}
async function fixture(){
 const owner=uid(); const w=await api(owner,'POST','/workspaces',body({name:'Nhóm'}));
 const base=await api(owner,'POST',`/workspaces/${w.id}/bases`,body({name:'Dự án'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,body({name:'Công việc'}));
 return {owner,w,base,table};
}
before(async()=>{db=new PGlite();await db.exec('create role anon; create role authenticated; create role service_role;');await db.exec(await readFile(new URL('../supabase/migrations/202610040001_workspaces.sql',import.meta.url),'utf8'));});
after(async()=>{await db?.close();});
test('new verified user needs no invitation and duplicate create is idempotent',async()=>{
 const user=uid();assert.equal((await api(user,'GET','/me')).uid,user);assert.deepEqual(await api(user,'GET','/workspaces'),[]);
 const request=body({name:'Riêng tư'});const w=await api(user,'POST','/workspaces',request);
 assert.deepEqual(await api(user,'POST','/workspaces',request),w);
 assert.equal((await api(user,'GET','/workspaces')).length,1);
 await assert.rejects(api(user,'POST','/workspaces',{...request,name:'Khác'}),/CONFLICT/);
});
test('cross workspace paths and forged roles cannot expose data',async()=>{
 const {owner,w,base,table}=await fixture();const outsider=uid();
 for(const path of [`/workspaces/${w.id}`,`/bases/${base.id}`,`/tables/${table.id}`]) await assert.rejects(api(outsider,'GET',path),/FORBIDDEN/);
 await assert.rejects(api(outsider,'POST',`/tables/${table.id}/records`,body({role:'OWNER',cells:{}})),/FORBIDDEN/);
 assert.equal((await api(owner,'GET',`/workspaces/${w.id}`)).role,'OWNER');
});
test('invites require matching email and viewer cannot write; revoked invite cannot accept',async()=>{
 const {owner,w,table}=await fixture();const viewer=uid();
 const inv=await api(owner,'POST',`/workspaces/${w.id}/invites`,body({email:viewer+'@example.com',role:'VIEWER'}));
 await assert.rejects(api(uid(),'POST',`/invitations/${inv.id}/accept`,body()),/FORBIDDEN/);
 await api(viewer,'POST',`/invitations/${inv.id}/accept`,body());
 assert.equal((await api(viewer,'GET',`/tables/${table.id}`)).role,'VIEWER');
 await assert.rejects(api(viewer,'POST',`/tables/${table.id}/records`,body({cells:{}})),/FORBIDDEN/);
 const invitee=uid();const old=await api(owner,'POST',`/workspaces/${w.id}/invites`,body({email:invitee+'@example.com',role:'EDITOR'}));
 await api(owner,'DELETE',`/workspaces/${w.id}/invites/${old.id}`,body());
 await assert.rejects(api(invitee,'POST',`/invitations/${old.id}/accept`,body()),/INVALID_STATE/);
});
test('typed cells, immutable field type, version conflict and replay',async()=>{
 const {owner,table}=await fixture();const f=await api(owner,'POST',`/tables/${table.id}/fields`,body({name:'Điểm',type:'NUMBER'}));
 await assert.rejects(api(owner,'POST',`/tables/${table.id}/records`,body({cells:{[f.id]:'abc'}})),/VALIDATION/);
 await assert.rejects(api(owner,'POST',`/tables/${table.id}/records`,body({cells:{[uid()]:'unknown'}})),/VALIDATION/);
 const rec=await api(owner,'POST',`/tables/${table.id}/records`,body({cells:{[f.id]:4}}));
 const update=body({expectedVersion:rec.version,cells:{[f.id]:5}});
 const next=await api(owner,'PATCH',`/tables/${table.id}/records/${rec.id}`,update);
 assert.equal(next.version,2);assert.equal(next.cells[f.id],5);
 assert.deepEqual(await api(owner,'PATCH',`/tables/${table.id}/records/${rec.id}`,update),next);
 await assert.rejects(api(owner,'PATCH',`/tables/${table.id}/records/${rec.id}`,body({expectedVersion:1,cells:{[f.id]:9}})),/CONFLICT/);
});
test('date, select and checkbox validation and private direct access',async()=>{
 const {owner,table}=await fixture();
 for(const [type,good,bad,options] of [['DATE','2026-10-04','2026-02-30',[]],['CHECKBOX',true,'true',[]],['SELECT','Đang làm','Khác',['Đang làm','Xong']]]){
 const f=await api(owner,'POST',`/tables/${table.id}/fields`,body({name:type,type,options}));
 await api(owner,'POST',`/tables/${table.id}/records`,body({cells:{[f.id]:good}}));
 await assert.rejects(api(owner,'POST',`/tables/${table.id}/records`,body({cells:{[f.id]:bad}})),/VALIDATION|date\/time field|date out of range/);
 }
 const denied=await db.query("select has_table_privilege('anon','ws_records','SELECT') allowed, has_function_privilege('authenticated','workspace_api(text,text,text,text,text,jsonb)','EXECUTE') rpc");
 assert.equal(denied.rows[0].allowed,false);assert.equal(denied.rows[0].rpc,false);
});
