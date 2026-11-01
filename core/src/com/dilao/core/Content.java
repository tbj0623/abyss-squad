package com.dilao.core;

/**
 * 原创内容表：职业 / 流派 / 生态 / 房间 / 模式 / 稀有度 / 加载提示。
 *
 * <p>契约见 docs/design-v1.md §3 §4 §5 §6 §13。LOCKED 字段名与顺序不得更改，只允许追加。
 * 本类为纯数据，不引用任何 Android API；唯一的逻辑是 {@link #valid()} 自检。
 *
 * <p>遗物表见 {@link Relics}，事件表见 {@link Events}，数值预算见 {@link Balance}。
 */
public final class Content {
    private Content() {}

    // ══════════════════════════════════════════════════════════════
    //  规模常量（LOCKED）
    // ══════════════════════════════════════════════════════════════

    public static final int ROLE_COUNT = 12, SCHOOL_COUNT = 6, BIOME_COUNT = 6;

    // ══════════════════════════════════════════════════════════════
    //  §13 标志位（LOCKED）：Relics.FLAGS / Evolutions.FLAGS 共用
    // ══════════════════════════════════════════════════════════════

    public static final int
            F_MULTISHOT = 1 << 0,  F_PIERCE    = 1 << 1,  F_EXPLODE   = 1 << 2,  F_HOMING    = 1 << 3,
            F_CHAIN     = 1 << 4,  F_ORBIT     = 1 << 5,  F_CONE      = 1 << 6,  F_SPLIT     = 1 << 7,
            F_BURN      = 1 << 8,  F_FREEZE    = 1 << 9,  F_LIFESTEAL = 1 << 10, F_KNOCKBACK = 1 << 11,
            F_RICOCHET  = 1 << 12, F_BEAM      = 1 << 13, F_MINE      = 1 << 14, F_SUMMON    = 1 << 15,
            F_SHIELD    = 1 << 16, F_TELEPORT  = 1 << 17, F_SLOWFIELD = 1 << 18, F_EXECUTE   = 1 << 19,
            F_VOLT      = 1 << 20, F_VOID      = 1 << 21, F_TAUNT     = 1 << 22, F_REGEN     = 1 << 23,
            F_THORNS    = 1 << 24, F_CRIT      = 1 << 25, F_SPLITSHOT = 1 << 26, F_CHARGE    = 1 << 27,
            F_AURA      = 1 << 28, F_STUN      = 1 << 29;

    // ══════════════════════════════════════════════════════════════
    //  12 职业（§4，LOCKED）
    // ══════════════════════════════════════════════════════════════

    public static final String[] ROLES = {
            "游侠", "守卫", "星医", "术士", "炼金", "影舞",
            "机铠", "圣歌", "霜灵", "血誓", "雷铳", "虚空行者"
    };

    public static final String[] ROLE_TITLE = {
            "疾风之眼", "铁壁", "微光之手", "霜咒者", "坩埚之主", "无影",
            "齿轮匠", "星辉牧师", "寒寂", "赤契", "霹雳手", "裂隙"
    };

    /** 列表用短描述，≤14 字。 */
    public static final String[] ROLE_DESC = {
            "高速连射 / 扇面齐射", "散射重炮 / 举盾震场", "治疗救援 / 拉起重伤", "穿透冰弹 / 大范围冻结",
            "爆破弹道 / 熔核清场", "暴击位移 / 残影穿行", "双联火力 / 过载炮台", "全队鼓舞 / 生命共鸣",
            "冰棱齐射 / 永冬控场", "血刃近战 / 残血爆发", "穿透电击 / 落雷连锁", "裂隙闪现 / 虚空处决"
    };

