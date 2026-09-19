package com.bibliarium.app.data.importer

import android.util.Base64
import android.util.Xml
import java.io.File
import org.xmlpull.v1.XmlPullParser

/**
 * Readium не поддерживает FB2 (проверено по списку форматов toolkit 3.4.0),
 * поэтому заголовок, автора, жанр и обложку берём из самого XML.
 * Это не парсер EPUB и не движок рендеринга — только блок <description>.
 */
object Fb2MetadataReader {

    data class Fb2Metadata(
        val title: String?,
        val author: String?,
        val genre: String?,
        val coverId: String?,
    )

    fun read(file: File): Fb2Metadata {
        var title: String? = null
        var genre: String? = null
        var firstName: String? = null
        var middleName: String? = null
        var lastName: String? = null
        var coverId: String? = null

        var inTitleInfo = false
        var inAuthor = false
        var inCoverpage = false

        runCatching {
            file.inputStream().buffered().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "title-info" -> inTitleInfo = true
                            "author" -> if (inTitleInfo) inAuthor = true
                            "coverpage" -> if (inTitleInfo) inCoverpage = true
                            "book-title" -> if (inTitleInfo && title == null) {
                                title = parser.nextText().trim()
                            }
                            "genre" -> if (inTitleInfo && genre == null) {
                                genre = parser.nextText().trim()
                            }
                            "first-name" -> if (inAuthor && firstName == null) {
                                firstName = parser.nextText().trim()
                            }
                            "middle-name" -> if (inAuthor && middleName == null) {
                                middleName = parser.nextText().trim()
                            }
                            "last-name" -> if (inAuthor && lastName == null) {
                                lastName = parser.nextText().trim()
                            }
                            "image" -> if (inCoverpage && coverId == null) {
                                coverId = hrefOf(parser)?.removePrefix("#")
                            }
                        }

                        XmlPullParser.END_TAG -> when (parser.name) {
                            "title-info" -> inTitleInfo = false
                            "author" -> inAuthor = false
                            "coverpage" -> inCoverpage = false
                            // Дальше идёт тело книги — метаданные закончились.
                            "description" -> return@use
                        }
                    }
                    event = parser.next()
                }
            }
        }

        val author = listOfNotNull(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        return Fb2Metadata(
            title = title?.takeIf { it.isNotBlank() },
            author = author,
            genre = genre?.takeIf { it.isNotBlank() },
            coverId = coverId?.takeIf { it.isNotBlank() },
        )
    }

    /** Обложка FB2 лежит в <binary> в base64 в конце файла. */
    fun readBinary(file: File, id: String): ByteArray? {
        runCatching {
            file.inputStream().buffered().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "binary") {
                        if (parser.getAttributeValue(null, "id") == id) {
                            val encoded = parser.nextText()
                            return Base64.decode(encoded, Base64.DEFAULT)
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return null
    }

    /** Атрибут href лежит в пространстве имён xlink, поэтому ищем по суффиксу имени. */
    private fun hrefOf(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            if (name == "href" || name.endsWith(":href")) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }
}
