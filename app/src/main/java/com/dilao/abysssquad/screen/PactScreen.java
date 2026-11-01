package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 契约词条（Pact / Heat）选择界面，1440×810。
 *
 * <p>12 条词条按 <b>3 列 × 4 行</b> 排布，玩家可任意开关组合。总 heat 决定本局结算倍率
 * （{@link Balance#heatRewardMult(int)}，满配 {@link Balance#PACT_HEAT_MAX} = ×3.64）。
 * 未解锁词条压暗且不可点。
 *
 * <p><b>动作码</b>
 * <ul>
 *   <li>{@code 12000 + index} — 切换第 index 条词条（调用方执行 {@code game.togglePact(index)}）</li>
 *   <li>{@link Ui#A_BACK} — 返回上一页</li>
 *   <li>{@link Ui#A_START} — 确认出发 / 开始远征</li>
 * </ul>
 *
 * <p><b>反射安全</b>：{@code Balance.PACT_*} 已定稿，这里硬编码引用；同时所有数组访问都做
 * 长度钳制，任何一张表缺失或长度不符时降级为空列表（画「数据未就绪」占位），不抛异常。
 */
public final class PactScreen {

    /** 切换词条的动作码基址：{@code 12000 + index}。 */
    public static final int A_TOGGLE_BASE = 12000;

    public int hit = -1;

    private float px = -1, py = -1;
    private int pressedId = -1;
    private long pressedAt;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    // ── 3 × 4 网格（1440×810）──────────────────────────────────
    private static final int COLS = 3;
    private static final float GRID_X = 60f;
    private static final float GRID_Y = 214f;
    private static final float CARD_W = 424f;
    private static final float CARD_H = 78f;
    private static final float GAP_X = 24f;
    private static final float GAP_Y = 24f;

    public PactScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    /**
     * 返回动作码：{@code 12000 + index}、{@link Ui#A_BACK}、{@link Ui#A_START}，其余 -1。
     */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id >= A_TOGGLE_BASE && id < A_TOGGLE_BASE + count()) return id;
        if (id == Ui.A_BACK) return Ui.A_BACK;
        if (id == Ui.A_START) return Ui.A_START;
        return -1;
    }

    // ══════════════════════════════════════════════════════════════
    /**
     * @param g 大厅态 Game（{@code phase == LOBBY}）。可为 null —— 此时按「零词条」渲染，
     *          仍可显示与点击，只是切换不会生效。
     */
    /**
     * 当前通关进度（已通关的最高契约档），由 GameView 每帧从 Game.pactUnlocked 灌入。
     * <p>为什么需要它：{@code Balance.PACT_UNLOCKED} 是 static final，本类原先只读它，
     * 于是 12 条词条里有 8 条**永久锁死**——玩家通关到炼狱也解锁不出第 5 条。
     * 现在改读 {@link Balance#pactAvailable(int, int)}，按通关进度开放。
     */
    private int progress = -1;

    public void draw(Canvas c, Ui ui, float time, Game g) {
        if (c == null || ui == null) return;
        progress = g == null ? -1 : g.pactUnlocked;
        ui.clearHits();
        hit = -1;

        drawBackdrop(c, ui, time);

        int n = count();
        if (n <= 0) {
            drawEmpty(c, ui);
            hit = ui.hitAt(px, py);
            return;
        }

        int mods = g == null ? 0 : g.pactMods;
        int heat = heatOf(mods);
        int enabled = enabledCount(mods);

        drawHeader(c, ui, time, heat, enabled, n);
        if (enabled >= 2) drawWarning(c, ui, time, heat);
        drawGrid(c, ui, mods, n);
        drawFooter(c, ui, heat, enabled, n);

        hit = ui.hitAt(px, py);
    }

    // ── 背景 ────────────────────────────────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time) {
        ui.box(c, 0, 0, 1440, 810, 0, 0x77070d11);
        for (int i = 0; i < 20; i++) {
            float x = 70 + (i * 197) % 1300;
            float y = 110 + (i * 233) % 640;
            float a = .09f + .06f * (float) Math.sin(time * .9f + i * 1.3f);
            ui.circle(c, x, y, 1.3f + (i % 3) * .6f, ui.alpha(Ui.RED, (int) (a * 255)));
        }
    }

    private void drawEmpty(Canvas c, Ui ui) {
        ui.panel(c, 330, 296, 780, 208, 18, 0xCC101c23, ui.alpha(Ui.EDGE, 200));
        ui.center(c, "词条数据尚未就绪", 720, 372, 26, Ui.WHITE, true);
        ui.centerSmall(c, "Balance.PACT_* 表为空或长度不足，界面已安全降级", 720, 408, 13,
                ui.alpha(Ui.MUTED, 220));
        ui.button(c, Ui.A_BACK, 570, 444, 300, 88, "返回", false, true);
    }

    // ── 顶部：总 heat + 结算倍率 ────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, float time, int heat, int enabled, int n) {
        ui.box(c, 0, 0, 1440, 160, 0, 0xCC0b141b);
        ui.divider(c, 60, 152, 1320);

        ui.text(c, "远征 · 契约词条", 60, 40, 12.5f, ui.alpha(Ui.MUTED, 225), false);
        ui.box(c, 60, 52, 56, 3, 1.5f, ui.alpha(Ui.RED, 220));
        ui.text(c, "契 约 词 条", 60, 100, 36, Ui.WHITE, true);
        ui.text(c, "PACT  ·  HEAT CONTRACT", 60, 126, 10.5f, ui.alpha(Ui.RED, 175), false);

        // 居中大字：「总 heat 12 / 22 · 结算 ×2.44」
        float heatSize = 34, multSize = 34;
        String hStr = "总 heat " + heat + " / " + Balance.PACT_HEAT_MAX;
        String mStr = "·   结算 ×" + multText(heat);
        float hw = ui.measure(hStr, heatSize, true);
        float mw = ui.measure(mStr, multSize, true);
        float totalW = hw + mw + 24;
        float startX = 720 - totalW * .5f;

        ui.text(c, hStr, startX, 92, heatSize, heatColor(heat), true);
        ui.text(c, mStr, startX + hw + 24, 92, multSize, Ui.GOLD, true);

        ui.centerSmall(c, "已开启 " + enabled + " / " + n + " 条词条   ·   倍率作用于星尘与深渊印记结算",
                720, 124, 11.5f, ui.alpha(Ui.MUTED, 205));

        // 右上角：难度条（heat 0..PACT_HEAT_MAX）
        float bx = 1148, by = 46, bw = 232;
        ui.text(c, "难度压力", 1148, 40, 10.5f, ui.alpha(Ui.MUTED, 190), false);
        float ratio = Balance.PACT_HEAT_MAX <= 0 ? 0f
                : Math.max(0f, Math.min(1f, heat / (float) Balance.PACT_HEAT_MAX));
        ui.bar(c, bx, by + 12, bw, 12, ratio, heatColor(heat));
        ui.right(c, heat + " / " + Balance.PACT_HEAT_MAX, 1380, 40, 10.5f,
                ui.alpha(Ui.MUTED, 200), false);
    }

    // ── ≥2 条开启：警告条 ──────────────────────────────────────
    private void drawWarning(Canvas c, Ui ui, float time, int heat) {
        float pulse = .5f + .5f * (float) Math.sin(time * 2.6f);
        int a = (int) (185 + pulse * 60);
        ui.gradientRect(c, 60, 166, 1320, 36, 8,
                ui.alpha(Ui.RED, (int) (a * .24f)), ui.alpha(Ui.RED, (int) (a * .10f)));
        ui.stroke(c, 60, 166, 1320, 36, 8, ui.alpha(Ui.RED, a), 1.2f);
        ui.icon(c, 84, 184, 16, 0, ui.alpha(Ui.RED, 240));
        ui.text(c, "警告 · 已叠加 " + (heat >= 8 ? "高" : "多") + "条词条，难度显著上升（总 heat "
                        + heat + "，结算 ×" + multText(heat) + "）。请确认构筑强度后再出发。",
                102, 189, 12.5f, ui.alpha(Ui.RED, 245), true);
    }

    // ── 3 × 4 网格 ──────────────────────────────────────────────
    private void drawGrid(Canvas c, Ui ui, int mods, int n) {
        for (int i = 0; i < COLS * 4; i++) {
            int col = i % COLS, row = i / COLS;
            float x = GRID_X + col * (CARD_W + GAP_X);
            float y = GRID_Y + row * (CARD_H + GAP_Y);
            if (i >= n) {
                ui.box(c, x, y, CARD_W, CARD_H, 14, 0x2A0d161d);
                ui.centerSmall(c, "—", x + CARD_W * .5f, y + CARD_H * .5f, 15,
                        ui.alpha(Ui.MUTED, 80));
                continue;
            }
            drawCard(c, ui, i, x, y, mods);
        }
    }

    private void drawCard(Canvas c, Ui ui, int i, float x, float y, int mods) {
        String name = name(i);
        String desc = desc(i);
        int heat = heat(i);
        int hc = heatColor(heat);
        boolean on = (mods & (1 << i)) != 0;
        boolean unlocked = unlocked(i);

        if (!unlocked) {
            // 未解锁：压暗、不可点、显示解锁条件
            // 纵向节奏：标题 y+22 / 描述 y+38（单行到 y+56）/ 解锁提示 y+71
            ui.card(c, x, y, CARD_W, CARD_H, Ui.EDGE, false, false);
            ui.box(c, x + 1, y + 1, CARD_W - 2, CARD_H - 2, 13, 0x55070d11);
            ui.text(c, name, x + 16, y + 22, ui.fit(name, CARD_W - 130, 15, 11),
                    ui.alpha(Ui.WHITE, 145), true);
            ui.wrap(c, desc, x + 16, y + 38, CARD_W - 110, 11, ui.alpha(Ui.MUTED, 115));
            ui.text(c, "未解锁 · 通关对应契约后开放", x + 16, y + 72, 10,
                    ui.alpha(Ui.MUTED, 130), false);
            // 右上 heat（暗）
            ui.badge(c, x + CARD_W - 26, y + 18, heat, ui.alpha(hc, 110));
            return;
        }

        boolean hover = pressed(A_TOGGLE_BASE + i) || inside(x, y, CARD_W, CARD_H);
        ui.toggleCard(c, A_TOGGLE_BASE + i, x, y, CARD_W, CARD_H, name, desc, on, hc);
        // toggleCard 内部已 ui.hit(...)，这里不重复登记

        if (hover) {
            ui.stroke(c, x + 2, y + 2, CARD_W - 4, CARD_H - 4, 12, ui.alpha(hc, 85), 1.2f);
        }

        // 右上 heat 角标
        ui.badge(c, x + CARD_W - 22, y + 18, heat, hc);

        // heat 分段条：画在卡片下方 4px 的空隙里（GAP_Y=24 足够），完全不与卡片正文冲突
        ui.meter(c, x + 14, y + CARD_H + 4, 110, heat, 3, hc);

        // 底部右侧状态字（卡片内右下，与描述末端错开）
        ui.right(c, on ? "已启用" : "未启用", x + CARD_W - 14, y + CARD_H - 10, 10.5f,
                on ? hc : ui.alpha(Ui.MUTED, 165), on);
    }

    // ── 底部 ────────────────────────────────────────────────────
    private void drawFooter(Canvas c, Ui ui, int heat, int enabled, int n) {
        float y = 700, bh = 88;

        ui.button(c, Ui.A_BACK, 60, y, 264, bh, "返回", false, true);

        String mid = "已开启 " + enabled + " / " + n + " 条   ·   总 heat " + heat
                + "   ·   结算倍率 ×" + multText(heat);
        ui.center(c, mid, 720, y + 40, 14, ui.alpha(Ui.WHITE, 225), false);
        String tip = enabled == 0 ? "零词条：标准难度，结算倍率 ×1.00"
                : enabled == 1 ? "单条词条：轻度加压，收益小幅提升"
                : "多条叠加：难度已明显上升，请确认构筑强度";
        ui.centerSmall(c, tip, 720, y + 64, 11.5f,
                enabled >= 2 ? ui.alpha(Ui.RED, 225) : ui.alpha(Ui.MUTED, 190));

        ui.button(c, Ui.A_START, 1116, y, 264, bh, "确认出发  →", true, true);
    }

    // ══ 数据访问（全部长度钳制；缺表降级）═══════════════════════
    private static int count() {
        int n = Balance.PACT_COUNT;
        n = Math.min(n, len(Balance.PACT_NAME));
        n = Math.min(n, len(Balance.PACT_DESC));
        n = Math.min(n, Balance.PACT_HEAT == null ? 0 : Balance.PACT_HEAT.length);
        return Math.max(0, n);
    }

    private static int len(Object[] a) { return a == null ? 0 : a.length; }

    private static String name(int i) {
        try {
            String s = Balance.PACT_NAME[i];
            return s == null || s.isEmpty() ? ("词条 " + (i + 1)) : s;
        } catch (Throwable t) {
            return "词条 " + (i + 1);
        }
    }

    private static String desc(int i) {
        try {
            String s = Balance.PACT_DESC[i];
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int heat(int i) {
        try {
            return Math.max(1, Math.min(3, Balance.PACT_HEAT[i]));
        } catch (Throwable t) {
            return 1;
        }
    }

    private boolean unlocked(int i) {
        try {
            return Balance.pactAvailable(i, progress);
        } catch (Throwable t) {
            return true;
        }
    }

    private static int heatOf(int mods) {
        try {
            return Balance.heatOf(mods);
        } catch (Throwable t) {
            int sum = 0, n = count();
            for (int i = 0; i < n; i++) if ((mods & (1 << i)) != 0) sum += heat(i);
            return sum;
        }
    }

    private static int enabledCount(int mods) {
        int c = 0, n = count();
        for (int i = 0; i < n; i++) if ((mods & (1 << i)) != 0) c++;
        return c;
    }

    private static float mult(int heat) {
        try {
            return Balance.heatRewardMult(heat);
        } catch (Throwable t) {
            return 1f + Math.max(0, heat) * .12f;
        }
    }

    /** 1.00 / 2.44 / 3.64 这类两位小数文本。 */
    private static String multText(int heat) {
        int v = Math.round(mult(heat) * 100f);
        return (v / 100) + "." + ((v / 10) % 10) + (v % 10);
    }

    /** heat 配色：1=MINT / 2=GOLD / 3=RED。 */
    private static int heatColor(int heat) {
        if (heat >= 3) return Ui.RED;
        if (heat == 2) return Ui.GOLD;
        return Ui.MINT;
    }

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}
