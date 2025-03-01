package com.wa2c.android.cifsdocumentsprovider.presentation.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.wa2c.android.cifsdocumentsprovider.common.utils.logD
import com.wa2c.android.cifsdocumentsprovider.common.utils.logE
import com.wa2c.android.cifsdocumentsprovider.common.utils.mimeType
import com.wa2c.android.cifsdocumentsprovider.common.values.AccessMode
import com.wa2c.android.cifsdocumentsprovider.common.values.URI_AUTHORITY
import com.wa2c.android.cifsdocumentsprovider.domain.model.CifsFile
import com.wa2c.android.cifsdocumentsprovider.domain.repository.CifsRepository
import com.wa2c.android.cifsdocumentsprovider.presentation.PresentationModule
import com.wa2c.android.cifsdocumentsprovider.presentation.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import java.nio.file.Paths

/**
 * CIFS DocumentsProvider
 */
class CifsDocumentsProvider : DocumentsProvider() {

    /** Context */
    private val providerContext: Context by lazy { context!! }
    /** Storage Manager */
    private val storageManager: StorageManager by lazy { providerContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager }
    /** Cifs Repository */
    private val cifsRepository: CifsRepository by lazy {
        val clazz = PresentationModule.DocumentsProviderEntryPoint::class.java
        val hiltEntryPoint = EntryPointAccessors.fromApplication(providerContext, clazz)
        hiltEntryPoint.getCifsRepository()
    }

    /** File handler */
    private val fileHandler: Handler by lazy {
        HandlerThread(this.javaClass.simpleName)
            .apply { start() }
            .let { Handler(it.looper) }
    }

