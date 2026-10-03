package com.threeddk.tasks.data
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
data class Task(val id:String="",val title:String="",val description:String="",val dueAt:String="",val priority:String="NORMAL",val status:String="TODO",val representativeId:String="",val assigneeIds:List<String> = emptyList(),val version:Int=1,val archivedAt:String?=null,val currentSubmissionId:String?=null) {
 fun canSubmit(uid:String)=representativeId==uid && status=="IN_PROGRESS" && archivedAt==null
 fun overdue(now:Instant=Instant.now())=archivedAt==null && status!="DONE" && runCatching {Instant.parse(dueAt).isBefore(now)}.getOrDefault(false)
}
data class Member(val uid:String="",val email:String?=null,val displayName:String="",val role:String="MEMBER",val active:Boolean=true,val avatarUrl:String="")
data class Invite(val email:String="",val createdAt:String="")
data class Notice(val id:String="",val taskId:String="",val kind:String="",val read:Boolean=false,val createdAt:String="")
data class Submission(val id:String="",val submittedBy:String="",val description:String="",val link:String="",val reviewStatus:String="",val reviewedBy:String?=null,val reason:String?=null,val createdAt:String="")
data class Event(val id:String="",val actorId:String="",val kind:String="",val detail:Map<String,Any?> = emptyMap(),val createdAt:String="")
data class History(val events:List<Event> = emptyList(),val submissions:List<Submission> = emptyList())
val statusLabels=linkedMapOf("TODO" to "Chưa làm","IN_PROGRESS" to "Đang làm","IN_REVIEW" to "Chờ duyệt","DONE" to "Hoàn thành")
val priorityLabels=linkedMapOf("LOW" to "Thấp","NORMAL" to "Bình thường","HIGH" to "Cao")
val eventLabels=mapOf("CREATED" to "Được giao công việc","UPDATED" to "Thay đổi công việc","STARTED" to "Bắt đầu làm","SUBMITTED" to "Nộp kết quả","APPROVED" to "Đã duyệt","REJECTED" to "Yêu cầu sửa","REOPENED" to "Mở lại công việc","ARCHIVED" to "Lưu trữ","REMINDER_24H" to "Sắp đến hạn trong 24 giờ","REMINDER_1H" to "Sắp đến hạn trong 1 giờ")
fun formattedTime(value:String)=runCatching {DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.parse(value))}.getOrDefault(value)
fun submissionError(description:String,link:String):String? {
 if(description.trim().length !in 1..5000) return "Nhập kết quả từ 1 đến 5.000 ký tự."
 if(link.length>2048) return "Đường link quá dài."
 if(link.isNotBlank() && !runCatching {val uri=URI(link);uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null}.getOrDefault(false)) return "Dùng đường link HTTPS hợp lệ."
 return null
}
fun friendlyError(error:Throwable):String=when((error as? ApiException)?.code) {
 "NOT_INVITED" -> "Email này chưa được nhóm trưởng mời."
 "FORBIDDEN" -> "Bạn không có quyền thực hiện thao tác này."
 "UNVERIFIED" -> "Hãy xác minh email rồi thử lại."
 "UNAUTHORIZED" -> "Phiên đăng nhập đã hết. Hãy đăng nhập lại."
 "CONFLICT" -> "Công việc đã thay đổi. Dữ liệu mới đã được tải; hãy kiểm tra và thử lại."
 "INVALID_STATE" -> "Trạng thái công việc không còn cho phép thao tác này."
 "ASSIGNED_WORK" -> "Cần phân công lại việc chưa hoàn thành trước khi ngừng quyền thành viên."
 "VALIDATION" -> "Thông tin chưa hợp lệ. Kiểm tra lại các trường."
 "NOT_FOUND" -> "Không tìm thấy dữ liệu này."
 "RATE_LIMIT" -> "Bạn thao tác quá nhanh. Thử lại sau một phút."
 else -> if(error is java.io.IOException) "Không kết nối được. Dữ liệu đã lưu và bản nháp vẫn còn." else "Thao tác chưa thành công. Kiểm tra kết nối và thử lại."
}
