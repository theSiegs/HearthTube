"""Draws HearthTube's launcher icon and TV banner (the stplus flavor).

YouTube's red play button resting on Hearth's hearthstone, flames rising behind it, on Hearth's dark ember tile, so it
sits naturally next to the Hearth launcher. Everything is drawn at 4x and downsampled. Re-run after changing the design:

    py -3 tools/generate_hearthtube_icons.py

Needs Pillow (py -3 -m pip install pillow).
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "smarttubetv" / "src" / "stplus" / "res"
SS = 4  # supersampling factor

# Hearth's palette (LtvLauncher/tool/generate_icons.py) plus YouTube red.
BG_TOP = (52, 26, 18)
BG_BOTTOM = (18, 10, 8)
STONE_TOP = (226, 204, 176)
STONE_FRONT = (170, 144, 118)
STONE_JOINT = (132, 108, 88)
FLAME_OUTER = (255, 112, 26)
FLAME_MID = (255, 170, 48)
FLAME_CORE = (255, 236, 170)
YT_RED = (255, 0, 0)
YT_RED_SHADE = (200, 0, 0)
TEXT = (246, 228, 200)


def vertical_gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def teardrop(cx, cy, w, h, lean=0.0, steps=240):
    """Flame outline: round at the bottom, pointed at the top, the tip pushed sideways by `lean`."""
    points = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        x = math.sin(t) * math.sin(t / 2) ** 1.6
        y = -math.cos(t)
        top = max(0.0, -y)
        points.append((cx + w * x + lean * w * top ** 2, cy + h * y))
    return points


def emblem(size):
    """Play button on the hearthstone with flames behind, on a transparent square canvas of `size` px."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # Firelight.
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([s * 0.14, s * 0.10, s * 0.86, s * 0.74], fill=FLAME_OUTER + (130,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(s * 0.08)))

    draw = ImageDraw.Draw(img)
    # Flames rising behind the button: a tall centre tongue and two side tongues.
    for cx, cy, w, h, lean in ((0.31, 0.40, 0.12, 0.22, -0.35), (0.69, 0.40, 0.12, 0.22, 0.35),
                               (0.50, 0.36, 0.17, 0.30, 0.15)):
        draw.polygon(teardrop(s * cx, s * cy, s * w, s * h, lean), fill=FLAME_OUTER + (255,))
    for cx, cy, w, h, lean in ((0.32, 0.44, 0.07, 0.15, -0.3), (0.68, 0.44, 0.07, 0.15, 0.3),
                               (0.50, 0.40, 0.10, 0.22, -0.15)):
        draw.polygon(teardrop(s * cx, s * cy, s * w, s * h, lean), fill=FLAME_MID + (255,))

    # Hearthstone: lit top face and a darker front split into three blocks.
    draw.polygon([(s * 0.14, s * 0.70), (s * 0.86, s * 0.70), (s * 0.94, s * 0.76), (s * 0.06, s * 0.76)],
                 fill=STONE_TOP + (255,))
    draw.rounded_rectangle([s * 0.06, s * 0.76, s * 0.94, s * 0.88], radius=s * 0.02, fill=STONE_FRONT + (255,))
    for x in (0.35, 0.65):
        draw.line([(s * x, s * 0.765), (s * x, s * 0.875)], fill=STONE_JOINT + (255,), width=round(s * 0.012))

    # YouTube play button resting on the stone, with a thin shaded base so it reads as sitting on it.
    draw.rounded_rectangle([s * 0.18, s * 0.36, s * 0.82, s * 0.73], radius=s * 0.10, fill=YT_RED_SHADE + (255,))
    draw.rounded_rectangle([s * 0.18, s * 0.34, s * 0.82, s * 0.71], radius=s * 0.10, fill=YT_RED + (255,))
    # Play triangle glowing like an ember: cream with a warm core.
    tri = [(s * 0.435, s * 0.435), (s * 0.615, s * 0.525), (s * 0.435, s * 0.615)]
    draw.polygon(tri, fill=(255, 255, 255, 255))
    cx, cy = (s * 0.488, s * 0.525)
    draw.polygon([(cx + (x - cx) * 0.55, cy + (y - cy) * 0.55) for x, y in tri], fill=FLAME_CORE + (255,))

    return img.resize((size, size), Image.LANCZOS)


def icon(px):
    """Square launcher icon: emblem on a rounded ember tile."""
    s = px * SS
    tile = vertical_gradient((s, s), BG_TOP, BG_BOTTOM).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.22, fill=255)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(tile, (0, 0), mask)
    out = out.resize((px, px), Image.LANCZOS)
    mark = emblem(round(px * 0.84))
    off = (px - mark.width) // 2
    out.alpha_composite(mark, (off, off))
    return out


def banner(w, h):
    """TV banner (320x180 dp): the emblem with the name under the stone, like Hearth's banner."""
    s_w, s_h = w * SS, h * SS
    img = vertical_gradient((s_w, s_h), BG_TOP, BG_BOTTOM).convert("RGBA")
    glow = Image.new("RGBA", (s_w, s_h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([s_w * 0.30, -s_h * 0.10, s_w * 0.70, s_h * 0.80], fill=FLAME_OUTER + (60,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(s_h * 0.12)))
    img = img.resize((w, h), Image.LANCZOS)

    # Trim the emblem to its solid shapes (not the glow) so the stone sits right above the name.
    mark = emblem(h * 2)
    mark = mark.crop(mark.getchannel("A").point(lambda a: 255 if a > 200 else 0).getbbox())
    mark_h = round(h * 0.60)
    mark = mark.resize((round(mark.width * mark_h / mark.height), mark_h), Image.LANCZOS)
    top = round(h * 0.07)
    img.alpha_composite(mark, ((w - mark.width) // 2, top))

    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype("C:/Windows/Fonts/georgiab.ttf", round(h * 0.17))
    text = "HearthTube"
    l, t, r, b = draw.textbbox((0, 0), text, font=font)
    draw.text(((w - (r - l)) // 2 - l, top + mark_h + round(h * 0.05) - t), text, font=font, fill=TEXT)
    return img.convert("RGB")


def main():
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, scale in densities.items():
        out = RES / f"drawable-{name}"
        out.mkdir(exist_ok=True)
        icon(round(96 * scale)).save(out / "hearthtube_icon.png")
    banner(640, 360).save(RES / "drawable-xhdpi" / "hearthtube_banner.png")  # xhdpi, the density Android TV uses
    print("icons written")


if __name__ == "__main__":
    main()
