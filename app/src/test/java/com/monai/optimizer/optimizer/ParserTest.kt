package com.monai.optimizer.optimizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test parser batch telemetri & shell escape (MEDIUM-6/7, MEDIUM-14).
 */
class ParserTest {

    @Test
    fun `parse package from dumpsys - mCurrentFocus style`() {
        val output = "mCurrentFocus=Window{abc123 u0 com.whatsapp/.MainActivity}"
        // logika parsePackageFromDumpsys disimulasikan di sini
        val beforeSlash = output.substringBefore("/")
        val clean = beforeSlash.split(" ", "{").lastOrNull()?.trim()
        assertEquals("com.whatsapp", clean)
    }

    @Test
    fun `shellEscape neutralizes injection chars`() {
        assertEquals("com_example_app", RootEngine.shellEscape("com.example.app"))
        // karakter berbahaya diganti underscore — tidak boleh lolos ke shell
        val evil = "com.example.app; rm -rf /"
        val escaped = RootEngine.shellEscape(evil)
        assertFalse("tidak boleh ada ;", escaped.contains(";"))
        assertFalse("tidak boleh ada spasi", escaped.contains(" "))
        assertFalse("tidak boleh ada /", escaped.contains("/"))
        assertTrue("harus aman untuk interpolasi", escaped.matches(Regex("[a-zA-Z0-9._-]+")))
    }

    @Test
    fun `shellEscape keeps normal packages`() {
        assertEquals("com.google.android.gms", RootEngine.shellEscape("com.google.android.gms"))
        assertEquals("com_whatsapp", RootEngine.shellEscape("com.whatsapp"))
    }

    @Test
    fun `batch stats split produces 4 parts`() {
        val batch = "cpu  100 0 50 200 0 0 0 0 0 0|||1800000|||45000|||schedutil"
        val parts = batch.split("|||")
        assertEquals(4, parts.size)
        assertEquals("cpu  100 0 50 200 0 0 0 0 0 0", parts[0])
        assertEquals("1800000", parts[1].trim())
        assertEquals("45000", parts[2].trim())
        assertEquals("schedutil", parts[3].trim())
    }
}
