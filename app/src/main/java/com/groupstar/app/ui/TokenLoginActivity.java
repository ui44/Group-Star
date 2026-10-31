package com.groupstar.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.groupstar.app.R;
import com.groupstar.app.accounts.AccountManager;

public class TokenLoginActivity extends AppCompatActivity {

    private AccountManager accountManager;
    private EditText etToken, etNickname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_token_login);

        accountManager = new AccountManager(this);
        etToken    = findViewById(R.id.et_token);
        etNickname = findViewById(R.id.et_nickname);

        findViewById(R.id.btn_login).setOnClickListener(v -> doLogin());

        // زر إضافة حساب ثاني (يظهر إذا في حسابات موجودة)
        View btnBack = findViewById(R.id.btn_back);
        if (accountManager.isEmpty()) {
            btnBack.setVisibility(View.GONE);
        } else {
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }
    }

    private void doLogin() {
        String token    = etToken.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();

        if (TextUtils.isEmpty(token)) {
            etToken.setError("ضع التوكن هنا");
            return;
        }

        int index = accountManager.addAccount(token, nickname);
        accountManager.switchTo(index);

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
