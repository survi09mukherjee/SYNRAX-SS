package com.synrax.ss.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.code.scanner.GmsBarcodeScanner;
import com.google.android.gms.code.scanner.GmsBarcodeScanning;
import com.google.gson.Gson;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.data.model.Event;
import com.synrax.ss.data.model.User;
import com.synrax.ss.network.ApiService;
import com.synrax.ss.network.RetrofitClient;
import com.synrax.ss.ui.adapter.EventAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * HomeActivity - Main dashboard for SYNRAX SS.
 * Manages quick actions (Create/Join Room) and displays the user's history of joined events.
 */
public class HomeActivity extends AppCompatActivity {

    private TextView txtWelcome, txtEmpty;
    private MaterialButton btnProfile;
    private MaterialCardView cardCreateRoom, cardJoinRoom;
    private RecyclerView recyclerEvents;
    private EventAdapter adapter;
    private List<Event> recentEvents;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        // Redirect to Login if not authenticated
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Bind views
        txtWelcome = findViewById(R.id.txt_welcome);
        txtEmpty = findViewById(R.id.txt_empty);
        btnProfile = findViewById(R.id.btn_profile);
        cardCreateRoom = findViewById(R.id.card_create_room);
        cardJoinRoom = findViewById(R.id.card_join_room);
        recyclerEvents = findViewById(R.id.recycler_events);

        // Welcome text from cached user details
        User user = sessionManager.getUser();
        if (user != null && user.getFullName() != null) {
            txtWelcome.setText("Welcome, " + user.getFullName() + "!");
        }

        // Setup RecyclerView
        recentEvents = new ArrayList<>();
        adapter = new EventAdapter(recentEvents, event -> {
            Intent intent = new Intent(HomeActivity.this, EventGalleryActivity.class);
            intent.putExtra("event_id", event.getId());
            startActivity(intent);
        });
        recyclerEvents.setLayoutManager(new LinearLayoutManager(this));
        recyclerEvents.setAdapter(adapter);

        // Click listeners
        btnProfile.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, ProfileActivity.class)));
        cardCreateRoom.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, CreateEventActivity.class)));
        cardJoinRoom.setOnClickListener(v -> showJoinDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentRooms();
    }

    /** Loads full details of locally cached room IDs from the backend */
    private void loadRecentRooms() {
        Set<String> roomIds = sessionManager.getRecentRooms();
        if (roomIds.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerEvents.setVisibility(View.GONE);
            recentEvents.clear();
            adapter.notifyDataSetChanged();
            return;
        }

        txtEmpty.setVisibility(View.GONE);
        recyclerEvents.setVisibility(View.VISIBLE);

        List<Event> tempEvents = new ArrayList<>();
        final int total = roomIds.size();
        final AtomicInteger count = new AtomicInteger(0);

        for (String roomId : roomIds) {
            apiService.getEventDetails(roomId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    int currentCount = count.incrementAndGet();
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String json = response.body().string();
                            Event event = new Gson().fromJson(json, Event.class);
                            if (event != null) {
                                synchronized (tempEvents) {
                                    tempEvents.add(event);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (currentCount == total) {
                        runOnUiThread(() -> {
                            recentEvents.clear();
                            recentEvents.addAll(tempEvents);
                            adapter.notifyDataSetChanged();
                            if (recentEvents.isEmpty()) {
                                txtEmpty.setVisibility(View.VISIBLE);
                                recyclerEvents.setVisibility(View.GONE);
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    int currentCount = count.incrementAndGet();
                    if (currentCount == total) {
                        runOnUiThread(() -> {
                            recentEvents.clear();
                            recentEvents.addAll(tempEvents);
                            adapter.notifyDataSetChanged();
                            if (recentEvents.isEmpty()) {
                                txtEmpty.setVisibility(View.VISIBLE);
                                recyclerEvents.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            });
        }
    }

    /** Custom Dialog to select between manual room code entry or QR scanning */
    private void showJoinDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_join_room, null);
        EditText editRoomCode = view.findViewById(R.id.edit_dialog_room_code);
        EditText editPasscode = view.findViewById(R.id.edit_dialog_passcode);
        MaterialButton btnScanQr = view.findViewById(R.id.btn_dialog_scan_qr);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setTitle("Join Event Room")
                .setPositiveButton("Join", null) // Overridden below to prevent auto-closing on validation failure
                .setNegativeButton("Cancel", null)
                .create();

        btnScanQr.setOnClickListener(v -> {
            dialog.dismiss();
            launchQRScanner();
        });

        dialog.show();

        // Override Positive Button action
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String roomId = editRoomCode.getText().toString().trim().toUpperCase();
            String passcode = editPasscode.getText().toString().trim();

            if (roomId.isEmpty()) {
                editRoomCode.setError("Room code is required");
                return;
            }

            dialog.dismiss();
            joinEventRoom(roomId, passcode);
        });
    }

    /** Join event room via standard Room Code + Passcode */
    private void joinEventRoom(String roomId, String passcode) {
        Map<String, String> request = new HashMap<>();
        request.put("event_id", roomId);
        if (!passcode.isEmpty()) {
            request.put("passcode", passcode);
        }

        apiService.joinEvent(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    sessionManager.addRecentRoom(roomId);
                    Toast.makeText(HomeActivity.this, "Joined room successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Navigate directly to event gallery
                    Intent intent = new Intent(HomeActivity.this, EventGalleryActivity.class);
                    intent.putExtra("event_id", roomId);
                    startActivity(intent);
                } else {
                    Toast.makeText(HomeActivity.this, "Failed to join: Invalid room code or passcode", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Launch Google Play Services Code Scanner to perform camera QR Scanning */
    private void launchQRScanner() {
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this);
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null && !rawValue.isEmpty()) {
                        joinViaQrPayload(rawValue);
                    } else {
                        Toast.makeText(this, "Empty QR code detected", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Scan failed or cancelled: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** Post scanned QR payload to backend join-qr endpoint */
    private void joinViaQrPayload(String qrPayload) {
        apiService.joinViaQr(qrPayload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // QR payload contains event details, let's extract the event_id
                    // Typically, payload format is a JSON with event_id or the raw event_id itself.
                    // Let's check if the raw payload is valid JSON or plain text.
                    String extractedEventId = qrPayload;
                    try {
                        // Assuming payload could be a json string like {"event_id":"ABC12"} or just "ABC12"
                        if (qrPayload.trim().startsWith("{")) {
                            Map<?, ?> map = new Gson().fromJson(qrPayload, Map.class);
                            if (map.containsKey("event_id")) {
                                extractedEventId = (String) map.get("event_id");
                            } else if (map.containsKey("id")) {
                                extractedEventId = (String) map.get("id");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    sessionManager.addRecentRoom(extractedEventId);
                    Toast.makeText(HomeActivity.this, "Successfully joined via QR Code!", Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(HomeActivity.this, EventGalleryActivity.class);
                    intent.putExtra("event_id", extractedEventId);
                    startActivity(intent);
                } else {
                    Toast.makeText(HomeActivity.this, "Invalid QR code payload for event", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
