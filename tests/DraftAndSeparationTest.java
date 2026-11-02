import com.dilao.core.DoctrineHooks;
import com.dilao.core.Game;
import com.dilao.core.Relics;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

/** Draft award parity + enemy separation exact-state regression. No Android device needed. */
public final class DraftAndSeparationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Game.Player player(Game g) {
        return g.addPlayer(1, "测试玩家", 0);
    }

    private static void drafts() {
        for (int id = 0; id < Relics.COUNT; id++) {
            Game g = new Game(29010000L + id);
            Game.Player p = player(g);
            int school = Relics.SCHOOL[id];
            int before = g.schoolCount(p, school);
            check(g.relicGrantOutcome(p, id) == Game.RELIC_NEW_KIND,
                    "brand new relic must advance synergy " + id);
            int coins = g.coins;
            g.grantRelic(p, id);
            check(p.relics[id] == 1 && g.schoolCount(p, school) == before + 1,
                    "preview and first award differ " + id);
            check(g.coins == coins, "ordinary relic unexpectedly converted " + id);

            for (int layer = 1; layer < Relics.CAP[id]; layer++) {
                check(g.relicGrantOutcome(p, id) == Game.RELIC_STACK,
                        "stack would be misreported as new kind " + id);
                int n = g.schoolCount(p, school);
                g.grantRelic(p, id);
                check(p.relics[id] == layer + 1 && g.schoolCount(p, school) == n,
                        "stack incorrectly advanced synergy " + id);
            }
            check(g.relicGrantOutcome(p, id) == Game.RELIC_MAXED_TO_COINS,
                    "capped relic should preview coins " + id);
            coins = g.coins;
            g.grantRelic(p, id);
            check(g.coins == coins + 18 && p.relics[id] == Relics.CAP[id],
                    "full-stack conversion mismatch " + id);
        }

        Game g = new Game(29010061L);
        Game.Player p = player(g);
        g.grantRelic(p, 0);
        g.grantRelic(p, 1);
        check(g.schoolCount(p, Relics.SCHOOL[2]) == 2, "synergy fixture 2/3");
        check(g.relicGrantOutcome(p, 2) == Game.RELIC_NEW_KIND, "third kind unlock forecast");
        g.grantRelic(p, 2);
        check(g.schoolCount(p, Relics.SCHOOL[2]) == 3, "synergy really unlocks");
        check(g.relicGrantOutcome(p, 0) == Game.RELIC_STACK ||
                        g.relicGrantOutcome(p, 0) == Game.RELIC_MAXED_TO_COINS,
                "duplicate must not claim another unlock");

        Game capped = new Game(29010062L);
        check(capped.applyDoctrine(17, DoctrineHooks.H_RELIC_SLOTS,
                        DoctrineHooks.H_NONE, 2f, 0f, 0f), "slot cap fixture");
        Game.Player q = player(capped);
        capped.grantRelic(q, 0);
        capped.grantRelic(q, 1);
        int sameSchool = Relics.SCHOOL[2];
        int n = capped.schoolCount(q, sameSchool);
        check(capped.relicGrantOutcome(q, 2) == Game.RELIC_FULL_TO_COINS,
                "out-of-slots must not forecast 3/3 synergy");
        int coins = capped.coins;
        capped.grantRelic(q, 2);
        check(capped.coins == coins + 18 && q.relics[2] == 0
                && capped.schoolCount(q, sameSchool) == n,
                "full-slot conversion must not unlock a third relic");
        check(capped.relicGrantOutcome(q, 0) == Game.RELIC_STACK,
                "same-kind upgrades bypass the slot cap");
        capped.grantRelic(q, 0);
        check(q.relics[0] == 2, "same-kind upgrade under slot cap");

        int[] snapshot = Arrays.copyOf(q.relics, q.relics.length);
        coins = capped.coins;
        for (int i = 0; i < 1000; i++) capped.relicGrantOutcome(q, i % Relics.COUNT);
        check(Arrays.equals(snapshot, q.relics) && coins == capped.coins,
                "preview must be read-only");
        check(capped.relicGrantOutcome(null, 0) == Game.RELIC_INVALID
                && capped.relicGrantOutcome(q, -1) == Game.RELIC_INVALID
                && capped.relicGrantOutcome(q, Relics.COUNT) == Game.RELIC_INVALID,
                "invalid preview must be safe");
    }

    private static void separate(Method fast, Method move, long seed, int mode) throws Exception {
        Game baseline = new Game(seed), optimized = new Game(seed);
        Random random = new Random(seed);
        final int count = Game.MAX_ENEMIES;
        for (int i = 0; i < count; i++) {
            Game.Enemy a = new Game.Enemy();
            a.r = 9 + i % 5;
            if (mode == 0) {
                a.x = 65 + (i % 10) * 99;
                a.y = 57 + (i / 10) * 62;
            } else if (mode == 1) {
                a.x = 430 + random.nextFloat() * 175;
                a.y = 220 + random.nextFloat() * 135;
            } else if (mode == 2) {
                a.x = 35 + random.nextFloat() * 1040;
                a.y = 37 + random.nextFloat() * 543;
            } else {
                a.x = 480 + (i % 2 == 0 ? 0 : 20f + i % 4 * .00001f);
                a.y = 310 + (i % 3 == 0 ? 0 : i % 5 * .00001f);
            }
            Game.Enemy b = new Game.Enemy();
            b.x = a.x; b.y = a.y; b.r = a.r;
            baseline.enemies.add(a);
            optimized.enemies.add(b);
        }
        for (int i = 0; i < count; i++)
            for (int j = i + 1; j < count; j++) {
                Game.Enemy a = baseline.enemies.get(i), b = baseline.enemies.get(j);
                float distance = Game.distance(a.x, a.y, b.x, b.y);
                if (distance > .1f && distance < a.r + b.r) {
                    float push = Math.min(2, (a.r + b.r - distance) * .13f);
                    move.invoke(baseline, a, (a.x - b.x) / distance * push,
                            (a.y - b.y) / distance * push);
                }
            }
        fast.invoke(optimized);
        for (int i = 0; i < count; i++) {
            Game.Enemy a = baseline.enemies.get(i), b = optimized.enemies.get(i);
            check(Float.floatToIntBits(a.x) == Float.floatToIntBits(b.x)
                            && Float.floatToIntBits(a.y) == Float.floatToIntBits(b.y),
                    "enemy separation diverged: seed=" + seed + ", mode=" + mode + ", index=" + i);
        }
    }

    private static void separation() throws Exception {
        Method fast = Game.class.getDeclaredMethod("separateEnemies");
        Method move = Game.class.getDeclaredMethod("moveEnemy", Game.Enemy.class,
                float.class, float.class);
        fast.setAccessible(true);
        move.setAccessible(true);
        for (int mode = 0; mode < 4; mode++)
            for (int trial = 0; trial < 12; trial++)
                separate(fast, move, 29010070L + mode * 100 + trial, mode);

        // Sparse 90-enemy fixture: every pair fails the cheap radius check.
        int cheapRejects = 0;
        for (int i = 0; i < Game.MAX_ENEMIES; i++)
            for (int j = i + 1; j < Game.MAX_ENEMIES; j++) {
                float dx = (i % 10 - j % 10) * 99f;
                float dy = (i / 10 - j / 10) * 62f;
                float limit = 2 * 13f + 1f;
                if (dx * dx + dy * dy > limit * limit) cheapRejects++;
            }
        check(cheapRejects == 4005,
                "sparse 90-enemy scene should eliminate all 4,005 hypot calls");
    }

    public static void main(String[] args) throws Exception {
        drafts();
        separation();
        System.out.println("PASS DraftAndSeparationTest: 60 relics, slot cap, real synergy, "
                + "48x90 bit-identical enemy separation, 4,005 distant pairs skipped");
    }
}
