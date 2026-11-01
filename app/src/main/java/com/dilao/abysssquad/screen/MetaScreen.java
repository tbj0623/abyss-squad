package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Meta;
import com.dilao.abysssquad.Ui;

/**
 * 星图元进度树：32 个节点，按 {@link Meta#NODE_GROUP} 分成 6 组展示。
 *
 * <p><b>唯一数据源是 {@link Meta}</b>：节点名 / 说明 / 分组 / 等级上限 / 价格全部直接读
 * {@code Meta} 的表，本类不再维护任何本地副本。分组不再硬编码区间，而是按
 * {@code NODE_GROUP[i]} 动态分桶，桶内保持原始下标升序 —— 这样 {@code click()}
 * 返回的 {@code Ui.A_NODE + n} 里的 n 永远是 Meta 的真实下标。
 *
 * <p>价格统一走 {@link Meta#costForLevel(int)}，与 {@link Meta#buyNode(int)} 的扣费严格一致；
 * 每节点上限走 {@link Meta#NODE_MAX}（节点 24 复活=2、25 开局遗物=1、26 契约精通=2、
 * 30 首领卷宗=8，其余 5）。
 *
 * <p>动作码：点击节点返回 {@code Ui.A_NODE + n}（由 GameView 调 {@code buyNode} 判断成败）；
 * 重置返回 {@link Ui#A_CONFIRM}；返回 {@link Ui#A_BACK}。
 */
public final class MetaScreen {

    // ── 控件 id ──
    private static final int C_BACK = 1;
    private static final int C_RESET = 2;
    private static final int C_NODE0 = 100;          // 100..131 节点 0..31
    private static final int C_BUY0 = C_NODE0 + 500; // 600..631 购买按钮

    public static final int NODES = Meta.NODES;

    // ── 布局 ──
    private static final float GX = 64f, GY = 150f, GW = 940f;
    private static final float BAND_H = 78f;      // 每组占高
    private static final float CARD_H = 56f;
    private static final float CARD_GAP = 8f;
    private static final float CARD_MAX_W = 140f;

    // ── 天赋牌横条（v2.0 支柱 2）──
    private static final float DOC_Y = 618f;      // 横条顶部
    private static final float DOC_H = 104f;
    private static final int C_DOC0 = 700;        // 700..717 天赋牌格子

    // ── 状态 ──
    private int sel = 0;
    private boolean confirmReset = false;
    public int hit = -1;

    /** draw() 缓存的星尘余额，供 click() 判断"星尘不足"反馈。 */
    private int lastDust;
    /** draw() 缓存的节点等级，供 click() 判断满级。 */
    private int[] lastLevels = new int[NODES];
    /** 点击被拒绝时的闪烁反馈（节点下标 + 触发时刻）。 */
    private int denyId = -1;
    private long denyNanos = 0L;

    public MetaScreen() {
    }

    /**
     * 由 GameView 注入当前 Meta 实例，用于读取「当前装备的天赋牌」与单张解锁状态。
     *
     * <p>不注入也不会崩：{@link DoctrineApi} 会退化到静态表 + 默认解锁规则。</p>
     */
    public void bindMeta(Object meta) {
        DoctrineApi.bind(meta);
    }

    /**
     * @param stats     {dust, marks}；可为 null
     * @param nodeLevel 节点等级数组；长度可不足 32，缺的部分按 0 处理
     */
    public void draw(Canvas c, Ui ui, float time, int[] stats, int[] nodeLevel) {
        ui.clearHits();
        hit = -1;
        if (c == null || ui == null) return;

        int[] st = stats == null || stats.length < 2 ? new int[2] : stats;
        int dust = Math.max(0, st[0]);
        int[] lv = nodeLevel;
        lastDust = dust;
        lastLevels = lv;

        // 选中下标防御
        if (sel < 0) sel = 0;
        if (sel >= NODES) sel = NODES - 1;

        backdrop(c, ui, time);
        header(c, ui, dust);
        grid(c, ui, dust, lv);
        detail(c, ui, dust, lv);
        doctrineBar(c, ui, time);
        footer(c, ui, dust, lv);
    }

