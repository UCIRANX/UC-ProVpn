"""Generate every Android launcher resource from one geometry definition.

The UC ProVpn mark is a two-stroke checkmark: a short red leg and a longer
phosphorescent-green leg, meeting at one vertex. There is a single fixed icon
now (no per-user light/dark picker, no launcher-icon switching), so this
script emits exactly one adaptive icon plus the legacy density PNGs pre-API-26
launchers need.

Adaptive-icon rule enforced here: the 108dp canvas is masked down to 72dp and
only the central 66dp is safe, so the mark lives in the FOREGROUND, inside
that 66dp circle, and the BACKGROUND is a plain flat plate. Putting artwork in
the background layer is what made icons crop differently on every launcher.

Run from any directory: python android/tools/gen_launcher_icons.py
"""
from pathlib import Path
from math import hypot
from typing import NamedTuple

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android" / "app" / "src" / "main" / "res"

CANVAS_DP = 108.0      # adaptive icon canvas
MASK_DP = 72.0         # what the launcher mask keeps
SAFE_DP = 66.0         # what the launcher mask guarantees
VIEWPORT = 1024.0      # vector drawable units

# The checkmark, in viewport units on a 1024x1024 canvas centred at (512, 512):
# a short leg and a long leg sharing the vertex. Coordinates were chosen so
# every stroke endpoint, including its round cap, stays inside the 66dp safe
# circle (radius ~= 312.9 units) — see check_safe_zone().
VERTEX = (464.0, 621.0)
SHORT_LEG_START = (342.0, 498.0)   # red
LONG_LEG_END = (689.0, 362.0)      # phosphorescent green
STROKE_WIDTH = 95.0

BG_PLATE = "#FF16181C"       # flat dark charcoal, same in every context
RED = "#FFE4312A"
GREEN = "#FF39FF14"
MONOCHROME = "#FF000000"     # the system tints this itself; colour is a placeholder

LEGACY_DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Filenames from the old light/dark/system launcher-icon picker. Deleted up
# front so re-running this script on an older checkout cannot leave stale
# variants behind alongside the new single icon.
STALE_BASENAMES = ("ic_launcher_light", "ic_launcher_dark")


def rgb(color: str) -> tuple:
    """#AARRGGBB -> (r, g, b)."""
    return tuple(int(color[i:i + 2], 16) for i in (3, 5, 7))


def check_safe_zone() -> None:
    """Every stroke endpoint, plus its round cap, must fit the 66dp safe circle."""
    cx, cy = VIEWPORT / 2, VIEWPORT / 2
    safe_radius = SAFE_DP / CANVAS_DP * VIEWPORT / 2
    cap_radius = STROKE_WIDTH / 2
    for label, point in (
        ("vertex", VERTEX),
        ("short leg start", SHORT_LEG_START),
        ("long leg end", LONG_LEG_END),
    ):
        reach = hypot(point[0] - cx, point[1] - cy) + cap_radius
        if reach > safe_radius:
            raise ValueError(
                f"{label} reaches {reach:.1f}u from centre, "
                f"exceeds the {safe_radius:.1f}u safe zone"
            )
    print(f"checkmark fits the {safe_radius:.1f}u safe zone with round-cap margin")


def write(text: str, folder: str, name: str) -> None:
    destination = RES / folder / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(text, encoding="utf-8", newline="\n")
    print("wrote", destination)


def save(image: Image.Image, folder: str, name: str) -> None:
    destination = RES / folder / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, "PNG", optimize=True)
    print("wrote", destination)


def remove_stale() -> None:
    for folder in ("drawable", "mipmap-anydpi-v26", *LEGACY_DENSITIES):
        base = RES / folder
        if not base.is_dir():
            continue
        for stale in STALE_BASENAMES:
            for suffix in ("", "_round", "_background.xml", "_foreground.xml", ".xml", "_round.xml", ".png", "_round.png"):
                candidate = base / f"{stale}{suffix}"
                if candidate.is_file():
                    candidate.unlink()
                    print("removed stale", candidate)
    night = RES / "drawable-night"
    if night.is_dir():
        for child in night.glob("ic_launcher*"):
            child.unlink()
            print("removed stale", child)
        if not any(night.iterdir()):
            night.rmdir()
            print("removed stale", night)


