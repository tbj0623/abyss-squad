package com.dilao.abysssquad;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.dilao.core.Bosses;
import com.dilao.core.Content;
import com.dilao.core.Enemies;
import com.dilao.core.Game;
import com.dilao.core.Relics;

/**
 * 首次运行引导（FTUE）导演 —— 第一趟的四个半分钟。
 *
 * <p><b>规格来源</b>：{@code abyss-squad-ux-report.md} §3「First-run script (0:00 → 5:00)」
 * 与 §5「Mistakes to avoid」。三条硬规则贯穿全类：
 * <ol>
 *   <li><b>没有文字墙</b>：每条提示 ≤ 12 个词；每一条都是控件上的一个脉冲 / 一个幽灵手势，
 *       从不是模态框。移动这一步干脆没有文字。</li>
 *   <li><b>前五分钟不死人</b>：{@link #update(Game, float)} 每帧把本地玩家顶成无敌，
 *       掉血过低再兜底拉回；倒下也会被立刻扶起。玩家可以打得很烂，
 *       但不会在学会四个按钮之前输掉。</li>
 *   <li><b>教一次就够</b>：完成或跳过都写 {@code onboardingStep = 99}，之后永不重播。</li>
 * </ol>
 *
 * <h3>自包含性</h3>
 * 本类<b>不修改 core，也不依赖 GameView / Fx / BattleHud 的任何改动</b>：只读 {@link Game}
 * 的公开字段，并只在引导激活时往 {@code g.enemies} 里放教学用的敌人（技能锥形群 + 迷你首领）。
 * 接线只需要在 GameView 加 4 个调用点，精确补丁见 {@code docs/onboarding-quickrun.md}。
 *
 * <h3>存档</h3>
 * 进度存在独立 SharedPreferences（{@code "abyss-onboarding"}）里，<b>绝不写 Meta 的 JSON</b>，
 * 避免与并行改 Meta 的其它工作冲突。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   onboarding.bind(this);                         // 构造后一次
 *   onboarding.startIfFirstRun();                  // 开新局时
 *   onboarding.update(g, simDt);                   // 局内每帧，session.update 之后
 *   onboarding.drawHint(c, ui, g, p);              // 阶段 switch 之后、覆盖层之前
 *   if (onboarding.click(id)) return;              // activate() 最开头
 *   if (onboarding.wantsDash()) dashSerial++;      // 每帧
 * </pre>
 */
public final class Onboarding {

    // ══════════════════════════════════════════════════════════════
    //  持久化
    // ══════════════════════════════════════════════════════════════

    /** 独立 prefs 文件。不与 Meta 的 {@code "abyss-meta"} 混写。 */
    public static final String PREFS = "abyss-onboarding";
    /** {@code -1} = 从未开始；{@code 0..7} = 进行中的步骤；{@code 99} = 已完成。 */
    public static final String KEY_STEP = "onboardingStep";
    public static final int NEVER = -1, DONE = 99;

    // ══════════════════════════════════════════════════════════════
    //  步骤（0:00 → 5:00）
    // ══════════════════════════════════════════════════════════════

    /** 0:00–0:55 移动：幽灵拇指环，无文字。 */
    public static final int S_MOVE = 0;
    /** 0:55–1:15 首次三选一：给推荐卡加金边缎带。 */
    public static final int S_DRAFT = 1;
    /** 1:15–1:55 瞄准：右半屏的幽灵拖动手指。 */
    public static final int S_AIM = 2;
    /** 1:55–2:35 闪避：按钮脉冲，3.5 秒未按则静默自动触发一次。 */
    public static final int S_DASH = 3;
    /** 2:35–3:20 技能：铺 12 敌锥形集群，形状正好是技能清场形状。 */
    public static final int S_SKILL = 4;
    /** 3:20–4:00 终极：脚本化充满能量条。 */
    public static final int S_ULT = 5;
    /** 4:00–4:45 迷你首领登场，把这一趟收尾。 */
    public static final int S_BOSS = 6;
    /** 4:45–5:00 结算：脚本收尾，交给 ResultScreen。 */
    public static final int S_RESULT = 7;

    public static final int STEP_COUNT = 8;

    /** 每一步的超时（秒）。超时即推进——引导永远能走完，不会被卡住。 */
    static final float[] STEP_MAX = {42f, 30f, 26f, 11f, 16f, 18f, 50f, 12f};

    /** 引导局的标称长度：4:45。 */
    public static final float RUN_LENGTH = 285f;

    // ── 脚本化时刻（相对本步开始）──
    /** 玩家还没按闪避时的静默自动触发。 */
    static final float DASH_AUTO_AT = 3.5f;
    /** 终极充满所需时间。 */
    static final float ULT_FILL_TIME = 1.4f;
    /** 充满之后留给玩家按下的观察时间。 */
    static final float ULT_GRACE = 6f;
    /** 迷你首领死后停留的这一拍，让「打倒了」读得出来。 */
    static final float BOSS_DEATH_BEAT = 1.4f;

