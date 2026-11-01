"""20 敌种 —— 形态差异优先，行为类型可从剪影读出。

顺序同 Enemies.NAMES 0..19：
0 幽狼(追击) 1 孢子射手(射手) 2 冲角兽(冲锋) 3 爆裂菇(自爆) 4 裂殖体(分裂)
5 织巢蛛(召唤) 6 灰烬祭司(区域) 7 骨弩手(狙击) 8 铁卫(坦克) 9 噬星虫(虫群)
10 影跃者(闪现) 11 霜愈使(治疗) 12 岩盾兵(持盾) 13 涡心(螺旋) 14 哨戒炮(炮台)
15 虚翼(飞行) 16 熔壳巨兽(坦克) 17 冰晶守卫(持盾) 18 雷暴使(螺旋) 19 深渊之眼(狙击)

脚底 y≈140，躯干中心 x≈80，朝右。
"""
import math
from _art_core import Rig, C, sh, mixc

FOOT = 142.0


def _eye(r, x, y, rad, col, a=255):
    r.emit(x, y, rad, col, glow=True, a=a)


def _pupil(r, x, y, rad, col='#0b0f16'):
    r.ell((x - rad, y - rad, x + rad, y + rad), col)
    r.emit(x - rad * .32, y - rad * .34, rad * .34, '#ffffff', glow=False, a=225)


def m_wolf(t):
    r = Rig(2000)
    y = (0.0, 1.2, -0.54, -1.4)[t & 3]
    sw = (2.2, 0.0, -2.2, 0.0)[t & 3]
    c = '#6a5a58'
    for bx, sgn in ((58, -1), (72, 1)):
        r.solid([(bx - 5, 92), (bx + 5, 92), (bx + 4 + sgn * sw * .4, 128),
                 (bx + 5 + sgn * sw, 140), (bx - 4 + sgn * sw, 140),
                 (bx - 3.6 + sgn * sw * .4, 128)], sh(c, .60), bevel=2.0, top=1.25, dark=.55)
    r.solid([(40, 84 + y), (56, 66 + y), (96, 64 + y), (118, 80 + y),
             (116, 96 + y), (96, 100 + y), (52, 98 + y), (38, 92 + y)],
            c, bevel=3.4, top=1.28, dark=.54)
    for i in range(5):
        r.solid([(58 + i * 11, 66 + y - i * 1.5), (64 + i * 11, 46 + y - i * 2),
                 (70 + i * 11, 68 + y - i * 1.5)], sh(c, .74), bevel=1.6, top=1.4, dark=.5)
    for bx, sgn in ((100, 1), (112, -1)):
        r.solid([(bx - 5, 92), (bx + 5, 92), (bx + 4 - sgn * sw * .4, 128),
                 (bx + 5 - sgn * sw, 140), (bx - 4 - sgn * sw, 140),
                 (bx - 3.6 - sgn * sw * .4, 128)], sh(c, .86), bevel=2.0, top=1.3, dark=.55)
    r.solid([(96, 52 + y), (114, 48 + y), (124, 56 + y), (138, 62 + y),
             (138, 70 + y), (122, 74 + y), (108, 76 + y), (98, 68 + y)],
            sh(c, 1.06), bevel=3.0, top=1.32, dark=.52)
    r.solid([(120, 62 + y), (138, 66 + y), (137, 72 + y), (120, 73 + y)], sh(c, .70),
            bevel=1.8, top=1.3, dark=.5)
    r.solid([(102, 52 + y), (98, 30 + y), (110, 46 + y)], sh(c, .84), bevel=1.8, top=1.4, dark=.5)
    r.solid([(112, 50 + y), (116, 28 + y), (122, 48 + y)], sh(c, .74), bevel=1.8, top=1.4, dark=.5)
    _eye(r, 116, 59 + y, 2.8, '#ffd27a')
    _pupil(r, 116, 59 + y, 1.6)
    for i in range(4):
        r.poly([(126 + i * 3, 71 + y), (127.4 + i * 3, 76 + y), (129 + i * 3, 71 + y)],
               '#efe6d0')
    r.solid([(42, 86 + y), (26, 68 + y - sw * .8), (20, 52 + y - sw * 1.2),
             (26, 58 + y - sw), (32, 74 + y), (44, 92 + y)], sh(c, .68),
            bevel=2.0, top=1.3, dark=.5)
    return r.final(rim=('#e8b0a0', .40), shadow=(78, 144, 40, 115), grain=.06, glow_blur=5.0)


