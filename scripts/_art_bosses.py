"""8 首领 —— 撑满 160 单元格，威压感优先。

顺序同 Bosses.NAMES 0..7：
0 苔冠守望者(苔光) 1 赤炉铸造者(赤铜) 2 月蚀档案官(月影) 3 霜心女猎手(霜蚀)
4 熔核暴君(熔火) 5 虚空执政官(虚空) 6 双生回响(通用双体) 7 终焉·深渊之心

设计要点：多眼 / 巨角 / 破碎王冠 / 悬浮核心 / 机械翼 / 晶体躯干；
4 帧有明显动作（武器抬起 / 核心脉冲 / 翅膀展开）。
"""
import math
from _art_core import Rig, C, sh, mixc


def _pupil(r, x, y, rad, col='#0b0f16'):
    r.ell((x - rad, y - rad, x + rad, y + rad), col)
    r.emit(x - rad * .3, y - rad * .34, rad * .32, '#ffffff', glow=False, a=230)


def _eye(r, x, y, rad, col, a=255):
    r.emit(x, y, rad, col, glow=True, a=a)


# ── 0 苔冠守望者：巨石躯干 + 苔冠巨角 + 多眼 ───────────────────
def b_moss(t):
    r = Rig(3000)
    y = (0.0, 1.2, -0.48, -1.6)[t & 3]
    pulse = (0.0, .6, 0.27, 1.0)[t & 3]
    c = '#6a7a64'
    # 石足
    for bx, m in ((56, .66), (100, .98)):
        r.solid([(bx - 14, 104), (bx + 14, 104), (bx + 13, 132), (bx + 16, 142),
                 (bx - 16, 142), (bx - 13, 132)], sh(c, m), bevel=3.0, top=1.3, dark=.55)
    # 巨石躯干（撑满单元格宽度）
    r.solid([(22, 60 + y), (52, 26 + y), (108, 24 + y), (138, 58 + y),
             (136, 100 + y), (110, 118 + y), (50, 118 + y), (20, 98 + y)],
            c, bevel=4.5, top=1.26, dark=.50)
    # 岩块分面
    for i, (bx, by, bw, bh) in enumerate(((34, 50, 30, 22), (68, 42, 34, 20),
                                          (36, 78, 28, 22), (70, 74, 36, 24),
                                          (108, 56, 26, 26))):
        r.solid([(bx, by + y), (bx + bw, by - 3 + y), (bx + bw + 3, by + bh + y),
                 (bx - 3, by + bh + 3 + y)], sh(c, 1.14 + i * .05),
                bevel=2.4, top=1.4, dark=.5)
    # 苔藓（顶部绿色）
    for bx, bw in ((36, 34), (78, 40), (116, 22)):
        r.solid([(bx, 30 + y), (bx + bw, 26 + y), (bx + bw - 4, 40 + y), (bx + 2, 44 + y)],
                '#4a7a52', bevel=2.4, top=1.42, dark=.5)
    # 苔须下垂
    for i in range(7):
        bx = 34 + i * 15
        r.line([(bx, 42 + y), (bx + 2, 58 + y), (bx - 1, 70 + y)], '#3a5c42', 2.2)
    # 胸口发光核心
    r.emit(80, 78 + y, 4.4 + pulse, '#a8f0a0', glow=True, a=225)
    r.ring(80, 78 + y, 11, C('#a8f0a0', 160), 1.8)
    # 巨角冠
    r.solid([(50, 30 + y), (34, -8 + y), (46, -2 + y), (58, 28 + y)], '#c8c0a0',
            bevel=2.6, top=1.5, dark=.55)
    r.solid([(110, 28 + y), (126, -10 + y), (114, -4 + y), (102, 26 + y)], '#c8c0a0',
            bevel=2.6, top=1.5, dark=.55)
    r.solid([(74, 20 + y), (80, -14 + y), (88, 20 + y)], '#d8d0b0',
            bevel=2.4, top=1.5, dark=.55)
    # 头：多眼 + 石面
    r.solid([(58, 34 + y), (106, 32 + y), (112, 60 + y), (104, 78 + y),
             (58, 78 + y), (52, 58 + y)], sh(c, 1.04), bevel=3.4, top=1.32, dark=.5)
    for (ex, ey, er) in ((70, 52, 5.4), (90, 52, 5.4), (80, 64, 4.4)):
        _eye(r, ex, ey + y, er, '#d8ffa0')
        _pupil(r, ex, ey + y, er * .42)
    r.line([(60, 46 + y), (104, 44 + y)], sh(c, 1.4), 2.4)
    # 藤蔓缠绕
    for s in (-1, 1):
        r.line([(80 + s * 40, 100 + y), (80 + s * 50, 80 + y), (80 + s * 44, 60 + y)],
               '#3a5c42', 3.0)
    return r.final(rim=('#c8f0b0', .46), shadow=(80, 145, 58, 150), grain=.08, glow_blur=5.5)


