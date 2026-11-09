package labs.hongducdev.recora.model

enum class AudioSourceType {
    MUTE,
    MIC,
    INTERNAL,
    MIC_AND_INTERNAL,
}

data class VideoResolution(
    val label: String,
    val width: Int,
    val height: Int,
)

data class RecordingConfig(
    val resolutionWidth: Int = 1080,
    val resolutionHeight: Int = 1920,
    val frameRate: Int = 60,
    val bitRate: Int = 12_000_000,
    val audioSource: AudioSourceType = AudioSourceType.MIC,
    val countdownSeconds: Int = 3,
    val enableFloatingControls: Boolean = true,
    val enableFacecam: Boolean = false,
)
