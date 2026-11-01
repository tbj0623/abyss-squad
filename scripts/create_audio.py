#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""深渊小队 v2.0 — 程序化原创音频生成器（纯 Python 标准库 + 可选 ffmpeg 编码）。

用法:
    python scripts/create_audio.py            # 生成全部 33 音效 + 4 循环音乐
    python scripts/create_audio.py --sfx      # 只生成音效（调试用）
    python scripts/create_audio.py --music    # 只生成音乐（调试用）

v2.0 变化:
  * 新增 13 个音效（契约 / 天赋 / 精英预警 / 波次 / 连击 / 神龛 / 赌局）。
  * **响度归一化**：音效按整合 LUFS（ITU-R BS.1770-4，K 加权 + 门限）对齐到
    同一个响度体系，音效之间差距 ≤ 8 dB；音乐自动比音效均值低 5 dB。
    单纯按峰值归一化会让 UI 音效比打击音还响，v1.0 的 9.7 dB 离散度就是这么来的。
  * 音乐输出 OGG（ffmpeg libvorbis 可用时）；没有 ffmpeg 则回落 16-bit WAV。
    脚本会删除 raw/ 里不在本次清单中的旧文件，因此重复运行不会残留
    music_*.wav + music_*.ogg 双份资源（会造成 aapt 重复资源失败）。

设计:
  * 音效 44100 Hz / 16-bit / mono，短促、无削波。峰值上限 -3 dBFS。
  * 音乐 22050 Hz，明确写出的旋律 + 和声进行，结构为 前奏→主段→变奏→主段，
    渲染时多留 1~2 秒尾巴再环形折回开头，保证循环无缝。
  * 所有文件过 soft_clip()，绝不削波。