# ── 1 赤炉铸造者：熔炉巨躯 + 烟囱 + 巨锤 ──────────────────────
def b_forge(t):
    r = Rig(3001)
    y = (0.0, 1.2, -0.48, -1.4)[t & 3]
    swell = (0.0, 1.4, 0.0, .4)[t & 3]
    c = '#8a5a3c'
    for bx, m in ((58, .66), (100, .96)):
        r.solid([(bx - 15, 104), (bx + 15, 104), (bx + 14, 132), (bx + 17, 142),
                 (bx - 17, 142), (bx - 14, 132)], sh(c, m), bevel=3.2, top=1.3, dark=.55)
    # 熔炉躯干
    r.solid([(28, 54 + y), (56, 28 + y), (104, 26 + y), (134, 52 + y),
             (132, 102 + y), (104, 120 + y), (52, 120 + y), (26, 100 + y)],
            c, bevel=4.5, top=1.26, dark=.5)
    # 炉门（发光）
    r.solid([(58, 62 + y), (104, 60 + y), (108, 100 + y), (54, 102 + y)],
            '#3a2a22', bevel=3.4, top=1.3, dark=.55)
    r.gell((64, 68 + swell + y, 100, 98 - swell * .4 + y), '#ff9a4a', .1, .4)
    r.emit(82, 82 + y, 4.6 + swell, '#ffb060', glow=True, a=225)
    for i in range(3):
        r.line([(62, 76 + i * 9 + y), (100, 75 + i * 9 + y)], C('#ffd08a', 200), 1.6)
    # 炉门框铆钉
    for i in range(6):
        px = 58 + i * 9
        r.ell((px - 1.8, 60 + y, px + 1.8, 63.6 + y), '#c8a870')
        r.ell((px - 1.8, 100 + y, px + 1.8, 103.6 + y), '#c8a870')
    # 双烟囱
    for bx in (64, 96):
        r.solid([(bx - 7, 30 + y), (bx + 7, 30 + y), (bx + 6, -6 + y), (bx - 6, -6 + y)],
                sh(c, .78), bevel=2.6, top=1.36, dark=.55)
        r.emit(bx, -8 + y, 4.4, '#ff9a4a', glow=True, a=180)
        for k in range(2):
            r.emit(bx + math.sin(t * .8 + k) * 6, -16 - k * 8 + y, 3.2, '#7a6a62', glow=True, a=110)
    # 头：铁面具 + 多眼
    r.solid([(52, 20 + y), (108, 18 + y), (114, 44 + y), (106, 62 + y),
             (54, 62 + y), (46, 44 + y)], '#5a4436', bevel=3.6, top=1.34, dark=.5)
    r.solid([(58, 30 + y), (104, 28 + y), (103, 48 + y), (59, 50 + y)], '#161012',
            bevel=2.4, top=1.6, dark=.6)
    for (ex, er) in ((68, 4.6), (82, 4.6), (96, 4.6)):
        _eye(r, ex, 39 + y, er, '#ff7a3a')
        _pupil(r, ex, 39 + y, er * .4)
    r.solid([(60, 8 + y), (70, -10 + y), (78, 8 + y)], '#9a7a54', bevel=2.0, top=1.5, dark=.55)
    r.solid([(84, 8 + y), (94, -12 + y), (102, 8 + y)], '#9a7a54', bevel=2.0, top=1.5, dark=.55)
    # 巨锤（抬起动作随帧变化）
    lift = (0.0, -8.0, 0.0, -14.0)[t & 3]
    hx = 128
    r.line([(hx, 60 + y), (hx + 14, 30 + y + lift)], '#4a3624', 6.0)
    r.solid([(hx + 4, 20 + y + lift), (hx + 32, 12 + y + lift),
             (hx + 36, 34 + y + lift), (hx + 8, 42 + y + lift)], '#6a6a70',
            bevel=3.2, top=1.4, dark=.5)
    r.emit(hx + 20, 26 + y + lift, 6.0, '#ff9a4a', glow=True, a=190)
    return r.final(rim=('#ffc090', .48), shadow=(80, 145, 60, 155), grain=.08, glow_blur=5.5)


