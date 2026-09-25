package com.personal.triptrail.util

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PreparedImportCleanupTest {
    @Test fun abandonedPreviewDeletesItsMediaOnly() {
        val directory = Files.createTempDirectory("import-cleanup").toFile()
        try {
            val temporary = directory.resolve("preview.jpg").apply { writeText("preview") }
            val existing = directory.resolve("saved.jpg").apply { writeText("saved") }
            val prepared = PreparedImport("preview", listOf(temporary))
            prepared.discard()
            prepared.discard()
            assertFalse(temporary.exists())
            assertTrue(existing.exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun savedImportSurvivesPreviewDisposal() {
        val file = Files.createTempFile("saved-import", ".jpg").toFile()
        try {
            val prepared = PreparedImport("saved", listOf(file))
            prepared.commit()
            prepared.discard()
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
