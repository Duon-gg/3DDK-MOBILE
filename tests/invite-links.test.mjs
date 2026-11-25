import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@links.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 for(const name of ['202610040001_workspaces.sql','202610170001_p0_workspaces.sql','202611130001_base_actions.sql','202611130002_workspace_actions.sql','202611140001_workspace_trash.sql','202611240001_field_edit.sql','202611250001_field_insert.sql','202611250002_invite_links.sql'])
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

async function setup(){
 const owner=crypto.randomUUID(),guest=crypto.randomUUID();
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'Base A'}));
 return {owner,guest,base:`/bases/${base.id}`,baseId:base.id};
}

test('owner creates, shares and revokes an invite link',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.inviteLinks,true);
 await assert.rejects(api(f.guest,'POST',`${f.base}/invite-link`,{role:'EDITOR'}),/FORBIDDEN/);
 const {inviteLink}=await api(f.owner,'POST',`${f.base}/invite-link`,{role:'VIEWER'});
 assert.match(inviteLink.token,/^[0-9a-f]{32}$/);
 const sharing=await api(f.owner,'GET',`${f.base}/sharing`);assert.equal(sharing.inviteLink.token,inviteLink.token);assert.equal(sharing.people.find(p=>p.uid===f.owner).role,'OWNER');
 const preview=await api(f.guest,'GET',`/invite-links/${inviteLink.token}`);
 assert.deepEqual([preview.baseName,preview.role,preview.currentRole],['Base A','VIEWER',null]);
 const joined=await api(f.guest,'POST',`/invite-links/${inviteLink.token}/accept`);
 assert.deepEqual([joined.name,joined.role],['Base A','VIEWER']);
 // A new link replaces the old one; the old token stops working.
 const next=(await api(f.owner,'POST',`${f.base}/invite-link`,{role:'EDITOR'})).inviteLink;
 await assert.rejects(api(f.guest,'GET',`/invite-links/${inviteLink.token}`),/NOT_FOUND/);
 assert.equal((await api(f.guest,'POST',`/invite-links/${next.token}/accept`)).role,'EDITOR');
 await api(f.owner,'DELETE',`${f.base}/invite-link`);
 assert.equal((await api(f.owner,'GET',`${f.base}/sharing`)).inviteLink,null);
 await assert.rejects(api(crypto.randomUUID(),'POST',`/invite-links/${next.token}/accept`),/NOT_FOUND/);
});

test('joining by link never lowers existing access and validates role',async()=>{
 const f=await setup();
 await assert.rejects(api(f.owner,'POST',`${f.base}/invite-link`,{role:'OWNER'}),/VALIDATION/);
 const {inviteLink}=await api(f.owner,'POST',`${f.base}/invite-link`,{role:'VIEWER'});
 assert.equal((await api(f.owner,'POST',`/invite-links/${inviteLink.token}/accept`)).role,'OWNER');
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
