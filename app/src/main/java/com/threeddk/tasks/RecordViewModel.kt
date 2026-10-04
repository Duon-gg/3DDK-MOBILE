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

fun typedCells(fields:List<JsonObject>,raw:JsonObject):JsonObject=JsonObject().apply{
 fields.forEach{field->
  val id=field.text("id");val value=raw.get(id)
  if(value==null||value.isJsonNull||value.asString.isBlank()){add(id,JsonNull.INSTANCE);return@forEach}
  val text=value.asString
  try{
   when(field.text("type")){
    "NUMBER"->{require(text.length<=100);addProperty(id,text.toBigDecimal())}
    "DATE"->{require(text.matches(Regex("\\d{4}-\\d{2}-\\d{2}")));LocalDate.parse(text);addProperty(id,text)}
    "CHECKBOX"->{require(text in listOf("true","false"));addProperty(id,text.toBoolean())}
    "SELECT"->{require(field.getAsJsonArray("options").any{it.asString==text});addProperty(id,text)}
    else->{require(text.length<=5000);addProperty(id,text)}
   }
  }catch(e:Exception){throw IllegalArgumentException("Cột ${field.text("name")}: giá trị chưa đúng kiểu dữ liệu.")}
 }
}

class RecordViewModel(app:Application):AndroidViewModel(app){
 private val session=(app as TasksApp).repository
 private val repo=session.workspaces
 val table=MutableStateFlow<JsonObject?>(null)
 val status=MutableStateFlow("Đang tải…")
 val ready=MutableStateFlow(false)
 val busy=MutableStateFlow(false)
 val conflict=MutableStateFlow(false)
 val finished=MutableStateFlow(false)
 var raw=JsonObject();private set
 var version=1;private set
 var writable=false;private set
 private var path="";private var recordId="";private var key=""
 private var requestId=java.util.UUID.randomUUID().toString()
 private var observer:Job?=null
 private val draftMutex=Mutex()
 fun open(resource:String,id:String){
  if(path.isNotEmpty())return
  path=resource;recordId=id;key="v2/$resource/$id"
  viewModelScope.launch{
   val draft=repo.draft(key)
   if(draft!=null){raw=JsonParser.parseString(draft.description).asJsonObject;version=draft.expectedVersion;requestId=draft.requestId}
   observer=launch{repo.observe(repo.uid,path).collect{snapshot->
    if(snapshot!=null){table.value=snapshot;writable=snapshot.text("role")!="VIEWER"
     if(!ready.value){
      val record=snapshot.objects("records").firstOrNull{it.text("id")==recordId}
      if(draft==null){raw=record?.getAsJsonObject("cells")?.deepCopy()?:JsonObject();version=record?.get("version")?.asInt?:1}
      ready.value=true;status.value=if(draft!=null)"Đã khôi phục bản nháp trên máy" else if(writable)"Thay đổi được lưu nháp trên máy" else "Bạn có quyền chỉ xem"
     }
     if(recordId!="new"&&snapshot.objects("records").none{it.text("id")==recordId}){writable=false;status.value="Dòng không còn tồn tại. Bản nháp vẫn được giữ."}
    }
   }}
   try{repo.refresh(path)}catch(e:Exception){if(e is ApiException&&e.code in listOf("FORBIDDEN","UNAUTHORIZED"))writable=false;status.value=friendlyError(e)}
  }
 }
 fun change(id:String,value:String){
  if(busy.value)return
  raw.addProperty(id,value);requestId=java.util.UUID.randomUUID().toString()
  val draft=makeDraft()
  viewModelScope.launch{try{draftMutex.withLock{repo.save(draft)};status.value="Đã lưu nháp trên máy • Chưa gửi"}catch(e:Exception){status.value="Chưa lưu được nháp. Hãy giữ màn hình và thử lại."}}
 }
 private fun makeDraft()=Draft(repo.uid,key,raw.toString(),requestId=requestId,expectedVersion=version)
 fun reconcile(){
  if(busy.value)return
  viewModelScope.launch{
   busy.value=true
   try{
    repo.refresh(path)
    // Read the refreshed snapshot directly, not the asynchronously emitted previous UI value.
    val entry=session.dao.entry(repo.uid,"v2",path)?:throw ApiException("NOT_FOUND")
    val snapshot=JsonParser.parseString(entry.json).asJsonObject
    val record=snapshot.objects("records").firstOrNull{it.text("id")==recordId}?:throw ApiException("NOT_FOUND")
    if(snapshot.text("role")=="VIEWER")throw ApiException("FORBIDDEN")
    table.value=snapshot;version=record.get("version").asInt;requestId=java.util.UUID.randomUUID().toString();conflict.value=false
    draftMutex.withLock{repo.save(makeDraft())};status.value="Đã nhận phiên bản mới. Kiểm tra nội dung nháp trước khi bấm Lưu."
   }catch(e:Exception){status.value=friendlyError(e)}finally{busy.value=false}
  }
 }
 fun submit(){
  if(busy.value||!writable||!ready.value||conflict.value)return
  val cells=try{typedCells(table.value!!.objects("fields"),raw)}catch(e:Exception){status.value=e.message?:"Giá trị chưa hợp lệ";return}
  val draft=makeDraft()
  viewModelScope.launch{
   busy.value=true
   try{
    draftMutex.withLock{repo.save(draft)}
    val payload=JsonObject().apply{addProperty("requestId",draft.requestId);addProperty("expectedVersion",draft.expectedVersion);add("cells",cells)}
    repo.mutate(if(recordId=="new")"POST" else "PATCH","$path/records"+if(recordId=="new")"" else "/$recordId",payload)
    draftMutex.withLock{repo.removeDraft(key)};runCatching{repo.refresh(path)};finished.value=true
   }catch(e:Exception){
    if(e is ApiException&&e.code=="CONFLICT"){conflict.value=true;status.value="Dòng đã được sửa trên thiết bị khác. Nháp vẫn còn; hãy xem bản mới trước khi lưu lại."}
    else status.value=friendlyError(e)
   }finally{busy.value=false}
  }
 }
}
