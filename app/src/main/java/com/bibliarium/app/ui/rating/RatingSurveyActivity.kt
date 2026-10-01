package com.bibliarium.app.ui.rating

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliarium.app.R
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.BookRating
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.ThemeVariant

/**
 * Опрос после прочтения.
 *
 * Отдельный экран поверх читалки: книга дочитана, и спросить стоит сразу,
 * пока впечатление свежее. Отвечать на все вопросы необязательно —
 * среднее считается по тем, на которые ответили.
 */
class RatingSurveyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        val container = appContainer
        setContent {
            BibliariumTheme(variant = ThemeVariant.ARCHIVE) {
                val model: RatingSurveyViewModel = viewModel(
                    factory = RatingSurveyViewModel.factory(container, bookId),
                )
                RatingSurveyScreen(
                    viewModel = model,
                    onDone = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "bookId"

        fun intent(context: Context, bookId: String): Intent =
            Intent(context, RatingSurveyActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}

@Composable
private fun RatingSurveyScreen(viewModel: RatingSurveyViewModel, onDone: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    val title by viewModel.bookTitle.collectAsStateWithLifecycle()
    val saved by viewModel.rating.collectAsStateWithLifecycle()

    var useful by remember(saved) { mutableStateOf(saved?.useful) }
    var clarity by remember(saved) { mutableStateOf(saved?.clarity) }
    var novelty by remember(saved) { mutableStateOf(saved?.novelty) }
    var engagement by remember(saved) { mutableStateOf(saved?.engagement) }
    var note by remember(saved) { mutableStateOf(saved?.note.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.survey_title),
            style = type.headlineMd,
            color = colors.text,
            modifier = Modifier.padding(top = spacing.xl),
        )
        Text(
            text = title,
            style = type.labelMd,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Question(R.string.survey_useful, useful, "Польза") { useful = it }
        Question(R.string.survey_clarity, clarity, "Ясность") { clarity = it }
        Question(R.string.survey_novelty, novelty, "Новизна") { novelty = it }
        Question(R.string.survey_engagement, engagement, "Удержание") { engagement = it }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.survey_note)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(NOTE_HEIGHT.dp),
        )

        Button(
            onClick = {
                viewModel.save(
                    BookRating(
                        bookId = viewModel.bookId,
                        useful = useful,
                        clarity = clarity,
                        novelty = novelty,
                        engagement = engagement,
                        overall = saved?.overall,
                        note = note.trim().takeIf { it.isNotEmpty() },
                    ),
                )
                onDone()
            },
            shape = RoundedCornerShape(BibliariumTheme.shapes.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.survey_save), style = type.labelLg)
        }

        TextButton(onClick = onDone) {
            Text(
                text = stringResource(R.string.survey_skip),
                style = type.labelMd,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Question(labelRes: Int, value: Int?, label: String, onPick: (Int) -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(labelRes),
            style = type.bodyMd,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        RatingStars(
            value = value,
            onPick = onPick,
            label = label,
            modifier = Modifier.padding(top = BibliariumTheme.spacing.xs),
        )
    }
}

private const val NOTE_HEIGHT = 120
