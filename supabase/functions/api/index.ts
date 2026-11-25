import { createRemoteJWKSet } from 'jose';
import { verifyFirebase } from '../_shared/auth.ts';
import { env, rpc } from '../_shared/backend.ts';
import { createApiHandler } from '../_shared/api_handler.ts';
import { inviteMail } from '../_shared/mail.ts';

declare const EdgeRuntime: {waitUntil(promise:Promise<unknown>):void};
const keys=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
Deno.serve(createApiHandler({
 verifyToken:token=>verifyFirebase(token,env('FIREBASE_PROJECT_ID'),keys),
 rpc,
 sendMail:mail=>{
  const key=Deno.env.get('RESEND_API_KEY');
  // Mail is optional: without a key (or a verified sender) invites still work in the app.
  if(key&&typeof EdgeRuntime!=='undefined') EdgeRuntime.waitUntil(fetch('https://api.resend.com/emails',{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:JSON.stringify({from:Deno.env.get('MAIL_FROM')??'3DDK Tasks <onboarding@resend.dev>',...inviteMail(mail)}),signal:AbortSignal.timeout(10000)}).then(r=>{if(!r.ok)console.error('invite_mail_failed',r.status);}).catch(()=>console.error('invite_mail_failed')));
 },
 dispatchLegacy:()=>{
  const secret=Deno.env.get('DISPATCH_SECRET');
  if(secret&&typeof EdgeRuntime!=='undefined') EdgeRuntime.waitUntil(fetch(env('SUPABASE_URL')+'/functions/v1/dispatch',{method:'POST',headers:{'x-dispatch-secret':secret},signal:AbortSignal.timeout(20000)}).catch(()=>undefined));
 },
}));
