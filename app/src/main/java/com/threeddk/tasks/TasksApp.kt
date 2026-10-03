package com.threeddk.tasks
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.threeddk.tasks.data.TaskRepository
class TasksApp:Application(){
 val repository by lazy {TaskRepository(this)}
 override fun onCreate(){
  super.onCreate()
  if(BuildConfig.FIREBASE_APP_ID.isNotBlank()&&BuildConfig.FIREBASE_API_KEY.isNotBlank()&&BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()&&FirebaseApp.getApps(this).isEmpty()){
   FirebaseApp.initializeApp(this,FirebaseOptions.Builder().setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
  }
  if(android.os.Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("tasks","Công việc và deadline",NotificationManager.IMPORTANCE_DEFAULT))
 }
}

