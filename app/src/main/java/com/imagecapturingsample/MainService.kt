package com.imagecapturingsample

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class MainService : Service() {
    private val imageSet = mutableSetOf<String>()
    private val TAG = MainService::class.java.name

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting main service")
        startServiceInForeground()

        AndroidScreenGrabber({ image ->
            val frame64 = Base64.getEncoder().encodeToString(image)
            imageSet.add(frame64)
            Log.d(TAG, "Number of unique images received - ${imageSet.size}")
        }, this).start()

        return START_STICKY
    }

    private fun startServiceInForeground() {
        createNotificationChannel()

        val pendingIntent: PendingIntent =
            Intent(this, MainService::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification: Notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Image capturing sample")
            .setContentText("Image capturing sample running")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        Log.d(TAG, "Starting main service in foreground")
        startForeground(FOREGROUND_SERVICE_ID, notification)
    }


    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager!!.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "com.imagecapturingsample"
        private const val FOREGROUND_SERVICE_ID = 99
        private val TAG = MainService::class.java.name
    }
}