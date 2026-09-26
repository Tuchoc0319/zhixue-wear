package com.zhixue.wear.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhixue.wear.LoginWebActivity;
import com.zhixue.wear.R;
import com.zhixue.wear.net.ZhixueClient;
import com.zhixue.wear.store.SessionStore;

import java.util.ArrayList;
import java.util.List;

/** 带顶栏 / 三态 / 可选大分数区的通用列表页。 */
public abstract class ListActivityBase extends Activity {

    /** 列表行：普通模式（title+sub）或成绩模式（name+bar+value） */
    public static class Row {
        public final String title, sub;
        public final Object tag;

        public final boolean scoreMode;
        public final String scoreName, scoreValue;
        public final float ratio;      // 0..1

        public Row(String title, String sub) { this(title, sub, null); }

        public Row(String title, String sub, Object tag) {
            this.title = title; this.sub = sub; this.tag = tag;
            this.scoreMode = false;
            this.scoreName = null; this.scoreValue = null; this.ratio = 0f;
        }

        private Row(String name, String value, float ratio) {
            this.title = null; this.sub = null; this.tag = null;
            this.scoreMode = true;
            this.scoreName = name; this.scoreValue = value; this.ratio = ratio;
        }

        /** 成绩行：学科名 + 进度条 + "得分 / 满分" */
        public static Row score(String name, float got, float full) {
            float r = full > 0 ? got / full : 0f;
            String v = trim(got) + " / " + trim(full);
            return new Row(name, v, r);
        }

        static String trim(float f) {
            if (f == Math.floor(f) && !Float.isInfinite(f)) return String.valueOf((long) f);
            return String.format(java.util.Locale.US, "%.1f", f);
        }
    }

    protected SessionStore session;
    protected ZhixueClient client;

    private Adapter adapter;
    private TextView tvTitle, tvEmpty, tvHeaderSub;
    private ProgressBar progress;
    private View heroBlock, accentLine, headerBar;
    private TextView tvHeroScore, tvHeroUnit, tvHeroCaption;
    private RecyclerView list;

    private int animatedUntil = 0;   // 错峰入场进度

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        session = new SessionStore(this);
        client = new ZhixueClient(session);

        tvTitle = findViewById(R.id.tv_title);
        tvEmpty = findViewById(R.id.tv_empty);
        progress = findViewById(R.id.progress);
        heroBlock = findViewById(R.id.hero_block);
        tvHeroScore = findViewById(R.id.tv_hero_score);
        tvHeroUnit = findViewById(R.id.tv_hero_unit);
        tvHeroCaption = findViewById(R.id.tv_hero_caption);
        tvHeaderSub = findViewById(R.id.tv_header_sub);
        accentLine = findViewById(R.id.accent_line);
        headerBar = findViewById(R.id.header_bar);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        Anim.wipeIn(accentLine, 0, 420);
        Anim.fadeUp(headerBar, 60);
    }

    // ---------------------------------------------------------------- 顶栏

    protected void setHeader(CharSequence text) { tvTitle.setText(text); }

    /** 标题下方的小标题（考试名称等），为空则隐藏 —— 避免长名字挤占标题 */
    protected void setSubtitle(CharSequence text) {
        if (text == null || text.length() == 0) {
            tvHeaderSub.setVisibility(View.GONE);
        } else {
            tvHeaderSub.setText(text);
            tvHeaderSub.setVisibility(View.VISIBLE);
            Anim.fadeUp(tvHeaderSub, 140);
        }
    }

    /** 大分数区：大数字滚动 + 单位 + 说明行 */
    protected void setHero(float score, boolean decimal, String unit, String caption) {
        heroBlock.setVisibility(View.VISIBLE);
        tvHeroUnit.setText(unit == null ? "" : unit);
        tvHeroCaption.setText(caption == null ? "" : caption);
        Anim.fadeUp(heroBlock, 80);
        Anim.countUp(tvHeroScore, 0, score, 200, 900, decimal);
        if (caption != null && caption.length() > 0) Anim.fadeUp(tvHeroCaption, 500);
    }

    // ---------------------------------------------------------------- 状态

    protected void loading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    protected void submit(List<Row> rows) {
        loading(false);
        animatedUntil = 0;
        adapter.items.clear();
        if (rows != null) adapter.items.addAll(rows);
        adapter.notifyDataSetChanged();
        tvEmpty.setText(emptyText());
        tvEmpty.setVisibility(adapter.items.isEmpty() ? View.VISIBLE : View.GONE);
        if (adapter.items.isEmpty()) Anim.fadeUp(tvEmpty, 80);
    }

    protected void showError(Exception e) {
        loading(false);
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();

        if (isSessionExpired(msg)) {
            session.clear();
            Intent i = new Intent(this, LoginWebActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
            return;
        }

        adapter.items.clear();
        adapter.notifyDataSetChanged();
        tvEmpty.setText("加载失败" + ((char) 10) + msg);
        tvEmpty.setVisibility(View.VISIBLE);
        Anim.fadeUp(tvEmpty, 60);
    }

    protected static boolean isSessionExpired(String msg) {
        if (msg == null) return false;
        String m = msg.replace(" ", "");
        return m.contains("3002")
                || m.contains("未登录或登录失效")
                || m.contains("登录失效")
                || m.contains("未登录")
                || m.contains("登录已过期");
    }

    protected String emptyText() { return "暂无数据"; }

    protected void onRowClick(Row row) { }

    // --------------------------------------------------------------- 适配器

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final List<Row> items = new ArrayList<>();

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = items.get(position);
            h.itemView.setOnClickListener(v -> onRowClick(r));

            if (r.scoreMode) {
                h.listMode.setVisibility(View.GONE);
                h.scoreMode.setVisibility(View.VISIBLE);
                h.scoreName.setText(r.scoreName);
                h.scoreValue.setText(r.scoreValue);
                // 进度条错峰弹入（延迟随行号递增）
                h.bar.animateTo(r.ratio, 120 + h.getBindingAdapterPosition() * 70L, 620, true);
            } else {
                h.scoreMode.setVisibility(View.GONE);
                h.listMode.setVisibility(View.VISIBLE);
                h.title.setText(r.title);
                if (r.sub == null || r.sub.isEmpty()) {
                    h.sub.setVisibility(View.GONE);
                } else {
                    h.sub.setVisibility(View.VISIBLE);
                    h.sub.setText(r.sub);
                }
            }

            // 错峰入场：每个 item 首次绑定时淡入上浮一次
            if (position == animatedUntil) {
                animatedUntil++;
                h.itemView.setAlpha(0f);
                h.itemView.setTranslationY(10f * h.itemView.getResources().getDisplayMetrics().density);
                h.itemView.animate().alpha(1f).translationY(0f)
                        .setStartDelay(80 + position * 55L)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                        .start();
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final View listMode, scoreMode;
            final TextView title, sub, scoreName, scoreValue;
            final ScoreBar bar;

            VH(View v) {
                super(v);
                listMode = v.findViewById(R.id.row_list_mode);
                scoreMode = v.findViewById(R.id.row_score_mode);
                title = v.findViewById(R.id.tv_row_title);
                sub = v.findViewById(R.id.tv_row_sub);
                scoreName = v.findViewById(R.id.tv_score_name);
                scoreValue = v.findViewById(R.id.tv_score_value);
                bar = v.findViewById(R.id.bar_row);
                Anim.press(v);
            }
        }
    }
}
