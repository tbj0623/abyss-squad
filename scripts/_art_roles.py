"""12 职业：剪影优先。头型 / 帽饰 / 武器轮廓 / 披风必须一眼可分。

统一骨架：头顶 18.5 / 肩 46 / 腰 84 / 髋 88 / 膝 112 / 脚底 142。
武器位于 x 100..148（朝右），可溢出单元边缘。朝左由渲染层镜像。
"""
import math
from _art_core import Rig, C, sh, mixc, CELL
from _art_heroes import (ROLE_RGB, BOB, SWING, CAPE, sway, cwave, legs, torso, arm,
                         head_skin, hood, eyes, cape, w_bow, w_staff, w_shield,
                         cloth_gap, CX, FOOT, HIP, KNEE, WAIST, SHOULDER,
                         HEAD_CY, HEAD_RX, HEAD_RY, HEAD_TOP)


def hero_ranger(t):
    c = ROLE_RGB[0]; r = Rig(1000)
    y = BOB[t & 3]
    cape(r, t, '#2b6b57', 44 + y, 140, 22, 1.2, 4, trim='#8fd8b8')
    legs(r, t, '#32443b', '#40331f')
    torso(r, t, sh(c, .68), '#6a5535', 'plate', 17.0)
    r.solid([(53, 44), (67, 40), (71, 80), (57, 84)], '#4e3a26', bevel=2.4, top=1.4, dark=.55)
    r.poly([(54, 46), (62, 44), (65, 80), (59, 82)], '#6d5336')
    for i in range(3):
        r.line([(54 + i * 4.6, 44), (49 + i * 5.0, 24)], '#e6d6ae', 1.3)
        r.poly([(48 + i * 5.0, 24), (50 + i * 5.0, 18), (52 + i * 5.0, 24)], c)
    arm(r, t, sh(c, .62), 62, SHOULDER, 78, False)
    hood(r, t, sh(c, .84), CX, HEAD_CY, 15.0, -13)
    head_skin(r, t, CX, HEAD_CY + 1, 9.6, 10.8, '#c3a184')
    eyes(r, CX, HEAD_CY - 1 + y, '#d8fff0', 5.0, 2.7, 1.4)
    r.poly([(CX - 15, 22 + y), (CX + 16, 20 + y), (CX + 15, 28 + y), (CX - 14, 30 + y)],
           sh(c, .74))
    r.poly([(CX - 14, 27 + y), (CX + 15, 25 + y), (CX + 14, 29 + y), (CX - 13, 31 + y)],
           sh(c, .52))
    w_bow(r, t, c)
    r.solid([(94, 52), (105, 52), (107, 64), (96, 65)], sh(c, .76), bevel=2.2, top=1.3, dark=.55)
    r.ell((102, 57, 111, 66), '#c3a184')
    r.emit(CX + 1, 68 + y, 2.4, c, glow=False)
    return r.final(rim=('#bdf3de', .42), shadow=(79, 144, 23, 150), grain=.05)


def hero_guard(t):
    c = ROLE_RGB[1]; r = Rig(1001)
    y = BOB[t & 3]
    cape(r, t, '#6b5228', 42 + y, 138, 25, .9, 4, trim='#e2c78e')
    legs(r, t, '#7a6644', '#483a2a', 71.5, 93.5, 1.16)
    torso(r, t, sh(c, .76), '#8a6a38', 'heavy', 20.5)
    r.solid([(CX, 26 + y), (CX - 5, -2 + y), (CX + 5, 6 + y), (CX + 14, -4 + y),
             (CX + 8, 27 + y)], '#a83f38', bevel=2.2, top=1.5, dark=.5)
    r.poly([(CX + 1, 28 + y), (CX - 2, 8 + y), (CX + 4, 12 + y), (CX + 10, 6 + y),
            (CX + 6, 29 + y)], '#d9745a')
    r.solid([(65, 16 + y), (100, 14 + y), (105, 34 + y), (101, 56 + y),
             (65, 58 + y), (60, 34 + y)], sh(c, .90), bevel=3.4, top=1.34, dark=.52, hi=1.60)
    r.solid([(67, 28 + y), (100, 27 + y), (99, 48 + y), (68, 49 + y)], '#1e242c',
            bevel=2.0, top=1.6, dark=.7)
    r.solid([(68, 35 + y), (100, 34 + y), (99, 40 + y), (69, 41 + y)], '#e6cc90', bevel=1.4)
    r.solid([(79, 23 + y), (88, 22 + y), (88, 52 + y), (79, 53 + y)], sh(c, 1.14),
            bevel=2.0, top=1.4, dark=.55)
    eyes(r, CX, 38 + y, '#ffe3a6', 8.4, 2.6, 1.3)
    w_shield(r, t, sh(c, .92), '#a8843c', 108, 36 + y, 48, 66)
    arm(r, t, sh(c, .66), 63, SHOULDER, 78, False)
    r.solid([(96, 60), (106, 59), (109, 86), (99, 87)], sh(c, .72), bevel=2.2, top=1.3)
    r.poly([(112, 30 + y), (117, 8 + y), (122, 30 + y)], '#b8c4c8')
    r.poly([(113, 29 + y), (117, 13 + y), (120, 29 + y)], '#eef4f6')
    return r.final(rim=('#fbe2ae', .42), shadow=(80, 144, 25, 158), grain=.04)


