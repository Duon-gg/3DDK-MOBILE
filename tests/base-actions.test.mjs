import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
async function api(uid,method,path,body={}) {
 return (await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',
  [uid,`${uid}@base-tests.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
}
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql'])
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});
test('workspace trash is owner-only, blocks descendants and restores without reviving deleted bases',async()=>{
 const f=await setup(), path=`/workspaces/${f.source.id}`;
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[f.source.id,f.guest]);
 await api(f.owner,'DELETE',`/bases/${f.other.id}`,request({expectedVersion:1}));
 const body=request({expectedVersion:1});
 await assert.rejects(api(f.guest,'DELETE',path,body),/FORBIDDEN/);
 await api(f.owner,'DELETE',path,body);
 await api(f.owner,'DELETE',path,body);
 assert.equal((await api(f.owner,'GET','/workspaces')).some(x=>x.id===f.source.id),false);
 assert.equal((await api(f.owner,'GET','/workspace-trash')).length,1);
 assert.equal((await api(f.guest,'GET','/workspace-trash')).length,0);
 for(const who of [f.owner,f.guest]) await assert.rejects(api(who,'GET',`/tables/${f.table.id}/metadata`),/NOT_FOUND/);
 await assert.rejects(api(f.guest,'POST',`${path}/restore`,request({expectedVersion:2})),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'POST',`${path}/restore`,request({expectedVersion:1})),/CONFLICT/);
 await api(f.owner,'POST',`${path}/restore`,request({expectedVersion:2}));
 await api(f.guest,'GET',`/tables/${f.table.id}/metadata`);
 await assert.rejects(api(f.owner,'GET',`/bases/${f.other.id}`),/NOT_FOUND/);
});
test('workspace rename requires owner, validates name and version, and retries once',async()=>{
 const f=await setup();
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[f.source.id,f.guest]);
 const path=`/workspaces/${f.source.id}`;
 const body=request({name:'New name',expectedVersion:1});
 await assert.rejects(api(f.guest,'PATCH',path,body),/FORBIDDEN/);
 await assert.rejects(api(f.owner,'PATCH',path,request({name:'  ',expectedVersion:1})),/VALIDATION/);
 const result=await api(f.owner,'PATCH',path,body);
 assert.equal(result.version,2);assert.deepEqual(await api(f.owner,'PATCH',path,body),result);
 assert.equal((await api(f.owner,'GET',path)).name,'New name');
 await assert.rejects(api(f.owner,'PATCH',path,request({name:'Stale',expectedVersion:1})),/CONFLICT/);
});
test('trashed workspace hides direct shares and pending invitations and rejects move-in',async()=>{
 const f=await setup(),path=`/workspaces/${f.source.id}`;
 const direct=await api(f.owner,'POST',`/bases/${f.base.id}/invites`,request({email:`${f.guest}@base-tests.invalid`,role:'EDITOR',expectedVersion:1}));
 await api(f.guest,'POST',`/base-invitations/${direct.id}/accept`,request());
 const waiting=crypto.randomUUID(); await api(waiting,'GET','/me');
 const invite=await api(f.owner,'POST',`${path}/invites`,request({email:`${waiting}@base-tests.invalid`,role:'VIEWER'}));
 const incoming=await api(f.owner,'POST',`/workspaces/${f.target.id}/bases`,request({name:'Incoming'}));
 await api(f.owner,'DELETE',path,request({expectedVersion:1}));
 assert.deepEqual(await api(f.guest,'GET','/shared-bases'),[]);
 assert.deepEqual(await api(waiting,'GET','/invitations'),[]);
 await assert.rejects(api(waiting,'POST',`/invitations/${invite.id}/accept`,request()),/NOT_FOUND/);
 await assert.rejects(api(f.guest,'GET',`/tables/${f.table.id}/metadata`),/NOT_FOUND/);
 await assert.rejects(api(f.owner,'POST',`/bases/${incoming.id}/move`,request({workspaceId:f.source.id,expectedVersion:1})),/NOT_FOUND/);
 await api(f.owner,'POST',`${path}/restore`,request({expectedVersion:2}));
 assert.equal((await api(f.guest,'GET','/shared-bases')).length,1);
});
async function setup(){
 const owner=crypto.randomUUID(),guest=crypto.randomUUID();
 await api(guest,'GET','/me');
 const source=await api(owner,'POST','/workspaces',request({name:'Source'}));
 const target=await api(owner,'POST','/workspaces',request({name:'Target'}));
 const base=await api(owner,'POST',`/workspaces/${source.id}/bases`,request({name:'Private base'}));
 const other=await api(owner,'POST',`/workspaces/${source.id}/bases`,request({name:'Other base'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'Work'}));
 return {owner,guest,source,target,base,other,table};
}
test('sharing a base does not grant workspace or sibling access',async()=>{
 const f=await setup();
 const invite=await api(f.owner,'POST',`/bases/${f.base.id}/invites`,request({email:`${f.guest}@base-tests.invalid`,role:'VIEWER',expectedVersion:1}));
 await api(f.guest,'POST',`/base-invitations/${invite.id}/accept`,request());
 assert.equal((await api(f.guest,'GET',`/bases/${f.base.id}`)).role,'VIEWER');
 await api(f.guest,'GET',`/tables/${f.table.id}/metadata`);
 await assert.rejects(api(f.guest,'GET',`/bases/${f.other.id}`),/FORBIDDEN/);
 await assert.rejects(api(f.guest,'GET',`/workspaces/${f.source.id}`),/FORBIDDEN/);
 await assert.rejects(api(f.guest,'POST',`/bases/${f.base.id}/tables`,request({name:'Denied'})),/FORBIDDEN/);
});
test('moving preserves direct collaborators and rejects stale versions',async()=>{
 const f=await setup();
 const invite=await api(f.owner,'POST',`/bases/${f.base.id}/invites`,request({email:`${f.guest}@base-tests.invalid`,role:'EDITOR',expectedVersion:1}));
 await api(f.guest,'POST',`/base-invitations/${invite.id}/accept`,request());
 const before=await api(f.owner,'GET',`/bases/${f.base.id}`);
 const body=request({workspaceId:f.target.id,expectedVersion:before.version});
 await api(f.owner,'POST',`/bases/${f.base.id}/move`,body);
 assert.equal((await api(f.guest,'GET',`/bases/${f.base.id}`)).workspaceId,f.target.id);
 await assert.rejects(api(f.owner,'POST',`/bases/${f.base.id}/move`,request({workspaceId:f.source.id,expectedVersion:before.version})),/CONFLICT/);
});
test('duplicate is idempotent and deletion blocks table reads until restore',async()=>{
 const f=await setup();
 const before=await api(f.owner,'GET',`/bases/${f.base.id}`);
 const body=request({name:'Copy',expectedVersion:before.version});
 const copy=await api(f.owner,'POST',`/bases/${f.base.id}/duplicate`,body);
 assert.equal((await api(f.owner,'POST',`/bases/${f.base.id}/duplicate`,body)).id,copy.id);
 assert.notEqual(copy.id,f.base.id);
 assert.equal((await api(f.owner,'GET',`/bases/${copy.id}`)).tables.length,1);
 const deletion=request({expectedVersion:before.version});
 const deleted=await api(f.owner,'DELETE',`/bases/${f.base.id}`,deletion);
 assert.deepEqual(await api(f.owner,'DELETE',`/bases/${f.base.id}`,deletion),deleted);
 await assert.rejects(api(f.owner,'GET',`/tables/${f.table.id}/metadata`),/NOT_FOUND/);
 await api(f.owner,'POST',`/bases/${f.base.id}/restore`,request({expectedVersion:before.version+1}));
 assert.equal((await api(f.owner,'GET',`/bases/${f.base.id}`)).id,f.base.id);
});

test('revocation, inherited access changes, and owner-only administration',async()=>{
 const f=await setup();
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR')",[f.source.id,f.guest]);
 for(const [method,suffix,values] of [['POST','/move',{workspaceId:f.target.id}],['POST','/duplicate',{name:'Denied'}],['DELETE','',{}],['POST','/invites',{email:'x@example.com',role:'EDITOR'}]])
  await assert.rejects(api(f.guest,method,`/bases/${f.base.id}${suffix}`,request({expectedVersion:1,...values})),/FORBIDDEN/);
 await api(f.owner,'POST',`/bases/${f.base.id}/move`,request({workspaceId:f.target.id,expectedVersion:1}));
 await assert.rejects(api(f.guest,'GET',`/tables/${f.table.id}/metadata`),/FORBIDDEN/);
 const invite=await api(f.owner,'POST',`/bases/${f.base.id}/invites`,request({email:`${f.guest}@base-tests.invalid`,role:'VIEWER',expectedVersion:2}));
 await assert.rejects(api(crypto.randomUUID(),'POST',`/base-invitations/${invite.id}/accept`,request()),/FORBIDDEN/);
 await api(f.guest,'POST',`/base-invitations/${invite.id}/accept`,request());
 await api(f.owner,'DELETE',`/bases/${f.base.id}/members/${f.guest}`,request({expectedVersion:4}));
 await assert.rejects(api(f.guest,'GET',`/bases/${f.base.id}`),/FORBIDDEN/);
 for(const path of [`/tables/${f.table.id}/metadata`,`/tables/${f.table.id}/views`,`/tables/${f.table.id}/records/${crypto.randomUUID()}/history`])
  await assert.rejects(api(f.guest,'GET',path),/FORBIDDEN/);
 await assert.rejects(api(f.guest,'POST',`/tables/${f.table.id}/records/query`,{}),/FORBIDDEN/);
 assert.deepEqual(await api(f.guest,'GET','/shared-bases'),[]);
 await assert.rejects(api(f.guest,'POST',`/bases/${f.base.id}/restore`,request({expectedVersion:5})),/FORBIDDEN/);
});

test('unknown base cannot report a successful mutation',async()=>{
 const f=await setup();
 await assert.rejects(api(f.owner,'DELETE',`/bases/${crypto.randomUUID()}`,request()),/NOT_FOUND/);
});

test('duplicate remaps fields and views without copying private access or archived records',async()=>{
 const f=await setup();
 const table=await api(f.owner,'POST',`/bases/${f.base.id}/tables`,request({name:'Tasks',template:'TASKS'}));
 const meta=await api(f.owner,'GET',`/tables/${table.id}/metadata`);
 const field=meta.fields.find(x=>x.type==='TEXT');
 const recordId=crypto.randomUUID();
 await db.query('insert into ws_records(id,table_id,cells) values($1,$2,$3::jsonb)',[recordId,table.id,JSON.stringify({[field.id]:'Keep'})]);
 await db.query("insert into ws_records(table_id,cells,archived_at) values($1,'{}',now())",[table.id]);
 await db.query("insert into ws_base_members(base_id,uid,role) values($1,$2,'EDITOR')",[f.base.id,f.guest]);
 await db.query("insert into ws_views(table_id,name,visibility,owner_uid,filter,field_ids) values($1,'Private','PERSONAL',$2,'{\"op\":\"and\",\"conditions\":[]}', '[]')",[table.id,f.guest]);
 const copy=await api(f.owner,'POST',`/bases/${f.base.id}/duplicate`,request({name:'Copy',expectedVersion:1}));
 const copied=(await api(f.owner,'GET',`/bases/${copy.id}`)).tables.find(x=>x.name==='Tasks');
 const copiedMeta=await api(f.owner,'GET',`/tables/${copied.id}/metadata`);
 assert.equal(copiedMeta.views.some(x=>x.name==='Private'),false);
 const rows=(await db.query('select cells from ws_records where table_id=$1',[copied.id])).rows;
 assert.equal(rows.length,1);
 assert.equal(rows[0].cells[copiedMeta.fields.find(x=>x.name===field.name).id],'Keep');
 assert.equal(rows[0].cells[field.id],undefined);
 await assert.rejects(api(f.guest,'GET',`/bases/${copy.id}`),/FORBIDDEN/);
});
