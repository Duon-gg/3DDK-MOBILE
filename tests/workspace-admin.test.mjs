import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@wsadmin.invalid`,uid,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

test('workspace admin has owner rights but cannot touch the owner',async()=>{
 const owner=crypto.randomUUID(),admin=crypto.randomUUID(),other=crypto.randomUUID();
 await api(admin,'GET','/me');await api(other,'GET','/me');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}));
 const w=`/workspaces/${ws.id}`;
 await db.query("insert into ws_members(workspace_id,uid,role) values($1,$2,'EDITOR'),($1,$3,'VIEWER')",[ws.id,admin,other]);
 const member=async uid=>(await api(owner,'GET',w)).members.find(m=>m.uid===uid);
 await assert.rejects(api(other,'PATCH',`${w}/members/${admin}`,request({role:'ADMIN',expectedVersion:(await member(admin)).version})),/FORBIDDEN/);
 await api(owner,'PATCH',`${w}/members/${admin}`,request({role:'ADMIN',expectedVersion:(await member(admin)).version}));
 assert.equal((await member(admin)).role,'ADMIN');
 assert.equal((await api(owner,'GET','/capabilities')).features.workspaceAdmin,true);
 // Owner rights: create a base, change someone's role.
 assert.ok((await api(admin,'POST',`${w}/bases`,request({name:'From admin'}))).id);
 await api(admin,'PATCH',`${w}/members/${other}`,request({role:'EDITOR',expectedVersion:(await member(other)).version}));
 assert.equal((await member(other)).role,'EDITOR');
 // But never the real owner.
 await assert.rejects(api(admin,'PATCH',`${w}/members/${owner}`,request({role:'VIEWER',expectedVersion:1})),/FORBIDDEN/);
 // Demote back to editor: owner rights are gone.
 await api(owner,'PATCH',`${w}/members/${admin}`,request({role:'EDITOR',expectedVersion:(await member(admin)).version}));
 assert.equal((await member(admin)).role,'EDITOR');
 await assert.rejects(api(admin,'PATCH',`${w}/members/${other}`,request({role:'VIEWER',expectedVersion:(await member(other)).version})),/FORBIDDEN/);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