def vector(comment: str, body: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{CANVAS_DP:.0f}dp"\n'
        f'    android:height="{CANVAS_DP:.0f}dp"\n'
        f'    android:viewportWidth="{VIEWPORT:.0f}"\n'
        f'    android:viewportHeight="{VIEWPORT:.0f}">\n'
        f"{body}"
        "</vector>\n"
    )


def stroke_path(color: str, start: tuple, end: tuple) -> str:
    return (
        "    <path\n"
        '        android:fillColor="#00000000"\n'
        f'        android:strokeColor="{color}"\n'
        f'        android:strokeWidth="{STROKE_WIDTH:.1f}"\n'
        '        android:strokeLineCap="round"\n'
        '        android:strokeLineJoin="round"\n'
        f'        android:pathData="M{start[0]:.1f},{start[1]:.1f} '
        f'L{end[0]:.1f},{end[1]:.1f}" />\n'
    )


def adaptive_icon() -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )


def background_layer() -> str:
    return vector(
        "Full-bleed flat plate. No artwork here: the launcher mask crops this layer.",
        f'    <path android:fillColor="{BG_PLATE}" android:pathData="M0,0h1024v1024h-1024z" />\n',
    )


def foreground_layer() -> str:
    return vector(
        "UC ProVpn checkmark, centred inside the 66dp safe zone of the 108dp canvas.",
        stroke_path(RED, SHORT_LEG_START, VERTEX) + stroke_path(GREEN, VERTEX, LONG_LEG_END),
    )


def monochrome_layer() -> str:
    return vector(
        "Themed-icon layer: the same checkmark as a flat silhouette, tinted by the system.",
        stroke_path(MONOCHROME, SHORT_LEG_START, VERTEX) + stroke_path(MONOCHROME, VERTEX, LONG_LEG_END),
    )


def draw_round_cap_line(draw: ImageDraw.ImageDraw, start: tuple, end: tuple, width: float, color: tuple) -> None:
    draw.line([start, end], fill=color, width=max(1, round(width)))
    radius = width / 2
    for point in (start, end):
        draw.ellipse(
            (point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius),
            fill=color,
        )


def legacy(size: int, round_mask: bool) -> Image.Image:
    scale = 4
    large = size * scale
    image = Image.new("RGBA", (large, large), rgb(BG_PLATE) + (255,))
    if round_mask:
        mask = Image.new("L", (large, large), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, large - 1, large - 1), fill=255)
        image.putalpha(mask)

    draw = ImageDraw.Draw(image)
    px = large / VIEWPORT
    stroke_px = STROKE_WIDTH * px
    draw_round_cap_line(
        draw,
        (SHORT_LEG_START[0] * px, SHORT_LEG_START[1] * px),
        (VERTEX[0] * px, VERTEX[1] * px),
        stroke_px,
        rgb(RED) + (255,),
    )
    draw_round_cap_line(
        draw,
        (VERTEX[0] * px, VERTEX[1] * px),
        (LONG_LEG_END[0] * px, LONG_LEG_END[1] * px),
        stroke_px,
        rgb(GREEN) + (255,),
    )
    return image.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    check_safe_zone()
    remove_stale()

    write(background_layer(), "drawable", "ic_launcher_background.xml")
    write(foreground_layer(), "drawable", "ic_launcher_foreground.xml")
    write(monochrome_layer(), "drawable", "ic_launcher_monochrome.xml")
    write(adaptive_icon(), "mipmap-anydpi-v26", "ic_launcher.xml")
    write(adaptive_icon(), "mipmap-anydpi-v26", "ic_launcher_round.xml")

    for folder, pixels in LEGACY_DENSITIES.items():
        save(legacy(pixels, False), folder, "ic_launcher.png")
        save(legacy(pixels, True), folder, "ic_launcher_round.png")

    print("UC ProVpn launcher icon: one fixed adaptive icon, no light/dark variants")


if __name__ == "__main__":
    main()
