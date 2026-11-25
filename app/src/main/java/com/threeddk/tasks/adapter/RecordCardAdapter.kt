package com.threeddk.tasks.adapter
import com.threeddk.tasks.ui.cellLabel
import com.threeddk.tasks.R
import com.threeddk.tasks.model.text

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.threeddk.tasks.databinding.ItemRecordCardBinding
import com.threeddk.tasks.databinding.ItemRecordCardFieldBinding

/** [onBindCard] lets a host add extra behaviour to each card (Kanban uses it for drag and the move action). */
class RecordCardAdapter(private val click: (String) -> Unit, private val onBindCard: ((android.view.View, String) -> Unit)? = null) :
    ListAdapter<JsonObject, RecordCardAdapter.Holder>(object : DiffUtil.ItemCallback<JsonObject>() {
        override fun areItemsTheSame(old: JsonObject, new: JsonObject) = old.text("id") == new.text("id")
        override fun areContentsTheSame(old: JsonObject, new: JsonObject) = old == new
    }) {
    var fields = emptyList<JsonObject>()
    var members = emptyList<JsonObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemRecordCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val binding = holder.binding
        val context = binding.root.context
        val row = getItem(position)
        val cells = row.get("cells")?.takeIf { it.isJsonObject }?.asJsonObject
        val empty = context.getString(R.string.p0_cell_empty)
        fun value(field: JsonObject) =
            cellLabel(context, field, cells?.get(field.text("id")), members).ifBlank { empty }
        fun label(field: JsonObject) = field.text("name").ifBlank { field.text("id") }

        val preview = fields.drop(1).sortedBy {
            when (it.text("type")) { "DATE" -> 0; "USER" -> 1; "SELECT" -> 2; else -> 3 }
        }.take(3)
        val shown = fields.take(1) + preview
        val titleField = shown.firstOrNull()
        binding.title.text = titleField?.let(::value) ?: empty
        binding.fields.removeAllViews()
        shown.drop(1).forEach { field ->
            val detail = ItemRecordCardFieldBinding.inflate(
                LayoutInflater.from(context), binding.fields, false
            )
            detail.root.text = "${label(field)}: ${value(field)}"
            binding.fields.addView(detail.root)
        }
        binding.fields.isVisible = binding.fields.childCount > 0
        binding.root.contentDescription = shown.joinToString(", ") { "${label(it)}: ${value(it)}" }
            .ifBlank { empty }
        binding.root.setOnClickListener { click(row.text("id")) }
        onBindCard?.invoke(binding.root, row.text("id"))
    }

    override fun onViewRecycled(holder: Holder) {
        holder.binding.apply {
            root.setOnClickListener(null)
            root.setOnLongClickListener(null)
            root.contentDescription = null
            title.text = ""
            fields.removeAllViews()
            fields.isVisible = false
        }
        super.onViewRecycled(holder)
    }

    class Holder(val binding: ItemRecordCardBinding) : RecyclerView.ViewHolder(binding.root)
}
