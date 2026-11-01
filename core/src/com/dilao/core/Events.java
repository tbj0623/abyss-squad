package com.dilao.core;

/**
 * 36 个事件。{@link #TYPE} 描述的是<b>选择结构</b>（这一站要求玩家做什么样的取舍），
 * 而不是"效果模板"。同一个 TYPE 里的事件靠 {@link #PARAM_A} / {@link #PARAM_B} / {@link #PARAM_C}
 * 区分量级、概率、时长与结算币种——因此 36 个事件是 36 种决策，不是 13 种换皮。
 *
 * <p>13 种结构：
 * <pre>
 *   0  献血       —— 用当前生命换永久强度（血脉契约遗物自带生命上限代价）
 *   1  献金       —— 用星尘换内容
 *   2  预言       —— 用星尘或生命换信息的折扣（免费重掷）
 *   3  祭坛       —— 血与星尘同时付，换确定数量的遗物
 *   4  医疗       —— 立刻回复，3 站后挂账回收（隐性代价）
 *   5  锻造       —— 强化已有构筑；强化目标由 PARAM_B 决定（依赖当前 build）
 *   6  赌局       —— 单一概率：赢了给遗物，输了掉当前生命
 *   7  诅咒       —— 拿强遗物，背持久负面（唯一允许增加 CURSED 的结构）
 *   8  风险累积   —— 每次选择叠一层「血债」，3 站后一次性还清
 *   9  延迟结算   —— 现在白拿，3 站后按生命或星尘结账
 *   10 二选一赌注 —— A/B 两侧胜率与赔率互补，两侧都有失败概率
 *   11 交换       —— 交出一件已有遗物，换回若干新遗物与星尘
 *   12 反向代价   —— 两个选项都要付血，但"拒绝"付得更多；必须选
 * </pre>
 *
 * <p><b>参数列语义（按 TYPE 分）</b>：
 * <pre>
 *   TYPE  A 主效果                B 副效果                      C 第三参数
 *   0     献出的当前生命比例       血脉契约层数                   拒绝时的星尘
 *   1     遗物件数                 B 扣的当前生命（点）            拒绝时的星尘
 *   2     免费重掷次数             A 额外扣的当前生命比例          拒绝时的星尘
 *   3     献出的当前生命比例       遗物件数                       拒绝时的治疗量
 *   4     立即治疗量               3 站后扣的生命比例              拒绝时的星尘
 *   5     强化层数                目标模式(0最厚/1最贵/2最薄)      拒绝时的星尘
 *   6     胜率                    胜利遗物件数                    失败扣的生命比例
 *   7     遗物件数                拒绝时的治疗量                  —
 *   8     每层血债的生命比例       立即星尘                        拒绝(抽身)时的星尘
 *   9     立即遗物件数             挂账量(生命比例或星尘)           挂账币种(0生命/1星尘)
 *   10    A 胜率                  A 失败的生命比例                B 失败的生命比例
 *   11    交换额外得到的星尘       换回的遗物件数                  拒绝时的星尘
 *   12    A 付的生命比例           A 的遗物件数                    B 付的生命比例
 * </pre>
 *
 * <p><b>⚠️ 硬约束</b>：{@link #TYPE} 中出现的任何值，{@link Game#resolveEvent}
 * 都必须有对应的 {@code case} 分支。<b>两者必须同批修改。</b>
 * {@code tests/EventContractTest.java} 会断言本表不超过 {@link #IMPLEMENTED_TYPE_MAX}，
 * 且只有 TYPE 7 可以产生 {@code Relics.CURSED}——
 * 历史上曾出现「只改 Events.TYPE 没改 resolveEvent」导致 19 个事件静默走错分支的事故。
 *
 * <p><b>代价口径</b>：{@link #COST_A} / {@link #COST_B} 只表示<b>预付星尘</b>。
 * 生命代价、延迟代价、遗物代价由 PARAM 列描述——这三类代价全部是真实的，
 * 且刻意不经过 {@code bonus*} 成长字段（那些字段会被 {@code recompute} 的捕获逻辑衰减，
 * 用它们记代价会导致"加一件遗物就把代价还回来"）。
 */
public final class Events {
    private Events() {}

