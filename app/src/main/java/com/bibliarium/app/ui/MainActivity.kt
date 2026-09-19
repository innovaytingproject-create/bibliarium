package com.bibliarium.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliarium.app.appContainer
import com.bibliarium.app.ui.library.LibraryScreen
import com.bibliarium.app.ui.library.LibraryViewModel
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.ThemeVariant

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = appContainer

        setContent {
            // Переключатель тем появится в настройках на шестом этапе.
            BibliariumTheme(variant = ThemeVariant.ARCHIVE) {
                val viewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.factory(container),
                )
                LibraryScreen(viewModel = viewModel)
            }
        }
    }
}
