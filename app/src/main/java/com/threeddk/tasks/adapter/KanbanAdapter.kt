package com.threeddk.tasks.adapter

import android.content.ClipData
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.ItemKanbanColumnBinding

/**
 * One column per stack value; each column reuses the record card list.
 * Long-press a card and drop it on another column to call [move] with (recordId, column value; null = empty).
 * Screen-reader users get a "move to column" action instead, which calls [chooseColumn].
 */
class KanbanAdapter(private val click:(String)->Unit,private val move:(String,String?)->Unit,private val chooseColumn:(String)->Unit):RecyclerView.Adapter<KanbanAdapter.Holder>(){
 var columns=emptyList<Pair<String?,List<JsonObject>>>()
 var fields=emptyList<JsonObject>()
 var members=emptyList<JsonObject>()
 var editable=false
 class Holder(val binding:ItemKanbanColumnBinding,val cards:RecordCardAdapter):RecyclerView.ViewHolder(binding.root)
 override fun getItemCount()=columns.size
 private fun bindCard(card:View,id:String){
  (card.getTag(R.id.kanban_move_action) as? Int)?.let{ViewCompat.removeAccessibilityAction(card,it)}
  if(!editable){card.setOnLongClickListener(null);return}
  card.setOnLongClickListener{it.startDragAndDrop(ClipData.newPlainText("record",id),View.DragShadowBuilder(it),id,0)}
  card.setTag(R.id.kanban_move_action,ViewCompat.addAccessibilityAction(card,card.context.getString(R.string.kanban_move)){_,_->chooseColumn(id);true})
 }
 override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
  val binding=ItemKanbanColumnBinding.inflate(LayoutInflater.from(parent.context),parent,false)
  val cards=RecordCardAdapter(click,::bindCard);binding.cards.layoutManager=LinearLayoutManager(parent.context);binding.cards.adapter=cards
  val holder=Holder(binding,cards)
  binding.root.setOnDragListener{view,event->
   when(event.action){
    DragEvent.ACTION_DRAG_STARTED->true
    DragEvent.ACTION_DRAG_ENTERED->{view.alpha=.7f;true}
    DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->{view.alpha=1f;true}
    DragEvent.ACTION_DROP->{
     view.alpha=1f
     val id=event.localState as? String;val column=columns.getOrNull(holder.bindingAdapterPosition)
     // Dropping on the card's own column changes nothing.
     if(id!=null&&column!=null&&column.second.none{it.get("id")?.asString==id})move(id,column.first)
     true
    }
    else->true
   }
  }
  return holder
 }
 override fun onBindViewHolder(holder:Holder,position:Int){
  val (value,rows)=columns[position];val context=holder.binding.root.context
  holder.binding.title.text=context.getString(R.string.group_header,value?:context.getString(R.string.p0_cell_empty),rows.size)
  holder.cards.fields=fields;holder.cards.members=members;holder.cards.submitList(rows);holder.cards.notifyDataSetChanged()
 }
}
