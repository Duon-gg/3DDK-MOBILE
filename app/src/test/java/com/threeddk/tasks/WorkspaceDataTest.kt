package com.threeddk.tasks

import com.google.gson.JsonParser
import com.threeddk.tasks.data.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceDataTest {
 private fun json(value:String)=JsonParser.parseString(value).asJsonObject
 @Test fun editingOneCellDoesNotSendUnchangedCellsOrEraseRemoteEdits(){
  val base=json("""{"title":"Before","status":"Todo","count":1}""")
  val mine=json("""{"title":"Mine","status":"Todo","count":1}""")
  val remote=json("""{"title":"Before","status":"Done","count":2}""")
  assertEquals(json("""{"title":"Mine"}"""),cellChanges(base,mine))
  assertEquals(json("""{"title":"Mine","status":"Done","count":2}"""),mergeCells(base,mine,remote,emptyMap()))
  assertTrue(cellConflicts(emptyList(),base,mine,remote).isEmpty())
 }
 @Test fun sameCellConflictsNeedExplicitChoiceAndClearIsAPatch(){
  val base=json("""{"f":"Before","optional":"old"}""")
  val mine=json("""{"f":"Mine","optional":null}""")
  val remote=json("""{"f":"Theirs","optional":"old"}""")
  val conflicts=cellConflicts(listOf(json("""{"id":"f","name":"Title"}""")),base,mine,remote)
  assertEquals(1,conflicts.size);assertEquals("Title",conflicts.single().name)
  assertEquals("Theirs",mergeCells(base,mine,remote,emptyMap()).text("f"))
  assertEquals("Mine",mergeCells(base,mine,remote,mapOf("f" to true)).text("f"))
  assertTrue(cellChanges(base,mine)["optional"].isJsonNull)
 }
 @Test fun userFieldsUseMemberIdsAndDoNotConvertDisplayNames(){
  val fields=listOf(json("""{"id":"u","name":"Owner","type":"USER"}"""))
  val members=listOf(json("""{"uid":"uid-a","displayName":"Alice"}"""))
  assertEquals("uid-a",typedCells(fields,json("""{"u":"uid-a"}"""),members).text("u"))
  for(value in listOf("Alice","uid-outside")){
   try{typedCells(fields,json("""{"u":"$value"}"""),members);fail("Expected membership validation")}catch(_:IllegalArgumentException){}
  }
  val unchanged=json("""{"u":"former-member"}""")
  assertEquals(unchanged,typedCells(fields,unchanged,members,unchanged))
 }
 @Test fun cacheScopeIncludesViewSchemaSearchProjectionAndFilter(){
  val a=json("""{"limit":50,"expectedSchemaVersion":2,"viewId":"v1","viewVersion":3,"filter":{"op":"and","conditions":[]}}""")
  val reordered=json("""{"filter":{"conditions":[],"op":"and"},"viewVersion":3,"viewId":"v1","expectedSchemaVersion":2,"limit":50,"cursor":"opaque"}""")
  assertEquals(queryCacheKey("tables/t",a),queryCacheKey("tables/t",reordered))
  for(key in listOf("expectedSchemaVersion","viewId","viewVersion","search","fieldIds")){
   val other=a.deepCopy().apply{addProperty(key,"different")}
   assertNotEquals(queryCacheKey("tables/t",a),queryCacheKey("tables/t",other))
  }
 }
 @Test fun pageMergeDeduplicatesIdsAndRejectsChangedSchemaOrView(){
  val first=json("""{"items":[{"id":"a","version":1}],"schemaVersion":2,"viewVersion":4,"hasMore":true}""")
  val next=json("""{"items":[{"id":"a","version":2},{"id":"b","version":1}],"schemaVersion":2,"viewVersion":4,"hasMore":false}""")
  val merged=mergeRecordPage(first,next)
  assertEquals(2,merged.objects("items").size);assertEquals(2,merged.objects("items").first()["version"].asInt)
  for(key in listOf("schemaVersion","viewVersion")){
   try{mergeRecordPage(first,next.deepCopy().apply{addProperty(key,99)});fail("Expected stale cursor")}
   catch(_:IllegalArgumentException){}
  }
 }
 @Test fun revokingOneWorkspaceRetainsTheOtherWorkspaceCache(){
  val rows=listOf(
   CacheEntry("alice","v2","workspaces","""{"items":[{"id":"w1"},{"id":"w2"}]}"""),
   CacheEntry("alice","v2","workspaces/w1","""{"bases":[{"id":"b1"}]}"""),
   CacheEntry("alice","v2","bases/b1","""{"workspaceId":"w1","tables":[{"id":"t1"}]}"""),
   CacheEntry("alice","v2-meta","tables/t1","""{"workspaceId":"w1"}"""),
   CacheEntry("alice","v2-query","tables/t1|scope","{}"),
   CacheEntry("alice","v2-record","tables/t1/r1","{}"),
   CacheEntry("alice","v2-meta","tables/t2","""{"workspaceId":"w2"}"""),
   CacheEntry("alice","v2-query","tables/t2|scope","{}"))
  val kept=cacheAfterWorkspaceRevocation(rows,"tables/t1/records/query")
  assertEquals(setOf("workspaces","tables/t2","tables/t2|scope"),kept.map{it.id}.toSet())
  assertEquals("w2",json(kept.first{it.id=="workspaces"}.json).objects("items").single().text("id"))
 }
 @Test fun pendingCreateReplaysItsOriginalIdentityAndPayloadAfterRestart(){
  val before=Draft("alice","v2/tables/t/new/local",requestId="request-X",expectedSchemaVersion=2)
  val original=createRecordPayload(before,json("""{"title":"First attempt"}"""))
  val persisted=before.copy(pendingCreateJson=original.toString())
  val restored=persisted.copy(requestId="request-Y",expectedSchemaVersion=99)
  val retry=createRecordPayload(restored,json("""{"title":"Changed after timeout"}"""))
  assertEquals(original,retry)
  assertEquals("request-X",retry.text("requestId"));assertEquals(2,retry["expectedSchemaVersion"].asInt)
  assertEquals("First attempt",retry.getAsJsonObject("cells").text("title"))
 }
 @Test fun schemaRejectionUnlocksUncommittedCreateAfterTimeoutButForbiddenDoesNot(){
  assertTrue(canUnlockPendingCreation("SCHEMA_CHANGED",retrying=true,networkAttempted=true))
  assertFalse(canUnlockPendingCreation("FORBIDDEN",retrying=true,networkAttempted=true))
  assertFalse(canUnlockPendingCreation(null,retrying=true,networkAttempted=true))
  assertTrue(canUnlockPendingCreation("VALIDATION",retrying=false,networkAttempted=true))
  val pending=Draft("alice","v2/tables/t/new/local",requestId="request-X",pendingCreateJson="""{"requestId":"request-X","expectedSchemaVersion":1,"cells":{"title":"Mine"}}""")
  val reconciled=pending.copy(pendingCreateJson="",requestId="request-Y",expectedSchemaVersion=2)
  val payload=createRecordPayload(reconciled,json("""{"title":"Mine"}"""))
  assertEquals("request-Y",payload.text("requestId"));assertEquals(2,payload["expectedSchemaVersion"].asInt)
 }
 @Test fun taskTemplateRequiresItsTitleButLegacyTextFieldsRemainOptional(){
  val title=json("""{"id":"title","name":"Tên công việc","type":"TEXT","systemKey":"title"}""")
  try{typedCells(listOf(title),json("""{"title":"   "}"""));fail("Expected required title")}
  catch(expected:IllegalArgumentException){assertTrue(expected.message!!.contains("Tên công việc"))}
  assertTrue(typedCells(listOf(json("""{"id":"legacy","name":"Tên","type":"TEXT"}""")),json("{}"))["legacy"].isJsonNull)
 }
 @Test fun newerSharedViewReplacesOldOverridesButSameVersionKeepsLocalEdits(){
  val query=json("""{"viewId":"v","viewVersion":1,"search":"urgent","filter":{"op":"and","conditions":[{"fieldId":"status","operator":"eq","value":"Old"}]},"sort":[],"fieldIds":["old"]}""")
  val meta=json("""{"schemaVersion":3,"views":[{"id":"v","version":2,"filter":{"op":"and","conditions":[]},"sort":[{"fieldId":"due","direction":"asc"}],"fieldIds":["title","due"],"fieldOrder":["due","title"]}]}""")
  val refreshed=refreshedViewQuery(query,meta,"v")
  assertEquals(meta.objects("views").single()["filter"],refreshed["filter"])
  assertEquals(meta.objects("views").single()["fieldIds"],refreshed["fieldIds"])
  assertEquals("urgent",refreshed.text("search"));assertEquals(2,refreshed["viewVersion"].asInt)
  val local=refreshed.deepCopy().apply{add("sort",JsonParser.parseString("[]"))}
  assertEquals(local,refreshedViewQuery(local,meta,"v"))
 }
 @Test fun oldQueryPagesNeverRenderAgainstNewSchemaOrViewMetadata(){
  val page=json("""{"schemaVersion":1,"viewVersion":1}""")
  val query=json("""{"viewId":"v","viewVersion":1,"expectedSchemaVersion":1}""")
  val meta=json("""{"schemaVersion":1,"views":[{"id":"v","version":1}]}""")
  assertTrue(cachedPageMatchesMetadata(page,meta,query))
  assertFalse(cachedPageMatchesMetadata(page,meta.deepCopy().apply{addProperty("schemaVersion",2)},query))
  assertFalse(cachedPageMatchesMetadata(page,json("""{"schemaVersion":1,"views":[{"id":"v","version":2}]}"""),query))
  assertFalse(cachedPageMatchesMetadata(page,json("""{"schemaVersion":1,"views":[]}"""),query))
 }
}
