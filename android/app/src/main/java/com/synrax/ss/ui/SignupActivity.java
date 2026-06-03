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
import com.synrax.ss.data.model.RegisterRequest;
import com.synrax.ss.data.model.TokenResponse;
import com.synrax.ss.data.model.User;
import com.synrax.ss.network.ApiService;
import com.synrax.ss.network.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SignupActivity - Handles user registration.
 * On successful registration, it automatically attempts to login and direct to HomeActivity.
 */
public class SignupActivity extends AppCompatActivity {

    private TextInputEditText editName, editEmail, editPassword;
    private MaterialButton btnSignup;
    private TextView txtNavLogin;
    private FrameLayout loaderOverlay;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Session and ApiService
        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getClient(this).create(ApiService.class);

        // Bind Views
        editName = findViewById(R.id.edit_name);
        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        btnSignup = findViewById(R.id.btn_signup);
        txtNavLogin = findViewById(R.id.txt_nav_login);
        loaderOverlay = findViewById(R.id.loader_overlay);

        // Signup button click listener
        btnSignup.setOnClickListener(v -> attemptSignup());

        // Navigate back to Login Screen
        txtNavLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptSignup() {
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        // Form Validation
        if (name.isEmpty()) {
            editName.setError("Full name is required");
            return;
        }
        if (email.isEmpty()) {
            editEmail.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            editPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            editPassword.setError("Password must be at least 6 characters");
            return;
        }

        showLoader(true);

        RegisterRequest registerRequest = new RegisterRequest(email, password, name);
        apiService.signup(registerRequest).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Registration successful, trigger automatic login
                    Toast.makeText(SignupActivity.this, "Account created! Logging in...", Toast.LENGTH_SHORT).show();
                    attemptAutoLogin(email, password);
                } else {
                    showLoader(false);
                    Toast.makeText(SignupActivity.this, "Signup failed: Email might be taken or invalid format", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                showLoader(false);
                Toast.makeText(SignupActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptAutoLogin(String email, String password) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        apiService.login(loginRequest).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    sessionManager.saveAuthToken(response.body().getAccessToken());
                    fetchUserProfile();
                } else {
                    showLoader(false);
                    // If auto-login fails, redirect to Login Screen
                    startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                    finish();
                }
            }

            @Override
            public void onFailure(Call<TokenResponse> call, Throwable t) {
                showLoader(false);
                // If auto-login fails due to network, redirect to Login Screen
                startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                finish();
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
                }
                // Always navigate to Home on successful token acquisition
                startActivity(new Intent(SignupActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                showLoader(false);
                // Still navigate to Home since the token is stored
                startActivity(new Intent(SignupActivity.this, HomeActivity.class));
                finish();
            }
        });
    }

    private void showLoader(boolean show) {
        loaderOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSignup.setEnabled(!show);
    }
}
