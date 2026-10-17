package com.threeddk.tasks

import android.app.Application
import androidx.lifecycle.*
import com.google.gson.*
import com.threeddk.tasks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

fun typedCells(fields:List<JsonObject>,raw:JsonObject,members:List<JsonObject> = emptyList(),baseline:JsonObject=JsonObject()):JsonObject=JsonObject().apply{
 fields.forEach{field->
  val id=field.text("id");val value=raw.get(id)
  if(field.text("systemKey")=="title")require(value!=null&&!value.isJsonNull&&value.asString.isNotBlank()){"Cột ${field.text("name")}: cần nhập tên công việc."}
  if(value==null||value.isJsonNull||value.asString.isBlank()){add(id,JsonNull.INSTANCE);return@forEach}
  val text=value.asString
  try{
   when(field.text("type")){
    "NUMBER"->{require(text.length<=100);addProperty(id,text.toBigDecimal())}
    "DATE"->{require(text.matches(Regex("\\d{4}-\\d{2}-\\d{2}")));LocalDate.parse(text);addProperty(id,text)}
    "CHECKBOX"->{require(text in listOf("true","false"));addProperty(id,text.toBoolean())}
    "SELECT"->{require(field.getAsJsonArray("options").any{it.asString==text});addProperty(id,text)}
    "USER"->{require(baseline.get(id)==value||members.any{it.text("uid")==text&&it.get("active")?.asBoolean!=false});addProperty(id,text)}
    "TEXT"->{require(text.length<=5000);addProperty(id,text)}
    else->throw IllegalArgumentException("Unsupported field type")
   }
  }catch(e:Exception){throw IllegalArgumentException("Cột ${field.text("name")}: giá trị chưa đúng kiểu dữ liệu.")}
 }
}

data class CellConflict(val fieldId:String,val name:String,val base:JsonElement?,val mine:JsonElement?,val remote:JsonElement?)
private fun cellValue(cells:JsonObject,id:String)=cells[id]?:JsonNull.INSTANCE
fun cellChanges(base:JsonObject,edited:JsonObject)=JsonObject().apply{
 edited.entrySet().forEach{(id,value)->if(value!=cellValue(base,id))add(id,value.deepCopy())}
}
fun cellConflicts(fields:List<JsonObject>,base:JsonObject,edited:JsonObject,remote:JsonObject)=cellChanges(base,edited).entrySet().mapNotNull{(id,value)->
 if(cellValue(remote,id)!=cellValue(base,id)&&cellValue(remote,id)!=value)CellConflict(id,fields.firstOrNull{it.text("id")==id}?.text("name")?:id,cellValue(base,id),value,cellValue(remote,id)) else null
}
fun mergeCells(base:JsonObject,edited:JsonObject,remote:JsonObject,keepMine:Map<String,Boolean>):JsonObject=remote.deepCopy().apply{
 cellChanges(base,edited).entrySet().forEach{(id,value)->
  val conflicts=cellValue(remote,id)!=cellValue(base,id)&&cellValue(remote,id)!=value
  if(!conflicts||keepMine[id]==true)add(id,value.deepCopy())
 }
}
fun createRecordPayload(draft:Draft,cells:JsonObject):JsonObject=
 if(draft.pendingCreateJson.isNotBlank())JsonParser.parseString(draft.pendingCreateJson).asJsonObject else JsonObject().apply{
  addProperty("requestId",draft.requestId);addProperty("expectedVersion",draft.expectedVersion)
  addProperty("expectedSchemaVersion",draft.expectedSchemaVersion);add("cells",cells.deepCopy())
 }
fun canUnlockPendingCreation(code:String?,retrying:Boolean,networkAttempted:Boolean):Boolean {
 // SQL replays committed requests before checking schema, so this rejection proves no row was created.
 if(!networkAttempted||code=="SCHEMA_CHANGED")return true
 return !retrying&&code in listOf("VALIDATION","FORBIDDEN","UNVERIFIED","RATE_LIMIT","NOT_FOUND","CLIENT_UPGRADE_REQUIRED")
}

