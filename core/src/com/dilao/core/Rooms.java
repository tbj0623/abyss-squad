package com.dilao.core;

import java.util.*;

/**
 * 房间布局语法与导航网格。
 * <p>每张布局由人手设计，随机旋转/镜像保持可通行的逃生通道；
 * 6 个生态各有专属的障碍物密度与危险区分布。
 */
public final class Rooms {
    private Rooms() {}

    public static final String[] LAYOUTS = {
            "双生石庭", "四柱回廊", "断桥天井", "交错迷庭", "环形书架",
            "炉心工坊", "星辉十字", "空旷祭室", "双塔对峙", "回旋阶梯",
            "雾中庭", "碎镜厅"
    };

    /** 布局 id → 障碍矩形 [x,y,w,h] 列表（基准坐标，镜像/旋转由 build 处理）。 */
    private static final float[][][] SHAPES = {
            // 0 双生石庭
            {{275, 220, 78, 66}, {770, 330, 78, 66}},
            // 1 四柱回廊
            {{296, 176, 76, 56}, {748, 176, 76, 56}, {296, 388, 76, 56}, {748, 388, 76, 56}},
            // 2 断桥天井
            {{246, 186, 216, 38}, {658, 396, 216, 38}},
            // 3 交错迷庭
            {{262, 306, 66, 142}, {792, 148, 66, 142}, {498, 178, 112, 38}},
            // 4 环形书架
            {{256, 188, 174, 32}, {690, 188, 174, 32}, {256, 408, 174, 32}, {690, 408, 174, 32}},
            // 5 炉心工坊
            {{280, 168, 84, 84}, {756, 368, 84, 84}, {508, 276, 104, 36}},
            // 6 星辉十字
            {{296, 196, 56, 124}, {768, 300, 56, 124}, {508, 142, 104, 36}},
            // 7 空旷祭室（无固定障碍，用于首领房与决战）
            {},
            // 8 双塔对峙
            {{248, 144, 62, 200}, {810, 276, 62, 200}, {498, 288, 124, 44}},
            // 9 回旋阶梯
            {{262, 168, 60, 60}, {330, 236, 60, 60}, {758, 396, 60, 60}, {690, 328, 60, 60}},
            // 10 雾中庭
            {{396, 236, 60, 148}, {664, 236, 60, 148}, {512, 148, 96, 34}},
            // 11 碎镜厅
            {{248, 256, 120, 32}, {752, 256, 120, 32}, {416, 172, 60, 60}, {644, 388, 60, 60}},
    };

    public static void build(Game g) {
        g.blocks.clear();
        g.props.clear();
        g.hazards.clear();
        if (g.roomType == Game.ROOM_BOSS) g.layout = 7;
        if (g.layout < 0 || g.layout >= SHAPES.length) g.layout = 7;
        float[][] shape = SHAPES[g.layout];
        for (float[] r : shape) block(g, r[0], r[1], r[2], r[3]);
        // 挑战房"星环"目标需要中央空地
        if (g.roomType == Game.ROOM_CHALLENGE && g.objective == 1)
            g.blocks.removeIf(b -> b.x < 690 && b.x + b.w > 430 && b.y < 430 && b.y + b.h > 170);
        if (g.roomType != Game.ROOM_BOSS) {
            // 可破坏木箱：4 个，提供清房间时的小额星尘
            for (int j = 0; j < 4; j++) {
                Game.Prop p = new Game.Prop();
                p.id = g.allocateId();
                p.type = 0;
                p.x = 124 + j * 288;
                p.y = j % 2 == 0 ? 108 : 512;
                p.hp = 26 + g.floor * 6;
                g.props.add(p);
            }
            // 补给宝箱
            Game.Prop chest = new Game.Prop();
            chest.id = g.allocateId();
            chest.type = 1;
            chest.x = g.random.nextBoolean() ? 152 : 968;
            chest.y = 300;
            chest.hp = 40;
            g.props.add(chest);
            // 生态装饰道具
            for (int j = 0; j < 4; j++) {
                Game.Prop deco = new Game.Prop();
                deco.id = g.allocateId();
                deco.type = 2 + (j % 6);
                deco.x = 90 + g.random.nextFloat() * 940;
                deco.y = 80 + g.random.nextFloat() * 460;
                if (!blocked(g.blocks, deco.x, deco.y, 30)) g.props.add(deco);
            }
        }
        // 环境危险区：随生态与楼层增加
        int hazardCount = 0;
        if (g.roomType != Game.ROOM_BOSS) {
            hazardCount = 1 + Math.min(3, g.floor / 3);
            if (g.mutator == 2) hazardCount += 2;
        }
        for (int j = 0; j < hazardCount; j++) {
            Game.Hazard h = new Game.Hazard();
            h.id = g.allocateId();
            float[] pos = hazardSpot(g, j, hazardCount);
            h.x = pos[0];
            h.y = pos[1];
            h.r = g.biome == 2 ? 70 : g.biome == 5 ? 62 : g.biome == 3 ? 66 : 52;
            h.kind = g.mutator == 2 ? 3 : hazardKind(g.biome);
            h.clock = 2.4f + j * 1.3f;
            h.active = -1;
            g.hazards.add(h);
        }
        g.navigation.rebuild(g.blocks);
    }

