package com.bibliarium.app

import android.content.Context
import androidx.room.Room
import com.bibliarium.app.data.db.BibliariumDatabase
import com.bibliarium.app.data.db.ImportQueueDao
import com.bibliarium.app.data.importer.BatchImporter
import com.bibliarium.app.data.importer.BookImporter
import com.bibliarium.app.data.access.FileAccessProvider
import com.bibliarium.app.data.scan.BookScanner
import com.bibliarium.app.data.scan.ScanMetadataReader
import com.bibliarium.app.data.settings.AppSettings
import com.bibliarium.app.data.settings.ReaderSettingsStore
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.reader.ReaderContentOpener
import com.bibliarium.app.data.store.HighlightStore
import com.bibliarium.app.data.store.LocalBookStore
import com.bibliarium.app.data.store.LocalHighlightStore
import java.io.File
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Сервис-локатор вместо DI-фреймворка (раздел 2 ТЗ). Создаётся один раз в [BibliariumApp].
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val booksDir: File = File(appContext.filesDir, "books").apply { mkdirs() }
    val coversDir: File = File(appContext.filesDir, "covers").apply { mkdirs() }

    private val database: BibliariumDatabase by lazy {
        Room.databaseBuilder(appContext, BibliariumDatabase::class.java, BibliariumDatabase.NAME)
            .addMigrations(BibliariumDatabase.MIGRATION_1_2)
            .build()
    }

    // Readium. HTTP-клиент нужен конструкторам, но сеть не используется:
    // разрешение INTERNET из манифеста удалено.
    private val httpClient by lazy { DefaultHttpClient() }

    private val assetRetriever by lazy {
        AssetRetriever(appContext.contentResolver, httpClient)
    }

    val pdfDocumentFactory: PdfiumDocumentFactory by lazy { PdfiumDocumentFactory(appContext) }

    private val publicationParser by lazy {
        DefaultPublicationParser(
            context = appContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            // Без фабрики PDF Readium вообще не разбирает PDF: parser просто
            // не создаётся. Это адаптер самого Readium, не сторонний движок.
            pdfFactory = pdfDocumentFactory,
        )
    }

    val publicationOpener: PublicationOpener by lazy { PublicationOpener(publicationParser) }

    /** Нужен экрану чтения, чтобы открыть книгу по её файлу. */
    val assetRetrieverForReading: AssetRetriever get() = assetRetriever

    private val bookImporter by lazy {
        BookImporter(
            context = appContext,
            assetRetriever = assetRetriever,
            publicationOpener = publicationOpener,
            booksDir = booksDir,
            coversDir = coversDir,
        )
    }

    val bookStore: BookStore by lazy { LocalBookStore(database.bookDao(), bookImporter) }

    val highlightStore: HighlightStore by lazy { LocalHighlightStore(database.highlightDao()) }

    val importQueueDao: ImportQueueDao by lazy { database.importQueueDao() }

    val settings: AppSettings by lazy { AppSettings(appContext) }

    private val scanMetadataReader by lazy {
        ScanMetadataReader(appContext, assetRetriever, publicationOpener)
    }

    val bookScanner: BookScanner by lazy {
        BookScanner(bookImporter, scanMetadataReader)
    }

    val fileAccessProvider: FileAccessProvider by lazy {
        FileAccessProvider(appContext, settings)
    }

    val batchImporter: BatchImporter by lazy { BatchImporter(appContext, importQueueDao) }

    val readerSettings: ReaderSettingsStore by lazy { ReaderSettingsStore(appContext) }

    val readerContentOpener: ReaderContentOpener by lazy {
        ReaderContentOpener(appContext, assetRetriever, publicationOpener)
    }
}
