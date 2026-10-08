package uz.notgis.documate

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class DocFile(
    val uri: Uri,
    val name: String,
    val type: FType,
    val size: Long,
    val modified: Long,
    val folder: String,
)

/** Foydalanuvchi tanlagan papkani (SAF) rekursiv skanerlaydi. */
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
}
