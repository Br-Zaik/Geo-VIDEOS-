"""
Zeta Motion · dibujo propio para las miniaturas de «Mezcla y opacidad» (sin imágenes de terceros).
Fondo: atardecer con montañas y lago. Arriba: una esfera de color (rosa → violeta → cian).
Salida: base.raw y top.raw (RGBA 8 bits, N×N). La mezcla la hace el código de la app (BlendModes).
"""
import numpy as np, sys
N = int(sys.argv[1]) if len(sys.argv) > 1 else 168
S = 4                      # supermuestreo
W = N * S
y, x = np.mgrid[0:W, 0:W].astype(np.float32) / W

def mix(a, b, t): return a + (b - a) * t[..., None]
def col(h): h = h.lstrip('#'); return np.array([int(h[i:i+2], 16) / 255 for i in (0, 2, 4)], np.float32)
def smooth(e0, e1, v): t = np.clip((v - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def noise1(v, seed, n=10):
    r = np.random.RandomState(seed); k = r.rand(n + 2)
    i = np.floor(v * n).astype(int); f = v * n - i; f = f * f * (3 - 2 * f)
    return k[np.clip(i, 0, n + 1)] * (1 - f) + k[np.clip(i + 1, 0, n + 1)] * f

horizon = .60
# Cielo: violeta arriba → rosa → naranja en el horizonte
t = np.clip(y / horizon, 0, 1)
sky = mix(mix(np.broadcast_to(col('#241a4f'), (W, W, 3)), np.broadcast_to(col('#a8417a'), (W, W, 3)), smooth(0, .75, t)),
          np.broadcast_to(col('#ffb05a'), (W, W, 3)), smooth(.62, 1, t))
img = sky.copy()
# Estrellas pequeñas arriba
r = np.random.RandomState(3)
for _ in range(40):
    sx, sy = r.rand(), r.rand() * .3; s = .0025 + r.rand() * .002
    d = np.hypot(x - sx, y - sy); img += (np.clip(1 - d / s, 0, 1) ** 2)[..., None] * .8 * (1 - y / .35).clip(0, 1)[..., None]
# Sol grande y suave
sunx, suny = .70, .36
d = np.hypot(x - sunx, (y - suny))
img = mix(img, np.broadcast_to(col('#fff1c9'), (W, W, 3)), smooth(.085, .075, d))
img += (np.exp(-(d / .16) ** 2) * .35)[..., None] * col('#ffcf86')
# Montañas en capas (lejos claras, cerca oscuras)
for k, (base, amp, c, seed) in enumerate([(.50, .12, '#8a5a98', 1), (.55, .09, '#5a3f80', 2), (.59, .05, '#2f2656', 5)]):
    ridge = base - amp * (noise1(x, seed, 5) * .8 + noise1(x, seed + 7, 13) * .2)
    m = smooth(ridge - .002, ridge + .002, y) * (y < horizon + .002)
    img = mix(img, np.broadcast_to(col(c), (W, W, 3)), m)
# Lago con reflejo del cielo y del sol
wy = np.clip((y - horizon) / (1 - horizon), 0, 1)
ry = np.clip(2 * horizon - y, 0, 1)
refl = sky[np.clip((ry * W).astype(int), 0, W - 1), np.arange(W)[None, :].repeat(W, 0)]
water = mix(refl * .82, np.broadcast_to(col('#1b1838'), (W, W, 3)), smooth(0, 1, wy) * .75)
shimmer = (np.sin((y * 70 + noise1(x, 9, 30) * 3) * 6.283) > .6) * np.exp(-((x - sunx) / .06) ** 2) * (1 - wy) ** 1.5 * .8
water += shimmer[..., None] * col('#ffd79a')
img = np.where((y > horizon)[..., None], water, img)
# Orilla cercana con pinos
shore = .90 - .025 * noise1(x, 11, 6)
img = mix(img, np.broadcast_to(col('#140f26'), (W, W, 3)), smooth(shore - .003, shore + .003, y))
for px, h in [(.07, .17), (.14, .11), (.88, .13), (.95, .19)]:
    tri = (y > shore - h) & (np.abs(x - px) < (y - (shore - h)) * .26) & (y < shore + .01)
    img = np.where(tri[..., None], col('#140f26'), img)
base = np.clip(img, 0, 1)

# Esfera de color (capa de arriba), con transparencia fuera del círculo
cx, cy, rad = .40, .52, .28
d = np.hypot(x - cx, y - cy) / rad
ang = np.clip((x - cx + y - cy) / (2 * rad) * .5 + .5, 0, 1)
hue = mix(np.broadcast_to(col('#ff4fa3'), (W, W, 3)), np.broadcast_to(col('#7b4dff'), (W, W, 3)), smooth(0, .6, ang))
hue = mix(hue, np.broadcast_to(col('#28d7ff'), (W, W, 3)), smooth(.55, 1, ang))
# brillo: luz arriba-izquierda y borde más oscuro (así se notan los modos de contraste)
light = np.clip(1 - np.hypot(x - (cx - rad * .35), y - (cy - rad * .35)) / (rad * 1.3), 0, 1)
top_rgb = np.clip(hue * (.55 + .45 * (1 - d ** 2).clip(0, 1))[..., None] + light[..., None] ** 2 * .75, 0, 1)
alpha = np.clip((1 - d) * rad * W / 1.5, 0, 1)

def down(a): return a.reshape(N, S, N, S, -1).mean((1, 3))
b8 = (down(base) * 255 + .5).astype(np.uint8)
t8 = np.concatenate([(down(top_rgb * alpha[..., None]) / np.maximum(down(alpha[..., None]), 1e-6)).clip(0, 1), down(alpha[..., None])], -1)
t8 = (t8 * 255 + .5).astype(np.uint8)
np.dstack([b8, np.full((N, N), 255, np.uint8)]).tofile('base.raw'); t8.tofile('top.raw')
from PIL import Image
Image.fromarray(b8).save('base.png'); Image.fromarray(t8, 'RGBA').save('top.png')
print(N)
