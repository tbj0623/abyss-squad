"""深渊小队 v1.0 —— 原创程序化美术生成（Pillow only，无任何外部素材/版权内容）。

所有调色板、剪影、材质与动画都在本文件内手工编写。
运行： python scripts/create_art.py
产物： app/src/main/res/drawable-nodpi/{actors_v4,props_v4,floor_v4_0..5,cover_v4_0..5,ui_v4}.png
检查： build/qa-v4/*.png

渲染管线（每个角色/敌人/首领，全部 2x 超采样后降采样）
  1. 平坦色块 + 每块 3~5 级明暗   —— 剪影与体积
  2. 定向光（左上主光 / 右下暗部）
  3. 材质噪点（布/皮/石）
  4. LANCZOS 降到 160             —— 抗锯齿边缘
  5. 边缘光 rim light（右下）
  6. 2px 深色描边（膨胀轮廓）
  7. 自发光（眼睛/法杖/核心）
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math, random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi'
QA = ROOT / 'build' / 'qa-v4'
OUT.mkdir(parents=True, exist_ok=True)
QA.mkdir(parents=True, exist_ok=True)

S = 2
CELL = 160
INK = (8, 11, 16, 255)
R = Image.Resampling


def C(s, a=255):
    if isinstance(s, tuple):
        return s if len(s) == 4 else (s[0], s[1], s[2], a)
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def sh(c, m, a=None):
    c = C(c)
    return (max(0, min(255, int(c[0] * m))), max(0, min(255, int(c[1] * m))),
            max(0, min(255, int(c[2] * m))), c[3] if a is None else a)


def mixc(a, b, t):
    a, b = C(a), C(b)
    t = max(0.0, min(1.0, t))
    return (int(a[0] + (b[0] - a[0]) * t), int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), int(a[3] + (b[3] - a[3]) * t))


# ══════════════════════════════════════════════════════════════════
class Rig:
    """160x160 单元绘制上下文，内部 2x 超采样。"""

    def __init__(self, seed=0, w=CELL, h=CELL):
        self.w, self.h = w, h
        self.W, self.H = w * S, h * S
        self.base = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.gl = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.em = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.base)
        self.gd = ImageDraw.Draw(self.gl)
        self.ed = ImageDraw.Draw(self.em)
        self.r = random.Random(seed)

    def P(self, pts):
        return [(x * S, y * S) for x, y in pts]

    def B(self, box):
        return (box[0] * S, box[1] * S, box[2] * S, box[3] * S)

    def poly(self, pts, c):
        self.d.polygon(self.P(pts), fill=C(c))

    def ell(self, box, c):
        self.d.ellipse(self.B(box), fill=C(c))

    def rect(self, box, c):
        self.d.rectangle(self.B(box), fill=C(c))

    def line(self, pts, c, w=1.0):
        P = self.P(pts)
        if len(P) > 1:
            self.d.line(P, fill=C(c), width=max(1, int(round(w * S))), joint='curve')


    def arc(self, box, a0, a1, c, w=1.0):
        self.d.arc(self.B(box), a0, a1, fill=C(c), width=max(1, int(round(w * S))))

    def pieslice(self, box, a0, a1, c):
        self.d.pieslice(self.B(box), a0, a1, fill=C(c))
    def ring(self, cx, cy, rad, c, w=1.0):
        self.d.ellipse(self.B((cx - rad, cy - rad, cx + rad, cy + rad)),
                       outline=C(c), width=max(1, int(round(w * S))))

    def gell(self, box, c, top=1.20, bot=0.62):
        b = self.B(box)
        m = Image.new('L', (self.W, self.H), 0)
        ImageDraw.Draw(m).ellipse(b, fill=255)
        bb = m.getbbox()
        if not bb:
            return
        m = m.crop(bb)
        w, h = bb[2] - bb[0], bb[3] - bb[1]
        g = Image.new('RGB', (1, h))
        gd = ImageDraw.Draw(g)
        for y in range(h):
            t = (y / max(1, h - 1)) ** .92
            gd.point((0, y), fill=sh(c, top + (bot - top) * t)[:3])
        g = g.resize((w, h), R.NEAREST)
        lay = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        lay.paste(g, (0, 0))
        self.base.paste(lay, (bb[0], bb[1]), m)

    def gpoly(self, pts, c_top, c_bot, focus=0.32):
        P = self.P(pts)
        xs = [p[0] for p in P]; ys = [p[1] for p in P]
        x0, y0 = max(0, int(min(xs))), max(0, int(min(ys)))
        x1, y1 = min(self.W, int(max(xs)) + 1), min(self.H, int(max(ys)) + 1)
        if x1 <= x0 or y1 <= y0:
            return
        m = Image.new('L', (x1 - x0, y1 - y0), 0)
        ImageDraw.Draw(m).polygon([(x - x0, y - y0) for x, y in P], fill=255)
        h = y1 - y0
        g = Image.new('RGB', (1, h))
        gd = ImageDraw.Draw(g)
        for y in range(h):
            t = y / max(1, h - 1)
            t = min(1.0, t / focus) if t < focus else (t - focus) / max(1e-3, 1 - focus)
            gd.point((0, y), fill=mixc(c_top, c_bot, t)[:3])
        g = g.resize((x1 - x0, h), R.NEAREST)
        lay = Image.new('RGBA', (x1 - x0, h), (0, 0, 0, 0))
        lay.paste(g, (0, 0))
        self.base.paste(lay, (x0, y0), m)

    # ——— 关键：卡通描边式体积。任何多边形自动获得
    #      顶面高光 / 左上受光 / 右下暗部 / 接地阴影 四层明暗 ———
    def solid(self, pts, c, bevel=3.2, mode='round', top=None, dark=None, hi=None):
        """把多边形画成有体积的块：4 段明暗 + 抗锯齿。"""
        P = self.P(pts)
        xs = [p[0] for p in P]; ys = [p[1] for p in P]
        x0, y0 = max(0, int(min(xs)) - 1), max(0, int(min(ys)) - 1)
        x1, y1 = min(self.W, int(max(xs)) + 2), min(self.H, int(max(ys)) + 2)
        if x1 - x0 < 2 or y1 - y0 < 2:
            return
        m = Image.new('L', (x1 - x0, y1 - y0), 0)
        ImageDraw.Draw(m).polygon([(x - x0, y - y0) for x, y in P], fill=255)
        base = C(c)
        k = max(1, int(bevel * S * 0.72))
        # 受光侧（左上）
        off = ImageChops.offset(m, -k, -k)
        lit = ImageChops.multiply(m, ImageChops.invert(off)) if False else ImageChops.subtract(m, off)
        # 暗部（右下）
        off2 = ImageChops.offset(m, k, k)
        dk = ImageChops.subtract(m, off2)
        # 剩余核（中间调）
        mid = ImageChops.subtract(m, ImageChops.lighter(lit, dk))
        # 顶边高光
        off3 = ImageChops.offset(m, 0, max(1, k // 2))
        tpx = ImageChops.subtract(m, off3)
        lay = Image.new('RGBA', (x1 - x0, y1 - y0), (0, 0, 0, 0))
        col = Image.new('RGB', (x1 - x0, y1 - y0), base[:3])
        # 中间调先用 base 全覆盖
        lay.paste(col, (0, 0), m)
        # 受光
        c_lit = sh(base, top if top else 1.30)
        l1 = Image.new('RGB', (x1 - x0, y1 - y0), c_lit[:3])
        lay.paste(l1, (0, 0), lit)
        # 顶高光
        l0 = Image.new('RGB', (x1 - x0, y1 - y0), sh(base, hi if hi else 1.62)[:3])
        lay.paste(l0, (0, 0), tpx)
        # 暗部
        c_dk = sh(base, dark if dark else 0.60)
        l2 = Image.new('RGB', (x1 - x0, y1 - y0), c_dk[:3])
        lay.paste(l2, (0, 0), dk)
        self.base.paste(lay, (x0, y0), m)

    def tube(self, x0, y0, x1, y1, w, c, bevel=2.4):
        """有体积的管状肢体（沿方向的亮暗带）。"""
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L * w * .5, dx / L * w * .5
        r1 = self.P([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny),
                     (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])
        self.d.polygon(r1, fill=C(c))
        # 亮侧（左下法线方向）
        self.d.polygon(self.P([(x0 + nx * .72, y0 + ny * .72), (x1 + nx * .72, y1 + ny * .72),
                               (x1 + nx * .18, y1 + ny * .18), (x0 + nx * .18, y0 + ny * .18)]),
                       fill=C(sh(c, 1.32)))
        # 暗侧
        self.d.polygon(self.P([(x0 - nx * .18, y0 - ny * .18), (x1 - nx * .18, y1 - ny * .18),
                               (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]), fill=C(sh(c, .60)))
        # 顶盖
        self.d.ellipse(self.P([(x0 - w * .5, y0 - w * .34), (x0 + w * .5, y0 + w * .34)]),
                       fill=C(sh(c, 1.18)))

    def grain(self, pts, c, amount=0.16, density=1.0):
        r = self.r
        P = self.P(pts)
        xs = [p[0] for p in P]; ys = [p[1] for p in P]
        x0, y0, x1, y1 = int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))
        if x1 <= x0 or y1 <= y0:
            return
        m = Image.new('L', (self.W, self.H), 0)
        ImageDraw.Draw(m).polygon(P, fill=255)
        lay = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        ld = ImageDraw.Draw(lay)
        n = int((x1 - x0) * (y1 - y0) / 22 * density)
        for _ in range(n):
            x = r.randint(x0, max(x0, x1 - 1)); y = r.randint(y0, max(y0, y1 - 1))
            if not m.getpixel((x, y)):
                continue
            v = 1 + r.uniform(-amount, amount)
            ld.point((x, y), fill=sh(c, v, r.randint(45, 150)))
        self.base.alpha_composite(lay)

    def emit(self, x, y, rad, c, glow=True, a=255, core=0.55):
        if glow:
            self.gd.ellipse(self.B((x - rad * 2.3, y - rad * 2.3, x + rad * 2.3, y + rad * 2.3)),
                            fill=C(c, min(255, int(a * 0.34))))
        self.ed.ellipse(self.B((x - rad, y - rad, x + rad, y + rad)), fill=C(c, a))
        self.ed.ellipse(self.B((x - rad * core, y - rad * core, x + rad * core, y + rad * core)),
                        fill=mixc(c, '#ffffff', .8))

    def emit_poly(self, pts, c, a=255):
        self.ed.polygon(self.P(pts), fill=C(c, a))

    def emit_line(self, pts, c, w=1.0, a=255):
        self.ed.line(self.P(pts), fill=C(c, a), width=max(1, int(round(w * S))), joint='curve')

    def final(self, rim=('#a8d8e8', .40), rim_dir=(1, 1), outline=2, shadow=None,
              grain=0.0, key=0.55, floor_dark=0.90, glow_blur=9.0):
        W, H = self.W, self.H
        body = self.base
        if body.getbbox() is None:
            return Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        gw = 16
        small = Image.new('RGB', (gw, gw))
        sp = small.load()
        for yy in range(gw):
            for xx in range(gw):
                fx = xx / (gw - 1) * 2 - 1
                fy = yy / (gw - 1) * 2 - 1
                v = 1.05 - (0.050 * fx + 0.082 * fy) * (key * 2)
                v = max(0.86, min(1.14, v))
                sp[xx, yy] = (int(255 * v),) * 3
        grad = small.resize((W, H), R.BICUBIC)
        rgb = ImageChops.multiply(body.convert('RGB'), grad)
        vg = Image.new('L', (1, 40))
        vp = vg.load()
        for y in range(40):
            t = y / 39.0
            vp[0, y] = int(255 * (1.0 - (1.0 - floor_dark) * max(0.0, t - .42) / .58))
        vg = vg.resize((W, H), R.BILINEAR)
        rgb = ImageChops.multiply(rgb, Image.merge('RGB', (vg, vg, vg)))
        if grain > 0:
            n = Image.effect_noise((W, H), 40).convert('L')
            n = n.point(lambda v: int(max(0, min(255, 128 + (v - 128) * grain))))
            rgb = ImageChops.multiply(rgb, Image.merge('RGB', (n, n, n)))
        body = Image.merge('RGBA', (*rgb.split(), body.split()[3]))
        a = body.split()[3].resize((self.w, self.h), R.LANCZOS)
        body = body.resize((self.w, self.h), R.LANCZOS)
        body.putalpha(a)
        if rim:
            dx, dy = rim_dir
            off = ImageChops.offset(a, int(round(-3 * dx)), int(round(-3 * dy)))
            band = ImageChops.subtract(a, off).filter(ImageFilter.GaussianBlur(0.8))
            layer = Image.new('RGBA', (self.w, self.h), C(rim[0], 0))
            layer.putalpha(band.point(lambda v: int(v * rim[1])))
            body = Image.alpha_composite(body, layer)
        off = ImageChops.offset(a, 0, 3)
        band = ImageChops.subtract(a, off).filter(ImageFilter.GaussianBlur(0.7))
        layer = Image.new('RGBA', (self.w, self.h), C('#fff0d2', 0))
        layer.putalpha(band.point(lambda v: int(v * 0.20)))
        body = Image.alpha_composite(body, layer)
        out = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        if shadow is not None:
            sx, sy, sr, sa = shadow
            sm = Image.new('L', (self.w, self.h), 0)
            ImageDraw.Draw(sm).ellipse((sx - sr, sy - sr * .30, sx + sr, sy + sr * .30), fill=sa)
            sm = sm.filter(ImageFilter.GaussianBlur(2.6))
            out.paste(Image.new('RGBA', (self.w, self.h), (2, 5, 10, 255)), (0, 0), sm)
        if outline > 0:
            k = int(outline * 2) + 1
            dil = a.filter(ImageFilter.MaxFilter(min(11, k + 2)))
            ring = ImageChops.subtract(dil, a)
            lay = Image.new('RGBA', (self.w, self.h), INK)
            lay.putalpha(ring.point(lambda v: int(v * 0.95)))
            out = Image.alpha_composite(out, lay)
        out = Image.alpha_composite(out, body)
        er = a.filter(ImageFilter.MinFilter(3))
        inner = ImageChops.subtract(a, er)
        lay = Image.new('RGBA', (self.w, self.h), (5, 8, 14, 255))
        lay.putalpha(inner.point(lambda v: int(v * 0.28)))
        out = Image.alpha_composite(out, lay)
        if self.em.getbbox():
            out = Image.alpha_composite(out, self.em.resize((self.w, self.h), R.LANCZOS))
        if self.gl.getbbox():
            gl = self.gl.filter(ImageFilter.GaussianBlur(glow_blur))
            gl = gl.resize((self.w, self.h), R.LANCZOS)
            rgb2 = ImageChops.add(out.convert('RGB'), gl.convert('RGB'))
            al = ImageChops.add(out.split()[3], gl.split()[3])
            out = Image.merge('RGBA', (*rgb2.split(), al))
        return out
