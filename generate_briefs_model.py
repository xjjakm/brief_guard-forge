from bbmodel import Model

# Source model for the lower-body shell rendered by BriefsLayer.
m = Model("brief_guard_underwear", tex_size=64)
m.region("briefs", 0, 0, 32, 32)
m.fill("briefs", (118, 76, 52))
m.paint("briefs", lambda u, v, w, h: (
    (63, 37, 29) if v in (0, h - 1) or u in (0, w - 1)
    else (168, 112, 73) if (u * 3 + v) % 13 == 0
    else None
))

body = m.group("body", origin=(0, 12, 0))
right_leg = m.group("right_leg", origin=(-1.9, 12, 0), parent=body)
left_leg = m.group("left_leg", origin=(1.9, 12, 0), parent=body)

# A shallow waist shell sits just outside the player's body.
m.cube("waist", (-4.25, 0, -2.35), (4.25, 4.2, 2.35), tex="briefs", parent=body, inflate=0.12)
m.cube("front_panel", (-3.55, 0.8, -2.55), (3.55, 3.65, -2.28), tex="briefs", parent=body, inflate=0.04)

# Separate leg shells follow the vanilla leg pivots, so walking never desynchronizes.
m.cube("right_leg_cover", (-1.98, 0, -2.15), (0.0, 4.15, 2.15), tex="briefs", parent=right_leg, inflate=0.10)
m.cube("left_leg_cover", (0.0, 0, -2.15), (1.98, 4.15, 2.15), tex="briefs", parent=left_leg, inflate=0.10)

m.save("out/brief_guard_underwear.bbmodel")