def hero_medic(t):
    c = ROLE_RGB[2]; r = Rig(1002)
    y = BOB[t & 3]
    r.solid([(58, 76 + y), (108, 74 + y), (120, 126), (105, 141), (85, 132), (67, 142),
             (48, 126), (54, 100)], sh(c, .58), bevel=3.4, top=1.24, dark=.52)
    r.poly([(66, 80 + y), (100, 78 + y), (107, 124), (94, 130), (80, 124), (65, 130),
            (57, 118), (61, 98)], sh(c, .72))
    r.line([(67, 82 + y), (62, 124)], sh(c, 1.36), 1.4)
    r.line([(99, 80 + y), (104, 120)], sh(c, 1.16), 1.1)
    r.solid([(79, 56 + y), (87, 56 + y), (87, 82 + y), (79, 82 + y)], '#eef6fa',
            bevel=1.8, top=1.2, dark=.78)
    r.solid([(71, 64 + y), (95, 64 + y), (95, 73 + y), (71, 73 + y)], '#eef6fa',
            bevel=1.8, top=1.2, dark=.78)
    r.emit(CX, 68 + y, 5.2, '#b9dcff', glow=True)
    legs(r, t, '#3a4a5a', '#4a3e32', 72.5, 91, .9)
    torso(r, t, sh(c, .74), '#b9c8d2', 'robe', 16.0)
    hood(r, t, sh(c, .84), CX, HEAD_CY, 15.0, -13)
    head_skin(r, t, CX, HEAD_CY + 1, 9.6, 10.8, '#c9ab8c')
    eyes(r, CX, HEAD_CY - 1 + y, '#e2f2ff', 5.0, 2.6, 1.3)
    r.poly([(CX - 14, 22 + y), (CX + 15, 20 + y), (CX + 14, 27 + y), (CX - 13, 29 + y)],
           sh(c, .82))
    arm(r, t, sh(c, .66), 61, SHOULDER, 78, False)
    r.line([(59, 76), (50, 88)], '#8a7448', 1.3)
    r.solid([(42, 86), (54, 84), (55, 101), (43, 103)], '#6c5638', bevel=2.0, top=1.5, dark=.5)
    r.emit(48.5, 93, 5.4, '#ffe7ae', glow=True)
    w_staff(r, t, '#93b3c8', 115, 0, 144, orb='#b8d6ff', rune=False, big=True)
    return r.final(rim=('#cfe8ff', .46), shadow=(79, 144, 26, 142), grain=.04)


def hero_warlock(t):
    c = ROLE_RGB[3]; r = Rig(1003)
    y = BOB[t & 3]
    r.solid([(54, 76 + y), (112, 74 + y), (124, 126), (108, 140), (86, 131), (64, 141),
             (42, 125), (50, 98)], sh(c, .50), bevel=3.4, top=1.26, dark=.50)
    r.poly([(64, 80 + y), (104, 78 + y), (114, 124), (98, 130), (82, 124), (62, 130),
            (52, 118), (58, 96)], sh(c, .64))
    for sx, sy in ((68, 94), (96, 104), (78, 116), (104, 88), (58, 112), (88, 88)):
        r.emit(sx, sy + y, 1.9, '#dcc9ff', glow=False, a=210)
    legs(r, t, '#2c2740', '#201b2e', 73, 91, .9)
    torso(r, t, sh(c, .62), '#6b5480', 'robe', 15.0)
    r.emit(CX, 62 + y, 4.0, '#e0d0ff', glow=True)
    r.ring(CX, 62 + y, 7.0, '#9a7fc8', 1.1)
    r.solid([(44, 30 + y), (122, 27 + y), (119, 38 + y), (48, 41 + y)], sh(c, .58),
            bevel=3.0, top=1.42, dark=.5, hi=1.62)
    r.poly([(50, 32 + y), (116, 29.6 + y), (114, 36 + y), (53, 38.4 + y)], sh(c, .76))
    r.solid([(66, 33 + y), (100, 31 + y), (93, 4 + y), (88, -10 + y)], sh(c, .82),
            bevel=3.0, top=1.36, dark=.5, hi=1.62)
    r.line([(72, 30 + y), (89, 6 + y), (88, -6 + y)], sh(c, 1.62), 1.3)
    r.emit(88, -4 + y, 3.4, '#e2d4ff', glow=True)
    head_skin(r, t, CX, HEAD_CY + 2, 9.6, 10.4, '#8f7a66')
    r.poly([(CX - 15, 26 + y), (CX + 16, 24 + y), (CX + 15, 44 + y), (CX - 14, 46 + y)],
           '#171226')
    eyes(r, CX, HEAD_CY - 2 + y, '#dcc9ff', 5.2, 3.0, 1.6)
    r.poly([(CX - 14, 25 + y), (CX + 15, 23 + y), (CX + 15, 28 + y), (CX - 14, 30 + y)],
           sh(c, .28))
    r.poly([(CX - 13, 42 + y), (CX + 14, 40 + y), (CX + 12, 47 + y), (CX - 11, 49 + y)],
           sh(c, .36))
    w_staff(r, t, c, 116, -6, 144, orb='#d8c6ff', rune=True, big=True)
    arm(r, t, sh(c, .62), 62, SHOULDER, 78, False)
    return r.final(rim=('#d6c2ff', .48), shadow=(79, 144, 26, 134), grain=.04)


