package com.threeddk.tasks.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun mostRecentTable(basePath:String,tables:List<JsonObject>,entries:List<CacheEntry>,owner:String):String?{
 val available=tables.map{it.text("id")}.toSet()
 return entries.asSequence().filter{it.owner==owner&&it.kind=="v2-home"}.mapNotNull{entry->
  val saved=runCatching{JsonParser.parseString(entry.json).asJsonObject}.getOrNull()?:return@mapNotNull null
  val id=entry.id.removePrefix("tables/")
  if(saved.text("basePath")!=basePath||id !in available)return@mapNotNull null
  id to (saved["opened"]?.asLong?:0L)
 }.maxByOrNull{it.second}?.first?:tables.firstOrNull()?.text("id")
}
