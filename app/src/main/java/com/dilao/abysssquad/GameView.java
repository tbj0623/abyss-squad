package com.dilao.abysssquad;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.view.*;
import com.dilao.core.*;
import com.dilao.abysssquad.screen.*;
import java.util.*;

/**
 * Android 表现层与输入层。所有玩法逻辑都在共享的 {@link Game} 核心中。
 * <p>职责：
 * <ol>
 *   <li>屏幕坐标变换（逻辑 1440×810 等比缩放居中）</li>
 *   <li>多指触控分发（虚拟摇杆 / 手动瞄准 / 技能 / 闪避 / 终极 / UI 点击）</li>
 *   <li>顶层绘制调度（菜单 → 各 run 界面 → 战斗 → 覆盖层）</li>
 *   <li>表现层反馈（震屏、顿帧、飘字经 Fx；音频经 GameAudio）</li>
 * </ol>
 */
public final class GameView extends View {

    // ── 逻辑分辨率 ──
    static final float W = 1440, H = 810;
    /**
     * 自动暂停后的恢复缓冲（秒）。UX 报告：一次来电就足以毁掉整局，
     * 所以回来时先给 3 秒倒计时，让玩家把手指放回摇杆再开打。
     */
    static final float AUTO_RESUME_DELAY = 3f;
    /** 战斗区偏移与尺寸（1120×620） */
    static final float WX = 160, WY = 106;

    // ── 屏幕 id ──
    // S_PACT / S_DOCTRINE 是 v2.0 新增：契约词条选择、天赋牌选择。
    static final int S_MENU = 0, S_RUN = 1, S_CHAR = 2, S_CODEX = 3, S_META = 4, S_SET = 5,
            S_PACT = 6, S_DOCTRINE = 7;

    private final MainActivity activity;
    private final Art art;
    private final Ui ui;
    private final Fx fx = new Fx();
    private final SharedPreferences prefs;

    private final MenuScreen menu = new MenuScreen();
    private final CharScreen charScreen = new CharScreen();
    /**
     * 首次运行引导（FTUE）导演。构造后 bind 一次；只在<b>单机</b>局内每帧推进。
     * <p>它只被动读 {@link Game} 的公开字段，不改 core，也不依赖任何其它屏幕。
     */
    private final Onboarding onboarding = new Onboarding();
    /** 引导局在地图上替玩家选路的计时（秒）；离开 MAP 即归零，玩家自己点了也自然失效。 */
    private float tutorialMapClock;
    /** 本次结算是否来自教学导演的脚本收尾；只活在当前显示会话里。 */
    private boolean tutorialResult;
    private final CodexScreen codex = new CodexScreen();
    private final PactScreen pactScreen = new PactScreen();
    private final DoctrineScreen doctrineScreen = new DoctrineScreen();
    /** 选词条用的临时 LOBBY 态 Game，不参与真实对局。 */
    private Game pactLobby;
    private final MetaScreen metaScreen = new MetaScreen();
    private final SetScreen setScreen = new SetScreen();
    // 局内界面（由 ui-run 提供；缺失时降级）
    private Object mapScreen, draftScreen, shopScreen, eventScreen, campScreen, hud, resultScreen;

    private Session session;
    private Meta meta;
    private SaveStore saves;
    private GameAudio audio;

    private int screen = S_MENU;
    private float sx, sy, scale = 1;
    private float visualTime;
    private long lastNanos;
    private float fps = 60;
    private float lastDrawMs;
    private long lastLogAt;
    static int perfLog = 0;

    // 输入状态
    private float dx, dy, ax = 1, ay;
    private boolean aiming;
    private int moveId = -1, aimId = -1;
    private float moveOriginX = 170, moveOriginY = 650;
    private int dashSerial, skillSerial;
    private boolean suspended;
    private boolean paused;
    /** 失焦自动暂停（UX 报告 QoL 第 1 项：接电话/切后台不该必死）。 */
    private boolean autoPaused;
    /** 恢复倒计时（秒），>0 时冻结模拟并显示 3-2-1。 */
    private float resumeCountdown;
    private boolean inventory;
    private float transition;
    private float saveClock;
    private int lastPhase = -1;
    /** 结算入场时的视觉时钟：每一局都从零播放奖励入场动画。 */
    private float resultEnteredAt;
    private int lastKills;
    private float previousHp = -1;
    private boolean recorded;

    // 设置
    private boolean sound = true, musicOn = true, vibrate = true;
    private int volume = 70, shakeLevel = 60, quality = 1, showFps = 0, lang = 0;
    // v2.0 无障碍：三个独立滑条（0..100），与 quality（性能档）解耦
    private int flashLevel = 100, particleLevel = 100, numberLevel = 100;
    /** 左撇子镜像布局（UX 报告：左右手习惯不能假设）。 */
    private boolean leftHanded;
    /** 可选单手作战：空白处浮动摇杆 + 情境自动技能，默认关闭。 */
    private boolean oneHand;
    private float lastAssistSkillAt = -1000f, lastAssistUltAt = -1000f;
    private final Paint floatingStickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selMode = Game.MODE_EXPEDITION, selRole = 0, selContract = 1;

    // 战斗区缓存
    private Bitmap arenaCache;
    /** 把 Game 的状态差分翻译成 Fx 调用；GameView 只负责每帧 tick 一次。 */
    private final CombatFeedback combat = new CombatFeedback();
    /** destroy() 之后禁止任何绘制，避免命中已 recycle 的位图。 */
    private volatile boolean destroyed;
    /** 音频懒加载只做一次。 */
    private boolean audioReady;
    /** 从局内暂停面板进入设置时置位，返回时回到局内而不是主菜单。 */
    private boolean settingsFromRun;
    private int arenaKey = -1;

    // 复用绘制对象
    private final AnimatedBackground bg = new AnimatedBackground();

