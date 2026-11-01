"""最终 QA：1) 浅底合成查 halo 2) 用 v1 函数重建 before 封面做对比。"""
import os, sys
sys.path.insert(0, 'scripts')
from PIL import Image, ImageDraw
import importlib

os.makedirs('build/qa-v7', exist_ok=True)

# ══ 1. 浅底合成：检测描边/泛光是否产生难看的黑边或白边 ══
im = Image.open('app/src/main/res/drawable-nodpi/actors_v4.png').convert('RGBA')
sheet = Image.new('RGB', (160 * 8 * 2, 160 * 2), (0, 0, 0))
# 深底 / 浅底 两种背景各放一行（12 职业 frame0）
dark = Image.new('RGB', (160 * 8, 160), (14, 20, 26))
light = Image.new('RGB', (160 * 8, 160), (222, 226, 220))
for row_bg, ypos in ((dark, 0), (light, 160)):
    for i in range(8):
        t = im.crop((0, i * 160, 160, (i + 1) * 160))
        row_bg.paste(t, (i * 160, 0), t)
    sheet.paste(row_bg, (0, ypos))
sheet = sheet.resize((1280, 320), Image.Resampling.LANCZOS)
sheet.save('build/qa-v7/edge-check-dark-vs-light.png')
print('edge-check saved')

# ══ 2. 用 v1 floor/cover 函数重建 before 封面 ══
try:
    import _art_env
    importlib.reload(_art_env)
    for b in range(6):
        v1 = _art_env.cover_art(b)
        v1.save(f'build/qa-v7/before/cover_v4_{b}.png')
    print('v1 covers rebuilt for comparison')
except Exception as e:
    print('rebuild v1 cover failed:', e)
