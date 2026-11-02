import com.dilao.core.Game;
import com.dilao.core.OneHandAssist;

/** Pure-core single-thumb assist policy regression; does not require Android UI. */
public final class OneHandAssistTest {
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static Game.Enemy enemy(Game g, float x, float y, boolean boss) {
        Game.Enemy e = new Game.Enemy();
        e.id = g.allocateId();
        e.x = x;
        e.y = y;
        e.hp = e.maxHp = 100;
        e.bossIndex = boss ? 0 : -1;
        g.enemies.add(e);
        return e;
    }
    public static void main(String[] args) {
        Game g = new Game(28012026L);
        Game.Player p = g.addPlayer(1, "单手玩家", 0);
        p.x = 500f;
        p.y = 300f;
        p.ultReady = 1f;
        g.phase = Game.FIGHT;
        check(OneHandAssist.actions(g, p) == 0, "empty room must not waste cooldowns");
        Game.Enemy e = enemy(g, 600f, 300f, false);
        check(OneHandAssist.actions(g, p) == OneHandAssist.SKILL,
                "one nearby enemy should prompt skill, not ultimate");
        p.skillCd = 2f;
        check(OneHandAssist.actions(g, p) == 0, "skill on cooldown must not fire");
        p.skillCd = 0f;
        e.x = 1200f;
        check(OneHandAssist.actions(g, p) == 0, "far enemy must not cause spam");

        e.x = 900f;
        e.bossIndex = 0;
        check(OneHandAssist.actions(g, p) == (OneHandAssist.SKILL | OneHandAssist.ULTIMATE),
                "boss within range should prompt both abilities");
        e.bossIndex = -1;
        g.enemies.clear();
        for (int i = 0; i < 6; i++) enemy(g, 570 + i * 10, 310, false);
        check(OneHandAssist.actions(g, p) == (OneHandAssist.SKILL | OneHandAssist.ULTIMATE),
                "six enemies should trigger crowd ultimate");
        p.ultReady = 0f;
        check(OneHandAssist.actions(g, p) == OneHandAssist.SKILL,
                "unready ultimate cannot be suggested");

        g.enemies.clear();
        // Player registration is legal only in the lobby, even for a test fixture.
        g.phase = Game.LOBBY;
        Game.Player healer = g.addPlayer(2, "星医", 2);
        g.phase = Game.FIGHT;
        healer.x = 500f;
        healer.y = 300f;
        healer.hp = healer.maxHp * .6f;
        healer.skillCd = 0f;
        healer.ultReady = 0f;
        check(OneHandAssist.actions(g, healer) == OneHandAssist.SKILL,
                "support classes should heal wounded allies even in an empty room");
        healer.hp = healer.maxHp;
        p.hp = p.maxHp * .5f;
        check(OneHandAssist.actions(g, healer) == OneHandAssist.SKILL,
                "support should notice another wounded ally");
        p.hp = p.maxHp;
        check(OneHandAssist.actions(g, healer) == 0, "healthy party must not waste heal");
        healer.ultReady = 1f;
        healer.hp = healer.maxHp * .3f;
        check((OneHandAssist.actions(g, healer) & OneHandAssist.ULTIMATE) != 0,
                "critical medic should use a ready defensive ultimate");

        healer.down = true;
        check(OneHandAssist.actions(g, healer) == 0, "downed players cannot act");
        healer.down = false;
        healer.connected = false;
        check(OneHandAssist.actions(g, healer) == 0, "disconnected players cannot act");
        healer.connected = true;
        g.phase = Game.CHOICE;
        check(OneHandAssist.actions(g, healer) == 0, "drafts must not consume skills");
        g.phase = Game.FIGHT;
        check(OneHandAssist.actions(null, healer) == 0
                && OneHandAssist.actions(g, null) == 0, "nulls must be harmless");

        int stable = OneHandAssist.actions(g, healer);
        for (int i = 0; i < 100; i++)
            check(OneHandAssist.actions(g, healer) == stable, "policy must be deterministic");
        System.out.println("PASS OneHandAssistTest: combat/support/empty/disabled/network-safe cases");
    }
}
