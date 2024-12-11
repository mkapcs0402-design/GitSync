package com.viscouspot.gitsync.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.R
import java.util.Locale

class ConflictEditorAdapter (
    private val context: Context,
    private val dataSet: MutableList<String>,
    private val conflictEditor: HorizontalScrollView,
    private val onAction: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class BasicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val line: TextView = view.findViewById(R.id.line)
        val lineNumber: TextView = view.findViewById(R.id.lineNumber)
    }

    class ConflictViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val lineLocal: TextView = view.findViewById(R.id.lineLocal)
        val lineNumberLocal: TextView = view.findViewById(R.id.lineNumberLocal)
        val lineRemote: TextView = view.findViewById(R.id.lineRemote)
        val lineNumberRemote: TextView = view.findViewById(R.id.lineNumberRemote)

        val localButton: MaterialButton = view.findViewById(R.id.local)
        val bothButton: MaterialButton = view.findViewById(R.id.both)
        val remoteButton: MaterialButton = view.findViewById(R.id.remote)
    }

    override fun getItemViewType(position: Int): Int {
        return if (dataSet[position].indexOf(context.getString(R.string.conflict_end)) < 0) 0 else 1
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            1 -> {
                val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.merge_conflict_lines, viewGroup, false)
                return ConflictViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.merge_conflict_line, viewGroup, false)
                return BasicViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            0 -> {
                holder as BasicViewHolder
                holder.line.text = dataSet[position]
                holder.lineNumber.text = position.toPaddedString()
            }
            1 -> {
                holder as ConflictViewHolder
                conflictEditor.post {
                    val view = holder.itemView.findViewById<ConstraintLayout>(R.id.container)
                    view.layoutParams.width =
                        conflictEditor.measuredWidth - conflictEditor.paddingLeft - conflictEditor.paddingRight
                    view.requestLayout()
                }

                val lines = dataSet[position].split("\n")
                val startIndex = lines.indexOfFirst { it.contains(context.getString(R.string.conflict_start)) }
                val midIndex = lines.indexOfFirst { it.contains(context.getString(R.string.conflict_separator)) }
                val endIndex = lines.indexOfLast { it.contains(context.getString(R.string.conflict_end)) }

                val localLines = lines.subList(startIndex+1, midIndex)
                val remoteLines = lines.subList(midIndex+1, endIndex)

                holder.lineLocal.text = localLines.joinToString("\n")
                holder.lineRemote.text = remoteLines.joinToString("\n")

                holder.lineNumberLocal.text = if (localLines.isEmpty()) (position + 1).toPaddedString() else (position + 1..position + localLines.size).toList()
                    .joinToString("\n") {
                        it.toPaddedString()
                    }
                holder.lineNumberRemote.text = if (remoteLines.isEmpty()) (position + 1).toPaddedString() else (position + 1..position + remoteLines.size).toList()
                    .joinToString("\n") {
                        it.toPaddedString()
                    }

                holder.localButton.setOnClickListener {
                    dataSet.removeAt(position)
                    notifyItemRemoved(position)
                    dataSet.addAll(position, localLines)
                    notifyItemRangeInserted(position, localLines.size)
                    onAction()
                }

                holder.remoteButton.setOnClickListener {
                    dataSet.removeAt(position)
                    notifyItemRemoved(position)
                    dataSet.addAll(position, remoteLines)
                    notifyItemRangeInserted(position, remoteLines.size)
                    onAction()
                }

                holder.bothButton.setOnClickListener {
                    dataSet.removeAt(position)
                    notifyItemRemoved(position)
                    dataSet.addAll(position, localLines)
                    notifyItemRangeInserted(position, localLines.size)
                    dataSet.addAll(position, remoteLines)
                    notifyItemRangeInserted(position, remoteLines.size)
                    onAction()
                }
            }
            else -> { }
        }
    }

    private fun Int.toPaddedString(): String {
        return String.format(Locale.ROOT, "%d", this + 1).padStart(String.format(Locale.ROOT, "%d", dataSet.joinToString("\n").split("\n").size).length, '0')
    }

    override fun getItemCount() = dataSet.size
}