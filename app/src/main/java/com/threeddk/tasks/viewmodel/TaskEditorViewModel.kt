package com.threeddk.tasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.threeddk.tasks.data.TaskEditSession
import com.threeddk.tasks.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

data class TaskEditorState(
 val title:String="", val description:String="", val priority:String="NORMAL",
 val selected:Set<String> = emptySet(), val representative:String="",
 val due:String=Instant.now().plusSeconds(86400).toString()
)

class TaskEditorViewModel(private val saved:SavedStateHandle=SavedStateHandle()):ViewModel(){
 private val mutableState=MutableStateFlow(saved.get<String>("draft")?.let{Gson().fromJson(it,TaskEditorState::class.java)}?:TaskEditorState())
 val state=mutableState.asStateFlow()
 private var initialized=saved.get<Boolean>("initialized")?:false
 private var edit=TaskEditSession(saved.get<Int>("version")?:1,saved.get<String>("requestId")?:java.util.UUID.randomUUID().toString(),saved.get<String>("lastBody"))
 val version get()=edit.version
 private fun persist(){
  saved["draft"]=Gson().toJson(state.value);saved["initialized"]=initialized
  saved["version"]=edit.version;saved["requestId"]=edit.requestId;saved["lastBody"]=edit.lastBody
 }
 fun open(task:Task?){if(initialized)return;initialized=true;if(task!=null)useRemote(task);persist()}
 fun input(title:String,description:String,priority:String){mutableState.value=state.value.copy(title=title,description=description,priority=priority);persist()}
 fun due(value:String){mutableState.value=state.value.copy(due=value);persist()}
 fun assign(ids:Set<String>){val old=state.value;mutableState.value=old.copy(selected=ids.toSet(),representative=old.representative.takeIf{it in ids}?:ids.firstOrNull().orEmpty());persist()}
 fun representative(id:String){if(id in state.value.selected){mutableState.value=state.value.copy(representative=id);persist()}}
 fun reconcile(version:Int){edit.reconcile(version);persist()}
 fun useRemote(task:Task){mutableState.value=TaskEditorState(task.title,task.description,task.priority,task.assigneeIds.toSet(),task.representativeId,task.dueAt);edit.reconcile(task.version);persist()}
 val titleError get()=if(state.value.title.isBlank())"Nhập tiêu đề." else null
 val descriptionError get()=if(state.value.description.isBlank())"Mô tả đầu ra cần bàn giao." else null
 fun validationError():String?=when{
  titleError!=null->titleError
  descriptionError!=null->descriptionError
  state.value.selected.isEmpty()||state.value.representative !in state.value.selected->"Chọn thành viên và người đại diện."
  else->null
 }
 fun request():JsonObject{
  require(validationError()==null)
  val s=state.value
  return edit.request(JsonObject().apply{
   addProperty("title",s.title.trim());addProperty("description",s.description.trim());addProperty("dueAt",s.due);addProperty("priority",s.priority)
   add("assigneeIds",Gson().toJsonTree(s.selected.sorted()));addProperty("representativeId",s.representative)
  }).also{persist()}
 }
}
