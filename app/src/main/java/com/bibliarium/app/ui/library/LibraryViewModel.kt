package com.bibliarium.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.importer.ImportException
import com.bibliarium.app.data.importer.ImportFailure
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.domain.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Сообщение пользователю; в строку его превращает экран, слой данных строк не знает. */
sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage
    data class ImportFailed(val failure: ImportFailure) : LibraryMessage
    data class Prepared(val title: String) : LibraryMessage
    data class PreparationFailed(val title: String) : LibraryMessage
}

class LibraryViewModel(
    private val bookStore: BookStore,
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookStore.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    fun import(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = bookStore.add(uri)
            _isImporting.value = false
            _message.value = result.fold(
                onSuccess = { LibraryMessage.Imported(it.title) },
                onFailure = { error ->
                    val failure = (error as? ImportException)?.failure ?: ImportFailure.STORAGE_FAILED
                    LibraryMessage.ImportFailed(failure)
                },
            )
        }
    }

    private val _retrying = MutableStateFlow<String?>(null)
    val retrying: StateFlow<String?> = _retrying.asStateFlow()

    /** Повтор подготовки книги, которую в прошлый раз не удалось открыть. */
    fun retryPreparation(id: String) {
        viewModelScope.launch {
            _retrying.value = id
            val title = bookStore.get(id)?.title.orEmpty()
            val result = bookStore.retryPreparation(id)
            _retrying.value = null
            _message.value = if (result.isSuccess) {
                LibraryMessage.Prepared(title)
            } else {
                LibraryMessage.PreparationFailed(title)
            }
        }
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch { bookStore.setFavorite(book.id, !book.isFavorite) }
    }

    fun delete(id: String) {
        viewModelScope.launch { bookStore.delete(id) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.bookStore) }
        }
    }
}
