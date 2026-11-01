package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;
import com.dilao.core.*;

/**
 * 星图选路界面（1440x810）。
 *
 * <p>左侧为竖排星轨（本章 3 个可选节点 + 已走过的历史），右侧为 3 张横向路线卡，
 * 底部为当前构筑速览。点击返回 {@code 1000 + option}，离开返回 {@link Ui#A_LEAVE}。
 *
 * <p><b>静态层烘焙</b>：本屏是全游戏最贵的整屏 UI（ABYSSPERF 实测 ui=21.1ms/帧，
 * 背景只有 0.2ms）。开销来自每帧重画约 60 段文字、10 个渐变圆角矩形、9 次光晕
 * 采样和 6 组流派徽记——而这些图元的输入（站点、路线、构筑）在两站之间完全不变。
 * 因此沿用 {@code GameView.drawArena} 里 {@code arenaCache} 的做法：
 * 把静态层烘焙进一张 Bitmap，每帧只画「位图 + 悬停高亮 + 当前节点呼吸环 + 箭头」。
 *
 * <p>静态 / 动态的分界见 {@link #drawStatic} 与 {@link #drawDynamic}；
 * 缓存失效键见 {@link #signature}。
 */
public final class MapScreen {
    public int hit = -1;

    /** 每帧绘制时的命中兜底：外部可注入指针以启用 hover。 */
    private float px = -1, py = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 贴缓存位图用：设备缩放不是 1:1 时双线性采样，避免最近邻放大把文字拉成锯齿。 */
    private final Paint cachePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path starPath = new Path();

    /** 房型强调色（下标与 Game.ROOM_* 一致）。 */
    public static final int[] ROOM_ACCENT = {
            0xffedf1e7, // 战斗 白
            0xffff91c8, // 精英 粉
            0xffc2a7ff, // 事件 紫
            0xffeac487, // 商店 金
            0xff9de7c1, // 营火 薄荷
            0xffefd97a, // 宝藏 黄
            0xfff18f8b, // 首领 红
            0xff93c4ff, // 挑战 蓝
            0xff7fd8d8, // 铁匠 青
            0xffb78cff  // 神龛 紫
    };
    private static final int[] ROOM_ICON = {0, 4, 2, 8, 9, 1, 3, 7, 5, 6};

    // ══════════════════════════════════════════════════════════════
    //  静态层缓存
    //
    //  覆盖面实测：标题条横跨 0..1440（全宽，无法裁窄）；所有图元的最低点
    //  是底栏 panel() 的 2px 投影 726+2+64=792，最高点是标题条 0。
    //  因此缓存位图取 1440x792 = 4.35MB，比整屏 1440x810 省 0.31MB；
    //  再往下裁会切到阴影，再窄会切到标题条。
    // ══════════════════════════════════════════════════════════════
    private static final int CACHE_X = 0, CACHE_Y = 0, CACHE_W = 1440, CACHE_H = 792;

    private Bitmap cache;
    private Canvas cacheCanvas;
    /** 当前缓存对应的键；{@link Integer#MIN_VALUE} 表示无缓存。 */
    private int cacheSig = Integer.MIN_VALUE;

    // ── 与静态层共享的几何常量（缓存、命中、动态层必须用同一套坐标）──
    private static final float HEADER_H = 168;
    private static final float LEAVE_X = 1252, LEAVE_Y = 34, LEAVE_W = 148, LEAVE_H = 92;

    private static final float ORBIT_X = 64, ORBIT_Y = 190, ORBIT_W = 452, ORBIT_H = 516;
    private static final float NODE_X = ORBIT_X + 76;
    private static final float[] NODE_Y = {ORBIT_Y + 176, ORBIT_Y + 306, ORBIT_Y + 436};

    private static final float CARD_X = 548, CARD_W = 828, CARD_H = 160;
    private static final float[] CARD_Y = {190, 368, 546};

