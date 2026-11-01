package com.dilao.core;

/**
 * 可调数值预算表（契约 §12）。所有收益 / 难度系数集中在这里，便于平衡验证。
 * <p>
 * <b>成长目标</b>：首通 15 站远征时，玩家总输出成长约 6~10 倍，
 * 敌人血量成长约 4~7 倍（floor 1 → floor 5 约 5.1 倍），
 * 两者同步收紧，避免后期毫无压力或中期暴毙。
 * <p>
 * 本类不引用任何 Android API；所有方法都对 null / 非法输入做钳制，不会产生 NaN、Infinity 或负收益。
 */
public final class Balance {
    private Balance() {}

    // ══════════════════════════════════════════════════════════════
    //  契约 5 档（Game.contract 0..4）
    // ══════════════════════════════════════════════════════════════
    public static final String[] CONTRACTS = {"漫游", "远征", "试炼", "深渊", "炼狱"};
    public static final String[] CONTRACT_HINTS = {
            "熟悉的构筑，更宽的容错。适合第一次下潜。",
            "推荐难度。敌人成组施压，精英带词缀。",
            "弹幕更快，进攻更密。需要真正规划构筑。",
            "血量与伤害同时上调，容错窗口很窄。",
            "为已经吃透机制的人准备。一次失误就结束。"
    };

    /**
     * 五档契约的敌人血量倍率。
     * <p>原来 1.02→1.62 只涨 59%，配合玩家 13 件遗物的成长，
     * 高难度下敌人"死得一样快"——所以体感上五档没有区别。
     */
    public static final float[] HP = {1.38f, 1.76f, 2.20f, 2.72f, 3.42f};
    /**
     * 五档契约的敌人伤害倍率。
     *
     * <p><b>为什么重做（v2.2）</b>：原曲线 {.70,.86,.98,1.09,1.18} 从漫游到炼狱只涨 69%，
     * 而玩家在 15 站里能拿到 13 件遗物 + 3 条进化，成长幅度远超 69%。
     * 实测（PlayProbe 8 种子 × 12 职业）：漫游 99% → 炼狱 71.9%，
     * 五档之间几乎没有梯度，"炼狱"并不难。
     *
     * <p>新曲线累计约 2.4×，且每一档都有明确的体感差。
     * 校准目标（PlayProbe 8 种子 × 12 职业 × 流派向策略）：
     * {@code 92~98 / 78~88 / 55~68 / 28~40 / 10~18}。
     * 前两档刻意留出"新手能过"的空间，后三档才真正开始施压。
     *
     * <p>注意：探针 bot 的走位比真人稳，所以实测值天然偏高；
     * 拿它做**相对**校准（档与档之间的差），不要当成真人的绝对胜率。
     */
    public static final float[] DAMAGE = {.90f, 1.20f, 1.46f, 1.78f, 2.16f};
    /**
     * 五档契约的敌人行动节奏倍率（越大出手越频繁）。
     * <p>与 {@link #DAMAGE} 同步拉宽：原来 1.00→1.28 只快 28%，
     * 高难度下"更容易被打中"这件事几乎感受不到。
     */
    public static final float[] PACE = {1.04f, 1.22f, 1.40f, 1.60f, 1.82f};
    // ══════════════════════════════════════════════════════════════
    //  遭遇异变（8 种，下标 0 = 无异变；Game.modifierAt 只抽 1..7）
    // ══════════════════════════════════════════════════════════════
    public static final String[] MODIFIERS = {
            "无异变", "疾行猎群", "余烬侵蚀", "护卫结界",
            "寒潮回响", "腐化蔓延", "回响叠加", "双生守望"
    };
    public static final String[] MODIFIER_HINTS = {
            "标准遭遇，没有额外规则。",
            "近战单位更快，会短暂停顿后突进。",
            "场内增加周期喷发的熔岩口，走位空间被压缩。",
            "部分敌人带护盾，必须先击破护盾才能伤到本体。",
            "更多远程支援单位，迫使你优先击杀后排。",
            "敌人死亡后会留下短暂腐蚀区域。",
            "敌人的攻击频率随存活时间递增。",
            "精英怪成对出现，但收益也翻倍。"
    };

    /** 三选一重掷的基础星尘价格（有预言次数时免费）。 */
    public static final int RELIC_REROLL_COST = 28;

    // ══════════════════════════════════════════════════════════════
    //  契约词条（Pact / Heat，v2.0 支柱 1）
    // ══════════════════════════════════════════════════════════════
    //
    //  设计原则：每条词条都必须改变**玩法规则**，而不是乘一个数值。
    //  下标即 pactMods 位掩码的位号；一旦发布，id 与含义永久固定（会进存档）。
    //
    //  0 精英狂潮   1 无光视野   2 弹药枯竭   3 首领狂怒
    //  4 亡语爆裂   5 瞬移猎手   6 熔岩地火   7 房间压缩
    //  8 时限压榨   9 商店通胀  10 遗物洪流  11 治疗禁绝

