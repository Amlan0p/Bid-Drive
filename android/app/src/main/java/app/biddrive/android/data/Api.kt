package app.biddrive.android.data

import app.biddrive.android.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface BidDriveApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("auth/demo") suspend fun demoLogin(): AuthResponse

    @GET("me") suspend fun me(): UserDto
    @PATCH("users/me") suspend fun updateMe(@Body body: UpdateProfileRequest): UserDto

    @GET("auctions") suspend fun auctions(@QueryMap query: Map<String, String>): AuctionListResponse
    @GET("auctions/{id}") suspend fun auction(@Path("id") id: Int): AuctionDto
    @POST("auctions/{id}/bids") suspend fun placeBid(@Path("id") id: Int, @Body body: PlaceBidRequest): PlaceBidResponse

    @GET("bids/my") suspend fun myBids(): List<MyBidDto>

    @GET("watchlist") suspend fun watchlist(): List<WatchlistItemDto>
    @POST("watchlist/{auctionId}") suspend fun addToWatchlist(@Path("auctionId") id: Int)
    @DELETE("watchlist/{auctionId}") suspend fun removeFromWatchlist(@Path("auctionId") id: Int)
}

/** Result type used by every repository call. A failed request is never reported as a success. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String, val code: String? = null, val httpStatus: Int? = null) : ApiResult<Nothing>
}

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object ApiConfig {
    val baseUrl: String = run {
        val trimmed = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        val withApi = if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
        "$withApi/"
    }
    /** True while the release placeholder URL has not been replaced. */
    val isConfigured: Boolean = !baseUrl.contains("CONFIGURE-ME")

    /** Scheme + host (+ port) of the API, used to resolve relative image paths such as /api/cars/images/x.png. */
    private val origin: String = runCatching {
        val u = baseUrl.toHttpUrl()
        val port = if (u.port == 80 || u.port == 443) "" else ":${u.port}"
        "${u.scheme}://${u.host}$port"
    }.getOrDefault("")

    fun imageUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> origin + (if (path.startsWith("/")) path else "/$path")
    }
}

object ImagePreloader {
    fun preload(context: android.content.Context, urls: List<String?>) {
        val loader = coil.Coil.imageLoader(context)
        urls.filterNotNull().forEach { rawUrl ->
            val resolved = ApiConfig.imageUrl(rawUrl)
            if (resolved != null) {
                val req = coil.request.ImageRequest.Builder(context)
                    .data(resolved)
                    .memoryCacheKey(resolved)
                    .diskCacheKey(resolved)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
                loader.enqueue(req)
            }
        }
    }
}


object ApiFactory {
    fun create(tokenProvider: () -> String?, onUnauthorized: () -> Unit): BidDriveApi {
        val auth = Interceptor { chain ->
            val token = tokenProvider()
            val request = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
            val response = chain.proceed(request)
            // A 401 on a request that carried a token means the session is no longer valid.
            if (response.code == 401 && token != null) onUnauthorized()
            response
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)   // free Render instances can take ~30s to wake up
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(ApiConfig.baseUrl)
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BidDriveApi::class.java)
    }
}

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> {
    if (!ApiConfig.isConfigured) {
        return ApiResult.Failure("The backend URL is not configured. Build the app with -PbiddriveApiBaseUrl=https://<your-service>/api/ (see README).", "not_configured")
    }
    return try {
        ApiResult.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { AppJson.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
        val msg = parsed?.error ?: parsed?.message ?: when (e.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You don't have permission to do that."
            404 -> "Not found."
            429 -> "Too many requests. Please wait a moment."
            in 500..599 -> "The server had a problem. Please try again."
            else -> "Request failed (${e.code()})."
        }
        ApiResult.Failure(msg, parsed?.code, e.code())
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure("The server took too long to respond. If it was idle it may be waking up - try again in a moment.", "timeout")
    } catch (e: IOException) {
        ApiResult.Failure("Can't reach the BidDrive server. Check your internet connection and the API URL.", "network")
    } catch (e: Exception) {
        ApiResult.Failure("Something went wrong: ${e.message ?: e::class.java.simpleName}", "unknown")
    }
}
