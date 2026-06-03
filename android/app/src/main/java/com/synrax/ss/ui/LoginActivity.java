package com.synrax.ss.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;
import com.synrax.ss.data.model.LoginRequest;
import com.synrax.ss.data.model.TokenResponse;
import com.synrax.ss.data.model.User;
import com.synrax.ss.network.ApiService;
import com.synrax.ss.network.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LoginActivity - Handles user authentication.
 * On successful login:
 * 1. Saves JWT token to SessionManager
 * 2. Fetches user profile via /auth/me
 * 3. Navigates to HomeActivity
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editEmail, editPassword;
    private MaterialButton btnLogin;
    private TextView txtNavSignup;
    private FrameLayout loaderOverlay;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize
        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        // Bind views
        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        txtNavSignup = findViewById(R.id.txt_nav_signup);
        loaderOverlay = findViewById(R.id.loader_overlay);

        // Login button click
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Navigate to Signup
        txtNavSignup.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        // Basic validation
        if (email.isEmpty()) {
            editEmail.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            editPassword.setError("Password is required");
            return;
        }

        showLoader(true);

        LoginRequest request = new LoginRequest(email, password);
        apiService.login(request).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Save token
                    sessionManager.saveAuthToken(response.body().getAccessToken());
                    // Now fetch user profile
                    fetchUserProfile();
                } else {
                    showLoader(false);
                    Toast.makeText(LoginActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<TokenResponse> call, Throwable t) {
                showLoader(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchUserProfile() {
        // Re-create ApiService so the interceptor picks up the newly saved token
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        apiService.getMe().enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                showLoader(false);
                if (response.isSuccessful() && response.body() != null) {
                    sessionManager.saveUser(response.body());
                    // Navigate to Home
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to fetch profile", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                showLoader(false);
                // Token saved, profile fetch failed — still navigate to Home
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }
        });
    }

    private void showLoader(boolean show) {
        loaderOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }
}
