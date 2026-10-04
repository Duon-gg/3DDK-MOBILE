package com.threeddk.tasks.data

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** V2 snapshots stay in Room, partitioned by account and resource path. */
class WorkspaceRepository(private val session:TaskRepository) {
 private val lock=Mutex()
 val uid get()=session.uid ?: throw ApiException("UNAUTHORIZED")
 fun observe(owner:String,path:String)=session.dao.observe(owner).map { rows->
  rows.firstOrNull{it.kind=="v2"&&it.id==path}?.let{session.gson.fromJson(it.json,JsonObject::class.java)}
 }
 suspend fun refresh(path:String)=lock.withLock {
  val owner=uid
  try {
   val response=session.workspaceApi.call("GET",path)
   val snapshot=if(response.isJsonArray) JsonObject().apply{add("items",response)} else response.asJsonObject
   if(session.uid!=owner)throw ApiException("UNAUTHORIZED")
   session.dao.put(listOf(CacheEntry(owner,"v2",path,snapshot.toString())))
  } catch(e:ApiException){
   if(e.code in listOf("FORBIDDEN","UNAUTHORIZED","UNVERIFIED"))session.dao.clearKind(owner,"v2")
   throw e
  }
 }
 suspend fun mutate(method:String,path:String,body:JsonObject):JsonObject=lock.withLock {
  val owner=uid;val response=session.workspaceApi.call(method,path,body).asJsonObject
  if(session.uid!=owner)throw ApiException("UNAUTHORIZED")
  response
 }
 suspend fun draft(key:String)=session.dao.draft(uid,key)
 suspend fun save(draft:Draft)=lock.withLock {if(draft.owner!=uid)throw ApiException("UNAUTHORIZED");session.dao.save(draft)}
 suspend fun removeDraft(key:String)=session.dao.deleteDraft(uid,key)
}

fun JsonObject.text(key:String)=get(key)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
fun JsonObject.objects(key:String)=getAsJsonArray(key)?.map{it.asJsonObject}.orEmpty()
