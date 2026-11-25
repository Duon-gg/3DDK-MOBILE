package com.threeddk.tasks.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonObject
import com.threeddk.tasks.R
import com.threeddk.tasks.adapter.RowAdapter
import com.threeddk.tasks.databinding.DialogCollaboratorsBinding
import com.threeddk.tasks.databinding.ItemCollaboratorBinding
import com.threeddk.tasks.model.objects
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.WorkspaceViewModel

/**
 * Link to share. With https support it is a normal web link (opens the app through the "join" function, works in Zalo,
 * Messenger and browsers); otherwise the app's own scheme. Both are accepted by "Tham gia bằng link mời".
 */
fun inviteLinkUrl(token:String,https:Boolean=false)=
 if(https&&com.threeddk.tasks.BuildConfig.SUPABASE_URL.isNotBlank())com.threeddk.tasks.BuildConfig.SUPABASE_URL.trimEnd('/')+"/functions/v1/join/$token" else "threeddk://join/$token"
/** Accepts a full link, a link inside shared text, or the bare 32-char token. */
fun inviteToken(text:String)=Regex("""(?:join/)?([0-9a-f]{32})""").find(text.trim())?.groupValues?.get(1)

/**
 * Airtable-style "Manage collaborators" for one base: invite by email, invite link, and everyone with access.
 * [base] is the path "bases/{id}". Only owners can open it (the backend enforces this too).
 */
