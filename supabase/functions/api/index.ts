import { createRemoteJWKSet } from 'jose';
import { verifyFirebase } from '../_shared/auth.ts';
import { env, failure, rpc } from '../_shared/backend.ts';
declare const EdgeRuntime: {waitUntil(promise:Promise<unknown>):void};
const keys=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
Deno.serve(async request=>{
 const requestId=crypto.randomUUID();
 try {
  const match=new URL(request.url).pathname.match(/\/api\/v1(\/.*)$/);
  if(!match||!['GET','POST','PATCH','PUT','DELETE'].includes(request.method)) throw new Error('NOT_FOUND');
  const bearer=request.headers.get('Authorization')?.match(/^Bearer (.+)$/)?.[1];
  if(!bearer) throw new Error('UNAUTHORIZED');
  let actor;
  try { actor=await verifyFirebase(bearer,env('FIREBASE_PROJECT_ID'),keys); }
  catch(error) { if(error instanceof Error&&error.message==='UNVERIFIED') throw error; throw new Error('UNAUTHORIZED'); }
  let body:Record<string,unknown>={};
  if(request.method!=='GET') {
   const reader=request.body?.getReader(); let total=0; const chunks:Uint8Array[]=[];
   if(reader) { while(true) {const {value,done}=await reader.read();if(done) break;total+=value.byteLength;if(total>32768){await reader.cancel();throw new Error('PAYLOAD_TOO_LARGE');}chunks.push(value);} }
   const buffer=new Uint8Array(total);let offset=0;for(const chunk of chunks){buffer.set(chunk,offset);offset+=chunk.length;}
   try { body=JSON.parse(new TextDecoder().decode(buffer)); } catch { throw new Error('VALIDATION'); }
   if(!body||Array.isArray(body)||typeof body!=='object') throw new Error('VALIDATION');
  }
  const result=await rpc('api_dispatch',{p_uid:actor.uid,p_email:actor.email,p_name:actor.name,p_method:request.method,p_path:match[1],p_body:body});
  if(request.method!=='GET'&&typeof EdgeRuntime!=='undefined') EdgeRuntime.waitUntil(fetch(`${env('SUPABASE_URL')}/functions/v1/dispatch`,{method:'POST',headers:{'x-dispatch-secret':env('DISPATCH_SECRET')},signal:AbortSignal.timeout(20000)}).catch(()=>undefined));
  return Response.json(result,{headers:{'X-Request-Id':requestId,'Cache-Control':'no-store'}});
 } catch(error) {
  const response=failure(error); console.error(JSON.stringify({requestId,status:response.status})); response.headers.set('X-Request-Id',requestId);return response;
 }
});