    /** 详情用长提示，40~70 字：写清这个职业怎么打，不是套模板。 */
    public static final String[] ROLE_TIP = {
            "攻速最快、身板最轻。别站着对射：边走边打，用闪避穿过弹幕，七向齐射一开，整条走廊都是她的弹道。",
            "三层散射让贴脸的敌人一次吃满。护盾既免伤又把围攻震开，所以她要往前走，而不是退到墙角等弹幕过去。",
            "唯一的持续治疗者。星愈能把倒地的队友从血泊里拉起来，代价是她必须站得很近，近到能听见自己的心跳。",
            "法球天然穿透，站在怪堆一侧就能打出成排伤害。霜环先冻住场，再一轮贯穿收割，节奏慢但没有一秒是浪费的。",
            "普攻自带爆炸，敌人越密越赚。炼成弹会留下持续灼烧的火，他打完就该走，把整片区域留给还在烧的东西。",
            "移速最高、暴击最高、血量最低。贴脸输出要立刻闪走，残影舞散开的十道影子，是敌人唯一看得见的东西。",
            "双联发射加一座自动炮台，火力点自己会站住脚。过载期间攻速暴涨，他要做的是活着让齿轮一轮轮转下去。",
            "光环让全队回血加速、状态拉齐。单人时靠高拾取范围滚雪球，她在场，队伍才敢站在明处而不是缩进角落。",
            "普攻自带冰缓，敌人会一步步被拖进泥里。冰棱爆发能同时冻住一整片，她讨厌混乱，于是让所有东西变慢。",
            "攻击吸血，越打越稳；技能主动扣血换爆发。他靠受伤换取窗口，越是残血越该往前压，而不是退。",
            "穿透弹配合落雷，一次点名多个目标。落雷有短暂延迟，得预判走位——他也同样怕被成群的东西围住。",
            "技能是短距位移，用来穿墙脱身或贴脸开爆发。虚空侵蚀会削掉重型敌人的护甲，残血的目标不会再跑掉。"
    };

    public static final String[] SKILLS = {
            "齐射", "震荡盾", "星愈", "霜环", "炼成弹", "残影舞",
            "过载炮台", "共鸣", "冰棱爆发", "血刃", "落雷", "裂隙闪现"
    };

    /** 技能描述：与 Game.doSkill 的实际行为一一对应。 */
    public static final String[] SKILL_DESC = {
            "朝七个方向各射出三发弹丸，一轮铺满整片扇面。",
            "获得相当于生命上限三成五的护盾，震退身边敌人并短暂眩晕。",
            "为全队回复生命；范围内倒地的队友以四成五生命被拉起。",
            "冻结身周大范围敌人并重击一次，减速持续数秒。",
            "在身周引爆炼成弹，范围内敌人受创并被点燃。",
            "进入短暂无敌，重置冲刺，并向四周洒出一圈暴击残影。",
            "部署一座持续十秒的自动炮台，同时进入过载射速。",
            "为全队回血并提升伤害，顺带震开贴身的敌人。",
            "冻结并眩晕周围敌人，同时射出八向冰棱。",
            "燃烧自身一成二的生命，换一次高额范围爆发与增伤。",
            "在不同目标头顶降下六道落雷，延迟后同时命中。",
            "向前闪现一段距离，伤害沿途敌人并削减其护甲。"
    };

    public static final String[] ULTIMATES = {
            "千刃风暴", "不动堡垒", "群星回响", "绝对零度", "熔核临界", "千影乱舞",
            "零式协议", "圣咏终章", "永冬", "献祭狂欢", "万雷归宗", "深渊之门"
    };

    /** 终极描述：与 Game.doUltimate 的实际行为一一对应。 */
    public static final String[] ULTIMATE_DESC = {
            "三层刃环依次炸开，每层十八道，整个房间没有落点之外的地方。",
            "四秒无敌，重击并眩晕周身，同时为全队回复三成生命。",
            "全队回满生命、解除倒地，并获得三秒无敌。",
            "全场冻结并眩晕，随后承受一轮巨额冰霜伤害。",
            "十四处熔核在身周依次引爆，把房间烧成一片火海。",
            "三重环形刃幕连发，期间近乎无敌，冲刺冷却归零。",
            "四角同时竖起炮台，并把身周敌人一次轰散。",
            "全队回满生命、大幅增伤，并获得两秒半无敌。",
            "冻结并眩晕全场，造成本作最高的一次控制与爆发。",
            "把生命压到四成，换取十秒极端增伤窗口与范围血爆。",
            "二十四道雷柱以自身为圆心依次落下，无处可躲。",
            "撕开大片虚空，重创全场并大幅削减敌人护甲。"
    };

    public static final int[] COLORS = {
            0xFF83EDC6, 0xFFEEBE75, 0xFF93C4FF, 0xFFC2A7FF, 0xFFFFA37B, 0xFFFF91C8,
            0xFF83D6D9, 0xFFF3D58A, 0xFFA9DBFF, 0xFFFF7A7A, 0xFFFFE58F, 0xFFB79BFF
    };

