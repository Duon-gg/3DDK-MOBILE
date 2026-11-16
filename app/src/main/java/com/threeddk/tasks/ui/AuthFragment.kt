package com.threeddk.tasks.ui
import com.threeddk.tasks.R
import com.threeddk.tasks.model.text
import com.threeddk.tasks.viewmodel.AppViewModel
import com.threeddk.tasks.viewmodel.AuthEntryViewModel
import com.threeddk.tasks.adapter.WelcomeAdapter
import com.threeddk.tasks.data.googleIdToken
import com.threeddk.tasks.data.googleWebClientId
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.threeddk.tasks.databinding.ItemWelcomeBinding
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.threeddk.tasks.databinding.FragmentAuthBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
class AuthFragment:Fragment(){
 private var binding:FragmentAuthBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private val entry:AuthEntryViewModel by viewModels()
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentAuthBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;b.email.setText(vm.savedEmail());b.remember.isChecked=b.email.text?.isNotEmpty()==true
  val layout=LinearLayoutManager(requireContext(),RecyclerView.HORIZONTAL,false)
  b.welcomePages.layoutManager=layout
  b.welcomePages.adapter=WelcomeAdapter(entry.pages)
  val snap=PagerSnapHelper();snap.attachToRecyclerView(b.welcomePages)
  fun indicator(index:Int){
   b.pageIndicator.text=(0..2).joinToString(" "){if(it==index)"●" else "○"}
   b.pageIndicator.contentDescription=getString(R.string.welcome_page,index+1)
  }
  indicator(0)
  b.welcomePages.addOnScrollListener(object:RecyclerView.OnScrollListener(){
   override fun onScrollStateChanged(recycler:RecyclerView,newState:Int){
    if(newState==RecyclerView.SCROLL_STATE_IDLE)snap.findSnapView(layout)?.let{indicator(layout.getPosition(it))}
   }
  })
  b.welcomeLogin.setOnClickListener{entry.choose(false)}
  b.welcomeRegister.setOnClickListener{entry.choose(true)}
  b.formBack.setOnClickListener{entry.back()}
  val back=object:OnBackPressedCallback(false){override fun handleOnBackPressed(){entry.back()}}
  requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,back)
  fun renderEntry(){
   val auth=vm.session.value=="AUTH"
   val welcome=auth&&entry.screen.value=="WELCOME"
   val register=entry.screen.value=="REGISTER"
   b.welcome.isVisible=welcome;b.authForm.isVisible=!welcome
   b.formBack.isVisible=auth;back.isEnabled=auth&&!welcome
   b.orLabel.isVisible=auth
   b.formTitle.setText(if(register)R.string.welcome_create else R.string.welcome_title)
   if(auth){
    b.status.isVisible=false
    b.passwordBox.isVisible=entry.passwordStep.value
    b.remember.isVisible=entry.passwordStep.value
    b.login.setText(if(!entry.passwordStep.value)R.string.welcome_continue else if(register)R.string.welcome_register else R.string.welcome_login)
    b.register.setText(if(register)R.string.welcome_login else R.string.welcome_register)
    b.reset.isVisible=!register
   }else b.status.isVisible=true
  }
  watch(entry.screen){renderEntry()};watch(entry.passwordStep){renderEntry()}
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
  b.login.setOnClickListener{when(vm.session.value){"AUTH"->{if(!entry.passwordStep.value){if(android.util.Patterns.EMAIL_ADDRESS.matcher(b.email.text.toString().trim()).matches()){b.emailBox.error=null;entry.continueEmail()}else b.emailBox.error=getString(R.string.welcome_email)}else credentials(entry.screen.value=="REGISTER")};"VERIFY"->vm.verifyAgain();"BLOCKED"->vm.refresh()}}
  b.register.setOnClickListener{when(vm.session.value){"AUTH"->entry.choose(entry.screen.value!="REGISTER");"VERIFY"->vm.resendVerification();"BLOCKED"->vm.logout()}}
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
    "VERIFY"->{b.status.text="Kiểm tra email ${vm.accountEmail} và mở liên kết xác minh.";b.login.text="Tôi đã xác minh";b.register.text="Gửi lại email";b.reset.text="Đăng xuất"}
    "BLOCKED"->{b.status.text="Tài khoản chưa được mời hoặc đã ngừng quyền. Liên hệ nhóm trưởng.";b.login.text="Kiểm tra lại quyền";b.register.text="Đăng xuất"}
   }
   renderEntry()
  }
  watch(vm.loading){buttons()}
 }
 override fun onDestroyView(){binding?.welcomePages?.adapter=null;binding=null;super.onDestroyView()}
}

