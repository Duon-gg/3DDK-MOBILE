package com.threeddk.tasks

import com.google.gson.JsonParser
import com.threeddk.tasks.data.csvCell
import com.threeddk.tasks.data.inferFieldType
import com.threeddk.tasks.data.parseCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImportTest {
 @Test fun parsesQuotesNewlinesAndBom(){
  val rows=parseCsv("﻿Tên,Chi tiết\r\n\"A, b\",\"dòng 1\ndòng \"\"2\"\"\"\r\n\r\nC,\n")
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
}
