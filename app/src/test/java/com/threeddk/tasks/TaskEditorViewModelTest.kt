package com.threeddk.tasks

import com.threeddk.tasks.model.Task
import com.threeddk.tasks.viewmodel.TaskEditorViewModel
import org.junit.Assert.*
import org.junit.Test

class TaskEditorViewModelTest {
 @Test fun savedStateRestoresDraftAndRetryAfterProcessRecreation(){
  val saved=androidx.lifecycle.SavedStateHandle();val vm=TaskEditorViewModel(saved)
  vm.open(null);vm.input("Draft","Deliverable","HIGH");vm.assign(setOf("u"));val request=vm.request()
  val restored=TaskEditorViewModel(androidx.lifecycle.SavedStateHandle(saved.keys().associateWith{saved.get<Any?>(it)}))
  restored.open(null)
  assertEquals(vm.state.value,restored.state.value);assertEquals(request,restored.request())
 }
 @Test fun reopeningAfterRotationKeepsDraftAndRetryIdentity(){
  val vm=TaskEditorViewModel();val task=Task(id="a",title="Old",description="Output",assigneeIds=listOf("u"),representativeId="u",version=3)
  vm.open(task);vm.input("Draft","Edited output","HIGH");val request=vm.request()
  vm.open(task)
  assertEquals("Draft",vm.state.value.title);assertEquals(request,vm.request());assertEquals(3,vm.version)
 }
 @Test fun assignmentCannotKeepRepresentativeOutsideSelectedMembers(){
  val vm=TaskEditorViewModel();vm.assign(setOf("a","b"));vm.representative("b");vm.assign(setOf("a"))
  assertEquals("a",vm.state.value.representative)
  vm.representative("outsider");assertEquals("a",vm.state.value.representative)
  vm.assign(emptySet());vm.input("Work","Output","NORMAL");assertNotNull(vm.validationError())
 }
 @Test fun remoteVersionNeedsExplicitReconciliation(){
  val vm=TaskEditorViewModel();vm.open(Task(title="Old",description="Output",assigneeIds=listOf("u"),representativeId="u",version=1))
  vm.input("Mine","Output","HIGH");val old=vm.request()
  vm.reconcile(2);assertEquals("Mine",vm.state.value.title)
  val next=vm.request();assertEquals(2,next["expectedVersion"].asInt);assertNotEquals(old["requestId"],next["requestId"])
 }
}
