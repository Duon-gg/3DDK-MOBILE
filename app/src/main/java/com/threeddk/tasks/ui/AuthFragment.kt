package com.threeddk.tasks.ui
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.threeddk.tasks.AppViewModel
import com.threeddk.tasks.R
import com.threeddk.tasks.data.googleIdToken
import com.threeddk.tasks.data.googleWebClientId
import com.threeddk.tasks.databinding.FragmentAuthBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
class AuthFragment:Fragment(){
 private var binding:FragmentAuthBinding?=null
 private val vm:AppViewModel by activityViewModels()
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentAuthBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;b.email.setText(vm.repo.savedEmail());b.remember.isChecked=b.email.text?.isNotEmpty()==true
  var choosingGoogle=false
  fun buttons(){
   val enabled=!vm.loading.value&&!choosingGoogle
   b.login.isEnabled=enabled;b.register.isEnabled=enabled;b.reset.isEnabled=enabled;b.google.isEnabled=enabled
  }
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
  b.google.setOnClickListener{
   if(vm.session.value!="AUTH"||vm.loading.value||choosingGoogle)return@setOnClickListener
   val clientId=googleWebClientId(requireContext())
   if(clientId.isBlank()){vm.message.value=getString(R.string.auth_google_setup);return@setOnClickListener}
   choosingGoogle=true;buttons()
   viewLifecycleOwner.lifecycleScope.launch{
    try{vm.loginWithGoogle(googleIdToken(requireActivity(),clientId))}
    catch(e:GetCredentialCancellationException){/* The user dismissed the Google account picker. */}
    catch(e:CancellationException){throw e}
    catch(e:NoCredentialException){vm.message.value=getString(R.string.auth_google_no_account)}
    catch(e:GetCredentialException){vm.message.value=getString(R.string.auth_google_unavailable)}
    catch(e:GoogleIdTokenParsingException){vm.message.value=getString(R.string.auth_google_invalid)}
    catch(e:IllegalArgumentException){vm.message.value=getString(R.string.auth_google_invalid)}
    finally{choosingGoogle=false;if(binding!=null)buttons()}
   }
  }
  watch(vm.session){session->
   val auth=session=="AUTH";b.emailBox.isVisible=auth;b.passwordBox.isVisible=auth;b.remember.isVisible=auth;b.google.isVisible=auth
   b.login.isVisible=session!="CONFIG";b.register.isVisible=session!="CONFIG";b.reset.isVisible=auth||session=="VERIFY"
   when(session){
    "CONFIG"->b.status.text="Ứng dụng chưa được kết nối Firebase/Supabase.\n\nNhóm phát triển cần điền cấu hình trong local.properties rồi dựng lại APK. Xem README trong repository 3DDK-MOBILE."
    "AUTH"->{b.status.setText(R.string.auth_intro);b.login.text="Đăng nhập";b.register.text="Tạo tài khoản";b.reset.text="Quên mật khẩu"}
    "VERIFY"->{b.status.text="Kiểm tra email ${vm.repo.auth?.currentUser?.email} và mở liên kết xác minh.";b.login.text="Tôi đã xác minh";b.register.text="Gửi lại email";b.reset.text="Đăng xuất"}
    "BLOCKED"->{b.status.text="Tài khoản chưa được mời hoặc đã ngừng quyền. Liên hệ nhóm trưởng.";b.login.text="Kiểm tra lại quyền";b.register.text="Đăng xuất"}
   }
  }
  watch(vm.loading){buttons()}
 }
 override fun onDestroyView(){binding=null;super.onDestroyView()}
}