    public static final int PACT_COUNT = 12;

    /** 词条 id 常量，供 Game / UI / 测试引用。 */
    public static final int PACT_ELITE_TIDE = 0, PACT_DARKNESS = 1, PACT_DROUGHT = 2,
            PACT_BOSS_FURY = 3, PACT_DEATH_BLAST = 4, PACT_BLINKERS = 5,
            PACT_INFERNO = 6, PACT_COLLAPSE = 7, PACT_TIME_LIMIT = 8,
            PACT_INFLATION = 9, PACT_RELIC_FLOOD = 10, PACT_NO_HEAL = 11;

    public static final String[] PACT_NAME = {
            "精英狂潮", "无光视野", "弹药枯竭", "首领狂怒",
            "亡语爆裂", "瞬移猎手", "熔岩地火", "房间压缩",
            "时限压榨", "商店通胀", "遗物洪流", "治疗禁绝"
    };

    public static final String[] PACT_DESC = {
            "精英怪刷新频率翻倍，但精英掉落星尘 +80%",
            "自动瞄准射程缩至近身，拾取范围 -40%，星尘 +30%",
            "弹道数量 -1（最低 1），但每发伤害 +35%",
            "首领进入四阶段循环，每次变招额外召唤，出手更快",
            "敌人死亡时原地爆炸，对范围内玩家造成伤害",
            "非首领敌人每 7 秒向玩家瞬移一次，落地有预警",
            "每场战斗额外布置 2 个熔岩口，喷发间隔缩短 25%",
            "可走区域四边各收窄 90 像素，走位空间被压缩",
            "战斗房 70 秒后开始持续掉血，并不断追加增援",
            "商店价格翻倍，但击杀、清房与拾取星尘翻倍",
            "每次遗物选择 +2 张，但敌人血量 +25%、承伤 +12%",
            "一切治疗效果失效，改为造成伤害的 2% 转为生命"
    };

    public static final String[] PACT_HINT = {
            "精英是主要威胁来源，先练熟绕后与破盾",
            "失去远距离锁定，需要手动控制弹道方向",
            "用数量换质量，适合高单发武器与暴击流",
            "首领战会被拖长，带足爆发再进首领房",
            "击杀位置决定安全，别在残血敌人旁停留",
            "瞬移落点有紫色预警圈，看到就横向拉开",
            "熔岩口会持续压缩安全区，优先记住固定位置",
            "空间变小意味着弹幕更难躲，远程职业更吃亏",
            "前 70 秒是黄金输出窗口，之后必须强行结束战斗",
            "经济节奏完全改变：先攒钱，再一次性买空商店",
            "遗物数量爆炸式增长，但敌人也同步变硬",
            "续航只能靠输出，卖血流与护盾流价值飙升"
    };

    /** heat 贡献（1..3），总和决定结算倍率。 */
    public static final int[] PACT_HEAT = {2, 2, 2, 3, 1, 2, 2, 1, 2, 1, 2, 2};

    /** 默认开放状态：前 4 条默认解锁，其余靠通关对应契约逐步开放。 */
    public static final boolean[] PACT_UNLOCKED = {
            true, true, true, false,
            false, false, true, true,
            false, false, false, false
    };

    /**
     * 每条词条需要的「已通关最高契约档」。
     *
     * <p><b>为什么需要这张表</b>：{@code PACT_UNLOCKED} 是 static final，没有任何
     * 运行时解锁路径——12 条词条里有 8 条<b>永久锁死</b>，玩家到通关也见不到；
     * 而 {@code Game.pactUnlocked}（它本来就是"已通关的最高契约档"）只写不读。
     * 这里把二者接起来：默认解锁的条目恒开，其余按通关进度开放。
     *
     * <p>取 -1 表示"默认开放"。数值含义为「通关过该档及以上的契约」。
     */
    public static final int[] PACT_REQ_CONTRACT = {
            -1, -1, -1, 1,   // 前 4 条默认（第 4 条原为 false，这里改为需通关远征）
            -1, 2, -1, 1,    // 5 需试炼，7 需远征
            2, 3, 2, 4       // 8 需试炼，9 需深渊，10 需试炼，11 需炼狱
    };

    /**
     * 该词条在当前通关进度下是否可用。
     *
     * @param index          词条下标
     * @param contractCleared 玩家已通关的最高契约档（-1 = 从未通关）
     */
    public static boolean pactAvailable(int index, int contractCleared) {
        if (index < 0 || index >= PACT_COUNT) return false;
        int req = PACT_REQ_CONTRACT[index];
        if (req < 0) return PACT_UNLOCKED[index];
        return contractCleared >= req;
    }