def hero_alch(t):
    c = ROLE_RGB[4]; r = Rig(1004)
    y = BOB[t & 3]
    legs(r, t, '#5d4a34', '#3c2e20', 72.5, 92, 1.12)
    r.solid([(62, 70 + y), (104, 68 + y), (116, 122), (98, 136), (84, 127), (69, 137),
             (50, 122)], '#55412c', bevel=3.2, top=1.30, dark=.52)
    r.poly([(67, 73 + y), (100, 71 + y), (108, 120), (95, 129), (82, 121), (67, 129),
            (58, 117)], '#6d543a')
    r.line([(69, 75 + y), (63, 118)], '#a98a5e', 1.5)
    r.poly([(74, 92 + y), (96, 90 + y), (98, 112), (72, 114)], '#46351f')
    torso(r, t, sh(c, .78), '#8a6a46', 'heavy', 18.0)
    for px, pc, ph in ((69, '#7fd9a8', 0), (80, '#f0c060', 5)):
        r.solid([(px - 3.6, 58 + y + ph), (px + 3.6, 58 + y + ph), (px + 4.2, 73 + y + ph),
                 (px - 4.2, 73 + y + ph)], '#c2d8d0', bevel=1.6, top=1.4, dark=.6)
        r.ell((px - 4, 66 + y + ph, px + 4, 75 + y + ph), pc)
        r.rect((px - 2.2, 54 + y + ph, px + 2.2, 59 + y + ph), '#8a7048')
        r.emit(px, 71 + y + ph, 2.0, pc, glow=False)
    r.solid([(63, 12 + y), (102, 10 + y), (105, 32 + y), (101, 55 + y),
             (65, 57 + y), (59, 32 + y)], sh(c, .90), bevel=3.2, top=1.34, dark=.52)
    r.poly([(64, 14 + y), (101, 12 + y), (100, 26 + y), (65, 28 + y)], '#715935')
    for gx in (72.5, 93.5):
        r.ring(gx, 34 + y, 9.4, '#c9a066', 3.2)
        r.gell((gx - 7.4, 27 + y, gx + 7.4, 41 + y), '#a8d8dc', .1, .45)
        r.emit(gx + 2.2, 30 + y, 2.1, '#d8fff8', glow=False)
    r.solid([(73, 45 + y), (94, 44 + y), (92, 55 + y), (75, 56 + y)], '#a58457',
            bevel=2.2, top=1.34, dark=.5)
    for i in range(3):
        r.rect((77 + i * 4.4, 47 + y, 79 + i * 4.4, 53 + y), '#5c4632')
    arm(r, t, sh(c, .64), 62, SHOULDER, 78, False)
    ax = 104 + sway(t, 1.4)
    r.solid([(94, 58 + y), (107, 56 + y), (114, 71), (102, 75)], sh(c, .80),
            bevel=2.2, top=1.34, dark=.55)
    r.line([(109, 67), (126, 58 + y * .3)], '#4e3a26', 3.0)
    r.gell((121, 44, 145, 74), '#9a7d55', .1, .40)
    r.poly([(121, 60), (145, 60), (142, 76), (124, 76)], '#5a4630')
    r.emit(133, 58, 7.0, '#ffb96e', glow=True)
    r.ell((126, 50, 140, 61), '#c8a066')
    r.solid([(135 + ax * .2, 28), (143 + ax * .2, 26), (144 + ax * .2, 38),
             (136 + ax * .2, 40)], '#b8e0cc', bevel=1.6, top=1.5, dark=.55)
    r.emit(140 + ax * .2, 32, 2.8, '#8fe8b4', glow=False)
    return r.final(rim=('#ffd2a8', .42), shadow=(80, 144, 26, 150), grain=.06)


