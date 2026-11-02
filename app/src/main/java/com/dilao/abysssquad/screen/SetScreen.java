package com.dilao.abysssquad.screen;

import android.graphics.*;
import com.dilao.abysssquad.Ui;

/**
 * 设置：音频 / 画面与触感 / 无障碍 / 玩法说明 / 存档。
 *
 * <p>布局（逻辑画布 1440×810，全部坐标写死，便于 QA 脚本静态核算）：</p>
 * <pre>
 *   顶栏             y   0 .. 86
 *   左栏 音频        x  64 .. 676   y 104 .. 336
 *   左栏 画面与触感  x  64 .. 676   y 348 .. 658
 *   右栏 无障碍      x 700 ..1376   y 104 .. 460
 *   右栏 存档与关于  x 700 ..1376   y 472 .. 658
 *   底部 玩法说明    x  64 ..1376   y 670 .. 760
 * </pre>
 *
 * <p>三栏两组 + 底部通栏，比原来的「左两栏 + 右两栏」多容纳 4 个控件（3 滑条 + 1 开关）
 * 而不加滚动。</p>
 *
 * <p>动作码：开关类返回 -1 并就地修改 opts；返回 {@link Ui#A_BACK}；
 * 重置存档 {@link Ui#A_CONFIRM}。</p>
 */
public final class SetScreen {

    // ── opts 下标（必须与 GameView.settingsOpts() 逐项对应，任何一侧插队都会全体错位）──
    // 0 sound · 1 music · 2 volume · 3 vibrate · 4 shake · 5 quality
    // 6 showFps · 7 lang · 8 flash · 9 particles · 10 numbers · 11 leftHanded · 12 oneHand
    private static final int O_SOUND = 0, O_MUSIC = 1, O_VOLUME = 2, O_VIBRATE = 3,
            O_SHAKE = 4, O_QUALITY = 5, O_FPS = 6, O_LANG = 7,
            O_FLASH = 8, O_PARTICLES = 9, O_NUMBERS = 10, O_LEFT = 11, O_ONE = 12;
    /** opts[] 长度。改这里必须同步改 GameView.settingsOpts()。 */
    private static final int OPT_LEN = 13;

    // ── 控件 id ──
    private static final int C_BACK = 1;
    private static final int C_SOUND = 10, C_MUSIC = 11, C_VOL = 12, C_VIBRATE = 13,
            C_SHAKE = 14, C_Q_LOW = 15, C_Q_MID = 16, C_Q_HIGH = 17, C_FPS = 18, C_LANG = 19,
            C_FLASH = 20, C_PARTICLES = 21, C_NUMBERS = 22, C_LEFT = 23, C_ONE = 24;
    private static final int C_RESET = 30, C_RESET_YES = 31, C_RESET_NO = 32;

    private boolean confirmReset = false;
    public int hit = -1;

    public SetScreen() {
    }

    /**
     * @param opts 13 项，见类注释的下标表；长度不足时用内置默认值绘制（只读不影响游戏）
     * @param fps  当前帧率（showFps 打开时显示）
     */
    public void draw(Canvas c, Ui ui, float time, int[] opts, float fps) {
        ui.clearHits();
        hit = -1;
        sliderGeoN = 0;
        if (c == null || ui == null) return;

        boolean fresh = opts == null || opts.length < OPT_LEN;
        int[] o = fresh ? new int[OPT_LEN] : opts;
        if (fresh) {
            // 与 GameView.loadSettings() 的默认值保持一致，避免首帧滑条全 0
            o[O_SOUND] = 1;
            o[O_MUSIC] = 1;
            o[O_VOLUME] = 70;
            o[O_VIBRATE] = 1;
            o[O_SHAKE] = 60;
            o[O_QUALITY] = 1;
            o[O_FPS] = 0;
            o[O_LANG] = 0;
            o[O_FLASH] = 60;
            o[O_PARTICLES] = 100;
            o[O_NUMBERS] = 100;
            o[O_LEFT] = 0;
            o[O_ONE] = 0;
        }

        backdrop(c, ui, time);
        header(c, ui, time, o, fps);

        // 左栏：音频 / 画面与触感
        audioPanel(c, ui, time, o);
        visualPanel(c, ui, time, o);
        // 右栏：无障碍（4 滑条 + 左撇子）/ 存档
        accessPanel(c, ui, time, o);
        archivePanel(c, ui, time);
        // 底部通栏：玩法说明（纯文字，无命中区）
        guidePanel(c, ui, time, o);
    }