    private static final float FOOT_X = 64, FOOT_Y = 726, FOOT_W = 1312, FOOT_H = 64;

    public MapScreen() {}

    public void pointer(float x, float y) { px = x; py = y; }

    /**
     * 返回导航/动作码：选路 {@code 1000 + index}，离开 {@link Ui#A_LEAVE}，其余 -1。
     */
    public int click(int id) {
        pressedId = id;
        pressedAt = System.nanoTime();
        if (id >= 1000 && id < 1003) return id;
        if (id == Ui.A_LEAVE) return Ui.A_LEAVE;
        return -1;
    }

    /**
     * hover 反馈：GameView 只派发离散点击，因此用「最近一次点击的 260ms」模拟按下高亮，
     * 同时兼容外部注入的指针坐标。
     */
    private int pressedId = -1;
    private long pressedAt;

    private boolean pressed(int id) {
        return id == pressedId && System.nanoTime() - pressedAt < 260_000_000L;
    }

    // ══════════════════════════════════════════════════════════════
    public void draw(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        if (c == null || ui == null || g == null) return;
        ui.clearHits();
        hit = -1;

        // 1) 静态层：命中缓存就直接贴图；创建失败时退回立即模式（绝不崩）
        ensureCache(ui, g, canLead);
        if (cache != null) c.drawBitmap(cache, CACHE_X, CACHE_Y, cachePaint);
        else drawStatic(c, ui, g, canLead);

        // 2) 命中表：必须每帧登记，绝不能烘焙进位图（否则指针永远命中不到）
        registerHits(ui, g, canLead);

        // 3) 动态层：悬停高亮 / 当前节点呼吸环 / 箭头。本层零分配。
        drawDynamic(c, ui, time, g, canLead);

        hit = ui.hitAt(px, py);
    }

    /** 释放缓存位图；调用点：GameView.destroy()（与 arenaCache.recycle() 同一处）。 */
    public void dispose() {
        dropCache();
    }

    // ══════════════════════════════════════════════════════════════
    //  缓存管理
    // ══════════════════════════════════════════════════════════════

    /** 键变化才重建；OOM / recycle 异常与 drawArena 一样吞掉并降级。 */
    private void ensureCache(Ui ui, Game g, boolean canLead) {
        int sig = signature(g, canLead);
        if (cache != null && sig == cacheSig) return;
        dropCache();
        try {
            Bitmap bmp = Bitmap.createBitmap(CACHE_W, CACHE_H, Bitmap.Config.ARGB_8888);
            if (cacheCanvas == null) cacheCanvas = new Canvas();
            cacheCanvas.setBitmap(bmp);
            drawStatic(cacheCanvas, ui, g, canLead);
            cache = bmp;
            cacheSig = sig;
        } catch (Throwable t) {
            // 与 GameView.drawArena 一致：建不出来就放弃缓存，next frame 走立即模式
            dropCache();
        }
    }

    private void dropCache() {
        if (cacheCanvas != null) {
            try { cacheCanvas.setBitmap(null); } catch (Throwable ignored) {}
        }
        if (cache != null) {
            try { cache.recycle(); } catch (Throwable ignored) {}
        }
        cache = null;
        cacheSig = Integer.MIN_VALUE;
    }