# ── 2 月蚀档案官：悬浮书页 + 环 + 面具 ────────────────────────
def b_archive(t):
    r = Rig(3002)
    y = (0.0, 1.6, -0.64, -2.2)[t & 3]
    c = '#5a5a8a'
    # 悬浮书页环（多页翻飞）
    for i in range(9):
        a = i * .698 + t * .12
        px = 80 + math.cos(a) * 58
        py = 74 + y + math.sin(a) * 38
        w = 10 + (i % 3) * 3
        r.solid([(px - w, py - 7), (px + w, py - 9), (px + w + 1, py + 7), (px - w + 1, py + 8)],
                '#c8c8e8' if i % 2 else '#e8e8f8', bevel=1.6, top=1.4, dark=.55)
        r.line([(px - w + 1, py - 1), (px + w - 1, py - 2)], '#8a8ab8', 1.0)
    # 长袍本体
    r.solid([(44, 54 + y), (116, 52 + y), (128, 120 + y * .3), (110, 142),
             (82, 132), (54, 142), (32, 120)], sh(c, .66), bevel=3.8, top=1.28, dark=.5)
    r.poly([(56, 58 + y), (104, 56 + y), (112, 118), (96, 128), (82, 118), (54, 126),
            (44, 112)], sh(c, .84))
    # 胸口月蚀纹
    r.emit(80, 84 + y, 4.2, '#c8c8ff', glow=True, a=225)
    r.ring(80, 84 + y, 14, C('#a8a8e8', 190), 1.8)
    r.circle = None
    # 大面具（多眼）
    r.solid([(52, 22 + y), (108, 20 + y), (116, 44 + y), (106, 66 + y),
             (54, 66 + y), (44, 44 + y)], '#2a2a48', bevel=3.6, top=1.34, dark=.5)
    r.solid([(58, 30 + y), (104, 28 + y), (102, 56 + y), (60, 58 + y)], '#0e0e1a',
            bevel=2.2, top=1.6, dark=.6)
    for i in range(4):
        _eye(r, 66 + i * 10, 40 + (i % 2) * 8 + y, 3.6, '#c8c8ff')
    # 尖冠
    for i in range(3):
        r.solid([(62 + i * 18, 22 + y), (66 + i * 18, 0 + y - (i % 2) * 6),
                 (72 + i * 18, 22 + y)], '#7a7ab8', bevel=1.8, top=1.5, dark=.55)
    # 悬浮钥匙
    r.line([(112, 90 + y), (122, 112 + y)], '#d8c890', 3.0)
    r.ring(114, 88 + y, 6, '#e8d8a8', 2.0)
    return r.final(rim=('#d0d0ff', .5), shadow=(80, 145, 54, 140), grain=.05, glow_blur=5.5)


