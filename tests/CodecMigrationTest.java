import com.dilao.core.*;

import java.io.IOException;
import java.util.Arrays;

/**
 * 当前 Codec v11 的真实兼容范围：v10 单笔挂账迁移与不受支持版本拒绝。
 * 历史上此文件仍把 v7 当当前版本、把 v6 当可读存档，已与实际协议冲突。
 * v7–v9 的中段字段布局与 v11 不同，不能仅通过修改版本头伪造旧档。
 */
public final class CodecMigrationTest {
    private static int checks;
    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) throw new AssertionError(what);
    }
    private static void setVersion(byte[] data, int version) {
        data[4] = (byte) (version >>> 24);
        data[5] = (byte) (version >>> 16);
        data[6] = (byte) (version >>> 8);
        data[7] = (byte) version;
    }
    private static int versionOf(byte[] data) {
        return ((data[4] & 255) << 24) | ((data[5] & 255) << 16)
                | ((data[6] & 255) << 8) | (data[7] & 255);
    }
    private static Game fresh() {
        Game g = new Game(20261102L);
        g.addPlayer(1, "Migration", 0);
        check(g.startSolo(1, Game.MODE_EXPEDITION), "start solo");
        g.coins = 800;
        g.pactMods = 1 << Balance.PACT_ELITE_TIDE;
        g.pactHeat = Balance.heatOf(g.pactMods);
        return g;
    }
    private static void step(Game g) {
        g.phase = Game.EVENT;
        g.eventChoice = 0;
        check(g.continueEvent(), "advance after migration");
    }
    private static void reject(byte[] data, String label) {
        boolean failed = false;
        try { Codec.decode(data); }
        catch (IOException expected) { failed = true; }
        check(failed, label);
    }
    public static void main(String[] args) throws Exception {
        Game g = fresh();
        byte[] v11 = Codec.encode(g);
        check(versionOf(v11) == 11, "current header must be v11");
        Game roundTrip = Codec.decode(v11);
        check(roundTrip.pactMods == g.pactMods && roundTrip.pactHeat == g.pactHeat,
                "current pact state survives roundtrip");
        check(Arrays.equals(v11, Codec.encode(roundTrip)), "v11 roundtrip byte-identical");

        g.phase = Game.EVENT;
        g.eventType = 4; // 医疗：3 站后扣血
        g.eventChoice = -1;
        check(g.resolveEvent(0), "delayed fixture selected");
        check(g.pendingChargeCount() == 1, "delayed fixture has one charge");
        // v11 在完整 v10 快照尾部新增 count(4) + 16 字节/笔，截掉后就是 v10。
        byte[] v10 = Arrays.copyOf(Codec.encode(g), Codec.encode(g).length - 20);
        setVersion(v10, 10);
        Game migrated = Codec.decode(v10);
        check(migrated.pendingChargeCount() == 1, "v10 single invoice migrated");
        check(migrated.delayedCharges.get(0).source == Game.DELAY_SOURCE_LEGACY,
                "v10 unknown source safely marked");
        check(migrated.delayedAmount == g.delayedAmount
                && migrated.delayedRooms == g.delayedRooms, "v10 amount/clock retained");
        check(migrated.pactMods == g.pactMods, "v10 pact state retained");
        Game saveAgain = Codec.decode(Codec.encode(migrated));
        check(Arrays.equals(Codec.encode(migrated), Codec.encode(saveAgain)),
                "migrated invoice byte-identical after v11 re-save");
        for (int j = 0; j < 3; j++) { step(g); step(migrated); }
        check(g.pendingChargeCount() == 0 && migrated.pendingChargeCount() == 0,
                "v10 charge expires exactly once");
        check(Float.floatToIntBits(g.players.get(0).hp)
                        == Float.floatToIntBits(migrated.players.get(0).hp),
                "v10 charge applies same health penalty");

        for (int unsupported : new int[]{0, 5, 6, 12, 99}) {
            byte[] bad = Arrays.copyOf(v11, v11.length);
            setVersion(bad, unsupported);
            reject(bad, "unsupported v" + unsupported + " accepted");
        }
        byte[] trailing = Arrays.copyOf(v11, v11.length + 1);
        reject(trailing, "trailing data accepted");
        System.out.println("CodecMigrationTest PASS " + checks + " assertions");
    }
    private CodecMigrationTest() {}
}
