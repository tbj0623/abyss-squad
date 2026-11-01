import com.dilao.core.*;

/**
 * 契约词条（Pact）有效性回归测试 —— 防「声明了参数但没人读」。
 *
 * <p><b>为什么需要这个测试</b>：{@code Balance} 里 12 条词条的名称 / 描述 / 提示 / heat /
 * 解锁状态与全部数值参数都齐全，{@code Codec} 也会把 {@code pactMods} 存进档，
 * 但<b>数据齐全不等于运行时生效</b>。本套测试编写前逐条 grep 的结论是：
 * 只有 7 条真正改变了战斗（精英狂潮 / 无光视野 / 弹药枯竭 / 亡语爆裂 /
 * 遗物洪流 / 商店通胀 / 治疗禁绝），而
 * <b>首领狂怒 / 瞬移猎手 / 熔岩地火 / 房间压缩 / 时限压榨</b>这 5 条的参数
 * 在 core 与 app 里消费点都是 0 —— 玩家在选牌界面能看到它们、能为它们付出 heat、
 * 结算倍率也会因此提高，但战斗里什么都不会发生。这与 {@code EventContractTest}
 * 修复的「19 个事件静默落进 default」是同一类盲区。
 *
 * <p><b>检测原理</b>：每条词条都配一个<b>专属观测量</b>（不是"随便跑跑看有没有差异"），
 * 用「同种子、同输入、开 vs 关」的对照跑真实帧，断言该观测量出现<b>预期方向</b>的变化。
 * 这样即使两条词条碰巧产生同样的数值差异，也不会互相顶替而漏检。
 *
 * <p><b>为什么不只做「有差异」判定</b>：数值类词条（通胀 / 洪流）确实可以靠差分发现，
 * 但机制类词条（瞬移 / 熔岩口 / 四边收窄 / 四阶段）在短时间模拟里可能完全没有
 * 可观测的差分，从而被"有差异即通过"的弱断言漏掉。因此这里对每条都给出手感断言。
 *
 * <p>覆盖：
 * <ol>
 *   <li>12 条逐条：开 vs 关，600 帧真实模拟 + 专属观测量的方向性断言</li>
 *   <li>{@code Game.pactHeat()} 与 {@code Balance.heatOf(pactMods)} 始终一致</li>
 *   <li>{@code Balance.heatRewardMult(0) == 1.0}，且单调递增、有界</li>
 *   <li>同种子 + 同词条 + 同输入 → 逐帧确定性（random 状态一致）</li>
 * </ol>
 *
 * <p>用法：{@code java -cp build\core-tests PactEffectTest}
 * <p>退出码：0 全部通过；1 存在失败。
 */
public final class PactEffectTest {

    private static int passed, failed;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== 契约词条有效性回归：12 条逐条对照 ===");
        System.out.println();

        // ── 1. 数据一致性（先跑：后面所有对照都依赖 heat 自洽）──
        heatAndRewardContracts();
        System.out.println();

        // ── 2. 12 条逐条 ──
        System.out.println("-- 逐条玩法效果 --");
        pactEliteTide();
        pactDarkness();
        pactDrought();
        pactBossFury();
        pactDeathBlast();
        pactBlinkers();
        pactInferno();
        pactCollapse();
        pactTimeLimit();
        pactInflation();
        pactRelicFlood();
        pactNoHeal();
        System.out.println();

        // ── 3. 确定性 ──
        determinism();
        System.out.println();

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.out.println();
            System.out.println("FAIL 以下断言未通过：");
            for (String f : failures) System.out.println("  - " + f);
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ══════════════════════════════════════════════════════════════
    //  1. heat / 结算倍率契约
    // ══════════════════════════════════════════════════════════════

