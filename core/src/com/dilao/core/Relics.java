package com.dilao.core;

/**
 * 60 件遗物：6 流派 × 10 件。
 * <p>RARITY 每流派分布：3 普通 / 4 稀有 / 2 史诗 / 1 传说。
 * <p>POWER/RATE/AREA/SPEEDVAL 是<b>每层加算的乘区增量</b>；
 * MAG 表示该遗物的 FLAGS 效果强度/层，由引擎按标志位解释。
 */
public final class Relics {
    private Relics() {}

    public static final int COUNT = 60;

    // ══════════════════════════════════════════════════════════════
    //  便利索引（与下方数组顺序严格一一对应）
    // ══════════════════════════════════════════════════════════════
    // 余烬 0..9
    public static final int SPLIT_PRISM = 0, FIRE_HEART = 1, EMBER_CLOAK = 2, BLOOD_RING = 3,
            TWIN_GEAR = 4, ARMOR_SHARD = 5, TIDE_LENS = 6, SOUL_WICK = 7, BURST_SEED = 8,
            EMBER_CROWN = 9;
    // 疾风 10..19
    public static final int WIND_GEAR = 10, FEATHER_CLOAK = 11, ECHO_BOOTS = 12,
            STARPIERCER = 13, STARDUST_PULL = 14, LUCKY_DICE = 15, GALE_WINGS = 16,
            AFTERIMAGE = 17, SWIFT_CHARM = 18, STORM_CORE = 19;
    // 霜星 20..29
    public static final int FROST_SHARD = 20, FROST_CROWN = 21, ICE_BOTTLE = 22,
            COLD_BREATH = 23, FROST_MAIL = 24, ICE_PRISM = 25, PERMAFROST = 26,
            SNOW_BLIND = 27, FROST_PENDANT = 28, WINTER_CROWN = 29;
    // 生命 30..39
    public static final int AMBER_HEART = 30, LIFE_SPORE = 31, MEDIC_LAMP = 32,
            REGEN_VINE = 33, GUARDIAN_SIGIL = 34, BLOOD_PACT = 35, THORN_MAIL = 36,
            ECLIPSE_BATTERY = 37, PRAYER_SEAL = 38, UNDYING_FLAME = 39;
    // 雷光 40..49
    public static final int PULSE_COIL = 40, LIGHTNING_BOX = 41, BOLT_QUIVER = 42,
            CHARGE_CAP = 43, ARC_PRISM = 44, STORM_EYE = 45, STATIC_FIELD = 46,
            CONDUCTIVE = 47, EM_CORE = 48, THUNDER_CROWN = 49;
    // 虚空 50..59
    public static final int VOID_SHARD = 50, ABYSS_GAZE = 51, WARP_RING = 52,
            VOID_ENGINE = 53, RIFT_BLADE = 54, DEVOUR_MASK = 55, ENTROPY_HOURGLASS = 56,
            VOID_SIGIL = 57, DARK_MATTER = 58, ABYSS_CROWN = 59;

