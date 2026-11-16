package com.threeddk.tasks
import com.threeddk.tasks.viewmodel.filterOperators
import org.junit.Assert.*
import org.junit.Test
class TableQueryRulesTest {
 @Test fun operatorsMatchFieldTypes(){
  assertTrue("contains" in filterOperators("TEXT"))
  assertFalse("contains" in filterOperators("NUMBER"))
  assertTrue("gte" in filterOperators("DATE"))
  assertTrue("isMe" in filterOperators("USER"))
  assertFalse("isMe" in filterOperators("CHECKBOX"))
  assertEquals(listOf("eq","ne","isEmpty"),filterOperators("UNKNOWN"))
 }
}
