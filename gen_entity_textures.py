from PIL import Image
import colorsys

BASE = "src/main/resources/assets/brief_guard/textures/entity/briefs/leather.png"
OUT = "src/main/resources/assets/brief_guard/textures/entity/briefs"
img = Image.open(BASE).convert("RGBA")
px = img.load()
w, h = img.size

# (hue[0..1], sat_mult, val_mult, [name])
themes = {
    "mechanical":  (0.58, 0.30, 1.00),
    "seven_curses": (0.78, 0.85, 0.95),
    "hi_teeth":     (0.11, 0.28, 1.20),
    "firework":     (0.63, 0.90, 1.00),
    "briefs_briefs": (0.60, 0.55, 1.00),
    "poor":         (0.09, 0.35, 0.95),
    "broken":       (0.06, 0.18, 0.90),
    "heavy":        (0.55, 0.18, 1.00),
    "curry":        (0.09, 0.90, 1.05),
    "ender_pearl":  (0.47, 0.70, 1.00),
    "creeper":      (0.30, 0.80, 1.00),
    "mirror":       (0.56, 0.35, 1.12),
}

def recolor(name, hue, sm, vm):
    im = img.copy()
    p = im.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = p[x, y]
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            # 用目标的色相 + 原亮度/饱和度调制，得到统一主题色的带阴影贴图。
            ns = min(1.0, s * sm + 0.05)
            nv = min(1.0, v * vm + 0.05)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, ns, nv)
            p[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    im.save(f"{OUT}/{name}.png")

def rainbow():
    im = img.copy()
    p = im.load()
    stops = [(1.0,0.0,0.0),(1.0,0.7,0.0),(1.0,1.0,0.0),(0.0,1.0,0.0),(0.0,0.6,1.0),(0.6,0.0,1.0)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = p[x, y]
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r/255.0, g/255.0, b/255.0)
            idx = int((x / w) * len(stops))
            idx = min(len(stops)-1, idx)
            cr, cg, cb = stops[idx]
            # 用原亮度调制，保留阴影
            nv = min(1.0, (v * 0.5 + 0.55) * 1.0)
            nr, ng, nb = colorsys.hsv_to_rgb(*colorsys.rgb_to_hsv(cr, cg, cb))  # keep hue/sat
            # 直接取彩虹色并乘亮度
            p[x, y] = (int(cr * 255 * nv), int(cg * 255 * nv), int(cb * 255 * nv), a)
    im.save(f"{OUT}/rainbow.png")

for name,(hue,sm,vm) in themes.items():
    recolor(name, hue, sm, vm)
rainbow()
print("done", len(themes)+1)
