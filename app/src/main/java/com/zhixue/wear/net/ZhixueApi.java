package com.zhixue.wear.net;

/** 智学网接口地址（与 zhixuewang-python 1.5.1 的 urls.py 保持一致） */
public final class ZhixueApi {

    public static final String BASE = "https://www.zhixue.com";
    public static final String APP_BASE = "https://mhw.zhixue.com";

    // 账号
    public static final String CURRENT_USER = BASE + "/container/getCurrentUser";
    public static final String STUDENT_INFO = BASE + "/container/container/student/account/";
    public static final String TEACHER_INFO = BASE + "/container/container/teacher/teacherAccountNew";
    public static final String TEACHER_ADVANCED = BASE + "/paperfresh/api/common/getCurrentUser";

    // 鉴权
    public static final String XTOKEN = BASE + "/container/app/token/getToken";

    // 学生 - 考试 / 成绩
    public static final String ACADEMIC_YEAR = BASE + "/zhixuebao/base/common/academicYear";
    public static final String EXAM_LIST = BASE + "/zhixuebao/report/exam/getUserExamList";
    public static final String RECENT_EXAM = BASE + "/zhixuebao/report/exam/getRecentExam";
    public static final String REPORT_MAIN = BASE + "/zhixuebao/report/exam/getReportMain";
    public static final String CHECKSHEET = BASE + "/zhixuebao/report/checksheet/";
    public static final String SUBJECT_DIAGNOSIS = BASE + "/zhixuebao/report/exam/getSubjectDiagnosis";
    public static final String ERROR_BOOK = BASE + "/zhixuebao/report/paper/getLostTopicAndAnalysis";

    // 班级 / 同学
    public static final String CLAZZ_LIST = BASE + "/zhixuebao/zhixuebao/friendmanage/";
    public static final String CLASSMATES = BASE + "/container/contact/student/students";

    // 作业
    public static final String HOMEWORK_LIST = APP_BASE + "/homework_middle_service/stuapp/getStudentHomeWorkList";
    public static final String HOMEWORK_EXERCISE = APP_BASE + "/hw/manage/homework/redeploy";
    public static final String HOMEWORK_BANK = APP_BASE + "/hwreport/question/listView";

    // 私信
    public static final String PERSONAL_MESSAGES = BASE + "/container/personal/getPersonalMessage/";

    // 登录页
    public static final String WAP_LOGIN = "https://www.zhixue.com/wap_login.html";

    /** 构造带查询参数的 URL 构建器 */
    public static okhttp3.HttpUrl.Builder q(String url) {
        okhttp3.HttpUrl u = okhttp3.HttpUrl.parse(url);
        if (u == null) throw new IllegalArgumentException("bad url: " + url);
        return u.newBuilder();
    }

    /** 便捷方法：GET 请求的完整 URL */
    public static String withParam(String url, String k, String v) {
        return q(url).addQueryParameter(k, v).build().toString();
    }

    private ZhixueApi() {}
}
