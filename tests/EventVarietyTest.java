import com.dilao.core.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 36 个事件「真的不一样」的回归测试。
 *
 * <p><b>为什么需要这个测试</b>：{@code Events} 曾经有 36 条完整文案，但
 * {@code Game.resolveEvent} 只按 8 个 TYPE 分支，同 TYPE 效果逐字节相同。
 * 更糟的是 32/36 个事件的 {@code COST_A}/{@code COST_B} 都是 0——
 * 也就是说"36 个事件"实际只有 8 种玩法、5 种有代价。文案在骗玩家。
 *
 * <p>本测试用<b>黑盒</b>方式（只调 {@code resolveEvent} + 读公开字段）验证：
 * <ol>
 *   <li><b>效果指纹两两不同</b>：指纹 = TYPE + COST_A + COST_B + 两个分支各自产生的
 *       状态变化向量。任意两个事件的指纹相同即失败。</li>
 *   <li><b>每个 TYPE ≥2 个事件</b>，且同 TYPE 内跑完 {@code resolveEvent} 后的
 *       状态变化向量必须不同——否则就是换皮。</li>
 *   <li><b>至少 20 个事件有非零代价</b>（COST_A + COST_B &gt; 0），否则"有代价的选择"
 *       只是文案。</li>
 *   <li><b>延迟结算类（TYPE 4/8/9）真的会结算</b>：选完之后推进 3 站，
 *       断言代价确实落了地，而不是挂在字段上没人读。</li>
 *   <li><b>确定性</b>：同种子同输入 → 同结果（随机走 {@code Game.random}）。</li>
 * </ol>
 *
 * <p><b>不做什么</b>：不改 {@code Codec}。事件挂账字段（{@code eventDebt} /
 * {@code delayedKind} / {@code delayedRooms} / {@code delayedAmount}）目前<b>没有</b>
 * 序列化，本测试因此显式断言"这是已知缺口"，避免它被误当成已修复。
 *
 * <p>用法：
 * <pre>
 *   javac -encoding UTF-8 -cp build\core-tests -d build\evt-var tests\EventVarietyTest.java
 *   java -ea -cp "build\core-tests;build\evt-var" EventVarietyTest
 * </pre>
 * 退出码：0 全部通过；1 存在失败。
 */
public final class EventVarietyTest {

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    /** 每个事件至少要覆盖几个真实状态维度：血量 / 上限 / 伤害 / 星尘 / 遗物 / 重掷 / 挂账。 */
    private static final String[] VECTOR_NAMES = {
            "hp", "maxHp", "damage", "crit", "speed", "coins", "relics", "scouted",
            "debt", "delayKind", "delayRooms", "delayAmount", "cursed"
    };