    // ── 「不死」缓冲 ──
    /** 每帧给玩家顶到的最小无敌时间；远大于一帧内可能消耗掉的量。 */
    static final float SAFETY_INVULN = 1.5f;
    /** 血量掉到这条线以下直接兜底拉回。 */
    static final float SAFETY_HP_FRAC = .55f;

    // ── 教学敌人 ──
    static final int CONE_COUNT = 12;
    static final float CONE_HALF_ANGLE = .55f;
    static final float CONE_RADIUS = 300f;
    static final float CONE_HP = 26f;
    /** 迷你首领血量：够撑满 4:00–4:45，又不至于打不动。 */
    static final float MINIBOSS_HP = 900f;

    // ══════════════════════════════════════════════════════════════
    //  实例状态
    // ══════════════════════════════════════════════════════════════

    private SharedPreferences prefs;
    private int step = NEVER;

    /** 本步骤内已经过的秒数。 */
    private float stepClock;
    /** 整局引导已过的秒数。 */
    private float runClock;

    private boolean moved, aimed, dashed, skilled, ulted;
    private boolean draftSeen, dashAutoFired, coneSpawned, bossSpawned, skipArmed;
    private long skipArmedAt;
    /** 是否见到过「终极已就绪」；之后它掉下来就说明玩家放过了。 */
    private boolean ultReadySeen;
    /** 自动闪避信号：GameView 读一次 {@link #wantsDash()} 后清零。 */
    private boolean dashPending;
    /** 本步骤放出去的迷你首领 id（-1 = 没有）。 */
    private int minibossId = -1;
    /** 迷你首领被打死之后的计时。 */
    private float bossDeadClock = -1f;
    /** 是否已经脚本收尾（防止重复触发 WON）。 */
    private boolean closed;

    public Onboarding() {}

    // ══════════════════════════════════════════════════════════════
    //  绑定与持久化
    // ══════════════════════════════════════════════════════════════

    /**
     * 绑定 Context。可重复调用，只有第一次会读盘。
     * <p>GameView 里已经持有 {@code activity}，构造之后调用一次 {@code bind(activity)} 即可。
     */
    public Onboarding bind(Context ctx) {
        if (prefs != null || ctx == null) return this;
        try {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            step = clampStep(prefs.getInt(KEY_STEP, NEVER));
        } catch (Throwable t) {
            prefs = null;
            step = NEVER;
        }
        return this;
    }

    /** 无 Context 的降级构造：进度只活在内存里（单元测试 / 预览用）。 */
    public Onboarding withStep(int s) {
        step = clampStep(s);
        return this;
    }

    private static int clampStep(int s) {
        if (s == DONE) return DONE;
        if (s < NEVER) return NEVER;
        if (s >= STEP_COUNT) return DONE;
        return s;
    }

    private void persist(int s) {
        step = s;
        if (prefs == null) return;
        try {
            prefs.edit().putInt(KEY_STEP, s).apply();
        } catch (Throwable ignored) {}
    }

    // ══════════════════════════════════════════════════════════════
    //  查询
    // ══════════════════════════════════════════════════════════════

    /** 引导是否正在进行。已完成 / 从未开始都返回 false。 */
    public boolean active() {
        return step >= 0 && step < STEP_COUNT;
    }

    /** 是否已经完成过（99）。 */
    public boolean done() {
        return step == DONE;
    }

    /** 是否从未开始过（-1）。这是「是不是首局」的唯一判据。 */
    public boolean neverStarted() {
        return step == NEVER;
    }

    /** 当前步骤下标：-1 未开始 / 0..7 进行中 / 99 已完成。 */
    public int step() {
        return step;
    }

    /** 当前步骤内已经过的时间（秒）。 */
    public float stepClock() {
        return stepClock;
    }

    /** 整局引导已过的时间（秒）。未开始时为 0。 */
    public float runClock() {
        return runClock;
    }

    /** 是否正停在第 {@code s} 步。 */
    public boolean at(int s) {
        return step == s;
    }

    /** 玩家是否已经动过摇杆。 */
    public boolean hasMoved() { return moved; }

    /** 玩家是否已经拖过瞄准区。 */
    public boolean hasAimed() { return aimed; }

    // ══════════════════════════════════════════════════════════════
    //  生命周期
    // ══════════════════════════════════════════════════════════════

    /**
     * 首局才开始的语义糖：从未开始则 {@link #start()}，否则什么都不做。
     * @return 是否真的开始了
     */
    public boolean startIfFirstRun() {
        return neverStarted() && start();
    }

    /**
     * 开始一趟新的单机局。尚未完成教学时从移动步骤重新开始，避免上一次中途
     * 返回营地或放弃存档后，从技能/结算的半截脚本恢复并白拿「胜利」。
     * 如果玩家选择的是「继续存档」，GameView 不调用本方法，仍从保存的步骤继续。
     */
    public boolean startForNewRun() {
        if (done()) return false;
        resetRun();
        persist(S_MOVE);
        return true;
    }

