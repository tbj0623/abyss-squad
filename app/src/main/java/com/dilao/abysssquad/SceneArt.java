package com.dilao.abysssquad;

import android.graphics.*;
import com.dilao.core.*;

/**
 * 战斗场景绘制：缓存地板层 + 每帧对象层 + 大气覆盖层 + 边框层。
 *
 * <p><b>分层与调用顺序</b>（见 GameView.drawArena）：
 * <ol>
 *   <li>{@link #bakeFloor} —— 每房间烘焙一次到 1120×620 位图：地板、种子化碎石/苔痕、墙体浮雕；</li>
 *   <li>{@link #objects} —— 每帧：障碍物、危险区、道具、炮台、召唤物、环境粒子；</li>
 *   <li>（实体、弹幕、特效由 GameView 绘制）；</li>
 *   <li>{@link #overlay} —— 每帧：暗角、颗粒、生态大气；</li>
 *   <li>{@link #frame} —— 每帧：四角框架、边光、首领红脉冲。</li>
 * </ol>
 *
 * <p><b>性能约定</b>：所有 Paint / RectF / Path / Shader 均为静态复用字段，
 * 绘制循环内零分配；环境粒子用确定性三角函数生成，不创建 Random。
 *
 * <p><b>对齐约定</b>：可走区域与碰撞完全一致 —— x∈[40,1080]、y∈[44,577]
 * （对应 {@code Game.move} 的 clamp 与 {@code Rooms.Navigation} 的 closed 判定），
 * 因此墙体浮雕严格压在碰撞带外侧。
 */
final class SceneArt {
    private SceneArt() {}

    // ── 战斗区与可走边界（与碰撞精确对齐）──────────────────────────
    private static final float W = 1120f, H = 620f;
    private static final float WALK_L = 40f, WALK_R = 1080f, WALK_T = 44f, WALK_B = 577f;
    private static final int BIOMES = 6;

    // ── 复用绘制对象（循环内绝不 new）──────────────────────────────
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final RectF R = new RectF();
    private static final RectF R2 = new RectF();
    private static final Path PATH = new Path();
    private static final Rect SRC = new Rect();

    private static RadialGradient vignette;
    private static Paint grainPaint;

    // ── 危险区配色：0 火 1 冰 2 毒 3 熔岩 4 虚空 ────────────────────
    private static final int[] HAZARD_MAIN = {
            0xffe8a06a, 0xff9bcfe8, 0xffa8c86a, 0xffff8a52, 0xffb79bff
    };
    private static final int[] HAZARD_HI = {
            0xffffd7a8, 0xffdff3ff, 0xffdcf0b0, 0xffffc09a, 0xffe0ccff
    };

    /** 障碍材质基色：0 石柱 1 书架 2 熔岩块 3 冰晶。 */
    private static final int[] BLOCK_BASE = {
            0xff5b6b64, 0xff7b5a3e, 0xff6b3a2c, 0xff93bdd8
    };

    // ══════════════════════════════════════════════════════════════
    //  1. 地板烘焙（每房间一次）
    // ══════════════════════════════════════════════════════════════

    /** 把地板 + 静态装饰 + 墙体浮雕烘焙到缓存位图。 */
    static void bakeFloor(Canvas c, Art a, Game g) {
        if (c == null || a == null || g == null) return;
        int biome = clampBiome(g.biome);

        drawFloorBase(c, a, biome);

        // 种子化：同房间每次一致，不同房间/布局不同
        java.util.Random rnd = new java.util.Random(
                g.seed * 31L + g.room * 971L + g.layout * 131L + (g.mirror ? 7 : 0));

        c.save();
        c.clipRect(WALK_L - 16f, WALK_T - 14f, WALK_R + 16f, WALK_B + 16f);
        // v2：先铺"地面散落物"（碎石/骨头/杂草/木屑），它们要有落地阴影
        scatterLitter(c, a, biome, rnd);
        scatterDebris(c, a, biome, rnd);          // 24~40 处碎石/苔痕/锈迹/书页/冰晶/岩浆纹
        scatterHighlights(c, a, biome, rnd);      // 8~14 处地砖高光/暗部
        biomeLandmark(c, a, biome, rnd);          // 生态大图案（全部避开中央）
        c.restore();

        // v2：墙角积灰 + 地面裂缝网（烘焙一次，不进每帧）
        bakeCornerGrime(c, biome, rnd);
        bakeFloorCracks(c, biome, rnd);

        drawWalls(c, a, biome);                   // 墙体浮雕（压在可走区外侧）
        drawWallSetPiece(c, a, biome);            // 贴墙的生态装置（炉口 / 裂隙等）
        settleCenter(c, biome);                   // 中央轻微压暗，保住低对比
    }

    // ── 地板底图 ────────────────────────────────────────────────
    private static void drawFloorBase(Canvas c, Art a, int biome) {
        Bitmap bmp = a.sprites.floor(biome);
        if (bmp != null) {
            if (bmp.getWidth() == (int) W && bmp.getHeight() == (int) H) {
                c.drawBitmap(bmp, 0, 0, null);
            } else {
                SRC.set(0, 0, bmp.getWidth(), bmp.getHeight());
                R.set(0, 0, W, H);
                c.drawBitmap(bmp, SRC, R, BP);
            }
            // 叠一层极淡的生态环境色，让三张底图更统一
            R.set(0, 0, W, H);
            P.setStyle(Paint.Style.FILL);
            P.setShader(null);
            P.setColor(col(Content.BIOME_FLOOR[biome], 46));
            c.drawRect(R, P);
            return;
        }
        // 缺图降级：整块渐变 + 抖动色块，仍然可玩
        LinearGradient grad = new LinearGradient(0, 0, 0, H,
                Content.BIOME_BG[biome], Content.BIOME_FLOOR[biome], Shader.TileMode.CLAMP);
        P.setShader(grad);
        R.set(0, 0, W, H);
        c.drawRect(R, P);
        P.setShader(null);
        for (int i = 0; i < 9; i++) {
            float x = 60 + i * 118f, y = 70 + (i * 79 % 470);
            oval(c, x, y, 92, 46, col(Content.BIOME_FLOOR[biome], 90));
        }
    }