    /**
     * Run on fileHandler
     */
    private fun <T> runOnFileHandler(function: suspend () -> T): T {
        return runBlocking(fileHandler.asCoroutineDispatcher()) {
            try {
                function()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val useAsLocal = runBlocking { cifsRepository.useAsLocalFlow.first() }
        // Add root columns
        return MatrixCursor(projection.toRootProjection()).also {
            it.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, URI_AUTHORITY)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(DocumentsContract.Root.COLUMN_TITLE, providerContext.getString(R.string.app_name))
                add(DocumentsContract.Root.COLUMN_SUMMARY, providerContext.getString(R.string.app_summary))
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, Int.MAX_VALUE)
                add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                            if (useAsLocal) DocumentsContract.Root.FLAG_LOCAL_ONLY else 0
                )
            }
        }
    }

    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        logD("queryDocument: documentId=$documentId")
        val cursor = MatrixCursor(projection.toProjection())
        if (documentId.isRoot()) {
            // Root
            includeRoot(cursor)
        } else {
            // File / Directory
            runBlocking {
                documentId?.let {
                    val uri = getCifsUri(it)
                    val file = try {
                        cifsRepository.getFile(uri)
                    } catch (e: Exception) {
                        logE(e)
                        null
                    } ?: return@let
                    includeFile(cursor, file)
                }
            }
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        logD("queryChildDocuments: parentDocumentId=$parentDocumentId")
        val cursor = MatrixCursor(projection.toProjection())
        if (parentDocumentId.isRoot()) {
            runBlocking {
                cifsRepository.loadConnection().forEach { connection ->
                    try {
                        val file = cifsRepository.getFile(null, connection) ?: return@forEach
                        includeFile(cursor, file, connection.name)
                    } catch (e: Exception) {
                        logE(e)
                    }
                }
            }
        } else {
            runBlocking {
                val uri = getCifsDirectoryUri(parentDocumentId!!)
                cifsRepository.getFileChildren(uri).forEach { file ->
                    try {
                        includeFile(cursor, file)
                    } catch (e: Exception) {
                        logE(e)
                    }
                }
            }
        }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        val parent = if (parentDocumentId.isRoot()) "/" else parentDocumentId ?: return false
        val child = documentId ?: return false
        return child.indexOf(parent) == 0
    }

    override fun getDocumentType(documentId: String?): String {
        return documentId.mimeType
    }

    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        return null
    }

    override fun openDocument(
        documentId: String?,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        logD("openDocument: documentId=$documentId")
        val accessMode = AccessMode.fromSafMode(mode)
        return runOnFileHandler {
            val uri = documentId?.let { getCifsFileUri(it) } ?: return@runOnFileHandler null
            cifsRepository.getCallback(uri, accessMode)
        }?.let { callback ->
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(accessMode.safMode),
                callback,
                fileHandler
            )
        } ?: let {
            throw OperationCanceledException()
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String?,
        displayName: String
    ): String? {
        logD("createDocument: parentDocumentId=$parentDocumentId, displayName=$displayName")
        return runOnFileHandler {
            val documentId = Paths.get(parentDocumentId, displayName).toString()
            val uri = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                getCifsDirectoryUri(documentId)
            } else {
                getCifsFileUri(documentId)
            }
            cifsRepository.createFile(uri, mimeType)?.documentId
        }
    }

    override fun deleteDocument(documentId: String?) {
        logD("deleteDocument: documentId=$documentId")
        if (documentId == null) throw OperationCanceledException()
        runOnFileHandler {
             cifsRepository.deleteFile(getCifsUri(documentId))
        }
    }

    override fun renameDocument(documentId: String?, displayName: String?): String? {
        logD("renameDocument: documentId=$documentId, displayName=$displayName")
        if (documentId == null || displayName == null) return null
        return runOnFileHandler {
            cifsRepository.renameFile(getCifsFileUri(documentId), displayName)?.documentId
        }
    }

    override fun copyDocument(sourceDocumentId: String?, targetParentDocumentId: String?): String? {
        logD("copyDocument: sourceDocumentId=$sourceDocumentId, targetParentDocumentId=$targetParentDocumentId")
        if (sourceDocumentId == null || targetParentDocumentId == null) return null
       return runOnFileHandler {
            cifsRepository.copyFile(getCifsFileUri(sourceDocumentId), getCifsFileUri(targetParentDocumentId))?.documentId
        }
    }

    override fun moveDocument(
        sourceDocumentId: String?,
        sourceParentDocumentId: String?,
        targetParentDocumentId: String?
    ): String? {
        logD("moveDocument: sourceDocumentId=$sourceDocumentId, targetParentDocumentId=$targetParentDocumentId")
        if (sourceDocumentId == null || targetParentDocumentId == null) return null
        return runOnFileHandler {
            cifsRepository.moveFile(getCifsFileUri(sourceDocumentId), getCifsFileUri(targetParentDocumentId))?.documentId
        }
    }

    override fun removeDocument(documentId: String?, parentDocumentId: String?) {
        logD("removeDocument: documentId=$documentId")
        deleteDocument(documentId)
    }

    override fun shutdown() {
        logD("shutdown")
        runOnFileHandler { cifsRepository.closeAllSessions() }
        fileHandler.looper.quit()
    }

    private fun includeRoot(cursor: MatrixCursor) {
        cursor.newRow().let { row ->
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "/")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
        }
    }

    private fun includeFile(cursor: MatrixCursor, file: CifsFile?, name: String? = null) {
        cursor.newRow().let { row ->
            when {
                file == null -> {
                    // Error
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ERROR_DOCUMENT_ID)
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, providerContext.getString(R.string.provider_error_message))
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "*/*")
                    row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT)
                }
                file.isDirectory -> {
                    // Directory
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.documentId)
                    row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name ?: file.name)
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                                DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED or
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                                DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                DocumentsContract.Document.FLAG_SUPPORTS_REMOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    )
                }
                else -> {
                    // File
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.documentId)
                    row.add(DocumentsContract.Document.COLUMN_SIZE, file.size)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name ?: file.name)
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified)
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.name.mimeType)
                    row.add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                                DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                                DocumentsContract.Document.FLAG_SUPPORTS_REMOVE or
                                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    )
                }
            }
        }
    }

    private fun getCifsDirectoryUri(documentId: String): String {
        val uri = "smb://$documentId"
        return uri + (if (documentId.last() == '/') "" else '/')
    }

    private fun getCifsFileUri(documentId: String): String {
        return "smb://${documentId.trim('/')}"
    }

    private fun getCifsUri(documentId: String): String {
        return "smb://${documentId}"
    }

    private fun Array<String>?.toRootProjection(): Array<String> {
        return if (this.isNullOrEmpty()) {
            DEFAULT_ROOT_PROJECTION
        } else {
            this
        }
    }

    private fun Array<String>?.toProjection(): Array<String> {
        return if (this.isNullOrEmpty()) {
            DEFAULT_DOCUMENT_PROJECTION
        } else {
            this
        }
    }

    /**
     * True if the document id is root.
     */
    private fun String?.isRoot(): Boolean {
        return (this.isNullOrEmpty() || this == ROOT_DOCUMENT_ID)
    }

    companion object {

        private const val ROOT_DOCUMENT_ID = "/"
        private const val ERROR_DOCUMENT_ID = "////"

        private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }


}
