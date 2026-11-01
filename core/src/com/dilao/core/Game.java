package com.dilao.core;

import java.util.*;

/**
 * 平台无关的权威模拟。
 * <p>所有动作都会校验阶段；单机与联机共享同一套规则。
 * 本类不引用任何 Android API，可在 JVM 上直接跑回归测试。
 */
public final class Game {

    // ── 阶段（LOCKED）──────────────────────────────────────────────
    public static final int LOBBY = 0, FIGHT = 1, CHOICE = 2, WON = 3, LOST = 4, MAP = 5,
            EVENT = 6, SHOP = 7, CAMP = 8, ARMORY = 9, SHRINE = 10, GAMBLE = 11;

    // ── 模式（LOCKED）──────────────────────────────────────────────
    public static final int MODE_EXPEDITION = 0, MODE_ENDLESS = 1, MODE_BOSS_RUSH = 2, MODE_DAILY = 3;

    // ── 房间类型（LOCKED）──────────────────────────────────────────
    public static final int ROOM_COMBAT = 0, ROOM_ELITE = 1, ROOM_EVENT = 2, ROOM_SHOP = 3,
            ROOM_CAMP = 4, ROOM_TREASURE = 5, ROOM_BOSS = 6, ROOM_CHALLENGE = 7,
            ROOM_ARMORY = 8, ROOM_SHRINE = 9;

    // ── 世界常量（LOCKED）──────────────────────────────────────────
    public static final float WIDTH = 1120, HEIGHT = 620, STEP = 1f / 30;
    public static final int MAX_ENEMIES = 90, MAX_SHOTS = 380, MAX_GEMS = 180,
            MAX_EFFECTS = 160, MAX_RELICS = 60;

    // ── 敌人行为（LOCKED，与 Enemies.BEHAVIOR 对应）─────────────────
    public static final int B_CHASE = 0, B_SHOOTER = 1, B_CHARGER = 2, B_BOMBER = 3,
            B_SPLITTER = 4, B_SUMMONER = 5, B_ZONER = 6, B_SNIPER = 7, B_TANK = 8,
            B_SWARM = 9, B_BLINK = 10, B_HEALER = 11, B_SHIELD = 12, B_SPIRAL = 13,
            B_TURRET = 14, B_FLYER = 15;

    // ── 首领招式（LOCKED）──────────────────────────────────────────
    public static final int BOSS_RING = 0, BOSS_FAN = 1, BOSS_HOMING = 2, BOSS_CHARGE = 3,
            BOSS_BOMBARD = 4, BOSS_SPIRAL = 5, BOSS_SUMMON = 6, BOSS_FIELD = 7,
            BOSS_LASER = 8, BOSS_BLINK = 9;

    // ── 状态 ──────────────────────────────────────────────────────
    public int phase = LOBBY, room, floor = 1, level = 1, kills, tick, spawned, target,
            route, captain, mode, roomType, biome, layout, hazard, roomsVisited,
            roomsTotal = 12, routeIndex = -1, eventType, eventChoice = -1, shopRerolls,
            coins = 80, contract = 1, mutator, wave, waves = 3, nextWaveAt, roomStartKills,
            bossesDefeated, routeCount = 3, objective, rewardsLeft = 1, rerolls = 1,
            scouted, nextId = 10, tier;
    public float waveWait, damageTaken;
    // ══════════════════════════════════════════════════════════════
    //  事件机制状态（v2.2 · 13 种选择结构）
    //  v9/v10 保存过单笔摘要；v11 新增 delayedCharges 多笔账本。
    //  旧档会迁移成一笔来源未知的账，之后每笔都单独计时和结算。
    // ══════════════════════════════════════════════════════════════
    /**
     * 「血债」层数（TYPE 8 风险累积）：每层在结算时按 {@code delayedAmount} 比例扣当前生命。
     * <p>选择 B 选项（抽身）可退一层，但收益也因此打折——这是"越贪越贵"的旋钮。
     */
    public int eventDebt;
    /**
     * 兼容 v9/v10 的最早到期账目摘要，供旧测试与旧档格式使用。
     * 真实账本为 {@link #delayedCharges}，不可用以下三字段判断总笔数。
     */
    public int delayedKind;
    /** 距离结算还剩几站（{@link #advance()} 每次进入下一站时递减）。 */
    public int delayedRooms;
    /** 结算量：生命币种为<b>最大生命的比例</b>（0..1）；星尘币种为<b>整数星尘</b>。 */
    public float delayedAmount;
    /** 挂账结算类型：不结算。 */
    public static final int DELAY_NONE = 0;
    /** 挂账结算类型：按最大生命比例扣血。 */
    public static final int DELAY_HP = 1;
    /** 挂账结算类型：扣星尘。 */
    public static final int DELAY_COINS = 2;
    /** v11：同一时间允许多笔独立挂账，避免后来的事件冲掉原本欠下的代价。 */
    public static final int MAX_DELAYED_CHARGES = 16;
    /** 从 v9/v10 单笔旧档迁移而来，来源已不可追溯。 */
    public static final int DELAY_SOURCE_LEGACY = 0;
    /** TYPE 8 血债专用来源；加层/减层只修改自身，不动其他事件的借款。 */
    public static final int DELAY_SOURCE_DEBT = 8;
    public static final class DelayedCharge {
        public int kind, rooms, source;
        public float amount;
        public DelayedCharge(int kind, int rooms, float amount, int source) {
            this.kind = kind;
            this.rooms = rooms;
            this.amount = amount;
            this.source = source;
        }
    }
    /** 欠款账本按加入顺序保留；Codec v11 独立写入每一笔。 */
    public final ArrayList<DelayedCharge> delayedCharges = new ArrayList<>();
    /**
     * 上一笔挂账的终局播报，供 UI / 探针读取。
     * <p>挂账只影响"下一站开始时"的一次性结算，不参与战斗判定。
     */
    public String delayedNote = "";
    /** 房间僵持计时：连续无击杀且配额已刷完时累加，用于兜底避免卡房。 */
    public float stallClock;
    /** 元进度解锁的契约上限（Session 注入），仅用于 UI 展示与难度提示。 */
    public int pactUnlocked;
    // ── v2.0 支柱 1：契约词条 ──────────────────────────────────────
    /** 本局开启的词条位掩码（下标见 Balance.PACT_*，最多 31 条）。 */
    public int pactMods;
    /** 本局总 heat（由 pactMods 推导，切换时同步缓存，并进入存档）。 */
    public int pactHeat;
    /**
     * 「时限压榨」的追兵计时：距下一次追加增援还剩多少秒。
     * <p>每房间由 {@link #beginCombat()} 重置为 {@link Balance#PACT_TIME_REINFORCE}，
     * 并由 Codec v10 保存。读档不能提前刷出追兵，破坏后续战斗确定性。
     */
    float pactReinforceClock;
    /** 「时限压榨」的灼烧结算节拍（每秒一次，避开 hurt 的无敌帧吞伤害）。 */
    float pactBurnClock;
    /** 「时限压榨」是否已播报过"黄金窗口结束"，避免每帧刷 notice。 */
    boolean pactLimitAnnounced;

    // ── v2.0 支柱 2：天赋牌（Doctrine）─────────────────────────────
    /**
     * 本局装备的天赋牌下标（0..17）；-1 = 未装备。
     * <p>由 {@code Session} 在 {@code startSolo()} 之前写入，core 侧只读，不查 {@code Meta} 表。
     * 钩子 id 见 {@link DoctrineHooks}，必须与 {@code Meta.H_*} 数值一致。
     */
    public int doctrine = -1;
    /** 主钩子 id（{@link DoctrineHooks#H_NONE} = 无）。 */
    public int doctrineHook;
    /** 副钩子 id（代价部分；{@link DoctrineHooks#H_NONE} = 无）。 */
    public int doctrineSideHook;
    /** 参数：[0]/[1] = 主钩子参数，[2] = 副钩子参数，[3] = 预留。 */
    public final float[] doctrineParams = new float[4];
    /**
     * #4 献祭契约：升级时累积、下次三选一补发的额外次数。
     * <p>升级发生在战斗中（{@code phase == FIGHT}），不能直接进 CHOICE，
     * 因此先累积，在 {@link #prepareChoice(int)} 时一次性并入 {@code rewardsLeft}。
     */
    public int doctrineBonusChoices;
    /**
     * #11 血脉共鸣：距上次"每 N 杀回复"已经过去的击杀数。
     * <p>进 Codec v8 —— 否则读档会把计数归零，等于每次读档白送一次回复。
     */
    public int doctrineKillClock;
    /**
     * #8 极寒迟滞：本房间限时倍率（1 = 原始限时，0.8 = 限时 -20%）。
     * <p>0 表示"未设置"，此时按房型硬编码兜底；由 {@link #beginCombat()} 每房间重置。
     */
    public float roomTimeLimit;

    public boolean soloRun, skipNextNode, mirror, campAfterBoss, shrineUsed;
    public long seed;
    public float time, roomTime, spawnClock, charge, xp, xpGoal = 55, choiceClock,
            eventClock, comboClock, combo, xpTotal, dustEarned, marksEarned;
    public String notice = "等待探险者集合", sceneTitle = "", sceneHint = "";

    // ── v1.1 追加：腐化阶梯（中段加压）────────────────────────────
    /** 当前腐化等级 0..4，由 {@link Balance#corruptionFor(int)} 在进入房间时推导。 */
    public int corruption;
    /** 本房间叠加的第二条异变（0 = 无）。星图仍只显示第一条。 */
    public int mutator2;
    /** 上次播报过的腐化等级，用于只在跨级时写入 notice（不参与存档）。 */
    public int corruptionAnnounced;

    public final int[] routeOptions = {0, 1, 2}, shopOffers = {-1, -1, -1},
            history = new int[256], shopUsed = {0, 0, 0};
    public final int[] shopPrices = {-1, -1, -1};
    public final boolean[] shopSold = {false, false, false};
    /**
     * v2.3 #3「买空送重摆」：本次进店是否已经领过那一次免费重摆。
     *
     * <p>由 Codec v10 持久化，防止玩家通过读档重复领取免费重摆。
     * 与 {@code shopSold} 一起恢复，保证交易经济在存读档后完全一致。
     */
    boolean shopBuyoutRewardUsed;
    /**
     * 本次进店的免费重摆余额（v2.3 #3）。
     *
     * <p>用"余额"而不是"把 shopRerolls 减一"，是为了保住
     * {@code shopRerolls} 的唯一含义：<b>本店已付费重摆的次数</b>（UI 的价格阶梯就读它）。
     * 免费重摆既不增加次数，也不打折后续价格——否则"买空"会变成"重摆永久变便宜"的复利。
     */
    int shopFreeRerolls;

    public final ArrayList<Player> players = new ArrayList<>();
    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Shot> shots = new ArrayList<>();
    public final ArrayList<Gem> gems = new ArrayList<>();
    public final ArrayList<Effect> effects = new ArrayList<>();
    public final ArrayList<Block> blocks = new ArrayList<>();
    public final ArrayList<Prop> props = new ArrayList<>();
    public final ArrayList<Hazard> hazards = new ArrayList<>();
    public final ArrayList<Turret> turrets = new ArrayList<>();
    public final ArrayList<Minion> minions = new ArrayList<>();
    public final RunRandom random;
    final Rooms.Navigation navigation = new Rooms.Navigation();
    private final ArrayList<Enemy> pendingEnemies = new ArrayList<>();


    // ══════════════════════════════════════════════════════════════
    //  实体定义
    // ══════════════════════════════════════════════════════════════

    public static final class Player {
        public int id, role, kills, chosen = -1, vote, dashSerial, skillSerial, shotCount,
                synergyMask, weaponForm, evolution, evolutionsTaken, ultSerial, ultCharge;
        public String name = "探险者";
        /**
         * v2.1：铁匠铺里是否已经主动选过武器形态。
         * <p>不能用 {@code weaponForm != 0} 代替——「标准枪管」是形态 0，
         * 同样是一次有效选择，否则选标准的玩家会被反复要求重选。
         */
        public boolean formChosen;
        public boolean connected = true, down;
        public float x = 560, y = 430, hp = 120, maxHp = 120, speed = 200, damage = 20,
                interval = .36f, attack, dx, dy, ax = 1, ay, manual;
        public float dashCd, skillCd, invuln, revive, disconnectedFor, crit = .06f,
                magnet = 100, xpBonus, dashFactor = 1, buff, buffKind, frostHealCd,
                ultCd, ultReady;
        public boolean nextCrit;
        public float reactionCd, leechBudget = 6, regenClock, shield, shieldMax;
        public int reactions, multishot = 1, pierce, slow, leech, explode, armorBreak,
                soul, discount, bossLoot, thorns, execute, knockback, knockbackPower;
        // 遗物 / 进化 提供的累计乘区（由 recompute 计算）
        public float bonusPower, bonusRate, bonusArea, bonusSpeed, bonusHp, bonusCrit,
                bonusMagnet, bonusRegen, bonusXp, bonusCoin;
        /**
         * 闪避冷却的**外部**修正（元进度 / 天赋牌）。
         * <p>闪避冷却 = 基准 1.0 × 遗物乘区 × 本字段。它与遗物乘区分开，
         * 才不会在 recompute 里互相吞掉（见 {@code dashFactorMul} 的注释）。
         */
        public float dashGrow = 1f;

        // ══════════════════════════════════════════════════════════
        //  元进度的第二组外部修正（v2.2）
        //
        //  Meta 里有 7 个节点 getter 全项目零消费：承伤减免、生命回复、
        //  攻速、攻击范围、星尘掉落、遗物栏上限、事件收益——合计 35 级、
        //  3150 星尘，占整棵树 22.9%。玩家点了节点，面板没有任何变化。
        //  这里给它们一个**独立于遗物**的存放处，由 Session 注入、
        //  recompute / 战斗代码读取，保证不会被遗物重算冲掉。
        // ══════════════════════════════════════════════════════════
        /** 承伤减免比例（0..0.15，乘法作用于最终承伤）。 */
        public float metaArmor;
        /** 生命回复加成比例（0..0.40，作用于 heal() 的正向治疗量）。 */
        public float metaRegen;
        /** 攻速加成比例（0..0.15，与 attackSpeedMul 相乘）。 */
        public float metaAtkSpeed;
        /** 攻击范围加成比例（0..0.20，作用于弹丸存活距离）。 */
        public float metaRange;
        /** 星尘掉落加成比例（0..0.40）。 */
        public float metaCoin;
        /** 事件收益加成比例（0..1+）。 */
        public float metaEvent;
        /** 遗物栏上限增量（种类数）。 */
        public int metaRelicSlots;
        /** 经验加成比例（由元进度节点 10 注入，与遗物加数相加）。 */
        public float metaXp;
        public int flags, extraProjectiles, chainCount, orbitCount, mineCount,
                splitCount, ricochetCount, beamCount, summonCount, shieldLayers, stunChance;
        // ── v2.0：让 Content.F_* 里"零消费"的标志位真正产生效果 ────────
        /** F_AURA：持续范围伤害光环层数（0 = 无）。 */
        public int auraCount;
        /** F_SLOWFIELD：持续减速领域层数（0 = 无）。 */
        public int slowFieldCount;
        /** F_LIFESTEAL：命中吸血点数（按伤害比例回血）。 */
        public int lifeSteal;
        /** F_TAUNT：嘲讽层数（敌人优先锁定该玩家）。 */
        public int taunt;
        /** F_CHARGE：蓄力层数（静止蓄力后强化下一发）。 */
        public int chargeCount;
        /** F_SPLITSHOT：命中后向两侧分裂的弹道数。 */
        public int splitShot;
        /**
         * v2.0 进化机制原型（{@link Evolutions#MECHANIC}）。
         * <p>0 = 纯 FLAGS；1..14 = 需要专用战斗逻辑的原型。多条进化时取第一个非 0。
         */
        public int evoMechanic;
        /**
         * v2.0 P0-6：当前持有的机制原型位掩码（{@code 1 << Evolutions.MECHANIC[i]}）。
         * <p>与 {@link #evoMechanic}（只记第一个非 0 原型）不同，这里记录<b>全部</b>原型：
         * 一个玩家可能同时持有 2～3 条进化，机制必须叠加而不是互相覆盖。
         * 由 {@link #recompute} 刷新；分发入口见 {@code Game#applyMechanic}。
         */
        public int mechanicMask;
        // ── v2.0 P0-6：15 个机制原型的运行态（全部进 Codec v8）──────────
        /** M2 连击递增：当前连击目标 / 层数 / 攻速加成 / 断连计时。 */
        public int comboTargetId = -1, comboCount;
        public float comboRateBonus, comboTimer;
        /** M3 低血动态：攻速倍率 1..2 / 额外弹丸 0..2。 */
        public float lowHpRateMul = 1f;
        public int lowHpExtraShots;
        /** M2+M3+M13 汇总出的攻击速度乘区（fire 间隔 = interval / 本值）。 */
        public float attackSpeedMul = 1f;
        /** M8 二段判定冷却（0.25s）。 */
        public float secondStrikeCd;
        /** M12 状态转化冷却。 */
        public float mechReactCd;
        /** M9 全局计时：倒计时 / 当前间隔（每次 ×0.92，下限 2.5s）。 */
        public float globalClock, globalInterval;
        /** M13 击杀堆叠：层数与攻速乘区。 */
        public int feastStacks;
        public float feastRateMul = 1f;
        /** M11 位移联动冷却（3s）。 */
        public float linkClock;
        /** M14 支援转化冷却（0.5s）。 */
        public float supportCd;
        /** M5 场地生成节拍。 */
        public float siteClock;
        /** M10 剥离冷却。 */
        public float drainCd;
        /** M14-7 治疗光爆冷却。 */
        public float healBurstCd;
        /**
         * 机制护盾（M7 溢出转化 / M10 剥离）。
         * <p>不复用 {@link #shield}：后者受 F_SHIELD 与 recompute 的 shieldMax 重置支配，
         * 而这两条进化本身并不带 F_SHIELD。独立字段能保证"转化来的护盾"真的能吸收伤害。
         */
        public float mechShield;
        /**
         * v2.0 P0-4：范围乘区（由遗物 / 进化的 AREA 累加而来）。
         * <p>此前 AREA 只被累加从不读取，是纯死属性。现在它会放大：
         * 光环半径、减速领域半径、地雷半径、爆炸半径。
         */
        public float areaBonus;
        /** 机制内部计时器（不入存档；读档后按 0 重启，最多损失一次周期）。 */
        public float auraClock, mineClock, teleportClock, chargeClock, chargeHold;
        // ── v2.0 支柱 2：天赋牌的玩家级状态（全部进 Codec v8）─────────
        /** #13 过载运转：距离下一次强制过热的累计秒数。 */
        public float overheatClock;
        /** #13 过载运转：过热停顿剩余秒数（> 0 时跳过开火）。 */
        public float overheatStun;
        /** #2 疾影步：闪避后的攻速增益剩余秒数与增量（+0.60 = 攻速 ×1.6）。 */
        public float dashHaste, dashHasteMul;
        public int[] offers = {0, 1, 2};
        public final int[] relics = new int[MAX_RELICS];
        public final int[] evolveOffer = {-1, -1, -1};
        public final boolean[] evolved = new boolean[Evolutions.COUNT];
        /**
         * v2.3 #2：上一次发给这位玩家的三张牌（重掷时要避开的那一手）。
         *
         * <p><b>刻意 transient，不进 Codec</b>：它只是"刚才给你看了什么"的记忆，
         * 不是游戏状态。读档后重置为 -1 的后果最多是"读档后的第一次重掷可能重复上一手"，
         * 而升 Codec.VERSION 的代价是全员联机版本不兼容——不划算。
         * 用 {@code transient} 明确标记，任何将来把 Player 整体序列化的改动都不会带上它。
         */
        public final transient int[] lastOffer = {-1, -1, -1};

        // ── v1.1 追加（不参与 Codec 序列化，restored() / recompute() 会重建）──
        /** 已激活的跨流派组合位掩码，下标见 {@link Content#CROSS_NAMES}。 */
        public int crossMask;
        /** 本局已发现过的跨流派组合（用于只播报一次、以及图鉴）。 */
        public int crossSeen;
        /** 疾风+余烬：移速加成的 35% 转成的伤害乘区（recompute 写入）。 */
        public float crossSpeedPower;
        /** 虚空+疾风：闪避后的短时增伤剩余时间。 */
        public float crossRush;

        public boolean has(int flag) { return (flags & flag) != 0; }
        /** 某跨流派组合是否激活（越界安全）。 */
        public boolean cross(int i) {
            return i >= 0 && i < Content.CROSS_COUNT && (crossMask & (1 << i)) != 0;
        }
    }

    public static final class Enemy {
        public int id, type, stage, variant, owner = -1, affix, phaseTriggers,
                behavior, bossIndex = -1, summonClock, addCount;
        public float guard, chargeTime, chargeVx, chargeVy;
        /**
         * v2.1：首领冲锋的一次性命中伤害与"本次冲锋是否已经撞过玩家"标记。
         * <p>此前 {@link Bosses#CHARGE_DAMAGE} 这张表从未被读——三个冲锋型首领
         * 全部走 {@code hurt(q, 20)} 硬编码，体型/阶段差异完全丢失。
         */
        public float chargeDamage;
        public boolean chargeHit;
        /**
         * v2.1：护盾词缀（affix 4）的再生计时。护盾被打空后，脱离受击 4 秒开始
         * 每秒回 12% 最大护盾——给玩家「先破盾再爆发」的节奏窗口，而不是无限消耗。
         */
        public float shieldClock;
        public float shieldMax;
        public float hurtClock;
        public float x, y, hp, maxHp, r = 16, speed = 65, cooldown = 1.5f, telegraph,
                tx, ty, slow, hit, burn, burnClock, armor, blinkClock, healClock,
                shield, flash, stun;
        public boolean elite, summoned, splitDone;

        // ── v2.0 词条「瞬移猎手」的运行态 ─────────────────────────────
        //
        // 节奏用**已序列化**的 blinkClock 承载（Codec 早已写入它，此前无人读），
        // 因此读档后"还有几秒瞬移"不会丢；只有 0.55 秒的落点预警窗口是瞬态的，
        // 与 Player 的 auraClock / mineClock 同口径（读档后最多重来一次预警）。
        // 若读档正好落在预警窗口内，blinkX/blinkY 会是 0，由 isBlinkDestinationValid()
        // 判定为"无效落点"并直接取消本次预警——绝不会把敌人瞬移到 (0,0)。
        /** 落点坐标（瞬态）。 */
        public float blinkX, blinkY;
        /** 落点预警剩余秒数；> 0 表示正在预警，到 0 时真正瞬移。 */
        public float blinkWarn;

        // ── v1.1 追加：危险预警（UI 可直接读取，缺省时退回 Enemies.TELEGRAPH）──
        /** 本次蓄力的总时长；0 表示当前没有蓄力。 */
        public float windup;
        /** 预警危险等级 0=普通 1=重伤 2=致命。UI 据此换色 / 加粗 / 加音效。 */
        public int threat;
        /** 蓄力进度 0..1，由 update 维护；-1 表示未在蓄力。 */
        public float windupT;

        /**
         * 预警可读性：0 = 没有蓄力，1 = 刚开始，越大越接近出手。
         * <p>UI 用它驱动"危险圈收紧 / 红线变亮"；不必自己算除法，也不会除零。
         */
        public float windupProgress() { return windupT; }
    }

    public static final class Shot {
        public int id, owner, pierce, slow, explode, lastHit = -1, element, chain,
                ricochet, homing, flags, splitCount;
        public float x, y, vx, vy, damage, r = 5, life = 1.8f, radius;
        public boolean enemy, crit;
        public final int[] hits = new int[10];
        public int hitCount;
        /**
         * M1 回旋镖：0 = 普通弹丸；1 = 去程（首次命中后折返）；2 = 返程（不再折返）。
         * <p>返程需要绕过 {@link #hits} 才能再次命中同一目标，折返时会被清空。
         */
        public int boomerang;
        /**
         * v2.0 支柱 2 #6 霜封：命中后施加的减速秒数（0 = 沿用引擎默认的 1.5s）。
         * <p>与 {@link #slow}（int 标记）分工：标记决定"是否会减速 / 是否算冰痕"，
         * 本字段决定"减速多久"。只在 {@code slow > 0} 时有意义，并进 Codec v8。
         */
        public float slowSeconds;
    }

    public static final class Gem {
        public int id, kind;   // kind 0=经验 1=星尘 2=生命 3=能量
        public float x, y, value;
    }

    public static final class Effect {
        public int id, type, color;
        public float x, y, r, life, maxLife, value;
    }

    public static final class Block {
        public float x, y, w, h;
        public int kind;   // 0 石柱 1 书架 2 熔岩块 3 冰晶
        public Block(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    public static final class Prop {
        public int id, type;
        public float x, y, hp;
        public boolean opened;
    }

    public static final class Hazard {
        public int id, kind;   // 0 火 1 冰 2 毒 3 熔岩 4 虚空
        /** v2.0：布置者玩家 id（-1 = 环境危害）。玩家地雷只伤敌人，不伤玩家。 */
        public int owner = -1;
        public float x, y, r, clock, active;
    }

    public static final class Turret {
        public int id, owner, kind;
        public float x, y, life, cooldown;
    }

    public static final class Minion {
        public int id, owner, kind;
        public float x, y, life, cooldown, angle;
    }

    // ══════════════════════════════════════════════════════════════
    //  构造与玩家
    // ══════════════════════════════════════════════════════════════

    public Game(long seed) {
        this.seed = seed;
        random = new RunRandom(seed);
        Arrays.fill(history, -1);
        Arrays.fill(shopPrices, -1);
    }

    /**
     * 分配实体 id。
     *
     * <p><b>为什么有回绕</b>：{@code Codec} 读档时用 {@code range(nextId, 10, 5_000_000)}
     * 做健全性校验。{@code nextId} 是单调递增且会进存档的，实测约 1.45 id/秒，
     * 正常一局（8–16 分钟）只用掉一千出头。但无尽模式理论上可以把计数推过 500 万，
     * 那一刻存档会被自己的校验判成坏档——**玩家丢进度却不报错**。
     *
     * <p>回绕的做法是：越过上限时从 {@link #ID_FLOOR} 重新起步，并跳过仍被存活实体
     * 占用的 id。id 的唯一性只需要在「当前存在的实体之间」成立——弹丸的命中表、
     * 敌人的 targeting 都是短生命周期比较，不跨代际。
     */
    public int allocateId() {
        if (nextId >= ID_CEIL) {
            nextId = ID_FLOOR;
            int guard = 0;
            while (idLive(nextId) && guard++ < ID_CEIL - ID_FLOOR) nextId++;
        }
        return nextId++;
    }

    /** id 回绕的起点（须 >= Codec 的下界 10，留出玩家 1..9 的余量）。 */
    static final int ID_FLOOR = 16;
    /** id 回绕的上限（须 < Codec 校验上界 5_000_000，留 10 万安全余量）。 */
    static final int ID_CEIL = 4_900_000;

    /** 该 id 当前是否被某个存活实体占用。 */
    private boolean idLive(int id) {
        for (Enemy e : enemies) if (e.id == id) return true;
        for (Enemy e : pendingEnemies) if (e.id == id) return true;
        for (Shot s : shots) if (s.id == id) return true;
        for (Gem g : gems) if (g.id == id) return true;
        for (Hazard h : hazards) if (h.id == id) return true;
        for (Turret t : turrets) if (t.id == id) return true;
        for (Minion m : minions) if (m.id == id) return true;
        return false;
    }

    public Player player(int id) {
        for (Player p : players) if (p.id == id) return p;
        return null;
    }

    /** 创建玩家并按职业初始化基础属性。仅在 LOBBY 阶段可用。 */
    public Player addPlayer(int id, String name, int role) {
        if (players.size() >= 4 || player(id) != null || phase != LOBBY) return null;
        if (role < 0 || role >= Content.ROLE_COUNT) return null;
        Player p = new Player();
        p.id = id;
        p.name = safeName(name);
        p.role = role;
        p.x = 460 + players.size() * 65;
        p.y = 475;
        applyRoleBase(p, role);
        players.add(p);
        if (players.size() == 1) captain = id;
        return p;
    }

    /**
     * 职业基础面板。
     *
     * <p><b>唯一真相源</b>：全部数值都取自 {@code roleBase*()} 系列静态方法（{@code recompute}
     * 也用同一批）。历史上这里是一张独立的 switch 表，与 {@code roleBase*()} 有 13 处不一致，后果：
     * <ul>
     *   <li>被 {@code captureGrowth} 捕获的字段（damage/speed/crit）→ 变成<b>幻影永久成长</b>，写进存档后永久偏高</li>
     *   <li>不被捕获的字段（interval/multishot/pierce）→ 首次 {@code recompute} 就<b>直接损失</b></li>
     * </ul>
     * 因此这里<b>只能调用</b> {@code roleBase*()}，不得再写任何字面量。
     * {@code CoreTests} 的 D2 已从 warn 升级为断言，任何重新分叉都会被拦下。
     */
    // D2-FIX: applyRoleBase 以 roleBase*() 为唯一真相源
    private void applyRoleBase(Player p, int role) {
        p.damage = roleBaseDamage(role);
        p.interval = roleBaseInterval(role);
        p.speed = roleBaseSpeed(role);
        p.crit = roleBaseCrit(role);
        p.multishot = roleBaseMultishot(role);
        p.pierce = roleBasePierce(role);
        p.slow = roleBaseSlow(role);
        p.leech = roleBaseLeech(role);
        p.explode = roleBaseExplode(role);
        // 圣歌（role 7）额外带拾取范围加成；其余职业用默认值。
        p.magnet = baseMagnet() + (role == 7 ? 35 : 0);
        p.maxHp = p.hp = roleBaseHp(role);
        p.weaponForm = 0;
        p.formChosen = false;
        p.evolution = 0;
        p.flags = 0;
        p.ultCd = 22f;
        p.ultReady = 0;
    }

    private String safeName(String name) {
        String s = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (s.isEmpty()) return "探险者";
        return s.substring(0, Math.min(12, s.length()));
    }

    /** 联机：房主开始远征。 */
    public boolean start(int id) {
        return beginRun(id, MODE_EXPEDITION, false);
    }

    /** 单机：指定模式开局。 */
    public boolean startSolo(int id, int mode) {
        return beginRun(id, mode, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  契约词条（v2.0 支柱 1）
    // ══════════════════════════════════════════════════════════════

    /**
     * 在 LOBBY 阶段切换一条契约词条。返回切换后的状态是否"已开启"。
     * <p>只允许在大厅（尚未开局）修改，保证已开始的局内规则不被中途破坏，
     * 也让存档里的 pactMods 与实战规则始终一致。
     *
     * @param index 词条 id（{@code Balance.PACT_*} / 0..PACT_COUNT-1）
     * @return 该词条切换后是否为开启状态；非法调用返回 false
     */
    public boolean togglePact(int index) {
        if (phase != LOBBY || index < 0 || index >= Balance.PACT_COUNT) return false;
        pactMods ^= (1 << index);
        pactHeat = Balance.heatOf(pactMods);
        return (pactMods & (1 << index)) != 0;
    }

    /** 本局总 heat。以 pactMods 为准实时计算，避免读档后缓存失配。 */
    public int pactHeat() {
        pactHeat = Balance.heatOf(pactMods);
        return pactHeat;
    }

    /**
     * 统一治疗入口（v2.0 词条「治疗禁绝」）。
     * <p>开启该词条后，一切主动/被动治疗都返回 0，玩家只能靠"造成伤害转生命"维生。
     *
     * @return 实际回复量
     */
    public float heal(Player p, float amount) {
        if (p == null || p.down || !(amount > 0f)) return 0f;
        if (hasPact(Balance.PACT_NO_HEAL)) return 0f;
        // ── v2.0 支柱 2 #10 荆棘誓约：所有回复效果 -60% ────────────────
        // 乘在治疗入口而不是各个调用点：升级/营火/首领房/遗物再生/技能治疗
        // 全都走这里，一处改完全部生效，也不会漏掉将来新增的回复来源。
        amount *= doctrineRegenMul();
        // v2.2：元进度「生命回复 +8%/级」（节点 2）。同样乘在治疗入口，
        // 一处覆盖升级/营火/首领房/遗物再生/技能治疗全部来源。
        amount *= 1f + Math.max(0f, Math.min(.40f, p.metaRegen));
        if (!(amount > 0f)) return 0f;
        float before = p.hp;
        p.hp = Math.min(p.maxHp, p.hp + amount);
        float actual = Math.max(0f, p.hp - before);
        // ── v2.0 P0-6 M7 溢出转化（进化 23 生命洪流）：溢出量转机制护盾 ──
        // 必须放在治疗入口（不是 hurt），否则"治疗满了才转盾"的因果就反了。
        if (actual > 0f && p.mechanicMask != 0 && p.evolved[23]
                && Evolutions.mechanic(23) == 7) {
            float overflow = amount - actual;
            if (overflow > 0f) {
                float cap = p.maxHp * .4f;
                p.mechShield = Math.min(cap, p.mechShield + overflow * (.6f + Evolutions.POWER[23]));
            }
        }
        // ── v2.0 P0-6 M14-7 愈光之环（进化 7）：治疗在目标周围产生光爆 ──
        if (actual > 0f && p.mechanicMask != 0 && p.evolved[7]
                && Evolutions.mechanic(7) == 14) {
            healBurst(p, actual);
        }
        return actual;
    }

    /** 指定词条是否开启。 */
    public boolean hasPact(int index) {
        return index >= 0 && index < Balance.PACT_COUNT && (pactMods & (1 << index)) != 0;
    }

    /** 关闭全部词条（大厅里"重置"用）。 */
    public void clearPacts() {
        if (phase != LOBBY) return;
        pactMods = 0;
        pactHeat = 0;
    }

    // ══════════════════════════════════════════════════════════════
    //  天赋牌（v2.0 支柱 2）：core 侧钩子分发
    //
    //  数据源是 Session 写进来的 4 个字段（doctrine / doctrineHook /
    //  doctrineSideHook / doctrineParams），core 不查 Meta 表（不变式 5）。
    //
    //  分三类落点：
    //   * 开局一次性（beginRun → applyDoctrineStart）
    //   * 乘区类（recompute / hurt / fire 现场读）
    //   * 行为类（伤害、击杀、闪避、开火、升级等事件点）
    //
    //  所有判定都走 `doctrineHook == H_X` / `doctrineSideHook == H_X` 的双独立检查：
    //  H_TAKEN_DAMAGE_MUL / H_MAXHP_MUL 等既可能是主钩子也可能是副钩子（#16 / #5），
    //  只写一次会漏掉另一张牌。
    // ══════════════════════════════════════════════════════════════

    /**
     * 装备一张天赋牌。**必须**在 {@link #startSolo(int, int)} / {@link #start(int)} 之前调用。
     *
     * <p>这是 {@code Session} 唯一的接线入口：core 只保存牌号与钩子，不依赖 app 层的 Meta 表。
     * 传入越界值时整组参数归零（等价于"未装备"），绝不带病运行。
     *
     * @param card  牌号 0..{@link DoctrineHooks#COUNT}；-1 = 卸下
     * @param hook  主钩子 id（{@link DoctrineHooks}）
     * @param side  副钩子 id（代价部分）
     * @param p0    主钩子参数 1
     * @param p1    主钩子参数 2（只有 H_KILL_HEAL 用到）
     * @param p2    副钩子参数
     * @return 是否成功装备（牌号/钩子 id/参数任一非法 → false，且不写入任何字段）
     */
    public boolean applyDoctrine(int card, int hook, int side, float p0, float p1, float p2) {
        if (card < -1 || card >= DoctrineHooks.COUNT) return false;
        if (!DoctrineHooks.valid(hook) || !DoctrineHooks.valid(side)) return false;
        if (!Float.isFinite(p0) || !Float.isFinite(p1) || !Float.isFinite(p2)) return false;
        doctrine = card;
        doctrineHook = card < 0 ? DoctrineHooks.H_NONE : hook;
        doctrineSideHook = card < 0 ? DoctrineHooks.H_NONE : side;
        doctrineParams[0] = card < 0 ? 0f : p0;
        doctrineParams[1] = card < 0 ? 0f : p1;
        doctrineParams[2] = card < 0 ? 0f : p2;
        doctrineParams[3] = 0f;
        doctrineBonusChoices = 0;
        return true;
    }

    /** 是否装备了某张天赋牌。 */
    public boolean hasDoctrine(int card) {
        return doctrine >= 0 && doctrine == card;
    }

    /**
     * 面板是否已经乘过天赋牌乘区。
     *
     * <p>{@link #beginRun} 里第一次 {@code captureGrowth} 时，玩家面板还是 Session 直接写的
     * 裸值（meta 加成），<b>尚未</b>乘过 #4/#11 的最大生命乘区。#4 献祭契约把 -25% 反解成
     * +33% 的"成长"，下一轮 recompute 再乘回去，代价就被自己抵消——这是必须区分这两种时机的
     * 唯一原因。它不参与序列化：读档后第一次 recompute 会重新置位。
     */
    private boolean doctrinePanelApplied;

    /** 牌名用的流派下标（#0/#3/#9/#12 的开局遗物按流派而不是按牌号）。 */
    private boolean mainHook(int hook) {
        return doctrine >= 0 && doctrineHook == hook;
    }

    private boolean sideHook(int hook) {
        return doctrine >= 0 && doctrineSideHook == hook;
    }

    /** 主钩子参数的容错读取（越界返回 0）。 */
    private float dParam(int i) {
        return i >= 0 && i < doctrineParams.length ? doctrineParams[i] : 0f;
    }

    private void doctrineResetPerPlayer() {
        for (Player p : players) {
            p.overheatClock = 0f;
            p.overheatStun = 0f;
            p.dashHaste = 0f;
            p.dashHasteMul = 0f;
        }
    }

    /**
     * 开局一次性钩子。
     *
     * <p>只在 {@link #beginRun(int, int, boolean)} 里调用一次，位于 {@code generateRoute()} 之前：
     * 路线生成会消费 {@link #random}，因此"开局重掷归零 / 开局遗物"必须先落定，
     * 否则同 seed + 同牌会得到不同结果。
     */
    private void applyDoctrineStart() {
        doctrineBonusChoices = 0;
        doctrineResetPerPlayer();
        roomTimeLimit = 0f;
        if (doctrine < 0) return;

        // ── 主钩子：开局类 ────────────────────────────────────────
        if (mainHook(DoctrineHooks.H_START_RELIC)) {
            int school = Math.round(dParam(0));
            grantStartingRelic(school);
        }
        if (mainHook(DoctrineHooks.H_START_COINS)) coins += Math.round(dParam(0));
        if (mainHook(DoctrineHooks.H_MAX_EVOLUTIONS)) { /* 状态钩子：EvolutionAvailable 每次读 */ }

        // ── 副钩子：开局类 ────────────────────────────────────────
        if (sideHook(DoctrineHooks.H_COINS_ZERO)) coins = 0;
        if (sideHook(DoctrineHooks.H_REROLL_ZERO)) rerolls = 0;
        if (sideHook(DoctrineHooks.H_START_COINS)) coins += Math.round(dParam(2));

        coins = Math.max(0, coins);
    }

    /**
     * #0/#3/#9/#12 的开局遗物：固定给该流派的一件**稀有**（RARITY == 1）遗物。
     *
     * <p>走 {@link #random} 选具体是哪一件，保证确定性；若该流派稀有遗物已满层，
     * 依次退化到同流派任意未满层遗物，再退化到任意未满层遗物，最后给 18 星尘兜底。
     */
    private void grantStartingRelic(int school) {
        if (players.isEmpty()) return;
        if (school < 0 || school >= Content.SCHOOL_COUNT) return;
        // 候选 1：本流派稀有
        int pick = -1;
        int seen = 0;
        for (int i = 0; i < Relics.COUNT; i++) {
            if (Relics.SCHOOL[i] != school || Relics.RARITY[i] != 1) continue;
            seen++;
            if (random.nextInt(seen) == 0) pick = i;     // 蓄水池抽样：O(n)、确定性
        }
        if (pick < 0 || players.get(0).relics[pick] >= Relics.CAP[pick]) {
            // 候选 2：本流派任意
            pick = -1;
            seen = 0;
            for (int i = 0; i < Relics.COUNT; i++) {
                if (Relics.SCHOOL[i] != school) continue;
                if (players.get(0).relics[i] >= Relics.CAP[i]) continue;
                seen++;
                if (random.nextInt(seen) == 0) pick = i;
            }
        }
        if (pick < 0) {
            // 候选 3：任意未满层
            seen = 0;
            for (int i = 0; i < Relics.COUNT; i++) {
                if (players.get(0).relics[i] >= Relics.CAP[i]) continue;
                seen++;
                if (random.nextInt(seen) == 0) pick = i;
            }
        }
        if (pick < 0) {
            coins += 18;
            return;
        }
        for (Player p : players) grantRelic(p, pick);
        notice = "天赋牌 · 开局遗物 " + Relics.NAMES[pick];
    }

    /** 本局被 #3 余烬开局 锁定的流派；-1 = 不锁。 */
    private int lockedSchool() {
        if (!sideHook(DoctrineHooks.H_SCHOOL_LOCK)) return -1;
        int school = Math.round(dParam(2));
        return school >= 0 && school < Content.SCHOOL_COUNT ? school : -1;
    }

    // ── 乘区类：被 recompute / hurt / fire / updateEnemies 现场读取 ──

    /** #14 最大生命乘区（主 + 副独立检查；#4 献祭 0.75 / #11 血脉 0.85）。 */
    private float doctrineMaxHpMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_MAXHP_MUL)) mul *= safeMul(dParam(0));
        if (sideHook(DoctrineHooks.H_MAXHP_MUL)) mul *= safeMul(dParam(2));
        return mul;
    }

