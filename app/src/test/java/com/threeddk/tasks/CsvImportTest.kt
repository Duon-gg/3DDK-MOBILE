package com.threeddk.tasks

import com.google.gson.JsonParser
import com.threeddk.tasks.data.csvCell
import com.threeddk.tasks.data.csvCellInvalid
import com.threeddk.tasks.data.csvBatches
import com.threeddk.tasks.data.readLimited
import com.threeddk.tasks.data.inferFieldType
import com.threeddk.tasks.data.parseCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImportTest {
 @Test fun parsesQuotesNewlinesAndBom(){
  val rows=parseCsv("\uFEFFTên,Chi tiết\r\n\"A, b\",\"dòng 1\ndòng \"\"2\"\"\"\r\n\r\nC,\n")
  assertEquals(listOf(listOf("Tên","Chi tiết"),listOf("A, b","dòng 1\ndòng \"2\""),listOf("C","")),rows)
 }
 @Test fun infersTypes(){
  assertEquals("CHECKBOX",inferFieldType(listOf("true","false","")).first)
  assertEquals("SELECT" to listOf("Chưa làm","Xong"),inferFieldType(listOf("Chưa làm","Chưa làm","Xong","Chưa làm")))
  assertEquals("TEXT",inferFieldType(listOf("a","b","c")).first)
 }
 @Test fun convertsCells(){
  val select=JsonParser.parseString("""{"type":"SELECT","options":["A"]}""").asJsonObject
  assertEquals("A",csvCell(select,"A")!!.asString)
  assertNull(csvCell(select,"B"))
  assertEquals(true,csvCell(JsonParser.parseString("""{"type":"CHECKBOX"}""").asJsonObject,"Checked")!!.asBoolean)
  assertNull(csvCell(JsonParser.parseString("""{"type":"DATE"}""").asJsonObject,"Ngày mai"))
 }
 @Test fun flagsCellsTheServerWouldReject(){
  fun f(type:String)=JsonParser.parseString("""{"type":"$type","options":["A"]}""").asJsonObject
  assertEquals(false,csvCellInvalid(f("URL"),"https://3ddk.vn/a"))
  assertEquals(true,csvCellInvalid(f("URL"),"3ddk.vn"))
  assertEquals(true,csvCellInvalid(f("EMAIL"),"a@b"))
  assertEquals(true,csvCellInvalid(f("PHONE"),"gọi tôi"))
  assertEquals(true,csvCellInvalid(f("SELECT"),"B"))
  assertEquals(false,csvCellInvalid(f("SELECT"),"  "))
  assertEquals(false,csvCellInvalid(f("USER"),"Khang"))
 }
 @Test fun refusesStreamsOverTheLimit(){
  assertEquals(3,readLimited("abc".byteInputStream(),3)!!.size)
  assertNull(readLimited(ByteArray(20_000).inputStream(),10_000))
 }
 @Test fun batchesStayUnderTheBodyLimit(){
  val rows=List(500){JsonParser.parseString("""{"f":"${"ă".repeat(100)}"}""").asJsonObject}
  val batches=csvBatches(rows)
  assertEquals(500,batches.sumOf{it.size})
  batches.forEach{b->assert(b.sumOf{it.toString().toByteArray().size+12}<=24_000)}
  assertEquals(1,csvBatches(rows.take(3)).size)
 }
}
