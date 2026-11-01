package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/** 营地三合一：营火（CAMP）/ 铁匠铺（ARMORY）/ 神龛（SHRINE）。 */
public final class CampScreen {
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private Shader backdrop;
    private int backdropPhase = -1;

    public CampScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        if (c == null || ui == null || g == null) return;
        ui.clearHits();
        hit = -1;

        drawBackdrop(c, ui, time, g);
        if (g.phase == Game.ARMORY) {
            drawArmory(c, ui, time, g, canLead);
        } else if (g.phase == Game.SHRINE) {
            drawShrine(c, ui, time, g, canLead);
        } else {
            drawCamp(c, ui, time, g, canLead);
        }
        drawFooter(c, ui, g, canLead);
        hit = ui.hitAt(px, py);
    }

    /** 营火 {@code 5000+n}，铁匠铺 {@code 5100+n}，神龛 {@code 5200+n}。 */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id >= 5000 && id < 5300) return id;
        return -1;
    }

    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ── 背景 ────────────────────────────────────────────────────
    private void drawBackdrop(Canvas c, Ui ui, float time, Game g) {
        int phase = g.phase;
        if (backdrop == null || backdropPhase != phase) {
            backdropPhase = phase;
            int top, bottom;
            if (phase == Game.ARMORY) { top = 0xff1b1a22; bottom = 0xff0a0b10; }
            else if (phase == Game.SHRINE) { top = 0xff1a1626; bottom = 0xff08070f; }
            else { top = 0xff1b2118; bottom = 0xff080c0a; }
            backdrop = new LinearGradient(0, 0, 0, 810, top, bottom, Shader.TileMode.CLAMP);
        }
        paint.setShader(backdrop);
        rect.set(0, 0, 1440, 810);
        c.drawRect(rect, paint);
        paint.setShader(null);

        int accent = accentOf(phase);
        // 光晕
        ui.glow(c, 720, 60, 460, ui.alpha(accent, 34));
        ui.glow(c, 720, 100, 240, ui.alpha(accent, 30));
        // 漂浮火星
        for (int i = 0; i < 26; i++) {
            float x = 70 + (i * 211) % 1300;
            float drift = (float) Math.sin(time * .55f + i) * 16f;
            float rise = ((time * (14 + i % 9)) + i * 61) % 560;
            float y = 720 - rise;
            float a = .10f + .30f * (1f - rise / 560f);
            ui.circle(c, x + drift, y, 1.2f + (i % 3) * .7f, ui.alpha(accent, (int) (a * 255)));
        }
    }

    private static int accentOf(int phase) {
        if (phase == Game.ARMORY) return Ui.GOLD;
        if (phase == Game.SHRINE) return Ui.VIOLET;
        return Ui.MINT;
    }

    // ══════════════════════════════════════════════════════════════
    //  营火
    // ══════════════════════════════════════════════════════════════
    private void drawCamp(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        drawHeader(c, ui, "休 整 营 地", "CAMPFIRE",
                "火光把石壁照得发红，这里只够做一件事。", g, canLead);
        drawCampArt(c, ui, time, g);

        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        float cardW = 368, cardH = 236, gap = 28;
        float startX = (1440 - (cardW * 3 + gap * 2)) * .5f;
        float y = 452;

        campCard(c, ui, 5000, startX, y, cardW, cardH, "休息", "REST", Ui.MINT, canLead,
                p == null ? "生命 +45%" : "生命 " + (int) p.hp + " → " + (int) Math.min(p.maxHp, p.hp + p.maxHp * .45f),
                "在火边坐一会儿。生命上限的 45% 会被填回，倒下的队友也会重新站起。",
                "恢复量随生命上限提高，越靠后期越值钱。");
        campCard(c, ui, 5001, startX + cardW + gap, y, cardW, cardH, "锻造", "FORGE", Ui.GOLD, canLead,
                "伤害 +10%  ·  生命上限 +14",
                "把碎片敲进武器。基础伤害永久提升，同时补上一点生命上限。",
                "偏进攻的选择，适合已经能站住的构筑。");
        campCard(c, ui, 5002, startX + (cardW + gap) * 2, y, cardW, cardH, "探索", "SCOUT", Ui.BLUE, canLead,
                "移速 +6%  ·  暴击 +6%  ·  星尘 +24",
                "循着岩缝里的风声走一圈。带回星尘，也带回一点灵活。",
                "偏功能性的选择，星尘差距在商店里会放大。");
    }

    private void drawCampArt(Canvas c, Ui ui, float time, Game g) {
        float x = 464, y = 186, w = 512, h = 246;
        ui.panel(c, x, y, w, h, 18, 0x99101519, ui.alpha(Ui.EDGE, 170));
        c.save();
        c.clipRect(x + 1, y + 1, x + w - 1, y + h - 1);

        // 地面
        ui.box(c, x, y + h - 42, w, 42, 0, 0xff111a16);
        // 火焰
        float cx = x + w * .5f, cy = y + h - 58;
        float flick = (float) Math.sin(time * 7.3f) * 8f + (float) Math.sin(time * 11.7f) * 4f;
        ui.glow(c, cx, cy - 20, 150 + flick, 0x4434e0a0);
        ui.glow(c, cx, cy - 10, 78, 0x66ffd47a);
        for (int i = 0; i < 3; i++) {
            float ww = 46 - i * 10, hh = 96 - i * 22 + flick * (i + 1) * .4f;
            paint.setStyle(Paint.Style.FILL);
            int col = i == 0 ? 0xAAf18f8b : i == 1 ? 0xCCeac487 : 0xEEfff3d0;
            paint.setColor(col);
            path.reset();
            path.moveTo(cx, cy - hh);
            path.cubicTo(cx + ww, cy - hh * .55f, cx + ww * .8f, cy, cx, cy);
            path.cubicTo(cx - ww * .8f, cy, cx - ww, cy - hh * .55f, cx, cy - hh);
            path.close();
            c.drawPath(path, paint);
        }
        // 柴堆
        ui.box(c, cx - 70, cy + 2, 140, 12, 6, 0xff2a2018);
        ui.box(c, cx - 54, cy - 8, 108, 10, 5, 0xff3a2c20);
        // 队友剪影
        if (!g.players.isEmpty()) {
            for (int i = 0; i < Math.min(3, g.players.size()); i++) {
                Game.Player q = g.players.get(i);
                float ax = x + 110 + i * 46;
                ui.avatar(c, ax, cy + 18, 68, Math.max(0, Math.min(Content.ROLES.length - 1, q.role)),
                        time + i * 1.7f, i % 2 == 1);
            }
        }
        c.restore();
        ui.stroke(c, x, y, w, h, 18, ui.alpha(Ui.EDGE, 180), 1.3f);
        ui.right(c, Content.ROOM_SUBTITLE[Game.ROOM_CAMP], x + w - 20, y + 28, 11,
                ui.alpha(Ui.MUTED, 190), false);
    }

    private void campCard(Canvas c, Ui ui, int id, float x, float y, float w, float h,
                          String name, String en, int accent, boolean canLead,
                          String value, String desc, String tip) {
        boolean hover = canLead && (inside(x, y, w, h) || pressed(id));
        ui.card(c, x, y, w, h, accent, false, hover);
        if (canLead) ui.hit(id, x, y, w, h);
        if (hover) ui.box(c, x + 1, y + 1, 5, h - 2, 2.5f, ui.alpha(accent, 200));

        ui.gradientRect(c, x + 1, y + 1, w - 2, 76, 15, ui.alpha(accent, 30), 0x00000000);
        ui.circle(c, x + 46, y + 46, 26, ui.alpha(accent, 40));
        ui.ring(c, x + 46, y + 46, 26, ui.alpha(accent, 170), 1.6f);
        ui.icon(c, x + 46, y + 46, 26, id == 5000 ? 6 : id == 5001 ? 3 : 0, accent);

        ui.text(c, name, x + 86, y + 52, 26, canLead ? Ui.WHITE : ui.alpha(Ui.WHITE, 180), true);
        ui.text(c, en, x + 86 + ui.measure(name, 26, true) + 12, y + 52, 10,
                ui.alpha(Ui.MUTED, 170), false);

        ui.divider(c, x + 24, y + 86, w - 48);
        ui.text(c, value, x + 24, y + 118, ui.fit(value, w - 48, 15, 11),
                canLead ? accent : ui.alpha(accent, 140), true);
        ui.wrap(c, desc, x + 24, y + 146, w - 48, 12.5f, ui.alpha(Ui.WHITE, 205));
        ui.text(c, tip, x + 24, y + h - 20, ui.fit(tip, w - 48, 10.5f, 8.5f),
                ui.alpha(Ui.MUTED, 150), false);

        if (!canLead) ui.locked(c, x + 24, y + h - 74, w - 48, 40, "等待队长");
    }

    // ══════════════════════════════════════════════════════════════
    //  铁匠铺
    // ══════════════════════════════════════════════════════════════
    private void drawArmory(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        drawHeader(c, ui, "武 器 祭 坛", "ARMORY",
                "铁砧还热着。三条进化线只够选一条。", g, canLead);

        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        int role = p == null ? 0 : Math.max(0, Math.min(Content.ROLES.length - 1, p.role));

        float cardW = 400, cardH = 430, gap = 30;
        float startX = (1440 - (cardW * 3 + gap * 2)) * .5f;
        float y = 208;
        for (int line = 0; line < 3; line++) {
            int idx = Evolutions.index(role, line);
            armoryCard(c, ui, 5100 + line, startX + line * (cardW + gap), y, cardW, cardH,
                    idx, p, canLead, line);
        }

        ui.centerSmall(c, "进化会替换武器表现与数值，已拥有的进化线不再重复提供。",
                720, 672, 12, ui.alpha(Ui.MUTED, 190));
    }

    private void armoryCard(Canvas c, Ui ui, int id, float x, float y, float w, float h,
                            int idx, Game.Player p, boolean canLead, int line) {
        boolean owned = p != null && idx >= 0 && idx < Evolutions.COUNT && p.evolved[idx];
        boolean hover = canLead && (inside(x, y, w, h) || pressed(id));
        int tier = Math.max(1, Math.min(3, Evolutions.TIER[Math.max(0, Math.min(Evolutions.COUNT - 1, idx))]));
        int accent = owned ? ui.alpha(Ui.GOLD, 150) : tier >= 3 ? Ui.GOLD : tier == 2 ? Ui.PINK : Ui.MINT;

        ui.card(c, x, y, w, h, accent, hover, hover);
        if (canLead) ui.hit(id, x, y, w, h);
        if (hover) ui.box(c, x + 1, y + 1, 5, h - 2, 2.5f, ui.alpha(accent, 200));

        ui.gradientRect(c, x + 1, y + 1, w - 2, 92, 15, ui.alpha(accent, 28), 0x00000000);
        ui.pill(c, x + 22, y + 22, 112, "进化线 " + (line + 1), accent);
        ui.pill(c, x + 146, y + 22, 74, "第 " + tier + " 阶",
                tier >= 3 ? Ui.GOLD : ui.alpha(Ui.MUTED, 220));

        float cx = x + w * .5f, cy = y + 152;
        ui.glow(c, cx, cy, 88, ui.alpha(accent, 50));
        ui.circle(c, cx, cy, 58, 0xCC0b141b);
        ui.ring(c, cx, cy, 58, ui.alpha(accent, 190), 2.2f);
        ui.icon(c, cx, cy, 52, 3 + line, accent);
        if (owned) {
            ui.pill(c, cx - 40, cy + 66, 80, "已解锁", Ui.GOLD);
        }

        String name = Evolutions.NAMES[Math.max(0, Math.min(Evolutions.COUNT - 1, idx))];
        ui.center(c, name, x + w * .5f, y + 262, ui.fit(name, w - 56, 26, 17),
                owned ? ui.alpha(Ui.WHITE, 190) : Ui.WHITE, true);
        ui.centerSmall(c, Evolutions.KEY[Math.max(0, Math.min(Evolutions.COUNT - 1, idx))],
                x + w * .5f, y + 284, 9.5f, ui.alpha(accent, 170));

        String desc = Evolutions.DESC[Math.max(0, Math.min(Evolutions.COUNT - 1, idx))];
        ui.wrap(c, desc, x + 28, y + 312, w - 56, 13, ui.alpha(Ui.WHITE, 220));

        // 需求
        int req = Evolutions.REQ_RELIC[Math.max(0, Math.min(Evolutions.COUNT - 1, idx))];
        float ry = y + h - 156;
        ui.divider(c, x + 24, ry - 14, w - 48);
        if (req < 0) {
            ui.text(c, "需求 · 无前置遗物", x + 24, ry + 12, 12, ui.alpha(Ui.MUTED, 215), false);
        } else {
            boolean has = p != null && p.relics[req] > 0;
            ui.text(c, "需求 · " + Relics.NAMES[req], x + 24, ry + 12, 12,
                    has ? Ui.MINT : Ui.RED, true);
            ui.right(c, has ? "已持有" : "未持有", x + w - 24, ry + 12, 11.5f,
                    has ? Ui.MINT : ui.alpha(Ui.MUTED, 190), false);
        }
        if (p != null) {
            int totalRelics = 0;
            for (int r = 0; r < Relics.COUNT; r++) totalRelics += Math.max(0, p.relics[r]);
            ui.text(c, "当前遗物 " + totalRelics, x + 24, ry + 34, 11.5f,
                    ui.alpha(Ui.MUTED, 190), false);
            ui.right(c, "已进化 " + Math.max(0, p.evolutionsTaken) + " 条", x + w - 24, ry + 34, 11.5f,
                    ui.alpha(Ui.MUTED, 190), false);
        }

        boolean usable = canLead;
        ui.button(c, id, x + 26, y + h - 96, w - 52, 88,
                owned ? "已拥有 · 确认强化" : "选择这条进化", owned, usable);
    }

    // ══════════════════════════════════════════════════════════════
    //  神龛
    // ══════════════════════════════════════════════════════════════
    private void drawShrine(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        drawHeader(c, ui, "沉 默 神 龛", "SHRINE",
                "神龛只回应一次。付钱，或者留下代价。", g, canLead);
        drawShrineArt(c, ui, time);

        boolean used = g.shrineUsed;
        float cardW = 520, cardH = 268, gap = 40;
        float startX = (1440 - (cardW * 2 + gap)) * .5f;
        float y = 456;

        shrineCard(c, ui, 5200, startX, y, cardW, cardH, "供奉", "OFFERING", Ui.GOLD,
                "支付 60 星尘", "暴击 +10%  ·  伤害 +12%",
                "把星尘放在基座上。神龛收下，然后在你的武器里留下一点光。",
                canLead && !used && g.coins >= 60, used,
                !used && g.coins < 60 ? "星尘不足 · 需要 60" : "供奉不可重复");

        shrineCard(c, ui, 5201, startX + cardW + gap, y, cardW, cardH, "取走供品", "TAKE",
                Ui.VIOLET, "无需星尘", "获得一件遗物  ·  本层承伤提升",
                "供品本来就是给死人的。你可以拿走它，代价会跟着你走一段路。",
                canLead && !used, used, "本层承伤将提高");

        if (used) {
            ui.center(c, "神龛已沉寂，本次回应不可重复。", 720, 758, 13, ui.alpha(Ui.MUTED, 200), false);
        }
    }

    private void drawShrineArt(Canvas c, Ui ui, float time) {
        float x = 520, y = 178, w = 400, h = 250;
        ui.panel(c, x, y, w, h, 18, 0x99100e18, ui.alpha(Ui.VIOLET, 120));
        c.save();
        c.clipRect(x + 1, y + 1, x + w - 1, y + h - 1);
        float cx = x + w * .5f, cy = y + h * .62f;
        // 光束
        for (int i = 0; i < 7; i++) {
            float a = (float) (Math.PI * (1.05 + i * .14));
            ui.line(c, cx, cy - 30, cx + (float) Math.cos(a) * 260, cy + (float) Math.sin(a) * 260,
                    ui.alpha(Ui.VIOLET, 22 + i * 3), 26f);
        }
        ui.glow(c, cx, cy - 60, 120, 0x55a06bff);
        // 基座
        ui.box(c, cx - 92, cy + 4, 184, 26, 8, 0xff231e33);
        ui.box(c, cx - 70, cy - 20, 140, 28, 6, 0xff2d2642);
        // 神像
        path.reset();
        path.moveTo(cx, cy - 132);
        path.lineTo(cx + 30, cy - 20);
        path.lineTo(cx - 30, cy - 20);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff3a3153);
        c.drawPath(path, paint);
        ui.circle(c, cx, cy - 146, 22, 0xff4a3f6b);
        ui.ring(c, cx, cy - 146, 28, ui.alpha(Ui.VIOLET, 120), 1.6f);
        // 浮尘
        for (int i = 0; i < 12; i++) {
            float ang = time * .5f + i * .52f;
            float rr = 60 + (i % 4) * 32;
            ui.circle(c, cx + (float) Math.cos(ang) * rr,
                    cy - 70 + (float) Math.sin(ang * 1.3f) * rr * .7f,
                    1.6f + i % 2, ui.alpha(Ui.VIOLET, 140));
        }
        c.restore();
        ui.stroke(c, x, y, w, h, 18, ui.alpha(Ui.VIOLET, 130), 1.3f);
    }

    private void shrineCard(Canvas c, Ui ui, int id, float x, float y, float w, float h,
                            String name, String en, int accent, String cost, String gain,
                            String desc, boolean enabled, boolean used, String warn) {
        boolean hover = enabled && (inside(x, y, w, h) || pressed(id));
        ui.card(c, x, y, w, h, accent, hover, hover);
        if (enabled) ui.hit(id, x, y, w, h);
        if (hover) ui.box(c, x + 1, y + 1, 5, h - 2, 2.5f, ui.alpha(accent, 200));

        ui.gradientRect(c, x + 1, y + 1, w - 2, 84, 15, ui.alpha(accent, 26), 0x00000000);
        ui.text(c, name, x + 28, y + 52, 27, enabled ? Ui.WHITE : ui.alpha(Ui.WHITE, 160), true);
        ui.text(c, en, x + 28 + ui.measure(name, 27, true) + 14, y + 52, 10,
                ui.alpha(Ui.MUTED, 165), false);
        ui.right(c, cost, x + w - 28, y + 52, 14,
                enabled ? Ui.GOLD : ui.alpha(Ui.MUTED, 180), true);

        ui.divider(c, x + 26, y + 82, w - 52);
        ui.text(c, gain, x + 28, y + 118, ui.fit(gain, w - 56, 16, 11.5f),
                enabled ? accent : ui.alpha(accent, 130), true);
        ui.wrap(c, desc, x + 28, y + 148, w - 56, 12.5f, ui.alpha(Ui.WHITE, 200));
        ui.text(c, warn, x + 28, y + h - 22, 11, enabled ? ui.alpha(Ui.MUTED, 165) : Ui.RED, false);

        if (used) {
            ui.locked(c, x + 26, y + h - 86, w - 52, 48, "已使用");
        } else if (!enabled) {
            ui.locked(c, x + 26, y + h - 86, w - 52, 48, warn);
        }
    }

    // ── 通用头部 / 底部 ─────────────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, String title, String en, String hint,
                            Game g, boolean canLead) {
        ui.box(c, 0, 0, 1440, 168, 0, 0xB30b141b);
        ui.centerSmall(c, en, 720, 44, 11, ui.alpha(accentOf(g.phase), 180));
        ui.center(c, title, 720, 92, 40, Ui.WHITE, true);
        ui.centerSmall(c, hint, 720, 128, 14, ui.alpha(Ui.MUTED, 225));
        ui.divider(c, 300, 150, 840);

        ui.text(c, "第 " + Math.max(1, g.floor) + " 章  ·  第 " + (g.roomsVisited + 1) + " / "
                + g.roomsTotal + " 站", 64, 52, 13, ui.alpha(Ui.MUTED, 215), false);
        ui.right(c, "星尘 " + Math.max(0, g.coins), 1376, 52, 19, Ui.GOLD, true);
        if (!canLead) ui.right(c, "等待队长选择", 1376, 78, 12, Ui.BLUE, false);
    }

    private void drawFooter(Canvas c, Ui ui, Game g, boolean canLead) {
        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        ui.box(c, 0, 764, 1440, 46, 0, 0x99070d11);
        if (p != null) {
            int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));
            ui.text(c, Content.ROLES[role], 64, 793, 13, Ui.WHITE, true);
            ui.text(c, "生命 " + (int) p.hp + " / " + (int) p.maxHp, 64 + 92, 793, 12.5f,
                    p.hp / Math.max(1f, p.maxHp) < .4f ? Ui.RED : Ui.MINT, false);
            ui.text(c, "遗物 " + g.relicCount(p), 64 + 250, 793, 12.5f, Ui.GOLD, false);
            ui.text(c, "击杀 " + Math.max(0, g.kills), 64 + 340, 793, 12.5f,
                    ui.alpha(Ui.MUTED, 215), false);
        }
        ui.right(c, "选择后自动离开本房间", 1376, 793, 11.5f, ui.alpha(Ui.MUTED, 180), false);
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}
