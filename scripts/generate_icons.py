import os
from PIL import Image, ImageDraw, ImageFilter

def render_master_icon(target_size):
    # Render with 4x supersampling for ultra-crisp edges
    S = target_size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = S // 2
    
    # Minimal padding so the laptop is HUGE and the screen details pop
    pad_x = int(S * 0.04)
    pad_top = int(S * 0.04)
    
    lid_w = S - 2 * pad_x
    lid_h = int(S * 0.65)
    lid_x0 = cx - lid_w // 2
    lid_x1 = cx + lid_w // 2
    lid_y0 = pad_top
    lid_y1 = lid_y0 + lid_h
    lid_r = int(S * 0.06)
    
    base_h = int(S * 0.25)
    base_y0 = lid_y1 - int(S * 0.012)
    base_y1 = base_y0 + base_h
    base_top_w = int(lid_w * 0.98)
    base_bot_w = int(S * 0.98)
    
    # Shadow under laptop base
    shadow_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow_layer)
    sdraw.ellipse([cx - int(S*0.46), base_y1 - int(S*0.06), cx + int(S*0.46), base_y1 + int(S*0.05)], fill=(0, 0, 0, 160))
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(max(2, int(S * 0.03))))
    img.alpha_composite(shadow_layer)
    
    # 1. Laptop Lid Bezel (Modern Slate Aluminum)
    bezel_color = (36, 42, 56, 255)
    bezel_border = (85, 100, 130, 255)
    border_w = max(2, int(S * 0.012))
    draw.rounded_rectangle([lid_x0, lid_y0, lid_x1, lid_y1], radius=lid_r, fill=bezel_color, outline=bezel_border, width=border_w)
    
    # 2. Screen Display (Deep radiant tech navy)
    screen_pad_x = int(S * 0.035)
    screen_pad_top = int(S * 0.045)
    screen_pad_bot = int(S * 0.035)
    sc_x0 = lid_x0 + screen_pad_x
    sc_y0 = lid_y0 + screen_pad_top
    sc_x1 = lid_x1 - screen_pad_x
    sc_y1 = lid_y1 - screen_pad_bot
    sc_r = max(2, int(S * 0.04))
    
    draw.rounded_rectangle([sc_x0, sc_y0, sc_x1, sc_y1], radius=sc_r, fill=(10, 18, 34, 255), outline=(0, 140, 255, 200), width=max(1, int(S * 0.008)))
    
    sc_w = sc_x1 - sc_x0
    sc_h = sc_y1 - sc_y0
    sc_cx = (sc_x0 + sc_x1) // 2
    sc_cy = (sc_y0 + sc_y1) // 2
    
    # Radial glow behind screen logos
    glow_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_layer)
    gdraw.ellipse([sc_cx - int(sc_w*0.42), sc_cy - int(sc_h*0.35), sc_cx + int(sc_w*0.42), sc_cy + int(sc_h*0.35)], fill=(0, 130, 255, 75))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(max(2, int(S * 0.05))))
    img.alpha_composite(glow_layer)
    
    # 3. Screen Logos
    # Left: BLUETOOTH RUNE (Vibrant Electric Cyan #00B4FF)
    bt_cx = sc_cx - int(sc_w * 0.28)
    bt_cy = sc_cy
    bt_h = int(sc_h * 0.74)
    bt_w = int(bt_h * 0.42)
    bt_thick = max(2, int(S * 0.032)) # Bold, thick strokes for clarity
    bt_color = (0, 195, 255, 255)
    
    # BT spine
    draw.line([(bt_cx, bt_cy - bt_h//2), (bt_cx, bt_cy + bt_h//2)], fill=bt_color, width=bt_thick)
    # Upper wing
    draw.line([(bt_cx, bt_cy - bt_h//2), (bt_cx + bt_w, bt_cy - bt_h//4)], fill=bt_color, width=bt_thick)
    draw.line([(bt_cx + bt_w, bt_cy - bt_h//4), (bt_cx - int(bt_w*0.65), bt_cy + int(bt_h*0.14))], fill=bt_color, width=bt_thick)
    # Lower wing
    draw.line([(bt_cx - int(bt_w*0.65), bt_cy - int(bt_h*0.14)), (bt_cx + bt_w, bt_cy + bt_h//4)], fill=bt_color, width=bt_thick)
    draw.line([(bt_cx + bt_w, bt_cy + bt_h//4), (bt_cx, bt_cy + bt_h//2)], fill=bt_color, width=bt_thick)
    
    # Right: ANDROID ROBOT HEAD (Authentic Android Green #3DDC84)
    andro_cx = sc_cx + int(sc_w * 0.28)
    andro_cy = sc_cy + int(sc_h * 0.06)
    andro_r = int(sc_h * 0.33)
    andro_color = (61, 220, 132, 255)
    
    # Head dome
    draw.pieslice([andro_cx - andro_r, andro_cy - andro_r, andro_cx + andro_r, andro_cy + andro_r], 180, 360, fill=andro_color)
    draw.line([(andro_cx - andro_r, andro_cy), (andro_cx + andro_r, andro_cy)], fill=andro_color, width=max(1, int(S * 0.015)))
    
    # Antennae
    ant_w = max(2, int(S * 0.024))
    ant_len_x = int(andro_r * 0.35)
    ant_len_y = int(andro_r * 0.55)
    draw.line([(andro_cx - int(andro_r * 0.48), andro_cy - int(andro_r * 0.7)),
               (andro_cx - int(andro_r * 0.48) - ant_len_x, andro_cy - int(andro_r * 0.7) - ant_len_y)],
              fill=andro_color, width=ant_w)
    draw.line([(andro_cx + int(andro_r * 0.48), andro_cy - int(andro_r * 0.7)),
               (andro_cx + int(andro_r * 0.48) + ant_len_x, andro_cy - int(andro_r * 0.7) - ant_len_y)],
              fill=andro_color, width=ant_w)
    
    # Eyes
    eye_r = max(1, int(andro_r * 0.14))
    eye_dx = int(andro_r * 0.42)
    eye_dy = int(andro_r * 0.38)
    draw.ellipse([andro_cx - eye_dx - eye_r, andro_cy - eye_dy - eye_r, andro_cx - eye_dx + eye_r, andro_cy - eye_dy + eye_r], fill=(255, 255, 255, 255))
    draw.ellipse([andro_cx + eye_dx - eye_r, andro_cy - eye_dy - eye_r, andro_cx + eye_dx + eye_r, andro_cy - eye_dy + eye_r], fill=(255, 255, 255, 255))
    
    # Center: Connecting Wireless Bridge Arcs
    bridge_w = max(2, int(S * 0.022))
    r1 = int(sc_w * 0.08)
    r2 = int(sc_w * 0.15)
    draw.arc([sc_cx - r1 - int(S*0.01), sc_cy - r1, sc_cx + r1 - int(S*0.01), sc_cy + r1], 120, 240, fill=(0, 195, 255, 240), width=bridge_w)
    draw.arc([sc_cx - r1 + int(S*0.01), sc_cy - r1, sc_cx + r1 + int(S*0.01), sc_cy + r1], 300, 60, fill=(61, 220, 132, 240), width=bridge_w)
    
    draw.arc([sc_cx - r2 - int(S*0.01), sc_cy - r2, sc_cx + r2 - int(S*0.01), sc_cy + r2], 120, 240, fill=(0, 195, 255, 200), width=bridge_w)
    draw.arc([sc_cx - r2 + int(S*0.01), sc_cy - r2, sc_cx + r2 + int(S*0.01), sc_cy + r2], 300, 60, fill=(61, 220, 132, 200), width=bridge_w)
    
    dot_r = max(2, int(S * 0.018))
    draw.ellipse([sc_cx - dot_r, sc_cy - dot_r, sc_cx + dot_r, sc_cy + dot_r], fill=(255, 255, 255, 255))
    
    # 4. Laptop Base
    top_x0 = cx - base_top_w // 2
    top_x1 = cx + base_top_w // 2
    bot_x0 = cx - base_bot_w // 2
    bot_x1 = cx + base_bot_w // 2
    
    base_poly = [
        (top_x0, base_y0),
        (top_x1, base_y0),
        (bot_x1, base_y1 - int(S * 0.04)),
        (bot_x1 - int(S * 0.03), base_y1),
        (bot_x0 + int(S * 0.03), base_y1),
        (bot_x0, base_y1 - int(S * 0.04)),
    ]
    draw.polygon(base_poly, fill=(46, 54, 72, 255), outline=(90, 108, 140, 255))
    draw.line([(top_x0 + int(S*0.02), base_y0 + 1), (top_x1 - int(S*0.02), base_y0 + 1)], fill=(140, 165, 205, 220), width=max(1, int(S*0.006)))
    
    kb_w = int(base_top_w * 0.88)
    kb_h = int(base_h * 0.42)
    kb_y0 = base_y0 + int(base_h * 0.14)
    draw.rounded_rectangle([cx - kb_w//2, kb_y0, cx + kb_w//2, kb_y0 + kb_h], radius=max(2, int(S*0.015)), fill=(26, 32, 44, 255), outline=(62, 74, 95, 255), width=max(1, int(S*0.005)))
    
    tp_w = int(base_bot_w * 0.28)
    tp_h = int(base_h * 0.24)
    tp_y0 = kb_y0 + kb_h + int(base_h * 0.08)
    draw.rounded_rectangle([cx - tp_w//2, tp_y0, cx + tp_w//2, tp_y0 + tp_h], radius=max(2, int(S*0.01)), fill=(36, 44, 58, 255), outline=(75, 88, 114, 255), width=max(1, int(S*0.005)))
    
    notch_w = int(S * 0.08)
    draw.rounded_rectangle([cx - notch_w//2, base_y1 - max(2, int(S*0.01)), cx + notch_w//2, base_y1 + max(2, int(S*0.005))], radius=max(1, int(S*0.005)), fill=(20, 24, 34, 255))
    
    return img.resize((target_size, target_size), Image.Resampling.LANCZOS)

# 1. Generate Multi-DPI Windows app.ico
ico_sizes = [256, 128, 64, 48, 40, 32, 24, 20, 16]
ico_images = {s: render_master_icon(s) for s in ico_sizes}

base_256 = ico_images[256]
other_images = [ico_images[s] for s in ico_sizes if s != 256]

ico_path = "/root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/windows/res/app.ico"
base_256.save(
    ico_path,
    format="ICO",
    sizes=[(s, s) for s in ico_sizes],
    append_images=other_images
)
print(f"Successfully generated multi-size {ico_path} with sizes: {ico_sizes}")

# Verify ICO file frames
verify_im = Image.open(ico_path)
print(f"Verified {ico_path} contains {len(verify_im.ico.entry)} entries:")
for i, entry in enumerate(verify_im.ico.entry):
    print(f"  Frame {i}: {entry.dim}")

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
    out_sq = render_master_icon(size)
    sq_path = os.path.join(dir_path, "ic_launcher.png")
    out_sq.save(sq_path)
    
    # Circular mask for round icon
    circ_mask = Image.new("L", (size, size), 0)
    cdraw = ImageDraw.Draw(circ_mask)
    cdraw.ellipse([0, 0, size, size], fill=255)
    out_circ = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out_circ.paste(out_sq, (0, 0), circ_mask)
    round_path = os.path.join(dir_path, "ic_launcher_round.png")
    out_circ.save(round_path)
    print(f"Generated Android {sq_path} and {round_path} ({size}x{size})")

# Save high-res drawable fallback
render_master_icon(192).save(os.path.join(android_res, "drawable", "ic_launcher.png"))
print("Done generating all unified icons!")
