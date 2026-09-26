package com.zhixue.wear;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zhixue.wear.net.ZhixueApi;
import com.zhixue.wear.store.SessionStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 智学网登录（WebView 方案）。
 *
 * 智学网的登录风控使用「极验 Geetest」行为验证码（captchaType=third），
 * 无法在原生代码里复刻，因此这里直接加载官方手机端登录页：
 *   1. 自动填充账号 / 密码（避免在手表上打字）
 *   2. 需要人机验证时，验证控件直接在页面上弹出，用户用手指完成滑块
 *   3. 登录成功后从 CookieManager 回收 Cookie，交给 SessionStore 持久化
 *
 * ⚠️ 重要：登录页会**在点击登录的瞬间**用 JS 写入 loginUserName Cookie
 * （`$.cookie("loginUserName", userName, {expires:7})`），它只是「记住用户名」，
 * 完全不代表登录成功。因此绝不能只凭该 Cookie 判定成功，
 * 必须拿 Cookie 去调一次 /container/getCurrentUser 校验服务端身份。
 */
public class LoginWebActivity extends Activity {

    private static final String PREF = "zhixue_creds";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 11; Wear OS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0 Mobile Safari/537.36";

    private WebView web;
    private ProgressBar progress;
    private TextView hint;
    private SessionStore session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean finished;
    /** 是否已经离开登录页（登录成功的必要条件之一） */
    private volatile boolean leftLoginPage;
    private volatile boolean verifying;