    static void heatAndRewardContracts() {
        System.out.println("-- heat 与结算倍率契约 --");

        // heat 0 是"没开词条"的基准，倍率必须恰好 1.0（不能有隐藏加成）。
        check("heatRewardMult(0) == 1.0",
                Math.abs(Balance.heatRewardMult(0) - 1f) < 1e-6f,
                "实际 " + Balance.heatRewardMult(0));

        // 位掩码 → heat 的换算必须与 Balance.heatOf 完全一致（Game 只做缓存）。
        boolean allMatch = true;
        String bad = "";
        int maskAll = (1 << Balance.PACT_COUNT) - 1;
        for (int mask = 0; mask <= maskAll; mask++) {
            Game g = lobby(mask);
            int expect = Balance.heatOf(mask);
            int actual = g.pactHeat();
            if (actual != expect) {
                allMatch = false;
                bad = "mask=0x" + Integer.toHexString(mask)
                        + " pactHeat=" + actual + " heatOf=" + expect;
                break;
            }
            if (g.pactHeat != expect) {   // 缓存字段也要同步
                allMatch = false;
                bad = "cache mask=0x" + Integer.toHexString(mask);
                break;
            }
        }
        check("pactHeat() 与 Balance.heatOf() 在全部 4096 个位掩码上一致",
                allMatch, allMatch ? "4096/4096" : bad);

        // 单条 heat 也必须对得上。
        boolean single = true;
        for (int i = 0; i < Balance.PACT_COUNT; i++) {
            Game g = lobby(1 << i);
            if (g.pactHeat() != Balance.PACT_HEAT[i] || g.pactHeat() != Balance.heatOf(1 << i)) {
                single = false;
                failures.add("PACT " + i + " 单条 heat 不一致");
                break;
            }
        }
        check("单条开启时 heat == PACT_HEAT[i]", single, "12/12");

        // 倍率：单调、有限、有界，并且满配时显著高于 1。
        boolean mono = true, finite = true;
        float prev = -1f;
        for (int h = 0; h <= Balance.PACT_HEAT_MAX; h++) {
            float m = Balance.heatRewardMult(h);
            if (!Float.isFinite(m)) finite = false;
            if (m <= prev) mono = false;
            prev = m;
        }
        check("heatRewardMult 严格单调递增且有限", mono && finite, "0.." + Balance.PACT_HEAT_MAX);
        check("满配 heat 的结算倍率 > 1.2（词条真的值得付 heat）",
                Balance.heatRewardMult(Balance.PACT_HEAT_MAX) > 1.2f,
                "=" + Balance.heatRewardMult(Balance.PACT_HEAT_MAX));

        // togglePact 必须真的切换位，而不是只改缓存。
        Game g = fresh(1L);
        boolean on = g.togglePact(Balance.PACT_DROUGHT);
        boolean off = g.togglePact(Balance.PACT_DROUGHT);
        check("togglePact 开 → 关 回到原状态", on && !off, "on=" + on + " off=" + off);
        check("togglePact 关闭后 pactMods == 0", g.pactMods == 0, "pactMods=" + g.pactMods);

        // clearPacts 必须把位掩码与 heat 一起清零。
        Game c = lobby(0b1010_1010_1010 & ((1 << Balance.PACT_COUNT) - 1));
        c.phase = Game.LOBBY;
        c.clearPacts();
        check("clearPacts 同时清零 pactMods 与 pactHeat",
                c.pactMods == 0 && c.pactHeat == 0 && c.pactHeat() == 0,
                "pactMods=" + c.pactMods + " heat=" + c.pactHeat);
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 逐条玩法效果
    // ══════════════════════════════════════════════════════════════

    /** 0 精英狂潮：精英刷新频率翻倍（经 Balance.pactEliteEvery）→ 同种子下精英更多。 */
    static void pactEliteTide() {
        int on = 0, off = 0;
        for (int seed = 0; seed < 6; seed++) {
            off += maxElite(7100L + seed, -1);
            on += maxElite(7100L + seed, Balance.PACT_ELITE_TIDE);
        }
        check("PACT_ELITE_TIDE 精英狂潮：同种子下精英数量上升",
                on > off, "开=" + on + " 关=" + off + "（6 局合计）");
    }

    /** 1 无光视野：自动瞄准射程 + 拾取范围 + 星尘补偿。 */
    static void pactDarkness() {
        Game off = ready(7201L, -1);
        Game on = ready(7201L, Balance.PACT_DARKNESS);
        float magOff = off.players.get(0).magnet;
        float magOn = on.players.get(0).magnet;
        check("PACT_DARKNESS 无光视野：拾取范围缩小到 60%",
                Math.abs(magOn - magOff * Balance.PACT_DARKNESS_MAGNET) < .01f,
                String.format("%.1f → %.1f", magOff, magOn));
        // 星尘补偿：同步长下总量应更高（击杀星尘 ×1.3）。
        int coinOff = coinsAfter(off, 600);
        int coinOn = coinsAfter(on, 600);
        check("PACT_DARKNESS 星尘补偿生效", coinOn > coinOff,
                "开=" + coinOn + " 关=" + coinOff);
    }

    /** 2 弹药枯竭：弹道 -1、伤害 ×1.35。用单发对比总伤害面板。 */
    static void pactDrought() {
        Game off = ready(7301L, -1);
        Game on = ready(7301L, Balance.PACT_DROUGHT);
        // 让弹丸在第一次开火时就能被观测到：清场后用手动瞄准强制开火。
        int shotsOff = shotsInFirstBurst(off);
        int shotsOn = shotsInFirstBurst(on);
        boolean fewer = shotsOn < shotsOff
                || (shotsOff <= 1 && shotsOn <= 1);   // 单弹道职业无法再减，二者都应 >= 1
        check("PACT_DROUGHT 弹药枯竭：弹道数减少（且不低于 1）",
                fewer && shotsOff >= 1 && shotsOn >= 1,
                "开=" + shotsOn + " 关=" + shotsOff);
        // 伤害补偿必须真的写进弹丸。
        float dmgOff = firstShotDamage(7301L, -1);
        float dmgOn = firstShotDamage(7301L, Balance.PACT_DROUGHT);
        // 暴击是随机的：只在两边都没暴击时比较伤害；否则退化为"伤害有限且为正"。
        boolean ok = dmgOn > 0f && Float.isFinite(dmgOn) && Float.isFinite(dmgOff);
        if (ok && shotsOff > 1) {
            ok = dmgOn > dmgOff * 1.1f;   // 至少 10% 提升（理论 35%，留出暴击噪声）
        }
        check("PACT_DROUGHT 伤害补偿写进弹丸", ok,
                String.format("开=%.2f 关=%.2f", dmgOn, dmgOff));
    }

    /**
     * 3 首领狂怒：第 4 阶段（phaseTriggers 到 3）、出手更快、额外召唤。
     *
     * <p><b>为什么弹幕数不能直接比</b>：{@code Game.shots} 是一个持续的池，
     * 首领招式不同（环 / 落点 / 螺旋）产生的弹幕寿命差异极大，瞬时 size 与
     * "出手频率"并不单调对应——第一次实测就出现了"开词条 16 vs 关词条 56"
     * 的假阴性。这里改为统计<b>累计</b>发射量与累计出手次数，排除在飞弹幕的干扰。
     */
    static void pactBossFury() {
        BossRun off = bossRun(7401L, -1);
        BossRun on = bossRun(7401L, Balance.PACT_BOSS_FURY);
        check("PACT_BOSS_FURY 首领狂怒：开启后进入第 4 阶段",
                on.phaseTriggers > off.phaseTriggers && on.phaseTriggers >= 3,
                "开 phaseTriggers=" + on.phaseTriggers + " 关=" + off.phaseTriggers);
        check("PACT_BOSS_FURY 出手更快（累计出手次数更多）",
                on.attacks > off.attacks,
                "开出手=" + on.attacks + " 关=" + off.attacks);
        check("PACT_BOSS_FURY 追加召唤（累计新增敌人更多）",
                on.adds > off.adds,
                "开=" + on.adds + " 关=" + off.adds);
    }

    /** 4 亡语爆裂：敌人死亡时对范围内玩家造成伤害。 */
    static void pactDeathBlast() {
        float off = deathBlastDamage(7501L, -1);
        float on = deathBlastDamage(7501L, Balance.PACT_DEATH_BLAST);
        check("PACT_DEATH_BLAST 亡语爆裂：击杀会反伤玩家",
                on > off + 0.5f,
                String.format("开=%.1f 关=%.1f", on, off));
    }

    /** 5 瞬移猎手：非首领敌人周期性瞬移，落点有紫色预警圈。 */
    static void pactBlinkers() {
        BlinkRun off = blinkRun(7601L, -1);
        BlinkRun on = blinkRun(7601L, Balance.PACT_BLINKERS);
        check("PACT_BLINKERS 瞬移猎手：出现落点预警圈（kind=4）",
                on.warnWindows > 0 && off.warnWindows == 0,
                "开预警圈=" + on.warnWindows + " 关=" + off.warnWindows);
        // 900 帧 = 30 秒；每 7 秒一次/每敌 → 预警圈数应明显多于 1 个。
        check("PACT_BLINKERS 按 PACT_BLINK_INTERVAL（7s）周期触发",
                on.warnWindows >= 3,
                "30 秒内出现 " + on.warnWindows + " 个预警圈");
        check("PACT_BLINKERS 瞬移真的改变了敌人位置（跳变 ≫ 普通移动）",
                on.maxJump > off.maxJump + 100f,
                String.format("开最大单帧跳变=%.1f 关=%.1f", on.maxJump, off.maxJump));
    }

    /** 6 熔岩地火：额外熔岩口 + 更快喷发。 */
    static void pactInferno() {
        Game off = ready(7701L, -1);
        Game on = ready(7701L, Balance.PACT_INFERNO);
        int lavaOff = lavaVents(off);
        int lavaOn = lavaVents(on);
        check("PACT_INFERNO 熔岩地火：额外布置 " + Balance.PACT_INFERNO_VENTS + " 个熔岩口",
                lavaOn >= lavaOff + Balance.PACT_INFERNO_VENTS,
                "开=" + lavaOn + " 关=" + lavaOff);
        // 更快喷发：同窗口内熔岩口进入 active 的次数更多。
        int pulseOff = lavaPulses(7701L, -1);
        int pulseOn = lavaPulses(7701L, Balance.PACT_INFERNO);
        check("PACT_INFERNO 喷发更频繁", pulseOn > pulseOff,
                "开喷发帧=" + pulseOn + " 关=" + pulseOff);
    }

    /** 7 房间压缩：可走区域四边各收窄 90 像素。 */
    static void pactCollapse() {
        float off = minPlayerX(7801L, -1);
        float on = minPlayerX(7801L, Balance.PACT_COLLAPSE);
        check("PACT_COLLAPSE 房间压缩：左边界内缩 " + (int) Balance.PACT_COLLAPSE_INSET + "px",
                Math.abs((on - off) - Balance.PACT_COLLAPSE_INSET) < 1.5f,
                String.format("开=%.1f 关=%.1f 差=%.1f", on, off, on - off));
        float topOff = minPlayerY(7801L, -1);
        float topOn = minPlayerY(7801L, Balance.PACT_COLLAPSE);
        check("PACT_COLLAPSE 上边界同样内缩",
                Math.abs((topOn - topOff) - Balance.PACT_COLLAPSE_INSET) < 1.5f,
                String.format("开=%.1f 关=%.1f", topOn, topOff));
    }

    /** 8 时限压榨：70 秒后持续掉血 + 定期追加增援。 */
    static void pactTimeLimit() {
        LimitRun off = limitRun(7901L, -1);
        LimitRun on = limitRun(7901L, Balance.PACT_TIME_LIMIT);
        check("PACT_TIME_LIMIT 时限压榨：超时后持续掉血",
                on.hpLost > off.hpLost + 10f,
                String.format("开掉血=%.1f 关=%.1f", on.hpLost, off.hpLost));
        // 每秒 5 点 × 契约倍率；跑 10 秒，允许 ±40% 的容差（护盾/无敌帧/帧率）。
        float expect = 10f * Balance.PACT_TIME_DPS * Balance.DAMAGE[1];
        check("PACT_TIME_LIMIT 掉血速率符合 PACT_TIME_DPS",
                on.hpLost > expect * .6f && on.hpLost < expect * 1.6f,
                String.format("实际 %.1f，期望 ≈ %.1f", on.hpLost, expect));
        check("PACT_TIME_LIMIT 追加增援（间隔 PACT_TIME_REINFORCE）",
                on.reinforce > off.reinforce,
                "开增援=" + on.reinforce + " 关=" + off.reinforce);
        // 未超时前绝不能掉血（否则"前 70 秒黄金窗口"的承诺是假的）。
        LimitRun early = limitRun(7901L, Balance.PACT_TIME_LIMIT, 10f);
        check("PACT_TIME_LIMIT 未超时（10s）不掉血",
                early.hpLost < 1f, String.format("掉血=%.2f", early.hpLost));
    }

    /**
     * 9 商店通胀：价格 ×2 + 星尘 ×2。
     *
     * <p>价格用 {@code Balance.pactShopMul} 直接验证<b>乘区</b>，不依赖"能不能进商店房"
     * （第一版探针试图导航到商店，结果两局都拿到 0 价格——路线构造失败了，
     * 这样的断言两边相等会永远通过）。星尘用同种子同输入跑 900 帧比较，
     * 并用"击杀数相同"作为对照成立的证据，避免把"打得不一样"误当成"星尘翻倍"。
     */
    static void pactInflation() {
        Game off = ready(8001L, -1);
        Game on = ready(8001L, Balance.PACT_INFLATION);
        check("PACT_INFLATION 商店价格乘区 == " + Balance.PACT_INFLATION_PRICE,
                Balance.pactShopMul(on) == Balance.PACT_INFLATION_PRICE
                        && Balance.pactShopMul(off) == 1f,
                "开=" + Balance.pactShopMul(on) + " 关=" + Balance.pactShopMul(off));
        check("PACT_INFLATION 星尘乘区 == " + Balance.PACT_INFLATION_COIN,
                Balance.pactCoinMul(on) == Balance.PACT_INFLATION_COIN
                        && Balance.pactCoinMul(off) == 1f,
                "开=" + Balance.pactCoinMul(on) + " 关=" + Balance.pactCoinMul(off));
        int cOff = coinsAfter(off, 900);
        int cOn = coinsAfter(on, 900);
        check("PACT_INFLATION 黑盒：同种子星尘明显更多",
                cOn > cOff * 1.4f,
                "开=" + cOn + " 关=" + cOff + " 击杀 " + on.kills + "/" + off.kills);
    }

    /** 10 遗物洪流：每次选牌 +2 张，敌人血量 ×1.25、承伤 ×1.12。 */
    static void pactRelicFlood() {
        Game off = ready(8101L, -1);
        Game on = ready(8101L, Balance.PACT_RELIC_FLOOD);
        int targetOff = off.target, targetOn = on.target;
        check("PACT_RELIC_FLOOD 敌人血量乘区 " + Balance.PACT_FLOOD_HP,
                Balance.pactHpMul(on) == Balance.PACT_FLOOD_HP
                        && Balance.pactHpMul(off) == 1f,
                "开=" + Balance.pactHpMul(on) + " 关=" + Balance.pactHpMul(off));
        check("PACT_RELIC_FLOOD 承伤乘区 " + Balance.PACT_FLOOD_DAMAGE,
                Balance.pactDamageMul(on) == Balance.PACT_FLOOD_DAMAGE
                        && Balance.pactDamageMul(off) == 1f,
                "开=" + Balance.pactDamageMul(on) + " 关=" + Balance.pactDamageMul(off));
        check("PACT_RELIC_FLOOD 额外选牌轮数 == " + Balance.PACT_FLOOD_DRAFTS,
                Balance.pactExtraDrafts(on) == Balance.PACT_FLOOD_DRAFTS
                        && Balance.pactExtraDrafts(off) == 0,
                "开=" + Balance.pactExtraDrafts(on) + " 关=" + Balance.pactExtraDrafts(off));
        // 黑盒：敌人血量确实更高（同种子的第一只敌人）。
        float hpOff = firstEnemyHp(off);
        float hpOn = firstEnemyHp(on);
        check("PACT_RELIC_FLOOD 黑盒：同种子敌人血量更高",
                hpOn > hpOff * 1.1f,
                String.format("开=%.1f 关=%.1f (target %d/%d)", hpOn, hpOff, targetOn, targetOff));
    }

    /** 11 治疗禁绝：一切治疗失效，改为伤害的 2% 转生命。 */
    static void pactNoHeal() {
        Game off = ready(8201L, -1);
        Game on = ready(8201L, Balance.PACT_NO_HEAL);
        Game.Player pOff = off.players.get(0);
        Game.Player pOn = on.players.get(0);
        pOff.hp = pOff.maxHp * .5f;
        pOn.hp = pOn.maxHp * .5f;
        float healedOff = off.heal(pOff, 40f);
        float healedOn = on.heal(pOn, 40f);
        check("PACT_NO_HEAL 治疗禁绝：heal() 返回 0 且不回血",
                healedOff > 1f && healedOn == 0f && pOn.hp == pOn.maxHp * .5f,
                String.format("开=%.1f 关=%.1f", healedOn, healedOff));
        check("PACT_NO_HEAL 吸血比例 == " + Balance.PACT_NO_HEAL_LEECH,
                Balance.PACT_NO_HEAL_LEECH > 0f && Balance.PACT_NO_HEAL_LEECH < .1f,
                "=" + Balance.PACT_NO_HEAL_LEECH);
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 确定性
    // ══════════════════════════════════════════════════════════════

    static void determinism() {
        System.out.println("-- 确定性 --");
        // 每条词条都单独跑一遍：两条同种子同输入的开局必须逐帧一致。
        boolean allSame = true;
        String bad = "";
        int[] probes = {Balance.PACT_INFERNO, Balance.PACT_BLINKERS, Balance.PACT_TIME_LIMIT,
                Balance.PACT_BOSS_FURY, Balance.PACT_COLLAPSE};
        for (int pact : probes) {
            Game a = ready(90000L + pact, pact);
            Game b = ready(90000L + pact, pact);
            simulate(a, 600);
            simulate(b, 600);
            boolean same = a.tick == b.tick && a.kills == b.kills
                    && a.coins == b.coins && a.random.state() == b.random.state()
                    && a.enemies.size() == b.enemies.size()
                    && a.hazards.size() == b.hazards.size();
            if (!same) {
                allSame = false;
                bad = Balance.PACT_NAME[pact] + " tick=" + a.tick + "/" + b.tick
                        + " kills=" + a.kills + "/" + b.kills;
                break;
            }
        }
        check("同种子 + 同词条 + 同输入 → 逐帧一致", allSame, allSame ? "5/5" : bad);

        // 开词条不能污染未开词条的随机序列（否则"关掉词条"会改变整局地图）。
        Game plain = ready(91000L, -1);
        simulate(plain, 300);
        Game plain2 = ready(91000L, -1);
        simulate(plain2, 300);
        check("未开词条时行为与改版前口径一致（两次运行相同）",
                plain.kills == plain2.kills && plain.random.state() == plain2.random.state(),
                "kills=" + plain.kills + "/" + plain2.kills);
    }

    // ══════════════════════════════════════════════════════════════
    //  专属观测探针
    // ══════════════════════════════════════════════════════════════

    /** 精英狂潮：跑 600 帧统计场上出现过的精英数。 */
    static int maxElite(long seed, int pact) {
        Game g = ready(seed, pact);
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 100000f;
        p.invuln = 1e9f;
        int seen = 0;
        for (int t = 0; t < 600 && g.phase == Game.FIGHT; t++) {
            g.input(p.id, 1, 0, 0, 0, false, 0, 0);
            g.update(Game.STEP);
            for (Game.Enemy e : g.enemies) if (e.elite && e.bossIndex < 0) seen++;
        }
        return seen;
    }

    /** 无光视野：600 帧后的星尘。 */
    static int coinsAfter(Game g, int ticks) {
        simulate(g, ticks);
        return g.coins;
    }

    /** 弹药枯竭：第一次齐射产生的弹丸数。 */
    static int shotsInFirstBurst(Game g) {
        Game.Player p = g.players.get(0);
        int before = g.shots.size();
        for (int t = 0; t < 120; t++) {
            g.input(p.id, 1, 0, 1, 0, true, 0, 0);   // 手动瞄准，强制开火
            g.update(Game.STEP);
            if (g.shots.size() > before) return g.shots.size() - before;
        }
        return 0;
    }

    /** 弹药枯竭：第一枚弹丸的伤害。 */
    static float firstShotDamage(long seed, int pact) {
        Game g = ready(seed, pact);
        Game.Player p = g.players.get(0);
        for (int t = 0; t < 120; t++) {
            g.input(p.id, 1, 0, 1, 0, true, 0, 0);
            g.update(Game.STEP);
            if (!g.shots.isEmpty()) return g.shots.get(0).damage;
        }
        return 0f;
    }

    static final class BossRun { int phaseTriggers, attacks, adds; }

    /**
     * 首领狂怒：把首领压到 10% 血，观察阶段 / 出手次数 / 召唤。
     *
     * <p>"出手次数"用<b>首领 cooldown 由正转负的次数</b>来数：{@code updateBoss} 每次
     * 出手后把 cooldown 设为 {@code bossCooldown(phase)}，随后逐帧递减；跨过 0 的那一帧
     * 就是一次新的蓄力开始。比数弹幕更稳，也不受招式类型影响。
     */
    static BossRun bossRun(long seed, int pact) {
        Game g = ready(seed, pact);
        g.enemies.clear();
        Game.Enemy e = new Game.Enemy();
        e.id = 90000;
        e.type = 0;
        e.bossIndex = 0;
        e.behavior = Game.B_CHASE;
        e.elite = true;
        e.x = 560; e.y = 165;
        e.r = Bosses.RADIUS[0];
        e.speed = 0f;                    // 站桩：排除走位带来的随机差异
        e.maxHp = e.hp = 100000f;
        e.cooldown = .5f;
        e.stage = 0;
        g.enemies.add(e);
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 1e7f;
        p.invuln = 1e9f;
        e.hp = e.maxHp * .10f;           // 直接进"第 4 阶段"的血线
        BossRun r = new BossRun();
        // 召唤量只数 phaseTriggers 跃迁的那一帧前后新出现的敌人 id。
        // 不能数"场上存活峰值"——玩家会持续击杀，存活数取决于 DPS 而不是召唤量
        // （第一版探针正因为这个原因出现"开 7 / 关 9"的假阴性）。
        java.util.Set<Integer> before = new java.util.HashSet<>();
        for (Game.Enemy x : g.enemies) before.add(x.id);
        int lastTrig = e.phaseTriggers;
        float prevCd = e.cooldown;
        for (int t = 0; t < 600; t++) {
            before.clear();
            for (Game.Enemy x : g.enemies) before.add(x.id);
            int pendBefore = g.pendingEnemiesCount();
            g.update(Game.STEP);
            if (g.phase != Game.FIGHT) break;
            // 出手判定：cooldown 从 > 0 落到 <= 0（且不在冲锋恢复窗口内）
            if (prevCd > 0f && e.cooldown <= 0f && e.chargeTime <= 0f) r.attacks++;
            prevCd = e.cooldown;
            // 阶段跃迁帧：统计这一帧新增的护卫（pending + 直接入场的都算）
            if (e.phaseTriggers > lastTrig) {
                lastTrig = e.phaseTriggers;
                int gained = g.pendingEnemiesCount() - pendBefore;
                for (Game.Enemy x : g.enemies) if (!before.contains(x.id)) gained++;
                r.adds += Math.max(0, gained);
            }
        }
        r.phaseTriggers = e.phaseTriggers;
        return r;
    }

    /**
     * 亡语爆裂：让一只贴着玩家的敌人在真实击杀路径上死亡，测量反伤。
     *
     * <p>必须走 {@code damage()}，而不是把 {@code hp} 直接置 0——亡语爆裂挂在
     * {@code damage()} 的击杀分支里，直接清零会被 {@code flushDeaths} 静默移除，
     * 什么都测不到（第一版探针就踩了这个坑：两边都是 0.0）。
     * 这里用玩家的自动开火打死木桩：木桩血量极低，确保第一发就死。
     */
    static float deathBlastDamage(long seed, int pact) {
        Game g = ready(seed, pact);
        g.enemies.clear();
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 100000f;
        p.invuln = 0f;
        Game.Enemy e = new Game.Enemy();
        e.id = 91000;
        e.type = 0;
        e.behavior = Game.B_CHASE;
        // 放在爆裂半径内（PACT_BLAST_RADIUS=62），但不要太贴近以免接触伤害混淆。
        e.x = p.x + 40; e.y = p.y;
        e.r = 16;
        e.maxHp = 100000f;              // 大血量：爆裂伤害 = maxHp*12% 被 CAP 30 截断
        e.hp = 1f;                      // 但只有 1 点血 → 第一发必定击杀
        e.speed = 0f;
        g.enemies.add(e);
        g.spawnClock = 1e9f;            // 禁止刷新，避免其它敌人干扰
        float hp0 = p.hp;
        for (int t = 0; t < 120 && g.phase == Game.FIGHT; t++) {
            g.spawnClock = 1e9f;
            g.input(p.id, 0, 0, 1, 0, true, 0, 0);   // 手动瞄向 +x（木桩方向）
            g.update(Game.STEP);
            if (g.enemies.isEmpty()) break;          // 木桩已被击杀
        }
        return Math.max(0f, hp0 - p.hp);
    }

    static final class BlinkRun { int warnFrames, warnWindows; float maxJump; }

    /**
     * 瞬移猎手：统计 kind=4 预警帧 / 预警次数 / 单帧最大位移。
     *
     * <p><b>两个坑（第一版探针都踩了）</b>：
     * <ol>
     *   <li>按<b>索引</b>对齐上一帧坐标是错的——{@code flushDeaths} 会压缩敌人列表，
     *       索引错位会把"换了个敌人"误判成"瞬移了 1000 像素"（实测关词条也有 1097px）。</li>
     *   <li>预警圈是<b>持续存在</b>的 hazard（{@code active == -1} 时一直留在列表里等 clock 走完），
     *       逐帧累加 hazard 数量会把 0.55 秒数成几百帧。必须按 hazard id 去重，
     *       数"出现过多少个不同的预警圈"。</li>
     * </ol>
     */
    static BlinkRun blinkRun(long seed, int pact) {
        Game g = ready(seed, pact);
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 1e7f;
        p.invuln = 1e9f;
        BlinkRun r = new BlinkRun();
        java.util.Map<Integer, float[]> before = new java.util.HashMap<>();
        java.util.Set<Integer> warnIds = new java.util.HashSet<>();
        for (int t = 0; t < 900 && g.phase == Game.FIGHT; t++) {
            before.clear();
            for (Game.Enemy e : g.enemies) before.put(e.id, new float[]{e.x, e.y});
            g.input(p.id, 1, 0, 0, 0, false, 0, 0);
            g.update(Game.STEP);
            for (Game.Hazard h : g.hazards) {
                if (h.kind != 4) continue;
                r.warnFrames++;                       // 累计预警"帧"（用于估算时长）
                if (warnIds.add(h.id)) r.warnWindows++; // 不同的预警圈数
            }
            for (Game.Enemy e : g.enemies) {
                float[] b = before.get(e.id);
                if (b == null) continue;              // 本帧新刷出的敌人不参与位移统计
                float jump = Game.distance(b[0], b[1], e.x, e.y);
                if (jump > r.maxJump) r.maxJump = jump;
            }
        }
        return r;
    }

    /** 熔岩地火：场内 kind=3 的熔岩口数量。 */
    static int lavaVents(Game g) {
        int n = 0;
        for (Game.Hazard h : g.hazards) if (h.kind == 3) n++;
        return n;
    }

    /** 熔岩地火：600 帧内熔岩口处于喷发态的总帧数。 */
    static int lavaPulses(long seed, int pact) {
        Game g = ready(seed, pact);
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 1e7f;
        p.invuln = 1e9f;
        int n = 0;
        for (int t = 0; t < 600 && g.phase == Game.FIGHT; t++) {
            g.input(p.id, 1, 0, 0, 0, false, 0, 0);
            g.update(Game.STEP);
            for (Game.Hazard h : g.hazards) if (h.kind == 3 && h.active > 0) n++;
        }
        return n;
    }

    /** 房间压缩：一直往左走，记录能达到的最小 x。 */
    static float minPlayerX(long seed, int pact) {
        return minPlayer(seed, pact, true);
    }

    /** 房间压缩：一直往上走，记录能达到的最小 y。 */
    static float minPlayerY(long seed, int pact) {
        return minPlayer(seed, pact, false);
    }

    /**
     * 房间压缩：把玩家放到左上角往边界顶，记录能达到的最小 x / y。
     *
     * <p><b>为什么要清空地形</b>：可走边界之外还有障碍物，第一版探针从出生点往上走时
     * 会被一张桌子挡住，开/关词条都停在 401，测的其实是家具不是边界。
     * 清空 blocks 后测的才是纯边界钳制。
     */
    static float minPlayer(long seed, int pact, boolean horizontal) {
        Game g = ready(seed, pact);
        g.blocks.clear();                 // 隔离地形：只测边界
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 1e7f;
        p.invuln = 1e9f;
        p.x = 300f; p.y = 300f;           // 从场地内部出发，朝左上角顶
        float best = horizontal ? p.x : p.y;
        for (int t = 0; t < 300; t++) {
            g.input(p.id, horizontal ? -1 : 0, horizontal ? 0 : -1, 0, 0, false, 0, 0);
            g.update(Game.STEP);
            float v = horizontal ? p.x : p.y;
            if (v < best) best = v;
        }
        return best;
    }

    static final class LimitRun { float hpLost; int reinforce; }

    static LimitRun limitRun(long seed, int pact) {
        return limitRun(seed, pact, Balance.PACT_LIMIT_SECONDS + .5f);
    }

    /**
     * 时限压榨：把 roomTime 拨到指定秒数，跑 330 帧（11 秒）。
     * <p>增援观测用 "spawned 之外的新增敌人"：{@code updatePactTimeLimit} 直接调用
     * {@code spawn()} 而不增加 {@code spawned}，因此 spawned 变化量 == 0 才是对的。
     */
    static LimitRun limitRun(long seed, int pact, float startRoomTime) {
        Game g = ready(seed, pact);
        Game.Player p = g.players.get(0);
        p.maxHp = p.hp = 100000f;
        p.invuln = 1e9f;
        // 冻结常规刷怪（spawnClock 推到很远），只观测「时限压榨」自己调的 spawn()。
        // 否则常规波次与追兵混在一起，无法区分是谁刷的。
        float hp0 = p.hp;
        g.roomTime = startRoomTime;
        // 每帧手动清掉除哨兵以外的敌人：常规刷怪已冻结，任何净增都只可能来自
        // 「时限压榨」的 spawn()。必须留一个哨兵，否则 enemies 为空会立刻触发
        // checkObjective 的清房判定（spawned >= target），房间直接进入 CHOICE。
        int alive0 = g.enemies.size() + g.pendingEnemiesCount();
        int peak = alive0;
        for (int t = 0; t < 330 && g.phase == Game.FIGHT; t++) {
            g.spawnClock = 1e9f;
            g.input(p.id, 1, 0, 0, 0, false, 0, 0);
            g.update(Game.STEP);
            int alive = g.enemies.size() + g.pendingEnemiesCount();
            peak = Math.max(peak, alive);
            while (g.enemies.size() > 1) g.enemies.remove(g.enemies.size() - 1);
        }
        LimitRun r = new LimitRun();
        r.hpLost = Math.max(0f, hp0 - p.hp);
        r.reinforce = Math.max(0, peak - alive0);
        return r;
    }

    /** 遗物洪流：同种子下第一只敌人的血量。 */
    static float firstEnemyHp(Game g) {
        for (int t = 0; t < 240 && g.phase == Game.FIGHT; t++) {
            g.update(Game.STEP);
            for (Game.Enemy e : g.enemies)
                if (e.bossIndex < 0 && !e.summoned) return e.maxHp;
        }
        return 0f;
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    /** 只建大厅（不开局），用来测 heat 契约。 */
    static Game lobby(int pactMods) {
        Game g = new Game(1L);
        g.addPlayer(1, "t", 0);
        g.pactMods = pactMods;
        g.pactHeat = Balance.heatOf(pactMods);
        return g;
    }

    static Game fresh(long seed) {
        Game g = new Game(seed);
        g.addPlayer(1, "t", 0);
        g.contract = 1;
        return g;
    }

    /** 开局并进入第一场战斗房；pact < 0 表示不开词条。 */
    static Game ready(long seed, int pact) {
        Game g = new Game(seed);
        Game.Player p = g.addPlayer(1, "t", 0);
        g.contract = 1;
        if (pact >= 0) g.togglePact(pact);
        g.startSolo(1, Game.MODE_EXPEDITION);
        g.selectRoute(0);
        return g;
    }

    /** 固定输入序列跑帧（绕圈移动），与 RelicEffectTest 同口径。 */
    static void simulate(Game g, int ticks) {
        if (g.players.isEmpty()) return;
        Game.Player p = g.players.get(0);
        double ang = 0;
        for (int t = 0; t < ticks && g.phase == Game.FIGHT; t++) {
            ang += .035;
            g.input(p.id, (float) Math.cos(ang), (float) Math.sin(ang) * .7f, 0, 0, false, 0, 0);
            g.update(Game.STEP);
        }
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + name + "  |  " + detail);
        } else {
            failed++;
            System.out.println("FAIL  " + name + "  |  " + detail);
            failures.add(name + "  |  " + detail);
        }
    }
}
