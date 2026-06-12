#!/usr/bin/env python3
"""Generate captioned screenshots for the README.

Each image in ``raw/`` listed in ``CAPTIONS`` gets a title band rendered on
top (bold title + muted subtitle) and is written next to this script, where
the README references it. The band colour matches the app's dark UI so it
reads as a titled card. Editing a caption here and re-running regenerates the
images — the originals in ``raw/`` are never touched.

Usage:
    python3 -m venv .venv && .venv/bin/pip install Pillow
    .venv/bin/python generate_captions.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"

# filename (in raw/) -> (bold title, muted subtitle)
CAPTIONS = {
    "main.jpg": ("Main screen", "intent preview & launcher"),
    "package_explorer.jpg": ("Package explorer", "installed apps"),
    "package_explorer_components.jpg": ("App components", "exported & permissions"),
    "intent_editor.jpg": ("Intent editor", "typed extras & flags"),
    "content_provider_query.jpg": ("Content provider", "query a content:// URI"),
    "resource_browser_images.jpg": ("Resource browser", "images & XML"),
}

# Band styling (tuned for ~1080px-wide phone screenshots).
BG = "#080a0c"
TITLE_COLOR = "#e6edf3"
SUB_COLOR = "#9da7b3"
TITLE_SIZE = 46
SUB_SIZE = 38
PAD_Y = 26
GAP = 10
JPEG_QUALITY = 90

# Font lookup: macOS first, then common Linux paths.
TITLE_FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
SUB_FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def load_font(candidates, size):
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit(
        "No usable TTF font found. Tried:\n  " + "\n  ".join(candidates)
    )


def line_height(font):
    # ascent + descent is fixed for a given font + size, independent of which
    # glyphs a particular string uses, so every band ends up the same height.
    ascent, descent = font.getmetrics()
    return ascent + descent


def draw_centered(draw, text, font, color, width, top):
    # anchor="ma" -> horizontally centred, vertically aligned to the ascender,
    # so the text top sits exactly at `top` regardless of descenders.
    draw.text((width / 2, top), text, font=font, fill=color, anchor="ma")


def caption(src: Path, title: str, subtitle: str, dest: Path):
    img = Image.open(src).convert("RGB")
    width, height = img.size

    title_font = load_font(TITLE_FONT_CANDIDATES, TITLE_SIZE)
    sub_font = load_font(SUB_FONT_CANDIDATES, SUB_SIZE)

    title_h = line_height(title_font)
    sub_h = line_height(sub_font)
    band_h = PAD_Y + title_h + GAP + sub_h + PAD_Y

    canvas = Image.new("RGB", (width, band_h + height), BG)
    draw = ImageDraw.Draw(canvas)
    draw_centered(draw, title, title_font, TITLE_COLOR, width, PAD_Y)
    draw_centered(draw, subtitle, sub_font, SUB_COLOR, width, PAD_Y + title_h + GAP)
    canvas.paste(img, (0, band_h))
    canvas.save(dest, quality=JPEG_QUALITY)
    print(f"wrote {dest.name} ({canvas.size[0]}x{canvas.size[1]})")


def main():
    for name, (title, subtitle) in CAPTIONS.items():
        src = RAW / name
        if not src.exists():
            raise SystemExit(f"missing source: {src}")
        caption(src, title, subtitle, HERE / name)


if __name__ == "__main__":
    main()
