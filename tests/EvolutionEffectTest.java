import com.dilao.core.*;

/**
 * 进化机制有效性回归 —— 验证 36 条进化「真的改变战斗结果」。
 *
 * <p><b>为什么需要这个测试</b>：进化系统经历过两轮"假修"：
 * <ol>
 *   <li>第一轮：36 条进化只有 FLAGS + 数值，{@code evoMechanic} 写了但从不被读，
 *       15 个机制原型全是死定义。</li>
 *   <li>第二轮：补上了 {@code applyMechanic} 的 14 个分派分支，但**没有任何测试**
 *       证明这些分支会产生可测量差异——一旦某个分支写错或漏接，编译和现有测试全绿。</li>
 * </ol>
 * 本套对 36 条进化逐条做 A/B：进化前 vs 进化后，跑同一段真实帧，比较战斗产出。
 * 只接受"有可测差异"的条目通过；纯数值进化用击杀/伤害衡量，机制进化用其机制特征量衡量。
 */
public final class EvolutionEffectTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 进化机制有效性（36 条 A/B）===");
        System.out.println();

        int[] dead = new int[Evolutions.COUNT];
        int deadCount = 0;
        int effective = 0;

        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            System.out.println("── " + Content.ROLES[role] + " ──");
            for (int line = 0; line < 3; line++) {
                int idx = Evolutions.index(role, line);
                float before = fingerprint(role, idx, false);
                float after = fingerprint(role, idx, true);
                boolean differs = Math.abs(after - before) > Math.max(1e-4f, Math.abs(before) * .01f);
                if (differs) effective++; else { dead[deadCount++] = idx; }
                System.out.printf("  %s %-10s mechanic=%2d  %s  before=%.2f after=%.2f%n",
                        differs ? "OK  " : "DEAD", Evolutions.NAMES[idx],
                        Evolutions.mechanic(idx), differs ? "≠" : "==", before, after);
            }
        }

        System.out.println();
        check("36 条进化全部产生可测差异", deadCount == 0,
                effective + "/" + Evolutions.COUNT + " 有效"
                        + (deadCount == 0 ? "" : "，无效：" + java.util.Arrays.toString(
                                java.util.Arrays.copyOf(dead, deadCount))));
        check("无机制的进化（mechanic=0）靠 FLAGS 也能产生差异",
                pureNumericStillWorks(), "逐条检查 mechanic=0 的条目");
        check("机制原型不重复堆叠（同职业 3 条不同原型）", prototypesDistinct(),
                Evolutions.valid() ? "valid()=true" : "valid()=false");

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /**
     * 同一 role+seed 下跑 6 秒固定战斗，返回总造成伤害。
     *
     * <p><b>关键约束：A/B 两边的房间、站位、木桩、随机序列必须完全一致</b>，
     * 否则测出来的是"两局不同的游戏"，不是"这条进化的效果"。
     * 具体做法：两边走同一条开局路径（同一 seed → 同一路线 → 同一房间），
     * 只在「进入战斗房之后」才应用进化；进化用反射直接调 {@code applyEvolution}，
     * 因为它不推进房间（{@code evolveWeapon} 会 advance()，把 A/B 打到不同房间里）。
     *
     * @param evolve 是否在开跑前解锁该条进化
     */
    static float fingerprint(int role, int evoIdx, boolean evolve) {
        Game g = new Game(20_000L + role * 37L + evoIdx);
        Game.Player p = g.addPlayer(1, "p", role);
        g.contract = 0;
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        return runWindow(g, p, evoIdx, evolve);
    }

    static java.lang.reflect.Method mApplyEvolution;

    static void applyEvolution(Game g, Game.Player p, int idx) {
        try {
            if (mApplyEvolution == null) {
                mApplyEvolution = Game.class.getDeclaredMethod(
                        "applyEvolution", Game.Player.class, int.class);
                mApplyEvolution.setAccessible(true);
            }
            mApplyEvolution.invoke(g, p, idx);
        } catch (Exception e) {
            throw new IllegalStateException("applyEvolution 反射失败", e);
        }
    }

    static float runWindow(Game g, Game.Player p, int evoIdx, boolean evolve) {
        // 1) 两边走完全相同的开局路径，进入第一个战斗房。
        for (int i = 0; i < 80 && g.phase != Game.FIGHT; i++) {
            if (g.phase == Game.MAP) g.selectRoute(0);
            else if (g.phase == Game.EVENT) { g.resolveEvent(1); g.continueEvent(); }
            else if (g.phase == Game.SHOP) g.leaveShop();
            else if (g.phase == Game.CAMP) g.camp(0);
            else if (g.phase == Game.ARMORY) g.evolveWeapon(0);
            else if (g.phase == Game.SHRINE) g.shrine(1);
            else if (g.phase == Game.CHOICE) g.choose(1, 0, 0);
            else g.update(1f / 60f);
        }
        if (g.phase != Game.FIGHT) return -1f;

        // 2) 到达战斗房之后才应用进化 —— 这样两边的房间/站位/刷怪完全一致。
        if (evolve) applyEvolution(g, p, evoIdx);
        p.invuln = 1e9f;

        // 3) 摆一组固定木桩。speed=0 且每帧重置位置，保证几何与命中完全可复现。
        g.enemies.clear();
        for (int i = 0; i < 8; i++) {
            Game.Enemy e = new Game.Enemy();
            e.id = 5000 + i;
            e.type = 0; e.behavior = Game.B_CHASE;
            e.x = 300 + (i % 4) * 140;
            e.y = 220 + (i / 4) * 120;
            e.maxHp = e.hp = 100000f;
            e.r = 16; e.stage = -1;
            e.speed = 0; e.telegraph = 0f;
            g.enemies.add(e);
        }

        // 4) 状态类机制需要前置状态才可能触发：冰系（idx 10/11）要求目标已被冻/冰缓，
        //    灼烧系（idx 12/27）要求目标已被点燃，雷铳/虚空之类要求穿透。
        //    这不是"产品没实现"，而是"测试场景没覆盖"。给木桩常驻所需的减益，
        //    让 A/B 两边都有触发条件——否则会把实现正确的机制误判成死代码
        //    （本套第一版就是这么误报了 9 条）。
        float total = 0f;
        for (int f = 0; f < 360; f++) {     // 6 秒
            p.invuln = 1e9f;
            p.x = 560; p.y = 420;
            for (int i = 0; i < g.enemies.size() && i < 8; i++) {
                Game.Enemy e = g.enemies.get(i);
                e.x = 300 + (i % 4) * 140;  // 冻结木桩位置（击退/位移类效果会移动它们）
                e.y = 220 + (i / 4) * 120;
                e.speed = 0f;
                // 常驻前置状态：既覆盖冰系也覆盖燃烧系；不影响纯伤害类进化的对比。
                e.slow = Math.max(e.slow, 3f);
                e.burn = Math.max(e.burn, 3f);
                if (e.owner < 0) e.owner = p.id;
            }
            g.update(1f / 60f);
            for (Game.Enemy e : g.enemies) total += 100000f - e.hp;
            for (Game.Enemy e : g.enemies) e.hp = 100000f;
        }
        return total;
    }

    /** mechanic=0 的条目只有 FLAGS + 数值，必须仍能造成差异（否则就是"纯面板"）。 */
    static boolean pureNumericStillWorks() {
        boolean ok = true;
        for (int idx = 0; idx < Evolutions.COUNT; idx++) {
            if (Evolutions.mechanic(idx) != 0) continue;
            int role = idx / 3;
            float before = fingerprint(role, idx, false);
            float after = fingerprint(role, idx, true);
            if (Math.abs(after - before) <= Math.max(1e-4f, Math.abs(before) * .01f)) {
                System.out.println("    mechanic=0 但无差异：" + Evolutions.NAMES[idx]);
                ok = false;
            }
        }
        return ok;
    }

    /** 每个职业的 3 条进化不应共享同一个机制原型。 */
    static boolean prototypesDistinct() {
        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            int a = Evolutions.mechanic(role * 3);
            int b = Evolutions.mechanic(role * 3 + 1);
            int c = Evolutions.mechanic(role * 3 + 2);
            // 允许 0（纯 FLAGS），但两个非 0 原型不能相同
            if (a != 0 && a == b) return false;
            if (b != 0 && b == c) return false;
            if (a != 0 && a == c) return false;
        }
        return true;
    }
}
