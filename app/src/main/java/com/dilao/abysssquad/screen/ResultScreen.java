package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/** 结算（胜/败）与暂停面板。 */
public final class ResultScreen {
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private Shader backdrop;
    private int backdropWon = -1;

    public ResultScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, boolean won, boolean paused, int[] stats) {
        if (c == null || ui == null) return;
        ui.clearHits();
        hit = -1;

        if (paused) {
            drawPause(c, ui, time, g, stats);
        } else {
            drawResult(c, ui, time, g, won, stats);
        }
        hit = ui.hitAt(px, py);
    }

    /** 再来一局 {@link Ui#A_RESTART}，返回营地 {@link Ui#A_HOME}，继续 {@link Ui#A_RESUME}。 */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id == Ui.A_RESTART) return Ui.A_RESTART;
        if (id == Ui.A_HOME) return Ui.A_HOME;
        if (id == Ui.A_RESUME) return Ui.A_RESUME;
        if (id == Ui.A_LEAVE) return Ui.A_LEAVE;
        if (id == Ui.A_SETTINGS) return Ui.A_SETTINGS;
        return -1;
    }

    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ══════════════════════════════════════════════════════════════
    //  结算
    // ══════════════════════════════════════════════════════════════
    private void drawResult(Canvas c, Ui ui, float time, Game g, boolean won, int[] stats) {
        drawBackdrop(c, ui, time, won);
        float reveal = Math.min(1f, time * .9f);
        boolean tutorial = stats != null && stats.length > 5 && stats[5] != 0;

        int accent = won ? Ui.GOLD : Ui.BLUE;

        // 标题分支按「是否打满本局总站数」走，而不是只看 won。
        //
        // 引导局在 4:45 由导演脚本直接置 WON（Onboarding.closeRun），
        // 玩家看到的却是「远征完成 · 抵达 2 / 15」—— 前后自相矛盾，
        // 会被理解成「完整远征只值两站」或「游戏在骗我」。
        // 教学脚本只是一次安全撤离，不计正式胜场；标题和下一步按钮也需说清楚。
        boolean full = won && !tutorial && fullClear(g);
        ui.centerSmall(c, !won ? "SQ U A D   L O S T"
                        : full ? "EXPEDITION COMPLETE" : "FIRST DIVE CLEARED",
                720, 92, 12, ui.alpha(accent, 190));
        ui.glow(c, 720, 150, 240 + (float) Math.sin(time * 1.6f) * 12f, ui.alpha(accent, 30));
        ui.center(c, !won ? "小 队 失 守" : full ? "远 征 完 成" : "第 一 趟 完 成",
                720, 158, 56, won ? Ui.GOLD : ui.alpha(Ui.BLUE, 235), true);
        ui.box(c, 560, 184, 320, 3, 1.5f, ui.alpha(accent, 160));

        String sub = !won
                ? "星环熄灭在身后，但带出的星尘与印记不会消失。"
                : tutorial ? "实战教学完成！奖励归你，正式通关从下一趟算起。"
                : full ? "王座熄灭了。你们从深渊里带回了可以带走的一切。"
                : "深渊记住了你。这只是一次试手，真正的远征还在下面。";
        ui.center(c, sub, 720, 216, 14, ui.alpha(Ui.WHITE, 210), false);

        // 统计卡
        drawStats(c, ui, time, g, stats, reveal, accent);

        // 奖励
        drawRewards(c, ui, time, g, won, reveal, stats);

        // 按钮
        float by = 692, bw = 270, bh = 88, gap = 28;
        boolean canRestart = true;
        ui.button(c, Ui.A_RESTART, 720 - bw - gap * .5f, by, bw, bh,
                tutorial ? "强 化 星 盘" : "再 来 一 局", true, canRestart);
        ui.button(c, Ui.A_HOME, 720 + gap * .5f, by, bw, bh,
                tutorial ? "准 备 远 征" : "返 回 营 地", false, true);
        ui.centerSmall(c, tutorial ? "首趟奖励已入账 · 下一局开始累计正式胜场" : "记录已自动写入星盘",
                720, by + bh + 20, 11.5f, ui.alpha(Ui.MUTED, 180));
    }

    /**
     * 这一局是否真的走完了全部站点。
     *
     * <p>三种模式的总站数不同（远征 15 / 首领连战 6 / 每日 9 / 无尽 999），
     * 而「胜利」既可能来自正常推进到最后一站（{@code Game.advance / clearRoom}），
     * 也可能来自引导导演在 4:45 的脚本收尾。两者都该庆祝，
     * 但只有前者能说「远征完成」。
     */
    private static boolean fullClear(Game g) {
        if (g == null) return true;
        return g.roomsVisited + 1 >= g.roomsTotal;
    }

    private void drawBackdrop(Canvas c, Ui ui, float time, boolean won) {        int id = won ? 1 : 0;
        if (backdrop == null || backdropWon != id) {
            backdropWon = id;
            backdrop = new LinearGradient(0, 0, 0, 810,
                    won ? 0xff2a2317 : 0xff111a24,
                    won ? 0xff0a0906 : 0xff06090d, Shader.TileMode.CLAMP);
        }
        paint.setShader(backdrop);
        rect.set(0, 0, 1440, 810);
        c.drawRect(rect, paint);
        paint.setShader(null);

        int accent = won ? Ui.GOLD : Ui.BLUE;
        ui.glow(c, 720, 140, 520, ui.alpha(accent, 26));
        // 星屑
        for (int i = 0; i < 34; i++) {
            float x = 40 + (i * 197) % 1360;
            float base = 90 + (i * 263) % 560;
            float y = base + (float) Math.sin(time * .5f + i * .7f) * 14f;
            float a = .08f + .20f * (float) (0.5 + 0.5 * Math.sin(time + i));
            ui.circle(c, x, y, 1.2f + (i % 3) * .8f, ui.alpha(accent, (int) (a * 255)));
        }
        if (!won) {
            // 冷色裂纹
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(0x2293c4ff);
            for (int i = 0; i < 6; i++) {
                path.reset();
                float sx = 120 + i * 220;
                path.moveTo(sx, 0);
                path.lineTo(sx - 60, 240);
                path.lineTo(sx + 30, 420);
                c.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawStats(Canvas c, Ui ui, float time, Game g, int[] stats, float reveal, int accent) {
        float x = 388, y = 252, w = 664, h = 88;
        ui.panel(c, x, y, w, h, 14, 0x99101c23, ui.alpha(Ui.EDGE, 150));

        int rooms = g == null ? 0 : Math.max(0, g.roomsVisited);
        int floors = g == null ? 1 : Math.max(1, g.floor);
        int kills = g == null ? 0 : Math.max(0, g.kills);
        int level = g == null ? 1 : Math.max(1, g.level);
        int bosses = g == null ? 0 : Math.max(0, g.bossesDefeated);
        float seconds = g == null ? 0f : Math.max(0f, g.time);

        String[] labels = {"抵达站数", "最高层数", "击杀", "等级", "首领", "用时"};
        // 脚本收尾的引导局只走了 2 站，硬写 "2 / 15" 会读成失败。
        // 这种情况改报「试手 N 站」，并保留总站数在副标题里，信息不丢。
        boolean partial = g != null && g.phase == Game.WON && !fullClear(g);
        String roomsText = g == null ? "0"
                : partial ? "试手 " + rooms + " 站"
                : rooms + " / " + g.roomsTotal;
        String[] values = {roomsText, floors + " 层",
                Integer.toString(kills), "Lv." + level, Integer.toString(bosses), clock(seconds)};
        int[] colors = {Ui.WHITE, Ui.WHITE, Ui.PINK, Ui.MINT, Ui.GOLD, Ui.WHITE};

        float cellW = w / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float cx = x + cellW * (i + .5f);
            float k = Math.min(1f, Math.max(0f, reveal * 1.6f - i * .10f));
            ui.centerSmall(c, labels[i], cx, y + 28, 11.5f, ui.alpha(Ui.MUTED, (int) (200 * k)));
            ui.center(c, values[i], cx, y + 60, ui.fit(values[i], cellW - 24, 22, 14),
                    ui.alpha(colors[i], (int) (255 * k)), true);
            if (i > 0) ui.box(c, x + cellW * i, y + 20, 1, h - 40, .5f, ui.alpha(Ui.EDGE, 150));
        }
    }

    private void drawRewards(Canvas c, Ui ui, float time, Game g, boolean won, float reveal, int[] stats) {
        float y = 372;
        int dust = g == null ? 0 : Math.max(0, Balance.dustReward(g, won));
        int marks = g == null ? 0 : Math.max(0, Balance.markReward(g, won));

        // 星尘
        float x0 = 388, ww = 324;
        ui.panel(c, x0, y, ww, 132, 16, 0x99121d18, ui.alpha(Ui.GOLD, 110));
        ui.text(c, "获得星尘", x0 + 26, y + 40, 12, ui.alpha(Ui.MUTED, 210), false);
        ui.text(c, "+" + Math.round(dust * reveal), x0 + 26, y + 96, 44, Ui.GOLD, true);
        ui.circle(c, x0 + ww - 52, y + 72, 26, 0x33eac487);
        ui.icon(c, x0 + ww - 52, y + 72, 30, 8, ui.alpha(Ui.GOLD, 220));
        ui.text(c, "已计入元进度总星尘", x0 + 26, y + 116, 11, ui.alpha(Ui.MUTED, 165), false);

        // 印记
        float x1 = 388 + ww + 16, w2 = 324;
        ui.panel(c, x1, y, w2, 132, 16, 0x99141a24, ui.alpha(Ui.VIOLET, 110));
        ui.text(c, "获得印记", x1 + 26, y + 40, 12, ui.alpha(Ui.MUTED, 210), false);
        ui.text(c, "+" + Math.round(marks * reveal), x1 + 26, y + 96, 44, Ui.VIOLET, true);
        ui.circle(c, x1 + w2 - 52, y + 72, 26, 0x33c2a7ff);
        ui.icon(c, x1 + w2 - 52, y + 72, 30, 1, ui.alpha(Ui.VIOLET, 220));
        ui.text(c, g != null && g.mode == Game.MODE_ENDLESS ? "无尽模式不结算印记"
                        : "可用于解锁职业与天赋",
                x1 + 26, y + 116, 11, ui.alpha(Ui.MUTED, 165), false);

        // 累计
        int dustAll = stats != null && stats.length > 0 ? stats[0] : dust;
        int marksAll = stats != null && stats.length > 1 ? stats[1] : marks;
        int bestFloor = stats != null && stats.length > 2 ? stats[2] : (g == null ? 1 : g.floor);
        int runs = stats != null && stats.length > 3 ? stats[3] : 1;
        int wins = stats != null && stats.length > 4 ? stats[4] : (won ? 1 : 0);

        float y2 = 524;
        ui.panel(c, 388, y2, 664, 84, 14, 0x99101c23, ui.alpha(Ui.EDGE, 140));
        ui.text(c, "星盘累计", 412, y2 + 32, 11.5f, ui.alpha(Ui.MUTED, 200), false);
        ui.divider(c, 412, y2 + 42, 616);
        String[] k = {"总星尘", "总印记", "最深层", "远征次数", "通关次数"};
        String[] v = {Integer.toString(dustAll), Integer.toString(marksAll),
                bestFloor + " 层", Integer.toString(runs), Integer.toString(wins)};
        for (int i = 0; i < k.length; i++) {
            float cx = 412 + 44 + i * 124;
            ui.text(c, k[i], cx, y2 + 30, 10.5f, ui.alpha(Ui.MUTED, 180), false);
            ui.text(c, v[i], cx, y2 + 56, 15, i == 4 && wins > 0 ? Ui.GOLD : Ui.WHITE, true);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  暂停
    // ══════════════════════════════════════════════════════════════
    private void drawPause(Canvas c, Ui ui, float time, Game g, int[] stats) {
        // 半透明遮罩
        ui.box(c, 0, 0, 1440, 810, 0, 0xC0070b0e);
        ui.box(c, 0, 0, 1440, 810, 0, 0x330b141b);

        float w = 560, h = 574, x = (1440 - w) * .5f, y = (810 - h) * .5f;
        ui.panel(c, x, y, w, h, 22, 0xEE101c23, ui.alpha(Ui.EDGE, 220));
        ui.glow(c, x + w * .5f, y - 10, 240, 0x229de7c1);

        ui.centerSmall(c, "PAUSED", x + w * .5f, y + 42, 11, ui.alpha(Ui.MUTED, 190));
        ui.center(c, "远 征 暂 停", x + w * .5f, y + 90, 36, Ui.WHITE, true);
        ui.divider(c, x + 40, y + 116, w - 80);

        if (g != null) {
            Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
            ui.centerSmall(c, Content.BIOME_NAMES[Math.max(0, Math.min(Content.BIOME_NAMES.length - 1, g.biome))]
                            + "  ·  第 " + (Math.max(0, g.roomsVisited) + 1) + " / " + g.roomsTotal + " 站",
                    x + w * .5f, y + 146, 13, ui.alpha(Ui.WHITE, 210));
            if (p != null) {
                int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));
                ui.centerSmall(c, Content.ROLES[role] + "  ·  生命 " + (int) p.hp + " / " + (int) p.maxHp
                                + "  ·  遗物 " + g.relicCount(p) + "  ·  星尘 " + Math.max(0, g.coins),
                        x + w * .5f, y + 170, 12, ui.alpha(Ui.MUTED, 210));
            }
        }
        ui.divider(c, x + 40, y + 192, w - 80);

        ui.button(c, Ui.A_RESUME, x + 60, y + 216, w - 120, 88, "继 续 远 征", true, true);
        ui.button(c, Ui.A_LEAVE, x + 60, y + 322, w - 120, 88, "保存并返回营地", false, true);
        ui.button(c, Ui.A_SETTINGS, x + 60, y + 428, w - 120, 88, "设 置", false, true);

        ui.centerSmall(c, "战斗已暂停，进度会自动写入存档", x + w * .5f, y + h - 18, 11,
                ui.alpha(Ui.MUTED, 165));
    }

    private static String clock(float s) {
        int t = Math.max(0, (int) s);
        int m = t / 60, sec = t % 60;
        return (m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
    }
}
