package com.threeddk.tasks.adapter
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.recyclerview.widget.*
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.model.text
import com.threeddk.tasks.databinding.ItemGridRecordBinding
import com.threeddk.tasks.ui.cellLabel
 class GridAdapter(val click:(String)->Unit):androidx.recyclerview.widget.ListAdapter<JsonObject,GridHolder>(object:DiffUtil.ItemCallback<JsonObject>(){
  override fun areItemsTheSame(a:JsonObject,b:JsonObject)=a.text("id")==b.text("id")
  override fun areContentsTheSame(a:JsonObject,b:JsonObject)=a==b
 }){
  var fields=emptyList<JsonObject>()
  var members=emptyList<JsonObject>()
  var primary:JsonObject?=null

  override fun onCreateViewHolder(parent:ViewGroup,type:Int)=GridHolder(ItemGridRecordBinding.inflate(LayoutInflater.from(parent.context),parent,false))
  override fun onBindViewHolder(holder:GridHolder,position:Int){
   val row=getItem(position)
   val cells=row.getAsJsonObject("cells")?:JsonObject()
   val primaryField=primary
   holder.title.isVisible=primaryField!=null
   holder.title.text=primaryField?.let{cellLabel(holder.root.context,it,cells.get(it.text("id")),members)}.orEmpty()
   holder.line.removeAllViews()
   fields.forEach{field->
    val value=cellLabel(holder.root.context,field,cells.get(field.text("id")),members)
    holder.line.addView(gridCell(holder.root.context,value,field.text("type")))
   }
   holder.root.contentDescription=(listOfNotNull(primaryField)+fields).joinToString(", "){field->
    field.text("name")+": "+cellLabel(holder.root.context,field,cells.get(field.text("id")),members)
   }
   holder.root.setOnClickListener{click(row.text("id"))}
  }
 }
 class GridHolder(val binding:ItemGridRecordBinding):RecyclerView.ViewHolder(binding.root){val root get()=binding.root;val title get()=binding.title;val line get()=binding.cells}

 internal fun gridCell(context:android.content.Context,value:String,type:String,header:Boolean=false)=TextView(context).apply{
  val density=resources.displayMetrics.density
  val horizontalInset=(10*density).toInt()
  val verticalInset=((if(header) 4 else 8)*density).toInt()
  val columnWidth=when(type){
   "TEXT"->200
   "NUMBER","CHECKBOX"->120
   else->150
  }
  if(!header)setBackgroundResource(R.drawable.grid_cell_background)
  text=value;textSize=if(header)12f else 13f;setPadding(horizontalInset,verticalInset,horizontalInset,verticalInset)
  minHeight=((if(header)32 else 40)*density).toInt();gravity=Gravity.CENTER_VERTICAL;maxLines=1
  ellipsize=android.text.TextUtils.TruncateAt.END
  layoutParams=LinearLayout.LayoutParams((columnWidth*density).toInt(),if(header) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT)
  if(header){
   val ink=com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant)
   setTextColor(ink)
   val icon=when(type){"NUMBER"->R.drawable.ic_field_number;"DATE"->R.drawable.ic_field_date;"CHECKBOX"->R.drawable.ic_field_checkbox;"SELECT"->R.drawable.ic_field_select;"USER"->R.drawable.ic_field_user;else->R.drawable.ic_field_text}
   setCompoundDrawablesRelativeWithIntrinsicBounds(icon,0,0,0);compoundDrawablePadding=(6*density).toInt()
   compoundDrawableTintList=android.content.res.ColorStateList.valueOf(ink)
  }
 }
