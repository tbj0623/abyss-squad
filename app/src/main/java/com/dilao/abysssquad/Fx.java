package com.dilao.abysssquad;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 战斗表现层：粒子 / 地面贴花 / 飘字 / 震屏 / 顿帧 / 全屏闪 / 暗角 / 转场。
 *
 * <p>设计要点：全部使用「预分配平行数组 + 环形写入」，运行期不产生任何对象；
 * 粒子 1300 / 贴花 200 / 飘字 72 / 光束 48 / 延时事件 12 封顶，超出时覆盖最旧的一条。
 * 所有坐标：{@link #drawUnder}/{@link #draw} 用战斗区本地坐标 (0..1120, 0..620)，
 * {@link #drawScreen} 用逻辑屏幕坐标 (1440x810)。</p>
 *
 * <p>手感分层（v2）：</p>
 * <ul>
 *   <li>震屏 = 创伤（trauma）模型：调用方加创伤，实际位移 = trauma² × MAX × 噪声，
 *       小击轻微、大击暴烈，且天然平滑衰减（v2.3 替换旧的线性幅度）。</li>
 *   <li>顿帧 = 冻结帧 + 可选慢放尾巴，带「最短间隔 + 每秒预算」双闸，{@link #timeScale()} 必然回到 1.0。</li>
 *   <li>元素 = 0 物理 / 1 火 / 2 冰 / 3 雷 / 4 虚空，各有独立的粒子运动、形状与配色。</li>
 *   <li>击杀 / 升级 / 终极 / 首领死亡用 {@link #cue 延时事件池} 拆成多段，读起来有节奏。</li>
 * </ul>
 */
final class Fx {
    // ---------- 硬性容量上限（池容量按 v2.3 冲击分层后重新标定）----------
    /**
     * 粒子池容量：从 900 提到 1300。
     * <p>理由：一次命中现在固定铺 3 层（亮芯火花 + 定向喷溅 + 余烬），
     * 单次命中粒子数由约 3~14 涨到约 6~22；60 敌人同屏且全部命中时，
     * 旧 900 池在 1.5 秒内就会被写穿，导致「亮芯」被自己的余烬挤掉。
     * 1300 ≈ 旧峰值 900 × 1.45，足够覆盖 3 层分层 + 首领死亡 46 颗关键粒子。</p>
     */
    static final int MAX_PARTICLES = 1300;
    /**
     * 贴花池容量：从 160 提到 200。
     * <p>理由：击杀确认新增「每个击杀一圈径向环」，密集清场时（同帧 4 杀 × 首领战）
     * 贴花写入速度比旧版高约 25%；200 吸收这一增量而不影响单帧绘制预算。</p>
     */
    static final int MAX_MARKS = 200;
    /**
     * 飘字池容量：64 → 72。
     * <p>理由：合并规则按「同一敌人 220ms」生效，但 60 个不同敌人同时挨打仍然会需要
     * 更多槽位；72 是「同屏可读上限 12」的 6 倍余量，再大只会增加每帧扫描成本。</p>
     */
    static final int MAX_NUMBERS = 72;
    static final int MAX_BEAMS = 48;        // 光束 / 闪电池容量
    static final int MAX_CUES = 12;         // 延时事件池容量
    private static final float MAX_DT = 0.1f;       // 单帧时间步上限
    private static final float W = 1440f, H = 810f; // 逻辑屏幕尺寸
    private static final float MAX_HITSTOP = 0.35f; // 单次顿帧硬上限
    private static final float NO_DIR = 1e9f;       // 「无方向」哨兵

    /** 供外部（CombatFeedback）表示「无方向」的哨兵值。 */
    static final float DIR_NONE = NO_DIR;

    // ---------- 粒子种类 ----------
    private static final int P_DOT = 0;      // 圆点
    private static final int P_STREAK = 1;   // 速度方向短线（火花 / 闪电叉）
    private static final int P_DIAMOND = 2;  // 菱形
    private static final int P_SHARD = 3;    // 三角碎片
    private static final int P_RING = 4;     // 扩散圆环（可压扁）
    private static final int P_SMOKE = 5;    // 软烟雾
    private static final int P_FLAME = 6;    // 火焰（向上，外焰内焰）
    private static final int P_CRYSTAL = 7;  // 六角冰晶
    private static final int P_ARC = 8;      // 弧形刀光
    private static final int P_SPARK = 9;    // 高速粒子（亮芯短线）
    private static final int P_EMBER = 10;   // 余烬：缓慢上浮、闪烁、熄灭

    // ---------- 贴花种类 ----------
    private static final int M_PATCH = 0;    // 地面光斑
    private static final int M_WAVE = 1;     // 冲击波环（向外扩散）
    private static final int M_RUNE = 2;     // 地面符文印记
    private static final int M_FROST = 3;    // 地面霜痕
    private static final int M_VOID = 4;     // 虚空收缩环（向内收缩）
    private static final int M_STAIN = 5;    // 血渍 / 焦痕（贴地椭圆，缓慢淡出）
    private static final int M_SHOCK = 6;    // 锐利冲击环（细、快、高对比）
    private static final int M_EMBER = 7;    // 余烬光池（呼吸式）

    // ---------- 光束种类 ----------
    private static final int B_BEAM = 0;     // 直线光束
    private static final int B_BOLT = 1;     // 锯齿闪电
    private static final int B_FORK = 2;     // 主闪电 + 分叉

    // ---------- 延时事件种类 ----------
    private static final int CUE_BOSS_2 = 0; // 首领死亡第二段：次级冲击 + 碎屑
    private static final int CUE_BOSS_3 = 1; // 首领死亡第三段：余烬铺开
    private static final int CUE_ULT_2 = 2;  // 终极第二段：扩散环 + 余烬
    private static final int CUE_LEVEL_2 = 3;// 升级第二段：字幕 + 星屑
    private static final int CUE_HURT_2 = 4; // 受击第二段：暗角加深

    // ---------- 手感预设编号（配合 juice 使用） ----------
    static final int J_HIT = 0, J_CRIT = 1, J_KILL = 2, J_ELITE = 3, J_BOSS = 4,
            J_HURT = 5, J_DEATH = 6, J_LEVEL = 7, J_SKILL = 8, J_ULT = 9, J_PHASE = 10,
            J_CLASH = 11;
    // 震屏幅度（像素）
    private static final float[] J_AMP = {1.4f, 3.8f, 2.2f, 5.2f, 12f, 6f, 10f, 3.2f, 4.2f, 9f, 11f, 2.6f};
    // 震屏时长（秒）
    private static final float[] J_DUR = {.055f, .10f, .08f, .17f, .62f, .22f, .5f, .16f, .13f, .36f, .5f, .09f};
    // 顿帧时长（秒）
    private static final float[] J_STOP = {0f, .04f, 0f, .07f, .26f, .06f, .3f, .10f, .05f, .16f, .2f, 0f};
    // chatter 频率（Hz）：重击低频大幅度，轻击高频小幅度
    private static final float[] J_CHAT_FREQ = {30f, 26f, 22f, 20f, 16f, 18f, 15f, 29f, 28f, 18f, 17f, 34f};
    // chatter 幅度比例（相对主震幅度）
    private static final float[] J_CHAT_MUL = {.16f, .26f, .24f, .36f, .48f, .22f, .28f, .10f, .14f, .42f, .38f, .18f};

    // ---------- 飘字样式 ----------
    static final int N_DAMAGE = 0;  // 普通伤害
    static final int N_CRIT = 1;    // 暴击
    static final int N_HEAL = 2;    // 治疗
    static final int N_COIN = 3;    // 星尘
    static final int N_XP = 4;      // 经验
    static final int N_STATUS = 5;  // 状态文字
    static final int N_SHIELD = 6;  // 护盾吸收
    static final int N_BIG = 7;     // 大字幕

    // ---------- 元素 ----------
    static final int EL_PHYS = 0, EL_FIRE = 1, EL_FROST = 2, EL_BOLT = 3, EL_VOID = 4;

    // ---------- 粒子池（平行数组，零 GC） ----------
    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] pvx = new float[MAX_PARTICLES];
    private final float[] pvy = new float[MAX_PARTICLES];
    private final float[] plife = new float[MAX_PARTICLES];  // 剩余寿命
    private final float[] pmax = new float[MAX_PARTICLES];   // 初始寿命
    private final float[] psize = new float[MAX_PARTICLES];  // 半径
    private final float[] pgrow = new float[MAX_PARTICLES];  // 半径增长/秒
    private final float[] pgrav = new float[MAX_PARTICLES];  // 重力像素/秒²
    private final float[] pdrag = new float[MAX_PARTICLES];  // 阻力系数
    private final float[] pseed = new float[MAX_PARTICLES];  // 随机相位/角度
    private final float[] ptint = new float[MAX_PARTICLES];  // 次级参数（拖尾长度 / 压扁比）
    private final int[] pcolor = new int[MAX_PARTICLES];
    private final int[] pkind = new int[MAX_PARTICLES];
    private final byte[] pprio = new byte[MAX_PARTICLES];  // 优先级 0..3，保护关键粒子不被杂兵粒子挤掉
    private int pNext, pAlive;

    // ---------- 粒子优先级（清单 #19）----------
    /** 杂兵命中火花：池满时最先被牺牲。 */
    static final int PRIO_TRASH = 0;
    /** 普通效果（击杀碎片、环境粒子）。 */
    static final int PRIO_NORMAL = 1;
    /** 重要效果（精英、玩家、暴击）。 */
    static final int PRIO_IMPORTANT = 2;
    /** 关键效果（首领预警/死亡、升级、终极、玩家受伤）：绝不被低优先级驱逐。 */
    static final int PRIO_CRITICAL = 3;
    /** 优先级冲突时的探测半径（摊销 O(1)，不做全池扫描）。 */
    private static final int PRIO_PROBE = 24;

    // ---------- 贴花池 ----------
    private final float[] mx = new float[MAX_MARKS];
    private final float[] my = new float[MAX_MARKS];
    private final float[] mr0 = new float[MAX_MARKS];   // 起始半径
    private final float[] mr1 = new float[MAX_MARKS];   // 结束半径
    private final float[] mlife = new float[MAX_MARKS];
    private final float[] mmax = new float[MAX_MARKS];
    private final float[] mseed = new float[MAX_MARKS];
    private final int[] mcolor = new int[MAX_MARKS];
    private final int[] mkind = new int[MAX_MARKS];
    private int mNext, mAlive;

    // ---------- 飘字池 ----------
    private final float[] nx = new float[MAX_NUMBERS];
    private final float[] ny = new float[MAX_NUMBERS];
    private final float[] nvx = new float[MAX_NUMBERS];  // 水平漂移速度
    private final float[] nvalue = new float[MAX_NUMBERS];
    private final float[] nlife = new float[MAX_NUMBERS];
    private final float[] nmax = new float[MAX_NUMBERS];
    private final int[] ncolor = new int[MAX_NUMBERS];
    private final int[] nstyle = new int[MAX_NUMBERS];
    private final String[] nstr = new String[MAX_NUMBERS];
    private int nNext, nAlive;

    // ---------- 光束池 ----------
    private final float[] bx1 = new float[MAX_BEAMS], by1 = new float[MAX_BEAMS];
    private final float[] bx2 = new float[MAX_BEAMS], by2 = new float[MAX_BEAMS];
    private final float[] blife = new float[MAX_BEAMS], bmax = new float[MAX_BEAMS];
    private final long[] bseed = new long[MAX_BEAMS];
    private final int[] bcolor = new int[MAX_BEAMS];
    private final int[] bkind = new int[MAX_BEAMS];
    private final int[] bfork = new int[MAX_BEAMS];   // 分叉数量
    private int bNext, bAlive;

    // ---------- 延时事件池（零 GC 的小调度器） ----------
    private final float[] cueDelay = new float[MAX_CUES];
    private final float[] cueX = new float[MAX_CUES];
    private final float[] cueY = new float[MAX_CUES];
    private final float[] cueA = new float[MAX_CUES];
    private final int[] cueKind = new int[MAX_CUES];
    private final int[] cueColor = new int[MAX_CUES];
    private int cueNext, cueAlive;

    // ---------- 复用绘制资源（构造时一次分配） ----------
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 加色混合（构造时创建一次）：敌人本体闪白用它，比 saveLayer 便宜一个数量级。 */
    private final PorterDuffXfermode ADD_MODE = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final char[] chars = new char[12]; // 复用的数字字符缓冲，drawText(char[]) 不产生临时对象
    private final Shader vigShader;          // 常驻暗角渐变
    /** 彩色暗角脉冲：8 个预建渐变槽位（构造时一次建好），运行期零分配。 */
    private static final int PULSE_SLOTS = 8;
    private final Shader[] pulseShaders = new Shader[PULSE_SLOTS];
    private final int[] pulseSlotColor = new int[PULSE_SLOTS];
    /** 预置的脉冲色：受击红 / 首领金 / 终极紫 / 通用白 / 薄荷 / 蓝 / 青 / 橙。 */
    private static final int[] PULSE_PRESET = {
            0xfff18f8b, 0xffefbe8a, 0xffc2a7ff, 0xffffffff,
            0xff9de7c1, 0xff93c4ff, 0xff83d6d9, 0xffffa37b};

    // ---------- 震屏（v2.3 创伤 / trauma 模型）----------
    //
    // 为什么换成 trauma：旧版每次命中直接把幅度写到 shakeAmp，60 只怪同时挨打时
    // 幅度被「取最大」压成一条水平线 —— 小击和大击看起来一样，且衰减是线性的，
    // 尾巴拖得很长。trauma 模型把「调用量」累加成 0..1 的创伤值，实际位移取平方：
    // 小击（trauma 0.2）几乎无感，大击（trauma 0.9）剧烈，天然做到「轻重分层」。
    /** 调用量归一化基准：6px 的调用量 = 一次满额创伤。 */
    private static final float SHAKE_NORMALIZE = 6f;
    /** trauma=1 时每轴最大位移（像素）；旧的线性最大值约 13px，这里放大到 22px 让首领级打击更暴烈。 */
    private static final float SHAKE_MAX_PX = 22f;
    /** 创伤衰减速度：1.7/s，满创伤约 0.6s 归零（旧版 0.7s 线性，观感接近但曲线更自然）。 */
    private static final float TRAUMA_DECAY = 1.7f;
    /** 小于该增量的调用不刷新震屏方向，避免杂兵火花把首领的命中轴带偏。 */
    private static final float TRAUMA_DIR_EPS = 0.04f;
    private float trauma;                     // 0..1，累加式
    private float traumaPeak;                 // 最近一次主导创伤量，用于方向优先级
    private float shakeAngle = -1.5707964f;   // 主震方向，默认向上
    private float shakeBias;                  // 有向分量占比 0..1
    private float shakeAmp, shakeDur, shakeTime;  // 旧字段保留：仅作诊断 / busy() 的预估
    /** 每帧推进一次的噪声状态（xorshift64，非 Math.random）：同帧内多次读取必然一致。 */
    private long noiseState = 0xC0FFEE123456789L;
    private float noiseX, noiseY;             // 本帧噪声 [-1,1]
    private float chatAmp, chatDur, chatTime, chatFreq = 26f;
    private float shakeIntensity = 1f;        // 调用方音量（0..1）
    private float shakeOutX, shakeOutY;       // 本帧采样值（update 后同帧稳定）
    private boolean shakeSampled;

    // ---------- 顿帧 ----------
    private float hitStop;                    // 冻结剩余（秒）
    private float slowTime, slowDur, slowScale = 1f; // 慢放尾巴

    // ---------- 顿帧预算（清单 #3：按目标类型分配 + 每秒总时长上限）----------
    /** 目标类型：杂兵。 */
    static final int HS_TRASH = 0;
    /** 目标类型：精英。 */
    static final int HS_ELITE = 1;
    /** 目标类型：首领普通受击。 */
    static final int HS_BOSS = 2;
    /** 目标类型：首领阶段转换 / 死亡。 */
    static final int HS_PHASE = 3;
    /** 目标类型：玩家自身事件（受伤、终极）。 */
    static final int HS_PLAYER = 4;
    /** 每种目标类型单次顿帧上限（秒）：杂兵 0 帧，精英 2~3 帧，首领 2 帧，阶段 4~8 帧。 */
    private static final float[] HS_CAP = {0f, 0.05f, 0.035f, 0.14f, 0.15f};
    /**
     * 滑动窗口长度：1 秒。
     * <p>为什么是滑动窗口而不是「回充池」：回充池在爆发期会允许多次 0.05s 叠加，
     * 一秒内实际冻结可能超过 200ms（build 越强越像幻灯片）。滑动窗口给出硬上界：
     * 任意 1 秒窗口内总顿帧 ≤ {@link #HS_BUDGET_MAX}。</p>
     */
    private static final float HS_WINDOW = 1f;
    /** 窗口分桶数：8 × 0.125s，桶滚动实现「最近 1 秒」而不需要保存整段历史。 */
    private static final int HS_BUCKETS = 8;
    private static final float HS_BUCKET_DT = HS_WINDOW / HS_BUCKETS;
    /**
     * 每秒顿帧总预算：0.12s（≈7 帧 @60fps）。
     * <p>清单 #3 的经验值：超过 120ms/s 的顿帧人眼会读成「卡顿」而不是「打击感」。</p>
     */
    private static final float HS_BUDGET_MAX = 0.12f;
    /**
     * 相邻两次顿帧的最短间隔：60ms（≈4 帧）。
     * <p>没有这个间隔时，同一帧内的多段 AoE 会各自命中并叠加冻结 —— 玩家读到的
     * 是「掉帧」。60ms 刚好让两次顿帧在时间上分开、仍属同一次打击感。</p>
     */
    private static final float HS_MIN_GAP = 0.06f;
    private final float[] hsBucket = new float[HS_BUCKETS];
    private int hsBucketIdx;
    private float hsBucketClock;              // 当前桶已累计的时间
    private float hsWindowSum;                // 最近 1 秒内已消耗的顿帧总和
    private float hsLastAt = -99f;            // 上次真正生效顿帧的 clock
    private float hitstopApplied;             // 最近一次实际生效的顿帧（诊断用）

    // ---------- 伤害数字合并表（同一敌人 220ms 内复用同一个数字）----------
    /**
     * 每敌人「最近一个伤害数字」的索引表，开放寻址，容量 128（2 的幂）。
     * <p>为什么需要它：密集战斗中同一个小怪每秒会挨 5~10 下，每个都新建数字会
     * 立刻撞上同屏上限 12，导致真正重要的伤害被丢弃。按敌人 id 复用同一个槽位，
     * 数字只更新数值并重置寿命，屏幕上的信息量恒定。</p>
     */
    private static final int NKEY_TABLE = 128;
    private static final int NKEY_PROBE = 8;
    private static final float NUMBER_MERGE_WINDOW = 0.22f;   // 220ms
    private final int[] nkeyId = new int[NKEY_TABLE];          // 敌人 id（0 = 空槽）
    private final int[] nkeySlot = new int[NKEY_TABLE];        // 对应飘字槽位，-1 = 无
    private final float[] nkeyAt = new float[NKEY_TABLE];      // 最近一次命中的 clock
    /** 玩家锚点：伤害数字要主动避开玩家角色（0 距离时退化，不影响其它布局）。 */
    private static final float NUMBER_PLAYER_CLEAR = 82f;      // 数字与玩家中心的最小间距
    private float anchorX, anchorY;
    private boolean anchorSet;

    // ---------- 暴击飘字的弹出动画 ----------
    /** 弹出时长：120ms（清单要求 1.35× → 1.0 的短促回弹）。 */
    private static final float NPOP_TIME = 0.12f;
    private static final float NPOP_SCALE = 0.35f;             // 1.0 + 0.35 = 1.35×
    private final float[] npop = new float[MAX_NUMBERS];       // 弹出剩余时间（秒），0 = 无
    private final int[] nkeyOf = new int[MAX_NUMBERS];         // 该数字归属的敌人 id（0 = 无）
    /** 暴击统一金色（要求：暴击必须是金色，元素色只保留在放射线上）。 */
    private static final int CRIT_GOLD = 0xffffd45a;

    // ---------- 击杀确认 ----------
    static final int KILL_NORMAL = 0, KILL_ELITE = 1, KILL_BOSS = 2;
    /** tier 2 白闪的峰值系数：0.30 → 实际绘制 alpha ≈ 0.105（低 alpha，不刺眼）。 */
    private static final float KILL_BOSS_FLASH_SCALE = 0.30f;

    // ---------- 无障碍 / 可调强度（4 个正交滑条）----------
    private float flashIntensity = 1f;        // 全屏闪光强度 0..1（"flash" 设置）
    private float particleDensity = 1f;       // 粒子密度 0..1（"particles" 设置）
    private float numberDensity = 1f;         // 伤害数字密度 0..1（"numbers" 设置）
    private static final float FLASH_ALPHA_CAP = 0.35f;  // 单次全屏闪光 alpha 上限（WCAG 建议）
    private static final float FLASH_MIN_INTERVAL = 0.16f; // 两次闪光最短间隔
    private static final int FLASH_MAX_PER_SEC = 3;        // WCAG 2.3.1：每秒不超过 3 次
    private float flashLastAt = -99f;         // 上次闪光发生的 clock
    private float flashWinStart = -99f;       // 当前统计窗口起点
    private int flashWinCount;                // 窗口内已触发次数
    private float flashPeak = 1f;             // 本次闪光的峰值 alpha 系数

    /** 全局同屏伤害数字上限（清单 #2：超过 12 个数字就变成噪点）。 */
    private static final int NUMBER_HARD_CAP = 12;
    private int numberCap = NUMBER_HARD_CAP;  // 由 numberDensity 缩放后的实际上限

    // ---------- 其它状态 ----------
    private float clock;                      // 累计时间，供旋转/闪烁动画
    private float fadeLevel, fadeTarget, fadeRate; // 黑幕 0..1
    private float flashTime, flashMax; private int flashColor;
    private float vigStrength, vigTime, vigMax; private int vigColor;
    private float vignetteBase = 0.25f;       // 常驻暗角强度（首领战可调到 0.45）
    private float globalScale = 1f;           // 粒子数量预算（性能/可读性）
    private long rngState = 0x2545F4914F6CDD1DL; // xorshift64，零分配随机

    /** 构造：预建全部渐变（常驻暗角 + 8 个脉冲色槽），此后运行期不再分配任何绘制对象。 */
    Fx() {
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        // 中心透明、边缘变黑的径向渐变；绘制时用 paint alpha 控制强度
        vigShader = new RadialGradient(W * 0.5f, H * 0.5f, 900f,
                new int[]{0x00000000, 0x00000000, 0xff000000},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        // 脉冲暗角：按预设色一次性建好，运行期只按颜色查表（零分配）
        for (int i = 0; i < PULSE_SLOTS; i++) {
            int rgb = PULSE_PRESET[i] & 0x00FFFFFF;
            pulseSlotColor[i] = PULSE_PRESET[i];
            pulseShaders[i] = new RadialGradient(W * 0.5f, H * 0.5f, 900f,
                    new int[]{rgb, rgb, 0xff000000 | rgb},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    // =====================================================================
    // 随机数 / 颜色（无对象分配）
    // =====================================================================
    /** 返回 [0,1) 随机数。 */
    private float rnd() {
        long s = rngState;
        s ^= s << 13; s ^= s >>> 7; s ^= s << 17; rngState = s;
        return (float) ((s >>> 40) & 0xFFFFFF) / 16777216f;
    }

    /** 返回 [-1,1] 随机数。 */
    private float rnd2() { return rnd() * 2f - 1f; }

    /** 稳定哈希：由种子与索引生成 [-1,1]，用于闪电折线（不闪烁）。 */
    private static float hash(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + (i + 1) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (float) (((h >>> 40) & 0xFFFF) - 32768) / 32768f;
    }

    /** 颜色叠加透明度：a=0..1，保留原 RGB；原色 alpha 为 0 时视为不透明。 */
    private static int alpha(int color, float a) {
        if (a < 0f) a = 0f; else if (a > 1f) a = 1f;
        int ca = (color >>> 24) & 0xff;
        if (ca == 0) ca = 255;
        return ((int) (ca * a) << 24) | (color & 0x00FFFFFF);
    }

    /** 颜色向白色提亮：t=0 原色，t=1 纯白；只改 RGB。 */
    private static int lighten(int color, float t) {
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        int r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 元素主色。 */
    private static int elColor(int el, boolean crit) {
        if (crit) {
            switch (el) {
                case EL_FIRE: return 0xffffd27a;
                case EL_FROST: return 0xffd8f4ff;
                case EL_BOLT: return 0xfffff6c8;
                case EL_VOID: return 0xffe2c8ff;
                default: return 0xfffff2c4;
            }
        }
        switch (el) {
            case EL_FIRE: return 0xffff8a4a;
            case EL_FROST: return 0xff9fd8f5;
            case EL_BOLT: return 0xff8fe3ff;
            case EL_VOID: return 0xff9d7ce8;
            default: return 0xfffff0d8;
        }
    }

    // =====================================================================
    // 主循环
    // =====================================================================
    /** 更新所有效果。dt 单位秒，内部钳制到 ≤0.1；传入 NaN 时直接忽略。 */
    void update(float dt) {
        if (Float.isNaN(dt) || Float.isInfinite(dt)) return;
        if (dt < 0f) dt = 0f;
        if (dt > MAX_DT) dt = MAX_DT;
        clock += dt;

        // 顿帧：冻结 → 慢放 → 恢复 1.0（两条路径都会自然归零）
        if (hitStop > 0f) {
            hitStop -= dt;
            if (hitStop <= 0f) hitStop = 0f;
        }
        if (slowTime > 0f) {
            slowTime -= dt;
            if (slowTime <= 0f) { slowTime = 0f; slowDur = 0f; slowScale = 1f; }
        }
        // 顿帧滑动窗口前进：按 0.125s 分桶滚动，回收 1 秒前的消耗
        hsBucketClock += dt;
        while (hsBucketClock >= HS_BUCKET_DT) {
            hsBucketClock -= HS_BUCKET_DT;
            hsBucketIdx = (hsBucketIdx + 1 >= HS_BUCKETS) ? 0 : hsBucketIdx + 1;
            // 覆盖即将写入的那个桶前，先把它的旧值从窗口总和里扣掉
            hsWindowSum -= hsBucket[hsBucketIdx];
            if (hsWindowSum < 0f) hsWindowSum = 0f;
            hsBucket[hsBucketIdx] = 0f;
        }
        // 闪光频率窗口：每秒重置一次计数（WCAG 2.3.1）
        if (flashTime > 0f || flashWinStart > -90f) {
            if (clock - flashWinStart >= 1f) { flashWinStart = clock; flashWinCount = 0; }
        }

        // 震屏：创伤线性衰减 + 独立 chatter 层（两者都自然归零）
        if (trauma > 0f) {
            trauma -= TRAUMA_DECAY * dt;
            if (trauma < 0f) trauma = 0f;
        }
        if (shakeTime > 0f) {
            shakeTime -= dt;
            if (shakeTime <= 0f) { shakeTime = 0f; shakeAmp = 0f; shakeDur = 0f; }
        }
        if (chatTime > 0f) {
            chatTime -= dt;
            if (chatTime <= 0f) { chatTime = 0f; chatAmp = 0f; chatDur = 0f; }
        }
        shakeSampled = false;

        // 全屏闪光 / 暗角脉冲
        if (flashTime > 0f) { flashTime -= dt; if (flashTime < 0f) flashTime = 0f; }
        if (vigTime > 0f) { vigTime -= dt; if (vigTime < 0f) vigTime = 0f; }
        // 黑幕渐变趋近目标
        if (fadeLevel != fadeTarget && fadeRate > 0f) {
            float step = fadeRate * dt;
            fadeLevel = fadeLevel < fadeTarget
                    ? Math.min(fadeTarget, fadeLevel + step)
                    : Math.max(fadeTarget, fadeLevel - step);
        }

        // 延时事件：倒计时到点即触发（先递减再触发，保证同一帧内可再调度）
        for (int i = 0; i < MAX_CUES; i++) {
            if (cueDelay[i] <= 0f) continue;
            cueDelay[i] -= dt;
            if (cueDelay[i] <= 0f) {
                cueDelay[i] = 0f;
                cueAlive--;
                fireCue(i);
            }
        }
        if (cueAlive < 0) cueAlive = 0;

        // 粒子积分
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (plife[i] <= 0f) continue;
            plife[i] -= dt;
            if (plife[i] <= 0f) { plife[i] = 0f; pAlive--; continue; }
            float d = 1f - pdrag[i] * dt;
            if (d < 0.85f) d = 0.85f; else if (d > 1f) d = 1f;
            pvx[i] *= d; pvy[i] *= d;
            pvy[i] += pgrav[i] * dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            psize[i] += pgrow[i] * dt;
            if (psize[i] < 0f) psize[i] = 0f;
        }
        if (pAlive < 0) pAlive = 0;

        // 贴花计时
        for (int i = 0; i < MAX_MARKS; i++) {
            if (mlife[i] <= 0f) continue;
            mlife[i] -= dt;
            if (mlife[i] <= 0f) { mlife[i] = 0f; mAlive--; }
        }
        if (mAlive < 0) mAlive = 0;

        // 飘字计时（位置在绘制时按 prog 求值，这里只推进寿命）
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (npop[i] > 0f) {                     // 暴击弹出计时：120ms 归零
                npop[i] -= dt;
                if (npop[i] < 0f) npop[i] = 0f;
            }
            if (nlife[i] <= 0f) continue;
            nlife[i] -= dt;
            if (nlife[i] <= 0f) { nlife[i] = 0f; nAlive--; nkeyOf[i] = 0; }
        }
        if (nAlive < 0) nAlive = 0;

        // 光束计时
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (blife[i] <= 0f) continue;
            blife[i] -= dt;
            if (blife[i] <= 0f) { blife[i] = 0f; bAlive--; }
        }
        if (bAlive < 0) bAlive = 0;
    }

    /** 触发一条延时事件。 */
    private void fireCue(int i) {
        switch (cueKind[i]) {
            case CUE_BOSS_2:
                shockwave(cueX[i], cueY[i], 260f, 0xfff3d8a8, 0.5f);
                shockRing(cueX[i], cueY[i], 190f, 0xffffffff, 0.3f);
                for (int k = 0; k < 14; k++) {
                    float an = rnd() * 6.2831853f;
                    spawnP(cueX[i], cueY[i], (float) Math.cos(an) * (120f + rnd() * 220f),
                            (float) Math.sin(an) * (120f + rnd() * 220f) - 40f,
                            2.6f + rnd() * 3.4f, 0f, 300f, 0.9f, 0.5f + rnd() * 0.5f,
                            cueColor[i], P_SHARD);
                }
                break;
            case CUE_BOSS_3:
                spawnM(cueX[i], cueY[i] + 8f, 70f, 92f, 2.6f, 0x55ffb46a, M_EMBER);
                for (int k = 0; k < 20; k++)
                    spawnP(cueX[i] + rnd2() * 70f, cueY[i] + rnd2() * 40f,
                            rnd2() * 12f, -24f - rnd() * 50f,
                            1.8f + rnd() * 2.4f, -0.4f, -12f, 0.75f, 1.4f + rnd() * 1.8f,
                            rnd() < 0.5f ? 0xffffc98a : 0xffe0884a, P_EMBER);
                break;
            case CUE_ULT_2:
                shockwave(cueX[i], cueY[i], 300f, lighten(cueColor[i], 0.4f), 0.55f);
                for (int k = 0; k < 16; k++)
                    spawnP(cueX[i] + rnd2() * 80f, cueY[i] + rnd2() * 50f,
                            rnd2() * 26f, -40f - rnd() * 70f,
                            2f + rnd() * 2.6f, -0.6f, -14f, 0.8f, 1.1f + rnd() * 1.4f,
                            rnd() < 0.4f ? 0xffffe8b8 : cueColor[i], P_EMBER);
                break;
            case CUE_LEVEL_2:
                levelUpB(cueX[i], cueY[i]);
                break;
            case CUE_HURT_2:
                vignettePulse(cueColor[i], 0.42f, 0.3f);
                break;
            default: break;
        }
    }

    /** 写入一条延时事件：delay 秒后触发。 */
    private void cue(int kind, float delay, float x, float y, int color, float a) {
        if (Float.isNaN(delay) || delay < 0f) return;
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        int i = cueNext;
        cueNext = (i + 1 >= MAX_CUES) ? 0 : i + 1;
        if (cueDelay[i] <= 0f) cueAlive++;
        cueKind[i] = kind;
        cueDelay[i] = delay <= 0.001f ? 0.001f : delay;
        cueX[i] = x; cueY[i] = y; cueColor[i] = color; cueA[i] = a;
    }

    // =====================================================================
    // 池分配（环形覆盖最旧）
    // =====================================================================
    /**
     * 取一个粒子槽位，带优先级保护（清单 #19）。
     *
     * <p>策略：从环形写指针起最多探测 {@link #PRIO_PROBE} 个槽位 —— 优先用已死亡的槽；
     * 全是活粒子时，只有当探测范围内存在「优先级 ≤ 本次请求」的槽时才覆盖它，
     * 否则返回 -1 拒绝本次生成。这样首领预警 / 终极粒子不会被 40 个小怪的命中火花挤掉，
     * 同时保持 O(1) 摊销、零分配、不扫描全池。</p>
     *
     * @param prio 本次请求的优先级 {@link #PRIO_TRASH}..{@link #PRIO_CRITICAL}
     * @return 可用槽位下标；池内全是更高优先级粒子时返回 -1
     */
    private int allocP(int prio) {
        int dead = -1, lowest = -1, lowestPrio = 99;
        int i = pNext;
        for (int n = 0; n < PRIO_PROBE; n++) {
            if (plife[i] <= 0f) { dead = i; break; }
            int pi = pprio[i];
            if (pi < lowestPrio) { lowestPrio = pi; lowest = i; }
            i = (i + 1 >= MAX_PARTICLES) ? 0 : i + 1;
        }
        int slot;
        if (dead >= 0) {
            slot = dead;
        } else if (lowest >= 0 && lowestPrio <= prio) {
            slot = lowest;      // 只牺牲不高于自己优先级的粒子
        } else {
            return -1;          // 全部是更高优先级：拒绝，保护关键信号
        }
        pNext = (slot + 1 >= MAX_PARTICLES) ? 0 : slot + 1;
        if (plife[slot] <= 0f) pAlive++;
        return slot;
    }

    /** 写入一个粒子。x,y 位置；vx,vy 速度；size 半径；grow 半径增速；grav 重力；drag 阻力；life 寿命。 */
    private int spawnP(float x, float y, float vx, float vy, float size, float grow,
                       float grav, float drag, float life, int color, int kind) {
        return spawnPP(x, y, vx, vy, size, grow, grav, drag, life, color, kind, PRIO_NORMAL);
    }

    /**
     * 写入一个带优先级的粒子（内部主入口）。
     *
     * @param prio 0..3，见 {@link #PRIO_TRASH} / {@link #PRIO_NORMAL} /
     *             {@link #PRIO_IMPORTANT} / {@link #PRIO_CRITICAL}
     * @return 槽位下标；被优先级保护拒绝时返回 -1
     */
    private int spawnPP(float x, float y, float vx, float vy, float size, float grow,
                        float grav, float drag, float life, int color, int kind, int prio) {
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(vx) || Float.isNaN(vy)) return -1;
        int p = prio < 0 ? 0 : (prio > 3 ? 3 : prio);
        int i = allocP(p);
        if (i < 0) return -1;
        px[i] = x; py[i] = y; pvx[i] = vx; pvy[i] = vy;
        psize[i] = size > 0f ? size : 1f;
        pgrow[i] = grow; pgrav[i] = grav; pdrag[i] = drag;
        pmax[i] = plife[i] = (life > 0.01f ? life : 0.01f);
        pcolor[i] = color; pkind[i] = kind; pseed[i] = rnd() * 6.2831853f;
        pprio[i] = (byte) p;
        ptint[i] = 0f;
        return i;
    }

    /** 取一个贴花槽位；槽满时覆盖最旧贴花。 */
    private int allocM() {
        int i = mNext;
        mNext = (i + 1 >= MAX_MARKS) ? 0 : i + 1;
        if (mlife[i] <= 0f) mAlive++;
        return i;
    }

    /** 写入一个贴花。r0 起始半径，r1 结束半径（用于扩散/收缩）。 */
    private int spawnM(float x, float y, float r0, float r1, float life, int color, int kind) {
        if (Float.isNaN(x) || Float.isNaN(y)) return -1;
        int i = allocM();
        mx[i] = x; my[i] = y;
        mr0[i] = r0 > 0.5f ? r0 : 0.5f;
        mr1[i] = r1 > 0.5f ? r1 : mr0[i];
        mmax[i] = mlife[i] = (life > 0.01f ? life : 0.01f);
        mcolor[i] = color; mkind[i] = kind; mseed[i] = rnd() * 6.2831853f;
        return i;
    }

    /** 取一个飘字槽位；槽满时覆盖最旧飘字。 */
    private int allocN() {
        int i = nNext;
        nNext = (i + 1 >= MAX_NUMBERS) ? 0 : i + 1;
        if (nlife[i] <= 0f) nAlive++;
        return i;
    }

    /** 写入一条飘字。 */
    private int spawnN(float x, float y, float vx, float life, int color,
                       int style, String s, float value) {
        if (Float.isNaN(x) || Float.isNaN(y)) return -1;
        int i = allocN();
        nx[i] = x; ny[i] = y; nvx[i] = vx;
        nmax[i] = nlife[i] = (life > 0.05f ? life : 0.05f);
        ncolor[i] = color; nstyle[i] = style; nstr[i] = s;
        nvalue[i] = (Float.isNaN(value) || Float.isInfinite(value)) ? 0f : value;
        npop[i] = 0f;
        nkeyOf[i] = 0;
        return i;
    }

    /** 取一个光束槽位；槽满时覆盖最旧光束。 */
    private int allocB() {
        int i = bNext;
        bNext = (i + 1 >= MAX_BEAMS) ? 0 : i + 1;
        if (blife[i] <= 0f) bAlive++;
        return i;
    }

    /** 写入一条光束，返回槽位下标；被拒绝时返回 -1。 */
    private int spawnB(float x1, float y1, float x2, float y2, float life, int color, int kind) {
        if (Float.isNaN(x1) || Float.isNaN(y1) || Float.isNaN(x2) || Float.isNaN(y2)) return -1;
        int i = allocB();
        bx1[i] = x1; by1[i] = y1; bx2[i] = x2; by2[i] = y2;
        bmax[i] = blife[i] = (life > 0.01f ? life : 0.01f);
        bcolor[i] = color; bkind[i] = kind; bfork[i] = 0;
        long s = ((long) (rnd() * 0x7FFFFFFF)) << 7 | (long) (x1 + y2);
        bseed[i] = s == 0L ? 0x1234ABCDL : s;
        return i;
    }

    // =====================================================================
    // 震动 / 顿帧 / 屏幕事件
    // =====================================================================
    /**
     * 无向震屏（兼容旧接口）：把幅度换算成创伤后累加。
     * <p>签名不变，但语义已从「覆盖幅度」改为「累加创伤」：连续小击会叠加成中等震感，
     * 单次大击仍然暴烈。amp 的单位仍是「像素」，除以 {@link #SHAKE_NORMALIZE} 归一。</p>
     */
    void shake(float amplitude, float duration) {
        applyShake(amplitude, duration, -1.5707964f, 0f);
    }

    /**
     * 有向震屏：dx,dy 是命中方向向量（无需归一化）。
     * 抖动会朝该方向来回弹，让打击「有来处」。
     */
    void shakeDir(float amplitude, float duration, float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) { shake(amplitude, duration); return; }
        applyShake(amplitude, duration, (float) Math.atan2(dy, dx), 0.65f);
    }

    /**
     * 震屏完整入口：把「幅度 × 时长」转成一次创伤注入。
     *
     * <p>创伤公式：{@code trauma = min(1, trauma + amp/SHAKE_NORMALIZE × durScale)}。
     * {@code durScale} 让长震屏（首领死亡 0.62s）比短震屏（命中 0.055s）注入更多创伤，
     * 否则短促的高频命中在旧模型里反而更明显。</p>
     *
     * <p>方向策略：只有当本次注入量显著大于上一轮主导量时才改写主震方向，
     * 避免 40 个小怪的命中轴把首领的「来源方向」冲掉。</p>
     */
    private void applyShake(float amplitude, float duration, float angle, float bias) {
        if (Float.isNaN(amplitude) || Float.isNaN(duration) || Float.isNaN(angle)) return;
        if (amplitude <= 0f || duration <= 0f) return;
        // 时长归一：0.10s 为基准，长震屏按比例加成，但最多 2.5×（防止史诗演出把创伤长期锁在 1）
        float durScale = duration / 0.10f;
        if (durScale < 0.55f) durScale = 0.55f; else if (durScale > 2.5f) durScale = 2.5f;
        float add = amplitude / SHAKE_NORMALIZE * durScale;
        if (add < 0.012f) add = 0.012f;      // 极轻的调用也要留下一点余震
        trauma += add;
        if (trauma > 1f) trauma = 1f;
        if (add >= traumaPeak - TRAUMA_DIR_EPS) {
            traumaPeak = add;
            shakeAngle = angle;
            shakeBias = bias < 0f ? 0f : (bias > 1f ? 1f : bias);
        }
        // 旧字段只用于 busy() / 诊断，不代表实际幅度
        shakeAmp = amplitude;
        shakeDur = Math.max(shakeDur, duration);
        shakeTime = Math.max(shakeTime, duration);
        shakeSampled = false;
    }

    /** chatter 叠加层：正弦高频小幅抖动，重击时制造「震得发麻」的手感。 */
    private void chatter(float amplitude, float duration, float freq) {
        if (Float.isNaN(amplitude) || Float.isNaN(duration)) return;
        if (amplitude <= 0f || duration <= 0f) return;
        if (amplitude > chatAmp) chatAmp = amplitude;
        chatDur = Math.max(chatDur, duration);
        chatTime = Math.max(chatTime, duration);
        if (freq > 6f) chatFreq = freq;
    }

    /**
     * 顿帧（带双闸预算，清单 #3）。d 秒内 timeScale() 返回 0。
     *
     * <p>无类型信息的兼容入口：按「首领普通受击」档处理，仍然受两份约束 ——
     * ① 相邻顿帧至少间隔 {@link #HS_MIN_GAP}；② 任意 1 秒窗口总量 ≤ {@link #HS_BUDGET_MAX}。
     * 因此后期暴击构筑疯狂触发时不会把游戏拖成幻灯片。</p>
     */
    void hitstop(float duration) {
        hitstopFor(HS_BOSS, duration);
    }

    /**
     * 按目标类型申请顿帧，并通过「最短间隔 + 每秒滑动窗口」双闸。
     *
     * <p>三道闸门的顺序有讲究：</p>
     * <ol>
     *   <li><b>档位上限</b>（单次最多几帧）—— 保证单次打击不会长到脱离手感；</li>
     *   <li><b>最短间隔 60ms</b> —— 同一帧里多个 AoE 连续命中时不叠加冻结；</li>
     *   <li><b>每秒预算 120ms</b> —— 滑动窗口给出硬上界，build 再强也最多冻 7 帧/秒。</li>
     * </ol>
     * 任何一道被拒绝都静默返回 0，调用方无需分支。
     *
     * @param target {@link #HS_TRASH} 杂兵（永远 0 帧）/ {@link #HS_ELITE} 精英 /
     *               {@link #HS_BOSS} 首领受击 / {@link #HS_PHASE} 阶段转换 / {@link #HS_PLAYER} 玩家事件
     * @param duration 期望时长（秒），内部按该档位上限与剩余预算截断
     * @return 实际生效的时长（秒）；0 表示被间隔或预算拒绝
     */
    float hitstopFor(int target, float duration) {
        if (Float.isNaN(duration) || duration <= 0f) return 0f;
        if (target < 0) target = HS_TRASH;
        if (target >= HS_CAP.length) target = HS_BOSS;
        float cap = HS_CAP[target];
        if (cap <= 0f) return 0f;                       // 杂兵：绝不冻结世界
        // 闸 1：最短间隔。刚冻过就不许再冻，把连续命中合并成一次「打击感」。
        // 例外：HS_PHASE（首领死亡 / 阶段转换）是手动编排的演出，允许在 0.2s 内连发；
        // 它依然受闸 2 的每秒预算约束，所以不会失控。
        if (target != HS_PHASE && clock - hsLastAt < HS_MIN_GAP) return 0f;
        float want = duration > cap ? cap : duration;
        // 闸 2：滑动窗口预算（剩余 = 上限 - 最近 1 秒内已消耗）
        float left = HS_BUDGET_MAX - hsWindowSum;
        if (left <= 0.001f) return 0f;
        float applied = want > left ? left : want;
        // 记账：写入当前桶 + 窗口总和
        hsBucket[hsBucketIdx] += applied;
        hsWindowSum += applied;
        hsLastAt = clock;
        hitStop = Math.max(hitStop, applied);
        hitstopApplied = applied;
        return applied;
    }

    /**
     * 清空顿帧滑动窗口（阶段切换 / 换房 / 重开时调用）。
     *
     * <p>为什么需要：首领死亡这类演出会在 0.2s 内连续申请两次大额度顿帧，
     * 如果窗口里还残留上一场战斗的消耗，演出会被静默拒绝。</p>
     */
    void resetHitstopBudget() {
        for (int i = 0; i < HS_BUCKETS; i++) hsBucket[i] = 0f;
        hsBucketIdx = 0;
        hsBucketClock = 0f;
        hsWindowSum = 0f;
        hsLastAt = -99f;
        hitStop = 0f;
        hitstopApplied = 0f;
    }

    /** 顿帧 + 慢放尾巴：freeze 秒冻结之后，慢放 slow 秒（scale 倍速）。 */
    void hitstopSlow(float freeze, float slow, float scale) {
        hitstopFor(HS_PLAYER, freeze);
        if (Float.isNaN(slow) || Float.isNaN(scale)) return;
        if (slow <= 0f) return;
        if (scale < 0.08f) scale = 0.08f;
        if (scale > 1f) scale = 1f;
        if (scale >= 1f) return;
        slowScale = scale;
        slowDur = slow;
        slowTime = Math.max(slowTime, slow);
    }

    /**
     * 当前时间缩放：冻结期间 0，慢放期间 slowScale∈[0.08,1)，否则严格 1.0。
     * <p>这是模拟是否被冻结的唯一开关，任何路径都必须回到 1.0。</p>
     */
    float timeScale() {
        if (hitStop > 0f) return 0f;
        if (slowTime > 0f) {
            float s = slowScale;
            if (s < 0.08f) s = 0.08f; else if (s > 1f) s = 1f;
            return s;
        }
        return 1f;
    }

    /** 慢放是否正在生效。 */
    boolean slowMotion() { return slowTime > 0f; }

    /**
     * 每帧采样一次震屏偏移：同帧内多次读取 shakeX/shakeY 得到同一个值。
     *
     * <p>创伤模型：位移 = trauma² × MAX × noise，noise 是一条 <b>每帧重新采样</b>
     * 的确定性伪随机序列（{@link #noiseState} xorshift64，不是 Math.random）。
     * 用平方而不是线性，是小击/大击分层的全部秘密：trauma 0.3 只有 9% 的位移，
     * trauma 0.9 有 81% —— 两者相差 9 倍，而线性只有 3 倍。</p>
     *
     * <p>每帧换随机值而不是「一次随机、整段保持」，是因为 60fps 下的逐帧抖动
     * 本身就是震屏的观感来源；若保持不变，屏幕只会整体平移一次。</p>
     */
    private void sampleShake() {
        if (shakeSampled) return;
        shakeSampled = true;
        float sx = 0f, sy = 0f;
        if (trauma > 0.0001f) {
            // 逐帧推进噪声（与本帧 shakeX/shakeY 的多次调用解耦：只在首次采样时推进）
            long ns = noiseState;
            ns ^= ns << 13; ns ^= ns >>> 7; ns ^= ns << 17; noiseState = ns;
            noiseX = (float) ((ns >>> 40) & 0xFFFF) / 32768f - 1f;   // [-1,1]
            noiseY = (float) ((ns >>> 16) & 0xFFFF) / 32768f - 1f;

            float t2 = trauma * trauma;                 // 核心：平方曲线
            float mag = t2 * SHAKE_MAX_PX;
            // 主轴（命中方向）始终满幅；垂直方向按 bias 抑制，
            // bias=0 时退化成两轴对称抖动（旧行为），bias=1 时只在命中轴上弹。
            float along = noiseX * mag;
            float side = noiseY * mag * (1f - shakeBias) * 0.8f;
            float cs = (float) Math.cos(shakeAngle), sn = (float) Math.sin(shakeAngle);
            sx += cs * along - sn * side;
            sy += sn * along + cs * side;
        }
        if (chatTime > 0f) {
            float k = chatTime / Math.max(0.001f, chatDur);
            float a = chatAmp * k * k;
            float t = clock * chatFreq * 6.2831853f;
            sx += (float) Math.sin(t) * a;
            sy += (float) Math.cos(t * 1.37f + 1.1f) * a * 0.8f;
        }
        float m = shakeIntensity;
        if (m < 0f) m = 0f; else if (m > 1f) m = 1f;
        shakeOutX = sx * m;
        shakeOutY = sy * m;
    }

    /** 本帧震屏水平偏移（同帧稳定）。 */
    float shakeX() { sampleShake(); return shakeOutX; }

    /** 本帧震屏垂直偏移（同帧稳定）。 */
    float shakeY() { sampleShake(); return shakeOutY; }

    /** 调用方音量（设置里的 shakeLevel/100）：0 完全静音，1 满强度。 */
    void setShakeIntensity(float v) {
        if (Float.isNaN(v)) return;
        if (v < 0f) v = 0f; else if (v > 1f) v = 1f;
        shakeIntensity = v;
    }

    /** 兼容契约写法：out[0]=dx, out[1]=dy。 */
    void screenShakeOffset(float[] out) {
        if (out == null || out.length < 2) return;
        out[0] = shakeX();
        out[1] = shakeY();
    }

    /**
     * 全屏闪光（带无障碍限制，清单附录 2 / WCAG 2.3.1）。
     *
     * <p>三重限制：① 峰值 alpha 上限 {@link #FLASH_ALPHA_CAP}（0.35）；
     * ② 每秒最多 {@link #FLASH_MAX_PER_SEC} 次；③ 两次之间至少 {@link #FLASH_MIN_INTERVAL}。
     * 强度再乘 {@link #setFlashIntensity 用户闪光滑条}。被拒绝时静默忽略，不抛异常。</p>
     *
     * @param color    闪光颜色（只取 RGB；红色请优先用 {@link #vignettePulse} 的边缘形式）
     * @param duration 期望时长（秒），会被压缩到安全窗口内
     */
    void flash(int color, float duration) {
        flashScaled(color, duration, 1f);
    }

    /**
     * 带峰值缩放的闪光（内部 / 击杀确认用）。
     *
     * <p>为什么需要单独一个缩放参数：首领击杀只允许「一帧低 alpha 白闪」，
     * 直接复用 {@link #flash} 会拿到 0.35 的峰值，在暗场里非常刺眼。
     * 缩放系数先乘用户强度，再统一走 {@link #FLASH_ALPHA_CAP} 的 WCAG 上限。</p>
     *
     * @param scale 0..1 的峰值缩放（{@link #KILL_BOSS_FLASH_SCALE} = 0.30）
     */
    private void flashScaled(int color, float duration, float scale) {
        if (Float.isNaN(duration) || duration <= 0f) return;
        if (Float.isNaN(scale) || scale <= 0f) return;
        if (flashIntensity <= 0.01f) return;                       // 用户关闭闪光
        if (clock - flashLastAt < FLASH_MIN_INTERVAL) return;      // 太密：丢弃
        if (clock - flashWinStart >= 1f) { flashWinStart = clock; flashWinCount = 0; }
        if (flashWinCount >= FLASH_MAX_PER_SEC) return;            // 一秒内已闪够：丢弃
        flashWinCount++;
        flashLastAt = clock;
        float dur = duration > 0.5f ? 0.5f : duration;             // 单次不超过 0.5s
        float peak = 0.85f * flashIntensity * (scale > 1f ? 1f : scale);
        if (peak > 1f) peak = 1f;
        flashPeak = peak;
        flashColor = color;
        flashMax = dur;
        flashTime = Math.max(flashTime, dur);
    }

    /**
     * 用户闪光强度：设置里的「画面闪光」滑条（prefs 键 {@code flash}，0..100 → 0..1）。
     * 0 = 完全关闭全屏闪光（暗角脉冲仍保留，因为它只影响边缘）。
     */
    void setFlashIntensity(float v) {
        if (Float.isNaN(v)) return;
        if (v < 0f) v = 0f; else if (v > 1f) v = 1f;
        flashIntensity = v;
    }

    /** 当前闪光强度（诊断 / 设置页回显）。 */
    float flashIntensity() { return flashIntensity; }

    /**
     * 粒子密度：设置里的「粒子密度」滑条（prefs 键 {@code particles}，0..100 → 0..1）。
     * <p>与画质档位解耦 —— 玩家可以为了舒适度降粒子而不同时降画面质量。</p>
     */
    void setParticleDensity(float v) {
        if (Float.isNaN(v)) return;
        if (v < 0f) v = 0f; else if (v > 1f) v = 1f;
        particleDensity = v;
        globalScale = 0.25f + 0.75f * v;   // 最低保留 25% 关键反馈，避免完全没打击感
    }

    /** 当前粒子密度 0..1。 */
    float particleDensity() { return particleDensity; }

    /**
     * 伤害数字密度：设置里的「伤害数字」滑条（prefs 键 {@code numbers}，0..100 → 0..1）。
     * 0 = 完全不显示飘字；1 = 上限 12 个。
     */
    void setNumberDensity(float v) {
        if (Float.isNaN(v)) return;
        if (v < 0f) v = 0f; else if (v > 1f) v = 1f;
        numberDensity = v;
        numberCap = Math.round(NUMBER_HARD_CAP * v);
    }

    /** 当前数字密度 0..1。 */
    float numberDensity() { return numberDensity; }

    /** 最近 1 秒窗口内剩余的顿帧额度（秒），设置页 / 诊断用。 */
    float hitstopBudget() {
        float left = HS_BUDGET_MAX - hsWindowSum;
        return left < 0f ? 0f : left;
    }

    /** 屏幕暗角脉冲：受击时用红色、中毒用绿色等。strength 0..1，duration 秒。 */
    void vignettePulse(int color, float strength, float duration) {
        if (Float.isNaN(strength) || Float.isNaN(duration) || duration <= 0f) return;
        if (strength > 1f) strength = 1f;
        if (strength <= 0f) return;
        vigColor = color | 0xFF000000;
        vigStrength = Math.max(vigStrength, strength);
        vigMax = duration;
        vigTime = Math.max(vigTime, duration);
    }

    /** 常驻暗角强度（默认 0.25；首领战建议 0.45）。 */
    void setVignette(float strength) {
        if (Float.isNaN(strength)) return;
        vignetteBase = Math.max(0f, Math.min(1f, strength));
    }

    /** 粒子数量预算：0.25（最低）~1.0（默认）。不影响时间缩放。 */
    void setIntensity(float v) {
        setParticleDensity(v);
    }

    /** 黑幕淡出到全黑：duration 秒后 fade() 达到 1。 */
    void fadeOut(float duration) {
        float d = (Float.isNaN(duration) || duration <= 0.001f) ? 0.001f : duration;
        fadeTarget = 1f; fadeRate = 1f / d;
    }

    /** 黑幕淡入到透明：duration 秒后 fade() 回到 0。 */
    void fadeIn(float duration) {
        float d = (Float.isNaN(duration) || duration <= 0.001f) ? 0.001f : duration;
        fadeTarget = 0f; fadeRate = 1f / d;
    }

    /** 当前黑屏强度 0..1。 */
    float fade() { return fadeLevel; }

    /** 是否还有活动特效（供外部决定是否重绘/跳过优化）。 */
    boolean busy() {
        return pAlive > 0 || mAlive > 0 || nAlive > 0 || bAlive > 0 || cueAlive > 0
                || trauma > 0.0001f || chatTime > 0f || hitStop > 0f || slowTime > 0f
                || flashTime > 0f || vigTime > 0f;
    }

    // =====================================================================
    // 触发接口 —— 粒子
    // =====================================================================
    /** 爆发粒子：count 个随机方向圆点，speed 基础速度像素/秒，life 寿命秒。 */
    void burst(float x, float y, int count, int color, float speed, float life) {
        burstDir(x, y, count, color, speed, life, NO_DIR);
    }

    /** 有向爆发：dir 为喷射主方向（弧度），粒子在该方向 ±0.7rad 内散开。 */
    void burstDir(float x, float y, int count, int color, float speed, float life, float dir) {
        if (count <= 0) return;
        count = (int) (count * globalScale + 0.5f);
        if (count > 120) count = 120;
        if (count <= 0) return;
        if (speed <= 0f) speed = 120f;
        if (life <= 0f) life = 0.4f;
        for (int i = 0; i < count; i++) {
            float an = (dir == NO_DIR) ? rnd() * 6.2831853f : dir + rnd2() * 0.7f;
            float sp = speed * (0.45f + rnd() * 0.8f);
            spawnP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    2.2f + rnd() * 2.8f, -1.5f, 210f, 2.4f,
                    life * (0.7f + rnd() * 0.6f), color, P_DOT);
        }
    }

    /** 单颗火花：短促速度线，size 为粗细基准。 */
    void spark(float x, float y, int color, float size) {
        sparkDir(x, y, color, size, NO_DIR);
    }

    /** 有向火花：沿命中方向擦出。 */
    void sparkDir(float x, float y, int color, float size, float dir) {
        if (size <= 0f) size = 3f;
        float an = (dir == NO_DIR) ? rnd() * 6.2831853f : dir + rnd2() * 0.85f;
        float sp = 170f + rnd() * 240f;
        int i = spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                size, -size * 1.6f, 60f, 6f, 0.16f + rnd() * 0.14f, color, P_STREAK,
                PRIO_TRASH);
        if (i >= 0) ptint[i] = 0.4f;
    }

    /**
     * 关键火花（{@link #PRIO_CRITICAL}）：首领预警、终极、阶段转换专用。
     * <p>池满时也不会被杂兵命中火花挤掉 —— 首领的预警信号必须活着（清单 #19）。</p>
     *
     * @param x,y   位置
     * @param color 颜色
     * @param size  粗细基准
     */
    void criticalSpark(float x, float y, int color, float size) {
        if (size <= 0f) size = 4f;
        float an = rnd() * 6.2831853f;
        float sp = 60f + rnd() * 120f;
        int i = spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                size, -size * 0.8f, -20f, 2f, 0.5f + rnd() * 0.5f, color, P_EMBER,
                PRIO_CRITICAL);
        if (i >= 0) ptint[i] = 0.5f;
    }

    /**
     * 关键爆发（{@link #PRIO_CRITICAL}）：首领预警 / 阶段转换的粒子环。
     *
     * @param x,y   中心
     * @param count 数量
     * @param color 颜色
     * @param speed 扩散速度
     * @param life  寿命
     * @param dir   方向（{@link #NO_DIR} 表示四周）
     */
    void criticalBurst(float x, float y, int count, int color, float speed, float life, float dir) {
        if (count <= 0) return;
        if (count > 80) count = 80;
        if (speed <= 0f) speed = 150f;
        if (life <= 0f) life = 0.5f;
        for (int i = 0; i < count; i++) {
            float an = (dir == NO_DIR) ? rnd() * 6.2831853f : dir + rnd2() * 0.8f;
            float sp = speed * (0.5f + rnd() * 0.9f);
            spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    2.4f + rnd() * 3f, -1f, 40f, 1.6f,
                    life * (0.8f + rnd() * 0.5f), color, P_EMBER, PRIO_CRITICAL);
        }
    }

    /** 扩散圆环：radius 起始半径，life 寿命。 */
    void ring(float x, float y, float radius, int color, float life) {
        if (radius <= 0f) radius = 8f;
        if (life <= 0f) life = 0.25f;
        spawnP(x, y, 0f, 0f, radius, 0f, 0f, 0f, life, color, P_RING);
    }

    /** 压扁的椭圆冲击圈（横向拉长，像「削」过去）。ratio 为纵向比例。 */
    void ringFlat(float x, float y, float radius, float ratio, int color, float life) {
        if (radius <= 0f) radius = 10f;
        if (ratio <= 0f) ratio = 0.45f;
        int i = spawnP(x, y, 0f, 0f, radius, 0f, 0f, 0f,
                life <= 0f ? 0.12f : life, color, P_RING);
        if (i >= 0) ptint[i] = ratio;
    }

    /** 弧形刀光：angle 弧度方向，len 弧长（像素）。 */
    void slash(float x, float y, float angle, float len, int color) {
        if (len <= 0f) len = 60f;
        int i = spawnP(x, y, 0f, 0f, len, 0f, 0f, 0f, 0.17f, color, P_ARC);
        if (i >= 0) pseed[i] = angle;
    }

    /** 尾迹点：跟随实体的柔光小点。 */
    void trail(float x, float y, int color, float size) {
        if (size <= 0f) size = 4f;
        spawnP(x + rnd2() * 2f, y + rnd2() * 2f, rnd2() * 16f, rnd2() * 16f,
                size, -size * 0.9f, 0f, 1.5f, 0.28f, alpha(color, 0.75f), P_SMOKE);
    }

    /** 元素尾迹：按元素换形状与运动（火上升、冰缓漂、雷短促、虚空内吸）。 */
    void trailElement(float x, float y, int element, float size) {
        if (size <= 0f) size = 4f;
        switch (element) {
            case EL_FIRE:
                spawnP(x + rnd2() * 3f, y + rnd2() * 3f, rnd2() * 14f, -30f - rnd() * 40f,
                        size * 0.9f, -size * 0.6f, -20f, 1.2f, 0.26f + rnd() * 0.14f,
                        rnd() < 0.4f ? 0xffffd490 : 0xffff8a4a, P_FLAME);
                break;
            case EL_FROST:
                spawnP(x + rnd2() * 3f, y + rnd2() * 3f, rnd2() * 8f, rnd2() * 8f - 6f,
                        size * 0.7f, -size * 0.2f, 0f, 0.9f, 0.4f + rnd() * 0.2f,
                        0xffbfe4ff, P_CRYSTAL);
                break;
            case EL_BOLT:
                spawnP(x, y, rnd2() * 40f, rnd2() * 40f,
                        size * 0.6f, -size, 0f, 4f, 0.12f, 0xffd8f6ff, P_STREAK);
                break;
            case EL_VOID:
                spawnP(x + rnd2() * 4f, y + rnd2() * 4f, rnd2() * 6f, rnd2() * 6f,
                        size * 0.8f, -size * 0.3f, 0f, 1f, 0.34f, 0x889a6cff, P_SMOKE);
                break;
            default:
                trail(x, y, 0xffe8dcc0, size * 0.8f);
                break;
        }
    }

    /** 血点飞溅：dir 为水平方向意图（-1/0/1），color 由调用方给。 */
    void blood(float x, float y, int dir, int color) {
        int n = 6 + (int) (rnd() * 4f);
        for (int i = 0; i < n; i++) {
            float vx = rnd2() * 190f + dir * 110f;
            float vy = -90f - rnd() * 190f;
            spawnP(x, y, vx, vy, 2f + rnd() * 2.6f, 0f, 680f, 1.1f,
                    0.35f + rnd() * 0.3f, color, P_DOT);
        }
    }

    /** 契约默认血点颜色。 */
    void blood(float x, float y, int dir) { blood(x, y, dir, 0xffc2473f); }

    /** 碎片飞溅：敌人死亡时的彩色三角碎片。 */
    void shard(float x, float y, int color) {
        spawnP(x, y, rnd2() * 230f, -120f - rnd() * 200f,
                4f + rnd() * 4.5f, 0f, 560f, 1.1f, 0.55f + rnd() * 0.45f, color, P_SHARD);
    }

    /** 有向碎片：沿击杀方向甩出，dir 弧度。 */
    void shardTowards(float x, float y, int color, float dir, float power) {
        if (power <= 0f) power = 1f;
        float an = dir + rnd2() * 0.7f;
        float sp = (140f + rnd() * 190f) * power;
        spawnP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp - 60f,
                3.4f + rnd() * 4.2f, 0f, 520f, 1.15f, 0.5f + rnd() * 0.45f, color, P_SHARD);
    }

    /** 烟雾：缓慢上升并膨胀的柔光团。 */
    void smoke(float x, float y, int color, float size) {
        if (size <= 0f) size = 8f;
        spawnP(x + rnd2() * 4f, y + rnd2() * 4f, rnd2() * 18f, -16f - rnd() * 30f,
                size, size * 0.9f, -8f, 1.4f, 0.65f + rnd() * 0.5f, alpha(color, 0.32f), P_SMOKE);
    }

    /** 灼烧：橙红火焰粒子向上飘（持续调用即连续）。 */
    void burn(float x, float y, float size) {
        if (size <= 0f) size = 5f;
        int warm = rnd() < 0.35f ? 0xffffe0a8 : (rnd() < 0.5f ? 0xffffab6a : 0xffe2653f);
        spawnP(x + rnd2() * size, y + rnd2() * size * 0.6f,
                rnd2() * 18f, -40f - rnd() * 70f,
                size * (0.5f + rnd() * 0.6f), -size * 0.35f, -22f, 0.9f,
                0.28f + rnd() * 0.3f, warm, P_FLAME);
    }

    /** 冰缓：淡蓝六角冰晶碎片 + 地面霜痕。 */
    void frost(float x, float y, float r) {
        if (r <= 0f) r = 24f;
        for (int i = 0; i < 5; i++) {
            float an = rnd() * 6.2831853f, sp = 60f + rnd() * 130f;
            spawnP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    3f + rnd() * 3f, 0f, 160f, 1.6f, 0.4f + rnd() * 0.25f,
                    0xffbfe4ff, P_CRYSTAL);
        }
        spawnM(x, y + r * 0.35f, r, r, 1.1f, 0xff8fc8ee, M_FROST);
    }

    /** 虚空：紫黑向内收缩的环 + 向心碎屑。 */
    void voidPull(float x, float y, float r) {
        if (r <= 0f) r = 60f;
        spawnM(x, y, r, r * 0.05f, 0.45f, 0xffb79bff, M_VOID);
        spawnP(x, y, 0f, 0f, r * 0.9f, -r * 0.4f, 0f, 0f, 0.4f, 0x889a6cff, P_RING);
        for (int i = 0; i < 6; i++) {
            float an = rnd() * 6.2831853f;
            float rad = r * (0.7f + rnd() * 0.5f);
            float c = (float) Math.cos(an), s = (float) Math.sin(an);
            spawnP(x + c * rad, y + s * rad, -c * r * 1.6f, -s * r * 1.6f,
                    1.8f + rnd() * 2f, -1.2f, 0f, 0.6f, 0.42f, 0xff9d7ce8, P_DOT);
        }
    }

    /** 直线光束。 */
    void beam(float x1, float y1, float x2, float y2, int color, float life) {
        spawnB(x1, y1, x2, y2, life <= 0f ? 0.12f : life, color, B_BEAM);
    }

    /** 锯齿闪电：8 段随机折线，life 建议 0.06~0.1。 */
    void lightning(float x1, float y1, float x2, float y2, int color, float life) {
        spawnB(x1, y1, x2, y2, life <= 0f ? 0.08f : life, color, B_BOLT);
    }

    /** 分叉闪电：主链 + forks 条分支，用于雷元素重击。 */
    void boltFork(float x1, float y1, float x2, float y2, int color, float life, int forks) {
        int i = spawnB(x1, y1, x2, y2, life <= 0f ? 0.09f : life, color, B_FORK);
        if (i >= 0) bfork[i] = forks < 1 ? 1 : (forks > 3 ? 3 : forks);
    }

    /** 大范围冲击波：扩散到 maxR 的粗环。 */
    void shockwave(float x, float y, float maxR, int color, float life) {
        if (maxR <= 0f) maxR = 100f;
        spawnM(x, y, 10f, maxR, life <= 0f ? 0.45f : life, color, M_WAVE);
    }

    /** 锐利冲击环：细、高对比、扩散快，用于暴击与阶段转换。 */
    void shockRing(float x, float y, float maxR, int color, float life) {
        if (maxR <= 0f) maxR = 80f;
        spawnM(x, y, 4f, maxR, life <= 0f ? 0.22f : life, color, M_SHOCK);
    }

    /** 地面贴花：带符文的圆形印记。 */
    void groundMark(float x, float y, float r, int color, float life) {
        if (r <= 0f) r = 30f;
        spawnM(x, y, r, r, life <= 0f ? 1.2f : life, color, M_RUNE);
    }

    /** 血渍 / 焦痕：贴地椭圆，寿命较长，压在地面层。 */
    void stain(float x, float y, float r, int color, float life) {
        if (r <= 0f) r = 18f;
        spawnM(x, y, r, r, life <= 0f ? 1.4f : life, color, M_STAIN);
    }

    // =====================================================================
    // 敌人本体反馈（清单 #1 头号项 / #6 预警填充）
    // =====================================================================
    /**
     * 敌人本体受击闪白 —— 打击感的头号来源。
     *
     * <p>为什么不是「画个环」：60 个敌人的屏幕上圆环根本看不清，只有让<b>整个剪影</b>
     * 变白才读得出「打中了」。实现上复用同一套画笔：在敌人所在位置叠一个按体型计算的
     * 加色多边形（ADD 混合），强度随 {@code e.hit} 衰减 —— 零 Bitmap、零 saveLayer、
     * 零 new，60 个敌人同屏也只有 60 次 drawPath。</p>
     *
     * @param c     画布（战斗区坐标系）
     * @param x     敌人中心 x
     * @param y     敌人中心 y
     * @param size  敌人显示宽度（与 Art.enemy 的 width 同源）
     * @param k     闪白强度 0..1（0 = 不画）；调用方传 e.hit / 0.10f 的钳制值
     */
    void enemyFlash(Canvas c, float x, float y, float size, float k) {
        if (c == null) return;
        if (k <= 0.01f) return;
        if (k > 1f) k = 1f;
        if (size <= 0f) return;
        Paint p = paint;
        // ADD 混和：白色叠加在精灵上 => 剪影提亮，不遮住轮廓细节
        p.setStyle(Paint.Style.FILL);
        p.setXfermode(ADD_MODE);
        p.setColor(alpha(0xffffffff, k * 0.72f));
        // 菱形剪影：比矩形更贴合像素精灵的轮廓，且 4 个顶点足够便宜
        float w = size * 0.30f;
        float h = size * 0.34f;
        path.reset();
        path.moveTo(x, y - h);
        path.lineTo(x + w, y - h * 0.12f);
        path.lineTo(x + w * 0.72f, y + h * 0.55f);
        path.lineTo(x, y + h * 0.72f);
        path.lineTo(x - w * 0.72f, y + h * 0.55f);
        path.lineTo(x - w, y - h * 0.12f);
        path.close();
        c.drawPath(path, p);
        // 再叠一个小一圈的亮芯，让「白」不是均匀的灰白而是有热点
        p.setColor(alpha(0xfffff6e2, k * 0.55f));
        c.drawCircle(x, y - h * 0.18f, Math.max(2f, w * 0.52f), p);
        p.setXfermode(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xffffffff);
    }

    /**
     * 预警填充扇形（清单 #6）：把危险圈从「移动的描边」升级成「会涨满的面积」。
     *
     * <p>60 个描边在屏幕上等于没有信息；填充面积是另一个视觉通道，能穿过混乱活下来。
     * 进度用 {@code Game.Enemy.windupProgress()}（0..1），越接近 1 越危险。</p>
     *
     * @param c        画布（战斗区坐标系）
     * @param x        圆心 x
     * @param y        圆心 y
     * @param r        半径（通常是 e.r + 10 + t*14）
     * @param progress 蓄力进度 0..1，&lt;0 视为无预警
     * @param color    颜色（普通 0x99f2bd8c / 致命更红）
     * @param threat   危险等级 0 普通 / 1 重伤 / 2 致命，用于加粗描边
     */
    void telegraphFill(Canvas c, float x, float y, float r, float progress,
                       int color, int threat) {
        if (c == null || r <= 0f) return;
        if (Float.isNaN(progress) || progress < 0f) return;
        if (progress > 1f) progress = 1f;
        Paint p = paint;
        float a = 0.10f + 0.22f * progress;          // 面积随时间变得更实
        // 1) 填充扇形：从 12 点方向顺时针扫过 progress*360°
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(color, a));
        if (progress > 0.002f) {
            rect.set(x - r, y - r, x + r, y + r);
            path.reset();
            path.moveTo(x, y);
            path.arcTo(rect, -90f, 360f * progress, false);
            path.close();
            c.drawPath(path, p);
        }
        // 2) 描边：致命预警加粗并提亮
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(threat >= 2 ? 3.4f : threat == 1 ? 2.6f : 2f);
        p.setColor(alpha(threat >= 1 ? lighten(color, 0.28f) : color, 0.55f + 0.4f * progress));
        c.drawCircle(x, y, r, p);
        // 3) 收口环：进度 >70% 时内侧再收一圈，读到「马上要炸」
        if (progress > 0.7f) {
            float k = (progress - 0.7f) / 0.3f;
            p.setStrokeWidth(1.6f);
            p.setColor(alpha(0xffffffff, 0.35f * k));
            c.drawCircle(x, y, r * (1f - 0.35f * k), p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    /** 伤害数字：普通白字上飘，暴击亮色大字 + 描边 + 放射线条（无元素版本）。 */
    void number(float x, float y, float value, boolean crit, int color) {
        number(x, y, value, crit, color, -1);
    }

    /**
     * 伤害数字：普通白字上飘，暴击亮色大字 + 描边 + 放射线条。
     * <p>受 {@link #NUMBER_HARD_CAP 全局同屏上限} 约束：池里已有同级或更多数字时，
     * 优先保留暴击 / 大伤害 —— 普通小伤害直接丢弃，而不是让 40 个数字糊满屏幕。</p>
     */
    void number(float x, float y, float value, boolean crit, int color, int element) {
        numberFor(0, x, y, value, crit, color, element);
    }

    /**
     * 带「归属敌人」的伤害数字入口 —— 同屏合并的关键。
     *
     * <p>合并规则（为什么是 220ms）：一个小怪在弹幕流下每 100~200ms 挨一次打，
     * 若每次都新建数字，同屏上限 12 会在一秒内被同 3 个敌人占满，真正致命的那一下
     * 反而被丢弃。按敌人 id 复用同一个槽位后，屏幕上的数字数量 = 正在挨打的敌人数，
     * 信息量恒定且可读。</p>
     *
     * <p>数值用<b>累加</b>而不是替换：玩家关心的是「这段时间我打了多少」，
     * 替换会让高攻速构筑的数字永远停在单次伤害上，读起来像 DPS 掉了一半。</p>
     *
     * @param enemyId 归属敌人 id（0 = 无归属，走旧的按位置合并逻辑）
     */
    void numberFor(int enemyId, float x, float y, float value, boolean crit,
                   int color, int element) {
        if (Float.isNaN(value) || Float.isNaN(x) || Float.isNaN(y)) return;
        if (numberCap <= 0) return;                    // 用户把「伤害数字」滑条拉到 0
        float v = value < 1f ? 1f : value;
        if (!crit && v < 3f) return;                   // 刮痧数字不刷屏
        // 玩家锚点避让：数字绝不允许压在玩家立绘上（清单「What NOT to do」）
        float sx = avoidPlayerX(x, y);
        float sy = avoidPlayerY(x, y);

        // ── 同一敌人 220ms 内：复用旧槽位，只更新数值并重置寿命 ──
        if (enemyId != 0) {
            int reuse = numberSlotOf(enemyId);
            if (reuse >= 0) {
                nvalue[reuse] += v;
                nlife[reuse] = nmax[reuse];            // 重置寿命：数字「重新跳一下」
                nx[reuse] = sx; ny[reuse] = sy;
                if (crit && nstyle[reuse] != N_CRIT) { // 小伤害升级成暴击：升格样式
                    nstyle[reuse] = N_CRIT;
                    ncolor[reuse] = CRIT_GOLD;
                    nmax[reuse] = nlife[reuse] = 0.7f;
                    npop[reuse] = NPOP_TIME;
                }
                if (crit) {
                    npop[reuse] = NPOP_TIME;
                    critStreaks(x, y, element);
                }
                return;
            }
        }

        // ── 全局同屏上限（清单 #2）──
        if (nAlive >= numberCap) {
            if (!crit) return;                          // 普通数字：满员后直接丢弃
            // 暴击有特权：驱逐一个最旧的普通数字给它腾位
            if (!evictOldestNumber()) return;
        }
        if (crit) {
            // 暴击统一金色（要求），元素色只保留在放射线与火花上
            int idx = spawnN(sx, sy - 8f, rnd2() * 9f, 0.7f, CRIT_GOLD, N_CRIT, null, v);
            if (idx >= 0) {
                npop[idx] = NPOP_TIME;
                nkeyOf[idx] = enemyId;
                bindNumber(enemyId, idx);
            }
            critStreaks(x, y, element);
            return;
        }
        if (enemyId == 0 && mergeNumber(sx, sy, v)) return;   // 旧路径：按位置合并
        int stack = stackAt(sy);
        if (stack > 0) {
            sx += (stack % 2 == 0 ? -1f : 1f) * (7f + stack * 2.5f);
            sy -= Math.min(56f, stack * 17f);   // 往上错开，避免糊成一团
        }
        int c = stack > 2 ? 0xffd4dde4 : 0xfffff5e6;
        int idx = spawnN(sx + rnd2() * 5f, sy - 4f, rnd2() * 5f, 0.52f, c, N_DAMAGE, null, v);
        if (idx >= 0) {
            npop[idx] = 0f;
            nkeyOf[idx] = enemyId;
            bindNumber(enemyId, idx);
        }
    }

    /**
     * 设置「玩家锚点」：伤害数字会主动离开该点 {@link #NUMBER_PLAYER_CLEAR} 像素以上。
     *
     * <p>每帧由 CombatFeedback 更新一次（零分配）。不设置时避让逻辑自动禁用，
     * 因此旧调用方（测试 / harness）行为不变。</p>
     */
    void setNumberAnchor(float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        anchorX = x; anchorY = y; anchorSet = true;
    }

    /** 清除玩家锚点（退出战斗 / 换房时调用）。 */
    void clearNumberAnchor() { anchorSet = false; }

    /** 数字横坐标避让：太靠近玩家时沿「玩家→数字」方向外推。 */
    private float avoidPlayerX(float x, float y) {
        if (!anchorSet) return x;
        float dx = x - anchorX, dy = y - anchorY;
        float d2 = dx * dx + dy * dy;
        if (d2 >= NUMBER_PLAYER_CLEAR * NUMBER_PLAYER_CLEAR) return x;
        float d = (float) Math.sqrt(d2);
        if (d < 0.001f) return x + 18f;                 // 正好重合：往右上推开
        return anchorX + dx / d * (NUMBER_PLAYER_CLEAR + 6f);
    }

    /** 数字纵坐标避让：只在横向推不开（正上/正下）时才纵向补偿。 */
    private float avoidPlayerY(float x, float y) {
        if (!anchorSet) return y;
        float dx = x - anchorX, dy = y - anchorY;
        float d2 = dx * dx + dy * dy;
        if (d2 >= NUMBER_PLAYER_CLEAR * NUMBER_PLAYER_CLEAR) return y;
        float d = (float) Math.sqrt(d2);
        if (d < 0.001f) return y - 22f;
        return anchorY + dy / d * (NUMBER_PLAYER_CLEAR + 6f);
    }

    /** 按敌人 id 查最近一次数字槽位；过期（> 220ms）或已消失返回 -1。 */
    private int numberSlotOf(int enemyId) {
        if (enemyId == 0) return -1;
        int h = enemyId * 0x9E3779B1;
        int i = (h ^ (h >>> 16)) & (NKEY_TABLE - 1);
        for (int n = 0; n < NKEY_PROBE; n++) {
            if (nkeyId[i] == 0) return -1;
            if (nkeyId[i] == enemyId) {
                int slot = nkeySlot[i];
                if (slot < 0 || slot >= MAX_NUMBERS) return -1;
                if (nlife[slot] <= 0f) return -1;                    // 槽位已过期
                if (clock - nkeyAt[i] > NUMBER_MERGE_WINDOW) return -1; // 超过合并窗口
                return slot;
            }
            i = (i + 1) & (NKEY_TABLE - 1);
        }
        return -1;
    }

    /** 记录「该敌人最近一次用了哪个数字槽」。 */
    private void bindNumber(int enemyId, int slot) {
        if (enemyId == 0) return;
        int h = enemyId * 0x9E3779B1;
        int i = (h ^ (h >>> 16)) & (NKEY_TABLE - 1);
        for (int n = 0; n < NKEY_PROBE; n++) {
            if (nkeyId[i] == 0 || nkeyId[i] == enemyId) {
                nkeyId[i] = enemyId;
                nkeySlot[i] = slot;
                nkeyAt[i] = clock;
                return;
            }
            i = (i + 1) & (NKEY_TABLE - 1);
        }
        // 探测范围内全是别的敌人：直接占用第一格，旧关联失效（下一次退化为新建）
        nkeyId[i] = enemyId;
        nkeySlot[i] = slot;
        nkeyAt[i] = clock;
    }

    /**
     * 驱逐一个最旧的普通伤害数字，给暴击腾位置。
     *
     * @return true 表示成功腾出（至少清掉一个普通数字槽）
     */
    private boolean evictOldestNumber() {
        int oldest = -1;
        float best = -1f;
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (nlife[i] <= 0f) continue;
            if (nstyle[i] != N_DAMAGE) continue;        // 只牺牲普通伤害，不动暴击/治疗/字幕
            float age = nmax[i] - nlife[i];
            if (age > best) { best = age; oldest = i; }
        }
        if (oldest < 0) return false;
        nlife[oldest] = 0f;
        nAlive--;
        if (nAlive < 0) nAlive = 0;
        return true;
    }

    /** 治疗数字：绿色 + 「+」。 */
    void heal(float x, float y, float amount) {
        if (Float.isNaN(amount) || amount < 0.5f) return;
        spawnN(x + rnd2() * 3f, y - 4f, rnd2() * 5f, 0.8f, 0xff8fe0a8, N_HEAL, null, amount);
    }

    /** 星尘：金色小字。 */
    void coin(float x, float y, float amount) {
        if (Float.isNaN(amount) || amount < 1f) return;
        if (mergeColorless(x, y, amount, N_COIN)) return;
        spawnN(x + rnd2() * 4f, y - 2f, rnd2() * 6f, 0.72f, 0xffffd98a, N_COIN, null, amount);
    }

    /** 经验：青色小字。 */
    void xp(float x, float y, float amount) {
        if (Float.isNaN(amount) || amount < 1f) return;
        if (mergeColorless(x, y, amount, N_XP)) return;
        spawnN(x + rnd2() * 4f, y - 2f, rnd2() * 6f, 0.68f, 0xff9fe6d8, N_XP, null, amount);
    }

    /** 护盾吸收：蓝白小字。 */
    void shieldNumber(float x, float y, float amount) {
        if (Float.isNaN(amount) || amount < 1f) return;
        spawnN(x + rnd2() * 3f, y - 4f, rnd2() * 5f, 0.62f, 0xffcfe6ff, N_SHIELD, null, amount);
    }

    /** 漂浮文字（如「免疫」「格挡」）。 */
    void text(float x, float y, String s, int color, float life) {
        if (s == null || s.isEmpty()) return;
        spawnN(x, y, rnd2() * 6f, life <= 0f ? 0.9f : life, color, N_STATUS, s, 0f);
    }

    /** 大字幕（屏幕居中偏上的大号提示）。 */
    void banner(float x, float y, String s, int color) {
        if (s == null || s.isEmpty()) return;
        spawnN(x, y, 0f, 1.35f, color, N_BIG, s, 0f);
    }

    /** 同一竖列已有的飘字数量（用于错位）。 */
    private int stackAt(float y) {
        int n = 0;
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (nlife[i] <= 0f || nstyle[i] == N_BIG) continue;
            if (Math.abs(ny[i] - y) < 26f) n++;
        }
        return n;
    }

    /**
     * 合并 0.16s 内、40px 内的同列普通伤害数字，避免群怪同时挨打时糊成一片。
     *
     * @return true 表示已并入既有数字（调用方不要再新建）
     */
    private boolean mergeNumber(float x, float y, float value) {
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (nlife[i] <= 0f || nstyle[i] != N_DAMAGE) continue;
            float age = nmax[i] - nlife[i];
            if (age > 0.16f) continue;
            float dx = nx[i] - x, dy = ny[i] - y;
            if (dx * dx + dy * dy < 40f * 40f) {
                nvalue[i] += value;
                nx[i] = (nx[i] + x) * 0.5f;
                ny[i] = Math.min(ny[i], y);
                float keep = Math.max(nmax[i], 0.55f);
                nmax[i] = keep;
                nlife[i] = Math.max(nlife[i], keep * 0.9f);
                return true;
            }
        }
        return false;
    }

    /**
     * 星尘 / 经验也做一次合并，防止拾取时数字刷屏。
     *
     * @return true 表示已并入既有数字
     */
    private boolean mergeColorless(float x, float y, float value, int style) {
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (nlife[i] <= 0f || nstyle[i] != style) continue;
            if (nmax[i] - nlife[i] > 0.5f) continue;
            float dx = nx[i] - x, dy = ny[i] - y;
            if (dx * dx + dy * dy < 46f * 46f) {
                nvalue[i] += value;
                float keep = Math.max(nmax[i], 0.7f);
                nmax[i] = keep;
                nlife[i] = Math.max(nlife[i], keep * 0.9f);
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // 触发接口 —— 组合手感
    // =====================================================================
    /** 按预设事件触发震屏 + 顿帧。 */
    void juice(int event) { juice(event, 1f); }

    /** 同上，但用 weight（0.15..1）缩放强度：轻击不抢戏，重击更有分量。 */
    void juice(int event, float weight) {
        if (event < 0 || event >= J_AMP.length) return;
        if (Float.isNaN(weight)) weight = 1f;
        if (weight < 0.15f) weight = 0.15f; else if (weight > 1f) weight = 1f;
        float amp = J_AMP[event] * weight;
        applyShake(amp, J_DUR[event], shakeAngle, 0.45f);
        float cm = J_CHAT_MUL[event] * weight;
        if (cm > 0.02f) chatter(amp * cm, Math.min(J_DUR[event] * 1.25f, 0.22f), J_CHAT_FREQ[event]);
        if (J_STOP[event] > 0f) hitstopFor(juiceTarget(event), J_STOP[event] * weight);
    }

    /** 手感预设 → 顿帧目标档位：命中类按普通受击，击杀类按精英/首领。 */
    private static int juiceTarget(int event) {
        switch (event) {
            case J_CRIT: return HS_ELITE;
            case J_KILL: return HS_TRASH;
            case J_ELITE: return HS_ELITE;
            case J_BOSS: return HS_PHASE;
            case J_HURT: case J_DEATH: return HS_PLAYER;
            case J_LEVEL: return HS_PLAYER;
            case J_SKILL: case J_ULT: return HS_PLAYER;
            case J_PHASE: return HS_PHASE;
            default: return HS_BOSS;
        }
    }

    /** 命中反馈（简化入口）。 */
    void impact(float x, float y, int color, boolean crit) {
        impact(x, y, color, crit, NO_DIR, 1f, -1);
    }

    /**
     * 命中反馈完整入口。
     *
     * @param dir      命中方向弧度（{@link #NO_DIR} 表示无方向）
     * @param power    0.2~1.6 的伤害权重，控制粒子数量、火花速度与震屏
     * @param element  元素 0..4（-1 表示未标注）
     */
    void impact(float x, float y, int color, boolean crit, float dir, float power, int element) {
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        if (Float.isNaN(power)) power = 1f;
        if (power < 0.2f) power = 0.2f;
        if (power > 1.6f) power = 1.6f;
        boolean ranged = dir != NO_DIR;
        int el = element < 0 ? EL_PHYS : element;

        // ── 火花：沿命中方向成锥形；小伤害只有 2~3 条 ──
        // 优先级：暴击 = 重要（不会被杂兵火花挤掉），普通命中 = 杂兵（池满时最先牺牲）
        int impactPrio = crit ? PRIO_IMPORTANT : PRIO_TRASH;
        int n = crit ? 8 : (power > 0.7f ? 5 : 2);
        n = (int) (n * globalScale * (0.65f + 0.45f * power) + 0.5f);
        if (n < 2) n = 2; else if (n > 14) n = 14;
        int sparkColor = crit ? elColor(el, true) : color;
        for (int i = 0; i < n; i++) {
            float an = ranged ? dir + rnd2() * 0.9f : rnd() * 6.2831853f;
            float sp = (150f + rnd() * 230f) * (0.75f + 0.35f * power);
            int si = spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    1.8f + rnd() * (crit ? 3.2f : 2f), -1.2f, 120f, 5f,
                    (crit ? 0.18f : 0.10f) + rnd() * 0.1f,
                    i % 4 == 0 ? 0xfffff2c4 : sparkColor, P_STREAK, impactPrio);
            if (si >= 0) ptint[si] = 0.4f + rnd() * 0.5f;
        }

        // ══════════════════════════════════════════════════════════
        //  冲击三层（v2.3）：亮芯 → 定向喷溅（上面那圈） → 慢速余烬
        //
        //  为什么必须分三层：单层火花在 60 敌同屏时会被对手的粒子淹没；
        //  三种速度/寿命完全不同的粒子叠在一起，人眼才能在混乱里分离出「这一下打中了」。
        //  层 1（亮芯）0.09s，负责「帧内闪」；层 2（喷溅）0.1~0.28s，负责方向；
        //  层 3（余烬）0.5~1.0s，负责「余韵」，也是唯一在命中后仍留在屏幕上的信号。
        // ══════════════════════════════════════════════════════════
        // 层 1：亮芯 —— 单个高亮大点，寿命极短（比火花更快消失，形成「闪一下」）
        int core = spawnPP(x, y, 0f, -12f,
                3.2f + 2.4f * power, -9f, 0f, 0f, 0.09f,
                crit ? 0xffffffe0 : lighten(sparkColor, 0.65f), P_SPARK,
                crit ? PRIO_IMPORTANT : PRIO_NORMAL);
        if (core >= 0) ptint[core] = 1.0f;
        // 层 3：余烬 —— 慢速上浮，寿命长，把「这里刚被打中」延续到下一次命中
        int emberN = crit ? 2 : 1;
        for (int i = 0; i < emberN; i++) {
            spawnPP(x + rnd2() * 6f, y + rnd2() * 4f,
                    rnd2() * 22f, -26f - rnd() * 34f,
                    1.6f + rnd() * 1.6f, -0.5f, -14f, 0.9f,
                    0.55f + rnd() * 0.45f,
                    crit ? 0xffffce7a : lighten(color, 0.15f), P_EMBER, impactPrio);
        }

        // ── 冲击圈：按元素换形状 ──
        float r = crit ? 20f + 12f * power : 9f + 8f * power;
        switch (el) {
            case EL_BOLT:
                ring(x, y, r * 0.9f, crit ? 0xffe8fbff : 0xccd8f6ff, 0.1f);
                for (int i = 0; i < (crit ? 3 : 1); i++) {
                    float an = rnd() * 6.2831853f;
                    boltFork(x, y, x + (float) Math.cos(an) * (34f + rnd() * 26f),
                            y + (float) Math.sin(an) * (34f + rnd() * 26f),
                            0xffbfeaff, 0.085f, 2);
                }
                break;
            case EL_FIRE:
                ring(x, y, r * 0.85f, crit ? 0xffffd58a : 0xccffb785, 0.11f);
                break;
            case EL_FROST:
                ringFlat(x, y, r * 1.1f, 0.5f, crit ? 0xffe8f8ff : 0xccdff3ff, 0.13f);
                break;
            case EL_VOID:
                ring(x, y, r * 0.8f, crit ? 0xffd6b8ff : 0xccb79bff, 0.12f);
                break;
            default:
                ringFlat(x, y, r * 1.15f, crit ? 0.42f : 0.62f,
                        crit ? 0xfffff0c0 : 0xccfff4e2, crit ? 0.14f : 0.1f);
                break;
        }
        if (crit) {
            ring(x, y, r * 1.6f, 0x99ffffff, 0.18f);
            spawnM(x, y, r * 0.5f, r * 2.4f, 0.2f, 0xffffe9c0, M_SHOCK);
        }

        // ── 元素附加表现 ──
        switch (el) {
            case EL_FIRE:
                for (int i = 0; i < (crit ? 5 : 2); i++)
                    spawnP(x + rnd2() * 6f, y + rnd2() * 5f, rnd2() * 26f, -50f - rnd() * 60f,
                            3f + rnd() * 2.4f, -2f, -30f, 1.1f, 0.3f + rnd() * 0.22f,
                            rnd() < 0.45f ? 0xffffd490 : 0xffff8a4a, P_FLAME);
                break;
            case EL_FROST:
                spawnM(x, y + 6f, r * 1.1f, r * 1.1f, 0.55f, 0xff8fc8ee, M_FROST);
                for (int i = 0; i < (crit ? 4 : 2); i++) {
                    float an = rnd() * 6.2831853f, sp = 40f + rnd() * 90f;
                    spawnP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                            2.4f + rnd() * 2.2f, 0f, 120f, 1.7f, 0.45f + rnd() * 0.2f,
                            0xffcfe9ff, P_CRYSTAL);
                }
                break;
            case EL_VOID:
                for (int i = 0; i < (crit ? 5 : 3); i++) {
                    float an = rnd() * 6.2831853f, rad = 12f + rnd() * 18f;
                    float c = (float) Math.cos(an), s = (float) Math.sin(an);
                    spawnP(x + c * rad, y + s * rad, -c * 90f, -s * 90f,
                            1.6f + rnd() * 1.8f, -0.8f, 0f, 1.4f, 0.34f, 0xcc9d7ce8, P_DOT);
                }
                if (crit) spawnM(x, y, r * 2.2f, 6f, 0.3f, 0xffb79bff, M_VOID);
                break;
            default:
                if (crit) for (int i = 0; i < 3; i++)
                    spawnP(x, y, rnd2() * 160f, -60f - rnd() * 120f,
                            2.4f + rnd() * 2.4f, 0f, 420f, 1.2f, 0.32f, 0xffffe3bd, P_SHARD);
                break;
        }

        // ── 震动：有向 + 力度分层；暴击额外 chatter 与短闪 ──
        float amp = (crit ? 3.6f : 1.4f) * (0.7f + 0.4f * power);
        float dur = crit ? 0.1f : 0.055f;
        if (ranged) applyShake(amp, dur, dir, 0.68f);
        else applyShake(amp, dur, -1.5707964f, 0f);
        if (crit) {
            chatter(2.4f * power, 0.12f, 32f);
            if (power > 0.95f) {
                flash(lighten(elColor(el, true), 0.55f), 0.05f);
            }
        }
    }

    /** 暴击的放射线条：八向细亮线。 */
    private void critStreaks(float x, float y, int element) {
        int el = element < 0 ? EL_PHYS : element;
        int c = lighten(elColor(el, true), 0.3f);
        int n = (int) (8 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            float an = i * 0.7853982f + rnd() * 0.18f;
            float sp = 300f + rnd() * 220f;
            int idx = spawnP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    1.6f + rnd() * 1.6f, -2.2f, 0f, 9f, 0.13f + rnd() * 0.05f, c, P_SPARK);
            if (idx >= 0) ptint[idx] = 0.9f;
        }
    }

    /** 击杀爆散（简化入口）。 */
    void killBurst(float x, float y, int color) {
        killBurst(x, y, color, 1f, false, NO_DIR, -1);
    }

    // =====================================================================
    // 击杀确认（v2.3 统一入口）
    // =====================================================================
    /**
     * 击杀确认：按等级给出<b>可区分</b>的三档反馈。
     *
     * <p>为什么需要这个入口：击杀是 survivors 里唯一的正反馈节拍。旧代码里
     * 普通击杀、精英击杀、首领击杀只靠「碎片数量多一点」区分，在 60 敌同屏的
     * 混战里完全读不出来。这里用三个正交通道做区分 ——</p>
     * <ul>
     *   <li><b>粒子形态</b>：普通 = 少量上飘余烬；精英 = 环形碎片 + 余烬；首领 = 三层爆发。</li>
     *   <li><b>径向环</b>：tier 越高环越粗、越大、越白（贴花，走地面层）。</li>
     *   <li><b>全屏闪</b>：只有首领触发，且是一帧的低 alpha 白闪（约 0.105，不刺眼）。</li>
     * </ul>
     *
     * <p>全部走既有粒子 / 贴花 / 闪光池，无任何运行期分配。所有层数都乘
     * {@code globalScale}（粒子密度滑条），全屏闪继续受 flashIntensity + WCAG 限流约束。</p>
     *
     * @param tier 0 = 普通 / 1 = 精英 / 2 = 首领（越界自动钳制）
     * @param x,y  击杀位置（战斗区本地坐标）
     */
    void kill(int tier, float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        if (tier < KILL_NORMAL) tier = KILL_NORMAL;
        if (tier > KILL_BOSS) tier = KILL_BOSS;

        // ── 通道 1：粒子爆发 ──
        int shards, embers, emberColor;
        float shardSpeed, shardSize;
        int prio;
        switch (tier) {
            case KILL_BOSS:
                shards = 26; embers = 16; emberColor = 0xffffcf7a;
                shardSpeed = 330f; shardSize = 6.4f; prio = PRIO_CRITICAL;
                break;
            case KILL_ELITE:
                shards = 14; embers = 8; emberColor = 0xffffc46a;
                shardSpeed = 250f; shardSize = 5f; prio = PRIO_IMPORTANT;
                break;
            default:
                shards = 6; embers = 3; emberColor = 0xffffb070;
                shardSpeed = 175f; shardSize = 3.4f; prio = PRIO_NORMAL;
                break;
        }
        int sc = (int) (shards * globalScale + 0.5f);
        if (sc > 40) sc = 40;
        for (int i = 0; i < sc; i++) {
            float an = rnd() * 6.2831853f;
            float sp = shardSpeed * (0.6f + rnd() * 0.8f);
            int idx = spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp - 50f,
                    shardSize * (0.7f + rnd() * 0.6f), 0f, 620f, 1.05f,
                    0.42f + rnd() * 0.4f,
                    i % 4 == 0 ? 0xfffff2d8 : emberColor, P_SHARD, prio);
            if (idx >= 0) ptint[idx] = 0.5f;
        }
        int ec = (int) (embers * globalScale + 0.5f);
        for (int i = 0; i < ec; i++) {
            spawnPP(x + rnd2() * 9f, y + rnd2() * 6f,
                    rnd2() * 26f, -30f - rnd() * 60f,
                    1.8f + rnd() * 2.2f, -0.5f, -16f, 0.85f,
                    0.6f + rnd() * 0.7f,
                    rnd() < 0.4f ? 0xffffe9b8 : emberColor, P_EMBER, prio);
        }

        // ── 通道 2：径向环（贴花，地面层；越高档越粗越白）──
        switch (tier) {
            case KILL_BOSS:
                shockRing(x, y, 132f, 0xffffffff, 0.34f);
                shockwave(x, y, 92f, 0xffffe2b8, 0.46f);
                break;
            case KILL_ELITE:
                shockRing(x, y, 62f, 0xfffff2d8, 0.24f);
                break;
            default:
                // 普通击杀用细环，宽度 4→只画一次，避免密集清场时贴花池被打满
                spawnM(x, y, 5f, 28f, 0.17f, 0xcffff4e2, M_SHOCK);
                break;
        }
        // 地面残痕：只有精英以上留痕（普通击杀留痕会在 30 秒内把地面涂满）
        if (tier >= KILL_ELITE) stain(x, y + 5f, tier == KILL_BOSS ? 46f : 24f,
                alpha(emberColor, 0.38f), 1.1f);

        // ── 通道 3：全屏白闪（仅首领，低 alpha，一帧）──
        // 一帧的原因：长闪会把「击杀」读成「被击中」，短闪才是「我赢了」。
        if (tier == KILL_BOSS) {
            flashScaled(0xffffffff, 0.055f, KILL_BOSS_FLASH_SCALE);
            applyShake(7.5f, 0.32f, -1.5707964f, 0.25f);
            chatter(2.2f, 0.2f, 19f);
        } else if (tier == KILL_ELITE) {
            applyShake(2.6f, 0.1f, -1.5707964f, 0.2f);
        }
    }

    /**
     * 击杀爆散（分级）：pop + 有向碎片 + 地面血渍，按体型 / 精英缩放。
     *
     * @param scale   敌人体型（0.6~2.2）
     * @param elite   是否精英
     * @param dir     击杀方向（{@link #NO_DIR} 表示四面）
     * @param element 元素 0..4（-1 未标注）
     */
    void killBurst(float x, float y, int color, float scale, boolean elite, float dir, int element) {
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        if (Float.isNaN(scale)) scale = 1f;
        if (scale < 0.6f) scale = 0.6f; else if (scale > 2.2f) scale = 2.2f;
        int el = element < 0 ? EL_PHYS : element;
        boolean directed = dir != NO_DIR;
        float baseDir = directed ? dir : -1.5707964f;

        // pop：一圈白闪 + 元素色扩散
        ring(x, y, 15f * scale, 0xdffffff0, 0.15f);
        spawnM(x, y, 10f * scale, 44f * scale, 0.3f, lighten(color, 0.25f), M_SHOCK);

        // 碎片：有向时朝击杀方向甩出。精英击杀 = 重要优先级，杂兵 = 普通优先级。
        int killPrio = elite ? PRIO_IMPORTANT : PRIO_NORMAL;
        int count = (int) ((elite ? 17 : 10) * scale * globalScale + 0.5f);
        if (count > 40) count = 40;
        for (int i = 0; i < count; i++) {
            float an = directed ? baseDir + rnd2() * 1.5f : rnd() * 6.2831853f;
            float sp = (150f + rnd() * 210f) * scale;
            int idx = spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp - 40f,
                    (3.2f + rnd() * 4f) * (0.8f + 0.3f * scale), 0f, 520f, 1.1f,
                    (0.5f + rnd() * 0.5f) * (elite ? 1.2f : 1f),
                    i % 5 == 0 ? lighten(color, 0.45f) : color, P_SHARD, killPrio);
            if (idx >= 0) ptint[idx] = 0.5f;
        }
        // 地面残留：体型越大越明显
        stain(x, y + 6f * scale, 16f * scale, alpha(color, 0.42f), 0.7f + 0.4f * scale);
        // 烟
        int smokes = (int) ((elite ? 5 : 3) * globalScale + 0.5f);
        for (int i = 0; i < smokes; i++)
            smoke(x + rnd2() * 10f, y + rnd2() * 8f, color, (elite ? 13f : 9f) * scale);
        // 元素余韵
        switch (el) {
            case EL_FIRE:
                for (int i = 0; i < 5; i++) burn(x + rnd2() * 12f, y + rnd2() * 8f, 6f * scale);
                spawnM(x, y + 4f, 20f * scale, 20f * scale, 0.8f, 0x66ff8a4a, M_EMBER);
                break;
            case EL_FROST:
                spawnM(x, y + 6f, 22f * scale, 22f * scale, 1.4f, 0xff8fc8ee, M_FROST);
                break;
            case EL_VOID:
                spawnM(x, y, 56f * scale, 6f, 0.5f, 0xffb79bff, M_VOID);
                break;
            case EL_BOLT:
                boltFork(x - 20f, y - 20f, x + 20f, y + 18f, 0xffcdefff, 0.1f, 2);
                break;
            default: break;
        }
        // 精英 / 大体型：额外冲击波 + 顿帧（普通小怪不触发，避免连续命中把节奏拖慢）
        if (elite || scale > 1.35f) {
            shockwave(x, y, 90f * scale, 0xccffe2bd, 0.42f);
            shockRing(x, y, 60f * scale, 0xfffff2d8, 0.26f);
            applyShake(3.4f * scale, 0.12f, baseDir, 0.5f);
            chatter(1.6f, 0.1f, 24f);
            if (elite) hitstopFor(HS_ELITE, 0.03f);
        }
    }

    /**
     * 首领死亡：拆成三段 —— 白闪/冻结 → 次级冲击 + 碎屑 → 余烬铺开。
     * 第二三段由 {@link #cue} 在 0.18s / 0.55s 后补上。
     */
    void bossDeath(float x, float y, int color) {
        // 1) 白闪 + 冻结：全场安静一瞬
        flash(0xfff6f0ff, 0.48f);
        vignettePulse(lighten(color, 0.35f), 0.75f, 1.2f);
        hitstopFor(HS_PHASE, 0.24f);
        hitstopSlow(0f, 0.5f, 0.3f);
        applyShake(13f, 0.7f, -1.5707964f, 0.35f);
        chatter(5f, 0.45f, 15f);
        // 2) 第一波冲击
        shockwave(x, y, 420f, 0xfffff2d8, 0.9f);
        shockRing(x, y, 300f, 0xffffffff, 0.42f);
        spawnM(x, y, 20f, 200f, 0.6f, lighten(color, 0.45f), M_WAVE);
        // 3) 碎片爆发（关键优先级：首领死亡演出不允许被杂兵粒子挤掉）
        int n = (int) (46 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            float an = rnd() * 6.2831853f;
            float sp = 120f + rnd() * 420f;
            spawnPP(x + rnd2() * 22f, y + rnd2() * 22f,
                    (float) Math.cos(an) * sp, (float) Math.sin(an) * sp - 80f,
                    3.5f + rnd() * 6f, 0f, 420f, 0.85f, 0.7f + rnd() * 0.9f,
                    i % 4 == 0 ? 0xfffff0c8 : color, i % 7 == 0 ? P_SPARK : P_SHARD,
                    PRIO_CRITICAL);
        }
        // 4) 放射光束：把「大」一次性写出来
        int beams = (int) (10 * globalScale + 0.5f);
        for (int i = 0; i < beams; i++) {
            float an = i * 0.6283185f + rnd() * 0.1f;
            float len = 220f + rnd() * 180f;
            beam(x, y, x + (float) Math.cos(an) * len, y + (float) Math.sin(an) * len,
                    lighten(color, 0.5f), 0.3f);
        }
        // 5) 地面留痕
        stain(x, y + 10f, 90f, alpha(color, 0.32f), 3.2f);
        // 6) 后续两段：延时触发，形成「白闪 → 冲击波 → 余烬」的节奏
        cue(CUE_BOSS_2, 0.18f, x, y, color, 0f);
        cue(CUE_BOSS_3, 0.55f, x, y, color, 0f);
        banner(x, y - 70f, "首领击破", 0xffffe9b8);
        juice(J_BOSS, 0.9f);
    }

    /** 首领死亡后的余烬脉冲：由调用方在残骸上持续调用即可。 */
    void bossDeathPulse(float x, float y, float t) {
        if (t <= 0f || rnd() > 0.3f) return;
        spawnP(x + rnd2() * 70f, y + rnd2() * 30f, rnd2() * 12f, -20f - rnd() * 40f,
                1.6f + rnd() * 2f, -0.3f, -10f, 0.7f, 1.2f + rnd() * 1.4f,
                rnd() < 0.5f ? 0xffffd08a : 0xffc76a3a, P_EMBER);
    }

    /** 玩家受伤：红色屏幕边缘暗角脉冲。 */
    void hurt() { vignettePulse(0xfff18f8b, 0.55f, 0.42f); }

    /** 玩家受伤（带方向）：暗角 + 有向震屏 + chatter。 */
    void hurt(float dx, float dy, float power) {
        if (Float.isNaN(power)) power = 1f;
        if (power < 0.2f) power = 0.2f; else if (power > 1.5f) power = 1.5f;
        vignettePulse(0xfff18f8b, 0.32f + 0.25f * power, 0.42f);
        float amp = 5.5f * power;
        if (dx != 0f || dy != 0f) shakeDir(amp, 0.24f, dx, dy);
        else shake(amp, 0.24f);
        chatter(1.8f * power, 0.16f, 20f);
        cue(CUE_HURT_2, 0.14f, 0f, 0f, 0xfff18f8b, 0f);
    }

    // =====================================================================
    // 升级 / 终极
    // =====================================================================
    /** 升级：起势 + 收束（第二段延时触发，读起来像「先亮再炸」）。 */
    void levelUp(float x, float y) {
        levelUpA(x, y);
        cue(CUE_LEVEL_2, 0.14f, x, y, 0xfffff3cf, 0f);
    }

    /** 升级第 1 段：地面光池 + 冲击波 + 金色光柱（即时）。 */
    void levelUpA(float x, float y) {
        spawnM(x, y, 54f, 54f, 1.15f, 0xffeac487, M_PATCH);
        spawnM(x, y, 12f, 300f, 0.6f, 0xfffff0c8, M_SHOCK);
        spawnM(x, y, 10f, 150f, 0.55f, 0xffffe7b0, M_WAVE);
        int n = (int) (38 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            spawnPP(x + rnd2() * 26f, y + rnd2() * 12f, rnd2() * 26f, -170f - rnd() * 300f,
                    2.6f + rnd() * 3.2f, -1.8f, -30f, 0.85f, 0.55f + rnd() * 0.55f,
                    rnd() < 0.35f ? 0xfffffbe8 : (rnd() < 0.5f ? 0xffffe9a8 : 0xfff3c46a),
                    rnd() < 0.25f ? P_SPARK : P_FLAME, PRIO_CRITICAL);
        }
        // 上升的金色光环，逐层散开
        for (int i = 0; i < 3; i++) {
            int idx = spawnP(x, y, 0f, -30f, 40f + i * 16f, 22f, 0f, 0.4f,
                    0.55f + i * 0.08f, alpha(0xffffe2a0, 0.7f - i * 0.15f), P_RING);
            if (idx >= 0) ptint[idx] = 0.35f;
        }
        flash(0xfffff0c0, 0.16f);
        applyShake(3.4f, 0.2f, -1.5707964f, 0.25f);
        chatter(1.2f, 0.16f, 26f);
        hitstopFor(HS_PLAYER, 0.06f);
    }

    /** 升级第 2 段：字幕 + 星屑下坠 + 符文圈。 */
    void levelUpB(float x, float y) {
        spawnM(x, y, 60f, 260f, 1.2f, 0xffeac487, M_RUNE);
        banner(x, y - 60f, "LEVEL UP", 0xfffff3cf);
        int n = (int) (18 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            float sx = x + rnd2() * 200f;
            float sy = y - 220f - rnd() * 80f;
            spawnPP(sx, sy, rnd2() * 20f, 120f + rnd() * 160f,
                    2f + rnd() * 2.4f, 0f, 40f, 0.4f, 0.65f + rnd() * 0.45f,
                    0xfffff6d8, P_SPARK, PRIO_CRITICAL);
        }
        for (int i = 0; i < 6; i++)
            spawnPP(x + rnd2() * 40f, y - 20f - rnd() * 60f, rnd2() * 40f, -90f - rnd() * 90f,
                    2.6f + rnd() * 2f, -1.2f, -20f, 0.8f, 0.8f, 0xffffe9b0, P_EMBER,
                    PRIO_CRITICAL);
    }

    /** 终极：蓄力（向内收）→ 释放（白闪 + 冲击波 + 放射光束）→ 余波（延时）。 */
    void ultimate(float x, float y, int color) {
        ultCharge(x, y, color);
        ultRelease(x, y, color);
    }

    /** 终极蓄力：向中心收束的粒子 + 收缩环，制造 0.4s 的「吸气」。 */
    void ultCharge(float x, float y, int color) {
        int n = (int) (30 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            float an = rnd() * 6.2831853f, rad = 70f + rnd() * 90f;
            float c = (float) Math.cos(an), s = (float) Math.sin(an);
            spawnPP(x + c * rad, y + s * rad, -c * rad * 1.9f, -s * rad * 1.9f,
                    2f + rnd() * 2.6f, 0f, 0f, 0.2f, 0.4f + rnd() * 0.12f,
                    rnd() < 0.4f ? 0xffffffff : lighten(color, 0.3f),
                    rnd() < 0.3f ? P_SPARK : P_DOT, PRIO_CRITICAL);
        }
        spawnM(x, y, 110f, 24f, 0.42f, lighten(color, 0.2f), M_VOID);
        spawnM(x, y, 8f, 70f, 0.4f, 0xffffffff, M_SHOCK);
        hitstopSlow(0.05f, 0.18f, 0.45f);
        vignettePulse(lighten(color, 0.25f), 0.5f, 0.5f);
        applyShake(2f, 0.3f, -1.5707964f, 0.2f);
    }

    /** 终极释放：全游戏最亮的一帧。 */
    void ultRelease(float x, float y, int color) {
        int c = lighten(color, 0.35f);
        flash(0xfffdf6ff, 0.34f);
        hitstopFor(HS_PLAYER, 0.12f);
        hitstopSlow(0f, 0.38f, 0.32f);
        applyShake(10f, 0.45f, -1.5707964f, 0.3f);
        chatter(4f, 0.34f, 17f);
        shockRing(x, y, 460f, 0xffffffff, 0.42f);
        shockwave(x, y, 360f, c, 0.65f);
        spawnM(x, y, 30f, 220f, 0.7f, lighten(c, 0.5f), M_WAVE);
        spawnM(x, y, 70f, 70f, 1.1f, alpha(c, 0.6f), M_PATCH);
        int beams = (int) (14 * globalScale + 0.5f);
        for (int i = 0; i < beams; i++) {
            float an = i * 0.44879895f + rnd() * 0.12f;
            float len = 260f + rnd() * 240f;
            beam(x, y, x + (float) Math.cos(an) * len, y + (float) Math.sin(an) * len, c, 0.28f);
        }
        int n = (int) (44 * globalScale + 0.5f);
        for (int i = 0; i < n; i++) {
            float an = rnd() * 6.2831853f;
            float sp = 200f + rnd() * 520f;
            spawnPP(x, y, (float) Math.cos(an) * sp, (float) Math.sin(an) * sp,
                    2f + rnd() * 3.4f, -1.4f, 40f, 1.6f, 0.4f + rnd() * 0.6f,
                    rnd() < 0.3f ? 0xffffffff : c,
                    rnd() < 0.45f ? P_SPARK : P_STREAK, PRIO_CRITICAL);
        }
        cue(CUE_ULT_2, 0.16f, x, y, c, 0f);
        banner(x, y - 80f, "ULTIMATE", 0xfff7f0ff);
    }

    /** 清空所有特效与计时器（换房/重开时调用）。 */
    void clear() {
        for (int i = 0; i < MAX_PARTICLES; i++) plife[i] = 0f;
        for (int i = 0; i < MAX_MARKS; i++) mlife[i] = 0f;
        for (int i = 0; i < MAX_NUMBERS; i++) {
            nlife[i] = 0f; nstr[i] = null; npop[i] = 0f; nkeyOf[i] = 0;
        }
        for (int i = 0; i < NKEY_TABLE; i++) { nkeyId[i] = 0; nkeySlot[i] = -1; nkeyAt[i] = 0f; }
        for (int i = 0; i < MAX_BEAMS; i++) blife[i] = 0f;
        for (int i = 0; i < MAX_CUES; i++) cueDelay[i] = 0f;
        pNext = pAlive = 0;
        mNext = mAlive = 0;
        nNext = nAlive = 0;
        bNext = bAlive = 0;
        cueNext = cueAlive = 0;
        shakeAmp = shakeDur = shakeTime = 0f;
        trauma = 0f; traumaPeak = 0f;
        chatAmp = chatDur = chatTime = 0f;
        shakeOutX = shakeOutY = 0f; shakeSampled = false;
        hitStop = 0f; slowTime = 0f; slowDur = 0f; slowScale = 1f;
        resetHitstopBudget();
        flashTime = 0f; vigTime = 0f;
        flashWinStart = -99f; flashWinCount = 0; flashLastAt = -99f;
        fadeLevel = fadeTarget = 0f; fadeRate = 0f;
        anchorSet = false;
    }

    /** 释放资源：本类无 Bitmap，只有画笔与渐变，置空即可。 */
    void dispose() { clear(); }

    // =====================================================================
    // 绘制 —— 实体之下：地面贴花 / 冲击波 / 范围指示
    // =====================================================================
    /** 绘制所有地面层效果（调用方应在实体之前、战斗区坐标系内调用）。 */
    void drawUnder(Canvas c) {
        if (c == null) return;
        Paint p = paint;
        for (int i = 0; i < MAX_MARKS; i++) {
            if (mlife[i] <= 0f) continue;
            float k = mlife[i] / mmax[i];     // 1→0
            if (k > 1f) k = 1f;
            float prog = 1f - k;              // 0→1
            switch (mkind[i]) {
                case M_PATCH: { // 地面光斑：淡出并轻微收缩
                    float r = mr0[i] * (1f - prog * 0.22f);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(mcolor[i], k * k * 0.85f));
                    c.drawCircle(mx[i], my[i], r, p);
                    break;
                }
                case M_WAVE: { // 冲击波：三次缓出扩散的粗环
                    float e = 1f - (1f - prog) * (1f - prog) * (1f - prog);
                    float r = mr0[i] + (mr1[i] - mr0[i]) * e;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f + 10f * k);
                    p.setColor(alpha(mcolor[i], k * 0.9f));
                    c.drawCircle(mx[i], my[i], r, p);
                    break;
                }
                case M_SHOCK: { // 锐利冲击环：细、快、高对比
                    float e = 1f - (1f - prog) * (1f - prog);
                    float r = mr0[i] + (mr1[i] - mr0[i]) * e;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1f + 4.5f * k * k);
                    p.setColor(alpha(mcolor[i], k * k));
                    c.drawCircle(mx[i], my[i], r, p);
                    if (k > 0.35f) {
                        p.setStrokeWidth(1f);
                        p.setColor(alpha(lighten(mcolor[i], 0.5f), k * 0.5f));
                        c.drawCircle(mx[i], my[i], r * 0.86f, p);
                    }
                    break;
                }
                case M_RUNE: { // 符文印记：淡底 + 双环 + 旋转刻度
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(mcolor[i], k * 0.14f));
                    c.drawCircle(mx[i], my[i], mr0[i], p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f);
                    p.setColor(alpha(mcolor[i], k * 0.8f));
                    c.drawCircle(mx[i], my[i], mr0[i], p);
                    p.setColor(alpha(mcolor[i], k * 0.45f));
                    c.drawCircle(mx[i], my[i], mr0[i] * 0.62f, p);
                    float rot = mseed[i] + prog * 1.7f;
                    for (int t = 0; t < 4; t++) {
                        float an = rot + t * 1.5707964f;
                        float cos = (float) Math.cos(an), sin = (float) Math.sin(an);
                        c.drawLine(mx[i] + cos * mr0[i] * 0.72f, my[i] + sin * mr0[i] * 0.72f,
                                mx[i] + cos * mr0[i] * 0.96f, my[i] + sin * mr0[i] * 0.96f, p);
                    }
                    break;
                }
                case M_FROST: { // 霜痕：淡蓝底 + 六角冰晶排列
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(mcolor[i], k * 0.24f));
                    c.drawCircle(mx[i], my[i], mr0[i], p);
                    p.setColor(alpha(mcolor[i], k * 0.55f));
                    for (int t = 0; t < 6; t++) {
                        float an = mseed[i] + t * 1.0471976f;
                        float dx = (float) Math.cos(an) * mr0[i] * 0.58f;
                        float dy = (float) Math.sin(an) * mr0[i] * 0.58f;
                        diamond(c, mx[i] + dx, my[i] + dy, mr0[i] * 0.16f);
                    }
                    break;
                }
                case M_VOID: { // 虚空收缩环
                    float e = prog * prog;
                    float r = mr0[i] * (1f - e) + 3f;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f + 7f * k);
                    p.setColor(alpha(mcolor[i], k * 0.85f));
                    c.drawCircle(mx[i], my[i], r, p);
                    p.setStrokeWidth(1f);
                    p.setColor(alpha(0xff2a1346, k * 0.6f));
                    c.drawCircle(mx[i], my[i], r * 0.78f, p);
                    break;
                }
                case M_STAIN: { // 血渍 / 焦痕：贴地椭圆，缓慢淡出
                    float r = mr0[i] * (0.85f + 0.15f * k);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(mcolor[i], k * k * 0.5f));
                    c.drawOval(mx[i] - r, my[i] - r * 0.42f, mx[i] + r, my[i] + r * 0.42f, p);
                    p.setColor(alpha(0xff140c10, k * k * 0.3f));
                    c.drawOval(mx[i] - r * 0.6f, my[i] - r * 0.25f,
                            mx[i] + r * 0.6f, my[i] + r * 0.25f, p);
                    break;
                }
                case M_EMBER: { // 余烬光池：呼吸式明暗
                    float breathe = 0.55f + 0.45f * (float) Math.sin(clock * 3.1f + mseed[i]);
                    float r = mr0[i] * (0.9f + 0.1f * breathe);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(mcolor[i], k * 0.5f * breathe));
                    c.drawCircle(mx[i], my[i], r, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.4f);
                    p.setColor(alpha(lighten(mcolor[i], 0.3f), k * 0.35f * breathe));
                    c.drawCircle(mx[i], my[i], r * 0.7f, p);
                    break;
                }
                default: break;
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    // =====================================================================
    // 绘制 —— 分层：地面贴花 / 实体之下 / 实体之上 / 屏幕空间
    // =====================================================================
    /**
     * 绘制所有前景效果（调用方应在实体之后、战斗区坐标系内调用）。
     * <p>只画粒子与光束 —— 飘字/伤害数字已拆到 {@link #drawNumbers(Canvas)}，
     * 由调用方放在实体<b>之前</b>绘制，这样数字永远不会盖住玩家角色
     * （清单「What NOT to do」：飘字压在 avatar 上 = 看不见自己）。</p>
     */
    void draw(Canvas c) {
        if (c == null) return;
        drawBeams(c);
        drawParticles(c);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1f);
    }

    /**
     * 绘制飘字 / 伤害数字 —— 单独暴露，供调用方在「实体之前」调用。
     * <p>伤害数字属于背景信息层：玩家、子弹、敌人都应盖在它上面。
     * 分层顺序建议：{@code drawUnder → drawNumbers → 实体 → draw → drawScreen}。</p>
     */
    void drawNumbers(Canvas c) {
        if (c == null) return;
        drawNumberPool(c);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1f);
    }

    /** 绘制粒子：按 kind 分支，全部使用复用的 Paint/Path。 */
    private void drawParticles(Canvas c) {
        Paint p = paint;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (plife[i] <= 0f) continue;
            float k = plife[i] / pmax[i];     // 1→0
            if (k > 1f) k = 1f;
            float prog = 1f - k;
            float x = px[i], y = py[i], s = psize[i];
            if (s <= 0.01f) continue;
            switch (pkind[i]) {
                case P_DOT: { // 圆点：随寿命缩小淡出
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k));
                    c.drawCircle(x, y, s * (0.5f + 0.5f * k), p);
                    break;
                }
                case P_STREAK: { // 速度线火花（高速的那些带一层辉光）
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    float tail = 0.022f + 0.012f * ptint[i];
                    p.setStrokeWidth(Math.max(1f, s * 0.55f));
                    p.setColor(alpha(pcolor[i], k));
                    c.drawLine(x, y, x - pvx[i] * tail, y - pvy[i] * tail, p);
                    if (ptint[i] > 0.62f) {
                        p.setStrokeWidth(Math.max(1f, s * 1.5f));
                        p.setColor(alpha(lighten(pcolor[i], 0.55f), k * 0.2f));
                        c.drawLine(x, y, x - pvx[i] * tail * 0.6f, y - pvy[i] * tail * 0.6f, p);
                    }
                    p.setStrokeCap(Paint.Cap.BUTT);
                    break;
                }
                case P_SPARK: { // 高速粒子：细长亮线 + 白芯
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    float tl = 0.03f * (0.6f + ptint[i]);
                    p.setStrokeWidth(Math.max(1f, s * 0.7f));
                    p.setColor(alpha(lighten(pcolor[i], 0.3f), k * k * 0.95f));
                    c.drawLine(x, y, x - pvx[i] * tl, y - pvy[i] * tl, p);
                    p.setColor(alpha(0xffffffff, k * k * 0.5f));
                    c.drawLine(x, y, x - pvx[i] * tl * 0.55f, y - pvy[i] * tl * 0.55f, p);
                    p.setStrokeCap(Paint.Cap.BUTT);
                    break;
                }
                case P_DIAMOND: {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k));
                    diamond(c, x, y, s * (0.6f + 0.4f * k));
                    break;
                }
                case P_SHARD: { // 旋转三角碎片
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k));
                    shardPath(c, x, y, s, pseed[i] + clock * 7f);
                    break;
                }
                case P_RING: { // 扩散圆环（ptint>0 时压扁成椭圆）
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1f + 2.5f * k);
                    p.setColor(alpha(pcolor[i], k * 0.95f));
                    float rr = s * (0.3f + prog * 1.5f);
                    if (rr > 0.5f) {
                        if (ptint[i] > 0.02f) {
                            rect.set(x - rr, y - rr * ptint[i], x + rr, y + rr * ptint[i]);
                            c.drawOval(rect, p);
                        } else {
                            c.drawCircle(x, y, rr, p);
                        }
                    }
                    break;
                }
                case P_SMOKE: { // 柔和烟雾
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k * 0.55f));
                    c.drawCircle(x, y, s * (0.8f + prog * 0.6f), p);
                    break;
                }
                case P_FLAME: { // 火焰：外焰 + 内焰（+亮芯，仅大颗粒），向上飘，闪烁
                    float flick = 0.82f + 0.18f * (float) Math.sin(clock * 24f + pseed[i] * 3f);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k * 0.26f * flick));
                    c.drawCircle(x, y, s * 1.55f, p);
                    p.setColor(alpha(pcolor[i], k * 0.85f));
                    c.drawCircle(x, y, s * 0.78f, p);
                    if (s > 5f) {   // 小火焰省略亮芯，省一次绘制
                        p.setColor(alpha(lighten(pcolor[i], 0.55f), k * k * 0.7f));
                        c.drawCircle(x, y - s * 0.1f, s * 0.36f, p);
                    }
                    break;
                }
                case P_EMBER: { // 余烬：小点 + 闪烁 + 熄灭
                    float flick = 0.5f + 0.5f * (float) Math.sin(clock * 5.5f + pseed[i] * 4f);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(alpha(pcolor[i], k * (0.35f + 0.65f * flick)));
                    c.drawCircle(x, y, s * (0.5f + 0.5f * k), p);
                    p.setColor(alpha(lighten(pcolor[i], 0.6f), k * k * flick * 0.6f));
                    c.drawCircle(x, y, s * 0.35f, p);
                    break;
                }
                case P_CRYSTAL: { // 六角冰晶：缓慢自转（大颗粒才加内芯）
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.4f);
                    p.setColor(alpha(pcolor[i], k * 0.9f));
                    hexPath(c, x, y, s, pseed[i] + clock * 0.9f);
                    if (s > 4.5f) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(alpha(lighten(pcolor[i], 0.6f), k * k * 0.55f));
                        c.drawCircle(x, y - s * 0.5f, s * 0.22f, p);
                    }
                    break;
                }
                case P_ARC: { // 弧形刀光
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f + 5f * k);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setColor(alpha(pcolor[i], k * 0.95f));
                    float half = s * 0.5f;
                    float cx = x + (float) Math.cos(pseed[i]) * half * 0.55f;
                    float cy = y + (float) Math.sin(pseed[i]) * half * 0.55f;
                    rect.set(cx - half, cy - half, cx + half, cy + half);
                    path.reset();
                    path.addArc(rect, pseed[i] * 57.29578f - 62f, 124f);
                    c.drawPath(path, p);
                    p.setStrokeCap(Paint.Cap.BUTT);
                    break;
                }
                default: break;
            }
        }
    }

    /** 绘制光束与闪电。 */
    private void drawBeams(Canvas c) {
        Paint p = paint;
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (blife[i] <= 0f) continue;
            float k = blife[i] / bmax[i];
            if (k > 1f) k = 1f;
            if (k <= 0f) continue;
            if (bkind[i] == B_BEAM) {
                // 光束：外层色光 + 内层白芯
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(7f * k + 1.5f);
                p.setColor(alpha(bcolor[i], k * 0.45f));
                c.drawLine(bx1[i], by1[i], bx2[i], by2[i], p);
                p.setStrokeWidth(2.2f * k + 0.8f);
                p.setColor(alpha(0xffffffff, k * 0.9f));
                c.drawLine(bx1[i], by1[i], bx2[i], by2[i], p);
            } else {
                // 闪电：8 段随机折线（种子固定，同一条闪电不抖动）
                float dx = bx2[i] - bx1[i], dy = by2[i] - by1[i];
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1f) continue;
                float nxx = -dy / len, nyy = dx / len;
                float spread = Math.min(len * 0.14f, 52f);
                path.reset();
                path.moveTo(bx1[i], by1[i]);
                int seg = 8;
                for (int t = 1; t < seg; t++) {
                    float f = t / (float) seg;
                    float off = hash(bseed[i], t) * spread;
                    path.lineTo(bx1[i] + dx * f + nxx * off, by1[i] + dy * f + nyy * off);
                }
                path.lineTo(bx2[i], by2[i]);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(8f * k + 2f);
                p.setColor(alpha(bcolor[i], k * 0.35f));
                c.drawPath(path, p);
                p.setStrokeWidth(2.6f * k + 1f);
                p.setColor(alpha(0xfffff8e0, k * 0.95f));
                c.drawPath(path, p);
                if (bkind[i] == B_FORK && bfork[i] > 0) {
                    for (int f = 0; f < bfork[i]; f++) {
                        float f0 = 0.35f + f * 0.22f;
                        float ox = bx1[i] + dx * f0 + nxx * hash(bseed[i], 11 + f) * spread;
                        float oy = by1[i] + dy * f0 + nyy * hash(bseed[i], 11 + f) * spread;
                        float fx = ox + nxx * 26f * hash(bseed[i], 20 + f) + dx * 0.16f;
                        float fy = oy + nyy * 26f * hash(bseed[i], 20 + f) + dy * 0.16f;
                        p.setStrokeWidth(1.4f);
                        p.setColor(alpha(0xfffff4d8, k * 0.65f));
                        c.drawLine(ox, oy, fx, fy, p);
                    }
                }
            }
        }
    }

    /** 绘制伤害数字与漂浮文字：上飘 + 淡出，暴击放大并描边。 */
    /** 绘制飘字池本体（内部实现，外部走 {@link #drawNumbers(Canvas)}）。 */
    private void drawNumberPool(Canvas c) {
        Paint tp = textPaint;
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (nlife[i] <= 0f) continue;
            float k = nlife[i] / nmax[i];
            if (k > 1f) k = 1f;
            float prog = 1f - k;
            if (prog < 0f) prog = 0f;
            float ease = 1f - (1f - prog) * (1f - prog);   // 先快后慢上抛
            int style = nstyle[i];
            float rise;
            switch (style) {
                case N_BIG: rise = 0f; break;
                case N_CRIT: rise = 74f; break;
                case N_HEAL: rise = 50f; break;
                case N_COIN:
                case N_XP: rise = 40f; break;
                case N_STATUS: rise = 38f; break;
                default: rise = 44f;
            }
            float y = ny[i] - rise * ease;
            float x = nx[i] + nvx[i] * prog * 0.35f;
            float a = k < 0.42f ? k / 0.42f : 1f;          // 末段淡出
            if (prog < 0.1f) a *= prog / 0.1f;             // 出现瞬间淡入
            if (a <= 0.01f) continue;
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setFakeBoldText(true);
            switch (style) {
                case N_BIG: { // 大字幕：弹入 + 深色描边，屏幕中最抢眼
                    float pop = 1f + 0.22f * (1f - Math.min(1f, prog * 5f));
                    tp.setTextSize(46f * pop);
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(7f);
                    tp.setColor(alpha(0xcc2a1a10, a * k));
                    c.drawText(nstr[i], x, y, tp);
                    tp.setStyle(Paint.Style.FILL);
                    tp.setColor(alpha(ncolor[i], a));
                    c.drawText(nstr[i], x, y, tp);
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(1.6f);
                    tp.setColor(alpha(0xffffffff, a * 0.55f * k));
                    c.drawText(nstr[i], x, y - 1f, tp);
                    tp.setStyle(Paint.Style.FILL);
                    break;
                }
                case N_STATUS: {
                    tp.setTextSize(20f);
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(3.5f);
                    tp.setColor(alpha(0xcc000000, a * 0.8f));
                    c.drawText(nstr[i], x, y, tp);
                    tp.setStyle(Paint.Style.FILL);
                    tp.setColor(alpha(ncolor[i], a));
                    c.drawText(nstr[i], x, y, tp);
                    break;
                }
                case N_CRIT: { // 暴击：金色大字 + 双层描边 + 弹出 + 上方短线
                    // 弹出：npop 从 0.12s 线性归零，缩放 1.35× → 1.0（120ms 内完成）
                    float popT = npop[i];
                    float pk = popT > 0f ? (popT / NPOP_TIME) : 0f;
                    if (pk > 1f) pk = 1f;
                    // 30% 放大：基础 30 → 39（要求「暴击数字 30% 更大」）
                    float pop = 1.30f + NPOP_SCALE * pk;
                    tp.setTextSize(30f * pop);
                    int len = numChars(nvalue[i]);
                    // 第一层描边：深棕，撑开轮廓（比普通数字更粗，因为字更大）
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(5.6f);
                    tp.setColor(alpha(0xe6200f08, a));
                    c.drawText(chars, 0, len, x, y, tp);
                    // 第二层描边（要求「1 extra outline pass」）：金色外发光，让暴击在一屏白字里跳出来
                    tp.setStrokeWidth(3.2f);
                    tp.setColor(alpha(CRIT_GOLD, a * 0.55f));
                    c.drawText(chars, 0, len, x, y, tp);
                    // 填充：白芯 + 金色主体
                    tp.setStyle(Paint.Style.FILL);
                    tp.setColor(alpha(0xfffffff4, a * 0.9f));
                    c.drawText(chars, 0, len, x, y - 1.6f * pop, tp);
                    tp.setColor(alpha(ncolor[i], a));
                    c.drawText(chars, 0, len, x, y, tp);
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(2f);
                    tp.setColor(alpha(lighten(ncolor[i], 0.35f), a * 0.7f));
                    c.drawLine(x - 12f * pop, y - 26f * pop, x + 12f * pop, y - 26f * pop, tp);
                    tp.setStyle(Paint.Style.FILL);
                    break;
                }
                default: { // 普通数值：伤害 / 治疗 / 星尘 / 经验 / 护盾
                    float size;
                    switch (style) {
                        case N_HEAL: size = 20f; break;
                        case N_SHIELD: size = 17f; break;
                        case N_COIN:
                        case N_XP: size = 16f; break;
                        default: size = 23f; break;
                    }
                    tp.setTextSize(size);
                    boolean prefix = style != N_DAMAGE;
                    int len = prefix ? numPrefix(style, nvalue[i]) : numChars(nvalue[i]);
                    tp.setStyle(Paint.Style.STROKE);
                    tp.setStrokeWidth(prefix ? 3f : 3.6f);
                    tp.setColor(alpha(0xaa100a0c, a * (prefix ? 0.75f : 0.9f)));
                    c.drawText(chars, 0, len, x, y, tp);
                    tp.setStyle(Paint.Style.FILL);
                    tp.setColor(alpha(ncolor[i], a));
                    c.drawText(chars, 0, len, x, y, tp);
                    break;
                }
            }
        }
        tp.setFakeBoldText(false);
    }

    /** 把带前缀的数值写入 char[]（纯 ASCII，保证零分配）。 */
    private int numPrefix(int style, float v) {
        int p = 0;
        switch (style) {
            case N_HEAL: chars[p++] = '+'; break;
            case N_COIN: chars[p++] = 'x'; break;
            case N_XP: chars[p++] = '+'; break;
            case N_SHIELD: chars[p++] = '['; break;
            default: break;
        }
        int n = (int) v;
        if (n < 0) n = 0;
        if (n > 99999) n = 99999;
        int start = p;
        if (n == 0) chars[p++] = '0';
        else {
            while (n > 0) { chars[p++] = (char) ('0' + n % 10); n /= 10; }
            for (int i = start, j = p - 1; i < j; i++, j--) {
                char t = chars[i]; chars[i] = chars[j]; chars[j] = t;
            }
        }
        if (style == N_SHIELD) chars[p++] = ']';
        return p;
    }

    // =====================================================================
    // 绘制 —— 屏幕空间：暗角 / 全屏闪 / 黑幕
    // =====================================================================
    /** 屏幕空间绘制（1440x810 逻辑坐标）。 */
    void drawScreen(Canvas c) { drawScreen(c, W, H); }

    /** 屏幕空间绘制：w,h 为当前逻辑屏幕尺寸，内部按 1440x810 等比缩放。 */
    void drawScreen(Canvas c, float w, float h) {
        if (c == null || w <= 0f || h <= 0f) return;
        c.save();
        c.scale(w / W, h / H);
        Paint p = paint;
        p.setStyle(Paint.Style.FILL);

        // 1) 暗角：常驻 0.25，可被脉冲叠加增强
        float pulse = vigTime > 0f ? vigStrength * (vigTime / Math.max(0.001f, vigMax)) : 0f;
        float v = Math.max(vignetteBase, pulse);
        if (v > 0.005f) {
            p.setShader(pulse >= vignetteBase ? pulseShader() : vigShader);
            p.setColor(0xffffffff);
            p.setAlpha((int) (Math.min(1f, v) * 255f));
            c.drawRect(0f, 0f, W, H, p);
            p.setShader(null);
            p.setAlpha(255);
        }

        // 2) 全屏闪光：二次曲线快速衰减，峰值受 FLASH_ALPHA_CAP 与用户滑条双重限制
        if (flashTime > 0f) {
            float fk = flashTime / Math.max(0.001f, flashMax);
            float peak = flashPeak * FLASH_ALPHA_CAP;
            if (peak > FLASH_ALPHA_CAP) peak = FLASH_ALPHA_CAP;
            int a = (int) (fk * fk * peak * 255f);
            if (a > 1) {
                p.setShader(null);
                p.setColor((a << 24) | (flashColor & 0x00FFFFFF));
                c.drawRect(0f, 0f, W, H, p);
            }
        }

        // 3) 黑幕（转场）
        if (fadeLevel > 0.002f) {
            p.setShader(null);
            p.setColor(((int) (Math.min(1f, fadeLevel) * 255f) << 24) | 0x000000);
            c.drawRect(0f, 0f, W, H, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    /**
     * 取彩色暗角渐变：按颜色查预建槽位。
     *
     * <p>命中预设色 → 直接返回对应 Shader；非预设色（例如自定义元素色）→ 就近取
     * 亮度最接近的预设槽。这样暗角脉停在运行期完全不分配 Shader。</p>
     */
    private Shader pulseShader() {
        int rgb = vigColor & 0x00FFFFFF;
        for (int i = 0; i < PULSE_SLOTS; i++) {
            if ((pulseSlotColor[i] & 0x00FFFFFF) == rgb) return pulseShaders[i];
        }
        // 非预设色：按 RGB 距离找最接近的预设（避免任何运行期 new）
        int vr = (rgb >> 16) & 0xff, vg = (rgb >> 8) & 0xff, vb = rgb & 0xff;
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < PULSE_SLOTS; i++) {
            int c = pulseSlotColor[i];
            int dr = ((c >> 16) & 0xff) - vr, dg = ((c >> 8) & 0xff) - vg, db = (c & 0xff) - vb;
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return pulseShaders[best];
    }

    // =====================================================================
    // 复用路径助手
    // =====================================================================
    /** 把伤害值写入复用 char[]（0..999999），返回字符个数；绘制端零临时对象。 */
    private int numChars(float v) {
        int n = (int) v;
        if (n < 0) n = 0;
        if (n > 999999) n = 999999;
        int len = 0;
        if (n == 0) { chars[0] = '0'; return 1; }
        while (n > 0) { chars[len++] = (char) ('0' + n % 10); n /= 10; }
        for (int i = 0, j = len - 1; i < j; i++, j--) {
            char t = chars[i]; chars[i] = chars[j]; chars[j] = t;
        }
        return len;
    }

    /** 用当前 Paint 画菱形（中心 x,y，半径 r）。 */
    private void diamond(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y - r);
        path.lineTo(x + r * 0.72f, y);
        path.lineTo(x, y + r);
        path.lineTo(x - r * 0.72f, y);
        path.close();
        c.drawPath(path, paint);
    }

    /** 用当前 Paint 画六角冰晶（中心 x,y，外接半径 r，rot 旋转）。 */
    private void hexPath(Canvas c, float x, float y, float r, float rot) {
        path.reset();
        for (int i = 0; i < 6; i++) {
            float an = rot + i * 1.0471976f;
            float xx = x + (float) Math.cos(an) * r;
            float yy = y + (float) Math.sin(an) * r;
            if (i == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
        }
        path.close();
        c.drawPath(path, paint);
    }

    /** 用当前 Paint 画三角碎片（中心 x,y，半径 r，rot 旋转）。 */
    private void shardPath(Canvas c, float x, float y, float r, float rot) {
        path.reset();
        float a2 = rot + 2.2f, a3 = rot + 4.1f;
        path.moveTo(x + (float) Math.cos(rot) * r, y + (float) Math.sin(rot) * r);
        path.lineTo(x + (float) Math.cos(a2) * r * 0.8f, y + (float) Math.sin(a2) * r * 0.8f);
        path.lineTo(x + (float) Math.cos(a3) * r * 0.7f, y + (float) Math.sin(a3) * r * 0.7f);
        path.close();
        c.drawPath(path, paint);
    }

    // =====================================================================
    // 诊断（harness 专用，运行期不调用）
    // =====================================================================
    /** 当前存活的粒子数（必然 ≤ {@link #MAX_PARTICLES}）。 */
    int debugParticles() { return pAlive; }

    /** 当前存活贴花数。 */
    int debugMarks() { return mAlive; }

    /** 当前存活飘字数。 */
    int debugNumbers() { return nAlive; }

    /** 当前存活光束数。 */
    int debugBeams() { return bAlive; }

    /** 当前待触发的延时事件数。 */
    int debugCues() { return cueAlive; }

    /** 顿帧剩余（秒）。 */
    float debugHitStop() { return hitStop; }

    /** 慢放剩余（秒）。 */
    float debugSlow() { return slowTime; }

    /** 当前慢放倍率。 */
    float debugSlowScale() { return slowScale; }

    /** 最近 1 秒窗口内剩余的顿帧额度（秒）。 */
    float debugBudget() { return hitstopBudget(); }

    /** 当前创伤值 0..1（震屏诊断）。 */
    float debugTrauma() { return trauma; }

    /** 最近一次实际生效的顿帧（秒）。 */
    float debugHitStopApplied() { return hitstopApplied; }

    /** 当前全局伤害数字上限（受「伤害数字」滑条影响，最高 12）。 */
    int debugNumberCap() { return numberCap; }

    /** 当前存活的关键优先级粒子数（诊断用）。 */
    int debugCritical() {
        int n = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) if (plife[i] > 0f && pprio[i] >= PRIO_CRITICAL) n++;
        return n;
    }

    /** 当前闪光累计窗口内次数（WCAG 限流诊断）。 */
    int debugFlashCount() { return flashWinCount; }

    /** 当前全屏闪光剩余（秒）。 */
    float debugFlashTime() { return flashTime; }
}
