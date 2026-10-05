import os
import math
from PIL import Image, ImageDraw, ImageFilter

SIZE = 2048
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
cx = SIZE // 2
cy = SIZE // 2

# Background Squircle Badge
pad = 80
corner_radius = 420
bg_draw = ImageDraw.Draw(img)

# Deep tech squircle background with rich gradient
for step in range(pad, pad + 160):
    ratio = (step - pad) / 160.0
    r = int(14 + ratio * 8)
    g = int(17 + ratio * 10)
    b = int(26 + ratio * 16)
    cr = int(corner_radius * (1 - ratio * 0.15))
    bg_draw.rounded_rectangle([step, step, SIZE - step, SIZE - step], radius=cr, fill=(r, g, b, 255))

# Outer subtle border on squircle
bg_draw.rounded_rectangle([pad, pad, SIZE - pad, SIZE - pad], radius=corner_radius, outline=(65, 80, 115, 200), width=16)

# ----------------- LAPTOP DRAWING -----------------
laptop_w = 1560
lid_h = 940
base_h = 320
lid_top = 280
lid_bottom = lid_top + lid_h

lid_x0 = cx - 720
lid_x1 = cx + 720

base_top = lid_bottom - 15
base_bottom = base_top + base_h
base_top_w = 1460
base_bot_w = 1680

# Shadow beneath laptop
shadow_layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
sdraw = ImageDraw.Draw(shadow_layer)
sdraw.ellipse([cx - 820, base_bottom - 70, cx + 820, base_bottom + 120], fill=(0, 0, 0, 180))
shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(40))
img.alpha_composite(shadow_layer)

draw = ImageDraw.Draw(img)

# --- 1. Laptop Lid Outer Shell (Sleek Slate Aluminum) ---
lid_corner = 54
draw.rounded_rectangle([lid_x0, lid_top, lid_x1, lid_bottom], radius=lid_corner, fill=(40, 46, 60, 255), outline=(95, 110, 140, 255), width=14)

# Notch / Webcam at top
draw.ellipse([cx - 12, lid_top + 20, cx + 12, lid_top + 44], fill=(15, 18, 26, 255), outline=(70, 85, 110, 255), width=4)
draw.ellipse([cx - 4, lid_top + 28, cx + 4, lid_top + 36], fill=(40, 140, 255, 200))

# --- 2. Screen Display (Vibrant Deep Blue Tech Glass) ---
sc_pad_x = 48
sc_pad_top = 60
sc_pad_bot = 48
sc_x0 = lid_x0 + sc_pad_x
sc_y0 = lid_top + sc_pad_top
sc_x1 = lid_x1 - sc_pad_x
sc_y1 = lid_bottom - sc_pad_bot
sc_radius = 28

screen_img = Image.new("RGBA", (sc_x1 - sc_x0, sc_y1 - sc_y0), (0, 0, 0, 0))
sc_draw = ImageDraw.Draw(screen_img)
sw = sc_x1 - sc_x0
sh = sc_y1 - sc_y0

for y in range(sh):
    t = y / float(sh)
    sr = int(10 + t * 14)
    sg = int(16 + t * 24)
    sb = int(32 + t * 45)
    sc_draw.line([(0, y), (sw, y)], fill=(sr, sg, sb, 255))

