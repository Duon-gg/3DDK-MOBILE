package com.threeddk.tasks.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import android.text.InputType
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.RecordViewModel
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.*

class RecordEditorFragment:Fragment(){
 private var binding:FragmentRecordEditorBinding?=null
 private val vm:RecordViewModel by viewModels()
 private var rendered=false
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentRecordEditorBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;b.save.setOnClickListener{vm.submit()}
  b.reconcile.setOnClickListener{MaterialAlertDialogBuilder(requireContext()).setTitle("Dùng phiên bản mới?").setMessage("Giữ nội dung nháp của bạn và nhận phiên bản mới nhất. Khi lưu tiếp, nội dung nháp sẽ thay thế dòng đó.").setPositiveButton("Tải phiên bản mới"){_,_->vm.reconcile()}.setNegativeButton("Giữ nháp",null).show()}
  watch(vm.ready){if(it&&!rendered)render()};watch(vm.status){b.status.text=it;updateEnabled()};watch(vm.busy){updateEnabled()}
  watch(vm.conflict){b.reconcile.isVisible=it;updateEnabled()};watch(vm.finished){if(it)findNavController().popBackStack()}
  vm.open(requireArguments().getString("path")!!,requireArguments().getString("recordId")?:"new")
 }
 private fun updateEnabled(){binding?.save?.isEnabled=vm.ready.value&&vm.writable&&!vm.busy.value&&!vm.conflict.value;binding?.reconcile?.isEnabled=!vm.busy.value}
 private fun render(){
  val b=binding?:return;val table=vm.table.value?:return;rendered=true;b.fields.removeAllViews()
  table.objects("fields").forEach{field->
   val f=ItemFieldEditorBinding.inflate(layoutInflater,b.fields,false);val id=field.text("id");val value=vm.raw.get(id)?.takeUnless{it.isJsonNull}?.asString.orEmpty()
   f.inputBox.hint=field.text("name");f.input.setText(value);f.input.isEnabled=vm.writable
   when(field.text("type")){
    "CHECKBOX"->{f.inputBox.isVisible=false;f.check.isVisible=true;f.check.text=field.text("name");f.check.isChecked=value=="true";f.check.isEnabled=vm.writable;f.check.setOnCheckedChangeListener{_,checked->vm.change(id,checked.toString())}}
    "SELECT"->{
     f.inputBox.isVisible=false;f.label.isVisible=true;f.label.text=field.text("name");f.options.isVisible=true;f.options.contentDescription=field.text("name");f.options.isEnabled=vm.writable
     val options=listOf("—")+field.getAsJsonArray("options").map{it.asString};f.options.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,options);f.options.setSelection(options.indexOf(value).coerceAtLeast(0))
     f.options.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,row:Long){val next=if(position==0)"" else options[position];if(next!=(vm.raw.get(id)?.takeUnless{it.isJsonNull}?.asString?:""))vm.change(id,next)};override fun onNothingSelected(p:AdapterView<*>?){}}
    }
    else->{if(field.text("type")=="NUMBER")f.input.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;if(field.text("type")=="DATE")f.inputBox.helperText="YYYY-MM-DD, ví dụ 2026-10-04";f.input.doAfterTextChanged{vm.change(id,it.toString())}}
   }
   b.fields.addView(f.root)
  };updateEnabled()
 }
 override fun onDestroyView(){binding=null;rendered=false;super.onDestroyView()}
}
