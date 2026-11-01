package com.dilao.abysssquad;

import android.content.Context;
import android.graphics.*;

/**
 * 共享的不可变图集：每个 View 只解码一次，永不逐帧生成位图。
 * <p>图集布局（v1.0）：
 * <ul>
 *   <li>actors_v4.png：640×6400，4 帧 × 40 行，单元格 160×160。
 *       行 0..11 职业、12..31 敌种、32..39 首领。</li>
 *   <li>props_v4.png：160×1920，12 行。</li>
 *   <li>floor_v4_0..5.png：1120×620。</li>
 *   <li>cover_v4_0..5.png：1000×600。</li>
 *   <li>ui_v4.png：1536×384，16×4 格，单元格 96×96。</li>
 * </ul>
 * 缺图时全部降级为空操作，不会崩溃。
 */
final class SpriteAtlas {

    static final int ACTOR_CELL = 160, ACTOR_ROWS = 40, ACTOR_FRAMES = 4;
    static final int UI_CELL = 96;

    private final Bitmap actors, props, uiSheet;
    /**
     * 地板与封面改为<b>按需加载</b>。
     *
     * <p>为什么：整套图集常驻 48.7 MB（actors 15.6 + 地板 6×2.6 + 封面 6×2.3 + 其它），
     * 而运行时同一时刻只会用到<b>当前生态</b>的一张地板和一张封面。
     * 也就是说一次性解码 6 个生态 = 纯浪费 6×2.6 + 6×2.3 ≈ 29 MB 常驻内存，
     * 在低端机上是直接的 OOM 风险与 GC 压力来源。
     *
     * <p>代价：切换生态（每 3 站一次）时会同步解码一张图（约 2.6 MB，几毫秒）。
     * 换房本来就有 0.75 秒转场，用户感知不到；换来的常驻内存下降接近 60%。
     */
    private final Bitmap[] floors = new Bitmap[Content6.BIOMES];
    private final Bitmap[] covers = new Bitmap[Content6.BIOMES];
    private final BitmapFactory.Options opt = new BitmapFactory.Options();
    private Context appContext;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final RectF cdst = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /** 与 Content.BIOME_COUNT 解耦，避免渲染层直接依赖 core 常量。 */
    private static final class Content6 { static final int BIOMES = 6; }

    SpriteAtlas(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
        opt.inScaled = false;
        actors = decode(context, "actors_v4", opt);
        props = decode(context, "props_v4", opt);
        uiSheet = decode(context, "ui_v4", opt);
        // 地板 / 封面不在这里解码，见 lazyFloor / lazyCover。
    }

