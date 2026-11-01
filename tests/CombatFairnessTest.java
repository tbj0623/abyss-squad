import com.dilao.core.Balance;
import com.dilao.core.Enemies;
import com.dilao.core.Evolutions;
import com.dilao.core.Game;
import com.dilao.core.Relics;

import java.lang.reflect.Method;

/**
 * Runtime fairness regressions: the table validator alone cannot prove that the
 * actual enemy attack state machine respects the telegraph floor, or that an
 * offline teammate picks a useful valid reward.
 */
public final class CombatFairnessTest {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static Object call(Method m, Object target, Object... args) throws Exception {
        return m.invoke(target, args);
    }

    public static void main(String[] args) throws Exception {
        Method start = Game.class.getDeclaredMethod(
                "startTelegraph", Game.Enemy.class, Game.Player.class);
        start.setAccessible(true);
        Game g = new Game(27102026L);
        Game.Player p = g.addPlayer(1, "公平性测试", 0);
        int checked = 0;
        boolean floorUsed = false;
        for (int contract = 0; contract < Balance.PACE.length; contract++) {
            g.contract = contract;
            for (int type = 0; type < Enemies.COUNT; type++) {
                for (int elite = 0; elite <= 1; elite++) {
                    Game.Enemy e = new Game.Enemy();
                    e.type = type;
                    e.behavior = Enemies.BEHAVIOR[type];
                    e.elite = elite == 1;
                    call(start, g, e, p);
                    float base = Enemies.TELEGRAPH[type] * (e.elite ? .85f : 1f);
                    float expected = Math.max(Enemies.TELEGRAPH_FLOOR,
                            base * Balance.telegraphScale(e.behavior));
                    check(Float.isFinite(e.telegraph) && e.telegraph >= Enemies.TELEGRAPH_FLOOR,
                            "unreadable windup: type=" + type + ", contract=" + contract + ", elite=" + elite);
                    check(Math.abs(e.telegraph - expected) < .0001f,
                            "unexpected windup: type=" + type + ", actual=" + e.telegraph);
                    check(Math.abs(e.windup - e.telegraph) < .0001f,
                            "HUD windup disagrees with combat timer");
                    check(Float.isFinite(e.cooldown) && e.cooldown > 0f,
                            "invalid followup cooldown");
                    float paidWarning = expected - base;
                    float expectedCooldown = Math.max(base * .35f,
                            Enemies.COOLDOWN[type] / Balance.PACE[contract] - paidWarning);
                    check(Math.abs(e.cooldown - expectedCooldown) < .0001f,
                            "longer warning must be paid for by followup cooldown");
                    if (base * Balance.telegraphScale(e.behavior) < Enemies.TELEGRAPH_FLOOR)
                        floorUsed = true;
                    checked++;
                }
            }
        }
        check(floorUsed, "test never exercised the floor");
        check(Float.isFinite(Enemies.telegraphSeconds(9, Float.NaN)),
                "invalid pace must not create a NaN warning");
        check(Float.isFinite(Enemies.telegraphSeconds(9, Float.POSITIVE_INFINITY)),
                "infinite pace must not create an invalid warning");

        Method pick = Game.class.getDeclaredMethod("bestAutoChoice", Game.Player.class);
        pick.setAccessible(true);
        Game choiceGame = new Game(1234L);
        Game.Player q = choiceGame.addPlayer(2, "自动选牌", 2);
        // A full-rank relic must never defeat an upgrade that can still be taken.
        q.offers[0] = Relics.EMBER_CROWN;
        q.relics[Relics.EMBER_CROWN] = Relics.CAP[Relics.EMBER_CROWN];
        q.offers[1] = Relics.FROST_SHARD;
        q.offers[2] = -1;
        check((int) call(pick, choiceGame, q) == 1, "full relic was auto-picked");

        // Completing three DISTINCT frost relics is more valuable than a random card.
        q.relics[Relics.FROST_SHARD] = 1;
        q.relics[Relics.FROST_CROWN] = 1;
        q.offers[0] = Relics.STORM_CORE;
        q.offers[1] = Relics.ICE_BOTTLE;
        q.offers[2] = -1;
        check((int) call(pick, choiceGame, q) == 1, "third frost school relic not favored");

        // Evolution draft has sparse slots. Never pick the empty middle slot, and
        // prefer a qualified tier-2 evolution over a tier-1 one.
        q.offers[0] = q.offers[1] = q.offers[2] = -1;
        q.evolveOffer[0] = Evolutions.index(q.role, 0);
        q.evolveOffer[1] = -1;
        q.evolveOffer[2] = Evolutions.index(q.role, 2);
        check((int) call(pick, choiceGame, q) == 2, "tier-2 evolution missed");

        // Identical ranks have a stable tie break, with no RNG consumption.
        q.evolveOffer[0] = q.evolveOffer[1] = q.evolveOffer[2] = -1;
        q.offers[0] = q.offers[1] = Relics.FROST_SHARD;
        q.offers[2] = -1;
        check((int) call(pick, choiceGame, q) == 0, "auto choice not deterministic");

        // Integration: disconnected player is handled before 50-second timeout.
        q.offers[0] = Relics.EMBER_CROWN;
        q.offers[1] = Relics.ICE_BOTTLE;
        q.offers[2] = -1;
        q.chosen = -1;
        q.connected = false;
        choiceGame.soloRun = false;
        choiceGame.phase = Game.CHOICE;
        choiceGame.choiceClock = 49f;
        choiceGame.rewardsLeft = 2;
        int before = q.relics[Relics.ICE_BOTTLE];
        Method update = Game.class.getDeclaredMethod("updateChoice", float.class);
        update.setAccessible(true);
        call(update, choiceGame, 1f / 60f);
        check(q.relics[Relics.ICE_BOTTLE] == before + 1,
                "disconnected player's chosen relic was not actually granted");

        System.out.println("PASS CombatFairnessTest: " + checked
                + " runtime warning states, NaN inputs, and multiplayer auto-choice");
    }
}
