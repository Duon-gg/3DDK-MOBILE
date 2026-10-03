export function env(name:string):string { const value=Deno.env.get(name); if(!value) throw new Error(`Missing configuration: ${name}`); return value; }
export async function rpc(name:string, body:Record<string,unknown>={}) {
 const key=env('SUPABASE_SERVICE_ROLE_KEY');
 const response=await fetch(`${env('SUPABASE_URL')}/rest/v1/rpc/${name}`,{method:'POST',headers:{apikey:key,Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
 const data=await response.json();
 if(!response.ok) throw Object.assign(new Error(data.message??'SERVER_ERROR'),{sqlCode:data.code});
 return data;
}
export function failure(error:unknown):Response {
 const message=error instanceof Error?error.message:'SERVER_ERROR';
 const statuses:Record<string,number>={UNAUTHORIZED:401,UNVERIFIED:403,NOT_INVITED:403,FORBIDDEN:403,NOT_FOUND:404,CONFLICT:409,INVALID_STATE:409,ASSIGNED_WORK:409,VALIDATION:422,RATE_LIMIT:429,PAYLOAD_TOO_LARGE:413};
 const sqlCode=(error as {sqlCode?:string})?.sqlCode;
 const code=message in statuses?message:sqlCode?.startsWith('22')||sqlCode?.startsWith('23')?'VALIDATION':'SERVER_ERROR';
 return Response.json({code,message:code},{status:statuses[code]??500});
}
export async function secretMatches(given:string,expected:string) {
 const digest=async(s:string)=>new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)));
 const [a,b]=await Promise.all([digest(given),digest(expected)]); let difference=0;
 for(let i=0;i<a.length;i++) difference|=a[i]^b[i];
 return difference===0;
}
