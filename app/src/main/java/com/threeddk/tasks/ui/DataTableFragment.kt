package com.threeddk.tasks.ui
import com.threeddk.tasks.adapter.GridAdapter
import com.threeddk.tasks.adapter.gridCell
import com.threeddk.tasks.adapter.fieldTypeIcon
import com.threeddk.tasks.model.objects
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.WorkspaceViewModel
import com.threeddk.tasks.viewmodel.filterOperators
import com.threeddk.tasks.viewmodel.matchingPickerIndices
import com.threeddk.tasks.adapter.RecordCardAdapter

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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.*
import com.threeddk.tasks.R
import com.threeddk.tasks.data.recentBases
import com.threeddk.tasks.databinding.*
import java.time.LocalDate

class DataTableFragment:Fragment(){
 private var binding:FragmentDataTableBinding?=null
 private val vm:WorkspaceViewModel by viewModels()
 private val path get()=requireArguments().getString("path")!!
 private val basePath get()=requireArguments().getString("basePath").orEmpty()
 private val baseName get()=arguments?.getString("baseName").orEmpty()
 private var remembered=false
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 private var schema=""
 private var dateDialog:DatePickerDialog?=null
 private lateinit var adapter:GridAdapter
 private lateinit var cardAdapter:RecordCardAdapter
 private lateinit var kanbanAdapter:com.threeddk.tasks.adapter.KanbanAdapter
 // Layout, group-by and Kanban stack field are remembered per table on this device.
 private val prefs get()=requireContext().getSharedPreferences("table_layout",android.content.Context.MODE_PRIVATE)
 private var layoutMode="GRID"
 private val showCards get()=layoutMode=="CARDS"
 private var groupFieldId:String?=null
 private var kanbanFieldId:String?=null
 private var searchExpanded=false
 private val pickCsv=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->
  if(uri==null)return@registerForActivityResult
  val read=runCatching{requireContext().contentResolver.openInputStream(uri)!!.use{com.threeddk.tasks.data.readLimited(it,2_000_000)}}
  val bytes=read.getOrNull()
  if(bytes!=null)vm.importCsv(bytes.toString(Charsets.UTF_8))
  else com.google.android.material.snackbar.Snackbar.make(requireView(),if(read.isSuccess)R.string.import_csv_too_large else R.string.import_csv_unreadable,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
 }
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentDataTableBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!
  if(basePath.isNotBlank())(requireActivity() as com.threeddk.tasks.MainActivity).paintChrome(com.threeddk.tasks.adapter.RowAdapter.tileFor(basePath))
  if(basePath.isNotBlank())vm.baseTitle(basePath,baseName){name->
   if(name.isNotBlank()&&binding===b&&findNavController().currentDestination?.id==R.id.dataTable){
    (requireActivity() as androidx.appcompat.app.AppCompatActivity).supportActionBar?.title=name
   }
  }
  adapter=GridAdapter(::edit);b.grid.layoutManager=LinearLayoutManager(requireContext());b.grid.adapter=adapter
  cardAdapter=RecordCardAdapter(::edit);b.cards.layoutManager=LinearLayoutManager(requireContext());b.cards.adapter=cardAdapter
  kanbanAdapter=com.threeddk.tasks.adapter.KanbanAdapter(::edit,::moveCard,::chooseCardColumn);b.kanban.layoutManager=LinearLayoutManager(requireContext(),LinearLayoutManager.HORIZONTAL,false);b.kanban.adapter=kanbanAdapter
  layoutMode=prefs.getString("$path/layout","GRID")!!;groupFieldId=prefs.getString("$path/group",null);kanbanFieldId=prefs.getString("$path/kanban",null)
  b.group.setOnClickListener{chooseGroup()}
  searchExpanded=s?.getBoolean("searchExpanded",searchExpanded)?:searchExpanded
  b.searchBox.isVisible=searchExpanded
  b.searchToggle.setOnClickListener{setSearchExpanded(!b.searchBox.isVisible)}
  b.queryActions.isVisible=s?.getBoolean("queryExpanded")==true
  b.queryToggle.setOnClickListener{b.queryActions.isVisible=!b.queryActions.isVisible}
  b.tableList.setOnClickListener{pickTable()}
  b.viewChoice.setOnClickListener{pickView()}
  vm.observeHome()
  watch(vm.recentBases){requireActivity().invalidateOptionsMenu()}
  b.search.setOnEditorActionListener{_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){setSearchExpanded(false);true}else false}
  updatePresentation()
  b.search.setText(vm.queryConfig.text("search"));b.search.doAfterTextChanged{vm.search(it.toString());updateSearchLabel()};b.refresh.setOnClickListener{vm.refresh()};b.add.setOnClickListener{edit("new")};b.more.setOnClickListener{vm.loadMore()}
  b.filter.setOnClickListener{filters()};b.sort.setOnClickListener{sorts()};b.columns.setOnClickListener{columns()};setHasOptionsMenu(true)
  watch(vm.data){data->
   if(data!=null&&!vm.accessDenied.value&&!remembered){remembered=true;vm.rememberTable(path,basePath)}
   render();requireActivity().invalidateOptionsMenu()
  };watch(vm.accessDenied){render();requireActivity().invalidateOptionsMenu()};watch(vm.drafts){requireActivity().invalidateOptionsMenu()};watch(vm.selectedViewId){vm.queryConfig.text("search").let{if(b.search.text.toString()!=it)b.search.setText(it)};render()};watch(vm.hasMore){b.more.isVisible=it};watch(vm.capabilities){render();requireActivity().invalidateOptionsMenu()}
  watch(vm.busy){val usable=vm.data.value!=null&&!vm.accessDenied.value;b.refresh.isEnabled=!it;b.add.isEnabled=!it&&usable;b.more.isEnabled=!it&&usable;b.viewChoice.isEnabled=!it&&usable;b.filter.isEnabled=!it&&usable;b.sort.isEnabled=!it&&usable;b.columns.isEnabled=!it&&usable;dialog?.getButton(-1)?.isEnabled=!it;b.status.text=if(it)getString(R.string.p0_dang_tai_giu_du_lieu_da_hien_thi) else vm.error.value?:vm.note.value}
  watch(vm.note){b.status.text=it};watch(vm.notice){if(it!=null){tell(it);vm.notice.value=null}};watch(vm.error){b.status.isVisible=it!=null;if(it!=null){b.status.text=it;dialog?.setMessage(it)}};vm.openTable(path,arguments?.getString("initialViewId"))
 }
 override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("queryExpanded",binding?.queryActions?.isVisible==true);outState.putBoolean("searchExpanded",searchExpanded);super.onSaveInstanceState(outState)}
 /** "50 / 300 dòng" while more rows match than are loaded, like Airtable's record count. */
 private fun countLabel(loaded:Int,data:JsonObject):String{
  val total=data.get("total")?.takeIf{it.isJsonPrimitive}?.asInt?:loaded
  return if(total>loaded)getString(R.string.summary_count_of,loaded,total) else getString(R.string.summary_count,loaded)
 }
 private fun tell(message:String){view?.let{com.google.android.material.snackbar.Snackbar.make(it,message,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()}}
 private fun updatePresentation(){val b=binding?:return;val ok=!vm.accessDenied.value;b.cards.isVisible=ok&&layoutMode=="CARDS";b.kanban.isVisible=ok&&layoutMode=="KANBAN";b.gridContainer.isVisible=ok&&layoutMode=="GRID";b.group.isVisible=layoutMode=="GRID"}
 private fun saveLayout(){
  prefs.edit().putString("$path/layout",layoutMode).putString("$path/group",groupFieldId).putString("$path/kanban",kanbanFieldId).apply()
  val layout=JsonObject().apply{addProperty("type",layoutMode);addProperty("groupFieldId",groupFieldId);addProperty("kanbanFieldId",kanbanFieldId)}
  appliedLayout=layout;vm.setLayout(layout)
 }
 /** The open view's saved layout wins over this device's last choice, so everyone sees the same view. */
 private var appliedLayout:JsonObject?=null
 private fun applyViewLayout(){
  val layout=vm.viewLayout
  if(layout===appliedLayout)return
  appliedLayout=layout
  if(layout==null){layoutMode=prefs.getString("$path/layout","GRID")!!;groupFieldId=prefs.getString("$path/group",null);kanbanFieldId=prefs.getString("$path/kanban",null)}
  else{layoutMode=layout.text("type").takeIf{it in listOf("GRID","CARDS","KANBAN")}?:"GRID";groupFieldId=layout.text("groupFieldId").ifBlank{null};kanbanFieldId=layout.text("kanbanFieldId").ifBlank{null}}
  requireActivity().invalidateOptionsMenu()
 }
 private fun selectFields()=vm.data.value?.objects("fields").orEmpty().filter{it.text("type")=="SELECT"}
 private fun chooseLayout(){
  val modes=listOf("GRID","CARDS","KANBAN");val labels=listOf(R.string.layout_grid,R.string.layout_cards,R.string.layout_kanban).map(::getString)
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.layout_choose).setSingleChoiceItems(labels.toTypedArray(),modes.indexOf(layoutMode)){d,i->
   d.dismiss()
   if(modes[i]!="KANBAN"){layoutMode=modes[i];saveLayout();render();requireActivity().invalidateOptionsMenu();return@setSingleChoiceItems}
   val stacks=selectFields()
   if(stacks.isEmpty()){binding?.status?.apply{isVisible=true;setText(R.string.kanban_need_select)};return@setSingleChoiceItems}
   fun use(field:JsonObject){layoutMode="KANBAN";kanbanFieldId=field.text("id");saveLayout();render();requireActivity().invalidateOptionsMenu()}
   if(stacks.size==1)use(stacks[0]) else dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.kanban_stack_by).setItems(stacks.map{it.text("name")}.toTypedArray()){_,j->use(stacks[j])}.setNegativeButton(R.string.p0_huy,null).show()
  }.setNegativeButton(R.string.p0_huy,null).show()
 }
 private fun moveCard(recordId:String,value:String?){kanbanFieldId?.let{vm.moveCard(recordId,it,value)}}
 /** Non-drag path (screen readers, or anyone who prefers a list): pick the target column. */
 private fun chooseCardColumn(recordId:String){
  val columns=kanbanAdapter.columns;val current=columns.indexOfFirst{c->c.second.any{it.text("id")==recordId}}
  val title=columns.getOrNull(current)?.second?.firstOrNull{it.text("id")==recordId}?.let{r->vm.data.value?.objects("fields")?.firstOrNull()?.let{f->r.getAsJsonObject("cells")?.get(f.text("id"))?.takeUnless{it.isJsonNull}?.asString}}.orEmpty()
  val labels=columns.map{it.first?:getString(R.string.p0_cell_empty)}
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.kanban_move_to,title)).setSingleChoiceItems(labels.toTypedArray(),current){d,i->
   d.dismiss();if(i!=current)moveCard(recordId,columns[i].first)
  }.setNegativeButton(R.string.p0_huy,null).show()
 }
 private fun chooseGroup(){
  val fields=vm.data.value?.objects("fields").orEmpty()
  val labels=listOf(getString(R.string.group_none))+fields.map{it.text("name")}
  val current=fields.indexOfFirst{it.text("id")==groupFieldId}+1
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.group_by).setSingleChoiceItems(labels.toTypedArray(),current){d,i->
   groupFieldId=fields.getOrNull(i-1)?.text("id");saveLayout();d.dismiss();render()
  }.setNegativeButton(R.string.p0_huy,null).show()
 }
 private fun setSearchExpanded(expanded:Boolean){
  val b=binding?:return;searchExpanded=expanded;b.searchBox.isVisible=expanded
  val keyboard=androidx.core.view.ViewCompat.getWindowInsetsController(b.search)
  if(expanded){b.search.requestFocus();keyboard?.show(androidx.core.view.WindowInsetsCompat.Type.ime())}else{b.search.clearFocus();keyboard?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
 }
 private fun updateSearchLabel(){val b=binding?:return;b.searchToggle.isSelected=!b.search.text.isNullOrBlank();b.searchToggle.contentDescription=getString(R.string.mobile_search_query,b.search.text.toString())}
 private fun render(){
  val b=binding?:return;val data=vm.data.value.takeUnless{vm.accessDenied.value}
  applyViewLayout()
  val usable=data?.has("schemaVersion")==true&&!vm.accessDenied.value
  b.search.isEnabled=usable;b.searchToggle.isEnabled=usable;b.filter.isEnabled=usable&&!vm.busy.value;b.sort.isEnabled=usable&&!vm.busy.value;b.columns.isEnabled=usable&&!vm.busy.value
  b.add.isVisible=data?.text("role") in listOf("OWNER","EDITOR")&&!vm.accessDenied.value
  updatePresentation()
  updateSearchLabel()
  if(data==null){
   if(vm.accessDenied.value){adapter.submitList(emptyList());cardAdapter.submitList(emptyList());b.header.removeAllViews();b.add.isVisible=false;b.viewChoice.isVisible=false;schema=""}
   b.empty.isVisible=vm.accessDenied.value||adapter.currentList.isEmpty();b.empty.text=if(vm.accessDenied.value)getString(R.string.p0_access_revoked) else if(vm.busy.value)getString(R.string.p0_dang_tai_du_lieu) else getString(R.string.p0_chua_co_du_lieu_tren_thiet_bi_ket);return
  }
  b.add.isVisible=data.text("role")!="VIEWER"
  val fields=data.objects("fields");val shown=vm.displayedFields()
  val primary=fields.firstOrNull()?.takeIf{it.text("type")=="TEXT"&&it in shown&&shown.size>1}
  val gridFields=shown.filter{it!=primary}
  adapter.members=data.objects("members")
  cardAdapter.members=adapter.members
  val signature=shown.toString()+primary.toString()+adapter.members.toString()+vm.canEditFields()
  if(schema!=signature||b.header.childCount==0){schema=signature;b.header.removeAllViews();gridFields.forEach{f->b.header.addView(gridCell(requireContext(),f.text("name"),f.text("type"),true).apply{if(vm.canEditFields()){isClickable=true;setBackgroundResource(android.R.drawable.list_selector_background);contentDescription=getString(R.string.field_menu_description,f.text("name"));setOnClickListener{fieldMenu(f)}}})};adapter.fields=gridFields;adapter.primary=primary;cardAdapter.fields=shown;adapter.notifyDataSetChanged();cardAdapter.notifyDataSetChanged()}
  val records=data.objects("records");cardAdapter.submitList(records);b.empty.isVisible=records.isEmpty()
  val groupField=fields.firstOrNull{it.text("id")==groupFieldId}
  b.group.text=getString(R.string.group_count,if(groupField==null)0 else 1)
  adapter.submitList(if(groupField==null)records else com.threeddk.tasks.viewmodel.groupRows(records,groupField).flatMap{(value,rows)->
   listOf(JsonObject().apply{addProperty("group",getString(R.string.group_header,if(value==null)getString(R.string.p0_cell_empty) else cellLabel(requireContext(),groupField,value,adapter.members),rows.size))})+rows})
  b.summary.removeAllViews()
  gridFields.forEachIndexed{i,f->
   val s=com.threeddk.tasks.viewmodel.columnSummary(records,f)
   val text=when(s.kind){"checked"->getString(R.string.summary_checked,s.value.toInt());"sum"->getString(R.string.summary_sum,s.value);else->getString(R.string.summary_filled,s.value.toInt())}
   b.summary.addView(gridCell(requireContext(),if(i==0)countLabel(records.size,data)+" · "+text else text,f.text("type"),header=true,icon=false))
  }
  val stack=fields.firstOrNull{it.text("id")==kanbanFieldId&&it.text("type")=="SELECT"}
  if(layoutMode=="KANBAN"&&stack==null){layoutMode="GRID";updatePresentation()}
  if(stack!=null){kanbanAdapter.editable=vm.canEditRecords();kanbanAdapter.fields=shown;kanbanAdapter.members=adapter.members;kanbanAdapter.columns=com.threeddk.tasks.viewmodel.kanbanColumns(records,stack);kanbanAdapter.notifyDataSetChanged()}
  val filtered=b.search.text?.isNotBlank()==true||conditions().isNotEmpty()
  b.empty.text=if(data.get("queryCached")?.asBoolean!=true)getString(R.string.p0_query_not_cached) else if(filtered)getString(R.string.p0_khong_co_dong_phu_hop_thu_doi_tim) else getString(R.string.p0_chua_co_dong_trong_du_lieu_da_tai)
  b.filter.text=getString(R.string.p0_filter_count,conditions().size);b.sort.text=getString(R.string.p0_sort_count,sortItems().size)
  val selectedView=data.objects("views").firstOrNull{it.text("id")==vm.selectedViewId.value}
  val viewName=selectedView?.text("name")?:getString(when(layoutMode){"CARDS"->R.string.layout_cards;"KANBAN"->R.string.layout_kanban;else->R.string.layout_grid})
  b.viewChoice.text=if(vm.supports("savedViews")) "${data.text("name")} / $viewName" else data.text("name")
  b.viewChoice.isVisible=true
 }
 private fun pickView()=pickTable()
 private fun pickTable(){
  if(basePath.isBlank()){findNavController().navigateUp();return}
  if(vm.accessDenied.value||vm.busy.value||dialog?.isShowing==true)return
  val view=binding?.root?:return
  vm.tablesInBase(basePath){tables->
   if(binding?.root!==view||findNavController().currentDestination?.id!=R.id.dataTable)return@tablesInBase
   val b=DialogTablePickerBinding.inflate(layoutInflater)
   val picker=MaterialAlertDialogBuilder(requireContext()).setView(b.root).create()
   dialog=picker
   picker.setOnDismissListener{vm.cancelPickerLoading()}
   b.pickerHeading.text=baseName.ifBlank{(requireActivity() as androidx.appcompat.app.AppCompatActivity).supportActionBar?.title}
   b.pickerHeading.isClickable=false
   b.closePicker.setOnClickListener{picker.dismiss()}
   val choices=ArrayAdapter<String>(requireContext(),R.layout.item_picker_view)
   b.choices.adapter=choices
   var selectedPath=path
   var views=emptyList<JsonObject>()
   var matches=emptyList<Int>()
   var ready=false
   fun filter(){
    val labels=listOf(getString(R.string.picker_all_records))+views.map{it.text("name")}
    matches=matchingPickerIndices(labels,b.search.text.toString())
    choices.clear();choices.addAll(matches.map(labels::get));b.choices.clearChoices()
    val selected=if(selectedPath==path)views.indexOfFirst{it.text("id")==vm.selectedViewId.value}+1 else 0
    val checked=matches.indexOf(selected)
    if(checked>=0)b.choices.setItemChecked(checked,true)
    b.pickerMessage.isVisible=matches.isEmpty()
    b.pickerMessage.setText(R.string.picker_no_views)
   }
   fun load(resource:String){
    selectedPath=resource;ready=false;choices.clear();b.pickerMessage.isVisible=false
    b.pickerLoading.isVisible=true;b.createView.isVisible=false
    vm.pickerMetadata(resource){meta,error->
     if(!picker.isShowing||binding?.root!==view||selectedPath!=resource)return@pickerMetadata
     b.pickerLoading.isVisible=false
     if(meta==null){b.pickerMessage.isVisible=true;b.pickerMessage.text=error;return@pickerMetadata}
     views=meta.objects("views");ready=true;filter()
     b.createView.isVisible=resource==path&&vm.supports("savedViews")&&meta.text("role") in listOf("OWNER","EDITOR")
    }
   }
   tables.forEach{table->b.tableTabs.addTab(b.tableTabs.newTab().setText(table.text("name")).setTag("tables/"+table.text("id")),false)}
   val selected=tables.indexOfFirst{"tables/"+it.text("id")==path}
   if(selected>=0)b.tableTabs.getTabAt(selected)?.select()
   b.tableTabs.addOnTabSelectedListener(object:com.google.android.material.tabs.TabLayout.OnTabSelectedListener{
    override fun onTabSelected(tab:com.google.android.material.tabs.TabLayout.Tab){load(tab.tag as String)}
    override fun onTabUnselected(tab:com.google.android.material.tabs.TabLayout.Tab){}
    override fun onTabReselected(tab:com.google.android.material.tabs.TabLayout.Tab){if(!ready)load(tab.tag as String)}
   })
   b.search.doAfterTextChanged{if(ready)filter()}
   b.createView.setOnClickListener{picker.dismiss();saveView()}
   b.choices.setOnItemClickListener{_,_,position,_->
    if(!ready)return@setOnItemClickListener
    val id=views.getOrNull(matches[position]-1)?.text("id")
    picker.dismiss()
    if(selectedPath==path){if(id!=vm.selectedViewId.value)vm.selectView(id)}
    else findNavController().navigate(R.id.dataTable,bundleOf("path" to selectedPath,"basePath" to basePath,"baseName" to baseName,"initialViewId" to id),
     navOptions{popUpTo(R.id.dataTable){inclusive=true}})
   }
   picker.show()
   picker.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
   picker.window?.setBackgroundDrawableResource(android.R.color.transparent)
   picker.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
   load(path)
  }
 }
 private fun edit(id:String){
  if(vm.accessDenied.value)return
  val rows=if(layoutMode=="GRID")adapter.currentList.filter{!it.has("group")} else if(layoutMode=="KANBAN")kanbanAdapter.columns.flatMap{it.second} else cardAdapter.currentList
  val ids=if(id=="new")emptyArray() else rows.map{it.text("id")}.toTypedArray()
  findNavController().navigate(R.id.recordEditor,bundleOf("path" to path,"recordId" to id,"rowIds" to ids))
 }
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){
  if(vm.data.value!=null&&!vm.accessDenied.value){
   val starred=vm.recentBases.value.any{it.path==basePath&&it.starred}
   menu.add(0,R.id.toggle_table_star,1,getString(if(starred)R.string.home_unstar else R.string.home_star))
  }
  menu.add(0,R.id.display_mode,0,getString(R.string.layout_choose)).setIcon(if(layoutMode=="GRID")R.drawable.ic_table else R.drawable.ic_cards).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
  if(basePath.isNotBlank()&&vm.data.value?.text("role")=="OWNER"&&vm.supports("inviteLinks"))menu.add(0,15,0,getString(R.string.collab_title)).setIcon(R.drawable.ic_person_add).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
  if(basePath.isNotBlank())listOfNotNull(menu.findItem(R.id.display_mode),menu.findItem(15)).forEach{it.icon?.mutate()?.setTint(com.threeddk.tasks.adapter.RowAdapter.tileFor(basePath).second)}
  if(vm.supports("savedViews")){menu.add(0,11,0,getString(R.string.p0_luu_thanh_che_do_xem_moi));if(editableView()!=null)menu.add(0,12,1,getString(R.string.p0_luu_thay_doi_che_do_xem))}
  if(vm.data.value?.text("role")=="OWNER"){menu.add(0,10,2,getString(R.string.p0_them_cot));vm.data.value?.objects("fields")?.firstOrNull()?.let{menu.add(0,16,2,getString(R.string.field_primary,it.text("name")))};menu.add(0,14,2,getString(R.string.import_csv))}
  if(vm.drafts.value.isNotEmpty())menu.add(0,13,3,getString(R.string.p0_draft_count,vm.drafts.value.size))
 }
 override fun onOptionsItemSelected(item:MenuItem):Boolean{when(item.itemId){R.id.display_mode->chooseLayout();R.id.toggle_table_star->if(!vm.accessDenied.value)vm.toggleBaseStar(basePath);10->addField();16->vm.data.value?.objects("fields")?.firstOrNull()?.let{fieldMenu(it,primary=true)};11->saveView();12->updateView();13->drafts();15->showCollaborators(vm,basePath,(requireActivity() as androidx.appcompat.app.AppCompatActivity).supportActionBar?.title?.toString()?:baseName);14->pickCsv.launch(arrayOf("text/*","application/csv","application/vnd.ms-excel"));else->return super.onOptionsItemSelected(item)};return true}
 private fun drafts(){
  val items=vm.drafts.value;val labels=vm.draftLabels()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_ban_nhap_chua_gui_len_bang)).setItems(labels){_,index->
   val draft=items[index]
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(labels[index]).setPositiveButton(getString(R.string.p0_tiep_tuc_soan)){_,_->edit("new/"+draft.taskId.substringAfterLast('/'))}.setNeutralButton(getString(if(draft.pendingCreateJson.isBlank())R.string.p0_xoa_nhap else R.string.p0_check_previous_submit)){_,_->
    if(draft.pendingCreateJson.isNotBlank()){
     dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_check_previous_submit)).setMessage(getString(R.string.p0_pending_draft_no_delete)).setPositiveButton(getString(R.string.p0_tiep_tuc_soan)){_,_->edit("new/"+draft.taskId.substringAfterLast('/'))}.setNegativeButton(getString(R.string.p0_dong),null).show()
    }else dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_xoa_nhap_tren_thiet_bi)).setMessage(getString(R.string.p0_noi_dung_nay_chua_duoc_gui_len_bang)).setPositiveButton(getString(R.string.p0_xoa_nhap)){_,_->vm.discardDraft(draft.taskId)}.setNegativeButton(getString(R.string.p0_giu_lai),null).show()
   }.setNegativeButton(getString(R.string.p0_dong),null).show()
  }.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun editableView()=vm.editableView()
 private fun conditions()=vm.conditions()
 private fun sortItems()=vm.sorts()
 private fun applyConditions(items:List<JsonObject>){vm.applyConditions(items);render()}
 private fun fieldName(id:String)=vm.data.value?.objects("fields")?.firstOrNull{it.text("id")==id}?.text("name")?:id
 private fun filters(){
  val items=conditions()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(if(vm.queryConfig.getAsJsonObject("filter")?.text("op")=="or")R.string.p0_filter_or else R.string.p0_loc_thoa_tat_ca_dieu_kien)).setItems(items.map{fieldName(it.text("fieldId"))+" "+operatorLabel(requireContext(),it.text("operator"))+" "+(it.get("value")?.let{v->cellLabel(requireContext(),vm.data.value?.objects("fields")?.firstOrNull{f->f.text("id")==it.text("fieldId")},v,vm.data.value?.objects("members").orEmpty())}?:"")}.toTypedArray()){_,index->
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_xoa_dieu_kien_nay)).setPositiveButton(getString(R.string.p0_xoa)){_,_->vm.removeCondition(index)}.setNegativeButton(getString(R.string.p0_huy),null).show()
  }.setPositiveButton(getString(R.string.p0_them_dieu_kien)){_,_->if(items.size<10)chooseField(::chooseOperator) else tell(getString(R.string.p0_toi_da_10_dieu_kien_trong_giao_dien))}.setNeutralButton(getString(R.string.p0_xoa_tat_ca)){_,_->applyConditions(emptyList())}.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun chooseField(done:(JsonObject)->Unit){
  val fields=vm.data.value?.objects("fields").orEmpty()
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_chon_cot)).setItems(fields.map{it.text("name")}.toTypedArray()){_,index->done(fields[index])}.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 private fun chooseOperator(field:JsonObject){
  val operators=filterOperators(field.text("type"))
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(field.text("name")).setItems(operators.map{operatorLabel(requireContext(),it)}.toTypedArray()){_,index->
   val op=operators[index]
   fun apply(value:JsonElement?){vm.addCondition(field.text("id"),op,value)}
   if(op in listOf("isEmpty","isMe"))apply(null) else filterValue(field,::apply)
  }.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 private fun filterValue(field:JsonObject,done:(JsonElement)->Unit){
  val members=vm.data.value?.objects("members").orEmpty()
  val values=when(field.text("type")){"SELECT","MULTI_SELECT"->field.getAsJsonArray("options")?.map{it.asString}.orEmpty();"USER"->members.map{it.text("uid")};"CHECKBOX"->listOf("true","false");else->emptyList()}
  if(field.text("type") in listOf("SELECT","MULTI_SELECT","USER","CHECKBOX")){
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
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_sap_xep_o_trong_o_cuoi)).setItems(items.map{fieldName(it.text("fieldId"))+if(it.text("direction")=="desc")getString(R.string.p0_giam_dan) else getString(R.string.p0_tang_dan)}.toTypedArray()){_,index->vm.removeSort(index);render()}
   .setPositiveButton(getString(R.string.p0_them_cot)){_,_->if(items.size<3)chooseField{field->dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(field.text("name")).setItems(arrayOf(getString(R.string.p0_tang_dan_2),getString(R.string.p0_giam_dan_2))){_,direction->vm.addSort(field.text("id"),direction==1);render()}.setNegativeButton(getString(R.string.p0_huy),null).show()} else tell(getString(R.string.p0_toi_da_3_cot_sap_xep))}
   .setNeutralButton(getString(R.string.p0_xoa_tat_ca)){_,_->vm.queryTable(sort=JsonArray());render()}.setNegativeButton(getString(R.string.p0_dong),null).show()
 }
 private fun columnOrder(){
  val fields=vm.data.value?.objects("fields").orEmpty();val order=vm.queryConfig.getAsJsonArray("fieldOrder")?.map{it.asString}.orEmpty()+fields.map{it.text("id")}
  val sorted=order.distinct().mapNotNull{id->fields.firstOrNull{it.text("id")==id}}
  val list=RecyclerView(requireContext()).apply{layoutManager=LinearLayoutManager(requireContext())}
  val reorder=com.threeddk.tasks.adapter.ColumnOrderAdapter(sorted).also{it.attach(list)}
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.column_order).setMessage(R.string.column_order_hint).setView(list)
   .setPositiveButton(R.string.p0_ap_dung){_,_->vm.setFieldOrder(reorder.order.map{it.text("id")});schema="";render()}
   .setNegativeButton(R.string.p0_huy,null).show()
 }
 private fun columns(){
  val fields=vm.data.value?.objects("fields").orEmpty();val selected=vm.queryConfig.getAsJsonArray("fieldIds")?.map{it.asString}.orEmpty();val checked=BooleanArray(fields.size){selected.isEmpty()||fields[it].text("id") in selected}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_cot_hien_thi)).setMultiChoiceItems(fields.map{it.text("name")}.toTypedArray(),checked){_,index,value->checked[index]=value}.setPositiveButton(getString(R.string.p0_ap_dung),null).setNeutralButton(R.string.column_order){_,_->columnOrder()}.setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{if(checked.none{it}){android.widget.Toast.makeText(requireContext(),R.string.p0_chon_it_nhat_mot_cot,android.widget.Toast.LENGTH_SHORT).show();return@setOnClickListener};vm.selectColumns(fields,checked);render();d.dismiss()}};d.show()
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
   vm.updateSelectedView()
  }.setNegativeButton(getString(R.string.p0_huy),null).show()
 }
 /** Airtable-style field sheet: header with the field's type icon, then one row per action. */
 /** [primary]: the record name field, which Airtable never hides, deletes or lets anything sit left of. */
 private fun fieldMenu(field:JsonObject,primary:Boolean=false){
  val ctx=requireContext();val sheet=com.google.android.material.bottomsheet.BottomSheetDialog(ctx);val density=resources.displayMetrics.density
  val ink=com.google.android.material.color.MaterialColors.getColor(binding!!.root,com.google.android.material.R.attr.colorOnSurface)
  fun row(text:String,icon:Int,heading:Boolean=false,action:(()->Unit)?=null)=TextView(ctx).apply{
   this.text=text;textSize=if(heading)18f else 16f;setTextColor(ink);gravity=Gravity.CENTER_VERTICAL;minHeight=(56*density).toInt()
   setPadding((24*density).toInt(),0,(24*density).toInt(),0);compoundDrawablePadding=(20*density).toInt()
   setCompoundDrawablesRelativeWithIntrinsicBounds(icon,0,0,0);compoundDrawableTintList=android.content.res.ColorStateList.valueOf(ink)
   if(heading){setTypeface(typeface,Typeface.BOLD);androidx.core.view.ViewCompat.setAccessibilityHeading(this,true)}
   if(action!=null){setBackgroundResource(android.R.drawable.list_selector_background);setOnClickListener{sheet.dismiss();action()}}
  }
  val shown=vm.displayedFields()
  val list=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(0,(8*density).toInt(),0,(16*density).toInt())}
  list.addView(row(field.text("name"),fieldTypeIcon(field.text("type")),heading=true))
  list.addView(row(getString(R.string.field_edit),R.drawable.ic_field_customize){addField(field)})
  list.addView(row(getString(R.string.field_duplicate),R.drawable.ic_field_duplicate){vm.duplicateField(field)})
  if(!primary)list.addView(row(getString(R.string.field_insert_left),R.drawable.ic_field_insert_left){addField(insertNextTo=field,right=false)})
  list.addView(row(getString(R.string.field_insert_right),R.drawable.ic_field_insert_right){addField(insertNextTo=field,right=true)})
  if(!primary&&shown.size>1)list.addView(row(getString(R.string.field_hide),R.drawable.ic_field_hide){
   val fields=vm.data.value?.objects("fields").orEmpty();vm.selectColumns(fields,BooleanArray(fields.size){fields[it] in shown&&fields[it].text("id")!=field.text("id")});render()
  })
  if(!primary)list.addView(row(getString(R.string.field_delete),R.drawable.ic_field_delete){
   dialog=MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.field_delete_title,field.text("name"))).setMessage(R.string.field_delete_message).setPositiveButton(R.string.field_delete){_,_->vm.deleteField(field)}.setNegativeButton(R.string.p0_huy,null).show()
  })
  sheet.setContentView(android.widget.ScrollView(ctx).apply{addView(list)});sheet.show()
 }
 /** A view with its own column order would put the new field last; place it beside its neighbour there too. */
 private fun placeNewField(id:String,neighbour:JsonObject?,right:Boolean){
  if(neighbour==null||id.isBlank()||(vm.queryConfig.getAsJsonArray("fieldOrder")?.size()?:0)==0)return
  val order=vm.displayedFields().map{it.text("id")}.filter{it!=id}.toMutableList()
  val at=order.indexOf(neighbour.text("id")).takeIf{it>=0}?:return
  order.add(if(right)at+1 else at,id);vm.setFieldOrder(order);schema="";render()
 }
 /** Default name like Airtable's "Field 6": the first "Cột N" not already used. */
 private fun nextFieldName():String{
  val names=vm.data.value?.objects("fields").orEmpty().map{it.text("name").lowercase()}.toSet()
  return generateSequence(names.size+1){it+1}.map{getString(R.string.field_default_name,it)}.first{it.lowercase() !in names}
 }
 private fun addField(field:JsonObject?=null,insertNextTo:JsonObject?=null,right:Boolean=false){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten_cot)
  val types=vm.fieldTypes()
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,types.map{fieldTypeLabel(requireContext(),it)})
  b.choice.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){b.extraBox.isVisible=types[position] in listOf("SELECT","MULTI_SELECT")};override fun onNothingSelected(p:AdapterView<*>?){} }
  if(field!=null){
   b.value.setText(field.text("name"));types.indexOf(field.text("type")).takeIf{it>=0}?.let{b.choice.setSelection(it)}
   b.extra.setText(field.getAsJsonArray("options")?.joinToString(", "){it.asString}.orEmpty())
  }else{b.value.setText(nextFieldName());b.value.selectAll()}
  val title=when{field!=null->getString(R.string.field_edit);insertNextTo!=null->getString(if(right)R.string.field_insert_right else R.string.field_insert_left);else->getString(R.string.p0_them_cot_tuy_chinh)}
  // Insert left/right: new field takes the neighbour's slot (left) or the one after it (right).
  val position=insertNextTo?.get("position")?.takeUnless{it.isJsonNull}?.asInt?.let{if(right)it+1 else it}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(b.root).setPositiveButton(getString(if(field==null)R.string.p0_them else R.string.p0_luu),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val error=if(field==null)vm.createField(b.value.text.toString(),types[b.choice.selectedItemPosition],b.extra.text.toString(),position,created={id->placeNewField(id,insertNextTo,right)}){if(isAdded)d.dismiss()}
    else vm.updateField(field,b.value.text.toString(),types[b.choice.selectedItemPosition],b.extra.text.toString()){if(isAdded)d.dismiss()}
   b.valueBox.error=null;b.extraBox.error=null
   if(error!=null){if(error==R.string.p0_nhap_ten_tu_1_den_100_ky_tu)b.valueBox.error=getString(error) else b.extraBox.error=getString(error)}

  }};d.show()
 }
 override fun onDestroyView(){(requireActivity() as com.threeddk.tasks.MainActivity).paintChrome(null);dateDialog?.dismiss();dateDialog=null;dialog?.dismiss();dialog=null;binding?.grid?.adapter=null;binding?.cards?.adapter=null;binding=null;schema="";super.onDestroyView()}
}

