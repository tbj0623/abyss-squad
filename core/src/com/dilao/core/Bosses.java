package com.dilao.core;

/**
 * 8 位首领 × 3 阶段。阶段主招式见 {@link Game} 的 BOSS_* 常量。
 * ADDS 为阶段转换时召唤的护卫数量。
 *
 * <p><b>v2.1 内容深度修复</b>：
 * <ul>
 *   <li>{@link #PHASES} 去重——1/3/4 原来都排了"冲锋 + 落点 + 扇"，冲锋又是死代码，
 *       导致三者体感完全一样。现在每个首领都有至少一个别人没有的招式，
 *       且三个"冲锋型"首领的冲锋参数差异很大（{@link #CHARGE_SPEED} 等）。</li>
 *   <li>{@link #BIOME} 与 {@link Game#bossIndexForRoom()} 的楼层映射一一对应，
 *       保证 8 个首领在远征 15 站里全部出现（原来 4/5/6 永远见不到）。</li>
 * </ul>
 */
public final class Bosses {
    private Bosses() {}

    public static final int COUNT = 8;

    public static final String[] NAMES = {
            "苔冠守望者", "赤炉铸造者", "月蚀档案官", "霜心女猎手",
            "熔核暴君", "虚空执政官", "双生回响", "终焉 · 深渊之心"
    };

    public static final String[] TITLE = {
            "万年苔主", "不熄的炉心", "被抹去的名字", "永冬的猎手",
            "熔岩之下的王", "深渊的代言人", "同一枚硬币的两面", "所有旅程的终点"
    };

    public static final String[] DESC = {
            "以环形弹幕覆盖全场，会在脚下长出孢子地带。绕圈跑，别停在原地。",
            "冲锋与扇形喷射交替，半血后开始撒落熔岩。跟它保持中距离。",
            "记录你每一次移动然后重放。弹幕会重复上一轮的轨迹。",
            "射出的箭会留下冰径，被冰径困住就会被连射。先清场地。",
            "第一阶段只用落点轰炸，进入狂暴后会一边冲撞一边留下火海。",
            "瞬移和激光交替，走位失误就会被交叉扫射。注意传送后的停顿。",
            "两个身体共享血量，一个近一个远。打近的会被远距离消耗。",
            "三阶段分别对应环形、螺旋与全方位地火。它没有弱点，只有节奏。"
    };

    public static final int[] ART = {32, 33, 34, 35, 36, 37, 38, 39};

    /**
     * 首领所属生态。与 {@link Game#bossIndexForRoom()} 的楼层映射严格一致：
     * 远征 15 站 = 5 章 × 3 站，第 1..5 章的首领是 0..4，第 15 站（终章）是 7。
     * 首领 5 / 6 在远征里作为"第 5 章 / 第 6 章的替代首领"轮换出现——
     * 靠 {@link Game#bossIndexForRoom()} 按 {@code roomsTotal} 分档，不再依赖 biome。
     */
    public static final int[] BIOME = {0, 1, 2, 3, 4, 5, 5, 5};

    public static final float[] HP = {1150, 1600, 2000, 2350, 2900, 3500, 4050, 5600};

    public static final float[] SPEED = {62, 58, 66, 70, 52, 64, 60, 56};

    public static final float[] RADIUS = {38, 42, 36, 34, 46, 38, 36, 52};

