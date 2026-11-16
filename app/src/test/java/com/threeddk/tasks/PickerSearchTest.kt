package com.threeddk.tasks

import com.threeddk.tasks.ui.matchingPickerIndices
import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser

class PickerSearchTest {
 @Test fun filteredSelectionRetainsOriginalViewIndex(){
  val labels=listOf("Tất cả dòng","Grid view","Dev","Test","Test")
  assertEquals(listOf(3,4),matchingPickerIndices(labels," TEST "))
  assertEquals(listOf(0,1,2,3,4),matchingPickerIndices(labels,""))
  assertTrue(matchingPickerIndices(labels,"missing").isEmpty())
 }
 @Test fun openingAnotherTablesViewAppliesItsSavedQuery(){
  val current=JsonParser.parseString("""{"limit":50}""").asJsonObject
  val metadata=JsonParser.parseString("""{"schemaVersion":7,"views":[{"id":"view-b","version":3,"filter":{"op":"and","conditions":[]},"sort":[{"fieldId":"priority","direction":"asc"}],"fieldIds":["title","priority"],"fieldOrder":["priority","title"]}]}""").asJsonObject
  val result=refreshedViewQuery(current,metadata,"view-b")
  assertEquals("view-b",result["viewId"].asString)
  assertEquals(3,result["viewVersion"].asInt)
  assertEquals(metadata.getAsJsonArray("views")[0].asJsonObject["fieldOrder"],result["fieldOrder"])
  assertEquals(7,result["expectedSchemaVersion"].asInt)
  assertFalse(current.has("viewId"))
 }
}
