package com.viscouspot.gitsync.ui.fragment

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.viscouspot.gitsync.BuildConfig
import com.viscouspot.gitsync.R
import com.viscouspot.gitsync.ui.adapter.RepoListAdapter
import com.viscouspot.gitsync.util.GitManager
import com.viscouspot.gitsync.util.Helper
import com.viscouspot.gitsync.util.LogType
import com.viscouspot.gitsync.util.Logger.log
import com.viscouspot.gitsync.util.SettingsManager
import com.viscouspot.gitsync.util.rightDrawable

class CloneRepoFragment(
    private val settingsManager: SettingsManager,
    private val gitManager: GitManager,
    private val dirSelectionCallback: ((dirUri: Uri?) -> Unit)
): DialogFragment(R.layout.clone_repo_fragment) {
    private val repoList = mutableListOf<Pair<String, String>>()
    private var repoUrl = ""
    private var loadNextRepos: (() -> Unit)? = {}
    private var callback: ((dirUri: Uri?) -> Unit) = {dirUri: Uri? -> dirSelectionCallback(dirUri)}
    private lateinit var adapter: RepoListAdapter
    private lateinit var repoListRecycler: RecyclerView
    private var loadingRepos = false

    private lateinit var dirSelectionLauncher: ActivityResultLauncher<Uri?>

    override fun onAttach(context: Context) {
        super.onAttach(context)

        dirSelectionLauncher = Helper.getDirSelectionLauncher(this, requireContext()) {
            callback.invoke(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.clone_repo_fragment, container, false)

        repoListRecycler = view.findViewById(R.id.repoList)
        val repoUrlEditText = view.findViewById<EditText>(R.id.repoUrlEditText)
        val pullButton = view.findViewById<MaterialButton>(R.id.pullButton)
        val divider = view.findViewById<View>(R.id.divider)
        val localRepo = view.findViewById<MaterialButton>(R.id.localRepo)
        repoListRecycler.setLayoutManager(GridLayoutManager(context, 1))

        adapter = RepoListAdapter(repoList) {
            repoUrl = it.second
            selectLocalDir()
        }

        setLoadingRepos(true)
        gitManager.getGithubRepos(settingsManager.getGitAuthCredentials().second, ::addRepos) {
            loadNextRepos = it
        }

        repoUrlEditText.doOnTextChanged { _, _, _, _ ->
            repoUrlEditText.rightDrawable(null)
        }

        pullButton.setOnClickListener {
            if (Helper.isValidGitRepo(repoUrlEditText.text.toString())) {
                repoUrl = repoUrlEditText.text.toString()
                selectLocalDir()
            } else {
                repoUrlEditText.rightDrawable(R.drawable.circle_xmark)
                repoUrlEditText.compoundDrawableTintList = requireContext().getColorStateList(R.color.auth_red)
            }
        }

        repoListRecycler.setAdapter(adapter)
        repoListRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val linearLayoutManager = recyclerView.layoutManager as LinearLayoutManager?
                if (!loadingRepos) {
                    if (linearLayoutManager != null && linearLayoutManager.findLastCompletelyVisibleItemPosition() == repoList.size - 1) {
                        setLoadingRepos(true)
                        loadNextRepos?.invoke()
                    }
                }
            }
        })

        localRepo.setOnClickListener {
            selectLocalRepo()
        }

        if (!BuildConfig.ALL_FILES) {
            divider.visibility = View.GONE
            localRepo.visibility = View.GONE
        }

        return view
    }

    private fun setLoadingRepos(loading: Boolean) {
        if (loading && loadNextRepos != null) {
            repoList.add(Pair("Loading...", ""))
            activity?.runOnUiThread {
                adapter.notifyItemInserted(repoList.size - 1)
                repoListRecycler.scrollToPosition(repoList.size - 1)
            }

        } else {
            val loadingIndex = repoList.indexOf(Pair("Loading...", ""))
            if (loadingIndex > -1) {
                repoList.removeAt(loadingIndex)
                activity?.runOnUiThread {
                adapter.notifyItemRemoved(loadingIndex)
                }
            }
        }
        loadingRepos = loading
    }

    private fun addRepos(repos: List<Pair<String, String>>) {
        val prevEnd = repoList.size - 1
        setLoadingRepos(false)
        repoList.addAll(repos)
        activity?.runOnUiThread {
            adapter.notifyItemRangeInserted(prevEnd, repos.size)
            repoListRecycler.scrollToPosition(prevEnd)
        }
    }

    private fun localRepoCallback(dirUri: Uri?){
        dirSelectionCallback.invoke(dirUri)
        dismiss()
    }

    private fun selectLocalRepo() {
        callback = ::localRepoCallback
        dirSelectionLauncher.launch(null)
    }

    private fun localDirCallback(dirUri: Uri?) {
        if (dirUri == null) {
            dirSelectionCallback.invoke(null)
            dismiss()
            return
        }
        val authCredentials = settingsManager.getGitAuthCredentials()

        val cloneDialog = ProgressDialog(requireContext()).apply {
            setTitle(getString(R.string.cloning_repository))
            setMessage(getString(R.string.clone_message))
            max = 100
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            show()
        }
        gitManager.cloneRepository(repoUrl, dirUri, authCredentials.first, authCredentials.second,
            { task -> activity?.runOnUiThread { cloneDialog.setMessage("${getString(R.string.clone_message)}$task") } },
            { progress -> cloneDialog.progress = progress},
            { error ->
                log(LogType.CloneRepo, error)
                requireActivity().runOnUiThread {
                    Toast.makeText(context, getString(R.string.clone_failed), Toast.LENGTH_SHORT).show()
                    cloneDialog.dismiss()
                    val message = if (getString(R.string.clone_failed) == error) "" else error
                    AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        .setTitle(getString(R.string.clone_failed))
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                        }
                        .show()
                }
            },
            {
                cloneDialog.dismiss()
                dirSelectionCallback.invoke(dirUri)
                dismiss()
            })
    }

    private fun selectLocalDir() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Select a folder to clone into")
            .setPositiveButton("select") { _, _ ->
                callback = ::localDirCallback
                dirSelectionLauncher.launch(null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            dialog.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}