    // ─────────────────────────── 背景 / 顶栏 ───────────────────────────
    private void backdrop(Canvas c, Ui ui, float time) {
        ui.glow(c, 520, 360, 620, 0x0e7f8fd6);
        ui.glow(c, 1200, 620, 400, 0x0b9de7c1);
        for (int i = 0; i < 46; i++) {
            float x = (i * 167 + 30) % 1400;
            float y = (i * 97 + 40) % 780;
            float tw = .5f + .5f * (float) Math.sin(time * 1.1f + i * .7f);
            ui.circle(c, x, y, .7f + (i % 3) * .5f, ui.alpha(0xffdbe6ff, (int) (22 + tw * 46)));
        }
    }

    private void header(Canvas c, Ui ui, int dust) {
        ui.diamond(c, 78, 44, 13, Ui.VIOLET);
        ui.text(c, "星图元进度", 102, 53, 27, Ui.WHITE, true);
        ui.text(c, "CONSTELLATION", 258, 52, 12, ui.alpha(Ui.MUTED, 190), false);

        ui.circle(c, 906, 44, 4, Ui.GOLD);
        ui.text(c, "星尘", 920, 50, 14, Ui.MUTED, false);
        ui.text(c, format(dust), 960, 50, 21, Ui.GOLD, true);

        ui.ghost(c, C_BACK, 1252, 24, 120, 46, "返回  ←");
        ui.divider(c, 64, 86, 1312);
    }

    // ─────────────────────────── 6 组网格（按 NODE_GROUP 动态分桶）───────────────────────────
    private void grid(Canvas c, Ui ui, int dust, int[] lv) {
        for (int g = 0; g < Meta.GROUPS; g++) {
            int[] ids = Meta.groupNodes(g);
            if (ids.length == 0) continue;
            float y = GY + g * BAND_H;
            int color = groupColor(g);

            // 组标题
            ui.text(c, Meta.nodeGroupName(g), GX, y + 11, 14, color, true);
            ui.box(c, GX + 62, y + 4, 1, 13, 0, ui.alpha(color, 120));
            ui.text(c, groupSub(g), GX + 74, y + 11, 11f, ui.alpha(Ui.MUTED, 185), false);
            ui.right(c, ids.length + " 个节点", GX + GW, y + 11, 11f, ui.alpha(Ui.MUTED, 150), false);

            // 节点横排（居中，行宽不足时自动拉满）
            float w = cardWidth(ids.length);
            float rowW = ids.length * w + (ids.length - 1) * CARD_GAP;
            float x0 = GX + Math.max(0f, (GW - rowW) * .5f);
            for (int k = 0; k < ids.length; k++) {
                float x = x0 + k * (w + CARD_GAP);
                node(c, ui, dust, lv, ids[k], x, y + 17, w, CARD_H, color);
            }
        }
    }

    private static float cardWidth(int count) {
        if (count <= 0) return CARD_MAX_W;
        return Math.min(CARD_MAX_W, (GW - (count - 1) * CARD_GAP) / count);
    }

    private void node(Canvas c, Ui ui, int dust, int[] lv, int id,
                      float x, float y, float w, float h, int groupColor) {
        int max = Meta.nodeMax(id);
        int level = levelOf(lv, id, max);
        boolean maxed = level >= max;
        int cost = Meta.costForLevel(level);
        boolean affordable = !maxed && dust >= cost;
        boolean on = sel == id;
        boolean denied = id == denyId && System.nanoTime() - denyNanos < 700_000_000L;

        int accent = denied ? Ui.RED : on ? Ui.MINT : maxed ? Ui.GOLD : affordable ? groupColor : Ui.EDGE;
        ui.card(c, x, y, w, h, accent, on || denied, false);

        // 图标
        int iconColor = denied ? Ui.RED : maxed ? Ui.GOLD : (level > 0 ? groupColor : ui.alpha(Ui.MUTED, 150));
        ui.icon(c, x + 16, y + 17, 20, iconOf(id), iconColor);

        // 名称（按可用宽度自动缩字号）
        float nameMax = w - 34 - 44;
        float nameSize = ui.fit(Meta.nodeName(id), nameMax, 12f, 11f);
        ui.text(c, Meta.nodeName(id), x + 31, y + 21, nameSize,
                level > 0 ? Ui.WHITE : ui.alpha(Ui.WHITE, 205), true);

        // 等级 x / max
        ui.right(c, level + "/" + max, x + w - 8, y + 21, 10,
                maxed ? Ui.GOLD : ui.alpha(Ui.MUTED, 215), true);

        // 等级点阵（按该节点真实上限）
        float dotX = x + 11, dotY = y + 38;
        float spacing = Math.min(9f, (w - 24) / Math.max(1, max));
        for (int i = 0; i < max; i++) {
            boolean filled = i < level;
            ui.circle(c, dotX + i * spacing, dotY, 3f,
                    filled ? groupColor : ui.alpha(Ui.EDGE, 220));
        }

        // 花费 / 已满级
        if (maxed) {
            ui.right(c, "已满级", x + w - 8, y + h - 8, 11f, Ui.GOLD, true);
        } else {
            int costColor = denied ? Ui.RED : affordable ? Ui.GOLD : ui.alpha(Ui.MUTED, 170);
            ui.right(c, "◆ " + cost, x + w - 8, y + h - 8, 11f, costColor, true);
        }

        ui.hit(C_NODE0 + id, x, y, w, h);
    }

