package com.threeddk.tasks.ui
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.AppViewModel
import com.threeddk.tasks.data.changePassword
import android.os.Bundle
import com.threeddk.tasks.data.accountAvatar
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.*
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.BuildConfig
import com.threeddk.tasks.R
import com.threeddk.tasks.databinding.FragmentProfileBinding
import com.threeddk.tasks.databinding.DialogChangePasswordBinding
class ProfileFragment:Fragment(){
 private var binding:FragmentProfileBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private var avatar=""
 private var passwordDialog:androidx.appcompat.app.AlertDialog?=null
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentProfileBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;val me=vm.me;b.name.setText(me?.displayName);b.email.text=me?.email?:vm.accountEmail;avatar=state?.getString("avatar")?:me?.avatarUrl.orEmpty()
  b.displayName.text=me?.displayName?.takeIf{it.isNotBlank()}?:vm.accountName
  b.changeAvatar.isVisible=vm.googleAvatar==null
  b.buildVersion.text=getString(R.string.profile_build,BuildConfig.VERSION_NAME)
  b.profileEditor.isVisible=state?.getBoolean("editing")==true
  b.editProfile.setOnClickListener{b.profileEditor.isVisible=!b.profileEditor.isVisible}
  fun image(){Glide.with(this).load(vm.avatar(avatar)).placeholder(R.drawable.ic_person).error(R.drawable.ic_person).circleCrop().into(b.avatar)}
  image()
  b.changeAvatar.setOnClickListener{MaterialAlertDialogBuilder(requireContext()).setTitle("Chọn avatar").setItems((1..6).map{"Avatar $it"}.toTypedArray()){_,index->avatar="https://api.dicebear.com/9.x/shapes/png?seed=${index+1}";image()}.show()}
  b.save.setOnClickListener{val name=b.name.text.toString().trim();if(name.isBlank()){b.nameBox.error="Nhập tên hiển thị.";return@setOnClickListener};vm.updateProfile(name,avatar){if(binding===b){b.displayName.text=name;b.profileEditor.isVisible=false}}}
  b.notifications.isVisible=false
  b.changePassword.isVisible=vm.canChangePassword
  b.changePassword.setOnClickListener{showPasswordDialog()}
  watch(vm.changingPassword){b.changePassword.isEnabled=!it;b.logout.isEnabled=!it&&!vm.loading.value}
  b.logout.setOnClickListener{vm.logoutDraftCount{count->
   if(!isAdded||binding!==b)return@logoutDraftCount
   MaterialAlertDialogBuilder(requireContext()).setTitle("Đăng xuất?").setMessage(if(count>0)"Có $count bản nháp chưa gửi. Đăng xuất sẽ xóa bản nháp và dữ liệu cục bộ trên thiết bị này." else "Dữ liệu đã tải trên thiết bị sẽ được xóa.").setPositiveButton("Đăng xuất"){_,_->vm.logout()}.setNegativeButton("Ở lại",null).show()
  }}
  watch(vm.loading){b.save.isEnabled=!it;b.logout.isEnabled=!it&&!vm.changingPassword.value};renderNotifications()
 }
 private fun showPasswordDialog(){
  if(!vm.canChangePassword||vm.changingPassword.value)return
  val form=DialogChangePasswordBinding.inflate(layoutInflater)
  val dialog=MaterialAlertDialogBuilder(requireContext())
   .setTitle(R.string.password_change)
   .setView(form.root)
   .setNegativeButton(R.string.p0_huy,null)
   .setPositiveButton(R.string.password_change,null)
   .create()
  passwordDialog=dialog
  dialog.setOnDismissListener{
   listOf(form.current,form.replacement,form.confirmation).forEach{it.text?.clear()}
   if(passwordDialog===dialog)passwordDialog=null
  }
  dialog.setOnShowListener{
   val submit=dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
   val cancel=dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
   submit.setOnClickListener{
    val current=form.current.text.toString()
    val replacement=form.replacement.text.toString()
    val confirmation=form.confirmation.text.toString()
    listOf(form.currentBox,form.replacementBox,form.confirmationBox).forEach{it.error=null}
    form.error.isVisible=false
    when{
     current.isEmpty()->form.currentBox.error=getString(R.string.password_current_required)
     replacement.length<6->form.replacementBox.error=getString(R.string.password_policy)
     current==replacement->form.replacementBox.error=getString(R.string.password_same)
     replacement!=confirmation->form.confirmationBox.error=getString(R.string.password_mismatch)
     else->{
      submit.isEnabled=false;cancel.isEnabled=false;dialog.setCancelable(false)
      vm.changePassword(current,replacement){error->
       if(binding==null||passwordDialog!==dialog)return@changePassword
       if(error==null){dialog.dismiss();return@changePassword}
       if(error==R.string.password_wrong_current){
        form.current.text?.clear()
        form.currentBox.error=getString(error)
        form.current.requestFocus()
       }else{
        form.error.setText(error)
        form.error.isVisible=true
       }
       submit.isEnabled=true;cancel.isEnabled=true;dialog.setCancelable(true)
      }
     }
    }
   }
  }
  dialog.show()
 }
 private fun renderNotifications(){binding?.notificationStatus?.setText(R.string.p0_notifications_pending)}
 override fun onResume(){super.onResume();renderNotifications()}
 override fun onSaveInstanceState(out:Bundle){out.putString("avatar",avatar);out.putBoolean("editing",binding?.profileEditor?.isVisible==true);super.onSaveInstanceState(out)}
 override fun onDestroyView(){passwordDialog?.dismiss();binding=null;super.onDestroyView()}
}

