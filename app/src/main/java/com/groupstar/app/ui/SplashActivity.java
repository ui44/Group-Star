package com.groupstar.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.groupstar.app.accounts.AccountManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccountManager am = new AccountManager(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent next;
            if (am.isEmpty()) {
                // مفيش حسابات — روح لشاشة التوكن
                next = new Intent(this, TokenLoginActivity.class);
            } else {
                // في حساب محفوظ — شغّل مباشرة
                next = new Intent(this, MainActivity.class);
            }
            startActivity(next);
            finish();
        }, 1200);
    }
}
