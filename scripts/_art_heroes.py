"""角色骨架与通用部件 —— 统一比例常量，卡通描边体积。

比例（160x160 单元）：头顶 18.5 / 下巴 41 / 肩 46 / 腰 84 / 髋 88 / 膝 112 / 脚底 142
=> 头高 23px，总高 124px，约 5.4 头身。武器可以溢出到 y=0..160。
"""
import math
from _art_core import Rig, C, sh, mixc, CELL, S

FOOT = 142.0
HIP = 88.0
KNEE = 112.0
WAIST = 84.0
SHOULDER = 46.0
HEAD_CY = 30.0
HEAD_RX = 10.0
HEAD_RY = 11.2
HEAD_TOP = 18.5
CX = 83.0

ROLE_RGB = ['#83EDC6', '#EEBE75', '#93C4FF', '#C2A7FF', '#FFA37B', '#FF91C8',
            '#83D6D9', '#F3D58A', '#A9DBFF', '#FF7A7A', '#FFE58F', '#B79BFF']
ROLE_NAMES = ['游侠', '守卫', '星医', '术士', '炼金', '影舞',
              '机铠', '圣歌', '霜灵', '血誓', '雷铳', '虚空行者']

# 4 帧 = 一个完整步幅：f0 左腿前 / f1 过渡(低) / f2 右腿前 / f3 过渡(高)
# BOB 用两个起伏周期（每个步幅身体沉两次），SWING 正负交替（腿真的换边），
# CAPE 半周期滞后（披风拖尾）。这样 4 帧两两互不相同。
# 4 帧两两互不相同（关键：BOB/SWING 都不能在第 1、3 帧取同值）
#   f0 左腿前 + 身体最低      f1 过渡 + 身体抬到中位
#   f2 右腿前 + 身体次低      f3 过渡 + 身体最高（与 f1 明显不同）
BOB = (-1.10, 0.10, -0.55, 1.05)
SWING = (2.3, 0.7, -2.3, -0.7)
CAPE = (1.6, -0.2, -1.6, 1.0)


def sway(t, amp=1.0):
    return SWING[t & 3] * amp


def cwave(t, amp=1.0):
    return CAPE[t & 3] * amp


def torso(r, t, col, trim, kind='plate', half=18.0, skirt=0.0, y0=SHOULDER, y1=WAIST,
          accent=None):
    yb = BOB[t & 3]
    y0 += yb; y1 += yb
    sh_w = half + 2.5
    waist = half - 4.0
    if skirt > 0:
        pts = [(CX - sh_w, y0 + 5), (CX + sh_w, y0 + 3), (CX + waist + 2, y1),
               (CX + waist + skirt, y1 + skirt * .60), (CX + waist - 2, y1 + skirt),
               (CX - waist - 7, y1 + skirt * .94), (CX - waist - skirt * .74, y1 + skirt * .56),
               (CX - waist - 1, y1)]
    else:
        pts = [(CX - sh_w, y0 + 5), (CX + sh_w, y0 + 3), (CX + waist + 2, y1),
               (CX + waist - 1, y1 + 6), (CX - waist - 1, y1 + 7), (CX - waist - 2, y1)]
    r.solid(pts, col, bevel=3.4, top=1.26, dark=.58, hi=1.50)
    r.poly([(CX - sh_w + .8, y0 + 7), (CX - 4, y0 + 6), (CX - 5, y1 - 3), (CX - waist + 1, y1)],
           sh(col, 1.18))
    r.poly([(CX + 5, y0 + 5), (CX + sh_w - .8, y0 + 4), (CX + waist + 1, y1),
            (CX + 4, y1 - 1)], sh(col, .72))
    if kind == 'heavy':
        for i in range(2):
            yy = y1 - 17 + i * 8
            r.poly([(CX - waist + 3, yy), (CX + waist - 2, yy - 1), (CX + waist - 3, yy + 5),
                    (CX - waist + 3, yy + 6)], sh(col, .62 + i * .05))
            r.line([(CX - waist + 4, yy), (CX + waist - 3, yy - 1)], sh(col, 1.32), 1.0)
        for s in (-1, 1):
            r.solid([(CX + s * sh_w - 4.6, y0 - 2), (CX + s * sh_w + 4.6, y0 - 2),
                     (CX + s * sh_w + 3.4, y0 + 8), (CX + s * sh_w - 3.6, y0 + 8)],
                    sh(trim, 1.0), bevel=2.0, top=1.5, dark=.5)
    elif kind == 'robe':
        r.line([(CX - waist + 1, y0 + 9), (CX - waist - 2, y1 + skirt - 4)],
               sh(col, 1.34), 1.3)
        r.line([(CX + waist - 2, y0 + 7), (CX + waist + skirt * .46, y1 + skirt - 6)],
               sh(col, .52), 1.4)
    else:
        r.line([(CX - waist + 4, y0 + 10), (CX - waist + 3, y1 - 5)], sh(col, 1.30), 1.1)
        r.line([(CX + waist - 3, y0 + 8), (CX + waist - 2, y1 - 5)], sh(col, .52), 1.3)
    if accent:
        r.emit(CX, y0 + 16, 4.0, accent, glow=True)
    r.solid([(CX - 7.6, y0 + 1), (CX + 8.6, y0), (CX + 6.6, y0 + 7), (CX - 6.6, y0 + 8)],
            trim, bevel=1.9, top=1.42, dark=.62)
    r.poly([(CX - 4.8, y0 + 1.8), (CX + 5.8, y0 + 1.2), (CX + 4.8, y0 + 4.6),
            (CX - 4.8, y0 + 5.4)], sh(trim, .40))
    r.solid([(CX - waist - 1, y1), (CX + waist + 1, y1 - 1), (CX + waist + 1, y1 + 7),
             (CX - waist - 1, y1 + 8)], trim, bevel=2.1, top=1.44, dark=.58)
    r.ell((CX - 4.2, y1 - 1, CX + 4.4, y1 + 7), sh(trim, 1.62))
    r.ell((CX - 2.2, y1 + 1.2, CX + 1.2, y1 + 5), sh(trim, .42))
    return y0, y1


