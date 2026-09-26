package com.zhixue.wear;

import android.os.Bundle;

import com.zhixue.wear.net.ZhixueClient;
import com.zhixue.wear.net.ZhixueService;
import com.zhixue.wear.ui.ListActivityBase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 最近一次考试成绩：大分数 + 各科进度条（对应参考图 2） */
public class MarksActivity extends ListActivityBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHeader("考试成绩");
        load();
    }

    private void load() {
        loading(true);
        client.async(() -> {
            JSONObject recent = ZhixueService.latestExam(client);
            if (recent == null) throw new IOException("暂无考试数据");
            JSONObject examInfo = recent.optJSONObject("examInfo");
            String examId = examInfo == null ? "" : examInfo.optString("examId");
            if (examId.isEmpty()) throw new IOException("未获取到考试 ID");

            JSONObject report = ZhixueService.reportMain(client, examId);
            JSONObject result = report.optJSONObject("result");
            if (result == null) throw new IOException("成绩接口无数据");

            JSONArray papers = result.optJSONArray("paperList");
            List<Row> rows = new ArrayList<>();
            float sumGot = 0f, sumFull = 0f;
            if (papers != null) {
                for (int i = 0; i < papers.length(); i++) {
                    JSONObject p = papers.optJSONObject(i);
                    if (p == null) continue;
                    String name = p.optString("subjectName", "?");
                    float got = (float) p.optDouble("userScore", 0);
                    float full = (float) p.optDouble("standardScore", 0);
                    sumGot += got;
                    sumFull += full;
                    rows.add(Row.score(name, got, full));
                }
            }

            JSONObject total = result.optJSONObject("totalScore");
            float totalGot = total != null ? (float) total.optDouble("userScore", 0) : sumGot;
            float totalFull = total != null ? (float) total.optDouble("standardScore", 0) : sumFull;
            if (totalFull <= 0) totalFull = sumFull;

            String examName = examInfo == null ? "" : examInfo.optString("examName", "");
            boolean hasDecimal = (totalGot != Math.floor(totalGot));

            return new Result(rows, totalGot, totalFull, hasDecimal, examName);
        }, new ZhixueClient.Callback<Result>() {
            @Override public void onSuccess(Result d) {
                setSubtitle(d.examName);
                if (d.totalFull > 0) {
                    setHero(d.totalGot, d.hasDecimal,
                            "/" + trim(d.totalFull),
                            d.rows.size() + " 个科目");
                }
                submit(d.rows);
            }
            @Override public void onError(Exception e) { showError(e); }
        });
    }

    private static String trim(float f) {
        if (f == Math.floor(f) && !Float.isInfinite(f)) return String.valueOf((long) f);
        return String.format(java.util.Locale.US, "%.1f", f);
    }

    private static class Result {
        final List<Row> rows; final float totalGot, totalFull;
        final boolean hasDecimal; final String examName;
        Result(List<Row> r, float g, float f, boolean d, String n) {
            rows = r; totalGot = g; totalFull = f; hasDecimal = d; examName = n;
        }
    }
}