    /**
     * 每阶段主招式 id：0 环 1 扇 2 跟踪 3 冲锋 4 落点 5 螺旋 6 召唤 7 场地 8 激光 9 瞬移。
     *
     * <p>去重原则：8 行里，每个首领至少有一个"只有它有"的招式组合。
     * <pre>
     *   0 苔冠   环 / 落点 / 螺旋   —— 唯一以环起手的远程压制
     *   1 赤炉   扇 / 冲锋 / 场地   —— 冲锋型，招牌是熔岩场地
     *   2 月蚀   跟踪 / 激光 / 召唤 —— 唯一带跟踪 + 召唤的记录者
     *   3 霜心   冲锋 / 落点 / 激光 —— 冲锋型，招牌是激光封走位
     *   4 熔核   场地 / 冲锋 / 扇   —— 冲锋型，起手就是场地
     *   5 虚空   瞬移 / 激光 / 环   —— 唯一以瞬移起手
     *   6 双生   召唤 / 扇 / 跟踪   —— 唯一以召唤起手
     *   7 终焉   螺旋 / 场地 / 激光 —— 终章的全方位组合
     * </pre>
     * 冲锋只给 1/3/4（三个冲锋型），且 {@link #CHARGE_SPEED} 等参数按体型分别调校。
     */
    public static final int[][] PHASES = {
            {0, 4, 5},   // 苔冠守望者：环 → 落点 → 螺旋
            {1, 3, 7},   // 赤炉铸造者：扇 → 冲锋 → 场地
            {2, 8, 6},   // 月蚀档案官：跟踪 → 激光 → 召唤（原为环，与 0 重复）
            {3, 8, 1},   // 霜心女猎手：冲锋 → 激光 → 扇（原为冲锋/落点/扇）
            {7, 3, 4},   // 熔核暴君：场地 → 冲锋 → 落点（原为落点/冲锋/场地，与 1 重复）
            {9, 8, 0},   // 虚空执政官：瞬移 → 激光 → 环（原为激光/瞬移/螺旋）
            {6, 1, 2},   // 双生回响：召唤 → 扇 → 跟踪（唯一以召唤起手）
            {5, 7, 8}    // 终焉：螺旋 → 场地 → 激光
    };

    // ── 冲锋参数（Boss 1 / 3 / 4 各一套，让三者的冲锋手感完全不同）──────────
    //
    //  不变量（由 valid() 强校验）：本行非 0 ⟺ 该首领的 PHASES 里出现 BOSS_CHARGE。
    //  下标 0 / 2 / 5 / 6 / 7 的冲锋参数必须全 0——这两个位置曾经残留了旧数值，
    //  使 valid() 长期为 false（CoreTests 唯一一条红）。
    //
    //  三者的人格化差异：
    //    1 赤炉铸造者 —— 沉重：起步慢、冲得久、伤害高，恢复窗口最长（重甲的代价）
    //    3 霜心女猎手 —— 迅捷：预警短、冲刺快、伤害最低，靠「短促的惊吓」而不是数值
    //    4 熔核暴君   —— 长驱：一次冲得最远，是三种里唯一能横穿半个场地的
    /** 冲刺速度 px/s。 */
    public static final float[] CHARGE_SPEED = {0f, 520f, 0f, 780f, 600f, 0f, 0f, 0f};
    /** 预警时长（秒）。冲刺越快，预警必须越长，否则不可反应。 */
    public static final float[] CHARGE_WINDUP = {0f, .78f, 0f, .52f, .68f, 0f, 0f, 0f};
    /** 冲刺持续（秒）。距离 ≈ CHARGE_SPEED × CHARGE_TIME：1≈208px / 3≈203px / 4≈300px。 */
    public static final float[] CHARGE_TIME = {0f, .40f, 0f, .26f, .50f, 0f, 0f, 0f};
    /** 撞上玩家的一次性伤害（同一次冲锋只结算一次）。 */
    public static final float[] CHARGE_DAMAGE = {0f, 28f, 0f, 18f, 34f, 0f, 0f, 0f};
    /** 冲刺结束后的恢复窗口（秒），期间不移动、不攻击——这是玩家的输出窗口。 */
    public static final float[] CHARGE_RECOVER = {0f, 1.00f, 0f, .70f, .95f, 0f, 0f, 0f};

    /** 冲锋预警的绝对下限（秒）。与 {@code Balance.bossWindup()} 一致，低于它冲锋就不可躲。 */
    public static final float CHARGE_MIN_WINDUP = .45f;

