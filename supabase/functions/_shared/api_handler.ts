import { failure } from './backend.ts';

type Actor={uid:string;email:string;name:string};
type Dependencies={
 verifyToken:(token:string)=>Promise<Actor>;
 rpc:(name:string,body:Record<string,unknown>)=>Promise<unknown>;
 dispatchLegacy?:()=>void;
 sendMail?:(mail:InviteMail)=>void;
};
export type InviteMail={to:string;role:string;base:string;inviter:string};
const maxBodyBytes=32768;

async function jsonBody(request:Request):Promise<Record<string,unknown>> {
 const declared=request.headers.get('Content-Length');
 if(declared!==null) {
  if(!/^\d+$/.test(declared)) throw new Error('VALIDATION');
  if(Number(declared)>maxBodyBytes) throw new Error('PAYLOAD_TOO_LARGE');
 }
 const reader=request.body?.getReader();let total=0;const chunks:Uint8Array[]=[];
 if(reader) {
  while(true) {
   const {value,done}=await reader.read();if(done) break;
   total+=value.byteLength;
   if(total>maxBodyBytes) {await reader.cancel().catch(()=>undefined);throw new Error('PAYLOAD_TOO_LARGE');}
   chunks.push(value);
  }
 }
 const buffer=new Uint8Array(total);let offset=0;
 for(const chunk of chunks) {buffer.set(chunk,offset);offset+=chunk.length;}
 let body;
 try {body=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(buffer));}
 catch {throw new Error('VALIDATION');}
 if(!body||Array.isArray(body)||typeof body!=='object') throw new Error('VALIDATION');
 return body;
}

export function createApiHandler(dependencies:Dependencies) {
 return async(request:Request):Promise<Response>=>{
  const requestId=crypto.randomUUID();
  try {
   const match=new URL(request.url).pathname.match(/^(?:\/functions\/v1)?\/api\/v([12])(\/.*)$/);
   if(!match||!['GET','POST','PATCH','PUT','DELETE'].includes(request.method)) throw new Error('NOT_FOUND');
   const bearer=request.headers.get('Authorization')?.match(/^Bearer (.+)$/)?.[1];
   if(!bearer) throw new Error('UNAUTHORIZED');
   let actor:Actor;
   try {actor=await dependencies.verifyToken(bearer);}
   catch(error) {if(error instanceof Error&&error.message==='UNVERIFIED') throw error;throw new Error('UNAUTHORIZED');}
   const body=request.method==='GET'?{}:await jsonBody(request);
   const result=await dependencies.rpc(match[1]==='2'?'workspace_api':'api_dispatch',{
    p_uid:actor.uid,p_email:actor.email,p_name:actor.name,p_method:request.method,p_path:match[2],p_body:body,
   });
   // v1 writes and v2 comment writes (which may queue @mention pushes) wake the dispatcher right away.
   if(request.method!=='GET'&&(match[1]==='1'||/^\/tables\/[^/]+\/records\/[^/]+\/comments(\/[^/]+)?$/.test(match[2]))) dependencies.dispatchLegacy?.();
   // A base invite carries the email to send; the client never sees it.
   if(match[1]==='2'&&request.method==='POST'&&/^\/bases\/[^/]+\/invites$/.test(match[2])&&result&&typeof result==='object'&&'mail' in result){
    const {mail,...rest}=result as {mail:InviteMail}&Record<string,unknown>;
    dependencies.sendMail?.(mail);
    return Response.json(rest,{headers:{'X-Request-Id':requestId,'Cache-Control':'no-store'}});
   }
   return Response.json(result,{headers:{'X-Request-Id':requestId,'Cache-Control':'no-store'}});
  } catch(error) {
   const response=failure(error,requestId);
   console.error(JSON.stringify({requestId,status:response.status}));
   return response;
  }
 };
}
