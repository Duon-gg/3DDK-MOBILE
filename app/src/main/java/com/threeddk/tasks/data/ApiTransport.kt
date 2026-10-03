package com.threeddk.tasks.data
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
class ApiException(val code:String):Exception(code)
private interface Rest {
 @GET suspend fun get(@Url path:String,@Header("Authorization") token:String):JsonElement
 @POST suspend fun post(@Url path:String,@Header("Authorization") token:String,@Body body:JsonObject):JsonElement
 @PATCH suspend fun patch(@Url path:String,@Header("Authorization") token:String,@Body body:JsonObject):JsonElement
 @PUT suspend fun put(@Url path:String,@Header("Authorization") token:String,@Body body:JsonObject):JsonElement
 @HTTP(method="DELETE",hasBody=true) suspend fun delete(@Url path:String,@Header("Authorization") token:String,@Body body:JsonObject):JsonElement
}
class ApiTransport(url:String,private val token:suspend(Boolean)->String) {
 private val rest=Retrofit.Builder().baseUrl(url).client(OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()).addConverterFactory(GsonConverterFactory.create()).build().create(Rest::class.java)
 suspend fun call(method:String,path:String,body:JsonObject=JsonObject()):JsonElement {
  for(attempt in 0..1) {
   val auth="Bearer ${token(attempt==1)}"
   try {
    return when(method) {"GET"->rest.get(path,auth);"POST"->rest.post(path,auth,body);"PATCH"->rest.patch(path,auth,body);"PUT"->rest.put(path,auth,body);"DELETE"->rest.delete(path,auth,body);else->throw IllegalArgumentException("HTTP method")}
   } catch(e:HttpException) {
    if(e.code()==401&&attempt==0) continue
    val code=runCatching {JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject["code"].asString}.getOrDefault("SERVER_ERROR")
    throw ApiException(code)
   }
  }
  throw ApiException("UNAUTHORIZED")
 }
}
