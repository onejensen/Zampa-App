package com.sozolab.zampa.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sozolab.zampa.MainActivity
import com.sozolab.zampa.R
import com.sozolab.zampa.ZampaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZampaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val firebaseService = FirebaseService()
        CoroutineScope(Dispatchers.IO).launch {
            firebaseService.registerDeviceToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        val menuId = remoteMessage.data["menuId"]

        showNotification(title, body, menuId)
    }

    private fun showNotification(title: String?, body: String?, menuId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            menuId?.let { putExtra("menuId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // El canal ya fue creado por ZampaApp.onCreate() con el sonido custom.
        // Aquí solo construimos la notificación; setSound() local por si la
        // versión pre-O ignora el canal.
        val soundUri = Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.zampa_bell}"
        )
        val notificationBuilder = NotificationCompat.Builder(this, ZampaApp.CHANNEL_ID)
            .setSmallIcon(androidx.core.R.drawable.notification_bg)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
