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
