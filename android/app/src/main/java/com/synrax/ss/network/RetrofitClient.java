package com.synrax.ss.network;

import android.content.Context;
import com.synrax.ss.data.SessionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // 10.0.2.2 points to localhost of host PC from official Android Emulator
    public static final String BASE_URL = "http://10.0.2.2:8000/api/v1/";
    private static Retrofit retrofit = null;

    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {
            final SessionManager sessionManager = new SessionManager(context.getApplicationContext());

            // Build OkHttpClient
            OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

            // Authorization Interceptor to auto-inject Bearer JWT tokens
            httpClient.addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();

                String token = sessionManager.getAuthToken();
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                return chain.proceed(requestBuilder.build());
            });

            // HTTP request logging for debugging in Logcat
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient.addInterceptor(logging);

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient.build())
                    .build();
        }
        return retrofit;
    }
}