def m_spore(t):
    r = Rig(2001)
    y = (0.0, 0.9, -0.41, -1.1)[t & 3]
    sw = (1.8, 0.0, -1.8, 0.0)[t & 3]
    c = '#7fa06a'
    for bx, m in ((68, .62), (90, .9)):
        r.solid([(bx - 4, 104), (bx + 4, 104), (bx + 3 + sw * .3, 132),
                 (bx + 4, 141), (bx - 4, 141), (bx - 3 + sw * .3, 132)],
                sh(c, m), bevel=1.8, top=1.3, dark=.55)
    r.solid([(60, 88 + y), (100, 86 + y), (104, 110), (94, 120), (78, 118), (58, 110)],
            c, bevel=2.8, top=1.3, dark=.54)
    r.gell((52, 46 + y, 112, 92 + y), '#a8c784', 1.2, .5)
    r.solid([(52, 70 + y), (112, 68 + y), (108, 82 + y), (56, 84 + y)], '#8aac6a',
            bevel=2.4, top=1.34, dark=.5)
    for sx, sy, srad in ((66, 58, 6), (86, 52, 8), (100, 62, 5), (76, 70, 4)):
        r.solid([(sx - srad, sy + y), (sx, sy - srad + y), (sx + srad, sy + y),
                 (sx, sy + srad * .6 + y)], '#d8e8b8', bevel=1.2, top=1.5, dark=.6)
    _eye(r, 78, 100 + y, 2.4, '#f0ff9a')
    _eye(r, 92, 100 + y, 2.4, '#f0ff9a')
    bx = 106 + sw
    r.line([(bx + 6, 74), (bx - 4, 100), (bx + 6, 126)], '#6a5a3a', 4.4)
    r.line([(bx + 2, 76), (bx - 8, 100), (bx + 2, 124)], '#c8d898', 1.4)
    r.line([(bx + 6, 76), (bx - 2, 100), (bx + 6, 124)], '#e8f4c8', 1.0)
    for i in range(5):
        a = i * 1.2566 + t * .3
        _eye(r, 80 + math.cos(a) * 44, 44 + math.sin(a) * 26, 1.6, '#c8f0a0', a=140)
    return r.final(rim=('#c8f0a0', .42), shadow=(80, 144, 34, 105), grain=.07, glow_blur=5.0)


def m_charger(t):
    r = Rig(2002)
    y = (0.0, 1.4, -0.63, -1.6)[t & 3]
    sw = (3.0, 0.0, -3.0, 0.0)[t & 3]
    c = '#7a5a48'
    for bx, m, sgn in ((54, .58, 1), (72, .68, -1), (98, .92, -1), (116, 1.0, 1)):
        r.solid([(bx - 6, 100), (bx + 6, 100), (bx + 5 + sgn * sw * .4, 128),
                 (bx + 7 + sgn * sw, 141), (bx - 6 + sgn * sw, 141),
                 (bx - 4 + sgn * sw * .4, 128)], sh(c, m), bevel=2.2, top=1.28, dark=.55)
    r.solid([(38, 76 + y), (58, 56 + y), (104, 54 + y), (126, 72 + y),
             (124, 100 + y), (100, 106 + y), (52, 104 + y), (34, 92 + y)],
            c, bevel=3.6, top=1.26, dark=.52)
    for i in range(4):
        r.solid([(58 + i * 15, 56 + y - i * .6), (66 + i * 15, 44 + y),
                 (76 + i * 15, 58 + y - i * .6), (66 + i * 15, 62 + y)],
                sh(c, 1.16), bevel=2.0, top=1.4, dark=.5)
    r.solid([(106, 62 + y), (130, 58 + y), (142, 74 + y), (138, 94 + y),
             (118, 98 + y), (104, 84 + y)], sh(c, 1.06), bevel=3.2, top=1.32, dark=.52)
    r.solid([(130, 54 + y), (150, 34 + y), (146, 30 + y), (128, 50 + y)], '#e0d2b0',
            bevel=2.2, top=1.5, dark=.55)
    r.solid([(128, 52 + y), (144, 36 + y), (142, 33 + y), (126, 48 + y)], '#f4ecd8',
            bevel=1.6, top=1.6, dark=.6)
    r.solid([(122, 76 + y), (142, 66 + y), (144, 78 + y), (126, 88 + y)], '#d8c8a8',
            bevel=2.2, top=1.5, dark=.55)
    _eye(r, 126, 76 + y, 3.0, '#ff8a5a')
    _pupil(r, 126, 76 + y, 1.7)
    r.ring(136, 84 + y, 5, '#c8a870', 1.6)
    r.solid([(38, 84 + y), (22, 74 + y - sw), (16, 60 + y - sw * 1.4),
             (22, 66 + y - sw), (28, 78 + y), (40, 92 + y)], sh(c, .68),
            bevel=2.0, top=1.3, dark=.5)
    return r.final(rim=('#ffb890', .42), shadow=(80, 144, 46, 120), grain=.06, glow_blur=5.0)


def m_bomber(t):
    r = Rig(2003)
    y = (0.0, 1.0, -0.45, -2.0)[t & 3]
    p = (.8, 0.0, -.8, 0.0)[t & 3]
    c = '#9a5a52'
    for bx, m in ((68, .66), (90, .92)):
        r.solid([(bx - 6, 110), (bx + 6, 110), (bx + 5, 132), (bx + 7, 141),
                 (bx - 6, 141), (bx - 4, 132)], sh(c, m), bevel=2.0, top=1.3, dark=.55)
    r.gell((46, 40 + y - p * 3, 116, 116 + y), '#b86a5e', 1.2, .46)
    r.gell((58, 52 + y - p * 3, 104, 100 + y), '#d88a72', 1.24, .5)
    for (x0, y0, x1, y1) in ((68, 56, 76, 88), (94, 52, 88, 86),
                             (78, 94, 92, 74), (62, 78, 74, 96)):
        r.emit_line([(x0, y0 + y), (x1, y1 + y)], '#ffb070', 1.7, a=210)
    r.emit(80, 76 + y, 4.0, '#ff9a5a', glow=True, a=210)
    r.solid([(56, 44 + y), (106, 42 + y), (102, 54 + y), (60, 56 + y)], '#8a4a44',
            bevel=2.2, top=1.4, dark=.5)
    for sx, sy, srad in ((64, 68, 5), (100, 66, 4), (72, 96, 4), (96, 92, 5)):
        r.ell((sx - srad, sy + y, sx + srad, sy + srad * 2 + y), '#e8b0a0')
    _eye(r, 72, 66 + y, 3.6, '#ffe08a')
    _eye(r, 90, 66 + y, 3.6, '#ffe08a')
    r.ring(80, 76 + y, 40 + p * 3, C('#ff7a5a', 90), 1.6)
    return r.final(rim=('#ffb890', .46), shadow=(80, 144, 34, 118), grain=.07, glow_blur=5.0)


