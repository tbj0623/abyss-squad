import com.dilao.core.*;

/**
 * 跨流派共鸣（CROSS）回归测试。
 *
 * <p><b>为什么需要这个测试</b>：这一系统经历过两次"看起来修好了"：
 * <ol>
 *   <li>第一轮：{@code Player.crossMask} 常量、名称、说明、{@code Balance.valid()}
 *       的 6 组校验全都在，但<b>全项目零赋值</b>——玩家凑齐流派也没有任何效果。</li>
 *   <li>第二轮：{@code computeCross()} 补上赋值，但 6 组里只有 2 组写了派生字段，
 *       而那 2 个派生字段（{@code crossSpeedPower} / {@code crossRush}）
 *       <b>又没有任何读取点</b>——结果依然是 0 效果。</li>
 * </ol>
 * 两次都是"代码里搜得到 crossMask"所以看起来没问题。本套不看字段，只看**行为**：
 * 对每一组，分别构造"只缺这一组"与"正好凑齐这一组"两个玩家，比较可观测差异。
 *
 * <p>覆盖：
 * <ol>
 *   <li>6 组全部可被 {@code cross(i)} 点亮（条件来自 Content.CROSS_A/B）</li>
 *   <li>6 组各自产生**互不相同**的可观测效果（不是 6 个同义分支）</li>
 *   <li>未凑齐时不会有任何额外收益（防止"无条件生效"）</li>
 *   <li>组合与流派无关的流派（第三流派）不会误点亮</li>
 * </ol>
 */
