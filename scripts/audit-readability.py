"""v2.0 移动端可读性审计：最小字号 / alpha 下限 / 触摸目标尺寸。

用法: python scripts/audit-readability.py [--verbose]

检查三类问题：
  1. 字号 < 11 的绘制调用（任务要求全局最小 11 逻辑像素）
  2. alpha(color, n) 中 n < 150 的文字色（次要信息对比度过低）
  3. ui.hit(id, x, y, w, h) 中 w<44 或 h<44 的触摸目标

对每一处输出 文件:行 + 代码片段，便于人工判断是否是真问题
（装饰性极淡文字/纯视觉元素可以保留，脚本不自动改）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\11930\Desktop\dilao")
APP = ROOT / "app/src/main/java/com/dilao/abysssquad"

# 我的责任范围（v2.0 任务指定）
FILES = [
    APP / "Ui.java",
    APP / "screen/MenuScreen.java",
    APP / "screen/CharScreen.java",
    APP / "screen/CodexScreen.java",
    APP / "screen/MetaScreen.java",
    APP / "screen/SetScreen.java",
]

MIN_FONT = 11.0
MIN_ALPHA = 150
MIN_TOUCH = 44.0

# 绘制调用里"字号参数"的识别：ui.text/center/right/small/wrap 的第 4/5 个数字参数
TEXT_CALL = re.compile(
    r'ui\.(text|center|right|small|wrap|centerSmall)\s*\((.+?)\)\s*;', re.S)
ALPHA_CALL = re.compile(r'(?:ui|Ui)\.alpha\(\s*([^,]+?)\s*,\s*(\d+)\s*\)')
HIT_CALL = re.compile(
    r'ui\.hit\(\s*[^,]+,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*,\s*([-\d.]+)f?\s*\)')
# 字号字面量（如 10.5f / 9f），用于直接扫描
SIZE_LITERAL = re.compile(r'(?<![\w.])(\d+(?:\.\d+)?)f\b')


def find_small_fonts(path: Path):
    """找 ui.text/center/right/small 调用里作为 size 的小数字面量。"""
    out = []
    src = path.read_text(encoding="utf-8", errors="replace")
    lines = src.splitlines()
    for i, ln in enumerate(lines, 1):
        # 常见形态： ui.text(c, "...", x, y, 10.5f, color, true)
        for m in re.finditer(r'\bui\.(text|center|right|small)\s*\(', ln):
            tail = ln[m.end():]
            # 按逗号切，前两个是 c 和 s；s 可能含逗号（"a"+b），所以从右往左找 size
            parts = [p.strip() for p in tail.split(',')]
            if len(parts) < 4:
                continue
            for p in parts[2:]:
                mm = re.match(r'^(\d+(?:\.\d+)?)f$', p)
                if mm:
                    v = float(mm.group(1))
                    if v < MIN_FONT:
                        out.append((i, v, ln.strip()))
                    break
        # 另外抓 fit(...) 的 minSize 参数
        for m in re.finditer(r'ui\.fit\(([^)]*)\)', ln):
            args = [a.strip() for a in m.group(1).split(',')]
            if len(args) >= 4:
                mm = re.match(r'^(\d+(?:\.\d+)?)f$', args[3])
                if mm and float(mm.group(1)) < MIN_FONT:
                    out.append((i, float(mm.group(1)), ln.strip()))
    return out


def find_low_alpha(path: Path):
    out = []
    src = path.read_text(encoding="utf-8", errors="replace")
    lines = src.splitlines()
    for i, ln in enumerate(lines, 1):
        for m in ALPHA_CALL.finditer(ln):
            a = int(m.group(2))
            if a < MIN_ALPHA:
                # 找出这是不是文字色：同一行有 ui.text/center/right/small/wrap/fit
                is_text = bool(re.search(r'ui\.(text|center|right|small|wrap|centerSmall)', ln))
                out.append((i, a, is_text, ln.strip()))
    return out


def find_small_touch(path: Path):
    out = []
    src = path.read_text(encoding="utf-8", errors="replace")
    lines = src.splitlines()
    for i, ln in enumerate(lines, 1):
        for m in HIT_CALL.finditer(ln):
            w = float(m.group(3))
            h = float(m.group(4))
            if w < MIN_TOUCH or h < MIN_TOUCH:
                out.append((i, w, h, ln.strip()))
    return out


def main():
    verbose = "--verbose" in sys.argv
    grand = 0
    for path in FILES:
        if not path.exists():
            print(f"  [skip] {path.name} 不存在")
            continue
        fonts = find_small_fonts(path)
        lowa = find_low_alpha(path)
        touch = find_small_touch(path)
        n = len(fonts) + len(lowa) + len(touch)
        grand += n
        if n == 0:
            print(f"✓ {path.name}: 无问题")
            continue

        print(f"\n=== {path.name}  ({n} 处待判断) ===")
        if fonts:
            print(f"  ▸ 字号 < {MIN_FONT}:")
            for ln, v, code in fonts[:40]:
                print(f"    L{ln}: size={v}f")
                if verbose:
                    print(f"         {code[:120]}")
        if lowa:
            text_low = [x for x in lowa if x[2]]
            deco_low = [x for x in lowa if not x[2]]
            if text_low:
                print(f"  ▸ 文字 alpha < {MIN_ALPHA}:")
                for ln, a, _, code in text_low[:40]:
                    print(f"    L{ln}: alpha={a}")
                    if verbose:
                        print(f"         {code[:120]}")
            if deco_low:
                print(f"  · 装饰性 alpha < {MIN_ALPHA}: {len(deco_low)} 处（可保留）")
                if verbose:
                    for ln, a, _, code in deco_low[:15]:
                        print(f"    L{ln}: alpha={a}  {code[:100]}")
        if touch:
            print(f"  ▸ 触摸目标 < {MIN_TOUCH}x{MIN_TOUCH}:")
            for ln, w, h, code in touch[:30]:
                print(f"    L{ln}: {w:.0f}x{h:.0f}")

    print(f"\n{'='*50}\n合计待判断：{grand} 处")
    return 0


if __name__ == "__main__":
    sys.exit(main())