def hero_shadow(t):
    c = ROLE_RGB[5]; r = Rig(1005)
    y = BOB[t & 3]
    sw = sway(t, 3.6)
    r.solid([(70, 38 + y), (88, 36 + y), (78, 58), (44 + sw, 72 + y),
             (24 + sw * 1.35, 86 + y), (50, 90), (74, 66)], sh(c, .60),
            bevel=2.4, top=1.3, dark=.5)
    r.poly([(72, 42 + y), (83, 41 + y), (74, 60), (54 + sw * .8, 76 + y), (48, 86),
            (70, 68)], sh(c, .82))
    legs(r, t, '#2a2430', '#1b1720', 72.5, 92, .94)
    torso(r, t, sh(c, .64), '#5a4058', 'robe', 16.0)
    arm(r, t, sh(c, .60), 62, SHOULDER, 78, False)
    arm(r, t, sh(c, .76), 100, SHOULDER, 76, True)
    for ang, bx, by, sc in ((-26, 103, 64, 1.0), (40, 110, 74, .84)):
        a = math.radians(ang)
        ex, ey = bx + 34 * sc * math.cos(a), by + 34 * sc * math.sin(a)
        r.line([(bx - 2, by + 3), (ex, ey)], '#93a2ac', 4.6)
        r.line([(bx - 2, by + 2), (ex, ey - 1.2)], '#eaf4fa', 1.7)
        r.poly([(ex, ey), (ex + 4.0, ey + 3.2), (ex - 1.0, ey + 1.4)], '#f4fbff')
        r.solid([(bx - 5, by - 2), (bx - 6, by + 9), (bx + 2, by + 9), (bx + 1, by - 2)],
                '#3c2e26', bevel=1.6, top=1.5, dark=.5)
        r.emit(ex, ey, 1.9, '#ffd2e8', glow=False, a=215)
    r.solid([(64, 12 + y), (101, 10 + y), (103, 32 + y), (99, 54 + y),
             (66, 56 + y), (60, 32 + y)], sh(c, .88), bevel=3.2, top=1.36, dark=.50)
    r.poly([(64, 20 + y), (102, 18 + y), (101, 40 + y), (65, 42 + y)], '#241a2a')
    r.solid([(66, 28 + y), (101, 26 + y), (100, 38 + y), (67, 40 + y)], sh(c, 1.0),
            bevel=2.2, top=1.3, dark=.55)
    eyes(r, CX, HEAD_CY - 1 + y, '#ffd2e8', 5.4, 2.9, 1.5)
    r.solid([(65, 38 + y), (101, 36 + y), (99, 48 + y), (67, 50 + y)], '#30243a',
            bevel=2.4, top=1.3, dark=.5)
    r.poly([(66, 39 + y), (100, 37 + y), (99, 42 + y), (67, 44 + y)], sh(c, .66))
    r.poly([(52, 76), (41, 92), (46, 94), (56, 80)], '#8a98a2')
    r.line([(52, 76), (42, 91)], '#c6d2d8', 1.0)
    return r.final(rim=('#ffc4e2', .50), shadow=(79, 144, 23, 140), grain=.04)


def hero_mech(t):
    c = ROLE_RGB[6]; r = Rig(1006)
    y = BOB[t & 3] * .7
    legs(r, t, '#46565a', '#283238', 71.5, 93, 1.2)
    r.solid([(43, 40 + y), (62, 38 + y), (66, 88), (45, 92)], '#37464b', bevel=2.8, top=1.3, dark=.5)
    r.poly([(46, 43 + y), (58, 42 + y), (60, 86), (48, 89)], '#4d6065')
    for px in (49, 57):
        r.solid([(px, 40 + y), (px + 7, 39 + y), (px + 7, 14 + y), (px, 15 + y)],
                '#5a6e73', bevel=1.8, top=1.4, dark=.5)
        r.poly([(px + .8, 39 + y), (px + 2.6, 38.6 + y), (px + 2.6, 15.6 + y),
                (px + .8, 16 + y)], '#93adb2')
        r.emit(px + 3.5, 12 + y, 2.6, '#9fe8ec', glow=True, a=155)
    torso(r, t, sh(c, .68), '#4a5c60', 'heavy', 19.5)
    r.solid([(66, 50 + y), (100, 48 + y), (102, 80), (68, 82)], '#576b70',
            bevel=2.8, top=1.32, dark=.5)
    r.line([(67, 52 + y), (99, 50 + y)], sh(c, 1.7), 1.3)
    r.ring(CX + 1, 64 + y, 10.6, '#c8f4f6', 1.4)
    r.emit(CX + 1, 64 + y, 6.4, '#a8f4f8', glow=True)
    arm(r, t, sh(c, .62), 60, SHOULDER, 78, False)
    r.solid([(62, 8 + y), (101, 6 + y), (107, 32 + y), (102, 55 + y),
             (63, 57 + y), (56, 32 + y)], '#52656a', bevel=3.4, top=1.34, dark=.5, hi=1.6)
    r.poly([(63, 11 + y), (78, 10 + y), (67, 52 + y), (58, 32 + y)], '#6f858a')
    r.solid([(64, 17 + y), (101, 15 + y), (103, 29 + y), (65, 31 + y)], '#141c20',
            bevel=2.0, top=1.7, dark=.6)
    r.emit_poly([(67, 19 + y), (99, 17 + y), (100, 27 + y), (68, 29 + y)], '#b8f8fa')
    r.solid([(61, 31 + y), (105, 29 + y), (104, 40 + y), (62, 42 + y)], sh(c, 1.28),
            bevel=2.2, top=1.36, dark=.55)
    for i in range(4):
        r.rect((68 + i * 8.6, 43 + y, 72 + i * 8.6, 49 + y), sh(c, .38))
    gx = 100 + sway(t, .8)
    r.solid([(gx - 9, 54 + y), (gx + 8, 52 + y), (gx + 18, 66), (gx + 8, 82), (gx - 9, 80)],
            '#4b5d62', bevel=2.6, top=1.32, dark=.5)
    r.solid([(gx + 12, 58), (gx + 42, 52), (gx + 42, 70), (gx + 13, 78)], '#5c7075',
            bevel=2.4, top=1.36, dark=.5)
    r.poly([(gx + 13, 60), (gx + 40, 54.6), (gx + 40, 59.6), (gx + 13, 66)], '#8aa4a8')
    r.solid([(gx + 40, 53), (gx + 49, 52), (gx + 49, 69), (gx + 40, 70)], '#2e3c41',
            bevel=2.0, top=1.4, dark=.6)
    r.emit(gx + 49, 61, 6.2, '#b8f6f8', glow=True)
    r.ring(gx + 23, 66, 5.6, '#93b8bc', 1.3)
    return r.final(rim=('#c2f6f8', .46), shadow=(80, 144, 26, 154), grain=.03)


