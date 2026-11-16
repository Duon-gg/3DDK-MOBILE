package com.threeddk.tasks
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.threeddk.tasks.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class OfflineDatabaseTest {
 @Test fun pendingCreatePayloadSurvivesDatabaseReopen()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val name="pending-${java.util.UUID.randomUUID()}.db"
  var db=Room.databaseBuilder(context,TaskDatabase::class.java,name).build()
  try{
   val payload="{\"requestId\":\"request-X\",\"expectedSchemaVersion\":1,\"cells\":{\"title\":\"First attempt\"}}"
   val draft=Draft("alice","v2/tables/t/new/local","{\"title\":\"First attempt\"}",requestId="request-X",pendingCreateJson=payload)
   db.dao().save(draft);db.close()
   db=Room.databaseBuilder(context,TaskDatabase::class.java,name).build()
   val reopened=db.dao().draft("alice",draft.taskId)!!
   assertEquals(payload,reopened.pendingCreateJson);assertEquals(draft,reopened)
  }finally{db.close();context.deleteDatabase(name)}
 }
 @Test fun migrationPreservesLegacyAndIndependentNewDrafts()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val name="migration-${java.util.UUID.randomUUID()}.db"
  context.openOrCreateDatabase(name,android.content.Context.MODE_PRIVATE,null).use{legacy->
   legacy.execSQL("CREATE TABLE cache (owner TEXT NOT NULL,kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(owner,kind,id))")
   legacy.execSQL("CREATE TABLE drafts (owner TEXT NOT NULL,taskId TEXT NOT NULL,description TEXT NOT NULL,link TEXT NOT NULL,requestId TEXT NOT NULL,expectedVersion INTEGER NOT NULL,PRIMARY KEY(owner,taskId))")
   legacy.execSQL("INSERT INTO cache VALUES ('alice','v2','tables/t','{}')")
   legacy.execSQL("INSERT INTO drafts VALUES ('alice','v2/tables/t/new','legacy draft','','request-old',3)")
   legacy.version=1
  }
  val db=Room.databaseBuilder(context,TaskDatabase::class.java,name).addMigrations(TaskDatabase.MIGRATION_1_2).build()
  try{
   val legacy=db.dao().draft("alice","v2/tables/t/new")!!
   assertEquals("legacy draft",legacy.description);assertEquals("request-old",legacy.requestId);assertEquals(3,legacy.expectedVersion)
   assertEquals("",legacy.baselineJson);assertEquals(1,legacy.expectedSchemaVersion);assertEquals("",legacy.pendingCreateJson)
   assertNotNull(db.dao().entry("alice","v2","tables/t"))
   db.dao().save(legacy.copy(taskId="v2/tables/t/new/one",description="first",baselineJson="{}",pendingCreateJson="{\"requestId\":\"pending-X\"}"))
   db.dao().save(legacy.copy(taskId="v2/tables/t/new/two",description="second",baselineJson="{}"))
   assertEquals("first",db.dao().draft("alice","v2/tables/t/new/one")!!.description)
   assertEquals("{\"requestId\":\"pending-X\"}",db.dao().draft("alice","v2/tables/t/new/one")!!.pendingCreateJson)
   assertEquals("second",db.dao().draft("alice","v2/tables/t/new/two")!!.description)
   db.dao().save(Draft("bob","draft","private"));db.dao().clearOwnerDrafts("alice")
   assertEquals("private",db.dao().draft("bob","draft")!!.description)
  }finally{db.close();context.deleteDatabase(name)}
 }
 @Test fun expiredAuthenticationRetainsDraftUntilExplicitRevocation()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TaskDatabase::class.java).build()
  try {
   val draft=Draft("alice","task","Chưa gửi")
   db.dao().save(draft);db.dao().put(listOf(CacheEntry("alice","me","alice","{}")))
   db.handleAccessFailure("UNAUTHORIZED")
   assertEquals(draft,db.dao().draft("alice","task"));assertNull(db.dao().entry("alice","me","alice"))
   db.handleAccessFailure("FORBIDDEN")
   assertNull(db.dao().draft("alice","task"))
  } finally{db.close()}
 }
 @Test fun draftsSurviveReopenAndAreIsolatedByAccount()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val name="test-${java.util.UUID.randomUUID()}.db"
  var db=Room.databaseBuilder(context,TaskDatabase::class.java,name).build()
  try {
   val draft=Draft("alice","task","Kết quả chưa gửi","https://example.com",expectedVersion=3)
   db.dao().save(draft);db.dao().put(listOf(CacheEntry("alice","tasks","task","{}")))
   db.close();db=Room.databaseBuilder(context,TaskDatabase::class.java,name).build()
   assertEquals(draft,db.dao().draft("alice","task"));assertNull(db.dao().draft("bob","task"))
   assertTrue(db.dao().observe("bob").first().isEmpty())
   db.dao().clearKind("alice","tasks");assertEquals(draft,db.dao().draft("alice","task"))
   db.withTransaction{db.dao().clearCache();db.dao().clearDrafts()}
   assertNull(db.dao().draft("alice","task"));assertTrue(db.dao().observe("alice").first().isEmpty())
  } finally {db.close();context.deleteDatabase(name)}
 }
 @Test fun failedSnapshotDoesNotDestroyCachedTasks()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TaskDatabase::class.java).build()
  try {
   val row=CacheEntry("alice","tasks","task","{\"title\":\"cached\"}");db.dao().put(listOf(row))
   try{db.withTransaction{db.dao().clearKind("alice","tasks");throw java.io.IOException("Interrupted refresh")}}catch(_:java.io.IOException){}
   assertEquals(row,db.dao().entry("alice","tasks","task"))
  } finally{db.close()}
 }
}
