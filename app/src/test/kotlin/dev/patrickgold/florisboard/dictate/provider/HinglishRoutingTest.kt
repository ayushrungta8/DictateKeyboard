package dev.patrickgold.florisboard.dictate.provider

import dev.patrickgold.florisboard.dictate.DictateLanguages
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HinglishRoutingTest {
    @Test
    fun `Hinglish has a distinct visible mode and uses a Roman Hinglish model`() {
        val mode = DictateLanguages.of(DictateLanguages.HINGLISH)
        assertEquals("HING", mode.shortCode)
        assertEquals("Hinglish (English letters)", mode.displayName(Locale.ENGLISH))
        assertNotEquals(DictateLanguages.of("hi"), mode)
        assertEquals("hinglish-apex", LocalModelCatalog.personalModelForLanguage(mode.code, "whisper-small"))
    }

    @Test
    fun `English keeps the original engine even if a Hinglish engine was selected`() {
        assertEquals("whisper-small", LocalModelCatalog.personalModelForLanguage("en", "whisper-small"))
        assertEquals("whisper-small", LocalModelCatalog.personalModelForLanguage("en", "hinglish-apex"))
        assertEquals("whisper-small", LocalModelCatalog.personalModelForLanguage("en", "hinglish-swift"))
    }

    @Test
    fun `explicit fast Hinglish choice is respected but other languages are unchanged`() {
        assertEquals("hinglish-swift", LocalModelCatalog.personalModelForLanguage("hi-Latn", "hinglish-swift"))
        assertEquals("whisper-small", LocalModelCatalog.personalModelForLanguage("hi", "whisper-small"))
        assertEquals("whisper-small", LocalModelCatalog.personalModelForLanguage("detect", "whisper-small"))
    }
}
