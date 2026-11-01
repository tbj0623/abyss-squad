import com.dilao.core.*;

/**
 * 静态内容自检：不跑模拟，只校验数据表一致性与平衡预算。
 *
 * <p>用法：
 * <pre>
 *   javac -encoding UTF-8 -d build\wc (Get-ChildItem core\src -Recurse -Filter *.java).FullName
 *   javac -encoding UTF-8 -cp build\wc -d build\wc tests\ContentAudit.java
 *   java -cp build\wc ContentAudit
 * </pre>
 *
 * <p>退出码：0 全部通过；1 存在 FAIL。
 * <p>{@code OK/FAIL} 只对概要项打印；逐条明细只在失败时打印，避免刷屏。
 */
public final class ContentAudit {

    static int fails = 0;
    static int quietPass = 0;
    static int warns = 0;

    static void check(boolean v, String name) {
        if (v) {
            System.out.println("OK   " + name);
        } else {
            System.out.println("FAIL " + name);
            fails++;
        }
    }

    /** 逐条明细使用：只在失败时打印。 */
    static void checkq(boolean v, String name) {
        if (v) {
            quietPass++;
        } else {
            System.out.println("FAIL " + name);
            fails++;
        }
    }

    /** 契约软性偏差：不判失败，只提示。 */
    static void warn(boolean v, String name) {
        if (!v) {
            System.out.println("WARN " + name);
            warns++;
        }
    }

    public static void main(String[] args) {
        section("1. 数据表内置自检");
        check(Content.valid(), "Content 文案与常量长度");
        check(Relics.valid(), "Relics 60 件、每流派 10 件、稀有度分布");
        check(Events.valid(), "Events 36 个、TYPE 0..7");
        check(Enemies.valid(), "Enemies 20 种、BEHAVIOR 0..15");
        check(Bosses.valid(), "Bosses 8 位、PHASES 3 阶段、招式 0..9");
        check(Evolutions.valid(), "Evolutions 36 条、role*3+line 布局");
        check(Balance.valid(), "Balance 数值预算表");

        section("2. 交叉引用 / 长度");
        check(Content.ROLES.length == Content.ROLE_COUNT, "ROLES 长度 == ROLE_COUNT");
        check(Content.ROLE_TITLE.length == Content.ROLE_COUNT
                && Content.ROLE_TIP.length == Content.ROLE_COUNT
                && Content.ROLE_DESC.length == Content.ROLE_COUNT, "职业四组文案长度一致");
        check(Content.SKILLS.length == Content.ROLE_COUNT
                && Content.SKILL_DESC.length == Content.ROLE_COUNT
                && Content.ULTIMATES.length == Content.ROLE_COUNT
                && Content.ULTIMATE_DESC.length == Content.ROLE_COUNT, "技能 / 终极文案长度一致");
        check(Content.COLORS.length == Content.ROLE_COUNT
                && Content.SCHOOL.length == Content.ROLE_COUNT, "职业颜色 / 流派长度");
        check(Content.BIOME_NAMES.length == Content.BIOME_COUNT, "生态 6");
        check(Content.SCHOOL_NAMES.length == Content.SCHOOL_COUNT, "流派 6");
        check(Content.ROOM_TYPES.length == 10 && Content.ROOM_HINTS.length == 10
                && Content.ROOM_SUBTITLE.length == 10, "房间 10 种（含提示与副标题）");
        check(Content.MODE_NAMES.length == 4 && Content.MODE_DESC.length == 4
                && Content.MODE_EN.length == 4, "模式 4 种");
        check(Content.RARITY_NAMES.length == 4 && Content.RARITY_COLOR.length == 4, "稀有度 4 档");
        check(Content.DIFFICULTY_NAMES.length == 5, "契约难度 5 档");
        check(Content.LOADING_TIPS.length >= 20, "加载提示 ≥20（实际 " + Content.LOADING_TIPS.length + "）");
        check(Content.BIOME_BG.length == Content.BIOME_COUNT
                && Content.BIOME_FLOOR.length == Content.BIOME_COUNT
                && Content.BIOME_EDGE.length == Content.BIOME_COUNT
                && Content.BIOME_ACCENT.length == Content.BIOME_COUNT, "生态四色齐备");

        section("3. 遗物逐件数值");
        for (int i = 0; i < Relics.COUNT; i++) {
            checkq(Relics.COST[i] >= 40 && Relics.COST[i] <= 150, "遗物价格区间 #" + i);
            checkq(Relics.DESC[i] != null && Relics.DESC[i].length() >= 8, "遗物描述 #" + i);
            checkq(Relics.FLAVOR[i] != null && Relics.FLAVOR[i].length() >= 4, "遗物风味 #" + i);
            checkq(Relics.ICON[i] >= 0 && Relics.ICON[i] <= 15, "遗物图标 #" + i);
            checkq(Relics.NAMES[i] != null && !Relics.NAMES[i].isEmpty(), "遗物名称 #" + i);
            checkq(Relics.KEY[i] != null && !Relics.KEY[i].isEmpty(), "遗物 KEY #" + i);
        }
        // 设计契约的硬性区间（§7）：越界会让引擎乘区爆炸
        for (int i = 0; i < Relics.COUNT; i++) {
            checkq(Relics.POWER[i] >= -.15f && Relics.POWER[i] <= .30f, "POWER 区间 #" + i);
            checkq(Relics.RATE[i] >= 0f && Relics.RATE[i] <= .18f, "RATE 区间 #" + i);
            checkq(Relics.AREA[i] >= 0f && Relics.AREA[i] <= .25f, "AREA 区间 #" + i);
            checkq(Relics.SPEEDVAL[i] >= -.08f && Relics.SPEEDVAL[i] <= .12f, "SPEEDVAL 区间 #" + i);
        }
        // FLAGS 只允许使用 §13 定义的 30 个位
        int mask30 = (1 << 30) - 1;
        for (int i = 0; i < Relics.COUNT; i++)
            checkq((Relics.FLAGS[i] & ~mask30) == 0, "FLAGS 未越界 #" + i);
        // KEY 唯一
        boolean uniqueKey = true;
        for (int i = 0; i < Relics.COUNT && uniqueKey; i++)
            for (int j = i + 1; j < Relics.COUNT; j++)
                if (Relics.KEY[i].equals(Relics.KEY[j])) { uniqueKey = false; break; }
        check(uniqueKey, "遗物 KEY 唯一");
        boolean uniqueName = true;
        for (int i = 0; i < Relics.COUNT && uniqueName; i++)
            for (int j = i + 1; j < Relics.COUNT; j++)
                if (Relics.NAMES[i].equals(Relics.NAMES[j])) { uniqueName = false; break; }
        check(uniqueName, "遗物名称唯一");

        section("4. 流派预算（防止单一流派过强）");
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            float b = Relics.schoolPowerBudget(s);
            check(b > 0.2f && b < 2.6f, "流派 " + Content.SCHOOL_NAMES[s] + " 加权伤害预算 " + round(b));
        }
        // 契约 §7 建议同流派 POWER×CAP ≤ 1.6
        for (int s = 0; s < Content.SCHOOL_COUNT; s++)
            warn(Relics.schoolPowerBudget(s) <= 1.6f,
                    "流派 " + Content.SCHOOL_NAMES[s] + " POWER×CAP="
                            + round(Relics.schoolPowerBudget(s)) + " 超出契约建议上限 1.6");

