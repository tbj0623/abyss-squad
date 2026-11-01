import com.dilao.core.*;

import java.io.*;
import java.util.Arrays;

/**
 * Codec v6 → v7 迁移测试。
 * <p>做法：用 v7 编码一个真实 Game，然后手工把字节流"降级"成 v6 形态
 * （版本号改回 6、砍掉尾部 8 字节的 pactMods/pactHeat），再交给 decode。
 * 这保证了 v6 字节布局与真实旧档完全一致，而不是伪造一个近似结构。
 */
public final class CodecMigrationTest {

    static int passed;
    static final int OFF_VERSION = 4;   // MAGIC(4) 之后就是 version
    static final int TAIL_V7 = 8;       // pactMods + pactHeat 两个 int

    public static void main(String[] args) throws Exception {
        testV7RoundTrip();
        testV6MigratesToNoPacts();
        testV6PreservesGameState();
        testPactModsRoundTrip();
        testCorruptRejected();
        System.out.println(passed + " CHECKS PASSED");
    }

    /** v7 自身可正常往返。 */
    static void testV7RoundTrip() throws Exception {
        Game g = makeGame();
        g.pactMods = (1 << Balance.PACT_ELITE_TIDE) | (1 << Balance.PACT_DROUGHT);
        g.pactHeat = Balance.heatOf(g.pactMods);
        byte[] data = Codec.encode(g);
        check(readInt(data, OFF_VERSION) == 7, "v7 快照版本号写入为 7");
        Game copy = Codec.decode(data);
        check(copy.pactMods == g.pactMods, "v7 往返：pactMods 一致");
        check(copy.pactHeat == g.pactHeat, "v7 往返：pactHeat 一致");
    }

    /** 核心验收：v6 字节流能读，且 pactMods/pactHeat 归零。 */
    static void testV6MigratesToNoPacts() throws Exception {
        Game g = makeGame();
        g.pactMods = 0;
        g.pactHeat = 0;
        byte[] v7 = Codec.encode(g);
        byte[] v6 = downgradeToV6(v7);
        check(readInt(v6, OFF_VERSION) == 6, "降级后的字节流版本号为 6");
        check(v6.length == v7.length - TAIL_V7, "v6 字节流比 v7 少 8 字节");

        Game migrated = Codec.decode(v6);
        check(migrated != null, "v6 字节流 decode 成功（不抛异常）");
        check(migrated.pactMods == 0, "v6 迁移后 pactMods == 0");
        check(migrated.pactHeat == 0, "v6 迁移后 pactHeat == 0");
    }

    /** v6 迁移不能丢其它状态。 */
    static void testV6PreservesGameState() throws Exception {
        Game g = makeGame();
        g.coins = 321;
        g.roomsVisited = 9;
        g.floor = 4;
        g.level = 7;
        g.kills = 88;
        byte[] v6 = downgradeToV6(Codec.encode(g));
        Game m = Codec.decode(v6);
        check(m.coins == 321, "v6 迁移保留 coins");
        check(m.roomsVisited == 9, "v6 迁移保留 roomsVisited");
        check(m.floor == 4, "v6 迁移保留 floor");
        check(m.level == 7, "v6 迁移保留 level");
        check(m.kills == 88, "v6 迁移保留 kills");
        check(m.players.size() == g.players.size(), "v6 迁移保留玩家数");
        check(m.seed == g.seed, "v6 迁移保留 seed");
    }

    /** 词条位掩码进存档后仍然生效。 */
    static void testPactModsRoundTrip() throws Exception {
        Game g = makeGame();
        int all = (1 << Balance.PACT_COUNT) - 1;
        g.pactMods = all;
        g.pactHeat = Balance.heatOf(all);
        Game copy = Codec.decode(Codec.encode(g));
        check(copy.pactMods == all, "满配 12 条词条可完整存读档");
        check(copy.pactHeat == Balance.PACT_HEAT_MAX, "满配 heat == PACT_HEAT_MAX");
    }

    /** 损坏的版本号仍然被拒绝。 */
    static void testCorruptRejected() throws Exception {
        Game g = makeGame();
        byte[] data = Codec.encode(g);
        writeInt(data, OFF_VERSION, 5);
        boolean rejected = false;
        try {
            Codec.decode(data);
        } catch (IOException e) {
            rejected = true;
        }
        check(rejected, "未知版本号 v5 被拒绝（只接受 6 / 7）");
    }

    // ── 工具 ──────────────────────────────────────────────────────

    static Game makeGame() {
        Game g = new Game(20260101L);
        g.addPlayer(1, "mig", 0);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.selectRoute(0);
        return g;
    }

    /** 把 v7 字节流还原成 v6：版本号写回 6，并砍掉尾部 8 字节。 */
    static byte[] downgradeToV6(byte[] v7) {
        byte[] v6 = Arrays.copyOf(v7, v7.length - TAIL_V7);
        writeInt(v6, OFF_VERSION, 6);
        return v6;
    }

    static int readInt(byte[] d, int off) {
        return ((d[off] & 0xff) << 24) | ((d[off + 1] & 0xff) << 16)
                | ((d[off + 2] & 0xff) << 8) | (d[off + 3] & 0xff);
    }

    static void writeInt(byte[] d, int off, int value) {
        d[off] = (byte) (value >>> 24);
        d[off + 1] = (byte) (value >>> 16);
        d[off + 2] = (byte) (value >>> 8);
        d[off + 3] = (byte) value;
    }

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("FAIL " + name);
        passed++;
        System.out.println("PASS " + name);
    }
}
