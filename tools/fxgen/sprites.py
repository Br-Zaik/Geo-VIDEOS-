"""Zeta Motion · hojas y pétalos pintados por código (sin assets de terceros).

Cada sprite de 128×128 guarda DATOS (no color): R = detalle/luz (nervaduras, textura),
G = cobertura (forma), B = borde (contorno fino). El shader pone el color (estilo de hojas,
luz de la escena, sakura), así un mismo sprite sirve para verdes, secas, otoño o manga.
Se dibuja a 4× y se reduce (bordes suaves sin dientes). El eje x del sprite = largo de la hoja
(tallo a la izquierda, punta a la derecha), como en el shader.
"""
import numpy as np
from scipy.ndimage import gaussian_filter, zoom, binary_erosion

N = 128
SS = 4
M = N * SS
rng = np.random.default_rng(3)
y, x = (np.mgrid[0:M, 0:M] + 0.5) / M * 2.0 - 1.0     # x: largo, y: ancho, en [-1, 1]
x = x / 0.92; y = y / 0.92                              # margen para que los mips no se mezclen

def noise(scale, seed):
    r = np.random.default_rng(seed).standard_normal((M // scale + 2, M // scale + 2))
    return zoom(r, scale, order=3)[:M, :M]

def finish(cov, detail):
    cov = np.clip(cov, 0, 1)
    edge = cov - np.clip(gaussian_filter(binary_erosion(cov > 0.5, iterations=SS * 2).astype(float), SS * 0.6), 0, 1)
    edge = np.clip(edge * 1.4, 0, 1) * (cov > 0.02)
    out = np.stack([np.clip(detail, 0, 1), cov, edge], -1)
    out = out.reshape(N, SS, N, SS, 3).mean((1, 3))
    return out

def leaf(halfw, veins=7, serr=0.0, vein_ang=0.9, rib=1.0, tex=0.12, seed=0):
    t = (x + 1) / 2
    hw = halfw(x) * (1 + serr * np.sin(x * 70 + seed))
    inside = (np.abs(y) <= hw) & (np.abs(x) <= 1)
    d = hw - np.abs(y)                                    # distancia al borde (aprox.)
    cov = np.clip(d * M * 0.5, 0, 1) * inside
    # tallo (pecíolo) a la izquierda
    stem = (x < -0.95) & (x > -1.08) & (np.abs(y) < 0.018)
    cov = np.maximum(cov, stem.astype(float))
    det = 0.72 + 0.16 * (1 - t) - 0.10 * (np.abs(y) / (hw + 1e-3))   # más luz al centro
    det += tex * noise(16, seed + 1) * 0.5 + 0.04 * noise(4, seed + 2)
    # nervio central y secundarias (oscuras y finas)
    mid = np.exp(-(y / (0.012 + 0.01 * (1 - t))) ** 2) * (x < 0.97)
    det -= 0.26 * mid * rib
    for k in range(1, veins + 1):
        x0 = -0.9 + 1.75 * k / (veins + 1)
        # secundaria: sale del nervio central hacia el borde, inclinada hacia la punta
        line = np.abs((x - x0) - np.abs(y) * vein_ang)
        m = np.exp(-(line / 0.010) ** 2) * (np.abs(y) < hw * 0.88) * (np.abs(y) > 0.015)
        det -= 0.10 * m
    det = np.where(stem, 0.45, det)
    return finish(cov, det)

def maple(seed=0):
    r = np.hypot(x * 1.0 + 0.15, y)
    th = np.arctan2(y, x + 0.15)
    lobes = 0.58 + 0.30 * np.abs(np.cos(2.5 * th)) ** 0.5 - 0.12 * (np.cos(th) < -0.6)
    lobes *= 1 + 0.04 * np.sin(th * 26)
    inside = r < lobes
    cov = np.clip((lobes - r) * M * 0.5, 0, 1) * inside
    det = 0.74 + 0.08 * noise(16, seed + 5) + 0.04 * noise(4, seed + 6) - 0.12 * (r / lobes)
    for k in range(5):
        a = (k - 2) * 0.75
        line = np.abs(np.sin(th - a)) * r
        det -= 0.22 * np.exp(-(line / 0.012) ** 2) * (np.cos(th - a) > 0) * inside
    stem = (x < -0.2) & (x > -1.05) & (np.abs(y) < 0.016)
    cov = np.maximum(cov, stem.astype(float))
    det = np.where(stem & ~inside, 0.45, det)
    return finish(cov, det)

def petal(narrow=0.0, seed=0):
    t = np.clip((x + 1) / 2, 0, 1)
    hw = (0.50 - 0.15 * narrow) * np.sin(np.pi * np.clip(t, 0, 1) * 0.92) ** 0.75 * (0.55 + 0.45 * t)
    inside = (np.abs(y) <= hw) & (np.abs(x) <= 1)
    notch = np.hypot((x - 1.0) / 0.22, y / 0.14) < 1.0
    inside &= ~notch
    d = hw - np.abs(y)
    cov = np.clip(d * M * 0.5, 0, 1) * inside
    # R = posición base→punta (el shader pone rosa intenso en la base y casi blanco en la punta)
    det = 0.35 + 0.6 * t + 0.05 * noise(8, seed + 9)
    for k in range(-3, 4):                                   # venitas finas en abanico
        line = np.abs(y - k * 0.06 * t)
        det -= 0.06 * np.exp(-(line / 0.008) ** 2) * (t > 0.08)
    return finish(cov, det)

cells = [
    leaf(lambda xx: 0.42 * np.clip(1 - xx * xx, 0, 1) ** 0.72 * (1 - 0.25 * np.clip(xx, 0, 1) ** 2), veins=7, seed=1),   # ovalada
    maple(seed=2),                                                                                               # arce
    leaf(lambda xx: 0.20 * np.clip(1 - xx * xx, 0, 1) ** 0.8, veins=9, vein_ang=1.6, tex=0.08, seed=3),           # alargada
    leaf(lambda xx: 0.52 * np.clip(1 - xx * xx, 0, 1) ** 0.55, veins=6, serr=0.035, vein_ang=0.7, seed=4),        # redonda dentada
    petal(0.0, seed=5),                                                                                          # pétalo sakura
    petal(0.6, seed=6),                                                                                          # pétalo estrecho
    leaf(lambda xx: 0.34 * np.clip(1 - xx * xx, 0, 1) ** 0.6 * (1 + 0.3 * xx), veins=6, seed=7),                 # lanceolada ancha
    leaf(lambda xx: 0.30 * np.clip(1 - xx * xx, 0, 1) ** 0.9, veins=8, serr=0.02, seed=8),                       # sauce dentada
]
np.save('/tmp/sprites.npy', np.array(cells))
print('ok', np.array(cells).shape)
