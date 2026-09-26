package com.zhixue.wear.net;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhixue.wear.store.SessionStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 智学网 HTTP 客户端。
 *
 * 复刻 zhixuewang-python 的学生端行为：
 *  - 所有请求复用同一个 CookieJar（== requests.Session）
 *  - 业务接口携带 XToken 鉴权头，600 秒缓存
 */
public class ZhixueClient {

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Wear OS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private final OkHttpClient http;
    private final SessionStore session;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile String xToken;
    private volatile long xTokenAt;

    public ZhixueClient(SessionStore session) {
        this.session = session;
        this.http = new OkHttpClient.Builder()
                .cookieJar(session)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public SessionStore session() { return session; }

    // ------------------------------------------------------------ 同步原语

    private Request.Builder base(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", ZhixueApi.BASE + "/");
    }

    public Response execute(Request req) throws IOException {
        return http.newCall(req).execute();
    }

    public JSONObject getJson(String url, @Nullable HttpUrl.Builder params, boolean withAuth) throws IOException {
        Request.Builder b = base(url);
        if (params != null) b.url(params.build());
        if (withAuth) addAuth(b);
        return parse(execute(b.build()));
    }

    public JSONObject postForm(String url, FormBody body, boolean withAuth) throws IOException {
        Request.Builder b = base(url).post(body);
        if (withAuth) addAuth(b);
        return parse(execute(b.build()));
    }

    public JSONObject postJson(String url, String json, boolean withAuth, @Nullable String authHeaderValue) throws IOException {
        Request.Builder b = base(url)
                .post(okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json; charset=utf-8")));
        if (authHeaderValue != null) b.header("Authorization", authHeaderValue);
        if (withAuth) addAuth(b);
        return parse(execute(b.build()));
    }

    private JSONObject parse(Response r) throws IOException {
        try (ResponseBody body = r.body()) {
            String text = body == null ? "" : body.string();
            if (!r.isSuccessful()) {
                throw new IOException("HTTP " + r.code() + ": " + truncate(text));
            }
            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new IOException("响应不是 JSON (" + r.code() + "): " + truncate(text));
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    // --------------------------------------------------------------- 鉴权

    /** 生成 authguid / authtimestamp / authtoken 三件套（与 Python 端一致） */
    private void addAuth(Request.Builder b) throws IOException {
        String guid = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis());
        String sign = md5(guid + ts + "iflytek!@#123student");
        b.header("authbizcode", "0001")
         .header("authguid", guid)
         .header("authtimestamp", ts)
         .header("authtoken", sign)
         .header("XToken", getXToken(guid, ts, sign));
    }

    /** 获取 XToken，600 秒内复用 */
    private synchronized String getXToken(String guid, String ts, String sign) throws IOException {
        if (xToken != null && System.currentTimeMillis() - xTokenAt < 600_000L) {
            return xToken;
        }
        Request req = new Request.Builder()
                .url(ZhixueApi.XTOKEN)
                .header("User-Agent", UA)
                .header("authbizcode", "0001")
                .header("authguid", guid)
                .header("authtimestamp", ts)
                .header("authtoken", sign)
                .build();
        JSONObject o = parse(execute(req));
        String token = o.optString("result", "");
        if (token.isEmpty()) throw new IOException("获取 XToken 失败: " + o);
        xToken = token;
        xTokenAt = System.currentTimeMillis();
        return xToken;
    }

    /** 供作业等接口使用：把 XToken 作为查询参数传递（与 Python 端一致） */
    public String acquireXToken() throws IOException {
        String guid = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis());
        String sign = md5(guid + ts + "iflytek!@#123student");
        return getXToken(guid, ts, sign);
    }

    public void invalidateToken() {
        xToken = null;
        xTokenAt = 0;
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------- 异步工具

    public <T> void async(java.util.concurrent.Callable<T> work, Callback<T> cb) {
        pool.execute(() -> {
            try {
                T r = work.call();
                main.post(() -> cb.onSuccess(r));
            } catch (Exception e) {
                main.post(() -> cb.onError(e));
            }
        });
    }

    // ------------------------------------------------------------- 业务封装

    /** 学生基本信息；家长 / 教师账号会失败，返回 null */
    @Nullable
    public JSONObject studentInfo() throws IOException {
        JSONObject o = getJson(ZhixueApi.STUDENT_INFO, null, false);
        return o.optJSONObject("student");
    }

    /** 当前账号角色：student / teacher / parent */
    public String role() throws IOException {
        JSONObject o = getJson(ZhixueApi.CURRENT_USER, null, false);
        JSONObject r = o.optJSONObject("result");
        return r == null ? "" : r.optString("role", "");
    }
}
