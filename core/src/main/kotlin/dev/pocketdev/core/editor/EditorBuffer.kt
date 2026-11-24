package dev.pocketdev.core.editor

data class TextChange(
    val start: Int,
    val end: Int,
    val oldText: String,
    val newText: String,
)

class EditorBuffer(initialText: String) {
    private var value: String = initialText
    private var savedValue: String = initialText
    private var lineStarts: List<Int> = TextLines.starts(initialText)
    private val undoStack = ArrayDeque<TextChange>()
    private val redoStack = ArrayDeque<TextChange>()

    val text: String get() = value
    val lineStarts: List<Int> get() = lineStarts
    val dirty: Boolean get() = value != savedValue
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun replace(start: Int, end: Int, replacement: String): Boolean {
        if (start < 0 || end < start || end > value.length) return false
        val oldText = value.substring(start, end)
        if (oldText == replacement) return false
        val change = TextChange(start, end, oldText, replacement)
        applyChange(change)
        undoStack.addLast(change)
        redoStack.clear()
        return true
    }

    fun replaceRange(range: IntRange, replacement: String): Boolean =
        replace(range.first, range.last + 1, replacement)

    fun undo(): Boolean {
        val change = undoStack.removeLastOrNull() ?: return false
        applyChange(
            TextChange(
                start = change.start,
                end = change.start + change.newText.length,
                oldText = change.newText,
                newText = change.oldText,
            ),
        )
        redoStack.addLast(change)
        return true
    }

    fun redo(): Boolean {
        val change = redoStack.removeLastOrNull() ?: return false
        applyChange(change)
        undoStack.addLast(change)
        return true
    }

    fun markSaved() {
        savedValue = value
    }

    fun replaceAllText(replacement: String): Boolean = replace(0, value.length, replacement)

    fun lineText(line: Int): String {
        if (line < 0 || line >= lineStarts.size) return ""
        val start = lineStarts[line]
        val end = if (line + 1 < lineStarts.size) lineStarts[line + 1] else value.length
        return value.substring(start, end).trimEnd('\n')
    }

    fun lineAndColumn(offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, value.length)
        val line = TextLines.lineNumberAt(lineStarts, safeOffset)
        val start = lineStarts[line]
        return line + 1 to safeOffset - start + 1
    }

    private fun applyChange(change: TextChange) {
        value = buildString {
            append(value, 0, change.start)
            append(change.newText)
            append(value, change.end, value.length)
        }
        lineStarts = TextLines.starts(value)
    }
}