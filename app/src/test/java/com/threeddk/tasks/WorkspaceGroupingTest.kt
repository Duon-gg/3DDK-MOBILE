package com.threeddk.tasks
import com.threeddk.tasks.data.isTestWorkspace
import org.junit.Assert.*
import org.junit.Test

class WorkspaceGroupingTest {
 @Test fun groupsKnownQaSpacesOnly(){
  assertTrue(isTestWorkspace("3DDK • Kiểm thử P0 a6842cf9"))
  assertTrue(isTestWorkspace(" 3DDK • Kiểm thử P0 ABCD1234 "))
  listOf("Team mobile","3DDK • Bảng dùng thử","Dự án kiểm thử","3DDK • Kiểm thử P0 khách hàng","").forEach{assertFalse(isTestWorkspace(it))}
 }
}
