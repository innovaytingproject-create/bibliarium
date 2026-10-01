package com.bibliarium.app.ui.book

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.data.store.HighlightStore
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.Highlight
import com.bibliarium.app.reader.ReaderContentOpener
import com.bibliarium.app.reader.TocEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Что знает карточка о книге сверх самой записи. */
data class BookDetails(
    /** Сколько всего страниц; 0 — пока не посчитано. */
    val pages: Int = 0,
    val tableOfContents: List<TocEntry> = emptyList(),
)

class BookCardViewModel(
    private val bookId: String,
    private val bookStore: BookStore,
    private val highlightStore: HighlightStore,
    private val opener: ReaderContentOpener,
) : ViewModel() {

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _details = MutableStateFlow(BookDetails())
    val details: StateFlow<BookDetails> = _details.asStateFlow()

    val highlights: StateFlow<List<Highlight>> = highlightStore.observeForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), emptyList())

    val quotesCount: StateFlow<Int> = highlights
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), 0)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        reload()
        loadDetails()
    }

    /**
     * Объём и оглавление берутся из самой книги, а для этого её надо открыть
     * движком. Это не мгновенно, поэтому карточка сначала показывает всё
     * остальное, а числа появляются следом.
     */
    private fun loadDetails() {
        viewModelScope.launch {
            val book = bookStore.get(bookId) ?: return@launch
            if (!book.isReadable) return@launch
            opener.open(book).onSuccess { content ->
                _details.value = BookDetails(
                    pages = content.totalPositions,
                    tableOfContents = content.tableOfContents,
                )
                content.close()
            }
        }
    }

    fun reload() {
        viewModelScope.launch { _book.value = bookStore.get(bookId) }
    }

    fun rename(title: String, author: String?) {
        viewModelScope.launch {
            bookStore.rename(bookId, title, author)
            _book.value = bookStore.get(bookId)
        }
    }

    fun setDescription(text: String?) {
        viewModelScope.launch {
            bookStore.setDescription(bookId, text)
            _book.value = bookStore.get(bookId)
        }
    }

    fun setCover(uri: Uri?) {
        viewModelScope.launch {
            bookStore.setCustomCover(bookId, uri)
            _book.value = bookStore.get(bookId)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = _book.value ?: return@launch
            bookStore.setFavorite(bookId, !current.isFavorite)
            _book.value = bookStore.get(bookId)
        }
    }

    fun markFinished() {
        viewModelScope.launch {
            bookStore.saveProgress(bookId, 1f, _book.value?.locator)
            _book.value = bookStore.get(bookId)
        }
    }

    fun delete() {
        viewModelScope.launch {
            bookStore.delete(bookId)
            _deleted.value = true
        }
    }

    companion object {
        private const val SUBSCRIPTION_MS = 5_000L

        fun factory(container: AppContainer, bookId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BookCardViewModel(
                        bookId = bookId,
                        bookStore = container.bookStore,
                        highlightStore = container.highlightStore,
                        opener = container.readerContentOpener,
                    )
                }
            }
    }
}
