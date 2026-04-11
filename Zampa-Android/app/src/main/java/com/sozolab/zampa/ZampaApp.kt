package com.sozolab.zampa

import android.app.Application
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
    }
}
