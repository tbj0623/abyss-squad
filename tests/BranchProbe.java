import com.dilao.core.*;
import java.util.*;

/**
 * 分支经济探针：同一职业 + 三件固定遗物，各跑一次第二站房间，量出真实代价与回报。
 * <p>输出可复现 CSV；不估计稀有度，也不做概率声明。
 *
 * <p>用法：{@code java -cp build\core-quality BranchProbe [seeds=16]}
 */
public final class BranchProbe {
    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 16;
        System.out.println("room_type,seed,result,time,damage_taken,coins,relic_choices");
        int[] types = {Game.ROOM_COMBAT, Game.ROOM_ELITE, Game.ROOM_TREASURE, Game.ROOM_CHALLENGE};
        for (int type : types)
            for (int seed = 0; seed < seeds; seed++) {
                Game g = new Game(7300 + seed);
                g.addPlayer(1, "Probe", 0);
                g.startSolo(1, Game.MODE_EXPEDITION);
                g.contract = 1;
                g.roomsVisited = 4;
                g.room = 4;
                g.biome = 1;
                g.floor = 2;
                g.routeOptions[0] = type;
                g.routeCount = 1;
                g.grantRelic(g.players.get(0), 1);
                g.grantRelic(g.players.get(0), 2);
                g.grantRelic(g.players.get(0), 3);
                int coins = g.coins;
                g.selectRoute(0);
                for (int i = 0; i < 30 * 150 && g.phase == Game.FIGHT; i++) {
                    if (i % 3 == 0) BalanceProbe.control(g, i);
                    g.update(Game.STEP);
                }
                String result = g.phase == Game.CHOICE ? "clear"
                        : g.phase == Game.LOST ? "loss" : "timeout";
                System.out.printf(Locale.US, "%d,%d,%s,%.1f,%.1f,%d,%d%n",
                        type, seed, result, g.time, g.damageTaken, g.coins - coins,
                        g.phase == Game.CHOICE ? g.rewardsLeft : 0);
            }
    }
}