    private Bitmap decode(Context context, String name, BitmapFactory.Options opt) {
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) return null;
            return BitmapFactory.decodeResource(context.getResources(), id, opt);
        } catch (Throwable t) {
            return null;
        }
    }

    boolean hasActors() { return actors != null; }

    /**
     * 画一帧角色/敌人/首领。
     *
     * @param row   图集行（0..39）
     * @param frame 帧号（0..3），自动取模
     * @param x     脚底中心的屏幕 x
     * @param y     脚底中心的屏幕 y
     * @param width 期望的显示宽度（像素）
     * @param flip  是否水平镜像
     * @param alpha 0..255
     */
    void actor(Canvas c, int row, int frame, float x, float y, float width, boolean flip, int alpha) {
        if (c == null || actors == null || width <= 0) return;
        row = Math.floorMod(row, ACTOR_ROWS);
        frame = Math.floorMod(frame, ACTOR_FRAMES);
        int left = frame * ACTOR_CELL;
        int top = row * ACTOR_CELL;
        src.set(left, top, left + ACTOR_CELL, top + ACTOR_CELL);
        // 脚底在单元格 y≈140：显示框按 160 等比缩放，再上移使脚底落在 y
        float h = width;
        float cx = flip ? -width * .5f : -width * .5f;
        c.save();
        c.translate(x, y);
        if (flip) c.scale(-1, 1);
        // 让 140/160 的位置对齐到 y
        float offset = width * (140f / 160f);
        dst.set(cx, -offset, cx + width, -offset + h);
        paint.setAlpha(clampAlpha(alpha));
        c.drawBitmap(actors, src, dst, paint);
        c.restore();
        paint.setAlpha(255);
    }

    /** 直接按矩形画一帧（用于 UI 小图标）。 */
    void actorCell(Canvas c, int row, int frame, RectF target, int alpha) {
        if (c == null || actors == null || target == null) return;
        row = Math.floorMod(row, ACTOR_ROWS);
        frame = Math.floorMod(frame, ACTOR_FRAMES);
        src.set(frame * ACTOR_CELL, row * ACTOR_CELL,
                (frame + 1) * ACTOR_CELL, (row + 1) * ACTOR_CELL);
        paint.setAlpha(clampAlpha(alpha));
        c.drawBitmap(actors, src, target, paint);
        paint.setAlpha(255);
    }

    // 关键性能点：UI 每帧会多次请求同一格，必须缓存，否则每帧 createBitmap 极慢
    private final Bitmap[] cellCache = new Bitmap[ACTOR_ROWS * ACTOR_FRAMES];

    Bitmap actorCellBitmap(int row, int frame) {
        if (actors == null) return null;
        row = Math.floorMod(row, ACTOR_ROWS);
        frame = Math.floorMod(frame, ACTOR_FRAMES);
        int idx = row * ACTOR_FRAMES + frame;
        Bitmap cached = cellCache[idx];
        if (cached != null) return cached;
        try {
            cached = Bitmap.createBitmap(actors, frame * ACTOR_CELL, row * ACTOR_CELL,
                    ACTOR_CELL, ACTOR_CELL);
            cellCache[idx] = cached;
            return cached;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 直接把图集里的一格画到目标矩形（零分配，不创建中间位图）。 */
    void actorCellTo(Canvas c, int row, int frame, RectF target, int alpha) {
        actorCell(c, row, frame, target, alpha);
    }

    /** 道具/障碍物：按单元格等比缩放绘制。 */
    void prop(Canvas c, int row, float x, float y, float w, float h) {
        if (c == null || props == null || w <= 0 || h <= 0) return;
        row = Math.floorMod(row, 12);
        src.set(0, row * ACTOR_CELL, ACTOR_CELL, (row + 1) * ACTOR_CELL);
        dst.set(x - w * .5f, y - h, x + w * .5f, y);
        c.drawBitmap(props, src, dst, paint);
    }

    /** UI 图集：index 0..63（16 列 × 4 行）。 */
    void uiIcon(Canvas c, int index, float x, float y, float size, int tint, int alpha) {
        if (c == null || uiSheet == null || size <= 0) return;
        int idx = Math.floorMod(index, 64);
        int col = idx % 16, row = idx / 16;
        src.set(col * UI_CELL, row * UI_CELL, (col + 1) * UI_CELL, (row + 1) * UI_CELL);
        dst.set(x - size * .5f, y - size * .5f, x + size * .5f, y + size * .5f);
        int old = paint.getAlpha();
        paint.setAlpha(clampAlpha(alpha));
        if (tint != 0) paint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(uiSheet, src, dst, paint);
        paint.setColorFilter(null);
        paint.setAlpha(old);
    }

    Bitmap floor(int biome) {
        int i = Math.floorMod(biome, floors.length);
        Bitmap b = floors[i];
        if (b == null && appContext != null) {
            b = floors[i] = decode(appContext, "floor_v4_" + i, opt);
        }
        return b;
    }

    Bitmap cover(int biome) {
        int i = Math.floorMod(biome, covers.length);
        Bitmap b = covers[i];
        if (b == null && appContext != null) {
            b = covers[i] = decode(appContext, "cover_v4_" + i, opt);
        }
        return b;
    }

    private static int clampAlpha(int a) { return a < 0 ? 0 : a > 255 ? 255 : a; }

    void dispose() {
        for (int i = 0; i < cellCache.length; i++) {
            if (cellCache[i] != null) { try { cellCache[i].recycle(); } catch (Throwable ignored) {} cellCache[i] = null; }
        }
        if (actors != null) actors.recycle();
        if (props != null) props.recycle();
        if (uiSheet != null) uiSheet.recycle();
        for (int i = 0; i < floors.length; i++) {
            if (floors[i] != null) { try { floors[i].recycle(); } catch (Throwable ignored) {} floors[i] = null; }
        }
        for (int i = 0; i < covers.length; i++) {
            if (covers[i] != null) { try { covers[i].recycle(); } catch (Throwable ignored) {} covers[i] = null; }
        }
        appContext = null;
    }
}
