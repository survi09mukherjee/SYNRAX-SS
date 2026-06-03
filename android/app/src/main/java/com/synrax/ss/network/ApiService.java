package com.synrax.ss.network;

import com.synrax.ss.data.model.Event;
import com.synrax.ss.data.model.FaceCluster;
import com.synrax.ss.data.model.LoginRequest;
import com.synrax.ss.data.model.Media;
import com.synrax.ss.data.model.RegisterRequest;
import com.synrax.ss.data.model.TokenResponse;
import com.synrax.ss.data.model.User;
import java.util.List;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // --- Authentication ---
    @POST("auth/signup")
    Call<User> signup(@Body RegisterRequest request);

    @POST("auth/login")
    Call<TokenResponse> login(@Body LoginRequest request);

    @GET("auth/me")
    Call<User> getMe();

    // --- Event Rooms ---
    @POST("events/create")
    Call<Event> createEvent(@Body Map<String, Object> requestBody); // Maps to EventCreate schema

    @POST("events/join")
    Call<ResponseBody> joinEvent(@Body Map<String, String> requestBody); // {"event_id": id, "passcode": passcode}

    @POST("events/join-qr")
    Call<ResponseBody> joinViaQr(@Query("payload") String qrPayloadStr);

    @GET("events/{event_id}")
    Call<ResponseBody> getEventDetails(@Path("event_id") String eventId); // Raw JSON to parse participants lists

    @GET("events/{event_id}/qr")
    Call<Map<String, String>> getEventQr(@Path("event_id") String eventId); // Returns {"qr_code_image": base64, "qr_payload": raw}

    // --- Media Management ---
    @Multipart
    @POST("media/upload")
    Call<Media> uploadMedia(
        @Part("event_id") RequestBody eventId,
        @Part MultipartBody.Part file
    );

    @GET("media/event/{event_id}")
    Call<List<Media>> getEventMedia(@Path("event_id") String eventId);

    @GET("media/faces/{event_id}")
    Call<List<FaceCluster>> getEventFaceClusters(@Path("event_id") String eventId);

    @GET("media/faces/cluster/{cluster_id}")
    Call<List<Media>> getMediaByFaceCluster(@Path("cluster_id") int clusterId);

    @DELETE("media/{media_id}")
    Call<ResponseBody> deleteMedia(@Path("media_id") int mediaId);
}
