begin;
do $$
declare u text:='qa-ws-'||gen_random_uuid(); g text:='qa-guest-'||gen_random_uuid(); w jsonb; b jsonb; t jsonb; payload jsonb; blocked boolean;
begin
 w:=workspace_api(u,u||'@example.invalid','QA','POST','/workspaces',jsonb_build_object('name','QA rollback only','requestId',gen_random_uuid()));
 perform workspace_api(g,g||'@example.invalid','QA guest','GET','/me','{}');
 insert into ws_members(workspace_id,uid,role) values((w->>'id')::uuid,g,'EDITOR');
 b:=workspace_api(u,u||'@example.invalid','QA','POST','/workspaces/'||(w->>'id')||'/bases',jsonb_build_object('name','QA base','requestId',gen_random_uuid()));
 t:=workspace_api(u,u||'@example.invalid','QA','POST','/bases/'||(b->>'id')||'/tables',jsonb_build_object('name','QA table','requestId',gen_random_uuid()));
 payload:=jsonb_build_object('expectedVersion',1,'requestId',gen_random_uuid());
 blocked:=false;
 begin perform workspace_api(g,g||'@example.invalid','QA','DELETE','/workspaces/'||(w->>'id'),payload);
 exception when others then if sqlerrm<>'FORBIDDEN' then raise;end if;blocked:=true;end;
 if not blocked then raise exception 'QA editor deletion accepted';end if;
 perform workspace_api(u,u||'@example.invalid','QA','DELETE','/workspaces/'||(w->>'id'),payload);
 perform workspace_api(u,u||'@example.invalid','QA','DELETE','/workspaces/'||(w->>'id'),payload);
 blocked:=false;
 begin perform workspace_api(g,g||'@example.invalid','QA','GET','/tables/'||(t->>'id')||'/metadata','{}');
 exception when others then if sqlerrm<>'NOT_FOUND' then raise;end if;blocked:=true;end;
 if not blocked then raise exception 'QA deleted workspace readable';end if;
 if jsonb_array_length(workspace_api(u,u||'@example.invalid','QA','GET','/workspace-trash','{}'))<>1 then raise exception 'QA trash missing';end if;
 perform workspace_api(u,u||'@example.invalid','QA','POST','/workspaces/'||(w->>'id')||'/restore',jsonb_build_object('expectedVersion',2,'requestId',gen_random_uuid()));
 perform workspace_api(g,g||'@example.invalid','QA','GET','/tables/'||(t->>'id')||'/metadata','{}');
end $$;
rollback;
select 'PASS: owner permission, replay, descendant blocking, trash, restore; test data rolled back' as result;
