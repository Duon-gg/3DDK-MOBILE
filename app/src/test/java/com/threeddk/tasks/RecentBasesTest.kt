package com.threeddk.tasks

import com.threeddk.tasks.data.*
import org.junit.Assert.*
import org.junit.Test

class RecentBasesTest {
 private fun row(kind:String,id:String,json:String,owner:String="a")=CacheEntry(owner,kind,id,json)
 private val base=row("v2","bases/b","""{"name":"Project"}""")
 @Test fun twoLegacyTablesBecomeOneStarredBaseWithLatestVisit(){
  val result=recentBases(listOf(base,
   row("v2-home","tables/1","""{"basePath":"bases/b","opened":10,"starred":true}"""),
   row("v2-home","tables/2","""{"basePath":"bases/b","opened":20,"starred":false}""")),"a")
  assertEquals(1,result.size);assertEquals(20L,result.single().opened);assertTrue(result.single().starred)
 }
 @Test fun explicitUnstarOverridesLegacyStarsAndOtherAccountsAreExcluded(){
  val result=recentBases(listOf(base,
   row("v2-home","tables/1","""{"basePath":"bases/b","opened":10,"starred":true}"""),
   row("v2-base-star","bases/b","""{"starred":false}"""),
   row("v2-recent-base","bases/b","""{"opened":999}""","other")),"a")
  assertFalse(result.single().starred);assertEquals(10L,result.single().opened)
 }
 @Test fun missingBaseIsNotExposed(){
  assertTrue(recentBases(listOf(row("v2-home","tables/1","""{"basePath":"bases/b","opened":10,"starred":true}""")),"a").isEmpty())
 }
 @Test fun workspaceComesFromCachedBaseForTheCurrentAccount(){
  val entries=listOf(
   row("v2","bases/b","""{"name":"Project","workspaceId":"workspace-a"}"""),
   row("v2-recent-base","bases/b","""{"opened":20,"workspaceId":"stale-workspace"}"""),
   row("v2","bases/b","""{"name":"Other project","workspaceId":"workspace-other"}""","other"),
   row("v2-recent-base","bases/b","""{"opened":99}""","other"))
  val mine=recentBases(entries,"a").single()
  assertEquals("workspace-a",mine.workspaceId)
  assertEquals("Project",mine.name)
  assertEquals(20L,mine.opened)
  assertEquals("workspace-other",recentBases(entries,"other").single().workspaceId)
 }
 @Test fun legacyVisitUsesCachedWorkspaceAndMissingWorkspaceDefaultsToEmpty(){
  val visit=row("v2-home","tables/1","""{"basePath":"bases/b","opened":10}""")
  val withWorkspace=row("v2","bases/b","""{"name":"Project","workspaceId":"workspace-a"}""")
  assertEquals("workspace-a",recentBases(listOf(withWorkspace,visit),"a").single().workspaceId)
  assertEquals("",recentBases(listOf(base,visit),"a").single().workspaceId)
 }
}
