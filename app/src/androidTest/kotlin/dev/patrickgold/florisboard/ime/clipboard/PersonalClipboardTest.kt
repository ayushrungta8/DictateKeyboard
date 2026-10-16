package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a real Android editor and the running keyboard, not a mocked InputConnection. */
@RunWith(AndroidJUnit4::class)
class PersonalClipboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager by context.clipboardManager()

    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < until) {
            var done = false
            instrumentation.runOnMainSync { done = condition() }
            if (done) return
            SystemClock.sleep(50)
        }
        fail(message)
    }

    private fun clip(text: String) = ClipboardItem(
        id = 0, type = ItemType.TEXT, text = text, uri = null,
        creationTimestampMs = System.currentTimeMillis(), isPinned = false,
        mimeTypes = listOf("text/plain"), isSensitive = false,
    )

    @Test
    fun unicodeChunksWorkOnAndroid() = runBlocking {
        val chunks = mutableListOf<String>()
        val text = "Hello कि 👩🏽‍💻 हिंदी"
        assertTrue(typeClipboardText(text, { true }, { chunks.add(it) }, {}))
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.contains("कि"))
        assertTrue(chunks.contains("👩🏽‍💻"))
    }

    @Test
    fun typeOutInRealTextAndPasswordFieldsAndKeepHistoryAfterSystemClear() {
        ActivityScenario.launch(ClipboardTestActivity::class.java).use { scenario ->
            val app by context.appContext()
            await("Preferences did not load") { app.preferenceStoreLoaded.value }
            instrumentation.waitForIdleSync()
            lateinit var field: EditText
            scenario.onActivity { activity ->
                field = EditText(activity).apply {
                    id = 12345
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
                activity.setContentView(field)
                field.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            await("The personal keyboard did not connect") {
                val editor by context.editorInstance()
                FlorisImeService.currentInputConnection() != null && editor.activeInfo.base.fieldId == 12345
            }
            SystemClock.sleep(500)
            val phrase = "English और हिंदी १२३ 👩🏽‍💻"
            instrumentation.runOnMainSync { manager.typeOutItem(clip(phrase)) }

            await("Type-out did not insert exact English/Hindi text") { field.text.toString() == phrase }
            await("Type-out did not finish") { !manager.isTypingOut.value }

            // Changing focus must cancel before any remaining text reaches the new field.
            instrumentation.runOnMainSync { manager.typeOutItem(clip("a".repeat(200))) }
            SystemClock.sleep(100)
            lateinit var password: EditText
            scenario.onActivity { activity ->
                password = EditText(activity).apply {
                    id = 12346
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                activity.setContentView(password)
                password.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(password, InputMethodManager.SHOW_IMPLICIT)
            }
            SystemClock.sleep(800)
            assertFalse(manager.isTypingOut.value)
            instrumentation.runOnMainSync { assertEquals("", password.text.toString()) }
            instrumentation.runOnMainSync { manager.typeOutItem(clip("Test123!")) }
            await("Password field did not accept explicit type-out") { password.text.toString() == "Test123!" }
            await("Password type-out did not finish") { !manager.isTypingOut.value }

            val clipboard = context.getSystemService(ClipboardManager::class.java)
            instrumentation.runOnMainSync {
                clipboard.setPrimaryClip(ClipData.newPlainText("test", phrase))
            }
            await("History did not retain copied text") { manager.currentHistory.all.any { it.text == phrase } }
            instrumentation.runOnMainSync { clipboard.clearPrimaryClip() }
            SystemClock.sleep(500)
            assertTrue(manager.currentHistory.all.any { it.text == phrase })
            instrumentation.runOnMainSync {
                val keyboard by context.keyboardManager()
                keyboard.activeState.imeUiMode = ImeUiMode.CLIPBOARD
            }
            SystemClock.sleep(500)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.filesDir, "clipboard-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            // Visual smoke capture on the documented Pixel 8 emulator (1080 x 2400).
            // The clipboard item's bounds were inspected in clipboard-preview.png.
            val automation = instrumentation.uiAutomation
            val touchAt = SystemClock.uptimeMillis()
            instrumentation.sendPointerSync(android.view.MotionEvent.obtain(
                touchAt, touchAt, android.view.MotionEvent.ACTION_DOWN, 250f, 1810f, 0))
            SystemClock.sleep(700)
            instrumentation.sendPointerSync(android.view.MotionEvent.obtain(
                touchAt, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, 250f, 1810f, 0))
            SystemClock.sleep(400)
            automation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.filesDir, "clipboard-menu-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }

        }
    }
}