    public static void main(String[] args) {
        System.out.println("=== 事件多样性测试：36 个事件 = 36 种决策 ===");
        System.out.println();

        structuralChecks();
        fingerprintUniqueness();
        sameTypeDifferentOutcomes();
        costReality();
        delayedSettlement();
        determinism();
        codecGapIsDeclared();

        System.out.println();
        System.out.println("──────────────────────────────────────────");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.out.println(failed + " FAILURES:");
            for (String f : failures) System.out.println("  · " + f);
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 结构：每个 TYPE 至少 2 个，参数列长度正确
    // ══════════════════════════════════════════════════════════════

    private static void structuralChecks() {
        System.out.println("── 1. 结构（13 种选择结构 · 每类 ≥2 个事件）──");
        check("Events.valid() 通过", Events.valid(), "见 stderr");

        check("PARAM_A/B/C 长度 == COUNT",
                Events.PARAM_A.length == Events.COUNT
                        && Events.PARAM_B.length == Events.COUNT
                        && Events.PARAM_C.length == Events.COUNT,
                Events.PARAM_A.length + "/" + Events.PARAM_B.length + "/" + Events.PARAM_C.length);

        check("IMPLEMENTED_TYPE_MAX == 12（13 种结构）",
                Events.IMPLEMENTED_TYPE_MAX == 12, "实际 " + Events.IMPLEMENTED_TYPE_MAX);

        int typesWithEvents = 0;
        for (int t = 0; t <= Events.IMPLEMENTED_TYPE_MAX; t++) {
            int n = Events.countOfType(t);
            check("TYPE " + t + " " + Events.typeName(t) + " 有 " + n + " 个事件（≥2）",
                    n >= 2, "只有 " + n + " 个");
            if (n > 0) typesWithEvents++;
        }
        check("13 种结构全部被使用", typesWithEvents == 13, "实际 " + typesWithEvents);

        // 同 TYPE 内 PARAM_A 不能全部相同（Events.valid() 也查，这里给出可读明细）
        for (int t = 0; t <= Events.IMPLEMENTED_TYPE_MAX; t++) {
            List<Integer> ids = eventsOfType(t);
            boolean allSame = true;
            for (int i = 1; i < ids.size(); i++)
                if (Events.PARAM_A[ids.get(i)] != Events.PARAM_A[ids.get(0)]) allSame = false;
            check("TYPE " + t + " 内 PARAM_A 有区分度", !allSame,
                    "全部 = " + (ids.isEmpty() ? "?" : Events.PARAM_A[ids.get(0)]));
        }
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 36 个事件两两指纹不同
    // ══════════════════════════════════════════════════════════════

    private static void fingerprintUniqueness() {
        System.out.println("── 2. 效果指纹：36 个事件两两不同 ──");

        String[] prints = new String[Events.COUNT];
        Map<String, Integer> firstOwner = new LinkedHashMap<>();
        int dup = 0;

        for (int ev = 0; ev < Events.COUNT; ev++) {
            prints[ev] = fingerprint(ev);
            Integer prev = firstOwner.putIfAbsent(prints[ev], ev);
            if (prev != null) {
                dup++;
                fail("事件 #" + prev + "（" + Events.NAMES[prev] + "）与 #" + ev
                        + "（" + Events.NAMES[ev] + "）效果指纹完全相同");
            }
        }
        check("36 个事件效果指纹两两不同（重复 " + dup + " 对）", dup == 0,
                "有 " + dup + " 对事件在任何 seed 上都会给出相同结果");

        // 打印一行"差异度"摘要：不同指纹数 / 总事件数
        int distinct = firstOwner.size();
        check("不同指纹数 == 36", distinct == Events.COUNT, "实际 " + distinct);
        System.out.println("     实测：36 个事件 → " + distinct + " 个不同指纹");
        System.out.println();
    }

    /**
     * 计算某事件的效果指纹。
     *
     * <p>为了不让"随机结果"把两个本质相同的事件伪装成不同，这里对每个分支
     * 跑 {@value #FINGERPRINT_SEEDS} 个种子并把结果拼接成一个向量：
     * 若两个事件在<b>所有</b>种子上的状态变化都一样，才算同一个指纹。
     */
    private static final int FINGERPRINT_SEEDS = 3;

    private static String fingerprint(int ev) {
        StringBuilder sb = new StringBuilder();
        sb.append("T").append(Events.TYPE[ev])
                .append("|A").append(Events.COST_A[ev])
                .append("|B").append(Events.COST_B[ev])
                .append("|").append(Arrays.toString(new float[]{
                        Events.PARAM_A[ev], Events.PARAM_B[ev], Events.PARAM_C[ev]}));
        for (int choice = 0; choice < 2; choice++) {
            sb.append("|c").append(choice);
            for (int seed = 0; seed < FINGERPRINT_SEEDS; seed++)
                sb.append(changeVector(ev, choice, seed).shortString());
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 同 TYPE 内事件跑完后的状态变化必须不同
    // ══════════════════════════════════════════════════════════════

    private static void sameTypeDifferentOutcomes() {
        System.out.println("── 3. 同 TYPE 内的事件产生不同状态变化 ──");
        for (int t = 0; t <= Events.IMPLEMENTED_TYPE_MAX; t++) {
            List<Integer> ids = eventsOfType(t);
            boolean ok = true;
            String detail = "";
            outer:
            for (int a = 0; a < ids.size(); a++) {
                for (int b = a + 1; b < ids.size(); b++) {
                    String va = branchVector(ids.get(a));
                    String vb = branchVector(ids.get(b));
                    if (va.equals(vb)) {
                        ok = false;
                        detail = "#" + ids.get(a) + " 与 #" + ids.get(b) + " 完全一致";
                        break outer;
                    }
                }
            }
            check("TYPE " + t + " " + Events.typeName(t) + "（" + ids.size()
                    + " 个事件）状态变化互不相同", ok, detail);
        }
        System.out.println();
    }

    /** 一个事件两个分支的联合向量（用于同 TYPE 比较）。 */
    private static String branchVector(int ev) {
        return changeVector(ev, 0, 0).shortString() + changeVector(ev, 1, 0).shortString();
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 代价必须真实
    // ══════════════════════════════════════════════════════════════

    private static void costReality() {
        System.out.println("── 4. 代价必须真实（≥20 个事件有非零预付星尘）──");
        int paid = 0;
        for (int i = 0; i < Events.COUNT; i++)
            if (Events.COST_A[i] + Events.COST_B[i] > 0) paid++;
        check("有预付星尘代价的事件 ≥ 20", paid >= 20, "实际 " + paid + " / " + Events.COUNT);
        System.out.println("     实测：" + paid + " / " + Events.COUNT + " 个事件收预付星尘");

        // 0 星尘玩家不能被卡死：每个事件至少有一个分支付得起
        int stuck = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            if (!canAfford(ev, 0, 0) && !canAfford(ev, 1, 0)) stuck++;
        }
        check("0 星尘时没有任何事件把玩家卡死", stuck == 0, stuck + " 个事件两个分支都付不起");

        // 生命代价也必须真实落地：TYPE 0/3/12 的 A 选项必须扣血（按当前生命）
        int hpCost = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            int t = Events.TYPE[ev];
            if (t != 0 && t != 3 && t != 12) continue;
            Change c = changeVector(ev, 0, 0);
            if (c.hp < -0.5f) hpCost++;
        }
        check("献血/祭坛/反向代价的 A 选项真的扣血（" + hpCost + " 个）", hpCost >= 5,
                "只有 " + hpCost + " 个事件扣了血");

        // 拒绝也不是永远免费：至少 8 个事件的 B 选项有代价（掉血或掉星尘）
        int bCost = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            Change c = changeVector(ev, 1, 0);
            if (c.hp < -0.5f || c.coins < 0 || c.relicsLost) bCost++;
        }
        check("B 选项也常带代价（" + bCost + " 个事件）", bCost >= 8,
                "只有 " + bCost + " 个事件的 B 不是纯白给");
        System.out.println();
    }

    private static boolean canAfford(int ev, int choice, int coins) {
        Game g = freshGame(7000L + ev);
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        g.coins = coins;
        return g.canEvent(choice);
    }

    // ══════════════════════════════════════════════════════════════
    //  5. 延迟结算：选完之后推进 3 站，代价真的落地
    // ══════════════════════════════════════════════════════════════

    private static void delayedSettlement() {
        System.out.println("── 5. 延迟结算（TYPE 4 / 8 / 9）真的会在 3 站后结算 ──");

        int delayedEvents = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            int t = Events.TYPE[ev];
            if (t != 4 && t != 8 && t != 9) continue;
            delayedEvents++;
            probeDelayed(ev, 0);
        }
        check("延迟结算类事件存在（" + delayedEvents + " 个）", delayedEvents >= 6,
                "只有 " + delayedEvents + " 个");
        System.out.println();
    }

    /**
     * 单个延迟结算事件的完整生命周期验证。
     *
     * <p><b>测法（差分对照）</b>：同一 seed 跑两局，逐站驱动到完全相同的进度。
     * 唯一差别是：A 局保留挂账，B 局在事件结算后把挂账字段清零（模拟"代价不存在"）。
     * 两局的 hp / 星尘差就是挂账<b>真实造成</b>的代价。
     *
     * <p>为什么不用"扣血前后相减"：战斗房清场会回星尘、升级会回血，
     * 单看差值分不清是挂账扣的还是别处补的。差分对照能把这两者完全抵消。
     *
     * <p>验证四点：
     * <ol>
     *   <li>选完之后 {@code delayedKind != NONE} 且 {@code delayedRooms == 3}</li>
     *   <li>推进 2 站后仍在途；推进第 3 站后清零</li>
     *   <li>3 站结束时 A 局比 B 局少掉的血 / 星尘 ≈ 挂账量</li>
     *   <li>挂账不会把玩家扣死、也不会扣成负星尘</li>
     * </ol>
     */
    private static void probeDelayed(int ev, int choice) {
        Step withDelay = driveStations(ev, choice, 2, false);
        if (!withDelay.scheduled) {
            fail("事件 #" + ev + "（TYPE " + Events.TYPE[ev] + "）A 分支没有挂账");
            return;
        }
        check("事件 #" + ev + " 挂账 3 站后结算（kind=" + withDelay.kind + "）",
                withDelay.rooms == 3, "delayedRooms=" + withDelay.rooms);
        check("事件 #" + ev + " 的挂账在第 2 站后仍在途", withDelay.pending,
                "delayedKind=" + withDelay.kindAfter + " rooms=" + withDelay.roomsAfter);

        Step a = driveStations(ev, choice, 3, false);
        Step b = driveStations(ev, choice, 3, true);
        check("事件 #" + ev + " 的挂账在第 3 站后清零", !a.pending,
                "delayedKind=" + a.kindAfter + " rooms=" + a.roomsAfter);

        float hpDiff = b.hp - a.hp;
        int coinDiff = b.coins - a.coins;
        if (withDelay.kind == Game.DELAY_HP) {
            float expect = withDelay.expectHp;
            check("事件 #" + ev + " 第 3 站真的扣了 " + round(expect) + " 生命（差分 "
                            + round(hpDiff) + "）",
                    hpDiff > 0.5f && Math.abs(hpDiff - expect) <= Math.max(1f, expect * .05f),
                    "差分 " + hpDiff + "，预期 " + expect);
        } else if (withDelay.kind == Game.DELAY_COINS) {
            int expect = withDelay.expectCoins;
            check("事件 #" + ev + " 第 3 站真的扣了 " + expect + " 星尘（差分 " + coinDiff + "）",
                    coinDiff == expect, "差分 " + coinDiff + "，预期 " + expect);
        }

        check("事件 #" + ev + " 挂账结算不会扣死玩家 / 扣成负星尘",
                a.hp >= 1f && a.coins >= 0, "hp=" + a.hp + " coins=" + a.coins);
    }

    /** 一次"推进 N 站"的观测结果。 */
    static final class Step {
        boolean scheduled, pending;
        int kind, rooms, kindAfter, roomsAfter, expectCoins, coins;
        float expectHp, hp;
    }

    /**
     * 同种子跑一局：结算事件 → 推进 {@code stations} 站。
     *
     * @param suppressDelay true 时在事件结算后抹掉挂账，作为"代价不存在"的对照组
     */
    private static Step driveStations(int ev, int choice, int stations, boolean suppressDelay) {
        Step out = new Step();
        Game g = freshGame(8100L + ev * 10 + choice);
        g.roomsTotal = 999;
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        g.coins = 800;
        Game.Player p = g.players.get(0);
        p.hp = p.maxHp;                       // 满血，扣血一定看得见

        if (!g.resolveEvent(choice)) {
            fail("事件 #" + ev + " 分支 " + choice + " 被拒绝，无法验证延迟结算");
            return out;
        }
        out.scheduled = g.delayedKind != Game.DELAY_NONE && g.delayedRooms > 0;
        out.kind = g.delayedKind;
        out.rooms = g.delayedRooms;
        out.expectHp = p.maxHp * g.delayedAmount;
        out.expectCoins = Math.round(g.delayedAmount);
        if (suppressDelay) {
            g.delayedKind = Game.DELAY_NONE;
            g.delayedRooms = 0;
            g.delayedAmount = 0f;
        }

        int start = g.roomsVisited;
        for (int i = 0; i < stations; i++) advanceOneStation(g);
        out.pending = g.delayedKind != Game.DELAY_NONE && g.delayedRooms > 0;
        out.kindAfter = g.delayedKind;
        out.roomsAfter = g.delayedRooms;
        out.hp = p.hp;
        out.coins = g.coins;
        if (g.roomsVisited - start != stations) {
            fail("事件 #" + ev + " 的推进器偏离预期：推进 " + stations + " 站实际走了 "
                    + (g.roomsVisited - start) + " 站");
        }
        return out;
    }

    /** 严格推进「一站」：roomsVisited 恰好 +1，与事件挂账的结算点一一对应。 */
    private static void advanceOneStation(Game g) {
        Game.Player p = g.players.get(0);
        p.invuln = 1e9f;                     // 不做闪避判定，避免死亡打断推进
        if (g.phase == Game.EVENT && g.eventChoice >= 0) {
            g.continueEvent();               // → advance()，恰好一站
            return;
        }
        if (g.phase == Game.MAP) {
            g.routeOptions[0] = Game.ROOM_COMBAT;
            g.routeCount = 1;
            g.selectRoute(0);
        }
        if (g.phase == Game.FIGHT) finishRoom(g);   // clearRoom → CHOICE → advance，恰好一站
    }

    /** 把当前战斗房"打完"：清空敌人与配额，让 clearRoom → prepareChoice → advance 走通。 */
    private static void finishRoom(Game g) {
        g.spawned = g.target;
        g.enemies.clear();
        for (int i = 0; i < 8 && g.phase == Game.FIGHT; i++) g.update(Game.STEP);
        if (g.phase == Game.CHOICE) {
            for (Game.Player p : g.players) if (p.chosen < 0) g.choose(p.id, 0, 0);
            for (int i = 0; i < 8 && g.phase == Game.CHOICE; i++) g.update(Game.STEP);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  6. 确定性
    // ══════════════════════════════════════════════════════════════

    private static void determinism() {
        System.out.println("── 6. 确定性：同种子同输入 → 同结果 ──");
        int bad = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            for (int choice = 0; choice < 2; choice++) {
                String a = changeVector(ev, choice, 42).shortString();
                String b = changeVector(ev, choice, 42).shortString();
                if (!a.equals(b)) {
                    bad++;
                    fail("事件 #" + ev + " 分支 " + choice + " 两次结算结果不同");
                }
            }
        }
        check("全部 " + (Events.COUNT * 2) + " 个分支在同种子下结果一致", bad == 0,
                bad + " 个分支不确定");

        // 不同种子应当能产生不同结果（说明随机真的走 Game.random，没有写死）
        int varies = 0;
        for (int ev = 0; ev < Events.COUNT; ev++) {
            int t = Events.TYPE[ev];
            if (t != 6 && t != 10) continue;
            String a = changeVector(ev, 0, 1).shortString();
            String b = changeVector(ev, 0, 2).shortString();
            if (!a.equals(b)) varies++;
        }
        check("赌局类事件的结果确实随种子变化（" + varies + " 个）", varies >= 3,
                "只有 " + varies + " 个事件对种子敏感——随机可能被写死了");
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════
    //  7. 延迟代价必须穿过存档；缺失即测试失败，不能再作为提示放行
    // ══════════════════════════════════════════════════════════════

    private static void codecGapIsDeclared() {
        System.out.println("── 7. 事件挂账存档往返强校验 ──");
        for (int ev = 0; ev < Events.COUNT; ev++) {
            int type = Events.TYPE[ev];
            if (type != 4 && type != 8 && type != 9) continue;
            Game g = freshGame(9100L + ev);
            g.roomsTotal = 999;
            g.phase = Game.EVENT;
            g.eventType = ev;
            g.eventChoice = -1;
            g.coins = 800;
            boolean accepted = g.resolveEvent(0);
            check("事件 #" + ev + " 挂账可以建立", accepted && g.hasDelayedPenalty(), "");
            if (!accepted || !g.hasDelayedPenalty()) continue;
            try {
                Game back = Codec.decode(Codec.encode(g));
                check("事件 #" + ev + " 读档保留挂账种类/站数/金额/血债",
                        back.delayedKind == g.delayedKind
                        && back.delayedRooms == g.delayedRooms
                        && Math.abs(back.delayedAmount - g.delayedAmount) < 1e-6f
                        && back.eventDebt == g.eventDebt, "Codec 丢失了事件延迟状态");
            } catch (Exception e) {
                fail("事件 #" + ev + " Codec 往返抛异常：" + e);
            }
        }
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════
    //  状态变化向量
    // ══════════════════════════════════════════════════════════════

    /** 一次结算带来的状态变化。 */
    static final class Change {
        float hp, maxHp, damage, crit, speed;
        int coins, relics, scouted, debt, delayKind, delayRooms;
        float delayAmount;
        boolean cursed, relicsLost;

        String shortString() {
            return String.format("[h%.1f|m%.1f|d%.2f|c%.3f|s%.1f|$%d|r%d|sc%d|db%d|dk%d|dr%d|da%.3f|cu%s|rl%s]",
                    hp, maxHp, damage, crit, speed, coins, relics, scouted,
                    debt, delayKind, delayRooms, delayAmount, cursed ? "1" : "0",
                    relicsLost ? "1" : "0");
        }
    }

    /**
     * 在指定种子上跑一次 {@code resolveEvent}，返回状态变化向量（及是否变了）。
     * <p>每个维度都取自 {@code Game.Player} 的公开字段或 {@code Game} 的公开字段，
     * 不依赖任何测试钩子。
     */
    private static Change changeVector(int ev, int choice, int seedSalt) {
        Game g = freshGame(1234L + ev * 131L + choice * 17L + seedSalt * 7919L);
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        g.coins = 800;
        Game.Player p = g.players.get(0);
        p.hp = p.maxHp;                          // 统一满血起点，扣血才可见

        Change c = new Change();
        float hp0 = p.hp, max0 = p.maxHp, dmg0 = p.damage, crit0 = p.crit, spd0 = p.speed;
        int coins0 = g.coins, relics0 = relicCount(p), scouted0 = g.scouted, debt0 = g.eventDebt;
        int cursed0 = p.relics[Relics.CURSED];

        if (!g.resolveEvent(choice)) return c;   // 被拒绝 → 全零向量

        c.hp = p.hp - hp0;
        c.maxHp = p.maxHp - max0;
        c.damage = p.damage - dmg0;
        c.crit = p.crit - crit0;
        c.speed = p.speed - spd0;
        c.coins = g.coins - coins0;
        c.relics = relicCount(p) - relics0;
        c.scouted = g.scouted - scouted0;
        c.debt = g.eventDebt - debt0;
        c.delayKind = g.delayedKind;
        c.delayRooms = g.delayedRooms;
        c.delayAmount = g.delayedAmount;
        c.cursed = p.relics[Relics.CURSED] > cursed0;

        c.relicsLost = c.relics < 0;
        return c;
    }

    // ══════════════════════════════════════════════════════════════

    private static Game freshGame(long seed) {
        Game g = new Game(seed);
        g.addPlayer(1, "Tester", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        return g;
    }

    private static List<Integer> eventsOfType(int type) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Events.COUNT; i++) if (Events.TYPE[i] == type) out.add(i);
        return out;
    }

    private static int relicCount(Game.Player p) {
        int n = 0;
        for (int v : p.relics) n += v;
        return n;
    }

    private static String round(float v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private static void check(String name, boolean cond, String detail) {
        if (cond) passed++;
        else fail(name + "  ← " + detail);
    }

    private static void fail(String name) {
        failed++;
        failures.add(name);
        System.out.println("  FAIL  " + name);
    }

    private EventVarietyTest() {}
}
