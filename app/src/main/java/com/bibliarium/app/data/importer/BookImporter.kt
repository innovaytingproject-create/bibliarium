package com.bibliarium.app.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.ReadingStatus
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener

/**
 * Кладёт файл книги в [booksDir], вытаскивает метаданные и сразу делает
 * миниатюру обложки — оригинал в списки никогда не попадает (раздел 7 ТЗ).
 */
class BookImporter(
    private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    private val booksDir: File,
    private val coversDir: File,
) {

    private data class SourceMetadata(
        val title: String?,
        val author: String?,
        val genre: String?,
        val cover: Bitmap?,
    )

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)
        val extension = displayName?.substringAfterLast('.', "").orEmpty()
        val format = BookFormat.fromExtension(extension)
            ?: return@withContext Result.failure(ImportException(ImportFailure.UNKNOWN_FORMAT))

        if (!format.isSupported) {
            return@withContext Result.failure(ImportException(ImportFailure.UNSUPPORTED_FORMAT))
        }

        val id = UUID.randomUUID().toString()
        val target = File(booksDir, "$id.${format.extension}")

        try {
            copyToStorage(uri, target)

            val metadata = when (format) {
                BookFormat.EPUB -> readWithReadium(target)
                    ?: throw ImportException(ImportFailure.PARSE_FAILED)
                BookFormat.FB2 -> readFb2(target)
                else -> throw ImportException(ImportFailure.UNSUPPORTED_FORMAT)
            }

            val fallbackTitle = displayName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: target.name

            val coverPath = metadata.cover?.let { saveThumbnail(id, it) }

            val book = Book(
                id = id,
                title = metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                author = metadata.author?.takeIf { it.isNotBlank() },
                format = format,
                filePath = target.absolutePath,
                coverPath = coverPath,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progress = 0f,
                locator = null,
                status = ReadingStatus.NOT_STARTED,
                genre = metadata.genre?.takeIf { it.isNotBlank() },
                shelfId = null,
                isFavorite = false,
            )
            Result.success(book)
        } catch (e: ImportException) {
            target.delete()
            Result.failure(e)
        } catch (e: Exception) {
            target.delete()
            Result.failure(ImportException(ImportFailure.STORAGE_FAILED, e))
        }
    }

    private fun copyToStorage(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException(ImportFailure.UNREADABLE_FILE)
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
    }

    /** EPUB читает Readium. Своего парсера нет и не будет. */
    private suspend fun readWithReadium(file: File): SourceMetadata? {
        val asset = assetRetriever.retrieve(file).getOrElse { return null }
        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return null
            }

        return try {
            SourceMetadata(
                title = publication.metadata.title,
                author = publication.metadata.authors.firstOrNull()?.name,
                genre = publication.metadata.subjects.firstOrNull()?.name,
                cover = publication.cover(),
            )
        } finally {
            publication.close()
        }
    }

    private fun readFb2(file: File): SourceMetadata {
        val meta = Fb2MetadataReader.read(file)
        val cover = meta.coverId
            ?.let { Fb2MetadataReader.readBinary(file, it) }
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        return SourceMetadata(
            title = meta.title,
            author = meta.author,
            genre = meta.genre,
            cover = cover,
        )
    }

    private fun saveThumbnail(id: String, cover: Bitmap): String? = runCatching {
        val thumbnail = scaleToFit(cover, THUMBNAIL_MAX_PX)
        val file = File(coversDir, "$id.jpg")
        file.outputStream().use { out ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        }
        if (thumbnail !== cover) thumbnail.recycle()
        file.absolutePath
    }.getOrNull()

    private fun scaleToFit(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        val ratio = maxPx.toFloat() / longest
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }

    private companion object {
        const val THUMBNAIL_MAX_PX = 200
        const val THUMBNAIL_QUALITY = 85
    }
}
