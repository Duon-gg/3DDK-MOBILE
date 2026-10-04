package com.threeddk.tasks

import android.os.Bundle
import android.widget.EditText
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.navigation.fragment.NavHostFragment
import com.threeddk.tasks.data.*
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditLiveTest {
 private fun eventually(assertion:()->Unit){var last:Throwable?=null;repeat(40){try{assertion();return}catch(e:Throwable){last=e;android.os.SystemClock.sleep(250)}};throw last!!}
 @Test fun editDraftRotateAndSubmitFromPhone():Unit=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkspace")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>();val session=app.repository;val api=session.workspaceApi
  assertTrue(session.auth?.currentUser?.isEmailVerified==true)
  val w=api.call("GET","workspaces").asJsonArray.map{it.asJsonObject}.first{it.text("name")=="3DDK • Bảng dùng thử"}
  val base=api.call("GET","workspaces/${w.text("id")}").asJsonObject.objects("bases").first()
  val table=api.call("GET","bases/${base.text("id")}").asJsonObject.objects("tables").first();val path="tables/${table.text("id")}"
  session.workspaces.refresh(path)
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   scenario.onActivity{activity->(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(R.id.dataTable,Bundle().apply{putString("path",path)})}
   eventually{onView(withId(R.id.add)).check(matches(isEnabled()))}
   onView(withId(R.id.add)).perform(click())
   val title=allOf(isAssignableFrom(EditText::class.java),withHint("Tên"))
   eventually{onView(title).check(matches(isDisplayed()))}
   onView(title).perform(replaceText("Nhập trực tiếp trên điện thoại"),closeSoftKeyboard())
   eventually{assertTrue(runBlocking{session.dao.draft(session.uid!!,"v2/$path/new")}?.description?.contains("Nhập trực tiếp") == true)}
   scenario.recreate()
   eventually{onView(title).check(matches(withText("Nhập trực tiếp trên điện thoại")))}
   onView(withId(R.id.save)).perform(click())
   eventually{onView(withId(R.id.heading)).check(matches(withText("Công việc của nhóm")));onView(withId(R.id.add)).check(matches(isEnabled()))}
   val rows=api.call("GET",path).asJsonObject.objects("records")
   assertTrue(rows.any{it.toString().contains("Nhập trực tiếp trên điện thoại")})
   assertNull(session.dao.draft(session.uid!!,"v2/$path/new"))
   val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(app.getExternalFilesDir(null),"workspace-preview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  Unit
 }
}
