package com.threeddk.tasks
import com.google.gson.JsonObject
import com.threeddk.tasks.data.TaskEditSession
import org.junit.Assert.*
import org.junit.Test
class TaskEditSessionTest {
 @Test fun rotationKeepsRetryIdentityAndPriorityChangesGetNewIdentity(){
  val fields=JsonObject().apply{addProperty("title","Same text");addProperty("priority","NORMAL")}
  val draft=TaskEditSession(2)
  val first=draft.request(fields)
  val restored=TaskEditSession(draft.version,draft.requestId,draft.lastBody)
  assertEquals(first,restored.request(fields.deepCopy()))
  fields.addProperty("priority","HIGH")
  assertNotEquals(first["requestId"],restored.request(fields)["requestId"])
 }
 @Test fun newerServerVersionRequiresExplicitReconciliation(){
  val session=TaskEditSession(2)
  val original=session.request(JsonObject())
  assertEquals(2,session.request(JsonObject())["expectedVersion"].asInt)
  session.reconcile(3)
  val reconciled=session.request(JsonObject())
  assertEquals(3,reconciled["expectedVersion"].asInt)
  assertNotEquals(original["requestId"],reconciled["requestId"])
 }
}
