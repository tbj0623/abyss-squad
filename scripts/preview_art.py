"""快速预览：python scripts/preview_art.py heroes|monsters|bosses|all"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import _art_core
from _art_core import CELL
from _art_roles import HEROES

ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / 'build' / 'qa-v4'
QA.mkdir(parents=True, exist_ok=True)


def sheet(items, cols, path, bg=(20, 28, 34), zoom=1):
    rows = (len(items) + cols - 1) // cols
    im = Image.new('RGB', (CELL * cols * zoom, CELL * rows * zoom), bg)
    for i, fn in enumerate(items):
        tile = fn(0)
        if zoom != 1:
            tile = tile.resize((CELL * zoom, CELL * zoom), Image.Resampling.NEAREST)
        im.paste(tile, ((i % cols) * CELL * zoom, (i // cols) * CELL * zoom), tile)
    im.save(path)
    print('->', path)


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'heroes'
    zoom = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    if what in ('heroes', 'all'):
        sheet(HEROES, 6, QA / '_heroes.png', zoom=zoom)
    if what in ('monsters', 'all'):
        import _art_monsters
        sheet(_art_monsters.MONSTERS, 5, QA / '_monsters.png', zoom=zoom)
    if what in ('bosses', 'all'):
        import _art_bosses
        sheet(_art_bosses.BOSSES, 4, QA / '_bosses.png', zoom=zoom)