fun Fragment.showCollaborators(vm:WorkspaceViewModel,base:String,baseName:String){
 val ctx=requireContext();val sheet=BottomSheetDialog(ctx);val b=DialogCollaboratorsBinding.inflate(layoutInflater)
 // Links never hand out admin rights; email invites and promotions can (owner or admin only).
 val roles=listOf("EDITOR","VIEWER");val roleNames=roles.map{roleLabel(ctx,it)}
 val memberRoles=(if(vm.supports("adminRole"))listOf("ADMIN") else emptyList())+roles;val memberRoleNames=memberRoles.map{roleLabel(ctx,it)}
 b.subtitle.text=baseName
 b.emailRole.adapter=ArrayAdapter(ctx,android.R.layout.simple_spinner_dropdown_item,memberRoleNames);b.emailRole.setSelection(memberRoles.indexOf("EDITOR"))
 b.linkRole.adapter=ArrayAdapter(ctx,android.R.layout.simple_spinner_dropdown_item,roleNames)
 var sharing=JsonObject()
 var load:()->Unit={}

 fun render(data:JsonObject){
  sharing=data
  val link=data.get("inviteLink")?.takeIf{it.isJsonObject}?.asJsonObject
  b.linkState.text=if(link==null)ctx.getString(R.string.collab_link_none) else ctx.getString(R.string.collab_link_active,roleLabel(ctx,link.text("role")))
  b.linkCreate.setText(if(link==null)R.string.collab_link_create else R.string.collab_link_regenerate)
  b.linkShare.isEnabled=link!=null;b.linkOff.isEnabled=link!=null
  link?.let{b.linkRole.setSelection(roles.indexOf(it.text("role")).coerceAtLeast(0))}

  val people=data.objects("people");val members=data.objects("members").associateBy{it.text("uid")};val invites=data.objects("invites")
  b.peopleTitle.text=ctx.getString(R.string.collab_people,people.size+invites.size)
  b.people.removeAllViews()
  people.forEach{p->
   val row=ItemCollaboratorBinding.inflate(layoutInflater,b.people,false)
   val name=p.text("displayName").ifBlank{p.text("uid")};val member=members[p.text("uid")]
   val tile=RowAdapter.tileFor(p.text("uid"))
   row.avatar.text=name.take(1).uppercase();row.avatar.setTextColor(tile.second)
   row.avatar.background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(tile.first)}
   row.name.text=name
   row.detail.text=member?.text("email")?:ctx.getString(R.string.collab_from_workspace)
   row.role.text=roleLabel(ctx,p.text("role"))
   // Only access granted on this base can be changed here; workspace access is managed on the workspace.
   if(member!=null&&p.text("role")!="OWNER"&&p.text("uid")!=runCatching{vm.myUid()}.getOrNull()){
    row.root.contentDescription=ctx.getString(R.string.collab_change_role,name)
    row.root.setOnClickListener{
     MaterialAlertDialogBuilder(ctx).setTitle(name).setItems((memberRoleNames+ctx.getString(R.string.collab_remove)).toTypedArray()){_,which->
      val remove=which==memberRoles.size
      vm.versionedMutation(if(remove)"DELETE" else "PATCH","$base/members/${p.text("uid")}",sharing,JsonObject().apply{if(!remove)addProperty("role",memberRoles[which])}){load()}
     }.setNegativeButton(R.string.p0_huy,null).show()
    }
   }else row.root.isClickable=false
   b.people.addView(row.root)
  }
  invites.forEach{i->
   val row=ItemCollaboratorBinding.inflate(layoutInflater,b.people,false)
   row.avatar.text="@";row.avatar.background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0xFFE5E9F0.toInt())}
   row.name.text=i.text("email");row.detail.setText(R.string.collab_pending);row.role.text=roleLabel(ctx,i.text("role"))
   row.root.contentDescription=ctx.getString(R.string.collab_revoke,i.text("email"))
   row.root.setOnClickListener{
    MaterialAlertDialogBuilder(ctx).setTitle(R.string.collab_revoke_title).setMessage(i.text("email")).setNegativeButton(R.string.p0_huy,null)
     .setPositiveButton(R.string.p0_xac_nhan){_,_->vm.versionedMutation("DELETE","$base/invites/${i.text("id")}",sharing){load()}}.show()
   }
   b.people.addView(row.root)
  }
 }
 load={vm.readNow("$base/sharing"){if(sheet.isShowing)render(it)}}

 b.invite.setOnClickListener{
  val email=b.email.text.toString().trim()
  if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()){b.emailBox.error=ctx.getString(R.string.p0_email_chua_hop_le);return@setOnClickListener}
  b.emailBox.error=null
  vm.inviteMember(base,email,memberRoles[b.emailRole.selectedItemPosition],sharing){b.email.setText("");load()}
 }
 b.linkCreate.setOnClickListener{vm.setInviteLink(base,roles[b.linkRole.selectedItemPosition]){load()}}
 b.linkOff.setOnClickListener{vm.setInviteLink(base,null){load()}}
 b.linkShare.setOnClickListener{
  val token=sharing.get("inviteLink")?.takeIf{it.isJsonObject}?.asJsonObject?.text("token")?:return@setOnClickListener
  val text=ctx.getString(R.string.collab_share_text,baseName,inviteLinkUrl(token,vm.supports("httpsInviteLinks")))
  startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),ctx.getString(R.string.collab_link_share)))
 }
 b.linkActions.isSingleSelection=false
 sheet.setContentView(b.root)
 sheet.behavior.state=BottomSheetBehavior.STATE_EXPANDED;sheet.behavior.skipCollapsed=true

 vm.error.value=null
 // Errors (duplicate invite, lost access, offline) would otherwise land on the screen hidden behind the sheet.
 val errors=viewLifecycleOwner.lifecycleScope.launch{vm.error.collect{if(it!=null&&sheet.isShowing)com.google.android.material.snackbar.Snackbar.make(b.root,it,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()}}
 sheet.setOnDismissListener{errors.cancel()}
 // Close with the screen (rotation, navigation) so late callbacks never touch a dead view.
 viewLifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY)sheet.dismiss()})
 sheet.show();load()
}

/** Join flow for a link: preview the base and role, then join and open it. */
fun Fragment.joinByInvite(vm:WorkspaceViewModel,token:String,opened:(String)->Unit){
 vm.previewInvite(token){p->
  if(!isAdded)return@previewInvite
  val current=p.text("currentRole")
  MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.collab_join_title,p.text("baseName")))
   .setMessage(if(current.isBlank())getString(R.string.collab_join_message,roleLabel(requireContext(),p.text("role"))) else getString(R.string.collab_join_already,roleLabel(requireContext(),current)))
   .setNegativeButton(R.string.p0_huy,null)
   .setPositiveButton(R.string.collab_join){_,_->vm.acceptInvite(token){r->opened("bases/"+r.text("id"))}}.show()
 }
}
