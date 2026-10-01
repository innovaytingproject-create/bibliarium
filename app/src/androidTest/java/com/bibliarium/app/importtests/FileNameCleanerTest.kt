package com.bibliarium.app.importtests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.data.importer.FileNameCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Чистка имени файла — таблицей.
 *
 * Правило «проверка глазами человека» здесь ни при чём: это одна функция
 * с одним ответом на каждый вход, и проверять её через экран значило бы
 * прятать настоящую проверку за кликами. На экране проверяется другое —
 * что книга без метаданных показывает очищенное имя.
 */
@RunWith(AndroidJUnit4::class)
class FileNameCleanerTest {

    @Test
    fun cleansRealFileNames() {
        val cases = listOf(
            "Кислородные_деньги_—_обложка_«Росток».pdf" to "Кислородные деньги",
            "00. Лиды на 100млн.pdf" to "Лиды на 100млн",
            "01 - Хроники.epub" to "Хроники",
            "1.Введение.fb2" to "Введение",
            "[12] Солярис.epub" to "Солярис",
            "Мастер_и_Маргарита_final.epub" to "Мастер и Маргарита",
            "Дюна (1).pdf" to "Дюна",
            "Записки  о    кошках.epub" to "Записки о кошках",
            "Толстой. Война и мир.fb2.zip" to "Толстой. Война и мир",
        )

        val wrong = cases.mapNotNull { (input, expected) ->
            val actual = FileNameCleaner.clean(input).title
            if (actual == expected) null else "«$input» → «$actual», ждали «$expected»"
        }

        TestArtifacts.note(
            "filename-cleaning",
            cases.joinToString("\n") { (input, _) ->
                "$input → ${FileNameCleaner.clean(input).title}"
            },
        )
        assertTrue("Имена чистятся не так:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun splitsAuthorAndTitle() {
        val cleaned = FileNameCleaner.clean("Фрэнк Герберт - Дюна.epub")
        assertEquals("Дюна", cleaned.title)
        assertEquals("Фрэнк Герберт", cleaned.author)
    }

    @Test
    fun knowsJunkTitlesFromPdf() {
        val junk = listOf(
            "Microsoft Word - Документ1",
            "untitled",
            "Документ1",
            "Scan_0001",
            "C:\\Users\\user\\Documents\\отчёт.pdf",
            "ab",
        )
        val notJunk = listOf(
            "Лиды на 100 миллионов",
            "Дюна",
            "Создание сущностей. Первая ступень",
        )

        junk.forEach {
            assertTrue("«$it» должно считаться мусором", FileNameCleaner.isJunkTitle(it, "file.pdf"))
        }
        notJunk.forEach {
            assertFalse(
                "«$it» — нормальное название, а признано мусором",
                FileNameCleaner.isJunkTitle(it, "file.pdf"),
            )
        }

        // Название, повторяющее имя файла, — это не название.
        assertTrue(
            "Название, равное имени файла, должно считаться отсутствующим",
            FileNameCleaner.isJunkTitle("00 лиды на 100млн", "00 лиды на 100млн.pdf"),
        )
    }
}
