#version 300 es
// v38 · Shader ligero de la vista previa.
// Solo dibuja la imagen (con el zoom de la vista) y, al pintar, la máscara activa.
// Se usa en el primer cuadro (mientras el shader completo se prepara), durante los
// gestos de dos dedos y mientras se pinta: compila en milisegundos y cuesta una sola
// lectura de textura por píxel, así el zoom responde al instante en cualquier teléfono.
precision highp float;
precision highp int;

in vec2 vScreen;
out vec4 fragColor;

uniform sampler2D uImage;
uniform highp sampler2DArray uMasks;
uniform vec2 uViewOffset;
uniform vec2 uViewScale;
uniform float uFlipY;
uniform float uShowMask;
uniform int uActiveMask;
uniform float uMaskAlpha;     // v39: transparencia de la máscara (0.1–0.9)
// v44.23 · zonas con forma (igual que fondo.frag) para verlas en morado al pintar.
uniform vec4 uZoneA[12];
uniform vec4 uZoneB[12];
uniform float uImgAspect;
float zoneShape(vec2 uv, int i) {
    vec4 b = uZoneB[i];
    if (b.z < 0.5) return 0.0;
    vec4 a = uZoneA[i];
    vec2 d = vec2(uv.x - a.x, (uv.y - a.y) / max(uImgAspect, 1e-3));
    float c = cos(b.x), sn = sin(b.x);
    vec2 q = vec2(c * d.x + sn * d.y, -sn * d.x + c * d.y) / max(a.zw, vec2(1e-4));
    float r = b.z < 1.5 ? length(q) : max(abs(q.x), abs(q.y));
    float e0 = 1.0 - clamp(b.y, 0.0, 1.0) * 0.95 - 1e-3;
    return 1.0 - smoothstep(e0, 1.0, r);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 s = vec2(vScreen.x, mix(1.0 - vScreen.y, vScreen.y, uFlipY));
    vec2 uv = clamp(uViewOffset + s * uViewScale, vec2(0.0), vec2(1.0));
    vec3 c = texture(uImage, uv).rgb;
    if (uShowMask > 0.5 && uActiveMask >= 0) {
        float m = texture(uMasks, vec3(uv, float(uActiveMask))).r;
        if (uActiveMask >= 3 && uActiveMask < 12) m = max(m, zoneShape(uv, uActiveMask));
        c = mix(c, vec3(0.72, 0.39, 1.0), m * uMaskAlpha);
    }
    c += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