    /**
     * 该首领在第 {@code phase} 阶段的冲锋速度（px/s，0 表示没有冲锋）。
     *
     * <p><b>为什么要这组访问器</b>：{@code Game.doBossPattern} 的 {@code BOSS_CHARGE} 分支此前写的是
     * <pre>sp = real ? Bosses.CHARGE_SPEED[idx] : 620f;  time = real ? ... : .5f;  dmg = ... : 20f;</pre>
     * 这组 {@code 620/.5f/20f} 是<b>全局硬编码兜底</b>：一旦某个首领的 PHASES 里出现冲锋却没填表，
     * 八个首领的冲锋手感会全部塌缩成同一个数字（见 docs/audit-content-2026-10-round2.md R8）。
     * 现在兜底改为<b>按首领体型/移速推导 + 随阶段递增</b>，所以：
     * <ul>
     *   <li>有表值 → <b>原样返回</b>（1/3/4 的手工调校不该被任何乘算污染）；</li>
     *   <li>无表值 → 由 {@link #SPEED} / {@link #RADIUS} 推导，任何两个首领都不会同手感；</li>
     *   <li>第 2/3 阶段更快更痛（+10% 速度/阶段、+6 伤害/阶段），符合"越打越急"的读法。</li>
     * </ul>
     * {@code valid()} 仍然强制"表非 0 ⟺ PHASES 里有冲锋"，所以兜底路径在正常数据下不可达——
     * 它存在只是为了"将来有人放宽数据校验时，退化得好看一点"，而不是变成一个隐形的统一招式。
     */
    public static float chargeSpeed(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return 0f;
        if (CHARGE_SPEED[idx] > 0f) return CHARGE_SPEED[idx];
        return SPEED[idx] * 9f * (1f + .10f * clampPhase(phase));
    }

    /** 该首领第 {@code phase} 阶段的冲锋预警（秒）。无表值时按阶段轻微收紧，但不低于 {@link #CHARGE_MIN_WINDUP}。 */
    public static float chargeWindup(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return CHARGE_MIN_WINDUP;
        if (CHARGE_WINDUP[idx] > 0f) return CHARGE_WINDUP[idx];
        return clamp(.62f - .05f * clampPhase(phase), CHARGE_MIN_WINDUP, 1.2f);
    }

    /** 该首领第 {@code phase} 阶段的冲锋持续（秒）。无表值时由速度反推，保证冲刺距离 ≈ 240px。 */
    public static float chargeTime(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return 0f;
        if (CHARGE_TIME[idx] > 0f) return CHARGE_TIME[idx];
        return clamp(240f / Math.max(1f, chargeSpeed(idx, phase)), .20f, .70f);
    }

    /** 该首领第 {@code phase} 阶段的冲锋伤害。无表值时 16 / 22 / 28，随阶段递增。 */
    public static float chargeDamage(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return 0f;
        if (CHARGE_DAMAGE[idx] > 0f) return CHARGE_DAMAGE[idx];
        return 16f + 6f * clampPhase(phase);
    }

    /** 该首领第 {@code phase} 阶段的冲锋恢复窗口（秒）。无表值时按体型（半径越大，收势越慢）。 */
    public static float chargeRecover(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return 0f;
        if (CHARGE_RECOVER[idx] > 0f) return CHARGE_RECOVER[idx];
        return clamp(.60f + RADIUS[idx] / 120f, .50f, 1.20f);
    }

