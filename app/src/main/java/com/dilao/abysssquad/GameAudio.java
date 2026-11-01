package com.dilao.abysssquad;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 音频与触觉反馈。
 * <p>设计要点：
 * <ul>
 *   <li>SoundPool 加载全部短音效；同音效 60ms 节流 + 最多 4 实例，避免机枪扫射时爆音。</li>
 *   <li>音乐按需创建 MediaPlayer 并缓存，切歌 0.6 秒淡入淡出（等功率曲线）。</li>
 *   <li>所有加载失败都降级为静音，绝不崩溃。</li>
 * </ul>
 * <p>v2.0 变更：
 * <ul>
 *   <li>音效从 20 增至 33（契约/天赋/精英/波次/连击/神龛/赌局）。</li>
 *   <li>淡入淡出改用等功率曲线（sin/cos 四分之一周期）：线性曲线在交叉点
 *       会有 -3dB 的功率凹陷，听起来像"音量掉了一下再回来"。</li>
 *   <li>混音音量改为按响度预算对齐：音效由脚本按 LUFS 归一到 -19.5，
 *       音乐自动低 5dB，所以这里 sfxVolume / musicVolume 只需做用户偏好缩放。</li>
 * </ul>
 */
final class GameAudio {

    // ── 音效 id ──
    static final int UI_CLICK = 0, UI_BACK = 1, UI_ERROR = 2, HIT = 3, HIT_CRIT = 4,
            KILL = 5, HURT = 6, DASH = 7, SKILL = 8, ULT = 9, LEVELUP = 10, COIN = 11,
            BUY = 12, CHEST = 13, HEAL = 14, FREEZE = 15, EXPLODE = 16, BOSS_WARN = 17,
            VICTORY = 18, DEFEAT = 19;
    // ── v2.0 新增 ──
    static final int PACT_ON = 20, PACT_OFF = 21, PACT_CONFIRM = 22,
            DOCTRINE_SELECT = 23, DOCTRINE_LOCK = 24, ELITE_WARN = 25, WAVE_START = 26,
            COMBO_10 = 27, COMBO_30 = 28, COMBO_60 = 29, SHRINE_PICK = 30,
            GAMBLE_WIN = 31, GAMBLE_LOSE = 32;
    static final int SFX_COUNT = 33;

    // ── 音乐 id ──
    static final int MUSIC_MENU = 0, MUSIC_COMBAT_0 = 1, MUSIC_COMBAT_1 = 2, MUSIC_BOSS = 3;
    static final int MUSIC_COUNT = 4;

    /** 资源名，与 res/raw 下的文件名一致；缺失的会被跳过。顺序必须和上面的 id 对齐。 */
    private static final String[] SFX_NAMES = {
            "ui_click", "ui_back", "ui_error", "hit", "hit_crit",
            "kill", "hurt", "dash", "skill", "ult", "levelup", "coin",
            "buy", "chest", "heal", "freeze", "explode", "boss_warn",
            "victory", "defeat",
            // v2.0
            "pact_on", "pact_off", "pact_confirm", "doctrine_select", "doctrine_lock",
            "elite_warn", "wave_start", "combo_10", "combo_30", "combo_60",
            "shrine_pick", "gamble_win", "gamble_lose"
    };
    private static final String[] MUSIC_NAMES = {
            "music_menu", "music_combat_0", "music_combat_1", "music_boss"
    };

    /** 同类音效最多同时响的实例数（防机枪扫射叠成噪音墙）。 */
    private static final int MAX_PER_SFX = 4;
    /** 同一音效的最小重复间隔。 */
    private static final long THROTTLE_MS = 60;
    /** 切歌淡入淡出时长。 */
    private static final long FADE_MS = 600;

    private final Context context;
    private final SoundPool pool;
    private final int[] sfx = new int[SFX_COUNT];
    private final long[] lastPlay = new long[SFX_COUNT];
    /** 每个音效当前在响的实例： [soundId, startedAtMillis]，用于 MAX_PER_SFX 限制。 */
    private final java.util.ArrayList<long[]> live = new java.util.ArrayList<>();
    private final MediaPlayer[] music = new MediaPlayer[MUSIC_COUNT];
    /** 每个播放器上还没跑完的淡变 Runnable（切歌时只取消对应的那一条）。 */
    private final java.util.HashMap<MediaPlayer, Runnable> pendingFade = new java.util.HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentMusic = -1;
    private boolean enabled = true, foreground, focused;
    private float sfxVolume = .7f, musicVolume = .5f;
    private boolean vibration = true;
    private Vibrator vibrator;
    private final AudioManager manager;
    private final AudioFocusRequest focus;

