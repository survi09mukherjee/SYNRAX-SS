package com.synrax.ss.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.network.RetrofitClient;

/**
 * PhotoViewActivity - Displays high-resolution event photos full screen.
 */
public class PhotoViewActivity extends AppCompatActivity {

    private ImageView imgFull;
    private ImageButton btnClose;
    private TextView txtTitle, txtUploader, txtUploadTime;
    private ProgressBar photoLoader;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        sessionManager = new SessionManager(this);

        // Bind Views
        imgFull = findViewById(R.id.img_full);
        btnClose = findViewById(R.id.btn_close);
        txtTitle = findViewById(R.id.txt_photo_title);
        txtUploader = findViewById(R.id.txt_uploader);
        txtUploadTime = findViewById(R.id.txt_upload_time);
        photoLoader = findViewById(R.id.photo_loader);

        // Fetch inputs
        String storagePath = getIntent().getStringExtra("storage_path");
        String fileName = getIntent().getStringExtra("file_name");
        int uploaderId = getIntent().getIntExtra("uploader_id", -1);
        String createdAt = getIntent().getStringExtra("created_at");

        // UI text content setup
        if (fileName != null) {
            txtTitle.setText(fileName);
        }

        if (uploaderId != -1) {
            if (sessionManager.getUser() != null && uploaderId == sessionManager.getUser().getId()) {
                txtUploader.setText("Shared by: Me");
            } else {
                txtUploader.setText("Shared by: Participant #" + uploaderId);
            }
        }

        if (createdAt != null) {
            String formatted = createdAt.replace("T", " ");
            if (formatted.contains(".")) {
                formatted = formatted.substring(0, formatted.indexOf("."));
            }
            txtUploadTime.setText("Uploaded on: " + formatted);
        }

        // Close action
        btnClose.setOnClickListener(v -> finish());

        // Resolve absolute URL
        String imageUrl = "";
        if (storagePath != null) {
            if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
                imageUrl = storagePath;
            } else {
                String baseUrl = RetrofitClient.BASE_URL;
                if (baseUrl.contains("/api/v1/")) {
                    String host = baseUrl.substring(0, baseUrl.indexOf("/api/v1/"));
                    imageUrl = host + "/uploads/" + storagePath;
                } else {
                    imageUrl = "http://10.0.2.2:8000/uploads/" + storagePath;
                }
            }
        }

        // Load image using Glide with spinner listener hooks
        Glide.with(this)
                .load(imageUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        photoLoader.setVisibility(View.GONE);
                        Toast.makeText(PhotoViewActivity.this, "Failed loading high-res photo", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        photoLoader.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(imgFull);
    }
}