    // ── 种子化碎屑 ──────────────────────────────────────────────
    private static void scatterDebris(Canvas c, Art a, int biome, java.util.Random rnd) {
        int count = 24 + rnd.nextInt(17);                 // 24..40
        int edge = Content.BIOME_EDGE[biome];
        int accent = accent(biome);
        for (int i = 0; i < count; i++) {
            float x = WALK_L + 12 + rnd.nextFloat() * (WALK_R - WALK_L - 24);
            float y = WALK_T + 12 + rnd.nextFloat() * (WALK_B - WALK_T - 24);
            // 中央保持低对比：不往 165px 内放任何高对比图案
            if (dist(x, y, 560, 310) < 165f) continue;
            float s = .6f + rnd.nextFloat() * .9f;
            int pick = rnd.nextInt(4);
            switch (biome) {
                case 0:   // 苔光：裂纹 + 苔斑 + 须根
                    if (pick == 0) {
                        crack(c, x, y, s * 16, col(edge, 70));
                    } else if (pick == 1) {
                        oval(c, x, y, s * 13, s * 8, col(0xff6c9d7a, 40));
                        oval(c, x + 4, y - 3, s * 6, s * 4, col(0xff8fc49a, 34));
                    } else if (pick == 2) {
                        line(c, x, y, x + s * 9, y - s * 4, col(edge, 58), 1f);
                        line(c, x + s * 9, y - s * 4, x + s * 15, y + s * 3, col(edge, 46), 1f);
                    } else {
                        circle(c, x, y, s * 1.6f, col(accent, 60));
                    }
                    break;
                case 1:   // 赤铜：铁锈流痕 + 铆钉 + 煤渣
                    if (pick == 0) {
                        line(c, x, y, x + s * 13, y + s * 7, col(0xff8a5a34, 66), 1.4f);
                        line(c, x + s * 3, y + s * 2, x + s * 11, y + s * 9, col(0xff6b4327, 50), 1f);
                    } else if (pick == 1) {
                        circle(c, x, y, s * 2.2f, col(0xffa37b52, 70));
                        circle(c, x, y, s * .8f, col(0xffd0a878, 60));
                    } else if (pick == 2) {
                        oval(c, x, y, s * 9, s * 5, col(0xff3a2a20, 54));
                    } else {
                        circle(c, x, y, s * 1.4f, col(0xffd08a4a, 44));
                    }
                    break;
                case 2:   // 月影：书页碎片 + 墨渍 + 星点
                    if (pick == 0) {
                        bookPage(c, x, y, s, (rnd.nextFloat() - .5f) * .8f);
                    } else if (pick == 1) {
                        oval(c, x, y, s * 10, s * 7, col(0xff14172a, 60));
                    } else if (pick == 2) {
                        diamond(c, x, y, s * 2.6f, col(0xffd8dcff, 54));
                    } else {
                        line(c, x, y, x + s * 12, y - s * 2, col(0xff5c5f8e, 50), 1f);
                    }
                    break;
                case 3:   // 霜蚀：冰晶 + 霜环 + 冻裂纹
                    if (pick == 0) {
                        iceShard(c, x, y, s * 6, col(0xffcfe9ff, 70));
                    } else if (pick == 1) {
                        oval(c, x, y, s * 12, s * 9, col(0xffbcdcf5, 26));
                    } else if (pick == 2) {
                        crack(c, x, y, s * 18, col(0xff9fd0ea, 56));
                    } else {
                        circle(c, x, y, s * 1.5f, col(0xffe8f7ff, 66));
                    }
                    break;
                case 4:   // 熔火：发光岩浆纹 + 焦裂 + 黑曜石
                    if (pick == 0) {
                        lavaVein(c, x, y, s, rnd);
                    } else if (pick == 1) {
                        crack(c, x, y, s * 17, col(0xff1d1210, 92));
                    } else if (pick == 2) {
                        tri(c, x, y, s * 5, col(0xff2a1a16, 100), rnd.nextFloat() * 6.28f);
                    } else {
                        circle(c, x, y, s * 1.5f, col(0xffff9a5a, 58));
                    }
                    break;
                default:  // 虚空：符文碎片 + 裂隙 + 紫色微粒
                    if (pick == 0) {
                        runeMark(c, x, y, s * 5, col(0xffb79bff, 58));
                    } else if (pick == 1) {
                        line(c, x, y, x + s * 14, y - s * 9, col(0xff4a3a78, 72), 1.6f);
                    } else if (pick == 2) {
                        diamond(c, x, y, s * 3.2f, col(0xff8a72d8, 48));
                    } else {
                        circle(c, x, y, s * 1.4f, col(0xffd9c7ff, 60));
                    }
                    break;
            }
        }
    }

    // ── 地砖高光 / 暗部 ─────────────────────────────────────────
    private static void scatterHighlights(Canvas c, Art a, int biome, java.util.Random rnd) {
        int count = 8 + rnd.nextInt(7);                  // 8..14
        int accent = accent(biome);
        for (int i = 0; i < count; i++) {
            float x = WALK_L + 30 + rnd.nextFloat() * (WALK_R - WALK_L - 60);
            float y = WALK_T + 30 + rnd.nextFloat() * (WALK_B - WALK_T - 60);
            if (dist(x, y, 560, 310) < 150f) continue;
            float w = 46 + rnd.nextFloat() * 96;
            float h = w * (.42f + rnd.nextFloat() * .34f);
            boolean lit = rnd.nextBoolean();
            if (lit) {
                oval(c, x, y, w * .5f, h * .5f, col(0xffffffff, 9));
                oval(c, x, y, w * .34f, h * .32f, col(accent, 12));
            } else {
                oval(c, x, y, w * .5f, h * .5f, col(0xff000000, 26));
                oval(c, x + w * .08f, y + h * .1f, w * .3f, h * .26f, col(0xff000000, 18));
            }
        }
    }

    // ── 生态大图案（全部偏离中央）────────────────────────────────
    private static void biomeLandmark(Canvas c, Art a, int biome, java.util.Random rnd) {
        int accent = accent(biome);
        int edge = Content.BIOME_EDGE[biome];
        switch (biome) {
            case 0: {  // 苔光：半埋的环形祭坛 + 苔藓
                float cx = 262, cy = 191;
                oval(c, cx, cy, 118, 74, col(0xff203a36, 120));
                oval(c, cx, cy, 104, 64, col(edge, 38));
                for (int i = 0; i < 3; i++) ring(c, cx, cy, 36 + i * 22, col(edge, 40 - i * 8), 2f - i * .4f);
                for (int i = 0; i < 12; i++) {
                    float ang = i * .5236f;
                    circle(c, cx + (float) Math.cos(ang) * 92, cy + (float) Math.sin(ang) * 56,
                            3.4f, col(accent, 40));
                }
                oval(c, cx - 30, cy + 22, 42, 20, col(0xff6f9f7d, 30));
                break;
            }
            case 1: {  // 赤铜：地面散热格栅 + 铆钉阵列
                float cx = 838, cy = 448;
                oval(c, cx, cy, 132, 76, col(0xff2a1f18, 130));
                for (int i = 0; i < 5; i++) {
                    float w = 96 - i * 16;
                    line(c, cx - w, cy - 26 + i * 13, cx + w, cy - 26 + i * 13,
                            col(0xff7a5535, 64 - i * 8), 3f);
                }
                for (int i = 0; i < 8; i++) {
                    float ang = i * .7854f;
                    circle(c, cx + (float) Math.cos(ang) * 120, cy + (float) Math.sin(ang) * 66,
                            2.6f, col(0xffb98a58, 70));
                }
                break;
            }
            case 2: {  // 月影：星图圆环 + 星座连线
                float cx = 868, cy = 176;
                ring(c, cx, cy, 92, col(0xff5d6199, 62), 1.6f);
                ring(c, cx, cy, 66, col(0xff5d6199, 48), 1.2f);
                ring(c, cx, cy, 34, col(0xff5d6199, 40), 1f);
                float[] px = {cx - 62, cx - 18, cx + 26, cx + 58, cx + 8, cx - 40};
                float[] py = {cy + 30, cy - 44, cy - 12, cy + 26, cy + 58, cy - 8};
                for (int i = 0; i < px.length; i++) {
                    int j = (i + 1) % px.length;
                    line(c, px[i], py[i], px[j], py[j], col(0xff8b90c8, 40), 1f);
                    circle(c, px[i], py[i], 2.4f, col(0xffe4e8ff, 76));
                }
                break;
            }
            case 3: {  // 霜蚀：放射状冰裂 + 冻湖
                float cx = 806, cy = 452;
                oval(c, cx, cy, 124, 72, col(0xff17303f, 120));
                oval(c, cx, cy, 104, 58, col(0xff9fd0ea, 16));
                for (int i = 0; i < 9; i++) {
                    float ang = i * .6981f + .3f;
                    float len = 62 + (i % 3) * 26;
                    float ex = cx + (float) Math.cos(ang) * len;
                    float ey = cy + (float) Math.sin(ang) * len * .62f;
                    line(c, cx, cy, ex, ey, col(0xffbfe4fb, 52), 1.4f);
                    line(c, (cx + ex) * .5f, (cy + ey) * .5f,
                            (cx + ex) * .5f + (float) Math.sin(ang) * 16,
                            (cy + ey) * .5f + (float) Math.cos(ang) * 10,
                            col(0xffbfe4fb, 36), 1f);
                }
                circle(c, cx, cy, 4f, col(0xffe8f7ff, 70));
                break;
            }
            case 4: {  // 熔火：贯穿场地的熔岩裂口
                float x = 196, y = 502;
                PATH.reset();
                PATH.moveTo(x - 30, y + 40);
                PATH.lineTo(x + 24, y + 6);
                PATH.lineTo(x + 12, y - 22);
                PATH.lineTo(x + 86, y - 54);
                PATH.lineTo(x + 148, y - 40);
                SP.setShader(null);
                SP.setStyle(Paint.Style.STROKE);
                SP.setStrokeWidth(16f);
                SP.setColor(col(0xff180d0a, 150));
                c.drawPath(PATH, SP);
                SP.setStrokeWidth(9f);
                SP.setColor(col(0xff7d2f1c, 170));
                c.drawPath(PATH, SP);
                SP.setStrokeWidth(3.4f);
                SP.setColor(col(0xffffa15a, 130));
                c.drawPath(PATH, SP);
                SP.setStyle(Paint.Style.FILL);
                glow(c, x + 60, y - 20, 66, col(0xffff7a3c, 255), .9f);
                break;
            }
            default: {  // 虚空：大型符文环
                float cx = 872, cy = 188;
                ring(c, cx, cy, 96, col(0xff7a64b0, 58), 1.8f);
                ring(c, cx, cy, 78, col(0xff7a64b0, 34), 1f);
                for (int i = 0; i < 8; i++) {
                    float ang = i * .7854f;
                    float rx = cx + (float) Math.cos(ang) * 86;
                    float ry = cy + (float) Math.sin(ang) * 86;
                    diamond(c, rx, ry, 5.5f, col(0xffb79bff, 54));
                }
                diamond(c, cx, cy, 26, col(0xff3a2d63, 130));
                diamond(c, cx, cy, 15, col(0xffb79bff, 40));
                line(c, cx - 110, cy + 66, cx + 96, cy - 72, col(0xff5b4a92, 44), 1.4f);
                break;
            }
        }
    }

