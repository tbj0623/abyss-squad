"""生成 build/qa-v7 的「改前 vs 改后」对比证据图。"""
import os
from PIL import Image, ImageDraw

os.makedirs('build/qa-v7', exist_ok=True)
B = 'build/qa-v7/before'
A = 'build/qa-v7/after'


def label(im, text, size=18):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, len(text) * size * .62 + 16, size + 12), fill=(0, 0, 0))
    d.text((8, 6), text, fill=(240, 245, 250))
    return im


def cmp_floor(biome, name):
    b = Image.open(f'{B}/floor_v4_{biome}.png').convert('RGB')
    a = Image.open(f'{A}/floor_v4_{biome}.png').convert('RGB')
    W, H = 560, 310
    b = b.resize((W, H), Image.Resampling.LANCZOS)
    a = a.resize((W, H), Image.Resampling.LANCZOS)
    out = Image.new('RGB', (W * 2 + 16, H + 34), (14, 18, 22))
    out.paste(label(b.copy(), 'BEFORE v1'), (4, 26))
    out.paste(label(a.copy(), 'AFTER v2'), (W + 12, 26))
    d = ImageDraw.Draw(out)
    d.text((4, 6), f'floor_v4_{biome}.png  —  {name}', fill=(230, 236, 244))
    return out


NAMES = ['苔光遗迹 Mosslight Ruins', '赤铜熔炉 Copper Forge', '月影书库 Moonshadow Archive',
         '霜蚀回廊 Frostbite Gallery', '熔火深渊 Molten Abyss', '虚空王座 Void Throne']

rows = [cmp_floor(i, NAMES[i]) for i in range(6)]
tw = rows[0].width
sheet = Image.new('RGB', (tw, sum(r.height + 8 for r in rows)), (10, 12, 16))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height + 8
sheet.save('build/qa-v7/compare-floors.png')
print('compare-floors.png', sheet.size)

# 角色动画：before vs after 的 4 帧并排（12 职业）
def role_strip(path):
    im = Image.open(path).convert('RGBA')
    out = Image.new('RGB', (160 * 4, 160 * 12), (20, 26, 32))
    for r in range(12):
        for f in range(4):
            t = im.crop((f * 160, r * 160, (f + 1) * 160, (r + 1) * 160))
            out.paste(t, (f * 160, r * 160), t)
    return out

rb = role_strip(f'{B}/actors_v4.png')
ra = role_strip(f'{A}/actors_v4.png')
out = Image.new('RGB', (160 * 4 * 2 + 20, 160 * 12 + 34), (14, 18, 22))
out.paste(label(rb, 'BEFORE v1', 20), (4, 28))
out.paste(label(ra, 'AFTER v2', 20), (160 * 4 + 16, 28))
d = ImageDraw.Draw(out)
d.text((4, 8), 'actors_v4.png  12 roles x 4 frames  (frame-anchor stabilized)', fill=(230, 236, 244))
out.save('build/qa-v7/compare-actors.png')
print('compare-actors.png', out.size)

# 道具前后
pb = Image.open(f'{B}/props_v4.png').convert('RGBA')
pa = Image.open(f'{A}/props_v4.png').convert('RGBA')
w = 160 * 6
h = 160 * 2
sheet = Image.new('RGB', (w * 2 + 16, h * 2 + 40), (14, 18, 22))
d = ImageDraw.Draw(sheet)
sheet.paste(label(pb.resize((w, h), Image.Resampling.LANCZOS).convert('RGB'), 'BEFORE v1', 18), (4, 30))
sheet.paste(label(pa.resize((w, h), Image.Resampling.LANCZOS).convert('RGB'), 'AFTER v2', 18), (w + 12, 30))
d.text((4, 8), 'props_v4.png  12 props', fill=(230, 236, 244))
sheet.save('build/qa-v7/compare-props.png')
print('compare-props.png', sheet.size)
