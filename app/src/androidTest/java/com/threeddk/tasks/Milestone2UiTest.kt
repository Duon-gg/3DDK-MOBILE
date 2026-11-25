package com.threeddk.tasks

import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.threeddk.tasks.adapter.GridAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Milestone2UiTest {
 @Test fun gridBindingDisplaysAndOpensCorrectRecord(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val context=android.view.ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_Tasks)
   val parent=FrameLayout(context);var opened=""
   val adapter=GridAdapter{opened=it}
   adapter.fields=listOf(JsonParser.parseString("""{"id":"title","name":"Tên","type":"TEXT"}""").asJsonObject)
   adapter.submitList(listOf(JsonParser.parseString("""{"id":"row-a","cells":{"title":"Nội dung xuống dòng\nDòng thứ hai"}}""").asJsonObject))
   val holder=adapter.onCreateViewHolder(parent,0);adapter.onBindViewHolder(holder,0)
   assertEquals(1,holder.binding.cells.childCount)
   assertTrue((holder.binding.cells.getChildAt(0) as android.widget.TextView).text.contains("Dòng thứ hai"))
   holder.binding.root.performClick();assertEquals("row-a",opened)
  }
 }
}
