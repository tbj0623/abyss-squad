"""封面 v2：解决「6 张封面过于同质」（都是同一个拱门 + 石头剪影）。

问题：v1 的 cover_art 用同一套拱门/柱子/乱石模板，只换配色，
      6 张看起来像同一张图换了濾鏡。

v2 策略：
  * 保留「拱门」作为构图锚点（主菜单右侧大图需要一个稳定的视觉中心），
    但每个生态的**中景主题物**完全不同的类型与轮廓：
      0 苔光：巨树根+藤蔓，拱门被树根包裹
      1 赤铜：巨型齿轮+管道+炉膛，拱门是机械门框
      2 月影：书架高墙+悬浮书页+星图穹顶
      3 霜蚀：冰瀑+冰柱林，拱门是冰封门
      4 熔火：熔岩瀑布+铁链吊桥，拱门是熔炉口
      5 虚空：破碎浮岛+裂隙，拱门是虚空门
  * 前/中/背景三层更明确；前景加剪影（不抢主体）。
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _art_env import BIOMES, COVER_W, COVER_H, _sh, _mix


def _blob(cx, cy, rx, ry, rnd, n=10, irregular=.3):
    pts = []
    for i in range(n):
        a = i * math.tau / n
        k = 1.0 + rnd.uniform(-irregular, irregular)
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return pts


def cover_art_v2(biome):
    cfg = BIOMES[biome]
    rnd = random.Random(4400 + biome * 97)
    w, h = COVER_W, COVER_H
    bg, fl, edge, acc = cfg['bg'], cfg['floor'], cfg['edge'], cfg['accent']
    deep, warm = cfg['deep'], cfg['warm']

    # ══════════════════════════════════════════════════════════
    # 背景层：天空渐变 + 远景剪影（每个生态不同轮廓）
    # ══════════════════════════════════════════════════════════
    im = Image.new('RGB', (w, h), deep)
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        d.line((0, y, w, y), fill=_mix(_sh(bg, .52), _sh(bg, 1.24), t ** .78))

    if biome == 0:      # 苔光：巨型树冠剪影
        for i in range(6):
            x = -40 + i * 210 + rnd.uniform(-40, 40)
            th = rnd.uniform(300, 470)
            d.polygon([(x, h), (x + 34, h - th * .5), (x + 96, h - th),
                       (x + 170, h - th * .8), (x + 205, h)], fill=_sh(bg, .44))
    elif biome == 1:    # 赤铜：烟囱/塔吊剪影
        for i in range(7):
            x = -30 + i * 165
            tw = rnd.uniform(26, 52)
            th = rnd.uniform(180, 400)
            d.rectangle((x, h - th, x + tw, h), fill=_sh(bg, .46))
            if i % 2 == 0:
                d.rectangle((x - 8, h - th - 16, x + tw + 8, h - th), fill=_sh(bg, .40))
    elif biome == 2:    # 月影：书库穹顶/高窗
        for i in range(5):
            x = -60 + i * 250
            d.pieslice((x, h - 420, x + 220, h + 60), 180, 360, fill=_sh(bg, .44))
        for i in range(9):
            x = 20 + i * 118
            d.rectangle((x, 60, x + 52, h - 120), fill=_sh(bg, .38))
    elif biome == 3:    # 霜蚀：冰川尖峰
        for i in range(8):
            x = -80 + i * 165 + rnd.uniform(-30, 30)
            ph = rnd.uniform(240, 460)
            d.polygon([(x, h), (x + 54, h - ph), (x + 108, h)], fill=_sh(bg, .46))
            d.polygon([(x + 54, h - ph), (x + 78, h - ph * .55), (x + 108, h)],
                      fill=_sh(bg, .56))
    elif biome == 4:    # 熔火：熔岩穹顶/渣山
        for i in range(6):
            x = -60 + i * 200
            d.polygon([(x, h), (x + 90, h - rnd.uniform(180, 330)), (x + 200, h)],
                      fill=_sh(bg, .40))
        d.rectangle((0, h - 26, w, h), fill=_sh(warm, .30))
    else:               # 虚空：悬浮碎块 + 裂隙
        for i in range(26):
            x = rnd.uniform(-40, w + 40)
            y = rnd.uniform(-40, h * .7)
            s = rnd.uniform(14, 62)
            d.polygon([(x, y - s), (x + s * .9, y), (x, y + s), (x - s * .9, y)],
                      fill=_sh(_mix(deep, bg, rnd.uniform(.3, .7)), 1.0))
        for i in range(3):
            x = rnd.uniform(80, w - 80)
            d.line([(x, 0), (x + rnd.uniform(-50, 50), h * .4),
                    (x + rnd.uniform(-80, 80), h)], fill=_sh(acc, .22), width=4)

    # ══════════════════════════════════════════════════════════
    # 中景主题物（每个生态完全不同）
    # ══════════════════════════════════════════════════════════
    ax, ay = w * .5, h * .60          # 拱门中心
    aw = 288

    if biome == 0:      # 巨型树根环绕
        for s in (-1, 1):
            for k in range(5):
                bx = ax + s * (aw * .5 + 40 + k * 26)
                pts = [(bx, h + 20),
                       (bx + s * rnd.uniform(10, 40), h - 120 - k * 30),
                       (bx + s * rnd.uniform(-20, 60), h - 260 - k * 36),
                       (bx + s * rnd.uniform(-30, 50), h - 380 - k * 20)]
                d.line(pts, fill=_sh(_mix(fl, (44, 82, 58), .55), .96),
                       width=max(6, 26 - k * 4))
                d.line(pts, fill=_sh(_mix(fl, acc, .30), 1.02),
                       width=max(2, 10 - k * 2))
        # 垂藤
        for i in range(16):
            x = rnd.uniform(20, w - 20)
            ln = rnd.uniform(60, 200)
            d.line([(x, 0), (x + rnd.uniform(-16, 16), ln)],
                   fill=_sh(_mix(fl, (56, 100, 68), .6), .9), width=2)

    elif biome == 1:    # 机械齿轮 + 管道
        for gx, gy, gr in ((ax - 230, 200, 92), (ax + 250, 290, 74), (ax - 120, 470, 56)):
            for k in range(12):
                a = k * math.tau / 12
                d.polygon([(gx + math.cos(a) * gr * .72, gy + math.sin(a) * gr * .72),
                           (gx + math.cos(a + .12) * gr, gy + math.sin(a + .12) * gr),
                           (gx + math.cos(a + .26) * gr * .72, gy + math.sin(a + .26) * gr * .72)],
                          fill=_sh(edge, .52))
            d.ellipse((gx - gr * .7, gy - gr * .7, gx + gr * .7, gy + gr * .7),
                      fill=_sh(edge, .38))
            d.ellipse((gx - gr * .34, gy - gr * .34, gx + gr * .34, gy + gr * .34),
                      fill=_sh(deep, 1.5))
            d.ellipse((gx - gr * .7, gy - gr * .7, gx + gr * .7, gy + gr * .7),
                      outline=_sh(edge, .78), width=3)
        for i in range(3):
            yy = 110 + i * 150
            d.rectangle((0, yy, w, yy + 20), fill=_sh(edge, .40))
            d.rectangle((0, yy, w, yy + 5), fill=_sh(edge, .60))

    elif biome == 2:    # 书架高墙 + 悬浮书页 + 星图
        for s in (-1, 1):
            for col in range(3):
                bx = ax + s * (aw * .5 + 60 + col * 96)
                for row in range(5):
                    by = 40 + row * 104
                    d.rectangle((bx - 44, by, bx + 44, by + 92),
                                fill=_sh(edge, .30 + row * .02))
                    for k in range(7):
                        bh = rnd.uniform(40, 72)
                        c = _sh(_mix(fl, edge, rnd.uniform(.25, .75)), rnd.uniform(.78, 1.2))
                        d.rectangle((bx - 38 + k * 11, by + 88 - bh,
                                     bx - 30 + k * 11, by + 88), fill=c)
        for _ in range(34):
            x, y = rnd.uniform(60, w - 60), rnd.uniform(50, h - 150)
            s2 = rnd.uniform(7, 15)
            d.polygon([(x - s2, y - s2), (x + s2, y - s2 * 1.2),
                       (x + s2, y + s2), (x - s2, y + s2 * .8)],
                      fill=_mix(fl, (206, 210, 236), rnd.uniform(.34, .58)))

    elif biome == 3:    # 冰瀑 + 冰柱林
        # 冰瀑（从顶部倾泻）
        for i in range(5):
            x = 90 + i * 180
            d.polygon([(x - 46, 0), (x + 52, 0), (x + 88, h * .72), (x - 20, h * .72)],
                      fill=_sh(_mix(fl, (140, 184, 214), .60), .86))
            d.polygon([(x - 12, 0), (x + 20, 0), (x + 44, h * .62), (x + 6, h * .62)],
                      fill=_sh(_mix(fl, (196, 226, 244), .58), 1.0))
        # 冰柱
        for i in range(14):
            x = rnd.uniform(0, w)
            ln = rnd.uniform(40, 190)
            wd = rnd.uniform(8, 26)
            d.polygon([(x - wd, 0), (x, ln), (x + wd, 0)],
                      fill=_sh(_mix(edge, acc, .34), .82))
            d.polygon([(x - wd * .3, 0), (x, ln * .8), (x + wd * .2, 0)],
                      fill=_sh(acc, .62))

    elif biome == 4:    # 熔岩瀑布 + 铁链吊桥
        for i in range(4):
            x = 110 + i * 230
            d.polygon([(x - 30, 0), (x + 34, 0), (x + 52, h), (x - 50, h)],
                      fill=_sh(warm, .40))
            d.polygon([(x - 10, 0), (x + 12, 0), (x + 22, h), (x - 18, h)],
                      fill=_sh(warm, .86))
        # 吊桥链条
        for s in (-1, 1):
            x0 = ax + s * 210
            for k in range(9):
                yy = 90 + k * 44
                d.ellipse((x0 - 9, yy, x0 + 9, yy + 26), outline=_sh(edge, .68), width=3)
        # 熔岩池（底部）
        d.ellipse((-120, h - 130, w + 120, h + 120), fill=_sh(warm, .52))
        d.ellipse((-60, h - 96, w + 60, h + 100), fill=_sh(warm, 1.02))

    else:               # 虚空裂隙 + 破碎拱门
        for i in range(4):
            x = ax - 160 + i * 108
            d.line([(x, 0), (x + rnd.uniform(-30, 30), h * .35),
                    (x + rnd.uniform(-50, 50), h)], fill=_sh(acc, .30), width=6)
        # 环绕拱门的碎块
        for i in range(22):
            a = i * math.tau / 22
            rr = aw * .5 + rnd.uniform(30, 110)
            px = ax + math.cos(a) * rr
            py = ay + math.sin(a) * rr * .78
            s2 = rnd.uniform(8, 26)
            d.polygon([(px, py - s2), (px + s2 * .8, py), (px, py + s2), (px - s2 * .8, py)],
                      fill=_sh(_mix(deep, acc, rnd.uniform(.12, .34)), 1.0))

    # ══════════════════════════════════════════════════════════
    # 拱门（构图的稳定锚点，每个生态不同材质）
    # ══════════════════════════════════════════════════════════
    # ── 拱门几何：半圆拱顶（圆心 cyA）+ 两根直柱 ──
    aw = 268
    RR = aw / 2 + 26          # 外半径
    RI = aw / 2 - 6           # 内半径
    cyA = ay - 96             # 拱心（spring line）
    ceil = cyA - RR           # 拱最高点

    d = ImageDraw.Draw(im)

    # 门内的"景象"：先画（被拱石压住边缘）
    gate_col = {
        0: _sh(_mix(fl, (54, 112, 88), .72), 1.0),
        1: _sh(_mix(warm, deep, .46), 1.0),
        2: _sh(_mix(fl, (88, 94, 146), .72), 1.0),
        3: _sh(_mix(fl, (124, 170, 204), .72), 1.0),
        4: _sh(_mix(warm, (116, 38, 18), .52), 1.0),
        5: _sh(_mix(deep, acc, .32), 1.0),
    }[biome]
    # 门内 = 内半径半圆 + 往下到底的矩形
    d.pieslice((ax - RI, cyA - RI, ax + RI, cyA + RI), 180, 360, fill=gate_col)
    d.rectangle((ax - RI, cyA, ax + RI, h), fill=gate_col)

    # 门内的景深：底部压暗 + 中心微亮
    grad = Image.new('L', (int(RI * 2), 220), 0)
    gd2 = ImageDraw.Draw(grad)
    for i in range(220):
        gd2.line((0, i, RI * 2, i), fill=int(70 * (i / 220.0) ** .8))
    darkstrip = Image.new('RGB', (int(RI * 2), 220), _sh(gate_col, .50))
    im.paste(darkstrip, (int(ax - RI), int(h - 220)), grad)
    d = ImageDraw.Draw(im)

    # 拱石（沿半圆排列，每块独立明暗 + 上下沿厚度）
    N = 15
    for i in range(N):
        a = math.pi + i * math.pi / N
        b = math.pi + (i + 1) * math.pi / N
        pts = [(ax + math.cos(a) * RR, cyA + math.sin(a) * RR),
               (ax + math.cos(b) * RR, cyA + math.sin(b) * RR),
               (ax + math.cos(b) * RI, cyA + math.sin(b) * RI),
               (ax + math.cos(a) * RI, cyA + math.sin(a) * RI)]
        c = _sh(edge, rnd.uniform(.56, .92))
        d.polygon(pts, fill=c, outline=_sh(edge, .30))
        d.line([pts[0], pts[1]], fill=_sh(c, 1.32), width=2)
        d.line([pts[2], pts[3]], fill=_sh(c, .64), width=2)
        # 砖缝放射线
        d.line([pts[0], pts[3]], fill=_sh(edge, .34), width=1)

    # 两根直柱（从 spring line 到底部）
    for sdir in (-1, 1):
        px = ax + sdir * (RI + RR) / 2
        wide = (RR - RI) / 2
        d.rectangle((px - wide, cyA - 2, px + wide, h), fill=_sh(edge, .48))
        # 柱身分节
        yy = cyA + 8
        k = 0
        while yy < h:
            d.rectangle((px - wide + 2, yy, px + wide - 2, yy + 30),
                        fill=_sh(edge, .58 + (k % 3) * .03), outline=_sh(edge, .30))
            d.line([(px - wide + 3, yy + 1), (px + wide - 3, yy + 1)],
                   fill=_sh(edge, .86), width=1)
            yy += 34
            k += 1
        # 柱头（柱顶的挑檐）
        d.rectangle((px - wide - 8, cyA - 16, px + wide + 8, cyA + 6), fill=_sh(edge, .66))
        d.line([(px - wide - 7, cyA - 15), (px + wide + 7, cyA - 15)],
               fill=_sh(edge, 1.05), width=2)

    # 拱顶石（keystone）
    d.polygon([(ax - 11, ceil + 6), (ax + 11, ceil + 6),
               (ax + 15, cyA - RI - 6), (ax - 15, cyA - RI - 6)],
              fill=_sh(edge, .80), outline=_sh(edge, .40))

    # ══════════════════════════════════════════════════════════
    # 雾 / 光 / 前景剪影
    # ══════════════════════════════════════════════════════════
    glow = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((ax - 240, ay - 250, ax + 240, ay + 290), fill=(*_sh(acc, 1.0), 58))
    glow = glow.filter(ImageFilter.GaussianBlur(78))
    im = Image.alpha_composite(im.convert('RGBA'), glow).convert('RGB')

    for i in range(3):
        fog = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        fd = ImageDraw.Draw(fog)
        fy = h * (.34 + i * .18)
        fd.ellipse((-220, fy - 100, w + 220, fy + 140), fill=(*_sh(bg, 1.6), 24 - i * 5))
        fog = fog.filter(ImageFilter.GaussianBlur(64))
        im = Image.alpha_composite(im.convert('RGBA'), fog).convert('RGB')

    d = ImageDraw.Draw(im)
    # 前景：暗剪影（乱石/根/管道），不抢主体
    for i in range(18):
        x = rnd.uniform(-30, w + 30)
        y = h - rnd.uniform(-10, 62)
        ww = rnd.uniform(36, 104)
        d.polygon([(x, y), (x + ww * .5, y - rnd.uniform(10, 30)),
                   (x + ww, y + rnd.uniform(-4, 16)), (x + ww + 14, h), (x - 14, h)],
                  fill=_sh(deep, rnd.uniform(.9, 1.5)))
    # 边缘暗角
    vg = Image.new('L', (w, h), 0)
    vd = ImageDraw.Draw(vg)
    vd.rectangle((0, 0, w, h), fill=255)
    vd.ellipse((-w * .22, -h * .26, w * 1.22, h * 1.26), fill=0)
    vg = vg.filter(ImageFilter.GaussianBlur(108))
    im = Image.composite(im, Image.new('RGB', (w, h), deep),
                         vg.point(lambda v: 255 - int(v * .78)))
    return im


if __name__ == '__main__':
    import os
    os.makedirs('build/qa-v7/preview', exist_ok=True)
    ims = []
    for b in range(6):
        im = cover_art_v2(b)
        im.save(f'build/qa-v7/preview/cover_v4_{b}.png')
        ims.append(im)
        print('cover', b, BIOMES[b]['name'])
    sheet = Image.new('RGB', (500 * 2 + 12, 300 * 3 + 16), (10, 14, 18))
    for i, im in enumerate(ims):
        th = im.resize((500, 300), Image.Resampling.LANCZOS)
        sheet.paste(th, ((i % 2) * 512, (i // 2) * 306))
    sheet.save('build/qa-v7/preview/covers-after.png')
    print('sheet saved')
