package com.threeddk.tasks.data

import com.google.gson.JsonObject

fun visibleRecords(records: List<JsonObject>, search: String, statusFieldId: String?, status: String?, deadlineFieldId: String?, sortDeadline: Boolean): List<JsonObject> {
    val query = search.trim()
    val filtered = records.filter { row ->
        val cells = row.getAsJsonObject("cells") ?: return@filter false
        (query.isEmpty() || cells.entrySet().any { (_, value) -> !value.isJsonNull && value.asString.contains(query, true) }) &&
            (status == null || cells.get(statusFieldId)?.takeUnless { it.isJsonNull }?.asString == status)
    }
    if (!sortDeadline || deadlineFieldId == null) return filtered
    // shortcut: ISO dates sort lexically; add server sorting when tables exceed the API page limit.
    return filtered.sortedWith(compareBy<JsonObject> {
        it.getAsJsonObject("cells")?.get(deadlineFieldId)?.takeUnless { value -> value.isJsonNull }?.asString ?: "9999-12-31"
    }.thenBy { it.get("createdAt")?.asString ?: "" })
}
