package com.dilao.abysssquad;

import com.dilao.core.Game;

/**
 * 战斗表现驱动器：把 {@link Game} 的状态差分翻译成 {@link Fx} 的表现调用。
 *
 * <p>为什么需要这个类：{@code core} 是纯模拟、只暴露状态；{@code GameView} 只负责编排。
 * 之前没有任何地方调用 Fx 的 {@code impact/killBurst/number/burn…}，所以整套粒子系统
 * 在运行期其实是死代码。本类补上这一层「状态 → 表现」的翻译。</p>
 *
 * <p>接入方式（只需要一行，放在 GameView 战斗绘制里 {@code fx.update()} 之后）：</p>
 * <pre>
 *   combat.tick(g, fx, simDt, session.localId);
 * </pre>
 *
 * <p>零 GC：查询表在构造时一次分配；每帧只读写基本类型数组，不产生临时对象。</p>
 */
final class CombatFeedback {

    private static final int TABLE = 1024;      // 必须是 2 的幂
    private static final int PROBE = 24;

    // 敌人差分表（开放寻址，identityHashCode 定位，引用确认身份）
    private final Object[] keys = new Object[TABLE];
    private final float[] prevHp = new float[TABLE];
    private final float[] cooldown = new float[TABLE];
    /** 每个敌人的「命中闪白」剩余寿命（秒）：比 Game.Enemy.hit 更长更稳，驱动本体闪白。 */
    private final float[] flash = new float[TABLE];
    /** 闪白强度上限（秒）：0.10s 的衰减窗口约等于 6 帧，够看清又不会糊。 */
    private static final float FLASH_TIME = 0.10f;

    // 全局量基线
    private int lastKills = -1, lastLevel = -1, lastCoins = -1, lastBosses = -1;
    private float lastPlayerHp = -1f, lastUlt = -1f;
    private boolean primed;

    // 玩家位置（给击杀 / 受击方向用）
    private float px, py;
    private boolean havePlayer;

    // 复用随机（不用 Math.random，避免每次调用产生 double 装箱 / 全局锁）
    private long rng = 0x9E3779B97F4A7C15L;

    private float rnd() {
        long s = rng;
        s ^= s << 13; s ^= s >>> 7; s ^= s << 17; rng = s;
        return (float) ((s >>> 40) & 0xFFFFFF) / 16777216f;
    }

    private float rnd2() { return rnd() * 2f - 1f; }

    /**
     * 每帧调用一次（仅在战斗中）。第一帧只做基线采样。
     *
     * @param g  当前模拟状态
     * @param fx 表现层
     * @param dt 本帧模拟步长
     * @param me 本地玩家 id
     */
    void tick(Game g, Fx fx, float dt, int localId) {
        if (g == null || fx == null) return;
        if (Float.isNaN(dt) || dt <= 0f) dt = 1f / 60f;
        if (dt > 0.1f) dt = 0.1f;

        Game.Player me = g.player(localId);
        if (me != null) { px = me.x; py = me.y; havePlayer = true; }

        if (!primed) { prime(g, me); return; }

        // ── 敌人受伤 ──
        for (int i = 0; i < g.enemies.size(); i++) {
            Game.Enemy e = g.enemies.get(i);
            int slot = slot(e);
            if (keys[slot] != e) {
                keys[slot] = e;
                prevHp[slot] = e.hp;
                cooldown[slot] = 0f;
                flash[slot] = 0f;
                continue;
            }
            if (cooldown[slot] > 0f) cooldown[slot] -= dt;
            if (flash[slot] > 0f) { flash[slot] -= dt; if (flash[slot] < 0f) flash[slot] = 0f; }
            float dmg = prevHp[slot] - e.hp;
            prevHp[slot] = e.hp;
            if (dmg <= 0.01f) continue;

            // 闪白和粒子节流分开：节流只省粒子，闪白必须「每次命中都看得见」（清单 #1）
            flash[slot] = FLASH_TIME;

            float ratio = dmg / Math.max(1f, e.maxHp);
            boolean heavy = ratio > 0.16f || dmg > 60f || e.hp <= 0f;
            // 同一敌人 70ms 内只播一次轻击，防止弹幕打空粒子池
            if (cooldown[slot] > 0f && !heavy) continue;
            cooldown[slot] = heavy ? 0.05f : 0.09f;

            float hx = e.x + rnd2() * e.r * 0.3f;
            float hy = e.y - e.r * 0.35f;
            float dir = havePlayer ? (float) Math.atan2(e.y - py, e.x - px) : Fx.DIR_NONE;
            float power = Math.min(1.6f, 0.4f + ratio * 3.4f + (dmg > 40f ? 0.3f : 0f));
            fx.impact(hx, hy, 0xfffff0d8, heavy, dir, power, -1);
            fx.number(hx, hy - e.r * 0.5f, dmg, heavy,
                    heavy ? 0xfffff0b0 : 0xfffff5e6);
            if (e.bossIndex >= 0 || e.elite) fx.ring(e.x, e.y, e.r + 6f, 0xffcfd8e0, 0.18f);
        }

        // ── 清掉死敌的表项，避免引用滞留 ──
        for (int s = 0; s < TABLE; s++) {
            Object o = keys[s];
            if (o != null && ((Game.Enemy) o).hp <= 0f) keys[s] = null;
        }

        // ── 击杀：以 g.kills 增量为权威（敌人可能已从列表移除） ──
        if (g.kills > lastKills) {
            int delta = g.kills - lastKills;
            if (delta > 4) delta = 4;       // 同帧大规模清场时限流
            Game.Enemy corpse = nearestDead(g, px, py);
            for (int k = 0; k < delta; k++) {
                float kx = corpse != null ? corpse.x + rnd2() * 8f : px + rnd2() * 120f;
                float ky = corpse != null ? corpse.y + rnd2() * 6f : py + rnd2() * 90f;
                float dir = havePlayer ? (float) Math.atan2(ky - py, kx - px) : Fx.DIR_NONE;
                boolean elite = corpse != null && (corpse.elite || corpse.bossIndex >= 0);
                float scale = corpse == null ? 1f
                        : Math.max(0.7f, Math.min(2.2f, corpse.r / 16f));
                fx.killBurst(kx, ky, elite ? 0xffffd08a : 0xff9fe6c0, scale, elite, dir, -1);
            }
        }

        // ── 首领死亡：多段演出 ──
        if (g.bossesDefeated > lastBosses) {
            Game.Enemy boss = nearestBoss(g);
            float bx = boss != null ? boss.x : px;
            float by = boss != null ? boss.y : py;
            fx.bossDeath(bx, by, 0xffee9cd8);
        }

        // ── 玩家受伤 ──
        if (me != null) {
            if (lastPlayerHp >= 0f && me.hp < lastPlayerHp - 0.5f) {
                float d = lastPlayerHp - me.hp;
                float power = Math.min(1.4f, 0.45f + d / Math.max(1f, me.maxHp) * 3f);
                // 受击方向取反：从敌人方向推向玩家
                fx.hurt(havePlayer ? rnd2() : 0f, havePlayer ? rnd2() : 0f, power);
                fx.number(me.x, me.y - 26f, d, false, 0xffff8a8a);
            }
            lastPlayerHp = me.hp;

            // ── 升级：起势 + 收束两段（levelUp 内部会调度第二段） ──
            if (g.level > lastLevel) {
                fx.levelUp(me.x, me.y);
            }

            // ── 终极就绪：只给一圈提示，不要误放「释放」演出 ──
            if (lastUlt >= 0f && me.ultReady >= 1f && lastUlt < 1f) {
                fx.ring(me.x, me.y, 46f, 0xffeac487, 0.4f);
                fx.ring(me.x, me.y, 30f, 0xfffff3cf, 0.55f);
            }
            lastUlt = me.ultReady;
        }

        // ── 星尘 ──
        if (g.coins > lastCoins && me != null && rnd() < 0.45f) {
            int gain = g.coins - lastCoins;
            if (gain > 0) fx.coin(me.x, me.y - 40f, gain);
        }

        lastKills = g.kills;
        lastLevel = g.level;
        lastCoins = g.coins;
        lastBosses = g.bossesDefeated;
    }

