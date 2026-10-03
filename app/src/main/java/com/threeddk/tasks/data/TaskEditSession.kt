package com.threeddk.tasks.data
import com.google.gson.JsonObject
import java.util.UUID

class TaskEditSession(var version:Int, var requestId:String=UUID.randomUUID().toString(), var lastBody:String?=null) {
 fun request(fields:JsonObject):JsonObject {
  val body=fields.deepCopy().apply{remove("requestId");addProperty("expectedVersion",version)}
  val fingerprint=body.toString()
  if(lastBody!=null&&lastBody!=fingerprint)requestId=UUID.randomUUID().toString()
  lastBody=fingerprint
  return body.apply{addProperty("requestId",requestId)}
 }
 fun reconcile(newVersion:Int){version=newVersion;requestId=UUID.randomUUID().toString();lastBody=null}
}
