package com.threeddk.tasks

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.threeddk.tasks.databinding.ItemRecordCardBinding
import com.threeddk.tasks.ui.RecordCardAdapter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordCardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Tasks
    )

    @Test fun cardKeepsTitleAndPrioritizesDeadlinePersonAndFirstSelectAndOpensTheSameRecord() {
        instrumentation.runOnMainSync {
            var openedId: String? = null
            val adapter = RecordCardAdapter { openedId = it }
            adapter.fields = listOf(
                field("topic", "Chủ đề", "TEXT"),
                field("description", "Mô tả", "TEXT"),
                field("state", "Tiến độ", "SELECT"),
                field("priority", "Ưu tiên", "SELECT"),
                field("day", "Ngày dự kiến", "DATE"),
                field("contact", "Liên hệ", "USER")
            )
            adapter.members = listOf(json("""{"uid":"user-1","displayName":"Nguyễn Dương"}"""))
            adapter.submitList(listOf(json("""{"id":"record-a","cells":{"topic":"Kế hoạch tuần","description":"Mô tả dài không hiển thị","state":"Đang làm","priority":"Cao","contact":"user-1","day":"2026-11-01","hidden":"Cột ẩn không hiển thị"}}""")))
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            val binding = ItemRecordCardBinding.bind(holder.itemView)

            assertEquals("Kế hoạch tuần", binding.title.text.toString())
            assertEquals(listOf(
                "Ngày dự kiến: 01/11/2026",
                "Liên hệ: Nguyễn Dương",
                "Tiến độ: Đang làm"
            ), binding.fields.children.map { (it as TextView).text.toString() }.toList())
            assertEquals(2, binding.title.maxLines)
            assertTrue(binding.fields.children.all { (it as TextView).maxLines == 2 })
            assertTrue(holder.itemView.isClickable && holder.itemView.isFocusable)
            assertTrue(holder.itemView.minimumHeight >= (48 * context.resources.displayMetrics.density).toInt())
            val spoken = holder.itemView.contentDescription.toString()
            assertTrue(spoken.contains("Chủ đề: Kế hoạch tuần"))
            assertTrue(spoken.contains("Liên hệ: Nguyễn Dương"))
            assertFalse(spoken.contains("Mô tả dài không hiển thị"))
            assertFalse(spoken.contains("Ưu tiên: Cao"))
            assertFalse(spoken.contains("Cột ẩn không hiển thị"))
            assertTrue(holder.itemView.performClick())
            assertEquals("record-a", openedId)
        }
    }

    @Test fun rebindReplacesOldFieldsMemberNamesAndClickAndRecycleClearsContent() {
        lateinit var adapter: RecordCardAdapter
        lateinit var holder: RecordCardAdapter.Holder
        var openedId: String? = null
        instrumentation.runOnMainSync {
            adapter = RecordCardAdapter { openedId = it }
            adapter.fields = listOf(
                field("old", "Cột cũ", "TEXT"), field("extra", "Ghi chú cũ", "TEXT")
            )
            adapter.submitList(listOf(json("""{"id":"old-record","cells":{"old":"Nội dung cũ","extra":"Không giữ lại"}}""")))
            holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
        }
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            adapter.fields = listOf(
                field("contact", "Liên hệ mới", "USER"),
                field("allowed", "Được xem", "CHECKBOX"),
                field("blankUser", "Chưa giao", "USER"),
                field("missingDate", "Ngày chưa chọn", "DATE")
            )
            adapter.members = listOf(json("""{"uid":"user-2","displayName":"Hồ Quốc Duy"}"""))
            adapter.submitList(listOf(json("""{"id":"new-record","cells":{"contact":"user-2","allowed":false,"blankUser":null}}"""))) {
                committed.countDown()
            }
        }
        assertTrue("Danh sách mới phải được áp dụng", committed.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            adapter.onBindViewHolder(holder, 0)
            val binding = ItemRecordCardBinding.bind(holder.itemView)
            assertEquals("Hồ Quốc Duy", binding.title.text.toString())
            assertEquals(listOf(
                "Ngày chưa chọn: ${context.getString(R.string.p0_cell_empty)}",
                "Chưa giao: ${context.getString(R.string.p0_cell_empty)}",
                "Được xem: ${context.getString(R.string.p0_cell_no)}"
            ), binding.fields.children.map { (it as TextView).text.toString() }.toList())
            assertFalse(holder.itemView.contentDescription.toString().contains("cũ"))
            holder.itemView.performClick()
            assertEquals("new-record", openedId)

            adapter.fields = emptyList()
            adapter.onBindViewHolder(holder, 0)
            assertEquals(context.getString(R.string.p0_cell_empty), binding.title.text.toString())
            assertEquals(0, binding.fields.childCount)
            adapter.onViewRecycled(holder)
            assertEquals("", binding.title.text.toString())
            assertNull(holder.itemView.contentDescription)
            assertFalse(holder.itemView.performClick())
        }
    }

    private fun field(id: String, name: String, type: String) = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("type", type)
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
}
