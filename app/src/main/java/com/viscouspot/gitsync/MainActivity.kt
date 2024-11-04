package com.viscouspot.gitsync

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.ui.RecyclerViewEmptySupport
import com.viscouspot.gitsync.ui.adapter.ApplicationGridAdapter
import com.viscouspot.gitsync.ui.adapter.ApplicationListAdapter
import com.viscouspot.gitsync.ui.adapter.Commit
import com.viscouspot.gitsync.ui.adapter.ConflictEditorAdapter
import com.viscouspot.gitsync.ui.adapter.RecentCommitsAdapter
import com.viscouspot.gitsync.ui.fragment.CloneRepoFragment
import com.viscouspot.gitsync.util.GitManager
import com.viscouspot.gitsync.util.Helper
import com.viscouspot.gitsync.util.LogType
import com.viscouspot.gitsync.util.Logger.log
import com.viscouspot.gitsync.util.SettingsManager
import com.viscouspot.gitsync.util.rightDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var applicationObserverMax: ConstraintSet
    private lateinit var applicationObserverMin: ConstraintSet

    private lateinit var gitManager: GitManager
    private lateinit var settingsManager: SettingsManager

    private val recentCommits: MutableList<Commit> = mutableListOf()
    private lateinit var recentCommitsRecycler: RecyclerViewEmptySupport
    private lateinit var recentCommitsAdapter: RecentCommitsAdapter

    private var mergeConflictDialog: Dialog? = null

    private lateinit var forceSyncButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var syncMessageButton: MaterialButton

    private lateinit var gitRepoName: EditText
    private lateinit var cloneRepoButton: MaterialButton
    private lateinit var gitAuthButton: MaterialButton

    private lateinit var gitDirPath: EditText
    private lateinit var deselectDirButton: MaterialButton
    private lateinit var selectDirButton: MaterialButton

    private lateinit var viewDocs: MaterialButton

    private lateinit var applicationObserverPanel: ConstraintLayout
    private lateinit var applicationObserverSwitch: Switch

    private lateinit var selectApplication: MaterialButton

    private val applicationList: MutableList<Drawable> = mutableListOf()
    private lateinit var applicationRecycler: RecyclerView
    private lateinit var applicationListAdapter: ApplicationListAdapter

    private lateinit var syncAppOpened: Switch
    private lateinit var syncAppClosed: Switch

    private var onStoragePermissionGranted: (() -> Unit)? = null
    private var requestLegacyStoragePermission: ActivityResultLauncher<Array<String>>? = null
    private var requestStoragePermission: ActivityResultLauncher<Intent>? = null

    private var requestedPermission = false


    companion object {
        const val REFRESH = "REFRESH"
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                REFRESH -> refreshRecentCommits()
            }
        }
    }

    private val dirSelectionLauncher = Helper.getDirSelectionLauncher(this, this, ::dirSelectionCallback)

    private fun dirSelectionCallback(dirUri: Uri?) {
        if (dirUri == null) {
            Toast.makeText(this, getString(R.string.inaccessible_directory_message), Toast.LENGTH_LONG).show()
            return
        }

        settingsManager.setGitDirUri(dirUri.toString())


        gitDirPath.setText(Helper.getPathFromUri(this, dirUri))
        refreshGitRepo()
    }

    private val requestNotificationPermission = this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        if (code == null || state == null ) return

        log(LogType.GithubOAuthFlow, "Flow Ended")

        gitManager.getGithubAuthCredentials(code, state) { username, authToken ->
            log(LogType.GithubAuthCredentials, "Username and Token Received")

            settingsManager.setHadFirstTime()
            settingsManager.setGitAuthCredentials(username, authToken)
            refreshAuthButton()

            CloneRepoFragment(settingsManager, gitManager, ::dirSelectionCallback).show(supportFragmentManager, getString(R.string.clone_repo_title))
        }
    }

    private fun setRecyclerViewHeight(recyclerView: RecyclerView) {
        val adapter = recyclerView.adapter ?: return

        val viewHolder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
        viewHolder.itemView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        val itemHeight = (viewHolder.itemView.layoutParams as ViewGroup.MarginLayoutParams).topMargin + viewHolder.itemView.measuredHeight

        recyclerView.layoutParams.height = itemHeight * 3
        recyclerView.requestLayout()
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()

        if (settingsManager.isFirstTime()) return

        if (requestedPermission) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                settingsManager.setSyncMessageEnabled(true)
            }
            if (checkAccessibilityPermission()) {
                settingsManager.setApplicationObserverEnabled(true)
            }
            requestedPermission = false
        }

        refreshAll()

        if (gitManager.getConflicting(settingsManager.getGitDirUri()).isNotEmpty()) {
            openMergeConflictDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            log(this, LogType.Global, paramThrowable)
        }

        settingsManager = SettingsManager(this)
        settingsManager.runMigrations()

        requestLegacyStoragePermission = this.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGrantedMap ->
            if (isGrantedMap.values.all { it }) {
                onStoragePermissionGranted?.invoke()
            }
        }

        requestStoragePermission = this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermissions) {
                onStoragePermissionGranted?.invoke()
            }
        }

        if (BuildConfig.ALL_FILES && !settingsManager.isFirstTime()) {
            checkAndRequestStoragePermission()
        }

        val bManager = LocalBroadcastManager.getInstance(this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(REFRESH)
        bManager.registerReceiver(broadcastReceiver, intentFilter)

        window.statusBarColor = getColor(R.color.app_bg)

        gitManager = GitManager(this, this)

        recentCommitsRecycler = findViewById(R.id.recentCommitsRecycler)

        recentCommitsAdapter = RecentCommitsAdapter(this, recentCommits, ::openMergeConflictDialog)

        forceSyncButton = findViewById(R.id.forceSyncButton)
        settingsButton = findViewById(R.id.settingsButton)
        syncMessageButton = findViewById(R.id.syncMessageButton)

        gitRepoName = findViewById(R.id.gitRepoName)
        cloneRepoButton = findViewById(R.id.cloneRepoButton)
        gitAuthButton = findViewById(R.id.gitAuthButton)

        gitDirPath = findViewById(R.id.gitDirPath)
        deselectDirButton = findViewById(R.id.deselectDirButton)
        selectDirButton = findViewById(R.id.selectDirButton)

        viewDocs = findViewById(R.id.viewDocs)

        applicationObserverPanel = findViewById(R.id.applicationObserverPanel)
        applicationObserverSwitch = applicationObserverPanel.findViewById(R.id.enableApplicationObserver)

        selectApplication = findViewById(R.id.selectApplication)
        applicationRecycler = findViewById(R.id.applicationRecycler)
        applicationListAdapter = ApplicationListAdapter(applicationList)
        syncAppOpened = findViewById(R.id.syncAppOpened)
        syncAppClosed = findViewById(R.id.syncAppClosed)

        applicationObserverMax = ConstraintSet().apply { clone(applicationContext, R.layout.application_observer_max) }
        applicationObserverMin = ConstraintSet().apply { clone(applicationContext, R.layout.application_observer_min) }

        recentCommitsRecycler.adapter = recentCommitsAdapter
        applicationRecycler.adapter = applicationListAdapter

        setRecyclerViewHeight(recentCommitsRecycler)

        val emptyCommitsView = findViewById<TextView>(R.id.emptyCommitsView)
        recentCommitsRecycler.setEmptyView(emptyCommitsView)

        forceSyncButton.setOnClickListener {
            val forceSyncIntent = Intent(this, GitSyncService::class.java)
            forceSyncIntent.setAction(GitSyncService.FORCE_SYNC)
            startService(forceSyncIntent)
        }

        settingsButton.setOnClickListener {
            openSettingsDialog()
        }

        syncMessageButton.setOnClickListener {
            val syncMessageEnabled = settingsManager.getSyncMessageEnabled()

            if (!syncMessageEnabled) {
                checkAndRequestNotificationPermission {
                    settingsManager.setSyncMessageEnabled(true)
                    syncMessageButton.setIconResource(R.drawable.notify)
                    syncMessageButton.setIconTintResource(R.color.auth_green)
                }
            } else {
                settingsManager.setSyncMessageEnabled(false)
                syncMessageButton.setIconResource(R.drawable.notify_off)
                syncMessageButton.setIconTintResource(R.color.textPrimary)
            }
        }

        gitAuthButton.setOnClickListener {
            gitManager.launchGithubOAuthFlow()

        }

        gitDirPath.isEnabled = false

        deselectDirButton.setOnClickListener {
            settingsManager.setGitDirUri("")

            gitDirPath.setText("")
            refreshGitRepo()
            val recentCommitsSize = recentCommits.size
            recentCommits.clear()
            recentCommitsAdapter.notifyItemRangeRemoved(0, recentCommitsSize)
        }

        selectDirButton.setOnClickListener {
            dirSelectionLauncher.launch(null)
        }

        (if (settingsManager.getApplicationObserverEnabled()) applicationObserverMax else applicationObserverMin).applyTo(applicationObserverPanel)
        updateApplicationObserverSwitch()

        applicationObserverSwitch.setOnCheckedChangeListener { _, isChecked ->
            (if (isChecked) applicationObserverMax else applicationObserverMin).applyTo(applicationObserverPanel)
            if (isChecked) {
                updateApplicationObserverSwitch(true)
                if (!checkAccessibilityPermission()) {
                    applicationObserverSwitch.isChecked = false
                    syncAppOpened.isChecked = false
                    syncAppClosed.isChecked = false
                    updateApplicationObserverSwitch(false)
                    applicationObserverMin.applyTo(applicationObserverPanel)
                    displayProminentDisclosure()
                } else {
                    settingsManager.setApplicationObserverEnabled(true)
                    refreshSelectedApplications()
                }
            } else {
                updateApplicationObserverSwitch(false)
                settingsManager.setApplicationObserverEnabled(false)
            }
        }

        selectApplication.setOnClickListener {
            showApplicationSelectDialog()
        }

        syncAppOpened.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSyncOnAppOpened(isChecked)
        }

        syncAppClosed.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSyncOnAppClosed(isChecked)
        }

        viewDocs.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.docs_link)))
            startActivity(browserIntent)
        }

        if (settingsManager.isFirstTime()) {
            var hasSkipped = false

            val authDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setCancelable(false)
                .setTitle(getString(R.string.auth_dialog_title))
                .setMessage(getString(R.string.auth_dialog_message))
                .setPositiveButton(getString(android.R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                    gitManager.launchGithubOAuthFlow()
                }
                .setNegativeButton(
                    getString(R.string.skip)
                ) { dialog, _ ->
                    dialog.dismiss()
                    settingsManager.setHadFirstTime()
                }

            val almostThereDialogLink = TextView(this).apply {
                movementMethod = LinkMovementMethod.getInstance()
                gravity = Gravity.END
                setPadding(
                    0,
                    0,
                    resources.getDimension(R.dimen.space_md).toInt(),
                    0
                )
                text = Html.fromHtml(
                    getString(R.string.documentation_html_link).format(getString(R.string.docs_link)),
                    0
                )
            }

            val almostThereDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setCancelable(false)
                .setTitle(getString(R.string.almost_there_dialog_title))
                .setView(almostThereDialogLink)
                .setMessage(getString(R.string.almost_there_dialog_message))
                .setPositiveButton(getString(android.R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                    authDialogBuilder.create().show()
                }
                .setNegativeButton(
                    getString(android.R.string.cancel)
                ) { dialog, _ ->
                    dialog.dismiss()
                }

            fun showAlmostThereOrSkip() {
                if (hasSkipped) return
                almostThereDialogBuilder.create().show()
            }

            val enableAllFilesDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setCancelable(false)
                .setTitle(getString(R.string.all_files_access_dialog_title))
                .setMessage(getString(R.string.all_files_access_dialog_message))
                .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                .setNegativeButton(
                    getString(R.string.skip)
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
                            this.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.done)
                        }
                    }
                }

            fun showAllFilesAccessOrNext() {
                val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

                if (BuildConfig.ALL_FILES && !hasPermissions) {
                    enableAllFilesDialogBuilder.show()
                } else {
                    showAlmostThereOrSkip()
                }
            }

            val enableNotificationsDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setCancelable(false)
                .setTitle(getString(R.string.notification_dialog_title))
                .setMessage(getString(R.string.notification_dialog_message))
                .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                .setNegativeButton(
                    getString(R.string.skip)
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
                                getString(R.string.done)
                        }
                    }
                }

            fun showNotificationsOrNext() {
                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    enableNotificationsDialogBuilder.show()
                } else {
                    showAllFilesAccessOrNext()
                }
            }

            val welcomeDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setCancelable(false)
                .setTitle(getString(R.string.welcome))
                .setMessage(getString(R.string.welcome_message))
                .setPositiveButton(getString(R.string.welcome_positive)) { dialog, _ ->
                    dialog.dismiss()
                    showNotificationsOrNext()
                }
                .setNeutralButton(
                    getString(R.string.welcome_neutral)
                ) { dialog, _ ->
                    hasSkipped = true
                    dialog.dismiss()
                    showNotificationsOrNext()
                }
                .setNegativeButton(
                    getString(R.string.welcome_negative)
                ) { dialog, _ ->
                    hasSkipped = true
                    settingsManager.setHadFirstTime()
                    dialog.dismiss()
                    showNotificationsOrNext()
                }

            welcomeDialogBuilder
                .create()
                .show()

            return
        }

        refreshAll()
    }

    private fun openMergeConflictDialog() {
        mergeConflictDialog?.dismiss()

        val conflicts = gitManager.getConflicting(settingsManager.getGitDirUri())
        if (conflicts.isEmpty()) return

        log(conflicts.toString())

        val builder = AlertDialog.Builder(this, R.style.AlertDialogMinTheme)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.merge_conflict_dialog, null)

        builder.setView(dialogView)

        mergeConflictDialog = builder.create()

        val conflictEditor = dialogView.findViewById<HorizontalScrollView>(R.id.conflictEditor)
        val conflictEditorInput = dialogView.findViewById<RecyclerView>(R.id.conflictEditorInput)
        val fileName = dialogView.findViewById<MaterialButton>(R.id.fileName)
        val filePrev = dialogView.findViewById<MaterialButton>(R.id.filePrev)
        val fileNext = dialogView.findViewById<MaterialButton>(R.id.fileNext)
        val merge = dialogView.findViewById<MaterialButton>(R.id.merge)
        val abortMerge = dialogView.findViewById<MaterialButton>(R.id.abortMerge)

        val conflictSections = mutableListOf<String>()
        var lineCount = 0

        conflictEditorInput.adapter = ConflictEditorAdapter(this, conflictSections, conflictEditor, lineCount) {
            if (conflictSections.isEmpty() || conflictSections.firstOrNull { it.contains(getString(R.string.conflict_start)) } == null) {
                merge.isEnabled = true
                merge.backgroundTintList = ColorStateList.valueOf(getColor(R.color.auth_green))
                merge.setTextColor(getColor(R.color.card_secondary_bg))
            } else {
                merge.isEnabled = false
                merge.backgroundTintList = ColorStateList.valueOf(getColor(R.color.card_secondary_bg))
                merge.setTextColor(getColor(R.color.textSecondary))
            }
        }

        val inset = InsetDrawable(ColorDrawable(Color.TRANSPARENT), resources.getDimensionPixelOffset(R.dimen.space_lg))
        mergeConflictDialog?.window!!.setBackgroundDrawable(inset)
        mergeConflictDialog?.show()

        var conflictIndex = 0


        fileName.setOnClickListener {
            val file = File("${Helper.getPathFromUri(this, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}")

            if (file.exists()) {
                val intent = Intent(Intent.ACTION_VIEW)
                val fileUri: Uri = FileProvider.getUriForFile(this, "${this.packageName}.fileprovider", file)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "text/plain"

                intent.setDataAndType(fileUri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                this.startActivity(Intent.createChooser(intent, "Open file with"))
            }

        }

        filePrev.setOnClickListener {
            conflictIndex = max(conflictIndex - 1, 0)
            refreshMergeConflictDialog(conflicts, conflictIndex, merge, fileName, filePrev, fileNext, conflictEditorInput, conflictSections)
        }

        fileNext.setOnClickListener {
            conflictIndex = min(conflictIndex + 1, conflicts.size - 1)
            refreshMergeConflictDialog(conflicts, conflictIndex, merge, fileName, filePrev, fileNext, conflictEditorInput, conflictSections)
        }

        merge.post {
            refreshMergeConflictDialog(conflicts, conflictIndex, merge, fileName, filePrev, fileNext, conflictEditorInput, conflictSections)
        }

        merge.setOnClickListener{
            File("${Helper.getPathFromUri(this, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}").bufferedWriter().use { writer ->
                conflictSections.forEach { line ->
                    writer.write(line)
                    writer.newLine()
                }
            }

            if (conflicts.size > 1) {
                conflicts.removeAt(conflictIndex)
                conflictIndex = min(conflictIndex, conflicts.size - 1)
                refreshMergeConflictDialog(conflicts, conflictIndex, merge, fileName, filePrev, fileNext, conflictEditorInput, conflictSections)
                return@setOnClickListener
            }

            merge.text = "..."
            merge.isEnabled = false
            merge.backgroundTintList = ColorStateList.valueOf(getColor(R.color.card_secondary_bg))
            merge.setTextColor(getColor(R.color.textSecondary))
            abortMerge.visibility = View.GONE

            CoroutineScope(Dispatchers.Default).launch {
                val authCredentials = settingsManager.getGitAuthCredentials()
                if (settingsManager.getGitDirUri() == null || authCredentials.first == "" || authCredentials.second == "") return@launch

                val pushResult = gitManager.uploadChanges(
                    settingsManager.getGitDirUri()!!,
                    settingsManager.getSyncMessage(),
                    authCredentials.first,
                    authCredentials.second
                ) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            "Resolving merge...",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }

                when (pushResult) {
                    null -> {
                        log(LogType.Sync, "Merge Failed")
                        return@launch
                    }

                    true -> log(LogType.Sync, "Merge Complete")
                    false -> log(LogType.Sync, "Merge Not Required")
                }

                refreshRecentCommits()

                val forceSyncIntent = Intent(this@MainActivity, GitSyncService::class.java)
                forceSyncIntent.setAction(GitSyncService.FORCE_SYNC)
                startService(forceSyncIntent)

                mergeConflictDialog?.dismiss()
            }
        }

        abortMerge.setOnClickListener {
            gitManager.abortMerge(settingsManager.getGitDirUri())
            mergeConflictDialog?.dismiss()
            refreshRecentCommits()
        }
    }

    private fun refreshMergeConflictDialog(conflicts: MutableList<String>, conflictIndex: Int, merge: MaterialButton, fileName: MaterialButton, filePrev: MaterialButton, fileNext: MaterialButton, conflictEditorInput: RecyclerView, conflictSections: MutableList<String>) {
        filePrev.isEnabled = conflictIndex > 0
        fileNext.isEnabled = conflictIndex < conflicts.size - 1

        fileName.text = conflicts.elementAt(conflictIndex).substringAfterLast("/")

        val file = File("${Helper.getPathFromUri(this, settingsManager.getGitDirUri()!!)}/${conflicts.elementAt(conflictIndex)}")

        if (conflictSections.isNotEmpty()) {
            val conflictSectionsSize = conflictSections.size
            conflictSections.clear()
            conflictEditorInput.adapter?.notifyItemRangeRemoved(0, conflictSectionsSize)
        }

        if (file.exists()) {
            val conflictBuilder = StringBuilder()
            var inConflict = false
            var lineCount = 0

            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineCount += 1
                    when {
                        line!!.startsWith(getString(R.string.conflict_end)) -> {
                            conflictBuilder.append(line)
                            conflictSections.add(conflictBuilder.toString().trim())
                            conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
                            conflictBuilder.clear()
                            inConflict = false
                        }
                        inConflict -> {
                            conflictBuilder.append(line).append("\n")
                        }
                        line!!.startsWith(getString(R.string.conflict_start)) -> {
                            inConflict = true
                            conflictBuilder.append(line).append("\n")
                        }
                        else -> {
                            conflictSections.add(line!!.trim())
                            conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
                        }
                    }
                }

                if (conflictBuilder.isNotEmpty()) {
                    conflictSections.add(conflictBuilder.toString().trim())
                    conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
                }
            }


            log(conflictSections.toString())
        } else {
            conflictSections.add("File not found.")
            conflictEditorInput.adapter?.notifyItemInserted(conflictSections.size)
        }

        if (conflictSections.firstOrNull { it.contains("\n") || it.contains("File not found.") } == null) {
            merge.isEnabled = true
            merge.backgroundTintList = ColorStateList.valueOf(getColor(R.color.auth_green))
            merge.setTextColor(getColor(R.color.card_secondary_bg))
        } else {
            merge.isEnabled = false
            merge.backgroundTintList = ColorStateList.valueOf(getColor(R.color.card_secondary_bg))
            merge.setTextColor(getColor(R.color.textSecondary))
        }
    }

    private fun updateApplicationObserverSwitch(upDown: Boolean = settingsManager.getApplicationObserverEnabled()) {
        applicationObserverSwitch.setCompoundDrawablesWithIntrinsicBounds(null, null, ContextCompat.getDrawable(this, if (upDown) R.drawable.angle_up else R.drawable.angle_down)
            ?.apply {
                setTint(getColor(if (checkAccessibilityPermission()) R.color.auth_green else R.color.textSecondary))
            }, null)
    }

    private fun openSettingsDialog() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogMinTheme)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.settings_dialog, null)

        setupSyncMessageSettings(dialogView)
        setupGitignoreSettings(dialogView)

        builder.setView(dialogView)

        val dialog = builder.create()
        val back = ColorDrawable(Color.TRANSPARENT)
        val inset = InsetDrawable(back, resources.getDimensionPixelOffset(R.dimen.space_lg))
        dialog.window!!.setBackgroundDrawable(inset)
        dialog.show()
    }

    private fun setupSyncMessageSettings(view: View) {
        val syncMessageInput = view.findViewById<EditText>(R.id.syncMessageInput)
        syncMessageInput.setText(settingsManager.getSyncMessage())
        highlightStringInFormat(syncMessageInput)
        syncMessageInput.doOnTextChanged { text, _, _, _ ->
            settingsManager.setSyncMessage(text.toString())
            highlightStringInFormat(syncMessageInput)
        }
    }

    private fun setupGitignoreSettings(view: View) {
        val gitignoreInputWrapper = view.findViewById<HorizontalScrollView>(R.id.gitignoreInputWrapper)
        val gitignoreInput = view.findViewById<EditText>(R.id.gitignoreInput)
        gitignoreInput.setText(gitManager.readGitignore(gitDirPath.text.toString()))
        gitignoreInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                gitignoreInputWrapper.post { gitignoreInputWrapper.scrollTo(0, 0) }
            }
        }
        gitignoreInput.doOnTextChanged { text, _, _, _ ->
            gitManager.writeGitignore(gitDirPath.text.toString(), text.toString())
        }
    }

    private fun highlightStringInFormat(syncMessageInput: EditText) {
        val start = syncMessageInput.text.indexOf("%s")
        if (start == -1) return

        syncMessageInput.getText().setSpan(ForegroundColorSpan(getColor(R.color.additions)), start, start + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun getDeviceApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)

        return apps.map {
            it.activityInfo.packageName
        }.sortedBy { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }
    }

    private fun showApplicationSelectDialog() {
        val builderSingle = AlertDialog.Builder(this@MainActivity, R.style.AlertDialogTheme)
        val selectedPackageNames = mutableListOf<String>()

        builderSingle.setTitle(getString(R.string.select_application))
        builderSingle.setPositiveButton(getString(R.string.save_application)) { dialog, _ ->
            dialog.cancel()
            settingsManager.setApplicationPackages(selectedPackageNames)
            refreshSelectedApplications()
        }
        builderSingle.setNegativeButton(getString(android.R.string.cancel)) { dialog, _ -> dialog.dismiss() }

        val applicationSelectDialog = layoutInflater.inflate(R.layout.application_select_dialog, null)
        builderSingle.setView(applicationSelectDialog)
        val dialog = builderSingle.create()

        val devicePackageNames = getDeviceApps()
        val filteredDevicePackageNames = devicePackageNames.toMutableList()

        val recyclerView = applicationSelectDialog.findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = ApplicationGridAdapter(packageManager, filteredDevicePackageNames, selectedPackageNames)

        val searchView = applicationSelectDialog.findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if ((newText == null) or (newText == "")) {
                    filteredDevicePackageNames.clear()
                    filteredDevicePackageNames.addAll(devicePackageNames)
                    adapter.notifyDataSetChanged()
                } else {
                    filteredDevicePackageNames.clear()
                    filteredDevicePackageNames.addAll(
                        devicePackageNames.filter {
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(it, 0)
                            ).toString()
                                .lowercase(Locale.getDefault())
                                .contains(
                                    newText.toString().lowercase(Locale.getDefault())
                                )
                        }
                    )
                    adapter.notifyDataSetChanged()
                }
                return true
            }

        })

        recyclerView.adapter = adapter
        dialog.show()
        searchView.requestFocus()
    }

    private fun refreshAll() {
        runOnUiThread {
            refreshRecentCommits()

            if (settingsManager.getSyncMessageEnabled()) {
                settingsManager.setSyncMessageEnabled(false)
                checkAndRequestNotificationPermission {
                    settingsManager.setSyncMessageEnabled(true)
                    syncMessageButton.setIconResource(R.drawable.notify)
                    syncMessageButton.setIconTintResource(R.color.auth_green)
                }
            } else {
                syncMessageButton.setIconResource(R.drawable.notify_off)
                syncMessageButton.setIconTintResource(R.color.textPrimary)
            }

            refreshAuthButton()
            refreshGitRepo()

            settingsManager.getGitDirUri()?.let {
                gitDirPath.setText(Helper.getPathFromUri(this, it))
            }

            val applicationObserverEnabled = settingsManager.getApplicationObserverEnabled()
            applicationObserverSwitch.isChecked = applicationObserverEnabled

            if (applicationObserverEnabled) {
                if (!checkAccessibilityPermission()) {
                    applicationObserverSwitch.isChecked = false
                    settingsManager.setApplicationObserverEnabled(false)
                    requestAccessibilityPermission()
                }
            }

            (if (applicationObserverSwitch.isChecked) applicationObserverMax else applicationObserverMin).applyTo(
                applicationObserverPanel
            )

            updateApplicationObserverSwitch()
            refreshSelectedApplications()

            syncAppOpened.isChecked = settingsManager.getSyncOnAppOpened()
            syncAppClosed.isChecked = settingsManager.getSyncOnAppClosed()
        }
    }

    private fun refreshSelectedApplications() {
        runOnUiThread {
            if (settingsManager.getApplicationPackages().isEmpty()) {
                syncAppOpened.isEnabled = false
                syncAppOpened.isChecked = false
                syncAppClosed.isEnabled = false
                syncAppClosed.isChecked = false
            } else {
                syncAppOpened.isEnabled = true
                syncAppClosed.isEnabled = true
            }

            val packageNames = settingsManager.getApplicationPackages()
            if (packageNames.isNotEmpty()) {
                when (packageNames.size) {
                    1 -> {
                        selectApplication.text = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(
                                packageNames.elementAt(0),
                                0
                            )
                        ).toString()
                        selectApplication.icon =
                            packageManager.getApplicationIcon(packageNames.elementAt(0))
                        selectApplication.iconTintMode = PorterDuff.Mode.MULTIPLY
                        selectApplication.iconTint = getColorStateList(android.R.color.white)

                        applicationRecycler.visibility = View.GONE
                    }

                    else -> {
                        selectApplication.text = getString(R.string.multiple_application_selected).format(if (packageNames.size < 5) packageNames.size else getString(R.string.lg_3_apps_selected_text))
                        selectApplication.icon = null

                        applicationRecycler.visibility = View.VISIBLE
                        val iconList = packageNames.map { packageManager.getApplicationIcon(it) }
                        applicationList.clear()
                        applicationList.addAll(iconList)
                        applicationListAdapter.notifyDataSetChanged()
                    }
                }
            } else {
                selectApplication.text = getString(R.string.application_not_set)
                selectApplication.setIconResource(R.drawable.circle_plus)
                selectApplication.setIconTintResource(R.color.primary_light)
                selectApplication.iconTintMode = PorterDuff.Mode.SRC_IN

                applicationRecycler.visibility = View.GONE
            }
        }
    }

    private fun refreshRecentCommits() {
        runOnUiThread {
            val mergeConflictIndex = recentCommits.indexOfFirst { it.reference == RecentCommitsAdapter.MERGE_CONFLICT}
            if (mergeConflictIndex >= 0) {
                recentCommits.removeAt(mergeConflictIndex)
                recentCommitsAdapter.notifyItemRemoved(mergeConflictIndex)
            }

            val recentCommitsReferences = recentCommits.map { commit -> commit.reference }
            val newRecentCommits = gitManager.getRecentCommits(gitDirPath.text.toString())
                .filter { !recentCommitsReferences.contains(it.reference) }
            if (newRecentCommits.isNotEmpty()) {
                recentCommits.addAll(0, newRecentCommits)
                recentCommitsAdapter.notifyItemRangeInserted(0, newRecentCommits.size)
                recentCommitsRecycler.smoothScrollToPosition(0);
            }

            if (gitManager.getConflicting(settingsManager.getGitDirUri()).isNotEmpty()) {
                forceSyncButton.isEnabled = false

                recentCommits.add(0, Commit("", "", 0L, RecentCommitsAdapter.MERGE_CONFLICT, 0, 0))
                recentCommitsAdapter.notifyItemInserted(0)
                recentCommitsRecycler.smoothScrollToPosition(0);
            } else {
                forceSyncButton.isEnabled = true
            }
        }
    }

    private fun refreshGitRepo() {
        runOnUiThread {
            var repoName = ""
            val gitDirUri = settingsManager.getGitDirUri()

            gitDirUri?.let {
                val gitConfigFile = File("${Helper.getPathFromUri(this, it)}/${getString(R.string.git_config_path)}")
                if (gitConfigFile.exists()) {
                    val fileContents = gitConfigFile.readText()

                    val gitConfigUrlRegex = "url = (.*?)\\n".toRegex()
                    var gitConfigUrlResult = gitConfigUrlRegex.find(fileContents)
                    val url = gitConfigUrlResult?.groups?.get(1)?.value

                    val gitRepoNameRegex = ".*/([^/]+)\\.git$".toRegex()
                    val gitRepoNameResult = gitRepoNameRegex.find(url.toString())
                    repoName = gitRepoNameResult?.groups?.get(1)?.value ?: ""
                }
            }

            if (repoName == "") {
                gitRepoName.setText(getString(R.string.repo_not_found))
                gitRepoName.isEnabled = false

                cloneRepoButton.visibility = View.VISIBLE
                deselectDirButton.visibility = View.GONE
                cloneRepoButton.setOnClickListener {
                    CloneRepoFragment(settingsManager, gitManager, ::dirSelectionCallback).show(supportFragmentManager, getString(R.string.clone_repo))
                }

                applicationObserverSwitch.isChecked = false
                applicationObserverSwitch.isEnabled = false
                settingsButton.isEnabled = false

                if (gitDirPath.text.isEmpty()) {
                    gitRepoName.rightDrawable(null)
                    gitRepoName.compoundDrawablePadding = 0
                    return@runOnUiThread
                }

                gitRepoName.rightDrawable(R.drawable.circle_xmark)
                gitRepoName.compoundDrawableTintList = getColorStateList(R.color.auth_red)
                gitRepoName.compoundDrawablePadding =
                    (4 * resources.displayMetrics.density + 0.5f).toInt()

                val recentCommitsSize = recentCommits.size
                recentCommits.clear()
                recentCommitsAdapter.notifyItemRangeRemoved(0, recentCommitsSize)

                return@runOnUiThread
            }

            gitRepoName.setText(repoName)
            gitRepoName.isEnabled = true
            settingsButton.isEnabled = true
            gitRepoName.rightDrawable(R.drawable.circle_check)
            gitRepoName.compoundDrawableTintList = getColorStateList(R.color.auth_green)
            gitRepoName.compoundDrawablePadding =
                (4 * resources.displayMetrics.density + 0.5f).toInt()

            cloneRepoButton.visibility = View.GONE
            deselectDirButton.visibility = View.VISIBLE

            applicationObserverSwitch.isEnabled = true

            refreshRecentCommits()
        }
    }

    private fun refreshAuthButton() {
        runOnUiThread {
            if (settingsManager.getGitAuthCredentials().second != "") {
                gitAuthButton.icon = getDrawable(R.drawable.circle_check)
                gitAuthButton.setIconTintResource(R.color.auth_green)

                selectDirButton.isEnabled = true
                cloneRepoButton.isEnabled = true

                return@runOnUiThread
            }

            gitAuthButton.icon = getDrawable(R.drawable.circle_xmark)
            gitAuthButton.setIconTintResource(R.color.auth_red)

            selectDirButton.isEnabled = false
            cloneRepoButton.isEnabled = false
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == GitSyncAccessibilityService::class.java.name
        }
    }

    private fun displayProminentDisclosure() {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(getString(R.string.accessibility_service_disclosure_title))
            .setMessage(getString(R.string.accessibility_service_disclosure_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestAccessibilityPermission()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestAccessibilityPermission() {
        val openSettings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        openSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        startActivity(openSettings)
        requestedPermission = true
        Toast.makeText(this, getString(R.string.enable_accessibility_service), Toast.LENGTH_LONG).show()
    }

    private fun checkAndRequestNotificationPermission(onGranted: (() -> Unit)? = null) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            onGranted?.invoke()
            return
        }

        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

        requestedPermission = true
        requestNotificationPermission.launch(intent)
    }

    private fun checkAndRequestStoragePermission(onGranted: (() -> Unit)? = null) {
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED


        if (hasPermissions) {
            onGranted?.invoke()
            return
        }

        onStoragePermissionGranted = onGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = Uri.fromParts("package", packageName, null)
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            intent.data = Uri.fromParts("package", packageName, null)
            requestStoragePermission?.launch(intent)
        } else {
            requestLegacyStoragePermission?.launch(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }
}
