import com.dilao.core.*;

/**
 * 实体 id 分配的不变量回归。
 *
 * <p><b>为什么需要这个测试</b>：{@code Game.nextId} 单调递增并随存档序列化，
 * 而 {@code Codec.decode} 用 {@code range(nextId, 10, 5_000_000)} 做健全性校验。
 * 无尽模式理论上可以把计数推过 500 万——那一刻**玩家自己的存档会被自己的校验拒收**，
 * 表现为"读档失败"且没有任何提示。这条路径靠人工几乎测不到（要连玩近千小时）。
 *
 * <p>这里用直接推进 {@code nextId} 的方式把边界拉到手边，验证：
 * <ol>
 *   <li>越过上限后不会崩，也不会产生重复 id</li>
 *   <li>回绕后分配出的 id 全部落在 Codec 接受区间内</li>
 *   <li>回绕不会把 id 分配给"仍然活着的实体"</li>
 *   <li>回绕后的存档能正常 encode → decode 一个来回</li>
 * </ol>
 */
public final class IdRolloverTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 实体 id 回绕回归 ===");
        System.out.println();

        basics();
        System.out.println();
        rollover();
        System.out.println();
        survivesSaveRoundTrip();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    // ── 1. 正常区间内单调递增、不重复 ─────────────────────────────
    static void basics() {
        Game g = new Game(1L);
        g.addPlayer(1, "p", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);

        int prev = -1;
        boolean mono = true;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        boolean unique = true;
        for (int i = 0; i < 2000; i++) {
            int id = g.allocateId();
            if (id <= prev) mono = false;
            if (!seen.add(id)) unique = false;
            prev = id;
        }
        check("2000 次分配严格递增", mono, "last=" + prev);
        check("2000 次分配无重复", unique, "distinct=" + seen.size());
        check("id 均 >= Codec 下界 10", seen.stream().allMatch(i -> i >= 10),
                "min=" + seen.stream().mapToInt(Integer::intValue).min().orElse(-1));
    }

    // ── 2. 越过上限后回绕，且回绕结果仍合法、不重复 ────────────────
    static void rollover() {
        Game g = new Game(2L);
        g.addPlayer(1, "p", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.phase = Game.FIGHT;

        // 把计数器推到上限附近（模拟无尽模式跑了很久）。
        g.nextId = 4_899_995;

        int[] ids = new int[400];
        for (int i = 0; i < ids.length; i++) ids[i] = g.allocateId();

        boolean inRange = true;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int id : ids) {
            if (id < 10 || id >= 5_000_000) inRange = false;
            min = Math.min(min, id);
            max = Math.max(max, id);
        }
        check("回绕后分配仍在 Codec 接受区间 [10, 5000000)", inRange,
                "min=" + min + " max=" + max);

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        boolean unique = true;
        for (int id : ids) if (!seen.add(id)) unique = false;
        check("回绕后 400 次分配无重复", unique, "distinct=" + seen.size());

        check("回绕确实发生了（出现了远小于起点的 id）", min < 4_899_995,
                "起点 4899995，回绕后最小 id=" + min);
    }

    // ── 3. 回绕不会撞上仍然活着的实体 ─────────────────────────────
    static void survivesSaveRoundTrip() throws Exception {
        Game g = new Game(3L);
        g.addPlayer(1, "p", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.phase = Game.FIGHT;

        // 在回绕起点附近放一批"活着的"实体，占住 id。
        int occupied = 0;
        for (int i = 0; i < 40; i++) {
            Game.Enemy e = new Game.Enemy();
            e.id = 16 + i;                       // ID_FLOOR 起的连续段
            e.type = 0;
            e.behavior = Enemies.BEHAVIOR[0];
            e.x = 300 + i * 10; e.y = 300;
            e.maxHp = e.hp = 100f;
            e.r = 12; e.stage = -1;
            if (g.enemies.size() < Game.MAX_ENEMIES) { g.enemies.add(e); occupied++; }
        }

        g.nextId = 4_900_000;                    // 强制触发回绕
        int first = g.allocateId();
        check("回绕跳过被占用的 id", first >= 16 + occupied,
                "首个回绕 id=" + first + "，占用段 16.." + (16 + occupied - 1));

        // 回绕后的存档必须仍然能被 Codec 读回来（这是最初的问题：读档失败）。
        boolean roundTrip = false;
        String detail;
        try {
            byte[] packed = Checkpoint.pack(g);
            Game back = Checkpoint.unpack(packed);
            roundTrip = back.nextId > 0 && back.players.size() == 1;
            detail = "encode " + packed.length + " bytes → decode OK, nextId=" + back.nextId;
        } catch (Exception e) {
            detail = "存档往返失败: " + e;
        }
        check("回绕后的存档能正常往返", roundTrip, detail);
    }
}
