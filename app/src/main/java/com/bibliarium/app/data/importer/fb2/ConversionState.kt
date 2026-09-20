package com.bibliarium.app.data.importer.fb2

import org.xmlpull.v1.XmlPullParser

/**
 * Состояние разбора. Держит и метаданные, и то, где мы сейчас внутри книги.
 *
 * Разметка переносится по простому правилу: известный тег FB2 превращается
 * в свой аналог XHTML, неизвестный пропускается, а его текст остаётся.
 * Так незнакомый тег не теряет содержимое и не ломает разбор.
 */
internal class ConversionState {

    var zone: Zone = Zone.NONE
    var sectionDepth: Int = 0
    var truncated: Boolean = false
    var imageCount: Int = 0
    var noteCount: Int = 0

    val chapters = mutableListOf<String>()

    // --- метаданные --------------------------------------------------------

    private var title: String? = null
    private val authors = mutableListOf<String>()
    private val translators = mutableListOf<String>()
    private val genres = mutableListOf<String>()
    private var language: String? = null
    private var year: String? = null
    private var annotation: String? = null
    private var seriesName: String? = null
    private var seriesNumber: Int? = null
    private var coverId: String? = null

    private var inTitleInfo = false
    private var inPerson = false
    private var personIsTranslator = false
    private var inAnnotation = false
    private var inCoverpage = false
    private var descriptionField: String? = null
    private val fieldText = StringBuilder()
    private val personParts = linkedMapOf<String, String>()

    fun info(): Fb2BookInfo = Fb2BookInfo(
        title = title?.trim()?.takeIf { it.isNotEmpty() },
        authors = authors.toList(),
        translators = translators.toList(),
        genres = genres.toList(),
        language = language?.trim()?.takeIf { it.isNotEmpty() },
        year = year?.trim()?.takeIf { it.isNotEmpty() },
        annotation = annotation?.trim()?.takeIf { it.isNotEmpty() },
        seriesName = seriesName?.trim()?.takeIf { it.isNotEmpty() },
        seriesNumber = seriesNumber,
        coverId = coverId,
    )

    fun onDescriptionStart(parser: XmlPullParser, name: String) {
        when (name) {
            "title-info" -> inTitleInfo = true
            "author" -> if (inTitleInfo) {
                inPerson = true
                personIsTranslator = false
                personParts.clear()
            }
            "translator" -> if (inTitleInfo) {
                inPerson = true
                personIsTranslator = true
                personParts.clear()
            }
            "annotation" -> if (inTitleInfo) {
                inAnnotation = true
                fieldText.setLength(0)
            }
            "coverpage" -> if (inTitleInfo) inCoverpage = true
            "image" -> if (inCoverpage && coverId == null) {
                coverId = hrefOf(parser)?.removePrefix("#")
            }
            "sequence" -> if (inTitleInfo) {
                seriesName = parser.getAttributeValue(null, "name")
                seriesNumber = parser.getAttributeValue(null, "number")?.toIntOrNull()
            }
            "date" -> {
                // Год берём из атрибута value, если он есть: там формат надёжнее.
                val value = parser.getAttributeValue(null, "value")
                if (value != null && year == null) year = value.take(4)
                descriptionField = "date"
                fieldText.setLength(0)
            }
            "book-title", "genre", "lang", "first-name", "middle-name", "last-name",
            "nickname",
            -> {
                descriptionField = name
                fieldText.setLength(0)
            }
        }
    }

    fun onDescriptionEnd(name: String) {
        when (name) {
            "title-info" -> inTitleInfo = false
            "coverpage" -> inCoverpage = false
            "annotation" -> if (inAnnotation) {
                inAnnotation = false
                if (annotation == null) annotation = fieldText.toString()
                fieldText.setLength(0)
            }
            "author", "translator" -> if (inPerson) {
                val full = listOfNotNull(
                    personParts["first-name"],
                    personParts["middle-name"],
                    personParts["last-name"],
                ).filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { personParts["nickname"].orEmpty() }
                    .trim()

                if (full.isNotEmpty()) {
                    if (personIsTranslator) translators += full else authors += full
                }
                inPerson = false
                personParts.clear()
            }
            descriptionField -> {
                val value = fieldText.toString().trim()
                when (name) {
                    "book-title" -> if (inTitleInfo && title == null) title = value
                    "genre" -> if (inTitleInfo && value.isNotEmpty()) genres += value
                    "lang" -> if (inTitleInfo && language == null) language = value
                    "date" -> if (year == null && value.isNotEmpty()) year = value.take(4)
                    "first-name", "middle-name", "last-name", "nickname" ->
                        if (inPerson) personParts[name] = value
                }
                descriptionField = null
                fieldText.setLength(0)
            }
        }
    }

    // --- текст книги -------------------------------------------------------

    private var inTitle = false
    private var titleLevel = 1
    private val titleText = StringBuilder()
    private var pendingChapterTitle: String? = null
    private var chapterOpen = false

    fun onBodyStart(parser: XmlPullParser, epub: EpubBuilder, name: String) {
        when (name) {
            "body" -> Unit

            "section" -> {
                sectionDepth++
                if (sectionDepth == 1) {
                    epub.startChapter(chapters.size + 1, "Глава ${chapters.size + 1}")
                    chapterOpen = true
                    pendingChapterTitle = null
                } else {
                    epub.writeRaw("<section>\n")
                }
            }

            "title" -> {
                inTitle = true
                titleLevel = sectionDepth.coerceIn(1, 6)
                titleText.setLength(0)
            }

            "image" -> {
                val id = hrefOf(parser)?.removePrefix("#") ?: return
                epub.writeRaw("<img src=\"images/${fb2ImageFileName(id)}\" alt=\"\"/>\n")
            }

            "a" -> {
                val href = hrefOf(parser).orEmpty()
                val type = parser.getAttributeValue(null, "type")
                if (type == "note" && href.startsWith("#")) {
                    // epub:type="noteref" — то, по чему Readium показывает сноску
                    // всплывающим окном, а не уводит в конец книги.
                    epub.writeRaw(
                        "<a epub:type=\"noteref\" href=\"notes.xhtml$href\">",
                    )
                    noteCount++
                } else {
                    epub.writeRaw("<a href=\"${escapeAttribute(href)}\">")
                }
            }

            else -> openTag(name)?.let { epub.writeRaw(it) }
        }
    }

