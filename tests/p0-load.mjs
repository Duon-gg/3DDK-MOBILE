import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {performance} from 'node:perf_hooks';
import {PGlite} from '@electric-sql/pglite';

// shortcut: PGlite queues one connection, use staging PostgreSQL/Edge to measure real concurrent load.
const users=Array.from({length:100},()=>crypto.randomUUID());
const viewers=new Set(users.slice(1,21));
const operationCount=200;
const migrations=['202610040001_workspaces.sql','202610170001_p0_workspaces.sql'];
const mutation=data=>({requestId:crypto.randomUUID(),...data});
function summary(samples) {
 const values=samples.map(sample=>sample.ms).sort((a,b)=>a-b);
 const percentile=fraction=>Number(values[Math.ceil(values.length*fraction)-1].toFixed(2));
 return {count:values.length,p50Ms:percentile(.5),p95Ms:percentile(.95),p99Ms:percentile(.99),maxMs:values.at(-1),maxResponseBytes:Math.max(...samples.map(sample=>sample.bytes))};
}
async function scenario(rowCount,concurrency) {
 const db=new PGlite();
 try {
  await db.exec('create role anon;create role authenticated;create role service_role;');
  for(const file of migrations) await db.exec(await readFile(new URL(`../supabase/migrations/${file}`,import.meta.url),'utf8'));
  const api=async(user,method,path,body={})=>(await db.query('select workspace_api($1,$2,$3,$4,$5,$6::jsonb) result',[user,`${user}@load.invalid`,'Synthetic member',method,path,JSON.stringify(body)])).rows[0].result;
  const owner=users[0];
  const workspace=await api(owner,'POST','/workspaces',mutation({name:'LOCAL synthetic load'}));
  const base=await api(owner,'POST',`/workspaces/${workspace.id}/bases`,mutation({name:'Synthetic fixtures'}));
  const table=await api(owner,'POST',`/bases/${base.id}/tables`,mutation({name:'Synthetic tasks',template:'TASKS'}));
  await db.query("insert into ws_profiles(uid,email,display_name) select uid,uid||'@load.invalid','Synthetic member' from unnest($1::text[]) uid on conflict do nothing",[users]);
  await db.query("insert into ws_members(workspace_id,uid,role) select $1,uid,'EDITOR' from unnest($2::text[]) uid where uid<>$3",[workspace.id,users,owner]);
  await db.query("update ws_members set role='VIEWER' where workspace_id=$1 and uid=any($2::text[])",[workspace.id,[...viewers]]);
  const metadata=await api(owner,'GET',`/tables/${table.id}/metadata`);
  const title=metadata.fields.find(field=>field.type==='TEXT');assert.ok(title);
  // Fixture INSERT deliberately bypasses the API cap only inside this private in-memory database.
  const records=(await db.query("insert into ws_records(table_id,cells) select $1,jsonb_build_object($2::text,'Task '||lpad(i::text,5,'0')) from generate_series(1,$3::integer) i returning id",[table.id,title.id,rowCount])).rows;
  await assert.rejects(api(owner,'POST',`/tables/${table.id}/records`,mutation({expectedSchemaVersion:metadata.schemaVersion,cells:{[title.id]:'Over API cap'}})),/VALIDATION/);
  assert.equal(Number((await db.query('select count(*) n from ws_records where table_id=$1',[table.id])).rows[0].n),rowCount);
  const readSamples=[],writeSamples=[];let nextOperation=0,expectedDenied=0,unexpectedErrors=0;
  const unexpectedCodes={};
  const started=performance.now();
  await Promise.all(Array.from({length:concurrency},async()=>{
   while(nextOperation<operationCount) {
    const operation=nextOperation++;const user=users[operation%users.length];const writing=operation%5===0;
    const before=performance.now();
    let bytes=0;
    try {
     const result=writing?
      await api(user,'PATCH',`/tables/${table.id}/records/${records[operation].id}/cells`,mutation({expectedVersion:1,expectedSchemaVersion:metadata.schemaVersion,changes:{[title.id]:`Updated ${operation}`}})):
      await api(user,'POST',`/tables/${table.id}/records/query`,{limit:50,search:'Task',sort:[{fieldId:title.id,direction:'asc'}]});
     bytes=Buffer.byteLength(JSON.stringify(result));
     if(writing) {assert.equal(viewers.has(user),false);assert.equal(result.version,2);}
     else assert.ok(result.items.length<=50);
    } catch(error) {
     if(writing&&viewers.has(user)&&error.message==='FORBIDDEN')expectedDenied++;
     else {unexpectedErrors++;const code=/^[A-Z_]+$/.test(error.message)?error.message:'UNEXPECTED';unexpectedCodes[code]=(unexpectedCodes[code]??0)+1;}
    }
    const sample={ms:Number((performance.now()-before).toFixed(2)),bytes};
    (writing?writeSamples:readSamples).push(sample);
   }
  }));
  const elapsedMs=Number((performance.now()-started).toFixed(2));
  assert.equal(expectedDenied,8);assert.equal(readSamples.length,160);assert.equal(writeSamples.length,40);
  const conflictRow=records[300].id;
  const attempts=await Promise.allSettled([users[21],users[22]].map(user=>api(user,'PATCH',`/tables/${table.id}/records/${conflictRow}/cells`,mutation({expectedVersion:1,expectedSchemaVersion:metadata.schemaVersion,changes:{[title.id]:'Conflict check'}}))));
  assert.equal(attempts.filter(result=>result.status==='fulfilled').length,1);
  assert.equal(attempts.filter(result=>result.status==='rejected'&&result.reason.message==='CONFLICT').length,1);
  assert.equal((await api(owner,'GET',`/tables/${table.id}/records/${conflictRow}`)).version,2);
  for(const user of users.slice(90,95)) {
   const revoked=await api(owner,'POST',`/workspaces/${workspace.id}/invites`,mutation({email:`${user}@load.invalid`,role:'EDITOR'}));
   await api(owner,'DELETE',`/workspaces/${workspace.id}/invites/${revoked.id}`,mutation());
   await assert.rejects(api(user,'POST',`/invitations/${revoked.id}/accept`,mutation()),/INVALID_STATE/);
   await api(owner,'DELETE',`/workspaces/${workspace.id}/members/${user}`,mutation({expectedVersion:1}));
   await assert.rejects(api(user,'POST',`/tables/${table.id}/records/query`,{}),/FORBIDDEN/);
   const fresh=await api(owner,'POST',`/workspaces/${workspace.id}/invites`,mutation({email:`${user}@load.invalid`,role:'EDITOR'}));
   await api(user,'POST',`/invitations/${fresh.id}/accept`,mutation());
   assert.ok((await api(user,'POST',`/tables/${table.id}/records/query`,{limit:1})).items.length===1);
  }
  return {rows:rowCount,syntheticUsers:users.length,roles:{owner:1,editor:79,viewer:20},queuedWorkers:concurrency,operations:operationCount,read:summary(readSamples),write:{...summary(writeSamples),expectedDenied},elapsedMs,unexpectedErrors,errorRate:unexpectedErrors/operationCount,expectedPermissionRejectionRate:expectedDenied/operationCount,unexpectedCodes,sameRowConflict:{committed:1,rejected:1},inviteRevokeChurn:{cycles:5,revokedInviteRejected:5,removedMemberDenied:5,reinvitedMemberReadRestored:5},apiRecordCapStillEnforced:true};
 } finally {await db.close();}
}
console.log('LOCAL PGlite experiment only: one queued connection; no HTTP, Firebase, production data or proof of 100-user capacity.');
try {
 for(const rows of [500,5000])for(const concurrency of [1,5,20]) {
  const result=await scenario(rows,concurrency);console.log(JSON.stringify(result));if(result.unexpectedErrors>0)process.exitCode=1;
 }
} catch(error) {
 console.error(`Local load experiment failed: ${error.message}`);process.exitCode=1;
}
