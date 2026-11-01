"""12 个场景道具，160x160 单元，脚底 y≈142。

0 木箱  1 补给箱  2 书架  3 石柱  4 荆棘  5 火盆
6 冰晶  7 图腾  8 宝箱(关)  9 宝箱(开)  10 传送门底座  11 祭坛
"""
import math
from _art_core import Rig, C, sh, mixc


def p_crate(t):
    r = Rig(4000)
    c = '#8a6a42'
    r.solid([(38, 148), (122, 148), (126, 142), (34, 142)], '#2a1e14', bevel=0, top=1, dark=1)
    r.solid([(34, 62), (126, 62), (130, 142), (30, 142)], c, bevel=4.0, top=1.30, dark=.52)
    # 板条
    for i in range(3):
        y = 74 + i * 24
        r.solid([(34, y), (126, y), (130, y + 18), (30, y + 18)],
                sh(c, 1.10 - i * .06), bevel=2.6, top=1.36, dark=.55)
        r.line([(36, y + 1), (126, y + 1)], sh(c, 1.42), 1.2)
    # 铁箍
    for x in (46, 108):
        r.solid([(x, 62), (x + 8, 62), (x + 9, 142), (x - 1, 142)], '#4a4a4e',
                bevel=2.0, top=1.4, dark=.55)
        for y in (72, 96, 120):
            r.ell((x + 1, y, x + 5, y + 4), '#8a8a90')
    # 斜撑
    r.line([(40, 132), (120, 76)], sh(c, .72), 5.0)
    r.line([(40, 130), (120, 74)], sh(c, 1.22), 1.6)
    # 顶部
    r.solid([(34, 56), (52, 46), (110, 46), (126, 58), (124, 66), (36, 66)],
            sh(c, 1.16), bevel=3.0, top=1.4, dark=.55)
    r.line([(38, 58), (108, 50)], sh(c, 1.46), 1.4)
    return r.final(rim=('#ffd8a0', .30), shadow=(80, 146, 52, 130), grain=.09, key=.4)


def p_supply(t):
    r = Rig(4001)
    c = '#7a6a44'
    r.solid([(38, 148), (122, 148), (124, 140), (36, 140)], '#241a10', bevel=0, top=1, dark=1)
    r.solid([(34, 58), (126, 58), (128, 142), (32, 142)], c, bevel=4.0, top=1.30, dark=.52)
    # 军绿补给箱
    r.solid([(38, 64), (122, 64), (124, 136), (36, 136)], '#4a5a3a',
            bevel=3.2, top=1.3, dark=.52)
    r.poly([(40, 66), (120, 66), (120, 84), (40, 84)], '#5a6c46')
    # 十字标记
    r.solid([(74, 92), (86, 92), (86, 124), (74, 124)], '#d8e8c8', bevel=1.6, top=1.2, dark=.8)
    r.solid([(62, 102), (98, 102), (98, 114), (62, 114)], '#d8e8c8', bevel=1.6, top=1.2, dark=.8)
    # 提手
    r.solid([(66, 46), (94, 46), (98, 58), (62, 58)], '#3a4430', bevel=2.4, top=1.4, dark=.5)
    r.ring(80, 46, 12, '#4a5a3a', 3.0)
    # 卡扣
    for x in (48, 112):
        r.solid([(x - 4, 84), (x + 4, 84), (x + 4, 96), (x - 4, 96)], '#9a9a80',
                bevel=1.6, top=1.4, dark=.55)
    r.emit(80, 60, 2.6, '#a8e8a0', glow=True, a=90)
    return r.final(rim=('#e8ffd0', .32), shadow=(80, 146, 52, 132), grain=.08, key=.4)


