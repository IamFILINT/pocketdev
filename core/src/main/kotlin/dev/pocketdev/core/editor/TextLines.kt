package dev.pocketdev.core.editor

object TextLines {
    fun starts(text: String): List<Int> {
        val starts = mutableListOf(0)
        text.forEachIndexed { index, char ->
            if (char == '\n') starts.add(index + 1)
        }
        return starts
    }

    fun lineNumberAt(starts: List<Int>, offset: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val start = starts[middle]
            if (start <= offset) {
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return (high + 1).coerceAtMost(starts.lastIndex)
    }
}