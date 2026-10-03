package com.threeddk.tasks.ui
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.*
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.threeddk.tasks.*
import com.threeddk.tasks.databinding.FragmentProfileBinding
class ProfileFragment:Fragment(){
 private var binding:FragmentProfileBinding?=null
 private val vm:AppViewModel by activityViewModels()
 private var avatar=""
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){renderNotifications();vm.execute{vm.repo.registerDevice()}}
 override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?)=FragmentProfileBinding.inflate(inflater,container,false).also{binding=it}.root
 override fun onViewCreated(view:View,state:Bundle?){
  val b=binding!!;val me=vm.me;b.name.setText(me?.displayName);b.email.text=me?.email?:vm.repo.auth?.currentUser?.email;avatar=state?.getString("avatar")?:me?.avatarUrl.orEmpty()
  fun image(){Glide.with(this).load(avatar).placeholder(R.drawable.ic_person).error(R.drawable.ic_person).circleCrop().into(b.avatar)}
  image()
  b.changeAvatar.setOnClickListener{MaterialAlertDialogBuilder(requireContext()).setTitle("Chọn avatar").setItems((1..6).map{"Avatar $it"}.toTypedArray()){_,index->avatar="https://api.dicebear.com/9.x/shapes/png?seed=${index+1}";image()}.show()}
  b.save.setOnClickListener{val name=b.name.text.toString().trim();if(name.isBlank()){b.nameBox.error="Nhập tên hiển thị.";return@setOnClickListener};vm.mutation("PATCH","me",vm.repo.body("displayName" to name,"avatarUrl" to avatar)){vm.message.value="Đã lưu hồ sơ."}}
  b.notifications.setOnClickListener{
   if(android.os.Build.VERSION.SDK_INT>=33&&!NotificationManagerCompat.from(requireContext()).areNotificationsEnabled())permission.launch(Manifest.permission.POST_NOTIFICATIONS)
   else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,requireContext().packageName))
  }
  b.logout.setOnClickListener{vm.execute{
   val count=vm.repo.uid?.let{vm.repo.dao.draftCount(it)}?:0
   if(!isAdded)return@execute
   MaterialAlertDialogBuilder(requireContext()).setTitle("Đăng xuất?").setMessage(if(count>0)"Có $count bản nháp chưa gửi. Đăng xuất sẽ xóa bản nháp và dữ liệu cục bộ trên thiết bị này." else "Dữ liệu đã tải trên thiết bị sẽ được xóa.").setPositiveButton("Đăng xuất"){_,_->vm.logout()}.setNegativeButton("Ở lại",null).show()
  }}
  watch(vm.loading){b.save.isEnabled=!it;b.logout.isEnabled=!it};renderNotifications()
 }
 private fun renderNotifications(){binding?.notificationStatus?.text=if(NotificationManagerCompat.from(requireContext()).areNotificationsEnabled())"Thông báo trên điện thoại đang bật." else "Thông báo trên điện thoại đang tắt. Bạn vẫn xem được hộp thông báo trong app."}
 override fun onResume(){super.onResume();renderNotifications()}
 override fun onSaveInstanceState(out:Bundle){out.putString("avatar",avatar);super.onSaveInstanceState(out)}
 override fun onDestroyView(){binding=null;super.onDestroyView()}
}

