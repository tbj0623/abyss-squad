"""深渊小队 v1.0 —— 原创程序化美术生成（Pillow only，无任何外部素材/版权内容）。

运行：  python scripts/create_art.py
产物：  app/src/main/res/drawable-nodpi/
          actors_v4.png      640 x 6400   4 帧 × 40 行，单元 160
          props_v4.png       160 x 1920   12 行，单元 160
          floor_v4_0..5.png  1120 x 620   6 生态地板
          cover_v4_0..5.png  1000 x 600   6 生态主菜单插画
          ui_v4.png          1536 x 384   16 遗物 + 6 流派 + 12 职业
检查：  build/qa-v4/actor-sheet.png / floors.png / props.png / covers.png / ui.png

模块划分：
  _art_core.py      Rig：2x 超采样 + 定向光 + 描边 + 边缘光 + 自发光
  _art_heroes.py    共享骨架部件（躯干/腿/头/兜帽/披风/武器）
  _art_roles.py     12 职业
  _art_monsters.py  20 敌种
  _art_bosses.py    8 首领
  _art_props.py     12 道具
  _art_env.py       6 地板 + 6 封面
  _art_ui.py        UI 图标
"""
import sys
import time
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

ROOT = HERE.parent
OUT = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi'
QA = ROOT / 'build' / 'qa-v4'
OUT.mkdir(parents=True, exist_ok=True)
QA.mkdir(parents=True, exist_ok=True)

CELL = 160


