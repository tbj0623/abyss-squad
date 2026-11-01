package com.dilao.core;

/**
 * 36 条武器进化 = 12 职业 × 3 条线。
 * <p>布局：{@code index = role*3 + line}。
 * <ul>
 *   <li>line 0 与 line 1 是<b>第一进化</b>（TIER=1），在 Lv5 后的铁匠铺/三选一可直接选取。</li>
 *   <li>line 2 是<b>进阶进化</b>（TIER=2），需要 Lv8 且持有指定的某件遗物（REQ_RELIC &gt;= 0）。</li>
 * </ul>
 * 进化会替换弹道行为（FLAGS），并调整 POWER/RATE/AREA。
 */
public final class Evolutions {
    private Evolutions() {}

    public static final int COUNT = 36;

    public static final String[] NAMES = {
            // 游侠（疾风）
            "裂空三叉", "鹰眼穿刺", "风暴之环",
            // 守卫（生命）
            "堡垒重炮", "荆棘回响", "不动王座",
            // 星医（生命）
            "星辉权杖", "愈光之环", "群星圣愈",
            // 术士（霜星）
            "霜痕贯穿", "冰河爆裂", "绝对零界",
            // 炼金（余烬）
            "连锁爆燃", "熔渣地雷", "末日坩埚",
            // 影舞（疾风）
            "千刃回旋", "残影连击", "无影终舞",
            // 机铠（雷光）
            "雷网炮阵", "磁暴散射", "零式齐射",
            // 圣歌（生命）
            "共鸣领域", "圣咏扩散", "生命洪流",
            // 霜灵（霜星）
            "冰棱三重", "永冻之息", "寒渊之眼",
            // 血誓（余烬）
            "嗜血刃舞", "血焰喷发", "赤色盛宴",
            // 雷铳（雷光）
            "万雷贯穿", "落雷弹幕", "电磁风暴",
            // 虚空行者（虚空）
            "裂隙跳跃", "虚空吞噬", "深渊回响"
    };

