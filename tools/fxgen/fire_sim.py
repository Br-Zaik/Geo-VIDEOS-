"""Zeta Motion · simulador de fuego (fluidos 2D, estilo Stam «stable fluids»).

Genera el flipbook del fuego SIN assets de terceros: todo sale de esta física.
- Aire caliente que sube (flotación), se enrosca (confinamiento de vorticidad + turbulencia),
  se enfría y deja humo.
- Se guarda CALOR y HUMO (no color): el shader colorea (fuego normal, azul, verde, violeta, manga).
- Bucle perfecto: se simulan 2N cuadros y se funde f[i+N] → f[i] (el cuadro N-1 enlaza con el 0).

Uso: python3 fire_sim.py salida.npz [semilla]
"""
import sys
import numpy as np
from scipy.ndimage import map_coordinates, gaussian_filter

W, H = 112, 224          # celdas de simulación (ancho, alto); y = 0 abajo
FRAMES = 32               # cuadros del bucle
SUB = 3                   # pasos de simulación por cuadro
WARM = 260                # pasos de calentamiento (el fuego ya está encendido)
rng = np.random.default_rng(int(sys.argv[2]) if len(sys.argv) > 2 else 7)

u = np.zeros((H, W)); v = np.zeros((H, W))     # velocidad x, y (celdas/paso)
T = np.zeros((H, W)); S = np.zeros((H, W))     # temperatura, humo
yy, xx = np.mgrid[0:H, 0:W].astype(float)

def advect(f, u, v, order=1):
    return map_coordinates(f, [yy - v, xx - u], order=order, mode='nearest')

def project(u, v, it=36):
    div = np.zeros_like(u)
    div[1:-1, 1:-1] = 0.5 * (u[1:-1, 2:] - u[1:-1, :-2] + v[2:, 1:-1] - v[:-2, 1:-1])
    p = np.zeros_like(u)
    for _ in range(it):
        p[1:-1, 1:-1] = (p[1:-1, 2:] + p[1:-1, :-2] + p[2:, 1:-1] + p[:-2, 1:-1] - div[1:-1, 1:-1]) * 0.25
        p[:, 0] = p[:, 1]; p[:, -1] = p[:, -2]; p[0, :] = p[1, :]; p[-1, :] = 0.0   # arriba abierto
    u[1:-1, 1:-1] -= 0.5 * (p[1:-1, 2:] - p[1:-1, :-2])
    v[1:-1, 1:-1] -= 0.5 * (p[2:, 1:-1] - p[:-2, 1:-1])
    u[:, 0] = u[:, -1] = 0; v[0, :] = 0
    return u, v

def vorticity(u, v, eps):
    w = np.zeros_like(u)
    w[1:-1, 1:-1] = 0.5 * (v[1:-1, 2:] - v[1:-1, :-2] - u[2:, 1:-1] + u[:-2, 1:-1])
    aw = np.abs(w)
    gx = np.zeros_like(u); gy = np.zeros_like(u)
    gx[1:-1, 1:-1] = 0.5 * (aw[1:-1, 2:] - aw[1:-1, :-2])
    gy[1:-1, 1:-1] = 0.5 * (aw[2:, 1:-1] - aw[:-2, 1:-1])
    n = np.sqrt(gx * gx + gy * gy) + 1e-6
    gx /= n; gy /= n
    return eps * gy * w, -eps * gx * w

# Quemador: base ancha en el centro con «lenguas» de combustible que cambian lentamente.
cx = W * 0.5
base_w = W * 0.36
noise_cols = gaussian_filter(rng.standard_normal((4000, W)), (6, 2.5))
turb_field = rng.standard_normal((4000, H // 8 + 2, W // 8 + 2))

def step(k):
    global u, v, T, S
    # fuente de calor (perfil suave + ruido que fluctúa en el tiempo)
    prof = np.exp(-((xx[0] - cx) / base_w) ** 4)
    flick = 0.65 + 0.9 * np.clip(noise_cols[k % 4000] * 1.6 + 0.4, 0, 1.4)
    src = prof * flick
    for r in range(3, 11):
        T[r] = np.maximum(T[r], src * (1.0 - (r - 3) / 12.0))
    # flotación: el calor sube, el humo pesa un poco
    v += (0.32 * T - 0.02 * S)
    # turbulencia grande que cambia con el tiempo (lenguas que se doblan)
    tf = turb_field[k % 4000]
    big = map_coordinates(tf, [yy / 8.0, xx / 8.0], order=3, mode='nearest')
    u += 0.22 * big * np.clip(T * 2.0, 0, 1)
    fu, fv = vorticity(u, v, 0.32)
    u += fu; v += fv
    u *= 0.99; v *= 0.97
    np.clip(v, -6, 6, out=v); np.clip(u, -4, 4, out=u)
    u, v = project(u, v)
    u2 = advect(u, u, v); v2 = advect(v, u, v); u, v = u2, v2
    T = advect(T, u, v); S = advect(S, u, v)
    # enfriamiento: rápido arriba (las puntas se cortan en lenguas) y humo que nace del calor
    cool = 0.012 + 0.028 * T * T
    S += np.clip(T, 0, 1) * cool * 0.9
    T = np.clip(T - cool, 0, 2)
    S *= 0.985
    T[-6:] *= 0.5; S[-6:] *= 0.7

for k in range(WARM):
    step(k)
frames_T, frames_S = [], []
k = WARM
for f in range(2 * FRAMES):
    for _ in range(SUB):
        step(k); k += 1
    frames_T.append(T.copy()); frames_S.append(S.copy())
frames_T = np.array(frames_T); frames_S = np.array(frames_S)
# bucle: out[i] = mezcla(f[i+N], f[i], i/N) → out[N-1] ≈ f[N-1] y le sigue out[0] = f[N]
outT, outS = [], []
for i in range(FRAMES):
    w = i / FRAMES
    a = frames_T[i + FRAMES]; b = frames_T[i]
    outT.append(np.maximum((1 - w) * a, w * b) * 0.6 + ((1 - w) * a + w * b) * 0.4)   # menos «fantasma» que la media
    outS.append((1 - w) * frames_S[i + FRAMES] + w * frames_S[i])
np.savez_compressed(sys.argv[1], T=np.array(outT), S=np.array(outS))
print('ok', np.array(outT).max(), np.array(outS).max())
