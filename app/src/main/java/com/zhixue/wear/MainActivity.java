package com.zhixue.wear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.zhixue.wear.store.SessionStore;
import com.zhixue.wear.ui.Anim;

public class MainActivity extends Activity {

    private SessionStore session;
    private TextView tvUser;
    private View btnLogin, btnExit, accentLine, headerBar;
    private ViewGroup menuContainer;
    private boolean firstLaunch = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        session = new SessionStore(this);

        tvUser = findViewById(R.id.tv_user);
        btnLogin = findViewById(R.id.btn_login);
        btnExit = findViewById(R.id.btn_exit);
        accentLine = findViewById(R.id.accent_line);
        headerBar = findViewById(R.id.header_bar);
        menuContainer = findViewById(R.id.menu_container);

        btnExit.setOnClickListener(v -> exitApp());
        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginWebActivity.class)));

        int[] ids = {R.id.btn_marks, R.id.btn_exams, R.id.btn_homework,
                     R.id.btn_messages, R.id.btn_about};
        Class<?>[] targets = {MarksActivity.class, ExamsActivity.class, HomeworkActivity.class,
                              MessagesActivity.class, AboutActivity.class};
        for (int i = 0; i < ids.length; i++) {
            final Class<?> target = targets[i];
            View row = findViewById(ids[i]);
            Anim.press(row);
            row.setOnClickListener(v -> open(target));
        }
        Anim.press(btnLogin);
        Anim.press(btnExit);
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = new SessionStore(this);
        boolean logged = session.hasLoginUserName();
        btnLogin.setVisibility(logged ? View.GONE : View.VISIBLE);
        if (logged) {
            String name = session.get("www.zhixue.com", "loginUserName");
            tvUser.setText(name == null ? "已登录" : name.substring(name.indexOf('=') + 1));
        } else {
            tvUser.setText(R.string.not_logged_in);
        }
        if (firstLaunch) {
            firstLaunch = false;
            playEntrance();
        }
    }

    /** 入场动效：强调线展开 → 顶栏浮现 → 菜单逐项错峰上浮 */
    private void playEntrance() {
        Anim.wipeIn(accentLine, 0, 460);
        Anim.fadeUp(headerBar, 70);
        menuContainer.post(() -> Anim.staggerIn(menuContainer, 150, 55, 10f));
    }

    private void exitApp() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        moveTaskToBack(true);
    }

    @Override
    public void onBackPressed() {
        exitApp();
    }

    private void open(Class<?> cls) {
        if (!session.hasLoginUserName()) {
            startActivity(new Intent(this, LoginWebActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        startActivity(new Intent(this, cls));
    }
}
