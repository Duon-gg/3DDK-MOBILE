package com.threeddk.tasks.viewmodel
import com.threeddk.tasks.data.hasPasswordProvider
import com.threeddk.tasks.R
import com.threeddk.tasks.TasksApp
import com.threeddk.tasks.model.History
import com.threeddk.tasks.model.Invite
import com.threeddk.tasks.model.Member
import com.threeddk.tasks.model.Notice
import com.threeddk.tasks.model.Row
import com.threeddk.tasks.model.Task
import com.threeddk.tasks.model.eventLabels
import com.threeddk.tasks.model.formattedTime
import com.threeddk.tasks.model.friendlyError
import com.threeddk.tasks.model.priorityLabels
import com.threeddk.tasks.model.statusLabels
import com.threeddk.tasks.model.submissionError
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.gson.JsonObject
import com.threeddk.tasks.data.ApiException
import com.threeddk.tasks.data.CacheEntry
import com.threeddk.tasks.data.Draft
import com.threeddk.tasks.data.accountAvatar
import com.threeddk.tasks.data.authenticationErrorResource
import com.threeddk.tasks.data.changePassword
import com.threeddk.tasks.data.requestPasswordReset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class AppViewModel(app:Application):AndroidViewModel(app) {
 private val repo=(app as TasksApp).repository
 @PublishedApi internal val modelJson=repo.gson
 val session=MutableStateFlow(if(repo.configured)"AUTH" else "CONFIG")
 val rows=MutableStateFlow<List<CacheEntry>>(emptyList())
 val drafts=MutableStateFlow<List<Draft>>(emptyList())
 val loading=MutableStateFlow(false)
 val changingPassword=MutableStateFlow(false)
 val canChangePassword get()=repo.auth?.currentUser?.hasPasswordProvider==true
 val message=MutableStateFlow<String?>(null)
 val syncNote=MutableStateFlow("Chưa đồng bộ")
 var pendingTaskId:String?=null
 private var observer:Job?=null
 val me get()=rows.value.firstOrNull{it.kind=="me"}?.let{repo.gson.fromJson(it.json,Member::class.java)}
 inline fun <reified T> records(kind:String)=rows.value.filter{it.kind==kind}.map{modelJson.fromJson(it.json,T::class.java)}
 fun task(id:String)=records<Task>("tasks").firstOrNull{it.id==id}
 fun history(id:String)=rows.value.firstOrNull{it.kind=="history"&&it.id==id}?.let{repo.gson.fromJson(it.json,History::class.java)}?:History()
 init { if(repo.configured) execute{restore()} }
 fun execute(block:suspend()->Unit) {
  viewModelScope.launch {
   loading.value=true
   try {block()} catch(e:CancellationException){throw e} catch(e:Exception){
    message.value=when(e){is FirebaseAuthException->when(e.errorCode){"ERROR_INVALID_EMAIL"->"Email không hợp lệ.";"ERROR_EMAIL_ALREADY_IN_USE"->"Email này đã được đăng ký.";"ERROR_WEAK_PASSWORD"->"Mật khẩu cần ít nhất 6 ký tự.";else->getApplication<Application>().getString(authenticationErrorResource(e.errorCode))};else->friendlyError(e)}
   } finally {loading.value=false}
  }
 }
 private suspend fun restore() {
  val user=repo.auth?.currentUser
  if(user==null){session.value="AUTH";return}
  if(!user.isEmailVerified){session.value="VERIFY";return}
  observer?.cancel()
  rows.value=emptyList();drafts.value=emptyList()
  observer=viewModelScope.launch{
   launch{repo.observe(user.uid).collect{rows.value=it}}
   launch{repo.dao.observeDrafts(user.uid).collect{drafts.value=it}}
  }
  session.value="READY"
  refreshNow()
 }
 private suspend fun refreshNow() {
  try {
   repo.refresh();session.value="READY";syncNote.value="Đã đồng bộ • ${formattedTime(java.time.Instant.now().toString())}"
  } catch(e:Exception){
   syncNote.value="Đang xem dữ liệu đã lưu • Chưa đồng bộ được"
   if(e is ApiException && e.code in listOf("NOT_INVITED","FORBIDDEN","UNVERIFIED","UNAUTHORIZED")) {
    observer?.cancel();rows.value=emptyList();drafts.value=emptyList();session.value=if(e.code=="UNVERIFIED")"VERIFY" else "AUTH"
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
 fun loginWithGoogle(idToken:String)=execute{
  check(session.value=="AUTH")
  require(idToken.isNotBlank())
  requireNotNull(repo.auth).signInWithCredential(GoogleAuthProvider.getCredential(idToken,null)).await()
  restore()
 }
 fun verifyAgain()=execute{
  repo.auth?.currentUser?.reload()?.await()
  repo.auth?.currentUser?.getIdToken(true)?.await()
  restore()
 }
 fun resendVerification()=execute{repo.auth?.currentUser?.sendEmailVerification()?.await();message.value="Đã gửi email xác minh. Kiểm tra cả thư mục spam."}
 fun reset(email:String)=execute{
  requireNotNull(repo.auth).requestPasswordReset(email)
  message.value=getApplication<Application>().getString(R.string.password_reset_requested)
 }
 fun changePassword(current:String,replacement:String,done:(Int?)->Unit){
  if(changingPassword.value)return
  changingPassword.value=true
  viewModelScope.launch{
   val error=try{
    requireNotNull(repo.auth).changePassword(current,replacement)
    message.value=getApplication<Application>().getString(R.string.password_changed)
    null
   }catch(e:CancellationException){throw e}
   catch(e:Exception){
    when((e as? FirebaseAuthException)?.errorCode){
     "ERROR_WRONG_PASSWORD","ERROR_INVALID_CREDENTIAL","ERROR_INVALID_LOGIN_CREDENTIALS"->R.string.password_wrong_current
     "ERROR_WEAK_PASSWORD","ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS"->R.string.password_policy
     "ERROR_TOO_MANY_REQUESTS"->R.string.password_retry_later
     else->if(e is com.google.firebase.FirebaseNetworkException)R.string.password_network else R.string.password_change_failed
    }
   }finally{changingPassword.value=false}
   done(error)
  }
 }
 fun logout()=execute{
  observer?.cancel();repo.logout();rows.value=emptyList();drafts.value=emptyList();session.value="AUTH";pendingTaskId=null
  try{CredentialManager.create(getApplication()).clearCredentialState(ClearCredentialStateRequest())}
  catch(e:ClearCredentialException){message.value=getApplication<Application>().getString(R.string.auth_logout_provider_warning)}
 }
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

 fun overviewRows(kind:String,filter:Int,search:String):List<Row>{
  val members=records<Member>("members").associateBy{it.uid}
  val rows=when(kind){
   "members"->members.values.sortedBy{it.displayName}.map{Row(it.uid,it.displayName,(if(it.role=="LEADER")"Nhóm trưởng" else "Thành viên")+(if(it.active)"" else " • Đã ngừng quyền"),it.avatarUrl)}
   "notifications"->records<Notice>("notifications").sortedByDescending{it.createdAt}.map{Row(it.id,(if(it.read)"" else "● ")+(eventLabels[it.kind]?:it.kind),formattedTime(it.createdAt))}
   else->if(filter==7)drafts.value.map{d->Row(d.taskId,task(d.taskId)?.title?:"Bản nháp công việc đã thay đổi",d.description.take(120))} else records<Task>("tasks").filter{t->t.archivedAt==null&&t.title.contains(search,true)&&when(val selected=filter){
    1->repo.uid in t.assigneeIds;2->t.overdue();in 3..6->t.status==statusLabels.keys.toList()[selected-3];else->true
   }}.sortedBy{it.dueAt}.map{t->Row(t.id,t.title,"${statusLabels[t.status]} • Ưu tiên ${priorityLabels[t.priority]}\n${if(t.overdue())"Quá hạn: " else "Hạn: "}${formattedTime(t.dueAt)}\nĐại diện: ${members[t.representativeId]?.displayName?:t.representativeId} • ${t.assigneeIds.size} người",members[t.representativeId]?.avatarUrl?:"",t.overdue())}
  }
  return rows
 }


 val currentUid get()=repo.uid.orEmpty()
 val accountEmail get()=me?.email?:repo.auth?.currentUser?.email
 val accountName get()=me?.displayName?.takeIf{it.isNotBlank()}?:repo.auth?.currentUser?.displayName
 val googleAvatar get()=accountAvatar(repo.auth?.currentUser,null)
 fun avatar(fallback:String?=me?.avatarUrl)=accountAvatar(repo.auth?.currentUser,fallback)
 fun savedEmail()=repo.savedEmail()
 fun loadInvites(done:(List<Invite>)->Unit)=execute{done(repo.invites())}
 fun logoutDraftCount(done:(Int)->Unit)=execute{done(repo.uid?.let{repo.dao.draftCount(it)}?:0)}
 fun updateProfile(name:String,avatar:String,done:()->Unit){
  if(name.trim().isBlank()){message.value="Nhập tên hiển thị.";return}
  mutation("PATCH","me",repo.body("displayName" to name.trim(),"avatarUrl" to avatar)){message.value="Đã lưu hồ sơ.";done()}
 }
 fun setMemberActive(id:String,active:Boolean)=mutation("PATCH","members/$id",repo.body("active" to active))
 fun inviteEmail(email:String)=mutation("POST","invites",repo.body("email" to email.trim()))
 fun revokeInvite(email:String)=mutation("DELETE","invites",repo.body("email" to email))
 fun markNoticeRead(id:String)=mutation("PATCH","notifications/$id",repo.body())
 fun loadDraft(id:String,done:(Draft?)->Unit)=execute{done(repo.draft(id))}
 fun submitResult(task:Task,description:String,link:String,done:()->Unit)=execute{
  submissionError(description,link)?.let{message.value=it;return@execute}
  repo.saveDraft(task,description,link);repo.submit(task);repo.detail(task.id)
  message.value="Đã nộp kết quả, chờ duyệt.";done()
 }
 fun taskAction(id:String,name:String,extra:Array<out Pair<String,Any?>>,done:()->Unit){
  val task=task(id)?:return
  mutation("POST","tasks/$id/$name",repo.body("expectedVersion" to task.version,*extra)){if(name!="archive")detail(id);done()}
 }


 fun assignableMembers()=records<Member>("members").filter{it.active}
 fun selectedMembers(ids:Set<String>)=records<Member>("members").filter{it.uid in ids}
 fun saveTask(id:String,payload:JsonObject,done:()->Unit){
  if(me?.role!="LEADER"){message.value="Bạn không thể sửa công việc này.";return}
  mutation(if(id.isEmpty())"POST" else "PATCH",if(id.isEmpty())"tasks" else "tasks/$id",payload,done)
 }

}
