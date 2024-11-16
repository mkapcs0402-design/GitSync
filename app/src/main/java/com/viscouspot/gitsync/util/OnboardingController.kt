package com.viscouspot.gitsync.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.viscouspot.gitsync.BuildConfig
import com.viscouspot.gitsync.R

class OnboardingController(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val gitManager: GitManager,
    private val checkAndRequestNotificationPermission: (onGranted: (() -> Unit)?) -> Unit,
    private val checkAndRequestStoragePermission: (onGranted: (() -> Unit)?) -> Unit
    ) {
    private var hasSkipped = false

    fun show() {
        when (settingsManager.getOnboardingStep()) {
            0 -> getWelcomeDialogBuilder().show()
            1 -> showAlmostThereOrSkip()
        }
    }

    fun getAuthDialogBuilder(): AlertDialog {
        return  AlertDialog.Builder(context, R.style.AlertDialogTheme)
        .setCancelable(false)
        .setTitle(context.getString(R.string.auth_dialog_title))
        .setMessage(context.getString(R.string.auth_dialog_message))
        .setPositiveButton(context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            gitManager.launchGithubOAuthFlow()
        }
        .setNegativeButton(
            context.getString(R.string.skip)
        ) { dialog, _ ->
            dialog.dismiss()
            settingsManager.setHadFirstTime()
        }.create()
    }

    val almostThereDialogLink = TextView(context).apply {
        movementMethod = LinkMovementMethod.getInstance()
        gravity = Gravity.END
        setPadding(
            0,
            0,
            resources.getDimension(R.dimen.space_md).toInt(),
            0
        )
        text = Html.fromHtml(
            context.getString(R.string.documentation_html_link).format(context.getString(R.string.docs_link)),
            0
        )
    }

    fun getAlmostThereDialogBuilder(): AlertDialog {
        return AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setCancelable(false)
            .setTitle(context.getString(R.string.almost_there_dialog_title))
            .setView(almostThereDialogLink)
            .setMessage(context.getString(R.string.almost_there_dialog_message))
            .setPositiveButton(context.getString(android.R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                getAuthDialogBuilder().show()
            }
            .setNegativeButton(
                context.getString(android.R.string.cancel)
            ) { dialog, _ ->
                dialog.dismiss()
            }.create()
    }

    fun showAlmostThereOrSkip() {
        settingsManager.setOnboardingStep(1)
        if (hasSkipped) return
        getAlmostThereDialogBuilder().show()
    }

    fun getEnableAllFilesDialogBuilder(): AlertDialog {
        return AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setCancelable(false)
            .setTitle(context.getString(R.string.all_files_access_dialog_title))
            .setMessage(context.getString(R.string.all_files_access_dialog_message))
            .setPositiveButton(context.getString(android.R.string.ok)) { _, _ -> }
            .setNegativeButton(
                context.getString(R.string.skip)
            ) { dialog, _ ->
                dialog.dismiss()
                showAlmostThereOrSkip()
            }.create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        checkAndRequestStoragePermission {
                            dismiss()
                            showAlmostThereOrSkip()
                        }
                        this.getButton(AlertDialog.BUTTON_POSITIVE).text =
                            context.getString(R.string.done)
                    }
                }
            }
    }

    fun showAllFilesAccessOrNext() {
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        if (BuildConfig.ALL_FILES && !hasPermissions) {
            getEnableAllFilesDialogBuilder().show()
        } else {
            showAlmostThereOrSkip()
        }
    }

    fun getEnableNotificationsDialogBuilder(): AlertDialog {
        return AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setCancelable(false)
            .setTitle(context.getString(R.string.notification_dialog_title))
            .setMessage(context.getString(R.string.notification_dialog_message))
            .setPositiveButton(context.getString(android.R.string.ok)) { _, _ -> }
            .setNegativeButton(
                context.getString(R.string.skip)
            ) { dialog, _ ->
                dialog.dismiss()
                settingsManager.setSyncMessageEnabled(false)
                showAllFilesAccessOrNext()
            }.create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        checkAndRequestNotificationPermission {
                            dismiss()
                            showAllFilesAccessOrNext()
                        }
                        getButton(AlertDialog.BUTTON_POSITIVE).text =
                            context.getString(R.string.done)
                    }
                }
            }
    }

    fun showNotificationsOrNext() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            getEnableNotificationsDialogBuilder().show()
        } else {
            showAllFilesAccessOrNext()
        }
    }

    fun getWelcomeDialogBuilder(): AlertDialog {
        return AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setCancelable(false)
            .setTitle(context.getString(R.string.welcome))
            .setMessage(context.getString(R.string.welcome_message))
            .setPositiveButton(context.getString(R.string.welcome_positive)) { dialog, _ ->
                dialog.dismiss()
                showNotificationsOrNext()
            }
            .setNeutralButton(
                context.getString(R.string.welcome_neutral)
            ) { dialog, _ ->
                hasSkipped = true
                dialog.dismiss()
                showNotificationsOrNext()
            }
            .setNegativeButton(
                context.getString(R.string.welcome_negative)
            ) { dialog, _ ->
                hasSkipped = true
                settingsManager.setHadFirstTime()
                dialog.dismiss()
                showNotificationsOrNext()
            }.create()
    }
}