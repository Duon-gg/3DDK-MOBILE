package com.threeddk.tasks.data
import com.threeddk.tasks.model.text
import com.threeddk.tasks.repository.WorkspaceRepository

import com.google.gson.JsonParser

internal fun recentBases(entries:List<CacheEntry>,owner:String):List<WorkspaceRepository.RecentBase> {
 val rows=entries.filter{it.owner==owner}
 val bases=rows.filter{it.kind=="v2"&&it.id.startsWith("bases/")}.associateBy{it.id}
 val explicitStars=rows.filter{it.kind=="v2-base-star"}.associate{it.id to JsonParser.parseString(it.json).asJsonObject["starred"].asBoolean}
 return rows.filter{it.kind=="v2-home"||it.kind=="v2-recent-base"}.mapNotNull{entry->
  val saved=JsonParser.parseString(entry.json).asJsonObject
  val path=if(entry.kind=="v2-recent-base")entry.id else saved.text("basePath")
  val base=bases[path]?:return@mapNotNull null
  val metadata=JsonParser.parseString(base.json).asJsonObject
  val name=metadata.text("name")
  if(name.isBlank())return@mapNotNull null
  WorkspaceRepository.RecentBase(path,name,saved["opened"].asLong,saved["starred"]?.asBoolean?:false,metadata.text("workspaceId"))
 }.groupBy{it.path}.map{(path,visits)->
  visits.maxBy{it.opened}.copy(starred=explicitStars[path]?:visits.any{it.starred})
 }.sortedByDescending{it.opened}
}
