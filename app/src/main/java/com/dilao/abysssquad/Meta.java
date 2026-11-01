package com.dilao.abysssquad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dilao.core.Balance;
import com.dilao.core.Bosses;
import com.dilao.core.Content;
import com.dilao.core.Enemies;
import com.dilao.core.Game;
import com.dilao.core.Relics;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 局外进度（契约 design-v1 §14）：星尘树 32 节点、深渊印记、契约阶梯、图鉴、24 成就。
 *
 * <p>存储：{@code SharedPreferences("abyss-meta")} 里的一个 JSON 字符串，带 {@code version} 字段。
 * 反序列化完全防御：缺字段、类型错、数组长度不符都退回默认值，绝不抛到调用方。
 *
 * <p>所有落盘都在单线程 {@link ExecutorService} 上完成，主线程只负责把对象序列化成字符串；
 * 需要同步写盘的场合（App 切后台）用 {@link #saveNow()}。
 */
public final class Meta {

    // ══════════════════════════════════════════════════════════════
    //  常量
    // ══════════════════════════════════════════════════════════════

    public static final int NODES = 32;
    public static final int GROUPS = 6;
    static final int ROLES = 12;
    static final int RELICS = 60;
    static final int ENEMIES = 20;
    static final int BOSSES = 8;
    static final int ACHIEVEMENTS = Achievements.COUNT;
    static final int VERSION = 1;

    static final String PREFS = "abyss-meta";
    static final String KEY = "meta";
    /** 旧版 GameView 直接写过的记录区，启动时单向迁移一次。 */
    static final String LEGACY_PREFS = "abyss-records";

    /** 节点分组（LOCKED）：0 生存 1 输出 2 经济 3 流派专精 4 解锁 5 特殊。 */
    public static final String[] GROUP_NAMES = {"生存", "输出", "经济", "流派专精", "解锁", "特殊"};

    // ══════════════════════════════════════════════════════════════
    //  32 节点表（index 与契约 §14 顺序一致）
    // ══════════════════════════════════════════════════════════════

    public static final String[] NODE_NAME = {
            /* 0  */ "坚韧体魄", "铁壁", "回复", "闪避娴熟", "迅捷",
            /* 5  */ "锋锐", "精准", "连射", "范围",
            /* 9  */ "贪婪", "学者", "磁力",
            /* 12 */ "初始星尘", "重掷",
            /* 14 */ "余烬专精", "疾风专精", "霜星专精", "生命专精", "雷光专精", "虚空专精",
            /* 20 */ "遗物槽位",
            /* 21 */ "商店折扣", "事件收益", "首领奖励",
            /* 24 */ "复活", "开局遗物", "契约精通", "成就奖励",
            /* 28 */ "遗物直觉", "敌情档案", "首领卷宗", "深渊馈赠"
    };

    /** 0 生存 / 1 输出 / 2 经济 / 3 流派专精 / 4 解锁 / 5 特殊。 */
    public static final int[] NODE_GROUP = {
            0, 0, 0, 0, 0,
            1, 1, 1, 1,
            2, 2, 2,
            5, 5,
            3, 3, 3, 3, 3, 3,
            4,
            2, 2, 2,
            5, 5, 4, 2,
            4, 4, 4, 5
    };

    /** 每级效果说明（UI 直接显示）。 */
    public static final String[] NODE_DESC = {
            "最大生命 +6",
            "承受伤害 -3%",
            "生命回复 +8%",
            "闪避冷却 -5%",
            "移动速度 +3%",
            "伤害 +5%",
            "暴击率 +2%",
            "攻击速度 +3%",
            "攻击范围 +4%",
            "星尘掉落 +8%",
            "经验获取 +6%",
            "拾取范围 +10",
            "开局星尘 +15",
            "开局重掷 +1",
            "余烬遗物效果 +5%",
            "疾风遗物效果 +5%",
            "霜星遗物效果 +5%",
            "生命遗物效果 +5%",
            "雷光遗物效果 +5%",
            "虚空遗物效果 +5%",
            "遗物携带上限 +1",
            "商店价格 -3%",
            "事件收益 +6%",
            "首领星尘 +10%",
            "免费复活 +1",
            "开局携带 1 件随机遗物",
            "可选契约上限 +1",
            "深渊印记获取 +5%",
            "图鉴揭示遗物 +4",
            "图鉴揭示敌种 +2",
            "图鉴揭示首领 +1",
            "局末结算星尘 +5%"
    };

    /** 效果键：Session / GameView 可据此做数据驱动的数值注入。 */
    public static final String[] NODE_KEY = {
            "hp", "armor", "regen", "dash", "speed",
            "damage", "crit", "atkspeed", "range",
            "coin", "xp", "magnet",
            "coins", "rerolls",
            "school0", "school1", "school2", "school3", "school4", "school5",
            "slots",
            "shop", "event", "boss",
            "revive", "startrelic", "pact", "mark",
            "codex_relic", "codex_enemy", "codex_boss", "dust"
    };

    /** 每节点等级上限（默认 5；特殊节点更短）。 */
    public static final int[] NODE_MAX = {
            5, 5, 5, 5, 5,
            5, 5, 5, 5,
            5, 5, 5,
            5, 5,
            5, 5, 5, 5, 5, 5,
            5,
            5, 5, 5,
            2, 1, 2, 5,
            5, 5, 8, 5
    };

    static {
        if (NODE_NAME.length != NODES || NODE_GROUP.length != NODES || NODE_DESC.length != NODES
                || NODE_KEY.length != NODES || NODE_MAX.length != NODES) {
            throw new IllegalStateException("Meta node tables must have " + NODES + " entries");
        }
        for (int i = 0; i < NODES; i++) {
            if (NODE_GROUP[i] < 0 || NODE_GROUP[i] >= GROUPS) NODE_GROUP[i] = 5;
            if (NODE_MAX[i] < 1) NODE_MAX[i] = 1;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  天赋牌 Doctrine（v2.0 支柱 2）— 18 张，6 组 × 3
    // ══════════════════════════════════════════════════════════════

    public static final int DOCTRINE_COUNT = 18;
    /** 6 组，与 6 流派一一对应（余烬/疾风/霜星/生命/雷光/虚空）。 */
    public static final int DOCTRINE_GROUPS = 6;
    /** 未装备任何天赋牌。 */
    public static final int DOCTRINE_NONE = -1;

    /** 组名 = 流派名，直接复用 Content 的顺序。 */
    public static final String[] DOCTRINE_GROUP_NAMES = {"余烬", "疾风", "霜星", "生命", "雷光", "虚空"};

    public static final String[] DOCTRINE_NAME = {
            /* 0 疾风 */ "疾风契约", "双重进化", "疾影步",
            /* 1 余烬 */ "余烬开局", "献祭契约", "焚身",
            /* 2 霜星 */ "霜封", "寒冰壁垒", "极寒迟滞",
            /* 3 生命 */ "生命开局", "荆棘誓约", "血脉共鸣",
            /* 4 雷光 */ "雷光开局", "过载运转", "雷霆传导",
            /* 5 虚空 */ "星尘债务", "玻璃大炮", "孤注一掷"
    };

    /** 一行说清"改了什么规则 + 代价是什么"。 */
    public static final String[] DOCTRINE_DESC = {
            /* 0 疾风 */
            "开局固定获得 1 件疾风系稀有遗物，但开局星尘清零。",
            "每局可进化两条武器线，但进化等级门槛 +3。",
            "闪避改为消耗 6 星尘（无冷却），闪避后 1.5 秒攻速 +60%。",
            /* 1 余烬 */
            "开局固定获得 1 件余烬系稀有遗物，但本局不再出现生命系遗物。",
            "最大生命 -25%，但每次升级额外获得一次三选一。",
            "每次受伤在自身周围引爆火环（半径 130），但承受伤害 +20%。",
            /* 2 霜星 */
            "所有弹丸附带 1.5 秒减速，但弹丸伤害 -20%。",
            "每层开始获得 30% 最大生命的护盾，但护盾不再自动恢复。",
            "敌人移速 -25%，但房间限时 -20%。",
            /* 3 生命 */
            "开局固定获得 1 件生命系稀有遗物，但每层结束不再自动回复。",
            "反弹 35% 承受伤害给 90 范围内的敌人，但所有回复效果 -60%。",
            "每击杀 25 个敌人回复 18% 最大生命，但最大生命 -15%。",
            /* 4 雷光 */
            "开局固定获得 1 件雷光系稀有遗物，但开局重掷次数归零。",
            "攻速 +45%，但每 6 秒强制过热停顿 0.7 秒。",
            "击杀时向最近敌人释放连锁闪电（60% 伤害），但暴击率归零。",
            /* 5 虚空 */
            "开局 -50 星尘，但商店价格 -25%。",
            "承受伤害 +50%，但伤害 +60%。",
            "只能携带 2 件遗物，但每件遗物效果 ×1.6。"
    };

    /** 0..5，对应 6 流派。 */
    public static final int[] DOCTRINE_GROUP = {
            1, 1, 1,
            0, 0, 0,
            2, 2, 2,
            3, 3, 3,
            4, 4, 4,
            5, 5, 5
    };

    /** 存档键（唯一英文键，用于存档与成就；一经发布不得改名）。 */
    public static final String[] DOCTRINE_KEY = {
            "gale_pact", "dual_evolve", "shadow_step",
            "ember_start", "sacrifice_pact", "burning_body",
            "frost_seal", "ice_bastion", "permafrost",
            "life_start", "thorn_oath", "blood_pact",
            "volt_start", "overdrive", "thunder_chain",
            "dust_debt", "glass_cannon", "all_in"
    };

    /** 解锁条件的可读说明（未解锁时直接显示在卡面）。 */
    public static final String[] DOCTRINE_UNLOCK_DESC = {
            "默认解锁",
            "累计通关 3 次远征",
            "以「试炼」或更高契约通关一次",
            "默认解锁",
            "解锁成就「初次远征」",
            "以「深渊」或更高契约通关一次",
            "默认解锁",
            "累计通关 6 次远征",
            "以「深渊」或更高契约通关一次",
            "默认解锁",
            "解锁成就「斩首者」",
            "累计获得 200 深渊印记",
            "默认解锁",
            "累计通关 10 次远征",
            "击败 3 位不同的首领",
            "默认解锁",
            "以「炼狱」契约通关一次",
            "解锁成就「八王之证」"
    };

    // ── 解锁条件：类型 + 阈值 ────────────────────────────────────
    public static final int REQ_NONE = 0, REQ_WINS = 1, REQ_CONTRACT = 2,
            REQ_ACHIEVEMENT = 3, REQ_TOTAL_MARKS = 4, REQ_BOSS_KINDS = 5;

    public static final int[] DOCTRINE_REQ_TYPE = {
            REQ_NONE, REQ_WINS, REQ_CONTRACT,
            REQ_NONE, REQ_ACHIEVEMENT, REQ_CONTRACT,
            REQ_NONE, REQ_WINS, REQ_CONTRACT,
            REQ_NONE, REQ_ACHIEVEMENT, REQ_TOTAL_MARKS,
            REQ_NONE, REQ_WINS, REQ_BOSS_KINDS,
            REQ_NONE, REQ_CONTRACT, REQ_ACHIEVEMENT
    };

    /** REQ_NONE=0；REQ_WINS=次数；REQ_CONTRACT=契约下标；REQ_ACHIEVEMENT=成就下标；其余=阈值。 */
    public static final int[] DOCTRINE_REQ_VALUE = {
            0, 3, 2,
            0, Achievements.A_FIRST_WIN, 3,
            0, 6, 3,
            0, Achievements.A_FIRST_BOSS, 200,
            0, 10, 3,
            0, 4, Achievements.A_ALL_BOSS
    };

    // ── 效果钩子：core 侧按 hook id 实现，Meta 只负责把参数递过去 ──
    /** 无效果（占位）。 */
    public static final int H_NONE = 0;
    /** 开局固定 1 件该流派稀有遗物；param=流派 0..5。 */
    public static final int H_START_RELIC = 1;
    /** 开局星尘清零。 */
    public static final int H_COINS_ZERO = 2;
    /** 该流派遗物本局不再出现；param=流派 0..5。 */
    public static final int H_SCHOOL_LOCK = 3;
    /** 每层结束不再自动回复。 */
    public static final int H_NO_FLOOR_HEAL = 4;
    /** 开局重掷次数归零。 */
    public static final int H_REROLL_ZERO = 5;
    /** 所有弹丸附带减速；param=秒。 */
    public static final int H_ALL_SHOTS_SLOW = 6;
    /** 弹丸伤害乘区增量；param=增量（-0.20 = -20%）。 */
    public static final int H_SHOT_DAMAGE_MUL = 7;
    /** 开局星尘增量；param=增量。 */
    public static final int H_START_COINS = 8;
    /** 商店价格乘区增量；param=增量。 */
    public static final int H_SHOP_PRICE_MUL = 9;
    /** 每局可进化线数；param=条数。 */
    public static final int H_MAX_EVOLUTIONS = 10;
    /** 进化等级门槛增量；param=增量。 */
    public static final int H_EVOLVE_LEVEL_PEN = 11;
    /** 闪避消耗星尘而非冷却；param=每次星尘。 */
    public static final int H_DASH_COSTS_COIN = 12;
    /** 闪避后攻速加成；param=增量，持续 1.5 秒。 */
    public static final int H_DASH_ATKSPEED = 13;
    /** 最大生命乘区；param=乘区（0.75 = -25%）。 */
    public static final int H_MAXHP_MUL = 14;
    /** 每次升级额外三选一次数；param=次数。 */
    public static final int H_LEVELUP_EXTRA_CHOICE = 15;
    /** 受伤时自身周围火环；param=半径。 */
    public static final int H_HURT_FIRE_RING = 16;
    /** 承受伤害乘区；param=乘区。 */
    public static final int H_TAKEN_DAMAGE_MUL = 17;
    /** 每层开始护盾；param=最大生命比例。 */
    public static final int H_FLOOR_SHIELD = 18;
    /** 护盾不再自动恢复。 */
    public static final int H_SHIELD_NO_REGEN = 19;
    /** 敌人移速乘区增量；param=增量。 */
    public static final int H_ENEMY_SPEED_MUL = 20;
    /** 房间限时乘区增量；param=增量。 */
    public static final int H_ROOM_TIME_MUL = 21;
    /** 反弹承受伤害；param=比例。 */
    public static final int H_REFLECT = 22;
    /** 回复效果乘区增量；param=增量。 */
    public static final int H_REGEN_MUL = 23;
    /** 每击杀 N 个敌人回复最大生命比例；param=击杀数，param2=回复比例。 */
    public static final int H_KILL_HEAL = 24;
    /** 伤害乘区；param=乘区。 */
    public static final int H_DAMAGE_MUL = 25;
    /** 过热：每隔 param 秒强制停顿 0.7 秒。 */
    public static final int H_OVERHEAT = 26;
    /** 攻速乘区增量；param=增量。 */
    public static final int H_ATKSPEED_MUL = 27;
    /** 击杀时连锁闪电；param=伤害比例。 */
    public static final int H_CHAIN_ON_KILL = 28;
    /** 暴击率归零。 */
    public static final int H_CRIT_ZERO = 29;
    /** 遗物携带上限；param=上限。 */
    public static final int H_RELIC_SLOTS = 30;
    /** 遗物效果乘区；param=乘区。 */
    public static final int H_RELIC_EFFECT_MUL = 31;

    /** 主钩子。 */
    public static final int[] DOCTRINE_HOOK = {
            H_START_RELIC, H_MAX_EVOLUTIONS, H_DASH_COSTS_COIN,
            H_START_RELIC, H_MAXHP_MUL, H_HURT_FIRE_RING,
            H_ALL_SHOTS_SLOW, H_FLOOR_SHIELD, H_ENEMY_SPEED_MUL,
            H_START_RELIC, H_REFLECT, H_KILL_HEAL,
            H_START_RELIC, H_OVERHEAT, H_CHAIN_ON_KILL,
            H_START_COINS, H_TAKEN_DAMAGE_MUL, H_RELIC_EFFECT_MUL
    };

    /** 主钩子参数。 */
    public static final float[] DOCTRINE_PARAM = {
            1f, 2f, 6f,
            0f, .75f, 130f,
            1.5f, .30f, -.25f,
            3f, .35f, 25f,
            4f, 6f, .60f,
            -50f, 1.5f, 1.6f
    };

    /** 主钩子的第二参数（只有 H_KILL_HEAL 用到：回复比例）。 */
    public static final float[] DOCTRINE_PARAM2 = {
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, .18f,
            0f, 0f, 0f,
            0f, 0f, 0f
    };

    /** 副钩子（代价部分）；H_NONE 表示没有。 */
    public static final int[] DOCTRINE_SIDE = {
            H_COINS_ZERO, H_EVOLVE_LEVEL_PEN, H_DASH_ATKSPEED,
            H_SCHOOL_LOCK, H_LEVELUP_EXTRA_CHOICE, H_TAKEN_DAMAGE_MUL,
            H_SHOT_DAMAGE_MUL, H_SHIELD_NO_REGEN, H_ROOM_TIME_MUL,
            H_NO_FLOOR_HEAL, H_REGEN_MUL, H_MAXHP_MUL,
            H_REROLL_ZERO, H_ATKSPEED_MUL, H_CRIT_ZERO,
            H_SHOP_PRICE_MUL, H_DAMAGE_MUL, H_RELIC_SLOTS
    };

    /** 副钩子参数。 */
    public static final float[] DOCTRINE_SIDE_PARAM = {
            0f, 3f, .60f,
            3f, 1f, 1.2f,
            -.20f, 0f, -.20f,
            0f, -.60f, .85f,
            0f, .45f, 0f,
            -.25f, 1.6f, 2f
    };

    static {
        if (DOCTRINE_NAME.length != DOCTRINE_COUNT || DOCTRINE_DESC.length != DOCTRINE_COUNT
                || DOCTRINE_GROUP.length != DOCTRINE_COUNT || DOCTRINE_KEY.length != DOCTRINE_COUNT
                || DOCTRINE_UNLOCK_DESC.length != DOCTRINE_COUNT
                || DOCTRINE_HOOK.length != DOCTRINE_COUNT || DOCTRINE_PARAM.length != DOCTRINE_COUNT
                || DOCTRINE_PARAM2.length != DOCTRINE_COUNT || DOCTRINE_SIDE.length != DOCTRINE_COUNT
                || DOCTRINE_SIDE_PARAM.length != DOCTRINE_COUNT
                || DOCTRINE_REQ_TYPE.length != DOCTRINE_COUNT
                || DOCTRINE_REQ_VALUE.length != DOCTRINE_COUNT) {
            throw new IllegalStateException("Doctrine tables must have " + DOCTRINE_COUNT + " entries");
        }
        for (int i = 0; i < DOCTRINE_COUNT; i++) {
            if (DOCTRINE_GROUP[i] < 0 || DOCTRINE_GROUP[i] >= DOCTRINE_GROUPS) DOCTRINE_GROUP[i] = 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  持久字段（契约 §14）
    // ══════════════════════════════════════════════════════════════

    /** 星尘：星图节点货币。 */
    int dust;
    /** 深渊印记：职业 / 契约解锁货币。 */
    int marks;

    int bestFloor, runs, wins, bestKills, totalKills, playSeconds;

    /** 初始解锁 0..3，其余用印记购买。 */
    boolean[] roleUnlocked = new boolean[ROLES];
    /** 0..4，已解锁的最高契约档位。 */
    int pactUnlocked;

    boolean[] relicSeen = new boolean[RELICS];
    boolean[] enemySeen = new boolean[ENEMIES];
    boolean[] bossSeen = new boolean[BOSSES];
    int[] bossKills = new int[BOSSES];

    /** 24 个成就的进度值（0 或目标值）。 */
    int[] achievements = new int[ACHIEVEMENTS];
    /** 32 个元进度节点的等级（0..NODE_MAX）。 */
    int[] nodeLevel = new int[NODES];

    // ── 追加字段（契约允许追加；仅供成就与统计，UI 可忽略）──────
    /** 真正拿到过的遗物（与"图鉴已揭示"区分，后者会被节点直接点亮）。 */
    boolean[] relicFound = new boolean[RELICS];
    boolean[] roleWon = new boolean[ROLES];
    int bestEndless, bestBossRush, bestSynergy, bestXp, totalDustGained;

    // ── v2.0 天赋牌 Doctrine ────────────────────────────────────
    /** 18 张天赋牌的解锁状态。默认解锁 6 张（每组 1 张），其余靠条件解锁。 */
    boolean[] DOCTRINE_UNLOCKED = new boolean[DOCTRINE_COUNT];
    /** 当前装备的天赋牌下标；{@link #DOCTRINE_NONE} 表示未装备。 */
    int doctrine = DOCTRINE_NONE;

    int version = VERSION;

    // ══════════════════════════════════════════════════════════════
    //  运行时
    // ══════════════════════════════════════════════════════════════

    private static final String TAG = "AbyssMeta";

    /**
     * 安全的 info 日志：{@code android.util.Log} 在 JVM 单元测试的桩实现里会抛
     * {@code RuntimeException("Stub!")}，而 {@link #resetAll()} 承诺"绝不抛"，所以这里兜一层。
     * 真机/模拟器上永远正常输出。
     */
    private static void logInfo(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }

    private final SharedPreferences prefs;
    private final SharedPreferences legacyPrefs;
    /**
     * {@link Onboarding} 自己的 prefs 句柄。
     * <p>重置存档要连「首次引导已跑过」一起清掉，否则重置后永远不再出现引导。
     * 这里只借 Meta 的重置入口写这一个键，不改 {@link Onboarding} 本身（它不归本类维护）。
     */
    private final SharedPreferences onboardingPrefs;
    private final ExecutorService io;
    private volatile String lastJson = "";
    /** 最近一次读/写失败的说明，供 UI 展示。 */
    volatile String error = "";

    private Meta(SharedPreferences prefs, SharedPreferences legacyPrefs,
                 SharedPreferences onboardingPrefs, ExecutorService io) {
        this.prefs = prefs;
        this.legacyPrefs = legacyPrefs;
        this.onboardingPrefs = onboardingPrefs;
        this.io = io;
        resetDefaults();
    }

    private void resetDefaults() {
        // 先补数组：构造器、restore() 的 catch、resetAll() 三条路径都必须不抛。
        // 反序列化不会把数组置 null，但这里是"绝不抛"的最后一道兜底，成本可以忽略。
        if (roleUnlocked == null || roleUnlocked.length != ROLES) roleUnlocked = new boolean[ROLES];
        if (roleWon == null || roleWon.length != ROLES) roleWon = new boolean[ROLES];
        if (relicSeen == null || relicSeen.length != RELICS) relicSeen = new boolean[RELICS];
        if (relicFound == null || relicFound.length != RELICS) relicFound = new boolean[RELICS];
        if (enemySeen == null || enemySeen.length != ENEMIES) enemySeen = new boolean[ENEMIES];
        if (bossSeen == null || bossSeen.length != BOSSES) bossSeen = new boolean[BOSSES];
        if (bossKills == null || bossKills.length != BOSSES) bossKills = new int[BOSSES];
        if (achievements == null || achievements.length != ACHIEVEMENTS) achievements = new int[ACHIEVEMENTS];
        if (nodeLevel == null || nodeLevel.length != NODES) nodeLevel = new int[NODES];
        if (DOCTRINE_UNLOCKED == null || DOCTRINE_UNLOCKED.length != DOCTRINE_COUNT) {
            DOCTRINE_UNLOCKED = new boolean[DOCTRINE_COUNT];
        }
        dust = 0;
        marks = 0;
        bestFloor = 0;
        runs = 0;
        wins = 0;
        bestKills = 0;
        totalKills = 0;
        playSeconds = 0;
        Arrays.fill(roleUnlocked, false);
        for (int i = 0; i < ROLES && i < 4; i++) roleUnlocked[i] = true;
        pactUnlocked = 0;
        Arrays.fill(relicSeen, false);
        Arrays.fill(relicFound, false);
        Arrays.fill(enemySeen, false);
        Arrays.fill(bossSeen, false);
        Arrays.fill(bossKills, 0);
        Arrays.fill(achievements, 0);
        Arrays.fill(nodeLevel, 0);
        Arrays.fill(roleWon, false);
        bestEndless = 0;
        bestBossRush = 0;
        bestSynergy = 0;
        bestXp = 0;
        totalDustGained = 0;
        totalMarksSpent = 0;
        highestContractCleared = -1;
        Arrays.fill(DOCTRINE_UNLOCKED, false);
        for (int i = 0; i < DOCTRINE_COUNT; i++) {
            if (DOCTRINE_REQ_TYPE[i] == REQ_NONE) DOCTRINE_UNLOCKED[i] = true;
        }
        doctrine = DOCTRINE_NONE;
        version = VERSION;
    }

    /** 读取元进度。任何异常都返回一份可用的默认进度，绝不返回 null。 */
    static Meta load(Context ctx) {
        ExecutorService io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "abyss-meta-io");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
        SharedPreferences prefs = null;
        SharedPreferences legacy = null;
        SharedPreferences onboarding = null;
        try {
            if (ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                legacy = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
                onboarding = ctx.getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE);
            }
        } catch (Throwable ignored) {}
        Meta meta = new Meta(prefs, legacy, onboarding, io);
        meta.restore();
        return meta;
    }

    // ══════════════════════════════════════════════════════════════
    //  重置存档（全局唯一不可撤销入口）
    // ══════════════════════════════════════════════════════════════

    /**
     * 把全部局外进度清空到「首次启动」状态，并立即落盘。
     *
     * <p>清空范围与 {@link #encode()} 的键一一对应：星尘/印记、记录与统计、
     * 职业解锁、契约档位、图鉴（遗物/敌种/首领含击杀数）、24 成就、32 节点等级、
     * 18 张天赋牌与当前装备，以及天赋牌解锁依据（累计印记/最高通关契约）。
     * 另外顺带清掉首次引导的进度键，否则重置后玩家再也见不到引导。
     *
     * <p><b>成就奖励如何被清掉</b>：奖励没有独立存储，{@link #achievementRewardActive(int)}
     * 每次都从 {@code achievements[]} 与 {@link Achievements#TARGET} 现场推导。
     * {@link #resetDefaults()} 里 {@code Arrays.fill(achievements, 0)} 之后，
     * 六个奖励（{@link #PERK_*}）必然同时失效 —— 不是"额外再清一次"，
     * 而是根本不存在可以被漏掉的那份数据。
     *
     * <p><b>幂等</b>：重复调用只会再写一次同样的默认值。
     * <b>绝不抛</b>：所有分支都包在 try/catch 里，失败只记录 {@link #error} 与一条 log。
     * 写盘走 {@link #save()}（主线程只做序列化），单线程池保证在它之前排队的
     * 旧进度写任务先落盘、再被本次默认值覆盖。
     */
    public void resetAll() {
        // 先在锁内取快照：日志要能证明"确实清掉了东西"，而 resetDefaults() 之后这些值就没了。
        int clearedAch, clearedRoles, clearedPacts, clearedNodes, clearedDoctrine;
        int oldDust, oldMarks, oldRuns, oldWins, oldBest, oldContract;
        try {
            synchronized (this) {
                oldDust = dust;
                oldMarks = marks;
                oldRuns = runs;
                oldWins = wins;
                oldBest = bestFloor;
                oldContract = highestContractCleared;
                clearedAch = achievementsStarted(achievements);
                // 前 4 个职业是默认解锁，不算"被清掉的进度"
                clearedRoles = Math.max(0, unlockedRoles() - 4);
                clearedPacts = Math.max(0, pactUnlocked - 2);
                clearedNodes = nodesLit();
                clearedDoctrine = doctrine;
                resetDefaults();
                // error 只在内存里，但重置后还留着上一次存档损坏的报错会误导玩家
                error = "";
                lastJson = "";
            }
        } catch (Throwable t) {
            // resetDefaults() 内部已做了数组兜底，走到这里基本只剩 OOM；仍旧不向上抛。
            error = "元进度重置失败：" + t.getClass().getSimpleName();
            logInfo("resetAll: 内存复位失败 error=" + error);
            return;
        }

        // 引导进度独立存在 "abyss-onboarding"，必须一起清 —— 但只能经过 Meta 自己的句柄，
        // 不改 Onboarding.java。写 NEVER(-1) 与它 neverStarted() 的判据一致。
        boolean onboardingCleared = false;
        try {
            if (onboardingPrefs != null) {
                onboardingPrefs.edit().putInt(Onboarding.KEY_STEP, Onboarding.NEVER).commit();
                onboardingCleared = true;
            }
        } catch (Throwable t) {
            logInfo("resetAll: 引导进度清除失败 " + t.getClass().getSimpleName());
        }

        // 旧版记录区（"abyss-records"）：正常情况下新 JSON 存在就不会再迁移，
        // 但若本次落盘失败，下次启动的 migrateLegacy() 会把 runs/wins/best 复活。
        // 重置语义要求这些值彻底消失，所以主动清一次。
        try {
            if (legacyPrefs != null) legacyPrefs.edit().clear().commit();
        } catch (Throwable ignored) {}

        try {
            save();
        } catch (Throwable t) {
            error = "元进度重置后保存失败：" + t.getClass().getSimpleName();
        }

        logInfo("resetAll: cleared dust=" + oldDust + " marks=" + oldMarks
                + " runs=" + oldRuns + " wins=" + oldWins + " bestFloor=" + oldBest
                + " highestContract=" + oldContract
                + " | achievements=" + clearedAch + " roles=" + clearedRoles
                + " pacts=" + clearedPacts + " nodes=" + clearedNodes
                + " doctrine=" + clearedDoctrine
                + " | onboarding=" + onboardingCleared
                + " | error=\"" + error + "\"");
    }

    /** 进度大于 0 的成就数（重置日志用；与 {@link #count(boolean[])} 同名的 int 版本）。 */
    private static int achievementsStarted(int[] a) {
        int n = 0;
        if (a != null) for (int v : a) if (v > 0) n++;
        return n;
    }

    // ══════════════════════════════════════════════════════════════
    //  反序列化
    // ══════════════════════════════════════════════════════════════

    private void restore() {
        String json = null;
        try {
            json = prefs == null ? null : prefs.getString(KEY, null);
        } catch (Throwable ignored) {}
        if (json == null || json.isEmpty()) {
            migrateLegacy();
            applyNodeReveals();
            refreshAchievements();
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            version = optInt(root, "version", VERSION, 0, 1_000_000);
            dust = optInt(root, "dust", 0, 0, 2_000_000_000);
            marks = optInt(root, "marks", 0, 0, 2_000_000_000);
            bestFloor = optInt(root, "bestFloor", 0, 0, 1_000_000);
            runs = optInt(root, "runs", 0, 0, 100_000_000);
            wins = optInt(root, "wins", 0, 0, 100_000_000);
            if (wins > runs) wins = runs;
            bestKills = optInt(root, "bestKills", 0, 0, 100_000_000);
            totalKills = optInt(root, "totalKills", 0, 0, 2_000_000_000);
            playSeconds = optInt(root, "playSeconds", 0, 0, 2_000_000_000);
            pactUnlocked = optInt(root, "pactUnlocked", 0, 0, 4);
            bestEndless = optInt(root, "bestEndless", 0, 0, 1_000_000);
            bestBossRush = optInt(root, "bestBossRush", 0, 0, 1_000_000);
            bestSynergy = optInt(root, "bestSynergy", 0, 0, 6);
            bestXp = optInt(root, "bestXp", 0, 0, 2_000_000_000);
            totalDustGained = optInt(root, "totalDustGained", 0, 0, 2_000_000_000);
            if (totalDustGained < dust) totalDustGained = dust;
            totalMarksSpent = optInt(root, "totalMarksSpent", 0, 0, 2_000_000_000);
            highestContractCleared = optInt(root, "highestContractCleared", -1, -1,
                    Math.max(0, contractCount() - 1));
            readBools(roleUnlocked, root.optString("roleUnlocked", ""));
            readBools(roleWon, root.optString("roleWon", ""));
            readBools(relicSeen, root.optString("relicSeen", ""));
            readBools(relicFound, root.optString("relicFound", ""));
            readBools(enemySeen, root.optString("enemySeen", ""));
            readBools(bossSeen, root.optString("bossSeen", ""));
            readInts(bossKills, root.optString("bossKills", ""), 0, 2_000_000_000);
            readInts(nodeLevel, root.optString("nodeLevel", ""), 0, 5);
            readInts(achievements, root.optString("achievements", ""), 0, 2_000_000_000);
            // v2.0 天赋牌：旧档没有这两个字段 → 保留默认（6 张默认解锁、未装备）
            if (root.has("doctrineUnlocked")) {
                Arrays.fill(DOCTRINE_UNLOCKED, false);
                readBools(DOCTRINE_UNLOCKED, root.optString("doctrineUnlocked", ""));
            }
            doctrine = optInt(root, "doctrine", DOCTRINE_NONE, DOCTRINE_NONE, DOCTRINE_COUNT - 1);
            // 老版本没有 relicFound：用已见图鉴兜底，避免成就进度看起来倒退。
            if (!root.has("relicFound")) {
                for (int i = 0; i < RELICS; i++) relicFound[i] = relicSeen[i];
            }
            normalize();
        } catch (Throwable t) {
            resetDefaults();
            error = "元进度数据损坏，已回退为默认进度（旧数据保留在系统中）";
        }
        applyNodeReveals();
        refreshAchievements();
    }

    /** 始终解锁 0..3；等级/成就钳制到各自上限。 */
    private void normalize() {
        for (int i = 0; i < ROLES && i < 4; i++) roleUnlocked[i] = true;
        for (int i = 0; i < NODES; i++) {
            if (nodeLevel[i] < 0) nodeLevel[i] = 0;
            if (nodeLevel[i] > NODE_MAX[i]) nodeLevel[i] = NODE_MAX[i];
        }
        for (int i = 0; i < ACHIEVEMENTS; i++) {
            if (achievements[i] < 0) achievements[i] = 0;
            if (achievements[i] > Achievements.TARGET[i]) achievements[i] = Achievements.TARGET[i];
        }
        for (int i = 0; i < BOSSES; i++) if (bossKills[i] < 0) bossKills[i] = 0;
        if (pactUnlocked < 0) pactUnlocked = 0;
        if (pactUnlocked > 4) pactUnlocked = 4;
        if (bestSynergy < 0) bestSynergy = 0;
        if (bestSynergy > 6) bestSynergy = 6;
        for (int i = 0; i < RELICS; i++) if (relicFound[i]) relicSeen[i] = true;
        normalizeDoctrine();
    }

    /**
     * 天赋牌一致性：默认牌永远解锁、装备的牌必须已解锁、未解锁的不能留作当前选择。
     * <p>旧存档读到 doctrine 字段缺失时是 -1（未装备），不会被误判。
     */
    private void normalizeDoctrine() {
        if (DOCTRINE_UNLOCKED == null || DOCTRINE_UNLOCKED.length < DOCTRINE_COUNT) {
            DOCTRINE_UNLOCKED = new boolean[DOCTRINE_COUNT];
        }
        for (int i = 0; i < DOCTRINE_COUNT; i++) {
            if (DOCTRINE_REQ_TYPE[i] == REQ_NONE) DOCTRINE_UNLOCKED[i] = true;
        }
        if (doctrine >= DOCTRINE_COUNT) doctrine = DOCTRINE_NONE;
        if (doctrine < DOCTRINE_NONE) doctrine = DOCTRINE_NONE;
        if (doctrine != DOCTRINE_NONE && !DOCTRINE_UNLOCKED[doctrine]) doctrine = DOCTRINE_NONE;
    }

    /** 旧版直接把 runs/wins/best 写进 SharedPreferences，这里做一次单向迁移。 */
    private void migrateLegacy() {
        try {
            if (legacyPrefs == null) return;
            if (!legacyPrefs.contains("runs") && !legacyPrefs.contains("wins")
                    && !legacyPrefs.contains("best")) return;
            runs = Math.max(0, legacyPrefs.getInt("runs", 0));
            wins = Math.max(0, Math.min(runs, legacyPrefs.getInt("wins", 0)));
            bestFloor = Math.max(0, legacyPrefs.getInt("best", 0));
            saveNow();
        } catch (Throwable ignored) {}
    }

    private JSONObject encode() {
        JSONObject root = new JSONObject();
        put(root, "version", VERSION);
        put(root, "dust", dust);
        put(root, "marks", marks);
        put(root, "bestFloor", bestFloor);
        put(root, "runs", runs);
        put(root, "wins", wins);
        put(root, "bestKills", bestKills);
        put(root, "totalKills", totalKills);
        put(root, "playSeconds", playSeconds);
        put(root, "pactUnlocked", pactUnlocked);
        put(root, "bestEndless", bestEndless);
        put(root, "bestBossRush", bestBossRush);
        put(root, "bestSynergy", bestSynergy);
        put(root, "bestXp", bestXp);
        put(root, "totalDustGained", totalDustGained);
        put(root, "totalMarksSpent", totalMarksSpent);
        put(root, "highestContractCleared", highestContractCleared);
        put(root, "roleUnlocked", packBools(roleUnlocked));
        put(root, "roleWon", packBools(roleWon));
        put(root, "relicSeen", packBools(relicSeen));
        put(root, "relicFound", packBools(relicFound));
        put(root, "enemySeen", packBools(enemySeen));
        put(root, "bossSeen", packBools(bossSeen));
        put(root, "bossKills", packInts(bossKills));
        put(root, "nodeLevel", packInts(nodeLevel));
        put(root, "achievements", packInts(achievements));
        put(root, "doctrineUnlocked", packBools(DOCTRINE_UNLOCKED));
        put(root, "doctrine", doctrine);
        return root;
    }

    private static void put(JSONObject o, String key, int value) {
        try {
            o.put(key, value);
        } catch (Throwable ignored) {}
    }

    private static void put(JSONObject o, String key, String value) {
        try {
            o.put(key, value == null ? "" : value);
        } catch (Throwable ignored) {}
    }

    private static int optInt(JSONObject root, String key, int fallback, int min, int max) {
        try {
            int value = root.optInt(key, fallback);
            if (value < min) value = min;
            if (value > max) value = max;
            return value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String packBools(boolean[] a) {
        if (a == null) return "";
        StringBuilder b = new StringBuilder(a.length);
        for (boolean v : a) b.append(v ? '1' : '0');
        return b.toString();
    }

    /** 只覆盖字符串里出现的下标；缺失部分保持默认值。 */
    private static void readBools(boolean[] into, String s) {
        if (into == null || s == null) return;
        int n = Math.min(into.length, s.length());
        for (int i = 0; i < n; i++) into[i] = s.charAt(i) == '1';
    }

    private static String packInts(int[] a) {
        if (a == null || a.length == 0) return "";
        StringBuilder b = new StringBuilder(a.length * 3);
        for (int i = 0; i < a.length; i++) {
            if (i > 0) b.append(',');
            b.append(Math.max(0, a[i]));
        }
        return b.toString();
    }

    /** 逐项解析；缺失/坏项保留默认值，长度不够时只填到末尾。 */
    private static void readInts(int[] into, String s, int min, int max) {
        if (into == null || s == null || s.isEmpty()) return;
        String[] parts = s.split(",");
        int n = Math.min(into.length, parts.length);
        for (int i = 0; i < n; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i].trim());
            } catch (Throwable t) {
                continue;
            }
            if (value < min) value = min;
            if (value > max) value = max;
            into[i] = value;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  写盘
    // ══════════════════════════════════════════════════════════════

    /** 异步保存：主线程只做序列化（百级字符串拼接），磁盘写在单线程池。 */
    void save() {
        final String json;
        synchronized (this) {
            json = encode().toString();
            lastJson = json;
        }
        try {
            io.execute(new Runnable() {
                @Override public void run() { write(json); }
            });
        } catch (RejectedExecutionException e) {
            write(json);   // 已关闭：至少同步落盘一次，别丢进度
        } catch (Throwable t) {
            error = "元进度保存失败：" + t.getClass().getSimpleName();
        }
    }

    /** 同步保存（App 切后台/退出时使用）。 */
    void saveNow() {
        final String json;
        synchronized (this) {
            json = encode().toString();
            lastJson = json;
        }
        write(json);
    }

    private void write(String json) {
        if (json == null || json.isEmpty()) return;
        if (prefs == null) {
            error = "元进度存储不可用";
            return;
        }
        try {
            boolean ok = prefs.edit().putString(KEY, json).commit();
            error = ok ? "" : "元进度保存失败：设备存储拒绝写入";
        } catch (Throwable t) {
            error = "元进度保存失败：空间不足或存储不可写";
        }
    }

    /** 测试/工具用：等待异步写盘完成（最多 2 秒）。 */
    void flush() {
        try {
            io.submit(new Runnable() {
                @Override public void run() {}
            }).get(2, TimeUnit.SECONDS);
        } catch (Throwable ignored) {}
    }

    void close() {
        try {
            saveNow();
        } catch (Throwable ignored) {}
        try {
            io.shutdown();
        } catch (Throwable ignored) {}
    }

    String lastJson() { return lastJson; }

    // ══════════════════════════════════════════════════════════════
    //  节点
    // ══════════════════════════════════════════════════════════════

    /** 下一级价格：20 + 当前等级 × 35。 */
    public int nodeCost(int node) {
        if (node < 0 || node >= NODES) return Integer.MAX_VALUE;
        return 20 + level(node) * 35;
    }

    /** 花星尘购买一级。满级或星尘不足返回 false（不扣钱）。 */
    boolean buyNode(int node) {
        if (node < 0 || node >= NODES) return false;
        synchronized (this) {
            if (nodeLevel[node] >= NODE_MAX[node]) return false;
            int cost = nodeCost(node);
            if (dust < cost) return false;
            dust -= cost;
            nodeLevel[node]++;
            applyNodeReveals();
            refreshAchievements();
        }
        save();
        return true;
    }

    boolean nodeMaxed(int node) {
        return node < 0 || node >= NODES || nodeLevel[node] >= NODE_MAX[node];
    }

    int level(int node) {
        if (node < 0 || node >= NODES || nodeLevel == null || node >= nodeLevel.length) return 0;
        int v = nodeLevel[node];
        if (v < 0) return 0;
        return Math.min(v, NODE_MAX[node]);
    }

    public static String nodeName(int node) { return node >= 0 && node < NODES ? NODE_NAME[node] : ""; }

    public static String nodeDesc(int node) { return node >= 0 && node < NODES ? NODE_DESC[node] : ""; }

    public static int nodeGroup(int node) { return node >= 0 && node < NODES ? NODE_GROUP[node] : 5; }

    public static String nodeGroupName(int group) {
        return group >= 0 && group < GROUPS ? GROUP_NAMES[group] : "";
    }

    public static int nodeMax(int node) { return node >= 0 && node < NODES ? NODE_MAX[node] : 0; }

    public static String nodeKey(int node) { return node >= 0 && node < NODES ? NODE_KEY[node] : ""; }

    /**
     * 指定等级下的下一级价格：{@code 20 + level*35}。
     * <p>与 {@link #nodeCost(int)} 同一公式，但直接吃"当前等级"，
     * 供 UI 在没有 Meta 实例时按屏幕上的等级算价（保证显示价与扣费一致）。
     */
    public static int costForLevel(int level) {
        int l = Math.max(0, level);
        return 20 + l * 35;
    }

    /** 某分组下的全部节点下标（升序、保持原始下标），供 UI 动态分桶。 */
    public static int[] groupNodes(int group) {
        int n = 0;
        for (int i = 0; i < NODES; i++) if (NODE_GROUP[i] == group) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < NODES; i++) if (NODE_GROUP[i] == group) out[k++] = i;
        return out;
    }

    /** 每个分组的节点数量（长度 = GROUPS）。 */
    public static int[] groupSizes() {
        int[] out = new int[GROUPS];
        for (int i = 0; i < NODES; i++) out[NODE_GROUP[i]]++;
        return out;
    }

    /** 全部节点等级上限之和（总进度条分母）。 */
    public static int maxTotalLevels() {
        int n = 0;
        for (int i = 0; i < NODES; i++) n += NODE_MAX[i];
        return n;
    }

    /** 已点亮（至少 1 级）的节点数。 */
    int nodesLit() {
        int n = 0;
        for (int i = 0; i < NODES; i++) if (level(i) > 0) n++;
        return n;
    }

    int nodesMaxed() {
        int n = 0;
        for (int i = 0; i < NODES; i++) if (nodeMaxed(i)) n++;
        return n;
    }

    /** 该分组已投入的总等级。 */
    int groupLevel(int group) {
        int n = 0;
        for (int i = 0; i < NODES; i++) if (NODE_GROUP[i] == group) n += level(i);
        return n;
    }

    // ══════════════════════════════════════════════════════════════
    //  成就奖励（研究 §2.8：成就 → 具名解锁）
    //
    //  设计约束（为什么这么写）：
    //   * 奖励**不落盘**。激活状态完全由 achievements[] 与 Achievements.TARGET[]
    //     现场推导，没有第二份布尔表，也就不可能出现"成就清零但奖励还在"的漂移。
    //   * 因此 resetAll() → resetDefaults() → Arrays.fill(achievements, 0) 之后，
    //     本组方法必然全部返回 false / 0，无需额外清理代码。
    //   * 六个奖励全部挂在**已有的 getter** 上（startingCoins / freeRevives /
    //     runDustBonus / startingRelics / markGain / bonusDamage），这些 getter
    //     本来就被 Session.applyMetaBoosts 与 recordRun 消费，所以不需要新子系统，
    //     也不新增任何 SharedPreferences 键。
    // ══════════════════════════════════════════════════════════════

    /** 奖励位掩码常量，下标与 {@link Achievements} 的成就下标一一对应。 */
    public static final int PERK_FIRST_WIN = 1 << Achievements.A_FIRST_WIN;       // 开局星尘
    public static final int PERK_RUNS_10 = 1 << Achievements.A_RUNS_10;           // 免费复活
    public static final int PERK_RUNS_100 = 1 << Achievements.A_RUNS_100;         // 局末星尘
    public static final int PERK_FIRST_BOSS = 1 << Achievements.A_FIRST_BOSS;     // 开局遗物
    public static final int PERK_ALL_BOSS = 1 << Achievements.A_ALL_BOSS;         // 印记获取
    public static final int PERK_KILLS_1K = 1 << Achievements.A_KILLS_1K;         // 伤害

    /**
     * 成就 i 的奖励是否生效。
     *
     * <p><b>永远派生，绝不存储</b>：等价于 {@code achievements[i] >= TARGET[i]}。
     * 成就进度本身是单调的且由 {@link #refreshAchievementsLocked()} 统一推进，
     * 所以这里不需要加锁，也读不到"半更新"的状态。
     *
     * @param i 成就下标；越界返回 false
     */
    public boolean achievementRewardActive(int i) {
        return Achievements.complete(this, i);
    }

    /** 已生效的奖励位掩码（{@link #PERK_*} 的按位或），供 UI / 调试一次性读取。 */
    public int achievementPerks() {
        int mask = 0;
        int n = Math.min(ACHIEVEMENTS, Achievements.REWARDED);
        for (int i = 0; i < n; i++) {
            if (achievementRewardActive(i)) mask |= 1 << i;
        }
        return mask;
    }

    /** 已生效的奖励数量（0..Achievements.REWARDED），给"成就奖励 x/6"这类文案用。 */
    public int achievementPerksActive() {
        int n = 0;
        int max = Math.min(ACHIEVEMENTS, Achievements.REWARDED);
        for (int i = 0; i < max; i++) if (achievementRewardActive(i)) n++;
        return n;
    }

    // ══════════════════════════════════════════════════════════════
    //  派生加成（Session / GameView 读取）
    // ══════════════════════════════════════════════════════════════

    /** 最大生命（绝对值）。 */
    float bonusHp() { return level(0) * 6f; }

    /** 承伤减免比例 0..0.15。Session 应以 {@code 1 - bonusArmor()} 作为承伤乘区。 */
    float bonusArmor() { return Math.min(.15f, level(1) * .03f); }

    /** 承伤减免比例（等价 {@link #bonusArmor()}）。 */
    float damageReduction() { return bonusArmor(); }

    /** 生命回复加成比例。 */
    float bonusRegen() { return Math.min(.40f, level(2) * .08f); }

    /** 闪避冷却缩减比例 0..0.25。 */
    float bonusDashCd() { return Math.min(.25f, level(3) * .05f); }

    /** 移速加成比例。 */
    float bonusSpeed() { return Math.min(.15f, level(4) * .03f); }

    /**
     * 伤害加成比例。
     * <p>成就「千军辟易」奖励 +4%（{@link #PERK_KILLS_1K}），与节点 5 的 +5%/级叠加。
     * 消费方：{@code Session.applyMetaBoosts} → {@code p.damage *= 1 + bonusDamage()}。
     */
    float bonusDamage() {
        float v = Math.min(.25f, level(5) * .05f);
        if (achievementRewardActive(Achievements.A_KILLS_1K)) v += .04f;
        return v;
    }

    /** 暴击率加成（绝对值）。 */
    float bonusCrit() { return Math.min(.10f, level(6) * .02f); }

    /** 攻速加成比例。 */
    float bonusAtkSpeed() { return Math.min(.15f, level(7) * .03f); }

    /** 攻击范围加成比例。 */
    float bonusRange() { return Math.min(.20f, level(8) * .04f); }

    /** 星尘掉落加成比例。 */
    float bonusCoin() { return Math.min(.40f, level(9) * .08f); }

    /** 经验加成比例。 */
    float bonusXp() { return Math.min(.30f, level(10) * .06f); }

    /** 拾取范围（像素，绝对值）。 */
    float bonusMagnet() { return level(11) * 10f; }

    /** 流派专精乘区增量（Game.schoolBonus 语义为 0..0.5）。 */
    float schoolBonus(int school) {
        if (school < 0 || school >= 6) return 0f;
        return Math.min(.50f, level(14 + school) * .05f);
    }

    /**
     * 免费复活次数（节点 24 提供，契约上限 2 级）。
     * <p>成就「十次下潜」奖励 +1（{@link #PERK_RUNS_10}），与节点叠加。
     * 消费方：{@code Session.applyMetaBoosts} → {@code g.freeReviveCount}。
     */
    int freeRevives() {
        int v = level(24);
        if (achievementRewardActive(Achievements.A_RUNS_10)) v += 1;
        return v;
    }

    /**
     * 开局额外星尘。
     * <p>成就「初次远征」奖励 +15（{@link #PERK_FIRST_WIN}），与节点 12 的 +15/级叠加。
     * 消费方：{@code Session.applyMetaBoosts} → {@code g.coins += startingCoins()}。
     */
    int startingCoins() {
        int v = level(12) * 15;
        if (achievementRewardActive(Achievements.A_FIRST_WIN)) v += 15;
        return v;
    }

    /** 开局额外重掷。 */
    int startingRerolls() { return level(13); }

    /** 契约 §14 的别名：额外重掷次数。 */
    float bonusDraftReroll() { return level(13); }

    /**
     * 开局自带遗物数量。
     * <p>成就「斩首者」奖励 +1（{@link #PERK_FIRST_BOSS}），与节点 25 叠加。
     * 消费方：{@code Session.applyMetaBoosts} 按此数量发放随机遗物。
     */
    int startingRelics() {
        int v = level(25);
        if (achievementRewardActive(Achievements.A_FIRST_BOSS)) v += 1;
        return v;
    }

    /** 遗物携带上限增量。 */
    int relicSlots() { return level(20); }

    /** 商店折扣比例 0..0.15。 */
    float shopDiscount() { return Math.min(.15f, level(21) * .03f); }

    /** 事件收益加成比例。 */
    float eventBonus() { return level(22) * .06f; }

    /** 首领星尘加成比例。 */
    float bossBonus() { return level(23) * .10f; }

    /**
     * 印记获取加成比例。
     * <p>成就「八王之证」奖励 +10%（{@link #PERK_ALL_BOSS}），与节点 27 的 +5%/级叠加。
     * 消费方：{@link #recordRun} 里 {@code marks += round(marksGain * (1 + markGain()))}。
     */
    float markGain() {
        float v = Math.min(.25f, level(27) * .05f);
        if (achievementRewardActive(Achievements.A_ALL_BOSS)) v += .10f;
        return v;
    }

    /**
     * 局末星尘加成比例（节点 31 · 深渊馈赠）。
     * <p>成就「百战之证」奖励 +10%（{@link #PERK_RUNS_100}），与节点叠加。
     * 消费方：{@link #recordRun} 的局末星尘乘区。
     */
    float runDustBonus() {
        float v = Math.min(.25f, level(31) * .05f);
        if (achievementRewardActive(Achievements.A_RUNS_100)) v += .10f;
        return v;
    }

    // ══════════════════════════════════════════════════════════════
    //  契约 / 职业
    // ══════════════════════════════════════════════════════════════

    private static int contractCount() {
        try {
            int n = Balance.CONTRACTS.length;
            return n < 1 ? 1 : n;
        } catch (Throwable t) {
            return 3;
        }
    }

    /** 当前可选择的最高契约下标（基础 0..2 免费）。 */
    int contractCap() {
        int max = contractCount() - 1;
        int base = Math.max(2, pactUnlocked) + level(26);
        if (base < 0) base = 0;
        return Math.min(max, base);
    }

    /**
     * 解锁指定契约档所需的印记（纯函数，给 UI 报价用）。
     *
     * <p>必须与 {@link #pactCost()} 同源：那个是实例上的"下一档"，
     * 这个按任意档位算，供准备页在锁定卡上直接标价。
     * 分成两个方法而不是让 UI 自己算，是因为职业解锁那次
     * （UI 写 60 / 实际扣 80）已经证明"两处各写一份公式"必然漂移。
     *
     * @param tier 目标档位；0..2 免费返回 0，越界返回 0
     */
    public static int pactCostOf(int tier) {
        int max = contractCount() - 1;
        if (tier < 0 || tier > max || tier <= 2) return 0;
        return 100 + (tier - 3) * 120;
    }

    /** 解锁下一档契约所需印记；已全部解锁返回 -1。 */
    int pactCost() {
        int max = contractCount() - 1;
        int next = Math.max(2, pactUnlocked) + 1;
        if (next > max) return -1;
        return pactCostOf(next);
    }

    boolean unlockPact() {
        synchronized (this) {
            int cost = pactCost();
            if (cost < 0 || marks < cost) return false;
            marks -= cost;
            totalMarksSpent += cost;
            pactUnlocked = Math.max(2, pactUnlocked) + 1;
        }
        save();
        return true;
    }

    /** 职业解锁价格（印记，递增）；0..3 免费。 */
    int roleCost(int role) { return roleCostOf(role); }

    /**
     * 职业解锁价格（静态版）。
     *
     * <p>UI 层（{@code screen/CharScreen}）必须显示与这里**完全一致**的数字。
     * 历史上 UI 自己算了一套 `60 + (n-4)*30`，而这里扣的是 `80 + (n-4)*60`——
     * 标价 60、实扣 80，玩家点下去发现印记不够。现在两边共用这一个函数。
     */
    public static int roleCostOf(int role) {
        if (role < 0 || role >= ROLES) return -1;
        if (role < 4) return 0;
        return 80 + (role - 4) * 60;
    }

    boolean isRoleUnlocked(int role) {
        if (role < 0 || role >= ROLES || roleUnlocked == null) return false;
        return role < 4 || roleUnlocked[role];
    }

    /** 花印记解锁职业。已解锁或印记不足返回 false（不扣印记）。 */
    boolean unlockRole(int role) {
        if (role < 0 || role >= ROLES) return false;
        synchronized (this) {
            if (isRoleUnlocked(role)) return false;
            int cost = roleCost(role);
            if (cost < 0 || marks < cost) return false;
            marks -= cost;
            totalMarksSpent += cost;
            roleUnlocked[role] = true;
            refreshAchievements();
        }
        save();
        return true;
    }

    int unlockedRoles() {
        int n = 0;
        for (int i = 0; i < ROLES; i++) if (isRoleUnlocked(i)) n++;
        return n;
    }

    boolean allRolesUnlocked() { return unlockedRoles() >= ROLES; }

    // ══════════════════════════════════════════════════════════════
    //  图鉴
    // ══════════════════════════════════════════════════════════════

    void revealRelic(int id) {
        if (id < 0 || id >= RELICS || relicSeen == null) return;
        if (relicSeen[id]) return;
        synchronized (this) { relicSeen[id] = true; }
        save();
    }

    void revealEnemy(int id) {
        if (id < 0 || id >= ENEMIES || enemySeen == null) return;
        if (enemySeen[id]) return;
        synchronized (this) { enemySeen[id] = true; }
        save();
    }

    void revealBoss(int id) {
        if (id < 0 || id >= BOSSES || bossSeen == null) return;
        if (bossSeen[id]) return;
        synchronized (this) { bossSeen[id] = true; }
        save();
    }

    boolean relicSeen(int id) { return relicSeen != null && id >= 0 && id < RELICS && relicSeen[id]; }

    boolean enemySeen(int id) { return enemySeen != null && id >= 0 && id < ENEMIES && enemySeen[id]; }

    boolean bossSeen(int id) { return bossSeen != null && id >= 0 && id < BOSSES && bossSeen[id]; }

    /** 真正获得过的遗物（成就用；图鉴揭示不算）。 */
    boolean relicFound(int id) { return relicFound != null && id >= 0 && id < RELICS && relicFound[id]; }

    int relicSeenCount() { return count(relicSeen); }

    int enemySeenCount() { return count(enemySeen); }

    int bossSeenCount() { return count(bossSeen); }

    int relicFoundCount() { return count(relicFound); }

    private static int count(boolean[] a) {
        int n = 0;
        if (a != null) for (boolean v : a) if (v) n++;
        return n;
    }

    /** 遗物图鉴完成度 0..1。 */
    float relicCompletion() { return Math.min(1f, relicSeenCount() / (float) RELICS); }

    float enemyCompletion() { return Math.min(1f, enemySeenCount() / (float) ENEMIES); }

    float bossCompletion() { return Math.min(1f, bossSeenCount() / (float) BOSSES); }

    int bossKillsOf(int id) {
        return bossKills != null && id >= 0 && id < BOSSES ? Math.max(0, bossKills[id]) : 0;
    }

    /** 节点赋予的图鉴揭示：确定性地点亮前 K 个条目，重登保持一致。 */
    private void applyNodeReveals() {
        int relics = Math.min(RELICS, level(28) * 4);
        for (int i = 0; i < relics; i++) relicSeen[i] = true;
        int enemies = Math.min(ENEMIES, level(29) * 2);
        for (int i = 0; i < enemies; i++) enemySeen[i] = true;
        int bosses = Math.min(BOSSES, level(30));
        for (int i = 0; i < bosses; i++) bossSeen[i] = true;
    }

    // ══════════════════════════════════════════════════════════════
    //  结算
    // ══════════════════════════════════════════════════════════════

    /**
     * 局末结算：星尘/印记、记录、图鉴、成就。只读 {@link Game} 的公开字段，不修改战局。
     * <p>数值来自 {@code Balance.dustReward/markReward}（contract §12）。
     */
    void recordRun(Game g, boolean win) {
        if (g == null) return;
        int dustGain;
        int marksGain;
        try {
            dustGain = Math.max(0, Balance.dustReward(g, win));
            marksGain = Math.max(0, Balance.markReward(g, win));
        } catch (Throwable t) {
            dustGain = Math.max(0, g.kills / 3);
            marksGain = win ? 2 : 1;
        }
        int stay = 0;
        if (Float.isFinite(g.time) && g.time > 0) stay = (int) Math.min(86_400f, g.time);

        synchronized (this) {
            // 元进度加成：首领星尘 + 局末星尘（节点 23 / 31）。
            int bosses = Math.max(0, g.bossesDefeated);
            float bossPart = 1f + bossBonus() * Math.min(1f, bosses / 3f);
            float runPart = 1f + runDustBonus();
            dustGain = Math.round(dustGain * Math.min(4f, bossPart * runPart));

            runs++;
            if (win) wins++;
            totalKills += Math.max(0, g.kills);
            bestKills = Math.max(bestKills, Math.max(0, g.kills));
            playSeconds += stay;
            bestFloor = Math.max(bestFloor, Math.max(1, g.roomsVisited + 1));

            dust += dustGain;
            totalDustGained += dustGain;
            marks += Math.round(marksGain * (1f + markGain()));

            if (g.bossKillsRun != null) {
                for (int i = 0; i < BOSSES && i < g.bossKillsRun.length; i++) {
                    int n = g.bossKillsRun[i];
                    if (n <= 0) continue;
                    bossKills[i] += n;
                    bossSeen[i] = true;
                }
            }
            if (g.bossesDefeated > 0) bossesDefeatedVisual();

            if (g.mode == Game.MODE_ENDLESS) bestEndless = Math.max(bestEndless, g.roomsVisited);
            if (g.mode == Game.MODE_BOSS_RUSH) bestBossRush = Math.max(bestBossRush, g.bossesDefeated);
            if (Float.isFinite(g.xpTotal)) bestXp = Math.max(bestXp, (int) Math.min(2_000_000_000f, g.xpTotal));

            int maxSynergy = 0;
            int role = -1;
            if (g.players != null) {
                for (Game.Player p : g.players) {
                    if (p == null) continue;
                    if (role < 0) role = p.role;
                    if (p.relics != null) {
                        for (int id = 0; id < RELICS && id < p.relics.length; id++) {
                            if (p.relics[id] > 0) { relicSeen[id] = true; relicFound[id] = true; }
                        }
                    }
                    maxSynergy = Math.max(maxSynergy, Integer.bitCount(p.synergyMask & 0x3F));
                }
            }
            bestSynergy = Math.max(bestSynergy, Math.min(6, maxSynergy));
            if (win && role >= 0 && role < ROLES) roleWon[role] = true;

            // 敌种图鉴：结算时仍在场的敌种（Game 没有全量遭遇表，属防御性近似）
            if (g.enemies != null) {
                for (Game.Enemy e : g.enemies) {
                    if (e == null || e.hp <= 0) continue;
                    if (e.type >= 0 && e.type < ENEMIES) enemySeen[e.type] = true;
                }
            }

            // 一次性成就
            if (win) {
                int hardest = Math.max(0, contractCount() - 1);
                if (g.contract >= hardest) achievementLocked(Achievements.A_INFERNO, 1);
                if (g.freeRevivesUsed <= 0) achievementLocked(Achievements.A_NO_REVIVE, 1);
                if (g.mode == Game.MODE_DAILY) achievementLocked(Achievements.A_DAILY, 1);
                achievementLocked(Achievements.A_FIRST_WIN, 1);
                // 天赋牌解锁依据：记录通关过的最高契约
                noteContractCleared(g.contract);
            }
            if (g.bossesDefeated > 0) achievementLocked(Achievements.A_FIRST_BOSS, 1);

            refreshAchievementsLocked();
            // 结算后自动解锁条件已满足的天赋牌
            for (int i = 0; i < DOCTRINE_COUNT; i++) {
                if (doctrineUnlocked(i)) continue;
                if (doctrineRequirementMet(i)) DOCTRINE_UNLOCKED[i] = true;
            }
            normalizeDoctrine();
        }
        save();
    }

    /** 兜底：没有 bossKillsRun 数据时，按已击败数量点亮图鉴。 */
    private void bossesDefeatedVisual() {
        for (int i = 0; i < BOSSES; i++) if (bossKills[i] > 0) bossSeen[i] = true;
    }

    /** 外部推进成就（事件/图鉴界面可调用）。 */
    void achievement(int index, int value) {
        synchronized (this) { achievementLocked(index, value); }
        save();
    }

    private void achievementLocked(int index, int value) {
        if (index < 0 || index >= ACHIEVEMENTS || achievements == null) return;
        int v = Math.max(0, Math.min(value, Achievements.TARGET[index]));
        if (v > achievements[index]) achievements[index] = v;
    }

    private void refreshAchievements() {
        synchronized (this) { refreshAchievementsLocked(); }
    }

    /** 根据持久计数器刷新成就（单调递增，不覆盖更高的已有进度）。 */
    private void refreshAchievementsLocked() {
        if (achievements == null) return;
        achievementLocked(Achievements.A_FIRST_WIN, wins > 0 ? 1 : 0);
        achievementLocked(Achievements.A_RUNS_10, runs);
        achievementLocked(Achievements.A_RUNS_100, runs);
        achievementLocked(Achievements.A_FIRST_BOSS, bossKillsTotal() > 0 ? 1 : 0);
        achievementLocked(Achievements.A_ALL_BOSS, bossesDefeatedCount());
        achievementLocked(Achievements.A_KILLS_1K, totalKills);
        achievementLocked(Achievements.A_KILLS_10K, totalKills);
        achievementLocked(Achievements.A_RUN_KILLS, bestKills);
        achievementLocked(Achievements.A_RELIC_20, relicFoundCount());
        achievementLocked(Achievements.A_RELIC_40, relicFoundCount());
        achievementLocked(Achievements.A_RELIC_60, relicFoundCount());
        achievementLocked(Achievements.A_ROLES_12, unlockedRoles());
        achievementLocked(Achievements.A_NODES_16, nodesLit());
        achievementLocked(Achievements.A_NODES_32, nodesLit());
        achievementLocked(Achievements.A_SYNERGY_3, bestSynergy);
        achievementLocked(Achievements.A_XP_10K, bestXp);
        achievementLocked(Achievements.A_ALL_ROLE_WIN, roleWonCount());
        achievementLocked(Achievements.A_ENDLESS_20, bestEndless);
        achievementLocked(Achievements.A_BOSS_RUSH_5, bestBossRush);
        achievementLocked(Achievements.A_PLAYTIME, playSeconds);
        achievementLocked(Achievements.A_DUST_5K, totalDustGained);
    }

    int bossKillsTotal() {
        int n = 0;
        if (bossKills != null) for (int v : bossKills) n += Math.max(0, v);
        return n;
    }

    /** 至少击败过一次的首领数量。 */
    int bossesDefeatedCount() {
        int n = 0;
        if (bossKills != null) for (int v : bossKills) if (v > 0) n++;
        return n;
    }

    int roleWonCount() {
        int n = 0;
        if (roleWon != null) for (boolean v : roleWon) if (v) n++;
        return n;
    }

    // ══════════════════════════════════════════════════════════════
    //  成就查询（供 CodexScreen / 结算界面直接用）
    // ══════════════════════════════════════════════════════════════

    boolean achievementComplete(int index) { return Achievements.complete(this, index); }

    int achievementProgress(int index) { return Achievements.progress(this, index); }

    String achievementText(int index) { return Achievements.progressText(this, index); }

    int achievementsComplete() { return Achievements.completed(this); }

    float achievementCompletion() { return Achievements.completion(this); }

    // ══════════════════════════════════════════════════════════════
    //  天赋牌 Doctrine：查询 / 解锁 / 选择
    // ══════════════════════════════════════════════════════════════

    public static String doctrineName(int i) {
        return i >= 0 && i < DOCTRINE_COUNT ? DOCTRINE_NAME[i] : "";
    }

    public static String doctrineDesc(int i) {
        return i >= 0 && i < DOCTRINE_COUNT ? DOCTRINE_DESC[i] : "";
    }

    public static int doctrineGroup(int i) {
        return i >= 0 && i < DOCTRINE_COUNT ? DOCTRINE_GROUP[i] : 0;
    }

    public static String doctrineGroupName(int group) {
        return group >= 0 && group < DOCTRINE_GROUPS ? DOCTRINE_GROUP_NAMES[group] : "";
    }

    public static String doctrineKey(int i) {
        return i >= 0 && i < DOCTRINE_COUNT ? DOCTRINE_KEY[i] : "";
    }

    public static String doctrineUnlockDesc(int i) {
        return i >= 0 && i < DOCTRINE_COUNT ? DOCTRINE_UNLOCK_DESC[i] : "";
    }

    public static String doctrineUnlockDesc(int i, Meta m) {
        if (i < 0 || i >= DOCTRINE_COUNT) return "";
        if (m == null || m.doctrineUnlocked(i)) return "已解锁";
        return DOCTRINE_UNLOCK_DESC[i];
    }

    /** 该组下的牌下标（升序）。 */
    public static int[] doctrineGroupMembers(int group) {
        int n = 0;
        for (int i = 0; i < DOCTRINE_COUNT; i++) if (DOCTRINE_GROUP[i] == group) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < DOCTRINE_COUNT; i++) if (DOCTRINE_GROUP[i] == group) out[k++] = i;
        return out;
    }

    /** 是否已解锁。越界返回 false。 */
    public boolean doctrineUnlocked(int i) {
        if (i < 0 || i >= DOCTRINE_COUNT || DOCTRINE_UNLOCKED == null) return false;
        if (DOCTRINE_REQ_TYPE[i] == REQ_NONE) return true;
        return DOCTRINE_UNLOCKED[i];
    }

    /** 当前满足解锁条件但尚未解锁的牌数量（UI 可显示小红点）。 */
    public int doctrineUnlockableCount() {
        int n = 0;
        for (int i = 0; i < DOCTRINE_COUNT; i++) {
            if (!doctrineUnlocked(i) && doctrineRequirementMet(i)) n++;
        }
        return n;
    }

    public int doctrineUnlockedCount() {
        int n = 0;
        for (int i = 0; i < DOCTRINE_COUNT; i++) if (doctrineUnlocked(i)) n++;
        return n;
    }

    /**
     * 判断解锁条件是否满足（不检查"是否已经解锁"）。
     * <p>纯计算，不改字段；{@link #unlockDoctrine(int)} 与 {@link #refreshDoctrineUnlocks()} 共用。
     */
    boolean doctrineRequirementMet(int i) {
        if (i < 0 || i >= DOCTRINE_COUNT) return false;
        int type = DOCTRINE_REQ_TYPE[i];
        int need = DOCTRINE_REQ_VALUE[i];
        switch (type) {
            case REQ_NONE:
                return true;
            case REQ_WINS:
                return wins >= need;
            case REQ_CONTRACT:
                return highestClearedContract() >= need;
            case REQ_ACHIEVEMENT:
                return achievementComplete(need);
            case REQ_TOTAL_MARKS:
                return marks + totalMarksSpent >= need;
            case REQ_BOSS_KINDS:
                return bossesDefeatedCount() >= need;
            default:
                return false;
        }
    }

    /**
     * 尝试解锁一张牌（条件满足才成功）。成功返回 true 并落盘。
     * <p>已解锁返回 false（不重复扣任何东西）。
     */
    boolean unlockDoctrine(int i) {
        if (i < 0 || i >= DOCTRINE_COUNT) return false;
        boolean ok;
        synchronized (this) {
            if (doctrineUnlocked(i)) return false;
            if (!doctrineRequirementMet(i)) return false;
            DOCTRINE_UNLOCKED[i] = true;
            ok = true;
        }
        if (ok) save();
        return ok;
    }

    /** 条件满足的牌自动解锁（开局/结算时调用），返回本次新解锁的数量。 */
    int refreshDoctrineUnlocks() {
        int n = 0;
        synchronized (this) {
            for (int i = 0; i < DOCTRINE_COUNT; i++) {
                if (doctrineUnlocked(i)) continue;
                if (!doctrineRequirementMet(i)) continue;
                DOCTRINE_UNLOCKED[i] = true;
                n++;
            }
        }
        if (n > 0) save();
        return n;
    }

    /** 当前装备的牌；未装备返回 {@link #DOCTRINE_NONE}。 */
    public int selectedDoctrine() {
        int d = doctrine;
        if (d < 0 || d >= DOCTRINE_COUNT) return DOCTRINE_NONE;
        return doctrineUnlocked(d) ? d : DOCTRINE_NONE;
    }

    /**
     * 装备一张牌。传 {@link #DOCTRINE_NONE} 表示卸下。未解锁或越界的牌不会被装备。
     *
     * @return 是否成功（未装备牌传 -1 也算成功）
     */
    boolean selectDoctrine(int i) {
        if (i < DOCTRINE_NONE || i >= DOCTRINE_COUNT) return false;
        synchronized (this) {
            if (i != DOCTRINE_NONE && !doctrineUnlocked(i)) return false;
            doctrine = i;
        }
        save();
        return true;
    }

    /** 当前装备牌的名字（未装备返回"未装备"）。 */
    String selectedDoctrineName() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? "未装备" : DOCTRINE_NAME[d];
    }

    /** 当前装备牌的主钩子；未装备返回 {@link #H_NONE}。 */
    int doctrineHook() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? H_NONE : DOCTRINE_HOOK[d];
    }

    float doctrineParam() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? 0f : DOCTRINE_PARAM[d];
    }

    float doctrineParam2() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? 0f : DOCTRINE_PARAM2[d];
    }

    int doctrineSideHook() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? H_NONE : DOCTRINE_SIDE[d];
    }

    float doctrineSideParam() {
        int d = selectedDoctrine();
        return d == DOCTRINE_NONE ? 0f : DOCTRINE_SIDE_PARAM[d];
    }

    // ══════════════════════════════════════════════════════════════
    //  解锁依据的累计量（天赋牌条件与 UI 进度共用）
    // ══════════════════════════════════════════════════════════════

    /** 历史累计花掉的印记（用于"累计获得 200 印记"这类条件，避免花掉就不算）。 */
    int totalMarksSpent;

    /** 已通关的最高契约下标；从未通关返回 -1。 */
    int highestClearedContract() {
        return Math.max(-1, highestContractCleared);
    }

    /** 已通关的最高契约下标；-1 表示还没通关过。 */
    int highestContractCleared = -1;

    /** 记录一次通关的契约（单调取最大）。 */
    private void noteContractCleared(int contract) {
        if (contract > highestContractCleared) highestContractCleared = contract;
    }

    /** 解锁条件进度文案，如 "通关 2 / 3"。 */
    public String doctrineRequirementProgress(int i) {
        if (i < 0 || i >= DOCTRINE_COUNT) return "";
        int type = DOCTRINE_REQ_TYPE[i];
        int need = DOCTRINE_REQ_VALUE[i];
        switch (type) {
            case REQ_NONE:
                return "默认解锁";
            case REQ_WINS:
                return Math.min(need, wins) + " / " + need + " 次通关";
            case REQ_CONTRACT:
                return (highestClearedContract() >= need ? "已达成" : "未达成") + " · 需通关「"
                        + contractName(need) + "」";
            case REQ_ACHIEVEMENT:
                return achievementComplete(need) ? "已达成" : "未达成 · 需成就「"
                        + Achievements.name(need) + "」";
            case REQ_TOTAL_MARKS:
                return Math.min(need, marks + totalMarksSpent) + " / " + need + " 印记";
            case REQ_BOSS_KINDS:
                return Math.min(need, bossesDefeatedCount()) + " / " + need + " 位首领";
            default:
                return "";
        }
    }

    private static String contractName(int idx) {
        try {
            int i = Math.max(0, Math.min(Balance.CONTRACTS.length - 1, idx));
            return Balance.CONTRACTS[i];
        } catch (Throwable t) {
            return "契约 " + idx;
        }
    }

    /** 天赋牌表自检。 */
    static boolean doctrinesValid() {
        if (DOCTRINE_NAME.length != DOCTRINE_COUNT || DOCTRINE_DESC.length != DOCTRINE_COUNT
                || DOCTRINE_GROUP.length != DOCTRINE_COUNT || DOCTRINE_KEY.length != DOCTRINE_COUNT
                || DOCTRINE_UNLOCK_DESC.length != DOCTRINE_COUNT
                || DOCTRINE_HOOK.length != DOCTRINE_COUNT || DOCTRINE_PARAM.length != DOCTRINE_COUNT
                || DOCTRINE_SIDE.length != DOCTRINE_COUNT
                || DOCTRINE_REQ_TYPE.length != DOCTRINE_COUNT
                || DOCTRINE_REQ_VALUE.length != DOCTRINE_COUNT) return false;
        for (int i = 0; i < DOCTRINE_COUNT; i++) {
            if (DOCTRINE_NAME[i] == null || DOCTRINE_NAME[i].isEmpty()) return false;
            if (DOCTRINE_DESC[i] == null || DOCTRINE_DESC[i].isEmpty()) return false;
            if (DOCTRINE_KEY[i] == null || DOCTRINE_KEY[i].isEmpty()) return false;
            if (DOCTRINE_UNLOCK_DESC[i] == null || DOCTRINE_UNLOCK_DESC[i].isEmpty()) return false;
            if (DOCTRINE_GROUP[i] < 0 || DOCTRINE_GROUP[i] >= DOCTRINE_GROUPS) return false;
        }
        // 每组恰好 3 张
        int[] per = new int[DOCTRINE_GROUPS];
        for (int i = 0; i < DOCTRINE_COUNT; i++) per[DOCTRINE_GROUP[i]]++;
        for (int g = 0; g < DOCTRINE_GROUPS; g++) if (per[g] != 3) return false;
        // 存档键唯一
        for (int i = 0; i < DOCTRINE_COUNT; i++)
            for (int j = i + 1; j < DOCTRINE_COUNT; j++)
                if (DOCTRINE_KEY[i].equals(DOCTRINE_KEY[j])) return false;
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  自检
    // ══════════════════════════════════════════════════════════════

    /** 表结构自检：节点表与内容表规模对得上、成就表长度正确。 */
    static boolean valid() {
        if (NODE_NAME.length != NODES || NODE_DESC.length != NODES || NODE_KEY.length != NODES
                || NODE_GROUP.length != NODES || NODE_MAX.length != NODES) return false;
        for (int i = 0; i < NODES; i++) {
            if (NODE_NAME[i] == null || NODE_NAME[i].isEmpty()) return false;
            if (NODE_DESC[i] == null || NODE_DESC[i].isEmpty()) return false;
            if (NODE_KEY[i] == null || NODE_KEY[i].isEmpty()) return false;
            if (NODE_GROUP[i] < 0 || NODE_GROUP[i] >= GROUPS) return false;
            if (NODE_MAX[i] < 1) return false;
        }
        return Achievements.valid()
                && doctrinesValid()
                && Content.ROLE_COUNT == ROLES
                && Content.SCHOOL_COUNT == 6
                && Relics.COUNT == RELICS
                && Enemies.COUNT == ENEMIES
                && Bosses.COUNT == BOSSES;
    }

    /** 测试用：不落盘的进度对象（prefs 为 null，save 只留在内存）。 */
    static Meta forTest() {
        ExecutorService io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "abyss-meta-test");
                t.setDaemon(true);
                return t;
            }
        });
        Meta meta = new Meta(null, null, null, io);
        meta.applyNodeReveals();
        meta.refreshAchievements();
        return meta;
    }

    /** 人类可读摘要，用于"继续远征"卡片/日志。 */
    String summary() {
        return "星尘 " + dust + " · 印记 " + marks + " · 远征 " + runs + " 次 / 通关 " + wins
                + " · 节点 " + nodesLit() + "/" + NODES
                + " · 成就 " + achievementsComplete() + "/" + ACHIEVEMENTS
                + " · 天赋 " + doctrineUnlockedCount() + "/" + DOCTRINE_COUNT
                + "（已装备 " + selectedDoctrineName() + "）";
    }
}
