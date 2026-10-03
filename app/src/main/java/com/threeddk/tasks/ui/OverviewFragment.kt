package com.threeddk.tasks.ui
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
import com.threeddk.tasks.*
import com.threeddk.tasks.data.*
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
  val members=vm.records<Member>("members").associateBy{it.uid}
  val rows=when(kind){
   "members"->members.values.sortedBy{it.displayName}.map{Row(it.uid,it.displayName,(if(it.role=="LEADER")"Nhóm trưởng" else "Thành viên")+(if(it.active)"" else " • Đã ngừng quyền"),it.avatarUrl)}
   "notifications"->vm.records<Notice>("notifications").sortedByDescending{it.createdAt}.map{Row(it.id,(if(it.read)"" else "● ")+(eventLabels[it.kind]?:it.kind),formattedTime(it.createdAt))}
   else->if(b.filter.selectedItemPosition==7)vm.drafts.value.map{d->Row(d.taskId,vm.task(d.taskId)?.title?:"Bản nháp công việc đã thay đổi",d.description.take(120))} else vm.records<Task>("tasks").filter{t->t.archivedAt==null&&t.title.contains(b.search.text.toString(),true)&&when(val selected=b.filter.selectedItemPosition){
    1->vm.repo.uid in t.assigneeIds;2->t.overdue();in 3..6->t.status==statusLabels.keys.toList()[selected-3];else->true
   }}.sortedBy{it.dueAt}.map{t->Row(t.id,t.title,"${statusLabels[t.status]} • Ưu tiên ${priorityLabels[t.priority]}\n${if(t.overdue())"Quá hạn: " else "Hạn: "}${formattedTime(t.dueAt)}\nĐại diện: ${members[t.representativeId]?.displayName?:t.representativeId} • ${t.assigneeIds.size} người",members[t.representativeId]?.avatarUrl?:"",t.overdue())}
  }
  adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()
  b.empty.text=if(kind=="tasks"&&vm.records<Task>("tasks").isNotEmpty())"Không có công việc khớp bộ lọc." else "Chưa có dữ liệu. Kết nối mạng và bấm Làm mới."
 }
 private fun open(id:String){
  when(kind){
   "notifications"->{val n=vm.records<Notice>("notifications").first{it.id==id};vm.mutation("PATCH","notifications/$id",vm.repo.body());findNavController().navigate(R.id.detail,bundleOf("taskId" to n.taskId))}
   "members"->{val m=vm.records<Member>("members").first{it.uid==id}
    if(vm.me?.role=="LEADER"&&m.role!="LEADER")MaterialAlertDialogBuilder(requireContext()).setTitle(m.displayName).setMessage(m.email).setPositiveButton(if(m.active)"Ngừng quyền" else "Khôi phục"){_,_->vm.mutation("PATCH","members/$id",vm.repo.body("active" to !m.active))}.setNegativeButton("Đóng",null).show()
   }
   else->findNavController().navigate(R.id.detail,bundleOf("taskId" to id))
  }
 }
 private fun invitations(){
  vm.execute {
   val invites=vm.repo.invites()
   if(!isAdded||binding==null)return@execute
   MaterialAlertDialogBuilder(requireContext()).setTitle("Email được mời").setItems(invites.map{it.email}.toTypedArray()){_,which->
    MaterialAlertDialogBuilder(requireContext()).setMessage("Thu hồi lời mời cho ${invites[which].email}? Tài khoản đã tham gia cần được ngừng quyền ở danh sách thành viên.").setPositiveButton("Thu hồi"){_,_->vm.mutation("DELETE","invites",vm.repo.body("email" to invites[which].email))}.setNegativeButton("Hủy",null).show()
   }.setPositiveButton("Mời email"){_,_->val input=EditText(requireContext()).apply{hint="Email";inputType=android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT;minHeight=64}
    val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Mời thành viên").setView(input).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create()
    dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{val email=input.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())input.error="Email không hợp lệ" else{vm.mutation("POST","invites",vm.repo.body("email" to email));dialog.dismiss()}}};dialog.show()
   }.setNegativeButton("Đóng",null).show()
  }
 }
 override fun onDestroyView(){binding?.list?.adapter=null;binding=null;super.onDestroyView()}
}