def p_bookshelf(t):
    r = Rig(4002)
    c = '#6a4a36'
    r.solid([(34, 152), (126, 152), (128, 146), (32, 146)], '#1e1410', bevel=0, top=1, dark=1)
    # 书架主体
    r.solid([(36, 20), (124, 20), (128, 146), (32, 146)], c, bevel=3.6, top=1.28, dark=.52)
    r.poly([(40, 24), (120, 24), (120, 142), (40, 142)], sh(c, .58))
    # 三层 + 书
    book_cols = [('#8a5a48', '#c8a070'), ('#4a6a7a', '#8ac8c8'), ('#7a6a48', '#d8c890'),
                 ('#6a4a6a', '#b8a0d8'), ('#5a7a52', '#a8d8a0')]
    for row in range(3):
        y = 30 + row * 38
        r.solid([(38, y + 30), (122, y + 30), (124, y + 36), (36, y + 36)],
                sh(c, 1.16), bevel=2.0, top=1.4, dark=.55)
        x = 42
        k = 0
        while x < 118:
            bw = 5 + (k * 3) % 6
            bh = 22 + (k * 5) % 9
            if x + bw > 120:
                break
            base, hi = book_cols[(row * 3 + k) % len(book_cols)]
            r.solid([(x, y + 30 - bh), (x + bw, y + 30 - bh), (x + bw, y + 29), (x, y + 29)],
                    base, bevel=1.2, top=1.4, dark=.55)
            r.line([(x + 1, y + 30 - bh + 2), (x + 1, y + 27)], hi, 1.0)
            x += bw + 1
            k += 1
    # 侧板
    r.solid([(32, 18), (42, 18), (44, 148), (30, 148)], sh(c, .86), bevel=2.4, top=1.36, dark=.55)
    r.solid([(118, 18), (130, 18), (132, 148), (116, 148)], sh(c, .86),
            bevel=2.4, top=1.36, dark=.55)
    # 顶部装饰
    r.solid([(28, 10), (132, 10), (130, 22), (30, 22)], sh(c, 1.14),
            bevel=2.4, top=1.4, dark=.55)
    r.emit(80, 14, 3.0, '#c8d8ff', glow=True, a=90)
    return r.final(rim=('#ffd8a0', .30), shadow=(80, 150, 54, 124), grain=.08, key=.4)


def p_pillar(t):
    r = Rig(4003)
    c = '#8a8a80'
    # 柱础
    r.solid([(36, 152), (124, 152), (126, 146), (34, 146)], '#22201c', bevel=0, top=1, dark=1)
    r.solid([(34, 126), (126, 126), (128, 142), (32, 142)], sh(c, .82),
            bevel=3.0, top=1.36, dark=.5)
    # 柱身
    r.solid([(50, 34), (110, 34), (114, 128), (46, 128)], c, bevel=3.6, top=1.26, dark=.5)
    for i in range(4):  # 凹槽
        x = 58 + i * 15
        r.line([(x, 38), (x - 2, 124)], sh(c, .62), 2.6)
        r.line([(x + 2, 38), (x, 124)], sh(c, 1.26), 1.6)
    # 柱头
    r.solid([(40, 26), (120, 26), (124, 40), (36, 40)], sh(c, 1.12),
            bevel=3.0, top=1.4, dark=.52)
    r.solid([(34, 14), (126, 14), (124, 28), (36, 28)], sh(c, 1.2),
            bevel=3.0, top=1.42, dark=.55)
    r.line([(40, 18), (120, 18)], sh(c, 1.5), 1.4)
    # 裂纹
    r.line([(76, 44), (82, 70), (74, 96)], sh(c, .58), 1.4)
    return r.final(rim=('#e8e0c8', .30), shadow=(80, 150, 54, 120), grain=.07, key=.4)


def p_thorn(t):
    r = Rig(4004)
    c = '#3a4a30'
    r.solid([(36, 150), (124, 150), (126, 144), (34, 144)], '#181c12', bevel=0, top=1, dark=1)
    # 荆棘丛：多条弯曲枝 + 尖刺
    for i in range(7):
        bx = 44 + i * 12
        mid = 60 + (i % 3) * 18
        pts = [(bx, 140), (bx + (i - 3) * 5, mid + 20), (bx + (i - 3) * 9, mid),
               (bx + (i - 3) * 6, mid - 26)]
        r.line(pts, sh(c, .8 + (i % 3) * .1), 4.2)
        r.line([(p[0] - .6, p[1]) for p in pts], sh(c, 1.4), 1.2)
        for k in range(3):
            t2 = k / 3
            px = bx + (i - 3) * 9 * t2
            py = 140 + (mid - 26 - 140) * t2
            sgn = 1 if k % 2 else -1
            r.poly([(px, py), (px + sgn * 11, py - 5), (px + sgn * 2, py + 3)],
                   sh(c, 1.1))
    # 红色浆果/花
    for i, (bx, by) in enumerate(((56, 88), (78, 66), (100, 92), (68, 108))):
        r.ell((bx - 4, by - 4, bx + 4, by + 4), '#8a3038')
        r.emit(bx, by, 2.2, '#ff7a6a', glow=False, a=180)
    return r.final(rim=('#a8c890', .34), shadow=(80, 150, 52, 118), grain=.08, key=.4)