    // ══════════════════════════════════════════════════════════════
    //  语义别名：全部指向唯一真实 id，供 Game 的 switch/引用使用
    // ══════════════════════════════════════════════════════════════
    public static final int ORBIT_STONE = 16;      // 疾风之翼：环绕弹
    public static final int LEECH_SEED = 7;        // 灵魂灯芯：击杀回血
    public static final int PIERCE_NEEDLE = 13;    // 穿星针：穿透
    public static final int SLOW_FROST = 20;       // 极寒碎片：减速
    public static final int MAGNET_CORE = 14;      // 星尘引力：拾取 + 经验
    public static final int CRIT_LENS = 15;        // 幸运骰子：暴击
    public static final int EXPLOSIVE_SEED = 8;    // 爆裂种子：命中爆炸
    public static final int LOW_HP_RAGE = 3;       // 血契指环：低血增伤
    public static final int SCALE_COIL = 4;        // 双生齿轮：遗物数增伤
    public static final int THUNDER_COIL = 40;     // 脉冲线圈：连锁闪电
    public static final int GREED_COIN = 57;       // 虚无印记：星尘
    public static final int MULTI_CHARM = 9;       // 余烬王冠：多弹丸
    public static final int MINE_KIT = 55;         // 吞噬面具：地雷
    public static final int SPLIT_SHARD = 52;      // 扭曲之环：分裂弹
    public static final int RICOCHET_CHARM = 42;   // 雷矢箭袋：弹射
    public static final int BEAM_FOCUS = 44;       // 弧光棱镜：贯穿光束
    public static final int SUMMON_BELL = 50;      // 虚空裂片：召唤
    public static final int STUN_HAMMER = 48;      // 电磁核心：眩晕
    public static final int WAR_DRUM = 47;         // 导电金属：击退
    public static final int SHIELD_CORE = 34;      // 守护符：护盾
    public static final int REGEN_HEART = 33;      // 再生藤蔓：持续回复
    public static final int CDR = 37;              // 月蚀电池：技能冷却
    public static final int FROST_HEART = 29;      // 凛冬之冠：冰元素
    public static final int CURSED = 59;           // 深渊王冠：诅咒
    public static final int VITAL = 30;            // 琥珀之心：生命上限
    public static final int EXECUTIONER = 51;      // 深渊凝视：处决
    public static final String[] NAMES = {
            "分裂棱镜", "熔火之心", "余烬披风", "血契指环", "双生齿轮",
            "碎甲棱角", "潮汐镜片", "灵魂灯芯", "爆裂种子", "余烬王冠",
            "疾风发条", "轻羽斗篷", "回响靴", "穿星针", "星尘引力",
            "幸运骰子", "疾风之翼", "残影石", "疾行护符", "风暴核心",
            "极寒碎片", "霜花王冠", "冰封瓶", "寒潮之息", "霜纹甲",
            "冰棱棱镜", "永冻之石", "雪盲面具", "霜语吊坠", "凛冬之冠",
            "琥珀之心", "生命孢子", "星医之灯", "再生藤蔓", "守护符",
            "血脉契约", "荆棘护甲", "月蚀电池", "祈求之印", "不灭之火",
            "脉冲线圈", "闪电匣", "雷矢箭袋", "蓄能电容", "弧光棱镜",
            "雷暴之眼", "静电场", "导电金属", "电磁核心", "万雷之冠",
            "虚空裂片", "深渊凝视", "扭曲之环", "虚空引擎", "裂隙之刃",
            "吞噬面具", "熵之沙漏", "虚无印记", "暗物质囊", "深渊王冠"
    };

    public static final String[] FLAVOR = {
            "光线在它内部折了三次才出来。", "熔炉的心脏从未冷却。", "披上它，你知道自己会被点燃。",
            "每一次心跳都写成一行小字。", "两个齿轮咬合时，命运会卡住一瞬。", "它专门找缝下嘴。",
            "潮水记得每一次退去。", "灯芯烧的是别人的影子。", "种子在土里就开始爆炸。",
            "戴上它的人最后都成了炭。", "发条还在转，时间已经走远了。", "轻到会被风吹错方向。",
            "每一步都留下两个脚印。", "针尖上沾着别人的星光。", "它让远处的光自己走过来。",
            "六面都是六点。", "风从不问路怎么走。", "影子里住着一个慢半拍的你。",
            "给你一点，再收回一点。", "风暴眼是安静的。", "碎片比整块更冷。",
            "花开在冰冻之前。", "瓶里的冬天没关上。", "呼出的气会先结冰再落下。",
            "甲片之间结着霜。", "看上去是冰，其实是被冻住的光。", "石头里没有时间。",
            "戴上后雪盲，摘下后看见。", "吊坠晃一下，温度就降一度。", "冠冕戴在冰上。",
            "虫子在里面睡着了。", "孢子在呼吸间长大了。", "灯光只照活着的人。",
            "断掉的地方会再长出来。", "符纸写着一句没说完的话。", "签下的名字不能擦掉。",
            "穿上它的人靠近就会痛。", "电量总在关键时刻剩余一点。", "祈祷的回应总是慢半拍。",
            "烧尽之后还有余温。", "线圈里绕着一场没下的雨。", "雷被关在盒子里等开门。",
            "箭返回来时记得路。", "充满，然后一次放空。", "弧光映出不该看的东西。",
            "它睁着的时候不要抬头。", "靠近时头发会站起来。", "金属习惯了疼痛。",
            "核心的嗡鸣像心跳。", "戴上就听不见雷声了。", "裂片割开的是距离。",
            "深渊也在看你。", "环的里面和外面不是同一个地方。", "引擎烧的是可能性。",
            "刃口没有厚度，只有方向。", "面具后面是空的。", "沙漏里的沙在往回走。",
            "印记烙在看不见的地方。", "囊里装着不发光的东西。", "坐上它的人不会回来。"
    };

