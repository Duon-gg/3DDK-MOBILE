package com.threeddk.tasks.ui
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.AppViewModel
import com.threeddk.tasks.data.*
import com.threeddk.tasks.databinding.FragmentEditorBinding
import java.time.Instant
import java.util.*
class EditorFragment:Fragment(){
 private var binding:FragmentEditorBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private val id get()=arguments?.getString("taskId").orEmpty()
 private var selected=mutableSetOf<String>()
 private var representative=""
 private var due=Instant.now().plusSeconds(86400).toString()
 private var requestId=UUID.randomUUID().toString()
 private var version=1
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentEditorBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;val task=vm.task(id)
  if(vm.me?.role!="LEADER" || (id.isNotEmpty()&&task==null)){vm.message.value="Bạn không thể sửa công việc này.";findNavController().popBackStack();return}
  if(task!=null){b.title.setText(task.title);b.description.setText(task.description);selected=task.assigneeIds.toMutableSet();representative=task.representativeId;due=task.dueAt;version=task.version}
  if(state!=null){selected=state.getStringArrayList("selected")?.toMutableSet()?:selected;representative=state.getString("representative",representative);due=state.getString("due",due);requestId=state.getString("requestId",requestId);version=state.getInt("version",version)}
  b.priority.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,priorityLabels.values.toList())
  b.priority.setSelection(priorityLabels.keys.indexOf(task?.priority?:"NORMAL").coerceAtLeast(0))
  fun changed(){requestId=UUID.randomUUID().toString()}
  b.title.doAfterTextChanged{changed()};b.description.doAfterTextChanged{changed()}
  b.deadline.setOnClickListener{
   val cal=Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply{timeInMillis=Instant.parse(due).toEpochMilli()}
   DatePickerDialog(requireContext(),{_,y,m,d->
    TimePickerDialog(requireContext(),{_,h,min->cal.set(y,m,d,h,min,0);cal.set(Calendar.MILLISECOND,0);due=cal.toInstant().toString();changed();render()},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show()
   },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()
  }
  b.assignees.setOnClickListener{
   val users=vm.records<Member>("members").filter{it.active}
   val choice=selected.toMutableSet()
   MaterialAlertDialogBuilder(requireContext()).setTitle("Người được giao").setMultiChoiceItems(users.map{it.displayName}.toTypedArray(),users.map{it.uid in choice}.toBooleanArray()){_,which,checked->if(checked)choice.add(users[which].uid) else choice.remove(users[which].uid)}.setPositiveButton("Chọn"){_,_->
    selected=choice;if(representative !in selected)representative=selected.firstOrNull().orEmpty();changed();render()
   }.setNegativeButton("Hủy",null).show()
  }
  b.representative.setOnClickListener{
   val users=vm.records<Member>("members").filter{it.uid in selected}
   if(users.isEmpty()){vm.message.value="Chọn người được giao trước.";return@setOnClickListener}
   MaterialAlertDialogBuilder(requireContext()).setTitle("Người đại diện nộp kết quả").setItems(users.map{it.displayName}.toTypedArray()){_,i->representative=users[i].uid;changed();render()}.show()
  }
  b.save.setOnClickListener{
   val title=b.title.text.toString().trim();val description=b.description.text.toString().trim()
   b.titleBox.error=if(title.isBlank())"Nhập tiêu đề." else null;b.descriptionBox.error=if(description.isBlank())"Mô tả đầu ra cần bàn giao." else null
   if(title.isBlank()||description.isBlank())return@setOnClickListener
   if(selected.isEmpty()||representative !in selected){vm.message.value="Chọn thành viên và người đại diện.";return@setOnClickListener}
   val payload=vm.repo.body("requestId" to requestId,"expectedVersion" to version,"title" to title,"description" to description,"dueAt" to due,"priority" to priorityLabels.keys.toList()[b.priority.selectedItemPosition],"assigneeIds" to selected.sorted(),"representativeId" to representative)
   vm.mutation(if(id.isEmpty())"POST" else "PATCH",if(id.isEmpty())"tasks" else "tasks/$id",payload){if(isAdded)findNavController().popBackStack()}
  }
  watch(vm.loading){b.save.isEnabled=!it}
  // A conflict refreshes the shared task; next explicit save uses its new version.
  watch(vm.rows){vm.task(id)?.let{latest->if(latest.version!=version){version=latest.version;requestId=UUID.randomUUID().toString()}}}
  render()
 }
 private fun render(){val b=binding?:return;val users=vm.records<Member>("members").associateBy{it.uid};b.deadline.text="Hạn: ${formattedTime(due)} (giờ Việt Nam)";b.selected.text=selected.map{users[it]?.displayName?:it}.joinToString(", ");b.representative.text="Đại diện: ${users[representative]?.displayName?:"Chưa chọn"}"}
 override fun onSaveInstanceState(out:Bundle){out.putStringArrayList("selected",ArrayList(selected));out.putString("representative",representative);out.putString("due",due);out.putString("requestId",requestId);out.putInt("version",version);super.onSaveInstanceState(out)}
 override fun onDestroyView(){binding=null;super.onDestroyView()}
}

