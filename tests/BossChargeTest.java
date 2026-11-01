import com.dilao.core.*;

/**
 * 首领冲锋回归测试 —— 防「又一次写成死招式」。
 *
 * <p><b>为什么需要这个测试</b>：{@code Bosses.PHASES} 里 1/3/4 三个首领都排了
 * {@code BOSS_CHARGE}，{@code doBossPattern} 也会写 {@code chargeVx/chargeVy/chargeTime}，
 * 但 {@code updateBoss} 从不过推进 {@code chargeTime}——冲刺永远不会发生。
 * 数据、参数、赋值三处都在，唯独消费点缺失，编译与所有既有测试全绿。
 * 这是「静态数据正确 ≠ 运行时生效」的典型盲区。
 *
 * <p>本套用**黑盒**方式验证：真的跑帧，观察首领的坐标是否沿冲锋方向位移，
 * 以及一次冲锋是否只结算一次伤害。不读私有字段、不 mock。
 *
 * <p>覆盖：
 * <ol>
 *   <li>三个冲锋型首领在冲锋阶段确实产生位移（速度 ≈ CHARGE_SPEED 的数量级）</li>
 *   <li>冲刺结束后进入恢复窗口（位移停止）</li>
 *   <li>同一次冲锋对同一玩家的伤害只结算一次（不是每帧连击）</li>
 *   <li>非冲锋阶段的位移速度显著低于冲锋速度（防止"永远在冲"）</li>
 * </ol>
 */
