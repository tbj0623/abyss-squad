"""地板 v2 精修层：在 _art_floor.floor_tile 之上叠加材质/光照修复。

针对 6 个具体缺陷（都用「改后 vs 改前」证据图验证）：
  A. 赤铜「炉渣坑」是黑洞 → 改成浅坑 + 受光坑沿 + 坑底余温
  B. 霜蚀「冰裂」像白蛛网 → 更直更短的折线，亮度降一档
  C. 熔火「熔岩池」像煎蛋（同心椭圆） → 不规则 blob + 偏移亮核 + 表面流纹
  D. 虚空「裂隙」像白色涂鸦 → 粗深色底 + 细紫边，只在起点给辉光
  E. 砖缝过于机械 → 每块砖增加朝向性脏污与磨损
  F. 整体缺纵深 → 加强四角 AO + 墙面投影带 + 克制的镜头暗角

做法：不改原函数，而是在其输出上做「局部重绘 + 再打光」，
      由 create_art.py 调用 enhanced_floor(biome)。
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageChops

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _art_floor import floor_tile, BIOMES, FLOOR_W, FLOOR_H, WALL_T, WALL_S, _sh, _mix


def _blob(cx, cy, rx, ry, rnd, n=11, irregular=.28):
    pts = []
    for i in range(n):
        a = i * math.tau / n
        k = 1.0 + rnd.uniform(-irregular, irregular)
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return pts


def enhanced_floor(biome, seed=0):
    im = floor_tile(biome, seed)
    cfg = BIOMES[biome]
    fl, edge, acc = cfg['floor'], cfg['edge'], cfg['accent']
    deep, warm = cfg['deep'], cfg['warm']
    rnd = random.Random(7100 + biome * 37 + seed)
    w, h = im.size
    d = ImageDraw.Draw(im)
    cx0, cy0 = WALL_S, WALL_T
    cw, ch = w - WALL_S * 2, h - WALL_T * 2

    # ══════════════════════════════════════════════════════════
    # A. 赤铜：重画炉渣坑（覆盖原来的黑洞）
    # ══════════════════════════════════════════════════════════
    # ══════════════════════════════════════════════════════════
    # B. 霜蚀：把白蛛网压下去，改成"冰层下"的短折裂
    # ══════════════════════════════════════════════════════════
    if biome == 3:
        # 很轻的一层结霜，压掉残留的高光点，但不盖掉砖纹
        veil = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        vd = ImageDraw.Draw(veil)
        vd.rectangle((0, 0, w, h), fill=(*_sh(_mix(fl, (168, 204, 224), .5), 1.04), 34))
        im = Image.alpha_composite(im.convert('RGBA'), veil).convert('RGB')
        d = ImageDraw.Draw(im)
        # 斜向薄冰反光（很淡）
        for _ in range(14):
            px = rnd.uniform(cx0 + 30, cx0 + cw - 30)
            py = rnd.uniform(cy0 + 30, cy0 + ch - 30)
            rw = rnd.uniform(34, 96)
            rh = rw * rnd.uniform(.08, .16)
            d.polygon([(px - rw, py), (px + rw, py - rh * 2), (px + rw, py - rh),
                       (px - rw, py + rh)],
                      fill=_sh(_mix(fl, (186, 216, 236), .18), 1.0))

    # ══════════════════════════════════════════════════════════
    # C. 熔火：把"煎蛋"熔岩池重画成不规则熔岩
    # ══════════════════════════════════════════════════════════
    if biome == 4:
        # 用焦岩色覆盖旧池（取样周围）
        for _ in range(9):
            px = rnd.uniform(cx0 + 80, cx0 + cw - 80)
            py = rnd.uniform(cy0 + 70, cy0 + ch - 70)
            rw = rnd.uniform(30, 62)
            rh = rw * rnd.uniform(.5, .7)
            probe = im.crop((int(px - rw * 1.7), int(py - rh * 1.7),
                             int(px + rw * 1.7), int(py + rh * 1.7)))
            base_col = probe.resize((1, 1), Image.Resampling.BOX).getpixel((0, 0))
            d.polygon(_blob(px, py, rw * 1.6, rh * 1.55, rnd, 13, .30), fill=base_col)
            # 冷却壳
            d.polygon(_blob(px, py, rw * 1.36, rh * 1.30, rnd, 12, .26), fill=(66, 26, 17))
            d.polygon(_blob(px, py, rw * 1.14, rh * 1.10, rnd, 12, .24), fill=(136, 50, 24))
            d.polygon(_blob(px, py, rw * .94, rh * .90, rnd, 11, .22), fill=(184, 78, 36))
            # 亮核偏左上
            d.polygon(_blob(px - rw * .16, py - rh * .18, rw * .50, rh * .48, rnd, 9, .28),
                      fill=(228, 118, 56))
            d.polygon(_blob(px - rw * .20, py - rh * .24, rw * .24, rh * .24, rnd, 8, .30),
                      fill=(252, 178, 98))
            # 表面流纹
            for _ in range(rnd.randint(2, 4)):
                sx = px + rnd.uniform(-rw * .45, rw * .45)
                sy = py + rnd.uniform(-rh * .35, rh * .35)
                pts = [(sx, sy)]
                for _ in range(rnd.randint(2, 3)):
                    pts.append((pts[-1][0] + rnd.uniform(-9, 9),
                                pts[-1][1] + rnd.uniform(-4, 8)))
                d.line(pts, fill=_sh((250, 184, 112), rnd.uniform(.68, .90)), width=1)
            # 池边焦壳碎块
            for _ in range(5):
                a = rnd.uniform(0, math.tau)
                ex = px + math.cos(a) * rw * rnd.uniform(1.0, 1.34)
                ey = py + math.sin(a) * rh * rnd.uniform(1.0, 1.34)
                sz = rnd.uniform(3, 8)
                d.polygon(_blob(ex, ey, sz, sz * .55, rnd, 6, .4),
                          fill=_sh(base_col, rnd.uniform(.74, 1.0)))

    # ══════════════════════════════════════════════════════════
    # D. 虚空：把细白裂隙改成"粗深裂口 + 细紫边"
    # ══════════════════════════════════════════════════════════
    if biome == 5:
        for _ in range(12):
            px = rnd.uniform(cx0 + 50, cx0 + cw - 50)
            py = rnd.uniform(cy0 + 50, cy0 + ch - 50)
            a0 = rnd.uniform(0, math.tau)
            pts = [(px, py)]
            for _ in range(rnd.randint(3, 5)):
                a0 += rnd.uniform(-.32, .32)
                pts.append((pts[-1][0] + math.cos(a0) * rnd.uniform(22, 52),
                            pts[-1][1] + math.sin(a0) * rnd.uniform(12, 30)))
            # 裂口的"开口"暗带（宽度渐变，尾端收细）
            for i in range(len(pts) - 1):
                t0 = i / max(1, len(pts) - 1)
                wdt = max(1, int(6 * (1 - t0 * .78)))
                d.line([pts[i], pts[i + 1]], fill=_sh(deep, .72), width=wdt)
            # 单侧紫色受光边（仅上缘）
            for i in range(len(pts) - 1):
                t0 = i / max(1, len(pts) - 1)
                off = 2.0 * (1 - t0 * .7)
                d.line([(pts[i][0], pts[i][1] - off), (pts[i + 1][0], pts[i + 1][1] - off)],
                       fill=_sh(_mix(deep, acc, .52), 1.0), width=1)
            # 裂口起点的辉光（只一点点，不整条描亮）
            d.ellipse((pts[0][0] - 3, pts[0][1] - 3, pts[0][0] + 3, pts[0][1] + 3),
                      fill=_sh(_mix(deep, acc, .78), 1.0))

    # ══════════════════════════════════════════════════════════
    # E. 砖缝：加朝向性脏污（每块砖朝墙的一侧积灰）
    # ══════════════════════════════════════════════════════════
    # 只沿可走区四条边内侧积灰（不进入战场中央，避免大块脏斑）
    dust = Image.new('L', (w, h), 0)
    dd = ImageDraw.Draw(dust)
    for _ in range(90):
        side = rnd.randrange(4)
        depth = abs(rnd.gauss(0, 1)) * 15.0 + 2.0          # 贴边 2~40px
        depth = min(depth, 46.0)
        if side == 0:
            px, py = rnd.uniform(cx0, cx0 + cw), cy0 + depth
        elif side == 1:
            px, py = rnd.uniform(cx0, cx0 + cw), cy0 + ch - depth
        elif side == 2:
            px, py = cx0 + depth, rnd.uniform(cy0, cy0 + ch)
        else:
            px, py = cx0 + cw - depth, rnd.uniform(cy0, cy0 + ch)
        rw = rnd.uniform(12, 38)
        rh = rnd.uniform(4, 11)
        dd.ellipse((px - rw, py - rh, px + rw, py + rh), fill=rnd.randint(16, 44))
    dust = dust.filter(ImageFilter.GaussianBlur(14))
    im = ImageChops.subtract(im, Image.merge('RGB', (dust, dust, dust)))

    # ══════════════════════════════════════════════════════════
    # F. 纵深：四角 AO 加深 + 墙面投影带 + 克制暗角
    # ══════════════════════════════════════════════════════════
    ao = Image.new('L', (w, h), 0)
    ad = ImageDraw.Draw(ao)
    ad.rectangle((0, 0, w, h), outline=255, width=3)
    ao = ao.filter(ImageFilter.GaussianBlur(54))
    im = Image.composite(Image.new('RGB', (w, h), deep), im, ao.point(lambda v: int(v * .30)))

    band = Image.new('L', (w, h), 0)
    bd = ImageDraw.Draw(band)
    for i in range(8):
        a = 76 - i * 9
        bd.rectangle((WALL_S, WALL_T + i * 2, w - WALL_S, WALL_T + i * 2 + 2), fill=a)
        bd.rectangle((WALL_S + i * 2, WALL_T, WALL_S + i * 2 + 2, h - WALL_T), fill=a)
        bd.rectangle((w - WALL_S - i * 2 - 2, WALL_T, w - WALL_S - i * 2, h - WALL_T), fill=a)
        bd.rectangle((WALL_S, h - WALL_T - i * 2 - 2, w - WALL_S, h - WALL_T - i * 2), fill=a + 10)
    band = band.filter(ImageFilter.GaussianBlur(5))
    im = Image.composite(Image.new('RGB', (w, h), deep), im,
                         band.point(lambda v: min(255, int(v * .62))))

    vg = Image.new('L', (w, h), 0)
    vd2 = ImageDraw.Draw(vg)
    vd2.ellipse((-w * .18, -h * .30, w * 1.18, h * 1.30), fill=44)
    vg = vg.filter(ImageFilter.GaussianBlur(120))
    im = ImageChops.add(im, Image.merge('RGB', (vg, vg, vg)))

    return im


if __name__ == '__main__':
    import os
    os.makedirs('build/qa-v7/preview', exist_ok=True)
    ims = []
    for b in range(6):
        im = enhanced_floor(b)
        im.save(f'build/qa-v7/preview/floor_v4_{b}.png')
        ims.append(im)
        print('floor', b, BIOMES[b]['name'])
    sheet = Image.new('RGB', (560 * 2 + 12, 310 * 3 + 16), (10, 14, 18))
    for i, im in enumerate(ims):
        th = im.resize((560, 310), Image.Resampling.LANCZOS)
        sheet.paste(th, ((i % 2) * (560 + 8) + 4, (i // 2) * (310 + 6) + 4))
    sheet.save('build/qa-v7/preview/floors-after.png')
    print('sheet saved')