# ── 3 霜心女猎手：细长身形 + 冰弓 + 披肩冰晶 ─────────────────
def b_huntress(t):
    r = Rig(3003)
    y = (0.0, 1.2, -0.48, -2.0)[t & 3]
    c = '#6a94b8'
    # 冰披肩
    for i in range(6):
        bx = 40 + i * 16
        r.solid([(bx, 46 + y), (bx + 6, 22 + y - (i % 2) * 8), (bx + 12, 46 + y)],
                C('#b8dcf4', 235), bevel=1.8, top=1.6, dark=.55)
    # 腿（细长）
    for bx, m in ((68, .72), (94, .96)):
        r.solid([(bx - 8, 92), (bx + 8, 92), (bx + 7, 130), (bx + 10, 142),
                 (bx - 10, 142), (bx - 7, 130)], sh(c, m), bevel=2.6, top=1.3, dark=.55)
    # 躯干
    r.solid([(50, 44 + y), (110, 42 + y), (114, 94 + y), (100, 106 + y),
             (60, 106 + y), (46, 92 + y)], c, bevel=3.6, top=1.3, dark=.52)
    r.poly([(60, 48 + y), (102, 46 + y), (106, 92 + y), (92, 100 + y), (80, 94 + y),
            (64, 100 + y), (52, 90 + y)], sh(c, 1.18))
    # 胸口霜心
    r.solid([(80, 56 + y), (92, 74 + y), (80, 94 + y), (68, 74 + y)],
            C('#d8f0ff', 250), bevel=2.4, top=1.5, dark=.6)
    r.emit(80, 74 + y, 4.0, '#d8f0ff', glow=True, a=225)
    # 头 + 兜帽
    r.solid([(64, 14 + y), (98, 12 + y), (104, 36 + y), (98, 56 + y),
             (66, 56 + y), (58, 36 + y)], sh(c, 1.06), bevel=3.2, top=1.34, dark=.5)
    r.solid([(68, 26 + y), (94, 24 + y), (92, 48 + y), (70, 48 + y)], '#0e1e2a',
            bevel=2.2, top=1.6, dark=.6)
    _eye(r, 76, 35 + y, 3.6, '#a8f0ff')
    _eye(r, 88, 35 + y, 3.6, '#a8f0ff')
    # 冰冠
    for i in range(5):
        r.poly([(64 + i * 10, 14 + y), (67 + i * 10, -10 + y - (i % 2) * 8),
                (72 + i * 10, 14 + y)], C('#c8ecff', 240))
    # 巨冰弓（拉弓动作）
    pull = (0.0, 3.0, 0.0, -3.0)[t & 3]
    bx = 122
    r.line([(bx + 6, 18), (bx - 8, 78), (bx + 6, 138)], C('#b8dcf4', 240), 6.0)
    r.line([(bx + 3, 22), (bx - 12, 78), (bx + 3, 134)], C('#e8f8ff', 230), 2.4)
    r.line([(bx + 6, 20), (bx + 2 - pull, 78), (bx + 6, 136)], '#f4fcff', 1.4)
    r.line([(bx + 2 - pull, 78), (bx + 40, 76)], C('#d8f0ff', 235), 2.0)
    r.poly([(bx + 40, 76), (bx + 32, 72), (bx + 32, 81)], C('#e8f8ff', 245))
    # 冰刺肩甲
    for s in (-1, 1):
        r.solid([(80 + s * 34, 44 + y), (80 + s * 50, 30 + y),
                 (80 + s * 46, 54 + y)], C('#a8d0ec', 235), bevel=2.0, top=1.5, dark=.55)
    return r.final(rim=('#e8f8ff', .52), shadow=(80, 145, 52, 140), grain=.05, glow_blur=5.5)


