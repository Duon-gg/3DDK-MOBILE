package com.threeddk.tasks
import com.threeddk.tasks.data.*
import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
class ApiTransportTest {
 @Test fun expiredTokenRetriesOnceAndKeepsRequestBody()=runTest {
  val server=MockWebServer();server.start()
  try {
   server.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"UNAUTHORIZED\"}"))
   server.enqueue(MockResponse().setBody("{\"id\":\"task\"}"))
   val api=ApiTransport(server.url("/").toString()){force->if(force)"fresh" else "old"}
   val body=JsonObject().apply {addProperty("requestId","same-request")}
   assertEquals("task",api.call("POST","tasks",body).asJsonObject["id"].asString)
   val a=server.takeRequest();val b=server.takeRequest()
   assertEquals("Bearer old",a.getHeader("Authorization"));assertEquals("Bearer fresh",b.getHeader("Authorization"))
   assertEquals(a.body.readUtf8(),b.body.readUtf8())
  } finally {server.shutdown()}
 }
 @Test fun conflictIsPreservedForDraftRecovery()=runTest {
  val server=MockWebServer();server.start()
  try {
   server.enqueue(MockResponse().setResponseCode(409).setBody("{\"code\":\"CONFLICT\"}"))
   try {ApiTransport(server.url("/").toString()){"token"}.call("POST","tasks",JsonObject());fail("Expected conflict")}
   catch(e:ApiException){assertEquals("CONFLICT",e.code)}
   assertEquals(1,server.requestCount)
  } finally {server.shutdown()}
 }
}
