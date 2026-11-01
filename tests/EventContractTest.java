import com.dilao.core.*;

/**
 * 事件契约测试：断言 {@code Events.TYPE} 与 {@code Game.resolveEvent} 的实现一致。
 *
 * <p><b>为什么需要这个测试</b>：{@code Events.TYPE} 是纯数据，但它的语义由
 * {@code Game.resolveEvent} 的 {@code switch} 实现。历史上曾出现
 * 「只改了 {@code Events.TYPE}，忘了改 {@code resolveEvent}」的事故：
 * 19 个事件的 TYPE 超出实现范围，全部静默落进 {@code default} 分支（TYPE 7 诅咒），
 * 导致文案说「注视 +1 层」而玩家实际拿到诅咒遗物——<b>行为与文案完全矛盾</b>。
 *
 * <p><b>检测原理</b>：{@code Game.resolveEvent} 的 TYPE 7 是唯一会给玩家
 * 增加 {@code Relics.CURSED} 的分支。因此：
 * <ul>
 *   <li>TYPE == 7 的事件，A 选项后 CURSED 必须增加</li>
 *   <li>TYPE != 7 的事件，A 选项后 CURSED <b>绝不能</b>增加</li>
 * </ul>
 * 任何「TYPE 没有对应 case」的未知值都会落进 {@code default}（TYPE 7 诅咒），
 * 从而被第二条断言抓出来。<b>这是黑盒验证，不依赖任何手动维护的常量。</b>
 *
 * <p>附加断言：每个 TYPE 至少覆盖 2 个事件（防孤岛/防换皮回归）。
 *
 * <p>用法：
 * <pre>
 *   javac -encoding UTF-8 -cp build\core-check -d build\evt-test tests\EventContractTest.java
 *   java -cp "build\core-check;build\evt-test" EventContractTest
 * </pre>
 * 退出码：0 全部通过；1 存在失败。
 */
public final class EventContractTest {

    private static int passed, failed;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== 事件契约测试：Events.TYPE ↔ Game.resolveEvent ===");
        System.out.println();

        // ── 1. 每个 TYPE 都有对应实现（黑盒：非 7 不得产生 CURSED）──
        for (int type = 0; type <= Events.IMPLEMENTED_TYPE_MAX; type++) {
            int ev = firstEventOfType(type);
            if (ev < 0) {
                fail("TYPE " + type + " 没有任何事件（孤岛类型）");
                continue;
            }
            checkTypeImplemented(type, ev);
        }

        // ── 2. 超过实现范围的 TYPE 必须不存在（防止 re-TYPE 后忘改 Game）──
        int maxUsed = 0;
        for (int t : Events.TYPE) maxUsed = Math.max(maxUsed, t);
        check("Events.TYPE 最大值 " + maxUsed + " <= 已实现上界 " + Events.IMPLEMENTED_TYPE_MAX,
                maxUsed <= Events.IMPLEMENTED_TYPE_MAX,
                "Events.TYPE 里出现了未实现的 TYPE " + maxUsed
                        + "，它会静默落进 resolveEvent 的 default 分支！");

        // ── 3. 每个 TYPE 至少 2 个事件（防孤岛 / 防换皮）──
        for (int type = 0; type <= Events.IMPLEMENTED_TYPE_MAX; type++) {
            int n = Events.countOfType(type);
            check("TYPE " + type + " 覆盖 " + n + " 个事件（需 >= 2）", n >= 2,
                    "TYPE " + type + " 只有 " + n + " 个事件");
        }

        // ── 4. 双向结算：A / B 都不能抛异常 ──
        for (int ev = 0; ev < Events.COUNT; ev++) {
            checkBothChoicesSafe(ev);
        }

        // ── 汇总 ──
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

    /** 找第一个该 TYPE 的事件 id；没有返回 -1。 */
    private static int firstEventOfType(int type) {
        for (int ev = 0; ev < Events.COUNT; ev++)
            if (Events.TYPE[ev] == type) return ev;
        return -1;
    }

    /**
     * 黑盒验证某个 TYPE 确实被 resolveEvent 实现：
     * TYPE 7 必须产生 CURSED；其它 TYPE 绝不能产生 CURSED。
     */
    private static void checkTypeImplemented(int type, int ev) {
        Game g = freshGame();
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        g.coins = 999;
        Game.Player p = g.players.get(0);
        int before = p.relics[Relics.CURSED];

        boolean ok;
        try {
            ok = g.resolveEvent(0);
        } catch (Throwable t) {
            fail("TYPE " + type + "（事件 " + ev + " " + Events.NAMES[ev] + "）A 选项抛异常: " + t);
            return;
        }
        if (!ok) {
            fail("TYPE " + type + "（事件 " + ev + "）A 选项被拒绝（canEvent 返回 false）");
            return;
        }
        int after = p.relics[Relics.CURSED];
        boolean cursedUp = after > before;

        if (type == 7) {
            check("TYPE 7（诅咒）事件 " + ev + " A 选项产生 CURSED", cursedUp,
                    "TYPE 7 应该增加 CURSED，但 " + before + " -> " + after);
        } else {
            check("TYPE " + type + " 走的是自己的分支（未误入诅咒 default）", !cursedUp,
                    "TYPE " + type + "（事件 " + ev + " " + Events.NAMES[ev] + "）"
                            + "A 选项后 CURSED " + before + " -> " + after
                            + "，说明它落进 resolveEvent 的 default（诅咒）分支——"
                            + "该 TYPE 没有对应的 case！");
        }
    }

    /** A、B 两个选项都不能抛异常，且都必须返回 true。 */
    private static void checkBothChoicesSafe(int ev) {
        for (int choice = 0; choice <= 1; choice++) {
            Game g = freshGame();
            g.phase = Game.EVENT;
            g.eventType = ev;
            g.eventChoice = -1;
            g.coins = 999;
            String label = "事件 " + ev + "（" + Events.NAMES[ev] + "，TYPE "
                    + Events.TYPE[ev] + "）选项 " + (choice == 0 ? "A" : "B");
            try {
                if (!g.resolveEvent(choice)) {
                    fail(label + " 被拒绝");
                    continue;
                }
                pass(label + " 结算正常");
            } catch (Throwable t) {
                fail(label + " 抛异常: " + t);
            }
        }
    }

    private static Game freshGame() {
        Game g = new Game(20240601L);
        g.addPlayer(1, "Tester", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        return g;
    }

    private static void check(String name, boolean cond, String detail) {
        if (cond) pass(name);
        else fail(name + "  ← " + detail);
    }

    private static void pass(String name) { passed++; }

    private static void fail(String name) { failed++; failures.add(name); }
}