# ── 4 熔核暴君：巨型熔岩躯 + 悬浮熔核 + 破碎王冠 ──────────────
def b_tyrant(t):
    r = Rig(3004)
    y = (0.0, 1.4, -0.56, -1.8)[t & 3]
    pulse = (0.0, 1.0, 0.45, 2.0)[t & 3]
    c = '#6a3a2e'
    for bx, m in ((56, .66), (100, .98)):
        r.solid([(bx - 16, 104), (bx + 16, 104), (bx + 15, 132), (bx + 18, 142),
                 (bx - 18, 142), (bx - 15, 132)], sh(c, m), bevel=3.2, top=1.28, dark=.55)
    # 巨型躯干
    r.solid([(24, 56 + y), (54, 26 + y), (106, 24 + y), (138, 54 + y),
             (136, 104 + y), (106, 122 + y), (50, 122 + y), (22, 102 + y)],
            c, bevel=4.5, top=1.26, dark=.5)
    # 熔岩裂纹
    for pts in (((42, 52, 52, 84, 46, 108), (74, 44, 82, 76, 76, 102),
                 (106, 56, 112, 86, 104, 110))):
        r.emit_line([(pts[0], pts[1] + y), (pts[2], pts[3] + y), (pts[4], pts[5] + y)],
                    '#ff8a4a', 2.6, a=240)
    # 悬浮熔核
    r.gell((62, 66 + y, 100, 104 + y), '#ffb060', .1, .45)
    r.emit(81, 85 + y, 5.2 + pulse, '#ffd08a', glow=True, a=225)
    r.ring(81, 85 + y, 14 + pulse, C('#ffb060', 175), 2.0)
    # 头 + 破碎王冠
    r.solid([(56, 20 + y), (106, 18 + y), (112, 44 + y), (104, 62 + y),
             (58, 62 + y), (50, 44 + y)], sh(c, 1.06), bevel=3.6, top=1.32, dark=.5)
    r.solid([(62, 30 + y), (102, 28 + y), (100, 50 + y), (64, 52 + y)], '#160c0a',
            bevel=2.4, top=1.6, dark=.6)
    for (ex, er) in ((70, 5.0), (82, 5.0), (94, 5.0)):
        _eye(r, ex, 39 + y, er, '#ff7a3a')
        _pupil(r, ex, 39 + y, er * .4)
    # 破碎王冠（不规则尖，缺口）
    crown = [(54, 20 + y), (58, -6 + y), (66, 12 + y), (70, -12 + y), (78, 8 + y),
             (82, -2 + y), (88, 12 + y), (94, -10 + y), (100, 10 + y), (106, 20 + y)]
    r.solid(crown, '#c8a860', bevel=2.8, top=1.48, dark=.55)
    for i in range(4):
        r.ell((60 + i * 12, 6 + y - (i % 2) * 8, 66 + i * 12, 12 + y - (i % 2) * 8),
              '#ff9a5a')
    # 巨臂
    for s in (-1, 1):
        r.solid([(80 + s * 46, 56 + y), (80 + s * 64, 66 + y),
                 (80 + s * 62, 96 + y), (80 + s * 44, 90 + y)],
                sh(c, .92), bevel=3.0, top=1.34, dark=.52)
        r.solid([(80 + s * 60, 92 + y), (80 + s * 70, 104 + y),
                 (80 + s * 56, 112 + y), (80 + s * 50, 100 + y)],
                sh(c, 1.1), bevel=2.4, top=1.4, dark=.55)
    return r.final(rim=('#ffb07a', .52), shadow=(80, 145, 62, 158), grain=.08, glow_blur=5.5)


