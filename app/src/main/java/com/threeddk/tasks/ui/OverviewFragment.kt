package com.threeddk.tasks.ui
import com.threeddk.tasks.model.Member
import com.threeddk.tasks.model.Notice
import com.threeddk.tasks.model.Task
import com.threeddk.tasks.model.statusLabels
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.AppViewModel
import com.threeddk.tasks.adapter.RowAdapter
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.FragmentOverviewBinding
class OverviewFragment:Fragment(){
 private var binding:FragmentOverviewBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private val kind get()=requireArguments().getString("kind")?:"tasks"
 private lateinit var adapter:RowAdapter
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentOverviewBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;adapter=RowAdapter{open(it)};b.list.layoutManager=LinearLayoutManager(requireContext());b.list.adapter=adapter
  b.heading.text=when(kind){"members"->"Nhóm 3DDK";"notifications"->"Thông báo";else->"Công việc"}
  b.searchBox.isVisible=kind=="tasks";b.filter.isVisible=kind=="tasks"
  b.filter.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Tất cả","Của tôi","Quá hạn")+statusLabels.values+"Bản nháp")
  b.filter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){render()};override fun onNothingSelected(p:AdapterView<*>?){}}
  b.search.doAfterTextChanged{render()};b.refresh.setOnClickListener{vm.refresh()}
  b.add.setOnClickListener{if(kind=="tasks")findNavController().navigate(R.id.editor) else invitations()}
  watch(vm.rows){render()};watch(vm.drafts){render()};watch(vm.syncNote){b.sync.text=it};watch(vm.loading){b.refresh.isEnabled=!it;b.add.isEnabled=!it}
  vm.refresh()
 }
 private fun render(){
  val b=binding?:return
  b.add.isVisible=vm.me?.role=="LEADER"&&kind!="notifications";b.add.text=if(kind=="members")"Lời mời" else "Tạo công việc"
  val rows=vm.overviewRows(kind,b.filter.selectedItemPosition,b.search.text.toString())
  adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()
  b.empty.text=if(kind=="tasks"&&vm.records<Task>("tasks").isNotEmpty())"Không có công việc khớp bộ lọc." else "Chưa có dữ liệu. Kết nối mạng và bấm Làm mới."
 }
 private fun open(id:String){
  when(kind){
   "notifications"->{val n=vm.records<Notice>("notifications").first{it.id==id};vm.markNoticeRead(id);findNavController().navigate(R.id.detail,bundleOf("taskId" to n.taskId))}
   "members"->{val m=vm.records<Member>("members").first{it.uid==id}
    if(vm.me?.role=="LEADER"&&m.role!="LEADER")MaterialAlertDialogBuilder(requireContext()).setTitle(m.displayName).setMessage(m.email).setPositiveButton(if(m.active)"Ngừng quyền" else "Khôi phục"){_,_->vm.setMemberActive(id,!m.active)}.setNegativeButton("Đóng",null).show()
   }
   else->findNavController().navigate(R.id.detail,bundleOf("taskId" to id))
  }
 }
 private fun invitations(){
  vm.loadInvites {invites->
   if(!isAdded||binding==null)return@loadInvites
   MaterialAlertDialogBuilder(requireContext()).setTitle("Email được mời").setItems(invites.map{it.email}.toTypedArray()){_,which->
    MaterialAlertDialogBuilder(requireContext()).setMessage("Thu hồi lời mời cho ${invites[which].email}? Tài khoản đã tham gia cần được ngừng quyền ở danh sách thành viên.").setPositiveButton("Thu hồi"){_,_->vm.revokeInvite(invites[which].email)}.setNegativeButton("Hủy",null).show()
   }.setPositiveButton("Mời email"){_,_->val input=EditText(requireContext()).apply{hint="Email";inputType=android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT;minHeight=64}
    val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Mời thành viên").setView(input).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create()
    dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{val email=input.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())input.error="Email không hợp lệ" else{vm.inviteEmail(email);dialog.dismiss()}}};dialog.show()
   }.setNegativeButton("Đóng",null).show()
  }
 }
 override fun onDestroyView(){binding?.list?.adapter=null;binding=null;super.onDestroyView()}
}
