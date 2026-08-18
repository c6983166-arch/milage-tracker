from pathlib import Path
from PIL import Image

p = Path('app/src/main/res/drawable-nodpi/ba_l2_shiny_gold_logo.webp')
im = Image.open(p).convert('RGBA')
pix = im.load()
transparent = 0
for y in range(im.height):
    for x in range(im.width):
        r, g, b, a = pix[x, y]
        if a == 0:
            transparent += 1
            continue
        hi = max(r, g, b)
        lo = min(r, g, b)
        spread = hi - lo
        # Remove only neutral/near-white background pixels. Gold highlights remain colored.
        if lo >= 245 and spread <= 18:
            pix[x, y] = (r, g, b, 0)
            transparent += 1
        elif lo >= 225 and spread <= 20:
            # Soft edge removal prevents a white halo around the metallic-gold artwork.
            alpha = int(a * max(0, min(1, (245 - lo) / 20)))
            pix[x, y] = (r, g, b, alpha)
            if alpha < 255:
                transparent += 1

if transparent == 0:
    raise SystemExit('No background pixels became transparent; refusing to package an unverified logo.')

# Save back to WebP with alpha. The artwork itself is not redrawn or restyled.
im.save(p, 'WEBP', lossless=True, method=6)
check = Image.open(p).convert('RGBA')
amin, amax = check.getchannel('A').getextrema()
if amin != 0 or amax != 255:
    raise SystemExit(f'Logo alpha verification failed: {(amin, amax)}')
print(f'BA-L2 transparency verified: {check.size}, alpha={(amin, amax)}')
