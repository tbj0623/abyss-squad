package com.dilao.abysssquad;

import android.graphics.*;
import com.dilao.core.*;

/**
 * 代码原生绘制原语与图集访问。
 * <p>本类只负责"怎么画"，不负责"画什么"；所有业务绘制在 SceneArt / screen/* 中。
 * 全部 Paint / RectF / Path 复用，避免每帧分配。
 */
final class Art {

    final SpriteAtlas sprites;

    // ── 调色板（与 Ui 对齐）──
    static final int BG = 0xff0b141b, PANEL = 0xff14232c, PANEL_HI = 0xff1b2e38,
            EDGE = 0xff2c4248, WHITE = 0xffedf1e7, MUTED = 0xff94abae,
            MINT = 0xff9de7c1, GOLD = 0xffeac487, RED = 0xfff18f8b,
            VIOLET = 0xffc2a7ff, BLUE = 0xff93c4ff, PINK = 0xffff91c8;

    // ── 复用资源 ──
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    /**
     * 复用的 FontMetrics 容器。
     * <p>{@code Paint.getFontMetrics()} 每次调用都会 new 一个对象；这个方法在 HUD、
     * 飘字、菜单里每帧被调用几十次，60fps 下就是每秒数千次分配。
     * {@code Ui} 一直用的是 getFontMetrics(dst) 覆写版本，{@code Art} 这边漏了。
     */
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF r = new RectF();
    final Path path = new Path();
    final Typeface normal = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    final Typeface mono = Typeface.create("monospace", Typeface.NORMAL);

    Art(android.content.Context context) {
        sprites = new SpriteAtlas(context);
        t.setTypeface(normal);
    }

    void dispose() {
        if (sprites != null) sprites.dispose();
    }

    // ══════════════════════════════════════════════════════════════
    //  颜色工具
    // ══════════════════════════════════════════════════════════════

    int alpha(int color, int a) {
        return ((a < 0 ? 0 : a > 255 ? 255 : a) << 24) | (color & 0xffffff);
    }

    int blend(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xff000000
                | ((int) (ar + (br - ar) * f) << 16)
                | ((int) (ag + (bg - ag) * f) << 8)
                | (int) (ab + (bb - ab) * f);
    }