# ── 5 虚空执政官：悬浮 + 虚空裂隙环 + 高冠 + 多臂 ─────────────
def b_consul(t):
    r = Rig(3005)
    y = (0.0, 1.8, -0.72, -2.6)[t & 3]
    c = '#4a3a70'
    # 虚空裂隙环
    for i in range(10):
        a = i * .628 + t * .18
        r.poly([(80 + math.cos(a) * 46, 74 + y + math.sin(a) * 34),
                (80 + math.cos(a + .3) * 66, 74 + y + math.sin(a + .3) * 48),
                (80 + math.cos(a + .12) * 48, 74 + y + math.sin(a + .12) * 36)],
               C('#7a4ac8', 160))
    # 悬浮长袍（下方飘散）
    r.solid([(48, 46 + y), (112, 44 + y), (122, 100 + y), (108, 134 + y),
             (92, 124 + y), (80, 140 + y), (66, 126 + y), (48, 136 + y), (38, 98 + y)],
            c, bevel=3.8, top=1.28, dark=.48)
    r.poly([(58, 50 + y), (102, 48 + y), (110, 96 + y), (96, 118 + y), (80, 126 + y),
            (64, 116 + y), (50, 94 + y)], sh(c, 1.16))
    # 胸口裂隙
    r.solid([(80, 52 + y), (94, 74 + y), (86, 104 + y), (74, 78 + y)], '#0b0510',
            bevel=2.4, top=1.5, dark=.7)
    r.solid([(80, 58 + y), (90, 74 + y), (86, 98 + y), (77, 78 + y)], '#c8a6ff',
            bevel=1.8, top=1.5, dark=.6)
    r.emit(80, 78 + y, 4.2, '#c8a6ff', glow=True, a=225)
    # 高冠
    r.solid([(58, 18 + y), (102, 16 + y), (106, 34 + y), (56, 34 + y)], '#3a2a5a',
            bevel=2.8, top=1.4, dark=.5)
    for i in range(5):
        r.solid([(58 + i * 11, 18 + y), (62 + i * 11, -14 + y - (i % 2) * 10),
                 (68 + i * 11, 18 + y)], C('#8a6ac8', 240), bevel=1.8, top=1.5, dark=.55)
    # 面部（无脸面具 + 多眼）
    r.solid([(62, 22 + y), (98, 20 + y), (102, 44 + y), (96, 58 + y),
             (64, 58 + y), (58, 44 + y)], '#180e28', bevel=2.8, top=1.4, dark=.5)
    for i in range(4):
        _eye(r, 68 + i * 8, 38 + (i % 2) * 7 + y, 3.0, '#d0a8ff')
    # 多臂（6 臂展开）
    for s in (-1, 1):
        for k, (ang, ln) in enumerate(((0.0, 34), (.5, 44), (1.0, 32))):
            a = math.radians(ang * 30 + (0 if s > 0 else 20))
            r.line([(80 + s * 30, 62 + y), (80 + s * (30 + ln), 62 + y + math.sin(a) * ln * .5)],
                   sh(c, .86), 4.4)
    # 虚空裂刃
    for s in (-1, 1):
        r.solid([(80 + s * 66, 50 + y), (80 + s * 76, 20 + y),
                 (80 + s * 72, 54 + y)], C('#a882ff', 240), bevel=2.0, top=1.6, dark=.55)
    return r.final(rim=('#d8c2ff', .54), shadow=(80, 145, 54, 136), grain=.04, glow_blur=5.5)


# ── 6 双生回响：两个对称的镜像体 ─────────────────────────────
def b_twin(t):
    r = Rig(3006)
    y = (0.0, 1.2, -0.48, -2.0)[t & 3]
    mirror = (0.0, -6.0, 0.0, 6.0)[t & 3]
    for s, base in ((-1, 56), (1, 104)):
        cx = base
        c = '#6a6a9a' if s < 0 else '#8a6a9a'
        # 悬浮体
        off = mirror * s
        r.solid([(cx - 26, 46 + y), (cx + 26, 44 + y), (cx + 32, 96 + y),
                 (cx + 20, 126 + y + off * .3), (cx, 136 + y + off * .5),
                 (cx - 20, 126 + y + off * .3), (cx - 32, 96 + y)],
                c, bevel=3.4, top=1.3, dark=.5)
        r.poly([(cx - 20, 50 + y), (cx + 20, 48 + y), (cx + 26, 94 + y),
                (cx + 14, 116 + y + off * .3), (cx, 124 + y + off * .4),
                (cx - 14, 116 + y + off * .3), (cx - 26, 94 + y)], sh(c, 1.16))
        # 头部
        r.solid([(cx - 18, 14 + y), (cx + 18, 12 + y), (cx + 22, 36 + y),
                 (cx + 14, 50 + y), (cx - 14, 50 + y), (cx - 22, 36 + y)],
                sh(c, 1.06), bevel=3.0, top=1.34, dark=.5)
        r.solid([(cx - 14, 24 + y), (cx + 14, 22 + y), (cx + 12, 42 + y),
                 (cx - 12, 42 + y)], '#12101e', bevel=2.0, top=1.6, dark=.6)
        _eye(r, cx - 6, 32 + y, 4.0, '#e0d0ff')
        _eye(r, cx + 6, 32 + y, 4.0, '#e0d0ff')
        # 胸口核心
        r.emit(cx, 74 + y, 3.2, '#e0d0ff', glow=True, a=225)
        r.ring(cx, 74 + y, 10, C('#c8b0ff', 160), 1.6)
        # 裂刃
        r.solid([(cx + s * 30, 60 + y), (cx + s * 42, 26 + y), (cx + s * 38, 64 + y)],
                C('#b89aff', 235), bevel=2.0, top=1.6, dark=.55)
    # 中间的共鸣连线
    r.emit_line([(62, 74 + y), (80, 68 + y), (98, 74 + y)], '#c8b0ff', 1.8, a=170)
    r.emit(80, 70 + y, 2.4, '#e0d0ff', glow=True, a=190)
    return r.final(rim=('#e0d0ff', .52), shadow=(80, 145, 58, 140), grain=.04, glow_blur=5.5)


