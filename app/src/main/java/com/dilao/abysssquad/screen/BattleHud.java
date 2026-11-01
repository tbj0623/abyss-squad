package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 局内战斗 HUD（1440x810 覆盖层）。
 *
 * <p>战斗区 1120x620 居中偏移 (160,106)。战斗按钮的圆心必须与 {@code GameView.onTouchEvent}
 * 的固定热区一致：闪避 (1170,700) r54、技能 (1300,660) r54、终极 (1300,552) r46。
 * 摇杆区域为 {@code x < 620 && y > 330}，瞄准区域为 {@code x > 980 && 340 < y < 640}。
 */
public final class BattleHud {
    /** 逻辑画布宽（与 GameView 的 1440x810 一致），左撇子镜像用。 */
    private static final float W = 1440f;

    /** 本帧 hover 的命中 id（由外部注入指针坐标时有效）。 */
    public int hit = -1;

    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    /**
     * 左撇子镜像。
     *
     * <p>GameView 的触摸判定早就按 {@code bx = leftHanded ? W - x : x} 镜像了，
     * 但 HUD 一直画在右侧 —— 「画面上球在右下、实际要点左上」，等于把游戏玩坏。
     * 开启后摇杆搬到右侧、闪避/技能/终极搬到左侧，视觉与热区落在同一处。
     *
     * <p>顶部信息条<b>不镜像</b>：它没有任何触摸目标，纯信息展示，
     * 翻转只会把「用时 / 击杀 / 等级 / 星尘」的阅读顺序弄别扭。
     */
    private boolean mirror;

    public void setMirror(boolean m) { mirror = m; }

    /** 中心坐标镜像。 */
    private float mx(float x) { return mirror ? W - x : x; }

    /** 矩形左边界镜像：给定未镜像的左边界与宽度，返回镜像后的左边界。 */
    private float mrect(float x, float w) { return mirror ? W - x - w : x; }

    // ── 技能按钮按压反馈 ────────────────────────────────────────
    // 闪避/技能/终极在 GameView 里是三个纯距离热点（不走 Ui 矩形登记表），
    // 所以以前按下它们画面上毫无反应。移动端「点了没反应」会被读成「按钮坏了」。
    // GameView 触发时调 press()，这里做 160ms 的压下动画。
    public static final int P_DASH = 0, P_SKILL = 1, P_ULT = 2;
    private final long[] pressAt = {-1, -1, -1};
    private static final long PRESS_MS = 160_000_000L;

    public void press(int which) {
        if (which >= 0 && which < pressAt.length) pressAt[which] = System.nanoTime();
    }

    /** 1 = 刚按下，0 = 动画结束。 */
    private float punch(int which) {
        long t = pressAt[which];
        if (t < 0) return 0f;
        float f = 1f - (System.nanoTime() - t) / (float) PRESS_MS;
        return f <= 0f ? 0f : f;
    }

    public BattleHud() {}

    /** 可选：由外部注入指针坐标以启用 hover 反馈。 */
    public void pointer(float x, float y) { px = x; py = y; }

