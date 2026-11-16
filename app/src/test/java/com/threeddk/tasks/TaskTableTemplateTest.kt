package com.threeddk.tasks

import com.threeddk.tasks.data.taskTableFields
import org.junit.Assert.*
import org.junit.Test

class TaskTableTemplateTest {
    @Test fun presetSupportsStatusAndDeadlineViews() {
        val fields = taskTableFields()
        assertEquals(fields.size, fields.map { it.get("name").asString }.distinct().size)
        assertEquals("SELECT", fields.single { it.get("name").asString == "Trạng thái" }.get("type").asString)
        assertEquals("DATE", fields.single { it.get("name").asString == "Hạn" }.get("type").asString)
        assertTrue(fields.single { it.get("name").asString == "Trạng thái" }.getAsJsonArray("options").any { it.asString == "Hoàn thành" })
    }
}
