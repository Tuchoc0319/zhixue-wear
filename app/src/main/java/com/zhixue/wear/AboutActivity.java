package com.zhixue.wear;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.zhixue.wear.store.SessionStore;
import com.zhixue.wear.ui.Anim;

/** 关于 / 退出（参考图二：品牌卡 + 信息行 + 免责声明 + 退出按钮） */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        SessionStore session = new SessionStore(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 版本 / 构建号
        String ver = "-";
        String build = "-";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName == null ? "-" : pi.versionName;
            build = String.valueOf(pi.versionCode);
        } catch (Exception ignored) {
        }
        ((TextView) findViewById(R.id.tv_ver_value)).setText(ver);
        ((TextView) findViewById(R.id.tv_build_value)).setText(build);

        // 当前账号
        String acc = session.get("www.zhixue.com", "loginUserName");
        String accText = (acc == null || acc.isEmpty())
                ? "未登录"
                : acc.substring(acc.indexOf('=') + 1);
        ((TextView) findViewById(R.id.tv_account_value)).setText(accText);

        // 免责声明
        ((TextView) findViewById(R.id.tv_about)).setText(
                "免责声明：本客户端为非官方概念作品，仅用于个人学习与前端交互演示；"
                + "页面内成绩、试卷与姓名等数据均来自智学网公开网页接口，"
                + "与本应用开发者及设备厂商无任何关联。");

        Button logout = findViewById(R.id.btn_logout);
        logout.setOnClickListener(v -> {
            session.clear();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        // 入场动效
        Anim.wipeIn(findViewById(R.id.accent_line), 0, 420);
        Anim.fadeUp(findViewById(R.id.header_bar), 60);
        Anim.fadeUp(findViewById(R.id.tv_ver_value).getRootView().findViewById(R.id.btn_back), 0);
        Anim.press(logout);
        Anim.press(findViewById(R.id.btn_back));
    }
}