    /**
     * 菜单按钮返回 {@link Ui#A_LEAVE}，构筑按钮返回 {@link Ui#A_GUIDE}；
     * 同时兼容 GameView 的 9102（暂停）/ 9101（构筑）。
     */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id == Ui.A_LEAVE || id == 9102) return Ui.A_LEAVE;
        if (id == Ui.A_GUIDE || id == 9101) return Ui.A_GUIDE;
        return -1;
    }

    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, Game.Player p,
                     boolean canLead, float shakeX, float shakeY) {
        if (c == null || ui == null) return;
        ui.clearHits();
        hit = -1;
        if (g == null) return;

        drawTop(c, ui, time, g, p);
        drawSideButtons(c, ui, time);
        if (p != null) {
            drawVitals(c, ui, time, g, p);
            // 流派层数条排在摇杆之前：两者纵向只有 10px 重叠（条带 676..698 vs 摇杆下缘 686），
            // 让摇杆后画、浮在常驻信息条之上，拇指下的控件才不会被压暗。
            drawSchoolStrip(c, ui, g, p);
            drawJoystick(c, ui, p);
            drawAim(c, ui, p);
            drawOrbs(c, ui, time, p);
        }
        drawNotice(c, ui, g);
        // shakeX / shakeY 已由 GameView 施加到战斗区，此处不再重复位移。
        if (shakeX != 0f || shakeY != 0f) hit = ui.hitAt(px, py);
    }

    // ── 顶部条（高 100）────────────────────────────────────────
    private void drawTop(Canvas c, Ui ui, float time, Game g, Game.Player p) {
        ui.gradientRect(c, 0, 0, 1440, 106, 0, 0xF214232C, 0xF20B141B);
        ui.box(c, 0, 104, 1440, 2, 1, ui.alpha(Ui.EDGE, 190));
        ui.glow(c, 720, 0, 320, 0x149de7c1);

        // 左：模式 / 站数 / 生态
        int mode = Math.max(0, Math.min(3, g.mode));
        int biome = Math.max(0, Math.min(Content.BIOME_NAMES.length - 1, g.biome));
        int station = Math.min(g.roomsTotal, g.roomsVisited + 1);
        ui.text(c, Content.MODE_NAMES[mode] + "   ·   第 " + station + " / " + g.roomsTotal + " 站",
                32, 32, 12, ui.alpha(Ui.MUTED, 225), false);
        ui.text(c, Content.BIOME_NAMES[biome], 32, 64, 24, Ui.WHITE, true);
        ui.text(c, Content.BIOME_SUB[biome], 32, 86, 10, ui.alpha(Ui.MUTED, 165), false);

        // 中：目标进度
        drawObjective(c, ui, time, g, 404, 1036);

        // 右：状态数值
        statCell(c, ui, 1076, 22, "用时", clock(g.roomTime), ui.alpha(Ui.WHITE, 245), false);
        statCell(c, ui, 1152, 22, "击杀", Integer.toString(Math.max(0, g.kills)), Ui.PINK, false);
        statCell(c, ui, 1220, 22, "等级", Integer.toString(Math.max(1, g.level)), Ui.MINT, false);
        statCell(c, ui, 1298, 22, "星尘", Integer.toString(Math.max(0, g.coins)), Ui.GOLD, true);
        ui.bar(c, 1108, 82, 190, 7, Math.max(0f, Math.min(1f, g.xp / Math.max(1f, g.xpGoal))), Ui.MINT);
        ui.text(c, "EXP", 1108, 74, 8.5f, ui.alpha(Ui.MUTED, 200), false);
    }

    private void statCell(Canvas c, Ui ui, float cx, float y, String label, String value,
                          int color, boolean alignRight) {
        if (alignRight) {
            ui.right(c, label, cx + 34, y + 12, 9.5f, ui.alpha(Ui.MUTED, 190), false);
            ui.right(c, value, cx + 34, y + 44, ui.fit(value, 66, 20, 12), color, true);
        } else {
            ui.centerSmall(c, label, cx, y + 12, 9.5f, ui.alpha(Ui.MUTED, 190));
            ui.center(c, value, cx, y + 38, ui.fit(value, 62, 20, 12), color, true);
        }
    }

    private void drawObjective(Canvas c, Ui ui, float time, Game g, float x0, float x1) {
        float w = x1 - x0, cx = (x0 + x1) * .5f;
        if (g.roomType == Game.ROOM_BOSS) {
            Game.Enemy boss = null;
            for (int i = 0; i < g.enemies.size(); i++) {
                Game.Enemy e = g.enemies.get(i);
                if (e != null && e.bossIndex >= 0) { boss = e; break; }
            }
            int bi = Math.max(0, Math.min(Bosses.COUNT - 1, g.bossIndexForRoom()));
            String name = boss != null && boss.bossIndex >= 0
                    ? Bosses.NAMES[Math.max(0, Math.min(Bosses.COUNT - 1, boss.bossIndex))]
                    : Bosses.NAMES[bi];
            String title = boss != null && boss.bossIndex >= 0
                    ? Bosses.TITLE[Math.max(0, Math.min(Bosses.COUNT - 1, boss.bossIndex))]
                    : Bosses.TITLE[bi];
            ui.centerSmall(c, "首 领", cx, 20, 9.5f, ui.alpha(Ui.RED, 190));
            ui.center(c, name + "   ·   " + title, cx, 42, 17, Ui.WHITE, true);
            float amount = boss == null || boss.maxHp <= 0 ? 0f : Math.max(0f, boss.hp / boss.maxHp);
            ui.barSeg(c, x0, 56, w, 18, amount, Ui.RED, 3);
            int phase = amount > .66f ? 1 : amount > .33f ? 2 : 3;
            ui.centerSmall(c, "阶段 " + phase + " / 3   ·   " + Math.max(0, (int) (boss == null ? 0 : boss.hp))
                    + " / " + (int) (boss == null ? 0 : boss.maxHp), cx, 92, 10.5f,
                    ui.alpha(Ui.MUTED, 215));
            return;
        }

        String label;
        String detail;
        float amount;
        int color = Ui.MINT;
        int total = Math.max(1, g.target);
        if (g.roomType == Game.ROOM_CHALLENGE) {
            color = Ui.BLUE;
            if (g.objective == 1) {
                label = "守住星环";
                amount = Math.max(0f, Math.min(1f, g.charge / 26f));
                detail = (int) Math.min(26f, g.charge) + " / 26 秒充能";
            } else if (g.objective == 2) {
                label = "生存倒计时";
                amount = Math.max(0f, Math.min(1f, g.roomTime / 40f));
                detail = "剩余 " + Math.max(0, 40 - (int) g.roomTime) + " 秒";
            } else {
                label = "灵魂狩猎";
                int need = 18, got = Math.max(0, g.kills - g.roomStartKills);
                amount = Math.max(0f, Math.min(1f, got / (float) need));
                detail = Math.min(need, got) + " / " + need + " 名守卫";
            }
        } else if (g.roomType == Game.ROOM_ELITE) {
            color = Ui.PINK;
            label = "精英清剿";
            amount = Math.max(0f, Math.min(1f, g.spawned / (float) total));
            detail = Math.max(0, Math.min(g.target, g.spawned)) + " / " + g.target
                    + "   ·   剩余精英 " + eliteLeft(g);
        } else if (g.roomType == Game.ROOM_TREASURE) {
            color = Ui.GOLD;
            label = "封印宝库";
            amount = Math.max(0f, Math.min(1f, g.spawned / (float) total));
            detail = Math.max(0, Math.min(g.target, g.spawned)) + " / " + g.target + "   ·   击破守卫解锁宝藏";
        } else {
            label = "清剿进度";
            amount = Math.max(0f, Math.min(1f, g.spawned / (float) total));
            detail = Math.max(0, Math.min(g.target, g.spawned)) + " / " + g.target
                    + (g.waves > 0 ? "   ·   第 " + Math.min(g.waves, g.wave + 1) + " / " + g.waves + " 波" : "");
        }
        ui.centerSmall(c, label, cx, 20, 10.5f, ui.alpha(Ui.MUTED, 210));
        ui.bar(c, x0, 30, w, 20, amount, color);
        ui.centerSmall(c, detail, cx, 78, 11, ui.alpha(Ui.WHITE, 205));
        if (amount > 0f && amount < 1f) {
            ui.glow(c, x0 + w * amount, 40, 24, ui.alpha(color, 55));
        }
    }

    private static int eliteLeft(Game g) {
        int n = 0;
        for (int i = 0; i < g.enemies.size(); i++) {
            Game.Enemy e = g.enemies.get(i);
            if (e != null && e.elite && e.hp > 0) n++;
        }
        return n;
    }

    // ── 右下：技能 / 闪避 / 终极（与 GameView 热区一致）──────────
    private void drawOrbs(Canvas c, Ui ui, float time, Game.Player p) {
        int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));

        // 闪避 (1170,700) r54
        float dashMax = Math.max(.5f, 3.2f * Math.max(.35f, p.dashFactor));
        float dashLeft = Math.max(0f, p.dashCd);
        boolean dashReady = dashLeft <= .001f;
        float dCx = mx(1170);
        drawOrb(c, ui, dCx, 700, 54, dashReady ? Ui.MINT : ui.EDGE,
                1f - Math.min(1f, dashLeft / dashMax), dashReady ? null : one(dashLeft),
                punch(P_DASH));
        ui.icon(c, dCx, 700 - 54 * .30f, 30, 7, dashReady ? Ui.WHITE : ui.alpha(Ui.MUTED, 200));
        ui.centerSmall(c, "闪避", dCx, 700 + 54 * .62f, 10,
                dashReady ? Ui.WHITE : ui.alpha(Ui.MUTED, 190));
        ui.centerSmall(c, "SPACE", dCx, 776, 8.5f, ui.alpha(Ui.MUTED, 140));

        // 技能 (1300,660) r54 —— 中心与命中热区一致
        float skillMax = skillMax(p.role);
        float skillLeft = Math.max(0f, p.skillCd);
        boolean skillReady = skillLeft <= .001f;
        float sCx = mx(1300);
        drawOrb(c, ui, sCx, 660, 54, skillReady ? Ui.BLUE : Ui.EDGE,
                1f - Math.min(1f, skillLeft / skillMax), skillReady ? null : one(skillLeft),
                punch(P_SKILL));
        ui.schoolIcon(c, sCx, 660 - 54 * .34f, 30, Content.SCHOOL[role]);
        ui.centerSmall(c, Content.SKILLS[role], sCx, 660 + 54 * .56f, 10.5f,
                skillReady ? Ui.WHITE : ui.alpha(Ui.MUTED, 190));

        // 终极 (1300,552) r46 —— 与技能圈间隙 552+46=598 / 660-54=606，留 8px
        // 说明文字一律画在球内，不再向外溢出压到相邻按钮
        float uCx = mx(1300);
        float chargeF = Math.max(0f, Math.min(1f, p.ultCharge / 100f));
        boolean ready = p.ultReady >= 1 || chargeF >= 1f;
        float pulse = ready ? .5f + .5f * (float) Math.sin(time * 5.4f) : 0f;
        if (ready) {
            ui.glow(c, uCx, 552, 46 * (1.55f + pulse * .18f), ui.alpha(Ui.GOLD, (int) (48 + pulse * 54)));
            ui.glow(c, uCx, 552, 46 * 1.12f, ui.alpha(Ui.GOLD, 76));
        } else {
            ui.glow(c, uCx, 552, 46 * 1.10f, ui.alpha(Ui.VIOLET, 30));
        }
        drawOrb(c, ui, uCx, 552, 46, ready ? Ui.GOLD : Ui.VIOLET, ready ? 1f : chargeF,
                ready ? null : Integer.toString((int) p.ultCharge), punch(P_ULT));
        if (ready) {
            ui.center(c, "终 极", uCx, 552 - 8, 17, Ui.GOLD, true);
            ui.centerSmall(c, "READY", uCx, 552 + 14, 9, ui.alpha(Ui.GOLD, 220));
        } else {
            ui.centerSmall(c, (int) p.ultCharge + "%", uCx, 552 + 4, 12f,
                    ui.alpha(Ui.WHITE, 215));
        }
    }

    private static float skillMax(int role) {
        return role == 2 ? 15f : role == 6 ? 13f : role == 7 ? 11f : 9.5f;
    }

    private void drawOrb(Canvas c, Ui ui, float x, float y, float r, int accent,
                         float fill, String cdText, float punch) {
        // 按压时整颗球缩 8%，并压一层白光 —— 拇指被球盖住时这是唯一的“我按到了”证据
        float scale = 1f - .08f * punch;
        float rr = r * scale;
        ui.circle(c, x, y, rr, 0xE60B141B);
        ui.circle(c, x, y, rr - 5 * scale, ui.alpha(Ui.PANEL_HI, 235));
        if (fill < .999f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xC9000000);
            arc.set(x - rr + 5 * scale, y - rr + 5 * scale, x + rr - 5 * scale, y + rr - 5 * scale);
            c.drawArc(arc, -90f + 360f * fill, 360f * (1f - fill), true, paint);
        }
        ui.ring(c, x, y, rr - 2.5f, ui.alpha(accent, fill >= .999f ? 235 : 150),
                fill >= .999f ? 3.2f : 2.2f);
        ui.ring(c, x, y, rr - 8f, ui.alpha(accent, 40), 1.2f);
        if (punch > .01f) {
            ui.circle(c, x, y, rr, ui.alpha(Ui.WHITE, (int) (58 * punch)));
        }
        if (cdText != null) {
            ui.center(c, cdText, x, y - rr * .10f, rr * .38f, Ui.WHITE, true);
        }
    }

    // ── 左下：生命 / 职业 / 遗物 / Buff ─────────────────────────
    //  版面（相对面板左上角 x,y；面板 620x96）：
    //   y+22  职业名 + 称号（基线）        右侧 y+22：遗物 / 流派
    //   y+36  血条（高 22，位于 y+36..y+58，与上方文字不重叠）
    //   y+52  血条内居中数值
    //   y+74  Buff 行 / 状态行
    private void drawVitals(Canvas c, Ui ui, float time, Game g, Game.Player p) {
        // 左撇子时整块面板搬到右下：摇杆去了右侧 (1118,590)，闪避球去了左侧 (270,700)，
        // 面板若留在左下就会被闪避球压住血条。面板宽 620，镜像后 788..1408，
        // 与摇杆（y 494..686）没有纵向交集，与瞄准环（x 394..462）也没有横向交集。
        float x = mrect(32, 620), y = 700, w = 620, h = 96;
        ui.gradientRect(c, x, y, w, h, 14, 0xE614232C, 0xE60B141B);
        ui.outline(c, x, y, w, h, 14, ui.alpha(Ui.EDGE, 200));

        int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));

        // 第一行：图标 + 职业名 + 称号 + 右侧遗物/流派
        ui.schoolIcon(c, x + 24, y + 20, 20, Content.SCHOOL[role]);
        ui.text(c, Content.ROLES[role], x + 42, y + 26, 15, Ui.WHITE, true);
        float nameW = ui.measure(Content.ROLES[role], 15, true);
        ui.text(c, Content.ROLE_TITLE[role], x + 42 + nameW + 10, y + 26, 10.5f,
                ui.alpha(Ui.MUTED, 205), false);
        ui.right(c, "流派 " + Content.SCHOOL_NAMES[Content.SCHOOL[role]], x + w - 20, y + 26, 11.5f,
                ui.alpha(Ui.MUTED, 215), false);
        ui.right(c, "遗物 " + g.relicCount(p), x + w - 108, y + 26, 11.5f, Ui.GOLD, true);

        // 第二行：血条（独立一行，绝不与文字重叠）
        float barY = y + 36, barH = 22, barX = x + 24, barW = 420;
        float hpF = p.maxHp <= 0 ? 0 : Math.max(0f, Math.min(1f, p.hp / p.maxHp));
        int hpColor = hpF > .55f ? Ui.MINT : hpF > .28f ? Ui.GOLD : Ui.RED;
        ui.bar(c, barX, barY, barW, barH, hpF, hpColor);
        if (p.shield > 1f) {
            float sf = Math.max(0f, Math.min(1f, p.shield / Math.max(1f, p.maxHp)));
            float sw = Math.max(10f, (barW - 4) * sf);
            ui.box(c, barX + 2, barY + 3, Math.min(barW - 4, sw), 7, 3.5f, 0xDDEDF1E7);
            ui.box(c, barX + 2, barY + 3, Math.min(barW - 4, sw) * .62f, 2.5f, 1.2f, 0x8CFFFFFF);
        }
        ui.stroke(c, barX, barY, barW, barH, 11, ui.alpha(Ui.EDGE, 210), 1.2f);
        ui.text(c, (int) p.hp + " / " + (int) p.maxHp, barX + 10, barY + 16, 12.5f, 0xE60B141B, true);
        if (p.shield > 1f) {
            ui.right(c, "护盾 " + (int) p.shield, barX + barW - 10, barY + 16, 11.5f, 0xE60B141B, true);
        }
        // 血条右侧：等级与经验
        int level = Math.max(1, g.level);
        ui.text(c, "Lv." + level, x + 462, barY + 16, 13, Ui.MINT, true);
        float xpF = Math.max(0f, Math.min(1f, g.xp / Math.max(1f, g.xpGoal)));
        ui.bar(c, x + 500, barY + 6, 96, 10, xpF, Ui.MINT);

        // 第三行：Buff 行 / 状态（pill 高 28，基线 y+66，必须留出到 y+96 的空间）
        float bx = x + 24, by = y + 64;
        if (p.buff > 0) bx = chip(c, ui, bx, by, "强化 " + one(p.buff), Ui.MINT);
        if (p.invuln > 0) bx = chip(c, ui, bx, by, "无敌 " + one(p.invuln), Ui.BLUE);
        if (g.roomType == Game.ROOM_CHALLENGE && g.objective == 1) {
            bx = chip(c, ui, bx, by, "星环 " + (int) Math.min(26f, g.charge) + "s", Ui.BLUE);
        }
        // v2.1：把 v2.0 新增的两个系统暴露到局内 HUD。
        // 此前契约词条与跨流派共鸣只在选牌界面可见——玩家开局后完全看不到
        // "本局开了哪些 heat""凑齐了哪几组流派"，等于做完了但没有反馈。
        bx = drawRunModifiers(c, ui, bx, by, g, p, x + w);
        if (bx == x + 24) {
            String state = p.down ? "已倒下 · 等待救援" : "状态良好";
            ui.text(c, state, bx, by + 18, 11, p.down ? Ui.RED : ui.alpha(Ui.MUTED, 180), false);
        }
    }

    /**
     * 本局的"规则性修正"标签：契约词条 heat、已激活的跨流派共鸣。
     *
     * <p>这两类东西改变的是<b>整局规则</b>，不是临时 buff，所以用暗金色与紫色区分，
     * 并且放在状态行最左侧——玩家瞟一眼就知道"这一局我给自己加了什么、又凑出了什么"。
     * 最多显示 3 个共鸣名，其余折叠成 +N，避免短屏被挤爆。
     */
    private float drawRunModifiers(Canvas c, Ui ui, float bx, float by, Game g, Game.Player p,
                                   float panelRight) {
        int heat = g.pactHeat();
        if (heat > 0) {
            bx = chip(c, ui, bx, by, "契约 " + heat, Ui.GOLD);
        }
        if (p == null || p.crossMask == 0) return bx;
        int shown = 0;
        for (int i = 0; i < Content.CROSS_COUNT && shown < 3; i++) {
            if ((p.crossMask & (1 << i)) == 0) continue;
            if (bx > panelRight - 150) break;      // 剩余宽度不够就折叠
            bx = chip(c, ui, bx, by, Content.CROSS_NAMES[i], Ui.PINK);
            shown++;
        }
        int total = Integer.bitCount(p.crossMask);
        if (shown < total && bx < panelRight - 70) {
            bx = chip(c, ui, bx, by, "+" + (total - shown), ui.alpha(Ui.PINK, 200));
        }
        return bx;
    }

    private float chip(Canvas c, Ui ui, float x, float y, String s, int color) {
        float w = Math.max(60f, ui.measure(s, 10.5f, true) + 20);
        ui.pill(c, x, y, w, s, color);
        return x + w + 8;
    }

    // ── 流派层数条：面板上方的 6 格常驻读数 ──────────────────────
    /**
     * 共鸣阈值，必须与 core 侧同值。
     * <p>{@code Game.recompute} 写死的是 {@code schoolCount(p, school) >= 3}；
     * 这里一旦漂移，HUD 就会在「还差一件」和「已共鸣」之间对玩家撒谎。
     */
    private static final int RESONANCE = 3;

    /**
     * 预建的层数文案 "0/3".."10/3"。
     *
     * <p>{@code Relics.SCHOOL} 每个流派恰好 10 件，所以层数上限就是 10。
     * 这些串在类加载时一次性建好，逐帧只做数组取值 —— 否则 6 个
     * {@code n + "/3"} 每秒要分配 360 个小对象（60fps），
     * 正好违反「绘制期零分配」的约定。
     */
    private static final String[] STACKS = {
            "0/3", "1/3", "2/3", "3/3", "4/3", "5/3", "6/3", "7/3", "8/3", "9/3", "10/3"};

    /**
     * 六流派层数条：玩家在局内唯一能看见「离共鸣还差几件」的地方。
     *
     * <p>此前 {@code n/3} 只出现在星图底栏、三选一、构筑页——战斗里必须开构筑页
     * 才知道进度，而联机下开构筑页不暂停。契约 heat 与跨流派共鸣已经在
     * {@link #drawRunModifiers} 露出，唯独缺这条最可操作的反馈。
     *
     * <p><b>为什么另起一条，不塞进面板</b>：面板高 96 已被三行占满
     * （标题基线 726 / 血条 736..758 / Buff 胶囊 764..792），任何新行都会压到血条或 Buff。
     * 所以贴在面板<b>上沿</b>单开一条 22px 信息带。
     *
     * <p><b>版面算术</b>（未镜像坐标；镜像时整条经 {@link #mrect} 搬到 788..1408，
     * 与同样镜像的 vitals 面板左右对齐）：
     * <pre>
     *   条带     x = 32 .. 652（与面板同宽 620），y = 676 .. 698（高 22）
     *   面板     x = 32 .. 652，y = 700 .. 796  → 条带下沿 698 与面板顶 700 留 2px 缝
     *   内边距   左右各 13 → 可用宽 620-26 = 594
     *   6 格     cell = 94，gap = 6 → 5×(94+6) + 94 = 500 + 94 = 594，正好用满 ✓
     *            右缘 = 32+13+594 = 639 = 652-13 ✓
     *   格内     icon 圆心 x0+7   （直径 13 → 0.5..13.5）
     *            名字 x0+16 @10.5 （2 个汉字 ≈ 21 → 16..37）
     *            层数 x0+40 @10.5 （"10/3" ≈ 21 → 40..61）
     *            3 段 x0+64..x0+92（3×8 + 2×2 间隙 = 28）
     *            跨流派菱形 x0+88、y=680.5（竖直 677.1..683.9，在第三段 684.5 之上 0.6px）
     *   摇杆     圆心 (322,590) R=96 → 下缘 686，与条带上沿 676 重叠 10px。
     *            摇杆是「按住才出现」的瞬时控件、外圈近乎全透明，
     *            且调用顺序保证它在条带<b>之后</b>绘制 —— 拇指下的控件永远在上层。
     * </pre>
     */
    private void drawSchoolStrip(Canvas c, Ui ui, Game g, Game.Player p) {
        final float x = mrect(32, 620), y = 676, w = 620, h = 22;
        // 背景比面板更暗：它是附加读数，不该和血条抢视觉权重
        ui.gradientRect(c, x, y, w, h, 11, 0xC80B141B, 0xC8101C23);
        ui.outline(c, x, y, w, h, 11, ui.alpha(Ui.EDGE, 150));

        final float cell = 94, gap = 6, x0 = x + 13, mid = y + h * .5f;
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            int n = Math.max(0, Math.min(STACKS.length - 1, g.schoolCount(p, s)));
            int color = Content.SCHOOL_COLOR[s];
            float cx = x0 + s * (cell + gap);
            boolean resonating = (p.synergyMask & (1 << s)) != 0;
            int links = crossLinks(p, s);

            // 共鸣已激活：整格垫一层流派色胶囊底 + 底部 1.7px 实线。
            // 这里**不用 ui.glow**：条带只有 22px 高，光晕会往战斗区漏；
            // 限定在格内的胶囊底 + 底线既克制，也让色弱玩家能靠「有线」读出状态。
            if (resonating) {
                ui.box(c, cx - 3, y + 1.6f, cell + 6, h - 3.2f, 8f, ui.alpha(color, 26));
                ui.box(c, cx, y + h - 3.8f, cell, 1.7f, .85f, ui.alpha(color, 210));
            }

            ui.schoolIcon(c, cx + 7, mid, 13, s);

            int nameColor = resonating ? Ui.WHITE
                    : n > 0 ? ui.alpha(color, 230) : ui.alpha(Ui.MUTED, 150);
            ui.text(c, Content.SCHOOL_NAMES[s], cx + 16, mid + 3.6f, 10.5f, nameColor, resonating);

            ui.text(c, STACKS[n], cx + 40, mid + 3.6f, 10.5f,
                    resonating ? Ui.MINT : n > 0 ? ui.alpha(Ui.WHITE, 215) : ui.alpha(Ui.MUTED, 165),
                    resonating);

            // 三段小格 = 3 件门槛；超过 3 件时三格全亮（共鸣只看「是否到 3」）
            float px = cx + 64;
            for (int k = 0; k < RESONANCE; k++) {
                boolean filled = k < n;
                ui.box(c, px + k * 10, mid - 2.5f, 8, 5, 2.5f,
                        filled ? ui.alpha(color, resonating ? 255 : 220) : 0x2Effffff);
            }

            // 跨流派标记：该流派参与的任意组合已激活时，右上角一颗粉色小菱形。
            // 组合是 2+2 门槛，标记只说明「这条流派已经在对的组合里」，不承担计数；
            // 同时参与两组以上时菱形略大、更亮（最多 3 组，见 Content.CROSS_A/B）。
            if (links > 0) {
                ui.diamond(c, cx + 88, y + 4.5f, links >= 2 ? 3.4f : 2.8f,
                        ui.alpha(Ui.PINK, links >= 2 ? 235 : 190));
            }
        }
    }

    /** 该流派参与了几组已激活的跨流派组合（0..3）。 */
    private static int crossLinks(Game.Player p, int school) {
        int count = Math.min(Content.CROSS_COUNT,
                Math.min(Content.CROSS_A.length, Content.CROSS_B.length));
        int n = 0;
        for (int k = 0; k < count; k++) {
            if ((p.crossMask & (1 << k)) == 0) continue;
            if (Content.CROSS_A[k] == school || Content.CROSS_B[k] == school) n++;
        }
        return n;
    }

    // ── 摇杆 / 瞄准 ─────────────────────────────────────────────
    private void drawJoystick(Canvas c, Ui ui, Game.Player p) {
        float mag = (float) Math.sqrt(p.dx * p.dx + p.dy * p.dy);
        if (mag < .02f && p.manual <= 0f) return;
        float cx = mx(322), cy = 590, R = 96, r = 44;
        ui.glow(c, cx, cy, R * 1.3f, 0x1FEDF1E7);
        ui.circle(c, cx, cy, R, 0x3314232C);
        ui.ring(c, cx, cy, R, ui.alpha(Ui.WHITE, 85), 2f);
        ui.ring(c, cx, cy, R * .62f, ui.alpha(Ui.WHITE, 30), 1.2f);
        float nx = p.dx, ny = p.dy;
        float n = (float) Math.sqrt(nx * nx + ny * ny);
        if (n > 1f) { nx /= n; ny /= n; }
        // 摇杆头方向不镜像：dx/dy 是世界空间里的移动方向（战斗区本身不镜像），
        // 摇杆只是一个「往哪走」的读数，跟着世界走才对得上角色实际移动。
        float hx = cx + nx * R * .58f, hy = cy + ny * R * .58f;
        ui.circle(c, hx, hy, r, ui.alpha(Ui.WHITE, 40));
        ui.ring(c, hx, hy, r, ui.alpha(Ui.WHITE, 185), 2.2f);
        ui.circle(c, hx, hy, r * .40f, ui.alpha(Ui.MINT, 110));
    }

    private void drawAim(Canvas c, Ui ui, Game.Player p) {
        float ax = p.ax, ay = p.ay;
        float n = (float) Math.sqrt(ax * ax + ay * ay);
        if (n < .05f) return;
        ax /= n;
        ay /= n;
        float cx = mx(1012), cy = 700, R = 34;
        // 方向向量同样不镜像：ax/ay 是世界空间的真实瞄准方向，战斗区没有翻转
        ui.circle(c, cx, cy, R + 8, 0x5514232C);
        ui.ring(c, cx, cy, R + 8, ui.alpha(Ui.EDGE, 200), 1.4f);
        ui.line(c, cx, cy, cx + ax * R, cy + ay * R, ui.alpha(Ui.WHITE, 205), 2.4f);
        ui.circle(c, cx + ax * R, cy + ay * R, 4.5f, Ui.WHITE);
        ui.centerSmall(c, "瞄准", cx, cy + R + 20, 9.5f, ui.alpha(Ui.MUTED, 175));
    }

    // ── 右侧常驻按钮 ────────────────────────────────────────────
    private void drawSideButtons(Canvas c, Ui ui, float time) {
        // 面板本身固定在 x=1300..1420（宽 120，高 88，与 GameView 的 1300,118 对齐）；
        // 左撇子时整块搬到左侧，矩形镜像后为 20..140，热区在 GameView 侧同样镜像。
        float sx = mrect(1300, 120);
        drawSide(c, ui, 9102, sx, 118, "菜单", "MENU",
                inside(sx, 118, 120, 88) || pressed(9102) || pressed(Ui.A_LEAVE), Ui.RED);
        drawSide(c, ui, 9101, sx, 214, "构筑", "BUILD",
                inside(sx, 214, 120, 88) || pressed(9101) || pressed(Ui.A_GUIDE), Ui.VIOLET);
    }

    private void drawSide(Canvas c, Ui ui, int id, float x, float y, String label, String en,
                          boolean hover, int accent) {
        // x 已是矩形左边界（由 mrect 算好），内部一律用矩形语义
        ui.hit(id, x, y, 120, 88);
        // 自绘面板不走 Ui.button，GameView.activate 里那次 ui.press(id) 的状态
        // 要在这里手动读出来，否则「菜单 / 构筑」按下去依然毫无反应。
        // 收缩量比 Ui.button 的 3% 更小（2%）：120x88 已经很大，3% 会显得整块在跳。
        float punch = ui.pressAmount(id);
        float ix = x + 60, iy = y + 44, scale = 1f - .02f * punch;
        int bgTop = hover ? 0xEE24343E : 0xD9101C23;
        ui.gradientRect(c, ix - 60 * scale, iy - 44 * scale, 120 * scale, 88 * scale, 14, bgTop, 0xD90B141B);
        ui.stroke(c, ix - 60 * scale, iy - 44 * scale, 120 * scale, 88 * scale, 14,
                ui.alpha(hover ? accent : Ui.EDGE, 220), 1f);
        if (hover) ui.box(c, x + 1, y + 1, 4, 86, 2, ui.alpha(accent, 150));
        ui.center(c, label, x + 60, y + 38, 17, Ui.WHITE, true);
        ui.centerSmall(c, en, x + 60, y + 62, 9, ui.alpha(Ui.MUTED, 190));
        if (punch > .01f) {
            // 白色压光只覆盖内圆角区，别把外描边也一起洗白
            ui.box(c, x + 1.5f, y + 1.5f, 117, 85, 12.5f, ui.alpha(Ui.WHITE, (int) (46 * punch)));
        }
    }

    private void drawNotice(Canvas c, Ui ui, Game g) {
        if (g.notice == null || g.notice.length() == 0) return;
        String s = g.notice;
        ui.center(c, s, 720, 130, ui.fit(s, 620, 13, 9.5f), ui.alpha(Ui.WHITE, 195), false);
    }

    // ── 工具 ────────────────────────────────────────────────────
    private static String clock(float s) {
        int t = Math.max(0, (int) s);
        int m = t / 60, sec = t % 60;
        return (m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private static String one(float v) {
        int t = (int) (Math.max(0f, v) * 10f);
        return (t / 10) + "." + (t % 10);
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
}
