package com.viscouspot.gitsync.ui.dialog

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.GitSyncService
import com.viscouspot.gitsync.R
import com.viscouspot.gitsync.ui.adapter.ConflictEditorAdapter
import com.viscouspot.gitsync.util.GitManager
import com.viscouspot.gitsync.util.Helper
import com.viscouspot.gitsync.util.SettingsManager
import java.io.File
import kotlin.math.max
import kotlin.math.min


class MergeConflictDialog(private val context: Context, private val repoIndex: Int, private val settingsManager: SettingsManager, private val gitManager: GitManager, private val refreshRecentCommits: () -> Unit) : BaseDialog(context) {
    private var smoothScrollStart: SmoothScroller = object : LinearSmoothScroller(context) {
        override fun getVerticalSnapPreference(): Int {
            return SNAP_TO_START
        }
    }
    private lateinit var syncMessageInput: EditText

    override fun onStart() {
        super.onStart()
        setContentView(R.layout.dialog_merge_conflict)

        val conflicts = gitManager.getConflicting(settingsManager.getGitDirUri())
        if (conflicts.isEmpty()) return

        syncMessageInput = findViewById(R.id.syncMessageInput) ?: return
        val conflictEditor = findViewById<HorizontalScrollView>(R.id.conflictEditor) ?: return
        val conflictEditorInput = findViewById<RecyclerView>(R.id.conflictEditorInput) ?: return
        val fileName = findViewById<MaterialButton>(R.id.fileName) ?: return
        val prev = findViewById<MaterialButton>(R.id.prev) ?: return
        val next = findViewById<MaterialButton>(R.id.next) ?: return
        val merge = findViewById<MaterialButton>(R.id.merge) ?: return
        val abortMerge = findViewById<MaterialButton>(R.id.abortMerge) ?: return

        val conflictSections = mutableListOf<String>()

        conflictEditorInput.adapter = ConflictEditorAdapter(context, conflictSections, conflictEditor) {
            if (conflictSections.isEmpty() || conflictSections.firstOrNull { it.contains(context.getString(R.string.conflict_start)) } == null) {
                merge.isEnabled = true
                merge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.auth_green))
                merge.setTextColor(ContextCompat.getColor(context, R.color.card_secondary_bg))
            } else {
                merge.isEnabled = false
                merge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_secondary_bg))
                merge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }

        var conflictIndex = 0

        fileName.setOnClickListener {
            val file = File("${Helper.getPathFromUri(context, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}")

            if (file.exists()) {
                val intent = Intent(Intent.ACTION_VIEW)
                val fileUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "text/plain"

                intent.setDataAndType(fileUri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                context.startActivity(Intent.createChooser(intent, "Open file with"))
            }

        }

        conflictEditorInput.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    refreshArrowKeys(conflicts, conflictSections, conflictIndex, prev, next, conflictEditorInput)
                }
            }
        })

        prev.setOnClickListener {
            val firstConflictIndex = conflictSections.indexOfFirst{ it.contains(context.getString(R.string.conflict_start)) }
            if ((conflictEditorInput.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= firstConflictIndex || firstConflictIndex == -1) {
                conflictIndex = max(conflictIndex - 1, 0)
                refreshMergeConflictDialog(conflicts, conflictSections, conflictIndex, merge, fileName, prev, next, conflictEditorInput)
            } else {
                val currentIndex = (conflictEditorInput.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                val lastConflictIndex = conflictSections.indexOfLast{ it.contains(context.getString(R.string.conflict_start)) }
                val endIndex = if (currentIndex < 0) lastConflictIndex else currentIndex - 1
                val prevConflictIndex = conflictSections.subList(0, endIndex).indexOfLast{ it.contains(context.getString(R.string.conflict_start)) }
                smoothScrollStart.targetPosition = if (prevConflictIndex < 0) 0 else prevConflictIndex
                (conflictEditorInput.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScrollStart)
                (conflictEditorInput.adapter as ConflictEditorAdapter).notifyItemChanged(prevConflictIndex)
            }
        }

        next.setOnClickListener {
            val lastConflictIndex = conflictSections.indexOfLast{ it.contains(context.getString(R.string.conflict_start)) }
            if ((conflictEditorInput.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() >= lastConflictIndex || conflictSections.isEmpty() || conflictSections.firstOrNull { it.contains(context.getString(R.string.conflict_start)) } == null) {
                conflictIndex = min(conflictIndex + 1, conflicts.size - 1)
                refreshMergeConflictDialog(conflicts, conflictSections, conflictIndex, merge, fileName, prev, next, conflictEditorInput)
            } else {
                val currentIndex = (conflictEditorInput.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                val startIndex = if (currentIndex < 0) 1 else currentIndex + 1
                val nextConflictIndex = conflictSections.subList(startIndex, conflictSections.size).indexOfFirst{ it.contains(context.getString(R.string.conflict_start)) } + startIndex
                smoothScrollStart.targetPosition = if (nextConflictIndex < 0) conflictSections.size else nextConflictIndex
                (conflictEditorInput.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScrollStart)
                (conflictEditorInput.adapter as ConflictEditorAdapter).notifyItemChanged(nextConflictIndex)
            }

        }

        merge.post {
            refreshMergeConflictDialog(conflicts, conflictSections, conflictIndex, merge, fileName, prev, next, conflictEditorInput)
        }

        merge.setOnClickListener{
            File("${Helper.getPathFromUri(context, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}").bufferedWriter().use { writer ->
                conflictSections.forEach { line ->
                    writer.write(line)
                    writer.newLine()
                }
            }

            if (conflicts.size > 1) {
                conflicts.removeAt(conflictIndex)
                conflictIndex = min(conflictIndex, conflicts.size - 1)
                refreshMergeConflictDialog(conflicts, conflictSections, conflictIndex, merge, fileName, prev, next, conflictEditorInput)
                return@setOnClickListener
            }

            merge.text = context.getString(R.string.merging)
            merge.isEnabled = false
            merge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_secondary_bg))
            merge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            abortMerge.visibility = View.GONE

            val forceSyncIntent = Intent(context, GitSyncService::class.java)
            forceSyncIntent.setAction(GitSyncService.MERGE)
            forceSyncIntent.putExtra("repoIndex", repoIndex)
            forceSyncIntent.putExtra("commitMessage", syncMessageInput.text.toString())
            context.startService(forceSyncIntent)
        }

        abortMerge.setOnClickListener {
            gitManager.abortMerge(settingsManager.getGitDirUri())
            dismiss()
            refreshRecentCommits()
        }
    }

    private fun refreshArrowKeys(conflicts: MutableList<String>, conflictSections: MutableList<String>, conflictIndex: Int, prev: MaterialButton, next: MaterialButton, conflictEditorInput: RecyclerView) {
        val firstConflictIndex = conflictSections.indexOfFirst{ it.contains(context.getString(R.string.conflict_start)) }
        val lastConflictIndex = conflictSections.indexOfLast{ it.contains(context.getString(R.string.conflict_start)) }

        prev.isEnabled = (conflictEditorInput.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > firstConflictIndex
                || conflictIndex > 0
        next.isEnabled = (conflictEditorInput.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() < lastConflictIndex
                || conflictIndex < conflicts.size - 1
    }

    private fun refreshMergeConflictDialog(conflicts: MutableList<String>, conflictSections: MutableList<String>, conflictIndex: Int, merge: MaterialButton, fileName: MaterialButton, prev: MaterialButton, next: MaterialButton, conflictEditorInput: RecyclerView) {
        refreshArrowKeys(conflicts, conflictSections, conflictIndex, prev, next, conflictEditorInput)

        fileName.text = conflicts.elementAt(conflictIndex).substringAfterLast("/")

        val file = File("${Helper.getPathFromUri(context, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}")

        if (conflictSections.isNotEmpty()) {
            val conflictSectionsSize = conflictSections.size
            conflictSections.clear()
            conflictEditorInput.adapter?.notifyItemRangeRemoved(0, conflictSectionsSize)
        }

        if (file.exists()) {
            Helper.extractConflictSections(context, file) {
                conflictSections.add(it)
                conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
            }
        } else {
            conflictSections.add("File not found.")
            conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
        }

        if (conflictSections.firstOrNull { it.contains("\n") || it.contains("File not found.") } == null) {
            merge.isEnabled = true
            merge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.auth_green))
            merge.setTextColor(ContextCompat.getColor(context, R.color.card_secondary_bg))
        } else {
            merge.isEnabled = false
            merge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_secondary_bg))
            merge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }
}