def legs(r, t, col, boot, xl=72.5, xr=92.0, thick=1.0, hip=HIP, knee=KNEE, foot=FOOT):
    r.poly([(xl + 1.5, hip - 4), (xr - 1.5, hip - 4), (xr - 1.5, hip + 9), (xl + 1.5, hip + 9)],
           (6, 9, 14, 240))
    for front, xc in ((False, xl), (True, xr)):
        s = SWING[t & 3] * (.9 if front else -.62)
        m = 1.0 if front else .72
        c = sh(col, m); b = sh(boot, m)
        w1 = 5.6 * thick; w2 = 4.4 * thick
        r.solid([(xc - w1 + (1.4 if front else 0), hip), (xc + w1 - (1.8 if front else 0), hip),
                 (xc + w2 - .5 + s * .55, knee), (xc - w2 + .5 + s * .55, knee)],
                c, bevel=2.3, top=1.30, dark=.56)
        r.solid([(xc - w2 + .4 + s * .55, knee - 1), (xc + w2 - .4 + s * .55, knee - 1),
                 (xc + w2 * .80 + s, foot - 7), (xc - w2 * .80 + s, foot - 7)],
                c, bevel=2.1, top=1.24, dark=.54)
        r.solid([(xc - w2 - 1.6 + s, foot - 9), (xc + w2 + 1.6 + s, foot - 9),
                 (xc + w2 + 4.8 + s * 1.4, foot), (xc - w2 - 3.6 + s, foot)],
                b, bevel=2.2, top=1.42, dark=.50)
        r.line([(xc - w2 + .5 + s * .55, knee), (xc + w2 - .7 + s * .55, knee)],
               sh(c, .48), 1.0)
        r.solid([(xc - 3.2 + s * .55, knee - 4.6), (xc + 3.2 + s * .55, knee - 4.6),
                 (xc + 2.4 + s * .55, knee + .8), (xc - 2.4 + s * .55, knee + .8)],
                sh(c, 1.16), bevel=1.5, top=1.38, dark=.62)


def arm(r, t, col, x, y0=SHOULDER, y1=78.0, front=True, thick=1.0, skin='#c2a184',
        hand=True):
    m = 1.0 if front else .70
    s = SWING[t & 3] * (.55 if front else -.42)
    c = sh(col, m)
    w = 4.8 * thick
    ex = x + s
    r.solid([(x - w, y0), (x + w, y0), (ex + w * .88, y1), (ex - w * .88, y1)],
            c, bevel=2.2, top=1.34, dark=.54)
    r.solid([(ex - w * .94, y1 - 9), (ex + w * .94, y1 - 9), (ex + w, y1 + 1.5),
             (ex - w, y1 + 1.5)], sh(c, 1.12), bevel=1.7, top=1.44, dark=.56)
    if hand:
        r.solid([(ex - 3.6, y1 + 1), (ex + 3.6, y1 + 1), (ex + 3.2, y1 + 7.6),
                 (ex - 3.2, y1 + 7.6)], sh(skin, m), bevel=1.5, top=1.32, dark=.56)
    return ex, y1 + 4


