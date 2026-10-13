package xendroid.compose

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import xendroid.compose.archive.ContentBusyException
import xendroid.compose.core.StorageAccess
import xendroid.compose.userdata.GameDataView
import xendroid.compose.userdata.UserDataFiles

/**
 * L07: the emulator's user data in the system file manager ("Open user data", and any app the
 * user grants a folder to). A thin adapter: what may be seen and changed, and how, is
 * [UserDataFiles] (inside the root only, caches and bookkeeping hidden, changes under the
 * storage lease). Same class name and authority as the provider it replaces, so document ids
 * and grants already given keep working. "Games by title" gathers each game's data (saves per
 * profile, DLC and updates, its config and patches) in read-only folders whose entries are the
 * real files; and while a game runs or a save/content job holds the lease, everything is shown
 * read-only (changes were refused then; now the file manager does not offer them).
 */
class DocumentsProvider : android.provider.DocumentsProvider() {
    private val files by lazy { UserDataFiles(File(Utils.get_storage_root_path())) { StorageAccess.acquire() } }
    private val closeHandler by lazy { Handler(Looper.getMainLooper()) }
    private val games by lazy { GameDataView(files.root, files::isVisible) }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val root = files.root
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT)
            add(Root.COLUMN_SUMMARY, if (busy()) context!!.getString(R.string.ud_read_only_now) else null)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
            add(Root.COLUMN_DOCUMENT_ID, files.docIdFor(root))
            add(Root.COLUMN_MIME_TYPES, "*/*")
            add(Root.COLUMN_AVAILABLE_BYTES, root.freeSpace)
            add(Root.COLUMN_ICON, R.drawable.zephyr_app_icon)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
            when {
                documentId == GAMES -> includeFolder(cursor, GAMES, context!!.getString(R.string.ud_games))
                documentId.startsWith(GAME) -> titleOf(documentId).let { includeFolder(cursor, documentId, gameName(it, titleNames())) }
                else -> include(cursor, files.fileFor(documentId), busy())
            }
        }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
            val readOnly = busy()
            when {
                parentDocumentId == GAMES -> {
                    val names = titleNames()
                    games.titles().forEach { includeFolder(cursor, GAME + it, gameName(it, names)) }
                }
                parentDocumentId.startsWith(GAME) -> games.entries(titleOf(parentDocumentId)).forEach { entry ->
                    when (entry.kind) {
                        GameDataView.Kind.PROFILE_DATA ->
                            include(cursor, entry.file, readOnly, context!!.getString(R.string.ud_profile_data, entry.owner), gathering = true)
                        GameDataView.Kind.CONSOLE_DATA ->
                            include(cursor, entry.file, readOnly, context!!.getString(R.string.ud_console_data), gathering = true)
                        else -> include(cursor, entry.file, readOnly)
                    }
                }
                else -> {
                    if (parentDocumentId == files.docIdFor(files.root)) includeFolder(cursor, GAMES, context!!.getString(R.string.ud_games))
                    files.children(parentDocumentId).forEach { include(cursor, it, readOnly) }
                }
            }
        }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
            val readOnly = busy()
            files.search(query).forEach { include(cursor, it, readOnly) }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = when {
        documentId == GAMES -> parentDocumentId == files.docIdFor(files.root)
        documentId.startsWith(GAME) -> parentDocumentId == GAMES || parentDocumentId == files.docIdFor(files.root)
        parentDocumentId == GAMES -> runCatching { games.titleOf(files.fileFor(documentId)) != null }.getOrDefault(false)
        parentDocumentId.startsWith(GAME) ->
            runCatching { games.titleOf(files.fileFor(documentId)) == titleOf(parentDocumentId) }.getOrDefault(false)
        else -> files.isChild(parentDocumentId, documentId)
    }

    override fun getDocumentType(documentId: String): String =
        if (documentId == GAMES || documentId.startsWith(GAME)) Document.MIME_TYPE_DIR else typeOf(files.fileFor(documentId))

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val access = ParcelFileDescriptor.parseMode(mode)
        if (access == ParcelFileDescriptor.MODE_READ_ONLY) {
            val file = files.fileFor(documentId)
            if (file.isDirectory) throw FileNotFoundException("A folder cannot be opened")
            return ParcelFileDescriptor.open(file, access)
        }
        // Writing holds the storage lease until the caller closes the file.
        val (file, lease) = guarded { files.openForWriting(documentId) }
        return try {
            ParcelFileDescriptor.open(file, access, closeHandler) { lease.close() }
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
        val file = files.fileFor(documentId)
        if (!typeOf(file).startsWith("image/")) throw FileNotFoundException("No thumbnail")
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0,
            AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String =
        guarded { files.create(parentDocumentId, displayName, directory = mimeType == Document.MIME_TYPE_DIR) }

    override fun renameDocument(documentId: String, displayName: String): String =
        guarded { files.rename(documentId, displayName) }

    override fun deleteDocument(documentId: String) = guarded { files.delete(documentId) }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String =
        guarded { files.copy(sourceDocumentId, targetParentDocumentId) }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String =
        guarded { files.move(sourceDocumentId, targetParentDocumentId) }

    /** The file UI shows FileNotFoundException messages; a running game holds the lease. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ContentBusyException) {
        throw FileNotFoundException("Close the running game first: its data cannot change while it runs")
    } catch (e: FileNotFoundException) {
        throw e
    } catch (e: SecurityException) {
        throw e
    } catch (e: Exception) {
        throw FileNotFoundException(e.message ?: "The change failed")
    }

    /** [readOnly]: the lease is taken (a game runs), so no change is offered. [gathering]: a game's
     *  folder shown in "Games by title" under another name: it keeps its place and name. */
    private fun include(cursor: MatrixCursor, file: File, readOnly: Boolean, name: String = file.name, gathering: Boolean = false) {
        val type = typeOf(file)
        var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
            Document.FLAG_SUPPORTS_MOVE or Document.FLAG_SUPPORTS_COPY
        flags = flags or if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        if (file == files.root) flags = Document.FLAG_DIR_SUPPORTS_CREATE
        if (gathering) flags = Document.FLAG_SUPPORTS_COPY or Document.FLAG_DIR_SUPPORTS_CREATE
        // Read-only: a copy elsewhere still works (the file manager reads the file); nothing here changes.
        if (readOnly) flags = 0
        if (type.startsWith("image/")) flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, files.docIdFor(file))
            add(Document.COLUMN_DISPLAY_NAME, name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, type)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_ICON, R.drawable.zephyr_app_icon)
        }
    }

    /** A gathering folder of "Games by title": nothing can be created, renamed or removed in it. */
    private fun includeFolder(cursor: MatrixCursor, id: String, name: String) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, id)
            add(Document.COLUMN_DISPLAY_NAME, name)
            add(Document.COLUMN_SIZE, null)
            add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            add(Document.COLUMN_LAST_MODIFIED, null)
            add(Document.COLUMN_FLAGS, 0)
            add(Document.COLUMN_ICON, R.drawable.zephyr_app_icon)
        }
    }

    private fun titleOf(documentId: String): String = documentId.removePrefix(GAME).uppercase()
        .takeIf(GameDataView::isGameTitle) ?: throw FileNotFoundException("$documentId not found")

    /** "Halo 3 (4D5307E6)" when a run of the title is on record, else the Title ID. */
    private fun gameName(titleId: String, names: Map<String, String>): String =
        names[titleId]?.let { "$it ($titleId)" } ?: titleId

    private fun titleNames(): Map<String, String> = runCatching {
        xendroid.compose.sessions.SessionRuns.store().runs().filter { it.titleId != null }
            .associate { it.titleId!!.uppercase() to xendroid.compose.data.MissingTitles.nameFromPath(it.gamePath, it.titleId) }
    }.getOrDefault(emptyMap())

    /** A game or a save/content job holds the storage lease right now. */
    private fun busy(): Boolean = runCatching { StorageAccess.acquire().close() }.isFailure

    private fun typeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private companion object {
        const val ROOT = "root"
        /** Not paths (document ids of files are absolute paths), so they never collide. */
        const val GAMES = "xendroid:games"
        const val GAME = "xendroid:game:"
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_MIME_TYPES, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_AVAILABLE_BYTES,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
        )
    }
}
