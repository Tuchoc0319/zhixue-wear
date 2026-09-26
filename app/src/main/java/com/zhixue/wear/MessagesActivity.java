package com.zhixue.wear;

import android.os.Bundle;

import com.zhixue.wear.net.ZhixueApi;
import com.zhixue.wear.net.ZhixueClient;
import com.zhixue.wear.ui.ListActivityBase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 私信列表 */
public class MessagesActivity extends ListActivityBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHeader("私信");
        load();
    }

    private void load() {
        loading(true);
        client.async(() -> {
            long now = System.currentTimeMillis();
            JSONObject resp = client.getJson(
                    ZhixueApi.q(ZhixueApi.PERSONAL_MESSAGES)
                            .addQueryParameter("_t", String.valueOf(now))
                            .addQueryParameter("pageIndex", "1")
                            .addQueryParameter("pageSize", "50")
                            .addQueryParameter("type", "personalMsg")
                            .addQueryParameter("_", String.valueOf(now - 300))
                            .build().toString(),
                    null, false);

            List<Row> rows = new ArrayList<>();
            JSONObject pager = resp.optJSONObject("pagerMessages");
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            if (pager != null) {
                JSONArray list = pager.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        JSONObject notify = item.optJSONObject("notify");
                        if (notify == null) continue;
                        String content = notify.optString("content", "");
                        long t = notify.optLong("createTime", 0);
                        JSONObject sender = parseSender(notify.optString("senderDetail", ""));
                        String from = sender == null ? "" : sender.optString("userName", "");
                        String when = t > 0 ? sdf.format(new Date(t)) : "";
                        rows.add(new Row(content, (from.isEmpty() ? "" : from + " · ") + when));
                    }
                }
            }
            return rows;
        }, new ZhixueClient.Callback<List<Row>>() {
            @Override public void onSuccess(List<Row> data) { submit(data); }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    private static JSONObject parseSender(String raw) {
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