    /**
     * 玩家做选择前看到的实际收益与风险。与 Game.resolveEvent 的 13 个分支保持一致；
     * COST_* 只代表预付星尘，生命、遗物及延迟代价必须在这里明确列出。
     * 预览不消耗随机数，也不改变游戏状态。
     */
    public static String preview(Game g, int event, int choice) {
        if (event < 0 || event >= COUNT || choice < 0 || choice > 1)
            return "未知事件";
        boolean a = choice == 0;
        int cost = a ? COST_A[event] : COST_B[event];
        String prefix = cost > 0 ? "星尘 -" + cost + " · " : "";
        float pa = PARAM_A[event], pb = PARAM_B[event], pc = PARAM_C[event];
        float meta = g != null && !g.players.isEmpty()
                ? 1f + Math.max(0f, g.players.get(0).metaEvent) : 1f;
        String effect;
        switch (TYPE[event]) {
            case 0:
                effect = a ? "当前生命 -" + pct(pa) + " · 血脉契约 +" + count(pb) + "层"
                        : "星尘 +" + Math.round((int) pc * meta);
                break;
            case 1:
                effect = a ? "遗物 +" + count(pa) + "件"
                        : "生命 -" + (int) pb + "点 · 星尘 +" + Math.round((int) pc * meta);
                break;
            case 2:
                effect = a ? "免费重掷 +" + count(pa)
                        + (pb > 0 ? " · 当前生命 -" + pct(pb) : "")
                        : "星尘 +" + Math.round((int) pc * meta);
                break;
            case 3:
                effect = a ? "当前生命 -" + pct(pa) + " · 遗物 +" + count(pb) + "件"
                        : "生命恢复 " + (int) pc + "点";
                break;
            case 4:
                effect = a ? "生命恢复 " + (int) pa + " · 冷却清零 · 3站后生命 -" + pct(pb)
                        : "星尘 +" + Math.round((int) pc * meta);
                break;
            case 5:
                if (a) {
                    boolean hasRelic = false;
                    if (g != null && !g.players.isEmpty()) {
                        for (int n : g.players.get(0).relics)
                            if (n > 0) { hasRelic = true; break; }
                    }
                    String target = (int) pb == 1 ? "最贵" : (int) pb == 2 ? "最薄" : "最厚";
                    effect = hasRelic ? "强化" + target + "遗物 +" + count(pa) + "层" : "无遗物时伤害 +12%";
                } else effect = "星尘 +" + Math.round((int) pc * meta);
                break;
            case 6:
                effect = a ? "胜率 " + pct(pa) + " · 赢遗物 +" + count(pb)
                        + " · 输最大生命 -" + pct(pc)
                        : "星尘 +" + (count(pb) * 12);
                break;
            case 7:
                effect = a ? "遗物 +" + count(pa) + "件 · 诅咒 +1层"
                        : "生命恢复 " + (int) pb + "点";
                break;
            case 8: {
                int debt = g == null ? 0 : g.eventDebt;
                int next = a ? Math.min(9, debt + 1) : Math.max(0, debt - 1);
                effect = "星尘 +" + Math.round((int) (a ? pb : pc) * meta)
                        + " · 血债 " + (a ? "+1" : (debt > 0 ? "-1" : "不变"))
                        + (next > 0 ? " · 3站后生命 -" + pct(pa * next)
                        : (debt > 0 ? " · 血债已清零" : " · 无血债待还"));
                break;
            }
            case 9:
                effect = a ? "遗物 +" + count(pa) + "件 · 3站后"
                        + ((int) pc == 1 ? "星尘 -" + (int) pb : "生命 -" + pct(pb))
                        : "星尘 +" + Math.max(6, Math.round(pb));
                break;
            case 10:
                effect = a ? "胜率 " + pct(pa) + " · 赢遗物 +1 · 输最大生命 -" + pct(pb)
                        : "胜率 " + pct(Math.max(.05f, 1f - pa))
                        + " · 赢遗物 +2 · 输最大生命 -" + pct(pc);
                break;
            case 11: {
                boolean hasRelic = false;
                if (g != null && !g.players.isEmpty())
                    for (int n : g.players.get(0).relics)
                        if (n > 0) { hasRelic = true; break; }
                effect = a ? (hasRelic ? "失去遗物 1层 · 换回 +" + count(pb) + "件"
                        + ((int) pa > 0 ? " · 星尘 +" + (int) pa : "")
                        : "没有遗物 · 免费获得 1件")
                        : "星尘 +" + Math.round((int) pc * meta);
                break;
            }
            case 12:
                effect = a ? "当前生命 -" + pct(pa) + " · 遗物 +" + count(pb) + "件"
                        : "当前生命 -" + pct(pc) + " · 星尘 +" + Math.round(pc * 100f);
                break;
            default: return prefix + "效果待确认";
        }
        return prefix + effect;
    }

