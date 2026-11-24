package dev.pocketdev.core.editor

enum class SyntaxTokenType {
    PLAIN,
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    IDENTIFIER,
    PUNCTUATION,
    ANNOTATION,
}

data class SyntaxToken(
    val start: Int,
    val end: Int,
    val type: SyntaxTokenType,
)

object SyntaxHighlighter {
    fun tokenize(text: String, language: EditorLanguage): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var index = 0
        while (index < text.length) {
            val char = text[index]

            val blockComment = blockCommentEnd(language, text, index)
            if (blockComment != null) {
                val end = text.indexOf(blockComment.second, index + blockComment.first.length)
                val stop = if (end < 0) text.length else end + blockComment.second.length
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.COMMENT))
                index = stop
                continue
            }

            if (lineCommentAt(language, text, index)) {
                val stop = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.COMMENT))
                index = stop
                continue
            }

            if (language == EditorLanguage.MARKDOWN && char == '#' &&
                (index == 0 || text[index - 1] == '\n')
            ) {
                val stop = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.KEYWORD))
                index = stop
                continue
            }

            val tripleQuote = tripleQuote(text, index)
            if (tripleQuote != null) {
                val end = text.indexOf(tripleQuote, index + 3)
                val stop = if (end < 0) text.length else end + 3
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.STRING))
                index = stop
                continue
            }

            if (char == '"' || char == '\'' || char == '`') {
                val stop = consumeQuoted(text, index, char)
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.STRING))
                index = stop
                continue
            }

            if (char.isDigit()) {
                var stop = index + 1
                while (stop < text.length && (text[stop].isLetterOrDigit() || text[stop] == '.')) {
                    stop++
                }
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.NUMBER))
                index = stop
                continue
            }

            if (char.isLetter() || char == '_') {
                var stop = index + 1
                while (stop < text.length && (text[stop].isLetterOrDigit() || text[stop] == '_')) {
                    stop++
                }
                val word = text.substring(index, stop)
                val type = when {
                    isKeyword(language, word) -> SyntaxTokenType.KEYWORD
                    isTypeName(language, word) -> SyntaxTokenType.IDENTIFIER
                    else -> SyntaxTokenType.IDENTIFIER
                }
                tokens.add(SyntaxToken(index, stop, type))
                index = stop
                continue
            }

            if (char == '@' && language in setOf(
                    EditorLanguage.KOTLIN,
                    EditorLanguage.JAVA,
                    EditorLanguage.TYPESCRIPT,
                )
            ) {
                var stop = index + 1
                while (stop < text.length && (text[stop].isLetterOrDigit() || text[stop] == '_')) {
                    stop++
                }
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.ANNOTATION))
                index = stop
                continue
            }

            if (char.isWhitespace()) {
                var stop = index + 1
                while (stop < text.length && text[stop].isWhitespace()) stop++
                tokens.add(SyntaxToken(index, stop, SyntaxTokenType.PLAIN))
                index = stop
                continue
            }

            tokens.add(SyntaxToken(index, index + 1, SyntaxTokenType.PUNCTUATION))
            index++
        }
        return tokens
    }

    private fun lineCommentAt(language: EditorLanguage, text: String, index: Int): Boolean =
        when (language) {
            EditorLanguage.KOTLIN,
            EditorLanguage.JAVA,
            EditorLanguage.JAVASCRIPT,
            EditorLanguage.TYPESCRIPT,
            -> text.startsWith("//", index)

            EditorLanguage.PYTHON,
            EditorLanguage.SHELL,
            EditorLanguage.YAML,
            -> text[index] == '#'

            else -> false
        }

    private fun blockCommentEnd(
        language: EditorLanguage,
        text: String,
        index: Int,
    ): Pair<String, String>? =
        when (language) {
            EditorLanguage.KOTLIN,
            EditorLanguage.JAVA,
            EditorLanguage.JAVASCRIPT,
            EditorLanguage.TYPESCRIPT,
            -> if (text.startsWith("/*", index)) "/*" to "*/" else null

            EditorLanguage.XML -> if (text.startsWith("<!--", index)) "<!--" to "-->" else null
            else -> null
        }

    private fun tripleQuote(text: String, index: Int): String? =
        if (text.startsWith("\"\"\"", index)) {
            "\"\"\""
        } else if (text.startsWith("'''", index)) {
            "'''"
        } else {
            null
        }

    private fun consumeQuoted(text: String, index: Int, quote: Char): Int {
        var cursor = index + 1
        while (cursor < text.length) {
            if (text[cursor] == '\\') {
                cursor += 2
                continue
            }
            if (text[cursor] == quote) return cursor + 1
            if (text[cursor] == '\n') return cursor
            cursor++
        }
        return text.length
    }

    private fun isKeyword(language: EditorLanguage, word: String): Boolean =
        when (language) {
            EditorLanguage.KOTLIN -> word in KOTLIN_KEYWORDS
            EditorLanguage.JAVA -> word in JAVA_KEYWORDS
            EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT -> word in JAVASCRIPT_KEYWORDS
            EditorLanguage.PYTHON -> word in PYTHON_KEYWORDS
            EditorLanguage.JSON -> word in setOf("true", "false", "null")
            EditorLanguage.SHELL -> word in SHELL_KEYWORDS
            EditorLanguage.YAML -> word in setOf("true", "false", "null")
            else -> false
        }

    private fun isTypeName(language: EditorLanguage, word: String): Boolean =
        language in setOf(EditorLanguage.KOTLIN, EditorLanguage.JAVA, EditorLanguage.TYPESCRIPT) &&
            word.firstOrNull()?.isUpperCase() == true

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "val", "var", "when", "while", "import",
        "override", "private", "public", "internal", "suspend", "data", "sealed",
    )
    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "false",
        "final", "finally", "float", "for", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "null", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "true", "try", "void",
        "volatile", "while",
    )
    private val JAVASCRIPT_KEYWORDS = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", "default",
        "delete", "do", "else", "export", "extends", "false", "finally", "for", "from",
        "function", "if", "import", "in", "instanceof", "interface", "let", "new", "null",
        "of", "return", "static", "super", "switch", "this", "throw", "true", "try",
        "typeof", "undefined", "var", "void", "while", "yield",
    )
    private val PYTHON_KEYWORDS = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "False", "finally", "for", "from", "global", "if",
        "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise",
        "return", "True", "try", "while", "with", "yield",
    )
    private val SHELL_KEYWORDS = setOf(
        "case", "do", "done", "else", "elif", "esac", "fi", "for", "function", "if", "in",
        "local", "then", "while", "export",
    )
}