    // ── 墙体浮雕（严格贴着碰撞带）────────────────────────────────
    private static void drawWalls(Canvas c, Art a, int biome) {
        int floor = Content.BIOME_FLOOR[biome];
        int edge = Content.BIOME_EDGE[biome];
        int stone = blend(shade(floor, .72f), 0xff000000, .34f);
        int stoneHi = blend(stone, edge, .30f);
        int seam = col(0xff000000, 90);

        // 四边墙基
        rect(c, 0, 0, W, WALK_T, stone);
        rect(c, 0, WALK_B, W, H - WALK_B, stone);
        rect(c, 0, WALK_T, WALK_L, WALK_B - WALK_T, stone);
        rect(c, WALK_R, WALK_T, W - WALK_R, WALK_B - WALK_T, stone);

        // 顶墙砖缝（两行错缝）
        for (float x = 0; x < W; x += 64f) line(c, x, 0, x, 22f, seam, 1f);
        for (float x = 32f; x < W; x += 64f) line(c, x, 22f, x, WALK_T, seam, 1f);
        line(c, 0, 22f, W, 22f, seam, 1f);
        // 底墙砖缝
        for (float x = 20f; x < W; x += 64f) line(c, x, WALK_B, x, WALK_B + 21f, seam, 1f);
        for (float x = 52f; x < W; x += 64f) line(c, x, WALK_B + 21f, x, H, seam, 1f);
        line(c, 0, WALK_B + 21f, W, WALK_B + 21f, seam, 1f);
        // 左右墙砖缝
        for (float y = WALK_T + 24f; y < WALK_B; y += 48f) {
            line(c, 0, y, WALK_L, y, seam, 1f);
            line(c, WALK_R, y, W, y, seam, 1f);
        }

        // 墙顶受光边
        line(c, 0, WALK_T - 1f, W, WALK_T - 1f, col(stoneHi, 150), 2f);
        line(c, WALK_L - 1f, WALK_T, WALK_L - 1f, WALK_B, col(stoneHi, 130), 2f);
        line(c, WALK_R + 1f, WALK_T, WALK_R + 1f, WALK_B, col(stoneHi, 130), 2f);
        line(c, 0, WALK_B + 1f, W, WALK_B + 1f, col(stoneHi, 110), 2f);

        // 可走区内侧投影（让墙有高度）
        for (int i = 0; i < 5; i++) {
            int al = 30 - i * 6;
            rect(c, WALK_L, WALK_T + i * 2f, WALK_R - WALK_L, 2f, col(0xff000000, al));
            rect(c, WALK_L + i * 2f, WALK_T, 2f, WALK_B - WALK_T, col(0xff000000, al));
            rect(c, WALK_R - 2f - i * 2f, WALK_T, 2f, WALK_B - WALK_T, col(0xff000000, al));
            rect(c, WALK_L, WALK_B - 2f - i * 2f, WALK_R - WALK_L, 2f, col(0xff000000, al - 6));
        }

        // 四角石墩
        cornerStone(c, 0, 0, stoneHi, edge);
        cornerStone(c, W - 60f, 0, stoneHi, edge);
        cornerStone(c, 0, H - 56f, stoneHi, edge);
        cornerStone(c, W - 60f, H - 56f, stoneHi, edge);

        // 可走区四角的生态色标（很淡，用于强化区域感）
        int ac = accent(biome);
        diamond(c, WALK_L, WALK_T, 6f, col(ac, 92));
        diamond(c, WALK_R, WALK_T, 6f, col(ac, 92));
        diamond(c, WALK_L, WALK_B, 6f, col(ac, 92));
        diamond(c, WALK_R, WALK_B, 6f, col(ac, 92));
    }

    private static void cornerStone(Canvas c, float x, float y, int hi, int edge) {
        rect(c, x, y, 60f, 56f, shade(hi, .82f));
        stroke(c, x + .5f, y + .5f, 59f, 55f, 3f, col(edge, 70), 1.2f);
        rect(c, x + 6f, y + 6f, 48f, 10f, col(0xffffffff, 12));
    }

