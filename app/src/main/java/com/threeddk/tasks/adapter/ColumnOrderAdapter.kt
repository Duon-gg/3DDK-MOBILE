package com.threeddk.tasks.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.ItemColumnOrderBinding
import com.threeddk.tasks.model.text

/** Drag-to-reorder list of columns; [order] is the live result. */
class ColumnOrderAdapter(fields:List<JsonObject>):RecyclerView.Adapter<ColumnOrderAdapter.Holder>(){
 val order=fields.toMutableList()
 class Holder(val binding:ItemColumnOrderBinding):RecyclerView.ViewHolder(binding.root)
 override fun getItemCount()=order.size
 override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(ItemColumnOrderBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:Holder,position:Int){
  holder.binding.root.text=order[position].text("name")
  holder.binding.root.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_table_list,0,0,0)
  // TalkBack cannot drag: offer "move up/down" actions on each row instead.
  val view=holder.binding.root;val ctx=view.context
  (view.getTag(R.id.column_move_actions) as? IntArray)?.forEach{androidx.core.view.ViewCompat.removeAccessibilityAction(view,it)}
  val ids=intArrayOf(
   androidx.core.view.ViewCompat.addAccessibilityAction(view,ctx.getString(R.string.column_move_up)){_,_->move(holder.bindingAdapterPosition,-1)},
   androidx.core.view.ViewCompat.addAccessibilityAction(view,ctx.getString(R.string.column_move_down)){_,_->move(holder.bindingAdapterPosition,1)})
  view.setTag(R.id.column_move_actions,ids)
 }
 /** Moves one row by [step]; false at either end. */
 fun move(position:Int,step:Int):Boolean{
  val target=position+step
  if(position<0||target !in order.indices)return false
  order.add(target,order.removeAt(position));notifyItemMoved(position,target);notifyItemChanged(target);notifyItemChanged(position);return true
 }
 fun attach(list:RecyclerView){
  list.adapter=this
  ItemTouchHelper(object:ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,0){
   override fun onMove(rv:RecyclerView,from:RecyclerView.ViewHolder,to:RecyclerView.ViewHolder):Boolean{
    order.add(to.bindingAdapterPosition,order.removeAt(from.bindingAdapterPosition));notifyItemMoved(from.bindingAdapterPosition,to.bindingAdapterPosition);return true
   }
   override fun onSwiped(holder:RecyclerView.ViewHolder,direction:Int){}
  }).attachToRecyclerView(list)
 }
}
