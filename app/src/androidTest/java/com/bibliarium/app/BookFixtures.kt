package com.bibliarium.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Тестовые книги. Собираются кодом, а не лежат в репозитории двоичными файлами:
 * так видно, что именно внутри, и нечему протухнуть.
 *
 * Раскладываются прямо в общую память из процесса теста, поэтому годятся только
 * для прогона, где полный доступ уже выдан.
 */
object BookFixtures {

    const val DIRECTORY = "/sdcard/BibliariumTest"

    const val EPUB_TITLE = "Тестовый EPUB"
    const val FB2_TITLE = "Тестовый FB2"
    const val FB2_ZIP_TITLE = "Тестовый FB2 в архиве"

    val fileNames = listOf("valid.epub", "valid.fb2", "archived.fb2.zip", "broken.epub")

    /**
     * Кладёт набор книг в общую память. Пишем напрямую из процесса теста:
     * на Android 11 shell не читает /sdcard/Android/data, поэтому раскладывать
     * файлы через промежуточный каталог приложения и `cp` нельзя — на API 30
     * так ничего не доезжало. Вызывать только там, где разрешение уже выдано.
     */
    fun seed(): String {
        val directory = File(DIRECTORY)
        directory.deleteRecursively()
        check(directory.mkdirs() || directory.isDirectory) {
            "Не удалось создать $DIRECTORY — есть ли полный доступ к файлам?"
        }

        File(directory, "valid.epub").writeBytes(epub(EPUB_TITLE))
        File(directory, "valid.fb2").writeBytes(fb2(FB2_TITLE))
        File(directory, "archived.fb2.zip").writeBytes(fb2Archive(FB2_ZIP_TITLE))
        // Расширение книжное, содержимое — мусор: импорт такого файла должен
        // отказаться, а не уронить всю пачку.
        File(directory, "broken.epub").writeText("это не epub, а просто текст")

        TestArtifacts.note(
            "fixtures-seeded",
            directory.listFiles()
                ?.joinToString(separator = ", ") { "${it.name} ${it.length()}" }
                ?: "каталог не читается",
        )
        return DIRECTORY
    }

    fun cleanUp() {
        runCatching { File(DIRECTORY).deleteRecursively() }
    }

    private fun fb2(title: String): ByteArray = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                     xmlns:l="http://www.w3.org/1999/xlink">
          <description>
            <title-info>
              <genre>sf</genre>
              <author>
                <first-name>Иван</first-name>
                <last-name>Тестов</last-name>
              </author>
              <book-title>$title</book-title>
            </title-info>
          </description>
          <body>
            <section><p>Текст книги.</p></section>
          </body>
        </FictionBook>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private fun fb2Archive(title: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("book.fb2"))
            zip.write(fb2(title))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /**
     * Минимальный, но настоящий EPUB 3: mimetype первой записью без сжатия,
     * иначе движки такой файл не признают.
     */
    private fun epub(title: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val entry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            }
            zip.putNextEntry(entry)
            zip.write(mimetype)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">urn:uuid:bibliarium-test</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:creator>Иван Тестов</dc:creator>
                    <dc:language>ru</dc:language>
                    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="chapter"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <head><title>$title</title></head>
                  <body>
                    <nav epub:type="toc"><ol><li><a href="chapter.xhtml">Глава</a></li></ol></nav>
                  </body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/chapter.xhtml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Глава</title></head>
                  <body><p>Текст книги.</p></body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
