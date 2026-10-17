import { createRemoteJWKSet } from 'jose';
import { verifyFirebase } from '../_shared/auth.ts';
import { env, rpc } from '../_shared/backend.ts';
import { createApiHandler } from '../_shared/api_handler.ts';

declare const EdgeRuntime: {waitUntil(promise:Promise<unknown>):void};
const keys=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
Deno.serve(createApiHandler({
 verifyToken:token=>verifyFirebase(token,env('FIREBASE_PROJECT_ID'),keys),
 rpc,
 dispatchLegacy:()=>{
  const secret=Deno.env.get('DISPATCH_SECRET');
  if(secret&&typeof EdgeRuntime!=='undefined') EdgeRuntime.waitUntil(fetch(env('SUPABASE_URL')+'/functions/v1/dispatch',{method:'POST',headers:{'x-dispatch-secret':secret},signal:AbortSignal.timeout(20000)}).catch(()=>undefined));
 },
}));
