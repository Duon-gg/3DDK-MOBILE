package com.threeddk.tasks.viewmodel
import com.threeddk.tasks.R
import com.threeddk.tasks.TasksApp
import com.threeddk.tasks.model.friendlyError
import com.threeddk.tasks.model.objects
import com.threeddk.tasks.model.text
import com.threeddk.tasks.repository.WorkspaceRepository

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import com.threeddk.tasks.data.ApiException
import com.threeddk.tasks.data.Draft
import com.threeddk.tasks.data.isTestWorkspace
import com.threeddk.tasks.data.recentBases
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
 private val session=(app as TasksApp).repository
 private val repo=session.workspaces
 val home=MutableStateFlow<List<WorkspaceRepository.HomeTable>>(emptyList())
 val recentBases=MutableStateFlow<List<WorkspaceRepository.RecentBase>>(emptyList())
 val workspaceStars=MutableStateFlow<Set<String>>(emptySet())
 private var homeObserver:Job?=null
 fun observeHome(){
  if(homeObserver!=null)return
  val owner=repo.uid
  homeObserver=viewModelScope.launch{
   launch{repo.observeHome(owner).collect{home.value=it}}
   launch{repo.observeRecentBases(owner).collect{recentBases.value=it}}
   launch{repo.observeWorkspaceStars(owner).collect{workspaceStars.value=it}}
  }
 }
 fun rememberTable(resource:String,basePath:String,toggleStar:Boolean=false){
  viewModelScope.launch{
   try{repo.updateHome(resource,basePath,toggleStar)}
   catch(e:CancellationException){throw e}
   catch(e:Exception){error.value=friendlyError(e)}
  }
 }
 fun toggleBaseStar(basePath:String){
  viewModelScope.launch{
   try{if(recentBases.value.none{it.path==basePath}){repo.refresh(basePath);repo.rememberBase(basePath)};repo.toggleBaseStar(basePath)}
   catch(e:CancellationException){throw e}
   catch(e:Exception){error.value=friendlyError(e)}
  }
 }
 fun toggleWorkspaceStar(resource:String){run{repo.toggleWorkspaceStar(resource)}}
 /** A tap made while a load runs waits for it to finish instead of being dropped without a word. */
 private fun whenIdle(action:()->Unit){if(!busy.value)action() else viewModelScope.launch{busy.first{!it};action()}}
 fun tablesInBase(basePath:String,done:(List<JsonObject>)->Unit){
  if(busy.value){whenIdle{tablesInBase(basePath,done)};return}
  run{
   val data=try{repo.refresh(basePath)}catch(e:java.io.IOException){repo.cached(basePath)?:throw e}
   done(data.objects("tables"))
  }
 }
 fun resolveBase(resource:String,done:(JsonObject,String?)->Unit){
  run{
   val base=try{repo.refresh(resource)}catch(e:java.io.IOException){repo.cached(resource)?:throw e}
   val table=repo.recentTable(resource,base.objects("tables"))
   repo.rememberBase(resource)
   busy.value=false;done(base,table)
  }
 }
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
 fun readResource(resource:String,done:(JsonObject)->Unit){
  if(busy.value){whenIdle{readResource(resource,done)};return}
  run{val result=repo.refresh(resource);busy.value=false;done(result)}
 }
 fun open(resource:String){
  if(path!=resource||observer==null){path=resource;tableMode=false;observe()}
  refresh()
 }
 fun openTable(resource:String,initialViewId:String?=null){
  if(path!=resource||!tableMode||observer==null){
   selectedViewId.value=initialViewId
   path=resource;tableMode=true;queryConfig=JsonObject().apply{addProperty("limit",50)};observe()
   draftObserver?.cancel();val owner=repo.uid
   draftObserver=viewModelScope.launch{session.dao.observeDrafts(owner).collect{all->drafts.value=all.filter{it.taskId.startsWith("v2/$resource/new/")||it.taskId=="v2/$resource/new"}}}
  }
  refresh()
 }
 private var pickerJob:Job?=null
 fun cancelPickerLoading(){pickerJob?.cancel();pickerJob=null}
 fun pickerMetadata(resource:String,done:(JsonObject?,String?)->Unit){
  pickerJob?.cancel()
  pickerJob=viewModelScope.launch{
   try{
    val meta=try{repo.metadata(resource)}catch(e:java.io.IOException){repo.cached(resource,"v2-meta")?:throw e}
    done(meta,null)
   }catch(e:CancellationException){throw e}catch(e:Exception){done(null,friendlyError(e))}
  }
 }
 private var rememberedBase=""
 private fun observe(){
  observer?.cancel();data.value=null
  val owner=repo.uid;val scope=queryConfig.deepCopy()
  observer=viewModelScope.launch{
   (if(tableMode)repo.observeTable(owner,path,scope) else repo.observe(owner,path)).collect{
    if(it!=null&&!tableMode&&path.startsWith("bases/")&&rememberedBase!=path){
     repo.rememberBase(path)
     rememberedBase=path
    }
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
  if(queryConfig.get("viewVersion")!=next.get("viewVersion"))viewLayout=meta.objects("views").firstOrNull{it.text("id")==selectedViewId.value}?.get("layout")?.takeIf{it.isJsonObject}?.asJsonObject
  viewResetNotice=selectedViewId.value!=null&&queryConfig.get("viewVersion")!=next.get("viewVersion")
  if(selectedViewId.value!=null&&!next.has("viewId"))selectedViewId.value=null
  if(viewResetNotice){note.value="Chế độ xem đã đổi • Đã nhận bộ lọc, sắp xếp và cột mới; các điều chỉnh chưa lưu đã được bỏ";notice.value=note.value}
  queryConfig=next;observe();repo.query(path,queryConfig)
 }
 private suspend fun queryCurrent(append:Boolean=false){
  if(!supports("pagedRecords")){loadCapabilities();if(!supports("pagedRecords"))throw ApiException("CLIENT_UPGRADE_REQUIRED")}
  if(!queryConfig.has("expectedSchemaVersion")){
   val meta=repo.cached(path,"v2-meta")?:repo.metadata(path)
   queryConfig=queryConfig.deepCopy().apply{addProperty("expectedSchemaVersion",meta["schemaVersion"].asInt)}
   observe()
  }
  // Schema changed elsewhere (another device or member): fetch fresh metadata once instead of staying stuck.
  try{repo.query(path,queryConfig,append)}catch(e:ApiException){
   // SCHEMA_CHANGED: a field changed; CONFLICT: the open view was saved elsewhere. Both recover by reloading.
   if(e.code !in listOf("SCHEMA_CHANGED","CONFLICT")||append)throw e;reload()
  }
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
  try{queryCurrent(true)}catch(e:ApiException){if(e.code=="CURSOR_STALE"){queryCurrent();note.value="Dữ liệu đã đổi • Đã tải lại trang đầu";notice.value=note.value}else throw e}
 }}
 fun discardDraft(taskId:String){
  if(busy.value||!tableMode||!(taskId.startsWith("v2/$path/new/")||taskId=="v2/$path/new"))return
  run{repo.removeDraft(taskId)}
 }
 fun selectView(id:String?){
  val view=data.value?.objects("views")?.firstOrNull{it.text("id")==id}
  viewLayout=view?.get("layout")?.takeIf{it.isJsonObject}?.asJsonObject
  selectedViewId.value=id
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
   if(supports("viewLayout"))savableLayout()?.let{add("layout",it)}
  }
  mutate("POST","$path/views",body){result->selectView(result.text("id"));done()}
 }
 fun mutate(method:String,target:String,values:JsonObject,done:(JsonObject)->Unit={}){
  if(busy.value){whenIdle{mutate(method,target,values,done)};return}
  val signature=method+target+values.toString()
  val payload=if(pendingPair?.first==signature)pendingPair!!.second else values.deepCopy().apply{addProperty("requestId",java.util.UUID.randomUUID().toString())}.also{pendingPair=signature to it}
  run{
   val result=repo.mutate(method,target,payload);pendingPair=null
   try{reload()}catch(e:Exception){if(e is CancellationException)throw e;note.value="Đã lưu • Chưa tải lại được";notice.value=note.value}
   done(result)
  }
 }
 fun createTaskTable(name:String,done:(JsonObject)->Unit){
  if(!supports("atomicTemplates")){error.value="Máy chủ chưa hỗ trợ tạo bảng công việc an toàn. Hãy cập nhật backend trước.";return}
  mutate("POST","$path/tables",JsonObject().apply{addProperty("name",name);addProperty("template","TASKS")}){result->note.value="Đã tạo đầy đủ bảng công việc";done(result)}
 }

 fun baseTitle(resource:String,fallback:String,done:(String)->Unit){
  viewModelScope.launch{done(fallback.ifBlank{repo.cached(resource)?.text("name").orEmpty()})}
 }


 fun testSpaces(query:String)=data.value?.objects("items").orEmpty().filter{isTestWorkspace(it.text("name"))&&it.text("name").contains(query,true)}
 fun filteredBases(query:String,starred:Boolean=false)=recentBases.value.filter{(!starred||it.starred)&&it.name.contains(query,true)}
 fun starredSpaces(query:String)=data.value?.objects("items").orEmpty().filter{"workspaces/"+it.text("id") in workspaceStars.value&&it.text("name").contains(query,true)}
 fun matchingItems(key:String,query:String)=data.value?.objects(key).orEmpty().filter{it.text("name").contains(query,true)}
 fun sharedSpaces()=data.value?.objects("items").orEmpty().filter{it.text("role") in listOf("EDITOR","VIEWER")}
 fun moveTargets(workspaces:JsonObject,base:JsonObject)=workspaces.objects("items").filter{it.text("role")=="OWNER"&&it.text("id")!=base.text("workspaceId")}
 fun baseForActions(id:String,workspace:String)=recentBases.value.firstOrNull{it.path==id}?:data.value?.objects("bases")?.firstOrNull{"bases/"+it.text("id")==id}?.let{WorkspaceRepository.RecentBase(id,it.text("name"),0,workspaceId=workspace.substringAfter('/'))}
 /** Base invite link: role creates a new link (replacing the old one), null turns it off. */
 fun setInviteLink(base:String,role:String?,done:(JsonObject)->Unit){
  mutate(if(role==null)"DELETE" else "POST","$base/invite-link",JsonObject().apply{role?.let{addProperty("role",it)}},done)
 }
 fun previewInvite(token:String,done:(JsonObject)->Unit)=readNow("invite-links/$token",done)
 /** Read without the busy gate, so a sheet can refresh right after its own mutation; errors go to [error]. */
 fun readNow(resource:String,done:(JsonObject)->Unit){viewModelScope.launch{try{done(repo.refresh(resource))}catch(e:CancellationException){throw e}catch(e:Exception){error.value=friendlyError(e)}}}
 fun acceptInvite(token:String,done:(JsonObject)->Unit)=mutate("POST","invite-links/$token/accept",JsonObject(),done)
 fun versionedMutation(method:String,target:String,base:JsonObject,extra:JsonObject=JsonObject(),done:(JsonObject)->Unit={}){
  mutate(method,target,extra.deepCopy().apply{add("expectedVersion",base["version"])},done)
 }
 fun renameWorkspace(target:String,name:String,workspace:JsonObject,done:()->Unit){
  if(name.trim().length !in 1..100){error.value="Nhập tên từ 1 đến 100 ký tự.";return}
  versionedMutation("PATCH",target,workspace,JsonObject().apply{addProperty("name",name.trim())}){done()}
 }
 fun manageMember(target:String,member:JsonObject,action:Int){
  val transfer=action==3
  val versionMember=if(transfer)data.value?.objects("members")?.firstOrNull{it.text("role")=="OWNER"} else member
  val payload=JsonObject().apply{addProperty("expectedVersion",versionMember?.get("version")?.asInt?:1);if(transfer)addProperty("targetUid",member.text("uid")) else if(action<2)addProperty("role",if(action==0)"EDITOR" else "VIEWER")}
  mutate(if(action==2)"DELETE" else if(transfer)"POST" else "PATCH",if(transfer)"$target/transfer-ownership" else "$target/members/${member.text("uid")}",payload)
 }
 fun createResource(target:String,name:String,done:(JsonObject)->Unit){
  if(name.trim().length !in 1..100){error.value="Nhập tên từ 1 đến 100 ký tự.";return}
  val endpoint=if(target=="workspaces")target else if(target.startsWith("workspaces/"))"$target/bases" else "$target/tables"
  mutate("POST",endpoint,JsonObject().apply{addProperty("name",name.trim())},done)
 }


 fun editableView()=data.value?.objects("views")?.firstOrNull{it.text("id")==selectedViewId.value&&(data.value?.text("role")=="OWNER"||it.text("visibility")=="PERSONAL")}
 fun conditions()=queryConfig.getAsJsonObject("filter")?.objects("conditions").orEmpty()
 fun sorts()=queryConfig.objects("sort")
 fun applyConditions(items:List<JsonObject>){
  val op=queryConfig.getAsJsonObject("filter")?.text("op")?.takeIf{it=="or"}?:"and"
  queryTable(filter=JsonObject().apply{addProperty("op",op);add("conditions",Gson().toJsonTree(items))})
 }
 fun removeCondition(index:Int)=applyConditions(conditions().filterIndexed{i,_->i!=index})
 fun addCondition(fieldId:String,operator:String,value:JsonElement?){
  if(conditions().size>=10)return
  applyConditions(conditions()+JsonObject().apply{addProperty("fieldId",fieldId);addProperty("operator",operator);if(value!=null)add("value",value)})
 }
 fun removeSort(index:Int)=queryTable(sort=Gson().toJsonTree(sorts().filterIndexed{i,_->i!=index}).asJsonArray)
 fun addSort(fieldId:String,descending:Boolean){
  val next=sorts().filter{it.text("fieldId")!=fieldId}+JsonObject().apply{addProperty("fieldId",fieldId);addProperty("direction",if(descending)"desc" else "asc")}
  if(next.size<=3)queryTable(sort=Gson().toJsonTree(next).asJsonArray)
 }
 /** Kanban drop: set one SELECT cell; the board only changes after the server accepts it, so a failure leaves the card where it was. */
 fun moveCard(recordId:String,fieldId:String,value:String?){
  val record=data.value?.objects("records")?.firstOrNull{it.text("id")==recordId}?:return
  mutate("PATCH","$path/records/$recordId/cells",JsonObject().apply{
   addProperty("expectedVersion",record.get("version")?.asInt?:1);addProperty("expectedSchemaVersion",data.value?.get("schemaVersion")?.asInt?:1)
   add("changes",JsonObject().apply{if(value==null)add(fieldId,JsonNull.INSTANCE) else addProperty(fieldId,value)})
  })
 }
 fun canEditRecords()=data.value?.text("role") in listOf("OWNER","EDITOR")
 /** Grid/cards/Kanban, group-by and Kanban field of the open view; null means the view has none saved (the screen then uses the device's choice). */
 var viewLayout:JsonObject?=null
 /** One-shot messages the table screen shows as a snackbar (the status line only shows errors). */
 val notice=MutableStateFlow<String?>(null)
 /** The layout without fields deleted or retyped since it was saved, which the server would reject. */
 private fun savableLayout():JsonObject?{
  val layout=viewLayout?:return null;val fields=data.value?.objects("fields").orEmpty()
  val group=layout.text("groupFieldId").takeIf{id->fields.any{it.text("id")==id}}
  val stack=layout.text("kanbanFieldId").takeIf{id->fields.any{it.text("id")==id&&it.text("type")=="SELECT"}}
  return JsonObject().apply{addProperty("type",layout.text("type").takeUnless{it=="KANBAN"&&stack==null}?:"GRID");addProperty("groupFieldId",group);addProperty("kanbanFieldId",stack)}
 }
 /** Like Airtable, a layout change is saved on the view at once when this user may edit it; otherwise it stays a local change. */
 fun setLayout(layout:JsonObject){
  viewLayout=layout
  val view=editableView();val body=savableLayout()?:return
  if(view==null||!supports("viewLayout"))return
  if(busy.value){whenIdle{setLayout(layout)};return}
  run{
   val saved=repo.mutate("PATCH","$path/views/${view.text("id")}",JsonObject().apply{add("layout",body);add("expectedVersion",view["version"]);addProperty("requestId",java.util.UUID.randomUUID().toString())})
   // Only the layout changed on the server, so keep unsaved filter/sort edits instead of resetting them on reload.
   queryConfig=queryConfig.deepCopy().apply{add("viewVersion",saved["version"])}
   reload()
  }
 }
 /** Column order is part of the view config (fieldOrder), so "Lưu thay đổi chế độ xem" persists it like Airtable. */
 fun setFieldOrder(ids:List<String>){queryConfig=queryConfig.deepCopy().apply{add("fieldOrder",Gson().toJsonTree(ids))}}
 fun selectColumns(fields:List<JsonObject>,checked:BooleanArray){
  if(checked.none{it})return
  queryTable(fieldIds=Gson().toJsonTree(fields.filterIndexed{i,_->checked.getOrElse(i){false}}.map{it.text("id")}).asJsonArray)
 }
 fun updateSelectedView(){
  val view=editableView()?:return
  val payload=JsonObject().apply{listOf("filter","sort","fieldIds","fieldOrder").forEach{key->queryConfig.get(key)?.let{add(key,it.deepCopy())}};if(supports("viewLayout"))savableLayout()?.let{add("layout",it)};addProperty("expectedVersion",view.get("version")?.asInt?:1)}
  mutate("PATCH","$path/views/${view.text("id")}",payload){selectView(view.text("id"))}
 }
 fun fieldTypes()=listOf("TEXT","NUMBER","DATE","CHECKBOX","SELECT")+(if(supports("userFields"))listOf("USER") else emptyList())+if(supports("richFields"))listOf("LONG_TEXT","MULTI_SELECT","URL","EMAIL","PHONE") else emptyList()
 fun createField(name:String,type:String,rawOptions:String,position:Int?=null,created:(String)->Unit={},done:()->Unit):Int?{
  if(name.trim().length !in 1..100)return R.string.p0_nhap_ten_tu_1_den_100_ky_tu
  val options=rawOptions.split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
  if(type in listOf("SELECT","MULTI_SELECT")&&(options.isEmpty()||options.size>30||options.any{it.length>100}))return R.string.p0_nhap_tu_1_den_30_lua_chon_moi
  if(type !in fieldTypes())return R.string.p0_nhap_ten_tu_1_den_100_ky_tu
  mutate("POST","$path/fields",JsonObject().apply{addProperty("name",name.trim());addProperty("type",type);add("options",Gson().toJsonTree(options));addProperty("expectedSchemaVersion",data.value?.get("schemaVersion")?.asInt?:1);if(position!=null&&supports("fieldInsert"))addProperty("position",position)}){
   // Like Airtable, a field added from this screen shows in the current view even when the view hides columns.
   val ids=queryConfig.getAsJsonArray("fieldIds")
   if(ids!=null&&ids.size()>0&&it.text("id").isNotBlank())queryConfig=queryConfig.deepCopy().apply{getAsJsonArray("fieldIds").add(it.text("id"))}
   done();created(it.text("id"))
  }
  return null
 }
 fun canEditFields()=data.value?.text("role")=="OWNER"&&supports("fieldEdit")
 /** Same checks as createField; PATCH rolls back server-side if existing cells don't fit the new type/options. */
 fun updateField(field:JsonObject,name:String,type:String,rawOptions:String,done:()->Unit):Int?{
  if(name.trim().length !in 1..100)return R.string.p0_nhap_ten_tu_1_den_100_ky_tu
  val options=rawOptions.split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
  if(type in listOf("SELECT","MULTI_SELECT")&&(options.isEmpty()||options.size>30||options.any{it.length>100}))return R.string.p0_nhap_tu_1_den_30_lua_chon_moi
  mutate("PATCH","$path/fields/${field.text("id")}",JsonObject().apply{addProperty("name",name.trim());addProperty("type",type);add("options",Gson().toJsonTree(options));addProperty("expectedSchemaVersion",data.value?.get("schemaVersion")?.asInt?:1)}){done()}
  return null
 }
 fun deleteField(field:JsonObject)=mutate("DELETE","$path/fields/${field.text("id")}",JsonObject().apply{addProperty("expectedSchemaVersion",data.value?.get("schemaVersion")?.asInt?:1)})
 fun duplicateField(field:JsonObject){
  val names=data.value?.objects("fields").orEmpty().map{it.text("name").lowercase()}.toSet()
  val name=generateSequence(1){it+1}.map{if(it==1)"${field.text("name")} (bản sao)" else "${field.text("name")} (bản sao $it)"}.first{it.lowercase() !in names}.take(100)
  createField(name,field.text("type"),field.getAsJsonArray("options")?.joinToString(","){it.asString}.orEmpty()){}
 }
 /** Imports CSV rows into this table: matches headers to fields by name, creates missing fields, then one record per row. */
 fun importCsv(text:String){
  val rows=com.threeddk.tasks.data.parseCsv(text)
  if(rows.size<2||rows.size>501){error.value="File CSV cần dòng tiêu đề và từ 1 đến 500 dòng dữ liệu";return}
  val header=rows[0].map{it.trim()};val body=rows.drop(1)
  if(header.any{it.length !in 1..100}){error.value="Tên cột trong CSV phải từ 1 đến 100 ký tự";return}
  // Own job: run{} shares requestJob, which the table's re-query on resume cancels.
  viewModelScope.launch{try{
   busy.value=true;error.value=null
   var meta=repo.metadata(path)
   // Check every row before the first write: records are created one by one, so a bad row mid-file would leave a partial import.
   for((col,name) in header.withIndex()){
    val field=meta.objects("fields").firstOrNull{it.text("name").equals(name,true)}?:continue
    body.withIndex().firstOrNull{(_,row)->com.threeddk.tasks.data.csvCellInvalid(field,row.getOrElse(col){""})}?.let{(i,_)->error.value="Dòng ${i+2}, cột \"$name\": giá trị không hợp lệ với kiểu cột. Chưa nhập dòng nào.";return@launch}
    if(field.text("systemKey")=="title")body.withIndex().firstOrNull{(_,row)->row.getOrElse(col){""}.isBlank()}?.let{(i,_)->error.value="Dòng ${i+2}: cột \"$name\" không được để trống. Chưa nhập dòng nào.";return@launch}
   }
   if(meta.objects("fields").any{it.text("systemKey")=="title"&&header.none{h->h.equals(it.text("name"),true)}}){error.value="File CSV cần cột \"${meta.objects("fields").first{it.text("systemKey")=="title"}.text("name")}\". Chưa nhập dòng nào.";return@launch}
   for((col,name) in header.withIndex()){
    if(meta.objects("fields").any{it.text("name").equals(name,true)})continue
    val (type,options)=com.threeddk.tasks.data.inferFieldType(body.map{it.getOrElse(col){""}})
    repo.mutate("POST","$path/fields",JsonObject().apply{addProperty("requestId",java.util.UUID.randomUUID().toString());addProperty("name",name);addProperty("type",type);add("options",Gson().toJsonTree(options));addProperty("expectedSchemaVersion",meta["schemaVersion"].asInt)})
    meta=repo.metadata(path)
   }
   val fields=header.map{name->meta.objects("fields").first{it.text("name").equals(name,true)}}
   var created=0
   val rowsCells=body.map{row->JsonObject().also{cells->fields.forEachIndexed{col,field->com.threeddk.tasks.data.csvCell(field,row.getOrElse(col){""})?.let{cells.add(field.text("id"),it)}}}}
   try{
    if(supports("recordBatch"))for(chunk in com.threeddk.tasks.data.csvBatches(rowsCells)){
     // One request per chunk counts as one write on the server, so big files stay under the per-minute write limit.
     repo.mutate("POST","$path/records/batch",JsonObject().apply{addProperty("requestId",java.util.UUID.randomUUID().toString());addProperty("expectedSchemaVersion",meta["schemaVersion"].asInt);add("records",JsonArray().apply{chunk.forEach{c->add(JsonObject().apply{add("cells",c)})}})})
     created+=chunk.size
    }else for(cells in rowsCells){
     repo.mutate("POST","$path/records",JsonObject().apply{addProperty("requestId",java.util.UUID.randomUUID().toString());addProperty("expectedVersion",1);addProperty("expectedSchemaVersion",meta["schemaVersion"].asInt);add("cells",cells)});created++
    }
   }catch(e:CancellationException){throw e}catch(e:Exception){
    // e.g. the 500-record limit: say exactly how far it got so a retry does not duplicate rows.
    runCatching{reload()};error.value="Đã nhập $created/${body.size} dòng rồi dừng: ${friendlyError(e)}";return@launch
   }
   reload();note.value="Đã nhập ${body.size} dòng từ CSV";notice.value=note.value
  }catch(e:CancellationException){throw e}catch(e:Exception){error.value=friendlyError(e)}finally{busy.value=false}}
 }
 fun draftLabels():Array<String>{
  val firstField=data.value?.objects("fields")?.firstOrNull()?.text("id")
  return drafts.value.mapIndexed{index,draft->runCatching{JsonParser.parseString(draft.description).asJsonObject.text(firstField.orEmpty()).take(100)}.getOrDefault("").ifBlank{getApplication<Application>().getString(R.string.p0_new_row_number,index+1)}}.toTypedArray()
 }


 fun normalSpaces(query:String)=matchingItems("items",query).filterNot{isTestWorkspace(it.text("name"))}
 fun displayedFields():List<JsonObject>{
  val fields=data.value?.objects("fields").orEmpty();val ids=queryConfig.getAsJsonArray("fieldIds")?.map{it.asString}.orEmpty()
  val order=queryConfig.getAsJsonArray("fieldOrder")?.map{it.asString}.orEmpty()+fields.map{it.text("id")}
  return order.distinct().mapNotNull{id->fields.firstOrNull{it.text("id")==id&&(ids.isEmpty()||id in ids)}}
 }
 fun duplicateBase(target:String,base:JsonObject,name:String,done:(JsonObject)->Unit)=versionedMutation("POST","$target/duplicate",base,JsonObject().apply{addProperty("name",name.trim())},done)
 fun inviteMember(target:String,email:String,editor:Boolean,base:JsonObject?=null,done:()->Unit){
  if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){error.value="Email chưa hợp lệ.";return}
  val values=JsonObject().apply{addProperty("email",email.trim());addProperty("role",if(editor)"EDITOR" else "VIEWER");base?.get("version")?.let{add("expectedVersion",it)}}
  mutate("POST","$target/invites",values){done()}
 }

}
