from PIL import Image, ImageDraw, ImageFont
import os

# Create output directory
logo_dir = r'logos'
os.makedirs(logo_dir, exist_ok=True)

# Define icon sizes for different densities
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

def create_logo_v1_cloud_play(size, bg_color=(255, 255, 255)):
    """Minimal cloud with play button - inspired by YouTube & modern apps"""
    img = Image.new('RGBA', (size, size), (*bg_color, 0))
    draw = ImageDraw.Draw(img)
    
    # Draw a minimal cloud shape
    margin = size // 6
    cloud_y = size // 3
    
    # Cloud circles (minimalist approach)
    radius1 = size // 5
    radius2 = size // 6
    
    x_center = size // 2
    y_center = cloud_y
    
    # Left bump
    draw.ellipse([x_center - radius1*1.5, y_center - radius1//2, 
                  x_center - radius1*0.5, y_center + radius1*1.5], 
                 fill=(0, 102, 204, 255))
    # Center bump
    draw.ellipse([x_center - radius1*0.5, y_center - radius1, 
                  x_center + radius1*0.5, y_center + radius1], 
                 fill=(0, 102, 204, 255))
    # Right bump
    draw.ellipse([x_center + radius1*0.5, y_center - radius1//2, 
                  x_center + radius1*1.5, y_center + radius1*1.5], 
                 fill=(0, 102, 204, 255))
    
    # Play button triangle below cloud
    play_y = size * 2 // 3
    play_size = size // 5
    pts = [
        (x_center - play_size, play_y - play_size),
        (x_center - play_size, play_y + play_size),
        (x_center + play_size, play_y)
    ]
    draw.polygon(pts, fill=(0, 102, 204, 255))
    
    return img

def create_logo_v2_cp_monogram(size, bg_color=(255, 255, 255)):
    """CP monogram in modern geometric style"""
    img = Image.new('RGBA', (size, size), (*bg_color, 0))
    draw = ImageDraw.Draw(img)
    
    # Main circle background
    padding = size // 10
    draw.ellipse([padding, padding, size - padding, size - padding], 
                 fill=(0, 102, 204, 255))
    
    # Draw play symbol in white
    p_center_x = size // 2
    p_center_y = size // 2
    play_pts = [
        (p_center_x - size//6, p_center_y - size//6),
        (p_center_x - size//6, p_center_y + size//6),
        (p_center_x + size//5, p_center_y)
    ]
    draw.polygon(play_pts, fill=(255, 255, 255, 255))
    
    return img

def create_logo_v3_streaming_cloud(size, bg_color=(255, 255, 255)):
    """Cloud with streaming/flowing lines"""
    img = Image.new('RGBA', (size, size), (*bg_color, 0))
    draw = ImageDraw.Draw(img)
    
    # Cloud shape
    cloud_radius = size // 4
    x_center = size // 2
    y_center = size // 3
    
    # Left circle
    draw.ellipse([x_center - cloud_radius*1.5, y_center - cloud_radius*0.5,
                  x_center - cloud_radius*0.5, y_center + cloud_radius*1.5],
                 fill=(0, 102, 204, 255))
    # Center circle  
    draw.ellipse([x_center - cloud_radius*0.5, y_center - cloud_radius,
                  x_center + cloud_radius*0.5, y_center + cloud_radius],
                 fill=(0, 102, 204, 255))
    # Right circle
    draw.ellipse([x_center + cloud_radius*0.5, y_center - cloud_radius*0.5,
                  x_center + cloud_radius*1.5, y_center + cloud_radius*1.5],
                 fill=(0, 102, 204, 255))
    
    # Streaming lines below
    line_y_start = size * 2 // 3
    line_spacing = size // 8
    
    for i in range(3):
        y_offset = i * size // 12
        # Curved streaming lines
        draw.line([(x_center - line_spacing, line_y_start + y_offset),
                   (x_center + line_spacing, line_y_start - size//12 + y_offset)],
                  fill=(0, 102, 204, 255), width=max(1, size // 30))
    
    return img

def create_logo_v4_play_cloud(size, bg_color=(255, 255, 255)):
    """Minimalist: Cloud outline with play symbol integrated"""
    img = Image.new('RGBA', (size, size), (*bg_color, 0))
    draw = ImageDraw.Draw(img)
    
    thickness = max(2, size // 20)
    
    # Cloud outline (top half)
    cloud_radius = size // 5
    x_center = size // 2
    y_center = size // 2 - size // 8
    
    # Draw cloud outline
    arc_coords = [x_center - cloud_radius*1.5, y_center - cloud_radius,
                  x_center - cloud_radius*0.5, y_center + cloud_radius]
    draw.arc(arc_coords, 0, 180, fill=(0, 102, 204, 255), width=thickness)
    
    arc_coords = [x_center - cloud_radius*0.5, y_center - cloud_radius*1.2,
                  x_center + cloud_radius*0.5, y_center + cloud_radius*0.8]
    draw.arc(arc_coords, 0, 180, fill=(0, 102, 204, 255), width=thickness)
    
    arc_coords = [x_center + cloud_radius*0.5, y_center - cloud_radius,
                  x_center + cloud_radius*1.5, y_center + cloud_radius]
    draw.arc(arc_coords, 0, 180, fill=(0, 102, 204, 255), width=thickness)
    
    # Play button (large, centered)
    play_size = size // 5
    play_y = size * 2 // 3
    pts = [
        (x_center - play_size, play_y - play_size),
        (x_center - play_size, play_y + play_size),
        (x_center + play_size, play_y)
    ]
    draw.polygon(pts, fill=(0, 102, 204, 255))
    
    return img

def create_preview_grid(versions):
    """Create a preview grid of all versions"""
    preview_size = 256
    gap = 20
    cols = 2
    rows = (len(versions) + cols - 1) // cols
    
    total_width = cols * preview_size + (cols + 1) * gap
    total_height = rows * preview_size + (rows + 1) * gap
    
    preview = Image.new('RGB', (total_width, total_height), (250, 250, 250))
    
    for idx, (name, img) in enumerate(versions):
        row = idx // cols
        col = idx % cols
        x = gap + col * (preview_size + gap)
        y = gap + row * (preview_size + gap)
        
        # Resize to preview size
        resized = img.resize((preview_size, preview_size), Image.Resampling.LANCZOS)
        preview.paste(resized, (x, y), resized if resized.mode == 'RGBA' else None)
    
    return preview

# Generate logos for largest size first
max_size = sizes['xxxhdpi']

versions = [
    ('Cloud with Play Button', create_logo_v1_cloud_play(max_size)),
    ('CP Monogram', create_logo_v2_cp_monogram(max_size)),
    ('Streaming Cloud', create_logo_v3_streaming_cloud(max_size)),
    ('Cloud Play Outline', create_logo_v4_play_cloud(max_size)),
]

# Save preview
preview = create_preview_grid(versions)
preview.save(os.path.join(logo_dir, 'logo_preview.png'))
print('Preview saved to:', os.path.join(logo_dir, 'logo_preview.png'))

# Save all versions at max size
for name, img in versions:
    filename = name.lower().replace(' ', '_').replace('-', '_')
    filepath = os.path.join(logo_dir, f'{filename}.png')
    img.save(filepath)
    print(f'Saved: {filepath}')

print('\nLogo generation complete!')
