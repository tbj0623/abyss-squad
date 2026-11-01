"""锚点稳定化：保证同一角色的 4 帧「脚底对齐」，消除帧间上下抖动。

问题：v1.0 里每帧各自做 fit_vertical，导致同一角色 4 帧的脚底位置不一致
（实测第 9 行脚底 155/151/156/157，抖动 6px → 播放时"跳"）。

修法：
  1. 生成阶段：每个角色的 4 帧先全部渲染出来，算出一个**共享的脚底基准**，
     再把 4 帧按同一基准平移（而不是各自独立平移）。帧间 BOB 起伏保留在
     角色内部（腿部/躯干已画），整体单元不会被逐帧重新对位。
  2. 保留 fit_vertical 的单帧兜底（防止顶部被切），但只对"越界"帧生效。
"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _art_core import Rig

_orig_final = Rig.final
TOP_PAD = 4
FOOT_MAX = 157


def _solid_bbox(tile, thresh=170):
    a = tile.split()[3].point(lambda v: 255 if v > thresh else 0)
    return a.getbbox()


def final(self, rim=('#a8d8e8', .40), rim_dir=(1, 1), outline=2, shadow=None,
          grain=0.0, key=0.55, floor_dark=0.90, glow_blur=9.0):
    out = _orig_final(self, rim=rim, rim_dir=rim_dir, outline=outline, shadow=shadow,
                      grain=grain, key=key, floor_dark=floor_dark, glow_blur=glow_blur)
    # 仅做"防切顶"兜底；真正的帧间对齐在 atlas 组装阶段统一处理
    bb = _solid_bbox(out)
    if bb is None:
        return out
    x0, y0, x1, y1 = bb
    if y0 >= TOP_PAD:
        return out
    shift = min(TOP_PAD - y0, max(0, FOOT_MAX - y1))
    if shift <= 0:
        return out
    shifted = Image.new('RGBA', out.size, (0, 0, 0, 0))
    shifted.paste(out, (0, shift))
    return shifted


def align_frames(frames):
    """把同一角色的 4 帧按脚底对齐（共享基准），同时保证不切顶。"""
    boxes = [_solid_bbox(f) for f in frames]
    valid = [b for b in boxes if b]
    if not valid:
        return frames
    # 共享脚底基准 = 所有帧脚底的中位数（稳健，抗单帧异常）
    feet = sorted(b[3] for b in valid)
    base_foot = feet[len(feet) // 2]
    # 顶部最高的那一帧决定整体最多能上移多少
    top_min = min(b[1] for b in valid)
    out = []
    for f, b in zip(frames, boxes):
        if b is None:
            out.append(f)
            continue
        dy = base_foot - b[3]            # 让脚底对齐到 base_foot
        # 上移会切顶时让步
        if b[1] + dy < TOP_PAD:
            dy = TOP_PAD - b[1]
        if dy == 0:
            out.append(f)
            continue
        tile = Image.new('RGBA', f.size, (0, 0, 0, 0))
        tile.paste(f, (0, dy))
        out.append(tile)
    del top_min
    return out


Rig.final = final
print('rig patched: frame anchor stabilization enabled')
