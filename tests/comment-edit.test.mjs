import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@comments.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql','202611250003_comment_edit.sql'])
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
 const m=await api(owner,'GET',`/tables/${table.id}/metadata`);
 const rec=await api(owner,'POST',`/tables/${table.id}/records`,request({expectedVersion:1,expectedSchemaVersion:m.schemaVersion,cells:{}}));
 return {owner,guest,url:`/tables/${table.id}/records/${rec.id}/comments`};
}

test('author edits own comment; others cannot',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.commentEdit,true);
 const [c]=(await api(f.guest,'POST',f.url,{body:'first'})).comments;
 assert.equal(c.editedAt,null);
 await assert.rejects(api(f.owner,'PATCH',`${f.url}/${c.id}`,{body:'hijack'}),/FORBIDDEN/);
 await assert.rejects(api(f.guest,'PATCH',`${f.url}/${c.id}`,{body:'  '}),/VALIDATION/);
 await assert.rejects(api(f.guest,'PATCH',`${f.url}/${c.id}`,{body:'x',mentions:['stranger']}),/VALIDATION/);
 const [e]=(await api(f.guest,'PATCH',`${f.url}/${c.id}`,{body:' fixed @owner ',mentions:[f.owner]})).comments;
 assert.deepEqual([e.body,e.mentions],['fixed @owner',[f.owner]]);assert.ok(e.editedAt);
 assert.ok((await api(f.owner,'GET',f.url)).comments[0].editedAt);
 await assert.rejects(api(crypto.randomUUID(),'PATCH',`${f.url}/${c.id}`,{body:'x'}),/FORBIDDEN/);
 await assert.rejects(api(f.guest,'PATCH',`${f.url}/${crypto.randomUUID()}`,{body:'x'}),/NOT_FOUND/);
});

test('author or base owner deletes a comment',async()=>{
 const f=await setup();
 const a=(await api(f.owner,'POST',f.url,{body:'mine'})).comments[0];
 const b=(await api(f.guest,'POST',f.url,{body:'theirs'})).comments[1];
 await assert.rejects(api(f.guest,'DELETE',`${f.url}/${a.id}`),/FORBIDDEN/);
 assert.deepEqual((await api(f.owner,'DELETE',`${f.url}/${b.id}`)).comments.map(c=>c.body),['mine']);
 assert.deepEqual((await api(f.owner,'DELETE',`${f.url}/${a.id}`)).comments,[]);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
