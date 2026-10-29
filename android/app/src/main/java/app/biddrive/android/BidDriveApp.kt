package app.biddrive.android

import android.app.Application
import app.biddrive.android.data.ApiFactory
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.AuthRepository
import app.biddrive.android.data.FilterStore
import app.biddrive.android.data.SessionStore
import app.biddrive.android.data.SettingsStore
import app.biddrive.android.data.WatchlistRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container (keeps the project free of DI frameworks). */
class AppContainer(app: Application) {
    val session = SessionStore(app)
    val settings = SettingsStore(app)
    private val api = ApiFactory.create(tokenProvider = { session.token }, onUnauthorized = { session.onUnauthorized() })
    val auth = AuthRepository(api, session)
    val auctions = AuctionRepository(api)
    val watchlist = WatchlistRepository(api)
    val filters = FilterStore()
}

class BidDriveApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // Ensure car image responses are cached locally and immediately served from cache
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=2592000, immutable")
                    .removeHeader("Pragma")
                    .build()
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}

