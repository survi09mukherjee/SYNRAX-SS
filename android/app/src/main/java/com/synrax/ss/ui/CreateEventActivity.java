package com.synrax.ss.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.data.model.Event;
import com.synrax.ss.network.ApiService;
import com.synrax.ss.network.RetrofitClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * CreateEventActivity - Handles creation of temporary event rooms.
 */
public class CreateEventActivity extends AppCompatActivity {

    private TextInputEditText editName, editDescription, editPasscode, editDuration;
    private MaterialButton btnCreateEvent;
    private ImageButton btnBack;
    private FrameLayout loaderOverlay;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        // Bind Views
        editName = findViewById(R.id.edit_event_name);
        editDescription = findViewById(R.id.edit_event_description);
        editPasscode = findViewById(R.id.edit_event_passcode);
        editDuration = findViewById(R.id.edit_event_duration);
        btnCreateEvent = findViewById(R.id.btn_create_event);
        btnBack = findViewById(R.id.btn_back);
        loaderOverlay = findViewById(R.id.loader_overlay);

        // Listeners
        btnBack.setOnClickListener(v -> finish());
        btnCreateEvent.setOnClickListener(v -> attemptCreateEvent());
    }

    private void attemptCreateEvent() {
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String description = editDescription.getText() != null ? editDescription.getText().toString().trim() : "";
        String passcode = editPasscode.getText() != null ? editPasscode.getText().toString().trim() : "";
        String durationStr = editDuration.getText() != null ? editDuration.getText().toString().trim() : "";

        // Form Validation
        if (name.isEmpty()) {
            editName.setError("Event Name is required");
            return;
        }
        if (name.length() < 3) {
            editName.setError("Event Name must be at least 3 characters");
            return;
        }

        int durationHours = 24; // Default to 24 hours
        if (!durationStr.isEmpty()) {
            try {
                durationHours = Integer.parseInt(durationStr);
                if (durationHours <= 0) {
                    editDuration.setError("Duration must be a positive integer");
                    return;
                }
            } catch (NumberFormatException e) {
                editDuration.setError("Please enter a valid number");
                return;
            }
        }

        showLoader(true);

        // Construct request payload matching EventCreate schema
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        if (!description.isEmpty()) {
            requestBody.put("description", description);
        }
        if (!passcode.isEmpty()) {
            requestBody.put("passcode", passcode);
        }
        requestBody.put("duration_hours", durationHours);

        apiService.createEvent(requestBody).enqueue(new Callback<Event>() {
            @Override
            public void onResponse(Call<Event> call, Response<Event> response) {
                showLoader(false);
                if (response.isSuccessful() && response.body() != null) {
                    Event createdEvent = response.body();
                    
                    // Add newly created room code to local history list
                    sessionManager.addRecentRoom(createdEvent.getId());
                    Toast.makeText(CreateEventActivity.this, "Room created successfully!", Toast.LENGTH_SHORT).show();

                    // Navigate directly to event gallery for the room
                    Intent intent = new Intent(CreateEventActivity.this, EventGalleryActivity.class);
                    intent.putExtra("event_id", createdEvent.getId());
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(CreateEventActivity.this, "Failed to create room. Please verify your entries.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Event> call, Throwable t) {
                showLoader(false);
                Toast.makeText(CreateEventActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoader(boolean show) {
        loaderOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnCreateEvent.setEnabled(!show);
    }
}
