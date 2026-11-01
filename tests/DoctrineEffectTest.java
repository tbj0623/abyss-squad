import com.dilao.core.*;

import java.io.*;
import java.util.*;

/**
 * 天赋牌（Doctrine）有效性回归测试。
 *
 * <p><b>为什么需要它</b>：{@code Meta.java} 里 18 张牌的完整数据表、{@code DoctrineHooks.java}
 * 的 32 个钩子常量、{@code docs/doctrine-hooks.md} 的逐钩子落点全都存在过很长一段时间，
 * 而 {@code Game.java} 里一行分派代码都没有——玩家装备的牌 100% 不生效。
 * 这和 RelicEffectTest 记录的那次事故是同一类：<b>数据 + 常量 + 参数全都在，只有消费端缺失</b>。
 *
 * <p>本测试对 18 张牌逐张做"装备前 vs 装备后"对照，判定标准为下列任一成立：
 * <ol>
 *   <li><b>开局差分</b>：{@code startSolo()} 之后星尘 / 重掷 / 遗物 / 最大生命 / 暴击 任一变化。</li>
 *   <li><b>面板差分</b>：塞几件遗物后重算，伤害 / 攻速 / 暴击 / 生命 / 护盾 / 遗物上限 任一变化。</li>
 *   <li><b>行为差分</b>：同种子同房间跑 20 秒，击杀 / 承伤 / 剩余生命 / 星尘 / 敌人移速 任一出现可测量差异。</li>
 *   <li><b>定点探针</b>：进化、闪避这类"平时不触发"的牌，用专门的场景直接验证（见 {@link #specialEffect}）。</li>
 * </ol>
 *
 * <p>另外三项硬校验：
 * <ul>
 *   <li>core 的 {@link DoctrineHooks} 数值与 app 的 {@code Meta.H_*} 逐一对照（本文件硬编码期望值，
 *       因为 core 不能依赖 app，测试也只编译 core）。</li>
 *   <li>每张牌的<b>主钩子与副钩子两面</b>都要真的生效——只验"有差异"会让"代价实现了、收益没实现"
 *       的纯亏损牌蒙混过关。</li>
 *   <li>Codec v7 → v8 迁移：v7 字节流读入后新字段必须是默认值（doctrine = -1 / 参数全 0）。</li>
 * </ul>
 *
 * <p>用法：{@code java -ea -cp build/core-tests DoctrineEffectTest}
 * <p>退出码：0 全部通过；1 存在无效牌或校验失败。
 */
public final class DoctrineEffectTest {

    static int fails;
    static final List<String> problems = new ArrayList<>();

    // ── 18 张牌的期望数据（来源：app/src/main/java/com/dilao/abysssquad/Meta.java）──
    // 硬编码而不是反射读取 Meta：core 不能依赖 app，测试脚本也只编译 core + tests，
    // 不会把 Android 类拉进来。数值改了就改这里，等于给数据表加了一道锁。
    static final String[] CARD_NAME = {
            "疾风契约", "双重进化", "疾影步",
            "余烬开局", "献祭契约", "焚身",
            "霜封", "寒冰壁垒", "极寒迟滞",
            "生命开局", "荆棘誓约", "血脉共鸣",
            "雷光开局", "过载运转", "雷霆传导",
            "星尘债务", "玻璃大炮", "孤注一掷"
    };
    static final int[] CARD_HOOK = {
            DoctrineHooks.H_START_RELIC, DoctrineHooks.H_MAX_EVOLUTIONS, DoctrineHooks.H_DASH_COSTS_COIN,
            DoctrineHooks.H_START_RELIC, DoctrineHooks.H_MAXHP_MUL, DoctrineHooks.H_HURT_FIRE_RING,
            DoctrineHooks.H_ALL_SHOTS_SLOW, DoctrineHooks.H_FLOOR_SHIELD, DoctrineHooks.H_ENEMY_SPEED_MUL,
            DoctrineHooks.H_START_RELIC, DoctrineHooks.H_REFLECT, DoctrineHooks.H_KILL_HEAL,
            DoctrineHooks.H_START_RELIC, DoctrineHooks.H_OVERHEAT, DoctrineHooks.H_CHAIN_ON_KILL,
            DoctrineHooks.H_START_COINS, DoctrineHooks.H_TAKEN_DAMAGE_MUL, DoctrineHooks.H_RELIC_EFFECT_MUL
    };
    static final float[] CARD_P0 = {
            1f, 2f, 6f,
            0f, .75f, 130f,
            1.5f, .30f, -.25f,
            3f, .35f, 25f,
            4f, 6f, .60f,
            -50f, 1.5f, 1.6f
    };
    static final float[] CARD_P1 = {
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, .18f,
            0f, 0f, 0f,
            0f, 0f, 0f
    };
    static final int[] CARD_SIDE = {
            DoctrineHooks.H_COINS_ZERO, DoctrineHooks.H_EVOLVE_LEVEL_PEN, DoctrineHooks.H_DASH_ATKSPEED,
            DoctrineHooks.H_SCHOOL_LOCK, DoctrineHooks.H_LEVELUP_EXTRA_CHOICE, DoctrineHooks.H_TAKEN_DAMAGE_MUL,
            DoctrineHooks.H_SHOT_DAMAGE_MUL, DoctrineHooks.H_SHIELD_NO_REGEN, DoctrineHooks.H_ROOM_TIME_MUL,
            DoctrineHooks.H_NO_FLOOR_HEAL, DoctrineHooks.H_REGEN_MUL, DoctrineHooks.H_MAXHP_MUL,
            DoctrineHooks.H_REROLL_ZERO, DoctrineHooks.H_ATKSPEED_MUL, DoctrineHooks.H_CRIT_ZERO,
            DoctrineHooks.H_SHOP_PRICE_MUL, DoctrineHooks.H_DAMAGE_MUL, DoctrineHooks.H_RELIC_SLOTS
    };
    static final float[] CARD_P2 = {
            0f, 3f, .60f,
            3f, 1f, 1.2f,
            -.20f, 0f, -.20f,
            0f, -.60f, .85f,
            0f, .45f, 0f,
            -.25f, 1.6f, 2f
    };

