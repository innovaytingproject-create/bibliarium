package com.bibliarium.app.data.importer.fb2

import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Пишет EPUB прямо в поток, по мере того как разбирается FB2.
 *
 * Ничего не копится в памяти: глава уходит в архив сразу, как дочитана,
 * картинка — сразу, как декодирована. Поэтому книга с картинками на полсотни
 * мегабайт проходит так же, как маленькая.
 *
 * Порядок записи в zip свободный, кроме одного: mimetype обязан быть первой
 * записью и без сжатия, иначе EPUB не признают.
 */
class EpubBuilder(output: OutputStream) : AutoCloseable {

    private val zip = ZipOutputStream(output)

    private val chapters = mutableListOf<ChapterEntry>()
    private val images = mutableListOf<ImageEntry>()
    private var notesHref: String? = null

    data class ChapterEntry(val href: String, val title: String)

    data class ImageEntry(val href: String, val id: String, val mediaType: String)

    init {
        val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            },
        )
        zip.write(mimetype)
        zip.closeEntry()

        writeText(
            "META-INF/container.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent(),
        )
    }

    // --- Главы -------------------------------------------------------------

    private var openChapter: String? = null

    fun startChapter(index: Int, title: String): String {
        val href = "ch%03d.xhtml".format(index)
        openChapter = href
        zip.putNextEntry(ZipEntry("OEBPS/$href"))
        writeRaw(xhtmlHeader(title))
        return href
    }

    fun finishChapter(title: String) {
        val href = openChapter ?: return
        writeRaw(XHTML_FOOTER)
        zip.closeEntry()
        openChapter = null
        chapters += ChapterEntry(href, title)
    }

    fun startNotes(): String {
        val href = "notes.xhtml"
        openChapter = href
        notesHref = href
        zip.putNextEntry(ZipEntry("OEBPS/$href"))
        writeRaw(xhtmlHeader("Примечания"))
        return href
    }

    fun finishNotes() {
        val href = openChapter ?: return
        writeRaw(XHTML_FOOTER)
        zip.closeEntry()
        openChapter = null
        notesHref = href
    }

    /**
     * Пишет в текущую главу. Вне главы молчит: между </body> и первой секцией
     * в FB2 лежат переводы строк, и раньше они летели в архив, где ещё не было
     * ни одной открытой записи, — весь разбор на этом и обрывался.
     */
    fun writeRaw(text: String) {
        if (openChapter == null) return
        zip.write(text.toByteArray(Charsets.UTF_8))
    }

    // --- Картинки ----------------------------------------------------------

    fun startImage(id: String, mediaType: String): String {
        val href = "images/${fb2ImageFileName(id)}"
        zip.putNextEntry(ZipEntry("OEBPS/$href"))
        images += ImageEntry(href, id, mediaType)
        return href
    }

    fun writeImageBytes(bytes: ByteArray) {
        zip.write(bytes)
    }

    fun finishImage() {
        zip.closeEntry()
    }

    // --- Завершение --------------------------------------------------------

    /** Возвращает названия глав, попавших в книгу. Пусто — значит книги нет. */
    fun finish(info: Fb2BookInfo): List<String> {
        // Если разбор оборвался посреди главы, запись всё равно надо закрыть.
        openChapter?.let {
            writeRaw(XHTML_FOOTER)
            zip.closeEntry()
            openChapter = null
            if (it != notesHref) chapters += ChapterEntry(it, "Без названия")
        }

        writeText("OEBPS/content.opf", buildOpf(info))
        writeText("OEBPS/nav.xhtml", buildNav())
        zip.finish()
        return chapters.map { it.title }
    }

    override fun close() {
        runCatching { zip.close() }
    }

    private fun writeText(path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildOpf(info: Fb2BookInfo): String {
        val coverImage = info.coverId?.let { id -> images.firstOrNull { it.id == id } }

        val manifest = buildString {
            append("    <item id=\"nav\" href=\"nav.xhtml\" ")
            append("media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            chapters.forEachIndexed { index, chapter ->
                append("    <item id=\"ch$index\" href=\"${chapter.href}\" ")
                append("media-type=\"application/xhtml+xml\"/>\n")
            }
            notesHref?.let {
                append("    <item id=\"notes\" href=\"$it\" ")
                append("media-type=\"application/xhtml+xml\"/>\n")
            }
            images.forEachIndexed { index, image ->
                append("    <item id=\"img$index\" href=\"${image.href}\" ")
                append("media-type=\"${image.mediaType}\"")
                if (image === coverImage) append(" properties=\"cover-image\"")
                append("/>\n")
            }
        }

        val spine = buildString {
            chapters.indices.forEach { append("    <itemref idref=\"ch$it\"/>\n") }
            if (notesHref != null) append("    <itemref idref=\"notes\"/>\n")
        }

        val metadata = buildString {
            append("    <dc:identifier id=\"book-id\">urn:bibliarium:fb2:")
            append(escape(info.title ?: "book"))
            append("</dc:identifier>\n")
            append("    <dc:title>${escape(info.title ?: "Без названия")}</dc:title>\n")
            append("    <dc:language>${escape(info.language ?: "ru")}</dc:language>\n")
            info.authors.forEach { append("    <dc:creator>${escape(it)}</dc:creator>\n") }
            info.translators.forEach {
                append("    <dc:contributor>${escape(it)}</dc:contributor>\n")
            }
            info.genres.forEach { append("    <dc:subject>${escape(it)}</dc:subject>\n") }
            info.year?.let { append("    <dc:date>${escape(it)}</dc:date>\n") }
            info.annotation?.let {
                append("    <dc:description>${escape(it)}</dc:description>\n")
            }
            info.seriesName?.let { series ->
                append("    <meta property=\"belongs-to-collection\" id=\"series\">")
                append(escape(series))
                append("</meta>\n")
                append("    <meta refines=\"#series\" property=\"collection-type\">series</meta>\n")
                info.seriesNumber?.let { number ->
                    append("    <meta refines=\"#series\" property=\"group-position\">")
                    append(number)
                    append("</meta>\n")
                }
            }
            append("    <meta property=\"dcterms:modified\">2026-01-01T00:00:00Z</meta>\n")
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            $metadata  </metadata>
              <manifest>
            $manifest  </manifest>
              <spine>
            $spine  </spine>
            </package>
        """.trimIndent()
    }

    private fun buildNav(): String {
        val items = chapters.joinToString("\n") {
            "      <li><a href=\"${it.href}\">${escape(it.title)}</a></li>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>Оглавление</title></head>
              <body>
                <nav epub:type="toc">
                  <ol>
            $items
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }

    private fun xhtmlHeader(title: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>${escape(title)}</title><meta charset="utf-8"/></head>
<body>
"""

    private companion object {
        const val XHTML_FOOTER = "\n</body>\n</html>\n"

        fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

/**
 * Имя файла картинки внутри EPUB. Считается одинаково и при записи двоичных
 * данных, и при переписывании ссылки в тексте — иначе ссылка указывала бы
 * не туда.
 */
internal fun fb2ImageFileName(id: String): String {
    val cleaned = id.map { if (it.isLetterOrDigit() || it == '.' || it == '_') it else '_' }
        .joinToString("")
    return cleaned.ifBlank { "image" }
}