"""

import math
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import wave
import zlib
from array import array
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/raw"
QA = ROOT / "build/qa-v8"
TMP = ROOT / "build/.audio-tmp"

SFX_RATE = 44100
MUSIC_RATE = 22050
MUSIC_CHANNELS = 1          # 见文件头说明：stereo 会突破 APK 体积预算
TAU = 2.0 * math.pi
SIZE_LIMIT = 7.0 * 1024 * 1024
PEAK_CEILING_DB = -3.0      # 所有文件峰值上限
SFX_SPREAD_LIMIT = 8.0      # 音效之间 LUFS 离散度上限
MUSIC_BELOW_SFX_DB = 5.0    # 音乐目标：比音效均值低 5 dB
SFX_MEAN_FALLBACK = -18.6   # 只跑 --music（没重新生成音效）时的回落基准

# v0.3 遗留资源，新代码不再引用，必须删掉（约 1.42 MB）
OLD_FILES = ["fx_hurt", "fx_kill", "fx_reward", "fx_skill", "fx_ui", "music_expedition"]


# ═══════════════════════════════════════════════════════════════════
#  DSP 基础
# ═══════════════════════════════════════════════════════════════════

def soft_clip(x):
    """tanh 的 Padé 近似：|x|>3 时饱和，彻底杜绝硬削波。"""
    if x > 3.0:
        return 1.0
    if x < -3.0:
        return -1.0
    x2 = x * x
    return x * (27.0 + x2) / (27.0 + 9.0 * x2)


def dc_block(buf):
    """去掉直流偏移，避免低频占满 headroom。"""
    n = len(buf)
    if n == 0:
        return buf
    mean = sum(buf) / n
    if abs(mean) < 1e-9:
        return buf
    return [v - mean for v in buf]


def normalize(buf, peak_db=-3.0):
    """先 soft_clip 饱和，再线性归一到目标峰值（-3 dBFS），保证绝不削波。"""
    peak = 0.0
    for v in buf:
        a = -v if v < 0 else v
        if a > peak:
            peak = a
    if peak < 1e-9:
        return [0.0] * len(buf)
    target = 10.0 ** (peak_db / 20.0)
    gain = target / peak
    out = [soft_clip(v * gain) for v in buf]
    peak = 0.0
    for v in out:
        a = -v if v < 0 else v
        if a > peak:
            peak = a
    if peak < 1e-9:
        return out
    scale = target / peak
    return [v * scale for v in out]


def fade_edges(buf, rate, fade_in_ms, fade_out_ms):
    n = len(buf)
    fi = max(0, min(n, int(rate * fade_in_ms / 1000.0)))
    fo = max(0, min(n, int(rate * fade_out_ms / 1000.0)))
    for i in range(fi):
        buf[i] *= i / fi
    for i in range(fo):
        buf[n - 1 - i] *= i / fo
    return buf


# ═══════════════════════════════════════════════════════════════════
#  响度测量（ITU-R BS.1770-4 / EBU R128）：K 加权 + 400ms 块 + 双门限
# ═══════════════════════════════════════════════════════════════════

def _biquad_high_shelf(rate, f0, Q, gain_db):
    A = 10.0 ** (gain_db / 40.0)
    w0 = TAU * f0 / rate
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * Q)
    sq = 2.0 * math.sqrt(A) * alpha
    b0 = A * ((A + 1) + (A - 1) * cw + sq)
    b1 = -2 * A * ((A - 1) + (A + 1) * cw)
    b2 = A * ((A + 1) + (A - 1) * cw - sq)
    a0 = (A + 1) - (A - 1) * cw + sq
    a1 = 2 * ((A - 1) - (A + 1) * cw)
    a2 = (A + 1) - (A - 1) * cw - sq
    return (b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def _biquad_high_pass(rate, f0, Q):
    w0 = TAU * f0 / rate
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2.0 * Q)
    b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
    a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
    return (b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def _biquad_apply(x, c):
    b0, b1, b2, a1, a2 = c
    y = [0.0] * len(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        o = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, v
        y2, y1 = y1, o
        y[i] = o
    return y


def integrated_lufs(samples, rate, channels=1):
    """整合响度（LUFS）。短音效（<400ms）退化为单个块，仍可比相对大小。

    输入可以是 float（-1..1，合成中间结果）或 int16（-32768..32767，读回的 WAV）。
    用峰值范围自动判别量纲，避免把 float 又除一次 32768 得到 -104 LUFS 这种假值。
    """
    n = len(samples)
    if n == 0:
        return -70.0
    raw_peak = peak_of(samples)
    if raw_peak <= 1.5:
        scale = 1.0                       # 已经是归一化 float
    else:
        scale = 1.0 / 32768.0             # int16
    frames = n // channels
    if channels == 1:
        mono = [v * scale for v in samples]
    else:
        mono = [0.0] * frames
        for i in range(frames):
            mono[i] = 0.5 * scale * (samples[i * channels] + samples[i * channels + 1])
    k1 = _biquad_high_shelf(rate, 1681.9744509555319, 0.7071752369554196, 3.999843853973347)
    k2 = _biquad_high_pass(rate, 38.13547087602444, 0.5003270373238773)
    y = _biquad_apply(_biquad_apply(mono, k1), k2)

    block = max(1, int(0.4 * rate))
    hop = max(1, int(0.1 * rate))
    if len(y) < block:
        ms = sum(v * v for v in y) / len(y)
        return -0.691 + 10.0 * math.log10(max(ms, 1e-15))
    powers, lufs = [], []
    for start in range(0, len(y) - block + 1, hop):
        seg = y[start:start + block]
        ms = sum(v * v for v in seg) / block
        powers.append(ms)
        lufs.append(-0.691 + 10.0 * math.log10(max(ms, 1e-15)))
    gated = [(p, l) for p, l in zip(powers, lufs) if l > -70.0]
    if not gated:
        return -70.0
    rel_l = -0.691 + 10.0 * math.log10(
        max(sum(p for p, _ in gated) / len(gated), 1e-15)) - 10.0
    keep = [p for p, l in gated if l > rel_l]
    if not keep:
        keep = [p for p, _ in gated]
    return -0.691 + 10.0 * math.log10(max(sum(keep) / len(keep), 1e-15))


def peak_of(samples):
    p = 0.0
    for v in samples:
        a = -v if v < 0 else v
        if a > p:
            p = a
    return p


def gain_to_db(buf, gain_db):
    if abs(gain_db) < 1e-6:
        return buf
    g = 10.0 ** (gain_db / 20.0)
    return [soft_clip(v * g) for v in buf]


def limit_peak(buf, ceiling_db=PEAK_CEILING_DB, lookahead=None):
    """前瞻峰值限幅器：只压超过 ceiling 的瞬态，其余样本原样通过。

    v2.0 之前用「整体缩到 ceiling」会把 boss_warn / victory 这种
    LUFS 偏低、需要提升的曲子打回原形（提升后又撞天花板再缩回来，响度永远上不去）。
    这里改成滑动最大值包络 + 平滑增益曲线，只在超过阈值的局部压一下，
    整体响度基本不受影响，也不会产生削波。
    """
    n = len(buf)
    if n == 0:
        return buf, 0.0
    ceiling = 10.0 ** (ceiling_db / 20.0)
    peak = peak_of(buf)
    if peak <= ceiling:
        return buf, 0.0

    a = -ceiling  # 保存振幅导数绝对值数组

    # 需要多深的增益
    worst = peak    # 逐样本目标增益（>1 表示不处理）
    gain = [1.0] * n
    # 1) 先算出需要压的样本
    for i, v in enumerate(buf):
        av = -v if v < 0 else v
        if av > ceiling:
            gain[i] = ceiling / av
    # 2) 前瞻平滑：用半径 R 的滑动最小值把增益曲线铺开，避免单点硬压产生失真
    R = int(0.004 * 44100) if lookahead is None else max(1, int(lookahead))
    R = max(1, min(R, n // 2 if n > 2 else 1))
    # 滑动窗口最小值（单调队列，O(n)）
    from collections import deque
    dq = deque()
    smoothed = [1.0] * n
    for i in range(n):
        while dq and gain[dq[-1]] >= gain[i]:
            dq.pop()
        dq.append(i)
        while dq[0] < i - R:
            dq.popleft()
        smoothed[i] = gain[dq[0]]
    # 反向再跑一遍，做成对称的（前瞻 + 后瞻）
    dq.clear()
    for i in range(n - 1, -1, -1):
        while dq and smoothed[dq[-1]] >= smoothed[i]:
            dq.pop()
        dq.append(i)
        while dq[0] > i + R:
            dq.popleft()
        if smoothed[dq[0]] < gain[i]:
            gain[i] = smoothed[dq[0]]

    out = [0.0] * n
    total_db = 0.0
    cnt = 0
    for i in range(n):
        g = gain[i]
        if g < 1.0:
            total_db += 20.0 * math.log10(g)
            cnt += 1
        out[i] = soft_clip(buf[i] * g)
    # 保险：仍有残余超限则整体微缩（正常情况下不会触发）
    p2 = peak_of(out)
    if p2 > ceiling and p2 > 1e-9:
        s = ceiling / p2
        out = [v * s for v in out]
        total_db += 20.0 * math.log10(s)
    avg_db = total_db / cnt if cnt else 0.0
    return out, avg_db


def loudness_match(buf, rate, target_lufs, ceiling_db=PEAK_CEILING_DB,
                   iterations=3, tolerance=0.35):
    """把 buf 的整合响度对到 target_lufs，再做峰值限幅（迭代补偿限幅损失的响度）。

    返回 (buf, lufs_before, lufs_after, peak_db)。
    """
    cur = integrated_lufs(buf, rate)
    if cur <= -69.0:
        return buf, cur, cur, db(peak_of(buf))
    out = gain_to_db(buf, target_lufs - cur)
    # 限幅会吃掉一点响度；迭代补回，避免 boss_warn/victory 这类
    # "提升后撞天花板" 的音效永远对不准目标。
    for _ in range(max(1, iterations)):
        out, _ = limit_peak(out, ceiling_db)
        achieved = integrated_lufs(out, rate)
        err = target_lufs - achieved
        if abs(err) <= tolerance:
            break
        out = gain_to_db(out, err)
    return out, cur, integrated_lufs(out, rate), db(peak_of(out))



class LP:
    """一阶低通。"""
    __slots__ = ("a", "y")

    def __init__(self, fc, rate):
        fc = max(20.0, min(fc, rate * 0.45))
        self.a = 1.0 - math.exp(-TAU * fc / rate)
        self.y = 0.0

    def __call__(self, x):
        self.y += self.a * (x - self.y)
        return self.y


class HP:
    """一阶高通 = x - 低通。"""
    __slots__ = ("lp",)

    def __init__(self, fc, rate):
        self.lp = LP(fc, rate)

    def __call__(self, x):
        return x - self.lp(x)


def adsr(t, dur, a, d, s, r):
    if t < a:
        return t / a if a > 1e-6 else 1.0
    if t < a + d:
        return 1.0 - (1.0 - s) * (t - a) / d if d > 1e-6 else s
    if t < dur - r:
        return s
    if t < dur:
        return s * (1.0 - (t - (dur - r)) / r) if r > 1e-6 else 0.0
    return 0.0


# 振荡器：相位以「周期数」为单位
def s_sine(ph):
    return math.sin(TAU * ph)


def s_tri(ph):
    p = ph - math.floor(ph)
    return 4.0 * abs(p - 0.5) - 1.0


def s_saw(ph):
    p = ph - math.floor(ph)
    return 2.0 * p - 1.0


def s_square(ph):
    p = ph - math.floor(ph)
    return 1.0 if p < 0.5 else -1.0


WAVES = {"sine": s_sine, "tri": s_tri, "saw": s_saw, "square": s_square}

_DEGREE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5,
           "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def note_hz(name):
    """'A4' / 'Bb3' / 'F#5' -> Hz（A4 = 440）。"""
    s = name.strip()
    letter = s[0].upper()
    semi = _DEGREE[letter]
    i = 1
    if len(s) > 1 and s[1] in "#b":
        semi += 1 if s[1] == "#" else -1
        i = 2
    octave = int(s[i:])
    midi = 12 * (octave + 1) + semi
    return 440.0 * (2.0 ** ((midi - 69) / 12.0))


def add_reverb(buf, rate, wet=0.30, taps=((0.031, 0.30), (0.047, 0.24), (0.071, 0.175))):
    """多延迟混响（非反馈，稳定）。"""
    src = buf[:]
    n = len(buf)
    for delay_s, gain in taps:
        d = int(delay_s * rate)
        g = wet * gain
        for i in range(d, n):
            buf[i] += src[i - d] * g
    return buf


def pcm16(vals):
    a = array("h", (max(-32768, min(32767, int(round(v * 32767.0)))) for v in vals))
    if sys.byteorder == "big":
        a.byteswap()
    return a.tobytes()


def write_wav(path, samples, rate, channels=1):
    if channels == 1:
        data = pcm16(samples)
    else:
        flat = []
        for l, r in samples:
            flat.append(l)
            flat.append(r)
        data = pcm16(flat)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(data)
    return len(data)


# ═══════════════════════════════════════════════════════════════════
#  音效
# ═══════════════════════════════════════════════════════════════════

def _n(seconds):
    return int(round(seconds * SFX_RATE))


def sfx_ui_click():
    """40ms 短促清脆点击：方波下行 + 极短噪声。"""
    n = _n(0.040)
    lp = LP(7500, SFX_RATE)
    rng = random.Random(101)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 1700.0 * t - 12500.0 * t * t          # 1700 -> 700 Hz
        e = min(1.0, t / 0.0012) * math.exp(-t / 0.0095)
        v = (s_square(ph) * 0.5 + s_tri(ph * 2.0) * 0.22) * e
        v += (rng.random() * 2 - 1) * math.exp(-t / 0.0022) * 0.55
        b.append(lp(v))
    return b


def sfx_ui_back():
    """55ms 下行点击。"""
    n = _n(0.055)
    lp = LP(5200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 980.0 * t - 7400.0 * t * t            # 980 -> 570 Hz
        e = min(1.0, t / 0.0015) * math.exp(-t / 0.017)
        b.append(lp((s_square(ph) * 0.42 + s_sine(ph) * 0.26) * e))
    return b


def sfx_ui_error():
    """130ms 低频嗡嗡拒绝声（两个近频正弦拍频）。"""
    n = _n(0.130)
    lp = LP(950, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        e = min(1.0, t / 0.006) * math.exp(-t / 0.058)
        v = (s_sine(153.0 * t) * 0.36 + s_sine(161.0 * t) * 0.30
             + s_square(76.5 * t) * 0.16)
        b.append(lp(v * e))
    return b


def sfx_hit():
    """35ms 命中：噪声爆破 + 中频脉冲。"""
    n = _n(0.035)
    rng = random.Random(202)
    lp = LP(6800, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        noise = (rng.random() * 2 - 1) * math.exp(-t / 0.0085) * 0.85
        ph = 430.0 * t - 3000.0 * t * t
        pulse = (s_sine(ph) * 0.5 + s_square(ph) * 0.12) * math.exp(-t / 0.013)
        b.append(lp(noise + pulse))
    return b


def sfx_hit_crit():
    """50ms 暴击：更亮更高 + 短噪声。"""
    n = _n(0.050)
    rng = random.Random(303)
    lp = LP(13500, SFX_RATE)
    hp = HP(1600, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        noise = hp(rng.random() * 2 - 1) * math.exp(-t / 0.010) * 0.75
        ph = 900.0 * t + 7000.0 * t * t            # 900 -> 1600 Hz
        tone = (s_sine(ph) * 0.45 + s_tri(ph * 1.5) * 0.22) * math.exp(-t / 0.019)
        ring = s_sine(2600.0 * t) * math.exp(-t / 0.030) * 0.22
        b.append(lp(noise + tone + ring))
    return b


def sfx_kill():
    """90ms 击杀：下扫 + 噪声尾。"""
    n = _n(0.090)
    rng = random.Random(404)
    lp = LP(4200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 900.0 * t - 4166.0 * t * t            # 900 -> 150 Hz
        sweep = (s_sine(ph) * 0.55 + s_tri(ph) * 0.18) * math.exp(-t / 0.032)
        tail = lp(rng.random() * 2 - 1) * math.exp(-t / 0.055) * 0.42
        sub = s_sine(58.0 * t) * math.exp(-t / 0.045) * 0.30
        b.append(sweep + tail + sub)
    return b


def sfx_hurt():
    """170ms 玩家受伤：低频闷响 + 轻失真。"""
    n = _n(0.170)
    rng = random.Random(505)
    lp = LP(620, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 140.0 * t - 354.0 * t * t             # 140 -> 55 Hz
        thud = (s_sine(ph) * 0.55 + s_tri(ph * 0.5) * 0.2) * math.exp(-t / 0.055)
        grit = math.tanh((rng.random() * 2 - 1) * 3.2) * math.exp(-t / 0.045) * 0.45
        b.append(lp(thud + grit))
    return b


def sfx_dash():
    """130ms 闪避：上行扫频嘶声。"""
    n = _n(0.130)
    rng = random.Random(606)
    b = []
    dt = 1.0 / SFX_RATE
    lp = LP(500, SFX_RATE)
    ph = 0.0
    for i in range(n):
        t = i * dt
        u = t / 0.130
        fc = 400.0 + 3600.0 * u * u
        a = 1.0 - math.exp(-TAU * fc / SFX_RATE)
        e = math.sin(math.pi * min(1.0, u)) ** 1.2
        x = rng.random() * 2 - 1
        lp.y += a * (x - lp.y)
        air = lp.y * e * 0.9
        ph += (260.0 + 700.0 * u) * dt
        tone = s_sine(ph) * e * 0.12
        b.append(air + tone)
    return b


def sfx_skill():
    """240ms 技能释放：和弦上扫。"""
    n = _n(0.240)
    lp = LP(3600, SFX_RATE)
    rng = random.Random(707)
    b = []
    dt = 1.0 / SFX_RATE
    notes = [(0.000, 440.00), (0.042, 554.37), (0.084, 659.25), (0.126, 880.00)]
    phases = [0.0] * len(notes)
    for i in range(n):
        t = i * dt
        v = 0.0
        for k, (start, f) in enumerate(notes):
            if t < start:
                continue
            lt = t - start
            phases[k] += f * (1.0 + 0.10 * min(1.0, lt / 0.12)) * dt
            v += (s_tri(phases[k]) * 0.55 + s_sine(phases[k] * 2.0) * 0.22) * \
                math.exp(-lt / 0.16) * min(1.0, lt / 0.004)
        sweep = s_sine((300.0 * t + 1800.0 * t * t) % 1.0) * 0.10 * math.exp(-t / 0.24)
        v += sweep + (rng.random() * 2 - 1) * math.exp(-t / 0.02) * 0.10
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.26)


def sfx_ult():
    """650ms 终极：多层和弦 + 冲击。"""
    n = _n(0.650)
    rng = random.Random(808)
    lp = LP(6000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    chord = [(0.030, 65.41), (0.030, 130.81), (0.045, 196.00),
             (0.060, 261.63), (0.075, 329.63), (0.090, 392.00)]
    phases = [0.0] * len(chord)
    lp2 = LP(600, SFX_RATE)
    phb = 0.0
    for i in range(n):
        t = i * dt
        phb += (52.0 + 60.0 * math.exp(-t / 0.09)) * dt
        v = s_sine(phb) * math.exp(-t / 0.22) * 0.55
        for k, (start, f) in enumerate(chord):
            if t < start:
                continue
            lt = t - start
            phases[k] += f * dt
            env = min(1.0, lt / 0.012) * math.exp(-lt / 0.30)
            v += (s_tri(phases[k]) * 0.55 + s_sine(phases[k] * 2.0) * 0.18) * env * 0.20
        u = min(1.0, t / 0.42)
        fc = 400.0 + 6500.0 * u * u
        a = 1.0 - math.exp(-TAU * fc / SFX_RATE)
        x = rng.random() * 2 - 1
        lp2.y += a * (x - lp2.y)
        v += lp2.y * (1.0 - u) * 0.30
        v += (rng.random() * 2 - 1) * math.exp(-t / 0.012) * 0.35
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.30)


def sfx_levelup():
    """520ms 升级：明亮大三和弦琶音上行。"""
    n = _n(0.520)
    lp = LP(7000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    seq = [(0.000, 523.25), (0.095, 659.25), (0.190, 783.99), (0.285, 1046.50)]
    phases = [0.0] * len(seq)
    for i in range(n):
        t = i * dt
        v = 0.0
        for k, (start, f) in enumerate(seq):
            if t < start:
                continue
            lt = t - start
            phases[k] += f * dt
            env = min(1.0, lt / 0.006) * math.exp(-lt / 0.26)
            v += (s_tri(phases[k]) * 0.5 + s_sine(phases[k] * 2.0) * 0.26
                  + s_sine(phases[k] * 3.0) * 0.10) * env * 0.26
        if t > 0.285:
            lt = t - 0.285
            for f in (523.25, 659.25, 783.99, 1046.50):
                v += s_sine(f * t) * math.exp(-lt / 0.30) * 0.045
        v += s_sine(2093.0 * t) * math.exp(-t / 0.05) * 0.05
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.24)


def sfx_coin():
    """65ms 拾取星尘：清脆金属音。"""
    n = _n(0.065)
    lp = LP(14000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        e = min(1.0, t / 0.001) * math.exp(-t / 0.020)
        v = (s_sine(2093.0 * t) * 0.45
             + s_sine(3136.0 * t) * 0.28
             + s_sine(4186.0 * t) * 0.14)
        v += (s_sine(660.0 * t) * 0.20) * math.exp(-t / 0.035)
        b.append(lp(v * e))
    return b


def sfx_buy():
    """220ms 购买：两段确认音。"""
    n = _n(0.220)
    lp = LP(5200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        v = 0.0
        if t < 0.085:
            e = min(1.0, t / 0.003) * math.exp(-t / 0.045) * (1.0 - max(0.0, (t - 0.060) / 0.025))
            v += (s_tri(660.0 * t) * 0.55 + s_sine(1320.0 * t) * 0.16) * e
        if t >= 0.095:
            lt = t - 0.095
            e = min(1.0, lt / 0.003) * math.exp(-lt / 0.055)
            v += (s_tri(990.0 * lt) * 0.55 + s_sine(1980.0 * lt) * 0.16) * e
        b.append(lp(v))
    return b


def sfx_chest():
    """480ms 开宝箱：金属摩擦 + 上扬。"""
    n = _n(0.480)
    rng = random.Random(909)
    lp = LP(2200, SFX_RATE)
    hp = HP(900, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph = 0.0
    for i in range(n):
        t = i * dt
        u = t / 0.480
        fr = 26.0 + 40.0 * u
        mod = 0.55 + 0.45 * math.sin(TAU * fr * t)
        grit = hp(rng.random() * 2 - 1) * mod * (1.0 - 0.5 * u) * 0.55
        ph += (180.0 + 520.0 * u * u) * dt
        rise = (s_tri(ph) * 0.35 + s_sine(ph * 2.0) * 0.15) * u * 0.6
        v = grit + rise
        if t > 0.30:
            lt = t - 0.30
            for f in (783.99, 1046.50, 1318.51):
                v += s_sine(f * lt) * math.exp(-lt / 0.10) * 0.09
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.24)


def sfx_heal():
    """250ms 治疗：柔和上扫。"""
    n = _n(0.250)
    lp = LP(4200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph1 = 0.0
    ph2 = 0.0
    for i in range(n):
        t = i * dt
        u = t / 0.250
        f = 440.0 * (2.0 ** u)                     # 440 -> 880
        ph1 += f * (1.0 + 0.006 * math.sin(TAU * 5.5 * t)) * dt
        ph2 += f * 1.5 * dt
        e = min(1.0, t / 0.045) * math.exp(-t / 0.20)
        v = (s_sine(ph1) * 0.5 + s_sine(ph1 * 2.0) * 0.12 + s_sine(ph2) * 0.20)
        b.append(lp(v * e))
    return add_reverb(b, SFX_RATE, wet=0.22)


def sfx_freeze():
    """210ms 冰冻：玻璃破碎 + 高频。"""
    n = _n(0.210)
    rng = random.Random(1111)
    hp = HP(2600, SFX_RATE)
    lp = LP(16000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        crack = hp(rng.random() * 2 - 1) * math.exp(-t / 0.016) * 0.75
        v = crack
        for f, g, dec in ((2350.0, 0.32, 0.055), (3720.0, 0.22, 0.045), (5130.0, 0.15, 0.035)):
            v += s_sine(f * t) * math.exp(-t / dec) * g
        v += s_sine(6200.0 * t) * math.exp(-t / 0.09) * 0.07
        if 0.06 < t < 0.20:
            v += hp(rng.random() * 2 - 1) * math.exp(-(t - 0.06) / 0.03) * 0.10
        b.append(lp(v))
    return b


def sfx_explode():
    """420ms 爆炸：宽频噪声 + 低频轰。"""
    n = _n(0.420)
    rng = random.Random(1212)
    b = []
    dt = 1.0 / SFX_RATE
    lp = LP(9000, SFX_RATE)
    ph = 0.0
    for i in range(n):
        t = i * dt
        u = min(1.0, t / 0.30)
        fc = 9000.0 * (1.0 - u) ** 2.2 + 220.0
        a = 1.0 - math.exp(-TAU * fc / SFX_RATE)
        x = rng.random() * 2 - 1
        lp.y += a * (x - lp.y)
        body = lp.y * math.exp(-t / 0.13) * 0.95
        ph += (50.0 + 90.0 * math.exp(-t / 0.05)) * dt
        boom = s_sine(ph) * math.exp(-t / 0.18) * 0.75
        crackle = (rng.random() * 2 - 1) * math.exp(-t / 0.006) * 0.5
        b.append(soft_clip(body + boom + crackle))
    return add_reverb(b, SFX_RATE, wet=0.26)


def sfx_boss_warn():
    """520ms 首领预警：不谐和二度 + 渐强。"""
    n = _n(0.520)
    rng = random.Random(1313)
    lp = LP(1600, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        u = t / 0.520
        swell = u ** 1.6
        trem = 0.72 + 0.28 * math.sin(TAU * 7.0 * t)
        v = (s_sine(110.0 * t) * 0.30
             + s_sine(116.54 * t) * 0.30            # 小二度（A / Bb）
             + s_tri(55.0 * t) * 0.22
             + s_sine(220.0 * t) * 0.12)
        v *= swell * trem
        v += (rng.random() * 2 - 1) * u * 0.05
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.22)


def sfx_victory():
    """1500ms 胜利：明亮和弦解决 I - IV - V - I。"""
    n = _n(1.500)
    lp = LP(6500, SFX_RATE)
    rng = random.Random(1414)
    b = [0.0] * n
    dt = 1.0 / SFX_RATE
    chords = [
        (0.00, [261.63, 329.63, 392.00, 523.25], 0.60),
        (0.36, [349.23, 440.00, 523.25, 698.46], 0.60),
        (0.70, [392.00, 493.88, 587.33, 783.99], 0.30),
        (1.00, [261.63, 329.63, 392.00, 523.25, 659.25, 783.99], 0.50),
    ]
    for start, notes, dec in chords:
        n0 = int(start * SFX_RATE)
        for f in notes:
            ph = 0.0
            for i in range(n - n0):
                t = i * dt
                ph += f * dt
                env = min(1.0, t / 0.008) * math.exp(-t / dec)
                v = s_tri(ph) * 0.42 + s_saw(ph * 1.001) * 0.10 + s_sine(ph * 2.0) * 0.14
                b[n0 + i] += lp(v * env * 0.20)
    for i in range(n):
        t = i * dt
        b[i] += (rng.random() * 2 - 1) * math.exp(-t / 0.03) * 0.12
    add_reverb(b, SFX_RATE, wet=0.30)
    return b


def sfx_defeat():
    """1700ms 失败：下行小调 Am - F - Dm - E。"""
    n = _n(1.700)
    lp = LP(1500, SFX_RATE)
    b = [0.0] * n
    dt = 1.0 / SFX_RATE
    chords = [
        (0.00, [220.00, 261.63, 329.63], 0.75),
        (0.42, [174.61, 220.00, 261.63], 0.65),
        (0.80, [146.83, 174.61, 220.00], 0.55),
        (1.16, [82.41, 110.00, 130.81], 0.55),
    ]
    for start, notes, dec in chords:
        n0 = int(start * SFX_RATE)
        for f in notes:
            ph = 0.0
            for i in range(n - n0):
                t = i * dt
                ph += f * dt
                env = min(1.0, t / 0.02) * math.exp(-t / dec)
                v = s_tri(ph) * 0.40 + s_sine(ph) * 0.30
                b[n0 + i] += v * env * 0.20
    for i in range(n):
        t = i * dt
        if t > 1.10:
            lt = t - 1.10
            b[i] += s_sine(55.0 * lt) * math.exp(-lt / 0.45) * 0.18 * min(1.0, lt / 0.05)
    b = [lp(v) for v in b]
    add_reverb(b, SFX_RATE, wet=0.34)
    return b


# ═══════════════════════════════════════════════════════════════════
#  v2.0 新增音效（契约 / 天赋 / 精英 / 波次 / 连击 / 神龛 / 赌局）
#  合成参数（LP/HP 截止、ADSR、混响湿声）刻意复用上面 v1.0 那套，
#  保证新老音效是同一个"乐器音色库"。
# ═══════════════════════════════════════════════════════════════════

def sfx_pact_on():
    """70ms 契约词条开启：符文刻入的短促金属啮合（上行）。"""
    n = _n(0.070)
    rng = random.Random(2101)
    lp = LP(9000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 620.0 * t + 5200.0 * t * t            # 620 -> 985
        e = min(1.0, t / 0.0018) * math.exp(-t / 0.020)
        gear = (s_tri(ph) * 0.46 + s_sine(ph * 2.02) * 0.24 + s_square(ph * 0.5) * 0.10) * e
        click = (rng.random() * 2 - 1) * math.exp(-t / 0.0025) * 0.42
        b.append(lp(gear + click))
    return b


def sfx_pact_off():
    """65ms 契约词条关闭：啮合松开（下行），比 pact_on 更暗。"""
    n = _n(0.065)
    rng = random.Random(2102)
    lp = LP(4200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        ph = 900.0 * t - 4000.0 * t * t            # 900 -> 640
        e = min(1.0, t / 0.002) * math.exp(-t / 0.019)
        gear = (s_tri(ph) * 0.44 + s_sine(ph * 0.5) * 0.22) * e
        click = (rng.random() * 2 - 1) * math.exp(-t / 0.0035) * 0.34
        b.append(lp(gear + click))
    return b


def sfx_pact_confirm():
    """320ms 带词条出发：压迫感下行（小二度 → 根音），低通闷响收尾。"""
    n = _n(0.320)
    rng = random.Random(2103)
    lp = LP(2400, SFX_RATE)
    hp = HP(70, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph1 = 0.0
    ph2 = 0.0
    phb = 0.0
    for i in range(n):
        t = i * dt
        u = min(1.0, t / 0.320)
        f = 233.08 * (1.0 - 0.46 * u)              # Bb3 -> 约 F3
        ph1 += f * dt
        ph2 += f * 1.0595 * dt                     # 小二度叠置，制造压迫
        e = min(1.0, t / 0.008) * math.exp(-t / 0.150)
        v = (s_tri(ph1) * 0.40 + s_saw(ph2) * 0.16 + s_sine(ph1 * 0.5) * 0.22) * e
        phb += (58.0 + 40.0 * math.exp(-t / 0.06)) * dt
        v += s_sine(phb) * math.exp(-t / 0.13) * 0.34
        v += (rng.random() * 2 - 1) * math.exp(-t / 0.010) * 0.14
        b.append(lp(hp(v)))
    return add_reverb(b, SFX_RATE, wet=0.26)


def sfx_doctrine_select():
    """110ms 天赋牌选择：纸牌翻转的短促双音（上行三度）。"""
    n = _n(0.110)
    lp = LP(7600, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        v = 0.0
        if t < 0.052:
            e = min(1.0, t / 0.002) * math.exp(-t / 0.024)
            v += (s_tri(587.33 * t) * 0.50 + s_sine(1174.66 * t) * 0.18) * e
        else:
            lt = t - 0.052
            e = min(1.0, lt / 0.002) * math.exp(-lt / 0.028)
            v += (s_tri(739.99 * lt) * 0.50 + s_sine(1479.98 * lt) * 0.18) * e
        b.append(lp(v))
    return b


def sfx_doctrine_lock():
    """230ms 天赋装备锁定：咔嚓 + 低频确认，比 select 更"重"。"""
    n = _n(0.230)
    rng = random.Random(2104)
    lp = LP(3200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph = 0.0
    for i in range(n):
        t = i * dt
        # 第一击：金属卡扣
        latch = 0.0
        if t < 0.030:
            latch = (rng.random() * 2 - 1) * math.exp(-t / 0.004) * 0.62
        # 主体：A3 稳定音 + 五度
        if 0.012 < t < 0.230:
            lt = t - 0.012
            ph += 220.0 * dt
            e = min(1.0, lt / 0.004) * math.exp(-lt / 0.070)
            latch += (s_tri(ph) * 0.42 + s_sine(ph * 1.5) * 0.20) * e
        # 锁定确认：高八度短点
        if 0.075 < t < 0.230:
            lt = t - 0.075
            latch += s_sine(880.0 * lt) * math.exp(-lt / 0.055) * 0.14
        b.append(lp(latch))
    return add_reverb(b, SFX_RATE, wet=0.20)


def sfx_elite_warn():
    """360ms 精英预警：与 boss_warn 同构但轻一档（不谐和二度，低频减弱、时长更短）。"""
    n = _n(0.360)
    rng = random.Random(2105)
    lp = LP(2100, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        u = t / 0.360
        swell = u ** 1.5
        trem = 0.78 + 0.22 * math.sin(TAU * 8.5 * t)
        v = (s_sine(146.83 * t) * 0.26
             + s_sine(155.56 * t) * 0.26            # D / Eb 小二度
             + s_tri(73.42 * t) * 0.14
             + s_sine(293.66 * t) * 0.10)
        v *= swell * trem
        v += (rng.random() * 2 - 1) * u * 0.035
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.20)


def sfx_wave_start():
    """300ms 波次开始：干净的上行五度信号音（可被连击/战斗噪声盖住但不糊）。"""
    n = _n(0.300)
    lp = LP(6200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    notes = [(0.000, 440.00), (0.070, 587.33), (0.140, 880.00)]
    ph = [0.0] * len(notes)
    for i in range(n):
        t = i * dt
        v = 0.0
        for k, (start, f) in enumerate(notes):
            if t < start:
                continue
            lt = t - start
            ph[k] += f * dt
            e = min(1.0, lt / 0.004) * math.exp(-lt / 0.130)
            v += (s_tri(ph[k]) * 0.48 + s_sine(ph[k] * 2.0) * 0.20) * e
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.22)


def sfx_combo_10():
    """150ms 连击 10：第一层——单音 + 轻亮边。"""
    n = _n(0.150)
    lp = LP(8200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        e = min(1.0, t / 0.002) * math.exp(-t / 0.052)
        v = (s_tri(783.99 * t) * 0.46 + s_sine(1567.98 * t) * 0.18
             + s_sine(2351.97 * t) * 0.06)
        b.append(lp(v * e))
    return add_reverb(b, SFX_RATE, wet=0.20)


def sfx_combo_30():
    """230ms 连击 30：第二层——五度双音 + 更亮谐波（明显高于 combo_10）。"""
    n = _n(0.230)
    lp = LP(9800, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph1 = 0.0
    ph2 = 0.0
    for i in range(n):
        t = i * dt
        e = min(1.0, t / 0.002) * math.exp(-t / 0.080)
        ph1 += 880.00 * dt
        ph2 += 1318.51 * dt
        v = (s_tri(ph1) * 0.40 + s_tri(ph2) * 0.30
             + s_sine(ph1 * 2.0) * 0.14 + s_sine(ph2 * 2.0) * 0.10)
        b.append(lp(v * e))
    return add_reverb(b, SFX_RATE, wet=0.22)


def sfx_combo_60():
    """400ms 连击 60：第三层——大三和弦琶音 + 高频闪光，最亮最长的里程碑。"""
    n = _n(0.400)
    lp = LP(12000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    notes = [(0.000, 1046.50), (0.045, 1318.51), (0.090, 1567.98), (0.135, 2093.00)]
    ph = [0.0] * len(notes)
    for i in range(n):
        t = i * dt
        v = 0.0
        for k, (start, f) in enumerate(notes):
            if t < start:
                continue
            lt = t - start
            ph[k] += f * dt
            e = min(1.0, lt / 0.003) * math.exp(-lt / 0.120)
            v += (s_tri(ph[k]) * 0.34 + s_sine(ph[k] * 2.0) * 0.14) * e
        # 尾部高频闪烁
        v += s_sine(4186.0 * t) * math.exp(-t / 0.09) * 0.06
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.26)


def sfx_shrine_pick():
    """260ms 神龛选择：庄严的钟鸣 + 低沉确认（不是金属音，走铜钟味）。"""
    n = _n(0.260)
    lp = LP(5200, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    for i in range(n):
        t = i * dt
        e = min(1.0, t / 0.004) * math.exp(-t / 0.105)
        # 钟的泛音比列（1, 2.0, 2.76, 5.4）
        f = 523.25
        v = (s_sine(f * t) * 0.40 + s_sine(f * 2.0 * t) * 0.20
             + s_sine(f * 2.76 * t) * 0.12 + s_sine(f * 5.40 * t) * 0.05)
        v += s_sine(f * 0.5 * t) * math.exp(-t / 0.150) * 0.18
        b.append(lp(v * e))
    return add_reverb(b, SFX_RATE, wet=0.30)


def sfx_gamble_win():
    """520ms 赌局赢：上行大调三音 + 硬币叮当（和 coin/victory 同一族音色）。"""
    n = _n(0.520)
    rng = random.Random(2106)
    lp = LP(11000, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    seq = [(0.000, 659.25), (0.080, 830.61), (0.160, 1046.50)]
    ph = [0.0] * len(seq)
    for i in range(n):
        t = i * dt
        v = 0.0
        for k, (start, f) in enumerate(seq):
            if t < start:
                continue
            lt = t - start
            ph[k] += f * dt
            e = min(1.0, lt / 0.003) * math.exp(-lt / 0.150)
            v += (s_tri(ph[k]) * 0.36 + s_sine(ph[k] * 2.0) * 0.22
                  + s_sine(ph[k] * 3.0) * 0.08) * e
        # 结束和弦
        if t > 0.160:
            lt = t - 0.160
            for f in (1318.51, 1661.22, 2093.00):
                v += s_sine(f * lt) * math.exp(-lt / 0.180) * 0.055
        # 硬币颗粒
        if t < 0.30:
            v += (rng.random() * 2 - 1) * math.exp(-t / 0.012) * 0.10
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.24)


def sfx_gamble_lose():
    """460ms 赌局输：下行小二度塌陷 + 一点粗糙感（与 defeat 同族但短很多）。"""
    n = _n(0.460)
    rng = random.Random(2107)
    lp = LP(2600, SFX_RATE)
    b = []
    dt = 1.0 / SFX_RATE
    ph1 = 0.0
    ph2 = 0.0
    for i in range(n):
        t = i * dt
        u = min(1.0, t / 0.460)
        f = 392.00 * (1.0 - 0.42 * u)               # G4 -> 约 Eb4
        ph1 += f * dt
        ph2 += f * 0.9439 * dt                      # 半音下沉，塌陷感
        e = min(1.0, t / 0.006) * math.exp(-t / 0.140)
        v = (s_tri(ph1) * 0.40 + s_tri(ph2) * 0.26
             + s_sine(ph1 * 0.5) * 0.16) * e
        v += (rng.random() * 2 - 1) * math.exp(-t / 0.020) * 0.10
        # 尾部低频下坠
        if t > 0.22:
            lt = t - 0.22
            v += s_sine(98.0 * lt) * math.exp(-lt / 0.16) * 0.16
        b.append(lp(v))
    return add_reverb(b, SFX_RATE, wet=0.26)


# 新增音效的相对响度目标（相对于音效基线的偏移 dB）。0 = 与打击音同级。
# 负值 = UI/提示类更轻；正值 = 里程碑/结果类更突出。
# 这些偏移在 LUFS 归一时生效，保证"UI 不会比打击音还响"。
SFX_SPEC = [
    ("ui_click", 0.040, sfx_ui_click, 0.0),
    ("ui_back", 0.055, sfx_ui_back, 0.0),
    ("ui_error", 0.130, sfx_ui_error, 0.0),
    ("hit", 0.035, sfx_hit, 0.0),
    ("hit_crit", 0.050, sfx_hit_crit, +1.5),
    ("kill", 0.090, sfx_kill, +0.5),
    ("hurt", 0.170, sfx_hurt, 0.0),
    ("dash", 0.130, sfx_dash, 0.0),
    ("skill", 0.240, sfx_skill, +0.5),
    ("ult", 0.650, sfx_ult, +2.5),
    ("levelup", 0.520, sfx_levelup, +2.0),
    ("coin", 0.065, sfx_coin, 0.0),
    ("buy", 0.220, sfx_buy, 0.0),
    ("chest", 0.480, sfx_chest, +1.5),
    ("heal", 0.250, sfx_heal, 0.0),
    ("freeze", 0.210, sfx_freeze, 0.0),
    ("explode", 0.420, sfx_explode, +2.5),
    ("boss_warn", 0.520, sfx_boss_warn, +2.0),
    ("victory", 1.500, sfx_victory, +2.5),
    ("defeat", 1.700, sfx_defeat, +2.5),
    # ── v2.0 新增 ──
    ("pact_on", 0.070, sfx_pact_on, 0.0),
    ("pact_off", 0.065, sfx_pact_off, 0.0),
    ("pact_confirm", 0.320, sfx_pact_confirm, +1.5),
    ("doctrine_select", 0.110, sfx_doctrine_select, 0.0),
    ("doctrine_lock", 0.230, sfx_doctrine_lock, +1.0),
    ("elite_warn", 0.360, sfx_elite_warn, +1.0),
    ("wave_start", 0.300, sfx_wave_start, +1.0),
    ("combo_10", 0.150, sfx_combo_10, 0.0),
    ("combo_30", 0.230, sfx_combo_30, +1.5),
    ("combo_60", 0.400, sfx_combo_60, +3.0),
    ("shrine_pick", 0.260, sfx_shrine_pick, +1.0),
    ("gamble_win", 0.520, sfx_gamble_win, +2.0),
    ("gamble_lose", 0.460, sfx_gamble_lose, 0.0),
]

# 音效响度基线（LUFS）。所有音效先对到这条线，再叠加 SFX_SPEC 的偏移。
# -19.5 是 v1.0 实测 20 个音效的 LUFS 均值，保持它可让新老音效听感一致。
SFX_BASE_LUFS = -19.5


def build_sfx():
    """生成全部音效并按 LUFS 对齐。

    v1.0 教训：按峰值归一化时，ui_click(RMS -25.8) 与 explode(RMS -13.8) 相差 12 dB，
    用户会觉得"点按钮比打怪还响"。v2.0 改为整合 LUFS 对齐 + 峰值天花板。
    """
    results = []
    for name, dur, fn, loud_offset in SFX_SPEC:
        buf = fn()
        want = _n(dur)
        if len(buf) < want:
            buf = buf + [0.0] * (want - len(buf))
        buf = buf[:want]
        buf = fade_edges(buf, SFX_RATE, 0.8, min(20.0, max(4.0, dur * 200.0)))
        buf = dc_block(buf)
        target = SFX_BASE_LUFS + loud_offset
        buf, lufs_before, lufs_after, peak_db = loudness_match(buf, SFX_RATE, target)
        path = OUT / (name + ".wav")
        size = write_wav(path, buf, SFX_RATE, 1)
        results.append(dict(name=name, dur=dur, path=path, size=size,
                            lufs=lufs_after, lufs_raw=lufs_before,
                            peak_db=peak_db, target=target,
                            rms_db=db(math.sqrt(sum(v * v for v in buf) / len(buf)))))
    return results


# ═══════════════════════════════════════════════════════════════════
#  音乐引擎
# ═══════════════════════════════════════════════════════════════════

class Music:
    def __init__(self, name, bpm, bars, tail=1.4, beats=4, pad_lp=1700):
        self.name = name
        self.rate = MUSIC_RATE
        self.beat = 60.0 / bpm
        self.bar = self.beat * beats
        self.bpm = bpm
        self.bars = bars
        self.total = self.bar * bars
        self.n = int(round(self.total * self.rate))
        self.tail = int(tail * self.rate)
        self.buf = [0.0] * (self.n + self.tail)
        self.pad_lp = pad_lp
        self.rng = random.Random(20240607)

    def at(self, bar, beat=0.0):
        return bar * self.bar + beat * self.beat

    # ── 乐器 ──
    def pad_chord(self, start, dur, notes, amp=0.085, attack=0.25, release=0.55):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(dur * rate), len(buf) - n0)
        freqs = [note_hz(x) for x in notes]
        ph = [0.0] * len(freqs)
        filt = LP(self.pad_lp, rate)
        dt = 1.0 / rate
        scale = amp / max(1, len(freqs))
        d = dur * 0.3
        for i in range(n):
            t = i * dt
            e = adsr(t, dur, attack, d, 0.78, release)
            v = 0.0
            for k, f in enumerate(freqs):
                p = ph[k]
                ph[k] = p + f * dt
                v += s_tri(p) * 0.62 + s_sine(p) * 0.38
            buf[n0 + i] += filt(v) * e * scale

    def stab(self, start, dur, notes, amp=0.09, lp=2200, wave="saw"):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(dur * rate), len(buf) - n0)
        freqs = [note_hz(x) for x in notes]
        ph = [0.0] * len(freqs)
        filt = LP(lp, rate)
        dt = 1.0 / rate
        scale = amp / max(1, len(freqs))
        wf = WAVES[wave]
        for i in range(n):
            t = i * dt
            e = min(1.0, t / 0.006) * math.exp(-t / max(0.05, dur * 0.55))
            v = 0.0
            for k, f in enumerate(freqs):
                p = ph[k]
                ph[k] = p + f * dt
                v += wf(p) * 0.7 + s_sine(p) * 0.3
            buf[n0 + i] += filt(v) * e * scale

    def bass(self, start, dur, note, amp=0.20, lp=460, decay=None):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(dur * rate), len(buf) - n0)
        f = note_hz(note) if isinstance(note, str) else float(note)
        ph = 0.0
        filt = LP(lp, rate)
        dt = 1.0 / rate
        d = decay if decay else dur
        for i in range(n):
            t = i * dt
            e = min(1.0, t / 0.006) * math.exp(-t / d)
            ph += f * dt
            v = s_sine(ph) * 0.80 + s_tri(ph) * 0.32
            buf[n0 + i] += filt(v) * e * amp

    def lead(self, start, dur, note, amp=0.14, wave="tri", lp=3000,
             decay=None, attack=0.006, vibrato=0.0, vib_hz=5.2):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(dur * rate), len(buf) - n0)
        f = note_hz(note) if isinstance(note, str) else float(note)
        wf = WAVES[wave]
        filt = LP(lp, rate)
        dt = 1.0 / rate
        ph = 0.0
        d = decay if decay else max(0.08, dur)
        for i in range(n):
            t = i * dt
            ff = f * (1.0 + vibrato * math.sin(TAU * vib_hz * t)) if vibrato else f
            ph += ff * dt
            e = min(1.0, t / attack) * math.exp(-t / d)
            v = wf(ph) * 0.72 + s_sine(ph * 2.0) * 0.20 + s_sine(ph * 3.0) * 0.06
            buf[n0 + i] += filt(v) * e * amp

    def bell(self, start, note, amp=0.05, decay=1.2):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(min(2.5, decay * 2.0) * rate), len(buf) - n0)
        f = note_hz(note) if isinstance(note, str) else float(note)
        dt = 1.0 / rate
        for i in range(n):
            t = i * dt
            e = min(1.0, t / 0.004) * math.exp(-t / decay)
            v = (s_sine(f * t) * 0.62 + s_sine(f * 2.76 * t) * 0.20
                 + s_sine(f * 5.40 * t) * 0.07)
            buf[n0 + i] += v * e * amp

    def drone(self, start, dur, freqs, amp=0.05, trem=0.0, trem_hz=0.15):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(dur * rate), len(buf) - n0)
        ph = [0.0] * len(freqs)
        dt = 1.0 / rate
        for i in range(n):
            t = i * dt
            e = min(1.0, t / 1.5) * min(1.0, (dur - t) / 1.5 if dur > t else 0.0)
            if e <= 0:
                continue
            mod = 1.0 + trem * math.sin(TAU * trem_hz * t)
            v = 0.0
            for k, f in enumerate(freqs):
                ph[k] += f * dt
                v += s_sine(ph[k])
            buf[n0 + i] += v * e * amp * mod / max(1, len(freqs))

    def noise_bed(self, dur, amp=0.02, lp_fc=700):
        rate = self.rate
        buf = self.buf
        n = min(int(dur * rate), len(buf))
        filt = LP(lp_fc, rate)
        dt = 1.0 / rate
        for i in range(n):
            t = i * dt
            e = min(1.0, t / 2.0) * min(1.0, max(0.0, (dur - t) / 2.0))
            buf[i] += filt(self.rng.random() * 2 - 1) * e * amp

    # ── 鼓 ──
    def kick(self, start, amp=0.5):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(0.26 * rate), len(buf) - n0)
        dt = 1.0 / rate
        ph = 0.0
        for i in range(n):
            t = i * dt
            ph += (40.0 + 118.0 * math.exp(-t / 0.028)) * dt
            e = math.exp(-t / 0.075)
            v = math.sin(TAU * ph) * e * amp
            v += (self.rng.random() * 2 - 1) * math.exp(-t / 0.0035) * amp * 0.30
            buf[n0 + i] += v

    def snare(self, start, amp=0.30):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(0.18 * rate), len(buf) - n0)
        dt = 1.0 / rate
        hp = HP(700, rate)
        lp = LP(9000, rate)
        for i in range(n):
            t = i * dt
            e = math.exp(-t / 0.058)
            noise = hp(self.rng.random() * 2 - 1)
            tone = math.sin(TAU * 192.0 * t) * math.exp(-t / 0.032)
            buf[n0 + i] += (lp(noise) * 0.72 + tone * 0.32) * e * amp

    def hat(self, start, amp=0.06, open_=False):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        dur = 0.16 if open_ else 0.05
        n = min(int(dur * rate), len(buf) - n0)
        dt = 1.0 / rate
        hp = HP(6500, rate)
        d = 0.055 if open_ else 0.016
        for i in range(n):
            t = i * dt
            buf[n0 + i] += hp(self.rng.random() * 2 - 1) * math.exp(-t / d) * amp

    def tom(self, start, note="A2", amp=0.28):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(0.22 * rate), len(buf) - n0)
        dt = 1.0 / rate
        f0 = note_hz(note)
        ph = 0.0
        for i in range(n):
            t = i * dt
            ph += (f0 * 0.55 + f0 * 0.6 * math.exp(-t / 0.05)) * dt
            e = math.exp(-t / 0.09)
            v = math.sin(TAU * ph) * e * amp
            v += (self.rng.random() * 2 - 1) * math.exp(-t / 0.006) * amp * 0.12
            buf[n0 + i] += v

    def crash(self, start, amp=0.10):
        rate = self.rate
        buf = self.buf
        n0 = int(start * rate)
        if n0 >= len(buf):
            return
        n = min(int(0.9 * rate), len(buf) - n0)
        dt = 1.0 / rate
        hp = HP(4200, rate)
        for i in range(n):
            t = i * dt
            buf[n0 + i] += hp(self.rng.random() * 2 - 1) * math.exp(-t / 0.32) * amp

    # ── 收尾 ──
    def finish(self, peak_db=-3.0, fade_ms=20.0):
        """尾巴环形折回开头 → 无缝循环；首尾 20ms 淡入淡出防爆音。"""
        buf = self.buf
        n = self.n
        for i in range(self.tail):
            buf[i] += buf[n + i]
        out = buf[:n]
        fade_edges(out, self.rate, fade_ms, fade_ms)
        return normalize(dc_block(out), peak_db)

    def loop_seam(self, out):
        return abs(out[0]), abs(out[-1])


# ── 曲式 ─────────────────────────────────────────────────────────

def song_menu():
    """60 BPM / 32s / C 大调五声 / I - vi - IV - V。氛围铺底 + 稀疏拨弦。"""
    m = Music("music_menu", bpm=60.0, bars=8, tail=1.8, pad_lp=1500)
    prog = [
        ["C3", "G3", "B3", "E4"],     # Cmaj9
        ["A2", "E3", "G3", "C4"],     # Am7
        ["F2", "C3", "E3", "A3"],     # Fmaj7
        ["G2", "D3", "E3", "B3"],     # G6
    ]
    for bar in range(8):
        m.pad_chord(m.at(bar), m.bar * 1.0, prog[bar % 4], amp=0.085,
                    attack=0.7 if bar % 4 == 0 else 0.30)
    m.drone(0.0, m.total, [65.41, 98.00], amp=0.055, trem=0.18, trem_hz=0.11)
    m.noise_bed(m.total, amp=0.016, lp_fc=900)
    roots = ["C2", "A1", "F1", "G1"]
    for bar in range(8):
        m.bass(m.at(bar, 0), m.beat * 3.4, roots[bar % 4], amp=0.16, lp=380, decay=1.6)
    mel = [
        (2, 0, 2.0, "E5"), (2, 2.0, 1.0, "G5"), (2, 3.0, 1.0, "A5"),
        (3, 0, 2.0, "G5"), (3, 2.0, 1.0, "E5"), (3, 3.0, 1.0, "D5"),
        (4, 0, 2.0, "C6"), (4, 2.0, 2.0, "G5"),
        (5, 0, 2.0, "A5"), (5, 2.0, 2.0, "E5"),
        (6, 0, 2.0, "E5"), (6, 2.0, 1.0, "G5"), (6, 3.0, 1.0, "A5"),
        (7, 0, 2.0, "G5"), (7, 2.0, 1.0, "D5"), (7, 3.0, 1.0, "E5"),
    ]
    for bar, beat, dur, note in mel:
        m.lead(m.at(bar, beat), m.beat * dur * 0.96, note, amp=0.135,
               wave="tri", lp=3200, decay=m.beat * dur * 0.85, attack=0.010)
    for bar, beat, note in ((1, 2.0, "E6"), (5, 3.0, "G6"), (7, 3.0, "C6")):
        m.bell(m.at(bar, beat), note, amp=0.045, decay=1.6)
    add_reverb(m.buf, m.rate, wet=0.42,
               taps=((0.093, 0.34), (0.131, 0.26), (0.179, 0.20), (0.233, 0.14)))
    return m


def song_combat_0():
    """100 BPM / 33.6s / A 自然小调 / i - VI - III - VII。中速战斗。"""
    m = Music("music_combat_0", bpm=100.0, bars=14, tail=1.2, pad_lp=1900)
    prog = [["A2", "C3", "E3"], ["F2", "A2", "C3"], ["C3", "E3", "G3"], ["G2", "B2", "D3"]]
    roots = ["A2", "F2", "C2", "G2"]

    def chord_at(bar):
        return prog[(bar - 2) % 4] if bar >= 2 else prog[bar % 4]

    for bar in range(14):
        m.pad_chord(m.at(bar), m.bar * 1.0, chord_at(bar), amp=0.075,
                    attack=0.10 if bar >= 2 else 0.5)
        root = roots[(bar - 2) % 4] if bar >= 2 else roots[bar % 4]
        for beat in (0.0, 0.75, 1.5, 2.0, 2.75, 3.5):
            m.bass(m.at(bar, beat), m.beat * 0.42, root, amp=0.19, lp=430)
        if bar < 2:
            m.kick(m.at(bar, 0), 0.50)
            m.kick(m.at(bar, 2), 0.46)
            for e in range(4):
                m.hat(m.at(bar, e * 1.0), 0.07)
        else:
            m.kick(m.at(bar, 0.0), 0.55)
            m.kick(m.at(bar, 2.0), 0.50)
            m.kick(m.at(bar, 3.5), 0.28)
            m.snare(m.at(bar, 1.0), 0.30)
            m.snare(m.at(bar, 3.0), 0.32)
            for e in range(8):
                m.hat(m.at(bar, e * 0.5), 0.095 if e % 4 == 0 else (0.07 if e % 2 else 0.055))
            if bar in (2, 6, 10):
                m.crash(m.at(bar, 0.0), 0.055)
    A = [
        (0, 0.0, 2.0, "A4"), (0, 2.0, 1.0, "C5"), (0, 3.0, 1.0, "E5"),
        (1, 0.0, 2.0, "F5"), (1, 2.0, 1.0, "E5"), (1, 3.0, 1.0, "C5"),
        (2, 0.0, 1.5, "D5"), (2, 1.5, 0.5, "E5"), (2, 2.0, 2.0, "G5"),
        (3, 0.0, 3.0, "A5"), (3, 3.0, 1.0, "G5"),
    ]
    B = [
        (0, 0.0, 1.0, "E5"), (0, 1.0, 1.0, "G5"), (0, 2.0, 2.0, "A5"),
        (1, 0.0, 2.0, "C6"), (1, 2.0, 1.0, "A5"), (1, 3.0, 1.0, "G5"),
        (2, 0.0, 1.0, "F5"), (2, 1.0, 1.0, "E5"), (2, 2.0, 2.0, "D5"),
        (3, 0.0, 2.0, "C5"), (3, 2.0, 1.0, "B4"), (3, 3.0, 1.0, "D5"),
    ]
    for base in (2, 10):
        for b, beat, dur, note in A:
            m.lead(m.at(base + b, beat), m.beat * dur * 0.95, note, amp=0.155,
                   wave="tri", lp=3100, decay=m.beat * dur * 0.9)
    for b, beat, dur, note in B:
        m.lead(m.at(6 + b, beat), m.beat * dur * 0.95, note, amp=0.150,
               wave="tri", lp=3300, decay=m.beat * dur * 0.9)
    add_reverb(m.buf, m.rate, wet=0.28)
    return m


def song_combat_1():
    """130 BPM / 33.2s / D 自然小调 / i - VI - III - VII。高强度战斗。"""
    m = Music("music_combat_1", bpm=130.0, bars=18, tail=1.2, pad_lp=2100)
    prog = [["D3", "F3", "A3"], ["Bb2", "D3", "F3"], ["F2", "A2", "C3"], ["C3", "E3", "G3"]]
    roots = ["D2", "Bb1", "F2", "C2"]

    def chord_at(bar):
        return prog[(bar - 2) % 4] if bar >= 2 else prog[bar % 4]

    for bar in range(18):
        m.pad_chord(m.at(bar), m.bar * 1.0, chord_at(bar), amp=0.070,
                    attack=0.08 if bar >= 2 else 0.4)
        root = roots[(bar - 2) % 4] if bar >= 2 else roots[bar % 4]
        for e in range(8):
            note = root if e % 4 != 3 else note_hz(root) * 2.0
            m.bass(m.at(bar, e * 0.5), m.beat * 0.24, note, amp=0.175, lp=520, decay=0.14)
        if bar < 2:
            m.kick(m.at(bar, 0.0), 0.55)
            m.kick(m.at(bar, 1.5), 0.42)
            m.kick(m.at(bar, 2.5), 0.48)
            m.snare(m.at(bar, 3.0), 0.26)
            for e in range(8):
                m.hat(m.at(bar, e * 0.5), 0.09 if e % 4 == 0 else 0.055)
        else:
            m.kick(m.at(bar, 0.0), 0.58)
            m.kick(m.at(bar, 1.75), 0.40)
            m.kick(m.at(bar, 2.5), 0.52)
            m.snare(m.at(bar, 1.0), 0.28)
            m.snare(m.at(bar, 3.0), 0.33)
            for e in range(16):
                m.hat(m.at(bar, e * 0.25), 0.085 if e % 4 == 0 else 0.045)
            m.hat(m.at(bar, 3.5), 0.07, open_=True)
            if bar in (2, 6, 10, 14):
                m.crash(m.at(bar, 0.0), 0.075)
    A = [
        (0, 0.0, 0.5, "D5"), (0, 0.5, 0.5, "F5"), (0, 1.0, 1.0, "A5"),
        (0, 2.0, 1.0, "C6"), (0, 3.0, 1.0, "A5"),
        (1, 0.0, 1.5, "Bb5"), (1, 1.5, 0.5, "A5"), (1, 2.0, 1.0, "F5"), (1, 3.0, 1.0, "D5"),
        (2, 0.0, 0.5, "F5"), (2, 0.5, 0.5, "G5"), (2, 1.0, 1.0, "A5"), (2, 2.0, 2.0, "C6"),
        (3, 0.0, 1.0, "C6"), (3, 1.0, 1.0, "A5"), (3, 2.0, 1.0, "G5"), (3, 3.0, 1.0, "E5"),
    ]
    A2 = [
        (0, 0.0, 0.5, "A5"), (0, 0.5, 0.5, "C6"), (0, 1.0, 1.0, "D6"),
        (0, 2.0, 1.0, "F6"), (0, 3.0, 1.0, "D6"),
        (1, 0.0, 1.5, "D6"), (1, 1.5, 0.5, "C6"), (1, 2.0, 1.0, "A5"), (1, 3.0, 1.0, "F5"),
        (2, 0.0, 0.5, "A5"), (2, 0.5, 0.5, "Bb5"), (2, 1.0, 1.0, "C6"), (2, 2.0, 2.0, "F6"),
        (3, 0.0, 1.0, "E6"), (3, 1.0, 1.0, "D6"), (3, 2.0, 1.0, "C6"), (3, 3.0, 1.0, "A5"),
    ]
    B = []
    for b, notes in enumerate((
        ["D5", "F5", "A5", "F5", "D5", "F5", "A5", "C6"],
        ["Bb4", "D5", "F5", "D5", "Bb4", "D5", "F5", "A5"],
        ["F4", "A4", "C5", "A4", "F4", "A4", "C5", "E5"],
        ["C5", "E5", "G5", "E5", "C5", "E5", "G5", "Bb5"],
    )):
        for e, note in enumerate(notes):
            B.append((b, e * 0.5, 0.5, note))
    for b, beat, dur, note in A:
        m.lead(m.at(2 + b, beat), m.beat * dur * 0.92, note, amp=0.155,
               wave="tri", lp=3400, decay=m.beat * dur * 0.85)
    for b, beat, dur, note in A2:
        m.lead(m.at(6 + b, beat), m.beat * dur * 0.92, note, amp=0.145,
               wave="square", lp=3000, decay=m.beat * dur * 0.85)
    for b, beat, dur, note in B:
        m.lead(m.at(10 + b, beat), m.beat * dur * 0.88, note, amp=0.125,
               wave="saw", lp=2600, decay=m.beat * dur * 0.7)
    for b, beat, dur, note in A:
        m.lead(m.at(14 + b, beat), m.beat * dur * 0.92, note, amp=0.150,
               wave="tri", lp=3400, decay=m.beat * dur * 0.85)
    add_reverb(m.buf, m.rate, wet=0.24)
    return m


def song_boss():
    """110 BPM / 30.5s / E 弗里几亚 / i - bII - i - V。高压紧张。"""
    m = Music("music_boss", bpm=110.0, bars=14, tail=1.3, pad_lp=1600)
    prog = [["E2", "G2", "B2"], ["F2", "A2", "C3"], ["E2", "G2", "B2"], ["B2", "D3", "F3"]]
    roots = ["E2", "F2", "E2", "B2"]

    def chord_at(bar):
        return prog[(bar - 2) % 4] if bar >= 2 else prog[bar % 4]

    m.drone(0.0, m.total, [41.20, 82.41], amp=0.10, trem=0.25, trem_hz=2.0)
    for bar in range(14):
        m.pad_chord(m.at(bar), m.bar * 1.0, chord_at(bar), amp=0.085,
                    attack=0.10 if bar >= 2 else 0.6)
        root = roots[(bar - 2) % 4] if bar >= 2 else roots[bar % 4]
        for e in range(16):
            amp = 0.22 if e % 4 == 0 else 0.13
            note = root if e % 8 < 4 else note_hz(root) * 2.0
            m.bass(m.at(bar, e * 0.25), m.beat * 0.20, note, amp=amp, lp=600, decay=0.10)
        if bar >= 2:
            for beat in (0.0, 2.0):
                m.stab(m.at(bar, beat), 0.34, chord_at(bar), amp=0.11, lp=1700)
        if bar < 2:
            m.kick(m.at(bar, 0.0), 0.5)
            m.kick(m.at(bar, 2.0), 0.45)
            m.tom(m.at(bar, 3.0), "E2", 0.20)
        else:
            for beat in (0.0, 0.75, 1.5, 2.5, 3.25):
                m.kick(m.at(bar, beat), 0.52 if beat in (0.0, 2.5) else 0.34)
            m.snare(m.at(bar, 2.0), 0.36)
            m.snare(m.at(bar, 3.5), 0.30)
            for e in range(8):
                m.hat(m.at(bar, e * 0.5), 0.085 if e % 2 else 0.05)
            if bar in (2, 6, 10):
                m.crash(m.at(bar, 0.0), 0.085)
                m.tom(m.at(bar + 1, 3.75), "E2", 0.22)
    A = [
        (0, 0.0, 1.0, "E5"), (0, 1.0, 0.5, "F5"), (0, 1.5, 0.5, "E5"),
        (0, 2.0, 1.0, "D5"), (0, 3.0, 1.0, "B4"),
        (1, 0.0, 2.0, "F5"), (1, 2.0, 1.0, "E5"), (1, 3.0, 1.0, "F5"),
        (2, 0.0, 1.0, "E5"), (2, 1.0, 1.0, "G5"), (2, 2.0, 1.0, "F5"), (2, 3.0, 1.0, "E5"),
        (3, 0.0, 1.0, "B4"), (3, 1.0, 1.0, "D5"), (3, 2.0, 1.0, "F5"), (3, 3.0, 1.0, "E5"),
    ]
    B = [
        (0, 0.0, 1.0, "E6"), (0, 1.0, 1.0, "D6"), (0, 2.0, 2.0, "B5"),
        (1, 0.0, 2.0, "C6"), (1, 2.0, 1.0, "B5"), (1, 3.0, 1.0, "A5"),
        (2, 0.0, 1.0, "G5"), (2, 1.0, 1.0, "A5"), (2, 2.0, 2.0, "B5"),
        (3, 0.0, 1.0, "F5"), (3, 1.0, 1.0, "D5"), (3, 2.0, 2.0, "B4"),
    ]
    for base in (2, 10):
        for b, beat, dur, note in A:
            m.lead(m.at(base + b, beat), m.beat * dur * 0.92, note, amp=0.155,
                   wave="square", lp=2600, decay=m.beat * dur * 0.8)
    for b, beat, dur, note in B:
        m.lead(m.at(6 + b, beat), m.beat * dur * 0.92, note, amp=0.145,
               wave="saw", lp=2800, decay=m.beat * dur * 0.8)
    add_reverb(m.buf, m.rate, wet=0.30)
    return m


MUSIC_SPEC = [
    ("music_menu", song_menu, 8, 60.0),
    ("music_combat_0", song_combat_0, 14, 100.0),
    ("music_combat_1", song_combat_1, 18, 130.0),
    ("music_boss", song_boss, 14, 110.0),
]


def find_ffmpeg():
    """返回可用的 ffmpeg 路径（用于音乐 OGG 编码），没有则 None。"""
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    for cand in (r"C:\ffmpeg\bin\ffmpeg.exe",
                 r"C:\Program Files\ffmpeg\bin\ffmpeg.exe"):
        if Path(cand).exists():
            return cand
    return None


def encode_ogg(wav_path, ogg_path, ffmpeg, quality=4):
    """16-bit WAV -> OGG Vorbis。失败返回 None（调用方回落 WAV）。"""
    try:
        r = subprocess.run(
            [ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
             "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", str(quality),
             str(ogg_path)],
            capture_output=True, timeout=180)
        if r.returncode == 0 and ogg_path.exists() and ogg_path.stat().st_size > 1024:
            return ogg_path
        if r.stderr:
            print("    ffmpeg 警告: " + r.stderr.decode("utf-8", "replace").strip()[:200])
    except Exception as exc:
        print("    ffmpeg 编码失败: %s" % exc)
    return None


def build_music(music_target_lufs):
    """渲染 4 首循环曲。

    v2.0:
      * 响度按 LUFS 对齐到 music_target_lufs（= 音效均值 - 5 dB），而不是只压峰值。
      * 输出 OGG（体积约为 WAV 的 1/8），没有 ffmpeg 时回落 WAV。
    """
    results = []
    ffmpeg = find_ffmpeg()
    TMP.mkdir(parents=True, exist_ok=True)
    for name, fn, bars, bpm in MUSIC_SPEC:
        m = fn()
        data = m.finish(fade_ms=20.0)
        # 先对响度，再确保峰值不超过 -3 dBFS
        data, lufs_before, lufs_after, peak_db = loudness_match(
            data, m.rate, music_target_lufs, PEAK_CEILING_DB)
        dur = len(data) / m.rate
        seam = m.loop_seam(data)

        wav_path = TMP / (name + ".wav")
        if MUSIC_CHANNELS == 2:
            delay = int(0.012 * m.rate)
            stereo = []
            for i in range(len(data)):
                l = data[i] + (data[i - delay] * 0.18 if i >= delay else 0.0)
                r = data[i] + (data[i - delay * 2] * 0.14 if i >= delay * 2 else 0.0)
                stereo.append((l, r))
            write_wav(wav_path, stereo, m.rate, 2)
        else:
            write_wav(wav_path, data, m.rate, 1)

        path, codec = None, "wav"
        if ffmpeg:
            ogg_path = OUT / (name + ".ogg")
            if encode_ogg(wav_path, ogg_path, ffmpeg, quality=4):
                path, codec = ogg_path, "ogg"
        if path is None:
            path = OUT / (name + ".wav")
            shutil.copyfile(wav_path, path)
        size = path.stat().st_size
        results.append(dict(name=name, dur=dur, path=path, size=size, bpm=bpm,
                            bars=bars, seam=seam, lufs=lufs_after,
                            lufs_raw=lufs_before, peak_db=peak_db, codec=codec))
    shutil.rmtree(TMP, ignore_errors=True)
    return results


# ═══════════════════════════════════════════════════════════════════
#  自检 + 报告
# ═══════════════════════════════════════════════════════════════════

def read_wav(path):
    with wave.open(str(path), "rb") as w:
        rate = w.getframerate()
        ch = w.getnchannels()
        width = w.getsampwidth()
        frames = w.getnframes()
        raw = w.readframes(frames)
    count = frames * ch
    vals = struct.unpack("<%dh" % count, raw)
    peak = max(abs(v) for v in vals) / 32768.0
    rms = math.sqrt(sum(v * v for v in vals) / count) / 32768.0
    clipped = sum(1 for v in vals if abs(v) >= 32767)
    return dict(rate=rate, channels=ch, width=width, frames=frames,
                duration=frames / rate, peak=peak, rms=rms, clipped=clipped,
                samples=vals, size=path.stat().st_size)


def db(x):
    return 20.0 * math.log10(max(x, 1e-9))


def png_chunk(tag, data):
    body = tag + data
    return (struct.pack(">I", len(data)) + body
            + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF))


def write_wave_png(path, tracks, width=1280, row_h=140):
    """把多条波形画到一张 PNG（纯 zlib，无第三方库）。"""
    bg = (11, 20, 27)
    grid = (28, 46, 56)
    colors = [(157, 231, 193), (234, 196, 135), (241, 143, 139),
              (194, 167, 255), (147, 196, 255)]
    height = row_h * len(tracks)
    rows = [bytearray(bytes(bg) * width) for _ in range(height)]
    for y in range(0, height, row_h):
        rows[y] = bytearray(bytes(grid) * width)

    for ti, (name, vals, rate) in enumerate(tracks):
        top = ti * row_h
        mid = top + row_h // 2
        half = row_h // 2 - 8
        color = bytes(colors[ti % len(colors)])
        step = max(1, len(vals) // width)
        for x in range(width):
            chunk = vals[x * step:(x + 1) * step]
            if not chunk:
                break
            lo = min(chunk) / 32768.0
            hi = max(chunk) / 32768.0
            y0 = max(top + 2, min(top + row_h - 3, mid - int(hi * half)))
            y1 = max(top + 2, min(top + row_h - 3, mid - int(lo * half)))
            for y in range(y0, y1 + 1):
                off = x * 3
                rows[y][off:off + 3] = color

    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
           + png_chunk(b"IDAT", zlib.compress(raw, 6))
           + png_chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main():
    args = set(sys.argv[1:])
    do_sfx = "--music" not in args
    do_music = "--sfx" not in args

    OUT.mkdir(parents=True, exist_ok=True)

    # 期望产物清单：本次要生成的音效 + 音乐（后缀由编码器决定）
    expected_sfx = {name for name, _, _, _ in SFX_SPEC}
    expected_music = {name for name, _, _, _ in MUSIC_SPEC}
    keep = set()
    if do_sfx:
        keep |= {n + ".wav" for n in expected_sfx}
    if do_music:
        keep |= {n + ".ogg" for n in expected_music} | {n + ".wav" for n in expected_music}

    # 清理 raw/ 里所有不属于本次产物的音频：
    #  * v0.3 遗留 fx_*/music_expedition
    #  * 上一轮生成的 music_*.wav（现在改出 OGG，留着会与 .ogg 撞同名资源）
    #  * 已废弃/改名的音效
    dropped = []
    for p in sorted(OUT.iterdir()):
        if p.suffix.lower() in (".wav", ".ogg") and p.name not in keep:
            p.unlink()
            dropped.append(p.name)
    if dropped:
        print("清理过期音频: %d 个 (%s)" % (len(dropped), ", ".join(dropped)))

    sfx_rows, mus_rows = [], []
    if do_sfx:
        print("\n[1/2] 生成 %d 个音效 (44100 Hz / 16-bit / mono, LUFS 对齐) ..." % len(SFX_SPEC))
        for r in build_sfx():
            sfx_rows.append(r)
            print("  %-15s %6.3fs %6.1f KB  peak %6.1f dBFS  rms %6.1f dBFS  "
                  "LUFS %6.1f (target %6.1f)"
                  % (r["name"], r["dur"], r["size"] / 1024.0, r["peak_db"],
                     r["rms_db"], r["lufs"], r["target"]))

    # 音乐响度目标 = 音效均值 - 5 dB
    if sfx_rows:
        sfx_mean = sum(r["lufs"] for r in sfx_rows) / len(sfx_rows)
    else:
        sfx_mean = SFX_MEAN_FALLBACK
    music_target = sfx_mean - MUSIC_BELOW_SFX_DB

    if do_music:
        print("\n[2/2] 生成 %d 首循环音乐 (22050 Hz, 目标 LUFS %.1f = 音效均值 %.1f - %.1f dB) ..."
              % (len(MUSIC_SPEC), music_target, sfx_mean, MUSIC_BELOW_SFX_DB))
        for r in build_music(music_target):
            mus_rows.append(r)
            print("  %-15s %6.2fs %6.2f MB  %5.0f BPM  %s  peak %6.1f dBFS  "
                  "LUFS %6.1f  loop %5.3f/%5.3f"
                  % (r["name"], r["dur"], r["size"] / 1048576.0, r["bpm"],
                     r["codec"].upper(), r["peak_db"], r["lufs"],
                     r["seam"][0], r["seam"][1]))

    # ── 自检 ──
    print("\n自检:")
    problems = []
    total_bytes = 0
    for r in sfx_rows:
        info = read_wav(r["path"])
        total_bytes += r["size"]
        if info["width"] != 2:
            problems.append("%s 不是 16-bit" % r["name"])
        if info["clipped"] > 0:
            problems.append("%s 有 %d 个满幅采样" % (r["name"], info["clipped"]))
        if info["peak"] >= 0.999:
            problems.append("%s 削波 peak=%.4f" % (r["name"], info["peak"]))
        if info["rms"] <= 0.01:
            problems.append("%s 近似静音 rms=%.5f" % (r["name"], info["rms"]))
        if r["peak_db"] > PEAK_CEILING_DB + 0.05:
            problems.append("%s 峰值 %.1f 超过上限 %.1f"
                            % (r["name"], r["peak_db"], PEAK_CEILING_DB))

    for r in mus_rows:
        total_bytes += r["size"]
        if r["peak_db"] > PEAK_CEILING_DB + 0.05:
            problems.append("%s 峰值 %.1f 超过上限 %.1f"
                            % (r["name"], r["peak_db"], PEAK_CEILING_DB))
        if not (30.0 <= r["dur"] <= 45.0):
            problems.append("%s 时长 %.1fs 超出 30-45s" % (r["name"], r["dur"]))
        if r["seam"][0] > 0.01 or r["seam"][1] > 0.01:
            problems.append("%s 循环接缝 %.3f/%.3f 可能有爆音" % (r["name"], r["seam"][0], r["seam"][1]))

    # 响度平衡检查
    if sfx_rows:
        lo = min(r["lufs"] for r in sfx_rows)
        hi = max(r["lufs"] for r in sfx_rows)
        spread = hi - lo
        print("  音效 LUFS: %.1f .. %.1f，离散度 %.1f dB（上限 %.1f）"
              % (lo, hi, spread, SFX_SPREAD_LIMIT))
        if spread > SFX_SPREAD_LIMIT:
            problems.append("音效响度离散度 %.1f dB 超过 %.1f dB" % (spread, SFX_SPREAD_LIMIT))
        if mus_rows:
            mus_mean = sum(r["lufs"] for r in mus_rows) / len(mus_rows)
            delta = sfx_mean - mus_mean
            print("  音乐均值 LUFS %.1f，比音效均值低 %.1f dB（目标 4-6 dB）"
                  % (mus_mean, delta))
            if not (4.0 <= delta <= 6.0):
                problems.append("音乐与音效响度差 %.1f dB 不在 4-6 dB 区间" % delta)

    if total_bytes > SIZE_LIMIT:
        problems.append("总体积 %.2f MB 超过 %.1f MB 限制"
                        % (total_bytes / 1048576.0, SIZE_LIMIT / 1048576.0))

    if problems:
        print("  x 发现问题:")
        for p in problems:
            print("    - " + p)
    else:
        print("  ok: %d 个文件 16-bit / 无削波 / 非静音 / 峰值 <= %.0f dBFS / "
              "响度平衡 / 体积达标" % (len(sfx_rows) + len(mus_rows), PEAK_CEILING_DB))

    print("\n合计: %d 个文件, %.2f MB (%.1f KB) / 限制 %.1f MB"
          % (len(sfx_rows) + len(mus_rows), total_bytes / 1048576.0,
             total_bytes / 1024.0, SIZE_LIMIT / 1048576.0))

    # ── 混音平衡表（改前 vs 改后） ──
    if sfx_rows:
        print("\n混音平衡表 (LUFS):")
        print("  %-16s %8s %8s" % ("name", "target", "actual"))
        for r in sfx_rows:
            print("  %-16s %8.1f %8.1f" % (r["name"], r["target"], r["lufs"]))
        print("  %-16s %8s %8.1f" % ("SFX mean", "", sfx_mean))
        for r in mus_rows:
            print("  %-16s %8.1f %8.1f" % (r["name"], music_target, r["lufs"]))

    QA.mkdir(parents=True, exist_ok=True)
    if do_music and mus_rows and "--no-plot" not in args:
        tracks = []
        for r in mus_rows:
            # OGG 需要解码后才能画波形；没有 ffmpeg 就跳过热噪声这行
            decoded = decode_for_plot(r["path"])
            if decoded:
                tracks.append((r["name"], decoded[1], decoded[0]))
        concat = []
        for r in sfx_rows:
            info = read_wav(r["path"])
            concat.extend(info["samples"])
        if concat:
            tracks.append(("sfx_all", concat, SFX_RATE))
        if tracks:
            png = QA / "audio-wave.png"
            write_wave_png(png, tracks)
            print("\n波形图: %s (%.1f KB)" % (png, png.stat().st_size / 1024.0))
            print("行序: " + ", ".join(t[0] for t in tracks))

    return 0 if not problems else 1


def decode_for_plot(path):
    """把任意音频解成 (rate, int16 采样) 供画图；失败返回 None。"""
    if path.suffix.lower() == ".wav":
        info = read_wav(path)
        return info["rate"], info["samples"]
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        return None
    TMP.mkdir(parents=True, exist_ok=True)
    tmp = TMP / (path.stem + "_plot.wav")
    try:
        r = subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                            "-i", str(path), "-c:a", "pcm_s16le", str(tmp)],
                           capture_output=True, timeout=120)
        if r.returncode != 0 or not tmp.exists():
            return None
        info = read_wav(tmp)
        return info["rate"], info["samples"]
    except Exception:
        return None
    finally:
        tmp.unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())
