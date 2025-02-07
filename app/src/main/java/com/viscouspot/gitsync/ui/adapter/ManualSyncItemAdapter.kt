package com.viscouspot.gitsync.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.R
import com.viscouspot.gitsync.ui.dialog.BaseDialog


class ManualSyncItemAdapter (
    private val context: Context,
    private val filePaths: List<String>,
    private val selectedFilePaths: MutableList<String>,
    private val selectFile: (filePath: String) -> Unit,
    private val discardFileChanges: (filePath: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class BasicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filePath: MaterialButton = view.findViewById(R.id.filePath)
        val discardChanges: MaterialButton = view.findViewById(R.id.discardChanges)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder as BasicViewHolder
        val filePath = filePaths[position]
        holder.filePath.text = filePath
        if (selectedFilePaths.contains(filePaths[position])) {
            holder.filePath.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.additions))
        } else {
            holder.filePath.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
        }

        holder.filePath.setOnClickListener {
            selectFile.invoke(filePath)
            notifyItemRangeChanged(position, 1)
        }

        holder.discardChanges.setOnClickListener {
            BaseDialog(context)
                .setTitle(context.getString(R.string.discard_changes_title))
                .setMessage(context.getString(R.string.discard_changes_msg).format(filePath.split("/").last()))
                .setCancelable(1)
                .setPositiveButton(android.R.string.cancel) { _, _ -> }
                .setNegativeButton(R.string.discard_changes) { _, _ ->
                    discardFileChanges(filePath)
                }
                .show()
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.manual_sync_item, viewGroup, false)
        return BasicViewHolder(view)
    }

    override fun getItemCount() = filePaths.size
}