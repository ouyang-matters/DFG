package io.github.ouyangmatters.rounds.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos live under filesDir/photos/<itemId>/, one folder per item, which keeps
 * browsing and bulk deletion simple. Only the relative path is stored in the
 * database so a backup or restore is not tied to an absolute location.
 */
class PhotoStore(private val context: Context) {

    private val root: File get() = File(context.filesDir, ROOT_DIR)

    fun dirFor(itemId: Long): File = File(root, itemId.toString()).apply { mkdirs() }

    /** Creates an empty file for the camera to write into. */
    fun newPhotoFile(itemId: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(dirFor(itemId), "$stamp.jpg")
    }

    fun relativePath(file: File): String = "$ROOT_DIR/${file.parentFile?.name}/${file.name}"

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    /** A content:// URI the camera app is allowed to write to. */
    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copies a picked image in and returns its relative path. */
    suspend fun importFrom(uri: Uri, itemId: Long): String? = withContext(Dispatchers.IO) {
        val target = newPhotoFile(itemId)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            relativePath(target)
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        absoluteFile(relativePath).delete()
        Unit
    }

    /** Bytes used by one item's photos, shown on the photo screen. */
    suspend fun bytesUsed(itemId: Long): Long = withContext(Dispatchers.IO) {
        dirFor(itemId).listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Deletes files no entry references any more, so a failed delete cannot leave
     * orphans behind. Very recent files are skipped: the camera may still be
     * writing one whose entry has not been saved yet.
     */
    suspend fun pruneOrphans(referenced: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - IN_FLIGHT_GRACE_MILLIS
        root.listFiles()?.forEach { itemDir ->
            itemDir.listFiles()?.forEach { file ->
                if (relativePath(file) !in referenced && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
            if (itemDir.listFiles()?.isEmpty() == true) itemDir.delete()
        }
        Unit
    }

    private companion object {
        const val ROOT_DIR = "photos"
        const val IN_FLIGHT_GRACE_MILLIS = 10 * 60 * 1000L
    }
}
