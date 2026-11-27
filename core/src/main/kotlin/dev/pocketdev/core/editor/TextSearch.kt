package dev.pocketdev.core.editor

data class TextMatch(
    val start: Int,
    val end: Int,
)

object TextSearch {
    fun findAll(
        text: String,
        query: String,
        ignoreCase: Boolean = false,
        wholeWord: Boolean = false,
    ): List<TextMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<TextMatch>()
        var index = 0
        while (index <= text.length - query.length) {
            if (matchesAt(text, query, index, ignoreCase, wholeWord)) {
                matches.add(TextMatch(index, index + query.length))
                index += query.length
            } else {
                index++
            }
        }
        return matches
    }

    fun findNext(
        text: String,
        query: String,
        fromOffset: Int,
        ignoreCase: Boolean = false,
        wholeWord: Boolean = false,
    ): TextMatch? {
        if (query.isEmpty()) return null
        val start = fromOffset.coerceIn(0, text.length)
        for (match in findAll(text, query, ignoreCase, wholeWord)) {
            if (match.start >= start) return match
        }
        return findAll(text, query, ignoreCase, wholeWord).firstOrNull()
    }

    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        ignoreCase: Boolean = false,
        wholeWord: Boolean = false,
    ): Pair<String, Int> {
        val matches = findAll(text, query, ignoreCase, wholeWord)
        if (matches.isEmpty()) return text to 0
        val result = StringBuilder()
        var cursor = 0
        for (match in matches) {
            result.append(text, cursor, match.start)
            result.append(replacement)
            cursor = match.end
        }
        result.append(text, cursor, text.length)
        return result.toString() to matches.size
    }

    private fun matchesAt(
        text: String,
        query: String,
        index: Int,
        ignoreCase: Boolean,
        wholeWord: Boolean,
    ): Boolean {
        if (!text.regionMatches(index, query, 0, query.length, ignoreCase)) return false
        if (!wholeWord) return true
        val before = if (index == 0) null else text[index - 1]
        val after = if (index + query.length >= text.length) null else text[index + query.length]
        return !isWord(before) && !isWord(after)
    }

    private fun isWord(char: Char?): Boolean = char != null && (char.isLetterOrDigit() || char == '_')
}