package dev.pocketdev.core.terminal

import java.nio.charset.StandardCharsets

data class TerminalCell(
    val char: Char = ' ',
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val cursor: Boolean = false,
)

data class TerminalLine(val cells: List<TerminalCell>) {
    fun text(): String = cells.joinToString("") { it.char }.trimEnd()
}

data class TerminalSnapshot(
    val lines: List<TerminalLine>,
    val cols: Int,
    val rows: Int,
)

class AnsiBuffer(initialCols: Int = 80, initialRows: Int = 24) {

    private var cols = initialCols.coerceAtLeast(1)
    private var rows = initialRows.coerceAtLeast(1)
    private val lines = MutableList(rows) { blankLine() }
    private val scrollback = ArrayDeque<TerminalLine>()

    private var cursorRow = 0
    private var cursorCol = 0
    private var state = State.GROUND
    private val csi = StringBuilder()

    private var foreground: Int? = null
    private var background: Int? = null
    private var bold = false

    @Synchronized
    fun feed(bytes: ByteArray) {
        val text = String(bytes, StandardCharsets.UTF_8)
        for (char in text) consume(char)
    }

    @Synchronized
    fun resize(newCols: Int, newRows: Int) {
        cols = newCols.coerceAtLeast(1)
        rows = newRows.coerceAtLeast(1)

        for (index in lines.indices) {
            val cells = lines[index].cells.toMutableList()
            while (cells.size < cols) cells.add(TerminalCell())
            if (cells.size > cols) cells.subList(cols, cells.size).clear()
            lines[index] = TerminalLine(cells)
        }

        while (lines.size < rows) lines.add(blankLine())
        while (lines.size > rows) scrollback.addLast(lines.removeFirst())
        trimScrollback()

        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    @Synchronized
    fun snapshot(): TerminalSnapshot {
        val all = scrollback.toList() + lines
        val absoluteRow = scrollback.size + cursorRow
        if (absoluteRow in all.indices) {
            val cells = all[absoluteRow].cells.toMutableList()
            while (cells.size <= cursorCol) cells.add(TerminalCell())
            cells[cursorCol] = cells[cursorCol].copy(cursor = true)
            all[absoluteRow] = TerminalLine(cells)
        }
        return TerminalSnapshot(all, cols, rows)
    }

    @Synchronized
    fun plainText(): String = (scrollback.toList() + lines).joinToString("\n") { it.text() }

    private fun consume(char: Char) {
        when (state) {
            State.GROUND -> ground(char)
            State.ESCAPE -> escape(char)
            State.CSI -> controlSequence(char)
            State.OSC -> operatingSystemCommand(char)
            State.OSC_ESCAPE -> {
                state = if (char == '\\') State.GROUND else State.OSC
            }
        }
    }

    private fun ground(char: Char) {
        when (char) {
            '\u001B' -> state = State.ESCAPE
            '\r' -> cursorCol = 0
            '\n' -> newline()
            '\b' -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
            '\t' -> cursorCol = minOf(cols - 1, ((cursorCol / 8) + 1) * 8)
            else -> {
                if (char < ' ' && char != '\u0007') return
                if (cursorCol >= cols) newline()
                writeCell(char)
            }
        }
    }

    private fun escape(char: Char) {
        when (char) {
            '[' -> {
                csi.setLength(0)
                state = State.CSI
            }
            ']' -> state = State.OSC
            else -> state = State.GROUND
        }
    }

    private fun operatingSystemCommand(char: Char) {
        when (char) {
            '\u0007' -> state = State.GROUND
            '\u001B' -> state = State.OSC_ESCAPE
        }
    }

    private fun controlSequence(char: Char) {
        if (char in '0'..'9' || char == ';') {
            csi.append(char)
            return
        }
        dispatchCsi(char)
        csi.setLength(0)
        state = State.GROUND
    }

    private fun dispatchCsi(final: Char) {
        val raw = csi.toString().split(';')
        fun arg(index: Int, default: Int): Int = raw.getOrNull(index)?.toIntOrNull() ?: default

        when (final) {
            'A' -> cursorRow = (cursorRow - arg(0, 1).coerceAtLeast(1)).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + arg(0, 1).coerceAtLeast(1)).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + arg(0, 1).coerceAtLeast(1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - arg(0, 1).coerceAtLeast(1)).coerceAtLeast(0)
            'H', 'f' -> {
                cursorRow = (arg(0, 1) - 1).coerceAtLeast(0).coerceAtMost(rows - 1)
                cursorCol = (arg(1, 1) - 1).coerceAtLeast(0).coerceAtMost(cols - 1)
            }
            'J' -> eraseDisplay(arg(0, 0))
            'K' -> eraseLine(arg(0, 0))
            'm' -> applySgr(raw)
        }
    }

