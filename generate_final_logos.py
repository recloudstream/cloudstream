#!/usr/bin/env python3
"""
CloudPlay Logo Generator
Generates app logos in all required Android densities
"""

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call(['pip', 'install', 'Pillow'])
    from PIL import Image, ImageDraw

import os

# Create output directory
logo_base_dir = r'logos'
os.makedirs(logo_base_dir, exist_ok=True)

# Define sizes for different Android densities
SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

# Color scheme
BLUE = (0, 102, 204)  # #0066CC - YouTube-inspired
WHITE = (255, 255, 255)
TRANSPARENT = (255, 255, 255, 0)

def draw_cp_monogram(size):
    """
    Design 2: CP Monogram - Minimalist Circle
    Blue circle with white play symbol inside
    """
    img = Image.new('RGBA', (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    
    center = size // 2
    radius = int(size * 0.46)  # Slightly smaller than full size
    
    # Draw background circle
    draw.ellipse(
        [(center - radius, center - radius), 
         (center + radius, center + radius)],
        fill=BLUE
    )
    
    # Draw play symbol (white triangle)
    play_size = size // 4.5
    play_points = [
        (center - play_size // 1.5, center - play_size),
        (center - play_size // 1.5, center + play_size),
        (center + play_size, center)
    ]
    draw.polygon(play_points, fill=WHITE)
    
    return img

def draw_cp_monogram_rounded(size):
    """
    Rounded version: Same as CP Monogram but used for ic_launcher_round
    """
    # For rounded icon, we use the same design but add alpha for rounded corners
    img = draw_cp_monogram(size)
    return img

def generate_all_sizes(draw_func, filename_prefix):
    """Generate logos for all density sizes"""
    for density, size in SIZES.items():
        logo = draw_func(size)
        
        # Create output directory
        output_dir = os.path.join(logo_base_dir, f'mipmap-{density}')
        os.makedirs(output_dir, exist_ok=True)
        
        # Save regular launcher icon
        regular_path = os.path.join(output_dir, f'{filename_prefix}.png')
        logo.save(regular_path)
        print(f'✓ Generated: {density:8} ({size:3}x{size:3}) -> {filename_prefix}.png')
        
        # Save rounded launcher icon
        rounded_path = os.path.join(output_dir, f'{filename_prefix}_round.png')
        logo.save(rounded_path)
        print(f'✓ Generated: {density:8} ({size:3}x{size:3}) -> {filename_prefix}_round.png')

# Generate logos
print("=" * 70)
print("CloudPlay Logo Generation - Design 2: CP Monogram")
print("=" * 70)
print()

print("Generating ic_launcher (regular) and ic_launcher_round (rounded):")
print("-" * 70)
generate_all_sizes(draw_cp_monogram, 'ic_launcher')

print()
print("=" * 70)
print("✓ All logos generated successfully!")
print("=" * 70)
print()

# Verify files
print("Generated files:")
for root, dirs, files in os.walk(logo_base_dir):
    for file in sorted(files):
        if file.endswith('.png'):
            filepath = os.path.join(root, file)
            size = os.path.getsize(filepath)
            print(f"  {filepath} ({size} bytes)")

print()
print("Ready to copy to app/src/main/res/ and other variants")
