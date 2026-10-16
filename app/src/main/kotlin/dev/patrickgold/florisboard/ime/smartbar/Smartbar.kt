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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
val PersonalToolbarHeight = 52.dp

/** The core controls never collapse, change order, or disappear behind suggestions. */
@Composable
fun Smartbar() {
    PersonalKeyboardTheme {
        val prefs by FlorisPreferenceStore
        val context = LocalContext.current
        val keyboard by context.keyboardManager()
        val nlp by context.nlpManager()
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
            Row(
                Modifier.fillMaxWidth().height(PersonalToolbarHeight).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreHoriz, "More keyboard tools", Modifier.size(24.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf(
                            "Emoji" to ImeUiMode.MEDIA,
                            "Select & edit text" to ImeUiMode.EDITING,
                            "Dictation history" to ImeUiMode.HISTORY,
                        ).forEach { (label, mode) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                menuOpen = false
                                keyboard.activeState.imeUiMode = mode
                            }, enabled = !busy)
                        }
                        DropdownMenuItem(text = { Text("Keyboard settings") }, onClick = {
                            menuOpen = false
                            FlorisImeService.launchSettings()
                        })
                    }
                }
                IconButton(
                    onClick = { keyboard.activeState.imeUiMode = ImeUiMode.CLIPBOARD },
                    enabled = !busy, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface),
                ) { Icon(Icons.Default.ContentPaste, "Open clipboard", Modifier.size(22.dp)) }
                Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 20.dp)
                    .background(colors.outlineVariant))
                Row(Modifier.selectableGroup()) {
                    listOf(DictateLanguages.HINGLISH to "HING", "en" to "EN").forEach { (code, label) ->
                        val selected = language == code
                        Box(
                            Modifier.size(width = 52.dp, height = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(selected, enabled = !busy, role = Role.RadioButton) {
                                    DictateController.setLanguage(code)
                                }.semantics {
                                    contentDescription = if (code == DictateLanguages.HINGLISH)
                                        "Hinglish, English letters" else "English dictation"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(width = 46.dp, height = 30.dp)
                                .background(if (selected) colors.surfaceContainerHigh else colors.surface,
                                    RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = colors.onSurface.copy(alpha = if (busy) 0.45f else if (selected) 1f else 0.70f))
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = { DictateController.onMicClick(context) }, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer),
                ) {
                    Icon(if (busy) Icons.Default.Stop else Icons.Default.Mic,
                        if (recording) "Finish" else if (busy) "Cancel" else "Dictate", Modifier.size(24.dp))
                }
            }
            if (state !is DictateController.UiState.Idle || inline.isNotEmpty() || candidates.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(FlorisImeSizing.smartbarHeight),
                contentAlignment = Alignment.Center) {
                when {
                    state !is DictateController.UiState.Idle -> DictateSmartbarUi(state)
                    AndroidVersion.ATLEAST_API30_R && inline.isNotEmpty() -> InlineSuggestionsUi(inline)
                    candidates.isNotEmpty() -> CandidatesRow()
                    else -> Unit
                }
            }
            }
        }
    }
}
