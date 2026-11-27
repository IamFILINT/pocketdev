package dev.pocketdev.core.json

/**
 * Minimal dependency-free JSON reader for the small OpenCode responses PocketDev
 * consumes. Values are represented as `Map<String, Any?>`, `List<Any?>`, `String`,
 * `Long`, `Double`, `Boolean` or `null`.
 *
 * Deliberately not a general JSON library: it exists so `:core` stays pure Kotlin
 * with no serialization plugin, and so the same reader can later parse SSE event
 * payloads. Malformed input fails with a descriptive [JsonException].
 */
object Json {

    class JsonException(message: String) : RuntimeException(message)

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("trailing content at offset ${parser.offset}")
        return value
    }

    /** Parses an object, throwing when the document is not a JSON object. */
    fun parseObject(text: String): Map<String, Any?> = when (val value = parse(text)) {
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        null -> throw JsonException("expected a JSON object, got null")
        else -> throw JsonException("expected a JSON object, got ${value::class.simpleName}")
    }

    fun parseArray(text: String): List<Any?> = when (val value = parse(text)) {
        is List<*> -> value.toList()
        null -> throw JsonException("expected a JSON array, got null")
        else -> throw JsonException("expected a JSON array, got ${value::class.simpleName}")
    }

    private class Parser(private val text: String) {
        var offset: Int = 0

        fun atEnd(): Boolean = offset >= text.length

        fun skipWhitespace() {
            while (offset < text.length && text[offset].isWhitespace()) offset++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw JsonException("unexpected end of input")
            return when (val c = text[offset]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                offset++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> offset++
                    '}' -> {
                        offset++
                        return result
                    }
                    else -> throw JsonException("expected ',' or '}' at offset $offset")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                offset++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> offset++
                    ']' -> {
                        offset++
                        return result
                    }
                    else -> throw JsonException("expected ',' or ']' at offset $offset")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (offset < text.length) {
                val c = text[offset++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (offset >= text.length) break
                        when (val esc = text[offset++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append("\u000C")
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (offset + 4 > text.length) throw JsonException("truncated unicode escape")
                                val hex = text.substring(offset, offset + 4)
                                offset += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonException("invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw JsonException("unterminated string")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!text.startsWith(literal, offset)) {
                throw JsonException("invalid literal at offset $offset")
            }
            offset += literal.length
            return value
        }

        private fun parseNumber(): Any {
            val start = offset
            if (peek() == '-') offset++
            var isDouble = false
            while (offset < text.length) {
                val c = text[offset]
                when {
                    c.isDigit() -> offset++
                    c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-' -> {
                        isDouble = true
                        offset++
                    }
                    else -> break
                }
            }
            if (start == offset) throw JsonException("invalid value at offset $start")
            val raw = text.substring(start, offset)
            return if (isDouble) raw.toDouble() else raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun peek(): Char {
            if (atEnd()) throw JsonException("unexpected end of input")
            return text[offset]
        }

        private fun expect(c: Char) {
            if (atEnd() || text[offset] != c) {
                throw JsonException("expected '$c' at offset $offset")
            }
            offset++
        }
    }

}

// -- Accessor helpers -------------------------------------------------

fun Any?.asObject(): Map<String, Any?>? =
    (this as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }

fun Any?.asString(): String? = this as? String

fun Any?.asLong(): Long? = (this as? Number)?.toLong()

fun Any?.asInt(): Int? = (this as? Number)?.toInt()

fun Any?.asBoolean(): Boolean? = this as? Boolean

fun Any?.asArray(): List<Any?>? = this as? List<Any?>

fun Any?.field(name: String): Any? = (this as? Map<*, *>)?.get(name)
