package com.synrax.ss.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.synrax.ss.R;
import com.synrax.ss.data.QRUtils;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.data.model.Event;
import com.synrax.ss.data.model.FaceCluster;
import com.synrax.ss.data.model.Media;
import com.synrax.ss.network.ApiService;
import com.synrax.ss.network.RetrofitClient;
import com.synrax.ss.ui.adapter.FaceClusterAdapter;
import com.synrax.ss.ui.adapter.MediaAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * EventGalleryActivity - Shows grid gallery of rooms media.
 * Connects to WebSockets for realtime refresh, filters by AI faces, and handles camera uploads.
 */
public class EventGalleryActivity extends AppCompatActivity {

    private static final String TAG = "EventGalleryActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int REQUEST_GALLERY_IMAGE = 102;
    private static final int PERMISSION_CAMERA_CODE = 201;

    private TextView txtTitle, txtEmpty;
    private ImageButton btnBack, btnShowQr;
    private ProgressBar uploadProgress;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerMedia, recyclerFaces;
    private FloatingActionButton fabUpload;

    private MediaAdapter mediaAdapter;
    private FaceClusterAdapter faceAdapter;
    private List<Media> mediaList;
    private List<FaceCluster> faceList;

    private String eventId;
    private Event currentEvent;
    private SessionManager sessionManager;
    private ApiService apiService;

    // WebSocket objects
    private WebSocket webSocket;
    private OkHttpClient okHttpClient;

