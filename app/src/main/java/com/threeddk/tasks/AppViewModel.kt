package com.threeddk.tasks
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.google.gson.JsonObject
import com.threeddk.tasks.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class AppViewModel(app:Application):AndroidViewModel(app) {
 val repo=(app as TasksApp).repository
 val session=MutableStateFlow(if(repo.configured)"AUTH" else "CONFIG")
 val rows=MutableStateFlow<List<CacheEntry>>(emptyList())
 val drafts=MutableStateFlow<List<Draft>>(emptyList())
 val loading=MutableStateFlow(false)
 val message=MutableStateFlow<String?>(null)
 val syncNote=MutableStateFlow("Chưa đồng bộ")
 var pendingTaskId:String?=null
 private var observer:Job?=null
 val me get()=rows.value.firstOrNull{it.kind=="me"}?.let{repo.gson.fromJson(it.json,Member::class.java)}
 inline fun <reified T> records(kind:String)=rows.value.filter{it.kind==kind}.map{repo.gson.fromJson(it.json,T::class.java)}
 fun task(id:String)=records<Task>("tasks").firstOrNull{it.id==id}
 fun history(id:String)=rows.value.firstOrNull{it.kind=="history"&&it.id==id}?.let{repo.gson.fromJson(it.json,History::class.java)}?:History()
 init { if(repo.configured) execute{restore()} }
 fun execute(block:suspend()->Unit) {
  viewModelScope.launch {
   loading.value=true
   try {block()} catch(e:CancellationException){throw e} catch(e:Exception){
    message.value=when(e){is FirebaseAuthException->when(e.errorCode){"ERROR_INVALID_EMAIL"->"Email không hợp lệ.";"ERROR_EMAIL_ALREADY_IN_USE"->"Email này đã được đăng ký.";"ERROR_WEAK_PASSWORD"->"Mật khẩu cần ít nhất 6 ký tự.";else->"Không đăng nhập được. Kiểm tra email/mật khẩu hoặc thử lại."};else->friendlyError(e)}
   } finally {loading.value=false}
  }
 }
 private suspend fun restore() {
  val user=repo.auth?.currentUser
  if(user==null){session.value="AUTH";return}
  if(!user.isEmailVerified){session.value="VERIFY";return}
  observer?.cancel()
  observer=viewModelScope.launch{
   launch{repo.observe(user.uid).collect{rows.value=it}}
   launch{repo.dao.observeDrafts(user.uid).collect{drafts.value=it}}
  }
  if(repo.cachedMe()!=null)session.value="READY"
  refreshNow()
 }
 private suspend fun refreshNow() {
  try {
   repo.refresh();session.value="READY";syncNote.value="Đã đồng bộ • ${formattedTime(java.time.Instant.now().toString())}"
   runCatching{repo.registerDevice()}
  } catch(e:Exception){
   syncNote.value="Đang xem dữ liệu đã lưu • Chưa đồng bộ được"
   if(e is ApiException && e.code in listOf("NOT_INVITED","FORBIDDEN","UNVERIFIED","UNAUTHORIZED")) {
    rows.value=emptyList();session.value=if(e.code=="UNAUTHORIZED")"AUTH" else "BLOCKED"
   }
   throw e
  }
 }
 fun refresh()=execute{refreshNow()}
 fun login(email:String,password:String,register:Boolean,remember:Boolean)=execute{
  requireNotNull(repo.auth)
  if(register){repo.auth.createUserWithEmailAndPassword(email.trim(),password).await();repo.auth.currentUser!!.sendEmailVerification().await()}
  else repo.auth.signInWithEmailAndPassword(email.trim(),password).await()
  repo.rememberEmail(email.trim(),remember);restore()
 }
 fun verifyAgain()=execute{
  repo.auth?.currentUser?.reload()?.await()
  repo.auth?.currentUser?.getIdToken(true)?.await()
  restore()
 }
 fun resendVerification()=execute{repo.auth?.currentUser?.sendEmailVerification()?.await();message.value="Đã gửi email xác minh. Kiểm tra cả thư mục spam."}
 fun reset(email:String)=execute{repo.auth?.sendPasswordResetEmail(email.trim())?.await();message.value="Nếu email hợp lệ, bạn sẽ nhận hướng dẫn đặt lại mật khẩu."}
 fun logout()=execute{observer?.cancel();repo.logout();rows.value=emptyList();drafts.value=emptyList();session.value="AUTH";pendingTaskId=null}
 fun detail(id:String)=execute{repo.detail(id)}
 fun mutation(method:String,path:String,body:JsonObject,done:()->Unit={})=execute{
  try{repo.mutate(method,path,body)}catch(e:ApiException){if(e.code=="CONFLICT")runCatching{repo.refresh()};throw e}
  runCatching{repo.refresh()}.onFailure{syncNote.value="Đã lưu thay đổi • Chưa tải lại được"}
  done()
 }
 fun saveDraft(task:Task,description:String,link:String)=viewModelScope.launch {
  try{repo.saveDraft(task,description,link)}catch(e:Exception){message.value="Chưa lưu được bản nháp. Hãy thử lưu lại."}
 }
 fun submit(task:Task)=execute{
  repo.submit(task);repo.detail(task.id);message.value="Đã nộp kết quả, chờ nhóm trưởng duyệt."
 }
}
