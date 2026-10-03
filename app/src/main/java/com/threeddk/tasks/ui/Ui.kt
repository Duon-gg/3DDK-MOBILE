package com.threeddk.tasks.ui
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.ItemRowBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
fun <T> Fragment.watch(flow:Flow<T>,block:(T)->Unit)=viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){flow.collect{block(it)}}}
data class Row(val id:String,val title:String,val subtitle:String,val avatar:String="",val important:Boolean=false)
class RowAdapter(private val click:(String)->Unit):ListAdapter<Row,RowAdapter.Holder>(object:DiffUtil.ItemCallback<Row>(){
 override fun areItemsTheSame(a:Row,b:Row)=a.id==b.id
 override fun areContentsTheSame(a:Row,b:Row)=a==b
}){
 class Holder(val binding:ItemRowBinding):RecyclerView.ViewHolder(binding.root)
 override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:Holder,position:Int){val row=getItem(position);with(holder.binding){
  title.text=row.title;subtitle.text=row.subtitle
  title.setTextColor(android.graphics.Color.parseColor(if(row.important)"#B91C1C" else "#0F172A"))
  Glide.with(avatar).load(row.avatar.ifEmpty{null}).placeholder(R.drawable.ic_app).error(R.drawable.ic_app).circleCrop().into(avatar)
  root.setOnClickListener{click(row.id)}
 }}
 override fun onViewRecycled(holder:Holder){Glide.with(holder.binding.avatar).clear(holder.binding.avatar);super.onViewRecycled(holder)}
}