    /**
     * 静态层缓存键：全部由 int / boolean 原语算出，不分配任何对象。
     *
     * <p>键里每一个字段都对应静态层的一处像素，漏掉任何一个都会让画面停在旧状态：
     * <ul>
     *   <li>{@code roomsVisited}：星轨「已行 N 站」、历史点窗口范围、标题「第 N 站」、
     *       三个节点的变异词缀 {@code modifierAt(depth, i)}；</li>
     *   <li>{@code routeCount}：决定第 2/3 个节点是否「锁定」、第 3 张卡是否画
     *       「本层已无可选路线」；</li>
     *   <li>{@code routeOptions[0..2]}：节点与卡片的房型、强调色、图标、副标题、奖励文案；</li>
     *   <li>{@code biome}：标题区生物群系名与副标题；</li>
     *   <li>{@code mirror}：镜像开局会翻转整屏语义，必须让缓存失效（契约要求）；</li>
     *   <li>{@code mode}：模式名，以及 {@code modifierAt()} 在 BOSS_RUSH 下恒返回 0；</li>
     *   <li>{@code floor}：右上「第 N 章」与风险星级 {@code riskOf(room, floor)}；</li>
     *   <li>{@code roomsTotal}：标题「第 x / y 站」的分母；</li>
     *   <li>{@code contract}：右上契约名；</li>
     *   <li>{@code coins}：右上与底栏的「星尘」；</li>
     *   <li>{@code level}：底栏「等级 N」；</li>
     *   <li>{@code canLead}：节点/卡片的可用色与锁定态、底部「队长正在决定路线」提示；</li>
     *   <li>{@code seed}：{@code modifierAt()} 的异变词缀由 seed 派生；</li>
     *   <li>{@code role}：底栏职业名；</li>
     *   <li>{@code relics[i]}：底栏「遗物 N」与 6 组「流派共鸣 n/3」全部由遗物数组推导；</li>
     *   <li>{@code history[start..visited)}：星轨历史点的颜色（每个点取一个房型）。</li>
     * </ul>
     *
     * <p><b>刻意不进键</b>：{@code pressedId} / 指针坐标 / {@code hit}。
     * 它们只影响悬停高亮，而悬停高亮 100% 画在动态层里；若写进键，
     * 每次点击都要重建一张 4.3MB 位图，比不缓存还慢。
     */
    private static int signature(Game g, boolean canLead) {
        int k = 0x811c9dc5;                       // FNV-1a 偏移基
        k = mix(k, g.roomsVisited);
        k = mix(k, g.routeCount);
        k = mix(k, g.routeOptions[0]);
        k = mix(k, g.routeOptions[1]);
        k = mix(k, g.routeOptions[2]);
        k = mix(k, g.biome);
        k = mix(k, g.mirror ? 1 : 2);
        k = mix(k, g.mode);
        k = mix(k, g.floor);
        k = mix(k, g.roomsTotal);
        k = mix(k, g.contract);
        k = mix(k, g.coins);
        k = mix(k, g.level);
        k = mix(k, canLead ? 1 : 2);
        k = mix(k, (int) g.seed);
        k = mix(k, (int) (g.seed >>> 32));

        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        k = mix(k, p == null ? -1 : p.role);
        if (p != null) {
            // relics[] 同时决定 relicCount 与 schoolCount，逐项折叠即可完整覆盖
            for (int i = 0; i < p.relics.length; i++) k = mix(k, p.relics[i]);
        }

        // 历史点只取可见窗口（最多 8 个），避免为 256 项历史做无谓的哈希
        int visited = Math.max(0, Math.min(g.roomsVisited, 24));
        int start = Math.max(0, visited - 8);
        k = mix(k, visited);
        for (int i = start; i < visited; i++) k = mix(k, g.history[i % g.history.length]);
        return k;
    }

    private static int mix(int k, int v) {
        return (k ^ v) * 0x01000193;              // FNV-1a 素数
    }

    // ══════════════════════════════════════════════════════════════
    //  静态层：只有 (站点 / 路线 / 构筑 / canLead) 变化时才重画
    // ══════════════════════════════════════════════════════════════
    private void drawStatic(Canvas c, Ui ui, Game g, boolean canLead) {
        drawHeader(c, ui, g, canLead);
        drawOrbit(c, ui, g, canLead);
        drawCards(c, ui, g, canLead);
        drawFooter(c, ui, g);
    }