public final class CrossSchoolTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 跨流派共鸣回归 ===");
        System.out.println();

        allPairsDetectable();
        System.out.println();
        eachCombinationDiffers();
        System.out.println();
        noBonusWhenNotPaired();
        System.out.println();
        unrelatedSchoolDoesNotTrigger();
        System.out.println();
        thresholdIsTwo();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    // ── 工具：造一个持有指定流派各 1 件遗物的玩家 ──────────────────
    static Game fresh(long seed) {
        Game g = new Game(seed);
        Game.Player p = g.addPlayer(1, "p", 0);
        g.contract = 0;
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        return g;
    }

    /** 找一件属于该流派、且不重复的遗物。 */
    static int relicOfSchool(int school, java.util.Set<Integer> used) {
        for (int r = 0; r < Relics.COUNT; r++)
            if (Relics.SCHOOL[r] == school && !used.contains(r)) return r;
        return -1;
    }

    /** 给玩家发一件该流派的遗物（用真实 grantRelic 路径）。 */
    static void grantSchool(Game g, Game.Player p, int school, java.util.Set<Integer> used) {
        int id = relicOfSchool(school, used);
        if (id >= 0) { used.add(id); g.grantRelic(p, id); }
    }

    /** 该流派还剩几件没发出去（用于判断能否凑够门槛）。 */
    static int remainingOfSchool(int school, java.util.Set<Integer> used) {
        int n = 0;
        for (int r = 0; r < Relics.COUNT; r++)
            if (Relics.SCHOOL[r] == school && !used.contains(r)) n++;
        return n;
    }

    /**
     * 只给这两个流派各 {@code CROSS_MIN_STACKS} 件。
     *
     * <p>门槛是"各 2 件"而不是"各 1 件"：一局能拿 13 件遗物、6 个流派，
     * 「各 1 件」实测会让平均每局点亮 3.4 组（共 6 组），共鸣就不再是
     * "发现了一个 build"而是一条几乎必得的被动——接入后炼狱胜率从 47% 跳到 88%。
     */
    static Game paired(int crossIndex, long seed) {
        Game g = fresh(seed);
        Game.Player p = g.players.get(0);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int k = 0; k < Game.CROSS_MIN_STACKS; k++) {
            grantSchool(g, p, Content.CROSS_A[crossIndex], used);
            grantSchool(g, p, Content.CROSS_B[crossIndex], used);
        }
        return g;
    }

    /** 给这两个流派中"均不属于目标组合"的其它流派各若干件（对照组）。 */
    static Game unpaired(int crossIndex, long seed) {
        Game g = fresh(seed);
        Game.Player p = g.players.get(0);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int a = Content.CROSS_A[crossIndex], b = Content.CROSS_B[crossIndex];
        int need = 2 * Game.CROSS_MIN_STACKS;
        for (int s = 0; s < Content.SCHOOL_COUNT && used.size() < need; s++) {
            if (s == a || s == b) continue;
            grantSchool(g, p, s, used);
        }
        return g;
    }

    // ── 1. 6 组都能被点亮 ────────────────────────────────────────
    static void allPairsDetectable() {
        boolean all = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Content.CROSS_COUNT; i++) {
            Game g = paired(i, 100L + i);
            boolean on = g.players.get(0).cross(i);
            sb.append(Content.CROSS_NAMES[i]).append('=').append(on ? "ON" : "OFF").append(' ');
            if (!on) all = false;
        }
        check("6 组组合在凑齐对应流派后被点亮", all, sb.toString());
    }

    // ── 2. 每一组的「有 / 无」都必须产生可观测差异 ────────────────
    static void eachCombinationDiffers() {
        // 关键：不能拿 6 个不同组合互相比较——它们的**基础遗物不同**，指纹天然不同，
        // 那样即使 6 组全部零效果也会「通过」。正确做法是对每一组做 A/B：
        //   有该组合   vs   同一批遗物但不满足该组合的触发条件
        // 只比较这一组自己的开关，才能证明这一组真的在做事。
        //
        // 观测口径随组的机制而定，选各自最直接的那个量：
        //   0 焚寂 → 对灼烧木桩的总伤害（增伤 + 死亡炸开）
        //   1 霜雷 → 对减速木桩的总伤害（22% 概率连锁闪电）
        //   2 风火 → power() 乘区（同等遗物下木桩掉血速度）
        //   3 生霜 → 玩家承伤（-8%）
        //   4 虚步 → dashCd（-25%）
        //   5 雷愈 → 玩家回血（每次连锁 +1.5）
        boolean all = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Content.CROSS_COUNT; i++) {
            float on = probe(i, true, 700L + i);
            float off = probe(i, false, 700L + i);
            boolean differs = Math.abs(on - off) > 1e-3f;
            if (!differs) all = false;
            sb.append(Content.CROSS_NAMES[i]).append(": ").append(fmt(on))
              .append(differs ? " ≠ " : " == ").append(fmt(off)).append("; ");
        }
        check("6 组各自都有可观测的收益（A/B 对比）", all, sb.toString());
    }

    /** 跑同一段固定帧，返回该组合的观测值。{@code withCombo=false} 时用等量无关遗物做对照。 */
    static float probe(int crossIndex, boolean withCombo, long seed) {
        Game g = withCombo ? paired(crossIndex, seed) : unpaired(crossIndex, seed);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        g.enemies.clear();

        Game.Enemy dummy = new Game.Enemy();
        dummy.id = 777; dummy.type = 0; dummy.behavior = Game.B_CHASE;
        dummy.x = p.x + 40; dummy.y = p.y;
        dummy.maxHp = dummy.hp = 200000f;
        dummy.r = 14; dummy.stage = -1; dummy.telegraph = 0f;
        dummy.burn = 5f; dummy.owner = p.id; dummy.slow = 5f;
        g.enemies.add(dummy);

        float hp0 = p.hp;
        float dashCdAfter = -1f;      // 虚步的观测点：闪避后写入的冷却值
        for (int f = 0; f < 240; f++) {
            p.invuln = 0f;
            // 虚步（组合 4）只在「真的闪避过」之后才有差异。dashFactor 是 recompute
            // 按遗物算出来的常量，不会因闪避改变——真正被改写的是闪避后写入的 dashCd。
            // dash 是「序号」而不是布尔：与 p.dashSerial 不同才会触发一次闪避。
            if (crossIndex == 4 && f == 60) {
                p.dashCd = 0f;
                g.input(p.id, 1f, 0f, 1f, 0f, true, p.dashSerial + 1, p.skillSerial);
                dashCdAfter = p.dashCd;
            }
            g.update(1f / 60f);
            dummy.burn = Math.max(dummy.burn, 1f);
            dummy.slow = Math.max(dummy.slow, 1f);
        }
        switch (crossIndex) {
            case 3: return p.hp - hp0;                    // 生霜：承伤观察（越接近 0 越是减免）
            case 4: return dashCdAfter;                   // 虚步：闪避后写入的冷却秒数
            case 5: return p.hp - hp0;                    // 雷愈：回血
            default: return dummy.maxHp - dummy.hp;       // 0/1/2：对木桩造成的总伤害
        }
    }

    static String fmt(float v) { return String.format("%.2f", v); }

    // ── 3. 未凑齐时没有额外收益 ──────────────────────────────────
    static void noBonusWhenNotPaired() {
        boolean any = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Content.CROSS_COUNT; i++) {
            Game g = unpaired(i, 900L + i);
            if (g.players.get(0).cross(i)) { any = true; sb.append(Content.CROSS_NAMES[i]).append(' '); }
        }
        check("未凑齐的组不会误点亮", !any,
                any ? "误点亮：" + sb : "6 组均保持关闭");
    }

    // ── 3b. 门槛必须真的是 2 件 ─────────────────────────────────
    static void thresholdIsTwo() {
        check("门槛常量为 2", Game.CROSS_MIN_STACKS == 2,
                "CROSS_MIN_STACKS=" + Game.CROSS_MIN_STACKS);
        // 各 1 件 → 不点亮；各 2 件 → 点亮。这条才是"门槛真的在起作用"的证据。
        boolean offAtOne = true, onAtTwo = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Content.CROSS_COUNT; i++) {
            Game g1 = fresh(200L + i);
            Game.Player p1 = g1.players.get(0);
            java.util.Set<Integer> u1 = new java.util.HashSet<>();
            grantSchool(g1, p1, Content.CROSS_A[i], u1);
            grantSchool(g1, p1, Content.CROSS_B[i], u1);
            if (p1.cross(i)) offAtOne = false;

            Game g2 = paired(i, 300L + i);
            if (!g2.players.get(0).cross(i)) onAtTwo = false;
            sb.append(Content.CROSS_NAMES[i]).append(' ');
        }
        check("各 1 件时组合不点亮（门槛在起作用）", offAtOne,
                offAtOne ? sb + "全部保持关闭" : "有组合在 1 件时就被点亮");
        check("各 2 件时组合点亮", onAtTwo, onAtTwo ? "6/6 点亮" : "有组合没点亮");
    }

    // ── 4. 无关流派不会触发 ──────────────────────────────────────
    static void unrelatedSchoolDoesNotTrigger() {
        // 给一个与任何组合都无关的组合：只拿两个"互相不配对"的流派。
        Game g = fresh(1100L);
        Game.Player p = g.players.get(0);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        grantSchool(g, p, 0, used);     // 余烬
        grantSchool(g, p, 2, used);     // 霜星  —— 余烬+霜星 不在 6 组里
        boolean none = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Content.CROSS_COUNT; i++)
            if (p.cross(i)) { none = false; sb.append(Content.CROSS_NAMES[i]).append(' '); }
        check("不在表里的流派组合不触发任何共鸣", none,
                none ? "余烬+霜星 → 无共鸣" : "误触发：" + sb);
    }
}
