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
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.channel_description)
                    setShowBadge(false)
                }

            val savedChannel =
                NotificationChannel(
                    CHANNEL_SAVED,
                    getString(R.string.channel_saved_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = getString(R.string.channel_saved_description)
                }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(recordingChannel)
            manager.createNotificationChannel(savedChannel)
        }
    }

    companion object {
        const val CHANNEL_RECORDING = "recora_recording_channel"
        const val CHANNEL_SAVED = "recora_saved_channel"
    }
}