    private static int count(float n) { return Math.max(1, (int) n); }
    private static String pct(float n) { return Math.round(n * 100f) + "%"; }



    public static final int COUNT = 36;

    /** 引擎当前已实现的 TYPE 上界（含）。必须与 {@link Game#resolveEvent} 的 case 分支一致。 */
    public static final int IMPLEMENTED_TYPE_MAX = 12;

    /**
     * TYPE 表的设计上界（含）。
     *
     * @deprecated 容易与 {@link #IMPLEMENTED_TYPE_MAX} 混淆，新代码请显式选用其一。
     */
    @Deprecated
    public static final int TYPE_MAX = IMPLEMENTED_TYPE_MAX;

    /** TYPE 数量（当前已实现）。 */
    public static final int TYPE_COUNT = IMPLEMENTED_TYPE_MAX + 1;

    public static final String[] NAMES = {
            "会说话的井", "失控的炼金台", "流浪观星者", "双面祭坛", "遗忘的候车室", "血锈的锻炉",
            "无声的赌桌", "低语的根须", "冻住的旅人", "半张星图", "断弦的琴", "被遗忘的祭品",
            "熔炉里的婴儿", "镜中的另一个你", "深渊投喂口", "漂浮的书页", "空盔甲", "锈蚀的钟",
            "粘稠的水池", "没有影子的商人", "第六级台阶", "会走路的树", "银色的门", "沉默的合唱团",
            "一节断指", "时间的裂缝", "被撕掉的日记", "空白的画像", "会唱歌的骨头", "熄灭的灯塔",
            "一群萤火虫", "未完成的祷告", "半融的雕像", "倒着流的瀑布", "第七个梦", "最后的信"
    };

