package dev.pocketdev.core.editor

enum class EditorLanguage(val label: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    PYTHON("Python"),
    JSON("JSON"),
    XML("XML"),
    YAML("YAML"),
    SHELL("Shell"),
    MARKDOWN("Markdown"),
    PLAIN_TEXT("Plain text");

    companion object {
        fun fromPath(path: String): EditorLanguage {
            val extension = path.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "js", "jsx", "mjs", "cjs" -> JAVASCRIPT
                "ts", "tsx" -> TYPESCRIPT
                "py" -> PYTHON
                "json" -> JSON
                "xml", "html", "css" -> XML
                "yml", "yaml" -> YAML
                "sh", "bash", "zsh" -> SHELL
                "md", "markdown" -> MARKDOWN
                else -> PLAIN_TEXT
            }
        }
    }
}