    /**
     * 开始引导。只有「从未开始」才生效——已经跑过的一律拒绝，保证永不重播。
     * @return 是否真的开始了
     */
    public boolean start() {
        if (step != NEVER) return false;
        resetRun();
        persist(S_MOVE);
        return true;
    }

    private void resetRun() {
        stepClock = 0;
        runClock = 0;
        moved = aimed = dashed = skilled = ulted = false;
        draftSeen = dashAutoFired = coneSpawned = bossSpawned = skipArmed = false;
        ultReadySeen = dashPending = closed = false;
        minibossId = -1;
        bossDeadClock = -1f;
    }

    /**
     * 局内每帧调用。<b>幂等、绝不抛</b>：{@code g} 为 null 时只推进计时。
     *
     * <p>本方法按优先级做五件事：
     * <ol>
     *   <li>把本地玩家顶成无敌 / 兜底拉血（§5「前五分钟不死人」）；</li>
     *   <li>推进 {@link #stepClock} / {@link #runClock}；</li>
     *   <li>技能步铺 12 敌锥形群、首领步放出迷你首领；</li>
     *   <li>终极步把能量条按脚本充满；</li>
     *   <li>检查完成条件，必要时 {@link #advance()}。</li>
     * </ol>
     *
     * @param g  当前战局（可为 null）
     * @param dt 模拟步长（秒），传入的已经是 fx.timeScale() 之后的量
     */
    public void update(Game g, float dt) {
        if (!active()) return;
        if (!Float.isFinite(dt) || dt <= 0f) return;
        if (dt > .1f) dt = .1f;

        stepClock += dt;
        runClock += dt;

        Game.Player p = localPlayer(g);
        if (p == null) return;      // 还在 LOBBY：只计时，不推进

        guardPlayer(p);
        readPlayerSignals(p);
        boolean fighting = g.phase == Game.FIGHT;

        switch (step) {
            case S_MOVE:
                // 第一次升级（三选一弹出）就进入下一步；否则按超时兜底。
                // 刻意<b>不</b>因为 moved 而提前推进：S_DRAFT 的推荐缎带只按 p.offers 渲染，
                // 提前进入会在正式弹出三选一之前把缎带画到战斗画面上。
                if (g.phase == Game.CHOICE || stepClock >= STEP_MAX[S_MOVE]) advance();
                break;

            case S_DRAFT:
                if (g.phase == Game.CHOICE) draftSeen = true;
                else if (draftSeen) { advance(); break; }   // 选完回到战斗：这一步结束
                // 超时兜底同时覆盖两种情况：一次三选一都没遇到，或玩家停在三选一上不动。
                // （旧实现里还有一个 `p.chosen >= 0` 分支，但 core 在选卡的同一次 update 里
                //   就把阶段切回 FIGHT，draftSeen 分支会先命中，它是死代码。）
                if (stepClock >= STEP_MAX[S_DRAFT]) advance();
                break;

            case S_AIM:
                if (aimed || stepClock >= STEP_MAX[S_AIM]) advance();
                break;

            case S_DASH:
                if (!dashAutoFired && stepClock >= DASH_AUTO_AT) {
                    dashAutoFired = true;
                    dashPending = true;     // 由 GameView 读 wantsDash() 下发
                }
                if (dashed || stepClock >= STEP_MAX[S_DASH]) advance();
                break;

            case S_SKILL:
                if (!coneSpawned && fighting) {
                    coneSpawned = true;
                    spawnCone(g, p);
                }
                if (skilled || stepClock >= STEP_MAX[S_SKILL]) advance();
                break;

            case S_ULT: {
                // 脚本化充能：把能量条按固定节奏推到满
                float k = Math.min(1f, stepClock / ULT_FILL_TIME);
                int want = (int) (k * 100f);
                if (p.ultCharge < want) p.ultCharge = want;
                if (p.ultCharge >= 100) ultReadySeen = true;
                if (ulted || stepClock >= ULT_FILL_TIME + ULT_GRACE) advance();
                break;
            }

            case S_BOSS: {
                if (!bossSpawned && fighting) {
                    bossSpawned = true;
                    spawnMiniboss(g, p);
                }
                // 首领倒下：留一拍再走
                if (minibossId >= 0 && bossDeadClock < 0f && !alive(g, minibossId)) {
                    bossDeadClock = 0f;
                }
                if (bossDeadClock >= 0f) {
                    bossDeadClock += dt;
                    if (bossDeadClock >= BOSS_DEATH_BEAT) { advance(); break; }
                }
                if (stepClock >= STEP_MAX[S_BOSS]) advance();
                break;
            }

            case S_RESULT:
                if (!closed) {
                    closed = true;
                    closeRun(g);
                }
                if (stepClock >= STEP_MAX[S_RESULT]) complete();
                break;

            default:
                break;
        }
    }

