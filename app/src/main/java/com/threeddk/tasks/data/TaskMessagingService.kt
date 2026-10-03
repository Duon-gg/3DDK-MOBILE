package com.threeddk.tasks.data
import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.threeddk.tasks.*
import kotlinx.coroutines.*
class TaskMessagingService:FirebaseMessagingService(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onNewToken(token:String){scope.launch{runCatching{(application as TasksApp).repository.registerDevice()}}}
 override fun onMessageReceived(message:RemoteMessage){
  val repo=(application as TasksApp).repository
  if(repo.uid==null||message.data["recipientUid"]!=repo.uid)return
  if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val id=message.data["notificationId"]?:return
  val intent=Intent(this,MainActivity::class.java).putExtra("taskId",message.data["taskId"]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val pending=PendingIntent.getActivity(this,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  NotificationManagerCompat.from(this).notify(id,id.hashCode(),NotificationCompat.Builder(this,"tasks").setSmallIcon(R.drawable.ic_app).setContentTitle("3DDK Tasks").setContentText(message.notification?.body?:"Nhóm có cập nhật công việc.").setContentIntent(pending).setAutoCancel(true).build())
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}