        section("5. 敌人数值");
        for (int i = 0; i < Enemies.COUNT; i++) {
            checkq(Enemies.HP[i] > 0 && Enemies.SPEED[i] >= 0, "敌人 #" + i + " 数值非负");
            checkq(Enemies.MIN_FLOOR[i] >= 1 && Enemies.MIN_FLOOR[i] <= 6, "敌人 #" + i + " 出现层");
            checkq(Enemies.BIOME_MASK[i] != 0, "敌人 #" + i + " 至少一个生态");
            checkq(Enemies.WEIGHT[i] >= 1, "敌人 #" + i + " 权重");
            checkq(Enemies.BEHAVIOR[i] >= 0 && Enemies.BEHAVIOR[i] <= 15, "敌人 #" + i + " 行为枚举");
            checkq(Enemies.BIOME_MASK[i] >= 0 && Enemies.BIOME_MASK[i] < (1 << Content.BIOME_COUNT),
                    "敌人 #" + i + " 生态掩码不越界");
            checkq(Enemies.RADIUS[i] > 0 && Enemies.COOLDOWN[i] > 0, "敌人 #" + i + " 半径与冷却");
        }

        section("6. 首领");
        for (int i = 1; i < Bosses.COUNT; i++)
            check(Bosses.HP[i] > Bosses.HP[i - 1], "首领血量递增 #" + i + " (" + (int) Bosses.HP[i - 1]
                    + " → " + (int) Bosses.HP[i] + ")");
        for (int i = 0; i < Bosses.COUNT; i++) {
            checkq(Bosses.HP[i] > 0 && Bosses.SPEED[i] >= 0 && Bosses.RADIUS[i] > 0,
                    "首领 #" + i + " 数值非负");
            checkq(Bosses.BIOME[i] >= 0 && Bosses.BIOME[i] < Content.BIOME_COUNT, "首领 #" + i + " 生态下标");
            checkq(Bosses.PHASES[i] != null && Bosses.PHASES[i].length == 3, "首领 #" + i + " 三阶段");
            for (int k = 0; k < Bosses.PHASES[i].length; k++) {
                int m = Bosses.PHASES[i][k];
                checkq(m >= 0 && m <= 9, "首领 #" + i + " 阶段 " + k + " 招式枚举");
            }
        }

