package com.bibliarium.app.data.importer

import java.io.InputStream
import java.security.MessageDigest

/**
 * Отпечаток файла книги: размер плюс SHA-256 первых 64 КБ.
 *
 * По имени сравнивать нельзя — одна и та же книга часто лежит в двух местах
 * под разными именами. Полный хэш многомегабайтного файла считать незачем:
 * совпадение размера и начала файла для книг достаточно надёжно, а читать
 * приходится всего 64 КБ.
 */
data class BookFingerprint(
    val sizeBytes: Long,
    val headHash: String,
)

object Fingerprints {

    const val HEAD_BYTES: Int = 64 * 1024

    private const val HEX = "0123456789abcdef"

    /** Закрывает переданный поток. */
    fun headHash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        var remaining = HEAD_BYTES

        input.use { stream ->
            while (remaining > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }

        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }
}
