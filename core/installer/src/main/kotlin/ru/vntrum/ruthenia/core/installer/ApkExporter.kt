/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single file to save: the source and the name in the target folder. */
data class ExportEntry(val file: File, val fileName: String)

/** Failed to write the APK to the selected folder. */
class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Saving downloaded APKs to a folder chosen by the user via the Storage
 * Access Framework. Works directly through [DocumentsContract], without external
 * dependencies: creates a file (or a subfolder for a split build) and copies the bytes.
 */
@Singleton
class ApkExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Copies [entries] into the [treeUriString] tree.
     *
     * A single file is saved to the root of the tree; several (base + config splits) —
     * into the [folderName] subfolder, so the build does not get scattered across files.
     */
    suspend fun export(
        treeUriString: String,
        folderName: String,
        entries: List<ExportEntry>,
    ): Int = withContext(Dispatchers.IO) {
        require(entries.isNotEmpty()) { "No files to export" }
        val resolver = context.contentResolver
        val treeUri = Uri.parse(treeUriString)
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)

        val parent = if (entries.size == 1) {
            rootDocumentUri
        } else {
            ensureFolder(resolver, treeUri, rootDocumentUri, folderName)
        }

        entries.forEach { entry ->
            val documentUri = DocumentsContract.createDocument(
                resolver,
                parent,
                APK_MIME_TYPE,
                entry.fileName,
            ) ?: throw ExportException("Не удалось создать файл «${entry.fileName}»")

            resolver.openOutputStream(documentUri)?.use { output ->
                entry.file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw ExportException("Не удалось открыть «${entry.fileName}» на запись")
        }
        entries.size
    }

    private fun ensureFolder(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentUri: Uri,
        name: String,
    ): Uri {
        val parentDocumentId = DocumentsContract.getDocumentId(parentDocumentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }

        return DocumentsContract.createDocument(
            resolver,
            parentDocumentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw ExportException("Не удалось создать папку «$name»")
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
