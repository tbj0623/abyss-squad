package com.dilao.core;

/**
 * 20 种敌人。BEHAVIOR 决定 AI 的<b>基础</b>分支（见 {@link Game} 的 B_* 常量），
 * {@link #VARIANT} 决定同一基础分支下的<b>子行为</b>。
 *
 * <p><b>为什么要 VARIANT</b>：{@code BEHAVIOR} 只有 0..15 共 16 个槽位，却有 20 种敌人；
 * 更关键的是 {@code Codec} 的 {@code range(e.behavior, 0, 15)} 是存档契约（本次不可改），
 * 所以新增行为值会直接让读档失败。把"子行为"放进<b>本来就会序列化</b>的
 * {@code Enemy.variant} 字段，既能让 20 种敌人各有独立 AI，又不破坏存档格式。
 * 约定：{@code (BEHAVIOR, VARIANT)} 两两不同，且 variant=1 的行为必须在
 * {@code Game} 的 {@code canAttack / startTelegraph / doEnemyAttack / moveEnemyAI}
 * 四处分派里都有专属分支（探针 {@code ContentGapProbe} 会逐条验证）。
 */
public final class Enemies {
    private Enemies() {}

    public static final int COUNT = 20;

    public static final String[] NAMES = {
            "幽狼", "孢子射手", "冲角兽", "爆裂菇", "裂殖体",
            "织巢蛛", "灰烬祭司", "骨弩手", "铁卫", "噬星虫",
            "影跃者", "霜愈使", "岩盾兵", "涡心", "哨戒炮",
            "虚翼", "熔壳巨兽", "冰晶守卫", "雷暴使", "深渊之眼"
    };

    public static final String[] DESC = {
            "会绕着你侧移，等你露出侧背再扑上来。用走位把它带进你的弹道。",
            "远远吊着放三连射。必须优先打断，否则弹幕会越叠越密。",
            "蓄力后直线冲撞，落点有预警。侧移一步就能躲，硬接很痛。",
            "贴到身边就自爆，范围伤害。别让它靠太近。",
            "死亡后分裂成两只幼体。要留一手清残余。",
            "定期召出虫群，本身很脆但很烦。优先击杀。",
            "在玩家脚下投放延时火圈。保持移动就能化解。",
            "远距离蓄力单发高伤，预警很长。看到红线就绕开。",
            "高血低速，正面冲撞会把你顶退。绕后或用穿透打。",
            "体积小、速度极快、成群出现。范围伤害是唯一解。",
            "瞬移到你身边再攻击，落地有短暂硬直。",
            "给周围敌人回血加护盾。不处理会让整波变硬。",
            "正面免伤，必须绕到侧后打。移动到它背后。",
            "旋转弹幕，越靠近越密。拉开距离绕圈。",
            "不移动，持续射击。找到掩体或者直接冲上去拆掉。",
            "无视障碍直线飞来，速度快但血少。",
            "熔岩外壳会自行再生，被贴身就震地。先磨掉壳再输出。",
            "正面免伤，碎裂后炸成两块冰晶。先绕后，再清碎冰。",
            "长预警后雷电冲刺，落点留下雷池。看到紫线就横移。",
            "凝视蓄力后射出贯穿光束，被贴脸会瞬移拉开。别站直线。"
    };

