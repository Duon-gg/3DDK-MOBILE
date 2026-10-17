package com.threeddk.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.*
import com.threeddk.tasks.ui.cellLabel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CellDisplayTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private fun field(type:String)=JsonObject().apply{addProperty("type",type)}
 @Test fun userCellsShowNamesAndPreserveUnknownUid(){
  val members=listOf(JsonObject().apply{addProperty("uid","user-1");addProperty("displayName","Nguyễn Dương")})
  assertEquals("Nguyễn Dương",cellLabel(context,field("USER"),JsonPrimitive("user-1"),members))
  assertEquals(context.getString(R.string.p0_missing_member,"removed-user"),cellLabel(context,field("USER"),JsonPrimitive("removed-user"),members))
 }
 @Test fun falseIsNotEmptyAndDateUsesVietnameseOrder(){
  assertEquals(context.getString(R.string.p0_cell_no),cellLabel(context,field("CHECKBOX"),JsonPrimitive(false),emptyList()))
  assertEquals(context.getString(R.string.p0_cell_empty),cellLabel(context,field("CHECKBOX"),JsonNull.INSTANCE,emptyList()))
  assertEquals("17/10/2026",cellLabel(context,field("DATE"),JsonPrimitive("2026-10-17"),emptyList()))
  assertEquals("old-invalid-date",cellLabel(context,field("DATE"),JsonPrimitive("old-invalid-date"),emptyList()))
 }
 @Test fun unexpectedStructuredCellRemainsReadable(){
  val value=JsonArray().apply{add("kept")}
  assertEquals("[\"kept\"]",cellLabel(context,field("TEXT"),value,emptyList()))
 }
}