    public static final String[] TEXT = {
            "井底有声音在说话，先是很远，然后贴着耳朵。它说只要一点点命，就能把前路照亮。",
            "炼金台的坩埚已经烧红，指针顶到了尽头。桌上还有半成品，但要伸手就得先付点什么。",
            "观星者背对着你，面前摊着一整片星图。他没有回头，只说命运可以被改写两次。",
            "祭坛两面都在发光，一面温暖，一面冰凉。它不问你信什么，只问你愿意给什么。",
            "无人列车停在站台，车厢里还留着急救箱和旧补给。广播循环播放着三十年前的到站提醒。",
            "锻炉还在烧，铁砧上摆着一件已经成形的器物。炉火的温度让人想起自己的心跳。",
            "一张赌桌，对面没有荷官。筹码堆得很高，牌已经发好，规则写在墙上，字迹潦草。",
            "低语的根须从墙缝里伸出来，缠着几枚还在反光的星尘。它们不阻止你拿，只是越缠越紧。",
            "旅人冻在冰里，姿势像是想抓住什么。他怀里抱着一个还在发热的东西。",
            "半张星图钉在墙上，另外半张被撕走。你能看懂的部分已经足够指引一条捷径。",
            "断弦的琴悬在空中，无人拨动却发出声音。音符落在地上，像小小的、会发光的水洼。",
            "祭品被遗忘在角落，落满灰。它看起来不重要，但灰尘下面有什么在轻轻地动。",
            "熔炉里传出婴儿的哭声，也可能只是金属冷却时的收缩。两种解释都让人不敢靠近。",
            "镜子里的人比你慢了半拍，然后开始自己做决定。它似乎愿意替你承受一次伤害。",
            "墙上有一个投喂口，里面很黑。你把东西放进去，它会把更值钱的东西推出来——如果你放得够多。",
            "书页在空中缓慢翻动，每一页都写着不同的死法。有一页是空白的，还带着墨香。",
            "一套空盔甲立在走廊尽头，头盔下面是空的。它在等你把手伸进去。",
            "钟早就停了，指针却还在缓慢地走。每走一格，走廊就会变短一点。",
            "水池里漂着一层油光，倒影比现实更清楚。你能看见水池底部堆着什么。",
            "商人没有影子，笑起来的时候也没有。他把货摆在布上，重复说：价格是公平的。",
            "楼梯本来只有五级，你数了三次都是六级。第六级上有东西，看起来像一枚戒指。",
            "一棵树在缓慢地走，根须像腿一样交替着挪动。它上面结的果实会发光，也会眨眼。",
            "银色的门没有把手，也没有锁。站在它面前，你听见自己的名字被念了一遍。",
            "合唱团戴着面具，唱着没有歌词的调子。声音很轻，但让人想跟着一起唱。",
            "一节断指躺在地上，指节上还戴着戒指。戒指在轻轻地颤抖，像是还有脉搏。",
            "空气里有一道裂缝，边缘不齐，里面很安静。你看久了会觉得自己才是被看着的那个。",
            "日记被撕掉了前半本，剩下的部分全在写同一件事。笔迹越来越潦草，最后变成划痕。",
            "画框里的画是空白的，但你走过去时，画布上慢慢浮现出背影。背影穿着和你一样的衣服。",
            "骨头在唱歌，调子像摇篮曲。它唱到一半停下，等你接下一句。",
            "灯塔熄灭了，但海面上还有光。走近才发现，那不是灯，是很多双眼睛在水下睁开。",
            "萤火虫聚成一团，缓慢地变换形状。最后它变成了一个箭头，指向你背后的方向。",
            "祷告做到一半就停了，跪姿还保持着。地上有一个磨损的印记，摸上去还是温的。",
            "雕像融了一半，脸已经看不清。剩下的部分还在缓慢融化，像是在流汗。",
            "瀑布从下往上流，水声很轻。水里漂着东西，有些是木片，有些不是。",
            "你做了第七个梦，梦里也是这里。梦里你选择了另一条路，然后醒来发现自己还在原地。",
            "最后一封信没有寄出，封口还粘着。收信人的名字被雨水泡花了，但信封里很厚。"
    };

    public static final String[] CHOICE_A = {
            "献出 18% 当前生命 · 血脉契约 +1 层（伤害 +10%，生命上限 -14）",
            "支付 55 星尘 · 取走 1 件成形遗物",
            "支付 30 星尘 · 获得 3 次免费重掷",
            "支付 40 星尘并献出 26% 当前生命 · 确定获得 1 件遗物",
            "支付 28 星尘 · 回复 42 生命并清空冷却（3 站后 -8% 当前生命）",
            "支付 40 星尘 · 最厚的一件遗物 +1 层",
            "押上 30% 生命 · 50% 得 2 件遗物",
            "支付 15 星尘 · 获得 1 件遗物 + 1 层诅咒",
            "支付 15 星尘 · 负债 1 层（每层 6% 生命），立即 +24 星尘",
            "现在拿走 1 件遗物 · 3 站后付 34 星尘",
            "支付 25 星尘 · 最厚的一件遗物 +2 层",
            "交出一件已有遗物 · 换回 2 件未知的（无额外星尘）",
            "支付 20 星尘并献出 16% 当前生命 · 获得 1 件遗物",
            "献出 10% 当前生命 · 获得 1 件遗物",
            "支付 25 星尘 · 负债 1 层（每层 10% 生命），立即 +40 星尘",
            "献出 8% 当前生命 · 获得 2 次免费重掷",
            "支付 10 星尘 · 70% 得 1 件遗物，30% 掉 20% 生命",
            "现在拿走 1 件遗物 · 3 站后付 16% 生命",
            "支付 80 星尘 · 一次捞起 2 件遗物",
            "交出一件已有遗物 · 换回 2 件未知的并得到 15 星尘",
            "献出 12% 当前生命 · 确定获得 1 件遗物",
            "支付 20 星尘 · 65% 得 1 件遗物，35% 掉 15% 生命",
            "献出 34% 当前生命 · 血脉契约 +2 层（伤害 +20%，生命上限 -28）",
            "回复 30 生命并清空冷却（3 站后 -14% 当前生命）",
            "支付 30 星尘 · 35% 得 3 件遗物，65% 掉 45% 生命",
            "现在拿走 2 件遗物 · 3 站后付 24% 生命",
            "支付 25 星尘 · 45% 得 1 件遗物，55% 掉 25% 生命",
            "支付 20 星尘 · 获得 2 件遗物 + 1 层诅咒",
            "接下一句 · 最薄的一件遗物 +1 层",
            "交出一件已有遗物 · 换回 2 件未知的并得到 30 星尘",
            "支付 20 星尘 · 获得 1 次免费重掷",
            "支付 55 星尘并献出 10% 当前生命 · 确定获得 2 件遗物",
            "支付 20 星尘并献出 22% 当前生命 · 获得 2 件遗物",
            "支付 15 星尘 · 55% 得 1 件遗物，45% 掉 28% 生命",
            "支付 12 星尘 · 负债 1 层（每层 4% 生命），立即 +18 星尘",
            "支付 35 星尘 · 拆开信封取 1 件遗物"
    };