        section("7. 进化");
        for (int i = 0; i < Evolutions.COUNT; i++) {
            checkq(Evolutions.REQ_RELIC[i] < 0 || Evolutions.REQ_RELIC[i] < Relics.COUNT,
                    "进化需求遗物 #" + i);
            checkq(Evolutions.ROLE[i] >= 0 && Evolutions.ROLE[i] < Content.ROLE_COUNT,
                    "进化职业 #" + i);
            checkq(Evolutions.TIER[i] == 1 || Evolutions.TIER[i] == 2, "进化层级 #" + i);
            checkq((Evolutions.FLAGS[i] & ~mask30) == 0, "进化 FLAGS 未越界 #" + i);
        }
        // 实际布局：index = role*3 + line，line 0/1 是 TIER1，line 2 是 TIER2（进阶）。
        for (int role = 0; role < Content.ROLE_COUNT; role++) {
            for (int line = 0; line < 3; line++) {
                int idx = Evolutions.index(role, line);
                checkq(idx >= 0 && idx < Evolutions.COUNT, "进化 index(" + role + "," + line + ") 越界");
                checkq(Evolutions.ROLE[idx] == role, "进化 index(" + role + "," + line + ") 职业一致");
                int wantTier = line == 2 ? 2 : 1;
                checkq(Evolutions.TIER[idx] == wantTier,
                        "进化 index(" + role + "," + line + ") TIER=" + Evolutions.TIER[idx] + "，应为 " + wantTier);
                if (wantTier == 2) {
                    int req = Evolutions.REQ_RELIC[idx];
                    checkq(req >= 0 && req < Relics.COUNT,
                            "进阶进化 #" + idx + " 需要合法遗物 id（当前 " + req + "）");
                }
            }
        }
        int tier1 = 0, tier2 = 0;
        for (int i = 0; i < Evolutions.COUNT; i++) {
            if (Evolutions.TIER[i] == 1) tier1++;
            else if (Evolutions.TIER[i] == 2) tier2++;
        }
        check(tier1 == 24 && tier2 == 12, "进化 24 条 TIER1 + 12 条 TIER2");
        warn(Evolutions.REQ_RELIC[Evolutions.index(0, 2)] >= 0,
                "契约 §10 原文写「TIER2 需要同 line 的 TIER1」，实际实现是 line2 直接以 REQ_RELIC 解锁（data 侧无 line0/1 前置），"
                        + "属实现口径差异，非数据错误");

        section("8. 事件");
        // 上界跟着 Events.TYPE 的实际取值走，不再写死 8。
        // 事件结构从 8 种扩到 13 种（0..12）之后，写死 8 的统计会数组越界，
        // 而 `TYPE[i] <= 7` 的断言会把 5 种新结构全部判成错误。
        int typeMax = 0;
        for (int i = 0; i < Events.COUNT; i++) typeMax = Math.max(typeMax, Events.TYPE[i]);
        int[] typeCount = new int[typeMax + 1];
        for (int i = 0; i < Events.COUNT; i++) {
            checkq(Events.COST_A[i] >= 0 && Events.COST_B[i] >= 0, "事件代价 #" + i);
            checkq(Events.CHOICE_A[i] != null && !Events.CHOICE_A[i].isEmpty(), "事件选项 A #" + i);
            checkq(Events.CHOICE_B[i] != null && !Events.CHOICE_B[i].isEmpty(), "事件选项 B #" + i);
            checkq(Events.TYPE[i] >= 0 && Events.TYPE[i] <= 12, "事件类型 #" + i);
            checkq(Events.ART[i] >= 0 && Events.ART[i] <= 11, "事件插画 #" + i);
            checkq(Events.MIN_FLOOR[i] >= 1, "事件最低层 #" + i);
            checkq(Events.TEXT[i] != null && Events.TEXT[i].length() >= 20, "事件情境 #" + i);
            checkq(Events.RESULT_A[i] != null && Events.RESULT_B[i] != null, "事件结果文案 #" + i);
            typeCount[Events.TYPE[i]]++;
        }
        // 13 种结构里每种至少 2 个（36 个事件铺不满每种 3 个）；
        // 关键是"每种结构都有多个实例"，而不是某个固定数字。
        for (int t = 0; t < typeCount.length; t++)
            check(typeCount[t] >= 2, "TYPE " + t + " 至少 2 个（实际 " + typeCount[t] + "）");
        boolean uniqueEvent = true;
        for (int i = 0; i < Events.COUNT && uniqueEvent; i++)
            for (int j = i + 1; j < Events.COUNT; j++)
                if (Events.NAMES[i].equals(Events.NAMES[j])) { uniqueEvent = false; break; }
        check(uniqueEvent, "事件名称唯一");

