package com.zhixue.wear;

import android.app.AlertDialog;
import android.os.Bundle;

import com.zhixue.wear.net.ZhixueClient;
import com.zhixue.wear.net.ZhixueService;
import com.zhixue.wear.ui.ListActivityBase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 单场考试的科目成绩；点击科目可查看错题本 */
public class ExamDetailActivity extends ListActivityBase {

    private String examId;
    private String examName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        examId = getIntent().getStringExtra("examId");
        examName = getIntent().getStringExtra("examName");
        setHeader(examName == null ? "考试详情" : examName);
        load();
    }

    private void load() {
        loading(true);
        client.async(() -> {
            if (examId == null || examId.isEmpty()) throw new IOException("缺少考试 ID");
            JSONObject resp = ZhixueService.reportMain(client, examId);
            JSONObject result = resp.optJSONObject("result");
            if (result == null) throw new IOException("无成绩数据");
            JSONArray papers = result.optJSONArray("paperList");
            List<Row> rows = new ArrayList<>();
            if (papers != null) {
                for (int i = 0; i < papers.length(); i++) {
                    JSONObject p = papers.optJSONObject(i);
                    if (p == null) continue;
                    rows.add(new Row(p.optString("subjectName", "?") + "  " + trim(p.optDouble("userScore", 0)),
                            "满分 " + trim(p.optDouble("standardScore", 0)) + " · 点按看错题", p));
                }
            }
            JSONObject total = result.optJSONObject("totalScore");
            if (total != null) {
                rows.add(new Row(total.optString("subjectName", "总分") + "  " + trim(total.optDouble("userScore", 0)),
                        "满分 " + trim(total.optDouble("standardScore", 0)), null));
            }
            return rows;
        }, new ZhixueClient.Callback<List<Row>>() {
            @Override public void onSuccess(List<Row> data) { submit(data); }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    @Override
    protected void onRowClick(Row row) {
        JSONObject p = (JSONObject) row.tag;
        if (p == null) return;
        String topicSetId = p.optString("paperId", "");
        String subjectName = p.optString("subjectName", "");
        loading(true);
        client.async(() -> {
            JSONObject book = ZhixueService.errorBook(client, examId, topicSetId);
            if (book == null) throw new IOException("该学科暂无错题数据");
            JSONArray topics = book.optJSONObject("wrongTopicAnalysis") == null
                    ? null : book.optJSONObject("wrongTopicAnalysis").optJSONArray("topicList");
            List<Row> rows = new ArrayList<>();
            if (topics != null) {
                for (int i = 0; i < topics.length(); i++) {
                    JSONObject t = topics.optJSONObject(i);
                    if (t == null) continue;
                    rows.add(new Row("第 " + t.optString("disTitleNumber", "?") + " 题",
                            "得分 " + trim(t.optDouble("score", 0)) + " / " + trim(t.optDouble("standardScore", 0))));
                }
            }
            return rows;
        }, new ZhixueClient.Callback<List<Row>>() {
            @Override public void onSuccess(List<Row> data) {
                loading(false);
                showErrorBookDialog(data);
            }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    private void showErrorBookDialog(List<Row> rows) {
        if (rows.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("该学科暂无错题数据").setPositiveButton("好", null).show();
            return;
        }
        String[] items = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) items[i] = rows.get(i).title + "\n" + rows.get(i).sub;
        new AlertDialog.Builder(this)
                .setTitle("错题 (" + rows.size() + ")")
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
