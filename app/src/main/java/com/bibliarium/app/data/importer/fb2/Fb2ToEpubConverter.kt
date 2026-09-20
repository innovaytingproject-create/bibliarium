package com.bibliarium.app.data.importer.fb2

import android.util.Base64
import android.util.Xml
import java.io.File
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Превращает FB2 в EPUB при импорте, чтобы дальше книгу читал один движок.
 *
 * Разбор потоковый и односторонний: глава уходит в архив сразу, как дочитана,
 * картинка — сразу, как декодирована. Файл целиком в память не попадает,
 * поэтому книга с иллюстрациями на полсотни мегабайт проходит так же, как
 * маленькая.
 *
 * Ссылки на сноски и картинки переписываются в момент встречи, а сами сноски
 * и картинки лежат в конце файла — но ждать их не нужно: ссылка знает только
 * адрес, а содержимое приедет позже в том же проходе.
 *
 * Кодировка не угадывается: парсер читает объявление из пролога. FB2 из сети
 * часто в windows-1251, и предполагать UTF-8 нельзя.
 */
class Fb2ToEpubConverter {

    fun convert(openStream: () -> InputStream, target: File): Result<Fb2ConversionReport> {
        var builder: EpubBuilder? = null
        return try {
            val output = target.outputStream().buffered()
            val epub = EpubBuilder(output)
            builder = epub

            val state = ConversionState()
            val parser = Xml.newPullParser()
            val input = AmpersandSanitizingStream(openStream().buffered())

            input.use {
                // null вместо кодировки — парсер берёт её из пролога.
                parser.setInput(it, null)
                state.truncated = !parse(parser, epub, state)
            }

            // Проверяем после finish: если разбор оборвался посреди главы,
            // она всё равно закрывается и попадает в книгу.
            val titles = epub.finish(state.info())
            epub.close()

            if (titles.isEmpty()) {
                target.delete()
                return Result.failure(
                    Fb2ConversionException(
                        failure = Fb2ConversionFailure.NO_CONTENT,
                        detail = "оборван=${state.truncated}, картинок=${state.imageCount}, " +
                            "заголовок=${state.info().title}, ошибка=${state.parseError}",
                    ),
                )
            }

            Result.success(
                Fb2ConversionReport(
                    info = state.info(),
                    chapters = titles,
                    imageCount = state.imageCount,
                    noteCount = state.noteCount,
                    truncated = state.truncated,
                ),
            )
        } catch (e: Exception) {
            builder?.close()
            target.delete()
            Result.failure(
                Fb2ConversionException(
                    failure = Fb2ConversionFailure.UNREADABLE,
                    detail = e.message,
                    cause = e,
                ),
            )
        }
    }

    /**
     * Возвращает true, если дочитали до конца. На сломанном XML останавливаемся
     * и отдаём то, что успели: половина книги полезнее, чем ничего.
     */
    private fun parse(
        parser: XmlPullParser,
        epub: EpubBuilder,
        state: ConversionState,
    ): Boolean {
        try {
            // nextToken, а не next: next склеивает весь текст узла в одну строку,
            // и картинка в base64 приехала бы в память целиком.
            var event = parser.nextToken()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> onStart(parser, epub, state)
                    XmlPullParser.END_TAG -> onEnd(parser, epub, state)
                    XmlPullParser.TEXT,
                    XmlPullParser.CDSECT,
                    XmlPullParser.ENTITY_REF,
                    XmlPullParser.IGNORABLE_WHITESPACE,
                    -> parser.text?.let { state.onText(it, epub) }
                }
                event = parser.nextToken()
            }
            return true
        } catch (e: XmlPullParserException) {
            state.parseError = "XML: ${e.message}"
            return false
        } catch (e: java.io.IOException) {
            state.parseError = "IO: ${e.message}"
            return false
        } catch (e: RuntimeException) {
            // Парсер на битом документе умеет бросать и не проверяемые исключения.
            state.parseError = "${e.javaClass.simpleName}: ${e.message}"
            return false
        }
    }

    private fun onStart(parser: XmlPullParser, epub: EpubBuilder, state: ConversionState) {
        val name = parser.name ?: return

        when (name) {
            "description" -> state.zone = Zone.DESCRIPTION
            "binary" -> {
                readBinary(parser, epub, state)
                return
            }

            "body" -> {
                val bodyName = parser.getAttributeValue(null, "name")
                state.zone = if (bodyName == "notes" || bodyName == "comments") {
                    Zone.NOTES
                } else {
                    Zone.BODY
                }
                state.sectionDepth = 0
            }
        }

        when (state.zone) {
            Zone.DESCRIPTION -> state.onDescriptionStart(parser, name)
            Zone.BODY -> state.onBodyStart(parser, epub, name)
            Zone.NOTES -> state.onNotesStart(parser, epub, name)
            Zone.NONE -> Unit
        }
    }

    private fun onEnd(parser: XmlPullParser, epub: EpubBuilder, state: ConversionState) {
        val name = parser.name ?: return

        when (state.zone) {
            Zone.DESCRIPTION -> state.onDescriptionEnd(name)
            Zone.BODY -> state.onBodyEnd(epub, name)
            Zone.NOTES -> state.onNotesEnd(epub, name)
            Zone.NONE -> Unit
        }

        if (name == "description" || name == "body") {
            state.zone = Zone.NONE
        }
    }

    /**
     * Картинка декодируется кусками: base64 копится ровно до кратного четырём
     * размера и тут же уходит в архив.
     */
    private fun readBinary(parser: XmlPullParser, epub: EpubBuilder, state: ConversionState) {
        val id = parser.getAttributeValue(null, "id") ?: return
        val mediaType = parser.getAttributeValue(null, "content-type") ?: "image/jpeg"

        epub.startImage(id, mediaType)
        val pending = StringBuilder()

        try {
            var event = parser.nextToken()
            while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                    parser.text?.forEach { char -> if (!char.isWhitespace()) pending.append(char) }
                    val usable = pending.length - pending.length % 4
                    if (usable >= DECODE_CHUNK) {
                        val chunk = pending.substring(0, usable)
                        pending.delete(0, usable)
                        epub.writeImageBytes(Base64.decode(chunk, Base64.DEFAULT))
                    }
                }
                event = parser.nextToken()
            }
            if (pending.isNotEmpty()) {
                epub.writeImageBytes(Base64.decode(pending.toString(), Base64.DEFAULT))
            }
        } catch (e: IllegalArgumentException) {
            // Битый base64 — картинку теряем, книгу нет.
        }

        epub.finishImage()
        state.imageCount++
    }

    private companion object {
        /** Кратно четырём, чтобы база64 делилась без остатка. */
        const val DECODE_CHUNK = 8192
    }
}

internal enum class Zone { NONE, DESCRIPTION, BODY, NOTES }