    public GameView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        art = new Art(activity);
        ui = new Ui();
        ui.bindArt(new Ui.Art2() {
            @Override public Bitmap cover(int biome) { return art.sprites.cover(biome); }
            @Override public Bitmap actorCell(int row, int frame) {
                return art.sprites.actorCellBitmap(row, frame);
            }
        });
        prefs = activity.getSharedPreferences("abyss-settings", 0);
        loadSettings();
        saves = new SaveStore(activity);
        meta = Meta.load(activity);
        // 音频不在构造函数里初始化：GameAudio 会同步解 24 个资源并建 SoundPool，
        // 与首帧的图集解码 / RenderThread 启动挤在一起时，会让 performDraw 迟迟
        // 拿不到 GPU fence，主线程卡在 HardwareRenderer.setStopped 上触发 ANR。
        // 改为首帧绘制完成后再懒加载（见 lazyInitAudio()）。
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        selRole = Math.max(0, Math.min(Content.ROLE_COUNT - 1, prefs.getInt("role", 0)));
        // 不调用 setLayerType：默认就是 LAYER_TYPE_NONE（跟随窗口的硬件加速）。
        // 显式设 LAYER_TYPE_HARDWARE 会把整个 1440×810 视图隔离到一张离屏硬件纹理，
        // 在低端 GPU 上把 DequeueBuffer 拖到 482ms 并触发 ANR；而在已挂载的 View 上
        // 再调 setLayerType 会强制重启 HardwareRenderer（主线程卡在
        // RenderProxy::setStopped）。两种情况都要避免，所以这里什么都不做。
        setFocusableInTouchMode(true);
        setContentDescription(oneHand
                ? "深渊小队单手作战。任意空白处拖动移动，自动瞄准、技能与终极。"
                : "深渊小队。左侧拖动移动，右侧拖动瞄准，自动攻击。");
        // 引导进度存在独立的 "abyss-onboarding" prefs 里，不碰 Meta 的 JSON
        onboarding.bind(activity);
        warmUpScreens();
    }

    private void warmUpScreens() {
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.MapScreen");
            mapScreen = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.DraftScreen");
            draftScreen = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.ShopScreen");
            shopScreen = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.EventScreen");
            eventScreen = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.CampScreen");
            campScreen = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.BattleHud");
            hud = c.newInstance();
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName("com.dilao.abysssquad.screen.ResultScreen");
            resultScreen = c.newInstance();
        } catch (Throwable ignored) {}
    }

    // ══════════════════════════════════════════════════════════════
    //  设置
    // ══════════════════════════════════════════════════════════════

    private void loadSettings() {
        sound = prefs.getBoolean("sound", true);
        musicOn = prefs.getBoolean("music", true);
        vibrate = prefs.getBoolean("vibrate", true);
        volume = prefs.getInt("volume", 70);
        shakeLevel = prefs.getInt("shake", 60);
        quality = prefs.getInt("quality", 1);
        showFps = prefs.getInt("showFps", 0);
        lang = prefs.getInt("lang", 0);
        leftHanded = prefs.getBoolean("leftHanded", false);
        oneHand = prefs.getBoolean("oneHand", false);
        // v2.0 无障碍：三个独立滑条（0..100）。闪光默认 60（长途对局不刺眼），其余 100 = 不削减
        flashLevel = prefs.getInt("flash", 60);
        particleLevel = prefs.getInt("particles", 100);
        numberLevel = prefs.getInt("numbers", 100);
        applyFxSettings();
    }

    private void saveSettings() {
        prefs.edit().putBoolean("sound", sound).putBoolean("music", musicOn)
                .putBoolean("vibrate", vibrate).putInt("volume", volume)
                .putInt("shake", shakeLevel).putInt("quality", quality)
                .putInt("showFps", showFps).putBoolean("leftHanded", leftHanded)
                .putBoolean("oneHand", oneHand).putInt("lang", lang)
                .putInt("flash", flashLevel).putInt("particles", particleLevel)
                .putInt("numbers", numberLevel)
                .putInt("role", selRole).apply();
        if (audio != null) {
            audio.setSfxVolume(sound ? volume / 100f : 0);
            audio.setMusicVolume(musicOn ? volume / 100f * .7f : 0);
            audio.setVibration(vibrate);
        }
    }

    /**
     * 把 4 个视觉强度设置同步到 Fx（互相正交且各自独立：震屏 / 闪光 / 粒子密度 / 伤害数字）。
     *
     * <p><b>reduce-motion</b>：系统「动画时长缩放」为 0 时强制把震屏与闪光归零，
     * 与滑条位置无关（WCAG 2.3.3 / UX 报告 §4 第 6 项）。粒子与伤害数字不受影响 ——
     * 它们是静态密度而不是运动，关掉反而降低可读性。</p>
     *
     * <p><b>独立性</b>：这四个滑条直接映射到 Fx，quality（性能档）不再给粒子密度设上限。
     * 旧实现里低画质会把粒子锁在 70%，导致滑条推到 100% 却看不到变化，
     * 与「4 个独立滑条」的语义冲突。低端机的性能预算改由 quality 自己的
     * 画面特效（光效 / 分辨率档）负责。</p>
     */
    private void applyFxSettings() {
        boolean reduceMotion = animatorScaleZero();
        float shake = reduceMotion ? 0f : Math.max(0, Math.min(100, shakeLevel)) / 100f;
        float flash = reduceMotion ? 0f : Math.max(0, Math.min(100, flashLevel)) / 100f;
        fx.setShakeIntensity(shake);
        fx.setFlashIntensity(flash);
        fx.setParticleDensity(Math.max(0, Math.min(100, particleLevel)) / 100f);
        fx.setNumberDensity(Math.max(0, Math.min(100, numberLevel)) / 100f);
    }

    /** 读取系统「动画时长缩放」：0 表示用户已开启 reduce-motion（读不到时按正常处理）。 */
    private boolean animatorScaleZero() {
        try {
            float s = android.provider.Settings.Global.getFloat(
                    activity.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return s == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 打包 13 项设置给 {@code SetScreen}。下标即契约，必须与 {@code SetScreen.O_*} 一一对应：
     * <pre>
     *  0 sound  1 music  2 volume  3 vibrate  4 shake  5 quality
     *  6 showFps  7 lang  8 flash  9 particles  10 numbers  11 leftHanded  12 oneHand
     * </pre>
     */
    private int[] settingsOpts() {
        return new int[]{sound ? 1 : 0, musicOn ? 1 : 0, volume, vibrate ? 1 : 0,
                shakeLevel, quality, showFps, lang,
                flashLevel, particleLevel, numberLevel, leftHanded ? 1 : 0,
                oneHand ? 1 : 0};
    }

    /**
     * 把 SetScreen 写过的 opts 收回到字段并落盘。
     * <p>每次点击都会走到这里，所以「开关一按就持久化」，不需要单独的保存按钮。</p>
     */
    private void applySettingsOpts(int[] o) {
        if (o == null || o.length < 8) return;
        sound = o[0] != 0;
        musicOn = o[1] != 0;
        volume = Math.max(0, Math.min(100, o[2]));
        vibrate = o[3] != 0;
        shakeLevel = Math.max(0, Math.min(100, o[4]));
        quality = Math.max(0, Math.min(2, o[5]));
        showFps = o[6] != 0 ? 1 : 0;
        lang = Math.max(0, Math.min(1, o[7]));
        if (o.length >= 11) {
            flashLevel = Math.max(0, Math.min(100, o[8]));
            particleLevel = Math.max(0, Math.min(100, o[9]));
            numberLevel = Math.max(0, Math.min(100, o[10]));
        }
        if (o.length >= 12) leftHanded = o[11] != 0;
        if (o.length >= 13) oneHand = o[12] != 0;
        setContentDescription(oneHand
                ? "深渊小队单手作战。战斗空白区域任意拖动移动，自动瞄准、技能与终极；闪避仍可手动操作。"
                : "深渊小队。左侧拖动移动，右侧拖动瞄准，自动攻击。");
        applyFxSettings();
        saveSettings();
    }

    // ══════════════════════════════════════════════════════════════
    //  会话生命周期
    // ══════════════════════════════════════════════════════════════

    private void startSolo() {
        leave();
        dashSerial = skillSerial = 0;
        session = new Session();
        session.bindMeta(meta);
        session.solo(selRole, selMode, selMode == Game.MODE_DAILY ? 1 : selContract);
        // 契约词条：PactScreen 选的是临时 LOBBY 态 Game（见 pactHost()），这里把位掩码
        // 带进真实战局，否则「确认出发」选的词条会被新 Game 的默认值 0 覆盖。
        if (pactLobby != null && session.game != null) {
            session.game.pactMods = pactLobby.pactMods;
            session.game.pactHeat = Balance.heatOf(pactLobby.pactMods);
        }
        // 未完成的首局被替换时从头教；选择「继续存档」则保留步骤。
        tutorialResult = false;
        if (onboarding.startForNewRun()) tutorialMapClock = 0;
        screen = S_RUN;
        recorded = false;
        lastPhase = -1;
        lastKills = 0;
        previousHp = -1;
        saveClock = 0;
        paused = false;
        inventory = false;
        transition = .8f;
        requestFocus();
        checkpoint();
        if (audio != null) {
            audio.music(selMode == Game.MODE_DAILY ? GameAudio.MUSIC_COMBAT_0 : GameAudio.MUSIC_COMBAT_0);
        }
    }

    private void resumeSaved() {
        Game loaded = saves.load();
        if (loaded == null) return;
        // 兼容旧版本误将 WON/LOST 留在 .sav 的情况：已结算局不可再次载入领奖。
        if (loaded.phase == Game.WON || loaded.phase == Game.LOST) {
            saves.clear();
            return;
        }
        leave();
        session = new Session();
        session.bindMeta(meta);
        session.restore(loaded);
        tutorialResult = false;
        Game.Player p = loaded.player(loaded.captain);
        if (p != null) {
            p.dx = p.dy = p.manual = 0;
            dashSerial = p.dashSerial;
            skillSerial = p.skillSerial;
            selRole = Math.max(0, Math.min(Content.ROLE_COUNT - 1, p.role));
            selMode = loaded.mode;
            selContract = loaded.contract;
            previousHp = p.hp;
        }
        screen = S_RUN;
        recorded = false;
        lastPhase = -1;
        lastKills = loaded.kills;
        paused = loaded.phase == Game.FIGHT;
        lastNanos = 0;
        requestFocus();
    }

    private void host() {
        leave();
        dashSerial = skillSerial = 0;
        session = new Session();
        session.bindMeta(meta);
        session.host(selRole);
        screen = S_RUN;
        recorded = false;
        lastPhase = -1;
        lastKills = 0;
        previousHp = -1;
        requestFocus();
    }

    void join(String ip, int port, String name, int role) {
        leave();
        dashSerial = skillSerial = 0;
        session = new Session();
        session.bindMeta(meta);
        session.join(ip, port, name, role);
        screen = S_RUN;
        recorded = false;
        lastPhase = -1;
        lastKills = 0;
        previousHp = -1;
        requestFocus();
    }

    private void leave() {
        releaseInput();
        checkpoint();
        // 本局结束（胜/负/退出/返回菜单）都走这里：让引导把临时状态清干净
        onboarding.onRunEnd();
        tutorialMapClock = 0;
        if (session != null) {
            Session old = session;
            session = null;
            old.close();
        }
        paused = inventory = false;
        arenaKey = -1;
        transition = 0;
        if (audio != null) audio.music(GameAudio.MUSIC_MENU);
    }

    private void checkpoint() {
        if (session != null && !session.online() && session.game != null) {
            // 结算时 finishRun 已清档。之后 onPhaseChange/leave 又调用 checkpoint，
            // 不能把 WON/LOST 写回「继续远征」，否则玩家能反复进入已结算战局。
            int phase = session.game.phase;
            if (phase == Game.WON || phase == Game.LOST) return;
            try { saves.save(session.game); } catch (Throwable ignored) {}
        }
    }

    /**
     * 设置页的「重置存档」：局外元进度 + 局内 .sav 一起清掉。
     *
     * <p>这是全局唯一一个不可撤销的破坏性入口，所以它必须真的做事：
     * 清完之后再点「继续」不能回到一个半死的存档上。两步都做 try/catch，
     * 任何一步失败都要在屏幕上说出来，而不是静默当成功。
     */
    private void resetAllProgress() {
        String err = null;
        try {
            meta.resetAll();
        } catch (Throwable t) {
            err = "元进度重置失败：" + t.getClass().getSimpleName();
        }
        try {
            // clearSavedRun 而不是 clear()：clear() 只删 v5 基文件，.bak/.new 和
            // 旧版 v4/v3 存档都留着，而 loadFromDisk 会把它们重新迁移回来 ——
            // 玩家点完「重置存档」，下次进游戏又是一份旧进度。
            saves.clearSavedRun();
        } catch (Throwable t) {
            if (err == null) err = "局内存档清除失败：" + t.getClass().getSimpleName();
        }
        // 内存里的引导 / 结算状态也要跟着归零，否则界面还停在旧进度上
        if (session != null) { leave(); }
        screen = S_SET;
        if (err != null) saves.error = err;
        beep(GameAudio.UI_CLICK);
    }

    private void requestNewRun() {
        // 首次运行：跳过「模式 / 职业 / 契约」全部前置选择，直接开一局最简单的远征
        // （远征模式 + 游侠 + 漫游契约）。UX 报告 §3：0:08–0:20 只有一个 PLAY 按钮，
        // 模式与难度藏在 Advanced 后面；32 节点树在第一次结算前不出现。
        if (!onboarding.done()) {
            selMode = Game.MODE_EXPEDITION;
            selRole = 0;                    // 游侠：射程最长、最好读
            selContract = 0;                // 漫游：HP ×1.02 / DMG ×0.70
            // 首局同样会覆盖旧存档，但 First-run 的本意是「别让玩家在第一个存档
            // 确认框前就被劝退」——这里仍然问一次，只是文案换成新局口径。
            if (saves.available()) {
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("开始第一趟远征？")
                        .setMessage("已有一局可继续的远征。开始第一趟会替换该存档，历史记录保留。")
                        .setNegativeButton("先等等", null)
                        .setPositiveButton("开始第一趟", (d, w) -> startSolo())
                        .show();
            } else startSolo();
            return;
        }
        if (saves.available()) {
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("替换当前远征？")
                    .setMessage("已有一局可继续的单机远征。开始新局会替换该存档，历史记录保留。")
                    .setNegativeButton("先等等", null)
                    .setPositiveButton("开始新局", (d, w) -> startSolo())
                    .show();
        } else startSolo();
    }

    // ══════════════════════════════════════════════════════════════
    //  输入状态
    // ══════════════════════════════════════════════════════════════

    private void releaseInput() {
        dx = dy = 0;
        ax = 1;
        ay = 0;
        aiming = false;
        moveId = aimId = -1;
        if (session != null) session.input(0, 0, ax, ay, false, dashSerial, skillSerial);
    }

    private void setMove(float x, float y) {
        float vx = x - moveOriginX, vy = y - moveOriginY;
        float n = (float) Math.hypot(vx, vy);
        if (n < 12) { dx = dy = 0; return; }
        dx = Math.max(-1, Math.min(1, vx / 70));
        dy = Math.max(-1, Math.min(1, vy / 70));
    }

    private void setAim(float x, float y) {
        float vx = x - 1290, vy = y - 500;
        float n = (float) Math.hypot(vx, vy);
        if (n > 14) {
            ax = vx / n;
            ay = vy / n;
            aiming = true;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制主循环
    // ══════════════════════════════════════════════════════════════

    @Override protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        if (destroyed) return;
        float rawDt = lastNanos == 0 ? 0 : Math.min(.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (rawDt > 0) fps = fps * .9f + (1f / Math.max(.001f, rawDt)) * .1f;
        visualTime += rawDt;

        // 自动暂停恢复倒计时：倒计时结束才真正恢复模拟，避免"回到战场瞬间被围殴"
        if (resumeCountdown > 0) {
            resumeCountdown -= rawDt;
            if (resumeCountdown <= 0) {
                resumeCountdown = 0;
                paused = false;
                lastNanos = 0;
            }
        }

        if (audio != null) audio.state(sound || musicOn, !suspended);

        // 顿帧：战斗期间 timeScale 为 0 时暂停模拟但不暂停视觉
        // v2.0 P0：paused / inventory 必须真正冻结模拟。原实现只在 !paused 分支里
        // 覆盖 simDt，导致暂停时 simDt 仍等于 rawDt —— 遮罩写着"已暂停"但战斗照跑。
        float simDt = rawDt;
        if (paused || inventory || transition > 0) {
            simDt = 0f;
        } else if (session != null && session.game != null
                && session.game.phase == Game.FIGHT) {
            simDt = rawDt * fx.timeScale();
        }
        fx.update(rawDt);
        transition = Math.max(0, transition - rawDt);

        scale = Math.min(getWidth() / W, getHeight() / H);
        sx = (getWidth() - W * scale) * .5f;
        sy = (getHeight() - H * scale) * .5f;

        c.drawColor(Art.BG);
        c.save();
        c.translate(sx, sy);
        c.scale(scale, scale);
        ui.clearHits();

        long drawStart = System.nanoTime();
        switch (screen) {
            case S_MENU: drawMenu(c); break;
            case S_CHAR: drawChar(c); break;
            case S_CODEX: drawCodex(c); break;
            case S_META: drawMeta(c); break;
            case S_SET: drawSettings(c); break;
            case S_PACT: drawPact(c); break;
            case S_DOCTRINE: drawDoctrine(c); break;
            default: drawRun(c, simDt); break;
        }

        long drawEnd = System.nanoTime();
        lastDrawMs = lastDrawMs * .85f + ((drawEnd - drawStart) / 1_000_000f) * .15f;
        if (showFps > 0) art.small(c, String.format(Locale.US, "%.0f FPS  绘制%.1fms", fps, lastDrawMs), 12, 24, 14, Art.MUTED);
        // showFps 打开时顺带打性能日志：以前 perfLog 是一个没有任何入口的 static 字段
        // （全项目零赋值），所以 Section「性能」里那些 ABYSSPERF 数据其实永远不会输出。
        if (showFps > 0) perfLog = 1;
        c.restore();
        if (perfLog > 0 && System.currentTimeMillis() - lastLogAt > 2000) {
            lastLogAt = System.currentTimeMillis();
            android.util.Log.i("ABYSSPERF", "draw=" + String.format(Locale.US, "%.1f", lastDrawMs)
                    + "ms fps=" + String.format(Locale.US, "%.0f", fps) + " screen=" + screen
                    + " bg=" + String.format(Locale.US, "%.1f", bgMs)
                    + " ui=" + String.format(Locale.US, "%.1f", menuMs));
        }
        if (!suspended) {
            // 绘制过慢时降频，避免把主线程排满；正常情况下按 vsync 节奏。
            if (lastDrawMs > 28f) postInvalidateDelayed(24);
            else postInvalidateOnAnimation();
        }
        // 首帧已经画完，这时再初始化音频不会和 RenderThread 起动抢资源
        lazyInitAudio();
    }


    /** 首帧画完之后再初始化音频，避免与图集解码/RenderThread 起动争主线程。 */
    private void lazyInitAudio() {
        if (audioReady || destroyed) return;
        audioReady = true;
        try {
            audio = new GameAudio(activity);
            if (audio != null) audio.state(sound || musicOn, !suspended);
        } catch (Throwable ignored) {
            audio = null;
        }
    }
    private int[] stats() {
        return new int[]{meta.dust, meta.marks, meta.bestFloor, meta.runs, meta.wins};
    }

    /** 结算额外带入「是否教学局」标记，保持其它屏幕原有 stats 契约。 */
    private int[] resultStats() {
        return new int[]{meta.dust, meta.marks, meta.bestFloor, meta.runs, meta.wins,
                tutorialResult ? 1 : 0};
    }

    private void drawMenu(Canvas c) {
        long t0 = System.nanoTime();
        bg.draw(c, art, visualTime, screen == S_MENU);
        long t1 = System.nanoTime();
        menu.draw(c, ui, visualTime, stats(), saves.available(), sound);
        long t2 = System.nanoTime();
        if (perfLog > 0) {
            bgMs = bgMs * .8f + ((t1 - t0) / 1_000_000f) * .2f;
            menuMs = menuMs * .8f + ((t2 - t1) / 1_000_000f) * .2f;
        }
    }

    private float bgMs, menuMs;

    private void drawChar(Canvas c) {
        bg.draw(c, art, visualTime, false);
        charScreen.setSelection(selMode, selRole, selContract);
        charScreen.draw(c, ui, visualTime, new int[]{meta.marks, meta.pactUnlocked},
                meta.roleUnlocked);
    }

    private void drawCodex(Canvas c) {
        bg.draw(c, art, visualTime, false);
        int[] ach = new int[Achievements.COUNT];
        for (int i = 0; i < Achievements.COUNT; i++) ach[i] = Achievements.progress(meta, i);
        codex.draw(c, ui, visualTime, meta.relicSeen, meta.enemySeen, meta.bossSeen,
                meta.bossKills, ach);
    }

    private void drawMeta(Canvas c) {
        bg.draw(c, art, visualTime, false);
        metaScreen.bindMeta(meta);
        metaScreen.draw(c, ui, visualTime, new int[]{meta.dust, meta.marks}, meta.nodeLevel);
    }

    private void drawSettings(Canvas c) {
        bg.draw(c, art, visualTime, false);
        setScreen.draw(c, ui, visualTime, settingsOpts(), fps);
    }

    /**
     * 契约词条选择。需要一份 LOBBY 阶段的 Game 才能读写 pactMods，
     * 没有会话时临时造一个只用于选词条的实例（不会开局）。
     */
    private void drawPact(Canvas c) {
        bg.draw(c, art, visualTime, false);
        Game g = pactHost();
        if (g == null) { c.drawColor(Art.BG); return; }
        pactScreen.draw(c, ui, visualTime, g);
    }

    private void drawDoctrine(Canvas c) {
        bg.draw(c, art, visualTime, false);
        doctrineScreen.draw(c, ui, visualTime, meta);
    }

    /** PactScreen 需要一个 Game 读 pactMods；没有会话时临时造一个 LOBBY 态的。 */
    private Game pactHost() {
        Game host = null;
        if (session != null && session.game != null && session.game.phase == Game.LOBBY) {
            host = session.game;
        } else {
            if (pactLobby == null) {
                try {
                    pactLobby = new Game(0L);
                    Game.Player p = pactLobby.addPlayer(1, "p",
                            Math.max(0, Math.min(11, selRole)));
                    if (p != null) pactLobby.captain = p.id;
                } catch (Throwable t) {
                    pactLobby = null;
                }
            }
            host = pactLobby;
        }
        // 把"已通关的最高契约档"喂给 PactScreen，让它按进度开放词条。
        // 没有这一步，Balance.PACT_UNLOCKED 是 static final，12 条里有 8 条永久锁死。
        if (host != null && meta != null) host.pactUnlocked = Math.max(0, meta.highestContractCleared);
        return host;
    }

    // ── 局内 ──────────────────────────────────────────────────────

    /**
     * 单手模式只在可接收输入的战斗帧触发：空场/商店/转场不自动消耗技能。
     * 联机客户端可能比服务端晚收到冷却更新，额外的实时时间闸门防止同一决定
     * 在一段网络延迟内连续重复发送。技能仍经 Session.input 的权威入口处理。
     */
    private void assistCombat(Game g) {
        if (!oneHand || g == null || session == null) return;
        Game.Player p = g.player(session.localId);
        int actions = OneHandAssist.actions(g, p);
        if ((actions & OneHandAssist.SKILL) != 0 && visualTime - lastAssistSkillAt >= .75f) {
            lastAssistSkillAt = visualTime;
            skillSerial++;
            pressHud(1);
            beep(GameAudio.SKILL);
            if (!session.online()) onboarding.notifySkill();
        }
        if ((actions & OneHandAssist.ULTIMATE) != 0 && visualTime - lastAssistUltAt >= 1.25f) {
            lastAssistUltAt = visualTime;
            session.ultimate();
            pressHud(2);
            beep(GameAudio.ULT);
            if (!session.online()) onboarding.notifyUlt();
        }
    }

    private void drawRun(Canvas c, float simDt) {
        if (session == null) { leave(); return; }
        Game g = session.game;
        if (g == null) { drawConnecting(c); return; }

        boolean canLead = !session.online() || g.captain == session.localId;
        boolean blockInput = paused || inventory || transition > .01f;
        if (!blockInput && g.phase == Game.FIGHT) assistCombat(g);
        session.input(blockInput ? 0 : dx, blockInput ? 0 : dy, ax, ay,
                !blockInput && aiming, dashSerial, skillSerial);
        session.update(simDt);

        g = session.game;
        if (g == null) { drawConnecting(c); return; }
        Game.Player p = g.player(session.localId);

        // 状态差分 → 粒子 / 飘字 / 震屏。缺了这一行，整个 Fx 层在运行期就是死代码。
        if (g.phase == Game.FIGHT && !paused && !inventory && transition <= 0) {
            combat.tick(g, fx, simDt, session.localId);
        }

        // 阶段切换
        if (g.phase != lastPhase) {
            onPhaseChange(g, p);
        }
        if (!paused && !inventory) {
            saveClock += simDt;
            if (saveClock >= 6) { checkpoint(); saveClock = 0; }
        }
        // 首次运行引导：只在单机局推进。dt 必须是 fx.timeScale() 之后的模拟步长
        // （drawRun 拿到的 simDt 已经是这一层）；引导会把本地玩家顶成无敌。
        boolean tutorial = !session.online() && onboarding.active();
        if (tutorial && !paused && !inventory
                && g.phase != Game.WON && g.phase != Game.LOST) {
            onboarding.update(g, simDt);
            // 3.5 秒没按闪避时，由导演替玩家静默触发一次（消费型，只触发一次）。
            // 只在真正能接受输入的战斗帧下发：转场 / 非战斗阶段里发出去也没人消费，
            // 而 wantsDash() 是一次性的，白白烧掉这次自动闪避。
            if (g.phase == Game.FIGHT && transition <= 0 && !blockInput
                    && onboarding.wantsDash()) dashSerial++;
            // 「确认出发」之后是星图而不是第一个房间：引导局自动选第一条路线，
            // 让玩家 0:00 就落在战斗里，而不是先面对一张不会用的地图。
            // tutorialRoute 内部会自己调 onPhaseChange，这里不要再补一次（会重复播音乐 / 音效）。
            tutorialMapClock = tutorialRoute(g, simDt, tutorialMapClock);
            // 导演在这一帧把 FIGHT 改为 WON：立即结算并写 DONE。
            // 否则结果按钮可能在下一帧统计前就被点击，导致半截引导重播。
            if ((g.phase == Game.WON || g.phase == Game.LOST) && g.phase != lastPhase) {
                onPhaseChange(g, p);
            }
        } else {
            tutorialMapClock = 0;
        }
        feedback(g);

        // 战斗区
        if (g.phase == Game.FIGHT || transition > 0) {
            drawArena(c, g, p);
        }

        // 各阶段界面
        switch (g.phase) {
            case Game.FIGHT:
                drawHud(c, g, p, canLead);
                if (oneHand) drawOneHandOverlay(c);
                break;
            case Game.MAP:
                backgroundPanel(c);
                callScreen(mapScreen, "Map", c, ui, visualTime, g, canLead);
                break;
            case Game.CHOICE:
                backgroundPanel(c);
                callScreen(draftScreen, "Draft", c, ui, visualTime, g, p, canLead);
                break;
            case Game.SHOP:
                backgroundPanel(c);
                callScreen(shopScreen, "Shop", c, ui, visualTime, g, canLead);
                break;
            case Game.EVENT:
                backgroundPanel(c);
                callScreen(eventScreen, "Event", c, ui, visualTime, g, canLead);
                break;
            case Game.CAMP:
            case Game.ARMORY:
            case Game.SHRINE:
                backgroundPanel(c);
                callScreen(campScreen, "Camp", c, ui, visualTime, g, canLead);
                break;
            case Game.WON:
            case Game.LOST:
                drawArena(c, g, p);
                callScreen(resultScreen, "Result", c, ui,
                        Math.max(0f, visualTime - resultEnteredAt), g,
                        g.phase == Game.WON, false, resultStats());
                break;
            case Game.LOBBY:
                drawLobby(c, g);
                break;
            default:
                drawArena(c, g, p);
                break;
        }

        // 引导提示层：画在阶段界面之后、覆盖层之前 —— 脉冲能盖住 HUD 与三选一，
        // 又能被暂停 / 背包正确盖住。同时登记「跳过」的命中矩形。
        // 结算页（WON/LOST）不画：那里已经有「再来一局 / 返回营地」，再压一个跳过按钮
        // 既没意义又可能挡住 ResultScreen 的按钮。
        if (tutorial && !paused && !inventory
                && g.phase != Game.WON && g.phase != Game.LOST) {
            onboarding.drawHint(c, ui, g, p);
        }

        // 覆盖层
        if (inventory) drawInventory(c, g, p);
        if (paused) {
            if (autoPaused) {
                drawAutoPause(c);
            } else if (resultScreen != null) {
                callScreen(resultScreen, "Result", c, ui, visualTime, g, false, true, stats());
            } else drawPauseFallback(c);
        }

        // 换房转场
        if (transition > 0 && g.phase == Game.FIGHT && !paused) {
            float a = Math.min(1, transition / Math.min(.6f, transition + .001f));
            art.box(c, 0, 0, W, H, 0, art.alpha(Art.BG, (int) (a * 235)));
            art.centerShadow(c, Content.BIOME_NAMES[Math.min(5, g.biome)], 720, 350, 46,
                    Art.WHITE, true);
            art.center(c, Content.ROOM_TYPES[Math.min(9, g.roomType)] + "  ·  "
                    + Rooms.LAYOUTS[Math.max(0, Math.min(Rooms.LAYOUTS.length - 1, g.layout))],
                    720, 404, 22, Art.GOLD, false);
            art.center(c, g.sceneHint, 720, 458, 17, Art.MUTED, false);
        }

        // 联机状态
        if (session.client != null) {
            GameClient cl = session.client;
            if (!cl.connected || System.nanoTime() - cl.receivedAt > 3_000_000_000L) {
                art.box(c, 315, 14, 810, 50, 12, 0xf0443230);
                art.center(c, cl.status + " · 返回键可离开", 720, 46, 18, Art.GOLD, false);
            }
        }

        // 屏幕特效
        fx.drawScreen(c, W, H);
        if (!saves.error.isEmpty()) {
            art.center(c, saves.error, 720, 794, 14, Art.RED, false);
        }
    }

    /**
     * 引导局的星图自动选路：停在地图上 0.9 秒后自动走第一条可用路线。
     * <p>只推动玩家「已经确认出发」后的这一步，其余房间（三选一 / 商店 / 营地）仍然
     * 按导演的步骤节奏由玩家自己操作；离开 MAP 后计时归零，玩家手动点过也会立刻生效。
     *
     * @return 新的计时值（离开 MAP / 选了路之后为 0）
     */
    private float tutorialRoute(Game g, float dt, float clock) {
        if (g == null || g.phase != Game.MAP || session == null || session.online()) return 0f;
        clock += Math.max(0f, dt);
        if (clock >= .9f && g.captain == session.localId) {
            // 第一站优先战斗房：引导的 0:20–0:55 是「三个慢速敌人从正面来」，
            // 精英 / 挑战房的目标不同，会把节奏打乱。找不到战斗房时退回第一条路线。
            int pick = 0;
            int n = Math.max(1, Math.min(3, g.routeCount));
            for (int i = 0; i < n; i++) {
                if (g.routeOptions[i] == Game.ROOM_COMBAT) { pick = i; break; }
            }
            // 与 routeClick 完全同构：这里的 onPhaseChange 不是多余的——它在同一帧里
            // 就把转场 / 音乐 / 暗角切过去，drawRun 开头的 lastPhase 检查随后自然跳过。
            session.action(0, pick);
            transition = .75f;
            arenaKey = -1;
            Game.Player me = session.game == null ? null : session.game.player(session.localId);
            onPhaseChange(session.game, me);
            return 0f;
        }
        return clock;
    }

    private void onPhaseChange(Game g, Game.Player p) {
        if (g.phase == Game.FIGHT) combat.reset();
        releaseInput();
        lastPhase = g.phase;
        inventory = false;
        if (g.phase == Game.FIGHT) {
            transition = .75f;
            arenaKey = -1;
            fx.fadeIn(.35f);
            if (g.roomType == Game.ROOM_BOSS) {
                if (audio != null) { audio.music(GameAudio.MUSIC_BOSS); audio.play(GameAudio.BOSS_WARN); }
                haptic(60);          // 首领登场：长预警，与画面暗角/音乐同时给
                fx.vignettePulse(0xffefbe8a, .6f, 1.2f);
                fx.setVignette(.45f);   // 首领战加深常驻暗角（0.25 → 0.45）
            } else {
                fx.setVignette(.25f);   // 普通房间回到常驻强度
                if (audio != null) {
                    audio.music(g.biome % 3 == 0 ? GameAudio.MUSIC_COMBAT_0
                            : g.biome % 3 == 1 ? GameAudio.MUSIC_COMBAT_1 : GameAudio.MUSIC_COMBAT_0);
                }
            }
        } else if (g.phase == Game.WON) {
            resultEnteredAt = visualTime;
            if (audio != null) { audio.music(GameAudio.MUSIC_MENU); audio.play(GameAudio.VICTORY); }
            fx.flash(0xffeac487, .5f);
            if (!recorded) { recorded = true; finishRun(g, true); }
        } else if (g.phase == Game.LOST) {
            resultEnteredAt = visualTime;
            if (audio != null) audio.play(GameAudio.DEFEAT);
            if (!recorded) { recorded = true; finishRun(g, false); }
        } else if (g.phase == Game.CHOICE) {
            if (audio != null) audio.play(GameAudio.LEVELUP);
            haptic(20);              // 升级：正向里程碑，短促一下
        }
        checkpoint();
    }

    private void finishRun(Game g, boolean win) {
        try {
            if (!session.online()) {
                // 受保护的首局是实操教学：给资源，不冒充正式通关。
                tutorialResult = win && onboarding.at(Onboarding.S_RESULT);
                if (tutorialResult) meta.recordTutorial(g);
                else meta.recordRun(g, win);
                if (win && onboarding.active()) onboarding.complete();
                saves.clear();
            }
        } catch (Throwable ignored) {}
    }

    private Bitmap panelCache;
    private int panelBiome = -1;

    private void backgroundPanel(Canvas c) {
        Game g = session == null ? null : session.game;
        int biome = g == null ? 0 : g.biome;
        if (panelCache == null || panelBiome != biome) {
            try {
                if (panelCache != null) panelCache.recycle();
                panelCache = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888);
                Canvas pc = new Canvas(panelCache);
                pc.drawColor(Art.BG);
                Bitmap cover = art.sprites.cover(biome);
                if (cover != null) {
                    RectF dstRect = new RectF(-30, -30, 750, 435);
                    Paint pp = new Paint(Paint.FILTER_BITMAP_FLAG);
                    pp.setAlpha(38);
                    pc.drawBitmap(cover, null, dstRect, pp);
                }
                Paint gp = new Paint();
                gp.setShader(new LinearGradient(0, 0, 0, 405, 0x000b141b, 0xcc0b141b,
                        Shader.TileMode.CLAMP));
                pc.drawRect(0, 0, 720, 405, gp);
                panelBiome = biome;
            } catch (Throwable t) {
                panelCache = null;
            }
        }
        if (panelCache != null) {
            art.r.set(0, 0, W, H);
            c.drawBitmap(panelCache, null, art.r, null);
        } else {
            c.drawColor(Art.BG);
        }
    }

    private void drawConnecting(Canvas c) {
        backgroundPanel(c);
        art.portal(c, 720, 390, .9f, visualTime, Art.MINT);
        String status = session == null ? "正在准备…" : session.status;
        art.center(c, status, 720, 540, 25, Art.WHITE, true);
        art.center(c, "请保持屏幕前台，等待房主与队员连接。", 720, 580, 19, Art.MUTED, false);
        ui.button(c, 999, 540, 620, 360, 60, "返回营地", false, true);
    }

    private void drawLobby(Canvas c, Game g) {
        backgroundPanel(c);
        art.text(c, "营地 / 小队集合", 76, 96, 34, Art.WHITE, true);
        art.small(c, "同一 Wi-Fi 或热点 · 本版不含公网匹配", 80, 128, 15, Art.MUTED);
        art.box(c, 70, 160, 1300, 64, 14, Art.PANEL);
        art.small(c, "房间地址", 94, 200, 19, Art.MUTED);
        art.small(c, session == null ? "" : session.address, 230, 200, 24, Art.MINT);
        for (int i = 0; i < 4; i++) {
            float x = 70 + i * 330;
            art.box(c, x, 250, 306, 250, 18, Art.PANEL);
            if (i < g.players.size()) {
                Game.Player p = g.players.get(i);
                art.glow(c, x + 153, 350, 62, Content.COLORS[p.role]);
                art.avatar(c, x + 153, 372, 2.3f, p.role, visualTime * 2, 0, false);
                art.center(c, p.name, x + 153, 442, 22, Art.WHITE, true);
                art.center(c, Content.ROLES[p.role], x + 153, 478, 17,
                        Content.COLORS[p.role], false);
            } else {
                art.center(c, "＋", x + 153, 370, 48, Art.EDGE, false);
                art.center(c, "等待朋友加入", x + 153, 450, 18, Art.MUTED, false);
            }
        }
        if (session != null && session.localId == g.captain) {
            ui.button(c, 20, 990, 682, 380, 72, "全员出发  →", true, true);
        } else {
            art.center(c, "等待房主开始", 1180, 720, 20, Art.GOLD, false);
        }
        ui.button(c, 17, 70, 690, 230, 60, "离开小队", false, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  战斗区绘制
    // ══════════════════════════════════════════════════════════════

    private void drawArena(Canvas c, Game g, Game.Player p) {
        int key = g.room * 131 + g.biome * 17 + g.layout * 7 + (g.mirror ? 1 : 0);
        if (arenaCache == null || arenaKey != key) {
            if (arenaCache != null) arenaCache.recycle();
            try {
                arenaCache = Bitmap.createBitmap(1120, 620, Bitmap.Config.ARGB_8888);
                SceneArt.bakeFloor(new Canvas(arenaCache), art, g);
                arenaKey = key;
            } catch (Throwable t) {
                arenaCache = null;
            }
        }
        float ax0 = fx.shakeX();
        float ay0 = fx.shakeY();
        c.save();
        c.translate(WX + ax0, WY + ay0);
        c.clipRect(0, 0, 1120, 620);
        if (arenaCache != null) c.drawBitmap(arenaCache, 0, 0, null);
        SceneArt.objects(c, art, g, visualTime);
        fx.drawUnder(c);
        // 分层：飘字/伤害数字在实体之下（否则会盖住玩家，导致「看不见自己」）
        fx.drawNumbers(c);
        drawGems(c, g);
        drawEntities(c, g, p);
        fx.draw(c);
        SceneArt.overlay(c, art, g, visualTime);
        c.restore();
        c.save();
        c.translate(WX + ax0 * .35f, WY + ay0 * .35f);
        SceneArt.frame(c, art, g);
        c.restore();
    }

    private void drawGems(Canvas c, Game g) {
        for (Game.Gem gem : g.gems) {
            float bob = (float) Math.sin(visualTime * 4 + gem.id) * 2;
            art.glow(c, gem.x, gem.y + bob, 14, Art.GOLD);
            art.diamond(c, gem.x, gem.y + bob, 7, Art.GOLD);
        }
    }

    private void drawEntities(Canvas c, Game g, Game.Player me) {
        // 危险区预警：填充扇形（会涨满的面积）+ 描边，面积通道在 60 个描边里仍然可读
        for (Game.Enemy e : g.enemies) {
            if (e.telegraph > 0) {
                float t = 1 - Math.max(0, Math.min(1, e.telegraph
                        / Math.max(.01f, Enemies.TELEGRAPH[Math.max(0,
                        Math.min(Enemies.COUNT - 1, e.type))])));
                float r = e.r + 10 + t * 14;
                float prog = e.windupProgress();
                if (prog < 0f) prog = t;   // 没有 v1.1 预警数据时退回旧口径
                fx.telegraphFill(c, e.x, e.y, r, prog, 0x99f2bd8c, e.threat);
                if (e.behavior == Game.B_CHARGER || (e.bossIndex >= 0 && e.chargeTime > 0)) {
                    art.line(c, e.x, e.y, e.tx, e.ty, 0x55eea39d, e.r * 1.2f);
                    art.line(c, e.x, e.y, e.tx, e.ty, 0xddffb09c, 2);
                }
            }
        }
        // 子弹
        for (Game.Shot s : g.shots) {
            int color;
            if (s.enemy) color = Art.RED;
            else {
                Game.Player owner = g.player(s.owner);
                color = owner == null ? Art.MINT : Content.COLORS[owner.role];
            }
            float tx = s.x - s.vx * .022f, ty = s.y - s.vy * .022f;
            art.line(c, tx, ty, s.x, s.y, art.alpha(color, 130), Math.max(2, s.r * 1.4f));
            art.circle(c, s.x, s.y, s.r + 2, art.alpha(color, 60));
            art.circle(c, s.x, s.y, s.r, color);
        }
        // 敌人
        for (Game.Enemy e : g.enemies) drawEnemy(c, e);
        // 炮台 / 召唤物
        for (Game.Turret t : g.turrets) {
            art.glow(c, t.x, t.y, 30, 0xff88d2d0);
            art.box(c, t.x - 13, t.y - 11, 26, 25, 5, 0xff457f83);
            art.ring(c, t.x, t.y, 10, Art.MINT, 3);
            art.bar(c, t.x - 19, t.y + 20, 38, 3, t.life / 10f, Art.MINT);
        }
        for (Game.Minion m : g.minions) {
            art.glow(c, m.x, m.y, 18, 0xffb9d0ff);
            art.circle(c, m.x, m.y, 6, 0xffdfe9ff);
        }
        // 玩家
        for (Game.Player p : g.players) {
            if (p.id != session.localId) {
                art.avatar(c, p.x, p.y, 1.0f, p.role, g.time * 6 + p.id,
                        (float) Math.atan2(p.ay, p.ax), p.down);
                art.bar(c, p.x - 24, p.y - 40, 48, 4, p.hp / Math.max(1, p.maxHp),
                        Content.COLORS[p.role]);
                art.center(c, p.name + (p.connected ? "" : " · 离线"), p.x, p.y + 40, 12,
                        p.connected ? Art.WHITE : Art.MUTED, false);
                continue;
            }
            if (p.down) {
                art.avatar(c, p.x, p.y, 1.1f, p.role, 0, 0, true);
                art.ring(c, p.x, p.y, 64, 0x666ca5b4, 1);
                if (g.soloRun && g.freeRevivesUsed < g.freeReviveCount) {
                    art.center(c, "星火重燃 " + (int) (p.revive / 2.5f * 100) + "%",
                            p.x, p.y - 50, 14, Art.GOLD, true);
                } else {
                    art.center(c, "救援 " + (int) (p.revive / 3f * 100) + "%",
                            p.x, p.y - 50, 14, Art.GOLD, true);
                }
                continue;
            }
            art.ring(c, p.x, p.y + 10, 26, 0x779de7c1, 2);
            if (p.invuln > 0) art.ring(c, p.x, p.y, 32, 0x99efcc8b, 2);
            art.avatar(c, p.x, p.y, 1.12f, p.role,
                    (Math.abs(p.dx) + Math.abs(p.dy) > .1f ? g.time * 7 : 0),
                    (float) Math.atan2(p.ay, p.ax), false);
            art.center(c, p.name, p.x, p.y + 46, 11, Art.WHITE, false);
            art.bar(c, p.x - 26, p.y - 42, 52, 5, p.hp / Math.max(1, p.maxHp),
                    Content.COLORS[p.role]);
            if (p.shield > 0 && p.shieldMax > 0)
                art.bar(c, p.x - 26, p.y - 48, 52, 3, p.shield / Math.max(1, p.shieldMax),
                        0xffcfe6f2);
        }
        // 游戏特效实体
        for (Game.Effect e : g.effects) {
            float alpha = Math.max(0, e.life / Math.max(.001f, e.maxLife));
            float r = e.r * (1 - alpha * .6f);
            int color = art.alpha(e.color, (int) (alpha * 190));
            if (e.type == 4) {
                art.centerShadow(c, Integer.toString(Math.max(1, (int) e.value)),
                        e.x, e.y - (1 - alpha) * 28, 16, e.color, true);
                continue;
            }
            art.ring(c, e.x, e.y, r, color, 2);
            if (e.type == 3) {
                for (int j = 0; j < 6; j++) {
                    float ang = j * 1.047f + e.id;
                    art.diamond(c, e.x + (float) Math.cos(ang) * r,
                            e.y + (float) Math.sin(ang) * r, 4 * alpha, color);
                }
            }
        }
    }

    private void drawEnemy(Canvas c, Game.Enemy e) {
        float size;
        if (e.bossIndex >= 0) size = Bosses.RADIUS[e.bossIndex] * 4.4f;
        else size = Enemies.RADIUS[Math.max(0, Math.min(Enemies.COUNT - 1, e.type))] * 5.2f;
        float anim = visualTime * 2.2f + e.id;
        boolean flip = e.x > 560;
        if (e.bossIndex >= 0) {
            // 首领：底部光晕 + 阴影
            art.glow(c, e.x, e.y, e.r * 1.8f, 0x33efbe8a);
            art.enemy(c, e.x, e.y, size, 0, anim, flip, 0);
            // 用 boss 图集覆盖
            art.boss(c, e.x, e.y, size, e.bossIndex, visualTime, flip);
        } else {
            art.enemy(c, e.x, e.y, size, e.type, anim, flip, 255);
        }
        // 本体受击闪白（清单头号项）：每次命中让整个剪影亮起来，而不是只画一圈环
        fx.enemyFlash(c, e.x, e.y, size, combat.flashOf(e));
        if (e.hit > 0) {
            art.ring(c, e.x, e.y, e.r + 4, 0x88ffdbb0, 3);
        }
        if (e.stun > 0) {
            art.ring(c, e.x, e.y - e.r - 6, 7, Art.GOLD, 2);
        }
        if (e.guard > 0) {
            art.ring(c, e.x, e.y, e.r + 7, 0xaa9fc5d8, 2);
        }
        if (e.shield > 0) {
            art.ring(c, e.x, e.y, e.r + 5, 0x889dc6e0, 2);
        }
        if (e.hp < e.maxHp || e.bossIndex >= 0) {
            float w = e.bossIndex >= 0 ? e.r * 2.4f : e.r * 2;
            art.bar(c, e.x - w * .5f, e.y - e.r - 16, w, e.bossIndex >= 0 ? 6 : 4,
                    e.hp / Math.max(1, e.maxHp), e.bossIndex >= 0 ? Art.RED : 0xffd8a782);
        }
        if (e.elite) art.diamond(c, e.x, e.y - e.r - 28, 5,
                e.affix == 1 ? 0xffaed2df : e.affix == 2 ? Art.GOLD : 0xffbb93d3);
        if (e.slow > 0) art.ring(c, e.x, e.y + 6, e.r + 5, 0x6683c9e8, 1);
        if (e.burn > 0) art.diamond(c, e.x + e.r, e.y - 8, 5, 0xffffb784);
    }

    // ══════════════════════════════════════════════════════════════
    //  反射调用局内界面（由 ui-run 提供，缺失时降级）
    // ══════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════
    //  反射调用局内界面
    //
    //  为什么还在用反射：这些 screen 类由不同批次并行落地，方法签名可能在
    //  编译期与运行期不一致，反射让"类缺失/签名不匹配"降级成占位面板而不是崩。
    //
    //  但**绝不能每帧重新查找**：原来每帧调用 getMethod()（全表扫描 + 复制
    // 方法数组）再 invoke，并且每次都 new 一个 Class[] —— 60fps 下每秒 60 次
    // 反射查找 + 60 次数组分配。这里把 Method 与签名按 (类, tag) 缓存起来，
    // 之后每帧只剩一次 invoke。
    // ══════════════════════════════════════════════════════════════

    private static final Class<?>[] SIG_NORMAL = {
            Canvas.class, Ui.class, float.class, Game.class, boolean.class};
    private static final Class<?>[] SIG_DRAFT = {
            Canvas.class, Ui.class, float.class, Game.class, Game.Player.class, boolean.class};
    private static final Class<?>[] SIG_RESULT = {
            Canvas.class, Ui.class, float.class, Game.class, boolean.class, boolean.class, int[].class};

    /** (运行时类 + 签名) → Method。缓存失败结果用 sentinel 表示"不存在"。 */
    private static final java.util.Map<String, java.lang.reflect.Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.lang.reflect.Method NO_METHOD = noMethod();

    private static java.lang.reflect.Method noMethod() {
        try {
            return Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?>[] signatureOf(String tag) {
        switch (tag) {
            case "Map": case "Shop": case "Event": case "Camp": return SIG_NORMAL;
            case "Draft": return SIG_DRAFT;
            case "Result": return SIG_RESULT;
            default: return null;
        }
    }

    /** 取缓存的 draw 方法；找不到时返回 {@link #NO_METHOD}。 */
    private static java.lang.reflect.Method drawMethod(Object target, Class<?>[] sig) {
        String key = System.identityHashCode(target.getClass()) + "#" + sig.length;
        java.lang.reflect.Method m = METHOD_CACHE.get(key);
        if (m != null) return m;
        try {
            m = target.getClass().getMethod("draw", sig);
        } catch (Throwable t) {
            m = NO_METHOD;
        }
        METHOD_CACHE.put(key, m);
        return m;
    }

    /** 反射调用局内界面（类缺失时降级为占位面板）。 */
    private void callScreen(Object target, String tag, Object... args) {
        if (target == null) {
            if (!"Hud".equals(tag) && args.length > 0 && args[0] instanceof Canvas)
                drawFallbackPanel((Canvas) args[0], tag);
            return;
        }
        Class<?>[] sig = signatureOf(tag);
        if (sig == null) return;
        try {
            java.lang.reflect.Method m = drawMethod(target, sig);
            if (m == NO_METHOD) {
                if (args.length > 0 && args[0] instanceof Canvas) drawFallbackPanel((Canvas) args[0], tag);
                return;
            }
            m.invoke(target, args);
        } catch (Throwable t) {
            if (args.length > 0 && args[0] instanceof Canvas) drawFallbackPanel((Canvas) args[0], tag);
        }
    }

    private static final Class<?>[] SIG_HUD = {
            Canvas.class, Ui.class, float.class, Game.class, Game.Player.class,
            boolean.class, float.class, float.class};
    private java.lang.reflect.Method hudDraw;

    /** 浮动摇杆画在真实落指处，避免单手模式里「手指在右、摇杆在左」的错觉。 */
    private void drawOneHandOverlay(Canvas c) {
        ui.pill(c, 242, 24, 146, "单手 · 自动技能", Ui.MINT);
        if (moveId < 0) return;
        floatingStickPaint.setStyle(Paint.Style.FILL);
        floatingStickPaint.setColor(0x1a74e9c6);
        c.drawCircle(moveOriginX, moveOriginY, 70, floatingStickPaint);
        floatingStickPaint.setStyle(Paint.Style.STROKE);
        floatingStickPaint.setStrokeWidth(2.5f);
        floatingStickPaint.setColor(0x9999f5d9);
        c.drawCircle(moveOriginX, moveOriginY, 70, floatingStickPaint);
        floatingStickPaint.setStyle(Paint.Style.FILL);
        floatingStickPaint.setColor(0xb7a4ffe7);
        c.drawCircle(moveOriginX + dx * 42, moveOriginY + dy * 42, 23, floatingStickPaint);
    }

    private void drawHud(Canvas c, Game g, Game.Player p, boolean canLead) {
        if (hud != null) {
            try {
                // 左撇子镜像必须在 draw 之前推给 BattleHud：它自己不知道设置项，
                // 而 HUD 的绘制坐标与下面 onTouchEvent 的热区必须是同一套镜像。
                pushHudMirror();
                if (hudDraw == null) hudDraw = drawMethod(hud, SIG_HUD);
                if (hudDraw != NO_METHOD) {
                    hudDraw.invoke(hud, c, ui, visualTime, g, p, canLead, fx.shakeX(), fx.shakeY());
                    return;
                }
            } catch (Throwable ignored) {}
        }
        drawFallbackHud(c, g, p);
    }

    private java.lang.reflect.Method hudMirror;
    private boolean hudMirrorSent;

    private void pushHudMirror() {
        if (hudMirrorSent == leftHanded) return;
        try {
            if (hudMirror == null) hudMirror = hud.getClass().getMethod("setMirror", boolean.class);
            hudMirror.invoke(hud, leftHanded);
            hudMirrorSent = leftHanded;
        } catch (Throwable ignored) {
            hudMirrorSent = leftHanded;   // 没有这个方法就不再每帧重试
        }
    }

    /** 闪避/技能/终极的按压反馈：BattleHud 那边的 160ms 压下动画由这里触发。 */
    private java.lang.reflect.Method hudPress;

    private void pressHud(int which) {
        if (hud == null) return;
        try {
            if (hudPress == null) hudPress = hud.getClass().getMethod("press", int.class);
            hudPress.invoke(hud, which);
        } catch (Throwable ignored) {}
    }

    private void drawFallbackPanel(Canvas c, String tag) {
        art.text(c, tag, 100, 150, 40, Art.WHITE, true);
    }

    private void drawFallbackHud(Canvas c, Game g, Game.Player p) {
        art.box(c, 0, 0, W, 96, 0, 0xcc0b141b);
        art.text(c, Content.BIOME_NAMES[Math.min(5, g.biome)], 60, 56, 26, Art.WHITE, true);
    }

    private void drawPauseFallback(Canvas c) {
        art.box(c, 0, 0, W, H, 0, 0xd50b141b);
        art.center(c, "已暂停", 720, 380, 40, Art.WHITE, true);
    }

    /**
     * 失焦自动暂停遮罩。UX 报告：8–16 分钟一局是平均移动会话的 7–13 倍，
     * 一次来电就足以毁掉整局，所以切后台必须暂停，回来还要有 3-2-1 缓冲。
     */
    private void drawAutoPause(Canvas c) {
        art.box(c, 0, 0, W, H, 0, 0xe60b141b);
        art.center(c, "已自动暂停", 720, 320, 38, Art.WHITE, true);
        if (resumeCountdown > 0) {
            int n = Math.max(1, (int) Math.ceil(resumeCountdown));
            art.center(c, String.valueOf(n), 720, 440, 96, art.alpha(Art.GOLD, 210), true);
            art.center(c, "准备继续", 720, 512, 22, Art.MUTED, false);
        } else {
            art.center(c, "点击任意处继续", 720, 402, 24, Art.MUTED, false);
        }
    }

    /** 切后台/失焦：只在对局进行中自动暂停，菜单页不受影响。 */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && session != null && session.game != null
                && session.game.phase == Game.FIGHT && !paused) {
            paused = true;
            autoPaused = true;
            resumeCountdown = 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  背包 / 构筑
    // ══════════════════════════════════════════════════════════════

    private void drawInventory(Canvas c, Game g, Game.Player p) {
        ui.clearHits();
        art.box(c, 0, 0, W, H, 0, 0xf40b141b);
        if (p == null) return;
        art.text(c, "当前构筑", 67, 72, 34, Art.WHITE, true);
        art.text(c, Content.ROLES[p.role] + "  /  Lv." + g.level, 290, 71, 21,
                Content.COLORS[p.role], true);
        ui.button(c, 9001, 1211, 28, 162, 55, "返回  ×", false, true);
        art.text(c, String.format(Locale.US,
                        "伤害 %d    射速 %.1f/秒    暴击 %d%%    弹丸 %d    穿透 %d",
                        (int) p.damage, 1 / Math.max(.01f, p.interval), (int) (p.crit * 100),
                        p.multishot, p.pierce),
                66, 124, 19, Art.MUTED, false);
        // 流派
        for (int s = 0; s < Content.SCHOOL_COUNT; s++) {
            float x = 66 + s * 224;
            int count = g.schoolCount(p, s);
            art.box(c, x, 152, 210, 108, 14, Art.PANEL);
            art.schoolIcon(c, x + 26, 190, 26, s);
            art.text(c, Content.SCHOOL_NAMES[s], x + 48, 198, 22,
                    Content.SCHOOL_COLOR[s], true);
            art.text(c, count + " / 3", x + 150, 198, 19,
                    count >= 3 ? Art.GOLD : Art.MUTED, true);
            art.wrap(c, Content.SCHOOL_BONUS[s], x + 20, 232, 176, 13, Art.MUTED);
        }
        // 遗物
        int index = 0;
        for (int id = 0; id < Relics.COUNT; id++) {
            if (p.relics[id] <= 0) continue;
            float x = 66 + (index % 7) * 190, y = 290 + (index / 7) * 96;
            art.box(c, x, y, 176, 86, 12, Art.PANEL);
            art.relic(c, x + 30, y + 43, id, .52f);
            art.text(c, Relics.NAMES[id], x + 58, y + 36, 15,
                    Content.SCHOOL_COLOR[Relics.SCHOOL[id]], true);
            art.text(c, "× " + p.relics[id], x + 58, y + 62, 15, Art.WHITE, false);
            index++;
        }
        if (index == 0) {
            art.text(c, "尚未获得遗物", 66, 360, 30, Art.WHITE, true);
            art.text(c, "清理房间、发现宝藏或购买商品，会逐渐填满这里。", 66, 404, 20,
                    Art.MUTED, false);
        }
        // 进化
        int evoCount = 0;
        for (int i = 0; i < Evolutions.COUNT; i++) if (p.evolved[i]) evoCount++;
        if (evoCount > 0) {
            art.text(c, "武器进化", 66, 660, 20, Art.GOLD, true);
            int k = 0;
            for (int i = 0; i < Evolutions.COUNT; i++) {
                if (!p.evolved[i]) continue;
                art.text(c, "· " + Evolutions.NAMES[i], 160 + (k % 3) * 400,
                        660 + (k / 3) * 26, 17, Art.MINT, false);
                k++;
            }
        }
        art.text(c, session.online() ? "查看构筑不暂停联网战局。" : "查看构筑时，单机战斗已暂停。",
                66, 774, 17, Art.MUTED, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  反馈
    // ══════════════════════════════════════════════════════════════

    /**
     * 触觉反馈的最小间隔（毫秒）。受击是最高频的负面事件，一局里可能每秒发生多次；
     * 不加节流会震成一片，玩家反而会把震动关掉——那比完全不震更糟。
     */
    private static final long HAPTIC_MIN_GAP_MS = 120;
    private long lastHapticAt;

    /**
     * 只在「值得震一下」的时刻震动，并按事件分级：
     * <pre>
     *   受击      32ms —— 唯一持续给反馈的身体感，告诉玩家"这一下真的打到了"
     *   升级      20ms —— 正向里程碑
     *   首领登场  60ms —— 长预警，和音乐/画面错开
     * </pre>
     * 击杀<b>不震</b>：割草游戏每秒几十杀，震动会变成噪音。
     * 全部走 {@code audio.vibrate()}，因此尊重设置开关、系统触觉偏好与闸门节流。
     */
    private void haptic(int ms) {
        if (audio == null || !vibrate) return;
        long now = System.currentTimeMillis();
        if (now - lastHapticAt < HAPTIC_MIN_GAP_MS) return;
        lastHapticAt = now;
        audio.vibrate(ms);
    }

    private void feedback(Game g) {
        if (g.kills > lastKills) {
            int delta = g.kills - lastKills;
            if (delta > 0 && audio != null) audio.play(GameAudio.KILL);
            lastKills = g.kills;
        }
        for (Game.Player p : g.players) {
            if (p.id != session.localId) continue;
            if (previousHp >= 0 && p.hp < previousHp - .5f) {
                fx.shake(6, .22f);
                fx.hitstop(.06f);
                fx.vignettePulse(0xffff6060, .5f, .5f);
                if (audio != null) audio.play(GameAudio.HURT);
                haptic(32);
            }
            previousHp = p.hp;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  触摸
    // ══════════════════════════════════════════════════════════════

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        int pid = e.getPointerId(index);
        float x = (e.getX(index) - sx) / Math.max(.0001f, scale);
        float y = (e.getY(index) - sy) / Math.max(.0001f, scale);

        if (action == MotionEvent.ACTION_CANCEL) { releaseInput(); return true; }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            // ── 暂停态优先拦截 ────────────────────────────────────────
            // 遮罩上写着「点击任意处继续」，但此前根本没有任何点击路径：
            //   · 只有系统返回键（activate/onBackPressed）能恢复
            //   · 而 ui.hitAt() 在暂停判定**之前**，暂停时仍能点到下面的
            //     「菜单 / 构筑」按钮，玩家会误以为自己被踢出了战场
            // 现在：自动暂停下点任意处先进入 3 秒倒计时（防误触），
            // 倒计时未开始时再点一次则直接恢复；手动暂停同样点任意处恢复。
            if (paused) {
                if (autoPaused && resumeCountdown <= 0f) {
                    autoPaused = false;
                    resumeCountdown = AUTO_RESUME_DELAY;
                } else {
                    autoPaused = false;
                    paused = false;
                    lastNanos = 0;
                }
                performClick();
                return true;
            }
            int hit = ui.hitAt(x, y);
            if (hit >= 0) {
                activate(hit, x);
                performClick();
                return true;
            }
            if (screen != S_RUN || session == null || session.game == null) return true;
            Game g = session.game;
            if (g.phase != Game.FIGHT || paused || inventory || transition > 0) return true;
            Game.Player p = g.player(session.localId);
            // 战斗内按钮
            // 热区比视觉大 30%（UX 报告：Parhi 9.2mm / Hoober 12mm 拇指阈值；
            // 本画布 1mm≈10.8px，故闪避实际热区 168px≈15.5mm）
            if (p != null) {
                // ── 左撇子镜像 ───────────────────────────────────────────
                // 曾经写成 `bx = leftHanded ? W - x : x`，然后把**按钮判定用 bx、
                // 区域判定又用 bx 再镜像一次**——两处镜像互相抵消，结果是：
                //   · 移动区 / 瞄准区一步没动（bx > 820 ⟺ x < 620）
                //   · 闪避/技能/终极的热区被真的搬到了左半边，正压在摇杆上
                //     而画面上按钮仍在右边，玩家点右边的闪避键没有任何反应。
                // 正确做法：**只镜像一次**。按钮与区域的判定都必须用同一个镜像后的坐标，
                // 而 setMove / setAim 必须用**原始**触摸坐标（差值语义，不能镜像）。
                final float bx = leftHanded ? W - x : x;
                if (Game.distance(bx, y, 1170, 700) < 70) {
                    dashSerial++;
                    beep(GameAudio.DASH);
                    pressHud(0);
                    if (!session.online()) onboarding.notifyDash();
                } else if (Game.distance(bx, y, 1300, 660) < 66) {
                    skillSerial++;
                    beep(GameAudio.SKILL);
                    pressHud(1);
                    if (!session.online()) onboarding.notifySkill();
                } else if (Game.distance(bx, y, 1300, 552) < 58 && p.ultReady >= 1) {
                    // Session.ultimate() 是 void：能否释放已经由上面的 p.ultReady 判定过，
                    // 单机分支里它就是直接调用 Game.ultimate()，必成功。
                    session.ultimate();
                    if (!session.online()) onboarding.notifyUlt();
                    pressHud(2);
                    fx.shake(9, .35f);
                    fx.hitstop(.15f);
                    beep(GameAudio.ULT);
                } else if (oneHand && y > 145 && y < 790 && moveId < 0
                        && Game.distance(bx, y, 1300, 552) > 80) {
                    // 单手模式：任意战斗空白区都能起手浮动摇杆，保留顶部信息
                    // 与技能按钮热区。左右手镜像时只改按钮判断，不改触摸差值。
                    moveId = pid;
                    moveOriginX = x;
                    moveOriginY = y;
                    setMove(x, y);
                } else if (!oneHand && bx < 620 && y > 300 && moveId < 0) {
                    // 传统双摇杆区域保持原样，单手设置默认关闭。
                    moveId = pid;
                    moveOriginX = x;
                    moveOriginY = y;
                    setMove(x, y);
                } else if (!oneHand && bx > 980 && y > 320 && y < 680 && aimId < 0) {
                    aimId = pid;
                    setAim(x, y);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            // 非战斗态：把指针位置喂给当前屏幕，让它有 hover 提亮反馈
            if (screen != S_RUN || session == null || session.game == null) {
                pointer(x, y);
            }
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                float px = (e.getX(i) - sx) / Math.max(.0001f, scale);
                float py = (e.getY(i) - sy) / Math.max(.0001f, scale);
                if (id == moveId) setMove(px, py);
                if (id == aimId) setAim(px, py);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (pid == moveId) { moveId = -1; dx = dy = 0; }
            if (pid == aimId) { aimId = -1; aiming = false; }
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void beep(int soundId) {
        if (audio != null && sound) audio.play(soundId);
    }

    // ══════════════════════════════════════════════════════════════
    //  动作分发
    // ══════════════════════════════════════════════════════════════

    private void activate(int id) { activate(id, Float.NaN); }

    /**
     * @param touchX 点击的逻辑 x 坐标；滑条用它换算 0..100。非触摸来源传 NaN
     *               （NaN 会走 SetScreen 的「+10 步进」兜底）。
     */
    private void activate(int id, float touchX) {
        // 引导的「跳过」必须最先处理：它是在运行时登记的唯一一个全局 id，
        // 且点击已被消费时不能再落到任何别的屏幕/按钮上。
        if (screen == S_RUN && onboarding.click(id)) return;
        // 所有走 Ui.button / Ui.ghost 的控件都在这里拿到一次按下反馈。
        // 放在最前面且无条件调用：即使这个 id 最终没有对应动作，
        // 玩家至少看到"按到了"，而不是一个毫无响应的死按钮。
        ui.press(id);
        if (audio != null && sound) audio.play(GameAudio.UI_CLICK);
        // 背包返回
        if (id == 9001) { inventory = false; return; }

        switch (screen) {
            case S_MENU: {
                int a = menu.click(id);
                menuAction(a);
                return;
            }
            case S_CHAR: {
                int a = charScreen.click(id);
                if (a == Ui.A_START) {
                    selMode = charScreen.selectedMode();
                    selRole = charScreen.selectedRole();
                    selContract = charScreen.selectedContract();
                    if (!meta.isRoleUnlocked(selRole)) {
                        // 未解锁：尝试用印记购买
                        if (meta.unlockRole(selRole)) {
                            meta.save();
                            startSolo();
                        }
                        return;
                    }
                    requestNewRun();
                } else if (a == Ui.A_BACK) {
                    screen = S_MENU;
                } else if (a == Ui.A_BUY) {
                    int role = charScreen.selectedRole();
                    if (meta.unlockRole(role)) meta.save();
                } else if (a == Ui.A_NODE + 2000) {
                    screen = S_PACT;          // v2.0 契约词条
                } else if (a == Ui.A_NODE + 2001) {
                    screen = S_DOCTRINE;      // v2.0 天赋牌
                } else if (a >= 0) {
                    menuAction(a);
                }
                return;
            }
            case S_CODEX: {
                if (codex.click(id) == Ui.A_BACK) screen = S_MENU;
                return;
            }
            case S_META: {
                int a = metaScreen.click(id);
                if (a >= Ui.A_NODE && a < Ui.A_NODE + Meta.NODES) {
                    if (meta.buyNode(a - Ui.A_NODE)) meta.save();
                } else if (a >= Ui.A_NODE + 1000 && a < Ui.A_NODE + 1000 + Meta.DOCTRINE_COUNT) {
                    // 天赋牌横条：与 Meta 同包，可直接写包私有字段
                    meta.selectDoctrine(a - Ui.A_NODE - 1000);
                    meta.save();
                    beep(GameAudio.UI_CLICK);
                } else if (a == Ui.A_BACK) {
                    screen = S_MENU;
                }
                return;
            }
            case S_PACT: {
                int a = pactScreen.click(id);
                Game pg = pactHost();
                if (a >= PactScreen.A_TOGGLE_BASE && a < PactScreen.A_TOGGLE_BASE + 64) {
                    if (pg != null) pg.togglePact(a - PactScreen.A_TOGGLE_BASE);
                    beep(GameAudio.UI_CLICK);
                } else if (a == Ui.A_BACK) {
                    screen = S_CHAR;
                } else if (a == Ui.A_START) {
                    requestNewRun();
                }
                return;
            }
            case S_DOCTRINE: {
                int a = doctrineScreen.click(id);
                if (a >= DoctrineScreen.A_SELECT_BASE && a < DoctrineScreen.A_SELECT_BASE + 64) {
                    meta.selectDoctrine(a - DoctrineScreen.A_SELECT_BASE);
                    meta.save();
                    beep(GameAudio.UI_CLICK);
                } else if (a == DoctrineScreen.A_NONE) {
                    meta.selectDoctrine(-1);
                    meta.save();
                } else if (a == Ui.A_BACK || a == Ui.A_CONFIRM) {
                    screen = S_CHAR;
                }
                return;
            }
            case S_SET: {
                int[] opts = settingsOpts();
                int a = setScreen.click(id);
                if (setScreen.isSlider(id)) {
                    // 用绘制期记下的滑轨几何 + 真实触摸 x，点哪就跳到哪；
                    // 拿不到几何（例如 future 的非触摸触发）时退回 +10 步进。
                    float[] geo = new float[2];
                    if (Float.isFinite(touchX) && setScreen.sliderGeometry(id, geo)) {
                        setScreen.slideValue(opts, id, touchX, geo[0], geo[1] - geo[0]);
                    } else {
                        setScreen.slideValue(opts, id, 0, 0, 1);
                    }
                } else {
                    setScreen.toggleOption(opts, id);
                }
                applySettingsOpts(opts);
                if (a == Ui.A_CONFIRM) {
                    // 「重置存档」之前只把确认按钮画出来，A_CONFIRM 落到这里就被丢掉了：
                    // 一个明确的破坏性按钮完全无效，比没有更糟——玩家以为清干净了。
                    resetAllProgress();
                }
                if (a == Ui.A_BACK) {
                    if (settingsFromRun) {
                        settingsFromRun = false;
                        screen = S_RUN;
                        lastNanos = 0;
                    } else {
                        screen = S_MENU;
                    }
                }
                return;
            }
            default:
                break;
        }

        // ── 局内 ──
        if (session == null) return;
        Game g = session.game;
        if (g == null) {
            if (id == 999) leave();
            return;
        }
        if (id == 9100) { screen = S_MENU; leave(); return; }
        // 暂停面板的「设置」：A_SETTINGS(6) 之前落进 default 分支被吞掉，点了没反应
        if (id == Ui.A_SETTINGS) { settingsFromRun = true; screen = S_SET; paused = true; checkpoint(); return; }
        if (id == 9101) { inventory = !inventory; return; }
        if (id == 9102) { releaseInput(); paused = true; checkpoint(); return; }
        if (id == 9103) { paused = false; lastNanos = 0; return; }
        if (id == 9104) { leave(); screen = S_MENU; return; }
        if (id == 9105) { startSolo(); return; }
        if (id == 9106) { screen = S_CHAR; leave(); return; }

        // 各阶段
        switch (g.phase) {
            case Game.MAP: routeClick(g, id); break;
            case Game.CHOICE: draftClick(g, id); break;
            case Game.SHOP: shopClick(g, id); break;
            case Game.EVENT: eventClick(g, id); break;
            case Game.CAMP:
            case Game.ARMORY:
            case Game.SHRINE: campClick(g, id); break;
            case Game.WON:
            case Game.LOST: resultClick(id); break;
            case Game.LOBBY: {
                // 大堂只有两个按钮：20 = 全员出发（房主），17 = 离开小队。
                // 「离开小队」之前画出来了却没有任何分支处理——联机是宣传点，
                // 而第一个按钮点了没反应，玩家只能靠系统返回键逃出去。
                if (id == 20) session.start();
                else if (id == 17) { leave(); screen = S_MENU; }
                break;
            }
            default: break;
        }
    }

    private void menuAction(int a) {
        if (a < 0) return;
        switch (a) {
            case Ui.A_START:
                // 首局跳过整个「远征准备」：UX 报告 §3 的 0:08–0:20 只有一个 PLAY。
                // requestNewRun 会把模式/职业/契约锁成远征 + 游侠 + 漫游再开局。
                if (!onboarding.done()) requestNewRun();
                else screen = S_CHAR;
                break;
            case Ui.A_CONTINUE: resumeSaved(); break;
            case Ui.A_CODEX: screen = S_CODEX; break;
            case Ui.A_META: screen = S_META; break;
            case Ui.A_SETTINGS: screen = S_SET; break;
            case Ui.A_SOUND:
                sound = !sound;
                musicOn = sound;
                saveSettings();
                break;
            case Ui.A_GUIDE: screen = S_CODEX; break;
            case Ui.A_HOST: host(); break;
            case Ui.A_JOIN:
                if (activity != null) activity.joinDialog(selRole);
                break;
            case Ui.A_BACK: screen = S_MENU; break;
            default: break;
        }
    }

    /**
     * 把指针位置转发给当前屏幕，驱动 hover 高亮。
     * <p>用反射调用 {@code pointer(float,float)}，屏幕类缺失时静默跳过。
     */
    private void pointer(float x, float y) {
        Object target = null;
        switch (screen) {
            case S_MENU: target = menu; break;
            case S_CHAR: target = charScreen; break;
            case S_CODEX: target = codex; break;
            case S_META: target = metaScreen; break;
            case S_SET: target = setScreen; break;
            default: break;
        }
        if (target == null && session != null && session.game != null) {
            switch (session.game.phase) {
                case Game.MAP: target = mapScreen; break;
                case Game.CHOICE: target = draftScreen; break;
                case Game.SHOP: target = shopScreen; break;
                case Game.EVENT: target = eventScreen; break;
                case Game.CAMP:
                case Game.ARMORY:
                case Game.SHRINE: target = campScreen; break;
                default: break;
            }
        }
        if (target == null) return;
        try {
            target.getClass().getMethod("pointer", float.class, float.class).invoke(target, x, y);
        } catch (Throwable ignored) {
            // 屏幕没有 pointer() 时不影响任何交互
        }
    }

    /** 从局内 screen 读取 click 结果（反射安全）。 */
    private int screenClick(Object target, int id) {
        if (target == null) return -1;
        try {
            return (Integer) target.getClass().getMethod("click", int.class).invoke(target, id);
        } catch (Throwable t) {
            try {
                return (Integer) target.getClass().getField("hit").get(target);
            } catch (Throwable t2) {
                return -1;
            }
        }
    }

    private void routeClick(Game g, int id) {
        int a = screenClick(mapScreen, id);
        if (a >= 1000 && a < 1100) {
            session.action(0, a - 1000);
            transition = .75f;
            arenaKey = -1;
            onPhaseChange(session.game, session.game.player(session.localId));
        } else if (a == Ui.A_LEAVE || a == Ui.A_BACK) {
            releaseInput();
            paused = true;
        }
    }

    private void draftClick(Game g, int id) {
        int a = screenClick(draftScreen, id);
        if (a >= 2000 && a < 2100) session.choose(a - 2000, 0);
        else if (a == Ui.A_REROLL) session.reroll();
        else if (a == Ui.A_EVOLVE) session.switchToEvolution();
        else if (a == Ui.A_GUIDE) inventory = true;
    }

    private void shopClick(Game g, int id) {
        int a = screenClick(shopScreen, id);
        if (a >= 3000 && a < 3100) session.action(3, a - 3000);
        else if (a == Ui.A_REROLL) session.action(4, 0);
        else if (a == Ui.A_LEAVE) session.action(5, 0);
    }

    private void eventClick(Game g, int id) {
        int a = screenClick(eventScreen, id);
        if (a == 4000) session.action(1, 0);
        else if (a == 4001) session.action(1, 1);
        else if (a == 4002) session.action(2, 0);
    }

    private void campClick(Game g, int id) {
        int a = screenClick(campScreen, id);
        if (a >= 5000 && a < 5100) session.action(6, a - 5000);
        else if (a >= 5100 && a < 5200) session.action(7, a - 5100);
        else if (a >= 5200 && a < 5300) session.action(8, a - 5200);
    }

    private void resultClick(int id) {
        if (tutorialResult && (id == Ui.A_RESTART || id == 30
                || id == Ui.A_HOME || id == 31)) {
            // 第一趟后先让玩家用奖励强化/选职业。按钮文案由 ResultScreen 同步提供。
            boolean upgrade = id == Ui.A_RESTART || id == 30;
            leave();
            screen = upgrade ? S_META : S_CHAR;
            return;
        }
        if (id == Ui.A_RESTART || id == 30) startSolo();
        else if (id == Ui.A_HOME || id == 31) { leave(); screen = S_MENU; }
        else if (id == Ui.A_RESUME || id == 40) { paused = false; lastNanos = 0; }
        else if (id == Ui.A_LEAVE || id == 41) { leave(); screen = S_MENU; }
    }

    // ══════════════════════════════════════════════════════════════
    //  生命周期
    // ══════════════════════════════════════════════════════════════

    void suspend() {
        suspended = true;
        if (audio != null) audio.state(false, false);
        releaseInput();
        checkpoint();
        if (session != null && !session.online() && session.game != null
                && session.game.phase == Game.FIGHT) paused = true;
        try { meta.saveNow(); } catch (Throwable ignored) {}
    }

    void resume() {
        suspended = false;
        lastNanos = 0;
        if (audio != null) audio.state(sound || musicOn, true);
        invalidate();
    }

    void destroy() {
        destroyed = true;
        leave();
        try { saves.close(); } catch (Throwable ignored) {}
        try { meta.close(); } catch (Throwable ignored) {}
        if (audio != null) audio.close();
        if (arenaCache != null) { arenaCache.recycle(); arenaCache = null; }
        if (panelCache != null) { panelCache.recycle(); panelCache = null; }
        bg.dispose();
        art.recycleCaches();
        art.dispose();
        fx.dispose();
        ui.recycleCaches();
    }

    void back() {
        // v2.0 的两个新屏幕也必须有返回键出口：漏在名单外时返回键会落到
        // 后面的「局内暂停」分支，从 S_PACT / S_DOCTRINE 按返回没有任何反应。
        if (screen == S_CODEX || screen == S_META || screen == S_SET || screen == S_CHAR) {
            screen = S_MENU;
            return;
        }
        if (screen == S_PACT || screen == S_DOCTRINE) {
            screen = S_CHAR;          // 与两个屏幕自己的「返回」按钮一致
            return;
        }
        if (screen == S_MENU) { activity.finish(); return; }
        if (inventory) { inventory = false; return; }
        // 结算页 / 联机大堂按返回：以前会落到下面的「局内暂停」分支，
        // 在结算页弹出一个空白的暂停面板（每局结束都会撞上），在大堂则
        // 把玩家按在联机里出不去。结算与大堂都不是「可暂停的对局」，
        // 返回的语义只能是「收拾干净回主菜单」。
        if (session != null && session.game != null
                && (session.game.phase == Game.WON || session.game.phase == Game.LOST
                    || session.game.phase == Game.LOBBY)) {
            leave();
            screen = S_MENU;
            return;
        }
        if (paused) {
            // 与 onTouchEvent 的暂停分支保持同一套语义：
            // 自动暂停且尚未开始倒计时 → 进入缓冲；其余情况直接恢复。
            if (autoPaused && resumeCountdown <= 0f) {
                autoPaused = false;
                resumeCountdown = AUTO_RESUME_DELAY;
                return;
            }
            autoPaused = false;
            paused = false;
            lastNanos = 0;
            return;
        }
        releaseInput();
        paused = true;
        checkpoint();
    }

    /**
     * 主菜单的程序化动态背景：缓慢流动的星尘 + 生态色呼吸。
     * <p>与屏幕内容无关，纯装饰，开销恒定。
     */
    private static final class AnimatedBackground {
        private final Random rng = new Random(20261018L);
        private final float[] px = new float[34], py = new float[34], pv = new float[34];
        private final int[] pc = new int[34];
        private boolean ready;

        private android.graphics.Bitmap bgBmp;

        void draw(Canvas c, Art a, float time, boolean strong) {
            if (!ready) {
                for (int i = 0; i < px.length; i++) {
                    px[i] = rng.nextFloat() * 1440;
                    py[i] = rng.nextFloat() * 810;
                    pv[i] = .12f + rng.nextFloat() * .5f;
                    pc[i] = i % 3 == 0 ? 0xff9de7c1 : i % 3 == 1 ? 0xffeac487 : 0xff93c4ff;
                }
                ready = true;
            }
            // 渐变 + 暗角烘焙一次成位图（软件渲染下构造 Shader 极慢）
            if (bgBmp == null) {
                try {
                    bgBmp = android.graphics.Bitmap.createBitmap(360, 203,
                            android.graphics.Bitmap.Config.ARGB_8888);
                    Canvas bc = new Canvas(bgBmp);
                    Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
                    bp.setShader(new android.graphics.LinearGradient(0, 0, 0, 203,
                            0xff0d1a22, 0xff070d13, android.graphics.Shader.TileMode.CLAMP));
                    bc.drawRect(0, 0, 360, 203, bp);
                    Paint vp = new Paint(Paint.FILTER_BITMAP_FLAG);
                    vp.setShader(new android.graphics.RadialGradient(180, 101, 260,
                            new int[]{0x00000000, 0x73000000},
                            new float[]{.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                    bc.drawRect(0, 0, 360, 203, vp);
                } catch (Throwable ignored) { bgBmp = null; }
            }
            if (bgBmp != null) {
                a.r.set(0, 0, 1440, 810);
                c.drawBitmap(bgBmp, null, a.r, null);
            }
            for (int i = 0; i < px.length; i++) {
                float y = (py[i] - time * pv[i] * 26f) % 810f;
                if (y < 0) y += 810f;
                float x = px[i] + (float) Math.sin(time * .32f + i) * 16f;
                a.circle(c, x, y, (i % 4) * .6f + .8f, a.alpha(pc[i], strong ? 70 : 34));
            }
        }

        void dispose() {
            if (bgBmp != null) { bgBmp.recycle(); bgBmp = null; }
        }
    }
}
