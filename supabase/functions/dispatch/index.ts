import { SignJWT, importPKCS8 } from 'jose';
import { env, rpc, secretMatches } from '../_shared/backend.ts';
type Claimed={notification_id:string;task_id:string;uid:string;kind:string;claim_token:string;tokens:string[]};
Deno.serve(async request=>{
 if(request.method!=='POST'||!await secretMatches(request.headers.get('x-dispatch-secret')??'',env('DISPATCH_SECRET'))) return new Response('Unauthorized',{status:401});
 try {
  await rpc('enqueue_reminders');
  const account=JSON.parse(env('FIREBASE_SERVICE_ACCOUNT'));
  const key=await importPKCS8(account.private_key,'RS256');
  const assertion=await new SignJWT({scope:'https://www.googleapis.com/auth/firebase.messaging'}).setProtectedHeader({alg:'RS256'}).setIssuer(account.client_email).setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
  const auth=await fetch('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),signal:AbortSignal.timeout(10000)});
  if(!auth.ok) throw new Error('FCM_AUTH_FAILED');
  const {access_token}=await auth.json();
  const deadline=Date.now()+45000;let processed=0;
  // ponytail: one just-in-time lease per group; shard workers if group volume grows.
  while(Date.now()<deadline&&processed<20) {
   const jobs:Claimed[]=await rpc('claim_notifications');
   if(!jobs.length)break;
   const job=jobs[0];
   let success=true;const invalid:string[]=[];
   await Promise.all(job.tokens.map(async token=>{
    try {
     const response=await fetch(`https://fcm.googleapis.com/v1/projects/${env('FIREBASE_PROJECT_ID')}/messages:send`,{method:'POST',headers:{Authorization:`Bearer ${access_token}`,'Content-Type':'application/json'},body:JSON.stringify({message:{token,notification:{title:'3DDK Tasks',body:job.kind.startsWith('REMINDER')?'Bạn có công việc sắp đến hạn.':'Nhóm có cập nhật công việc.'},data:{taskId:job.task_id,notificationId:job.notification_id,recipientUid:job.uid},android:{priority:'high',ttl:'3600s',notification:{channel_id:'tasks',tag:job.notification_id}}}}),signal:AbortSignal.timeout(10000)});
     if(!response.ok){const detail=await response.json();if(detail.error?.details?.some((x:{errorCode?:string})=>x.errorCode==='UNREGISTERED')) invalid.push(token);else success=false;}
    } catch { success=false; }
   }));
   await rpc('finish_notification',{p_id:job.notification_id,p_claim:job.claim_token,p_success:success,p_invalid_tokens:invalid});
   processed++;
  }
  return Response.json({processed});
 } catch { console.error('dispatch_failed');return Response.json({code:'DISPATCH_FAILED'},{status:500}); }
});
