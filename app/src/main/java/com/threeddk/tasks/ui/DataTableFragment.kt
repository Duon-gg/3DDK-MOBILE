package com.threeddk.tasks.ui

import android.os.Bundle
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.*
import com.threeddk.tasks.*
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.*

class DataTableFragment:Fragment(){
 private var binding:FragmentDataTableBinding?=null
 private val vm:WorkspaceViewModel by viewModels()
 private val path get()=requireArguments().getString("path")!!
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 private var schema=""
 private lateinit var adapter:GridAdapter
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentDataTableBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;adapter=GridAdapter{edit(it)};b.grid.layoutManager=LinearLayoutManager(requireContext());b.grid.adapter=adapter
  b.search.doAfterTextChanged{render()};b.refresh.setOnClickListener{vm.refresh()};b.add.setOnClickListener{edit("new")};setHasOptionsMenu(true)
  watch(vm.data){render();requireActivity().invalidateOptionsMenu()};watch(vm.busy){b.refresh.isEnabled=!it;b.add.isEnabled=!it;dialog?.getButton(-1)?.isEnabled=!it}
  watch(vm.note){b.status.text=it};watch(vm.error){if(it!=null){b.status.text=it;dialog?.setMessage(it)}};vm.open(path)
 }
 private fun render(){
  val b=binding?:return;val data=vm.data.value?:return
  b.heading.text=data.text("name");b.add.isVisible=data.text("role")!="VIEWER"
  val fields=data.objects("fields");val signature=fields.toString()
  val schemaChanged=schema!=signature||b.header.childCount==0
  if(schemaChanged){schema=signature;b.header.removeAllViews();fields.forEach{b.header.addView(gridCell(it.text("name"),true))}}
  adapter.fields=fields
  if(schemaChanged)adapter.notifyDataSetChanged()
  val records=data.objects("records").filter{row->row.getAsJsonObject("cells").entrySet().any{(_,value)->value.toString().contains(b.search.text.toString(),true)}}
  val all=if(b.search.text.isNullOrBlank())data.objects("records") else records
  adapter.submitList(all);b.empty.isVisible=all.isEmpty()
  b.empty.text=if(b.search.text.isNullOrBlank())"Bảng trống. Thêm dòng đầu tiên để bắt đầu." else "Không có kết quả phù hợp."
 }
 private fun edit(id:String){findNavController().navigate(R.id.recordEditor,bundleOf("path" to path,"recordId" to id))}
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){if(vm.data.value?.text("role")=="OWNER")menu.add(0,10,0,"Thêm cột")}
 override fun onOptionsItemSelected(item:MenuItem):Boolean{if(item.itemId!=10)return super.onOptionsItemSelected(item);addField();return true}
 private fun addField(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint="Tên cột"
  val types=listOf("TEXT","NUMBER","DATE","CHECKBOX","SELECT")
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Văn bản","Số","Ngày (YYYY-MM-DD)","Đánh dấu","Lựa chọn"))
  b.choice.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){b.extraBox.isVisible=position==4};override fun onNothingSelected(p:AdapterView<*>?){}}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle("Thêm cột tùy chỉnh").setView(b.root).setPositiveButton("Thêm",null).setNegativeButton("Hủy",null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error="Nhập tên từ 1 đến 100 ký tự";return@setOnClickListener}
   val options=b.extra.text.toString().split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
   if(b.choice.selectedItemPosition==4&&(options.isEmpty()||options.size>30||options.any{it.length>100})){b.extraBox.error="Nhập từ 1 đến 30 lựa chọn, mỗi lựa chọn tối đa 100 ký tự";return@setOnClickListener}
   vm.mutate("POST","$path/fields",JsonObject().apply{addProperty("name",name);addProperty("type",types[b.choice.selectedItemPosition]);add("options",Gson().toJsonTree(options))}){if(isAdded)d.dismiss()}
  }};d.show()
 }
 private fun gridCell(value:String,header:Boolean=false)=TextView(requireContext()).apply{
  text=value;textSize=15f;setPadding(16,16,16,16);minHeight=(52*resources.displayMetrics.density).toInt();maxLines=3
  layoutParams=LinearLayout.LayoutParams((170*resources.displayMetrics.density).toInt(),ViewGroup.LayoutParams.WRAP_CONTENT)
  if(header)setTypeface(typeface,Typeface.BOLD)
 }
 private inner class GridAdapter(val click:(String)->Unit):androidx.recyclerview.widget.ListAdapter<JsonObject,GridHolder>(object:DiffUtil.ItemCallback<JsonObject>(){
  override fun areItemsTheSame(a:JsonObject,b:JsonObject)=a.text("id")==b.text("id")
  override fun areContentsTheSame(a:JsonObject,b:JsonObject)=a==b
 }){
  var fields=emptyList<JsonObject>()
  override fun onCreateViewHolder(parent:ViewGroup,type:Int)=GridHolder(LinearLayout(parent.context).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);isClickable=true;isFocusable=true})
  override fun onBindViewHolder(h:GridHolder,position:Int){val row=getItem(position);h.line.removeAllViews();val cells=row.getAsJsonObject("cells");fields.forEach{f->val value=cells.get(f.text("id"));h.line.addView(gridCell(if(value==null||value.isJsonNull)"—" else if(f.text("type")=="CHECKBOX")if(value.asBoolean)"✓ Có" else "Không" else value.asString))};h.line.setBackgroundColor(android.graphics.Color.parseColor(if(position%2==0)"#FFFFFF" else "#F1F5F9"));h.line.contentDescription=fields.joinToString(", "){it.text("name")+": "+(cells.get(it.text("id"))?.toString()?:"trống")};h.line.setOnClickListener{click(row.text("id"))}}
 }
 private class GridHolder(val line:LinearLayout):RecyclerView.ViewHolder(line)
 override fun onDestroyView(){dialog?.dismiss();dialog=null;binding?.grid?.adapter=null;binding=null;schema="";super.onDestroyView()}
}