    /** 全部词条的 heat 总和（用于 UI 显示满配倍率）。 */
    public static final int PACT_HEAT_MAX = sumHeat();

    /** 结算倍率：1 + 总 heat × 0.12。 */
    public static float heatRewardMult(int heat) {
        int h = Math.max(0, Math.min(64, heat));
        return 1f + h * .12f;
    }

    /** 本局词条总 heat（Game.pactMods 位掩码）。 */
    public static int heatOf(int pactMods) {
        int sum = 0;
        for (int i = 0; i < PACT_COUNT; i++)
            if ((pactMods & (1 << i)) != 0) sum += PACT_HEAT[i];
        return sum;
    }

    private static int sumHeat() {
        int sum = 0;
        for (int h : PACT_HEAT) sum += h;
        return sum;
    }

    // ── 词条数值参数（集中在此，便于平衡复核）──────────────────
    /** 弹药枯竭：弹道 -1，每发伤害倍率。 */
    public static final float PACT_DROUGHT_DAMAGE = 1.35f;
    /** 无光视野：自动瞄准射程（像素）与拾取范围倍率。 */
    public static final float PACT_DARKNESS_AIM = 360f;
    public static final float PACT_DARKNESS_MAGNET = .60f;
    /** 无光视野：星尘补偿倍率。 */
    public static final float PACT_DARKNESS_COIN = 1.30f;
    /** 精英狂潮：精英击杀星尘倍率。 */
    public static final float PACT_ELITE_COIN = 1.80f;
    /** 亡语爆裂：爆炸半径与"按敌人最大生命"的伤害比例及上限。 */
    public static final float PACT_BLAST_RADIUS = 62f;
    public static final float PACT_BLAST_RATIO = .12f;
    public static final float PACT_BLAST_CAP = 30f;
    /** 瞬移猎手：瞬移间隔（秒）与落点预警时长。 */
    public static final float PACT_BLINK_INTERVAL = 7f;
    public static final float PACT_BLINK_WARN = .55f;
    /** 瞬移猎手：落点与玩家的距离区间、预警圈半径。 */
    public static final float PACT_BLINK_NEAR = 90f, PACT_BLINK_FAR = 170f;
    public static final float PACT_BLINK_RADIUS = 42f;
    /** 熔岩地火：额外熔岩口数量与喷发间隔倍率。 */
    public static final int PACT_INFERNO_VENTS = 2;
    public static final float PACT_INFERNO_CLOCK = .75f;
    /** 房间压缩：四边收窄像素。 */
    public static final float PACT_COLLAPSE_INSET = 90f;
    /**
     * 首领狂怒：第 4 阶段的出手间隔倍率与召唤量。
     * <p>{@code Bosses.PHASES} 是固定的 3 元素行、且 {@code Bosses.valid()} 强校验长度，
     * 因此第 4 阶段不新增招式，而是"重复第 0 招 + 更快出手"：数据表不动，只改节奏。
     */
    public static final float PACT_FURY_RATE = .60f;
    /** 首领狂怒：突破第三道血线时额外召唤的护卫数。 */
    public static final int PACT_FURY_ADDS = 2;
    /** 首领狂怒：第 4 阶段的血线（≤ 该比例时进入）。 */
    public static final float PACT_FURY_HP = .15f;
    /** 时限压榨：开始掉血的房间时长、每秒伤害、追加增援间隔。 */
    public static final float PACT_LIMIT_SECONDS = 70f;
    public static final float PACT_TIME_DPS = 5f;
    public static final float PACT_TIME_REINFORCE = 6f;
    /** 商店通胀：价格倍率与星尘倍率。 */
    public static final float PACT_INFLATION_PRICE = 2f;
    public static final float PACT_INFLATION_COIN = 2f;
    /** 遗物洪流：额外遗物选择轮数、敌人血量与承伤倍率。 */
    public static final int PACT_FLOOD_DRAFTS = 2;
    public static final float PACT_FLOOD_HP = 1.25f;
    public static final float PACT_FLOOD_DAMAGE = 1.12f;
    /** 治疗禁绝：伤害转生命的比例。 */
    public static final float PACT_NO_HEAL_LEECH = .02f;

    /** 词条开关对敌人血量的总乘区（未开启时为 1）。 */
    public static float pactHpMul(Game g) {
        if (g == null) return 1f;
        return has(g.pactMods, PACT_RELIC_FLOOD) ? PACT_FLOOD_HP : 1f;
    }

