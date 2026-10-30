package com.threeddk.tasks

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
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditLiveTest {
 private fun eventually(assertion:()->Unit){var last:Throwable?=null;repeat(180){try{assertion();return}catch(e:AssertionError){last=e}catch(e:Exception){last=e};android.os.SystemClock.sleep(250)};throw last!!}
 @Test fun editDraftRotateAndSubmitFromPhone():Unit=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkspace")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>();val session=app.repository
  assertTrue("Đăng nhập và xác minh email trước khi chạy",session.auth?.currentUser?.isEmailVerified==true)
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   eventually{onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))}
   scenario.onActivity{activity->(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(R.id.workspaces)}
   fun createAndOpen(name:String){
    eventually{onView(withId(R.id.add)).check(matches(isEnabled()))}
    onView(withId(R.id.add)).perform(click())
    onView(withId(R.id.value)).perform(replaceText(name),closeSoftKeyboard())
    onView(withText(R.string.p0_tao)).perform(click())
    eventually{onView(withId(R.id.heading)).check(matches(withText(name)))}
   }
   createAndOpen("3DDK • Kiểm thử P0 ${java.util.UUID.randomUUID().toString().take(8)}")
   createAndOpen("Dự án kiểm thử")
   createAndOpen("Công việc của nhóm")
   var path=""
   scenario.onActivity{activity->
    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    assertEquals(R.id.dataTable,nav.currentDestination?.id)
    path=nav.currentBackStackEntry?.arguments?.getString("path").orEmpty()
   }
   assertTrue(path.startsWith("tables/"))
   val schemaVersion=session.workspaces.metadata(path)["schemaVersion"].asInt
   eventually{onView(withId(R.id.add)).check(matches(isEnabled()))}
   onView(withId(R.id.add)).perform(click())
   val title=allOf(isAssignableFrom(EditText::class.java),withHint("Tên"))
   eventually{onView(title).check(matches(isDisplayed()))}
   onView(title).perform(replaceText("Nhập trực tiếp trên điện thoại"),closeSoftKeyboard())
   var draftKey=""
   eventually{
    val draft=runBlocking{session.dao.observeDrafts(session.uid!!).first()}.firstOrNull{it.taskId.startsWith("v2/$path/new/")&&it.description.contains("Nhập trực tiếp")}
    assertNotNull("A new record must have its own UUID draft key",draft);draftKey=draft!!.taskId
   }
   scenario.recreate()
   eventually{onView(title).check(matches(withText("Nhập trực tiếp trên điện thoại")))}
   onView(withId(R.id.save)).perform(click())
   eventually{onView(withId(R.id.heading)).check(matches(withText("Công việc của nhóm")));onView(withId(R.id.add)).check(matches(isEnabled()))}
   val rows=session.workspaces.query(path,JsonObject().apply{addProperty("expectedSchemaVersion",schemaVersion);addProperty("limit",50)}).objects("items")
   assertTrue(rows.any{it.toString().contains("Nhập trực tiếp trên điện thoại")})
   assertNull(session.dao.draft(session.uid!!,draftKey))
   val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(app.getExternalFilesDir(null),"workspace-preview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  Unit
 }
}
