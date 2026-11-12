"""Vista previa del flipbook de fuego coloreado como en el shader (fireRamp)."""
import sys, numpy as np
from PIL import Image
from scipy.ndimage import zoom
d = np.load(sys.argv[1]); T = d['T']; S = d['S']
def ramp(t):
    t = np.clip(t, 0, 1)[..., None]
    hue = np.array([1.0, 0.40, 0.07]); c0 = hue * hue * 0.3 + np.array([0.02, 0, 0])
    c2 = hue + (np.array([1.0, 0.86, 0.42]) - hue) * 0.62; c3 = np.array([1.0, 0.97, 0.88])
    ss = lambda a, b, x: np.clip((x - a) / (b - a), 0, 1) ** 2 * (3 - 2 * np.clip((x - a) / (b - a), 0, 1))
    c = c0 + (hue - c0) * ss(0, .38, t); c = c + (c2 - c) * ss(.34, .72, t); c = c + (c3 - c) * ss(.70, 1, t)
    return c
idx = [int(x) for x in sys.argv[3].split(',')] if len(sys.argv) > 3 else range(0, 32, 4)
tiles = []
for i in idx:
    t = zoom(T[i], 2, order=3); s = zoom(S[i], 2, order=3)
    heat = np.clip(t * 1.1, 0, 1)
    a = np.clip((heat - 0.12) / 0.2, 0, 1)
    bg = np.array([0.06, 0.07, 0.10])
    sm = np.clip(s * 0.35, 0, 0.8)[..., None]
    img = bg * (1 - sm) + np.array([0.18, 0.17, 0.18]) * sm
    img = img * (1 - a[..., None]) + ramp(heat) * a[..., None]
    tiles.append((np.flipud(np.clip(img, 0, 1)) * 255).astype(np.uint8))
Image.fromarray(np.concatenate(tiles, axis=1)).save(sys.argv[2])