    /** 0 追击 1 射手 2 冲锋 3 自爆 4 分裂 5 召唤 6 区域 7 狙击
     *  8 坦克 9 虫群 10 闪现 11 治疗 12 持盾 13 螺旋 14 炮台 15 飞行
     *  <p>本表必须保持在 0..15：{@code Codec} 的读档校验与 CoreTests 的枚举断言都依赖它。 */
    public static final int[] BEHAVIOR = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 8, 12, 2, 7
    };

    /**
     * 子行为。0 = 基础型；1 = 该基础分支下的特殊型。
     *
     * <p>{@code (BEHAVIOR, VARIANT)} 组合必须两两不同，共 20 种：
     * <pre>
     *   16 熔壳巨兽 8+1  熔壳：护甲自行再生，贴身震地（对比 8 铁卫：纯高血 + 击退）
     *   17 冰晶守卫 12+1 冰晶：死亡碎裂成两块，攻击是冰环（对比 12 岩盾兵：正面免伤 + 五连扇）
     *   18 雷暴使   2+1  雷冲：1.7s 长预警冲刺 + 落点雷池（对比 2 冲角兽：0.9s 快冲）
     *   19 深渊之眼 7+1  凝视：长蓄力贯穿光束 + 被贴脸瞬移（对比 7 骨弩手：短预警单发）
     * </pre>
     */
    public static final int[] VARIANT = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 1, 1, 1, 1
    };

    /** 图集行号：敌人占 12..31（行 0..11 是职业，32..39 是首领）。 */
    public static final int[] ART = {
            12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31
    };

    public static final float[] HP = {
            38, 34, 52, 30, 46, 44, 40, 36, 120, 18,
            42, 56, 96, 70, 64, 32, 180, 110, 76, 260
    };

    public static final float[] SPEED = {
            118, 74, 96, 104, 92, 82, 70, 66, 54, 142,
            130, 60, 50, 0, 0, 156, 44, 40, 88, 30
    };

    public static final float[] RADIUS = {
            17, 15, 20, 14, 18, 16, 15, 14, 25, 10,
            15, 17, 23, 19, 18, 14, 30, 26, 18, 34
    };

    public static final float[] COOLDOWN = {
            1.6f, 2.2f, 3.0f, 1.2f, 2.0f, 4.0f, 2.8f, 3.4f, 2.2f, 1.8f,
            2.4f, 3.6f, 2.6f, 2.0f, 1.5f, 1.9f, 2.6f, 2.4f, 3.2f, 2.6f
    };

    public static final float[] TELEGRAPH = {
            0.5f, 0.72f, 0.9f, 0.4f, 0.6f, 0.7f, 0.85f, 1.1f, 0.8f, 0.35f,
            0.55f, 0.7f, 0.75f, 0.8f, 0.6f, 0.5f, 0.9f, 0.8f, 1.0f, 0.95f
    };

    /**
     * 预警时长下限（秒）——任何契约档、任何敌人都不得低于它。
     *
     * <p><b>为什么是 0.45 s（反应时长的生理依据）</b>：
     * <ul>
     *   <li>NN/g 的「0.1 / 1 / 10 秒」三档指出，0.1 秒是"瞬时"、人来不及形成决策；
     *       视觉刺激 → 运动皮层指令的可执行下限在 <b>0.25–0.40 s</b> 之间，
     *       0.45 s 是"看见 + 认出 + 手指动"还能连成一个动作的保守值。</li>
     *   <li>{@link #TELEGRAPH} 的最小值是 0.35 s（type 9 噬星虫），
     *       而 {@code Balance.PACE} 最高 1.82——同一条 0.35 s 的预告在炼狱档里
     *       会以 1.82 倍的频率反复出现，同时敌方弹丸也以 1.82 倍速度飞行。
     *       频率 × 速度一起压缩，玩家的<b>有效</b>决策窗口远小于表面数字。</li>
     *   <li>所以下限不是削弱数值，而是把"炼狱难在反应不过来"改成
     *       "炼狱难在要同时处理三件事"——难度来自数量与组合，不来自不可读。</li>
     * </ul>
     * 表级审计通过 {@link #telegraphSeconds(int, float)} 验证各契约的节奏下限；
     * 实战 {@code Game.startTelegraph} 在行为放大和精英缩短之后再次钳住本常量。
     */
    public static final float TELEGRAPH_FLOOR = .45f;

    /**
     * 某类敌人在给定节奏倍数下的<b>可用</b>预警时长（秒）。
     *
     * <p>公式：{@code max(TELEGRAPH_FLOOR, TELEGRAPH[type] / max(.01f, pace))}。
     * <ul>
     *   <li>{@code pace} 传 {@code Balance.PACE[contract]}；0 / 负数 / NaN 一律按 0.01 处理，
     *       避免出现 Infinity 预警（那等于永远不出手）。</li>
     *   <li>这是数据表审计辅助函数；实战攻击循环故意不将蓄力除以 PACE，
     *       PACE 只加快冷却和弹速。{@code Game.startTelegraph} 先叠加行为与精英倍率，
     *       最后直接对 TELEGRAPH_FLOOR 钳制，以免叠加倍率再次压回不可反应窗口。</li>
     *   <li>越界 type 直接返回下限：这是数据错误，宁可给出最保守的可反应值，也不要抛异常打断战斗。</li>
     * </ul>
     */
    public static float telegraphSeconds(int type, float pace) {
        if (type < 0 || type >= COUNT) return TELEGRAPH_FLOOR;
        float p = Float.isFinite(pace) ? Math.max(.01f, pace) : .01f;
        return Math.max(TELEGRAPH_FLOOR, TELEGRAPH[type] / p);
    }

    public static final int[] XP = {
            14, 12, 18, 11, 16, 17, 15, 13, 32, 6,
            15, 20, 28, 22, 20, 11, 46, 30, 24, 70
    };

    public static final int[] COINS = {
            2, 2, 3, 1, 2, 3, 2, 2, 4, 0,
            2, 3, 4, 3, 3, 1, 6, 5, 4, 9
    };

    /**
     * 出现层。远征最高 floor = 5（第 15 站），所以任何 MIN_FLOOR ≥6 的敌人在主线永远不刷。
     * 深渊之眼（19）原为 6 —— 这是审计里"Enemy.type 19 unreachable"的直接原因，现改为 5。
     */
    public static final int[] MIN_FLOOR = {
            1, 1, 1, 1, 1, 1, 2, 2, 3, 1,
            2, 3, 3, 3, 4, 3, 5, 4, 4, 5
    };

    public static final int[] WEIGHT = {
            10, 8, 7, 6, 6, 5, 6, 6, 4, 8,
            5, 4, 4, 4, 3, 4, 2, 3, 3, 1
    };

    /** 位掩码：1<<biome。 */
    public static final int[] BIOME_MASK = {
            (1 << 0) | (1 << 3), (1 << 0) | (1 << 2), (1 << 1) | (1 << 4),
            (1 << 0) | (1 << 1), (1 << 2) | (1 << 5), (1 << 0) | (1 << 2),
            (1 << 1) | (1 << 4), (1 << 2) | (1 << 3), (1 << 1) | (1 << 4) | (1 << 5),
            (1 << 0) | (1 << 3) | (1 << 5), (1 << 2) | (1 << 5), (1 << 3) | (1 << 0),
            (1 << 1) | (1 << 4), (1 << 2) | (1 << 5), (1 << 1) | (1 << 3),
            (1 << 5) | (1 << 2), (1 << 4) | (1 << 1), (1 << 3), (1 << 4) | (1 << 2),
            (1 << 4) | (1 << 5)          // 19 深渊之眼：深渊 + 虚空王座
    };

    /** 该敌人是否有非基础子行为。 */
    public static boolean variant(int type) {
        return type >= 0 && type < COUNT && VARIANT[type] != 0;
    }

    public static boolean valid() {
        int[] lens = {NAMES.length, DESC.length, BEHAVIOR.length, VARIANT.length, ART.length,
                HP.length, SPEED.length, RADIUS.length, COOLDOWN.length, TELEGRAPH.length,
                XP.length, COINS.length, MIN_FLOOR.length, WEIGHT.length, BIOME_MASK.length};
        for (int n : lens) if (n != COUNT) return false;
        for (int b : BEHAVIOR) if (b < 0 || b > 15) return false;
        for (int v : VARIANT) if (v < 0 || v > 3) return false;
        // 20 种敌人必须 (BEHAVIOR, VARIANT) 两两不同，否则就是换皮的 reskin。
        for (int i = 0; i < COUNT; i++)
            for (int j = i + 1; j < COUNT; j++)
                if (BEHAVIOR[i] == BEHAVIOR[j] && VARIANT[i] == VARIANT[j]) return false;
        // 数值必须为正（速度允许 0 只给炮台/涡心这类站桩单位，但其余不可为负）。
        for (int i = 0; i < COUNT; i++) {
            if (!(HP[i] > 0) || SPEED[i] < 0 || !(RADIUS[i] > 0) || !(COOLDOWN[i] > 0)
                    || !(TELEGRAPH[i] > 0) || XP[i] < 0 || COINS[i] < 0) return false;
            if (MIN_FLOOR[i] < 1 || MIN_FLOOR[i] > 5) return false;
            if (BIOME_MASK[i] == 0 || (BIOME_MASK[i] & ~0x3f) != 0) return false;
            if (WEIGHT[i] < 0) return false;
        }
        // ── v2.2 预警下限（research-best-in-class §2.5）─────────────────
        // 全组合断言：任意敌人 × 任意契约节奏，实际窗口都必须 ≥ TELEGRAPH_FLOOR。
        // 这是"可反应性"的硬契约——不是平衡问题，而是生理下限问题，
        // 所以它和长度/正值一起写进 valid()，而不是留给运行时靠手感发现。
        if (!(TELEGRAPH_FLOOR >= .40f && TELEGRAPH_FLOOR <= .60f)) return false;
        // 函数形状自检（防止有人把 max() 写成 min() 之类的退化）：
        //   ① 任何输入都不得低于下限；② 下限以上的区间必须随 pace 单调不增；
        //   ③ pace 退化（0 / 负数 / 极小）不得产出 Infinity。
        for (int i = 0; i < COUNT; i++) {
            float prev = Float.MAX_VALUE;
            for (int c = 0; c < Balance.PACE.length; c++) {
                float t = telegraphSeconds(i, Balance.PACE[c]);
                if (!Float.isFinite(t) || t < TELEGRAPH_FLOOR) return false;
                if (t > prev + 1e-5f) return false;      // pace 越大，窗口不得变长
                prev = t;
            }
            if (!Float.isFinite(telegraphSeconds(i, 0f))) return false;
            if (telegraphSeconds(i, -3f) < TELEGRAPH_FLOOR) return false;
            // 结算口径：Game 只做下限夹取（不除 PACE），所以有效预警 = max(下限, 表值 × 行为放大)。
            // 这里断言"表值本身不必高于下限"，即下限确实是唯一的兜底，而不是把整张表抬到 0.45。
            if (TELEGRAPH[i] > TELEGRAPH_FLOOR && telegraphSeconds(i, 1f) < TELEGRAPH[i]) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(valid() ? "Enemies valid: " + COUNT : "Enemies FAILED");
    }
}