def p_brazier(t):
    r = Rig(4005)
    c = '#5a5a5c'
    r.solid([(36, 150), (124, 150), (126, 144), (34, 144)], '#18181a', bevel=0, top=1, dark=1)
    # 三脚
    for s in (-1, 0, 1):
        r.line([(80 + s * 4, 112), (80 + s * 26, 144)], sh(c, .74), 5.0)
    # 火盆
    r.solid([(46, 94), (114, 94), (108, 118), (52, 118)], c, bevel=3.4, top=1.34, dark=.5)
    r.gell((50, 88, 110, 102), sh(c, 1.12), .1, .5)
    r.ell((54, 90, 106, 100), '#2a1a12')
    # 火焰（多层）
    r.gell((56, 52, 104, 96), '#d86a2a', .1, .5)
    r.emit(80, 74, 9.0, '#ff9a3a', glow=True, a=215)
    r.gell((64, 62, 96, 94), '#ffb050', .1, .5)
    r.gell((70, 72, 90, 94), '#fff0b0', .1, .5)
    for i in range(5):  # 火舌
        bx = 62 + i * 9
        wob = math.sin(i * 1.4) * 5
        r.poly([(bx, 82), (bx + 3 + wob, 48 + (i % 2) * 10), (bx + 7, 82)],
               C('#ffca6a', 220))
    # 火星
    for i in range(6):
        r.emit(64 + (i * 37) % 32, 34 + (i * 13) % 26, 1.8, '#ffd890', glow=False, a=190)
    return r.final(rim=('#ffb060', .48), shadow=(80, 150, 50, 118), grain=.05, key=.4,
                   glow_blur=8.0)


def p_icecrystal(t):
    r = Rig(4006)
    r.solid([(38, 150), (122, 150), (124, 144), (36, 144)], '#16202a', bevel=0, top=1, dark=1)
    # 主晶体
    r.solid([(80, 14), (108, 66), (96, 132), (64, 132), (52, 66)],
            C('#a8d8f0', 245), bevel=3.2, top=1.5, dark=.55, hi=1.9)
    r.poly([(80, 22), (98, 66), (88, 126), (72, 126), (61, 66)], C('#d8f0ff', 248))
    r.poly([(80, 26), (90, 66), (84, 118), (78, 118), (69, 66)], C('#f8fdff', 250))
    r.line([(80, 18), (80, 126)], C('#ffffff', 220), 1.4)
    # 侧晶
    r.solid([(50, 66), (66, 96), (58, 138), (40, 138), (34, 96)],
            C('#8cc8e8', 240), bevel=2.6, top=1.5, dark=.6)
    r.solid([(110, 58), (126, 92), (120, 138), (102, 138), (96, 94)],
            C('#8cc8e8', 240), bevel=2.6, top=1.5, dark=.6)
    r.emit(80, 70, 9.0, '#c8ecff', glow=True, a=130)
    for i in range(5):
        r.emit(54 + (i * 23) % 50, 100 + (i * 17) % 34, 1.8, '#e8f8ff', glow=False, a=150)
    return r.final(rim=('#e8f8ff', .48), shadow=(80, 150, 50, 118), grain=.04, key=.4,
                   glow_blur=8.0)


