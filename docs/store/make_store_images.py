from PIL import Image
import os

src = r"C:\Users\HP\.gemini\antigravity\brain\ddb59aeb-6c71-4ea6-b530-856e48c69e88"
out = r"E:\Dev\focuslock\docs\store"
os.makedirs(out, exist_ok=True)

# Icon: Play requires exactly 512x512, 32-bit PNG. Crop to a square first, then resize.
icon = Image.open(os.path.join(src, "focuslock_icon_v2_1794240689876.jpg")).convert("RGB")
w, h = icon.size
s = min(w, h)
icon = icon.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((512, 512), Image.LANCZOS)
icon.convert("RGBA").save(os.path.join(out, "icon-512.png"))

# Feature graphic: exactly 1024x500. Crop to the 1024:500 aspect, then resize.
fg = Image.open(os.path.join(src, "focuslock_feature_graphic_1794240708968.jpg")).convert("RGB")
w, h = fg.size
target = 1024 / 500
if w / h > target:
    nw = int(h * target)
    box = ((w - nw) // 2, 0, (w - nw) // 2 + nw, h)
else:
    nh = int(w / target)
    box = (0, (h - nh) // 2, w, (h - nh) // 2 + nh)
fg = fg.crop(box).resize((1024, 500), Image.LANCZOS)
fg.save(os.path.join(out, "feature-graphic-1024x500.png"))

for f in ("icon-512.png", "feature-graphic-1024x500.png"):
    im = Image.open(os.path.join(out, f))
    print(f, im.size, im.mode)
