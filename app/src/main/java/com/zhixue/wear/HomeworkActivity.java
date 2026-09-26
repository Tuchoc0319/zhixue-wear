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

/** 作业列表（未完成 / 已完成） */
public class HomeworkActivity extends ListActivityBase {

    private boolean isComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHeader("作业（未完成）");
        load();
    }

    private void load() {
        loading(true);
        final boolean complete = isComplete;
        client.async(() -> {
            String token = client.acquireXToken();
            JSONObject resp = client.getJson(
                    ZhixueApi.q(ZhixueApi.HOMEWORK_LIST)
                            .addQueryParameter("pageIndex", "1")
                            .addQueryParameter("pageSize", "20")
                            .addQueryParameter("completeStatus", complete ? "1" : "0")
                            .addQueryParameter("subjectCode", "-1")
                            .addQueryParameter("createTime", "0")
                            .addQueryParameter("token", token)
                            .build().toString(),
                    null, false);

            JSONObject result = resp.optJSONObject("result");
            if (result == null) throw new IOException("作业接口无数据");
            JSONArray list = result.optJSONArray("list");
            List<Row> rows = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject h = list.optJSONObject(i);
                    if (h == null) continue;
                    String title = h.optString("hwTitle", "作业");
                    double end = h.optDouble("endTime", 0) / 1000.0;
                    String when = end > 0 ? sdf.format(new Date((long) (end * 1000))) : "";
                    JSONObject type = h.optJSONObject("homeWorkTypeDTO");
                    String typeName = type == null ? "" : type.optString("typeName", "");
                    rows.add(new Row(title, (typeName.isEmpty() ? "" : typeName + " · ") + when, h));
                }
            }
            return rows;
        }, new ZhixueClient.Callback<List<Row>>() {
            @Override public void onSuccess(List<Row> data) { submit(data); }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    @Override
    protected void onRowClick(Row row) {
        isComplete = !isComplete;
        setHeader(isComplete ? "作业（已完成）" : "作业（未完成）");
        load();
    }

    @Override
    protected String emptyText() {
        return isComplete ? "暂无已完成作业" : "暂无未完成作业";
    }
}