    public static final String[] CHOICE_B = {
            "封住井口 · 获得 12 星尘",
            "拿走半成品 · -20 生命，+34 星尘",
            "不打扰他 · 获得 10 星尘",
            "只接受安慰 · 回复 16 生命",
            "搜刮车厢 · 获得 18 星尘",
            "变卖废料 · 获得 26 星尘",
            "见好就收 · 获得 24 星尘",
            "砍断根须 · 回复 24 生命",
            "离开冰面 · 抽走 1 层债，获得 10 星尘",
            "不碰它 · 获得 20 星尘",
            "掰断琴弦 · 获得 20 星尘",
            "花 6 星尘把祭品放回原处 · 净得 8 星尘",
            "堵住炉口 · -26% 当前生命，换 26 星尘",
            "砸碎镜子 · -16% 当前生命，换 16 星尘",
            "什么也不放 · 抽走 1 层债，获得 14 星尘",
            "合上书页 · 获得 14 星尘",
            "原路返回 · 30% 得 2 件遗物，70% 掉 30% 生命",
            "砸碎钟 · 获得 6 星尘",
            "只捞表面 · -12 生命，+22 星尘",
            "花 8 星尘买下他的推荐 · 净得 8 星尘",
            "不拿戒指 · 回复 10 生命",
            "绕开这棵树 · 获得 12 星尘",
            "花 8 星尘从门缝里换路 · 净得 10 星尘",
            "摘下耳塞 · 获得 12 星尘",
            "不碰它 · 获得 36 星尘",
            "退回去 · 获得 6 星尘",
            "合上日记 · 55% 得 2 件遗物，45% 掉 40% 生命",
            "移开视线 · 回复 12 生命",
            "安静离开 · 获得 24 星尘",
            "顺流而下 · 获得 6 星尘",
            "不跟 · 获得 6 星尘",
            "移开手 · 回复 20 生命",
            "不碰 · -35% 当前生命，换 35 星尘",
            "顺流而下 · 45% 得 2 件遗物，55% 掉 35% 生命",
            "忘掉它 · 抽走 1 层债，获得 8 星尘",
            "原样封好 · -8 生命，换 12 星尘"
    };