def hero_bard(t):
    c = ROLE_RGB[7]; r = Rig(1007)
    y = BOB[t & 3]
    cape(r, t, '#8a7038', 44 + y, 140, 24, 1.2, 4, trim='#f6e6b4')
    r.solid([(60, 76 + y), (106, 74 + y), (118, 124), (101, 138), (84, 129), (67, 139),
             (48, 124)], sh(c, .62), bevel=3.2, top=1.28, dark=.5)
    r.poly([(67, 79 + y), (101, 77 + y), (109, 122), (96, 130), (82, 121), (66, 129),
            (56, 118)], sh(c, .80))
    legs(r, t, '#54452e', '#3a2e1e', 72.5, 91.5, .95)
    torso(r, t, sh(c, .76), '#8f7440', 'robe', 16.0)
    for xx in (77.5, 88.5):
        r.line([(xx, 56 + y), (xx, 74)], '#fdf3cf', 1.8)
    r.line([(75, 56 + y), (91, 56 + y)], '#e8d49a', 1.5)
    r.emit(CX, 65 + y, 3.4, '#ffeeb0', glow=True)
    for i in range(12):
        a = i * math.pi * 2 / 12
        r.emit(CX + math.cos(a) * 17, -4 + y + math.sin(a) * 5.4, 2.1, '#fff0b8', glow=False)
    r.ring(CX, -4 + y, 17, C('#ffeeb0', 125), 1.3)
    head_skin(r, t, CX, HEAD_CY, 9.8, 10.8, '#c9ab8c')
    r.solid([(64, 16 + y), (70, 6 + y), (95, 7 + y), (106, 24 + y), (104, 52 + y),
             (96, 64 + y), (99, 30 + y), (70, 28 + y), (67, 58 + y), (60, 48 + y)],
            sh(c, .54), bevel=2.6, top=1.3, dark=.5)
    r.poly([(67, 16 + y), (80, 10 + y), (97, 13 + y), (102, 26 + y), (69, 26 + y)],
           sh(c, .72))
    r.line([(69, 17 + y), (95, 15 + y), (100, 27 + y)], '#e8d8a8', 1.4)
    r.poly([(70, 29 + y), (96, 27 + y), (95, 39 + y), (71, 41 + y)], '#f0e2bc')
    eyes(r, CX, HEAD_CY - 1 + y, '#fff6d6', 5.0, 2.5, 1.3)
    r.poly([(74, 41 + y), (92, 40 + y), (90, 46 + y), (76, 47 + y)], sh(c, .34))
    hx = 113 + sway(t, 1.0)
    r.solid([(hx - 11, 28), (hx + 8, 26), (hx + 11, 33), (hx - 9, 35)], '#6b4f2e',
            bevel=2.2, top=1.45, dark=.5)
    r.solid([(hx - 11, 30), (hx - 5, 30), (hx - 3, 104), (hx - 10, 104)], '#6b4f2e',
            bevel=2.0, top=1.4, dark=.5)
    r.solid([(hx + 6, 29), (hx + 12, 29), (hx + 16, 101), (hx + 8, 103)], '#6b4f2e',
            bevel=2.0, top=1.4, dark=.5)
    r.solid([(hx - 11, 98), (hx + 16, 96), (hx + 16, 107), (hx - 11, 109)], '#7d5c36',
            bevel=2.2, top=1.4, dark=.5)
    for i in range(5):
        r.line([(hx - 2 + i * 3.6, 35), (hx + 2.6 + i * 2.8, 97)], '#f8f0c8', 1.0)
    for i in range(3):
        r.emit(hx + 4, 50 + i * 16, 2.4, '#ffeeb0', glow=False)
    arm(r, t, sh(c, .70), 63, SHOULDER, 78, False)
    r.ell((hx - 9, 82, hx + 1, 92), '#c9ab8c')
    return r.final(rim=('#ffedbe', .46), shadow=(79, 144, 25, 146), grain=.04)


