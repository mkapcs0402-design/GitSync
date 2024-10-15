package com.viscouspot.gitsync.ui.adapter

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.R

data class Commit(val commitMessage: String, val author: String, val timestamp: Long, val reference: String, val additions: Int, val deletions: Int)

class RecentCommitsAdapter(private val context: Context, private val recentCommits: MutableList<Commit>) : RecyclerView.Adapter<RecentCommitsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commitMessage: TextView = view.findViewById(R.id.commitMessage)
        val author: TextView = view.findViewById(R.id.author)
        val commitDate: Chronometer = view.findViewById(R.id.commitDate)
        val commitRef: MaterialButton = view.findViewById(R.id.commitRef)
        val additions: TextView = view.findViewById(R.id.additions)
        val deletions: TextView = view.findViewById(R.id.deletions)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.recent_commit, viewGroup, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return recentCommits.size
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.commitDate.stop()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val commit = recentCommits[position]
        holder.commitMessage.text = commit.commitMessage
        holder.author.text = commit.author
        holder.commitRef.text = commit.reference
        holder.commitDate.base = commit.timestamp
        holder.commitDate.setOnChronometerTickListener { chronometer ->
            chronometer.text = DateUtils.getRelativeTimeSpanString(chronometer.base, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercase() }
        }
        holder.commitDate.start()
        holder.additions.text = context.getString(R.string.additions).format(commit.additions)
        holder.deletions.text = context.getString(R.string.deletions).format(commit.deletions)
    }
}