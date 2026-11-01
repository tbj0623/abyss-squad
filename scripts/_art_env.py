"""6 生态：地板（1120x620）与主菜单插画（1000x600）。

地板规则：
  - 上/下各 36px、左/右各 22px 是与 Room 碰撞边界对齐的墙体浮雕。
  - 中央战斗区域只放低对比材质，不放高对比图案。
  - 所有随机细节用固定种子，保证可复现。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter, ImageChops

FLOOR_W, FLOOR_H = 1120, 620
COVER_W, COVER_H = 1000, 600
WALL_T = 36
WALL_S = 22

BIOMES = [
    dict(bg=(23, 43, 45), floor=(26, 52, 53), edge=(78, 116, 104), accent=(140, 220, 176),
         deep=(14, 28, 30), warm=(120, 170, 140), name='苔光遗迹'),
    dict(bg=(48, 37, 31), floor=(58, 43, 37), edge=(130, 101, 76), accent=(255, 163, 123),
         deep=(26, 18, 15), warm=(220, 130, 80), name='赤铜熔炉'),
    dict(bg=(32, 36, 58), floor=(40, 42, 67), edge=(95, 98, 144), accent=(147, 196, 255),
         deep=(18, 20, 34), warm=(150, 160, 220), name='月影书库'),
    dict(bg=(26, 39, 51), floor=(33, 50, 68), edge=(110, 155, 184), accent=(169, 219, 255),
         deep=(14, 22, 30), warm=(170, 210, 235), name='霜蚀回廊'),
    dict(bg=(43, 26, 24), floor=(58, 33, 29), edge=(154, 90, 60), accent=(255, 140, 90),
         deep=(22, 12, 11), warm=(240, 120, 60), name='熔火深渊'),
    dict(bg=(27, 23, 48), floor=(36, 30, 62), edge=(122, 100, 176), accent=(194, 167, 255),
         deep=(14, 11, 26), warm=(150, 110, 230), name='虚空王座'),
]


def _sh(c, m):
    return tuple(max(0, min(255, int(v * m))) for v in c[:3])


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def floor_tile(biome, seed=0):
    cfg = BIOMES[biome]
    rnd = random.Random(9000 + biome * 131 + seed)
    w, h = FLOOR_W, FLOOR_H
    bg, fl, edge, acc = cfg['bg'], cfg['floor'], cfg['edge'], cfg['accent']
    deep, warm = cfg['deep'], cfg['warm']

    im = Image.new('RGB', (w, h), bg)
    d = ImageDraw.Draw(im)

    # ── 1. 地砖基础层 ──────────────────────────────────────
    rows = 9
    tile_h = (h - WALL_T * 2) / rows
    for row in range(rows):
        y = WALL_T + row * tile_h
        th = tile_h - 2
        cols = 9
        tile_w = (w - WALL_S * 2) / cols
        for col in range(cols):
            x = WALL_S + col * tile_w + (row % 2) * tile_w * .42
            if x > w - WALL_S - 10:
                continue
            tw = min(tile_w - rnd.uniform(1, 3), w - WALL_S - x)
            if tw < 12:
                continue
            noise = rnd.randint(-7, 7)
            base = tuple(max(0, min(255, c + noise)) for c in fl)
            jit = lambda: rnd.uniform(-2.6, 2.6)
            pts = [(x + 2 + jit(), y + 1 + jit()), (x + tw - 2 + jit(), y + jit()),
                   (x + tw + jit(), y + th - 3 + jit()), (x + tw - 4 + jit(), y + th + jit()),
                   (x + 3 + jit(), y + th + jit()), (x + jit(), y + th - 4 + jit())]
            d.polygon(pts, fill=base, outline=_sh(base, .62))
            d.line([pts[0], pts[1]], fill=_sh(base, 1.28), width=1)
            d.line([pts[1], pts[2]], fill=_sh(base, .80), width=1)
            if biome in (0, 2, 3, 5):
                for _ in range(int(tw * th / 90)):
                    px = rnd.uniform(x + 3, x + tw - 3)
                    py = rnd.uniform(y + 3, y + th - 3)
                    d.point((px, py), fill=_sh(base, rnd.uniform(.82, 1.16)))
            else:
                d.line([(x + 4, y + 4), (x + tw - 6, y + 3)], fill=_sh(base, 1.22), width=1)
                d.line([(x + 4, y + 7), (x + tw - 8, y + 6)], fill=_sh(base, .74), width=1)
            if rnd.random() < .26:
                cx, cy = x + rnd.uniform(8, max(8, tw - 12)), y + 3
                d.line([(cx, cy), (cx + rnd.uniform(-7, 7), cy + 11),
                        (cx + rnd.uniform(-9, 9), cy + 22)], fill=_sh(base, .58), width=1)

    # ── 2. 生态专属材质（差异化明显）────────────────────────
    cx0, cy0 = WALL_S, WALL_T
    cw, ch = w - WALL_S * 2, h - WALL_T * 2
    if biome == 0:  # 苔光遗迹：苔斑、亮孢子、垂根、潮湿反光
        for _ in range(360):
            x = rnd.uniform(cx0 + 6, cx0 + cw - 6)
            y = rnd.uniform(cy0 + 6, cy0 + ch - 6)
            rr = rnd.uniform(3, 13)
            g = rnd.randint(52, 108)
            d.ellipse((x, y, x + rr, y + rr * .68), fill=(18, g, 32))
            d.ellipse((x + rr * .18, y + rr * .12, x + rr * .7, y + rr * .42),
                      fill=(30, g + 26, 42))
        for _ in range(96):  # 孢子（发光点）
            x = rnd.uniform(cx0 + 10, cx0 + cw - 10)
            y = rnd.uniform(cy0 + 10, cy0 + ch - 10)
            d.ellipse((x, y, x + 2.2, y + 2.2), fill=_sh(acc, rnd.uniform(.55, .95)))
        for _ in range(40):  # 垂根
            x = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            y = rnd.choice([cy0 + 4, cy0 + ch - 6])
            sgn = 1 if y < h / 2 else -1
            d.line([(x, y), (x + rnd.uniform(-16, 16), y + sgn * rnd.uniform(26, 66))],
                   fill=_sh(_mix(fl, acc, .20), .78), width=2)
            d.line([(x + 1, y), (x + 2 + rnd.uniform(-14, 14), y + sgn * rnd.uniform(20, 50))],
                   fill=_sh(_mix(fl, acc, .34), .92), width=1)
        for _ in range(90):  # 潮湿反光
            x = rnd.uniform(cx0 + 14, cx0 + cw - 14)
            y = rnd.uniform(cy0 + 14, cy0 + ch - 14)
            d.ellipse((x, y, x + rnd.uniform(6, 22), y + rnd.uniform(2, 5)),
                      fill=_sh(_mix(fl, acc, .16), rnd.uniform(1.0, 1.3)))
    elif biome == 1:  # 赤铜熔炉：铜板、铆钉阵列、炉口、铁轨
        for _ in range(5):  # 炉口光池
            x = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            y = rnd.uniform(cy0 + 60, cy0 + ch - 60)
            d.ellipse((x - 60, y - 30, x + 60, y + 30), fill=_sh(warm, .30))
            d.ellipse((x - 36, y - 18, x + 36, y + 18), fill=_sh(warm, .52))
        for _ in range(210):  # 铜绿斑点
            x = rnd.uniform(cx0, cx0 + cw)
            y = rnd.uniform(cy0, cy0 + ch)
            rr = rnd.uniform(1.4, 4.4)
            d.ellipse((x, y, x + rr, y + rr), fill=_sh(_mix(fl, (70, 130, 110), .5),
                                                       rnd.uniform(.6, 1.0)))
        for gy in range(int(cy0) + 20, int(cy0 + ch), 44):  # 铆钉阵列
            for gx in range(int(cx0) + 24, int(cx0 + cw), 44):
                d.ellipse((gx, gy, gx + 4, gy + 4), fill=_sh(edge, .78))
                d.point((gx + 1, gy + 1), fill=_sh(edge, 1.5))
        for _ in range(22):  # 铁轨/铜条
            x = rnd.uniform(cx0 + 30, cx0 + cw - 30)
            y = rnd.uniform(cy0 + 30, cy0 + ch - 30)
            ln = rnd.uniform(60, 180)
            d.rectangle((x, y, x + ln, y + 5), fill=_sh(edge, .52))
            d.line([(x, y + 1), (x + ln, y + 1)], fill=_sh(edge, 1.3), width=1)
    elif biome == 2:  # 月影书库：星图地纹、书页、符文环、墨迹
        for i in range(3):  # 星图环
            cx2 = w * (.3 + i * .2)
            cy2 = h * .5
            rr = 110 + i * 36
            d.ellipse((cx2 - rr, cy2 - rr * .55, cx2 + rr, cy2 + rr * .55),
                      outline=_sh(_mix(fl, acc, .26), .9), width=1)
            for k in range(16):
                a = k * math.tau / 16
                px, py = cx2 + math.cos(a) * rr, cy2 + math.sin(a) * rr * .55
                d.ellipse((px - 1.6, py - 1.6, px + 1.6, py + 1.6),
                          fill=_sh(_mix(fl, acc, .55), 1.1))
        for _ in range(30):  # 书页
            x = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            y = rnd.uniform(cy0 + 20, cy0 + ch - 20)
            pw, ph = rnd.uniform(7, 14), rnd.uniform(9, 16)
            a = rnd.uniform(-.7, .7)
            pts = [(x + math.cos(a) * dx - math.sin(a) * dy, y + math.sin(a) * dx + math.cos(a) * dy)
                   for dx, dy in ((-pw, -ph), (pw, -ph), (pw, ph), (-pw, ph))]
            col = _sh(_mix(fl, (210, 214, 235), rnd.uniform(.18, .38)), rnd.uniform(.94, 1.14))
            d.polygon(pts, fill=col, outline=_sh(fl, .62))
            d.line(pts[0] + pts[1] if False else [pts[0], pts[1]], fill=_sh(col, 1.2), width=1)
        for _ in range(60):  # 墨迹
            x = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            y = rnd.uniform(cy0 + 20, cy0 + ch - 20)
            d.ellipse((x, y, x + rnd.uniform(3, 9), y + rnd.uniform(2, 6)),
                      fill=_sh(fl, .74))
    elif biome == 3:  # 霜蚀回廊：厚冰面、冰裂纹、霜花、冻住的碎骨
        for _ in range(84):  # 冰面片
            x = rnd.uniform(cx0 + 10, cx0 + cw - 10)
            y = rnd.uniform(cy0 + 10, cy0 + ch - 10)
            rr = rnd.uniform(8, 30)
            pts = [(x + math.cos(a) * rr, y + math.sin(a) * rr * .58)
                   for a in (i * math.pi / 3 for i in range(6))]
            d.polygon(pts, fill=_sh(_mix(fl, acc, rnd.uniform(.08, .22)), rnd.uniform(1.02, 1.16)))
            d.line([pts[0], pts[1]], fill=_sh(acc, .9), width=1)
        for _ in range(34):  # 长冰裂
            x = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            y = rnd.uniform(cy0 + 20, cy0 + ch - 20)
            a0 = rnd.uniform(0, math.tau)
            pts = [(x, y)]
            for k in range(rnd.randint(2, 4)):
                pts.append((pts[-1][0] + math.cos(a0 + rnd.uniform(-.5, .5)) * rnd.uniform(14, 34),
                            pts[-1][1] + math.sin(a0 + rnd.uniform(-.5, .5)) * rnd.uniform(10, 24)))
            d.line(pts, fill=_sh(_mix(fl, (225, 242, 255), .5), .94), width=2)
            d.line(pts, fill=_sh(acc, .74), width=1)
        for _ in range(280):  # 霜花晶粒
            x = rnd.uniform(cx0 + 6, cx0 + cw - 6)
            y = rnd.uniform(cy0 + 6, cy0 + ch - 6)
            d.point((x, y), fill=_sh(_mix(fl, (236, 248, 255), .62), rnd.uniform(.96, 1.24)))
        for _ in range(18):  # 冻住的碎骨
            x = rnd.uniform(cx0 + 24, cx0 + cw - 24)
            y = rnd.uniform(cy0 + 24, cy0 + ch - 24)
            ln = rnd.uniform(8, 20)
            d.line([(x, y), (x + ln, y + rnd.uniform(-5, 5))], fill=_sh(_mix(fl, (200, 200, 190), .5), .9), width=2)
            d.ellipse((x + ln - 2, y - 2, x + ln + 2, y + 2), fill=_sh(fl, 1.1))
    elif biome == 4:  # 熔火深渊：熔岩河、焦黑岩壳、灰烬、火星
        # 熔岩池：椭圆发光核心 + 外圈冷却壳
        for _ in range(11):
            x0_ = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            y0_ = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            rw = rnd.uniform(28, 78)
            rh = rw * rnd.uniform(.42, .62)
            d.ellipse((x0_ - rw - 10, y0_ - rh - 8, x0_ + rw + 10, y0_ + rh + 8),
                      fill=(96, 36, 22))
            d.ellipse((x0_ - rw, y0_ - rh, x0_ + rw, y0_ + rh), fill=(176, 66, 32))
            d.ellipse((x0_ - rw * .62, y0_ - rh * .58, x0_ + rw * .62, y0_ + rh * .58),
                      fill=(226, 106, 48))
            d.ellipse((x0_ - rw * .3, y0_ - rh * .28, x0_ + rw * .3, y0_ + rh * .28),
                      fill=(255, 172, 90))
        # 冷却岩壳（压在熔岩边缘，制造"裂开"感）
        for _ in range(70):
            x0_ = rnd.uniform(cx0, cx0 + cw)
            y0_ = rnd.uniform(cy0, cy0 + ch)
            rw = rnd.uniform(5, 18)
            d.ellipse((x0_, y0_, x0_ + rw, y0_ + rw * .6),
                      fill=_sh(fl, rnd.uniform(.78, 1.12)))
        for _ in range(200):  # 焦黑岩壳
            x = rnd.uniform(cx0, cx0 + cw)
            y = rnd.uniform(cy0, cy0 + ch)
            rr = rnd.uniform(4, 16)
            d.ellipse((x, y, x + rr, y + rr * .64), fill=_sh(fl, rnd.uniform(.66, 1.04)))
        for _ in range(140):  # 灰烬
            x = rnd.uniform(cx0, cx0 + cw)
            y = rnd.uniform(cy0, cy0 + ch)
            d.point((x, y), fill=_sh(_mix(fl, (180, 160, 140), .4), rnd.uniform(.9, 1.4)))
        for _ in range(60):  # 余烬点
            x = rnd.uniform(cx0, cx0 + cw)
            y = rnd.uniform(cy0, cy0 + ch)
            d.ellipse((x, y, x + 2, y + 2), fill=_sh(warm, rnd.uniform(.8, 1.2)))
    else:  # 5 虚空王座：漆黑裂隙、发光符文、悬浮碎屑、星点
        for _ in range(34):  # 裂隙
            x = rnd.uniform(cx0 + 30, cx0 + cw - 30)
            y = rnd.uniform(cy0 + 30, cy0 + ch - 30)
            pts = [(x, y)]
            for k in range(rnd.randint(3, 6)):
                pts.append((pts[-1][0] + rnd.uniform(-34, 34), pts[-1][1] + rnd.uniform(-18, 18)))
            d.line(pts, fill=_sh(deep, .82), width=6)
            d.line(pts, fill=_sh(acc, .52), width=2)
            d.line(pts, fill=_sh(acc, 1.1), width=1)
        for _ in range(78):  # 符文
            x = rnd.uniform(cx0 + 24, cx0 + cw - 24)
            y = rnd.uniform(cy0 + 24, cy0 + ch - 24)
            s_ = rnd.uniform(5, 11)
            col = _sh(acc, rnd.uniform(.30, .62))
            d.ellipse((x - s_, y - s_, x + s_, y + s_), outline=col, width=1)
            d.line([(x - s_ * .55, y), (x + s_ * .55, y)], fill=col, width=1)
            if rnd.random() < .5:
                d.line([(x, y - s_ * .55), (x, y + s_ * .55)], fill=col, width=1)
        for _ in range(200):  # 星点
            x = rnd.uniform(cx0, cx0 + cw)
            y = rnd.uniform(cy0, cy0 + ch)
            d.point((x, y), fill=_sh(_mix(fl, acc, rnd.uniform(.2, .8)), rnd.uniform(.9, 1.3)))
        for _ in range(26):  # 悬浮碎屑
            x = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            y = rnd.uniform(cy0 + 20, cy0 + ch - 20)
            s_ = rnd.uniform(3, 9)
            d.polygon([(x, y - s_), (x + s_ * .8, y), (x, y + s_), (x - s_ * .8, y)],
                      fill=_sh(_mix(fl, acc, .22), 1.1))

    # ── 3. 方向性光照衰减 ─────────────────────────────────
    light = Image.new('L', (w, h))
    ld = ImageDraw.Draw(light)
    for yy in range(h):
        base = 232 + 22 * math.sin(math.pi * yy / h) * .5
        ld.line((0, yy, w, yy), fill=int(base))
    light = light.filter(ImageFilter.GaussianBlur(24))
    im = ImageChops.multiply(im, Image.merge('RGB', (light, light, light)))

    glow = Image.new('L', (w, h), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-160, -120, w + 160, h + 160), fill=68)
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    im = ImageChops.add(im, Image.merge('RGB', (
        glow.point(lambda v: int(v * .62)), glow.point(lambda v: int(v * .70)),
        glow.point(lambda v: int(v * .80)))))

    d = ImageDraw.Draw(im)
    # ── 4. 墙体浮雕 ────────────────────────────────────────
    for side in (0, 1):
        yy = 0 if side == 0 else h - WALL_T
        d.rectangle((0, yy, w, yy + WALL_T), fill=_sh(edge, .34))
        for x in range(0, w, 74):
            bw = min(70, w - x)
            d.rectangle((x + 2, yy + 2, x + bw - 2, yy + WALL_T - 3), fill=_sh(edge, .50))
            d.rectangle((x + 4, yy + 4, x + bw - 4, yy + 13), fill=_sh(edge, .68))
            d.line([(x + 5, yy + 4), (x + bw - 5, yy + 4)],
                   fill=_sh(edge, .88 if side == 0 else 1.0), width=1)
            d.line([(x, yy), (x, yy + WALL_T)], fill=_sh(edge, .26), width=2)
            rw = random.Random(x + side * 17 + biome)
            if biome == 0:
                for _ in range(4):
                    px = x + rw.uniform(6, max(7, bw - 8))
                    d.ellipse((px, yy + 20, px + 3, yy + 24), fill=(38, 74, 50))
            elif biome == 1:
                if bw > 22: d.rectangle((x + 6, yy + 18, x + bw - 8, yy + 22), fill=_sh(warm, .34))
            elif biome == 2:
                d.line([(x + 10, yy + 22), (x + bw - 12, yy + 22)], fill=_sh(edge, .70), width=1)
            elif biome == 3:
                for _ in range(3):
                    px = x + rw.uniform(6, max(7, bw - 10))
                    d.polygon([(px, yy + 16), (px + 3, yy + 26), (px - 3, yy + 26)],
                              fill=_sh(acc, .46))
            elif biome == 4:
                d.line([(x + 8, yy + 20), (x + bw - 10, yy + 24)], fill=_sh(warm, .40), width=2)
            else:
                d.ellipse((x + bw / 2 - 4, yy + 18, x + bw / 2 + 4, yy + 26),
                          outline=_sh(acc, .34), width=1)
    for side in (0, 1):
        xx = 0 if side == 0 else w - WALL_S
        d.rectangle((xx, 0, xx + WALL_S, h), fill=_sh(edge, .32))
        for y in range(0, h, 66):
            d.rectangle((xx + 2, y + 2, xx + WALL_S - 3, y + 62), fill=_sh(edge, .40))
            d.line([(xx + 4, y + 4), (xx + WALL_S - 4, y + 4)], fill=_sh(edge, .72), width=1)
    d.rectangle((WALL_S, WALL_T, w - WALL_S, h - WALL_T),
                outline=_sh(deep, .8), width=2)
    d.rectangle((WALL_S + 2, WALL_T + 2, w - WALL_S - 2, h - WALL_T - 2),
                outline=_sh(edge, .34), width=1)
    return im


def cover_art(biome):
    cfg = BIOMES[biome]
    rnd = random.Random(4400 + biome * 97)
    w, h = COVER_W, COVER_H
    bg, fl, edge, acc = cfg['bg'], cfg['floor'], cfg['edge'], cfg['accent']
    deep, warm = cfg['deep'], cfg['warm']

    im = Image.new('RGB', (w, h), deep)
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        col = _mix(_sh(bg, .62), _sh(bg, 1.16), t ** .8)
        d.line((0, y, w, y), fill=col)

    for i in range(7):
        x = -60 + i * 160 + rnd.uniform(-30, 30)
        hh = rnd.uniform(180, 340)
        d.polygon([(x, h), (x + 40, h - hh * .72), (x + 96, h - hh),
                   (x + 150, h - hh * .66), (x + 190, h)], fill=_sh(bg, .52))

    ax, ay, aw, ah = w * .5, h * .58, 300, 420
    d.rectangle((ax - aw / 2, ay - ah * .34, ax + aw / 2, h), fill=_sh(fl, .78))
    d.pieslice((ax - aw / 2, ay - ah, ax + aw / 2, ay + ah * .2), 180, 360,
               fill=_sh(deep, 1.06))
    for i in range(15):
        a = math.pi + i * math.pi / 15
        b = math.pi + (i + 1) * math.pi / 15
        rr, ri = aw / 2 + 28, aw / 2 - 2
        pts = [(ax + math.cos(a) * rr, ay - 4 + math.sin(a) * rr),
               (ax + math.cos(b) * rr, ay - 4 + math.sin(b) * rr),
               (ax + math.cos(b) * ri, ay - 4 + math.sin(b) * ri),
               (ax + math.cos(a) * ri, ay - 4 + math.sin(a) * ri)]
        c = _sh(edge, rnd.uniform(.62, .92))
        d.polygon(pts, fill=c, outline=_sh(edge, .38))
        d.line([pts[0], pts[1]], fill=_sh(c, 1.3), width=2)
    for s in (-1, 1):
        px = ax + s * (aw / 2 + 44)
        d.rectangle((px - 22, ay - 60, px + 22, h), fill=_sh(edge, .48))
        for k in range(7):
            d.rectangle((px - 18, ay - 50 + k * 60, px + 18, ay - 30 + k * 60),
                        fill=_sh(edge, .60), outline=_sh(edge, .34))

    if biome == 0:
        for s in (-1, 1):
            x = ax + s * 240
            for k in range(4):
                pts = [(x, h), (x - s * 40, h - 120 - k * 40), (x + s * 20, h - 240 - k * 46),
                       (x - s * 30, h - 360 - k * 30)]
                d.line(pts, fill=_sh(_mix(fl, (40, 82, 56), .5), .8), width=18 - k * 3)
    elif biome == 1:
        for i in range(3):
            yy = 120 + i * 120
            d.rectangle((0, yy, w, yy + 26), fill=_sh(edge, .40))
            for x in range(20, w, 90):
                d.rectangle((x, yy + 4, x + 16, yy + 22), fill=_sh(edge, .58))
        d.ellipse((ax - 90, ay - 90, ax + 90, ay + 90), fill=_sh(warm, .5))
        d.ellipse((ax - 60, ay - 60, ax + 60, ay + 60), fill=_sh(warm, .95))
    elif biome == 2:
        for s in (-1, 1):
            x = ax + s * 280
            for row in range(4):
                yy = 90 + row * 110
                d.rectangle((x - 90, yy, x + 90, yy + 96), fill=_sh(edge, .34))
                for k in range(9):
                    bh = rnd.uniform(46, 78)
                    col = _sh(_mix(fl, edge, rnd.uniform(.2, .7)), rnd.uniform(.8, 1.2))
                    d.rectangle((x - 84 + k * 19, yy + 92 - bh, x - 70 + k * 19, yy + 92),
                                fill=col)
        for _ in range(26):
            x, y = rnd.uniform(80, w - 80), rnd.uniform(80, h - 120)
            s = rnd.uniform(6, 13)
            d.polygon([(x - s, y - s), (x + s, y - s * 1.2), (x + s, y + s), (x - s, y + s * .8)],
                      fill=_mix(fl, (200, 200, 230), .4))
    elif biome == 3:
        for i in range(9):
            x = rnd.uniform(40, w - 40)
            hh = rnd.uniform(60, 190)
            d.polygon([(x - 22, 0), (x, hh), (x + 22, 0)], fill=_sh(_mix(edge, acc, .3), .8))
            d.polygon([(x - 8, 0), (x, hh * .8), (x + 4, 0)], fill=_sh(acc, .58))
        for _ in range(120):
            x, y = rnd.uniform(0, w), rnd.uniform(0, h)
            d.ellipse((x, y, x + 2, y + 2), fill=_sh(acc, rnd.uniform(.5, .9)))
    elif biome == 4:
        for i in range(5):
            x = ax - 200 + i * 100
            d.polygon([(x, 0), (x + 30, 0), (x + 40, h), (x - 20, h)], fill=_sh(warm, .38))
            d.polygon([(x + 8, 0), (x + 18, 0), (x + 24, h), (x + 2, h)], fill=_sh(warm, .78))
        d.ellipse((0, h - 130, w, h + 60), fill=_sh(warm, .5))
    else:
        for i in range(22):
            x, y = rnd.uniform(0, w), rnd.uniform(0, h)
            s = rnd.uniform(10, 44)
            pts = [(x, y - s), (x + s * .8, y), (x, y + s), (x - s * .8, y)]
            d.polygon(pts, fill=_sh(_mix(deep, acc, .12), rnd.uniform(.7, 1.1)))
        for i in range(4):
            x = rnd.uniform(60, w - 60)
            d.line([(x, 0), (x + rnd.uniform(-40, 40), h / 2),
                    (x + rnd.uniform(-60, 60), h)], fill=_sh(acc, .30), width=3)

    glow = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((ax - 260, ay - 260, ax + 260, ay + 300), fill=(*_sh(acc, 1.0), 54))
    glow = glow.filter(ImageFilter.GaussianBlur(80))
    im = Image.alpha_composite(im.convert('RGBA'), glow).convert('RGB')
    d = ImageDraw.Draw(im)

    for i in range(3):
        fog = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        fd = ImageDraw.Draw(fog)
        fy = h * (.30 + i * .18)
        fd.ellipse((-200, fy - 90, w + 200, fy + 130), fill=(*_sh(bg, 1.5), 26 - i * 6))
        fog = fog.filter(ImageFilter.GaussianBlur(60))
        im = Image.alpha_composite(im.convert('RGBA'), fog).convert('RGB')
    d = ImageDraw.Draw(im)

    for i in range(16):
        x = rnd.uniform(0, w)
        y = h - rnd.uniform(0, 70)
        ww = rnd.uniform(30, 90)
        col = _sh(deep, rnd.uniform(1.1, 1.7))
        d.polygon([(x, y), (x + ww, y - rnd.uniform(8, 22)),
                   (x + ww + 10, h), (x - 10, h)], fill=col)
        d.line([(x + 2, y + 1), (x + ww, y - 10)], fill=_sh(edge, .46), width=2)
    if biome in (0, 2):
        for s in (-1, 1):
            x = ax + s * 330
            for k in range(3):
                d.line([(x, h), (x + s * 30, h - 90 - k * 30), (x - s * 10, h - 180 - k * 20)],
                       fill=_sh(_mix(deep, (40, 80, 60), .6), 1.0), width=8 - k * 2)

    vg = Image.new('L', (w, h), 0)
    vd = ImageDraw.Draw(vg)
    vd.rectangle((0, 0, w, h), fill=255)
    vd.ellipse((-w * .2, -h * .25, w * 1.2, h * 1.25), fill=0)
    vg = vg.filter(ImageFilter.GaussianBlur(110))
    dark = Image.new('RGB', (w, h), deep)
    im = Image.composite(im, dark, vg.point(lambda v: 255 - int(v * .82)))
    return im
