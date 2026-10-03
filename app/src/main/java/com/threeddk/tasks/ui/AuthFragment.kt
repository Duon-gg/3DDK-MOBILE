package com.threeddk.tasks.ui
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.*
import com.threeddk.tasks.AppViewModel
import com.threeddk.tasks.databinding.FragmentAuthBinding
class AuthFragment:Fragment(){
 private var binding:FragmentAuthBinding?=null
 private val vm:AppViewModel by activityViewModels()
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentAuthBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;b.email.setText(vm.repo.savedEmail());b.remember.isChecked=b.email.text?.isNotEmpty()==true
  fun credentials(register:Boolean){
   val email=b.email.text.toString().trim();val password=b.password.text.toString()
   b.emailBox.error=if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())"Nhập email hợp lệ." else null
   b.passwordBox.error=if(password.length<6)"Mật khẩu cần ít nhất 6 ký tự." else null
   if(b.emailBox.error==null&&b.passwordBox.error==null)vm.login(email,password,register,b.remember.isChecked)
  }
  b.login.setOnClickListener{when(vm.session.value){"AUTH"->credentials(false);"VERIFY"->vm.verifyAgain();"BLOCKED"->vm.refresh()}}
  b.register.setOnClickListener{when(vm.session.value){"AUTH"->credentials(true);"VERIFY"->vm.resendVerification();"BLOCKED"->vm.logout()}}
  b.reset.setOnClickListener{if(vm.session.value=="VERIFY")vm.logout() else {
   val email=b.email.text.toString().trim()
   if(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())vm.reset(email) else b.emailBox.error="Nhập email để nhận hướng dẫn."
  }}
  watch(vm.session){session->
   val auth=session=="AUTH";b.emailBox.isVisible=auth;b.passwordBox.isVisible=auth;b.remember.isVisible=auth
   b.login.isVisible=session!="CONFIG";b.register.isVisible=session!="CONFIG";b.reset.isVisible=auth||session=="VERIFY"
   when(session){
    "CONFIG"->b.status.text="Ứng dụng chưa được kết nối Firebase/Supabase.\n\nNhóm phát triển cần điền cấu hình trong local.properties rồi dựng lại APK. Xem README trong repository 3DDK-MOBILE."
    "AUTH"->{b.status.text="Đăng nhập bằng email đã được nhóm trưởng mời.";b.login.text="Đăng nhập";b.register.text="Tạo tài khoản";b.reset.text="Quên mật khẩu"}
    "VERIFY"->{b.status.text="Kiểm tra email ${vm.repo.auth?.currentUser?.email} và mở liên kết xác minh.";b.login.text="Tôi đã xác minh";b.register.text="Gửi lại email";b.reset.text="Đăng xuất"}
    "BLOCKED"->{b.status.text="Tài khoản chưa được mời hoặc đã ngừng quyền. Liên hệ nhóm trưởng.";b.login.text="Kiểm tra lại quyền";b.register.text="Đăng xuất"}
   }
  }
  watch(vm.loading){busy->b.login.isEnabled=!busy;b.register.isEnabled=!busy;b.reset.isEnabled=!busy}
 }
 override fun onDestroyView(){binding=null;super.onDestroyView()}
}

