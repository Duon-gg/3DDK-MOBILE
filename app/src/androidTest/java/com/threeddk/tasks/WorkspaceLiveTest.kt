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
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only. Creates a visibly named demo workspace using the device's existing verified session. */
@RunWith(AndroidJUnit4::class)
class WorkspaceLiveTest {
 @Test fun authenticatedCreateTableAndRenderGrid():Unit=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkspace")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>();val session=app.repository
  assertTrue("Đăng nhập và xác minh email trên thiết bị trước khi chạy",session.auth?.currentUser?.isEmailVerified==true)
  val repo=session.workspaces
  fun payload(vararg p:Pair<String,Any?>)=session.body(*p)
  val w=repo.mutate("POST","workspaces",payload("name" to "3DDK • Bảng dùng thử"))
  val base=repo.mutate("POST","workspaces/${w.text("id")}/bases",payload("name" to "Dự án đầu tiên"))
  val table=repo.mutate("POST","bases/${base.text("id")}/tables",payload("name" to "Công việc của nhóm"));val path="tables/${table.text("id")}"
  val priority=repo.mutate("POST","$path/fields",payload("name" to "Trạng thái","type" to "SELECT","options" to listOf("Chưa làm","Đang làm","Xong")))
  val done=repo.mutate("POST","$path/fields",payload("name" to "Hoàn thành","type" to "CHECKBOX"))
  repo.refresh(path)
  val snapshot=session.gson.fromJson(session.dao.entry(repo.uid,"v2",path)!!.json,JsonObject::class.java)
  val title=snapshot.objects("fields").first().text("id")
  val cells=JsonObject().apply{addProperty(title,"Thử bảng 3DDK");addProperty(priority.text("id"),"Đang làm");addProperty(done.text("id"),false)}
  val row=repo.mutate("POST","$path/records",payload("cells" to cells))
  cells.addProperty(title,"Bảng đã kết nối thành công")
  val edited=repo.mutate("PATCH","$path/records/${row.text("id")}",payload("expectedVersion" to row.get("version").asInt,"cells" to cells))
  assertEquals(2,edited.get("version").asInt)
  repo.refresh(path)
  assertTrue(session.dao.entry(repo.uid,"v2",path)!!.json.contains("Bảng đã kết nối thành công"))
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
