package com.threeddk.tasks.viewmodel

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.threeddk.tasks.model.text

private fun cell(row:JsonObject,fieldId:String):JsonElement?=row.getAsJsonObject("cells")?.get(fieldId)?.takeUnless{it.isJsonNull||(it.isJsonPrimitive&&it.asString.isEmpty())||(it.isJsonArray&&it.asJsonArray.size()==0)}

/** Airtable-style groups: SELECT follows option order, other types sort by value; empty cells go last. Rows keep their order inside a group. */
fun groupRows(records:List<JsonObject>,field:JsonObject):List<Pair<JsonElement?,List<JsonObject>>>{
 val id=field.text("id");val options=field.getAsJsonArray("options")?.map{it.asString}.orEmpty()
 val groups=records.groupBy{cell(it,id)}
 val (empty,filled)=groups.keys.partition{it==null}
 val ordered=when(field.text("type")){
  "SELECT"->filled.sortedBy{k->options.indexOf(k!!.asString).let{if(it<0)Int.MAX_VALUE else it}}
  "NUMBER"->filled.sortedBy{it!!.asBigDecimal}
  else->filled.sortedBy{(if(it!!.isJsonArray)it.asJsonArray.joinToString(", "){e->e.asString} else it.asString).lowercase()}
 }
 return (ordered+empty).map{it to groups.getValue(it)}
}

/** Footer summary for one column over the loaded rows: CHECKBOX → % ticked, NUMBER → sum, others → filled count. */
data class ColumnSummary(val kind:String,val value:String)
fun columnSummary(records:List<JsonObject>,field:JsonObject):ColumnSummary{
 val values=records.mapNotNull{cell(it,field.text("id"))}
 return when(field.text("type")){
  "CHECKBOX"->ColumnSummary("checked",(if(records.isEmpty())0 else values.count{it.asBoolean}*100/records.size).toString())
  "NUMBER"->ColumnSummary("sum",values.fold(java.math.BigDecimal.ZERO){s,v->s+v.asBigDecimal}.stripTrailingZeros().toPlainString())
  else->ColumnSummary("filled",values.size.toString())
 }
}

/** Kanban stacks: every SELECT option is a column (even empty, like Airtable); the no-value column only shows when it has rows. */
fun kanbanColumns(records:List<JsonObject>,field:JsonObject):List<Pair<String?,List<JsonObject>>>{
 val id=field.text("id");val options=field.getAsJsonArray("options")?.map{it.asString}.orEmpty()
 val byValue=records.groupBy{cell(it,id)?.asString}
 return listOf<String?>(null).plus(options).plus(byValue.keys.filterNotNull().filter{it !in options}).map{it to byValue[it].orEmpty()}.filter{it.first!=null||it.second.isNotEmpty()}
}