def m_splitter(t):
    r = Rig(2004)
    y = (0.0, 1.2, -0.54, -1.4)[t & 3]
    sw = (2.4, 0.0, -2.4, 0.0)[t & 3]
    c = '#6a5a86'
    for i in range(6):
        a = -1.1 + i * .44
        bx = 80 + (i - 2.5) * 9
        r.line([(bx, 100 + y), (bx + math.cos(a) * 16 + sw * .5, 124),
                (bx + math.cos(a) * 20 + sw, 138)], sh(c, .58), 3.2)
    r.gell((42, 34 + y, 118, 118 + y), '#7a68a0', 1.22, .46)
    r.gell((52, 44 + y, 108, 108 + y), '#9a88c0', 1.26, .5)
    for i in range(5):
        a = i * 1.2566 + .3
        cx = 80 + math.cos(a) * 22
        cy = 74 + y + math.sin(a) * 20
        r.gell((cx - 15, cy - 15, cx + 15, cy + 15), '#b8a8d8', 1.28, .5)
    for i in range(3):
        a = i * math.pi / 3 + .4
        r.line([(80 - math.cos(a) * 30, 74 + y - math.sin(a) * 28),
                (80 + math.cos(a) * 30, 74 + y + math.sin(a) * 28)], C('#2a1f3e', 190), 2.6)
    for (ex, ey, er) in ((70, 62, 5), (92, 66, 4), (80, 84, 4.6)):
        _eye(r, ex, ey + y, er, '#e8c8ff')
        _pupil(r, ex, ey + y, er * .38)
    for (sx, sy, sr) in ((44, 118, 9), (116, 116, 8)):
        r.gell((sx - sr, sy + y * .5 - sr, sx + sr, sy + y * .5 + sr), '#9a88c0', 1.2, .5)
        _eye(r, sx, sy + y * .5, 1.6, '#e8c8ff', a=170)
    return r.final(rim=('#d0b8ff', .48), shadow=(80, 144, 44, 122), grain=.05, glow_blur=5.0)


def m_spider(t):
    r = Rig(2005)
    y = (0.0, 1.0, -0.45, -1.2)[t & 3]
    c = '#6a5a4a'
    for i in range(4):
        bx = 62 + i * 8
        droop = (4.0, 0.0, -4.0, 0.0)[(t + i) & 3]
        r.line([(bx, 84 + y), (bx - 16 - i * 3, 104 + droop), (bx - 22 - i * 4, 136 + droop)],
               sh(c, .70), 3.6)
        r.line([(bx + 26, 84 + y), (bx + 44 + i * 3, 102 - droop),
                (bx + 50 + i * 4, 134 - droop)], sh(c, .88), 3.6)
    r.gell((40, 52 + y, 108, 116 + y), '#8a7058', 1.22, .46)
    for i in range(3):
        r.line([(52 + i * 12, 62 + y), (56 + i * 12, 108 + y)], sh(c, .58), 1.8)
    r.gell((60, 66 + y, 96, 100 + y), '#d8c890', 1.3, .55)
    for i in range(4):
        _eye(r, 66 + i * 8, 76 + (i % 2) * 10 + y, 2.0, '#ffe8a0', a=170)
    r.emit(78, 84 + y, 4.6, '#ffe8a0', glow=True, a=135)
    r.solid([(88, 46 + y), (114, 50 + y), (122, 66 + y), (114, 84 + y),
             (92, 84 + y), (84, 66 + y)], sh(c, 1.06), bevel=2.6, top=1.32, dark=.52)
    for ex, ey, er in ((100, 58, 3.4), (110, 60, 3.0), (100, 68, 2.6), (110, 70, 2.2)):
        _eye(r, ex, ey + y, er, '#ff9a6a')
    for s in (-1, 1):
        r.poly([(118, 76 + y), (130, 80 + y), (124, 88 + y)], '#4a3a30')
    return r.final(rim=('#f0d0a0', .42), shadow=(80, 144, 48, 118), grain=.06, glow_blur=5.0)


def m_priest(t):
    r = Rig(2006)
    y = (0.0, 1.0, -0.45, -1.6)[t & 3]
    sw = (1.4, 0.0, -1.4, 0.0)[t & 3]
    c = '#7a5a4a'
    for i in range(8):
        a = i * .7854 + t * .16
        r.emit(80 + math.cos(a) * 44, 78 + y + math.sin(a) * 30, 1.8, '#ffb070', a=155)
    r.solid([(54, 62 + y), (106, 60 + y), (118, 124 + y * .4), (104, 142),
             (82, 132), (60, 142), (44, 122)], sh(c, .72), bevel=3.2, top=1.28, dark=.5)
    r.poly([(64, 66 + y), (100, 64 + y), (108, 120), (94, 130), (80, 120), (62, 128),
            (54, 116)], sh(c, .88))
    r.line([(66, 68 + y), (56, 118)], sh(c, 1.4), 1.4)
    r.emit(80, 84 + y, 5.0, '#ffa860', glow=True, a=215)
    r.ring(80, 84 + y, 12, C('#ff9a50', 170), 1.6)
    r.solid([(64, 24 + y), (100, 22 + y), (104, 46 + y), (98, 62 + y),
             (66, 62 + y), (58, 46 + y)], sh(c, .86), bevel=3.2, top=1.34, dark=.5)
    r.poly([(68, 32 + y), (98, 30 + y), (96, 58 + y), (70, 58 + y)], '#120c0a')
    _eye(r, 76, 43 + y, 4.0, '#ffa860')
    _eye(r, 90, 43 + y, 4.0, '#ffa860')
    sx = 112 + sw
    r.line([(sx, 40), (sx - 2, 142)], '#4a3624', 3.2)
    r.gell((sx - 7, 26, sx + 7, 44), '#c88a50', .1, .4)
    r.emit(sx, 34, 4.4, '#ffb060', glow=True, a=215)
    return r.final(rim=('#ffc89a', .44), shadow=(80, 144, 34, 120), grain=.06, glow_blur=5.0)


