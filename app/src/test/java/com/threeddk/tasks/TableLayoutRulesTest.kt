package com.threeddk.tasks

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.threeddk.tasks.viewmodel.columnSummary
import com.threeddk.tasks.viewmodel.groupRows
import com.threeddk.tasks.viewmodel.kanbanColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class TableLayoutRulesTest {
 private fun row(id:String,cells:String)=JsonParser.parseString("""{"id":"$id","cells":$cells}""").asJsonObject
 private fun field(type:String,options:String="[]")=JsonParser.parseString("""{"id":"f","type":"$type","options":$options}""").asJsonObject
 private val rows=listOf(row("1","""{"f":"B"}"""),row("2","{}"),row("3","""{"f":"A"}"""),row("4","""{"f":"B"}"""))

 @Test fun selectGroupsFollowOptionOrderEmptyLast(){
  val groups=groupRows(rows,field("SELECT","""["B","A"]"""))
  assertEquals(listOf("B","A",null),groups.map{it.first?.asString})
  assertEquals(listOf("1","4"),groups[0].second.map{it["id"].asString})
 }
 @Test fun summariesPerType(){
  val nums=listOf(row("1","""{"f":1.5}"""),row("2","""{"f":2}"""),row("3","{}"))
  assertEquals("3.5",columnSummary(nums,field("NUMBER")).value)
  val checks=listOf(row("1","""{"f":true}"""),row("2","""{"f":false}"""),row("3","{}"),row("4","""{"f":true}"""))
  assertEquals("50",columnSummary(checks,field("CHECKBOX")).value)
  assertEquals("3",columnSummary(rows,field("TEXT")).value)
 }
 @Test fun kanbanHasEveryOptionAndEmptyColumn(){
  val columns=kanbanColumns(rows,field("SELECT","""["A","B","C"]"""))
  assertEquals(listOf(null,"A","B","C"),columns.map{it.first})
  assertEquals(listOf(1,1,2,0),columns.map{it.second.size})
 }
}
