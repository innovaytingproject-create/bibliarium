package com.bibliarium.app.data.scan

import android.content.Context
import android.net.Uri
import com.bibliarium.app.data.importer.Fb2MetadataReader
import com.bibliarium.app.domain.BookFormat
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener

/**
 * Читает название книги, не копируя файл к себе: EPUB открывает Readium прямо
 * по content://, FB2 разбирается потоком и останавливается на </description>.
 * Ошибки здесь не фатальны — если название не читается, в списке останется
 * имя файла.
 */
class ScanMetadataReader(
    private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
) {

    suspend fun titleOf(uri: Uri, fileName: String, format: BookFormat): String? = when {
        format == BookFormat.EPUB -> epubTitle(uri)
        format == BookFormat.FB2 && fileName.endsWith(".zip", ignoreCase = true) ->
            fb2TitleFromArchive(uri)
        format == BookFormat.FB2 -> fb2Title(uri)
        else -> null
    }

    private suspend fun epubTitle(uri: Uri): String? {
        val url = uri.toAbsoluteUrl() ?: return null
        val asset = assetRetriever.retrieve(url).getOrElse { return null }
        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return null
            }
        return try {
            publication.metadata.title
        } finally {
            publication.close()
        }
    }

    private fun fb2Title(uri: Uri): String? = runCatching {
        Fb2MetadataReader.read { openStream(uri) }.title
    }.getOrNull()

    private fun fb2TitleFromArchive(uri: Uri): String? = runCatching {
        openStream(uri).use { raw ->
            val zip = ZipInputStream(raw.buffered(), Charsets.ISO_8859_1)
            var checked = 0
            var entry = zip.nextEntry
            while (entry != null && checked < MAX_ZIP_ENTRIES) {
                if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                    // Читатель сам закроет поток; запись всё равно дочитывать не нужно.
                    return@runCatching Fb2MetadataReader.read { zip }.title
                }
                checked++
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }.getOrNull()

    private fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть $uri")

    private companion object {
        const val MAX_ZIP_ENTRIES = 16
    }
}