    /** 每职业的主流派，下标对应 SCHOOL_NAMES。 */
    public static final int[] SCHOOL = {1, 3, 3, 2, 0, 1, 4, 3, 2, 0, 4, 5};

    // ══════════════════════════════════════════════════════════════
    //  6 流派（§3，LOCKED）
    // ══════════════════════════════════════════════════════════════

    public static final String[] SCHOOL_NAMES = {"余烬", "疾风", "霜星", "生命", "雷光", "虚空"};

    public static final String[] SCHOOL_EN = {"EMBER", "GALE", "FROST", "LIFE", "VOLT", "VOID"};

    public static final String[] SCHOOL_BONUS = {
            "攻击附带 3 秒灼烧", "伤害 +12% / 移速 +8%", "弹丸附带减速 35%",
            "承伤 -18% / 回复 +25%", "每 4 发触发连锁闪电", "无视 20% 护盾，处决 <12% 血"
    };

    public static final int[] SCHOOL_COLOR = {
            0xFFFFA37B, 0xFF83EDC6, 0xFF93C4FF, 0xFFF3D58A, 0xFFFFE28A, 0xFFC2A7FF
    };

    // ══════════════════════════════════════════════════════════════
    //  6 生态（§5，LOCKED 名字与三色；ACCENT 为追加的高光色）
    // ══════════════════════════════════════════════════════════════

    public static final String[] BIOME_NAMES = {
            "苔光遗迹", "赤铜熔炉", "月影书库", "霜蚀回廊", "熔火深渊", "虚空王座"
    };

    /** 英文副标题。 */
    public static final String[] BIOME_SUB = {
            "MOSSGLOW RUINS", "COPPER FORGE", "MOONSHADE LIBRARY",
            "FROSTBITE GALLERY", "MOLTEN ABYSS", "VOID THRONE"
    };

    /** 长提示：先给氛围，再给危险感。 */
    public static final String[] BIOME_HINT = {
            "湿润的石缝里长着会呼吸的苔，孢子落进伤口。脚下的积水会突然变得很深——这里没有安静的一秒。",
            "旧熔炉还在低鸣，铁水沿沟槽缓慢流动。每一次呼吸都有焦味，踩错一步就是张开嘴的熔岩喷口。",
            "书库没有风，只有翻页声和越来越近的回响。书架会挪动，字迹会追人，站在原地就会被读透。",
            "冰晶从穹顶倒垂，冻尸保持着逃跑的姿势。空气会割伤裸露的皮肤，深处的寒意让动作一节节变慢。",
            "焦黑的岩壁渗出火光，地面裂成会呼吸的缝隙。这里的火从不熄灭，只会换一个方向烧过来。",
            "紫黑的裂隙在墙上呼吸，星光被吞进去就不再回来。虚空的注视会穿过护甲，一直落到骨头上。"
    };

    public static final int[] BIOME_BG = {
            0xFF172B2D, 0xFF30251F, 0xFF20243A, 0xFF1A2733, 0xFF2B1A18, 0xFF1B1730
    };

    public static final int[] BIOME_FLOOR = {
            0xFF1A3435, 0xFF3A2B25, 0xFF282A43, 0xFF213244, 0xFF3A211D, 0xFF241E3E
    };

    public static final int[] BIOME_EDGE = {
            0xFF4E7468, 0xFF82654C, 0xFF5F6290, 0xFF6E9BB8, 0xFF9A5A3C, 0xFF7A64B0
    };

    /** 追加字段：生态强调色，用于高光、孢子、火舌、冰晶与裂隙。 */
    public static final int[] BIOME_ACCENT = {
            0xFF9BE8C0, 0xFFFFA37B, 0xFF93C4FF, 0xFFA9DBFF, 0xFFFFB067, 0xFFC2A7FF
    };

    // ══════════════════════════════════════════════════════════════
    //  10 种房间（LOCKED 顺序）
    // ══════════════════════════════════════════════════════════════

    public static final String[] ROOM_TYPES = {
            "战斗房", "精英房", "事件房", "商店", "休整营地",
            "宝藏房", "首领房", "挑战房", "铁匠铺", "神龛"
    };

