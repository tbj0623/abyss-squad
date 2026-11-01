package com.dilao.abysssquad;

import android.graphics.*;

import java.util.ArrayList;

/**
 * 通用控件库：所有界面共用。逻辑分辨率 1440x810，坐标直接使用。
 *
 * <p>设计原则：所有控件自带圆角、内外发光层次、1~2px 描边与正确内边距；
 * 文字一律用 {@link Paint.FontMetrics} 精确垂直居中；所有方法不抛异常，
 * null 文本按空串处理；绘制期复用 Paint / RectF / Path / 渐变缓存，不在热路径 new。</p>
 *
 * <p>坐标语义：{@code text()/right()} 的 y 是<b>基线</b>（与 Art.text 一致）；
 * {@code center()} 的 cy 是<b>视觉中心</b>（内部换算基线）。</p>
 */
public final class Ui {

    // ─────────────────────────── 调色板（契约 §17） ───────────────────────────
    public static final int BG = 0xff0b141b, PANEL = 0xff14232c, PANEL_HI = 0xff1b2e38,
            EDGE = 0xff2c4248, WHITE = 0xffedf1e7, MUTED = 0xff94abae, MINT = 0xff9de7c1,
            GOLD = 0xffeac487, RED = 0xfff18f8b, VIOLET = 0xffc2a7ff, BLUE = 0xff93c4ff, PINK = 0xffff91c8;

    /** 6 流派主色，契约 §3 顺序：余烬 / 疾风 / 霜星 / 生命 / 雷光 / 虚空。 */
    public static final int[] SCHOOL_COLOR = {
            0xffffa37b, 0xff83edc6, 0xff93c4ff, 0xfff3d58a, 0xffffe28a, 0xffc2a7ff};

    // ─────────────────────────── 导航动作码（Lead 统一） ───────────────────────────
    public static final int A_HOME = 1, A_START = 2, A_CONTINUE = 3, A_CODEX = 4, A_META = 5,
            A_SETTINGS = 6, A_HOST = 7, A_JOIN = 8, A_SOUND = 9, A_GUIDE = 10, A_BACK = 11,
            A_CONFIRM = 12, A_REROLL = 13, A_LEAVE = 14, A_RESUME = 15, A_RESTART = 16,
            A_ULTIMATE = 17, A_SKIP = 18, A_BUY = 19, A_EVOLVE = 20, A_NODE = 40;

    // ─────────────────────────── 位图注入 ───────────────────────────
    /** 由 GameView 注入的图集访问器；screen 不直接依赖 Art。 */
    public interface Art2 {
        Bitmap cover(int biome);

        Bitmap actorCell(int row, int frame);
    }

    private Art2 art2;

    public void bindArt(Art2 a) {
        art2 = a;
    }

    public Ui() {
    }

    public Ui(Art2 art) {
        bindArt(art);
    }

    // ─────────────────────────── 绘制状态（全部复用） ───────────────────────────
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final RectF r2 = new RectF();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final Matrix gm = new Matrix();

    private static final Typeface F_NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface F_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface F_MONO = Typeface.create("monospace", Typeface.NORMAL);

    /** 行高倍数（与 Art.wrap 一致）。 */
    public static final float LINE = 1.56f;

    // 渐变缓存：按 (高, 上色, 下色) 精确命中，上限 16 条，避免每帧分配。
    private final int[] gH = new int[16];
    private final int[] gTop = new int[16];
    private final int[] gBot = new int[16];
    private final LinearGradient[] gVal = new LinearGradient[16];
    private int gN;

    // ─────────────────────────── 命中表 ───────────────────────────
    /**
     * 移动端最小可点尺寸（逻辑像素）。
     * <p>UX 审计实测：本画布 1mm ≈ 10.8 逻辑像素，44dp 最小触控 ≈ 82px。
     * 次级按钮常见只画 46~60px 高，按绘制矩形判定时拇指实际点不中，
     * 因此 {@link #button}/{@link #ghost} 会把命中区按轴向外扩到至少 82px。</p>
     */
    public static final float MIN_TOUCH = 82f;

    /** 逻辑画布尺寸：热区扩边后钳制在此范围内，避免登记到画布外的无效矩形。 */
    private static final float CANVAS_W = 1440f, CANVAS_H = 810f;

    private static final class Hit {
        final int id;
        final float x, y, w, h;
        /** 是否为外扩热区：只有扩边热区参与「中心距」裁决，旧热区行为保持不变。 */
        final boolean padded;