class RecordViewModel(app:Application,private val savedState:SavedStateHandle):AndroidViewModel(app){
 private val session=(app as TasksApp).repository
 private val repo=session.workspaces
 val table=MutableStateFlow<JsonObject?>(null)
 val history=MutableStateFlow<JsonObject?>(null)
 val status=MutableStateFlow("Đang tải…")
 val ready=MutableStateFlow(false)
 val busy=MutableStateFlow(false)
 val creationPending=MutableStateFlow(false)
 val conflict=MutableStateFlow(false)
 val conflictDetails=MutableStateFlow<List<CellConflict>>(emptyList())
 val finished=MutableStateFlow(false)
 var raw=JsonObject();private set
 var version=1;private set
 var writable=false;private set
 val canArchive get()=recordId!="new"&&writable&&ready.value
 private var owner="";private var path="";private var recordId="";private var key=""
 private var baseline=JsonObject()
 private var remoteRecord:JsonObject?=null
 private var schemaVersion=1
 private var dirty=false
 private var conflictLoaded=false
 private var pendingCreateJson=""
 private var requestId=java.util.UUID.randomUUID().toString()
 private var archiveRequestId=java.util.UUID.randomUUID().toString()
 private val draftMutex=Mutex()
 fun open(resource:String,id:String){
  if(path.isNotEmpty())return
  owner=repo.uid;path=resource;recordId=if(id.startsWith("new/"))"new" else id
  val localId=if(id.startsWith("new/"))id.substringAfter("new/") else if(id=="new")savedState.get<String>("localDraftId")?:java.util.UUID.randomUUID().toString().also{savedState["localDraftId"]=it} else id
  key="v2/$resource/"+if(recordId=="new")"new/$localId" else id
  viewModelScope.launch{
   try{
    val draft=repo.draft(key)?:if(recordId=="new")repo.migrateLegacyDraft("v2/$resource/new",key) else null
    if(draft!=null){dirty=true;raw=JsonParser.parseString(draft.description).asJsonObject;version=draft.expectedVersion;requestId=draft.requestId;schemaVersion=draft.expectedSchemaVersion;baseline=parseCells(draft.baselineJson);pendingCreateJson=draft.pendingCreateJson;creationPending.value=pendingCreateJson.isNotBlank()}
    val cachedMeta=repo.cached(path,"v2-meta")?:repo.cached(path)?.apply{if(!has("schemaVersion"))addProperty("schemaVersion",1)}
    val cachedRecord=if(recordId=="new")null else repo.cached("$path/$id","v2-record")
      ?:repo.cached(path)?.objects("records")?.firstOrNull{it.text("id")==id}
    if(cachedMeta!=null&&(recordId=="new"||cachedRecord!=null))initialize(cachedMeta,cachedRecord,draft)
    val meta=repo.metadata(path);val record=if(recordId=="new")null else repo.record(path,id)
    if(!ready.value||!dirty)initialize(meta,record,draft)
    table.value=meta;writable=meta.text("role") in listOf("OWNER","EDITOR")
    if(dirty&&!creationPending.value&&((record?.get("version")?.asInt?:1)!=version||meta["schemaVersion"].asInt!=schemaVersion))setConflict(meta,record)
    if(creationPending.value)status.value=pendingCreateMessage
   }catch(e:CancellationException){throw e}catch(e:Exception){
    if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","UNVERIFIED","NOT_FOUND"))hideRemoteData()
    status.value=friendlyError(e)+if(raw.size()>0)" Nháp vẫn được giữ trên máy." else ""
   }
  }
 }
 private fun parseCells(value:String)=runCatching{JsonParser.parseString(value).asJsonObject}.getOrDefault(JsonObject())
 private fun initialize(meta:JsonObject,record:JsonObject?,draft:Draft?){
  table.value=meta;writable=meta.text("role") in listOf("OWNER","EDITOR")
  if(draft==null){baseline=record?.getAsJsonObject("cells")?.deepCopy()?:JsonObject();raw=baseline.deepCopy();version=record?.get("version")?.asInt?:1;schemaVersion=meta["schemaVersion"].asInt}
  else if(draft.baselineJson.isBlank()&&record?.get("version")?.asInt==version)baseline=record.getAsJsonObject("cells").deepCopy()
  ready.value=true;status.value=if(creationPending.value)pendingCreateMessage else if(draft!=null)"Đã khôi phục bản nháp trên máy" else if(writable)"Thay đổi được lưu nháp trên máy" else "Bạn có quyền chỉ xem"
 }
 fun change(id:String,value:String){
  if(busy.value||!writable||creationPending.value)return
  dirty=true;raw.addProperty(id,value);requestId=java.util.UUID.randomUUID().toString()
  val draft=makeDraft()
  viewModelScope.launch{try{draftMutex.withLock{repo.save(draft)};status.value="Đã lưu nháp trên máy • Chưa gửi"}catch(e:CancellationException){throw e}catch(e:Exception){status.value="Chưa lưu được nháp. Hãy giữ màn hình và thử lại."}}
 }
 private val pendingCreateMessage="Chưa biết máy chủ đã tạo dòng hay chưa. Nội dung đang khóa; bấm Thử gửi lại để kiểm tra đúng yêu cầu cũ."
 private fun makeDraft()=Draft(owner,key,raw.toString(),requestId=requestId,expectedVersion=version,baselineJson=baseline.toString(),expectedSchemaVersion=schemaVersion,pendingCreateJson=pendingCreateJson)
 private fun currentTyped()=typedCells(table.value!!.objects("fields"),raw,table.value!!.objects("members"),baseline)
 private fun hideRemoteData(){
  raw=runCatching{cellChanges(baseline,currentTyped())}.getOrElse{JsonObject().apply{
   raw.entrySet().forEach{(id,value)->if(runCatching{value.asString!=cellValue(baseline,id).asString}.getOrDefault(value!=cellValue(baseline,id)))add(id,value.deepCopy())}
  }}
  writable=false;ready.value=false;table.value=null;history.value=null;remoteRecord=null;conflictDetails.value=emptyList();conflict.value=false
 }
 private fun setConflict(meta:JsonObject,record:JsonObject?){
  table.value=meta;remoteRecord=record
  conflictLoaded=true
  val edited=runCatching{currentTyped()}.getOrDefault(raw)
  conflictDetails.value=cellConflicts(meta.objects("fields"),baseline,edited,record?.getAsJsonObject("cells")?:JsonObject())
  conflict.value=true;status.value="Dòng hoặc cấu trúc bảng đã thay đổi. Nháp vẫn còn; hãy so sánh trước khi lưu lại."
 }
 fun reconcile(resolutions:Map<String,Boolean> = emptyMap()){
  if(busy.value||!conflict.value)return
  viewModelScope.launch{
   busy.value=true
   try{
    if(!conflictLoaded){setConflict(repo.metadata(path),if(recordId=="new")null else repo.record(path,recordId));return@launch}
    val meta=table.value?:throw ApiException("NOT_FOUND")
    if(meta.text("role") !in listOf("OWNER","EDITOR"))throw ApiException("FORBIDDEN")
    val latest=remoteRecord?.getAsJsonObject("cells")?:JsonObject()
    val fieldIds=meta.objects("fields").map{it.text("id")}.toSet()
    val orphans=raw.entrySet().filter{it.key !in fieldIds}.associate{it.key to it.value.deepCopy()}
    raw=mergeCells(baseline,runCatching{currentTyped()}.getOrDefault(raw),latest,resolutions)
    orphans.forEach{(id,value)->raw.add(id,value)}
    baseline=latest.deepCopy();version=remoteRecord?.get("version")?.asInt?:1;schemaVersion=meta["schemaVersion"].asInt
    requestId=java.util.UUID.randomUUID().toString();archiveRequestId=java.util.UUID.randomUUID().toString();conflict.value=false;conflictDetails.value=emptyList()
    draftMutex.withLock{repo.save(makeDraft())};status.value="Đã hợp nhất lựa chọn. Kiểm tra nội dung rồi bấm Lưu."
   }catch(e:CancellationException){throw e}catch(e:Exception){if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","NOT_FOUND"))hideRemoteData();status.value=friendlyError(e)}finally{busy.value=false}
  }
 }
 fun submit(){
  if(busy.value||!writable||!ready.value||conflict.value)return
  val isCreate=recordId=="new"
  val retryingCreate=isCreate&&pendingCreateJson.isNotBlank()
  val cells=try{if(retryingCreate)JsonParser.parseString(pendingCreateJson).asJsonObject.getAsJsonObject("cells") else currentTyped()}catch(e:Exception){status.value=e.message?:"Giá trị chưa hợp lệ";return}
  val changes=if(isCreate)cells else cellChanges(baseline,cells)
  if(isCreate){pendingCreateJson=createRecordPayload(makeDraft(),cells).toString();creationPending.value=true}
  val draft=makeDraft()
  viewModelScope.launch{
   busy.value=true
   var networkAttempted=false
   try{
    draftMutex.withLock{repo.save(draft)}
    var result:JsonObject?=null
    if(isCreate||changes.size()>0){
     val payload=if(isCreate)createRecordPayload(draft,cells) else JsonObject().apply{addProperty("requestId",draft.requestId);addProperty("expectedVersion",draft.expectedVersion);addProperty("expectedSchemaVersion",schemaVersion);add("changes",changes)}
     networkAttempted=true
     result=repo.mutate(if(isCreate)"POST" else "PATCH","$path/records"+if(isCreate)"" else "/$recordId/cells",payload)
     if(isCreate){
      require(result.text("id").isNotBlank())
      val oldKey=key;recordId=result.text("id");key="v2/$path/$recordId"
      version=result["version"]?.asInt?:1;baseline=result.getAsJsonObject("cells")?.deepCopy()?:cells.deepCopy()
      pendingCreateJson="";creationPending.value=false;requestId=java.util.UUID.randomUUID().toString()
      draftMutex.withLock{repo.acknowledgeCreation(makeDraft(),oldKey)}
     }
    }
    val fieldIds=table.value!!.objects("fields").map{it.text("id")}.toSet()
    if(raw.keySet().any{it !in fieldIds}){
     // Keep removed-column content locally so saving valid cells never destroys that draft.
     val oldKey=key
     result?.let{saved->
      if(recordId=="new"){recordId=saved.text("id");key="v2/$path/$recordId"}
      version=saved["version"]?.asInt?:version;baseline=saved.getAsJsonObject("cells")?.deepCopy()?:cells.deepCopy()
     }
     draftMutex.withLock{repo.save(makeDraft());if(oldKey!=key)repo.removeDraft(oldKey,owner)}
    }else draftMutex.withLock{repo.removeDraft(key,owner)}
    finished.value=true
   }catch(e:CancellationException){throw e}catch(e:Exception){
    if(isCreate&&creationPending.value){
     if(canUnlockPendingCreation((e as? ApiException)?.code,retryingCreate,networkAttempted)){
      pendingCreateJson="";creationPending.value=false
      val saved=try{draftMutex.withLock{repo.save(makeDraft())};true}catch(next:CancellationException){throw next}catch(_:Exception){false}
      if(saved)handleWriteError(e) else status.value="Chưa lưu được nháp. Hãy giữ màn hình và thử lại."
     }else {
      if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","UNVERIFIED","NOT_FOUND"))hideRemoteData()
      status.value=pendingCreateMessage+" "+friendlyError(e)
     }
    }else handleWriteError(e)
   }finally{busy.value=false}
  }
 }
 private suspend fun handleWriteError(e:Exception){
  if(e is ApiException&&e.code in listOf("CONFLICT","SCHEMA_CHANGED")){
   conflict.value=true
   try{setConflict(repo.metadata(path),if(recordId=="new")null else repo.record(path,recordId))}
   catch(next:Exception){if(next is CancellationException)throw next;if(next is ApiException&&next.code in listOf("FORBIDDEN","UNAUTHORIZED","NOT_FOUND"))hideRemoteData();status.value=friendlyError(next)+" Nháp vẫn được giữ."}
  }else{if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","NOT_FOUND"))hideRemoteData();status.value=friendlyError(e)}
 }
 fun archive(){
  if(busy.value||!canArchive||conflict.value)return
  viewModelScope.launch{busy.value=true
   try{
    repo.mutate("DELETE","$path/records/$recordId",JsonObject().apply{addProperty("expectedVersion",version);addProperty("expectedSchemaVersion",schemaVersion);addProperty("requestId",archiveRequestId)})
    draftMutex.withLock{repo.removeDraft(key,owner)};finished.value=true
   }catch(e:CancellationException){throw e}catch(e:Exception){handleWriteError(e)}finally{busy.value=false}
  }
 }
 fun loadHistory(){if(recordId=="new"||busy.value)return;viewModelScope.launch{
  try{history.value=repo.cached("$path/records/$recordId/history");repo.refresh("$path/records/$recordId/history");history.value=repo.cached("$path/records/$recordId/history")}
  catch(e:CancellationException){throw e}catch(e:Exception){if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED","NOT_FOUND"))hideRemoteData();status.value=friendlyError(e)}
 }}
}
