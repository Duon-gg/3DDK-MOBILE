import { generateKeyPair, SignJWT } from 'jose';
import { verifyFirebase } from './auth.ts';
import { createApiHandler } from './api_handler.ts';
const pair=await generateKeyPair('RS256');
async function token(overrides: Record<string,unknown>={}, audience='test-project') {
 return new SignJWT({email:'member@example.com',email_verified:true,auth_time:Math.floor(Date.now()/1000)-10,...overrides}).setProtectedHeader({alg:'RS256',kid:'test'}).setSubject('member').setIssuer('https://securetoken.google.com/test-project').setAudience(audience).setIssuedAt().setExpirationTime('1h').sign(pair.privateKey);
}
Deno.test('verified Firebase identity is taken from the signed subject',async()=>{
 const actor=await verifyFirebase(await token(),'test-project',()=>Promise.resolve(pair.publicKey));
 if(actor.uid!=='member'||actor.email!=='member@example.com') throw new Error('wrong identity');
});
Deno.test('rejects wrong project, unverified email and future auth time',async()=>{
 for(const jwt of [await token({},'another-project'),await token({email_verified:false}),await token({auth_time:Math.floor(Date.now()/1000)+500})]) {
  let rejected=false; try { await verifyFirebase(jwt,'test-project',()=>Promise.resolve(pair.publicKey)); } catch { rejected=true; }
  if(!rejected) throw new Error('invalid identity accepted');
 }
});
Deno.test('API rejects signed wrong-project and expired tokens before RPC, and returns unverified as 403',async()=>{
 let calls=0;
 const handler=createApiHandler({verifyToken:jwt=>verifyFirebase(jwt,'test-project',()=>Promise.resolve(pair.publicKey)),rpc:async()=>{calls++;return {};}});
 const expired=await new SignJWT({email:'member@example.com',email_verified:true,auth_time:Math.floor(Date.now()/1000)-100}).setProtectedHeader({alg:'RS256',kid:'test'}).setSubject('member').setIssuer('https://securetoken.google.com/test-project').setAudience('test-project').setIssuedAt().setExpirationTime(Math.floor(Date.now()/1000)-1).sign(pair.privateKey);
 for(const [jwt,status] of [[await token({},'another-project'),401],[expired,401],[await token({email_verified:false}),403]] as const) {
  const response=await handler(new Request('https://example.com/api/v2/me',{headers:{Authorization:`Bearer ${jwt}`}}));
  if(response.status!==status) throw new Error(`expected ${status}, received ${response.status}`);
 }
 if(calls!==0) throw new Error('invalid token reached database');
});
