package com.threeddk.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import com.threeddk.tasks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun refreshedViewQuery(current:JsonObject,metadata:JsonObject,viewId:String?):JsonObject=current.deepCopy().apply{
 addProperty("expectedSchemaVersion",metadata["schemaVersion"].asInt)
 if(viewId!=null){
  val view=metadata.objects("views").firstOrNull{it.text("id")==viewId}
  if(view==null){listOf("viewId","viewVersion","filter","sort","fieldIds","fieldOrder").forEach{remove(it)}}
  else {
   if(get("viewVersion")!=view["version"]){
    listOf("filter","sort","fieldIds","fieldOrder").forEach{key->remove(key);view[key]?.let{add(key,it.deepCopy())}}
   }
   addProperty("viewId",viewId);add("viewVersion",view["version"])
  }
 }
}

class WorkspaceViewModel(app:Application):AndroidViewModel(app) {
 val session=(app as TasksApp).repository
 val repo=session.workspaces
 val data=MutableStateFlow<JsonObject?>(null)
 val capabilities=MutableStateFlow<JsonObject?>(null)
 val busy=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 val accessDenied=MutableStateFlow(false)
 val note=MutableStateFlow("Đang xem dữ liệu đã lưu")
 val selectedViewId=MutableStateFlow<String?>(null)
 val hasMore=MutableStateFlow(false)
 val drafts=MutableStateFlow<List<Draft>>(emptyList())
 var queryConfig=JsonObject();private set
 private var observer:Job?=null
 private var draftObserver:Job?=null
 private var searchJob:Job?=null
 private var requestJob:Job?=null
 private var requestSerial=0
 private var path=""
 private var tableMode=false
 private var viewResetNotice=false
 private var pendingPair:Pair<String,JsonObject>?=null
 fun supports(feature:String)=capabilities.value?.getAsJsonObject("features")?.get(feature)?.asBoolean==true
 fun open(resource:String){
  if(path!=resource||observer==null){path=resource;tableMode=false;observe()}
  refresh()
 }
 fun openTable(resource:String){
  if(path!=resource||!tableMode||observer==null){
   path=resource;tableMode=true;queryConfig=JsonObject().apply{addProperty("limit",50)};observe()
   draftObserver?.cancel();val owner=repo.uid
   draftObserver=viewModelScope.launch{session.dao.observeDrafts(owner).collect{all->drafts.value=all.filter{it.taskId.startsWith("v2/$resource/new/")||it.taskId=="v2/$resource/new"}}}
  }
  refresh()
 }
 private fun observe(){
  observer?.cancel();data.value=null
  val owner=repo.uid;val scope=queryConfig.deepCopy()
  observer=viewModelScope.launch{
   (if(tableMode)repo.observeTable(owner,path,scope) else repo.observe(owner,path)).collect{
    data.value=it;hasMore.value=it?.get("hasMore")?.asBoolean==true
   }
  }
 }
 private suspend fun loadCapabilities(){
  capabilities.value=repo.cached("capabilities")
  try{repo.refresh("capabilities");capabilities.value=repo.cached("capabilities")}
  catch(e:CancellationException){throw e}catch(e:Exception){if(tableMode&&capabilities.value==null)throw e}
 }
 private suspend fun reload(){
  viewResetNotice=false
  loadCapabilities()
  if(!tableMode){repo.refresh(path);return}
  if(!supports("pagedRecords"))throw ApiException("CLIENT_UPGRADE_REQUIRED")
  val meta=repo.metadata(path)
  val next=refreshedViewQuery(queryConfig,meta,selectedViewId.value)
  viewResetNotice=selectedViewId.value!=null&&queryConfig.get("viewVersion")!=next.get("viewVersion")
  if(selectedViewId.value!=null&&!next.has("viewId"))selectedViewId.value=null
  if(viewResetNotice)note.value="Chế độ xem đã đổi • Đã nhận bộ lọc, sắp xếp và cột mới; các điều chỉnh chưa lưu đã được bỏ"
  queryConfig=next;observe();repo.query(path,queryConfig)
 }
 private suspend fun queryCurrent(append:Boolean=false){
  if(!supports("pagedRecords")){loadCapabilities();if(!supports("pagedRecords"))throw ApiException("CLIENT_UPGRADE_REQUIRED")}
  if(!queryConfig.has("expectedSchemaVersion")){
   val meta=repo.cached(path,"v2-meta")?:repo.metadata(path)
   queryConfig=queryConfig.deepCopy().apply{addProperty("expectedSchemaVersion",meta["schemaVersion"].asInt)}
   observe()
  }
  repo.query(path,queryConfig,append)
 }
 fun refresh(){if(busy.value)return;run{reload();if(!viewResetNotice)note.value=if(tableMode)"Đã cập nhật • Chỉ các trang đã tải có sẵn khi mất mạng" else "Đã cập nhật • Có thể xem khi mất mạng"}}
 private fun run(block:suspend()->Unit){val serial=++requestSerial;requestJob=viewModelScope.launch{
  busy.value=true;error.value=null
  try{block();accessDenied.value=false}catch(e:CancellationException){throw e}catch(e:Exception){
   if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","UNVERIFIED","NOT_FOUND"))accessDenied.value=true
   error.value=friendlyError(e);note.value="Chưa cập nhật được • Giữ dữ liệu đã tải"
  }finally{if(serial==requestSerial)busy.value=false}
 }}
 fun queryTable(filter:JsonObject?=null,sort:JsonArray?=null,search:String?=null,fieldIds:JsonArray?=null){
  if(!tableMode)return
  requestJob?.cancel()
  queryConfig=queryConfig.deepCopy().apply{
   filter?.let{add("filter",it)};sort?.let{add("sort",it)};search?.let{addProperty("search",it.trim())};fieldIds?.let{add("fieldIds",it)}
  }
  observe();run{queryCurrent();note.value="Đã tải kết quả • Có thể tải thêm trang nếu còn dữ liệu"}
 }
 fun search(query:String){searchJob?.cancel();searchJob=viewModelScope.launch{delay(300);queryTable(search=query)}}
 fun loadMore(){if(!tableMode||busy.value||!hasMore.value)return;run{
  try{queryCurrent(true)}catch(e:ApiException){if(e.code=="CURSOR_STALE"){queryCurrent();note.value="Dữ liệu đã đổi • Đã tải lại trang đầu"}else throw e}
 }}
 fun discardDraft(taskId:String){
  if(busy.value||!tableMode||!(taskId.startsWith("v2/$path/new/")||taskId=="v2/$path/new"))return
  run{repo.removeDraft(taskId)}
 }
 fun selectView(id:String?){
  selectedViewId.value=id
  val view=data.value?.objects("views")?.firstOrNull{it.text("id")==id}
  queryConfig=JsonObject().apply{
   addProperty("limit",50)
   data.value?.get("schemaVersion")?.let{add("expectedSchemaVersion",it)}
   if(view!=null){addProperty("viewId",id);add("viewVersion",view["version"]);listOf("filter","sort","fieldIds","fieldOrder").forEach{key->view[key]?.let{add(key,it.deepCopy())}}}
  }
  observe();run{queryCurrent()}
 }
 fun saveView(name:String,visibility:String,done:()->Unit={}){
  val body=JsonObject().apply{
   addProperty("name",name);addProperty("visibility",visibility);addProperty("type","GRID")
   add("filter",queryConfig["filter"]?:JsonObject().apply{addProperty("op","and");add("conditions",JsonArray())})
   listOf("sort","fieldIds","fieldOrder").forEach{add(it,queryConfig[it]?:JsonArray())}
  }
  mutate("POST","$path/views",body){result->selectView(result.text("id"));done()}
 }
 fun mutate(method:String,target:String,values:JsonObject,done:(JsonObject)->Unit={}){
  if(busy.value)return
  val signature=method+target+values.toString()
  val payload=if(pendingPair?.first==signature)pendingPair!!.second else values.deepCopy().apply{addProperty("requestId",java.util.UUID.randomUUID().toString())}.also{pendingPair=signature to it}
  run{
   val result=repo.mutate(method,target,payload);pendingPair=null
   try{reload()}catch(e:Exception){if(e is CancellationException)throw e;note.value="Đã lưu • Chưa tải lại được"}
   done(result)
  }
 }
 fun createTaskTable(name:String,done:()->Unit){
  if(!supports("atomicTemplates")){error.value="Máy chủ chưa hỗ trợ tạo bảng công việc an toàn. Hãy cập nhật backend trước.";return}
  mutate("POST","$path/tables",JsonObject().apply{addProperty("name",name);addProperty("template","TASKS")}){note.value="Đã tạo đầy đủ bảng công việc";done()}
 }
}
