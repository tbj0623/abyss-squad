import com.dilao.core.*;
import java.util.*;
import java.util.Locale;

/**
 * 遗物有效性测试（回归防线）。
 *
 * <p>对 60 件遗物逐一做"空手 vs 满层"对照，判定标准为下列任一成立：
 * <ol>
 *   <li><b>面板差分</b>：伤害/攻速/移速/暴击/生命上限/穿透/弹丸/护盾/环绕/地雷/分裂/弹射/光束/召唤/眩晕/处决/击退/反伤/冷却系数/回复/拾取/星尘 任一发生变化。</li>
 *   <li><b>行为差分</b>：同种子同房间跑 25 秒，击杀数 / 承伤 / 剩余生命 / 星尘 任一出现可测量差异。</li>
 *   <li><b>元素改派</b>：开火后弹丸 element 发生变化（针对 1 / 29 两件强制元素遗物）。</li>
 * </ol>
 *
 * <p>三档皆无差异 → FAIL 并打印 id/名称。任何遗物改动后都应重跑本测试。
 *
 * <p>用法：{@code java -cp build\core-check:build\relic-test RelicEffectTest}
 * <p>退出码：0 全部有效；1 存在死遗物。
 */
public final class RelicEffectTest {

    static int fails = 0;
    static final List<String> dead = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("== 遗物有效性回归测试：60 件逐件对照 ==");
        System.out.println("id,name,school,panel_changed,behavior_changed,element,verdict");

        for (int id = 0; id < Relics.COUNT; id++) {
            boolean panel = panelDiffers(id);
            boolean behavior = behaviorDiffers(id);
            boolean element = elementDiffers(id);
            boolean ok = panel || behavior || element;

            System.out.printf(Locale.US, "%d,%s,%d,%s,%s,%s,%s%n",
                    id, Relics.NAMES[id], Relics.SCHOOL[id],
                    panel ? "Y" : "-", behavior ? "Y" : "-", element ? "Y" : "-",
                    ok ? "OK" : "DEAD");

            if (!ok) {
                fails++;
                dead.add(id + ":" + Relics.NAMES[id]);
            }
        }