    private static int clampPhase(int phase) {
        return Math.max(0, Math.min(2, phase));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ══════════════════════════════════════════════════════════════
    //  阶段转换 = 0.9 s 可读怒吼（research-best-in-class §2.4）
    // ══════════════════════════════════════════════════════════════

    /**
     * 阶段转换的怒吼时长（秒）。
     *
     * <p><b>为什么正好 0.9 s</b>：
     * <ul>
     *   <li>0.9 s 长于一次人眼"注意 → 定位 → 决策"的完整周期（约 0.45–0.6 s），
     *       但不长到打断战斗节奏——它是<b>节拍</b>，不是过场动画。</li>
     *   <li>WCAG 2.3.1 要求 1 秒内闪烁不超过 3 次；本实现只闪 <b>1 次</b>，
     *       且 0.9 s 的窗口天然把两次转阶段闪光的间隔拉到 ≥0.9 s 以上。</li>
     *   <li>与 {@code Balance.bossWindup()} 的 1.08→0.94 s 同量级：
     *       玩家刚学会"看到首领不动 = 要出招"，怒吼就是同一套读法的复用。</li>
     * </ul>
     * 这段时间里首领<b>不出手、不移动</b>（Game 侧的 BOSS_ROAR 子状态），
     * 所以它不是"白送的无敌"，而是把后续伤害预算挪到怒吼之后。
     */
    public static final float PHASE_ROAR = .9f;

    /**
     * 血量比例 → 阶段编号（1 / 2 / 3，边界 2/3 与 1/3）。
     *
     * <p>返回值是<b>人读的</b> 1 基编号；{@code Game} 内部的 0 基阶段是 {@code phaseOf(...) - 1}。
     * 边界与 {@code updateBoss} 的旧字面量 {@code .34f / .67f} 只差 <0.7% 血量——
     * 这个差异只影响"哪一帧触发怒吼"，不影响任何数值。
     *
     * <p>为什么要有这个函数：怒吼必须和阶段切换<b>同一帧</b>触发，否则会出现
     * "已经进了二阶段、招式都变了，但怒吼晚一拍"的读法错位。把血线判据收敛到一处，
     * Game 与测试就都只能有一个真相。
     */
    public static int phaseOf(float hpFraction) {
        // 0 / 负数 / NaN 一律落到终阶段：这是最保守的读法（血条见底），
        // 不会因为一次除零而在满血时误触两次怒吼。
        if (!(hpFraction > 2f / 3f)) {
            if (!(hpFraction > 1f / 3f)) return 3;
            return 2;
        }
        return 1;
    }

    /**
     * 怒吼推开半径（px）。
     *
     * <p>240 px ≈ 战斗区宽度 1120 的 21%，也略大于 {@code Bosses.RADIUS} 最大值的 4 倍，
     * 保证"转阶段那一刻贴着首领的怪会被推出去"，但不会把半场清空——
     * 玩家仍然要面对下一波，只是不会在无敌窗口里被围死。
     * 推的是<b>敌人</b>（含召唤护卫），不是玩家：把玩家推走会破坏走位记忆。
     */
    public static float roarPushDistance() {
        return 240f;
    }

    /**
     * 转阶段怒吼的强度档（能力列，不是数值列）。
     * <pre>
     *   0 = 只清弹 + 推开敌人，首领<b>不</b>无敌（前两个首领；新手局"打不动"最劝退）
     *   1 = 标准：无敌 + 清弹 + 推开 + 一次亮度闪（默认）
     *   2 = 加强：在 1 的基础上，把首领脚下的延迟危险区也一并清掉（终章级战场重置）
     * </pre>
     * 为什么按首领分档而不是全局统一：无敌 0.9 s 对低 DPS 的 build 是"喘息"，
     * 对高 DPS 的 build 是"白丢一秒输出"。前两个首领不无敌，让新玩家不会觉得
     * "明明在打却没伤害"；后段首领用满档，把转阶段变成真正的节拍。
     */
    public static final int[] PHASE_IMMUNE = {0, 0, 1, 1, 1, 1, 1, 2};

    // ══════════════════════════════════════════════════════════════
    //  首领抗性表（research-best-in-class §2.6）
    // ══════════════════════════════════════════════════════════════

    /**
     * 状态位。前 7 个复用 {@link Content} 的 {@code F_*} 位掩码语义
     * （这样 {@code Player.has(flag)} 的结果可以直接拿来查表，不需要再翻译一层）；
     * {@link #S_ZONE} 是 Bosses 本地新增的第 30 位——"地面持续伤害区"在 Content 里没有独立位，
     * 它由 {@code Hazard}（火圈 / 熔岩 / 雷池）承载，语义上和"灼烧"是两回事：
     * 前者是<b>站位</b>问题，后者是<b>命中</b>问题。
     */
    public static final int
            S_BURN      = Content.F_BURN,        // 灼烧（命中附带的持续伤害）
            S_FREEZE    = Content.F_FREEZE,      // 冻结 / 冰痕减速
            S_KNOCKBACK = Content.F_KNOCKBACK,   // 击退
            S_SLOWFIELD = Content.F_SLOWFIELD,   // 持续减速领域 / 光环减速
            S_EXECUTE   = Content.F_EXECUTE,     // 低血处决
            S_STUN      = Content.F_STUN,        // 眩晕
            S_LOCK      = Content.F_HOMING,      // 单体锁定（追踪弹自动锁定当前身体）
            S_ZONE      = 1 << 30;               // 地面危险区（本地新增位，不与 Content 冲突）

    /** 所有可免疫状态的并集，供 {@code valid()} 校验掩码不越界。 */
    public static final int S_ALL = S_BURN | S_FREEZE | S_KNOCKBACK | S_SLOWFIELD
            | S_EXECUTE | S_STUN | S_LOCK | S_ZONE;

    /**
     * 每位首领的<b>一条</b>状态免疫。8 位首领覆盖 8 种不同状态（远超"≥5 种"的下限），
     * 所以每一种主流 build 都至少会遇上一次"我这套在这里不灵"：
     * <pre>
     *   0 苔冠守望者 → 击退      根须扎地，推不动（逼你换伤害而不是控场）
     *   1 赤炉铸造者 → 灼烧      本身就是炉心（火 build 对它只有直伤）
     *   2 月蚀档案官 → 眩晕      记录者不会被打断（雷锤 build 要换节奏）
     *   3 霜心女猎手 → 冻结      永冬的猎手（冰 build 的唯一硬钉子）
     *   4 熔核暴君   → 地面危险区 站在岩浆里长大（地雷/火圈对它无效）
     *   5 虚空执政官 → 处决      深渊的代言人，没有"斩杀线"（处决 build 失效）
     *   6 双生回响   → 单体锁定  必须打"当前面向的那个身体"（追踪弹不再代劳）
     *   7 终焉       → 减速领域  它的节奏不可被拖慢（减速光环对它无效）
     * </pre>
     *
     * <p><b>强度约定</b>：研究文档建议"减半（×0.25）而不是完全免疫"，避免某些 build 直接卡关。
     * 但本表只声明<b>免疫哪一条</b>，具体是"归零 / 减半 / 打折扣"由 {@code Game} 决定；
     * 表里不带数值，就不会出现"表说免疫 100%、代码实际只减 25%"的两套真相。
     */
    public static final int[] IMMUNE = {
            S_KNOCKBACK,   // 0 苔冠守望者
            S_BURN,        // 1 赤炉铸造者
            S_STUN,        // 2 月蚀档案官
            S_FREEZE,      // 3 霜心女猎手
            S_ZONE,        // 4 熔核暴君
            S_EXECUTE,     // 5 虚空执政官
            S_LOCK,        // 6 双生回响
            S_SLOWFIELD    // 7 终焉
    };

    /**
     * 该首领是否免疫 {@code statusFlag}。
     * <p>{@code statusFlag} 可以是多个位的组合：只要有<b>任意一位</b>命中免疫表就返回 true
     * （调用方若想区分"完全免疫 / 部分免疫"，请传单个位）。
     * 越界下标一律返回 false——"查不到数据"不等于"免疫情"。
     */
    public static boolean resists(int boss, int statusFlag) {
        if (boss < 0 || boss >= COUNT) return false;
        return (IMMUNE[boss] & statusFlag) != 0;
    }

    public static final int[] ADDS = {2, 2, 2, 3, 3, 3, 4, 4};

    public static final int[] XP = {180, 220, 260, 300, 340, 380, 420, 520};

    /** 该首领是否会冲锋。 */
    public static boolean charges(int idx) {
        return idx >= 0 && idx < COUNT && CHARGE_SPEED[idx] > 0;
    }

    /**
     * 该首领的第 {@code phase} 阶段是否会冲锋。
     * <p>冲锋现在有了完整的预警/冲刺/恢复状态机（{@code Game.updateBoss}），
     * 所以"要不要用冲锋人格（短预警+长恢复）"必须看当前这一阶段的招式，
     * 而不是看首领整体。
     */
    public static boolean chargesIn(int idx, int phase) {
        if (idx < 0 || idx >= COUNT) return false;
        int p = Math.max(0, Math.min(2, phase));
        return PHASES[idx][p] == Game.BOSS_CHARGE && charges(idx);
    }

    public static boolean valid() {
        int[] lens = {NAMES.length, TITLE.length, DESC.length, ART.length, BIOME.length,
                HP.length, SPEED.length, RADIUS.length, ADDS.length, XP.length,
                CHARGE_SPEED.length, CHARGE_WINDUP.length, CHARGE_TIME.length,
                CHARGE_DAMAGE.length, CHARGE_RECOVER.length,
                PHASE_IMMUNE.length, IMMUNE.length};
        for (int n : lens) if (n != COUNT) return false;
        if (PHASES.length != COUNT) return false;
        for (int[] row : PHASES) {
            if (row.length != 3) return false;
            for (int v : row) if (v < 0 || v > 9) return false;
        }
        // 冲锋参数自洽：声明了冲锋就必须有正的速度/时长，且预警足够长到可反应。
        for (int i = 0; i < COUNT; i++) {
            boolean lists = false;
            for (int v : PHASES[i]) if (v == Game.BOSS_CHARGE) lists = true;
            if (lists != charges(i)) return false;
            if (charges(i)) {
                if (!(CHARGE_SPEED[i] > 0) || !(CHARGE_TIME[i] > 0) || !(CHARGE_RECOVER[i] > 0))
                    return false;
                if (!(CHARGE_WINDUP[i] >= CHARGE_MIN_WINDUP && CHARGE_WINDUP[i] <= 1.2f)) return false;
                if (!(CHARGE_DAMAGE[i] > 0)) return false;
            }
        }
        // 三阶段不能有重复招式：同一首领重复排同一招 = 体感少一招。
        for (int[] row : PHASES) {
            for (int a = 0; a < row.length; a++)
                for (int b = a + 1; b < row.length; b++)
                    if (row[a] == row[b]) return false;
        }
        // ── v2.2 转阶段怒吼 ────────────────────────────────────────────
        // 怒吼时长必须落在"可读但不拖沓"的区间；推离半径必须够大（至少 3 倍最大体型），
        // 否则"转阶段被围死"这个它本来要解决的问题依然存在。
        if (!(PHASE_ROAR >= .6f && PHASE_ROAR <= 1.2f)) return false;
        if (!(roarPushDistance() >= RADIUS[COUNT - 1] * 3f)) return false;
        // 阶段开关只允许 0 / 1 / 2（0 = 不无敌，2 = 额外清地面危险区）。
        for (int v : PHASE_IMMUNE) if (v < 0 || v > 2) return false;
        // phaseOf 的三段边界必须单调且覆盖 1/2/3，否则怒吼永远不触发或每帧都触发。
        if (phaseOf(1f) != 1 || phaseOf(2f / 3f) != 2 || phaseOf(1f / 3f) != 3
                || phaseOf(.999f) != 1 || phaseOf(.5f) != 2 || phaseOf(.1f) != 3) return false;
        // ── v2.2 首领抗性表 ────────────────────────────────────────────
        // 每个首领恰好一条免疫（不允许 0 条：那等于"这条设计不存在"；
        // 也不允许一条里塞多元免疫：HUD 上写不下，玩家也读不出重点）。
        int covered = 0;
        for (int i = 0; i < COUNT; i++) {
            int mask = IMMUNE[i];
            if (mask == 0) return false;
            if ((mask & ~S_ALL) != 0) return false;           // 不能出现未定义位
            if ((mask & (mask - 1)) != 0) return false;       // 必须恰好一位（2 的幂）
            covered |= mask;
        }
        // 8 位首领必须覆盖 ≥5 种不同状态（研究文档的下限），否则"抗性"只是换了个说法。
        if (Integer.bitCount(covered) < 5) return false;
        return true;
    }

    public static void main(String[] args) {
        System.out.println(valid() ? "Bosses valid: " + COUNT : "Bosses FAILED");
    }
}
