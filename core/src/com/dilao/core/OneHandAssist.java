package com.dilao.core;

/**
 * Optional single-thumb assistance. This class only makes deterministic
 * recommendations from the already-synchronized Game snapshot; it never
 * modifies the simulation, RNG, save format or networking protocol.
 *
 * The player still controls movement and dodge. Auto-fire is the existing
 * default; this helper optionally handles active skill / ultimate timing.
 */
public final class OneHandAssist {
    private OneHandAssist() {}

    public static final int SKILL = 1;
    public static final int ULTIMATE = 2;

    public static int actions(Game g, Game.Player p) {
        if (g == null || p == null || g.phase != Game.FIGHT
                || !p.connected || p.down || p.hp <= 0f) return 0;

        boolean targetNearby = false, bossNearby = false;
        int crowd = 0;
        for (Game.Enemy e : g.enemies) {
            if (e == null || e.hp <= 0f) continue;
            float x = e.x - p.x, y = e.y - p.y;
            float d2 = x * x + y * y;
            if (d2 <= 240f * 240f) targetNearby = true;
            if (d2 <= 340f * 340f) crowd++;
            if (e.bossIndex >= 0 && d2 <= 520f * 520f) bossNearby = true;
        }

        // Dedicated support classes can heal a wounded ally without waiting
        // for an enemy to enter their immediate skill radius.
        boolean healingNeeded = false;
        if (p.role == 2 || p.role == 7) {
            for (Game.Player ally : g.players) {
                if (ally.connected && !ally.down && ally.hp > 0f
                        && ally.hp < ally.maxHp * .70f) {
                    healingNeeded = true;
                    break;
                }
            }
        }

        int result = 0;
        if (p.skillCd <= 0f && (targetNearby || bossNearby || healingNeeded))
            result |= SKILL;
        if (p.ultReady >= 1f && (bossNearby || crowd >= 6
                || (healingNeeded && p.hp < p.maxHp * .45f)))
            result |= ULTIMATE;
        return result;
    }
}
