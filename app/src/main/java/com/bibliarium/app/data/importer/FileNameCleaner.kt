package com.bibliarium.app.data.importer

/**
 * Название книги из имени файла — когда в самой книге названия нет.
 *
 * В библиотеке лежат файлы вида `Кислородные_деньги_—_обложка_«Росток».pdf`
 * и `00. Лиды на 100млн.pdf`. Показывать это человеку нельзя, но и выдумывать
 * за него нечего: всё, что можно сделать — убрать служебное и привести
 * к читаемому виду.
 */
object FileNameCleaner {

    /** Название и, если имя было вида «Автор - Название», ещё и автор. */
    data class CleanedName(val title: String, val author: String?)

    fun clean(fileName: String): CleanedName {
        val withoutExtension = dropExtension(fileName)
        val spaced = withoutExtension.replace('_', ' ')
        val withoutNumbering = NUMBERING.replace(spaced, "")

        val chunks = withoutNumbering
            .split(*DASHES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { isService(it) }

        if (chunks.isEmpty()) {
            return CleanedName(title = fileName.trim(), author = null)
        }

        // «Автор - Название»: автора отдаём отдельно, решать, брать ли его,
        // будет вызывающий — у книги уже может быть автор из метаданных.
        if (chunks.size == 2 && looksLikePerson(chunks[0])) {
            return CleanedName(
                title = dropServiceWords(tidy(chunks[1])),
                author = tidy(chunks[0]),
            )
        }

        val title = dropServiceWords(tidy(chunks.joinToString(" — ")))
        return CleanedName(
            title = title.ifBlank { tidy(chunks.joinToString(" — ")) },
            author = null,
        )
    }

    /**
     * Мусорное ли название.
     *
     * В PDF поле Title почти всегда заполняет не человек, а программа, которой
     * файл печатали: «Microsoft Word - Документ1», «untitled», путь к файлу.
     * Такое ничем не лучше имени файла, поэтому считаем его отсутствующим.
     */
    fun isJunkTitle(title: String?, fileName: String?): Boolean {
        val value = title?.trim().orEmpty()
        if (value.length < MIN_LENGTH) return true

        val lower = value.lowercase()
        if (JUNK.any { it.containsMatchIn(lower) }) return true

        // Путь вместо названия.
        if (value.contains('/') || value.contains('\\')) return true

        // Название совпадает с именем файла — значит его там и нет.
        val name = fileName?.let { dropExtension(it).replace('_', ' ').trim() }?.lowercase()
        return name != null && name.isNotEmpty() && name == lower
    }

    private fun dropExtension(fileName: String): String {
        var name = fileName.trim()
        // .fb2.zip — расширение из двух частей.
        if (name.lowercase().endsWith(".zip")) name = name.dropLast(".zip".length)
        val dot = name.lastIndexOf('.')
        if (dot > 0 && name.length - dot <= MAX_EXTENSION) name = name.substring(0, dot)
        return name
    }

    /**
     * Служебное слово не всегда отделено тире: «Мастер и Маргарита final»
     * и «Дюна (1)» — такие же пометки, просто дописанные через пробел.
     * Снимаем их с краёв, середину не трогаем: там слово может быть частью
     * названия.
     */
    private fun dropServiceWords(value: String): String {
        var words = value.split(' ').filter { it.isNotBlank() }
        while (words.isNotEmpty() && isServiceWord(words.last())) {
            words = words.dropLast(1)
        }
        while (words.isNotEmpty() && isServiceWord(words.first())) {
            words = words.drop(1)
        }
        return tidy(words.joinToString(" "))
    }

    private fun isServiceWord(word: String): Boolean {
        val lower = word.lowercase().trim(*TRIM_CHARS)
        if (lower.isEmpty()) return true
        if (NUMBER_IN_BRACKETS.matches(word.trim())) return true
        return SERVICE.any { it == lower || it == word.lowercase().trim() }
    }

    private fun isService(chunk: String): Boolean {
        val lower = chunk.lowercase().trim(*TRIM_CHARS)
        if (lower.isEmpty()) return true
        // Служебное слово либо занимает кусок целиком, либо открывает его:
        // «обложка «Росток»» — это по-прежнему пометка, а не название.
        return SERVICE.any { lower == it || lower.startsWith("$it ") }
    }

    private fun looksLikePerson(chunk: String): Boolean {
        val words = chunk.split(' ').filter { it.isNotBlank() }
        if (words.size !in PERSON_WORDS) return false
        return words.all { word -> word.first().isUpperCase() || word.first().isDigit().not() }
    }

    private fun tidy(value: String): String = value
        .replace(SPACES, " ")
        .trim(*TRIM_CHARS)
        .trim()

    private val DASHES = arrayOf(" — ", " – ", " - ", "—", "–")
    private val SPACES = Regex("\\s+")
    private val NUMBER_IN_BRACKETS = Regex("^[\[(]\d{1,3}[])]$")
    private val NUMBERING = Regex("^\\s*[\\[(]?\\d{1,3}[])]?\\s*[.)\\-–—]?\\s*")
    private val TRIM_CHARS = charArrayOf(' ', '.', ',', '-', '–', '—', '_')

    private val SERVICE = listOf(
        "обложка",
        "cover",
        "final",
        "финал",
        "итог",
        "v2",
        "(1)",
        "copy",
        "копия",
    )

    private val JUNK = listOf(
        Regex("^microsoft word"),
        Regex("^microsoft powerpoint"),
        Regex("^untitled"),
        Regex("^без имени"),
        Regex("^документ\\s*\\d*$"),
        Regex("^document\\s*\\d*$"),
        Regex("^doc\\d*$"),
        Regex("^scan[\\s_-]*\\d*$"),
        Regex("^\\d+$"),
        Regex("\\.(pdf|docx?|epub|fb2)$"),
    )

    private const val MIN_LENGTH = 3
    private const val MAX_EXTENSION = 6
    private val PERSON_WORDS = 2..3
}
