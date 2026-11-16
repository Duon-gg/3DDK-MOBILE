package com.threeddk.tasks.model
import com.google.gson.JsonObject
fun JsonObject.text(key:String)=get(key)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
fun JsonObject.objects(key:String)=getAsJsonArray(key)?.map{it.asJsonObject}.orEmpty()
