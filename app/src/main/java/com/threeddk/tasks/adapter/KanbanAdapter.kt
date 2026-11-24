package com.threeddk.tasks.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.ItemKanbanColumnBinding

/** One column per stack value; each column reuses the record card list. */
class KanbanAdapter(private val click:(String)->Unit):RecyclerView.Adapter<KanbanAdapter.Holder>(){
 var columns=emptyList<Pair<String?,List<JsonObject>>>()
 var fields=emptyList<JsonObject>()
 var members=emptyList<JsonObject>()
 class Holder(val binding:ItemKanbanColumnBinding,val cards:RecordCardAdapter):RecyclerView.ViewHolder(binding.root)
 override fun getItemCount()=columns.size
 override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
  val binding=ItemKanbanColumnBinding.inflate(LayoutInflater.from(parent.context),parent,false)
  val cards=RecordCardAdapter(click);binding.cards.layoutManager=LinearLayoutManager(parent.context);binding.cards.adapter=cards
  return Holder(binding,cards)
 }
 override fun onBindViewHolder(holder:Holder,position:Int){
  val (value,rows)=columns[position];val context=holder.binding.root.context
  holder.binding.title.text=context.getString(R.string.group_header,value?:context.getString(R.string.p0_cell_empty),rows.size)
  holder.cards.fields=fields;holder.cards.members=members;holder.cards.submitList(rows);holder.cards.notifyDataSetChanged()
 }
}
