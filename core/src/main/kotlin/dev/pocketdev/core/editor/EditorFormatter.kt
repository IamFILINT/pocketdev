package dev.pocketdev.core.editor

object EditorFormatter {
    private val INDENTED_LANGUAGES = setOf(
        EditorLanguage.KOTLIN,
        EditorLanguage.JAVA,
        EditorLanguage.JAVASCRIPT,
        EditorLanguage.TYPESCRIPT,
        EditorLanguage.PYTHON,
    )

    fun format(text: String, language: EditorLanguage): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines().map { line ->
            val expanded = if (language in INDENTED_LANGUAGES) line.replace("\t", "    ") else line
            expanded.trimEnd()
        }
        return if (lines.isEmpty()) {
            ""
        } else {
            lines.joinToString("\n").trimEnd() + "\n"
        }
    }
}