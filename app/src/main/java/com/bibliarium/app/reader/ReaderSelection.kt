package com.bibliarium.app.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.getSystemService
import com.bibliarium.app.R

/**
 * Меню над выделенным текстом.
 *
 * Системное меню «копировать / выделить всё» здесь не подходит: человеку
 * нужно не просто скопировать кусок, а оставить его в книге — поэтому
 * пункты свои.
 */
class ReaderSelectionMenu(
    private val context: Context,
    private val onHighlight: () -> Unit,
    private val onNote: () -> Unit,
    private val onShare: () -> Unit,
    private val selectedText: () -> String?,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.clear()
        menu.add(Menu.NONE, HIGHLIGHT, Menu.NONE, context.getString(R.string.reader_highlight))
        menu.add(Menu.NONE, NOTE, Menu.NONE, context.getString(R.string.reader_note))
        menu.add(Menu.NONE, COPY, Menu.NONE, context.getString(R.string.reader_copy))
        menu.add(Menu.NONE, SHARE, Menu.NONE, context.getString(R.string.reader_share))
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            HIGHLIGHT -> onHighlight()
            NOTE -> onNote()
            COPY -> copy()
            SHARE -> onShare()
            else -> return false
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit

    private fun copy() {
        val text = selectedText() ?: return
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    }

    private companion object {
        const val HIGHLIGHT = 1
        const val NOTE = 2
        const val COPY = 3
        const val SHARE = 4
    }
}

/** Отправка выделенного текста в другое приложение. */
fun shareText(context: Context, text: String, bookTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "«$text»\n\n$bookTitle")
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.reader_share)))
}
