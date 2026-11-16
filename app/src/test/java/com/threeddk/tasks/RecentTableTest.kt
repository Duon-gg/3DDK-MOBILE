package com.threeddk.tasks
import com.google.gson.JsonObject
import com.threeddk.tasks.data.*
import org.junit.Assert.*
import org.junit.Test

class RecentTableTest{
 private val tables=listOf("a","b").map{JsonObject().apply{addProperty("id",it)}}
 private fun visit(id:String,time:Long,owner:String="u",base:String="bases/base")=CacheEntry(owner,"v2-home","tables/$id","""{"basePath":"$base","opened":$time}""")
 @Test fun selectsLatestAvailableTableForSameAccountAndBase(){
  val entries=listOf(visit("a",1),visit("b",2),visit("deleted",10),visit("a",20,"other"),visit("a",30,base="bases/other"))
  assertEquals("b",mostRecentTable("bases/base",tables,entries,"u"))
 }
 @Test fun firstVisitOrRemovedTableFallsBackAndEmptyBaseStaysEmpty(){
  assertEquals("a",mostRecentTable("bases/base",tables,listOf(visit("deleted",10)),"u"))
  assertEquals("a",mostRecentTable("bases/base",tables,emptyList(),"u"))
  assertNull(mostRecentTable("bases/base",emptyList(),listOf(visit("a",1)),"u"))
 }
}
