import com.dilao.core.*;
import java.util.*;

/**
 * 机制对比探针：固定高血量靶，量 12 秒内的实际输出与反应次数。
 * <p>这是机制验证，<b>不是</b>强度排行。
 *
 * <p>用法：{@code java -cp build\core-quality BuildProbe}
 */
public final class BuildProbe {
    public static void main(String[] args) {
        int[][] builds = {
                {},
                {Relics.FIRE_HEART, Relics.EMBER_CLOAK, Relics.BURST_SEED},
                {Relics.WIND_GEAR, Relics.FEATHER_CLOAK, Relics.STARPIERCER},
                {Relics.FROST_SHARD, Relics.FROST_CROWN, Relics.ICE_BOTTLE},
                {Relics.AMBER_HEART, Relics.REGEN_VINE, Relics.MEDIC_LAMP},
                {Relics.PULSE_COIL, Relics.LIGHTNING_BOX, Relics.BOLT_QUIVER},
                {Relics.VOID_SHARD, Relics.ABYSS_GAZE, Relics.WARP_RING}
        };
        String[] names = {"base", "ember3", "gale3", "frost3", "life3", "volt3", "void3"};
        System.out.println("role,build,scenario,damage,reactions,multishot,interval");
        for (int role = 0; role < Content.ROLE_COUNT; role++)
            for (int build = 0; build < builds.length; build++)
                for (int scenario = 0; scenario < 3; scenario++) {
                    Game g = new Game(733119L);
                    g.addPlayer(1, "Probe", role);
                    g.startSolo(1, Game.MODE_EXPEDITION);
                    g.roomsTotal = 999;
                    g.phase = Game.MAP;
                    g.routeOptions[0] = Game.ROOM_COMBAT;
                    g.routeCount = 1;
                    g.selectRoute(0);
                    g.spawnClock = 10000;
                    g.blocks.clear();
                    g.props.clear();
                    g.hazards.clear();
                    g.restored();
                    Game.Player p = g.players.get(0);
                    p.x = 400;
                    p.y = 310;
                    for (int relic : builds[build]) g.grantRelic(p, relic);
                    int n = scenario == 0 ? 1 : 5;
                    for (int j = 0; j < n; j++) {
                        Game.Enemy e = new Game.Enemy();
                        e.id = g.allocateId();
                        e.type = 0;
                        e.behavior = Enemies.BEHAVIOR[0];
                        e.hp = e.maxHp = 100000;
                        e.speed = 0;
                        e.cooldown = 10000;
                        e.r = Enemies.RADIUS[0];
                        e.stage = 0;
                        e.x = scenario == 0 ? 620 : scenario == 1 ? 570 + j * 52 : 540 + (j % 2) * 37;
                        e.y = scenario == 2 ? 250 + j * 30 : 310;
                        g.enemies.add(e);
                    }
                    for (int i = 0; i < 360; i++) {
                        p.invuln = 1;
                        p.hp = p.maxHp;
                        g.update(Game.STEP);
                    }
                    float damage = 0;
                    for (Game.Enemy e : g.enemies) damage += e.maxHp - e.hp;
                    System.out.printf(Locale.US, "%d,%s,%d,%.1f,%d,%d,%.3f%n",
                            role, names[build], scenario, damage, p.reactions,
                            p.multishot, p.interval);
                }
    }
}