    /** 推进到下一步；最后一步自动 {@link #complete()}。 */
    public void advance() {
        if (!active()) return;
        int next = step + 1;
        if (next >= STEP_COUNT) {
            complete();
            return;
        }
        step = next;
        stepClock = 0;
        skipArmed = false;
        if (step == S_SKILL) coneSpawned = false;
        if (step == S_BOSS) { bossSpawned = false; bossDeadClock = -1f; minibossId = -1; }
        persist(step);
    }

    /** 标记完成（写 99，之后永不重播）。 */
    public void complete() {
        persist(DONE);
        stepClock = 0;
        skipArmed = false;
    }

    /** 玩家主动跳过：等同完成，只是不播放收尾。 */
    public void skip() {
        complete();
    }

    /** 本局结束（胜/负/退出）时调用：把内存进度同步回盘并清掉本局临时状态。 */
    public void onRunEnd() {
        skipArmed = false;
        coneSpawned = false;
        bossSpawned = false;
        minibossId = -1;
        bossDeadClock = -1f;
        if (step == DONE) persist(DONE);
    }

    // ══════════════════════════════════════════════════════════════
    //  玩家信号
    // ══════════════════════════════════════════════════════════════

    private void readPlayerSignals(Game.Player p) {
        if (!moved && Math.abs(p.dx) + Math.abs(p.dy) > .12f) moved = true;
        if (!aimed && p.manual > .5f) aimed = true;
        if (!dashed && p.dashCd > 0f) dashed = true;
        if (!skilled && p.skillCd > 0f) skilled = true;
        // 终极：能量从 100 掉下来 → 玩家按了。update 里已经见过满值，这里只判下降沿。
        // 注意 core 的 ultimate() 只清 ultReady，不会把 ultCharge 归零（下一帧 updatePlayer
        // 又会按 ultCharge>=100 把 ultReady 顶回 1），所以这条下降沿只在极少数帧成立。
        // 真正的判据是 GameView 在成功释放后调用的 notifyUlt()。
        if (!ulted && ultReadySeen && p.ultCharge < 100) ulted = true;
    }

    /**
     * GameView 在「终极确实释放成功」时调用。
     * <p>core 的 {@code Game.ultimate()} 不会把 {@code ultCharge} 归零，
     * {@code ultReady} 也会在下一帧被 {@code updatePlayer} 按满能量顶回 1，
     * 因此被动读字段抓不到这个事件；由调用点直接告知是唯一可靠的判据。
     */
    public void notifyUlt() {
        if (!active()) return;
        ulted = true;
    }

    /**
     * 本帧是否需要 GameView 替导演下发一次闪避（3.5 秒没按）。
     * <p>消费型：读一次就清；绝不重复触发。
     */
    public boolean wantsDash() {
        if (!active() || step != S_DASH || !dashPending) return false;
        dashPending = false;
        return true;
    }

    /**
     * GameView 在「玩家按了闪避键」时调用（无论是否真的进入冷却）。
     * <p>被动读 {@code p.dashCd > 0} 会漏掉两种情况：① 玩家在冷却中按了（不该算学会，
     * 但也不会被误判）；② 按键发生在帧之间的同一个 tick 里、冷却已经被下一帧减回 0。
     * 主动告知比猜字段更稳，也让「按过就推进」的节奏更跟手。
     */
    public void notifyDash() {
        if (active()) dashed = true;
    }

    /** GameView 在「玩家按了技能键」时调用；理由同 {@link #notifyDash()}。 */
    public void notifySkill() {
        if (active()) skilled = true;
    }

    /**
     * 「前五分钟不死人」的具体实现：每帧把本地玩家顶成无敌，血线过低直接拉回。
     * <p>不改 core，只写 {@link Game.Player} 的公开字段——与 GameView 已在做的同一层次。
     */
    private void guardPlayer(Game.Player p) {
        if (p.down) {
            // 兜底：理论上不会发生（一直无敌），但发生了也立刻扶起来
            p.down = false;
            p.revive = 0;
            p.hp = p.maxHp * .75f;
        }
        p.invuln = Math.max(p.invuln, SAFETY_INVULN);
        float floorHp = p.maxHp * SAFETY_HP_FRAC;
        if (p.hp < floorHp) p.hp = floorHp;
    }

    // ══════════════════════════════════════════════════════════════
    //  教学刷怪（只写 g.enemies；只在引导激活时发生）
    // ══════════════════════════════════════════════════════════════