def m_bonebow(t):
    r = Rig(2007)
    y = (0.0, 1.0, -0.45, -1.4)[t & 3]
    c = '#b8b0a0'
    for bx, m in ((70, .72), (90, .96)):
        r.solid([(bx - 5, 100), (bx + 5, 100), (bx + 4, 126), (bx + 6, 140),
                 (bx - 6, 140), (bx - 4, 126)], sh(c, m), bevel=2.0, top=1.3, dark=.55)
    r.solid([(64, 62 + y), (96, 60 + y), (100, 100 + y), (92, 106 + y),
             (68, 106 + y), (60, 100 + y)], sh(c, .92), bevel=2.8, top=1.3, dark=.55)
    for i in range(4):
        r.line([(66, 70 + i * 8 + y), (98, 68 + i * 8 + y)], '#6a6258', 2.4)
        r.poly([(68, 70 + i * 8 + y), (70, 76 + i * 8 + y), (72, 70 + i * 8 + y)], '#e8e0d0')
    r.solid([(70, 30 + y), (96, 28 + y), (102, 42 + y), (96, 56 + y),
             (74, 56 + y), (66, 42 + y)], '#e0d8c8', bevel=2.8, top=1.32, dark=.55)
    r.poly([(74, 42 + y), (96, 40 + y), (94, 52 + y), (76, 52 + y)], '#2a2620')
    r.emit_poly([(76, 44 + y), (86, 43 + y), (85, 50 + y), (77, 51 + y)], '#ff9a5a')
    r.emit_poly([(88, 43 + y), (95, 42 + y), (94, 49 + y), (89, 50 + y)], '#ff9a5a')
    for i in range(3):
        r.rect((78 + i * 5, 52 + y, 80 + i * 5, 56 + y), '#e8e0d0')
    gy = 74 + y
    r.solid([(92, gy - 4), (128, gy - 8), (128, gy + 6), (92, gy + 4)], '#5a4632',
            bevel=2.2, top=1.4, dark=.5)
    r.line([(96, gy - 14), (110, gy - 2), (96, gy + 10)], '#8a7050', 3.0)
    r.line([(96, gy - 13), (86, gy - 2), (96, gy + 9)], '#8a7050', 3.0)
    r.line([(96, gy - 13), (86, gy - 2), (96, gy + 9)], '#e8dcc0', 1.2)
    r.line([(104, gy - 1), (140, gy - 2)], '#d8c8a8', 1.6)
    r.poly([(140, gy - 2), (133, gy - 6), (133, gy + 3)], '#e8e0d0')
    r.emit(118, gy - 1, 2.2, '#ff9a5a', glow=False)
    return r.final(rim=('#ffd0a0', .44), shadow=(80, 144, 32, 115), grain=.05, glow_blur=5.0)


def m_ironguard(t):
    r = Rig(2008)
    y = (0.0, 1.2, -0.54, -1.2)[t & 3]
    c = '#6a7278'
    for bx, m in ((64, .72), (94, .96)):
        r.solid([(bx - 9, 102), (bx + 9, 102), (bx + 8, 130), (bx + 10, 141),
                 (bx - 10, 141), (bx - 8, 130)], sh(c, m), bevel=2.4, top=1.3, dark=.55)
    r.solid([(48, 56 + y), (110, 54 + y), (116, 96 + y), (104, 108 + y),
             (56, 108 + y), (44, 96 + y)], c, bevel=3.6, top=1.28, dark=.52)
    for i in range(3):
        r.solid([(52, 66 + i * 13 + y), (108, 64 + i * 13 + y), (109, 74 + i * 13 + y),
                 (51, 76 + i * 13 + y)], sh(c, 1.14 - i * .07), bevel=2.0, top=1.4, dark=.5)
    r.solid([(58, 20 + y), (100, 18 + y), (106, 40 + y), (100, 60 + y),
             (62, 60 + y), (54, 40 + y)], sh(c, 1.02), bevel=3.4, top=1.34, dark=.5)
    r.solid([(62, 32 + y), (100, 30 + y), (99, 42 + y), (63, 44 + y)], '#161c20',
            bevel=2.0, top=1.6, dark=.6)
    r.emit_poly([(66, 34 + y), (98, 32 + y), (97, 40 + y), (67, 42 + y)], '#ff8a5a')
    r.solid([(102, 40 + y), (136, 36 + y), (140, 74 + y), (128, 108 + y),
             (108, 112 + y), (98, 92 + y), (96, 60 + y)], sh(c, .82),
            bevel=3.2, top=1.36, dark=.55, hi=1.6)
    r.solid([(108, 52 + y), (130, 49 + y), (132, 74 + y), (122, 96 + y),
             (112, 98 + y), (104, 78 + y)], sh(c, 1.16), bevel=2.4, top=1.4, dark=.5)
    r.line([(104, 74 + y), (136, 70 + y)], '#c8a860', 2.4)
    r.emit(120, 74 + y, 3.0, '#ffd08a', glow=False)
    r.solid([(44, 60 + y), (34, 34 + y), (48, 30 + y), (56, 58 + y)], '#5a4a38',
            bevel=2.2, top=1.4, dark=.5)
    return r.final(rim=('#c8d8e0', .42), shadow=(80, 144, 44, 130), grain=.05, glow_blur=5.0)