def _grid_sheet(items, cols, path, bg=(22, 30, 36), zoom=1):
    n = len(items)
    rows = (n + cols - 1) // cols
    tile = CELL * zoom
    im = Image.new('RGB', (tile * cols, tile * rows), bg)
    for i, tile_img in enumerate(items):
        if zoom != 1:
            tile_img = tile_img.resize((tile, tile), Image.Resampling.LANCZOS)
        im.paste(tile_img, ((i % cols) * tile, (i // cols) * tile), tile_img)
    im.save(path)
    return im


def _fallback(row, frame):
    """通用兜底形状：有描边、有明暗、有帧差异。绝不中断脚本。"""
    from _art_core import Rig
    y = (0.0, 1.2, 0.0, -1.6)[frame & 3]
    r = Rig(9000 + row)
    c = ['#7a8a86', '#8a7a62', '#6a7a8a', '#8a6a7a'][row % 4]
    r.solid([(32, 60 + y), (48, 30 + y), (112, 28 + y), (128, 60 + y),
             (124, 122 + y), (96, 136), (48, 136), (28, 120 + y)],
            c, bevel=4.0, top=1.28, dark=.5)
    r.emit(80, 76 + y, 7.0, '#c8e8d8', glow=True)
    return r.final(rim=('#c8e8d8', .4), shadow=(80, 144, 40, 120), grain=.05)


def build_actors():
    import _art_fit  # noqa: F401  —— 垂直重定位 + 帧锚点稳定化
    from _art_roles import HEROES
    _align = getattr(_art_fit, 'align_frames', None)
    from _art_monsters import MONSTERS
    from _art_bosses import BOSSES

    assert len(HEROES) == 12, f'roles={len(HEROES)}'
    assert len(MONSTERS) == 20, f'monsters={len(MONSTERS)}'
    assert len(BOSSES) == 8, f'bosses={len(BOSSES)}'

    draws = list(HEROES) + list(MONSTERS) + list(BOSSES)
    atlas = Image.new('RGBA', (CELL * 4, CELL * len(draws)), (0, 0, 0, 0))
    t0 = time.time()
    fails = 0
    for row, fn in enumerate(draws):
        tiles = []
        for frame in range(4):
            try:
                tiles.append(fn(frame))
            except Exception as exc:
                fails += 1
                print(f'  ! row {row} frame {frame} 失败: {type(exc).__name__}: {exc}')
                tiles.append(_fallback(row, frame))
        # v2：同一行的 4 帧按共享脚底基准对齐，消除帧间上下抖动
        if _align is not None:
            try:
                tiles = _align(tiles)
            except Exception:
                pass
        for frame, tile in enumerate(tiles):
            atlas.paste(tile, (frame * CELL, row * CELL))
        if row % 8 == 7:
            print(f'  actors {row + 1}/{len(draws)}  ({time.time() - t0:.1f}s)')
    atlas.save(OUT / 'actors_v4.png', optimize=True)
    print(f'actors_v4.png {atlas.size[0]}x{atlas.size[1]}  失败帧={fails}')
    tiles = [atlas.crop((0, r * CELL, CELL, (r + 1) * CELL)) for r in range(len(draws))]
    _grid_sheet(tiles, 6, QA / 'actor-sheet.png', zoom=2)
    print('  -> actor-sheet.png')


def build_props():
    import _art_fit  # noqa: F401
    from _art_props import PROPS
    assert len(PROPS) == 12, f'props={len(PROPS)}'
    atlas = Image.new('RGBA', (CELL, CELL * len(PROPS)), (0, 0, 0, 0))
    for row, fn in enumerate(PROPS):
        try:
            tile = fn(0)
        except Exception as exc:
            print(f'  ! prop {row} 失败: {exc}')
            tile = _fallback(30 + row, 0)
        atlas.paste(tile, (0, row * CELL))
    atlas.save(OUT / 'props_v4.png', optimize=True)
    print(f'props_v4.png {atlas.size[0]}x{atlas.size[1]}')
    tiles = [atlas.crop((0, r * CELL, CELL, (r + 1) * CELL)) for r in range(len(PROPS))]
    _grid_sheet(tiles, 6, QA / 'props.png', zoom=2)
    print('  -> props.png')


def build_floors():
    from _art_env import BIOMES, FLOOR_W, FLOOR_H
    # v2：地板经过 enhanced_floor 精修（炉渣坑/冰裂/熔岩池/虚空裂隙/积灰/纵深）
    # 模块缺失时自动退回 v1 的 floor_tile，保证脚本永远能跑通
    try:
        from _art_floor_v2 import enhanced_floor as _tile
    except Exception as _e:
        print(f'  ! _art_floor_v2 不可用（{_e}），回退 v1 floor_tile')
        from _art_env import floor_tile as _tile
    ims = []
    for b in range(6):
        try:
            im = _tile(b)
        except Exception as exc:
            print(f'  ! floor {b} 失败: {exc}')
            im = Image.new('RGB', (FLOOR_W, FLOOR_H), BIOMES[b]['bg'])
        im.save(OUT / f'floor_v4_{b}.png', optimize=True)
        ims.append(im)
        print(f'floor_v4_{b}.png {im.size[0]}x{im.size[1]}  ({BIOMES[b]["name"]})')
    sheet = Image.new('RGB', (FLOOR_W // 2 * 2, FLOOR_H // 2 * 3), (10, 14, 18))
    for i, im in enumerate(ims):
        thumb = im.resize((FLOOR_W // 2, FLOOR_H // 2), Image.Resampling.LANCZOS)
        sheet.paste(thumb, ((i % 2) * (FLOOR_W // 2), (i // 2) * (FLOOR_H // 2)))
    sheet.save(QA / 'floors.png')
    print('  -> floors.png')


def build_covers():
    from _art_env import BIOMES, COVER_W, COVER_H
    # v2：6 张封面各自的生态主题物完全重做（不再是同一拱门换配色）
    try:
        from _art_cover_v2 import cover_art_v2 as _cover
    except Exception as _e:
        print(f'  ! _art_cover_v2 不可用（{_e}），回退 v1 cover_art')
        from _art_env import cover_art as _cover
    ims = []
    for b in range(6):
        try:
            im = _cover(b)
        except Exception as exc:
            print(f'  ! cover {b} 失败: {exc}')
            im = Image.new('RGB', (COVER_W, COVER_H), BIOMES[b]['bg'])
        im.save(OUT / f'cover_v4_{b}.png', optimize=True)
        ims.append(im)
        print(f'cover_v4_{b}.png {im.size[0]}x{im.size[1]}')
    sheet = Image.new('RGB', (COVER_W // 2 * 2, COVER_H // 2 * 3), (10, 14, 18))
    for i, im in enumerate(ims):
        thumb = im.resize((COVER_W // 2, COVER_H // 2), Image.Resampling.LANCZOS)
        sheet.paste(thumb, ((i % 2) * (COVER_W // 2), (i // 2) * (COVER_H // 2)))
    sheet.save(QA / 'covers.png')
    print('  -> covers.png')


def build_ui():
    from _art_ui import draw_ui_atlas, UC
    atlas = draw_ui_atlas()
    atlas.save(OUT / 'ui_v4.png', optimize=True)
    print(f'ui_v4.png {atlas.size[0]}x{atlas.size[1]}')
    bg = Image.new('RGB', (UC * 8, UC * 3), (22, 30, 36))
    for i in range(16):
        tile = atlas.crop((i * UC, 0, (i + 1) * UC, UC))
        bg.paste(tile, ((i % 8) * UC, (i // 8) * UC), tile)
    for i in range(12):
        row = 1 if i < 6 else 2
        idx = i if i < 6 else i - 6
        tile = atlas.crop((idx * UC, row * UC, (idx + 1) * UC, (row + 1) * UC))
        bg.paste(tile, ((i % 8) * UC, UC * 2 + (i // 8) * UC), tile)
    bg.save(QA / 'ui.png')
    print('  -> ui.png')


if __name__ == '__main__':
    only = sys.argv[1] if len(sys.argv) > 1 else None
    t0 = time.time()
    print('== 深渊小队 v1.0 美术生成 ==')
    if only in (None, 'actors'):
        build_actors()
    if only in (None, 'props'):
        build_props()
    if only in (None, 'floors'):
        build_floors()
    if only in (None, 'covers'):
        build_covers()
    if only in (None, 'ui'):
        build_ui()
    print(f'完成，用时 {time.time() - t0:.1f}s -> {OUT}')
