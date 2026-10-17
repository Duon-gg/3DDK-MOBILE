package com.threeddk.tasks.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import android.text.InputType
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.RecordViewModel
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.*
import java.time.LocalDate

class RecordEditorFragment:Fragment(){
 private var binding:FragmentRecordEditorBinding?=null
 private val vm:RecordViewModel by viewModels()
 private var rendered=false
 private var schema=""
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 private var historyRequested=false
 private var dateDialog:DatePickerDialog?=null
 private val recordId get()=requireArguments().getString("recordId")?:"new"
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentRecordEditorBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;setHasOptionsMenu(true);b.save.setOnClickListener{vm.submit()};b.reconcile.setOnClickListener{resolveConflict()}
  watch(vm.ready){if(it&&!rendered)render();requireActivity().invalidateOptionsMenu()}
  watch(vm.table){if(it==null){b.fields.removeAllViews();dialog?.dismiss();dateDialog?.dismiss();rendered=false;schema="";requireActivity().invalidateOptionsMenu()}else{val signature=it.objects("fields").toString()+it.objects("members").toString();if(vm.ready.value&&signature!=schema)render()}}
  watch(vm.status){b.status.text=it;updateEnabled()};watch(vm.busy){updateEnabled()}
  watch(vm.creationPending){if(it){dialog?.dismiss();dateDialog?.dismiss()};updateEnabled()}
  watch(vm.conflict){b.reconcile.isVisible=it;if(!it&&vm.ready.value)render();updateEnabled()};watch(vm.finished){if(it)findNavController().popBackStack()}
  watch(vm.history){if(historyRequested&&it!=null){historyRequested=false;showHistory(it)}}
  vm.open(requireArguments().getString("path")!!,recordId)
 }
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){if(vm.canArchive)menu.add(0,20,0,getString(R.string.p0_luu_tru_dong));if(recordId!="new"&&!recordId.startsWith("new/")&&vm.ready.value)menu.add(0,21,1,getString(R.string.p0_lich_su_thay_doi));if(vm.ready.value||vm.raw.size()>0)menu.add(0,22,2,getString(R.string.p0_sao_chep_ban_nhap))}
 override fun onOptionsItemSelected(item:MenuItem):Boolean{
  when(item.itemId){
   20->dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_luu_tru_dong_nay)).setMessage(getString(R.string.p0_dong_se_bien_mat_khoi_bang_nhap_tren)).setPositiveButton(getString(R.string.p0_luu_tru)){_,_->vm.archive()}.setNegativeButton(getString(R.string.p0_huy),null).show()
   21->{historyRequested=true;if(vm.history.value!=null){historyRequested=false;showHistory(vm.history.value!!)}else vm.loadHistory()}
   22->copyDraft()
   else->return super.onOptionsItemSelected(item)
  };return true
 }
 private fun copyDraft(){
  val fields=vm.table.value?.objects("fields").orEmpty();val members=vm.table.value?.objects("members").orEmpty()
  val text=vm.raw.entrySet().joinToString("\n"){(id,value)->val field=fields.firstOrNull{it.text("id")==id};(field?.text("name")?:id)+": "+cellLabel(requireContext(),field,value,members)}
  val clipboard=requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
  clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.p0_ban_nhap_3ddk),text));Toast.makeText(requireContext(),getString(R.string.p0_da_sao_chep_nhap),Toast.LENGTH_SHORT).show()
 }
 private fun updateEnabled(){
  val b=binding?:return;b.save.isEnabled=vm.ready.value&&vm.writable&&!vm.busy.value&&!vm.conflict.value;b.save.isVisible=vm.writable;b.reconcile.isEnabled=!vm.busy.value
  b.creationPending.isVisible=vm.creationPending.value;b.save.setText(if(vm.creationPending.value)R.string.p0_retry_submit else R.string.p0_luu_len_bang)
  fun enable(view:View){view.isEnabled=vm.writable&&!vm.busy.value&&!vm.conflict.value&&!vm.creationPending.value;if(view is ViewGroup)for(i in 0 until view.childCount)enable(view.getChildAt(i))}
  enable(b.fields)
 }
 private fun render(){
  val b=binding?:return;val table=vm.table.value?:return;rendered=true;schema=table.objects("fields").toString()+table.objects("members").toString();b.fields.removeAllViews()
  table.objects("fields").forEach{field->
   val f=ItemFieldEditorBinding.inflate(layoutInflater,b.fields,false);val id=field.text("id");val value=vm.raw.get(id)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
   f.inputBox.hint=field.text("name");f.input.setText(value);f.input.isEnabled=vm.writable
   when(field.text("type")){
    "CHECKBOX"->{f.inputBox.isVisible=false;f.check.isVisible=true;f.check.text=field.text("name");f.check.isChecked=value=="true";f.check.isEnabled=vm.writable;f.check.setOnCheckedChangeListener{_,checked->vm.change(id,checked.toString())}}
    "SELECT"->{
     f.inputBox.isVisible=false;f.label.isVisible=true;f.label.text=field.text("name");f.options.isVisible=true;f.options.contentDescription=field.text("name");f.options.isEnabled=vm.writable
     val configured=field.getAsJsonArray("options").map{it.asString};val options=listOf("")+configured+if(value.isNotBlank()&&value !in configured)listOf(value) else emptyList()
     val labels=options.map{if(it.isEmpty())getString(R.string.p0_cell_empty) else if(it !in configured)getString(R.string.p0_old_option,it) else it}
     f.options.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,labels);f.options.setSelection(options.indexOf(value).coerceAtLeast(0))
     f.options.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,row:Long){val next=options[position];if(next!=(vm.raw.get(id)?.takeUnless{it.isJsonNull}?.asString?:""))vm.change(id,next)};override fun onNothingSelected(p:AdapterView<*>?){} }
    }
    "USER","DATE"->{
     f.input.isFocusable=false;f.input.isClickable=true;f.input.minHeight=(48*resources.displayMetrics.density).toInt()
     f.input.setText(if(value.isBlank())"" else cellLabel(requireContext(),field,vm.raw.get(id),table.objects("members")))
     f.input.contentDescription=getString(if(field.text("type")=="USER")R.string.p0_choose_member_field else R.string.p0_choose_date_field,field.text("name"))
     f.inputBox.endIconMode=TextInputLayout.END_ICON_CUSTOM;f.inputBox.setEndIconDrawable(android.R.drawable.ic_menu_close_clear_cancel);f.inputBox.endIconContentDescription=getString(R.string.p0_clear_field,field.text("name"))
     f.inputBox.setEndIconOnClickListener{vm.change(id,"");f.input.setText("")}
     f.input.setOnClickListener{
      if(!vm.writable||vm.busy.value)return@setOnClickListener
      if(field.text("type")=="USER")memberPicker(table.objects("members")){uid->vm.change(id,uid);f.input.setText(if(uid.isBlank())"" else cellLabel(requireContext(),field,vm.raw.get(id),table.objects("members")))}
      else {val date=runCatching{LocalDate.parse(vm.raw.get(id)?.asString)}.getOrDefault(LocalDate.now());dateDialog=DatePickerDialog(requireContext(),{_,year,month,day->vm.change(id,LocalDate.of(year,month+1,day).toString());f.input.setText(cellLabel(requireContext(),field,vm.raw.get(id),emptyList()))},date.year,date.monthValue-1,date.dayOfMonth).also{it.show()}}
     }
    }
    else->{if(field.text("type")=="NUMBER")f.input.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;f.input.doAfterTextChanged{vm.change(id,it.toString())}}
   };b.fields.addView(f.root)
  }
  val fields=table.objects("fields");val orphan=vm.raw.entrySet().filter{(id,_)->fields.none{it.text("id")==id}}
  if(orphan.isNotEmpty())b.fields.addView(TextView(requireContext()).apply{text=getString(R.string.p0_orphan_draft,orphan.joinToString("\n"){(id,value)->id+": "+cellLabel(requireContext(),null,value,emptyList())});setTextIsSelectable(true);setPadding(0,16,0,16)})
  updateEnabled()
 }
 private fun memberPicker(members:List<JsonObject>,done:(String)->Unit){
  val b=DialogMemberPickerBinding.inflate(layoutInflater);val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_chon_nguoi_phu_trach)).setView(b.root).setNeutralButton(getString(R.string.p0_bo_phan_cong)){_,_->done("")}.setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  val adapter=RowAdapter{uid->done(uid);d.dismiss()};b.members.layoutManager=LinearLayoutManager(requireContext());b.members.adapter=adapter
  fun show(query:String){val rows=members.filter{it.text("displayName").contains(query,true)}.map{Row(it.text("uid"),it.text("displayName"),roleLabel(requireContext(),it.text("role")))};adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()}
  b.search.doAfterTextChanged{show(it.toString())};show("");d.setOnDismissListener{b.members.adapter=null};d.show()
 }
 private fun resolveConflict(){
  val conflicts=vm.conflictDetails.value
  if(conflicts.isEmpty()){
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_nhan_phien_ban_moi)).setMessage(getString(R.string.p0_giu_nhung_o_ban_da_sua_nhan_cac)).setPositiveButton(getString(R.string.p0_nhan_va_kiem_tra)){_,_->vm.reconcile();rendered=false}.setNegativeButton(getString(R.string.p0_giu_nhap),null).show();return
  }
  val resolutions=mutableMapOf<String,Boolean>();val table=vm.table.value;val members=table?.objects("members").orEmpty()
  fun choose(index:Int){
   if(index==conflicts.size){vm.reconcile(resolutions);rendered=false;return}
   val item=conflicts[index];val field=table?.objects("fields")?.firstOrNull{it.text("id")==item.fieldId}
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_conflict_title,index+1,conflicts.size,item.name))
    .setMessage(getString(R.string.p0_conflict_values,cellLabel(requireContext(),field,item.base,members),cellLabel(requireContext(),field,item.remote,members),cellLabel(requireContext(),field,item.mine,members)))
    .setPositiveButton(getString(R.string.p0_giu_nhap_o_nay)){_,_->resolutions[item.fieldId]=true;choose(index+1)}.setNeutralButton(getString(R.string.p0_dung_may_chu)){_,_->resolutions[item.fieldId]=false;choose(index+1)}.setNegativeButton(getString(R.string.p0_de_sau),null).show()
  };choose(0)
 }
 private fun showHistory(history:JsonObject){
  val fields=vm.table.value?.objects("fields").orEmpty();val members=vm.table.value?.objects("members").orEmpty();val items=history.objects("items")
  val text=if(items.isEmpty())getString(R.string.p0_chua_co_lich_su_thay_doi_da_tai) else items.joinToString("\n\n"){event->
   val changes=event.getAsJsonObject("changes")?.entrySet()?.joinToString("\n"){(id,delta)->val field=fields.firstOrNull{it.text("id")==id};val change=delta.asJsonObject;(field?.text("name")?:id)+": "+cellLabel(requireContext(),field,change.get("before"),members)+" → "+cellLabel(requireContext(),field,change.get("after"),members)}.orEmpty()
   event.text("createdAt")+" • "+event.text("actorName").ifBlank{event.text("actorUid")}+"\n"+when(event.text("action")){"CREATE","CREATED","RECORD_CREATED"->getString(R.string.p0_tao_dong);"UPDATE","UPDATED","RECORD_UPDATED"->getString(R.string.p0_sua_dong);"ARCHIVE","ARCHIVED","RECORD_ARCHIVED"->getString(R.string.p0_luu_tru_dong);else->event.text("action")}+"\n"+changes
  }
  val body=TextView(requireContext()).apply{this.text=text;setPadding(24,16,24,16);setTextIsSelectable(true)}
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_50_thay_doi_gan_nhat)).setView(ScrollView(requireContext()).apply{addView(body)}).setPositiveButton(getString(R.string.p0_dong),null).show()
 }
 override fun onDestroyView(){dateDialog?.dismiss();dateDialog=null;dialog?.dismiss();dialog=null;binding=null;rendered=false;schema="";historyRequested=false;super.onDestroyView()}
}
