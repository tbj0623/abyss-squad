package com.dilao.core;

/**
 * 12 职业的「约束规则」：每个职业一条<b>硬性门槛</b>（{@link #allows}）+ 一条<b>软性偏好</b>
 * （{@link #weightMul}），作用于遗物三选一与商店备货。
 *
 * <p><b>为什么要有这个类。</b>60 件遗物里一局只能拿十几件，但此前每个职业看到的是同一个池子，
 * 于是十二次远征的构筑长得一模一样，玩家的最优解永远是「拿最强的」。这里给每个职业一条
 * 说得出口的「不收什么」，让同一批遗物在不同职业手里被重新估值——照 genre report 的说法，
 * 这是 Brotato 62 角色 × 208 道具的乘法，而不是加法。
 *
 * <p><b>规则的两条纪律。</b>
 * <ol>
 *   <li>硬门槛必须<b>真的挡掉东西</b>（每个职业至少 5 件），否则只是文案；</li>
 *   <li>硬门槛不能把职业<b>饿死</b>——每个职业至少 25 件合法遗物，且 60 件里每一件
 *       至少对一个职业合法（{@code tests/ClassRulesTest.java} 会逐条验证）。</li>
 * </ol>
 *
 * <p><b>标签体系。</b>把遗物按「玩家拿它图什么」归到 7 条主轴上（一件可占 1~2 条）。
 * 规则只引用主轴，不写死 id，既好读也方便日后加遗物：新遗物只要在 {@link #TAG} 里
 * 标好主轴，就会自动被 12 条规则接纳/排斥。{@link #TAG} 与 {@link Relics} 的实际效果
 * 对齐（见 docs/relic-final-list.md 的「引擎落点」列）。
 *
 * <p><b>线程/边界。</b>纯静态、无状态；{@code role} 越界按「无规则」处理，{@code relicId}
 * 越界一律不合法。{@link #weightMul} 恒落在 [{@link #MIN_W}, {@link #MAX_W}]。
 */
public final class ClassRules {
    private ClassRules() {}

    /** 每个职业一条规则，下标与 {@link Content#ROLES} 一致。 */
    public static final int RULE_COUNT = 12;

    // ══════════════════════════════════════════════════════════════
    //  七条主轴（遗物的「主要属性」）
    // ══════════════════════════════════════════════════════════════

    /** 火力：攻速、直伤、多弹丸——把输出频率或单发数字做大的件。 */
    public static final int T_DPS = 1 << 0;
    /** 暴击：暴击率 / 暴击触发。 */
    public static final int T_CRIT = 1 << 1;
    /** 元素：灼烧 / 冰缓 / 连锁 / 虚空侵蚀等词条驱动件。 */
    public static final int T_ELEM = 1 << 2;
    /** 重装：护盾、生命上限、反伤——把身板做厚的件。 */
    public static final int T_GUARD = 1 << 3;
    /** 机动：移速、闪避、位移。 */
    public static final int T_MOVE = 1 << 4;
    /** 续航：每秒回复、击杀回血、吸血。 */
    public static final int T_SUSTAIN = 1 << 5;
    /** 弹道技巧：穿透、分裂、环绕、地雷、召唤、处决、击退、眩晕、经济。 */
    public static final int T_ART = 1 << 6;

