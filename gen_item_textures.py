from PIL import Image

OUT = "src/main/resources/assets/brief_guard/textures/item"
W = H = 32

# 旧版(初版/第二版)风格轮廓：实心一条的三角内裤 —— 宽腰带 + 无开叉的圆润下摆。
# 顶部为宽腰带带身(含收口/顶线/分隔线)，下方为完整实心的身体，向中间收成圆底。
ROWS = {
    4:(3,28),
    5:(2,29), 6:(2,29), 7:(2,29), 8:(2,29), 9:(2,29),      # 腰带带身
    10:(1,30), 11:(1,30), 12:(1,30), 13:(1,30), 14:(1,30),  # 髋部最宽
    15:(2,28),
    16:(5,26), 17:(5,26),
    18:(7,24),
    19:(8,23), 20:(8,23),
    21:(9,22),
    22:(10,21),
    23:(11,20), 24:(11,20),
    25:(12,19),
    26:(13,18),
}

def in_briefs(x, y):
    if y not in ROWS: return False
    lo, hi = ROWS[y]
    return lo <= x <= hi

def is_waist(y):
    return 4 <= y <= 9

def mix(c, f):
    return (max(0,min(255,int(c[0]*f))), max(0,min(255,int(c[1]*f))), max(0,min(255,int(c[2]*f))))

# 主题基底色
themes = {
    "mechanical":   (96,102,116),
    "seven_curses": (74,30,94),
    "hi_teeth":     (216,208,190),
    "firework":     (24,42,104),
    "briefs_briefs":(74,110,168),
    "poor":         (154,138,114),
    "broken":       (122,111,95),
    "heavy":        (90,95,102),
    "rainbow":      (0,0,0),
    "curry":        (209,138,30),
    "ender_pearl":  (31,95,79),
    "creeper":      (95,191,63),
    "mirror":       (190,214,235),
}

def waist_shade(base, x, y):
    if y == 4:  return mix(base, 0.80)          # 收口滚边
    if y == 5:  return mix(base, 0.55)          # 顶线(压暗)
    if y == 9:  return mix(base, 0.58)          # 腰带与身体分隔线
    # y6~8 腰带带身：中段略亮
    f = 1.06 if y == 7 else (1.00 if y == 6 else 0.93)
    # 腰带左右两端略暗
    if x <= 4 or x >= 27: f *= 0.88
    return mix(base, f)

def body_shade(base, x, y):
    t = (y - 10) / 16.0                         # 0 顶 -> 1 底
    d = abs(x - 15.5) / 13.5                    # 0 中心 -> 1 边缘
    hz = max(0.0, 1.0 - d * 1.7)                # 中央竖向高光带
    f = 1.26 - t * 0.60 + 0.20 * hz             # 上亮下暗 + 中心高光
    return mix(base, f)

RAINBOW = [(230,40,40),(245,140,35),(245,220,45),(80,200,70),(70,150,250),(150,80,230)]

def rainbow_shade(x, y):
    idx = int((x / 31.0) * (len(RAINBOW) - 1) + 0.5)
    c = RAINBOW[idx]
    t = (y - 10) / 16.0
    d = abs(x - 15.5) / 13.5
    hz = max(0.0, 1.0 - d * 1.7)
    f = 1.20 - t * 0.52 + 0.16 * hz
    return mix(c, f)

