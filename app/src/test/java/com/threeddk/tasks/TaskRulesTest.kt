package com.threeddk.tasks
import com.threeddk.tasks.data.*
import org.junit.Assert.*
import org.junit.Test
class TaskRulesTest {
 @Test fun onlyRepresentativeCanSubmitActiveWork() {
  val task=Task(id="t",representativeId="a",assigneeIds=listOf("a","b"),status="IN_PROGRESS")
  assertTrue(task.canSubmit("a")); assertFalse(task.canSubmit("b"))
  assertFalse(task.copy(status="IN_REVIEW").canSubmit("a"))
  assertFalse(task.copy(archivedAt="2026-01-01T00:00:00Z").canSubmit("a"))
 }
 @Test fun overdueIncludesPendingReviewButNotDoneOrArchived() {
  val task=Task(id="t",dueAt="2020-01-01T00:00:00Z",status="IN_REVIEW")
  assertTrue(task.overdue());assertFalse(task.copy(status="DONE").overdue())
  assertFalse(task.copy(archivedAt="2020-01-02T00:00:00Z").overdue())
 }
 @Test fun validatesHttpsAndOutputBeforeSubmission() {
  assertNotNull(submissionError("",""));assertNotNull(submissionError("Done","http://example.com"))
  assertNotNull(submissionError("Done","https://"));assertNull(submissionError("Done","https://example.com/result"))
 }
}