    public static void main(String[] args) throws Exception {
        System.out.println("== 天赋牌有效性回归测试：18 张逐张对照 ==");

        hooksMatchMeta();
        cardTableIsConsistent();
        applyRejectsOutOfRange();
        eachCardHasEffect();
        mainAndSideBothBite();
        codecV8RoundTrip();
        codecV7MigratesToNoDoctrine();

        System.out.println();
        if (fails > 0) {
            System.out.println("FAIL " + fails + " 项：");
            for (String s : problems) System.out.println("  - " + s);
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 交叉校验：core.DoctrineHooks 与 app.Meta 的钩子 id 数值一致
    // ══════════════════════════════════════════════════════════════

    /**
     * 钩子 id 的期望数值（来源：{@code Meta.H_NONE=0 ... H_RELIC_EFFECT_MUL=31}）。
     * <p>若有人给 {@code DoctrineHooks} 重新编号，这个测试会立刻拦下——而玩家侧的表现是
     * "牌面写着 A，实战生效的是 B"，没有本测试根本查不出来。
     */
    static void hooksMatchMeta() {
        int[] expected = {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
        };
        int[] actual = {
                DoctrineHooks.H_NONE, DoctrineHooks.H_START_RELIC, DoctrineHooks.H_COINS_ZERO,
                DoctrineHooks.H_SCHOOL_LOCK, DoctrineHooks.H_NO_FLOOR_HEAL, DoctrineHooks.H_REROLL_ZERO,
                DoctrineHooks.H_ALL_SHOTS_SLOW, DoctrineHooks.H_SHOT_DAMAGE_MUL, DoctrineHooks.H_START_COINS,
                DoctrineHooks.H_SHOP_PRICE_MUL, DoctrineHooks.H_MAX_EVOLUTIONS, DoctrineHooks.H_EVOLVE_LEVEL_PEN,
                DoctrineHooks.H_DASH_COSTS_COIN, DoctrineHooks.H_DASH_ATKSPEED, DoctrineHooks.H_MAXHP_MUL,
                DoctrineHooks.H_LEVELUP_EXTRA_CHOICE, DoctrineHooks.H_HURT_FIRE_RING, DoctrineHooks.H_TAKEN_DAMAGE_MUL,
                DoctrineHooks.H_FLOOR_SHIELD, DoctrineHooks.H_SHIELD_NO_REGEN, DoctrineHooks.H_ENEMY_SPEED_MUL,
                DoctrineHooks.H_ROOM_TIME_MUL, DoctrineHooks.H_REFLECT, DoctrineHooks.H_REGEN_MUL,
                DoctrineHooks.H_KILL_HEAL, DoctrineHooks.H_DAMAGE_MUL, DoctrineHooks.H_OVERHEAT,
                DoctrineHooks.H_ATKSPEED_MUL, DoctrineHooks.H_CHAIN_ON_KILL, DoctrineHooks.H_CRIT_ZERO,
                DoctrineHooks.H_RELIC_SLOTS, DoctrineHooks.H_RELIC_EFFECT_MUL
        };
        check(Arrays.equals(expected, actual), "DoctrineHooks 32 个钩子 id 与 Meta.H_* 数值一致");
        check(DoctrineHooks.COUNT == 18, "DoctrineHooks.COUNT == Meta.DOCTRINE_COUNT == 18");
        check(DoctrineHooks.MAX == 31, "DoctrineHooks.MAX == 31");
        check(DoctrineHooks.valid(-1) && DoctrineHooks.valid(0) && DoctrineHooks.valid(31),
                "DoctrineHooks.valid 接受 -1..31");
        check(!DoctrineHooks.valid(32) && !DoctrineHooks.valid(-2), "DoctrineHooks.valid 拒绝越界");
    }

    /** 18 张牌的表本身要自洽：主/副钩子都合法，牌名不重复。 */
    static void cardTableIsConsistent() {
        boolean ok = true;
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 18; i++) {
            if (!DoctrineHooks.valid(CARD_HOOK[i]) || !DoctrineHooks.valid(CARD_SIDE[i])) ok = false;
            if (CARD_HOOK[i] == DoctrineHooks.H_NONE) ok = false;     // 每张牌必须有一个主钩子
            if (!names.add(CARD_NAME[i])) ok = false;
        }
        check(ok, "18 张牌的主/副钩子 id 全部合法、牌名不重复");
    }

    // ══════════════════════════════════════════════════════════════
    //  2. applyDoctrine 的越界拒绝
    // ══════════════════════════════════════════════════════════════

    static void applyRejectsOutOfRange() {
        Game g = new Game(1L);
        g.addPlayer(1, "t", 0);
        check(!g.applyDoctrine(18, 1, 2, 1, 1, 1), "applyDoctrine 拒绝牌号 18");
        check(!g.applyDoctrine(-2, 1, 2, 1, 1, 1), "applyDoctrine 拒绝牌号 -2");
        check(!g.applyDoctrine(0, 32, 2, 1, 1, 1), "applyDoctrine 拒绝主钩子 32");
        check(!g.applyDoctrine(0, 1, -5, 1, 1, 1), "applyDoctrine 拒绝副钩子 -5");
        check(!g.applyDoctrine(0, 1, 2, Float.NaN, 1, 1), "applyDoctrine 拒绝 NaN 参数");
        check(g.doctrine == -1, "非法装备不写入牌号");
        check(g.applyDoctrine(0, 1, 2, 1, 0, 0), "applyDoctrine 接受合法牌");
        check(g.doctrine == 0 && g.doctrineHook == 1 && g.doctrineSideHook == 2, "合法装备写入牌号与钩子");
        check(g.applyDoctrine(-1, 1, 2, 1, 0, 0), "applyDoctrine(-1) 表示卸下");
        check(g.doctrine == -1 && g.doctrineHook == 0 && g.doctrineParams[0] == 0f,
                "卸下后牌号/钩子/参数全部清空");
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 18 张牌逐张：装备前 vs 装备后
    // ══════════════════════════════════════════════════════════════

    static final class Panel {
        int coins, rerolls, relicKinds, evoTaken;
        float damage, interval, crit, maxHp, shieldMax, shield, speed;
        boolean differs(Panel o) {
            return coins != o.coins || rerolls != o.rerolls || relicKinds != o.relicKinds
                || evoTaken != o.evoTaken
                || ne(damage, o.damage) || ne(interval, o.interval) || ne(crit, o.crit)
                || ne(maxHp, o.maxHp) || ne(shieldMax, o.shieldMax) || ne(shield, o.shield)
                || ne(speed, o.speed);
        }
    }

    static Panel panel(Game g) {
        Panel v = new Panel();
        v.coins = g.coins; v.rerolls = g.rerolls;
        Game.Player p = g.players.isEmpty() ? null : g.players.get(0);
        if (p == null) return v;
        for (int i = 0; i < Relics.COUNT; i++) if (p.relics[i] > 0) v.relicKinds++;
        v.evoTaken = p.evolutionsTaken;
        v.damage = p.damage; v.interval = p.interval; v.crit = p.crit;
        v.maxHp = p.maxHp; v.shieldMax = p.shieldMax; v.shield = p.shield; v.speed = p.speed;
        return v;
    }

    static final class Behavior {
        int kills, coins, level, enemies;
        float taken, hp, enemySpeed;
    }

    static Behavior run(Game g, int ticks) {
        Game.Player p = g.players.get(0);
        double ang = 0;
        for (int t = 0; t < ticks && g.phase == Game.FIGHT; t++) {
            ang += .035;
            g.input(p.id, (float) Math.cos(ang), (float) Math.sin(ang) * .7f, 0, 0, false, 0, 0);
            g.update(Game.STEP);
        }
        Behavior b = new Behavior();
        b.kills = g.kills; b.coins = g.coins; b.level = g.level;
        b.taken = g.damageTaken; b.hp = p.hp; b.enemies = g.enemies.size();
        b.enemySpeed = g.enemies.isEmpty() ? 0f : g.enemies.get(0).speed;
        return b;
    }

    static boolean behaviorDiffers(int card) {
        long seed = 91_000L + card;
        Behavior b0 = run(ready(seed, -1), 20 * 30);
        Behavior b1 = run(ready(seed, card), 20 * 30);
        return Math.abs(b1.kills - b0.kills) >= 2
            || Math.abs(b1.taken - b0.taken) >= 3f
            || Math.abs(b1.hp - b0.hp) >= 3f
            || Math.abs(b1.coins - b0.coins) >= 4
            || Math.abs(b1.enemies - b0.enemies) >= 3
            || b1.level != b0.level
            || Math.abs(b1.enemySpeed - b0.enemySpeed) >= 2f;
    }

    static void eachCardHasEffect() {
        System.out.println();
        System.out.println("card,name,start,panel,behavior,probe,verdict");
        for (int card = 0; card < 18; card++) {
            boolean start = startDiffers(card);
            boolean panel = panelDiffers(card);
            boolean behavior = start || panel ? false : behaviorDiffers(card);   // 省时间：已有效果就不跑行为
            boolean probe = specialEffect(card);
            boolean ok = start || panel || behavior || probe;
            System.out.printf(Locale.US, "%d,%s,%s,%s,%s,%s,%s%n", card, CARD_NAME[card],
                    start ? "Y" : "-", panel ? "Y" : "-", behavior ? "Y" : "-",
                    probe ? "Y" : "-", ok ? "OK" : "DEAD");
            if (!ok) {
                fails++;
                problems.add(card + ":" + CARD_NAME[card] + " 装备前后无任何可测差异（牌未接线）");
            }
        }
    }

    /** 开局差分：startSolo 之后立刻对比（覆盖开局类钩子）。 */
    static boolean startDiffers(int card) {
        return panel(fresh(-1)).differs(panel(fresh(card)));
    }

    /**
     * 面板差分：装备后在有遗物的战斗房里重算再比。
     * <p>专门覆盖"乘区类"钩子（#4 最大生命 / #13 攻速 / #14 暴击 / #17 遗物效果）。
     */
    static boolean panelDiffers(int card) {
        Game a = ready(1L, -1);
        Game b = ready(1L, card);
        // 给两边都塞几件遗物，让 #17 的"遗物效果 ×1.6"有东西可放大
        for (int id : new int[]{Relics.VITAL, Relics.WIND_GEAR, Relics.CRIT_LENS}) {
            a.grantRelic(a.players.get(0), id);
            b.grantRelic(b.players.get(0), id);
        }
        return panel(a).differs(panel(b));
    }

    /**
     * 定点探针：只针对"平时不触发、跑 20 秒看不出来"的牌。
     * <p>#1 双重进化要进铁匠铺；#2 疾影步要主动闪避。两张都不在通用对照里体现。
     */
    static boolean specialEffect(int card) {
        if (card == 1) {
            // 双重进化：一次 evolveWeapon 应该解锁 2 条线
            Game a = ready(31L, -1);
            Game b = ready(31L, 1);
            a.phase = Game.ARMORY;
            b.phase = Game.ARMORY;
            a.evolveWeapon(0);
            b.evolveWeapon(0);
            return b.players.get(0).evolutionsTaken > a.players.get(0).evolutionsTaken;
        }
        if (card == 2) {
            // 疾影步：闪避扣星尘并挂攻速增益
            Game g = ready(32L, 2);
            Game.Player p = g.players.get(0);
            p.invuln = 1e9f;
            int coins0 = g.coins;
            p.dashCd = 0;
            g.input(p.id, 1, 0, 1, 0, true, 1, 0);
            return g.coins <= coins0 - 6 && p.dashHaste > 0f;
        }
        if (card == 5 || card == 10 || card == 16) {
            // 受击类牌（#5 火环 / #10 反弹 / #16 承伤放大）：
            // 玩家绕圈跑 20 秒可能一次都没挨打，必须构造"贴身围殴"才能测出来。
            return hurtProbe(card);
        }
        return false;
    }

    /**
     * 受击探针：把一圈敌人贴到玩家身上、让它们打玩家，比较"有牌 / 无牌"两种情况下
     * 敌人掉的血与玩家掉的血。
     *
     * <p>用真实 {@code update()} 驱动，走的是 contactDamage → hurt 的真实路径。
     */
    static boolean hurtProbe(int card) {
        float[] with = hurtRun(card);
        float[] without = hurtRun(-1);
        // 任一维度出现差异即算生效：玩家承伤（#16 / #5 代价）、敌人掉血（#5 火环 / #10 反弹）
        return Math.abs(with[0] - without[0]) > 1f        // 玩家承伤
                || Math.abs(with[1] - without[1]) > 1f;   // 敌人总血量
    }

    /** @return {玩家累计承伤, 敌人剩余总血量} */
    static float[] hurtRun(int card) {
        Game g = ready(51_000L, card);
        Game.Player p = g.players.get(0);
        p.invuln = 0f;
        p.hp = p.maxHp = 5000f;                 // 撑住，别在 40 帧里死掉
        g.enemies.clear();
        for (int i = 0; i < 6; i++) {
            Game.Enemy e = new Game.Enemy();
            e.id = 5000 + i;
            e.type = 0;
            e.behavior = Game.B_CHASE;
            e.r = 16f;
            // 血量给到极高：这样火环/反弹**打不死**它们，40 帧内围殴人数恒定，
            // 承伤与敌人掉血两个维度才是可比的干净量。
            e.hp = e.maxHp = 1e6f;
            e.speed = 0f;                       // 站桩贴身，只测受击链路
            e.cooldown = 99f;                   // 不主动出手，靠接触伤害
            e.stage = -1;
            e.x = p.x + (i - 3) * 10f;
            e.y = p.y;
            g.enemies.add(e);
        }
        for (int t = 0; t < 40; t++) g.update(Game.STEP);
        float enemyHp = 0f;
        for (Game.Enemy e : g.enemies) enemyHp += Math.max(0f, e.hp);
        return new float[]{g.damageTaken, enemyHp};
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 同一张牌的主钩子与副钩子都要真的生效
    // ══════════════════════════════════════════════════════════════

    /**
     * 逐牌断言"收益 + 代价"两面都真的落地。
     *
     * <p>只验"有差异"是不够的：#5 焚身如果把 1.2 倍承伤写进了代码但火环没写，
     * 上面那条测试照样通过（因为承伤变了），玩家拿到的就是一张纯亏损牌。
     */
    static void mainAndSideBothBite() {
        System.out.println();

        // #0 疾风契约：开局给遗物 + 星尘清零
        Game g0 = fresh(0);
        check(g0.coins == 0, "#0 疾风契约 副钩子：开局星尘清零（实际 " + g0.coins + "）");
        check(relicKindsOf(g0) > 0, "#0 疾风契约 主钩子：开局至少 1 件遗物");
        Game g0b = fresh(3);
        check(relicKindsOf(g0b) > 0, "#3 余烬开局 主钩子：开局至少 1 件遗物");
        Game g0c = fresh(9);
        check(relicKindsOf(g0c) > 0, "#9 生命开局 主钩子：开局至少 1 件遗物");
        // #9 副钩子：首领房清场时的"每层自动回复"被关掉（对照仍有 20%）
        float withHeal = bossClearHp(-1);
        float noHeal = bossClearHp(9);
        check(noHeal < withHeal - 1f, "#9 生命开局 副钩子：每层结束不再自动回复（"
                + noHeal + " vs " + withHeal + "）");
        Game g0d = fresh(12);
        check(relicKindsOf(g0d) > 0 && g0d.rerolls == 0, "#12 雷光开局：开局遗物 + 重掷归零");
        {
            // 代价必须是"整局"，而不是"只在开局那一帧"：进一层后仍然是 0。
            Game g12 = ready(121L, 12);
            check(g12.rerolls == 0, "#12 雷光开局 副钩子：进层后重掷次数仍为 0（"
                    + g12.rerolls + "）");
            prepareChoice(g12, 1);
            check(g12.rerolls == 0, "#12 雷光开局 副钩子：三选一界面同样没有重掷");
        }
        // #3 副钩子：被锁的流派（生命=3）不能再出现在商店货架上
        check(schoolLockedInShop(3, 60), "#3 余烬开局 副钩子：生命系遗物本局不再出现");
        // 对照：不装备时同一个 60 次抽样里一定抽得到生命系
        check(!schoolLockedInShop(-1, 60), "#3 对照：未装备时生命系遗物正常出现");

        // #2 疾影步：闪避扣星尘 + 闪避后攻速提升
        Game g2 = ready(41L, 2);
        Game.Player p2 = g2.players.get(0);
        p2.invuln = 1e9f;
        int coinsBefore = g2.coins;
        p2.dashCd = 0;
        g2.input(p2.id, 1, 0, 1, 0, true, 1, 0);          // dashSerial 0 -> 1 触发闪避
        check(g2.coins <= coinsBefore - 6, "#2 疾影步 主钩子：闪避消耗 6 星尘（"
                + coinsBefore + " -> " + g2.coins + "）");
        check(p2.dashHaste > 0f && p2.dashHasteMul > .5f, "#2 疾影步 副钩子：闪避后攻速增益已挂上");

        // #4 献祭契约：最大生命 -25%
        Game g4 = fresh(4);
        Game g4b = fresh(-1);
        check(g4.players.get(0).maxHp < g4b.players.get(0).maxHp * .9f,
                "#4 献祭契约 主钩子：最大生命降低（" + g4.players.get(0).maxHp
                        + " vs " + g4b.players.get(0).maxHp + "）");
        // #4 副钩子：升级累积额外三选一（直接喂经验，避免依赖击杀节奏）
        Game g4c = ready(42L, 4);
        Game.Player p4 = g4c.players.get(0);
        p4.invuln = 1e9f;
        g4c.doctrineBonusChoices = 0;
        int lv0 = g4c.level;
        gainXp(g4c, 500f, p4);
        check(g4c.level > lv0, "#4 献祭契约：喂经验后确实升级（Lv." + lv0 + " -> " + g4c.level + "）");
        check(g4c.doctrineBonusChoices > 0, "#4 献祭契约 副钩子：升级累积了额外三选一（"
                + g4c.doctrineBonusChoices + "）");
        // 把累积数消费掉：prepareChoice 应当把它并入 rewardsLeft
        prepareChoice(g4c, 1);
        check(g4c.rewardsLeft > 1, "#4 献祭契约 副钩子：下次三选一的次数被补发（rewardsLeft="
                + g4c.rewardsLeft + "）");

        // #5 焚身：承伤 +20% + 受伤火环
        Game g5 = fight(5);
        Game g5b = fight(-1);
        check(Math.abs(g5.players.get(0).maxHp - g5b.players.get(0).maxHp) < .01f,
                "#5 焚身：不改变最大生命（只改承伤与受击行为）");
        float[] with5 = hurtRun(5);
        float[] without5 = hurtRun(-1);
        check(with5[0] > without5[0] * 1.1f, "#5 焚身 副钩子：承受伤害 +20%（"
                + with5[0] + " vs " + without5[0] + "）");
        check(with5[1] < without5[1] - 1f, "#5 焚身 主钩子：受伤时火环烧到近身敌人（"
                + with5[1] + " vs " + without5[1] + "）");

        // #6 霜封：弹丸减速 + 弹丸伤害 -20%
        Game g6 = fight(6);
        boolean slows = false, shotSeen = false;
        for (int t = 0; t < 120 && !slows && g6.phase == Game.FIGHT; t++) {
            g6.update(Game.STEP);
            for (Game.Shot s : g6.shots) if (s.owner == g6.players.get(0).id) {
                shotSeen = true;
                if (s.slowSeconds > 1f) slows = true;
            }
        }
        check(shotSeen, "#6 霜封：玩家真的开火了");
        check(slows, "#6 霜封 主钩子：弹丸带 1.5s 减速");
        // 副钩子：弹丸伤害 -20%（用首发的真实弹丸伤害比，不靠面板推算）
        float shotBase = firstShotDamage(-1);
        float shotCard = firstShotDamage(6);
        check(shotBase > 0f && Math.abs(shotCard / shotBase - .8f) < .03f,
                "#6 霜封 副钩子：弹丸伤害 -20%（" + shotCard + " vs " + shotBase + "）");

        // #7 寒冰壁垒：每层开始 30% 护盾 + 护盾不自动恢复
        Game g7 = ready(71L, 7);
        Game.Player p7 = g7.players.get(0);
        check(p7.shieldMax >= p7.maxHp * .25f, "#7 寒冰壁垒 主钩子：进层给 30% 最大生命护盾（"
                + p7.shieldMax + " / " + p7.maxHp + "）");
        check(p7.shield > 0f, "#7 寒冰壁垒：护盾当前值已满");
        p7.invuln = 1e9f;
        p7.shield = 0f;
        for (int t = 0; t < 120; t++) g7.update(Game.STEP);
        check(p7.shield <= .01f, "#7 寒冰壁垒 副钩子：护盾不自动恢复（shield=" + p7.shield + "）");
        // 对照：没有这张牌时，护盾会自己回满（证明上面测的是"牌造成的差异"而不是"盾本来就不回"）
        Game g7b = ready(71L, -1);
        Game.Player q7 = g7b.players.get(0);
        q7.shieldMax = 60f;
        q7.shield = 0f;
        q7.invuln = 1e9f;
        for (int t = 0; t < 120; t++) g7b.update(Game.STEP);
        check(q7.shield > 1f, "#7 对照：未装备时护盾会自动恢复（" + q7.shield + "）");

        // #8 极寒迟滞：敌人移速 -25% + 挑战房限时 -20%
        Game g8 = fight(8);
        check(Math.abs(g8.roomTimeLimit - .8f) < .05f,
                "#8 极寒迟滞 副钩子：挑战房限时倍率 0.8（实际 " + g8.roomTimeLimit + "）");
        check(Math.abs(doctrineEnemySpeed(g8) - .75f) < .05f,
                "#8 极寒迟滞 主钩子：敌人移速乘区 0.75（实测 " + doctrineEnemySpeed(g8) + "）");

        // #10 荆棘誓约：反弹伤害 + 回复 -60%
        Game g10 = ready(101L, 10);
        Game.Player p10 = g10.players.get(0);
        p10.maxHp = p10.hp = 200f;
        p10.hp = 100f;
        float healed10 = g10.heal(p10, 50f);
        Game g10b = ready(101L, -1);
        Game.Player q10 = g10b.players.get(0);
        q10.maxHp = q10.hp = 200f;
        q10.hp = 100f;
        float healedBase = g10b.heal(q10, 50f);
        check(healed10 < healedBase * .5f, "#10 荆棘誓约 副钩子：回复效果 -60%（"
                + healed10 + " vs " + healedBase + "）");
        check(doctrineReflect(g10) > 0f, "#10 荆棘誓约 主钩子：反弹比例已生效");

        // #11 血脉共鸣：最大生命 -15% + 每 25 杀回 18%
        Game g11 = fresh(11);
        Game g11b = fresh(-1);
        check(g11.players.get(0).maxHp < g11b.players.get(0).maxHp * .95f,
                "#11 血脉共鸣 副钩子：最大生命 -15%（" + g11.players.get(0).maxHp
                        + " vs " + g11b.players.get(0).maxHp + "）");
        Game g11c = ready(111L, 11);
        Game.Player p11 = g11c.players.get(0);
        p11.hp = p11.maxHp * .3f;
        float hp11 = p11.hp;
        for (int i = 0; i < 25; i++) {
            Game.Enemy e = spawnDummy(g11c, 9000 + i, p11.x + 200, p11.y, 1f);
            killEnemy(g11c, e, p11);
        }
        check(p11.hp > hp11 + 1f, "#11 血脉共鸣 主钩子：满 25 杀回复生命（"
                + hp11 + " -> " + p11.hp + "）");

        // #14 雷霆传导：击杀连锁闪电（主钩子）
        Game g14c = ready(141L, 14);
        Game.Player p14 = g14c.players.get(0);
        Game.Enemy victim = spawnDummy(g14c, 9100, p14.x + 200, p14.y, 1f);
        Game.Enemy near = spawnDummy(g14c, 9101, p14.x + 230, p14.y, 5000f);
        killEnemy(g14c, victim, p14);
        check(near.hp < near.maxHp - .5f, "#14 雷霆传导 主钩子：击杀后连锁闪电打到邻近敌人（"
                + near.hp + " / " + near.maxHp + "）");

        // #13 过载运转：攻速 +45% + 每 6 秒过热停顿
        Game g13 = ready(131L, 13);
        Game.Player p13 = g13.players.get(0);
        Game g13b = ready(131L, -1);
        check(p13.interval < g13b.players.get(0).interval * .85f,
                "#13 过载运转 主钩子：攻速提升（interval " + p13.interval
                        + " vs " + g13b.players.get(0).interval + "）");
        p13.invuln = 1e9f;
        boolean stunned = false;
        for (int t = 0; t < 30 * 8 && !stunned; t++) {
            g13.update(Game.STEP);
            if (p13.overheatStun > 0f) stunned = true;
        }
        check(stunned, "#13 过载运转 副钩子：约 6 秒后强制过热停顿");

        // #14 雷霆传导：暴击归零
        Game g14 = fresh(14);
        check(g14.players.get(0).crit <= 0f,
                "#14 雷霆传导 副钩子：暴击率归零（" + g14.players.get(0).crit + "）");

        // #15 星尘债务：开局 -50 星尘 + 商店 -25%
        Game g15 = fresh(15);
        Game g15b = fresh(-1);
        check(g15.coins <= g15b.coins - 50, "#15 星尘债务 主钩子：开局 -50 星尘（"
                + g15.coins + " vs " + g15b.coins + "）");
        // #15 副钩子：商店价格 -25%
        int priceCard = shopPriceSum(15), priceBase = shopPriceSum(-1);
        check(priceBase > 0 && priceCard < priceBase * .85f,
                "#15 星尘债务 副钩子：商店价格 -25%（" + priceCard + " vs " + priceBase + "）");

        // #16 玻璃大炮：伤害 +60% + 承伤 +50%
        Game g16 = ready(161L, 16);
        Game g16b = ready(161L, -1);
        check(g16.players.get(0).damage > g16b.players.get(0).damage * 1.3f,
                "#16 玻璃大炮 副钩子：伤害 +60%（" + g16.players.get(0).damage
                        + " vs " + g16b.players.get(0).damage + "）");

        // #17 孤注一掷：遗物效果 ×1.6 + 最多 2 种遗物
        Game g17 = ready(171L, 17);
        Game.Player p17 = g17.players.get(0);
        g17.grantRelic(p17, Relics.WIND_GEAR);
        g17.grantRelic(p17, Relics.VITAL);
        g17.grantRelic(p17, Relics.CRIT_LENS);
        check(relicKindsOf(g17) <= 2, "#17 孤注一掷 副钩子：不同种类遗物上限 2（实际 "
                + relicKindsOf(g17) + "）");
        // 主钩子：同样一件遗物带来的伤害**增量**必须恰好放大到 1.6 倍。
        // 用增量而不是绝对面板：WIND_GEAR 只有 +2% 伤害，绝对面板上的差异
        // 会被基础值淹没；而增量比是乘区的直接度量。
        float qGain = relicDamageGain(-1, Relics.WIND_GEAR);
        float rGain = relicDamageGain(17, Relics.WIND_GEAR);
        check(qGain > 0f && Math.abs(rGain / qGain - 1.6f) < .1f,
                "#17 孤注一掷 主钩子：遗物效果 ×1.6（增量 " + rGain + " vs " + qGain + "）");
        // 同一个乘区对高数值遗物同样成立
        float qBig = relicDamageGain(-1, Relics.ABYSS_CROWN);
        float rBig = relicDamageGain(17, Relics.ABYSS_CROWN);
        check(qBig > 0f && Math.abs(rBig / qBig - 1.6f) < .1f,
                "#17 孤注一掷：传说遗物同样 ×1.6（" + rBig + " vs " + qBig + "）");

        // #1 双重进化：进化门槛 +3（副钩子）
        Game g1 = ready(181L, 1);
        Game.Player p1 = g1.players.get(0);
        int t2 = Evolutions.index(p1.role, 2);
        // TIER2 还要求持有指定遗物，先给上，确保测的是"等级门槛"而不是遗物门槛。
        // 必须先置 level 再 grantRelic：grantRelic 会 recompute，而 maxHp/hp 会随 level 变。
        g1.grantRelic(p1, Evolutions.REQ_RELIC[t2]);
        g1.level = 9;                                  // 原门槛 8：没牌可进化，有牌要到 11
        check(!evolutionAvailable(g1, p1, t2),
                "#1 双重进化 副钩子：Lv9 时二阶进化被门槛 +3 挡住（需 Lv11）");
        g1.level = 11;
        check(evolutionAvailable(g1, p1, t2), "#1 双重进化：Lv11 时二阶进化开放");
        // 对照组：没有这张牌时 Lv9 就能进化
        Game g1b = ready(181L, -1);
        Game.Player p1b = g1b.players.get(0);
        g1b.grantRelic(p1b, Evolutions.REQ_RELIC[t2]);
        g1b.level = 9;
        check(evolutionAvailable(g1b, p1b, t2), "#1 对照：未装备时 Lv9 二阶进化就开放");
    }

    // ══════════════════════════════════════════════════════════════
    //  5. Codec v8：往返 + v7 迁移
    // ══════════════════════════════════════════════════════════════

    static void codecV8RoundTrip() throws Exception {
        Game g = ready(777L, 12);                       // #12 雷光开局
        g.doctrineBonusChoices = 3;
        g.doctrineKillClock = 7;
        Game.Player p = g.players.get(0);
        p.overheatClock = 2.5f;
        p.dashHaste = 1.1f;
        p.dashHasteMul = .6f;
        Game copy = Codec.decode(Codec.encode(g));
        check(copy.doctrine == 12, "v8 往返：doctrine 一致");
        check(copy.doctrineHook == g.doctrineHook, "v8 往返：主钩子一致");
        check(copy.doctrineSideHook == g.doctrineSideHook, "v8 往返：副钩子一致");
        check(Arrays.equals(copy.doctrineParams, g.doctrineParams), "v8 往返：4 个参数一致");
        check(copy.doctrineBonusChoices == 3 && copy.doctrineKillClock == 7,
                "v8 往返：升级累积/击杀计数一致");
        Game.Player q = copy.players.get(0);
        check(q.overheatClock == 2.5f && q.dashHaste == 1.1f && q.dashHasteMul == .6f,
                "v8 往返：玩家级过热/闪避增益一致");
        check(Arrays.equals(Codec.encode(g), Codec.encode(copy)), "v8 往返：字节可重编码一致");
    }

    /**
     * v7 → v8 迁移。
     *
     * <p>v8 新增的是<b>中段</b>字段（不是像 v6→v7 那样的尾部追加），所以不能用
     * "砍末尾 N 字节"的做法。这里用 {@link #encodeV7(Game)} 按 v7 的真实字节布局
     * 重新编码一份旧档——它和 {@link Codec#decode} 的 v7 路径严格对应，
     * 任何布局漂移都会让 decode 抛异常或报"trailing bytes"。
     */
    static void codecV7MigratesToNoDoctrine() throws Exception {
        Game g = ready(888L, 12);
        g.doctrineBonusChoices = 2;
        // 天赋牌是 v8 引入的，所以"当前版本 >= 8"是真正的契约；
        // 精确等号会随每一次后续升级（v9 加了难度状态）误报。
        check(Codec.VERSION >= 8 && Codec.VERSION_LEGACY >= 7,
                "Codec.VERSION >= 8 且 VERSION_LEGACY >= 7"
                        + "（当前 " + Codec.VERSION + "/" + Codec.VERSION_LEGACY + "）");

        byte[] v7 = encodeV7(g);
        check(readInt(v7, 4) == 7, "手工构造的旧档版本号为 7");

        Game m = Codec.decode(v7);
        check(m.doctrine == -1, "v7 迁移后 doctrine == -1（未装备）");
        check(m.doctrineHook == 0 && m.doctrineSideHook == 0, "v7 迁移后钩子 id == 0");
        check(m.doctrineParams[0] == 0f && m.doctrineParams[1] == 0f
                && m.doctrineParams[2] == 0f && m.doctrineParams[3] == 0f,
                "v7 迁移后参数全 0");
        check(m.doctrineBonusChoices == 0 && m.doctrineKillClock == 0,
                "v7 迁移后升级累积/击杀计数为 0");
        check(m.players.size() == g.players.size(), "v7 迁移保留玩家数");
        check(m.seed == g.seed && m.coins == g.coins && m.phase == g.phase,
                "v7 迁移保留种子/星尘/阶段");
        Game.Player q = m.players.get(0);
        check(q.overheatClock == 0f && q.overheatStun == 0f
                && q.dashHaste == 0f && q.dashHasteMul == 0f, "v7 迁移后玩家级增益为 0");
        check(Arrays.equals(q.relics, g.players.get(0).relics), "v7 迁移保留遗物表");
        check(Codec.encode(m).length > 0, "v7 迁移后的 Game 可再次编码");

        // 迁移后的档不能因为 roomTimeLimit 之类的派生字段被误判成"装备了牌"
        check(m.roomTimeLimit >= 0f && m.roomTimeLimit <= 1.01f, "v7 迁移后 roomTimeLimit 合法");
    }

    /**
     * 按 v7 的真实字节布局编码（= 当前 {@link Codec#encode} 去掉所有 v8 字段）。
     * <p>顺序必须与 {@code Codec.decode} 的 v7 分支一一对应，否则解码会抛异常。
     */
    static byte[] encodeV7(Game g) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(8192);
        DataOutputStream o = new DataOutputStream(b);
        o.writeInt(Codec.MAGIC);
        o.writeInt(7);                                  // VERSION_LEGACY
        o.writeLong(g.seed);
        o.writeLong(g.random.state());
        ints(o, g.phase, g.room, g.floor, g.level, g.kills, g.tick, g.spawned, g.target, g.route,
                g.captain, g.mode, g.roomType, g.biome, g.layout, g.hazard, g.roomsVisited,
                g.roomsTotal, g.routeIndex, g.eventType, g.eventChoice, g.shopRerolls, g.coins,
                g.bossesDefeated, g.routeCount, g.objective, g.rewardsLeft, g.rerolls, g.scouted,
                g.nextId, g.contract, g.mutator, g.wave, g.waves, g.nextWaveAt, g.roomStartKills,
                g.tier, g.freeReviveCount, g.freeRevivesUsed);
        // （v8 的 doctrine 组在这里不存在）
        o.writeBoolean(g.soloRun);
        o.writeBoolean(g.skipNextNode);
        o.writeBoolean(g.mirror);
        o.writeBoolean(g.campAfterBoss);
        o.writeBoolean(g.shrineUsed);
        ints(o, g.routeOptions);
        ints(o, g.shopOffers);
        ints(o, g.shopPrices);
        for (boolean s : g.shopSold) o.writeBoolean(s);
        ints(o, g.history);
        floats(o, g.time, g.roomTime, g.spawnClock, g.charge, g.xp, g.xpGoal, g.choiceClock,
                g.eventClock, g.comboClock, g.combo, g.xpTotal, g.dustEarned, g.marksEarned,
                g.waveWait, g.damageTaken);
        o.writeUTF(g.notice);
        o.writeUTF(g.sceneTitle);
        o.writeUTF(g.sceneHint);
        floats(o, g.schoolBonus);
        ints(o, g.bossKillsRun);

        o.writeInt(g.players.size());
        for (Game.Player p : g.players) {
            ints(o, p.id, p.role, p.kills, p.chosen, p.vote, p.dashSerial, p.skillSerial,
                    p.shotCount, p.synergyMask, p.weaponForm, p.evolution, p.evolutionsTaken,
                    p.ultSerial, p.ultCharge, p.multishot, p.pierce, p.slow, p.leech, p.explode,
                    p.armorBreak, p.soul, p.discount, p.bossLoot, p.thorns, p.execute,
                    p.knockback, p.knockbackPower, p.flags, p.extraProjectiles, p.chainCount,
                    p.orbitCount, p.mineCount, p.splitCount, p.ricochetCount, p.beamCount,
                    p.summonCount, p.shieldLayers, p.stunChance, p.reactions);
            ints(o, p.auraCount, p.slowFieldCount, p.lifeSteal, p.taunt,
                    p.chargeCount, p.splitShot, p.evoMechanic);
            o.writeUTF(p.name);
            o.writeBoolean(p.connected);
            o.writeBoolean(p.down);
            o.writeBoolean(p.nextCrit);
            floats(o, p.x, p.y, p.hp, p.maxHp, p.speed, p.damage, p.interval, p.attack, p.dx, p.dy,
                    p.ax, p.ay, p.manual, p.dashCd, p.skillCd, p.invuln, p.revive,
                    p.disconnectedFor, p.crit, p.magnet, p.xpBonus, p.dashFactor, p.buff,
                    p.buffKind, p.frostHealCd, p.ultCd, p.ultReady, p.reactionCd, p.leechBudget,
                    p.regenClock, p.shield, p.shieldMax, p.bonusPower, p.bonusRate, p.bonusArea,
                    p.bonusSpeed, p.bonusHp, p.bonusCrit, p.bonusMagnet, p.bonusRegen,
                    p.bonusXp, p.bonusCoin);
            // （v8 的 overheatClock/Stun/dashHaste/dashHasteMul 在这里不存在）
            ints(o, p.offers);
            ints(o, p.evolveOffer);
            ints(o, p.relics);
            for (boolean e : p.evolved) o.writeBoolean(e);
        }

        o.writeInt(g.enemies.size());
        for (Game.Enemy e : g.enemies) {
            ints(o, e.id, e.type, e.stage, e.variant, e.owner, e.affix, e.phaseTriggers,
                    e.behavior, e.bossIndex, e.summonClock, e.addCount);
            o.writeBoolean(e.elite);
            o.writeBoolean(e.summoned);
            o.writeBoolean(e.splitDone);
            floats(o, e.guard, e.chargeTime, e.chargeVx, e.chargeVy, e.x, e.y, e.hp, e.maxHp,
                    e.r, e.speed, e.cooldown, e.telegraph, e.tx, e.ty, e.slow, e.hit, e.burn,
                    e.burnClock, e.armor, e.blinkClock, e.healClock, e.shield, e.flash, e.stun);
        }

        o.writeInt(g.shots.size());
        for (Game.Shot s : g.shots) {
            ints(o, s.id, s.owner, s.pierce, s.slow, s.explode, s.lastHit, s.element, s.chain,
                    s.ricochet, s.homing, s.flags, s.splitCount, s.hitCount);
            ints(o, s.hits);
            o.writeBoolean(s.enemy);
            o.writeBoolean(s.crit);
            floats(o, s.x, s.y, s.vx, s.vy, s.damage, s.r, s.life, s.radius);
            // （v8 的 slowSeconds 在这里不存在）
        }

        o.writeInt(g.gems.size());
        for (Game.Gem gem : g.gems) { ints(o, gem.id, gem.kind); floats(o, gem.x, gem.y, gem.value); }

        o.writeInt(g.effects.size());
        for (Game.Effect e : g.effects) {
            ints(o, e.id, e.type, e.color);
            floats(o, e.x, e.y, e.r, e.life, e.maxLife, e.value);
        }

        o.writeInt(g.blocks.size());
        for (Game.Block block : g.blocks) {
            floats(o, block.x, block.y, block.w, block.h);
            o.writeInt(block.kind);
        }

        o.writeInt(g.props.size());
        for (Game.Prop p : g.props) {
            ints(o, p.id, p.type);
            floats(o, p.x, p.y, p.hp);
            o.writeBoolean(p.opened);
        }

        o.writeInt(g.hazards.size());
        for (Game.Hazard h : g.hazards) {
            ints(o, h.id, h.kind, h.owner);
            floats(o, h.x, h.y, h.r, h.clock, h.active);
        }

        o.writeInt(g.turrets.size());
        for (Game.Turret t : g.turrets) {
            ints(o, t.id, t.owner, t.kind);
            floats(o, t.x, t.y, t.life, t.cooldown);
        }

        o.writeInt(g.minions.size());
        for (Game.Minion m : g.minions) {
            ints(o, m.id, m.owner, m.kind);
            floats(o, m.x, m.y, m.life, m.cooldown, m.angle);
        }

        ints(o, g.pactMods, g.pactHeat);
        o.flush();
        return b.toByteArray();
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    static Game fresh(int card) {
        Game g = new Game(4242L);
        g.addPlayer(1, "t", 0);
        g.contract = 1;
        install(g, card);
        g.startSolo(1, Game.MODE_EXPEDITION);
        return g;
    }

    /** 进入第一间战斗房（装备牌）。 */
    static Game ready(long seed, int card) {
        Game g = new Game(seed);
        g.addPlayer(1, "t", 0);
        g.contract = 1;
        install(g, card);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.selectRoute(0);
        return g;
    }

    /** 与 {@link #ready} 相同；房间 0 就是战斗房。 */
    static Game fight(int card) {
        return ready(20_000L + card, card);
    }

    /** 把牌装进 Game（Session 做的就是这件事）。 */
    static void install(Game g, int card) {
        if (card < 0) {
            g.applyDoctrine(-1, 0, 0, 0, 0, 0);
            return;
        }
        g.applyDoctrine(card, CARD_HOOK[card], CARD_SIDE[card],
                CARD_P0[card], CARD_P1[card], CARD_P2[card]);
    }

    /** 刷新一次商店后的三件总价。 */
    static int shopPriceSum(int card) {
        Game g = ready(7L, card);
        call(g, "fillShop", new Class<?>[]{});
        int sum = 0;
        for (int k = 0; k < 3; k++) sum += Math.max(0, g.shopPrices[k]);
        return sum;
    }

    /** 打一场首领房清场后玩家的剩余生命（#9 每层自动回复的测量点）。 */
    static float bossClearHp(int card) {
        Game g = ready(9L, card);
        Game.Player p = g.players.get(0);
        p.hp = p.maxHp * .5f;
        g.roomType = Game.ROOM_BOSS;
        call(g, "clearRoom", new Class<?>[]{});
        return p.hp;
    }

    /** 反复刷新商店，看被锁流派（school）是否彻底不再出现。 */
    static boolean schoolLockedInShop(int card, int rounds) {
        Game g = ready(7L, card);
        boolean seen = false;
        for (int i = 0; i < rounds; i++) {
            call(g, "fillShop", new Class<?>[]{});
            for (int k = 0; k < 3; k++) {
                int id = g.shopOffers[k];
                if (id >= 0 && Relics.SCHOOL[id] == 3) seen = true;
            }
        }
        return !seen;
    }

    /** 玩家开火后第一枚自己的弹丸的伤害（用真实 update 路径产生）。 */
    static float firstShotDamage(int card) {
        Game g = ready(1L, card);
        Game.Player p = g.players.get(0);
        g.enemies.clear();
        spawnDummy(g, 9999, p.x + 200, p.y, 1e6f);
        p.nextCrit = false;
        p.crit = 0f;
        p.buff = 0f;
        for (int t = 0; t < 120; t++) {
            g.update(Game.STEP);
            for (Game.Shot s : g.shots) if (!s.enemy && s.owner == p.id) return s.damage;
        }
        return -1f;
    }

    /** 在场上放一个静止的木桩敌人。 */
    static Game.Enemy spawnDummy(Game g, int id, float x, float y, float hp) {
        Game.Enemy e = new Game.Enemy();
        e.id = id;
        e.type = 0;
        e.behavior = Game.B_CHASE;
        e.r = 16f;
        e.hp = e.maxHp = hp;
        e.speed = 0f;
        e.cooldown = 999f;
        e.stage = -1;
        e.x = x;
        e.y = y;
        g.enemies.add(e);
        return e;
    }

    /** 把敌人血量打到 0 并走一次真实伤害结算（触发击杀钩子）。 */
    static void killEnemy(Game g, Game.Enemy e, Game.Player by) {
        damage(g, e, e.maxHp * 10f + 1e6f, by.id);
    }

    /** 反射调用 private damage(Enemy, float, int)。 */
    static void damage(Game g, Game.Enemy e, float amount, int owner) {
        call(g, "damage", new Class<?>[]{Game.Enemy.class, float.class, int.class}, e, amount, owner);
    }

    /** 某件遗物在装备某张牌时带来的伤害增量（同种子、同职业）。 */
    static float relicDamageGain(int card, int relic) {
        Game g = ready(172L, card);
        Game.Player p = g.players.get(0);
        g.recompute(p);
        float before = p.damage;
        g.grantRelic(p, relic);
        g.recompute(p);
        return p.damage - before;
    }

    static int relicKindsOf(Game g) {
        Game.Player p = g.players.get(0);
        int n = 0;
        for (int i = 0; i < Relics.COUNT; i++) if (p.relics[i] > 0) n++;
        return n;
    }

    /**
     * 反射调用 private 方法。
     * <p>为什么用反射而不是把方法改成 public：硬约束是"公共方法签名不改，只新增"，
     * 而这些都是实现细节，不该为了测试扩公开 API。签名一旦被改，测试会在这里立刻炸掉。
     */
    static Object call(Game g, String name, Class<?>[] sig, Object... args) {
        try {
            java.lang.reflect.Method m = Game.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(g, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Game." + name + " 签名变了：" + e, e);
        }
    }

    /** 喂经验（gainXp 是 private，但它是 #4 副钩子唯一的触发点）。 */
    static void gainXp(Game g, float amount, Game.Player p) {
        call(g, "gainXp", new Class<?>[]{float.class, Game.Player.class}, amount, p);
    }

    /** 进入三选一（prepareChoice 是 private，但它是 #4 累积数的消费点）。 */
    static void prepareChoice(Game g, int rewards) {
        call(g, "prepareChoice", new Class<?>[]{int.class}, rewards);
    }

    /** 用反射读 EvolutionAvailable：它是 private，但它是 #1 门槛钩子唯一的消费点。 */
    static boolean evolutionAvailable(Game g, Game.Player p, int idx) {
        return (Boolean) call(g, "EvolutionAvailable",
                new Class<?>[]{Game.class, Game.Player.class, int.class}, g, p, idx);
    }

    /** 用反射读 #10 的反弹比例（private helper）。 */
    static float doctrineReflect(Game g) {
        return (Float) call(g, "doctrineReflectRatio", new Class<?>[]{});
    }

    /** #8 的敌人移速乘区（private helper）：用反射读，避免为了测试加公开 API。 */
    static float doctrineEnemySpeed(Game g) {
        return (Float) call(g, "doctrineEnemySpeedMul", new Class<?>[]{});
    }

    static boolean ne(float a, float b) { return Math.abs(a - b) > 1e-3f; }

    static void check(boolean ok, String name) {
        if (ok) {
            System.out.println("PASS " + name);
        } else {
            fails++;
            problems.add(name);
            System.out.println("FAIL " + name);
        }
    }

    static void ints(DataOutputStream o, int... v) throws IOException {
        for (int n : v) o.writeInt(n);
    }

    static void floats(DataOutputStream o, float... v) throws IOException {
        for (float n : v) o.writeFloat(n);
    }

    static int readInt(byte[] d, int off) {
        return ((d[off] & 0xff) << 24) | ((d[off + 1] & 0xff) << 16)
                | ((d[off + 2] & 0xff) << 8) | (d[off + 3] & 0xff);
    }
}
