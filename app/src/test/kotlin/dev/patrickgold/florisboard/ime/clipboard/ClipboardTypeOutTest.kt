package dev.patrickgold.florisboard.ime.clipboard

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardTypeOutTest {
    @Test
    fun `Hindi vowel marks and joined emoji remain whole and text stays exact`() = runTest {
        val chunks = mutableListOf<String>()
        val text = "Hello कि 👩🏽‍💻\nहिंदी १२३"
        assertTrue(typeClipboardText(text, { true }, { chunks.add(it) }, {}))
        assertEquals(text, chunks.joinToString(""))
        assertTrue("कि" in chunks)
        assertTrue("👩🏽‍💻" in chunks)
    }

    @Test
    fun `a rejected chunk stops without replaying the prefix`() = runTest {
        val attempted = mutableListOf<String>()
        assertFalse(typeClipboardText("abc", { true }, {
            attempted.add(it)
            it != "b"
        }, {}))
        assertEquals(listOf("a", "b"), attempted)
    }

    @Test
    fun `changing editor stops before the next character`() = runTest {
        val chunks = mutableListOf<String>()
        var current = true
        assertFalse(typeClipboardText("abc", { current }, { chunks.add(it) }, { current = false }))
        assertEquals(listOf("a"), chunks)
    }

    @Test
    fun `cancel during a delay never writes another character`() = runTest {
        val chunks = mutableListOf<String>()
        val job = launch {
            typeClipboardText("abc", { true }, { chunks.add(it) }, { delay(20) })
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf("a"), chunks)
    }
}
