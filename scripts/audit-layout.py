"""静态排版审计：扫描 screen/*.java 中 panel 与 text/right/center 的坐标关系。

思路（保守、低误报）：
1. 收集所有 ui.panel(c, X, Y, W, H, ...) 的字面量矩形。
2. 收集同一方法内 ui.text/right/center/ghost/button 的字面量坐标。
3. 对每个 text 检查它是否落在某个 panel 内却越界（y 超出底部、x+w 超出右侧）。
   由于 measureText 需要真实字体，这里只用「字面量 x 已超出 panel 右边界」这种
   确定越界的情况，以及对 y 的确定越界。

输出：越界清单（文件:行 + 说明）。
"""
import re, sys
from pathlib import Path

ROOT = Path(r"C:\Users\11930\Desktop\dilao")
FILES = ["MenuScreen", "CharScreen", "CodexScreen", "MetaScreen", "SetScreen", "BattleHud",
         "MapScreen", "DraftScreen", "ShopScreen", "EventScreen", "CampScreen", "ResultScreen"]

# ui.panel(c, x, y, w, h, r, fill, stroke)
PANEL = re.compile(r'ui\.panel\(\s*c\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,')
# ui.text(c, "...", x, y, size, ...)
TEXT  = re.compile(r'ui\.(text|right|center)\(\s*c\s*,\s*[^,]+,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?')
BOOL  = re.compile(r'ui\.(button|ghost)\(\s*c\s*,\s*[^,]+,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?')

def num(s):
    try: return float(s)
    except: return None

def main():
    issues = []
    for name in FILES:
        p = ROOT / "app/src/main/java/com/dilao/abysssquad/screen" / f"{name}.java"
        if not p.exists():
            continue
        lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
        panels = []
        for i, ln in enumerate(lines, 1):
            m = PANEL.search(ln)
            if m:
                x, y, w, h = (num(g) for g in m.groups()[:4])
                if None not in (x, y, w, h):
                    panels.append((i, x, y, w, h))

        for i, ln in enumerate(lines, 1):
            # 只检查某些明显越界的字面量
            m = TEXT.search(ln)
            if m:
                kind, x, y, size = m.group(1), num(m.group(2)), num(m.group(3)), num(m.group(4))
                if None in (x, y, size):
                    continue
                # 找最近的、包含该 y 的 panel（允许 y 在 panel 上下 4px 内）
                best = None
                for (pl, px, py, pw, ph) in panels:
                    if py - 4 <= y <= py + ph + 4 and px - 4 <= x <= px + pw + 4:
                        if best is None or (py <= y <= py + ph):
                            best = (pl, px, py, pw, ph)
                if best:
                    pl, px, py, pw, ph = best
                    if y > py + ph:
                        issues.append((name, i, f"text 基线 y={y} 超出 panel(y={py},h={ph}) 底部 {y-(py+ph):.1f}px"))
                    if kind != "right" and x > px + pw:
                        issues.append((name, i, f"text x={x} 超出 panel 右边界 {x-(px+pw):.1f}px"))
            mb = BOOL.search(ln)
            if mb:
                kind = mb.group(1); x = num(mb.group(2)); y = num(mb.group(3))
                w = num(mb.group(4)); h = num(mb.group(5))
                if None in (x, y, w, h):
                    continue
                if x + w > 1440:
                    issues.append((name, i, f"{kind} 右边界 {x+w:.0f} 超出画布 1440"))
                if y + h > 810:
                    issues.append((name, i, f"{kind} 下边界 {y+h:.0f} 超出画布 810"))
                if x < 0 or y < 0:
                    issues.append((name, i, f"{kind} 负坐标 x={x} y={y}"))

    if not issues:
        print("NO ISSUES FOUND")
        return 0
    print(f"FOUND {len(issues)} potential issues:\n")
    for name, line, msg in issues:
        print(f"  {name}.java:{line}  {msg}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