def hero_frost(t):
    c = ROLE_RGB[8]; r = Rig(1008)
    y = BOB[t & 3]
    sw = cwave(t, 1.7)
    r.poly([(54, 44 + y), (74, 38 + y), (66, 92), (38 + sw, 130), (44, 78)], C('#6ea8c8', 180))
    r.poly([(58, 49 + y), (68, 44 + y), (58, 88), (45 + sw * .8, 118)], C('#b8dcf0', 145))
    r.line([(58, 48 + y), (40 + sw, 128)], C('#e0f4ff', 170), 1.3)
    r.line([(66, 46 + y), (54 + sw * .7, 100)], C('#d0ecff', 125), 1.0)
    legs(r, t, '#38536c', '#243646', 72.5, 92, .94)
    torso(r, t, sh(c, .72), '#cfe6f4', 'robe', 16.0)
    r.solid([(CX, 50 + y), (95, 66), (CX, 86), (71, 66)], C('#bfe2f8', 240),
            bevel=2.6, top=1.5, dark=.55, hi=1.8)
    r.poly([(CX, 55 + y), (89, 66), (CX, 79), (77, 66)], C('#f6fbff', 250))
    r.line([(CX, 53 + y), (CX, 81)], C('#ffffff', 215), 1.0)
    r.emit(CX, 68 + y, 4.6, '#cfeaff', glow=True)
    for dx, hh in ((-13, 15), (-4, 25), (6, 20), (15, 13)):
        bx = CX + dx
        r.solid([(bx - 4.8, 16 + y), (bx, 16 - hh + y), (bx + 4.8, 16 + y)],
                C('#9ccae8', 248), bevel=1.8, top=1.6, dark=.6, hi=2.0)
        r.poly([(bx - 2.6, 16 + y), (bx, 16 - hh + y), (bx + 1.0, 16 + y)],
               C('#f4fcff', 252))
        r.emit(bx, 16 - hh * .62 + y, 2.6, '#d6f2ff', glow=True, a=195)
    hood(r, t, sh(c, .78), CX, HEAD_CY, 15.0, -12)
    head_skin(r, t, CX, HEAD_CY + 1, 9.6, 10.6, '#cfe2ea')
    r.poly([(CX - 14, 22 + y), (CX + 15, 20 + y), (CX + 14, 27 + y), (CX - 13, 29 + y)],
           sh(c, .84))
    eyes(r, CX, HEAD_CY - 1 + y, '#e8fcff', 5.0, 2.7, 1.4)
    arm(r, t, sh(c, .68), 61, SHOULDER, 78, False)
    sx = 114 + sway(t, .7)
    r.line([(sx, 20), (sx - 1, 144)], '#42596a', 3.2)
    r.solid([(sx - 5.4, 22), (sx - 1, -8), (sx + 5.4, 22)], C('#b8dcf0', 235),
            bevel=2.0, top=1.7, dark=.6, hi=2.1)
    r.poly([(sx - 2.6, 20), (sx - 1, -2), (sx + 1.4, 20)], C('#ffffff', 248))
    for ox, hh in ((-11, 20), (11, 16)):
        r.solid([(sx + ox * .4, 26), (sx + ox, 26 - hh), (sx + ox * .9, 26)],
                C('#a0c8e0', 220), bevel=1.6, top=1.6, dark=.6)
    r.emit(sx - 1, 0, 6.4, '#d8f4ff', glow=True)
    r.ell((sx - 3.8, 24, sx + 3.8, 32), '#7690a4')
    return r.final(rim=('#e4f8ff', .50), shadow=(79, 144, 23, 134), grain=.03)


