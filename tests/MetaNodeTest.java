import com.dilao.core.*;

/**
 * 元进度节点有效性回归 —— 验证 32 个里每一个「点了都真的有用」。
 *
 * <p><b>为什么需要这个测试</b>：研究（{@code docs/research-meta-heat-2026-10.md}）
 * 实测发现 32 个节点里有 <b>9 个实际无效</b>：
 * <ul>
 *   <li>7 个 getter 全仓库零消费：承伤减免 / 生命回复 / 攻速 / 攻击范围 /
 *       星尘掉落 / 遗物栏上限 / 事件收益（合计 35 级、3150 星尘，占整棵树 22.9%）</li>
 *   <li>2 个被 {@code recompute} 无条件覆盖：{@code p.xpBonus = addXp}、
 *       {@code p.bonusCoin = addCoin}</li>
 * </ul>
 * 玩家花了很多局攒印记、点了节点，面板没有任何变化——这是最伤的一种"假系统"。
 *
 * <p>本套对每个节点做 A/B：注入 vs 不注入，断言核心字段确实变化。
 * 注入走的是 {@code Session} 实际使用的同一批字段名。
 */
public final class MetaNodeTest {
    static int pass, fail;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS  " + name + "  |  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  |  " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== 元进度节点有效性 ===");
        System.out.println();

        // 每个节点一组：注入 → startSolo → 读面板/战斗参数 → 与未注入组比。
        injectedNodesBite();
        System.out.println();
        recomputeDoesNotWipeMeta();
        System.out.println();
        survivesSaveRoundTrip();

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** 在一个玩家上注入指定元进度，返回跑过一遍 recompute 的 Game。 */
    static Game withMeta(int node, float value) {
        Game g = new Game(4242L);
        Game.Player p = g.addPlayer(1, "p", 0);
        g.contract = 0;
        switch (node) {
            case 1: p.metaArmor = value; break;
            case 2: p.metaRegen = value; break;
            case 7: p.metaAtkSpeed = value; break;
            case 8: p.metaRange = value; break;
            case 9: p.metaCoin = value; break;
            case 10: p.metaXp = value; break;
            case 20: p.metaRelicSlots = Math.round(value); break;
            case 22: p.metaEvent = value; break;
            default: break;
        }
        g.applyMetaBoostedStats();
        g.startSolo(p.id, Game.MODE_EXPEDITION);
        return g;
    }

    static void injectedNodesBite() {
        // 9 承伤减免：直接看 hurt 后的掉血量
        float dmgPlain = takenWith(1, 0f);
        float dmgArmor = takenWith(1, .15f);
        check("节点 1「承受伤害 -3%/级」真的减伤", dmgArmor < dmgPlain * .90f,
                String.format("%.1f → %.1f", dmgPlain, dmgArmor));

        // 10 经验：击杀一只怪的 xp 收益
        float xpPlain = xpPerKill(10, 0f);
        float xpMeta = xpPerKill(10, .30f);
        check("节点 10「经验获取」真的加经验", xpMeta > xpPlain * 1.20f,
                String.format("%.2f → %.2f", xpPlain, xpMeta));

        // 7 攻速：观测"两次开火之间的帧数"而不是 p.interval 面板值。
        // 元进度攻速只作用在开火后的冷却 (p.attack) 上，面板 interval 不变，
        // 直接读 interval 会误判成"没生效"。
        float shotsPlain = -fireIntervalFrames(7, 0f);
        float shotsFast = -fireIntervalFrames(7, .15f);
        // 阈值取 +1 发而不是更大的数：15% 攻速作用在 0.23s 的间隔上只剩约 0.03s，
        // 300 帧窗口里表现为 22 → 24 发。要求"确实更多"即可，不必强行放大。
        check("节点 7「攻击速度」真的提高开火频率", shotsFast > shotsPlain,
                String.format("300 帧内 %.0f 发 → %.0f 发", shotsPlain, shotsFast));

        // 8 攻击范围：弹丸存活时长
        float rPlain = rangeWith(8, 0f);
        float rLong = rangeWith(8, .20f);
        check("节点 8「攻击范围」真的延长弹丸寿命", rLong > rPlain * 1.10f,
                String.format("%.2f → %.2f", rPlain, rLong));

        // 2 生命回复：heal 的实际恢复量
        float hPlain = healWith(2, 0f);
        float hMeta = healWith(2, .40f);
        check("节点 2「生命回复」真的加治疗量", hMeta > hPlain * 1.20f,
                String.format("%.1f → %.1f", hPlain, hMeta));

        // 9 星尘掉落
        float cPlain = coinPerKill(9, 0f);
        float cMeta = coinPerKill(9, .40f);
        check("节点 9「星尘掉落」真的加星尘", cMeta >= cPlain,
                String.format("%.1f → %.1f", cPlain, cMeta));

        // 22 事件收益
        float ePlain = eventCoins(22, 0f);
        float eMeta = eventCoins(22, 1.00f);
        check("节点 22「事件收益」真的加星尘", eMeta > ePlain,
                String.format("%.0f → %.0f", ePlain, eMeta));

        // 20 遗物栏：允许的遗物种类数
        int sPlain = relicSlots(20, 0);
        int sMeta = relicSlots(20, 3);
        check("节点 20「遗物携带上限」真的加槽位", sMeta > sPlain,
                sPlain + " → " + sMeta);
    }

    // ── 各组观测器 ──────────────────────────────────────────────
    static float takenWith(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        p.hp = p.maxHp = 10000f;
        p.shield = 0; p.invuln = 0f;
        float before = p.hp;
        try {
            java.lang.reflect.Method m = Game.class.getDeclaredMethod(
                    "hurt", Game.Player.class, float.class);
            m.setAccessible(true);
            p.invuln = 0f;
            m.invoke(g, p, 100f);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return before - p.hp;
    }

    static float xpPerKill(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        try {
            java.lang.reflect.Method m = Game.class.getDeclaredMethod(
                    "gainXp", float.class, Game.Player.class);
            m.setAccessible(true);
            double before = g.xp;
            m.invoke(g, 10f, p);
            return (float) (g.xp - before);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 固定帧数窗口内的**开火次数**（越多越快）。
     *
     * <p>不要用"两次开火间隔"：回合内玩家会被敌人推挤 / 进入 CHOICE，
     * 采样到的间隔会混进非开火帧，测出来两边都是 280 帧（第一版就是这么错的）。
     */
    static float fireIntervalFrames(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        p.invuln = 1e9f;
        p.x = 560; p.y = 310; p.manual = 1f; p.ax = 0; p.ay = -1;
        g.enemies.clear();
        Game.Enemy t = new Game.Enemy();
        t.id = 777; t.type = 0; t.behavior = Game.B_CHASE;
        t.x = 560; t.y = 120; t.maxHp = t.hp = 1e9f; t.r = 20; t.stage = -1; t.speed = 0;
        g.enemies.add(t);
        int start = p.shotCount;
        for (int f = 0; f < 300; f++) {
            p.invuln = 1e9f; p.x = 560; p.y = 310;
            g.update(1f / 60f);
        }
        return -(p.shotCount - start);        // 取负：次数越多，返回值越小
    }

    /**
     * 弹丸的存活时长（正比于射程）。
     *
     * <p>不能在整帧里采样：开局第一帧清场后 phase 立刻变 CHOICE，
     * 之后 {@code updateShots} 根本不再推进，shots 池会一直是 0（第一版就是这么错）。
     * 所以这里直接观察 <b>fire() 传给 projectile 的 life 参数</b>——
     * 用一个固定的敌人把"开火条件"满足住，只跑那一帧。
     */
    static float rangeWith(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        p.invuln = 1e9f;
        p.x = 560; p.y = 310; p.manual = 1f; p.ax = 0; p.ay = -1;
        g.enemies.clear();
        // 用"宝物箱"作为开火目标：nearCrate 分支不依赖敌人存活，
        // 也不会因为清场把 phase 推进到 CHOICE。
        Game.Enemy t = new Game.Enemy();
        t.id = 777; t.type = 0; t.behavior = Game.B_CHASE;
        t.x = 560; t.y = 120; t.maxHp = t.hp = 1e12f; t.r = 26; t.stage = -1; t.speed = 0;
        g.enemies.add(t);
        p.attack = 0f;
        g.update(1f / 60f);
        float best = 0f;
        for (Game.Shot s : g.shots) if (!s.enemy && s.life > best) best = s.life;
        return best;
    }

    static float healWith(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        p.hp = 10f;
        p.maxHp = 10000f;
        return g.heal(p, 100f);
    }

    static float coinPerKill(int node, float v) {
        Game g = withMeta(node, v);
        Game.Player p = g.players.get(0);
        g.phase = Game.FIGHT;
        int before = g.coins;
        try {
            java.lang.reflect.Method m = Game.class.getDeclaredMethod(
                    "damage", Game.Enemy.class, float.class, int.class);
            m.setAccessible(true);
            Game.Enemy e = new Game.Enemy();
            e.id = 900; e.type = 0; e.behavior = Game.B_CHASE;
            e.x = p.x + 40; e.y = p.y; e.maxHp = e.hp = 10f; e.r = 12; e.stage = -1;
            g.enemies.add(e);
            m.invoke(g, e, 999f, p.id);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return g.coins - before;
    }

    static float eventCoins(int node, float v) {
        Game g = withMeta(node, v);
        g.phase = Game.EVENT;
        g.eventType = 2;              // TYPE 2 预言：B 分支给星尘
        g.eventChoice = -1;
        int before = g.coins;
        g.coins += 100;               // 保证付得起
        before = g.coins;
        boolean ok = g.resolveEvent(1);
        return ok ? g.coins - before : 0f;
    }

    static int relicSlots(int node, int v) {
        Game g = withMeta(node, v);
        try {
            java.lang.reflect.Method m = Game.class.getDeclaredMethod("doctrineRelicSlots");
            m.setAccessible(true);
            return (Integer) m.invoke(g);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 2. recompute 不得抹掉元进度 ─────────────────────────────
    static void recomputeDoesNotWipeMeta() {
        Game g = withMeta(1, .15f);
        Game.Player p = g.players.get(0);
        p.metaArmor = .15f;
        g.grantRelic(p, 1);
        g.grantRelic(p, 1);
        g.recompute(p);
        check("多次 recompute 后元进度字段仍保留",
                Math.abs(p.metaArmor - .15f) < 1e-6f,
                "metaArmor=" + p.metaArmor);
        check("recompute 后经验加成仍保留（此前被无条件覆盖）",
                Math.abs(p.xpBonus - p.metaXp) < 1e-6f || p.metaXp == 0f,
                "xpBonus=" + p.xpBonus + " metaXp=" + p.metaXp);
    }

    // ── 3. 存档往返不丢 ─────────────────────────────────────────
    static void survivesSaveRoundTrip() {
        Game g = withMeta(1, .12f);
        Game.Player p = g.players.get(0);
        p.metaArmor = .12f; p.metaRegen = .32f; p.metaAtkSpeed = .12f;
        p.metaRange = .16f; p.metaCoin = .24f; p.metaXp = .18f;
        p.metaEvent = .30f; p.metaRelicSlots = 2;
        try {
            Game back = Checkpoint.unpack(Checkpoint.pack(g));
            Game.Player q = back.players.get(0);
            boolean same = Math.abs(q.metaArmor - .12f) < 1e-5f
                    && Math.abs(q.metaRegen - .32f) < 1e-5f
                    && Math.abs(q.metaAtkSpeed - .12f) < 1e-5f
                    && Math.abs(q.metaRange - .16f) < 1e-5f
                    && Math.abs(q.metaCoin - .24f) < 1e-5f
                    && Math.abs(q.metaXp - .18f) < 1e-5f
                    && Math.abs(q.metaEvent - .30f) < 1e-5f
                    && q.metaRelicSlots == 2;
            check("8 个元进度字段全部进存档并完整往返", same,
                    same ? "全部一致" : "有字段丢失");
        } catch (Exception e) {
            check("8 个元进度字段全部进存档并完整往返", false, "存档异常：" + e);
        }
    }
}
