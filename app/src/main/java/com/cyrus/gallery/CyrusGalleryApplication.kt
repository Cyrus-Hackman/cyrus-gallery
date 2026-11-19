package com.cyrus.gallery

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class CyrusGalleryApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var authManager: OneDriveAuthManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (host.contains("graph.microsoft.com")) {
                    val token = try {
                        if (::authManager.isInitialized) {
                            authManager.cachedAccessToken
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                    if (!token.isNullOrEmpty()) {
                        val authRequest = request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                        return@addInterceptor chain.proceed(authRequest)
                    }
                }
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
