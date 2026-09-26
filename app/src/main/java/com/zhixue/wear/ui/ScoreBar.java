package com.zhixue.wear.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.zhixue.wear.R;

/**
 * 学科成绩条：圆角底槽 + 柠檬绿填充，支持动画填充。
 *
 * 外观来自参考图逐像素采样：底槽 #1D1D1D、填充 #CBEE59、高 4dp、圆角 2dp。
 */
public class ScoreBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private float ratio = 0f;     // 当前显示进度 0..1
    private float radius = 0f;
    private ValueAnimator anim;

    public ScoreBar(Context c) { this(c, null); }

    public ScoreBar(Context c, @Nullable AttributeSet a) {
        super(c, a);
        track.setColor(c.getColor(R.color.zhixue_track));
        fill.setColor(c.getColor(R.color.zhixue_blue));
    }

    /** 直接设置，不做动画 */
    public void setRatio(float v) {
        cancel();
        ratio = clamp(v);
        invalidate();
    }

    /**
     * 动画填充。
     * @param delay  错峰延迟（毫秒）
     * @param dur    时长
     * @param spring true 用回弹插值（更有"弹"感）
     */
    public void animateTo(float v, long delay, long dur, boolean spring) {
        cancel();
        final float to = clamp(v);
        anim = ValueAnimator.ofFloat(ratio, to);
        anim.setDuration(dur);
        anim.setStartDelay(delay);
        anim.setInterpolator(spring ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            ratio = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void cancel() {
        if (anim != null) { anim.cancel(); anim = null; }
    }

    private static float clamp(float v) {
        if (Float.isNaN(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        radius = h / 2f;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float h = getHeight(), w = getWidth();
        if (h <= 0 || w <= 0) return;
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, radius, radius, track);
        float fw = w * ratio;
        if (fw > 0.5f) {
            // 填充宽度不足一个圆角时也要能看出形状
            float eff = Math.max(fw, h);
            r.set(0, 0, Math.min(eff, w), h);
            cv.drawRoundRect(r, radius, radius, fill);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }
}
