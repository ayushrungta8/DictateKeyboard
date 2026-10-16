package dev.patrickgold.florisboard.ime.clipboard

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in smoke test: requires local Whisper Small weights and fixture WAVs in the target files dir. */
@RunWith(AndroidJUnit4::class)
class OfflineDictationSmokeTest {
    @Test
    fun englishAndHindiWithTheNetworkDisabled() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, "dictate-models/whisper-small")
        assumeTrue("Provision local model/fixtures before running this optional smoke test",
            File(model, "encoder.onnx").exists() && File(context.filesDir, "model-smoke/hindi.wav").exists())
        val provider = LocalTranscriptionProvider(model)
        val records = mutableListOf<String>()
        for ((name, language) in listOf("english" to "en", "hindi" to "hi", "mixed" to null)) {
            val audio = File(context.filesDir, "model-smoke/$name.wav")
            assertTrue("Fixture is missing: $audio", audio.exists())
            val start = System.nanoTime()
            val result = provider.transcribe(TranscriptionRequest(audio, "whisper-small", language))
            val ms = (System.nanoTime() - start) / 1_000_000
            val record = "$name: ${ms}ms: ${result.text}"
            Log.i("PersonalKeyboardSmoke", record)
            records += record
            File(context.filesDir, "model-smoke/results.txt").writeText(records.joinToString("\n"))
            assertTrue("Empty $name transcription", result.text.isNotBlank())
            if (name == "hindi") assertTrue("Expected Devanagari output", result.text.any { it in '\u0900'..'\u097F' })
        }
    }
}