    // ── 顶部标题 ────────────────────────────────────────────────
    private void drawHeader(Canvas c, Ui ui, Game g, boolean canLead) {
        ui.box(c, 0, 0, 1440, HEADER_H, 0, 0xCC0b141b);

        int mode = Math.max(0, Math.min(3, g.mode));
        int biome = Math.max(0, Math.min(Content.BIOME_NAMES.length - 1, g.biome));
        int station = Math.min(g.roomsTotal, g.roomsVisited + 1);

        ui.text(c, Content.MODE_NAMES[mode] + "   ·   星图选路", 64, 42, 13, ui.alpha(Ui.MUTED, 235), false);
        ui.box(c, 64, 54, 54, 3, 1.5f, ui.alpha(ROOM_ACCENT[7], 220));
        ui.title(c, Content.BIOME_NAMES[biome], 64, 108, 44);
        ui.text(c, Content.BIOME_SUB[biome] + "   ·   第 " + station + " / " + g.roomsTotal + " 站",
                64, 136, 12.5f, ui.alpha(Ui.MUTED, 220), false);

        // 右上：章节 / 契约 / 星尘。右边界让开「离开远征」按钮（x 1252~1400）
        float rx = 1226;
        ui.right(c, "第 " + g.floor + " 章   ·   " + contractName(g), rx, 46, 13, ui.alpha(Ui.WHITE, 220), false);
        ui.right(c, "星尘 " + Math.max(0, g.coins), rx, 78, 20, Ui.GOLD, true);
        ui.right(c, canLead ? "选择一条路线继续下潜" : "等待队长选择路线", rx, 108, 12.5f,
                canLead ? ui.alpha(Ui.MUTED, 220) : Ui.BLUE, false);

        // 返回主菜单：只烘焙常态；hover 态由动态层叠加，避免点一下就重建位图
        ui.gradientRect(c, LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H, 14, 0xD914232C, 0xD90B141B);
        ui.outline(c, LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H, 14, ui.alpha(Ui.EDGE, 220));
        ui.center(c, "离开远征", LEAVE_X + LEAVE_W * .5f, LEAVE_Y + 38, 16, Ui.WHITE, true);
        ui.centerSmall(c, "MAIN MENU", LEAVE_X + LEAVE_W * .5f, LEAVE_Y + 64, 9.5f, ui.alpha(Ui.MUTED, 200));
    }

    private static String contractName(Game g) {
        int i = Math.max(0, Math.min(Balance.CONTRACTS.length - 1, g.contract));
        return Balance.CONTRACTS[i] + "契约";
    }

