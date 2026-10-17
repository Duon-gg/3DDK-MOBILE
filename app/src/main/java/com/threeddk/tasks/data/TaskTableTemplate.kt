package com.threeddk.tasks.data

import com.google.gson.JsonObject
import com.google.gson.JsonArray

fun taskTableFields(): List<JsonObject> = listOf(
    Triple("Mô tả", "TEXT", emptyList<String>()),
    Triple("Trạng thái", "SELECT", listOf("Chưa làm", "Đang làm", "Chờ duyệt", "Hoàn thành")),
    Triple("Ưu tiên", "SELECT", listOf("Thấp", "Bình thường", "Cao")),
    Triple("Hạn", "DATE", emptyList()),
    Triple("Người phụ trách", "TEXT", emptyList())
).map { (name, type, options) ->
    JsonObject().apply {
        addProperty("name", name)
        addProperty("type", type)
        add("options", JsonArray().apply { options.forEach { add(it) } })
    }
}
