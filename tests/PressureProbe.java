import com.dilao.core.*;
import java.util.*;

/**
 * 压力对比探针：契约 × 策略 × 职业 × 种子。
 * <p>输出可复现的 CSV 行；<b>不是</b>对真人胜率的主张。
 *
 * <p>用法：{@code java -cp build\core-quality PressureProbe [seeds=4]}
 */
public final class PressureProbe {
    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        System.out.println("contract,policy,role,seed,result,nodes,time,damage_taken,kills,coins");
        for (int contract = 0; contract < Balance.CONTRACTS.length; contract++)
            for (int policy = 0; policy < 3; policy++)
                for (int role = 0; role < Content.ROLE_COUNT; role++)
                    for (int seed = 0; seed < seeds; seed++) {
                        Game g = new Game(2300 + seed);
                        g.addPlayer(1, "Probe", role);
                        g.startSolo(1, Game.MODE_EXPEDITION);
                        g.contract = contract;
                        for (int frame = 0; frame < 14400
                                && g.phase != Game.WON && g.phase != Game.LOST; frame++) {
                            if (g.phase != Game.FIGHT) BalanceProbe.control(g, frame);
                            else if (frame % 3 == 0) {
                                Game.Player p = g.players.get(0);
                                if (policy == 2) BalanceProbe.control(g, frame);
                                else {
                                    float angle = frame * .01f;
                                    g.input(p.id,
                                            policy == 1 ? (float) Math.cos(angle) : 0,
                                            policy == 1 ? (float) Math.sin(angle) : 0,
                                            1, 0, false, 0, 0);
                                }
                            }
                            g.update(Game.STEP);
                        }
                        String result = g.phase == Game.WON ? "win"
                                : g.phase == Game.LOST ? "loss" : "timeout";
                        System.out.printf(Locale.US, "%d,%d,%d,%d,%s,%d,%.1f,%.1f,%d,%d%n",
                                contract, policy, role, seed, result, g.roomsVisited, g.time,
                                g.damageTaken, g.kills, g.coins);
                    }
    }
}
