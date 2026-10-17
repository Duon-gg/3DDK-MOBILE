package com.threeddk.tasks.data

import androidx.room.withTransaction
import com.google.gson.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account and complete query scope are part of every cache key. */
class WorkspaceRepository(private val session:TaskRepository) {
 private val lock=Mutex()
 val uid get()=session.uid ?: throw ApiException("UNAUTHORIZED")
 private fun checkOwner(owner:String){if(session.uid!=owner)throw ApiException("UNAUTHORIZED")}
 fun observe(owner:String,path:String)=session.dao.observe(owner).map { rows->
  rows.firstOrNull{it.kind=="v2"&&it.id==path}?.let{JsonParser.parseString(it.json).asJsonObject}
 }
 fun observeTable(owner:String,path:String,query:JsonObject)=session.dao.observe(owner).map { rows->
  val meta=rows.firstOrNull{it.kind=="v2-meta"&&it.id==path}?:rows.firstOrNull{it.kind=="v2"&&it.id==path}?:return@map null
  JsonParser.parseString(meta.json).asJsonObject.apply{
   val currentSchema=get("schemaVersion")?.asInt?:1
   val scopedQuery=query.deepCopy().apply{if(!has("expectedSchemaVersion"))addProperty("expectedSchemaVersion",currentSchema)}
   val page=rows.firstOrNull{it.kind=="v2-query"&&it.id==queryCacheKey(path,scopedQuery)}?.let{JsonParser.parseString(it.json).asJsonObject}?.takeIf{cachedPageMatchesMetadata(it,this,query)}
   val legacy=if(meta.kind=="v2"&&!query.has("filter")&&!query.has("search")&&!query.has("viewId"))get("records") else null
   add("records",page?.get("items")?:legacy?:JsonArray())
   addProperty("hasMore",page?.get("hasMore")?.asBoolean?:false)
   addProperty("queryCached",page!=null||legacy!=null)
   add("nextCursor",page?.get("nextCursor")?:JsonNull.INSTANCE)
  }
 }
 suspend fun cached(path:String,kind:String="v2"):JsonObject? {
  val owner=uid;val entry=session.dao.entry(owner,kind,path);checkOwner(owner)
  return entry?.let{JsonParser.parseString(it.json).asJsonObject}
 }
 private suspend fun fetch(owner:String,method:String,path:String,body:JsonObject=JsonObject()):JsonElement {
  try{return session.workspaceApi.call(method,path,body).also{checkOwner(owner)}}
  catch(e:ApiException){
   if(e.code=="FORBIDDEN")session.db.withTransaction{
    val before=session.dao.entries(owner).filter{it.kind.startsWith("v2")}
    val after=cacheAfterWorkspaceRevocation(before,path)
    session.dao.delete(before.filter{it !in after});session.dao.put(after.filter{it !in before})
   }else if(e.code in listOf("UNAUTHORIZED","UNVERIFIED"))session.db.withTransaction{
    listOf("v2","v2-meta","v2-query","v2-record").forEach{session.dao.clearKind(owner,it)}
   }
   throw e
  }
 }
 suspend fun refresh(path:String)=lock.withLock {
  val owner=uid;val response=fetch(owner,"GET",path)
  val snapshot=if(response.isJsonArray)JsonObject().apply{add("items",response)} else response.asJsonObject
  checkOwner(owner);session.dao.put(listOf(CacheEntry(owner,"v2",path,snapshot.toString())))
 }
 suspend fun metadata(path:String)=lock.withLock {
  val owner=uid;val response=fetch(owner,"GET","$path/metadata").asJsonObject
  session.dao.put(listOf(CacheEntry(owner,"v2-meta",path,response.toString())));response
 }
 suspend fun record(path:String,id:String)=lock.withLock {
  val owner=uid;val response=fetch(owner,"GET","$path/records/$id").asJsonObject
  session.dao.put(listOf(CacheEntry(owner,"v2-record","$path/$id",response.toString())));response
 }
 suspend fun query(path:String,query:JsonObject,append:Boolean=false)=lock.withLock {
  val owner=uid;val key=queryCacheKey(path,query)
  val previous=session.dao.entry(owner,"v2-query",key)?.let{JsonParser.parseString(it.json).asJsonObject}
  val request=query.deepCopy().apply{if(append){
   val cursor=previous?.text("nextCursor").orEmpty();if(cursor.isBlank())return@withLock previous?:JsonObject()
   addProperty("cursor",cursor)
  }}
  val response=fetch(owner,"POST","$path/records/query",request).asJsonObject
  val result=mergeRecordPage(if(append)previous else null,response)
  session.dao.put(listOf(CacheEntry(owner,"v2-query",key,result.toString())));result
 }
 suspend fun mutate(method:String,path:String,body:JsonObject):JsonObject=lock.withLock {
  val owner=uid;fetch(owner,method,path,body).asJsonObject
 }
 suspend fun draft(key:String):Draft? {val owner=uid;val draft=session.dao.draft(owner,key);checkOwner(owner);return draft}
 suspend fun save(draft:Draft)=lock.withLock {if(draft.owner!=uid)throw ApiException("UNAUTHORIZED");session.dao.save(draft)}
 suspend fun acknowledgeCreation(draft:Draft,oldKey:String)=lock.withLock {
  checkOwner(draft.owner)
  session.db.withTransaction{session.dao.save(draft);session.dao.deleteDraft(draft.owner,oldKey)}
 }
 suspend fun removeDraft(key:String,owner:String=uid)=lock.withLock {checkOwner(owner);session.dao.deleteDraft(owner,key)}
 suspend fun migrateLegacyDraft(legacyKey:String,newKey:String)=lock.withLock {
  val owner=uid;val legacy=session.dao.draft(owner,legacyKey)?:return@withLock null
  val draft=legacy.copy(taskId=newKey)
  session.db.withTransaction{checkOwner(owner);session.dao.save(draft);session.dao.deleteDraft(owner,legacyKey)};draft
 }
 suspend fun clearSession(owner:String)=lock.withLock {
  if(session.uid!=owner)return@withLock
  session.auth?.signOut()
  session.db.withTransaction{session.dao.clearOwnerCache(owner);session.dao.clearOwnerDrafts(owner)}
 }
}