def m_swarm(t):
    r = Rig(2009)
    y = (0.0, 1.6, -0.72, -1.6)[t & 3]
    c = '#8a6a4a'
    for i in range(3):
        droop = (4.0, 0.0, -4.0, 0.0)[(t + i) & 3]
        bx = 62 + i * 14
        r.line([(bx, 104 + y), (bx - 8, 122 + droop), (bx - 12, 138 + droop)],
               sh(c, .58), 2.6)
        r.line([(bx + 10, 104 + y), (bx + 18, 122 - droop), (bx + 22, 138 - droop)],
               sh(c, .76), 2.6)
    r.gell((48, 76 + y, 112, 122 + y), '#7a5c40', 1.22, .46)
    for i in range(4):
        r.line([(54 + i * 14, 82 + y), (56 + i * 14, 116 + y)], sh(c, .56), 1.8)
    r.gell((54, 56 + y, 106, 100 + y), '#9a7a52', 1.24, .5)
    for ex, ey, er in ((68, 72, 4.4), (92, 72, 4.4), (80, 84, 3.6)):
        _eye(r, ex, ey + y, er, '#ffe06a')
    for s in (-1, 1):
        r.poly([(74 + s * 8, 96 + y), (80 + s * 18, 104 + y), (74 + s * 6, 108 + y)],
               '#e8e0c0')
    return r.final(rim=('#ffd890', .42), shadow=(80, 144, 34, 100), grain=.07, glow_blur=5.0)


def m_blink(t):
    r = Rig(2010)
    y = (0.0, 1.2, -0.54, -2.4)[t & 3]
    c = '#5a4a6a'
    for (ox, al) in ((-20, 70), (-10, 110)):
        r.poly([(58 + ox, 60 + y), (86 + ox, 58 + y), (92 + ox, 96 + y),
                (80 + ox, 138), (62 + ox, 132), (52 + ox, 92)], C(c, al))
    r.solid([(54, 52 + y), (92, 50 + y), (98, 92 + y), (86, 138), (60, 134), (46, 90)],
            c, bevel=3.0, top=1.3, dark=.46)
    sw = (2.4, 0.0, -2.4, 0.0)[t & 3]
    for i in range(4):
        r.poly([(48 + i * 12, 128 + sw * .4), (50 + i * 12, 140 + sw),
                (54 + i * 12, 126)], sh(c, .5))
    _eye(r, 78, 74 + y, 7.0, '#d0a8ff', a=225)
    _pupil(r, 78, 74 + y, 4.4)
    r.ring(78, 74 + y, 13, C('#b88aff', 120), 1.6)
    r.solid([(52, 48 + y), (94, 46 + y), (92, 60 + y), (54, 62 + y)], sh(c, .74),
            bevel=2.2, top=1.36, dark=.5)
    return r.final(rim=('#d8b8ff', .5), shadow=(80, 144, 28, 100), grain=.05, glow_blur=5.0)


def m_healer(t):
    r = Rig(2011)
    y = (0.0, 1.0, -0.45, -1.8)[t & 3]
    c = '#8ab8c8'
    for i in range(2):
        rad = 30 + i * 12 + (t & 3) * 2
        r.ring(80, 84 + y, rad, C('#a8f0ff', 88 - i * 26), 1.6)
    for i in range(6):
        a = i * 1.047 + t * .22
        r.emit(80 + math.cos(a) * 34, 84 + y + math.sin(a) * 22, 1.7, '#c8f8ff', a=155)
    r.solid([(56, 52 + y), (102, 50 + y), (110, 96 + y), (96, 124 + y),
             (80, 132 + y), (64, 124 + y), (50, 96 + y)], c, bevel=3.2, top=1.3, dark=.5)
    r.poly([(64, 56 + y), (96, 54 + y), (102, 94 + y), (90, 116 + y), (80, 120 + y),
            (68, 114 + y), (58, 92 + y)], sh(c, 1.14))
    r.solid([(76, 70 + y), (84, 70 + y), (84, 100 + y), (76, 100 + y)], '#eafcff',
            bevel=1.8, top=1.2, dark=.8)
    r.solid([(66, 80 + y), (94, 80 + y), (94, 90 + y), (66, 90 + y)], '#eafcff',
            bevel=1.8, top=1.2, dark=.8)
    r.emit(80, 85 + y, 4.6, '#a8f0ff', glow=True, a=215)
    r.solid([(62, 24 + y), (100, 22 + y), (104, 44 + y), (98, 60 + y),
             (64, 62 + y), (58, 44 + y)], sh(c, 1.04), bevel=3.0, top=1.34, dark=.5)
    r.poly([(68, 32 + y), (96, 30 + y), (94, 52 + y), (70, 52 + y)], '#0e1c22')
    _eye(r, 76, 41 + y, 3.4, '#d8fcff')
    _eye(r, 88, 41 + y, 3.4, '#d8fcff')
    sx = 112
    r.line([(sx, 34), (sx - 2, 140)], '#4a6a78', 3.0)
    r.solid([(sx - 5, 34), (sx - 1, 14), (sx + 5, 34)], C('#c8f0ff', 235),
            bevel=1.8, top=1.6, dark=.6)
    r.emit(sx - 1, 20, 3.8, '#c8f8ff', glow=True, a=210)
    return r.final(rim=('#d8fcff', .5), shadow=(80, 144, 26, 92), grain=.05, glow_blur=5.0)