    GameAudio(Context context) {
        this.context = context;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        SoundPool localPool = null;
        try {
            localPool = new SoundPool.Builder().setMaxStreams(24)
                    .setAudioAttributes(attrs).build();
        } catch (Throwable ignored) {}
        pool = localPool;
        if (pool != null) {
            pool.setOnLoadCompleteListener((sp, sampleId, status) -> { /* 忽略 */ });
            for (int i = 0; i < SFX_COUNT; i++) sfx[i] = loadSfx(SFX_NAMES[i]);
        }
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> {
                    focused = change == AudioManager.AUDIOFOCUS_GAIN;
                    if (focused) sync();
                    else pauseMusicInternal();
                })
                .build();
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {}
    }

    private int loadSfx(String name) {
        if (pool == null || context == null) return 0;
        try {
            int id = context.getResources().getIdentifier(name, "raw", context.getPackageName());
            if (id == 0) return 0;
            return pool.load(context, id, 1);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 生命周期：enabled = 用户开关，foreground = 前台且可见。 */
    void state(boolean on, boolean fg) {
        if (enabled == on && foreground == fg) return;
        enabled = on;
        foreground = fg;
        if (enabled && foreground) {
            try {
                focused = manager == null || manager.requestAudioFocus(focus)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } catch (Throwable t) {
                focused = true;
            }
            sync();
        } else {
            pauseMusicInternal();
            if (pool != null) pool.autoPause();
            try { if (manager != null) manager.abandonAudioFocusRequest(focus); } catch (Throwable ignored) {}
            focused = false;
        }
    }

    private void sync() {
        if (!enabled || !foreground || !focused) return;
        if (pool != null) pool.autoResume();
        if (currentMusic >= 0) {
            MediaPlayer mp = music[currentMusic];
            if (mp != null) {
                try { if (!mp.isPlaying()) mp.start(); } catch (Throwable ignored) {}
            }
        }
    }

    void play(int id) {
        if (!enabled || !foreground || !focused || pool == null) return;
        if (id < 0 || id >= SFX_COUNT) return;
        int sample = sfx[id];
        if (sample == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastPlay[id] < THROTTLE_MS) return;

        // 同音效最多 4 个实例同时响；超出就回收最老的那个
        int count = 0;
        int oldestIndex = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < live.size(); i++) {
            long[] rec = live.get(i);
            if (rec[0] == id) {
                count++;
                if (rec[1] < oldest) { oldest = rec[1]; oldestIndex = i; }
            }
        }
        if (count >= MAX_PER_SFX && oldestIndex >= 0) {
            live.remove(oldestIndex);
        }
        // 清理超过 2 秒的陈旧记录（最长音效 1.7s）
        for (int i = live.size() - 1; i >= 0; i--) {
            if (now - live.get(i)[1] > 2000) live.remove(i);
        }

        lastPlay[id] = now;
        live.add(new long[]{id, now});
        try {
            float v = Math.max(0, Math.min(1f, sfxVolume));
            pool.play(sample, v, v, 1, 0, 1);
        } catch (Throwable ignored) {}
    }

    void music(int trackId) {
        if (trackId < 0 || trackId >= MUSIC_COUNT) return;
        if (trackId == currentMusic) return;
        int old = currentMusic;
        currentMusic = trackId;
        fadeOutPlayer(old, FADE_MS);
        if (!enabled || !foreground || !focused) return;
        MediaPlayer mp = obtain(trackId);
        if (mp == null) return;
        try {
            mp.setVolume(0, 0);
            mp.start();
            fadeInPlayer(mp, FADE_MS);
        } catch (Throwable ignored) {}
    }

    private MediaPlayer obtain(int trackId) {
        MediaPlayer mp = music[trackId];
        if (mp != null) return mp;
        try {
            int id = context.getResources().getIdentifier(MUSIC_NAMES[trackId], "raw",
                    context.getPackageName());
            if (id == 0) return null;
            mp = MediaPlayer.create(context, id);
            if (mp == null) return null;
            mp.setLooping(true);
            mp.setVolume(0, 0);
            music[trackId] = mp;
            return mp;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 等功率淡入：音量按 sin(π/2 · t) 上升。
     * <p>线性淡入在切歌交叉点会有 -3dB 功率凹陷（两首都只有一半功率），
     * 听感是"音量先掉一下再回来"。等功率曲线 sin²+cos²=1 保持总功率恒定。
     */
    private void fadeInPlayer(final MediaPlayer mp, final long duration) {
        if (mp == null) return;
        final long start = System.currentTimeMillis();
        final float target = Math.max(0, Math.min(1f, musicVolume));
        final Runnable fade = new Runnable() {
            @Override public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) duration);
                double curve = Math.sin(Math.PI * 0.5 * t);
                try { mp.setVolume((float) (target * curve), (float) (target * curve)); }
                catch (Throwable ignored) {}
                if (t < 1f) {
                    handler.postDelayed(this, 33);
                } else {
                    pendingFade.remove(mp);
                }
            }
        };
        scheduleFade(mp, fade);
    }

    /** 等功率淡出：音量按 cos(π/2 · t) 下降，与淡入曲线功率互补。 */
    private void fadeOutPlayer(final int trackId, final long duration) {
        if (trackId < 0 || trackId >= MUSIC_COUNT) return;
        final MediaPlayer mp = music[trackId];
        if (mp == null) return;
        final long start = System.currentTimeMillis();
        final float from = Math.max(0, Math.min(1f, musicVolume));
        final Runnable fade = new Runnable() {
            @Override public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) duration);
                double curve = Math.cos(Math.PI * 0.5 * t);
                try { mp.setVolume((float) (from * curve), (float) (from * curve)); }
                catch (Throwable ignored) {}
                if (t < 1f) {
                    handler.postDelayed(this, 33);
                } else {
                    try { if (mp.isPlaying()) mp.pause(); } catch (Throwable ignored) {}
                    pendingFade.remove(mp);
                }
            }
        };
        scheduleFade(mp, fade);
    }

    /**
     * 为某个播放器安排淡变，并取消它上一条未完成的淡变。
     * <p>只针对同一个 MediaPlayer 取消，所以交叉时旧曲淡出 / 新曲淡入互不干扰。
     */
    private void scheduleFade(MediaPlayer mp, Runnable fade) {
        Runnable previous = pendingFade.remove(mp);
        if (previous != null) handler.removeCallbacks(previous);
        pendingFade.put(mp, fade);
        handler.post(fade);
    }

    void stopMusic() {
        int old = currentMusic;
        currentMusic = -1;
        fadeOutPlayer(old, 400);
    }

    private void pauseMusicInternal() {
        for (MediaPlayer mp : music) {
            if (mp == null) continue;
            try { if (mp.isPlaying()) mp.pause(); } catch (Throwable ignored) {}
        }
    }

    void pause() { if (pool != null) pool.autoPause(); pauseMusicInternal(); }

    void resume() { sync(); }

    void setSfxVolume(float v) { sfxVolume = Math.max(0, Math.min(1f, v)); }
    void setMusicVolume(float v) {
        musicVolume = Math.max(0, Math.min(1f, v));
        int cur = currentMusic;
        if (cur >= 0 && music[cur] != null) {
            try { music[cur].setVolume(musicVolume, musicVolume); } catch (Throwable ignored) {}
        }
    }

    void setVibration(boolean on) { vibration = on; }

    /**
     * 触觉反馈：受设置与系统支持约束，失败静默。
     *
     * <p><b>为什么用预定义波形而不是 createOneShot</b>：Google 官方在 VibrationEffect
     * 文档里明确劝退 {@code createOneShot}——它没有"这真的是触觉反馈还是通知"的语义，
     * 会忽略用户的「触觉反馈」系统设置（DND / 无障碍 / 开发者选项里的关闭），
     * 在部分机型上也没有振幅控制。{@code EFFECT_CLICK / EFFECT_TICK} 这类预定义常量
     * 由系统决定实际波形与强度，并且会尊重用户的系统级偏好。
     *
     * <p>分段规则：≤20ms 当"轻触"，其余当"点击"；两者都不超过 120ms，
     * 保证连续受击时不会震成一片。
     */
    void vibrate(int ms) {
        if (!vibration || vibrator == null) return;
        try {
            if (!vibrator.hasVibrator()) return;
            int len = Math.max(8, Math.min(120, ms));
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                int effect = len <= 20 ? VibrationEffect.EFFECT_TICK : VibrationEffect.EFFECT_CLICK;
                vibrator.vibrate(VibrationEffect.createPredefined(effect));
            } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(len, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(len);
            }
        } catch (Throwable ignored) {}
    }

    void close() {
        state(false, false);
        pendingFade.clear();
        for (int i = 0; i < MUSIC_COUNT; i++) {
            MediaPlayer mp = music[i];
            if (mp != null) {
                try { mp.release(); } catch (Throwable ignored) {}
                music[i] = null;
            }
        }
        if (pool != null) pool.release();
        live.clear();
        handler.removeCallbacksAndMessages(null);
    }
}
