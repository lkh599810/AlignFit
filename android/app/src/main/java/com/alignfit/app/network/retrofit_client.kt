package com.alignfit.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Android emulator routes 10.0.2.2 to the host machine's localhost.
    // Change to http://127.0.0.1:8000/ when running on a physical device
    // that is on the same network as the backend server.
//    private const val BASE_URL = "http://10.0.2.2:8000/"
//    private const val BASE_URL = "https://alignfit-api.leekanghyun.co.kr/"
//    private const val BASE_URL = "http://192.168.0.2:8000/"   // LAN: phone on same Wi-Fi as PC
    // USB demo: `adb reverse tcp:8000 tcp:8000` tunnels the phone's 127.0.0.1:8000
    // to the PC backend, bypassing Wi-Fi/firewall. Public so the WebView screen can
    // reuse it for ${BASE_URL}demo.
    const val BASE_URL = "http://127.0.0.1:8000/"
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