    /**
     * 进化描述。每条都必须与 {@link #MECHANIC} 的语义严格对应——
     * 描述承诺的机制就是实现要交付的机制，不允许出现"描述了但机制原型不同"的错配。
     *
     * <p>写作口径：一句话说清「触发条件 → 产生什么 → 对谁生效」，
     * 不写"更强大""更密集"这类无法验证的形容词。
     */
    public static final String[] DESC = {
            // 0 游侠 line0 裂空三叉：M0 纯 FLAGS（MULTISHOT|SPLITSHOT）
            "每次射击额外分出两枚斜向弹丸，呈扇形覆盖",
            // 1 游侠 line1 鹰眼穿刺：M0 纯 FLAGS（PIERCE|BEAM）——穿透与贯穿都由标志位表达
            "弹丸穿透 +2 且远距离伤害不再衰减",
            // 2 游侠 line2 风暴之环：M5 场地生成（ORBIT）
            "绕着自身旋转的弹丸，持续削切开靠近的敌人",
            // 3 守卫 line0 堡垒重炮：M8 二段判定（EXPLODE|KNOCKBACK）
            "弹丸命中时追加一次范围震击，并击退受击敌人",
            // 4 守卫 line1 荆棘回响：M14 支援转化（REGEN|THORNS）
            "受到伤害时把反伤的一部分转化为自身治疗",
            // 5 守卫 line2 不动王座：M3 低血动态（CHARGE）
            "生命越低，攻击速度越快，最低生命时攻速翻倍",
            // 6 星医 line0 星辉权杖：M14 支援转化（BEAM|REGEN）
            "弹丸命中敌人时，为弹道附近的友军回复少量生命",
            // 7 星医 line1 愈光之环：M14 支援转化（REGEN|AURA）
            "每次治疗在目标周围产生一圈光爆，波及范围内友军",
            // 8 星医 line2 群星圣愈：M4 技能改写（SHIELD|REGEN）
            "改写技能：释放技能时全队回满生命并附加护盾",
            // 9 术士 line0 霜痕贯穿：M0 纯 FLAGS（FREEZE|RICOCHET）
            "弹丸在敌人之间自动弹射一次",
            // 10 术士 line1 冰河爆裂：M12 状态转化（EXPLODE|FREEZE）
            "命中的冻结敌人原地炸裂成冰刺，对周围造成伤害",
            // 11 术士 line2 绝对零界：M12 状态转化（FREEZE|STUN）
            "所有弹丸附带冻结；冰缓叠满时直接转为冻结并眩晕",
            // 12 炼金 line0 连锁爆燃：M12 状态转化（EXPLODE|BURN）
            "命中叠加燃烧层，燃烧叠满时目标炸裂并引燃周围",
            // 13 炼金 line1 熔渣地雷：M0 纯 FLAGS（EXPLODE|MINE）
            "攻击间歇在脚下埋设地雷",
            // 14 炼金 line2 末日坩埚：M9 全局计时（EXPLODE|BURN）
            "每隔数秒在敌人最密集处引爆一次，间隔随战斗时间缩短",
            // 15 影舞 line0 千刃回旋：M1 回旋镖（RICOCHET|CRIT）
            "弹丸命中后折返，往返都造成伤害，返程必定暴击",
            // 16 影舞 line1 残影连击：M2 连击递增（MULTISHOT|CRIT）
            "连续命中同一目标时攻速递增，换目标后重置",
            // 17 影舞 line2 无影终舞：M11 位移联动（MULTISHOT|CRIT）
            "闪避后立刻触发一次全方向弹幕，并重置连击计数",
            // 18 机铠 line0 雷网炮阵：M4 技能改写（CHAIN|VOLT）
            "改写炮台：炮台改为持续发射闪电网格，命中连锁",
            // 19 机铠 line1 磁暴散射：M8 二段判定（VOLT|SPLITSHOT）
            "射击同时向前方抛射磁暴弹，落地后追加一次电击",
            // 20 机铠 line2 零式齐射：M4 技能改写（CHAIN|VOLT）
            "改写炮台：炮台数量翻倍，射程与连锁跳数同步提升",
            // 21 圣歌 line0 共鸣领域：M0 纯 FLAGS（REGEN|AURA）
            "光环范围扩大，范围内友军持续回血",
            // 22 圣歌 line1 圣咏扩散：M6 增益扩散（SHIELD|AURA）
            "所有增益效果额外扩散一层给附近友军",
            // 23 圣歌 line2 生命洪流：M7 溢出转化（SHIELD|REGEN）
            "治疗量溢出的部分转化为护盾，不再浪费",
            // 24 霜灵 line0 冰棱三重：M0 纯 FLAGS（HOMING|FREEZE）
            "每次射击发射三枚追踪冰棱",
            // 25 霜灵 line1 永冻之息：M12 状态转化（FREEZE|SLOWFIELD）
            "冰缓效果缓慢叠加，叠满时转为冻结",
            // 26 霜灵 line2 寒渊之眼：M5 场地生成（SLOWFIELD|VOID）
            "在敌人最密集处生成持续冰渊，范围内敌人持续减速",
            // 27 血誓 line0 嗜血刃舞：M12 状态转化（EXPLODE|LIFESTEAL）
            "命中附带流血，流血目标死亡时爆裂并传染周围",
            // 28 血誓 line1 血焰喷发：M3 低血动态（MULTISHOT|BURN）
            "血量越低弹丸越多，最低生命时额外 +2 弹",
            // 29 血誓 line2 赤色盛宴：M13 击杀堆叠（LIFESTEAL|CRIT）
            "每击杀一个敌人回复生命并叠加攻速，倒地时清空",
            // 30 雷铳 line0 万雷贯穿：M8 二段判定（BEAM|VOLT）
            "弹丸穿透后在终点炸开成雷网，追加一次范围电击",
            // 31 雷铳 line1 落雷弹幕：M8 二段判定（VOLT|STUN）
            "命中时在该位置降下第二道落雷，并眩晕受击者",
            // 32 雷铳 line2 电磁风暴：M9 全局计时（CHAIN|VOLT）
            "全场敌人周期被雷击，间隔随战斗时间递减",
            // 33 虚空行者 line0 裂隙跳跃：M11 位移联动（TELEPORT|VOID）
            "击杀敌人后短距离瞬移，并获得一层护甲侵蚀",
            // 34 虚空行者 line1 虚空吞噬：M10 剥离（EXECUTE|VOID）
            "弹丸命中会剥离敌人的护盾与增益，转化为自身护盾",
            // 35 虚空行者 line2 深渊回响：M4 技能改写（VOID|AURA）
            "改写终极：冷却减半，释放时对全场施加虚空侵蚀"
    };

