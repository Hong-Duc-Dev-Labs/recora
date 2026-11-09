package labs.hongducdev.recora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RecoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel =
                NotificationChannel(
                    CHANNEL_RECORDING,
                    "Screen Recording",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notifications while screen recording is active"
                    setShowBadge(false)
                }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(recordingChannel)
        }
    }

    companion object {
        const val CHANNEL_RECORDING = "recora_recording_channel"
    }
}
