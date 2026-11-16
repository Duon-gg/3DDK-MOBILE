package com.threeddk.tasks
import com.threeddk.tasks.repository.cacheAfterBaseRemoval
import com.threeddk.tasks.repository.cacheAfterWorkspaceRevocation

import com.threeddk.tasks.data.CacheEntry
import org.junit.Assert.*
import org.junit.Test

class BaseCacheTest {
 @Test fun deletedWorkspaceRemovesDescendantsAndSharedListingButKeepsOtherWorkspace(){
  val rows=listOf(
   CacheEntry("a","v2","workspaces","""{"items":[{"id":"w"},{"id":"other"}]}"""),
   CacheEntry("a","v2","workspaces/w","""{"bases":[{"id":"b"}]}"""),
   CacheEntry("a","v2-workspace-star","workspaces/w","true"),
   CacheEntry("a","v2","bases/b","""{"workspaceId":"w","tables":[{"id":"t"}]}"""),
   CacheEntry("a","v2-meta","tables/t","""{"workspaceId":"w"}"""),
   CacheEntry("a","v2-home","tables/t","""{"basePath":"bases/b"}"""),
   CacheEntry("a","v2-query","tables/t|query","{}"),
   CacheEntry("a","v2","shared-bases","""{"items":[{"id":"b","workspaceId":"w"},{"id":"keep","workspaceId":"other"}]}"""),
   CacheEntry("a","v2","workspaces/other","{}"))
  val kept=cacheAfterWorkspaceRevocation(rows,"workspaces/w")
  assertEquals(setOf("workspaces","shared-bases","workspaces/other"),kept.map{it.id}.toSet())
  assertEquals("""{"items":[{"id":"other"}]}""",kept.first{it.id=="workspaces"}.json)
  assertFalse(kept.first{it.id=="shared-bases"}.json.contains("\"b\""))
 }
 @Test fun deletedBaseRemovesItsSnapshotsWithoutRemovingSibling(){
  val rows=listOf(
   CacheEntry("a","v2","workspaces/w","""{"bases":[{"id":"b"},{"id":"sibling"}]}"""),
   CacheEntry("a","v2","bases/b","""{"tables":[{"id":"t"}]}"""),
   CacheEntry("a","v2-base-star","bases/b","""{"starred":true}"""),
   CacheEntry("a","v2-meta","tables/t","{}"),
   CacheEntry("a","v2-query","tables/t|query","{}"),
   CacheEntry("a","v2-record","tables/t/record","{}"),
   CacheEntry("a","v2","bases/sibling","{}"))
  val kept=cacheAfterBaseRemoval(rows,"bases/b")
  assertEquals(setOf("workspaces/w","bases/sibling"),kept.map{it.id}.toSet())
  assertEquals("""{"bases":[{"id":"sibling"}]}""",kept.first().json)
 }
 @Test fun homeLinkInvalidatesTableEvenWithoutBaseSnapshot(){
  val rows=listOf(CacheEntry("a","v2-home","tables/t","""{"basePath":"bases/b"}"""),CacheEntry("a","v2-record","tables/t/r","{}"))
  assertTrue(cacheAfterBaseRemoval(rows,"bases/b").isEmpty())
 }
}
