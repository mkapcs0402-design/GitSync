package com.viscouspot.gitsync.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.viscouspot.gitsync.BuildConfig
import com.viscouspot.gitsync.R
import com.viscouspot.gitsync.util.Helper.makeToast
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random

enum class LogType(val type: String) {
    TEST("TEST"),
    Global("Global"),
    ToServiceCommand("ToServiceCommand"),
    AccessibilityService("AccessibilityService"),

    Sync("Sync"),
    AbortMerge("AbortMerge"),
    GetRepos("GetRepos"),
    CloneRepo("CloneRepo"),
    SelectDirectory("SelectDirectory"),
    ForcePull("ForcePull"),
    PullFromRepo("PullFromRepo"),
    PushToRepo("PushToRepo"),
    ForcePush("ForcePush"),
    GitStatus("GitStatus"),
    RecentCommits("RecentCommits"),

    SyncException("SyncException"),

    GithubOAuthFlow("GithubOAuthFlow"),
    GithubAuthCredentials("GithubAuthCredentials"),
    GiteaOAuthFlow("GiteaOAuthFlow"),
    GiteaAuthCredentials("GiteaAuthCredentials"),

}

object Logger {
    private val lastLogs = mutableListOf<Pair<LogType, String>>()
    private const val ERROR_NOTIFICATION_ID = 1757

    fun log(context: Context, type: LogType, e: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        log(type, "Error: $sw")
        addToLastLogs(type, "Error: $sw")

        sendBugReportNotification(context)
    }

    fun log(type: LogType, message: String) {
        Log.d("///Git Sync//${type.type}", message)
        addToLastLogs(type, message)
    }

    fun log(message: Any?) {
        Log.d("///Git Sync//${LogType.TEST.type}", message?.toString() ?: "null")
        addToLastLogs(LogType.TEST, message?.toString() ?: "null")
    }

    private fun addToLastLogs(type: LogType, message: String) {
        lastLogs.add(Pair(type, message))
        while (lastLogs.size > 20) {
            lastLogs.removeAt(0)
        }
    }

    fun sendBugReportNotification(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val channelId = "git_sync_bug_channel"
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                channelId,
                "Git Sync Bug",
                NotificationManager.IMPORTANCE_HIGH
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val githubIssueIntent = createGitHubIssueIntent()
        val buttonPendingIntent = PendingIntent.getActivity(context, Random.nextInt(0, 100), githubIssueIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.bug)
            .setContentTitle(context.getString(R.string.report_bug))
            .setContentText(if (lastLogs.size > 0) lastLogs.last().second.substringBefore("\n") else context.getString(R.string.unknown_error))
            .setContentIntent(buttonPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                makeToast(context, "${context.getString(R.string.report_bug)} ${context.getString(
                    R.string.enable_notifications)}", Toast.LENGTH_LONG)
                return
            }

            notify(ERROR_NOTIFICATION_ID, builder.build())
        }
    }

    fun copyLogsToClipboard(context: Context) {
        val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText(context.getString(R.string.copied_text), generateLogs())
        clipboard?.setPrimaryClip(clip)
    }

    private fun urlEncode(str: String): String{
        return URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
    }

    private fun createGitHubIssueIntent(): Intent {
        var url = "https://issue-wrapper.netlify.app/?q=*{%22appIconUrl%22:%22https://raw.githubusercontent.com/ViscousPot/GitSync/master/app/src/main/res/mipmap-xxxhdpi/ic*_launcher*_round.webp%22,%22appName%22:%22GitSync%22,%22repoPath%22:%22ViscousPot/GitSync%22,%22questions%22:[%22Do_you_have_steps_to_reproduce_this_issue_reliably*Q%22,%22Which_app_feature_is_affected*Q%22,%22Which_auth_method_are_you_attempting_to_use*Q%22,%22Is_there_anything_that_may_be_non-standard_in_your_setup_or_config*Q%22]}~"
        url += "&logs="
        url += urlEncode(generateLogs())
        url.take(2048)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return intent
    }

    private fun generateLogs(): String {
        val lastLogsString = lastLogs.reversed().joinToString(separator = "\n") { (first, second) -> "$first: $second" }
        return """
**Android Version:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
**Device Model:** ${Build.MANUFACTURER} ${Build.MODEL}
**App Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})

$lastLogsString
        """.trimIndent()
    }
}