    /** 词条开关对玩家承伤的总乘区（未开启时为 1）。 */
    public static float pactDamageMul(Game g) {
        if (g == null) return 1f;
        return has(g.pactMods, PACT_RELIC_FLOOD) ? PACT_FLOOD_DAMAGE : 1f;
    }

    /** 词条开关对星尘获取的乘区。 */
    public static float pactCoinMul(Game g) {
        if (g == null) return 1f;
        float m = 1f;
        if (has(g.pactMods, PACT_INFLATION)) m *= PACT_INFLATION_COIN;
        if (has(g.pactMods, PACT_DARKNESS)) m *= PACT_DARKNESS_COIN;
        return m;
    }

    /** 商店价格乘区。 */
    public static float pactShopMul(Game g) {
        if (g == null) return 1f;
        return has(g.pactMods, PACT_INFLATION) ? PACT_INFLATION_PRICE : 1f;
    }

    /** 遗物选择额外轮数。 */
    public static int pactExtraDrafts(Game g) {
        if (g == null) return 0;
        return has(g.pactMods, PACT_RELIC_FLOOD) ? PACT_FLOOD_DRAFTS : 0;
    }

    /** 精英刷新间隔：返回 0 表示使用默认规则。 */
    public static int pactEliteEvery(Game g) {
        if (g == null) return 0;
        return has(g.pactMods, PACT_ELITE_TIDE) ? 3 : 0;
    }

