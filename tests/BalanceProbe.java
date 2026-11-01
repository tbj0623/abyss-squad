import com.dilao.core.*;
import java.util.*;

/**
 * 普通数值的确定性启发式 agent 探针。不构成"人类难度"的证明，只做可复现对比。
 *
 * <p>同时对外提供 {@link #control(Game, int)}，供 {@code PressureProbe} / {@code BranchProbe} 复用。
 * 这个 agent <b>不作弊</b>：不改玩家血量、伤害或遗物上限，只用与玩家相同的输入接口。
 *
 * <p>用法：
 * <pre>
 *   java -cp build\core-quality BalanceProbe           # 默认 900 秒预算
 *   java -cp build\core-quality BalanceProbe 300       # 自定义秒数
 * </pre>
 */
public final class BalanceProbe {

    /** 一帧要做一次决策时的统一入口。 */
    static void control(Game g, int frame) {
        if (g.players.isEmpty()) return;
        Game.Player p = g.players.get(0);
        switch (g.phase) {
            case Game.MAP: {
                int pick = 0;
                for (int i = 0; i < g.routeCount; i++) {
                    int t = g.routeOptions[i];
                    if (t == Game.ROOM_TREASURE) pick = i;
                    if (t == Game.ROOM_CAMP && p.hp < p.maxHp * .7f) pick = i;
                    if (t == Game.ROOM_COMBAT) pick = i;
                }
                g.selectRoute(pick);
                break;
            }
            case Game.CAMP:
                g.camp(p.hp < p.maxHp * .8f ? 0 : 1);
                break;
            case Game.ARMORY:
                g.evolveWeapon(p.role == 1 || p.role == 5 ? 0 : 1);
                break;
            case Game.SHRINE:
                g.shrine(g.coins >= 70 ? 0 : 1);
                break;
            case Game.SHOP: {
                for (int i = 0; i < 3; i++)
                    if (g.shopPrice(i) > 0 && g.shopPrice(i) <= g.coins) g.buyShop(i);
                g.leaveShop();
                break;
            }
            case Game.EVENT:
                if (g.eventChoice < 0) {
                    if (!g.resolveEvent(1)) g.resolveEvent(0);
                } else g.continueEvent();
                break;
            case Game.CHOICE: {
                int selected = 0;
                for (int i = 0; i < 3; i++) {
                    int id = p.offers[i];
                    if (id >= 0 && (p.relics[id] < Relics.CAP[id])
                            && Relics.SCHOOL[id] == Content.SCHOOL[p.role]) selected = i;
                }
                g.choose(p.id, selected, 0);
                break;
            }
            case Game.FIGHT:
                fight(g, p, frame);
                break;
            default:
                g.update(Game.STEP);
                break;
        }
    }

    /** 16 向采样 + 危险规避 + 保持中距离。 */
    private static void fight(Game g, Game.Player p, int frame) {
        float best = -Float.MAX_VALUE, bx = 0, by = 0;
        for (int j = 0; j < 17; j++) {
            double angle = j * Math.PI * 2 / 16;
            float dx = j == 16 ? 0 : (float) Math.cos(angle);
            float dy = j == 16 ? 0 : (float) Math.sin(angle);
            float x = p.x + dx * 65, y = p.y + dy * 65;
            float score = 0;
            if (x < 70 || x > 1050 || y < 75 || y > 545) score -= 500;
            for (Game.Block b : g.blocks)
                if (x > b.x - 25 && x < b.x + b.w + 25 && y > b.y - 25 && y < b.y + b.h + 25) score -= 400;
            float nearest = 999;
            for (Game.Enemy e : g.enemies) {
                if (e.hp <= 0) continue;
                float d = Game.distance(x, y, e.x, e.y);
                nearest = Math.min(nearest, d);
                score -= d < 120 ? (120 - d) * 3 : 0;
                if (e.telegraph > 0 && Game.distance(x, y, e.tx, e.ty) < 105) score -= 230;
            }
            float preferred = p.role == 5 || p.role == 1 ? 180 : 240;
            score -= Math.abs(nearest - preferred) * .18f;
            for (Game.Shot s : g.shots)
                if (s.enemy) {
                    float d = Game.distance(x, y, s.x + s.vx * .3f, s.y + s.vy * .3f);
                    if (d < 65) score -= (65 - d) * 4;
                }
            for (Game.Hazard h : g.hazards)
                if ((h.active > 0 || h.clock < .7f) && Game.distance(x, y, h.x, h.y) < h.r + 25) score -= 180;
            if (g.objective == 1) score -= Game.distance(x, y, 560, 300) * .17f;
            else score -= Game.distance(x, y, 560, 310) * .013f;
            score += dx * p.dx + dy * p.dy;
            if (score > best) {
                best = score;
                bx = dx;
                by = dy;
            }
        }
        int skill = p.skillSerial + (p.skillCd <= 0 && !g.enemies.isEmpty()
                && (p.role != 2 || p.hp < p.maxHp * .7f) ? 1 : 0);
        int dash = p.dashSerial + (p.dashCd <= 0 && best < -160 ? 1 : 0);
        if (p.ultReady >= 1f) g.ultimate(p.id);
        g.input(p.id, bx, by, 1, 0, false, dash, skill);
    }

    public static void main(String[] args) {
        int maxTime = args.length > 0 ? Integer.parseInt(args[0]) : 900;
        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            int wins = 0, totalNodes = 0;
            float totalTime = 0;
            int seeds = 3;
            for (int seed = 0; seed < seeds; seed++) {
                Game g = new Game(51 + seed);
                g.addPlayer(1, "Bot", role);
                g.startSolo(1, Game.MODE_EXPEDITION);
                for (int frame = 0; frame < maxTime * 30
                        && g.phase != Game.WON && g.phase != Game.LOST; frame++) {
                    if (frame % 3 == 0 || g.phase != Game.FIGHT) control(g, frame);
                    g.update(Game.STEP);
                }
                if (g.phase == Game.WON) wins++;
                totalNodes += g.roomsVisited;
                totalTime += g.time;
                System.out.printf(Locale.US,
                        "role=%d seed=%d phase=%d nodes=%d kills=%d hp=%.1f time=%.1f enemies=%d%n",
                        role, seed, g.phase, g.roomsVisited, g.kills,
                        g.players.get(0).hp, g.time, g.enemies.size());
            }
            System.out.printf(Locale.US, "SUMMARY role=%d wins=%d/%d meanNodes=%.1f meanTime=%.1f%n",
                    role, wins, seeds, totalNodes / (float) seeds, totalTime / seeds);
        }
    }
}
