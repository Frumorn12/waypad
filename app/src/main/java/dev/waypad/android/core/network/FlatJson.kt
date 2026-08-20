package dev.waypad.android.core.network

/**
 * Minimal allocation-light JSON reader for the flat objects used by the screen stream wire
 * protocol.
 *
 * `org.json` is only a stub on the JVM unit-test classpath, so parsing the per-frame header with
 * it would make the protocol logic untestable without an emulator. The header is a flat object
 * with scalar values, so a hand written scanner is both testable and cheaper at 60 fps.
 */
internal object FlatJson {

    fun parseObject(text: String): Map<String, Any?> {
        val cursor = Cursor(text)
        cursor.skipWhitespace()
        cursor.expect('{')
        val result = LinkedHashMap<String, Any?>()
        cursor.skipWhitespace()
        if (cursor.peek() == '}') {
            cursor.advance()
            return result
        }
        while (true) {
            cursor.skipWhitespace()
            val key = cursor.readString()
            cursor.skipWhitespace()
            cursor.expect(':')
            cursor.skipWhitespace()
            result[key] = cursor.readValue()
            cursor.skipWhitespace()
            when (val separator = cursor.read()) {
                ',' -> Unit
                '}' -> return result
                else -> throw JsonFormatException("Unexpected '$separator' after value of '$key'")
            }
        }
    }

    fun escape(value: String): String {
        val builder = StringBuilder(value.length + 8)
        for (char in value) {
            when {
                char == '"' -> builder.append("\\\"")
                char == '\\' -> builder.append("\\\\")
                char == '\n' -> builder.append("\\n")
                char == '\r' -> builder.append("\\r")
                char == '\t' -> builder.append("\\t")
                char < ' ' -> builder.append("\\u").append("%04x".format(char.code))
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    private class Cursor(private val text: String) {
        private var index = 0

        fun peek(): Char =
            if (index < text.length) text[index] else throw JsonFormatException("Unexpected end of JSON")

        fun read(): Char = peek().also { index++ }

        fun advance() {
            index++
        }

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun expect(expected: Char) {
            val actual = read()
            if (actual != expected) {
                throw JsonFormatException("Expected '$expected' but found '$actual' at offset ${index - 1}")
            }
        }

        fun readValue(): Any? = when (peek()) {
            '"' -> readString()
            '{', '[' -> {
                skipContainer()
                null
            }
            't' -> {
                expectLiteral("true")
                true
            }
            'f' -> {
                expectLiteral("false")
                false
            }
            'n' -> {
                expectLiteral("null")
                null
            }
            else -> readNumber()
        }

        fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                when (val char = read()) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscape())
                    else -> builder.append(char)
                }
            }
        }

        private fun readEscape(): Char = when (val marker = read()) {
            '"', '\\', '/' -> marker
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > text.length) throw JsonFormatException("Truncated \\u escape")
                val code = text.substring(index, index + 4).toIntOrNull(16)
                    ?: throw JsonFormatException("Invalid \\u escape")
                index += 4
                code.toChar()
            }
            else -> throw JsonFormatException("Invalid escape '\\$marker'")
        }

        private fun readNumber(): Any {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] in NUMBER_CHARS)) index++
            val raw = text.substring(start, index)
            if (raw.isEmpty()) throw JsonFormatException("Expected a value at offset $start")
            return if (raw.any { it == '.' || it == 'e' || it == 'E' }) {
                raw.toDoubleOrNull() ?: throw JsonFormatException("Invalid number '$raw'")
            } else {
                raw.toLongOrNull() ?: throw JsonFormatException("Invalid number '$raw'")
            }
        }

        private fun expectLiteral(literal: String) {
            if (index + literal.length > text.length || text.regionMatches(index, literal, 0, literal.length).not()) {
                throw JsonFormatException("Expected literal '$literal' at offset $index")
            }
            index += literal.length
        }

        /** Consumes a nested object/array without materialising it; the header never uses them. */
        private fun skipContainer() {
            var depth = 0
            while (true) {
                when (val char = read()) {
                    '"' -> skipStringBody()
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) return
                    }
                }
            }
        }

        private fun skipStringBody() {
            while (true) {
                when (read()) {
                    '\\' -> read()
                    '"' -> return
                }
            }
        }

        private companion object {
            const val NUMBER_CHARS = "-+.eE"
        }
    }
}

class JsonFormatException(message: String) : IllegalArgumentException(message)

internal fun Map<String, Any?>.longValue(key: String, fallback: Long): Long = when (val value = this[key]) {
    is Long -> value
    is Double -> value.toLong()
    is Boolean -> if (value) 1L else 0L
    is String -> value.toLongOrNull() ?: fallback
    else -> fallback
}

internal fun Map<String, Any?>.intValue(key: String, fallback: Int): Int =
    longValue(key, fallback.toLong()).toInt()

internal fun Map<String, Any?>.booleanValue(key: String, fallback: Boolean): Boolean = when (val value = this[key]) {
    is Boolean -> value
    is Long -> value != 0L
    is Double -> value != 0.0
    is String -> value.equals("true", ignoreCase = true) || value == "1"
    else -> fallback
}

internal fun Map<String, Any?>.stringValue(key: String, fallback: String): String = when (val value = this[key]) {
    is String -> value
    null -> fallback
    else -> value.toString()
}
