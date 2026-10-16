/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.ui.DictateSmartbarUi
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.verticalTween

const val AnimationDuration = 200
val VerticalEnterTransition = EnterTransition.verticalTween(AnimationDuration)
val VerticalExitTransition = ExitTransition.verticalTween(AnimationDuration)
val PersonalToolbarHeight = 44.dp

/** The core controls never collapse, change order, or disappear behind suggestions. */
@Composable
fun Smartbar() {
    PersonalKeyboardTheme {
        val prefs by FlorisPreferenceStore
        val context = LocalContext.current
        val keyboard by context.keyboardManager()
        val nlp by context.nlpManager()
        val editor by context.editorInstance()
        val keyboardState by keyboard.activeState.collectAsState()
        val language by prefs.dictate.activeInputLanguage.collectAsState()
        val rawState by DictateController.state.collectAsState()
        // Personal keyboard: promotional nudges never take over the suggestion row.
        val state = if (rawState is DictateController.UiState.Promo) DictateController.UiState.Idle else rawState
        LaunchedEffect(rawState) {
            if (rawState is DictateController.UiState.Promo) DictateController.declinePromo()
        }
        val candidates by nlp.activeCandidatesFlow.collectAsState()
        val inline by NlpInlineAutofill.suggestions.collectAsState()
        val busy = state is DictateController.UiState.Recording ||
            state is DictateController.UiState.Transcribing || state is DictateController.UiState.Rewording
        val recording = state is DictateController.UiState.Recording
        var menuOpen by remember { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme

        Column(Modifier.fillMaxWidth().background(colors.surface)) {
            Row(Modifier.fillMaxWidth().height(PersonalToolbarHeight), verticalAlignment = Alignment.CenterVertically) {
                @Composable
                fun Tool(label: String, enabled: Boolean = !busy, action: () -> Unit, content: @Composable () -> Unit) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled,
                        role = Role.Button, onClick = action).semantics { contentDescription = label },
                        contentAlignment = Alignment.Center) { content() }
                }
                Tool("Emoji", action = { keyboard.activeState.imeUiMode = ImeUiMode.MEDIA }) {
                    Icon(Icons.Outlined.EmojiEmotions, null, Modifier.size(21.dp))
                }
                Tool("GIFs", action = { keyboard.activeState.imeUiMode = ImeUiMode.GIF }) {
                    Box(Modifier.size(width = 22.dp, height = 20.dp)
                        .border(1.3.dp, colors.onSurface, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text("GIF", fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Tool("Stickers", action = { keyboard.activeState.imeUiMode = ImeUiMode.STICKER }) {
                    Icon(PersonalIcons.Sticker, null, Modifier.size(21.dp))
                }
                Tool("Open clipboard", action = { keyboard.activeState.imeUiMode = ImeUiMode.CLIPBOARD }) {
                    Icon(PersonalIcons.Clipboard, null, Modifier.size(21.dp))
                }
                Tool(if (language == DictateLanguages.HINGLISH) "Hinglish, English letters" else "English dictation",
                    action = { DictateController.setLanguage(if (language == DictateLanguages.HINGLISH) "en" else DictateLanguages.HINGLISH) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (language == DictateLanguages.HINGLISH) "HING" else "EN", fontSize = 12.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.SwapHoriz, null, Modifier.size(10.dp))
                    }
                }
                Tool(if (recording) "Finish" else if (busy) "Cancel" else "Dictate", enabled = true,
                    action = { DictateController.onMicClick(context) }) {
                    Icon(if (busy) Icons.Outlined.Stop else PersonalIcons.Mic, null, Modifier.size(23.dp))
                }
                Tool("More keyboard tools", enabled = true, action = { menuOpen = true }) {
                    Icon(Icons.Default.MoreHoriz, null, Modifier.size(21.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Hinglish · English letters") }, enabled = !busy,
                    onClick = { menuOpen = false; DictateController.setLanguage(DictateLanguages.HINGLISH) })
                DropdownMenuItem(text = { Text("English dictation") }, enabled = !busy,
                    onClick = { menuOpen = false; DictateController.setLanguage("en") })
                DropdownMenuItem(text = { Text("Select & edit text") }, enabled = !busy,
                    onClick = { menuOpen = false; keyboard.activeState.imeUiMode = ImeUiMode.EDITING })
                DropdownMenuItem(text = { Text("Dictation history") }, enabled = !busy,
                    onClick = { menuOpen = false; keyboard.activeState.imeUiMode = ImeUiMode.HISTORY })
                DropdownMenuItem(text = { Text("Keyboard settings") },
                    onClick = { menuOpen = false; FlorisImeService.launchSettings() })
            }
            Box(Modifier.fillMaxWidth().height(FlorisImeSizing.smartbarHeight),
                contentAlignment = Alignment.Center) {
                when {
                    state !is DictateController.UiState.Idle -> DictateSmartbarUi(state)
                    AndroidVersion.ATLEAST_API30_R && inline.isNotEmpty() -> InlineSuggestionsUi(inline)
                    candidates.isNotEmpty() -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(PersonalIcons.Chevron, "Keyboard tools", Modifier.size(21.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) { CandidatesRow() }
                    }
                    else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(PersonalIcons.Chevron, "Keyboard tools", Modifier.size(21.dp))
                        }
                        if (keyboardState.keyVariation != KeyVariation.PASSWORD) {
                            listOf("I", "The", "You").forEach { word ->
                                Box(Modifier.weight(1f).fillMaxHeight().clickable { editor.commitText("$word ") },
                                    contentAlignment = Alignment.Center) {
                                    Text(word, fontSize = 25.sp, color = colors.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