def m_rock(t):
    r = Rig(2012)
    y = (0.0, 1.2, -0.54, -1.2)[t & 3]
    c = '#7a7268'
    for bx, m in ((66, .72), (94, .96)):
        r.solid([(bx - 8, 104), (bx + 8, 104), (bx + 7, 130), (bx + 9, 141),
                 (bx - 9, 141), (bx - 7, 130)], sh(c, m), bevel=2.4, top=1.3, dark=.55)
    r.solid([(50, 54 + y), (110, 52 + y), (116, 96 + y), (104, 110 + y),
             (56, 110 + y), (44, 96 + y)], c, bevel=3.6, top=1.28, dark=.5)
    for i, (bx, by, bw, bh) in enumerate(((56, 62, 22, 16), (82, 58, 20, 14),
                                          (60, 84, 18, 14), (84, 82, 22, 16))):
        r.solid([(bx, by + y), (bx + bw, by - 2 + y), (bx + bw + 2, by + bh + y),
                 (bx - 1, by + bh + 1 + y)], sh(c, 1.14 + i * .04),
                bevel=2.0, top=1.4, dark=.5)
    r.solid([(66, 28 + y), (98, 26 + y), (102, 44 + y), (96, 56 + y),
             (70, 56 + y), (62, 44 + y)], sh(c, 1.0), bevel=2.8, top=1.32, dark=.5)
    r.emit_poly([(72, 36 + y), (94, 34 + y), (93, 42 + y), (73, 44 + y)], '#ffb060')
    r.solid([(96, 42 + y), (132, 38 + y), (140, 72 + y), (130, 106 + y),
             (108, 114 + y), (96, 96 + y)], sh(c, .84), bevel=3.4, top=1.34, dark=.52)
    for i in range(3):
        r.line([(100 + i * 10, 48 + y), (104 + i * 10, 108 + y)], sh(c, 1.2), 2.0)
    r.line([(100, 74 + y), (136, 70 + y)], sh(c, 1.3), 2.0)
    r.emit(118, 76 + y, 3.2, '#ffb060', glow=False)
    return r.final(rim=('#e0d0b0', .42), shadow=(80, 144, 46, 128), grain=.08, glow_blur=5.0)


def m_vortex(t):
    r = Rig(2013)
    y = (0.0, 1.0, -0.45, -2.0)[t & 3]
    c = '#6a5a8a'
    for i in range(4):
        a = t * .55 + i * 1.5708
        bx, by = 80 + math.cos(a) * 44, 78 + y + math.sin(a) * 34
        r.poly([(bx + math.cos(a) * 10, by + math.sin(a) * 8),
                (bx + math.cos(a + .9) * 16, by + math.sin(a + .9) * 13),
                (bx + math.cos(a + .5) * 4, by + math.sin(a + .5) * 3)],
               C('#b89aff', 225))
    r.ring(80, 78 + y, 30, C('#a88ae0', 170), 2.6)
    r.ring(80, 78 + y, 22, C('#d0b8ff', 140), 1.8)
    r.gell((58, 56 + y, 102, 100 + y), '#8a72b8', 1.26, .46)
    r.gell((66, 64 + y, 94, 92 + y), '#b8a0e0', 1.3, .5)
    _eye(r, 80, 78 + y, 6.0, '#e8d8ff', a=225)
    _pupil(r, 80, 78 + y, 3.6)
    r.solid([(60, 108 + y), (100, 106 + y), (104, 124 + y), (56, 126 + y)],
            sh(c, .74), bevel=2.4, top=1.34, dark=.5)
    for i in range(3):
        r.line([(64 + i * 12, 110 + y), (66 + i * 12, 124 + y)], sh(c, 1.2), 1.6)
    return r.final(rim=('#e0ccff', .48), shadow=(80, 144, 32, 105), grain=.04, glow_blur=5.0)


def m_turret(t):
    r = Rig(2014)
    y = (0.0, 0.5, -0.23, -0.8)[t & 3]
    c = '#5a6a70'
    for a in (-.7, 0, .7):
        r.line([(80, 100), (80 + math.sin(a) * 34, 142)], sh(c, .70), 5.0)
    r.solid([(54, 84 + y), (106, 82 + y), (110, 100 + y), (50, 102 + y)],
            sh(c, .9), bevel=2.6, top=1.36, dark=.52)
    r.gell((56, 44 + y, 104, 92 + y), sh(c, 1.06), 1.22, .48)
    r.solid([(58, 62 + y), (102, 60 + y), (104, 74 + y), (56, 76 + y)],
            sh(c, 1.2), bevel=2.2, top=1.4, dark=.5)
    for oy in (-7, 7):
        r.solid([(96, 62 + y + oy), (136, 58 + y + oy), (136, 70 + y + oy),
                 (96, 72 + y + oy)], sh(c, .82), bevel=2.2, top=1.4, dark=.55)
        r.emit(136, 64 + y + oy, 3.0, '#ff9a5a', glow=True, a=180)
    _eye(r, 78, 56 + y, 4.4, '#ff7a5a', a=225)
    _pupil(r, 78, 56 + y, 2.6)
    r.ring(78, 56 + y, 12, C('#ff9a6a', 140), 1.6)
    r.line([(70, 46 + y), (66, 20 + y)], sh(c, 1.2), 1.8)
    r.emit(66, 18 + y, 2.4, '#ff6a4a', glow=False)
    return r.final(rim=('#ffb890', .44), shadow=(80, 144, 34, 100), grain=.05, glow_blur=5.0)


