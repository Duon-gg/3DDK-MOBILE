-- Run as administrator in SQL Editor. All test data is rolled back.
begin;
do $$
declare
 owner_id text:='qa-base-owner-'||gen_random_uuid();guest_id text:='qa-base-guest-'||gen_random_uuid();
 source_id uuid;destination_id uuid;base_id uuid;table_id uuid;copy_id uuid;invite_id uuid;
 result jsonb;payload jsonb;denied boolean;version_num integer;
begin
 perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/me');
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/workspaces',jsonb_build_object('requestId',gen_random_uuid(),'name','QA rollback source'));source_id:=(result->>'id')::uuid;
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/workspaces',jsonb_build_object('requestId',gen_random_uuid(),'name','QA rollback target'));destination_id:=(result->>'id')::uuid;
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/workspaces/'||source_id||'/bases',jsonb_build_object('requestId',gen_random_uuid(),'name','QA rollback base'));base_id:=(result->>'id')::uuid;
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/tables',jsonb_build_object('requestId',gen_random_uuid(),'name','QA table','template','TASKS'));table_id:=(result->>'id')::uuid;
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/invites',jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',1,'email',guest_id||'@example.invalid','role','VIEWER'));invite_id:=(result->>'id')::uuid;
 perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','POST','/base-invitations/'||invite_id||'/accept',jsonb_build_object('requestId',gen_random_uuid()));
 result:=workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/tables/'||table_id||'/metadata');
 if result->>'role'<>'VIEWER' then raise exception 'QA: wrong guest role';end if;
 denied:=false;
 begin perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','POST','/bases/'||base_id||'/tables',jsonb_build_object('requestId',gen_random_uuid(),'name','Denied'));exception when others then if sqlerrm<>'FORBIDDEN' then raise;end if;denied:=true;end;
 if not denied then raise exception 'QA: viewer can write';end if;
 denied:=false;
 begin perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/workspaces/'||source_id);exception when others then if sqlerrm<>'FORBIDDEN' then raise;end if;denied:=true;end;
 if not denied then raise exception 'QA: workspace leaked';end if;
 perform workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/move',jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',3,'workspaceId',destination_id));
 result:=workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/bases/'||base_id);
 if result->>'workspaceId'<>destination_id::text then raise exception 'QA: move failed';end if;
 payload:=jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',4,'name','QA copy');
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/duplicate',payload);copy_id:=(result->>'id')::uuid;
 result:=workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/duplicate',payload);
 if result->>'id'<>copy_id::text then raise exception 'QA: retry duplicated';end if;
 denied:=false;
 begin perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/bases/'||copy_id);exception when others then if sqlerrm<>'FORBIDDEN' then raise;end if;denied:=true;end;
 if not denied then raise exception 'QA: copied sharing';end if;
 payload:=jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',4);
 perform workspace_api(owner_id,owner_id||'@example.invalid','QA owner','DELETE','/bases/'||base_id,payload);
 perform workspace_api(owner_id,owner_id||'@example.invalid','QA owner','DELETE','/bases/'||base_id,payload);
 denied:=false;
 begin perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/tables/'||table_id||'/metadata');exception when others then if sqlerrm<>'NOT_FOUND' then raise;end if;denied:=true;end;
 if not denied then raise exception 'QA: deleted data readable';end if;
 perform workspace_api(owner_id,owner_id||'@example.invalid','QA owner','POST','/bases/'||base_id||'/restore',jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',5));
 perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','GET','/tables/'||table_id||'/metadata');
 perform workspace_api(owner_id,owner_id||'@example.invalid','QA owner','DELETE','/bases/'||base_id||'/members/'||guest_id,jsonb_build_object('requestId',gen_random_uuid(),'expectedVersion',6));
 denied:=false;
 begin perform workspace_api(guest_id,guest_id||'@example.invalid','QA guest','POST','/tables/'||table_id||'/records/query','{}');exception when others then if sqlerrm<>'FORBIDDEN' then raise;end if;denied:=true;end;
 if not denied then raise exception 'QA: revoked guest can query';end if;
end $$;
rollback;
select 'PASS: share, permissions, move, duplicate/retry, delete/retry, restore, revoke; test data rolled back' as qa_result,
 (select count(*) from ws_bases) as base_count,(select count(*) from ws_records) as record_count;