def hero_blood(t):
    c = ROLE_RGB[9]; r = Rig(1009)
    y = BOB[t & 3]
    cape(r, t, '#7a2b2f', 42 + y, 142, 28, 1.6, 5, trim='#c2585c')
    legs(r, t, '#442a2c', '#2c1c1a', 71.5, 92.5, 1.08)
    torso(r, t, sh(c, .62), '#6d3a35', 'heavy', 19.5)
    r.ring(CX, 64 + y, 11.6, '#d85a5a', 1.6)
    r.solid([(CX, 52 + y), (91, 64), (CX, 78), (75, 64)], '#e05a5a', bevel=1.8, top=1.5, dark=.6)
    r.poly([(CX, 56 + y), (87, 64), (CX, 74), (79, 64)], '#ff9e9e')
    r.emit(CX, 64 + y, 5.4, '#ff8e8e', glow=True)
    arm(r, t, sh(c, .60), 62, SHOULDER, 78, False)
    r.solid([(62, 6 + y), (101, 4 + y), (104, 30 + y), (100, 56 + y),
             (64, 58 + y), (59, 30 + y)], sh(c, .84), bevel=3.4, top=1.34, dark=.5)
    r.poly([(60, 14 + y), (48, -14 + y), (57, -6 + y), (70, 18 + y)], '#48161a')
    r.poly([(106, 13 + y), (120, -16 + y), (110, -8 + y), (96, 17 + y)], '#48161a')
    r.poly([(63, 16 + y), (53, -6 + y), (60, 0 + y), (69, 19 + y)], '#8a3538')
    r.poly([(103, 15 + y), (114, -9 + y), (107, -3 + y), (97, 19 + y)], '#8a3538')
    r.solid([(65, 18 + y), (100, 16 + y), (99, 42 + y), (66, 44 + y)], '#1c1216',
            bevel=2.2, top=1.6, dark=.6)
    r.solid([(73, 40 + y), (96, 39 + y), (94, 54 + y), (75, 55 + y)], sh(c, .48),
            bevel=2.2, top=1.3, dark=.5)
    for i in range(4):
        r.poly([(76 + i * 4.6, 41 + y), (77.9 + i * 4.6, 49 + y), (80 + i * 4.6, 41 + y)],
               '#e8d8d0')
    eyes(r, CX, 27 + y, '#ff9a9a', 5.6, 3.0, 1.7)
    sx = 112 + sway(t, 1.4)
    r.line([(sx + 3, 18), (sx - 3, 144)], '#2e1c1a', 4.2)
    r.line([(sx + 1.2, 20), (sx - 1.8, 142)], '#7a4c48', 1.4)
    r.solid([(sx + 1, 22), (sx + 34, 12), (sx + 50, 42), (sx + 27, 32), (sx + 5, 36)],
            C('#b83c44', 246), bevel=3.0, top=1.5, dark=.5, hi=1.9)
    r.poly([(sx + 2, 25), (sx + 31, 16), (sx + 44, 39), (sx + 25, 30), (sx + 6, 33)],
           C('#ea7272', 236))
    r.poly([(sx + 3, 28), (sx + 27, 20), (sx + 36, 32), (sx + 23, 28), (sx + 6, 30)],
           C('#ffb8b8', 226))
    r.emit(sx + 13, 24, 4.8, '#ff8e8e', glow=True)
    for i in range(4):
        r.poly([(sx + 38 + i * 3, 22 + i * 4.4), (sx + 46 + i * 3, 28 + i * 4.4),
                (sx + 38 + i * 3, 32 + i * 4.4)], C('#ffd4d4', 215))
    for i in range(3):
        r.ell((sx + 20 + i * 5.4, 36 + i * 5.4, sx + 22.6 + i * 5.4, 41 + i * 5.4),
              C('#c84048', 225))
    return r.final(rim=('#ffb0b0', .48), shadow=(80, 144, 26, 152), grain=.06)


def hero_thunder(t):
    c = ROLE_RGB[10]; r = Rig(1010)
    y = BOB[t & 3]
    r.solid([(56, 74 + y), (110, 72 + y), (122, 126), (105, 141), (86, 132), (68, 142),
             (44, 124)], sh(c, .52), bevel=3.2, top=1.28, dark=.5)
    r.poly([(64, 78 + y), (103, 76 + y), (111, 124), (97, 130), (82, 124), (63, 130),
            (52, 120), (59, 98)], sh(c, .68))
    r.line([(65, 80 + y), (57, 122)], sh(c, 1.34), 1.4)
    legs(r, t, '#4a3f2a', '#30281a', 73, 91, .98)
    torso(r, t, sh(c, .74), '#7c6234', 'heavy', 17.5)
    r.line([(65, 52 + y), (101, 78)], '#4a3a22', 6.5)
    for i in range(4):
        px, py = 69 + i * 7.6, 55 + i * 6.4
        r.solid([(px, py), (px + 4.6, py + 1), (px + 3.4, py + 8), (px - 1.4, py + 6.6)],
                '#b89440', bevel=1.4, top=1.5, dark=.55)
    arm(r, t, sh(c, .64), 61, SHOULDER, 78, False)
    r.solid([(56, 26 + y), (109, 23 + y), (111, 36 + y), (54, 39 + y)], '#4a3a22',
            bevel=2.8, top=1.4, dark=.5)
    r.poly([(58, 28 + y), (107, 26 + y), (107, 31.6 + y), (58, 33.6 + y)], '#8c7440')
    r.solid([(65, 6 + y), (100, 4 + y), (102, 28 + y), (99, 55 + y),
             (66, 57 + y), (61, 28 + y)], sh(c, .88), bevel=3.2, top=1.34, dark=.5)
    r.solid([(66, 14 + y), (99, 12 + y), (100, 28 + y), (67, 30 + y)], '#2a2114',
            bevel=2.0, top=1.5, dark=.6)
    r.solid([(68, 30 + y), (97, 28 + y), (96, 42 + y), (69, 44 + y)], '#100e0a',
            bevel=2.0, top=1.7, dark=.6)
    eyes(r, CX, 20 + y, '#ffe9a2', 6.0, 3.0, 1.6)
    r.solid([(69, 40 + y), (97, 38 + y), (95, 51 + y), (71, 53 + y)], '#58472a',
            bevel=2.2, top=1.34, dark=.5)
    gx = 102 + sway(t, 1.1)
    r.solid([(gx - 7, 54 + y), (gx + 9, 52 + y), (gx + 12, 72), (gx - 7, 75)], '#4e4028',
            bevel=2.4, top=1.34, dark=.5)
    r.solid([(gx + 5, 50), (gx + 46, 42), (gx + 46, 62), (gx + 7, 72)], '#5a4a2e',
            bevel=2.6, top=1.34, dark=.5)
    r.poly([(gx + 7, 52), (gx + 44, 44.4), (gx + 44, 50), (gx + 7, 59)], '#8c7440')
    for i in range(3):
        r.solid([(gx + 9 + i * 10.5, 40), (gx + 13.5 + i * 10.5, 39), (gx + 13.5 + i * 10.5, 66),
                 (gx + 9 + i * 10.5, 67)], '#c2a04a', bevel=1.4, top=1.5, dark=.55)
        r.emit(gx + 11.2 + i * 10.5, 44, 2.7, '#fff0a8', glow=True, a=205)
    r.solid([(gx + 44, 42), (gx + 54, 40), (gx + 54, 61), (gx + 44, 63)], '#332a1a',
            bevel=2.2, top=1.4, dark=.6)
    r.emit(gx + 54, 51, 7.4, '#ffe999', glow=True)
    r.emit_line([(gx + 54, 48), (gx + 62, 38), (gx + 57, 50), (gx + 66, 44)], '#fff4c8', 1.6)
    r.solid([(gx + 1, 72), (gx + 13, 70), (gx + 10, 84), (gx + 2, 84)], '#3e3220',
            bevel=2.0, top=1.35, dark=.5)
    return r.final(rim=('#ffefac', .48), shadow=(80, 144, 26, 150), grain=.05)