    public static final String[] FLAVOR = {
            "一条线分成三条，三条都不讲道理。", "先看穿，再穿透。", "风绕着你转，也绕着敌人转。",
            "把城墙推出去。", "痛会回来找你。", "坐着不动，也是一种进攻。",
            "光从杖尖流向别人。", "治愈会扩散，像涟漪。", "群星记得每一次呼喊。",
            "冷到骨头里，再穿出去。", "冰不会退让。", "这里没有温度这个词。",
            "先烧穿，再连着烧。", "留下一点惊喜在脚下。", "锅开了，别站在旁边。",
            "刃折返的时候更快。", "越来越快，快到看不见。", "影子也会累，但不会停。",
            "电流在地上织网。", "磁场把铁屑都吸过来。", "协议执行完毕。",
            "声音能治愈，也能扩散。", "一个人唱，一队人听见。", "光满溢出来就成了盾。",
            "三根冰棱，三个结局。", "冷到时间都不流了。", "眼睛在最深处睁开。",
            "血会引路。", "烧到自己都不剩。", "宴会开始了。",
            "雷穿过一切。", "第二道总是更响。", "天空暂时归你。",
            "一步之间两个地方。", "吞掉它的，成为你的。", "深渊也会回话。"
    };

    public static final int[] ROLE = {
            0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3,
            4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7,
            8, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11, 11
    };

    /** line2 是进阶进化（TIER=2）。 */
    public static final int[] TIER = {
            1, 1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2,
            1, 1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2,
            1, 1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2
    };

    /** TIER=2 需要的遗物 id（-1 = 无需求）。 */
    public static final int[] REQ_RELIC = {
            -1, -1, Relics.WIND_GEAR, -1, -1, Relics.GUARDIAN_SIGIL, -1, -1, Relics.MEDIC_LAMP,
            -1, -1, Relics.WINTER_CROWN, -1, -1, Relics.FIRE_HEART, -1, -1, Relics.AFTERIMAGE,
            -1, -1, Relics.PULSE_COIL, -1, -1, Relics.UNDYING_FLAME, -1, -1, Relics.PERMAFROST,
            -1, -1, Relics.BLOOD_RING, -1, -1, Relics.THUNDER_CROWN, -1, -1, Relics.VOID_ENGINE
    };

    public static final int[] FLAGS = {
            Content.F_MULTISHOT | Content.F_SPLITSHOT, Content.F_PIERCE | Content.F_BEAM, Content.F_ORBIT,
            Content.F_EXPLODE | Content.F_KNOCKBACK, Content.F_THORNS | Content.F_REGEN, Content.F_CHARGE,
            Content.F_REGEN | Content.F_BEAM, Content.F_REGEN | Content.F_AURA, Content.F_REGEN | Content.F_SHIELD,
            Content.F_FREEZE | Content.F_RICOCHET, Content.F_FREEZE | Content.F_EXPLODE, Content.F_FREEZE | Content.F_STUN,
            Content.F_BURN | Content.F_EXPLODE, Content.F_MINE | Content.F_EXPLODE, Content.F_EXPLODE | Content.F_BURN,
            Content.F_RICOCHET | Content.F_CRIT, Content.F_CRIT | Content.F_MULTISHOT, Content.F_CRIT | Content.F_MULTISHOT,
            Content.F_VOLT | Content.F_CHAIN, Content.F_VOLT | Content.F_SPLITSHOT, Content.F_VOLT | Content.F_CHAIN,
            Content.F_AURA | Content.F_REGEN, Content.F_AURA | Content.F_SHIELD, Content.F_REGEN | Content.F_SHIELD,
            Content.F_FREEZE | Content.F_HOMING, Content.F_FREEZE | Content.F_SLOWFIELD, Content.F_SLOWFIELD | Content.F_VOID,
            Content.F_LIFESTEAL | Content.F_EXPLODE, Content.F_BURN | Content.F_MULTISHOT, Content.F_LIFESTEAL | Content.F_CRIT,
            Content.F_BEAM | Content.F_VOLT, Content.F_VOLT | Content.F_STUN, Content.F_VOLT | Content.F_CHAIN,
            Content.F_TELEPORT | Content.F_VOID, Content.F_VOID | Content.F_EXECUTE, Content.F_VOID | Content.F_AURA
    };

    public static final float[] POWER = {
            .05f, .12f, .08f, .14f, .06f, .10f, .08f, .10f, .12f, .12f, .16f, .18f,
            .14f, .10f, .20f, .12f, .16f, .18f, .10f, .14f, .16f, .06f, .08f, .10f,
            .14f, .12f, .20f, .12f, .18f, .22f, .14f, .16f, .20f, .12f, .16f, .24f
    };

    public static final float[] RATE = {
            .04f, 0, .06f, -.04f, 0, .06f, 0, .04f, 0, .04f, 0, 0,
            .04f, .06f, -.08f, .10f, .08f, .14f, .06f, 0, .08f, 0, 0, 0,
            .08f, .04f, 0, .08f, .04f, .10f, .06f, .08f, .12f, .10f, .06f, .08f
    };

