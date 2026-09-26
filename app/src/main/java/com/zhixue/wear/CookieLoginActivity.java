package com.zhixue.wear;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.zhixue.wear.store.SessionStore;

/**
 * 手动登录 / 账号设置：
 *  - 保存账号密码（供网页登录页自动填充）
 *  - 直接粘贴 Cookie 登录（适用于没有 WebView 的设备，如部分 Wear OS 镜像）
 */
public class CookieLoginActivity extends Activity {

    private static final String PREF = "zhixue_creds";

    private EditText etUser, etPwd, etCookie;
    private SessionStore session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cookie_login);
        session = new SessionStore(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etUser = findViewById(R.id.et_user);
        etPwd = findViewById(R.id.et_pwd);
        etCookie = findViewById(R.id.et_cookie);

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        etUser.setText(sp.getString("username", ""));
        etPwd.setText(sp.getString("password", ""));

        Button saveCreds = findViewById(R.id.btn_save_creds);
        Button saveCookie = findViewById(R.id.btn_save_cookie);
        Button clear = findViewById(R.id.btn_clear);

        saveCreds.setOnClickListener(v -> {
            sp.edit()
              .putString("username", etUser.getText().toString().trim())
              .putString("password", etPwd.getText().toString())
              .apply();
            Toast.makeText(this, "已保存账号", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginWebActivity.class));
        });

        saveCookie.setOnClickListener(v -> {
            String c = etCookie.getText().toString().trim();
            if (c.isEmpty()) {
                Toast.makeText(this, "请先粘贴 Cookie", Toast.LENGTH_SHORT).show();
                return;
            }
            session.importCookieString(c);
            if (!session.hasLoginUserName()) {
                Toast.makeText(this, "Cookie 缺少 loginUserName，可能不完整", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        clear.setOnClickListener(v -> {
            session.clear();
            sp.edit().clear().apply();
            etUser.setText("");
            etPwd.setText("");
            etCookie.setText("");
            Toast.makeText(this, "已清除登录信息", Toast.LENGTH_SHORT).show();
        });
    }
}