    // ── 贴墙生态装置（只在上下墙面，不进入战场）──────────────────
    private static void drawWallSetPiece(Canvas c, Art a, int biome) {
        int ac = accent(biome);
        switch (biome) {
            case 1: {  // 赤铜：顶部炉口
                float x = 268, y = 0;
                rect(c, x - 54, y, 108, 34, 6, col(0xff17100c, 210));
                rect(c, x - 44, y + 6, 88, 26, 5, col(0xff2b1a12, 220));
                glow(c, x, 26, 52, col(0xffff8a3c, 255), 1f);
                rect(c, x - 30, y + 14, 60, 8, 3, col(0xffffb066, 150));
                for (int i = 0; i < 4; i++)
                    line(c, x - 40 + i * 26, y + 4, x - 40 + i * 26, y + 33, col(0xff3c2418, 180), 2f);
                break;
            }
            case 2: {  // 月影：顶墙书脊阵列
                float x = 700;
                rect(c, x, 4, 250, 26, 4, col(0xff141729, 170));
                for (int i = 0; i < 12; i++) {
                    float bx = x + 6 + i * 20;
                    int hgt = 16 + (i * 7 % 9);
                    rect(c, bx, 30 - hgt, 13, hgt, 2,
                            col(0xff000000 | ((0x50 + i * 13) << 16) | ((0x44 + i * 9) << 8) | 0x70, 150));
                }
                break;
            }
            case 3: {  // 霜蚀：顶墙垂冰
                for (int i = 0; i < 7; i++) {
                    float x = 120 + i * 132f;
                    tri(c, x, WALK_T - 4, 9f + (i % 3) * 4, col(0xffcfe9ff, 68), 1.5708f);
                }
                break;
            }
            case 4: {  // 熔火：底部熔岩缝
                rect(c, 120, WALK_B + 6, 880, 8, 4, col(0xffff7a3c, 70));
                rect(c, 200, WALK_B + 18, 700, 5, 2, col(0xffffa15a, 52));
                glow(c, 560, WALK_B + 10, 150, col(0xffff6a2a, 255), .6f);
                break;
            }
            case 5: {  // 虚空：顶墙裂隙
                PATH.reset();
                PATH.moveTo(430, 44);
                PATH.lineTo(468, 18);
                PATH.lineTo(452, 0);
                SP.setStyle(Paint.Style.STROKE);
                SP.setShader(null);
                SP.setStrokeWidth(9f);
                SP.setColor(col(0xff4a3570, 200));
                c.drawPath(PATH, SP);
                SP.setStrokeWidth(3f);
                SP.setColor(col(ac, 150));
                c.drawPath(PATH, SP);
                SP.setStyle(Paint.Style.FILL);
                break;
            }
            default: {  // 苔光：墙面垂藤
                for (int i = 0; i < 6; i++) {
                    float x = 90 + i * 176f;
                    float len = 12 + (i * 11 % 20);
                    line(c, x, 0, x + 4, len, col(0xff4e7468, 110), 2f);
                    circle(c, x + 4, len, 2.6f, col(0xff8fc49a, 80));
                }
                break;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  v2 场景丰富层（全部烘焙一次，不进每帧预算）
    // ══════════════════════════════════════════════════════════════

    /**
     * 地面散落物：碎石 / 骨头 / 杂草 / 木屑。
     * <p>v1 的 scatterDebris 只有"涂鸦式"的线条，缺少**体积**。
     * 这里每件散落物都带一圈落地阴影 + 顶面受光，看起来是"躺在"地上。
     */
    private static void scatterLitter(Canvas c, Art a, int biome, java.util.Random rnd) {
        int edge = Content.BIOME_EDGE[biome];
        int ac = accent(biome);
        int n = 30 + rnd.nextInt(14);                 // 30..43 件
        for (int i = 0; i < n; i++) {
            float x = WALK_L + 16 + rnd.nextFloat() * (WALK_R - WALK_L - 32);
            float y = WALK_T + 16 + rnd.nextFloat() * (WALK_B - WALK_T - 32);
            // 中央 190px 内不放（战斗可读性），但允许极小的碎粒
            boolean centerFree = dist(x, y, 560, 310) > 190f;
            float s = .55f + rnd.nextFloat() * 1.05f;
            int pick = rnd.nextInt(6);
            if (!centerFree && pick > 1) continue;

            switch (pick) {
                case 0: {   // 碎石：多边形 + 落地阴影 + 顶光
                    float w = s * (7 + rnd.nextFloat() * 9);
                    float h = w * (.48f + rnd.nextFloat() * .3f);
                    oval(c, x + 1.5f, y + h * .55f, w * .74f, h * .42f, col(0xff000000, 74));
                    int base = blend(Content.BIOME_FLOOR[biome], edge, .34f);
                    base = shade(base, .78f + rnd.nextFloat() * .5f);
                    PATH.reset();
                    int verts = 5;
                    for (int k = 0; k < verts; k++) {
                        double ang = k * Math.PI * 2 / verts + rnd.nextDouble() * .3;
                        float px = x + (float) Math.cos(ang) * w * (k % 2 == 0 ? 1f : .82f);
                        float py = y + (float) Math.sin(ang) * h * (k % 2 == 0 ? 1f : .84f);
                        if (k == 0) PATH.moveTo(px, py); else PATH.lineTo(px, py);
                    }
                    PATH.close();
                    P.setStyle(Paint.Style.FILL);
                    P.setShader(null);
                    P.setColor(base);
                    c.drawPath(PATH, P);
                    line(c, x - w * .6f, y - h * .3f, x + w * .1f, y - h * .55f,
                            col(shade(base, 1.45f), 150), 1f);
                    break;
                }
                case 1: {   // 骨头：两节 + 端头圆球
                    float ln = s * 11;
                    float ang = rnd.nextFloat() * 3.14f;
                    float ex = x + (float) Math.cos(ang) * ln;
                    float ey = y + (float) Math.sin(ang) * ln;
                    oval(c, x + 1, y + 2, ln * .5f, 2.2f * s, col(0xff000000, 60));
                    int bone = blend(0xffd8d2c0, Content.BIOME_FLOOR[biome], .30f);
                    line(c, x, y, ex, ey, bone, 2.4f * s);
                    circle(c, x, y, 2.2f * s, bone);
                    circle(c, ex, ey, 2.2f * s, bone);
                    line(c, x, y - .8f, ex, ey - .8f, col(shade(bone, 1.3f), 170), 1f);
                    break;
                }
                case 2: {   // 杂草：3~5 根弯叶
                    int blades = 3 + rnd.nextInt(3);
                    int green = blend(0xff4f7a52, Content.BIOME_FLOOR[biome], .34f);
                    for (int k = 0; k < blades; k++) {
                        float bx = x + (k - blades * .5f) * 2.6f * s;
                        float hgt = s * (7 + rnd.nextFloat() * 9);
                        float bend = (rnd.nextFloat() - .5f) * 8f * s;
                        line(c, bx, y, bx + bend * .5f, y - hgt * .6f,
                                col(green, 190), 1.2f);
                        line(c, bx + bend * .5f, y - hgt * .6f, bx + bend, y - hgt,
                                col(green, 160), 1f);
                    }
                    break;
                }
                case 3: {   // 木屑 / 铁屑
                    float w = s * (5 + rnd.nextFloat() * 8);
                    int wood = blend(0xff7a5a38, Content.BIOME_FLOOR[biome], .28f);
                    oval(c, x + 1, y + 1.6f, w * .6f, 2f, col(0xff000000, 56));
                    line(c, x, y, x + w, y - 1.6f, col(wood, 210), 1.8f);
                    line(c, x + .6f, y - .7f, x + w, y - 2.3f, col(shade(wood, 1.4f), 150), 1f);
                    break;
                }
                case 4: {   // 苔/霜/灰 的小片堆积（生态色）
                    int tintC = biome == 3 ? 0xffcfe9ff : (biome == 4 ? 0xff8a5a34 : ac);
                    oval(c, x, y, s * (5 + rnd.nextFloat() * 6), s * 2.6f,
                            col(tintC, 26 + rnd.nextInt(18)));
                    break;
                }
                default: {  // 极小的碎石粒（可以进中央，几乎不可见）
                    circle(c, x, y, .8f + s * .5f, col(shade(edge, .8f), 90));
                    break;
                }
            }
        }
    }

    /**
     * 墙角积灰：四角向内衰减的暗斑 + 少量蛛网/霉斑。
     * <p>这是最强的"房间有年头"线索，同时天然不干扰中央战斗。
     */
    private static void bakeCornerGrime(Canvas c, int biome, java.util.Random rnd) {
        int deep = blend(Content.BIOME_FLOOR[biome], 0xff000000, .62f);
        float[][] corners = {
                {WALK_L, WALK_T}, {WALK_R, WALK_T},
                {WALK_L, WALK_B}, {WALK_R, WALK_B}
        };
        for (float[] cn : corners) {
            float x = cn[0], y = cn[1];
            // 三层递减的椭圆，模拟从角落漫出的灰
            for (int i = 0; i < 3; i++) {
                float r = 92f - i * 26f;
                oval(c, x + (WALK_L == x ? r * .24f : -r * .24f),
                        y + (WALK_T == y ? r * .2f : -r * .2f),
                        r * .74f, r * .5f, col(deep, 26 - i * 6));
            }
            // 角落霉斑/积尘颗粒
            int dots = 18 + rnd.nextInt(14);
            for (int i = 0; i < dots; i++) {
                float a = rnd.nextFloat() * 6.283f;
                float rr = rnd.nextFloat() * 62f;
                float px = x + (WALK_L == x ? 1 : -1) * (float) Math.abs(Math.cos(a)) * rr;
                float py = y + (WALK_T == y ? 1 : -1) * (float) Math.abs(Math.sin(a)) * rr;
                float sz = .8f + rnd.nextFloat() * 1.7f;
                circle(c, px, py, sz, col(deep, 40 + rnd.nextInt(40)));
            }
            // 雪/霜 生态在角落加一点亮色霜花
            if (biome == 3) {
                for (int i = 0; i < 6; i++) {
                    float a = rnd.nextFloat() * 6.283f;
                    float rr = 18f + rnd.nextFloat() * 44f;
                    float px = x + (WALK_L == x ? 1 : -1) * (float) Math.abs(Math.cos(a)) * rr;
                    float py = y + (WALK_T == y ? 1 : -1) * (float) Math.abs(Math.sin(a)) * rr;
                    circle(c, px, py, 1.4f + rnd.nextFloat(), col(0xffdff2ff, 54));
                }
            }
        }
    }

    /**
     * 地面裂缝网：从 3~5 个"应力点"放射的不规则裂缝。
     * <p>比 v1 的独立短线更像"地壳被撕开"，且线宽有粗细变化。
     */
    private static void bakeFloorCracks(Canvas c, int biome, java.util.Random rnd) {
        int crackCol = blend(Content.BIOME_FLOOR[biome], 0xff000000, .48f);
        if (biome == 4) crackCol = blend(crackCol, 0xffff7a3c, .22f);   // 熔火裂缝透红光
        if (biome == 3) crackCol = blend(crackCol, 0xffbfe4fb, .26f);   // 霜蚀裂缝透冷光
        int nodes = 4 + rnd.nextInt(3);
        for (int n = 0; n < nodes; n++) {
            float ox = WALK_L + 70 + rnd.nextFloat() * (WALK_R - WALK_L - 140);
            float oy = WALK_T + 60 + rnd.nextFloat() * (WALK_B - WALK_T - 120);
            // 中央的应力点只允许细裂
            boolean center = dist(ox, oy, 560, 310) < 210f;
            int branches = 2 + rnd.nextInt(3);
            for (int b = 0; b < branches; b++) {
                float ang = rnd.nextFloat() * 6.283f;
                float x = ox, y = oy;
                float width = center ? 1f : 1.6f + rnd.nextFloat() * 1.1f;
                int segs = 2 + rnd.nextInt(4);
                for (int sIdx = 0; sIdx < segs; sIdx++) {
                    ang += (rnd.nextFloat() - .5f) * 1.1f;
                    float len = 16 + rnd.nextFloat() * 34;
                    float nx = x + (float) Math.cos(ang) * len;
                    float ny = y + (float) Math.sin(ang) * len * .74f;
                    if (nx < WALK_L + 6 || nx > WALK_R - 6 || ny < WALK_T + 6 || ny > WALK_B - 6) break;
                    // 暗底 + 一侧高光 = 有深度的裂口
                    line(c, x, y, nx, ny, col(crackCol, 168), width);
                    line(c, x, y - .8f, nx, ny - .8f, col(0xffffffff, 16), 1f);
                    x = nx; y = ny;
                    width *= .78f;
                    if (width < .8f) break;
                }
            }
        }
    }

    // ── 中央压暗，保证低对比 ─────────────────────────────────────
    private static void settleCenter(Canvas c, int biome) {
        // 用两组大椭圆做极淡的中心抑制，避免中央出现抢眼图案
        oval(c, 560, 310, 250, 168, col(0xff000000, 16));
        oval(c, 560, 310, 160, 104, col(0xff000000, 10));
        int ac = accent(biome);
        oval(c, 560, 310, 320, 200, col(ac, 8));
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 每帧对象层
    // ══════════════════════════════════════════════════════════════

    /** 障碍物、危险区、道具、炮台、召唤物、环境粒子。 */
    static void objects(Canvas c, Art a, Game g, float time) {
        if (c == null || a == null || g == null) return;
        int biome = clampBiome(g.biome);

        drawBlocks(c, a, g, biome);
        drawHazards(c, a, g, time);
        drawProps(c, a, g, time);
        drawTurrets(c, a, g, time);
        drawMinions(c, a, g, time);
        drawAmbient(c, biome, time);
    }

    // ── 障碍物：落地阴影 + 侧面 + 顶面 + 材质纹理 ────────────────
    private static void drawBlocks(Canvas c, Art a, Game g, int biome) {
        float rise = 9f;   // 侧面厚度：底边下沉，脚点仍对齐碰撞矩形
        for (Game.Block b : g.blocks) {
            if (b.w <= 0 || b.h <= 0) continue;
            int kind = b.kind < 0 || b.kind > 3 ? 0 : b.kind;
            int base = BLOCK_BASE[kind];
            base = blend(base, Content.BIOME_EDGE[biome], .26f);
            base = blend(base, accent(biome), .10f);
            float cx = b.x + b.w * .5f, by = b.y + b.h;

            // 落地阴影（椭圆，低透明）
            oval(c, cx + 2f, by + 5f, b.w * .60f, Math.max(5f, b.h * .26f), col(0xff000000, 96));
            oval(c, cx, by + 3f, b.w * .46f, Math.max(3f, b.h * .18f), col(0xff000000, 70));

            // 侧面（比顶面暗）
            rect(c, b.x, b.y + rise, b.w, b.h, col(shade(base, .46f), 255));
            // 顶面
            rect(c, b.x, b.y, b.w, b.h, base);
            // 顶面前缘受光
            rect(c, b.x + 1.5f, b.y + 1.5f, b.w - 3f, Math.max(3f, b.h * .30f),
                    col(shade(base, 1.34f), 150));
            // 外描边
            stroke(c, b.x + .5f, b.y + .5f, b.w - 1f, b.h - 1f, 4f,
                    col(shade(base, .34f), 230), 1.4f);

            drawBlockTexture(c, a, b, kind, biome, base);
        }
    }

    private static void drawBlockTexture(Canvas c, Art a, Game.Block b, int kind,
                                         int biome, int base) {
        float cx = b.x + b.w * .5f, cy = b.y + b.h * .5f;
        int seed = (int) (b.x * 73f + b.y * 131f);
        switch (kind) {
            case 1: {  // 书架：书脊
                int n = Math.max(3, (int) (b.w / 9f));
                for (int i = 0; i < n; i++) {
                    float x = b.x + 3f + i * (b.w - 6f) / n;
                    float hgt = b.h * (.42f + ((seed + i * 37) % 43) / 100f);
                    int tone = 0xff000000 | (((0x58 + (seed + i * 23) % 0x38) << 16)
                            | ((0x40 + (seed + i * 17) % 0x2c) << 8) | (0x38 + (seed + i * 11) % 0x28));
                    rect(c, x, b.y + b.h * .30f, Math.max(2f, (b.w - 6f) / n - 1.4f), hgt,
                            col(tone, 190));
                }
                break;
            }
            case 2: {  // 熔岩块：发光裂缝
                for (int i = 0; i < 2; i++) {
                    float x1 = b.x + b.w * (.22f + i * .46f);
                    float y1 = b.y + b.h * .18f;
                    float x2 = x1 + ((seed + i * 19) % 11 - 5);
                    float y2 = b.y + b.h * .82f;
                    line(c, x1, y1, x2, y2, col(0xffff8a3c, 170), 1.8f);
                    line(c, x1, y1, x2, y2, col(0xffffd7a8, 90), .8f);
                }
                glow(c, cx, cy, Math.max(b.w, b.h) * .55f, col(0xffff7a3c, 255), .5f);
                break;
            }
            case 3: {  // 冰晶：棱面 + 冷光
                tri(c, cx - b.w * .18f, cy - b.h * .10f, b.w * .20f, col(0xffe8f7ff, 70), 1.2f);
                tri(c, cx + b.w * .20f, cy + b.h * .06f, b.w * .16f, col(0xffcfe9ff, 56), 4.1f);
                line(c, b.x + b.w * .24f, b.y + b.h * .16f,
                        b.x + b.w * .52f, b.y + b.h * .74f, col(0xffe8f7ff, 60), 1.2f);
                glow(c, cx, cy, Math.max(b.w, b.h) * .5f, col(0xff9bcfe8, 255), .4f);
                break;
            }
            default: {  // 石柱：凿痕 + 苔点
                for (int i = 0; i < 3; i++) {
                    float x = b.x + b.w * (.20f + ((seed + i * 29) % 60) / 100f);
                    float y = b.y + b.h * (.24f + ((seed + i * 43) % 52) / 100f);
                    circle(c, x, y, 1.7f, col(0xff000000, 70));
                }
                oval(c, b.x + b.w * .68f, b.y + b.h * .72f, b.w * .16f, b.h * .10f,
                        col(0xff6f9f7d, 54));
                line(c, b.x + b.w * .18f, b.y + b.h * .66f,
                        b.x + b.w * .56f, b.y + b.h * .58f, col(0xff000000, 62), 1f);
                break;
            }
        }
    }

    // ── 危险区 ─────────────────────────────────────────────────
    private static void drawHazards(Canvas c, Art a, Game g, float time) {
        for (Game.Hazard h : g.hazards) {
            if (h.r <= 0) continue;
            int kind = h.kind < 0 || h.kind > 4 ? 0 : h.kind;
            int main = HAZARD_MAIN[kind];
            int hi = HAZARD_HI[kind];
            if (h.active > 0) {
                // 激活态：实心色块 + 边缘光 + 粒子
                glow(c, h.x, h.y, h.r * 1.35f, col(main, 255), .55f);
                oval(c, h.x, h.y, h.r, h.r * .96f, col(main, 92));
                oval(c, h.x, h.y, h.r * .68f, h.r * .64f, col(hi, 70));
                ring(c, h.x, h.y, h.r, col(hi, 220), 3f);
                ring(c, h.x, h.y, h.r * .52f, col(hi, 90), 1.6f);
                for (int i = 0; i < 6; i++) {
                    float ang = i * 1.0472f + time * 1.6f;
                    float rr = h.r * .62f;
                    circle(c, h.x + (float) Math.cos(ang) * rr,
                            h.y + (float) Math.sin(ang) * rr * .9f, 3.2f, col(hi, 210));
                }
                hazardGlyph(c, h, kind, main, hi, time);
            } else {
                // 预警态：收缩的预警环 + 脉冲
                float delay = 1.15f;
                float t = clamp01(Math.abs(h.clock) / delay);   // 1 → 0（逼近引爆）
                float pulse = .5f + .5f * (float) Math.sin(time * 9f + h.id * .7f);
                float outer = h.r * (1f + .34f * t);
                oval(c, h.x, h.y, h.r, h.r * .96f, col(main, 12 + (int) (26 * (1f - t))));
                ring(c, h.x, h.y, outer, col(main, 70 + (int) (70 * pulse)), 2f);
                ring(c, h.x, h.y, h.r, col(main, 130 + (int) (80 * (1f - t))), 2.4f);
                // 八段短弧，做出"虚线收缩"的读感
                for (int i = 0; i < 8; i++) {
                    if ((i & 1) != 0) continue;
                    float ang = i * .7854f + pulse * .18f;
                    float x1 = h.x + (float) Math.cos(ang) * outer * .82f;
                    float y1 = h.y + (float) Math.sin(ang) * outer * .80f;
                    float x2 = h.x + (float) Math.cos(ang) * outer;
                    float y2 = h.y + (float) Math.sin(ang) * outer * .96f;
                    line(c, x1, y1, x2, y2, col(hi, 150), 2.4f);
                }
                diamond(c, h.x, h.y, 8f + pulse * 3f, col(main, 150));
                diamond(c, h.x, h.y, 4f, col(hi, 190));
            }
        }
    }

    private static void hazardGlyph(Canvas c, Game.Hazard h, int kind, int main, int hi,
                                    float time) {
        switch (kind) {
            case 0:   // 火：向上升腾的火舌
                for (int i = 0; i < 3; i++) {
                    float yy = h.y - (time * 26f + i * 18f) % (h.r * 1.1f);
                    diamond(c, h.x + (i - 1) * 13f, yy, 5.5f, col(hi, 120));
                }
                break;
            case 1:   // 冰：六向冰棱
                for (int i = 0; i < 6; i++) {
                    float ang = i * 1.0472f + .52f;
                    line(c, h.x, h.y,
                            h.x + (float) Math.cos(ang) * h.r * .72f,
                            h.y + (float) Math.sin(ang) * h.r * .68f, col(hi, 110), 1.8f);
                }
                break;
            case 2:   // 毒：气泡
                for (int i = 0; i < 4; i++) {
                    float yy = h.y + h.r * .4f - (time * 18f + i * 15f) % (h.r * .9f);
                    circle(c, h.x + (i - 1.5f) * 11f, yy, 3.4f, col(hi, 120));
                }
                break;
            case 3:   // 熔岩：亮核环
                ring(c, h.x, h.y, h.r * .38f + (float) Math.sin(time * 6f) * 3f,
                        col(0xffffe0b0, 140), 2.2f);
                break;
            default:  // 虚空：旋转菱形
                for (int i = 0; i < 2; i++) {
                    float ang = time * 1.2f + i * 1.5708f;
                    line(c, h.x + (float) Math.cos(ang) * h.r * .70f,
                            h.y + (float) Math.sin(ang) * h.r * .66f,
                            h.x - (float) Math.cos(ang) * h.r * .70f,
                            h.y - (float) Math.sin(ang) * h.r * .66f, col(hi, 120), 1.6f);
                }
                break;
        }
    }

    // ── 道具 ───────────────────────────────────────────────────
    private static void drawProps(Canvas c, Art a, Game g, float time) {
        for (Game.Prop p : g.props) {
            float bob = (float) Math.sin(time * 2.2f + p.id) * 1.2f;
            if (p.opened) {
                // 残骸：几块碎木板 + 淡尘
                oval(c, p.x, p.y + 4, 17, 6, col(0xff000000, 70));
                rect(c, p.x - 14, p.y + 1, 11, 4, 1.5f, col(0xff5d4a38, 220));
                rect(c, p.x - 1, p.y + 4, 9, 3, 1.2f, col(0xff4a3a2c, 200));
                rect(c, p.x + 7, p.y, 7, 3.4f, 1f, col(0xff3f3125, 190));
                circle(c, p.x - 3, p.y - 5 + bob * .4f, 1.4f, col(0xff9c8a72, 60));
                continue;
            }
            int row;
            if (p.type == 0) row = 3;          // 木箱
            else if (p.type == 1) row = 4;     // 补给箱
            else row = 5 + Math.floorMod(p.type - 2, 6);   // 装饰

            // 阴影
            oval(c, p.x + 1f, p.y + 4f, p.type == 1 ? 24f : 19f, p.type == 1 ? 9f : 7f,
                    col(0xff000000, 92));
            a.sprites.prop(c, row, p.x, p.y + 3f + bob * .3f,
                    p.type == 1 ? 46 : 40, p.type == 1 ? 44 : 38);

            if (p.type == 1) {
                // 补给箱：金色菱形浮动标记
                float my = p.y - 52f + bob * 1.6f;
                glow(c, p.x, my, 17, col(0xffeac487, 255), .8f);
                diamond(c, p.x, my, 6f, col(0xffffe9b8, 240));
                diamond(c, p.x, my, 3.2f, col(0xffffffff, 220));
                line(c, p.x, my + 7f, p.x, my + 13f, col(0xffeac487, 120), 1.4f);
            }
        }
    }

    // ── 炮台 ───────────────────────────────────────────────────
    private static void drawTurrets(Canvas c, Art a, Game g, float time) {
        for (Game.Turret t : g.turrets) {
            float frac = clamp01(t.life / 10f);
            // 阴影与底座
            oval(c, t.x, t.y + 8f, 17f, 7f, col(0xff000000, 90));
            oval(c, t.x, t.y + 2f, 14f, 11f, col(0xff2b4a4c, 255));
            ring(c, t.x, t.y + 2f, 13f, col(0xff6fb7b4, 150), 1.6f);
            // 旋转炮管（用枪口方向近似：朝向 cooldown 相位）
            float ang = time * 1.4f + t.id;
            float bx = (float) Math.cos(ang) * 15f, by = (float) Math.sin(ang) * 15f;
            line(c, t.x, t.y, t.x + bx, t.y + by, col(0xff417273, 255), 6f);
            line(c, t.x, t.y, t.x + bx, t.y + by, col(0xff8fd6d3, 210), 2.2f);
            // 能量核心
            float pulse = .6f + .4f * (float) Math.sin(time * 7f + t.id);
            glow(c, t.x, t.y, 16f * pulse, col(0xff9de7c1, 255), .8f);
            circle(c, t.x, t.y, 5.2f, col(0xffe8fff2, 240));
            circle(c, t.x, t.y, 2.8f, col(0xffffffff, 250));
            // 剩余时间细条
            rect(c, t.x - 19, t.y + 16f, 38, 3f, 1.5f, col(0xff1a2b30, 220));
            rect(c, t.x - 19, t.y + 16f, 38 * frac, 3f, 1.5f, col(0xff9de7c1, 230));
        }
    }

    // ── 召唤物 ─────────────────────────────────────────────────
    private static void drawMinions(Canvas c, Art a, Game g, float time) {
        for (Game.Minion m : g.minions) {
            float frac = clamp01(m.life / 12f);
            float wob = (float) Math.sin(time * 5f + m.id) * 2.4f;
            // 拖尾（三层递减）
            for (int i = 3; i >= 1; i--) {
                float trail = i * 5.5f;
                circle(c, m.x - trail * .6f, m.y + wob * .4f + trail * .18f,
                        4.2f - i * .8f, col(0xffb79bff, 26 - i * 6));
            }
            glow(c, m.x, m.y + wob, 15, col(0xffb79bff, 255), .8f);
            circle(c, m.x, m.y + wob, 5.6f, col(0xffd9c7ff, 220));
            circle(c, m.x, m.y + wob, 3.2f, col(0xffffffff, 240));
            diamond(c, m.x, m.y + wob, 8.2f, col(0xffb79bff, (int) (110 * frac)));
        }
    }

    // ── 环境粒子（确定性，≤ 60 个）──────────────────────────────
    private static void drawAmbient(Canvas c, int biome, float time) {
        final int COUNT = 44;
        int accent = accent(biome);
        boolean up = biome != 2 && biome != 3;   // 书页下落、雪花下沉
        for (int i = 0; i < COUNT; i++) {
            // 黄金分割分布，避免成列
            float gx = (i * .61803399f) % 1f;
            float gy = (i * .38196601f) % 1f;
            float speed = 13f + (i % 7) * 6.5f;
            float travel = (time * speed + i * 47f) % (up ? H : H);
            float y = up ? (H - travel) : travel;
            float x = WALK_L + 8 + gx * (WALK_R - WALK_L - 16);
            x += (float) Math.sin(time * .9f + i * 1.7f) * 9f;
            // 边界内淡出，避免粒子在墙里闪烁
            if (x < WALK_L + 4 || x > WALK_R - 4) continue;
            if (y < WALK_T + 4 || y > WALK_B - 4) continue;
            float fade = 1f - Math.abs(y - H * .5f) / (H * .62f);
            int al = (int) (26 + 46 * Math.max(0f, fade));
            float size = biome == 2 ? 1.6f : biome == 3 ? 1.5f : 1.9f;
            switch (biome) {
                case 0:   // 苔光：绿色孢子
                    circle(c, x, y, size, col(0xff9de7c1, al));
                    if ((i & 3) == 0) circle(c, x, y, size * 2.6f, col(0xff9de7c1, al / 3));
                    break;
                case 1:   // 赤铜：橙色火星
                    circle(c, x, y, size * .9f, col(0xffeac487, al));
                    line(c, x, y, x + 1.4f, y + 4f, col(0xffffa37b, al / 2), 1f);
                    break;
                case 2:   // 月影：飘落书页
                    rect(c, x, y, 3.4f, 4.6f, .8f, col(0xffdfe4ff, al));
                    break;
                case 3:   // 霜蚀：雪花
                    circle(c, x, y, size, col(0xffcfe9ff, al));
                    line(c, x - size * 2, y, x + size * 2, y, col(0xffcfe9ff, al / 2), .8f);
                    break;
                case 4:   // 熔火：红色余烬
                    circle(c, x, y, size * .85f, col(0xffffa37b, al));
                    if ((i & 3) == 1) circle(c, x, y, size * 2.2f, col(0xffff7a3c, al / 3));
                    break;
                default:  // 虚空：裂隙闪烁
                    float flick = .5f + .5f * (float) Math.sin(time * 4.2f + i * 2.1f);
                    diamond(c, x, y, size * (1f + flick), col(0xffb79bff, (int) (al * flick)));
                    break;
            }
        }
        // 生态色极淡的整体氛围染色
        R.set(0, 0, W, H);
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(col(accent, 7));
        c.drawRect(R, P);
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 大气覆盖层
    // ══════════════════════════════════════════════════════════════

    /** 生态色暗角 + 极淡颗粒 + 生态特有大气。不干扰战斗可读性。 */
    static void overlay(Canvas c, Art a, Game g, float time) {
        if (c == null || a == null || g == null) return;
        int biome = clampBiome(g.biome);
        int accent = accent(biome);

        // 生态色径向暗角（边缘变暗，强度约 0.3）
        ensureVignette();
        P.setShader(vignette);
        P.setStyle(Paint.Style.FILL);
        P.setAlpha(78);
        R.set(0, 0, W, H);
        c.drawRect(R, P);
        P.setAlpha(255);
        P.setShader(null);

        // 极淡颗粒（一次 tile 绘制，alpha ≤ 10）
        ensureGrain();
        if (grainPaint != null) {
            grainPaint.setAlpha(9);
            c.drawRect(R, grainPaint);
            grainPaint.setAlpha(255);
        }

        // 生态特有大气
        switch (biome) {
            case 0:   // 苔光：低位薄雾
                haze(c, 0xff9de7c1, time, 2);
                break;
            case 1:   // 赤铜：热浪横条
                for (int i = 0; i < 3; i++) {
                    float y = 150 + i * 168f + (float) Math.sin(time * 1.3f + i) * 12f;
                    float w = 640 + (float) Math.sin(time * .7f + i * 2f) * 90f;
                    rect(c, 560 - w * .5f, y, w, 5f, 2f, col(0xffeac487, 7));
                    rect(c, 560 - w * .42f, y + 9f, w * .84f, 2.4f, 1f, col(0xffffa37b, 6));
                }
                break;
            case 2:   // 月影：星光闪烁
                for (int i = 0; i < 14; i++) {
                    float x = 90 + (i * 233) % 950;
                    float y = 70 + (i * 157) % 470;
                    float tw = .5f + .5f * (float) Math.sin(time * 2.2f + i * 1.9f);
                    if (tw < .35f) continue;
                    circle(c, x, y, 1.2f + tw * 1.1f, col(0xffe4e8ff, (int) (60 * tw)));
                }
                break;
            case 3:   // 霜蚀：冷雾下沉
                haze(c, 0xffa9dbff, time, 3);
                break;
            case 4:   // 熔火：底部热光
                rect(c, 0, WALK_B - 90f, W, 90f, 0, col(0xffff6a2a, 9));
                rect(c, 0, WALK_B - 46f, W, 46f, 0, col(0xffff6a2a, 8));
                break;
            default:  // 虚空：紫雾脉动
                float p = .5f + .5f * (float) Math.sin(time * .8f);
                rect(c, 0, 0, W, H, 0, col(0xff7a64b0, (int) (8 + 6 * p)));
                haze(c, 0xffb79bff, time, 1);
                break;
        }
    }

    private static void haze(Canvas c, int color, float time, int bands) {
        for (int i = 0; i < bands; i++) {
            float y = 170 + i * 176f + (float) Math.sin(time * .55f + i * 1.6f) * 16f;
            float w = 760 + (float) Math.sin(time * .42f + i) * 110f;
            rect(c, 560 - w * .5f, y, w, 22f, 11f, col(color, 6));
            rect(c, 560 - w * .38f, y + 26f, w * .76f, 12f, 6f, col(color, 5));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 边框与氛围
    // ══════════════════════════════════════════════════════════════

    /** 四角框架、上下边光；首领战时边缘红色脉动。 */
    static void frame(Canvas c, Art a, Game g) {
        if (c == null || a == null || g == null) return;
        int biome = clampBiome(g.biome);
        int accent = accent(biome);

        // ── 上下 1px 渐变边光（三层叠加模拟渐变，零分配）──
        for (int i = 0; i < 3; i++) {
            int al = 66 - i * 20;
            rect(c, 0, i * 1.2f, W, 1.3f, 0, col(accent, al));
            rect(c, 0, H - 1.2f - i * 1.2f, W, 1.3f, 0, col(accent, al - 6));
        }

        // ── 四角内嵌框架 ──
        float m = 7f, len = 52f;
        int lineCol = col(accent, 118);
        int lineCol2 = col(accent, 62);
        // 左上
        line(c, m, m + len, m, m, lineCol, 2f);
        line(c, m, m, m + len, m, lineCol, 2f);
        // 右上
        line(c, W - m - len, m, W - m, m, lineCol, 2f);
        line(c, W - m, m, W - m, m + len, lineCol, 2f);
        // 左下
        line(c, m, H - m - len, m, H - m, lineCol, 2f);
        line(c, m, H - m, m + len, H - m, lineCol, 2f);
        // 右下
        line(c, W - m - len, H - m, W - m, H - m, lineCol, 2f);
        line(c, W - m, H - m - len, W - m, H - m, lineCol, 2f);

        // 角标小菱形
        diamond(c, m, m, 4.4f, lineCol);
        diamond(c, W - m, m, 4.4f, lineCol);
        diamond(c, m, H - m, 4.4f, lineCol);
        diamond(c, W - m, H - m, 4.4f, lineCol);

        // 四边细框（很淡，界定战场）
        stroke(c, m, m, W - m * 2f, H - m * 2f, 6f, lineCol2, 1f);

        // 顶部中央刻线（装饰）
        line(c, 560 - 90, m, 560 - 26, m, lineCol2, 2f);
        line(c, 560 + 26, m, 560 + 90, m, lineCol2, 2f);
        diamond(c, 560, m, 5f, col(accent, 150));

        // ── 首领战：边缘红色脉动 ──
        if (g.roomType == Game.ROOM_BOSS) {
            float t = g.time;
            float pulse = .5f + .5f * (float) Math.sin(t * 3.1f);
            int al = (int) (34 + 46 * pulse);
            for (int i = 0; i < 3; i++) {
                int a2 = Math.max(0, al - i * 12);
                rect(c, 0, i * 2f, W, 2f, 0, col(0xfff18f8b, a2));
                rect(c, 0, H - 2f - i * 2f, W, 2f, 0, col(0xfff18f8b, a2));
                rect(c, i * 2f, 0, 2f, H, 0, col(0xfff18f8b, a2));
                rect(c, W - 2f - i * 2f, 0, 2f, H, 0, col(0xfff18f8b, a2));
            }
            for (int i = 0; i < 4; i++) {
                float cx = i == 0 || i == 2 ? 22f : W - 22f;
                float cy = i < 2 ? 22f : H - 22f;
                ring(c, cx, cy, 9f + pulse * 5f, col(0xfff18f8b, (int) (60 * pulse)), 1.8f);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  共享缓存（一次性构建，绝不每帧分配）
    // ══════════════════════════════════════════════════════════════

    private static void ensureVignette() {
        if (vignette != null) return;
        try {
            vignette = new RadialGradient(560f, 310f, 690f,
                    new int[]{0x00000000, 0x33000000, 0xff000000},
                    new float[]{.48f, .80f, 1f}, Shader.TileMode.CLAMP);
        } catch (Throwable t) {
            vignette = null;
        }
    }

    /** 4×4 颗粒 tile，用 BitmapShader 一次铺满，替代逐行扫描线。 */
    private static void ensureGrain() {
        if (grainPaint != null) return;
        try {
            Bitmap tile = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            Canvas tc = new Canvas(tile);
            tc.drawColor(0x00000000);
            Paint gp = new Paint();
            gp.setStyle(Paint.Style.FILL);
            gp.setColor(0xffffffff);
            tc.drawRect(0, 0, 1, 1, gp);
            tc.drawRect(2, 3, 3, 4, gp);
            tile.setDensity(Bitmap.DENSITY_NONE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            paint.setStyle(Paint.Style.FILL);
            grainPaint = paint;
        } catch (Throwable t) {
            grainPaint = null;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制原语（全部静态复用，无分配）
    // ══════════════════════════════════════════════════════════════

    private static void rect(Canvas c, float x, float y, float w, float h, int color) {
        rect(c, x, y, w, h, 0f, color);
    }

    private static void rect(Canvas c, float x, float y, float w, float h, float rad, int color) {
        if (w <= 0 || h <= 0) return;
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        R.set(x, y, x + w, y + h);
        c.drawRoundRect(R, rad, rad, P);
    }

    private static void stroke(Canvas c, float x, float y, float w, float h, float rad,
                               int color, float width) {
        if (w <= 0 || h <= 0) return;
        SP.setStyle(Paint.Style.STROKE);
        SP.setShader(null);
        SP.setStrokeWidth(width);
        SP.setColor(color);
        R.set(x, y, x + w, y + h);
        c.drawRoundRect(R, rad, rad, SP);
    }

    private static void line(Canvas c, float x1, float y1, float x2, float y2,
                             int color, float width) {
        SP.setStyle(Paint.Style.STROKE);
        SP.setShader(null);
        SP.setStrokeWidth(width);
        SP.setStrokeCap(Paint.Cap.ROUND);
        SP.setColor(color);
        c.drawLine(x1, y1, x2, y2, SP);
    }

    private static void circle(Canvas c, float x, float y, float rad, int color) {
        if (rad <= 0) return;
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        c.drawCircle(x, y, rad, P);
    }

    private static void ring(Canvas c, float x, float y, float rad, int color, float width) {
        if (rad <= 0) return;
        SP.setStyle(Paint.Style.STROKE);
        SP.setShader(null);
        SP.setStrokeWidth(width);
        SP.setColor(color);
        c.drawCircle(x, y, rad, SP);
    }

    private static void oval(Canvas c, float cx, float cy, float rx, float ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        R2.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(R2, P);
    }

    private static void diamond(Canvas c, float x, float y, float rad, int color) {
        if (rad <= 0) return;
        PATH.reset();
        PATH.moveTo(x, y - rad);
        PATH.lineTo(x + rad * .7f, y);
        PATH.lineTo(x, y + rad);
        PATH.lineTo(x - rad * .7f, y);
        PATH.close();
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        c.drawPath(PATH, P);
    }

    private static void tri(Canvas c, float x, float y, float size, int color, float rotation) {
        if (size <= 0) return;
        PATH.reset();
        PATH.moveTo(size, 0);
        PATH.lineTo(-size * .7f, size * .82f);
        PATH.lineTo(-size * .7f, -size * .82f);
        PATH.close();
        c.save();
        c.translate(x, y);
        c.rotate((float) (rotation * 180f / Math.PI));
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        c.drawPath(PATH, P);
        c.restore();
    }

    /** 多层同心圆近似辉光；bands 越多越亮。 */
    private static void glow(Canvas c, float x, float y, float rad, int color, float strength) {
        if (rad <= 0) return;
        int baseA = (int) (48 * clamp01(strength));
        for (int i = 3; i >= 1; i--) {
            circle(c, x, y, rad * i / 3f, col(color, baseA / (4 - i)));
        }
    }

    /** 折线裂纹。 */
    private static void crack(Canvas c, float x, float y, float len, int color) {
        PATH.reset();
        PATH.moveTo(x, y);
        PATH.lineTo(x + len * .34f, y - len * .22f);
        PATH.lineTo(x + len * .62f, y + len * .10f);
        PATH.lineTo(x + len, y - len * .06f);
        SP.setStyle(Paint.Style.STROKE);
        SP.setShader(null);
        SP.setStrokeWidth(1.1f);
        SP.setColor(color);
        c.drawPath(PATH, SP);
    }

    /** 发光岩浆纹。 */
    private static void lavaVein(Canvas c, float x, float y, float s, java.util.Random rnd) {
        float len = 18 + rnd.nextFloat() * 22;
        PATH.reset();
        PATH.moveTo(x, y);
        PATH.lineTo(x + len * .38f, y + s * 5f);
        PATH.lineTo(x + len * .70f, y - s * 3f);
        PATH.lineTo(x + len, y + s * 2f);
        SP.setStyle(Paint.Style.STROKE);
        SP.setShader(null);
        SP.setStrokeWidth(4.2f);
        SP.setColor(col(0xff2a120c, 170));
        c.drawPath(PATH, SP);
        SP.setStrokeWidth(1.8f);
        SP.setColor(col(0xffff9a5a, 150));
        c.drawPath(PATH, SP);
    }

    /** 书页碎片。 */
    private static void bookPage(Canvas c, float x, float y, float s, float rot) {
        c.save();
        c.translate(x, y);
        c.rotate((float) (rot * 57.2958f));
        rect(c, -5f * s, -7f * s, 10f * s, 14f * s, 1.2f, col(0xffc9cbe0, 58));
        line(c, -3.4f * s, -3f * s, 3.4f * s, -3f * s, col(0xff8f93b8, 46), .8f);
        line(c, -3.4f * s, 0f, 3.4f * s, 0f, col(0xff8f93b8, 40), .8f);
        c.restore();
    }

    /** 冰晶碎片。 */
    private static void iceShard(Canvas c, float x, float y, float s, int color) {
        PATH.reset();
        PATH.moveTo(x, y - s);
        PATH.lineTo(x + s * .52f, y);
        PATH.lineTo(x, y + s * .78f);
        PATH.lineTo(x - s * .52f, y);
        PATH.close();
        P.setStyle(Paint.Style.FILL);
        P.setShader(null);
        P.setColor(color);
        c.drawPath(PATH, P);
        line(c, x, y - s * .70f, x, y + s * .55f, col(0xffffffff, 60), .8f);
    }

    /** 符文刻痕。 */
    private static void runeMark(Canvas c, float x, float y, float s, int color) {
        diamond(c, x, y, s, color);
        line(c, x, y - s * .70f, x, y + s * .70f, color, 1.2f);
        line(c, x - s * .50f, y, x + s * .50f, y, color, 1.2f);
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    private static int accent(int biome) {
        return Content.BIOME_ACCENT[clampBiome(biome)];
    }

    private static int clampBiome(int biome) {
        return biome < 0 ? 0 : biome >= BIOMES ? BIOMES - 1 : biome;
    }

    /** 给 RGB 套上 alpha。 */
    private static int col(int rgb, int alpha) {
        int a = alpha < 0 ? 0 : alpha > 255 ? 255 : alpha;
        return (a << 24) | (rgb & 0xffffff);
    }

    private static int shade(int color, float mul) {
        int r = Math.min(255, Math.max(0, (int) (((color >> 16) & 0xff) * mul)));
        int g = Math.min(255, Math.max(0, (int) (((color >> 8) & 0xff) * mul)));
        int b = Math.min(255, Math.max(0, (int) ((color & 0xff) * mul)));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float f) {
        float k = f < 0 ? 0 : f > 1 ? 1 : f;
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xff000000
                | ((int) (ar + (br - ar) * k) << 16)
                | ((int) (ag + (bg - ag) * k) << 8)
                | (int) (ab + (bb - ab) * k);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