# ── 7 终焉·深渊之心：巨大心脏 + 触须 + 多眼环 ────────────────
def b_heart(t):
    r = Rig(3007)
    y = (0.0, 1.6, -0.64, -2.4)[t & 3]
    beat = (0.0, 1.6, 0.0, 3.2)[t & 3]
    c = '#4a2a44'
    # 外围触须（8 条，摆动）
    for i in range(8):
        a = i * .7854
        wob = math.sin(t * 1.2 + i) * 6
        bx = 80 + math.cos(a) * 50
        by = 78 + y + math.sin(a) * 42
        r.line([(80 + math.cos(a) * 30, 78 + y + math.sin(a) * 26),
                (bx + wob * .5, by), (bx + math.cos(a) * 22 + wob, by + math.sin(a) * 20)],
               sh(c, .72 + (i % 3) * .08), 5.0)
    # 巨大心脏主体
    r.gell((26, 24 + y - beat, 134, 132 + y), '#5a3050', 1.2, .42)
    r.gell((36, 32 + y - beat, 124, 124 + y), '#7a3e60', 1.24, .46)
    # 血管/裂口
    for i in range(7):
        a = i * .9 + .3
        r.line([(80, 78 + y), (80 + math.cos(a) * 34, 78 + y + math.sin(a) * 30)],
               C('#3a1a30', 200), 3.2)
    # 中央巨眼
    r.gell((48, 48 + y - beat * .4, 112, 112 + y - beat * .4), '#d8c8b8', 1.2, .62)
    r.gell((60, 60 + y - beat * .4, 100, 100 + y - beat * .4), '#c85a3a', 1.26, .5)
    _pupil(r, 80, 80 + y - beat * .4, 9.0, '#0a0206')
    _eye(r, 80, 80 + y - beat * .4, 5.0, '#ff8a4a', a=215)
    # 多眼环（8 个小眼）
    for i in range(8):
        a = i * .7854 + t * .1
        ex = 80 + math.cos(a) * 46
        ey = 78 + y + math.sin(a) * 36
        _eye(r, ex, ey, 2.6, '#ff9a6a', a=200)
        _pupil(r, ex, ey, 1.8)
    # 顶部破碎冠刺
    for i in range(6):
        bx = 40 + i * 16
        r.solid([(bx, 34 + y), (bx + 5, 2 + y - (i % 2) * 10), (bx + 11, 34 + y)],
                C('#9a5a7a', 240), bevel=2.0, top=1.5, dark=.55)
    # 底部熔岩裂缝
    r.emit_line([(46, 116 + y), (62, 128 + y), (58, 140 + y)], '#ff7a4a', 2.4, a=230)
    r.emit_line([(112, 114 + y), (98, 128 + y), (104, 140 + y)], '#ff7a4a', 2.4, a=230)
    return r.final(rim=('#ff9a8a', .52), shadow=(80, 146, 66, 160), grain=.07, glow_blur=5.5)


BOSSES = [b_moss, b_forge, b_archive, b_huntress, b_tyrant, b_consul, b_twin, b_heart]
