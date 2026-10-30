package com.threeddk.tasks

import android.os.Bundle
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.navigation.fragment.NavHostFragment
import com.threeddk.tasks.data.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

internal data class LiveWorkspaceFixture(val path:String,val metadata:JsonObject) {
 val titleId get()=metadata.objects("fields").first{it.text("systemKey")=="title"}.text("id")
 val schemaVersion get()=metadata["schemaVersion"].asInt
}

/** Each run owns its demo workspace, so retries never reuse or alter a teammate's data. */
internal suspend fun createLiveWorkspace(session:TaskRepository):LiveWorkspaceFixture {
 assertTrue("Đăng nhập và xác minh email trên thiết bị trước khi chạy",session.auth?.currentUser?.isEmailVerified==true)
 val repo=session.workspaces
 val capabilities=session.workspaceApi.call("GET","capabilities").asJsonObject
 assertTrue("Cloud phải được cập nhật P0",capabilities.getAsJsonObject("features")["atomicTemplates"].asBoolean)
 val suffix=java.util.UUID.randomUUID().toString().take(8)
 val workspace=repo.mutate("POST","workspaces",session.body("name" to "3DDK • Kiểm thử P0 $suffix"))
 val base=repo.mutate("POST","workspaces/${workspace.text("id")}/bases",session.body("name" to "Dự án kiểm thử"))
 val table=repo.mutate("POST","bases/${base.text("id")}/tables",session.body("name" to "Công việc của nhóm","template" to "TASKS"))
 val path="tables/${table.text("id")}"
 return LiveWorkspaceFixture(path,repo.metadata(path))
}

/** Opt-in only. Creates a visibly named demo workspace using the device's existing verified session. */
@RunWith(AndroidJUnit4::class)
class WorkspaceLiveTest {
 @Test fun authenticatedCreateTableAndRenderGrid():Unit=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkspace")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>();val session=app.repository
  val repo=session.workspaces
  fun payload(vararg p:Pair<String,Any?>)=session.body(*p)
  val fixture=createLiveWorkspace(session);val path=fixture.path;val title=fixture.titleId
  val cells=JsonObject().apply{addProperty(title,"Thử bảng 3DDK")}
  val creation=payload("expectedSchemaVersion" to fixture.schemaVersion,"cells" to cells)
  val row=repo.mutate("POST","$path/records",creation)
  assertEquals("Thử lại cùng yêu cầu không được tạo dòng thứ hai",row,repo.mutate("POST","$path/records",creation))
  val changes=JsonObject().apply{addProperty(title,"Bảng đã kết nối thành công")}
  val update=payload("expectedVersion" to row["version"].asInt,"expectedSchemaVersion" to fixture.schemaVersion,"changes" to changes)
  val edited=repo.mutate("PATCH","$path/records/${row.text("id")}/cells",update)
  assertEquals(2,edited.get("version").asInt)
  assertEquals(edited,repo.mutate("PATCH","$path/records/${row.text("id")}/cells",update))
  try {
   repo.mutate("PATCH","$path/records/${row.text("id")}/cells",payload("expectedVersion" to row["version"].asInt,"expectedSchemaVersion" to fixture.schemaVersion,"changes" to cells))
   fail("Bản đã cũ phải bị từ chối")
  }catch(e:ApiException){assertEquals("CONFLICT",e.code)}
  val query=JsonObject().apply{addProperty("expectedSchemaVersion",fixture.schemaVersion);addProperty("limit",50)}
  val page=repo.query(path,query)
  assertEquals(1,page.objects("items").size)
  assertEquals("Bảng đã kết nối thành công",page.objects("items").single().getAsJsonObject("cells").text(title))
  val cached=repo.observeTable(repo.uid,path,query).first()!!
  assertEquals(page.objects("items"),cached.objects("records"))
  assertTrue("Dữ liệu đã tải phải có cache trên máy",cached["queryCached"].asBoolean)
  val history=session.workspaceApi.call("GET","$path/records/${row.text("id")}/history").asJsonObject.objects("items")
  assertEquals("Replay và xung đột không được sinh thêm lịch sử",2,history.size)
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   scenario.onActivity{activity->val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController;nav.navigate(R.id.dataTable,Bundle().apply{putString("path",path)})}
   var visible=false
   for(attempt in 0..30){
    try{onView(withId(R.id.heading)).check(matches(withText("Công việc của nhóm")));visible=true;break}catch(e:AssertionError){android.os.SystemClock.sleep(300)}
   }
   assertTrue("Table UI did not render",visible)
   onView(withId(R.id.grid)).check{view,error->if(error!=null)throw error;assertTrue("Grid must have visible height",view.height>100)}
   scenario.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(app.getExternalFilesDir(null),"workspace-preview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  Unit
 }
}