    public static final String[] RESULT_A = {
            "井底吸走一部分你。手背浮起一道新纹，指节上的光更刺眼。",
            "坩埚吐出一件东西，落在你手里还是烫的。",
            "星图在你眼前重新排列，你记住了三条更好的路。",
            "祭坛的一面熄灭、另一面烧得更旺。血和星尘都不见了，手里多了一件东西。",
            "急救箱是好的，疼痛退下去了。你知道账单不会不来。",
            "铁砧上的器物被敲得更结实，层数压得更深。",
            "骰子停下。有人赢了。",
            "根须松开时留下几道印子，怀里多了一件东西，皮肤底下有东西在爬。",
            "深渊在你的账簿上添了一笔，同时把星尘推到脚边。",
            "星图收进怀里。回去的路你已经记不清了。",
            "琴被重新调准，那件旧器物又厚了两层。",
            "你把一件旧东西放在祭品旁边。回头看时，它已经不见了。",
            "炉火没有伤你。它把东西递了出来，代价从你体内被抽走。",
            "镜面泛起波纹。你觉得自己轻了一点，也少了一点。",
            "投喂口把星尘推了出来，账簿上多了一行。",
            "那把死法在你脑子里排成一行，你记住了该在哪一步停手。",
            "盔甲的手套是空的，但遗物落进了你的行囊。门在你身后合上。",
            "钟声停下的瞬间，怀里多了一件东西，指针又开始走了。",
            "池底的那层油光散开，露出下面的东西。",
            "他递过来的东西确实值得那个价，还多塞了几枚星尘。",
            "戒指戴上去时，楼梯变成了五级。",
            "果实冰凉，入口却像火。",
            "门后没有房间，只有一小段更近的路，和两条更重的契约。",
            "你唱完了。面具鼓了掌，身体却开始发冷。",
            "骰子滚了很久。它停在你想不到的那一面。",
            "裂缝吞下星尘，然后吐出两件东西。",
            "日记翻到最后一页时，你的手抖了一下。",
            "画布上的背影转了过来，眼里有和你一样的疲惫。",
            "骨头没有停下。它只是把声音换成了你的。",
            "你把那件旧东西推过去。水下的光把它拉走了。",
            "箭头指向的位置，你已经站过一次。",
            "印记进入掌心，留下一圈淡淡的痕。",
            "雕像的脸清楚了，是你。它还在融化，你也是。",
            "水里有什么东西替你签了字。",
            "记忆被翻过去了，账簿上的字更重了。",
            "信封里是厚厚一沓星尘，还有一张写着你名字的纸。"
    };

    public static final String[] RESULT_B = {
            "你把井盖合上。声音停了，地上留下几枚硬币。",
            "灼痕换回了星尘，你把剩下的半成品收进袋子里。",
            "观星者没有回头。星图合上了。",
            "温暖的那面暗下去，但你还站着。",
            "车厢里能拿的都拿了。",
            "废料卖了个不错的价钱。",
            "筹码收回，什么也没发生。",
            "根须断了，汁液溅了一地。伤口合上了。",
            "你退开几步。账簿上的字淡了一点，脚下多了点星尘。",
            "你没有碰那半张星图。",
            "琴弦断了，声音像一声叹息。",
            "你什么也没拿。",
            "炉口堵住后，哭声也停了。你带走了一些星尘，留下了更多血。",
            "镜子碎了。碎片里每个自己都在看着你，手里多了些星尘。",
            "你什么也没放。投喂口合上了，账簿上少了一行。",
            "书合上了，你顺手收走了几粒星尘。",
            "你没有进那套盔甲。门自己合上了。",
            "钟碎了，指针掉在地上转了两圈。",
            "你没有多捞，只带走了表面那一层。",
            "你什么都没买。",
            "你没有拿那枚戒指。",
            "树走远了。",
            "你没有推门。门缝里塞出几枚星尘。",
            "你把耳塞戴上了。",
            "你绕开了它。",
            "裂缝还在，但你没有伸进去。",
            "日记合上了。你的手还在抖。",
            "你移开了视线。",
            "歌声在你身后停了。",
            "你顺着瀑布下来了。",
            "你没有跟过去。",
            "印记还在那里。",
            "你没有碰，但空气已经开始结冰，你只能拿血去换路。",
            "你回到了岸上。",
            "梦醒了，账簿上的字淡了一点。",
            "你把信原样封好。封口又被血粘住了。"
    };

    /**
     * A 选项的<b>预付星尘</b>。生命 / 遗物 / 延迟代价由 PARAM 列描述，不走这一列。
     * <p>每个事件至少有一个分支为 0：0 星尘的玩家不能被事件房永久卡住。
     */
    public static final int[] COST_A = {
            0, 55, 30, 40, 28, 40, 0, 15, 15, 0, 25, 0,
            20, 0, 25, 0, 10, 0, 80, 0, 0, 20, 0, 0,
            30, 20, 25, 20, 0, 0, 20, 55, 20, 15, 12, 35
    };

    /** B 选项的预付星尘。B 的代价主要是生命 / 机会成本，因此全部为 0。 */
    public static final int[] COST_B = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6,
            0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 8, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    // ══════════════════════════════════════════════════════════════
    //  参数列（每个 TYPE 的语义不同，见类注释的对照表）
    // ══════════════════════════════════════════════════════════════

