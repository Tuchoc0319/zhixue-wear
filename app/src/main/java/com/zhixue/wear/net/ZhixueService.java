package com.zhixue.wear.net;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * 学生端高层业务流程（与 Python 端 get_latest_exam / get_self_mark 等对应）。
 *
 * 智学网的学年 / 考试接口需要按学年轮询，这里做了封装。
 */
public final class ZhixueService {

    private ZhixueService() {}

    /**
     * 找到最近一个「有考试数据」的学年，并返回该学年的 recent_exam 结果。
     * 返回对象结构：{ "examInfo": {...}, "gradeCode": "..." }
     */
    @Nullable
    public static JSONObject latestExam(ZhixueClient c) throws IOException {
        JSONObject yearsResp = c.getJson(ZhixueApi.ACADEMIC_YEAR, null, true);
        JSONArray years = yearsResp.optJSONArray("result");
        if (years == null) return null;
        for (int i = 0; i < years.length(); i++) {
            JSONObject y = years.optJSONObject(i);
            if (y == null) continue;
            String begin = y.optString("beginTime", "");
            String end = y.optString("endTime", "");
            if (begin.isEmpty() || end.isEmpty()) continue;
            JSONObject r = c.getJson(ZhixueApi.RECENT_EXAM,
                    ZhixueApi.q(ZhixueApi.RECENT_EXAM)
                            .addQueryParameter("startSchoolYear", begin)
                            .addQueryParameter("endSchoolYear", end),
                    true);
            JSONObject res = r.optJSONObject("result");
            if (res != null && res.optJSONObject("examInfo") != null) {
                try {
                    res.put("_beginTime", begin);
                    res.put("_endTime", end);
                    res.put("_yearName", y.optString("name", ""));
                    res.put("_yearCode", y.optString("code", ""));
                } catch (org.json.JSONException ignored) {
                    // put 只会因 key 为 null 失败，这里 key 固定，忽略即可
                }
                return res;
            }
        }
        return null;
    }

    /** 指定考试的完整成绩（result.paperList + result.totalScore） */
    public static JSONObject reportMain(ZhixueClient c, String examId) throws IOException {
        return c.getJson(ZhixueApi.REPORT_MAIN,
                ZhixueApi.q(ZhixueApi.REPORT_MAIN).addQueryParameter("examId", examId),
                true);
    }

    /** 错题本；errorCode != 0 时返回 null */
    @Nullable
    public static JSONObject errorBook(ZhixueClient c, String examId, String topicSetId) throws IOException {
        JSONObject r = c.getJson(ZhixueApi.ERROR_BOOK,
                ZhixueApi.q(ZhixueApi.ERROR_BOOK)
                        .addQueryParameter("examId", examId)
                        .addQueryParameter("paperId", topicSetId),
                true);
        if (r.optInt("errorCode", 0) != 0) return null;
        return r.optJSONObject("result");
    }

    /**
     * 找到最近一个「有考试数据」的学年（对应 Python 的 _get_latest_valid_academic_year）。
     * 返回一个包含 name/code/beginTime/endTime 的 JSONObject。
     */
    @Nullable
    public static JSONObject latestValidYear(ZhixueClient c) throws IOException {
        JSONObject yearsResp = c.getJson(ZhixueApi.ACADEMIC_YEAR, null, true);
        JSONArray years = yearsResp.optJSONArray("result");
        if (years == null) return null;
        for (int i = 0; i < years.length(); i++) {
            JSONObject y = years.optJSONObject(i);
            if (y == null) continue;
            String begin = y.optString("beginTime", "");
            String end = y.optString("endTime", "");
            if (begin.isEmpty() || end.isEmpty()) continue;
            JSONObject r = c.getJson(ZhixueApi.RECENT_EXAM,
                    ZhixueApi.q(ZhixueApi.RECENT_EXAM)
                            .addQueryParameter("startSchoolYear", begin)
                            .addQueryParameter("endSchoolYear", end),
                    true);
            if (r.optJSONObject("result") != null) return y;
        }
        return null;
    }

    /** 某学年某一页的考试列表 */
    public static JSONObject pageExams(ZhixueClient c, JSONObject year, int pageIndex) throws IOException {
        return c.getJson(ZhixueApi.EXAM_LIST,
                ZhixueApi.q(ZhixueApi.EXAM_LIST)
                        .addQueryParameter("pageIndex", String.valueOf(pageIndex))
                        .addQueryParameter("pageSize", "10")
                        .addQueryParameter("startSchoolYear", year.optString("beginTime", ""))
                        .addQueryParameter("endSchoolYear", year.optString("endTime", "")),
                true);
    }
}
