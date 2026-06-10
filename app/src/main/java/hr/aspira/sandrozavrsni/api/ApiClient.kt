package hr.aspira.sandrozavrsni.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE = "http://192.168.4.1:5000"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val ok = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val api: Api = Retrofit.Builder()
        .baseUrl(BASE)
        .client(ok)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(Api::class.java)
}
