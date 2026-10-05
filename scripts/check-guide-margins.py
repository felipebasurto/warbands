#!/usr/bin/env python3
"""Check rendered guide text against the parchment margins, independently of the Java layout code."""
import argparse
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('screenshots', type=Path)
args = parser.parse_args()
files = sorted(args.screenshots.glob('*warbands-guide-*.png'))
if not files:
    parser.error('No rendered guide screenshots found; run gallery.guide first.')
failures = []
for path in files:
    image = Image.open(path).convert('RGB')
    border = [(x, y) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y)) == (90, 58, 34)]
    if not border:
        failures.append(f'{path.name}: parchment border not found')
        continue
    left, right = min(x for x, _ in border), max(x for x, _ in border)
    top, bottom = min(y for _, y in border), max(y for _, y in border)
    scale = (right - left + 1) / 240
    inside_left, inside_right = left + round(10 * scale), right + 1 - round(10 * scale)
    escaped = [(x, y) for y in range(top + round(25 * scale), bottom - round(25 * scale))
               for x in range(image.width)
               if (x < inside_left or x >= inside_right) and image.getpixel((x, y)) == (58, 42, 26)]
    if escaped:
        failures.append(f'{path.name}: {len(escaped)} text pixels outside horizontal content margins')
print(f'Checked {len(files)} rendered handbook pages; {len(failures)} margin failures.')
for failure in failures:
    print('FAIL', failure)
raise SystemExit(bool(failures))
