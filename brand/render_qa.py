from pathlib import Path
import io
import cairosvg
from PIL import Image, ImageDraw

root = Path(__file__).parent
canvas = Image.new("RGB", (1200, 850), "#F4F0E5")
dark = Image.new("RGB", (1200, 300), "#173F3B")
canvas.paste(dark, (0, 300))
for source, top in (("logo-horizontal.svg", 20), ("logo-horizontal-dark.svg", 320)):
    data = cairosvg.svg2png(url=str(root / source), output_width=1080, output_height=260)
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    canvas.paste(image, (60, top), image)
draw = ImageDraw.Draw(canvas)
x = 40
for size in (16, 32, 64, 128, 512):
    icon = Image.open(root / "generated" / f"icon-{size}.png")
    canvas.paste(icon, (x, 630), icon)
    draw.text((x, 630 + size + 8), f"{size}px", fill="#173F3B")
    x += size + 40
canvas.save(root / "generated" / "visual-qa.png", optimize=True)
