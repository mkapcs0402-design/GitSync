package com.viscouspot.gitsync

import com.viscouspot.gitsync.util.Helper.isValidGitRepo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsValidGitRepoTest {

    @Test
    fun testValidHttpGitRepo() {
        val validUrls = listOf(
            "https://github.com/user/repo.git",
            "http://github.com/user/repo.git"
        )

        validUrls.forEach { url ->
            assertTrue("Expected true for $url", isValidGitRepo(url))
        }
    }

    @Test
    fun testInvalidGitRepo() {
        val invalidUrls = listOf(
            "git@github.com:user/repo.git",        // Invalid protocol
            "git@bitbucket.org:user/repo.git",     // Invalid protocol
            "ftp://github.com/user/repo.git",      // Invalid protocol
            "https://github.com/user/repo",        // Missing .git
            "git@github.com:user/repo",            // Missing .git
            "github.com/user/repo.git",            // Missing protocol
            "git://github.com/user/repo.git",      // Invalid protocol (git://)
            "git@github.com:/user/repo.git"        // Extra colon after @
        )

        invalidUrls.forEach { url ->
            assertFalse("Expected false for $url", isValidGitRepo(url))
        }
    }
}