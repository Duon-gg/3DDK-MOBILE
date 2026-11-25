// https invite links: https://<project>.supabase.co/functions/v1/join/<token>.
// Supabase serves HTML from functions as plain text, so this answers with a redirect that Android
// turns into "open in 3DDK Tasks" (intent URL with the app's package); the token is checked by the app.
Deno.serve(request=>{
 const token=new URL(request.url).pathname.match(/\/join\/([0-9a-f]{32})\/?$/)?.[1];
 if(!token) return new Response('Link mời không hợp lệ.',{status:404,headers:{'Content-Type':'text/plain; charset=utf-8'}});
 const intent=`intent://join/${token}#Intent;scheme=threeddk;package=com.threeddk.tasks;end`;
 return new Response(null,{status:302,headers:{Location:intent,'Cache-Control':'no-store'}});
});