def p_totem(t):
    r = Rig(4007)
    c = '#6a5044'
    r.solid([(36, 152), (124, 152), (126, 146), (34, 146)], '#180f0c', bevel=0, top=1, dark=1)
    # 主柱
    r.solid([(56, 20), (104, 20), (110, 146), (50, 146)], c, bevel=3.6, top=1.26, dark=.5)
    r.poly([(60, 24), (100, 24), (104, 142), (56, 142)], sh(c, .60))
    # 三段雕刻面
    for i in range(3):
        y = 32 + i * 38
        r.solid([(58, y), (102, y), (103, y + 30), (57, y + 30)],
                sh(c, 1.16), bevel=2.4, top=1.4, dark=.55)
        # 眼/嘴
        r.ell((66, y + 8, 74, y + 16), '#12100c')
        r.ell((86, y + 8, 94, y + 16), '#12100c')
        r.emit(70, y + 12, 2.2, '#ffd070', glow=False, a=210)
        r.emit(90, y + 12, 2.2, '#ffd070', glow=False, a=210)
        r.poly([(70, y + 20), (90, y + 20), (86, y + 27), (74, y + 27)], '#12100c')
    # 顶部羽冠
    for i in range(5):
        bx = 56 + i * 12
        r.poly([(bx, 22), (bx + 4, -2 + (i % 2) * 8), (bx + 10, 22)],
               ['#9a4a3a', '#c8a860', '#4a6a7a'][i % 3])
    # 发光符文
    r.emit(80, 96, 6.0, '#ffb060', glow=True, a=160)
    r.ring(80, 96, 10, C('#ffd080', 200), 1.8)
    return r.final(rim=('#ffd8a0', .42), shadow=(80, 150, 50, 120), grain=.07, key=.4)


def p_chest_closed(t):
    r = Rig(4008)
    c = '#8a6a3a'
    r.solid([(38, 150), (122, 150), (124, 144), (36, 144)], '#1e160c', bevel=0, top=1, dark=1)
    # 箱体
    r.solid([(38, 86), (122, 86), (124, 142), (36, 142)], c, bevel=3.8, top=1.3, dark=.52)
    r.poly([(42, 90), (118, 90), (120, 138), (40, 138)], sh(c, 1.08))
    # 拱形箱盖
    r.solid([(36, 60), (50, 40), (110, 40), (124, 60), (122, 90), (38, 90)],
            sh(c, 1.18), bevel=3.4, top=1.42, dark=.55)
    r.poly([(40, 62), (52, 46), (108, 46), (120, 62), (118, 86), (42, 86)],
           sh(c, 1.3))
    # 金属包边
    for x in (48, 104):
        r.solid([(x, 40), (x + 9, 40), (x + 9, 142), (x, 142)], '#b89850',
                bevel=2.2, top=1.4, dark=.55)
    r.line([(40, 88), (120, 88)], '#c8a860', 3.2)
    # 锁
    r.solid([(72, 82), (90, 82), (90, 108), (72, 108)], '#d8b860',
            bevel=2.4, top=1.44, dark=.55)
    r.ell((77, 88, 85, 96), '#5a4418')
    r.ell((79, 96, 83, 102), '#5a4418')
    r.emit(81, 92, 2.2, '#ffe8a0', glow=True, a=110)
    # 顶部
    r.line([(50, 44), (108, 44)], sh(c, 1.5), 1.4)
    return r.final(rim=('#ffe0a0', .42), shadow=(80, 150, 52, 128), grain=.06, key=.4)


def p_chest_open(t):
    r = Rig(4009)
    c = '#8a6a3a'
    r.solid([(38, 150), (122, 150), (124, 144), (36, 144)], '#1e160c', bevel=0, top=1, dark=1)
    # 打开的箱体
    r.solid([(38, 86), (122, 86), (124, 142), (36, 142)], c, bevel=3.8, top=1.3, dark=.52)
    r.poly([(42, 90), (118, 90), (120, 138), (40, 138)], sh(c, 1.08))
    # 内部暗 + 金光
    r.solid([(44, 86), (116, 86), (114, 100), (46, 100)], '#2a1e10',
            bevel=2.0, top=1.3, dark=.7)
    r.emit(80, 96, 8.4, '#ffe08a', glow=True, a=200)
    r.gell((60, 88, 100, 102), '#ffe8a0', .1, .5)
    # 掀开的盖子（向后翻）
    r.solid([(36, 82), (46, 46), (114, 46), (124, 82), (122, 74), (38, 74)],
            sh(c, 1.16), bevel=3.2, top=1.4, dark=.55)
    r.poly([(40, 78), (50, 50), (110, 50), (120, 78)], sh(c, .52))
    for x in (48, 104):
        r.solid([(x, 46), (x + 9, 46), (x + 9, 86), (x, 86)], '#b89850',
                bevel=2.2, top=1.4, dark=.55)
    # 宝光
    for i in range(6):
        a = i * math.pi / 3
        r.line([(80, 92), (80 + math.cos(a) * 26, 92 + math.sin(a) * 20)],
               C('#ffe8a0', 190), 1.6)
    r.emit(80, 92, 5.0, '#fff4c0', glow=False)
    return r.final(rim=('#ffe0a0', .46), shadow=(80, 150, 52, 128), grain=.06, key=.4)


