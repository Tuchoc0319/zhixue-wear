package com.zhixue.wear.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * 会话存储：持久化智学网 Cookie，并提供 OkHttp 的 CookieJar。
 *
 * 对应 Python 库中的 requests.Session + session.cookies。
 * 额外维护 uname = base64(loginUserName)，这是智学网服务端要求的字段。
 */
public class SessionStore implements CookieJar {

    private static final String PREF = "zhixue_session";

    /** host -> (name -> raw "name=value") */
    private final Map<String, Map<String, String>> jar = new LinkedHashMap<>();
    private final SharedPreferences sp;

    public SessionStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    // ---------------------------------------------------------------- 持久化

    private void load() {
        jar.clear();
        String raw = sp.getString("cookies", "");
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            // 格式: host\tname=value
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String host = line.substring(0, tab);
            String kv = line.substring(tab + 1);
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String name = kv.substring(0, eq);
            put(host, name, kv);
        }
    }

    private void persist() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : jar.entrySet()) {
            for (String kv : e.getValue().values()) {
                if (kv == null || kv.isEmpty()) continue;
                sb.append(e.getKey()).append('\t').append(kv).append('\n');
            }
        }
        sp.edit().putString("cookies", sb.toString()).apply();
    }

    /** zhixue.com 的各级域名共用一个 Cookie 桶（等价于浏览器中的 .zhixue.com 域 Cookie） */
    private static String bucket(String host) {
        if (host == null) return "www.zhixue.com";
        return host.endsWith("zhixue.com") ? "www.zhixue.com" : host;
    }

    private void put(String host, String name, String kv) {
        host = bucket(host);
        Map<String, String> m = jar.get(host);
        if (m == null) {
            m = new LinkedHashMap<>();
            jar.put(host, m);
        }
        m.put(name, kv);
    }

    public void clear() {
        jar.clear();
        sp.edit().clear().apply();
    }

    public boolean isEmpty() {
        for (Map<String, String> m : jar.values()) if (!m.isEmpty()) return false;
        return true;
    }

    // --------------------------------------------------------- CookieJar 实现

    @NonNull
    @Override
    public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
        Map<String, String> m = jar.get(bucket(url.host()));
        if (m == null || m.isEmpty()) return Collections.emptyList();
        List<Cookie> out = new ArrayList<>();
        for (String kv : m.values()) {
            Cookie c = Cookie.parse(url, kv);
            if (c != null) out.add(c);
        }
        return out;
    }

    @Override
    public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        if (cookies.isEmpty()) return;
        for (Cookie c : cookies) {
            put(url.host(), c.name(), c.name() + "=" + c.value());
        }
        persist();
        ensureUname();
    }

    // ------------------------------------------------------------- 对外接口

    /**
     * 由浏览器 Cookie 字符串导入，例如 "a=1; b=2"。
     * 默认写入 www.zhixue.com；包含下划线/异常的条目会被忽略。
     */
    public void importCookieString(@Nullable String cookieString) {
        if (cookieString == null) return;
        importCookieString(cookieString, "www.zhixue.com");
    }

    public void importCookieString(@Nullable String cookieString, String host) {
        if (cookieString == null) return;
        for (String part : cookieString.split(";")) {
            String kv = part.trim();
            if (kv.isEmpty()) continue;
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            put(host, kv.substring(0, eq).trim(), kv);
        }
        persist();
        ensureUname();
    }

    /**
     * 服务端要求 cookie 中存在 uname = base64(loginUserName)。
     * 缺少时账号信息接口会失败。
     */
    public void ensureUname() {
        String loginUserName = get("www.zhixue.com", "loginUserName");
        if (loginUserName == null || loginUserName.isEmpty()) return;
        String value = loginUserName.substring(loginUserName.indexOf('=') + 1);
        String uname = Base64.encodeToString(value.getBytes(), Base64.NO_WRAP);
        put("www.zhixue.com", "uname", "uname=" + uname);
        persist();
    }

    /** 读取某个 host 下的 cookie 原始值（不含 name=） */
    @Nullable
    public String get(String host, String name) {
        Map<String, String> m = jar.get(bucket(host));
        if (m == null) return null;
        String kv = m.get(name);
        if (kv == null) return null;
        return kv.substring(kv.indexOf('=') + 1);
    }

    /** 生成完整 Cookie 头（用于 WebView / 调试） */
    public String toCookieHeader() {
        StringBuilder sb = new StringBuilder();
        Map<String, String> m = jar.get("www.zhixue.com");
        if (m != null) for (String kv : m.values()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(kv);
        }
        return sb.toString();
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : jar.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue().keySet()).append('\n');
        }
        return sb.toString();
    }

    public boolean hasLoginUserName() {
        String v = get("www.zhixue.com", "loginUserName");
        return v != null && v.length() > 1;
    }
}
