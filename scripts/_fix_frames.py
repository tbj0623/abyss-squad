import re

# ══════════════════════════════════════════════════════════════
# 修复：4 帧循环里 f0 与 f2 完全相同 → 所谓"4 帧动画"其实只有 3 个姿势
# 正确做法：一个完整步幅 = 4 帧（左前 → 过渡 → 右前 → 过渡）
#   SWING 必须正负交替，而不是 0/+x/0/-x（中间两帧才是 0）
# ══════════════════════════════════════════════════════════════

p = 'scripts/_art_heroes.py'
s = open(p, encoding='utf-8').read()

old = """BOB = (0.0, 1.0, 0.0, -1.6)
SWING = (0.0, 2.0, 0.0, -2.0)
CAPE = (0.0, -2.8, 0.6, 2.4)"""

new = """# 4 帧 = 一个完整步幅：f0 左腿前 / f1 过渡(低) / f2 右腿前 / f3 过渡(高)
# BOB 用两个起伏周期（每个步幅身体沉两次），SWING 正负交替（腿真的换边），
# CAPE 半周期滞后（披风拖尾）。这样 4 帧两两互不相同。
BOB = (-0.9, 0.9, -0.9, 0.9)
SWING = (2.2, 0.0, -2.2, 0.0)
CAPE = (1.5, -1.3, -1.5, 1.3)"""

assert old in s, 'phase constants not found'
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
print('heroes: 4-frame real stride')

# ══════════════════════════════════════════════════════════════
# 敌种：同样的 f0/f2 重复问题。逐个把 (a, b, a, c) 形式改成真正交替。
# 大部分敌种用的是 y = (0.0, x, 0.0, y) / sw = (0.0, a, 0.0, -a)
# 处理方式：把垂直起伏改为相位错开，把水平摆动改为正负交替。
# ══════════════════════════════════════════════════════════════
p = 'scripts/_art_monsters.py'
s = open(p, encoding='utf-8').read()

# 常见模式 1: y = (0.0, A, 0.0, B)
pat_y = re.compile(r"y = \(0\.0, ([\d.\-]+), 0\.0, ([\d.\-]+)\)\[t & 3\]")


def yrep(m):
    a, b = m.group(1), m.group(2)
    # 改为四帧各自不同：0, +a, -0.4a, +b
    try:
        av = float(a)
    except ValueError:
        av = 1.0
    mid = round(-av * 0.45, 2)
    return "y = (0.0, %s, %s, %s)[t & 3]" % (a, mid, b)


s, n_y = pat_y.subn(yrep, s)

# 常见模式 2: sw = (0.0, A, 0.0, -A)  → 正负交替
pat_sw = re.compile(r"sw = \(0\.0, ([\d.\-]+), 0\.0, (-?[\d.\-]+)\)\[t & 3\]")
s, n_sw = pat_sw.subn(lambda m: "sw = (%s, 0.0, -%s, 0.0)[t & 3]" % (m.group(1), m.group(1)),
                      s)

# 常见模式 3: drop = (0.0, 4.0, 0.0, -4.0)[(t + i) & 3]  → 交替
pat_dr = re.compile(r"\(0\.0, ([\d.\-]+), 0\.0, ([\d.\-]+)\)\[\(t \+ i\) & 3\]")
s, n_dr = pat_dr.subn(lambda m: "(%s, 0.0, -%s, 0.0)[(t + i) & 3]" % (m.group(1), m.group(1)), s)

# 常见模式 4: p = (0.0, .8, 0.0, -.8)[t & 3]
pat_p = re.compile(r"p = \(0\.0, ([\d.\-]+), 0\.0, (-?[\d.\-]+)\)\[t & 3\]")
s, n_p = pat_p.subn(lambda m: "p = (%s, 0.0, -%s, 0.0)[t & 3]" % (m.group(1), m.group(1)), s)

open(p, 'w', encoding='utf-8').write(s)
print('monsters: y=%d sw=%d drop=%d p=%d patterns fixed' % (n_y, n_sw, n_dr, n_p))

# ══════════════════════════════════════════════════════════════
# 首领：明显动作（武器抬起/核心脉冲/翅膀展开）原本已有，检查 f0/f2
# ══════════════════════════════════════════════════════════════
p = 'scripts/_art_bosses.py'
s = open(p, encoding='utf-8').read()
pat_b = re.compile(r"y = \(0\.0, ([\d.\-]+), 0\.0, ([\d.\-]+)\)\[t & 3\]")


def brep(m):
    a = float(m.group(1))
    mid = round(-a * 0.4, 2)
    return "y = (0.0, %s, %s, %s)[t & 3]" % (m.group(1), mid, m.group(2))


s, n_b = pat_b.subn(brep, s)
pat_bp = re.compile(r"pulse = \(0\.0, ([\d.\-]+), 0\.0, ([\d.\-]+)\)\[t & 3\]")
s, n_bp = pat_bp.subn(lambda m: "pulse = (0.0, %s, %s, %s)[t & 3]"
                      % (m.group(1), round(float(m.group(1)) * .45, 2), m.group(2)), s)
open(p, 'w', encoding='utf-8').write(s)
print('bosses: y=%d pulse=%d patterns fixed' % (n_b, n_bp))