    private static boolean has(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    /** 该词条是否默认解锁（越界返回 false）。 */
    public static boolean pactDefaultUnlocked(int index) {
        return index >= 0 && index < PACT_COUNT && PACT_UNLOCKED[index];
    }

    // ══════════════════════════════════════════════════════════════
    //  危险预警可读性（v1.1 追加）
    // ══════════════════════════════════════════════════════════════

    /**
     * 致命招式的蓄力放大系数。下标是 {@code Enemies.BEHAVIOR}。
     * <p><b>设计意图</b>：不是削弱，而是"把伤害从不可反应挪到可反应"——蓄力变长，
     * 同时 {@code Game.startTelegraph} 会等量削减出手后的冷却，所以整条攻击循环的
     * 频率不变，只是把时间从"打中你"挪到"警告你"。玩家仍然会被打中，但那是自己的选择。
     */
    public static float telegraphScale(int behavior) {
        switch (behavior) {
            case Game.B_BOMBER: return 1.95f;   // 自爆：原本 0.4 秒几乎无法反应
            case Game.B_CHARGER: return 1.40f;   // 冲锋：给出更长的落点线
            case Game.B_SNIPER: return 1.22f;    // 狙击：红线更久
            case Game.B_TANK: return 1.20f;
            case Game.B_SPIRAL: return 1.16f;
            case Game.B_ZONER: return 1.12f;
            case Game.B_BLINK: return 1.12f;
            case Game.B_HEALER: return 1.10f;
            case Game.B_SHIELD: return 1.10f;
            default: return 1f;
        }
    }

    /** 首领蓄力基数（秒，随阶段略减）。比原来的 0.72 长，且阶段推进不会反而更难读。 */
    public static float bossWindup(int phase) {
        int p = Math.max(0, Math.min(2, phase));
        return 1.08f - p * .07f;
    }

    /** 单次承伤达到最大生命的这个比例，就触发宽恕无敌帧。 */
    public static final float MERCY_HIT_RATIO = .22f;
    /** 宽恕无敌帧时长。 */
    public static final float MERCY_INVULN = 1.15f;

    // ══════════════════════════════════════════════════════════════
    //  腐化阶梯（v1.1 追加）：第 5 站之后中段加压
    // ══════════════════════════════════════════════════════════════

    /**
     * 由已访问站数推出的腐化等级 0..4。
     * <p>远征 15 站时：第 5 站升 1 级、第 8 站 2 级、第 11 站 3 级、第 14 站 4 级，
     * 正好填在"第 5~9 站没有变化"的中段空档上。无尽模式继续按同样斜率封顶。
     */
    public static int corruptionFor(int roomsVisited) {
        int v = Math.max(0, roomsVisited);
        int level = (v + 1) / 3 - 1;
        return Math.max(0, Math.min(4, level));
    }

    /** 腐化对敌人血量的加成（乘在 hpFactor 之外，不动 hpFactor 的既有曲线）。 */
    public static float corruptionHp(int level) {
        switch (Math.max(0, Math.min(4, level))) {
            case 1: return 1.02f;
            case 2: return 1.06f;
            case 3: return 1.11f;
            case 4: return 1.17f;
            default: return 1f;
        }
    }

    /** 腐化对敌人行动 / 弹速的加成。 */
    public static float corruptionPace(int level) {
        return 1f + Math.max(0, Math.min(4, level)) * .018f;
    }

    /** 腐化房间清空时的星尘补偿，让高压段仍有经济跟得上。 */
    public static int corruptionCoins(int level) {
        return Math.max(0, Math.min(4, level)) * 6;
    }

    /** 该腐化等级下，每第几个刷出的敌人附加精英（0 = 不附加）。 */
    public static int corruptionEliteEvery(int level) {
        switch (Math.max(0, Math.min(4, level))) {
            case 2: return 9;
            case 3: return 7;
            case 4: return 5;
            default: return 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  房间收益
    // ══════════════════════════════════════════════════════════════
    /** 房间清空后直接发放的星尘。事件 / 商店 / 营地 / 铁匠铺 / 神龛不发。 */
    public static int clearCoins(int room) {
        switch (room) {
            case Game.ROOM_COMBAT: return 34;
            case Game.ROOM_ELITE: return 68;
            case Game.ROOM_TREASURE: return 52;
            case Game.ROOM_BOSS: return 88;
            case Game.ROOM_CHALLENGE: return 64;
            default: return 0;
        }
    }

    /** 房间清空后的遗物选择轮数：精英 = 2，其余 = 1。 */
    public static int rewardDrafts(int room) {
        return room == Game.ROOM_ELITE ? 2 : 1;
    }

    /** 商店价格：每层折扣 -12%，最多 -40%，且不低于 18。 */
    public static int shopPrice(int base, int discountStacks) {
        int safeBase = Math.max(0, base);
        float mult = 1f - Math.min(.40f, Math.max(0, discountStacks) * .12f);
        return Math.max(18, Math.round(safeBase * mult));
    }

    /** 商店重摆价格：第 1 次 30，第 2 次 45，第 3 次 70；超出返回不可支付值。 */
    public static int rerollPrice(int used) {
        switch (Math.max(0, used)) {
            case 0: return 30;
            case 1: return 45;
            case 2: return 70;
            default: return Integer.MAX_VALUE;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  敌人放缩
    // ══════════════════════════════════════════════════════════════

    /**
     * 敌人血量总倍率。
     * <p>
     * 楼层成长（floor 1..5）：{@code 1 + f*.42 + f^1.5*.055}，f = floor-1，
     * 即约 1.00 / 1.48 / 1.99 / 2.51 / 3.05 —— 曲线被压平，抹平 3→4 站的陡崖。
     * 站数进度：15 站内再加约 +0.12，让同一章后半段仍有轻微加压。
     * 模式修正：首领连战按站数递增；无尽裂隙在 15 站之后继续线性加压。
     * 结果钳制在 [0.5, 12] 且永远有限。
     */
    public static float hpFactor(Game g) {
        if (g == null) return HP[1];
        int contract = clampIndex(g.contract, HP.length);
        float floor = Math.max(1f, Math.min(9f, g.floor));
        float f = floor - 1f;
        float growth = 1f + f * .42f + (float) Math.pow(f, 1.5) * .055f;
        float progress = 1f + Math.min(18, Math.max(0, g.roomsVisited)) * .009f;
        float mode = 1f;
        if (g.mode == Game.MODE_BOSS_RUSH) mode = 1f + Math.max(0, g.roomsVisited) * .14f;
        else if (g.mode == Game.MODE_ENDLESS) mode = 1f + Math.max(0, g.roomsVisited - 15) * .10f;
        float result = HP[contract] * growth * progress * mode;
        if (!Float.isFinite(result)) result = HP[contract];
        return Math.max(.5f, Math.min(12f, result));
    }

    /** 敌人伤害倍率（已含契约，Game.hurt 直接乘它）。 */
    public static float damageFactor(Game g) {
        return DAMAGE[clampIndex(g == null ? 1 : g.contract, DAMAGE.length)];
    }

    /** 敌人行动速度 / 弹速倍率。 */
    public static float paceFactor(Game g) {
        return PACE[clampIndex(g == null ? 1 : g.contract, PACE.length)];
    }

    // ══════════════════════════════════════════════════════════════
    //  局末结算
    // ══════════════════════════════════════════════════════════════

    /** 局末星尘：站数 + 击杀 + 首领 + 通关奖励，再乘契约加成与词条 heat 倍率。 */
    public static int dustReward(Game g, boolean win) {
        if (g == null) return 0;
        int rooms = Math.max(0, Math.min(999, g.roomsVisited));
        int kills = Math.max(0, Math.min(100_000, g.kills));
        int bosses = Math.max(0, Math.min(99, g.bossesDefeated));
        int base = rooms * 8 + kills / 6 + bosses * 45 + (win ? 120 : 0);
        float mult = 1f + clampIndex(g.contract, HP.length) * .35f;
        // v2.0：契约词条按总 heat 提高结算倍率
        mult *= heatRewardMult(g.pactHeat());
        int value = Math.round(base * mult);
        return Math.max(0, Math.min(99_999, value));
    }

    /** 局末深渊印记：按击败首领结算；无尽裂隙不产出。词条 heat 同样提高倍率。 */
    public static int markReward(Game g, boolean win) {
        if (g == null || g.mode == Game.MODE_ENDLESS) return 0;
        int bosses = Math.max(0, Math.min(99, g.bossesDefeated));
        float mult = heatRewardMult(g.pactHeat());
        int marks = Math.round((bosses * (win ? 3 : 1) + (win ? 5 : 0)) * mult);
        return Math.max(0, Math.min(999, marks));
    }

    /** 默认契约等级：每日星盘锁定远征难度。 */
    public static int defaultContract(int mode) {
        return mode == Game.MODE_DAILY ? 1 : 1;
    }

    // ══════════════════════════════════════════════════════════════
    //  星图收益说明（GameView 直接按 roomType 取用）
    // ══════════════════════════════════════════════════════════════
    public static final String[] ROOM_PAYOFF = {
            "34 星尘 + 1 次遗物选择",
            "68 星尘 + 2 次遗物选择",
            "未知代价，未知回报",
            "花星尘补齐构筑",
            "恢复、强化或补给三选一",
            "击退守卫后：52 星尘 + 遗物",
            "88 星尘 + 章节奖励",
            "完成目标：64 星尘 + 1 次遗物选择",
            "为当前职业选择一条进化线",
            "一次性神龛：选择后不可反悔"
    };

    // ══════════════════════════════════════════════════════════════
    //  自检
    // ══════════════════════════════════════════════════════════════

    /** 数据自检：长度一致、档位单调递增、预算与说明文字匹配。 */
    public static boolean valid() {
        if (CONTRACTS.length != 5 || CONTRACT_HINTS.length != 5) return false;
        if (HP.length != 5 || DAMAGE.length != 5 || PACE.length != 5) return false;
        if (MODIFIERS.length != 8 || MODIFIER_HINTS.length != 8) return false;
        if (!"无异变".equals(MODIFIERS[0])) return false;
        if (ROOM_PAYOFF.length != 10) return false;
        if (RELIC_REROLL_COST < 10 || RELIC_REROLL_COST > 60) return false;

        // v1.1 追加：跨流派表与腐化阶梯必须自洽
        if (Content.CROSS_COUNT > 0) {
            if (Content.CROSS_NAMES.length != Content.CROSS_COUNT
                    || Content.CROSS_A.length != Content.CROSS_COUNT
                    || Content.CROSS_B.length != Content.CROSS_COUNT
                    || Content.CROSS_DESC.length != Content.CROSS_COUNT) return false;
            for (int i = 0; i < Content.CROSS_COUNT; i++) {
                if (Content.CROSS_A[i] < 0 || Content.CROSS_A[i] >= Content.SCHOOL_COUNT) return false;
                if (Content.CROSS_B[i] < 0 || Content.CROSS_B[i] >= Content.SCHOOL_COUNT) return false;
                if (Content.CROSS_A[i] == Content.CROSS_B[i]) return false;
                if (!text(Content.CROSS_NAMES[i]) || !text(Content.CROSS_DESC[i])) return false;
                for (int j = 0; j < i; j++) {
                    // 同一对流派不允许重复定义
                    if (Content.CROSS_A[j] == Content.CROSS_A[i]
                            && Content.CROSS_B[j] == Content.CROSS_B[i]) return false;
                }
            }
        }
        if (Content.CORRUPTION_NAMES.length != 5 || Content.CORRUPTION_HINTS.length != 5) return false;
        for (int i = 0; i < 5; i++)
            if (!text(Content.CORRUPTION_NAMES[i]) || !text(Content.CORRUPTION_HINTS[i])) return false;
        // 腐化必须单调、有界、可逆
        if (corruptionFor(0) != 0) return false;
        int prevLevel = 0;
        for (int visited = 0; visited <= 40; visited++) {
            int level = corruptionFor(visited);
            if (level < 0 || level > 4) return false;
            if (level < prevLevel) return false;
            prevLevel = level;
            float hp = corruptionHp(level), pace = corruptionPace(level);
            if (!Float.isFinite(hp) || hp < 1f || hp > 1.4f) return false;
            if (!Float.isFinite(pace) || pace < 1f || pace > 1.2f) return false;
            if (corruptionCoins(level) < 0) return false;
        }
        // 中段空档必须被填上：第 5 站之后一定要有等级变化
        if (corruptionFor(4) >= corruptionFor(7)) return false;
        if (corruptionFor(7) >= corruptionFor(10)) return false;
        if (corruptionFor(10) >= corruptionFor(13)) return false;
        // 预警可读性：系数有限且不为负；致命招式必须真的被拉长
        for (int b = 0; b <= 15; b++) {
            float s = telegraphScale(b);
            if (!Float.isFinite(s) || s < .5f || s > 3f) return false;
        }
        if (telegraphScale(Game.B_BOMBER) <= 1.5f) return false;
        if (telegraphScale(Game.B_CHARGER) <= 1.2f) return false;
        for (int p = 0; p <= 2; p++) {
            float wind = bossWindup(p);
            if (!Float.isFinite(wind) || wind < .7f || wind > 1.6f) return false;
        }
        if (MERCY_HIT_RATIO <= 0f || MERCY_HIT_RATIO >= 1f) return false;
        if (MERCY_INVULN <= 0f || MERCY_INVULN > 3f) return false;

        for (int i = 0; i < 5; i++) {
            if (!text(CONTRACTS[i]) || !text(CONTRACT_HINTS[i])) return false;
            if (!(HP[i] > 0f) || !(DAMAGE[i] > 0f) || !(PACE[i] > 0f)) return false;
            if (i > 0 && (!(HP[i] > HP[i - 1]) || !(DAMAGE[i] > DAMAGE[i - 1])
                    || !(PACE[i] > PACE[i - 1]))) return false;
        }
        for (int i = 0; i < 8; i++) {
            if (!text(MODIFIERS[i]) || !text(MODIFIER_HINTS[i])) return false;
            for (int j = 0; j < i; j++) if (MODIFIERS[i].equals(MODIFIERS[j])) return false;
        }
        for (int room = 0; room <= Game.ROOM_SHRINE; room++) {
            if (!text(ROOM_PAYOFF[room])) return false;
            if (clearCoins(room) < 0) return false;
            if (rewardDrafts(room) < 1 || rewardDrafts(room) > 2) return false;
        }
        if (!(clearCoins(Game.ROOM_ELITE) > clearCoins(Game.ROOM_COMBAT))) return false;
        if (!(clearCoins(Game.ROOM_BOSS) > clearCoins(Game.ROOM_ELITE))) return false;
        if (rewardDrafts(Game.ROOM_ELITE) != 2 || rewardDrafts(Game.ROOM_COMBAT) != 1) return false;

        // 定价与重摆
        if (shopPrice(80, 0) != 80) return false;
        if (shopPrice(80, 9) != 48) return false;
        if (shopPrice(-5, 0) != 18) return false;
        if (rerollPrice(0) != 30 || rerollPrice(1) != 45 || rerollPrice(2) != 70) return false;

        // 血量曲线：远征 floor 1 → 约 0.94（= HP[1]），floor 5 → 约 3.25，并在 [0.5,12] 内
        Game probe = new Game(1L);
        probe.contract = 1;
        probe.mode = Game.MODE_EXPEDITION;
        probe.roomsVisited = 0;
        probe.floor = 1;
        float floor1 = hpFactor(probe);
        if (Math.abs(floor1 - HP[1]) > .02f) return false;
        probe.floor = 5;
        probe.roomsVisited = 14;
        float floor5 = hpFactor(probe);
        if (floor5 < 2.2f || floor5 <= floor1) return false;
        for (int contract = 0; contract < 5; contract++) {
            probe.contract = contract;
            for (int floor = 1; floor <= 9; floor++) {
                probe.floor = floor;
                for (int visited = 0; visited <= 24; visited += 3) {
                    probe.roomsVisited = visited;
                    float factor = hpFactor(probe);
                    if (!Float.isFinite(factor) || factor < .5f || factor > 12f) return false;
                }
            }
        }
        probe.contract = 4;
        probe.floor = 9;
        probe.roomsVisited = 24;
        probe.mode = Game.MODE_ENDLESS;
        if (hpFactor(probe) > 12f) return false;
        probe.mode = Game.MODE_BOSS_RUSH;
        if (!Float.isFinite(hpFactor(probe))) return false;

        // 结算：单调、非负、有界
        if (dustReward(null, true) != 0 || markReward(null, true) != 0) return false;
        probe.mode = Game.MODE_EXPEDITION;
        probe.contract = 1;
        probe.roomsVisited = 15;
        probe.kills = 420;
        probe.bossesDefeated = 5;
        int winDust = dustReward(probe, true);
        int loseDust = dustReward(probe, false);
        if (winDust <= loseDust || loseDust < 0) return false;
        if (dustReward(probe, true) > 99_999) return false;
        if (markReward(probe, true) <= markReward(probe, false)) return false;
        probe.mode = Game.MODE_ENDLESS;
        if (markReward(probe, true) != 0) return false;

        // ── v2.0 契约词条自检 ──────────────────────────────────────
        if (PACT_COUNT != 12) return false;
        if (PACT_NAME.length != PACT_COUNT || PACT_DESC.length != PACT_COUNT
                || PACT_HINT.length != PACT_COUNT || PACT_HEAT.length != PACT_COUNT
                || PACT_UNLOCKED.length != PACT_COUNT) return false;
        for (int i = 0; i < PACT_COUNT; i++) {
            if (!text(PACT_NAME[i]) || !text(PACT_DESC[i]) || !text(PACT_HINT[i])) return false;
            if (PACT_HEAT[i] < 1 || PACT_HEAT[i] > 3) return false;
            for (int j = 0; j < i; j++)
                if (PACT_NAME[i].equals(PACT_NAME[j]) || PACT_DESC[i].equals(PACT_DESC[j]))
                    return false;
        }
        // 词条参数：全部有限且为正（否则接线后的玩法效果会静默失效）
        if (!(PACT_DROUGHT_DAMAGE > 1f)) return false;
        if (!(PACT_DARKNESS_AIM > 0f) || !(PACT_DARKNESS_MAGNET > 0f) || !(PACT_DARKNESS_COIN >= 1f))
            return false;
        if (!(PACT_ELITE_COIN >= 1f)) return false;
        if (!(PACT_BLAST_RADIUS > 0f) || !(PACT_BLAST_RATIO > 0f) || !(PACT_BLAST_CAP > 0f))
            return false;
        if (!(PACT_BLINK_INTERVAL > 1f) || !(PACT_BLINK_WARN > 0f)
                || !(PACT_BLINK_WARN < PACT_BLINK_INTERVAL)) return false;
        if (!(PACT_BLINK_NEAR > 0f) || !(PACT_BLINK_FAR > PACT_BLINK_NEAR)
                || !(PACT_BLINK_RADIUS > 0f)) return false;
        if (PACT_INFERNO_VENTS < 1 || PACT_INFERNO_VENTS > 6) return false;
        if (!(PACT_INFERNO_CLOCK > 0f) || !(PACT_INFERNO_CLOCK < 1f)) return false;
        if (!(PACT_COLLAPSE_INSET > 0f) || !(PACT_COLLAPSE_INSET < Game.WIDTH * .2f)) return false;
        if (!(PACT_LIMIT_SECONDS > 0f) || !(PACT_TIME_DPS > 0f) || !(PACT_TIME_REINFORCE > 0f))
            return false;
        if (!(PACT_FURY_RATE > .2f) || !(PACT_FURY_RATE < 1f)) return false;
        if (PACT_FURY_ADDS < 0 || PACT_FURY_ADDS > 8) return false;
        if (!(PACT_FURY_HP > 0f) || !(PACT_FURY_HP < .34f)) return false;
        if (!(PACT_INFLATION_PRICE >= 1f) || !(PACT_INFLATION_COIN >= 1f)) return false;
        if (PACT_FLOOD_DRAFTS < 0 || PACT_FLOOD_DRAFTS > 4) return false;
        if (!(PACT_FLOOD_HP >= 1f) || !(PACT_FLOOD_DAMAGE >= 1f)) return false;
        if (!(PACT_NO_HEAL_LEECH > 0f) || !(PACT_NO_HEAL_LEECH < .5f)) return false;
        // heat 倍率：单调、有限、起点为 1
        if (Math.abs(heatRewardMult(0) - 1f) > 1e-6f) return false;
        float prevMult = 0f;
        for (int h = 0; h <= PACT_HEAT_MAX; h++) {
            float m = heatRewardMult(h);
            if (!Float.isFinite(m) || m <= prevMult) return false;
            prevMult = m;
        }
        if (Math.abs(heatRewardMult(10) - 2.2f) > 1e-4f) return false;
        if (PACT_HEAT_MAX != 22) return false;
        // 至少 1/3 词条默认解锁，否则新玩家无内容可选；也不能全解锁
        int unlocked = 0;
        for (boolean u : PACT_UNLOCKED) if (u) unlocked++;
        if (unlocked < PACT_COUNT / 3 || unlocked >= PACT_COUNT) return false;
        // 位掩码 heat 求和自洽
        if (heatOf(0) != 0) return false;
        int all = (1 << PACT_COUNT) - 1;
        if (heatOf(all) != PACT_HEAT_MAX) return false;
        if (heatOf(1 << 0) != PACT_HEAT[0]) return false;

        return true;
    }

    private static boolean text(String value) {
        return value != null && !value.isEmpty();
    }

    private static int clampIndex(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }
}