def m_wing(t):
    r = Rig(2015)
    y = (0.0, 2.0, -0.9, -2.4)[t & 3]
    c = '#5a4a7a'
    for s in (-1, 1):
        flap = (-6.0, 0.0, --6.0, 0.0)[t & 3] * -s
        r.solid([(78, 70 + y),
                 (78 + s * 34, 34 + y + flap), (78 + s * 56, 50 + y + flap * .6),
                 (78 + s * 40, 74 + y + flap * .3), (78 + s * 52, 96 + y - flap * .5),
                 (78 + s * 26, 88 + y)], C(c, 235), bevel=2.6, top=1.34, dark=.5)
        r.poly([(78, 72 + y), (78 + s * 28, 42 + y + flap),
                (78 + s * 42, 56 + y + flap * .6), (78 + s * 24, 76 + y)],
               C('#8a6ab8', 200))
    r.solid([(66, 56 + y), (96, 54 + y), (100, 96 + y), (88, 112 + y),
             (72, 112 + y), (62, 96 + y)], c, bevel=3.0, top=1.3, dark=.48)
    _eye(r, 80, 74 + y, 6.6, '#c8a0ff', a=225)
    _pupil(r, 80, 74 + y, 4.0)
    r.poly([(76, 112 + y), (80, 138), (84, 112 + y)], C('#8a6ab8', 225))
    for i in range(3):
        aa = t * .4 + i * 2.09
        r.emit(80 + math.cos(aa) * 30, 84 + y + math.sin(aa) * 22, 1.6, '#c8a0ff', a=140)
    return r.final(rim=('#d8c0ff', .5), shadow=(80, 144, 26, 88), grain=.04, glow_blur=5.0)


def m_magma(t):
    r = Rig(2016)
    y = (0.0, 1.4, -0.63, -1.4)[t & 3]
    c = '#5a3a32'
    for bx, m in ((58, .66), (76, .78), (98, .94), (116, 1.0)):
        r.solid([(bx - 9, 96), (bx + 9, 96), (bx + 8, 126), (bx + 10, 141),
                 (bx - 10, 141), (bx - 8, 126)], sh(c, m), bevel=2.4, top=1.28, dark=.55)
    r.solid([(32, 62 + y), (58, 38 + y), (108, 36 + y), (132, 58 + y),
             (130, 96 + y), (104, 112 + y), (52, 112 + y), (30, 92 + y)],
            c, bevel=4.0, top=1.26, dark=.5)
    for i, (bx, by, bw, bh) in enumerate(((44, 50, 26, 18), (74, 44, 28, 16),
                                          (48, 76, 24, 18), (78, 72, 30, 20),
                                          (104, 60, 22, 22))):
        r.solid([(bx, by + y), (bx + bw, by - 2 + y), (bx + bw + 2, by + bh + y),
                 (bx - 2, by + bh + 2 + y)], sh(c, 1.14 + i * .05),
                bevel=2.2, top=1.4, dark=.5)
    r.emit_line([(56, 56 + y), (66, 84 + y), (60, 104 + y)], '#ff8a4a', 2.2, a=235)
    r.emit_line([(86, 52 + y), (94, 78 + y), (88, 100 + y)], '#ff8a4a', 2.2, a=235)
    r.emit(78, 80 + y, 5.4, '#ff7a3a', glow=True, a=215)
    r.solid([(100, 36 + y), (134, 34 + y), (142, 58 + y), (132, 82 + y),
             (106, 84 + y), (98, 62 + y)], sh(c, 1.02), bevel=3.2, top=1.32, dark=.5)
    for ex in (114, 128):
        _eye(r, ex, 56 + y, 3.2, '#ffca5a')
        _pupil(r, ex, 56 + y, 2.0)
    for i in range(5):
        r.poly([(108 + i * 6, 74 + y), (110 + i * 6, 82 + y), (112 + i * 6, 74 + y)],
               '#f0e0c0')
    for i in range(3):
        r.poly([(56 + i * 20, 40 + y), (62 + i * 20, 20 + y - i * 2),
                (70 + i * 20, 40 + y)], sh(c, .84))
    return r.final(rim=('#ffb07a', .46), shadow=(80, 144, 52, 140), grain=.08, glow_blur=5.0)


def m_ice(t):
    r = Rig(2017)
    y = (0.0, 1.0, -0.45, -1.4)[t & 3]
    c = '#7aa8c8'
    for bx, m in ((68, .74), (93, .98)):
        r.solid([(bx - 7, 102), (bx + 7, 102), (bx + 6, 130), (bx + 8, 141),
                 (bx - 8, 141), (bx - 6, 130)], sh(c, m), bevel=2.2, top=1.3, dark=.55)
    r.solid([(54, 48 + y), (108, 46 + y), (114, 94 + y), (100, 108 + y),
             (60, 108 + y), (48, 94 + y)], C('#a8d4ec', 240), bevel=3.4, top=1.32, dark=.55)
    r.solid([(80, 54 + y), (94, 74 + y), (80, 98 + y), (66, 74 + y)],
            C('#d8f0ff', 250), bevel=2.4, top=1.5, dark=.6)
    r.poly([(80, 60 + y), (88, 74 + y), (80, 90 + y), (73, 74 + y)], C('#f8fdff', 252))
    r.emit(80, 76 + y, 4.0, '#c8ecff', glow=True, a=215)
    r.solid([(64, 20 + y), (98, 18 + y), (102, 40 + y), (96, 58 + y),
             (66, 58 + y), (60, 40 + y)], C('#b8dcf0', 244), bevel=3.2, top=1.34, dark=.55)
    r.poly([(68, 30 + y), (96, 28 + y), (94, 50 + y), (70, 50 + y)], '#12242e')
    _eye(r, 76, 39 + y, 2.8, '#a8f0ff')
    _eye(r, 88, 39 + y, 2.8, '#a8f0ff')
    for i in range(4):
        r.poly([(62 + i * 14, 22 + y), (66 + i * 14, 0 + y - (i % 2) * 6),
                (72 + i * 14, 22 + y)], C('#c8ecff', 235))
    r.solid([(100, 50 + y), (130, 46 + y), (136, 78 + y), (124, 104 + y),
             (104, 106 + y), (96, 84 + y)], C('#a0cce8', 235),
            bevel=3.0, top=1.36, dark=.55)
    for i in range(3):
        r.line([(104 + i * 9, 54 + y), (108 + i * 9, 100 + y)], C('#e8f8ff', 190), 1.6)
    return r.final(rim=('#e0f8ff', .52), shadow=(80, 144, 44, 122), grain=.04, glow_blur=5.0)