    /**
     * 12 个敌人在玩家正前方铺成锥形——形状与「齐射 / 冰棱 / 震荡盾」等技能的实际清场形状一致。
     * 血量刻意压低，一次技能能全清，让因果在一帧内成立。
     */
    private void spawnCone(Game g, Game.Player p) {
        if (g == null || p == null || g.enemies == null) return;
        float ax = p.ax, ay = p.ay;
        if (Math.abs(ax) + Math.abs(ay) < .01f) { ax = 0f; ay = -1f; }
        float baseAngle = (float) Math.atan2(ay, ax);
        for (int i = 0; i < CONE_COUNT; i++) {
            float t = CONE_COUNT <= 1 ? 0f : i / (float) (CONE_COUNT - 1) - .5f;   // -0.5..0.5
            float ang = baseAngle + t * 2f * CONE_HALF_ANGLE;
            float dist = CONE_RADIUS * (.55f + .45f * ((i % 3) / 2f));            // 三层纵深
            Game.Enemy e = new Game.Enemy();
            e.id = g.allocateId();
            e.type = 9;                     // 噬星虫：小、快、血薄，适合被范围技能一次清掉
            e.behavior = Game.B_CHASE;
            e.r = Enemies.RADIUS[9];
            e.speed = 96f;
            e.hp = e.maxHp = CONE_HP;
            e.cooldown = 2.4f;
            e.telegraph = 0f;
            e.stage = -1;
            e.x = clamp(p.x + (float) Math.cos(ang) * dist, 40f, 1080f);
            e.y = clamp(p.y + (float) Math.sin(ang) * dist, 44f, 577f);
            g.enemies.add(e);
        }
    }

    /** 迷你首领：把 4:00–4:45 那一拍变成一个有名字、有血条的对手。 */
    private void spawnMiniboss(Game g, Game.Player p) {
        if (g == null || p == null || g.enemies == null) return;
        int idx = 0;                        // 苔冠守望者：招式最慢、最好读
        Game.Enemy e = new Game.Enemy();
        e.id = g.allocateId();
        e.type = 0;
        e.bossIndex = idx;
        e.behavior = Game.B_CHASE;
        e.elite = true;
        e.r = Bosses.RADIUS[idx];
        e.speed = Bosses.SPEED[idx] * .9f;
        e.hp = e.maxHp = MINIBOSS_HP;
        e.cooldown = 2.4f;
        e.telegraph = 1.2f;
        e.windup = 1.2f;
        e.stage = 0;
        e.phaseTriggers = 0;
        e.variant = Bosses.ART[idx];
        e.x = clamp(p.x, 120f, 1000f);
        e.y = clamp(p.y - 250f, 80f, 540f);
        g.enemies.add(e);
        minibossId = e.id;
    }

    /** 某个敌人是否还活着（找不到也算死了）。 */
    private static boolean alive(Game g, int id) {
        if (g == null || g.enemies == null) return false;
        for (Game.Enemy e : g.enemies) if (e != null && e.id == id && e.hp > 0f) return true;
        return false;
    }