    /**
     * 60 件遗物的主轴（与 {@link Relics} 的 id 一一对应）。
     *
     * <p>判定口径：写「玩家为了什么拿它」而不是「它带什么 FLAGS」。例如 0 分裂棱镜
     * 是弹丸 +1（弹道轴），不是直伤轴；35 血脉契约的核心是拿血换伤害（火力 + 续航）。
     */
    public static final int[] TAG = {
            /*  0 分裂棱镜 */ T_ART,
            /*  1 熔火之心 */ T_DPS | T_ELEM,
            /*  2 余烬披风 */ T_ELEM | T_MOVE,
            /*  3 血契指环 */ T_DPS,
            /*  4 双生齿轮 */ T_DPS,
            /*  5 碎甲棱角 */ T_ART,
            /*  6 潮汐镜片 */ T_DPS | T_ART,
            /*  7 灵魂灯芯 */ T_SUSTAIN,
            /*  8 爆裂种子 */ T_ART,
            /*  9 余烬王冠 */ T_ART | T_ELEM,
            /* 10 疾风发条 */ T_DPS | T_CRIT,
            /* 11 轻羽斗篷 */ T_MOVE,
            /* 12 回响靴   */ T_MOVE,
            /* 13 穿星针   */ T_ART,
            /* 14 星尘引力 */ T_ART,
            /* 15 幸运骰子 */ T_CRIT,
            /* 16 疾风之翼 */ T_ART | T_MOVE,
            /* 17 残影石   */ T_CRIT | T_MOVE,
            /* 18 疾行护符 */ T_MOVE,
            /* 19 风暴核心 */ T_DPS,
            /* 20 极寒碎片 */ T_ELEM,
            /* 21 霜花王冠 */ T_ELEM | T_ART,
            /* 22 冰封瓶   */ T_ELEM,
            /* 23 寒潮之息 */ T_ELEM,
            /* 24 霜纹甲   */ T_GUARD | T_ELEM,
            /* 25 冰棱棱镜 */ T_ELEM | T_ART,
            /* 26 永冻之石 */ T_ELEM | T_SUSTAIN,
            /* 27 雪盲面具 */ T_CRIT | T_ELEM,
            /* 28 霜语吊坠 */ T_DPS | T_ELEM,
            /* 29 凛冬之冠 */ T_ELEM | T_DPS,
            /* 30 琥珀之心 */ T_GUARD,
            /* 31 生命孢子 */ T_SUSTAIN,
            /* 32 星医之灯 */ T_SUSTAIN,
            /* 33 再生藤蔓 */ T_SUSTAIN,
            /* 34 守护符   */ T_GUARD,
            /* 35 血脉契约 */ T_DPS | T_SUSTAIN,
            /* 36 荆棘护甲 */ T_GUARD,
            /* 37 月蚀电池 */ T_DPS,
            /* 38 祈求之印 */ T_SUSTAIN,
            /* 39 不灭之火 */ T_DPS | T_ELEM,
            /* 40 脉冲线圈 */ T_ELEM | T_ART,
            /* 41 闪电匣   */ T_ELEM | T_ART,
            /* 42 雷矢箭袋 */ T_ART | T_DPS,
            /* 43 蓄能电容 */ T_DPS,
            /* 44 弧光棱镜 */ T_ART | T_ELEM,
            /* 45 雷暴之眼 */ T_ELEM,
            /* 46 静电场   */ T_GUARD | T_ELEM,
            /* 47 导电金属 */ T_ART | T_ELEM,
            /* 48 电磁核心 */ T_ELEM,
            /* 49 万雷之冠 */ T_ELEM | T_DPS,
            /* 50 虚空裂片 */ T_ART | T_ELEM,
            /* 51 深渊凝视 */ T_ART,
            /* 52 扭曲之环 */ T_ART,
            /* 53 虚空引擎 */ T_DPS,
            /* 54 裂隙之刃 */ T_ART,
            /* 55 吞噬面具 */ T_ART,
            /* 56 熵之沙漏 */ T_GUARD,
            /* 57 虚无印记 */ T_ART,
            /* 58 暗物质囊 */ T_DPS | T_ELEM,
            /* 59 深渊王冠 */ T_DPS | T_ELEM
    };

    // ══════════════════════════════════════════════════════════════
    //  12 条规则（顺序 = Content.ROLES）
    // ══════════════════════════════════════════════════════════════

