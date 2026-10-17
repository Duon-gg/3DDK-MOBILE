package com.threeddk.tasks.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.graphics.Typeface
import android.view.*
import android.widget.*
import android.text.InputType
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
import java.time.LocalDate

class DataTableFragment:Fragment(){
 private var binding:FragmentDataTableBinding?=null
 private val vm:WorkspaceViewModel by viewModels()
 private val path get()=requireArguments().getString("path")!!
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 private var schema=""
 private var viewSignature=""
 private var dateDialog:DatePickerDialog?=null
 private lateinit var adapter:GridAdapter
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentDataTableBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;adapter=GridAdapter(::edit);b.grid.layoutManager=LinearLayoutManager(requireContext());b.grid.adapter=adapter
  b.search.setText(vm.queryConfig.text("search"));b.search.doAfterTextChanged{vm.search(it.toString())};b.refresh.setOnClickListener{vm.refresh()};b.add.setOnClickListener{edit("new")};b.more.setOnClickListener{vm.loadMore()}
  b.filter.setOnClickListener{filters()};b.sort.setOnClickListener{sorts()};b.columns.setOnClickListener{columns()};setHasOptionsMenu(true)
  watch(vm.data){render();requireActivity().invalidateOptionsMenu()};watch(vm.accessDenied){render()};watch(vm.drafts){requireActivity().invalidateOptionsMenu()};watch(vm.selectedViewId){b.search.setText(vm.queryConfig.text("search"));render()};watch(vm.hasMore){b.more.isVisible=it}
  watch(vm.busy){val usable=vm.data.value!=null&&!vm.accessDenied.value;b.refresh.isEnabled=!it;b.add.isEnabled=!it&&usable;b.more.isEnabled=!it&&usable;b.viewChoice.isEnabled=!it&&usable;b.filter.isEnabled=!it&&usable;b.sort.isEnabled=!it&&usable;b.columns.isEnabled=!it&&usable;dialog?.getButton(-1)?.isEnabled=!it;if(it)b.status.text=getString(R.string.p0_dang_tai_giu_du_lieu_da_hien_thi)}
  watch(vm.note){b.status.text=it};watch(vm.error){if(it!=null){b.status.text=it;dialog?.setMessage(it)}};vm.openTable(path)
 }
 private fun render(){
  val b=binding?:return;val data=vm.data.value.takeUnless{vm.accessDenied.value}
  val usable=data?.has("schemaVersion")==true&&!vm.accessDenied.value
  b.search.isEnabled=usable;b.filter.isEnabled=usable&&!vm.busy.value;b.sort.isEnabled=usable&&!vm.busy.value;b.columns.isEnabled=usable&&!vm.busy.value
  b.add.isVisible=data?.text("role") in listOf("OWNER","EDITOR")&&!vm.accessDenied.value
  if(data==null){
   if(vm.accessDenied.value){adapter.submitList(emptyList());b.header.removeAllViews();b.heading.text="";b.add.isVisible=false;b.viewChoice.isVisible=false;schema="";viewSignature=""}
   b.empty.isVisible=vm.accessDenied.value||adapter.currentList.isEmpty();b.empty.text=if(vm.accessDenied.value)getString(R.string.p0_access_revoked) else if(vm.busy.value)getString(R.string.p0_dang_tai_du_lieu) else getString(R.string.p0_chua_co_du_lieu_tren_thiet_bi_ket);return
  }
  b.heading.text=data.text("name");b.add.isVisible=data.text("role")!="VIEWER"
  val fields=data.objects("fields");val ids=vm.queryConfig.getAsJsonArray("fieldIds")?.map{it.asString}.orEmpty()
  val order=vm.queryConfig.getAsJsonArray("fieldOrder")?.map{it.asString}.orEmpty()+fields.map{it.text("id")}
  val shown=order.distinct().mapNotNull{id->fields.firstOrNull{it.text("id")==id&&(ids.isEmpty()||id in ids)}}
  adapter.members=data.objects("members")
  val signature=shown.toString()+adapter.members.toString()
  if(schema!=signature||b.header.childCount==0){schema=signature;b.header.removeAllViews();shown.forEach{b.header.addView(gridCell(it.text("name"),true))};adapter.fields=shown;adapter.notifyDataSetChanged()}
  val records=data.objects("records");adapter.submitList(records);b.empty.isVisible=records.isEmpty()
  val filtered=b.search.text?.isNotBlank()==true||conditions().isNotEmpty()
  b.empty.text=if(data.get("queryCached")?.asBoolean!=true)getString(R.string.p0_query_not_cached) else if(filtered)getString(R.string.p0_khong_co_dong_phu_hop_thu_doi_tim) else getString(R.string.p0_chua_co_dong_trong_du_lieu_da_tai)
  b.filter.text=getString(R.string.p0_filter_count,conditions().size);b.sort.text=getString(R.string.p0_sort_count,sortItems().size)
  val views=data.objects("views");val nextSignature=views.toString()+vm.selectedViewId.value
  b.viewChoice.isVisible=vm.supports("savedViews")
  if(viewSignature!=nextSignature){
   viewSignature=nextSignature;b.viewChoice.onItemSelectedListener=null
   val labels=listOf(getString(R.string.p0_tat_ca_chua_luu_che_do_xem))+views.map{getString(R.string.p0_view_label,it.text("name"),getString(if(it.text("visibility")=="PERSONAL")R.string.p0_personal_view else R.string.p0_shared_view))}
   b.viewChoice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,labels)
   b.viewChoice.setSelection(views.indexOfFirst{it.text("id")==vm.selectedViewId.value}+1)
   b.viewChoice.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
    override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){val selected=views.getOrNull(position-1)?.text("id");if(selected!=vm.selectedViewId.value)vm.selectView(selected)}
    override fun onNothingSelected(p:AdapterView<*>?){}
   }
  }
 }
 private fun edit(id:String){findNavController().navigate(R.id.recordEditor,bundleOf("path" to path,"recordId" to id))}
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){
  if(vm.supports("savedViews")){menu.add(0,11,0,getString(R.string.p0_luu_thanh_che_do_xem_moi));if(editableView()!=null)menu.add(0,12,1,getString(R.string.p0_luu_thay_doi_che_do_xem))}
  if(vm.data.value?.text("role")=="OWNER")menu.add(0,10,2,getString(R.string.p0_them_cot))
  if(vm.drafts.value.isNotEmpty())menu.add(0,13,3,getString(R.string.p0_draft_count,vm.drafts.value.size))
 }
 override fun onOptionsItemSelected(item:MenuItem):Boolean{when(item.itemId){10->addField();11->saveView();12->updateView();13->drafts();else->return super.onOptionsItemSelected(item)};return true}
 private fun drafts(){
  val items=vm.drafts.value;val firstField=vm.data.value?.objects("fields")?.firstOrNull()?.text("id")
  val labels=items.mapIndexed{index,draft->runCatching{JsonParser.parseString(draft.description).asJsonObject.text(firstField.orEmpty()).take(100)}.getOrDefault("").ifBlank{getString(R.string.p0_new_row_number,index+1)}}.toTypedArray()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_ban_nhap_chua_gui_len_bang)).setItems(labels){_,index->
   val draft=items[index]
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(labels[index]).setPositiveButton(getString(R.string.p0_tiep_tuc_soan)){_,_->edit("new/"+draft.taskId.substringAfterLast('/'))}.setNeutralButton(getString(if(draft.pendingCreateJson.isBlank())R.string.p0_xoa_nhap else R.string.p0_check_previous_submit)){_,_->
    if(draft.pendingCreateJson.isNotBlank()){
     dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_check_previous_submit)).setMessage(getString(R.string.p0_pending_draft_no_delete)).setPositiveButton(getString(R.string.p0_tiep_tuc_soan)){_,_->edit("new/"+draft.taskId.substringAfterLast('/'))}.setNegativeButton(getString(R.string.p0_dong),null).show()
    }else dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_xoa_nhap_tren_thiet_bi)).setMessage(getString(R.string.p0_noi_dung_nay_chua_duoc_gui_len_bang)).setPositiveButton(getString(R.string.p0_xoa_nhap)){_,_->vm.discardDraft(draft.taskId)}.setNegativeButton(getString(R.string.p0_giu_lai),null).show()
   }.setNegativeButton(getString(R.string.p0_dong),null).show()
  }.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun editableView()=vm.data.value?.objects("views")?.firstOrNull{it.text("id")==vm.selectedViewId.value&&(vm.data.value?.text("role")=="OWNER"||it.text("visibility")=="PERSONAL")}
 private fun conditions()=vm.queryConfig.getAsJsonObject("filter")?.objects("conditions").orEmpty()
 private fun sortItems()=vm.queryConfig.objects("sort")
 private fun applyConditions(items:List<JsonObject>){val op=vm.queryConfig.getAsJsonObject("filter")?.text("op")?.takeIf{it=="or"}?:"and";vm.queryTable(filter=JsonObject().apply{addProperty("op",op);add("conditions",Gson().toJsonTree(items))});render()}
 private fun fieldName(id:String)=vm.data.value?.objects("fields")?.firstOrNull{it.text("id")==id}?.text("name")?:id
 private fun filters(){
  val items=conditions()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(if(vm.queryConfig.getAsJsonObject("filter")?.text("op")=="or")R.string.p0_filter_or else R.string.p0_loc_thoa_tat_ca_dieu_kien)).setItems(items.map{fieldName(it.text("fieldId"))+" "+operatorLabel(requireContext(),it.text("operator"))+" "+(it.get("value")?.let{v->cellLabel(requireContext(),null,v,vm.data.value?.objects("members").orEmpty())}?:"")}.toTypedArray()){_,index->
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_xoa_dieu_kien_nay)).setPositiveButton(getString(R.string.p0_xoa)){_,_->applyConditions(items.filterIndexed{i,_->i!=index})}.setNegativeButton(getString(R.string.p0_huy),null).show()
  }.setPositiveButton(getString(R.string.p0_them_dieu_kien)){_,_->if(items.size<10)chooseField(::chooseOperator) else binding?.status?.text=getString(R.string.p0_toi_da_10_dieu_kien_trong_giao_dien)}.setNeutralButton(getString(R.string.p0_xoa_tat_ca)){_,_->applyConditions(emptyList())}.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun chooseField(done:(JsonObject)->Unit){
  val fields=vm.data.value?.objects("fields").orEmpty()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_chon_cot)).setItems(fields.map{it.text("name")}.toTypedArray()){_,index->done(fields[index])}.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 private fun chooseOperator(field:JsonObject){
  val operators=mutableListOf("eq","ne","isEmpty")
  if(field.text("type")=="TEXT")operators.add("contains")
  if(field.text("type") in listOf("NUMBER","DATE"))operators.addAll(listOf("lt","lte","gt","gte"))
  if(field.text("type")=="USER")operators.add("isMe")
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(field.text("name")).setItems(operators.map{operatorLabel(requireContext(),it)}.toTypedArray()){_,index->
   val op=operators[index]
   fun apply(value:JsonElement?){applyConditions(conditions()+JsonObject().apply{addProperty("fieldId",field.text("id"));addProperty("operator",op);if(value!=null)add("value",value)})}
   if(op in listOf("isEmpty","isMe"))apply(null) else filterValue(field,::apply)
  }.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 private fun filterValue(field:JsonObject,done:(JsonElement)->Unit){
  val members=vm.data.value?.objects("members").orEmpty()
  val values=when(field.text("type")){"SELECT"->field.getAsJsonArray("options")?.map{it.asString}.orEmpty();"USER"->members.map{it.text("uid")};"CHECKBOX"->listOf("true","false");else->emptyList()}
  if(field.text("type") in listOf("SELECT","USER","CHECKBOX")){
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_value_of_field,field.text("name"))).setItems(values.map{cellLabel(requireContext(),field,JsonPrimitive(it),members)}.toTypedArray()){_,index->done(if(field.text("type")=="CHECKBOX")JsonPrimitive(values[index].toBoolean()) else JsonPrimitive(values[index]))}.setNegativeButton(getString(R.string.p0_huy),null).show();return
  }
  if(field.text("type")=="DATE"){val day=LocalDate.now();dateDialog=DatePickerDialog(requireContext(),{_,year,month,date->done(JsonPrimitive(LocalDate.of(year,month+1,date).toString()))},day.year,day.monthValue-1,day.dayOfMonth).also{it.show()};return}
  val b=DialogInputBinding.inflate(layoutInflater);b.choice.isVisible=false;b.extraBox.isVisible=false;b.valueBox.hint=field.text("name")
  if(field.text("type")=="NUMBER")b.value.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_gia_tri_loc)).setView(b.root).setPositiveButton(getString(R.string.p0_ap_dung),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{val value=b.value.text.toString();val json=if(field.text("type")=="NUMBER"){val number=value.toBigDecimalOrNull();if(number==null){b.valueBox.error=getString(R.string.p0_nhap_mot_so_hop_le);return@setOnClickListener};JsonPrimitive(number)}else JsonPrimitive(value);done(json);d.dismiss()}};d.show()
 }
 private fun sorts(){
  val items=sortItems()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_sap_xep_o_trong_o_cuoi)).setItems(items.map{fieldName(it.text("fieldId"))+if(it.text("direction")=="desc")getString(R.string.p0_giam_dan) else getString(R.string.p0_tang_dan)}.toTypedArray()){_,index->vm.queryTable(sort=Gson().toJsonTree(items.filterIndexed{i,_->i!=index}).asJsonArray);render()}
   .setPositiveButton(getString(R.string.p0_them_cot)){_,_->if(items.size<3)chooseField{field->dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(field.text("name")).setItems(arrayOf(getString(R.string.p0_tang_dan_2),getString(R.string.p0_giam_dan_2))){_,direction->val next=items.filter{it.text("fieldId")!=field.text("id")}+JsonObject().apply{addProperty("fieldId",field.text("id"));addProperty("direction",if(direction==0)"asc" else "desc")};vm.queryTable(sort=Gson().toJsonTree(next).asJsonArray);render()}.setNegativeButton(getString(R.string.p0_huy),null).show()} else binding?.status?.text=getString(R.string.p0_toi_da_3_cot_sap_xep)}
   .setNeutralButton(getString(R.string.p0_xoa_tat_ca)){_,_->vm.queryTable(sort=JsonArray());render()}.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun columns(){
  val fields=vm.data.value?.objects("fields").orEmpty();val selected=vm.queryConfig.getAsJsonArray("fieldIds")?.map{it.asString}.orEmpty();val checked=BooleanArray(fields.size){selected.isEmpty()||fields[it].text("id") in selected}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_cot_hien_thi)).setMultiChoiceItems(fields.map{it.text("name")}.toTypedArray(),checked){_,index,value->checked[index]=value}.setPositiveButton(getString(R.string.p0_ap_dung),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{if(checked.none{it}){binding?.status?.text=getString(R.string.p0_chon_it_nhat_mot_cot);return@setOnClickListener};vm.queryTable(fieldIds=Gson().toJsonTree(fields.filterIndexed{i,_->checked[i]}.map{it.text("id")}).asJsonArray);render();d.dismiss()}};d.show()
 }
 private fun saveView(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten_che_do_xem);b.extraBox.isVisible=false
  val owner=vm.data.value?.text("role")=="OWNER";b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,if(owner)listOf(getString(R.string.p0_ca_nhan_2),getString(R.string.p0_chung_cho_khong_gian)) else listOf(getString(R.string.p0_ca_nhan_2)))
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_luu_bo_loc_sap_xep_va_cot)).setView(b.root).setPositiveButton(getString(R.string.p0_luu),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_ten_can_tu_1_den_100_ky_tu);return@setOnClickListener};vm.saveView(name,if(owner&&b.choice.selectedItemPosition==1)"SHARED" else "PERSONAL"){if(isAdded)d.dismiss()}}};d.show()
 }
 private fun updateView(){
  val view=editableView()?:return
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_update_view_title,view.text("name"))).setMessage(if(view.text("visibility")=="SHARED")getString(R.string.p0_bo_loc_sap_xep_va_cot_moi_se) else getString(R.string.p0_luu_cau_hinh_hien_tai_vao_che_do)).setPositiveButton(getString(R.string.p0_luu)){_,_->
   val payload=JsonObject().apply{listOf("filter","sort","fieldIds","fieldOrder").forEach{key->vm.queryConfig.get(key)?.let{add(key,it.deepCopy())}};addProperty("expectedVersion",view.get("version")?.asInt?:1)}
   vm.mutate("PATCH","$path/views/${view.text("id")}",payload){vm.selectView(view.text("id"))}
  }.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 private fun addField(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten_cot)
  val types=listOf("TEXT","NUMBER","DATE","CHECKBOX","SELECT")+if(vm.supports("userFields"))listOf("USER") else emptyList()
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.p0_van_ban),getString(R.string.p0_so),getString(R.string.p0_ngay),getString(R.string.p0_danh_dau),getString(R.string.p0_lua_chon))+if(types.contains("USER"))listOf(getString(R.string.p0_thanh_vien_phu_trach)) else emptyList())
  b.choice.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){b.extraBox.isVisible=position==4};override fun onNothingSelected(p:AdapterView<*>?){} }
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_them_cot_tuy_chinh)).setView(b.root).setPositiveButton(getString(R.string.p0_them),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_nhap_ten_tu_1_den_100_ky_tu);return@setOnClickListener}
   val options=b.extra.text.toString().split(',').map{it.trim()}.filter{it.isNotEmpty()}.distinct()
   if(b.choice.selectedItemPosition==4&&(options.isEmpty()||options.size>30||options.any{it.length>100})){b.extraBox.error=getString(R.string.p0_nhap_tu_1_den_30_lua_chon_moi);return@setOnClickListener}
   vm.mutate("POST","$path/fields",JsonObject().apply{addProperty("name",name);addProperty("type",types[b.choice.selectedItemPosition]);add("options",Gson().toJsonTree(options));addProperty("expectedSchemaVersion",vm.data.value?.get("schemaVersion")?.asInt?:1)}){if(isAdded)d.dismiss()}
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
  var fields=emptyList<JsonObject>();var members=emptyList<JsonObject>()
  override fun onCreateViewHolder(parent:ViewGroup,type:Int)=GridHolder(LinearLayout(parent.context).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);isClickable=true;isFocusable=true})
  override fun onBindViewHolder(h:GridHolder,position:Int){val row=getItem(position);h.line.removeAllViews();val cells=row.getAsJsonObject("cells")?:JsonObject();fields.forEach{f->h.line.addView(gridCell(cellLabel(requireContext(),f,cells.get(f.text("id")),members)))};h.line.setBackgroundColor(android.graphics.Color.parseColor(if(position%2==0)"#FFFFFF" else "#F1F5F9"));h.line.contentDescription=fields.joinToString(", "){it.text("name")+": "+cellLabel(requireContext(),it,cells.get(it.text("id")),members)};h.line.setOnClickListener{click(row.text("id"))}}
 }
 private class GridHolder(val line:LinearLayout):RecyclerView.ViewHolder(line)
 override fun onDestroyView(){dateDialog?.dismiss();dateDialog=null;dialog?.dismiss();dialog=null;binding?.grid?.adapter=null;binding=null;schema="";viewSignature="";super.onDestroyView()}
}

