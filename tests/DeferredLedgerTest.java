import com.dilao.core.*;
import java.io.IOException;
import java.util.Arrays;

/** v11 多笔延迟代价账本：不丢账、不双扣，旧档迁移和联网快照逐字节稳定。 */
public final class DeferredLedgerTest {
    private static int checked;
    private static void ok(boolean cond, String message) {
        checked++;
        if (!cond) throw new AssertionError(message);
    }
    private static Game fresh(long seed) {
        Game g = new Game(seed);
        g.addPlayer(1, "Ledger", 0);
        ok(g.startSolo(1, Game.MODE_EXPEDITION), "start solo");
        g.roomsTotal = 999;
        g.coins = 1000;
        Game.Player p = g.players.get(0);
        p.hp = p.maxHp;
        return g;
    }
    private static int event(int type, boolean coinDelay) {
        for (int i = 0; i < Events.COUNT; i++)
            if (Events.TYPE[i] == type && (type != 9 || ((int) Events.PARAM_C[i] == 1) == coinDelay))
                return i;
        throw new AssertionError("missing event type " + type);
    }
    private static void choose(Game g, int ev, int choice) {
        g.phase = Game.EVENT;
        g.eventType = ev;
        g.eventChoice = -1;
        ok(g.resolveEvent(choice), "cannot choose " + ev + "/" + choice);
    }
    private static void step(Game g) {
        g.phase = Game.EVENT;
        g.eventChoice = 0;
        ok(g.continueEvent(), "cannot advance");
    }
    private static void same(Game a, Game b, String label) throws Exception {
        ok(Arrays.equals(Codec.encode(a), Codec.encode(b)), label + " save bytes diverged");
    }
    private static void bad(byte[] snapshot, String message) throws Exception {
        boolean rejected = false;
        try { Codec.decode(snapshot); }
        catch (IOException expected) { rejected = true; }
        ok(rejected, message);
    }
    private static void testOverlapping() throws Exception {
        Game g = fresh(3201);
        int medical = event(4, false), loan = event(9, true);
        choose(g, medical, 0);
        ok(g.pendingChargeCount() == 1, "medical debt missing");
        step(g);
        ok(g.nearestChargeRooms() == 2, "medical countdown first");
        choose(g, loan, 0);
        ok(g.pendingChargeCount() == 2, "new event erased old medical debt");
        ok(g.delayedCharges.get(0).source == 4 && g.delayedCharges.get(1).source == 9,
                "source identity not preserved");
        step(g);
        ok(g.pendingChargeCount() == 2 && g.nearestChargeRooms() == 1,
                "overlapping debts countdown wrong");
        Game loaded = Codec.decode(Codec.encode(g));
        same(g, loaded, "two pending charges on load");
        step(g);
        step(loaded);
        same(g, loaded, "first charge expiry");
        ok(g.pendingChargeCount() == 1 && g.delayedCharges.get(0).source == 9,
                "first expiry removed second charge");
        int before = g.coins;
        int due = Math.round(g.delayedCharges.get(0).amount);
        step(g);
        step(loaded);
        same(g, loaded, "second charge expiry");
        ok(g.pendingChargeCount() == 0 && !g.hasDelayedPenalty(), "second charge not settled");
        ok(g.coins == Math.max(0, before - due), "coin invoice was overwritten/unpaid");
    }
    private static void testDebtCancellationAndExpiry() throws Exception {
        int debt = event(8, false), medical = event(4, false);
        Game g = fresh(3202);
        choose(g, debt, 0);
        ok(g.eventDebt == 1 && g.pendingChargeCount() == 1, "blood debt not created");
        choose(g, medical, 0);
        ok(g.pendingChargeCount() == 2, "medical invoice overwrote blood debt");
        choose(g, debt, 1);
        ok(g.eventDebt == 0, "blood debt B did not lower to 0");
        ok(g.pendingChargeCount() == 1 && g.delayedCharges.get(0).source == 4,
                "blood debt cancellation erased unrelated medical charge");
        Game roundTrip = Codec.decode(Codec.encode(g));
        same(g, roundTrip, "cancellation save");
        for (int j = 0; j < 3; j++) { step(g); step(roundTrip); }
        same(g, roundTrip, "cancellation continuation");
        ok(g.pendingChargeCount() == 0, "medical penalty did not expire");

        Game h = fresh(3203);
        choose(h, debt, 0);
        for (int j = 0; j < 3; j++) step(h);
        ok(h.eventDebt == 0 && h.pendingChargeCount() == 0,
                "paid blood debt still counts toward future debt");
        h.phase = Game.EVENT;
        h.eventChoice = -1;
        h.eventType = debt;
        h.eventDebt = 9;
        ok(!h.canEvent(0) && h.canEvent(1), "max blood debt permits free infinite coins");
    }
    private static void testLegacyAndCorruption() throws Exception {
        Game g = fresh(3204);
        choose(g, event(4, false), 0);
        byte[] modern = Codec.encode(g);
        ok(g.pendingChargeCount() == 1, "legacy fixture not scheduled");
        // v11 在 v10 布局末尾追加 int count + count*(3 int + float)，删去它即可恢复 v10。
        int trailer = 4 + g.pendingChargeCount() * 16;
        byte[] old = Arrays.copyOf(modern, modern.length - trailer);
        old[4] = 0; old[5] = 0; old[6] = 0; old[7] = 10;
        Game migrated = Codec.decode(old);
        ok(migrated.pendingChargeCount() == 1
                && migrated.delayedCharges.get(0).source == Game.DELAY_SOURCE_LEGACY,
                "v10 invoice migration lost amount/source");
        ok(migrated.delayedKind == g.delayedKind
                && migrated.delayedRooms == g.delayedRooms
                && Float.floatToIntBits(migrated.delayedAmount) == Float.floatToIntBits(g.delayedAmount),
                "v10 migration changed invoice");
        Game again = Codec.decode(Codec.encode(migrated));
        same(migrated, again, "migrated v10 saved as v11");

        Game empty = fresh(3205);
        byte[] malformed = Codec.encode(empty);
        malformed[malformed.length - 4] = 0x7f;
        malformed[malformed.length - 3] = (byte) 0xff;
        malformed[malformed.length - 2] = (byte) 0xff;
        malformed[malformed.length - 1] = (byte) 0xff;
        bad(malformed, "corrupt charge count accepted");
        byte[] inconsistent = Arrays.copyOf(modern, modern.length);
        inconsistent[inconsistent.length - 1] ^= 1; // 修改一笔的浮点金额但不修改旧式摘要
        bad(inconsistent, "inconsistent legacy summary accepted");
    }
    public static void main(String[] args) throws Exception {
        testOverlapping();
        testDebtCancellationAndExpiry();
        testLegacyAndCorruption();
        System.out.println("DeferredLedgerTest PASS " + checked + " assertions");
    }
    private DeferredLedgerTest() {}
}
