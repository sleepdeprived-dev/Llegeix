"""Builds the adaptive-icon layers from the source artwork.

Run with `python3 tools/icon/build.py`; it needs Pillow, NumPy and SciPy, and it
writes the two raster layers into every mipmap density. It is kept in the tree
with the artwork beside it because a release once went out without a new icon
for want of the file it was to be cut from.

The artwork is one flat picture: a rounded square of Senyera stripes with an
open book and a rose standing on it. An adaptive icon needs those two things
apart — a background that runs past every edge of the 108dp canvas, and a
foreground that stands in the middle of it — so they are separated here rather
than by hand.

The separation works because the stripes are horizontal and even: every row of
the artwork is one colour from edge to edge except where the book covers it, so
the row's colour can be read from the margin beside the book and painted across
the whole row. Anything that differs from its own row's colour by more than a
little is the book.
"""

from pathlib import Path

from PIL import Image, ImageFilter
import numpy as np
from scipy import ndimage

HERE = Path(__file__).resolve().parent
SRC = HERE / "artwork.png"
OUT = HERE.parent.parent / "app/src/main/res"

# The 108dp canvas, drawn at 12px per dp and reduced from there.
DP = 12
CANVAS = 108 * DP
# How wide the book stands on that canvas. The middle 72dp is all a launcher
# promises to show and a circle mask cuts to less; 54dp leaves the book whole
# through any of them.
BOOK_DP = 54
# The rounded square's own bevel, as a fraction of the artwork. Dropped from
# the background so the top and bottom of the icon are stripe rather than the
# dark edge the artwork was drawn with.
BEVEL = 0.025
# How far the artwork's painted shadow reaches past the book, in source pixels.
SHADOW_PX = 16

DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def artwork_mask(rgb):
    """The rounded square, told apart from the checkerboard painted round it."""
    sat = rgb.max(2) - rgb.min(2)
    greyish = sat < 22
    lab, _ = ndimage.label(greyish)
    outside = np.zeros(greyish.shape, bool)
    for corner in ((0, 0), (0, -1), (-1, 0), (-1, -1)):
        outside |= lab == lab[corner]
    art = ndimage.binary_fill_holes(~outside)
    return ndimage.binary_erosion(art, np.ones((5, 5)))


def stripe_colours(rgb, art):
    """Each row's stripe colour, read from the margin the book never reaches."""
    height = rgb.shape[0]
    rows = np.where(art.any(1))[0]
    widest = np.ptp(np.where(art.any(0))[0]) + 1
    colours = np.zeros((height, 3))
    for y in range(rows.min(), rows.max() + 1):
        cols = np.where(art[y])[0]
        if cols.size == 0:
            continue
        lo, hi = cols.min(), cols.max()
        span = hi - lo + 1
        if span < 0.60 * widest:
            # A row through the rounded corner: too short for the book to be
            # on it at all, so the whole of it is stripe.
            inset = int(0.10 * span)
            sample = rgb[y, lo + inset:hi - inset + 1]
        else:
            edge, depth = int(0.045 * span), int(0.075 * span)
            sample = np.concatenate(
                [rgb[y, lo + edge:lo + edge + depth], rgb[y, hi - edge - depth:hi - edge]],
                axis=0,
            )
        colours[y] = np.median(sample, axis=0)
    return colours


def book_mask(rgb, art, colours):
    """The book and its rose: what does not match the stripe behind it."""
    # Measured inside the bevel. The sheen along the rounded edge is itself a
    # departure from the row's colour, and without this it joins the book.
    inner = ndimage.binary_erosion(art, ndimage.generate_binary_structure(2, 2), iterations=55)
    apart = np.abs(rgb - colours[:, None, :]).sum(2) > 55
    mask = ndimage.binary_fill_holes(ndimage.binary_closing(inner & apart, np.ones((9, 9))))
    lab, count = ndimage.label(mask)
    sizes = ndimage.sum(mask, lab, range(1, count + 1))
    book = lab == (np.argmax(sizes) + 1)
    # Pulled in to the book's own outline. What it is trimming is the soft
    # shadow the artwork painted round the book, which differs from the stripe
    # as much as the book does and so comes away with it — carrying a sliver of
    # stripe along the edge, which on a cut-out layer reads as a yellow burr.
    return ndimage.binary_erosion(book, np.ones((3, 3)), iterations=SHADOW_PX)


def main():
    rgb = np.asarray(Image.open(SRC).convert("RGB")).astype(float)
    art = artwork_mask(rgb)
    colours = stripe_colours(rgb, art)
    book = book_mask(rgb, art, colours)

    rows = np.where(art.any(1))[0]
    top, bottom = rows.min(), rows.max()
    bevel = int((bottom - top + 1) * BEVEL)

    # --- background: the stripes, full bleed ------------------------------
    band = colours[top + bevel:bottom - bevel + 1]
    background = Image.fromarray(
        np.clip(np.repeat(band[:, None, :], 8, axis=1), 0, 255).astype("uint8")
    ).resize((CANVAS, CANVAS), Image.LANCZOS)

    # --- foreground: the book, with a shadow of its own -------------------
    ys, xs = np.where(book)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    cut = Image.fromarray(np.clip(rgb, 0, 255).astype("uint8")).crop(box)
    # Feathered by a pixel of the source rather than cut clean, so the edge
    # survives being reduced to 108px without a staircase on it.
    alpha = Image.fromarray((book * 255).astype("uint8")).crop(box).filter(
        ImageFilter.GaussianBlur(1.5)
    )
    cut.putalpha(alpha)

    width = BOOK_DP * DP
    height = round(width * cut.height / cut.width)
    cut = cut.resize((width, height), Image.LANCZOS)

    foreground = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    left, topped = (CANVAS - width) // 2, (CANVAS - height) // 2
    # The artwork's own shadow fell on stripes that are not in this layer any
    # more, so the book is given one that travels with it.
    shadow = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 90), (left, topped + 2 * DP), cut.split()[3])
    foreground = Image.alpha_composite(foreground, shadow.filter(ImageFilter.GaussianBlur(1.2 * DP)))
    foreground.alpha_composite(cut, (left, topped))

    for folder, size in DENSITIES.items():
        background.resize((size, size), Image.LANCZOS).convert("RGB").save(
            OUT / f"mipmap-{folder}/ic_launcher_background.png"
        )
        foreground.resize((size, size), Image.LANCZOS).save(
            OUT / f"mipmap-{folder}/ic_launcher_foreground.png"
        )


if __name__ == "__main__":
    main()
