package com.bibliarium.app.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bibliarium.app.R
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Развилка между поиском по телефону и обычным системным выбором файла.
 * Нужна потому, что человек чаще всего не помнит, в какой папке лежат книги.
 */
@Composable
fun AddBookScreen(
    onScan: () -> Unit,
    onFilePicked: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(onFilePicked) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.margin),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.add_title),
                style = type.displayLg,
                color = colors.text,
                modifier = Modifier.padding(top = spacing.lg, bottom = spacing.sm),
            )

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            ChoiceCard(
                title = stringResource(R.string.add_scan),
                hint = stringResource(R.string.add_scan_hint),
                onClick = onScan,
            )

            ChoiceCard(
                title = stringResource(R.string.add_manual),
                hint = stringResource(R.string.add_manual_hint),
                onClick = { picker.launch(arrayOf("*/*")) },
            )

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.common_back),
                    style = type.labelLg,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val shape = RoundedCornerShape(BibliariumTheme.shapes.card)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .clickable(onClick = onClick)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text = title, style = type.headlineSm, color = colors.text)
        Text(text = hint, style = type.bodySm, color = colors.textSecondary)
    }
}
