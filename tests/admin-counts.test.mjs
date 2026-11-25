import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

let db;
const request=data=>({requestId:crypto.randomUUID(),...data});
const api=async(uid,method,path,body={},name=uid)=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[uid,`${uid}@admin.invalid`,name,method,path,JSON.stringify(body)])).rows[0].result;
before(async()=>{
 db=new PGlite();await db.exec('create role anon;create role authenticated;create role service_role;');
 const names=(await readdir(new URL('../supabase/migrations/',import.meta.url))).filter(n=>n>='202610040001'&&n.endsWith('.sql')).sort();
 for(const name of names)await db.exec(await readFile(new URL(`../supabase/migrations/${name}`,import.meta.url),'utf8'));
});
after(async()=>{await db?.close();});

async function setup(){
 const owner=crypto.randomUUID(),admin=crypto.randomUUID(),viewer=crypto.randomUUID();
 await api(admin,'GET','/me',{},'Khang');await api(viewer,'GET','/me',{},'Trâm');
 const ws=await api(owner,'POST','/workspaces',request({name:'W'}),'Dương');
 const base=await api(owner,'POST',`/workspaces/${ws.id}/bases`,request({name:'Base A'}));
 const table=await api(owner,'POST',`/bases/${base.id}/tables`,request({name:'T'}));
 return {owner,admin,viewer,ws,bid:base.id,base:`/bases/${base.id}`,path:`/tables/${table.id}`};
}
const sharing=(f,uid)=>api(uid,'GET',`${f.base}/sharing`);

test('admin is invited by email, then acts like the owner on the base',async()=>{
 const f=await setup();
 assert.equal((await api(f.owner,'GET','/capabilities')).features.adminRole,true);
 let s=await sharing(f,f.owner);
 const inv=await api(f.owner,'POST',`${f.base}/invites`,request({email:`${f.admin}@admin.invalid`,role:'ADMIN',expectedVersion:s.version}));
 assert.deepEqual(inv.mail,{to:`${f.admin}@admin.invalid`,role:'ADMIN',base:'Base A',inviter:'Dương'});
 const pending=(await api(f.admin,'GET','/base-invitations')).items??(await api(f.admin,'GET','/base-invitations'));
 await api(f.admin,'POST',`/base-invitations/${inv.id}/accept`,request({}));
 s=await sharing(f,f.owner);
 assert.equal(s.people.find(p=>p.uid===f.admin).role,'ADMIN');
 // Owner-only actions now work for the admin: manage sharing, invite link, fields.
 s=await sharing(f,f.admin);
 await api(f.admin,'POST',`${f.base}/invites`,request({email:`${f.viewer}@admin.invalid`,role:'VIEWER',expectedVersion:s.version}));
 assert.ok((await api(f.admin,'POST',`${f.base}/invite-link`,{role:'EDITOR'})).inviteLink.token);
 const m=await api(f.admin,'GET',`${f.path}/metadata`);
 await api(f.admin,'POST',`${f.path}/fields`,request({name:'Mới',type:'TEXT',options:[],expectedSchemaVersion:m.schemaVersion}));
 assert.equal(m.role,'OWNER');
 void pending;
});

test('owner promotes and demotes an admin; editors cannot',async()=>{
 const f=await setup();
 let s=await sharing(f,f.owner);
 const {inviteLink}=await api(f.owner,'POST',`${f.base}/invite-link`,{role:'EDITOR'});
 await api(f.admin,'POST',`/invite-links/${inviteLink.token}/accept`);
 await api(f.viewer,'POST',`/invite-links/${inviteLink.token}/accept`);
 s=await sharing(f,f.owner);
 await assert.rejects(api(f.viewer,'PATCH',`${f.base}/members/${f.admin}`,request({role:'ADMIN',expectedVersion:s.version})),/FORBIDDEN/);
 await api(f.owner,'PATCH',`${f.base}/members/${f.admin}`,request({role:'ADMIN',expectedVersion:s.version}));
 s=await sharing(f,f.owner);
 assert.equal(s.people.find(p=>p.uid===f.admin).role,'ADMIN');
 assert.equal(s.members.find(p=>p.uid===f.admin).role,'ADMIN');
 // The admin can now change other people's access, like the owner.
 await api(f.admin,'PATCH',`${f.base}/members/${f.viewer}`,request({role:'VIEWER',expectedVersion:s.version}));
 s=await sharing(f,f.owner);
 await api(f.owner,'PATCH',`${f.base}/members/${f.admin}`,request({role:'EDITOR',expectedVersion:s.version}));
 assert.equal((await sharing(f,f.owner)).people.find(p=>p.uid===f.admin).role,'EDITOR');
 await assert.rejects(api(f.admin,'POST',`${f.base}/invite-link`,{role:'EDITOR'}),/FORBIDDEN/);
});

test('group counts cover the whole table and respect search',async()=>{
 const f=await setup();
 let m=await api(f.owner,'GET',`${f.path}/metadata`);
 const st=await api(f.owner,'POST',`${f.path}/fields`,request({name:'Status',type:'SELECT',options:['A','B'],expectedSchemaVersion:m.schemaVersion}));
 m=await api(f.owner,'GET',`${f.path}/metadata`);const name=m.fields.find(x=>x.type==='TEXT').id;
 await api(f.owner,'POST',`${f.path}/records/batch`,request({expectedSchemaVersion:m.schemaVersion,records:Array.from({length:130},(_,i)=>({cells:i%3===0?{[name]:`web ${i}`}:{[name]:`x${i}`,[st.id]:i%2?'A':'B'}}))}));
 const c=await api(f.owner,'POST',`${f.path}/records/counts`,{fieldId:st.id});
 const by=Object.fromEntries(c.counts.map(x=>[JSON.stringify(x.value),x.count]));
 assert.equal(by['"A"']+by['"B"']+by['null'],130);assert.equal(by['null'],44);
 const w=await api(f.owner,'POST',`${f.path}/records/counts`,{fieldId:st.id,search:'web'});
 assert.deepEqual(w.counts.map(x=>[x.value,x.count]),[[null,44]]);
 await assert.rejects(api(f.owner,'POST',`${f.path}/records/counts`,{fieldId:crypto.randomUUID()}),/VALIDATION/);
 await assert.rejects(api(crypto.randomUUID(),'POST',`${f.path}/records/counts`,{fieldId:st.id}),/FORBIDDEN/);
 assert.equal((await db.query("select has_function_privilege('service_role','public.workspace_api(text,text,text,text,text,jsonb)','execute') ok")).rows[0].ok,true);
});
