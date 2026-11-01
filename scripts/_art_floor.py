"""重写地板生成：解决 v1 的三个观感问题。

1. **苔点像彩纸**：原来 360 个均匀随机的高饱和绿点，现在改成 14~18 个
   「苔斑簇」——每簇由几十个低对比、多色阶的软边小块叠成，边缘羽化。
2. **地砖太齐整**：每块砖独立抽取明度/脏污/湿度，并叠加 4 个尺度的大色块，
   打破"平铺贴图"感。
3. **缺深度**：补三类必杀技——墙角 AO 暗角、墙面对地面的投影带、
   砖缝里的积灰。
4. **顶部/底部的"毛发"竖线**（v1 的垂根）被移除，改成贴墙的苔藓堆。
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter, ImageChops

FLOOR_W, FLOOR_H = 1120, 620
COVER_W, COVER_H = 1000, 600
WALL_T = 36
WALL_S = 22

BIOMES = [
    dict(bg=(23, 43, 45), floor=(30, 58, 58), edge=(78, 116, 104), accent=(140, 220, 176),
         deep=(12, 24, 26), warm=(120, 170, 140), name='苔光遗迹'),
    dict(bg=(48, 37, 31), floor=(64, 47, 40), edge=(130, 101, 76), accent=(255, 163, 123),
         deep=(24, 16, 14), warm=(220, 130, 80), name='赤铜熔炉'),
    dict(bg=(32, 36, 58), floor=(44, 46, 72), edge=(95, 98, 144), accent=(147, 196, 255),
         deep=(16, 18, 32), warm=(150, 160, 220), name='月影书库'),
    dict(bg=(26, 39, 51), floor=(37, 56, 74), edge=(110, 155, 184), accent=(169, 219, 255),
         deep=(12, 20, 28), warm=(170, 210, 235), name='霜蚀回廊'),
    dict(bg=(43, 26, 24), floor=(64, 36, 31), edge=(154, 90, 60), accent=(255, 140, 90),
         deep=(20, 10, 9), warm=(240, 120, 60), name='熔火深渊'),
    dict(bg=(27, 23, 48), floor=(40, 33, 68), edge=(122, 100, 176), accent=(194, 167, 255),
         deep=(12, 9, 24), warm=(150, 110, 230), name='虚空王座'),
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

    # ══════════════════════════════════════════════════════════
    # 1. 底色 + 大尺度色块（湿/干、明/暗），打破平铺感
    # ══════════════════════════════════════════════════════════
    im = Image.new('RGB', (w, h), fl)
    blot = Image.new('L', (w, h), 0)
    bd = ImageDraw.Draw(blot)
    for _ in range(9):
        x = rnd.uniform(-100, w + 100)
        y = rnd.uniform(-80, h + 80)
        rx = rnd.uniform(160, 420)
        ry = rx * rnd.uniform(.45, .8)
        bd.ellipse((x - rx, y - ry, x + rx, y + ry), fill=rnd.randint(40, 110))
    blot = blot.filter(ImageFilter.GaussianBlur(70))
    dark_layer = Image.new('RGB', (w, h), _sh(fl, .74))
    im = Image.composite(dark_layer, im, blot.point(lambda v: int(v * 1.3)))
    d = ImageDraw.Draw(im)

    # ══════════════════════════════════════════════════════════
    # 2. 地砖：每块独立 tone / 脏污 / 磨损
    # ══════════════════════════════════════════════════════════
    rows, cols = 9, 9
    tile_h = (h - WALL_T * 2) / rows
    tile_w = (w - WALL_S * 2) / cols
    tiles = []
    for row in range(rows):
        y = WALL_T + row * tile_h
        th = tile_h - 1.6
        for col in range(cols):
            x = WALL_S + col * tile_w + (row % 2) * tile_w * .42
            if x > w - WALL_S - 14:
                continue
            tw = min(tile_w - rnd.uniform(1.2, 3.2), w - WALL_S - x)
            if tw < 14:
                continue
            tiles.append((x, y, tw, th))

    for (x, y, tw, th) in tiles:
        tone = rnd.uniform(.86, 1.12)          # 每块砖独立明度
        dirty = rnd.random()                   # 脏污程度
        base = _sh(fl, tone)
        jit = lambda k=2.2: rnd.uniform(-k, k)
        pts = [(x + 2 + jit(), y + 1 + jit()), (x + tw - 2 + jit(), y + jit()),
               (x + tw + jit(), y + th - 3 + jit()), (x + tw - 4 + jit(), y + th + jit()),
               (x + 3 + jit(), y + th + jit()), (x + jit(), y + th - 4 + jit())]
        d.polygon(pts, fill=base)
        # 砖块内侧四边：上亮、下暗、右中缝
        d.line([pts[0], pts[1]], fill=_sh(base, 1.30), width=1)
        d.line([pts[1], pts[2]], fill=_sh(base, .84), width=1)
        d.line([pts[3], pts[4], pts[5]], fill=_sh(base, .62), width=1)
        d.line([pts[5], pts[0]], fill=_sh(base, 1.10), width=1)
        # 砖面材质
        if biome in (0, 2, 3, 5):
            for _ in range(int(tw * th / 70)):
                px = rnd.uniform(x + 3, x + tw - 3)
                py = rnd.uniform(y + 3, y + th - 3)
                d.point((px, py), fill=_sh(base, rnd.uniform(.86, 1.14)))
        else:
            d.line([(x + 5, y + 5), (x + tw - 7, y + 4)], fill=_sh(base, 1.20), width=1)
            d.line([(x + 5, y + 8), (x + tw - 9, y + 7)], fill=_sh(base, .78), width=1)
        # 脏污：从砖角往内渗的暗斑
        if dirty > .48:
            gx = x + rnd.choice([4, tw - 12])
            gy = y + rnd.choice([3, th - 12])
            d.ellipse((gx - 8, gy - 6, gx + 20, gy + 14), fill=_sh(base, rnd.uniform(.80, .90)))
        # 磨损裂纹
        if rnd.random() < .22:
            cx = x + rnd.uniform(8, max(9, tw - 14))
            cy = y + rnd.uniform(3, max(4, th - 16))
            cpts = [(cx, cy)]
            for _ in range(rnd.randint(2, 4)):
                cpts.append((cpts[-1][0] + rnd.uniform(-6, 6), cpts[-1][1] + rnd.uniform(4, 11)))
            d.line(cpts, fill=_sh(base, .60), width=1)

    # 砖缝压实（砖块之间的暗线，让格子更立得住）
    seam = Image.new('L', (w, h), 0)
    sd = ImageDraw.Draw(seam)
    for (x, y, tw, th) in tiles:
        sd.rectangle((x - 1, y - 1, x + tw + .5, y + th + .5), outline=110, width=1)
    seam = seam.filter(ImageFilter.GaussianBlur(.6))
    im = Image.composite(Image.new('RGB', (w, h), deep), im, seam)

    # ══════════════════════════════════════════════════════════
    # 3. 生态材质（全部成簇，不再均匀撒点）
    # ══════════════════════════════════════════════════════════
    d = ImageDraw.Draw(im)
    cx0, cy0 = WALL_S, WALL_T
    cw, ch = w - WALL_S * 2, h - WALL_T * 2

    def cluster(n_clusters, n_blobs, rmin, rmax, tones, alpha_edges=True):
        """在场地内画 n_clusters 个软边斑块簇。"""
        for _ in range(n_clusters):
            ccx = rnd.uniform(cx0 + 30, cx0 + cw - 30)
            ccy = rnd.uniform(cy0 + 30, cy0 + ch - 30)
            spread = rnd.uniform(26, 78)
            for _ in range(n_blobs):
                a = rnd.uniform(0, math.tau)
                rr = rnd.uniform(0, spread) ** .7
                px = ccx + math.cos(a) * rr
                py = ccy + math.sin(a) * rr * .62
                br = rnd.uniform(rmin, rmax) * (1.15 - rr / max(1, spread) * .55)
                tone = rnd.choice(tones) if isinstance(tones, list) else tones
                c = _sh(_mix(fl, tone, rnd.uniform(.45, .9)), rnd.uniform(.95, 1.1))
                d.ellipse((px - br, py - br * .7, px + br, py + br * .7), fill=c)

    if biome == 0:  # 苔光遗迹：苔藓成片 + 积水反光 + 孢子微光
        cluster(17, 40, 2.6, 8.4, [(60, 120, 74), (46, 96, 60), (78, 140, 88)])
        cluster(6, 26, 3.0, 9.0, [(40, 88, 62)])
        # 积水：湿润反光（略深的同色相 + 顶部弧形高光，不是黑洞）
        for _ in range(7):
            px = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            py = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            rw = rnd.uniform(24, 54)
            rh = rw * rnd.uniform(.30, .44)
            d.ellipse((px - rw, py - rh, px + rw, py + rh),
                      fill=_sh(_mix(fl, (34, 76, 84), .52), .94))
            d.ellipse((px - rw, py - rh, px + rw, py + rh),
                      outline=_sh(_mix(fl, acc, .30), 1.02), width=1)
            d.arc((px - rw * .86, py - rh * .92, px + rw * .34, py + rh * .22), 195, 330,
                  fill=_sh(_mix(fl, acc, .62), 1.14), width=2)
        # 孢子只在苔藓附近
        for _ in range(70):
            px = rnd.uniform(cx0 + 40, cx0 + cw - 40)
            py = rnd.uniform(cy0 + 40, cy0 + ch - 40)
            d.ellipse((px, py, px + 1.6, py + 1.6), fill=_sh(acc, rnd.uniform(.5, .78)))

    elif biome == 1:  # 赤铜熔炉：铜锈成片 + 铁锈流 + 铆钉 + 炉渣
        cluster(12, 30, 2.4, 7.0, [(64, 126, 108), (52, 108, 96)])
        cluster(9, 22, 3.0, 8.0, [(150, 82, 44), (176, 100, 56)])
        # 锈迹流痕（竖向）
        for _ in range(22):
            px = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            py = rnd.uniform(cy0 + 10, cy0 + ch - 60)
            ln = rnd.uniform(20, 70)
            d.line([(px, py), (px + rnd.uniform(-4, 4), py + ln)],
                   fill=_sh((150, 92, 52), rnd.uniform(.8, 1.15)), width=rnd.choice([1, 2]))
        # 铆钉阵列（压暗，不抢眼）
        for gy in range(int(cy0) + 26, int(cy0 + ch), 56):
            for gx in range(int(cx0) + 34, int(cx0 + cw), 56):
                d.ellipse((gx, gy, gx + 3.4, gy + 3.4), fill=_sh(edge, .62))
                d.point((gx + 1, gy + 1), fill=_sh(edge, 1.15))
        # 炉渣坑：浅凹（比地面深 20~24%），带受光坑沿与坑底余温——不是黑洞
        for _ in range(13):
            px = rnd.uniform(cx0 + 40, cx0 + cw - 40)
            py = rnd.uniform(cy0 + 40, cy0 + ch - 40)
            rw = rnd.uniform(9, 22)
            rh = rw * rnd.uniform(.42, .58)
            base_pit = _sh(_mix(fl, (120, 88, 62), .30), rnd.uniform(.94, 1.06))
            # 冷却壳
            d.ellipse((px - rw - 3, py - rh - 2.4, px + rw + 3, py + rh + 2.4),
                      fill=_sh(base_pit, 1.06))
            # 浅坑
            pit = _sh(base_pit, rnd.uniform(.74, .80))
            d.ellipse((px - rw, py - rh, px + rw, py + rh), fill=pit)
            # 坑底余温
            if rnd.random() < .38:
                d.ellipse((px - rw * .40, py - rh * .34, px + rw * .40, py + rh * .34),
                          fill=_sh(_mix(pit, warm, .30), 1.02))
            # 受光坑沿（左上弧）
            d.arc((px - rw - .8, py - rh - .8, px + rw + .8, py + rh + .8), 168, 304,
                  fill=_sh(_mix(base_pit, edge, .46), 1.16), width=1)
            # 背光坑壁（右下弧）
            d.arc((px - rw + .4, py - rh + .4, px + rw - .4, py + rh - .4), 5, 140,
                  fill=_sh(pit, .80), width=1)
        # 余烬微光
        for _ in range(46):
            px = rnd.uniform(cx0, cx0 + cw)
            py = rnd.uniform(cy0, cy0 + ch)
            d.ellipse((px, py, px + 1.8, py + 1.8), fill=_sh(warm, rnd.uniform(.4, .68)))

    elif biome == 2:  # 月影书库：星图刻线 + 墨渍 + 书页
        # 星图：同心圆 + 星座连线（低对比，刻在地面上）
        for k in range(3):
            ccx = w * (.26 + k * .24)
            ccy = h * .52
            rr = 96 + k * 30
            d.ellipse((ccx - rr, ccy - rr * .56, ccx + rr, ccy + rr * .56),
                      outline=_sh(_mix(fl, acc, .22), .92), width=1)
        stars = []
        for _ in range(26):
            px = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            py = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            stars.append((px, py, rnd.uniform(1.4, 2.6)))
        for i, (px, py, sr) in enumerate(stars):
            for j in range(i + 1, min(i + 3, len(stars))):
                qx, qy, _ = stars[j]
                if abs(px - qx) + abs(py - qy) < 150:
                    d.line([(px, py), (qx, qy)], fill=_sh(_mix(fl, acc, .16), .92), width=1)
            d.ellipse((px - sr, py - sr, px + sr, py + sr),
                      fill=_sh(_mix(fl, acc, .5), 1.05))
        # 墨渍成片
        cluster(11, 26, 3.0, 9.0, [(18, 20, 40), (24, 26, 52)])
        # 散落书页（带投影）
        for _ in range(22):
            px = rnd.uniform(cx0 + 40, cx0 + cw - 40)
            py = rnd.uniform(cy0 + 40, cy0 + ch - 40)
            pw, ph = rnd.uniform(6, 12), rnd.uniform(8, 15)
            a = rnd.uniform(-.8, .8)
            pts = [(px + math.cos(a) * dx - math.sin(a) * dy,
                    py + math.sin(a) * dx + math.cos(a) * dy)
                   for dx, dy in ((-pw, -ph), (pw, -ph), (pw, ph), (-pw, ph))]
            d.polygon([(p[0] + 2, p[1] + 3) for p in pts], fill=_sh(deep, 1.15))
            d.polygon(pts, fill=_sh(_mix(fl, (206, 210, 232), rnd.uniform(.26, .44)), 1.02),
                      outline=_sh(fl, .70))
            d.line([pts[0], pts[1]], fill=_sh(_mix(fl, (220, 224, 240), .5), 1.1), width=1)

    elif biome == 3:  # 霜蚀回廊：厚冰面 + 长冰裂 + 霜花 + 冻骨
        # 冰面片（多边形，成簇）
        cluster(15, 22, 5.0, 15.0, [(70, 110, 140), (92, 136, 168)])
        # 冰裂：短、直的折线（低对比），只在起点给一处细高光
        for _ in range(13):
            ccx = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            ccy = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            for k in range(rnd.randint(2, 3)):
                a0 = rnd.uniform(0, math.tau)
                pts = [(ccx, ccy)]
                for _ in range(rnd.randint(2, 3)):
                    a0 += rnd.uniform(-.20, .20)
                    pts.append((pts[-1][0] + math.cos(a0) * rnd.uniform(20, 46),
                                pts[-1][1] + math.sin(a0) * rnd.uniform(14, 30)))
                # 暗一点的裂谷
                d.line(pts, fill=_sh(_mix(fl, (128, 168, 196), .58), .96), width=2)
                # 单侧细高光
                d.line([(p[0] + .8, p[1] + .8) for p in pts],
                       fill=_sh(_mix(fl, (204, 230, 246), .40), 1.0), width=1)
        # 霜花晶粒（贴近冰缝）
        for _ in range(300):
            px = rnd.uniform(cx0 + 8, cx0 + cw - 8)
            py = rnd.uniform(cy0 + 8, cy0 + ch - 8)
            d.point((px, py), fill=_sh(_mix(fl, (236, 248, 255), .66), rnd.uniform(.94, 1.2)))
        # 冻住的碎骨
        for _ in range(16):
            px = rnd.uniform(cx0 + 30, cx0 + cw - 30)
            py = rnd.uniform(cy0 + 30, cy0 + ch - 30)
            ln = rnd.uniform(8, 20)
            ang = rnd.uniform(0, math.tau)
            ex, ey = px + math.cos(ang) * ln, py + math.sin(ang) * ln
            d.line([(px, py), (ex, ey)], fill=_sh(_mix(fl, (198, 198, 190), .5), .92), width=2)
            d.ellipse((ex - 2, ey - 2, ex + 2, ey + 2), fill=_sh(fl, 1.14))

    elif biome == 4:  # 熔火深渊：熔岩池 + 冷却岩壳 + 灰烬
        # 冷却岩壳（先铺，作为底）
        cluster(14, 26, 6.0, 20.0, [(38, 22, 19), (48, 28, 23)])
        # 熔岩池：多层椭圆发光核心
        for _ in range(9):
            px = rnd.uniform(cx0 + 70, cx0 + cw - 70)
            py = rnd.uniform(cy0 + 60, cy0 + ch - 60)
            rw = rnd.uniform(26, 62)
            rh = rw * rnd.uniform(.4, .6)
            d.ellipse((px - rw - 12, py - rh - 10, px + rw + 12, py + rh + 10),
                      fill=(84, 32, 20))
            d.ellipse((px - rw, py - rh, px + rw, py + rh), fill=(158, 58, 28))
            d.ellipse((px - rw * .66, py - rh * .62, px + rw * .66, py + rh * .62),
                      fill=(214, 96, 44))
            d.ellipse((px - rw * .34, py - rh * .3, px + rw * .34, py + rh * .3),
                      fill=(250, 164, 84))
        # 焦黑岩块（压在熔岩边缘）
        for _ in range(60):
            px = rnd.uniform(cx0, cx0 + cw)
            py = rnd.uniform(cy0, cy0 + ch)
            rw = rnd.uniform(5, 16)
            d.ellipse((px, py, px + rw, py + rw * .58),
                      fill=_sh(fl, rnd.uniform(.76, 1.08)))
        # 灰烬
        for _ in range(160):
            px = rnd.uniform(cx0, cx0 + cw)
            py = rnd.uniform(cy0, cy0 + ch)
            d.point((px, py), fill=_sh(_mix(fl, (176, 156, 138), .42), rnd.uniform(.9, 1.3)))

    else:  # 虚空王座：裂隙 + 发光符文 + 星点 + 悬浮碎屑
        # 裂隙簇
        for _ in range(11):
            ccx = rnd.uniform(cx0 + 50, cx0 + cw - 50)
            ccy = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            for k in range(rnd.randint(2, 4)):
                a0 = rnd.uniform(0, math.tau)
                pts = [(ccx, ccy)]
                for _ in range(rnd.randint(2, 4)):
                    a0 += rnd.uniform(-.5, .5)
                    pts.append((pts[-1][0] + math.cos(a0) * rnd.uniform(16, 40),
                                pts[-1][1] + math.sin(a0) * rnd.uniform(10, 26)))
                d.line(pts, fill=_sh(deep, .86), width=5)
                d.line(pts, fill=_sh(acc, .44), width=2)
                d.line(pts, fill=_sh(acc, .96), width=1)
        # 符文环（成组，像法阵碎片）
        for _ in range(7):
            ccx = rnd.uniform(cx0 + 60, cx0 + cw - 60)
            ccy = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            rr = rnd.uniform(26, 54)
            d.arc((ccx - rr, ccy - rr * .5, ccx + rr, ccy + rr * .5),
                  rnd.uniform(0, 180), rnd.uniform(200, 360),
                  fill=_sh(acc, rnd.uniform(.28, .46)), width=1)
            for k in range(rnd.randint(3, 6)):
                a = k * 1.05 + rnd.uniform(0, .4)
                px = ccx + math.cos(a) * rr
                py = ccy + math.sin(a) * rr * .5
                d.ellipse((px - 3, py - 3, px + 3, py + 3),
                          outline=_sh(acc, rnd.uniform(.30, .5)), width=1)
        # 星点
        for _ in range(220):
            px = rnd.uniform(cx0, cx0 + cw)
            py = rnd.uniform(cy0, cy0 + ch)
            d.point((px, py), fill=_sh(_mix(fl, acc, rnd.uniform(.22, .8)), rnd.uniform(.9, 1.25)))
        # 悬浮碎屑
        for _ in range(26):
            px = rnd.uniform(cx0 + 20, cx0 + cw - 20)
            py = rnd.uniform(cy0 + 20, cy0 + ch - 20)
            sr = rnd.uniform(3, 9)
            d.polygon([(px, py - sr), (px + sr * .8, py), (px, py + sr), (px - sr * .8, py)],
                      fill=_sh(_mix(fl, acc, .22), 1.1))

    # ══════════════════════════════════════════════════════════
    # 4. 光照：方向性衰减 + 四角 AO + 墙面投影
    # ══════════════════════════════════════════════════════════
    light = Image.new('L', (w, h))
    ld = ImageDraw.Draw(light)
    for yy in range(h):
        t = yy / h
        # 上方略亮（主光从左上来），中央给一点提亮保证可读性
        v = 244 - 22 * t + 14 * math.sin(math.pi * t)
        ld.line((0, yy, w, yy), fill=max(0, min(255, int(v))))
    light = light.filter(ImageFilter.GaussianBlur(30))
    im = ImageChops.multiply(im, Image.merge('RGB', (light, light, light)))

    # 四角 AO：墙角积灰，最强的深度线索
    ao = Image.new('L', (w, h), 0)
    ad = ImageDraw.Draw(ao)
    ad.rectangle((0, 0, w, h), fill=0)
    ad.rectangle((0, 0, w, h), outline=255, width=3)
    ao = ao.filter(ImageFilter.GaussianBlur(46))
    im = Image.composite(Image.new('RGB', (w, h), deep), im, ao.point(lambda v: int(v * .58)))

    # 墙面对地面的投影（内侧两圈）
    shade_band = Image.new('L', (w, h), 0)
    shd = ImageDraw.Draw(shade_band)
    for i in range(7):
        a = 74 - i * 10
        shd.rectangle((WALL_S, WALL_T + i * 2, w - WALL_S, WALL_T + i * 2 + 2), fill=a)
        shd.rectangle((WALL_S + i * 2, WALL_T, WALL_S + i * 2 + 2, h - WALL_T), fill=a)
        shd.rectangle((w - WALL_S - i * 2 - 2, WALL_T, w - WALL_S - i * 2, h - WALL_T), fill=a)
        shd.rectangle((WALL_S, h - WALL_T - i * 2 - 2, w - WALL_S, h - WALL_T - i * 2), fill=a + 8)
    shade_band = shade_band.filter(ImageFilter.GaussianBlur(4))
    im = Image.composite(Image.new('RGB', (w, h), deep), im,
                         shade_band.point(lambda v: min(255, int(v * .9))))

    d = ImageDraw.Draw(im)

    # ══════════════════════════════════════════════════════════
    # 5. 墙体浮雕（与碰撞边界对齐：上下 36px / 左右 22px）
    # ══════════════════════════════════════════════════════════
    def wall_block(bx, by, bw, bh, top_face=False):
        c0 = _sh(edge, .46)
        d.rectangle((bx, by, bx + bw, by + bh), fill=c0)
        # 顶面（受光）
        if top_face:
            d.rectangle((bx + 2, by + 2, bx + bw - 2, by + bh * .34), fill=_sh(edge, .62))
            d.line([(bx + 3, by + 2), (bx + bw - 3, by + 2)], fill=_sh(edge, .96), width=1)
        # 底面暗
        d.line([(bx + 2, by + bh - 2), (bx + bw - 2, by + bh - 2)],
               fill=_sh(edge, .24), width=2)
        # 竖向砖缝
        d.line([(bx, by), (bx, by + bh)], fill=_sh(edge, .22), width=2)

    for side in (0, 1):
        yy = 0 if side == 0 else h - WALL_T
        d.rectangle((0, yy, w, yy + WALL_T), fill=_sh(edge, .30))
        for x in range(0, w, 74):
            bw = min(70, w - x)
            wall_block(x + 2, yy + 2, bw - 4, WALL_T - 5, top_face=(side == 0))
            rw = random.Random(x * 13 + side * 7 + biome)
            # 生态墙面装饰
            if biome == 0:
                for _ in range(5):
                    px = x + rw.uniform(6, max(7, bw - 10))
                    py = yy + rw.uniform(14, WALL_T - 6)
                    d.ellipse((px, py, px + rw.uniform(3, 7), py + rw.uniform(2, 4)),
                              fill=_sh((52, 100, 66), rw.uniform(.8, 1.15)))
            elif biome == 1:
                if bw > 24:
                    d.rectangle((x + 7, yy + 17, x + bw - 9, yy + 21),
                                fill=_sh(warm, rw.uniform(.26, .40)))
                    for k in range(2):
                        dx = x + 12 + k * (bw - 26)
                        d.ellipse((dx, yy + 24, dx + 3, yy + 27), fill=_sh(edge, .68))
            elif biome == 2:
                if bw > 24:
                    d.line([(x + 9, yy + 21), (x + bw - 11, yy + 21)],
                           fill=_sh(edge, .74), width=1)
                    for k in range(3):
                        dx = x + 12 + k * 17
                        d.rectangle((dx, yy + 11, dx + 4, yy + 26),
                                    fill=_sh(_mix(edge, fl, .4), rw.uniform(.7, 1.0)))
            elif biome == 3:
                for _ in range(3):
                    px = x + rw.uniform(8, max(9, bw - 12))
                    d.polygon([(px, yy + 12), (px + 3.4, yy + 27), (px - 3.4, yy + 27)],
                              fill=_sh(acc, rw.uniform(.30, .52)))
            elif biome == 4:
                if bw > 24:
                    d.line([(x + 7, yy + 19), (x + bw - 9, yy + 24)],
                           fill=_sh(warm, rw.uniform(.26, .44)), width=2)
            else:
                cxw = x + bw / 2
                d.ellipse((cxw - 4.5, yy + 15, cxw + 4.5, yy + 24),
                          outline=_sh(acc, rw.uniform(.22, .38)), width=1)
    for side in (0, 1):
        xx = 0 if side == 0 else w - WALL_S
        d.rectangle((xx, 0, xx + WALL_S, h), fill=_sh(edge, .28))
        for y in range(0, h, 66):
            d.rectangle((xx + 2, y + 2, xx + WALL_S - 3, y + 62), fill=_sh(edge, .44))
            d.line([(xx + 3, y + 3), (xx + WALL_S - 3, y + 3)], fill=_sh(edge, .82), width=1)
            d.line([(xx + 2, y + 2), (xx + 2, y + 62)], fill=_sh(edge, .20), width=2)

    # 可走区边界：深色内框 + 环境色细线（帮助读碰撞）
    d.rectangle((WALL_S, WALL_T, w - WALL_S, h - WALL_T), outline=_sh(deep, .72), width=2)
    d.rectangle((WALL_S + 1, WALL_T + 1, w - WALL_S - 1, h - WALL_T - 1),
                outline=_sh(_mix(fl, edge, .5), .66), width=1)

    return im
