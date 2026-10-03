package com.threeddk.tasks.ui
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.*
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.FragmentDetailBinding
import kotlinx.coroutines.launch
class DetailFragment:Fragment(){
 private var binding:FragmentDetailBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private val id get()=requireArguments().getString("taskId")!!
 private var draftLoaded=false
 private var hasDraft=false
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentDetailBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!
  b.refresh.setOnClickListener{vm.detail(id)}
  b.edit.setOnClickListener{findNavController().navigate(R.id.editor,bundleOf("taskId" to id))}
  b.start.setOnClickListener{action("start")}
  b.approve.setOnClickListener{MaterialAlertDialogBuilder(requireContext()).setMessage("Xác nhận kết quả đang chờ duyệt đã hoàn thành?").setPositiveButton("Duyệt"){_,_->action("review","approve" to true,"submissionId" to vm.task(id)?.currentSubmissionId)}.setNegativeButton("Hủy",null).show()}
  b.reject.setOnClickListener{reason("Lý do yêu cầu sửa"){action("review","approve" to false,"submissionId" to vm.task(id)?.currentSubmissionId,"reason" to it)}}
  b.reopen.setOnClickListener{reason("Lý do mở lại"){action("reopen","reason" to it)}}
  b.archive.setOnClickListener{MaterialAlertDialogBuilder(requireContext()).setMessage("Lưu trữ công việc? Lịch sử được giữ và nhắc hạn sẽ dừng.").setPositiveButton("Lưu trữ"){_,_->action("archive")}.setNegativeButton("Hủy",null).show()}
  fun save(){if(draftLoaded)vm.task(id)?.let{task->hasDraft=b.result.text?.isNotEmpty()==true||b.link.text?.isNotEmpty()==true;vm.saveDraft(task,b.result.text.toString(),b.link.text.toString())}}
  b.result.doAfterTextChanged{save()};b.link.doAfterTextChanged{save()}
  b.submit.setOnClickListener{
   val task=vm.task(id)?:return@setOnClickListener
   val error=submissionError(b.result.text.toString(),b.link.text.toString())
   if(error!=null){vm.message.value=error;return@setOnClickListener}
   vm.execute {
    vm.repo.saveDraft(task,b.result.text.toString(),b.link.text.toString())
    vm.repo.submit(task);vm.repo.detail(id)
    if(binding===b){draftLoaded=false;b.result.setText("");b.link.setText("");hasDraft=false;draftLoaded=true;render()}
    vm.message.value="Đã nộp kết quả, chờ duyệt."
   }
  }
  viewLifecycleOwner.lifecycleScope.launch{
   val draft=vm.repo.draft(id)
   if(binding!==b)return@launch
   b.result.setText(draft?.description.orEmpty());b.link.setText(draft?.link.orEmpty());hasDraft=draft!=null;draftLoaded=true;render()
  }
  watch(vm.rows){render()};watch(vm.loading){busy->listOf(b.start,b.approve,b.reject,b.reopen,b.archive,b.edit,b.refresh).forEach{it.isEnabled=!busy};b.submit.isEnabled=!busy&&vm.task(id)?.canSubmit(vm.repo.uid.orEmpty())==true;b.result.isEnabled=!busy;b.link.isEnabled=!busy}
  render();vm.detail(id)
 }
 private fun action(name:String,vararg extra:Pair<String,Any?>){
  val t=vm.task(id)?:return
  vm.mutation("POST","tasks/$id/$name",vm.repo.body("expectedVersion" to t.version,*extra)){
   if(name=="archive"){if(isAdded)findNavController().popBackStack()} else vm.detail(id)
  }
 }
 private fun reason(title:String,save:(String)->Unit){
  val input=EditText(requireContext()).apply{hint="Nhập lý do";minLines=2;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(5000))}
  val dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(input).setPositiveButton("Xác nhận",null).setNegativeButton("Hủy",null).create()
  dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{if(input.text.isBlank())input.error="Lý do là bắt buộc." else{save(input.text.toString().trim());dialog.dismiss()}}};dialog.show()
 }
 private fun render(){
  val b=binding?:return;val t=vm.task(id)
  val leader=vm.me?.role=="LEADER";val writable=t?.archivedAt==null&&t!=null
  b.title.text=t?.title?:"Đang tải công việc"
  val members=vm.records<Member>("members").associateBy{it.uid}
  fun name(uid:String)=members[uid]?.displayName?:uid
  b.summary.text=t?.let{"${statusLabels[it.status]} • ${priorityLabels[it.priority]}\nHạn: ${formattedTime(it.dueAt)}\nĐại diện: ${name(it.representativeId)}\nCùng làm: ${it.assigneeIds.joinToString(", "){uid->name(uid)}}${if(it.archivedAt!=null)"\nĐã lưu trữ" else ""}"}?:"Kết nối mạng để tải lần đầu. Bản nháp đã lưu vẫn được giữ."
  b.description.text=t?.description
  b.edit.isVisible=leader&&writable&&t?.status in listOf("TODO","IN_PROGRESS")
  b.start.isVisible=writable&&t?.status=="TODO"&&t.representativeId==vm.repo.uid
  b.approve.isVisible=leader&&writable&&t?.status=="IN_REVIEW";b.reject.isVisible=b.approve.isVisible
  b.reopen.isVisible=leader&&writable&&t?.status=="DONE";b.archive.isVisible=leader&&writable
  val canSubmit=t?.canSubmit(vm.repo.uid.orEmpty())==true
  listOf(b.resultBox,b.linkBox,b.draftHint).forEach{it.isVisible=canSubmit||hasDraft};b.submit.isVisible=canSubmit;b.submit.isEnabled=canSubmit&&!vm.loading.value
  b.draftHint.text=if(canSubmit)"Bản nháp tự lưu trên thiết bị; chỉ gửi khi bấm Nộp kết quả." else "Công việc đã thay đổi. Bản nháp được giữ để bạn sao chép; không thể gửi."
  val h=vm.history(id)
  b.history.text=(h.submissions.map{s->"${formattedTime(s.createdAt)} • ${name(s.submittedBy)}\n${s.description}\n${s.link}\n${when(s.reviewStatus){"APPROVED"->"Đã duyệt";"REJECTED"->"Yêu cầu sửa: ${s.reason}";else->"Chờ duyệt"}}"+(s.reviewedBy?.let{" • ${name(it)}"}?:"")}+h.events.map{"${formattedTime(it.createdAt)} • ${name(it.actorId)}: ${eventLabels[it.kind]?:it.kind}"}).joinToString("\n\n").ifBlank{"Chưa có lịch sử đã tải."}
 }
 override fun onDestroyView(){binding=null;draftLoaded=false;super.onDestroyView()}
}

