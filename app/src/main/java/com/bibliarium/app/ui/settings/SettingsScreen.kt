package com.bibliarium.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.ui.TestTags
import com.bibliarium.app.ui.theme.BibliariumTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val context = LocalContext.current

    // Разрешение выдают и отзывают в системных настройках, в другом приложении.
    // Поэтому перечитываем состояние каждый раз, когда экран снова виден.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> viewModel.onFolderPicked(uri) }

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
                    text = stringResource(R.string.settings_title),
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.allFilesSupported) {
                    item(key = "full-access") {
                        Column(modifier = Modifier.padding(vertical = spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_full_access),
                                    style = type.headlineSm,
                                    color = colors.text,
                                )
                                Switch(
                                    modifier = Modifier.testTag(TestTags.FULL_ACCESS_SWITCH),
                                    checked = state.allFilesGranted,
                                    onCheckedChange = {
                                        viewModel.allFilesAccessIntent()
                                            ?.let(context::startActivity)
                                    },
                                )
                            }
                            Text(
                                text = if (state.allFilesGranted) {
                                    stringResource(R.string.settings_full_access_on)
                                } else {
                                    stringResource(R.string.settings_full_access_off)
                                },
                                style = type.bodySm,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                            Text(
                                text = stringResource(R.string.settings_full_access_note),
                                style = type.bodySm,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                    }
                }

                item(key = "folders-header") {
                    Column(modifier = Modifier.padding(top = spacing.md, bottom = spacing.sm)) {
                        Text(
                            text = stringResource(R.string.settings_folders),
                            style = type.headlineSm,
                            color = colors.text,
                        )
                        Text(
                            text = if (state.allFilesGranted) {
                                stringResource(R.string.settings_folders_not_needed)
                            } else {
                                stringResource(R.string.settings_folders_hint)
                            },
                            style = type.bodySm,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = spacing.xs),
                        )

                        state.pickFailure?.let { failure ->
                            Text(
                                text = when (failure) {
                                    FolderPickFailure.BLOCKED_BY_SYSTEM ->
                                        stringResource(R.string.settings_pick_blocked)
                                    FolderPickFailure.CANCELLED ->
                                        stringResource(R.string.settings_pick_cancelled)
                                },
                                style = type.bodySm,
                                color = colors.accent,
                                modifier = Modifier.padding(top = spacing.sm),
                            )
                        }
                    }
                }

                if (state.roots.isEmpty()) {
                    item(key = "folders-empty") {
                        Text(
                            text = stringResource(R.string.settings_folders_empty),
                            style = type.bodyMd,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = spacing.sm),
                        )
                    }
                } else {
                    items(state.roots.size, key = { index -> state.roots[index].uri }) { index ->
                        val root = state.roots[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = root.label,
                                style = type.bodyMd,
                                color = colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = spacing.sm),
                            )
                            TextButton(onClick = { viewModel.removeRoot(root.uri) }) {
                                Text(
                                    text = stringResource(R.string.settings_remove_folder),
                                    style = type.labelMd,
                                    color = colors.accent,
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                    }
                }

                item(key = "add-folder") {
                    TextButton(
                        onClick = {
                            viewModel.consumePickFailure()
                            folderPicker.launch(null)
                        },
                        modifier = Modifier.padding(vertical = spacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_add_folder),
                            style = type.labelLg,
                            color = colors.accent,
                        )
                    }
                }

                item(key = "restrictions") {
                    Text(
                        text = stringResource(R.string.settings_restrictions),
                        style = type.bodySm,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = spacing.xl),
                    )
                }
            }
        }
    }
}