    /**
     * 规则名：选人界面上一眼能读懂的四个字。
     *
     * <p>命名统一为「否定式」——它描述的是这个职业<b>不做什么</b>，因为构筑的形状来自拒绝。
     */
    private static final String[] NAME = {
            "轻装疾射", // 0 游侠：不穿甲、不缠绷带，只靠身法
            "铁壁缓行", // 1 守卫：不抢攻速、不靠跑位，站着把线守住
            "不斩之誓", // 2 星医：不追暴击、不处决，只把人从血泊里拉回来
            "纯元素",   // 3 术士：不碰纯物理，只让元素词条滚雪球
            "覆盖射击", // 4 炼金：不信瞄得准，只信炸得广
            "轻甲",     // 5 影舞：穿不了重装（CAP≥4 一律不收）
            "齿轮为命", // 6 机铠：不靠血肉自愈、不靠双腿跑位
            "正面颂唱", // 7 圣歌：不使暗器、不闪不避，站在明处唱完
            "唯霜",     // 8 霜灵：不碰火与雷，只信一种温度
            "以血换血", // 9 血誓：不举盾、不赌暴击，用伤口换伤害
            "只信导线", // 10 雷铳：不碰生命流派，血肉会漏电
            "弃形"      // 11 虚空行者：不回复、不暴击，靠裂隙与处决
    };

    /** 规则一句话说明（≤30 字，选人界面单行显示；{@code tests/ClassRulesTest} 会检查长度）。 */
    private static final String[] DESC = {
            "禁护盾/生命/回复类遗物；移速 ×1.7、火力 ×1.2",
            "禁攻速与移速类遗物；护盾 ×1.9、回复 ×1.3",
            "禁暴击与处决类遗物；回复 ×1.9、护盾 ×1.5",
            "禁纯物理（无元素词条）遗物；元素 ×1.8",
            "禁暴击与精密穿透；弹道 ×1.55、元素 ×1.45",
            "禁 CAP≥4 的重装；暴击 ×1.8、移速 ×1.7",
            "禁回复与位移类遗物；火力 ×1.7、弹道 ×1.4",
            "禁位移与弹道技巧；回复 ×1.8、护盾 ×1.5",
            "禁余烬与雷光流派；霜星 ×2.0、护盾 ×1.2",
            "禁护盾与暴击类遗物；火力 ×1.8、续航 ×1.4",
            "禁生命流派；雷光 ×1.9、弹道 ×1.15",
            "禁回复与暴击类遗物；元素 ×1.5、弹道 ×1.5"
    };

    /** 各职业额外点名封禁的遗物（少量「机制上就不该给」的件，与主轴互补）。 */
    private static final int[][] BAN_IDS = {
            /* 0 游侠   */ {},
            /* 1 守卫   */ {},
            /* 2 星医   */ {Relics.ARMOR_SHARD, Relics.EXECUTIONER, Relics.RIFT_BLADE},
            /* 3 术士   */ {},
            /* 4 炼金   */ {Relics.PIERCE_NEEDLE, Relics.FROST_CROWN, Relics.RICOCHET_CHARM, Relics.BEAM_FOCUS},
            /* 5 影舞   */ {},
            /* 6 机铠   */ {},
            /* 7 圣歌   */ {},
            /* 8 霜灵   */ {},
            /* 9 血誓   */ {},
            /* 10 雷铳  */ {},
            /* 11 虚空  */ {}
    };

    /** 软偏好：额外加权的轴。 */
    private static final int[] BOOST_TAG = {
            T_MOVE,                 // 0 游侠
            T_GUARD,                // 1 守卫
            T_SUSTAIN,              // 2 星医
            T_ELEM,                 // 3 术士
            T_ART,                  // 4 炼金
            T_CRIT,                 // 5 影舞
            T_DPS,                  // 6 机铠
            T_SUSTAIN,              // 7 圣歌
            T_ELEM,                 // 8 霜灵
            T_DPS,                  // 9 血誓
            T_ELEM,                 // 10 雷铳
            T_ELEM | T_ART          // 11 虚空行者
    };

    private static final float[] BOOST_TAG_W = {
            1.70f, 1.90f, 1.90f, 1.80f, 1.55f, 1.80f, 1.70f, 1.80f, 1.40f, 1.80f, 1.20f, 1.50f
    };