def m_storm(t):
    r = Rig(2018)
    y = (0.0, 1.8, -0.81, -2.2)[t & 3]
    c = '#6a6a92'
    for i in range(3):
        a0 = t * .7 + i * 2.094
        pts = []
        for k in range(9):
            a = a0 + k * .55
            rr = 30 + (k % 2) * 12
            pts.append((80 + math.cos(a) * rr, 74 + y + math.sin(a) * rr * .7))
        r.emit_line(pts, '#ffe98a', 1.5, a=200)
    r.solid([(56, 48 + y), (102, 46 + y), (110, 96 + y), (94, 128 + y),
             (80, 136 + y), (64, 128 + y), (50, 96 + y)], c, bevel=3.2, top=1.3, dark=.5)
    r.poly([(64, 52 + y), (96, 50 + y), (102, 94 + y), (88, 118 + y), (80, 122 + y),
            (68, 116 + y), (58, 92 + y)], sh(c, 1.16))
    r.gell((66, 66 + y, 94, 94 + y), '#e8e0a8', .1, .5)
    r.emit(80, 80 + y, 5.4, '#ffe98a', glow=True, a=215)
    r.solid([(62, 20 + y), (100, 18 + y), (104, 42 + y), (98, 58 + y),
             (64, 60 + y), (58, 42 + y)], sh(c, 1.04), bevel=3.0, top=1.34, dark=.5)
    r.poly([(68, 28 + y), (96, 26 + y), (94, 50 + y), (70, 50 + y)], '#14141e')
    _eye(r, 76, 38 + y, 4.0, '#fff0a0')
    _eye(r, 88, 38 + y, 4.0, '#fff0a0')
    sx = 112
    r.line([(sx, 30), (sx - 2, 138)], '#4a4a3a', 3.2)
    r.solid([(sx - 6, 32), (sx, 10), (sx + 6, 32)], '#c8b860', bevel=1.8, top=1.6, dark=.55)
    r.emit(sx, 18, 4.0, '#ffe98a', glow=True, a=210)
    return r.final(rim=('#fff0b0', .5), shadow=(80, 144, 26, 90), grain=.04, glow_blur=5.0)


def m_abysseye(t):
    r = Rig(2019)
    y = (0.0, 1.2, -0.54, -2.0)[t & 3]
    c = '#4a3a5a'
    for i in range(7):
        bx = 46 + i * 12
        wob = math.sin(t * 1.1 + i * .9) * 5
        r.line([(bx, 104 + y), (bx - 8 + wob, 122), (bx - 4 + wob * 1.6, 140)],
               sh(c, .66 + (i % 3) * .1), 3.4)
    r.gell((34, 30 + y, 126, 116 + y), '#6a5486', 1.24, .44)
    r.gell((44, 38 + y, 116, 108 + y), '#8a6aa8', 1.26, .48)
    r.gell((56, 48 + y, 106, 100 + y), '#e8e0d8', 1.22, .66)
    r.gell((66, 58 + y, 96, 90 + y), '#c86a4a', 1.3, .5)
    _pupil(r, 81, 74 + y, 7.0, '#0a0408')
    r.emit(81, 74 + y, 2.8, '#ffb060', glow=True, a=190)
    for i in range(5):
        a = i * 1.2566 + .4
        r.line([(81 + math.cos(a) * 16, 74 + y + math.sin(a) * 14),
                (81 + math.cos(a) * 26, 74 + y + math.sin(a) * 22)], C('#b04a3a', 180), 1.2)
    r.line([(52, 68 + y), (66, 54 + y), (98, 52 + y), (110, 66 + y)], sh(c, 1.3), 3.0)
    r.line([(52, 82 + y), (66, 96 + y), (98, 98 + y), (110, 84 + y)], sh(c, .8), 3.0)
    for i in range(4):
        r.poly([(60 + i * 14, 34 + y), (64 + i * 14, 8 + y - (i % 2) * 5),
                (70 + i * 14, 34 + y)], sh(c, 1.1))
    return r.final(rim=('#e8a0a0', .46), shadow=(80, 144, 50, 128), grain=.06, glow_blur=5.0)


MONSTERS = [m_wolf, m_spore, m_charger, m_bomber, m_splitter, m_spider, m_priest,
            m_bonebow, m_ironguard, m_swarm, m_blink, m_healer, m_rock, m_vortex,
            m_turret, m_wing, m_magma, m_ice, m_storm, m_abysseye]
