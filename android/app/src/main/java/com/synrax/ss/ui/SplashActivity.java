package com.synrax.ss.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.synrax.ss.R;
import com.synrax.ss.data.SessionManager;

/**
 * SplashActivity - Entry point of the application.
 * Shows branding for 2 seconds, then routes the user to:
 * - HomeActivity  → if already logged in (JWT token exists in SharedPreferences)
 * - LoginActivity → if not logged in
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SessionManager sessionManager = new SessionManager(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent;
            if (sessionManager.isLoggedIn()) {
                // Token found — go directly to Home
                intent = new Intent(SplashActivity.this, HomeActivity.class);
            } else {
                // No token — user needs to login
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            startActivity(intent);
            finish(); // Remove splash from back stack
        }, SPLASH_DELAY_MS);
    }
}