        Hit(int id, float x, float y, float w, float h, boolean padded) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.padded = padded;
        }
    }

    private final ArrayList<Hit> hits = new ArrayList<>(96);

    /** 每个 screen 在 draw() 开头必须调用。 */
    public void clearHits() {
        hits.clear();
    }

    /** 登记可点击矩形；id 只在当前 screen 内唯一。 */
    public void hit(int id, float x, float y, float w, float h) {
        hits.add(new Hit(id, x, y, w, h, false));
    }

    /** 登记可点击矩形，并四周外扩 {@code pad}；超出画布 0..1440 / 0..810 的部分会被钳制。 */
    public void hit(int id, float x, float y, float w, float h, float pad) {
        hitPad(id, x, y, w, h, pad, pad);
    }

    /** 按轴分别外扩：button/ghost 需要 X/Y 独立的 pad，才能把两轴都补到 MIN_TOUCH。 */
    private void hitPad(int id, float x, float y, float w, float h, float padX, float padY) {
        if (w <= 0 || h <= 0) return;
        float px = Math.max(0f, padX), py = Math.max(0f, padY);
        float x0 = Math.max(0f, x - px), y0 = Math.max(0f, y - py);
        float x1 = Math.min(CANVAS_W, x + w + px), y1 = Math.min(CANVAS_H, y + h + py);
        if (x1 <= x0 || y1 <= y0) return;
        hits.add(new Hit(id, x0, y0, x1 - x0, y1 - y0, px > 0f || py > 0f));
    }

    /** 把 {@code size} 补到 MIN_TOUCH 所需的单侧扩边量（已达标则为 0）。 */
    private static float padFor(float size) {
        return Math.max(0f, (MIN_TOUCH - size) * .5f);
    }

    // ── 按压反馈 ────────────────────────────────────────────────
    // 移动端"点了没反应"会被读成"按钮坏了"。button/ghost 之前只有 hover 没有按下态，
    // 于是所有次级控件（图鉴 tab、设置开关、翻页…）点下去画面毫无变化。
    // GameView 在 activate(id) 里调 press(id)；这里做 140ms 的压下动画。
    private int pressId = -1;
    private long pressAt;
    private static final long PRESS_NS = 140_000_000L;

    /** 记录一次按下，供下一次 draw 播出压下动画。零分配。 */
    public void press(int id) {
        pressId = id;
        pressAt = System.nanoTime();
    }

    /** 1 = 刚按下，0 = 动画结束。 */
    private float pressPunch(int id) {
        if (id != pressId || pressId == -1) return 0f;
        float f = 1f - (System.nanoTime() - pressAt) / (float) PRESS_NS;
        return f <= 0f ? 0f : f;
    }

    /**
     * {@link #pressPunch} 的公开只读版。
     *
     * <p>供<b>不经 {@link #button}/{@link #ghost}</b> 的自绘控件复用同一份按压状态：
     * 目前只有 {@code BattleHud.drawSide}（局内「菜单 / 构筑」两块 120x88 面板，
     * 它自己画形状、只调 {@code ui.hit} 登记热区）。GameView 在 {@code activate()}
     * 里已经统一调过 {@link #press(int)}，所以这些自绘按钮也能拿到 140ms 压下。
     * 返回 0 表示没有按压，调用方按普通态绘制即可。</p>
     */
    public float pressAmount(int id) {
        return pressPunch(id);
    }

    /**
     * 命中最上层控件；无命中返回 -1。
     * <p>扩边后热区会与邻居重叠。纯「后注册者优先」会让间距只有 46px 的两行按钮互相抢点击，
     * 因此当且仅当命中的候选里存在扩边热区时，改按「中心离触点最近」裁决；
     * 候选全是旧式热区时仍走后注册优先，既有界面行为不变。</p>
     */
    public int hitAt(float x, float y) {
        int newest = -1;
        int nearest = -1;
        float nearest2 = Float.MAX_VALUE;
        boolean anyPadded = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit t = hits.get(i);
            if (x < t.x || x > t.x + t.w || y < t.y || y > t.y + t.h) continue;
            if (newest < 0) newest = t.id; // 倒序扫描，第一个命中的就是原来的「最上层」
            if (t.padded) anyPadded = true;
            float dx = x - (t.x + t.w * .5f), dy = y - (t.y + t.h * .5f);
            float d2 = dx * dx + dy * dy;
            if (d2 < nearest2) { // 严格小于：距离相同保留更晚注册的，结果确定可复现
                nearest2 = d2;
                nearest = t.id;
            }
        }
        if (newest < 0) return -1;
        return anyPadded ? nearest : newest;
    }

    public int hitCount() {
        return hits.size();
    }

    // ─────────────────────────── 色值工具 ───────────────────────────
    /** 线性混合 a→b，t 会被钳制到 0..1。 */
    public int blend(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = (a >>> 24), ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = (b >>> 24), br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        int oa = (int) (aa + (ba - aa) * t + .5f), oR = (int) (ar + (br - ar) * t + .5f),
                oG = (int) (ag + (bg - ag) * t + .5f), oB = (int) (ab + (bb - ab) * t + .5f);
        return (oa << 24) | (oR << 16) | (oG << 8) | oB;
    }

    /** 设置绝对不透明度（0..255），保留 RGB。 */
    public int alpha(int color, int a) {
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (color & 0x00ffffff);
    }

    /** 在已有 alpha 基础上再乘一个系数（0..1）。 */
    public int fade(int color, float k) {
        int a = (int) (((color >>> 24) * Math.max(0f, Math.min(1f, k))) + .5f);
        return (a << 24) | (color & 0x00ffffff);
    }

    private int lighten(int color, float k) {
        return blend(color, (color & 0x00ffffff) | 0xff000000, Math.max(0f, Math.min(1f, k)));
    }

    // ─────────────────────────── 基础图形 ───────────────────────────
    public void box(Canvas c, float x, float y, float w, float h, float rad, int color) {
        if (c == null) return;
        if (w <= 0 || h <= 0) return;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setColor(color);
        r.set(x, y, x + w, y + h);
        float rr = Math.max(0f, Math.min(rad, Math.min(w, h) * .5f));
        c.drawRoundRect(r, rr, rr, p);
    }

    public void stroke(Canvas c, float x, float y, float w, float h, float rad, int color, float width) {
        if (c == null) return;
        if (w <= 0 || h <= 0 || width <= 0) return;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        r.set(x + width * .5f, y + width * .5f, x + w - width * .5f, y + h - width * .5f);
        float rr = Math.max(0f, Math.min(rad, Math.min(w, h) * .5f));
        c.drawRoundRect(r, rr, rr, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    public void line(Canvas c, float x0, float y0, float x1, float y1, int color, float width) {
        if (c == null) return;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(.5f, width));
        p.setColor(color);
        c.drawLine(x0, y0, x1, y1, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    public void circle(Canvas c, float x, float y, float radius, int color) {
        if (c == null || radius <= 0) return;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(x, y, radius, p);
    }

    public void ring(Canvas c, float x, float y, float radius, int color, float width) {
        if (c == null || radius <= 0 || width <= 0) return;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawCircle(x, y, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    /**
     * 径向光晕：烘焙成 48×48 软圆位图后缩放。
     * <p>按颜色做多槽缓存。旧实现只保留「最后一种颜色」，
     * 一帧里只要出现两种以上颜色的 glow（几乎每个界面都会），
     * 就会每帧 recycle + createBitmap 数次——在硬件加速 Canvas 上
     * 每张新位图都要上传成一张 GPU 纹理，模拟器上直接把帧拖到秒级并触发 ANR。
     */
    private static final int GLOW_SLOTS = 12;
    private final android.graphics.Bitmap[] glowBmp = new android.graphics.Bitmap[GLOW_SLOTS];
    private final int[] glowRgb = new int[GLOW_SLOTS];
    private int glowCursor;

    /**
     * 按 <b>RGB</b> 缓存，不把 alpha 算进键。
     *
     * <p>v2.3 修：旧实现按完整 ARGB 缓存，而 {@code MenuScreen} 的主按钮与标题图标
     * 用的是呼吸 alpha（{@code .55f + .45f * pulse}），每帧颜色都不同 ——
     * 缓存 100% 未命中，12 个槽每帧全部 recycle + createBitmap + 新建 RadialGradient。
     * 硬件加速 Canvas 上每张新位图都要上传成 GPU 纹理，
     * 实测主菜单稳定 700–1500ms/帧并直接触发 ANR。
     *
     * <p>烘焙时用满不透明度，实际亮度交给 draw 时的 Paint.alpha 相乘。
     * 顺带修掉一个隐性错误：旧绘制路径无条件 {@code setAlpha(255 * aScale)}，
     * 把烘焙用的 baseA 整个盖掉了 —— 所有 {@code alpha(color, n)} 里的 n 从来就没生效过。
     */
    private android.graphics.Bitmap glowFor(int rgb) {
        for (int i = 0; i < GLOW_SLOTS; i++)
            if (glowBmp[i] != null && glowRgb[i] == rgb) return glowBmp[i];
        int slot = glowCursor;
        glowCursor = (glowCursor + 1) % GLOW_SLOTS;
        try {
            if (glowBmp[slot] != null) glowBmp[slot].recycle();
            int size = 48;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size,
                    android.graphics.Bitmap.Config.ARGB_8888);
            Canvas gc = new Canvas(bmp);
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setShader(new RadialGradient(size * .5f, size * .5f, size * .5f,
                    new int[]{alpha(rgb | 0xff000000, 77),
                            alpha(rgb | 0xff000000, 26),
                            0x00000000},
                    new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
            gc.drawCircle(size * .5f, size * .5f, size * .5f, gp);
            glowBmp[slot] = bmp;
            glowRgb[slot] = rgb;
            return bmp;
        } catch (Throwable t) {
            glowBmp[slot] = null;
            glowRgb[slot] = 0;
            return null;
        }
    }

    public void glow(Canvas c, float x, float y, float radius, int color) {
        if (c == null || radius <= 0) return;
        int rgb = color & 0x00ffffff;
        int baseA = color >>> 24;
        if (baseA == 0) baseA = 255;
        android.graphics.Bitmap bmp = glowFor(rgb);
        if (bmp == null) return;
        // 大半径光晕在软件渲染上非常昂贵：限制实际绘制尺寸，用 alpha 补偿观感
        float rDraw = Math.min(radius, 190f);
        float aScale = Math.min(1f, radius / 190f);
        int oldAlpha = p.getAlpha();
        // 调用方 alpha × 半径补偿，两者都要保留（旧代码只留了后者）
        int eff = (int) (baseA * aScale);
        p.setAlpha(Math.max(1, Math.min(255, eff)));
        r.set(x - rDraw, y - rDraw, x + rDraw, y + rDraw);
        c.drawBitmap(bmp, null, r, p);
        p.setAlpha(oldAlpha);
    }

    public void diamond(Canvas c, float x, float y, float w, int color) {
        if (c == null || w <= 0) return;
        path.reset();
        path.moveTo(x, y - w);
        path.lineTo(x + w * .68f, y);
        path.lineTo(x, y + w);
        path.lineTo(x - w * .68f, y);
        path.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }

    // ─────────────────────────── 渐变缓存 ───────────────────────────
    private LinearGradient vertical(float h, int top, int bottom) {
        int hh = Math.max(1, Math.round(h));
        for (int i = 0; i < gN; i++) {
            if (gH[i] == hh && gTop[i] == top && gBot[i] == bottom) return gVal[i];
        }
        if (gN == gH.length) gN = 0;
        LinearGradient g = new LinearGradient(0, 0, 0, hh, top, bottom, Shader.TileMode.CLAMP);
        gH[gN] = hh;
        gTop[gN] = top;
        gBot[gN] = bottom;
        gVal[gN] = g;
        gN++;
        return g;
    }

    /**
     * 竖向渐变圆角矩形。
     * <p>软件渲染下"圆角矩形 + 渐变 shader"非常昂贵，因此把渐变+圆角烘焙成
     * 一张低分辨率位图后缩放绘制；对相同尺寸/颜色的重复调用直接复用。
     */
    // 渐变缓存：按 (高度,上色,下色) 缓存 Shader，绘制时用 canvas.translate 定位，
    // 避免 setLocalMatrix 的高开销，也不再创建中间位图。
    private static final int GRAD_SLOTS = 20;
    private final LinearGradient[] gShader = new LinearGradient[GRAD_SLOTS];
    private final int[] shH = new int[GRAD_SLOTS];
    private final int[] shTop = new int[GRAD_SLOTS];
    private final int[] shBot = new int[GRAD_SLOTS];
    private int gCursor;

    /**
     * 渐变的规范高度。
     * <p>关键：渐变**不再按实际高度缓存**。以前每个不同高度都会建一个新的
     * LinearGradient，一旦一帧内用到的 (高度,上色,下色) 组合超过缓存槽，
     * 就会每帧重建，而 HWUI 每遇到一个新的 Shader 都要现场编译 GLSL 程序
     * （模拟器上卡在 glGetShaderiv / qemu_pipe_read 长达数秒，直接 ANR）。
     * 现在所有竖向渐变都建在同一个规范高度上，绘制时用 canvas.scale 拉伸，
     * Shader 总数只取决于颜色组合，与尺寸无关。
     */
    private static final int GRAD_CANON = 64;

    private LinearGradient shaderFor(int unusedHeight, int top, int bottom) {
        for (int i = 0; i < GRAD_SLOTS; i++)
            if (gShader[i] != null && shTop[i] == top && shBot[i] == bottom) return gShader[i];
        int slot = gCursor;
        gCursor = (gCursor + 1) % GRAD_SLOTS;
        LinearGradient g = new LinearGradient(0, 0, 0, GRAD_CANON, top, bottom, Shader.TileMode.CLAMP);
        gShader[slot] = g;
        shH[slot] = GRAD_CANON;
        shTop[slot] = top;
        shBot[slot] = bottom;
        return g;
    }

    public void gradientRect(Canvas c, float x, float y, float w, float h, float rad, int top, int bottom) {
        if (c == null || w <= 0 || h <= 0) return;
        float rr = Math.max(0f, Math.min(rad, Math.min(w, h) * .5f));
        c.save();
        c.translate(x, y);
        // 在规范高度的坐标系里画：圆角半径同比缩小，避免拉伸后圆角变形
        float k = h / GRAD_CANON;
        c.scale(1f, k);
        p.setStyle(Paint.Style.FILL);
        p.setShader(shaderFor(GRAD_CANON, top, bottom));
        r.set(0, 0, w, GRAD_CANON);
        float rrScaled = rr / Math.max(1e-4f, k);
        c.drawRoundRect(r, rrScaled, rrScaled, p);
        p.setShader(null);
        c.restore();
    }

    /** 释放渐变与光晕缓存（Activity 销毁时）。 */
    public void recycleCaches() {
        for (int i = 0; i < GRAD_SLOTS; i++) { gShader[i] = null; shH[i] = 0; shTop[i] = 0; shBot[i] = 0; }
        for (int i = 0; i < GLOW_SLOTS; i++) {
            if (glowBmp[i] != null) { try { glowBmp[i].recycle(); } catch (Throwable ignored) {} }
            glowBmp[i] = null;
            glowRgb[i] = 0;
        }
        glowCursor = 0;
    }

    // ─────────────────────────── panel ───────────────────────────
    /** 圆角面板：外层深色 1px + 内层竖向渐变 + 顶部 1px 高光。 */
    public void panel(Canvas c, float x, float y, float w, float h, float r, int fill, int strokeColor) {
        if (c == null || w <= 0 || h <= 0) return;
        int base = (fill == 0) ? PANEL : fill;
        int rr = Math.max(0, Math.round(r));
        // 底部投影，制造脱离背景的层次
        box(c, x, y + 2, w, h, rr, 0x33000000);
        // 主体渐变：顶部提亮 3%，底部压暗 12%
        gradientRect(c, x, y, w, h, rr, lighten(base, .035f), blend(base, (base & 0x00ffffff) | 0xff000000, .12f));
        // 顶部 1px 内高光
        int hiA = Math.max(0, (int) (((base >>> 24) * .16f)));
        box(c, x + rr * .6f, y + 1, w - rr * 1.2f, 1, 0, (hiA << 24) | 0xffffff);
        // 描边
        if (strokeColor != 0) stroke(c, x, y, w, h, rr, strokeColor, 1f);
        else stroke(c, x, y, w, h, rr, EDGE, 1f);
    }

    /** 纯描边圆角框（无填充），用于嵌套区块。 */
    public void outline(Canvas c, float x, float y, float w, float h, float rad, int color) {
        stroke(c, x, y, w, h, rad, color, 1f);
    }

    // ─────────────────────────── card ───────────────────────────
    /** 图鉴/选择卡片：selected 发光 + 2px 亮边；hover 背景提亮 6%。 */
    public void card(Canvas c, float x, float y, float w, float h, int accent, boolean selected, boolean hover) {
        if (c == null || w <= 0 || h <= 0) return;
        int base = selected ? blend(PANEL_HI, accent, .10f) : hover ? lighten(PANEL, .06f) : PANEL;
        if (selected) {
            // 外发光：3 层扩散
            box(c, x - 3, y - 3, w + 6, h + 6, 17, alpha(accent, 26));
            box(c, x - 1.5f, y - 1.5f, w + 3, h + 3, 15, alpha(accent, 40));
            box(c, x, y + 2, w, h, 14, alpha(accent, 22));
            gradientRect(c, x, y, w, h, 14, lighten(base, .06f), blend(base, (base & 0xffffff) | 0xff000000, .16f));
            stroke(c, x, y, w, h, 14, accent, 2f);
        } else {
            box(c, x, y + 2, w, h, 14, 0x33000000);
            gradientRect(c, x, y, w, h, 14, lighten(base, .03f), blend(base, 0xff000000, .12f));
            stroke(c, x, y, w, h, 14, hover ? alpha(accent == 0 ? EDGE : accent, 150) : EDGE, 1f);
        }
        box(c, x + 9, y + 1, w - 18, 1, 0, 0x1affffff);
    }

    // ─────────────────────────── button ───────────────────────────
    /**
     * 标准按钮。primary：MINT 填充 + 深色文字 + 底部 3px 立体阴影线；
     * secondary：PANEL 填充 + EDGE 描边 + 白字；disabled：整体 40% + MUTED 文字。
     * 文字过长自动缩小字号（最小 12）。
     */
    public void button(Canvas c, int id, float x, float y, float w, float h, String label, boolean primary, boolean enabled) {
        if (c == null || w <= 0 || h <= 0) return;
        // 热区按两轴分别补到 MIN_TOUCH（1mm≈10.8px → 82px≈7.6mm）；
        // 绘制尺寸不变，只放大可点范围。
        hitPad(id, x, y, w, h, padFor(w), padFor(h));
        String s = label == null ? "" : label;
        float rad = Math.min(14f, h * .30f);
        int dim = enabled ? 255 : 102;
        // 按下时整体缩 3% + 压一层白：拇指盖住按钮时这是唯一的"我按到了"证据
        float punchAmt = pressPunch(id);
        if (punchAmt > .01f) {
            float sx = w * .03f * punchAmt, sy = h * .03f * punchAmt;
            x += sx; y += sy; w -= sx * 2; h -= sy * 2;
        }

        if (primary) {
            int face = MINT;
            // 底部立体阴影条
            box(c, x, y + h - 4, w, 4 + rad * .35f, rad, alpha(blend(MINT, 0xff0b141b, .45f), dim));
            if (!enabled) {
                gradientRect(c, x, y, w, h, rad, alpha(blend(MINT, MUTED, .45f), dim), alpha(blend(MINT, 0xff0b141b, .30f), dim));
            } else {
                gradientRect(c, x, y, w, h, rad, lighten(face, .10f), blend(face, 0xff14342c, .18f));
            }
            // 内顶高光
            box(c, x + rad * .7f, y + 1.5f, w - rad * 1.4f, 1.5f, 0, alpha(0xffffffff, enabled ? 60 : 18));
            // 1px 亮边
            stroke(c, x, y, w, h, rad, alpha(blend(MINT, 0xffffffff, .25f), enabled ? 200 : 70), 1f);
            float size = fit(s, w - h * .55f, h * .40f, 12f);
            textC(c, s, x + w * .5f, y + h * .5f - 1.5f, size, alpha(BG, dim), true);
        } else {
            gradientRect(c, x, y, w, h, rad, lighten(PANEL_HI, .02f), blend(PANEL, 0xff000000, .18f));
            box(c, x, y + h - 2.5f, w, 2.5f, rad, 0x33000000);
            stroke(c, x, y, w, h, rad, alpha(EDGE, enabled ? 255 : 110), 1f);
            box(c, x + rad * .7f, y + 1, w - rad * 1.4f, 1, 0, alpha(0xffffffff, enabled ? 26 : 10));
            float size = fit(s, w - h * .55f, h * .38f, 12f);
            textC(c, s, x + w * .5f, y + h * .5f - 1f, size, enabled ? WHITE : MUTED, true);
        }
        if (punchAmt > .01f) {
            box(c, x, y, w, h, rad, alpha(0xffffffff, (int) (48 * punchAmt)));
        }
    }

    /** 轻量幽灵按钮：无填充、弱描边，用于次级操作与“返回”。 */
    public void ghost(Canvas c, int id, float x, float y, float w, float h, String label) {
        if (c == null || w <= 0 || h <= 0) return;
        hitPad(id, x, y, w, h, padFor(w), padFor(h));
        float punchAmt = pressPunch(id);
        if (punchAmt > .01f) {
            float sx = w * .03f * punchAmt, sy = h * .03f * punchAmt;
            x += sx; y += sy; w -= sx * 2; h -= sy * 2;
        }
        String s = label == null ? "" : label;
        float rad = Math.min(12f, h * .32f);
        gradientRect(c, x, y, w, h, rad, 0x14ffffff, 0x0affffff);
        stroke(c, x, y, w, h, rad, alpha(EDGE, 210), 1f);
        float size = fit(s, w - h * .5f, h * .38f, 12f);
        textC(c, s, x + w * .5f, y + h * .5f, size, WHITE, false);
        if (punchAmt > .01f) {
            box(c, x, y, w, h, rad, alpha(0xffffffff, (int) (48 * punchAmt)));
        }
    }

    /** 小号标签/胶囊，高度固定 28。 */
    public void pill(Canvas c, float x, float y, float w, String s, int color) {
        if (c == null || w <= 0) return;
        float h = 28;
        box(c, x, y, w, h, h * .5f, alpha(color, 26));
        stroke(c, x, y, w, h, h * .5f, alpha(color, 160), 1f);
        float size = fit(s == null ? "" : s, w - 16, 12.5f, 11f);
        textC(c, s, x + w * .5f, y + h * .5f, size, color, true);
    }

    // ─────────────────────────── bar ───────────────────────────
    /** 圆角进度条：轨道 + 进度 + 内阴影 + 顶部高光。 */
    public void bar(Canvas c, float x, float y, float w, float h, float amount, int color) {
        if (c == null || w <= 0 || h <= 0) return;
        float rad = h * .5f;
        box(c, x, y, w, h, rad, 0xff1b2a33);
        box(c, x, y, w, h, rad, 0x99000000);
        float k = amount;
        if (Float.isNaN(k) || k < 0) k = 0;
        if (k > 1) k = 1;
        if (k > 0) {
            float fw = Math.max(h * .9f, w * k);
            if (fw > w) fw = w;
            gradientRect(c, x, y, fw, h, rad, lighten(color, .14f), blend(color, 0xff000000, .16f));
            box(c, x + rad, y + 1, Math.max(0, fw - rad * 2), Math.max(1, h * .30f), h * .15f, 0x2effffff);
        }
        stroke(c, x, y, w, h, rad, 0x33ffffff, 1f);
        // 内阴影（顶部暗线）
        box(c, x + rad, y, Math.max(0, w - rad * 2), 1, 0, 0x44000000);
    }

    /** 分段进度条（如血量 5 段）：段间留 2px 间隙。 */
    public void barSeg(Canvas c, float x, float y, float w, float h, float amount, int color, int segs) {
        if (c == null || w <= 0 || h <= 0) return;
        if (segs < 1) segs = 1;
        float gap = 2f;
        float sw = (w - gap * (segs - 1)) / segs;
        if (sw <= 0) sw = w / segs;
        float k = Float.isNaN(amount) ? 0 : Math.max(0f, Math.min(1f, amount));
        for (int i = 0; i < segs; i++) {
            float sx = x + i * (sw + gap);
            float fillK = Math.max(0f, Math.min(1f, k * segs - i));
            box(c, sx, y, sw, h, Math.min(3, h * .35f), 0xff1b2a33);
            if (fillK > 0) {
                float fw = sw * fillK;
                gradientRect(c, sx, y, fw, h, Math.min(3, h * .35f), lighten(color, .12f), blend(color, 0xff000000, .18f));
                if (h >= 5) box(c, sx + 2, y + 1, Math.max(0, fw - 4), 1, 0, 0x2effffff);
            }
            stroke(c, sx, y, sw, h, Math.min(3, h * .35f), 0x33ffffff, 1f);
        }
    }

    /** 分隔线：上 1px EDGE + 下 1px 黑色，形成压印感。 */
    public void divider(Canvas c, float x, float y, float w) {
        if (c == null || w <= 0) return;
        box(c, x, y, w, 1, 0, EDGE);
        box(c, x, y + 1, w, 1, 0, 0x55000000);
    }

    /** 细滚动条：轨道 + 滑块。pos/page 均为 0..1。 */
    public void scrollbar(Canvas c, float x, float y, float h, float pos, float page) {
        if (c == null || h <= 0) return;
        float w = 4;
        box(c, x, y, w, h, 2, 0x22ffffff);
        float p0 = Math.max(0f, Math.min(1f, pos));
        float pg = Math.max(.08f, Math.min(1f, page));
        float th = Math.max(24, h * pg);
        float ty = y + (h - th) * p0;
        box(c, x, ty, w, th, 2, 0x66ffffff);
        box(c, x, ty, w, 1, 0, 0x55ffffff);
    }

    /** 横向分段控件（模式 tab / 契约档位）。ids 可为 null（不登记点击）。 */
    public void segmented(Canvas c, float x, float y, float w, float h, int count, int selected, int[] ids, String[] labels) {
        if (c == null || count <= 0 || w <= 0 || h <= 0) return;
        float rad = Math.min(12f, h * .42f);
        gradientRect(c, x, y, w, h, rad, 0xff101d24, 0xff0d181e);
        stroke(c, x, y, w, h, rad, EDGE, 1f);
        float pad = 4;
        float inner = w - pad * 2;
        float sw = inner / count;
        for (int i = 0; i < count; i++) {
            float sx = x + pad + i * sw;
            boolean on = i == selected;
            if (on) {
                gradientRect(c, sx + 1.5f, y + pad, sw - 3, h - pad * 2, rad * .7f, lighten(PANEL_HI, .10f), blend(PANEL_HI, 0xff000000, .14f));
                stroke(c, sx + 1.5f, y + pad, sw - 3, h - pad * 2, rad * .7f, alpha(MINT, 150), 1f);
                box(c, sx + 6, y + pad + 1, Math.max(0, sw - 12), 1, 0, 0x33ffffff);
            }
            String lab = (labels != null && i < labels.length) ? labels[i] : "";
            float size = fit(lab, sw - 16, h * .34f, 11f);
            textC(c, lab, sx + sw * .5f, y + h * .5f, size, on ? WHITE : MUTED, on);
            if (ids != null && i < ids.length) hit(ids[i], sx, y + pad, sw, h - pad * 2);
        }
    }

    // ─────────────────────────── 文字 ───────────────────────────
    /** y 为基线。 */
    public void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        tp.setShader(null);
        tp.setStyle(Paint.Style.FILL);
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(size);
        tp.setColor(color);
        c.drawText(v, x, y, tp);
    }

    /** 等宽小字（英文小标签）。 */
    public void small(Canvas c, String s, float x, float y, float size, int color) {
        if (c == null || size <= 0) return;
        tp.setShader(null);
        tp.setStyle(Paint.Style.FILL);
        tp.setTypeface(F_MONO);
        tp.setTextSize(size);
        tp.setColor(color);
        c.drawText(s == null ? "" : s, x, y, tp);
    }

    /** 视觉中心对齐（cy 是文字块中心，不是基线）。 */
    public void center(Canvas c, String s, float cx, float cy, float size, int color, boolean bold) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        float tw = tp.measureText(v);
        textV(c, v, cx - tw * .5f, cy, size, color, bold);
    }

    /** 右对齐，y 为基线。 */
    public void right(Canvas c, String s, float rx, float y, float size, int color, boolean bold) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(size);
        float tw = tp.measureText(v);
        text(c, v, rx - tw, y, size, color, bold);
    }

    /** 内部：x 为左边缘，cy 为文字块视觉中心。 */
    private void textV(Canvas c, String s, float x, float cy, float size, int color, boolean bold) {
        if (c == null || size <= 0) return;
        tp.setShader(null);
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.getFontMetrics(fm);
        float baseline = cy - (fm.ascent + fm.descent) * .5f;
        c.drawText(s == null ? "" : s, x, baseline, tp);
    }

    /** 内部：cx 为水平中心，cy 为文字块视觉中心。按钮/胶囊等控件的标准文字绘制入口。 */
    private void textC(Canvas c, String s, float cx, float cy, float size, int color, boolean bold) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        tp.setShader(null);
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.getFontMetrics(fm);
        float baseline = cy - (fm.ascent + fm.descent) * .5f;
        c.drawText(v, cx - tp.measureText(v) * .5f, baseline, tp);
    }

    /** 以 (cx, cy) 为中心的混排小标题（等宽英文）。 */
    public void centerSmall(Canvas c, String s, float cx, float cy, float size, int color) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        tp.setTypeface(F_MONO);
        tp.setTextSize(size);
        float tw = tp.measureText(v);
        tp.getFontMetrics(fm);
        tp.setColor(color);
        c.drawText(v, cx - tw * .5f, cy - (fm.ascent + fm.descent) * .5f, tp);
    }

    /** 界面大标题：柔和投影 + 主字。y 为基线。 */
    public void title(Canvas c, String s, float x, float y, float size) {
        if (c == null || size <= 0) return;
        String v = s == null ? "" : s;
        text(c, v, x + 1.2f, y + 1.6f, size, 0x66000000, true);
        text(c, v, x, y, size, WHITE, true);
    }

    /** 按宽度自适应字号（不放大，只在放不下时缩小，下限 minSize）。 */
    public float fit(String s, float maxW, float size, float minSize) {
        if (s == null || s.isEmpty() || maxW <= 0) return Math.max(minSize, size);
        tp.setTypeface(F_BOLD);
        tp.setTextSize(size);
        float w = tp.measureText(s);
        if (w <= maxW) return size;
        float f = size * maxW / Math.max(1f, w);
        return Math.max(minSize, f);
    }

    /** 测量文字宽度。 */
    public float measure(String s, float size, boolean bold) {
        tp.setTypeface(bold ? F_BOLD : F_NORMAL);
        tp.setTextSize(Math.max(1f, size));
        return s == null ? 0 : tp.measureText(s);
    }

    // ─────────────────────────── wrap（中文按字符换行） ───────────────────────────
    /** 中文按字符换行；英文单词尽量整词；行首禁止出现标点。y 为第一行基线。 */
    public void wrap(Canvas c, String s, float x, float y, float maxW, float size, int color) {
        if (c == null || size <= 0 || maxW <= 8) return;
        String v = s == null ? "" : s;
        tp.setTypeface(F_NORMAL);
        tp.setTextSize(size);
        tp.setColor(color);
        int i = 0, n = v.length();
        float lineY = y;
        while (i < n) {
            int end = lineEnd(v, i, maxW);
            if (end <= i) end = i + 1;
            String part = v.substring(i, Math.min(end, n)).trim();
            if (!part.isEmpty()) text(c, part, x, lineY, size, color, false);
            i = end;
            // 跳过紧随其后的换行符
            if (i < n && v.charAt(i) == '\n') i++;
            lineY += size * LINE;
        }
    }

    /** 与 wrap 同算法，返回所需高度。 */
    public float wrapHeight(String s, float maxW, float size) {
        if (size <= 0 || maxW <= 8) return size;
        String v = s == null ? "" : s;
        tp.setTypeface(F_NORMAL);
        tp.setTextSize(size);
        int i = 0, n = v.length(), lines = 0;
        while (i < n) {
            int end = lineEnd(v, i, maxW);
            if (end <= i) end = i + 1;
            i = end;
            if (i < n && v.charAt(i) == '\n') i++;
            lines++;
            if (lines > 400) break;
        }
        if (lines == 0) lines = 1;
        return lines * size * LINE;
    }

    private static boolean isAsciiWord(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || ch == '_' || ch == '-' || ch == '/' || ch == ':' || ch == '%'
                || ch == '.' || ch == '+';
    }

    private static boolean isNoStart(char ch) {
        return "，。、；：！？）》」』】〕…—·,.;:!?)]}%".indexOf(ch) >= 0;
    }

    /** 返回一行在 [start,end) 的结束下标（不含）。 */
    private int lineEnd(String s, int start, float maxW) {
        int n = s.length();
        if (start >= n) return n;
        if (s.charAt(start) == '\n') return start;
        int i = start, lastBreak = -1;
        float w = 0;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\n') return i;
            int j = i + 1;
            if (isAsciiWord(ch)) {
                while (j < n && isAsciiWord(s.charAt(j))) j++;
            }
            float cw = tp.measureText(s, i, j);
            if (w + cw > maxW && w > 0) {
                if (isNoStart(ch)) { // 标点不另起一行，容忍轻微溢出
                    i = j;
                    w += cw;
                    continue;
                }
                return lastBreak > start ? lastBreak : i;
            }
            w += cw;
            if (ch == ' ' || ch == '\u3000' || ch == '\t') lastBreak = j;
            i = j;
        }
        return n;
    }

    // ─────────────────────────── icon（16 形状） ───────────────────────────
    /** iconId 0..15：菱形/圆环/三角/六边/齿轮/十字/星形/裂片/眼睛/火焰/雪花/闪电/骷髅/翅膀/锚/种子。 */
    public void icon(Canvas c, float x, float y, float size, int iconId, int color) {
        if (c == null || size <= 0) return;
        float s = size * .5f;
        int id = Math.floorMod(iconId, 16);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (id) {
            case 0: // 菱形
                path.reset();
                path.moveTo(x, y - s);
                path.lineTo(x + s * .72f, y);
                path.lineTo(x, y + s);
                path.lineTo(x - s * .72f, y);
                path.close();
                c.drawPath(path, p);
                break;
            case 1: // 圆环
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .34f);
                c.drawCircle(x, y, s * .74f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case 2: // 三角
                path.reset();
                path.moveTo(x, y - s * .92f);
                path.lineTo(x + s, y + s * .72f);
                path.lineTo(x - s, y + s * .72f);
                path.close();
                c.drawPath(path, p);
                break;
            case 3: { // 六边形
                path.reset();
                for (int i = 0; i < 6; i++) {
                    double an = -Math.PI / 2 + i * Math.PI / 3;
                    float px = x + (float) Math.cos(an) * s, py = y + (float) Math.sin(an) * s;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, p);
                break;
            }
            case 4: { // 齿轮
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45f, x, y);
                    r2.set(x - s * .105f, y - s * .98f, x + s * .105f, y - s * .58f);
                    c.drawRoundRect(r2, s * .07f, s * .07f, p);
                    c.restore();
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .30f);
                c.drawCircle(x, y, s * .44f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 5: // 十字
                box(c, x - s * .22f, y - s * .92f, s * .44f, s * 1.84f, s * .10f, color);
                box(c, x - s * .92f, y - s * .22f, s * 1.84f, s * .44f, s * .10f, color);
                break;
            case 6: { // 星形（5 角）
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double an = -Math.PI / 2 + i * Math.PI / 5;
                    float rad = (i % 2 == 0) ? s : s * .43f;
                    float px = x + (float) Math.cos(an) * rad, py = y + (float) Math.sin(an) * rad;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, p);
                break;
            }
            case 7: { // 裂片（不规则四边）
                path.reset();
                path.moveTo(x - s * .22f, y - s);
                path.lineTo(x + s * .92f, y - s * .18f);
                path.lineTo(x + s * .22f, y + s);
                path.lineTo(x - s * .92f, y + s * .18f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case 8: { // 眼睛
                path.reset();
                path.moveTo(x - s, y);
                path.quadTo(x, y - s * .95f, x + s, y);
                path.quadTo(x, y + s * .95f, x - s, y);
                path.close();
                c.drawPath(path, p);
                circle(c, x, y, s * .34f, 0xdd000000);
                circle(c, x + s * .10f, y - s * .08f, s * .12f, 0xddffffff);
                break;
            }
            case 9: { // 火焰
                path.reset();
                path.moveTo(x, y - s);
                path.cubicTo(x + s * .92f, y - s * .18f, x + s * .62f, y + s * .92f, x, y + s * .92f);
                path.cubicTo(x - s * .62f, y + s * .92f, x - s * .92f, y - s * .18f, x, y - s);
                path.close();
                c.drawPath(path, p);
                circle(c, x, y + s * .34f, s * .30f, 0x55ffffff);
                break;
            }
            case 10: { // 雪花
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .17f);
                for (int i = 0; i < 3; i++) {
                    double an = i * Math.PI / 3;
                    float dx = (float) Math.cos(an) * s, dy = (float) Math.sin(an) * s;
                    c.drawLine(x - dx, y - dy, x + dx, y + dy, p);
                    float bx = (float) Math.cos(an) * s * .52f, by = (float) Math.sin(an) * s * .52f;
                    for (int k = -1; k <= 1; k += 2) {
                        double ba = an + k * Math.PI / 3.4;
                        c.drawLine(x + bx, y + by, x + bx + (float) Math.cos(ba) * s * .34f,
                                y + by + (float) Math.sin(ba) * s * .34f, p);
                    }
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 11: { // 闪电
                path.reset();
                path.moveTo(x + s * .30f, y - s);
                path.lineTo(x - s * .58f, y + s * .12f);
                path.lineTo(x - s * .02f, y + s * .12f);
                path.lineTo(x - s * .26f, y + s);
                path.lineTo(x + s * .62f, y - s * .16f);
                path.lineTo(x + s * .06f, y - s * .16f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case 12: { // 骷髅
                r2.set(x - s * .80f, y - s * .92f, x + s * .80f, y + s * .34f);
                c.drawRoundRect(r2, s * .78f, s * .78f, p);
                box(c, x - s * .44f, y + s * .24f, s * .88f, s * .58f, s * .18f, color);
                circle(c, x - s * .34f, y - s * .24f, s * .25f, 0xcc000000);
                circle(c, x + s * .34f, y - s * .24f, s * .25f, 0xcc000000);
                box(c, x - s * .06f, y + s * .12f, s * .12f, s * .18f, s * .05f, 0xcc000000);
                break;
            }
            case 13: { // 翅膀
                path.reset();
                path.moveTo(x, y + s * .55f);
                path.cubicTo(x - s * .34f, y - s * .10f, x - s * .78f, y - s * .32f, x - s, y - s * .74f);
                path.cubicTo(x - s * .86f, y + s * .10f, x - s * .46f, y + s * .62f, x, y + s * .55f);
                path.close();
                c.drawPath(path, p);
                path2.reset();
                path2.moveTo(x, y + s * .55f);
                path2.cubicTo(x + s * .34f, y - s * .10f, x + s * .78f, y - s * .32f, x + s, y - s * .74f);
                path2.cubicTo(x + s * .86f, y + s * .10f, x + s * .46f, y + s * .62f, x, y + s * .55f);
                path2.close();
                c.drawPath(path2, p);
                break;
            }
            case 14: { // 锚
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .20f);
                c.drawCircle(x, y - s * .62f, s * .26f, p);
                c.drawLine(x, y - s * .34f, x, y + s * .72f, p);
                c.drawLine(x - s * .56f, y - s * .10f, x + s * .56f, y - s * .10f, p);
                path.reset();
                path.moveTo(x - s * .78f, y + s * .26f);
                path.quadTo(x - s * .40f, y + s * .96f, x, y + s * .80f);
                path.quadTo(x + s * .40f, y + s * .96f, x + s * .78f, y + s * .26f);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            default: { // 15 种子
                path.reset();
                path.moveTo(x, y - s * .96f);
                path.cubicTo(x + s * .92f, y - s * .30f, x + s * .70f, y + s * .86f, x, y + s * .96f);
                path.cubicTo(x - s * .70f, y + s * .86f, x - s * .92f, y - s * .30f, x, y - s * .96f);
                path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .10f);
                p.setColor(0x66000000);
                c.drawLine(x, y - s * .40f, x, y + s * .60f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeJoin(Paint.Join.MITER);
    }

    // ─────────────────────────── schoolIcon（6 徽记） ───────────────────────────
    /** 流派徽记 0..5：余烬/疾风/霜星/生命/雷光/虚空。 */
    public void schoolIcon(Canvas c, float x, float y, float size, int school) {
        if (c == null || size <= 0) return;
        int sid = Math.floorMod(school, 6);
        int color = SCHOOL_COLOR[sid];
        float s = size * .5f;
        glow(c, x, y, s * 1.15f, alpha(color, 62));
        circle(c, x, y, s * .98f, alpha(color, 20));
        ring(c, x, y, s * .98f, alpha(color, 120), Math.max(1f, size * .045f));
        switch (sid) {
            case 0: { // 余烬：火焰 + 底部火种
                path.reset();
                path.moveTo(x, y - s * .74f);
                path.cubicTo(x + s * .58f, y - s * .10f, x + s * .36f, y + s * .70f, x, y + s * .62f);
                path.cubicTo(x - s * .36f, y + s * .70f, x - s * .58f, y - s * .10f, x, y - s * .74f);
                path.close();
                p.setColor(color);
                c.drawPath(path, p);
                circle(c, x, y + s * .24f, s * .18f, alpha(WHITE, 120));
                break;
            }
            case 1: { // 疾风：三道风痕
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .17f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(color);
                c.drawLine(x - s * .62f, y - s * .38f, x + s * .24f, y - s * .38f, p);
                c.drawLine(x - s * .30f, y, x + s * .66f, y, p);
                c.drawLine(x - s * .58f, y + s * .38f, x + s * .10f, y + s * .38f, p);
                p.setStyle(Paint.Style.FILL);
                p.setStrokeCap(Paint.Cap.BUTT);
                break;
            }
            case 2: // 霜星：六角雪花
                icon(c, x, y, size * .78f, 10, color);
                break;
            case 3: { // 生命：叶芽
                path.reset();
                path.moveTo(x, y + s * .72f);
                path.cubicTo(x - s * .05f, y - s * .10f, x - s * .62f, y - s * .16f, x - s * .58f, y - s * .70f);
                path.cubicTo(x + s * .34f, y - s * .66f, x + s * .50f, y - s * .04f, x, y + s * .72f);
                path.close();
                p.setColor(color);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .10f);
                p.setColor(alpha(0xff000000, 110));
                c.drawLine(x, y + s * .60f, x - s * .16f, y - s * .46f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case 4: // 雷光：闪电
                icon(c, x, y, size * .82f, 11, color);
                break;
            default: { // 虚空：缺口环 + 内星
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .19f);
                p.setColor(color);
                r2.set(x - s * .62f, y - s * .62f, x + s * .62f, y + s * .62f);
                c.drawArc(r2, 28, 300, false, p);
                p.setStyle(Paint.Style.FILL);
                circle(c, x, y, s * .20f, color);
                break;
            }
        }
    }

    // ─────────────────────────── relic ───────────────────────────
    /** 遗物图标：稀有度外环 + 流派色光晕 + 形状。 */
    public void relic(Canvas c, float x, float y, float size, int relicId, int schoolColor, int rarityColor) {
        if (c == null || size <= 0) return;
        float s = size * .5f;
        int sc = schoolColor == 0 ? SCHOOL_COLOR[Math.floorMod(relicId, 6)] : schoolColor;
        int rc = rarityColor == 0 ? alpha(MUTED, 200) : rarityColor;
        glow(c, x, y, s * 1.22f, alpha(sc, 58));
        circle(c, x, y, s * .94f, alpha(0xff000000, 120));
        gradientRect(c, x - s * .90f, y - s * .90f, s * 1.80f, s * 1.80f, s * .90f, alpha(sc, 44), alpha(0xff000000, 150));
        ring(c, x, y, s * .90f, alpha(sc, 170), Math.max(1f, size * .035f));
        ring(c, x, y, s * 1.02f, alpha(rc, 190), Math.max(1f, size * .028f));
        icon(c, x, y, size * .86f, relicId, sc);
    }

    /** 简化版（不区分稀有度）用于紧凑列表。 */
    public void relic(Canvas c, float x, float y, float size, int relicId) {
        relic(c, x, y, size, relicId, 0, 0);
    }

    // ─────────────────────────── sprite / avatar / cover ───────────────────────────
    /**
     * 绘制图集单位（160x160 单元）。width 为绘制宽度；锚点为角色脚底中心附近，
     * 与 Art.avatar 的比例一致。
     */
    public void sprite(Canvas c, int row, int frame, float x, float y, float width, boolean flip, int alphaByte) {
        if (c == null || width <= 0) return;
        Bitmap bmp = art2 == null ? null : art2.actorCell(row, frame);
        if (bmp == null || bmp.isRecycled()) {
            int col = SCHOOL_COLOR[Math.floorMod(row, 6)];
            circle(c, x, y - width * .1f, width * .34f, alpha(col, 90));
            ring(c, x, y - width * .1f, width * .34f, alpha(col, 200), Math.max(1f, width * .04f));
            return;
        }
        dst.set(x - width * .5f, y - width * .595f, x + width * .5f, y + width * .405f);
        ip.setAlpha(Math.max(0, Math.min(255, alphaByte)));
        if (flip) {
            c.save();
            c.scale(-1f, 1f, x, y);
            c.drawBitmap(bmp, null, dst, ip);
            c.restore();
        } else {
            c.drawBitmap(bmp, null, dst, ip);
        }
        ip.setAlpha(255);
    }

    /** 角色头像/立绘。anim 驱动 4 帧循环，flip 控制朝向。 */
    public void avatar(Canvas c, float x, float y, float size, int row, float anim, boolean flip) {
        int frame = ((int) (anim * .9f)) & 3;
        sprite(c, Math.max(0, row), frame, x, y, size, flip, 255);
    }

    /** 生态插画，等比裁切填满目标矩形。 */
    public void cover(Canvas c, float x, float y, float w, float h, int biome, float alphaF) {
        if (c == null || w <= 0 || h <= 0) return;
        Bitmap bmp = art2 == null ? null : art2.cover(biome);
        float a = Math.max(0f, Math.min(1f, alphaF));
        if (bmp == null || bmp.isRecycled()) {
            int col = EDGE;
            gradientRect(c, x, y, w, h, 0, alpha(blend(PANEL_HI, col, .3f), (int) (a * 255)), alpha(PANEL, (int) (a * 255)));
            ring(c, (int) (x + w * .5f), (int) (y + h * .5f), Math.min(w, h) * .22f, alpha(col, (int) (a * 200)), 2f);
            return;
        }
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float targetRatio = w / h, srcRatio = bw / (float) bh;
        int sw, sh;
        if (srcRatio > targetRatio) { // 源更宽，裁左右
            sh = bh;
            sw = Math.max(1, Math.round(bh * targetRatio));
        } else {
            sw = bw;
            sh = Math.max(1, Math.round(bw / targetRatio));
        }
        int sx = (bw - sw) / 2, sy = (bh - sh) / 2;
        src.set(sx, sy, sx + sw, sy + sh);
        dst.set(x, y, x + w, y + h);
        ip.setAlpha((int) (a * 255));
        c.drawBitmap(bmp, src, dst, ip);
        ip.setAlpha(255);
    }

    /** 稀有度颜色 + 边框小徽章。 */
    public void rarityBadge(Canvas c, float x, float y, float w, float h, int rarity, String label) {
        int color = rarityColor(rarity);
        box(c, x, y, w, h, h * .34f, alpha(color, 30));
        stroke(c, x, y, w, h, h * .34f, alpha(color, 170), 1f);
        float size = fit(label == null ? "" : label, w - 10, h * .62f, 11f);
        textC(c, label == null ? "" : label, x + w * .5f, y + h * .5f, size, color, true);
    }

    public int rarityColor(int rarity) {
        switch (Math.floorMod(rarity, 4)) {
            case 0:
                return 0xff9aa8ac;
            case 1:
                return 0xff93c4ff;
            case 2:
                return 0xffc2a7ff;
            default:
                return 0xffeac487;
        }
    }

    /** 空状态占位块（未解锁 / 未发现）。 */
    public void locked(Canvas c, float x, float y, float w, float h, String label) {
        if (c == null || w <= 0 || h <= 0) return;
        box(c, x, y, w, h, 10, 0x33000000);
        stroke(c, x, y, w, h, 10, alpha(EDGE, 170), 1f);
        float size = fit(label == null ? "" : label, w - 12, Math.min(h * .6f, 14f), 11f);
        textC(c, label == null ? "" : label, x + w * .5f, y + h * .5f, size, MUTED, true);
    }

    /** 竖排滚动内容上/下渐隐遮罩（用于长列表边缘）。 */
    public void fadeEdges(Canvas c, float x, float y, float w, float h, int bgColor) {
        if (c == null || w <= 0 || h <= 0) return;
        int fh = Math.min(18, (int) (h * .18f));
        gradientRect(c, x, y, w, fh, 0, alpha(bgColor, 220), alpha(bgColor, 0));
        gradientRect(c, x, y + h - fh, w, fh, 0, alpha(bgColor, 0), alpha(bgColor, 220));
    }

    // ═══════════════════════════════════════════════════════════════
    //  v2.0 新增控件（Pact / Heat / Doctrine 支撑）
    //  仅新增方法，未改动任何既有方法签名。
    // ═══════════════════════════════════════════════════════════════

    /**
     * 小圆形数值徽记（heat 值、层数、数量角标）。
     *
     * <p>中心锚点 (x,y)，直径按 count 位数自适应（1 位 22、2 位 26、3 位以上 30）。
     * 深色底 + 彩色描边 + 高对比数字，任何背景上都可读。</p>
     *
     * @param count 数值；&lt;0 显示 "0"，&gt;99 显示 "99+"
     * @param color 主色（描边 / 文字 / 内发光）
     */
    public void badge(Canvas c, float x, float y, int count, int color) {
        if (c == null) return;
        int v = count;
        String s;
        if (v > 99) s = "99+";
        else if (v < 0) s = "0";
        else s = String.valueOf(v);

        float d = (s.length() >= 3) ? 30f : (s.length() == 2 ? 26f : 22f);
        float rad = d * .5f;

        // 外发光 + 深底，保证在浅色/插画背景上也清晰
        glow(c, x, y, rad * 1.9f, alpha(color, 90));
        circle(c, x, y, rad, alpha(0xff0b141b, 235));
        ring(c, x, y, rad, alpha(color, 220), Math.max(1.5f, d * .08f));
        // 内圈高光
        circle(c, x, y, rad * .72f, alpha(color, 38));

        float size = (s.length() >= 3) ? 10.5f : (s.length() == 2 ? 12f : 13f);
        textC(c, s, x, y + .5f, size, color, true);
    }

    /**
     * 一排彩色小标签（流派 / 稀有度 / 机制关键词）。
     *
     * <p>自左向右顺排，间距 {@code gap}；每个标签宽度按文字实测 + 内边距，
     * 高度 22。返回<b>整排总宽度</b>，便于调用方做居中或右对齐。</p>
     *
     * @param labels 标签文字数组，null / 空串会被跳过
     * @param colors 每项颜色；长度不足时回退到 MUTED
     * @param gap    标签之间的水平间距（逻辑像素），建议 6~10
     * @return 整排总宽度（不含起始 x）
     */
    public float chipRow(Canvas c, float x, float y, String[] labels, int[] colors, float gap) {
        if (c == null || labels == null || labels.length == 0) return 0f;
        float g = Math.max(0f, gap);
        float cursor = 0f;
        final float h = 22f, padX = 9f;

        for (int i = 0; i < labels.length; i++) {
            String s = labels[i];
            if (s == null) s = "";
            s = s.trim();
            if (s.isEmpty()) continue;

            int col = (colors != null && i < colors.length && colors[i] != 0) ? colors[i] : MUTED;
            float size = 11f;
            float tw = measure(s, size, true);
            float w = tw + padX * 2;

            // 胶囊底 + 描边 + 左侧小色点
            box(c, x + cursor, y, w, h, h * .5f, alpha(col, 30));
            stroke(c, x + cursor, y, w, h, h * .5f, alpha(col, 175), 1f);
            textC(c, s, x + cursor + w * .5f, y + h * .5f, size, col, true);

            cursor += w + g;
        }
        // 末尾多算了一个 gap，扣掉
        return Math.max(0f, cursor - (cursor > 0 ? g : 0f));
    }

    /**
     * 可开关的卡片（Pact 词条）。整卡可点击。
     *
     * <p>三层视觉状态：<br>
     * · <b>开</b>：accent 2px 描边 + 外发光 + 右上角「已启用」角标；<br>
     * · <b>关</b>：EDGE 1px 描边 + 压暗；<br>
     * · 整卡覆盖命中区（{@code id}），高度应 ≥ 44（触摸规范）。</p>
     *
     * @param title  标题（如「精英狂潮」），过长自动缩字号
     * @param desc   说明（如「精英出现频率翻倍，遗物稀有度 +1」），自动按字符换行
     * @param on     当前是否启用
     * @param accent 启用时的高亮色（建议用流派色或 heat 色）
     */
    public void toggleCard(Canvas c, int id, float x, float y, float w, float h,
                           String title, String desc, boolean on, int accent) {
        if (c == null || w <= 0 || h <= 0) return;
        int ac = accent == 0 ? MINT : accent;

        // 卡片主体（复用 card() 的统一质感）
        card(c, x, y, w, h, on ? ac : EDGE, on, false);
        hit(id, x, y, w, h);

        float padX = 14f, padY = 12f;
        // 左侧状态竖条：开=实心 accent，关=暗 EDGE
        box(c, x + 1.5f, y + 8, 3f, h - 16, 1.5f, on ? ac : alpha(EDGE, 200));

        // 标题（给右侧开关留 46px）
        float titleMax = w - padX * 2 - 46f;
        String t = title == null ? "" : title;
        float tSize = fit(t, titleMax, 15f, 11f);
        text(c, t, x + padX, y + padY + 15f, tSize, on ? WHITE : alpha(WHITE, 210), true);

        // 描述（标题下方，自动换行，最多占满剩余高度）
        String d = desc == null ? "" : desc;
        if (!d.isEmpty()) {
            float dMaxW = w - padX * 2 - 34f;
            float dSize = 11.5f;
            float dTop = y + padY + 26f;
            float availH = (y + h) - dTop - 6f;
            // 放不下就缩字号（下限 10.5，仍高于最小可读 11 的边界内）
            if (wrapHeight(d, dMaxW, dSize) > availH && availH > 12) {
                dSize = Math.max(10.5f, dSize * (availH / wrapHeight(d, dMaxW, dSize)));
            }
            wrap(c, d, x + padX, dTop + 10f, dMaxW, dSize,
                    on ? alpha(MUTED, 230) : alpha(MUTED, 190));
        }

        // 右上角开关指示：胶囊轨道 + 滑块（与 SetScreen 同语言）
        float sw = 38f, sh = 20f;
        float sx = x + w - sw - 14f, sy = y + padY + 2f;
        box(c, sx, sy, sw, sh, sh * .5f, on ? alpha(ac, 80) : 0x44000000);
        stroke(c, sx, sy, sw, sh, sh * .5f, on ? alpha(ac, 210) : alpha(EDGE, 220), 1f);
        float kx = on ? sx + sw - sh * .5f : sx + sh * .5f;
        circle(c, kx, sy + sh * .5f, sh * .5f - 3.5f, on ? ac : alpha(MUTED, 190));
    }

    /**
     * 单选卡片（Doctrine 天赋牌）。整卡可点击。
     *
     * <p>与 {@link #toggleCard} 的区别：单选语义 —— 选中时更强调（2px 描边 +
     * 外发光 + 左上角选中勾），未选中为普通卡片。</p>
     *
     * @param selected 是否为当前选中项
     * @param accent   该项的强调色（建议用流派色）
     */
    public void selectCard(Canvas c, int id, float x, float y, float w, float h,
                           String title, String desc, boolean selected, int accent) {
        if (c == null || w <= 0 || h <= 0) return;
        int ac = accent == 0 ? MINT : accent;

        card(c, x, y, w, h, selected ? ac : EDGE, selected, false);
        hit(id, x, y, w, h);

        float padX = 14f, padY = 12f;

        // 左上角单选圆环
        float rc = 7f;
        float rx = x + padX + rc, ry = y + padY + rc + 1f;
        if (selected) {
            circle(c, rx, ry, rc, ac);
            circle(c, rx, ry, rc * .42f, alpha(BG, 255));
        } else {
            ring(c, rx, ry, rc, alpha(MUTED, 150), 1.5f);
        }

        // 标题（让开单选圆环）
        float titleX = x + padX + rc * 2 + 9f;
        float titleMax = (x + w - 12f) - titleX;
        String t = title == null ? "" : title;
        float tSize = fit(t, titleMax, 15f, 11f);
        text(c, t, titleX, y + padY + 14f, tSize, selected ? ac : alpha(WHITE, 215), true);

        // 描述
        String d = desc == null ? "" : desc;
        if (!d.isEmpty()) {
            float dMaxW = w - padX * 2;
            float dSize = 11.5f;
            float dTop = y + padY + 28f;
            float availH = (y + h) - dTop - 8f;
            if (wrapHeight(d, dMaxW, dSize) > availH && availH > 12) {
                dSize = Math.max(10.5f, dSize * (availH / wrapHeight(d, dMaxW, dSize)));
            }
            wrap(c, d, x + padX, dTop + 10f, dMaxW, dSize,
                    selected ? alpha(MUTED, 235) : alpha(MUTED, 200));
        }

        // 选中时右上角小勾
        if (selected) {
            float cxp = x + w - 16f, cyp = y + 16f;
            line(c, cxp - 5f, cyp, cxp - 1.5f, cyp + 4f, ac, 2.2f);
            line(c, cxp - 1.5f, cyp + 4f, cxp + 6f, cyp - 5f, ac, 2.2f);
        }
    }

    /**
     * 分段计量条（heat 1–3 级）。
     *
     * <p>画 {@code max} 个等宽分段，前 {@code value} 段以 color 填充，
     * 其余为暗轨道；每段带圆角与内高光。宽度自动等分并留 3px 间隙。</p>
     *
     * @param value 当前值（会被钳制到 0..max）
     * @param max   段数（建议 3，对应 heat 1–3）
     * @param color 填充色（建议按 heat 等级渐变：青→金→红）
     */
    public void meter(Canvas c, float x, float y, float w, int value, int max, int color) {
        if (c == null || w <= 0 || max <= 0) return;
        final float h = 10f, gap = 3f;
        int n = Math.min(max, 12);            // 上限保护，避免 segment 宽度为 0
        int v = Math.max(0, Math.min(n, value));
        float sw = (w - gap * (n - 1)) / n;
        if (sw <= 0) return;

        for (int i = 0; i < n; i++) {
            float sx = x + i * (sw + gap);
            boolean on = i < v;
            // 轨道
            box(c, sx, y, sw, h, 2.5f, 0xff1b2a33);
            if (on) {
                gradientRect(c, sx, y, sw, h, 2.5f,
                        lighten(color, .16f), alpha(blend(color, 0xff000000, .18f), 245));
                // 顶部高光
                box(c, sx + 2, y + 1.2f, Math.max(0f, sw - 4), 1.2f, 0, 0x33ffffff);
            }
            stroke(c, sx, y, sw, h, 2.5f, on ? alpha(color, 200) : 0x22ffffff, 1f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  分页 / 网格（v2.3 新增，仅追加）
    //  用途：构筑页（GameView.drawInventory）的遗物列表此前按 7 列无上限铺开，
    //        第 6 行底边 856 已经画到 810 之外，还压住「武器进化」标题。
    //        这里只提供纯算术（不画、不登记命中），让调用方自己决定画什么。
    // ═══════════════════════════════════════════════════════════════

    /**
     * 每页容量。
     *
     * <p>调用方用它算「第几页」「共几页」，不必自己写 {@code (total + per - 1) / per}
     * 这类容易差一的表达式。{@code cols}/{@code rows} 任一 ≤ 0 时返回 0（表示不可分页），
     * 调用方应当退回「单页 + 折叠」而不是除零。</p>
     */
    public static int pageSize(int cols, int rows) {
        return cols <= 0 || rows <= 0 ? 0 : cols * rows;
    }

    /** 总页数；{@code per} ≤ 0 时返回 1（空列表也总有第 1 页）。 */
    public static int pageCount(int total, int per) {
        if (per <= 0) return 1;
        int t = Math.max(0, total);
        return Math.max(1, (t + per - 1) / per);
    }

    /** 把页码钳制到 {@code 0..pageCount-1}；用于数据变少（遗物被折算）时自愈。 */
    public static int clampPage(int page, int total, int per) {
        return Math.max(0, Math.min(pageCount(total, per) - 1, page));
    }

    /**
     * 第 {@code k} 个格子在网格中的左上角 x。
     *
     * <p>参数顺序刻意是 <b>(k, cols, cellW, gapX)</b> 而不是「行列先行」：
     * 调用方通常只有一个线性下标（遗物 id 或遍历计数），让它自己
     * {@code % / ÷} 再传两次坐标，等于把同一处算术抄两遍，改列数时必漏一处。</p>
     *
     * @param k     线性下标（≥ 0）
     * @param cols  每行列数（≤ 0 时按 1 处理）
     */
    public static float gridX(int k, int cols, float cellW, float gapX) {
        int c = cols <= 0 ? 1 : cols;
        return Math.max(0, k) % c * (cellW + gapX);
    }

    /** 第 {@code k} 个格子在网格中的左上角 y；行距 = cellH + gapY。 */
    public static float gridY(int k, int cols, float cellH, float gapY) {
        int c = cols <= 0 ? 1 : cols;
        return Math.max(0, k) / c * (cellH + gapY);
    }

    /**
     * 网格总高度：{@code rows*cellH + (rows-1)*gapY}。用于校验「不会画出底边界」。
     * <p>例：7 列 2 行、cellH 86、gapY 10 → 2*86 + 10 = 182。
     */
    public static float gridHeight(int rows, float cellH, float gapY) {
        int r = Math.max(0, rows);
        return r == 0 ? 0f : r * cellH + (r - 1) * gapY;
    }

    /**
     * 「还有 N 件」折叠角标：右下角一颗暗底胶囊，不登记命中。
     *
     * <p>构筑页的第 3 行之后不再铺卡片，改为在末格右下画这个角标——比
     * 直接截断更诚实（玩家知道列表没画完），也比滚动条省事（全作没有滚动容器）。</p>
     *
     * @param hidden 被折叠的条目数；≤ 0 时不画
     */
    public void moreBadge(Canvas c, float x, float y, int hidden) {
        if (c == null || hidden <= 0) return;
        // 文案不进 StringBuilder：调用频率虽低，但这里同样遵守「绘制期零分配」，
        // 用一个 0..99 的查表即可覆盖所有实际用例（遗物总数上限 60）。
        String s = hidden >= MORE_TEXT.length ? MORE_TEXT[MORE_TEXT.length - 1] : MORE_TEXT[hidden];
        float w = measure(s, 11f, true) + 18f, h = 24f;
        box(c, x - w, y - h, w, h, h * .5f, 0xD9101C23);
        stroke(c, x - w, y - h, w, h, h * .5f, alpha(EDGE, 210), 1f);
        textC(c, s, x - w * .5f, y - h * .5f, 11f, alpha(WHITE, 210), true);
    }

    /** "还有 N 件" 的预建文案，索引 0..99；越界时取最后一格。 */
    private static final String[] MORE_TEXT = buildMoreText();

    private static String[] buildMoreText() {
        String[] out = new String[100];
        for (int i = 0; i < out.length; i++) out[i] = "还有 " + i + " 件";
        return out;
    }
}
