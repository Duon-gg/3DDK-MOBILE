package com.threeddk.tasks

import android.widget.EditText
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
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
    eventually{
     if(name.startsWith("Công việc QA")){
      onView(withId(R.id.view_choice)).check(matches(withText(org.hamcrest.Matchers.startsWith("$name / "))))
     }else onView(withId(R.id.heading)).check(matches(withText(name)))
    }
   }
   createAndOpen("3DDK • Kiểm thử P0 ${java.util.UUID.randomUUID().toString().take(8)}")
   createAndOpen("Dự án kiểm thử")
   val tableName="Công việc QA ${java.util.UUID.randomUUID().toString().take(8)}"
   createAndOpen(tableName)
   onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))
   var path=""
   scenario.onActivity{activity->
    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    assertEquals(R.id.dataTable,nav.currentDestination?.id)
    path=nav.currentBackStackEntry?.arguments?.getString("path").orEmpty()
   }
   assertTrue(path.startsWith("tables/"))
   val metadata=session.workspaces.metadata(path)
   val schemaVersion=metadata["schemaVersion"].asInt
   val titleField=metadata.objects("fields").first().text("id")
   eventually{onView(withId(R.id.add)).check(matches(isEnabled()))}
   onView(withId(R.id.add)).perform(click())
   val title=allOf(isAssignableFrom(EditText::class.java),withHint("Tên"))
   eventually{onView(title).check(matches(isDisplayed()))}
   onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))
   val wrappedTitle="Nhập trực tiếp trên điện thoại để kiểm tra đường kẻ của hàng có nội dung dài"
   onView(title).perform(replaceText(wrappedTitle),closeSoftKeyboard())
   var draftKey=""
   eventually{
    val draft=runBlocking{session.dao.observeDrafts(session.uid!!).first()}.firstOrNull{it.taskId.startsWith("v2/$path/new/")&&it.description.contains("Nhập trực tiếp")}
    assertNotNull("A new record must have its own UUID draft key",draft);draftKey=draft!!.taskId
   }
   scenario.recreate()
   eventually{onView(title).check(matches(withText(wrappedTitle)))}
   onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))
   onView(withId(R.id.save)).perform(click())
   eventually{onView(withId(R.id.view_choice)).check(matches(withText(org.hamcrest.Matchers.startsWith("$tableName / "))));onView(withId(R.id.add)).check(matches(isEnabled()))}
   val rows=session.workspaces.query(path,JsonObject().apply{addProperty("expectedSchemaVersion",schemaVersion);addProperty("limit",50)}).objects("items")
   assertTrue(rows.any{it.toString().contains(wrappedTitle)})
   assertNull(session.dao.draft(session.uid!!,draftKey))
   val first=rows.single()
   val otherTitle="Dòng đối chiếu"
   session.workspaces.mutate("POST","$path/records",JsonObject().apply{
    addProperty("expectedSchemaVersion",schemaVersion)
    addProperty("requestId",java.util.UUID.randomUUID().toString())
    add("cells",JsonObject().apply{addProperty(titleField,otherTitle)})
   })
   eventually{
    val star=onView(withText(R.string.home_star))
    try{star.check(matches(isDisplayed()))}
    catch(_:androidx.test.espresso.NoMatchingViewException){openActionBarOverflowOrOptionsMenu(app)}
    star.perform(click())
   }
   eventually{
    assertTrue(runBlocking{session.workspaces.observeRecentBases(session.uid!!).first()}.any{it.name=="Dự án kiểm thử"&&it.starred})
   }
   scenario.onActivity{activity->
    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    assertTrue(nav.popBackStack(R.id.workspaces,false))
   }
   eventually{onView(withId(R.id.starred_tab)).check(matches(isDisplayed()))}
   onView(withId(R.id.starred_tab)).perform(click())
   eventually{onView(withText("Dự án kiểm thử")).check(matches(isDisplayed()))}
   onView(withText("Dự án kiểm thử")).perform(click())
   eventually{onView(withText(tableName)).check(matches(isDisplayed()))}
   onView(withText(tableName)).perform(click())
   eventually{onView(withText(otherTitle)).check(matches(isDisplayed()))}
   onView(withText(wrappedTitle)).check { view, error ->
    if(error!=null) throw error
    val cell=view as android.widget.TextView
    val record=cell.parent as android.widget.LinearLayout
    assertEquals("Primary field is displayed above the cells",0,record.indexOfChild(cell))
    val row=record.getChildAt(1) as android.widget.LinearLayout
    for(index in 0 until row.childCount){
     assertEquals("Every cell must meet the row border",row.height,row.getChildAt(index).bottom)
    }
   }.perform(click())
   eventually{onView(title).check(matches(withText(wrappedTitle)))}
   onView(withContentDescription(R.string.record_close)).check(matches(isDisplayed()))
   onView(title).check { view, error ->
    if(error!=null) throw error
    assertNull("Record title should render without an input box",view.background)
   }
   val detail=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(app.getExternalFilesDir(null),"record-detail-preview.png").outputStream().use{
    detail.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
   }
   val editedTitle="Bản nháp sau khi chuyển dòng"
   onView(title).perform(replaceText(editedTitle),closeSoftKeyboard())
   val nextEnabled=runCatching{onView(withId(R.id.next_row)).check(matches(isEnabled()))}.isSuccess
   val away=if(nextEnabled)R.id.next_row else R.id.previous_row
   val back=if(nextEnabled)R.id.previous_row else R.id.next_row
   onView(withId(away)).perform(click())
   eventually{onView(title).check(matches(withText(otherTitle)))}
   onView(withId(back)).perform(click())
   eventually{onView(title).check(matches(withText(editedTitle)))}
   val recordId=first.text("id")
   assertEquals(wrappedTitle,session.workspaces.record(path,recordId).getAsJsonObject("cells").text(titleField))
   onView(withId(R.id.save)).perform(click())
   eventually{onView(withId(R.id.view_choice)).check(matches(withText(org.hamcrest.Matchers.startsWith("$tableName / "))))}
   val updated=session.workspaces.record(path,recordId)
   assertEquals(editedTitle,updated.getAsJsonObject("cells").text(titleField))
   assertTrue(updated["version"].asInt>first["version"].asInt)
   assertNull(session.dao.draft(session.uid!!,"v2/$path/$recordId"))
   val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(app.getExternalFilesDir(null),"workspace-preview.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  Unit
 }
}