    /** 主效果强度。 */
    public static final float[] PARAM_A = {
            .18f, 1f, 3f, .26f, 42f, 1f, .50f, 1f, .06f, 1f, 2f, 0f,
            .16f, .10f, .10f, 2f, .70f, 1f, 2f, 15f, .12f, .65f, .34f, 30f,
            .35f, 2f, .45f, 2f, 1f, 30f, 1f, .10f, .22f, .55f, .04f, 1f
    };

    /** 副效果。 */
    public static final float[] PARAM_B = {
            1f, 20f, 0f, 1f, .08f, 0f, 2f, 24f, 24f, 34f, 0f, 2f,
            1f, 1f, 40f, .08f, .20f, .16f, 12f, 2f, 1f, 1f, 2f, .14f,
            3f, .24f, .25f, 12f, 2f, 2f, 0f, 2f, .35f, .28f, 18f, 8f
    };

    /** 第三参数。 */
    public static final float[] PARAM_C = {
            12f, 20f, 10f, 16f, 18f, 26f, .30f, 0f, 10f, 1f, 20f, 14f,
            .26f, .16f, 14f, 14f, .30f, 0f, 22f, 16f, 10f, .15f, 18f, 12f,
            .45f, 20f, .40f, 20f, 24f, 6f, 6f, 20f, .35f, .35f, 8f, 12f
    };