    /** 软偏好：被压低的轴（不封禁，只是很少出现，保留「万一」的意外）。 */
    private static final int[] DAMP_TAG = {
            T_GUARD | T_SUSTAIN,    // 0 游侠：不穿甲不缠绷带
            T_DPS | T_MOVE,         // 1 守卫
            T_CRIT | T_DPS,         // 2 星医
            T_DPS | T_CRIT,         // 3 术士
            T_CRIT,                 // 4 炼金
            T_GUARD,                // 5 影舞
            T_SUSTAIN | T_MOVE,     // 6 机铠
            T_MOVE | T_ART,         // 7 圣歌
            0,                      // 8 霜灵（靠流派禁，不额外压）
            T_GUARD | T_CRIT,       // 9 血誓
            T_SUSTAIN,              // 10 雷铳
            T_SUSTAIN | T_CRIT      // 11 虚空行者
    };

    private static final float[] DAMP_TAG_W = {
            0.35f, 0.40f, 0.30f, 0.45f, 0.40f, 0.40f, 0.30f, 0.45f, 1f, 0.25f, 0.60f, 0.35f
    };

    /** 职业主流派加权（-1 = 不额外加权）。 */
    private static final int[] BOOST_SCHOOL = {
            1, 3, 3, 2, 0, 1, 4, 3, 2, 0, 4, 5
    };

    private static final float[] BOOST_SCHOOL_W = {
            1.25f, 1.15f, 1.15f, 1.30f, 1.30f, 1.25f, 1.20f, 1.20f, 2.00f, 1.30f, 1.90f, 1.40f
    };

    /** 权重下界 / 上界：任何遗物都保留「被看见」的可能，也绝不允许一家独大。 */
    public static final float MIN_W = 0.20f, MAX_W = 2.00f;

    // ══════════════════════════════════════════════════════════════
    //  查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 该职业能否被<b>推荐</b>这件遗物。
     *
     * <p>硬门槛，参与三选一与商店备货的过滤；不参与 {@code grantRelic}——事件、锻造、
     * 神龛这些「已经发生」的奖励不回溯检查，否则存档里已有的遗物会变成非法状态。
     *
     * @param role    职业下标（越界视为无规则，返回 true）
     * @param relicId 遗物下标（越界返回 false）
     */
    public static boolean allows(int role, int relicId) {
        if (relicId < 0 || relicId >= Relics.COUNT) return false;
        if (role < 0 || role >= RULE_COUNT) return true;
        int tag = TAG[relicId];
        switch (role) {
            case 0:  // 游侠：轻装。护盾/生命/反伤与一切续航件都不收，只信身法。
                return (tag & (T_GUARD | T_SUSTAIN)) == 0;
            case 1:  // 守卫：铁壁。攻速与移速件与「站着守线」冲突，全部不收。
                return (tag & (T_DPS | T_MOVE)) == 0;
            case 2:  // 星医：不斩。暴击与处决/处刑类与「救人」定位互斥（另点名 3 件）。
                return (tag & T_CRIT) == 0 && !bannedId(role, relicId);
            case 3:  // 术士：纯元素。带任何元素词条的都收；不带元素词条的纯物理件一律不收。
                if ((tag & T_ELEM) != 0) return true;
                return (tag & (T_DPS | T_ART | T_CRIT)) == 0;
            case 4:  // 炼金：覆盖。暴击与需要瞄准的穿透/弹射件不收（另点名 4 件）。
                return (tag & T_CRIT) == 0 && !bannedId(role, relicId);
            case 5:  // 影舞：轻甲。CAP≥4 的重装件穿不动（与硬编码 CAP 表联动，改表即生效）。
                return Relics.CAP[relicId] < 4;
            case 6:  // 机铠：齿轮。回复与位移件不收——机铠靠炮台站桩，不靠血肉与腿脚。
                return (tag & (T_SUSTAIN | T_MOVE)) == 0;
            case 7:  // 圣歌：正面。位移与弹道技巧（暗器）不收，只留正面硬碰硬的东西。
                return (tag & (T_MOVE | T_ART)) == 0;
            case 8:  // 霜灵：唯霜。余烬(0)与雷光(4)两个流派整系不收。
                return Relics.SCHOOL[relicId] != 0 && Relics.SCHOOL[relicId] != 4;
            case 9:  // 血誓：以血换血。护盾与暴击不收——他不挡，也不赌。
                return (tag & (T_GUARD | T_CRIT)) == 0;
            case 10: // 雷铳：只信导线。生命(3)流派整系不收。
                return Relics.SCHOOL[relicId] != 3;
            case 11: // 虚空行者：弃形。回复与暴击不收，靠裂隙与处决。
                return (tag & (T_SUSTAIN | T_CRIT)) == 0;
            default:
                return true;
        }
    }

