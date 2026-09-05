package com.qiuminal.zhhhelper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun blankOrNullNeverNewer() {
        assertFalse(Version.isNewer(null, "0.2.3"))
        assertFalse(Version.isNewer("0.2.3", null))
        assertFalse(Version.isNewer("", "0.2.3"))
        assertFalse(Version.isNewer("0.2.3", "  "))
    }

    @Test
    fun comparesMajorMinorPatch() {
        assertTrue(Version.isNewer("0.2.3", "0.2.2"))
        assertTrue(Version.isNewer("1.0.0", "0.9.9"))
        assertTrue(Version.isNewer("1.0.1", "1.0.0"))
        assertTrue(Version.isNewer("0.10.0", "0.9.9"))
        assertFalse(Version.isNewer("0.2.2", "0.2.3"))
        assertFalse(Version.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun handlesShorterSegmentsAsZero() {
        assertTrue(Version.isNewer("1.0", "0.9.9"))
        assertFalse(Version.isNewer("1.0", "1.0.1"))
    }
}
