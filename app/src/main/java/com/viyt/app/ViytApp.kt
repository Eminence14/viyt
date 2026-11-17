package com.viyt.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.viyt.app.core.Engine
import com.viyt.app.core.InnerTube
import com.viyt.app.core.Offline

class ViytApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Offline.init(this)
        Engine.start(this)
    }

    /** Thumbnails share the API's HTTP/2 connection pool and are cached on disk. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(InnerTube.http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(120)
        .respectCacheHeaders(false)
        .build()
}
