import com.dilao.core.Game;

/**
 * captureGrowth 幂等性守卫 —— 防止 v2.0 的"面板自乘"回归。
 *
 * <p><b>背景</b>：P0-1 修复（元进度清零）引入 {@code captureGrowth}，把外部写入的
 * 裸面板加成反解进 {@code bonusPower/bonusHp/bonusArea/bonusCrit}。但首版实现写的是
 * {@code dmgRatio - 1f}，没有减去面板里已含的 {@code addPower}，于是每次
 * {@code recompute()} 都把上一轮的遗物加成重新吸收一遍：
 *
 * <pre>
 *   start        dmg=21.00  bonusPower=0.00
 *   +1 relic     dmg=22.89  bonusPower=0.00
 *   +2 relics    dmg=26.67  bonusPower=0.09   ← 第一件遗物的加成漏进成长
 *   +6 relics    dmg=49.35  bonusPower=0.90
 *   再 5 次 recompute（无新遗物）：58.80 → 68.25 → 77.70 → 87.15 → 96.60
 * </pre>
 *
 * <p>后果：一局 13 件遗物会让面板指数膨胀，五档难度全部被打到 100% 通关率。
 * {@code CoreTests} 抓不到，因为它测的是"读档一致性"而非"重复调用幂等性"。
 *
 * <p>本测试就是补上这个缺口。
 */
public final class CaptureGrowthIdempotencyTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] a) {
        noRelicIdempotent();
        oneRelicIdempotent();
        sixRelicsIdempotent();
        metaInjectionSurvives();
        allRolesIdempotent();
        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static Game fresh(long seed, int role) {
        Game g = new Game(seed);
        g.addPlayer(1, "p", role);
        g.contract = 1;
        g.startSolo(1, Game.MODE_EXPEDITION);
        return g;
    }

    /** 空手时反复 recompute，面板必须完全不变。 */
    static void noRelicIdempotent() {
        Game g = fresh(1001L, 0);
        Game.Player p = g.players.get(0);
        float d0 = p.damage, h0 = p.maxHp, s0 = p.speed, c0 = p.crit;
        for (int i = 0; i < 10; i++) g.recompute(p);
        boolean same = eq(d0, p.damage) && eq(h0, p.maxHp) && eq(s0, p.speed) && eq(c0, p.crit);
        check("空手：连续 10 次 recompute 面板不变", same,
                "dmg " + d0 + "→" + p.damage + ", hp " + h0 + "→" + p.maxHp
                        + ", spd " + s0 + "→" + p.speed + ", crit " + c0 + "→" + p.crit);
    }

    /** 拿 1 件遗物后反复 recompute，面板必须稳定在同一个值。 */
    static void oneRelicIdempotent() {
        Game g = fresh(1002L, 0);
        Game.Player p = g.players.get(0);
        g.grantRelic(p, 30);
        g.recompute(p);
        float d1 = p.damage, h1 = p.maxHp;
        float bp1 = p.bonusPower;
        for (int i = 0; i < 8; i++) g.recompute(p);
        boolean stable = eq(d1, p.damage) && eq(h1, p.maxHp);
        boolean growthStable = Math.abs(bp1 - p.bonusPower) < 1e-3f;
        check("1 件遗物：连续 8 次 recompute 面板与 bonusPower 不变", stable && growthStable,
                "dmg " + d1 + "→" + p.damage + ", bonusPower " + bp1 + "→" + p.bonusPower);
    }

    /** 拿 6 件遗物后反复 recompute —— 这是原 bug 最明显的地方。 */
    static void sixRelicsIdempotent() {
        Game g = fresh(1003L, 0);
        Game.Player p = g.players.get(0);
        int[] ids = {30, 31, 34, 35, 36, 37};
        for (int id : ids) g.grantRelic(p, id);
        g.recompute(p);
        float d1 = p.damage, h1 = p.maxHp;
        float bp1 = p.bonusPower;
        // 原 bug：每轮 ×1.086
        float[] trail = new float[6];
        for (int i = 0; i < 6; i++) { g.recompute(p); trail[i] = p.damage; }
        boolean stable = eq(d1, p.damage);
        check("6 件遗物：连续 6 次 recompute 伤害不变（原 bug 会 ×1.086/次）",
                stable, "dmg " + d1 + " → " + p.damage + " 轨迹=" + java.util.Arrays.toString(trail));
        check("6 件遗物：bonusPower 不因重复 recompute 增长",
                Math.abs(bp1 - p.bonusPower) < 1e-3f,
                "bonusPower " + bp1 + " → " + p.bonusPower);
    }

    /** 元进度注入（外部写裸字段 + applyMetaBoostedStats）必须被保留，且之后幂等。 */
    static void metaInjectionSurvives() {
        Game g = fresh(1004L, 0);
        Game.Player p = g.players.get(0);
        // 模拟 Session.applyMetaBoosts：直接写裸字段
        p.damage *= 1.25f;
        p.maxHp += 60f;
        p.speed *= 1.10f;
        float injectedDmg = p.damage;
        g.applyMetaBoostedStats();          // 捕获成长
        g.grantRelic(p, 30);                // 拿遗物触发普通 recompute
        g.recompute(p);
        // 元进度注入的部分必须仍在（+25% 伤害 / +60 生命 / +10% 移速）
        boolean kept = p.bonusPower > .15f && p.bonusHp > 50f && p.bonusArea > .05f;
        check("元进度注入被捕获且不丢失", kept,
                "bonusPower=" + p.bonusPower + ", bonusHp=" + p.bonusHp
                        + ", bonusArea=" + p.bonusArea + "（注入前 dmg=" + injectedDmg + "）");
        // 且之后幂等
        float d1 = p.damage;
        for (int i = 0; i < 6; i++) g.recompute(p);
        check("元进度捕获后再反复 recompute 保持幂等", eq(d1, p.damage),
                "dmg " + d1 + " → " + p.damage);
    }

    /** 12 个职业全测一遍，防止某个角色的基线算法不同导致自乘。 */
    static void allRolesIdempotent() {
        int bad = 0;
        StringBuilder detail = new StringBuilder();
        for (int role = 0; role < 12; role++) {
            Game g = fresh(2000L + role, role);
            Game.Player p = g.players.get(0);
            g.grantRelic(p, 30);
            g.grantRelic(p, 34);
            g.recompute(p);
            float d1 = p.damage;
            for (int i = 0; i < 6; i++) g.recompute(p);
            if (!eq(d1, p.damage)) {
                bad++;
                if (detail.length() < 300) {
                    detail.append("role").append(role).append(": ")
                          .append(d1).append("→").append(p.damage).append("  ");
                }
            }
        }
        check("全部 12 个职业的 recompute 均幂等", bad == 0,
                bad + " 个职业不自乘：" + detail);
    }

    static boolean eq(float a, float b) { return Math.abs(a - b) < 1e-3f; }
}
