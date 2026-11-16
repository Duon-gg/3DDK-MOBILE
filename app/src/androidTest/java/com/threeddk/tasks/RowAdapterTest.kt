package com.threeddk.tasks

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.threeddk.tasks.ui.Row
import com.threeddk.tasks.ui.RowAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RowAdapterTest {
    @Test fun homeRowsShowStarsAndOnlyBasesHandleLongPressAfterRebinding() {
        val longPressed = mutableListOf<String>()
        val clicked = mutableListOf<String>()
        val adapter = RowAdapter { clicked.add(it) }.apply {
            onLongClick = { longPressed.add(it) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                adapter.submitList(listOf(
                    Row("bases/starred", "Starred base", "Workspace", starred = true, homeItem = true),
                    Row("bases/recent", "Recent base", "Workspace", homeItem = true),
                    Row("workspaces/team", "Team", "Workspace", homeItem = true),
                    Row("ordinary", "Ordinary row", "Subtitle")
                ))
                val holder = adapter.onCreateViewHolder(RecyclerView(activity), 0)

                adapter.onBindViewHolder(holder, 0)
                assertEquals(View.VISIBLE, holder.binding.star.visibility)
                assertTrue(holder.itemView.performLongClick())
                assertTrue(holder.itemView.performClick())
                assertEquals(listOf("bases/starred"), clicked)

                adapter.onBindViewHolder(holder, 1)
                assertEquals(View.GONE, holder.binding.star.visibility)
                assertTrue(holder.itemView.performLongClick())
                assertEquals(listOf("bases/starred", "bases/recent"), longPressed)

                adapter.onBindViewHolder(holder, 2)
                assertFalse(holder.itemView.performLongClick())
                assertEquals(listOf("bases/starred", "bases/recent"), longPressed)

                adapter.onBindViewHolder(holder, 0)
                adapter.onViewRecycled(holder)
                adapter.onBindViewHolder(holder, 3)
                assertEquals(View.GONE, holder.binding.star.visibility)
                assertFalse(holder.itemView.performLongClick())
                assertEquals(listOf("bases/starred", "bases/recent"), longPressed)
                assertTrue(holder.itemView.performClick())
                assertEquals(listOf("bases/starred", "ordinary"), clicked)
            }
        }
    }

    @Test fun recyclingAfterActivityDestructionDoesNotLookUpDestroyedActivity() {
        val adapter = RowAdapter {}
        lateinit var holder: RowAdapter.Holder
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                adapter.submitList(listOf(Row("test", "Dòng thử", "")))
                val parent = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                }
                holder = adapter.onCreateViewHolder(parent, 0)
                adapter.onBindViewHolder(holder, 0)
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter.onViewRecycled(holder)
        }
    }
}
