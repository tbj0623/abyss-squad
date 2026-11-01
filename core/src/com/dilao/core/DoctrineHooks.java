package com.dilao.core;

/**
 * 天赋牌（Doctrine）效果钩子常量 —— <b>core 侧独立声明</b>。
 *
 * <p>为什么重复声明：{@code core} 不能依赖 {@code app} 层的 {@code Meta}（不变式 5：
 * 模拟层不引用任何 Android / UI 数据）。{@code Session} 在开局前把「牌号 + 主/副钩子 id +
 * 参数」写进 {@link Game}，core 只按钩子 id 分发，因此本表必须与
 * {@code Meta.H_*} 数值完全一致。{@code tests/DoctrineEffectTest} 会逐项交叉校验。
 *
 * <p>参数约定：主钩子读 {@code doctrineParams[0] / [1]}，副钩子读
 * {@code doctrineParams[2]}；{@code doctrineParams[3]} 预留。
 */
public final class DoctrineHooks {
    private DoctrineHooks() {}

    /** 牌数（与 Meta.DOCTRINE_COUNT 一致；core 只用它做范围校验）。 */
    public static final int COUNT = 18;
    /** 钩子 id 上限（含）。 */
    public static final int MAX = 31;

    public static final int
            H_NONE = 0,
            H_START_RELIC = 1,            // 开局固定 1 件该流派稀有遗物；param=流派 0..5
            H_COINS_ZERO = 2,             // 开局星尘清零
            H_SCHOOL_LOCK = 3,            // 该流派遗物本局不再出现；param=流派 0..5
            H_NO_FLOOR_HEAL = 4,          // 每层结束不再自动回复
            H_REROLL_ZERO = 5,            // 开局重掷次数归零
            H_ALL_SHOTS_SLOW = 6,         // 所有弹丸附带减速；param=秒
            H_SHOT_DAMAGE_MUL = 7,        // 弹丸伤害乘区增量；param=增量（-0.20 = -20%）
            H_START_COINS = 8,            // 开局星尘增量；param=增量
            H_SHOP_PRICE_MUL = 9,         // 商店价格乘区增量；param=增量
            H_MAX_EVOLUTIONS = 10,        // 每局可进化线数；param=条数
            H_EVOLVE_LEVEL_PEN = 11,      // 进化等级门槛增量；param=增量
            H_DASH_COSTS_COIN = 12,       // 闪避消耗星尘而非冷却；param=每次星尘
            H_DASH_ATKSPEED = 13,         // 闪避后攻速加成；param=增量，持续 1.5s
            H_MAXHP_MUL = 14,             // 最大生命乘区；param=乘区（0.75 = -25%）
            H_LEVELUP_EXTRA_CHOICE = 15,  // 每次升级额外三选一次数；param=次数
            H_HURT_FIRE_RING = 16,        // 受伤时自身周围火环；param=半径
            H_TAKEN_DAMAGE_MUL = 17,      // 承受伤害乘区；param=乘区
            H_FLOOR_SHIELD = 18,          // 每层开始护盾；param=最大生命比例
            H_SHIELD_NO_REGEN = 19,       // 护盾不再自动恢复
            H_ENEMY_SPEED_MUL = 20,       // 敌人移速乘区增量；param=增量
            H_ROOM_TIME_MUL = 21,         // 房间限时乘区增量；param=增量
            H_REFLECT = 22,               // 反弹承受伤害；param=比例
            H_REGEN_MUL = 23,             // 回复效果乘区增量；param=增量
            H_KILL_HEAL = 24,             // 每击杀 N 个敌人回复；param=击杀数, param2=回复比例
            H_DAMAGE_MUL = 25,            // 伤害乘区；param=乘区
            H_OVERHEAT = 26,              // 过热：每隔 param 秒强制停顿 0.7s
            H_ATKSPEED_MUL = 27,          // 攻速乘区增量；param=增量
            H_CHAIN_ON_KILL = 28,         // 击杀时连锁闪电；param=伤害比例
            H_CRIT_ZERO = 29,             // 暴击率归零
            H_RELIC_SLOTS = 30,           // 遗物携带上限；param=上限
            H_RELIC_EFFECT_MUL = 31;      // 遗物效果乘区；param=乘区

    /** 是否是合法钩子 id（-1 表示未装备，也视为合法）。 */
    public static boolean valid(int hook) {
        return hook >= -1 && hook <= MAX;
    }

    /** 是否是合法流派下标。 */
    public static boolean validSchool(int school) {
        return school >= 0 && school < Content.SCHOOL_COUNT;
    }
}
