package com.threeddk.tasks.adapter
import com.threeddk.tasks.R
import com.threeddk.tasks.model.Row
import com.threeddk.tasks.model.text
import android.view.*
import android.graphics.drawable.GradientDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import com.threeddk.tasks.databinding.ItemRowBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
class RowAdapter(private val click:(String)->Unit):ListAdapter<Row,RowAdapter.Holder>(object:DiffUtil.ItemCallback<Row>(){
 override fun areItemsTheSame(a:Row,b:Row)=a.id==b.id
 override fun areContentsTheSame(a:Row,b:Row)=a==b
}){
 var onLongClick:((String)->Unit)?=null
 class Holder(val binding:ItemRowBinding):RecyclerView.ViewHolder(binding.root)
 override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:Holder,position:Int){val row=getItem(position);with(holder.binding){
  title.text=row.title;subtitle.text=row.subtitle
  subtitle.isVisible=row.subtitle.isNotBlank()
  star.isVisible=row.starred
  androidx.core.view.ViewCompat.setStateDescription(root,if(row.starred)root.context.getString(R.string.home_starred) else null)
  val density=root.resources.displayMetrics.density
  val home=row.homeItem||row.vividMonogram
  val iconSize=((if(home)46 else 42)*density).toInt()
  listOf(avatar,monogram).forEach{it.layoutParams=it.layoutParams.apply{width=iconSize;height=iconSize}}
  monogram.textSize=if(row.vividMonogram)22f else 14f
  title.textSize=if(home)17f else 16f
  subtitle.textSize=if(home)14f else 13f
  listOf(title,subtitle).forEach{it.layoutParams=(it.layoutParams as ViewGroup.MarginLayoutParams).apply{marginStart=((if(home)16 else 12)*density).toInt()}}
  val content=root.getChildAt(0)
  val vertical=((if(home)10 else 9)*density).toInt()
  val horizontal=((if(home)0 else 4)*density).toInt()
  content.setPadding(horizontal,vertical,horizontal,vertical)
  title.setTextColor(android.graphics.Color.parseColor(if(row.important)"#B91C1C" else "#202124"))
  val hasImage=row.avatar.isNotBlank()
  avatar.visibility=if(hasImage||row.iconRes!=0) View.VISIBLE else View.INVISIBLE
  monogram.isVisible=!hasImage&&row.iconRes==0
  avatar.background=null;avatar.setPadding(0,0,0,0)
  if(row.iconRes!=0){
   Glide.with(avatar).clear(avatar)
   avatar.setImageResource(row.iconRes)
   avatar.background=GradientDrawable().apply{cornerRadius=11*density;setColor(android.graphics.Color.parseColor("#E9EBF0"));setStroke((density).toInt(),android.graphics.Color.parseColor("#D6D9DF"))}
   val inset=(11*density).toInt();avatar.setPadding(inset,inset,inset,inset)
  }else if(hasImage){
   Glide.with(avatar).load(row.avatar).placeholder(R.drawable.ic_app).error(R.drawable.ic_app).circleCrop().into(avatar)
  }else{
   Glide.with(avatar).clear(avatar)
   val hue=(row.id.hashCode().toUInt()%360u).toFloat()
   monogram.text=if(row.vividMonogram)row.title.trim().take(2) else row.title.trim().take(2).uppercase()
   val tile=TILES[(row.id.hashCode().toUInt()%TILES.size.toUInt()).toInt()]
   monogram.setTextColor(if(row.vividMonogram)tile.second else android.graphics.Color.HSVToColor(floatArrayOf(hue,.78f,.34f)))
   monogram.background=GradientDrawable().apply{
    cornerRadius=(if(row.vividMonogram)11 else 8)*density
    setColor(if(row.vividMonogram)tile.first else android.graphics.Color.HSVToColor(floatArrayOf(hue,.17f,.96f)))
    if(row.vividMonogram)setStroke(density.toInt(),0x26000000)
   }
  }
  root.setOnClickListener{click(row.id)}
  root.setOnLongClickListener(null)
  root.isLongClickable=(row.id.startsWith("bases/")||row.id.startsWith("workspaces/"))&&onLongClick!=null
  if(root.isLongClickable)root.setOnLongClickListener{onLongClick?.invoke(row.id);true}
 }}
 override fun onViewRecycled(holder:Holder){
  val avatar=holder.binding.avatar
  // RecyclerView may release cached holders after the host Activity is destroyed.
  Glide.with(avatar.context.applicationContext).clear(avatar)
  holder.binding.root.setOnLongClickListener(null)
  holder.binding.root.isLongClickable=false
  holder.binding.root.setOnClickListener(null)
  holder.binding.star.isVisible=false
  super.onViewRecycled(holder)
 }
 companion object{
  private const val DARK=0xFF1D1F25.toInt()
  private const val LIGHT=0xFFFFFFFF.toInt()
  // Fixed bright tiles with contrast-picked text (dark on light fills, white on dark fills).
  private val TILES=listOf(0xFF5FD9D4.toInt() to DARK,0xFFF5BE3F.toInt() to DARK,0xFF7134E6.toInt() to LIGHT,0xFF3A8A2A.toInt() to LIGHT,0xFF63C5F8.toInt() to DARK,0xFF8E4558.toInt() to LIGHT,0xFF2D7FF9.toInt() to LIGHT,0xFFE8577E.toInt() to LIGHT)
 }
}
