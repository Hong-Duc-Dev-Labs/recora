package labs.hongducdev.recora.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import labs.hongducdev.recora.model.RecordedVideo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoStorageManager(
    private val context: Context,
) {
    fun createVideoOutputUri(): Uri? {
        val resolver = context.contentResolver
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Recora_$timeStamp.mp4"

        val contentValues =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Recora")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    fun completeVideoOutputUri(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
            context.contentResolver.update(uri, contentValues, null, null)
        }
    }

    suspend fun queryRecordedVideos(): List<RecordedVideo> =
        withContext(Dispatchers.IO) {
            val videos = mutableListOf<RecordedVideo>()
            val projection =
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                )

            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("Recora_%")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver
                    .query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder,
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            val name = cursor.getString(nameColumn) ?: "Video"
                            val size = cursor.getLong(sizeColumn)
                            val duration = cursor.getLong(durationColumn)
                            val dateAdded = cursor.getLong(dateColumn)
                            val width = cursor.getInt(widthColumn)
                            val height = cursor.getInt(heightColumn)

                            videos.add(
                                RecordedVideo(
                                    id = id,
                                    uri = contentUri,
                                    displayName = name,
                                    sizeBytes = size,
                                    durationMillis = duration,
                                    dateAdded = dateAdded,
                                    width = width,
                                    height = height,
                                ),
                            )
                        }
                    }
            } catch (_: Exception) {
                // Log or ignore query exception
            }
            videos
        }

    fun deleteVideo(uri: Uri): Boolean =
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }

    fun createPlayIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun createShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