def head_skin(r, t, cx=CX, cy=HEAD_CY, rx=HEAD_RX, ry=HEAD_RY, skin='#c9ab8c', neck=True):
    yb = BOB[t & 3]
    cy += yb
    if neck:
        r.solid([(cx - 4.6, cy + ry * .62), (cx + 4.6, cy + ry * .62),
                 (cx + 5.0, cy + ry + 8), (cx - 5.0, cy + ry + 8)],
                sh(skin, .52), bevel=1.8, top=1.2, dark=.5)
    r.gell((cx - rx, cy - ry, cx + rx, cy + ry), skin, 1.16, .52)
    r.poly([(cx + rx * .14, cy - ry * .96), (cx + rx * .98, cy - ry * .48),
            (cx + rx * .84, cy + ry * .72), (cx + rx * .08, cy + ry * .99)], sh(skin, .66))
    r.poly([(cx - rx * .68, cy + ry * .26), (cx + rx * .68, cy + ry * .26),
            (cx + rx * .46, cy + ry * .99), (cx - rx * .46, cy + ry * .99)], sh(skin, .84))
    r.poly([(cx - rx * .56, cy + ry * .80), (cx + rx * .56, cy + ry * .80),
            (cx + rx * .26, cy + ry * 1.0), (cx - rx * .26, cy + ry * 1.0)], '#9c7d63')
    r.ell((cx - rx * .82, cy - ry * .96, cx - rx * .18, cy - ry * .26), sh(skin, 1.32))
    return cx, cy


def hood(r, t, col, cx=CX, cy=HEAD_CY, spread=15.0, tall=-12.0, inner=(12, 17, 25)):
    yb = BOB[t & 3]
    cy += yb
    pts = [(cx - spread, cy + 17), (cx - spread - 1.4, cy - 1), (cx - spread * .62, cy - 12),
           (cx - spread * .08, tall), (cx + spread * .56, cy - 14),
           (cx + spread * .96, cy - 3), (cx + spread, cy + 8),
           (cx + spread * .72, cy + 17), (cx + spread * .34, cy + 18),
           (cx + spread * .36, cy + 2), (cx - spread * .50, cy + 2),
           (cx - spread * .52, cy + 17)]
    r.solid(pts, col, bevel=3.0, top=1.34, dark=.52, hi=1.58)
    r.poly([(cx - spread * .40, cy + 3), (cx + spread * .30, cy + 3),
            (cx + spread * .26, cy + 15), (cx, cy + 19), (cx - spread * .40, cy + 15)],
           C(inner, 255))
    r.poly([(cx - spread * .39, cy + 3), (cx + spread * .29, cy + 3),
            (cx + spread * .26, cy + 6), (cx - spread * .39, cy + 6)], C(sh(inner, .55), 255))
    r.line([(cx - spread * .62, cy - 11), (cx - spread * .08, tall + 2),
            (cx + spread * .5, cy - 12)], sh(col, 1.60), 1.3)
    return cy


def eyes(r, cx, cy, col, gap=5.4, w=3.0, h=1.7, a=255):
    for s in (-1, 1):
        r.emit_poly([(cx + s * gap - w, cy - h), (cx + s * gap + w, cy - h * .5),
                     (cx + s * gap + w * .9, cy + h), (cx + s * gap - w, cy + h * .6)], col, a)
    r.emit(cx - gap, cy, w * .48, col, glow=False, a=a)
    r.emit(cx + gap, cy, w * .48, col, glow=False, a=a)


def cape(r, t, col, y0=SHOULDER - 2, y1=138.0, half=24.0, amp=1.0, fringe=4, trim=None,
         cx=CX):
    w = cwave(t, amp)
    pts = [(cx + half * .14, y0), (cx - half * .48, y0 + 4)]
    for i in range(fringe + 1):
        f = i / fringe
        pts.append((cx - half - 3 - f * 5,
                    y0 + 26 + (y1 - y0 - 26) * (.55 + f * .5) + ((i % 2) * 6 - 3) + w * (.3 + f)))
    pts.append((cx - half * .28, y1 - 32))
    r.solid(pts, sh(col, .84), bevel=2.9, top=1.22, dark=.50)
    inner = [(cx + half * .08, y0 + 3), (cx - half * .38, y0 + 8)]
    for i in range(fringe + 1):
        f = i / fringe
        inner.append((cx - half * .78 + 1 - f * 3,
                      y0 + 30 + (y1 - y0 - 30) * (.55 + f * .46) + ((i % 2) * 4 - 2) +
                      w * (.2 + f * .8)))
    inner.append((cx - 3, y1 - 36))
    r.poly(inner, sh(col, 1.08))
    for k in range(3):
        xx = cx - half * (.28 + k * .19)
        r.line([(xx, y0 + 9 + k * 3), (xx - 6 - k * 2 + w * .6, y1 - 26 - k * 4)],
               sh(col, .60), 1.1)
    if trim:
        r.line([(cx + half * .10, y0 + 1), (cx - half * .46, y0 + 6)], trim, 1.3)


