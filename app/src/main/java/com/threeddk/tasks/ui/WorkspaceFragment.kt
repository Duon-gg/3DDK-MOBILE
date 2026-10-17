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
  b.filter.isVisible=false;b.searchBox.hint=getString(R.string.p0_tim_ten);b.search.doAfterTextChanged{render()}
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
  b.heading.text=if(root)getString(R.string.p0_khong_gian_cua_ban) else if(invites)getString(R.string.p0_loi_moi_tham_gia) else data?.text("name")?:getString(R.string.p0_dang_tai)
  b.add.text=if(root)getString(R.string.p0_tao_khong_gian) else if(workspace)getString(R.string.p0_tao_co_so_du_lieu) else getString(R.string.p0_tao_bang)
  b.add.isVisible=!invites&&(root||data?.text("role") in listOf("OWNER","EDITOR"))
  val key=if(root||invites)"items" else if(workspace)"bases" else "tables"
  val rows=data?.objects(key).orEmpty().filter{it.text("name").contains(b.search.text.toString(),true)}.map{
   Row(it.text("id"),it.text("name"),if(root||invites)roleLabel(requireContext(),it.text("role")) else if(workspace)getString(R.string.p0_mo_cac_bang_du_lieu) else getString(R.string.p0_mo_bang_nhap_va_chinh_sua_du_lieu))
  }
  adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()
  b.empty.text=if(data==null)getString(R.string.p0_chua_co_du_lieu_da_tai_ket_noi) else if(root)getString(R.string.p0_chao_mung_den_3ddk_tao_khong_gian_dau) else if(invites)getString(R.string.p0_ban_chua_co_loi_moi_ban_van_co) else getString(R.string.p0_chua_co_du_lieu_bam_nut_tao_phia)
 }
 private fun open(id:String){
  if(path=="invitations"){
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_tham_gia_khong_gian)).setMessage(getString(R.string.p0_ban_se_co_quyen_nhu_ghi_trong_loi)).setPositiveButton(getString(R.string.p0_join_workspace)){_,_->vm.mutate("POST","invitations/$id/accept",JsonObject())}.setNegativeButton(getString(R.string.p0_de_sau),null).show();return
  }
  val next=if(path=="workspaces")"workspaces/$id" else if(path.startsWith("workspaces/"))"bases/$id" else "tables/$id"
  findNavController().navigate(if(next.startsWith("tables/"))R.id.dataTable else R.id.workspaceBrowser,bundleOf("path" to next))
 }
 private fun create(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten);b.extraBox.isVisible=false
  val canUseTemplate=path.startsWith("bases/")&&vm.data.value?.text("role")=="OWNER"
  b.choice.isVisible=canUseTemplate
  if(canUseTemplate){b.choice.contentDescription=getString(R.string.p0_loai_bang);b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.p0_bang_cong_viec_co_san_cot),getString(R.string.p0_bang_trong)))}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(binding?.add?.text).setView(b.root).setPositiveButton(getString(R.string.p0_tao),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_nhap_ten_tu_1_den_100_ky_tu);return@setOnClickListener}
   val target=if(path=="workspaces")path else if(path.startsWith("workspaces/"))"$path/bases" else "$path/tables"
   if(canUseTemplate&&b.choice.selectedItemPosition==0)vm.createTaskTable(name){if(isAdded)d.dismiss()}
   else vm.mutate("POST",target,JsonObject().apply{addProperty("name",name)}){if(isAdded)d.dismiss()}
  }};d.show()
 }
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){
  if(path.startsWith("workspaces/")&&vm.data.value!=null){menu.add(0,1,0,getString(R.string.p0_thanh_vien));if(vm.data.value?.text("role")=="OWNER"){menu.add(0,2,1,getString(R.string.p0_moi_thanh_vien));menu.add(0,3,2,getString(R.string.p0_loi_moi_da_gui))}}
 }
 override fun onOptionsItemSelected(item:MenuItem):Boolean{
  if(item.itemId==1){members();return true}
  if(item.itemId==2){invite();return true}
  if(item.itemId==3){sentInvites();return true}
  return super.onOptionsItemSelected(item)
 }
 private fun members(){
  val data=vm.data.value?:return
  val members=data.objects("members")
  val labels=members.map{it.text("displayName")+" • "+roleLabel(requireContext(),it.text("role"))}.toTypedArray()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_thanh_vien)).setItems(labels){_,index->
   val member=members[index]
   if(data.text("role")=="OWNER"&&vm.supports("memberAdmin")&&member.text("role")!="OWNER")manageMember(member)
  }.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun manageMember(member:JsonObject){
  val actions=arrayOf(getString(R.string.p0_cho_phep_chinh_sua),getString(R.string.p0_chuyen_sang_chi_xem),getString(R.string.p0_go_khoi_khong_gian),getString(R.string.p0_chuyen_quyen_chu_khong_gian))
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(member.text("displayName")).setItems(actions){_,which->
   val transfer=which==3
   val message=when(which){2->getString(R.string.p0_nguoi_nay_se_mat_quyen_truy_cap_khong);3->getString(R.string.p0_ban_se_chuyen_thanh_thanh_vien_co_the);else->getString(R.string.p0_new_role,getString(if(which==0)R.string.p0_co_the_chinh_sua else R.string.p0_chi_xem))}
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(actions[which]+"?").setMessage(member.text("displayName")+"\n\n"+message).setPositiveButton(getString(R.string.p0_xac_nhan)){_,_->
    val versionMember=if(transfer)vm.data.value?.objects("members")?.firstOrNull{it.text("role")=="OWNER"} else member
    val payload=JsonObject().apply{addProperty("expectedVersion",versionMember?.get("version")?.asInt?:1);if(transfer)addProperty("targetUid",member.text("uid")) else if(which<2)addProperty("role",if(which==0)"EDITOR" else "VIEWER")}
    vm.mutate(if(which==2)"DELETE" else if(transfer)"POST" else "PATCH",if(transfer)"$path/transfer-ownership" else "$path/members/${member.text("uid")}",payload)
   }.setNegativeButton(getString(R.string.p0_huy),null).show()
  }.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun sentInvites(){
  val invites=vm.data.value?.objects("invites").orEmpty()
  if(invites.isEmpty()){dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_loi_moi_da_gui)).setMessage(getString(R.string.p0_chua_co_loi_moi_nao_dang_cho)).setPositiveButton(getString(R.string.p0_dong),null).show();return}
  val labels=invites.map{it.text("email")+" • "+roleLabel(requireContext(),it.text("role"))}.toTypedArray()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_loi_moi_dang_cho)).setItems(labels){_,index->
   val invite=invites[index]
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_thu_hoi_loi_moi)).setMessage(invite.text("email")+getString(R.string.p0_se_khong_the_dung_loi_moi_nay_de)).setPositiveButton(getString(R.string.p0_thu_hoi)){_,_->vm.mutate("DELETE","$path/invites/${invite.text("id")}",JsonObject())}.setNegativeButton(getString(R.string.p0_giu_lai),null).show()
  }.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun invite(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_email_duoc_moi);b.value.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;b.extraBox.isVisible=false
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.p0_co_the_chinh_sua),getString(R.string.p0_chi_xem)))
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_moi_vao_khong_gian)).setMessage(getString(R.string.p0_nguoi_nhan_dang_nhap_bang_email_nay_va)).setView(b.root).setPositiveButton(getString(R.string.p0_moi),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val email=b.value.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){b.valueBox.error=getString(R.string.p0_email_chua_hop_le);return@setOnClickListener}
   vm.mutate("POST","$path/invites",JsonObject().apply{addProperty("email",email);addProperty("role",if(b.choice.selectedItemPosition==0)"EDITOR" else "VIEWER")}){if(isAdded)d.dismiss()}
  }};d.show()
 }
 override fun onDestroyView(){dialog?.dismiss();dialog=null;binding?.list?.adapter=null;binding=null;super.onDestroyView()}
}
fun roleLabel(context:android.content.Context,role:String)=context.getString(when(role){"OWNER"->R.string.p0_workspace_owner;"EDITOR"->R.string.p0_co_the_chinh_sua;else->R.string.p0_chi_xem})
