package labs.hongducdev.recora.model

import java.util.Locale

enum class RecordingStatus {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    STOPPED,
}

data class RecordingSessionState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val durationSeconds: Long = 0,
    val countdownRemaining: Int = 0,
    val outputUriString: String? = null,
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
}