    private Runnable cookieWatcher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionStore(this);
        if (!isWebViewAvailable()) {
            Toast.makeText(this, "本机无 WebView，请改用 Cookie 登录", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, CookieLoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login_web);
        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        hint = findViewById(R.id.tv_hint);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUserAgentString(s.getUserAgentString() + " ZhixueWear/1.0");
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setTextZoom(100);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                autofill(v);
                // 只有真正离开登录页，才可能登录成功。
                // 停留在 wap_login.html 说明还在输入 / 验证码阶段，绝不能判定成功。
                if (url != null && !url.contains("wap_login")) {
                    leftLoginPage = true;
                    hint.setText("已提交，正在校验登录状态…");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return false;
            }
        });

        hint.setOnClickListener(v -> {
            autofill(web);
            Toast.makeText(this, "已重新填充账号密码", Toast.LENGTH_SHORT).show();
        });

        web.loadUrl(ZhixueApi.WAP_LOGIN);
        startCookieWatcher();
        scheduleRefill();
    }

    /** 是否具备 WebView（部分 Wear OS 镜像没有 WebView 提供程序） */
    private boolean isWebViewAvailable() {
        try {
            return WebView.getCurrentWebViewPackage() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------- 登录成功判定

    /**
     * 定时检查：只有「出现了 loginUserName Cookie」**且**「服务端认可该会话」才算成功。
     * 单看 Cookie 会误判 —— 页面点登录时就会写入它。
     */
    private void startCookieWatcher() {
        cookieWatcher = () -> {
            if (finished) return;
            String cookie = CookieManager.getInstance().getCookie(ZhixueApi.BASE);
            // 不再依赖「是否离开登录页」做闸门 —— 页面可能用 JS 跳转而不触发 onPageFinished，
            // 那样会漏判成功。这里改为周期性轮询，**唯一判据是双重接口校验是否通过**：
            // 校验不过就继续等（哪怕 URL 已经变了），校验通过才算真的登录成功。
            if (cookie != null && !cookie.isEmpty() && !verifying) {
                verifying = true;
                final String snapshot = cookie;
                new Thread(() -> {
                    boolean ok = verifySession(snapshot);
                    runOnUiThread(() -> {
                        if (finished) return;
                        verifying = false;
                        if (ok) {
                            onLoginSuccess(snapshot);
                        } else {
                            hint.setText(leftLoginPage ? "尚未通过校验，请完成滑块验证" : "请完成滑块验证后点登录");
                        }
                    });
                }, "verify-login").start();
            }
            handler.postDelayed(cookieWatcher, 3000);
        };
        handler.postDelayed(cookieWatcher, 1500);
    }

    /**
     * 用抓到的 Cookie 做**双重**校验，任一不通过都视为未登录：
     *   1. /container/getCurrentUser 必须返回非空 role
     *   2. /container/app/token/getToken 必须返回非空 token（App 后续所有请求都依赖它）
     * 只看第 1 项不够：可能存在「服务端认角色、但拿不到 XToken」的半失效会话。
     */
    private boolean verifySession(String cookieString) {
        try {
            final HttpUrl baseUrl = HttpUrl.parse(ZhixueApi.BASE);
            if (baseUrl == null) return false;
            final List<Cookie> jar = new ArrayList<>();
            for (String part : cookieString.split(";")) {
                Cookie c = Cookie.parse(baseUrl, part.trim());
                if (c != null) jar.add(c);
            }
            OkHttpClient client = new OkHttpClient.Builder()
                    .cookieJar(new CookieJar() {
                        @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) { }
                        @Override public List<Cookie> loadForRequest(HttpUrl url) { return jar; }
                    })
                    .build();

            // 1) 角色
            JSONObject user = call(client, new Request.Builder()
                    .url(ZhixueApi.CURRENT_USER)
                    .header("User-Agent", UA)
                    .build());
            if (user == null) return false;
            JSONObject result = user.optJSONObject("result");
            if (result == null) return false;
            if (result.optString("role", "").isEmpty()) return false;

            // 2) XToken（App 真正依赖的凭据）
            String guid = java.util.UUID.randomUUID().toString();
            String ts = String.valueOf(System.currentTimeMillis());
            String sign = com.zhixue.wear.net.ZhixueClient.md5(guid + ts + "iflytek!@#123student");
            JSONObject tokenResp = call(client, new Request.Builder()
                    .url(ZhixueApi.XTOKEN)
                    .header("User-Agent", UA)
                    .header("authbizcode", "0001")
                    .header("authguid", guid)
                    .header("authtimestamp", ts)
                    .header("authtoken", sign)
                    .build());
            if (tokenResp == null) return false;
            return !tokenResp.optString("result", "").isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 执行一次请求并解析 JSON，失败返回 null */
    private static JSONObject call(OkHttpClient client, Request req) {
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            return new JSONObject(resp.body().string());
        } catch (Throwable t) {
            return null;
        }
    }

    private void onLoginSuccess(String cookie) {
        finished = true;
        handler.removeCallbacks(cookieWatcher);
        session.importCookieString(cookie);
        session.ensureUname();
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ------------------------------------------------------------ 自动填充

    /**
     * 填充账号密码。
     *
     * ⚠️ 绝对不要在 WebView 里调用 el.focus()：对输入框 focus 会把软键盘弹出来。
     * 旧实现在页面加载后每 2 秒重填一次（共 10 次），每次都 focus，
     * 用户看到的现象就是「验证码弹窗时输入法每隔几秒弹一下」。
     * 现在：不 focus、不派发 blur，只赋值 + 补发 input/change/keyup，
     * 让页面 JS 模型同步到值即可（页面提交时本来也是读 DOM 的）。
     */
    private void autofill(WebView v) {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String user = sp.getString("username", "");
        String pwd = sp.getString("password", "");
        if (user.isEmpty() && pwd.isEmpty()) return;

        String js = "(function(){" +
                "function fire(el){" +
                  "['input','change','keyup'].forEach(function(t){" +
                    "try{el.dispatchEvent(new Event(t,{bubbles:true}));}catch(e){}" +
                  "});" +
                  "if(window.jQuery){try{jQuery(el).trigger('input').trigger('change').trigger('keyup');}catch(e){}}" +
                "}" +
                "function put(el,val){" +
                  "if(!el)return;" +
                  "if(!el.value){el.value=val;fire(el);}" +
                "}" +
                "put(document.getElementById('txtUserName')," + JSONObject.quote(user) + ");" +
                "put(document.getElementById('txtPassword')," + JSONObject.quote(pwd) + ");" +
                "})();";
        v.evaluateJavascript(js, null);
        hideKeyboard();
    }

    /** 登录页自身的 JS 会 .focus() 输入框，主动把键盘收起来 */
    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && web != null) imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 只补填一次：页面渲染可能晚于 onPageFinished。
     * （旧实现每 2 秒补一次共 10 次，正是「键盘反复弹出」的元凶）
     */
    private void scheduleRefill() {
        handler.postDelayed(() -> {
            if (!finished && web != null) autofill(web);
        }, 2000L);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        // 关键：不要用 super.onBackPressed()，否则本页会留在任务栈顶，
        // 下次从桌面打开 App 会被系统恢复成「又看到登录页」。
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finished = true;
        if (cookieWatcher != null) handler.removeCallbacks(cookieWatcher);
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
    }
}
