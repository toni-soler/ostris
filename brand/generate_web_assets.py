from pathlib import Path
import io
import cairosvg
from PIL import Image

ROOT = Path(__file__).parent
OUT = ROOT / "generated"
OUT.mkdir(exist_ok=True)
micro_source = ROOT / "favicon.svg"
full_source = ROOT / "logo-mark.svg"
for size in (16, 32, 48, 64, 128, 180, 192, 512, 1024):
    source = micro_source if size <= 32 else full_source
    png = cairosvg.svg2png(url=str(source), output_width=size, output_height=size)
    Image.open(io.BytesIO(png)).convert("RGBA").save(OUT / f"icon-{size}.png", optimize=True)
Image.open(OUT / "icon-512.png").save(OUT / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
(OUT / "favicon.svg").write_bytes(micro_source.read_bytes())
(OUT / "manifest.webmanifest").write_text(
    '{\n  "name":"osTRIS",\n  "icons":[\n'
    '    {"src":"icon-192.png","sizes":"192x192","type":"image/png"},\n'
    '    {"src":"icon-512.png","sizes":"512x512","type":"image/png","purpose":"any maskable"}\n'
    '  ]\n}\n', encoding="utf-8")