    // ─────────────────────────── 背景 / 顶栏 ───────────────────────────
    private void backdrop(Canvas c, Ui ui, float time) {
        ui.glow(c, 700, 260, 540, 0x0ca8b8e8);
        ui.glow(c, 200, 660, 360, 0x0a9de7c1);
        for (int i = 0; i < 20; i++) {
            float x = (i * 181 + 50) % 1400;
            float y = 120 + ((i * 107 + time * 5f) % 650);
            ui.circle(c, x, y, .8f + (i % 3) * .35f, ui.alpha(Ui.BLUE, 16 + (i % 4) * 9));
        }
    }

    private void header(Canvas c, Ui ui, float time, int[] o, float fps) {
        ui.diamond(c, 78, 44, 13, Ui.BLUE);
        ui.text(c, "设置", 102, 53, 27, Ui.WHITE, true);
        ui.text(c, "SETTINGS", 176, 52, 12, ui.alpha(Ui.MUTED, 190), false);

        if (o[O_FPS] != 0) {
            int color = fps >= 55 ? Ui.MINT : fps >= 35 ? Ui.GOLD : Ui.RED;
            ui.pill(c, 1090, 30, 120, String.format(java.util.Locale.US, "%.0f FPS", fps), color);
        }
        ui.ghost(c, C_BACK, 1252, 24, 120, 46, "返回  ←");
        ui.divider(c, 64, 86, 1312);
    }

    // ─────────────────────────── 音频（x 64..676, y 104..336）───────────────────────────
    private void audioPanel(Canvas c, Ui ui, float time, int[] o) {
        final float x = 64, y = 104, w = 612, h = 232;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);
        sectionTitle(c, ui, x + 24, y + 18, "音频", "AUDIO");

        rowToggle(c, ui, C_SOUND, x + 24, y + 40, w - 48, "音效", "技能 / 受击 / 界面反馈", o[O_SOUND] != 0);
        rowToggle(c, ui, C_MUSIC, x + 24, y + 106, w - 48, "音乐", "远征背景音乐", o[O_MUSIC] != 0);

