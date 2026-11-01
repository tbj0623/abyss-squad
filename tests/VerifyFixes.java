import com.dilao.core.*;

/**
 * 回归验证：核心模块报告的 bug 是否真的修好了。
 * <p>关键原则：只走**真实游戏路径**（营地、升级、事件、存读档），
 * 不直接改裸字段——直接改字段会绕过增量登记，测出来的结论没有意义。
 */
public final class VerifyFixes {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name); }
        else { fail++; System.out.println("FAIL  " + name + "  -> " + detail); }
    }

    static Game fresh(long seed, int mode) {
        Game g = new Game(seed);
        Game.Player p0 = g.addPlayer(1, "p", 0);
        g.contract = 1;
        g.startSolo(p0.id, mode);
        return g;
    }

    /** 把当前非战斗相位推进到 MAP；返回是否成功回到 MAP。 */
    static boolean advance(Game g, int guardLimit) {
        for (int i = 0; i < guardLimit; i++) {
            if (g.phase == Game.MAP || g.phase == Game.WON || g.phase == Game.LOST) return true;
            switch (g.phase) {
                case Game.CHOICE:
                    for (Game.Player q : g.players) if (q.chosen < 0) g.choose(q.id, 0, 0);
                    g.update(Game.STEP);
                    break;
                case Game.EVENT:
                    if (g.eventChoice < 0) { if (!g.resolveEvent(0)) g.update(Game.STEP); }
                    else if (!g.continueEvent()) g.update(Game.STEP);
                    break;
                case Game.SHOP: g.leaveShop(); break;
                case Game.CAMP: g.camp(1); break;
                case Game.ARMORY: g.evolveWeapon(0); break;
                case Game.SHRINE:
                case Game.GAMBLE: g.shrine(1); break;
                default: g.update(Game.STEP); break;
            }
        }
        return g.phase == Game.MAP;
    }

    public static void main(String[] a) throws Exception {
        bug1_growthSurvivesRecomputeAndSave();
        bug2_shrineNoSoftLock();
        bug3_campShrineReachable();
        bug5_gambleSerializable();
        bug6_expeditionBossNodes();
        System.out.println();
        System.out.println("pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    // ── bug 1 ──────────────────────────────────────────────────
    static void bug1_growthSurvivesRecomputeAndSave() throws Exception {
        Game g = fresh(20261018L, Game.MODE_EXPEDITION);
        Game.Player p = g.players.get(0);
        float d0 = p.damage, hp0 = p.maxHp;

        // 真实路径 1：营地强化（走 camp()，内部会登记增量）
        g.phase = Game.CAMP;
        g.roomType = Game.ROOM_CAMP;
        g.camp(1);
        float d1 = p.damage, hp1 = p.maxHp;
        check("bug1a 营地强化立即生效", d1 > d0 && hp1 > hp0,
                "damage " + d0 + "->" + d1 + ", maxHp " + hp0 + "->" + hp1);

        // 再触发一次 recompute（历史上正是这里把成长抹掉）
        g.recompute(p);
        check("bug1b recompute 后营地成长仍在",
                Math.abs(p.damage - d1) < .5f && Math.abs(p.maxHp - hp1) < .5f,
                "damage=" + p.damage + " (expect " + d1 + "), maxHp=" + p.maxHp + " (expect " + hp1 + ")");

        // 真实路径 2：存档往返
        Game back = Codec.decode(Codec.encode(g));
        Game.Player q = back.players.get(0);
        check("bug1c 存档往返后成长仍在",
                Math.abs(q.damage - d1) < .5f && Math.abs(q.maxHp - hp1) < .5f,
                "damage=" + q.damage + " (expect " + d1 + "), maxHp=" + q.maxHp + " (expect " + hp1 + ")");

        // 真实路径 3：再存再读一次，必须保持稳定（不衰减、不膨胀）
        Game back2 = Codec.decode(Codec.encode(back));
        Game.Player r = back2.players.get(0);
        check("bug1d 连续两次存读档保持稳定",
                Math.abs(r.damage - d1) < .5f && Math.abs(r.maxHp - hp1) < .5f,
                "damage=" + r.damage + ", maxHp=" + r.maxHp);
    }

    // ── bug 2 ──────────────────────────────────────────────────
    static void bug2_shrineNoSoftLock() {
        Game g = fresh(7L, Game.MODE_EXPEDITION);
        g.phase = Game.SHRINE;
        g.roomType = Game.ROOM_SHRINE;
        g.coins = 10;
        g.shrineUsed = false;

        boolean r0 = g.shrine(0);
        check("bug2a 付不起时 shrine(0) 返回 false", !r0, "returned true");
        check("bug2b 付不起时 shrineUsed 不被置位", !g.shrineUsed, "shrineUsed=" + g.shrineUsed);
        boolean r1 = g.shrine(1);
        check("bug2c 仍可离开（不会永久卡死）", r1 || g.phase != Game.SHRINE,
                "shrine(1)=" + r1 + " phase=" + g.phase);
    }

    // ── bug 3 ──────────────────────────────────────────────────
    static void bug3_campShrineReachable() {
        int camp = 0, shrine = 0, shop = 0, armory = 0, treasure = 0, boss = 0, combat = 0;
        int runs = 250;
        for (int seed = 1; seed <= runs; seed++) {
            Game g = fresh(seed * 7919L, Game.MODE_EXPEDITION);
            for (int depth = 0; depth < 15 && g.phase == Game.MAP; depth++) {
                for (int o : g.routeOptions) {
                    switch (o) {
                        case Game.ROOM_CAMP: camp++; break;
                        case Game.ROOM_SHRINE: shrine++; break;
                        case Game.ROOM_SHOP: shop++; break;
                        case Game.ROOM_ARMORY: armory++; break;
                        case Game.ROOM_TREASURE: treasure++; break;
                        case Game.ROOM_BOSS: boss++; break;
                        case Game.ROOM_COMBAT: combat++; break;
                    }
                }
                if (!g.selectRoute(0)) break;
                if (!advance(g, 3000)) break;
            }
        }
        System.out.println("      route tallies over " + runs + " runs: combat=" + combat
                + " camp=" + camp + " shrine=" + shrine + " shop=" + shop
                + " armory=" + armory + " treasure=" + treasure + " boss=" + boss);
        check("bug3a CAMP 出现在路线中", camp > 0, "count=" + camp);
        check("bug3b SHRINE 出现在路线中", shrine > 0, "count=" + shrine);
        check("bug3c SHOP 未被挤掉", shop > 0, "count=" + shop);
        check("bug3d BOSS 仍在路线中", boss > 0, "count=" + boss);
    }

    // ── bug 5 ──────────────────────────────────────────────────
    static void bug5_gambleSerializable() {
        Game g = fresh(5L, Game.MODE_EXPEDITION);
        g.phase = Game.GAMBLE;
        try {
            Game back = Codec.decode(Codec.encode(g));
            check("bug5 GAMBLE 相位可往返序列化", back.phase == Game.GAMBLE, "phase=" + back.phase);
        } catch (Exception e) {
            check("bug5 GAMBLE 相位可往返序列化", false, e.toString());
        }
    }

    // ── bug 6 ──────────────────────────────────────────────────
    /** 直接问路线表：远征 15 站必须恰好 5 个首领节点（第 3/6/9/12/15 站）。 */
    static void bug6_expeditionBossNodes() {
        int bad = 0, bossCount = 0;
        for (int seed = 1; seed <= 200; seed++) {
            Game g = fresh(seed * 104729L, Game.MODE_EXPEDITION);
            int n = 0;
            for (int depth = 0; depth < 15; depth++) {
                int[] opts = g.preview(depth);
                boolean hasBoss = false;
                for (int o : opts) if (o == Game.ROOM_BOSS) hasBoss = true;
                boolean shouldBoss = (depth % 3 == 2);
                if (hasBoss != shouldBoss) bad++;
                if (hasBoss) n++;
                if (!shouldBoss && opts.length < 2) bad++;
            }
            if (n != 5) bad++;
            bossCount += n;
        }
        check("bug6 远征 15 站 = 5 个首领节点且每站可二选一", bad == 0,
                "violations=" + bad + " avgBoss=" + (bossCount / 200f));
    }
}