package com.dilao.abysssquad;

/**
 * 24 个深渊成就。
 * <p>只提供静态名录与只读判定；进度值存放在 {@link Meta#achievements}，
 * 由 {@link Meta#recordRun} 在结算时更新。这里是纯数据 + 纯函数，
 * 不持有 Context、不写盘、不抛异常。
 */
public final class Achievements {

    private Achievements() {}

    public static final int COUNT = 24;

    // ── 索引常量（Meta 更新进度时使用）────────────────────────────
    public static final int A_FIRST_WIN = 0, A_RUNS_10 = 1, A_RUNS_100 = 2, A_FIRST_BOSS = 3,
            A_ALL_BOSS = 4, A_KILLS_1K = 5, A_KILLS_10K = 6, A_RUN_KILLS = 7,
            A_INFERNO = 8, A_NO_REVIVE = 9, A_RELIC_20 = 10, A_RELIC_40 = 11,
            A_RELIC_60 = 12, A_ROLES_12 = 13, A_NODES_16 = 14, A_NODES_32 = 15,
            A_SYNERGY_3 = 16, A_XP_10K = 17, A_ALL_ROLE_WIN = 18, A_ENDLESS_20 = 19,
            A_BOSS_RUSH_5 = 20, A_DAILY = 21, A_PLAYTIME = 22, A_DUST_5K = 23;

    // ── 品质：0 铜 / 1 银 / 2 金 / 3 白金（LOCKED 顺序）──────────
    public static final String[] TIER_NAMES = {"铜", "银", "金", "白金"};
    public static final int[] TIER_COLOR = {0xFFC08A5E, 0xFFB9C6CC, 0xFFEAC487, 0xFFC2A7FF};

    public static final String[] NAMES = {
            "初次远征", "十次下潜", "百战之证", "斩首者",
            "八王之证", "千军辟易", "万骨成山", "单局屠戮",
            "炼狱之路", "不灭意志", "收藏家·初", "收藏家·中",
            "收藏家·终", "十二面体", "星火初燃", "星火燎原",
            "三重共鸣", "战斗狂热", "全职业制霸", "无尽行者",
            "连战之王", "星盘常客", "深渊常客", "星尘富翁"
    };

    public static final String[] DESC = {
            "完成一次远征，把星光带回营地",
            "累计完成 10 次远征",
            "累计完成 100 次远征",
            "首次击败任意一位首领",
            "击败全部 8 位首领",
            "累计击破 1000 个敌人",
            "累计击破 10000 个敌人",
            "单局击破 500 个敌人",
            "以炼狱契约完成一次远征",
            "不使用免费复活完成一次远征",
            "遗物图鉴点亮 20 件",
            "遗物图鉴点亮 40 件",
            "遗物图鉴点亮 60 件",
            "解锁全部 12 个职业",
            "点亮 16 个元进度节点",
            "点亮全部 32 个元进度节点",
            "单局同时激活 3 个流派共鸣",
            "单局经验当量突破 10000",
            "用 12 个职业各通关一次",
            "无尽模式抵达第 20 站",
            "首领连战击败 5 位首领",
            "完成一次每日星盘",
            "累计游戏时长 10 小时",
            "累计获得 5000 星尘"
    };

    /** 0 铜 / 1 银 / 2 金 / 3 白金。 */
    public static final int[] TIER = {
            0, 0, 2, 0,
            3, 1, 3, 2,
            3, 1, 0, 1,
            2, 2, 1, 3,
            2, 2, 3, 2,
            1, 1, 2, 1
    };

    /** 目标进度值；二元成就为 1。 */
    public static final int[] TARGET = {
            1, 10, 100, 1,
            8, 1000, 10000, 500,
            1, 1, 20, 40,
            60, 12, 16, 32,
            3, 10000, 12, 20,
            5, 1, 36000, 5000
    };

    // ── 具名奖励（研究 §2.8：成就 → 解锁"有名字的东西"）──────────
    //
    // 为什么只给前 6 个：这 6 个是"新手一定会撞到"的成就（通关一次、击杀首领、
    // 累计击杀、十次下潜、百次下潜、全首领），把真正的玩法解锁放在这里，
    // 玩家第一小时内就能感到"成就不是计数器"。其余 18 个留空，将来可挂外观/图鉴。
    //
    // 为什么奖励不落存档：激活状态完全由 Meta.achievementRewardActive(i) 按
    // achievements[i] >= TARGET[i] 现场推导，不新增布尔表、不新增 SharedPreferences 键。
    // 这样 resetAll() 清零成就后奖励必然一起失效，也不存在两套数据互相漂移。
    //
    // 每个奖励都必须能"在代码里被验证"——下面是权威落点（不新造子系统）：
    //   0 初次远征 → Meta.startingCoins()   → Session.applyMetaBoosts 写 g.coins
    //   1 十次下潜 → Meta.freeRevives()     → Session.applyMetaBoosts 写 g.freeReviveCount
    //   2 百战之证 → Meta.runDustBonus()    → Meta.recordRun 局末星尘乘区
    //   3 斩首者   → Meta.startingRelics()  → Session.applyMetaBoosts 发开局遗物
    //   4 八王之证 → Meta.markGain()        → Meta.recordRun 印记获取乘区
    //   5 千军辟易 → Meta.bonusDamage()     → Session.applyMetaBoosts 乘进 p.damage

    /** 带具名奖励的成就数量（下标 0..REWARDED-1）。 */
    public static final int REWARDED = 6;

    /** 奖励名，index 与 {@link #NAMES} 对齐；空串表示该成就没有奖励。 */
    public static final String[] REWARD_NAME = {
            "余烬火种", "老练下潜", "百战账本", "猎首战利品", "八王功勋", "千军余威",
            "", "", "", "", "", "",
            "", "", "", "", "", "",
            "", "", "", "", "", ""
    };