    private fun applySgr(raw: List<String>) {
        if (raw.all { it.isBlank() }) {
            foreground = null
            background = null
            bold = false
            return
        }
        for (entry in raw) {
            when (val code = entry.toIntOrNull() ?: 0) {
                0 -> {
                    foreground = null
                    background = null
                    bold = false
                }
                1 -> bold = true
                22 -> bold = false
                39 -> foreground = null
                49 -> background = null
                in 30..37 -> foreground = ANSI_COLORS[code - 30]
                in 90..97 -> foreground = ANSI_COLORS[code - 90 + 8]
                in 40..47 -> background = ANSI_COLORS[code - 40]
                in 100..107 -> background = ANSI_COLORS[code - 100 + 8]
            }
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                for (column in cursorCol until cols) replaceCell(cursorRow, column)
                for (row in cursorRow + 1 until rows) lines[row] = blankLine()
            }
            1 -> {
                for (row in 0 until cursorRow) lines[row] = blankLine()
                for (column in 0..cursorCol.coerceAtMost(cols - 1)) replaceCell(cursorRow, column)
            }
            2 -> for (row in 0 until rows) lines[row] = blankLine()
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (column in cursorCol until cols) replaceCell(cursorRow, column)
            1 -> for (column in 0..cursorCol.coerceAtMost(cols - 1)) replaceCell(cursorRow, column)
            2 -> lines[cursorRow] = blankLine()
        }
    }

    private fun writeCell(char: Char) {
        val cells = lines[cursorRow].cells.toMutableList()
        while (cells.size <= cursorCol) cells.add(TerminalCell())
        cells[cursorCol] = TerminalCell(char, foreground, background, bold)
        lines[cursorRow] = TerminalLine(cells)
        cursorCol += 1
    }

    private fun replaceCell(row: Int, column: Int) {
        val cells = lines[row].cells.toMutableList()
        while (cells.size <= column) cells.add(TerminalCell())
        cells[column] = TerminalCell()
        lines[row] = TerminalLine(cells)
    }

    private fun newline() {
        if (cursorRow == rows - 1) {
            scrollback.addLast(lines.removeFirst())
            lines.add(blankLine())
            trimScrollback()
        } else {
            cursorRow += 1
        }
    }

    private fun blankLine(): TerminalLine = TerminalLine(List(cols) { TerminalCell() })

    private fun trimScrollback() {
        while (scrollback.size > MAX_SCROLLBACK) scrollback.removeFirst()
    }

    private enum class State {
        GROUND,
        ESCAPE,
        CSI,
        OSC,
        OSC_ESCAPE,
    }

    private companion object {
        const val MAX_SCROLLBACK = 1000
        val ANSI_COLORS = intArrayOf(
            0xFF1C1C1C.toInt(),
            0xFFCD3131.toInt(),
            0xFF0DBC79.toInt(),
            0xFFE5E510.toInt(),
            0xFF2472C8.toInt(),
            0xFFBC3FBC.toInt(),
            0xFF11A8CD.toInt(),
            0xFFE5E5E5.toInt(),
            0xFF666666.toInt(),
            0xFFF14C4C.toInt(),
            0xFF23D18B.toInt(),
            0xFFF5F543.toInt(),
            0xFF3B8EEA.toInt(),
            0xFFD670D6.toInt(),
            0xFF29B8DB.toInt(),
            0xFFFFFFFF.toInt(),
        )
    }
}
