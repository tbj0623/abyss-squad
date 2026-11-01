"""补一张封面 before/after 对比 + 图集质量抽查（透明边缘 / 帧间抖动 / 颜色脏）。"""
import os
from PIL import Image, ImageDraw, ImageChops
import shutil

os.makedirs('build/qa-v7', exist_ok=True)


def label(im, text, size=18):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, len(text) * size * .62 + 16, size + 12), fill=(0, 0, 0))
    d.text((8, 6), text, fill=(240, 245, 250))
    return im


# ── 封面 before/after ──
NAMES = ['Mosslight Ruins', 'Copper Forge', 'Moonshadow Archive',
         'Frostbite Gallery', 'Molten Abyss', 'Void Throne']
rows = []
for b in range(6):
    # before: 从 v1 备份取（如果没有就用当前）
    bp = f'build/qa-v7/before/cover_v4_{b}.png'
    ap = f'build/qa-v7/after/cover_v4_{b}.png'
    if not os.path.exists(bp):
        print('missing before cover', b)
        continue
    a = Image.open(bp).convert('RGB').resize((480, 288), Image.Resampling.LANCZOS)
    c = Image.open(ap).convert('RGB').resize((480, 288), Image.Resampling.LANCZOS)
    row = Image.new('RGB', (480 * 2 + 16, 288 + 34), (14, 18, 22))
    row.paste(label(a.copy(), 'BEFORE v1'), (4, 26))
    row.paste(label(c.copy(), 'AFTER v2'), (488, 26))
    dd = ImageDraw.Draw(row)
    dd.text((4, 6), f'cover_v4_{b}.png  —  {NAMES[b]}', fill=(230, 236, 244))
    rows.append(row)

if rows:
    sheet = Image.new('RGB', (rows[0].width, sum(r.height + 8 for r in rows)), (10, 12, 16))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y)); y += r.height + 8
    sheet.save('build/qa-v7/compare-covers.png')
    print('compare-covers.png', sheet.size)

# ── 图集质量抽查 ──
report = []
actors = 'app/src/main/res/drawable-nodpi/actors_v4.png'
im = Image.open(actors).convert('RGBA')
report.append(f'actors_v4.png  {im.size[0]}x{im.size[1]}  mode=RGBA')

# 1) 空行 / 空帧
empty = []
for r in range(40):
    for f in range(4):
        t = im.crop((f * 160, r * 160, (f + 1) * 160, (r + 1) * 160))
        if t.getbbox() is None:
            empty.append((r, f))
report.append(f'  空单元格: {empty if empty else "无"}')

# 2) 帧间抖动（脚底）
jit_v, jit_h = [], []
for r in range(40):
    bbs = []
    for f in range(4):
        t = im.crop((f * 160, r * 160, (f + 1) * 160, (r + 1) * 160))
        m = t.split()[3].point(lambda v: 255 if v > 170 else 0)
        bbs.append(m.getbbox())
    if any(x is None for x in bbs):
        continue
    feet = [x[3] for x in bbs]
    cxs = [(x[0] + x[2]) / 2 for x in bbs]
    if max(feet) - min(feet) > 2:
        jit_v.append((r, max(feet) - min(feet)))
    if max(cxs) - min(cxs) > 3:
        jit_h.append((r, round(max(cxs) - min(cxs), 1)))
report.append(f'  帧间竖向抖动>2px: {jit_v if jit_v else "无"}')
report.append(f'  帧间横向抖动>3px: {jit_h if jit_h else "无"}')

# 3) 透明边缘发黑：检查 alpha 边缘的 RGB 是否异常暗
dark_edge = 0
for r in range(40):
    t = im.crop((0, r * 160, 160, (r + 1) * 160))
    px = t.load()
    for y in range(160):
        for x in range(160):
            a = px[x, y][3]
            if 40 < a < 210:                     # 半透明边缘
                rr, gg, bb = px[x, y][0], px[x, y][1], px[x, y][2]
                if rr + gg + bb < 24:            # 近黑
                    dark_edge += 1
report.append(f'  半透明边缘近黑像素: {dark_edge}  (描边本身是深色，<5000 视为正常)')

# 4) 颜色脏度：统计全图饱和度分布（过低=灰脏，过高且集中在暗部=脏）
import colorsys
sample_pixels = 0
low_sat = 0
for r in range(0, 40, 3):
    t = im.crop((0, r * 160, 640, (r + 1) * 160))
    small = t.resize((80, 20), Image.Resampling.BOX)
    px = small.load()
    for y in range(20):
        for x in range(80):
            rr, gg, bb, aa = px[x, y]
            if aa < 100:
                continue
            sample_pixels += 1
            mx, mn = max(rr, gg, bb), min(rr, gg, bb)
            sat = 0 if mx == 0 else (mx - mn) / mx
            if sat < 0.06 and mx < 90:
                low_sat += 1
report.append(f'  暗部低饱和(脏灰)占比: {low_sat}/{sample_pixels} = '
              f'{100.0*low_sat/max(1,sample_pixels):.1f}%')

# 5) 脚底 / 头顶分布
feet_all, head_all = [], []
for r in range(40):
    t = im.crop((0, r * 160, 160, (r + 1) * 160))
    m = t.split()[3].point(lambda v: 255 if v > 170 else 0)
    bb = m.getbbox()
    if bb:
        head_all.append(bb[1]); feet_all.append(bb[3])
report.append(f'  脚底 y: {min(feet_all)}..{max(feet_all)}   头顶 y: {min(head_all)}..{max(head_all)}')

# 6) props / floors 尺寸
for f in ['props_v4.png', 'ui_v4.png'] + [f'floor_v4_{i}.png' for i in range(6)] + \
         [f'cover_v4_{i}.png' for i in range(6)]:
    p = 'app/src/main/res/drawable-nodpi/' + f
    ii = Image.open(p)
    report.append(f'  {f}  {ii.size[0]}x{ii.size[1]}  {os.path.getsize(p)/1024:.0f} KB')

txt = '\n'.join(report)
open('build/qa-v7/atlas-audit.txt', 'w', encoding='utf-8').write(txt)
print(txt)
