package com.bibliarium.app.ui.scan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.data.importer.BatchProgress
import com.bibliarium.app.data.importer.ImportFailure
import com.bibliarium.app.data.scan.ScanPhase
import com.bibliarium.app.data.scan.ScanUpdate
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.domain.FoundBookState
import com.bibliarium.app.ui.theme.BibliariumTheme

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val context = LocalContext.current

    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Без persistable-разрешения доступ пропадёт после перезапуска,
            // и фоновый импорт уже не сможет прочитать файлы.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.onRootChosen(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.margin),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.lg, bottom = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.scan_title),
                    style = type.displayLg,
                    color = colors.text,
                )
                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(R.string.common_back),
                        style = type.labelLg,
                        color = colors.textSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            when (state.stage) {
                ScanStage.NEED_ROOT -> ChooseRoot(onChoose = { treePicker.launch(null) })

                ScanStage.READY -> Ready(
                    onStart = viewModel::startScan,
                    onChangeFolder = { treePicker.launch(null) },
                )

                ScanStage.SCANNING -> Scanning(
                    progress = state.progress,
                    onCancel = viewModel::cancelScan,
                )

                ScanStage.RESULTS -> Results(
                    state = state,
                    onToggle = viewModel::toggle,
                    onToggleAll = viewModel::toggleSelectAll,
                    onOnlyNewChange = viewModel::setOnlyNew,
                    onImport = viewModel::importSelected,
                    onRepeat = viewModel::startScan,
                    onChangeFolder = { treePicker.launch(null) },
                )

                ScanStage.IMPORTING -> Importing(
                    batch = state.batch,
                    onDone = viewModel::acknowledgeImport,
                )
            }
        }
    }
}

@Composable
private fun ChooseRoot(onChoose: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = Modifier.padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.scan_need_root_title),
            style = type.headlineMd,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.scan_need_root_hint),
            style = type.bodyMd,
            color = colors.textSecondary,
        )
        PrimaryButton(text = stringResource(R.string.scan_choose_folder), onClick = onChoose)
    }
}