    // ─────────────────────────── 右侧详情 ───────────────────────────
    private void detail(Canvas c, Ui ui, int dust, int[] lv) {
        // 高度收到 460（150..610），给下方天赋条（618 起）留 8px 间隙
        final float x = 1024f, y = 150f, w = 352f, h = 460f;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);

        int id = Math.max(0, Math.min(NODES - 1, sel));
        int max = Meta.nodeMax(id);
        int level = levelOf(lv, id, max);
        int cost = Meta.costForLevel(level);
        boolean maxed = level >= max;
        boolean affordable = !maxed && dust >= cost;
        int group = Meta.nodeGroup(id);
        int groupColor = groupColor(group);
        String name = Meta.nodeName(id);
        String desc = Meta.nodeDesc(id);

        // 顶部图标区
        ui.gradientRect(c, x + 1, y + 1, w - 2, 96, 17,
                ui.blend(0xff16262e, groupColor, .16f), 0xff101d24);
        ui.glow(c, x + w / 2f, y + 52, 54, ui.alpha(groupColor, 56));
        ui.ring(c, x + w / 2f, y + 50, 31, ui.alpha(groupColor, 150), 1.5f);
        ui.icon(c, x + w / 2f, y + 50, 40, iconOf(id), groupColor);

        // 名称 + 分组
        ui.center(c, name, x + w / 2f, y + 120, 20, Ui.WHITE, true);
        ui.pill(c, x + w / 2f - 46, y + 138, 92, Meta.nodeGroupName(group), groupColor);

        // 等级 + 分段条
        ui.center(c, "等级 " + level + " / " + max, x + w / 2f, y + 188, 12.5f,
                maxed ? Ui.GOLD : ui.alpha(Ui.MUTED, 220), true);
        ui.barSeg(c, x + 30, y + 202, w - 60, 10,
                max > 0 ? level / (float) max : 0f, groupColor, Math.max(1, max));

        ui.divider(c, x + 24, y + 230, w - 48);

        // 每级效果
        ui.text(c, "每级效果", x + 26, y + 252, 11, ui.alpha(Ui.MUTED, 190), false);
        ui.wrap(c, desc, x + 26, y + 272, w - 52, 13, Ui.WHITE);

        // 当前 / 下一级
        ui.text(c, "当前加成", x + 26, y + 318, 11, ui.alpha(Ui.MUTED, 190), false);
        ui.text(c, level <= 0 ? "未激活" : scaleDesc(desc, level), x + 26, y + 338, 13,
                level <= 0 ? ui.alpha(Ui.MUTED, 170) : groupColor, true);
        if (!maxed) {
            ui.text(c, "下一级", x + 26, y + 364, 11, ui.alpha(Ui.MUTED, 190), false);
            ui.text(c, scaleDesc(desc, level + 1), x + 26, y + 384, 13,
                    ui.alpha(groupColor, 210), true);
        }

        // 底部购买区
        float by = y + h - 78;
        ui.divider(c, x + 24, by, w - 48);
        ui.text(c, "花费", x + 26, by + 26, 12, ui.alpha(Ui.MUTED, 190), false);
        ui.text(c, maxed ? "—" : "◆ " + cost, x + 66, by + 26, 15,
                maxed ? Ui.MUTED : affordable ? Ui.GOLD : Ui.RED, true);
        ui.text(c, "持有 ◆ " + format(dust), x + 26, by + 50, 11, ui.alpha(Ui.MUTED, 175), false);

