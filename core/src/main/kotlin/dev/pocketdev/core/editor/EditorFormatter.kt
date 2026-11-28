package dev.pocketdev.core.editor

object EditorFormatter {
    private val FORMATTED_LANGUAGES = setOf(
        EditorLanguage.KOTLIN,
        EditorLanguage.JAVA,
        EditorLanguage.JAVASCRIPT,
        EditorLanguage.TYPESCRIPT,
        EditorLanguage.PYTHON,
    )

    private enum class LexicalState {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        TRIPLE_STRING,
    }

    private data class ScanState(
        val state: LexicalState = LexicalState.NORMAL,
        val quote: Char = '"',
        val escaped: Boolean = false,
    )

    fun supportsFormatting(language: EditorLanguage): Boolean = language in FORMATTED_LANGUAGES

    fun format(text: String, language: EditorLanguage): String {
        if (!supportsFormatting(language)) return text
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isEmpty()) return ""
        val hadTrailingNewline = normalized.endsWith("\n")
        val body = if (hadTrailingNewline) normalized.dropLast(1) else normalized
        var state = ScanState()
        val lines = body.split('\n').map { line ->
            val trimmed = line.trimStart()
            val isLineComment = trimmed.startsWith("//") ||
                (language == EditorLanguage.PYTHON && trimmed.startsWith("#"))
            val transformed = if (state.state == LexicalState.NORMAL && !isLineComment) {
                val originalIndent = line.takeWhile { it == ' ' || it == '\t' }
                val bodyWithoutIndent = line.substring(originalIndent.length).trimEnd()
                originalIndent.replace("\t", "    ") + bodyWithoutIndent
            } else {
                line
            }
            state = scanLine(line, state, language)
            transformed
        }
        val result = lines.joinToString("\n")
        return if (hadTrailingNewline) "$result\n" else result
    }

    private fun scanLine(line: String, initial: ScanState, language: EditorLanguage): ScanState {
        var state = initial.state
        var quote = initial.quote
        var escaped = initial.escaped
        var index = 0
        while (index < line.length) {
            val char = line[index]
            val next = line.getOrNull(index + 1)
            when (state) {
                LexicalState.NORMAL -> when {
                    char == '/' && next == '/' -> {
                        state = LexicalState.LINE_COMMENT
                        index += 2
                    }
                    char == '/' && next == '*' -> {
                        state = LexicalState.BLOCK_COMMENT
                        index += 2
                    }
                    language == EditorLanguage.PYTHON && char == '#' -> {
                        state = LexicalState.LINE_COMMENT
                        index++
                    }
                    isTripleQuoteStart(line, index, language) -> {
                        quote = char
                        state = LexicalState.TRIPLE_STRING
                        index += 3
                    }
                    char == '"' || char == '\'' || char == '`' -> {
                        quote = char
                        state = LexicalState.STRING
                        index++
                    }
                    else -> index++
                }
                LexicalState.LINE_COMMENT -> break
                LexicalState.BLOCK_COMMENT -> {
                    if (char == '*' && next == '/') {
                        state = LexicalState.NORMAL
                        index += 2
                    } else {
                        index++
                    }
                }
                LexicalState.STRING -> when {
                    escaped -> {
                        escaped = false
                        index++
                    }
                    char == '\\' -> {
                        escaped = true
                        index++
                    }
                    char == quote -> {
                        state = LexicalState.NORMAL
                        index++
                    }
                    else -> index++
                }
                LexicalState.TRIPLE_STRING -> when {
                    language != EditorLanguage.KOTLIN && char == '\\' -> index += 2
                    char == quote && line.getOrNull(index + 1) == quote &&
                        line.getOrNull(index + 2) == quote -> {
                        state = LexicalState.NORMAL
                        index += 3
                    }
                    else -> index++
                }
            }
        }
        return when (state) {
            LexicalState.LINE_COMMENT -> ScanState()
            else -> ScanState(state, quote, escaped)
        }
    }

    private fun isTripleQuoteStart(
        line: String,
        index: Int,
        language: EditorLanguage,
    ): Boolean {
        val second = line.getOrNull(index + 1) ?: return false
        val third = line.getOrNull(index + 2) ?: return false
        if (second != line[index] || third != line[index]) return false
        return when (language) {
            EditorLanguage.KOTLIN -> line[index] == '"'
            EditorLanguage.PYTHON -> line[index] == '"' || line[index] == '\''
            else -> false
        }
    }
}