@Composable
private fun Ready(onStart: () -> Unit, onChangeFolder: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = Modifier.padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        PrimaryButton(text = stringResource(R.string.scan_start), onClick = onStart)
        TextButton(onClick = onChangeFolder) {
            Text(
                text = stringResource(R.string.scan_change_folder),
                style = type.labelLg,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Scanning(progress: ScanUpdate.Progress?, onCancel: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = Modifier.padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            Text(
                text = progressText(progress),
                style = type.bodyMd,
                color = colors.text,
            )
        }

        progress?.currentFolder?.let { folder ->
            Text(
                text = folder,
                style = type.bodySm,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.scan_cancel),
                style = type.labelLg,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun progressText(progress: ScanUpdate.Progress?): String {
    if (progress == null) {
        return stringResource(R.string.scan_progress_walking, 0, 0)
    }
    return when (progress.phase) {
        ScanPhase.WALKING -> stringResource(
            R.string.scan_progress_walking,
            progress.foldersScanned,
            progress.booksFound,
        )
        ScanPhase.INSPECTING_ARCHIVES -> stringResource(
            R.string.scan_progress_archives,
            progress.processed,
            progress.total,
        )
        ScanPhase.MATCHING -> stringResource(
            R.string.scan_progress_matching,
            progress.processed,
            progress.total,
        )
        ScanPhase.READING_TITLES -> stringResource(
            R.string.scan_progress_titles,
            progress.processed,
            progress.total,
        )
    }
}

@Composable
private fun Results(
    state: ScanUiState,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onOnlyNewChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onRepeat: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    if (state.books.isEmpty()) {
        EmptyResult(
            title = stringResource(R.string.scan_nothing_title),
            hint = stringResource(R.string.scan_nothing_hint),
            onRepeat = onRepeat,
            onChangeFolder = onChangeFolder,
        )
        return
    }

    if (state.visibleBooks.isEmpty()) {
        EmptyResult(
            title = stringResource(R.string.scan_all_added_title),
            hint = stringResource(R.string.scan_all_added_hint),
            onRepeat = onRepeat,
            onChangeFolder = onChangeFolder,
            onShowAll = { onOnlyNewChange(false) },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.scan_selected,
                    state.selected.size,
                    state.selectableCount,
                ),
                style = type.labelLg,
                color = colors.text,
            )
            TextButton(onClick = onToggleAll) {
                Text(
                    text = if (state.allSelected) {
                        stringResource(R.string.scan_clear_selection)
                    } else {
                        stringResource(R.string.scan_select_all)
                    },
                    style = type.labelMd,
                    color = colors.accent,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.scan_only_new),
                    style = type.labelMd,
                    color = colors.text,
                )
                if (state.onlyNew && state.alreadyAddedCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.scan_hidden_added,
                            state.alreadyAddedCount,
                        ),
                        style = type.labelSm,
                        color = colors.textSecondary,
                    )
                }
            }
            Switch(checked = state.onlyNew, onCheckedChange = onOnlyNewChange)
        }

        HorizontalDivider(thickness = 1.dp, color = colors.line)

        val grouped = state.visibleBooks.groupBy { it.folder }

        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (folder, books) ->
                item(key = "folder:$folder") {
                    Text(
                        text = folder.ifEmpty { stringResource(R.string.scan_root_folder) },
                        style = type.labelSm,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceRecessed)
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items(books, key = { it.uri }) { book ->
                    FoundBookRow(
                        book = book,
                        checked = book.uri in state.selected,
                        onToggle = { onToggle(book.uri) },
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.line)
                }
            }
        }

        Column(modifier = Modifier.padding(vertical = spacing.sm)) {
            PrimaryButton(
                text = stringResource(R.string.scan_add_selected),
                onClick = onImport,
                enabled = state.selected.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun EmptyResult(
    title: String,
    hint: String,
    onRepeat: () -> Unit,
    onChangeFolder: () -> Unit,
    onShowAll: (() -> Unit)? = null,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = Modifier.padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(text = title, style = type.headlineMd, color = colors.text)
        Text(text = hint, style = type.bodyMd, color = colors.textSecondary)

        if (onShowAll != null) {
            TextButton(onClick = onShowAll) {
                Text(
                    text = stringResource(R.string.scan_show_all),
                    style = type.labelLg,
                    color = colors.accent,
                )
            }
        }
        TextButton(onClick = onRepeat) {
            Text(
                text = stringResource(R.string.scan_repeat),
                style = type.labelLg,
                color = colors.accent,
            )
        }
        TextButton(onClick = onChangeFolder) {
            Text(
                text = stringResource(R.string.scan_change_folder),
                style = type.labelLg,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FoundBookRow(
    book: FoundBook,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    val enabled = book.selectable
    val primaryColor = if (enabled) colors.text else colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = null, enabled = enabled)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title ?: book.fileName,
                style = type.headlineSm,
                color = primaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.title != null) {
                Text(
                    text = book.fileName,
                    style = type.bodySm,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val stateLabel = when (book.state) {
                FoundBookState.ALREADY_ADDED -> stringResource(R.string.scan_already_added)
                FoundBookState.NOT_SUPPORTED_YET -> stringResource(R.string.scan_not_supported_yet)
                FoundBookState.NEW -> null
            }
            Text(
                text = listOfNotNull(
                    book.format.name,
                    formatSize(book.sizeBytes),
                    stateLabel,
                ).joinToString(" · "),
                style = type.labelSm,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Importing(batch: BatchProgress?, onDone: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    var showProblems by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (batch == null || !batch.finished) {
            Text(
                text = stringResource(
                    R.string.batch_progress,
                    batch?.processed ?: 0,
                    batch?.total ?: 0,
                ),
                style = type.headlineMd,
                color = colors.text,
            )
            LinearProgressIndicator(
                progress = {
                    val total = batch?.total ?: 0
                    if (total == 0) 0f else (batch?.processed ?: 0).toFloat() / total
                },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.surfaceRecessed,
            )
            Text(
                text = stringResource(R.string.batch_background_hint),
                style = type.bodySm,
                color = colors.textSecondary,
            )
            return@Column
        }

        Text(
            text = if (batch.failed == 0) {
                stringResource(R.string.batch_done_clean, batch.done)
            } else {
                stringResource(R.string.batch_done_with_errors, batch.done, batch.failed)
            },
            style = type.headlineMd,
            color = colors.text,
        )

        if (batch.failed > 0) {
            TextButton(onClick = { showProblems = !showProblems }) {
                Text(
                    text = if (showProblems) {
                        stringResource(R.string.batch_hide_problems)
                    } else {
                        stringResource(R.string.batch_show_problems)
                    },
                    style = type.labelLg,
                    color = colors.accent,
                )
            }

            if (showProblems) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(batch.failures.size, key = { index -> batch.failures[index].uri }) { index ->
                        val failure = batch.failures[index]
                        Column(modifier = Modifier.padding(vertical = spacing.xs)) {
                            Text(
                                text = failure.name,
                                style = type.bodyMd,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(failure.failure.messageRes()),
                                style = type.bodySm,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        PrimaryButton(text = stringResource(R.string.batch_close), onClick = onDone)
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(BibliariumTheme.shapes.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.text,
            contentColor = colors.surface,
            disabledContainerColor = colors.surfaceRecessed,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Text(text = text, style = type.labelLg)
    }
}

@Composable
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.size_kb, (bytes / 1024).toString())
    else -> stringResource(
        R.string.size_mb,
        String.format("%.1f", bytes / (1024.0 * 1024.0)),
    )
}

private fun ImportFailure?.messageRes(): Int = when (this) {
    ImportFailure.UNKNOWN_FORMAT -> R.string.import_error_unknown_format
    ImportFailure.UNSUPPORTED_FORMAT -> R.string.import_error_unsupported_format
    ImportFailure.UNREADABLE_FILE -> R.string.import_error_unreadable
    ImportFailure.PARSE_FAILED -> R.string.import_error_parse
    ImportFailure.STORAGE_FAILED, null -> R.string.import_error_storage
}