    private static boolean bannedId(int role, int relicId) {
        for (int id : BAN_IDS[role]) if (id == relicId) return true;
        return false;
    }

    /**
     * 软偏好：三选一 / 商店备货时的抽取权重。
     *
     * <p>不做硬过滤，只让「顺路」的遗物更常出现。返回值恒在
     * [{@link #MIN_W}, {@link #MAX_W}]；无规则时返回 1。
     */
    public static float weightMul(int role, int relicId) {
        if (relicId < 0 || relicId >= Relics.COUNT) return 1f;
        if (role < 0 || role >= RULE_COUNT) return 1f;
        int tag = TAG[relicId];
        float w = 1f;
        if ((tag & BOOST_TAG[role]) != 0) w *= BOOST_TAG_W[role];
        if ((tag & DAMP_TAG[role]) != 0) w *= DAMP_TAG_W[role];
        if (Relics.SCHOOL[relicId] == BOOST_SCHOOL[role]) w *= BOOST_SCHOOL_W[role];
        if (w < MIN_W) w = MIN_W;
        if (w > MAX_W) w = MAX_W;
        return w;
    }

    /** 规则名（四个字，选人界面标题）。 */
    public static String ruleName(int role) {
        if (role < 0 || role >= RULE_COUNT) return "";
        return NAME[role];
    }

    /** 规则一句话说明（选人界面副标题，≤30 字）。 */
    public static String ruleDesc(int role) {
        if (role < 0 || role >= RULE_COUNT) return "";
        return DESC[role];
    }

    /** 该职业合法遗物数量（供测试与平衡巡检；O(60)，不要放进每帧循环）。 */
    public static int legalCount(int role) {
        int n = 0;
        for (int i = 0; i < Relics.COUNT; i++) if (allows(role, i)) n++;
        return n;
    }

    /** 自检：表长、主轴与流派合法、规则不饿死职业。返回 null 表示全部通过。 */
    public static String validate() {
        if (TAG.length != Relics.COUNT) return "TAG 长度 " + TAG.length + " != " + Relics.COUNT;
        if (NAME.length != RULE_COUNT || DESC.length != RULE_COUNT) return "NAME/DESC 长度错误";
        if (BOOST_TAG.length != RULE_COUNT || DAMP_TAG.length != RULE_COUNT) return "偏好表长度错误";
        if (BAN_IDS.length != RULE_COUNT) return "BAN_IDS 长度错误";
        for (int i = 0; i < Relics.COUNT; i++) if (TAG[i] == 0) return "遗物 #" + i + " 没有主轴";
        for (int r = 0; r < RULE_COUNT; r++) {
            int legal = legalCount(r);
            if (legal < 25) return "职业 " + r + " 只剩 " + legal + " 件合法遗物（<25）";
            int banned = Relics.COUNT - legal;
            if (banned < 5) return "职业 " + r + " 只封禁 " + banned + " 件（<5，规则不成立）";
            if (DESC[r] == null || DESC[r].length() > 30) return "职业 " + r + " 规则说明过长";
        }
        for (int i = 0; i < Relics.COUNT; i++) {
            boolean any = false;
            for (int r = 0; r < RULE_COUNT && !any; r++) any = allows(r, i);
            if (!any) return "遗物 #" + i + " 对所有职业都非法";
        }
        return null;
    }

    /** 命令行自检：{@code java -cp ... com.dilao.core.ClassRules}。 */
    public static void main(String[] args) {
        String err = validate();
        if (err != null) {
            System.out.println("ClassRules INVALID: " + err);
            System.exit(1);
        }
        for (int r = 0; r < RULE_COUNT; r++) {
            System.out.printf("%2d %-5s %-6s legal=%2d banned=%2d :: %s%n",
                    r, Content.ROLES[r], ruleName(r), legalCount(r), Relics.COUNT - legalCount(r), ruleDesc(r));
        }
    }
}
