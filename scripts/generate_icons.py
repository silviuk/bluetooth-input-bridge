import math
import os
from PIL import Image, ImageDraw, ImageFilter

def render_master_icon(target_size=1024):
    S = 1024
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx, cy = S // 2, S // 2

    # 1. Outer Dark Rounded Background Disc
    disc_r = int(S * 0.46)
    # Ambient shadow behind disc
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.ellipse([cx - disc_r + 6, cy - disc_r + 18, cx + disc_r + 6, cy + disc_r + 18], fill=(0, 0, 0, 200))
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    img.alpha_composite(shadow)

    # Disc base with subtle rich dark gradient
    draw.ellipse([cx - disc_r, cy - disc_r, cx + disc_r, cy + disc_r], fill=(13, 19, 33, 255), outline=(56, 75, 102, 255), width=7)
    
    # Inner subtle glow ring
    inner_disc_r = int(disc_r * 0.95)
    draw.ellipse([cx - inner_disc_r, cy - inner_disc_r, cx + inner_disc_r, cy + inner_disc_r], fill=(10, 15, 26, 255))

    # 2. Orbital Connection Track at 120 degrees
    orbit_r = int(S * 0.28) # R = 286 px
    ring_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rdraw = ImageDraw.Draw(ring_layer)
    # Subtle glowing ring
    rdraw.ellipse([cx - orbit_r, cy - orbit_r, cx + orbit_r, cy + orbit_r], outline=(56, 189, 248, 160), width=8)
    rdraw.ellipse([cx - orbit_r, cy - orbit_r, cx + orbit_r, cy + orbit_r], outline=(56, 189, 248, 50), width=24)
    
    # Radial energy pulses from center to the 3 symbols
    for angle_deg in [-90, 30, 150]:
        rad = math.radians(angle_deg)
        x2 = int(cx + orbit_r * math.cos(rad))
        y2 = int(cy + orbit_r * math.sin(rad))
        rdraw.line([(cx, cy), (x2, y2)], fill=(56, 189, 248, 70), width=4)

    img.alpha_composite(ring_layer)

    # 3. Center Nexus: Glowing Bluetooth Rune
    bt_h = 110
    bt_w = 48
    bt_thick = 10
    bt_color = (0, 220, 255, 255)
    
    # BT Center Glow
    bt_glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bgdraw = ImageDraw.Draw(bt_glow)
    bgdraw.ellipse([cx - 70, cy - 70, cx + 70, cy + 70], fill=(0, 200, 255, 80))
    bt_glow = bt_glow.filter(ImageFilter.GaussianBlur(20))
    img.alpha_composite(bt_glow)

    # BT Rune Lines
    draw.line([(cx, cy - bt_h//2), (cx, cy + bt_h//2)], fill=bt_color, width=bt_thick)
    draw.line([(cx, cy - bt_h//2), (cx + bt_w, cy - bt_h//4)], fill=bt_color, width=bt_thick)
    draw.line([(cx + bt_w, cy - bt_h//4), (cx - int(bt_w*0.65), cy + int(bt_h*0.14))], fill=bt_color, width=bt_thick)
    draw.line([(cx - int(bt_w*0.65), cy - int(bt_h*0.14)), (cx + bt_w, cy + bt_h//4)], fill=bt_color, width=bt_thick)
    draw.line([(cx + bt_w, cy + bt_h//4), (cx, cy + bt_h//2)], fill=bt_color, width=bt_thick)

    # -------------------------------------------------------------
    # 4. SYMBOL 1: KEYBOARD KEY (Top, -90 degrees)
    # -------------------------------------------------------------
    k_cx = cx
    k_cy = cy - orbit_r
    kw, kh = 220, 175
    kr = 32
    
    # Key drop shadow
    k_shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ksdraw = ImageDraw.Draw(k_shadow)
    ksdraw.rounded_rectangle([k_cx - kw//2, k_cy - kh//2 + 14, k_cx + kw//2, k_cy + kh//2 + 24], radius=kr, fill=(0, 0, 0, 180))
    k_shadow = k_shadow.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(k_shadow)

    # Key Bezel / Side (3D Depth)
    draw.rounded_rectangle([k_cx - kw//2, k_cy - kh//2 + 16, k_cx + kw//2, k_cy + kh//2 + 16], radius=kr, fill=(18, 38, 68, 255), outline=(14, 165, 233, 255), width=5)
    # Key Top Face
    draw.rounded_rectangle([k_cx - kw//2, k_cy - kh//2, k_cx + kw//2, k_cy + kh//2], radius=kr, fill=(28, 52, 90, 255), outline=(56, 189, 248, 255), width=7)

    # Keyboard layout engraved inside keycap
    grid_color = (224, 242, 254, 255)
    row1_y = k_cy - 30
    for col_x in [k_cx - 58, k_cx - 20, k_cx + 20, k_cx + 58]:
        draw.rounded_rectangle([col_x - 13, row1_y - 12, col_x + 13, row1_y + 12], radius=6, fill=grid_color)
    row2_y = k_cy + 4
    for col_x in [k_cx - 58, k_cx - 20, k_cx + 20, k_cx + 58]:
        draw.rounded_rectangle([col_x - 13, row2_y - 12, col_x + 13, row2_y + 12], radius=6, fill=grid_color)
    # Spacebar
    row3_y = k_cy + 38
    draw.rounded_rectangle([k_cx - 52, row3_y - 10, k_cx + 52, row3_y + 10], radius=6, fill=grid_color)

    # -------------------------------------------------------------
    # 5. SYMBOL 2: MOUSE POINTER (Bottom-Right, +30 degrees)
    # -------------------------------------------------------------
    angle_m = math.radians(30)
    m_cx = int(cx + orbit_r * math.cos(angle_m))
    m_cy = int(cy + orbit_r * math.sin(angle_m))
    
    # Cursor polygon points scaled up 1.35x
    scale_c = 1.35
    pts = [
        (0, 0),         # Tip
        (0, int(140 * scale_c)),       # Left edge
        (int(38 * scale_c), int(105 * scale_c)),      # Notch left
        (int(72 * scale_c), int(172 * scale_c)),      # Stem right-bottom
        (int(102 * scale_c), int(158 * scale_c)),     # Stem right-top
        (int(68 * scale_c), int(92 * scale_c)),       # Notch right
        (int(120 * scale_c), int(92 * scale_c)),      # Right corner
    ]
    # Rotate pointer by -30 degrees so tip points directly towards center
    rot_angle = math.radians(-30)
    cos_r = math.cos(rot_angle)
    sin_r = math.sin(rot_angle)
    
    cursor_w = int(120 * scale_c)
    cursor_h = int(172 * scale_c)
    ox = cursor_w * 0.38
    oy = cursor_h * 0.45
    
    transformed_pts = []
    for px, py in pts:
        rx = (px - ox) * cos_r - (py - oy) * sin_r + m_cx
        ry = (px - ox) * sin_r + (py - oy) * cos_r + m_cy
        transformed_pts.append((rx, ry))

    # Mouse pointer drop shadow
    m_shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    msdraw = ImageDraw.Draw(m_shadow)
    shadow_pts = [(x + 10, y + 18) for x, y in transformed_pts]
    msdraw.polygon(shadow_pts, fill=(0, 0, 0, 180))
    m_shadow = m_shadow.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(m_shadow)

    # Draw pointer body with modern mint / emerald styling
    draw.polygon(transformed_pts, fill=(16, 185, 129, 255), outline=(5, 150, 105, 255))
    
    # Crisp glowing interior
    inner_pts = []
    for px, py in pts:
        ix = ox + (px - ox) * 0.82
        iy = oy + (py - oy) * 0.82
        rx = (ix - ox) * cos_r - (iy - oy) * sin_r + m_cx
        ry = (ix - ox) * sin_r + (iy - oy) * cos_r + m_cy
        inner_pts.append((rx, ry))
    draw.polygon(inner_pts, fill=(209, 250, 229, 255))

    # -------------------------------------------------------------
    # 6. SYMBOL 3: STYLUS PEN (Bottom-Left, +150 degrees)
    # -------------------------------------------------------------
    angle_s = math.radians(150)
    s_cx = int(cx + orbit_r * math.cos(angle_s))
    s_cy = int(cy + orbit_r * math.sin(angle_s))
    
    # Modern Digital Stylus Pen (Scaled up ~1.35x, bold and crisp)
    pen_rot = math.radians(-42)
    p_cos = math.cos(pen_rot)
    p_sin = math.sin(pen_rot)
    
    def transform_pen_point(x, y):
        rx = x * p_cos - y * p_sin + s_cx
        ry = x * p_sin + y * p_cos + s_cy
        return (rx, ry)

    pw = 20 # half-width -> 40px wide body
    
    # Pen shadow
    pen_shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    psdraw = ImageDraw.Draw(pen_shadow)
    p_box = [
        transform_pen_point(-pw - 5, -115),
        transform_pen_point(pw + 5, -115),
        transform_pen_point(pw + 5, 80),
        transform_pen_point(0, 125),
        transform_pen_point(-pw - 5, 80)
    ]
    p_box_shadow = [(x + 10, y + 18) for x, y in p_box]
    psdraw.polygon(p_box_shadow, fill=(0, 0, 0, 180))
    pen_shadow = pen_shadow.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(pen_shadow)

    # Pen Body (Purple / Lavender modern gradient)
    body_pts = [
        transform_pen_point(-pw, -95),
        transform_pen_point(pw, -95),
        transform_pen_point(pw, 70),
        transform_pen_point(-pw, 70),
    ]
    draw.polygon(body_pts, fill=(168, 85, 247, 255), outline=(147, 51, 234, 255))
    
    # Subtle body highlight line along pen length
    hl_pts = [
        transform_pen_point(-pw + 5, -90),
        transform_pen_point(-pw + 10, -90),
        transform_pen_point(-pw + 10, 65),
        transform_pen_point(-pw + 5, 65),
    ]
    draw.polygon(hl_pts, fill=(216, 180, 254, 200))

    # Eraser top cap
    cap_pts = [
        transform_pen_point(-pw, -115),
        transform_pen_point(pw, -115),
        transform_pen_point(pw, -95),
        transform_pen_point(-pw, -95),
    ]
    draw.polygon(cap_pts, fill=(233, 213, 255, 255), outline=(192, 132, 252, 255))

    # Stylus rocker button
    btn_pts = [
        transform_pen_point(pw - 7, -25),
        transform_pen_point(pw + 5, -25),
        transform_pen_point(pw + 5, 25),
        transform_pen_point(pw - 7, 25),
    ]
    draw.polygon(btn_pts, fill=(245, 208, 254, 255), outline=(192, 132, 252, 255))

    # Nose cone (tapering from body to nib)
    cone_pts = [
        transform_pen_point(-pw, 70),
        transform_pen_point(pw, 70),
        transform_pen_point(pw * 0.35, 105),
        transform_pen_point(-pw * 0.35, 105),
    ]
    draw.polygon(cone_pts, fill=(192, 132, 252, 255), outline=(216, 180, 254, 255))

    # Fine nib tip
    nib_pts = [
        transform_pen_point(-pw * 0.35, 105),
        transform_pen_point(pw * 0.35, 105),
        transform_pen_point(0, 125),
    ]
    draw.polygon(nib_pts, fill=(255, 255, 255, 255))

    if target_size != S:
        return img.resize((target_size, target_size), Image.Resampling.LANCZOS)
    return img

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
print("Done generating all unified icons with keyboard, mouse pointer and stylus at 120 deg!")
