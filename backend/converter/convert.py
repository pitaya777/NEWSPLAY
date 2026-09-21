"""Render a PDF to display-sized WebP pages and small WebP thumbnails."""
import sys
from pathlib import Path
import pypdfium2 as pdfium
from PIL import Image


def main():
    source, pages_dir, thumbs_dir = map(Path, sys.argv[1:4])
    pages_dir.mkdir(parents=True, exist_ok=True)
    thumbs_dir.mkdir(parents=True, exist_ok=True)
    pdf = pdfium.PdfDocument(str(source))
    if len(pdf) == 0:
        raise ValueError("PDF 没有页面")
    for index in range(len(pdf)):
        page = pdf[index]
        width, height = page.get_size()
        scale = min(3.0, max(1.0, 1920 / width, 1080 / height))
        image = page.render(scale=scale).to_pil().convert("RGB")
        image.save(pages_dir / f"page_{index + 1:03d}.webp", "WEBP", quality=88, method=4)
        image.thumbnail((360, 240), Image.Resampling.LANCZOS)
        image.save(thumbs_dir / f"thumb_{index + 1:03d}.webp", "WEBP", quality=72)
        page.close()
    print(len(pdf))


if __name__ == "__main__":
    main()