    // ── 左：竖排星轨 ────────────────────────────────────────────
    private void drawOrbit(Canvas c, Ui ui, Game g, boolean canLead) {
        float x = ORBIT_X, y = ORBIT_Y, w = ORBIT_W, h = ORBIT_H;
        ui.panel(c, x, y, w, h, 18, 0x99101c23, ui.alpha(Ui.EDGE, 190));
        ui.text(c, "星轨 · 本章路径", x + 24, y + 38, 16, Ui.WHITE, true);
        ui.right(c, "已行 " + Math.max(0, g.roomsVisited) + " 站", x + w - 24, y + 38, 12,
                ui.alpha(Ui.MUTED, 210), false);
        ui.divider(c, x + 20, y + 54, w - 40);

        drawHistory(c, ui, g, x + 24, y + 78, w - 48);

        // 三个节点：纵排（节点本体是静态的；呼吸环在动态层叠加）
        float nx = NODE_X;
        int count = Math.max(1, Math.min(3, g.routeCount));
        for (int i = 0; i < 3; i++) {
            boolean valid = i < count;
            int room = valid ? g.routeOptions[i] : g.routeOptions[Math.max(0, count - 1)];
            int accent = ROOM_ACCENT[clampRoom(room)];
            boolean usable = valid && canLead;

            // 连接线
            if (i > 0) {
                ui.line(c, nx, NODE_Y[i - 1] + 46, nx, NODE_Y[i] - 46, ui.alpha(Ui.EDGE, usable ? 220 : 120), 3f);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(usable ? 4f : 2.5f);
            paint.setColor(ui.alpha(accent, usable ? 200 : 90));
            c.drawCircle(nx, NODE_Y[i], 42, paint);
            ui.circle(c, nx, NODE_Y[i], 36, 0xCC0b141b);
            ui.glow(c, nx, NODE_Y[i], usable ? 62 : 44, ui.alpha(accent, usable ? 40 : 16));
            ui.icon(c, nx, NODE_Y[i], 30, ROOM_ICON[clampRoom(room)], ui.alpha(accent, usable ? 235 : 120));

            ui.text(c, Content.ROOM_TYPES[clampRoom(room)], nx + 66, NODE_Y[i] - 8, 20,
                    usable ? Ui.WHITE : ui.alpha(Ui.WHITE, 130), true);
            ui.text(c, Content.ROOM_SUBTITLE[clampRoom(room)], nx + 66, NODE_Y[i] + 12, 9.5f,
                    ui.alpha(Ui.MUTED, 170), false);
            String payoff = Balance.ROOM_PAYOFF[clampRoom(room)];
            ui.text(c, payoff, nx + 66, NODE_Y[i] + 34, ui.fit(payoff, w - 160, 12, 9.5f),
                    usable ? ui.alpha(Ui.MUTED, 235) : ui.alpha(Ui.MUTED, 120), false);

            if (!usable) {
                ui.pill(c, x + w - 92, NODE_Y[i] - 46, 62, "锁定", ui.alpha(Ui.MUTED, 200));
            }
        }

        if (!canLead) {
            ui.text(c, "队长正在决定路线，你的选择会被同步", x + 24, y + h - 16, 11.5f, Ui.BLUE, false);
        }
    }

    private void drawHistory(Canvas c, Ui ui, Game g, float x, float y, float w) {
        ui.text(c, "历史", x, y + 4, 10.5f, ui.alpha(Ui.MUTED, 180), false);
        int visited = Math.max(0, Math.min(g.roomsVisited, 24));
        int start = Math.max(0, visited - 8);
        float cx = x + 44, step = Math.min(38f, (w - 52) / 8f);
        ui.line(c, cx, y - 2, cx + step * Math.max(0, Math.min(7, visited - 1 - start)), y - 2,
                0x33EDF1E7, 1.5f);
        for (int i = start; i < visited; i++) {
            int t = (int) ((i * 2654435761L) >>> 4) & 7;
            int room = g.history[i % g.history.length];
            int accent = ROOM_ACCENT[clampRoom(room)];
            float dx = cx + (i - start) * step;
            ui.circle(c, dx, y - 2, 9, ui.alpha(accent, 210));
            ui.circle(c, dx, y - 2, 4, 0xCC0b141b);
            if (t < 0) ui.circle(c, dx, y - 2, 1, accent);
        }
        if (visited == 0) {
            ui.text(c, "尚未出发", cx, y + 4, 11.5f, ui.alpha(Ui.MUTED, 170), false);
        }
    }

    // ── 右：三张路线卡 ──────────────────────────────────────────
    private void drawCards(Canvas c, Ui ui, Game g, boolean canLead) {
        float x = CARD_X, w = CARD_W, h = CARD_H;
        int count = Math.max(1, Math.min(3, g.routeCount));

        ui.text(c, "可 选 路 线", x + 4, 178, 12, ui.alpha(Ui.MUTED, 210), false);
        ui.right(c, canLead ? "点击或按 1 / 2 / 3 选择" : "观战模式", x + w, 178, 12,
                ui.alpha(Ui.MUTED, 190), false);

        for (int i = 0; i < 3; i++) {
            boolean valid = i < count;
            int room = valid ? g.routeOptions[i] : 0;
            int accent = ROOM_ACCENT[clampRoom(room)];
            float y = CARD_Y[i];
            boolean usable = valid && canLead;

            if (!valid) {
                ui.panel(c, x, y, w, h, 16, 0x66101c23, ui.alpha(Ui.EDGE, 90));
                ui.center(c, "本层已无可选路线", x + w * .5f, y + h * .5f, 14, ui.alpha(Ui.MUTED, 120), false);
                continue;
            }

            // 卡片本体只烘焙常态（hover=false）；hover 态由动态层叠加
            ui.card(c, x, y, w, h, accent, false, false);

            // 左侧图标块
            ui.circle(c, x + 58, y + h * .5f, 40, 0xCC0b141b);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(ui.alpha(accent, usable ? 210 : 100));
            c.drawCircle(x + 58, y + h * .5f, 40, paint);
            ui.icon(c, x + 58, y + h * .5f, 30, ROOM_ICON[clampRoom(room)], ui.alpha(accent, usable ? 235 : 120));

            // 主标题 / 副标题
            float tx = x + 118;
            ui.text(c, Content.ROOM_TYPES[clampRoom(room)], tx, y + 48, 27,
                    usable ? Ui.WHITE : ui.alpha(Ui.WHITE, 140), true);
            ui.text(c, Content.ROOM_SUBTITLE[clampRoom(room)], tx, y + 70, 10, ui.alpha(Ui.MUTED, 175), false);

            // 变异词缀
            int mod = g.modifierAt(g.roomsVisited, i);
            int modIndex = Math.max(0, Math.min(Balance.MODIFIERS.length - 1, mod));
            float mx = tx + 158;
            if (mod > 0) {
                String ms = "异变 · " + Balance.MODIFIERS[modIndex];
                float mw = ui.measure(ms, 11, true) + 22;
                ui.pill(c, mx, y + 30, mw, ms, Ui.PINK);
                String hint = Balance.MODIFIER_HINTS[modIndex];
                ui.text(c, hint, mx, y + 80, ui.fit(hint, w - 132 - 158 - 30, 11.5f, 9.5f),
                        ui.alpha(Ui.MUTED, usable ? 230 : 140), false);
            } else {
                ui.pill(c, mx, y + 30, 74, "无异变", Ui.MINT);
                String hint = Balance.MODIFIER_HINTS[0];
                ui.text(c, hint, mx, y + 80, ui.fit(hint, w - 320, 11.5f, 9.5f),
                        ui.alpha(Ui.MUTED, usable ? 230 : 140), false);
            }

            // 奖励
            String payoff = Balance.ROOM_PAYOFF[clampRoom(room)];
            ui.text(c, "预估奖励", tx, y + 112, 10.5f, ui.alpha(Ui.MUTED, 185), false);
            ui.text(c, payoff, tx + 66, y + 112, ui.fit(payoff, 300, 13, 10),
                    usable ? Ui.GOLD : ui.alpha(Ui.GOLD, 120), false);

            // 风险 1-5 星
            int risk = riskOf(room, g.floor);
            ui.text(c, "风险", tx, y + 138, 10.5f, ui.alpha(Ui.MUTED, 185), false);
            for (int s = 0; s < 5; s++) {
                int sc = s < risk ? (risk >= 4 ? Ui.RED : risk >= 3 ? Ui.GOLD : Ui.MINT) : 0x33EDF1E7;
                star(c, tx + 62 + s * 20, y + 132, 7.5f, ui.alpha(sc, usable ? 255 : 120));
            }
            String hint = Content.ROOM_HINTS[clampRoom(room)];
            ui.text(c, hint, x + w - 56 - ui.measure(hint, 10.5f, false), y + 138,
                    ui.fit(hint, 230, 10.5f, 8.5f), ui.alpha(Ui.MUTED, usable ? 225 : 130), false);

            // 箭头不烘焙：它随 hover 改变尺寸与颜色，由动态层每帧绘制

            if (!usable && canLead) {
                ui.locked(c, x + w - 190, y + 14, 150, 34, "不可选");
            }
            if (!usable && !canLead) {
                ui.locked(c, x + w - 190, y + 14, 150, 34, "等待队长");
            }
        }
    }

    // ── 底部构筑速览 ────────────────────────────────────────────
    private void drawFooter(Canvas c, Ui ui, Game g) {
        float x = FOOT_X, y = FOOT_Y, w = FOOT_W, h = FOOT_H;
        ui.panel(c, x, y, w, h, 14, 0x99101c23, ui.alpha(Ui.EDGE, 170));

        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        String role = p == null ? "未选择" : Content.ROLES[Math.max(0, Math.min(Content.ROLES.length - 1, p.role))];
        int relics = p == null ? 0 : g.relicCount(p);
        int level = Math.max(1, g.level);

        ui.text(c, role, x + 22, y + 38, 15, Ui.WHITE, true);
        ui.text(c, "等级 " + level, x + 118, y + 38, 12.5f, ui.alpha(Ui.MUTED, 225), false);
        ui.text(c, "遗物 " + relics, x + 206, y + 38, 12.5f, Ui.GOLD, true);

        // 流派共鸣：6 组，每组固定 118 宽（图标 16 + 名字 2 字 + 「n/3」），组间 12 空隙
        float sx = x + 296;
        ui.text(c, "流派共鸣", sx, y + 38, 11.5f, ui.alpha(Ui.MUTED, 205), false);
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            int n = p == null ? 0 : g.schoolCount(p, s);
            float cellX = sx + 78 + s * 118;
            int color = Content.SCHOOL_COLOR[s];
            ui.circle(c, cellX, y + 32, 11, ui.alpha(color, n >= 3 ? 210 : 40));
            ui.schoolIcon(c, cellX, y + 32, 15, s);
            ui.text(c, Content.SCHOOL_NAMES[s], cellX + 17, y + 37, 11.5f,
                    n >= 3 ? Ui.WHITE : ui.alpha(Ui.MUTED, 210), n >= 3);
            ui.text(c, n + "/3", cellX + 17 + ui.measure(Content.SCHOOL_NAMES[s], 11.5f, n >= 3) + 6,
                    y + 37, 10.5f, n >= 3 ? Ui.MINT : ui.alpha(Ui.MUTED, 170), false);
        }

        ui.right(c, "星尘 " + Math.max(0, g.coins), x + w - 22, y + 38, 14, Ui.GOLD, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  每帧路径（零分配）：命中表 + 动态层
    // ══════════════════════════════════════════════════════════════

    /**
     * 命中矩形与静态层坐标一一对应。顺序必须保持「离开按钮 → 星轨节点 → 路线卡」，
     * 因为 {@link Ui#hitAt} 在纯旧式热区时按「后注册者优先」裁决。
     */
    private void registerHits(Ui ui, Game g, boolean canLead) {
        ui.hit(Ui.A_LEAVE, LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H);

        int count = Math.max(1, Math.min(3, g.routeCount));
        for (int i = 0; i < 3; i++) {
            if (i < count && canLead) {
                ui.hit(1000 + i, NODE_X - 74, NODE_Y[i] - 46, ORBIT_W - 48, 92);
            }
        }
        for (int i = 0; i < 3; i++) {
            if (i < count && canLead) {
                ui.hit(1000 + i, CARD_X, CARD_Y[i], CARD_W, CARD_H);
            }
        }
    }

    /**
     * 动态层：所有随时间 / 指针变化的东西。
     * <p>只做叠加，不重画静态内容，因此不需要重建位图：
     * <ol>
     *   <li>当前节点呼吸环（{@code sin(time)} 半径 + 透明度，静态层里的节点本体不动）；</li>
     *   <li>「离开远征」悬停：半透明提亮 + 红描边（常态底图来自缓存）；</li>
     *   <li>路线卡悬停：主色调提亮 + 主色描边 + 左侧色条（常态卡面来自缓存）；</li>
     *   <li>三支箭头：每帧绘制，hover 时放大并换成房型主色。</li>
     * </ol>
     */
    private void drawDynamic(Canvas c, Ui ui, float time, Game g, boolean canLead) {
        // 1) 当前节点呼吸环：半径 46~54 落在静态节点描边（r=42、宽 4 → 40~44）之外，
        //    只做外圈光晕叠加，不覆盖缓存里的图标与文字
        int leadAccent = ROOM_ACCENT[clampRoom(g.routeOptions[0])];
        float p = .5f + .5f * (float) Math.sin(time * 2.2f);
        ui.ring(c, NODE_X, NODE_Y[0], 46f + p * 8f,
                ui.alpha(leadAccent, 35 + (int) (p * 75f)), 2.5f);
        if (canLead) {
            ui.glow(c, NODE_X, NODE_Y[0], 54f + p * 8f,
                    ui.alpha(leadAccent, 14 + (int) (p * 18f)));
        }

        // 2) 离开按钮悬停
        if (inside(LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H) || pressed(Ui.A_LEAVE)) {
            ui.box(c, LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H, 14, 0x332b1e22);
            ui.outline(c, LEAVE_X, LEAVE_Y, LEAVE_W, LEAVE_H, 14, ui.alpha(Ui.RED, 220));
        }

        // 3) 路线卡悬停 + 箭头
        int count = Math.max(1, Math.min(3, g.routeCount));
        for (int i = 0; i < 3; i++) {
            if (i >= count) continue;
            int accent = ROOM_ACCENT[clampRoom(g.routeOptions[i])];
            float y = CARD_Y[i];
            boolean hover = canLead && (inside(CARD_X, y, CARD_W, CARD_H) || pressed(1000 + i));
            if (hover) {
                ui.box(c, CARD_X, y, CARD_W, CARD_H, 14, ui.alpha(accent, 20));
                ui.outline(c, CARD_X, y, CARD_W, CARD_H, 14, ui.alpha(accent, 150));
                ui.box(c, CARD_X + 1, y + 1, 5, CARD_H - 2, 2.5f, ui.alpha(accent, 200));
            }
            arrow(c, ui, CARD_X + CARD_W - 34, y + CARD_H * .5f, hover ? 20 : 17,
                    canLead ? (hover ? accent : ui.alpha(Ui.MUTED, 190)) : ui.alpha(Ui.MUTED, 90));
        }
    }

    // ── 小工具 ──────────────────────────────────────────────────
    private static int clampRoom(int room) {
        return Math.max(0, Math.min(ROOM_ACCENT.length - 1, room));
    }

    private static int riskOf(int room, int floor) {
        int base;
        switch (room) {
            case Game.ROOM_BOSS: base = 5; break;
            case Game.ROOM_ELITE: base = 4; break;
            case Game.ROOM_CHALLENGE: base = 4; break;
            case Game.ROOM_TREASURE: base = 3; break;
            case Game.ROOM_EVENT: base = 2; break;
            case Game.ROOM_SHRINE: base = 2; break;
            case Game.ROOM_COMBAT: base = 2; break;
            default: base = 1; break;
        }
        return Math.max(1, Math.min(5, base + Math.max(0, floor - 1) / 2));
    }

    private boolean inside(float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void star(Canvas c, float x, float y, float r, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        Path path = starPath;
        path.reset();
        for (int i = 0; i < 10; i++) {
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float rr = (i & 1) == 0 ? r : r * .46f;
            float sx = x + (float) Math.cos(a) * rr;
            float sy = y + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(sx, sy); else path.lineTo(sx, sy);
        }
        path.close();
        c.drawPath(path, paint);
    }

    private void arrow(Canvas c, Ui ui, float x, float y, float r, int color) {
        ui.line(c, x - r * .7f, y, x + r * .5f, y, color, 2.6f);
        ui.line(c, x + r * .5f, y, x - r * .1f, y - r * .6f, color, 2.6f);
        ui.line(c, x + r * .5f, y, x - r * .1f, y + r * .6f, color, 2.6f);
    }
}