        System.out.println();
        System.out.println("有效 " + (Relics.COUNT - fails) + " / " + Relics.COUNT);
        if (fails > 0) {
            System.out.println("FAIL 无任何效果的遗物 " + fails + " 件：");
            for (String d : dead) System.out.println("  - " + d);
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 面板差分
    // ══════════════════════════════════════════════════════════════

    static final class Panel {
        float damage, interval, speed, crit, maxHp, shieldMax, dashFactor, bonusRegen, magnet, bonusCoin;
        int pierce, multishot, slow, leech, explode, orbit, mine, split, ricochet, beam, summon;
        int stun, execute, knockback, thorns, armorBreak, flags;

        static Panel of(Game.Player p) {
            Panel v = new Panel();
            v.damage = p.damage; v.interval = p.interval; v.speed = p.speed; v.crit = p.crit;
            v.maxHp = p.maxHp; v.shieldMax = p.shieldMax; v.dashFactor = p.dashFactor;
            v.bonusRegen = p.bonusRegen; v.magnet = p.magnet; v.bonusCoin = p.bonusCoin;
            v.pierce = p.pierce; v.multishot = p.multishot; v.slow = p.slow; v.leech = p.leech;
            v.explode = p.explode; v.orbit = p.orbitCount; v.mine = p.mineCount; v.split = p.splitCount;
            v.ricochet = p.ricochetCount; v.beam = p.beamCount; v.summon = p.summonCount;
            v.stun = p.stunChance; v.execute = p.execute; v.knockback = p.knockback;
            v.thorns = p.thorns; v.armorBreak = p.armorBreak; v.flags = p.flags;
            return v;
        }

        boolean differs(Panel o) {
            // flags 本身不是效果：引擎没有直接读 p.flags 改变战斗，只有 13 处 p.has(F_X)。
            // 因此 flags 差异不计入"有效果"，否则会给假机制遗物发通行证。
            return ne(damage, o.damage) || ne(interval, o.interval) || ne(speed, o.speed)
                || ne(crit, o.crit) || ne(maxHp, o.maxHp) || ne(shieldMax, o.shieldMax)
                || ne(dashFactor, o.dashFactor) || ne(bonusRegen, o.bonusRegen)
                || ne(magnet, o.magnet) || ne(bonusCoin, o.bonusCoin)
                || pierce != o.pierce || multishot != o.multishot || slow != o.slow
                || leech != o.leech || explode != o.explode || orbit != o.orbit
                || mine != o.mine || split != o.split || ricochet != o.ricochet
                || beam != o.beam || summon != o.summon || stun != o.stun
                || execute != o.execute || knockback != o.knockback || thorns != o.thorns
                || armorBreak != o.armorBreak;
        }

        static boolean ne(float a, float b) { return Math.abs(a - b) > 1e-3f; }
    }

    static boolean panelDiffers(int relicId) {
        Game g = fresh();
        Game.Player p = g.players.get(0);
        Panel before = Panel.of(p);
        grantMax(g, p, relicId);
        return Panel.of(p).differs(before);
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 行为差分
    // ══════════════════════════════════════════════════════════════

    static final class Behavior { int kills, coins, level; float taken, hp; }

    static Behavior run(Game.Player p, Game g, int ticks) {
        double ang = 0;
        for (int t = 0; t < ticks && g.phase == Game.FIGHT; t++) {
            ang += .035;
            g.input(p.id, (float) Math.cos(ang), (float) Math.sin(ang) * .7f, 0, 0, false, 0, 0);
            g.update(Game.STEP);
        }
        Behavior b = new Behavior();
        b.kills = g.kills; b.coins = g.coins; b.level = g.level;
        b.taken = g.damageTaken; b.hp = p.hp;
        return b;
    }

    static boolean behaviorDiffers(int relicId) {
        long seed = 77_001L + relicId;
        Game g0 = ready(seed, -1);
        Behavior b0 = run(g0.players.get(0), g0, 25 * 30);

        Game g1 = ready(seed, relicId);
        Behavior b1 = run(g1.players.get(0), g1, 25 * 30);

        return Math.abs(b1.kills - b0.kills) >= 2
            || Math.abs(b1.taken - b0.taken) >= 3f
            || Math.abs(b1.hp - b0.hp) >= 3f
            || Math.abs(b1.coins - b0.coins) >= 4
            || b1.level != b0.level;
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 元素改派
    // ══════════════════════════════════════════════════════════════

    /** 只有强制元素的两件：1 火、29 冰。其余返回 false。 */
    static boolean elementDiffers(int relicId) {
        if (relicId != 1 && relicId != 29) return false;
        Game g = ready(88_001L + relicId, relicId);
        Game.Player p = g.players.get(0);
        // 手动锁定朝向并开火
        for (int t = 0; t < 90; t++) {
            g.input(p.id, 1f, 0f, 1f, 0f, true, 0, 0);
            g.update(Game.STEP);
            if (!g.shots.isEmpty()) {
                int element = g.shots.get(0).element & 3;
                return relicId == 1 ? element == 1 : element == 2;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    static Game fresh() {
        Game g = new Game(42L);
        g.addPlayer(1, "t", 0);
        g.contract = 1;
        g.startSolo(1, Game.MODE_EXPEDITION);
        return g;
    }

    /** 进入第一间战斗房，可选先给遗物。 */
    static Game ready(long seed, int relicId) {
        Game g = new Game(seed);
        Game.Player p = g.addPlayer(1, "t", 0);
        g.contract = 1;
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.selectRoute(0);
        if (relicId >= 0) grantMax(g, p, relicId);
        return g;
    }

    /** 授予到该遗物的层数上限。 */
    static void grantMax(Game g, Game.Player p, int id) {
        int cap = Math.max(1, Relics.CAP[id]);
        for (int i = 0; i < cap; i++) g.grantRelic(p, id);
    }
}
