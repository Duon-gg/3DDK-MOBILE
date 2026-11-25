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

private val TRUE=setOf("true","1","yes","checked","x","có","co","đúng","✓","✔")
private val FALSE=setOf("false","0","no","unchecked","không","khong","sai")
private val BOOL=TRUE+FALSE+setOf("")

/** "1234.5", "1,234.5" (US) and "1.234,5" (VN) all read as 1234.5; anything else is not a number. */
fun csvNumber(text:String):Double?{
 val v=text.trim().replace(" ","")
 val lastComma=v.lastIndexOf(',');val lastDot=v.lastIndexOf('.')
 val normal=when{
  lastComma>=0&&lastDot>=0->if(lastComma>lastDot)v.replace(".","").replace(',','.') else v.replace(",","")
  lastComma>=0->if(v.count{it==','}==1&&v.length-lastComma-1!=3)v.replace(',','.') else v.replace(",","")
  else->v
 }
 return normal.toDoubleOrNull()?.takeIf{it.isFinite()}
}

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
  // Unknown words are flagged instead of silently becoming "unchecked".
  "CHECKBOX"->when(v.lowercase()){in TRUE->com.google.gson.JsonPrimitive(true);in FALSE->com.google.gson.JsonPrimitive(false);else->null}
  "NUMBER"->csvNumber(v)?.let{com.google.gson.JsonPrimitive(it)}
  "DATE"->v.takeIf{Regex("""\d{4}-\d{2}-\d{2}""").matches(it)}?.let{com.google.gson.JsonPrimitive(it)}
  "SELECT"->v.takeIf{field.getAsJsonArray("options")?.any{o->o.asString==v}==true}?.let{com.google.gson.JsonPrimitive(it)}
  "LONG_TEXT"->com.google.gson.JsonPrimitive(v.take(20000))
  // Same patterns as ws_p0_validate on the server.
  "URL"->v.takeIf{it.length<=2000&&Regex("""^https?://\S+$""",RegexOption.IGNORE_CASE).matches(it)}?.let{com.google.gson.JsonPrimitive(it)}
  "EMAIL"->v.takeIf{it.length<=254&&Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(it)}?.let{com.google.gson.JsonPrimitive(it)}
  "PHONE"->v.takeIf{Regex("""^[+0-9 ().-]{3,40}$""").matches(it)}?.let{com.google.gson.JsonPrimitive(it)}
  "MULTI_SELECT"->{val opts=field.getAsJsonArray("options")?.map{it.asString}.orEmpty();com.google.gson.JsonArray().apply{v.split(',').map{it.trim()}.filter{it in opts}.distinct().forEach(::add)}.takeIf{it.size()>0}}
  else->null
 }
}

/** A filled cell the server would reject; USER cells are skipped on import, never rejected. */
fun csvCellInvalid(field:JsonObject,raw:String)=raw.isNotBlank()&&field["type"]?.asString!="USER"&&csvCell(field,raw)==null

/** Reads at most [limit] bytes; null when the stream is longer, so a big file is refused instead of cut mid-row. */
fun readLimited(input:java.io.InputStream,limit:Int):ByteArray?{
 val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
 while(true){val n=input.read(buf);if(n<0)return out.toByteArray();out.write(buf,0,n);if(out.size()>limit)return null}
}

/** Splits rows into batches that stay under the API's 32 KB body limit (UTF-8, with room for the envelope). */
fun csvBatches(rows:List<JsonObject>,maxBytes:Int=24_000):List<List<JsonObject>>{
 val batches=mutableListOf<MutableList<JsonObject>>();var size=0
 for(row in rows){
  val bytes=row.toString().toByteArray(Charsets.UTF_8).size+12
  if(batches.isEmpty()||size+bytes>maxBytes){batches.add(mutableListOf());size=0}
  batches.last().add(row);size+=bytes
 }
 return batches
}

/** Repeated column names become "Tên 2", "Tên 3"… so each CSV column keeps its own field instead of collapsing into one. */
fun csvHeaders(raw:List<String>):List<String>{
 val used=mutableSetOf<String>()
 return raw.map{name->
  val base=name.trim();var candidate=base;var n=2
  while(candidate.lowercase() in used){candidate="$base $n".take(100);n++}
  used.add(candidate.lowercase());candidate
 }
}
