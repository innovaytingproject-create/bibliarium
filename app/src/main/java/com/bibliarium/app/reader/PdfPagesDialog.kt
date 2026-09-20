package com.bibliarium.app.reader

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bibliarium.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Сетка страниц PDF — то, что показывается вместо оглавления, когда закладок
 * в книге нет.
 *
 * Пустое окно «оглавление отсутствует» не помогает ничем: в сканах закладок не
 * бывает в принципе, а листать пятисотстраничный документ по одной странице —
 * это не навигация. Поэтому здесь страницы видно в лицо, с номерами.
 */
class PdfPagesDialog(
    private val activity: AppCompatActivity,
    private val thumbnails: PdfPageThumbnails,
    private val currentPage: Int,
    private val onPick: (Int) -> Unit,
) {

    fun show() {
        if (thumbnails.pageCount <= 0) {
            AlertDialog.Builder(activity)
                .setMessage(R.string.reader_pages_empty)
                .setPositiveButton(R.string.reader_close, null)
                .show()
            return
        }

        val grid = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_pdf_pages, null) as GridView
        grid.adapter = PagesAdapter(activity, thumbnails, activity.lifecycleScope)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.reader_pages)
            .setView(grid)
            .setNegativeButton(R.string.reader_close, null)
            .create()

        grid.setOnItemClickListener { _, _, position, _ ->
            onPick(position + 1)
            dialog.dismiss()
        }

        dialog.show()
        // Открываемся там, где человек сейчас читает, а не в начале книги.
        grid.post { grid.setSelection((currentPage - 1).coerceAtLeast(0)) }
    }
}

private class PagesAdapter(
    private val context: Context,
    private val thumbnails: PdfPageThumbnails,
    private val scope: CoroutineScope,
) : BaseAdapter() {

    private val thumbWidth =
        context.resources.getDimensionPixelSize(R.dimen.pdf_page_thumb_width)

    override fun getCount(): Int = thumbnails.pageCount

    override fun getItem(position: Int): Any = position + 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_pdf_page, parent, false)

        val image = view.findViewById<ImageView>(R.id.pdf_page_image)
        val number = view.findViewById<TextView>(R.id.pdf_page_number)
        val page = position + 1

        number.text = page.toString()
        // Клетки переиспользуются. Отметка нужна, чтобы дорисованная миниатюра
        // не попала в клетку, которая уже показывает другую страницу.
        image.tag = page

        val ready = thumbnails.cached(page)
        image.setImageBitmap(ready)
        if (ready == null) {
            scope.launch {
                val bitmap = thumbnails.render(page, thumbWidth)
                if (bitmap != null && image.tag == page) {
                    image.setImageBitmap(bitmap)
                }
            }
        }

        return view
    }
}
