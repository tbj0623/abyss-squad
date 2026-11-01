package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 主菜单 / 营地入口。
 *
 * <p>信息层级：左上品牌 → 中左行动区（主按钮视觉权重最高）→ 右侧生态插画 →
 * 底部玩家总览条。所有可点击元素高度 ≥ 88 逻辑像素的视觉占位（按钮 64~82，
 * 加上外层留白后满足触控要求）。</p>
 *
 * <p>动作码：返回 {@link Ui#A_START} 等导航码；内部状态切换返回 -1。</p>
 */
public final class MenuScreen {

    // ── screen 局部控件 id（仅本屏内唯一）──
    private static final int C_START = 1, C_CONTINUE = 2, C_HOST = 3, C_JOIN = 4,
            C_CODEX = 5, C_META = 6, C_SETTINGS = 7, C_SOUND = 8, C_GUIDE = 9;

    /** 最近一次 draw 时命中的控件 id（只读，调试用）。 */
    public int hit = -1;

    private int lastSave = -1;

    // 插画交叉淡入：6 张生态，每 5 秒切换，边缘 0.9 秒过渡
    private static final float SLIDE = 5f, FADE = .9f;

    public MenuScreen() {
    }

    /**
     * @param stats {dust, marks, bestFloor, runs, wins}
     */
    public void draw(Canvas c, Ui ui, float time, int[] stats, boolean hasSave, boolean sound) {
        ui.clearHits();
        hit = -1;
        if (c == null || ui == null) return;

        int[] st = safe(stats, 5);
        drawBackdrop(c, ui, time);

        // ───────────────── 顶栏 ─────────────────
        float pulse = (float) (Math.sin(time * 1.7f) * .5f + .5f);
        ui.glow(c, 82, 46, 30 + pulse * 7f, ui.alpha(Ui.MINT, 70));
        ui.diamond(c, 82, 46, 15, Ui.MINT);
        ui.diamond(c, 82, 46, 7, Ui.BG);
        ui.text(c, "深渊小队", 112, 56, 25, Ui.WHITE, true);
        ui.small(c, "ABYSS SQUAD", 112, 74, 11, Ui.MUTED);
        ui.right(c, "v1.0  ·  重制版", 1376, 50, 14, Ui.MUTED, false);
        ui.right(c, Content.MODE_EN[0] + "  READY", 1376, 70, 10, ui.alpha(Ui.MINT, 160), false);
        ui.divider(c, 64, 92, 1312);

        // ───────────────── 左：品牌主张 ＋ 行动区 ─────────────────
        ui.pill(c, 64, 118, 224, "单机远征  ·  全新篇章", Ui.MINT);

        ui.title(c, "下一站，", 62, 210, 52);
        ui.title(c, "一起下地牢。", 62, 272, 52);
        ui.text(c, "捡起星光，搭出你的流派。", 66, 314, 21, Ui.MUTED, false);
        ui.text(c, "一个人出发，也能和三位朋友并肩。", 66, 344, 19, ui.alpha(Ui.MUTED, 210), false);

        // 主行动：呼吸微动的高权重按钮
        float breathe = 1f + (float) (Math.sin(time * 2.1) * .006f);
        float bw = 540 * breathe, bh = 82 * breathe;
        float bx = 64 - (bw - 540) / 2f, by = 384 - (bh - 82) / 2f;
        ui.glow(c, bx + bw / 2, by + bh / 2, 118, ui.fade(ui.alpha(Ui.MINT, 34), .55f + .45f * pulse));
        ui.button(c, C_START, bx, by, bw, bh, "开始远征    →", true, true);

        boolean hasSaveNow = hasSave;
        lastSave = hasSaveNow ? 1 : 0;
        if (hasSaveNow) {
            ui.button(c, C_CONTINUE, 64, 486, 540, 66, "继续上次远征", false, true);
            // 存档状态由底部总览条统一承载（「存档待继续」），此处不再叠加胶囊，避免与按钮文字重叠
        }

        ui.button(c, C_HOST, 64, 574, 262, 64, "＋ 创建小队", false, true);
        ui.button(c, C_JOIN, 342, 574, 262, 64, "加入小队  ↗", false, true);

        // 次级导航行
        float nx = 64, nw = 129, gap = 8.6f;
        ui.ghost(c, C_CODEX, nx, 658, nw, 52, "图鉴");
        ui.ghost(c, C_META, nx + (nw + gap), 658, nw, 52, "元进度");
        ui.ghost(c, C_SETTINGS, nx + (nw + gap) * 2, 658, nw, 52, "设置");
        ui.ghost(c, C_SOUND, nx + (nw + gap) * 3, 658, nw, 52, sound ? "音效  开" : "音效  关");

        // ───────────────── 右：生态插画 ─────────────────
        hero(c, ui, time);

        // ───────────────── 底部总览条 ─────────────────
        overview(c, ui, time, st, hasSaveNow);
    }

    // ─────────────────────────── 背景 ───────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time) {
        // 深色径向氛围：中心偏右上
        ui.glow(c, 1030, 330, 190, 0x1a9de7c1);
        ui.glow(c, 300, 620, 190, 0x1793c4ff);
        // 缓慢上升的星尘
        for (int i = 0; i < 34; i++) {
            float seed = i * 137.5f;
            float x = (seed % 1370) + 40;
            float speed = .10f + (i % 5) * .035f;
            float y = 800 - ((time * speed * 46 + i * 61) % 830);
            float r = .9f + (i % 3) * .55f;
            int a = (int) (34 + 40 * (.5f + .5f * (float) Math.sin(time * 1.3f + i)));
            ui.circle(c, x, y, r, ui.alpha(0xffffffff, a));
        }
        // 细网格纹理，压低对比度
        for (int i = 0; i <= 12; i++) {
            ui.box(c, 0, 92 + i * 60, 1440, 1, 0, 0x081de7c1);
        }
    }

    // ─────────────────────────── 右侧插画 ───────────────────────────
    private void hero(Canvas c, Ui ui, float time) {
        final float x = 664, y = 108, w = 712, h = 596, rad = 24;

        // 外框 + 投影
        ui.box(c, x - 2, y + 4, w + 4, h + 4, rad + 2, 0x44000000);
        ui.panel(c, x, y, w, h, rad, 0xff101d24, Ui.EDGE);

        // 圆角裁剪内绘插画
        c.save();
        pathClip(ui, c, x + 2, y + 2, w - 4, h - 4, rad - 1);

        int idx = (int) (time / SLIDE) % Content.BIOME_NAMES.length;
        int nxt = (idx + 1) % Content.BIOME_NAMES.length;
        float phase = time % SLIDE;
        float k = phase > SLIDE - FADE ? (phase - (SLIDE - FADE)) / FADE : 0f;
        if (k > 0f) k = k * k * (3 - 2 * k); // smoothstep

        // 缓慢平移（呼吸的运镜）
        float drift = (float) Math.sin(time * .12f) * 10;
        ui.cover(c, x + 2 - drift, y + 2, w - 4 + drift * 2, h - 4, idx, 1f);
        if (k > 0f) ui.cover(c, x + 2 - drift, y + 2, w - 4 + drift * 2, h - 4, nxt, k);

        // 上部压暗，保证浮层文字可读
        ui.gradientRect(c, x + 2, y + 2, w - 4, 190, 0, 0xcc000000, 0x00000000);
        // 下部压暗，托住角色群像
        ui.gradientRect(c, x + 2, y + h - 300, w - 4, 298, 0, 0x00000000, 0xee0b141b);

        // 星尘微粒（插画内）
        for (int i = 0; i < 20; i++) {
            float px = x + 40 + (i * 97 % (int) (w - 80));
            float py = y + 60 + ((i * 71 + time * 12f) % (h - 190));
            ui.circle(c, px, py, 1 + i % 2, ui.alpha(0xffbff3dd, 60 + (i % 4) * 30));
        }

        // 角色群像：主角色居中偏前，其余环绕
        int base = chosenRole;
        float bob = (float) Math.sin(time * 1.5f) * 4f;
        heroActor(c, ui, x + w * .30f, y + h - 96 + bob * .5f, 300, (base + 1) % 12, time + .8f);
        heroActor(c, ui, x + w * .70f, y + h - 88 - bob * .4f, 268, (base + 2) % 12, time + 1.6f);
        heroActor(c, ui, x + w * .855f, y + h - 78 + bob * .3f, 212, (base + 3) % 12, time + 2.4f);
        // 主角
        ui.glow(c, x + w * .5f, y + h - 150, 180, ui.alpha(Content.COLORS[base], 54));
        ui.avatar(c, x + w * .5f, y + h - 108, 430, base, time, false);

        c.restore();

        // 插画浮层：生态名 + 分页指示
        int cur = idx;
        ui.pill(c, x + 22, y + 22, 168, Content.BIOME_NAMES[cur], Ui.GOLD);
        ui.pill(c, x + w - 168, y + 22, 146, "1 – 4 PLAYERS", Ui.MINT);
        ui.small(c, "BIOME 0" + (cur + 1) + " / 0" + Content.BIOME_NAMES.length, x + 24, y + h - 22, 12,
                ui.alpha(Ui.WHITE, 190));
        ui.right(c, Content.BIOME_SUB[cur], x + w - 24, y + h - 22, 20, ui.alpha(Ui.WHITE, 200), true);

        // 生态指示点
        for (int i = 0; i < Content.BIOME_NAMES.length; i++) {
            float dx = x + w - 24 - (Content.BIOME_NAMES.length - 1 - i) * 16;
            boolean on = i == cur;
            ui.circle(c, dx, y + h - 44, on ? 3.6f : 2.4f, on ? Ui.MINT : ui.alpha(Ui.MUTED, 110));
        }
    }

    private void heroActor(Canvas c, Ui ui, float x, float y, float size, int role, float anim) {
        ui.glow(c, x, y - size * .28f, size * .42f, ui.alpha(Content.COLORS[role], 34));
        ui.avatar(c, x, y, size, role, anim, false);
    }

    /** 裁剪用复用的 Path/RectF，避免每帧 new（主菜单 60fps 下这是 GC 热点）。 */
    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();

    private void pathClip(Ui ui, Canvas c, float x, float y, float w, float h, float rad) {
        clipPath.reset();
        clipRect.set(x, y, x + w, y + h);
        clipPath.addRoundRect(clipRect, rad, rad, Path.Direction.CW);
        c.clipPath(clipPath);
    }

    /** 主角展示位（由外部注入当前选中职业，默认 0）。 */
    public void setPreviewRole(int role) {
        chosenRole = Math.max(0, Math.min(11, role));
    }

    private int chosenRole = 0;

    // ─────────────────────────── 底部总览 ───────────────────────────
    private void overview(Canvas c, Ui ui, float time, int[] st, boolean hasSave) {
        final float x = 64, y = 720, w = 1312, h = 58;
        ui.panel(c, x, y, w, h, 16, 0xff111e26, Ui.EDGE);

        // 标签在上、数值在下，两行都完整落在面板内（面板 y=720..778）
        float labelBase = y + 22, valueBase = y + 45;
        float[] colX = {x + 26, x + 246, x + 470, x + 700, x + 930};
        String[] labels = {"星尘", "深渊印记", "最佳层数", "胜场", "总场次"};
        String[] values = {
                format(st[0]), format(st[1]),
                st[2] <= 0 ? "—" : String.valueOf(st[2]),
                String.valueOf(st[3]), String.valueOf(st[4])};
        int[] colors = {Ui.GOLD, Ui.VIOLET, Ui.MINT, Ui.MINT, Ui.WHITE};

        for (int i = 0; i < labels.length; i++) {
            ui.circle(c, colX[i] + 5, labelBase - 4, 3, ui.alpha(colors[i], 210));
            ui.text(c, labels[i], colX[i] + 16, labelBase, 13, Ui.MUTED, false);
            ui.text(c, values[i], colX[i] + 16, valueBase, 19, colors[i], true);
            if (i < labels.length - 1) ui.box(c, colX[i] + 196, y + 14, 1, h - 28, 0, ui.alpha(Ui.EDGE, 200));
        }

        // 右侧：存档状态
        if (hasSave) {
            ui.pill(c, x + w - 176, y + (h - 28) / 2f, 150, "存档待继续", Ui.GOLD);
        } else {
            ui.pill(c, x + w - 176, y + (h - 28) / 2f, 150, "无进行中存档", Ui.MUTED);
        }

        // 页脚小字放在面板下方，不再与面板重叠（面板底 778，屏高 810）
        ui.text(c, "局域网 1–4 人   /   手机直接建房   /   无内购", 64, 798, 12,
                ui.alpha(Ui.MUTED, 170), false);
        ui.right(c, "© 深渊小队  ·  全部美术与文本为原创", 1376, 798, 11, ui.alpha(Ui.MUTED, 150), false);
    }

    // ─────────────────────────── 点击 ───────────────────────────
    /**
     * @return 导航动作码；-1 表示仅内部状态变化，GameView 无需动作。
     */
    public int click(int id) {
        hit = id;
        switch (id) {
            case C_START:
                return Ui.A_START;
            case C_CONTINUE:
                return Ui.A_CONTINUE;
            case C_HOST:
                return Ui.A_HOST;
            case C_JOIN:
                return Ui.A_JOIN;
            case C_CODEX:
                return Ui.A_CODEX;
            case C_META:
                return Ui.A_META;
            case C_SETTINGS:
                return Ui.A_SETTINGS;
            case C_SOUND:
                return Ui.A_SOUND;
            case C_GUIDE:
                return Ui.A_GUIDE;
            default:
                return -1;
        }
    }

    // ─────────────────────────── 工具 ───────────────────────────
    private static int[] safe(int[] v, int n) {
        if (v != null && v.length >= n) return v;
        int[] o = new int[n];
        if (v != null) System.arraycopy(v, 0, o, 0, Math.min(v.length, n));
        return o;
    }

    private static String format(int v) {
        if (v < 1000) return String.valueOf(v);
        if (v < 1000000) return (v / 1000) + "," + String.format(java.util.Locale.US, "%03d", v % 1000);
        return (v / 1000000) + "," + String.format(java.util.Locale.US, "%03d,%03d", (v / 1000) % 1000, v % 1000);
    }
}
