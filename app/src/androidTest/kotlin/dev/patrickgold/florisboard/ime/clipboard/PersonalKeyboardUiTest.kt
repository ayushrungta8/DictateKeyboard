package dev.patrickgold.florisboard.ime.clipboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.os.SystemClock
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual IME controls against an Android editor, including the explicit paste fallback. */
@RunWith(AndroidJUnit4::class)
class PersonalKeyboardUiTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private val automation = instrument.uiAutomation
    private val manager by context.clipboardManager()
    private val prefs by FlorisPreferenceStore

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrument.runOnMainSync { ready = predicate() }
            if (ready) return
            SystemClock.sleep(80)
        }
        capture("failure")
        val keyboard by context.keyboardManager()
        println("UI failure state: ${keyboard.activeState.imeUiMode}")
        fail(message)
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        // Compose reuses its IME window across panels. Android can retain the previous virtual tree.
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { return it } }
            return null
        }
        return automation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { visit(it) }
    }

    private fun tap(label: String, holdMs: Long = 60) {
        var node: AccessibilityNodeInfo? = null
        await("Control not visible: $label") { node = find(label); node != null }
        // Real touches also verify hit targets; virtual ACTION_CLICK IDs can be retired between IME panels.
        val bounds = android.graphics.Rect()
        node!!.getBoundsInScreen(bounds)
        assertFalse("Control has no touch bounds: $label", bounds.isEmpty)
        val at = SystemClock.uptimeMillis()
        instrument.sendPointerSync(android.view.MotionEvent.obtain(at, at,
            android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0))
        if (holdMs > 60) SystemClock.sleep(holdMs)
        instrument.sendPointerSync(android.view.MotionEvent.obtain(at, SystemClock.uptimeMillis(),
            android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0))
        SystemClock.sleep(250)
    }

    private fun capture(name: String) {
        instrument.waitForIdleSync()
        automation.waitForIdle(200, 2000)
        SystemClock.sleep(500)
        // UiAutomation.takeScreenshot can omit glyph layers on this emulator; screencap captures the actual compositor.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p")).use { input ->
            java.io.File(context.filesDir, "ui-$name.png").outputStream().use { input.copyTo(it) }
        }
    }

    @Test
    fun visibleLanguageSwitchAndClipboardActions() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        ActivityScenario.launch(ClipboardTestActivity::class.java).use { scenario ->
            val app by context.appContext()
            await("Preferences not loaded") { app.preferenceStoreLoaded.value }
            lateinit var field: EditText
            scenario.onActivity { activity ->
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 60, 32, 16)
                    setBackgroundColor(Color.rgb(247, 247, 247))
                }
                layout.addView(TextView(activity).apply {
                    text = "Keyboard test"; textSize = 24f; setTextColor(Color.DKGRAY)
                })
                layout.addView(TextView(activity).apply {
                    text = "Hinglish, English, and your saved clips."; textSize = 16f
                    setPadding(0, 20, 0, 30); setTextColor(Color.DKGRAY)
                })
                field = EditText(activity).apply {
                    id = 98123
                    hint = "Type or tap Dictate…"
                    setHintTextColor(Color.GRAY)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 3; gravity = android.view.Gravity.TOP
                    setTextColor(Color.DKGRAY)
                }
                layout.addView(field)
                activity.setContentView(layout)
                field.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            val keyboard by context.keyboardManager()
            await("Keyboard did not connect") { find("Open clipboard") != null }
            if (InstrumentationRegistry.getArguments().getString("previewOnly") == "true") {
                capture("phone-keyboard")
                tap("Open clipboard")
                capture("phone-clipboard")
                tap("Keyboard")
                return
            }
            tap("Hinglish, English letters")
            await("EN was not selected") { prefs.dictate.activeInputLanguage.get() == "en" }
            tap("English dictation")
            await("HING was not selected") { prefs.dictate.activeInputLanguage.get() == DictateLanguages.HINGLISH }
            capture("keyboard")
            if (InstrumentationRegistry.getArguments().getString("inspectPreview") == "true") SystemClock.sleep(10000)
            if (InstrumentationRegistry.getArguments().getString("testMic") == "true") {
                tap("1")
                await("Number row did not insert a digit") { field.text.toString() == "1" }
                instrument.runOnMainSync { field.setText("") }
                SystemClock.sleep(350)
                tap("Q")
                await("Uppercase key face changed lowercase typing") { field.text.toString() == "q" }
                instrument.runOnMainSync { field.setText("") }
                SystemClock.sleep(350)
                tap("Q", holdMs = 650)
                await("Q long-press did not insert its displayed percent symbol") { field.text.toString() == "%" }
                instrument.runOnMainSync { field.setText("") }
                SystemClock.sleep(350)
                tap(".", holdMs = 650)
                await("Period long-press did not insert a question mark") { field.text.toString() == "?" }
                instrument.runOnMainSync { field.setText("") }
                SystemClock.sleep(350)
                tap(",", holdMs = 650)
                await("Comma long-press did not start dictation") { DictateController.state.value is DictateController.UiState.Recording }
                tap("Cancel recording")
                await("Comma recording did not cancel") { DictateController.state.value is DictateController.UiState.Idle }
                tap("Dictate")
                await("Mic did not start recording") { DictateController.state.value is DictateController.UiState.Recording }
                capture("recording")
                tap("Cancel recording")
                await("Recording did not cancel") { DictateController.state.value is DictateController.UiState.Idle }
            }

            val phrase = "Kal morning mein meeting hai. Please send the report."
            val added = ClipboardItem(
                id = 0, type = ItemType.TEXT, text = phrase, uri = null,
                creationTimestampMs = System.currentTimeMillis(), isPinned = false,
                mimeTypes = listOf("text/plain"), isSensitive = false,
            )
            try {
                // Seed only our own test row. Do not touch the system clipboard or show its overlay.
                instrument.runOnMainSync { manager.insertClip(added) }
                await("Clip not saved") { added.id != 0L && manager.currentHistory.all.any { it.id == added.id } }
                tap("Open clipboard")
                await("Clipboard did not open") { keyboard.activeState.imeUiMode == ImeUiMode.CLIPBOARD }
                capture("clipboard")
                tap("Keyboard")
                await("Keyboard did not return from clipboard") { keyboard.activeState.imeUiMode == ImeUiMode.TEXT }
                tap("Open clipboard")
                capture("reopened")
                tap("Pin clip")
                await("Clip did not pin") { manager.currentHistory.all.any { it.text == phrase && it.isPinned } }
                tap("Paste")
                await("Paste did not insert the clip") { field.text.toString() == phrase }
                if (prefs.clipboard.historyHideOnPaste.get()) {
                    await("Paste did not return to keyboard") { keyboard.activeState.imeUiMode == ImeUiMode.TEXT }
                } else {
                    tap("Keyboard")
                }
                // Replacing an Android editor's contents restarts its input session. Clear before opening the panel.
                instrument.runOnMainSync { field.setText("") }
                SystemClock.sleep(350)
                tap("Open clipboard")
                await("Clipboard did not reopen for type-out") { keyboard.activeState.imeUiMode == ImeUiMode.CLIPBOARD }
                capture("before-typeout")
                tap("Type out")
                await("Type out did not insert the clip") { field.text.toString() == phrase }
                await("Type out did not finish") { !manager.isTypingOut.value }
                capture("typed")
                println("UI mode after type-out: ${keyboard.activeState.imeUiMode}")
                if (keyboard.activeState.imeUiMode == ImeUiMode.CLIPBOARD) tap("Keyboard")
                await("Keyboard did not return") { keyboard.activeState.imeUiMode == ImeUiMode.TEXT && find("Dictate") != null }
            } finally {
                instrument.runOnMainSync {
                    manager.cancelTypeOut()
                    manager.deleteClip(added, onlyIfUnpinned = false)
                    keyboard.activeState.imeUiMode = ImeUiMode.TEXT
                }
            }
        }
    }
}
