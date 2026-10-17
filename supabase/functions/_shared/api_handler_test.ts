import { createApiHandler } from './api_handler.ts';

const actor={uid:'verified-uid',email:'member@example.com',name:'Member'};
function assert(value:unknown,message='assertion failed') { if(!value) throw new Error(message); }
function fixture() {
 const calls:{name:string;body:Record<string,unknown>}[]=[];
 const dispatch:string[]=[];
 const handler=createApiHandler({
  verifyToken:async token=>{if(token!=='valid') throw new Error('UNAUTHORIZED');return actor;},
  rpc:async(name,body)=>{calls.push({name,body});return {ok:true};},
  dispatchLegacy:()=>{dispatch.push('dispatch');},
 });
 const request=(path='/api/v2/me',method='GET',body?:string,headers:Record<string,string>={})=>new Request(`https://example.com${path}`,{method,headers:{Authorization:'Bearer valid',...headers},body});
 return {handler,calls,dispatch,request};
}
Deno.test('handler authenticates before RPC and never trusts the client actor',async()=>{
 const {handler,calls,request}=fixture();
 for(const Authorization of ['', 'Bearer invalid']) {
  const response=await handler(request('/api/v2/me','GET',undefined,{Authorization}));
  assert(response.status===401);assert(calls.length===0);
  const detail=await response.json();assert(detail.code==='UNAUTHORIZED');
  assert(detail.requestId===response.headers.get('X-Request-Id'));
 }
 await handler(request('/api/v2/me','PATCH',JSON.stringify({uid:'forged',role:'OWNER'})));
 assert(calls[0].body.p_uid===actor.uid);
 assert(calls[0].body.p_email===actor.email);
});
Deno.test('read-only POST query reaches v2 unchanged, without requestId or legacy push',async()=>{
 const {handler,calls,dispatch,request}=fixture();
 const body={limit:50,filter:{op:'and',conditions:[]}};
 const response=await handler(request('/functions/v1/api/v2/tables/table-id/records/query','POST',JSON.stringify(body)));
 assert(response.status===200);assert(calls[0].name==='workspace_api');
 assert(calls[0].body.p_method==='POST');assert(calls[0].body.p_path==='/tables/table-id/records/query');
 assert(JSON.stringify(calls[0].body.p_body)===JSON.stringify(body));assert(dispatch.length===0);
 assert(response.headers.get('Cache-Control')==='no-store');assert(response.headers.has('X-Request-Id'));
});
Deno.test('v1 dispatch remains limited to successful v1 mutations',async()=>{
 const {handler,calls,dispatch,request}=fixture();
 await handler(request('/api/v1/me'));assert(dispatch.length===0);
 await handler(request('/api/v1/me','PATCH','{}'));
 assert(calls.every(call=>call.name==='api_dispatch'));assert(dispatch.length===1);
});
Deno.test('malformed, scalar, array, null and empty JSON are rejected before RPC',async()=>{
 const {handler,calls,request}=fixture();
 for(const body of ['{', 'null', '[]', '1', '"text"', '']) {
  const response=await handler(request('/api/v2/me','PATCH',body));
  assert(response.status===422);assert((await response.json()).code==='VALIDATION');
 }
 assert(calls.length===0);
});
Deno.test('request limit checks declared Content-Length and actual streamed UTF-8 bytes',async()=>{
 const {handler,calls,request}=fixture();
 const declared=await handler(request('/api/v2/me','PATCH','{}',{'Content-Length':'32769'}));
 assert(declared.status===413);
 const streamed=await handler(request('/api/v2/me','PATCH',JSON.stringify({text:'é'.repeat(17000)})));
 assert(streamed.status===413);assert(calls.length===0);
 const exact=JSON.stringify({text:'a'.repeat(32757)});
 assert(new TextEncoder().encode(exact).length===32768);
 assert((await handler(request('/api/v2/me','PATCH',exact))).status===200);
 const badLength=await handler(request('/api/v2/me','PATCH','{}',{'Content-Length':'-1'}));
 assert(badLength.status===422);
});
Deno.test('oversized multi-chunk streams are cancelled and invalid UTF-8 is rejected',async()=>{
 const {handler,calls}=fixture();let cancelled=false;
 const stream=new ReadableStream<Uint8Array>({pull(controller){controller.enqueue(new Uint8Array(20000));},cancel(){cancelled=true;throw new Error('stream closed');}});
 const response=await handler(new Request('https://example.com/api/v2/me',{method:'PATCH',headers:{Authorization:'Bearer valid'},body:stream}));
 assert(response.status===413);assert(cancelled);assert(calls.length===0);
 const invalid=await handler(new Request('https://example.com/api/v2/me',{method:'PATCH',headers:{Authorization:'Bearer valid'},body:new Uint8Array([123,34,120,34,58,34,255,34,125])}));
 assert(invalid.status===422);assert(calls.length===0);
});
Deno.test('unknown versions, unexpected prefixes and unsupported methods do not reach authentication or RPC',async()=>{
 const {handler,calls,request}=fixture();
 for(const [path,method] of [['/api/v3/me','GET'],['/api/v2','GET'],['/other/api/v2/me','GET'],['/api/v2/me','OPTIONS'],['/api/v2/me','HEAD']]) {
  assert((await handler(request(path,method))).status===404);
 }
 assert(calls.length===0);
});
Deno.test('RPC errors expose only stable code, request ID and retry advice',async()=>{
 const codes:Record<string,number>={SCHEMA_CHANGED:409,REQUEST_REUSED:409,CURSOR_STALE:409,CLIENT_UPGRADE_REQUIRED:409,INVALID_VIEW:422,UNSUPPORTED_FIELD:422,RATE_LIMIT:429};
 for(const [code,status] of Object.entries(codes)) {
  const handler=createApiHandler({verifyToken:async()=>actor,rpc:async()=>{throw new Error(code);}});
  const response=await handler(new Request('https://example.com/api/v2/me',{headers:{Authorization:'Bearer valid'}}));
  assert(response.status===status);const detail=await response.json();assert(detail.code===code);
  assert(detail.requestId===response.headers.get('X-Request-Id'));assert(response.headers.get('Cache-Control')==='no-store');
  if(code==='RATE_LIMIT') assert(response.headers.get('Retry-After')==='60');
 }
 for(const message of ['private SQL/internal secret','toString','constructor']) {
  const handler=createApiHandler({verifyToken:async()=>actor,rpc:async()=>{throw new Error(message);}});
  const response=await handler(new Request('https://example.com/api/v2/me',{headers:{Authorization:'Bearer valid'}}));
  assert(response.status===500);assert((await response.json()).code==='SERVER_ERROR');
 }
});
