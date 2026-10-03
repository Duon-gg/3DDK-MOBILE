package com.threeddk.tasks.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName="cache",primaryKeys=["owner","kind","id"])
data class CacheEntry(val owner:String,val kind:String,val id:String,val json:String)
@Entity(tableName="drafts",primaryKeys=["owner","taskId"])
data class Draft(val owner:String,val taskId:String,val description:String="",val link:String="",val requestId:String=java.util.UUID.randomUUID().toString(),val expectedVersion:Int=1)
@Dao interface TaskDao {
 @Query("SELECT * FROM cache WHERE owner=:owner") fun observe(owner:String):Flow<List<CacheEntry>>
 @Query("SELECT * FROM cache WHERE owner=:owner AND kind=:kind AND id=:id LIMIT 1") suspend fun entry(owner:String,kind:String,id:String):CacheEntry?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(entries:List<CacheEntry>)
 @Query("DELETE FROM cache WHERE owner=:owner AND kind=:kind") suspend fun clearKind(owner:String,kind:String)
 @Query("SELECT * FROM drafts WHERE owner=:owner AND taskId=:taskId") suspend fun draft(owner:String,taskId:String):Draft?
 @Query("SELECT count(*) FROM drafts WHERE owner=:owner AND (description<>'' OR link<>'')") suspend fun draftCount(owner:String):Int
 @Query("SELECT * FROM drafts WHERE owner=:owner AND (description<>'' OR link<>'')") fun observeDrafts(owner:String):Flow<List<Draft>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(draft:Draft)
 @Query("DELETE FROM drafts WHERE owner=:owner AND taskId=:taskId") suspend fun deleteDraft(owner:String,taskId:String)
 @Query("DELETE FROM drafts") suspend fun clearDrafts()
 @Query("DELETE FROM cache") suspend fun clearCache()
}
@Database(entities=[CacheEntry::class,Draft::class],version=1,exportSchema=true)
abstract class TaskDatabase:RoomDatabase() {
 abstract fun dao():TaskDao
 suspend fun handleAccessFailure(code:String){
  if(code in listOf("NOT_INVITED","FORBIDDEN","UNVERIFIED","UNAUTHORIZED"))withTransaction{
   dao().clearCache()
   if(code!="UNAUTHORIZED")dao().clearDrafts()
  }
 }
 companion object {
  @Volatile private var instance:TaskDatabase?=null
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,TaskDatabase::class.java,"3ddk-tasks.db").build().also{instance=it}}
 }
}