    /** 第一帧只采样基线，不产生表现。 */
    private void prime(Game g, Game.Player me) {
        for (int s = 0; s < TABLE; s++) { keys[s] = null; prevHp[s] = 0f; cooldown[s] = 0f; flash[s] = 0f; }
        for (int i = 0; i < g.enemies.size(); i++) {
            Game.Enemy e = g.enemies.get(i);
            int slot = slot(e);
            keys[slot] = e;
            prevHp[slot] = e.hp;
            cooldown[slot] = 0f;
        }
        lastKills = g.kills;
        lastLevel = g.level;
        lastCoins = g.coins;
        lastBosses = g.bossesDefeated;
        lastPlayerHp = me == null ? -1f : me.hp;
        lastUlt = me == null ? -1f : me.ultReady;
        primed = true;
    }

    /** 重开 / 换房时调用：丢弃差分状态。 */
    void reset() {
        primed = false;
        lastKills = lastLevel = lastCoins = -1;
        lastBosses = -1;
        lastPlayerHp = -1f;
        lastUlt = -1f;
        havePlayer = false;
        for (int s = 0; s < TABLE; s++) { keys[s] = null; cooldown[s] = 0f; flash[s] = 0f; }
    }

    /**
     * 查询某个敌人的本体闪白强度（0..1），供 GameView.drawEnemy 使用。
     *
     * <p>没有条目 / 已衰减完时返回 0 —— 调用方拿 0 直接跳过绘制，零成本。
     * 与粒子节流解耦：每次掉血都会刷新这个值，所以「每一刀都看得见」。</p>
     *
     * @param e 敌人（可为 null）
     * @return 0..1 的白闪强度
     */
    float flashOf(Game.Enemy e) {
        if (e == null) return 0f;
        int slot = slot(e);
        if (keys[slot] != e) return 0f;
        float t = flash[slot];
        if (t <= 0f) return 0f;
        float k = t / FLASH_TIME;
        if (k > 1f) k = 1f;
        return k;
    }

    /** identityHashCode 开放寻址，固定探测次数保证零分配。 */
    private int slot(Game.Enemy e) {
        int h = System.identityHashCode(e) * 0x9E3779B1;
        int i = (h ^ (h >>> 16)) & (TABLE - 1);
        for (int n = 0; n < PROBE; n++) {
            Object o = keys[i];
            if (o == null || o == e) return i;
            i = (i + 1) & (TABLE - 1);
        }
        return i;
    }

    private Game.Enemy nearestBoss(Game g) {
        Game.Enemy best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < g.enemies.size(); i++) {
            Game.Enemy e = g.enemies.get(i);
            if (e.bossIndex < 0) continue;
            float d = dist2(e.x, e.y, px, py);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private Game.Enemy nearestDead(Game g, float x, float y) {
        Game.Enemy best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < g.enemies.size(); i++) {
            Game.Enemy e = g.enemies.get(i);
            if (e.hp > 0f) continue;
            float d = dist2(e.x, e.y, x, y);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static float dist2(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }
}