    public static final String[] DESC = {
            "弹丸 +1，单发伤害 -18%，偏向近身群攻与扇形覆盖",
            "基础伤害 +9%/层，命中附带 3 秒灼烧且可刷新",
            "闪避时向周围释放一次火环，灼烧近身敌人 2 秒",
            "每层：已损失的生命转为伤害，满损失 +55%",
            "每层：每件遗物 +1.8% 伤害，上限 +40%",
            "处决线 +6%/层，对重型与首领额外 +6% 伤害",
            "伤害 +10%、范围 +4%，弹道更宽更沉，偏向清群",
            "击杀回血 1 点/层，额度每 2 秒补回，上限 6 点",
            "命中爆炸，70 像素内敌人受 38% 溅射",
            "攻击必定点燃目标，弹丸 +1/层，灼烧可叠加",
            "攻击间隔 -6%/层，层数越高出手越密，最多 5 层",
            "移动速度 +7%/层，拾取与走位同步变快",
            "闪避冷却 -12%/层，冲刺后紧接着可以再冲一次",
            "弹丸穿透 +1/层，穿透之后伤害不再衰减",
            "拾取范围 +52，经验 +16%/层，自动吸附",
            "暴击率 +10%/层，上限 75%，暴击双倍伤害",
            "每层环绕一枚弹丸，持续切割靠近你的敌人，可叠加",
            "闪避后下一次攻击必定暴击，效果最多叠加 2 层",
            "移速 +4%/层，受减速影响时间额外缩短一半",
            "伤害 +12%/层，常驻生效，不因移动或站定改变",
            "命中使敌人减速 35%，减速持续 1.5 秒以上",
            "穿透 +1/层，对被减速目标额外造成 15% 伤害",
            "冰缓强度 +0.6 秒/层，减速覆盖更久更难挣脱",
            "被减速的敌人有 8%/层 概率被冻结 0.8 秒",
            "护盾上限 +16/层，脱战后自动回充，等效减伤",
            "弹丸飞行时留下冰痕，经过的敌人被减速 2 秒",
            "击杀回血 1 点/层，对冻结目标额外回复 1 点",
            "暴击率 +4%/层，冻结目标被暴击时额外 +15% 伤害",
            "攻速 +2%/层，技能冷却同步缩短，出手更密",
            "攻击附带冰缓，命中已减速目标再 +18% 伤害",
            "生命上限 +24/层，获得时立刻回复 12 点生命",
            "击杀回血 1 点/层，回复额度上限 +2 点",
            "每秒回复 0.4 生命/层，等价于每 5 秒 2 点",
            "每秒回复 0.8 生命/层，持续叠加不衰减",
            "护盾上限 +26/层，脱战后每秒回充 6%",
            "每层：最大生命 -14，伤害 +10%，代价不退",
            "受到的近战伤害反弹 4 点/层，无视护甲与减伤",
            "技能冷却按指数缩短，层数越高收益就越明显",
            "每秒回复 0.6 生命/层，可与其它回复叠加",
            "伤害 +8%、范围 +12%，生命归零时触发一次爆发",
            "每件遗物提供 +1.5% 伤害/层，上限 +30%",
            "连锁闪电 +1 跳/层，每 4 发触发一次",
            "弹丸命中后有 12% 概率额外弹射一次，最多三次",
            "伤害 +5%/层，击杀时额外积累终极能量",
            "弹丸穿透 3 + 层数，贯穿成列敌人，伤害 +6%",
            "眩晕概率 +1/层，命中时 10% 概率眩晕 0.5 秒",
            "近身反伤光环 6 点/层，90 像素内敌人持续受创",
            "攻击有 10% 概率击退并短暂麻痹，力度随层数增加",
            "眩晕概率 +10%/层，命中时有概率打断敌人",
            "连锁闪电额外 +1 跳，雷光覆盖范围同步扩大",
            "召唤暗影随从 +1/层，继承你 42% 的伤害",
            "处决生命低于 12% 的非首领敌人，直接清空血量",
            "弹丸命中分裂成两枚，各造成 55% 伤害",
            "攻速 +3%/层，终极循环更快，能量积攒更顺",
            "命中时击退敌人并造成额外压制，范围 90 像素",
            "每层周期埋设地雷，踩中爆炸，范围 34 像素",
            "护盾上限 +18/层，护盾存在期间免疫击退",
            "击杀有 10% 概率获得 3 星尘/层，经济滚雪球",
            "伤害 +12%，命中时削弱敌人护甲，可叠加",
            "诅咒：每层承受伤害 +20%，深渊因此记住你"    };

