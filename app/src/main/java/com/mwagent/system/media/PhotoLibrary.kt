package com.mwagent.system.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class Photo(val id: Long, val uri: Uri, val name: String, val album: String, val takenAt: Long, val width: Int, val height: Int)

data class PhotoQuery(val from: Long? = null, val to: Long? = null, val album: String? = null, val name: String? = null)

class PhotoLibrary(private val context: Context) {
    val canRead: Boolean
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    suspend fun find(query: PhotoQuery, offset: Int, limit: Int): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val result = mutableListOf<Photo>()
        var skipped = 0
        var scanned = 0
        context.contentResolver.query(COLLECTION, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            while (cursor.moveToNext() && result.size < limit && scanned++ < MAX_SCAN) {
                val id = cursor.getLong(0)
                val photo = Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(COLLECTION, id),
                    name = cursor.getString(1).orEmpty(),
                    album = cursor.getString(2).orEmpty(),
                    takenAt = cursor.getLong(3).takeIf { it > 0 } ?: (cursor.getLong(4) * MS_PER_SECOND),
                    width = cursor.getInt(5),
                    height = cursor.getInt(6),
                )
                if (!photo.fits(query)) continue
                if (skipped < offset) skipped++ else result += photo
            }
        }
        result
    }

    suspend fun get(id: Long): Photo? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(COLLECTION, id)
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) Photo(id, uri, cursor.getString(0).orEmpty(), "", 0L, 0, 0) else null
        }
    }

    suspend fun thumbnail(photo: Photo, side: Int): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.loadThumbnail(photo.uri, Size(side, side), null)
        try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun Photo.fits(query: PhotoQuery): Boolean =
        (query.from == null || takenAt >= query.from) &&
            (query.to == null || takenAt < query.to) &&
            (query.album == null || album.contains(query.album, ignoreCase = true)) &&
            (query.name == null || name.contains(query.name, ignoreCase = true))

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val COLLECTION: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        const val MAX_SCAN = 20_000
        const val MS_PER_SECOND = 1_000L
        const val JPEG_QUALITY = 80
    }
}
