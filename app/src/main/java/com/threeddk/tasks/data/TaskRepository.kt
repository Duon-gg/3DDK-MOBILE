package com.threeddk.tasks.data
import android.content.Context
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.threeddk.tasks.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TaskRepository(private val context:Context) {
 val db=TaskDatabase.get(context);val dao=db.dao();val gson=Gson()
 val configured=BuildConfig.SUPABASE_URL.startsWith("https://")&&FirebaseApp.getApps(context).isNotEmpty()
 val auth:FirebaseAuth?=if(configured)FirebaseAuth.getInstance() else null
 private val mutex=Mutex()
 private val settings=context.getSharedPreferences("device",Context.MODE_PRIVATE)
 private val secure by lazy {
  EncryptedSharedPreferences.create(context,"login",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 }
 val uid get()=auth?.currentUser?.uid
 private val api=ApiTransport(if(configured)BuildConfig.SUPABASE_URL.trimEnd('/')+"/functions/v1/api/v1/" else "https://unconfigured.invalid/"){force->
  auth?.currentUser?.getIdToken(force)?.await()?.token?:throw ApiException("UNAUTHORIZED")
 }
 val workspaceApi=ApiTransport(if(configured)BuildConfig.SUPABASE_URL.trimEnd('/')+"/functions/v1/api/v2/" else "https://unconfigured.invalid/"){force->
  auth?.currentUser?.getIdToken(force)?.await()?.token?:throw ApiException("UNAUTHORIZED")
 }
 val workspaces by lazy {WorkspaceRepository(this)}
 fun observe(owner:String)=dao.observe(owner)
 fun savedEmail()=runCatching {secure.getString("email","")?:""}.getOrDefault("")
 fun rememberEmail(email:String,remember:Boolean){if(remember)secure.edit().putString("email",email).apply() else secure.edit().remove("email").apply()}
 private fun owner()=uid?:throw ApiException("UNAUTHORIZED")
 private fun checkOwner(expected:String){if(uid!=expected)throw ApiException("UNAUTHORIZED")}
 fun body(vararg pairs:Pair<String,Any?>)=gson.toJsonTree(mapOf("requestId" to UUID.randomUUID().toString(),*pairs)).asJsonObject
 suspend fun cachedMe():Member?=uid?.let{dao.entry(it,"me",it)?.let{row->gson.fromJson(row.json,Member::class.java)}}
 suspend fun refresh()=mutex.withLock {
  val owner=owner()
  try {
   val me=workspaceApi.call("GET","me")
   checkOwner(owner)
   db.withTransaction {
    dao.put(listOf(CacheEntry(owner,"me",owner,me.toString())))
   }
  } catch(e:ApiException) {
   db.handleAccessFailure(e.code)
   throw e
  }
 }
 suspend fun detail(id:String)=mutex.withLock {
  val owner=owner();val task=api.call("GET","tasks/$id");val history=api.call("GET","tasks/$id/history")
  checkOwner(owner);dao.put(listOf(CacheEntry(owner,"tasks",id,task.toString()),CacheEntry(owner,"history",id,history.toString())))
 }
 suspend fun mutate(method:String,path:String,body:JsonObject)=mutex.withLock {
  val owner=owner();val result=(if(path=="me")workspaceApi else api).call(method,path,body);checkOwner(owner)
  if(result.isJsonObject&&result.asJsonObject.has("assigneeIds"))dao.put(listOf(CacheEntry(owner,"tasks",result.asJsonObject["id"].asString,result.toString())))
  result
 }
 suspend fun invites():List<Invite> = api.call("GET","invites").asJsonArray.map{gson.fromJson(it,Invite::class.java)}
 suspend fun saveDraft(task:Task,description:String,link:String)=mutex.withLock{
  val owner=owner();val old=dao.draft(owner,task.id)
  if(old?.description!=description||old.link!=link)dao.save(Draft(owner,task.id,description,link,expectedVersion=task.version))
 }
 suspend fun draft(id:String)=uid?.let{dao.draft(it,id)}
 suspend fun submit(task:Task)=mutex.withLock {
  val owner=owner();val draft=dao.draft(owner,task.id)?:throw ApiException("VALIDATION")
  submissionError(draft.description,draft.link)?.let{throw ApiException("VALIDATION")}
  val payload=body("requestId" to draft.requestId,"expectedVersion" to draft.expectedVersion,"description" to draft.description.trim(),"link" to draft.link.trim())
  try {
   val result=api.call("POST","tasks/${task.id}/submissions",payload)
   checkOwner(owner);db.withTransaction{dao.put(listOf(CacheEntry(owner,"tasks",task.id,result.toString())));dao.deleteDraft(owner,task.id)}
  } catch(e:ApiException){
   if(e.code=="CONFLICT"){
    val current=gson.fromJson(api.call("GET","tasks/${task.id}"),Task::class.java)
    checkOwner(owner);dao.put(listOf(CacheEntry(owner,"tasks",task.id,gson.toJson(current))))
    dao.save(draft.copy(requestId=UUID.randomUUID().toString(),expectedVersion=current.version))
   }
   throw e
  }
 }
 suspend fun registerDevice(){
  if(uid==null||!configured)return
  val installation=settings.getString("installation",null)?:UUID.randomUUID().toString().also{settings.edit().putString("installation",it).apply()}
  val token=FirebaseMessaging.getInstance().token.await()
  mutate("PUT","devices/$installation",body("token" to token))
 }
 suspend fun logout()=mutex.withLock {
  val installation=settings.getString("installation",null)
  // Rotate locally even offline; stale pushes contain no private task content.
  if(uid!=null&&installation!=null)runCatching{api.call("DELETE","devices/$installation",body())}
  runCatching{FirebaseMessaging.getInstance().deleteToken().await()}
  settings.edit().remove("installation").apply()
  auth?.signOut();db.withTransaction{dao.clearCache();dao.clearDrafts()}
 }
}