internal fun operatorLabel(context:android.content.Context,operator:String):String=when(operator){
 "eq"->R.string.p0_operator_eq;"ne"->R.string.p0_operator_ne;"contains"->R.string.p0_operator_contains;"isEmpty"->R.string.p0_operator_empty;"isMe"->R.string.p0_operator_me;"lt"->R.string.p0_operator_lt;"lte"->R.string.p0_operator_lte;"gt"->R.string.p0_operator_gt;"gte"->R.string.p0_operator_gte;else->null
}?.let{context.getString(it)}?:operator
internal fun cellLabel(context:android.content.Context,field:JsonObject?,value:JsonElement?,members:List<JsonObject>):String{
 if(value==null||value.isJsonNull)return context.getString(R.string.p0_cell_empty)
 if(value.isJsonArray)return value.asJsonArray.joinToString(", "){it.asString}.ifEmpty{context.getString(R.string.p0_cell_empty)}
 if(!value.isJsonPrimitive)return value.toString()
 val text=value.asString
 return when(field?.text("type")){
  "CHECKBOX"->context.getString(if(text=="true")R.string.p0_cell_yes else R.string.p0_cell_no)
  "USER"->members.firstOrNull{it.text("uid")==text}?.text("displayName")?.takeIf{it.isNotBlank()}?:context.getString(R.string.p0_missing_member,text)
  "DATE"->runCatching{val day=LocalDate.parse(text);"%02d/%02d/%04d".format(day.dayOfMonth,day.monthValue,day.year)}.getOrDefault(text)
  else->text
 }
}
internal fun fieldTypeLabel(context:android.content.Context,type:String)=context.getString(when(type){
 "NUMBER"->R.string.p0_so;"DATE"->R.string.p0_ngay;"CHECKBOX"->R.string.p0_danh_dau;"SELECT"->R.string.p0_lua_chon;"USER"->R.string.p0_thanh_vien_phu_trach
 "LONG_TEXT"->R.string.field_type_long_text;"MULTI_SELECT"->R.string.field_type_multi_select;"URL"->R.string.field_type_url;"EMAIL"->R.string.field_type_email;"PHONE"->R.string.field_type_phone
 else->R.string.p0_van_ban})
