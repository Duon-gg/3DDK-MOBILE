package com.threeddk.tasks.viewmodel
fun filterOperators(type:String):List<String> = if(type=="MULTI_SELECT")listOf("contains","isEmpty") else buildList{
 addAll(listOf("eq","ne","isEmpty"))
 when(type){"TEXT","LONG_TEXT","URL","EMAIL","PHONE"->add("contains");"NUMBER","DATE"->addAll(listOf("lt","lte","gt","gte"));"USER"->add("isMe")}
}
internal fun matchingPickerIndices(labels:List<String>,query:String)=labels.indices.filter{labels[it].contains(query.trim(),true)}
