"""Empaqueta el fuego simulado + los sprites en UNA imagen de datos (fxbook.png, RGB).

Distribución (la usa el shader, ver FXB_* en fondo.frag):
  · fuego: 32 cuadros de 128×192 en 8 columnas × 4 filas (y = 0..767). R = calor, G = humo.
  · sprites: 8 celdas de 128×128 en la fila y = 768..895. R = detalle, G = forma, B = borde.
"""
import sys, numpy as np
from PIL import Image
from scipy.ndimage import zoom, gaussian_filter
fire = np.load(sys.argv[1]); spr = np.load(sys.argv[2])
T, S = fire['T'], fire['S']
atlas = np.zeros((896, 1024, 3), np.float32)
for i in range(32):
    t = T[i][:168]; s = S[i][:168]                     # se recorta el aire vacío de arriba
    t = zoom(t, (192 / 168, 128 / 112), order=3); s = zoom(s, (192 / 168, 128 / 112), order=3)
    t = np.flipud(t); s = np.flipud(s)                 # base de la llama abajo en la celda
    fade = np.ones_like(t); fade[:, :6] = np.linspace(0, 1, 6); fade[:, -6:] = np.linspace(1, 0, 6); fade[:6] *= np.linspace(0, 1, 6)[:, None]
    r, c = divmod(i, 8)
    atlas[r * 192:(r + 1) * 192, c * 128:(c + 1) * 128, 0] = np.clip(t / 1.0, 0, 1) * fade
    atlas[r * 192:(r + 1) * 192, c * 128:(c + 1) * 128, 1] = np.clip(gaussian_filter(s, 0.8) / 0.5, 0, 1) * fade
for j in range(8):
    atlas[768:896, j * 128:(j + 1) * 128] = spr[j]
Image.fromarray((np.clip(atlas, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(sys.argv[3], optimize=True)
