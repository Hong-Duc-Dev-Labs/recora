package labs.hongducdev.recora.utils

import android.content.Context
import android.content.SharedPreferences
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.model.RecordingConfig

class PreferenceManager(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("recora_preferences", Context.MODE_PRIVATE)

    var resolutionWidth: Int
        get() = prefs.getInt(KEY_RES_WIDTH, 1080)
        set(value) = prefs.edit().putInt(KEY_RES_WIDTH, value).apply()

    var resolutionHeight: Int
        get() = prefs.getInt(KEY_RES_HEIGHT, 1920)
        set(value) = prefs.edit().putInt(KEY_RES_HEIGHT, value).apply()

    var frameRate: Int
        get() = prefs.getInt(KEY_FRAME_RATE, 60)
        set(value) = prefs.edit().putInt(KEY_FRAME_RATE, value).apply()

    var bitRate: Int
        get() = prefs.getInt(KEY_BIT_RATE, 12_000_000)
        set(value) = prefs.edit().putInt(KEY_BIT_RATE, value).apply()

    var audioSource: AudioSourceType
        get() {
            val name = prefs.getString(KEY_AUDIO_SOURCE, AudioSourceType.MIC.name)
            return try {
                AudioSourceType.valueOf(name ?: AudioSourceType.MIC.name)
            } catch (_: Exception) {
                AudioSourceType.MIC
            }
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value.name).apply()

    var countdownSeconds: Int
        get() = prefs.getInt(KEY_COUNTDOWN, 3)
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN, value).apply()

    var enableFloatingControls: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_CONTROLS, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_CONTROLS, value).apply()

    var pitchBlackTheme: Boolean
        get() = prefs.getBoolean(KEY_PITCH_BLACK, true)
        set(value) = prefs.edit().putBoolean(KEY_PITCH_BLACK, value).apply()

    var appHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
            HapticUtil.isAppHapticsEnabled.value = value
        }

    fun getRecordingConfig(): RecordingConfig =
        RecordingConfig(
            resolutionWidth = resolutionWidth,
            resolutionHeight = resolutionHeight,
            frameRate = frameRate,
            bitRate = bitRate,
            audioSource = audioSource,
            countdownSeconds = countdownSeconds,
            enableFloatingControls = enableFloatingControls,
            enableFacecam = false,
        )

    companion object {
        private const val KEY_RES_WIDTH = "resolution_width"
        private const val KEY_RES_HEIGHT = "resolution_height"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_BIT_RATE = "bit_rate"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_COUNTDOWN = "countdown_seconds"
        private const val KEY_FLOATING_CONTROLS = "floating_controls"
        private const val KEY_PITCH_BLACK = "pitch_black"
        private const val KEY_HAPTICS = "app_haptics"
    }
}