    public static final float[] AREA = {
            .10f, .05f, .14f, .12f, .10f, .06f, .14f, .16f, .18f, .10f, .16f, .22f,
            .14f, .10f, .20f, .06f, .08f, .10f, .10f, .12f, .14f, .20f, .18f, .16f,
            .10f, .16f, .20f, .12f, .10f, .16f, .10f, .12f, .18f, .10f, .16f, .22f
    };

    public static final float[] SPEEDVAL = {
            0, .02f, 0, -.02f, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, .06f, .04f, .08f, 0, 0, 0, 0, 0, 0,
            0, 0, 0, .04f, 0, 0, 0, 0, 0, .08f, .06f, .10f
    };

    public static final String[] KEY = {
            "ranger_split", "ranger_pierce", "ranger_storm",
            "guard_cannon", "guard_thorns", "guard_throne",
            "medic_wand", "medic_halo", "medic_grace",
            "warlock_shatter", "warlock_glacier", "warlock_zero",
            "alch_chain", "alch_mine", "alch_cauldron",
            "dancer_spin", "dancer_combo", "dancer_final",
            "mech_grid", "mech_magnet", "mech_volley",
            "bard_field", "bard_choir", "bard_flood",
            "frost_triple", "frost_breath", "frost_abyss",
            "blood_blade", "blood_eruption", "blood_feast",
            "bolt_pierce", "bolt_barrage", "bolt_storm",
            "void_step", "void_devour", "void_echo"
    };

    /**
     * 机制原型表：36 条进化各用一种机制原型实现，与 {@link #FLAGS} 互补。
     * <p>{@code FLAGS} 表达的是"弹道属性"，本表表达的是"状态机"——回旋、叠层、低血动态、
     * 技能改写、场地生成等无法用单个位表示的机制。消费方应优先读本表分发，FLAGS 作为兜底。
     *
     * <pre>
     *   0  纯 FLAGS     现有标志位已足够，不额外实现（11 条）
     *   1  回旋镖       命中后返回，往返都造成伤害
     *   2  连击递增     连续命中同一目标时攻速/伤害递增
     *   3  低血动态     血量越低，弹丸数量或攻速越高
     *   4  技能改写     改写 doSkill / doUltimate 的行为
     *   5  场地生成     在敌人最密集处生成持续区域
     *   6  增益扩散     增益效果额外扩散一层给队友
     *   7  溢出转化     治疗/护盾溢出部分转化为另一种资源
     *   8  二段判定     命中后再追加一次独立攻击判定
     *   9  全局计时     全场周期性事件，间隔随战斗时间递减
     *  10  剥离         吞噬敌人的护盾/增益并转为己用
     *  11  位移联动     闪避或击杀后触发一次额外行为
     *  12  状态转化     一条状态链推进（冰缓→冻结、流血→爆裂）
     *  13  击杀堆叠     击杀累积层数并转化为属性，倒地时清空
     *  14  支援转化     命中或治疗产生第二段支援效果（回血/护盾/光爆）
     * </pre>
     */
    public static final int[] MECHANIC = {
            // 游侠：扇形分叉 / 穿透不衰减 / 环绕刃环
            0, 0, 5,
            // 守卫：命中震击 / 反伤转治疗 / 低血攻速
            8, 14, 3,
            // 星医：命中治疗友军 / 治疗光爆 / 技能改写（全队满血+盾）
            14, 14, 4,
            // 术士：弹射附冰 / 冻结炸裂冰刺 / 冰缓满转冻结
            0, 12, 12,
            // 炼金：燃烧叠层连锁 / 脚下埋雷 / 周期大爆炸
            12, 0, 9,
            // 影舞：命中折返 / 连击递增 / 闪避触发弹幕
            1, 2, 11,
            // 机铠：技能改写（炮台闪电网）/ 磁暴二段 / 技能改写（炮台翻倍）
            4, 8, 4,
            // 圣歌：光环范围回血 / 增益扩散 / 治疗溢出转盾
            0, 6, 7,
            // 霜灵：三枚追踪冰棱 / 冰缓叠至冻结 / 密集处冰渊
            0, 12, 5,
            // 血誓：流血爆裂 / 低血弹丸增多 / 击杀堆叠攻速
            12, 3, 13,
            // 雷铳：终点炸雷网 / 二段落雷 / 全场周期雷击
            8, 8, 9,
            // 虚空行者：击杀瞬移蚀甲 / 剥离护盾增益 / 技能改写（终极减半）
            11, 10, 4
    };

