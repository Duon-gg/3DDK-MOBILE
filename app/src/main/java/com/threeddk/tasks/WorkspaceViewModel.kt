package com.threeddk.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.threeddk.tasks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WorkspaceViewModel(app:Application):AndroidViewModel(app) {
 val session=(app as TasksApp).repository
 val repo=session.workspaces
 val data=MutableStateFlow<JsonObject?>(null)
 val busy=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 val note=MutableStateFlow("Đang xem dữ liệu đã lưu")
 private var observer:Job?=null
 private var path=""
 private var pendingPair:Pair<String,JsonObject>?=null
 fun open(resource:String){
  if(path!=resource||observer==null){
   path=resource;observer?.cancel();data.value=null
   observer=viewModelScope.launch{repo.observe(repo.uid,resource).collect{data.value=it}}
  }
  refresh()
 }
 fun refresh(){if(busy.value)return;run{repo.refresh(path);note.value="Đã cập nhật • Có thể xem khi mất mạng"}}
 private fun run(block:suspend()->Unit){viewModelScope.launch{
  busy.value=true;error.value=null
  try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=if(e is ApiException&&e.code=="CONFLICT")"Dữ liệu đã thay đổi. Làm mới rồi kiểm tra lại." else friendlyError(e);note.value="Chưa cập nhật được • Giữ dữ liệu đã tải"}finally{busy.value=false}
 }}
 fun mutate(method:String,target:String,values:JsonObject,done:(JsonObject)->Unit={}){
  if(busy.value)return
  // Reuse the exact request ID if the same form is retried after a timeout.
  val signature=method+target+values.toString()
  val payload=if(pendingPair?.first==signature)pendingPair!!.second else values.deepCopy().apply{addProperty("requestId",java.util.UUID.randomUUID().toString())}.also{pendingPair=signature to it}
  run{val result=repo.mutate(method,target,payload);pendingPair=null;runCatching{repo.refresh(path)}.onFailure{note.value="Đã lưu • Chưa tải lại được"};done(result)}
 }
}
