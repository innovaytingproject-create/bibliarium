package com.bibliarium.app.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.data.store.RatingStore
import com.bibliarium.app.domain.BookRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RatingSurveyViewModel(
    val bookId: String,
    private val bookStore: BookStore,
    private val ratingStore: RatingStore,
) : ViewModel() {

    private val _bookTitle = MutableStateFlow("")
    val bookTitle: StateFlow<String> = _bookTitle.asStateFlow()

    /** Прежние ответы: опрос можно открыть снова и поправить. */
    val rating: StateFlow<BookRating?> = ratingStore.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), null)

    init {
        viewModelScope.launch { _bookTitle.value = bookStore.get(bookId)?.title.orEmpty() }
    }

    fun save(rating: BookRating) {
        viewModelScope.launch { ratingStore.save(rating.copy(surveyShown = true)) }
    }

    companion object {
        private const val SUBSCRIPTION_MS = 5_000L

        fun factory(container: AppContainer, bookId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RatingSurveyViewModel(
                        bookId = bookId,
                        bookStore = container.bookStore,
                        ratingStore = container.ratingStore,
                    )
                }
            }
    }
}
