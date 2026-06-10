package maifetch.json

import java.io.Reader
import java.math.BigDecimal

object Json {
    fun parse(input: String?): Any? {
        val parser = Parser(input.orEmpty())
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) {
            throw IllegalArgumentException("unexpected trailing JSON content")
        }
        return value
    }

    fun parse(reader: Reader): Any? = parse(reader.readText())

    private class Parser(private val input: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("unexpected end of JSON")
            return when (val ch = input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> {
                    expect("true")
                    true
                }
                'f' -> {
                    expect("false")
                    false
                }
                'n' -> {
                    expect("null")
                    null
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("unexpected JSON character: $ch")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                expect('}')
                return result
            }
            while (true) {
                skipWhitespace()
                if (!peek('"')) throw IllegalArgumentException("object key must be a string")
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (peek('}')) {
                    expect('}')
                    return result
                }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val values = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) {
                expect(']')
                return values
            }
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (peek(']')) {
                    expect(']')
                    return values
                }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (!atEnd()) {
                    val ch = input[index++]
                    if (ch == '"') return@buildString
                    if (ch != '\\') {
                        append(ch)
                        continue
                    }
                    if (atEnd()) throw IllegalArgumentException("unterminated JSON escape")
                    when (val escaped = input[index++]) {
                        '"', '\\', '/' -> append(escaped)
                        'b' -> append('\b')
                        'f' -> append('\u000C')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'u' -> append(parseUnicodeEscape())
                        else -> throw IllegalArgumentException("unsupported JSON escape: $escaped")
                    }
                }
                throw IllegalArgumentException("unterminated JSON string")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > input.length) {
                throw IllegalArgumentException("incomplete unicode escape")
            }
            val hex = input.substring(index, index + 4)
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek('-')) index++
            readDigits()
            if (peek('.')) {
                index++
                readDigits()
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                readDigits()
            }
            val raw = input.substring(start, index)
            if ('.' in raw || 'e' in raw || 'E' in raw) return BigDecimal(raw)
            return raw.toIntOrNull() ?: raw.toLong()
        }

        private fun readDigits() {
            val start = index
            while (!atEnd() && input[index].isDigit()) {
                index++
            }
            if (start == index) {
                throw IllegalArgumentException("expected JSON number digit")
            }
        }

        private fun expect(text: String) {
            if (!input.startsWith(text, index)) throw IllegalArgumentException("expected $text")
            index += text.length
        }

        private fun expect(ch: Char) {
            if (atEnd() || input[index] != ch) throw IllegalArgumentException("expected JSON character: $ch")
            index++
        }

        private fun peek(ch: Char): Boolean = !atEnd() && input[index] == ch

        fun skipWhitespace() {
            while (!atEnd() && input[index] in charArrayOf(' ', '\n', '\r', '\t')) {
                index++
            }
        }

        fun atEnd(): Boolean = index >= input.length
    }
}