    /** #17 承受伤害乘区（#5 焚身 1.2 / #16 玻璃大炮 1.5）。 */
    private float doctrineTakenDamageMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_TAKEN_DAMAGE_MUL)) mul *= safeMul(dParam(0));
        if (sideHook(DoctrineHooks.H_TAKEN_DAMAGE_MUL)) mul *= safeMul(dParam(2));
        return mul;
    }

    /** #25 伤害乘区（#16 玻璃大炮 1.6，副钩子位）。 */
    private float doctrineDamageMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_DAMAGE_MUL)) mul *= safeMul(dParam(0));
        if (sideHook(DoctrineHooks.H_DAMAGE_MUL)) mul *= safeMul(dParam(2));
        return mul;
    }

    /** #7 弹丸伤害乘区增量（#6 霜封 -20%，副钩子位）。 */
    private float doctrineShotDamageMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_SHOT_DAMAGE_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_SHOT_DAMAGE_MUL)) mul *= 1f + dParam(2);
        return safeMul(mul);
    }

    /** #27 攻速乘区增量（#13 过载运转 +45%，副钩子位）。 */
    private float doctrineAtkSpeedMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_ATKSPEED_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_ATKSPEED_MUL)) mul *= 1f + dParam(2);
        return safeMul(mul);
    }

    /** #23 回复乘区增量（#10 荆棘誓约 -60%）。 */
    private float doctrineRegenMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_REGEN_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_REGEN_MUL)) mul *= 1f + dParam(2);
        return Math.max(0f, safeMul(mul));
    }

    /** #20 敌人移速乘区增量（#8 极寒迟滞 -25%）。 */
    private float doctrineEnemySpeedMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_ENEMY_SPEED_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_ENEMY_SPEED_MUL)) mul *= 1f + dParam(2);
        return Math.max(.25f, safeMul(mul));
    }

    /** #21 房间限时乘区增量（#8 极寒迟滞 -20%，副钩子位；只在挑战房生效）。 */
    private float doctrineRoomTimeMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_ROOM_TIME_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_ROOM_TIME_MUL)) mul *= 1f + dParam(2);
        return Math.max(.2f, safeMul(mul));
    }

    /** #9 商店价格乘区增量（#15 星尘债务 -25%，副钩子位）。 */
    private float doctrineShopPriceMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_SHOP_PRICE_MUL)) mul *= 1f + dParam(0);
        if (sideHook(DoctrineHooks.H_SHOP_PRICE_MUL)) mul *= 1f + dParam(2);
        return Math.max(.2f, safeMul(mul));
    }

    /** #31 遗物效果乘区（#17 孤注一掷 1.6）。 */
    private float doctrineRelicEffectMul() {
        float mul = 1f;
        if (mainHook(DoctrineHooks.H_RELIC_EFFECT_MUL)) mul *= safeMul(dParam(0));
        if (sideHook(DoctrineHooks.H_RELIC_EFFECT_MUL)) mul *= safeMul(dParam(2));
        return Math.max(.1f, mul);
    }

    /** 乘区容错：非有限值一律视为 1，避免腐坏存档把面板打成 NaN。 */
    private static float safeMul(float mul) {
        return Float.isFinite(mul) && mul > 0f ? mul : 1f;
    }

    /** #30 遗物携带上限（#17 孤注一掷 2 件；默认 = 全 60 种）。 */
    /**
     * 遗物栏上限（按**不同种类**计）。
     *
     * <p>v2.2：元进度节点 20「遗物携带上限 +1」此前全项目零消费。
     * 现在叠加在基础值上；天赋牌 #17 会把上限压到 2（孤注一掷的代价），
     * 因此元进度只能**加**不能突破牌面的硬上限——先取牌面值，再加节点。
     */
    private int doctrineRelicSlots() {
        int slots = Relics.COUNT;
        if (mainHook(DoctrineHooks.H_RELIC_SLOTS)) slots = Math.max(0, Math.round(dParam(0)));
        if (sideHook(DoctrineHooks.H_RELIC_SLOTS)) slots = Math.max(0, Math.round(dParam(2)));
        boolean capped = mainHook(DoctrineHooks.H_RELIC_SLOTS) || sideHook(DoctrineHooks.H_RELIC_SLOTS);
        if (capped) return slots;                       // 天赋牌的硬上限不可被元进度突破
        int bonus = 0;
        for (Player p : players) bonus = Math.max(bonus, Math.max(0, p.metaRelicSlots));
        return slots + bonus;
    }

    /** #10 可进化线数（#1 双重进化 2 条；默认 3 条 = 本职业全部线）。 */
    private int doctrineMaxEvolutions() {
        if (mainHook(DoctrineHooks.H_MAX_EVOLUTIONS)) return Math.max(1, Math.round(dParam(0)));
        return 3;
    }

    /** #11 进化等级门槛增量（#1 双重进化 +3）。 */
    private int doctrineEvolveLevelPen() {
        if (sideHook(DoctrineHooks.H_EVOLVE_LEVEL_PEN)) return Math.max(0, Math.round(dParam(2)));
        return 0;
    }

    private boolean doctrineCritZero() {
        return mainHook(DoctrineHooks.H_CRIT_ZERO) || sideHook(DoctrineHooks.H_CRIT_ZERO);
    }

    /** 不同遗物种类数（#17 的"2 件"按种类算，不是层数总和）。 */
    private int distinctRelics(Player p) {
        int n = 0;
        for (int i = 0; i < Relics.COUNT; i++) if (p.relics[i] > 0) n++;
        return n;
    }

    /**
     * 统一回复入口（薄封装，保留调用点语义）。
     * <p>#23 回复乘区已经乘在 {@link #heal(Player, float)} 里了 —— 那是唯一真入口，
     * 在这里再乘一次会变成 -60% × -60% = -84%，代价被放大成两倍多。
     */
    private float doctrineHeal(Player p, float amount) {
        return heal(p, amount);
    }

    /** #10 荆棘誓约的反弹比例（主钩子读 [0]，副钩子读 [2]）。 */
    private float doctrineReflectRatio() {
        if (mainHook(DoctrineHooks.H_REFLECT)) return Math.max(0f, dParam(0));
        if (sideHook(DoctrineHooks.H_REFLECT)) return Math.max(0f, dParam(2));
        return 0f;
    }

    /**
     * 反弹伤害：给玩家周围 90 范围内的敌人。
     * <p>用 {@link #damage} 走正常结算（护甲/护盾/吸血/击杀都照常），
     * 但百分比是"每帧最多一次"——{@code hurt} 本身有 0.5s 无敌帧，天然限频。
     */
    private void reflectDamage(Player p, float amount) {
        if (!(amount > 0f)) return;
        for (Enemy e : enemies)
            if (e.hp > 0 && distance(p.x, p.y, e.x, e.y) < 90 + e.r)
                damage(e, amount, p.id);
        effect(p.x, p.y, 90, 5, 0xffc8f0b8, .28f);
    }

    private boolean beginRun(int id, int mode, boolean single) {
        if (id != captain || phase != LOBBY || players.isEmpty() || mode < 0 || mode > 3) return false;
        soloRun = single;
        this.mode = mode;
        clearPendingPenalties();
        // ── v2.0 支柱 2：天赋牌的开局类钩子必须先于 generateRoute() ─────
        // generateRoute 会消费 random；开局遗物/星尘/重掷若在其后应用，
        // 同 seed + 同牌会得到不同的路线（确定性测试会挂）。
        applyDoctrineStart();
        // ── v2.0 P0-1：开局前先捕获元进度注入的裸面板加成 ─────────────
        // 旧代码在这里把 bonus* 全部清零，等于把 32 节点元进度树一起清零。
        // 现在改成"先反解进成长字段"，再走正常 recompute，加成得以保留。
        for (Player p : players) {
            // 开局时遗物里可能已经有牌（Meta 的「开局携带 1 件随机遗物」、
            // 天赋牌 H_START_RELIC），所以这里同样要剔除遗物贡献再反解。
            float[] relic = relicNumericContribution(p);
            captureGrowth(p, roleBaseDamage(p.role), roleBaseHp(p.role), roleBaseSpeed(p.role),
                    relic[0], relic[1], relic[2]);
            recompute(p);
        }
        roomsTotal = mode == MODE_ENDLESS ? 999 : mode == MODE_BOSS_RUSH ? 6 : mode == MODE_DAILY ? 9
                : 15;
        // 远征 15 站 = 5 章 × 3 站，每章结尾首领
        contract = mode == MODE_DAILY ? 1 : Math.max(0, Math.min(Balance.CONTRACTS.length - 1, contract));
        roomsVisited = room = 0;
        roomsTotal = Math.max(1, roomsTotal);
        level = 1;
        xpGoal = 55;
        generateRoute();
        phase = MAP;
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  输入
    // ══════════════════════════════════════════════════════════════

    public void input(int id, float dx, float dy, float ax, float ay, boolean manual,
                      int dash, int skill) {
        Player p = player(id);
        if (p == null || !Float.isFinite(dx) || !Float.isFinite(dy)
                || !Float.isFinite(ax) || !Float.isFinite(ay)) return;
        float n = (float) Math.hypot(dx, dy);
        if (n > 1) { dx /= n; dy /= n; }
        p.dx = dx; p.dy = dy;
        p.manual = manual ? 1 : 0;
        n = (float) Math.hypot(ax, ay);
        if (manual && n > .1f) { p.ax = ax / n; p.ay = ay / n; }
        if (phase == FIGHT && !p.down && p.connected) {
            if (dash != p.dashSerial) {
                p.dashSerial = dash;
                if (p.dashCd <= 0) doDash(p);
            }
            if (skill != p.skillSerial) {
                p.skillSerial = skill;
                if (p.skillCd <= 0) { doSkill(p); flushDeaths(); }
            }
        } else {
            p.dashSerial = dash;
            p.skillSerial = skill;
        }
    }

    /** 触发终极技能（能量满时由 UI 调用）。返回是否成功。 */
    public boolean ultimate(int id) {
        Player p = player(id);
        if (p == null || phase != FIGHT || p.down || !p.connected) return false;
        if (p.ultReady < 1) return false;
        p.ultReady = 0;
        p.ultCd = 0;
        doUltimate(p);
        flushDeaths();
        return true;
    }

    private void doDash(Player p) {
        // ── v2.0 支柱 2 #2 疾影步：闪避改为消耗星尘、无冷却 ───────────
        // 扣费必须在位移**之前**：先付钱再移动。放在位移之后的话，
        // 玩家即使星尘不够也已经飞出去了，"消耗星尘"就成了纯提示文本。
        boolean coinDash = mainHook(DoctrineHooks.H_DASH_COSTS_COIN);
        if (coinDash) {
            int cost = Math.max(0, Math.round(dParam(0)));
            if (coins < cost) {
                p.dashCd = .25f;                     // 短冷却防止连点刷屏
                return;
            }
            coins -= cost;
        }
        float vx = p.dx, vy = p.dy;
        if (Math.hypot(vx, vy) < .1) { vx = p.ax; vy = p.ay; }
        float n = Math.max(.01f, (float) Math.hypot(vx, vy));
        float ox = p.x, oy = p.y;
        for (int i = 0; i < 16; i++) move(p, vx / n * 10, vy / n * 10);
        // v2.1 跨流派组合 4「虚步」（虚空 + 疾风）：闪避冷却 -25%，闪避后 1.6 秒伤害 +20%。
        // 派生量 crossRush 只在这里置位、在 power() 里按冷却递减读取；失效时由 computeCross 清零。
        boolean voidStep = p.cross(4);
        // 无冷却 ≠ 每帧可闪：0.22s 的节流让"按住闪避"不至于是无敌连打，
        // 同时远短于默认 3.2s，玩家仍能明显感到"星尘够就能连着闪"。
        p.dashCd = coinDash ? DASH_COIN_THROTTLE : 3.2f * p.dashFactor * (voidStep ? .75f : 1f);
        p.invuln = Math.max(p.invuln, .55f);
        p.nextCrit |= p.has(Content.F_CRIT) && p.relics[Relics.AFTERIMAGE] > 0;
        for (int i = 0; i < 6; i++)
            effect(ox + (p.x - ox) * i / 5f, oy + (p.y - oy) * i / 5f, 22, 1,
                    Content.COLORS[p.role], .3f);
        p.ultCharge = Math.min(100, p.ultCharge + gainUlt(p, 3));
        if (voidStep) p.crossRush = 1.6f;
        // ── v2.0 支柱 2 #2 副钩子：闪避后 1.5s 攻速 +60% ─────────────
        if (sideHook(DoctrineHooks.H_DASH_ATKSPEED) || mainHook(DoctrineHooks.H_DASH_ATKSPEED)) {
            p.dashHaste = DASH_HASTE_SECONDS;
            p.dashHasteMul = mainHook(DoctrineHooks.H_DASH_ATKSPEED) ? dParam(0) : dParam(2);
            p.dashHasteMul = Math.max(0f, p.dashHasteMul);
        }
        applyMechanic(p, H_DASH, null, null, 0f, 0f);   // M11 位移联动
    }

    /** #2 疾影步：闪避后攻速增益的持续时间（秒）。 */
    static final float DASH_HASTE_SECONDS = 1.5f;
    /**
     * #2 疾影步：付费闪避的最小间隔（秒）。
     * <p>牌面写"无冷却"是指不再受 3.2s 冷却限制；这里保留一个极短的节流，
     * 避免玩家按住闪避键时每帧位移 160px + 每帧刷新 0.55s 无敌，变成无敌连打。
     */
    static final float DASH_COIN_THROTTLE = .22f;

    private void doSkill(Player p) {
        p.skillCd = (p.role == 2 ? 15 : p.role == 6 ? 13 : p.role == 7 ? 11 : 9.5f)
                * (float) Math.pow(.86, p.relics[Relics.CDR] * .5);
        effect(p.x, p.y, p.role == 4 ? 230 : 168, 2, Content.COLORS[p.role], .6f);
        p.ultCharge = Math.min(100, p.ultCharge + gainUlt(p, 8));
        switch (p.role) {
            case 0: for (int i = -3; i <= 3; i++) fire(p, 3, i * .24f); break;
            case 1:
                p.invuln = 1.6f; p.shield = p.shieldMax = Math.max(p.shieldMax, p.maxHp * .35f);
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 165) { damage(e, power(p) * 3f, p.id); e.slow = 2.5f; e.stun = Math.max(e.stun, .4f); }
                break;
            case 2:
                // ── v2.0 P0-6 M4 技能改写（进化 8 群星圣愈）──
                // 改写 = 替换：不再是"小幅治疗"，而是全队回满 + 护盾。
                if (p.evolved[8] && Evolutions.mechanic(8) == 4) {
                    float shieldAdd = p.maxHp * .2f;
                    for (Player q : players) {
                        if (q.down && distance(p.x, p.y, q.x, q.y) < 260) {
                            q.down = false;
                            q.revive = 0;
                            q.invuln = 2;
                        }
                        q.hp = q.maxHp;
                        q.shield = Math.max(q.shield, shieldAdd);
                        q.shieldMax = Math.max(q.shieldMax, shieldAdd);
                        effect(q.x, q.y, 90, 6, 0xffefc8ff, .8f);
                    }
                    notice = "群星圣愈 · 全队回满并附加护盾";
                } else {
                    for (Player q : players) {
                        if (q.down && distance(p.x, p.y, q.x, q.y) < 190) { q.down = false; q.hp = q.maxHp * .45f; q.invuln = 2; }
                        else if (!q.down) { q.hp = Math.min(q.maxHp, q.hp + 30); q.invuln = Math.max(q.invuln, .4f); }
                    }
                }
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 150) damage(e, power(p) * 1.2f, p.id);
                break;
            case 3:
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 235) { e.slow = 4.5f; e.stun = Math.max(e.stun, .35f); damage(e, power(p) * 2.7f, p.id); }
                break;
            case 4:
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 250) { e.burn = 5.5f; e.owner = p.id; damage(e, power(p) * 2.9f, p.id); }
                break;
            case 5:
                p.invuln = 1.25f; p.dashCd = 0; p.nextCrit = true;
                for (int i = 0; i < 10; i++) projectile(p, i * (float) Math.PI / 5, power(p) * 2.1f, 6, 3, 660, .7f);
                break;
            case 6:
                // ── v2.0 P0-6 M4 技能改写（进化 20 零式齐射）──
                // 改写 = 替换：炮台数量翻倍（2 座），并延长 50% 持续（等效射程覆盖更久）。
                if (p.evolved[20] && Evolutions.mechanic(20) == 4) {
                    deployTurret(p, p.x - 40, p.y, 19.5f);
                    deployTurret(p, p.x + 40, p.y, 19.5f);
                    notice = "零式齐射 · 双炮台部署";
                } else {
                    deployTurret(p, p.x, p.y, 13);
                }
                p.buff = 6; p.buffKind = 3;
                break;
            case 7:
                for (Player q : players) {
                    if (!q.down) q.hp = Math.min(q.maxHp, q.hp + 22);
                    q.buff = 6; q.buffKind = 2;
                }
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 165) damage(e, power(p) * 2f, p.id);
                break;
            case 8:
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 210) { e.slow = 3.2f; e.stun = Math.max(e.stun, .9f); damage(e, power(p) * 1.9f, p.id); }
                for (int i = 0; i < 8; i++) projectile(p, i * (float) Math.PI / 4, power(p) * 1.5f, 7, 2, 520, 1.1f);
                break;
            case 9:
                p.hp = Math.max(1, p.hp - p.maxHp * .06f);
                p.buff = 5; p.buffKind = 1;
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 200) { damage(e, power(p) * 3.4f, p.id); e.burn = 4; }
                break;
            case 10:
                for (int i = 0; i < 6; i++) {
                    Enemy target = nearestEnemy(p.x, p.y, false);
                    float tx = target != null ? target.x : p.x + p.ax * 200;
                    float ty = target != null ? target.y : p.y + p.ay * 200;
                    warningPool(tx, ty, 46, 0, .35f, p.id);
                }
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 120) damage(e, power(p) * 1.6f, p.id);
                break;
            default: // 11 虚空行者
                p.x = clamp(p.x + p.ax * 160, 40, 1080);
                p.y = clamp(p.y + p.ay * 160, 44, 577);
                p.invuln = 1.5f;
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 175) { damage(e, power(p) * 2.6f, p.id); e.armor = Math.min(.24f, e.armor + .06f); }
                break;
        }
    }

    private void doUltimate(Player p) {
        effect(p.x, p.y, 320, 6, Content.COLORS[p.role], 1.2f);
        p.invuln = 2.2f;
        switch (p.role) {
            case 0:
                for (int ring = 0; ring < 3; ring++)
                    for (int i = 0; i < 18; i++)
                        projectile(p, i * (float) Math.PI * 2 / 18 + ring * .17f, power(p) * 2.4f, 7, 3, 700, 1.1f);
                break;
            case 1:
                p.invuln = 4f;
                for (Enemy e : enemies)
                    if (distance(p.x, p.y, e.x, e.y) < 320) { damage(e, power(p) * 4.5f, p.id); e.stun = 1.6f; }
                for (Player q : players) q.hp = Math.min(q.maxHp, q.hp + q.maxHp * .3f);
                break;
            case 2:
                for (Player q : players) { q.hp = q.maxHp; q.down = false; q.invuln = 3f; }
                break;
            case 3:
                for (Enemy e : enemies) { e.slow = 6; e.stun = 1.2f; damage(e, power(p) * 3.2f, p.id); }
                break;
            case 4:
                for (int i = 0; i < 14; i++) {
                    float a = i * (float) Math.PI * 2 / 14;
                    warningPool(p.x + (float) Math.cos(a) * 150, p.y + (float) Math.sin(a) * 150, 70, 0, .3f + i * .04f, p.id);
                }
                break;
            case 5:
                p.invuln = 3.5f; p.dashCd = 0;
                for (int burst = 0; burst < 3; burst++)
                    for (int i = 0; i < 12; i++)
                        projectile(p, i * (float) Math.PI * 2 / 12 + burst * .26f, power(p) * 2.6f, 6, 3, 780, 1f);
                break;
            case 6:
                for (int i = 0; i < 4; i++) {
                    float a = i * (float) Math.PI / 2;
                    deployTurret(p, p.x + (float) Math.cos(a) * 90, p.y + (float) Math.sin(a) * 90, 14);
                }
                for (Enemy e : enemies) if (distance(p.x, p.y, e.x, e.y) < 260) damage(e, power(p) * 2.2f, p.id);
                break;
            case 7:
                for (Player q : players) { q.hp = q.maxHp; q.buff = 10; q.buffKind = 2; q.invuln = 2.5f; }
                for (Enemy e : enemies) if (distance(p.x, p.y, e.x, e.y) < 340) damage(e, power(p) * 3f, p.id);
                break;
            case 8:
                for (Enemy e : enemies) {
                    e.slow = 8; e.stun = 2.2f;
                    damage(e, power(p) * 4f, p.id);
                }
                break;
            case 9:
                // 血誓终极：以血换爆发。下限保护到 35% 最大生命，避免"放完就死"。
                p.hp = Math.max(p.maxHp * .35f, p.hp * .55f);
                p.buff = 10; p.buffKind = 1; p.invuln = Math.max(p.invuln, .8f);
                for (Enemy e : enemies) if (distance(p.x, p.y, e.x, e.y) < 300) { damage(e, power(p) * 5.5f, p.id); e.burn = 6; }
                break;
            case 10:
                for (int i = 0; i < 24; i++) {
                    float a = i * (float) Math.PI * 2 / 24;
                    float d = 90 + (i % 4) * 70;
                    warningPool(p.x + (float) Math.cos(a) * d, p.y + (float) Math.sin(a) * d, 54, 0, .25f + (i % 5) * .07f, p.id);
                }
                break;
            default:
                // ── v2.0 P0-6 M4 技能改写（进化 35 深渊回响）──
                // 改写 = 替换：虚空侵蚀更狠（护甲 +0.15 → +0.22，上限 .4 → .55）；
                // "终极冷却减半"落在充能速度翻倍上（见 gainUlt / ultChargeMul）。
                boolean abyss = p.evolved[35] && Evolutions.mechanic(35) == 4;
                float armorCap = abyss ? .55f : .4f;
                float armorAdd = abyss ? .22f : .15f;
                for (Enemy e : enemies) if (distance(p.x, p.y, e.x, e.y) < 400) {
                    damage(e, power(p) * 3.6f, p.id);
                    e.armor = Math.min(armorCap, e.armor + armorAdd);
                }
                p.invuln = 3f;
                break;
        }
        for (Enemy e : enemies) if (e.hp <= 0) e.flash = 0;
        flushDeaths();
    }

    // ══════════════════════════════════════════════════════════════
    //  路线与房间
    // ══════════════════════════════════════════════════════════════

    public boolean selectRoute(int option) {
        if (phase != MAP || option < 0 || option >= routeCount) return false;
        room = roomsVisited;
        routeIndex = option;
        roomType = routeOptions[option];
        history[room % history.length] = roomType;
        layout = random.nextInt(Rooms.LAYOUTS.length);
        mirror = random.nextBoolean();
        hazard = biome;
        eventChoice = -1;
        // ── v2.0 支柱 2 #12 雷光开局：开局重掷次数归零 ──────────────────
        // 每进一层都会重置重掷次数；如果这里不认钩子，beginRun 里设的 0
        // 会在玩家点第一层时被悄悄改回 1，牌面承诺当场失效。
        rerolls = (mainHook(DoctrineHooks.H_REROLL_ZERO) || sideHook(DoctrineHooks.H_REROLL_ZERO)) ? 0 : 1;
        shrineUsed = false;
        mutator = modifierAt(room, option);
        corruption = Balance.corruptionFor(room);
        mutator2 = secondModifier(room, option);
        sceneTitle = Content.BIOME_NAMES[biome] + " · " + Content.ROOM_TYPES[roomType];
        sceneHint = objectiveHint();
        roomTime = 0;
        clearEntities();
        resetPositions();
        // ── v2.0 支柱 2 #7 寒冰壁垒：每层开始给护盾 ────────────────────
        // 放在 clearEntities/resetPositions 之后：那里会把玩家放回起点，
        // 但不会碰 shield，因此这条只增不减。
        floorShield();
        // #8 极寒迟滞的限时倍率按房间重置；非战斗房保持 1（无限制目标）。
        roomTimeLimit = 1f;
        switch (roomType) {
            case ROOM_EVENT: {
                eventType = pickEvent();
                eventClock = 0;
                phase = EVENT;
                notice = Events.TEXT[eventType];
                break;
            }
            case ROOM_SHOP:
                shopRerolls = 0;
                shopFreeRerolls = 0;                 // v2.3 #3：免费重摆余额只在本店内有效
                shopBuyoutRewardUsed = false;        // 每次进店最多领一次"买空赠礼"
                fillShop();
                phase = SHOP;
                notice = "可自由挑选；交易后手动离开";
                break;
            case ROOM_CAMP: phase = CAMP; campAfterBoss = false; notice = "选择一次休整收益"; break;
            case ROOM_ARMORY: phase = ARMORY; notice = "选择一项武器进化"; break;
            case ROOM_SHRINE: phase = SHRINE; notice = "神龛只会回应一次"; break;
            case ROOM_TREASURE: beginCombat(); notice = "封印宝库 · 击败守卫后获取奖励"; break;
            default: beginCombat(); break;
        }
        return true;
    }

    private int pickEvent() {
        int count = Events.COUNT;
        int best = 0;
        for (int tries = 0; tries < 24; tries++) {
            int id = random.nextInt(count);
            if (Events.MIN_FLOOR[id] <= floor) return id;
            best = id;
        }
        return best;
    }

    /** 预览某深度的路线选项（用于星图长期规划）。 */
    public int[] preview(int depth) {
        if (mode == MODE_BOSS_RUSH) return new int[]{ROOM_BOSS};
        if (mode != MODE_ENDLESS && depth >= roomsTotal) return new int[]{ROOM_BOSS};
        // 每 3 站一章，章末必是首领
        if (depth % 3 == 2) return new int[]{ROOM_BOSS};
        RunRandom r = new RunRandom(seed ^ ((depth + 1) * 0x9E3779B97F4A7C15L));
        int cycle = depth % 3;
        if (cycle == 0)
            return shuffle(r, ROOM_COMBAT, ROOM_ELITE, ROOM_CHALLENGE, 0);
        if (cycle == 1) {
            // 章中站：营地与神龛必须真正出现在路线里，否则 camp()/shrine() 是死内容。
            // 第 2 章起每章轮换：一章给营地，下一章给神龛，保证一局各至少一次，
            // 同时始终保留商店（经济主干）与一个战斗/事件位。
            int chapter = depth / 3;                 // 0,1,2,...（0 为第一章）
            int rest = r.nextBoolean() ? ROOM_EVENT : ROOM_COMBAT;
            if (chapter == 0) {
                // 第一章就引入，让玩家早期学会这两个房间
                return shuffle(r, ROOM_SHOP, rest, ROOM_CAMP, 1);
            }
            int utility = (chapter % 2 == 1) ? ROOM_SHRINE : ROOM_CAMP;
            int third = r.nextBoolean() ? ROOM_ARMORY : ROOM_TREASURE;
            // 三选一：商店固定占位，另外两个位在这三者之间随机，
            // 保证每章都有商店，且营地/神龛至少有 1/2 概率出现（跨章必然覆盖）。
            int[] pool = {rest, third, utility};
            int j = r.nextInt(3);
            int t = pool[0]; pool[0] = pool[j]; pool[j] = t;
            return new int[]{ROOM_SHOP, pool[0], pool[1]};
        }
        return new int[]{ROOM_BOSS};
    }

    private int[] shuffle(RunRandom r, int a, int b, int c, int unused) {
        int[] arr = {a, b, c};
        for (int i = arr.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
        return arr;
    }

    public int modifierAt(int depth, int option) {
        if (mode == MODE_BOSS_RUSH) return 0;
        int[] opts = preview(depth);
        if (option < 0 || option >= opts.length) return 0;
        int type = opts[option];
        if (type != ROOM_COMBAT && type != ROOM_ELITE && type != ROOM_TREASURE
                && type != ROOM_CHALLENGE) return 0;
        RunRandom r = new RunRandom(seed ^ ((depth + 5) * 17873587L) ^ option * 784117L);
        if (depth == 0 && type == ROOM_COMBAT) return 0;
        return 1 + r.nextInt(Balance.MODIFIERS.length - 1);
    }

    /**
     * 腐化叠加出的第二条异变。完全由 (seed, depth, option, 已选房间类型) 决定，
     * 不消耗 {@link #random}，因此不影响既有随机序列与存档兼容性。
     *
     * <p>规则：腐化 ≥2 且该房间本来就有异变（不是 combat 首站/非战斗房）时，
     * 再抽一条与之不同的异变。等级越高，叠加概率越高——腐化 4 时几乎必叠。
     */
    private int secondModifier(int depth, int option) {
        if (corruption < 2) return 0;
        if (mode == MODE_BOSS_RUSH) return 0;
        int first = modifierAt(depth, option);
        if (first == 0) return 0;
        RunRandom r = new RunRandom(seed ^ ((depth + 11) * 0x9E3779B97F4A7C15L) ^ (option * 2654435761L));
        // 腐化 2/3/4 → 叠加概率 45% / 75% / 100%
        float chance = corruption >= 4 ? 1f : corruption == 3 ? .75f : .45f;
        if (r.nextFloat() >= chance) return 0;
        int span = Balance.MODIFIERS.length - 1;   // 1..7
        int second = 1 + r.nextInt(span);
        if (second == first) second = 1 + (second % span);   // 保证不同
        return second;
    }

    private void generateRoute() {
        room = roomsVisited;
        floor = 1 + roomsVisited / 3;
        biome = (mode == MODE_BOSS_RUSH ? roomsVisited : roomsVisited / 3) % Content.BIOME_COUNT;
        int[] options = preview(roomsVisited);
        routeCount = options.length;
        for (int i = 0; i < 3; i++) routeOptions[i] = options[Math.min(i, options.length - 1)];
        sceneTitle = Content.BIOME_NAMES[biome];
        notice = Content.BIOME_HINT[biome];
        corruption = Balance.corruptionFor(roomsVisited);
        mutator2 = 0;
        if (corruption > corruptionAnnounced) {
            corruptionAnnounced = corruption;
            if (corruption > 0)
                notice = Content.CORRUPTION_NAMES[corruption] + " · "
                        + Content.CORRUPTION_HINTS[corruption];
        }
    }

    private void beginCombat() {
        phase = FIGHT;
        roomTime = 0;
        roomStartKills = kills;
        spawned = 0;
        charge = 0;
        stallClock = 0;
        lastStallKills = 0;
        enraged = false;
        spawnClock = 1.1f;
        wave = 0;
        waves = roomType == ROOM_TREASURE ? 2 : 3;
        nextWaveAt = 0;
        waveWait = 0;
        stallClock = 0;
        objective = roomType == ROOM_CHALLENGE ? 1 + Math.floorMod(room + biome, 3) : 0;
        // ── v2.0 支柱 2 #8 极寒迟滞：房间限时 -20%（副钩子位）────────
        // 只影响挑战房的限时目标（守环 / 生存 / 狩猎），普通清怪房没有限时。
        roomTimeLimit = roomType == ROOM_CHALLENGE ? doctrineRoomTimeMul() : 1f;
        target = baseTarget();
        if (roomType != ROOM_BOSS) target += (players.size() - 1) * 5;
        Rooms.build(this);
        // ── v2.0 词条「熔岩地火」：每个战斗房额外布置熔岩口 ─────────────
        // Rooms.build 已经把该生态的常规危险区放好；这里追加指定条数，
        // 并且全部 kind=3（熔岩）、clock 按 PACT_INFERNO_CLOCK 缩短。
        // 追加发生在 build 之后，因此随机序列与不开词条时完全相同
        // ——只有真正开了词条才会消耗 random，不会改变既有地图布局。
        if (hasPact(Balance.PACT_INFERNO) && roomType != ROOM_BOSS) addInfernoVents();
        // ── v2.0 词条「时限压榨」：重置追兵节拍 ───────────────────────
        pactReinforceClock = Balance.PACT_TIME_REINFORCE;
        pactBurnClock = 1f;
        pactLimitAnnounced = false;
        sceneHint = objectiveHint();
        notice = sceneHint;
    }

    /**
     * 「熔岩地火」：追加 {@link Balance#PACT_INFERNO_VENTS} 个熔岩口。
     * <p>位置从固定的候选点里随机挑——不用纯随机坐标，是为了让熔岩口
     * 落在"值得绕路"的位置而不是贴墙死角，也不会与出生点重叠。
     */
    private void addInfernoVents() {
        // 候选点与 Rooms.hazardSpot 同源，但避开出生点与中央通道。
        float[][] pool = {
                {340, 300}, {780, 320}, {560, 462}, {560, 148},
                {212, 430}, {906, 190}, {420, 452}, {716, 176}
        };
        for (int j = 0; j < Balance.PACT_INFERNO_VENTS; j++) {
            int pick = random.nextInt(pool.length);
            float x = clamp(pool[pick][0] + (random.nextFloat() - .5f) * 80f, 90f, 1030f);
            float y = clamp(pool[pick][1] + (random.nextFloat() - .5f) * 60f, 80f, 540f);
            if (distance(x, y, 560f, 470f) < 150f) y = clamp(y - 170f, 80f, 540f);
            Hazard h = new Hazard();
            h.id = allocateId();
            h.x = x;
            h.y = y;
            h.r = biome == 4 ? 70f : 62f;      // 熔火生态的熔岩口更大
            h.kind = 3;                         // 熔岩
            h.clock = 1.2f + j * Balance.PACT_INFERNO_CLOCK;
            h.active = -1;
            hazards.add(h);
        }
    }

    /** 挑战房的限时（秒），已应用 #8 的房间限时乘区。 */
    private float challengeTime(float base) {
        float mul = roomTimeLimit > 0f ? roomTimeLimit : 1f;
        return Math.max(5f, base * mul);
    }

    private int baseTarget() {
        switch (roomType) {
            case ROOM_BOSS: return 1;
            case ROOM_ELITE: return 9 + floor * 2;
            case ROOM_CHALLENGE: return 70 + floor * 8;
            case ROOM_TREASURE: return 12 + floor * 3;
            default: return 13 + floor * 3;
        }
    }

    public String objectiveHint() {
        if (roomType == ROOM_BOSS) {
            int idx = bossIndexForRoom();
            return Bosses.DESC[idx];
        }
        if (roomType == ROOM_CHALLENGE) {
            if (objective == 1) return "守住星环：站在圈内累积 26 秒，敌人进圈会阻止充能";
            if (objective == 2) return "生存：坚持 40 秒，后半段更强的追击者会出现";
            return "灵魂狩猎：45 秒内击败 18 个守卫";
        }
        if (roomType == ROOM_TREASURE) return "封印宝库：击败守卫才能拿到里面的东西";
        if (roomType == ROOM_ELITE) return "精英房：先处理远程与治疗单位，别被包夹";
        return "分批围攻 · 优先打断远程，不能只等敌人排队靠近";
    }

    /** 当前房间对应的首领下标。 */
    public int bossIndexForRoom() {
        if (mode == MODE_BOSS_RUSH) return Math.floorMod(roomsVisited, Bosses.COUNT);
        if (roomsVisited >= roomsTotal - 1) return Bosses.COUNT - 1;
        return Math.min(Bosses.COUNT - 2, biome);
    }

    // ══════════════════════════════════════════════════════════════
    //  事件
    // ══════════════════════════════════════════════════════════════

    public boolean canEvent(int choice) {
        if (phase != EVENT || eventChoice >= 0 || choice < 0 || choice > 1) return false;
        if (players.isEmpty()) return false;
        int type = Events.TYPE[eventType];
        int cost = choice == 0 ? Events.COST_A[eventType] : Events.COST_B[eventType];
        if (coins < cost) return false;
        // 不能让血债已满的玩家继续无限借星尘，也不能挤爆有限账本。
        if (choice == 0 && type == 8 && eventDebt >= 9) return false;
        if (choice == 0 && delayedCharges.size() >= MAX_DELAYED_CHARGES) {
            if (type == 4 || type == 9) return false;
            if (type == 8 && !hasDebtCharge()) return false;
        }
        if (choice == 0) {
            switch (type) {
                // 献血 / 祭坛 / 反向代价 / 诅咒：A 选项都会直接扣当前生命，
                // 残血时必须拒绝，否则会出现"选完直接倒下"的不可读结果。
                case 0: case 3: case 12: case 7:
                    for (Player p : players) if (p.hp <= p.maxHp * .32f) return false;
                    break;
                default: break;
            }
        }
        return true;
    }

    /**
     * 结算一次事件选择。
     *
     * <p><b>结构 vs 参数</b>：{@link Events#TYPE} 决定走哪条代码路径（13 种选择结构），
     * {@link Events#PARAM_A} / {@code B} / {@code C} 决定这一条路径上的量级。
     * 每个 case 顶部注释写明它读取哪些参数——同 TYPE 的两个事件因此会产生不同结果。
     *
     * <p><b>代价的真实性</b>：生命代价一律按<b>当前生命</b>或<b>最大生命</b>现扣，
     * 延迟代价写进 {@link #delayedKind} 由 {@link #advance()} 在 3 站后兑现。
     * 全部不经过 {@code bonus*} 成长字段（那些字段会被 {@code recompute} 的捕获逻辑衰减，
     * 用它记代价会导致"加一次遗物就把代价还回来"）。
     */
    public boolean resolveEvent(int choice) {
        if (!canEvent(choice)) return false;
        eventChoice = choice;
        int type = Events.TYPE[eventType];
        int cost = choice == 0 ? Events.COST_A[eventType] : Events.COST_B[eventType];
        coins -= cost;
        boolean a = choice == 0;
        float pa = Events.PARAM_A[eventType];
        float pb = Events.PARAM_B[eventType];
        float pc = Events.PARAM_C[eventType];
        Player lead = players.get(0);
        // v2.2：元进度「事件收益 +6%/级」（节点 22）此前全项目零消费。
        // 只放大**正向**收益（星尘/治疗量），不放大代价——否则点了节点反而更亏。
        float metaGain = 1f + Math.max(0f, lead.metaEvent);
        // 延迟代价进入 v11 多笔账本：医疗/赊账互不覆盖；
        // 血债只更新自身条目，结清时也不取消其他事件的欠款。
        switch (type) {
            // ── 0 献血：用当前生命换永久强度 ────────────────────────
            // PARAM_A = 献出的当前生命比例；PARAM_B = 获得的「血脉契约」层数；
            // PARAM_C = B 的星尘收益。
            // 代价落在当前生命（不会随 recompute 复原），收益是遗物层数（本身就被
            // recompute 计入面板），因此两边都是真的。
            case 0: {
                if (a) {
                    int n = Math.max(1, (int) pb);
                    for (Player p : players) {
                        p.hp = Math.max(1f, p.hp - p.hp * pa);
                        for (int k = 0; k < n; k++) grantRelic(p, Relics.BLOOD_PACT);
                    }
                    notice = "血契成立 · 生命被抽走 " + pct(pa) + "，血脉契约 +" + n + " 层";
                } else {
                    coins += Math.round((int) pc * metaGain);
                    notice = "你封住了井口 · 得到 " + (int) pc + " 星尘";
                }
                break;
            }

            // ── 1 献金：用星尘换内容（COST_A 已预付）───────────────
            // PARAM_A = 遗物件数；PARAM_B = B 扣的当前生命；PARAM_C = B 的星尘收益。
            case 1: {
                if (a) {
                    int n = Math.max(1, (int) pa);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < n; k++) {
                        int id = rollRelic(lead);
                        for (Player q : players) grantRelic(q, id);
                        sb.append(k == 0 ? "" : "、").append(Relics.NAMES[id]);
                    }
                    notice = "你带回 " + n + " 件遗物：" + sb;
                } else {
                    for (Player p : players) p.hp = Math.max(1f, p.hp - pb);
                    coins += Math.round((int) pc * metaGain);
                    notice = "受到灼伤 -" + (int) pb + " 生命，回收 " + (int) pc + " 星尘";
                }
                break;
            }

            // ── 2 预言：用资源换信息（免费重掷）───────────────────
            // PARAM_A = 免费重掷次数；PARAM_B = A 额外扣的当前生命比例；PARAM_C = B 的星尘。
            case 2: {
                if (a) {
                    int n = Math.max(1, (int) pa);
                    scouted += n;
                    if (pb > 0f) for (Player p : players) p.hp = Math.max(1f, p.hp - p.hp * pb);
                    notice = "命运被改写 · 获得 " + n + " 次免费重掷"
                            + (pb > 0f ? "（-" + pct(pb) + " 生命）" : "");
                } else {
                    coins += Math.round((int) pc * metaGain);
                    notice = "你保留了星尘 · +" + (int) pc;
                }
                break;
            }

            // ── 3 祭坛：血 + 星尘同时付，换确定数量的遗物 ──────────
            // PARAM_A = 当前生命比例代价；PARAM_B = 遗物件数；PARAM_C = B 的治疗量。
            case 3: {
                if (a) {
                    for (Player p : players) p.hp = Math.max(1f, p.hp - p.hp * pa);
                    int n = Math.max(1, (int) pb);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < n; k++) {
                        int id = rollRelic(lead);
                        for (Player q : players) grantRelic(q, id);
                        sb.append(k == 0 ? "" : "、").append(Relics.NAMES[id]);
                    }
                    notice = "祭坛收下血与星尘 · " + n + " 件遗物：" + sb;
                } else {
                    for (Player p : players) heal(p, pc);
                    notice = "安息祝福 · 回复 " + (int) pc + " 生命";
                }
                break;
            }

            // ── 4 医疗：立刻回复，3 站后挂账回收 ──────────────────
            // PARAM_A = 回复量；PARAM_B = 延迟的生命比例代价；PARAM_C = B 的星尘。
            case 4:
                if (a) {
                    for (Player p : players) { heal(p, pa * metaGain); p.skillCd = p.dashCd = 0; }
                    scheduleDelay(DELAY_HP, 3, pb);
                    notice = "急救站 · 回复 " + (int) pa + " 生命并清空冷却（3 站后 -" + pct(pb) + "）";
                } else {
                    coins += Math.round((int) pc * metaGain);
                    notice = "搜刮车厢 · +" + (int) pc + " 星尘";
                }
                break;

            // ── 5 锻造：强化已有构筑，依赖当前 build ───────────────
            // PARAM_A = 强化层数；PARAM_B = 目标模式（0=最厚，1=最贵，2=最薄）；
            // PARAM_C = B 的星尘收益。没有遗物时退化为 +12% 成长伤害。
            // 三种目标模式让同 TYPE 的事件在不同 build 下给出不同决策：
            // 堆单件的玩法要"最厚"，广铺的玩法要"最薄"。
            case 5: {
                if (a) {
                    int mode = (int) pb;
                    int best = -1;
                    for (int i = 0; i < Relics.COUNT; i++) {
                        if (lead.relics[i] <= 0) continue;
                        if (best < 0) { best = i; continue; }
                        if (mode == 1) {
                            if (Relics.COST[i] > Relics.COST[best]) best = i;
                        } else if (mode == 2) {
                            if (lead.relics[i] < lead.relics[best]) best = i;
                        } else {
                            if (lead.relics[i] > lead.relics[best]) best = i;
                        }
                    }
                    int n = Math.max(1, (int) pa);
                    if (best >= 0) {
                        for (int k = 0; k < n; k++) for (Player q : players) grantRelic(q, best);
                        notice = Relics.NAMES[best] + " +" + n + " 层";
                    } else {
                        for (Player q : players) { growDamageMul(q, 1.12f); q.damage *= 1.12f; }
                        notice = "没有可强化的遗物 · 基础伤害 +12%";
                    }
                } else {
                    coins += Math.round((int) pc * metaGain);
                    notice = "变卖废料 · +" + (int) pc + " 星尘";
                }
                break;
            }

            // ── 6 赌局：单一概率，赢了给遗物，输了掉当前生命 ────────
            // PARAM_A = 胜率；PARAM_B = 胜利遗物件数；PARAM_C = 失败扣的当前生命比例。
            case 6: {
                if (a) {
                    if (random.nextFloat() < pa) {
                        int n = Math.max(1, (int) pb);
                        StringBuilder sb = new StringBuilder();
                        for (int k = 0; k < n; k++) {
                            int id = rollRelic(lead);
                            for (Player q : players) grantRelic(q, id);
                            sb.append(k == 0 ? "" : "、").append(Relics.NAMES[id]);
                        }
                        notice = "赌赢了 · " + sb;
                    } else {
                        for (Player p : players) p.hp = Math.max(1f, p.hp - p.maxHp * pc);
                        notice = "赌输了 · 生命被抽走 " + pct(pc) + "（按最大生命）";
                    }
                } else {
                    // 「见好就收」的价码 = 每件潜在遗物 12 星尘：
                    // 赔率越高的赌局，收手时拿到的钱也越多，否则大赌局的 B 永远被支配。
                    int gain = Math.max(1, (int) pb) * 12;
                    coins += gain;
                    notice = "见好就收 · +" + gain + " 星尘";
                }
                break;
            }

            // ── 7 诅咒：拿强遗物，背持久负面（唯一产生 CURSED 的结构）──
            // PARAM_A = 遗物件数；PARAM_B = B 的回复量；PARAM_C = 无（保留）。
            // EventContractTest 依赖：只有 TYPE 7 可以增加 Relics.CURSED。
            case 7: {
                if (a) {
                    int n = Math.max(1, (int) pa);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < n; k++) {
                        int id = rollRelic(lead);
                        for (Player q : players) grantRelic(q, id);
                        sb.append(k == 0 ? "" : "、").append(Relics.NAMES[id]);
                    }
                    for (Player q : players)
                        q.relics[Relics.CURSED] = Math.min(2, q.relics[Relics.CURSED] + 1);
                    notice = "你接受诅咒 · " + sb + "（承伤提升）";
                } else {
                    for (Player p : players) heal(p, pb * metaGain);
                    notice = "你拒绝了诱惑 · 回复 " + (int) pb + " 生命";
                }
                break;
            }

            // ── 8 风险累积：叠一层血债，3 站后一次性还清 ────────────
            // PARAM_A = 每层血债的生命比例；PARAM_B = 立即给的星尘；
            // PARAM_C = B（抽身）的星尘收益。
            // 层数越多，结算越痛——但每层的即时星尘是固定的，所以"早点抽身"更划算。
            case 8:
                if (a) {
                    eventDebt = Math.min(9, eventDebt + 1);
                    coins += Math.round((int) pb * metaGain);
                    // 挂账量 = 当前全部层数 × 每层比例（覆盖写入，结算时按当时层数重算）
                    rescheduleDebt(pa * eventDebt);
                    notice = "深渊记下第 " + eventDebt + " 笔债 · +" + (int) pb
                            + " 星尘（3 站后 -" + pct(pa * eventDebt) + "）";
                } else {
                    eventDebt = Math.max(0, eventDebt - 1);
                    coins += Math.round((int) pc * metaGain);
                    rescheduleDebt(pa * eventDebt);
                    notice = "你抽身了 · 剩 " + eventDebt + " 层债，+" + (int) pc + " 星尘";
                }
                break;

            // ── 9 延迟结算：现在白拿，3 站后付代价 ─────────────────
            // PARAM_A = 立即遗物件数；PARAM_B = 挂账量；PARAM_C = 币种（0=生命比例，1=星尘）。
            // 注意：B 分支的星尘收益写死在 PB（与 A 的挂账量无关），
            // 否则"生命币种"的事件会给出一个不合理的 B 数值。
            case 9: {
                if (a) {
                    int n = Math.max(1, (int) pa);
                    for (int k = 0; k < n; k++) {
                        int id = rollRelic(lead);
                        for (Player q : players) grantRelic(q, id);
                    }
                    if ((int) pc == 1) {
                        scheduleDelay(DELAY_COINS, 3, pb);
                        notice = "先拿 " + n + " 件遗物 · 3 站后付 " + (int) pb + " 星尘";
                    } else {
                        scheduleDelay(DELAY_HP, 3, pb);
                        notice = "先拿 " + n + " 件遗物 · 3 站后付 " + pct(pb) + " 生命";
                    }
                } else {
                    // 不签 = 直接把挂账量折算成星尘拿走（生命币种按 6 星尘最低值兜底）。
                    int gain = Math.max(6, Math.round(pb));
                    coins += gain;
                    notice = "你没有签 · +" + gain + " 星尘";
                }
                break;
            }

            // ── 10 二选一赌注：两侧都有明确失败概率，赔率互补 ──────
            // PARAM_A = A 胜率；PARAM_B = A 失败代价（当前生命比例）；
            // PARAM_C = B 失败代价。B 的胜率 = 1 - A 胜率：两侧互为镜像，
            // A 高赔率低概率、B 低赔率高概率，玩家按当前血量选。
            case 10: {
                float winA = pa;
                float winB = Math.max(.05f, 1f - pa);
                if (a) {
                    if (random.nextFloat() < winA) {
                        for (Player q : players) grantRelic(q, rollRelic(q));
                        notice = "赌注兑现 · 你拿到了一件遗物";
                    } else {
                        for (Player p : players) p.hp = Math.max(1f, p.hp - p.maxHp * pb);
                        notice = "赌注落空 · 生命 -" + pct(pb);
                    }
                } else {
                    if (random.nextFloat() < winB) {
                        for (Player q : players) { grantRelic(q, rollRelic(q)); grantRelic(q, rollRelic(q)); }
                        notice = "大赌赢了 · 两件遗物落进你手里";
                    } else {
                        for (Player p : players) p.hp = Math.max(1f, p.hp - p.maxHp * pc);
                        notice = "大赌输了 · 生命 -" + pct(pc);
                    }
                }
                break;
            }

            // ── 11 交换：用一件已有遗物换其他资源 ─────────────────
            // PARAM_A = 交换额外得到的星尘；PARAM_B = 换回的遗物件数；PARAM_C = B 的星尘收益。
            // 空手时降级为"深渊白送 1 件"，避免负收益死局。
            case 11: {
                if (a) {
                    int lost = -1;
                    for (int i = Relics.COUNT - 1; i >= 0; i--)
                        if (lead.relics[i] > 0) { lost = i; break; }
                    int n = Math.max(1, (int) pb);
                    int paid = (int) pa;
                    if (lost >= 0) {
                        for (Player q : players) {
                            q.relics[lost] = Math.max(0, q.relics[lost] - 1);
                            for (int k = 0; k < n; k++) grantRelic(q, rollRelic(q));
                            recompute(q);
                        }
                        coins += paid;
                        notice = "交出「" + Relics.NAMES[lost] + "」 · 换回 " + n + " 件未知"
                                + (paid > 0 ? " + " + paid + " 星尘" : "");
                    } else {
                        for (Player q : players) grantRelic(q, rollRelic(q));
                        notice = "你身上没有可交换的 · 深渊留下 1 件";
                    }
                } else {
                    coins += Math.round((int) pc * metaGain);
                    notice = "你没有交换 · +" + (int) pc + " 星尘";
                }
                break;
            }

            // ── 12 反向代价：两个选项都要付血，"拒绝"更贵 ──────────
            // PARAM_A = A 的当前生命比例代价；PARAM_B = A 的遗物件数；
            // PARAM_C = B 的当前生命比例代价（必须 > PARAM_A）。
            case 12: {
                if (a) {
                    for (Player p : players) p.hp = Math.max(1f, p.hp - p.hp * pa);
                    int n = Math.max(1, (int) pb);
                    for (int k = 0; k < n; k++) {
                        int id = rollRelic(lead);
                        for (Player q : players) grantRelic(q, id);
                    }
                    notice = "你付了 " + pct(pa) + " 生命 · 换回 " + n + " 件遗物";
                } else {
                    // 拒绝也要付血，但血会换成星尘：付得越多，换回越多。
                    for (Player p : players) p.hp = Math.max(1f, p.hp - p.hp * pc);
                    int gain = Math.round(pc * 100f);
                    coins += gain;
                    notice = "拒绝也要付 · 生命 -" + pct(pc) + "，换回 " + gain + " 星尘";
                }
                break;
            }

            default:
                // 不再用业务分支兜底：未知 TYPE 必须炸出来，而不是静默给错奖励。
                throw new IllegalStateException("resolveEvent: 未实现的 Event TYPE " + type);
        }
        return true;
    }

    /** 百分比排版：0.26 → "26%"，用于 notice 文案。 */
    private static String pct(float ratio) {
        return Math.round(ratio * 100f) + "%";
    }

    /**
     * 登记一笔独立挂账。TYPE 4 / 9 的代价不可被后来事件冲抵，来源用于追踪。
     * 3 站的倒计时从玩家离开该事件房时第一次递减，与旧版本节拍兼容。
     */
    private void scheduleDelay(int kind, int rooms, float amount) {
        if (kind == DELAY_NONE || !(amount > 0f) || !Float.isFinite(amount)) return;
        if (delayedCharges.size() >= MAX_DELAYED_CHARGES)
            throw new IllegalStateException("delayed ledger full");
        delayedCharges.add(new DelayedCharge(kind, Math.max(1, rooms), amount,
                Events.TYPE[eventType]));
        refreshDelayedSummary();
    }

    private boolean hasDebtCharge() {
        for (DelayedCharge c : delayedCharges) if (c.source == DELAY_SOURCE_DEBT) return true;
        return false;
    }

    /** 风险累积事件只修改自己那笔账，清零必须真的撤销，不能顺手抹掉医疗账单。 */
    private void rescheduleDebt(float amount) {
        for (int j = 0; j < delayedCharges.size(); j++) {
            DelayedCharge c = delayedCharges.get(j);
            if (c.source != DELAY_SOURCE_DEBT) continue;
            if (!(amount > 0f)) delayedCharges.remove(j);
            else { c.kind = DELAY_HP; c.rooms = 3; c.amount = amount; }
            refreshDelayedSummary();
            return;
        }
        if (amount > 0f) {
            if (delayedCharges.size() >= MAX_DELAYED_CHARGES)
                throw new IllegalStateException("delayed ledger full");
            delayedCharges.add(new DelayedCharge(DELAY_HP, 3, amount, DELAY_SOURCE_DEBT));
        }
        refreshDelayedSummary();
    }

    /** 兼容 v9/v10 的单笔字段：始终镜像最早到期的一笔，不参与账本真值运算。 */
    void refreshDelayedSummary() {
        DelayedCharge nearest = null;
        for (DelayedCharge c : delayedCharges)
            if (nearest == null || c.rooms < nearest.rooms) nearest = c;
        delayedKind = nearest == null ? DELAY_NONE : nearest.kind;
        delayedRooms = nearest == null ? 0 : nearest.rooms;
        delayedAmount = nearest == null ? 0f : nearest.amount;
    }

    /** Codec v9/v10 迁移入口：遗留快照只有一笔，按原数值继续履约。 */
    void migrateLegacyDelay() {
        delayedCharges.clear();
        if (delayedKind != DELAY_NONE && delayedRooms > 0 && delayedAmount > 0f)
            delayedCharges.add(new DelayedCharge(delayedKind, delayedRooms, delayedAmount,
                    DELAY_SOURCE_LEGACY));
        refreshDelayedSummary();
    }

    /** 在一局新的远征或测试差分对照中清空当前挂账。不会影响其他模拟状态。 */
    public void clearPendingPenalties() {
        delayedCharges.clear();
        eventDebt = 0;
        refreshDelayedSummary();
    }

    /** 当前全部在途笔数（不只是一笔旧式摘要）。 */
    public int pendingChargeCount() { return delayedCharges.size(); }

    /** 最早到期的站数；无欠款返回 0。 */
    public int nearestChargeRooms() { return delayedRooms; }

    /**
     * 每次进站为每笔账独立倒计时；同站到期的所有账同时清算。
     * 扣生命保底 1，扣星尘保底 0；扣除的是当前资源且真实落入结算。
     */
    private void settleDelayedPenalty() {
        if (delayedCharges.isEmpty()) return;
        StringBuilder settled = new StringBuilder();
        for (Iterator<DelayedCharge> it = delayedCharges.iterator(); it.hasNext();) {
            DelayedCharge c = it.next();
            if (--c.rooms > 0) continue;
            if (c.kind == DELAY_HP) {
                for (Player p : players) p.hp = Math.max(1f, p.hp - p.maxHp * c.amount);
                if (settled.length() > 0) settled.append("；");
                settled.append("生命 -").append(pct(c.amount));
            } else if (c.kind == DELAY_COINS) {
                int due = Math.min(coins, Math.round(c.amount));
                coins -= due;
                if (settled.length() > 0) settled.append("；");
                settled.append("星尘 -").append(due);
            }
            if (c.source == DELAY_SOURCE_DEBT || c.source == DELAY_SOURCE_LEGACY)
                eventDebt = 0;
            it.remove();
        }
        refreshDelayedSummary();
        if (settled.length() > 0) {
            delayedNote = "挂账到期 · " + settled;
            notice = delayedNote;
        }
    }

    /** 是否有任何挂账（v11 多笔账本）。 */
    public boolean hasDelayedPenalty() { return !delayedCharges.isEmpty(); }

    public boolean continueEvent() {
        if (phase != EVENT || eventChoice < 0) return false;
        advance();
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  商店
    // ══════════════════════════════════════════════════════════════

    public int shopPrice(int index) {
        if (index < 0 || index >= 3) return -1;
        return shopPrices[index];
    }

    /**
     * 商店重摆的价目阶梯（v2.3 #3）。
     *
     * <p>前三次与历史价 {@link Balance#rerollPrice}（30 / 45 / 70）<b>逐位一致</b>，
     * 之后按 ×1.5 递推到 105，再被 {@link #SHOP_REROLL_CAP_PRICE} 封在 120。
     * 设计依据（{@code docs/research-best-in-class-2026-10b.md} §2.3）：
     * Brotato 的重摆价格随波次递增且<b>没有硬性次数上限</b>，
     * DRG:S 的中途升级重摆是 5/7/10/14/20/28/39/55/77/108——
     * 共同点是"越刷越贵，但从不告诉你'次数用尽'"。
     * 硬上限的问题不是价格，而是<b>死局</b>：钱还剩 300 却因为"今日 3 次用完"
     * 再也花不出去，货架上全是满层遗物时玩家只能空手离开。
     */
    private static final int[] SHOP_REROLL_LADDER = {30, 45, 70, 105};
    /** 重摆价格的天花板：越过这一档后价格恒定，而不是无限通胀到买不起。 */
    public static final int SHOP_REROLL_CAP_PRICE = 120;

    /**
     * 本店下一次重摆的实际价格（0 = 本次免费）。
     *
     * <p>UI 应当读这个而不是 {@link Balance#rerollPrice}——后者在 {@code used >= 3}
     * 时返回 {@code Integer.MAX_VALUE}（"次数用尽"），与去掉硬上限后的规则不符。
     * 保留 {@link Balance#rerollPrice} 不动是为了它自己的单测与 {@code Balance.valid()}。
     */
    public int shopRerollPrice() {
        if (shopFreeRerolls > 0) return 0;
        int used = Math.max(0, shopRerolls);
        if (used < SHOP_REROLL_LADDER.length) return SHOP_REROLL_LADDER[used];
        // 第 5 次起：105 × 1.5 = 157，被 120 封顶 → 之后恒定 120。
        return SHOP_REROLL_CAP_PRICE;
    }

    /**
     * 三格是否全部买下（v2.3 #3 买空送重摆的判定）。
     * <p>空货格（{@code shopOffers == -1} 且从未售出）不算买下——
     * 货架本来就摆不满时不该发奖励，否则"买空"会退化成"进店送重摆"。
     */
    public boolean shopSoldOut() {
        for (int i = 0; i < 3; i++) if (!shopSold[i]) return false;
        return true;
    }

    public boolean buyShop(int index) {
        if (phase != SHOP || index < 0 || index >= 3) return false;
        int price = shopPrices[index];
        int id = shopOffers[index];
        if (price < 0 || id < 0 || shopSold[index] || coins < price) return false;
        coins -= price;
        shopSold[index] = true;
        shopOffers[index] = -1;
        shopPrices[index] = -1;
        for (Player p : players) grantRelic(p, id);
        notice = "已购买 " + Relics.NAMES[id];
        // ── v2.3 #3「买空送重摆」──────────────────────────────────────
        // 依据：Brotato「Buying all four offers grants a free reroll.」
        // 这是对"把钱花完"的正向反馈，也让"最后一件买不起"变成"买完还能再刷一次"。
        // 每次进店最多 1 次（shopBuyoutRewardUsed），免费次数不算进 shopRerolls，
        // 因此不会打折后续的价格阶梯——避免"买空 → 免费 → 买空"滚成复利。
        if (!shopBuyoutRewardUsed && shopSoldOut()) {
            shopBuyoutRewardUsed = true;
            shopFreeRerolls = Math.max(shopFreeRerolls, 1);
            notice = "已购买 " + Relics.NAMES[id] + " · 买空赠礼：本店免费重摆 1 次";
        }
        return true;
    }

    public boolean rerollShop() {
        if (phase != SHOP) return false;
        // v2.3 #3：去掉 shopRerolls >= 3 的硬上限，只靠递增价格与 120 的天花板收敛。
        boolean free = shopFreeRerolls > 0;
        int price = free ? 0 : shopRerollPrice();
        if (coins < price) return false;
        coins -= price;
        if (free) shopFreeRerolls--;
        else shopRerolls++;             // shopRerolls 的语义保持"本店已付费重摆次数"
        fillShop();
        notice = free ? "重摆货物 · 买空赠礼（免费）" : "重摆货物 · 花费 " + price + " 星尘";
        return true;
    }

    public boolean leaveShop() {
        if (phase != SHOP) return false;
        advance();
        return true;
    }

    private void fillShop() {
        Player p = players.isEmpty() ? null : players.get(0);
        // ── v2.0 支柱 2：货架过滤（#3 流派封锁 / #17 遗物栏上限）──────
        int locked = lockedSchool();
        boolean full = p != null && distinctRelics(p) >= doctrineRelicSlots();
        ArrayList<Integer> choices = new ArrayList<>();
        // v2.2：把 {@link ClassRules} 也接到商店——同一个职业，三选一与货架必须
        // 用同一套"收什么、不收什么"。否则玩家会看到"三选一里永远没有的东西
        // 乖乖躺在商店里"，规则的表达就失效了。
        int role = p == null ? -1 : p.role;
        for (int j = 0; j < Relics.COUNT; j++) {
            if (locked >= 0 && Relics.SCHOOL[j] == locked) continue;      // #3 余烬开局
            if (p != null && p.relics[j] >= Relics.CAP[j]) continue;      // 满层不上架
            if (full && p != null && p.relics[j] <= 0) continue;          // #17 遗物栏已满
            if (!ClassRules.allows(role, j)) continue;                    // 职业硬门槛
            choices.add(j);
        }
        // 硬门槛把货架抽空时（理论上不该发生，ClassRules 保证每职业 ≥25 件合法），
        // 退化成不过滤，宁可给错也不要让商店空着。
        if (choices.isEmpty()) {
            for (int j = 0; j < Relics.COUNT; j++) {
                if (locked >= 0 && Relics.SCHOOL[j] == locked) continue;
                if (p != null && p.relics[j] >= Relics.CAP[j]) continue;
                choices.add(j);
            }
        }
        int discount = p == null ? 0 : p.discount;
        float priceMul = Balance.pactShopMul(this) * doctrineShopPriceMul();
        for (int i = 0; i < 3; i++) {
            shopSold[i] = false;
            if (choices.isEmpty()) { shopOffers[i] = -1; shopPrices[i] = -1; continue; }
            int id = choices.remove(random.nextInt(choices.size()));
            shopOffers[i] = id;
            shopPrices[i] = Math.max(1, Math.round(
                    Balance.shopPrice(Relics.COST[id], discount) * priceMul));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  营火 / 铁匠铺 / 神龛
    // ══════════════════════════════════════════════════════════════

    public boolean camp(int choice) {
        if (phase != CAMP || choice < 0 || choice > 2) return false;
        for (Player p : players) {
            if (choice == 0) heal(p, p.maxHp * .45f);
            else if (choice == 1) {
                growDamageMul(p, 1.10f); p.damage *= 1.10f;
                growHp(p, 14f); p.maxHp += 14; p.hp += 14;
            } else {
                growSpeedMul(p, 1.06f); p.speed *= 1.06f;
                growCrit(p, .06f); p.crit = Math.min(.7f, p.crit + .06f);
            }
            p.down = false;
        }
        if (choice == 2) coins += 24;
        notice = choice == 0 ? "休息 · 生命恢复 45%" : choice == 1 ? "锻造 · 伤害 +10%，生命上限 +14" : "探索 · 移速 +6%，暴击 +6%，星尘 +24";
        if (campAfterBoss) { campAfterBoss = false; generateRoute(); phase = MAP; }
        else advance();
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  武器形态（weaponForm）
    //
    //  背景：Game.fire() 里有三处 weaponForm 分支（弹道 +1 且单发 -32% /
    //  单发 +36% / 攻速与散布变化），Codec 也序列化了这个字段——但全项目
    //  **只有 `p.weaponForm = 0` 一次写入**，三种形态永远只有第 0 种可用。
    //  这属于「消费点齐全、产生路径缺失」的休眠系统，与 Boss 冲锋同类。
    //
    //  设计：形态不单独开一个界面，而是挂到已有的铁匠铺上——
    //  铁匠铺本来就是"改造武器"的语义，三步流程（选形态 → 选进化线 → 离开）
    //  不需要新屏幕，也不会和「进化」抢概念：进化换的是招式的**内容**，
    //  形态换的是**手感**（更快更散 / 更慢更重 / 原样）。
    // ══════════════════════════════════════════════════════════════

    /** 形态数：0 标准 / 1 连发 / 2 重击。 */
    public static final int FORM_COUNT = 3;
    public static final String[] FORM_NAME = {"标准枪管", "连发机匣", "重击膛线"};
    public static final String[] FORM_DESC = {
            "不改变任何参数。稳，但没有偏向。",
            "弹道 +1、单发伤害 -32%、攻速 +14%、散布更宽。适合靠数量与覆盖。",
            "单发伤害 +36%、攻速 -20%、散布更窄。适合靠单发命中与穿透。"
    };
    public static final String[] FORM_TAG = {"原样", "更多更散", "更少更重"};

    /** 铁匠铺当前是否应让玩家先选形态（还没选过）。 */
    public boolean needsWeaponForm() {
        if (phase != ARMORY) return false;
        for (Player p : players) if (p.connected && p.weaponForm == 0 && !p.formChosen) return true;
        return false;
    }

    /**
     * 在铁匠铺里选定武器形态。选完仍停留在 ARMORY，紧接着选进化线。
     *
     * <p>形态 0 是"不改变"，但它同样算一次主动选择——所以用 {@code formChosen}
     * 而不是 {@code weaponForm != 0} 来判断"选过了"，否则选标准枪管的玩家会被反复要求选。
     */
    public boolean chooseWeaponForm(int form) {
        if (phase != ARMORY || form < 0 || form >= FORM_COUNT) return false;
        for (Player p : players) {
            if (!p.connected) continue;
            p.weaponForm = form;
            p.formChosen = true;
            recompute(p);
        }
        notice = "武器形态 · " + FORM_NAME[form];
        return true;
    }

    /** 铁匠铺：选择一条武器进化线。 */
    public boolean evolveWeapon(int line) {
        if (phase != ARMORY || line < 0 || line > 2) return false;
        int idx = Evolutions.index(players.isEmpty() ? 0 : players.get(0).role, line);
        // ── v2.0 支柱 2 #1 双重进化：每次进化机会解锁 param 条线 ────────
        // 选中的线本身照常生效，额外的线取"本职业下一条尚未解锁且可用的线"。
        int[] extras = new int[doctrineEvolutionsPerPick() - 1];
        int found = 0;
        for (int l = 0; l < 3 && found < extras.length; l++) {
            int other = Evolutions.index(players.isEmpty() ? 0 : players.get(0).role, l);
            if (other == idx) continue;
            Player probe = players.isEmpty() ? null : players.get(0);
            if (probe != null && probe.evolved[other]) continue;
            if (!EvolutionAvailable(this, probe, other)) continue;
            extras[found++] = other;
        }
        boolean any = false;
        for (Player p : players) {
            if (!p.evolved[idx]) { p.evolved[idx] = true; p.evolutionsTaken++; any = true; }
            p.evolution = idx + 1;
            for (int k = 0; k < found; k++)
                if (extras[k] >= 0 && !p.evolved[extras[k]]) {
                    p.evolved[extras[k]] = true;
                    p.evolutionsTaken++;
                    any = true;
                }
            recompute(p);
        }
        if (!any) { // 全部已拥有：转化为强化
            for (Player p : players) { growDamageMul(p, 1.15f); p.damage *= 1.15f; }
            notice = "铁匠铺 · 已拥有全部进化，改为基础伤害 +15%";
        } else {
            notice = "进化解锁 · " + Evolutions.NAMES[idx];
        }
        advance();
        return true;
    }

    /** 神龛：花星尘换一次强化，或免费拿一件随机遗物但承伤提升。 */
    public boolean shrine(int choice) {
        if (phase != SHRINE || shrineUsed || choice < 0 || choice > 1) return false;
        // 先校验再落锁：旧代码在余额判断之前就设了 shrineUsed，
        // 付不起时 shrine(0) 返回 false 却已烧掉神龛，之后 shrine(1) 也拒绝，
        // 阶段永远停在 SHRINE(10)，玩家彻底卡死。
        if (choice == 0 && coins < 60) return false;
        shrineUsed = true;
        if (choice == 0) {
            coins -= 60;
            for (Player p : players) {
                growCrit(p, .10f); p.crit = Math.min(.7f, p.crit + .10f);
                growDamageMul(p, 1.12f); p.damage *= 1.12f;
            }
            notice = "神龛回应 · 暴击 +10%，伤害 +12%";
        } else {
            for (Player p : players) { grantRelic(p, rollRelic(p)); p.relics[Relics.CURSED] = Math.min(2, p.relics[Relics.CURSED] + 1); }
            notice = "你带走了供品 · 获得遗物，但本层承伤提升";
        }
        advance();
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  三选一 / 进化选择
    // ══════════════════════════════════════════════════════════════

    public void choose(int id, int card, int ignoredVote) {
        Player p = player(id);
        if (phase != CHOICE || p == null || p.chosen >= 0 || card < 0 || card > 2) return;
        p.chosen = card;
        int relic = p.offers[card];
        grantRelic(p, relic);
        int evo = p.evolveOffer[card];
        if (evo >= 0) applyEvolution(p, evo);
    }

    public boolean rerollDraft(int id) {
        Player p = player(id);
        if (phase != CHOICE || p == null || p.chosen >= 0 || rerolls <= 0) return false;
        int cost = scouted > 0 ? 0 : Balance.RELIC_REROLL_COST;
        if (coins < cost) return false;
        coins -= cost;
        rerolls--;
        if (scouted > 0) scouted--;
        // v2.3 #2：重掷必须换牌——把刚看过的那一手作为避让集合传下去。
        offer(p, true);
        return true;
    }

    /**
     * 把选择卡切换成"武器进化"卡（由 UI 的进化按钮调用）。
     *
     * <p><b>v2.3 #7 进化时刻三选一</b>：此前这里只写 {@code evolveOffer[0]}，
     * {@code evolveOffer[1]/[2]} 恒为 -1，于是"每个人物有 3 条进化线"的宣传在
     * 决策点上只兑现成 <b>1 张卡</b>。依据 {@code docs/research-best-in-class-2026-10b.md}
     * §2.0 #7：这是本项目"只有一个选项的决策时刻"里最刺眼的一处——
     * 玩家点开进化按钮，看到的不是选择，而是一个确认框。
     *
     * <p>现在按线序（0→1→2）收集<b>所有</b>当前可用的线，填满前 3 格：
     * <ul>
     *   <li>资格门槛不变：{@code p.evolved[idx]} 排除已拥有的线，
     *       {@link #EvolutionAvailable} 继续管 TIER2 的等级与遗物前置；</li>
     *   <li>只有 1 条可用时仍只给 1 张（例如 Lv5、未持有 TIER2 前置遗物），这是设计允许的；</li>
     *   <li><b>不改 {@code Player.evolution} 的持久语义</b>：它仍然只在
     *       {@link #applyEvolution} 里被写成"选中那条线的 {@code idx+1}"，
     *       一次选择只应用玩家实际点的那一张卡。</li>
     * </ul>
     * 收集是确定性的（固定线序），不消耗随机数。
     */
    public boolean switchToEvolution(int id) {
        Player p = player(id);
        if (phase != CHOICE || p == null || p.chosen >= 0) return false;
        // 收集全部可用的进化线（最多 3 条，每职业就是 3 条）。
        int[] lines = new int[3];
        int count = 0;
        for (int line = 0; line < 3; line++) {
            int idx = Evolutions.index(p.role, line);
            if (p.evolved[idx]) continue;
            if (!EvolutionAvailable(this, p, idx)) continue;
            lines[count++] = idx;
        }
        if (count == 0) return false;
        for (int i = 0; i < 3; i++) {
            p.offers[i] = -1;
            p.evolveOffer[i] = i < count ? lines[i] : -1;
        }
        return true;
    }

    /**
     * 当前这位玩家有几条进化线可用（UI 用来判断"进化按钮该不该亮"）。
     *
     * <p>与 {@link #switchToEvolution(int)} 共用同一套资格判定，
     * 避免出现"按钮亮着但点下去只有 0 张卡"（以前是"按钮亮着但只有 1 张卡"）。
     * {@code DraftScreen} 目前用 {@code !p.evolved[idx]} 粗判，但那会把
     * "未满 Lv8 / 缺前置遗物"的 TIER2 线也算成可用；本方法是更准确的口径。
     *
     * <p>注：v2.3 之前这里还有一个 {@code pickEvolutionLine}（只返回<b>第一条</b>可用线），
     * 它是"进化只给 1 张卡"的根因，改成多线收集后已无调用点，故删除。
     */
    public int evolutionChoices(Player p) {
        if (p == null) return 0;
        int n = 0;
        for (int line = 0; line < 3; line++) {
            int idx = Evolutions.index(p.role, line);
            if (p.evolved[idx]) continue;
            if (!EvolutionAvailable(this, p, idx)) continue;
            n++;
        }
        return n;
    }

    private boolean EvolutionAvailable(Game g, Player p, int idx) {
        int tier = Evolutions.TIER[idx];
        if (tier == 2) {
            // ── v2.0 支柱 2 #1 双重进化：进化等级门槛 +3（副钩子位）──────
            if (g.level < 8 + doctrineEvolveLevelPen()) return false;
            int req = Evolutions.REQ_RELIC[idx];
            return req < 0 || p.relics[req] > 0;
        }
        return true;
    }

    /**
     * 本次进化机会可以解锁几条线。
     *
     * <p><b>与规格的偏差（必须记录）</b>：{@code docs/doctrine-hooks.md} 假设"每局只能进化 1 条线"，
     * 把 {@code H_MAX_EVOLUTIONS=2} 实现为"上限 2"。但当前引擎的实际基线是
     * <b>每局最多 3 条线</b>（每个职业 3 条，铁匠铺每次给 1 条），照字面实现会把这张牌
     * 变成"少一条线 + 门槛 +3"的纯亏损。因此这里保留钩子的语义（param = 进化条数），
     * 但落在"<b>每次进化机会解锁 param 条线</b>"上：默认 1，装备后 2。
     * 收益是真实的（铁匠铺/三选一的进化位是稀缺资源），代价（TIER2 门槛 +3）也真实。
     */
    private int doctrineEvolutionsPerPick() {
        if (mainHook(DoctrineHooks.H_MAX_EVOLUTIONS))
            return Math.max(1, Math.min(3, Math.round(dParam(0))));
        if (sideHook(DoctrineHooks.H_MAX_EVOLUTIONS))
            return Math.max(1, Math.min(3, Math.round(dParam(2))));
        return 1;
    }

    private void applyEvolution(Player p, int idx) {
        if (idx < 0 || idx >= Evolutions.COUNT) return;
        p.evolved[idx] = true;
        p.evolutionsTaken++;
        p.evolution = idx + 1;
        // ── v2.0 支柱 2 #1 双重进化：三选一里的进化卡同样给额外一条线 ──
        int granted = 0;
        for (int l = 0; l < 3 && granted < doctrineEvolutionsPerPick() - 1; l++) {
            int other = Evolutions.index(p.role, l);
            if (other == idx || p.evolved[other]) continue;
            if (!EvolutionAvailable(this, p, other)) continue;
            p.evolved[other] = true;
            p.evolutionsTaken++;
            granted++;
        }
        recompute(p);
        notice = granted > 0
                ? "双重进化 · " + Evolutions.NAMES[idx] + " 与另一条线同时解锁"
                : "武器进化 · " + Evolutions.NAMES[idx];
        effect(p.x, p.y, 180, 6, Content.COLORS[p.role], 1f);
    }

    // ══════════════════════════════════════════════════════════════
    //  遗物
    // ══════════════════════════════════════════════════════════════

    public void grantRelic(Player p, int id) {
        if (id < 0 || id >= Relics.COUNT) return;
        if (p.relics[id] >= Relics.CAP[id]) {
            coins += 18;
            notice = "满层遗物转化为 18 星尘";
            return;
        }
        // ── v2.0 支柱 2 #17 孤注一掷：遗物携带上限按"不同种类数"算 ────
        // 已有的种类升层不受限（否则升满 5 层会变成 5 个槽），只有新种类要占槽。
        // 满槽时按 18 星尘转化，和"满层转化"同一套经济，不会白给。
        if (p.relics[id] <= 0 && distinctRelics(p) >= doctrineRelicSlots()) {
            coins += 18;
            notice = "遗物栏已满 · 转化为 18 星尘";
            return;
        }
        // ── 时序契约（v2.2 修复）──────────────────────────────────────
        // recompute(p) 默认 capture=true，它会把"面板里除遗物以外的部分"反解成成长。
        // 这里的 p.damage/p.maxHp 还是**加遗物之前**的旧面板，而 recompute 内部的
        // relicPower 却是**加完之后**的总量。两者差着这一件遗物，直接反解会让
        // 这笔差额被当成负成长从 bonusPower 里扣掉 —— 表现就是
        //   「捡第 1 件遗物 → bonusPower 0.25 → 0.16；捡第 2 件 → 0.07」
        // 即每捡一件遗物就凭空蒸发一截元进度 / 营地 / 事件攒下的成长。
        //
        // 修法：加遗物之前先用**旧**遗物集合做一次反解（capture=true），
        // 让 bonusPower 与旧面板严格对齐；然后再 ++relics 并走 capture=false，
        // 此时面板 = 遗物(新) + 成长(已对齐)，不会再产生任何"反解"误差。
        recompute(p, true);
        p.relics[id]++;
        recompute(p, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  成长增量（v1.1 修复：营地 / 升级 / 事件 / 元进度不再被 recompute 抹掉）
    // ══════════════════════════════════════════════════════════════
    //
    //  问题：旧 recompute() 只按「职业基线 + 遗物」重算，把 camp()、gainXp()、
    //  resolveEvent()、元进度直接乘到 damage/maxHp/speed/crit 上的成长全部覆盖掉——
    //  升 25 级后拿一件遗物就退回 1 级面板；每次存读档静默删掉整局成长。
    //
    //  修法：成长沉淀到<b>本来就被 Codec 序列化</b>的 bonus* 字段，在 recompute 里
    //  与遗物加数并列相加。既不改 Codec.VERSION，也不破坏存档往返恒等性。
    //  字段语义（一次性固定；这些字段此前没有任何代码读写，属于安全复用）：
    //    bonusPower → 成长伤害增量（绝对值，加在遗物 addPower 上）
    //    bonusArea  → 成长移速增量（绝对值，加在遗物 addSpeed 上）
    //    bonusCrit  → 成长暴击增量（绝对值，加在暴击率上）
    //    bonusHp    → 成长生命增量（绝对值，加在最大生命上）
    //  升级带来的成长<b>不占字段</b>：它只由已序列化的 {@code level} 推导。
    //  （bonusRate / bonusMagnet / bonusRegen / bonusXp / bonusCoin 保持原状，未被使用。）

    /** 每级：伤害乘区 +3.5%，生命上限 +2。 */
    static final float LEVEL_DAMAGE = .035f;
    static final float LEVEL_HP = 2f;

    /** 升级带来的伤害乘区，只依赖已序列化的 level。 */
    private float levelDamageMul() {
        int lv = Math.max(1, level);
        return (float) Math.pow(1 + LEVEL_DAMAGE, lv - 1);
    }

    /** 升级带来的生命上限加数。 */
    private float levelHpBonus() {
        return LEVEL_HP * Math.max(0, level - 1);
    }

    /** 记录一次伤害乘算（调用方仍照常改写 damage，此处只登记增量）。 */
    private void growDamageMul(Player p, float mul) {
        if (p == null || !Float.isFinite(mul)) return;
        p.bonusPower += mul - 1f;
    }

    /** 记录一次移速乘算。 */
    private void growSpeedMul(Player p, float mul) {
        if (p == null || !Float.isFinite(mul)) return;
        p.bonusArea += mul - 1f;
    }

    /** 记录一次暴击增量。 */
    private void growCrit(Player p, float delta) {
        if (p == null || !Float.isFinite(delta)) return;
        p.bonusCrit += delta;
    }

    /** 记录一次生命上限增量。 */
    private void growHp(Player p, float delta) {
        if (p == null || !Float.isFinite(delta)) return;
        p.bonusHp += delta;
    }

    /** 根据遗物与进化重新计算所有派生属性。 */
    /**
     * 重算玩家面板。
     * <p><b>v2.0 P0-1</b>：默认开启 capture —— 绝不清掉"遗物之外的成长"
     * （元进度、Session 注入、事件奖励等直接写裸字段的加成）。
     * capture 是幂等的（写绝对值），因此反复调用安全。
     */
    public void recompute(Player p) {
        recompute(p, true);
    }

    /**
     * @param capture true 时先把"当前裸面板里的非遗物部分"反解进成长增量，
     *                用于吸收元进度在开局前对裸字段的直接写入（Session 注入）。
     */
    /**
     * P0-1：把"当前裸面板相对职业基线的超出部分"反解进成长增量字段。
     *
     * <p>场景：{@code Session.applyMetaBoosts} 在 {@code startSolo} 之前直接写
     * {@code p.maxHp / damage / speed / crit} 等裸字段；随后第一次 {@code recompute}
     * 会用 {@code roleBase*} 重算并覆盖它们，导致整棵元进度树归零。
     *
     * <p><b>幂等性</b>：这里写入的是"超出基线"的绝对值，而不是累加。
     * 因此连续调用两次 {@code recompute(p, true)} 结果不变；而 {@code recompute(p, false)}
     * 之后这些增量仍然保留（它们已并入最终面板）。
     */
    /**
     * 本次遗物会贡献的数值增量 {@code [addPower, addHp, addSpeed]}。
     *
     * <p>与 {@link #recompute} 主循环里的累加**必须使用同一套公式**
     * （同乘法区 {@code schoolMul × relicMul}、同 VITAL 的 24/层）。
     * 一旦两边分叉，{@code captureGrowth} 就会把差额当成"外部成长"永久写进存档。
     */
    private float[] relicNumericContribution(Player p) {
        float mul = doctrineRelicEffectMul();
        float power = 0f, hp = 0f, speed = 0f;
        for (int id = 0; id < Relics.COUNT; id++) {
            int stacks = p.relics[id];
            if (stacks <= 0) continue;
            float numerical = schoolMul(p, Relics.SCHOOL[id]) * mul;
            power += Relics.POWER[id] * stacks * numerical;
            speed += Relics.SPEEDVAL[id] * stacks * numerical;
            if (id == Relics.VITAL) hp += 24 * stacks;
        }
        return new float[]{power, hp, speed};
    }

    /**
     * @param relicPower / relicHp / relicSpeed 本次遗物会贡献的增量，
     *        由 {@link #relicNumericContribution} 算出。反解成长时必须先剔掉它们，
     *        否则遗物加成会被"捕获成成长"并与真正算入的遗物加成叠加 —— 见调用点注释。
     */
    private void captureGrowth(Player p, float baseDamage, float baseHp, float baseSpeed,
                               float relicPower, float relicHp, float relicSpeed) {
        // 伤害：面板 = baseDamage * (1 + addPower + growthPower) * levelDamageMul() * docDmgMul
        // 先剥离等级与天赋牌乘区，再减去职业基线 1.0，得到"非职业基线"的乘区增量。
        // 不剥离天赋牌乘区的话，第一次 recompute 就会把「玻璃大炮 +60%」当成永久成长
        // 写进 bonusPower，之后每读一次档再乘一次，面板会指数膨胀。
        float levelMul = Math.max(1e-6f, levelDamageMul());
        float docDmg = Math.max(1e-6f, doctrinePanelApplied ? doctrineDamageMul() : 1f);
        // 面板公式（见 recompute 末尾）：
        //   damage = baseDamage · (1 + addPower + growthPower) · levelDamageMul · docDmgMul
        //          / (1 + (multishot-1)·0.18)
        // 反解 growthPower 时，等式右边每一项都要还原，包括**多弹丸惩罚**——
        // 旧实现漏了它，导致 multishot>1 的职业每次反解都少算一截。
        float multishotPenalty = 1f + (Math.max(1, Math.min(7, p.multishot)) - 1) * .18f;
        // 语义约定：p.damage 是"上一次 recompute 的结果"，**永远不含本次新加的遗物**
        // （grantRelic 先 ++relics 再 recompute，所以 relicPower 是新总量，
        //  而 p.damage 还是加之前的旧面板）。因此反解时：
        //   旧面板 = base · (1 + relicPower_old + growthPower) · level · doc
        //   relicPower 是新总量，relicPower_old 是"不含本次新增"的量 —— 两者相等
        // 的误差来自本次新增的那一点。为了让语义唯一，这里统一采用：
        //   growthPower = 旧面板反推的总乘区 − 1 − relicPower
        // 这正是"面板里除遗物以外的部分"，也就是所有非遗物成长之和。
        float dmgRatio = p.damage * multishotPenalty / (baseDamage * levelMul * docDmg);
        if (Float.isFinite(dmgRatio)) {
            // 减去 relicPower：面板已经含遗物，不剔掉就会被当成成长重复计入。
            // **不要再减 p.bonusPower**：dmgRatio 是"当前面板"反推出的总乘区，
            // 而 p.bonusPower 是待求的未知量本身；减它会把成长逐轮清零。
            float captured = Math.max(0f, Math.min(20f, dmgRatio - 1f - relicPower));
            if (Math.abs(captured - p.bonusPower) > 1e-4f) p.bonusPower = captured;
        }
        // 生命：面板 = (baseHp + addHp + growthHp + levelHpBonus()) * docHpMul
        float docHp = Math.max(1e-6f, doctrinePanelApplied ? doctrineMaxHpMul() : 1f);
        // 同理：hpDelta 已经是"总成长"，不要再减 p.bonusHp（那是未知量本身）。
        float hpDelta = p.maxHp / docHp - baseHp - levelHpBonus() - relicHp;
        if (Float.isFinite(hpDelta)) {
            float captured = Math.max(0f, Math.min(5000f, hpDelta));
            if (Math.abs(captured - p.bonusHp) > 1e-2f) p.bonusHp = captured;
        }
        // 移速：面板 = baseSpeed * (1 + addSpeed + growthSpeed)
        float spdRatio = p.speed / Math.max(1e-6f, baseSpeed);
        if (Float.isFinite(spdRatio)) {
            float captured = Math.max(0f, Math.min(5f, spdRatio - 1f - relicSpeed));
            if (Math.abs(captured - p.bonusArea) > 1e-4f) p.bonusArea = captured;
        }
        // 暴击：面板 = roleBaseCrit + addCrit + growthCrit
        // #14 雷霆传导把暴击硬归零，此时"不足部分"不是负成长，必须整段跳过，
        // 否则一次 recompute 就把玩家攒了一局的暴击成长永久清空。
        if (!doctrineCritZero()) {
            float critDelta = p.crit - roleBaseCrit(p.role) - p.bonusCrit;
            if (Float.isFinite(critDelta)) {
                float captured = Math.max(0f, Math.min(.75f, critDelta));
                if (Math.abs(captured - p.bonusCrit) > 1e-5f) p.bonusCrit = captured;
            }
        }
    }

    /**
     * v2.0 P0-3：跨流派组合计算。
     *
     * <p>数据来自 {@link Content#CROSS_A} / {@link Content#CROSS_B}（6 组两两组合）。
     * 判定条件：两个流派各持有 <b>至少 1 件</b>遗物。激活后写入
     * {@code crossMask}（位掩码）、{@code crossSeen}（只增不减，用于播报一次）、
     * 以及两个派生量 {@code crossSpeedPower}（疾风+余烬）与 {@code crossRush}（虚空+疾风）。
     *
     * <p><b>v2.1 修复（P0-3 的续）</b>：上一轮把 {@code crossMask} 从"零赋值"接到"有赋值"，
     * 但 6 组里只有 2 组写了派生字段，而这 2 个派生字段<b>又没有任何读取点</b>——
     * 也就是说玩家凑齐任意两组流派，实际收益仍然是 0。现在 6 组全部有真正的消费点：
     * <pre>
     *   0 焚寂  余烬+虚空 —— 灼烧中的敌人受到 +25%，死亡时炸开   → damage() / killEnemy
     *   1 霜雷  霜星+雷光 —— 命中减速目标 22% 概率触发连锁闪电   → updateShots
     *   2 风火  疾风+余烬 —— 移速加成的 35% 转化为伤害          → power()
     *   3 生霜  生命+霜星 —— 承伤 -8%，受击冻结近身攻击者       → hurt()
     *   4 虚步  虚空+疾风 —— 闪避冷却 -25%，闪避后 1.6s 伤害 +20% → doDash / power()
     *   5 雷愈  生命+雷光 —— 每次连锁闪电回 1.5 生命            → lightningChain
     * </pre>
     */
    /**
     * 跨流派共鸣的门槛：两条流派**各**需要至少这么多件遗物。
     *
     * <p><b>为什么从 1 提到 2</b>：触发条件原本是"各 1 件"，而一局能拿 13 件遗物、
     * 6 个流派，实测<b>平均每局点亮 3.4 组</b>（共 6 组）——它不再是"发现了一个 build"，
     * 而是一条几乎必得的被动。接入后炼狱胜率从 47% 直接跳到 88%。
     *
     * <p>提高到各 2 件之后，命中需要在这两条流派上各投入 2 件，
     * 机会成本才够高，"凑齐"才重新变成一个值得追求的目标。
     * 数值本身就够强（每组合等于一件传说级遗物），不需要再放大。
     */
    public static final int CROSS_MIN_STACKS = 2;

    private void computeCross(Player p) {
        int mask = 0;
        for (int i = 0; i < Content.CROSS_COUNT; i++) {
            int a = Content.CROSS_A[i], b = Content.CROSS_B[i];
            if (a < 0 || b < 0 || a >= Content.SCHOOL_COUNT || b >= Content.SCHOOL_COUNT) continue;
            if (schoolCount(p, a) >= CROSS_MIN_STACKS && schoolCount(p, b) >= CROSS_MIN_STACKS)
                mask |= 1 << i;
        }
        p.crossMask = mask;
        p.crossSeen |= mask;
        // 组合 2「风火」：疾风 + 余烬 —— 移动加成的 35% 转化为伤害乘区（power() 读取）
        p.crossSpeedPower = (mask & (1 << 2)) != 0 ? Math.max(0f, p.bonusArea) * .35f : 0f;
        // 组合 4「虚步」：虚空 + 疾风 —— 闪避后的短时增伤（doDash 置位，power() 读取）
        if ((mask & (1 << 4)) == 0) p.crossRush = 0f;
    }

    private void recompute(Player p, boolean capture) {        if (p == null) return;
        float baseDamage = roleBaseDamage(p.role);
        float baseInterval = roleBaseInterval(p.role);
        float baseHp = roleBaseHp(p.role);
        float baseSpeed = roleBaseSpeed(p.role);

        // ── v2.0 P0-1：捕获外部写入的成长（元进度 / Session 注入）──────
        // 不捕获的话，下面的 baseXxx 重算会把元进度加成全部清零。
        //
        // ── v2.2 修复：这里必须先扣掉**本次遗物会贡献的部分** ────────────
        // 原实现直接拿 `p.damage / baseDamage - 1 - p.bonusPower` 当成长捕获。
        // 但 p.damage 是上一次 recompute 的结果，**已经含遗物加成**，
        // 而下面第 2032 行又会把遗物加成再加一遍 —— 于是每件遗物都被算两次：
        //   1 层熔火之心（+9%）→ 实际 +17%？不，是 bonusPower 被写成 .09，
        //   面板再 (1 + .09遗物 + .09成长) = +18%，偏差 +8.3%；
        //   5 层 → 偏差 +31%；13 件遗物 → 审计实测 +54%。
        // 这不影响确定性，但让所有遗物数值都不可信，也把平衡探针的结论带偏。
        //
        // 修法：先把遗物（含流派/天赋牌乘区）的数值贡献算出来，从"当前面板"里
        // 剔除，再做反解。relicAddPower/relicAddHp/relicAddSpeed 与下面的累加
        // **用同一套公式**，保证两边严格一致。
        if (capture) {
            float[] relicContribution = relicNumericContribution(p);
            captureGrowth(p, baseDamage, baseHp, baseSpeed,
                    relicContribution[0], relicContribution[1], relicContribution[2]);
        }

        float addPower = 0, addRate = 0, addArea = 0, addSpeed = 0, addHp = 0,
                addCrit = 0, addMagnet = 0, addRegen = 0, addXp = 0, addCoin = 0;
        int flags = 0, extraProj = 0, chain = 0, orbit = 0, mine = 0, split = 0,
                ricochet = 0, beam = 0, summon = 0, stun = 0;
        int multishot = roleBaseMultishot(p.role);
        int pierce = roleBasePierce(p.role);
        int slow = roleBaseSlow(p.role);
        int leech = roleBaseLeech(p.role);
        int explode = roleBaseExplode(p.role);
        int armorBreak = 0, thorns = 0, execute = 0, knockback = 0;
        float shieldMax = p.has(Content.F_SHIELD) ? p.shieldMax : 0;
        // 闪避冷却倍率：**每次 recompute 都必须从 1 重新算**。
        // 直接在 case 里改 p.dashFactor 会让它随调用次数指数衰减（永久无敌）。
        float dashFactorMul = 1f;

        // ── v2.0 支柱 2 #17 孤注一掷：遗物效果乘区 ────────────────────
        // 只放大 POWER/RATE/AREA/SPEEDVAL 四条数值列；FLAGS 与 switch 里的
        // 机制计数（层数、弹丸数等）不放大 —— 否则"×1.6"会变成"数值+机制双吃"。
        float relicMul = doctrineRelicEffectMul();
        for (int id = 0; id < Relics.COUNT; id++) {
            int stacks = p.relics[id];
            if (stacks <= 0) continue;
            int school = Relics.SCHOOL[id];
            float numerical = schoolMul(p, school) * relicMul;
            addPower += Relics.POWER[id] * stacks * numerical;
            addRate += Relics.RATE[id] * stacks * numerical;
            addArea += Relics.AREA[id] * stacks * numerical;
            addSpeed += Relics.SPEEDVAL[id] * stacks * numerical;
            flags |= Relics.FLAGS[id];
            switch (id) {
                case Relics.VITAL: addHp += 24 * stacks; break;
                case Relics.CRIT_LENS: addCrit += .10f * stacks; break;
                case Relics.MAGNET_CORE: addMagnet += 52 * stacks; addXp += .16f * stacks; break;
                case Relics.SPLIT_PRISM: extraProj += stacks; break;
                case Relics.PIERCE_NEEDLE: pierce += stacks; break;
                case Relics.EXPLOSIVE_SEED: explode = Math.max(explode, 1); break;
                case Relics.THUNDER_COIL: chain += stacks; break;
                case Relics.ORBIT_STONE: orbit += stacks; break;
                case Relics.MINE_KIT: mine += stacks; break;
                case Relics.SPLIT_SHARD: split += stacks; break;
                case Relics.RICOCHET_CHARM: ricochet += stacks; break;
                case Relics.BEAM_FOCUS: beam += stacks; break;
                case Relics.SUMMON_BELL: summon += stacks; break;
                case Relics.STUN_HAMMER: stun += stacks; break;
                case Relics.THORN_MAIL: thorns += 4 * stacks; break;
                case Relics.EXECUTIONER: execute += stacks; break;
                case Relics.WAR_DRUM: knockback += stacks; break;
                case Relics.LEECH_SEED: leech += stacks; break;
                case Relics.SLOW_FROST: slow = 1; break;
                case Relics.SHIELD_CORE: shieldMax = Math.max(shieldMax, 26 * stacks); break;
                case Relics.REGEN_HEART: addRegen += .8f * stacks; break;
                case Relics.GREED_COIN: addCoin += .12f * stacks; break;
                case Relics.BLOOD_PACT: addPower += .10f * stacks; addHp -= 14 * stacks; break;
                case Relics.MULTI_CHARM: multishot = Math.min(7, multishot + stacks); break;
                // ══════════════════════════════════════════════════════
                //  遗物去水 v2（world-data）：把 DESC 承诺的机制落到真正
                //  被战斗代码读取的字段上（只使用已验证有读取点的字段）。
                // ══════════════════════════════════════════════════════
                case Relics.ECHO_BOOTS:
                    // 12 回响靴：闪避冷却 -12%/层。
                    // **不要在这里改 p.dashFactor**——recompute 会被升级、捡遗物、读档
                    // 反复调用，对自身累乘等于每调用一次再乘 0.88^n：
                    // 25 次之后闪避 CD 只剩 0.115s，短于 0.55s 无敌帧 → **永久无敌**。
                    // 这里只登记到一个局部累加量，最后与基准值一起一次性算出。
                    dashFactorMul *= (float) Math.pow(.88, stacks);
                    break;
                case Relics.LIFE_SPORE:         // 31 生命孢子：击杀回血 1/层
                    leech += stacks; break;
                case Relics.MEDIC_LAMP:         // 32 星医之灯：每 5 秒回复 2/层
                    addRegen += .4f * stacks; break;
                case Relics.PRAYER_SEAL:        // 38 祈求之印：治疗与回复 +20%/层
                    addRegen += .6f * stacks; break;
                case Relics.STORM_EYE:          // 45 雷暴之眼：闪电命中眩晕
                    stun += stacks; break;
                case Relics.THUNDER_CROWN:      // 49 万雷之冠：连锁额外跳一次
                    chain += stacks; break;
                case Relics.STATIC_FIELD:       // 46 静电场：近身持续雷伤
                    thorns += 6 * stacks; break;
                case Relics.ENTROPY_HOURGLASS:  // 56 熵之沙漏：周期护盾（原为完全无效）
                    shieldMax = Math.max(shieldMax, 18 * stacks); break;
                case Relics.RIFT_BLADE:         // 54 裂隙之刃：近身压制
                    knockback += stacks; break;
                case Relics.FROST_MAIL:         // 24 霜纹甲：承伤减免
                    shieldMax = Math.max(shieldMax, 16 * stacks); break;
                case Relics.SNOW_BLIND:         // 27 雪盲面具：冻结目标必暴
                    addCrit += .04f * stacks; break;
                case Relics.FROST_CROWN:        // 21 霜花王冠：对减速目标易伤
                    pierce += stacks; break;
                case Relics.ARMOR_SHARD:        // 5 碎甲棱角：对重型易伤
                    execute += stacks; break;
                case Relics.ICE_PRISM:          // 25 冰棱棱镜：冰痕减速
                    slow = 1; break;
                case Relics.COLD_BREATH:        // 23 寒潮之息：概率冻结
                    stun += stacks; break;
                case Relics.ICE_BOTTLE:         // 22 冰封瓶：减速时长
                    slow = 1; break;
                case Relics.PERMAFROST:         // 26 永冻之石：掉冰晶
                    leech += stacks; break;
                default: break;
            }
        }

        // 进化
        int mechanic = 0, mechanicMask = 0;
        for (int i = 0; i < Evolutions.COUNT; i++) {
            if (!p.evolved[i]) continue;
            flags |= Evolutions.FLAGS[i];
            addPower += Evolutions.POWER[i];
            addRate += Evolutions.RATE[i];
            addArea += Evolutions.AREA[i];
            addSpeed += Evolutions.SPEEDVAL[i];
            // 多条进化时取第一个非 0 原型（优先级从低 id 到高 id）
            if (mechanic == 0) mechanic = Evolutions.mechanic(i);
            // v2.0 P0-6：位掩码记录**全部**原型，供 applyMechanic 分发
            mechanicMask |= 1 << Evolutions.mechanic(i);
        }
        p.evoMechanic = mechanic;
        p.mechanicMask = mechanicMask;
        // M3 / M13 的派生乘区在这里刷新，fire() 每发都读，保持与 live 状态一致。
        refreshMechanicDerived(p);

        // 流派共鸣（3 件不同遗物）
        p.synergyMask = 0;
        for (int school = 0; school < Content.SCHOOL_COUNT; school++)
            if (schoolCount(p, school) >= 3) p.synergyMask |= 1 << school;

        // ── v2.0 P0-3：跨流派组合（此前 crossMask 从未被赋值）──────────
        // 规则：同时持有 CROSS_A 与 CROSS_B 两个流派的遗物（各 ≥1 件）即激活该组合。
        computeCross(p);
        p.flags = flags;
        // ── v2.0：把此前"零消费"的标志位展开成可被战斗代码读取的计数 ────
        // 约定：层数 = 1 + flags 是否置位（保证至少 1 层），与遗物 id 分支叠加时取较大值。
        p.auraCount = (flags & Content.F_AURA) != 0 ? 1 : 0;
        p.slowFieldCount = (flags & Content.F_SLOWFIELD) != 0 ? 1 : 0;
        p.lifeSteal = (flags & Content.F_LIFESTEAL) != 0 ? 1 : 0;
        p.taunt = (flags & Content.F_TAUNT) != 0 ? 1 : 0;
        p.chargeCount = (flags & Content.F_CHARGE) != 0 ? 1 : 0;
        p.splitShot = (flags & Content.F_SPLITSHOT) != 0 ? 1 : 0;
        // F_TELEPORT / F_KNOCKBACK / F_EXECUTE 复用既有字段（knockback/execute），
        // 这里只保证"仅靠 FLAGS 声明"时也能生效。
        if ((flags & Content.F_KNOCKBACK) != 0 && knockback <= 0) knockback = 1;
        if ((flags & Content.F_EXECUTE) != 0 && execute <= 0) execute = 1;
        p.extraProjectiles = extraProj;
        p.chainCount = chain;
        p.orbitCount = orbit;
        p.mineCount = mine;
        p.splitCount = split;
        p.ricochetCount = ricochet;
        p.beamCount = beam;
        p.summonCount = summon;
        p.stunChance = stun;
        p.multishot = Math.max(1, Math.min(7, multishot + extraProj / 2));
        p.pierce = pierce;
        p.slow = slow;
        p.leech = leech;
        p.explode = explode;
        p.armorBreak = armorBreak;
        p.thorns = thorns;
        p.execute = execute;
        p.knockback = knockback;

        // ── 成长增量并入（v1.1 修复）──────────────────────────────
        // 遗物加数与成长加数并列相加：拿遗物、升级、存读档都不会再丢成长。
        float growthPower = p.bonusPower;
        float growthHp = p.bonusHp;
        float growthSpeed = p.bonusArea;
        float growthCrit = p.bonusCrit;

        // ── v2.0 支柱 2：天赋牌的乘区钩子 ──────────────────────────
        // 全部走"当前已装备的牌"现场读取，不缓存：读档/换牌后无需重建即可自洽。
        float docHpMul = doctrineMaxHpMul();
        float docDmgMul = doctrineDamageMul();
        float docAtkMul = doctrineAtkSpeedMul();

        p.maxHp = Math.max(40, (baseHp + addHp + growthHp + levelHpBonus()) * docHpMul);
        p.hp = Math.min(p.hp, p.maxHp);
        p.damage = baseDamage * Math.max(.25f, 1 + addPower + growthPower) * levelDamageMul()
                * docDmgMul
                // v2.0 P0-2：多弹丸惩罚（面板级）。每多一枚 -18% 单发，
                // DPS ≈ count/(1+(count-1)*.18)：2 发 ×1.69、3 发 ×2.20、5 发 ×2.91。
                / (1f + (Math.max(1, Math.min(7, multishot + extraProj / 2)) - 1) * .18f);
        // 攻速乘区（#13 过载运转 +45%）：interval 越小越快，因此除而不是乘。
        p.interval = Math.max(.075f, baseInterval / Math.max(.3f, 1 + addRate) / docAtkMul);
        p.speed = Math.min(360, baseSpeed * Math.max(.5f, 1 + addSpeed + growthSpeed));
        // #14 雷霆传导：暴击率归零（主钩子位）。放在面板上而不是只在 fire() 里判定，
        // 这样 UI 显示的暴击率与实战一致，玩家看得到代价。
        p.crit = doctrineCritZero() ? 0f
                : Math.min(.75f, roleBaseCrit(p.role) + addCrit + growthCrit);
        p.magnet = (baseMagnet() + addMagnet) * (hasPact(Balance.PACT_DARKNESS) ? Balance.PACT_DARKNESS_MAGNET : 1f);
        // 闪避冷却：基准 1.0 × 遗物乘区 × 外部修正（元进度 dir / 天赋牌）。
        // 下限 0.25 是安全阀：再低就会短于 0.55s 的无敌帧，等于常驻无敌。
        p.dashFactor = Math.max(.25f, Math.min(1f, dashFactorMul * p.dashGrow));
        // ── v2.2：这两个字段此前被**无条件覆盖**，把元进度节点整个抹掉 ──
        // recpute 里原来直接 `p.xpBonus = addXp` / `p.bonusCoin = addCoin`，
        // 而 Session 已经把元进度写进了这两个裸字段（+6%/级 经验、+8%/级 星尘），
        // 于是第一次 recompute 就把它们归零。captureGrowth 只保 damage/maxHp/
        // speed/crit 四项，管不到这两个。
        // 现在改为「遗物部分 + 外部修正」，两边都保留。
        p.xpBonus = addXp + p.metaXp;
        p.bonusCoin = addCoin + p.metaCoin;
        p.bonusRegen = addRegen;
        // ── v2.0 P0-4：AREA 不再是死属性 ──────────────────────────
        // addArea 此前只累加、从不读取。现在落成 areaBonus，被光环/领域/地雷/爆炸半径消费。
        p.areaBonus = Math.max(0f, Math.min(2f, addArea));
        // ── v2.0 支柱 2 #7 寒冰壁垒：每层开始给的护盾要能挺过 recompute ──
        // 不能把它塞进上面的 shieldMax 分支：那条会把盾补满，而这张牌的代价
        // 恰恰是"护盾不再自动恢复"。这里只刷新上限，当前值只减不增。
        float docShield = 0f;
        if (mainHook(DoctrineHooks.H_FLOOR_SHIELD) || sideHook(DoctrineHooks.H_FLOOR_SHIELD)) {
            float ratio = mainHook(DoctrineHooks.H_FLOOR_SHIELD) ? dParam(0) : dParam(2);
            if (ratio > 0f) docShield = p.maxHp * ratio;
        }
        if (shieldMax > 0) {
            p.shieldMax = shieldMax;
            p.shield = Math.min(shieldMax, Math.max(p.shield, shieldMax));
        } else if (docShield > 0f) {
            p.shieldMax = Math.max(p.shieldMax, docShield);
            p.shield = Math.min(p.shieldMax, Math.max(0f, p.shield));
        } else if (!p.has(Content.F_SHIELD)) {
            p.shield = 0; p.shieldMax = 0;
        }
        // 共鸣加成
        if ((p.synergyMask & (1 << 1)) != 0) p.damage *= 1.12f;   // 疾风
        if ((p.synergyMask & (1 << 3)) != 0) { p.maxHp *= 1.10f; p.hp = Math.min(p.hp, p.maxHp); }
        // 面板从这里起已含天赋牌乘区：下次 captureGrowth 必须把它剔除，
        // 否则 #4/#11/#16 的乘区会被反复捕获成成长。
        doctrinePanelApplied = true;
    }

    private float schoolMul(Player p, int school) {
        // 元进度专精加成通过 Session 注入到 Game 的这组字段
        return 1f + schoolBonus[school];
    }

    /** 元进度的流派专精加成，由 Session 在开局前写入（0..0.5）。 */
    public final float[] schoolBonus = new float[Content.SCHOOL_COUNT];

    public int schoolCount(Player p, int school) {
        int n = 0;
        for (int i = 0; i < Relics.COUNT; i++)
            if (Relics.SCHOOL[i] == school && p.relics[i] > 0) n++;
        return n;
    }

    public int relicCount(Player p) {
        int n = 0;
        for (int r : p.relics) n += r;
        return n;
    }

    /** 重掷去重不可行时（池子太小）对上一手的权重惩罚（v2.3 #2）。 */
    private static final float RELIC_AVOID_DISCOUNT = .35f;
    /** 去重时"够凑一手"的最小候选数；低于它就不硬排除（v2.3 #2）。 */
    private static final int RELIC_AVOID_MIN_POOL = 3;
    /** 重掷避让模式：不避让 / 硬排除 / 只压权重。 */
    private static final int AVOID_NONE = 0, AVOID_EXCLUDE = 1, AVOID_DISCOUNT = 2;
    /** 流派自适应权重的步长：每拥有 1 件同流派遗物 +30%，最多 +90%（v2.3 #1）。 */
    private static final float RELIC_SCHOOL_BIAS_STEP = .30f;

    /**
     * 流派自适应权重（v2.3 #1）。
     *
     * <p><b>为什么这样加权</b>（依据 {@code docs/research-best-in-class-2026-10b.md} §2.1）：
     * <ul>
     *   <li><b>Brotato 的同类偏置</b>：它的商店有固定概率从"你已持有物品的 tag"里选，
     *       于是"手里有什么"会自然地吸引同类——玩家不是被随机数牵着走，而是在已有方向上加速。
     *       本项目的共鸣要求同流派 3 件不同遗物，纯均匀抽取时凑齐完全靠运气，
     *       这条偏置把"看运气"变成"可以追"。</li>
     *   <li><b>Death Must Die 的结论</b>：让玩家在战斗后自己选神，能<b>大幅减少重掷需求</b>——
     *       抽取本身贴合当前流派，玩家才不需要靠付费重掷去凑共鸣。
     *       所以这里选择"温和加权"而不是"硬性只出同流派"：既保住稀有度的惊喜感，
     *       又不让重掷成为凑共鸣的唯一手段。</li>
     * </ul>
     *
     * <p>公式：{@code bias = 1 + 0.30 × min(3, schoolCount)}，即 0/1/2/3+ 件时为 ×1.0 / ×1.3 / ×1.6 / ×1.9。
     * 偏置只改权重、不改候选表：不会屏蔽任何遗物，不消耗随机数，
     * 因此同 seed 的确定性不受影响。
     */
    private float schoolBias(Player p, int relicId) {
        if (p == null || relicId < 0 || relicId >= Relics.COUNT) return 1f;
        int school = Relics.SCHOOL[relicId];
        if (school < 0 || school >= Content.SCHOOL_COUNT) return 1f;
        return 1f + RELIC_SCHOOL_BIAS_STEP * Math.min(3, schoolCount(p, school));
    }

    /**
     * 随机一件玩家还能再拿的遗物。
     * <p>#3 余烬开局：{@link #lockedSchool()} 锁定的流派本局不再出现。
     * <p>#17 孤注一掷：遗物栏满时不再给新种类（否则上限形同虚设），
     * 改为只给"已持有且未满层"的种类；全都满层才走兜底。
     */
    /**
     * 抽一件遗物。
     *
     * <p><b>v2.2：接上 {@link ClassRules}</b>——这个类有 354 行、12 个职业各自的
     * 硬门槛与软偏好，还自带"每职业至少 5 件被挡、至少 25 件合法"的设计纪律，
     * 但此前**全项目零调用**：十二个职业看到的是同一个遗物池，
     * 于是十二次远征的构筑长得一模一样，最优解永远是"拿最强的"。
     *
     * <p>接入方式（两段式，保证永远不会抽空）：
     * <ol>
     *   <li>前 48 次尝试用 {@link ClassRules#weightMul} 加权抽样——
     *       让"顺路"的遗物更常出现，但被职业排斥的并非绝对不出现；</li>
     *   <li>若一直抽不到合法的，退化成 {@link ClassRules#allows} 硬过滤的均匀抽样。</li>
     * </ol>
     * 加权抽样的随机数消耗是**固定 2 次**（一次取表、一次取权重），
     * 因此同 seed 的确定性不受候选数量影响。
     */
    private int rollRelic(Player p) {
        return rollRelic(p, null);
    }

    /**
     * 抽一件遗物，并可选地避开一组 id。
     *
     * @param avoid 需要避开的遗物 id（重掷时传入上一手）；null / 全 -1 = 不避开。
     *              是否硬排除由池子大小决定，见下面的注释。
     */
    private int rollRelic(Player p, int[] avoid) {
        int locked = lockedSchool();
        int slots = doctrineRelicSlots();
        boolean full = distinctRelics(p) >= slots;
        int role = p == null ? -1 : p.role;

        // ── v2.3 #2：重掷不得原样返还同一手 ────────────────────────────
        // 先数一遍"把上一手排除掉之后还剩多少合法候选"（纯计数，不消耗 RNG）：
        //   够凑一手（≥3）→ 硬排除，新手牌与旧手牌完全不相交；
        //   不够（后期大量满层 / 职业规则收窄）→ 不排除，只把上一手权重压到 ×0.35。
        // 这样既兑现"重掷至少换牌"，又永远不会因为去重把三选一抽空。
        int avoidMode = AVOID_NONE;
        if (avoid != null && avoid.length > 0) {
            int after = 0;
            for (int i = 0; i < Relics.COUNT; i++) {
                if (!relicEligible(i, p, role, locked, full, true)) continue;
                if (containsRelic(avoid, i)) continue;
                after++;
            }
            avoidMode = after >= RELIC_AVOID_MIN_POOL ? AVOID_EXCLUDE : AVOID_DISCOUNT;
        }

        // 候选表：先过"流派被锁 / 层数满 / 遗物栏满"的硬条件，
        // 再过职业的**硬门槛** {@link ClassRules#allows}。
        // 注意：只做加权、不做硬过滤是不够的——影舞（CAP<4）与霜灵（流派排除）
        // 这两条规则挡掉的比例本来就高，若禁品仍能以 0.2x 权重出现，
        // 实测违规率会与完全不接线时**持平**（35% vs 30%），规则等于没写。
        relicPool.clear();
        relicWeight.clear();
        float total = 0f;
        for (int pass = 0; pass < 2 && relicPool.isEmpty(); pass++) {
            for (int i = 0; i < Relics.COUNT; i++) {
                // pass 0：严格执行职业规则；pass 1：只在抽空时放宽（宁可给错也不给空）
                if (!relicEligible(i, p, role, locked, full, pass == 0)) continue;
                boolean avoided = avoidMode != AVOID_NONE && containsRelic(avoid, i);
                if (avoided && avoidMode == AVOID_EXCLUDE) continue;
                float w = pass == 1 ? 1f : ClassRules.weightMul(role, i);
                if (!(w > 0f) || !Float.isFinite(w)) w = 1f;
                // v2.3 #1：流派自适应偏置（设计依据见 schoolBias 的注释）
                w *= schoolBias(p, i);
                // v2.3 #2：池子不够大时，上一手仍在表里，但只剩 35% 的出现概率
                if (avoided) w *= RELIC_AVOID_DISCOUNT;
                relicPool.add(i);
                relicWeight.add(w);
                total += w;
            }
        }
        if (!relicPool.isEmpty() && total > 0f) {
            float roll = random.nextFloat() * total;
            for (int k = 0; k < relicPool.size(); k++) {
                roll -= relicWeight.get(k);
                if (roll <= 0f) return relicPool.get(k);
            }
            return relicPool.get(relicPool.size() - 1);
        }
        // 兜底一：绕过"遗物栏满"再抽一次。
        for (int i = 0; i < Relics.COUNT; i++) {
            if (locked >= 0 && Relics.SCHOOL[i] == locked) continue;
            if (p != null && p.relics[i] >= Relics.CAP[i]) continue;
            if (ClassRules.allows(role, i)) return i;
        }
        // 兜底二：全表被锁时返回 1（与旧行为一致，不返回 -1，
        // 否则调用方 grantRelic(-1) 会静默丢掉整次奖励）。
        return 1;
    }

    /**
     * 候选资格判定（v2.3：从 rollRelic 的两段式循环里抽出来，
     * 让"去重前先数池子"与真正的候选表使用同一套条件，避免两处规则漂移）。
     *
     * @param strict true = pass 0（严格执行职业规则）；false = pass 1（放宽职业规则）
     */
    private boolean relicEligible(int i, Player p, int role, int locked, boolean full, boolean strict) {
        if (locked >= 0 && Relics.SCHOOL[i] == locked) return false;
        if (p != null && p.relics[i] >= Relics.CAP[i]) return false;
        if (full && p != null && p.relics[i] <= 0) return false;
        if (strict && !ClassRules.allows(role, i)) return false;
        return true;
    }

    private static boolean containsRelic(int[] ids, int id) {
        for (int v : ids) if (v == id) return true;
        return false;
    }

    /** rollRelic 的候选缓冲（复用，避免每件遗物都新建两个 List）。 */
    private final java.util.ArrayList<Integer> relicPool = new java.util.ArrayList<>(Relics.COUNT);
    private final java.util.ArrayList<Float> relicWeight = new java.util.ArrayList<>(Relics.COUNT);

    /**
     * 供探针 / 测试观察"这位玩家实际会抽到什么"的只读入口。
     *
     * <p>刻意<b>不</b>加 {@code @VisibleForTesting} 之类的注解——{@code core}
     * 不依赖注解库。命名带 {@code ForProbe} 表明它不是玩法 API，
     * 但也不做权限收紧，因为探针本身就在同一个包里运行。
     */
    public int rollRelicForProbe(Player p) {
        return p == null ? -1 : rollRelic(p);
    }

    private void offer(Player p) {
        offer(p, false);
    }

    /**
     * 发一手三张牌。
     *
     * <p><b>v2.3 #2 抽取去重 + 近期记忆</b>：{@code reroll == true} 时把
     * {@link Player#lastOffer}（玩家刚刚看过的那三张）作为避让集合传给
     * {@link #rollRelic(Player, int[])}，让重掷至少换掉牌面。依据
     * {@code docs/research-best-in-class-2026-10b.md} §2.2：
     * VS 的 Arcana 重掷明确写了"previous options will not be offered again"，
     * DMD 2026 则把"bad RNG protection"直接做成卖点——玩家花了 28 星尘却拿回同一手，
     * 是重掷系统最廉价的信任损失。
     *
     * <p>避让集合只是一次性的（用完即弃，覆盖在 {@link Player#lastOffer}），
     * 不影响后续正常抽取；池子太小会自动退化成 ×0.35（见 rollRelic）。
     * {@link Player#lastOffer} 是 transient、不进 Codec，因此存档格式与 VERSION 不变。
     */
    private void offer(Player p, boolean reroll) {
        int[] avoid = null;
        if (reroll) {
            // 避让"上一次真正发给玩家的那一手"（Player.lastOffer），而不是 p.offers：
            // 玩家可能先点了进化按钮（offers 被清成 -1）再重掷，此时 p.offers 已经
            // 不是他刚刚看过的牌面了。lastOffer 在每次 offer 末尾更新，才是那份记忆。
            avoid = new int[3];
            System.arraycopy(p.lastOffer, 0, avoid, 0, 3);
        }
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            int id, tries = 0;
            do { id = rollRelic(p, avoid); } while (used.contains(id) && ++tries < 120);
            used.add(id);
            p.offers[i] = id;
            p.evolveOffer[i] = -1;
        }
        // 记住"这一次给出的手牌"，供下一次重掷避让（transient，不入存档）。
        System.arraycopy(p.offers, 0, p.lastOffer, 0, 3);
        p.chosen = -1;
    }

    private void prepareChoice(int rewards) {
        phase = CHOICE;
        choiceClock = soloRun ? 0 : 50;
        rewardsLeft = rewards;
        // ── v2.0 支柱 2 #4 献祭契约：把升级累积的额外次数一次性补发 ────
        if (doctrineBonusChoices > 0) {
            rewardsLeft += doctrineBonusChoices;
            doctrineBonusChoices = 0;
        }
        // #12 雷光开局：整局重掷次数归零（不只是开局那一瞬）。
        rerolls = (mainHook(DoctrineHooks.H_REROLL_ZERO) || sideHook(DoctrineHooks.H_REROLL_ZERO)) ? 0 : 1;
        shots.clear();
        turrets.clear();
        minions.clear();
        for (Player p : players) { p.dx = p.dy = 0; offer(p); }
        notice = "三选一 · 同流派 3 件不同遗物解锁共鸣";
    }

    /**
     * #7 寒冰壁垒：每层开始给护盾（最大生命的 param[0]）。
     *
     * <p>在 {@link #selectRoute(int)} 里调用（每次进入新一层都算），
     * 不放在 {@code beginCombat} —— 事件/商店层也应该"开局给盾"，
     * 否则牌面写的"每层开始"会变成"每场战斗开始"。
     */
    private void floorShield() {
        if (!(mainHook(DoctrineHooks.H_FLOOR_SHIELD) || sideHook(DoctrineHooks.H_FLOOR_SHIELD))) return;
        float ratio = mainHook(DoctrineHooks.H_FLOOR_SHIELD) ? dParam(0) : dParam(2);
        if (!(ratio > 0f)) return;
        for (Player p : players) {
            float add = p.maxHp * ratio;
            // 取较大值而不是相加：避免"先吃遗物盾再进层"叠成双层盾。
            p.shieldMax = Math.max(p.shieldMax, add);
            p.shield = Math.max(p.shield, p.shieldMax);
        }
    }

    private void advance() {
        roomsVisited++;
        room = roomsVisited;
        // 事件挂账（TYPE 4 / 8 / 9）在这里递减并到期兑现：
        // 挂账写的是"3 站后"，而 advance() 每调用一次就推进一站，
        // 因此这是唯一与"站"一一对应的推进点（三个分支都在它之后）。
        settleDelayedPenalty();
        if (mode != MODE_ENDLESS && roomsVisited >= roomsTotal) {
            phase = WON;
            notice = "远征完成 · 星光已被带回";
            return;
        }
        if (mode == MODE_BOSS_RUSH) {
            if (roomsVisited >= roomsTotal) { phase = WON; notice = "首领连战完成"; return; }
            campAfterBoss = true;
            phase = CAMP;
            sceneTitle = "首领间歇 · 营火";
            return;
        }
        generateRoute();
        phase = MAP;
    }

    private void clearEntities() {
        enemies.clear(); pendingEnemies.clear(); shots.clear(); gems.clear();
        effects.clear(); blocks.clear(); props.clear(); hazards.clear();
        turrets.clear(); minions.clear();
    }

    private void resetPositions() {
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.x = 470 + i * 60;
            p.y = 475;
            p.dx = p.dy = 0;
            p.chosen = -1;
            p.revive = 0;
            if (p.down) { p.hp = p.maxHp * .4f; p.down = false; }
            p.invuln = 1;
            p.attack = 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  主循环
    // ══════════════════════════════════════════════════════════════

    public void update(float dt) {
        if (!Float.isFinite(dt) || dt <= 0 || dt > .1f) return;
        tick++;
        updateEffects(dt);
        for (Player p : players) {
            if (!p.connected) { p.disconnectedFor += dt; p.dx = p.dy = 0; }
            else p.disconnectedFor = 0;
        }
        if (phase == CHOICE) { updateChoice(dt); return; }
        if (phase != FIGHT) return;
        boolean connected = false;
        for (Player p : players) if (p.connected) connected = true;
        if (!connected) return;
        time += dt;
        roomTime += dt;
        spawnClock -= dt;
        comboClock -= dt;
        if (comboClock <= 0) combo = 0;
        for (Player p : players) updatePlayer(p, dt);
        updateTurrets(dt);
        updateMinions(dt);
        updateHazards(dt);
        updateProps();
        spawnLogic(dt);
        updateEnemies(dt);
        updateShots(dt);
        flushDeaths();
        updateGems(dt);
        revives(dt);
        boolean alive = false;
        for (Player p : players) if (p.connected && !p.down) alive = true;
        if (!alive) { phase = LOST; notice = "小队失守 · 这次远征已结束"; return; }
        updatePactTimeLimit(dt);
        updateStall(dt);
        checkObjective();
    }

    // ── v2.0 词条「时限压榨」──────────────────────────────────────
    //
    //  此前 PACT_LIMIT_SECONDS / PACT_TIME_DPS / PACT_TIME_REINFORCE 三个参数
    //  声明了却零消费。这里接上真正的"倒计时压力"：前 70 秒是黄金输出窗口，
    //  之后**规则变了**——持续掉血 + 无限追兵，玩家必须主动结束战斗，
    //  否则会被时间本身杀死。这不是乘一个数值，而是把房间从"清怪"改成"限时攻坚"。
    //
    //  只作用于战斗类房间（战斗 / 精英 / 宝库 / 挑战），首领房不受影响：
    //  首领战已经有阶段压力，再叠加烧血会变成"必输的消耗战"。
    private void updatePactTimeLimit(float dt) {
        if (!hasPact(Balance.PACT_TIME_LIMIT)) return;
        if (roomType == ROOM_BOSS) return;
        float limit = Balance.PACT_LIMIT_SECONDS;
        if (!(roomTime > limit)) return;
        // ── 1. 持续掉血 ───────────────────────────────────────────────
        //     不能每帧调 hurt()：hurt 有 0.5s 无敌帧，5 dps 会被吞成"每 0.5 秒扣一次"，
        //     而且会顺手刷掉玩家躲弹幕用的无敌帧（等于给词条附赠免伤）。
        //     这里按"每秒结算一次"的节拍扣血，走独立路径：
        //     护盾先吸收 → 剩余按契约承伤倍率扣除 → 与 hurt 同样的伤害统计与死亡判定。
        //     治疗禁绝的 2% 吸血同样补上，保证两个词条叠加时语义一致。
        pactBurnClock -= dt;
        if (pactBurnClock <= 0f) {
            pactBurnClock = 1f;
            float burn = Balance.PACT_TIME_DPS;
            for (Player p : players) {
                if (!p.connected || p.down || p.hp <= 0f) continue;
                float amount = burn * Balance.DAMAGE[contract] * Balance.pactDamageMul(this);
                if (p.shield > 0f) {
                    float absorbed = Math.min(p.shield, amount);
                    p.shield -= absorbed;
                    amount -= absorbed;
                }
                if (amount <= 0f) continue;
                float taken = Math.min(p.hp, amount);
                p.hp -= taken;
                if (hasPact(Balance.PACT_NO_HEAL)) {
                    int siphon = Math.round(taken * Balance.PACT_NO_HEAL_LEECH);
                    if (siphon > 0) p.hp = Math.min(p.maxHp, p.hp + siphon);
                }
                damageTaken += taken;
                effect(p.x, p.y, 34, 2, 0xffe07a4a, .35f);
                if (p.hp <= 0f) {
                    p.down = true;
                    p.dx = p.dy = 0;
                    p.revive = 0;
                    notice = soloRun
                            ? (freeRevivesUsed < freeReviveCount ? "你倒下了 · 星火会把你拉回来" : "你倒下了")
                            : "队友倒下 · 靠近 3 秒可以救援";
                }
            }
        }
        if (!pactLimitAnnounced) {
            pactLimitAnnounced = true;
            notice = "时限压榨 · " + Math.round(limit) + " 秒已到，深渊开始灼烧";
            effect(560, 300, 320, 5, 0xffe07a4a, 1f);
        }
        // ── 2. 追加增援：每 PACT_TIME_REINFORCE 秒追加一只，不占房间配额 ──
        //     直接 spawned++ 会让 checkObjective 的"配额刷完"判定提前成立，
        //     所以只往场上放敌人，不动 spawned/target。
        pactReinforceClock -= dt;
        if (pactReinforceClock <= 0f) {
            pactReinforceClock = Balance.PACT_TIME_REINFORCE;
            if (enemies.size() + pendingEnemies.size() < MAX_ENEMIES) {
                spawn();
                effect(560, 120, 90, 6, 0xffe07a4a, .7f);
            }
        }
    }

    private void updateChoice(float dt) {
        if (!soloRun) {
            choiceClock -= dt;
            for (Player p : players)
                if (p.chosen < 0 && (!p.connected || choiceClock <= 0)) choose(p.id, 0, 0);
        }
        boolean done = true;
        for (Player p : players) if (p.chosen < 0) done = false;
        if (done) {
            if (--rewardsLeft > 0) { for (Player p : players) offer(p); }
            else advance();
        }
    }

    /**
     * 房间僵持兜底。
     * <p>当配额已刷完、场上还有残余但长时间没有击杀时：先让所有敌人进入"狂暴追击"，
     * 再持续更久就强制清房（奖励降低）。这保证玩家不会因为一个卡在墙后的炮台而永远打不完。
     */
    private void updateStall(float dt) {
        if (roomType == ROOM_BOSS) return;
        if (spawned < target) { stallClock = 0; return; }
        if (roomKills() != lastStallKills) {
            lastStallKills = roomKills();
            stallClock = 0;
            return;
        }
        stallClock += dt;
        // 12 秒无击杀：残余敌人狂暴，炮台开始移动
        if (stallClock >= 12 && !enraged) {
            enraged = true;
            for (Enemy e : enemies) {
                if (e.speed <= 0) e.speed = 60;
                e.speed *= 1.25f;
            }
            for (Hazard h : hazards) if (h.active > 0) h.active = 0;
            notice = "残余守卫开始狂暴 · 它们会主动扑上来";
        }
        // 35 秒无击杀：强制清房（奖励衰减）
        if (stallClock >= 35 && enemies.isEmpty() && pendingEnemies.isEmpty()) {
            clearRoom();
        } else if (stallClock >= 35 && !enemies.isEmpty()) {
            for (Enemy e : enemies) e.hp = Math.min(e.hp, 1);
            for (Enemy e : enemies) damage(e, 1e6f, players.isEmpty() ? -1 : players.get(0).id);
            flushDeaths();
            if (enemies.isEmpty()) clearRoom();
        }
    }

    int lastStallKills;
    /**
     * 僵持狂暴是否已触发。需要进存档（Codec v9）：
     * 否则读档后归零，玩家可以靠"存档—读档"重置狂暴状态，
     * 把 35 秒僵持的加压机制变成无限续命。
     */
    boolean enraged;

    private void checkObjective() {
        if (roomType == ROOM_CHALLENGE) {
            if (objective == 1) {
                boolean contested = false;
                for (Enemy e : enemies) if (e.hp > 0 && distance(e.x, e.y, 560, 300) < 118) contested = true;
                if (!contested) for (Player p : players)
                    if (p.connected && !p.down && distance(p.x, p.y, 560, 300) < 165) {
                        charge += enemies.isEmpty() ? Game.STEP * 2 : Game.STEP;
                        break;
                    }
                // 守环目标按"充能秒数"计，与房间限时无关；#8 只削限时类目标。
                if (charge >= 26) clearRoom();
            } else if (objective == 2) {
                charge = roomTime;
                if (roomTime >= challengeTime(40f)) clearRoom();
            } else {
                charge = roomKills();
                if (charge >= 18) clearRoom();
                else if (roomTime >= challengeTime(45f)) { coins += 16; prepareChoice(1); notice = "狩猎超时 · 星尘奖励降为 16"; }
            }
        } else if (spawned >= target && enemies.isEmpty() && pendingEnemies.isEmpty()) {
            clearRoom();
        }
    }

    private void spawnLogic(float dt) {
        if (spawned >= target || spawnClock > 0
                || enemies.size() + pendingEnemies.size() >= MAX_ENEMIES) return;
        if (roomType == ROOM_BOSS) {
            spawnBoss();
            spawned++;
            return;
        }
        if (roomType == ROOM_CHALLENGE) {
            int count = roomTime < 16 ? 2 : 3;
            for (int j = 0; j < count && spawned < target; j++) { spawn(); spawned++; }
            spawnClock = 2.5f / Balance.PACE[contract];
            return;
        }
        if (spawned >= nextWaveAt) {
            if (enemies.size() <= 2 || waveWait >= 6) {
                wave++;
                nextWaveAt = Math.min(target, (int) Math.ceil(target * wave / (float) waves));
                waveWait = 0;
            } else {
                waveWait += .25f;
                spawnClock = .25f;
            }
        } else {
            int count = Math.min(4, nextWaveAt - spawned);
            for (int j = 0; j < count; j++) { spawn(); spawned++; }
            spawnClock = .22f;
        }
    }

    private void updateEffects(float dt) {
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            e.life -= dt;
            if (e.life <= 0) it.remove();
        }
    }

    private float power(Player p) {
        float mul = 1;
        if (p.buff > 0 && p.buffKind == 1) mul += .35f;
        if (p.buff > 0 && p.buffKind == 2) mul += .30f;
        int lowHp = lowHpStacks(p);
        if (lowHp > 0) mul += (1 - p.hp / Math.max(1, p.maxHp)) * .55f * lowHp;
        int scale = scaleStacks(p);
        if (scale > 0) mul += Math.min(.40f, scale * .018f * relicCount(p));
        if ((p.synergyMask & (1 << 1)) != 0) mul += .06f;
        // v2.1 跨流派组合 2「风火」与组合 4「虚步」——这两个派生量此前只赋值、从不读取。
        mul += p.crossSpeedPower;
        if (p.crossRush > 0f) mul += .20f;
        return p.damage * mul;
    }

    private int lowHpStacks(Player p) { return p.relics[Relics.LOW_HP_RAGE]; }
    private int scaleStacks(Player p) { return p.relics[Relics.SCALE_COIL]; }

    /**
     * 终极充能入口。
     * <p>v2.0 P0-6 M4 技能改写（进化 35 深渊回响）："终极冷却减半"落在<b>充能速度 ×2</b>上
     * ——这是唯一真实存在的"冷却"（{@code ultCd} 在 ultimate() 里被直接清零，没有消费点）。
     * 改充能速度而不是改阈值，玩家看到的仍是"能量条满得更快"。
     */
    /**
     * v2.0 终极充能速度倍率。
     *
     * <p>当前无任何来源修改它（天赋牌 #13「过载运转」等钩子待接），保持 1.0。
     * 接线后只改本方法体，调用点无需变动。
     *
     * <p>注意与 {@code gainUlt} 的分工：{@code gainUlt} 处理<b>进化 35 深渊回响</b>
     * 的 ×2（那是已验证的机制），本方法处理<b>天赋/遗物</b>层面的全局充能倍率。
     */
    // ultChargeMul 占位
    private float ultChargeMul(Player p) {
        return 1f;
    }

    private int gainUlt(Player p, int amount) {
        int n = amount;
        if (p != null && p.evolved[35] && Evolutions.mechanic(35) == 4) n = Math.round(n * 2f);
        return Math.max(0, n);
    }

    private void updatePlayer(Player p, float dt) {
        p.attack -= dt;
        p.dashCd = Math.max(0, p.dashCd - dt);
        p.skillCd = Math.max(0, p.skillCd - dt);
        p.invuln = Math.max(0, p.invuln - dt);
        p.buff = Math.max(0, p.buff - dt);
        p.frostHealCd = Math.max(0, p.frostHealCd - dt);
        p.reactionCd = Math.max(0, p.reactionCd - dt);
        // v2.1 跨流派组合 4「虚步」的闪避后增伤窗口
        p.crossRush = Math.max(0f, p.crossRush - dt);
        // v2.0 支柱 2 #2 疾影步：闪避后的攻速窗口
        p.dashHaste = Math.max(0f, p.dashHaste - dt);
        p.leechBudget = Math.min(6, p.leechBudget + dt * .5f);
        if (p.ultReady < 1) {
            p.ultCharge = Math.min(100, p.ultCharge);
            p.ultReady = p.ultCharge >= 100 ? 1 : 0;
        }
        // 回复
        if (p.bonusRegen > 0) { p.regenClock += dt; if (p.regenClock >= 1) { p.regenClock = 0; doctrineHeal(p, p.bonusRegen); } }
        // ── v2.0 支柱 2 #7 寒冰壁垒：护盾不再自动恢复 ────────────────
        // 只在装备了这张牌时停掉自动回盾；否则保持原有行为（遗物/进化给的盾照常回复）。
        boolean shieldNoRegen = sideHook(DoctrineHooks.H_SHIELD_NO_REGEN)
                || mainHook(DoctrineHooks.H_SHIELD_NO_REGEN);
        if (!shieldNoRegen && p.shieldMax > 0 && p.shield < p.shieldMax)
            p.shield = Math.min(p.shieldMax, p.shield + p.shieldMax * .06f * dt);
        if (p.down || !p.connected) return;
        // ── v2.1 修复：机制钩子 H_TICK 此前从未被调用 ──────────────────
        // 这是最致命的一个缺失：M2 连击计时、M3 低血动态、M5 场地生成、
        // M6 增益扩散、M13 击杀堆叠的衰减全都在 H_TICK 里。没有它，
        // 30 条带机制的进化里有 20+ 条的世界观是"永远不会发生"。
        // 放在 updatePactFlags 之前：让机制先刷新派生量，本帧的开火就能用上。
        applyMechanic(p, H_TICK, null, null, 0f, dt);
        // v2.0：F_AURA / F_SLOWFIELD / F_MINE / F_CHARGE / F_REGEN 的周期效果
        updatePactFlags(p, dt);
        float moveSpeed = p.speed;
        for (Hazard h : hazards)
            if (h.kind == 1 && h.active > 0 && distance(p.x, p.y, h.x, h.y) < h.r) moveSpeed *= .72f;
        move(p, p.dx * moveSpeed * dt, p.dy * moveSpeed * dt);
        Enemy enemy = nearestEnemy(p.x, p.y, true);
        if (enemy == null && p.manual < .5f) enemy = nearestEnemy(p.x, p.y, false);
        if (p.manual < .5f && enemy != null) {
            float d = Math.max(1, distance(p.x, p.y, enemy.x, enemy.y));
            p.ax = (enemy.x - p.x) / d;
            p.ay = (enemy.y - p.y) / d;
        }
        if (enemy == null && p.manual < .5f)
            for (Prop prop : props)
                if (prop.type == 0 && !prop.opened && distance(p.x, p.y, prop.x, prop.y) < 220
                        && lineClear(p.x, p.y, prop.x, prop.y)) {
                    float d = Math.max(1, distance(p.x, p.y, prop.x, prop.y));
                    p.ax = (prop.x - p.x) / d;
                    p.ay = (prop.y - p.y) / d;
                    break;
                }
        // ── v2.0 支柱 2 #13 过载运转：每 param 秒强制过热停顿 0.7s ─────
        // 判定放在开火之前：过热期间连 extras（轨道/地雷/召唤）一起停，
        // 否则"停顿"只停了主弹道，代价会被机制类遗物稀释。
        if (mainHook(DoctrineHooks.H_OVERHEAT)) {
            if (p.overheatStun > 0f) {
                p.overheatStun = Math.max(0f, p.overheatStun - dt);
                return;                     // 停顿期间不开火、不发 extras
            }
            p.overheatClock += dt;
            float period = Math.max(1f, dParam(0));
            if (p.overheatClock >= period) {
                p.overheatClock = 0f;
                p.overheatStun = OVERHEAT_STUN;
                effect(p.x, p.y, 40, 1, 0xffff9a5c, .35f);
                return;
            }
        }
        if (p.attack <= 0 && (enemy != null || p.manual > .5f || nearCrate(p))) {
            // v2.1：机制钩子 H_FIRE（M3 低血动态在这里重算弹丸/攻速派生量）
            applyMechanic(p, H_FIRE, null, null, 0f, 0f);
            fire(p, p.multishot, 0);
            p.shotCount++;
            // 进化特殊效果
            if (extras(p)) { /* 已由 extras 发射 */ }
            // #2 疾影步：闪避后 1.5s 内攻速 +60%；#27 攻速乘区（#13 的代价）已落在 interval 上。
            float haste = p.dashHaste > 0f ? 1f + Math.max(0f, p.dashHasteMul) : 1f;
            // ── v2.1 修复：攻击间隔此前**完全不读 attackSpeedMul** ───────────
            // refreshMechanicDerived() 一直在算 M2 连击加攻速（最多 +32%）
            // 与 M13 击杀堆叠加攻速（每层 +3%），写进 p.attackSpeedMul 之后
            // 全项目没有任何读取点——两条机制都是纯空气。
            // 现在把它作为独立乘区接在攻击间隔上（与其他乘区相乘，不覆盖）。
            p.attack = p.interval * (p.role == 6 && p.buff > 0 ? .78f : 1)
                    * (p.weaponForm == 1 ? .88f : p.weaponForm == 2 ? 1.25f : 1)
                    / haste / Math.max(.5f, Math.min(2f, p.attackSpeedMul))
                    / (1f + Math.max(0f, Math.min(.15f, p.metaAtkSpeed)));
            p.nextCrit = false;
            p.ultCharge = Math.min(100, p.ultCharge + gainUlt(p, 1));
        }
    }

    /** #13 过载运转：每次过热的强制停顿时长（秒）。 */
    static final float OVERHEAT_STUN = .7f;

    /** 击杀钩子的递归深度护栏（连锁闪电再次击杀会再次进入本方法）。 */
    private int doctrineKillDepth;

    /**
     * 击杀钩子：#14 雷霆传导（连锁闪电）与 #11 血脉共鸣（每 N 杀回复）。
     * <p>两者都在主钩子位，因此按 {@code mainHook} 判定，参数读 [0] / [1]。
     */
    private void onDoctrineKill(Player p, Enemy e) {
        if (p == null || doctrine < 0 || doctrineKillDepth > 2) return;
        doctrineKillDepth++;
        try {
            // #14 雷霆传导：击杀时向最近敌人释放连锁闪电（伤害 = param[0] × 当前面板伤害）。
            // 用 power(p) 作基数而不是 e.maxHp，避免"打小怪秒大怪"的滚雪球。
            if (mainHook(DoctrineHooks.H_CHAIN_ON_KILL)) {
                Enemy next = nearestOther(e);
                if (next != null) {
                    float ratio = Math.max(0f, dParam(0));
                    lightningChain(p, next, 2, Math.max(1f, power(p) * ratio));
                    effect(e.x, e.y, 60, 7, 0xffe28aff, .32f);
                }
            }
            // #11 血脉共鸣：每 param[0] 次击杀回复 param[1] × 最大生命。
            // 计数器是本局累计击杀（不序列化，最坏情况读档后重数一次，
            // 不会让玩家白赚或漏赚一整局）。
            if (mainHook(DoctrineHooks.H_KILL_HEAL)) {
                int every = Math.max(1, Math.round(dParam(0)));
                float ratio = Math.max(0f, dParam(1));
                doctrineKillClock++;
                if (doctrineKillClock % every == 0 && ratio > 0f) {
                    doctrineHeal(p, p.maxHp * ratio);
                    effect(p.x, p.y, 46, 6, 0xffa8e6b0, .5f);
                }
            }
        } finally {
            doctrineKillDepth--;
        }
    }

    /**
     * 距离 {@code from} 最近的另一个存活敌人。
     * <p>与 {@link #nearestEnemy} 的区别：不要求视线、不限射程——
     * 连锁闪电本来就是"跳到附近任意目标"。
     */
    private Enemy nearestOther(Enemy from) {
        Enemy best = null;
        float dist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e == from || e.hp <= 0) continue;
            float d = distance(from.x, from.y, e.x, e.y);
            if (d < dist) { dist = d; best = e; }
        }
        return best;
    }

    /** 每发子弹的附加行为：轨道、链、分裂、地雷等。 */
    private boolean extras(Player p) {
        boolean any = false;
        if (p.orbitCount > 0) {
            float baseAngle = (float) Math.atan2(p.ay, p.ax) + (tick * .06f);
            for (int i = 0; i < p.orbitCount; i++) {
                float a = baseAngle + i * (float) Math.PI * 2 / p.orbitCount;
                projectile(p, a, power(p) * .55f, 5, 4, 260, 2.6f);
                any = true;
            }
        }
        if (p.mineCount > 0 && tick % 30 == 0) {
            for (int i = 0; i < p.mineCount; i++) {
                float a = tick * .3f + i * 2.1f;
                warningPool(p.x + (float) Math.cos(a) * 40, p.y + (float) Math.sin(a) * 40, 34, 0, .5f, p.id);
            }
            any = true;
        }
        if (p.chainCount > 0 && p.shotCount % 4 == 0) {
            Enemy e = nearestEnemy(p.x, p.y, true);
            if (e != null) {
                lightningChain(p, e, p.chainCount, power(p) * .8f);
                any = true;
            }
        }
        if (p.summonCount > 0 && minions.size() < 2 + p.summonCount && tick % 90 == 0) {
            Minion m = new Minion();
            m.id = allocateId();
            m.owner = p.id;
            m.kind = 0;
            m.x = p.x; m.y = p.y;
            m.life = 12;
            m.cooldown = .6f;
            minions.add(m);
            any = true;
        }
        return any;
    }

    private boolean nearCrate(Player p) {
        for (Prop prop : props)
            if (!prop.opened && prop.type == 0 && distance(p.x, p.y, prop.x, prop.y) < 220
                    && lineClear(p.x, p.y, prop.x, prop.y)) return true;
        return false;
    }

    private Enemy nearestEnemy(float x, float y, boolean sight) {
        Enemy best = null;
        float dist = Float.MAX_VALUE;
        // v2.0 词条「无光视野」：自动瞄准只能锁定近身范围
        float limit = hasPact(Balance.PACT_DARKNESS) ? Balance.PACT_DARKNESS_AIM : Float.MAX_VALUE;
        for (Enemy e : enemies)
            if (e.hp > 0) {
                float d = distance(x, y, e.x, e.y);
                if (d < dist && d <= limit && (!sight || lineClear(x, y, e.x, e.y))) { dist = d; best = e; }
            }
        return best;
    }

    private void fire(Player p, int count, float offset) {
        float damage = power(p);
        // ── v2.0 支柱 2 #14 雷霆传导：暴击率归零（副钩子位）─────────
        // 同时关掉 nextCrit（遗物「残影」闪避必暴），否则"暴击归零"会被绕过。
        boolean critZero = doctrineCritZero();
        boolean crit = !critZero && (p.nextCrit || random.nextFloat() < p.crit);
        if (critZero) p.nextCrit = false;
        if (crit) damage *= 2.1f;
        // ── v2.0 支柱 2 #6 霜封：弹丸伤害 -20%（副钩子位）──────────
        // 注意是在"弹丸层"而不是面板层：炮台/召唤物走 shot() 不经过这里，
        // 因此这条代价只影响玩家自己的弹道，符合牌面描述。
        damage *= doctrineShotDamageMul();
        // v2.0 F_CHARGE：静止蓄力后第一发获得加成，然后清空蓄力
        if (p.chargeHold > 0f) {
            float chargeBonus = 1f + Math.min(1f, p.chargeHold / CHARGE_MAX) * CHARGE_DAMAGE;
            damage *= chargeBonus;
            p.chargeHold = 0f;
        }
        if (p.weaponForm == 1) { count = Math.min(8, count + 1); damage *= .68f; }
        else if (p.weaponForm == 2) damage *= 1.36f;
        // ── v2.1 修复：血誓「血焰喷发」（进化 28）此前是死机制 ────────────
        // refreshMechanicDerived() 一直在算 lowHpExtraShots（血量 <25% 时 +2 弹），
        // 但这个字段**全项目没有任何读取点**——算出来就扔掉了，玩家看不出一点变化。
        // 这里把它接到真实的弹道数上，并同样施加多弹丸惩罚，避免又造出一个
        // "无代价的弹丸放大器"（P0-2 的老问题）。
        if (p.lowHpExtraShots > 0) {
            count += p.lowHpExtraShots;
            damage *= 1f / (1f + p.lowHpExtraShots * .18f);
        }
        // 说明：多弹丸惩罚 P0-2 已在 recompute() 里按面板统一折算，此处不得重复施加。
        // v2.0 词条「弹药枯竭」：弹道 -1（最低 1），单发伤害 +35%
        if (hasPact(Balance.PACT_DROUGHT)) {
            count = Math.max(1, count - 1);
            damage *= Balance.PACT_DROUGHT_DAMAGE;
        }
        int element = roleElement(p);
        // v2.2：元进度「攻击范围 +4%/级」（节点 8）。range 是弹丸的**存活时长系数**，
        // 乘上去等于射程变长——此前这个节点点了也毫无变化。
        float range = (p.role == 5 ? .85f : p.role == 1 ? 1.15f : 1.9f)
                * (1f + Math.max(0f, Math.min(.20f, p.metaRange)));
        float spread = count == 1 ? 0 : (p.weaponForm == 1 ? .26f : p.role == 1 ? .11f : .19f);
        for (int i = 0; i < count; i++) {
            float off = offset + (i == 0 ? 0 : ((i % 2 == 1 ? 1 : -1) * ((i + 1) / 2)) * spread);
            Shot s = projectile(p, off, damage, p.role == 3 || p.role == 4 || p.role == 8 ? 8 : 5,
                    element, p.role == 5 ? 700 : 520, range);
            if (s != null) {
                s.crit = crit;
                // ── v2.0 支柱 2 #6 霜封：所有弹丸附带减速 ───────────────
                // 只写 slowSeconds，不置 Shot.slow 标记：后者会顺带激活
                // "冰痕"体系（冻结反应、对减速目标 +18%），那样 -20% 的代价
                // 会被隐藏收益抵掉，牌面承诺的"用伤害换控制"就不成立了。
                if (mainHook(DoctrineHooks.H_ALL_SHOTS_SLOW))
                    s.slowSeconds = Math.max(s.slowSeconds, Math.max(0f, dParam(0)));
                if (p.has(Content.F_FREEZE)) s.slow = 1;
                if (p.has(Content.F_EXPLODE)) s.explode = 1;
                if (p.has(Content.F_HOMING)) s.homing = 1;
                if (p.has(Content.F_RICOCHET)) s.ricochet = Math.max(1, p.ricochetCount);
                if (p.has(Content.F_BEAM)) s.pierce = Math.max(s.pierce, 3 + p.beamCount);
                if (p.has(Content.F_SPLIT)) s.splitCount = Math.max(1, p.splitCount);
            }
        }
        if (p.has(Content.F_CONE) && count > 1) {
            for (int i = -1; i <= 1; i += 2) projectile(p, offset + i * .55f, damage * .7f, 5, element, 480, range);
        }
    }

    private Shot projectile(Player p, float offset, float damage, float r, int element,
                            float speed, float life) {
        if (shots.size() >= MAX_SHOTS) return null;
        float a = (float) Math.atan2(p.ay, p.ax) + offset;
        Shot s = new Shot();
        s.id = allocateId();
        s.owner = p.id;
        s.x = p.x + (float) Math.cos(a) * 20;
        s.y = p.y + (float) Math.sin(a) * 20;
        s.vx = (float) Math.cos(a) * speed;
        s.vy = (float) Math.sin(a) * speed;
        s.damage = damage;
        s.r = r;
        s.element = element;
        s.life = life;
        s.pierce = p.pierce;
        s.slow = p.slow;
        s.explode = p.explode;
        if (p.has(Content.F_BURN)) s.element = 1;
        shots.add(s);
        return s;
    }

    private void lightningChain(Player p, Enemy first, int jumps, float damage) {
        Enemy current = first;
        Set<Enemy> hit = new HashSet<>();
        // v2.1 跨流派组合 5「雷愈」（生命 + 雷光）：每次连锁闪电为施法者回复 1.5 生命。
        if (p.cross(5)) heal(p, 1.5f);
        for (int i = 0; i < jumps && current != null; i++) {
            hit.add(current);
            damage(current, damage, p.id);
            effect(current.x, current.y, 26, 7, 0xffe28aff, .3f);
            Enemy next = null;
            float best = 210;
            for (Enemy e : enemies)
                if (e.hp > 0 && !hit.contains(e)) {
                    float d = distance(current.x, current.y, e.x, e.y);
                    if (d < best) { best = d; next = e; }
                }
            current = next;
        }
    }

    private void deployTurret(Player p, float x, float y, float life) {
        if (turrets.size() >= 10) return;
        Turret t = new Turret();
        t.id = allocateId();
        t.owner = p.id;
        t.x = x; t.y = y;
        t.life = life;
        turrets.add(t);
    }

    private void updateTurrets(float dt) {
        for (Iterator<Turret> it = turrets.iterator(); it.hasNext(); ) {
            Turret t = it.next();
            t.life -= dt;
            t.cooldown -= dt;
            if (t.life <= 0) { it.remove(); continue; }
            Player p = player(t.owner);
            if (p == null) continue;
            Enemy e = nearestEnemy(t.x, t.y, true);
            if (t.cooldown <= 0 && e != null) {
                float a = (float) Math.atan2(e.y - t.y, e.x - t.x);
                shot(t.x, t.y, a, 580f, power(p) * .78f, false, p.id, null);
                t.cooldown = .3f;
            }
        }
    }

    private void updateMinions(float dt) {
        for (Iterator<Minion> it = minions.iterator(); it.hasNext(); ) {
            Minion m = it.next();
            m.life -= dt;
            if (m.life <= 0) { it.remove(); continue; }
            Player p = player(m.owner);
            if (p == null) continue;
            Enemy e = nearestEnemy(m.x, m.y, false);
            if (e != null) {
                float d = Math.max(1, distance(m.x, m.y, e.x, e.y));
                if (d > 150) { m.x += (e.x - m.x) / d * 110 * dt; m.y += (e.y - m.y) / d * 110 * dt; }
                m.cooldown -= dt;
                if (m.cooldown <= 0 && d < 260) {
                    m.cooldown = .7f;
                    float a = (float) Math.atan2(e.y - m.y, e.x - m.x);
                    shot(m.x, m.y, a, 520f, power(p) * .42f, false, p.id, null);
                }
            } else {
                float d = Math.max(1, distance(m.x, m.y, p.x, p.y));
                if (d > 70) { m.x += (p.x - m.x) / d * 130 * dt; m.y += (p.y - m.y) / d * 130 * dt; }
            }
        }
    }

    private int roleElement(Player p) {
        if (p.relics[Relics.FIRE_HEART] > 0) return 1;
        if (p.relics[Relics.FROST_HEART] > 0) return 2;
        switch (p.role) {
            case 4: case 9: return 1;
            case 3: case 8: return 2;
            case 10: return 3;
            case 11: return 4;
            default: return 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  危险区 / 道具 / 刷怪
    // ══════════════════════════════════════════════════════════════

    /**
     * 危险区更新。
     * <p><b>并发安全</b>：地雷伤害会走 {@link #damage} → 击杀 → 亡语爆裂 / 分裂等路径，
     * 这些路径可能再次调用 {@link #warningPool} 往 {@code hazards} 里加元素。
     * 因此这里**不对 hazards 直接迭代**，而是先把当前快照拷到复用缓冲里再遍历，
     * 新增的 hazard 从下一帧开始参与结算（视觉上无差别，但绝不会 CME）。
     */
    private final ArrayList<Hazard> hazardScratch = new ArrayList<>(32);

    private void updateHazards(float dt) {
        hazardScratch.clear();
        hazardScratch.addAll(hazards);
        for (Hazard h : hazardScratch) {
            if (!hazards.contains(h)) continue;      // 快照期间被移除的（极少数路径）
            if (h.active > 0) {
                h.active -= dt;
                // v2.0：玩家布置的地雷（owner >= 0）伤害敌人，环境危害才伤害玩家
                if (h.owner >= 0) {
                    Player owner = player(h.owner);
                    if (owner != null) {
                        for (Enemy e : enemies)
                            if (e.hp > 0 && distance(h.x, h.y, e.x, e.y) < h.r + e.r)
                                damage(e, power(owner) * MINE_DAMAGE_RATIO, h.owner);
                    }
                } else if (h.kind != 1) {
                    for (Player p : players)
                        if (distance(p.x, p.y, h.x, h.y) < h.r + 10)
                            hurt(p, h.kind == 0 ? 16 : h.kind == 3 ? 22 : 19);
                }
                if (h.active <= 0 && h.clock < 0) hazards.remove(h);
            } else {
                h.clock -= dt;
                if (h.clock <= 0) {
                    boolean once = h.active < 0;
                    h.active = h.kind == 1 ? 2.4f : .45f;
                    h.clock = once ? -1 : h.kind == 0 ? 4.2f : h.kind == 3 ? 3.4f : 5.2f;
                    effect(h.x, h.y, h.r, 2, h.kind == 0 ? 0xffefac77 : h.kind == 1 ? 0xff9bcfe8
                            : h.kind == 3 ? 0xffe07a4a : 0xffafa7ef, .5f);
                }
            }
        }
        hazardScratch.clear();
    }

    private void updateProps() {
        for (Prop prop : props)
            if (prop.type == 1 && !prop.opened) {
                for (Player p : players)
                    if (p.connected && !p.down && distance(prop.x, prop.y, p.x, p.y) < 54) {
                        prop.opened = true;
                        int gain = 12 + floor * 3;
                        coins += gain;
                        effect(prop.x, prop.y, 48, 3, 0xffefc785, .65f);
                        notice = "发现补给箱 · 星尘 +" + gain;
                        break;
                    }
            }
    }

    private void spawn() {
        if (enemies.size() >= MAX_ENEMIES) return;
        Enemy e = new Enemy();
        e.id = allocateId();
        int type = pickEnemyType();
        e.type = type;
        e.behavior = Enemies.BEHAVIOR[type];
        e.r = Enemies.RADIUS[type];
        // ── v2.0 支柱 2 #8 极寒迟滞：敌人移速 -25%（主钩子位）──────────
        // 用乘法乘在既有 pacing 之后：契约/腐化/异变的相对权重保持不变。
        e.speed = Enemies.SPEED[type] * Balance.PACE[contract]
                * Balance.corruptionPace(corruption)
                * (mutator == 1 ? 1.12f : 1)
                * (mutator2 == 1 ? 1.10f : 1)
                * doctrineEnemySpeedMul();
        e.cooldown = Enemies.COOLDOWN[type] * (.8f + random.nextFloat() * .4f);
        e.telegraph = Enemies.TELEGRAPH[type];
        e.stage = -1;
        float hp = Enemies.HP[type] * Balance.hpFactor(this) * Balance.corruptionHp(corruption)
                * Balance.pactHpMul(this)
                * (1 + (players.size() - 1) * .5f);
        e.maxHp = e.hp = hp;
        if (roomType == ROOM_ELITE && spawned % 6 == 0) makeElite(e);
        if (mutator == 3 && spawned % 6 == 0) makeElite(e);
        if (mutator == 7 && spawned % 4 == 0) makeElite(e);
        if (mutator2 == 3 && spawned % 5 == 0) makeElite(e);
        int eliteEvery = Balance.corruptionEliteEvery(corruption);
        if (eliteEvery > 0 && spawned > 0 && spawned % eliteEvery == 0 && !e.elite) makeElite(e);
        // v2.0 词条「精英狂潮」：精英刷新频率翻倍（间隔减半）
        int pactEvery = Balance.pactEliteEvery(this);
        if (pactEvery > 0 && spawned % pactEvery == 0 && !e.elite) makeElite(e);
        placeEnemy(e);
        enemies.add(e);
        effect(e.x, e.y, e.r * 2.2f, 0, 0xffd1a580, .9f);
    }

    private int pickEnemyType() {
        int best = -1;
        int total = 0;
        int[] pool = new int[Enemies.COUNT];
        for (int i = 0; i < Enemies.COUNT; i++) {
            if (Enemies.MIN_FLOOR[i] > floor) continue;
            if ((Enemies.BIOME_MASK[i] & (1 << Math.min(5, biome))) == 0) continue;
            if (roomType == ROOM_ELITE && Enemies.BEHAVIOR[i] == B_SWARM) continue;
            pool[total++] = i;
        }
        if (total == 0) return 0;
        int sum = 0;
        for (int i = 0; i < total; i++) sum += Enemies.WEIGHT[pool[i]];
        int roll = random.nextInt(Math.max(1, sum));
        int acc = 0;
        for (int i = 0; i < total; i++) {
            acc += Enemies.WEIGHT[pool[i]];
            if (roll < acc) { best = pool[i]; break; }
        }
        return best < 0 ? pool[0] : best;
    }

    /**
     * 精英词缀。4 种，两两正交——玩家应该「看一眼就知道该怎么打」：
     * <pre>
     *   1 护甲   —— 前 22% 伤害被 70% 减免。破甲之后才是真血条。
     *   2 厚血   —— 血更多。最朴素，但给爆发流一个明确的检验。
     *   3 急速   —— 出手更快。逼玩家优先处理，不能用走位慢慢磨。
     *   4 护盾   —— 一层独立于血量的护盾，脱战 4 秒后开始缓慢再生。
     *               与 1 的区别：护甲是「减伤」，护盾是「额外的血」——可以被破盾打断节奏。
     * </pre>
     *
     * <p><b>v2.1 修复</b>：`affix = 1 + random.nextInt(4)` 会产出 4，但当时代码只有
     * 1/2/3 三个分支——**25% 的精英是白板**。现在 4 号接上 {@code Enemy.shield}
     * （{@code damage()} 早已消费该字段），并补上再生计时。
     */
    private void makeElite(Enemy e) {
        e.elite = true;
        e.affix = 1 + random.nextInt(4);
        e.maxHp *= 1.55f;
        e.hp = e.maxHp;
        e.r *= 1.14f;
        e.speed *= .96f;
        switch (e.affix) {
            case 1: e.guard = e.maxHp * .22f; break;
            case 2: e.maxHp *= 1.12f; e.hp = e.maxHp; break;
            case 3: e.cooldown *= .82f; break;
            default:
                // 护盾 = 最大生命的 30%；shieldMax 记上限，hurtClock 记「最后受击后多久」。
                e.shieldMax = e.shield = e.maxHp * .30f;
                e.shieldClock = 0f;
                e.hurtClock = 0f;
                break;
        }
    }

    private void placeEnemy(Enemy e) {
        float safest = -1, px = 60, py = 80;
        for (int attempts = 0; attempts < 32; attempts++) {
            int edge = random.nextInt(4);
            float x = edge == 0 ? 62 : edge == 1 ? 1058 : 90 + random.nextFloat() * 940;
            float y = edge == 2 ? 68 : edge == 3 ? 550 : 85 + random.nextFloat() * 450;
            float min = 2500;
            for (Player p : players) if (!p.down) min = Math.min(min, distance(x, y, p.x, p.y));
            if (!blocked(x, y, e.r + 4) && min > safest) { safest = min; px = x; py = y; }
            if (safest > 250) break;
        }
        e.x = px;
        e.y = py;
    }

    private void spawnBoss() {
        int idx = bossIndexForRoom();
        Enemy e = new Enemy();
        e.id = allocateId();
        e.type = 0;
        e.bossIndex = idx;
        e.behavior = B_CHASE;
        e.elite = true;
        e.x = 560;
        e.y = 165;
        e.r = Bosses.RADIUS[idx];
        // #8 极寒迟滞对首领同样生效（但不影响其冲锋速度，见 updateBoss 的 chargeVx/Vy）。
        e.speed = Bosses.SPEED[idx] * Balance.PACE[contract] * doctrineEnemySpeedMul();
        e.maxHp = e.hp = Bosses.HP[idx] * Balance.hpFactor(this)
                * Balance.corruptionHp(corruption) * (1 + (players.size() - 1) * .6f);
        e.cooldown = 2.2f;
        e.telegraph = 1f;
        e.stage = 0;
        e.phaseTriggers = 0;
        e.variant = Bosses.ART[idx];
        enemies.add(e);
        effect(e.x, e.y, 150, 6, 0xffefbe8a, 1.2f);
        notice = Bosses.NAMES[idx] + " · " + Bosses.TITLE[idx];
    }

    // ══════════════════════════════════════════════════════════════
    //  敌人 AI
    // ══════════════════════════════════════════════════════════════

    private void updateEnemies(float dt) {
        for (Enemy e : enemies) {
            if (e.hp <= 0) continue;
            e.hit = Math.max(0, e.hit - dt);
            e.flash = Math.max(0, e.flash - dt);
            e.stun = Math.max(0, e.stun - dt);
            if (e.burn > 0) {
                e.burn -= dt;
                e.burnClock -= dt;
                if (e.burnClock <= 0) {
                    e.burnClock = .5f;
                    Player src = player(e.owner);
                    damage(e, src == null ? 4 : power(src) * .10f, e.owner);
                    if (e.hp <= 0) continue;
                }
            }
            // v2.1：护盾词缀（affix 4）再生。脱战 4 秒后按每秒 12% 上限回复，
            // 让「破盾 → 爆发」成为可执行的战术，而不是一场无限消耗战。
            if (e.shieldMax > 0f && e.shield < e.shieldMax) {
                e.hurtClock += dt;
                e.shieldClock += dt;
                if (e.hurtClock >= 4f) {
                    e.shield = Math.min(e.shieldMax, e.shield + e.shieldMax * .12f * dt);
                    if (e.shield >= e.shieldMax) e.shieldClock = 0f;
                }
            }
            if (e.stun > 0) continue;
            e.slow = Math.max(0, e.slow - dt);
            e.cooldown -= dt;
            if (e.bossIndex >= 0) { updateBoss(e, dt); continue; }
            // v2.0 词条「瞬移猎手」：非首领敌人周期性瞬移（预警 → 落地）。
            // 放在 boss 分支之后、攻击状态机之前：Boss 与仆从不受影响，
            // 且冲锋 / 蓄力中的敌人会先完成当前动作再进入瞬移循环。
            if (updateBlinker(e, dt)) continue;
            Player p = nearestLiving(e.x, e.y);
            if (p == null) continue;
            if (e.behavior == B_TURRET) {
                if (e.cooldown <= 0) {
                    attackTurret(e, p);
                }
                continue;
            }
            if (e.chargeTime > 0) {
                e.chargeTime -= dt;
                moveEnemy(e, e.chargeVx * dt, e.chargeVy * dt);
                // v2.1：冲锋伤害读 Bosses.CHARGE_DAMAGE[bossIndex]（此前硬编码 20），
                // 且一次冲锋只结算一次，避免贴身时每帧连击。
                if (!e.chargeHit) {
                    float dmg = e.chargeDamage > 0 ? e.chargeDamage : 20f;
                    for (Player q : players)
                        if (q.connected && !q.down && distance(q.x, q.y, e.x, e.y) < e.r + 16) {
                            hurt(q, dmg);
                            e.chargeHit = true;
                            break;
                        }
                }
                continue;
            }
            if (e.telegraph > 0) {
                e.telegraph -= dt;
                e.windupT = e.windup > .01f
                        ? clamp(1f - e.telegraph / e.windup, 0f, 1f) : 1f;
                if (e.telegraph <= 0 && e.stage == -1) { e.stage = 0; e.windupT = -1; continue; }
                if (e.telegraph <= 0) {
                    e.windupT = -1;
                    e.windup = 0;
                    e.threat = 0;
                    doEnemyAttack(e, p);
                }
                continue;
            }
            if (e.telegraph <= 0 && e.windup > 0) { e.windup = 0; e.windupT = -1; }
            if (e.cooldown <= 0) {
                if (canAttack(e, p)) { startTelegraph(e, p); continue; }
            }
            moveEnemyAI(e, p, dt);
            for (Player q : players)
                if (q.connected && !q.down && distance(q.x, q.y, e.x, e.y) < e.r + 14)
                    hurt(q, contactDamage(e));
        }
        // 分离
        for (int i = 0; i < enemies.size(); i++)
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy e = enemies.get(i), f = enemies.get(j);
                float d = distance(e.x, e.y, f.x, f.y);
                if (d > .1f && d < e.r + f.r) {
                    float push = Math.min(2, (e.r + f.r - d) * .13f);
                    moveEnemy(e, (e.x - f.x) / d * push, (e.y - f.y) / d * push);
                }
            }
    }

    // ── v2.0 词条「瞬移猎手」──────────────────────────────────────
    //
    //  此前 Balance.PACT_BLINK_INTERVAL / PACT_BLINK_WARN 声明了却零消费。
    //  这里接上真正的循环，并且**改变的是走位规则而不是数值**：
    //  敌人不再只是"走"向你，而是每隔 7 秒"跳"到你身边；
    //  但它必须先在落点亮 0.55 秒的预警圈，等于把击杀窗口交给了会读圈的玩家。
    //
    //  设计约束：
    //   * 只作用于非首领敌人（首领已有 BOSS_BLINK 招式）；
    //   * 炮台不瞬移（它本来就是固定的）；
    //   * 落点必须在可通行处、且与玩家保持 90~170 像素——太近等于无预警秒杀；
    //   * 预警用 warningPool(kind=4 虚空紫)，与伤害型预警区分：它**不造成伤害**；
    //   * 所有随机走 random，同 seed 逐帧一致。
    private static final int BLINK_WARN_KIND = 4;

    /**
     * 推进「瞬移猎手」状态机。返回 true 表示本帧该敌人已被接管（跳过普通 AI）。
     * <p>节奏由已序列化的 {@code blinkClock} 承载：它从 {@link Balance#PACT_BLINK_INTERVAL}
     * 递减到 0 时开始预警，预警结束落地后重置。因此读档不会白送或吞掉一次瞬移。
     */
    private boolean updateBlinker(Enemy e, float dt) {
        if (!hasPact(Balance.PACT_BLINKERS)) return false;
        if (e.behavior == B_TURRET) return false;
        if (e.blinkWarn > 0f) {
            // ── 预警中：站定不动，落地才有可能被躲开 ──────────────
            e.blinkWarn -= dt;
            if (e.blinkWarn > 0f) return true;
            e.blinkWarn = 0f;
            if (isBlinkDestinationValid(e)) {
                effect(e.x, e.y, e.r * 1.6f, 6, 0xffbda5ed, .5f);
                e.x = e.blinkX;
                e.y = e.blinkY;
                effect(e.x, e.y, e.r * 2.2f, 6, 0xffd7c4ff, .6f);
            }
            e.blinkX = e.blinkY = 0f;
            e.blinkClock = Balance.PACT_BLINK_INTERVAL;
            return true;                       // 落地这一帧不移动，给玩家一次反应
        }
        e.blinkClock -= dt;
        if (e.blinkClock > 0f) return false;
        Player target = nearestLiving(e.x, e.y);
        if (target == null) { e.blinkClock = 1f; return false; }
        float near = Math.max(30f, Balance.PACT_BLINK_NEAR);
        float far = Math.max(near + 10f, Balance.PACT_BLINK_FAR);
        // 最多试 8 次找一个不压在地形里的落点；找不到就放弃这次瞬移（并缩短等待重试）。
        for (int tries = 0; tries < 8; tries++) {
            float a = random.nextFloat() * 6.2832f;
            float d = near + random.nextFloat() * (far - near);
            float nx = clamp(target.x + (float) Math.cos(a) * d, 50f, 1070f);
            float ny = clamp(target.y + (float) Math.sin(a) * d, 55f, 565f);
            if (blocked(nx, ny, e.r + 2)) continue;
            e.blinkX = nx;
            e.blinkY = ny;
            e.blinkWarn = Balance.PACT_BLINK_WARN;
            warningPool(nx, ny, Balance.PACT_BLINK_RADIUS, BLINK_WARN_KIND,
                    Balance.PACT_BLINK_WARN, -1);
            return true;
        }
        e.blinkClock = 1f;
        return false;
    }

    /**
     * 落点是否仍然有效。
     * <p>瞬态字段不存档：若在读档（或瞬态丢失）后 blinkX/blinkY 为 0，这里判为无效，
     * 于是取消本次落地而不是把敌人瞬移到坐标系原点。
     */
    private boolean isBlinkDestinationValid(Enemy e) {
        if (e.blinkX <= 0f || e.blinkY <= 0f) return false;
        return !blocked(e.blinkX, e.blinkY, e.r + 2);
    }

    /** 接触伤害原始值；契约承伤倍率由 {@link #hurt} 统一应用，此处不得再乘。 */
    private float contactDamage(Enemy e) {
        switch (e.behavior) {
            case B_TANK: return 16;
            case B_SWARM: return 7;
            case B_BOMBER: return 14;
            default: return 12;
        }
    }

    private boolean canAttack(Enemy e, Player p) {
        float d = distance(e.x, e.y, p.x, p.y);
        switch (e.behavior) {
            case B_CHASE: case B_SWARM: case B_SPLITTER: return d < e.r + 22;
            case B_SHOOTER: case B_SNIPER: case B_SPIRAL: case B_ZONER: case B_HEALER: case B_SHIELD: return d < 420;
            case B_CHARGER: return d < 340;
            case B_BOMBER: return d < 60;
            case B_SUMMONER: return true;
            case B_BLINK: return d < 360;
            case B_FLYER: return d < e.r + 24;
            default: return d < 300;
        }
    }

    /**
     * 危险等级：让玩家一眼分出"可以硬吃"和"必须躲开"的招式。
     * <p>0 = 普通接触 / 轻弹；1 = 重伤（被打中会掉一大截）；2 = 致命（可能直接决定生死）。
     * UI 可以据此换预警颜色、加粗描边、加速音效。
     */
    public static int threatOf(int behavior, boolean elite) {
        int base;
        switch (behavior) {
            case B_BOMBER: base = 2; break;
            case B_CHARGER: case B_SNIPER: case B_TANK: base = 1; break;
            case B_SPIRAL: case B_ZONER: case B_BLINK: case B_SHIELD: base = 1; break;
            default: base = 0; break;
        }
        if (elite && base == 1) base = 2;
        return base;
    }

    /**
     * 开始一次蓄力。
     * <p>关键改动（v1.1）：蓄力时长按 {@link Balance#telegraphScale} 拉长，
     * 同时<b>等量减少出手后的冷却</b>，因此攻击频率与伤害预算完全不变，
     * 变的只是"读得到 / 读不到"——这是可学习性，不是数值削弱。
     */
    private void startTelegraph(Enemy e, Player p) {
        float base = Enemies.TELEGRAPH[e.type] * (e.elite ? .85f : 1);
        float scale = Balance.telegraphScale(e.behavior);
        e.telegraph = base * scale;
        e.windup = e.telegraph;
        e.windupT = 0;
        e.threat = threatOf(e.behavior, e.elite);
        e.tx = clamp(p.x + p.dx * 34, 45, 1075);
        e.ty = clamp(p.y + p.dy * 34, 48, 572);
        e.cooldown = Enemies.COOLDOWN[e.type] * (e.elite && e.affix == 3 ? .7f : 1)
                / Balance.PACE[contract]
                - base * (scale - 1f);
        e.cooldown = Math.max(base * .35f, e.cooldown);
    }

    private void doEnemyAttack(Enemy e, Player p) {
        float aim = (float) Math.atan2(e.ty - e.y, e.tx - e.x);
        switch (e.behavior) {
            case B_CHASE: case B_SWARM: case B_SPLITTER:
                for (Player q : players)
                    if (distance(q.x, q.y, e.x, e.y) < e.r + 30) hurt(q, contactDamage(e) * 1.4f);
                break;
            case B_SHOOTER: {
                int n = e.elite ? 4 : 3;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, aim + (i - (n - 1) * .5f) * .16f, 220, 11, true, -1, e);
                break;
            }
            case B_CHARGER: {
                float d = Math.max(1, distance(e.x, e.y, e.tx, e.ty));
                e.chargeVx = (e.tx - e.x) / d * 520;
                e.chargeVy = (e.ty - e.y) / d * 520;
                e.chargeTime = .42f;
                break;
            }
            case B_BOMBER: {
                warningPool(e.x, e.y, e.elite ? 78 : 58, 0, .3f, -1);
                for (Player q : players)
                    if (distance(q.x, q.y, e.x, e.y) < 90) hurt(q, 26);
                e.hp = 0;
                break;
            }
            case B_ZONER:
                warningPool(e.tx, e.ty, 62, e.elite ? 3 : 0, .5f, -1);
                break;
            case B_SNIPER: {
                shot(e.x, e.y, aim, 340, 20, true, -1, e);
                break;
            }
            case B_SUMMONER: {
                if (enemies.size() < MAX_ENEMIES - 4) {
                    for (int i = 0; i < (e.elite ? 3 : 2); i++) {
                        Enemy child = new Enemy();
                        child.id = allocateId();
                        child.type = 9;
                        child.behavior = B_SWARM;
                        child.x = e.x + (i == 0 ? -26 : 26);
                        child.y = e.y + 18;
                        child.r = 11;
                        child.speed = 128;
                        child.maxHp = child.hp = 22 * Balance.hpFactor(this);
                        child.stage = -1;
                        child.telegraph = .5f;
                        child.summoned = true;
                        pendingEnemies.add(child);
                    }
                }
                break;
            }
            case B_SPIRAL: {
                int n = e.elite ? 14 : 10;
                float base = tick * .17f;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, base + i * (float) Math.PI * 2 / n, 175, 10, true, -1, e);
                break;
            }
            case B_BLINK: {
                float a = random.nextFloat() * 6.2832f;
                float d = 70 + random.nextFloat() * 70;
                float nx = clamp(p.x + (float) Math.cos(a) * d, 50, 1070);
                float ny = clamp(p.y + (float) Math.sin(a) * d, 55, 565);
                if (!blocked(nx, ny, e.r)) { e.x = nx; e.y = ny; effect(e.x, e.y, 40, 0, 0xffbda5ed, .6f); }
                for (Player q : players)
                    if (distance(q.x, q.y, e.x, e.y) < e.r + 30) hurt(q, 18);
                break;
            }
            case B_HEALER: {
                for (Enemy ally : enemies)
                    if (ally != e && ally.hp > 0 && distance(e.x, e.y, ally.x, ally.y) < 210) {
                        ally.hp = Math.min(ally.maxHp, ally.hp + ally.maxHp * .12f);
                        ally.shield = Math.max(ally.shield, 18);
                    }
                break;
            }
            case B_SHIELD: {
                for (int i = 0; i < 5; i++)
                    shot(e.x, e.y, aim + (i - 2) * .2f, 195, 12, true, -1, e);
                break;
            }
            case B_FLYER: {
                warningPool(e.tx, e.ty, 44, 0, .4f, -1);
                break;
            }
            default: {
                int n = e.elite ? 5 : 3;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, aim + (i - (n - 1) * .5f) * .18f, 215, 11, true, -1, e);
                break;
            }
        }
    }

    private void attackTurret(Enemy e, Player p) {
        float aim = (float) Math.atan2(p.y - e.y, p.x - e.x);
        e.cooldown = 1.4f / Balance.PACE[contract];
        for (int i = 0; i < (e.elite ? 3 : 2); i++)
            shot(e.x, e.y, aim + (i - .5f) * .12f, 285, 12, true, -1, e);
    }

    private void moveEnemyAI(Enemy e, Player p, float dt) {
        float d = Math.max(1, distance(e.x, e.y, p.x, p.y));
        float vx = (p.x - e.x) / d, vy = (p.y - e.y) / d;
        float speed = e.speed * (e.slow > 0 ? .64f : 1);
        boolean sight = lineClear(e.x, e.y, p.x, p.y);
        boolean walkSight = lineClearRadius(e.x, e.y, p.x, p.y, e.r + 5);
        boolean ranged = e.behavior == B_SHOOTER || e.behavior == B_SNIPER || e.behavior == B_SPIRAL
                || e.behavior == B_HEALER || e.behavior == B_SHIELD || e.behavior == B_ZONER;
        if (e.behavior == B_FLYER) {
            e.x = clamp(e.x + vx * speed * dt, 34, 1086);
            e.y = clamp(e.y + vy * speed * dt, 36, 584);
            return;
        }
        if (!walkSight) {
            navigation.target(p.x, p.y);
            int next = navigation.next(e.x, e.y);
            float len = Math.max(1, distance(e.x, e.y, navigation.x(next), navigation.y(next)));
            vx = (navigation.x(next) - e.x) / len;
            vy = (navigation.y(next) - e.y) / len;
        } else if (ranged && sight && d < 190) {
            vx = -vx; vy = -vy; speed *= .65f;
        } else if (ranged && sight && d < 320) {
            float sign = e.id % 2 == 0 ? 1 : -1;
            float tmp = vx;
            vx = -vy * sign; vy = tmp * sign; speed *= .4f;
        } else if (e.behavior == B_CHASE || e.behavior == B_SWARM) {
            float side = (e.id % 2 == 0 ? 1 : -1) * .22f;
            float tx = vx - vy * side;
            vy += vx * side;
            vx = tx;
        } else if (!sight || blocked(e.x + vx * 32, e.y + vy * 32, e.r + 2)) {
            navigation.target(p.x, p.y);
            int next = navigation.next(e.x, e.y);
            float len = Math.max(1, distance(e.x, e.y, navigation.x(next), navigation.y(next)));
            vx = (navigation.x(next) - e.x) / len;
            vy = (navigation.y(next) - e.y) / len;
        }
        if (d > e.r + 15 || ranged) moveEnemy(e, vx * speed * dt, vy * speed * dt);
    }

    // ══════════════════════════════════════════════════════════════
    //  首领 AI（8 位 × 3 阶段）
    // ══════════════════════════════════════════════════════════════

    /**
     * 首领阶段数：默认 3（{@code Bosses.PHASES} 的列数）。
     * <p>「首领狂怒」不能改 {@code Bosses.PHASES}（那是 8×3 的固定表，{@code Bosses.valid()}
     * 会校验列数），所以第 4 阶段是<b>运行时状态</b>：血量跌破
     * {@link Balance#PACT_FURY_HP} 时追加一段，招式复用第 0 招，但出手更快、再召一批护卫。
     */
    static final int BOSS_EXTRA_PHASE = 3;

    private void updateBoss(Enemy e, float dt) {
        int idx = e.bossIndex;
        int phase = e.hp < e.maxHp * .34f ? 2 : e.hp < e.maxHp * .67f ? 1 : 0;
        // ── v2.0 词条「首领狂怒」：第 4 阶段 ──────────────────────────
        // 在常规三阶段之外追加"最后一口气"：血量跌破 15% 进入。
        // 注意 phase 会被下面的 doBossPattern 直接拿来当 extra 用，
        // 所以这里必须把 3 映射回 0 再去查表 / 算弹量，绝不能让数组越界。
        if (hasPact(Balance.PACT_BOSS_FURY) && phase == 2
                && e.hp < e.maxHp * Balance.PACT_FURY_HP) {
            phase = BOSS_EXTRA_PHASE;
        }
        // ── v2.1 修复：首领冲锋此前完全没有推进（chargeVx/Vy 写了但无人消费），
        //    BOSS_CHARGE 等于一个"站着不动"的死招式。这里补齐三段式状态机：
        //    预警 → 冲刺 → 恢复窗口，参数全部读 Bosses.CHARGE_* 表。
        if (e.chargeTime > 0) {
            e.chargeTime -= dt;
            moveEnemy(e, e.chargeVx * dt, e.chargeVy * dt);
            if (!e.chargeHit) {
                float dmg = e.chargeDamage > 0 ? e.chargeDamage : 20f;
                for (Player q : players)
                    if (q.connected && !q.down && distance(q.x, q.y, e.x, e.y) < e.r + 18) {
                        hurt(q, dmg);
                        e.chargeHit = true;
                        break;
                    }
            }
            if (e.chargeTime <= 0) {
                // 冲刺结束进入恢复窗口：不移动、不攻击，这是玩家的输出窗口。
                e.telegraph = 0;
                e.stage = 0;
                e.cooldown = Bosses.charges(idx) ? Bosses.CHARGE_RECOVER[idx] : .5f;
                effect(e.x, e.y, e.r * 1.4f, 3, 0xffc9a06a, .45f);
            }
            return;
        }
        if (phase > e.phaseTriggers) {
            e.phaseTriggers = phase;
            e.cooldown = Math.min(e.cooldown, .35f);
            e.telegraph = .8f;
            e.stage = 0;
            notice = Bosses.NAMES[idx] + " 变招 · " + (phase == 1 ? "进入第二阶段"
                    : phase == 2 ? "进入狂暴" : "狂怒 · 最后一口气");
            effect(e.x, e.y, phase >= BOSS_EXTRA_PHASE ? 190 : 130, 6, 0xffefbe8a,
                    phase >= BOSS_EXTRA_PHASE ? 1.6f : 1.1f);
            // 第 4 阶段额外召一批护卫（数据表里的 ADDS 是"每次变招"的固定值，
            // 这里单独叠加，避免为了一个词条去改 Bosses 的公共表）。
            int adds = Bosses.ADDS[idx]
                    + (phase >= BOSS_EXTRA_PHASE ? Balance.PACT_FURY_ADDS : 0);
            for (int j = 0; j < adds && enemies.size() + pendingEnemies.size() < MAX_ENEMIES; j++) {
                Enemy add = new Enemy();
                add.id = allocateId();
                add.type = 9;
                add.behavior = B_SWARM;
                add.x = 120 + j * 90;
                add.y = 160;
                add.r = 13;
                add.maxHp = add.hp = 60 * Balance.hpFactor(this);
                add.speed = 120;
                add.stage = -1;
                add.telegraph = .9f;
                add.summoned = true;
                pendingEnemies.add(add);
            }
        }
        Player p = nearestLiving(e.x, e.y);
        if (p == null) return;
        if (e.telegraph > 0 && e.stage == 0) {
            e.telegraph -= dt;
            if (e.telegraph <= 0) {
                e.stage = 1;
                int pattern = bossPatternAt(idx, phase);
                // v2.1：冲锋的方向锁定在**预警开始那一刻**记录的 e.tx/e.ty。
                // 此前 doBossPattern 读的是实时玩家坐标，等于"预警给你看，但方向跟着你走"，
                // 侧移毫无意义。这里在出手前先恢复快照；非冲锋招式随后会用 p 的实时位置，
                // 两者语义不同但都不影响其它招式。
                if (pattern == BOSS_CHARGE) {
                    float sx = e.tx, sy = e.ty;
                    doBossPattern(e, p, pattern, phase);
                    e.tx = sx; e.ty = sy;
                } else {
                    doBossPattern(e, p, pattern, phase);
                }
                e.cooldown = bossCooldown(phase);
            }
            return;
        }
        float d = Math.max(1, distance(e.x, e.y, p.x, p.y));
        float vx = (p.x - e.x) / d, vy = (p.y - e.y) / d;
        float speed = e.speed * (e.slow > 0 ? .8f : 1);
        if (d > 200) moveEnemy(e, vx * speed * dt, vy * speed * dt);
        else if (d < 130) moveEnemy(e, -vx * speed * .55f * dt, -vy * speed * .55f * dt);
        else {
            float sign = e.id % 2 == 0 ? 1 : -1;
            moveEnemy(e, -vy * sign * speed * .5f * dt, vx * sign * speed * .5f * dt);
        }
        if (e.cooldown <= 0) {
            int next = bossPatternAt(idx, phase);
            // v2.1：冲锋的预警时长读表（0.52–0.78s，随体型变化），并且锁定此刻的玩家位置，
            // 让「看到预警 → 侧移躲开」成为可行的应对，而不是贴身必中。
            e.telegraph = next == BOSS_CHARGE && Bosses.charges(idx)
                    ? Bosses.CHARGE_WINDUP[idx] : Balance.bossWindup(bossBasePhase(phase));
            e.stage = 0;
            e.tx = p.x; e.ty = p.y;
            e.cooldown = .8f;
        }
        for (Player q : players)
            if (q.connected && !q.down && distance(q.x, q.y, e.x, e.y) < e.r + 16)
                hurt(q, 20);
    }

    /**
     * 把运行时阶段映射回 {@code Bosses.PHASES} 的合法列（0..2）。
     * <p>「首领狂怒」的第 4 阶段（{@link #BOSS_EXTRA_PHASE}）复用第 0 招——
     * 这样既不需要改公共数据表，也能让玩家读得懂："它又回到了开场的那套，
     * 但这次快得多"。
     */
    private int bossBasePhase(int phase) {
        if (phase >= BOSS_EXTRA_PHASE) return 0;
        return Math.max(0, Math.min(2, phase));
    }

    /** 该首领在指定运行时阶段使用的主招式。 */
    private int bossPatternAt(int idx, int phase) {
        return Bosses.PHASES[Math.max(0, Math.min(Bosses.COUNT - 1, idx))][bossBasePhase(phase)];
    }

    /**
     * 首领出手后的冷却。
     * <p>常规三阶段沿用原公式 {@code (1.32 - phase*0.22) / PACE}；第 4 阶段取
     * "第三阶段的冷却 × {@link Balance#PACT_FURY_RATE}"（0.60，即攻速约 +67%）。
     * 下限 0.18s 防止契约 4 + 多条减速叠加后把首领压成无冷却机关枪。
     */
    private float bossCooldown(int phase) {
        if (phase >= BOSS_EXTRA_PHASE) {
            float phase2 = 1.32f - 2 * .22f;
            return Math.max(.18f, phase2 * Balance.PACT_FURY_RATE / Balance.PACE[contract]);
        }
        return (1.32f - phase * .22f) / Balance.PACE[contract];
    }

    private void doBossPattern(Enemy e, Player p, int pattern, int phase) {
        float aim = (float) Math.atan2(p.y - e.y, p.x - e.x);
        // 弹量 / 数量参数按"基础阶段"取：狂怒阶段（3）不能直接乘出 28% 的额外弹幕，
        // 它的压力来自出手频率与护卫，而不是又一次数值膨胀。
        int extra = bossBasePhase(phase);
        switch (pattern) {
            case BOSS_RING: {
                int n = 20 + extra * 6;
                float off = tick * .2f;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, off + i * (float) Math.PI * 2 / n, 175 + extra * 20, 15, true, -1, e);
                break;
            }
            case BOSS_FAN: {
                int n = 7 + extra * 2;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, aim + (i - (n - 1) * .5f) * .13f, 270, 17, true, -1, e);
                break;
            }
            case BOSS_HOMING: {
                for (int i = 0; i < 3 + extra; i++) {
                    Shot s = shot(e.x, e.y, aim + (i - 1) * .3f, 180, 18, true, -1, e);
                    if (s != null) s.homing = 1;
                }
                break;
            }
            case BOSS_CHARGE: {
                int idx = Math.max(0, e.bossIndex);
                boolean real = Bosses.charges(idx);
                // v2.1：方向锁定在**预警开始那一刻**的玩家位置（e.tx/e.ty），
                // 而不是出手瞬间的实时位置——这样玩家看到预警后侧移就能躲开，
                // 才符合「把伤害从不可反应挪到可反应」的设计意图。
                float tx = e.tx, ty = e.ty;
                if (distance(e.x, e.y, tx, ty) < 1) { tx = p.x; ty = p.y; }
                float dx = tx - e.x, dy = ty - e.y;
                float d = Math.max(1, (float) Math.hypot(dx, dy));
                float sp = real ? Bosses.CHARGE_SPEED[idx] : 620f;
                e.chargeVx = dx / d * sp;
                e.chargeVy = dy / d * sp;
                e.chargeTime = real ? Bosses.CHARGE_TIME[idx] : .5f;
                e.chargeDamage = real ? Bosses.CHARGE_DAMAGE[idx] : 20f;
                e.chargeHit = false;
                break;
            }
            case BOSS_BOMBARD: {
                for (int i = 0; i < 4 + extra * 2; i++)
                    warningPool(clamp(p.x + (random.nextFloat() - .5f) * 300, 60, 1060),
                            clamp(p.y + (random.nextFloat() - .5f) * 220, 65, 555), 52, 0, 1f + i * .08f, -1);
                break;
            }
            case BOSS_SPIRAL: {
                int n = 22 + extra * 8;
                float off = tick * .23f;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, off + i * (float) Math.PI * 2 / n, 150 + (i % 3) * 40, 14, true, -1, e);
                break;
            }
            case BOSS_SUMMON: {
                for (int j = 0; j < 2 + extra && enemies.size() + pendingEnemies.size() < MAX_ENEMIES; j++) {
                    Enemy add = new Enemy();
                    add.id = allocateId();
                    add.type = 9;
                    add.behavior = B_SWARM;
                    add.x = 140 + j * 120;
                    add.y = 150 + (j % 2) * 60;
                    add.r = 13;
                    add.maxHp = add.hp = 55 * Balance.hpFactor(this);
                    add.speed = 130;
                    add.stage = -1;
                    add.telegraph = .8f;
                    add.summoned = true;
                    pendingEnemies.add(add);
                }
                break;
            }
            case BOSS_FIELD: {
                for (int i = 0; i < 3 + extra; i++) {
                    Hazard h = new Hazard();
                    h.id = allocateId();
                    h.kind = 3;
                    h.x = clamp(180 + random.nextFloat() * 760, 80, 1040);
                    h.y = clamp(140 + random.nextFloat() * 340, 90, 540);
                    h.r = 56;
                    h.clock = .8f + i * .2f;
                    h.active = -1;
                    hazards.add(h);
                }
                break;
            }
            case BOSS_LASER: {
                float a = aim;
                int n = 14 + extra * 6;
                for (int i = 0; i < n; i++)
                    shot(e.x, e.y, a + (float) Math.sin(i * .3f) * .1f, 300, 9, true, -1, e);
                warningPool(e.x, e.y, 40, 0, .5f, -1);
                break;
            }
            default: { // BOSS_BLINK
                for (int i = 0; i < 3 + extra; i++) {
                    float a = random.nextFloat() * 6.2832f;
                    float dd = 90 + random.nextFloat() * 130;
                    float nx = clamp(p.x + (float) Math.cos(a) * dd, 70, 1050);
                    float ny = clamp(p.y + (float) Math.sin(a) * dd, 80, 540);
                    warningPool(nx, ny, 52, 4, .6f, -1);
                }
                float a2 = random.nextFloat() * 6.2832f;
                e.x = clamp(e.x + (float) Math.cos(a2) * 160, 80, 1040);
                e.y = clamp(e.y + (float) Math.sin(a2) * 120, 90, 530);
                effect(e.x, e.y, 60, 6, 0xffbda5ed, .8f);
                break;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  弹幕 / 伤害
    // ══════════════════════════════════════════════════════════════

    private void warningPool(float x, float y, float r, int kind, float delay, int owner) {
        if (hazards.size() >= 30) return;
        Hazard h = new Hazard();
        h.id = allocateId();
        h.x = x; h.y = y; h.r = r; h.kind = kind;
        h.owner = owner;          // v2.0：记录布置者，玩家地雷只伤敌人
        h.clock = delay;
        h.active = -1;
        hazards.add(h);
    }

    private Shot shot(float x, float y, float angle, float speed, float damage, boolean hostile,
                      int owner, Enemy source) {
        if (shots.size() >= MAX_SHOTS) return null;
        Shot s = new Shot();
        s.id = allocateId();
        s.enemy = hostile;
        s.owner = owner;
        s.x = x; s.y = y;
        speed *= hostile ? Balance.PACE[contract] : 1;
        s.vx = (float) Math.cos(angle) * speed;
        s.vy = (float) Math.sin(angle) * speed;
        s.r = hostile ? 6 : 4;
        s.damage = damage;
        s.life = hostile ? 7 : 2;
        if (hostile && source != null && source.elite) s.damage *= 1.2f;
        shots.add(s);
        return s;
    }

    public void shoot(float x, float y, float angle, float speed, float damage, boolean hostile, int owner) {
        shot(x, y, angle, speed, damage, hostile, owner, null);
    }

    private Player nearestLiving(float x, float y) {
        Player best = null;
        float bestScore = Float.MAX_VALUE;
        for (Player p : players)
            if (p.connected && !p.down) {
                // v2.0 F_TAUNT：带嘲讽的玩家对敌人有更高的锁定权重（等效拉近距离）
                float nd = distance(x, y, p.x, p.y) / tauntWeight(p);
                if (nd < bestScore) { bestScore = nd; best = p; }
            }
        return best;
    }

    /**
     * 弹丸更新。
     * <p><b>遍历方式</b>：用索引循环，循环上界在开始时固定为当时的 {@code shots.size()}。
     * 这样：
     * <ul>
     *   <li>本帧新生成的弹丸（分裂弹、二段弹）会追加到列表尾部，<b>不会</b>在同一帧被再次处理
     *       —— 避免"分裂弹立刻命中、连锁分裂"的爆炸性递归；</li>
     *   <li>不再使用 {@code Iterator}，因此也不需要 {@code pendingShots} 这个中间列表，
     *       读档时不会再因为漏存中间态而破坏确定性。</li>
     * </ul>
     * 移除用"标记 + 尾部压缩"代替 {@code it.remove()}。
     */
    private void updateShots(float dt) {
        int count = shots.size();
        for (int index = 0; index < count; index++) {
            Shot s = shots.get(index);
            if (s.life <= 0f && s.hitCount < 0) continue;   // 已被标记移除的占位
            float ox = s.x, oy = s.y;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.life -= dt;
            if (s.homing > 0 && !s.enemy) {
                Enemy t = nearestEnemy(s.x, s.y, true);
                if (t != null) {
                    float d = Math.max(1, distance(s.x, s.y, t.x, t.y));
                    float sp = (float) Math.hypot(s.vx, s.vy);
                    s.vx = s.vx * .90f + (t.x - s.x) / d * sp * .10f;
                    s.vy = s.vy * .90f + (t.y - s.y) / d * sp * .10f;
                }
            }
            boolean remove = s.life <= 0 || s.x < 26 || s.x > 1094 || s.y < 26 || s.y > 594;
            if (!remove && blocked(s.x, s.y, s.r)) {
                if (s.ricochet > 0 && !s.enemy) {
                    // 简单弹射：反转一个轴向
                    if (blocked(s.x, oy, s.r)) s.vx = -s.vx;
                    else s.vy = -s.vy;
                    s.x = ox; s.y = oy;
                    s.ricochet--;
                    s.lastHit = -1;
                } else remove = true;
            }
            if (!remove && s.enemy) {
                for (Player p : players)
                    if (p.connected && !p.down
                            && segmentDistance(ox, oy, s.x, s.y, p.x, p.y) < s.r + 15) {
                        hurt(p, s.damage);
                        remove = true;
                        break;
                    }
            }
            if (!remove && !s.enemy) {
                Player owner = player(s.owner);
                for (Enemy e : enemies)
                    if (e.hp > 0 && !alreadyHit(s, e.id)
                            && segmentDistance(ox, oy, s.x, s.y, e.x, e.y) < e.r + s.r) {
                        s.hits[Math.min(9, s.hitCount++)] = e.id;
                        s.lastHit = e.id;
                        boolean chilling = s.slow > 0 || (s.element & 3) == 2
                                || owner != null && (owner.synergyMask & (1 << 2)) != 0;
                        if (owner != null && chilling && e.burn > 0 && owner.reactionCd <= 0) {
                            e.burn = 0;
                            owner.reactionCd = .8f;
                            owner.reactions++;
                            damage(e, s.damage * .55f, owner.id);
                            effect(e.x, e.y, 76, 8, 0xffded0ec, .6f);
                            for (Enemy other : enemies)
                                if (other != e && other.hp > 0 && distance(e.x, e.y, other.x, other.y) < 88)
                                    damage(other, s.damage * .3f, owner.id);
                        }
                        if (chilling) e.slow = Math.max(e.slow, 1.5f);
                        // #6 霜封：不改变 chilling 判定，只按弹丸自己的时长补一层减速。
                        // 与上面两行并存时取较长者，因此"原本就会冻结"的 build 也不会被削弱。
                        if (s.slowSeconds > 0f)
                            e.slow = Math.max(e.slow, s.slowSeconds);
                        if (owner != null) {
                            if (owner.armorBreak > 0 && e.type >= 3)
                                e.armor = Math.min(.24f, e.armor + .06f * owner.armorBreak);
                            if ((owner.synergyMask & 1) != 0 || (s.element & 3) == 1) {
                                e.burn = Math.max(e.burn, 3f);
                                e.owner = owner.id;
                            }
                            if (owner.has(Content.F_VOLT) && random.nextFloat() < .12f)
                                lightningChain(owner, e, 2, s.damage * .5f);
                            if (owner.stunChance > 0 && random.nextFloat() < Math.min(.45f, owner.stunChance * .10f))
                                e.stun = Math.max(e.stun, .5f);
                            if (owner.knockback > 0) {
                                float dd = Math.max(1, distance(owner.x, owner.y, e.x, e.y));
                                moveEnemy(e, (e.x - owner.x) / dd * 5f * owner.knockback,
                                        (e.y - owner.y) / dd * 5f * owner.knockback);
                            }
                            if (owner.execute > 0 && e.bossIndex < 0 && e.hp < e.maxHp * .12f)
                                damage(e, e.hp, owner.id);
                        }
                        // 霜星：命中已减速目标时额外 18% 伤害，让减速成为真实输出手段。
                        float dmg = s.damage * (1 + e.armor) * (chilling && e.slow > 0 ? 1.18f : 1f);
                        // v2.1 跨流派组合 0「焚寂」（余烬 + 虚空）：对灼烧中的敌人 +25% 伤害。
                        if (owner != null && owner.cross(0) && e.burn > 0f) dmg *= 1.25f;
                        // v2.1 跨流派组合 1「霜雷」（霜星 + 雷光）：命中减速目标有 22% 概率连锁闪电。
                        if (owner != null && owner.cross(1) && e.slow > 0f
                                && random.nextFloat() < .22f)
                            lightningChain(owner, e, 2, dmg * .45f);
                        if (e.shield > 0) {
                            float absorbed = Math.min(e.shield, dmg);
                            e.shield -= absorbed;
                            dmg -= absorbed;
                        }
                        damage(e, dmg, s.owner);
                        // v2.1：机制钩子 H_HIT 的**弹丸上下文**版本。
                        // damage() 里的 H_HIT 拿不到 Shot，凡是需要改弹丸本身的机制
                        // （回旋镖改写 vx/vy 与命中表、弹射、分裂）都会被 `s == null` 提前 return。
                        // 这里在弹丸命中点补一次，把 s 真正传进去。
                        // 注意：为避免同一击触发两次，damage() 内的 H_HIT 只服务于
                        // 「没有弹丸上下文」的伤害源（灼烧、光环、爆炸等）。
                        if (owner != null && dmg > 0f)
                            applyMechanic(owner, H_HIT, e, s, dmg, 0f);
                        if (s.crit) effect(e.x, e.y, 40, 4, 0xffffe58f, .3f);
                        if (s.explode > 0) {
                            effect(e.x, e.y, 64, 2, 0xffefbd78, .28f);
                            for (Enemy other : enemies)
                                if (other != e && other.hp > 0 && distance(other.x, other.y, e.x, e.y) < 70)
                                    damage(other, s.damage * .38f, s.owner);
                        }
                        // v2.0 F_SPLITSHOT：命中后再分出两枚斜向弹丸，扩大清场覆盖。
                        // 注意：这是"覆盖加成"而非"单体 DPS 加成"——正对单体木桩时不会提升，
                        // 这也是为什么它需要配合走位/聚怪，而不是纯粹数值增强。
                        int splitTotal = s.splitCount;
                        if (splitTotal <= 0 && s.owner >= 0) {
                            Player sp0 = player(s.owner);
                            if (sp0 != null && sp0.splitShot > 0 && !s.enemy) splitTotal = sp0.splitShot;
                        }
                        if (splitTotal > 0 && !s.crit) {
                            s.splitCount = splitTotal - 1;
                            for (int k = 0; k < 2 && shots.size() < MAX_SHOTS; k++) {
                                Shot c = new Shot();
                                c.id = allocateId();
                                c.owner = s.owner;
                                c.x = e.x; c.y = e.y;
                                float base = (float) Math.atan2(s.vy, s.vx);
                                float a = base + (k == 0 ? .7f : -.7f);
                                float sp = (float) Math.hypot(s.vx, s.vy) * .9f;
                                c.vx = (float) Math.cos(a) * sp;
                                c.vy = (float) Math.sin(a) * sp;
                                c.damage = s.damage * .55f;
                                c.r = 4;
                                c.life = .7f;
                                c.element = s.element;
                                // 直接追加到 shots：索引循环的上界已固定，本帧不会再次处理它
                                shots.add(c);
                            }
                        }
                        if (s.pierce-- <= 0 || s.hitCount >= 10) remove = true;
                        break;
                    }
                if (!remove && s.lastHit < 0)
                    for (Prop p : props)
                        if (p.type == 0 && !p.opened
                                && segmentDistance(ox, oy, s.x, s.y, p.x, p.y) < 20 + s.r) {
                            p.hp -= s.damage;
                            if (p.hp <= 0) {
                                p.opened = true;
                                coins += 2 + random.nextInt(4);
                                effect(p.x, p.y, 30, 3, 0xffccad83, .4f);
                            }
                            remove = true;
                            break;
                        }
            }
            if (remove) s.hitCount = -1;    // 标记移除；循环结束后统一压缩
        }
        // 尾部压缩：一次性移除被标记的弹丸（保持其余顺序稳定）
        int write = 0;
        for (int read = 0; read < shots.size(); read++) {
            Shot s = shots.get(read);
            if (s.hitCount < 0) continue;
            if (write != read) shots.set(write, s);
            write++;
        }
        while (shots.size() > write) shots.remove(shots.size() - 1);
    }

    private boolean alreadyHit(Shot s, int id) {
        for (int j = 0; j < Math.min(10, s.hitCount); j++) if (s.hits[j] == id) return true;
        return false;
    }

    private void damage(Enemy e, float amount, int ownerId) {
        if (e.hp <= 0 || !Float.isFinite(amount)) return;
        e.hurtClock = 0f;                       // v2.1：任何一次受击都会打断护盾再生计时
        Player p = player(ownerId);
        if (e.behavior == B_SHIELD && p != null && e.stun <= 0) {
            // ── v2.2 修复：正面减伤此前拿像素坐标当弧度用 ────────────────
            // 原代码是 `Math.atan2(Math.sin(ang - e.tx), ...)`，而 e.tx/e.ty 是
            // **目标点坐标**（45..1075 / 48..572 像素），不是朝向角。于是
            // `ang - e.tx` 是个几百弧度的数，Math.atan2 回绕后基本落在任意相位，
            // 减伤只在 e.tx≈100 附近的极小窗口里偶然生效——「正面免伤」等于没写。
            //
            // 正确语义：持盾兵**面朝玩家**（它总是转向最近的目标），
            // 所以"正面"就是它朝向玩家的方向。命中若来自正面（夹角 < 1.2 rad），
            // 也就是**攻击者与它面向的方向一致**时减伤。
            // 由于这里的伤害来源就是玩家本人，等价于「玩家站在它的正面」。
            float facing = (float) Math.atan2(p.y - e.y, p.x - e.x);
            // 用 e.tx/e.ty 记录的"上一次瞄准点"作为它当前朝向，避免被同帧的
            // 伤害回调打乱（伤害可能在 doEnemyAttack 之前发生）。
            float aim = (float) Math.atan2(e.ty - e.y, e.tx - e.x);
            float diff = Math.abs((float) Math.atan2(
                    Math.sin(facing - aim), Math.cos(facing - aim)));
            if (diff < 1.2f) amount *= .45f;
        }
        // v2.1：护盾词缀（affix 4）。护盾是一层独立的血，先于 guard 被消耗——
        // 与 affix 1「减伤」的区别就在于此：护盾可以被打破，破了就有一段时间没有保护。
        if (e.shield > 0f) {
            float absorbed = Math.min(e.shield, amount);
            e.shield -= absorbed;
            amount -= absorbed;
            if (e.shield <= 0f) {
                e.shieldClock = 0f;
                effect(e.x, e.y, e.r * 2f, 5, 0xff9fd7e6, .5f);   // 破盾反馈
            }
        }
        if (e.guard > 0 && amount > 0f) {
            float absorbed = Math.min(e.guard, amount * .7f);
            e.guard -= absorbed;
            amount -= absorbed;
        }
        if (p != null && p.has(Content.F_VOID)) amount *= 1.12f;
        e.hp -= amount;
        // v2.0 F_LIFESTEAL：命中即按实际伤害回血（与"击杀回血"的 leech 不同）
        if (p != null && amount > 0f) applyLifeSteal(p, amount);
        e.hit = .14f;
        e.flash = .1f;
        // ── v2.1 修复：机制钩子 H_HIT 此前从未被调用 ────────────────────
        // applyMechanic 的设计约定有 6 个钩子（fire/hit/kill/dash/hurt/tick），
        // 但全项目只有 H_DASH 一个调用点。这意味着凡是挂在 H_HIT 上的进化
        // （回旋镖、连击、二段判定、状态反应、剥离…）**一条都不会执行**。
        // 这里补上命中钩子；value 传本次实际伤害。
        if (p != null && amount > 0f) applyMechanic(p, H_HIT, e, null, amount, 0f);
        if (e.hp > 0) return;
        // v2.0 F_TELEPORT：击杀后短距瞬移追击下一个目标
        if (p != null) teleportStrike(p);
        kills++;
        combo++;
        comboClock = 4;
        if (p != null) {
            p.kills++;
            if (!p.down) {
                int leechAmount = Math.min(p.leech, (int) p.leechBudget);
                if (leechAmount > 0) {
                    doctrineHeal(p, leechAmount);
                    p.leechBudget = Math.max(0, p.leechBudget - p.leech);
                }
            }
            p.ultCharge = Math.min(100, p.ultCharge + gainUlt(p, 1));
            // ── v2.0 支柱 2 击杀类钩子 ──────────────────────────────
            onDoctrineKill(p, e);
            // v2.1：机制钩子 H_KILL（M13 击杀堆叠、M12 吸血、M10 剥离的击杀分支…）
            applyMechanic(p, H_KILL, e, null, 0f, 0f);
        }
        int coinBase = e.bossIndex >= 0 ? 0 : e.elite ? 5 : Enemies.COINS[Math.max(0, Math.min(Enemies.COUNT - 1, e.type))];
        // v2.0 词条：精英狂潮（精英星尘 ×1.8）+ 星尘总乘区（商店通胀 / 无光视野）
        if (e.elite && !e.summoned) coinBase = Math.round(coinBase * Balance.PACT_ELITE_COIN);
        coinBase = Math.round(coinBase * Balance.pactCoinMul(this));
        coins += coinBase;
        if (combo >= 5 && kills % 5 == 0) { coins += 4; notice = "连击奖励 · 星尘 +4"; }
        if (e.affix == 2) warningPool(e.x, e.y, 58, 0, 1.1f, -1);
        // v2.1 跨流派组合 0「焚寂」（余烬 + 虚空）：灼烧中的敌人死亡时炸开。
        if (p != null && p.cross(0) && e.burn > 0f && e.bossIndex < 0) {
            for (Enemy other : enemies)
                if (other != e && other.hp > 0 && distance(e.x, e.y, other.x, other.y) < 96) {
                    damage(other, e.maxHp * .22f, p.id);
                    other.burn = Math.max(other.burn, 2.5f);
                    other.owner = p.id;
                }
            effect(e.x, e.y, 96, 2, 0xffff9a6b, .38f);
        }
        // v2.0 词条「亡语爆裂」：非首领敌人死亡时原地爆炸
        if (hasPact(Balance.PACT_DEATH_BLAST) && e.bossIndex < 0) {
            float blast = Math.min(Balance.PACT_BLAST_CAP, e.maxHp * Balance.PACT_BLAST_RATIO);
            for (Player q : players)
                if (q.connected && !q.down
                        && distance(q.x, q.y, e.x, e.y) < Balance.PACT_BLAST_RADIUS) hurt(q, blast);
            effect(e.x, e.y, Balance.PACT_BLAST_RADIUS, 2, 0xffffa37b, .32f);
        }
        if (e.behavior == B_SPLITTER && !e.splitDone && e.bossIndex < 0
                && enemies.size() + pendingEnemies.size() < MAX_ENEMIES - 2) {
            for (int j = 0; j < 2; j++) {
                Enemy child = new Enemy();
                child.id = allocateId();
                child.type = e.type;
                child.behavior = B_SWARM;
                child.stage = -1;
                child.x = e.x + (j == 0 ? -16 : 16);
                child.y = e.y;
                child.r = Math.max(8, e.r * .6f);
                child.speed = e.speed * 1.25f;
                child.maxHp = child.hp = Math.max(8, e.maxHp * .28f);
                child.telegraph = .5f;
                child.summoned = true;
                pendingEnemies.add(child);
            }
        }
        effect(e.x, e.y, e.r * 2f, 3, e.bossIndex >= 0 ? 0xffee9cd8 : 0xffa9eacc, .5f);
        if (gems.size() < MAX_GEMS) {
            Gem gem = new Gem();
            gem.id = allocateId();
            gem.x = e.x;
            gem.y = e.y;
            gem.value = e.bossIndex >= 0 ? 160 : e.elite ? 34 : Enemies.XP[Math.max(0, Math.min(Enemies.COUNT - 1, e.type))];
            gems.add(gem);
        }
        if (e.bossIndex >= 0) {
            // 注意：bossesDefeated 只在 clearRoom() 里按「首领房」计一次。
            // 旧代码在这里也 ++，导致 15 站 5 个首领房被记成 10，
            // 直接把 dustReward(bosses*45) 与 markReward(bosses*3) 翻倍。
            markBossKill(e.bossIndex);
            effect(e.x, e.y, 260, 6, 0xffee9cd8, 1.6f);
        }
    }

    /** 记录首领击杀到元进度（由 Session 读取）。 */
    public final int[] bossKillsRun = new int[Bosses.COUNT];

    private void markBossKill(int idx) {
        if (idx >= 0 && idx < Bosses.COUNT) bossKillsRun[idx]++;
    }

    private void flushDeaths() {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); )
            if (it.next().hp <= 0) it.remove();
        enemies.addAll(pendingEnemies);
        pendingEnemies.clear();
    }

    private void hurt(Player p, float amount) {
        if (p.down || !p.connected || p.invuln > 0) return;
        if (p.relics[Relics.CURSED] > 0) amount *= 1 + p.relics[Relics.CURSED] * .2f;
        if ((p.synergyMask & (1 << 3)) != 0) amount *= .82f;
        // v2.1 跨流派组合 3「生霜」（生命 + 霜星）：承伤 -8%，受击冻结近身攻击者。
        if (p.cross(3)) {
            amount *= .92f;
            for (Enemy e : enemies)
                if (e.hp > 0 && distance(p.x, p.y, e.x, e.y) < 96) {
                    e.slow = Math.max(e.slow, 2.2f);
                    e.stun = Math.max(e.stun, .25f);
                }
            effect(p.x, p.y, 96, 5, 0xff9fd7e6, .3f);
        }
        // ── v2.0 支柱 2 #5 焚身 / #16 玻璃大炮：承受伤害乘区 ──────────
        // 两条牌一个在主钩子位、一个在副钩子位，因此这里必须各判一次。
        // 放在护盾吸收之前：护盾挡的是"被放大后"的伤害，与遗物 CURSED 同层。
        amount *= doctrineTakenDamageMul();
        // 护盾（#7 寒冰壁垒给的层也走这里）：不再要求 F_SHIELD，
        // 只要真的有盾就吸收，否则"每层开始 30% 护盾"会变成纯装饰。
        if (p.shield > 0 && amount > 0f) {
            float absorbed = Math.min(p.shield, amount);
            p.shield -= absorbed;
            amount -= absorbed;
            effect(p.x, p.y, 30, 8, 0xff9fc5d8, .3f);
            if (amount <= 0) { p.invuln = .25f; return; }
        }
        // 契约承伤倍率只在这里应用一次；调用方一律传原始伤害值。
        // v2.0 词条「遗物洪流」额外放大承伤。
        // v2.2：元进度「承受伤害 -3%/级」（节点 1）。它在契约倍率之上再乘一次，
        // 与遗物 CURSED、词条、天赋牌都是独立乘区，不会被 recompute 冲掉。
        float metaArmorMul = 1f - Math.max(0f, Math.min(.15f, p.metaArmor));
        float taken = Math.min(p.hp, amount * Balance.DAMAGE[contract]
                * Balance.pactDamageMul(this) * metaArmorMul);
        p.hp -= taken;
        // v2.0 词条「治疗禁绝」：一切治疗失效，改为伤害的 2% 转化为生命。
        // 注意：这是词条自身的补偿机制，必须绕过 heal() 的禁疗判定。
        if (hasPact(Balance.PACT_NO_HEAL)) {
            int siphon = Math.round(taken * Balance.PACT_NO_HEAL_LEECH);
            if (siphon > 0) p.hp = Math.min(p.maxHp, p.hp + siphon);
        }
        p.invuln = .5f;
        damageTaken += taken;
        effect(p.x, p.y, 30, 1, 0xffff8585, .28f);
        if (p.has(Content.F_THORNS) && p.thorns > 0) {
            for (Enemy e : enemies)
                if (distance(p.x, p.y, e.x, e.y) < 90) damage(e, p.thorns, p.id);
        }
        // ── v2.0 支柱 2：受伤触发的两个钩子（用"实际承伤"而不是面板值）──
        if (taken > 0f) {
            // v2.1：机制钩子 H_HURT（M3 低血动态、M14 荆棘回响的承伤转治疗…）
            applyMechanic(p, H_HURT, null, null, taken, 0f);
            // #10 荆棘誓约：反弹 taken 的 35% 给 90 范围内的敌人。
            if (mainHook(DoctrineHooks.H_REFLECT) || sideHook(DoctrineHooks.H_REFLECT))
                reflectDamage(p, taken * doctrineReflectRatio());
            // #5 焚身：在自身周围引爆火环（半径 param[0]，伤害 = 8% 最大生命）。
            if (mainHook(DoctrineHooks.H_HURT_FIRE_RING)) {
                float radius = Math.max(20f, dParam(0));
                float burst = p.maxHp * .08f;
                for (Enemy e : enemies)
                    if (e.hp > 0 && distance(p.x, p.y, e.x, e.y) < radius + e.r)
                        damage(e, burst, p.id);
                effect(p.x, p.y, radius, 2, 0xffffa463, .38f);
            }
        }
        if (p.hp <= 0) {
            p.down = true;
            p.dx = p.dy = 0;
            p.revive = 0;
            notice = soloRun
                    ? (freeRevivesUsed < freeReviveCount ? "你倒下了 · 星火会把你拉回来" : "你倒下了")
                    : "队友倒下 · 靠近 3 秒可以救援";
        }
    }

    /** 元进度提供的免费复活次数，由 Session 注入。 */
    public int freeReviveCount, freeRevivesUsed;

    private void revives(float dt) {
        for (Player p : players) {
            if (!p.down || !p.connected) continue;
            if (soloRun && freeRevivesUsed < freeReviveCount) {
                p.revive += dt;
                if (p.revive >= 2.5f) {
                    freeRevivesUsed++;
                    p.down = false;
                    p.hp = p.maxHp * .55f;
                    p.invuln = 3;
                    p.revive = 0;
                    effect(p.x, p.y, 160, 6, 0xffefc785, 1.2f);
                    notice = "星火重燃 · 免费复活 " + freeRevivesUsed + " / " + freeReviveCount;
                }
            } else {
                boolean help = false;
                for (Player q : players)
                    if (q != p && q.connected && !q.down && distance(p.x, p.y, q.x, q.y) < 70) help = true;
                p.revive = Math.max(0, p.revive + (help ? dt : -dt * .25f));
                if (p.revive >= 3) {
                    p.down = false;
                    p.hp = p.maxHp * .45f;
                    p.revive = 0;
                    p.invuln = 2;
                    notice = p.name + " 已被救起";
                }
            }
        }
    }

    private void gainXp(float amount, Player owner) {
        float gain = amount * (1 + (owner == null ? 0 : owner.xpBonus));
        xp += gain;
        xpTotal += gain;
        int guard = 0;
        while (xp >= xpGoal && guard++ < 32) {
            xp -= xpGoal;
            xpGoal *= 1.28f;
            level++;
            // 升级成长不再直接乘裸字段（那会被 recompute 抹掉），
            // 而是全部由已序列化的 level 推导，这里只重建派生属性。
            for (Player p : players) {
                recompute(p);
                if (!p.down) doctrineHeal(p, 6);
            }
            notice = "等级提升 Lv." + level + " · 伤害 +3.5%，生命 +2";
            for (Player p : players) p.ultCharge = Math.min(100, p.ultCharge + gainUlt(p, 4));
            // ── v2.0 支柱 2 #4 献祭契约：每次升级额外一次三选一 ────────
            // 升级发生在 FIGHT 阶段，不能当场开 CHOICE，先累积，
            // 由 prepareChoice() 在下次三选一时并入 rewardsLeft。
            if (sideHook(DoctrineHooks.H_LEVELUP_EXTRA_CHOICE)
                    || mainHook(DoctrineHooks.H_LEVELUP_EXTRA_CHOICE)) {
                int extra = Math.round(mainHook(DoctrineHooks.H_LEVELUP_EXTRA_CHOICE)
                        ? dParam(0) : dParam(2));
                if (extra > 0) doctrineBonusChoices += extra;
            }
        }
    }

    private void updateGems(float dt) {
        for (Iterator<Gem> it = gems.iterator(); it.hasNext(); ) {
            Gem gem = it.next();
            Player p = nearestLiving(gem.x, gem.y);
            if (p == null) continue;
            float d = distance(gem.x, gem.y, p.x, p.y);
            if (d < 26) {
                gainXp(gem.value, p);
                coins += (int) (gem.value * .08f * (1 + p.bonusCoin));
                it.remove();
            } else if (d < p.magnet) {
                float travel = Math.min(d, 340 * dt);
                gem.x += (p.x - gem.x) / d * travel;
                gem.y += (p.y - gem.y) / d * travel;
            }
        }
    }

    private int roomKills() { return kills - roomStartKills; }

    /**
     * 清空当前房间并进入结算。
     *
     * <p><b>幂等保护（v2.2）</b>：{@code update()} 末尾是
     * {@code updateStall(dt); checkObjective();} 两句连着的调用。
     * 当房间因"35 秒僵持"被强制清空时，{@code updateStall} 已经调过
     * {@link #clearRoom()}，紧接着 {@code checkObjective} 看到
     * {@code spawned >= target && enemies.isEmpty()} 会**再调一次**——
     * 星尘与尘按两倍结算、{@code prepareChoice} 被覆盖一次。
     * 实测 432 个房间里有 7 个命中（seed=29 第一房 80 → 281 星尘）。
     *
     * <p>由于第一次 clearRoom 必然把 phase 切出 FIGHT，这里用一个入口守卫即可：
     * 已经不在战斗阶段就说明本房间已经结算过。
     */
    private void clearRoom() {
        if (phase != FIGHT) return;
        Player owner = players.isEmpty() ? null : players.get(0);
        for (Gem gem : gems) gainXp(gem.value, owner);
        gems.clear();
        enemies.clear();
        pendingEnemies.clear();
        shots.clear();

        turrets.clear();
        minions.clear();
        int reward = Math.round((Balance.clearCoins(roomType) + Balance.corruptionCoins(corruption)) * Balance.pactCoinMul(this));
        coins += reward;
        dustEarned += reward * .35f;
        if (roomType == ROOM_BOSS) {
            bossesDefeated++;
            // ── v2.0 支柱 2 #9 生命开局：每层结束不再自动回复 ────────
            // 引擎里唯一"过层自动回复"就是首领房清场时的 20% 回血；
            // 只停这一条，击杀吸血 / 遗物再生 / 营火治疗都照常——
            // 否则这张牌的代价会把生命流派直接玩死。
            boolean noFloorHeal = sideHook(DoctrineHooks.H_NO_FLOOR_HEAL)
                    || mainHook(DoctrineHooks.H_NO_FLOOR_HEAL);
            if (!noFloorHeal)
                for (Player p : players) if (!p.down) doctrineHeal(p, p.maxHp * .2f);
        }
        if (mode != MODE_ENDLESS && roomsVisited >= roomsTotal - 1) {
            roomsVisited++;
            phase = WON;
            notice = "远征完成 · 星光已被带回";
            return;
        }
        int extra = 0;
        if (roomType == ROOM_BOSS) for (Player p : players) extra = Math.max(extra, p.bossLoot);
        prepareChoice(Balance.rewardDrafts(roomType) + Math.min(2, extra) + Balance.pactExtraDrafts(this));
    }

    public Effect effect(float x, float y, float r, int type, int color, float life) {
        if (effects.size() >= MAX_EFFECTS) effects.remove(0);
        Effect e = new Effect();
        e.id = allocateId();
        e.x = x; e.y = y; e.r = r;
        e.type = type; e.color = color;
        e.life = e.maxLife = life;
        effects.add(e);
        return e;
    }

    // ══════════════════════════════════════════════════════════════
    //  碰撞与移动
    // ══════════════════════════════════════════════════════════════

    /**
     * 玩家移动与四边边界。
     *
     * <p><b>v2.0 词条「房间压缩」</b>：开启后四边各收窄 {@link Balance#PACT_COLLAPSE_INSET}
     * 像素。此前这个常量声明了却零消费——它必须影响<b>可走区域</b>而不是画一条线，
     * 否则"走位空间被压缩"的承诺就是假的。
     *
     * <p>实现只钳制<b>玩家</b>：敌人仍然可以在整场活动（它们本来就是从墙外压进来的），
     * 于是收窄后的边带变成"只有你能被夹住、它们却进得来"的高风险区，
     * 这正是词条想要的手感。四条边界统一走 {@link #collapseInset()} 推导，
     * 不新增可序列化字段（读档后由 pactMods 重新推导，天然一致）。
     */
    private void move(Player p, float dx, float dy) {
        if (!blocked(p.x + dx, p.y, 16)) p.x = clamp(p.x + dx, boundLeft(), boundRight());
        if (!blocked(p.x, p.y + dy, 16)) p.y = clamp(p.y + dy, boundTop(), boundBottom());
    }

    /** 可走区域左边界（默认 40；「房间压缩」时 + PACT_COLLAPSE_INSET）。 */
    private float boundLeft() {
        return 40f + collapseInset();
    }

    /** 可走区域右边界（默认 1080）。 */
    private float boundRight() {
        return 1080f - collapseInset();
    }

    /** 可走区域上边界（默认 44）。 */
    private float boundTop() {
        return 44f + collapseInset();
    }

    /** 可走区域下边界（默认 577）。 */
    private float boundBottom() {
        return 577f - collapseInset();
    }

    /** 「房间压缩」的四边收窄量（未开启 = 0）。 */
    private float collapseInset() {
        return hasPact(Balance.PACT_COLLAPSE) ? Balance.PACT_COLLAPSE_INSET : 0f;
    }

    private void moveEnemy(Enemy e, float dx, float dy) {
        if (!blocked(e.x + dx, e.y, e.r)) e.x = clamp(e.x + dx, 34, 1086);
        if (!blocked(e.x, e.y + dy, e.r)) e.y = clamp(e.y + dy, 36, 584);
    }

    /**
     * 点 (x,y)、半径 r 是否与任意地形块相交。
     * <p><b>必须保持索引循环</b>：本方法位于 move()/lineClear()/nearestEnemy() 之下，
     * 实测 1 900–2 700 次/帧；for-each 每次都会 new 一个 ArrayList$Itr（16 B），
     * 是整局最大的单点分配源（EA 关闭时 ~39 kB/帧，占全部分配的 92%）。
     * 索引版零分配且语义完全等价。
     */
    private boolean blocked(float x, float y, float r) {
        for (int i = 0, n = blocks.size(); i < n; i++) {
            Block b = blocks.get(i);
            if (x + r > b.x && x - r < b.x + b.w && y + r > b.y && y - r < b.y + b.h) return true;
        }
        return false;
    }

    private boolean lineClearRadius(float x, float y, float tx, float ty, float radius) {
        int count = (int) (distance(x, y, tx, ty) / 12) + 1;
        for (int j = 1; j < count; j++)
            if (blocked(x + (tx - x) * j / count, y + (ty - y) * j / count, radius)) return false;
        return true;
    }

    private boolean lineClear(float x, float y, float tx, float ty) {
        int count = (int) (distance(x, y, tx, ty) / 14) + 1;
        for (int j = 1; j < count; j++)
            if (blocked(x + (tx - x) * j / count, y + (ty - y) * j / count, 3)) return false;
        return true;
    }

    private static float segmentDistance(float ax, float ay, float bx, float by, float x, float y) {
        float dx = bx - ax, dy = by - ay;
        float t = clamp(((x - ax) * dx + (y - ay) * dy) / Math.max(.001f, dx * dx + dy * dy), 0, 1);
        return distance(ax + dx * t, ay + dy * t, x, y);
    }

    public static float distance(float x, float y, float a, float b) {
        return (float) Math.hypot(x - a, y - b);
    }

    public static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    public int pendingEnemiesCount(){ return pendingEnemies.size(); }

    // ══════════════════════════════════════════════════════════════
    //  v2.0 支柱：F_* 标志位的真实消费点
    //  这些方法把 Content.F_* 里此前"声明了但零消费"的 9 个位
    //  接进战斗循环，让进化/遗物的机制承诺真正成立。
    // ══════════════════════════════════════════════════════════════

    /** 每帧处理玩家的周期性标志位效果（光环 / 减速领域 / 地雷 / 蓄力）。 */
    private void updatePactFlags(Player p, float dt) {
        // ── F_AURA：以玩家为中心的持续范围伤害 ──
        if (p.auraCount > 0) {
            p.auraClock -= dt;
            if (p.auraClock <= 0f) {
                p.auraClock = .5f;                       // 每 0.5 秒结算一次，避免每帧扫描
                float r = AURA_RADIUS * (1f + p.areaBonus) + (p.auraCount - 1) * 12f;
                float dmg = power(p) * AURA_RATIO;
                for (Enemy e : enemies)
                    if (e.hp > 0 && distance(p.x, p.y, e.x, e.y) < r + e.r)
                        damage(e, dmg, p.id);
                effect(p.x, p.y, r, 8, 0xffc2a7ff, .22f);
            }
        }
        // ── F_SLOWFIELD：以玩家为中心的持续减速领域 ──
        if (p.slowFieldCount > 0) {
            float r = SLOWFIELD_RADIUS * (1f + p.areaBonus) + (p.slowFieldCount - 1) * 14f;
            for (Enemy e : enemies)
                if (e.hp > 0 && distance(p.x, p.y, e.x, e.y) < r + e.r)
                    e.slow = Math.max(e.slow, .35f);     // 持续刷新，走出领域后自然衰减
        }
        // ── F_MINE：周期性在脚下布置地雷 ──
        if (p.mineCount > 0) {
            p.mineClock -= dt;
            if (p.mineClock <= 0f) {
                p.mineClock = MINE_INTERVAL;
                warningPool(p.x, p.y, MINE_RADIUS * (1f + p.areaBonus), 0, .55f, p.id);
            }
        }
        // ── F_CHARGE：静止蓄力，松开后强化下一发 ──
        if (p.chargeCount > 0) {
            boolean still = Math.abs(p.dx) + Math.abs(p.dy) < .15f;
            if (still) {
                p.chargeHold = Math.min(CHARGE_MAX, p.chargeHold + dt);
            } else if (p.chargeHold > 0f) {
                p.buff = Math.max(p.buff, .1f);
                p.buffKind = 1;
                p.chargeHold = 0f;
            }
        }
        // ── F_REGEN 的额外被动（FLAGS 位本身不带数值，给一点保底）──
        if (p.has(Content.F_REGEN) && p.bonusRegen <= 0f) {
            p.regenClock += dt;
            if (p.regenClock >= 2f) { p.regenClock = 0f; heal(p, 1f); }
        }
    }

    /** F_AURA 半径与每秒伤害比例（相对玩家基础伤害）。 */
    static final float AURA_RADIUS = 92f, AURA_RATIO = .34f;
    /** F_SLOWFIELD 半径。 */
    static final float SLOWFIELD_RADIUS = 118f;
    /** F_MINE 布设间隔与爆炸半径。 */
    static final float MINE_INTERVAL = 1.4f, MINE_RADIUS = 46f;
    /** F_MINE 爆炸伤害（相对玩家基础伤害）。 */
    static final float MINE_DAMAGE_RATIO = 1.6f;
    /** F_CHARGE 最长蓄力秒数。 */
    static final float CHARGE_MAX = 1.2f;
    /** F_CHARGE 满蓄力时的伤害加成。 */
    static final float CHARGE_DAMAGE = 1.1f;

    /**
     * F_TELEPORT：瞬移到目标附近并造成一次额外打击。
     * <p>由 {@link #damage} 在击杀时触发（与原描述"击杀后短距瞬移"一致）。
     * <p>防重入：内部那次伤害击杀目标后不会再连锁触发瞬移，避免长链递归。
     */
    private void teleportStrike(Player p) {
        if (p == null || !p.has(Content.F_TELEPORT) || teleportLock) return;
        Enemy target = null;
        float best = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.hp <= 0 || e.bossIndex >= 0) continue;
            float d = distance(p.x, p.y, e.x, e.y);
            if (d > 40f && d < 260f && d < best) { best = d; target = e; }
        }
        if (target == null) return;
        teleportLock = true;
        try {
            float nx = target.x - (target.x - p.x) * .25f;
            float ny = target.y - (target.y - p.y) * .25f;
            if (!blocked(nx, ny, 16)) { p.x = clamp(nx, 40, 1080); p.y = clamp(ny, 44, 577); }
            damage(target, power(p) * TELEPORT_BONUS, p.id);
            effect(p.x, p.y, 46, 8, 0xffbda5ed, .4f);
        } finally {
            teleportLock = false;
        }
    }

    /** 瞬移连锁防重入标志（单线程模拟，仅一帧内有效）。 */
    private boolean teleportLock;

    /** F_TELEPORT 附带的额外打击倍率。 */
    static final float TELEPORT_BONUS = .8f;

    /**
     * F_LIFESTEAL：命中时按伤害比例回血。
     * <p>与遗物 {@code leech}（击杀回血）不同，这是**命中即回**。
     */
    private void applyLifeSteal(Player p, float dealt) {
        if (p == null || p.lifeSteal <= 0 || dealt <= 0f || p.down) return;
        float amount = dealt * LIFESTEAL_RATIO * p.lifeSteal;
        if (amount > 0f) heal(p, amount);
    }

    /** F_LIFESTEAL 每层转化比例。 */
    static final float LIFESTEAL_RATIO = .035f;

    /**
     * F_TAUNT：让敌人优先锁定该玩家。
     * <p>在 {@link #nearestLiving} 里按权重选择；此处只提供权重。
     */
    private float tauntWeight(Player p) {
        return p != null && p.taunt > 0 ? 1f + p.taunt * .8f : 1f;
    }

    public void restored() {
        navigation.rebuild(blocks);
        pendingEnemies.clear();
        // 逐房间派生值不进存档，读档后按当前房型重建（#8 极寒迟滞）。
        roomTimeLimit = roomType == ROOM_CHALLENGE ? doctrineRoomTimeMul() : 1f;
        // v2.3 #2：lastOffer 是 transient，读档后为 -1。用"当前这一手"回填，
        // 这样读档后的第一次重掷仍然不会原样返还玩家屏幕上正看着的三张牌。
        // 只有当 offers 确实是一手有效遗物时才回填：玩家可能已经点了进化按钮
        // （offers 被清成 -1），此时没有"上一手"可记，留 -1 让去重自然失效即可。
        for (Player p : players) {
            if (p.offers[0] >= 0 && p.offers[1] >= 0 && p.offers[2] >= 0)
                System.arraycopy(p.offers, 0, p.lastOffer, 0, 3);
            else Arrays.fill(p.lastOffer, -1);
        }
        // 读档后用不捕获模式：面板已从存档恢复，不需要也不应该重新反解成长，
        // 否则浮点往返会产生极小的漂移，破坏"再次打包字节一致"。
        for (Player p : players) recompute(p, false);
    }

    /**
     * v2.0 P0-1：在外部（Session / Meta）直接写过裸面板字段之后调用。
     * <p>把元进度注入的加成捕获进成长增量，再安全重算面板。
     * <b>调用方约定</b>：{@code applyMetaBoosts} 末尾、以及 {@code startSolo} 之前各调用一次。
     * 重复调用是安全的（{@link #captureGrowth} 写绝对值，不累加）。
     */
    public void applyMetaBoostedStats() {
        for (Player p : players) recompute(p, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  v2.0 P0-6：进化机制原型分发（{@link Evolutions#MECHANIC}）
    //
    //  背景：36 条进化此前只有 FLAGS + 数值，{@code evoMechanic} 写了但从不被读，
    //  15 个机制原型全是死定义。这里给每条进化接上真正的状态机行为。
    //
    //  约定：
    //   * 钩子只有 6 个（fire/hit/kill/dash/hurt/tick），由战斗主循环调用；
    //   * {@code mechanicMask} 是位掩码，允许一个玩家同时持有多个原型（多条进化叠加）；
    //   * 所有随机性必须走 {@link #random}，不得引入 Math.random()，否则确定性测试崩；
    //   * 会改变战斗结果的状态全部进 Codec v8，读档后逐位恢复。
    // ══════════════════════════════════════════════════════════════

    /** 机制钩子（与 applyMechanic 的 hook 参数一一对应）。 */
    static final int H_FIRE = 0, H_HIT = 1, H_KILL = 2, H_DASH = 3, H_HURT = 4, H_TICK = 5;

    /** M2 每层连击的攻速增幅（规格建议值）。 */
    static final float COMBO_STEP = .04f;
    /** M2 连击断档时间：超过这个秒数没有再次命中同一目标就清零。 */
    static final float COMBO_WINDOW = 2.5f;

    /** 机制递归深度护栏：机制内的伤害可能再次触发机制，限制层数避免无限递归。 */
    private int mechanicDepth;

    static boolean hasMech(Player p, int m) {
        return p != null && m > 0 && m < 31 && (p.mechanicMask & (1 << m)) != 0;
    }

    /**
     * 机制分发入口。
     *
     * @param hook  {@link #H_FIRE} / {@link #H_HIT} / {@link #H_KILL} / {@link #H_DASH} / {@link #H_HURT} / {@link #H_TICK}
     * @param e     命中的敌人（HIT / KILL 时有值）
     * @param s     命中用的弹丸（HIT 时有值）
     * @param value 语义随钩子变化：HIT = 本次实际伤害；HURT = 实际承伤
     * @param dt    帧时长（TICK 用）
     */
    private void applyMechanic(Player p, int hook, Enemy e, Shot s, float value, float dt) {
        if (p == null || p.down || p.mechanicMask == 0) return;
        if (mechanicDepth > 2) return;              // 深度护栏
        mechanicDepth++;
        try {
            for (int idx = 0; idx < Evolutions.COUNT; idx++) {
                if (!p.evolved[idx]) continue;
                int m = Evolutions.mechanic(idx);
                if (m == 0) continue;               // 0 = 纯 FLAGS，战斗代码已消费
                switch (m) {
                    case 1:  mechBoomerang(p, hook, e, s, value, idx); break;
                    case 2:  mechCombo(p, hook, e, dt); break;
                    case 3:  mechLowHp(p, hook); break;
                    case 5:  mechSite(p, hook, dt, idx); break;
                    case 6:  mechSpread(p, hook, idx); break;
                    case 8:  mechSecondStrike(p, hook, e, s, value, idx); break;
                    case 9:  mechGlobal(p, hook, dt, idx); break;
                    case 10: mechDrain(p, hook, e, value, idx); break;
                    case 11: mechLink(p, hook, dt, idx); break;
                    case 12: mechReaction(p, hook, e, value, dt, idx); break;
                    case 13: mechFeast(p, hook, value, idx); break;
                    case 14: mechSupport(p, hook, value, dt, idx); break;
                    default: break;   // 4 技能改写：直接在 doSkill / doUltimate 内替换分支
                }
            }
        } finally {
            mechanicDepth--;
        }
    }

    /**
     * M3 低血动态 / M2 连击 / M13 击杀堆叠的派生乘区。
     * <p>recompute 与每帧 TICK 都会刷新：血量随时变化，不能只在 recompute 里算一次。
     */
    private void refreshMechanicDerived(Player p) {
        if (p == null) return;
        float low = 1f - clamp(p.hp / Math.max(1f, p.maxHp), 0f, 1f);
        float rateMul = 1f;
        float lowMul = 1f;
        int extraShots = 0;
        if (p.evolved[5]) { lowMul = 1f + low; rateMul *= lowMul; }          // 不动王座：1.0~2.0
        if (p.evolved[28]) extraShots = low >= .75f ? 2 : low >= .35f ? 1 : 0; // 血焰喷发：+0/1/2 弹
        if (p.evolved[16]) rateMul *= 1f + Math.min(.32f, p.comboCount * COMBO_STEP);
        if (p.evolved[29]) {
            p.feastRateMul = 1f + p.feastStacks * .03f;
            rateMul *= p.feastRateMul;
        } else {
            p.feastRateMul = 1f;
        }
        p.lowHpRateMul = lowMul;
        p.lowHpExtraShots = extraShots;
        // 规格约束：攻击间隔不得低于 interval × 0.5（即总乘区上限 2.0）
        p.attackSpeedMul = Math.max(.5f, Math.min(2f, rateMul));
        // 没有这三条进化的玩家必须保持 1.0，否则会吃到别人的乘区
        // （recompute 之后 p.attackSpeedMul 仍留着上一局的值就是从这里漏的）。
        if (!p.evolved[5] && !p.evolved[16] && !p.evolved[29]) p.attackSpeedMul = 1f;
    }

    /** M1 回旋镖：首次命中后折返，返程绕过 alreadyHit 以再次命中同一目标。 */
    private void mechBoomerang(Player p, int hook, Enemy e, Shot s, float value, int idx) {
        if (hook != H_HIT || s == null || s.enemy || s.boomerang != 1) return;
        Player owner = player(s.owner);
        if (owner == null) return;
        float a = (float) Math.atan2(owner.y - s.y, owner.x - s.x);
        float sp = (float) Math.hypot(s.vx, s.vy);
        if (sp < 1f) sp = 260f;
        s.vx = (float) Math.cos(a) * sp;
        s.vy = (float) Math.sin(a) * sp;
        s.boomerang = 2;                        // 最多往返一次
        s.damage *= .6f + Evolutions.POWER[idx];
        s.lastHit = -1;
        s.hitCount = 0;
        Arrays.fill(s.hits, -1);                // 清空命中表，返程可再次命中同一目标
        s.pierce = Math.max(s.pierce, 1);       // 保证返程这一击不会被 pierce 计数提前销毁
        s.life = Math.max(s.life, 1.2f);
        effect(s.x, s.y, 26, 4, 0xffe0d8ff, .25f);
    }

    /** M2 连击递增：连续命中同一目标叠层，换目标或断档重置。 */
    private void mechCombo(Player p, int hook, Enemy e, float dt) {
        if (hook == H_HIT) {
            if (e == null) return;
            if (e.id == p.comboTargetId) p.comboCount = Math.min(8, p.comboCount + 1);
            else { p.comboTargetId = e.id; p.comboCount = 1; }
            p.comboTimer = COMBO_WINDOW;
            refreshMechanicDerived(p);
        } else if (hook == H_TICK) {
            if (p.comboCount == 0) return;
            p.comboTimer = Math.max(0f, p.comboTimer - dt);
            if (p.comboTimer <= 0f) {
                p.comboCount = 0;
                p.comboTargetId = -1;
                refreshMechanicDerived(p);
            }
        }
    }

    /** M3 低血动态：血量越低攻速越快 / 弹丸越多（派生值见 refreshMechanicDerived）。 */
    private void mechLowHp(Player p, int hook) {
        if (hook == H_TICK || hook == H_FIRE) refreshMechanicDerived(p);
    }

    /** M5 场地生成：风暴之环（环绕弹丸）/ 寒渊之眼（密集处冰渊）。 */
    private void mechSite(Player p, int hook, float dt, int idx) {
        if (hook != H_TICK) return;
        if (idx == 2) {                          // 风暴之环：保证至少 2 枚环绕弹丸
            if (p.orbitCount <= 0) p.orbitCount = 2;
            return;
        }
        // idx 26 寒渊之眼
        p.siteClock -= dt;
        if (p.siteClock > 0f) return;
        p.siteClock = 4.5f;
        int sites = 0;
        for (Hazard h : hazards) if (h.kind == 1 && h.clock < 0f) sites++;
        if (sites >= 3) return;                  // 场上最多 3 个自建场地
        Enemy c = densestEnemy(p.x, p.y, 340f);
        if (c == null) c = nearestEnemy(p.x, p.y, false);
        if (c == null) return;
        float r = 80f + Evolutions.AREA[idx] * 200f;
        if (hazards.size() >= 30) return;
        Hazard h = new Hazard();
        h.id = allocateId();
        h.kind = 1;                              // 冰：只减速，不伤害
        h.owner = -1;                            // 环境危害（kind==1 不伤玩家）
        h.x = c.x; h.y = c.y; h.r = r;
        h.active = 4f;                           // 持续 4 秒
        h.clock = -1f;                           // 激活结束后立即移除（一次性）
        hazards.add(h);
        effect(c.x, c.y, r, 2, 0xff9bcfe8, .6f);
    }

    /** M6 增益扩散：把自身的增益（buff）额外扩散一层给附近友军。 */
    private void mechSpread(Player p, int hook, int idx) {
        if (hook != H_TICK || p.buff <= 0f) return;
        for (Player q : players) {
            if (q == p || q.down || !q.connected || q.buff > 0f) continue;
            if (distance(p.x, p.y, q.x, q.y) > 220f) continue;
            q.buff = p.buff * .8f;
            q.buffKind = p.buffKind;
            effect(q.x, q.y, 40, 2, 0xffcfe6ff, .4f);
        }
    }

    /** M8 二段判定：命中后追加一次独立攻击，0.25s 冷却（防止多弹武器爆炸）。 */
    private void mechSecondStrike(Player p, int hook, Enemy e, Shot s, float value, int idx) {
        if (hook != H_HIT || e == null || value <= 0f || p.secondStrikeCd > 0f) return;
        p.secondStrikeCd = .25f;
        float dmg = value * (.35f + Evolutions.POWER[idx]);
        switch (idx) {
            case 3: {   // 堡垒重炮：范围震击 + 击退
                for (Enemy o : enemies)
                    if (o.hp > 0 && distance(o.x, o.y, e.x, e.y) < 70f) damage(o, dmg, p.id);
                float dd = Math.max(1f, distance(p.x, p.y, e.x, e.y));
                moveEnemy(e, (e.x - p.x) / dd * 18f, (e.y - p.y) / dd * 18f);
                effect(e.x, e.y, 70, 2, 0xffefbd78, .3f);
                break;
            }
            case 19: {  // 磁暴散射：小范围电击
                for (Enemy o : enemies)
                    if (o.hp > 0 && distance(o.x, o.y, e.x, e.y) < 60f) damage(o, dmg, p.id);
                effect(e.x, e.y, 60, 7, 0xffa9d8ff, .3f);
                break;
            }
            case 30: {  // 万雷贯穿：终点炸开雷网
                if (s == null || s.pierce <= 1) lightningChain(p, e, 2, dmg);
                break;
            }
            case 31: {  // 落雷弹幕：二段落雷 + 眩晕
                damage(e, dmg, p.id);
                e.stun = Math.max(e.stun, .5f);
                effect(e.x, e.y, 40, 7, 0xffe2c8ff, .3f);
                break;
            }
            default: break;
        }
    }

    /** M9 全局计时：全场周期事件，间隔每次 ×0.92，下限 2.5s，单次最多 12 个目标。 */
    private void mechGlobal(Player p, int hook, float dt, int idx) {
        if (hook != H_TICK) return;
        if (p.globalInterval <= 0f) p.globalInterval = 6f;
        p.globalClock -= dt;
        if (p.globalClock > 0f) return;
        p.globalInterval = Math.max(2.5f, p.globalInterval * .92f);
        p.globalClock = p.globalInterval;
        if (idx == 14) {                         // 末日坩埚：敌人最密集处引爆
            Enemy c = densestEnemy(p.x, p.y, -1f);
            if (c == null) return;
            float r = 90f + Evolutions.AREA[idx] * 180f;
            for (Enemy o : enemies)
                if (o.hp > 0 && distance(o.x, o.y, c.x, c.y) < r) damage(o, power(p) * .9f, p.id);
            effect(c.x, c.y, r, 2, 0xffefac77, .5f);
        } else {                                 // 电磁风暴：全场雷击
            int hit = 0;
            for (Enemy o : enemies) {
                if (o.hp <= 0 || hit >= 12) continue;
                damage(o, power(p) * .8f, p.id);
                effect(o.x, o.y, 26, 7, 0xffe28aff, .25f);
                hit++;
            }
        }
    }

    /** M10 剥离：吞噬敌人护盾/护甲并转化为自身机制护盾。 */
    private void mechDrain(Player p, int hook, Enemy e, float value, int idx) {
        if (hook != H_HIT || e == null) return;
        float stolen = 0f;
        if (e.shield > 0f) {
            float take = Math.min(e.shield, value * (.5f + Evolutions.POWER[idx]));
            e.shield -= take;
            stolen += take;
        }
        if (e.guard > 0f) {
            float take = Math.min(e.guard, value * .3f);
            e.guard -= take;
            stolen += take;
        }
        if (stolen <= 0f) return;
        float cap = p.maxHp * .3f;
        p.mechShield = Math.min(cap, p.mechShield + stolen * (e.bossIndex >= 0 ? .5f : 1f));
        effect(e.x, e.y, 40, 8, 0xffbda5ed, .3f);
    }

    /** M11 位移联动：闪避后触发全方向弹幕（17）/ 击杀后侵蚀护甲（33）。 */
    private void mechLink(Player p, int hook, float dt, int idx) {
        if (idx == 17) {
            if (hook == H_TICK) {
                p.linkClock = Math.max(0f, p.linkClock - dt);
                return;
            }
            if (hook != H_DASH || p.linkClock > 0f) return;
            p.linkClock = 3f;                    // 每 3 秒最多一次
            for (int i = 0; i < 12; i++)
                projectile(p, i * (float) Math.PI * 2 / 12, power(p) * .6f, 5, 4, 560, 1.0f);
            p.comboCount = 0;
            p.comboTargetId = -1;
            refreshMechanicDerived(p);
            effect(p.x, p.y, 90, 6, 0xffd8c8ff, .5f);
        } else if (idx == 33) {
            if (hook != H_KILL) return;
            for (Enemy o : enemies)
                if (o.hp > 0 && distance(p.x, p.y, o.x, o.y) < 220f)
                    o.armor = Math.min(.24f, o.armor + .06f);
        }
    }

    /** M12 状态转化：冰缓→冻结、燃烧→爆裂、流血→死亡传染。 */
    private void mechReaction(Player p, int hook, Enemy e, float value, float dt, int idx) {
        if (hook == H_TICK) {
            p.mechReactCd = Math.max(0f, p.mechReactCd - dt);
            return;
        }
        if (idx == 27) {                          // 嗜血刃舞：命中附流血，流血目标死亡时爆裂传染
            if (hook == H_HIT && e != null && e.burn <= 0f) {
                e.burn = 3f;
                e.burnClock = .5f;
                e.owner = p.id;
            } else if (hook == H_KILL && e != null && e.burn > 0f) {
                for (Enemy o : enemies) {
                    if (o == e || o.hp <= 0) continue;
                    if (distance(o.x, o.y, e.x, e.y) < 90f) {
                        damage(o, power(p) * .6f, p.id);
                        o.burn = Math.max(o.burn, 2.5f);
                        o.owner = p.id;
                    }
                }
                effect(e.x, e.y, 90, 2, 0xffe07a4a, .35f);
            }
            return;
        }
        if (hook != H_HIT || e == null) return;
        switch (idx) {
            case 10: {  // 冰河爆裂：冻结目标原地炸裂
                if (e.slow > 0f && p.mechReactCd <= 0f) {
                    p.mechReactCd = .5f;
                    for (Enemy o : enemies)
                        if (o.hp > 0 && distance(o.x, o.y, e.x, e.y) < 80f)
                            damage(o, value * .5f, p.id);
                    effect(e.x, e.y, 80, 2, 0xff9bcfe8, .35f);
                }
                break;
            }
            case 11: {  // 绝对零界：冰缓叠满转冻结 + 眩晕
                if (e.slow > 2.5f && p.mechReactCd <= 0f) {
                    p.mechReactCd = .8f;
                    e.slow = 4f;
                    e.stun = Math.max(e.stun, .8f);
                    effect(e.x, e.y, 54, 2, 0xffcfe9ff, .4f);
                }
                break;
            }
            case 12: {  // 连锁爆燃：燃烧叠满炸裂并引燃周围
                if (e.burn > 2.5f && p.mechReactCd <= 0f) {
                    p.mechReactCd = .6f;
                    for (Enemy o : enemies)
                        if (o.hp > 0 && distance(o.x, o.y, e.x, e.y) < 70f) {
                            damage(o, value * .45f, p.id);
                            o.burn = Math.max(o.burn, 2f);
                            o.owner = p.id;
                        }
                    effect(e.x, e.y, 70, 2, 0xffefac77, .35f);
                }
                break;
            }
            case 25: {  // 永冻之息：冰缓缓慢叠加，叠满转冻结
                if (e.slow > 0f) {
                    e.slow = Math.min(6f, e.slow + .3f);
                    if (e.slow >= 5f) {
                        e.slow = 0f;
                        e.stun = Math.max(e.stun, 1.2f);
                        effect(e.x, e.y, 50, 8, 0xffcfe9ff, .4f);
                    }
                }
                break;
            }
            default: break;
        }
    }

    /** M13 击杀堆叠：击杀累积层数，层数转攻速与回血；倒地清空。 */
    private void mechFeast(Player p, int hook, float value, int idx) {
        if (hook == H_KILL) {
            p.feastStacks = Math.min(20, p.feastStacks + 1);
            heal(p, p.maxHp * Evolutions.POWER[idx]);
            effect(p.x, p.y, 40, 3, 0xffff8f9f, .35f);
        } else if (hook == H_TICK) {
            if (p.down) p.feastStacks = 0;
            refreshMechanicDerived(p);
        }
    }

    /** M14 支援转化：荆棘回响（承伤转治疗）/ 星辉权杖（命中治疗友军）。 */
    private void mechSupport(Player p, int hook, float value, float dt, int idx) {
        if (hook == H_TICK) {
            p.supportCd = Math.max(0f, p.supportCd - dt);
            p.healBurstCd = Math.max(0f, p.healBurstCd - dt);
            return;
        }
        if (idx == 4) {                            // 荆棘回响：承伤的 30% 转治疗
            if (hook != H_HURT || p.supportCd > 0f) return;
            p.supportCd = .5f;
            heal(p, Math.max(0f, value) * .3f);
        } else if (idx == 6) {                     // 星辉权杖：命中治疗附近友军
            if (hook != H_HIT || p.supportCd > 0f) return;
            p.supportCd = .5f;
            for (Player q : players)
                if (q != p && !q.down && q.connected && distance(q.x, q.y, p.x, p.y) < 260f)
                    heal(q, p.maxHp * .02f);
        }
    }

    /** M14-7 愈光之环：治疗产生光爆（由 {@link #heal} 调用）。 */
    private void healBurst(Player p, float amount) {
        if (p == null || p.healBurstCd > 0f || amount <= 0f || mechanicDepth > 2) return;
        p.healBurstCd = .5f;
        float r = 90f + p.areaBonus * 60f;
        for (Enemy o : enemies)
            if (o.hp > 0 && distance(o.x, o.y, p.x, p.y) < r) damage(o, amount * .4f, p.id);
        effect(p.x, p.y, r, 2, 0xffcfe6ff, .35f);
    }

    /** 找附近敌人最密集的那个（用于 M5 / M9 的落点）。radius &lt;= 0 表示不限距离。 */
    private Enemy densestEnemy(float nearX, float nearY, float radius) {
        Enemy best = null;
        int bestCount = -1;
        for (Enemy c : enemies) {
            if (c.hp <= 0) continue;
            if (radius > 0f && distance(nearX, nearY, c.x, c.y) > radius) continue;
            int n = 0;
            for (Enemy o : enemies) if (o.hp > 0 && distance(c.x, c.y, o.x, o.y) < 150f) n++;
            if (n > bestCount) { bestCount = n; best = c; }
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════════
    //  职业基线（供 recompute 使用）
    // ══════════════════════════════════════════════════════════════

    static float roleBaseDamage(int role) {
        switch (role) {
            case 0: return 21; case 1: return 13; case 2: return 19; case 3: return 26;
            case 4: return 33; case 5: return 21; case 6: return 19; case 7: return 18;
            case 8: return 25; case 9: return 27; case 10: return 30; default: return 20;
        }
    }

    static float roleBaseInterval(int role) {
        switch (role) {
            case 0: return .23f; case 1: return .62f; case 2: return .34f; case 3: return .36f;
            case 4: return .54f; case 5: return .28f; case 6: return .44f; case 7: return .42f;
            case 8: return .36f; case 9: return .46f; case 10: return .43f; default: return .32f;
        }
    }

    static float roleBaseHp(int role) {
        switch (role) {
            case 1: return 196; case 6: return 176; case 7: return 162; case 2: return 154;
            case 8: return 178; case 10: return 174; case 4: return 158; case 9: return 168;
            case 11: return 152; case 5: return 126; default: return 144;
        }
    }

    static float roleBaseSpeed(int role) {
        switch (role) {
            case 5: return 240; case 11: return 228; case 0: return 222; case 9: return 210;
            case 2: return 205; case 7: return 204; case 8: return 202; case 6: return 200;
            case 3: return 198; case 4: return 196; case 10: return 194; default: return 190;
        }
    }

    static float roleBaseCrit(int role) {
        switch (role) {
            case 5: return .24f; case 11: return .14f; case 0: return .10f; default: return .06f;
        }
    }

    static int roleBaseMultishot(int role) {
        switch (role) { case 1: return 3; case 6: return 2; default: return 1; }
    }

    static int roleBasePierce(int role) {
        switch (role) { case 3: case 10: return 1; default: return 0; }
    }

    static int roleBaseSlow(int role) { return role == 8 ? 1 : 0; }
    static int roleBaseLeech(int role) { return role == 9 ? 1 : 0; }
    static int roleBaseExplode(int role) { return role == 4 ? 1 : 0; }
    static float baseMagnet() { return 100; }
}
