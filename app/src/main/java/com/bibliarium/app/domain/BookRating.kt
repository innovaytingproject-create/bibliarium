package com.bibliarium.app.domain

/**
 * Оценка книги: четыре стороны и своя мысль о прочитанном.
 *
 * Отвечать на все вопросы необязательно. Среднее считается по тем, на которые
 * ответили: две четвёрки из четырёх вопросов — это четыре, а не двойка.
 */
data class BookRating(
    val bookId: String,
    /** Насколько книга была полезна, 1..5. */
    val useful: Int? = null,
    /** Понятно ли написано, 1..5. */
    val clarity: Int? = null,
    /** Много ли нового, 1..5. */
    val novelty: Int? = null,
    /** Хотелось ли читать дальше, 1..5. */
    val engagement: Int? = null,
    /** Итог: среднее по заполненным или оценка, поставленная руками. */
    val overall: Float? = null,
    /** Что осталось от книги, свободный текст. */
    val note: String? = null,
    val ratedAt: Long? = null,
    /**
     * Опрос по этой книге уже показывали. Второй раз сам не всплывает, даже
     * если человек снова долистает до конца.
     */
    val surveyShown: Boolean = false,
) {
    /** Ответы по сторонам, которые человек заполнил. */
    val answers: List<Int> get() = listOfNotNull(useful, clarity, novelty, engagement)

    /** Есть ли что показывать в карточке. */
    val hasRating: Boolean get() = overall != null

    companion object {
        const val MIN = 1
        const val MAX = 5

        /** Любимые книги на полке — от этой оценки и выше. */
        const val FAVOURITE_FROM = 4.5f

        /**
         * Среднее по заполненным ответам. Если не ответили ни на один,
         * значение не меняется: там может лежать оценка, поставленная руками
         * прямо в карточке.
         */
        fun averageOf(rating: BookRating): Float? {
            val answers = rating.answers
            if (answers.isEmpty()) return rating.overall
            return answers.sum().toFloat() / answers.size
        }
    }
}
