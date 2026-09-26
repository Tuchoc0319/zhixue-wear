package com.zhixue.wear;

import android.content.Intent;
import android.os.Bundle;

import com.zhixue.wear.net.ZhixueClient;
import com.zhixue.wear.net.ZhixueService;
import com.zhixue.wear.ui.ListActivityBase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 考试列表（最近一个有数据的学年，逐页拉取） */
public class ExamsActivity extends ListActivityBase {

    private static final int MAX_PAGES = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHeader("考试列表");
        load();
    }

    private void load() {
        loading(true);
        client.async(() -> {
            JSONObject year = ZhixueService.latestValidYear(client);
            if (year == null) throw new IOException("暂无考试数据");
            List<Row> rows = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                JSONObject resp = ZhixueService.pageExams(client, year, page);
                JSONObject result = resp.optJSONObject("result");
                if (result == null) break;
                JSONArray list = result.optJSONArray("examList");
                if (list == null || list.length() == 0) break;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject e = list.optJSONObject(i);
                    if (e == null) continue;
                    String name = e.optString("examName", "考试");
                    String id = e.optString("examId", "");
                    rows.add(new Row(name, id, e));
                }
                if (!result.optBoolean("hasNextPage", false)) break;
            }
            return rows;
        }, new ZhixueClient.Callback<List<Row>>() {
            @Override public void onSuccess(List<Row> data) { submit(data); }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    @Override
    protected void onRowClick(Row row) {
        JSONObject e = (JSONObject) row.tag;
        Intent i = new Intent(this, ExamDetailActivity.class);
        i.putExtra("examId", e.optString("examId", ""));
        i.putExtra("examName", row.title);
        startActivity(i);
    }
}
