package com.example.toxicchat.androidapp.data.remote

import android.util.Log
import com.example.toxicchat.androidapp.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ToxicityClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
    }

    private val debugRequestInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (BuildConfig.DEBUG && request.body != null && request.url.encodedPath.endsWith("toxicity/score")) {
            try {
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val bodyString = buffer.readUtf8()
                val adapter = moshi.adapter(ScoreRequestDto::class.java)
                val scoreReq = adapter.fromJson(bodyString)
                if (scoreReq != null) {
                    Log.d("ToxicityClient", "Request: conversationId=${scoreReq.conversationId}, modelVersion=${scoreReq.modelVersion}, itemsCount=${scoreReq.items.size}")
                }
            } catch (e: Exception) {
                Log.e("ToxicityClient", "Error logging request", e)
            }
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Aumentato a 60s
        .readTimeout(90, TimeUnit.SECONDS)    // Aumentato a 90s per gestire l'inferenza lenta di BERT
        .writeTimeout(60, TimeUnit.SECONDS)   // Aumentato a 60s
        .retryOnConnectionFailure(false)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(debugRequestInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    fun createApi(): ToxicityApi = retrofit.create(ToxicityApi::class.java)
}