def cloth_gap(r, x0, x1, y0, y1, a=228):
    r.poly([(x0, y0), (x1, y0), (x1 - .4, y1), (x0 + .4, y1)], (6, 9, 14, a))


def w_bow(r, t, col, x=110, y=16, h=112):
    a = sway(t, .9)
    x += a * .5
    pts = [(x + 2, y), (x - 9, y + h * .2), (x - 12, y + h * .5),
           (x - 9, y + h * .8), (x + 2, y + h)]
    r.line(pts, '#5c3c22', 5.6)
    r.line([(p[0] - .8, p[1]) for p in pts], '#b0803f', 2.8)
    r.line([(p[0] - 2.6, p[1]) for p in pts], '#dcb474', 1.0)
    r.line([(x + 2, y + .5), (x + 6.5 - a, y + h * .5), (x + 2, y + h - .5)], '#f0e8cc', 1.0)
    r.solid([(x - 12, y + h * .43), (x - 2, y + h * .41), (x - 2, y + h * .59),
             (x - 12, y + h * .57)], '#4a3220', bevel=1.6, top=1.5, dark=.6)
    r.emit(x - 4, y + h * .5, 2.6, '#d8f8e6', glow=False)
    r.line([(x + 4, y + h * .5), (x + 30, y + h * .48)], '#e6d8b8', 1.3)
    r.poly([(x + 30, y + h * .48), (x + 24, y + h * .42), (x + 24, y + h * .55)], col)


def w_staff(r, t, col, x=112, top=2, bot=144, orb='#cfe4ff', rune=True, glow=True, big=False):
    a = sway(t, .6)
    x += a * .5
    r.line([(x + 1, top + 12), (x, bot)], '#42301e', 4.0)
    r.line([(x - .8, top + 13), (x - 1.4, bot - 2)], '#9c7648', 1.6)
    r.solid([(x - 7.6, top + 15), (x - 3, top + 5), (x + 3, top + 5), (x + 7.6, top + 15),
             (x + 3.6, top + 24), (x - 3.6, top + 24)], '#6f5a40',
            bevel=2.1, top=1.44, dark=.55)
    r.poly([(x - 2.8, top + 24), (x - 8.4, top + 34), (x - 4.6, top + 35), (x, top + 25)],
           '#5e4a33')
    r.poly([(x + 2.8, top + 24), (x + 8.4, top + 34), (x + 4.6, top + 35), (x, top + 25)],
           '#5e4a33')
    if glow:
        rad = 7.2 if big else 6.0
        r.emit(x, top + 15, rad, orb, glow=True)
        r.ring(x, top + 15, rad + 1.6, sh(orb, .50), 1.0)
    if rune:
        for i in range(3):
            yy = top + 42 + i * 18
            r.poly([(x + 4, yy), (x + 8, yy + 2), (x + 8, yy + 4), (x + 4, yy + 6)],
                   sh(col, .95))
            r.emit(x + 6, yy + 3, 1.5, col, glow=False, a=175)


def w_shield(r, t, col, trim, x=106, y=32, w=46, h=64, style='kite'):
    y += BOB[t & 3] * .3
    if style == 'kite':
        pts = [(x - w * .5, y + 2), (x + w * .30, y), (x + w * .47, y + h * .28),
               (x + w * .37, y + h * .72), (x, y + h), (x - w * .40, y + h * .68),
               (x - w * .49, y + h * .26)]
    else:
        pts = [(x - w * .48, y), (x + w * .5, y), (x + w * .46, y + h), (x - w * .5, y + h)]
    r.solid(pts, col, bevel=3.2, top=1.34, dark=.56, hi=1.58)
    r.poly([pts[0], pts[1], (x + 3, y + h * .5), (x - w * .30, y + h * .58)], sh(col, 1.26))
    r.poly([(x + 5, y + 5), pts[2], pts[3], (x + 2, y + h * .72)], sh(col, .68))
    r.line(pts + [pts[0]], trim, 2.1)
    r.line([pts[0], pts[1]], sh(trim, 1.5), 1.3)
    r.line([pts[0], pts[6], pts[5]], sh(trim, 1.3), 1.2)
    ccx, ccy = x, y + h * .40
    r.solid([(ccx, ccy - 11), (ccx + 9, ccy), (ccx, ccy + 11), (ccx - 9, ccy)], trim,
            bevel=2.0, top=1.5, dark=.6)
    r.poly([(ccx, ccy - 6), (ccx + 5, ccy), (ccx, ccy + 6), (ccx - 5, ccy)], sh(col, .32))
    for px, py in [(x - w * .26, y + h * .18), (x + w * .24, y + h * .16),
                   (x - w * .28, y + h * .60), (x + w * .20, y + h * .58)]:
        r.ell((px - 1.8, py - 1.8, px + 1.8, py + 1.8), sh(trim, 1.45))
