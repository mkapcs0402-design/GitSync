package com.viscouspot.gitsync

import com.viscouspot.gitsync.util.Helper.extractConflictSections
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExtractConflictSectionsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun extractConflictSections_noConflicts() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("Line 1\nLine 2\nLine 3\n")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), output)
    }

    @Test
    fun extractConflictSections_singleConflictSection() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("Line 1\n<<<<<<< HEAD\nConflict Line 1\n=======\nConflict Line 2\n>>>>>>>\nLine 3\n")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(listOf("Line 1", "<<<<<<< HEAD\nConflict Line 1\n=======\nConflict Line 2\n>>>>>>>", "Line 3"), output)
    }

    @Test
    fun extractConflictSections_multipleConflictSections() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("Line 1\n<<<<<<< HEAD\nConflict 1\n=======\nConflict Alt 1\n>>>>>>>\nLine 2\n<<<<<<< HEAD\nConflict 2\n=======\nConflict Alt 2\n>>>>>>>\nLine 3\n")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(
            listOf(
                "Line 1",
                "<<<<<<< HEAD\nConflict 1\n=======\nConflict Alt 1\n>>>>>>>",
                "Line 2",
                "<<<<<<< HEAD\nConflict 2\n=======\nConflict Alt 2\n>>>>>>>",
                "Line 3"
            ), output
        )
    }

    @Test
    fun extractConflictSections_conflictAtFileStartAndEnd() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("<<<<<<< HEAD\nConflict Start\n=======\nConflict Alt Start\n>>>>>>>\nLine\n<<<<<<< HEAD\nConflict End\n=======\nConflict Alt End\n>>>>>>>\n")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(
            listOf(
                "<<<<<<< HEAD\nConflict Start\n=======\nConflict Alt Start\n>>>>>>>",
                "Line",
                "<<<<<<< HEAD\nConflict End\n=======\nConflict Alt End\n>>>>>>>"
            ), output
        )
    }

    @Test
    fun extractConflictSections_noConflictMarkers() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("This is a regular file\nwith no conflict markers.\nJust plain text.")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(listOf("This is a regular file", "with no conflict markers.", "Just plain text."), output)
    }

    @Test
    fun extractConflictSections_unbalancedConflictMarkers() {
        val file = File.createTempFile("tempFile", ".txt").apply {
            writeText("Line 1\n<<<<<<< HEAD\nConflict Line\nLine 2\n")
        }
        val output = mutableListOf<String>()
        extractConflictSections(context, file) { output.add(it) }
        assertEquals(listOf("Line 1", "<<<<<<< HEAD\nConflict Line\nLine 2"), output)
    }
}