    public static final String[] UPGRADE_HINT = {
            "满层后转化为星尘", "满层后转化为星尘", "闪避流核心", "血量就是弹药",
            "遗物越多越强", "克重装", "每三发一次", "续航核心", "清群核心", "火焰流终点",
            "攻速核心", "跑图核心", "闪避流畅", "穿透核心", "经验滚雪球",
            "暴击核心", "位移强化", "必暴一拍", "抗减速", "走位增益",
            "控场基础", "控场增伤", "延长冰缓", "概率冻结", "霜星防御",
            "冰痕区域", "经济补充", "暴击共鸣", "技能流", "双冰缓",
            "生存基础", "吸血核心", "持续回复", "半血收益", "护盾核心",
            "高风险高回报", "反伤", "技能流", "治疗强化", "保命底牌",
            "万金油增伤", "连锁核心", "弹射", "能量循环", "电弧",
            "控制", "近身压制", "控制强化", "终极流", "雷光终点",
            "破甲", "处决", "清群", "终极流", "近身削血",
            "减伤换治疗", "周期无敌", "经济", "反击", "保命爆发"
    };

    public static final int[] COST = {
            85, 90, 80, 95, 100, 105, 100, 95, 105, 130,
            70, 60, 65, 75, 60, 85, 80, 90, 70, 130,
            70, 75, 65, 85, 80, 90, 95, 105, 90, 130,
            60, 90, 70, 85, 80, 100, 95, 85, 80, 125,
            105, 100, 95, 85, 110, 90, 95, 85, 100, 130,
            110, 120, 105, 100, 115, 95, 110, 90, 105, 135
    };

