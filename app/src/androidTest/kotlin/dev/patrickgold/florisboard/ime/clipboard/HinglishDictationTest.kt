package dev.patrickgold.florisboard.ime.clipboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.flow.first
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HinglishDictationTest {
    @Test
    fun mixedSpeechStaysRomanAndEnglishStillUsesOriginalModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app by context.appContext()
        app.preferenceStoreLoaded.first { it }
        val prefs by FlorisPreferenceStore
        prefs.dictate.activeInputLanguage.set("hi-Latn")
        assertTrue(prefs.dictate.inputLanguages.get().contains("hi-Latn"))
        val modelId = InstrumentationRegistry.getArguments().getString("modelId") ?: "hinglish-swift"
        val model = File(context.filesDir, "dictate-models/$modelId")
        val samples = File(context.filesDir, "model-smoke")
        assumeTrue(File(model, "encoder.onnx").exists() && File(samples, "hindi.wav").exists())
        val provider = LocalTranscriptionProvider(model)
        val results = mutableListOf<String>()
        for (name in listOf("hindi", "mixed", "example")) {
            val audio = File(samples, "$name.wav")
            if (!audio.exists()) continue
            val start = System.nanoTime()
            val text = provider.transcribe(TranscriptionRequest(audio, modelId, "hi-Latn")).text
            results += "$name (${(System.nanoTime() - start) / 1_000_000} ms): $text"
            File(samples, "$modelId-results.txt").writeText(results.joinToString("\n"))
            assertTrue("Empty $name result", text.isNotBlank())
            assertFalse("$name unexpectedly used Devanagari: $text", text.any { it in '\u0900'..'\u097F' })
            val words = text.lowercase()
            if (name == "hindi") assertTrue("Lost the reference sentence: $text", listOf("main", "kal", "call", "kya").all { it in words })
            if (name == "mixed") assertTrue("Lost English words: $text", listOf("morning", "meeting", "send", "report").all { it in words })
        }
        val english = LocalTranscriptionProvider(File(context.filesDir, "dictate-models/whisper-small"))
            .transcribe(TranscriptionRequest(File(samples, "english.wav"), "whisper-small", "en")).text
        assertEquals("Please send the meeting notes tomorrow morning.", english)
    }
}