    /** MECHANIC 原型数量（0..14，共 15 种）。 */
    public static final int MECHANIC_COUNT = 15;

    /** 读某条进化的机制原型，越界回退到 0（纯 FLAGS），保证消费方不会崩。 */
    public static int mechanic(int index) {
        if (index < 0 || index >= COUNT) return 0;
        return MECHANIC[index];
    }

    /** index = role*3 + line */
    public static int index(int role, int line) {
        if (role < 0) role = 0;
        if (role >= Content.ROLE_COUNT) role = Content.ROLE_COUNT - 1;
        if (line < 0) line = 0;
        if (line > 2) line = 2;
        return role * 3 + line;
    }

    public static boolean valid() {
        int[] lens = {NAMES.length, DESC.length, FLAVOR.length, ROLE.length, TIER.length,
                REQ_RELIC.length, FLAGS.length, POWER.length, RATE.length, AREA.length,
                SPEEDVAL.length, KEY.length, MECHANIC.length};
        for (int n : lens) if (n != COUNT) return false;
        for (int i = 0; i < COUNT; i++) {
            if (ROLE[i] != i / 3) return false;
            if (TIER[i] != (i % 3 == 2 ? 2 : 1)) return false;
            if (TIER[i] == 2 && REQ_RELIC[i] < 0) return false;
            if (TIER[i] == 1 && REQ_RELIC[i] >= 0) return false;
            if (REQ_RELIC[i] >= Relics.COUNT) return false;
            if (MECHANIC[i] < 0 || MECHANIC[i] >= MECHANIC_COUNT) return false;
        }
        // 每个职业的三条进化不能是同一个原型，否则"三条线"名不副实
        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            int a = MECHANIC[role * 3], b = MECHANIC[role * 3 + 1], c = MECHANIC[role * 3 + 2];
            if (a == b && b == c) return false;
        }
        // DESC 必须与 MECHANIC 语义对齐：每种原型有一条"必备关键词"，
        // 描述里若不出现该关键词，说明文案在讲另一件机制（历史上出过 20 条错配）。
        for (int i = 0; i < COUNT; i++) {
            String d = DESC[i];
            if (d == null || d.isEmpty()) return false;
            String[] keys = MECHANIC_KEYWORDS[MECHANIC[i]];
            if (keys.length == 0) continue;            // 原型 0（纯 FLAGS）不做关键词约束
            boolean hit = false;
            for (String k : keys) if (d.contains(k)) { hit = true; break; }
            if (!hit) {
                System.err.println("Evolutions.DESC[" + i + "](" + NAMES[i] + ") 与 MECHANIC="
                        + MECHANIC[i] + " 不对齐：描述需包含 " + java.util.Arrays.toString(keys));
                return false;
            }
        }
        return true;
    }

    /**
     * 每种机制原型的"必备关键词"（DESC 必须命中其一）。
     * <p>下标与 {@link #MECHANIC} 取值一一对应；空数组表示不做约束。
     */
    private static final String[][] MECHANIC_KEYWORDS = {
            {},                                              // 0 纯 FLAGS
            {"折返", "返回", "回旋"},                         // 1 回旋镖
            {"连续命中", "连击", "递增"},                      // 2 连击递增
            {"生命越低", "血量越低", "低血"},                  // 3 低血动态
            {"改写", "冷却减半"},                             // 4 技能改写
            {"生成", "冰渊", "旋转", "绕"},                    // 5 场地生成
            {"扩散"},                                        // 6 增益扩散
            {"溢出"},                                        // 7 溢出转化
            {"追加", "第二道", "震击", "二段"},                // 8 二段判定
            {"每秒", "周期", "间隔", "全场"},                  // 9 全局计时
            {"剥离", "吞噬", "夺取"},                         // 10 剥离
            {"闪避后", "击杀敌人后", "击杀后", "瞬移"},         // 11 位移联动
            {"叠满", "转为", "爆裂", "炸裂", "传染"},           // 12 状态转化
            {"每击杀", "击杀累积", "清空"},                    // 13 击杀堆叠
            {"转化为", "光爆", "回复", "护盾"}                 // 14 支援转化
    };

    public static void main(String[] args) {
        if (!valid()) {
            System.out.println("Evolutions FAILED");
            System.exit(1);
        }
        System.out.println("Evolutions valid: " + COUNT);
        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            System.out.println("  " + Content.ROLES[role] + " : "
                    + mechanic(role * 3) + " / " + mechanic(role * 3 + 1) + " / " + mechanic(role * 3 + 2));
        }
    }
}
