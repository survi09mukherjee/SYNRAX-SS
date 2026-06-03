package com.synrax.ss.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.data.model.User;

/**
 * ProfileActivity - Shows user profile stats and logs out.
 */
public class ProfileActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView txtAvatarInitials, txtName, txtEmail, txtRoomsCount, txtUploadsCount;
    private MaterialButton btnLogout;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);

        // Bind Views
        btnBack = findViewById(R.id.btn_back);
        txtAvatarInitials = findViewById(R.id.txt_avatar_initials);
        txtName = findViewById(R.id.txt_profile_name);
        txtEmail = findViewById(R.id.txt_profile_email);
        txtRoomsCount = findViewById(R.id.txt_rooms_count);
        txtUploadsCount = findViewById(R.id.txt_uploads_count);
        btnLogout = findViewById(R.id.btn_logout);

        // Click listeners
        btnBack.setOnClickListener(v -> finish());
        btnLogout.setOnClickListener(v -> performLogout());

        // Load profile details
        loadUserProfile();
    }

    private void loadUserProfile() {
        User user = sessionManager.getUser();
        if (user == null) {
            Toast.makeText(this, "Profile session invalid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        txtName.setText(user.getFullName());
        txtEmail.setText(user.getEmail());

        // Set Initials Avatar e.g. "John Doe" -> "JD"
        String fullName = user.getFullName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length > 1) {
                String initials = "" + parts[0].charAt(0) + parts[1].charAt(0);
                txtAvatarInitials.setText(initials.toUpperCase());
            } else if (fullName.length() > 0) {
                txtAvatarInitials.setText(("" + fullName.charAt(0)).toUpperCase());
            }
        } else {
            txtAvatarInitials.setText("U");
        }

        // Set Rooms Count from local storage history size
        int roomsCount = sessionManager.getRecentRooms().size();
        txtRoomsCount.setText(String.valueOf(roomsCount));

        // Display dummy/estimated stats for photos shared (simulate backend syncing)
        int estimatedUploads = roomsCount * 3 + 2; 
        txtUploadsCount.setText(String.valueOf(estimatedUploads));
    }

    private void performLogout() {
        // Clear cached auth credentials & user profile details
        sessionManager.logout();
        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();

        // Redirect user to login activity and wipe the back stack
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
