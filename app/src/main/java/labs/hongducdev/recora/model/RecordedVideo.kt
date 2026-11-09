package labs.hongducdev.recora.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMillis: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMillis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    /** Recording time in the device locale, e.g. 09/11/2026 22:03. */
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateAdded * 1000L))

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}