    // File fields for camera capturing
    private File cameraPhotoFile;
    private Uri cameraPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_gallery);

        // Get Event ID from parameters
        eventId = getIntent().getStringExtra("event_id");
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Room session invalid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        // Bind Views
        txtTitle = findViewById(R.id.txt_gallery_title);
        txtEmpty = findViewById(R.id.txt_gallery_empty);
        btnBack = findViewById(R.id.btn_back);
        btnShowQr = findViewById(R.id.btn_show_qr);
        uploadProgress = findViewById(R.id.upload_progress);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerMedia = findViewById(R.id.recycler_media);
        recyclerFaces = findViewById(R.id.recycler_faces);
        fabUpload = findViewById(R.id.fab_upload);

        // Setup Media RecyclerView
        mediaList = new ArrayList<>();
        mediaAdapter = new MediaAdapter(mediaList, media -> {
            Intent intent = new Intent(EventGalleryActivity.this, PhotoViewActivity.class);
            // Pass media metadata and full image url
            intent.putExtra("media_id", media.getId());
            intent.putExtra("storage_path", media.getStoragePath());
            intent.putExtra("file_name", media.getFileName());
            intent.putExtra("uploader_id", media.getUploaderId());
            intent.putExtra("created_at", media.getCreatedAt());
            startActivity(intent);
        });
        recyclerMedia.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerMedia.setAdapter(mediaAdapter);

        // Setup Faces RecyclerView
        faceList = new ArrayList<>();
        faceAdapter = new FaceClusterAdapter(faceList, (cluster, isSelected) -> {
            if (isSelected && cluster != null) {
                // Filter by person
                loadMediaByFaceCluster(cluster.getId());
            } else {
                // Clear filter
                loadAllMedia();
            }
        });
        recyclerFaces.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerFaces.setAdapter(faceAdapter);

        // Actions Listener
        btnBack.setOnClickListener(v -> finish());
        btnShowQr.setOnClickListener(v -> showEventQrDialog());
        fabUpload.setOnClickListener(v -> showUploadSourceSelector());
        swipeRefresh.setOnRefreshListener(this::syncGalleryData);

        // Load data
        loadEventDetails();
        syncGalleryData();

        // Connect WebSockets
        connectWebSocket();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectWebSocket();
    }

    /** Reloads event details, media gallery items, and circular face filter groupings */
    private void syncGalleryData() {
        swipeRefresh.setRefreshing(true);
        loadFaceClusters();
        loadAllMedia();
    }

    private void loadEventDetails() {
        apiService.getEventDetails(eventId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        currentEvent = new Gson().fromJson(json, Event.class);
                        if (currentEvent != null) {
                            txtTitle.setText(currentEvent.getName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed loading event details: " + t.getMessage());
            }
        });
    }

    private void loadAllMedia() {
        if (faceAdapter != null) {
            faceAdapter.clearSelection();
        }
        apiService.getEventMedia(eventId).enqueue(new Callback<List<Media>>() {
            @Override
            public void onResponse(Call<List<Media>> call, retrofit2.Response<List<Media>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    updateMediaList(response.body());
                } else {
                    Toast.makeText(EventGalleryActivity.this, "Failed loading gallery", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Media>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(EventGalleryActivity.this, "Network failure loading gallery", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFaceClusters() {
        apiService.getEventFaceClusters(eventId).enqueue(new Callback<List<FaceCluster>>() {
            @Override
            public void onResponse(Call<List<FaceCluster>> call, retrofit2.Response<List<FaceCluster>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    faceList.clear();
                    faceList.addAll(response.body());
                    faceAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<List<FaceCluster>> call, Throwable t) {
                Log.e(TAG, "Failed loading face groups: " + t.getMessage());
            }
        });
    }

    private void loadMediaByFaceCluster(int clusterId) {
        swipeRefresh.setRefreshing(true);
        apiService.getMediaByFaceCluster(clusterId).enqueue(new Callback<List<Media>>() {
            @Override
            public void onResponse(Call<List<Media>> call, retrofit2.Response<List<Media>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    updateMediaList(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<Media>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(EventGalleryActivity.this, "Failed filtering gallery", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMediaList(List<Media> items) {
        mediaList.clear();
        mediaList.addAll(items);
        mediaAdapter.notifyDataSetChanged();

        if (mediaList.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerMedia.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recyclerMedia.setVisibility(View.VISIBLE);
        }
    }

    /** Add a media item to the top of grid, ignoring duplicates */
    private void addMediaToGrid(Media media) {
        // Prevent duplicates
        for (Media item : mediaList) {
            if (item.getId() == media.getId()) {
                return;
            }
        }
        mediaList.add(0, media);
        mediaAdapter.notifyItemInserted(0);
        
        txtEmpty.setVisibility(View.GONE);
        recyclerMedia.setVisibility(View.VISIBLE);
        recyclerMedia.scrollToPosition(0);
    }

    /** Setup OkHttp WebSocket Listener for Realtime changes */
    private void connectWebSocket() {
        String token = sessionManager.getAuthToken();
        if (token == null) return;

        okHttpClient = new OkHttpClient();

        // Convert base API http address to ws protocol address
        String baseUrl = RetrofitClient.BASE_URL;
        String wsUrl;
        if (baseUrl.startsWith("http://")) {
            wsUrl = baseUrl.replace("http://", "ws://");
        } else if (baseUrl.startsWith("https://")) {
            wsUrl = baseUrl.replace("https://", "wss://");
        } else {
            wsUrl = "ws://10.0.2.2:8000/api/v1/";
        }

        wsUrl = wsUrl + "events/" + eventId + "/ws?token=" + token;

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket connected successfully");
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "WebSocket message: " + text);
                try {
                    JSONObject root = new JSONObject(text);
                    String eventType = root.optString("event_type");
                    
                    if ("MEDIA_UPLOADED".equals(eventType)) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null) {
                            Media media = new Gson().fromJson(data.toString(), Media.class);
                            runOnUiThread(() -> {
                                addMediaToGrid(media);
                                // Reload face groups since a new face might be detected
                                loadFaceClusters();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing WebSocket payload: " + e.getMessage());
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
            }
        });
    }

    private void disconnectWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Activity destroyed");
            webSocket = null;
        }
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient = null;
        }
    }

    /** Present Dialog for user to choose camera capture or gallery file picker */
    private void showUploadSourceSelector() {
        String[] options = {"Take Photo (Camera)", "Choose from Gallery"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Share Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndLaunch();
                    } else {
                        launchGallery();
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_CODE);
        } else {
            launchCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchCamera() {
        try {
            cameraPhotoFile = new File(getCacheDir(), "cam_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(this, "com.synrax.ss.fileprovider", cameraPhotoFile);
            
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (cameraPhotoFile != null && cameraPhotoFile.exists()) {
                    uploadMediaFile(cameraPhotoFile);
                }
            } else if (requestCode == REQUEST_GALLERY_IMAGE && data != null) {
                Uri galleryUri = data.getData();
                if (galleryUri != null) {
                    try {
                        File tempFile = getFileFromUri(galleryUri);
                        uploadMediaFile(tempFile);
                    } catch (IOException e) {
                        Toast.makeText(this, "Unable to read selected image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    /** Copy InputStream from gallery Uri to cache file to resolve Scoped Storage permissions */
    private File getFileFromUri(Uri uri) throws IOException {
        File destination = new File(getCacheDir(), "gal_" + System.currentTimeMillis() + ".jpg");
        try (InputStream is = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
        return destination;
    }

    /** Upload temporary file via Retrofit Multipart Body API */
    private void uploadMediaFile(File file) {
        uploadProgress.setVisibility(View.VISIBLE);
        fabUpload.setEnabled(false);

        RequestBody eventIdPart = RequestBody.create(MediaType.parse("text/plain"), eventId);
        RequestBody filePart = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part fileBody = MultipartBody.Part.createFormData("file", file.getName(), filePart);

        apiService.uploadMedia(eventIdPart, fileBody).enqueue(new Callback<Media>() {
            @Override
            public void onResponse(Call<Media> call, retrofit2.Response<Media> response) {
                uploadProgress.setVisibility(View.GONE);
                fabUpload.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(EventGalleryActivity.this, "Shared successfully!", Toast.LENGTH_SHORT).show();
                    addMediaToGrid(response.body());
                    loadFaceClusters();
                } else {
                    Toast.makeText(EventGalleryActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Media> call, Throwable t) {
                uploadProgress.setVisibility(View.GONE);
                fabUpload.setEnabled(true);
                Toast.makeText(EventGalleryActivity.this, "Upload failure: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Local QR code generation overlay dialog using QRUtils helper */
    private void showEventQrDialog() {
        if (currentEvent == null) {
            Toast.makeText(this, "Event loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_show_qr, null);
        ImageView imgQrCode = view.findViewById(R.id.img_qr_code);
        TextView txtQrInfo = view.findViewById(R.id.txt_qr_code_info);
        MaterialButton btnClose = view.findViewById(R.id.btn_close_qr);

        txtQrInfo.setText("Room Code: " + currentEvent.getId());

        try {
            // Generate QR bitmap locally using the payload
            Bitmap qrBitmap = QRUtils.generateQRCode(currentEvent.getQrPayload(), 600, 600);
            imgQrCode.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Failed generating QR locally", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