    public static final String[] ROOM_HINTS = {
            "守卫会分批涌入。别只等它们排队靠近，先打断后排的射手，剩下的才轮到你清理。",
            "精英带词缀入场，血更厚，手段更脏。先拆掉治疗与护盾，被包夹一次就会少半条血。",
            "有人在这里等你做决定。两个选项都不便宜，先看清代价，再想清楚构筑到底缺什么。",
            "货物明码标价，也能花星尘重新摆放。钱要花在会变强的地方，而不是补一个已经溢出的位置。",
            "营火只回应一次：回血、锻造、或者去更深处摸点东西。挑那个能让下一章活下来的。",
            "宝库有守门人，打完才能拿。守卫的密度比普通房高一档，别指望靠磨过去。",
            "首领有完整的招式表。先看预警再谈输出，躲不开的伤害才是真伤害。",
            "房间会附加一条环境规则：守圈、生存或限时狩猎。目标不同，站位也得跟着换。",
            "铁匠能把武器推向下一条进化线。三条线各有代价，先想清楚你要数量还是穿透。",
            "神龛只回应一次。付星尘换确定的强化，或者免费拿走供品——但深渊会记住你拿了什么。"
    };

    public static final String[] ROOM_SUBTITLE = {
            "GUARDED HALL", "ELITE VIGIL", "STRANGE ENCOUNTER", "TRADE POST", "REST & FORGE",
            "SEALED VAULT", "BOSS CHAMBER", "TRIAL GROUND", "WEAPON ALTAR", "SILENT SHRINE"
    };

    // ══════════════════════════════════════════════════════════════
    //  4 种模式（LOCKED 名字与顺序）
    // ══════════════════════════════════════════════════════════════

    public static final String[] MODE_NAMES = {"远征", "无尽裂隙", "首领连战", "每日星盘"};

    public static final String[] MODE_DESC = {
            "十五个节点、五个章节，每三站一位首领。带着一套构筑，从遗迹一路走到王座。",
            "没有终点。敌人随层数变厚，只有星尘与遗物在累积，看你的构筑能撑到第几层。",
            "连续面对六位首领，中间只给一次短暂修整。没有小怪可刷，只有招式表。",
            "当天固定种子、九个节点。同样的开局，比的是谁把构筑读得更透。"
    };

    public static final String[] MODE_EN = {"EXPEDITION", "ENDLESS RIFT", "BOSS RUSH", "DAILY ORBIT"};

    // ══════════════════════════════════════════════════════════════
    //  4 种稀有度 / 5 档难度契约（LOCKED）
    // ══════════════════════════════════════════════════════════════

    public static final String[] RARITY_NAMES = {"普通", "稀有", "史诗", "传说"};

    public static final int[] RARITY_COLOR = {
            0xFF9AA8AC, 0xFF93C4FF, 0xFFC2A7FF, 0xFFEAC487
    };

    public static final String[] DIFFICULTY_NAMES = {"漫游", "远征", "试炼", "深渊", "炼狱"};

    // ══════════════════════════════════════════════════════════════
    //  加载界面提示：每条 20~50 字，只讲构筑、操作与机制
    // ══════════════════════════════════════════════════════════════

    public static final String[] LOADING_TIPS = {
            "同流派持有三件不同遗物即触发共鸣，同名遗物叠层不算数。",
            "冲刺自带无敌帧，冷却三秒出头，贴脸躲弹幕比硬吃划算。",
            "精英房给两轮遗物选择，普通房只给一轮，路线值得为它绕。",
            "冰属性击中带灼烧的目标会爆出热冲击，冷却 0.8 秒，别指望无限触发。",
            "护盾会先吸收伤害再自行回充，生命流靠它撑过首领的第二阶段。",
            "遗物有叠层上限，满层后再拿到会折成星尘，不会完全浪费。",
            "暴击伤害是两倍多一点，暴击率堆到五成以上收益开始递减。",
            "击杀回血有恢复额度，短时间内连续击杀才会明显回血。",
            "自动瞄准优先最近的可视目标，按手动方向键可以强行改朝向。",
            "武器进化会替换当前形态而不是叠加，三条线分别是数量、穿透与追踪。",
            "挑战房的目标分守圈、生存、狩猎三种，每一种都要换站位。",
            "星尘除了买东西，还能在神龛换确定收益，别把余钱全花在补药上。",
            "拾取范围决定经验宝石的吸附速度，跑图慢了就是这块数值不够。",
            "首领半血会换招式表并召唤护卫，先清场再谈输出。",
            "移速与范围都是独立乘区，堆到一定程度后收益会明显变小。",
            "多弹遗物会摊薄单发伤害，先算总输出再决定要不要拿。",
            "召唤物与炮台吃你的伤害乘区，不吃攻速以外的额外加成。",
            "诅咒类遗物会提高承伤，它换来的伤害通常只在顺风局划算。",
            "事件的两个选项大多都是亏的，挑亏得最少、最贴构筑的那个。",
            "营火的锻造常被低估，伤害与生命一起给，越早拿越值。",
            "结算按走到第几层折算星尘与印记，跑得深比跑得快重要。",
            "第一组共鸣之外还能再凑第二组，构筑宽度本身就是强度。",
            "难度契约同时影响敌人血量、伤害与追击频率，不是单纯加血。",
            "敌人护甲按固定值扣减，面对高护甲优先换穿透而不是堆面板。",
            "地图可以预读下一站，提前决定这一趟是补经济还是补生存。",
            "连击五杀起结算额外星尘，断连四秒就会清零。",
            "遗物栏位有限，拿到方向不对的遗物，不如折成星尘去买对的。",
            "首领与精英不吃处决，残血斩杀只对普通敌人生效。"
    };