    private static int hazardKind(int biome) {
        switch (biome) {
            case 0: return 2;   // 苔光：毒
            case 1: return 0;   // 赤铜：火
            case 2: return 4;   // 月影：虚空
            case 3: return 1;   // 霜蚀：冰
            case 4: return 3;   // 熔火：熔岩
            default: return 4;  // 虚空
        }
    }

    /** 危险区尽量放在障碍物附近，逼玩家改变走位但不封死通道。 */
    private static float[] hazardSpot(Game g, int index, int total) {
        float[][] pool = {
                {340, 300}, {780, 320}, {560, 462}, {560, 148},
                {212, 430}, {906, 190}, {420, 452}, {716, 176}
        };
        float[] base = pool[g.random.nextInt(pool.length)];
        float x = Game.clamp(base[0] + (g.random.nextFloat() - .5f) * 90, 90, 1030);
        float y = Game.clamp(base[1] + (g.random.nextFloat() - .5f) * 70, 80, 540);
        // 避免正好压在玩家出生点
        if (Game.distance(x, y, 560, 470) < 150) y = Game.clamp(y - 170, 80, 540);
        return new float[]{x, y};
    }

    private static boolean blocked(List<Game.Block> blocks, float x, float y, float r) {
        for (Game.Block b : blocks)
            if (x + r > b.x && x - r < b.x + b.w && y + r > b.y && y - r < b.y + b.h) return true;
        return false;
    }

    private static void block(Game g, float x, float y, float w, float h) {
        if (w <= 0 || h <= 0) return;
        if (g.mirror) { x = Game.WIDTH - x - w; y = Game.HEIGHT - y - h; }
        Game.Block b = new Game.Block(x, y, w, h);
        b.kind = g.biome == 2 ? 1 : g.biome == 3 ? 3 : g.biome == 4 ? 2 : 0;
        g.blocks.add(b);
    }

    /**
     * 绕障网格导航。每帧只对"当前目标格"跑一次 BFS，缓存结果供所有敌人共用。
     */
    public static final class Navigation {
        static final int COLS = 28, ROWS = 16, CELL = 40;
        private final boolean[] solid = new boolean[COLS * ROWS];
        private final int[] distance = new int[COLS * ROWS];
        private final int[] queue = new int[COLS * ROWS];
        private int goal = -1;

        public void rebuild(List<Game.Block> blocks) {
            Arrays.fill(solid, false);
            goal = -1;
            for (int y = 0; y < ROWS; y++)
                for (int x = 0; x < COLS; x++) {
                    float px = x * CELL + 20, py = y * CELL + 20;
                    boolean closed = px < 40 || px > 1080 || py < 40 || py > 580;
                    for (Game.Block b : blocks)
                        if (px + 28 > b.x && px - 28 < b.x + b.w
                                && py + 28 > b.y && py - 28 < b.y + b.h) closed = true;
                    solid[y * COLS + x] = closed;
                }
        }

        public void target(float x, float y) {
            int cell = cell(x, y);
            if (cell == goal) return;
            goal = cell;
            Arrays.fill(distance, 10000);
            int head = 0, tail = 0;
            queue[tail++] = cell;
            distance[cell] = 0;
            while (head < tail) {
                int at = queue[head++];
                int cx = at % COLS, cy = at / COLS;
                for (int d = 0; d < 4; d++) {
                    int to = d == 0 ? at - 1 : d == 1 ? at + 1 : d == 2 ? at - COLS : at + COLS;
                    if (to < 0 || to >= distance.length) continue;
                    if (d == 0 && cx == 0) continue;
                    if (d == 1 && cx == COLS - 1) continue;
                    if (d == 2 && cy == 0) continue;
                    if (d == 3 && cy == ROWS - 1) continue;
                    if (solid[to] || distance[to] != 10000) continue;
                    distance[to] = distance[at] + 1;
                    queue[tail++] = to;
                }
            }
        }

        public int next(float x, float y) {
            int at = cell(x, y), best = at, value = distance[at];
            for (int yy = -1; yy <= 1; yy++)
                for (int xx = -1; xx <= 1; xx++) {
                    if (xx != 0 && yy != 0) continue;
                    int cx = at % COLS + xx, cy = at / COLS + yy;
                    if (cx < 0 || cx >= COLS || cy < 0 || cy >= ROWS) continue;
                    int to = cy * COLS + cx;
                    if (!solid[to] && distance[to] < value) { best = to; value = distance[to]; }
                }
            return best;
        }

        public float x(int n) { return n % COLS * CELL + 20; }
        public float y(int n) { return n / COLS * CELL + 20; }

        private int cell(float x, float y) {
            return Math.min(ROWS - 1, Math.max(0, (int) y / CELL)) * COLS
                    + Math.min(COLS - 1, Math.max(0, (int) x / CELL));
        }
    }
}
