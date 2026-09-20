package com.bibliarium.app.data.importer.fb2

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Чинит на лету самую частую поломку в FB2 из сети — голый амперсанд.
 *
 * XML-парсер на `Тит & Ко` останавливается, хотя книга в остальном целая.
 * Здесь такой амперсанд превращается в `&amp;`, а настоящие сущности
 * (`&amp;`, `&#1071;`, `&#x42F;`) проходят нетронутыми.
 *
 * Работа идёт по байтам, и это безопасно: и амперсанд, и имена сущностей —
 * ASCII, а значит одинаковы и в UTF-8, и в windows-1251. Кодировку всего
 * остального определяет уже сам парсер по прологу.
 */
class AmpersandSanitizingStream(source: InputStream) : FilterInputStream(source) {

    /** Готовые к выдаче байты: либо пропущенные как есть, либо подставленные. */
    private val pending = ArrayDeque<Int>()

    private var finished = false

    override fun read(): Int {
        if (pending.isEmpty()) {
            fill()
        }
        return if (pending.isEmpty()) -1 else pending.removeFirst()
    }

    /**
     * Откат по потоку не поддерживается.
     *
     * FilterInputStream по умолчанию передаёт mark и reset вниз, а там свой
     * буфер — наше же состояние подстановки при этом не откатывается, и поток
     * разъезжается. Парсер, определяя кодировку по прологу, как раз может
     * попробовать откатиться, поэтому честно отвечаем, что так нельзя.
     */
    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) = Unit

    override fun reset() {
        throw java.io.IOException("Откат по потоку не поддерживается")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var written = 0
        while (written < length) {
            val next = read()
            if (next < 0) break
            buffer[offset + written] = next.toByte()
            written++
        }
        return if (written == 0) -1 else written
    }

    private fun fill() {
        if (finished) return

        val byte = `in`.read()
        if (byte < 0) {
            finished = true
            return
        }

        if (byte != AMPERSAND) {
            pending.addLast(byte)
            return
        }

        // Заглядываем вперёд ровно настолько, насколько может тянуться имя сущности.
        val lookahead = ArrayList<Int>(MAX_ENTITY_LENGTH)
        var valid = false
        while (lookahead.size < MAX_ENTITY_LENGTH) {
            val next = `in`.read()
            if (next < 0) {
                finished = true
                break
            }
            lookahead.add(next)
            if (next == SEMICOLON) {
                valid = looksLikeEntity(lookahead)
                break
            }
            if (!isEntityBodyByte(next, lookahead.size)) break
        }

        if (valid) {
            pending.addLast(AMPERSAND)
        } else {
            for (replacement in AMP_REPLACEMENT) {
                pending.addLast(replacement.code)
            }
        }
        lookahead.forEach { pending.addLast(it) }
    }

    private fun looksLikeEntity(bytes: List<Int>): Boolean {
        if (bytes.size < 2) return false
        val body = bytes.dropLast(1)
        if (body.isEmpty()) return false

        if (body[0] == HASH) {
            val digits = body.drop(1)
            if (digits.isEmpty()) return false
            return if (digits[0] == 'x'.code || digits[0] == 'X'.code) {
                digits.size > 1 && digits.drop(1).all { isHexDigit(it) }
            } else {
                digits.all { it in '0'.code..'9'.code }
            }
        }

        return isLetter(body[0]) && body.all { isLetter(it) || it in '0'.code..'9'.code }
    }

    private fun isEntityBodyByte(byte: Int, position: Int): Boolean =
        isLetter(byte) ||
            byte in '0'.code..'9'.code ||
            (position == 1 && byte == HASH) ||
            byte == 'x'.code ||
            byte == 'X'.code

    private fun isLetter(byte: Int): Boolean =
        byte in 'a'.code..'z'.code || byte in 'A'.code..'Z'.code

    private fun isHexDigit(byte: Int): Boolean =
        byte in '0'.code..'9'.code || byte in 'a'.code..'f'.code || byte in 'A'.code..'F'.code

    private companion object {
        const val AMPERSAND = '&'.code
        const val SEMICOLON = ';'.code
        const val HASH = '#'.code
        const val MAX_ENTITY_LENGTH = 12
        val AMP_REPLACEMENT = "&amp;".toCharArray()
    }
}