internal fun operatorLabel(context:android.content.Context,operator:String):String=when(operator){
 "eq"->R.string.p0_operator_eq;"ne"->R.string.p0_operator_ne;"contains"->R.string.p0_operator_contains;"isEmpty"->R.string.p0_operator_empty;"isMe"->R.string.p0_operator_me;"lt"->R.string.p0_operator_lt;"lte"->R.string.p0_operator_lte;"gt"->R.string.p0_operator_gt;"gte"->R.string.p0_operator_gte;else->null
}?.let{context.getString(it)}?:operator
internal fun cellLabel(context:android.content.Context,field:JsonObject?,value:JsonElement?,members:List<JsonObject>):String{
 if(value==null||value.isJsonNull)return context.getString(R.string.p0_cell_empty)
 if(!value.isJsonPrimitive)return value.toString()
 val text=value.asString
 return when(field?.text("type")){
  "CHECKBOX"->context.getString(if(text=="true")R.string.p0_cell_yes else R.string.p0_cell_no)
  "USER"->members.firstOrNull{it.text("uid")==text}?.text("displayName")?.takeIf{it.isNotBlank()}?:context.getString(R.string.p0_missing_member,text)
  "DATE"->runCatching{val day=LocalDate.parse(text);"%02d/%02d/%04d".format(day.dayOfMonth,day.monthValue,day.year)}.getOrDefault(text)
  else->text
 }
}