def hero_void(t):
    c = ROLE_RGB[11]; r = Rig(1011)
    y = BOB[t & 3]
    for i in range(8):
        a = i * math.pi * 2 / 8 + t * .24
        r.poly([(CX + math.cos(a) * 24, 48 + y + math.sin(a) * 26),
                (CX + math.cos(a + .46) * 42, 48 + y + math.sin(a + .46) * 38),
                (CX + math.cos(a + .15) * 26, 48 + y + math.sin(a + .15) * 28)],
               C('#4a2a78', 158))
    cape(r, t, '#33204d', 42 + y, 142, 27, 1.5, 4, trim='#7a5cb8')
    legs(r, t, '#2c2240', '#181226', 72.5, 92, .96)
    torso(r, t, sh(c, .60), '#5a4286', 'robe', 18.0)
    r.solid([(CX, 46 + y), (91, 62), (86, 84), (75, 66)], '#0b050f',
            bevel=2.2, top=1.5, dark=.7)
    r.solid([(CX, 51 + y), (88, 62), (84.6, 80), (78.4, 66)], '#c8a6ff',
            bevel=1.6, top=1.5, dark=.6)
    r.poly([(CX, 56 + y), (84.8, 62), (CX, 76), (81.2, 66)], '#f4ecff')
    r.emit(CX, 64 + y, 6.2, '#c8a6ff', glow=True)
    arm(r, t, sh(c, .62), 62, SHOULDER, 78, False)
    r.solid([(62, 4 + y), (101, 2 + y), (104, 30 + y), (100, 56 + y),
             (64, 58 + y), (59, 30 + y)], '#3e2c5e', bevel=3.4, top=1.34, dark=.5)
    r.poly([(64, 12 + y), (100, 10 + y), (99, 44 + y), (65, 46 + y)], '#0d0716')
    r.poly([(66, 24 + y), (98, 22 + y), (97, 36 + y), (67, 38 + y)], '#1a1030')
    eyes(r, CX, 22 + y, '#d9bcff', 6.0, 3.1, 1.7)
    r.line([(73, 8 + y), (77, 26), (72, 46 + y)], '#8a6ec8', 1.1)
    r.line([(94, 9 + y), (89, 28), (92, 45 + y)], '#6a4ea8', 1.0)
    for i in range(5):
        r.solid([(67 + i * 3.4, 42 + y), (69.6 + i * 3.4, 54 + y), (72.2 + i * 3.4, 42 + y)],
                '#2a1c42' if i % 2 else '#482f6e', bevel=1.2, top=1.4, dark=.5)
    sx = 110 + sway(t, 1.6)
    r.line([(sx + 2, 26), (sx - 2, 108)], '#20122e', 3.8)
    r.solid([(sx, -2), (sx + 10.4, 38), (sx + 2, 41), (sx - 5.4, 38)], C('#9c72ff', 246),
            bevel=2.2, top=1.6, dark=.55, hi=2.0)
    r.poly([(sx, 5), (sx + 5.4, 36), (sx + 1, 38), (sx - 2.6, 36)], C('#e8d9ff', 250))
    r.solid([(sx - 1, 40), (sx + 9.4, 30), (sx + 5.2, 44)], C('#c8a6ff', 215),
            bevel=1.6, top=1.5, dark=.6)
    r.emit(sx, 16, 7.4, '#c8a6ff', glow=True)
    for i in range(5):
        a = t * .5 + i * 1.2566
        px = sx + math.cos(a) * 9
        py = 60 + math.sin(a) * 14
        r.emit_poly([(px - 2.4, py - 1.2), (px + 2.6, py + 2.4), (px - .8, py + 3.6)],
                    '#b995ff', 215)
    return r.final(rim=('#d8c2ff', .52), shadow=(79, 144, 23, 130), grain=.03)


HEROES = [hero_ranger, hero_guard, hero_medic, hero_warlock, hero_alch, hero_shadow,
          hero_mech, hero_bard, hero_frost, hero_blood, hero_thunder, hero_void]
