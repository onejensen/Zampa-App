package com.sozolab.zampa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZampaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configurar Coil con caché generosa para que las imágenes cargadas
        // en el feed estén disponibles al instante al abrir el modal de detalle.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.30) // 30% de la RAM disponible
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(200L * 1024 * 1024) // 200 MB en disco
                        .build()
                }
                .crossfade(true) // transición suave en primera carga
                .build()
        )

        // Crear canal de notificación al arrancar. Obligatorio hacerlo aquí (no
        // dentro del FirebaseMessagingService) porque cuando la app está en
        // background, FCM muestra la notificación sin invocar onMessageReceived;
        // si el canal no existe, Android lo crea con config default y queda
        // inmutable. Creándolo aquí nos aseguramos de que el sonido custom
        // esté configurado la primera vez que llega una push.
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.zampa_bell}"
        )
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Menús favoritos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones cuando tus restaurantes favoritos publican oferta"
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)

        // Limpia el canal viejo sin sonido custom (idempotente).
        manager.deleteNotificationChannel("menu_updates")
    }

    companion object {
        const val CHANNEL_ID = "menu_updates_v2"
    }
}
