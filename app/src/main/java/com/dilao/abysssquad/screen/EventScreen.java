package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/** 事件房：情境 + 两个代价明确的选项 + 12 组插画构图。 */
public final class EventScreen {
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private Shader backdrop;
    private Shader shade;
    private Shader artGradient;
    private int artGradientKey;

    public EventScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        if (c == null || ui == null || g == null) return;
        ui.clearHits();
        hit = -1;

        int event = Math.max(0, Math.min(Events.COUNT - 1, g.eventType));
        drawBackdrop(c, ui, time, event, g);
        drawIllustration(c, ui, time, event);

        float leftX = 88, leftW = 700;
        drawStory(c, ui, time, g, event, leftX, leftW);
        if (g.eventChoice >= 0) {
            drawResult(c, ui, g, event, leftX, leftW);
        } else {
            drawChoices(c, ui, g, event, leftX, leftW, canLead);
        }
        drawSideInfo(c, ui, g, event, canLead);
        hit = ui.hitAt(px, py);
    }

    /** A → {@code 4000}，B → {@code 4001}，继续 → {@code 4002}。 */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id == 4000 || id == 4001 || id == 4002) return id;
        return -1;
    }

    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ── 背景 ────────────────────────────────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time, int event, Game g) {
        if (backdrop == null) {
            backdrop = new LinearGradient(0, 0, 0, 810, 0xff131d26, 0xff080e13, Shader.TileMode.CLAMP);
        }
        paint.setShader(backdrop);
        rect.set(0, 0, 1440, 810);
        c.drawRect(rect, paint);
        paint.setShader(null);
        int accent = artAccent(event);
        ui.glow(c, 1080, 300, 460, ui.alpha(accent, 26));
        for (int i = 0; i < 20; i++) {
            float x = 60 + (i * 197) % 1330;
            float y = 120 + (i * 233) % 660;
            float a = .10f + .08f * (float) Math.sin(time * .9f + i * 1.7f);
            ui.circle(c, x, y, 1.4f + (i % 3), ui.alpha(accent, (int) (a * 255)));
        }
    }

    // ── 左侧：标题与情境 ────────────────────────────────────────
    private void drawStory(Canvas c, Ui ui, float time, Game g, int event, float x, float w) {
        ui.text(c, "事 件 房  ·  " + (Math.max(0, g.floor)) + " 章", x, 74, 12,
                ui.alpha(Ui.MUTED, 215), false);
        ui.box(c, x, 86, 56, 3, 1.5f, ui.alpha(artAccent(event), 220));

        ui.title(c, Events.NAMES[event], x, 142, 40);
        ui.text(c, "EVENT  ·  " + typeName(Events.TYPE[event]), x, 170, 11.5f,
                ui.alpha(artAccent(event), 175), false);

        ui.panel(c, x, 196, w, 154, 16, 0x99101c23, ui.alpha(Ui.EDGE, 160));
        ui.wrap(c, Events.TEXT[event], x + 26, 232, w - 52, 15, ui.alpha(Ui.WHITE, 228));
    }

    // ── 选项 ────────────────────────────────────────────────────
    private void drawChoices(Canvas c, Ui ui, Game g, int event, float x, float w, boolean canLead) {
        int costA = Events.COST_A[event];
        int costB = Events.COST_B[event];
        boolean okA = g.canEvent(0);
        boolean okB = g.canEvent(1);

        drawChoice(c, ui, 4000, x, 372, w, 92, "A", Events.CHOICE_A[event],
                costA, okA ? Events.preview(g, event, 0) : blockedHint(g, event, 0),
                okA, canLead, Ui.MINT);
        drawChoice(c, ui, 4001, x, 480, w, 92, "B", Events.CHOICE_B[event],
                costB, okB ? Events.preview(g, event, 1) : blockedHint(g, event, 1),
                okB, canLead, Ui.BLUE);

        ui.text(c, "部分代价会在 3 站后结算，选择不可撤销。", x, 600, 12,
                ui.alpha(Ui.MUTED, 185), false);
    }

    private void drawChoice(Canvas c, Ui ui, int id, float x, float y, float w, float h,
                            String key, String text, int cost, String costText, boolean affordable,
                            boolean canLead, int accent) {
        boolean enabled = affordable && canLead;
        boolean hover = enabled && (inside(x, y, w, h) || pressed(id));
        ui.card(c, x, y, w, h, accent, false, hover);
        if (enabled) ui.hit(id, x, y, w, h);
        if (hover) ui.box(c, x + 1, y + 1, 5, h - 2, 2.5f, ui.alpha(accent, 200));

        // 字母徽章
        ui.circle(c, x + 42, y + h * .5f, 26, enabled ? ui.alpha(accent, 190) : ui.alpha(Ui.EDGE, 150));
        ui.center(c, key, x + 42, y + h * .5f, 20, enabled ? 0xff0b141b : ui.alpha(Ui.MUTED, 200), true);

        float tx = x + 86;
        ui.text(c, text, tx, y + 40, ui.fit(text, w - 300, 17, 12),
                enabled ? Ui.WHITE : ui.alpha(Ui.MUTED, 200), true);

        int costColor = affordable ? (cost > 0 ? Ui.GOLD : ui.alpha(Ui.MUTED, 225)) : Ui.RED;
        if (cost > 0) {
            ui.icon(c, tx + 8, y + 66, 13, 8, costColor);
            ui.text(c, costText, tx + 20, y + 71, ui.fit(costText, w - 172, 12.5f, 9.5f),
                    costColor, true);
        } else {
            ui.text(c, costText, tx, y + 71, ui.fit(costText, w - 152, 12.5f, 9.5f),
                    costColor, true);
        }
        if (!canLead) {
            ui.right(c, "等待队长决定", x + w - 96, y + 71, 11.5f, Ui.BLUE, false);
        }

        // 右侧箭头
        float ax = x + w - 40, ay = y + h * .5f;
        int arrowColor = enabled ? ui.alpha(accent, 210) : ui.alpha(Ui.MUTED, 90);
        ui.line(c, ax - 10, ay, ax + 6, ay, arrowColor, 2.4f);
        ui.line(c, ax + 6, ay, ax - 1, ay - 7, arrowColor, 2.4f);
        ui.line(c, ax + 6, ay, ax - 1, ay + 7, arrowColor, 2.4f);
    }

    /** 启用状态直接使用核心规则 Game.canEvent，避免 UI 与引擎的生命门槛漂移。 */
    private static String blockedHint(Game g, int event, int choice) {
        int cost = choice == 0 ? Events.COST_A[event] : Events.COST_B[event];
        if (g.coins < cost) return "星尘不足 · 需要 " + cost;
        if (choice == 0 && (Events.TYPE[event] == 0 || Events.TYPE[event] == 3
                || Events.TYPE[event] == 7 || Events.TYPE[event] == 12)) {
            for (Game.Player p : g.players)
                if (p.hp <= p.maxHp * .32f) return "生命过低 · 队员需高于 32% 生命";
        }
        return "当前不可选择";
    }

    // ── 结果态 ──────────────────────────────────────────────────
    private void drawResult(Canvas c, Ui ui, Game g, int event, float x, float w) {
        int choice = g.eventChoice;
        String result = choice == 0 ? Events.RESULT_A[event] : Events.RESULT_B[event];
        boolean good = choice == 0;

        ui.panel(c, x, 372, w, 196, 16, good ? 0x99142522 : 0x991a2028,
                ui.alpha(good ? Ui.MINT : Ui.BLUE, 150));
        ui.pill(c, x + 26, 390, 92, good ? "已接受" : "已拒绝", good ? Ui.MINT : Ui.BLUE);
        ui.wrap(c, result, x + 26, 452, w - 52, 15, ui.alpha(Ui.WHITE, 232));

        if (g.notice != null && g.notice.length() > 0) {
            ui.wrap(c, g.notice, x + 26, 508, w - 52, 12.5f, ui.alpha(Ui.GOLD, 220));
        }

        ui.button(c, 4002, x, 584, 320, 88, "继续前行  →", true, true);
        ui.text(c, "结算完成，本层事件已结束", x + 342, 638, 12,
                ui.alpha(Ui.MUTED, 195), false);
    }

    // ── 右侧插画 ────────────────────────────────────────────────
    private void drawIllustration(Canvas c, Ui ui, float time, int event) {
        float x = 812, y = 190, w = 556, h = 462;
        int accent = artAccent(event);
        ui.panel(c, x, y, w, h, 20, 0xff0d161d, ui.alpha(Ui.EDGE, 200));

        c.save();
        ui.box(c, x, y, w, h, 20, 0x00000000);
        c.clipRect(x + 1, y + 1, x + w - 1, y + h - 1);
        gradient(c, x, y, w, h, ui.alpha(accent, 40), 0xff070c10);
        drawArt(c, ui, time, Events.ART[event], x, y, w, h, accent);
        c.restore();

        ui.stroke(c, x, y, w, h, 20, ui.alpha(Ui.EDGE, 210), 1.4f);
        ui.pill(c, x + 22, y + h - 52, 116, "EVENT ART", ui.alpha(accent, 235));
        ui.right(c, Events.NAMES[event], x + w - 22, y + h - 26, 13,
                ui.alpha(Ui.WHITE, 200), false);
    }

    private void gradient(Canvas c, float x, float y, float w, float h, int top, int bottom) {
        if (artGradient == null || artGradientKey != top) {
            artGradientKey = top;
            artGradient = new LinearGradient(0, y, 0, y + h, top, bottom, Shader.TileMode.CLAMP);
        }
        paint.setShader(artGradient);
        rect.set(x, y, x + w, y + h);
        c.drawRect(rect, paint);
        paint.setShader(null);
    }

    /** 12 种事件构图（下标与 Events.ART 一致）。 */
    private void drawArt(Canvas c, Ui ui, float time, int art, float x, float y, float w, float h, int accent) {
        float cx = x + w * .5f, cy = y + h * .5f;
        paint.setStyle(Paint.Style.FILL);
        int soft = ui.alpha(accent, 110);
        int mid = ui.alpha(accent, 60);
        switch (Math.floorMod(art, 12)) {
            case 0: { // 深井 / 竖向回廊
                for (int i = 0; i < 7; i++) {
                    float rr = 40 + i * 26;
                    ui.ring(c, cx, cy + 60 - i * 12, rr, ui.alpha(accent, 120 - i * 13), 3f);
                }
                ui.glow(c, cx, cy + 120, 90, 0x5520e0c0);
                break;
            }
            case 1: { // 熔炉 / 坩埚
                ui.circle(c, cx, cy + 40, 120, ui.alpha(accent, 44));
                ui.circle(c, cx, cy + 40, 78, ui.alpha(accent, 90));
                for (int i = 0; i < 9; i++) {
                    float bx = cx - 80 + (i * 47) % 170;
                    float by = cy - 40 - ((i * 53 + (int) (time * 40)) % 160);
                    ui.circle(c, bx, by, 2.5f + i % 3, ui.alpha(Ui.GOLD, 160 - i * 12));
                }
                break;
            }
            case 2: { // 星图 / 观星
                for (int i = 0; i < 12; i++) {
                    float sx = cx - 180 + (i * 79) % 360;
                    float sy = cy - 140 + (i * 113) % 280;
                    ui.circle(c, sx, sy, 2f + i % 2, ui.alpha(Ui.WHITE, 190));
                    if (i % 3 == 0) ui.line(c, sx, sy, cx, cy + 90, ui.alpha(accent, 60), 1f);
                }
                ui.ring(c, cx, cy + 110, 92, ui.alpha(accent, 120), 2f);
                break;
            }
            case 3: { // 双面祭坛
                ui.diamond(c, cx - 70, cy, 130, ui.alpha(Ui.GOLD, 70));
                ui.diamond(c, cx + 70, cy, 130, ui.alpha(Ui.VIOLET, 70));
                ui.line(c, cx, y + 40, cx, y + h - 40, ui.alpha(Ui.WHITE, 60), 2f);
                break;
            }
            case 4: { // 候车室 / 长椅
                for (int i = 0; i < 4; i++) {
                    ui.box(c, cx - 190 + i * 20, cy + 20 + i * 26, 380 - i * 40, 10, 5,
                            ui.alpha(accent, 70 - i * 12));
                }
                ui.box(c, cx - 220, cy - 120, 60, 240, 8, 0x33edf1e7);
                ui.box(c, cx + 160, cy - 120, 60, 240, 8, 0x33edf1e7);
                break;
            }
            case 5: { // 锻炉 / 铁锤
                ui.box(c, cx - 150, cy + 40, 300, 26, 8, ui.alpha(accent, 70));
                ui.box(c, cx - 110, cy - 30, 220, 60, 10, ui.alpha(Ui.MUTED, 60));
                for (int i = 0; i < 5; i++) ui.circle(c, cx - 60 + i * 30, cy - 60, 4, ui.alpha(Ui.GOLD, 120));
                break;
            }
            case 6: { // 赌桌 / 骰
                ui.box(c, cx - 170, cy - 20, 340, 150, 16, ui.alpha(accent, 40));
                ui.icon(c, cx - 90, cy + 40, 64, 0, ui.alpha(Ui.GOLD, 200));
                ui.icon(c, cx + 20, cy + 50, 48, 1, ui.alpha(Ui.WHITE, 150));
                ui.icon(c, cx + 100, cy + 30, 40, 5, ui.alpha(Ui.VIOLET, 150));
                break;
            }
            case 7: { // 根须
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                for (int i = 0; i < 8; i++) {
                    paint.setColor(ui.alpha(accent, 130 - i * 10));
                    path.reset();
                    path.moveTo(x + 40, y + h - 40);
                    float tx = x + 40 + i * (w - 80) / 7f;
                    path.cubicTo(tx - 60, y + h * .72f, tx + 70, y + h * .42f, tx, y + 30 + i * 12);
                    c.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case 8: { // 冰封旅人
                for (int i = 0; i < 5; i++) {
                    float rr = 200 - i * 34;
                    ui.ring(c, cx, cy, rr, ui.alpha(Ui.BLUE, 100 - i * 14), 3f);
                }
                ui.avatar(c, cx, cy + 30, 120, 4, time, false);
                ui.circle(c, cx, cy + 30, 130, 0x2293c4ff);
                break;
            }
            case 9: { // 星图半张
                ui.box(c, cx - 160, cy - 120, 320, 240, 6, 0x33edf1e7);
                ui.line(c, cx - 160, cy - 120, cx + 160, cy + 120, ui.alpha(accent, 90), 2f);
                for (int i = 0; i < 8; i++) {
                    ui.circle(c, cx - 130 + (i * 61) % 260, cy - 90 + (i * 97) % 180, 3f,
                            ui.alpha(Ui.GOLD, 180));
                }
                break;
            }
            case 10: { // 琴 / 音波
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                for (int i = 0; i < 6; i++) {
                    paint.setColor(ui.alpha(accent, 120 - i * 16));
                    c.drawCircle(cx, cy + 60, 30 + i * 34, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                ui.line(c, cx - 120, cy + 40, cx + 120, cy + 40, ui.alpha(Ui.WHITE, 140), 4f);
                break;
            }
            default: { // 祭品 / 光柱
                for (int i = 0; i < 10; i++) {
                    float a = (float) (i * Math.PI * 2 / 10 + time * .12f);
                    float ex = cx + (float) Math.cos(a) * 150;
                    float ey = cy + (float) Math.sin(a) * 100;
                    ui.line(c, cx, cy, ex, ey, ui.alpha(accent, 40), 30f);
                }
                ui.circle(c, cx, cy, 46, ui.alpha(Ui.WHITE, 90));
                ui.circle(c, cx, cy, 26, ui.alpha(accent, 170));
                break;
            }
        }
        // 统一：底部渐隐 + 副光
        if (shade == null) {
            shade = new LinearGradient(0, y + h * .55f, 0, y + h,
                    0x00000000, 0xDD070c10, Shader.TileMode.CLAMP);
        }
        paint.setShader(shade);
        rect.set(x, y + h * .55f, x + w, y + h);
        c.drawRect(rect, paint);
        paint.setShader(null);
        if (soft != 0) ui.glow(c, cx, y + 60, 180, mid);
    }

    // ── 右侧下方信息 ────────────────────────────────────────────
    private void drawSideInfo(Canvas c, Ui ui, Game g, int event, boolean canLead) {
        float x = 812, y = 668, w = 556;
        ui.panel(c, x, y, w, 92, 14, 0x99101c23, ui.alpha(Ui.EDGE, 160));
        int costA = Events.COST_A[event];
        int costB = Events.COST_B[event];
        boolean okA = g.canEvent(0);
        boolean okB = g.canEvent(1);
        ui.text(c, "A  预付星尘 " + costA + (okA ? "  ·  可选" : "  ·  不可选"),
                x + 22, y + 36, 13, costA > 0 ? Ui.GOLD : Ui.MUTED, false);
        ui.text(c, "B  预付星尘 " + costB + (okB ? "  ·  可选" : "  ·  不可选"),
                x + 22, y + 62, 13, costB > 0 ? Ui.GOLD : Ui.MUTED, false);
        ui.right(c, "星尘余额 " + Math.max(0, g.coins), x + w - 22, y + 36, 13, Ui.WHITE, true);
        ui.right(c, canLead ? "生命与延迟代价见左侧" : "等待队长", x + w - 22, y + 62, 11.5f,
                canLead ? ui.alpha(Ui.MUTED, 200) : Ui.BLUE, false);
    }

    private static int artAccent(int event) {
        switch (Events.TYPE[Math.max(0, Math.min(Events.COUNT - 1, event))]) {
            case 0: return Ui.RED;
            case 1: return Ui.GOLD;
            case 2: return Ui.BLUE;
            case 3: return Ui.VIOLET;
            case 4: return Ui.MINT;
            case 5: return 0xffefd97a;
            case 6: return Ui.PINK;
            // v2.2：TYPE 7..12 原先全部落到 default（显示成紫色"诅咒"），
            // 于是 15 个新事件在界面上看起来和诅咒房一模一样。
            case 7: return Ui.VIOLET;      // 诅咒
            case 8: return 0xffff9a6b;     // 风险累积：保险丝橙
            case 9: return 0xff8fd4e8;     // 延迟结算：延迟的青
            case 10: return Ui.GOLD;       // 二选一赌注
            case 11: return 0xffb7e0a8;    // 交换：苔绿
            default: return Ui.RED;        // 12 反向代价：警示红
        }
    }

    private static String typeName(int type) {
        switch (type) {
            case 0: return "BLOOD PACT";
            case 1: return "TRIBUTE";
            case 2: return "ORACLE";
            case 3: return "ALTAR";
            case 4: return "RELIEF";
            case 5: return "FORGE";
            case 6: return "GAMBLE";
            case 7: return "CURSE";
            case 8: return "DEBT";
            case 9: return "DELAYED";
            case 10: return "WAGER";
            case 11: return "TRADE";
            default: return "TOLL";
        }
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}
