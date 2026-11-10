package labs.hongducdev.recora.model

enum class AudioSourceType {
    MUTE,
    MIC,
    INTERNAL,
    MIC_AND_INTERNAL,
    ;

    val hasMic: Boolean get() = this == MIC || this == MIC_AND_INTERNAL
    val hasInternal: Boolean get() = this == INTERNAL || this == MIC_AND_INTERNAL

    companion object {
        fun from(
            mic: Boolean,
            internal: Boolean,
        ) = when {
            mic && internal -> MIC_AND_INTERNAL
            mic -> MIC
            internal -> INTERNAL
            else -> MUTE
        }
    }
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