def accent(base, x, y, kind):
    """返回该主题在 (x,y) 的图案颜色；None 表示用底纹/底色。"""
    # 腰带中央徽记区 (x13-18, y6-8)
    emblem = 13 <= x <= 18 and 6 <= y <= 8
    if kind == "mechanical":
        if emblem and x in (13,18): return (232,218,150)       # 黄铜铆钉
        if emblem and 15 <= x <= 16 and y == 7: return (232,218,150)
        if (x in (5,26) and y == 7): return (210,196,140)      # 侧铆钉
        if x == 16 and y in (12,17): return (150,158,172)      # 躯干铆钉
        if x in (8,23) and y == 20: return (150,158,172)
    if kind == "seven_curses":
        if emblem and (x + y) % 2 == 0: return (196,92,238)    # 咒纹
        if (x * 3 + y * 7) % 11 == 0 and 11 <= y <= 24: return (160,70,200)
    if kind == "hi_teeth":
        if emblem and x in (14,17) and y in (7,8): return (250,250,244)
        if 12 <= y <= 24 and x % 2 == 0 and (x + y) % 3 == 0: return (250,250,244)  # 齿粒
    if kind == "firework":
        if emblem and (x == 16 or (x in (14,17) and y == 7)): return (255,190,80)
        if (x * 5 + y * 3) % 9 == 0 and 11 <= y <= 24: return (255,150,60)
    if kind == "briefs_briefs":
        if emblem and 14 <= x <= 17 and y == 7: return (245,245,250)  # 白色内衬扣
        if y in (13,14) and x % 4 == 0: return (52,84,140)            # 缝线
        if y in (19,21) and x % 4 == 2: return (52,84,140)
    if kind == "poor":
        if emblem and 13 <= x <= 18 and y == 7: return (232,224,196)  # 补丁
        if (x * 7 + y * 11) % 13 == 0 and 11 <= y <= 22: return mix(base, 0.60)
        if x in (12,13) and 12 <= y <= 15: return (232,224,196)       # 一角补丁
    if kind == "broken":
        if emblem and (x + y) % 3 == 0: return (60,52,46)             # 破口
        if (x * 3 + y * 5) % 9 == 0 and 11 <= y <= 23: return (58,50,44)  # 裂缝
    if kind == "heavy":
        if emblem and x in (13,18) and y == 7: return (205,210,220)   # 铆钉
        if emblem and 15 <= x <= 16 and y == 7: return (205,210,220)
        if x in (8,23) and y in (13,20): return (120,128,138)         # 板缝
        if x == 16 and y in (15,22): return (120,128,138)
    if kind == "curry":
        if emblem and (x + y) % 2 == 0: return (255,224,130)
        if (x * 4 + y * 6) % 11 == 0 and 11 <= y <= 23: return (244,196,90)  # 咖喱块
    if kind == "ender_pearl":
        if emblem and x in (14,17) and y == 7: return (140,225,245)
        if 10 <= y <= 20 and x % 4 == 1: return mix((120,205,225), 1.12)     # 珍珠高光
        if 12 <= y <= 18 and x in (13,14): return mix((140,225,245), 1.05)
    if kind == "creeper":
        # 苦力怕脸：两条眼 + 一张嘴
        if y == 13 and x in (12,19): return (40,90,32)
        if y == 14 and x in (12,19): return (40,90,32)
        if 16 <= y <= 19 and 14 <= x <= 17 and (x in (14,17) or y in (17,18)): return (40,90,32)
        if y >= 20 and x in (14,17) and y <= 24: return (40,90,32)
    if kind == "mirror":
        if emblem and x in (14,17) and y == 7: return (245,252,255)          # 镜面高光
        if (x + y) % 5 == 0 and 10 <= y <= 24: return (245,252,255)          # 镜面斜向反光
        if (x * 3 + y * 5) % 12 == 0 and 11 <= y <= 23: return mix(base, 0.62)  # 玻璃暗纹
    return None

def color_at(base, x, y, kind):
    if kind == "rainbow":
        return rainbow_shade(x, y)
    a = accent(base, x, y, kind)
    if a is not None:
        return a
    if is_waist(y):
        return waist_shade(base, x, y)
    return body_shade(base, x, y)

# 描边：与透明相邻的像素压暗
def outline(img, p, cells):
    for (x, y) in cells:
        edge = any(not in_briefs(nx, ny) for nx, ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)))
        if edge:
            r,g,b,a = p[x,y]
            p[x,y] = (int(r*0.58), int(g*0.58), int(b*0.58), 255)

def out_name(name):
    # 物品 id 与贴图文件一致：除 briefs_briefs 本身已带 _briefs 后缀外，其余为 <name>_briefs
    return name if name == "briefs_briefs" else name + "_briefs"

for name, base in themes.items():
    img = Image.new("RGBA", (W, H), (0,0,0,0))
    p = img.load()
    cells = [(x,y) for y in range(H) for x in range(W) if in_briefs(x,y)]
    for (x,y) in cells:
        c = color_at(base, x, y, name)
        p[x,y] = (c[0], c[1], c[2], 255)
    outline(img, p, cells)
    img.save(f"{OUT}/{out_name(name)}.png")
print("generated", len(themes))
