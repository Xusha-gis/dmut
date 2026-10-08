package uz.notgis.documate

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

data class DocFile(
    val uri: Uri,
    val name: String,
    val type: FType,
    val size: Long,
    val modified: Long,
    val folder: String,
)

/** SAF papka skaneri + MediaStore orqali barcha fayllar skaneri. */
object FileScanner {
    private const val LIMIT = 5000

    fun scan(context: Context, treeUri: Uri): List<DocFile> {
        val out = ArrayList<DocFile>()
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val pending = ArrayList<Pair<String, String>>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri) to "")

        while (pending.isNotEmpty() && out.size < LIMIT) {
            val (parentId, parentPath) = pending.removeAt(pending.size - 1)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) pending.add(id to "$parentPath/$name")
                    } else {
                        val type = typeOf(name) ?: continue
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)
                        out.add(
                            DocFile(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                name = name,
                                type = type,
                                size = size,
                                modified = modified,
                                folder = parentPath.ifEmpty { "/" },
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * Qurilmadagi BARCHA hujjatlarni MediaStore orqali o'qiydi.
     * MANAGE_EXTERNAL_STORAGE (All-files) ruxsati bo'lganda ishlaydi.
     */
    fun scanAllMedia(context: Context): List<DocFile> {
        val out = ArrayList<DocFile>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATA,
        )
        val exts = listOf(
            "pdf", "doc", "docx", "rtf", "odt",
            "xls", "xlsx", "csv", "ods",
            "ppt", "pptx", "odp",
            "txt", "md", "log",
        )
        val like = exts.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" }
        val args = exts.map { "%.$it" }.toTypedArray()
        try {
            context.contentResolver.query(
                collection, projection, "($like)", args,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeI = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateI = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val relI = c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val dataI = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext() && out.size < LIMIT * 2) {
                    val name = try {
                        c.getString(nameI)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    val type = typeOf(name) ?: continue
                    val id = c.getLong(idI)
                    val size = if (sizeI >= 0 && !c.isNull(sizeI)) c.getLong(sizeI) else 0L
                    val modified = if (dateI >= 0 && !c.isNull(dateI)) c.getLong(dateI) * 1000L else 0L
                    val folder = when {
                        relI >= 0 && !c.isNull(relI) -> c.getString(relI) ?: "/"
                        dataI >= 0 && !c.isNull(dataI) -> {
                            val p = c.getString(dataI) ?: ""
                            p.substringBeforeLast('/', "/").substringAfterLast('/', "/")
                        }
                        else -> "/"
                    }
                    out.add(
                        DocFile(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = name,
                            type = type,
                            size = size,
                            modified = modified,
                            folder = folder.trim().ifEmpty { "/" },
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            throw e
        }
        return out
    }
}