        // 音量：整行滑条（标题基线 y+186，滑轨 y+192..202，命中 y+174..220）
        sliderRow(c, ui, C_VOL, x + 24, y + 172, w - 48, "音量", "总输出，音乐随音量衰减", o[O_VOLUME], Ui.MINT);
    }

    // ────────────────────── 画面与触感（x 64..676, y 348..658）──────────────────────
    private void visualPanel(Canvas c, Ui ui, float time, int[] o) {
        final float x = 64, y = 348, w = 612, h = 310;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);
        sectionTitle(c, ui, x + 24, y + 18, "画面与触感", "VISUAL");

        // ① 震动开关（y+40..102）
        rowToggle(c, ui, C_VIBRATE, x + 24, y + 40, w - 48, "震动", "命中与受击时反馈", o[O_VIBRATE] != 0);

        // ② 画面特效三段控件：高 52 → 每段命中高 52-2*4 = 44（刚好达标）
        float qy = y + 108;
        ui.text(c, "画面特效", x + 24, qy + 31, 14, Ui.WHITE, true);
        String[] qLabels = {"低", "中", "高"};
        int[] qIds = {C_Q_LOW, C_Q_MID, C_Q_HIGH};
        int q = Math.max(0, Math.min(2, o[O_QUALITY]));
        ui.segmented(c, x + 128, qy, 240, 52, 3, q, qIds, qLabels);
        String[] qHints = {"粒子减半，适合低端机", "默认档位，平衡表现与性能", "全粒子 + 额外光效"};
        ui.text(c, qHints[q], x + 384, qy + 31, 12, ui.alpha(Ui.MUTED, 200), false);

        // ③ 显示帧率（y+168..230）
        rowToggle(c, ui, C_FPS, x + 24, y + 168, w - 48, "显示帧率", "画面右上角显示当前 FPS", o[O_FPS] != 0);

        // ④ 语言（y+236..298）
        rowToggle(c, ui, C_LANG, x + 24, y + 236, w - 48, "语言", "当前仅提供简体中文", o[O_LANG] != 0);
    }

    // ────────────────────── 无障碍（x 700..1376, y 104..460）──────────────────────
    private void accessPanel(Canvas c, Ui ui, float time, int[] o) {
        final float x = 700, y = 104, w = 676, h = 356;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);
        sectionTitle(c, ui, x + 24, y + 18, "无障碍与舒适度", "ACCESSIBILITY");

        // 4 个互相独立的 0..100 滑条，行距 56（命中 46 高，行间留 10px 不重叠）
        sliderRow(c, ui, C_SHAKE, x + 24, y + 42, w - 48, "镜头震动", "命中 / 受击时的画面抖动", o[O_SHAKE], Ui.GOLD);
        sliderRow(c, ui, C_FLASH, x + 24, y + 98, w - 48, "闪光强度", "全屏白闪的亮度", o[O_FLASH], Ui.BLUE);
        sliderRow(c, ui, C_PARTICLES, x + 24, y + 154, w - 48, "粒子密度", "特效粒子数量", o[O_PARTICLES], Ui.MINT);
        sliderRow(c, ui, C_NUMBERS, x + 24, y + 210, w - 48, "伤害数字密度", "同屏飘字上限", o[O_NUMBERS], Ui.VIOLET);

        // 双开关并排（y+264..326）：原左手布局与新增单手辅助互不覆盖。
        float half = (w - 60) * .5f;
        rowToggle(c, ui, C_LEFT, x + 24, y + 264, half, "左手布局",
                "镜像按钮与传统摇杆", o[O_LEFT] != 0);
        rowToggle(c, ui, C_ONE, x + 36 + half, y + 264, half, "单手作战",
                "浮动摇杆 + 自动技能", o[O_ONE] != 0);

        ui.text(c, "系统「动画时长缩放」为 0 时，震屏与闪光会自动归零。",
                x + 24, y + 344, 11.5f, ui.alpha(Ui.MUTED, 190), false);
    }

    // ────────────────────── 存档与关于（x 700..1376, y 472..658）──────────────────────
    private void archivePanel(Canvas c, Ui ui, float time) {
        final float x = 700, y = 472, w = 676, h = 186;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);
        sectionTitle(c, ui, x + 24, y + 18, "存档与关于", "ABOUT");

        ui.text(c, "重置存档会清空全部元进度", x + 24, y + 56, 13, Ui.WHITE, true);
        ui.text(c, "星尘、印记、节点、解锁与图鉴均会归零，且不可恢复。",
                x + 24, y + 78, 11.5f, ui.alpha(Ui.RED, 210), false);

        // 按钮带固定在 y+96..144，署名行固定在 y+168
        if (confirmReset) {
            ui.pill(c, x + 24, y + 100, 150, "确定要重置吗？", Ui.RED);
            ui.button(c, C_RESET_YES, x + w - 244, y + 96, 104, 48, "确定重置", false, true);
            ui.button(c, C_RESET_NO, x + w - 132, y + 96, 104, 48, "取消", false, true);
        } else {
            ui.ghost(c, C_RESET, x + w - 200, y + 96, 176, 48, "重置存档");
        }

        ui.text(c, "深渊小队 v2.8  ·  Canvas 2D 自绘", x + 24, y + 168, 11,
                ui.alpha(Ui.MUTED, 170), false);
        ui.right(c, "美术 / 文本 / 音效均为原创", x + w - 24, y + 168, 11,
                ui.alpha(Ui.MUTED, 150), false);
    }

    // ────────────────────── 玩法说明（底部通栏 x 64..1376, y 670..760）──────────────────────
    private void guidePanel(Canvas c, Ui ui, float time, int[] o) {
        final float x = 64, y = 670, w = 1312, h = 90;
        ui.panel(c, x, y, w, h, 18, 0xff101d24, Ui.EDGE);
        sectionTitle(c, ui, x + 24, y + 8, "玩法说明", "CONTROLS");

        String[][] tips = o[O_ONE] != 0 ? new String[][]{
                {"任意空白处拖动", "浮动摇杆移动"},
                {"松手即停", "始终自动瞄准射击"},
                {"技能自动释放", "附近出现敌人时触发"},
                {"终极自动释放", "首领或大规模敌群出现时"},
                {"闪避保留手动", "关键时刻仍可按闪避"},
                {"关闭单手辅助", "恢复双摇杆手动瞄准"}
        } : new String[][]{
                {"左半屏拖动", "移动，摇杆半径决定速度"},
                {"右半屏拖动", "手动瞄准，松手回自动"},
                {"自动攻击", "始终锁定最近敌人"},
                {"闪避按钮", "短距位移 + 无敌帧"},
                {"技能按钮", "职业主动技能，有冷却"},
                {"终结技能", "能量满后释放"}
        };
        float colW = (w - 48) / 3f;
        for (int i = 0; i < tips.length; i++) {
            float tx = x + 24 + (i % 3) * colW;
            float ty = y + 44 + (i / 3) * 26;
            ui.text(c, tips[i][0], tx, ty, 12.5f, Ui.WHITE, true);
            float nw = ui.measure(tips[i][0], 12.5f, true);
            ui.text(c, tips[i][1], tx + nw + 10, ty, 11.5f, ui.alpha(Ui.MUTED, 205), false);
        }
    }

    // ─────────────────────────── 复用小部件 ───────────────────────────
    private void sectionTitle(Canvas c, Ui ui, float x, float y, String cn, String en) {
        ui.text(c, cn, x, y + 12, 16, Ui.WHITE, true);
        float w = ui.measure(cn, 16, true);
        ui.small(c, en, x + w + 10, y + 12, 11f, ui.alpha(Ui.MUTED, 180));
    }

    /** 开关行：整行可点，命中矩形 = (x, y, w, 62)。 */
    private void rowToggle(Canvas c, Ui ui, int id, float x, float y, float w,
                           String name, String sub, boolean on) {
        ui.panel(c, x, y, w, 62, 12, 0xff16252d, 0);
        ui.text(c, name, x + 16, y + 26, 14.5f, Ui.WHITE, true);
        ui.text(c, sub, x + 16, y + 46, 11.5f, ui.alpha(Ui.MUTED, 205), false);
        toggle(c, ui, x + w - 84, y + 17, on);
        ui.hit(id, x, y, w, 62);
    }

    private void toggle(Canvas c, Ui ui, float x, float y, boolean on) {
        float w = 64, h = 30;
        int track = on ? ui.alpha(Ui.MINT, 70) : 0x33000000;
        ui.box(c, x, y, w, h, h / 2f, track);
        ui.stroke(c, x, y, w, h, h / 2f, on ? ui.alpha(Ui.MINT, 190) : ui.alpha(Ui.EDGE, 220), 1f);
        float kx = on ? x + w - h / 2f : x + h / 2f;
        ui.glow(c, kx, y + h / 2f, 16, ui.alpha(on ? Ui.MINT : Ui.MUTED, on ? 70 : 30));
        ui.circle(c, kx, y + h / 2f, h / 2f - 4, on ? Ui.MINT : ui.alpha(Ui.MUTED, 200));
    }

    /**
     * 整行滑条：标题 + 右对齐数值在上，滑轨占满行宽在下。
     * <p>命中矩形固定为 (x-12, y-18, w+24, 46)（= 滑轨 10px 高 + 上下 18px 手指余量）。</p>
     */
    private void sliderRow(Canvas c, Ui ui, int id, float x, float y, float w,
                           String name, String sub, int value, int color) {
        int v = Math.max(0, Math.min(100, value));
        ui.text(c, name, x, y + 14, 14, Ui.WHITE, true);
        float nw = ui.measure(name, 14, true);
        ui.text(c, sub, x + nw + 12, y + 14, 11.5f, ui.alpha(Ui.MUTED, 190), false);
        ui.right(c, v + "%", x + w, y + 14, 13, color, true);
        slider(c, ui, id, x, y + 20, w, v / 100f, color);
    }

    private void slider(Canvas c, Ui ui, int id, float x, float y, float w, float k, int color) {
        float h = 10;
        ui.box(c, x, y, w, h, h / 2f, 0xff1b2a33);
        float fw = Math.max(h, w * Math.max(0f, Math.min(1f, k)));
        ui.gradientRect(c, x, y, fw, h, h / 2f, ui.blend(color, 0xffffffff, .12f),
                ui.blend(color, 0xff000000, .16f));
        ui.stroke(c, x, y, w, h, h / 2f, 0x33ffffff, 1f);
        // 滑块
        float kx = x + w * Math.max(0f, Math.min(1f, k));
        ui.glow(c, kx, y + h / 2f, 18, ui.alpha(color, 90));
        ui.circle(c, kx, y + h / 2f, 9, Ui.WHITE);
        ui.circle(c, kx, y + h / 2f, 6, color);
        ui.hit(id, x - 12, y - 18, w + 24, h + 36);
        // 记下真实几何，供 GameView 把点击坐标换算成 0..100（见 sliderGeometry）
        rememberSlider(id, x, x + w);
    }

    /**
     * 最近一次 draw 时每条滑轨的几何：{x0, x1, id} 三元组。
     * <p>GameView 只拿到点击 id，没有坐标；没有这张表时
     * {@link #slideValue} 只能走「每次 +10」的兜底步进，滑条永远停在离散的 11 档、
     * 拖不到 37% 这种中间值。这里存一份绘制期几何，GameView 就能按比例换算。
     * <p>固定 5 条滑条 × 3 个 float，无分配、无锁（只在 UI 线程读写）。
     */
    private final float[] sliderGeo = new float[SLIDER_MAX * 3];
    private int sliderGeoN;
    private static final int SLIDER_MAX = 8;

    // ─────────────────────────── 点击 ───────────────────────────
    /**
     * 开关直接修改 opts 数组（GameView 持有同一引用）；
     * 滑条需要坐标，由 GameView 在调用前用 {@link #slideValue} 换算。
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
        if (id == C_RESET_YES) {
            confirmReset = false;
            return Ui.A_CONFIRM;
        }
        if (id == C_RESET_NO) {
            confirmReset = false;
            return -1;
        }
        return -1;
    }

    /** 开关类点击的就地取反；GameView 在 click() 返回 -1 后调用。 */
    public void toggleOption(int[] opts, int id) {
        if (opts == null || opts.length < OPT_LEN) return;
        switch (id) {
            case C_SOUND: opts[O_SOUND] = opts[O_SOUND] != 0 ? 0 : 1; break;
            case C_MUSIC: opts[O_MUSIC] = opts[O_MUSIC] != 0 ? 0 : 1; break;
            case C_VIBRATE: opts[O_VIBRATE] = opts[O_VIBRATE] != 0 ? 0 : 1; break;
            case C_FPS: opts[O_FPS] = opts[O_FPS] != 0 ? 0 : 1; break;
            case C_LANG: opts[O_LANG] = opts[O_LANG] != 0 ? 0 : 1; break;
            case C_LEFT: opts[O_LEFT] = opts[O_LEFT] != 0 ? 0 : 1; break;
            case C_ONE: opts[O_ONE] = opts[O_ONE] != 0 ? 0 : 1; break;
            case C_Q_LOW: opts[O_QUALITY] = 0; break;
            case C_Q_MID: opts[O_QUALITY] = 1; break;
            case C_Q_HIGH: opts[O_QUALITY] = 2; break;
            default: break;
        }
    }

    private void rememberSlider(int id, float x0, float x1) {
        if (sliderGeoN >= SLIDER_MAX) return;
        int i = sliderGeoN * 3;
        sliderGeo[i] = id;
        sliderGeo[i + 1] = x0;
        sliderGeo[i + 2] = x1;
        sliderGeoN++;
    }

    /**
     * 取某条滑轨的绘制几何：{@code out[0] = 左端 x}、{@code out[1] = 右端 x}。
     * <p>由 GameView 在收到滑条点击时调用，把触摸 x 换算成 0..100。
     *
     * @return false 表示本帧没有画过这条滑条（尚未 draw 或 id 不是滑条）
     */
    public boolean sliderGeometry(int id, float[] out) {
        if (out == null || out.length < 2) return false;
        for (int i = 0; i < sliderGeoN; i++) {
            int k = i * 3;
            if ((int) sliderGeo[k] == id) {
                out[0] = sliderGeo[k + 1];
                out[1] = sliderGeo[k + 2];
                return true;
            }
        }
        return false;
    }

    /**
     * 滑条点击换算。
     *
     * <p>GameView 第一版调用的是 {@code slideValue(opts, id, 0, 0, 1)}（没有传真实坐标），
     * 若按公式算 k=(0-0)/1=0，所有滑条会<b>永远被归零且拖不动</b>。因此这里双模式：</p>
     * <ul>
     *   <li>传入有效几何（totalW&gt;1 且 x 在区间内）→ 按真实位置换算 0..100；</li>
     *   <li>否则 → 每次 +10 步进（0,10,…,100 后回到 0），保证一定能调。</li>
     * </ul>
     * <p>GameView 现在会先取 {@link #sliderGeometry} 再把触摸 x 传进来，所以正常情况下
     * 走的是第一条路径：点哪就跳哪，可以拖到任意 1% 档位。</p>
     */
    public void slideValue(int[] opts, int id, float x, float totalX, float totalW) {
        if (opts == null || opts.length < OPT_LEN) return;
        int idx = sliderIndex(id);
        if (idx < 0) return;
        boolean validGeo = totalW > 1f && x >= totalX - 1 && x <= totalX + totalW + 1;
        if (validGeo) {
            float k = (x - totalX) / totalW;
            opts[idx] = Math.round(Math.max(0f, Math.min(1f, k)) * 100);
        } else {
            int next = opts[idx] + 10;
            if (next > 100) next = 0;
            opts[idx] = next;
        }
    }

    /** 滑条 id → opts 下标；非滑条返回 -1。 */
    private static int sliderIndex(int id) {
        switch (id) {
            case C_VOL: return O_VOLUME;
            case C_SHAKE: return O_SHAKE;
            case C_FLASH: return O_FLASH;
            case C_PARTICLES: return O_PARTICLES;
            case C_NUMBERS: return O_NUMBERS;
            default: return -1;
        }
    }

    /** 供 GameView 判断某次点击是否需要按坐标换算滑条值。 */
    public boolean isSlider(int id) {
        return sliderIndex(id) >= 0;
    }
}