    /**
     * 事件类型（选择结构）。
     * <b>上界必须与 {@link Game#resolveEvent} 的 case 分支一致。</b>
     */
    public static final int[] TYPE = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 5, 11,
            12, 12, 8, 2, 10, 9, 1, 11, 3, 6, 0, 4,
            6, 9, 10, 7, 5, 11, 2, 3, 12, 10, 8, 1
    };

    public static final int[] ART = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    };

    public static final int[] MIN_FLOOR = {
            1, 1, 1, 1, 1, 1, 2, 1, 2, 1, 2, 1,
            2, 2, 3, 2, 2, 3, 3, 3, 2, 3, 4, 3,
            3, 4, 4, 2, 4, 3, 4, 5, 4, 5, 5, 4
    };

    // ══════════════════════════════════════════════════════════════
    //  自检
    // ══════════════════════════════════════════════════════════════

    /**
     * 校验事件表。除长度与取值范围外，强制四条防退化规则：
     * <ol>
     *   <li>TYPE 必须在 {@code [0, IMPLEMENTED_TYPE_MAX]} 内——超出即说明
     *       {@code Game.resolveEvent} 还没实现该类型</li>
     *   <li>每个 TYPE 至少 2 个事件（防孤岛 / 防"只改了一个事件"）</li>
     *   <li>同一 TYPE 内 {@link #PARAM_A} 不允许全部相同——否则同类型事件退化成换皮</li>
     *   <li>每个事件至少有一个分支不收预付星尘——0 星尘玩家不能被永久卡住</li>
     * </ol>
     *
     * @return 全部通过返回 true；不通过时在 stderr 输出原因并返回 false
     */
    public static boolean valid() {
        boolean ok = true;
        int[] lens = {NAMES.length, TEXT.length, CHOICE_A.length, CHOICE_B.length,
                RESULT_A.length, RESULT_B.length, COST_A.length, COST_B.length,
                TYPE.length, ART.length, MIN_FLOOR.length,
                PARAM_A.length, PARAM_B.length, PARAM_C.length};
        for (int n : lens) {
            if (n != COUNT) {
                System.err.println("Events 数组长度必须全部等于 " + COUNT + "，发现 " + n);
                return false;
            }
        }

        for (int i = 0; i < COUNT; i++) {
            int t = TYPE[i];
            if (t < 0 || t > IMPLEMENTED_TYPE_MAX) {
                System.err.println("Events.TYPE[" + i + "]=" + t + " 超出引擎已实现范围 [0, "
                        + IMPLEMENTED_TYPE_MAX + "]。必须先改 Game.resolveEvent 再改本表。");
                ok = false;
                continue;
            }
            if (NAMES[i] == null || NAMES[i].isEmpty()) {
                System.err.println("Events.NAMES[" + i + "] 为空");
                ok = false;
            }
            if (TEXT[i] == null || TEXT[i].length() < 20) {
                System.err.println("Events.TEXT[" + i + "] 过短");
                ok = false;
            }
            if (CHOICE_A[i] == null || CHOICE_A[i].isEmpty()
                    || CHOICE_B[i] == null || CHOICE_B[i].isEmpty()) {
                System.err.println("Events 选项文案为空 #" + i);
                ok = false;
            }
            if (RESULT_A[i] == null || RESULT_B[i] == null) {
                System.err.println("Events 结果文案为空 #" + i);
                ok = false;
            }
            if (COST_A[i] < 0 || COST_B[i] < 0) {
                System.err.println("Events 代价为负 #" + i);
                ok = false;
            }
            if (COST_A[i] > 0 && COST_B[i] > 0) {
                System.err.println("Events 事件 #" + i + " 两个分支都收星尘，"
                        + "0 星尘玩家将无路可走");
                ok = false;
            }
            for (float v : new float[]{PARAM_A[i], PARAM_B[i], PARAM_C[i]}) {
                if (!Float.isFinite(v) || v < 0f) {
                    System.err.println("Events 参数非法 #" + i + " = " + v);
                    ok = false;
                }
            }
            if (ART[i] < 0 || ART[i] > 11) {
                System.err.println("Events.ART[" + i + "]=" + ART[i] + " 超出 [0, 11]");
                ok = false;
            }
            if (MIN_FLOOR[i] < 1 || MIN_FLOOR[i] > 6) {
                System.err.println("Events.MIN_FLOOR[" + i + "]=" + MIN_FLOOR[i] + " 超出 [1, 6]");
                ok = false;
            }
            for (int j = 0; j < i; j++) {
                if (NAMES[j].equals(NAMES[i])) {
                    System.err.println("Events.NAMES 重复： " + NAMES[i]);
                    ok = false;
                }
            }
        }

        // ── 防换皮：每个 TYPE ≥2 个事件，且同 TYPE 内 PARAM_A 不能全部相同 ──
        for (int t = 0; t <= IMPLEMENTED_TYPE_MAX; t++) {
            int n = 0, first = -1;
            boolean allSame = true;
            for (int i = 0; i < COUNT; i++) {
                if (TYPE[i] != t) continue;
                n++;
                if (first < 0) first = i;
                else if (PARAM_A[i] != PARAM_A[first]) allSame = false;
            }
            if (n < 2) {
                System.err.println("TYPE " + t + " 只有 " + n + " 个事件（需 >= 2，防孤岛）");
                ok = false;
            } else if (allSame) {
                System.err.println("TYPE " + t + " 内所有事件的 PARAM_A 都等于 "
                        + PARAM_A[first] + "——同类型事件已退化成换皮");
                ok = false;
            }
        }
        return ok;
    }

    /** 某 TYPE 覆盖的事件数量，供平衡探针与 UI 分组使用。 */
    public static int countOfType(int type) {
        int n = 0;
        for (int t : TYPE) if (t == type) n++;
        return n;
    }

    /** TYPE 的结构名（调试 / 探针输出用）。 */
    public static String typeName(int type) {
        switch (type) {
            case 0: return "献血";
            case 1: return "献金";
            case 2: return "预言";
            case 3: return "祭坛";
            case 4: return "医疗";
            case 5: return "锻造";
            case 6: return "赌局";
            case 7: return "诅咒";
            case 8: return "风险累积";
            case 9: return "延迟结算";
            case 10: return "二选一赌注";
            case 11: return "交换";
            case 12: return "反向代价";
            default: return "未知";
        }
    }

    public static void main(String[] args) {
        if (!valid()) {
            System.out.println("Events FAILED");
            System.exit(1);
        }
        System.out.println("Events valid: " + COUNT + " (TYPE 0.." + IMPLEMENTED_TYPE_MAX + ")");
        for (int t = 0; t <= IMPLEMENTED_TYPE_MAX; t++)
            System.out.println("  TYPE " + t + " " + typeName(t) + " : " + countOfType(t));
        int paid = 0;
        for (int i = 0; i < COUNT; i++) if (COST_A[i] + COST_B[i] > 0) paid++;
        System.out.println("  paid events: " + paid + " / " + COUNT);
    }
}
