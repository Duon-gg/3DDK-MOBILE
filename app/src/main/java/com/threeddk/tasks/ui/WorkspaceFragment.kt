package com.threeddk.tasks.ui

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.*
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.*
import com.google.gson.JsonObject

class WorkspaceFragment:Fragment(){
 private var binding:FragmentOverviewBinding?=null
 private val vm:WorkspaceViewModel by viewModels()
 private val path get()=requireArguments().getString("path")?:"workspaces"
 private lateinit var adapter:RowAdapter
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentOverviewBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;adapter=RowAdapter(::open);b.list.layoutManager=LinearLayoutManager(requireContext());b.list.adapter=adapter
  b.filter.isVisible=false;b.searchBox.hint="Tìm tên";b.search.doAfterTextChanged{render()}
  b.refresh.setOnClickListener{vm.refresh()};b.add.setOnClickListener{create()}
  setHasOptionsMenu(true)
  watch(vm.data){render();requireActivity().invalidateOptionsMenu()}
  watch(vm.busy){b.refresh.isEnabled=!it;b.add.isEnabled=!it;dialog?.getButton(-1)?.isEnabled=!it}
  watch(vm.note){b.sync.text=it};watch(vm.error){if(it!=null){b.sync.text=it;dialog?.setMessage(it)}}
  vm.open(path)
 }
 private fun render(){
  val b=binding?:return;val data=vm.data.value
  val root=path=="workspaces";val invites=path=="invitations";val workspace=path.startsWith("workspaces/")
  b.heading.text=if(root)"Không gian của bạn" else if(invites)"Lời mời tham gia" else data?.text("name")?:"Đang tải…"
  b.add.text=if(root)"Tạo không gian" else if(workspace)"Tạo cơ sở dữ liệu" else "Tạo bảng"
  b.add.isVisible=!invites&&(root||data?.text("role") in listOf("OWNER","EDITOR"))
  val key=if(root||invites)"items" else if(workspace)"bases" else "tables"
  val rows=data?.objects(key).orEmpty().filter{it.text("name").contains(b.search.text.toString(),true)}.map{
   Row(it.text("id"),it.text("name"),if(root||invites)roleLabel(it.text("role")) else if(workspace)"Mở các bảng dữ liệu" else "Mở bảng • Nhập và chỉnh sửa dữ liệu")
  }
  adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()
  b.empty.text=if(data==null)"Chưa có dữ liệu đã tải. Kết nối mạng và bấm Làm mới." else if(root)"Chào mừng đến 3DDK!\nTạo không gian đầu tiên để bắt đầu." else if(invites)"Bạn chưa có lời mời.\nBạn vẫn có thể tự tạo không gian của mình." else "Chưa có dữ liệu. Bấm nút tạo phía trên để bắt đầu."
 }
 private fun open(id:String){
  if(path=="invitations"){
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Tham gia không gian?").setMessage("Bạn sẽ có quyền như ghi trong lời mời.").setPositiveButton("Tham gia"){_,_->vm.mutate("POST","invitations/$id/accept",JsonObject())}.setNegativeButton("Để sau",null).show();return
  }
  val next=if(path=="workspaces")"workspaces/$id" else if(path.startsWith("workspaces/"))"bases/$id" else "tables/$id"
  findNavController().navigate(if(next.startsWith("tables/"))R.id.dataTable else R.id.workspaceBrowser,bundleOf("path" to next))
 }
 private fun create(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint="Tên";b.extraBox.isVisible=false;b.choice.isVisible=false
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(binding?.add?.text).setView(b.root).setPositiveButton("Tạo",null).setNegativeButton("Hủy",null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error="Nhập tên từ 1 đến 100 ký tự";return@setOnClickListener}
   val target=if(path=="workspaces")path else if(path.startsWith("workspaces/"))"$path/bases" else "$path/tables"
   vm.mutate("POST",target,JsonObject().apply{addProperty("name",name)}){if(isAdded)d.dismiss()}
  }};d.show()
 }
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){
  if(path.startsWith("workspaces/")&&vm.data.value!=null){menu.add(0,1,0,"Thành viên");if(vm.data.value?.text("role")=="OWNER")menu.add(0,2,1,"Mời thành viên")}
 }
 override fun onOptionsItemSelected(item:MenuItem):Boolean{
  if(item.itemId==1){val data=vm.data.value?:return true;dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Thành viên").setMessage(data.objects("members").joinToString("\n"){it.text("displayName")+" • "+roleLabel(it.text("role"))}).setPositiveButton("Đóng",null).show();return true}
  if(item.itemId==2){invite();return true};return super.onOptionsItemSelected(item)
 }
 private fun invite(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint="Email được mời";b.value.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;b.extraBox.isVisible=false
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Có thể chỉnh sửa","Chỉ xem"))
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle("Mời vào không gian").setMessage("Người nhận đăng nhập bằng email này và mở mục Lời mời. Lời mời có hiệu lực 7 ngày.").setView(b.root).setPositiveButton("Mời",null).setNegativeButton("Hủy",null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val email=b.value.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){b.valueBox.error="Email chưa hợp lệ";return@setOnClickListener}
   vm.mutate("POST","$path/invites",JsonObject().apply{addProperty("email",email);addProperty("role",if(b.choice.selectedItemPosition==0)"EDITOR" else "VIEWER")}){if(isAdded)d.dismiss()}
  }};d.show()
 }
 override fun onDestroyView(){dialog?.dismiss();dialog=null;binding?.list?.adapter=null;binding=null;super.onDestroyView()}
}
fun roleLabel(role:String)=when(role){"OWNER"->"Chủ không gian";"EDITOR"->"Có thể chỉnh sửa";else->"Chỉ xem"}
