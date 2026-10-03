import { generateKeyPair, SignJWT } from 'jose';
import { verifyFirebase } from './auth.ts';
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