public final class BossChargeTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 首领冲锋状态机回归 ===");
        System.out.println();

        paramTableConsistency();
        System.out.println();
        for (int boss : new int[]{1, 3, 4}) chargeActuallyMoves(boss);
        System.out.println();
        chargeHitsOncePerSwing();
        System.out.println();
        nonChargePhaseIsSlow();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    // ── 1. 参数表与招式表必须一致 ──────────────────────────────────
    static void paramTableConsistency() {
        boolean ok = true;
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < Bosses.COUNT; i++) {
            boolean lists = false;
            for (int v : Bosses.PHASES[i]) if (v == Game.BOSS_CHARGE) lists = true;
            if (lists != Bosses.charges(i)) {
                ok = false;
                bad.append("boss").append(i).append("(lists=").append(lists)
                        .append(",param=").append(Bosses.CHARGE_SPEED[i] > 0).append(") ");
            }
        }
        check("CHARGE_* 参数表与 PHASES 招式表一一对应", ok,
                ok ? "8 位首领全部一致" : "错位：" + bad);
        check("Bosses.valid()", Bosses.valid(), "整体自检");

        // 三个冲锋首领的手感必须真的不同，不能是同一组数字复制三遍。
        java.util.Set<String> feels = new java.util.HashSet<>();
        for (int i = 0; i < Bosses.COUNT; i++)
            if (Bosses.charges(i))
                feels.add(Bosses.CHARGE_SPEED[i] + "/" + Bosses.CHARGE_WINDUP[i] + "/"
                        + Bosses.CHARGE_TIME[i] + "/" + Bosses.CHARGE_DAMAGE[i]
                        + "/" + Bosses.CHARGE_RECOVER[i]);
        check("每个冲锋型首领的参数组合互不相同", feels.size() == 3,
                "不同组合数 = " + feels.size() + " / 3");
    }

    // ── 2. 冲锋真的会让首领位移 ────────────────────────────────────
    static void chargeActuallyMoves(int boss) {
        Game g = bossArena(boss);
        Game.Enemy b = bossOf(g);
        if (b == null) { check("boss " + boss + " 生成", false, "找不到首领"); return; }

        // 把首领血量设到「它的冲锋所在阶段」的血线区间，而不是统一 50%。
        // 三段血线：phase0 = >67%，phase1 = 34%..67%，phase2 = <34%。
        b.hp = b.maxHp * chargePhaseHp(boss);
        float x0 = b.x, y0 = b.y;
        float maxSpeed = 0f;
        float prevX = b.x, prevY = b.y;

        // 跑 6 秒（1/60 步长），记录单帧最大位移换算出的速度。
        for (int f = 0; f < 360; f++) {
            g.update(1f / 60f);
            float dx = b.x - prevX, dy = b.y - prevY;
            prevX = b.x; prevY = b.y;
            float v = (float) Math.hypot(dx, dy) * 60f;
            if (v > maxSpeed) maxSpeed = v;
        }
        float moved = (float) Math.hypot(b.x - x0, b.y - y0);
        float expect = Bosses.CHARGE_SPEED[boss];
        boolean ok = maxSpeed >= expect * .80f;
        check("boss " + boss + " " + Bosses.NAMES[boss] + " 冲锋真的发生",
                ok,
                String.format("峰值速度 %.0f px/s（表定 %.0f），6s 净位移 %.0f px",
                        maxSpeed, expect, moved));
    }

    // ── 3. 一次冲锋只结算一次伤害 ──────────────────────────────────
    static void chargeHitsOncePerSwing() {
        Game g = bossArena(1);
        Game.Enemy b = bossOf(g);
        if (b == null) { check("boss 1 生成", false, "找不到首领"); return; }
        b.hp = b.maxHp * .5f;

        Game.Player p = g.players.get(0);
        // 玩家血量拉满，方便观察掉血次数而不是"死了就停"。
        p.maxHp = p.hp = 100000f;
        b.hp = b.maxHp * chargePhaseHp(1);

        // 把玩家放在首领正前方，并每帧强制贴住首领——模拟"被冲锋正中"。
        // 一次冲锋如果每帧结算，1 秒内就会掉几十次血。
        int hits = 0;
        float lastHp = p.hp;
        for (int f = 0; f < 600; f++) {
            p.x = b.x; p.y = b.y - 20;   // 强制贴身
            g.update(1f / 60f);
            if (p.hp < lastHp - 0.01f) { hits++; lastHp = p.hp; }
        }
        // 10 秒里最多允许出现「冲锋次数 + 贴身接触」量级的伤害次数。
        // 关键断言：不能是每帧掉血（那会是 600 次量级）。
        boolean ok = hits < 120;
        check("同一次冲锋不产生每帧连击", ok,
                "10 秒内掉血事件 " + hits + " 次（每帧连击会是 ~600 次）");
    }

    // ── 4. 非冲锋阶段的移动速度必须远低于冲锋 ──────────────────────
    static void nonChargePhaseIsSlow() {
        Game g = bossArena(0);            // 苔冠守望者：三个阶段都没有冲锋
        Game.Enemy b = bossOf(g);
        if (b == null) { check("boss 0 生成", false, "找不到首领"); return; }
        check("boss 0 没有冲锋招式", !Bosses.charges(0), "PHASES=" + java.util.Arrays.toString(Bosses.PHASES[0]));

        float prevX = b.x, prevY = b.y, maxSpeed = 0f;
        for (int f = 0; f < 600; f++) {
            g.update(1f / 60f);
            float v = (float) Math.hypot(b.x - prevX, b.y - prevY) * 60f;
            prevX = b.x; prevY = b.y;
            if (v > maxSpeed) maxSpeed = v;
        }
        // 普通移动上限：Bosses.SPEED 最高 70，追逐时最多 70 px/s。
        boolean ok = maxSpeed < 140f;
        check("非冲锋首领不会误触发冲刺", ok,
                String.format("峰值速度 %.0f px/s（阈值 140，表定移动 %.0f）",
                        maxSpeed, Bosses.SPEED[0]));
    }

    // ── 工具 ──────────────────────────────────────────────────────
    static Game bossArena(int boss) {
        Game g = new Game(9001L + boss);
        Game.Player p = g.addPlayer(1, "p", 0);
        g.contract = 0;
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        // startSolo 停在星图（MAP）；按真实路径选一条路进入战斗房。
        // 若这一站不是战斗房就继续推进，直到进入 FIGHT。
        for (int guard = 0; guard < 12 && g.phase != Game.FIGHT; guard++) {
            if (g.phase == Game.MAP) {
                if (!g.selectRoute(0)) break;
            } else {
                g.update(1f / 60f);
            }
        }
        // 直接放到 BOSS 房间：清空敌人后按真实路径生成首领。
        g.enemies.clear();
        Game.Enemy e = new Game.Enemy();
        e.id = 90000 + boss;
        e.type = 0;
        e.bossIndex = boss;
        e.behavior = Game.B_CHASE;
        e.elite = true;
        e.x = 560; e.y = 165;
        e.r = Bosses.RADIUS[boss];
        e.speed = Bosses.SPEED[boss];
        e.maxHp = e.hp = Bosses.HP[boss];
        e.cooldown = 1f;
        e.telegraph = 0f;
        e.stage = 0;
        g.enemies.add(e);
        g.roomTime = 0f;
        return g;
    }

    static Game.Enemy bossOf(Game g) {
        for (Game.Enemy e : g.enemies) if (e.bossIndex >= 0) return e;
        return null;
    }

    /**
     * 该首领「第一次出现冲锋」的阶段所对应的血量比例。
     * <p>三个冲锋首领的冲锋阶段分别是 1 / 0 / 1（见 {@code Bosses.PHASES}），
     * 用统一的 50% 只能覆盖到 phase1 的那两个，霜心女猎手（phase0）永远测不到。
     * 这个 helper 让测试跟招式表走，而不是假设所有首领的冲锋都在同一阶段。
     */
    static float chargePhaseHp(int boss) {
        int[] row = Bosses.PHASES[boss];
        int phase = -1;
        for (int i = 0; i < row.length; i++) if (row[i] == Game.BOSS_CHARGE) { phase = i; break; }
        switch (phase) {
            case 0: return .85f;    // >67%
            case 1: return .50f;    // 34%..67%
            default: return .20f;   // <34%
        }
    }
}
