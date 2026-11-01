import com.dilao.core.*;

/**
 * 验证内容深度审计报告指出的 4 个结构性缺陷是否真实存在。
 * <p>只读验证，不改任何状态。用真实游戏路径，不直接改裸字段。
 */
public final class StructuralProbe {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("CONFIRMED  " + name + "  |  " + detail); }
        else { fail++; System.out.println("NOT-REPRO  " + name + "  |  " + detail); }
    }

    static Game fresh(long seed, int role) {
        Game g = new Game(seed);
        Game.Player p = g.addPlayer(1, "p", role);
        g.contract = 1;
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        return g;
    }

    public static void main(String[] a) {
        bug1_metaProgressWipedByRecompute();
        bug2_multishotNoPenalty();
        bug3_areaAttributeDead();
        bug4_crossSchoolUnwired();
        bug5_mechanicNeverDispatched();
        System.out.println();
        System.out.println("confirmed=" + pass + "  not-reproduced=" + fail);
    }

    // ── 1. recompute 是否清掉"遗物之外的成长" ──
    static void bug1_metaProgressWipedByRecompute() {
        Game g = fresh(1234L, 0);
        Game.Player p = g.players.get(0);
        // 模拟 Session.applyMetaBoosts 的做法：在 startSolo 之前/之后直接写裸字段
        p.damage = 27.3f;
        p.maxHp = 184f;
        p.crit = .20f;
        // v2.0 P0-1 的真实路径：外部写完裸字段后调 applyMetaBoostedStats()
        g.applyMetaBoostedStats();
        // 再捡一件遗物触发普通 recompute —— 成长不应丢失
        g.grantRelic(p, 30);
        g.recompute(p);
        boolean wiped = p.damage < 25f || p.maxHp < 175f || p.crit < .15f;
        check("元进度成长在 applyMetaBoostedStats + 后续 recompute 后存活", !wiped,
                "写入 dmg=27.3/hp=184/crit=.20 → recompute 后 dmg=" + p.damage
                        + "/hp=" + p.maxHp + "/crit=" + p.crit);
    }

    // ── 2. multishot 是否有单发惩罚 ──
    static void bug2_multishotNoPenalty() {
        Game g = fresh(99L, 0);
        Game.Player p = g.players.get(0);
        // 走真实路径：授予"分裂棱镜"（+1 弹丸）并让 recompute 派发，
        // 而不是手动改 p.multishot（那样不经过任何派发，测不到惩罚）。
        g.recompute(p);
        float dmg0 = p.damage;
        int m0 = p.multishot;
        g.grantRelic(p, Relics.SPLIT_PRISM);
        float dmg1 = p.damage;
        int m1 = p.multishot;
        g.grantRelic(p, Relics.SPLIT_PRISM);
        float dmg2 = p.damage;
        int m2 = p.multishot;
        boolean penalized = (m1 > m0 && dmg1 < dmg0 - .01f)
                || (m2 > m1 && dmg2 < dmg1 - .01f);
        float dpsRatio = (m2 * dmg2) / Math.max(.01f, m0 * dmg0);
        check("multishot 提升未降低单发伤害（无惩罚）", !penalized && m2 > m0,
                "弹丸 " + m0 + "→" + m1 + "→" + m2 + "：单发 dmg " + dmg0 + " → " + dmg1
                        + " → " + dmg2 + "，DPS 倍率 " + dpsRatio);
    }

    // ── 3. AREA 属性是否真的被消费 ──
    static void bug3_areaAttributeDead() {
        Game g = fresh(7L, 0);
        Game.Player p = g.players.get(0);
        g.recompute(p);
        float before = p.areaBonus;
        // 找一件 AREA 最高但 POWER 为 0 的遗物
        int best = -1;
        float bestArea = 0;
        for (int i = 0; i < Relics.COUNT; i++) {
            if (Relics.POWER[i] <= 0.001f && Relics.AREA[i] > bestArea) { bestArea = Relics.AREA[i]; best = i; }
        }
        if (best < 0) { check("AREA 属性被消费", true, "找不到纯 AREA 遗物，跳过"); return; }
        for (int s = 0; s < Relics.CAP[best]; s++) g.grantRelic(p, best);
        g.recompute(p);
        float after = p.areaBonus;
        boolean stillDead = Math.abs(before - after) < .001f;
        check("纯 AREA 遗物不改变任何可观测属性（AREA 死属性）", stillDead,
                "grant " + Relics.NAMES[best] + "（AREA=" + bestArea + ", POWER=0）："
                        + "areaBonus " + before + " → " + after);
    }

    // ── 4. 跨流派共鸣是否接线 ──
    static void bug4_crossSchoolUnwired() {
        Game g = fresh(555L, 0);
        Game.Player p = g.players.get(0);
        g.recompute(p);
        // 定向选真正能触发某个组合的两件遗物（分别属于 CROSS_A[i] / CROSS_B[i] 流派），
        // 而不是"随便两件不同流派"——有些流派对并不在 6 组组合表里。
        int relicA = -1, relicB = -1;
        int hitCross = -1;
        for (int i = 0; i < Content.CROSS_COUNT && relicA < 0; i++) {
            int sa = Content.CROSS_A[i], sb = Content.CROSS_B[i];
            int ra = -1, rb = -1;
            for (int r = 0; r < Relics.COUNT; r++) {
                if (ra < 0 && Relics.SCHOOL[r] == sa) ra = r;
                if (rb < 0 && Relics.SCHOOL[r] == sb) rb = r;
            }
            if (ra >= 0 && rb >= 0) { relicA = ra; relicB = rb; hitCross = i; }
        }
        if (relicA < 0) { check("跨流派共鸣 crossMask 被赋值", true, "无可用组合，跳过"); return; }
        g.grantRelic(p, relicA);
        g.grantRelic(p, relicB);
        g.recompute(p);
        boolean wired = p.crossMask != 0 && p.cross(hitCross);
        check("跨流派共鸣 crossMask 未被赋值", !wired,
                "grant " + Relics.NAMES[relicA] + "（流派" + Relics.SCHOOL[relicA] + "）+ "
                        + Relics.NAMES[relicB] + "（流派" + Relics.SCHOOL[relicB] + "）"
                        + " → 期望组合 " + hitCross + " " + Content.CROSS_NAMES[hitCross]
                        + "，crossMask=" + p.crossMask);
    }

    // ── 5. evoMechanic 是否被分发 ──
    static void bug5_mechanicNeverDispatched() {
        Game g = fresh(321L, 0);
        Game.Player p = g.players.get(0);
        int mechField = -999;
        try {
            java.lang.reflect.Field f = Game.Player.class.getDeclaredField("evoMechanic");
            f.setAccessible(true);
            mechField = (int) f.get(p);
        } catch (Throwable t) {
            System.out.println("  （Player 无 evoMechanic 字段）");
        }
        check("evoMechanic 字段存在且可读", mechField != -999, "值=" + mechField);
        System.out.println("      注：是否有 switch 分派需静态检查，本探针无法判断");
    }
}
