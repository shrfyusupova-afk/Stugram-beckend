package com.stugram.app.data.remote

import android.content.Context
import android.util.Log
import com.stugram.app.BuildConfig
import com.stugram.app.core.network.ApiRetryInterceptor
import com.stugram.app.core.storage.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var appContext: Context? = null
    private var tokenManager: TokenManager? = null
    private var retrofit: Retrofit? = null

    fun init(context: Context) {
        if (retrofit != null) return
        appContext = context.applicationContext
        tokenManager = TokenManager(appContext!!)

        if (BuildConfig.DEBUG) {
            Log.i("RetrofitClient", "API_BASE_URL=${BuildConfig.API_BASE_URL}")
        }

        val logging = SecureHttpLogging.createInterceptor(BuildConfig.DEBUG)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager!!))
            .addInterceptor(ApiRetryInterceptor())
            .addInterceptor(logging)
            .authenticator(TokenAuthenticator(tokenManager!!))
            .retryOnConnectionFailure(true)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun requireTokenManager(): TokenManager =
        tokenManager ?: error("RetrofitClient is not initialized. Call RetrofitClient.init(context) first.")

    private fun requireRetrofit(): Retrofit =
        retrofit ?: error("RetrofitClient is not initialized. Call RetrofitClient.init(context) first.")

    val authApi: AuthApi by lazy { requireRetrofit().create(AuthApi::class.java) }
    val mediaApi: MediaApi by lazy { requireRetrofit().create(MediaApi::class.java) }
    val profileApi: ProfileApi by lazy { requireRetrofit().create(ProfileApi::class.java) }
    val postApi: PostApi by lazy { requireRetrofit().create(PostApi::class.java) }
    val storyApi: StoryApi by lazy { requireRetrofit().create(StoryApi::class.java) }
    val followApi: FollowApi by lazy { requireRetrofit().create(FollowApi::class.java) }
    val notificationApi: NotificationApi by lazy { requireRetrofit().create(NotificationApi::class.java) }
    val searchApi: SearchApi by lazy { requireRetrofit().create(SearchApi::class.java) }
    val chatApi: ChatApi by lazy { requireRetrofit().create(ChatApi::class.java) }
    val groupChatApi: GroupChatApi by lazy { requireRetrofit().create(GroupChatApi::class.java) }
    val deviceApi: DeviceApi by lazy { requireRetrofit().create(DeviceApi::class.java) }
    val likeApi: LikeApi by lazy { requireRetrofit().create(LikeApi::class.java) }
    val commentApi: CommentApi by lazy { requireRetrofit().create(CommentApi::class.java) }
    val settingsApi: SettingsApi by lazy { requireRetrofit().create(SettingsApi::class.java) }
    val supportApi: SupportApi by lazy { requireRetrofit().create(SupportApi::class.java) }
    val recommendationApi: RecommendationApi by lazy { requireRetrofit().create(RecommendationApi::class.java) }
    val exploreApi: ExploreApi by lazy { requireRetrofit().create(ExploreApi::class.java) }

    fun createRefreshApi(): AuthApi {
        val context = appContext ?: error("RetrofitClient is not initialized")
        val client = OkHttpClient.Builder()
            .addInterceptor(
                SecureHttpLogging.createInterceptor(BuildConfig.DEBUG)
            )
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}

object SecureHttpLogging {
    private val sensitiveHeaders = listOf(
        "Authorization",
        "Cookie",
        "Set-Cookie",
        "X-Auth-Token",
        "X-Refresh-Token"
    )

    fun createInterceptor(isDebug: Boolean): HttpLoggingInterceptor =
        HttpLoggingInterceptor { message ->
            Log.d("HTTP", redact(message))
        }.apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            sensitiveHeaders.forEach(::redactHeader)
        }

    fun redact(message: String): String =
        message
            .replace(Regex("(?i)(Authorization:\\s*Bearer\\s+)[^\\s]+"), "$1██")
            .replace(Regex("(?i)(refreshToken|accessToken|token|otp|password)\"?\\s*[:=]\\s*\"?[A-Za-z0-9._~+\\-/=]+\"?"), "$1=██")
            .replace(Regex("(?i)(https?://[^\\s\"]*(cloudinary|signature|token|expires)[^\\s\"]*)"), "██redacted-url██")
}
