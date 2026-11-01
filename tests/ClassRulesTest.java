import com.dilao.core.*;

/**
 * 职业遗物规则（{@code ClassRules}）回归测试。
 *
 * <p><b>为什么需要这个测试</b>：{@code ClassRules} 有 354 行、12 条规则，类注释里
 * 明确写着「{@code tests/ClassRulesTest.java} 会逐条验证」——但那个文件**根本不存在**，
 * 而且 {@code ClassRules} 本身全项目零调用。于是：
 * <ul>
 *   <li>12 条规则无论写对写错都没人知道；</li>
 *   <li>12 个职业看到的是同一个遗物池，构筑长得一模一样。</li>
 * </ul>
 * 本套补上承诺的验证，并顺带守住两条设计纪律：
 * 每个职业至少挡掉 5 件、至少保留 25 件合法；60 件里每一件至少对一个职业合法。
 */
public final class ClassRulesTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 职业遗物规则回归 ===");
        System.out.println();

        tableIntegrity();
        System.out.println();
        everyClassHasRules();
        System.out.println();
        everyRelicIsLegalSomewhere();
        System.out.println();
        weightsAreSane();
        System.out.println();
        wiredIntoGame();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    // ── 1. 表自身的完整性 ────────────────────────────────────────
    static void tableIntegrity() {
        check("TAG 长度 == 遗物总数", ClassRules.TAG.length == Relics.COUNT,
                ClassRules.TAG.length + " vs " + Relics.COUNT);
        boolean tagsValid = true;
        for (int i = 0; i < Relics.COUNT; i++) {
            int t = ClassRules.TAG[i];
            if (t <= 0 || (t & ~0x7F) != 0) tagsValid = false;   // 7 条主轴
        }
        check("每件遗物都至少归到一条主轴", tagsValid, "7 位掩码内且非零");
        check("规则数量与职业数一致", ClassRules.RULE_COUNT == Content.ROLE_COUNT,
                ClassRules.RULE_COUNT + " vs " + Content.ROLE_COUNT);
    }

    // ── 2. 每个职业的规则真的挡东西、也没把自己饿死 ──────────────
    static void everyClassHasRules() {
        boolean blockedOk = true, keptOk = true;
        StringBuilder blocked = new StringBuilder(), kept = new StringBuilder();
        for (int role = 0; role < ClassRules.RULE_COUNT; role++) {
            int legal = 0;
            for (int id = 0; id < Relics.COUNT; id++) if (ClassRules.allows(role, id)) legal++;
            int banned = Relics.COUNT - legal;
            kept.append(Content.ROLES[role]).append('=').append(legal).append(' ');
            if (legal < 25) { keptOk = false; }
            if (banned < 5) { blockedOk = false; blocked.append(Content.ROLES[role])
                    .append("(挡").append(banned).append(") "); }
        }
        check("每个职业至少挡住 5 件（否则规则只是文案）", blockedOk,
                blockedOk ? "12 职业全部达标" : "不达标：" + blocked);
        check("每个职业至少 25 件合法（不会把职业饿死）", keptOk, kept.toString());
    }

    // ── 3. 没有"人人都不许拿"的死遗物 ────────────────────────────
    static void everyRelicIsLegalSomewhere() {
        StringBuilder dead = new StringBuilder();
        for (int id = 0; id < Relics.COUNT; id++) {
            boolean any = false;
            for (int role = 0; role < ClassRules.RULE_COUNT; role++)
                if (ClassRules.allows(role, id)) { any = true; break; }
            if (!any) dead.append(Relics.NAMES[id]).append(' ');
        }
        check("60 件遗物每件至少对一个职业合法", dead.length() == 0,
                dead.length() == 0 ? "全部可达" : "不可达：" + dead);
    }

    // ── 4. 权重必须落在声明区间内 ────────────────────────────────
    static void weightsAreSane() {
        boolean ok = true;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int role = 0; role < ClassRules.RULE_COUNT; role++) {
            for (int id = 0; id < Relics.COUNT; id++) {
                float w = ClassRules.weightMul(role, id);
                if (!Float.isFinite(w) || w < ClassRules.MIN_W || w > ClassRules.MAX_W) ok = false;
                min = Math.min(min, w);
                max = Math.max(max, w);
            }
        }
        check("weightMul 恒落在 [MIN_W, MAX_W]", ok,
                String.format("实测 [%.2f, %.2f]，声明 [%.2f, %.2f]",
                        min, max, ClassRules.MIN_W, ClassRules.MAX_W));

        // 越界输入不得抛异常，且必须给出"无规则"的安全值。
        boolean safe = ClassRules.allows(-1, 0) && ClassRules.allows(99, 0)
                && !ClassRules.allows(0, -1) && !ClassRules.allows(0, Relics.COUNT)
                && ClassRules.weightMul(-1, 0) == 1f && ClassRules.weightMul(0, -1) == 1f;
        check("越界参数安全降级（不抛、不误伤）", safe, "role=-1/99、id=-1/COUNT");
    }

    // ── 5. 规则真的接进了 Game ───────────────────────────────────
    static void wiredIntoGame() {
        // 每个职业抽 400 次三选一，统计"拿到被本职业禁止的遗物"的比例。
        // 接入 ClassRules 之后，加权抽样会大幅压低这个比例；
        // 完全不接线时约为 1 - legal/60，接线后应显著更低。
        boolean allLower = true;
        StringBuilder sb = new StringBuilder();
        for (int role = 0; role < ClassRules.RULE_COUNT; role++) {
            int legal = ClassRules.legalCount(role);
            float baseline = 1f - legal / (float) Relics.COUNT;   // 完全随机时的违规率
            int drawn = 0, banned = 0;
            Game g = new Game(9000L + role);
            g.addPlayer(1, "t", role);
            g.contract = 0;
            g.startSolo(1, Game.MODE_EXPEDITION);
            g.phase = Game.FIGHT;
            Game.Player p = g.players.get(0);
            p.invuln = 1e9f;
            for (int i = 0; i < 400; i++) {
                int id = g.rollRelicForProbe(p);
                if (id < 0) continue;
                drawn++;
                if (!ClassRules.allows(role, id)) banned++;
            }
            float rate = drawn == 0 ? 1f : banned / (float) drawn;
            sb.append(Content.ROLES[role]).append(String.format("=%.0f%%/%.0f%% ",
                    rate * 100, baseline * 100));
            // 要求：违规率明显低于"完全不接线"的基线（至少降 40%）。
            if (rate > baseline * .6f) allLower = false;
        }
        check("12 职业的三选一都受职业规则约束", allLower,
                "实测违规率/完全不接线的违规率：" + sb);
    }
}
