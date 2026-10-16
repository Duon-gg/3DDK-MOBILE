package com.threeddk.tasks

import com.google.gson.JsonParser
import com.threeddk.tasks.data.visibleRecords
import org.junit.Assert.assertEquals
import org.junit.Test

class TableViewTest {
    @Test fun filtersStatusSearchAndSortsDatesWithEmptyLast() {
        val rows = listOf(
            """{"id":"1","cells":{"n":"Việc A","s":"Đang làm","d":"2026-10-20"}}""",
            """{"id":"2","cells":{"n":"Việc B","s":"Xong","d":"2026-10-18"}}""",
            """{"id":"3","cells":{"n":"Việc C","s":"Đang làm","d":null}}""",
            """{"id":"4","cells":{"n":"Việc D","s":"Đang làm","d":"2026-10-17"}}"""
        ).map { JsonParser.parseString(it).asJsonObject }
        assertEquals(listOf("4", "1", "3"), visibleRecords(rows, "việc", "s", "Đang làm", "d", true).map { it.get("id").asString })
        assertEquals(listOf("2"), visibleRecords(rows, "B", "s", null, "d", false).map { it.get("id").asString })
    }
}