    // ══════════════════════════════════════════════════════════════
    //  跨流派共鸣（追加于 v1.1，纯追加，不影响既有下标）
    // ══════════════════════════════════════════════════════════════

    /**
     * 跨流派组合：两个流派各持有 2 件不同遗物即激活。
     * <p>与「同流派 3 件共鸣」并行存在，目的是让构筑在补齐主流派之后仍有发现空间。
     * 下标即 {@code Game.Player.crossMask} 的位序。
     */
    public static final int CROSS_COUNT = 6;

    /** 组合名（2 字，用于 HUD 与提示）。 */
    public static final String[] CROSS_NAMES = {
            "焚寂", "霜雷", "风火", "生霜", "虚步", "雷愈"
    };

    /** 组合的两个流派：CROSS_A 与 CROSS_B 必须是不同的流派。 */
    public static final int[] CROSS_A = {0, 2, 1, 3, 5, 3};
    public static final int[] CROSS_B = {5, 4, 0, 2, 1, 4};

    /** 组合生效时的机制说明（写清触发条件与效果，供图鉴/HUD 直接使用）。 */
    public static final String[] CROSS_DESC = {
            "余烬 + 虚空：灼烧中的敌人多受 25% 伤害，死亡时炸开",
            "霜星 + 雷光：命中减速目标有 22% 概率触发连锁闪电",
            "疾风 + 余烬：移动加成的 35% 转化为伤害",
            "生命 + 霜星：承伤 -8%，受击时冻结近身攻击者",
            "虚空 + 疾风：闪避冷却 -25%，闪避后 1.6 秒伤害 +20%",
            "生命 + 雷光：每次连锁闪电为你回复 1.5 点生命"
    };

    // ══════════════════════════════════════════════════════════════
    //  腐化阶梯（追加于 v1.1）：中段加压，5 站之后逐级抬升
    // ══════════════════════════════════════════════════════════════

    /** 腐化等级 0..4 的名字，用于 HUD 与星图提示。 */
    public static final String[] CORRUPTION_NAMES = {
            "稳定", "低语", "侵蚀", "沸腾", "崩坏"
    };

    /** 每级腐化的副作用说明。 */
    public static final String[] CORRUPTION_HINTS = {
            "深渊尚未注意到你。",
            "遭遇开始叠加第二条异变，敌人出手略快。",
            "第二条异变固定生效，精英开始成对出现。",
            "异变叠加且敌人血量上浮，房间不再有喘息。",
            "深渊全面沸腾：双重异变、精英成群、弹幕更密。"
    };

    // ══════════════════════════════════════════════════════════════
    //  自检
    // ══════════════════════════════════════════════════════════════