    fun onBodyEnd(epub: EpubBuilder, name: String) {
        when (name) {
            "title" -> {
                inTitle = false
                val text = titleText.toString().trim()
                if (text.isNotEmpty()) {
                    epub.writeRaw("<h$titleLevel>${escapeText(text)}</h$titleLevel>\n")
                    if (sectionDepth == 1 && pendingChapterTitle == null) {
                        pendingChapterTitle = text
                    }
                }
                titleText.setLength(0)
            }

            "section" -> {
                if (sectionDepth == 1 && chapterOpen) {
                    val chapterTitle = pendingChapterTitle ?: "Глава ${chapters.size + 1}"
                    epub.finishChapter(chapterTitle)
                    chapters += chapterTitle
                    chapterOpen = false
                } else if (sectionDepth > 1) {
                    epub.writeRaw("</section>\n")
                }
                sectionDepth = (sectionDepth - 1).coerceAtLeast(0)
            }

            "a" -> epub.writeRaw("</a>")

            else -> closeTag(name)?.let { epub.writeRaw(it) }
        }
    }

    // --- сноски ------------------------------------------------------------

    private var noteOpen = false

    fun onNotesStart(parser: XmlPullParser, epub: EpubBuilder, name: String) {
        when (name) {
            "body" -> epub.startNotes()

            "section" -> {
                sectionDepth++
                val id = parser.getAttributeValue(null, "id")
                if (sectionDepth == 1 && id != null) {
                    epub.writeRaw(
                        "<aside epub:type=\"footnote\" id=\"${escapeAttribute(id)}\">\n",
                    )
                    noteOpen = true
                }
            }

            "title" -> {
                inTitle = true
                titleLevel = 5
                titleText.setLength(0)
            }

            else -> openTag(name)?.let { epub.writeRaw(it) }
        }
    }

    fun onNotesEnd(epub: EpubBuilder, name: String) {
        when (name) {
            "body" -> {
                if (noteOpen) {
                    epub.writeRaw("</aside>\n")
                    noteOpen = false
                }
                epub.finishNotes()
            }

            "title" -> {
                inTitle = false
                val text = titleText.toString().trim()
                if (text.isNotEmpty()) {
                    epub.writeRaw("<h$titleLevel>${escapeText(text)}</h$titleLevel>\n")
                }
                titleText.setLength(0)
            }

            "section" -> {
                if (sectionDepth == 1 && noteOpen) {
                    epub.writeRaw("</aside>\n")
                    noteOpen = false
                }
                sectionDepth = (sectionDepth - 1).coerceAtLeast(0)
            }

            else -> closeTag(name)?.let { epub.writeRaw(it) }
        }
    }

    // --- текст -------------------------------------------------------------

    fun onText(text: String, epub: EpubBuilder) {
        when {
            inTitle -> titleText.append(text)

            zone == Zone.DESCRIPTION -> {
                if (inAnnotation || descriptionField != null) fieldText.append(text)
            }

            zone == Zone.BODY || zone == Zone.NOTES -> epub.writeRaw(escapeText(text))

            else -> Unit
        }
    }

    private fun hrefOf(parser: XmlPullParser): String? {
        for (index in 0 until parser.attributeCount) {
            val attribute = parser.getAttributeName(index)
            if (attribute == "href" || attribute.endsWith(":href")) {
                return parser.getAttributeValue(index)
            }
        }
        return null
    }

    private companion object {
        /** Известные теги FB2 и их пара в XHTML. Неизвестные молча пропускаются. */
        val TAGS: Map<String, Pair<String, String>> = mapOf(
            "p" to ("<p>" to "</p>\n"),
            "subtitle" to ("<h4 class=\"subtitle\">" to "</h4>\n"),
            "emphasis" to ("<em>" to "</em>"),
            "strong" to ("<strong>" to "</strong>"),
            "strikethrough" to ("<s>" to "</s>"),
            "sub" to ("<sub>" to "</sub>"),
            "sup" to ("<sup>" to "</sup>"),
            "code" to ("<code>" to "</code>"),
            "epigraph" to ("<blockquote class=\"epigraph\">" to "</blockquote>\n"),
            "cite" to ("<blockquote class=\"cite\">" to "</blockquote>\n"),
            "poem" to ("<div class=\"poem\">" to "</div>\n"),
            "stanza" to ("<div class=\"stanza\">" to "</div>\n"),
            "v" to ("<p class=\"verse\">" to "</p>\n"),
            "text-author" to ("<p class=\"text-author\">" to "</p>\n"),
            "empty-line" to ("<p class=\"empty\"> " to "</p>\n"),
            "table" to ("<table>" to "</table>\n"),
            "tr" to ("<tr>" to "</tr>\n"),
            "td" to ("<td>" to "</td>"),
            "th" to ("<th>" to "</th>"),
        )

        fun openTag(name: String): String? = TAGS[name]?.first

        fun closeTag(name: String): String? = TAGS[name]?.second

        fun escapeText(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun escapeAttribute(text: String): String = escapeText(text).replace("\"", "&quot;")
    }
}