    int shade(int color, float mul) {
        int a = (color >>> 24);
        int r = Math.min(255, Math.max(0, (int) (((color >> 16) & 0xff) * mul)));
        int g = Math.min(255, Math.max(0, (int) (((color >> 8) & 0xff) * mul)));
        int b = Math.min(255, Math.max(0, (int) ((color & 0xff) * mul)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ══════════════════════════════════════════════════════════════
    //  形状
    // ══════════════════════════════════════════════════════════════

    void box(Canvas c, float x, float y, float w, float h, float rad, int color) {
        if (c == null || w <= 0 || h <= 0) return;
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, rad, rad, p);
    }

    void stroke(Canvas c, float x, float y, float w, float h, float rad, int color, float width) {
        if (c == null || w <= 0 || h <= 0) return;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    void line(Canvas c, float x, float y, float a, float b, int color, float width) {
        if (c == null) return;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x, y, a, b, p);
        p.setStyle(Paint.Style.FILL);
    }

    void circle(Canvas c, float x, float y, float rad, int color) {
        if (c == null || rad <= 0) return;
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, rad, p);
    }

    void ring(Canvas c, float x, float y, float rad, int color, float width) {
        if (c == null || rad <= 0) return;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        c.drawCircle(x, y, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    void diamond(Canvas c, float x, float y, float w, int color) {
        if (c == null || w <= 0) return;
        path.reset();
        path.moveTo(x, y - w);
        path.lineTo(x + w * .7f, y);
        path.lineTo(x, y + w);
        path.lineTo(x - w * .7f, y);
        path.close();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    void tri(Canvas c, float x, float y, float w, int color, float rotation) {
        if (c == null || w <= 0) return;
        path.reset();
        path.moveTo(w, 0);
        path.lineTo(-w * .7f, w * .8f);
        path.lineTo(-w * .7f, -w * .8f);
        path.close();
        c.save();
        c.translate(x, y);
        c.rotate(rotation);
        p.setColor(color);
        c.drawPath(path, p);
        c.restore();
    }

    // 发光：用一张 64×64 的软圆位图缩放绘制，替代 5 次叠加圆（软件渲染下省 80%）
    // 多槽缓存：单槽时一帧出现两种颜色就会反复 createBitmap，
    // 每张新位图都要上传成 GPU 纹理，在模拟器上足以拖出秒级帧并触发 ANR。
    private static final int GLOW_SLOTS = 8;
    private Bitmap[] glowBmp = new Bitmap[GLOW_SLOTS];
    private int[] glowBmpColor = new int[GLOW_SLOTS];
    private int glowCursor;

    private Bitmap glowFor(int color) {
        for (int i = 0; i < GLOW_SLOTS; i++)
            if (glowBmp[i] != null && glowBmpColor[i] == color) return glowBmp[i];
        int slot = glowCursor;
        glowCursor = (glowCursor + 1) % GLOW_SLOTS;
        try {
            if (glowBmp[slot] != null) glowBmp[slot].recycle();
            int size = 64;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas gc = new Canvas(bmp);
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setShader(new RadialGradient(size * .5f, size * .5f, size * .5f,
                    new int[]{alpha(color, 70), alpha(color, 34), alpha(color, 10), 0x00000000},
                    new float[]{0f, .35f, .7f, 1f}, Shader.TileMode.CLAMP));
            gc.drawCircle(size * .5f, size * .5f, size * .5f, gp);
            glowBmp[slot] = bmp;
            glowBmpColor[slot] = color;
            return bmp;
        } catch (Throwable t) {
            glowBmp[slot] = null;
            glowBmpColor[slot] = 0;
            return null;
        }
    }

    void glow(Canvas c, float x, float y, float rad, int color) {
        if (c == null || rad <= 0) return;
        Bitmap bmp = glowFor(color);
        if (bmp == null) return;
        r.set(x - rad, y - rad, x + rad, y + rad);
        c.drawBitmap(bmp, null, r, p);
    }

    // ── 渐变缓存：避免每帧 new Shader（软件渲染上开销极大）──
    private Shader cachedShader;
    private long cachedKey;
    private Bitmap vignetteBmp;
    private float vignetteStrength = -1, vignetteW, vignetteH;

    private void useShader(Shader s, long key) {
        if (key != cachedKey || cachedShader != s) {
            cachedShader = s;
            cachedKey = key;
        }
        p.setShader(cachedShader);
    }

    /** 渐变规范高度：所有竖向渐变都建在这个高度上，绘制时纵向拉伸。 */
    private static final int GRAD_CANON = 64;

    void gradientRect(Canvas c, float x, float y, float w, float h, int top, int bottom) {
        if (c == null || w <= 0 || h <= 0) return;
        // 只按颜色缓存：若把高度也算进 key，每个新高度都会新建 Shader，
        // HWUI 就要现场编译一次 GLSL（模拟器上可卡数秒并触发 ANR）。
        long key = ((long) top << 32) ^ (bottom & 0xffffffffL);
        if (cachedShader == null || cachedKey != key) {
            cachedShader = new LinearGradient(0, 0, 0, GRAD_CANON, top, bottom, Shader.TileMode.CLAMP);
            cachedKey = key;
        }
        p.setShader(cachedShader);
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.translate(x, y);
        c.scale(1f, h / GRAD_CANON);
        c.drawRect(0, 0, w, GRAD_CANON, p);
        c.restore();
        p.setShader(null);
    }

    /** 暗角：烘焙成位图后复用，避免每帧构造 RadialGradient。 */
    void vignette(Canvas c, float w, float h, float strength) {
        if (c == null || w <= 0 || h <= 0 || strength <= 0) return;
        if (vignetteBmp == null || vignetteStrength != strength
                || vignetteW != w || vignetteH != h) {
            try {
                int iw = Math.max(8, (int) w / 4), ih = Math.max(8, (int) h / 4);
                vignetteBmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
                Canvas vc = new Canvas(vignetteBmp);
                RadialGradient g = new RadialGradient(iw * .5f, ih * .5f,
                        Math.max(iw, ih) * .72f,
                        new int[]{0x00000000, alpha(0x000000, (int) (strength * 255))},
                        new float[]{.55f, 1f}, Shader.TileMode.CLAMP);
                Paint vp = new Paint(Paint.FILTER_BITMAP_FLAG);
                vp.setShader(g);
                vc.drawRect(0, 0, iw, ih, vp);
                vignetteStrength = strength;
                vignetteW = w;
                vignetteH = h;
            } catch (Throwable t) {
                vignetteBmp = null;
                return;
            }
        }
        if (vignetteBmp != null) {
            r.set(0, 0, w, h);
            c.drawBitmap(vignetteBmp, null, r, p);
        }
    }

    /** 释放缓存位图。 */
    void recycleCaches() {
        if (vignetteBmp != null) { vignetteBmp.recycle(); vignetteBmp = null; }
        for (int i = 0; i < GLOW_SLOTS; i++) { if (glowBmp[i] != null) { glowBmp[i].recycle(); glowBmp[i] = null; } glowBmpColor[i] = 0; }
        cachedShader = null;
        cachedKey = 0;
    }

    void scanlines(Canvas c, float w, float h, int a) {
        if (c == null || a <= 0) return;
        p.setColor(a(0xffffff, a));
        p.setStrokeWidth(1);
        for (float y = 0; y < h; y += 3) c.drawLine(0, y, w, y, p);
    }

    private int a(int rgb, int al) { return alpha(rgb | 0xff000000, al); }

    // ══════════════════════════════════════════════════════════════
    //  文字
    // ══════════════════════════════════════════════════════════════

    void text(Canvas c, String s, float x, float y, float size, int color, boolean strong) {
        if (c == null || s == null || s.isEmpty()) return;
        t.setColor(color);
        t.setTypeface(strong ? bold : normal);
        t.setTextSize(size);
        c.drawText(s, x, y, t);
    }

    void center(Canvas c, String s, float cx, float cy, float size, int color, boolean strong) {
        if (c == null || s == null || s.isEmpty()) return;
        t.setTypeface(strong ? bold : normal);
        t.setTextSize(size);
        t.setColor(color);
        t.getFontMetrics(fm);
        float baseline = cy - (fm.ascent + fm.descent) * .5f;
        c.drawText(s, cx - t.measureText(s) * .5f, baseline, t);
    }

    void right(Canvas c, String s, float rx, float y, float size, int color, boolean strong) {
        if (c == null || s == null || s.isEmpty()) return;
        t.setTypeface(strong ? bold : normal);
        t.setTextSize(size);
        float w = t.measureText(s);
        text(c, s, rx - w, y, size, color, strong);
    }

    void small(Canvas c, String s, float x, float y, float size, int color) {
        if (c == null || s == null || s.isEmpty()) return;
        t.setColor(color);
        t.setTypeface(mono);
        t.setTextSize(size);
        c.drawText(s, x, y, t);
    }

    /** 带 1px 深色描边的文字，用于战斗飘字与高对比标题。 */
    void textShadow(Canvas c, String s, float x, float y, float size, int color, boolean strong) {
        if (c == null || s == null || s.isEmpty()) return;
        tp.set(t);
        tp.setColor(alpha(0xff000000, 200));
        tp.setTypeface(strong ? bold : normal);
        tp.setTextSize(size);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(Math.max(1.5f, size * .11f));
        tp.setStrokeJoin(Paint.Join.ROUND);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        text(c, s, x, y, size, color, strong);
    }

    void centerShadow(Canvas c, String s, float cx, float cy, float size, int color, boolean strong) {
        if (c == null || s == null || s.isEmpty()) return;
        t.setTypeface(strong ? bold : normal);
        t.setTextSize(size);
        t.getFontMetrics(fm);
        float baseline = cy - (fm.ascent + fm.descent) * .5f;
        textShadow(c, s, cx - t.measureText(s) * .5f, baseline, size, color, strong);
    }

    void wrap(Canvas c, String s, float x, float y, float maxW, float size, int color) {
        wrap(c, s, x, y, maxW, size, color, size * 1.52f);
    }

    void wrap(Canvas c, String s, float x, float y, float maxW, float size, int color, float lineH) {
        if (c == null || s == null || s.isEmpty() || maxW <= 0) return;
        t.setTypeface(normal);
        t.setTextSize(size);
        t.setColor(color);
        float yy = y;
        for (String para : s.split("\n", -1)) {
            int i = 0;
            while (i < para.length()) {
                int n = t.breakText(para, i, para.length(), true, maxW, null);
                if (n <= 0) n = 1;
                c.drawText(para, i, i + n, x, yy, t);
                i += n;
                yy += lineH;
            }
        }
    }

    /** 中文按字符换行的行数估算（用于排版）。 */
    float wrapHeight(String s, float maxW, float size, float lineH) {
        if (s == null || s.isEmpty() || maxW <= 0) return 0;
        t.setTypeface(normal);
        t.setTextSize(size);
        float lines = 0;
        for (String para : s.split("\n", -1)) {
            int i = 0;
            if (para.isEmpty()) { lines++; continue; }
            while (i < para.length()) {
                int n = t.breakText(para, i, para.length(), true, maxW, null);
                if (n <= 0) n = 1;
                i += n;
                lines++;
            }
        }
        return lines * lineH;
    }

    // ══════════════════════════════════════════════════════════════
    //  进度条
    // ══════════════════════════════════════════════════════════════

    void bar(Canvas c, float x, float y, float w, float h, float amount, int color) {
        if (c == null || w <= 0 || h <= 0) return;
        float a = Math.max(0, Math.min(1, amount));
        box(c, x, y, w, h, h * .5f, 0xff263841);
        if (a > 0) box(c, x, y, Math.max(h, w * a), h, h * .5f, color);
        box(c, x + 1, y + .5f, Math.max(0, w * a - 2), Math.max(1, h * .28f), h * .2f,
                alpha(0xffffff, 40));
    }

    void barSeg(Canvas c, float x, float y, float w, float h, float amount, int color, int segs) {
        if (segs <= 1) { bar(c, x, y, w, h, amount, color); return; }
        float gap = 2;
        float segW = (w - gap * (segs - 1)) / segs;
        int filled = (int) Math.ceil(amount * segs);
        for (int i = 0; i < segs; i++) {
            box(c, x + i * (segW + gap), y, segW, h, Math.min(3, h * .4f),
                    i < filled ? color : 0xff263841);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  角色 / 遗物 / 门户
    // ══════════════════════════════════════════════════════════════

    /** 玩家角色（row = role 0..11）。 */
    void avatar(Canvas c, float x, float y, float size, int role, float anim, float aim, boolean down) {
        if (c == null) return;
        if (down) {
            c.save();
            c.translate(x, y);
            c.rotate(78);
            sprites.actor(c, role, 0, 0, 0, 76 * size, false, 130);
            c.restore();
            line(c, x - 8 * size, y - 10 * size, x + 8 * size, y + 6 * size, RED, 2.5f);
            line(c, x + 8 * size, y - 10 * size, x - 8 * size, y + 6 * size, RED, 2.5f);
            return;
        }
        int frame = (int) (anim * .9f) & 3;
        sprites.actor(c, role, frame, x, y, 76 * size, Math.cos(aim) < 0, 255);
    }

    /** 敌种（type 0..19 → 图集行 12+type）。 */
    void enemy(Canvas c, float x, float y, float size, int type, float anim, boolean flip, int alpha) {
        sprites.actor(c, 12 + Math.max(0, Math.min(19, type)), (int) (anim * .8f) & 3,
                x, y, size, flip, alpha);
    }

    /** 首领（idx 0..7 → 图集行 32+idx）。 */
    void boss(Canvas c, float x, float y, float size, int idx, float anim, boolean flip) {
        sprites.actor(c, 32 + Math.max(0, Math.min(7, idx)), (int) (anim * .6f) & 3,
                x, y, size, flip, 255);
    }

    /** 遗物图标（用 ui_v4 图集 + 流派色发光）。 */
    void relic(Canvas c, float x, float y, int relicId, float scale) {
        if (c == null) return;
        int id = Math.max(0, Math.min(Relics.COUNT - 1, relicId));
        int icon = Relics.ICON[id];
        int school = Relics.SCHOOL[id];
        int color = Content.SCHOOL_COLOR[school];
        float size = 40 * scale;
        glow(c, x, y, size * .85f, color);
        sprites.uiIcon(c, icon, x, y, size, color, 255);
        // 稀有度描边环
        ring(c, x, y, size * .62f, Content.RARITY_COLOR[Relics.RARITY[id]], 1.6f * scale);
    }

    /** 流派徽记（ui_v4 图集第 2 行前 6 格）。 */
    void schoolIcon(Canvas c, float x, float y, float size, int school) {
        int idx = 16 + Math.floorMod(school, 6);
        sprites.uiIcon(c, idx, x, y, size, 0, 255);
    }

    /** 职业小徽记（ui_v4 图集第 3 行前 12 格）。 */
    void roleIcon(Canvas c, float x, float y, float size, int role) {
        int idx = 32 + Math.floorMod(role, 12);
        sprites.uiIcon(c, idx, x, y, size, 0, 255);
    }

    /**
     * 画一帧角色（UI 用）：直接把 160×160 单元格缩放到给定矩形。
     */
    void spriteRect(Canvas c, int row, int frame, float x, float y, float w, float h, int alpha) {
        r.set(x, y, x + w, y + h);
        sprites.actorCell(c, row, frame, r, alpha);
    }

    /** 通用门/传送门。 */
    void portal(Canvas c, float x, float y, float s, float time, int accent) {
        if (c == null) return;
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        glow(c, 0, -70, 155, accent);
        box(c, -101, -222, 202, 246, 86, 0xff203b40);
        stroke(c, -101, -222, 202, 246, 86, shade(accent, .55f), 12);
        box(c, -73, -195, 146, 215, 65, 0xff102b2d);
        stroke(c, -76, -198, 152, 217, 67, accent, 3);
        for (int i = 0; i < 5; i++) ring(c, 0, -90, 20 + i * 10, alpha(accent, 40), 2);
        diamond(c, 0, -92 + (float) Math.sin(time) * 7, 39, accent);
        diamond(c, 0, -92 + (float) Math.sin(time) * 7, 23, 0xff386b64);
        box(c, -115, 12, 230, 18, 4, 0xff4b6865);
        box(c, -127, 30, 254, 18, 3, 0xff2c4445);
        box(c, -141, 48, 282, 15, 2, 0xff21383c);
        c.restore();
    }

    /** 营火。 */
    void torch(Canvas c, float x, float y, float time) {
        if (c == null) return;
        glow(c, x, y - 8, 52, GOLD);
        box(c, x - 5, y, 10, 25, 2, 0xff74604c);
        diamond(c, x, y - 10, 14 + (float) Math.sin(time * 5 + x) * 2, GOLD);
        diamond(c, x, y - 13, 7, 0xffffe5ab);
    }
}
