package com.threeddk.tasks.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
@Entity(tableName="cache",primaryKeys=["owner","kind","id"])
data class CacheEntry(val owner:String,val kind:String,val id:String,val json:String)
@Entity(tableName="drafts",primaryKeys=["owner","taskId"])
data class Draft(val owner:String,val taskId:String,val description:String="",val link:String="",val requestId:String=java.util.UUID.randomUUID().toString(),val expectedVersion:Int=1,@ColumnInfo(defaultValue="''") val baselineJson:String="",@ColumnInfo(defaultValue="1") val expectedSchemaVersion:Int=1,@ColumnInfo(defaultValue="''") val pendingCreateJson:String="")
@Dao interface TaskDao {
 @Query("SELECT * FROM cache WHERE owner=:owner") fun observe(owner:String):Flow<List<CacheEntry>>
 @Query("SELECT * FROM cache WHERE owner=:owner") suspend fun entries(owner:String):List<CacheEntry>
 @Query("SELECT * FROM cache WHERE owner=:owner AND kind=:kind AND id=:id LIMIT 1") suspend fun entry(owner:String,kind:String,id:String):CacheEntry?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(entries:List<CacheEntry>)
 @Delete suspend fun delete(entries:List<CacheEntry>)
 @Query("DELETE FROM cache WHERE owner=:owner AND kind=:kind") suspend fun clearKind(owner:String,kind:String)
 @Query("SELECT * FROM drafts WHERE owner=:owner AND taskId=:taskId") suspend fun draft(owner:String,taskId:String):Draft?
 @Query("SELECT count(*) FROM drafts WHERE owner=:owner AND (description<>'' OR link<>'')") suspend fun draftCount(owner:String):Int
 @Query("SELECT * FROM drafts WHERE owner=:owner AND (description<>'' OR link<>'')") fun observeDrafts(owner:String):Flow<List<Draft>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(draft:Draft)
 @Query("DELETE FROM drafts WHERE owner=:owner AND taskId=:taskId") suspend fun deleteDraft(owner:String,taskId:String)
 @Query("DELETE FROM drafts") suspend fun clearDrafts()
 @Query("DELETE FROM cache") suspend fun clearCache()
 @Query("DELETE FROM drafts WHERE owner=:owner") suspend fun clearOwnerDrafts(owner:String)
 @Query("DELETE FROM cache WHERE owner=:owner") suspend fun clearOwnerCache(owner:String)
}
@Database(entities=[CacheEntry::class,Draft::class],version=2,exportSchema=true)
abstract class TaskDatabase:RoomDatabase() {
 abstract fun dao():TaskDao
 suspend fun handleAccessFailure(code:String,owner:String?=null){
  if(code in listOf("NOT_INVITED","FORBIDDEN","UNVERIFIED","UNAUTHORIZED"))withTransaction{
   if(owner==null)dao().clearCache() else dao().clearOwnerCache(owner)
   if(code!="UNAUTHORIZED"){if(owner==null)dao().clearDrafts() else dao().clearOwnerDrafts(owner)}
  }
 }
 companion object {
  @Volatile private var instance:TaskDatabase?=null
  val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("ALTER TABLE drafts ADD COLUMN baselineJson TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE drafts ADD COLUMN expectedSchemaVersion INTEGER NOT NULL DEFAULT 1")
   db.execSQL("ALTER TABLE drafts ADD COLUMN pendingCreateJson TEXT NOT NULL DEFAULT ''")
  }}
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,TaskDatabase::class.java,"3ddk-tasks.db").addMigrations(MIGRATION_1_2).build().also{instance=it}}
 }
}
