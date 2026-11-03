"""从上传的图标截图生成 Android 各密度图标资源。

源图 384x384，中间是 256x256 的深蓝圆角方块图标（边框 65,65 - 321,321）。

用法：python tools/gen_icons.py <源图路径>
"""

import os
import sys

from PIL import Image, ImageDraw

if len(sys.argv) < 2:
    raise SystemExit("用法：python tools/gen_icons.py <源图路径>")
SRC = sys.argv[1]
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   os.pardir, "app", "src", "main", "res")

NAVY = (11, 33, 86)          # 图标底色，取自原图
CROP = (65, 65, 321, 321)    # 图标本体 256x256
RADIUS = 56                  # 圆角半径（256 尺度下目测）

# 自适应图标：108dp 画布，内容占 100dp（留 4dp 余量给各 launcher 的裁切形状）
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def load_icon() -> Image.Image:
    """裁出图标本体，圆角外的白色替换为深蓝，成为完整的深蓝色方块。"""
    src = Image.open(SRC).convert("RGB").crop(CROP).convert("RGBA")
    w, h = src.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=RADIUS, fill=255)

    out = Image.new("RGBA", (w, h), NAVY + (255,))
    src_px, mask_px, out_px = src.load(), mask.load(), out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = src_px[x, y]
            brightness = 0.299 * r + 0.587 * g + 0.114 * b
            if mask_px[x, y] < 128 or brightness > 208:
                # 圆角外 / 圆角内侧残留的白边：统一压成底色
                out_px[x, y] = NAVY + (255,)
            else:
                out_px[x, y] = (r, g, b, 255)
    return out


def rounded_alpha(size: int, radius_ratio: float) -> Image.Image:
    """带透明圆角的 alpha 蒙版，用于传统图标的四个角。"""
    scale = 4
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        [0, 0, size * scale - 1, size * scale - 1],
        radius=int(size * scale * radius_ratio),
        fill=255,
    )
    return big.resize((size, size), Image.LANCZOS)


def main() -> None:
    icon = load_icon()

    # ---- 自适应图标前景：深蓝方块铺满 108dp 画布，圆形裁切后就是「深蓝圆 + 图形」
    for density, px in ADAPTIVE.items():
        canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        inner = round(px * 68 / 108)  # 图形外接圆恰好落在 66dp 安全区内
        art = icon.resize((inner, inner), Image.LANCZOS)
        offset = (px - inner) // 2
        canvas.paste(art, (offset, offset))
        d = os.path.join(RES, f"drawable-{density}")
        os.makedirs(d, exist_ok=True)
        canvas.save(os.path.join(d, "ic_launcher_foreground.png"))

    # ---- 传统图标：圆角外透明
    for density, px in LEGACY.items():
        art = icon.resize((px, px), Image.LANCZOS)
        art.putalpha(rounded_alpha(px, RADIUS / 256))
        d = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        art.save(os.path.join(d, "ic_launcher.png"))

    # ---- 开屏图标：画布 3:2，内容居中占 2/3，缩放后视觉大小与标准 splash 一致
    for density, px in {"mdpi": 288, "hdpi": 432, "xhdpi": 576, "xxhdpi": 864, "xxxhdpi": 1152}.items():
        canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        inner = round(px * 2 / 3)
        art = icon.resize((inner, inner), Image.LANCZOS)
        art.putalpha(rounded_alpha(inner, RADIUS / 256))
        canvas.paste(art, ((px - inner) // 2, (px - inner) // 2), art)
        d = os.path.join(RES, f"drawable-{density}")
        os.makedirs(d, exist_ok=True)
        canvas.save(os.path.join(d, "splash_logo.png"))

    print("生成完成：", sorted(os.listdir(os.path.join(RES, "drawable-xxxhdpi"))))


if __name__ == "__main__":
    main()