def p_portal(t):
    r = Rig(4010)
    c = '#4a6a68'
    r.solid([(30, 152), (130, 152), (132, 146), (28, 146)], '#0e1a1c', bevel=0, top=1, dark=1)
    # 台阶
    r.solid([(28, 130), (132, 130), (134, 142), (26, 142)], sh(c, .82),
            bevel=2.8, top=1.36, dark=.5)
    r.solid([(40, 116), (120, 116), (124, 132), (36, 132)], sh(c, .92),
            bevel=2.8, top=1.36, dark=.5)
    # 传送门底座的立柱
    for s in (-1, 1):
        x = 80 + s * 36
        r.solid([(x - 8, 24), (x + 8, 24), (x + 9, 120), (x - 9, 120)],
                sh(c, 1.0), bevel=2.8, top=1.38, dark=.52)
        r.solid([(x - 12, 18), (x + 12, 18), (x + 10, 30), (x - 10, 30)],
                sh(c, 1.2), bevel=2.4, top=1.44, dark=.55)
    # 拱
    r.arc((44, 0, 116, 96), 180, 360, sh(c, 1.16), 8.0)
    r.arc((44, 0, 116, 96), 180, 360, sh(c, 1.44), 2.4)
    # 传送门能量
    r.gell((52, 34, 108, 122), C('#4aa89a', 110), .1, .5)
    r.emit(80, 76, 11.0, '#8ae8d0', glow=True, a=170)
    r.ell((68, 60, 92, 94), C('#a8f8e0', 180))
    # 地面法阵
    for i, rad in enumerate((26, 34)):
        r.ring(80, 122, rad, C('#8ae8d0', 150 - i * 40), 1.6)
    return r.final(rim=('#a8f8e0', .5), shadow=(80, 150, 50, 122), grain=.04, key=.4,
                   glow_blur=8.0)


def p_altar(t):
    r = Rig(4011)
    c = '#7a7468'
    r.solid([(30, 152), (130, 152), (132, 146), (28, 146)], '#18181a', bevel=0, top=1, dark=1)
    # 基座
    r.solid([(34, 118), (126, 118), (130, 142), (30, 142)], sh(c, .86),
            bevel=3.2, top=1.36, dark=.5)
    r.solid([(44, 96), (116, 96), (120, 120), (40, 120)], c, bevel=3.2, top=1.36, dark=.52)
    # 台面
    r.solid([(32, 86), (128, 86), (132, 100), (28, 100)], sh(c, 1.16),
            bevel=3.0, top=1.42, dark=.55)
    r.line([(36, 88), (126, 88)], sh(c, 1.5), 1.5)
    # 铭文
    for i in range(5):
        r.line([(48 + i * 12, 104), (52 + i * 12, 114)], sh(c, .58), 1.6)
    # 悬浮祭品（宝石 + 光柱）
    r.emit(80, 66, 9.0, '#ffe8a0', glow=True, a=190)
    r.solid([(80, 40), (94, 62), (80, 84), (66, 62)], '#ffd870',
            bevel=2.4, top=1.6, dark=.55)
    r.poly([(80, 46), (88, 62), (80, 77), (72, 62)], '#fff4c8')
    # 光柱
    r.gell((62, 20, 98, 88), C('#ffe8a0', 62), .1, .4)
    # 侧烛台
    for s in (-1, 1):
        x = 80 + s * 40
        r.solid([(x - 4, 60), (x + 4, 60), (x + 5, 88), (x - 5, 88)], '#9a8250',
                bevel=1.8, top=1.4, dark=.55)
        r.emit(x, 54, 3.2, '#ffe0a0', glow=True, a=190)
    return r.final(rim=('#ffe8b0', .48), shadow=(80, 150, 52, 122), grain=.06, key=.4,
                   glow_blur=8.0)


PROPS = [p_crate, p_supply, p_bookshelf, p_pillar, p_thorn, p_brazier,
         p_icecrystal, p_totem, p_chest_closed, p_chest_open, p_portal, p_altar]