    /** 奖励效果说明（一格一行，UI 直接显示）；空串表示无奖励。 */
    public static final String[] REWARD_DESC = {
            "每局开局星尘 +15（与节点 12 叠加）",
            "每局免费复活 +1（与节点 24 叠加）",
            "局末结算星尘 +10%（与节点 31 叠加）",
            "开局额外携带 1 件随机遗物（与节点 25 叠加）",
            "深渊印记获取 +10%（与节点 27 叠加）",
            "永久伤害 +4%（与节点 5 叠加）",
            "", "", "", "", "", "",
            "", "", "", "", "", "",
            "", "", "", "", "", ""
    };

    static {
        // 名录自检：任何一张表长度不符都直接暴露，而不是在 UI 里越界崩溃。
        if (NAMES.length != COUNT || DESC.length != COUNT || TIER.length != COUNT
                || TARGET.length != COUNT || REWARD_NAME.length != COUNT
                || REWARD_DESC.length != COUNT) {
            throw new IllegalStateException("Achievements tables must have " + COUNT + " entries");
        }
        for (int i = 0; i < COUNT; i++) if (TARGET[i] < 1) TARGET[i] = 1;
        // 名称与描述必须成对出现：只有名字没有说明，玩家看不懂解锁了什么。
        for (int i = 0; i < COUNT; i++) {
            if (REWARD_NAME[i] == null) REWARD_NAME[i] = "";
            if (REWARD_DESC[i] == null) REWARD_DESC[i] = "";
            if (REWARD_NAME[i].isEmpty() != REWARD_DESC[i].isEmpty()) {
                throw new IllegalStateException(
                        "Achievements reward name/desc mismatch at " + i);
            }
        }
    }

    // ── 读取 ──────────────────────────────────────────────────────

    static boolean valid() {
        return NAMES.length == COUNT && DESC.length == COUNT
                && TIER.length == COUNT && TARGET.length == COUNT
                && rewardTablesValid();
    }

    /** 奖励表自检：长度对齐 + 名称/描述成对（空串=无奖励）。 */
    static boolean rewardTablesValid() {
        if (REWARD_NAME == null || REWARD_DESC == null) return false;
        if (REWARD_NAME.length != COUNT || REWARD_DESC.length != COUNT) return false;
        for (int i = 0; i < COUNT; i++) {
            String n = REWARD_NAME[i] == null ? "" : REWARD_NAME[i];
            String d = REWARD_DESC[i] == null ? "" : REWARD_DESC[i];
            if (n.isEmpty() != d.isEmpty()) return false;
        }
        // 前 REWARDED 个必须都有奖励：这 6 个是新手路线图，不允许被清空。
        for (int i = 0; i < REWARDED && i < COUNT; i++) {
            if (rewardName(i).isEmpty() || rewardDesc(i).isEmpty()) return false;
        }
        return true;
    }

    static boolean complete(Meta m, int i) {
        return m != null && i >= 0 && i < COUNT && progress(m, i) >= TARGET[i];
    }

    /** 当前进度（永远在 0..TARGET 之间）。 */
    static int progress(Meta m, int i) {
        if (m == null || i < 0 || i >= COUNT || m.achievements == null || i >= m.achievements.length)
            return 0;
        int value = m.achievements[i];
        if (value < 0) return 0;
        return Math.min(value, TARGET[i]);
    }

    static String progressText(Meta m, int i) {
        if (m == null || i < 0 || i >= COUNT) return "";
        int target = TARGET[i];
        int value = progress(m, i);
        if (value >= target) return "已完成";
        if (target <= 1) return "未完成";
        return value + " / " + target;
    }

    /** 已完成数量。 */
    static int completed(Meta m) {
        int n = 0;
        for (int i = 0; i < COUNT; i++) if (complete(m, i)) n++;
        return n;
    }

    /** 0..1，用于图鉴/成就页进度条。 */
    static float completion(Meta m) {
        return completed(m) / (float) COUNT;
    }

    static String name(int i) {
        return i >= 0 && i < COUNT ? NAMES[i] : "";
    }

    static String desc(int i) {
        return i >= 0 && i < COUNT ? DESC[i] : "";
    }

    /** 奖励名；越界或无奖励返回空串（UI 用空串判断"不画奖励行"）。 */
    public static String rewardName(int i) {
        if (i < 0 || i >= COUNT || REWARD_NAME == null || i >= REWARD_NAME.length) return "";
        String s = REWARD_NAME[i];
        return s == null ? "" : s;
    }

    /** 奖励说明；越界或无奖励返回空串。 */
    public static String rewardDesc(int i) {
        if (i < 0 || i >= COUNT || REWARD_DESC == null || i >= REWARD_DESC.length) return "";
        String s = REWARD_DESC[i];
        return s == null ? "" : s;
    }

    /** 该成就是否配置了奖励（与"是否已完成"无关）。 */
    public static boolean hasReward(int i) {
        return !rewardName(i).isEmpty();
    }

    static int tier(int i) {
        return i >= 0 && i < COUNT ? TIER[i] : 0;
    }

    static int target(int i) {
        return i >= 0 && i < COUNT ? TARGET[i] : 1;
    }

    static String tierName(int i) {
        int t = tier(i);
        return TIER_NAMES[Math.max(0, Math.min(TIER_NAMES.length - 1, t))];
    }

    static int tierColor(int i) {
        int t = tier(i);
        return TIER_COLOR[Math.max(0, Math.min(TIER_COLOR.length - 1, t))];
    }
}