glow_img = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
gdraw = ImageDraw.Draw(glow_img)
gdraw.ellipse([sw//2 - 450, sh//2 - 320, sw//2 + 450, sh//2 + 320], fill=(25, 75, 140, 130))
glow_img = glow_img.filter(ImageFilter.GaussianBlur(60))
screen_img.alpha_composite(glow_img)

mask_img = Image.new("L", (sw, sh), 0)
mdraw = ImageDraw.Draw(mask_img)
mdraw.rounded_rectangle([0, 0, sw, sh], radius=sc_radius, fill=255)
screen_composite = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
screen_composite.paste(screen_img, (0, 0), mask_img)
img.paste(screen_composite, (sc_x0, sc_y0), screen_composite)

draw.rounded_rectangle([sc_x0, sc_y0, sc_x1, sc_y1], radius=sc_radius, outline=(50, 75, 115, 255), width=8)

sheen_layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
sh_draw = ImageDraw.Draw(sheen_layer)
sh_poly = [
    (sc_x0 + 400, sc_y0 + 10),
    (sc_x1 - 40, sc_y0 + 10),
    (sc_x1 - 40, sc_y0 + 380),
    (sc_x0 + 180, sc_y1 - 80),
]
sh_draw.polygon(sh_poly, fill=(255, 255, 255, 25))
sheen_layer = sheen_layer.filter(ImageFilter.GaussianBlur(15))
img.alpha_composite(sheen_layer)

# --- 3. Screen Logos: Bluetooth & Android & Bridge Link ---
sc_cx = (sc_x0 + sc_x1) // 2
sc_cy = (sc_y0 + sc_y1) // 2

# Left: BLUETOOTH RUNE
bt_cx = sc_cx - 310
bt_cy = sc_cy
bt_h = 460
bt_half = bt_h // 2
bt_w = 185
bt_color = (0, 175, 255, 255)
bt_thick = 44

bt_glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
bglow_draw = ImageDraw.Draw(bt_glow)
bglow_draw.line([(bt_cx, bt_cy - bt_half), (bt_cx, bt_cy + bt_half)], fill=(0, 160, 255, 180), width=bt_thick + 20)
bglow_draw.line([(bt_cx, bt_cy - bt_half), (bt_cx + bt_w, bt_cy - bt_half // 2)], fill=(0, 160, 255, 180), width=bt_thick + 20)
bglow_draw.line([(bt_cx + bt_w, bt_cy - bt_half // 2), (bt_cx - bt_w + 55, bt_cy + 65)], fill=(0, 160, 255, 180), width=bt_thick + 20)
bglow_draw.line([(bt_cx - bt_w + 55, bt_cy - 65), (bt_cx + bt_w, bt_cy + bt_half // 2)], fill=(0, 160, 255, 180), width=bt_thick + 20)
bglow_draw.line([(bt_cx + bt_w, bt_cy + bt_half // 2), (bt_cx, bt_cy + bt_half)], fill=(0, 160, 255, 180), width=bt_thick + 20)
bt_glow = bt_glow.filter(ImageFilter.GaussianBlur(25))
img.alpha_composite(bt_glow)

draw.line([(bt_cx, bt_cy - bt_half), (bt_cx, bt_cy + bt_half)], fill=bt_color, width=bt_thick)
draw.line([(bt_cx, bt_cy - bt_half), (bt_cx + bt_w, bt_cy - bt_half // 2)], fill=bt_color, width=bt_thick)
draw.line([(bt_cx + bt_w, bt_cy - bt_half // 2), (bt_cx - bt_w + 55, bt_cy + 65)], fill=bt_color, width=bt_thick)
draw.line([(bt_cx - bt_w + 55, bt_cy - 65), (bt_cx + bt_w, bt_cy + bt_half // 2)], fill=bt_color, width=bt_thick)
draw.line([(bt_cx + bt_w, bt_cy + bt_half // 2), (bt_cx, bt_cy + bt_half)], fill=bt_color, width=bt_thick)

for pt in [
    (bt_cx, bt_cy - bt_half),
    (bt_cx, bt_cy + bt_half),
    (bt_cx + bt_w, bt_cy - bt_half // 2),
    (bt_cx + bt_w, bt_cy + bt_half // 2),
    (bt_cx - bt_w + 55, bt_cy + 65),
    (bt_cx - bt_w + 55, bt_cy - 65)
]:
    draw.ellipse([pt[0] - bt_thick//2, pt[1] - bt_thick//2, pt[0] + bt_thick//2, pt[1] + bt_thick//2], fill=bt_color)

# Right: ANDROID ROBOT HEAD
andro_cx = sc_cx + 310
andro_cy = sc_cy + 24
andro_r = 195
andro_color = (61, 220, 132, 255)

aglow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
aglow_draw = ImageDraw.Draw(aglow)
aglow_draw.pieslice([andro_cx - andro_r - 15, andro_cy - andro_r - 15, andro_cx + andro_r + 15, andro_cy + andro_r + 15], 180, 360, fill=(61, 220, 132, 160))
aglow = aglow.filter(ImageFilter.GaussianBlur(30))
img.alpha_composite(aglow)

draw.pieslice([andro_cx - andro_r, andro_cy - andro_r, andro_cx + andro_r, andro_cy + andro_r], 180, 360, fill=andro_color)
draw.line([(andro_cx - andro_r, andro_cy), (andro_cx + andro_r, andro_cy)], fill=andro_color, width=12)

ant_w = 30
draw.line([(andro_cx - 95, andro_cy - 135), (andro_cx - 145, andro_cy - 215)], fill=andro_color, width=ant_w)
draw.ellipse([andro_cx - 145 - ant_w//2, andro_cy - 215 - ant_w//2, andro_cx - 145 + ant_w//2, andro_cy - 215 + ant_w//2], fill=andro_color)

draw.line([(andro_cx + 95, andro_cy - 135), (andro_cx + 145, andro_cy - 215)], fill=andro_color, width=ant_w)
draw.ellipse([andro_cx + 145 - ant_w//2, andro_cy - 215 - ant_w//2, andro_cx + 145 + ant_w//2, andro_cy - 215 + ant_w//2], fill=andro_color)

eye_r = 25
eye_dx = 80
eye_dy = 70
draw.ellipse([andro_cx - eye_dx - eye_r, andro_cy - eye_dy - eye_r, andro_cx - eye_dx + eye_r, andro_cy - eye_dy + eye_r], fill=(255, 255, 255, 255))
draw.ellipse([andro_cx + eye_dx - eye_r, andro_cy - eye_dy - eye_r, andro_cx + eye_dx + eye_r, andro_cy - eye_dy + eye_r], fill=(255, 255, 255, 255))

# Center: DYNAMIC WIRELESS BRIDGE PULSES
b_cx = sc_cx
b_cy = sc_cy

for rad, alpha, w in [(70, 240, 22), (130, 200, 20), (190, 150, 16)]:
    draw.arc([b_cx - rad - 25, b_cy - rad, b_cx + rad - 25, b_cy + rad], 120, 240, fill=(0, 175, 255, alpha), width=w)
    draw.arc([b_cx - rad + 25, b_cy - rad, b_cx + rad + 25, b_cy + rad], 300, 60, fill=(61, 220, 132, alpha), width=w)

draw.ellipse([b_cx - 18, b_cy - 18, b_cx + 18, b_cy + 18], fill=(255, 255, 255, 255))

# --- 4. Laptop Base / Keyboard Deck ---
top_x0 = cx - base_top_w // 2
top_x1 = cx + base_top_w // 2
bot_x0 = cx - base_bot_w // 2
bot_x1 = cx + base_bot_w // 2

base_poly = [
    (top_x0, base_top),
    (top_x1, base_top),
    (bot_x1, base_bottom - 50),
    (bot_x1 - 35, base_bottom),
    (bot_x0 + 35, base_bottom),
    (bot_x0, base_bottom - 50),
]
draw.polygon(base_poly, fill=(50, 58, 76, 255), outline=(100, 118, 150, 255))
draw.line([(top_x0 + 20, base_top + 2), (top_x1 - 20, base_top + 2)], fill=(150, 175, 215, 220), width=6)

kb_t = base_top + 45
kb_b = base_top + 175
kb_w = 1260
draw.rounded_rectangle([cx - kb_w//2, kb_t, cx + kb_w//2, kb_b], radius=20, fill=(30, 36, 48, 255), outline=(70, 82, 105, 255), width=6)

for ky in range(kb_t + 28, kb_b - 12, 34):
    draw.line([(cx - kb_w//2 + 35, ky), (cx + kb_w//2 - 35, ky)], fill=(55, 65, 86, 200), width=8)

tp_t = kb_b + 28
tp_b = base_bottom - 22
tp_w = 400
draw.rounded_rectangle([cx - tp_w//2, tp_t, cx + tp_w//2, tp_b], radius=18, fill=(40, 48, 64, 255), outline=(85, 100, 130, 255), width=6)
draw.rounded_rectangle([cx - 90, base_bottom - 12, cx + 90, base_bottom + 10], radius=8, fill=(25, 30, 42, 255))

# Save 1024 master
master_1024 = img.resize((1024, 1024), Image.Resampling.LANCZOS)
master_1024.save("/tmp/master_icon.png")

# 1. Generate Windows ICO (Multi-DPI)
ico_sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
ico_images = [img.resize((s, s), Image.Resampling.LANCZOS) for s in ico_sizes]
ico_path = "/root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/windows/res/app.ico"
ico_images[0].save(ico_path, format="ICO", sizes=[(s, s) for s in ico_sizes], append_images=ico_images[1:])
print(f"Generated {ico_path} with sizes: {ico_sizes}")

# 2. Generate Android Mipmaps
android_res = "/root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/android/app/src/main/res"
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in densities.items():
    dir_path = os.path.join(android_res, folder)
    os.makedirs(dir_path, exist_ok=True)
    # Square/squircle icon
    out_sq = img.resize((size, size), Image.Resampling.LANCZOS)
    sq_path = os.path.join(dir_path, "ic_launcher.png")
    out_sq.save(sq_path)
    
    # Circular mask icon
    circ_mask = Image.new("L", (size, size), 0)
    cdraw = ImageDraw.Draw(circ_mask)
    cdraw.ellipse([1, 1, size - 1, size - 1], fill=255)
    out_circ = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out_circ.paste(out_sq, (0, 0), circ_mask)
    round_path = os.path.join(dir_path, "ic_launcher_round.png")
    out_circ.save(round_path)
    print(f"Generated {sq_path} and {round_path} ({size}x{size})")

# Also save high-res drawable fallback
os.makedirs(os.path.join(android_res, "drawable"), exist_ok=True)
img.resize((192, 192), Image.Resampling.LANCZOS).save(os.path.join(android_res, "drawable", "ic_launcher.png"))
print("Done generating all unified icons!")