    /**
     * 校验内容表长度与取值范围是否自洽。
     *
     * @return 全部通过返回 true；不通过时在 stderr 输出原因并返回 false
     */
    public static boolean valid() {
        boolean ok = true;
        ok &= len("ROLES", ROLES, ROLE_COUNT) && len("ROLE_TITLE", ROLE_TITLE, ROLE_COUNT)
                && len("ROLE_DESC", ROLE_DESC, ROLE_COUNT) && len("ROLE_TIP", ROLE_TIP, ROLE_COUNT)
                && len("SKILLS", SKILLS, ROLE_COUNT) && len("SKILL_DESC", SKILL_DESC, ROLE_COUNT)
                && len("ULTIMATES", ULTIMATES, ROLE_COUNT) && len("ULTIMATE_DESC", ULTIMATE_DESC, ROLE_COUNT)
                && len("COLORS", COLORS, ROLE_COUNT) && len("SCHOOL", SCHOOL, ROLE_COUNT);
        ok &= len("SCHOOL_NAMES", SCHOOL_NAMES, SCHOOL_COUNT) && len("SCHOOL_EN", SCHOOL_EN, SCHOOL_COUNT)
                && len("SCHOOL_BONUS", SCHOOL_BONUS, SCHOOL_COUNT) && len("SCHOOL_COLOR", SCHOOL_COLOR, SCHOOL_COUNT);
        ok &= len("BIOME_NAMES", BIOME_NAMES, BIOME_COUNT) && len("BIOME_SUB", BIOME_SUB, BIOME_COUNT)
                && len("BIOME_HINT", BIOME_HINT, BIOME_COUNT) && len("BIOME_BG", BIOME_BG, BIOME_COUNT)
                && len("BIOME_FLOOR", BIOME_FLOOR, BIOME_COUNT) && len("BIOME_EDGE", BIOME_EDGE, BIOME_COUNT)
                && len("BIOME_ACCENT", BIOME_ACCENT, BIOME_COUNT);
        ok &= len("ROOM_TYPES", ROOM_TYPES, 10) && len("ROOM_HINTS", ROOM_HINTS, 10)
                && len("ROOM_SUBTITLE", ROOM_SUBTITLE, 10);
        ok &= len("MODE_NAMES", MODE_NAMES, 4) && len("MODE_DESC", MODE_DESC, 4) && len("MODE_EN", MODE_EN, 4);
        ok &= len("RARITY_NAMES", RARITY_NAMES, 4) && len("RARITY_COLOR", RARITY_COLOR, 4);
        ok &= len("DIFFICULTY_NAMES", DIFFICULTY_NAMES, 5);
        ok &= len("LOADING_TIPS", LOADING_TIPS, LOADING_TIPS.length);
        if (LOADING_TIPS.length < 24) {
            System.err.println("Content.LOADING_TIPS 至少需要 24 条，当前 " + LOADING_TIPS.length);
            ok = false;
        }
        for (int i = 0; i < SCHOOL.length; i++)
            if (SCHOOL[i] < 0 || SCHOOL[i] >= SCHOOL_COUNT) {
                System.err.println("Content.SCHOOL[" + i + "] 越界： " + SCHOOL[i]);
                ok = false;
            }
        for (int i = 0; i < ROLES.length; i++) {
            if (ROLE_DESC[i].length() > 14) {
                System.err.println("Content.ROLE_DESC[" + i + "] 超过 14 字： " + ROLE_DESC[i].length());
                ok = false;
            }
            int tip = ROLE_TIP[i].length();
            if (tip < 40 || tip > 70) {
                System.err.println("Content.ROLE_TIP[" + i + "] 长度 " + tip + " 不在 40~70");
                ok = false;
            }
        }
        for (int i = 0; i < LOADING_TIPS.length; i++) {
            int n = LOADING_TIPS[i].length();
            if (n < 20 || n > 50) {
                System.err.println("Content.LOADING_TIPS[" + i + "] 长度 " + n + " 不在 20~50");
                ok = false;
            }
        }
        for (int i = 0; i < BIOME_HINT.length; i++) {
            int n = BIOME_HINT[i].length();
            if (n < 30) {
                System.err.println("Content.BIOME_HINT[" + i + "] 过短： " + n);
                ok = false;
            }
        }
        return ok;
    }

    private static boolean len(String name, Object[] arr, int expected) {
        if (arr != null && arr.length == expected) return true;
        System.err.println("Content." + name + " 长度错误： " + (arr == null ? "null" : arr.length) + " != " + expected);
        return false;
    }

    private static boolean len(String name, int[] arr, int expected) {
        if (arr != null && arr.length == expected) return true;
        System.err.println("Content." + name + " 长度错误： " + (arr == null ? "null" : arr.length) + " != " + expected);
        return false;
    }
}
