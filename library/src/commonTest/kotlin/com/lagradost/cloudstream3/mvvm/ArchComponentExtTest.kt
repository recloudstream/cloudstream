package com.lagradost.cloudstream3.mvvm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchComponentExtTest {

    @Test
    fun getStackTracePrettyStripsFullyQualifiedClassNames() {
        val throwable = try {
            throw IllegalStateException("Exception")
        } catch (t: Throwable) {
            t
        }

        val pretty = throwable.getStackTracePretty()

        // Should not contain the fully qualified package/class prefix
        assertFalse(
            pretty.contains("com.lagradost.cloudstream3.mvvm.ArchComponentExtTest"),
            "Expected no fully qualified class names, got:\n$pretty"
        )

        // Should still reference the source file itself
        assertTrue(
            pretty.contains("ArchComponentExtTest.kt"),
            "Expected pretty stack trace to reference a .kt file, got:\n$pretty"
        )
    }
}
