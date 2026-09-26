package com.zhixue.wear.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** 统一动效工具：错峰入场 / 数字滚动 / 强调线展开 / 按压反馈。 */
public final class Anim {

    private Anim() {}

    /** 列表错峰入场：逐项淡入 + 自下而上浮现 */
    public static void staggerIn(ViewGroup parent, long startDelay, long step, float dpUp) {
        int n = parent.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = parent.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dpUp * c.getResources().getDisplayMetrics().density);
            c.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(startDelay + i * step)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    /** 单个视图淡入上浮 */
    public static void fadeUp(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(10f * v.getResources().getDisplayMetrics().density);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                .setDuration(300).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    /** 数字滚动（带缓动），用于大分数 */
    public static void countUp(final TextView tv, final float from, final float to,
                               long delay, long dur, final boolean decimal) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(dur);
        a.setStartDelay(delay);
        a.setInterpolator(new DecelerateInterpolator(2.2f));
        a.addUpdateListener(an -> {
            float p = (float) an.getAnimatedValue();
            float v = from + (to - from) * p;
            tv.setText(decimal ? trim1(v) : String.valueOf(Math.round(v)));
        });
        tv.setText(decimal ? trim1(from) : String.valueOf(Math.round(from)));
        a.start();
    }

    private static String trim1(float v) {
        if (v == Math.floor(v) && !Float.isInfinite(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** 强调线/进度条从左侧展开 */
    public static void wipeIn(View v, long delay, long dur) {
        v.setPivotX(0f);
        v.setScaleX(0f);
        v.setAlpha(0.6f);
        v.animate().scaleX(1f).alpha(1f).setStartDelay(delay)
                .setDuration(dur).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    /** 按压反馈：按下缩到 0.96，松开回弹。返回 false 不拦截点击 */
    public static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
    }

    /** 强调色闪一下（用于数据更新提示） */
    public static void pulse(View v, long delay) {
        AnimatorSet s = new AnimatorSet();
        ObjectAnimator up = ObjectAnimator.ofFloat(v, "alpha", 0.35f, 1f);
        s.play(up);
        s.setStartDelay(delay);
        s.setDuration(420);
        s.start();
    }

    /** 整组错峰：对 ViewGroup 的直接子 View 依次 wipeIn */
    public static void wipeGroup(ViewGroup g, long startDelay, long step, long dur) {
        for (int i = 0; i < g.getChildCount(); i++) {
            wipeIn(g.getChildAt(i), startDelay + i * step, dur);
        }
    }
}
