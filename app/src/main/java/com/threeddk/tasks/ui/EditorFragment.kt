package com.threeddk.tasks.ui
import com.threeddk.tasks.R
import com.threeddk.tasks.model.Member
import com.threeddk.tasks.model.formattedTime
import com.threeddk.tasks.model.priorityLabels
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.AppViewModel
import com.threeddk.tasks.viewmodel.TaskEditorViewModel
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.databinding.FragmentEditorBinding
import java.time.Instant
import java.util.*
class EditorFragment:Fragment(){
 private var binding:FragmentEditorBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private val id get()=arguments?.getString("taskId").orEmpty()
 private val editor:TaskEditorViewModel by viewModels()
 private val selected get()=editor.state.value.selected
 private val representative get()=editor.state.value.representative
 private val due get()=editor.state.value.due
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentEditorBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;val task=vm.task(id)
  if(vm.me?.role!="LEADER" || (id.isNotEmpty()&&task==null)){vm.message.value="Bạn không thể sửa công việc này.";findNavController().popBackStack();return}
  editor.open(task)
  b.title.setText(editor.state.value.title);b.description.setText(editor.state.value.description)
  b.priority.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,priorityLabels.values.toList())
  b.priority.setSelection(priorityLabels.keys.indexOf(editor.state.value.priority).coerceAtLeast(0))
  fun input(){editor.input(b.title.text.toString(),b.description.text.toString(),priorityLabels.keys.toList()[b.priority.selectedItemPosition.coerceAtLeast(0)])}
  b.title.doAfterTextChanged{input()};b.description.doAfterTextChanged{input()}
  b.priority.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){input()}
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
  }
  b.deadline.setOnClickListener{
   val cal=Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply{timeInMillis=Instant.parse(due).toEpochMilli()}
   DatePickerDialog(requireContext(),{_,y,m,d->
    TimePickerDialog(requireContext(),{_,h,min->cal.set(y,m,d,h,min,0);cal.set(Calendar.MILLISECOND,0);editor.due(cal.toInstant().toString());render()},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show()
   },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
  }
  b.assignees.setOnClickListener{
   val users=vm.assignableMembers()
   val choice=selected.toMutableSet()
   MaterialAlertDialogBuilder(requireContext()).setTitle("Người được giao").setMultiChoiceItems(users.map{it.displayName}.toTypedArray(),users.map{it.uid in choice}.toBooleanArray()){_,which,checked->if(checked)choice.add(users[which].uid) else choice.remove(users[which].uid)}.setPositiveButton("Chọn"){_,_->
    editor.assign(choice);render()
   }.setNegativeButton("Hủy",null).show()
  }
  b.representative.setOnClickListener{
   val users=vm.selectedMembers(selected)
   if(users.isEmpty()){vm.message.value="Chọn người được giao trước.";return@setOnClickListener}
   MaterialAlertDialogBuilder(requireContext()).setTitle("Người đại diện nộp kết quả").setItems(users.map{it.displayName}.toTypedArray()){_,i->editor.representative(users[i].uid);render()}.show()
  }
  b.save.setOnClickListener{
   val latest=vm.task(id)
   if(latest!=null&&latest.version!=editor.version){
    val names=vm.records<Member>("members").associateBy{it.uid}
    MaterialAlertDialogBuilder(requireContext()).setTitle("Công việc đã thay đổi")
     .setMessage("Bản mới trên máy chủ:\n${latest.title}\n${latest.description}\nHạn: ${formattedTime(latest.dueAt)}\nƯu tiên: ${priorityLabels[latest.priority]}\nĐược giao: ${latest.assigneeIds.joinToString{names[it]?.displayName?:it}}\nĐại diện: ${names[latest.representativeId]?.displayName?:latest.representativeId}\n\nChọn bản cần dùng, kiểm tra rồi bấm Lưu lại.")
     .setPositiveButton("Dùng bản mới"){_,_->b.title.setText(latest.title);b.description.setText(latest.description);editor.useRemote(latest);b.priority.setSelection(priorityLabels.keys.indexOf(latest.priority));editor.reconcile(latest.version);render()}
     .setNeutralButton("Giữ bản soạn"){_,_->editor.reconcile(latest.version)}
     .setNegativeButton("Hủy",null).show()
    return@setOnClickListener
   }
   input()
   b.titleBox.error=editor.titleError;b.descriptionBox.error=editor.descriptionError
   editor.validationError()?.let{vm.message.value=it;return@setOnClickListener}
   vm.saveTask(id,editor.request()){if(isAdded)findNavController().popBackStack()}

  }
  watch(vm.loading){b.save.isEnabled=!it}
  watch(editor.state){render()}
 }
 private fun render(){val b=binding?:return;val users=vm.records<Member>("members").associateBy{it.uid};b.deadline.text="Hạn: ${formattedTime(due)} (giờ Việt Nam)";b.selected.text=selected.map{users[it]?.displayName?:it}.joinToString(", ");b.representative.text="Đại diện: ${users[representative]?.displayName?:"Chưa chọn"}"}
 override fun onDestroyView(){binding=null;super.onDestroyView()}
}
