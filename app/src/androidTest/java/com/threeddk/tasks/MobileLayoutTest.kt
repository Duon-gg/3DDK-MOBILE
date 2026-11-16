package com.threeddk.tasks
import com.threeddk.tasks.model.text

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.threeddk.tasks.databinding.FragmentDataTableBinding
import com.threeddk.tasks.databinding.FragmentRecordEditorBinding
import com.threeddk.tasks.databinding.FragmentWorkspaceBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Measures browse layouts without launching Auth, opening a session or calling the API. */
@RunWith(AndroidJUnit4::class)
class MobileLayoutTest {
 @Test fun tableLeavesRoomForRecordsOnSmallPhoneWithLargeText(){
  atPhoneFontScales { context ->
   val b=FragmentDataTableBinding.inflate(LayoutInflater.from(context))
   b.viewChoice.text="Kế hoạch công việc của nhóm phát triển / Tất cả công việc"
   b.status.text="Đã cập nhật"
   b.filter.text=context.getString(R.string.p0_filter_count,0)
   b.sort.text=context.getString(R.string.p0_sort_count,0)
   b.empty.visibility=View.GONE
   assertEquals(View.GONE,b.queryActions.visibility)
   b.queryActions.visibility=View.VISIBLE
   measurePhone(b.root,context)

   assertEquals(View.VISIBLE,b.gridContainer.visibility)
   assertTrue("Records must retain a usable viewport",b.gridContainer.height>100)
   assertEquals(View.VISIBLE,b.searchToggle.visibility)
   assertEquals("Search stays collapsed while browsing",View.GONE,b.searchBox.visibility)
   listOf(b.viewChoice,b.queryToggle,b.tableList,b.add,b.refresh,b.searchToggle,b.filter,b.sort,b.columns).forEach { button ->
    assertTouchTarget(button,context)
    assertInside(b.root,button)
   }
   assertTrue("Records must begin below the query controls",b.gridContainer.top>=b.queryActions.bottom)
   assertTrue("Records must end above the bottom actions",b.gridContainer.bottom<=b.actions.top)
   assertInside(b.root,b.gridContainer)
  }
 }

 @Test fun workspaceListAndSummaryRemainReachableWithLargeText(){
  atPhoneFontScales { context ->
   val b=FragmentWorkspaceBinding.inflate(LayoutInflater.from(context))
   b.heading.text=context.getString(R.string.p0_khong_gian_cua_ban)
   b.sync.text="Đã cập nhật"
   b.summary.text=context.getString(R.string.mobile_workspaces_count,3)
   b.homeTabs.visibility=View.VISIBLE
   b.homeTabScroll.visibility=View.VISIBLE
   b.homeTabs.check(R.id.spaces_tab)
   b.empty.visibility=View.GONE
   measurePhone(b.root,context)

   assertTrue("Workspace list must retain a usable viewport",b.list.height>100)
   assertTrue("Summary must not overlap actions",b.summary.top>=b.actions.bottom)
   assertTrue("List must begin below the summary",b.list.top>=b.summary.bottom)
   listOf(b.homeSearch,b.add,b.refresh).forEach { button ->
    assertTouchTarget(button,context)
    assertInside(b.root,button)
   }
   listOf(b.recentTab,b.starredTab,b.spacesTab).forEach { button ->
    assertTouchTarget(button,context)
    b.homeTabScroll.scrollTo((button.right-b.homeTabScroll.width).coerceAtLeast(0),0)
    assertInside(b.root,button)
   }
   assertInside(b.root,b.summary)
   assertInside(b.root,b.list)
  }
 }

 @Test fun recordNavigationKeepsSaveAndEditorReachable(){
  atPhoneFontScales { context ->
   val b=FragmentRecordEditorBinding.inflate(LayoutInflater.from(context))
   b.rowNavigation.visibility=View.VISIBLE
   b.rowPosition.text=context.getString(R.string.record_position,249,500)
   measurePhone(b.root,context)
   assertTrue(b.recordScroll.height>100)
   assertTrue(b.recordScroll.bottom<=b.rowNavigation.top)
   assertTrue(b.save.left>=b.nextRow.right)
   listOf(b.previousRow,b.nextRow,b.save).forEach{button->
    assertTouchTarget(button,context)
    assertInside(b.root,button)
   }
  }
 }

 private fun atPhoneFontScales(check:(Context)->Unit){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  for(scale in listOf(1f,2f)){
   var failure:Throwable?=null
   instrumentation.runOnMainSync {
    try {
     val base=instrumentation.targetContext
     val config=Configuration(base.resources.configuration).apply{fontScale=scale}
     val context=ContextThemeWrapper(base.createConfigurationContext(config),R.style.Theme_Tasks)
     check(context)
    } catch(error:Throwable){failure=error}
   }
   failure?.let{throw AssertionError("fontScale=$scale: ${it.message}",it)}
  }
 }

 private fun dp(context:Context,value:Int)=(value*context.resources.displayMetrics.density).roundToInt()
 private fun measurePhone(root:View,context:Context){
  val width=dp(context,320);val height=dp(context,580)
  root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
  root.layout(0,0,width,height)
 }
 private fun assertTouchTarget(view:View,context:Context){
  val minimum=dp(context,48)
  assertTrue("${view.resources.getResourceEntryName(view.id)} must be at least 48dp",view.width>=minimum&&view.height>=minimum)
 }
 private fun assertInside(root:ViewGroup,view:View){
  val bounds=Rect();view.getDrawingRect(bounds);root.offsetDescendantRectToMyCoords(view,bounds)
  assertTrue("${view.resources.getResourceEntryName(view.id)} must remain inside the phone viewport",bounds.left>=root.paddingLeft&&bounds.top>=root.paddingTop&&bounds.right<=root.width-root.paddingRight&&bounds.bottom<=root.height-root.paddingBottom)
 }
}
