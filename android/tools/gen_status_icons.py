"""Generate the status-bar notification icon and the Quick Settings tile icon.

Both are small (24dp) "UC" monogram glyphs, rasterised per density rather than
traced as a vector, since a hand-authored VectorDrawable path for legible
letterforms at this size is far more fragile than rendering real type.

ic_stat_ucprovpn: the persistent VPN-connected notification's small icon.
Android forces the true status-bar rendering of this to a flat alpha
silhouette on stock/AOSP-derived skins (colour is ignored there), but several
OEM skins (MIUI among them) do not enforce that and show the source colours
as-is. Shipped in the real phosphorescent-green mark so it renders correctly
wherever the platform allows it, and degrades to a silhouette everywhere else.

ic_qs_ucprovpn: the Quick Settings tile icon. Per Android's own guidance this
one must be solid white on transparent — the system always tints Quick
Settings tile icons itself (active/inactive/unavailable colours), so shipping
any other colour here would look wrong, not just get overridden.

Run from any directory: python android/tools/gen_status_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "android" / "app" / "src" / "main" / "res"
FONT = Path("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf")

BASE_DP = 24  # Android's standard small-icon / QS-tile-icon size
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

GREEN = (0x39, 0xFF, 0x14, 0xFF)
WHITE = (0xFF, 0xFF, 0xFF, 0xFF)

# Oversampled render size the glyph is drawn at, then downsized per density —
# keeps the letterforms crisp instead of rasterising straight at 24-96px.
SUPERSAMPLE = 512


def render_uc(color: tuple) -> Image.Image:
    """"UC" centred on a transparent SUPERSAMPLE x SUPERSAMPLE canvas."""
    canvas = Image.new("RGBA", (SUPERSAMPLE, SUPERSAMPLE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    # 78% of the canvas height, leaving a safety margin like the launcher mark.
    font_size = round(SUPERSAMPLE * 0.62)
    font = ImageFont.truetype(str(FONT), font_size)
    text = "UC"
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w, text_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (SUPERSAMPLE - text_w) / 2 - bbox[0]
    y = (SUPERSAMPLE - text_h) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=color)
    return canvas


def save_density_set(image: Image.Image, base_name: str) -> None:
    for density, scale in DENSITIES.items():
        size = round(BASE_DP * scale)
        resized = image.resize((size, size), Image.Resampling.LANCZOS)
        folder = RES / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        destination = folder / f"{base_name}.png"
        resized.save(destination, "PNG", optimize=True)
        print("wrote", destination)


def main() -> None:
    if not FONT.is_file():
        raise FileNotFoundError(f"font not found: {FONT}")
    save_density_set(render_uc(GREEN), "ic_stat_ucprovpn")
    save_density_set(render_uc(WHITE), "ic_qs_ucprovpn")
    print("status-bar and QS tile icons generated")


if __name__ == "__main__":
    main()