    /**
     * 脚本收尾：如果还在战斗，直接把这一趟判成「完成」，让 ResultScreen 接管。
     * <p>这正是报告里的「mini-boss **or scripted retreat** closes the run at 4:45」。
     * 只在引导激活且玩家还活着时做，且只做一次。
     */
    private void closeRun(Game g) {
        if (g == null) return;
        try {
            // 教学步长与真实房间进度不同步。若恰好停在 CHOICE/SHOP/EVENT，
            // 旧实现只认可 FIGHT，12 秒后写 DONE 却不弹结算，玩家丢失首趟奖励。
            // 脚本「撤离」允许从所有仍在进行的单机局阶段结束。
            if (g.phase != Game.WON && g.phase != Game.LOST && g.phase != Game.LOBBY
                    && g.player(g.captain) != null && !g.player(g.captain).down) {
                g.notice = "第一趟完成 · 深渊记住了你";
                g.phase = Game.WON;
            }
        } catch (Throwable ignored) {}
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    // ══════════════════════════════════════════════════════════════
    //  推荐卡（三选一的高亮目标）
    // ══════════════════════════════════════════════════════════════

    /**
     * 推荐哪张卡。评分规则简单且可解释：武器进化 &gt; 稀有度 &gt; 与本职业流派同源 &gt; 新遗物。
     * @return 0..2；没有可选卡时返回 -1
     */
    public int recommendedCard(Game g, Game.Player p) {
        if (g == null || p == null || p.offers == null) return -1;
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 3 && i < p.offers.length; i++) {
            int relic = p.offers[i];
            int evo = p.evolveOffer != null && i < p.evolveOffer.length ? p.evolveOffer[i] : -1;
            if (relic < 0 && evo < 0) continue;
            int score = 0;
            if (evo >= 0) score += 100000;                       // 进化卡永远最值
            if (relic >= 0) {
                int rid = Math.max(0, Math.min(Relics.COUNT - 1, relic));
                score += Relics.RARITY[rid] * 1000;
                int role = Math.max(0, Math.min(Content.ROLES.length - 1, p.role));
                if (Relics.SCHOOL[rid] == Content.SCHOOL[role]) score += 2500;
                if (p.relics != null && p.relics[rid] <= 0) score += 400;   // 新遗物 &gt; 叠层
                score += (int) (Relics.POWER[rid] * 10000f);
                score += (int) (Relics.RATE[rid] * 10000f);
                score += (int) (Relics.AREA[rid] * 10000f);
            }
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /**
     * 推荐卡的一行数值增量（报告要求「+18% DPS」这种，不是散文）。
     * @return 形如 {@code "+18% 伤害"}；算不出来时返回空串
     */
    public String cardDelta(Game g, Game.Player p, int i) {
        if (p == null || i < 0 || i >= 3) return "";
        if (p.evolveOffer != null && i < p.evolveOffer.length && p.evolveOffer[i] >= 0) return "武器进化";
        if (p.offers == null || i >= p.offers.length || p.offers[i] < 0) return "";
        int rid = Math.max(0, Math.min(Relics.COUNT - 1, p.offers[i]));
        if (Relics.RATE[rid] > 0f) return "+" + Math.round(Relics.RATE[rid] * 100f) + "% 攻速";
        if (Relics.POWER[rid] > 0f) return "+" + Math.round(Relics.POWER[rid] * 100f) + "% 伤害";
        if (Relics.AREA[rid] > 0f) return "+" + Math.round(Relics.AREA[rid] * 100f) + "% 范围";
        return "";
    }

    // ══════════════════════════════════════════════════════════════
    //  跳过
    // ══════════════════════════════════════════════════════════════

    /** 「跳过」控件的命中 id。GameView.activate() 开头交给 {@link #click(int)}。 */
    public static final int A_SKIP_ONBOARDING = 7701;

    // 底部中央的空档：左侧生命面板止于 x=652，右侧瞄准环起于 x=978；
    // 三选一界面的按钮行是 92..422 / 442..622 / 1000..1348，这一块同样空着。
    static final float SKIP_X = 800f, SKIP_Y = 742f, SKIP_W = 120f, SKIP_H = 44f;

    /**
     * 处理一次点击。
     * <p>二次确认：第一次点只是「上膛」（按钮变成「再点一次」），2.5 秒内再点一次才真跳过，
     * 避免玩家在战斗里误触。
     * @return true 表示这次点击被引导消费了，GameView 不要继续往下分发
     */
    public boolean click(int id) {
        if (id != A_SKIP_ONBOARDING || !active()) return false;
        long now = System.nanoTime();
        if (skipArmed && now - skipArmedAt < 2_500_000_000L) {
            skipArmed = false;
            skip();
            return true;
        }
        skipArmed = true;
        skipArmedAt = now;
        return true;
    }

    /** 跳过按钮是否正停在「再点一次」状态。 */
    public boolean skipArmed() {
        return active() && skipArmed && System.nanoTime() - skipArmedAt < 2_500_000_000L;
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制
    // ══════════════════════════════════════════════════════════════

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private static final int WHITE = 0xffedf1e7, MUTED = 0xff94abae, MINT = 0xff9de7c1,
            GOLD = 0xffeac487, BLUE = 0xff93c4ff;

    /**
     * 画引导提示层。请在阶段 {@code switch} <b>之后</b>、覆盖层（背包 / 暂停）<b>之前</b>调用，
     * 这样脉冲能盖在 HUD 与三选一上，又能被暂停面板正确盖住。
     *
     * <p>只画「手势 / 光环 / 缎带」，不接管任何别的屏幕；同时登记「跳过」的命中矩形。
     *
     * @param c  Canvas，已 translate / scale 到 1440×810 逻辑空间
     * @param ui Ui 实例；传 null 时降级为纯 Canvas 描边
     * @param g  当前战局（可为 null）
     * @param p  本地玩家（可为 null）
     */
    public void drawHint(Canvas c, Ui ui, Game g, Game.Player p) {
        if (c == null || !active()) return;
        boolean fighting = g == null || g.phase == Game.FIGHT;
        if (ui != null) drawHintUi(c, ui, g, p, fighting);
        else if (fighting) drawHintRaw(c);
        drawSkip(c, ui);
    }

    private void drawHintUi(Canvas c, Ui ui, Game g, Game.Player p, boolean fighting) {
        switch (step) {
            case S_MOVE: {
                if (moved || !fighting) return;
                float a = fadeIn(stepClock, 2f) * (1f - fadeOut(stepClock, 22f));
                if (a <= .01f) return;
                ghostThumb(c, ui, 322, 590, 96f, a);     // 与 BattleHud 的摇杆同心
                break;
            }
            case S_DRAFT: {
                drawRecommendedRibbon(c, ui, g, p);
                break;
            }
            case S_AIM: {
                if (aimed || !fighting) return;
                float a = fadeIn(stepClock, 1.2f) * (1f - fadeOut(stepClock, 18f));
                if (a <= .01f) return;
                ghostDragFinger(c, ui, 1120, 470, a);
                ui.centerSmall(c, "在右侧拖动瞄准", 1120, 536, 15, ui.alpha(WHITE, (int) (205 * a)));
                break;
            }
            case S_DASH: {
                if (!fighting) return;
                float k = (stepClock % 1.4f) / 1.4f;
                float r = 54f + 34f * k;
                ui.glow(c, 1170, 700, 96f, ui.alpha(MINT, (int) (40 + 40 * pulse(stepClock, 1.4f))));
                ui.ring(c, 1170, 700, r, ui.alpha(MINT, (int) (210 * (1f - k))), 3.6f);
                if (!dashed) ui.centerSmall(c, "闪避", 1170, 788, 14, ui.alpha(MINT, 225));
                break;
            }
            case S_SKILL: {
                if (!fighting) return;
                float k = (stepClock % 1.4f) / 1.4f;
                ui.glow(c, 1300, 660, 96f, ui.alpha(BLUE, (int) (40 + 40 * pulse(stepClock, 1.4f))));
                ui.ring(c, 1300, 660, 54f + 34f * k, ui.alpha(BLUE, (int) (210 * (1f - k))), 3.6f);
                ui.centerSmall(c, "技能 · 清掉这一片", 1300, 748, 13, ui.alpha(BLUE, 220));
                break;
            }
            case S_ULT: {
                if (!fighting) return;
                float k = Math.min(1f, stepClock / ULT_FILL_TIME);
                float pl = pulse(stepClock, 1.1f);
                ui.glow(c, 1300, 552, 88f, ui.alpha(GOLD, (int) (50 + 60 * pl)));
                ui.ring(c, 1300, 552, 46f + 30f * pl, ui.alpha(GOLD, (int) (180 * pl)), 3f);
                if (k < 1f) ui.centerSmall(c, "充能 " + (int) (k * 100) + "%", 1300, 620, 13, ui.alpha(GOLD, 215));
                else ui.centerSmall(c, "按住释放", 1300, 620, 14, ui.alpha(GOLD, 235));
                break;
            }
            case S_BOSS: {
                if (!fighting) return;
                float pl = pulse(stepClock, .9f);
                ui.centerSmall(c, "首领 · 先躲再打", 720, 190, 18, ui.alpha(GOLD, (int) (195 + 55 * pl)));
                break;
            }
            case S_RESULT: {
                ui.centerSmall(c, "第一趟结束 · 星盘已点亮", 720, 252, 18, ui.alpha(GOLD, 220));
                break;
            }
            default:
                break;
        }
    }

    /** ui 为 null 的降级：只描圈，不依赖 Ui 的字形与渐变缓存。 */
    private void drawHintRaw(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        switch (step) {
            case S_MOVE:
                if (moved) return;
                paint.setColor(0x66edf1e7);
                c.drawCircle(322, 590, 96f, paint);
                c.drawCircle(322, 590, 44f, paint);
                break;
            case S_DASH:
                paint.setColor(0xcc9de7c1);
                c.drawCircle(1170, 700, 66f, paint);
                break;
            case S_SKILL:
                paint.setColor(0xcc93c4ff);
                c.drawCircle(1300, 660, 66f, paint);
                break;
            case S_ULT:
                paint.setColor(0xcceac487);
                c.drawCircle(1300, 552, 58f, paint);
                break;
            default:
                break;
        }
    }

    /**
     * 三选一的推荐缎带。
     * <p>卡片几何直接复刻 {@code DraftScreen} 的常量（380×470、间距 40、顶端 y=180、整行居中）
     * ——只读地画在上面，因此<b>不需要改 DraftScreen</b>。
     */
    private void drawRecommendedRibbon(Canvas c, Ui ui, Game g, Game.Player p) {
        if (g == null || p == null) return;
        int idx = recommendedCard(g, p);
        if (idx < 0) return;
        final float cardW = 380f, cardH = 470f, gap = 40f, rowY = 180f;
        float x = (1440f - (cardW * 3f + gap * 2f)) * .5f + idx * (cardW + gap);
        float y = rowY;
        float pl = pulse(stepClock, 1.3f);
        ui.glow(c, x + cardW * .5f, y + cardH * .5f, 210f, ui.alpha(GOLD, (int) (26 + 26 * pl)));
        ui.stroke(c, x - 6, y - 6, cardW + 12, cardH + 12, 20,
                ui.alpha(GOLD, (int) (170 + 70 * pl)), 3f);

        // 顶部缎带
        float bw = 150f, bh = 34f;
        float bx = x + (cardW - bw) * .5f, by = y - 17f;
        ui.box(c, bx, by, bw, bh, bh * .5f, ui.alpha(GOLD, 235));
        ui.center(c, "推荐", bx + bw * .5f, by + bh * .5f, 16, 0xff2a2010, true);

        // 一行数值增量
        String delta = cardDelta(g, p, idx);
        if (delta.length() > 0) {
            ui.centerSmall(c, delta, x + cardW * .5f, y + cardH + 26, 14, ui.alpha(GOLD, 230));
        }
    }

    // ── 跳过按钮 ────────────────────────────────────────────────

    private void drawSkip(Canvas c, Ui ui) {
        boolean hot = skipArmed();
        if (ui != null) {
            ui.hit(A_SKIP_ONBOARDING, SKIP_X, SKIP_Y, SKIP_W, SKIP_H);
            ui.box(c, SKIP_X, SKIP_Y, SKIP_W, SKIP_H, SKIP_H * .5f, hot ? 0xcc3a2528 : 0x55101c23);
            ui.stroke(c, SKIP_X, SKIP_Y, SKIP_W, SKIP_H, SKIP_H * .5f,
                    ui.alpha(hot ? 0xfff18f8b : MUTED, 165), 1f);
            ui.center(c, hot ? "再点一次" : "跳过", SKIP_X + SKIP_W * .5f, SKIP_Y + SKIP_H * .5f,
                    13, ui.alpha(WHITE, 220), false);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55101c23);
            rect.set(SKIP_X, SKIP_Y, SKIP_X + SKIP_W, SKIP_Y + SKIP_H);
            c.drawRoundRect(rect, SKIP_H * .5f, SKIP_H * .5f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0x66edf1e7);
            c.drawRoundRect(rect, SKIP_H * .5f, SKIP_H * .5f, paint);
        }
    }

    // ── 手势图标 ────────────────────────────────────────────────

    /** 幽灵拇指环：大环 + 内环 + 轻微呼吸的指尖点。 */
    private void ghostThumb(Canvas c, Ui ui, float cx, float cy, float radius, float a) {
        ui.circle(c, cx, cy, radius, ui.alpha(WHITE, (int) (14 * a)));
        ui.ring(c, cx, cy, radius, ui.alpha(WHITE, (int) (115 * a)), 2.2f);
        ui.ring(c, cx, cy, radius * .62f, ui.alpha(WHITE, (int) (55 * a)), 1.4f);
        float bob = (float) Math.sin(stepClock * 2.6f) * 6f;
        ui.circle(c, cx + bob, cy + bob * .5f, radius * .30f, ui.alpha(MINT, (int) (85 * a)));
        ui.ring(c, cx + bob, cy + bob * .5f, radius * .30f, ui.alpha(MINT, (int) (175 * a)), 2f);
    }

    /** 幽灵拖动手指：指尖沿右半屏来回拖。 */
    private void ghostDragFinger(Canvas c, Ui ui, float cx, float cy, float a) {
        float travel = 150f;
        float k = (stepClock * .5f) % 1f;
        float x = cx - travel * .5f + travel * k;
        ui.line(c, cx - travel * .5f, cy, x, cy, ui.alpha(BLUE, (int) (70 * a)), 3f);
        ui.circle(c, x, cy, 28f, ui.alpha(BLUE, (int) (38 * a)));
        ui.ring(c, x, cy, 28f, ui.alpha(BLUE, (int) (195 * a)), 2.4f);
        ui.circle(c, x, cy, 9f, ui.alpha(WHITE, (int) (205 * a)));
    }

    // ── 缓动 ────────────────────────────────────────────────────

    private static float fadeIn(float t, float dur) {
        return dur <= 0f ? 1f : Math.max(0f, Math.min(1f, t / dur));
    }

    /** 保持 {@code hold} 秒后，用 8 秒淡出。 */
    private static float fadeOut(float t, float hold) {
        return Math.max(0f, Math.min(1f, (t - hold) / 8f));
    }

    private static float pulse(float t, float period) {
        return period <= 0f ? 0f : .5f + .5f * (float) Math.sin(t * 2f * Math.PI / period);
    }

    // ══════════════════════════════════════════════════════════════
    //  文案
    // ══════════════════════════════════════════════════════════════

    /** 每一步的一行提示（≤ 12 个词）。下标与 {@code S_*} 对应；移动与结算故意留空。 */
    public static final String[] HINTS = {
            "",
            "选一张牌 · 推荐有金边",
            "右侧拖动瞄准",
            "闪避 · 穿过红色预警",
            "技能 · 清掉这一片",
            "终极 · 按住释放",
            "首领 · 先躲再打",
            "第一趟结束"
    };

    /** 取某一步的提示；越界安全。 */
    public static String hintOf(int s) {
        return s >= 0 && s < HINTS.length ? HINTS[s] : "";
    }

    // ══════════════════════════════════════════════════════════════
    //  内部
    // ══════════════════════════════════════════════════════════════

    /** 取本地玩家：单人局就是 captain，联机时退回第 0 位。 */
    private static Game.Player localPlayer(Game g) {
        if (g == null || g.players == null || g.players.isEmpty()) return null;
        Game.Player p = g.player(g.captain);
        return p != null ? p : g.players.get(0);
    }
}
