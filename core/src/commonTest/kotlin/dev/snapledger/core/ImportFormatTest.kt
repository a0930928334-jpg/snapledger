package dev.snapledger.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportFormatTest {
    @Test
    fun acceptsMinorVersionsOfCurrentMajor() {
        assertTrue(ImportFormat.isSupported("1.0"))
        assertTrue(ImportFormat.isSupported("1.1"))
    }

    @Test
    fun rejectsOtherMajorVersions() {
        assertFalse(ImportFormat.isSupported("2.0"))
        assertFalse(ImportFormat.isSupported("0.9"))
    }
}
