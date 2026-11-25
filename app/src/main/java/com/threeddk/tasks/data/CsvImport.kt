package com.threeddk.tasks.data

import com.google.gson.JsonObject

/** RFC 4180 CSV: quoted fields, doubled quotes, CRLF/LF, newlines inside quotes, leading BOM. */
fun parseCsv(text:String):List<List<String>>{
 val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val cell=StringBuilder()
 var quoted=false;var i=if(text.startsWith('\uFEFF'))1 else 0
 while(i<text.length){
  val c=text[i]
  if(quoted){
   if(c=='"'&&text.getOrNull(i+1)=='"'){cell.append('"');i++}
   else if(c=='"')quoted=false
   else cell.append(c)
  }else when(c){
   '"'->quoted=true
   ','->{row.add(cell.toString());cell.clear()}
   '\r'->{}
   '\n'->{row.add(cell.toString());cell.clear();rows.add(row);row=mutableListOf()}
   else->cell.append(c)
  }
  i++
 }
 if(cell.isNotEmpty()||row.isNotEmpty()){row.add(cell.toString());rows.add(row)}
 return rows.filter{r->r.any{it.isNotBlank()}}
}

private val TRUE=setOf("true","1","yes","checked","x")
private val BOOL=TRUE+setOf("false","0","no","unchecked","")

/** Picks a field type for a new column: CHECKBOX, SELECT (few repeated values) or TEXT. */
fun inferFieldType(values:List<String>):Pair<String,List<String>>{
 val filled=values.map{it.trim()}.filter{it.isNotEmpty()}
 if(filled.isNotEmpty()&&values.all{it.trim().lowercase() in BOOL})return "CHECKBOX" to emptyList()
 val distinct=filled.distinct()
 if(distinct.isNotEmpty()&&distinct.size<=10&&filled.size>=2*distinct.size&&distinct.all{it.length<=100})return "SELECT" to distinct
 return "TEXT" to emptyList()
}

/** Converts one CSV cell to the JSON the backend validates; null means leave the cell empty. */
fun csvCell(field:JsonObject,raw:String):com.google.gson.JsonElement?{
 val v=raw.trim();if(v.isEmpty())return null
 return when(field["type"]?.asString){
  "TEXT"->com.google.gson.JsonPrimitive(v.take(5000))
  "CHECKBOX"->com.google.gson.JsonPrimitive(v.lowercase() in TRUE)
  "NUMBER"->v.replace(',','.').toDoubleOrNull()?.let{com.google.gson.JsonPrimitive(it)}
  "DATE"->v.takeIf{Regex("""\d{4}-\d{2}-\d{2}""").matches(it)}?.let{com.google.gson.JsonPrimitive(it)}
  "SELECT"->v.takeIf{field.getAsJsonArray("options")?.any{o->o.asString==v}==true}?.let{com.google.gson.JsonPrimitive(it)}
  "LONG_TEXT"->com.google.gson.JsonPrimitive(v.take(20000))
  "URL","EMAIL","PHONE"->com.google.gson.JsonPrimitive(v)
  "MULTI_SELECT"->{val opts=field.getAsJsonArray("options")?.map{it.asString}.orEmpty();com.google.gson.JsonArray().apply{v.split(',').map{it.trim()}.filter{it in opts}.distinct().forEach(::add)}.takeIf{it.size()>0}}
  else->null
 }
}
