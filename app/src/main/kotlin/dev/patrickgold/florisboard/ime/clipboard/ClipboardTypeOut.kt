package dev.patrickgold.florisboard.ime.clipboard

/**
 * Feed text through the editor as individual grapheme clusters (visible characters), not a paste
 * action. Keep Hindi vowel marks, surrogate pairs and joined emoji together. Never retry a rejected
 * chunk: the field may already contain a prefix, so retrying the whole clip would duplicate it.
 */
internal suspend fun typeClipboardText(
    text: String,
    isCurrentEditor: () -> Boolean,
    commit: (String) -> Boolean,
    pause: suspend () -> Unit,
): Boolean {
    for (character in Regex("\\X").findAll(text)) {
        if (!isCurrentEditor() || !commit(character.value)) return false
        pause()
    }
    return true
}