fun JsonObject.text(key:String)=get(key)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
fun JsonObject.objects(key:String)=getAsJsonArray(key)?.map{it.asJsonObject}.orEmpty()

private fun canonicalJson(value:JsonElement):JsonElement=when {
 value.isJsonObject->JsonObject().apply{value.asJsonObject.keySet().sorted().forEach{add(it,canonicalJson(value.asJsonObject[it]))}}
 value.isJsonArray->JsonArray().apply{value.asJsonArray.forEach{add(canonicalJson(it))}}
 else->value
}
fun queryCacheKey(path:String,query:JsonObject):String=path+"|"+canonicalJson(query.deepCopy().apply{remove("cursor")})
fun cachedPageMatchesMetadata(page:JsonObject,metadata:JsonObject,query:JsonObject):Boolean {
 if(page.get("schemaVersion")!=metadata.get("schemaVersion"))return false
 val selectedId=query.text("viewId")
 if(selectedId.isBlank())return page.get("viewVersion")?.takeUnless{it.isJsonNull}?.asInt==0
 val selected=metadata.objects("views").firstOrNull{it.text("id")==selectedId}?:return false
 return page.get("viewVersion")==selected.get("version")
}
fun mergeRecordPage(previous:JsonObject?,page:JsonObject):JsonObject=page.deepCopy().apply{
 if(previous!=null){
  require(previous.get("schemaVersion")==page.get("schemaVersion")&&previous.get("viewVersion")==page.get("viewVersion")){"CURSOR_STALE"}
  val rows=linkedMapOf<String,JsonObject>()
  (previous.objects("items")+page.objects("items")).forEach{rows[it.text("id")]=it}
  add("items",JsonArray().apply{rows.values.forEach{add(it)}})
 }
}

/** A workspace denial cannot erase offline work from another workspace. */
fun cacheAfterWorkspaceRevocation(rows:List<CacheEntry>,path:String):List<CacheEntry>{
 val target=path.trim('/').split('/').take(2).joinToString("/")
 val snapshots=rows.associateWith{runCatching{JsonParser.parseString(it.json).asJsonObject}.getOrDefault(JsonObject())}
 val workspaceId=if(target.startsWith("workspaces/"))target.substringAfter('/') else snapshots.entries.firstOrNull{it.key.id==target&&it.value.text("workspaceId").isNotBlank()}?.value?.text("workspaceId")
 val resources=mutableSetOf(target)
 if(workspaceId!=null){
  resources.add("workspaces/$workspaceId")
  snapshots.forEach{(row,json)->if(json.text("workspaceId")==workspaceId||row.id=="workspaces/$workspaceId"){
   resources.add(row.id.substringBefore('|'))
   json.objects("bases").forEach{resources.add("bases/${it.text("id")}")}
   json.objects("tables").forEach{resources.add("tables/${it.text("id")}")}
  }}
  snapshots.forEach{(row,json)->if(row.id in resources)json.objects("tables").forEach{resources.add("tables/${it.text("id")}")}}
 }
 return rows.mapNotNull{row->
  if(resources.any{row.id==it||row.id.startsWith("$it/")||row.id.startsWith("$it|")})null
  else if(row.kind=="v2"&&row.id=="workspaces"&&workspaceId!=null){
   val json=snapshots.getValue(row).deepCopy().apply{add("items",JsonArray().apply{snapshots.getValue(row).objects("items").filter{it.text("id")!=workspaceId}.forEach{add(it)}})}
   row.copy(json=json.toString())
  }else row
 }
}
