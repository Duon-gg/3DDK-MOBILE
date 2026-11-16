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
import com.threeddk.tasks.data.text
import com.threeddk.tasks.data.objects
import com.threeddk.tasks.databinding.*
import com.google.gson.JsonObject

class WorkspaceFragment:Fragment(){
 private var binding:FragmentWorkspaceBinding?=null
 private val vm:WorkspaceViewModel by viewModels()
 private val path get()=requireArguments().getString("path")?:"workspaces"
 private val mode get()=arguments?.getString("mode").orEmpty()
 private lateinit var adapter:RowAdapter
 private var dialog:androidx.appcompat.app.AlertDialog?=null
 private var baseSheet:com.google.android.material.bottomsheet.BottomSheetDialog?=null
 private var homeTab=R.id.recent_tab
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?)=FragmentWorkspaceBinding.inflate(i,c,false).also{binding=it}.root
 override fun onViewCreated(v:View,s:Bundle?){
  val b=binding!!;adapter=RowAdapter(::open);b.list.layoutManager=LinearLayoutManager(requireContext());b.list.adapter=adapter
  adapter.onLongClick={id->if(id.startsWith("workspaces/"))vm.readResource(id){data->if(binding!=null)workspaceActions(id,data)} else baseActions(id)}
  vm.observeHome()
  watch(vm.workspaceStars){render()}
  if(path.startsWith("workspaces/")){
   b.actions.isVisible=false;b.sync.isVisible=false
   watch(vm.recentBases){render()}
  }
  b.searchBox.isVisible=s?.getBoolean("homeSearchExpanded")==true
  b.search.doAfterTextChanged{render()}
  b.homeSearch.setOnClickListener{
   b.searchBox.isVisible=!b.searchBox.isVisible
   val keyboard=androidx.core.view.ViewCompat.getWindowInsetsController(b.search)
   if(b.searchBox.isVisible){b.search.requestFocus();keyboard?.show(androidx.core.view.WindowInsetsCompat.Type.ime())}
   else{b.search.text?.clear();b.search.clearFocus();keyboard?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
  }
  b.headerSearch.setOnClickListener{b.homeSearch.performClick()}
  b.homeAdd.setOnClickListener{
   android.widget.PopupMenu(requireContext(),b.homeAdd).apply{
    menu.add(0,1,0,R.string.p0_tao_khong_gian)
    menu.add(0,2,1,R.string.p0_lam_moi)
    if(vm.supports("baseActions"))menu.add(0,3,2,"Thùng rác")
    setOnMenuItemClickListener{when(it.itemId){1->create();3->trash();else->vm.refresh()};true}
   }.show()
  }
  b.homeMore.setOnClickListener{
   android.widget.PopupMenu(requireContext(),b.homeMore).apply{
    menu.add(0,1,0,R.string.workspace_tests)
    setOnMenuItemClickListener{findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "test-workspaces"));true}
   }.show()
  }
  b.homeProfile.setOnClickListener{findNavController().navigate(R.id.profile)}
  val account=androidx.lifecycle.ViewModelProvider(requireActivity())[AppViewModel::class.java]
  fun profileImage(){
   val photo=accountAvatar(account.repo.auth?.currentUser,account.me?.avatarUrl)
   com.bumptech.glide.Glide.with(this).load(photo).placeholder(R.drawable.ic_app).error(R.drawable.ic_app).circleCrop().into(b.homeProfile)
  }
  profileImage()
  watch(account.rows){profileImage()}
  b.homeInvitations.setOnClickListener{
   if(vm.supports("baseActions"))dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Lời mời").setItems(arrayOf("Lời mời không gian","Lời mời cơ sở dữ liệu")){_,which->
    if(which==0)findNavController().navigate(R.id.invitations) else baseInvitations()
   }.show() else findNavController().navigate(R.id.invitations)
  }
  b.refresh.setOnClickListener{vm.refresh()};b.add.setOnClickListener{create()}
  if(path=="workspaces"){
   homeTab=s?.getInt("homeTab",homeTab)?:homeTab
   b.homeHeader.isVisible=true
   b.actions.isVisible=false
   b.sync.isVisible=false
   b.homeTabs.isVisible=true
   b.homeTabScroll.isVisible=true
   b.homeTabs.check(homeTab)
   b.homeTabs.addOnButtonCheckedListener{_,id,checked->if(checked){homeTab=id;render()}}
   vm.observeHome()
   watch(vm.home){render()}
   watch(vm.recentBases){render()}
  }
  if(path.startsWith("bases/")){vm.observeHome();watch(vm.recentBases){requireActivity().invalidateOptionsMenu()}}
  setHasOptionsMenu(true)
  watch(vm.data){render();requireActivity().invalidateOptionsMenu()}
  watch(vm.capabilities){render();requireActivity().invalidateOptionsMenu()}
  watch(vm.busy){b.refresh.isEnabled=!it;b.add.isEnabled=!it;b.homeAdd.isEnabled=!it;dialog?.getButton(-1)?.isEnabled=!it}
  watch(vm.note){b.sync.text=it};watch(vm.error){if(it!=null){b.sync.isVisible=true;b.sync.text=it;dialog?.setMessage(it)}}
  if(path.startsWith("bases/")){
   b.list.isVisible=false;b.actions.isVisible=false;b.heading.setText(R.string.p0_dang_tai)
   vm.resolveBase(path){base,table->if(binding===b){
    if(table==null){b.list.isVisible=true;b.actions.isVisible=true;vm.open(path)}
    else findNavController().navigate(R.id.dataTable,bundleOf("path" to "tables/$table","basePath" to path,"baseName" to base.text("name")),androidx.navigation.navOptions{popUpTo(R.id.workspaceBrowser){inclusive=true}})
   }}
  }else vm.open(if(path=="test-workspaces")"workspaces" else path)
 }
 private fun render(){
  val b=binding?:return;val data=vm.data.value
  if(mode.isNotEmpty()){renderWorkspaceSettings(data);return}
  if(path=="test-workspaces"){
   (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.setTitle(R.string.workspace_tests)
   b.heading.isVisible=false;b.summary.isVisible=false;b.add.isVisible=false;b.sync.isVisible=vm.error.value!=null
   val spaces=data?.objects("items").orEmpty().filter{isTestWorkspace(it.text("name"))&&it.text("name").contains(b.search.text.toString(),true)}
   adapter.submitList(spaces.map{Row("workspaces/"+it.text("id"),it.text("name"),roleLabel(requireContext(),it.text("role")),homeItem=true,iconRes=R.drawable.ic_workspace_outline)})
   b.empty.isVisible=spaces.isEmpty();b.empty.setText(R.string.workspace_tests_empty)
   return
  }
  val root=path=="workspaces";val invites=path=="invitations";val workspace=path.startsWith("workspaces/")
  if(root)listOf(b.recentTab,b.starredTab,b.spacesTab).forEach{it.setTypeface(null,if(it.id==homeTab)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)}
  b.heading.isVisible=!root&&!workspace
  b.summary.isVisible=!root&&!workspace
  if(workspace)(activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title=data?.text("name")?:getString(R.string.p0_dang_tai)
  b.heading.text=if(invites)getString(R.string.p0_loi_moi_tham_gia) else data?.text("name")?:getString(R.string.p0_dang_tai)
  b.add.text=if(root)getString(R.string.p0_tao_khong_gian) else if(workspace)getString(R.string.p0_tao_co_so_du_lieu) else getString(R.string.p0_tao_bang)
  val showSavedTables=root&&homeTab!=R.id.spaces_tab
  b.add.isVisible=!invites&&(root||data?.text("role") in listOf("OWNER","EDITOR"))
  if(root&&homeTab==R.id.recent_tab){
   val bases=vm.recentBases.value.filter{it.name.contains(b.search.text.toString(),true)}
   adapter.submitList(bases.map{base->
    val time=android.text.format.DateUtils.getRelativeTimeSpanString(
     base.opened,System.currentTimeMillis(),android.text.format.DateUtils.MINUTE_IN_MILLIS
    )
    val subtitle=if(System.currentTimeMillis()-base.opened<60000)getString(R.string.home_just_opened) else getString(R.string.home_base_opened,time)
    Row(base.path,base.name,subtitle,vividMonogram=true,starred=base.starred)
   })
   b.empty.isVisible=bases.isEmpty()
   b.empty.setText(R.string.home_recent_empty)
   return
  }
  if(showSavedTables){
   val bases=vm.recentBases.value.filter{it.starred&&it.name.contains(b.search.text.toString(),true)}
   val spaces=data?.objects("items").orEmpty().filter{"workspaces/"+it.text("id") in vm.workspaceStars.value&&it.text("name").contains(b.search.text.toString(),true)}
   adapter.submitList(spaces.map{Row("workspaces/"+it.text("id"),it.text("name"),getString(R.string.home_spaces),homeItem=true,starred=true,iconRes=R.drawable.ic_workspace_outline)}+bases.map{Row(it.path,it.name,getString(R.string.home_base_type),vividMonogram=true,starred=true)})
   b.empty.isVisible=bases.isEmpty()&&spaces.isEmpty()
   b.empty.setText(R.string.home_starred_empty)
   return
  }
  if(root){
   val query=b.search.text.toString().trim()
   val owned=data?.objects("items").orEmpty().filter{it.text("name").contains(query,true)}
   val rows=owned.filterNot{isTestWorkspace(it.text("name"))}.map{Row("workspaces/"+it.text("id"),it.text("name"),roleLabel(requireContext(),it.text("role")),homeItem=true,starred="workspaces/"+it.text("id") in vm.workspaceStars.value,iconRes=R.drawable.ic_workspace_outline)}
   val shared=if(query.isBlank()&&data!=null)listOf(Row("home-shared",getString(R.string.home_shared),"",homeItem=true,iconRes=R.drawable.ic_shared_outline)) else emptyList()
   adapter.submitList(shared+rows)
   b.empty.isVisible=rows.isEmpty()&&shared.isEmpty()
   b.empty.setText(R.string.p0_chua_co_du_lieu_da_tai_ket_noi)
   return
  }
  val key=if(root||invites)"items" else if(workspace)"bases" else "tables"
  val rows=data?.objects(key).orEmpty().filter{it.text("name").contains(b.search.text.toString(),true)}.map{
   if(workspace){
    val id="bases/"+it.text("id");val recent=vm.recentBases.value.firstOrNull{base->base.path==id}
    val subtitle=recent?.let{base->getString(R.string.home_base_opened,android.text.format.DateUtils.getRelativeTimeSpanString(base.opened,System.currentTimeMillis(),60000))}?:getString(R.string.workspace_never_opened)
    Row(id,it.text("name"),subtitle,vividMonogram=true,starred=recent?.starred==true)
   }else Row(it.text("id"),it.text("name"),if(root||invites)roleLabel(requireContext(),it.text("role")) else getString(R.string.p0_mo_bang_nhap_va_chinh_sua_du_lieu))
  }
  adapter.submitList(rows);b.empty.isVisible=rows.isEmpty()
  b.summary.text=getString(if(root)R.string.mobile_workspaces_count else if(invites)R.string.mobile_invitations_count else if(workspace)R.string.mobile_bases_count else R.string.mobile_tables_count,rows.size)
  b.empty.text=if(data==null)getString(R.string.p0_chua_co_du_lieu_da_tai_ket_noi) else if(root)getString(R.string.p0_chao_mung_den_3ddk_tao_khong_gian_dau) else if(invites)getString(R.string.p0_ban_chua_co_loi_moi_ban_van_co) else getString(R.string.p0_chua_co_du_lieu_bam_nut_tao_phia)
 }
 private fun open(id:String){
  if(mode.isNotEmpty()){
   val data=vm.data.value?:return
   when(id){
    "settings-name"->if(data.text("role")=="OWNER")renameWorkspace(data)
    "settings-members"->workspaceScreen("share")
    "settings-invite"->invite()
    "settings-invites"->sentInvites()
    "settings-delete"->deleteWorkspace(data)
    else->if(id.startsWith("member/")&&data.text("role")=="OWNER")data.objects("members").firstOrNull{it.text("uid")==id.removePrefix("member/")&&it.text("role")!="OWNER"}?.let{manageMember(it)}
   };return
  }
  if(id.startsWith("bases/")||id.startsWith("workspaces/")){findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to id));return}
  if(path=="workspaces"&&id=="home-shared"){
   if(vm.supports("baseActions")){sharedBases();return}
   val shared=vm.data.value?.objects("items").orEmpty().filter{it.text("role") in listOf("EDITOR","VIEWER")}
   val builder=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.home_shared).setNegativeButton(R.string.p0_dong,null)
   if(shared.isEmpty())builder.setMessage(R.string.home_shared_empty)
   else builder.setItems(shared.map{it.text("name")}.toTypedArray()){_,index->findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "workspaces/"+shared[index].text("id")))}
   dialog=builder.show();return
  }
  if(path=="workspaces"&&homeTab==R.id.recent_tab){
   if(vm.recentBases.value.any{it.path==id})findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to id))
   return
  }
  if(path=="workspaces"&&homeTab==R.id.starred_tab){
   if(vm.recentBases.value.any{it.path==id&&it.starred})findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to id))
   return
  }
  if(path=="invitations"){
   dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.p0_tham_gia_khong_gian)).setMessage(getString(R.string.p0_ban_se_co_quyen_nhu_ghi_trong_loi)).setPositiveButton(getString(R.string.p0_join_workspace)){_,_->vm.mutate("POST","invitations/$id/accept",JsonObject())}.setNegativeButton(getString(R.string.p0_de_sau),null).show();return
  }
  val next=if(path=="workspaces")"workspaces/$id" else if(path.startsWith("workspaces/"))"bases/$id" else "tables/$id"
  if(next.startsWith("tables/"))findNavController().navigate(R.id.dataTable,bundleOf("path" to next,"basePath" to path,"baseName" to vm.data.value?.text("name")))
  else findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to next))
 }
 private fun baseActions(id:String){
  val base=vm.recentBases.value.firstOrNull{it.path==id}?:vm.data.value?.objects("bases")?.firstOrNull{"bases/"+it.text("id")==id}?.let{WorkspaceRepository.RecentBase(id,it.text("name"),0,workspaceId=path.substringAfter('/'))}?:return
  baseSheet?.dismiss()
  val b=SheetBaseActionsBinding.inflate(layoutInflater)
  val sheet=com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
  baseSheet=sheet;sheet.setContentView(b.root)
  b.baseName.text=base.name
  b.toggleStar.setText(if(base.starred)R.string.home_unstar else R.string.home_star)
  b.openData.setOnClickListener{sheet.dismiss();open(id)}
  b.toggleStar.setOnClickListener{sheet.dismiss();vm.toggleBaseStar(id)}
  b.goWorkspace.isVisible=base.workspaceId.isNotBlank()
  b.goWorkspace.setOnClickListener{sheet.dismiss();findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "workspaces/"+base.workspaceId))}
  if(vm.supports("baseActions")){
   vm.readResource(id){current->
    if(binding!=null&&sheet.isShowing&&current.text("role")=="OWNER"){
     fun action(label:String,icon:Int,block:()->Unit){
      val button=com.google.android.material.button.MaterialButton(requireContext(),null,com.google.android.material.R.attr.borderlessButtonStyle).apply{
       text=label;gravity=android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
       minHeight=(52*resources.displayMetrics.density).toInt();setIconResource(icon);iconPadding=(16*resources.displayMetrics.density).toInt()
       setOnClickListener{sheet.dismiss();block()}
      }
      b.root.addView(button)
     }
     action("Chia sẻ cơ sở dữ liệu",R.drawable.ic_shared_outline){baseSharing(id)}
     action("Di chuyển",R.drawable.ic_workspace_outline){moveBase(id,current)}
     action("Nhân bản",R.drawable.ic_database_outline){duplicateBase(id,current)}
     action("Xóa vào thùng rác",R.drawable.ic_close){confirmBaseAction("Xóa ${base.name}?","Dữ liệu sẽ được giữ trong Thùng rác để chủ không gian khôi phục. Người cộng tác không thể mở trong thời gian này.","DELETE",id,current)}
    }
   }
  }
  sheet.show()
  sheet.behavior.state=com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
 }
 private fun baseBody(base:JsonObject)=JsonObject().apply{add("expectedVersion",base["version"])}
 private fun confirmBaseAction(title:String,message:String,method:String,target:String,base:JsonObject,extra:JsonObject=JsonObject()){
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message).setNegativeButton(R.string.p0_huy,null).setPositiveButton(R.string.p0_xac_nhan){_,_->
   vm.mutate(method,target,baseBody(base).apply{extra.entrySet().forEach{add(it.key,it.value)}})
  }.show()
 }
 private fun moveBase(id:String,base:JsonObject){
  vm.readResource("workspaces"){workspaces->if(binding!=null){
   val targets=workspaces.objects("items").filter{it.text("role")=="OWNER"&&it.text("id")!=base.text("workspaceId")}
   val builder=MaterialAlertDialogBuilder(requireContext()).setTitle("Chuyển đến không gian").setNegativeButton(R.string.p0_huy,null)
   if(targets.isEmpty())builder.setMessage("Bạn cần sở hữu một không gian khác để di chuyển.")
   else builder.setItems(targets.map{it.text("name")}.toTypedArray()){_,index->
    confirmBaseAction("Di chuyển cơ sở dữ liệu?","Quyền từ không gian cũ sẽ mất; thành viên không gian mới sẽ có quyền. Người được chia sẻ riêng vẫn giữ quyền.","POST","$id/move",base,JsonObject().apply{addProperty("workspaceId",targets[index].text("id"))})
   };dialog=builder.show()
  }}
 }
 private fun duplicateBase(id:String,base:JsonObject){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint="Tên bản sao";b.value.setText(base.text("name")+" (bản sao)");b.extraBox.isVisible=false;b.choice.isVisible=false
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle("Nhân bản cơ sở dữ liệu").setMessage("Sao chép bảng, dòng đang dùng và chế độ xem bạn được phép xem. Không sao chép lời mời, người chia sẻ riêng hoặc lịch sử.").setView(b.root).setNegativeButton(R.string.p0_huy,null).setPositiveButton("Nhân bản",null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_nhap_ten_tu_1_den_100_ky_tu);return@setOnClickListener}
   vm.mutate("POST","$id/duplicate",baseBody(base).apply{addProperty("name",name)}){result->if(binding!=null){d.dismiss();findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "bases/"+result.text("id")))}}
  }};d.show()
 }
 private fun trash(){
  if(!vm.supports("workspaceTrash")){baseTrash();return}
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Thùng rác").setItems(arrayOf("Không gian đã xóa","Cơ sở dữ liệu đã xóa")){_,which->if(which==1)baseTrash() else workspaceTrash()}.setNegativeButton(R.string.p0_dong,null).show()
 }
 private fun workspaceTrash(){vm.readResource("workspace-trash"){data->if(binding!=null){
  val items=data.objects("items");val builder=MaterialAlertDialogBuilder(requireContext()).setTitle("Không gian đã xóa").setNegativeButton(R.string.p0_dong,null)
  if(items.isEmpty())builder.setMessage("Không có không gian đã xóa.")
  else builder.setItems(items.map{it.text("name")}.toTypedArray()){_,index->val item=items[index];confirmBaseAction("Khôi phục ${item.text("name")}?","Dữ liệu và quyền của các thành viên sẽ được khôi phục. Những cơ sở dữ liệu đã xóa riêng vẫn nằm trong thùng rác.","POST","workspaces/${item.text("id")}/restore",item)}
  dialog=builder.show()
 }}}
 private fun baseTrash(){vm.readResource("trash"){data->if(binding!=null){
  val items=data.objects("items");val builder=MaterialAlertDialogBuilder(requireContext()).setTitle("Thùng rác").setNegativeButton(R.string.p0_dong,null)
  if(items.isEmpty())builder.setMessage("Không có cơ sở dữ liệu đã xóa.")
  else builder.setItems(items.map{it.text("name")}.toTypedArray()){_,index->val base=items[index];confirmBaseAction("Khôi phục ${base.text("name")}?","Người có quyền trước khi xóa sẽ truy cập lại được.","POST","bases/${base.text("id")}/restore",base)}
  dialog=builder.show()
 }}}
 private fun baseInvitations(){vm.readResource("base-invitations"){data->if(binding!=null){
  val items=data.objects("items");val builder=MaterialAlertDialogBuilder(requireContext()).setTitle("Lời mời cơ sở dữ liệu").setNegativeButton(R.string.p0_dong,null)
  if(items.isEmpty())builder.setMessage("Chưa có lời mời đang chờ.")
  else builder.setItems(items.map{it.text("name")+" · "+roleLabel(requireContext(),it.text("role"))}.toTypedArray()){_,index->
   val invite=items[index];dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Tham gia ${invite.text("name")}? ").setNegativeButton(R.string.p0_huy,null).setPositiveButton(R.string.p0_xac_nhan){_,_->vm.mutate("POST","base-invitations/${invite.text("id")}/accept",JsonObject()){result->if(binding!=null)findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "bases/"+result.text("id")))}}.show()
  };dialog=builder.show()
 }}}
 private fun sharedBases(){vm.readResource("shared-bases"){data->if(binding!=null){
  val rows=vm.data.value?.objects("items").orEmpty().filter{it.text("role") in listOf("EDITOR","VIEWER")}.map{"workspaces/"+it.text("id") to it.text("name")}+data.objects("items").map{"bases/"+it.text("id") to it.text("name")}
  val builder=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.home_shared).setNegativeButton(R.string.p0_dong,null)
  if(rows.isEmpty())builder.setMessage(R.string.home_shared_empty) else builder.setItems(rows.map{it.second}.toTypedArray()){_,index->findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to rows[index].first))}
  dialog=builder.show()
 }}}
 private fun baseSharing(id:String){vm.readResource("$id/sharing"){data->if(binding!=null){
  val members=data.objects("members");val invites=data.objects("invites")
  val labels=listOf("Mời qua email")+members.map{it.text("email")+" · "+roleLabel(requireContext(),it.text("role"))}+invites.map{"Đang chờ: "+it.text("email")}
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Chia sẻ riêng cơ sở dữ liệu").setItems(labels.toTypedArray()){_,index->
   when{
    index==0->inviteBase(id,data)
    index<=members.size->{val member=members[index-1];dialog=MaterialAlertDialogBuilder(requireContext()).setTitle(member.text("email")).setItems(arrayOf("Có thể chỉnh sửa","Chỉ xem","Gỡ quyền chia sẻ riêng")){_,which->
     confirmBaseAction("Cập nhật quyền?","Quyền được cấp từ không gian (nếu có) vẫn giữ nguyên.",if(which==2)"DELETE" else "PATCH","$id/members/${member.text("uid")}",data,JsonObject().apply{if(which<2)addProperty("role",if(which==0)"EDITOR" else "VIEWER")})
    }.show()}
    else->{val invite=invites[index-members.size-1];confirmBaseAction("Thu hồi lời mời?",invite.text("email"),"DELETE","$id/invites/${invite.text("id")}",data)}
   }
  }.setNegativeButton(R.string.p0_dong,null).show()
 }}}
 private fun inviteBase(id:String,base:JsonObject){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_email_duoc_moi);b.value.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;b.extraBox.isVisible=false
  b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Có thể chỉnh sửa","Chỉ xem"))
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle("Mời vào cơ sở dữ liệu").setMessage("Người nhận mở mục Lời mời cơ sở dữ liệu bằng đúng email này. Không cấp quyền vào các cơ sở dữ liệu khác.").setView(b.root).setNegativeButton(R.string.p0_huy,null).setPositiveButton(R.string.p0_moi,null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val email=b.value.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){b.valueBox.error=getString(R.string.p0_email_chua_hop_le);return@setOnClickListener}
   vm.mutate("POST","$id/invites",baseBody(base).apply{addProperty("email",email);addProperty("role",if(b.choice.selectedItemPosition==0)"EDITOR" else "VIEWER")}){if(binding!=null)d.dismiss()}
  }};d.show()
 }
 private fun create(){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten);b.extraBox.isVisible=false
  val canUseTemplate=path.startsWith("bases/")&&vm.data.value?.text("role")=="OWNER"
  b.choice.isVisible=canUseTemplate
  if(canUseTemplate){b.choice.contentDescription=getString(R.string.p0_loai_bang);b.choice.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.p0_bang_cong_viec_co_san_cot),getString(R.string.p0_bang_trong)))}
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(binding?.add?.text).setView(b.root).setPositiveButton(getString(R.string.p0_tao),null).setNegativeButton(getString(R.string.p0_huy),null).create();dialog=d
  fun created(result:JsonObject){if(binding!=null&&d.isShowing){
   d.dismiss()
   if(path=="workspaces")findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "workspaces/"+result.text("id")))
   else open(result.text("id"))
  }}
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_nhap_ten_tu_1_den_100_ky_tu);return@setOnClickListener}
   val target=if(path=="workspaces")path else if(path.startsWith("workspaces/"))"$path/bases" else "$path/tables"
   if(canUseTemplate&&b.choice.selectedItemPosition==0)vm.createTaskTable(name,::created)
   else vm.mutate("POST",target,JsonObject().apply{addProperty("name",name)},::created)
  }};d.show()
 }
 override fun onCreateOptionsMenu(menu:Menu,inflater:MenuInflater){
  if(mode.isNotEmpty())return
  if(path.startsWith("workspaces/")&&vm.data.value!=null){
   if(vm.data.value?.text("role") in listOf("OWNER","EDITOR"))menu.add(0,41,1,R.string.p0_tao_co_so_du_lieu)
   menu.add(0,42,2,R.string.p0_lam_moi)
   menu.add(0,40,0,R.string.workspace_options).setIcon(R.drawable.ic_workspace_more).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
   return
  }
  if(path.startsWith("bases/")&&vm.data.value!=null){
   val starred=vm.recentBases.value.any{it.path==path&&it.starred}
   menu.add(0,R.id.toggle_table_star,0,getString(if(starred)R.string.home_unstar else R.string.home_star))
  }
  if(path.startsWith("workspaces/")&&vm.data.value!=null){menu.add(0,1,0,getString(R.string.p0_thanh_vien));if(vm.data.value?.text("role")=="OWNER"){menu.add(0,2,1,getString(R.string.p0_moi_thanh_vien));menu.add(0,3,2,getString(R.string.p0_loi_moi_da_gui))}}
 }
 override fun onOptionsItemSelected(item:MenuItem):Boolean{
  if(item.itemId==41){create();return true}
  if(item.itemId==42){vm.refresh();return true}
  if(item.itemId==40){vm.data.value?.let{workspaceActions(path,it)};return true}
  if(item.itemId==R.id.toggle_table_star){vm.toggleBaseStar(path);return true}
  if(item.itemId==1){members();return true}
  if(item.itemId==2){invite();return true}
  if(item.itemId==3){sentInvites();return true}
  return super.onOptionsItemSelected(item)
 }
 private fun workspaceScreen(screen:String,target:String=path){
  findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to target,"mode" to screen))
 }
 private fun workspaceActions(target:String=path,data:JsonObject){
  val owner=data.text("role")=="OWNER"
  val b=SheetWorkspaceActionsBinding.inflate(layoutInflater)
  val sheet=com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());baseSheet?.dismiss();baseSheet=sheet;sheet.setContentView(b.root)
  b.starWorkspace.setText(if(target in vm.workspaceStars.value)R.string.workspace_unstar else R.string.workspace_star)
  b.starWorkspace.setOnClickListener{sheet.dismiss();vm.toggleWorkspaceStar(target)}
  b.shareWorkspace.setOnClickListener{sheet.dismiss();workspaceScreen("share",target)}
  b.renameWorkspace.text="Chỉnh sửa không gian"
  b.renameWorkspace.isVisible=owner&&vm.supports("workspaceActions")
  b.renameWorkspace.setOnClickListener{sheet.dismiss();workspaceScreen("edit",target)}
  sheet.show()
 }
 private fun renderWorkspaceSettings(data:JsonObject?){
  val b=binding?:return
  (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title=if(mode=="share")"Quản lý thành viên" else "Chỉnh sửa không gian"
  b.actions.isVisible=false;b.heading.isVisible=false;b.searchBox.isVisible=false
  b.summary.isVisible=mode=="share"
  b.summary.text="Thành viên không gian có quyền truy cập tất cả cơ sở dữ liệu bên trong."
  b.summary.textSize=14f
  b.sync.isVisible=vm.error.value!=null
  b.empty.isVisible=data==null
  if(data==null){adapter.submitList(emptyList());return}
  val members=data.objects("members");val owner=data.text("role")=="OWNER"
  val rows=if(mode=="edit")buildList{
   add(Row("settings-name",data.text("name"),"TÊN KHÔNG GIAN",iconRes=R.drawable.ic_edit_workspace))
   add(Row("settings-members","Thành viên không gian",members.size.toString()+" thành viên · "+members.joinToString{it.text("displayName")},iconRes=R.drawable.ic_workspace_outline))
   if(owner&&vm.supports("workspaceTrash"))add(Row("settings-delete","Xóa không gian","Đưa vào thùng rác, có thể khôi phục",important=true,iconRes=R.drawable.ic_close))
  }else buildList{
   if(owner){add(Row("settings-invite","Thêm thành viên không gian","Mời bằng email và chọn quyền truy cập",iconRes=R.drawable.ic_shared_outline))
    if(data.objects("invites").isNotEmpty())add(Row("settings-invites","Lời mời đang chờ",data.objects("invites").size.toString()+" lời mời",iconRes=R.drawable.ic_shared_outline))}
   members.forEach{add(Row("member/"+it.text("uid"),it.text("displayName"),it.text("email")+" · "+roleLabel(requireContext(),it.text("role")),avatar=it.text("avatarUrl")))}
  }
  adapter.submitList(rows)
 }
 private fun deleteWorkspace(data:JsonObject){
  dialog=MaterialAlertDialogBuilder(requireContext()).setTitle("Xóa không gian “${data.text("name")}”?")
   .setMessage("Toàn bộ cơ sở dữ liệu bên trong sẽ tạm ngừng truy cập. Dữ liệu và thành viên được giữ lại; chỉ chủ không gian có thể khôi phục từ Thùng rác.")
   .setNegativeButton(R.string.p0_huy,null).setPositiveButton("Đưa vào thùng rác"){_,_->
    vm.mutate("DELETE",path,baseBody(data)){if(binding!=null)findNavController().navigate(R.id.workspaceBrowser,bundleOf("path" to "workspaces"),androidx.navigation.navOptions{popUpTo(R.id.workspaces){inclusive=false}})}
   }.show()
 }
 private fun renameWorkspace(data:JsonObject){
  val b=DialogInputBinding.inflate(layoutInflater);b.valueBox.hint=getString(R.string.p0_ten);b.value.setText(data.text("name"));b.extraBox.isVisible=false;b.choice.isVisible=false
  val d=MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.workspace_rename).setView(b.root).setPositiveButton("Lưu",null).setNegativeButton(R.string.p0_huy,null).create();dialog=d
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val name=b.value.text.toString().trim();if(name.length !in 1..100){b.valueBox.error=getString(R.string.p0_nhap_ten_tu_1_den_100_ky_tu);return@setOnClickListener}
   vm.mutate("PATCH",path,JsonObject().apply{addProperty("name",name);add("expectedVersion",data["version"])}){if(binding!=null)d.dismiss()}
  }};d.show()
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
 override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("homeSearchExpanded",binding?.searchBox?.isVisible==true);outState.putInt("homeTab",homeTab);super.onSaveInstanceState(outState)}
 override fun onDestroyView(){baseSheet?.dismiss();baseSheet=null;dialog?.dismiss();dialog=null;binding?.list?.adapter=null;binding=null;super.onDestroyView()}
}
fun roleLabel(context:android.content.Context,role:String)=context.getString(when(role){"OWNER"->R.string.p0_workspace_owner;"EDITOR"->R.string.p0_co_the_chinh_sua;else->R.string.p0_chi_xem})
