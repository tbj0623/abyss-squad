package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 远征准备：模式 → 职业 → 契约 → 确认出发。
 *
 * <p>布局：顶部模式 tab；左侧 4×3 职业网格；右侧详情面板；底部 5 档契约。
 * 状态全部自持，通过 {@link #selectedMode()} 等 getter 暴露给 GameView。</p>
 *
 * <p>动作码：模式/职业/契约切换返回 -1（内部状态）；确认出发返回 {@link Ui#A_START}；
 * 返回返回 {@link Ui#A_BACK}。</p>
 */
public final class CharScreen {

    // ── 控件 id（本屏内唯一）──
    private static final int C_BACK = 1;
    private static final int C_MODE0 = 10;      // 10..13 模式
    private static final int C_ROLE0 = 20;      // 20..31 职业
    private static final int C_PACT0 = 40;      // 40..44 契约
    private static final int C_GO = 60;
    private static final int C_ROLECAREER = 61; // 解锁职业按钮
    private static final int C_GOTOPACT = 62;   // v2.0 契约词条
    private static final int C_GOTODOCTRINE = 63; // v2.0 天赋牌

    // ── 状态 ──
    private int mode = 0, role = 0, pact = 1;

    /** 最近一次 draw 命中的控件 id。 */
    public int hit = -1;

    public CharScreen() {
    }

    public int selectedMode() {
        return mode;
    }

    public int selectedRole() {
        return role;
    }

    public int selectedContract() {
        return pact;
    }

    /**
     * 由 GameView 在每次 draw 前注入「上次确认的选择」。
     *
     * <p><b>幂等语义（重要）</b>：只在外部注入值<b>发生变化</b>时才覆盖内部状态。
     * 否则 GameView 每帧调用会把用户刚点的 tab / 职业 / 契约立刻冲掉
     * （GameView 的 selMode/selRole/selContract 只在「确认出发」时才从本屏回读）。</p>
     */
    public void setSelection(int mode, int role, int pact) {
        int m = clamp(mode, 0, Content.MODE_NAMES.length - 1);
        int r = clamp(role, 0, Content.ROLES.length - 1);
        int p = clamp(pact, 0, Content.DIFFICULTY_NAMES.length - 1);
        boolean externalChanged = (m != lastExternal[0] || r != lastExternal[1] || p != lastExternal[2]);
        if (!seeded || externalChanged) {
            this.mode = m;
            this.role = r;
            this.pact = p;
        }
        seeded = true;
        lastExternal[0] = m;
        lastExternal[1] = r;
        lastExternal[2] = p;
    }

    /** 上次收到的外部选择值，用于判定外部是否真的改变。 */
    private final int[] lastExternal = {-1, -1, -1};
    private boolean seeded = false;

    /**
     * @param stats      {marks, pactUnlocked}
     * @param roleUnlocked 12 个职业解锁状态；null 视为仅前 4 个解锁
     */
    public void draw(Canvas c, Ui ui, float time, int[] stats, boolean[] roleUnlocked) {
        ui.clearHits();
        hit = -1;
        if (c == null || ui == null) return;

        int[] st = safe(stats, 2, 2);
        boolean[] unlocked = roleUnlocked != null && roleUnlocked.length >= Content.ROLES.length
                ? roleUnlocked : defaultUnlock();
        int marks = st[0];
        // 契约解锁上限。stats[1] 是 Meta.pactUnlocked 的原始值（存档默认 0），
        // 但 Meta 自己对"最高可选档"的定义是 max(2, pactUnlocked) + 节点26（见 contractCap()），
        // 计费侧同样是这个口径：pactCost() 从第 3 档才收费、unlockPact() 把值以 2 为基推进。
        // 所以免费基线 0..2 必须在这里显式补上，否则连「远征 / 试炼」都会被判成锁定，
        // GameView 默认选的远征（1）还会被下面那句 clamp 悄悄压回漫游。
        // 节点 26 的 +1 档需要 Meta 实例才能读到，本屏只拿得到原始值，因此不叠加（宁缺不越权）。
        int pactMax = Math.max(2, Math.max(0, Math.min(Content.DIFFICULTY_NAMES.length - 1, st[1])));

        // 若当前选中的职业未解锁，回退到第一个已解锁职业
        if (!unlocked[role]) {
            for (int i = 0; i < unlocked.length; i++) if (unlocked[i]) { role = i; break; }
        }
        if (pact > pactMax) pact = pactMax;

        backdrop(c, ui, time);
        header(c, ui, time, marks);

        // 左侧：模式 + 职业网格
        modeTabs(c, ui, time);
        roleGrid(c, ui, time, marks, unlocked);

        // 右侧：详情
        detail(c, ui, time, marks, unlocked);

        // 底部：契约
        contractRow(c, ui, time, pactMax);

        // 右下：确认
        float pulse = (float) (Math.sin(time * 2f) * .5f + .5f);
        ui.glow(c, 1240, 742, 96, ui.fade(0x2e9de7c1, .6f + .4f * pulse));
        ui.button(c, C_GO, 1064, 704, 274, 76, "确认出发  →", true, true);
        // v2.0 两个入口按钮不在这里画：它们的槽位和「返回」同属顶栏，
        // 统一放在 header() 里维护，避免两处坐标各自漂移（见 HDR_* 常量的区间注释）。
    }

    // ─────────────────────────── 背景 ───────────────────────────
    private void backdrop(Canvas c, Ui ui, float time) {
        int accent = Content.COLORS[role];
        ui.glow(c, 1020, 300, 430, ui.alpha(accent, 16));
        ui.glow(c, 160, 690, 340, 0x0d93c4ff);
        for (int i = 0; i < 26; i++) {
            float x = (i * 173 + 60) % 1400;
            float y = 120 + ((i * 89 + time * 9f) % 660);
            int a = 20 + (i % 4) * 14;
            ui.circle(c, x, y, .8f + i % 3 * .4f, ui.alpha(accent, a));
        }
    }

    // ─────────────────────────── 顶栏 ───────────────────────────
    /**
     * 顶栏横向分区（逻辑 px）。相邻区间都必须留净空，改动数值时请同步这张表。
     *
     * <pre>
     *   宝石      65 .. 91          菱形中心 78，半宽 13（精确）
     *   标题      102 .. ≤ 250      「远征准备」27px；250 是副标题起点，标题不得越过
     *   副标题    250 .. ≤ 400      「模式 · 职业 · 契约」16px，实测约 140 宽
     *   印记块    ≥ 671 .. 826      色点 / 「印记」/ 数值，整块右对齐锚在 826
     *   契约词条  852 .. 1004       精确（幽灵按钮）
     *   天赋牌    1016 .. 1168      精确
     *   返回      1180 .. 1300      精确
     *   右留白    1300 .. 1312      与分隔线（x 64，宽 1312）的右边距对齐
     * </pre>
     *
     * <p>净空：副标题→印记块 ≥271、印记块→契约词条 26、按钮之间 12、返回→分隔线右端 12。
     * 印记块宽度随数字位数伸缩（marks 上限 10 位数 ≈ 105px @19px 粗体，
     * 整块最左约 671），所以它用右锚点 826 + 向左排布：位数再多也只吃左侧空白，
     * 不会顶到右侧按钮。</p>
     *
     * <p><b>为什么印记挪到 826 右对齐</b>：原布局把色点画在 1092、数值画在 1150，
     * 而「天赋牌」幽灵按钮占 1080..1232——数字整个压在按钮底下。印记恰恰是
     * 解锁职业要读的数，被盖住等于让玩家盲买。现在印记落在副标题与按钮组之间
     * 那段空白里，按钮组从 852 起，最右 1300，三者互不重叠。</p>
     *
     * <p>按钮高度 46：1440 逻辑宽下约等于 48dp 的最小触摸高度（按 810 高对应
     * 常见 3x 屏的 dp 换算取整），与契约卡、模式 tab 的纵向节奏一致。</p>
     */
    private static final float HDR_BTN_Y = 24, HDR_BTN_H = 46;
    private static final float HDR_PACT_X = 852, HDR_PACT_W = 152;
    private static final float HDR_DOCTRINE_X = 1016, HDR_DOCTRINE_W = 152;
    private static final float HDR_BACK_X = 1180, HDR_BACK_W = 120;
    /** 印记块的右锚点：数值右边界固定在这里，左边界随数字宽度自然伸缩。 */
    private static final float HDR_MARKS_RIGHT = 826;

    private void header(Canvas c, Ui ui, float time, int marks) {
        ui.diamond(c, 78, 44, 13, Ui.MINT);
        ui.text(c, "远征准备", 102, 53, 27, Ui.WHITE, true);
        ui.text(c, "模式 · 职业 · 契约", 250, 53, 16, Ui.MUTED, false);

        // 印记余额：数值右对齐到 826，标签和色点依次往左排（见上方区间表）
        String markText = String.valueOf(marks);
        String markLabel = "印记";
        float labelW = ui.measure(markLabel, 14, false);
        float valueLeft = HDR_MARKS_RIGHT - ui.measure(markText, 19, true);
        ui.text(c, markText, valueLeft, 50, 19, Ui.VIOLET, true);
        float labelLeft = valueLeft - 10 - labelW;   // 数值与标签之间留 10px
        ui.text(c, markLabel, labelLeft, 50, 14, Ui.MUTED, false);
        // 色点再往左 12px（中心到标签左边距 8px，半径 4，视觉上贴住标签）
        ui.circle(c, labelLeft - 12, 44, 4, Ui.VIOLET);

        // v2.0 两个入口 + 返回：三个 46 高幽灵按钮等距顺排，卡与卡之间 12px
        ui.ghost(c, C_GOTOPACT, HDR_PACT_X, HDR_BTN_Y, HDR_PACT_W, HDR_BTN_H, "契约词条");
        ui.ghost(c, C_GOTODOCTRINE, HDR_DOCTRINE_X, HDR_BTN_Y, HDR_DOCTRINE_W, HDR_BTN_H, "天赋牌");
        ui.ghost(c, C_BACK, HDR_BACK_X, HDR_BTN_Y, HDR_BACK_W, HDR_BTN_H, "返回  ←");
        ui.divider(c, 64, 86, 1312);
    }

    // ─────────────────────────── 模式 tab ───────────────────────────
    private void modeTabs(Canvas c, Ui ui, float time) {
        float x = 64, y = 100, w = 728, h = 56;
        int n = Math.min(4, Content.MODE_NAMES.length);
        int[] ids = {C_MODE0, C_MODE0 + 1, C_MODE0 + 2, C_MODE0 + 3};
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) labels[i] = Content.MODE_NAMES[i];
        ui.segmented(c, x, y, w, h, n, mode, ids, labels);

        // 模式说明（基线 176，与下方 01 / EXPLORER 标签 200 留足间距）
        int en = Math.min(mode, Content.MODE_EN.length - 1);
        int de = Math.min(mode, Content.MODE_DESC.length - 1);
        ui.small(c, Content.MODE_EN[en], x + 2, y + h + 20, 12, ui.alpha(Ui.GOLD, 220));
        ui.text(c, Content.MODE_DESC[de], x + 2 + ui.measure(Content.MODE_EN[en], 12, false) + 14,
                y + h + 21, 15, Ui.MUTED, false);
    }

    // ─────────────────────────── 职业网格 4×3 ───────────────────────────
    private void roleGrid(Canvas c, Ui ui, float time, int marks, boolean[] unlocked) {
        final float gx = 64, gy = 212, cw = 172, ch = 150, gapX = 12, gapY = 10;
        ui.small(c, "01  /  EXPLORER   选择出击者", gx, 200, 12, ui.alpha(Ui.MUTED, 200));

        for (int i = 0; i < Math.min(12, Content.ROLES.length); i++) {
            int col = i % 4, row = i / 4;
            float x = gx + col * (cw + gapX);
            float y = gy + row * (ch + gapY);
            boolean sel = i == role, lock = !unlocked[i];
            int color = Content.COLORS[i];
            int accent = lock ? Ui.EDGE : color;

            ui.card(c, x, y, cw, ch, accent, sel, false);

            if (lock) {
                // 未解锁：压暗 + 锁标 + 印记价格（卡片高 150，内部元素同步上收）
                ui.locked(c, x + 12, y + 12, cw - 24, ch - 24, "");
                ui.center(c, "🔒", x + cw / 2f, y + 54, 24, ui.alpha(Ui.MUTED, 180), false);
                ui.center(c, Content.ROLES[i], x + cw / 2f, y + 90, 16, ui.alpha(Ui.WHITE, 150), true);
                int price = unlockPrice(i);
                ui.center(c, "印记 " + price, x + cw / 2f, y + 116, 12.5f, marks >= price ? Ui.VIOLET : Ui.MUTED, true);
            } else {
                ui.glow(c, x + cw / 2f, y + 58, 50, ui.alpha(color, sel ? 42 : 22));
                ui.avatar(c, x + cw / 2f, y + 78, 118, i, time * .6f + i, false);
                ui.center(c, Content.ROLES[i], x + cw / 2f, y + 110, 16.5f, sel ? color : Ui.WHITE, true);
                String sub = safeStr(Content.ROLE_DESC, i, "");
                ui.center(c, sub, x + cw / 2f, y + 131, 11, ui.alpha(Ui.MUTED, 220), false);
                // 流派徽记（左上角）
                int school = schoolOf(i);
                ui.schoolIcon(c, x + 22, y + 22, 25, school);
            }
            if (sel) {
                ui.diamond(c, x + cw - 16, y + 16, 5, Content.COLORS[i]);
            }
            ui.hit(C_ROLE0 + i, x, y, cw, ch);
        }
    }

    // ─────────────────────────── 右侧详情 ───────────────────────────
    // 复用 Path/RectF：clipPath 每帧 new 一个会持续制造垃圾
    private static final Path sClipPath = new Path();
    private static final RectF sClipRect = new RectF();

    private void detail(Canvas c, Ui ui, float time, int marks, boolean[] unlocked) {
        final float x = 820, y = 104, w = 556, h = 586;
        int color = Content.COLORS[role];
        int school = schoolOf(role);

        ui.panel(c, x, y, w, h, 20, 0xff101d24, Ui.EDGE);

        // 顶部大图区（复用 Path/RectF，避免每帧分配）
        c.save();
        sClipPath.reset();
        sClipRect.set(x + 1, y + 1, x + w - 1, y + 196);
        sClipPath.addRoundRect(sClipRect, 19, 19, Path.Direction.CW);
        c.clipPath(sClipPath);
        ui.gradientRect(c, x + 1, y + 1, w - 2, 195, 0,
                ui.blend(0xff16262e, color, .12f), 0xff101d24);
        ui.glow(c, x + 130, y + 130, 130, ui.alpha(color, 60));
        ui.avatar(c, x + 130, y + 168, 250, role, time * .8f, false);
        // 右侧文字块
        ui.pill(c, x + 282, y + 30, 118, Content.SCHOOL_NAMES[school], color);
        ui.text(c, Content.ROLES[role], x + 284, y + 92, 38, Ui.WHITE, true);
        ui.text(c, "「" + safeStr(Content.ROLE_TITLE, role, "") + "」", x + 286, y + 124, 17, color, false);
        ui.small(c, "SCHOOL  /  " + safeStr(Content.SCHOOL_EN, school, ""), x + 286, y + 154, 11,
                ui.alpha(Ui.MUTED, 200));
        c.restore();

        float ty = y + 218;

        // 玩法提示
        ui.small(c, "PLAYSTYLE", x + 24, ty, 11, ui.alpha(Ui.GOLD, 200));
        String tip = safeStr(Content.ROLE_TIP, role, "");
        ui.wrap(c, tip, x + 24, ty + 22, w - 48, 14.5f, Ui.MUTED);
        float tipH = ui.wrapHeight(tip, w - 48, 14.5f);
        ty += 26 + tipH + 10;

        // 技能 / 终极
        skillRow(c, ui, x + 24, ty, (w - 48 - 12) / 2f, "主动技能", safeStr(Content.SKILLS, role, ""),
                safeStr(Content.SKILL_DESC, role, ""), color);
        skillRow(c, ui, x + 24 + (w - 48 - 12) / 2f + 12, ty, (w - 48 - 12) / 2f, "终极技能",
                safeStr(Content.ULTIMATES, role, ""), safeStr(Content.ULTIMATE_DESC, role, ""), Ui.GOLD);
        ty += 112;

        // 三条属性
        int[] stats = roleStats(role);
        ui.small(c, "ATTRIBUTES", x + 24, ty, 11, ui.alpha(Ui.MUTED, 200));
        String[] names = {"伤害", "生存", "机动"};
        int[] colors = {Ui.RED, Ui.MINT, Ui.BLUE};
        for (int i = 0; i < 3; i++) {
            float ry = ty + 20 + i * 30;
            ui.text(c, names[i], x + 24, ry + 11, 13, ui.MUTED, false);
            ui.barSeg(c, x + 76, ry + 2, 300, 10, stats[i] / 5f, colors[i], 5);
            ui.text(c, stats[i] + "", x + 388, ry + 11, 13, colors[i], true);
        }

        // 底部：流派共鸣说明
        float by = y + h - 62;
        ui.divider(c, x + 24, by - 14, w - 48);
        ui.schoolIcon(c, x + 42, by + 14, 28, school);
        ui.text(c, Content.SCHOOL_NAMES[school] + " 共鸣", x + 62, by + 8, 14, color, true);
        ui.wrap(c, safeStr(Content.SCHOOL_BONUS, school, ""), x + 62, by + 28, w - 100, 12,
                ui.alpha(Ui.MUTED, 210));

        // 未解锁提示
        if (!unlocked[role]) {
            int price = unlockPrice(role);
            ui.box(c, x + 1, y + 1, w - 2, h - 2, 19, 0xdd0b141b);
            ui.center(c, "该职业尚未解锁", x + w / 2f, y + h / 2f - 44, 24, Ui.WHITE, true);
            ui.center(c, "消耗深渊印记解锁，永久保留", x + w / 2f, y + h / 2f - 10, 15, Ui.MUTED, false);
            ui.center(c, "印记 " + price, x + w / 2f, y + h / 2f + 30, 22,
                    marks >= price ? Ui.VIOLET : Ui.MUTED, true);
            ui.button(c, C_ROLECAREER, x + w / 2f - 130, y + h / 2f + 58, 260, 62,
                    marks >= price ? "解锁职业" : "印记不足", marks >= price, marks >= price);
        }
    }

    private void skillRow(Canvas c, Ui ui, float x, float y, float w, String tag, String name, String desc, int color) {
        ui.panel(c, x, y, w, 100, 12, 0xff16252d, 0);
        ui.box(c, x, y, 3, 100, 1.5f, color);
        ui.small(c, tag, x + 14, y + 22, 10, ui.alpha(color, 210));
        ui.text(c, name, x + 14, y + 46, 18, Ui.WHITE, true);
        String d = desc == null ? "" : desc;
        ui.wrap(c, d, x + 14, y + 68, w - 28, 12, ui.alpha(Ui.MUTED, 225));
    }

    // ─────────────────────────── 契约行 ───────────────────────────
    /**
     * 底部 5 档契约。
     *
     * <p>纵向：职业网格 212 + 3×150 + 2×10 = 692 结束；标签基线 706；卡片 714..782；
     * 锁定说明基线 800（卡片下方 18px，仍在 810 内）。
     * 横向：单卡 sw = 972/5 = 194.4，卡体 sx+3 .. sx+sw-3，卡间留 6px 缝。</p>
     */
    private void contractRow(Canvas c, Ui ui, float time, int pactMax) {
        final float x = 64, y = 714, w = 972, h = 68;
        ui.small(c, "02  /  CONTRACT   选择契约档位", x, 706, 12, ui.alpha(Ui.GOLD, 200));

        int n = Math.min(5, Content.DIFFICULTY_NAMES.length);
        float sw = w / n;
        boolean anyLocked = false;
        for (int i = 0; i < n; i++) {
            float sx = x + i * sw;
            boolean on = i == pact, locked = i > pactMax;
            if (locked) {
                anyLocked = true;
                // 锁定档位走独立画法：压暗 + 锁标 + 一行开放条件，且不登记命中区
                lockedContract(c, ui, i, sx + 3, y, sw - 6, h, pactMax);
            } else {
                int accent = contractColor(i);
                ui.card(c, sx + 3, y, sw - 6, h, accent, on, false);
                ui.center(c, Content.DIFFICULTY_NAMES[i], sx + sw / 2f, y + 21, 15.5f,
                        on ? accent : Ui.WHITE, true);
                ui.center(c, "HP ×" + fmt(Balance.HP[safeIdx(Balance.HP.length, i)]), sx + sw / 2f, y + 42, 11f,
                        ui.alpha(Ui.MUTED, 200), false);
                ui.center(c, "DMG ×" + fmt(Balance.DAMAGE[safeIdx(Balance.DAMAGE.length, i)]), sx + sw / 2f, y + 57, 11f,
                        ui.alpha(accent, 190), false);
                ui.hit(C_PACT0 + i, sx + 3, y, sw - 6, h);
            }
            if (i < n - 1) ui.box(c, sx + sw - 1, y + 18, 1, h - 36, 0, ui.alpha(Ui.EDGE, 150));
        }

        // 契约提示：放到标题行右侧空白处（基线 706），完全不与卡片行/屏幕底边冲突
        String hint = safeStr(Balance.CONTRACT_HINTS, pact, "");
        float hintSize = ui.fit(hint, 620, 12.5f, 11f);
        float hintW = ui.measure(hint, hintSize, false);
        ui.right(c, "档位效果", x + w - hintW - 84, 706, 11.5f, ui.alpha(Ui.MUTED, 180), false);
        ui.right(c, hint, x + w - 12, 706, hintSize, ui.alpha(Ui.GOLD, 215), false);

        // 有锁定档时补一条全局说明：锁定卡的尺寸只够写"需先开哪一档"，
        // "解锁要花什么"这条规律放在卡片下方一行讲清楚，不用玩家自己猜。
        if (anyLocked) {
            // 这一行原先画在 y=800，而画布只有 810 高 —— 11.5px 文字下缘
            // 落在 813 附近，实测截图里只剩半行。
            // 契约卡占 y 714..782，卡上方 692..710 是空闲带：
            //   左侧 x=64..244 是「02 / CONTRACT」标题，右侧是「档位效果 + 推荐」
            // 所以把说明夹在两者之间（x=300 起），并在 690 基线画出。
            String note = "锁定档位需用深渊印记逐档解锁，前一档开启后下一档才可选";
            float noteSize = ui.fit(note, 360, 11f, 9.5f);
            ui.text(c, note, 300, 690, noteSize, ui.alpha(Ui.MUTED, 185), false);
        }
    }

    /**
     * 单个锁定契约档：压暗卡片 + 锁标 + 一行解锁条件。
     *
     * <p>为什么单独抽出来：解锁卡与锁定卡的内容结构不同（3 行 vs 2 行），
     * 混在一个循环里会让 y 偏移散落各处，后续调高度时极易漏改。
     * 纵向节奏（卡高 68，与解锁卡对齐）：标题/锁标中心 y+21、条件行 y+46。</p>
     *
     * <p>解锁条件只陈述 {@code Meta} 已有的规则，不在这里另造数值：
     * 契约档位由 {@code Meta.pactUnlocked} 这一个上限决定，{@code Meta.unlockPact()}
     * 每次只推进一档且花费印记，因此除「紧邻的下一档」以外，其余锁定档
     * 能给出的最短正确说明就是"需先解锁前一档"。</p>
     *
     * @param i      档位下标
     * @param pactMax 已解锁的最高档（= 当前上限）
     */
    private void lockedContract(Canvas c, Ui ui, int i, float x, float y, float w, float h,
                                int pactMax) {
        ui.card(c, x, y, w, h, Ui.EDGE, false, false);
        // 再压一层 55% 暗色：card() 的 EDGE 描边太接近正常卡，
        // 不足以表达"不可点"。与 PactScreen 未解锁卡的叠色手法保持一致。
        ui.box(c, x + 1, y + 1, w - 2, h - 2, 13, 0x55070d11);

        String name = Content.DIFFICULTY_NAMES[i];
        // 标题左移 9px 给锁标让位，两者合起来仍在卡内居中
        float nameCx = x + w / 2f - 9;
        ui.center(c, name, nameCx, y + 21, 15.5f, ui.alpha(Ui.WHITE, 148), true);
        // 锁标沿用职业网格的同一字形（🔒），12px 适配 68 高的卡片
        ui.center(c, "🔒", nameCx + ui.measure(name, 15.5f, true) / 2f + 11, y + 21, 12,
                ui.alpha(Ui.MUTED, 190), false);

        String req = i == pactMax + 1
                ? "下一档"
                : "需先解锁「" + Content.DIFFICULTY_NAMES[Math.max(0, i - 1)] + "」";
        // 只有紧邻的下一档能买，所以只有它报价格；更深档位报价格等于撒谎
        // （中间还隔着好几档，一次 unlockPact 只推进一档）。
        if (i == pactMax + 1) {
            ui.center(c, req, x + w / 2f, y + 43, 10.5f, ui.alpha(Ui.MUTED, 152), false);
            int price = pactPrice(i);
            if (price >= 0) {
                ui.center(c, "印记 " + price, x + w / 2f, y + 57, 11.5f,
                        ui.alpha(Ui.VIOLET, 200), true);
            }
        } else {
            ui.center(c, req, x + w / 2f, y + 50, 10.5f, ui.alpha(Ui.MUTED, 152), false);
        }
    }

    /**
     * 解锁该契约档所需印记，直接问 {@code Meta}（唯一计费实现）。
     *
     * <p>与职业标价同一个原则：UI 不许自己算钱。职业卡曾经写死 60、
     * 实际扣 80，玩家点下去才发现不够；契约档位不能重犯。
     */
    private static int pactPrice(int tier) {
        try {
            return com.dilao.abysssquad.Meta.pactCostOf(tier);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ─────────────────────────── 点击 ───────────────────────────
    public int click(int id) {
        hit = id;
        if (id >= C_MODE0 && id < C_MODE0 + 4) {
            mode = id - C_MODE0;
            // 每日星盘固定契约，其它模式沿用
            return -1;
        }
        if (id >= C_ROLE0 && id < C_ROLE0 + 12) {
            role = id - C_ROLE0;
            return -1;
        }
        if (id >= C_PACT0 && id < C_PACT0 + 5) {
            pact = id - C_PACT0;
            return -1;
        }
        if (id == C_GO) return Ui.A_START;
        if (id == C_BACK) return Ui.A_BACK;
        if (id == C_ROLECAREER) return Ui.A_BUY;
        if (id == C_GOTOPACT) return Ui.A_NODE + 2000;
        if (id == C_GOTODOCTRINE) return Ui.A_NODE + 2001;
        return -1;
    }

    /**
     * 解锁职业所需印记。
     *
     * <p><b>v2.2 修复</b>：这里原先独立算 `60 + (n-4)*30`，而真正扣费的是
     * {@code Meta.roleCost()} 的 `80 + (n-4)*60` —— UI 标 60、实际扣 80，
     * 玩家点下去发现印记不够，是首小时最容易崩塌信任的一处。
     * 现在把价格交给 Meta 那份**真实的**计费函数，单一真相源。
     */
    public static int unlockPrice(int roleIndex) {
        // 单一真相源：直接问 Meta。它同时是**真正扣费**的那份实现。
        return com.dilao.abysssquad.Meta.roleCostOf(roleIndex);
    }

    // ─────────────────────────── 工具 ───────────────────────────
    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static int[] safe(int[] v, int n, int fill) {
        int[] o = new int[n];
        if (v != null) {
            System.arraycopy(v, 0, o, 0, Math.min(v.length, n));
            for (int i = v.length; i < n; i++) o[i] = fill;
        } else {
            java.util.Arrays.fill(o, fill);
        }
        return o;
    }

    private static String safeStr(String[] a, int i, String fb) {
        if (a == null || a.length == 0) return fb;
        String s = a[Math.floorMod(i, a.length)];
        return s == null ? fb : s;
    }

    private static int safeLen(String[] a) {
        return a == null ? 1 : Math.max(1, a.length);
    }

    private static int safeIdx(int len, int i) {
        return len <= 0 ? 0 : Math.floorMod(i, len);
    }

    private static int schoolOf(int role) {
        if (Content.SCHOOL == null || Content.SCHOOL.length == 0) return 0;
        return Math.floorMod(Content.SCHOOL[Math.floorMod(role, Content.SCHOOL.length)], 6);
    }

    private static boolean[] defaultUnlock() {
        boolean[] u = new boolean[Content.ROLES.length];
        for (int i = 0; i < Math.min(4, u.length); i++) u[i] = true;
        return u;
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    /** 职业三维：伤害/生存/机动，1..5 星，按契约 §4 的定位手工标定。 */
    private static int[] roleStats(int role) {
        switch (Math.floorMod(role, 12)) {
            case 0: return new int[]{4, 2, 5};   // 游侠
            case 1: return new int[]{2, 5, 2};   // 守卫
            case 2: return new int[]{2, 4, 3};   // 星医
            case 3: return new int[]{4, 3, 2};   // 术士
            case 4: return new int[]{5, 2, 2};   // 炼金
            case 5: return new int[]{4, 2, 5};   // 影舞
            case 6: return new int[]{4, 3, 2};   // 机铠
            case 7: return new int[]{2, 4, 3};   // 圣歌
            case 8: return new int[]{4, 3, 2};   // 霜灵
            case 9: return new int[]{5, 2, 3};   // 血誓
            case 10: return new int[]{5, 2, 3};  // 雷铳
            default: return new int[]{4, 3, 4};  // 虚空行者
        }
    }

    private static int contractColor(int i) {
        switch (Math.floorMod(i, 5)) {
            case 0: return Ui.MINT;
            case 1: return Ui.BLUE;
            case 2: return Ui.GOLD;
            case 3: return Ui.VIOLET;
            default: return Ui.RED;
        }
    }
}
