package dev.patrickgold.florisboard.ime.keyboard

/** Long-press symbols observed on the user's SwiftKey QWERTY layout. */
object PersonalKeyLayout {
    private val hints = ("qwertyuiop".zip("%^~|[]<>{}") +
        "asdfghjkl".zip("@#&*-+=()") + "zxcvbnm".zip("_₹\"':;/")).toMap() + ('.' to '?')

    fun hintFor(code: Int): Char? = hints[code.toChar().lowercaseChar()]
}
