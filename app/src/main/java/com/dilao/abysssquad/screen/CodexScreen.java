package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Achievements;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 图鉴：遗物 / 敌种 / 首领 / 成就 四个 tab。
 *
 * <p>遗物 60 件按 6 列 × 5 行分页（每页 30，共 2 页），点选展开右侧详情。
 * 未发现条目显示「???」并用压暗卡片承载，避免信息泄露又不空洞。</p>
 *
 * <p>动作码：tab 切换、分页、选中均为内部状态 → 返回 -1；返回 → {@link Ui#A_BACK}。</p>
 */
public final class CodexScreen {

    // ── 控件 id ──
    private static final int C_BACK = 1;
    private static final int C_TAB0 = 10;        // 10..13 四个 tab
    private static final int C_PREV = 20, C_NEXT = 21;
    private static final int C_ENTRY0 = 100;     // 100+index 当前页条目
    private static final int C_CLOSE = 200;

    // ── 状态 ──
    private int tab = 0;
    private int page = 0;
    private int sel = -1;
    /** 遗物每页 6 列 × 5 行。 */
    private static final int RELIC_COLS = 6, RELIC_ROWS = 5;

    public int hit = -1;

    // ── 成就数据 ──────────────────────────────────
    // 直接引用 Achievements 的公开表（此前这里镜像复制了一份，因为 Achievements 是包私有；
    // 两处各写一份 24 项名称/描述/品质/目标值，改一边就漂移，图鉴会与结算文案脱节）。
    private static final int ACH_COUNT = Achievements.COUNT;

    public CodexScreen() {
    }

    /**
     * @param relicSeen   60
     * @param enemySeen   20
     * @param bossSeen    8
     * @param bossKills   8
     * @param achievements 24 项成就的进度值（0..Achievements.TARGET[i]，不是 0/1）；可为 null 时走占位
     */
    public void draw(Canvas c, Ui ui, float time, boolean[] relicSeen, boolean[] enemySeen,
                     boolean[] bossSeen, int[] bossKills, int[] achievements) {
        ui.clearHits();
        hit = -1;
        if (c == null || ui == null) return;

        int slots = tabSlots();
        if (page > Math.max(0, pageCount() - 1)) page = Math.max(0, pageCount() - 1);
        if (sel >= slots) sel = -1;

        backdrop(c, ui, time);
        header(c, ui, time);
        tabs(c, ui, time);

        switch (tab) {
            case 0:
                relicTab(c, ui, time, relicSeen);
                break;
            case 1:
                enemyTab(c, ui, time, enemySeen);
                break;
            case 2:
                bossTab(c, ui, time, bossSeen, bossKills);
                break;
            default:
                achTab(c, ui, time, achievements);
                break;
        }
        footer(c, ui, relicSeen, enemySeen, bossSeen, achievements);
    }

    // ─────────────────────────── 背景 / 顶栏 ───────────────────────────
    private void backdrop(Canvas c, Ui ui, float time) {
        ui.glow(c, 720, 200, 560, 0x0c9de7c1);
        ui.glow(c, 240, 700, 380, 0x0a93c4ff);
        for (int i = 0; i < 22; i++) {
            float x = (i * 191 + 40) % 1400;
            float y = 120 + ((i * 113 + time * 6f) % 650);
            ui.circle(c, x, y, .8f + (i % 3) * .4f, ui.alpha(Ui.MINT, 18 + (i % 4) * 10));
        }
    }

    private void header(Canvas c, Ui ui, float time) {
        ui.diamond(c, 78, 44, 13, Ui.GOLD);
        ui.text(c, "深渊图鉴", 102, 53, 27, Ui.WHITE, true);
        ui.text(c, "ARCHIVE", 240, 52, 12, ui.alpha(Ui.MUTED, 190), false);
        ui.ghost(c, C_BACK, 1252, 24, 120, 46, "返回  ←");
        ui.divider(c, 64, 86, 1312);
    }

    private void tabs(Canvas c, Ui ui, float time) {
        float x = 64, y = 104, w = 726, h = 58;
        int n = 4;
        int[] ids = {C_TAB0, C_TAB0 + 1, C_TAB0 + 2, C_TAB0 + 3};
        String[] labels = {"遗物", "敌种", "首领", "成就"};
        ui.segmented(c, x, y, w, h, n, tab, ids, labels);

        // 当前 tab 统计
        String stat = tabStat();
        ui.right(c, stat, 1376, y + h / 2f + 6, 15, ui.alpha(Ui.MUTED, 210), false);
    }

    // ─────────────────────────── 遗物 ───────────────────────────
    private void relicTab(Canvas c, Ui ui, float time, boolean[] seen) {
        // gy 下移到 204，给顶部「已收集 + 6 流派进度」这一行让出空间（y=186..212）
        final float gx = 64, gy = 204, cw = 148, ch = 88, gapX = 10, gapY = 9;
        int total = Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length);
        int per = RELIC_COLS * RELIC_ROWS;
        int start = page * per;

        for (int k = 0; k < per; k++) {
            int i = start + k;
            if (i >= total) break;
            int col = k % RELIC_COLS, row = k / RELIC_COLS;
            float x = gx + col * (cw + gapX), y = gy + row * (ch + gapY);
            boolean got = safeFlag(seen, i), on = sel == i;
            int school = safeInt(Relics.SCHOOL, i, 0) % 6;
            int rarity = safeInt(Relics.RARITY, i, 0);
            int sc = safeInt(Content.SCHOOL_COLOR, school, Ui.SCHOOL_COLOR[school]);
            int rc = ui.rarityColor(rarity);

            if (got) {
                // ① 稀有度边框（粗细/亮度递进，制造"收藏感"）
                float bw = 1f + rarity * .55f;                 // 普通1 / 稀有1.55 / 史诗2.1 / 传说2.65
                int borderColor = on ? Ui.MINT : ui.alpha(rc, 130 + rarity * 40);
                ui.card(c, x, y, cw, ch, borderColor, on, false);
                ui.stroke(c, x, y, cw, ch, 14, borderColor, bw);

                // ② 左侧 3px 流派色竖条
                int barColor = on ? Ui.MINT : sc;
                ui.box(c, x + 1.5f, y + 10, 3f, ch - 20, 1.5f, ui.alpha(barColor, 235));
                ui.glow(c, x + 3, y + ch * .5f, 16, ui.alpha(barColor, 46));

                // ③ 图标 + 名称
                ui.relic(c, x + 38, y + ch / 2f, 46, safeInt(Relics.ICON, i, 0), sc, rc);
                ui.text(c, safeStr(Relics.NAMES, i, "遗物"), x + 66, y + 38, 12.5f,
                        on ? Ui.MINT : Ui.WHITE, true);
                ui.text(c, safeStr(Content.SCHOOL_NAMES, school, ""), x + 66, y + 56, 11,
                        ui.alpha(sc, 235), false);

                // ④ 稀有度标签（右上）
                String rar = safeStr(Content.RARITY_NAMES, rarity, "");
                ui.right(c, rar, x + cw - 10, y + 20, 11, ui.alpha(rc, 245), true);

                // ⑤ 流派内序号「3/10」+ 上限（底部）
                int order = relicOrderInSchool(i, school) + 1;
                int cntInSchool = schoolTotal(school);
                ui.text(c, order + "/" + cntInSchool, x + 12, y + ch - 12, 11,
                        ui.alpha(rc, 225), true);
                ui.right(c, "上限 " + safeInt(Relics.CAP, i, 1), x + cw - 10, y + ch - 12, 11,
                        ui.alpha(Ui.MUTED, 210), false);
            } else {
                // 未解锁：压暗 + 锁标，一眼可辨
                ui.card(c, x, y, cw, ch, Ui.EDGE, on, false);
                ui.box(c, x + 1.5f, y + 10, 3f, ch - 20, 1.5f, ui.alpha(Ui.EDGE, 170));
                ui.locked(c, x + 8, y + 8, cw - 16, ch - 16, "");
                // 锁图标（用 icon() 的骷髅不合适，这里画一个锁）
                drawLock(c, ui, x + cw * .5f, y + 40, 19f, ui.alpha(Ui.MUTED, 175));
                ui.center(c, "???", x + cw / 2f, y + 62, 12.5f, ui.alpha(Ui.MUTED, 200), true);
                ui.center(c, "未发现", x + cw / 2f, y + ch - 13, 11, ui.alpha(Ui.MUTED, 175), false);
            }
            ui.hit(C_ENTRY0 + k, x, y, cw, ch);
        }

        // 顶部统计：总收集 + 6 个流派进度条
        schoolProgress(c, ui, seen, gx, gy, per);

        // 右侧详情面板
        detailPanel(c, ui, time, seen);
        pager(c, ui, total, per);
    }

    /** 该遗物在同流派 10 件里的序号（0 基）。 */
    private static int relicOrderInSchool(int relicId, int school) {
        int n = 0;
        int total = Math.min(Relics.COUNT, Relics.SCHOOL == null ? 0 : Relics.SCHOOL.length);
        for (int i = 0; i < relicId && i < total; i++) {
            if (Math.floorMod(safeInt(Relics.SCHOOL, i, -1), 6) == school) n++;
        }
        return n;
    }

    /** 某流派的遗物总数（契约要求每流派恰好 10 件，这里动态统计以容错）。 */
    private static int schoolTotal(int school) {
        int n = 0, total = Math.min(Relics.COUNT, Relics.SCHOOL == null ? 0 : Relics.SCHOOL.length);
        for (int i = 0; i < total; i++) {
            if (Math.floorMod(safeInt(Relics.SCHOOL, i, -1), 6) == school) n++;
        }
        return Math.max(1, n);
    }

    /** 画一把锁（描边风格，与 16 种 icon 区分开）。 */
    private static void drawLock(Canvas c, Ui ui, float x, float y, float size, int color) {
        float s = size * .5f;
        // 锁体
        ui.box(c, x - s * .78f, y - s * .05f, s * 1.56f, s * 1.15f, s * .22f, color);
        // 锁梁
        ui.ring(c, x, y - s * .38f, s * .46f, color, Math.max(1.5f, s * .22f));
        // 锁孔
        ui.circle(c, x, y + s * .34f, s * .16f, 0xcc0b141b);
    }

    /** 顶部统计条：已收集 X/60 + 6 个流派进度。占满卡片区上方一行（y=182..208）。 */
    private void schoolProgress(Canvas c, Ui ui, boolean[] seen, float gx, float gy, int per) {
        final float y = 182;
        int total = Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length);
        int got = count(seen, total);

        ui.text(c, "已收集 " + got + " / " + total, gx, y + 19, 13, Ui.MINT, true);

        // 6 个流派小进度条，横排在右侧
        float px = gx + 132;
        for (int s = 0; s < 6; s++) {
            int tot = schoolTotal(s);
            int have = 0;
            for (int i = 0; i < total; i++) {
                if (Math.floorMod(safeInt(Relics.SCHOOL, i, -1), 6) == s && safeFlag(seen, i)) have++;
            }
            int sc = safeInt(Content.SCHOOL_COLOR, s, Ui.SCHOOL_COLOR[s]);

            ui.circle(c, px + 4, y + 14, 3, ui.alpha(sc, 235));
            ui.text(c, safeStr(Content.SCHOOL_NAMES, s, ""), px + 12, y + 19, 11, ui.alpha(sc, 235), false);
            ui.bar(c, px + 40, y + 8, 40, 8, have / (float) Math.max(1, tot), sc);
            ui.text(c, have + "/" + tot, px + 86, y + 19, 11, ui.alpha(Ui.MUTED, 215), false);

            px += 108f;
        }
    }

    private void detailPanel(Canvas c, Ui ui, float time, boolean[] seen) {
        final float x = 1052, y = 186, w = 324, h = 522;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);

        int total = Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length);
        if (sel < 0 || sel >= total) {
            ui.center(c, "选择一件遗物", x + w / 2f, y + h / 2f - 10, 17, Ui.MUTED, true);
            ui.center(c, "查看描述 · 风味 · 上限 · 价格", x + w / 2f, y + h / 2f + 18, 12,
                    ui.alpha(Ui.MUTED, 160), false);
            return;
        }

        int school = safeInt(Relics.SCHOOL, sel, 0) % 6;
        int rarity = safeInt(Relics.RARITY, sel, 0);
        int sc = safeInt(Content.SCHOOL_COLOR, school, Ui.SCHOOL_COLOR[school]);
        int rc = ui.rarityColor(rarity);
        boolean got = safeFlag(seen, sel);

        // 顶图（按稀有度加一层色晕，传说最亮）
        ui.gradientRect(c, x + 1, y + 1, w - 2, 132, 17, ui.blend(0xff16262e, sc, .16f), 0xff101d24);
        ui.glow(c, x + w / 2f, y + 74, 62 + rarity * 8, ui.alpha(rc, 46 + rarity * 14));
        if (got) {
            ui.relic(c, x + w / 2f, y + 68, 76, safeInt(Relics.ICON, sel, 0), sc, rc);
        } else {
            drawLock(c, ui, x + w / 2f, y + 68, 44, ui.alpha(Ui.MUTED, 165));
        }

        float ty = y + 152;
        ui.center(c, got ? safeStr(Relics.NAMES, sel, "") : "???", x + w / 2f, ty, 21,
                got ? Ui.WHITE : ui.alpha(Ui.MUTED, 200), true);
        ty += 22;
        // 稀有度 + 流派（稀有度用其专属色，视觉上一档一色）
        String tag = safeStr(Content.RARITY_NAMES, rarity, "普通") + "  ·  "
                + safeStr(Content.SCHOOL_NAMES, school, "");
        ui.center(c, tag, x + w / 2f, ty, 12.5f, got ? rc : ui.alpha(Ui.MUTED, 180), true);
        ty += 24;
        // 「所属流派 · 第 N / 10 件」
        if (got) {
            int order = relicOrderInSchool(sel, school) + 1;
            ui.center(c, safeStr(Content.SCHOOL_NAMES, school, "") + "  ·  第 "
                            + order + " / " + schoolTotal(school) + " 件",
                    x + w / 2f, ty, 11.5f, ui.alpha(sc, 235), false);
        }
        ty += 22;

        ui.divider(c, x + 20, ty, w - 40);
        ty += 26;

        if (!got) {
            // 未发现：不泄露任何数值，只给获取提示
            ui.center(c, "尚未发现这件遗物", x + w / 2f, ty + 34, 14, ui.alpha(Ui.MUTED, 205), true);
            ui.wrap(c, "在远征中拾取、商店购买或事件奖励后会解锁图鉴记录。",
                    x + 30, ty + 66, w - 60, 12, ui.alpha(Ui.MUTED, 185));
        } else {
            // 描述
            ui.small(c, "效果", x + 22, ty, 11f, ui.alpha(Ui.GOLD, 210));
            String desc = safeStr(Relics.DESC, sel, "");
            ui.wrap(c, desc, x + 22, ty + 20, w - 44, 13.5f, Ui.WHITE);
            ty += 24 + ui.wrapHeight(desc, w - 44, 13.5f) + 14;

            // 风味
            String flavor = safeStr(Relics.FLAVOR, sel, "");
            if (!flavor.isEmpty()) {
                ui.wrap(c, "「" + flavor + "」", x + 22, ty, w - 44, 12, ui.alpha(Ui.MUTED, 195));
                ty += ui.wrapHeight("「" + flavor + "」", w - 44, 12) + 12;
            }
        }

        // 数据行（未发现时不显示真实数值）
        float by = y + h - 92;
        ui.divider(c, x + 20, by - 18, w - 40);
        if (got) {
            dataRow(c, ui, x + 22, by, "上限", safeInt(Relics.CAP, sel, 1) + " 层", sc);
            dataRow(c, ui, x + 22 + (w - 44) / 2f, by, "价格", safeInt(Relics.COST, sel, 0) + " 星尘", Ui.GOLD);
            dataRow(c, ui, x + 22, by + 34, "收集", String.format(java.util.Locale.US, "%02d / %02d", sel + 1, total),
                    ui.alpha(Ui.MUTED, 220));
        } else {
            dataRow(c, ui, x + 22, by, "上限", "—", ui.alpha(Ui.MUTED, 200));
            dataRow(c, ui, x + 22 + (w - 44) / 2f, by, "价格", "—", ui.alpha(Ui.MUTED, 200));
            dataRow(c, ui, x + 22, by + 34, "收集", String.format(java.util.Locale.US, "%02d / %02d", sel + 1, total),
                    ui.alpha(Ui.MUTED, 200));
        }
    }

    private void dataRow(Canvas c, Ui ui, float x, float y, String k, String v, int color) {
        ui.text(c, k, x, y, 11.5f, ui.alpha(Ui.MUTED, 190), false);
        ui.text(c, v, x, y + 18, 14, color, true);
    }

    private void pager(Canvas c, Ui ui, int total, int per) {
        int pages = Math.max(1, (total + per - 1) / per);
        // 底栏左段：页码小面板 64..296；翻页按钮紧随其后 306..526。
        // 右段 footer() 从 540 起，两者不重叠。
        float x = 64, y = 728, w = 232, h = 54;
        ui.panel(c, x, y, w, h, 14, 0xff101d24, Ui.EDGE);
        ui.text(c, "第 " + (page + 1) + " / " + pages + " 页", x + 20, y + 33, 14,
                ui.alpha(Ui.WHITE, 220), true);

        if (pages > 1) {
            boolean canPrev = page > 0, canNext = page < pages - 1;
            ui.button(c, C_PREV, x + w + 10, y + 6, 108, h - 12, "← 上一页", false, canPrev);
            ui.button(c, C_NEXT, x + w + 126, y + 6, 104, h - 12, "下一页 →", true, canNext);
        }
    }

    // ─────────────────────────── 敌种 ───────────────────────────
    private void enemyTab(Canvas c, Ui ui, float time, boolean[] seen) {
        // 几何重算（旧值 ch=150 / gapY=12 会越界）：
        //   底栏 footer() 固定在 y=728，卡片区底部安全线取 716（留 12px 呼吸位）。
        //   可用高度 = 716 - gy(186) = 530。4 行卡片的纵向占位 = 4*ch + 3*gapY。
        //   ch=124, gapY=9 → 4*124 + 3*9 = 496 + 27 = 523 ≤ 530 ✓
        //   末行底边 = 186 + 3*(124+9) + 124 = 186 + 399 + 124 = 709 ≤ 716 ✓
        //   （旧值 186 + 3*(150+12) + 150 = 822 > 810，直接画到屏幕外并压住底栏）
        final float gx = 64, gy = 186, cw = 306, ch = 124, gapX = 12, gapY = 9;
        int total = Math.min(Enemies.COUNT, Enemies.NAMES == null ? 0 : Enemies.NAMES.length);
        int per = 12, start = page * per;

        for (int k = 0; k < per; k++) {
            int i = start + k;
            if (i >= total) break;
            int col = k % 3, row = k / 3;
            if (row >= 4) break;
            float x = gx + col * (cw + gapX), y = gy + row * (ch + gapY);
            boolean got = safeFlag(seen, i), on = sel == i;
            int accent = on ? Ui.MINT : (got ? Ui.PINK : Ui.EDGE);

            ui.card(c, x, y, cw, ch, accent, on, false);
            int art = safeInt(Enemies.ART, i, i);
            if (got) {
                // 立绘锚点是脚底：feet=y+86、size=84 → 上沿 y+36、下沿 y+120，均在 ch=124 内
                ui.glow(c, x + 58, y + 68, 44, ui.alpha(Ui.PINK, 26));
                ui.avatar(c, x + 58, y + 86, 84, 12 + art, time * .5f + i, true);
            } else {
                ui.circle(c, x + 58, y + 64, 28, 0x33000000);
                ui.center(c, "?", x + 58, y + 64, 24, ui.alpha(Ui.MUTED, 175), true);
            }
            ui.text(c, got ? safeStr(Enemies.NAMES, i, "") : "???", x + 112, y + 32, 16,
                    got ? Ui.WHITE : ui.alpha(Ui.MUTED, 160), true);
            String behave = got ? behaviorName(safeInt(Enemies.BEHAVIOR, i, 0)) : "未遭遇";
            ui.pill(c, x + 112, y + 40, 88, behave, got ? Ui.PINK : Ui.EDGE);
            // 描述区：x+112 起、宽 186（现有最长 25 字 → 2 行）。
            //   行为胶囊底边 y+68，首行基线取 y+84（留 16px 视觉间隙）；
            //   2 行 10.5 号占 2*10.5*1.56 ≈ 32.8px，文字底沿 ≈ y+84+16.4+10.5 ≈ y+111 < ch=124。
            //   可用高按 (y+ch-6)-(y+84) = 34px 判定，放不下再缩字号（下限 10）：
            //   即使未来描述长到 3 行，10 号字 3 行底沿 ≈ y+118 仍在卡内。
            //   （旧实现从 y+104 起画 3 行 ≈ 54px，在 ch=150 的卡里也早已溢出到卡外。）
            String d = got ? safeStr(Enemies.DESC, i, "") : "在远征中遭遇后解锁详细描述。";
            float dw = cw - 120, dTop = y + 84, dh = (y + ch - 6) - dTop;
            float ds = 10.5f;
            float need = ui.wrapHeight(d, dw, ds);
            if (need > dh) ds = Math.max(10f, ds * dh / need);
            ui.wrap(c, d, x + 112, dTop, dw, ds, ui.alpha(Ui.MUTED, 210));
            ui.hit(C_ENTRY0 + k, x, y, cw, ch);
        }
        pager(c, ui, total, per);
    }

    // ─────────────────────────── 首领 ───────────────────────────
    private void bossTab(Canvas c, Ui ui, float time, boolean[] seen, int[] kills) {
        final float gx = 64, gy = 186, cw = 646, ch = 120, gapX = 20, gapY = 12;
        int total = Math.min(Bosses.COUNT, Bosses.NAMES == null ? 0 : Bosses.NAMES.length);
        int per = 8, start = page * per;

        for (int k = 0; k < per; k++) {
            int i = start + k;
            if (i >= total) break;
            int col = k % 2, row = k / 2;
            if (row >= 4) break;
            float x = gx + col * (cw + gapX), y = gy + row * (ch + gapY);
            boolean got = safeFlag(seen, i), on = sel == i;
            int kc = safeInt(kills, i, 0);
            int accent = on ? Ui.MINT : (got ? Ui.RED : Ui.EDGE);

            ui.card(c, x, y, cw, ch, accent, on, false);
            int art = safeInt(Bosses.ART, i, 32 + i);
            if (got) {
                ui.glow(c, x + 66, y + 66, 46, ui.alpha(Ui.RED, 34));
                ui.avatar(c, x + 66, y + 92, 104, art, time * .45f + i, true);
            } else {
                ui.circle(c, x + 66, y + 60, 30, 0x33000000);
                ui.center(c, "?", x + 66, y + 60, 26, ui.alpha(Ui.MUTED, 175), true);
            }
            int biome = safeInt(Bosses.BIOME, i, 0) % Math.max(1, Content.BIOME_NAMES.length);
            ui.text(c, got ? safeStr(Bosses.NAMES, i, "") : "???", x + 126, y + 40, 19,
                    got ? Ui.WHITE : ui.alpha(Ui.MUTED, 160), true);
            ui.text(c, got ? "「" + safeStr(Bosses.TITLE, i, "") + "」" : "未遭遇",
                    x + 126, y + 62, 12.5f, got ? ui.alpha(Ui.RED, 210) : ui.alpha(Ui.MUTED, 140), false);
            ui.wrap(c, got ? safeStr(Bosses.DESC, i, "") : "击败后解锁机制说明。",
                    x + 126, y + 84, cw - 158, 11.5f, ui.alpha(Ui.MUTED, 205));

            // 击杀数 + 生态
            ui.pill(c, x + cw - 118, y + 12, 104, safeStr(Content.BIOME_NAMES, biome, ""), Ui.BLUE);
            ui.right(c, "击破 " + kc, x + cw - 14, y + ch - 12, 12,
                    kc > 0 ? Ui.GOLD : ui.alpha(Ui.MUTED, 150), true);
            ui.hit(C_ENTRY0 + k, x, y, cw, ch);
        }
        pager(c, ui, total, per);
    }

    // ─────────────────────────── 成就 ───────────────────────────
    private void achTab(Canvas c, Ui ui, float time, int[] achievements) {
        // 几何重算：底栏 footer()/pager() 固定在 y=728，卡片区底线取 716。
        //   可用高度 = 716 - 186 = 530；
        //   ch=124, gapY=9 → 4*124 + 3*9 = 496 + 27 = 523 ≤ 530 ✓
        //   末行底边 = 186 + 3*(124+9) + 124 = 186 + 399 + 124 = 709 ≤ 716 ✓
        //   （旧值 ch=118/gapY=12 虽未越界，但塞不下「名称 + 品质 + 进度条 + 描述」四段；
        //    这里统一成与敌种 tab 相同的 124/9，两个网格末行都落在 709，视觉一致）
        final float gx = 64, gy = 186, cw = 306, ch = 124, gapX = 12, gapY = 9;
        int total = achievements == null ? 0 : Math.min(ACH_COUNT, achievements.length);
        int per = 12, start = page * per;

        if (total == 0) {
            // 兜底：GameView 没传进度数组时只给说明，不画空白卡片
            ui.panel(c, gx, gy, 1292, 240, 18, 0xff101d24, Ui.EDGE);
            ui.center(c, "成就系统接入中", gx + 646, gy + 96, 22, Ui.WHITE, true);
            ui.center(c, "完成远征、击破首领与收集遗物将逐步解锁成就。",
                    gx + 646, gy + 132, 14, Ui.MUTED, false);
            pager(c, ui, 0, per);
            return;
        }

        for (int k = 0; k < per; k++) {
            int i = start + k;
            if (i >= total) break;
            int col = k % 3, row = k / 3;
            if (row >= 4) break;
            float x = gx + col * (cw + gapX), y = gy + row * (ch + gapY);

            // 完成判定用真实目标值：Meta.refreshAchievementsLocked() 把进度钳制在
            // 0..Achievements.TARGET[i]，所以 v==target 即完成；进度条分母同样是 target。
            int target = Math.max(1, safeInt(Achievements.TARGET, i, 1));
            int v = Math.max(0, safeInt(achievements, i, 0));
            boolean done = v >= target;
            float k01 = Math.min(1f, v / (float) target);
            boolean on = sel == i;

            int t = Math.max(0, Math.min(Achievements.TIER_NAMES.length - 1, safeInt(Achievements.TIER, i, 0)));
            int tierCol = Achievements.TIER_COLOR[Math.max(0, Math.min(Achievements.TIER_COLOR.length - 1, t))];
            String tierName = Achievements.TIER_NAMES[t];
            int accent = on ? Ui.MINT : (done ? tierCol : Ui.EDGE);
            ui.card(c, x, y, cw, ch, accent, on, false);

            // ── 版面（自上而下四段，互不重叠）──
            //   ① 头部 y+8..y+38：图标 + 名称 + 右上品质胶囊
            //   ② 进度条 y+48..y+55（整宽，不钻到胶囊底下）
            //   ③ 进度文案 基线 y+70
            //   ④ 分隔线 y+80 + 描述块 y+86..y+116
            // 旧实现把进度条放在 y+52 且从 x+64 起画到 x+286，会横穿右上角控件；
            // 这里改成整行独立，胶囊只占头部。

            // ① 头部：图标 + 名称（自适应）+ 品质胶囊
            ui.icon(c, x + 30, y + 28, 32, done ? 6 : 12, done ? tierCol : ui.alpha(Ui.MUTED, 120));
            String name = safeStr(Achievements.NAMES, i, "成就");
            // 胶囊占右侧 62 宽 + 8 边距，名称可用宽度 = cw - 52 - 80 = 174
            float nameMax = cw - 132;
            float nameSize = ui.fit(name, nameMax, 15f, 11f);
            ui.text(c, name, x + 52, y + 34, nameSize,
                    done ? Ui.WHITE : ui.alpha(Ui.MUTED, 210), true);
            ui.pill(c, x + cw - 70, y + 8, 62, tierName, tierCol);

            // ② 进度条（目标值口径：k01 = min(1, v/target)）
            ui.bar(c, x + 14, y + 48, cw - 28, 7, k01, done ? tierCol : ui.alpha(Ui.MINT, 150));
            // ③ 进度文案
            String ptxt = done ? "已完成"
                    : (target <= 1 ? "未完成" : v + " / " + target);
            ui.small(c, ptxt, x + 14, y + 70, 10.5f,
                    done ? tierCol : ui.alpha(Ui.MUTED, 205));

            // ③b 具名奖励（右侧，与进度文案同基线）。
            //     成就从纯计数器变成"解锁有名字的东西"之后，玩家必须能在图鉴里
            //     直接看到自己拿到了什么；只有名字没有效果说明也不行，
            //     所以描述行里追加「奖励：...」。
            String rn = Achievements.rewardName(i);
            if (!rn.isEmpty()) {
                ui.right(c, "◈ " + rn, x + cw - 14, y + 70, 10.5f,
                        done ? Ui.GOLD : ui.alpha(Ui.MUTED, 150), true);
            }

            // ── 描述：x+14 起、宽 cw-28=278（现有最长 14 字 ≈ 161px → 单行）。
            //    文本块顶 y+86，可用高 = (y+124-10)-(y+86) = 28px：单行 11.5 号需 17.9 → 不缩字；
            //    若将来描述变长需两行（35.9 > 28），按比例缩到下限 9.5，两行 ≈ 29.6px，
            //    底沿 ≈ y+116 仍 < 卡片底 y+124。旧实现根本不画描述。
            ui.divider(c, x + 14, y + 80, cw - 28);
            String d = safeStr(Achievements.DESC, i, "");
            // 有奖励的成就在描述后追加效果，让"解锁了什么"一眼可见
            if (!rn.isEmpty()) {
                String rd = Achievements.rewardDesc(i);
                if (rd != null && rd.length() > 0) d = d + " · 奖励：" + rd;
            }
            float dw = cw - 28, blockTop = y + 86, dh = (y + ch - 10) - blockTop;
            float ds = 11.5f;
            float need = ui.wrapHeight(d, dw, ds);
            if (need > dh) ds = Math.max(9.5f, ds * dh / need);
            // wrap() 的 y 是首行基线；用 ascent 近似把文本块顶对齐到 blockTop
            ui.wrap(c, d, x + 14, blockTop + ds * .93f, dw, ds,
                    done ? ui.alpha(Ui.MUTED, 225) : ui.alpha(Ui.MUTED, 195));

            ui.hit(C_ENTRY0 + k, x, y, cw, ch);
        }
        pager(c, ui, total, per);
    }

    // ─────────────────────────── 底部完成度 ───────────────────────────
    private void footer(Canvas c, Ui ui, boolean[] relicSeen, boolean[] enemySeen,
                        boolean[] bossSeen, int[] achievements) {
        // 底栏被拆成两段：左侧 232px 归 pager()（页码 + 翻页按钮），
        // 本方法只画右侧 x=316 起的统计段，两者 y/h 一致，视觉上是一条底栏。
        // 旧实现两者都从 x=64 起画 1312 宽面板 → 完全重叠、文字互相压叠。
        float x = 540, y = 728, w = 836;
        int rTotal = Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length);
        int eTotal = Math.min(Enemies.COUNT, Enemies.NAMES == null ? 0 : Enemies.NAMES.length);
        int bTotal = Math.min(Bosses.COUNT, Bosses.NAMES == null ? 0 : Bosses.NAMES.length);
        int rGot = count(relicSeen, rTotal), eGot = count(enemySeen, eTotal), bGot = count(bossSeen, bTotal);

        ui.panel(c, x, y, w, 54, 14, 0xff101d24, Ui.EDGE);
        float cy = y + 28;
        String[] labels = {
                "遗物 " + rGot + " / " + rTotal,
                "敌种 " + eGot + " / " + eTotal,
                "首领 " + bGot + " / " + bTotal};
        int[] colors = {Ui.MINT, Ui.PINK, Ui.RED};
        // 统计项按实际文字宽度顺排（不要写死 x 偏移，否则中文变宽就压到一起）
        float cx = x + 22;
        for (int i = 0; i < labels.length; i++) {
            ui.circle(c, cx + 4, cy, 3, ui.alpha(colors[i], 220));
            ui.text(c, labels[i], cx + 14, cy + 6, 13.5f, colors[i], true);
            cx += 14 + ui.measure(labels[i], 13.5f, true) + 26;
        }

        // 总完成度（右端）
        int allGot = rGot + eGot + bGot, allTotal = Math.max(1, rTotal + eTotal + bTotal);
        float pct = allGot / (float) allTotal;
        ui.right(c, "总完成度", x + w - 246, cy + 6, 12.5f, ui.alpha(Ui.MUTED, 205), false);
        ui.bar(c, x + w - 176, cy - 5, 120, 11, pct, Ui.GOLD);
        ui.right(c, (int) (pct * 100) + "%", x + w - 16, cy + 6, 14, Ui.GOLD, true);
    }

    // ─────────────────────────── 点击 ───────────────────────────
    public int click(int id) {
        hit = id;
        if (id == C_BACK) return Ui.A_BACK;
        if (id >= C_TAB0 && id < C_TAB0 + 4) {
            tab = id - C_TAB0;
            page = 0;
            sel = -1;
            return -1;
        }
        if (id == C_PREV) {
            if (page > 0) page--;
            sel = -1;
            return -1;
        }
        if (id == C_NEXT) {
            if (page < pageCount() - 1) page++;
            sel = -1;
            return -1;
        }
        if (id >= C_ENTRY0) {
            int k = id - C_ENTRY0;
            int per = pageSize();
            int idx = page * per + k;
            sel = (sel == idx) ? -1 : idx;
            return -1;
        }
        return -1;
    }

    // ─────────────────────────── 工具 ───────────────────────────
    private int tabSlots() {
        switch (tab) {
            case 0: return Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length);
            case 1: return Math.min(Enemies.COUNT, Enemies.NAMES == null ? 0 : Enemies.NAMES.length);
            case 2: return Math.min(Bosses.COUNT, Bosses.NAMES == null ? 0 : Bosses.NAMES.length);
            default: return ACH_COUNT;
        }
    }

    private int pageSize() {
        switch (tab) {
            case 0: return RELIC_COLS * RELIC_ROWS;
            case 1: return 12;
            case 2: return 8;
            default: return 12;
        }
    }

    private int pageCount() {
        int t = tabSlots(), p = pageSize();
        if (tab == 3 && t <= 0) return 1;
        return Math.max(1, (t + p - 1) / p);
    }

    private String tabStat() {
        switch (tab) {
            case 0: return "共 " + Math.min(Relics.COUNT, Relics.NAMES == null ? 0 : Relics.NAMES.length) + " 件遗物  /  6 流派";
            case 1: return "共 " + Math.min(Enemies.COUNT, Enemies.NAMES == null ? 0 : Enemies.NAMES.length) + " 种敌种";
            case 2: return "共 " + Math.min(Bosses.COUNT, Bosses.NAMES == null ? 0 : Bosses.NAMES.length) + " 位首领";
            default: return "共 " + ACH_COUNT + " 项成就";
        }
    }

    private static String behaviorName(int b) {
        switch (Math.floorMod(b, 16)) {
            case 0: return "追击";
            case 1: return "射手";
            case 2: return "冲锋";
            case 3: return "自爆";
            case 4: return "分裂";
            case 5: return "召唤";
            case 6: return "区域";
            case 7: return "狙击";
            case 8: return "坦克";
            case 9: return "虫群";
            case 10: return "闪现";
            case 11: return "治疗";
            case 12: return "持盾";
            case 13: return "螺旋";
            case 14: return "炮台";
            default: return "飞行";
        }
    }

    private static boolean safeFlag(boolean[] a, int i) {
        return a != null && i >= 0 && i < a.length && a[i];
    }

    private static int safeInt(int[] a, int i, int fb) {
        return (a == null || i < 0 || i >= a.length) ? fb : a[i];
    }

    private static String safeStr(String[] a, int i, String fb) {
        if (a == null || i < 0 || i >= a.length) return fb;
        String s = a[i];
        return s == null ? fb : s;
    }

    private static int count(boolean[] a, int n) {
        if (a == null) return 0;
        int k = 0;
        for (int i = 0; i < Math.min(n, a.length); i++) if (a[i]) k++;
        return k;
    }
}