    public static final int[] SCHOOL = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5
    };

    public static final int[] CAP = {
            5, 5, 3, 4, 4, 3, 3, 4, 3, 2,
            5, 5, 4, 4, 3, 4, 3, 2, 4, 1,
            1, 3, 4, 3, 3, 2, 3, 2, 4, 2,
            4, 3, 5, 3, 2, 2, 3, 2, 3, 1,
            3, 3, 3, 3, 3, 2, 2, 4, 2, 1,
            4, 2, 3, 2, 3, 2, 2, 4, 3, 1
    };

    public static final int[] RARITY = {
            0, 1, 1, 0, 2, 1, 2, 0, 3, 1,
            1, 0, 0, 1, 2, 1, 2, 0, 3, 1,
            0, 1, 1, 0, 2, 1, 2, 3, 0, 1,
            0, 1, 1, 0, 2, 1, 2, 3, 0, 1,
            1, 0, 0, 1, 2, 1, 2, 3, 0, 1,
            1, 0, 0, 1, 2, 1, 2, 3, 0, 1
    };

    public static final int[] ICON = {
            6, 9, 4, 1, 4, 7, 2, 8, 9, 12,
            4, 13, 5, 7, 1, 6, 13, 3, 5, 9,
            7, 12, 14, 10, 3, 7, 10, 8, 14, 12,
            0, 3, 9, 4, 5, 8, 6, 8, 5, 9,
            4, 11, 13, 5, 7, 8, 10, 11, 3, 12,
            7, 8, 1, 4, 7, 8, 5, 6, 1, 12
    };

    /** 标志位，见 {@link Content} 的 F_* 常量。 */
    public static final int[] FLAGS = {
            Content.F_MULTISHOT | Content.F_SPLITSHOT, Content.F_BURN, Content.F_BURN,
            Content.F_BURN, 0, Content.F_PIERCE, 0, Content.F_LIFESTEAL,
            Content.F_EXPLODE, Content.F_BURN,
            Content.F_CRIT, 0, Content.F_TELEPORT, Content.F_PIERCE, Content.F_ORBIT,
            Content.F_CRIT, Content.F_TELEPORT, Content.F_CRIT, 0, 0,
            Content.F_FREEZE, Content.F_FREEZE, Content.F_FREEZE,
            Content.F_FREEZE | Content.F_STUN, 0, Content.F_SLOWFIELD,
            Content.F_FREEZE, Content.F_CRIT, 0, Content.F_FREEZE,
            Content.F_REGEN, Content.F_LIFESTEAL, Content.F_REGEN, Content.F_REGEN,
            Content.F_SHIELD, Content.F_REGEN, Content.F_THORNS, 0, Content.F_REGEN,
            Content.F_REGEN,
            Content.F_CHAIN, Content.F_CHAIN | Content.F_VOLT, Content.F_RICOCHET,
            Content.F_VOLT, Content.F_VOLT, Content.F_VOLT | Content.F_STUN,
            Content.F_AURA, Content.F_STUN | Content.F_KNOCKBACK, Content.F_STUN,
            Content.F_VOLT,
            Content.F_VOID | Content.F_SUMMON, Content.F_EXECUTE, Content.F_SPLIT,
            Content.F_VOID, Content.F_BEAM, Content.F_MINE, Content.F_SHIELD,
            0, Content.F_VOID, Content.F_VOID
    };

    /** FLAGS 效果的强度/层。引擎按标志位解释其含义。 */
    public static final float[] MAG = {
            .5f, .4f, .5f, .3f, .3f, .7f, .5f, .5f, .5f, .8f,
            .3f, .3f, .4f, .8f, .34f, .5f, .4f, .5f, .5f, .8f,
            .8f, .6f, .6f, .4f, .5f, .5f, .5f, .5f, .5f, .8f,
            .8f, .5f, .6f, .5f, .8f, .5f, .8f, .5f, .6f, .7f,
            .6f, .5f, .5f, .5f, .5f, .5f, .5f, .5f, .5f, .8f,
            .4f, .7f, .5f, .5f, .6f, .5f, .6f, .5f, .5f, .8f
    };

    public static final float[] POWER = {
            0, .09f, .04f, .02f, .03f, .05f, .10f, 0, .06f, .15f,
            .02f, 0, 0, .03f, 0, .05f, .04f, .06f, .03f, .12f,
            0, .05f, .03f, .04f, .02f, .05f, .04f, .04f, .04f, .10f,
            0, 0, 0, 0, 0, .14f, 0, 0, 0, .08f,
            .02f, .06f, .05f, .05f, .06f, .05f, .05f, .04f, .06f, .16f,
            .07f, .05f, .05f, .04f, .06f, .05f, .04f, .03f, .05f, .18f
    };

    public static final float[] RATE = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            .06f, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, .02f, 0,
            0, 0, 0, 0, 0, 0, 0, .03f, 0, 0,
            0, 0, .03f, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, .03f, 0, 0, 0, 0, 0, 0
    };

    public static final float[] AREA = {
            0, 0, .05f, 0, 0, 0, .04f, 0, .08f, .10f,
            0, 0, 0, 0, .06f, 0, 0, 0, 0, 0,
            0, 0, 0, .06f, 0, .05f, .05f, 0, 0, .08f,
            0, 0, .06f, 0, .06f, 0, .04f, 0, .05f, .12f,
            0, 0, 0, 0, .06f, .05f, .08f, 0, 0, .10f,
            .05f, .06f, .06f, 0, .06f, 0, 0, 0, 0, .14f
    };

    public static final float[] SPEEDVAL = {
            0, 0, .02f, 0, 0, 0, 0, 0, 0, 0,
            0, .07f, .02f, 0, 0, 0, .05f, 0, .04f, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, .02f, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    public static final String[] KEY = {
            "split_prism", "fire_heart", "ember_cloak", "blood_ring", "twin_gear",
            "armor_shard", "tide_lens", "soul_wick", "burst_seed", "ember_crown",
            "wind_gear", "feather_cloak", "echo_boots", "starpiercer", "stardust_pull",
            "lucky_dice", "gale_wings", "afterimage", "swift_charm", "storm_core",
            "frost_shard", "frost_crown", "ice_bottle", "cold_breath", "frost_mail",
            "ice_prism", "permafrost", "snow_blind", "frost_pendant", "winter_crown",
            "amber_heart", "life_spore", "medic_lamp", "regen_vine", "guardian_sigil",
            "blood_pact", "thorn_mail", "eclipse_battery", "prayer_seal", "undying_flame",
            "pulse_coil", "lightning_box", "bolt_quiver", "charge_cap", "arc_prism",
            "storm_eye", "static_field", "conductive", "em_core", "thunder_crown",
            "void_shard", "abyss_gaze", "warp_ring", "void_engine", "rift_blade",
            "devour_mask", "entropy_hourglass", "void_sigil", "dark_matter", "abyss_crown"
    };

    /** 自检：长度一致、每流派 10 件、稀有度分布正确。 */
    public static boolean valid() {
        int[][] arrays = {
                {NAMES.length, DESC.length, FLAVOR.length, UPGRADE_HINT.length, COST.length,
                        SCHOOL.length, CAP.length, RARITY.length, ICON.length, FLAGS.length,
                        MAG.length, POWER.length, RATE.length, AREA.length, SPEEDVAL.length,
                        KEY.length}
        };
        for (int n : arrays[0]) if (n != COUNT) return false;
        int[] perSchool = new int[Content.SCHOOL_COUNT];
        int[][] perRarity = new int[Content.SCHOOL_COUNT][4];
        for (int i = 0; i < COUNT; i++) {
            int s = SCHOOL[i];
            if (s < 0 || s >= Content.SCHOOL_COUNT) return false;
            perSchool[s]++;
            int r = RARITY[i];
            if (r < 0 || r > 3) return false;
            perRarity[s][r]++;
            if (CAP[i] < 1 || CAP[i] > 5) return false;
            if (!Float.isFinite(POWER[i]) || !Float.isFinite(RATE[i])
                    || !Float.isFinite(AREA[i]) || !Float.isFinite(SPEEDVAL[i])) return false;
        }
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            if (perSchool[s] != 10) return false;
            if (perRarity[s][0] < 2 || perRarity[s][1] < 2 || perRarity[s][3] != 1) return false;
        }
        return true;
    }

    /** 某流派按层数加权的总增伤，用于平衡自检。 */
    public static float schoolPowerBudget(int school) {
        float total = 0;
        for (int i = 0; i < COUNT; i++)
            if (SCHOOL[i] == school) total += POWER[i] * CAP[i];
        return total;
    }

    public static void main(String[] args) {
        if (!valid()) {
            System.out.println("Relics.valid() FAILED");
            System.exit(1);
        }
        System.out.println("Relics valid: " + COUNT + " items");
        for (int s = 0; s < Content.SCHOOL_COUNT; s++)
            System.out.println("  " + Content.SCHOOL_NAMES[s] + " budget=" + schoolPowerBudget(s));
    }
}
