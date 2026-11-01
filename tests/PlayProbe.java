import com.dilao.core.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 自动策略探针：用带人类缺陷的 bot 跑完整远征，输出胜率表、存活曲线与 CSV。
 *
 * <p><b>为什么不是"完美 bot"</b>：完美走位 + 完美技能会得到远高于真人的胜率，
 * 用它调数值等于拟合一个不存在的玩家。本探针刻意注入四类人类缺陷：
 * <ol>
 *   <li><b>反应延迟</b> 180~320 ms：危险区出现后不会立刻转向，而是先沿旧路线走完延迟。</li>
 *   <li><b>随机漂移</b>：走位向量叠加 ±15% 的随机偏角，不会贴着理论最优圆轨道跑。</li>
 *   <li><b>换向抖动</b>：每 2~4 秒随机换一次绕行方向。</li>
 *   <li><b>技能漏放</b>：技能/闪避/终极都有条件与 10% 的漏放率。</li>
 * </ol>
 *
 * <p><b>三档选牌策略</b>（{@link #STRAT_GREEDY} / {@link #STRAT_SCHOOL} / {@link #STRAT_BEST}）：
 * 分别代表"只看第一张"的下界、"补主流派"的真人常见行为、以及按乘区估算的上界。
 * <b>主指标用流派向</b>。
 *
 * <p><b>用法</b>
 * <pre>
 *   java -cp build\probe PlayProbe                 # 3 种子 × 5 契约 × 12 职业 × 三档策略
 *   java -cp build\probe PlayProbe 3 5 2           # 3 种子 × 5 契约，只跑流派向
 *   java -cp build\probe PlayProbe 2 5 1 0,5,10    # 指定职业
 * </pre>
 * 第 3 个参数：0=三档全跑（默认），1=贪心，2=流派向，3=最优。
 *
 * <p>CSV 写入 {@code build/qa6/}：probe-balance / probe-role / probe-progress。
 * <p>退出码：0 正常；1 出现崩溃 / NaN / 实体超限 / 死循环。
 */
public final class PlayProbe {

    // ══════════════════════════════════════════════════════════════
    //  配置
    // ══════════════════════════════════════════════════════════════

    static final int MAX_TICKS = 90_000;
    static final long MAX_WALL_MS = 25_000L;
    static final int MOVE_RECALC = 2;

    /** 选牌策略。 */
    static final int STRAT_GREEDY = 1, STRAT_SCHOOL = 2, STRAT_BEST = 3;
    static final String[] STRAT_NAME = {"", "贪心(取第1张)", "流派向(主指标)", "最优(按乘区)"};

    /** 人类缺陷参数（按质量档位取）。 */
    static final int Q_SLOPPY = 0, Q_HUMAN = 1, Q_CLEAN = 2;
    static int quality = Q_HUMAN;
    static float reactMin = .10f, reactMax = .18f;
    static float drift = .05f;
    static float skipChance = .05f;
    static float turnMin = 3f, turnMax = 6f;

    static void applyQuality(int q) {
        quality = q;
        switch (q) {
            case Q_SLOPPY:
                reactMin = .25f; reactMax = .42f; drift = .22f; skipChance = .22f; turnMin = 1.5f; turnMax = 3f;
                break;
            case Q_CLEAN:
                reactMin = 0f; reactMax = 0f; drift = 0f; skipChance = 0f; turnMin = 99f; turnMax = 99f;
                break;
            default:
                reactMin = .10f; reactMax = .18f; drift = .05f; skipChance = .05f; turnMin = 3f; turnMax = 6f;
                break;
        }
    }

    static final int CONTRACTS = 5;
    static final int ROLES = Content.ROLE_COUNT;
    /** 远征 15 站，索引 0..15 表示"到达第 n 站"。 */
    static final int STATIONS = 16;

    // ══════════════════════════════════════════════════════════════
    //  统计（按策略分开）
    // ══════════════════════════════════════════════════════════════

    static final class Tally {
        int runs, wins, losses, timeouts, errors;
        final int[] cRuns = new int[CONTRACTS], cWins = new int[CONTRACTS];
        final int[] rRuns = new int[ROLES], rWins = new int[ROLES];
        final int[] reached = new int[STATIONS + 1];   // 到达 >= n 站的局数
        final int[] deathAt = new int[STATIONS + 1];   // 死在第 n 站
        long sumTicks, sumWinTicks;
        int sumLevel, sumRelics, sumFloor;
        int evoRuns, evoTotal;
        final List<String> csv = new ArrayList<>();
    }

    static final Tally[] tally = new Tally[]{new Tally(), new Tally(), new Tally(), new Tally()};
    static int[] roles = allRoles();
    static int seeds = 3;

    public static void main(String[] args) {
        seeds = args.length > 0 ? clampInt(args[0], 1, 50, 3) : 3;
        int contracts = args.length > 1 ? clampInt(args[1], 1, CONTRACTS, CONTRACTS) : CONTRACTS;
        int mode = args.length > 2 ? clampInt(args[2], 0, 3, 0) : 0;
        roles = args.length > 3 ? parseRoles(args[3]) : allRoles();
        int[] strategies = mode == 0
                ? new int[]{STRAT_GREEDY, STRAT_SCHOOL, STRAT_BEST}
                : new int[]{mode};

        System.out.println("# AbyssSquad PlayProbe v2 — human-imperfect bot (reaction delay, drift, miss chance)");
        System.out.println("# seeds=" + seeds + " contracts=" + contracts + " roles=" + roles.length
                + " strategies=" + describe(strategies) + " maxTicks=" + MAX_TICKS);
        System.out.println("strategy,contract,role,seed,result,rooms,kills,level,relics,coins,duration_ticks,bosses,evolutions");

        long started = System.currentTimeMillis();
        for (int strategy : strategies) {
            for (int contract = 0; contract < contracts; contract++) {
                for (int role : roles) {
                    for (int seed = 1; seed <= seeds; seed++) {
                        runOne(strategy, contract, role, seed);
                    }
                }
            }
        }
        long elapsed = System.currentTimeMillis() - started;

        for (int strategy : strategies) {
            for (String line : tally[strategy].csv) System.out.println(line);
        }

        System.out.println();
        for (int strategy : strategies) printSummary(strategy, contracts, elapsed);

        // 主指标：流派向。CSV 只导出主指标，避免下游误用下界/上界。
        Tally main = tally[STRAT_SCHOOL];
        if (main.runs == 0 && strategies.length > 0) main = tally[strategies[0]];
        writeCsv(main, contracts);

        int errors = 0;
        for (int s = 1; s <= 3; s++) errors += tally[s].errors;
        System.out.println();
        System.out.println(errors == 0
                ? "ALL RUNS COMPLETED WITHOUT CRASH / NaN / OVERFLOW"
                : errors + " RUNS FAILED");
        if (errors > 0) System.exit(1);
    }

    // ══════════════════════════════════════════════════════════════
    //  单局
    // ══════════════════════════════════════════════════════════════

    static void runOne(int strategy, int contract, int role, int seed) {
        Tally t = tally[strategy];
        t.runs++;
        t.cRuns[contract]++;
        t.rRuns[role]++;

        long runSeed = (seed * 1_000_003L) ^ (contract * 7919L) ^ (role * 104_729L) ^ (strategy * 31_337L);
        Game g = new Game(runSeed);
        Game.Player p = g.addPlayer(1, "probe", role);
        if (p == null) { fail(strategy, contract, role, seed, "addPlayer null"); return; }
        g.contract = contract;
        if (!g.startSolo(1, Game.MODE_EXPEDITION)) {
            fail(strategy, contract, role, seed, "startSolo false");
            return;
        }

        Random rnd = new Random(runSeed ^ 0x5DEECE66DL);
        int dashSerial = 0, skillSerial = 0;
        int dir = rnd.nextBoolean() ? 1 : -1;
        float moveX = 0f, moveY = 0f;
        float frozenX = 0f, frozenY = 0f;
        float reaction = 0f;              // 剩余反应延迟（秒）
        float turnClock = turnMin + rnd.nextFloat() * (turnMax - turnMin);
        boolean dangerLastFrame = false;
        float stuckX = p.x, stuckY = p.y;
        float phaseCurve = rnd.nextFloat() * 6.28f;
        int stuckFrames = 0;
        long wallStart = System.currentTimeMillis();
        String result = "timeout";

        try {
            for (int tick = 0; tick < MAX_TICKS; tick++) {
                switch (g.phase) {
                    case Game.MAP:
                        if (!g.selectRoute(pickRoute(g, rnd))) {
                            fail(strategy, contract, role, seed, "selectRoute failed at room " + g.roomsVisited);
                            return;
                        }
                        break;

                    case Game.CHOICE: {
                        // 进化要消耗一次抽牌（offers 被清空），所以真人也只会在关键节点换：
                        // 第一次在 Lv5+，第二次在 Lv8+，之后回去抽遗物。全部换等于用 10 件遗物换 2 次进化。
                        boolean tookEvo = false;
                        for (Game.Player q : g.players) {
                            if (q.chosen >= 0) continue;
                            boolean wantEvo = (q.evolutionsTaken == 0 && g.level >= 5)
                                    || (q.evolutionsTaken == 1 && g.level >= 8);
                            if (wantEvo && rnd.nextFloat() < .70f && g.switchToEvolution(q.id)) {
                                tookEvo = true;
                            }
                        }
                        for (Game.Player q : g.players) {
                            if (q.chosen >= 0) continue;
                            int card = tookEvo ? 0 : pickCard(g, q, strategy, rnd);
                            g.choose(q.id, card, 0);
                        }
                        g.update(Game.STEP);
                        break;
                    }

                    case Game.EVENT: {
                        if (g.eventChoice < 0) {
                            int want = pickEventChoice(g, rnd);
                            if (!g.resolveEvent(want) && !g.resolveEvent(1 - want)) g.update(Game.STEP);
                        } else if (!g.continueEvent()) {
                            g.update(Game.STEP);
                        }
                        break;
                    }

                    case Game.SHOP:
                        for (int i = 0; i < 3; i++) {
                            // 只买买得起的，且留下 40 备用金（真人不会花光）
                            if (g.shopPrice(i) >= 0 && g.coins - g.shopPrice(i) >= 40) g.buyShop(i);
                        }
                        g.leaveShop();
                        break;

                    case Game.CAMP:
                        // 残血休息，半血以上锻造，健康时偶尔去探索
                        if (p.hp < p.maxHp * .55f) g.camp(0);
                        else if (p.hp < p.maxHp * .85f) g.camp(1);
                        else g.camp(rnd.nextFloat() < .5f ? 1 : 2);
                        break;

                    case Game.ARMORY:
                        g.evolveWeapon(pickLine(g, p, rnd));
                        break;

                    case Game.SHRINE:
                        g.shrine(g.coins >= 70 && rnd.nextFloat() < .8f ? 0 : 1);
                        break;

                    case Game.FIGHT: {
                        // ── 反应延迟 ──────────────────────────────────
                        boolean danger = hasDanger(g, p);
                        if (danger && !dangerLastFrame) {
                            reaction = reactMin + rnd.nextFloat() * (reactMax - reactMin);
                        }
                        dangerLastFrame = danger;
                        reaction = Math.max(0f, reaction - Game.STEP);

                        if (tick % MOVE_RECALC == 0) {
                            float[] mv = steer(g, p, tick, dir, phaseCurve);
                            frozenX = mv[0];
                            frozenY = mv[1];
                            // 漂移：人类走位不是精确的最优向量
                            float ang = (float) Math.atan2(frozenY, frozenX)
                                    + (rnd.nextFloat() - .5f) * drift;
                            moveX = (float) Math.cos(ang);
                            moveY = (float) Math.sin(ang);
                        }
                        if (reaction > 0f) {
                            // 延迟期内沿旧方向继续走：这是"没反应过来"的真实代价
                            moveX = frozenX;
                            moveY = frozenY;
                        }

                        // ── 换向抖动：每 2~4 秒随机换绕行方向 ─────────
                        turnClock -= Game.STEP;
                        if (turnClock <= 0f) {
                            if (rnd.nextFloat() < .5f) dir = -dir;
                            phaseCurve = rnd.nextFloat() * 6.28f;
                            turnClock = turnMin + rnd.nextFloat() * (turnMax - turnMin);
                        }

                        // ── 闪避：血量 <50% 或近身 3 个以上敌人 ───────
                        int near = enemiesWithin(g, p, 120f);
                        boolean wantDash = (p.hp < p.maxHp * .50f || near >= 3)
                                && rnd.nextFloat() > skipChance;
                        if (wantDash && p.dashCd <= 0) dashSerial++;

                        // ── 技能：冷却好且附近有敌人 ─────────────────
                        if (p.skillCd <= 0 && near >= 1 && rnd.nextFloat() > skipChance) skillSerial++;

                        // ── 终极：能量满 且（首领房 或 附近 6+ 敌人）──
                        if (p.ultReady >= 1f && (g.roomType == Game.ROOM_BOSS || near >= 6)) {
                            g.ultimate(p.id);
                        }

                        g.input(p.id, moveX, moveY, 0f, 0f, false, dashSerial, skillSerial);
                        g.update(Game.STEP);

                        // 卡住检测：真被地形卡住时换向（不算作弊，真人也会这么做）
                        if (tick % 10 == 0) {
                            if (!p.down && Game.distance(p.x, p.y, stuckX, stuckY) < 6f) {
                                if (++stuckFrames > 3) { dir = -dir; stuckFrames = 0; phaseCurve += 1.7f; }
                            } else stuckFrames = 0;
                            stuckX = p.x;
                            stuckY = p.y;
                        }
                        break;
                    }

                    case Game.WON: result = "win"; break;
                    case Game.LOST: result = "lose"; break;
                    default: g.update(Game.STEP); break;
                }

                if (g.phase == Game.WON || g.phase == Game.LOST) break;
                if (tick % 15 == 0) checkInvariants(g);
                if ((tick & 255) == 0 && System.currentTimeMillis() - wallStart > MAX_WALL_MS) {
                    result = "timeout";
                    break;
                }
            }
        } catch (Throwable ex) {
            t.errors++;
            System.out.println("ERROR strategy=" + strategy + " contract=" + contract + " role=" + role
                    + " seed=" + seed + " : " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            return;
        }

        if ("timeout".equals(result) && g.phase == Game.WON) result = "win";
        if ("timeout".equals(result) && g.phase == Game.LOST) result = "lose";

        int rooms = g.roomsVisited;
        if ("win".equals(result)) {
            t.wins++;
            t.cWins[contract]++;
            t.rWins[role]++;
            t.sumWinTicks += g.tick;
        } else if ("lose".equals(result)) {
            t.losses++;
            t.deathAt[Math.min(STATIONS, rooms)]++;
        } else {
            t.timeouts++;
        }
        t.sumTicks += g.tick;
        t.sumLevel += g.level;
        t.sumRelics += g.relicCount(p);
        t.sumFloor += g.floor;
        t.evoTotal++;
        if (p.evolutionsTaken > 0) t.evoRuns++;
        for (int n = 0; n <= STATIONS; n++) if (rooms >= n) t.reached[n]++;

        t.csv.add(strategy + "," + contract + "," + role + "," + seed + "," + result + ","
                + rooms + "," + g.kills + "," + g.level + "," + g.relicCount(p) + "," + g.coins + ","
                + g.tick + "," + g.bossesDefeated + "," + p.evolutionsTaken);
    }

    // ══════════════════════════════════════════════════════════════
    //  走位
    // ══════════════════════════════════════════════════════════════

    static float[] steer(Game g, Game.Player p, int tick, int dir, float phaseCurve) {
        float cx = Game.WIDTH * .5f, cy = Game.HEIGHT * .5f;

        // 挑战房「星环充能」必须站进中央圈；绕圈走位永远达不成目标。
        if (g.roomType == Game.ROOM_CHALLENGE && g.objective == 1) {
            float dx0 = cx - p.x, dy0 = cy - p.y;
            float d0 = (float) Math.hypot(dx0, dy0);
            if (d0 > 40f) return new float[]{dx0 / Math.max(.01f, d0), dy0 / Math.max(.01f, d0)};
            float ex = 0f, ey = 0f;
            for (Game.Enemy e : g.enemies) {
                if (e.hp <= 0) continue;
                float d = Game.distance(p.x, p.y, e.x, e.y);
                if (d > 110f || d < .01f) continue;
                float w = (110f - d) / 110f;
                ex += (p.x - e.x) / d * w;
                ey += (p.y - e.y) / d * w;
            }
            float l = (float) Math.hypot(ex, ey);
            return l < 1e-3f ? new float[]{0f, 0f} : new float[]{ex / l, ey / l};
        }

        float radius = g.roomType == Game.ROOM_BOSS ? 250f : 205f;
        float phase = tick * .0115f * dir + phaseCurve;
        float wantX = cx + (float) Math.cos(phase) * radius;
        float wantY = cy + (float) Math.sin(phase) * radius * .74f;

        float dx = wantX - p.x, dy = wantY - p.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) { dx = 1f; dy = -1f; len = 1.414f; }
        dx /= len;
        dy /= len;

        for (Game.Hazard h : g.hazards) {
            if (h.active <= 0) continue;
            float d = Game.distance(p.x, p.y, h.x, h.y);
            float safe = h.r + 78f;
            if (d < safe && d > .01f) {
                float w = (safe - d) / safe;
                dx += (p.x - h.x) / d * w * 2.6f;
                dy += (p.y - h.y) / d * w * 2.6f;
            }
        }

        float ex = 0f, ey = 0f;
        int counted = 0;
        for (Game.Enemy e : g.enemies) {
            if (e.hp <= 0) continue;
            float d = Game.distance(p.x, p.y, e.x, e.y);
            if (d > 130f || d < .01f) continue;
            float w = (130f - d) / 130f;
            ex += (p.x - e.x) / d * w;
            ey += (p.y - e.y) / d * w;
            if (++counted >= 8) break;
        }
        dx += ex * 1.25f;
        dy += ey * 1.25f;

        // ── 弹幕规避：真人最重要的一项技能就是"看到子弹就挪" ──────────
        // 只躲 2 秒内会命中自己的子弹（用最近点/方向做近似），不预判反弹与追踪。
        float bx = 0f, by = 0f;
        int bullets = 0;
        for (Game.Shot s : g.shots) {
            if (!s.enemy) continue;
            if (bullets >= 12) break;
            float d = Game.distance(p.x, p.y, s.x, s.y);
            if (d > 120f || d < 1f) continue;
            // 子弹是否朝我飞：速度向量与"我-子弹"夹角为锐角
            float toX = p.x - s.x, toY = p.y - s.y;
            float vlen = (float) Math.hypot(s.vx, s.vy);
            if (vlen < 1f) continue;
            float dot = (toX * s.vx + toY * s.vy) / (d * vlen);
            if (dot < .35f) continue;          // 没朝我来，不用躲
            // 垂直于子弹方向横向闪避（比正面后退更有效）
            float px2 = -s.vy / vlen, py2 = s.vx / vlen;
            // 选两侧中离我当前位置"更顺"的一侧
            float side = (px2 * dx + py2 * dy) >= 0 ? 1f : -1f;
            float w = (120f - d) / 120f;
            bx += px2 * side * w;
            by += py2 * side * w;
            bullets++;
        }
        dx += bx * 2.2f;
        dy += by * 2.2f;

        final float margin = 120f;
        if (p.x < margin) dx += (margin - p.x) / margin * 1.8f;
        if (p.x > Game.WIDTH - margin) dx -= (p.x - (Game.WIDTH - margin)) / margin * 1.8f;
        if (p.y < margin) dy += (margin - p.y) / margin * 1.8f;
        if (p.y > Game.HEIGHT - margin) dy -= (p.y - (Game.HEIGHT - margin)) / margin * 1.8f;

        len = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(len) || len < 1e-4f) return new float[]{1f, 0f};
        return new float[]{dx / len, dy / len};
    }

    /** 当前是否处于"需要转向"的危险状态（危险区激活 / 敌人贴近 / 弹幕逼近）。 */
    static boolean hasDanger(Game g, Game.Player p) {
        for (Game.Hazard h : g.hazards) {
            if (h.active > 0 && Game.distance(p.x, p.y, h.x, h.y) < h.r + 95f) return true;
        }
        for (Game.Enemy e : g.enemies) {
            if (e.hp <= 0) continue;
            if (e.chargeTime > 0 && Game.distance(p.x, p.y, e.x, e.y) < 190f) return true;
            if (Game.distance(p.x, p.y, e.x, e.y) < 105f) return true;
        }
        for (Game.Shot s : g.shots) {
            if (!s.enemy) continue;
            if (Game.distance(p.x, p.y, s.x, s.y) < 130f) return true;
        }
        return false;
    }

    static int enemiesWithin(Game g, Game.Player p, float r) {
        int n = 0;
        for (Game.Enemy e : g.enemies) {
            if (e.hp <= 0) continue;
            if (Game.distance(p.x, p.y, e.x, e.y) <= r) n++;
        }
        return n;
    }

    // ══════════════════════════════════════════════════════════════
    //  决策
    // ══════════════════════════════════════════════════════════════

    static int pickRoute(Game g, Random rnd) {
        Game.Player p = g.players.get(0);
        boolean early = g.roomsVisited == 0;
        boolean hurt = p.hp < p.maxHp * .55f;
        int best = 0, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < g.routeCount; i++) {
            int type = g.routeOptions[i];
            int score = routeScore(type);
            // 首战没有构筑，硬闯精英/挑战就是送；真人也这么选。
            if (early && (type == Game.ROOM_ELITE || type == Game.ROOM_CHALLENGE)) score -= 2000;
            if (hurt && (type == Game.ROOM_ELITE || type == Game.ROOM_CHALLENGE)) score -= 800;
            if (p.hp < p.maxHp * .35f && type == Game.ROOM_CAMP) score = 3000;
            score += rnd.nextInt(60);   // 轻微随机：真人不会每次选同一个
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    static int routeScore(int room) {
        switch (room) {
            case Game.ROOM_BOSS: return 900;
            case Game.ROOM_ELITE: return 800;
            case Game.ROOM_COMBAT: return 700;
            case Game.ROOM_CHALLENGE: return 600;
            case Game.ROOM_TREASURE: return 500;
            case Game.ROOM_ARMORY: return 450;
            case Game.ROOM_SHRINE: return 400;
            case Game.ROOM_EVENT: return 300;
            case Game.ROOM_SHOP: return 200;
            case Game.ROOM_CAMP: return 100;
            default: return 0;
        }
    }

    /** 三档选牌。 */
    static int pickCard(Game g, Game.Player p, int strategy, Random rnd) {
        int bestCard = 0;
        if (strategy == STRAT_GREEDY) {
            bestCard = 0;
        } else if (strategy == STRAT_SCHOOL) {
            int main = Content.SCHOOL[p.role];
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                int id = p.offers[i];
                if (id < 0) continue;
                int score = 0;
                if (Relics.SCHOOL[id] == main) score += 100;
                // 已持有同流派件数越多，越值得补到 3 件共鸣
                if (g.schoolCount(p, Relics.SCHOOL[id]) >= 2) score += 30;
                if (p.relics[id] >= Relics.CAP[id]) score -= 200;
                if (score > bestScore) { bestScore = score; bestCard = i; }
            }
        } else {
            float bestScore = -Float.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                int id = p.offers[i];
                if (id < 0) continue;
                if (p.relics[id] >= Relics.CAP[id]) continue;
                float score = Relics.POWER[id] * 1.0f + Relics.RATE[id] * .9f
                        + Relics.AREA[id] * .5f + Relics.SPEEDVAL[id] * .4f;
                if (Relics.SCHOOL[id] == Content.SCHOOL[p.role]) score += .03f;
                if (score > bestScore) { bestScore = score; bestCard = i; }
            }
        }
        return Math.max(0, Math.min(2, bestCard));
    }

    /** 铁匠铺：优先选没拿过的线，否则选 POWER 最高的。 */
    static int pickLine(Game g, Game.Player p, Random rnd) {
        int best = 0;
        float bestScore = -Float.MAX_VALUE;
        for (int line = 0; line < 3; line++) {
            int idx = Evolutions.index(p.role, line);
            float score = Evolutions.POWER[idx] + Evolutions.RATE[idx] * .8f;
            if (p.evolved[idx]) score -= 10f;
            if (score > bestScore) { bestScore = score; best = line; }
        }
        if (rnd.nextFloat() < .15f) best = rnd.nextInt(3);   // 真人会点错
        return best;
    }

    /**
     * 事件二选一的 bot 策略，按 {@code Events.TYPE} 分派。
     *
     * <p>这套策略要覆盖全部 13 种类型（0..12）。事件表从 8 种扩到 13 种之后，
     * 旧表只认识 0..6，7..12 全部落进 {@code default: return 1} ——
     * 不影响正确性（任何选项都能推进），但 bot 会无脑选 B，
     * 让「流派向」这档主指标的胜率被一串无脑决策带偏。
     *
     * <p>判定原则统一为「这笔交易在当前血量/星尘下是否划算」：
     * 付血的类型看血线，付钱的类型看星尘是否够且留够 40 备用金。
     */
    static int pickEventChoice(Game g, Random rnd) {
        Game.Player p = g.players.get(0);
        int type = Events.TYPE[g.eventType];
        int aCost = Events.COST_A[g.eventType];
        float hpF = p.maxHp <= 0 ? 0f : p.hp / p.maxHp;
        switch (type) {
            case 1: case 2: case 5:              // 献金 / 预言 / 医疗：付星尘
                return g.coins >= aCost + 40 ? 0 : 1;
            case 0: case 3:                      // 献血 / 双面祭坛：付固定生命
                return hpF > .72f ? 0 : 1;
            case 4:                              // 锻造：付钱换强度
                return hpF < .80f && g.coins >= aCost ? 0 : 1;
            case 6:                              // 诅咒：付生命换遗物
                return hpF > .78f ? 0 : 1;
            case 8:                              // 风险累积：血厚才叠债
                return hpF > .80f ? 0 : 1;
            case 9:                              // 延迟结算：白拿，血厚才签
                return hpF > .65f ? 0 : 1;
            case 10:                             // 二选一赌注：血厚选高赔率 A
                return hpF > .70f ? 0 : 1;
            case 11:                             // 交换：身上有遗物才换
                return g.relicCount(p) > 0 ? 0 : 1;
            case 12:                             // 反向代价：设计上 PA < PC，A 更便宜
                return 0;
            default:                             // 7 纯诅咒等：能不签就不签
                return 1;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  不变量
    // ══════════════════════════════════════════════════════════════

    static void checkInvariants(Game g) {
        if (g.enemies.size() > Game.MAX_ENEMIES) throw new IllegalStateException("敌人超限");
        if (g.shots.size() > Game.MAX_SHOTS) throw new IllegalStateException("弹丸超限");
        if (g.gems.size() > Game.MAX_GEMS) throw new IllegalStateException("宝石超限");
        if (g.effects.size() > Game.MAX_EFFECTS) throw new IllegalStateException("特效超限");
        for (Game.Player p : g.players) {
            finite(p.x, "player.x"); finite(p.y, "player.y");
            finite(p.hp, "player.hp"); finite(p.maxHp, "player.maxHp");
            finite(p.damage, "player.damage"); finite(p.interval, "player.interval");
            finite(p.speed, "player.speed");
            if (p.hp > p.maxHp + 1f) throw new IllegalStateException("生命超上限");
            if (p.interval <= 0f) throw new IllegalStateException("攻击间隔非正");
        }
        for (Game.Enemy e : g.enemies) {
            finite(e.x, "enemy.x"); finite(e.y, "enemy.y");
            finite(e.hp, "enemy.hp"); finite(e.maxHp, "enemy.maxHp");
        }
        for (Game.Shot s : g.shots) {
            finite(s.x, "shot.x"); finite(s.y, "shot.y"); finite(s.damage, "shot.damage");
        }
    }

    static void finite(float v, String what) {
        if (!Float.isFinite(v)) throw new IllegalStateException(what + " 非有限值: " + v);
    }

    // ══════════════════════════════════════════════════════════════
    //  汇总与 CSV
    // ══════════════════════════════════════════════════════════════

    static void printSummary(int strategy, int contracts, long elapsedMs) {
        Tally t = tally[strategy];
        int finished = t.wins + t.losses + t.timeouts;
        System.out.println("# ── 策略 " + STRAT_NAME[strategy] + " ──");
        System.out.printf(Locale.US,
                "# runs=%d win=%d lose=%d timeout=%d error=%d winrate=%.1f%% avgTicks=%.0f avgRooms=%.1f avgLevel=%.1f avgRelics=%.1f evoRate=%.0f%% wall=%.1fs%n",
                t.runs, t.wins, t.losses, t.timeouts, t.errors,
                finished == 0 ? 0.0 : t.wins * 100.0 / finished,
                finished == 0 ? 0.0 : t.sumTicks / (double) finished,
                finished == 0 ? 0.0 : stationSum(t) / (double) finished,
                finished == 0 ? 0.0 : t.sumLevel / (double) finished,
                finished == 0 ? 0.0 : t.sumRelics / (double) finished,
                t.evoTotal == 0 ? 0.0 : t.evoRuns * 100.0 / t.evoTotal,
                elapsedMs / 1000.0);

        System.out.println("# 契约胜率");
        for (int c = 0; c < contracts; c++) {
            if (t.cRuns[c] == 0) continue;
            System.out.printf(Locale.US, "# c%d,%s,%d,%d,%.1f%%%n",
                    c, Balance.CONTRACTS[c], t.cRuns[c], t.cWins[c], t.cWins[c] * 100.0 / t.cRuns[c]);
        }

        System.out.println("# 职业胜率（降序）");
        List<int[]> order = new ArrayList<>();
        for (int r : roles) if (t.rRuns[r] > 0) order.add(new int[]{r, t.rWins[r] * 1000 / Math.max(1, t.rRuns[r])});
        order.sort((a, b) -> b[1] - a[1]);
        for (int[] pair : order) {
            int r = pair[0];
            System.out.printf(Locale.US, "# r%d,%s,%d,%d,%.1f%%%n",
                    r, Content.ROLES[r], t.rRuns[r], t.rWins[r], t.rWins[r] * 100.0 / t.rRuns[r]);
        }

        // 存活曲线
        System.out.println("# 第 N 站存活率");
        StringBuilder sb = new StringBuilder("# ");
        for (int n = 0; n <= 15; n++) {
            sb.append(String.format(Locale.US, "%d:%.0f%% ", n,
                    t.runs == 0 ? 0.0 : t.reached[n] * 100.0 / t.runs));
        }
        System.out.println(sb.toString());
        if (t.wins > 0) {
            System.out.printf(Locale.US, "# 平均通关 %.0f 帧（%.1f 分钟游戏内时间）%n",
                    t.sumWinTicks / (double) t.wins, t.sumWinTicks / (double) t.wins / 30.0 / 60.0);
        }
        System.out.println();
    }

    static int stationSum(Tally t) {
        int sum = 0;
        for (int n = 1; n <= STATIONS; n++) sum += t.reached[n];
        return sum;
    }

    static void writeCsv(Tally t, int contracts) {
        File dir = new File("build/qa6");
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("# WARN 无法创建 build/qa6，CSV 未写出");
            return;
        }
        int finished = t.wins + t.losses + t.timeouts;
        try (PrintWriter w = new PrintWriter(new File(dir, "probe-balance.csv"), "UTF-8")) {
            w.println("contract,name,runs,wins,winrate_pct");
            for (int c = 0; c < contracts; c++) {
                if (t.cRuns[c] == 0) continue;
                w.printf(Locale.US, "%d,%s,%d,%d,%.1f%n",
                        c, Balance.CONTRACTS[c], t.cRuns[c], t.cWins[c], t.cWins[c] * 100.0 / t.cRuns[c]);
            }
        } catch (IOException e) {
            System.out.println("# WARN probe-balance.csv 写入失败: " + e.getMessage());
        }
        try (PrintWriter w = new PrintWriter(new File(dir, "probe-role.csv"), "UTF-8")) {
            w.println("role,name,runs,wins,winrate_pct,winrate_expedition_pct");
            for (int r : roles) {
                if (t.rRuns[r] == 0) continue;
                w.printf(Locale.US, "%d,%s,%d,%d,%.1f,%.1f%n",
                        r, Content.ROLES[r], t.rRuns[r], t.rWins[r],
                        t.rWins[r] * 100.0 / t.rRuns[r], contractRate(t, r, 1));
            }
        } catch (IOException e) {
            System.out.println("# WARN probe-role.csv 写入失败: " + e.getMessage());
        }
        try (PrintWriter w = new PrintWriter(new File(dir, "probe-progress.csv"), "UTF-8")) {
            w.println("station,survival_pct,death_count");
            for (int n = 0; n <= 15; n++) {
                w.printf(Locale.US, "%d,%.1f,%d%n", n,
                        t.runs == 0 ? 0.0 : t.reached[n] * 100.0 / t.runs,
                        n < t.deathAt.length ? t.deathAt[n] : 0);
            }
        } catch (IOException e) {
            System.out.println("# WARN probe-progress.csv 写入失败: " + e.getMessage());
        }
        System.out.println("# CSV 已写入 build/qa6/（probe-balance / probe-role / probe-progress），总局数 " + finished);
    }

    /** 指定契约下某职业的胜率（需要按契约×职业的联合统计）。 */
    static double contractRate(Tally t, int role, int contract) {
        int runs = 0, wins = 0;
        for (String line : t.csv) {
            String[] f = line.split(",");
            if (f.length < 5) continue;
            if (Integer.parseInt(f[1]) != contract || Integer.parseInt(f[2]) != role) continue;
            runs++;
            if ("win".equals(f[4])) wins++;
        }
        return runs == 0 ? 0.0 : wins * 100.0 / runs;
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    static int[] allRoles() {
        int[] r = new int[ROLES];
        for (int i = 0; i < ROLES; i++) r[i] = i;
        return r;
    }

    static int[] parseRoles(String s) {
        String[] parts = s.split(",");
        List<Integer> list = new ArrayList<>();
        for (String part : parts) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v >= 0 && v < ROLES) list.add(v);
            } catch (NumberFormatException ignored) {
                // 忽略非法输入
            }
        }
        if (list.isEmpty()) return allRoles();
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    static String describe(int[] strategies) {
        StringBuilder sb = new StringBuilder();
        for (int s : strategies) {
            if (sb.length() > 0) sb.append('+');
            sb.append(STRAT_NAME[s]);
        }
        return sb.toString();
    }

    static int clampInt(String raw, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static void fail(int strategy, int contract, int role, int seed, String why) {
        tally[strategy].errors++;
        System.out.println("ERROR strategy=" + strategy + " contract=" + contract + " role=" + role
                + " seed=" + seed + " : " + why);
    }
}
