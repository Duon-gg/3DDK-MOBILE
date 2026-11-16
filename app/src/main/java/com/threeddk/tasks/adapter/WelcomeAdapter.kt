package com.threeddk.tasks.adapter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.threeddk.tasks.databinding.ItemWelcomeBinding
class WelcomeAdapter(private val pages:List<Triple<Int,Int,Int>>):RecyclerView.Adapter<WelcomeAdapter.Holder>(){
 class Holder(val binding:ItemWelcomeBinding):RecyclerView.ViewHolder(binding.root)
 override fun getItemCount()=pages.size
 override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(ItemWelcomeBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:Holder,position:Int){with(holder.binding){title.setText(pages[position].first);description.setText(pages[position].second);illustration.setImageResource(pages[position].third)}}
}
