package com.bibliarium.app.fb2tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.data.importer.fb2.Fb2ConversionReport
import com.bibliarium.app.data.importer.fb2.Fb2ToEpubConverter
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Конвертер FB2 → EPUB сам по себе, без импорта и без экранов.
 *
 * Проверяется результат, а не «не упало»: число глав, наличие картинок,
 * рабочие сноски и целая кириллица.
 */
@RunWith(AndroidJUnit4::class)
class Fb2ConverterTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val converter = Fb2ToEpubConverter()
    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = File(instrumentation.targetContext.cacheDir, "fb2-tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun plainBookKeepsChaptersAndMarkup() {
        val (report, epub) = convert("fb2_plain.fb2")

        assertEquals("Обычная книга", report.info.title)
        assertEquals(listOf("Иван Петрович Тестов"), report.info.authors)
        assertEquals("Испытательная серия", report.info.seriesName)
        assertEquals(3, report.info.seriesNumber)
        assertTrue("Жанры не перенеслись: ${report.info.genres}", report.info.genres.isNotEmpty())
        assertNotNull("Аннотация не перенеслась", report.info.annotation)

        assertEquals(
            "Глав должно быть три, получилось ${report.chapters}",
            3,
            report.chapters.size,
        )
        assertEquals("Глава первая", report.chapters.first())

        val first = epub.textOf("OEBPS/ch001.xhtml")
        TestArtifacts.note("fb2-plain-ch1", first.take(1200))

        assertTrue("Курсив не перенёсся", first.contains("<em>курсивом</em>"))
        assertTrue("Полужирный не перенёсся", first.contains("<strong>полужирным</strong>"))
        assertTrue("Подзаголовок не перенёсся", first.contains("class=\"subtitle\""))
        assertTrue("Эпиграф не перенёсся", first.contains("class=\"epigraph\""))
        assertTrue("Цитата не перенеслась", first.contains("class=\"cite\""))
        assertTrue("Стихи не перенеслись", first.contains("class=\"verse\""))
        assertTrue("Вложенный раздел потерян", first.contains("Вложенный раздел"))

        val nav = epub.textOf("OEBPS/nav.xhtml")
        assertTrue("Оглавление не собралось", nav.contains("Глава вторая"))
    }

    @Test
    fun imagesAndCoverSurvive() {
        val (report, epub) = convert("fb2_images.fb2")

        assertEquals("Картинок должно быть две", 2, report.imageCount)

        val names = epub.names()
        assertTrue("Обложки нет среди файлов: $names", "OEBPS/images/cover.png" in names)
        assertTrue("Иллюстрации нет среди файлов: $names", "OEBPS/images/pic1.png" in names)

        val cover = epub.bytesOf("OEBPS/images/cover.png")
        assertTrue("Обложка пустая", cover.size > 50)
        assertTrue(
            "Обложка перестала быть PNG",
            cover.copyOfRange(0, 4).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)),
        )

        val chapter = epub.textOf("OEBPS/ch001.xhtml")
        assertTrue(
            "Ссылка на картинку не переписана: $chapter",
            chapter.contains("<img src=\"images/pic1.png\""),
        )

        val opf = epub.textOf("OEBPS/content.opf")
        assertTrue("Обложка не помечена в манифесте", opf.contains("cover-image"))
    }

    @Test
    fun notesBecomeClickableFootnotes() {
        val (report, epub) = convert("fb2_notes.fb2")

        assertEquals("Сносок должно быть две", 2, report.noteCount)

        val chapter = epub.textOf("OEBPS/ch001.xhtml")
        assertTrue(
            "Ссылка на сноску не стала noteref: $chapter",
            chapter.contains("epub:type=\"noteref\"") &&
                chapter.contains("href=\"notes.xhtml#n1\""),
        )

        val notes = epub.textOf("OEBPS/notes.xhtml")
        TestArtifacts.note("fb2-notes", notes.take(800))
        assertTrue("Сноска не помечена как footnote", notes.contains("epub:type=\"footnote\""))
        assertTrue("Первой сноски нет", notes.contains("id=\"n1\""))
        assertTrue("Текст сноски потерян", notes.contains("Пояснение к первому утверждению"))
        assertTrue(
            "В сноске остался пустой абзац от заголовка: $notes",
            !notes.contains("<p></p>"),
        )
    }

    @Test
    fun windows1251StaysReadable() {
        val (report, epub) = convert("fb2_cp1251.fb2")

        assertEquals("Книга в кодировке 1251", report.info.title)
        assertEquals(
            "Двух авторов не разобрало: ${report.info.authors}",
            listOf("Сергей Первый", "Мария Ивановна Вторая"),
            report.info.authors,
        )
        assertEquals(
            "Переводчик потерялся",
            listOf("Джон Переводчиков"),
            report.info.translators,
        )

        val chapter = epub.textOf("OEBPS/ch001.xhtml")
        TestArtifacts.note("fb2-cp1251", chapter.take(600))
        assertTrue("Кириллица превратилась в кракозябры: $chapter", chapter.contains("ёжик"))
        assertTrue("Кавычки-ёлочки потерялись", chapter.contains("«ёлки-палки»"))
        assertTrue("Тире потерялось", chapter.contains("—"))
    }

    @Test
    fun brokenFileKeepsWhatWasReadable() {
        val (report, epub) = convert("fb2_broken.fb2")

        assertTrue("Разбор должен быть помечен оборванным", report.truncated)
        assertTrue("Ничего не уцелело: ${report.chapters}", report.chapters.isNotEmpty())

        val chapter = epub.textOf("OEBPS/ch001.xhtml")
        TestArtifacts.note("fb2-broken", chapter.take(800))
        assertTrue(
            "Целая часть книги потеряна: $chapter",
            chapter.contains("Этот абзац читается нормально"),
        )
        assertTrue(
            "Голый амперсанд не починился: $chapter",
            chapter.contains("Тит &amp; Ко") || chapter.contains("Тит & Ко"),
        )
    }

    /**
     * Каждый XML внутри собранной книги обязан начинаться ровно с пролога.
     *
     * Проверка идёт по настоящим байтам выхода, а не по коду: телефон отказался
     * открывать FB2 с «processing instructions must not start with xml», и то,
     * куда съехал пролог, видно только в шестнадцатеричном дампе. Дамп головы
     * каждого файла уходит в артефакты — читать его можно глазами.
     */
    @Test
    fun everyXmlFileStartsWithTheProlog() {
        val (_, epub) = convert("fb2_plain.fb2")

        val broken = mutableListOf<String>()
        epub.names()
            .filter { it.endsWith(".xhtml") || it.endsWith(".opf") || it.endsWith(".xml") }
            .sorted()
            .forEach { name ->
                val bytes = epub.bytesOf(name)
                val head = bytes.copyOfRange(0, minOf(HEAD_BYTES, bytes.size))
                TestArtifacts.note("fb2-head-" + name.replace('/', '-'), hexDump(head))
                if (!head.toString(Charsets.UTF_8).startsWith(PROLOG)) {
                    broken += name + ":\n" +
                        hexDump(head.copyOfRange(0, minOf(BROKEN_BYTES, head.size)))
                }
            }

        assertTrue(
            "Пролог <?xml стоит не в начале файла:\n" + broken.joinToString("\n"),
            broken.isEmpty(),
        )
    }

    // --- вспомогательное ---------------------------------------------------

    /** Шестнадцатеричный дамп с колонкой печатных знаков — как в hexdump -C. */
    private fun hexDump(bytes: ByteArray): String =
        bytes.toList().chunked(HEX_ROW).mapIndexed { row, chunk ->
            val hex = chunk.joinToString(" ") { "%02x".format(it) }
            val text = chunk.map { byte ->
                val code = byte.toInt() and 0xFF
                if (code in PRINTABLE_FIRST..PRINTABLE_LAST) code.toChar() else '.'
            }.joinToString("")
            "%04d  %-47s |%s|".format(row * HEX_ROW, hex, text)
        }.joinToString("\n")

    private fun convert(assetName: String): Pair<Fb2ConversionReport, ZipFile> {
        val target = File(workDir, "$assetName.epub")
        val result = converter.convert({ openAsset(assetName) }, target)
        val report = result.getOrElse { error ->
            throw AssertionError("Конвертация $assetName не удалась: $error")
        }
        return report to ZipFile(target)
    }

    private fun openAsset(name: String): InputStream =
        instrumentation.context.assets.open(name)

    private fun ZipFile.names(): Set<String> =
        entries().toList().map { it.name }.toSet()

    private fun ZipFile.textOf(path: String): String {
        val entry = getEntry(path) ?: throw AssertionError("В книге нет файла $path: ${names()}")
        return getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun ZipFile.bytesOf(path: String): ByteArray {
        val entry = getEntry(path) ?: throw AssertionError("В книге нет файла $path: ${names()}")
        return getInputStream(entry).use { it.readBytes() }
    }

    private companion object {
        const val PROLOG = "<?xml"
        const val HEAD_BYTES = 200
        const val BROKEN_BYTES = 48
        const val HEX_ROW = 16
        const val PRINTABLE_FIRST = 0x20
        const val PRINTABLE_LAST = 0x7E
    }
}