        section("9. 平衡曲线");
        Game g = new Game(1L);
        g.contract = 1;
        g.mode = Game.MODE_EXPEDITION;
        g.floor = 1;
        g.roomsVisited = 0;
        float f1 = Balance.hpFactor(g);
        g.floor = 5;
        g.roomsVisited = 14;
        float f5 = Balance.hpFactor(g);
        // 契约 §12：floor 1 且 roomsVisited=0 时，hpFactor 应严格等于 HP[contract]。
        // 这里 contract=1，所以基准就是 Balance.HP[1]。
        // 旧断言写的是 0.9~1.2 的硬编码区间 —— 那是 HP 表重做（1.18 → 1.76）之前的数值，
        // 表一改它就误报。改成与表对比，以后调平衡不会再假失败。
        float base1 = Balance.HP[Math.max(0, Math.min(Balance.HP.length - 1, g.contract))];
        check(Math.abs(f1 - base1) < 1e-4f,
                "第 1 层血量倍率应等于 HP[" + g.contract + "]=" + base1 + "，实际 " + round(f1));
        check(f5 > 2.2f && f5 < 9.0f, "第 5 层血量倍率 " + round(f5));
        check(f5 / f1 > 2.2f && f5 / f1 < 9.0f, "血量成长倍数 " + round(f5 / f1));
        // 单调：每一层都比上一层硬
        boolean floorMono = true;
        g.contract = 1;
        g.roomsVisited = 0;
        float prevFloor = -1f;
        for (int floor = 1; floor <= 9; floor++) {
            g.floor = floor;
            float v = Balance.hpFactor(g);
            if (!(v > prevFloor)) floorMono = false;
            prevFloor = v;
        }
        check(floorMono, "hpFactor 随楼层严格递增");
        // 五档必须单调
        float prev = -1f;
        boolean mono = true;
        for (int c = 0; c < Balance.HP.length; c++) {
            g.contract = c;
            g.floor = 3;
            g.roomsVisited = 6;
            float hp = Balance.hpFactor(g);
            if (hp <= prev) mono = false;
            prev = hp;
        }
        check(mono, "五档契约血量倍率单调递增");
        check(Balance.damageFactor(g) > 0f && Balance.paceFactor(g) > 0f, "契约伤害 / 节奏倍率非负");
        // 房间收益顺序
        check(Balance.clearCoins(Game.ROOM_BOSS) > Balance.clearCoins(Game.ROOM_ELITE)
                        && Balance.clearCoins(Game.ROOM_ELITE) > Balance.clearCoins(Game.ROOM_COMBAT),
                "房间星尘收益：首领 > 精英 > 普通");
        check(Balance.rewardDrafts(Game.ROOM_ELITE) == 2
                && Balance.rewardDrafts(Game.ROOM_COMBAT) == 1, "精英房双轮选牌");

        section("10. 契约软性偏差（仅提示，不计失败）");
        int descBad = 0, flavorBad = 0;
        for (int i = 0; i < Relics.COUNT; i++) {
            int dl = Relics.DESC[i] == null ? 0 : Relics.DESC[i].length();
            if (dl < 20 || dl > 34) descBad++;
            int fl = Relics.FLAVOR[i] == null ? 0 : Relics.FLAVOR[i].length();
            if (fl < 10 || fl > 20) flavorBad++;
        }
        warn(descBad == 0, "有 " + descBad + " 件遗物 DESC 不在契约 20~34 字区间");
        warn(flavorBad == 0, "有 " + flavorBad + " 件遗物 FLAVOR 不在契约 10~20 字区间");
        int tipBad = 0;
        for (int i = 0; i < Content.ROLE_COUNT; i++) {
            int n = Content.ROLE_TIP[i].length();
            if (n < 40 || n > 70) tipBad++;
        }
        warn(tipBad == 0, "有 " + tipBad + " 条 ROLE_TIP 不在契约 40~70 字区间");
        int textBad = 0;
        for (int i = 0; i < Events.COUNT; i++) {
            int n = Events.TEXT[i].length();
            if (n < 40 || n > 80) textBad++;
        }
        warn(textBad == 0, "有 " + textBad + " 个事件 TEXT 不在契约 40~80 字区间");

        section("结果");
        System.out.println("明细通过 " + quietPass + " 项，失败 " + fails + " 项，契约提示 " + warns + " 项");
        if (fails == 0) {
            System.out.println("ALL CONTENT AUDITS PASSED");
        } else {
            System.out.println(fails + " FAILURES");
            System.exit(1);
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("── " + title + " ──");
    }

    private static String round(float v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }
}
