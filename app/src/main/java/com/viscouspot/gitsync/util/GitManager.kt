package com.viscouspot.gitsync.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.syari.kgit.KGit
import com.viscouspot.gitsync.R
import com.viscouspot.gitsync.Secrets
import com.viscouspot.gitsync.ui.adapter.Commit
import com.viscouspot.gitsync.util.Logger.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.errors.NotSupportedException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.BatchingProgressMonitor
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class GitManager(private val context: Context, private val activity: AppCompatActivity? = null) {
    private val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
    private val GIT_SCOPE = "repo"
    private val client = OkHttpClient()

    fun launchGithubOAuthFlow() {
        val fullAuthUrl = "$GITHUB_AUTH_URL?client_id=${Secrets.GIT_CLIENT_ID}&scope=$GIT_SCOPE&state=${UUID.randomUUID()}"

        if (activity == null) {
            log(LogType.GithubOAuthFlow, "Activity Not Found")
            return
        }

        log(LogType.GithubOAuthFlow, "Launching Flow")
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullAuthUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun getGithubAuthCredentials(code: String, state: String, setCallback: (username: String, authToken: String) -> Unit) {
        val authTokenRequest: Request = Request.Builder()
            .url("https://github.com/login/oauth/access_token?client_id=${Secrets.GIT_CLIENT_ID}&client_secret=${Secrets.GIT_CLIENT_SECRET}&code=$code&state=$state")
            .post("".toRequestBody())
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(authTokenRequest).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                log(context, LogType.GithubAuthCredentials, e)
            }

            override fun onResponse(call: Call, response: Response) {
                log(LogType.GithubAuthCredentials, "Auth Token Obtained")
                val authToken = JSONObject(response.body?.string() ?: "").getString("access_token")

                getGithubProfile(authToken, {
                    setCallback.invoke(it, authToken)
                }, { })
            }
        })
    }

    fun getGithubProfile(authToken: String, successCallback: (username: String) -> Unit, failureCallback: () -> Unit) {
        val profileRequest: Request = Request.Builder()
            .url("https://api.github.com/user")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "token $authToken")
            .build()

        log(LogType.GithubAuthCredentials, "Getting User Profile")
        client.newCall(profileRequest).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                log(context, LogType.GithubAuthCredentials, e)
                failureCallback.invoke()
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string())
                val username = json.getString("login")

                log(LogType.GithubAuthCredentials, "Username Retrieved")
                successCallback.invoke(username)
            }

        })
    }

    fun getRepos(authToken: String, updateCallback: (repos: List<Pair<String, String>>) -> Unit, nextPageCallback: (nextPage: (() -> Unit)?) -> Unit){
        log(LogType.GetRepos, "Getting User Repos")
        getReposRequest(authToken, "https://api.github.com/user/repos", updateCallback, nextPageCallback)
    }

    private fun getReposRequest(authToken: String, url: String, updateCallback: (repos: List<Pair<String, String>>) -> Unit, nextPageCallback: (nextPage: (() -> Unit)?) -> Unit) {
        client.newCall(
            Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "token $authToken")
                .build()
        ).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                log(context, LogType.GetRepos, e)
            }

            override fun onResponse(call: Call, response: Response) {
                log(LogType.GetRepos, "Repos Received")

                val jsonArray = JSONArray(response.body?.string())
                val repoMap: MutableList<Pair<String, String>> = mutableListOf()

                val link = response.headers["link"] ?: ""

                if (link != "") {
                    val regex = "<(.*?)>; rel=\"next\"".toRegex()

                    val match = regex.find(link)
                    val result = match?.groups?.get(1)?.value

                    val nextLink = result ?: ""
                    if (nextLink != "") {
                        nextPageCallback {
                            getReposRequest(authToken, nextLink, updateCallback, nextPageCallback)
                        }
                    } else {
                        nextPageCallback(null)
                    }
                }

                for (i in 0..<jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val cloneUrl = obj.getString("clone_url")
                    repoMap.add(Pair(name, cloneUrl))
                }

                updateCallback.invoke(repoMap)
            }
        })
    }

    fun cloneRepository(repoUrl: String, userStorageUri: Uri, username: String, token: String, taskCallback: (action: String) -> Unit, progressCallback: (progress: Int) -> Unit, failureCallback: (error: String) -> Unit, successCallback: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log(LogType.CloneRepo, "Cloning Repo")

                val monitor = object : BatchingProgressMonitor() {
                    override fun onUpdate(taskName: String?, workCurr: Int, duration: Duration?) {}

                    override fun onUpdate(
                        taskName: String?,
                        workCurr: Int,
                        workTotal: Int,
                        percentDone: Int,
                        duration: Duration?
                    ) {
                        taskCallback(taskName ?: "")
                        progressCallback(percentDone)
                    }

                    override fun onEndTask(taskName: String?, workCurr: Int, duration: Duration?) {
                    }

                    override fun onEndTask(
                        taskName: String?,
                        workCurr: Int,
                        workTotal: Int,
                        percentDone: Int,
                        duration: Duration?
                    ) {}
                }

                KGit.cloneRepository {
                    setURI(repoUrl)
                    setProgressMonitor(monitor)
                    setDirectory(File(Helper.getPathFromUri(context, userStorageUri)))
                    setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                }

                log(LogType.CloneRepo, "Repository cloned successfully")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Repository cloned successfully", Toast.LENGTH_SHORT).show()
                }

                successCallback.invoke()
            } catch (e: InvalidRemoteException) {
                failureCallback(context.getString(R.string.invalid_remote))
                return@launch
            } catch (e: TransportException) {
                failureCallback(e.localizedMessage ?: context.getString(R.string.clone_failed))
                return@launch
            } catch (e: GitAPIException) {
                failureCallback(context.getString(R.string.clone_failed))
                return@launch
            }
            catch (e: JGitInternalException) {
                if (e.cause is NotSupportedException) {
                    failureCallback(context.getString(R.string.invalid_remote))
                } else {
                    failureCallback(e.localizedMessage ?: context.getString(R.string.clone_failed))
                }
                return@launch
            } catch (e: OutOfMemoryError) {
                failureCallback(context.getString(R.string.out_of_memory))
                return@launch
            } catch (e: Throwable) {
                failureCallback(context.getString(R.string.clone_failed))

                log(context, LogType.CloneRepo, e)
            }
        }
    }

    fun pullRepository(userStorageUri: Uri, username: String, token: String, onSync: () -> Unit): Boolean? {
        try {
            var returnResult: Boolean? = false
            log(LogType.PullFromRepo, "Getting local directory")
            val repo = FileRepository("${Helper.getPathFromUri(context, userStorageUri)}/.git")
            val git = KGit(repo)
            val cp = UsernamePasswordCredentialsProvider(username, token)

            log(LogType.PullFromRepo, "Fetching changes")
            val fetchResult = git.fetch {
                setCredentialsProvider(cp)
            }

            if (!fetchResult.trackingRefUpdates.isEmpty()) {
                log(LogType.PullFromRepo, "Pulling changes")
                onSync.invoke()
                val result = git.pull {
                    setCredentialsProvider(cp)
                    remote = "origin"
                }
                if (result.isSuccessful()) {
                    returnResult = true
                } else {
                    returnResult = null
                }
            }

            log(LogType.PullFromRepo, "Closing repository")
            closeRepo(repo)

            return returnResult
        } catch (e: Exception) {
            log(context, LogType.PullFromRepo, e)
        }
        return null
    }

    fun pushAllToRepository(repoUrl: String, userStorageUri: Uri, username: String, token: String, onSync: () -> Unit): Boolean? {
        try {
            var returnResult = false
            log(LogType.PushToRepo, "Getting local directory")

            val repo = FileRepository("${Helper.getPathFromUri(context, userStorageUri)}/.git")
            val git = KGit(repo)

            logStatus(git)
            val status = git.status()

            if (status.uncommittedChanges.isNotEmpty() || status.untracked.isNotEmpty()) {
                onSync.invoke()
                log(LogType.PushToRepo, "Adding Files to Stage")
                git.add {
                    addFilepattern(".")
                }
                git.add {
                    addFilepattern(".")
                    isUpdate = true
                }

                log(LogType.PushToRepo, "Getting current time")
                val currentDateTime = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

                log(LogType.PushToRepo, "Committing changes")
                git.commit {
                    setCommitter(username, "")
                    message = "Last Sync: ${currentDateTime.format(formatter)} (Mobile)"
                }

                returnResult = true
            }

            log(LogType.PushToRepo, "Pushing changes")
            for (pushResult in git.push {
                setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                remote = repoUrl
            }) {
                for (remoteUpdate in pushResult.remoteUpdates) {
                    when (remoteUpdate.status) {
                        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            log(LogType.PushToRepo, "Attempting rebase on REJECTED_NONFASTFORWARD")
                            val rebaseResult = git.rebase {
                                setUpstream((repo.fullBranch ?: repo.findRef("HEAD")?.target?.name)!!)
                            }

                            if (rebaseResult.status != RebaseResult.Status.OK) {
                                git.rebase {
                                    setOperation(RebaseCommand.Operation.ABORT)
                                }
                                throw Exception("Remote is further ahead than local and we could not automatically rebase for you!")
                            }
                            break
                        }
                        else -> {}
                    }
                }
            }

            if (Looper.myLooper() == null) {
                Looper.prepare()
            }

            logStatus(git)

            log(LogType.PushToRepo, "Closing repository")
            closeRepo(repo)

            return returnResult
        } catch (e: Exception) {
            log(context, LogType.PushToRepo, e)
        }
        return null
    }

    private fun logStatus(git: KGit) {
        val status = git.status()
        log(LogType.GitStatus, """
            HasUncommittedChanges: ${status.hasUncommittedChanges()}
            Missing: ${status.missing}
            Modified: ${status.modified}
            Removed: ${status.removed}
            IgnoredNotInIndex: ${status.ignoredNotInIndex}
            Changed: ${status.changed}
            Untracked: ${status.untracked}
            Added: ${status.added}
            Conflicting: ${status.conflicting}
            UncommittedChanges: ${status.uncommittedChanges}
        """.trimIndent())
    }

    fun getRecentCommits(gitDirPath: String): List<Commit> {
        try {
            if (!File("$gitDirPath/.git").exists()) return listOf()

            log(LogType.RecentCommits, ".git folder found")
            
            val repo = FileRepository("$gitDirPath/.git")
            val revWalk = RevWalk(repo)

            val headRef = repo.fullBranch ?: repo.findRef("HEAD")?.target?.name
            val head = repo.resolve(headRef)
            revWalk.markStart(revWalk.parseCommit(head))
            log(LogType.RecentCommits, "HEAD parsed")
            
            revWalk.sort(RevSort.COMMIT_TIME_DESC)

            val commits = mutableListOf<Commit>()
            var count = 0
            val iterator = revWalk.iterator()

            while (iterator.hasNext() && count < 10) {
                val commit = iterator.next()

                val diffFormatter = DiffFormatter(DisabledOutputStream.INSTANCE)
                diffFormatter.setRepository(repo)
                val parent = if (commit.parentCount > 0) commit.getParent(0) else null
                val diffs = if (parent != null) diffFormatter.scan(parent.tree, commit.tree) else listOf()

                var additions = 0
                var deletions = 0
                for (diff in diffs) {
                    val editList = diffFormatter.toFileHeader(diff).toEditList()
                    for (edit in editList) {
                        additions += edit.endB - edit.beginB
                        deletions += edit.endA - edit.beginA
                    }
                }

                commits.add(
                    Commit(
                        commit.shortMessage,
                        commit.authorIdent.name,
                        commit.authorIdent.`when`.time,
                        commit.name.substring(0, 7),
                        additions,
                        deletions
                    )
                )
                count++
            }

            log(LogType.RecentCommits, "Recent commits retrieved")
            revWalk.dispose()
            closeRepo(repo)

            return commits
        } catch (e: java.lang.Exception) {
            log(context, LogType.RecentCommits, e)
        }
        return listOf()
    }

    private fun closeRepo(repo: FileRepository) {
        repo.close()
        val lockFile = File(repo.directory, "index.lock")
        if (lockFile.exists()) lockFile.delete()
    }
}