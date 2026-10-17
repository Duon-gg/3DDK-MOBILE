package com.threeddk.tasks
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class TypedCellsTest {
 private fun json(s:String)=JsonParser.parseString(s).asJsonObject
 @Test fun numbersAreNumbersAndEmptyValuesAreNull(){
  val fields=listOf(json("""{"id":"n","name":"Số","type":"NUMBER"}"""),json("""{"id":"d","name":"Ngày","type":"DATE"}"""))
  val result=typedCells(fields,json("""{"n":"4.25","d":""}"""));assertEquals("4.25",result["n"].asString);assertTrue(result["n"].asJsonPrimitive.isNumber);assertTrue(result["d"].isJsonNull)
 }
 @Test fun invalidDateAndChoiceAreRejected(){
  for((type,value) in listOf("DATE" to "2026-02-30","NUMBER" to "abc","SELECT" to "Khác")){
   try{typedCells(listOf(json("""{"id":"f","name":"Cột","type":"$type","options":["Xong"]}""")),json("""{"f":"$value"}"""));fail("Expected invalid $type")}catch(expected:IllegalArgumentException){assertTrue(expected.message!!.contains("Cột"))}
  }
 }
}