        if (maxed) {
            ui.button(c, C_BUY0 + id, x + w - 140, by + 8, 116, 44, "已满级", false, false);
        } else {
            String label = affordable ? "升级  →" : "星尘不足";
            ui.button(c, C_BUY0 + id, x + w - 140, by + 8, 116, 44, label, affordable, affordable);
        }
    }

    // ─────────────────────────── 底部 ───────────────────────────
    private void footer(Canvas c, Ui ui, int dust, int[] lv) {
        // v2.0：底部让给天赋条（618..706），本 footer 压缩成 706..796 的一条细带。
        float x = 64f, y = 706f, w = 940f, h = 34f;
        ui.panel(c, x, y, w, h, 10, 0xff101d24, Ui.EDGE);

        int total = 0, maxAll = 0;
        for (int i = 0; i < NODES; i++) {
            int m = Meta.nodeMax(i);
            maxAll += m;
            total += levelOf(lv, i, m);
        }
        ui.text(c, "总进度", x + 18, y + 22, 11.5f, ui.alpha(Ui.MUTED, 200), false);
        ui.bar(c, x + 68, y + 13, 300, 9, total / (float) Math.max(1, maxAll), Ui.MINT);
        ui.text(c, total + " / " + maxAll, x + 378, y + 22, 12, Ui.MINT, true);
        ui.text(c, "已点亮 " + litNodes(lv) + " / " + NODES, x + 470, y + 22, 11.5f,
                ui.alpha(Ui.MUTED, 205), false);
        ui.text(c, "每级 20 + 等级×35 星尘", x + 610, y + 22, 11,
                ui.alpha(Ui.MUTED, 180), false);

        if (confirmReset) {
            ui.pill(c, x + w - 302, y + 3, 120, "确定重置？", Ui.RED);
            ui.button(c, 3, x + w - 174, y + 2, 76, 30, "确定", false, true);
            ui.button(c, 4, x + w - 92, y + 2, 76, 30, "取消", false, true);
        } else {
            ui.ghost(c, C_RESET, x + w - 196, y + 2, 180, 30, "重置并返还 50%");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  天赋牌横条（v2.0 支柱 2）
    //  数据全部经 reflection 读取 Meta 的 doctrine API：Meta 里那些成员
    //  多为包私有，而本类在 .screen 子包，编译期无法直接访问。
    //  任何字段/方法缺失都降级为空列表，绝不抛异常、绝不硬编译依赖。
    // ══════════════════════════════════════════════════════════════

    private void doctrineBar(Canvas c, Ui ui, float time) {
        final float x = 64f, y = 618f, w = 940f, h = 88f;
        ui.panel(c, x, y, w, h, 14, 0xff101d24, Ui.EDGE);

        int count = DoctrineApi.count();
        if (count <= 0) {
            // 降级：Meta 的 doctrine API 不可用时，不显示这一栏
            ui.center(c, "天赋牌系统未就绪", x + w / 2f, y + h / 2f, 12,
                    ui.alpha(Ui.MUTED, 185), true);
            return;
        }

        // 标题行
        int equipped = DoctrineApi.equipped();
        int unlockedN = DoctrineApi.unlockedCount();
        ui.text(c, "天赋", x + 18, y + 22, 14, Ui.VIOLET, true);
        ui.small(c, "DOCTRINE", x + 54, y + 22, 10, ui.alpha(Ui.MUTED, 190));
        ui.text(c, "已解锁 " + unlockedN + " / " + count, x + 128, y + 22, 11.5f,
                ui.alpha(Ui.MUTED, 215), false);
        String eq = equipped >= 0 ? DoctrineApi.name(equipped) : "未装备";
        ui.right(c, "当前装备：" + eq, x + w - 18, y + 22, 11.5f,
                equipped >= 0 ? Ui.VIOLET : ui.alpha(Ui.MUTED, 200), true);

        // 6 组各 3 张，横排成一条
        int groups = DoctrineApi.groups();
        if (groups <= 0) groups = 1;
        float padX = 18f;
        float cellGap = 6f;
        float cellW = Math.min(148f, (w - padX * 2 - (count - 1) * cellGap) / Math.max(1, count));
        float cellH = 44f;
        float cy = y + 34f;
        float cx = x + padX;

        for (int i = 0; i < count; i++) {
            boolean unlocked = DoctrineApi.unlocked(i);
            boolean on = i == equipped;
            int g = DoctrineApi.group(i);
            int accent = Ui.SCHOOL_COLOR[Math.floorMod(g, 6)];
            if (!unlocked) accent = Ui.EDGE;

            // 卡片：选定态用 selectCard 的视觉语言（这里手绘更紧凑的格子）
            ui.card(c, cx, cy, cellW, cellH, on ? accent : (unlocked ? ui.alpha(accent, 150) : Ui.EDGE), on, false);
            if (!unlocked) {
                drawLockMini(ui, c, cx + 13, cy + cellH / 2f, 11, ui.alpha(Ui.MUTED, 165));
            } else {
                ui.schoolIcon(c, cx + 14, cy + cellH / 2f, 18, Math.floorMod(g, 6));
            }
            String nm = DoctrineApi.name(i);
            float ns = ui.fit(nm, cellW - 34, 11.5f, 11f);
            ui.text(c, nm, cx + 27, cy + 20, ns,
                    on ? Ui.VIOLET : (unlocked ? Ui.WHITE : ui.alpha(Ui.MUTED, 190)), true);
            ui.text(c, on ? "已装备" : (unlocked ? DoctrineApi.groupName(g) : "未解锁"),
                    cx + 27, cy + 36, 11,
                    on ? Ui.VIOLET : ui.alpha(unlocked ? accent : Ui.MUTED, 200), false);

            ui.hit(C_DOC0 + i, cx, cy, cellW, cellH);
            cx += cellW + cellGap;
        }
    }

    private void drawLockMini(Ui ui, Canvas c, float x, float y, float size, int color) {
        float s = size * .5f;
        ui.box(c, x - s * .8f, y - s * .05f, s * 1.6f, s * 1.15f, s * .2f, color);
        ui.ring(c, x, y - s * .38f, s * .46f, color, Math.max(1.2f, s * .22f));
    }

    /**
     * Meta.doctrine* API 的反射安全封装。
     *
     * <p>为什么不直接调：`Meta` 在 `com.dilao.abysssquad`，本类在
     * `com.dilao.abysssquad.screen`，而 `selectedDoctrine()` / `doctrine` 字段
     * 是<b>包私有</b>；`DOCTRINE_NAME` 等虽是 public static，但反射统一处理更稳
     * —— 若 meta 代理后续改签名，这里只会降级，不会让全仓库编译失败。</p>
     */
    static final class DoctrineApi {
        private static Meta meta;

        private static Class<?> metaClass() {
            try {
                return Class.forName("com.dilao.abysssquad.Meta");
            } catch (Throwable t) {
                return null;
            }
        }

        /** GameView 可注入当前 Meta 实例（可选；不注入则 equipped 恒为 -1）。 */
        public static void bind(Object m) {
            meta = (m instanceof Meta) ? (Meta) m : null;
        }

        static int count() {
            Integer v = staticInt("DOCTRINE_COUNT");
            return v == null ? 0 : Math.max(0, Math.min(64, v));
        }

        static int groups() {
            Integer v = staticInt("DOCTRINE_GROUPS");
            return v == null ? 0 : Math.max(0, Math.min(16, v));
        }

        private static Integer staticInt(String field) {
            try {
                Class<?> k = metaClass();
                if (k == null) return null;
                java.lang.reflect.Field f = k.getField(field);
                return f.getInt(null);
            } catch (Throwable t) {
                return null;
            }
        }

        private static String[] staticStrings(String field) {
            try {
                Class<?> k = metaClass();
                if (k == null) return null;
                java.lang.reflect.Field f = k.getField(field);
                Object o = f.get(null);
                return (o instanceof String[]) ? (String[]) o : null;
            } catch (Throwable t) {
                return null;
            }
        }

        private static int[] staticInts(String field) {
            try {
                Class<?> k = metaClass();
                if (k == null) return null;
                java.lang.reflect.Field f = k.getField(field);
                Object o = f.get(null);
                return (o instanceof int[]) ? (int[]) o : null;
            } catch (Throwable t) {
                return null;
            }
        }

        static String name(int i) {
            String[] a = staticStrings("DOCTRINE_NAME");
            if (a == null || i < 0 || i >= a.length) return "天赋 " + (i + 1);
            return a[i] == null ? "天赋 " + (i + 1) : a[i];
        }

        static int group(int i) {
            int[] a = staticInts("DOCTRINE_GROUP");
            if (a == null || i < 0 || i >= a.length) return 0;
            return Math.max(0, a[i]);
        }

        static String groupName(int g) {
            String[] a = staticStrings("DOCTRINE_GROUP_NAMES");
            if (a == null || g < 0 || g >= a.length) return "";
            return a[g] == null ? "" : a[g];
        }

        /** 单张解锁状态：优先实例方法，退化为反射读 boolean[] 字段。 */
        static boolean unlocked(int i) {
            // 1) 实例方法 doctrineUnlocked(int)
            if (meta != null) {
                try {
                    java.lang.reflect.Method m = metaClass().getMethod("doctrineUnlocked", int.class);
                    Object r = m.invoke(meta, i);
                    if (r instanceof Boolean) return (Boolean) r;
                } catch (Throwable ignored) {
                }
                // 2) 反射读包私有 boolean[] 字段
                try {
                    java.lang.reflect.Field f = metaClass().getDeclaredField("doctrineUnlocked");
                    f.setAccessible(true);
                    Object o = f.get(meta);
                    if (o instanceof boolean[]) {
                        boolean[] arr = (boolean[]) o;
                        return i >= 0 && i < arr.length && arr[i];
                    }
                } catch (Throwable ignored) {
                }
            }
            // 无实例时：按「默认每组解锁 1 张」的契约猜测，保证界面不空
            return i >= 0 && i % 3 == 0;
        }

        static int unlockedCount() {
            int n = 0, c = count();
            for (int i = 0; i < c; i++) if (unlocked(i)) n++;
            return n;
        }

        /** 当前装备的牌下标；未装备返回 -1。 */
        static int equipped() {
            if (meta == null) return -1;
            // 1) 包私有方法 selectedDoctrine()
            try {
                java.lang.reflect.Method m = metaClass().getDeclaredMethod("selectedDoctrine");
                m.setAccessible(true);
                Object r = m.invoke(meta);
                if (r instanceof Integer) return (Integer) r;
            } catch (Throwable ignored) {
            }
            // 2) 反射读包私有 int 字段 doctrine
            try {
                java.lang.reflect.Field f = metaClass().getDeclaredField("doctrine");
                f.setAccessible(true);
                Object r = f.get(meta);
                if (r instanceof Integer) return (Integer) r;
            } catch (Throwable ignored) {
            }
            return -1;
        }
    }

    // ─────────────────────────── 点击 ───────────────────────────
    /**
     * 返回动作码。购买/点击节点统一返回 {@code Ui.A_NODE + 真实下标}，
     * 由 GameView 调 {@code meta.buyNode(n)} 判定成功与否（星尘不足、满级都由那边兜底）。
     */
    public int click(int id) {
        hit = id;
        if (id == C_BACK) {
            confirmReset = false;
            return Ui.A_BACK;
        }
        if (id == C_RESET) {
            confirmReset = true;
            return -1;
        }
        if (id == 3) {
            confirmReset = false;
            return Ui.A_CONFIRM;
        }
        if (id == 4) {
            confirmReset = false;
            return -1;
        }
        // 天赋牌：点击切换装备。装备动作由 GameView 交给 Meta（本类只读，不直接写）。
        // 返回 A_NODE + 1000 + i，GameView 可据此调 meta.equipDoctrine(i)。
        if (id >= C_DOC0 && id < C_DOC0 + 32) {
            int d = id - C_DOC0;
            confirmReset = false;
            if (!DoctrineApi.unlocked(d)) return -1;   // 未解锁：不响应
            return Ui.A_NODE + 1000 + d;
        }
        // 详情面板的购买按钮
        if (id >= C_BUY0 && id < C_BUY0 + NODES) {
            int node = id - C_BUY0;
            sel = node;
            confirmReset = false;
            if (Meta.nodeMax(node) <= levelOf(lastLevels, node, Meta.nodeMax(node))) {
                // 满级：不再触发购买
                denyId = node;
                denyNanos = System.nanoTime();
                return -1;
            }
            if (lastDust < Meta.costForLevel(levelOf(lastLevels, node, Meta.nodeMax(node)))) {
                denyId = node;
                denyNanos = System.nanoTime();
            }
            return Ui.A_NODE + node;
        }
        // 节点卡片：选中 + 尝试购买（满级只选中，不购买）
        if (id >= C_NODE0 && id < C_NODE0 + NODES) {
            int node = id - C_NODE0;
            sel = node;
            confirmReset = false;
            int max = Meta.nodeMax(node);
            int level = levelOf(lastLevels, node, max);
            if (level >= max) {
                denyId = node;
                denyNanos = System.nanoTime();
                return -1;
            }
            if (lastDust < Meta.costForLevel(level)) {
                denyId = node;
                denyNanos = System.nanoTime();
            }
            return Ui.A_NODE + node;
        }
        return -1;
    }

    // ─────────────────────────── 数据 ───────────────────────────
    /** 读取节点等级并钳制到 [0, max]；数组不足 32 时按 0 处理。 */
    private static int levelOf(int[] lv, int id, int max) {
        if (lv == null || id < 0 || id >= lv.length) return 0;
        int v = lv[id];
        if (v < 0) return 0;
        return Math.min(v, Math.max(0, max));
    }

    private static int litNodes(int[] lv) {
        int n = 0;
        for (int i = 0; i < NODES; i++) if (levelOf(lv, i, Meta.nodeMax(i)) > 0) n++;
        return n;
    }

    /**
     * 把 {@code Meta.NODE_DESC} 里的首个数字乘以等级，得到"当前累计"文案。
     * <p>例：「最大生命 +6」×3 →「最大生命 +18」；「承受伤害 -3%」×5 →「承受伤害 -15%」。
     * 没有数字的说明原样返回。
     */
    private static String scaleDesc(String desc, int level) {
        if (desc == null || desc.isEmpty()) return "";
        int n = desc.length(), i = 0;
        while (i < n && !isDigit(desc.charAt(i))) i++;
        if (i >= n) return desc;
        int j = i;
        while (j < n && isDigit(desc.charAt(j))) j++;
        long value;
        try {
            value = Long.parseLong(desc.substring(i, j));
        } catch (Throwable t) {
            return desc;
        }
        long scaled = value * Math.max(0, level);
        return desc.substring(0, i) + scaled + desc.substring(j);
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /** 图标形状：由节点下标确定性映射到 Ui 的 16 种形状。 */
    private static int iconOf(int id) {
        switch (Meta.nodeKey(id)) {
            case "hp": return 3;
            case "armor": return 13;
            case "regen": return 15;
            case "dash": return 12;
            case "speed": return 11;
            case "damage": return 2;
            case "crit": return 6;
            case "atkspeed": return 7;
            case "range": return 14;
            case "coin": return 0;
            case "xp": return 9;
            case "magnet": return 1;
            case "coins": return 0;
            case "rerolls": return 7;
            case "slots": return 3;
            case "shop": return 4;
            case "event": return 10;
            case "boss": return 12;
            case "revive": return 6;
            case "startrelic": return 8;
            case "pact": return 14;
            case "mark": return 5;
            case "dust": return 9;
            default: break;
        }
        if (Meta.nodeKey(id).startsWith("school")) return 8;
        if (Meta.nodeKey(id).startsWith("codex")) return 8;
        return Math.floorMod(id, 16);
    }

    private static int groupColor(int g) {
        switch (Math.floorMod(g, Meta.GROUPS)) {
            case 0: return Ui.MINT;    // 生存
            case 1: return Ui.RED;     // 输出
            case 2: return Ui.GOLD;    // 经济
            case 3: return Ui.VIOLET;  // 流派专精
            case 4: return Ui.BLUE;    // 解锁
            default: return Ui.PINK;   // 特殊
        }
    }

    private static String groupSub(int g) {
        switch (Math.floorMod(g, Meta.GROUPS)) {
            case 0: return "SURVIVAL";
            case 1: return "OFFENSE";
            case 2: return "ECONOMY";
            case 3: return "MASTERY";
            case 4: return "UNLOCK";
            default: return "SPECIAL";
        }
    }

    private static String format(int v) {
        int n = Math.max(0, v);
        if (n < 10000) return String.valueOf(n);
        return (n / 1000) + "," + String.format(java.util.Locale.US, "%03d", n % 1000);
    }
}
