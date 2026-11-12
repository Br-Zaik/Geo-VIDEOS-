#version 300 es
// Fondo Zeta - shader principal.
// Todo el movimiento depende de uT (0..1 = un loop). Cada efecto usa un número
// ENTERO de ciclos por loop, así el último cuadro empalma con el primero.
precision highp float;
precision highp int;

// v44.15 · Las piezas extra (2.ª instancia, humo detrás/delante) necesitan su efecto base.
#if defined(FX_SMOKE2) || defined(FX_SMOKEBACK) || defined(FX_SMOKEFRONT)
#ifndef FX_SMOKE
#define FX_SMOKE 1
#endif
#endif
#if defined(FX_BLAST2) && !defined(FX_BLAST)
#define FX_BLAST 1
#endif
#if defined(FX_BLOOD2) && !defined(FX_BLOOD)
#define FX_BLOOD 1
#endif
#if defined(FX_SLASH2) && !defined(FX_SLASH)
#define FX_SLASH 1
#endif

in vec2 vScreen;
out vec4 fragColor;

uniform sampler2D uImage;
uniform sampler2D uFlow;     // RG: vector de flujo (unidades de imagen por ciclo)
uniform highp sampler2DArray uMasks;
uniform int uActiveMask;


uniform vec2 uViewOffset;
uniform vec2 uViewScale;
uniform float uFlipY;
uniform float uRotate;
uniform float uT;
// Parámetros de efectos en un Uniform Buffer (std140): evita superar el límite
// de vectores uniformes por defecto de GPUs Adreno/Mali económicas (≈224-256).
layout(std140) uniform ZetaParams {
    // ---- v36: forma/comportamiento, color propio y zonas de los efectos mejorados
    vec4 uNaturalBox;    // rectángulo pintado (x0, y0, x1, y1) para fijar la raíz real
    vec4 uFlameBox;      // rectángulo de las llamas: base del fuego
    vec4 uSmokeShape;    // forma, turbulencia, suavizado, origen (0 columna, 1 toda la zona)
    vec4 uFlameShape;    // forma, turbulencia, suavizado, altura
    vec4 uFlameExtra;    // chispas, humo
    vec4 uBlastShape;    // forma, destello, onda de choque, empuje direccional
    vec4 uBloodShape;    // forma, dirección (rad), apertura
    vec4 uSlashShape;    // forma, grosor
    vec4 uEnergyShape;   // forma, radio, turbulencia
    vec4 uEyesShape;     // forma, halo
    vec4 uFogShape;      // forma, turbulencia
    vec4 uRevealShape;   // forma, humo del borde
    vec4 uEventPhase;    // fase 0..1 de explosión, salpicadura, corte, revelación (-1 = bucle)
    vec4 uEventPhase2;   // destello, onda de choque
    vec4 uTintSmoke;     // rgb + w=1 si el usuario eligió color
    vec4 uTintFlame;
    vec4 uTintBlast;
    vec4 uTintBlood;
    vec4 uTintSlash;
    vec4 uTintEnergy;
    vec4 uTintEyes;
    vec4 uTintFog;
    vec4 uTintBlackaura;
    vec4 uTintReveal;
    float uAspect;       // ancho / alto de la imagen
    float uMono;
    float uPixelSnap;
    vec3 uFlow3;   // encendido, distancia, ciclos
    vec2 uDrift;   // deriva en zona 1 (uv por ciclo)
    vec3 uRipple;  // fuerza, frecuencia, ciclos
    vec3 uGlint;   // intensidad, densidad, ciclos
    vec3 uSway;    // fuerza, frecuencia, ciclos
    vec3 uHeat;    // fuerza, frecuencia, ciclos
    vec4 uRain;    // intensidad, ángulo (rad), ciclos, solo zona 1
    vec4 uSnow;    // intensidad, tamaño, ciclos, solo zona 1
    vec4 uSakura;  // intensidad, tamaño, ciclos, solo zona 1
    vec4 uStars;   // intensidad, densidad, ciclos, solo zona 1
    vec4 uFlies;   // intensidad, densidad, ciclos, solo zona 1
    vec4 uFog;     // intensidad, escala, ciclos, solo zona 1
    vec4 uRays;    // intensidad, origen x, ciclos, solo zona 1
    vec4 uEnergy;  // intensidad, rayos, ciclos, solo zona 1
    vec4 uSpeed;   // intensidad, ángulo, ciclos, solo zona 1
    vec4 uStudioSky;
    vec4 uStudioWater;
    vec4 uStudioDispersion;
    vec4 uDispersionMore;
    vec4 uCameraMode;
    vec4 uLoopControl;
    vec4 uZoneMotion;
    int uMaskZonemotion;
    vec4 uColorA, uColorB, uColorC, uColorD;
    vec4 uElementPose[4];
    vec4 uElementStyle[4];
    int uMaskSky;
    int uMaskWater;
    int uMaskDispersion;
    int uMaskCam;
    vec3 uCam;     // zoom, recorrido, ciclos
    vec4 uParallax;       // cantidad, dirección rad, ciclos, diferencia de profundidad
    vec4 uParallaxStyle;  // 0=planos, 1=órbita suave, 2=dolly · y foco · z dilatación · w placa de fondo (v46)
    vec4 uMesh;           // fuerza, tamaño, ciclos, variante
    vec4 uMeshDirection;  // dirección rad en x
    int uMaskMesh;
    vec4 uNatural;        // fuerza, dirección rad, ciclos, material
    vec4 uNaturalMore;    // flexibilidad, variación, ráfagas, borde fijo
    vec4 uNaturalDynamics; // peso, elasticidad (0..1)
    int uMaskNatural;
    vec4 uMotionRain;
    vec4 uMotionSnow;
    vec4 uMotionSakura;
    vec4 uMotionFog;
    vec4 uMotionRays;
    vec4 uMotionEnergy;
    vec4 uMotionSpeed;
    vec4 uMotionAsh;
    vec4 uMotionLeaves;
    vec4 uMotionEmbers;
    vec4 uMotionDust;
    vec4 uMotionBubbles;
    vec4 uMotionElectric;
    vec4 uMotionFocus;
    vec4 uMotionShock;
    vec4 uFxAsh;
    vec4 uFxLeaves;
    vec4 uLeavesStyle; // x=principal, y=copia: 0 verde, 1 seca, 2 otoño, 3 navidad
    vec4 uFxEmbers;
    vec4 uFxDust;
    vec4 uFxBubbles;
    vec4 uFxElectric;
    vec4 uFxFocus;
    vec4 uFxShock;
    vec4 uFxFlash;
    vec4 uFxTone;
    vec4 uFxGrain;
    vec4 uFxInk;
    vec4 uFxAshCopy;
    int uMaskAshCopy;
    vec4 uFxLeavesCopy;
    int uMaskLeavesCopy;
    vec4 uFxEmbersCopy;
    int uMaskEmbersCopy;
    vec4 uFxDustCopy;
    int uMaskDustCopy;
    vec4 uFxBubblesCopy;
    int uMaskBubblesCopy;
    vec4 uFxElectricCopy;
    int uMaskElectricCopy;
    vec4 uFxFocusCopy;
    int uMaskFocusCopy;
    vec4 uFxShockCopy;
    int uMaskShockCopy;
    vec4 uFxFlashCopy;
    int uMaskFlashCopy;
    vec4 uFxToneCopy;
    int uMaskToneCopy;
    vec4 uFxGrainCopy;
    int uMaskGrainCopy;
    vec4 uFxInkCopy;
    int uMaskInkCopy;
    vec4 uMotionAshCopy;
    vec4 uMotionLeavesCopy;
    vec4 uMotionEmbersCopy;
    vec4 uMotionDustCopy;
    vec4 uMotionBubblesCopy;
    vec4 uMotionElectricCopy;
    vec4 uMotionFocusCopy;
    vec4 uMotionShockCopy;
    vec4 uFxComet;
    int uMaskComet;
    vec4 uFxRunes;
    int uMaskRunes;
    vec4 uFxHatch;
    int uMaskHatch;
    vec4 uFxPulse;
    int uMaskPulse;
    vec4 uFxSmoke;
    vec4 uSmokeControl;   // color, densidad, deriva, delante de PNG
    int uMaskSmoke;
    vec4 uFxBlast;
    vec4 uBlastMore;     // estilo, humo, chispas, reservado
    int uMaskBlast;
    vec4 uMotionBlast;
    vec4 uFxBlood;
    vec4 uBloodStyle;
    int uMaskBlood;
    vec4 uMotionBlood;
    vec4 uFxSlash;
    vec4 uSlashStyle;
    int uMaskSlash;
    vec4 uMotionSlash;
    vec4 uFxReveal;
    vec4 uRevealStyle;
    int uMaskReveal;
    vec4 uFxShards;
    int uMaskShards;
    vec4 uFxFlame;
    vec4 uFxEyes;
    vec4 uFxBlink;
    vec4 uFxCloth;
    int uMaskFlame;
    int uMaskEyes;
    int uMaskBlink;
    int uMaskCloth;
    vec4 uMotionComet;
    vec4 uMotionRunes;
    vec4 uMotionPulse;
    vec4 uMotionSmoke;
    vec4 uMotionShards;
    vec4 uFxBokeh;
    int uMaskBokeh;
    vec4 uFxSparkle;
    int uMaskSparkle;
    vec4 uFxLightleak;
    int uMaskLightleak;
    vec4 uFxWaterfall;
    int uMaskWaterfall;
    vec4 uFxReflect;
    int uMaskReflect;
    vec4 uFxBlackaura;
    int uMaskBlackaura;
    vec4 uFxInkdrip;
    int uMaskInkdrip;
    vec4 uFxDramatic;
    int uMaskDramatic;
    int uMaskFlow;
    int uMaskDrift;
    int uMaskRipple;
    int uMaskGlint;
    int uMaskSway;
    int uMaskHeat;
    int uMaskRain;
    int uMaskSnow;
    int uMaskSakura;
    int uMaskStars;
    int uMaskFlies;
    int uMaskFog;
    int uMaskRays;
    int uMaskEnergy;
    int uMaskSpeed;
    int uMaskAsh;
    int uMaskLeaves;
    int uMaskEmbers;
    int uMaskDust;
    int uMaskBubbles;
    int uMaskElectric;
    int uMaskFocus;
    int uMaskShock;
    int uMaskFlash;
    int uMaskTone;
    int uMaskGrain;
    int uMaskInk;
    vec4 uLoopInfo;      // duración del bucle en segundos
    vec4 uBolt;          // intensidad, x, rayos por bucle, destello
    int uMaskBolt;
    vec4 uGust;          // fuerza, momento, duración, velocidad
    vec4 uGustMore;      // hojas y polvo
    vec4 uMotionGust;
    int uMaskGust;
    vec4 uTornado;       // fuerza, radio, giros, tipo
    vec4 uMotionTornado;
    int uMaskTornado;
    vec4 uAppear;        // opacidad, momento, transición, tipo
    vec4 uAppearPose;    // x, y, alto, giro
    float uCharAspect;
    vec4 uClouds3;       // opacidad, tamaño, velocidad, luz
    int uMaskClouds3;
    vec4 uShore;         // intensidad, olas, velocidad, tinte
    vec4 uShoreMore;     // espuma, estilo, reservado, reservado
    vec4 uMotionShore;
    int uMaskShore;
    vec4 uSunpath;       // brillo, x, ancho, titileo
    vec4 uLightStyles;   // rayos, sol, aura y llamas: estilos dentro de los efectos originales
    int uMaskSunpath;
    vec4 uImpact;        // zoom, temblor, momento, golpes
    vec4 uImpactMore;    // destello
    // v44 · efectos nuevos (cada uno: intensidad/valor/ciclos/zona, «More» con su estilo,
    // vector de movimiento = dirección·fuerza + origen, y ranura de máscara)
    vec4 uFxSand;
    vec4 uSandMore;
    vec4 uMotionSand;
    int uMaskSand;
    vec4 uFxCloudshadow;
    vec4 uCloudshadowMore;
    vec4 uMotionCloudshadow;
    int uMaskCloudshadow;
    vec4 uFxGlassrain;
    vec4 uGlassrainMore;
    int uMaskGlassrain;
    vec4 uFxBirds;
    vec4 uBirdsMore;
    vec4 uMotionBirds;
    int uMaskBirds;
    vec4 uFxAurora;
    vec4 uAuroraMore;
    int uMaskAurora;
    vec4 uFxCitylights;
    vec4 uCitylightsMore;
    vec4 uMotionCitylights;
    int uMaskCitylights;
    vec4 uFxLensflare;
    vec4 uLensflareMore;
    vec4 uMotionLensflare;
    int uMaskLensflare;
    vec4 uRainMore;      // charcos, gotas cercanas, neblina, reservado
    vec4 uStarsMore;     // estrellas fugaces, reservado…
    // v44 · segunda instancia simultánea (dos explosiones, dos humos… en sitios distintos)
    vec4 uFxBlast2;
    vec4 uBlastShape2;
    vec4 uBlastMore2;
    vec4 uMotionBlast2;
    vec4 uTintBlast2;
    int uMaskBlast2;
    vec4 uFxBlood2;
    vec4 uBloodStyle2;
    vec4 uBloodShape2;
    vec4 uMotionBlood2;
    vec4 uTintBlood2;
    int uMaskBlood2;
    vec4 uFxSlash2;
    vec4 uSlashStyle2;
    vec4 uSlashShape2;
    vec4 uMotionSlash2;
    vec4 uTintSlash2;
    int uMaskSlash2;
    vec4 uFxSmoke2;
    vec4 uSmokeShape2;
    vec4 uSmokeControl2;
    vec4 uMotionSmoke2;
    vec4 uTintSmoke2;
    int uMaskSmoke2;
    vec4 uEventPhaseB;
    vec4 uSnowMore;      // v44: remolinos, bruma de ventisca
    vec4 uFogMore;       // v44: altura de la niebla baja, velo base
    vec4 uLeavesMore;    // v44: remolino de hojas
    vec4 uWind;          // v44: viento global → ráfagas, turbulencia, dirección (x, y)
    vec4 uCamManual;     // v44: cámara libre → posición x, y, giro (rad), zoom
    vec4 uDepthInfo;     // v45: profundidad IA → activa, capas (0 = continua), corrección de zonas, ver mapa
    // v44.11 · efectos nuevos para manga / manhwa
    vec4 uFxSymbols;     // intensidad, tamaño, ciclos, zona
    vec4 uSymbolsMore;   // estilo (0 gota, 1 vena, 2 «!», 3 mareo)
    vec4 uMotionSymbols;
    int uMaskSymbols;
    vec4 uFxGlowup;      // intensidad, resplandor, ciclos, zona
    vec4 uGlowupMore;    // estilo (0 dorado, 1 rosa, 2 azul), luces que suben
    int uMaskGlowup;
    vec4 uFxInkweather;  // intensidad, cantidad, ciclos, zona
    vec4 uInkweatherMore;// estilo (0 lluvia de tinta, 1 nieve de tinta, 2 lluvia blanca), grosor
    vec4 uMotionInkweather;
    int uMaskInkweather;
    // v44.12 · luz entre hojas, cáusticas, vapor, luz parpadeante
    vec4 uFxDapple;      // intensidad, tamaño, ciclos, zona
    vec4 uDappleMore;    // estilo, suavidad
    int uMaskDapple;
    vec4 uFxCaustics;
    vec4 uCausticsMore;  // estilo
    int uMaskCaustics;
    vec4 uFxSteam;       // intensidad, ancho, ciclos, zona
    vec4 uSteamMore;     // altura
    vec4 uMotionSteam;
    int uMaskSteam;
    vec4 uFxFlicker;     // intensidad, -, ciclos, zona
    vec4 uFlickerMore;   // estilo
    int uMaskFlicker;
    // v44.13 · animar los cielos, mares y luces PINTADOS en la imagen + acabado de cine
    vec4 uFxSkydrift;    // intensidad, distancia, ciclos, zona
    vec4 uSkydriftMore;  // horizonte (y 0..1), vida (deformación suave)
    vec4 uMotionSkydrift;
    int uMaskSkydrift;
    vec4 uFxSeawaves;    // intensidad, tamaño de olas, ciclos, zona
    vec4 uSeawavesMore;  // horizonte, destellos
    int uMaskSeawaves;
    vec4 uFxSunlight;    // intensidad, tamaño, ciclos, zona
    vec4 uSunlightMore;  // estilo (0 atardecer, 1 día, 2 luna), rayos, calidez del ambiente
    vec4 uMotionSunlight;
    int uMaskSunlight;
    vec4 uFxFinish;      // intensidad del «look», resplandor, viñeta, grano
    vec4 uFinishMore;    // look (0..4), aberración cromática
    // v44.14 · luz y color «de edit»: tono en degradado, bruma adaptada, luz de color, contorno
    vec4 uFxGradmap;     // mezcla, contraste, -, zona
    vec4 uGradmapMore;   // paleta (0..5)
    int uMaskGradmap;
    vec4 uFxAtmos;       // intensidad, altura, ciclos, zona
    vec4 uAtmosMore;     // color (0 de la imagen, 1 blanca, 2 azul noche, 3 cálida)
    int uMaskAtmos;
    vec4 uFxLightdir;    // intensidad, alcance, ciclos, zona
    vec4 uLightdirMore;  // color (0..5), borde brillante, sombra del otro lado
    vec4 uMotionLightdir;
    int uMaskLightdir;
    vec4 uFxRimlight;    // intensidad, grosor, ciclos, zona
    vec4 uRimlightMore;  // color (0..5)
    vec4 uMotionRimlight;
    // v44.15 · efectos «de editor de PC»
    vec4 uFxGlitch;      // intensidad, frecuencia, ciclos, zona
    vec4 uGlitchMore;    // estilo (0 RGB, 1 bloques, 2 ambos)
    vec4 uFxRadialblur;  // intensidad, -, ciclos, zona
    vec4 uRadialblurMore;// estilo (0 pulso, 1 constante)
    vec4 uMotionRadialblur;
    vec4 uFxDeepglow;    // intensidad, umbral, tamaño, zona
    vec4 uDeepglowMore;  // color (0 natural, 1 cálido, 2 frío, 3 rosa)
    int uMaskDeepglow;
    vec4 uFxVhs;         // intensidad, líneas, ciclos, ruido
    vec4 uFxFilmburn;    // intensidad, tamaño, ciclos, zona
    vec4 uFilmburnMore;  // color (0 naranja, 1 rojo, 2 azul)
    vec4 uFxRaindrops;   // intensidad, cantidad, ciclos, zona
    vec4 uRaindropsMore; // estilo (0 gotas cercanas, 1 goteo, 2 salpicaduras), tamaño
    int uMaskRaindrops;
    vec4 uFxFocusblur;   // intensidad, ancho enfocado, ciclos, zona
    vec4 uFocusblurMore; // estilo (0 banda, 1 círculo), posición y del foco
    vec4 uMotionFocusblur;
    // v44.11 · color propio de cada efecto: (modo 0 original/1 B y N/2 sepia, saturación −1..1,
    // brillo −1..1, contraste −1..1). Índices en EffectGrade.ids (Kotlin).
    vec4 uGrade[61];
};
uniform vec2 uTexSize;
uniform float uShowMask;
uniform float uOverlayOnly;
uniform float uFast;          // 1 = gesto en curso en un teléfono lento: solo la imagen
uniform float uMotionBlur;    // v44.20 · estela de cámara (0 = apagada; la da «Calidad cine»)
uniform float uEmitAlpha;     // v44.19 · 1 = escena para «Calidad cine»: alfa = luz añadida por los efectos


// Dirección (x,y) y origen normalizado (z,w), compartidos por UI/vista previa/export.


// Segunda instancia independiente de cada efecto anime/manga.


// Nuevos efectos procedurales, independientes de servicios externos.

// v30: fondos localizados anime / manga / manhwa.

const float TAU = 6.28318530718;

// ---- v16: escenas anime con eventos ----
uniform sampler2D uCharacter;
// v45: mapa de profundidad calculado UNA vez por imagen (R: 1 = cerca, 0 = lejos).
uniform sampler2D uDepth;


// v44.23 · Zonas con forma (círculo / rectángulo) que se arrastran con tiradores. Una por
// máscara propia (3..11): A = (centro, medio ancho, medio alto en anchos de imagen),
// B = (giro, borde suave, tipo 1 círculo · 2 rectángulo, —). Fórmula = ZoneShapes.value.
uniform vec4 uZoneA[12];
uniform vec4 uZoneB[12];
float zoneShape(vec2 uv, int i) {
    vec4 b = uZoneB[i];
    if (b.z < 0.5) return 0.0;
    vec4 a = uZoneA[i];
    vec2 d = vec2(uv.x - a.x, (uv.y - a.y) * uTexSize.y / max(uTexSize.x, 1.0));
    float c = cos(b.x), sn = sin(b.x);
    vec2 q = vec2(c * d.x + sn * d.y, -sn * d.x + c * d.y) / max(a.zw, vec2(1e-4));
    float r = b.z < 1.5 ? length(q) : max(abs(q.x), abs(q.y));
    float e0 = 1.0 - clamp(b.y, 0.0, 1.0) * 0.95 - 1e-3;
    return 1.0 - smoothstep(e0, 1.0, r);
}

// Capas base del atlas de máscaras: 0 = proteger/personaje, 1 = zona azul, 2 = zona verde.
float maskLayer(vec2 uv, int layer) {
    float m = texture(uMasks, vec3(uv, float(layer))).r;
    if (layer >= 3 && layer < 12) m = max(m, zoneShape(uv, layer));
    return m;
}

// slot: -2 toda la imagen, -1 zona heredada, 0..11 máscara propia.
// slot + 128: el efecto queda DETRÁS del personaje (capa Proteger), sin otro shader.
float maskValue(vec2 uv, int slot, float fallback, float enabled) {
    float behind = 1.0;
    if (slot >= 64) { behind = 1.0 - maskLayer(uv, 0); slot -= 128; }
    if (slot == -2) return behind;
    return behind * (slot >= 0 ? maskLayer(uv, slot) : mix(1.0, fallback, enabled));
}

// Hash sin seno (Dave Hoskins, licencia MIT)
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}


float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
vec3 leafPalette(float style, vec2 p) {
    if (uMono > 0.5) return vec3(0.88);
    // v44.21 · variación suave (antes por cuadros: se veían bloques dentro de las hojas grandes)
    float n = smoothstep(0.25, 0.75, vnoise(p * 4.0 + vec2(19.0, 73.0)));
    if (style < 0.5) {
        return mix(vec3(0.19, 0.58, 0.24), vec3(0.48, 0.90, 0.34), n);
    }
    if (style < 1.5) {
        return mix(vec3(0.48, 0.28, 0.10), vec3(0.82, 0.64, 0.24), n);
    }
    if (style < 2.5) {
        return mix(vec3(0.76, 0.18, 0.07), vec3(0.96, 0.56, 0.08), n);
    }
    return n > 0.82 ? vec3(0.82, 0.08, 0.10) : mix(vec3(0.05, 0.28, 0.10), vec3(0.12, 0.55, 0.18), n);
}

// v40.1: cada octava gira ~37° (matriz × 2.03): desaparecen las formas cuadradas
// alineadas con la rejilla del ruido de valor (manchas "en bloque" en humo y fuego).
const mat2 OCT = mat2(1.62, 1.22, -1.22, 1.62);
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 4; k++) {
        v += a * vnoise(p);
        p = OCT * p + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

// ---------- v36: utilidades compartidas por los efectos ----------
// Todas son funciones de la posición y de fases ENTERAS del bucle, de modo que el
// cuadro N (que no se exporta) coincide con el 0 y la vista previa = exportación.

// Ruido celular (Worley F1) barato: 3x3 celdas.
float worley(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d = 8.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 g = vec2(float(x), float(y));
        vec2 o = hash22(i + g);
        d = min(d, length(g + o - f));
    }
    return d;
}

// fbm de 3 octavas para efectos grandes: mismo aspecto con un 25% menos de coste.
float fbm3(vec2 p) {
    float v = 0.5 * vnoise(p);
    p = OCT * p + vec2(17.1, 9.2);
    v += 0.25 * vnoise(p);
    p = OCT * p + vec2(17.1, 9.2);
    v += 0.125 * vnoise(p);
    return v / 0.875;
}

// Dos capas desplazadas media fase (flow map de Valve) con corrección de contraste:
// la mezcla lineal de dos ruidos baja la varianza a mitad de ciclo; se compensa con
// 1/sqrt((1-a)^2+a^2) para que el humo no "respire" ni se aclare en el centro del bucle.
float contrastFix(float n, float a) {
    return 0.5 + (n - 0.5) / sqrt((1.0 - a) * (1.0 - a) + a * a);
}

// Color personalizado (HEX) si el usuario lo eligió; si no, el de la paleta.
vec3 tintOr(vec3 base, vec4 tint) {
    vec3 c = tint.w > 0.5 ? tint.rgb : base;
    return uMono > 0.5 ? vec3(luma(c)) : c;
}

// Sombreado anime de 2 tonos. El gradiente de pantalla se pasa al espacio de la
// imagen con la jacobiana de P, así la luz no cambia con el giro del vídeo vertical,
// el volteo del PNG ni la resolución (solo se usa la dirección).
float celLight(float n, vec2 P) {
    vec2 gs = vec2(dFdx(n), dFdy(n));
    vec2 px = dFdx(P), py = dFdy(P);
    float det = px.x * py.y - px.y * py.x;
    if (abs(det) < 1e-12) return 0.6;
    // J^T * g = gs  ->  g = inverse(J^T) * gs
    vec2 g = vec2(py.y * gs.x - px.y * gs.y, -py.x * gs.x + px.x * gs.y) / det;
    float l = dot(normalize(g + vec2(1e-7)), normalize(vec2(-0.55, -0.8)));
    return smoothstep(-0.25, 0.35, l);
}

// 1 cerca de 0, 0 desde e en adelante (suave)
float falloff(float d, float e) {
    return 1.0 - smoothstep(0.0, e, d);
}

// v46: placa de fondo (lo que hay detrás del personaje, rellenado). En el 2.5D, los píxeles
// de fondo leen la placa; así, al moverse, no arrastran el contorno del personaje.
uniform sampler2D uPlate;
float gPlate = 0.0;   // 0 = imagen original, 1 = placa (por píxel, lo fija el 2.5D)
vec3 sourcePixel(vec2 q) {
    q = clamp(q, vec2(0.0), vec2(1.0));
    vec3 c = texture(uImage, q).rgb;
    if (gPlate > 0.0) c = mix(c, texture(uPlate, q).rgb, gPlate);
    return c;
}
vec3 sampleImg(vec2 q) {
    q=clamp(q,vec2(0.0),vec2(1.0));
    if(uPixelSnap>0.5)q=(floor(q*uTexSize)+0.5)/uTexSize;
    vec3 c=sourcePixel(q);
    if(uColorD.y>0.0){
        vec2 shift=vec2(uColorD.y*0.009,0.0);
        c.r=sourcePixel(q+shift).r;c.b=sourcePixel(q-shift).b;
    }
    if(uColorC.x>0.0){
        vec2 stepUV=1.0/max(uTexSize,vec2(1.0));
        vec3 neighbors=(sourcePixel(q+vec2(stepUV.x,0.0))+sourcePixel(q-vec2(stepUV.x,0.0))+
            sourcePixel(q+vec2(0.0,stepUV.y))+sourcePixel(q-vec2(0.0,stepUV.y)))*0.25;
        c+=uColorC.x*1.6*(c-neighbors);
    }
    if(uColorC.y>0.0){
        vec2 r=vec2(0.0045);
        vec3 glow=(sourcePixel(q+r)+sourcePixel(q-r)+sourcePixel(q+vec2(r.x,-r.y))+sourcePixel(q+vec2(-r.x,r.y)))*0.25;
        c+=glow*smoothstep(0.45,0.9,dot(glow,vec3(0.2126,0.7152,0.0722)))*uColorC.y*0.45;
    }
    return c;
}
// Keep the sampling footprint inside the source. Pin the outermost pixel centers,
// taper the displacement near edges and saturate it smoothly before the limit.
vec2 safeWarp(vec2 uv, vec2 displacement) {
    vec2 halfPixel=0.5/max(uTexSize,vec2(1.0));
    vec2 center=clamp(uv,halfPixel,1.0-halfPixel);
    vec2 room=max(vec2(0.0),min(center-halfPixel,1.0-halfPixel-center));
    vec2 envelope=smoothstep(vec2(0.0),vec2(0.055),room);
    vec2 delta=displacement*envelope;
    delta=delta/(vec2(1.0)+abs(delta)/max(room*0.25,vec2(0.000001)));
    return center+delta;
}
vec3 warpSample(vec2 uv,vec2 displacement){return sampleImg(safeWarp(uv,displacement));}

vec3 gradeColor(vec3 c,vec2 uv){
    c=c*exp2(uColorA.w)+uColorA.x;
    float lum=dot(c,vec3(0.2126,0.7152,0.0722));
    c+=uColorB.z*0.3*(1.0-smoothstep(0.0,0.6,lum));
    c+=uColorB.w*0.3*smoothstep(0.35,1.0,lum);
    c=(c-0.5)*(1.0+uColorA.y)+0.5;
    c+=uColorB.x*vec3(0.12,0.01,-0.12);
    if(abs(uColorB.y)>0.00001){
        vec3 axis=vec3(0.577350269);
        c=c*cos(uColorB.y)+cross(axis,c)*sin(uColorB.y)+axis*dot(axis,c)*(1.0-cos(uColorB.y));
    }
    lum=dot(c,vec3(0.2126,0.7152,0.0722));
    c=mix(vec3(lum),c,1.0+uColorA.z);
    c=mix(c,c*0.75+0.15,uColorD.x);
    c*=1.0-uColorC.z*smoothstep(0.2,0.7,length(uv-0.5))*0.85;
    if(uColorC.w>0.0)c+=(hash12(floor(uv*uTexSize))-0.5)*uColorC.w*0.16;
    return c;
}

// Rota un campo de caída vertical a la dirección elegida en la UI.
// (0,1) conserva el movimiento hacia abajo del shader original.
// v44: la LONGITUD de m.xy es la fuerza del viento/joystick (1 = como la v43). Más fuerza =
// el campo avanza más deprisa en pantalla y se estira en la dirección del movimiento (estela).
// El bucle se conserva: el tiempo del campo no cambia, solo su escala a lo largo del avance.
float windOf(vec4 m) { return clamp(length(m.xy), 0.5, 2.0); }
vec2 oriented(vec2 p, vec4 m) {
    vec2 d = normalize(m.xy + vec2(0.000001));
    vec2 base = p - m.zw * vec2(uAspect, 1.0);
    return vec2(d.y * base.x - d.x * base.y,
                (d.x * base.x + d.y * base.y) / sqrt(windOf(m)));
}
// v44 · Viento global: ráfagas (empujón que va y viene) y turbulencia (remolinos suaves).
// Cierra el bucle: todo depende de sin/cos de fases enteras de uT.
vec2 windWarp(vec2 p) {
    if (uWind.x <= 0.0 && uWind.y <= 0.0) return p;
    vec2 d = normalize(uWind.zw + vec2(1e-5, 0.0));
    float gust = uWind.x * (0.55 * sin(TAU * uT) + 0.30 * sin(TAU * 2.0 * uT + 1.3) + 0.15 * sin(TAU * 3.0 * uT + 0.4));
    p -= d * gust * 0.045;
    if (uWind.y > 0.0) {
        float a = TAU * uT;
        vec2 q = p * 3.0;
        vec2 w = vec2(vnoise(q + vec2(cos(a), sin(a)) * 0.8), vnoise(q + vec2(5.2, 1.3) + vec2(sin(a), cos(a)) * 0.8)) - 0.5;
        p += w * uWind.y * 0.06;
    }
    return p;
}
float emitArea(vec2 p, vec4 m) {
    vec2 d = normalize(m.xy + vec2(0.000001));
    vec2 from = m.zw * vec2(uAspect, 1.0);
    // El punto central conserva cobertura total; un emisor en un borde entra desde ese lado.
    if (distance(m.zw, vec2(0.5)) < 0.001) return 1.0;
    float edge = dot(p - from, d);
    return smoothstep(-0.04, 0.18, edge);
}

// ---------- Lluvia ----------
float rainLayer(vec2 P, float scale, float cyc, float ang, float seed) {
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 R = vec2(ca * P.x + sa * P.y, -sa * P.x + ca * P.y);
    const float N = 6.0;
    vec2 g = vec2(R.x * scale, R.y * scale * 0.25);
    g.y -= uT * cyc * N;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 idp = vec2(id.x, mod(id.y, N)) + seed;
    vec2 h = hash22(idp);
    float on = step(0.45, hash12(idp + 7.13));
    float x = 0.2 + 0.6 * h.x;
    // Variación por gota (no otra capa FX): profundidad, grosor y longitud no uniformes.
    float len = 0.22 + 0.58 * h.y;
    float y0 = (1.0 - len) * hash12(idp + 3.7);
    float width = mix(0.022, 0.06, hash12(idp + 12.0));
    float w = falloff(abs(f.x - x), width);
    float v = smoothstep(y0, y0 + 0.07, f.y) * (1.0 - smoothstep(y0 + len - 0.06, y0 + len, f.y));
    // v38: estela de movimiento: la cola se desvanece y la cabeza (abajo) brilla más.
    float head = smoothstep(y0, y0 + len, f.y);
    return w * v * on * (0.35 + 0.65 * head);
}

// v38: salpicaduras de lluvia en el suelo (anillos elípticos que nacen y se apagan).
float rainSplash(vec2 P, float cyc) {
    vec2 g = vec2(P.x * 9.0, P.y * 24.0);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    vec2 h = hash22(id + 3.1);
    float ph = fract(uT * cyc + h.x);
    float on = step(0.5, h.y);
    vec2 d = (f - (h - 0.5) * 0.4) * vec2(1.0, 2.4);
    float r = ph * 0.42;
    float ring = falloff(abs(length(d) - r), 0.03 + 0.03 * ph);
    float drop = falloff(length(d), 0.05) * smoothstep(0.12, 0.0, ph);
    return (ring * (1.0 - ph) + drop) * on;
}

// ---------- Nieve ----------
float snowLayer(vec2 P, float scale, float cyc, float size, float seed, float blur) {
    const float N = 5.0;
    vec2 g = P * scale;
    g.y -= uT * cyc * N;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 idp = vec2(id.x, mod(id.y, N)) + seed;
    vec2 h = hash22(idp);
    float on = step(0.5, hash12(idp + 1.7));
    float k = 1.0 + floor(h.y * 2.0);
    vec2 c = vec2(0.5 + 0.28 * sin(TAU * (uT * k + h.x)), 0.25 + 0.5 * h.y);
    // Diferentes tamaños y transparencias generan sensación de planos cercanos/lejanos.
    float r = clamp(size * (0.043 + 0.12 * h.x), 0.018, 0.20);
    float d = length(f - c);
    float depthAlpha = mix(0.55, 1.0, h.x);
    // v38: los copos cercanos son discos suaves (desenfoque de profundidad); los lejanos, nítidos.
    float flake = 1.0 - smoothstep(r * mix(0.35, 0.0, blur), r, d);
    return flake * on * depthAlpha * mix(1.0, 0.7, blur);
}

// ---------- Pétalos de sakura ----------
// v44.21 · LIBRO DE EFECTOS: fuego simulado con física de fluidos + hojas y pétalos pintados
// con detalle (tools/fxgen; todo propio, sin assets de terceros). Guarda DATOS, no color:
// fuego R = calor, G = humo; sprites R = detalle, G = forma, B = borde. El color lo pone el
// efecto (variantes, manga, luz de la escena). Solo se carga si se usa (uFxReady = 0 → procedural).
uniform sampler2D uFxBook;
uniform float uFxReady;
const vec2 FXB_SIZE = vec2(1024.0, 896.0);
vec2 fxFireCell(float i, vec2 q) {
    float c = mod(i, 8.0);
    float r = floor(i / 8.0);
    vec2 px = vec2(c * 128.0 + clamp(q.x, 0.0, 1.0) * 128.0, r * 192.0 + (1.0 - clamp(q.y, 0.0, 1.0)) * 192.0);
    px = clamp(px, vec2(c * 128.0 + 1.0, r * 192.0 + 1.0), vec2(c * 128.0 + 127.0, r * 192.0 + 191.0));
    return textureLod(uFxBook, px / FXB_SIZE, 0.0).rg;
}
// q: (0..1 a lo ancho, 0 base .. 1 arriba de la celda); frame: 0..32 (con fracción: se funden)
vec2 fxFire(vec2 q, float frame) {
    if (q.x <= 0.0 || q.x >= 1.0 || q.y >= 1.0) return vec2(0.0);
    float f0 = floor(frame);
    return mix(fxFireCell(mod(f0, 32.0), q), fxFireCell(mod(f0 + 1.0, 32.0), q), frame - f0);
}
// q en [-1, 1]: x a lo largo (tallo −1, punta +1), y a lo ancho
vec3 fxSprite(float cell, vec2 q, float lod) {
    if (abs(q.x) > 1.0 || abs(q.y) > 1.0) return vec3(0.0);
    vec2 n = q * 0.46 + 0.5;
    vec2 px = vec2(cell * 128.0 + n.x * 128.0, 768.0 + n.y * 128.0);
    return textureLod(uFxBook, px / FXB_SIZE, clamp(lod, 0.0, 2.5)).rgb;
}

// v44.20 · pétalos que cruzan el borde de su celda y se solapan (4 celdas cercanas), más
// sueltos (no en cuadrícula), con sombreado de cara/revés y desenfoque para la capa cercana.
vec4 sakuraLayerX(vec2 P, float scale, float cyc, float size, float seed, float soft) {
    const float N = 6.0;
    vec2 g = P * scale;
    g.y -= uT * cyc * N;
    g.x += uT * cyc * N;
    vec2 base = floor(g);
    vec2 fg = fract(g);
    vec2 side = sign(fg - 0.5);
    float aa = fwidth(g.x) * (1.0 + soft * 2.5) + soft * 0.015;
    vec4 best = vec4(0.0);
    for (int k = 0; k < 4; k++) {
        vec2 o = vec2((k == 1 || k == 3) ? side.x : 0.0, k >= 2 ? side.y : 0.0);
        vec2 id = base + o;
        vec2 f = fg - o;
        vec2 idp = mod(id, N) + seed;
        vec2 h = hash22(idp);
        if (hash12(idp + 4.1) < 0.55) continue;
        vec2 c = vec2(0.2 + 0.6 * h.x + 0.12 * sin(TAU * (uT + h.x)), 0.2 + 0.6 * h.y + 0.1 * cos(TAU * (uT + h.y)));
        vec2 d = f - c;
        if (dot(d, d) > 0.25) continue;
        float a = TAU * (uT * (1.0 + floor(h.x * 2.0)) + h.y);
        float ca = cos(a);
        float sa = sin(a);
        d = vec2(ca * d.x - sa * d.y, sa * d.x + ca * d.y);
        float r = clamp(size * (0.08 + 0.06 * h.y), 0.03, 0.32);
        float flip = cos(TAU * (uT * (1.0 + floor(h.y * 2.0)) + h.x));
        float thin = 0.25 + 0.75 * abs(flip);
        d.x /= r;
        d.y /= r * 0.55 * thin;
        float e = length(vec2(d.x, d.y * (1.0 + 0.35 * d.x)));
        float petal;
        vec3 pc;
        if (uFxReady > 0.5) {
            // v44.21 · pétalo pintado: muesca, venitas en abanico, rosa intenso en la base
            vec3 sp = fxSprite(4.0 + step(0.5, h.y), vec2(d.x, d.y * 0.55 * thin), log2(max(59.0 * aa / r, 1e-3)) + soft * 1.5);
            petal = sp.g;
            if (petal <= best.a) continue;
            pc = mix(vec3(0.97, 0.56, 0.72), vec3(1.0, 0.95, 0.97), clamp(sp.r, 0.0, 1.0));
            pc *= 1.0 - 0.28 * sp.b * (1.0 - soft);
        } else {
        float notch = (1.0 - smoothstep(0.0, 0.22, length(d - vec2(1.0, 0.0)))) * step(0.0, d.x);
        float w = aa / r;
        petal = (1.0 - smoothstep(1.0 - w - 0.25 * (1.0 - soft), 1.0 + w, e)) * (1.0 - notch);
        if (petal <= best.a) continue;
        pc = mix(vec3(0.98, 0.62, 0.76), vec3(1.0, 0.94, 0.97), clamp(0.5 + d.x * 0.45, 0.0, 1.0));
        pc *= 1.0 - 0.18 * smoothstep(0.55, 1.0, e) * (1.0 - soft);     // borde con volumen
        }
        pc *= flip > 0.0 ? 1.0 : 0.84;                                  // el revés más oscuro
        best = vec4(pc, petal);
    }
    return best;
}
vec4 sakuraLayer(vec2 P, float scale, float cyc, float size, float seed) {
    return sakuraLayerX(P, scale, cyc, size, seed, 0.0);
}

// ---------- Estrellas ----------
vec3 starsLayer(vec2 P, float dens, float cyc) {
    vec2 g = P * dens;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 h = hash22(id + 11.3);
    float present = step(0.62, hash12(id + 5.9));
    vec2 c = 0.2 + 0.6 * h;
    float d = length(f - c);
    float big = step(0.93, hash12(id + 2.2));
    float k = cyc * (1.0 + floor(h.x * 3.0));
    float tw = 0.45 + 0.55 * (0.5 + 0.5 * sin(TAU * (uT * k + h.y)));
    float core = falloff(d, 0.06 + 0.05 * big);
    float glow = falloff(d, 0.3) * 0.25;
    vec2 a = abs(f - c);
    float spikes = big * (falloff(a.x, 0.015) * falloff(a.y, 0.35) + falloff(a.y, 0.015) * falloff(a.x, 0.35));
    float s = (core + glow + spikes * 0.7) * tw * present;
    vec3 col = mix(vec3(0.8, 0.88, 1.0), vec3(1.0, 0.95, 0.8), h.x);
    return col * s;
}

// ---------- Luciérnagas ----------
vec3 fliesLayer(vec2 P, float dens, float cyc) {
    vec2 g = P * dens;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 h = hash22(id + 21.7);
    float present = step(0.55, hash12(id + 8.8));
    float kx = cyc * (1.0 + floor(h.x * 2.0));
    float ky = cyc * (1.0 + floor(h.y * 2.0));
    vec2 c = vec2(0.5 + 0.3 * sin(TAU * (uT * kx + h.y)), 0.5 + 0.3 * sin(TAU * (uT * ky + h.x) + 1.3));
    float d = length(f - c);
    float blink = smoothstep(-0.2, 1.0, sin(TAU * (uT * cyc * 2.0 + h.x)));
    float core = falloff(d, 0.035);
    float glow = falloff(d, 0.18);
    return vec3(0.85, 1.0, 0.45) * (core + glow * glow * 0.6) * blink * present;
}

// ---------- Niebla (fundido de dos fases = loop perfecto) ----------
float fogField(vec2 P, float scale, float cyc) {
    float p1 = fract(uT * cyc);
    float p2 = fract(p1 + 0.5);
    float w1 = 1.0 - abs(2.0 * p1 - 1.0);
    vec2 dir = vec2(0.9, 0.12);
    float a = fbm(P * scale + dir * (p1 - 0.5));
    float b = fbm(P * scale + dir * (p2 - 0.5));
    return mix(b, a, w1);
}

// ---------- Rayos de luz ----------
float raysField(vec2 P, float ox, float cyc) {
    vec2 o = uMotionRays.zw * vec2(uAspect, 1.0);
    vec2 v = P - o;
    float ang = atan(v.x, v.y) - atan(uMotionRays.y, uMotionRays.x);
    float ph = TAU * uT * cyc;
    float r = sin(ang * 17.0 + ph) + sin(ang * 29.0 - ph * 2.0 + 1.3) + sin(ang * 9.0 + ph + 2.1);
    r = clamp(r / 3.0 * 0.5 + 0.5, 0.0, 1.0);
    r = r * r * r;
    float fall = exp(-length(v) * 1.3);
    return r * fall;
}

// v44.17 · Rayos crepusculares REALES: dispersión de la luz en pantalla (GPU Gems 3, cap. 13).
// La luz nace de las zonas brillantes de la propia imagen (el sol, el cielo entre las nubes) y
// se corta donde hay nubes, montañas o el personaje: parecen filmados, no dibujados encima.
float godRaysField(vec2 uv, vec2 sun, float cyc) {
    const int N = 24;
    vec2 delta = (uv - sun) * (0.92 / float(N));
    vec2 p = uv - delta * hash12(gl_FragCoord.xy);   // sin escalones (ruido fijo por píxel)
    float decay = 1.0;
    float acc = 0.0;
    for (int i = 0; i < N; i++) {
        p -= delta;
        vec3 s = textureLod(uImage, clamp(p, vec2(0.0), vec2(1.0)), 3.0).rgb;
        float b = max(max(s.r, s.g), s.b) * 0.6 + luma(s) * 0.4;
        acc += smoothstep(0.74, 0.98, b) * decay;
        decay *= 0.955;
    }
    acc /= float(N) * 0.5;
    // Los haces «respiran» despacio; ciclos enteros: el bucle cierra sin salto.
    vec2 v = (uv - sun) * vec2(uAspect, 1.0);
    float ang = atan(v.y, v.x);
    float ph = TAU * uT * cyc;
    float breathe = 0.86 + 0.14 * sin(ang * 23.0 + ph) * sin(ang * 7.0 - ph + 1.7);
    return acc * breathe;
}

float energyField(vec2 P, float rays, float cyc) {
    vec2 q = P - uMotionEnergy.zw * vec2(uAspect, 1.0);
    float r = length(q);
    float a = atan(q.y, q.x);
    float pulse = 0.55 + 0.45 * sin(TAU * uT * cyc);
    float spokes = pow(0.5 + 0.5 * sin(a * rays + sin(uT * TAU * cyc) * 2.0), 10.0);
    return spokes * (1.0 - smoothstep(0.08, 0.58, r)) * pulse;
}

float speedField(vec2 P, float angle, float cyc) {
    float a = radians(angle);
    float ca = cos(a);
    float sa = sin(a);
    vec2 q = vec2(ca * P.x + sa * P.y, -sa * P.x + ca * P.y);
    vec2 g = q * vec2(9.0, 34.0);
    g.y -= uT * cyc * 8.0;
    float line = smoothstep(0.47, 0.49, fract(g.y));
    float fade = smoothstep(0.0, 0.18, q.y) * (1.0 - smoothstep(0.55, 1.0, q.y));
    // v40.1: patrón periódico en el desplazamiento del bucle (8·ciclos celdas): sin salto al repetir.
    vec2 id = floor(g);
    float per = 8.0 * max(1.0, floor(cyc + 0.5));
    return line * fade * (0.35 + 0.65 * hash12(vec2(id.x, mod(id.y + floor(id.x * 7.0), per))));
}

// Procedurales anime/manga: todos usan tiempo periódico para bucles limpios.
float particleDot(vec2 p, float density, float cycles, float size, float seed) {
    const float REP = 6.0;
    vec2 g = p * density;
    g.y -= uT * cycles * REP;
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 h = hash22(vec2(id.x, mod(id.y, REP)) + seed);
    vec2 c = vec2(0.2 + 0.6 * h.x, 0.2 + 0.6 * h.y);
    float r = clamp(size * 0.09, 0.018, 0.28);
    return (1.0 - smoothstep(r * 0.4, r, length(f - c))) * step(0.42, hash12(vec2(id.x, mod(id.y, REP)) + seed));
}
// v37: partículas con forma y comportamiento propios (no círculos):
// kind 0 brasas (estela alargada que parpadea), 1 hojas (giran y dan la vuelta),
// 2 polvo/motas (brillo suave), 3 ceniza (copos irregulares que se mecen).
// v44.20 · ya no viven encerradas en su «cajita»: se miran las 4 celdas más cercanas, así
// cada partícula puede estar en cualquier lugar, cruzar el borde y SOLAPARSE con otras.
// Devuelve (forma, brillo, sombreado 0..1.4, borde 0..1). [soft] = desenfoque (capa cercana).
vec4 particleFieldX(vec2 p, float density, float cycles, float size, float seed, float kind, float soft) {
    const float REP = 6.0;
    vec2 g = p * density;
    g.y -= uT * cycles * REP;
    vec2 base = floor(g);
    vec2 fg = fract(g);
    vec2 side = sign(fg - 0.5);
    float r = clamp(size * 0.09, 0.018, 0.30);
    float aa = fwidth(g.y) * (1.0 + soft * 2.5) + soft * 0.012;  // borde suave según tamaño en pantalla
    vec4 best = vec4(0.0, 0.0, 1.0, 0.0);
    for (int k = 0; k < 4; k++) {
        vec2 o = vec2((k == 1 || k == 3) ? side.x : 0.0, k >= 2 ? side.y : 0.0);
        vec2 id = base + o;
        vec2 f = fg - o;
        vec2 key = vec2(id.x, mod(id.y, REP)) + seed;
        vec2 h = hash22(key);
        if (hash12(key + 3.3) < 0.42) continue;
        float sway = sin(TAU * (uT * cycles + h.x)) * (kind > 0.5 ? 0.14 : 0.06);
        vec2 c = vec2(0.12 + 0.76 * h.x + sway, 0.12 + 0.76 * h.y);
        vec2 d = f - c;
        if (dot(d, d) > 0.25) continue;
        float shape;
        float glow = 0.0;
        float shade = 1.0;
        float edge = 0.0;
        if (kind < 0.5) {
            // brasa: alargada en la dirección del ascenso (estela), núcleo caliente y halo
            float e = length(vec2(d.x / (r * 0.55), d.y / (r * 1.9)));
            shape = 1.0 - smoothstep(0.4 - soft * 0.3, 1.0, e);
            glow = exp(-dot(d, d) / (r * r * 6.0)) * 0.5;
            float flick = 0.55 + 0.45 * sin(TAU * (uT * cycles * 3.0 + h.y * 7.0));
            shape *= flick; glow *= flick;
        } else if (kind < 1.5) {
            // hoja con punta: gira, «da la vuelta» (de canto = fina; el revés más claro),
            // nervio central y borde oscuro fino para que se lea sobre cualquier fondo.
            float ang = TAU * (uT * cycles * (1.0 + floor(h.y * 2.0)) + h.x);
            float flipC = cos(TAU * (uT * cycles + h.y));
            float flip = max(abs(flipC), 0.2 + 0.3 * soft);   // cerca: nunca una raya fina
            d = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * d;
            float len = r * (1.35 - 0.35 * soft);
            vec2 q = vec2(d.x / len, d.y / (len * flip));
            if (uFxReady > 0.5) {
                // v44.21 · hoja pintada con nervaduras y textura (6 formas: ovalada, arce,
                // alargada, dentada, lanceolada, sauce), con su nivel de detalle según tamaño.
                float pick = floor(hash12(key + 9.1) * 6.0);
                float cell = pick > 3.5 ? pick + 2.0 : pick;
                float lod = log2(max(59.0 * aa / (len * max(flip, 0.2)), 1e-3)) + soft * 1.5;
                vec3 sp = fxSprite(cell, q, lod);
                shape = sp.g;
                shade = (flipC > 0.0 ? 1.0 : 1.22) * (0.18 + 0.95 * sp.r) * (0.88 + 0.2 * sin(ang + 1.2));
                edge = clamp(sp.b * 1.8, 0.0, 1.0) * (1.0 - soft);
            } else {
            float hw = 0.40 * pow(max(1.0 - q.x * q.x, 0.0), 0.72);
            float dist = max(abs(q.y) - hw, abs(q.x) - 1.0) * flip * len;
            shape = 1.0 - smoothstep(-aa, aa, dist);
            float rib = (1.0 - smoothstep(0.0, 0.06, abs(q.y))) * step(abs(q.x), 0.9);
            shade = (flipC > 0.0 ? 1.0 : 1.22) * (0.80 + 0.30 * (0.5 - 0.5 * q.x)) * (0.86 + 0.22 * sin(ang + 1.2));
            shade *= 1.0 - 0.32 * rib;
            edge = smoothstep(-0.12 * r - aa, -aa * 0.5, dist) * (1.0 - soft);
            }
        } else if (kind < 2.5) {
            // mota de polvo luminosa
            shape = exp(-dot(d, d) / (r * r * (0.35 + soft * 1.5))) * (0.6 + 0.4 * sin(TAU * (uT * cycles * 2.0 + h.y)));
            glow = exp(-dot(d, d) / (r * r * 3.0)) * 0.35;
        } else {
            // ceniza: copo irregular
            float ang = atan(d.y, d.x);
            float rr = r * (0.75 + 0.35 * sin(ang * 3.0 + h.x * 6.0) + 0.15 * sin(ang * 5.0 + h.y * 9.0));
            shape = 1.0 - smoothstep(rr * (0.6 - soft * 0.5), rr, length(d));
            shade = 0.8 + 0.4 * h.y;
        }
        if (shape > best.x) { best.x = shape; best.z = shade; best.w = edge; }
        best.y = min(best.y + glow, 1.2);
    }
    return best;
}
vec2 particleField(vec2 p, float density, float cycles, float size, float seed, float kind) {
    return particleFieldX(p, density, cycles, size, seed, kind, 0.0).xy;
}
// v44.20 · color medio de la escena alrededor (mip alto): las partículas toman la luz del
// lugar (atardecer = cálidas, noche = frías) en vez de un color plano pegado encima.
vec3 sceneTint(vec2 uv) {
    vec3 a = textureLod(uImage, clamp(uv, vec2(0.0), vec2(1.0)), 6.0).rgb;
    float l = max(dot(a, vec3(0.2126, 0.7152, 0.0722)), 0.05);
    return mix(vec3(1.0), clamp(a / l, vec3(0.6), vec3(1.5)), 0.35) * mix(0.75, 1.05, smoothstep(0.05, 0.6, l));
}

// Rayos eléctricos ramificados que salen del origen y cambian de forma a saltos
// (como la animación limitada del anime). Fases enteras: el bucle cierra exacto.
float electricArc(vec2 p, vec4 motion, float branches, float cycles) {
    vec2 q = p - motion.zw * vec2(uAspect, 1.0);
    float r = length(q);
    float a = atan(q.y, q.x);
    float steps = max(6.0, cycles * 10.0);
    float k = floor(uT * steps);
    float count = clamp(floor(branches / 4.0), 2.0, 7.0);
    float best = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        if (fi >= count) break;
        float base = TAU * (fi + hash12(vec2(fi, k)) * 0.6) / count;
        float zig = (vnoise(vec2(r * 18.0, k * 3.1 + fi * 11.0)) - 0.5) * 0.9 +
                    (vnoise(vec2(r * 55.0, k * 1.7 + fi * 5.0)) - 0.5) * 0.3;
        float da = a - base - zig;
        da = mod(da + 3.14159, TAU) - 3.14159;
        float dist = abs(da) * r;
        float len = 0.18 + 0.32 * hash12(vec2(fi, k + 7.0));
        float along = 1.0 - smoothstep(len * 0.7, len, r);
        float core = 1.0 - smoothstep(0.0015, 0.005, dist);
        float halo = exp(-dist * 90.0) * 0.45;
        best = max(best, (core + halo) * along);
    }
    return best * smoothstep(0.0, 0.02, r);
}
float focusLines(vec2 p, vec4 motion, float density, float cycles) {
    vec2 q = p - motion.zw * vec2(uAspect, 1.0);
    float a = atan(q.y, q.x);
    float r = length(q);
    float index = floor(a / TAU * density);
    float phase = fract(a / TAU * density);
    float spike = (1.0 - smoothstep(0.03, 0.14, min(phase, 1.0-phase)));
    float endR = 0.16 + 0.25 * hash12(vec2(index, 27.1));
    return spike * smoothstep(endR, endR + 0.12, r) *
        (0.7 + 0.3 * sin(TAU * uT * cycles));
}


#ifdef FX_SKY
vec3 studioSky(vec2 uv) {
    float ph=TAU*uT*uStudioSky.y;
    vec2 drift=vec2(cos(ph),sin(ph))*0.28;
    float clouds=smoothstep(0.45,0.78,fbm(uv*vec2(4.0*uAspect,6.0)+drift));
    vec3 top=vec3(0.13,0.44,0.83), bottom=vec3(0.79,0.9,1.0);
    if(uStudioSky.z>0.5){top=vec3(0.32,0.22,0.61);bottom=vec3(1.0,0.59,0.38);}
    if(uStudioSky.z>1.5){top=vec3(0.025,0.035,0.13);bottom=vec3(0.16,0.19,0.37);}
    if(uStudioSky.z>2.5){top=vec3(0.1,0.12,0.2);bottom=vec3(0.4,0.44,0.52);}
    vec3 c=mix(top,bottom,smoothstep(0.0,1.0,uv.y));
    c=mix(c,uStudioSky.z>1.5?bottom:vec3(1.0,0.93,0.92),clouds*0.8);
    if(uStudioSky.z>1.5 && uStudioSky.z<2.5){
        vec2 grid=uv*vec2(uAspect,1.0)*90.0;
        vec2 h=hash22(floor(grid));
        float star=(1.0-smoothstep(0.01,0.09,length(fract(grid)-h)))*step(0.96,h.x);
        c+=star*(0.6+0.4*sin(ph+h.y*TAU));
    }
    if(uStudioSky.z>2.5)c+=pow(max(0.0,sin(ph)),40.0)*0.25;
    return c;
}
#endif
#ifdef FX_DISPERSION
vec3 studioDisperse(vec3 base, vec2 uv) {
    float strength=uStudioDispersion.x;
    float phase=0.5-0.5*cos(TAU*uT*uStudioDispersion.y);
    if(strength<0.001 || phase<0.0001) return base;
    float count=max(12.0,uDispersionMore.x);
    vec2 scale=vec2(uAspect,1.0)*count;
    vec2 g=uv*scale;
    vec2 cell=floor(g);
    vec2 direction=vec2(cos(uStudioDispersion.w),sin(uStudioDispersion.w));
    float distance=phase*uDispersionMore.y*1.6;
    float zone=maskValue(uv,uMaskDispersion,0.0,1.0);
    // Local color extrapolation, deliberately no claim of semantic/AI reconstruction.
    vec3 fill=vec3(0.0);float weight=0.0;
    for(int k=0;k<8;k++){
        float a=float(k)*TAU/8.0;
        vec2 q=uv+vec2(cos(a),sin(a))/scale*3.0;
        float w=1.0-maskValue(clamp(q,0.0,1.0),uMaskDispersion,0.0,1.0);
        fill+=sampleImg(q)*w;weight+=w;
    }
    fill=weight>0.01?fill/weight:sampleImg(uv+direction/scale*3.0);
    vec3 c=mix(base,fill,zone*phase*strength);
    // Search around the reverse displacement: only nine candidates per pixel.
    vec2 sourceCell=floor(g-direction*distance);
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec2 id=sourceCell+vec2(float(x),float(y));
        vec2 h=hash22(id+31.2);
        vec2 center=id+0.5+direction*distance+(h-0.5)*phase*0.5;
        vec2 local=g-center;
        float radius=mix(0.49,0.19,phase*strength);
        float shape=1.0-smoothstep(radius-0.04,radius,max(abs(local.x),abs(local.y)));
        if(uStudioDispersion.z>0.5)shape=1.0-smoothstep(radius-0.05,radius,length(local*vec2(1.0,1.5)));
        if(uStudioDispersion.z>1.5)shape=1.0-smoothstep(0.03,0.11,length(local));
        vec2 origin=(id+0.5)/scale;
        float valid=step(0.0,origin.x)*step(origin.x,1.0)*step(0.0,origin.y)*step(origin.y,1.0);
        float area=maskValue(clamp(origin,0.0,1.0),uMaskDispersion,0.0,1.0);
        c=mix(c,sampleImg(origin+local/scale),shape*area*valid*phase*strength);
    }
    return c;
}
#endif
vec3 studioElements(vec3 col,vec2 uv) {
    for(int i=0;i<4;i++) {
        vec4 style=uElementStyle[i]; if(style.x<0.001)continue;
        vec4 pose=uElementPose[i];
        vec2 q=(uv-pose.xy)*vec2(uAspect,1.0)/pose.z;
        q=mat2(cos(pose.w),-sin(pose.w),sin(pose.w),cos(pose.w))*q;
        if(max(abs(q.x),abs(q.y))>0.8)continue;
        float phase=TAU*uT*style.z;
        float opacity=0.0;vec3 tint=vec3(0.92,0.95,1.0);
        if(style.y<1.5 || style.y>4.5){
            float n=fbm(q*5.0+vec2(sin(phase),cos(phase))*0.35);
            float edge=1.0-smoothstep(0.15,0.58,length(q*vec2(0.8,1.3)));
            opacity=smoothstep(0.3,0.7,n)*edge;
            if(style.y>0.5 && style.y<1.5)tint=vec3(0.42,0.45,0.52);
            if(style.y>4.5)opacity*=0.55;
        } else if(style.y<3.5) {
            for(int j=0;j<8;j++){
                vec2 h=hash22(vec2(float(j),float(i)+41.0));
                vec2 center=(h-0.5)*0.8+vec2(sin(phase+h.x*TAU),cos(phase+h.y*TAU))*0.1;
                vec2 p=q-center;
                if(style.y<2.5){ opacity+=1.0-smoothstep(0.02,0.065,length(p*vec2(0.7,1.4)));tint=vec3(1.0,0.66,0.81); }
                else {opacity+=(1.0-smoothstep(0.0,0.08,abs(p.x)+abs(p.y)))*(0.6+0.4*sin(phase+h.x*TAU));tint=vec3(1.0,0.94,0.65);}
            }
        } else {
            float r=length(q);float ring=0.29+0.05*sin(phase);
            opacity=exp(-abs(r-ring)*65.0)*(0.8+0.2*sin(atan(q.y,q.x)*3.0+phase));
            tint=vec3(0.48,0.74,1.0);
        }
        col=mix(col,tint,clamp(opacity*style.x,0.0,1.0));
    }
    return col;
}


// ================= v16: funciones de escenas =================
// Ventana de un evento que empieza en 'start' y dura 'dur' (fracción del bucle).
float windowEnv(float start, float dur) {
    if (dur >= 0.999) return 1.0;
    float p = fract(uT - start);
    float a = smoothstep(0.0, min(0.08, dur * 0.3), p);
    float b = 1.0 - smoothstep(dur * 0.6, dur, p);
    return a * b;
}

float impactEnv() {
    // uLoopInfo.z >= 0: tiempo local del bloque de la nueva línea de tiempo.
    if (uLoopInfo.z >= 0.0) {
        float tSec = uLoopInfo.z;
        return exp(-tSec * 6.0) * smoothstep(0.0, 0.02, tSec);
    }
    float n = max(1.0, uImpact.w);
    float p = fract(uT * n - uImpact.z);
    float tSec = p * uLoopInfo.x / n;
    return exp(-tSec * 6.0) * smoothstep(0.0, 0.02, tSec);
}

float boltPath(float y, float seed) {
    return (vnoise(vec2(y * 7.0, seed)) - 0.5) * 0.16 + (vnoise(vec2(y * 31.0, seed + 3.3)) - 0.5) * 0.05;
}

#ifdef FX_BOLT
vec3 boltLayer(vec3 col, vec2 P, float mask) {
    float n = max(1.0, uBolt.z);
    float k;
    float tSec;
    float seed;
    // uLoopInfo.y >= 0: el rayo empieza exactamente al inicio del bloque elegido.
    if (uLoopInfo.y >= 0.0) {
        k = uLoopInfo.w;
        tSec = uLoopInfo.y;
        seed = mod(k, 997.0) * 0.173 + 3.0;
    } else {
        k = floor(uT * n - 0.3);
        float p = fract(uT * n - 0.3);
        tSec = p * uLoopInfo.x / n;
        seed = mod(k + n, n) * 17.0 + 3.0;
    }
    float vis = (1.0 - smoothstep(0.05, 0.35, tSec)) * (0.55 + 0.45 * step(0.5, fract(tSec * 18.0)));
    if (vis <= 0.001) return col;
    float x0 = uBolt.y * uAspect + (hash12(vec2(seed, 1.0)) - 0.5) * 0.15;
    float len = 0.55 + 0.35 * hash12(vec2(seed, 2.0));
    float grow = clamp(tSec / 0.06, 0.0, 1.0);
    float ymax = len * grow;
    float dm = abs(P.x - (x0 + boltPath(P.y, seed)));
    float along = 1.0 - step(ymax, P.y);
    float core = (1.0 - smoothstep(0.0015, 0.004, dm)) * along;
    float glow = exp(-dm * 55.0) * along * 0.6;
    float by0 = len * (0.25 + 0.3 * hash12(vec2(seed, 4.0)));
    float bdir = hash12(vec2(seed, 5.0)) > 0.5 ? 1.0 : -1.0;
    float bx = x0 + boltPath(by0, seed) + bdir * (P.y - by0) * 0.45 + boltPath(P.y, seed + 9.0) * 0.6;
    float bd = abs(P.x - bx);
    float balong = step(by0, P.y) * (1.0 - step(min(ymax, by0 + len * 0.35), P.y));
    core += (1.0 - smoothstep(0.001, 0.003, bd)) * balong * 0.8;
    glow += exp(-bd * 70.0) * balong * 0.35;
    vec3 bc = uMono > 0.5 ? vec3(1.0) : vec3(0.85, 0.9, 1.0);
    col = mix(col, bc, clamp((core + glow) * vis * uBolt.x * mask, 0.0, 1.0));
    float flash = (1.0 - smoothstep(0.0, 0.18, tSec)) * uBolt.w;
    col += vec3(0.75, 0.8, 1.0) * flash * 0.55 * uBolt.x;
    return col;
}
#endif

#ifdef FX_GUST
// v44.19 · Hoja con forma real (punta en los dos extremos), en unidades de su largo r:
// < 0 dentro. x va a lo largo de la hoja.
float gustLeafDist(vec2 d) {
    float x = d.x;
    float hw = 0.40 * pow(max(1.0 - x * x, 0.0), 0.72);
    return max(abs(d.y) - hw, abs(x) - 1.0);
}
vec3 gustLayer(vec3 col, vec2 P, float env, float mask) {
    if (env <= 0.001) return col;
    vec2 dir = normalize(uMotionGust.xy + vec2(1e-5, 0.0));
    vec2 nrm = vec2(-dir.y, dir.x);
    float a = dot(P, dir);
    float c = dot(P, nrm);
    // v44.19 · Líneas de viento: trazos que se afinan en las puntas. Sobre fondo claro son un
    // velo gris azulado (antes eran blancas y desaparecían en fondos blancos).
    float lines = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float rows = 5.0 + fk * 4.0;
        float cc = c * rows + 0.35 * sin(a * (2.2 + fk) + fk * 1.7 + TAU * uT);
        float row = floor(cc);
        float fr = fract(cc) - 0.5;
        float h = hash12(vec2(row, fk * 13.1));
        float speed = uGust.w * (2.0 + fk);
        float along = fract(a * 0.55 + h - uT * speed);
        float len = 0.25 + 0.2 * h;
        float seg = smoothstep(0.0, 0.08, along) * (1.0 - smoothstep(len, len + 0.2, along));
        float taper = sin(clamp(along / (len + 0.2), 0.0, 1.0) * 3.14159265);   // fino en las puntas
        float width = (0.012 + 0.028 * h) * (0.35 + 0.65 * taper);
        lines += (1.0 - smoothstep(width * 0.35, width, abs(fr))) * seg * step(0.45, h) * (0.9 - fk * 0.2);
    }
    float bl = luma(col);
    vec3 airLight = uMono > 0.5 ? vec3(1.0) : vec3(0.93, 0.97, 1.0);
    vec3 airDark = uMono > 0.5 ? col * 0.72 : col * vec3(0.70, 0.78, 0.88);
    vec3 wc = mix(airLight, airDark, smoothstep(0.55, 0.82, bl));
    col = mix(col, wc, clamp(lines * 0.7, 0.0, 1.0) * env * mask);
    if (uGustMore.x > 0.0) {
        // v44.19 · Hojas que vuelan: dos capas de profundidad (cerca grandes, suaves y rápidas;
        // lejos pequeñas y nítidas), forma de hoja con punta, giro 3D (se ponen de canto y
        // muestran el revés más claro), nervio central, borde oscuro fino y sombra suave
        // para que se lean sobre cualquier fondo. Todo con ciclos enteros: el bucle cierra.
        for (int L = 1; L >= 0; L--) {
            float near = float(L == 0);
            float scale = mix(7.5, 4.2, near);
            float REP = mix(6.0, 4.0, near);
            vec2 q = vec2(a, c) * scale;
            q.x -= uT * uGust.w * REP * mix(1.0, 2.0, near);
            vec2 id = floor(q);
            vec2 f = fract(q);
            vec2 idp = vec2(mod(id.x, REP), id.y) + 5.3 + near * 17.0;
            vec2 h = hash22(idp);
            float present = step(mix(0.55, 0.72, near), hash12(idp + 2.1));
            if (present < 0.5) continue;
            // centro y tamaño acotados: la hoja (y su sombra) nunca sale de su celda
            float bob = 0.10 * sin(TAU * (uT * floor(1.0 + 2.0 * h.x) + h.y));
            vec2 cpos = vec2(0.5 + 0.08 * sin(TAU * (uT + h.y)), 0.4 + 0.2 * h.y + bob);
            float r = mix(0.19, 0.24, near) * (0.8 + 0.3 * hash12(idp + 7.7));
            vec2 d0 = (f - cpos) / r;
            float spin = TAU * (uT * floor(1.0 + 2.0 * h.y) * (h.x > 0.5 ? 1.0 : -1.0) + h.x);
            float flipA = TAU * (uT * floor(1.0 + 1.5 * h.x) + h.y * 0.7);
            float flip = cos(flipA);
            mat2 R = mat2(cos(spin), -sin(spin), sin(spin), cos(spin));
            vec2 d = R * d0;
            d.y /= max(abs(flip), 0.18);                   // de canto = más fina
            float px = fwidth(q.x) / r * (1.0 + 2.5 * near);  // antialias (más suave cerca)
            float dist = gustLeafDist(d) * max(abs(flip), 0.18);
            float inside = 1.0 - smoothstep(-px, px, dist);
            // sombra: misma hoja desplazada hacia abajo-derecha, solo oscurece
            vec2 ds = R * (d0 - vec2(0.18, -0.22));
            ds.y /= max(abs(flip), 0.18);
            float shadow = (1.0 - smoothstep(-px * 3.0, px * 3.0, gustLeafDist(ds) * max(abs(flip), 0.18))) * (1.0 - inside);
            // color: verde, amarillo seco u otoño; el revés más claro y apagado
            float pick = hash12(idp + 4.4);
            vec3 base = pick < 0.4 ? vec3(0.30, 0.58, 0.22) : (pick < 0.75 ? vec3(0.78, 0.60, 0.25) : vec3(0.80, 0.36, 0.14));
            vec3 back = mix(base, vec3(0.85, 0.88, 0.70), 0.35);
            vec3 lc = flip > 0.0 ? base : back;
            lc *= 0.78 + 0.36 * (0.5 - 0.5 * d.x);                  // degradado a lo largo
            lc *= 0.85 + 0.25 * (0.5 + 0.5 * sin(spin + 1.2));      // luz que cambia al girar
            float rib = (1.0 - smoothstep(0.0, 0.05, abs(d.y))) * step(abs(d.x), 0.92);
            lc = mix(lc, lc * 0.62, rib * 0.7);
            float edge = smoothstep(-0.10 - px, -px * 0.5, dist);   // borde oscuro fino
            lc = mix(lc, lc * 0.45, edge * 0.8 * (1.0 - near * 0.6));
            if (uMono > 0.5) lc = vec3(luma(lc));
            float k = env * uGustMore.x * mask * mix(0.95, 0.85, near);
            col = mix(col, col * 0.72, shadow * 0.35 * k * smoothstep(0.25, 0.75, bl));
            col = mix(col, lc, inside * k);
        }
    }
    return col;
}
#endif

#ifdef FX_TORNADO
vec3 tornadoLayer(vec3 col, vec2 P, float mask) {
    vec2 c = uMotionTornado.zw * vec2(uAspect, 1.0);
    vec2 rel = P - c;
    float R = max(0.05, uTornado.y);
    if (uTornado.w < 0.5) {
        float r = length(rel);
        float ang = atan(rel.y, rel.x);
        float rn = r / R;
        float rot = ang + TAU * uT * uTornado.z * 2.0 + 2.5 * log(max(rn, 0.05));
        float arms = pow(0.5 + 0.5 * sin(rot * 3.0), 6.0);
        float band = 1.0 - smoothstep(0.3, 1.1, rn);
        float ripple = 0.7 + 0.3 * sin(rn * 20.0 - TAU * uT * uTornado.z * 3.0);
        vec3 dc = uMono > 0.5 ? vec3(0.9) : vec3(0.88, 0.83, 0.74);
        col = mix(col, dc, clamp(arms * band * ripple * 0.45 * uTornado.x * mask, 0.0, 1.0));
    } else {
        float y = c.y - P.y;
        float hN = clamp(y / (R * 2.2), 0.0, 1.0);
        float w = mix(0.03, R * 0.9, pow(hN, 1.6));
        float x = (P.x - c.x - 0.04 * sin(hN * 6.0 + TAU * uT * uTornado.z)) / w;
        float inside = (1.0 - smoothstep(0.7, 1.05, abs(x))) * step(0.0, y) * (1.0 - smoothstep(0.9, 1.0, hN));
        float a = asin(clamp(x, -1.0, 1.0));
        float stripes = 0.5 + 0.5 * sin(a * 5.0 + y * 28.0 - TAU * uT * uTornado.z * 3.0);
        float shade = 0.55 + 0.45 * (1.0 - abs(clamp(x, -1.0, 1.0)));
        vec3 tc = mix(vec3(0.35, 0.36, 0.40), vec3(0.74, 0.74, 0.77), stripes * shade);
        if (uMono > 0.5) tc = vec3(luma(tc));
        col = mix(col, tc, clamp(inside * (0.55 + 0.35 * stripes) * uTornado.x * mask, 0.0, 1.0));
        float base = 1.0 - smoothstep(0.0, R * 0.6, length(rel * vec2(0.6, 1.8)));
        float churn = 0.7 + 0.3 * sin(TAU * uT * uTornado.z * 4.0 + P.x * 30.0);
        col = mix(col, vec3(0.55, 0.5, 0.45), clamp(base * 0.5 * uTornado.x * mask * churn, 0.0, 1.0));
    }
    return col;
}
#endif

#ifdef FX_CLOUDS3
vec3 clouds3Layer(vec3 col, vec2 P, float mask) {
    vec3 lit = vec3(1.0);
    vec3 shadow = vec3(0.72, 0.78, 0.9);
    if (uClouds3.w > 0.5 && uClouds3.w < 1.5) { lit = vec3(1.0, 0.82, 0.62); shadow = vec3(0.62, 0.42, 0.52); }
    if (uClouds3.w > 1.5) { lit = vec3(0.55, 0.6, 0.78); shadow = vec3(0.16, 0.18, 0.3); }
    if (uMono > 0.5) { lit = vec3(luma(lit)); shadow = vec3(luma(shadow)); }
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float scl = uClouds3.y * (2.2 - fk * 0.6);
        float cyc = uClouds3.z * (1.0 + fk);
        float p1 = fract(uT * cyc);
        float p2 = fract(p1 + 0.5);
        float w1 = 1.0 - abs(2.0 * p1 - 1.0);
        vec2 q = P * scl + vec2(fk * 7.3, fk * 3.1);
        float na = fbm(q - vec2(0.9 * (p1 - 0.5), 0.0));
        float nb = fbm(q - vec2(0.9 * (p2 - 0.5), 0.0));
        float n = mix(nb, na, w1);
        float dens = smoothstep(0.5 - fk * 0.03, 0.62, n);
        float light = clamp(0.35 + (n - 0.5) * 3.0, 0.0, 1.0);
        // v38: luz desde arriba usando la pendiente de la nube: bordes iluminados y base en sombra.
        vec2 dP = vec2(dFdx(P.x), dFdy(P.y));
        dP = sign(dP) * max(abs(dP), vec2(1e-5));
        vec2 grad = vec2(dFdx(n), dFdy(n)) / dP / max(scl, 0.2);
        float shade = clamp(0.55 + dot(grad, vec2(0.25, 0.9)) * 0.35, 0.0, 1.0);
        light = clamp(mix(light, shade, 0.55) + (1.0 - dens) * 0.25, 0.0, 1.0);
        col = mix(col, mix(shadow, lit, light), dens * uClouds3.x * mask * (0.55 + fk * 0.2));
    }
    return col;
}
#endif

#ifdef FX_SHORE
vec3 shoreLayer(vec3 col, vec2 P, float mask) {
    vec2 dir = normalize(uMotionShore.xy + vec2(1e-5, 0.0));
    float sAlong = dot(P, dir);
    float across = dot(P, vec2(-dir.y, dir.x));
    float style = clamp(uShoreMore.y, 0.0, 3.0);
    float phase = sAlong * uShore.y - uT * uShore.z;
    float edgeNoise = (fbm(vec2(across * (7.0 + style * 1.3), sAlong * 2.7)) - 0.5) * (0.18 + style * 0.035);
    float f = fract(phase + edgeNoise);
    float frontWidth = mix(0.045, 0.075, style / 3.0);
    float front = 1.0 - smoothstep(0.0, frontWidth, f);
    float lace = smoothstep(0.58, 0.80, vnoise(vec2(across * 34.0, f * 9.0 + style)));
    float trail = (1.0 - smoothstep(frontWidth, 0.28 + style * 0.025, f)) * lace;
    float foam = clamp(front * 0.72 + trail * 0.50, 0.0, 1.0) * clamp(uShoreMore.x, 0.0, 1.0);
    float wet = (1.0 - smoothstep(0.0, 0.52, f)) * 0.055;
    col *= 1.0 - wet * mask * min(uShore.x, 1.0);

    // Natural por defecto: aclara el color original en vez de pegar una franja blanca opaca.
    float tint = uShore.w;
    vec3 foamColor = mix(col, vec3(1.0), 0.58);
    if (tint > 0.5 && tint < 1.5) foamColor = mix(col, vec3(0.58, 0.96, 0.93), 0.62);
    else if (tint < 2.5 && tint > 1.5) foamColor = mix(col, vec3(0.48, 0.76, 1.0), 0.62);
    else if (tint < 3.5 && tint > 2.5) foamColor = mix(col, vec3(1.0, 0.70, 0.54), 0.60);
    else if (tint > 3.5) foamColor = mix(col, vec3(0.42, 0.55, 0.82), 0.58);
    float foamMix = clamp(foam * uShore.x * mask * (0.48 + style * 0.05), 0.0, 0.82);
    col = mix(col, foamColor, foamMix);
    return col;
}
#endif

#ifdef FX_SUNPATH
vec3 sunpathLayer(vec3 col, vec2 P, float mask) {
    float cx = uSunpath.y * uAspect;
    float dx = abs(P.x - cx);
    float w = uSunpath.z * (0.4 + 0.8 * P.y);
    float style = floor(uLightStyles.y + 0.5);
    float column = 1.0 - smoothstep(w * 0.3, w * (style > 2.5 ? 0.65 : 1.0), dx);
    vec2 g = P * (style > 2.5 ? vec2(90.0, 190.0) : vec2(55.0, 140.0));
    vec2 id = floor(g);
    vec2 f = fract(g);
    vec2 h = hash22(id + 9.1);
    float tw = pow(0.5 + 0.5 * sin(TAU * (uT * uSunpath.w * (1.0 + floor(h.x * 3.0)) + h.y)), 6.0);
    float spark = (1.0 - smoothstep(0.0, 0.35, length((f - 0.5) * vec2(1.0, 2.5)))) * tw * step(0.35, h.y);
    vec3 scol = vec3(1.0, 0.95, 0.8);
    if (style > 0.5 && style < 1.5) scol = vec3(1.0, 0.60, 0.31); // atardecer
    else if (style > 1.5 && style < 2.5) scol = vec3(0.54, 0.73, 1.0); // luz de luna
    else if (style > 2.5) scol = vec3(0.82, 0.93, 1.0); // destellos finos
    if (uMono > 0.5) scol = vec3(1.0);
    float glimmer = style > 2.5 ? 2.0 : 1.0;
    col += scol * (spark * column * 1.4 * glimmer + column * 0.12) * uSunpath.x * mask;
    return col;
}
#endif

#ifdef FX_APPEAR
vec3 appearLayer(vec3 col, vec2 P) {
    float prog = clamp((uT - uAppear.y) / uAppear.z, 0.0, 1.0);
    float exitK = clamp((1.0 - uT) / (uAppear.z * 0.7), 0.0, 1.0);
    float r = min(prog, exitK);
    if (r <= 0.0) return col;
    vec2 pos = uAppearPose.xy * vec2(uAspect, 1.0);
    float hsz = max(0.02, uAppearPose.z);
    vec2 q = P - pos;
    float ca = cos(-uAppearPose.w);
    float sa = sin(-uAppearPose.w);
    q = vec2(ca * q.x - sa * q.y, sa * q.x + ca * q.y);
    float type = uAppear.w;
    vec2 offset = vec2(0.0);
    float scl = 1.0;
    if (type > 0.5 && type < 1.5) offset = vec2(-(1.0 - r) * (1.0 - r) * 0.35, 0.0);
    if (type > 1.5 && type < 2.5) scl = 1.0 + (1.0 - r) * 0.25;
    q = (q - offset) / scl;
    vec2 tuv = vec2(q.x / (hsz * max(uCharAspect, 0.05)) + 0.5, q.y / hsz + 0.5);
    float inside = step(0.0, tuv.x) * step(tuv.x, 1.0) * step(0.0, tuv.y) * step(tuv.y, 1.0);
    vec4 ch = textureLod(uCharacter, clamp(tuv, vec2(0.0), vec2(1.0)), 0.0) * inside;
    float reveal = r;
    if (type < 0.5) {
        float nz = fbm(tuv * 6.0 + 3.1);
        reveal = smoothstep(nz - 0.15, nz + 0.05, r * 1.2 - 0.1);
    }
    vec3 rgb = ch.a > 0.001 ? ch.rgb / ch.a : vec3(0.0);
    col = mix(col, rgb, clamp(ch.a * reveal * uAppear.x, 0.0, 1.0));
    float tr = r * (1.0 - r) * 4.0;
    if (type < 0.5) {
        vec2 sq = (P - pos) / hsz;
        float dist = length(sq * vec2(1.2, 0.8));
        float cloud = fbm(sq * 3.0 + vec2(0.0, -r * 2.0));
        float puff = (1.0 - smoothstep(0.35, 0.85, dist)) * smoothstep(0.35, 0.6, cloud);
        col = mix(col, vec3(0.92, 0.93, 0.96), clamp(puff * tr * uAppear.x, 0.0, 1.0));
    } else if (type < 1.5) {
        float band = 1.0 - smoothstep(0.0, hsz * 0.7, abs(P.y - pos.y));
        float rowId = floor(P.y * 90.0);
        float streak = step(0.6, hash12(vec2(rowId, 3.0))) *
            smoothstep(0.3, 0.9, fract(P.x * 3.0 - r * 4.0 + hash12(vec2(rowId, 7.0))));
        col = mix(col, vec3(0.95), clamp(streak * band * tr * 0.7 * uAppear.x, 0.0, 1.0));
    } else if (type < 2.5) {
        col = mix(col, vec3(1.0), clamp(tr * 0.85 * uAppear.x, 0.0, 1.0));
    }
    return col;
}
#endif


// v36: capas procedurales reutilizables en previsualización y exportación.
// Cada "forma" cambia el generador, el espacio y el movimiento, no solo el color.

// ---------------- v40: utilidades volumétricas (humo, fuego, explosión) ----------------
// Volutas "billow": |ruido| da bultos redondeados con pliegues, como el humo denso.
float billow(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int k = 0; k < 4; k++) {
        float n = vnoise(p) * 2.0 - 1.0;
        v += a * (sqrt(n * n + 0.012) - 0.1095);   // abs(n) suavizado: pliegues sin líneas duras
        p = OCT * p + vec2(11.3, 4.7);
        a *= 0.5;
    }
    return v / 0.9375;
}

// fbm con deformación de dominio: remolinos orgánicos en vez de manchas redondas.
float warpFbm(vec2 p, float amt) {
    vec2 w = vec2(fbm3(p * 0.8 + vec2(1.7, 9.2)), fbm3(p * 0.8 + vec2(8.3, 2.8))) - 0.5;
    return fbm(p + w * amt);
}

// Rampa de temperatura (cuerpo negro estilizado): brasa oscura -> color -> amarillo -> blanco.
// Con un color propio (azul, violeta…) la zona caliente tiende al blanco del mismo tono.
vec3 fireRamp(float t, vec3 hue) {
    t = clamp(t, 0.0, 1.0);
    bool warm = hue.r >= hue.b;
    vec3 c0 = hue * hue * 0.30 + vec3(0.02, 0.0, 0.0);
    vec3 c2 = mix(hue, warm ? vec3(1.0, 0.86, 0.42) : vec3(0.85, 0.95, 1.0), 0.62);
    vec3 c3 = warm ? vec3(1.0, 0.97, 0.88) : vec3(0.95, 0.98, 1.0);
    vec3 c = mix(c0, hue, smoothstep(0.0, 0.38, t));
    c = mix(c, c2, smoothstep(0.34, 0.72, t));
    c = mix(c, c3, smoothstep(0.70, 1.0, t));
    return c;
}

// Color de humo iluminado: cara a la luz más clara, parte inferior y núcleo más oscuros.
vec3 smokeShade(vec3 tint, float shade, float dens) {
    float bright = luma(tint);
    vec3 shadowC = tint * mix(0.50, 0.76, bright);
    vec3 litC = mix(tint, vec3(1.0), mix(0.50, 0.20, bright));
    vec3 c = mix(shadowC, litC, shade);
    return c * mix(1.0, mix(0.82, 0.95, bright), clamp(dens, 0.0, 1.0));
}

// ---------------- Humo ----------------
// Densidad del humo en coordenadas que viajan con él. Cada forma cambia la estructura,
// no solo el color: volutas, hilos, tinta manga o humo rastrero.
#ifdef FX_SMOKE
float smokeDensity(vec2 q, float shape, float turb) {
    if (shape < 0.5) {                       // Ondulante: volutas suaves con remolinos
        vec2 w = vec2(fbm3(q * 0.55 + vec2(1.7, 9.2)), fbm3(q * 0.55 + vec2(8.3, 2.8))) - 0.5;
        float a = fbm(q * 0.85 + w * (1.2 + 2.0 * turb));
        float b = fbm3(q * 1.9 + w * 1.5 + vec2(5.1, 3.3));
        return clamp((a * 0.8 + b * 0.3 - 0.30) / 0.52, 0.0, 1.0);
    } else if (shape < 1.5) {                // Hilos: filamentos estirados que se enroscan
        vec2 w = vec2(fbm3(q * 0.6 + 5.0), fbm3(q * 0.6 + 2.3)) - 0.5;
        vec2 s = q + w * (1.4 + 2.2 * turb);
        float r = 1.0 - abs(2.0 * fbm3(vec2(s.x * 2.2, s.y * 0.55)) - 1.0);
        float body = fbm3(q * 0.5 + 13.0);
        return r * r * (0.55 + 0.75 * body);
    } else if (shape < 2.5) {                // Tinta manga: masas compactas con borde
        float c = 1.0 - worley(q * 0.85 + vec2(vnoise(q * 0.5) * turb * 2.0));
        return clamp(c * 0.72 + warpFbm(q * 1.2, 1.0) * 0.42 - 0.06, 0.0, 1.0);
    }
    // Rastrero: aplanado, ondula pegado al suelo.
    vec2 r = vec2(q.x * 0.6, q.y * 1.7);
    return warpFbm(r, 1.0 + 2.0 * turb) * 1.08 + 0.04;
}
#endif

#ifdef FX_SMOKE
vec3 zetaSmoke(vec3 col, vec2 P, vec2 uv, float zone) {
    float m = maskValue(uv, uMaskSmoke, zone, uFxSmoke.w);
    if (m <= 0.001) return col;
    float cyc = max(1.0, uFxSmoke.z);
    float shape = floor(uSmokeShape.x + 0.5);
    float turb = clamp(uSmokeShape.y, 0.0, 1.0);
    float soft = clamp(uSmokeShape.z, 0.0, 1.0);
    vec2 origin = uMotionSmoke.zw * vec2(uAspect, 1.0);
    vec2 wind = normalize(uMotionSmoke.xy + vec2(0.001, 0.003));
    vec2 side = vec2(-wind.y, wind.x);
    float progress = fract(uT * cyc);
    float ph = TAU * uT * cyc;
    float density = clamp(uSmokeControl.y, 0.2, 2.0);
    float sizeK = clamp(max(0.5, uFxSmoke.y) / 3.0, 0.2, 3.0);
    // Luz desde arriba a la izquierda (en coordenadas de la imagen).
    vec2 lightDir = normalize(vec2(-0.45, -0.9));
    float dens, shade, presence = 1.0;
    float thr = mix(0.62, 0.38, clamp((density - 0.2) / 1.8, 0.0, 1.0));
    float edge = mix(0.05, 0.22, soft);
    if (uSmokeShape.w < 0.5 && shape > 0.5 && shape < 1.5) {
        // Hilos: filamentos que nacen en el origen y se estiran con el viento.
        vec2 rel = P - origin;
        float along = dot(rel, wind);
        float across = dot(rel, side);
        float L = (0.32 + 0.8 * clamp(uSmokeControl.z, 0.0, 1.5)) * mix(0.8, 1.3, clamp(sizeK, 0.0, 1.5) / 1.5);
        float au = max(along, 0.0);
        if (along < -0.08 || along > L * 1.15) return col;
        float width = (0.028 + au * mix(0.32, 0.62, turb)) * sizeK;
        float bend = (vnoise(vec2(au * 2.4, 4.0) + vec2(cos(ph), sin(ph)) * 0.45) - 0.5) * au * (0.30 + 0.5 * turb);
        float v = (across - bend) / width;
        if (abs(v) > 3.0) return col;
        float s = log(1.0 + 0.45 * au / 0.028) / 0.45 * 0.028 * 26.0;
        vec2 fq = vec2(v * 1.15, s);
        vec2 travel = vec2(0.0, 1.6);
        float n = contrastFix(mix(smokeDensity(fq - travel * progress, shape, turb),
            smokeDensity(fq - travel * (progress - 1.0), shape, turb), progress), progress);
        float body = exp(-v * v * 0.8);
        float t01 = clamp(along / L, 0.0, 1.0);
        presence = smoothstep(-0.03, 0.04, along) * (1.0 - smoothstep(0.45, 1.0, t01));
        float th = thr + 0.14 * t01 + (1.0 - body) * 0.95;
        dens = smoothstep(th - edge, th + edge * 1.6, n) * (1.0 - 0.45 * t01);
        shade = clamp(0.4 + n * 0.6 - v * 0.08, 0.0, 1.0);
    } else if (uSmokeShape.w < 0.5) {
        // Columna de volutas: bocanadas que nacen en el origen, suben, crecen, se
        // curvan y se disipan. Cada bocanada se ilumina como un volumen redondeado
        // (luz arriba-izquierda) y su borde se deforma con ruido propio que viaja con ella.
        float L = (0.32 + 0.8 * clamp(uSmokeControl.z, 0.0, 1.5)) * mix(0.8, 1.3, clamp(sizeK, 0.0, 1.5) / 1.5);
        float kw = mix(0.11, 0.24, turb);
        vec2 rel = P - origin;
        float along = dot(rel, wind);
        if (along < -0.12 || along > L * 1.35) return col;
        vec3 light3 = normalize(vec3(-0.45, -0.75, 0.55));
        float D = 0.0, S = 0.0, bestM = 0.0;
        for (int i = 0; i < 16; i++) {
            float fi = float(i);
            // Fase propia de cada bocanada: velocidad ENTERA por bucle (cierra sin saltos).
            float p = fract(fi / 16.0 + uT * cyc);
            float h1 = hash12(vec2(fi, 3.7)), h2 = hash12(vec2(fi, 8.1));
            // v40.1: sube rápido al nacer y frena arriba (el humo pierde empuje).
            float rise = 1.0 - pow(1.0 - p, 1.55);
            float au = L * rise;
            float w = (0.040 + au * kw * 1.15) * sizeK;
            float bendP = (vnoise(vec2(au * 2.4, 4.0) + vec2(cos(ph), sin(ph)) * 0.45) - 0.5) * au * (0.30 + 0.5 * turb);
            // Las bocanadas viejas se desvían a los lados (se abre en abanico, no un tubo).
            float spread = (h1 - 0.5) * w * (0.9 + 1.6 * p);
            vec2 c = origin + wind * au + side * (bendP + spread);
            float r = w * (0.75 + 0.5 * h2);
            vec2 u = (P - c) / r;
            if (shape > 2.5) u = vec2(dot(u, wind) * 0.55, dot(u, side) * 1.9);   // rastrero: aplanado a lo largo del viento
            float d2 = dot(u, u);
            // v44.15 · rendimiento: el radio máximo es 0,60 + 0,72 = 1,32 (1,32² ≈ 1,75); fuera de
            // él la bocanada no aporta nada. Antes se calculaba el ruido hasta 2,6 (×1,5 de área).
            if (d2 > 1.75) continue;
            float life = smoothstep(0.0, 0.10, p) * (1.0 - smoothstep(0.45, 1.0, p));
            if (life <= 0.001) continue;
            // Remolino propio: la textura interior gira lentamente mientras sube.
            float swirlA = (h2 - 0.5) * 2.4 * p + p * 1.3;
            mat2 rot = mat2(cos(swirlA), -sin(swirlA), sin(swirlA), cos(swirlA));
            vec2 ur = rot * u;
            float en = fbm3(ur * 1.4 + vec2(fi * 3.1, fi * 1.7) + vec2(0.0, p * 2.2));
            float rad = 0.60 + 0.72 * en;
            float x = sqrt(d2) / rad;
            if (x >= 1.0) continue;                     // fuera del cuerpo: sin el segundo ruido
            // Cuerpo suave + detalle interior (volutas pequeñas que se vuelven jirones al
            // envejecer). La masa se reparte en un área mayor: más transparente arriba.
            float bodyM = 1.0 - smoothstep(0.30 - 0.15 * soft, 1.0, x);
            float detail = fbm3(ur * 2.2 + vec2(fi * 5.7, p * 1.7));
            float erode = mix(0.28, 0.66, smoothstep(0.15, 0.95, p));
            float wisp = smoothstep(erode - 0.26, erode + 0.26, detail * 0.8 + bodyM * 0.38);
            float mm = bodyM * wisp * life * (0.75 + 0.5 * en) / (1.0 + 2.2 * p * p);
            if (mm <= 0.0) continue;
            vec2 uu = u / rad;
            float z = sqrt(max(1.0 - dot(uu, uu), 0.0));
            vec3 nrm = normalize(vec3(uu * 1.3 + (vec2(en, detail) - 0.5) * 1.2, z));
            float lit = clamp(dot(nrm, light3) * 0.65 + 0.35, 0.0, 1.0);
            // Sombreado ponderado por mm²: domina la bocanada más densa sin cortes de color
            // (antes se tomaba la luz de la "mejor" bocanada y aparecían bordes duros).
            D += mm; S += mm * mm * lit; bestM += mm * mm;
        }
        dens = clamp(D * mix(0.75, 1.15, clamp(density - 0.5, 0.0, 1.0)), 0.0, 2.0);
        shade = S / max(bestM, 1e-5);
        shade = smoothstep(0.15, 0.9, shade);
    } else {
        // Llenar toda la zona: banco de humo que avanza con el viento.
        vec2 q = (P - origin) * 4.5 / sizeK + vec2(3.7, 1.9);
        vec2 swirl = vec2(sin(ph), cos(ph)) * 0.14 * (0.4 + turb);
        vec2 travel = wind * (0.45 + uSmokeControl.z * 0.8) * 2.0;
        float da = smokeDensity(q + swirl - travel * progress, shape, turb);
        float db = smokeDensity(q + swirl - travel * (progress - 1.0), shape, turb);
        float n = contrastFix(mix(da, db, progress), progress);
        vec2 lq = q + lightDir * 0.3;
        float la = smokeDensity(lq + swirl - travel * progress, shape, turb);
        float lb = smokeDensity(lq + swirl - travel * (progress - 1.0), shape, turb);
        float nl = contrastFix(mix(la, lb, progress), progress);
        dens = smoothstep(thr - edge - 0.12, thr + edge, n) * 1.25;
        if (shape > 2.5) {
            dens *= 1.0 - smoothstep(-0.10, 0.30, origin.y - P.y);
            dens = max(dens, smoothstep(thr - edge - 0.12, thr + edge, n) * (1.0 - smoothstep(-0.25, 0.08, origin.y - P.y)) * 0.85);
        }
        shade = clamp(0.55 + (n - nl) * 3.2, 0.0, 1.0);
        presence = emitArea(P, uMotionSmoke);
    }
    float k = uFxSmoke.x * m * presence;
    // Absorción tipo Beer-Lambert: transparencia creíble y bordes suaves.
    float alpha = (1.0 - exp(-dens * min(density, 1.6) * 2.4)) * k;
    alpha = clamp(alpha, 0.0, 0.94);
    if (alpha <= 0.002) return col;
    float kind = floor(uSmokeControl.x + 0.5);
    vec3 tint = vec3(0.70, 0.72, 0.77);
    if (kind > 0.5 && kind < 1.5) tint = vec3(0.07, 0.065, 0.08);
    else if (kind > 1.5 && kind < 2.5) tint = vec3(0.90, 0.91, 0.94);
    else if (kind > 2.5 && kind < 3.5) tint = vec3(0.36, 0.24, 0.55);
    else if (kind > 3.5) tint = vec3(0.55, 0.38, 0.29);
    tint = tintOr(tint, uTintSmoke);
    vec3 smokeC = smokeShade(tint, shade, dens);
    if (shape > 1.5 && shape < 2.5) {
        // Tinta manga: dos tonos y contorno de tinta.
        float bright = luma(tint);
        smokeC = mix(tint * 0.62, mix(tint, vec3(1.0), 0.12), step(0.5, shade));
        float line = smoothstep(0.08, 0.2, dens) * (1.0 - smoothstep(0.2, 0.36, dens));
        smokeC = mix(smokeC, vec3(bright > 0.5 ? 0.10 : 0.0), line * 0.9);
        alpha = max(alpha, clamp(line * 0.85 * k, 0.0, 0.9));
    }
    if (uMono > 0.5) smokeC = vec3(luma(smokeC));
    return mix(col, smokeC, alpha);
}
#endif

// ---------------- Explosión por fases (v40) ----------------
// destello -> bola de fuego turbulenta (lóbulos, no un círculo) -> onda de choque ->
// chispas y escombros con gravedad -> humo residual iluminado que sube y se deshace.
#ifdef FX_BLAST
float blastFireDensity(vec2 nq, float churn, float seed) {
    // Volutas redondeadas (1 - billow) sobre una base con remolinos: puffs de fuego,
    // no grietas. Giran y suben lentamente (sin un punto singular en el centro).
    float cs = cos(churn * 0.35), sn = sin(churn * 0.35);
    vec2 rq = mat2(cs, -sn, sn, cs) * nq;
    float base = warpFbm(rq * 1.5 + vec2(seed * 5.3, seed * 2.1 + churn), 1.3);
    float puffs = 1.0 - billow(rq * 2.3 + vec2(3.1, seed * 1.7 + churn * 1.4));
    return clamp((base * 0.55 + puffs * 0.45 - 0.33) / 0.42, 0.0, 1.0);
}
#endif

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
    return length(pa - ba * h);
}

#ifdef FX_BLAST
vec3 zetaBlast(vec3 col, vec2 P, vec2 uv, float zone) {
    float a = uFxBlast.x * maskValue(uv, uMaskBlast, zone, uFxBlast.w);
    if (a <= 0.001) return col;
    float cycles = max(1.0, uFxBlast.z);
    float shape = floor(uBlastShape.x + 0.5);
    bool manga = shape > 1.5 && shape < 2.5;
    bool energy = shape > 2.5;
    bool mushroom = shape > 0.5 && shape < 1.5;
    float tau = uEventPhase.x >= 0.0 ? uEventPhase.x : fract(uT * cycles);
    if (manga) {                              // estallido manga: animación "en dos"
        float steps = max(4.0, floor(uLoopInfo.x * 12.0 / cycles));
        tau = floor(tau * steps) / steps;
    }
    float seed = uEventPhase.x >= 0.0 ? 1.0 : floor(uT * cycles);
    vec2 center = uMotionBlast.zw * vec2(uAspect, 1.0);
    float R = max(0.03, uFxBlast.y) * 0.45;
    vec2 aim = normalize(uMotionBlast.xy + vec2(1e-5, 0.0));
    vec2 q0 = P - center;
    float r0 = length(q0);

    vec3 hue = vec3(1.0, 0.47, 0.08);
    float kind = uBlastMore.x;
    if (kind > 0.5 && kind < 1.5) hue = vec3(0.25, 0.62, 1.0);
    else if (kind > 1.5 && kind < 2.5) hue = vec3(0.70, 0.32, 1.0);
    else if (kind > 2.5) hue = vec3(0.75, 0.16, 0.10);
    if (energy && uTintBlast.w < 0.5 && kind < 0.5) hue = vec3(0.30, 0.80, 1.0);
    hue = tintOr(hue, uTintBlast);

    // 1) Destello: luz que baña la escena y un núcleo cegador en el centro.
    float flashAmt = clamp(uBlastShape.y, 0.0, 1.0);
    float flash = pow(1.0 - smoothstep(0.0, 0.09, tau), 2.0) * flashAmt;
    if (manga) flash = (tau < 0.06 ? 1.0 : 0.0) * flashAmt;
    vec3 flashC = manga ? vec3(1.0) : mix(fireRamp(0.85, hue), vec3(1.0), 0.5);
    col = mix(col, col * 0.6 + flashC * 0.55, flash * 0.35 * a);
    col += flashC * flash * exp(-r0 * r0 / max(R * R * 1.2, 1e-4)) * a * 1.4;
    if (r0 > R * 4.2) return col;

    float pushAmt = clamp(uBlastShape.w, 0.0, 1.0);
    vec2 q = q0 - aim * R * 0.9 * pushAmt * sqrt(tau);
    float lift = mushroom ? R * 1.25 * pow(tau, 1.2) : R * 0.35 * smoothstep(0.15, 1.0, tau);
    q.y += lift;
    float r = length(q);
    float ang = atan(q.y, q.x);

    // 2) Bola de fuego: crece muy rápido y luego frena; silueta en lóbulos irregulares.
    float grow = 1.0 - pow(1.0 - clamp(tau / 0.28, 0.0, 1.0), 3.0);
    float rb = R * (0.16 + 0.84 * grow + 0.22 * tau);
    float h1 = hash12(vec2(seed, 1.3)) * TAU, h2 = hash12(vec2(seed, 2.7)) * TAU, h3 = hash12(vec2(seed, 4.1)) * TAU;
    float lobe = 1.0 + 0.06 * sin(2.0 * ang + h3) + 0.08 * sin(3.0 * ang + h1) + 0.07 * sin(5.0 * ang + h2) + 0.05 * sin(7.0 * ang + h1 * 1.7);
    float rad = rb * lobe;
    vec2 nq = q / max(rb, 0.01);
    float fd = blastFireDensity(nq, tau * 1.7, seed);
    // Segunda muestra hacia la luz: volumen redondeado (caras claras y huecos oscuros).
    float fdL = blastFireDensity(nq + vec2(0.05, 0.09), tau * 1.7, seed);
    float crease = clamp(0.5 + (fd - fdL) * 3.0, 0.0, 1.0) * 0.6 + fd * 0.4;
    float d = r / max(rad, 0.004);
    float ballEdge = d + (fd - 0.5) * 0.75;
    float fireLife = 1.0 - smoothstep(0.30, 0.68, tau);
    float body = 1.0 - smoothstep(0.80, 1.0, ballEdge);
    // Temperatura: núcleo blanco al principio; se enfría desde el borde hacia dentro.
    float heat = clamp((1.25 - ballEdge) * (0.30 + 1.10 * fd) - tau * 1.75 + 0.32, 0.0, 1.0);
    heat *= body;
    float coolT = smoothstep(0.34, 0.74, tau);
    // Al enfriarse el borde caliente se vuelve difuso: bolsas de brasa que se apagan, no un disco.
    float hot = smoothstep(0.03, 0.15 + 0.40 * coolT, heat) * (1.0 - 0.55 * coolT);
    // Costra carbonizada que se deshace mientras el humo residual la sustituye.
    float charA = (1.0 - hot) * (1.0 - smoothstep(0.28, 0.66, tau)) * 0.9;
    float fireA = body * max(hot * (1.0 - 0.5 * smoothstep(0.42, 0.7, tau)), charA);
    // v40.1: al apagarse la bola se rompe en jirones (agujeros que crecen) en vez de
    // encogerse como un disco plano.
    float breakup = coolT;
    fireA *= smoothstep(breakup * 0.95 - 0.18, breakup * 0.95 + 0.12, fd + 0.3 * (1.0 - ballEdge));

    // 5a) Humo residual: la bola se convierte en humo oscuro que sube y se abre.
    float smokeAmt = clamp(uBlastMore.y, 0.0, 1.0);
    vec3 outC = col;
    if (!energy && smokeAmt > 0.01) {
        float rs = R * (0.55 + 1.15 * tau);
        vec2 sq = q + vec2(0.0, R * 0.9 * tau);
        if (mushroom) sq.y += R * 0.4 * tau;
        vec2 sn = sq / max(rs, 0.01);
        vec2 drift = vec2(0.0, tau * 1.3);
        float s1 = warpFbm(sn * 2.0 + vec2(seed * 3.3, 0.0) + drift, 1.6);
        float s2 = 1.0 - billow(sn * 3.2 + vec2(4.1, seed) + drift * 1.4);
        float sd = s1 * 0.65 + s2 * 0.4;
        float sl = warpFbm((sn + vec2(0.06, 0.12)) * 2.0 + vec2(seed * 3.3, 0.0) + drift, 1.6);
        float rad2 = length(sn) * (1.0 + 0.12 * sin(4.0 * ang + h2));
        float erosion = 0.32 + 0.38 * smoothstep(0.55, 1.0, tau);    // se deshace en jirones
        float sdens = smoothstep(erosion, erosion + 0.25, sd + 0.45 - rad2 * 0.62);
        if (mushroom) {                                              // tallo del hongo
            vec2 st = q0;
            float top = -R * 1.25 * pow(tau, 1.2);
            float stemN = fbm3(vec2(st.x / R * 4.0, st.y / R * 2.0 + tau * 3.0));
            float stem = (1.0 - smoothstep(R * 0.12, R * 0.32, abs(st.x + (stemN - 0.5) * R * 0.3))) *
                smoothstep(top, top + R * 0.3, st.y) * (1.0 - smoothstep(-R * 0.02, R * 0.2, st.y));
            sdens = max(sdens, stem * 0.85 * smoothstep(0.1, 0.3, tau));
        }
        float life = smoothstep(0.10, 0.34, tau) * (1.0 - smoothstep(0.78, 1.0, tau));
        float sAlpha = (1.0 - exp(-sdens * 2.4)) * life * smokeAmt * 0.9;
        vec3 sTint = kind > 2.5 ? vec3(0.06) : mix(vec3(0.22, 0.21, 0.23), hue * 0.30, 0.22);
        float shade = clamp(0.55 + (sd - sl) * 4.5, 0.0, 1.0);
        vec3 smokeC = smokeShade(sTint, shade, sdens);
        // Luz anaranjada del fuego en la base del humo mientras la bola sigue viva.
        smokeC += fireRamp(0.55, hue) * (1.0 - shade) * fireLife * 0.35;
        if (manga) {
            float dots = step(0.5, fract(dot(P, vec2(90.0, 90.0)))) * step(0.5, fract(dot(P, vec2(90.0, -90.0))));
            smokeC = mix(vec3(0.95), mix(vec3(0.35), vec3(0.95), dots), step(shade, 0.5));
            float line = smoothstep(0.22, 0.30, sdens) * (1.0 - smoothstep(0.30, 0.40, sdens)) * (1.0 - smoothstep(1.0, 1.4, rad2));
            smokeC = mix(smokeC, vec3(0.02), line);
            sAlpha = max(sAlpha * step(0.22, sdens), line * life * smokeAmt);
        }
        if (uMono > 0.5) smokeC = vec3(luma(smokeC));
        outC = mix(outC, smokeC, clamp(sAlpha * a, 0.0, 0.92));
    }

    // 2b) Fuego encima del humo.
    vec3 fireC = fireRamp(heat + 0.05, hue);
    // Pliegues de las volutas; el núcleo muy caliente queda limpio (sin rayas de relieve).
    fireC *= mix(0.45 + 0.75 * crease, 1.0, smoothstep(0.45, 0.85, heat));
    // Al enfriarse, la superficie se carboniza: transición natural de fuego a humo.
    vec3 charC = mix(vec3(0.16, 0.13, 0.12), hue * 0.35, 0.3) * (0.7 + 0.5 * crease);
    fireC = mix(charC, fireC, smoothstep(0.04, 0.30, heat));
    if (manga) {
        // Manga: blanco caliente, trama de puntos en los medios tonos, negro en los
        // pliegues y contornos de tinta en el borde y entre las volutas.
        float tone = heat + (crease - 0.5) * 0.35;
        vec2 g = fract(P * 140.0) - 0.5;
        float dots = step(length(g), 0.32);
        vec3 ink = vec3(0.03);
        fireC = tone > 0.5 ? vec3(1.0) : (tone > 0.24 ? mix(vec3(1.0), ink, dots) : mix(ink, vec3(1.0), dots * 0.35));
        float outline = smoothstep(0.84, 0.9, ballEdge) * (1.0 - smoothstep(0.93, 0.99, ballEdge));
        float folds = 1.0 - smoothstep(0.0, 0.035, abs(fd - 0.5));
        // Pliegues de tinta solo hacia el borde: el centro queda limpio (sin garabatos).
        fireC = mix(fireC, ink, max(outline, folds * 0.7 * smoothstep(0.45, 0.85, ballEdge)));
        if (uTintBlast.w > 0.5) fireC *= uTintBlast.rgb;
    }
    if (energy) fireC = mix(hue, vec3(1.0), smoothstep(0.35, 0.9, heat)) * (0.8 + 0.4 * crease);
    if (uMono > 0.5) fireC = vec3(luma(fireC));
    outC = mix(outC, fireC, clamp(fireA * min(a * 1.25, 1.0), 0.0, 1.0));
    // Resplandor alrededor de la bola (luz emitida sobre la escena).
    float glow = exp(-max(d - 0.6, 0.0) * 2.6) * fireLife * (manga ? 0.0 : 1.0);
    outC += fireRamp(0.6, hue) * glow * 0.22 * a;
    col = outC;

    // 3) Onda de choque: anillo de aire comprimido, roto e irregular, más polvo claro.
    float ringAmt = clamp(uBlastShape.z, 0.0, 1.0);
    if (ringAmt > 0.01 && tau < 0.62) {
        float rr = length(q0);
        float a0 = atan(q0.y, q0.x);
        float rs = R * (0.3 + 3.2 * sqrt(tau)) * (1.0 + (vnoise(vec2(a0 * 4.0, seed + 2.0)) - 0.5) * 0.18);
        float th = R * (0.025 + 0.09 * tau);
        float ring = exp(-pow((rr - rs) / max(th, 0.003), 2.0)) * pow(1.0 - tau / 0.62, 1.6);
        ring *= 0.35 + 0.65 * smoothstep(0.3, 0.7, vnoise(vec2(a0 * 6.0, seed + 9.0)));
        float haze = smoothstep(rs, rs * 0.55, rr) * smoothstep(0.0, 0.05, tau) * (1.0 - smoothstep(0.1, 0.55, tau));
        col = mix(col, vec3(0.92, 0.9, 0.86), haze * 0.16 * ringAmt * a * (energy ? 0.0 : 1.0));
        vec3 ringC = energy ? mix(hue, vec3(1.0), 0.6) : vec3(1.0, 0.96, 0.9);
        col += ringC * ring * ringAmt * a * (energy ? 1.1 : 0.55);
        if (energy) col += hue * exp(-pow((rr - rs * 0.6) / max(0.004, R * 0.04), 2.0)) * (1.0 - tau) * a;
    }

    // 4) Chispas incandescentes y escombros oscuros con gravedad y estela.
    float sparksAmt = clamp(uBlastMore.z, 0.0, 1.0);
    if (sparksAmt > 0.01 && tau > 0.03 && tau < 0.9) {
        float best = 0.0, dark = 0.0;
        for (int i = 0; i < 24; i++) {
            float fi = float(i);
            vec2 h = hash22(vec2(fi * 7.13 + seed, seed * 3.7 + fi * 1.9));
            float life = 0.32 + 0.48 * hash12(vec2(fi, seed + 9.0));
            if (tau > life) continue;
            float an = h.x * TAU + fi * 0.37;
            vec2 dir = vec2(cos(an), sin(an) * 0.9 - 0.25);
            float sp = R * (0.8 + 3.4 * h.y * h.y);
            bool debris = mod(fi, 4.0) < 0.5;
            float g = R * (debris ? 3.2 : 1.2);
            float t1 = tau, t0 = max(tau - (debris ? 0.012 : 0.035), 0.0);
            vec2 p1 = dir * sp * (1.0 - exp(-3.2 * t1)) / 1.6 + vec2(0.0, g * t1 * t1);
            vec2 p0 = dir * sp * (1.0 - exp(-3.2 * t0)) / 1.6 + vec2(0.0, g * t0 * t0);
            float fade = 1.0 - smoothstep(life * 0.55, life, tau);
            float w = R * (debris ? 0.022 + 0.02 * h.x : 0.006 + 0.006 * h.y);
            float dd = segDist(q0, p0, p1);
            // Estela afinada: más gruesa y brillante en la cabeza que en la cola.
            float tip = clamp(dot(q0 - p0, p1 - p0) / max(dot(p1 - p0, p1 - p0), 1e-8), 0.0, 1.0);
            float ww = w * (debris ? 1.0 : 0.35 + 0.65 * tip);
            float v = (1.0 - smoothstep(ww * 0.4, ww, dd)) * fade * (debris ? 1.0 : (0.35 + 0.65 * tip) * (0.5 + 0.5 * h.x));
            if (debris) dark = max(dark, v * smoothstep(R * 0.5, R * 0.9, length(p1)));
            else best = max(best, v + 0.35 * fade * exp(-dd / max(w * 3.0, 1e-4)) * 0.5);
        }
        if (!energy) col = mix(col, vec3(0.07, 0.06, 0.06), clamp(dark * sparksAmt * a * 0.9, 0.0, 0.9));
        col += fireRamp(0.82, hue) * best * sparksAmt * a * 1.2;
    }

    // Líneas de impacto del estallido manga (v40.1): trazos finos de tinta que nacen
    // fuera de la bola y se ensanchan hacia el borde, como las líneas de concentración
    // dibujadas a mano (antes eran cuñas grises enormes).
    if (manga) {
        float a0 = atan(q0.y, q0.x);
        float N = 96.0;
        float sector = (a0 + 3.14159) / TAU * N;
        float sid = floor(sector);
        float h1 = hash12(vec2(sid, seed + 21.0)), h2 = hash12(vec2(sid, seed + 22.0));
        float lineA = (sid + 0.25 + 0.5 * h2) / N * TAU - 3.14159;
        float perp = r0 * abs(sin(a0 - lineA));
        float rStart = R * (0.95 + 0.45 * h1) * (0.85 + 0.6 * tau);
        float grow = smoothstep(rStart, rStart + R * 1.6, r0);
        float thick = R * (0.004 + 0.022 * h2) * grow;
        float ink = (1.0 - smoothstep(thick * 0.55, thick + 0.0015, perp)) * step(0.55, h1) * step(rStart, r0);
        float life = 1.0 - smoothstep(0.25, 0.55, tau);
        col = mix(col, vec3(0.02), clamp(ink * life * a, 0.0, 0.95));
    }
    return col;
}
#endif

// ---------------- Salpicaduras manga / manhwa ----------------
// Unión suave de círculos (smooth-min): gotas que se funden como tinta, borde duro.
float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

#ifdef FX_BLOOD
vec3 zetaBlood(vec3 col, vec2 P, vec2 uv, float zone) {
    float cycles = max(1.0, uFxBlood.z);
    float tau = uEventPhase.y >= 0.0 ? uEventPhase.y : fract(uT * cycles);
    float seed = uEventPhase.y >= 0.0 ? 13.0 : floor(uT * cycles) * 13.0;
    float shape = floor(uBloodShape.x + 0.5);
    vec2 origin = uMotionBlood.zw * vec2(uAspect, 1.0);
    float S = 0.5 * max(0.2, uFxBlood.y);
    vec2 x = P - origin;
    float a = uFxBlood.x * maskValue(uv, uMaskBlood, zone, uFxBlood.w);
    if (a <= 0.001) return col;
    float life = smoothstep(0.0, 0.04, tau) * (1.0 - smoothstep(0.72, 0.97, tau));
    float sd = 1e3;                       // distancia con signo a la tinta (<0 dentro)
    if (shape < 0.5) {
        // Spray: gotas lanzadas en abanico; frenan, caen y se estiran según su velocidad.
        float dirA = uBloodShape.y;
        float open = mix(0.35, 1.6, clamp(uBloodShape.z, 0.0, 1.0));
        float ease = 1.0 - pow(1.0 - min(tau / 0.35, 1.0), 3.0);
        sd = length(x * vec2(1.0, 1.2)) - S * 0.06 * smoothstep(0.0, 0.06, tau);
        for (int i = 0; i < 16; i++) {
            float k = float(i);
            float h1 = hash12(vec2(k, seed + 1.0)), h2 = hash12(vec2(k, seed + 2.0)), h3 = hash12(vec2(k, seed + 3.0));
            float an = dirA + (h1 - 0.5) * open;
            vec2 v = vec2(cos(an), -sin(an)) * S * (0.18 + 0.62 * h2);
            vec2 pos = v * ease + vec2(0.0, 0.10 * S * ease * ease);
            float rad = S * 0.012 * clamp(pow(h3 + 0.05, -0.6), 0.7, 3.4);
            vec2 rel = x - pos;
            vec2 dirv = normalize(v + 1e-5);
            float stretch = 1.0 + 3.0 * (1.0 - ease) * h2;
            float along = dot(rel, dirv) / stretch, across = dot(rel, vec2(-dirv.y, dirv.x));
            // estela hacia el origen mientras vuela
            float tail = max(0.0, -dot(rel, dirv)) / max(length(pos) + 1e-4, 1e-4);
            float dd = length(vec2(along, across)) - rad * (1.0 - 0.6 * clamp(tail, 0.0, 1.0));
            sd = smin(sd, dd, rad * 0.9);
        }
    } else if (shape < 1.5) {
        // Goteo: mancha que aparece y chorrea por gravedad.
        float splat = S * 0.17 * smoothstep(0.0, 0.08, tau);
        float rr = length(x * vec2(1.0, 1.25)) + (vnoise(vec2(atan(x.y, x.x) * 3.0, seed)) - 0.5) * splat * 0.6;
        sd = rr - splat;
        for (int i = 0; i < 6; i++) {
            float k = float(i);
            float hx = hash12(vec2(k, seed + 5.0)) - 0.5;
            float hl = hash12(vec2(k, seed + 6.0));
            float px = hx * splat * 1.5;
            float len = S * (0.12 + 0.5 * hl) * smoothstep(0.06, 0.65, tau);
            float wid = S * 0.014 * (0.6 + hl);
            float y = clamp(x.y, 0.0, len);
            float taper = 1.0 - (y / max(len, 1e-4)) * 0.45;
            float body = length(vec2(x.x - px, x.y - y)) - wid * taper;
            float bulb = length(vec2(x.x - px, x.y - len)) - wid * 1.45;
            sd = smin(sd, min(body, bulb), wid);
        }
    } else {
        // Mancha de tinta: borde irregular que se expande, con gotas satélite.
        float grow = 1.0 - pow(1.0 - min(tau / 0.22, 1.0), 3.0);
        float ang = atan(x.y, x.x);
        float rb = S * 0.2 * grow * (1.0 + 0.55 * (fbm3(vec2(ang * 1.6 + seed, seed)) - 0.5) * 2.0);
        sd = length(x) - rb;
        for (int i = 0; i < 10; i++) {
            float k = float(i);
            float an = hash12(vec2(k, seed + 9.0)) * TAU;
            float dist = S * (0.25 + 0.38 * hash12(vec2(k, seed + 10.0))) * grow;
            vec2 c = vec2(cos(an), sin(an)) * dist;
            float rad = S * 0.016 * (0.5 + hash12(vec2(k, seed + 11.0))) * grow;
            sd = smin(sd, length(x - c) - rad, rad * 0.5);
        }
    }
    float px = max(fwidth(sd), 1e-4);
    float ink = (1.0 - smoothstep(-px, px, sd)) * life;
    vec3 tint = vec3(0.62, 0.035, 0.09);
    if (uBloodStyle.x > 0.5 && uBloodStyle.x < 1.5) tint = vec3(0.025, 0.012, 0.03);
    else if (uBloodStyle.x > 1.5) tint = vec3(0.09, 0.37, 0.75);
    tint = tintOr(tint, uTintBlood);
    return mix(col, tint, clamp(ink * a, 0.0, 0.96));
}
#endif

// ---------------- Cortes de energía ----------------
#ifdef FX_SLASH
float slashArc(vec2 q, float len, float bend, float reveal, float width) {
    // Media luna: arco de círculo cuyo grosor se afina en los extremos.
    float Rr = len / max(0.2, bend);
    vec2 c = vec2(0.0, Rr);
    float r = length(q - c);
    float ang = atan(q.x, -(q.y - Rr)) / (len / Rr);   // -0.5..0.5 a lo largo del arco
    float s = ang + 0.5;
    float visible = step(0.0, s) * step(s, reveal);
    float thick = width * sin(3.14159 * clamp(s, 0.0, 1.0));
    return visible * (1.0 - smoothstep(thick * 0.6, thick, abs(r - Rr)));
}
#endif

#ifdef FX_SLASH
vec3 zetaSlash(vec3 col, vec2 P, vec2 uv, float zone) {
    vec2 diff = P - uMotionSlash.zw * vec2(uAspect, 1.0);
    float a0 = uSlashStyle.y;
    float shape = floor(uSlashShape.x + 0.5);
    float phase = uEventPhase.z >= 0.0 ? uEventPhase.z : fract(uT * max(1.0, uFxSlash.z));
    float len = max(0.10, uFxSlash.y) * 0.44;
    float width = mix(0.004, 0.03, clamp(uSlashShape.y, 0.0, 1.0));
    float m = uFxSlash.x * maskValue(uv, uMaskSlash, zone, uFxSlash.w);
    if (m <= 0.001) return col;
    vec3 hue = vec3(0.14, 0.76, 1.0);
    if (uSlashStyle.x > 0.5 && uSlashStyle.x < 1.5) hue = vec3(0.94);
    else if (uSlashStyle.x > 1.5 && uSlashStyle.x < 2.5) hue = vec3(1.0, 0.70, 0.19);
    else if (uSlashStyle.x > 2.5) hue = vec3(1.0, 0.19, 0.22);
    hue = tintOr(hue, uTintSlash);
    float glow = 0.0, core = 0.0;
    if (shape < 0.5) {
        // Trazo clásico con estela ondulada (v35 mejorado: aparece de un extremo al otro).
        vec2 basis = vec2(cos(a0), sin(a0));
        vec2 q = vec2(dot(diff, basis), dot(diff, vec2(-basis.y, basis.x)));
        float flash = sin(3.14159 * phase);
        float xfade = 1.0 - smoothstep(0.55, 0.96, phase);
        float dist = abs(q.y - 0.10 * len * sin(q.x / len * 3.0 - TAU * phase));
        float reveal = step(q.x, mix(-len, len, clamp(phase * 4.0, 0.0, 1.0)));
        float lengthFade = (1.0 - smoothstep(len * 0.57, len, abs(q.x))) * reveal;
        core = exp(-dist / width * 1.6) * lengthFade * flash * xfade;
        glow = exp(-dist / (width * 5.0)) * lengthFade * flash * xfade;
    } else {
        int count = shape < 1.5 ? 1 : (shape < 2.5 ? 2 : 3);
        for (int k = 0; k < 3; k++) {
            if (k >= count) break;
            float fk = float(k);
            float start = fk * 0.12;
            float local = clamp((phase - start) / 0.5, 0.0, 1.0);
            float reveal = smoothstep(0.0, 0.35, local) * 1.05;
            float life = 1.0 - smoothstep(0.45, 1.0, local);
            float ang = a0;
            vec2 offset = vec2(0.0);
            if (shape > 1.5 && shape < 2.5) ang = a0 + (fk == 0.0 ? 0.785 : -0.785);
            if (shape > 2.5) offset = vec2(-sin(a0), cos(a0)) * (fk - 1.0) * len * 0.35;
            vec2 basis = vec2(cos(ang), sin(ang));
            vec2 d2 = diff - offset;
            vec2 q = vec2(dot(d2, basis), dot(d2, vec2(-basis.y, basis.x)));
            float arc = slashArc(q, len * 2.0, shape < 1.5 ? 0.9 : 0.35, reveal, width * 1.6);
            float halo = slashArc(q, len * 2.0, shape < 1.5 ? 0.9 : 0.35, reveal, width * 3.2);
            core = max(core, arc * life * step(start, phase));
            glow = max(glow, halo * life * step(start, phase));
        }
    }
    col += hue * glow * 0.42 * m;
    col = mix(col, mix(hue, vec3(1.0), 0.75), clamp(core * m, 0.0, 1.0));
    return col;
}
#endif

// ---------------- Aparición / revelación entre humo ----------------
#ifdef FX_REVEAL
vec3 zetaReveal(vec3 col, vec2 P, vec2 uv, float zone) {
    // Revela píxeles existentes sin pretender recomponer el fondo oculto.
    float phase = uEventPhase.w >= 0.0 ? uEventPhase.w : fract(uT * max(1.0, uFxReveal.z));
    // En modo bucle vuelve a ocultarse al final para empalmar; en Una vez queda revelado.
    float visibility = smoothstep(0.08, 0.62, phase) * (uEventPhase.w >= 0.0 ? 1.0 : 1.0 - smoothstep(0.86, 1.0, phase));
    float shape = floor(uRevealShape.x + 0.5);
    float ph = TAU * uT;
    float n = fbm(P * (2.0 + uFxReveal.y * 3.0) + vec2(sin(ph), cos(ph)) * 0.28);
    // Frente de revelado: desde el centro, desde abajo o empujado por el viento.
    vec2 c = vec2(0.5 * uAspect, 0.5);
    float front = 0.0;
    if (shape < 0.5) front = length(P - c) * 1.2;
    else if (shape < 1.5) front = 1.0 - uv.y;
    else front = uv.x;
    // Orden de aparición 0..1 (menor = aparece antes) y avance del revelado.
    float order = clamp(front * 0.75 + (n - 0.5) * 0.7 + 0.12, 0.0, 1.0);
    float reveal = visibility * 1.2 - 0.06;
    float hidden = smoothstep(reveal - 0.06, reveal + 0.06, order);
    float m = maskValue(uv, uMaskReveal, zone, uFxReveal.w);
    float a = clamp(hidden * uFxReveal.x * m, 0.0, 1.0);
    vec3 shade = vec3(0.045, 0.052, 0.070);
    if (uRevealStyle.x > 0.5 && uRevealStyle.x < 1.5) shade = vec3(0.48, 0.54, 0.61);
    else if (uRevealStyle.x > 1.5) shade = vec3(0.018, 0.012, 0.030);
    shade = tintOr(shade, uTintReveal);
    col = mix(col, shade, a * (0.75 + 0.25 * smoothstep(0.39, 0.68, n)));
    // Humo que se arremolina justo en el borde del revelado.
    float edgeSmoke = clamp(uRevealShape.y, 0.0, 1.0);
    if (edgeSmoke > 0.01) {
        float band = 1.0 - smoothstep(0.0, 0.14, abs(order - reveal));
        float puff = smoothstep(0.42, 0.7, fbm(P * 6.0 + vec2(cos(ph), sin(ph)) * 0.5));
        vec3 smokeC = mix(shade, vec3(luma(shade) > 0.3 ? 0.95 : 0.35), 0.45);
        col = mix(col, smokeC, clamp(band * puff * edgeSmoke * uFxReveal.x * m * step(0.02, visibility) * 0.85, 0.0, 0.9));
    }
    return col;
}
#endif

// ---------------- Fuego natural y mágico (v40) ----------------
// La zona pintada es la BASE del fuego: las llamas suben por encima de ella.
// Sin zona pintada el fuego nace en el borde inferior con una altura moderada:
// ya no cubre toda la imagen ni tapa al personaje.
#ifdef FX_FLAME
// v42 · Ruido periódico en vertical: las llamas SUBEN de verdad y el bucle cierra exacto
// sin mezclar dos fases (la mezcla de la v40/v41 dejaba lenguas borrosas y "dobles").
float flameHashP(vec2 c, float py) { c.y = mod(c.y, py); return hash12(c + vec2(0.0, 0.37)); }
float flameNoiseP(vec2 p, float py) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = flameHashP(i, py), b = flameHashP(i + vec2(1.0, 0.0), py);
    float c = flameHashP(i + vec2(0.0, 1.0), py), d = flameHashP(i + vec2(1.0, 1.0), py);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float flameFbmP(vec2 p, float py) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * flameNoiseP(p, py);
        p = p * 2.0 + vec2(17.31, 0.0);   // solo se desplaza en x: conserva el periodo
        py *= 2.0;
        a *= 0.5;
    }
    return s / 0.96875;
}
// q: espacio de la llama (y crece hacia arriba). Devuelve el campo y el detalle fino.
float flameField(vec2 q, float progress, float turb, out float detail) {
    const float PER = 4.0;                       // periodo vertical = recorrido por ciclo
    vec2 s = vec2(q.x, q.y - progress * PER);    // el patrón sube PER unidades por ciclo
    // Turbulencia: deformación de dominio con el mismo periodo (lenguas que se retuercen).
    vec2 w = vec2(flameFbmP(s + vec2(5.2, 0.0), PER), flameFbmP(s * vec2(1.0, 1.0) + vec2(1.7, 0.0), PER)) - 0.5;
    float amt = 0.35 + 0.8 * turb;
    float n = flameFbmP(s + w * amt * vec2(1.25, 0.8), PER);
    detail = flameNoiseP((s + w * amt) * 2.0 + vec2(9.4, 0.0), PER * 2.0);
    return n;
}
#endif

#ifdef FX_FLAME
vec3 zetaFlame(vec3 col, vec2 P, vec2 uv) {
    vec4 box = uFlameBox;
    bool whole = box.z <= box.x + 0.001;
    if (whole) box = vec4(0.0, 0.88, 1.0, 1.0);
    vec2 size = max(box.zw - box.xy, vec2(0.02));
    float hp = clamp(uFlameShape.w, 0.0, 1.0);
    float H = whole ? 0.14 + 0.42 * hp : min(size.y * (1.2 + 2.6 * hp) + 0.07, 0.72);
    float hN = (box.w - uv.y) / H;                 // 0 en la base, 1 en la punta
    float shape = floor(uFlameShape.x + 0.5);
    float style = floor(uLightStyles.w + 0.5);
    vec3 base = vec3(1.0, 0.40, 0.07);
    if (style > 0.5 && style < 1.5) base = vec3(0.18, 0.55, 1.0);
    else if (style > 1.5 && style < 2.5) base = vec3(0.24, 0.9, 0.34);
    else if (style > 2.5) base = vec3(0.72, 0.30, 1.0);
    if (shape > 2.5 && shape < 3.5 && uTintFlame.w < 0.5 && style < 0.5) base = vec3(0.45, 0.35, 1.0);
    base = tintOr(base, uTintFlame);
    // Luz cálida que el fuego proyecta sobre la escena y el personaje (halo suave, sin bordes).
    float cyc0 = max(1.0, uFxFlame.z);
    float flick0 = 0.88 + 0.12 * sin(TAU * uT * cyc0 * 3.0) * sin(TAU * uT * cyc0 * 2.0 + 1.3);
    vec2 hc0 = vec2((uv.x - (box.x + box.z) * 0.5) / max(size.x * 0.75, 0.05), (uv.y - (box.w - H * 0.3)) / max(H * 0.85, 0.05));
    vec3 glowC = fireRamp(0.55, base);
    if (uMono > 0.5) glowC = vec3(luma(glowC));
    if (!whole) col += glowC * exp(-dot(hc0, hc0)) * 0.16 * clamp(uFxFlame.x, 0.0, 1.5) * flick0;
    if (hN < -0.08 || hN > 1.3) return col;
    // Columna de fuego: la máscara en la misma x, medida dentro de la zona pintada.
    float sampleY = clamp(uv.y, box.y + size.y * 0.5, box.w);
    float jitter = (vnoise(vec2(uv.y * 18.0, uv.x * 9.0)) - 0.5) * size.x * 0.12 * clamp(hN, 0.0, 1.0);
    vec2 su = vec2(uv.x + jitter, sampleY);
    // v40.1: la columna se mide con 3 muestras: los lados de la zona se funden en vez de
    // cortar el resplandor y el humo con un borde vertical recto.
    vec2 sd = vec2(size.x * 0.07, 0.0);
    float area = (maskValue(su - sd, uMaskFlame, maskLayer(su - sd, 2), 1.0) +
                  2.0 * maskValue(su, uMaskFlame, maskLayer(su, 2), 1.0) +
                  maskValue(su + sd, uMaskFlame, maskLayer(su + sd, 2), 1.0)) * 0.25;
    // No tapa al personaje por encima de la zona. v44.16: transición suave (antes un corte recto).
    area *= 1.0 - maskLayer(uv, 0) * 0.85 * (1.0 - smoothstep(box.y - 0.12, box.y + 0.02, uv.y));
    if (area <= 0.001) return col;
    float turb = clamp(uFlameShape.y, 0.0, 1.0);
    float soft = clamp(uFlameShape.z, 0.0, 1.0);
    float cx = (uv.x - box.x) / size.x - 0.5;
    float cyc = max(1.0, uFxFlame.z);
    float progress = fract(uT * cyc);
    float ph = TAU * uT * cyc;
    float sc = max(0.3, uFxFlame.y);
    float lean = 0.0;
    if (shape > 1.5 && shape < 2.5) lean = hN * hN * (0.10 + 0.02 * sin(ph));            // antorcha al viento
    if (shape > 2.5 && shape < 3.5) lean = sin(hN * 5.0 - ph) * 0.03 * hN;                 // mágica: serpentea
    // Espacio de la llama: estirado en vertical para formar lenguas.
    float hy = (box.w - uv.y) / sc;
    // Las lenguas se curvan más cuanto más suben (balanceo periódico: cierra el bucle).
    float curl = (vnoise(vec2(hy * 5.0 - progress * 0.0, P.x * 3.0) + vec2(cos(ph), sin(ph)) * 0.8) - 0.5) * clamp(hN, 0.0, 1.2) * 0.07 * (0.5 + turb);
    // v42: proporción de las lenguas ≈ 1:1,8 (antes 1:4, se veían como rayas estiradas).
    // Más arriba, menos frecuencia vertical: las lenguas se alargan y afinan al subir.
    float detail;
    float width = 1.0;
    if (shape > 0.5 && shape < 1.5) width = 1.0 - smoothstep(0.05 + 0.10 * (1.0 - hN), 0.12 + 0.16 * (1.0 - hN), abs(cx + lean * 2.0)); // vela
    float hc = clamp(hN, 0.0, 1.25);
    float fuel;
    float simSmoke = -1.0;
    if (uFxReady > 0.5) {
        // v44.21 · FUEGO SIMULADO: llamas de verdad (física de fluidos, 32 cuadros en bucle)
        // en fila, en dos capas desfasadas media llama (sin costuras ni repetición visible).
        // Cada llama tiene su fase, altura y lado; la velocidad es entera por bucle.
        float cellH = H / 0.50;                                  // la punta media llega a media celda
        float tileW = cellH * (2.0 / 3.0) * (shape > 0.5 && shape < 1.5 ? 0.6 : 0.8);
        float kf = max(1.0, floor(uLoopInfo.x * 0.85 * (0.55 + 0.45 * cyc) + 0.5));
        float px = (P.x - lean * 0.6 + curl * 0.6) / tileW;
        float heat = 0.0;
        float smk = 0.0;
        for (int L = 0; L < 2; L++) {
            float xx = px + float(L) * 0.5;
            float id = floor(xx);
            float h1 = hash12(vec2(id, 7.0 + float(L) * 13.0));
            float h2 = hash12(vec2(id, 31.0 + float(L) * 5.0));
            float hs = 0.82 + 0.34 * h2;                            // altura propia
            float u = fract(xx);
            if (h1 > 0.5) u = 1.0 - u;                              // llamas espejadas
            vec2 q = vec2(0.5 + (u - 0.5) * 0.92, hN * 0.50 / hs + 0.045);   // el quemador queda bajo la base
            float frame = fract(uT * kf + h1 * 7.31) * 32.0;
            vec2 fs = fxFire(q, frame);
            heat = 1.0 - (1.0 - heat) * (1.0 - fs.r);
            smk = max(smk, fs.g);
        }
        detail = heat;
        simSmoke = smk;
        fuel = (heat * 1.25 - 0.14) * width;
    } else {
    vec2 fq = vec2((P.x - lean + curl) * 7.0 / sc, hy * 3.3 * (1.0 - 0.3 * clamp(hN, 0.0, 1.0)));
    float n = flameField(fq, progress, turb, detail);
    n = n * 0.82 + detail * 0.18;
    float nn = clamp((n - 0.28) / 0.40, 0.0, 1.0);
    // Combustible: fuerte en la base y se agota hacia la punta; las puntas se desprenden.
    fuel = (nn * 1.1 - hc * 1.05 + (1.0 - smoothstep(0.0, 0.25, hc)) * 0.30) * width;
    }
    // Forma de pira: las lenguas laterales son más bajas que las centrales.
    if (!whole) fuel -= smoothstep(0.22, 0.5, abs(cx)) * (0.3 + hc * 0.9);
    float k = clamp(uFxFlame.x, 0.0, 1.5) * area;
    float edgeW = mix(0.025, 0.14, soft);
    float alpha = smoothstep(0.0, edgeW, fuel) * smoothstep(-0.08, 0.05, hN);
    float temp = clamp(fuel * 1.05 + (1.0 - hc) * 0.06, 0.0, 1.0);
    // v42: los bordes fríos son rojos y translúcidos (antes marrón oscuro opaco: "manchas").
    vec3 flameC = fireRamp(max(temp, 0.22), base) * (0.86 + 0.24 * detail);
    alpha *= smoothstep(0.0, 0.3, temp + 0.05);
    if (shape > 3.5) {
        // Fuego fantasma: contornos luminosos translúcidos.
        float line = (1.0 - smoothstep(0.0, 0.03 + edgeW * 0.4, abs(fuel - 0.04))) + 0.5 * (1.0 - smoothstep(0.0, 0.02 + edgeW * 0.3, abs(fuel - 0.22)));
        vec3 g = mix(base, vec3(1.0), 0.4);
        if (uMono > 0.5) g = vec3(luma(g));
        col += g * line * k + g * alpha * 0.12 * k;
        return col;
    }
    // Humo opcional: sube desde las puntas, iluminado y semitransparente.
    float smokeAmt = clamp(uFlameExtra.y, 0.0, 1.0);
    if (smokeAmt > 0.01 && hN > 0.35) {
        vec2 sq = vec2((P.x - lean) * 3.0 / sc, (box.w - uv.y) * 1.7 / sc);
        float sa = warpFbm(sq + vec2(0.0, -1.3) * progress, 1.5), sb = warpFbm(sq + vec2(0.0, -1.3) * (progress - 1.0), 1.5);
        float sn = contrastFix(mix(sa, sb, progress), progress);
        float sm = smoothstep(0.46, 0.7, sn) * smoothstep(0.35, 0.8, hN) * (1.0 - smoothstep(0.95, 1.28, hN));
        if (simSmoke >= 0.0) sm = mix(sm * 0.35, simSmoke * simSmoke, 0.75) * smoothstep(0.3, 0.7, hN) * (1.0 - smoothstep(0.85, 1.25, hN));   // v44.21 · humo de la simulación, se disipa antes del corte
        vec3 sc2 = smokeShade(vec3(0.22, 0.21, 0.23), clamp(sn * 1.4 - 0.2, 0.0, 1.0), sm);
        if (uMono > 0.5) sc2 = vec3(luma(sc2));
        col = mix(col, sc2, clamp((1.0 - exp(-sm * 2.2)) * smokeAmt * 0.85 * area, 0.0, 0.8));
    }
    // Luz emitida sobre lo que rodea al fuego (más fuerte cerca de la base).
    col += glowC * smoothstep(-0.35, 0.05, fuel) * smoothstep(-0.08, 0.04, hN) * (1.0 - smoothstep(0.0, 1.1, hN)) * 0.20 * k;
    if (uMono > 0.5) flameC = vec3(luma(flameC));
    // Núcleo opaco + emisión aditiva: visible sobre cielos claros y oscuros.
    col = mix(col, flameC, clamp(alpha * k * (0.85 + 0.25 * temp), 0.0, 1.0));
    col += flameC * alpha * 0.18 * k;
    // Chispas: brasas que suben girando, con halo y parpadeo (velocidad entera: sin saltos).
    float sparkAmt = clamp(uFlameExtra.x, 0.0, 1.0);
    if (sparkAmt > 0.01 && hN > 0.0) {
        vec2 g = vec2(P.x * 26.0 / sc, (box.w - uv.y) * 15.0 / sc - uT * cyc * 30.0);
        vec2 id = floor(g);
        vec2 h = hash22(vec2(id.x, mod(id.y, 30.0 * cyc)) + 3.0);
        vec2 fp = fract(g) - vec2(0.2 + 0.6 * h.x, 0.5);
        fp.x += sin(TAU * (uT * cyc * 2.0 + h.y)) * 0.16;
        float dd = length(fp * vec2(1.0, 0.55));
        float sp = ((1.0 - smoothstep(0.0, 0.05, dd)) + 0.35 * (1.0 - smoothstep(0.0, 0.16, dd))) * step(0.84, h.y);
        float flick = 0.55 + 0.45 * sin(TAU * (uT * cyc * 4.0 + h.x));
        vec3 spC = fireRamp(0.8, base);
        if (uMono > 0.5) spC = vec3(luma(spC));
        col += spC * sp * flick * smoothstep(0.15, 0.5, hN) * (1.0 - smoothstep(0.85, 1.25, hN)) * sparkAmt * 1.3 * area;
    }
    if (shape > 2.5 && shape < 3.5) {
        // chispas mágicas que suben (velocidad entera: cierran el bucle)
        vec2 g = P * 24.0 + vec2(0.0, uT * cyc * 24.0);
        vec2 id = floor(g);
        vec2 h = hash22(vec2(id.x, mod(id.y, 24.0)) + 7.0);
        float sp = (1.0 - smoothstep(0.02, 0.09, length(fract(g) - h))) * step(0.8, h.x);
        col += mix(base, vec3(1.0), 0.7) * sp * smoothstep(-0.3, 0.1, fuel) * k;
    }
    return col;
}
#endif

// ---------------- Energía y auras ----------------
// Halo graduado alrededor de la capa Proteger (personaje): media de 16 muestras en
// dos anillos. Da una caída suave hacia fuera que el ruido convierte en llamas.
float silhouetteGlow(vec2 uv, float radius) {
    float acc = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398 + 0.39;
        vec2 o = vec2(cos(a) / max(uAspect, 0.01), sin(a)) * radius;
        acc += maskLayer(uv + o, 0) + maskLayer(uv + o * 0.45, 0);
    }
    return acc / 16.0;
}

// Compatibilidad: anillo duro (se usa en el contorno de la variante batalla).
float silhouetteRing(vec2 uv, float radius) {
    return clamp(silhouetteGlow(uv, radius) * 1.6 - maskLayer(uv, 0), 0.0, 1.0);
}

#ifdef FX_ENERGY
vec3 zetaEnergy(vec3 col, vec2 P, vec2 uv, float g) {
    float shape = floor(uEnergyShape.x + 0.5);
    float style = floor(uLightStyles.z + 0.5);
    vec3 ec = vec3(0.28, 0.62, 1.0);
    if (style > 0.5 && style < 1.5) ec = vec3(0.78, 0.38, 1.0);
    else if (style > 1.5 && style < 2.5) ec = vec3(1.0, 0.78, 0.25);
    else if (style > 2.5) ec = vec3(1.0, 0.29, 0.34);
    ec = tintOr(ec, uTintEnergy);
    float cyc = max(1.0, uEnergy.z);
    float k = uEnergy.x * g;
    if (k <= 0.001) return col;
    if (shape < 0.5) {
        col += ec * energyField(P, uEnergy.y, cyc) * k;
        return col;
    }
    if (shape > 1.5 && shape < 2.5) {
        // Anillos de poder que se expanden desde el origen.
        vec2 q = P - uMotionEnergy.zw * vec2(uAspect, 1.0);
        float d = length(q);
        float R = mix(0.2, 0.9, clamp(uEnergyShape.y, 0.0, 1.0));
        float rings = 0.0;
        for (int i = 0; i < 3; i++) {
            float r = fract(uT * cyc + float(i) / 3.0) * R;
            float th = 0.008 + 0.02 * (1.0 - r / R);
            rings += (1.0 - smoothstep(th * 0.3, th, abs(d - r))) * (1.0 - r / R);
        }
        col += ec * rings * k;
        return col;
    }
    // Aura alrededor del personaje pintado en Proteger: llamas que suben desde la silueta.
    float radius = mix(0.012, 0.05, clamp(uEnergyShape.y, 0.0, 1.0));
    float m0 = maskLayer(uv, 0);
    float near = silhouetteGlow(uv, radius);
    float far = silhouetteGlow(uv + vec2(0.0, radius * 1.6), radius * 2.2);   // desplazada: el aura sube
    // v44.16: nada DENTRO del personaje (antes quedaban manchas del color del aura sobre él).
    float field = clamp(near * 0.9 + far * 0.8, 0.0, 1.2) * (1.0 - smoothstep(0.35, 0.85, m0));
    if (field <= 0.001) return col;
    float turb = clamp(uEnergyShape.z, 0.0, 1.0);
    float progress = fract(uT * cyc);
    if (shape > 2.5) { float st = max(4.0, floor(uLoopInfo.x * 12.0)); progress = fract(floor(uT * st) / st * cyc); }
    vec2 q = vec2(P.x * 14.0, P.y * 5.0);
    vec2 up = vec2(0.0, 2.2);
    float wa = vnoise(q * 0.7 + up * progress) - 0.5, wb = vnoise(q * 0.7 + up * (progress - 1.0)) - 0.5;
    float na = fbm3(q + up * progress + vec2(wa * turb * 2.0, 0.0));
    float nb = fbm3(q + up * (progress - 1.0) + vec2(wb * turb * 2.0, 0.0));
    float n = contrastFix(mix(na, nb, progress), progress);
    float v = field + (n - 0.5) * 1.1;
    float flame = smoothstep(0.40, 0.50, v);
    float hot = smoothstep(0.66, 0.72, v);
    // v44.16: el aura se PINTA encima (no solo suma luz): así se ve también sobre fondos
    // blancos de manga, donde sumar luz no cambiaba nada. En fondos oscuros queda igual.
    col += ec * clamp(field, 0.0, 1.0) * 0.12 * k;
    col = mix(col, ec * 1.08, clamp(flame * 0.78 * k, 0.0, 1.0));
    col = mix(col, mix(ec, vec3(1.0), 0.65), clamp(hot * k * (shape > 2.5 ? 1.0 : 0.55), 0.0, 1.0));
    if (shape > 2.5) {
        float edge = smoothstep(0.37, 0.40, v) - smoothstep(0.42, 0.45, v);
        col = mix(col, vec3(0.02), clamp(edge * k * 0.8, 0.0, 1.0));   // contorno de tinta (estilo batalla)
    }
    return col;
}
#endif

// ---------------- Ojos brillantes ----------------
#ifdef FX_EYES
vec3 zetaEyes(vec3 col, vec2 uv) {
    float shape = floor(uEyesShape.x + 0.5);
    float cyc = max(1.0, uFxEyes.z);
    float pulse = 0.65 + 0.35 * sin(TAU * uT * cyc);
    if (shape > 0.5 && shape < 1.5) pulse = 0.25 + 0.75 * pow(0.5 + 0.5 * sin(TAU * uT * cyc), 3.0);
    vec3 c = tintOr(vec3(0.20, 0.68, 1.0), uTintEyes);
    float core = maskValue(uv, uMaskEyes, maskLayer(uv, 1), 1.0);
    float haloR = mix(0.004, 0.03, clamp(uEyesShape.y, 0.0, 1.0));
    float halo = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        vec2 o = vec2(cos(a) / max(uAspect, 0.01), sin(a)) * haloR;
        halo += maskValue(uv + o, uMaskEyes, maskLayer(uv + o, 1), 1.0);
    }
    halo /= 8.0;
    float streak = 0.0;
    if (shape > 1.5) {
        for (int i = 1; i <= 6; i++) {
            float fi = float(i);
            vec2 o = vec2(fi * haloR * 1.8 / max(uAspect, 0.01), 0.0);
            streak += (maskValue(uv + o, uMaskEyes, maskLayer(uv + o, 1), 1.0) +
                maskValue(uv - o, uMaskEyes, maskLayer(uv - o, 1), 1.0)) * (1.0 - fi / 7.0);
        }
        streak *= 0.22;
    }
    float k = uFxEyes.x * uFxEyes.y * pulse;
    col += c * (core * 0.9 + halo * 0.8 + streak) * k;
    col = mix(col, mix(c, vec3(1.0), 0.7), clamp(core * k * 0.35, 0.0, 0.6));
    return col;
}
#endif

// Profundidad de un punto de la imagen para el 2.5D (cerca = +1, lejos = -diferencia).
// v45: con IA, la profundidad sale del mapa (dilatado hacia lo cercano para que el
// estiramiento caiga en el fondo) y se reparte en 2-4 capas; Zona verde = Acercar y
// Zona azul = Alejar pasan a ser pinceles de corrección sobre el mapa.
float depthQuant(float d) {
    float L = floor(uDepthInfo.y + 0.5);
    if (L < 1.5) return d;
    float x = clamp(d, 0.0, 1.0) * (L - 1.0);
    float k = min(floor(x), L - 2.0);
    return (k + smoothstep(0.42, 0.58, x - k)) / (L - 1.0);
}
float aiDepth(vec2 fu, float r) {
    vec2 rx = vec2(r / max(uAspect, 0.01), 0.0), ry = vec2(0.0, r);
    float d = texture(uDepth, fu).r;
    d = max(d, max(texture(uDepth, fu + rx).r, texture(uDepth, fu - rx).r));
    d = max(d, max(texture(uDepth, fu + ry).r, texture(uDepth, fu - ry).r));
    return d;
}
float parallaxDepth(vec2 fu) {
    float protect = maskLayer(fu, 0);
    float r = max(0.0005, uParallaxStyle.z);
    vec2 rx = vec2(r / max(uAspect, 0.01), 0.0), ry = vec2(0.0, r);
    float front = maskLayer(fu, 2);
    front = max(front, max(maskLayer(fu + rx, 2), maskLayer(fu - rx, 2)));
    front = max(front, max(maskLayer(fu + ry, 2), maskLayer(fu - ry, 2)));
    float back = maskLayer(fu, 1);
    if (uDepthInfo.x > 0.5) {
        float d = depthQuant(aiDepth(fu, r));
        d += uDepthInfo.z * (front - back);
        d = clamp(d, 0.0, 1.0);
        return (d * (1.0 + uParallax.w) - uParallax.w) * (1.0 - protect);
    }
    // v44.24: profundidad automática pedida pero el mapa aún se calcula: relieve provisional de
    // suelo (abajo cerca, arriba lejos) para que el 2.5D nunca se vea quieto mientras tanto.
    if (uDepthInfo.x > 0.1) {
        float d = clamp(fu.y + uDepthInfo.z * (front - back), 0.0, 1.0);
        return (d * (1.0 + uParallax.w) - uParallax.w) * (1.0 - protect);
    }
    return (front - back * uParallax.w) * (1.0 - protect);
}

// ---------------- v44: efectos nuevos y estilos ----------------
// Todos usan fases ENTERAS del bucle o flujo de dos fases: el último cuadro empalma con el primero.

// Campo de ruido que AVANZA en dirección dir (unidades por ciclo) y cierra el bucle:
// dos copias desfasadas medio ciclo y mezcladas (flow map) con corrección de contraste.
float flowFbm(vec2 q, vec2 dir, float cyc) {
    float p1 = fract(uT * cyc), p2 = fract(p1 + 0.5);
    float w1 = 1.0 - abs(2.0 * p1 - 1.0);
    float a = fbm(q - dir * (p1 - 0.5));
    float b = fbm(q - dir * (p2 - 0.5) + vec2(5.3, 1.7));
    return contrastFix(mix(b, a, w1), w1);
}

#ifdef FX_SAND
// Tormenta de arena: velo que avanza con el viento, vetas rápidas, granos y escena teñida.
vec3 sandLayer(vec3 col, vec2 P, float m) {
    float k = uFxSand.x * m;
    if (k <= 0.001) return col;
    float w = windOf(uMotionSand);
    vec2 dir = normalize(uMotionSand.xy + vec2(1e-5, 0.0));
    vec2 side = vec2(-dir.y, dir.x);
    float style = floor(uSandMore.x + 0.5);
    vec3 sc = vec3(0.86, 0.70, 0.46);
    if (style > 0.5 && style < 1.5) sc = vec3(0.70, 0.68, 0.64);
    else if (style > 1.5) sc = vec3(0.84, 0.48, 0.27);
    if (uMono > 0.5) sc = vec3(luma(sc));
    float cyc = max(1.0, uFxSand.z);
    vec2 q = vec2(dot(P, dir) * 1.1, dot(P, side) * 3.2);
    float haze = flowFbm(q * 1.4, vec2(2.4 * w, 0.0), cyc);
    float veil = smoothstep(0.30, 0.85, haze) * clamp(uSandMore.y, 0.0, 1.0);
    float streak = smoothstep(0.56, 0.86, flowFbm(vec2(q.x * 3.0, q.y * 9.0), vec2(6.0 * w, 0.0), cyc * 2.0));
    vec2 op = oriented(P, uMotionSand);
    float grains = (rainLayer(op, 60.0, cyc * 3.0 + 1.0, 0.0, 5.0) * 0.8 +
                    rainLayer(op, 110.0, cyc * 4.0 + 1.0, 0.0, 23.0) * 0.5) * clamp(uFxSand.y, 0.0, 1.0);
    // la luz filtrada por la arena: menos contraste y todo teñido
    vec3 tinted = mix(vec3(luma(col)), col, 0.55) * mix(vec3(1.0), sc * 1.25, 0.6);
    col = mix(col, tinted, clamp(k * 0.55, 0.0, 0.8));
    col = mix(col, sc * (0.92 + 0.16 * haze), clamp((veil * 0.65 + streak * 0.25) * k * min(w, 1.4), 0.0, 0.85));
    col = mix(col, sc * 1.15, clamp(grains * k * 0.7, 0.0, 0.8));
    return col;
}
#endif

#ifdef FX_CLOUDSHADOW
// Sombras de nubes que pasan: oscurecen y enfrían; lo que queda al sol se aclara un poco.
vec3 cloudShadowLayer(vec3 col, vec2 Q, float m) {
    float k = uFxCloudshadow.x * m;
    if (k <= 0.001) return col;
    vec2 dir = normalize(uMotionCloudshadow.xy + vec2(1e-5, 0.0));
    float w = windOf(uMotionCloudshadow);
    vec2 side = vec2(-dir.y, dir.x);
    vec2 q = vec2(dot(Q, dir), dot(Q, side)) * (1.6 / max(uFxCloudshadow.y, 0.3));
    float n = flowFbm(q, vec2(1.6 * w, 0.0), max(1.0, uFxCloudshadow.z));
    float soft = clamp(uCloudshadowMore.x, 0.0, 1.0);
    float sh = smoothstep(0.50 - 0.18 * soft, 0.56 + 0.10 * soft, n);
    vec3 shade = col * mix(vec3(1.0), vec3(0.55, 0.60, 0.72), k);
    if (uMono > 0.5) shade = col * (1.0 - 0.45 * k);
    vec3 lit = col * (1.0 + 0.10 * k);
    return mix(lit, shade, sh);
}
#endif

#ifdef FX_GLASSRAIN
// Gotas en el cristal de la cámara (espacio de PANTALLA: no se mueven con la cámara).
// Devuelve (desplazamiento de la lente xy, cobertura de gota o rastro, borde de la gota).
vec4 glassDrops(vec2 P) {
    float sz = max(0.4, uFxGlassrain.y);
    float cyc = max(1.0, uFxGlassrain.z);
    vec2 off = vec2(0.0);
    float cover = 0.0, rim = 0.0;
    // 1) gotas quietas que aparecen, crecen y se secan (dos rejillas)
    for (int L = 0; L < 2; L++) {
        float fl = float(L);
        vec2 g = P * (14.0 + 9.0 * fl) / sz + fl * 7.31;
        vec2 id = floor(g), f = fract(g) - 0.5;
        vec2 h = hash22(id + fl * 13.7);
        float life = fract(uT * cyc + h.x);
        // v44: ~45 % de las celdas con gota (antes 70 %): se lee como lluvia en el cristal, no como burbujas.
        float r = (0.16 + 0.22 * h.y) * smoothstep(0.0, 0.12, life) * (1.0 - smoothstep(0.80, 1.0, life)) * step(0.55, h.y);
        vec2 dv = f - (h - 0.5) * 0.45;
        float d = length(dv * vec2(1.0, 0.92));
        float drop = 1.0 - smoothstep(r * 0.82, r, d);
        if (drop > cover) {
            off = dv / max(r, 0.01) * drop;
            rim = smoothstep(r * 0.55, r, d) * drop;
        }
        cover = max(cover, drop);
    }
    // 2) gotas que bajan serpenteando y dejan un rastro limpio en el vaho
    float tr = clamp(uGlassrainMore.y, 0.0, 1.0);
    if (tr > 0.0) {
        float cols = 7.0 / sz;
        float gx = P.x * cols;
        float cid = floor(gx);
        float fx = fract(gx) - 0.5;
        float h1 = hash12(vec2(cid, 4.7)), h2 = hash12(vec2(cid, 9.1));
        if (h1 < 0.55 * tr + 0.15) {
            float kk = 1.0 + floor(h2 * 2.0);                   // 1 o 2 bajadas por ciclo
            float y = fract(uT * cyc * kk + h2) * 1.8 - 0.2;    // entra arriba y sale del todo abajo
            float wig = 0.12 * sin(P.y * 23.0 + h1 * 6.0) + 0.06 * sin(P.y * 57.0 + h2 * 9.0);
            float x = fx - wig;
            float rr = 0.17 + 0.08 * h2;
            vec2 dv = vec2(x, (P.y - y) * cols * 0.85);
            float d = length(dv);
            float drop = 1.0 - smoothstep(rr * 0.8, rr, d);
            float above = step(P.y, y) * smoothstep(y - 0.35, y, P.y);
            float line = (1.0 - smoothstep(0.03, 0.06, abs(x))) * above;
            float by = P.y * cols * 2.5 + h1;
            float bead = (1.0 - smoothstep(0.05, 0.09, length(vec2(x, fract(by) - 0.5) * vec2(1.0, 0.6)))) * above *
                         step(0.4, hash12(vec2(cid, floor(by))));
            float trail = max(line * 0.6, bead);
            if (drop > cover) { off = dv / rr * drop; rim = smoothstep(rr * 0.5, rr, d) * drop; }
            cover = max(cover, max(drop, trail * 0.8));
        }
    }
    return vec4(off, cover, rim);
}
#endif

#ifdef FX_BIRDS
// Un pájaro estilizado (alas en arco y cuerpo). q en unidades de imagen, s = media envergadura.
float birdShape(vec2 q, float flap, float s) {
    q /= s;
    q.x = abs(q.x);
    if (q.x > 1.15 || abs(q.y) > 1.0) return 0.0;
    float tipY = -flap * 0.55;
    float yWing = tipY * pow(clamp(q.x, 0.0, 1.0), 1.3) - 0.10 * sin(3.14159 * clamp(q.x, 0.0, 1.0)) * flap;
    float thick = 0.11 * (1.0 - 0.7 * clamp(q.x, 0.0, 1.0));
    float wing = (1.0 - smoothstep(thick * 0.55, thick, abs(q.y - yWing))) * (1.0 - smoothstep(0.95, 1.1, q.x));
    float body = 1.0 - smoothstep(0.07, 0.12, length(q * vec2(1.0, 1.7)));
    return max(wing, body);
}

// Bandada que cruza la imagen (entra y sale fuera de cuadro: el bucle no se nota).
vec3 birdsLayer(vec3 col, vec2 P, float m) {
    float k = uFxBirds.x * m;
    if (k <= 0.001) return col;
    vec2 dir = normalize(uMotionBirds.xy + vec2(1e-5, 0.0));
    vec2 side = vec2(-dir.y, dir.x);
    vec2 O = uMotionBirds.zw * vec2(uAspect, 1.0);
    float cyc = max(1.0, uFxBirds.z);
    // Recorrido: entra y sale justo por los bordes, así la bandada se ve casi todo el bucle.
    float half_ = 0.5 * uAspect + 0.22 * max(0.3, uBirdsMore.x) + 0.08;
    vec2 C = O + dir * (fract(uT * cyc) - 0.5) * 2.0 * half_;
    float sz = max(0.3, uBirdsMore.x);
    float flaps = max(1.0, floor(uLoopInfo.x * 2.5 + 0.5));
    float acc = 0.0;
    int n = int(clamp(uFxBirds.y, 1.0, 12.0));
    for (int i = 0; i < 12; i++) {
        if (i >= n) break;
        float fi = float(i);
        vec2 h = hash22(vec2(fi, 3.3));
        float lane = (mod(fi, 2.0) < 0.5 ? 1.0 : -1.0) * ceil(fi * 0.5);
        vec2 off = (-dir * abs(lane) * 0.055 + side * lane * 0.045 + (h - 0.5) * 0.06) * sz;
        off += vec2(sin(TAU * (uT * 2.0 + h.x)), cos(TAU * (uT * 3.0 + h.y))) * 0.010 * sz;
        vec2 q = P - (C + off);
        float s = 0.02 * sz * (0.8 + 0.4 * h.x);
        if (dot(q, q) > s * s * 2.0) continue;
        float glide = 0.35 + 0.65 * smoothstep(-0.2, 0.4, sin(TAU * (uT + h.x)));
        float flap = sin(TAU * (uT * flaps + h.y));
        acc = max(acc, birdShape(q, flap * glide, s));
    }
    vec3 bc = uBirdsMore.y > 0.5 ? vec3(0.96, 0.96, 0.94) : vec3(0.06, 0.06, 0.08);
    return mix(col, bc, clamp(acc * k, 0.0, 1.0));
}
#endif

#ifdef FX_AURORA
// Aurora: dos cortinas que ondulan, con estrías verticales que corren de lado.
vec3 auroraLayer(vec3 col, vec2 Q, float m) {
    float k = uFxAurora.x * m;
    if (k <= 0.001) return col;
    float ph = TAU * uT * max(1.0, uFxAurora.z);
    float x = Q.x;
    float style = floor(uAuroraMore.x + 0.5);
    vec3 cLow = vec3(0.25, 1.0, 0.55), cHigh = vec3(0.30, 0.50, 1.0);
    if (style > 0.5 && style < 1.5) { cLow = vec3(0.78, 0.38, 1.0); cHigh = vec3(0.30, 0.30, 1.0); }
    else if (style > 1.5) { cLow = vec3(0.30, 1.0, 0.60); cHigh = vec3(0.95, 0.30, 0.75); }
    vec3 glow = vec3(0.0);
    for (int b = 0; b < 2; b++) {
        float fb = float(b);
        float yc = uAuroraMore.y + fb * 0.07 + 0.06 * sin(x * 2.3 + ph + fb * 2.0)
                 + 0.035 * sin(x * 5.1 - ph * 2.0 + fb) + 0.02 * sin(x * 11.0 + ph * 3.0);
        float L = 0.22 * uFxAurora.y * (1.0 - 0.35 * fb);
        float dy = yc - Q.y;                         // > 0: por encima del borde inferior
        float body = smoothstep(-0.015, 0.01, dy) * exp(-max(dy, 0.0) / max(L, 0.02) * 2.2);
        float rays = vnoise(vec2(x * 38.0 + 3.0 * sin(ph + x * 2.0) + fb * 17.0, 0.5)) * 0.6
                   + vnoise(vec2(x * 90.0 - 2.0 * sin(ph * 2.0 + x), 3.5)) * 0.4;
        rays = 0.35 + 0.9 * rays * rays;
        float band = body * rays * (0.75 + 0.25 * sin(ph + x * 4.0 + fb * 3.0));
        glow += mix(cLow, cHigh, clamp(max(dy, 0.0) / max(L, 0.02), 0.0, 1.0)) * band * (1.0 - 0.3 * fb);
    }
    if (uMono > 0.5) glow = vec3(luma(glow)) * 1.4;
    float dark = 1.0 - smoothstep(0.35, 0.9, luma(col));   // se ve de noche; sobre blanco casi nada
    return col + glow * k * (0.35 + 0.65 * dark);
}
#endif

#ifdef FX_CITYLIGHTS
// Luces de ciudad: usa las luces que YA hay en la imagen (píxeles claros sobre entorno oscuro).
vec3 cityLightsLayer(vec3 col, vec2 uv, vec2 Q, float m) {
    float k = uFxCitylights.x * m;
    if (k <= 0.001) return col;
    float cyc = max(1.0, uFxCitylights.z);
    float style = floor(uCitylightsMore.x + 0.5);
    vec2 cu = clamp(uv, vec2(0.0), vec2(1.0));
    vec3 src = sourcePixel(cu);
    float L = luma(src);
    vec3 around = texture(uImage, cu, 3.0).rgb;            // entorno (mipmap = desenfoque barato)
    float light = smoothstep(0.42, 0.70, L) * smoothstep(0.02, 0.20, L - luma(around));
    vec2 cell = floor(cu * uTexSize / 6.0);
    float h = hash12(cell + 7.7);
    float steps = max(2.0, floor(uLoopInfo.x * 1.5) * cyc);
    float tick = floor(uT * steps);
    float state = step(h, clamp(uFxCitylights.y, 0.0, 1.0) * 0.5) > 0.5 ? step(0.45, hash12(cell + tick * 1.37)) : 1.0;
    float flick = sin(TAU * (uT * cyc * 3.0 + h * 7.0));
    vec3 halo = texture(uImage, cu, 4.5).rgb;
    float haloL = smoothstep(0.20, 0.65, luma(halo));
    vec3 outc = col;
    if (style < 0.5) {
        outc = mix(col, col * 0.22, light * (1.0 - state) * k);          // ventanas que se apagan
        outc += src * light * state * flick * 0.12 * k;                 // titileo suave
    } else if (style < 1.5) {
        float hue = fract(h + floor(uT * cyc * 2.0) * 0.37);
        vec3 neon = 0.5 + 0.5 * cos(TAU * (hue + vec3(0.0, 0.33, 0.67)));
        neon = mix(neon, vec3(1.0, 0.25, 0.75), 0.3);
        outc = mix(col, neon * (0.7 + 0.6 * L), clamp(light * k * 0.8 * state, 0.0, 1.0));
        halo = mix(halo, neon * luma(halo) * 2.0, 0.6);
    } else {
        // Tráfico: faros blancos en un carril y luces rojas en el otro, a la altura del origen.
        float yR = uMotionCitylights.w;
        float dirS = uMotionCitylights.x >= 0.0 ? 1.0 : -1.0;
        float per = 12.0 * cyc;
        for (int lane = 0; lane < 2; lane++) {
            float fl = float(lane);
            float ly = yR + (fl - 0.5) * 0.028;
            float sgn = (fl < 0.5 ? 1.0 : -1.0) * dirS;
            float g = Q.x * 6.0 - sgn * uT * per;
            float id = floor(g);
            float fx = fract(g) - 0.5;
            float hh = hash12(vec2(mod(id, per), 5.0 + fl));
            float on = step(0.35, hh);
            vec2 dv = vec2(fx, (Q.y - ly) * 6.0);
            float core = exp(-dot(dv, dv) * 900.0);
            float trail = exp(-abs(dv.y) * 160.0) * exp(-max(0.0, -sgn * fx) * 9.0) * step(0.0, -sgn * fx) * 0.5;
            vec3 lc = fl < 0.5 ? vec3(1.0, 0.95, 0.80) : vec3(1.0, 0.18, 0.12);
            if (uMono > 0.5) lc = vec3(1.0);
            outc += lc * (core * 1.6 + trail) * on * k;
        }
    }
    vec3 glowC = uMono > 0.5 ? vec3(luma(halo)) : halo;
    outc += glowC * haloL * clamp(uCitylightsMore.y, 0.0, 1.0) * 0.45 * k;
    return outc;
}
#endif

#ifdef FX_LENSFLARE
// Destello de lente desde la fuente (origen): brillo, estrella, halo y fantasmas hacia el centro.
vec3 lensFlareLayer(vec3 col, vec2 P, float m) {
    float k = uFxLensflare.x * m;
    if (k <= 0.001) return col;
    vec2 A = vec2(uAspect, 1.0);
    vec2 S = uMotionLensflare.zw * A;
    vec2 v = 0.5 * A - S;
    float sz = max(0.3, uFxLensflare.y);
    float ph = TAU * uT * max(1.0, uFxLensflare.z);
    float style = floor(uLensflareMore.x + 0.5);
    vec3 base = (style > 0.5 && style < 1.5) ? vec3(0.62, 0.80, 1.0) : vec3(1.0, 0.82, 0.55);
    vec3 add = vec3(0.0);
    vec2 d0 = P - S;
    float r0 = length(d0);
    add += base * exp(-r0 * 18.0 / sz) * 1.2;
    float ang = atan(d0.y, d0.x) + 0.05 * sin(ph);
    float star = pow(abs(cos(ang * 3.0)), 60.0) + 0.6 * pow(abs(cos(ang * 3.0 + 0.52)), 120.0);
    add += base * star * exp(-r0 * 6.0 / sz) * 0.7;
    add += vec3(0.6, 0.85, 1.0) * exp(-pow((r0 - 0.22 * sz) * 30.0, 2.0)) * 0.22;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec2 gp = S + v * (0.45 + fi * 0.38);
        float rr = (0.025 + 0.05 * fract(fi * 0.618 + 0.3)) * sz;
        float dg = length(P - gp);
        float disc = 1.0 - smoothstep(rr * 0.7, rr, dg);
        float edge = exp(-pow((dg - rr) * 90.0 / sz, 2.0)) * 0.5;
        vec3 gc = 0.55 + 0.45 * cos(TAU * (fi * 0.21 + vec3(0.0, 0.33, 0.67)));
        add += gc * (disc * 0.10 + edge * 0.06);
    }
    if (style > 1.5) add += vec3(0.45, 0.65, 1.0) * exp(-abs(d0.y) * 140.0 / sz) * exp(-abs(d0.x) * 1.6 / sz) * 0.9;
    if (uMono > 0.5) add = vec3(luma(add));
    add *= (0.9 + 0.1 * sin(ph)) * k;
    return 1.0 - (1.0 - col) * (1.0 - clamp(add, 0.0, 1.0));   // mezcla «pantalla»
}
#endif

// ---------------- v44.11 · color propio de cada efecto ----------------
// Se aplica SOLO a lo que el efecto añadió (diferencia entre antes y después), nunca a la imagen.
// «Modo manga» (uMono) pasa todos los efectos a blanco y negro.
vec3 fxGrade(vec3 before, vec3 after, vec4 g) {
    float mode = max(floor(g.x + 0.5), uMono > 0.5 ? 1.0 : 0.0);
    if (mode < 0.5 && abs(g.y) + abs(g.z) + abs(g.w) < 0.001) return after;
    vec3 d = after - before;
    float w = clamp(length(d) * 5.0, 0.0, 1.0);          // cuánto efecto hay en este píxel
    if (w <= 0.0) return after;
    vec3 r = after;
    if (mode > 0.5) {
        float l = luma(after);
        r = mix(after, mode > 1.5 ? l * vec3(1.07, 0.98, 0.82) : vec3(l), w);
    }
    vec3 dr = r - before;
    dr = mix(vec3(luma(dr)), dr, 1.0 + g.y);              // saturación del efecto
    r = before + dr;
    r = mix(r, (r - 0.5) * (1.0 + g.w) + 0.5, w);         // contraste del efecto
    r += vec3(g.z * 0.35 * w);                            // brillo del efecto
    return r;
}

#ifdef FX_SYMBOLS
// v44.11 · Símbolos manga dibujados con tinta: gota de sudor, vena de enojo, «!» y estrellas de
// mareo. Se colocan con Mover; se animan en bucle exacto (fases enteras de uT).
float symSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
float symStar(vec2 p, float r) {
    float a = atan(p.y, p.x);
    float k = 0.55 + 0.45 * cos(5.0 * a);
    return length(p) - r * (0.5 + 0.5 * k);
}
// Pinta una figura de distancia d (<0 dentro) con relleno y contorno de tinta.
vec3 symInk(vec3 col, float d, vec3 fill, vec3 ink, float edge, float k) {
    float aa = 0.035;
    float outl = 1.0 - smoothstep(edge, edge + aa, d);
    float inner = 1.0 - smoothstep(0.0, aa, d);
    col = mix(col, ink, outl * k);
    return mix(col, fill, inner * k);
}
vec3 symbolsLayer(vec3 col, vec2 P, float m) {
    float k = clamp(uFxSymbols.x, 0.0, 1.0) * m;
    if (k <= 0.001) return col;
    vec2 A = vec2(uAspect, 1.0);
    float sz = 0.11 * max(0.3, uFxSymbols.y);
    float ph = TAU * uT * max(1.0, floor(uFxSymbols.z + 0.5));
    float style = floor(uSymbolsMore.x + 0.5);
    vec2 q = (P - uMotionSymbols.zw * A) / sz;
    vec3 ink = vec3(0.04);
    if (style < 0.5) {
        // Gota de sudor: resbala un poco y se estira al caer.
        q.y -= 0.10 * sin(ph);
        q.x /= 1.0 - 0.06 * sin(ph);
        float t = clamp((q.y + 0.62) / 1.1, 0.0, 1.0);
        float wdt = 0.36 * pow(t, 0.75) * sqrt(clamp(1.0 - pow(max(t - 0.6, 0.0) / 0.4, 2.0), 0.0, 1.0));
        float d = (q.y < -0.62 || q.y > 0.48) ? 1.0 : abs(q.x) - wdt;
        d = max(d, -0.62 - q.y);
        col = symInk(col, d, vec3(0.74, 0.9, 1.0), ink, 0.07, k);
        float hl = length((q - vec2(0.1, 0.12)) * vec2(1.6, 0.9)) - 0.07;   // brillo
        col = mix(col, vec3(1.0), (1.0 - smoothstep(0.0, 0.03, hl)) * k);
    } else if (style < 1.5) {
        // Vena de enojo: cuatro arcos que laten.
        float s = 1.0 + 0.14 * pow(max(0.0, sin(ph)), 2.0);
        vec2 p = q / s;
        float d = 1.0;
        for (int i = 0; i < 4; i++) {
            vec2 sg = vec2(i == 0 || i == 3 ? 1.0 : -1.0, i < 2 ? 1.0 : -1.0);
            vec2 c = sg;   // arco interior de un círculo grande: forma de 💢
            float inQ = step(0.1, p.x * sg.x) * step(0.1, p.y * sg.y) * step(length(p), 0.95);
            float arc = abs(length(p - c) - 0.8) - 0.09;
            d = min(d, mix(1.0, arc, inQ));
        }
        col = symInk(col, d, vec3(0.86, 0.08, 0.14), ink, 0.06, k);
    } else if (style < 2.5) {
        // «!»: aparece con un saltito y tiembla un poco.
        float pop = 1.0 + 0.12 * max(0.0, sin(ph));
        vec2 p = q / pop + vec2(0.02 * sin(ph * 3.0), 0.0);
        float taper = mix(0.07, 0.15, clamp((0.15 - p.y) / 0.75, 0.0, 1.0));
        float bar = symSeg(p, vec2(0.0, -0.62), vec2(0.0, 0.1)) - taper;
        float dotD = length(p - vec2(0.0, 0.36)) - 0.12;
        col = symInk(col, min(bar, dotD), ink, vec3(1.0), 0.07, k);
    } else {
        // Mareo: tres estrellas que giran en una elipse.
        for (int i = 0; i < 3; i++) {
            float a = ph + TAU * float(i) / 3.0;
            vec2 c = vec2(cos(a) * 0.8, sin(a) * 0.28);
            float front = 0.75 + 0.25 * sin(a);
            float d = symStar((q - c) / front, 0.22) * front;
            col = symInk(col, d, vec3(1.0, 0.86, 0.2), ink, 0.05, k);
        }
    }
    return col;
}
#endif

#ifdef FX_GLOWUP
// v44.11 · Brillo manhwa: resplandor de las zonas claras, luces que suben y destellos
// verticales suaves, como en los webtoons. Mezcla «pantalla»: nunca oscurece.
vec3 glowupLayer(vec3 col, vec2 P, vec2 uv, float m) {
    float k = uFxGlowup.x * m;
    if (k <= 0.001) return col;
    float style = floor(uGlowupMore.x + 0.5);
    vec3 tint = style < 0.5 ? vec3(1.0, 0.86, 0.6) : (style < 1.5 ? vec3(1.0, 0.72, 0.86) : vec3(0.68, 0.84, 1.0));
    float cyc = max(1.0, floor(uFxGlowup.z + 0.5));
    vec3 add = vec3(0.0);
    // Resplandor (bloom) de lo claro de la imagen.
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    vec3 b1 = texture(uImage, cuv, 3.0).rgb;
    vec3 b2 = texture(uImage, cuv, 5.0).rgb;
    vec3 bloom = max(b1 - 0.62, 0.0) * 1.4 + max(b2 - 0.55, 0.0) * 1.1;
    add += mix(bloom, luma(bloom) * tint, 0.55) * uFxGlowup.y * (0.85 + 0.15 * sin(TAU * uT * cyc));
    // Luces que suben (patrón periódico en y: el bucle cierra exacto).
    float motes = uGlowupMore.y;
    if (motes > 0.0) {
        for (int L = 0; L < 2; L++) {
            float fl = float(L);
            float sc = 7.0 + fl * 5.0;
            float n = 6.0;
            vec2 g = vec2(P.x * sc, P.y * sc + uT * cyc * n * (1.0 + fl));
            vec2 id = floor(g);
            id.y = mod(id.y, n * (1.0 + fl));
            vec2 f = fract(g) - 0.5;
            vec2 h = hash22(id + fl * 17.0);
            if (h.y < 0.55) {
                vec2 c = (h - 0.5) * 0.6;
                float d = length(f - c);
                float tw = 0.6 + 0.4 * sin(TAU * (uT * cyc * 2.0 + h.x));
                add += tint * (exp(-d * d * 45.0) * 0.8 + exp(-d * d * 400.0)) * tw * motes * (1.0 - fl * 0.3);
            }
        }
        // Destellos verticales finos.
        float lx = P.x * 9.0;
        vec2 hs = hash22(vec2(floor(lx), 3.1));
        float line = exp(-pow((fract(lx) - 0.5) * 30.0, 2.0)) * step(0.7, hs.x);
        float pulse = pow(max(0.0, sin(TAU * (uT * cyc + hs.y))), 6.0);
        add += tint * line * pulse * 0.35 * motes * smoothstep(1.0, 0.2, abs(P.y - hs.y));
    }
    add *= k;
    return 1.0 - (1.0 - col) * (1.0 - clamp(add, 0.0, 1.0));
}
#endif

#ifdef FX_INKWEATHER
// v44.12 · Lluvia y nieve rehechas (antes parecían «palos» gruesos):
// - Lluvia: trazos FINÍSIMOS con estela (cola tenue arriba, cabeza más marcada abajo), tres
//   capas de profundidad (lejos: cortas, densas y tenues; cerca: largas y más visibles) y
//   antialias por píxel. Tinta: negra sobre lo claro y blanca sobre lo oscuro, como en el manga.
// - Nieve: copos suaves de varios tamaños; los cercanos, desenfocados; contorno fino solo en
//   el estilo manga.
// Bucle exacto: cada capa avanza un número entero de celdas que divide su repetición.
float inkStreak(vec2 r, vec2 sc, float speed, float wrap, float seed, float amount, float lenLo, float lenHi,
                float hw, out float head) {
    vec2 g = vec2(r.x * sc.x + seed, r.y * sc.y - uT * speed);
    vec2 id = floor(g);
    id.y = mod(id.y, wrap);
    vec2 f = fract(g);
    vec2 h = hash22(id + seed * 7.0);
    head = 0.0;
    if (hash12(id + seed + 3.1) > amount) return 0.0;
    float lenC = (lenLo + (lenHi - lenLo) * h.y) * sc.y;          // longitud en celdas
    float y0 = (1.0 - lenC) * fract(h.x * 7.31);
    float x0 = 0.15 + 0.7 * h.x;
    float along = (f.y - y0) / max(lenC, 1e-3);
    if (along < 0.0 || along > 1.0) return 0.0;
    float dx = abs(f.x - x0) / sc.x;                               // en unidades de imagen
    float px = max(fwidth(r.x), 1e-5);
    float w = hw * (0.45 + 0.55 * sin(3.14159 * along));           // afinado en las puntas
    float cov = clamp((w * 2.0) / px, 0.12, 1.0);                  // líneas más finas que un píxel
    float line = 1.0 - smoothstep(w, w + px * 1.25, dx);
    head = smoothstep(0.75, 1.0, along);
    return line * cov * mix(0.25, 1.0, along) * smoothstep(0.0, 0.12, along) * (0.55 + 0.45 * h.y);
}
vec3 inkweatherLayer(vec3 col, vec2 P, float m) {
    float k = clamp(uFxInkweather.x, 0.0, 1.0) * m;
    if (k <= 0.001) return col;
    float style = floor(uInkweatherMore.x + 0.5);
    float thick = max(0.4, uInkweatherMore.y);
    float cyc = max(1.0, floor(uFxInkweather.z + 0.5));
    float amount = clamp(uFxInkweather.y, 0.05, 1.0);
    vec2 r = oriented(P, uMotionInkweather);              // y = hacia donde cae
    if (style > 0.5 && style < 1.5) {
        // Nieve: 3 capas, copos suaves; los de delante más grandes y desenfocados.
        for (int L = 0; L < 3; L++) {
            float fl = float(L);
            float n = 4.0;
            float sc = 16.0 - fl * 4.5;
            vec2 g = vec2(r.x * sc + fl * 0.31, r.y * sc - uT * cyc * n * (1.0 + fl));
            vec2 id = floor(g);
            id.y = mod(id.y, n * (1.0 + fl) * 2.0);
            vec2 f = fract(g) - 0.5;
            vec2 h = hash22(id + fl * 31.0);
            if (hash12(id + 9.7 + fl) > amount * (1.1 - fl * 0.25)) continue;
            vec2 c = vec2((h.x - 0.5) * 0.55 + 0.16 * sin(TAU * (uT * cyc + h.x)), (h.y - 0.5) * 0.5);
            float rad = (0.06 + 0.07 * h.y) * thick * (0.7 + fl * 0.35);
            float d = length(f - c) - rad;
            float soft = 0.025 + fl * 0.06;                        // cerca = más desenfocado
            float flake = 1.0 - smoothstep(-soft, soft, d);
            float a = flake * k * (0.55 + fl * 0.2);
            col = mix(col, vec3(1.0), a);
            if (fl < 1.5) {                                        // contorno fino de manga
                float ring = exp(-pow(d / (0.02 * thick), 2.0));
                col = mix(col, vec3(0.08), ring * 0.35 * k);
            }
        }
        return col;
    }
    // Lluvia: 3 capas de profundidad.
    float inkTone = smoothstep(0.32, 0.6, luma(col));             // 1 = fondo claro
    vec3 ink = style < 0.5 ? mix(vec3(0.94), vec3(0.04), inkTone) : vec3(0.86, 0.91, 1.0);
    float acc = 0.0, heads = 0.0;
    for (int L = 0; L < 3; L++) {
        float fl = float(L);
        vec2 sc = vec2(70.0 - fl * 20.0, 4.5 - fl * 1.05);
        float speed = cyc * 8.0 * (1.0 + 0.5 * fl);                 // 8, 12, 16 celdas × ciclos
        float hd;
        float v = inkStreak(r, sc, speed, 4.0, fl * 11.0 + 1.0, amount * (1.0 - fl * 0.28),
            0.04 + fl * 0.03, 0.11 + fl * 0.08, (0.0005 + fl * 0.00045) * thick, hd);
        acc += v * (0.45 + fl * 0.3);
        heads += hd * v;
    }
    acc = clamp(acc, 0.0, 1.0);
    if (style < 0.5) col = mix(col, ink, acc * k * 0.9);
    else {
        // Lluvia realista: estelas claras en mezcla «pantalla» y ambiente húmedo y frío.
        col = mix(col, col * vec3(0.9, 0.94, 1.02), 0.18 * k);
        col = 1.0 - (1.0 - col) * (1.0 - ink * acc * k * 0.75);
        col += ink * clamp(heads, 0.0, 1.0) * 0.15 * k;
    }
    return col;
}
#endif

// ---------------- v44.12 · efectos nuevos para todo tipo de escenas ----------------
#ifdef FX_DAPPLE
// Luz entre hojas: manchas de sol que se mecen con el viento (bosque, calle, ventana).
vec3 dappleLayer(vec3 col, vec2 P, float m) {
    float k = uFxDapple.x * m;
    if (k <= 0.001) return col;
    float style = floor(uDappleMore.x + 0.5);
    vec3 tint = style < 0.5 ? vec3(1.0, 0.84, 0.55) : (style < 1.5 ? vec3(1.0, 0.98, 0.93) : vec3(0.7, 0.82, 1.0));
    float sc = 9.0 / max(0.3, uFxDapple.y);
    float ph = TAU * uT * max(1.0, floor(uFxDapple.z + 0.5));
    float soft = mix(0.04, 0.16, clamp(uDappleMore.y, 0.0, 1.0));
    // Manchas redondas (el sol entra entre las hojas) que se mecen; se agrupan donde el
    // follaje deja huecos (ruido grande) y el resto queda en sombra suave.
    vec2 q = P * sc * vec2(0.8, 1.0);
    vec2 ip = floor(q), fp = fract(q);
    float spot = 0.0;
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec2 o = vec2(float(i), float(j));
        vec2 h = hash22(ip + o + 4.2);
        vec2 c = o + 0.5 + 0.3 * vec2(sin(ph + TAU * h.x), cos(ph * 1.0 + TAU * h.y)) * 0.6;
        float rad = 0.14 + 0.2 * h.y;
        float d = length((fp - c) * vec2(1.0, 1.25));
        spot = max(spot, (1.0 - smoothstep(rad - soft, rad + soft, d)) * step(0.35, h.x));
    }
    float gaps = smoothstep(0.42, 0.62, fbm(P * 2.2 + vec2(sin(ph) * 0.08, cos(ph) * 0.06)));
    float lit = spot * gaps;
    vec3 r = col * (1.0 - 0.16 * (1.0 - lit));                      // sombra suave de las hojas
    r = 1.0 - (1.0 - r) * (1.0 - tint * lit * 0.8);                 // manchas de sol
    return mix(col, r, k);
}
#endif

#ifdef FX_CAUSTICS
// Cáusticas: la red de luz que deja el agua sobre el fondo de una piscina o el mar.
// Red de cáusticas: bordes entre celdas de Voronoi cuyos centros giran en círculo (bucle
// exacto: fases enteras). Dos escalas sumadas, como la luz real en el fondo de una piscina.
float causticNet(vec2 p, float ph, float seed) {
    vec2 ip = floor(p), fp = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec2 o = vec2(float(i), float(j));
        vec2 h = hash22(ip + o + seed);
        vec2 c = o + 0.5 + 0.38 * vec2(sin(ph + TAU * h.x), cos(ph + TAU * h.y));
        float d = length(fp - c);
        if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) f2 = d;
    }
    return 1.0 - smoothstep(0.0, 0.16, f2 - f1);
}
vec3 causticsLayer(vec3 col, vec2 P, float m) {
    float k = uFxCaustics.x * m;
    if (k <= 0.001) return col;
    float style = floor(uCausticsMore.x + 0.5);
    vec3 tint = style < 0.5 ? vec3(0.78, 0.95, 1.0) : (style < 1.5 ? vec3(0.62, 1.0, 0.95) : vec3(1.0, 0.88, 0.55));
    float sc = 7.0 / max(0.3, uFxCaustics.y);
    float ph = TAU * uT * max(1.0, floor(uFxCaustics.z + 0.5));
    vec2 w = vec2(fbm(P * 2.0 + vec2(sin(ph) * 0.2, 0.0)), fbm(P * 2.0 + vec2(3.7, cos(ph) * 0.2))) * 0.6;
    float c = causticNet(P * sc + w, ph, 1.0) * 0.7 + causticNet(P * sc * 1.9 + w * 1.4 + 3.3, -ph, 7.0) * 0.45;
    c = clamp(c, 0.0, 1.0);
    c = c * c;
    vec3 r = mix(col, col * vec3(0.9, 0.97, 1.03), 0.25 * step(style, 1.5));   // tono de agua
    r = 1.0 - (1.0 - r) * (1.0 - tint * c * 0.8);
    return mix(col, r, k);
}
#endif

#ifdef FX_STEAM
// Vapor: columnas suaves que suben desde el origen (taza, ramen, aguas termales, aliento).
vec3 steamLayer(vec3 col, vec2 P, float m) {
    float k = uFxSteam.x * m;
    if (k <= 0.001) return col;
    vec2 A = vec2(uAspect, 1.0);
    vec2 o = uMotionSteam.zw * A;
    float wdt = 0.13 * max(0.3, uFxSteam.y);
    float reach = 0.3 + 0.6 * clamp(uSteamMore.x, 0.0, 1.0);
    float cyc = max(1.0, floor(uFxSteam.z + 0.5));
    vec2 d = P - o;
    float up = -d.y;                                             // hacia arriba (y va hacia abajo)
    if (up < -0.03 || up > reach + 0.1) return col;
    float t = clamp(up / reach, 0.0, 1.0);
    float sway = sin(up * 9.0 - TAU * uT * cyc) * 0.025 * t + sin(up * 4.0 + TAU * uT * cyc) * 0.015 * t;
    float spread = wdt * (0.5 + 1.6 * t);
    float x = (d.x - sway) / spread;
    float body = exp(-x * x * 2.2);
    float n = flowFbm(vec2(d.x * 9.0, up * 6.0), vec2(0.0, 3.0), cyc);
    float wisps = smoothstep(0.28, 0.72, n) * (0.55 + 0.45 * flowFbm(vec2(d.x * 22.0, up * 14.0), vec2(0.0, 6.0), cyc));
    float fade = smoothstep(0.0, 0.08, up + 0.03) * (1.0 - smoothstep(0.55, 1.0, t));
    float a = body * wisps * fade * k;
    vec3 sc = uMono > 0.5 ? vec3(0.95) : vec3(0.96, 0.97, 1.0);
    return mix(col, 1.0 - (1.0 - col) * (1.0 - sc * 0.9), clamp(a, 0.0, 1.0));
}
#endif

#ifdef FX_FLICKER
// Luz parpadeante: lámpara que falla, fuego o neón. Valores por pasos de uT: bucle exacto.
vec3 flickerLayer(vec3 col, float m) {
    float k = uFxFlicker.x * m;
    if (k <= 0.001) return col;
    float style = floor(uFlickerMore.x + 0.5);
    float steps = 12.0 * max(1.0, floor(uFxFlicker.z + 0.5));
    float i = floor(uT * steps);
    float r = hash12(vec2(i, 7.3));
    float f;
    vec3 tint = vec3(1.0);
    if (style < 0.5) {
        // Lámpara: casi siempre encendida, apagones cortos y bruscos.
        f = r > 0.82 ? 0.25 + 0.3 * hash12(vec2(i, 2.1)) : 1.0;
    } else if (style < 1.5) {
        // Fuego: vaivén cálido y suave entre pasos.
        float r2 = hash12(vec2(mod(i + 1.0, steps), 7.3));
        float ft = fract(uT * steps);
        f = 0.78 + 0.22 * mix(r, r2, ft * ft * (3.0 - 2.0 * ft));
        tint = vec3(1.0, 0.85, 0.65);
    } else {
        // Neón: zumbido rápido y algún corte.
        f = (r > 0.9 ? 0.35 : 1.0) * (0.92 + 0.08 * sin(TAU * uT * steps * 3.0));
        tint = vec3(0.9, 0.95, 1.05);
    }
    vec3 lit = col * mix(vec3(1.0), tint, 0.5 * step(0.5, style)) * f;
    return mix(col, lit, k);
}
#endif

// ---------------- v44.13 · animar el cielo, el mar y la luz de TU imagen ----------------
#ifdef FX_SKYDRIFT
// Nubes de la imagen en movimiento: el cielo pintado se desliza despacio (flow map de dos
// fases con fase desplazada por zonas: no «respira» ni da saltos y el bucle cierra exacto).
// Perspectiva: cerca del horizonte se mueve menos. Lo muy brillante (sol, estrellas) se queda.
vec3 skydriftLayer(vec3 col, vec2 uv, float m) {
    float k = clamp(uFxSkydrift.x, 0.0, 1.0) * m;
    if (k <= 0.001) return col;
    float hz = clamp(uSkydriftMore.x, 0.05, 1.0);
    float above = 1.0 - smoothstep(hz - 0.035, hz + 0.004, uv.y);
    if (above <= 0.0) return col;
    float cyc = max(1.0, floor(uFxSkydrift.z + 0.5));
    vec2 dir = normalize(uMotionSkydrift.xy + vec2(1e-5, 0.0));
    float persp = mix(0.3, 1.0, smoothstep(hz, 0.0, uv.y));
    float dist = 0.045 * max(0.2, uFxSkydrift.y) * persp;
    vec2 A = vec2(uAspect, 1.0);
    float off = vnoise(uv * A * 1.6) * 0.8;                       // fase por zonas
    float p1 = fract(uT * cyc + off), p2 = fract(p1 + 0.5);
    float w1 = 1.0 - abs(2.0 * p1 - 1.0);
    float life = uSkydriftMore.y * 0.006;
    vec2 wa = (vec2(vnoise(uv * A * 4.0 + p1 * 1.7), vnoise(uv * A * 4.0 + vec2(5.1, 2.3) + p1 * 1.7)) - 0.5) * life;
    vec2 wb = (vec2(vnoise(uv * A * 4.0 + p2 * 1.7 + 9.0), vnoise(uv * A * 4.0 + vec2(3.3, 7.1) + p2 * 1.7)) - 0.5) * life;
    vec2 ua = uv - dir * dist * (p1 - 0.5) / A + wa;
    vec2 ub = uv - dir * dist * (p2 - 0.5) / A + wb;
    ua.y = min(ua.y, hz - 0.004); ub.y = min(ub.y, hz - 0.004);   // nunca toma píxeles del mar
    vec3 drift = mix(sampleImg(ub), sampleImg(ua), w1);
    float keep = smoothstep(0.84, 0.97, luma(col));                // sol y estrellas quietos
    drift = mix(drift, col, keep);
    return mix(col, drift, above * k);
}
#endif

#ifdef FX_SEAWAVES
// Mar en movimiento: olas en perspectiva (pequeñas y rápidas en el horizonte, grandes delante)
// que rompen el reflejo en horizontal, y destellos donde el agua ya brilla en tu imagen.
vec3 seawavesLayer(vec3 col, vec2 uv, float m) {
    float k = clamp(uFxSeawaves.x, 0.0, 1.5) * m;
    if (k <= 0.001) return col;
    float hz = clamp(uSeawavesMore.x, 0.0, 0.95);
    float below = smoothstep(hz + 0.003, hz + 0.03, uv.y);
    if (below <= 0.0) return col;
    float t = clamp((uv.y - hz) / max(1.0 - hz, 0.05), 0.0, 1.0);   // 0 horizonte, 1 delante
    float cyc = max(1.0, floor(uFxSeawaves.z + 0.5));
    float z = 1.0 / (t + 0.06);                                    // profundidad
    float sz = max(0.3, uFxSeawaves.y);
    vec2 w = vec2((uv.x - 0.5) * uAspect * z * 2.2, z * 1.4) / sz;
    float e = 0.12;
    float h = flowFbm(w, vec2(0.0, 1.6), cyc);
    float hx = flowFbm(w + vec2(e, 0.0), vec2(0.0, 1.6), cyc);
    float hy = flowFbm(w + vec2(0.0, e), vec2(0.0, 1.6), cyc);
    vec2 g = vec2(hx - h, hy - h) / e;
    float amp = 0.006 * k * (0.25 + 0.75 * t);
    vec2 offs = vec2(g.x * amp / uAspect, g.y * amp * 0.35);
    vec3 sea = sampleImg(clamp(uv + offs, vec2(0.0, hz + 0.002), vec2(1.0)));
    sea *= 1.0 - 0.09 * (0.5 - h) * t * k;                         // valles algo más oscuros
    float bright = smoothstep(0.5, 0.85, luma(sea));
    float spark = pow(smoothstep(0.6, 0.85, h), 3.0) * bright * uSeawavesMore.y;
    sea += vec3(1.0, 0.95, 0.85) * spark * 0.9 * k;
    return mix(col, sea, below);
}
#endif

#ifdef FX_SUNLIGHT
// Luz del sol (o de la luna): halo que respira, rayos que salen de la fuente y una luz
// cálida que baña la escena cerca de ella. Colócala sobre el sol con Mover.
vec3 sunlightLayer(vec3 col, vec2 P, float m) {
    float k = uFxSunlight.x * m;
    if (k <= 0.001) return col;
    float style = floor(uSunlightMore.x + 0.5);
    vec3 tint = style < 0.5 ? vec3(1.0, 0.72, 0.42) : (style < 1.5 ? vec3(1.0, 0.96, 0.86) : vec3(0.72, 0.84, 1.0));
    vec2 A = vec2(uAspect, 1.0);
    vec2 S = uMotionSunlight.zw * A;
    vec2 d = P - S;
    float r = length(d);
    float sz = max(0.3, uFxSunlight.y);
    float ph = TAU * uT * max(1.0, floor(uFxSunlight.z + 0.5));
    float pulse = 0.92 + 0.08 * sin(ph);
    float glow = exp(-r * 5.0 / sz) * 0.45 + exp(-r * 16.0 / sz) * 0.7;
    // Rayos: ruido sobre la DIRECCIÓN (círculo unidad) → sin costura en el ángulo.
    vec2 dirv = d / max(r, 1e-4);
    float rn = fbm(dirv * 2.6 + vec2(sin(ph) * 0.18, cos(ph) * 0.18) + 3.0);
    float rays = smoothstep(0.42, 0.78, rn) * exp(-r * 1.8 / sz) * smoothstep(0.0, 0.05, r);
    rays *= uSunlightMore.y * (0.45 + 0.55 * luma(col));
    if (style > 1.5) {                                            // luna: halo en anillo
        glow += exp(-pow((r - 0.16 * sz) * 22.0 / sz, 2.0)) * 0.12;
    }
    vec3 add = tint * (glow * pulse + rays * 0.55) * k;
    vec3 r1 = 1.0 - (1.0 - col) * (1.0 - clamp(add, 0.0, 1.0));
    // Ambiente: la luz tiñe la escena, más cerca de la fuente.
    float wash = uSunlightMore.z * exp(-r * 1.3 / sz) * 0.3 * k;
    r1 = mix(r1, r1 * mix(vec3(1.0), tint * 1.15, 0.6), wash);
    return r1;
}
#endif

#ifdef FX_FINISH
// Acabado de cine (como el «satsuei» del anime): resplandor de las luces con halo cálido,
// «look» de color, viñeta, grano de película y aberración cromática suave en los bordes.
vec3 finishLayer(vec3 col, vec2 uv, vec2 uvF) {
    float k = clamp(uFxFinish.x, 0.0, 1.0);
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    vec3 c = col;
    // Aberración cromática: más fuerte hacia los bordes del encuadre.
    float ab = uFinishMore.y;
    if (ab > 0.0) {
        vec2 dv = (uvF - 0.5);
        vec2 o = dv * 0.006 * ab;
        c.r += (sampleImg(cuv + o).r - sampleImg(cuv).r) * length(dv) * 2.0;
        c.b += (sampleImg(cuv - o).b - sampleImg(cuv).b) * length(dv) * 2.0;
    }
    // Resplandor (bloom) + halo cálido (halation) desde las zonas claras.
    float bl = uFxFinish.y;
    if (bl > 0.0) {
        vec3 b1 = texture(uImage, cuv, 2.5).rgb, b2 = texture(uImage, cuv, 4.5).rgb;
        vec3 bloom = max(b1 - 0.62, 0.0) * 0.9 + max(b2 - 0.5, 0.0) * 0.9;
        float hal = luma(max(b2 - 0.55, 0.0));
        c = 1.0 - (1.0 - c) * (1.0 - clamp(bloom * bl, 0.0, 1.0));
        c += vec3(1.0, 0.42, 0.2) * hal * 0.5 * bl;
    }
    // Look de color.
    float look = floor(uFinishMore.x + 0.5);
    float l = luma(c);
    vec3 g = c;
    if (look < 0.5) {                       // Natural+: algo más de contraste y color
        g = mix(vec3(l), c, 1.12);
        g = (g - 0.5) * 1.06 + 0.5;
    } else if (look < 1.5) {                // Atardecer cálido: luces doradas, sombras violeta
        g = c * vec3(1.07, 1.0, 0.9) + vec3(0.03, 0.0, 0.05) * (1.0 - smoothstep(0.0, 0.5, l));
        g = mix(vec3(luma(g)), g, 1.1);
    } else if (look < 2.5) {                // Noche azul
        g = mix(c, l * vec3(0.55, 0.72, 1.05) * 1.15, 0.38) * 0.94;
    } else if (look < 3.5) {                // Anime pastel: negros levantados, suave y luminoso
        g = mix(vec3(l), c, 1.15);
        g = g * 0.9 + 0.08;
    } else {                                // Cine: sombras turquesa, luces naranja
        g = c + mix(vec3(-0.02, 0.03, 0.05), vec3(0.05, 0.02, -0.04), smoothstep(0.2, 0.8, l));
        g = (g - 0.5) * 1.08 + 0.5;
    }
    if (uMono > 0.5) g = vec3(luma(g));
    c = mix(c, g, k);
    // Viñeta.
    vec2 vq = (uvF - 0.5) * vec2(uAspect, 1.0) / max(uAspect, 1.0);
    c *= 1.0 - uFxFinish.z * 0.55 * smoothstep(0.25, 0.75, length(vq) * 1.25);
    // Grano de película: cambia en cada cuadro.
    float gr = uFxFinish.w;
    if (gr > 0.0) {
        float n = hash12(gl_FragCoord.xy + floor(uT * 240.0) * vec2(17.3, 31.7)) - 0.5;
        c += n * 0.09 * gr * (1.0 - 0.5 * luma(c));
    }
    return c;
}
#endif

// ---------------- v44.14 · luz y color «de edit» (como los edits de manga/manhwa) ----------------
// Colores de luz compartidos: 0 rojo/rosa neón, 1 cálida, 2 fría, 3 morada, 4 blanca, 5 verde.
vec3 editLightColor(float v) {
    float s = floor(v + 0.5);
    if (s < 0.5) return vec3(1.0, 0.24, 0.42);
    if (s < 1.5) return vec3(1.0, 0.68, 0.38);
    if (s < 2.5) return vec3(0.42, 0.66, 1.0);
    if (s < 3.5) return vec3(0.72, 0.42, 1.0);
    if (s < 4.5) return vec3(1.0, 0.98, 0.95);
    return vec3(0.42, 1.0, 0.62);
}

#ifdef FX_GRADMAP
// Tono en degradado (gradient map): la luminosidad de la imagen se pinta con 3 colores
// (sombras · medios · luces). Es lo que da el gris azulado o el rosa-azul de los edits.
vec3 gradmapLayer(vec3 col, float m) {
    float k = clamp(uFxGradmap.x, 0.0, 1.0) * m;
    if (k <= 0.001) return col;
    float st = floor(uGradmapMore.x + 0.5);
    vec3 a, b, c;
    if (st < 0.5)      { a = vec3(0.07, 0.09, 0.13); b = vec3(0.44, 0.51, 0.59); c = vec3(0.93, 0.96, 0.99); }   // gris azulado (manga)
    else if (st < 1.5) { a = vec3(0.02, 0.03, 0.10); b = vec3(0.20, 0.32, 0.62); c = vec3(0.82, 0.91, 1.0); }    // noche azul
    else if (st < 2.5) { a = vec3(0.10, 0.10, 0.30); b = vec3(0.56, 0.46, 0.82); c = vec3(1.0, 0.86, 0.93); }    // rosa y azul (manhwa)
    else if (st < 3.5) { a = vec3(0.16, 0.05, 0.20); b = vec3(0.86, 0.36, 0.36); c = vec3(1.0, 0.89, 0.64); }    // atardecer
    else if (st < 4.5) { a = vec3(0.05, 0.0, 0.02);  b = vec3(0.55, 0.05, 0.08); c = vec3(1.0, 0.76, 0.70); }    // rojo intenso
    else               { a = vec3(0.06, 0.02, 0.12); b = vec3(0.45, 0.26, 0.66); c = vec3(0.96, 0.86, 1.0); }    // morado
    float l = clamp((luma(col) - 0.5) * (1.0 + uFxGradmap.y) + 0.5, 0.0, 1.0);
    vec3 g = l < 0.5 ? mix(a, b, smoothstep(0.0, 0.5, l)) : mix(b, c, smoothstep(0.5, 1.0, l));
    return mix(col, g, k);
}
#endif

#ifdef FX_ATMOS
// Bruma de ambiente: niebla suave que TOMA EL COLOR DE TU IMAGEN (color medio), más densa
// arriba/al fondo, que se mueve despacio y nunca cubre al personaje pintado en Proteger.
vec3 atmosLayer(vec3 col, vec2 uv, vec2 P, float m) {
    float k = uFxAtmos.x * m;
    if (k <= 0.001) return col;
    float st = floor(uAtmosMore.x + 0.5);
    vec3 avg = texture(uImage, vec2(0.5), 12.0).rgb;
    vec3 fogc = st < 0.5 ? clamp(mix(avg, vec3(luma(avg)), 0.25) * 1.35 + 0.12, 0.0, 1.0)
              : (st < 1.5 ? vec3(0.93, 0.95, 0.98) : (st < 2.5 ? vec3(0.32, 0.42, 0.65) : vec3(1.0, 0.82, 0.62)));
    float hgt = clamp(uFxAtmos.y, 0.05, 1.0);
    float far = 1.0 - smoothstep(1.0 - hgt - 0.25, 1.0 - hgt + 0.45, uv.y);   // fondo = arriba
    float n = flowFbm(P * vec2(1.4, 2.6), vec2(0.9, 0.1), max(1.0, floor(uFxAtmos.z + 0.5)));
    float a = k * (0.25 + 0.75 * far) * (0.35 + 0.65 * smoothstep(0.3, 0.75, n)) * 0.7;
    a *= 1.0 - maskLayer(clamp(uv, vec2(0.0), vec2(1.0)), 0);
    return mix(col, fogc, clamp(a, 0.0, 0.85));
}
#endif

#ifdef FX_LIGHTDIR
// Luz de color que entra desde un lado (la dirección del joystick es hacia dónde va la luz):
// degradado suave que ilumina más lo que ya es claro, borde brillante en el lado de la
// fuente y algo de sombra en el lado contrario. Respira despacio.
vec3 lightdirLayer(vec3 col, vec2 uvF, float m) {
    float k = uFxLightdir.x * m;
    if (k <= 0.001) return col;
    vec3 tint = editLightColor(uLightdirMore.x);
    vec2 L = normalize(uMotionLightdir.xy + vec2(1e-5, 0.0));
    vec2 c = (uvF - 0.5) * vec2(uAspect, 1.0) / max(uAspect, 1.0);
    float q = dot(c, L);                                   // −0,5 = lado de la luz
    float spread = clamp(uFxLightdir.y, 0.1, 1.0);
    float ph = TAU * uT * max(1.0, floor(uFxLightdir.z + 0.5));
    float g = (1.0 - smoothstep(-0.55, -0.55 + spread * 1.3, q)) * (0.88 + 0.12 * sin(ph));
    float edge = exp(-(q + 0.5) * 28.0) * uLightdirMore.y;
    vec3 add = tint * (g * (0.5 + 0.5 * luma(col)) * 0.85 + edge * 0.9);
    vec3 r = 1.0 - (1.0 - col) * (1.0 - clamp(add * k, 0.0, 1.0));
    r = mix(r, r * mix(vec3(1.0), tint, 0.45), g * k * 0.6);
    r = mix(r, tint * 1.05, clamp(edge * 0.55 * k, 0.0, 0.8));      // el borde toma el color aunque sea blanco
    r *= 1.0 - uLightdirMore.z * 0.45 * k * smoothstep(-0.05, 0.6, q);
    return r;
}
#endif

#ifdef FX_RIMLIGHT
// Luz de contorno: el borde del personaje (Proteger) que mira hacia la luz se ilumina.
vec3 rimlightLayer(vec3 col, vec2 uv, float m) {
    float k = uFxRimlight.x * m;
    if (k <= 0.001) return col;
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    float pm = maskLayer(cuv, 0);
    if (pm < 0.01) return col;
    vec2 L = normalize(uMotionRimlight.xy + vec2(1e-5, 0.0));
    vec2 st = L * 0.012 * max(0.3, uFxRimlight.y) * vec2(1.0 / max(uAspect, 0.01), 1.0);
    float o1 = maskLayer(clamp(cuv - st, vec2(0.0), vec2(1.0)), 0);
    float o2 = maskLayer(clamp(cuv - st * 2.0, vec2(0.0), vec2(1.0)), 0);
    float rim = clamp(pm - 0.5 * (o1 + o2), 0.0, 1.0);
    float ph = TAU * uT * max(1.0, floor(uFxRimlight.z + 0.5));
    vec3 lc = editLightColor(uRimlightMore.x);
    float a = clamp(rim * (0.85 + 0.15 * sin(ph)) * 1.3 * k, 0.0, 1.0);
    // v44.16: en zonas oscuras la luz se suma (pantalla); en zonas claras (manga, piel blanca)
    // se tiñe del color de la luz, si no el contorno no se veía.
    vec3 lit = 1.0 - (1.0 - col) * (1.0 - lc);
    vec3 tint = mix(col, lc * (0.55 + 0.55 * luma(col)), 0.75);
    return mix(col, mix(lit, tint, smoothstep(0.5, 0.9, luma(col))), a);
}
#endif

// ---------------- v44.15 · efectos «de editor de PC» ----------------
// Los que tocan la imagen (glitch, zoom radial, enfoque) recalculan la imagen base y
// conservan lo que los efectos ya añadieron (diferencia col − imagen).

#ifdef FX_GLITCH
// Glitch digital: ráfagas de separación RGB y franjas desplazadas. Pasos de uT: bucle exacto.
vec3 glitchLayer(vec3 col, vec2 uv) {
    float k = uFxGlitch.x;
    float steps = 18.0 * max(1.0, floor(uFxGlitch.z + 0.5));
    float i = floor(uT * steps);
    float burst = step(1.0 - clamp(uFxGlitch.y, 0.05, 1.0) * 0.6, hash12(vec2(i, 3.7)));
    float sub = floor(fract(uT * steps) * 4.0);
    float a = k * burst * (0.6 + 0.4 * hash12(vec2(i, sub + 9.0)));
    if (a <= 0.001) return col;
    float style = floor(uGlitchMore.x + 0.5);
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    vec3 base = sampleImg(cuv);
    vec2 q = cuv;
    if (style > 0.5) {
        float band = floor(cuv.y * (12.0 + 20.0 * hash12(vec2(i, 1.3))));
        float hb = hash12(vec2(band, i + sub));
        q.x += (hb > 0.72 ? (hb - 0.86) * 0.25 : 0.0) * a;
    }
    float sh = (style < 0.5 || style > 1.5 ? 0.012 : 0.0) * a;
    vec3 g = vec3(sampleImg(q + vec2(sh, 0.0)).r, sampleImg(q).g, sampleImg(q - vec2(sh, 0.0)).b);
    if (style > 0.5 && hash12(vec2(floor(cuv.y * 60.0), i)) > 0.97) g = 1.0 - g;   // línea invertida
    return col + (g - base);
}
#endif

#ifdef FX_RADIALBLUR
// Zoom radial: desenfoque hacia el centro elegido (golpe de cámara), en pulsos o constante.
vec3 radialblurLayer(vec3 col, vec2 uv) {
    float style = floor(uRadialblurMore.x + 0.5);
    float ph = fract(uT * max(1.0, floor(uFxRadialblur.z + 0.5)));
    float pulse = style < 0.5 ? pow(1.0 - ph, 3.0) : 1.0;
    float amt = uFxRadialblur.x * 0.08 * pulse;
    if (amt <= 0.0005) return col;
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    vec2 c = uMotionRadialblur.zw;
    vec3 acc = vec3(0.0);
    for (int n = 0; n < 10; n++) acc += sampleImg(mix(cuv, c, amt * float(n) / 9.0));
    return col + (acc / 10.0 - sampleImg(cuv));
}
#endif

#ifdef FX_FOCUSBLUR
// Enfoque (profundidad de campo): nítido en una banda o un círculo, desenfocado fuera, como
// una cámara con poca profundidad. El personaje pintado en Proteger nunca se desenfoca.
vec3 focusblurLayer(vec3 col, vec2 uv, vec2 uvF) {
    float k = uFxFocusblur.x;
    if (k <= 0.001) return col;
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    float style = floor(uFocusblurMore.x + 0.5);
    float wdt = clamp(uFxFocusblur.y, 0.05, 1.0) * 0.5;
    float ph = TAU * uT * max(1.0, floor(uFxFocusblur.z + 0.5));
    float d;
    if (style < 0.5) d = abs(uvF.y - uFocusblurMore.y) - wdt;
    else d = length((uvF - uMotionFocusblur.zw) * vec2(uAspect, 1.0)) - wdt;
    float blur = smoothstep(0.0, 0.35, d) * (0.92 + 0.08 * sin(ph));
    blur *= 1.0 - maskLayer(cuv, 0);
    if (blur <= 0.001) return col;
    vec3 b = texture(uImage, cuv, blur * 4.5 * k).rgb;
    return col + (b - sampleImg(cuv)) * clamp(k * 1.5, 0.0, 1.0);
}
#endif

#ifdef FX_DEEPGLOW
// Resplandor intenso («deep glow»): las luces de la imagen brillan con un halo grande y
// suave en varias capas, como el plugin de After Effects.
vec3 deepglowLayer(vec3 col, vec2 uv, float m) {
    float k = uFxDeepglow.x * m;
    if (k <= 0.001) return col;
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    float th = clamp(uFxDeepglow.y, 0.2, 0.95);
    float sz = clamp(uFxDeepglow.z, 0.3, 2.0);
    // Corte de brillo ANTES de desenfocar (si no, las luces pequeñas desaparecen): anillos de
    // muestras alrededor del píxel, cada una con su corte, sumadas con más peso cerca.
    vec3 g = vec3(0.0);
    vec2 asp = vec2(1.0 / max(uAspect, 0.01), 1.0);
    for (int r = 1; r <= 3; r++) {
        float fr = float(r);
        float rad = 0.014 * fr * sz;
        for (int a = 0; a < 6; a++) {
            float ang = float(a) * 1.0472 + fr * 0.6;
            vec3 c = texture(uImage, cuv + vec2(cos(ang), sin(ang)) * rad * asp, fr).rgb;
            g += max(c - th, 0.0) * (1.6 / fr);
        }
    }
    g = g / 6.0 + max(texture(uImage, cuv, 5.0 * sz).rgb - th * 0.8, 0.0) * 0.8;
    float st = floor(uDeepglowMore.x + 0.5);
    vec3 tint = st < 0.5 ? vec3(1.0) : (st < 1.5 ? vec3(1.0, 0.75, 0.45) : (st < 2.5 ? vec3(0.55, 0.75, 1.0) : vec3(1.0, 0.5, 0.8)));
    g = mix(g, vec3(luma(g)) * tint, st < 0.5 ? 0.0 : 0.75);
    return 1.0 - (1.0 - col) * (1.0 - clamp(g * k * 1.4, 0.0, 1.0));
}
#endif

#ifdef FX_VHS
// VHS retro: líneas de barrido, franja de «tracking» que baja, color corrido, algo de
// desaturación y ruido. Va casi al final, sobre todos los efectos.
vec3 vhsLayer(vec3 col, vec2 uv, vec2 uvF) {
    float k = uFxVhs.x;
    if (k <= 0.001) return col;
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    float cyc = max(1.0, floor(uFxVhs.z + 0.5));
    float trk = fract(uT * cyc);
    float band = exp(-pow((uvF.y - trk) * 18.0, 2.0));
    vec3 v = col;
    v += (sampleImg(cuv + vec2(0.004 + band * 0.02, 0.0)).r - sampleImg(cuv).r) * vec3(0.8, 0.0, 0.0);
    v = mix(v, vec3(luma(v)), 0.25);
    v *= vec3(1.05, 0.98, 0.92);
    float lines = uFxVhs.y;
    v *= 1.0 - lines * 0.18 * (0.5 + 0.5 * sin(uvF.y * 900.0));
    float n = hash12(gl_FragCoord.xy * 0.5 + floor(uT * 240.0) * 7.1) - 0.5;
    v += n * 0.12 * uFxVhs.w + band * 0.12 * uFxVhs.w * (hash12(vec2(floor(uvF.x * 80.0), floor(uT * 240.0))) - 0.3);
    return mix(col, v, k);
}
#endif

#ifdef FX_FILMBURN
// Quemadura de película: luz naranja orgánica que entra por los bordes y parpadea.
vec3 filmburnLayer(vec3 col, vec2 uvF, vec2 P, float m) {
    float k = uFxFilmburn.x * m;
    if (k <= 0.001) return col;
    float st = floor(uFilmburnMore.x + 0.5);
    vec3 hot = st < 0.5 ? vec3(1.0, 0.55, 0.18) : (st < 1.5 ? vec3(1.0, 0.18, 0.12) : vec3(0.35, 0.6, 1.0));
    float cyc = max(1.0, floor(uFxFilmburn.z + 0.5));
    vec2 e = min(uvF, 1.0 - uvF);
    float edge = 1.0 - min(e.x, e.y) * 2.0;
    float n = flowFbm(P * 2.2 / max(0.3, uFxFilmburn.y), vec2(1.2, -0.6), cyc);
    float burn = smoothstep(0.55, 1.0, edge + (n - 0.5) * 0.9);
    vec3 add = mix(hot, vec3(1.0, 0.95, 0.85), burn * burn) * burn * 1.2;
    return 1.0 - (1.0 - col) * (1.0 - clamp(add * k, 0.0, 1.0));
}
#endif

#ifdef FX_RAINDROPS
// Gotas de lluvia: gotas grandes cerca de la cámara que refractan la imagen, goteo desde el
// borde de arriba o salpicaduras (coronas) en el suelo.
vec3 raindropsLayer(vec3 col, vec2 uv, vec2 P, float m) {
    float k = uFxRaindrops.x * m;
    if (k <= 0.001) return col;
    float st = floor(uRaindropsMore.x + 0.5);
    float sz = max(0.3, uRaindropsMore.y);
    float cyc = max(1.0, floor(uFxRaindrops.z + 0.5));
    float amount = clamp(uFxRaindrops.y, 0.05, 1.0);
    vec2 cuv = clamp(uv, vec2(0.0), vec2(1.0));
    if (st < 0.5) {
        // Gotas cercanas: caen rápido, deformando lo que hay detrás como una lente.
        vec2 acc = vec2(0.0); float hl = 0.0;
        for (int L = 0; L < 2; L++) {
            float fl = float(L);
            float n = 3.0;
            float sc = (5.0 + fl * 4.0) / sz;
            vec2 g = vec2(P.x * sc + fl * 0.37, P.y * sc * 0.55 - uT * cyc * n * 2.0);
            vec2 id = floor(g); id.y = mod(id.y, n * 2.0);
            vec2 f = fract(g) - 0.5;
            vec2 h = hash22(id + fl * 19.0);
            if (hash12(id + 4.4 + fl) > amount) continue;
            vec2 c = (h - 0.5) * vec2(0.6, 0.3);
            vec2 d = (f - c) * vec2(1.0, 1.8);
            float r = 0.12 + 0.08 * h.y;
            float drop = 1.0 - smoothstep(r * 0.85, r, length(d));
            float tail = (1.0 - smoothstep(0.0, r * 0.3, abs(d.x))) * smoothstep(0.0, -0.35, d.y) * step(d.y, 0.0) * 0.35;
            acc += d / r * drop;
            hl += drop * smoothstep(0.5, 1.0, dot(normalize(d + 1e-4), vec2(-0.6, -0.8))) + tail * 0.4;
        }
        vec3 refr = sampleImg(clamp(cuv - acc * 0.03, vec2(0.0), vec2(1.0)));
        float inside = clamp(length(acc) * 3.0, 0.0, 1.0);
        col = mix(col, refr * 1.05 + 0.03, inside * k);
        return col + vec3(0.9, 0.95, 1.0) * clamp(hl, 0.0, 1.0) * 0.5 * k;
    }
    if (st < 1.5) {
        // Goteo: gotas que se forman en el borde superior, crecen y caen.
        float cols = 14.0 / sz;
        float id = floor(P.x * cols);
        float h = hash12(vec2(id, 2.2));
        if (h > amount) return col;
        float t = fract(uT * cyc + h);
        float x = (id + 0.5) / cols;
        float y = t < 0.6 ? 0.02 + 0.03 * t : 0.04 + pow((t - 0.6) / 0.4, 2.0) * 1.1;
        vec2 d = (P - vec2(x, y)) * vec2(1.0, 0.75);
        float r = (0.012 + 0.01 * h) * sz * (t < 0.6 ? 0.5 + t : 1.1);
        float drop = 1.0 - smoothstep(r * 0.8, r, length(d));
        vec3 refr = sampleImg(clamp(cuv - d * 1.5, vec2(0.0), vec2(1.0)));
        col = mix(col, refr * 1.08 + 0.04, drop * k);
        return col + vec3(1.0) * drop * smoothstep(0.3, 1.0, -d.y / r) * 0.4 * k;
    }
    // Salpicaduras: coronas que se abren en la parte baja de la imagen.
    float ground = smoothstep(0.6, 0.9, uv.y);
    if (ground <= 0.0) return col;
    vec2 g = vec2(P.x * 9.0 / sz, P.y * 22.0 / sz);
    vec2 id = floor(g), f = fract(g) - 0.5;
    vec2 h = hash22(id + 31.0);
    if (h.y > amount) return col;
    float ph = fract(uT * cyc * 2.0 + h.x);
    vec2 d = (f - (h - 0.5) * 0.4) * vec2(1.0, 2.4);
    float ring = exp(-pow((length(d) - ph * 0.42) * 30.0, 2.0)) * (1.0 - ph);
    float crown = exp(-pow((abs(d.x) - ph * 0.3) * 25.0, 2.0)) * step(d.y, 0.0) * exp(d.y * 6.0) * (1.0 - ph) * step(ph, 0.5);
    return col + vec3(0.85, 0.9, 1.0) * clamp(ring + crown, 0.0, 1.0) * ground * k * 0.8;
}
#endif

#ifdef FX_STARS
// Estrellas fugaces: 2 por bucle, cada una en un sitio distinto; invisibles en los extremos.
float shootingStars(vec2 P) {
    float acc = 0.0;
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float u = uT + fi * 0.5;
        float ph = fract(u);
        float life = smoothstep(0.0, 0.05, ph) * (1.0 - smoothstep(0.24, 0.32, ph));
        if (life <= 0.0) continue;
        vec2 h = hash22(vec2(fi, floor(u) + 3.0));
        vec2 start = vec2(0.15 + 0.7 * h.x, 0.05 + 0.25 * h.y) * vec2(uAspect, 1.0);
        vec2 dir = normalize(vec2(h.x > 0.5 ? -1.0 : 1.0, 0.45));
        vec2 q = P - (start + dir * ph / 0.32 * 0.5);
        float along = dot(q, -dir);
        float across = abs(dot(q, vec2(-dir.y, dir.x)));
        acc += (1.0 - smoothstep(0.0012, 0.0035, across)) * (1.0 - smoothstep(0.0, 0.16, along)) * step(-0.003, along) * life;
    }
    return acc;
}
#endif

#ifdef FX_RAIN
// Charcos: el suelo mojado se oscurece y brilla; los anillos de las gotas refractan la imagen.
vec3 rainPuddles(vec3 col, vec2 uv, vec2 P, float amt, float cyc) {
    float ground = smoothstep(0.62, 0.90, uv.y);
    if (ground <= 0.0 || amt <= 0.0) return col;
    vec2 g = vec2(P.x * 7.0, P.y * 16.0);
    vec2 acc = vec2(0.0);
    float rings = 0.0;
    for (int L = 0; L < 2; L++) {
        vec2 gg = g + float(L) * vec2(0.37, 0.61);
        vec2 id = floor(gg), f = fract(gg) - 0.5;
        vec2 h = hash22(id + 17.0 + float(L) * 5.0);
        float ph = fract(uT * cyc + h.x);
        vec2 d = (f - (h - 0.5) * 0.5) * vec2(1.0, 2.2);
        float dist = length(d) - ph * 0.45;
        float ring = exp(-dist * dist * 900.0) * (1.0 - ph) * step(0.35, h.y);
        rings += ring;
        acc += normalize(d + vec2(1e-5)) * ring;
    }
    vec3 refr = sampleImg(clamp(uv + acc * 0.006 * amt, vec2(0.0), vec2(1.0)));
    vec3 wet = col * 0.80 + vec3(0.03, 0.04, 0.06);
    col = mix(col, wet, ground * amt * 0.55);
    col = mix(col, refr * 0.9 + 0.06, clamp(rings, 0.0, 1.0) * ground * amt * 0.5);
    col += (uMono > 0.5 ? vec3(0.85) : vec3(0.75, 0.80, 0.90)) * clamp(rings, 0.0, 1.0) * ground * amt * 0.22;
    return col;
}
#endif

#ifdef FX_BLAST2
// v44.15 · solo se compila si la 2.ª copia está encendida (shader más ligero).
// v44 · 2.ª instancia de zetaBlast (generada: mismos cálculos con sus propios uniforms)
vec3 zetaBlast2(vec3 col, vec2 P, vec2 uv, float zone) {
    float a = uFxBlast2.x * maskValue(uv, uMaskBlast2, zone, uFxBlast2.w);
    if (a <= 0.001) return col;
    float cycles = max(1.0, uFxBlast2.z);
    float shape = floor(uBlastShape2.x + 0.5);
    bool manga = shape > 1.5 && shape < 2.5;
    bool energy = shape > 2.5;
    bool mushroom = shape > 0.5 && shape < 1.5;
    float tau = uEventPhaseB.x >= 0.0 ? uEventPhaseB.x : fract(uT * cycles);
    if (manga) {                              // estallido manga: animación "en dos"
        float steps = max(4.0, floor(uLoopInfo.x * 12.0 / cycles));
        tau = floor(tau * steps) / steps;
    }
    float seed = uEventPhaseB.x >= 0.0 ? 5.0 : floor(uT * cycles) + 37.0;
    vec2 center = uMotionBlast2.zw * vec2(uAspect, 1.0);
    float R = max(0.03, uFxBlast2.y) * 0.45;
    vec2 aim = normalize(uMotionBlast2.xy + vec2(1e-5, 0.0));
    vec2 q0 = P - center;
    float r0 = length(q0);

    vec3 hue = vec3(1.0, 0.47, 0.08);
    float kind = uBlastMore2.x;
    if (kind > 0.5 && kind < 1.5) hue = vec3(0.25, 0.62, 1.0);
    else if (kind > 1.5 && kind < 2.5) hue = vec3(0.70, 0.32, 1.0);
    else if (kind > 2.5) hue = vec3(0.75, 0.16, 0.10);
    if (energy && uTintBlast2.w < 0.5 && kind < 0.5) hue = vec3(0.30, 0.80, 1.0);
    hue = tintOr(hue, uTintBlast2);

    // 1) Destello: luz que baña la escena y un núcleo cegador en el centro.
    float flashAmt = clamp(uBlastShape2.y, 0.0, 1.0);
    float flash = pow(1.0 - smoothstep(0.0, 0.09, tau), 2.0) * flashAmt;
    if (manga) flash = (tau < 0.06 ? 1.0 : 0.0) * flashAmt;
    vec3 flashC = manga ? vec3(1.0) : mix(fireRamp(0.85, hue), vec3(1.0), 0.5);
    col = mix(col, col * 0.6 + flashC * 0.55, flash * 0.35 * a);
    col += flashC * flash * exp(-r0 * r0 / max(R * R * 1.2, 1e-4)) * a * 1.4;
    if (r0 > R * 4.2) return col;

    float pushAmt = clamp(uBlastShape2.w, 0.0, 1.0);
    vec2 q = q0 - aim * R * 0.9 * pushAmt * sqrt(tau);
    float lift = mushroom ? R * 1.25 * pow(tau, 1.2) : R * 0.35 * smoothstep(0.15, 1.0, tau);
    q.y += lift;
    float r = length(q);
    float ang = atan(q.y, q.x);

    // 2) Bola de fuego: crece muy rápido y luego frena; silueta en lóbulos irregulares.
    float grow = 1.0 - pow(1.0 - clamp(tau / 0.28, 0.0, 1.0), 3.0);
    float rb = R * (0.16 + 0.84 * grow + 0.22 * tau);
    float h1 = hash12(vec2(seed, 1.3)) * TAU, h2 = hash12(vec2(seed, 2.7)) * TAU, h3 = hash12(vec2(seed, 4.1)) * TAU;
    float lobe = 1.0 + 0.06 * sin(2.0 * ang + h3) + 0.08 * sin(3.0 * ang + h1) + 0.07 * sin(5.0 * ang + h2) + 0.05 * sin(7.0 * ang + h1 * 1.7);
    float rad = rb * lobe;
    vec2 nq = q / max(rb, 0.01);
    float fd = blastFireDensity(nq, tau * 1.7, seed);
    // Segunda muestra hacia la luz: volumen redondeado (caras claras y huecos oscuros).
    float fdL = blastFireDensity(nq + vec2(0.05, 0.09), tau * 1.7, seed);
    float crease = clamp(0.5 + (fd - fdL) * 3.0, 0.0, 1.0) * 0.6 + fd * 0.4;
    float d = r / max(rad, 0.004);
    float ballEdge = d + (fd - 0.5) * 0.75;
    float fireLife = 1.0 - smoothstep(0.30, 0.68, tau);
    float body = 1.0 - smoothstep(0.80, 1.0, ballEdge);
    // Temperatura: núcleo blanco al principio; se enfría desde el borde hacia dentro.
    float heat = clamp((1.25 - ballEdge) * (0.30 + 1.10 * fd) - tau * 1.75 + 0.32, 0.0, 1.0);
    heat *= body;
    float coolT = smoothstep(0.34, 0.74, tau);
    // Al enfriarse el borde caliente se vuelve difuso: bolsas de brasa que se apagan, no un disco.
    float hot = smoothstep(0.03, 0.15 + 0.40 * coolT, heat) * (1.0 - 0.55 * coolT);
    // Costra carbonizada que se deshace mientras el humo residual la sustituye.
    float charA = (1.0 - hot) * (1.0 - smoothstep(0.28, 0.66, tau)) * 0.9;
    float fireA = body * max(hot * (1.0 - 0.5 * smoothstep(0.42, 0.7, tau)), charA);
    // v40.1: al apagarse la bola se rompe en jirones (agujeros que crecen) en vez de
    // encogerse como un disco plano.
    float breakup = coolT;
    fireA *= smoothstep(breakup * 0.95 - 0.18, breakup * 0.95 + 0.12, fd + 0.3 * (1.0 - ballEdge));

    // 5a) Humo residual: la bola se convierte en humo oscuro que sube y se abre.
    float smokeAmt = clamp(uBlastMore2.y, 0.0, 1.0);
    vec3 outC = col;
    if (!energy && smokeAmt > 0.01) {
        float rs = R * (0.55 + 1.15 * tau);
        vec2 sq = q + vec2(0.0, R * 0.9 * tau);
        if (mushroom) sq.y += R * 0.4 * tau;
        vec2 sn = sq / max(rs, 0.01);
        vec2 drift = vec2(0.0, tau * 1.3);
        float s1 = warpFbm(sn * 2.0 + vec2(seed * 3.3, 0.0) + drift, 1.6);
        float s2 = 1.0 - billow(sn * 3.2 + vec2(4.1, seed) + drift * 1.4);
        float sd = s1 * 0.65 + s2 * 0.4;
        float sl = warpFbm((sn + vec2(0.06, 0.12)) * 2.0 + vec2(seed * 3.3, 0.0) + drift, 1.6);
        float rad2 = length(sn) * (1.0 + 0.12 * sin(4.0 * ang + h2));
        float erosion = 0.32 + 0.38 * smoothstep(0.55, 1.0, tau);    // se deshace en jirones
        float sdens = smoothstep(erosion, erosion + 0.25, sd + 0.45 - rad2 * 0.62);
        if (mushroom) {                                              // tallo del hongo
            vec2 st = q0;
            float top = -R * 1.25 * pow(tau, 1.2);
            float stemN = fbm3(vec2(st.x / R * 4.0, st.y / R * 2.0 + tau * 3.0));
            float stem = (1.0 - smoothstep(R * 0.12, R * 0.32, abs(st.x + (stemN - 0.5) * R * 0.3))) *
                smoothstep(top, top + R * 0.3, st.y) * (1.0 - smoothstep(-R * 0.02, R * 0.2, st.y));
            sdens = max(sdens, stem * 0.85 * smoothstep(0.1, 0.3, tau));
        }
        float life = smoothstep(0.10, 0.34, tau) * (1.0 - smoothstep(0.78, 1.0, tau));
        float sAlpha = (1.0 - exp(-sdens * 2.4)) * life * smokeAmt * 0.9;
        vec3 sTint = kind > 2.5 ? vec3(0.06) : mix(vec3(0.22, 0.21, 0.23), hue * 0.30, 0.22);
        float shade = clamp(0.55 + (sd - sl) * 4.5, 0.0, 1.0);
        vec3 smokeC = smokeShade(sTint, shade, sdens);
        // Luz anaranjada del fuego en la base del humo mientras la bola sigue viva.
        smokeC += fireRamp(0.55, hue) * (1.0 - shade) * fireLife * 0.35;
        if (manga) {
            float dots = step(0.5, fract(dot(P, vec2(90.0, 90.0)))) * step(0.5, fract(dot(P, vec2(90.0, -90.0))));
            smokeC = mix(vec3(0.95), mix(vec3(0.35), vec3(0.95), dots), step(shade, 0.5));
            float line = smoothstep(0.22, 0.30, sdens) * (1.0 - smoothstep(0.30, 0.40, sdens)) * (1.0 - smoothstep(1.0, 1.4, rad2));
            smokeC = mix(smokeC, vec3(0.02), line);
            sAlpha = max(sAlpha * step(0.22, sdens), line * life * smokeAmt);
        }
        if (uMono > 0.5) smokeC = vec3(luma(smokeC));
        outC = mix(outC, smokeC, clamp(sAlpha * a, 0.0, 0.92));
    }

    // 2b) Fuego encima del humo.
    vec3 fireC = fireRamp(heat + 0.05, hue);
    // Pliegues de las volutas; el núcleo muy caliente queda limpio (sin rayas de relieve).
    fireC *= mix(0.45 + 0.75 * crease, 1.0, smoothstep(0.45, 0.85, heat));
    // Al enfriarse, la superficie se carboniza: transición natural de fuego a humo.
    vec3 charC = mix(vec3(0.16, 0.13, 0.12), hue * 0.35, 0.3) * (0.7 + 0.5 * crease);
    fireC = mix(charC, fireC, smoothstep(0.04, 0.30, heat));
    if (manga) {
        // Manga: blanco caliente, trama de puntos en los medios tonos, negro en los
        // pliegues y contornos de tinta en el borde y entre las volutas.
        float tone = heat + (crease - 0.5) * 0.35;
        vec2 g = fract(P * 140.0) - 0.5;
        float dots = step(length(g), 0.32);
        vec3 ink = vec3(0.03);
        fireC = tone > 0.5 ? vec3(1.0) : (tone > 0.24 ? mix(vec3(1.0), ink, dots) : mix(ink, vec3(1.0), dots * 0.35));
        float outline = smoothstep(0.84, 0.9, ballEdge) * (1.0 - smoothstep(0.93, 0.99, ballEdge));
        float folds = 1.0 - smoothstep(0.0, 0.035, abs(fd - 0.5));
        // Pliegues de tinta solo hacia el borde: el centro queda limpio (sin garabatos).
        fireC = mix(fireC, ink, max(outline, folds * 0.7 * smoothstep(0.45, 0.85, ballEdge)));
        if (uTintBlast2.w > 0.5) fireC *= uTintBlast2.rgb;
    }
    if (energy) fireC = mix(hue, vec3(1.0), smoothstep(0.35, 0.9, heat)) * (0.8 + 0.4 * crease);
    if (uMono > 0.5) fireC = vec3(luma(fireC));
    outC = mix(outC, fireC, clamp(fireA * min(a * 1.25, 1.0), 0.0, 1.0));
    // Resplandor alrededor de la bola (luz emitida sobre la escena).
    float glow = exp(-max(d - 0.6, 0.0) * 2.6) * fireLife * (manga ? 0.0 : 1.0);
    outC += fireRamp(0.6, hue) * glow * 0.22 * a;
    col = outC;

    // 3) Onda de choque: anillo de aire comprimido, roto e irregular, más polvo claro.
    float ringAmt = clamp(uBlastShape2.z, 0.0, 1.0);
    if (ringAmt > 0.01 && tau < 0.62) {
        float rr = length(q0);
        float a0 = atan(q0.y, q0.x);
        float rs = R * (0.3 + 3.2 * sqrt(tau)) * (1.0 + (vnoise(vec2(a0 * 4.0, seed + 2.0)) - 0.5) * 0.18);
        float th = R * (0.025 + 0.09 * tau);
        float ring = exp(-pow((rr - rs) / max(th, 0.003), 2.0)) * pow(1.0 - tau / 0.62, 1.6);
        ring *= 0.35 + 0.65 * smoothstep(0.3, 0.7, vnoise(vec2(a0 * 6.0, seed + 9.0)));
        float haze = smoothstep(rs, rs * 0.55, rr) * smoothstep(0.0, 0.05, tau) * (1.0 - smoothstep(0.1, 0.55, tau));
        col = mix(col, vec3(0.92, 0.9, 0.86), haze * 0.16 * ringAmt * a * (energy ? 0.0 : 1.0));
        vec3 ringC = energy ? mix(hue, vec3(1.0), 0.6) : vec3(1.0, 0.96, 0.9);
        col += ringC * ring * ringAmt * a * (energy ? 1.1 : 0.55);
        if (energy) col += hue * exp(-pow((rr - rs * 0.6) / max(0.004, R * 0.04), 2.0)) * (1.0 - tau) * a;
    }

    // 4) Chispas incandescentes y escombros oscuros con gravedad y estela.
    float sparksAmt = clamp(uBlastMore2.z, 0.0, 1.0);
    if (sparksAmt > 0.01 && tau > 0.03 && tau < 0.9) {
        float best = 0.0, dark = 0.0;
        for (int i = 0; i < 24; i++) {
            float fi = float(i);
            vec2 h = hash22(vec2(fi * 7.13 + seed, seed * 3.7 + fi * 1.9));
            float life = 0.32 + 0.48 * hash12(vec2(fi, seed + 9.0));
            if (tau > life) continue;
            float an = h.x * TAU + fi * 0.37;
            vec2 dir = vec2(cos(an), sin(an) * 0.9 - 0.25);
            float sp = R * (0.8 + 3.4 * h.y * h.y);
            bool debris = mod(fi, 4.0) < 0.5;
            float g = R * (debris ? 3.2 : 1.2);
            float t1 = tau, t0 = max(tau - (debris ? 0.012 : 0.035), 0.0);
            vec2 p1 = dir * sp * (1.0 - exp(-3.2 * t1)) / 1.6 + vec2(0.0, g * t1 * t1);
            vec2 p0 = dir * sp * (1.0 - exp(-3.2 * t0)) / 1.6 + vec2(0.0, g * t0 * t0);
            float fade = 1.0 - smoothstep(life * 0.55, life, tau);
            float w = R * (debris ? 0.022 + 0.02 * h.x : 0.006 + 0.006 * h.y);
            float dd = segDist(q0, p0, p1);
            // Estela afinada: más gruesa y brillante en la cabeza que en la cola.
            float tip = clamp(dot(q0 - p0, p1 - p0) / max(dot(p1 - p0, p1 - p0), 1e-8), 0.0, 1.0);
            float ww = w * (debris ? 1.0 : 0.35 + 0.65 * tip);
            float v = (1.0 - smoothstep(ww * 0.4, ww, dd)) * fade * (debris ? 1.0 : (0.35 + 0.65 * tip) * (0.5 + 0.5 * h.x));
            if (debris) dark = max(dark, v * smoothstep(R * 0.5, R * 0.9, length(p1)));
            else best = max(best, v + 0.35 * fade * exp(-dd / max(w * 3.0, 1e-4)) * 0.5);
        }
        if (!energy) col = mix(col, vec3(0.07, 0.06, 0.06), clamp(dark * sparksAmt * a * 0.9, 0.0, 0.9));
        col += fireRamp(0.82, hue) * best * sparksAmt * a * 1.2;
    }

    // Líneas de impacto del estallido manga (v40.1): trazos finos de tinta que nacen
    // fuera de la bola y se ensanchan hacia el borde, como las líneas de concentración
    // dibujadas a mano (antes eran cuñas grises enormes).
    if (manga) {
        float a0 = atan(q0.y, q0.x);
        float N = 96.0;
        float sector = (a0 + 3.14159) / TAU * N;
        float sid = floor(sector);
        float h1 = hash12(vec2(sid, seed + 21.0)), h2 = hash12(vec2(sid, seed + 22.0));
        float lineA = (sid + 0.25 + 0.5 * h2) / N * TAU - 3.14159;
        float perp = r0 * abs(sin(a0 - lineA));
        float rStart = R * (0.95 + 0.45 * h1) * (0.85 + 0.6 * tau);
        float grow = smoothstep(rStart, rStart + R * 1.6, r0);
        float thick = R * (0.004 + 0.022 * h2) * grow;
        float ink = (1.0 - smoothstep(thick * 0.55, thick + 0.0015, perp)) * step(0.55, h1) * step(rStart, r0);
        float life = 1.0 - smoothstep(0.25, 0.55, tau);
        col = mix(col, vec3(0.02), clamp(ink * life * a, 0.0, 0.95));
    }
    return col;
}
#endif

#ifdef FX_BLOOD2
// v44.15 · solo se compila si la 2.ª copia está encendida (shader más ligero).
// v44 · 2.ª instancia de zetaBlood (generada: mismos cálculos con sus propios uniforms)
vec3 zetaBlood2(vec3 col, vec2 P, vec2 uv, float zone) {
    float cycles = max(1.0, uFxBlood2.z);
    float tau = uEventPhaseB.y >= 0.0 ? uEventPhaseB.y : fract(uT * cycles);
    float seed = uEventPhaseB.y >= 0.0 ? 29.0 : floor(uT * cycles) * 13.0 + 7.0;
    float shape = floor(uBloodShape2.x + 0.5);
    vec2 origin = uMotionBlood2.zw * vec2(uAspect, 1.0);
    float S = 0.5 * max(0.2, uFxBlood2.y);
    vec2 x = P - origin;
    float a = uFxBlood2.x * maskValue(uv, uMaskBlood2, zone, uFxBlood2.w);
    if (a <= 0.001) return col;
    float life = smoothstep(0.0, 0.04, tau) * (1.0 - smoothstep(0.72, 0.97, tau));
    float sd = 1e3;                       // distancia con signo a la tinta (<0 dentro)
    if (shape < 0.5) {
        // Spray: gotas lanzadas en abanico; frenan, caen y se estiran según su velocidad.
        float dirA = uBloodShape2.y;
        float open = mix(0.35, 1.6, clamp(uBloodShape2.z, 0.0, 1.0));
        float ease = 1.0 - pow(1.0 - min(tau / 0.35, 1.0), 3.0);
        sd = length(x * vec2(1.0, 1.2)) - S * 0.06 * smoothstep(0.0, 0.06, tau);
        for (int i = 0; i < 16; i++) {
            float k = float(i);
            float h1 = hash12(vec2(k, seed + 1.0)), h2 = hash12(vec2(k, seed + 2.0)), h3 = hash12(vec2(k, seed + 3.0));
            float an = dirA + (h1 - 0.5) * open;
            vec2 v = vec2(cos(an), -sin(an)) * S * (0.18 + 0.62 * h2);
            vec2 pos = v * ease + vec2(0.0, 0.10 * S * ease * ease);
            float rad = S * 0.012 * clamp(pow(h3 + 0.05, -0.6), 0.7, 3.4);
            vec2 rel = x - pos;
            vec2 dirv = normalize(v + 1e-5);
            float stretch = 1.0 + 3.0 * (1.0 - ease) * h2;
            float along = dot(rel, dirv) / stretch, across = dot(rel, vec2(-dirv.y, dirv.x));
            // estela hacia el origen mientras vuela
            float tail = max(0.0, -dot(rel, dirv)) / max(length(pos) + 1e-4, 1e-4);
            float dd = length(vec2(along, across)) - rad * (1.0 - 0.6 * clamp(tail, 0.0, 1.0));
            sd = smin(sd, dd, rad * 0.9);
        }
    } else if (shape < 1.5) {
        // Goteo: mancha que aparece y chorrea por gravedad.
        float splat = S * 0.17 * smoothstep(0.0, 0.08, tau);
        float rr = length(x * vec2(1.0, 1.25)) + (vnoise(vec2(atan(x.y, x.x) * 3.0, seed)) - 0.5) * splat * 0.6;
        sd = rr - splat;
        for (int i = 0; i < 6; i++) {
            float k = float(i);
            float hx = hash12(vec2(k, seed + 5.0)) - 0.5;
            float hl = hash12(vec2(k, seed + 6.0));
            float px = hx * splat * 1.5;
            float len = S * (0.12 + 0.5 * hl) * smoothstep(0.06, 0.65, tau);
            float wid = S * 0.014 * (0.6 + hl);
            float y = clamp(x.y, 0.0, len);
            float taper = 1.0 - (y / max(len, 1e-4)) * 0.45;
            float body = length(vec2(x.x - px, x.y - y)) - wid * taper;
            float bulb = length(vec2(x.x - px, x.y - len)) - wid * 1.45;
            sd = smin(sd, min(body, bulb), wid);
        }
    } else {
        // Mancha de tinta: borde irregular que se expande, con gotas satélite.
        float grow = 1.0 - pow(1.0 - min(tau / 0.22, 1.0), 3.0);
        float ang = atan(x.y, x.x);
        float rb = S * 0.2 * grow * (1.0 + 0.55 * (fbm3(vec2(ang * 1.6 + seed, seed)) - 0.5) * 2.0);
        sd = length(x) - rb;
        for (int i = 0; i < 10; i++) {
            float k = float(i);
            float an = hash12(vec2(k, seed + 9.0)) * TAU;
            float dist = S * (0.25 + 0.38 * hash12(vec2(k, seed + 10.0))) * grow;
            vec2 c = vec2(cos(an), sin(an)) * dist;
            float rad = S * 0.016 * (0.5 + hash12(vec2(k, seed + 11.0))) * grow;
            sd = smin(sd, length(x - c) - rad, rad * 0.5);
        }
    }
    float px = max(fwidth(sd), 1e-4);
    float ink = (1.0 - smoothstep(-px, px, sd)) * life;
    vec3 tint = vec3(0.62, 0.035, 0.09);
    if (uBloodStyle2.x > 0.5 && uBloodStyle2.x < 1.5) tint = vec3(0.025, 0.012, 0.03);
    else if (uBloodStyle2.x > 1.5) tint = vec3(0.09, 0.37, 0.75);
    tint = tintOr(tint, uTintBlood2);
    return mix(col, tint, clamp(ink * a, 0.0, 0.96));
}
#endif

#ifdef FX_SLASH2
// v44.15 · solo se compila si la 2.ª copia está encendida (shader más ligero).
// v44 · 2.ª instancia de zetaSlash (generada: mismos cálculos con sus propios uniforms)
vec3 zetaSlash2(vec3 col, vec2 P, vec2 uv, float zone) {
    vec2 diff = P - uMotionSlash2.zw * vec2(uAspect, 1.0);
    float a0 = uSlashStyle2.y;
    float shape = floor(uSlashShape2.x + 0.5);
    float phase = uEventPhaseB.z >= 0.0 ? uEventPhaseB.z : fract(uT * max(1.0, uFxSlash2.z));
    float len = max(0.10, uFxSlash2.y) * 0.44;
    float width = mix(0.004, 0.03, clamp(uSlashShape2.y, 0.0, 1.0));
    float m = uFxSlash2.x * maskValue(uv, uMaskSlash2, zone, uFxSlash2.w);
    if (m <= 0.001) return col;
    vec3 hue = vec3(0.14, 0.76, 1.0);
    if (uSlashStyle2.x > 0.5 && uSlashStyle2.x < 1.5) hue = vec3(0.94);
    else if (uSlashStyle2.x > 1.5 && uSlashStyle2.x < 2.5) hue = vec3(1.0, 0.70, 0.19);
    else if (uSlashStyle2.x > 2.5) hue = vec3(1.0, 0.19, 0.22);
    hue = tintOr(hue, uTintSlash2);
    float glow = 0.0, core = 0.0;
    if (shape < 0.5) {
        // Trazo clásico con estela ondulada (v35 mejorado: aparece de un extremo al otro).
        vec2 basis = vec2(cos(a0), sin(a0));
        vec2 q = vec2(dot(diff, basis), dot(diff, vec2(-basis.y, basis.x)));
        float flash = sin(3.14159 * phase);
        float xfade = 1.0 - smoothstep(0.55, 0.96, phase);
        float dist = abs(q.y - 0.10 * len * sin(q.x / len * 3.0 - TAU * phase));
        float reveal = step(q.x, mix(-len, len, clamp(phase * 4.0, 0.0, 1.0)));
        float lengthFade = (1.0 - smoothstep(len * 0.57, len, abs(q.x))) * reveal;
        core = exp(-dist / width * 1.6) * lengthFade * flash * xfade;
        glow = exp(-dist / (width * 5.0)) * lengthFade * flash * xfade;
    } else {
        int count = shape < 1.5 ? 1 : (shape < 2.5 ? 2 : 3);
        for (int k = 0; k < 3; k++) {
            if (k >= count) break;
            float fk = float(k);
            float start = fk * 0.12;
            float local = clamp((phase - start) / 0.5, 0.0, 1.0);
            float reveal = smoothstep(0.0, 0.35, local) * 1.05;
            float life = 1.0 - smoothstep(0.45, 1.0, local);
            float ang = a0;
            vec2 offset = vec2(0.0);
            if (shape > 1.5 && shape < 2.5) ang = a0 + (fk == 0.0 ? 0.785 : -0.785);
            if (shape > 2.5) offset = vec2(-sin(a0), cos(a0)) * (fk - 1.0) * len * 0.35;
            vec2 basis = vec2(cos(ang), sin(ang));
            vec2 d2 = diff - offset;
            vec2 q = vec2(dot(d2, basis), dot(d2, vec2(-basis.y, basis.x)));
            float arc = slashArc(q, len * 2.0, shape < 1.5 ? 0.9 : 0.35, reveal, width * 1.6);
            float halo = slashArc(q, len * 2.0, shape < 1.5 ? 0.9 : 0.35, reveal, width * 3.2);
            core = max(core, arc * life * step(start, phase));
            glow = max(glow, halo * life * step(start, phase));
        }
    }
    col += hue * glow * 0.42 * m;
    col = mix(col, mix(hue, vec3(1.0), 0.75), clamp(core * m, 0.0, 1.0));
    return col;
}
#endif

#ifdef FX_SMOKE2
// v44.15 · solo se compila si la 2.ª copia está encendida (shader más ligero).
// v44 · 2.ª instancia de zetaSmoke (generada: mismos cálculos con sus propios uniforms)
vec3 zetaSmoke2(vec3 col, vec2 P, vec2 uv, float zone) {
    float m = maskValue(uv, uMaskSmoke2, zone, uFxSmoke2.w);
    if (m <= 0.001) return col;
    float cyc = max(1.0, uFxSmoke2.z);
    float shape = floor(uSmokeShape2.x + 0.5);
    float turb = clamp(uSmokeShape2.y, 0.0, 1.0);
    float soft = clamp(uSmokeShape2.z, 0.0, 1.0);
    vec2 origin = uMotionSmoke2.zw * vec2(uAspect, 1.0);
    vec2 wind = normalize(uMotionSmoke2.xy + vec2(0.001, 0.003));
    vec2 side = vec2(-wind.y, wind.x);
    float progress = fract(uT * cyc);
    float ph = TAU * uT * cyc;
    float density = clamp(uSmokeControl2.y, 0.2, 2.0);
    float sizeK = clamp(max(0.5, uFxSmoke2.y) / 3.0, 0.2, 3.0);
    // Luz desde arriba a la izquierda (en coordenadas de la imagen).
    vec2 lightDir = normalize(vec2(-0.45, -0.9));
    float dens, shade, presence = 1.0;
    float thr = mix(0.62, 0.38, clamp((density - 0.2) / 1.8, 0.0, 1.0));
    float edge = mix(0.05, 0.22, soft);
    if (uSmokeShape2.w < 0.5 && shape > 0.5 && shape < 1.5) {
        // Hilos: filamentos que nacen en el origen y se estiran con el viento.
        vec2 rel = P - origin;
        float along = dot(rel, wind);
        float across = dot(rel, side);
        float L = (0.32 + 0.8 * clamp(uSmokeControl2.z, 0.0, 1.5)) * mix(0.8, 1.3, clamp(sizeK, 0.0, 1.5) / 1.5);
        float au = max(along, 0.0);
        if (along < -0.08 || along > L * 1.15) return col;
        float width = (0.028 + au * mix(0.32, 0.62, turb)) * sizeK;
        float bend = (vnoise(vec2(au * 2.4, 4.0) + vec2(cos(ph), sin(ph)) * 0.45) - 0.5) * au * (0.30 + 0.5 * turb);
        float v = (across - bend) / width;
        if (abs(v) > 3.0) return col;
        float s = log(1.0 + 0.45 * au / 0.028) / 0.45 * 0.028 * 26.0;
        vec2 fq = vec2(v * 1.15, s);
        vec2 travel = vec2(0.0, 1.6);
        float n = contrastFix(mix(smokeDensity(fq - travel * progress, shape, turb),
            smokeDensity(fq - travel * (progress - 1.0), shape, turb), progress), progress);
        float body = exp(-v * v * 0.8);
        float t01 = clamp(along / L, 0.0, 1.0);
        presence = smoothstep(-0.03, 0.04, along) * (1.0 - smoothstep(0.45, 1.0, t01));
        float th = thr + 0.14 * t01 + (1.0 - body) * 0.95;
        dens = smoothstep(th - edge, th + edge * 1.6, n) * (1.0 - 0.45 * t01);
        shade = clamp(0.4 + n * 0.6 - v * 0.08, 0.0, 1.0);
    } else if (uSmokeShape2.w < 0.5) {
        // Columna de volutas: bocanadas que nacen en el origen, suben, crecen, se
        // curvan y se disipan. Cada bocanada se ilumina como un volumen redondeado
        // (luz arriba-izquierda) y su borde se deforma con ruido propio que viaja con ella.
        float L = (0.32 + 0.8 * clamp(uSmokeControl2.z, 0.0, 1.5)) * mix(0.8, 1.3, clamp(sizeK, 0.0, 1.5) / 1.5);
        float kw = mix(0.11, 0.24, turb);
        vec2 rel = P - origin;
        float along = dot(rel, wind);
        if (along < -0.12 || along > L * 1.35) return col;
        vec3 light3 = normalize(vec3(-0.45, -0.75, 0.55));
        float D = 0.0, S = 0.0, bestM = 0.0;
        for (int i = 0; i < 16; i++) {
            float fi = float(i);
            // Fase propia de cada bocanada: velocidad ENTERA por bucle (cierra sin saltos).
            float p = fract(fi / 16.0 + uT * cyc);
            float h1 = hash12(vec2(fi, 3.7)), h2 = hash12(vec2(fi, 8.1));
            // v40.1: sube rápido al nacer y frena arriba (el humo pierde empuje).
            float rise = 1.0 - pow(1.0 - p, 1.55);
            float au = L * rise;
            float w = (0.040 + au * kw * 1.15) * sizeK;
            float bendP = (vnoise(vec2(au * 2.4, 4.0) + vec2(cos(ph), sin(ph)) * 0.45) - 0.5) * au * (0.30 + 0.5 * turb);
            // Las bocanadas viejas se desvían a los lados (se abre en abanico, no un tubo).
            float spread = (h1 - 0.5) * w * (0.9 + 1.6 * p);
            vec2 c = origin + wind * au + side * (bendP + spread);
            float r = w * (0.75 + 0.5 * h2);
            vec2 u = (P - c) / r;
            if (shape > 2.5) u = vec2(dot(u, wind) * 0.55, dot(u, side) * 1.9);   // rastrero: aplanado a lo largo del viento
            float d2 = dot(u, u);
            // v44.15 · rendimiento: el radio máximo es 0,60 + 0,72 = 1,32 (1,32² ≈ 1,75); fuera de
            // él la bocanada no aporta nada. Antes se calculaba el ruido hasta 2,6 (×1,5 de área).
            if (d2 > 1.75) continue;
            float life = smoothstep(0.0, 0.10, p) * (1.0 - smoothstep(0.45, 1.0, p));
            if (life <= 0.001) continue;
            // Remolino propio: la textura interior gira lentamente mientras sube.
            float swirlA = (h2 - 0.5) * 2.4 * p + p * 1.3;
            mat2 rot = mat2(cos(swirlA), -sin(swirlA), sin(swirlA), cos(swirlA));
            vec2 ur = rot * u;
            float en = fbm3(ur * 1.4 + vec2(fi * 3.1, fi * 1.7) + vec2(0.0, p * 2.2));
            float rad = 0.60 + 0.72 * en;
            float x = sqrt(d2) / rad;
            if (x >= 1.0) continue;                     // fuera del cuerpo: sin el segundo ruido
            // Cuerpo suave + detalle interior (volutas pequeñas que se vuelven jirones al
            // envejecer). La masa se reparte en un área mayor: más transparente arriba.
            float bodyM = 1.0 - smoothstep(0.30 - 0.15 * soft, 1.0, x);
            float detail = fbm3(ur * 2.2 + vec2(fi * 5.7, p * 1.7));
            float erode = mix(0.28, 0.66, smoothstep(0.15, 0.95, p));
            float wisp = smoothstep(erode - 0.26, erode + 0.26, detail * 0.8 + bodyM * 0.38);
            float mm = bodyM * wisp * life * (0.75 + 0.5 * en) / (1.0 + 2.2 * p * p);
            if (mm <= 0.0) continue;
            vec2 uu = u / rad;
            float z = sqrt(max(1.0 - dot(uu, uu), 0.0));
            vec3 nrm = normalize(vec3(uu * 1.3 + (vec2(en, detail) - 0.5) * 1.2, z));
            float lit = clamp(dot(nrm, light3) * 0.65 + 0.35, 0.0, 1.0);
            // Sombreado ponderado por mm²: domina la bocanada más densa sin cortes de color
            // (antes se tomaba la luz de la "mejor" bocanada y aparecían bordes duros).
            D += mm; S += mm * mm * lit; bestM += mm * mm;
        }
        dens = clamp(D * mix(0.75, 1.15, clamp(density - 0.5, 0.0, 1.0)), 0.0, 2.0);
        shade = S / max(bestM, 1e-5);
        shade = smoothstep(0.15, 0.9, shade);
    } else {
        // Llenar toda la zona: banco de humo que avanza con el viento.
        vec2 q = (P - origin) * 4.5 / sizeK + vec2(3.7, 1.9);
        vec2 swirl = vec2(sin(ph), cos(ph)) * 0.14 * (0.4 + turb);
        vec2 travel = wind * (0.45 + uSmokeControl2.z * 0.8) * 2.0;
        float da = smokeDensity(q + swirl - travel * progress, shape, turb);
        float db = smokeDensity(q + swirl - travel * (progress - 1.0), shape, turb);
        float n = contrastFix(mix(da, db, progress), progress);
        vec2 lq = q + lightDir * 0.3;
        float la = smokeDensity(lq + swirl - travel * progress, shape, turb);
        float lb = smokeDensity(lq + swirl - travel * (progress - 1.0), shape, turb);
        float nl = contrastFix(mix(la, lb, progress), progress);
        dens = smoothstep(thr - edge - 0.12, thr + edge, n) * 1.25;
        if (shape > 2.5) {
            dens *= 1.0 - smoothstep(-0.10, 0.30, origin.y - P.y);
            dens = max(dens, smoothstep(thr - edge - 0.12, thr + edge, n) * (1.0 - smoothstep(-0.25, 0.08, origin.y - P.y)) * 0.85);
        }
        shade = clamp(0.55 + (n - nl) * 3.2, 0.0, 1.0);
        presence = emitArea(P, uMotionSmoke2);
    }
    float k = uFxSmoke2.x * m * presence;
    // Absorción tipo Beer-Lambert: transparencia creíble y bordes suaves.
    float alpha = (1.0 - exp(-dens * min(density, 1.6) * 2.4)) * k;
    alpha = clamp(alpha, 0.0, 0.94);
    if (alpha <= 0.002) return col;
    float kind = floor(uSmokeControl2.x + 0.5);
    vec3 tint = vec3(0.70, 0.72, 0.77);
    if (kind > 0.5 && kind < 1.5) tint = vec3(0.07, 0.065, 0.08);
    else if (kind > 1.5 && kind < 2.5) tint = vec3(0.90, 0.91, 0.94);
    else if (kind > 2.5 && kind < 3.5) tint = vec3(0.36, 0.24, 0.55);
    else if (kind > 3.5) tint = vec3(0.55, 0.38, 0.29);
    tint = tintOr(tint, uTintSmoke2);
    vec3 smokeC = smokeShade(tint, shade, dens);
    if (shape > 1.5 && shape < 2.5) {
        // Tinta manga: dos tonos y contorno de tinta.
        float bright = luma(tint);
        smokeC = mix(tint * 0.62, mix(tint, vec3(1.0), 0.12), step(0.5, shade));
        float line = smoothstep(0.08, 0.2, dens) * (1.0 - smoothstep(0.2, 0.36, dens));
        smokeC = mix(smokeC, vec3(bright > 0.5 ? 0.10 : 0.0), line * 0.9);
        alpha = max(alpha, clamp(line * 0.85 * k, 0.0, 0.9));
    }
    if (uMono > 0.5) smokeC = vec3(luma(smokeC));
    return mix(col, smokeC, alpha);
}
#endif

#ifdef FX_CAM
// v44.20 · La cámara es una función del tiempo: así se sabe dónde estaba un instante antes y
// después (estela de movimiento real, sin memoria entre cuadros: el bucle sigue perfecto).
vec2 zetaCamera(vec2 s, float tt) {
    vec2 uvFrame = uViewOffset + s * uViewScale;
    vec2 sc = s;
    if (uCam.x > 0.0 || uCam.y > 0.0) {
        float phase = TAU * tt * uCam.z;
        float wave = 0.5 - 0.5 * cos(phase);
        float mode = uCameraMode.x;
        float zoom = 1.0 + uCam.x * (mode < 0.5 ? wave : 1.0-wave);
        vec2 offset = vec2(0.0);
        float angle = 0.0;
        if (mode > 1.5 && mode < 2.5) {
            zoom = 1.0 + uCam.x + uCam.y * 0.3;
            vec2 panDir = vec2(cos(uCameraMode.y), -sin(uCameraMode.y));
            offset = panDir * sin(phase) * (0.5 - 0.5 / zoom) * uCam.y;
        }
        if (mode > 2.5 && mode < 3.5) { angle = sin(phase)*uCam.x*0.3; zoom = 1.0 + abs(sin(angle))*max(uAspect,1.0/uAspect)+uCam.x*0.1; }
        if (mode > 3.5 && mode < 4.5) { zoom = 1.0+uCam.x*0.15+uCam.y*0.06; offset = vec2(sin(phase*3.0),sin(phase*5.0))*uCam.y*0.01; }
        if (mode > 5.5 && mode < 6.5) { // v42 · Latido: dos golpes de zoom por ciclo
            float beat = pow(0.5 + 0.5 * cos(phase * 2.0), 8.0);
            zoom = 1.0 + uCam.x * 0.25 + uCam.x * 0.9 * beat;
        }
        if (mode > 6.5 && mode < 7.5) { // v42 · Flotar: sube y baja suave
            zoom = 1.03 + uCam.x * 0.35;
            offset = vec2(0.0, sin(phase)) * (0.5 - 0.5 / zoom) * (0.55 + 0.45 * uCam.y);
            angle = sin(phase + 1.3) * 0.008;
        }
        if (mode > 7.5 && mode < 8.5) { // v42 · Balanceo: péndulo con el eje abajo
            angle = sin(phase) * (0.015 + uCam.x * 0.18);
            zoom = 1.02 + abs(angle) * max(uAspect, 1.0 / uAspect) * 1.3 + uCam.x * 0.05;
        }
        if (mode > 4.5 && mode < 5.5) { // órbita 3D simulada, sin inventar geometría oculta
            zoom = 1.04 + uCam.x * 0.7 + uCam.y * 0.16;
            float orbit = sin(phase) * (0.03 + 0.20 * uCam.y);
            vec2 travel = vec2(cos(uCameraMode.y), -sin(uCameraMode.y));
            offset = travel * orbit * 0.15;
        }
        if (mode > 8.5) { // v44 · Libre: valores directos (los anima la línea de tiempo con fotogramas clave)
            float k = clamp(uCam.x, 0.0, 1.0);
            zoom = mix(1.0, max(1.0, uCamManual.w), k);
            angle = uCamManual.z * k;
            zoom = max(zoom, 1.0 + abs(sin(angle)) * max(uAspect, 1.0 / uAspect));
            offset = clamp(uCamManual.xy, vec2(-1.0), vec2(1.0)) * k * (0.5 - 0.5 / zoom);
        }
        vec2 center = (s-0.5)*vec2(uAspect,1.0);
        vec2 pivot = (mode > 7.5 && mode < 8.5) ? vec2(0.0, 0.5) : vec2(0.0);   // abajo de la imagen
        center = mat2(cos(angle),-sin(angle),sin(angle),cos(angle))*(center - pivot) + pivot;
        if (mode > 4.5 && mode < 5.5) {
            float orbit = sin(phase) * (0.07 + 0.22 * uCam.y);
            center.x = center.x * (1.0 - 0.25 * abs(orbit)) + center.y * orbit;
            center.y = center.y * (1.0 - 0.08 * abs(orbit));
        }
        vec2 moved = center/vec2(uAspect,1.0)/zoom+0.5+offset;
        sc = mix(s,moved,maskValue(uvFrame,uMaskCam,1.0,0.0));
    }
    return sc;
}
// v44.20 · Estela de cámara: obturador de 180° a 30 fps (medio cuadro). Se promedian 8
// muestras de la imagen a lo largo del recorrido del píxel; solo se añade la DIFERENCIA,
// así se conservan las distorsiones y el agua ya calculadas.
vec3 cameraBlur(vec3 col, vec2 s, vec2 uv) {
    float dt = 0.5 / max(30.0 * uLoopInfo.x, 1.0);
    vec2 a = zetaCamera(s, uT - dt);
    vec2 b = zetaCamera(s, uT + dt);
    vec2 v = (b - a) * 0.5 * uViewScale * uMotionBlur * 2.0;
    float px = length(v * uTexSize);
    if (px < 1.2) return col;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 8; i++) acc += sourcePixel(uv + v * ((float(i) + 0.5) / 8.0 - 0.5));
    return col + (acc / 8.0 - sourcePixel(uv)) * smoothstep(1.2, 3.5, px);
}
#endif
void main() {
    // Coordenadas de pantalla con origen arriba-izquierda
    vec2 s = vec2(vScreen.x, mix(1.0 - vScreen.y, vScreen.y, uFlipY));
    if (uRotate > 0.5) {
        s = vec2(1.0 - s.y, s.x);
    }

    vec2 uvFrame = uViewOffset + s * uViewScale;
    // Painting uses the original frame: stable coordinates and no expensive effect work.
    if ((uShowMask > 0.5 && uActiveMask >= 0) || uFast > 0.5) {
        vec3 original=sourcePixel(uvFrame);
        float selected=(uShowMask > 0.5 && uActiveMask >= 0) ? maskLayer(uvFrame, max(uActiveMask,0)) : 0.0;
        original=mix(original,vec3(0.72,0.39,1.0),selected*0.48);
        original+=(hash12(gl_FragCoord.xy)-0.5)/255.0;
        fragColor=vec4(clamp(original,0.0,1.0),1.0);
        return;
    }


    // Smooth periodic camera; overscan prevents exposed corners during rotation/pan.
    vec2 sc = s;
#ifdef FX_CAM
    if (uCam.x > 0.0 || uCam.y > 0.0) sc = zetaCamera(s, uT);
#endif
    // Profundidad 2.5D: Zona verde = primer plano, Zona azul = fondo, Proteger = plano de foco.
    // v36: búsqueda iterativa de profundidad (como el parallax occlusion mapping), primer
    // plano dilatado para que el estiramiento caiga en el fondo y no en la silueta, y
    // sobreencuadre automático para que nunca se vean bordes. Cierra exacto en el bucle.
#ifdef FX_PARALLAX
    if (uParallax.x > 0.0) {
        float ph = TAU * uT * uParallax.z;
        float style = floor(uParallaxStyle.x + 0.5);
        vec2 dir = vec2(cos(uParallax.y), -sin(uParallax.y));
        float amt = uParallax.x;
        vec2 cam = dir * sin(ph);
        float dolly = 0.0;
        if (style > 0.5 && style < 1.5) cam = vec2(cos(ph), sin(ph) * 0.5) - vec2(1.0, 0.0);   // órbita, empieza en 0
        else if (style > 1.5) { dolly = 0.5 - 0.5 * cos(ph); cam = dir * dolly * 0.35; }
        float focus = clamp(uParallaxStyle.y, -1.0, 1.0);
        float over = 1.0 + amt * (1.0 + uParallax.w + abs(focus)) * (style > 0.5 && style < 1.5 ? 2.2 : 1.15) + dolly * amt * 1.2;
        vec2 base = (sc - 0.5) / over + 0.5;
        vec2 p = base;
        // v46: fuera del personaje (Proteger), el color sale de la placa de fondo rellenada.
        if (uParallaxStyle.w > 0.5) gPlate = 1.0 - maskLayer(clamp(uViewOffset + base * uViewScale, vec2(0.0), vec2(1.0)), 0);
        // v45: con mapa IA (relieve continuo) se refina más la búsqueda: bordes sin dobles.
        int iters = uDepthInfo.x > 0.5 ? 6 : 3;
        for (int i = 0; i < iters; i++) {
            vec2 fu = clamp(uViewOffset + p * uViewScale, vec2(0.0), vec2(1.0));
            float d = parallaxDepth(fu) - focus;
            float zoom = 1.0 + dolly * amt * 4.0 * d;
            p = (base - 0.5) / max(zoom, 0.2) + 0.5 - cam * amt * d;
        }
        sc = p;
    }
#endif

    float impactE = 0.0;
#ifdef FX_IMPACT
    if (uImpact.x > 0.0) {
        impactE = impactEnv();
        float kf = max(1.0, floor(uLoopInfo.x * 16.0 + 0.5));
        float z = 1.0 + (uImpact.x + 0.04 * uImpact.y) * impactE;
        vec2 shake = vec2(sin(TAU * uT * kf), sin(TAU * uT * (kf + 3.0) + 1.3)) * uImpact.y * 0.02 * impactE;
        sc = (sc - 0.5) / z + 0.5 + shake;
    }
#endif
    vec2 uv = uViewOffset + sc * uViewScale;
#ifdef FX_PARALLAX
    // v45: «Ver mapa de profundidad» → blanco = cerca, negro = lejos; verde/azul = correcciones.
    if (uDepthInfo.w > 0.5) {
        vec2 fu = clamp(uv, vec2(0.0), vec2(1.0));
        float dv = uDepthInfo.x > 0.5 ? depthQuant(texture(uDepth, fu).r) : 0.5 + 0.5 * (maskLayer(fu, 2) - maskLayer(fu, 1));
        vec3 vis = vec3(dv);
        vis = mix(vis, vec3(0.25, 0.95, 0.45), 0.35 * maskLayer(fu, 2));
        vis = mix(vis, vec3(0.25, 0.55, 1.0), 0.35 * maskLayer(fu, 1));
        vis = mix(vis, vec3(1.0, 0.35, 0.35), 0.30 * maskLayer(fu, 0));
        fragColor = vec4(vis, 1.0);
        return;
    }
#endif

    float freeze = maskLayer(uv, 0);
    float z1 = maskLayer(uv, 1);
    float z2 = maskLayer(uv, 2);
    float move = 1.0 - freeze;
    vec2 asp = vec2(1.0 / uAspect, 1.0);
    vec2 P = vec2(uvFrame.x * uAspect, uvFrame.y);
    vec2 Q = vec2(uv.x * uAspect, uv.y);

    vec3 col = vec3(0.0);

    if (uOverlayOnly < 0.5) {
        // Distorsiones
        vec2 d = vec2(0.0);
#ifdef FX_RIPPLE
        if (uRipple.x > 0.0) {
            vec2 q = Q * uRipple.y;
            float ph = TAU * uT * uRipple.z;
            d += vec2(sin(q.y * 1.7 + ph + sin(q.x * 0.9)), cos(q.x * 1.3 - ph + sin(q.y * 1.1))) * uRipple.x * maskValue(uv, uMaskRipple, z1, 1.0);
        }
#endif
#ifdef FX_SWAY
        if (uSway.x > 0.0) {
            float n = vnoise(Q * uSway.y);
            float ph = TAU * (uT * uSway.z + n);
            float sw = sin(ph) + 0.35 * sin(2.0 * ph + 1.3);
            d += vec2(sw, 0.3 * cos(ph)) * uSway.x * maskValue(uv, uMaskSway, z2, 1.0);
        }
#endif
#ifdef FX_HEAT
        if (uHeat.x > 0.0) {
            vec2 q = Q * uHeat.y;
            float ph = TAU * uT * uHeat.z;
            d += vec2(sin(q.y * 2.0 + ph + vnoise(q) * 3.0), 0.5 * cos(q.x * 1.5 + ph)) * uHeat.x * maskValue(uv, uMaskHeat, z2, 1.0);
        }
#endif
#ifdef FX_CLOTH
        if (uFxCloth.x > 0.0) {
            float ph = TAU * uT * uFxCloth.z;
            vec2 q = Q * uFxCloth.y;
            vec2 sway = vec2(sin(q.y + ph + 0.4 * sin(q.x)),
                             0.25 * cos(q.x + ph));
            d += sway * uFxCloth.x * maskValue(uv, uMaskCloth, z2, 1.0);
        }
#endif
#ifdef FX_MESH
        if (uMesh.x > 0.0) {
            float m = maskValue(uv, uMaskMesh, z2, 1.0);
            float ph = TAU * uT * uMesh.z;
            float a = uMeshDirection.x;
            vec2 dir = vec2(cos(a), -sin(a));
            vec2 side = vec2(-dir.y, dir.x);
            vec2 q = Q * uMesh.y;
            float along = dot(q, side);
            float across = dot(q, dir);
            vec2 deform;
            if (uMesh.w < 0.5) {
                // Ondulación: útil para cabello, cintas, agua o humo.
                deform = dir * sin(along + ph + 0.35 * sin(across * 0.65)) +
                    side * 0.22 * cos(across * 0.7 - ph);
            } else if (uMesh.w < 1.5) {
                // Respiración: expansión suave alrededor de la zona pintada.
                vec2 center = (uv - vec2(0.5)) * vec2(uAspect, 1.0);
                deform = normalize(center + vec2(0.0001)) * sin(ph) * (0.35 + 0.65 * smoothstep(0.0, 0.7, length(center)));
            } else {
                // Tela: dos ondas combinadas para evitar un aspecto rígido.
                deform = dir * (0.72 * sin(along + ph) + 0.28 * sin(along * 1.9 - ph * 0.8)) +
                    side * 0.18 * cos(across + ph * 0.7);
            }
            d += deform * uMesh.x * m;
        }
#endif
#ifdef FX_NATURAL
        if (uNatural.x > 0.0) {
            // v36: movimiento con raíz fija. s = distancia normalizada desde el borde fijo
            // DENTRO del rectángulo pintado (no de toda la imagen). La amplitud crece como
            // s^gamma (viga sujeta en un extremo) y una onda viaja de la raíz a la punta.
            // Fases 1x/2x/3x enteras: el cuadro final empalma con el primero.
            float m = maskValue(uv, uMaskNatural, z2, 1.0);
            if (m > 0.001) {
                float a = uNatural.y;
                vec2 wind = normalize(vec2(cos(a), -sin(a)) + vec2(1e-6, 0.0));
                vec2 side = vec2(-wind.y, wind.x);
                float phase = TAU * uT * uNatural.z;
                float flex = clamp(uNaturalMore.x, 0.0, 1.0);
                float variation = clamp(uNaturalMore.y, 0.0, 1.0);
                float gust = clamp(uNaturalMore.z, 0.0, 1.0);
                float material = floor(uNatural.w + 0.5);
                float anchor = floor(uNaturalMore.w + 0.5);
                float weight = clamp(uNaturalDynamics.x, 0.0, 1.0);
                float spring = clamp(uNaturalDynamics.y, 0.0, 1.0);
                vec4 box = uNaturalBox;
                if (box.z <= box.x + 0.001 || box.w <= box.y + 0.001) box = vec4(0.0, 0.0, 1.0, 1.0);
                vec2 bl = clamp((uv - box.xy) / max(box.zw - box.xy, vec2(0.02)), 0.0, 1.0);
                float s = 1.0;
                if (anchor > 0.5 && anchor < 1.5) s = bl.x;            // izquierda fija
                else if (anchor > 1.5 && anchor < 2.5) s = 1.0 - bl.x; // derecha fija
                else if (anchor > 2.5 && anchor < 3.5) s = 1.0 - bl.y; // abajo fija (árbol, pasto)
                else if (anchor > 3.5) s = bl.y;                       // arriba fija (pelo, cortina)
                // Peso: raíz más rígida y menos altas frecuencias. Flexibilidad: curva más suave.
                float gamma = mix(1.05, 2.2, weight) * mix(1.25, 0.8, flex);
                float pin = anchor < 0.5 ? 1.0 : pow(s, gamma) * smoothstep(0.0, 0.06, s) * 1.35;
                // Fase estática por mechón/rama: vecinos desfasados, sin efecto "gelatina".
                float theta = (vnoise(Q * mix(2.5, 9.0, variation) + 3.7) - 0.5) * 3.2 * variation;
                float kappa = 2.0;
                if (material < 0.5) kappa = 1.1;          // árbol
                else if (material < 1.5) kappa = 7.5;     // bandera
                else if (material < 2.5) kappa = 1.6;     // pasto
                else if (material < 3.5) kappa = 4.0;     // cortina
                else if (material < 4.5) kappa = 3.0;     // cabello
                else kappa = 3.4;                         // ropa
                kappa *= mix(0.6, 1.3, flex);
                float w1 = sin(phase - kappa * s + theta);
                float w2 = sin(2.0 * phase - kappa * 1.7 * s + theta * 1.3 + 1.1);
                float w3 = sin(3.0 * phase - kappa * 2.4 * s + theta * 0.7 + 2.3);
                float bounce = mix(0.10, 0.42, spring) * (1.0 - 0.45 * weight);
                float sway = (1.0 - bounce) * w1 + bounce * w2 + 0.14 * variation * (1.0 - weight) * w3;
                // Ráfagas: armónicos enteros (siguen cerrando el bucle) más fuertes en la punta.
                float gustEnv = 1.0 + gust * (0.34 * sin(phase + 0.7) + 0.16 * sin(2.0 * phase + 2.1)) * (0.5 + 0.5 * s);
                // El efecto Viento (ráfagas) también empuja cabello, ropa y ramas mientras sopla.
                if (uGust.x > 0.0) gustEnv *= 1.0 + clamp(uGust.x, 0.0, 1.5) * 0.55 * windowEnv(uGust.y, uGust.z) *
                    (0.75 + 0.25 * sin(TAU * uT * max(1.0, uGust.w)));
                if (uWind.x > 0.0) gustEnv *= 1.0 + uWind.x * 0.5 * (0.6 + 0.4 * sin(TAU * uT + 0.3));
                vec2 naturalD;
                if (material < 0.5) {
                    // Árbol: la copa se inclina; las hojas tiemblan con alta frecuencia en la punta.
                    float flutter = sin(4.0 * phase + vnoise(Q * 38.0) * TAU) * 0.22 * variation * smoothstep(0.35, 1.0, s);
                    naturalD = wind * (sway * 0.85 + flutter) + side * 0.16 * w2;
                } else if (material < 1.5) {
                    // Bandera: onda que viaja desde el mástil; la tela se estira en la dirección del viento.
                    float wave = sin(2.0 * phase - kappa * s + theta * 0.5) + 0.35 * sin(3.0 * phase - kappa * 1.9 * s + 0.8);
                    naturalD = side * wave * (0.55 + 0.45 * flex) + wind * (0.18 + 0.12 * w1);
                } else if (material < 2.5) {
                    // Pasto: cada brizna con su propia fase (ruido fino en x), base inmóvil.
                    float blade = (vnoise(vec2(Q.x * 70.0, 3.0)) - 0.5) * 4.0 * variation;
                    float g1 = sin(phase - kappa * s + blade) + 0.25 * sin(2.0 * phase + blade * 1.7);
                    naturalD = wind * g1 + side * 0.06 * w2;
                } else if (material < 3.5) {
                    // Cortina: pliegues verticales que se propagan hacia abajo.
                    float folds = sin(Q.x * 24.0 + phase - s * 2.0) * 0.5 + sway * 0.5;
                    naturalD = wind * folds + side * 0.10 * w2;
                } else if (material < 4.5) {
                    // Cabello: puntas con retraso y rebote; la raíz no se mueve.
                    naturalD = wind * sway + side * 0.14 * w2 * (0.5 + 0.5 * s);
                } else {
                    // Ropa: tela pesada, menos rebote y pliegues amplios.
                    naturalD = wind * sway * mix(1.0, 0.75, weight) + side * 0.10 * w3;
                }
                d += naturalD * uNatural.x * gustEnv * pin * m;
            }
        }
#endif
#ifdef FX_ZONEMOTION
        if(uZoneMotion.x>0.0){
            float zone=maskValue(uv,uMaskZonemotion,0.0,1.0);
            float phase=TAU*uT*uZoneMotion.y;
            vec2 direction=vec2(cos(uZoneMotion.w),sin(uZoneMotion.w));
            vec2 motion=direction*sin(phase)*uZoneMotion.x;
            if(uZoneMotion.z>0.5 && uZoneMotion.z<1.5)
                motion=direction*sin(phase+dot(Q,vec2(-direction.y,direction.x))*18.0)*uZoneMotion.x;
            if(uZoneMotion.z>1.5)
                motion=vec2(0.0,-sin(phase)*uZoneMotion.x*(0.35+0.65*smoothstep(0.0,1.0,uv.y)));
            d+=motion*zone;
        }
#endif
#ifdef FX_GUST
        if (uGust.x > 0.0) {
            vec2 gdir = normalize(uMotionGust.xy + vec2(1e-5, 0.0));
            float genv = windowEnv(uGust.y, uGust.z) * uGust.x;
            d += gdir * (0.5 + 0.5 * sin(TAU * uT * uGust.w * 2.0 + Q.y * 18.0)) * genv * 0.004 *
                maskValue(uv, uMaskGust, 1.0, 0.0);
        }
#endif
#ifdef FX_SHORE
        if (uShore.x > 0.0) {
            vec2 sdir = normalize(uMotionShore.xy + vec2(1e-5, 0.0));
            d += sdir * sin(TAU * (dot(Q, sdir) * uShore.y - uT * uShore.z)) * 0.002 * uShore.x *
                maskValue(uv, uMaskShore, 1.0, 0.0);
        }
#endif
        // v40.1: aire caliente sobre el fuego (refracción suave que ondula hacia arriba).
#ifdef FX_FLAME
        if (uFxFlame.x > 0.0) {
            vec4 fb = uFlameBox;
            bool fWhole = fb.z <= fb.x + 0.001;
            if (fWhole) fb = vec4(0.0, 0.88, 1.0, 1.0);
            vec2 fs = max(fb.zw - fb.xy, vec2(0.02));
            float fH = fWhole ? 0.14 + 0.42 * clamp(uFlameShape.w, 0.0, 1.0)
                              : min(fs.y * (1.2 + 2.6 * clamp(uFlameShape.w, 0.0, 1.0)) + 0.07, 0.72);
            float fh = (fb.w - uv.y) / fH;
            float fx = abs((uv.x - (fb.x + fb.z) * 0.5) / max(fs.x * 0.6, 0.04));
            float hz = smoothstep(0.35, 0.8, fh) * (1.0 - smoothstep(1.1, 1.7, fh)) * (1.0 - smoothstep(0.7, 1.15, fx));
            if (hz > 0.001) {
                float fph = TAU * uT * max(1.0, uFxFlame.z) * 2.0;
                vec2 wq = Q * vec2(26.0, 40.0);
                d += vec2(sin(wq.y - fph + sin(wq.x * 0.6)), 0.4 * cos(wq.x * 0.8 - fph)) *
                    0.0022 * hz * clamp(uFxFlame.x, 0.0, 1.5);
            }
        }
#endif
        d *= move;

        // Flujo (flechas) + deriva, con fundido de dos fases
        vec2 f = vec2(0.0);
#ifdef FX_FLOW
        if (uFlow3.x > 0.5) {
            f += texture(uFlow, uv).rg * uFlow3.y * maskValue(uv, uMaskFlow, z1, 0.0);
        }
#endif
#ifdef FX_DRIFT
        f += uDrift * maskValue(uv, uMaskDrift, z1, 1.0);
#endif
#ifdef FX_TORNADO
        if (uTornado.x > 0.0) {
            vec2 tc = uMotionTornado.zw;
            vec2 rel = (uv - tc) * vec2(uAspect, 1.0);
            float tr = length(rel);
            float TR = max(0.05, uTornado.y);
            float tfall = (1.0 - smoothstep(TR * 0.2, TR, tr)) * smoothstep(0.0, TR * 0.25, tr);
            vec2 tang = vec2(-rel.y, rel.x) / max(tr, 1e-4);
            float kind = uTornado.w > 0.5 ? 0.4 : 1.0;
            f += tang * uTornado.x * 0.18 * tfall * kind / vec2(uAspect, 1.0) * maskValue(uv, uMaskTornado, 1.0, 0.0);
        }
#endif
        f *= move;

        vec2 base = uv + d * asp;
        if (dot(f, f) > 1e-12) {
            float phase=uT*uFlow3.z+uLoopControl.z;
            if(uLoopControl.x<0.5){
                // v44.17: fase desplazada por zonas (ruido suave, fijo en el tiempo): el fundido de
                // las dos fases ya no ocurre a la vez en toda la imagen, así desaparece el «latido»
                // de doble imagen y el movimiento se ve continuo, como filmado. El bucle sigue cerrando.
                phase += vnoise(uv * vec2(uAspect, 1.0) * 2.3 + 7.1);
                float p1=fract(phase),p2=fract(p1+0.5);
                float w1=1.0-abs(2.0*p1-1.0);
                float blend=max(0.05,uLoopControl.y);
                if(blend<0.999)w1=smoothstep(0.5-blend*0.5,0.5+blend*0.5,w1);
                col=mix(warpSample(uv,d*asp-f*(p2-0.5)),warpSample(uv,d*asp-f*(p1-0.5)),w1);
            } else if(uLoopControl.x<1.5){
                col=warpSample(uv,d*asp-f*(0.5*sin(TAU*phase)));
            } else {
                col=warpSample(uv,d*asp-f*(0.5-0.5*cos(TAU*phase)));
            }
        } else {
            col = warpSample(uv,d*asp);
        }

        // Brillos en el agua
#ifdef FX_GLINT
        if (uGlint.x > 0.0) {
            vec2 g = Q * uGlint.y;
            vec2 id = floor(g);
            vec2 fq = fract(g);
            vec2 h = hash22(id + 4.4);
            vec2 c = 0.2 + 0.6 * h;
            float dd = length((fq - c) * vec2(0.55, 2.2));
            float tw = 0.5 + 0.5 * sin(TAU * (uT * uGlint.z + h.x));
            float gli = falloff(dd, 0.14) * tw * tw * tw * tw * step(0.5, h.y);
            col += vec3(1.0, 0.98, 0.9) * gli * uGlint.x * maskValue(uv, uMaskGlint, z1, 1.0) * move;
        }
#endif
    }

    if (uOverlayOnly < 0.5) {
#ifdef FX_WATER
        if(uStudioWater.x>0.0){
            // v37 · Agua: superficie de ruido desplazada en dos fases (flow map), así las ondas
            // fluyen de forma continua sin "respirar" ni saltar al repetir. El gradiente de la
            // altura desplaza la imagen (refracción) y los crestas dan brillos y espuma.
            float wm = maskValue(uv,uMaskWater,z1,1.0)*move;
            if (wm > 0.001) {
                float cyc = max(1.0, uStudioWater.y);
                float style = floor(uStudioWater.z + 0.5);
                vec2 dir = vec2(cos(uStudioWater.w), sin(uStudioWater.w));
                vec2 q = uv * vec2(uAspect, 1.0) * (style > 0.5 && style < 1.5 ? vec2(9.0, 18.0) : vec2(14.0, 26.0));
                float p1 = fract(uT * cyc), p2 = fract(p1 + 0.5);
                float wA = 1.0 - abs(2.0 * p1 - 1.0);
                vec2 flowv = dir * (style > 0.5 && style < 1.5 ? 2.2 : 0.9);
                vec2 qa = q - flowv * (p1 - 0.5), qb = q - flowv * (p2 - 0.5) + vec2(3.7, 1.9);
                float e = 0.35;
                float ha = fbm3(qa), hb = fbm3(qb);
                vec2 ga = vec2(fbm3(qa + vec2(e, 0.0)) - ha, fbm3(qa + vec2(0.0, e)) - ha) / e;
                vec2 gb = vec2(fbm3(qb + vec2(e, 0.0)) - hb, fbm3(qb + vec2(0.0, e)) - hb) / e;
                vec2 grad = mix(gb, ga, wA);
                float height = mix(hb, ha, wA);
                vec2 offset = grad * 0.011 * uStudioWater.x / vec2(uAspect, 1.0);
                vec3 water = warpSample(uv, offset);
                float crest = smoothstep(0.62, 0.78, height);
                float glint = pow(clamp(dot(normalize(vec3(-grad, 1.4)), normalize(vec3(-0.4, -0.6, 0.7))), 0.0, 1.0), 24.0);
                water += vec3(1.0, 0.98, 0.92) * glint * (style > 1.5 ? 0.9 : 0.35) * uStudioWater.x;
                if (style > 0.5 && style < 1.5) water = mix(water, mix(water, vec3(1.0), 0.55), crest * 0.35 * uStudioWater.x);
                col = mix(col, water, wm);
            }
        }
#endif
#ifdef FX_SKYDRIFT
        if (uFxSkydrift.x > 0.0) col = skydriftLayer(col, uv, maskValue(uv, uMaskSkydrift, 1.0, 0.0) * move);
#endif
#ifdef FX_SEAWAVES
        if (uFxSeawaves.x > 0.0) col = seawavesLayer(col, uv, maskValue(uv, uMaskSeawaves, 1.0, 0.0) * move);
#endif
#ifdef FX_SKY
        if(uStudioSky.x>0.0)col=mix(col,studioSky(uv),uStudioSky.x*maskValue(uv,uMaskSky,0.0,1.0));
#endif
#ifdef FX_DISPERSION
        if(uStudioDispersion.x>0.0)col=studioDisperse(col,uv);
#endif
#ifdef FX_CLOUDS3
        if (uClouds3.x > 0.0) col = clouds3Layer(col, P, maskValue(uv, uMaskClouds3, 1.0, 0.0) * move);
#endif
#ifdef FX_SHORE
        if (uShore.x > 0.0) col = shoreLayer(col, Q, maskValue(uv, uMaskShore, 1.0, 0.0) * move);
#endif
#ifdef FX_SUNPATH
        if (uSunpath.x > 0.0) col = sunpathLayer(col, P, maskValue(uv, uMaskSunpath, 1.0, 0.0));
#endif
    }
#ifdef FX_FOCUSBLUR
    if (uFxFocusblur.x > 0.0) col = focusblurLayer(col, uv, s);
#endif
#ifdef FX_RADIALBLUR
    if (uFxRadialblur.x > 0.0) col = radialblurLayer(col, uv);
#endif
#ifdef FX_GRADMAP
    if (uFxGradmap.x > 0.0) col = gradmapLayer(col, maskValue(uv, uMaskGradmap, 1.0, 0.0));
#endif
#ifdef FX_ATMOS
    vec3 gb70 = col;   // v44.11: color propio del efecto
    if (uFxAtmos.x > 0.0) col = atmosLayer(col, uv, P, maskValue(uv, uMaskAtmos, 1.0, 0.0));
    col = fxGrade(gb70, col, uGrade[56]);
#endif
    float bgL = luma(col);
#ifdef FX_CAM
    if (uMotionBlur > 0.0 && (uCam.x > 0.0 || uCam.y > 0.0)) col = cameraBlur(col, s, uv);
#endif
    vec3 cineBase = col;   // v44.19 · imagen antes de las capas de luz/partículas

#ifdef FX_RAYS
    vec3 gb0 = col;   // v44.11: color propio del efecto
    if (uRays.x > 0.0) {
        float g = maskValue(uv, uMaskRays, z1, uRays.w);
        float style = floor(uLightStyles.x + 0.5);
        float beam = raysField(P, uRays.y, uRays.z);
        vec3 rc = vec3(1.0, 0.92, 0.75);
        if (style > 0.5 && style < 1.5) { // contraluz concentrado
            float radius = length(P - uMotionRays.zw * vec2(uAspect, 1.0));
            beam = pow(max(0.0, beam), 0.70) * exp(-radius * 0.35);
            rc = vec3(1.0, 0.96, 0.87);
        } else if (style > 1.5 && style < 2.5) { // atardecer cálido
            beam = pow(max(0.0, beam), 0.9) * 0.85;
            rc = vec3(1.0, 0.54, 0.25);
        } else if (style > 2.5 && style < 3.5) { // franjas paralelas de ventana
            float bar = 0.5 + 0.5 * sin((P.x * 0.75 + P.y * 0.66) * 24.0);
            beam = pow(bar, 10.0) * (0.5 + 0.5 * sin(TAU * uT * uRays.z)) * 0.65;
            rc = vec3(1.0, 0.96, 0.84);
        } else if (style > 4.5) { // v44 · luz de luna: haces fríos, suaves y anchos
            beam = pow(max(0.0, beam), 1.15) * 0.75;
            rc = vec3(0.70, 0.80, 1.0);
        } else if (style > 3.5) { // haces volumétricos suaves
            vec2 diff = P - uMotionRays.zw * vec2(uAspect, 1.0);
            float ang = atan(diff.y, diff.x);
            float beams = 0.5 + 0.5 * cos(ang * 11.0 + 0.18 * sin(TAU * uT * uRays.z));
            beam = pow(beams, 7.0) * exp(-length(diff) * 0.9) * 0.70;
            rc = vec3(1.0, 0.90, 0.69);
        }
        if (uMono > 0.5) rc = vec3(1.0);
        // v38: el haz «respira» con niebla en movimiento (vuelve al inicio en el bucle).
        float shimmer = 0.65 + 0.7 * fogField(P * 2.2, 1.0, max(1.0, uRays.z));
        // v44.17: en los estilos de sol/luna los haces salen de la LUZ REAL de la imagen y las
        // nubes o el personaje los tapan; el dibujo anterior queda suave por debajo (imágenes oscuras).
        if (style < 2.5 || style > 4.5) {
            float scat = godRaysField(uv, uMotionRays.zw, max(1.0, uRays.z));
            // Sin una luz clara en el origen (imagen oscura), el haz dibujado sigue visible.
            float src = smoothstep(0.55, 0.85, luma(textureLod(uImage, clamp(uMotionRays.zw, vec2(0.0), vec2(1.0)), 5.0).rgb));
            beam = scat * 1.1 + beam * mix(1.0, 0.3, src) * (1.0 - clamp(scat * 2.5, 0.0, 1.0));
            shimmer = mix(shimmer, 1.0, 0.6);
        }
        // Mezcla «pantalla» (como la luz real): ilumina sin quemar el cielo a blanco.
        vec3 add = clamp(rc * beam * shimmer * uRays.x * g, 0.0, 1.0);
        col = 1.0 - (1.0 - clamp(col, 0.0, 1.0)) * (1.0 - add) + max(col - 1.0, 0.0);
    }
    col = fxGrade(gb0, col, uGrade[0]);
#endif
#ifdef FX_ENERGY
    vec3 gb1 = col;   // v44.11: color propio del efecto
    if (uEnergy.x > 0.0) {
        col = zetaEnergy(col, P, uv, maskValue(uv, uMaskEnergy, z1, uEnergy.w));
    }
    col = fxGrade(gb1, col, uGrade[1]);
#endif
#ifdef FX_SPEED
    vec3 gb2 = col;   // v44.11: color propio del efecto
    if (uSpeed.x > 0.0) {
        float g = maskValue(uv, uMaskSpeed, z1, uSpeed.w);
        vec3 sc = uMono > 0.5 ? vec3(1.0) : vec3(0.95, 0.88, 1.0);
        col += sc * speedField(P - uMotionSpeed.zw * vec2(uAspect, 1.0), degrees(atan(uMotionSpeed.y, uMotionSpeed.x)) - 90.0 + uSpeed.y, uSpeed.z) * uSpeed.x * g;
    }
    col = fxGrade(gb2, col, uGrade[2]);
#endif
#ifdef FX_STARS
    vec3 gb3 = col;   // v44.11: color propio del efecto
    if (uStars.x > 0.0) {
        float g = maskValue(uv, uMaskStars, z1, uStars.w);
        vec3 st = starsLayer(P, uStars.y, uStars.z);
        if (uMono > 0.5) {
            st = vec3(luma(st));
        }
        col += st * uStars.x * g;
        if (uStarsMore.x > 0.0) col += (uMono > 0.5 ? vec3(1.0) : vec3(0.92, 0.95, 1.0)) * shootingStars(P) * uStarsMore.x * uStars.x * g;
    }
    col = fxGrade(gb3, col, uGrade[3]);
#endif
#ifdef FX_FOG
    vec3 gb4 = col;   // v44.11: color propio del efecto
    if (uFog.x > 0.0) {
        float g = maskValue(uv, uMaskFog, z1, uFog.w);
        float shape = floor(uFogShape.x + 0.5);
        float turb = clamp(uFogShape.y, 0.0, 1.0);
        vec2 op = oriented(windWarp(P), uMotionFog);
        op += (vec2(vnoise(op * 1.3 + 4.0), vnoise(op * 1.3 + 9.0)) - 0.5) * turb * 0.6;
        float fv;
        if (shape < 0.5) {            // bancos que se desplazan (v44: alargados en horizontal + velo base)
            fv = smoothstep(0.3, 0.85, fogField(op * vec2(1.6, 0.55), uFog.y, uFog.z));
            fv = max(fv, uFogMore.y);
        } else if (shape < 1.5) {     // niebla baja pegada al suelo
            float f = fogField(op * vec2(1.0, 2.4), uFog.y * 0.8, uFog.z);
            float hgt = clamp(uFogMore.x, 0.1, 0.95);   // v44: altura de la niebla baja
            fv = smoothstep(0.22, 0.75, f) * smoothstep(1.0 - hgt, 1.0 - hgt * 0.35, uv.y) + 0.18 * smoothstep(1.0 - hgt * 0.6, 1.0, uv.y);
        } else {                      // bruma fina y uniforme
            fv = 0.25 + 0.55 * smoothstep(0.25, 0.8, fogField(op, uFog.y * 2.6, uFog.z));
        }
        fv *= emitArea(P, uMotionFog);
        vec3 fc = tintOr(vec3(0.86, 0.89, 0.95), uTintFog);
        if (uMono > 0.5) fc = vec3(1.0);
        col = mix(col, fc, clamp(fv * uFog.x * g, 0.0, 0.95));
    }
    col = fxGrade(gb4, col, uGrade[4]);
#endif
#ifdef FX_RAIN
    vec3 gb5 = col;   // v44.11: color propio del efecto
    if (uRain.x > 0.0) {
        float g = maskValue(uv, uMaskRain, z1, uRain.w);
        vec2 Pw = windWarp(P);
        float r = rainLayer(oriented(Pw, uMotionRain), 14.0, uRain.z + 2.0, uRain.y, 1.0)
                + 0.7 * rainLayer(oriented(Pw, uMotionRain), 24.0, uRain.z + 1.0, uRain.y, 17.0)
                + 0.45 * rainLayer(oriented(Pw, uMotionRain), 40.0, uRain.z, uRain.y, 41.0);
        vec3 rc = vec3(0.82, 0.88, 1.0);
        if (uMono > 0.5) {
            rc = bgL > 0.55 ? vec3(0.08) : vec3(0.97);
        }
        float ra = clamp(r, 0.0, 1.0) * uRain.x * g * emitArea(P, uMotionRain);
        // v38: ambiente húmedo (un poco más frío y apagado) y gotas en mezcla «pantalla»,
        // que aclaran sin lavar los colores.
        if (uMono < 0.5) col = mix(col, col * vec3(0.9, 0.93, 1.0), clamp(uRain.x, 0.0, 1.0) * g * 0.22);
        // v44.20 · sobre fondo claro la mezcla «pantalla» no se ve: ahí la gota oscurece un
        // poco (como la lluvia real contra un cielo blanco) y sobre oscuro aclara.
        vec3 rainLight = 1.0 - (1.0 - col) * (1.0 - rc);
        vec3 rainDark = col * (uMono > 0.5 ? vec3(0.72) : vec3(0.74, 0.78, 0.86));
        col = mix(col, mix(rainLight, rainDark, smoothstep(0.62, 0.86, luma(col))), ra);
        float ground = smoothstep(0.70, 0.95, uv.y);
        if (ground > 0.0) col += rc * rainSplash(P, uRain.z + 2.0) * ground * uRain.x * g * 0.45;
        // v44 · gotas CERCANAS (grandes, desenfocadas, más rápidas): dan profundidad.
        if (uRainMore.y > 0.0) {
            float near = rainLayer(oriented(P, uMotionRain) * 0.55, 6.0, uRain.z * 2.0 + 3.0, uRain.y, 97.0);
            col = mix(col, 1.0 - (1.0 - col) * (1.0 - rc * 0.85), clamp(near * 0.9, 0.0, 1.0) * uRainMore.y * uRain.x * g);
        }
        // v44 · neblina de lluvia lejana: la escena pierde contraste arriba, como a lo lejos.
        if (uRainMore.z > 0.0) {
            float haze = flowFbm(oriented(P, uMotionRain) * vec2(2.0, 0.8), vec2(0.0, 1.5), max(1.0, uRain.z));
            vec3 hc = uMono > 0.5 ? vec3(0.85) : vec3(0.78, 0.82, 0.88);
            col = mix(col, hc, clamp(uRainMore.z * uRain.x * g * (0.15 + 0.35 * haze) * (1.0 - 0.6 * smoothstep(0.4, 1.0, uv.y)), 0.0, 0.6));
        }
        if (uRainMore.x > 0.0) col = rainPuddles(col, uv, P, clamp(uRainMore.x * uRain.x, 0.0, 1.0) * g, max(1.0, uRain.z));
    }
    col = fxGrade(gb5, col, uGrade[5]);
#endif
#ifdef FX_SNOW
    vec3 gb6 = col;   // v44.11: color propio del efecto
    if (uSnow.x > 0.0) {
        float g = maskValue(uv, uMaskSnow, z1, uSnow.w);
        vec2 sp = oriented(windWarp(P), uMotionSnow);
        // v44 · remolinos: el campo gira un poco alrededor de varios centros (va y vuelve: bucle).
        if (uSnowMore.x > 0.0) {
            vec2 c = floor(sp * 1.5) + 0.5;
            vec2 rel = sp * 1.5 - c;
            float ang = uSnowMore.x * 1.4 * sin(TAU * uT + hash12(c) * TAU) * exp(-dot(rel, rel) * 3.0);
            sp = (c + mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * rel) / 1.5;
        }
        float sn = snowLayer(sp, 6.0, uSnow.z + 2.0, uSnow.y * 1.6, 3.0, 0.9)
                 + snowLayer(sp, 11.0, uSnow.z + 1.0, uSnow.y * 1.2, 11.0, 0.2)
                 + 0.8 * snowLayer(sp, 19.0, uSnow.z, uSnow.y, 29.0, 0.1)
                 + 0.55 * snowLayer(sp, 34.0, uSnow.z, uSnow.y * 0.75, 53.0, 0.0);
        vec3 snowC = uMono > 0.5 ? vec3(1.0) : vec3(0.97, 0.98, 1.0) * mix(vec3(1.0), sceneTint(uv), 0.6);
        col = mix(col, snowC, clamp(sn, 0.0, 1.0) * uSnow.x * g * emitArea(P, uMotionSnow));
        // v44.20 · copos muy cercanos como «bokeh»: discos grandes, transparentes y suaves.
        float bk = snowLayer(sp * 0.5 + vec2(0.23, 0.41), 4.0, uSnow.z * 2.0 + 2.0, uSnow.y * 1.3, 211.0, 1.0);
        col = mix(col, snowC, clamp(bk, 0.0, 1.0) * 0.30 * uSnow.x * g * emitArea(P, uMotionSnow));
        // v44 · bruma de ventisca: velo blanco que corre con el viento.
        if (uSnowMore.y > 0.0) {
            float b = flowFbm(sp * vec2(1.2, 0.6), vec2(0.0, 1.0), max(1.0, uSnow.z));
            col = mix(col, snowC * 0.92, clamp(uSnowMore.y * uSnow.x * g * (0.12 + 0.38 * smoothstep(0.35, 0.8, b)), 0.0, 0.7));
        }
    }
    col = fxGrade(gb6, col, uGrade[6]);
#endif
#ifdef FX_SAKURA
    vec3 gb7 = col;   // v44.11: color propio del efecto
    if (uSakura.x > 0.0) {
        float g = maskValue(uv, uMaskSakura, z1, uSakura.w);
        vec4 pa = sakuraLayer(oriented(windWarp(P), uMotionSakura), 5.0, uSakura.z + 1.0, uSakura.y * 1.2, 7.0);
        vec4 pb = sakuraLayer(oriented(windWarp(P), uMotionSakura), 9.0, uSakura.z, uSakura.y, 61.0);
        // v44.20 · pétalos CERCANOS: grandes, desenfocados y rápidos (como con lente abierta).
        vec4 pn = sakuraLayerX(oriented(windWarp(P), uMotionSakura) + vec2(0.31, 0.17), 3.0, uSakura.z * 2.0 + 2.0, uSakura.y * 1.1, 113.0, 1.0);
        vec3 tintS = uMono > 0.5 ? vec3(1.0) : sceneTint(uv);
        vec3 ca = pa.rgb * tintS;
        vec3 cb = pb.rgb * tintS;
        vec3 cn = pn.rgb * tintS;
        if (uMono > 0.5) {
            ca = vec3(luma(ca));
            cb = vec3(luma(cb));
            cn = vec3(luma(cn));
        }
        float kS = uSakura.x * g * emitArea(P, uMotionSakura);
        col = mix(col, cb, pb.a * kS);
        col = mix(col, ca, pa.a * kS);
        col = mix(col, cn, pn.a * kS * 0.85);
    }
    col = fxGrade(gb7, col, uGrade[7]);
#endif
#ifdef FX_FLIES
    vec3 gb8 = col;   // v44.11: color propio del efecto
    if (uFlies.x > 0.0) {
        float g = maskValue(uv, uMaskFlies, z1, uFlies.w);
        vec3 fl = fliesLayer(P, uFlies.y, uFlies.z);
        if (uMono > 0.5) {
            fl = vec3(luma(fl));
        }
        col += fl * uFlies.x * g;
    }
    col = fxGrade(gb8, col, uGrade[8]);
#endif
#ifdef FX_CLOUDSHADOW
    vec3 gb9 = col;   // v44.11: color propio del efecto
    if (uFxCloudshadow.x > 0.0) col = cloudShadowLayer(col, Q, maskValue(uv, uMaskCloudshadow, z1, uFxCloudshadow.w));
    col = fxGrade(gb9, col, uGrade[9]);
#endif
#ifdef FX_AURORA
    vec3 gb10 = col;   // v44.11: color propio del efecto
    if (uFxAurora.x > 0.0) col = auroraLayer(col, Q, maskValue(uv, uMaskAurora, z1, uFxAurora.w));
    col = fxGrade(gb10, col, uGrade[10]);
#endif
#ifdef FX_CITYLIGHTS
    vec3 gb11 = col;   // v44.11: color propio del efecto
    if (uFxCitylights.x > 0.0) col = cityLightsLayer(col, uv, Q, maskValue(uv, uMaskCitylights, z1, uFxCitylights.w));
    col = fxGrade(gb11, col, uGrade[11]);
#endif
#ifdef FX_BIRDS
    vec3 gb12 = col;   // v44.11: color propio del efecto
    if (uFxBirds.x > 0.0) col = birdsLayer(col, P, maskValue(uv, uMaskBirds, z1, uFxBirds.w));
    col = fxGrade(gb12, col, uGrade[12]);
#endif
#ifdef FX_SAND
    vec3 gb13 = col;   // v44.11: color propio del efecto
    if (uFxSand.x > 0.0) col = sandLayer(col, P, maskValue(uv, uMaskSand, z1, uFxSand.w));
    col = fxGrade(gb13, col, uGrade[13]);
#endif

    // Partículas de atmósfera con color propio y origen/dirección independientes.
#ifdef FX_ASH
    vec3 gb14 = col;   // v44.11: color propio del efecto
    if (uFxAsh.x > 0.0) {
        float v = particleField(oriented(windWarp(P), uMotionAsh), 13.0, uFxAsh.z, uFxAsh.y, 13.0, 3.0).x;
        col = mix(col, vec3(0.66) * (uMono > 0.5 ? vec3(1.0) : sceneTint(uv)), v * uFxAsh.x * maskValue(uv, uMaskAsh, z1, uFxAsh.w) * emitArea(P, uMotionAsh));
    }
    col = fxGrade(gb14, col, uGrade[14]);
#endif
#ifdef FX_LEAVES
    vec3 gb15 = col;   // v44.11: color propio del efecto
    if (uFxLeaves.x > 0.0) {
        vec2 lp = windWarp(P);
        // v44 · remolino de hojas alrededor del origen (va y vuelve: el bucle se cierra).
        if (uLeavesMore.x > 0.0) {
            vec2 o = uMotionLeaves.zw * vec2(uAspect, 1.0);
            vec2 rel = lp - o;
            float ang = uLeavesMore.x * 2.2 * exp(-dot(rel, rel) * 6.0) * sin(TAU * uT);
            lp = o + mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * rel;
        }
        // v44.20 · dos planos: lejos (pequeñas, nítidas) y cerca (grandes, desenfocadas, el
        // doble de rápidas). Cada hoja con sombreado, nervio, borde y la luz de la escena.
        float kL = uFxLeaves.x * maskValue(uv, uMaskLeaves, z1, uFxLeaves.w) * emitArea(P, uMotionLeaves);
        vec3 tintL = uMono > 0.5 ? vec3(1.0) : sceneTint(uv);
        vec4 lf = particleFieldX(oriented(lp, uMotionLeaves), 7.0, uFxLeaves.z, uFxLeaves.y, 33.0, 1.0, 0.0);
        vec3 lcF = leafPalette(uLeavesStyle.x, P) * lf.z * tintL;
        col = mix(col, mix(lcF, lcF * 0.45, lf.w * 0.8), lf.x * kL);
        vec4 ln = particleFieldX(oriented(lp, uMotionLeaves) * 0.5 + vec2(0.37, 0.11), 7.0, uFxLeaves.z * 2.0, uFxLeaves.y * 1.15, 133.0, 1.0, 1.0);
        vec3 lcN = leafPalette(uLeavesStyle.x, P + 0.3) * ln.z * tintL;
        col = mix(col, lcN, ln.x * kL * 0.9);
    }
    col = fxGrade(gb15, col, uGrade[15]);
#endif
#ifdef FX_EMBERS
    vec3 gb16 = col;   // v44.11: color propio del efecto
    if (uFxEmbers.x > 0.0) {
        vec2 pe = particleField(oriented(windWarp(P), uMotionEmbers), 17.0, uFxEmbers.z, uFxEmbers.y, 51.0, 0.0);
        float ke = uFxEmbers.x * maskValue(uv, uMaskEmbers, z1, uFxEmbers.w) * emitArea(P, uMotionEmbers);
        col += (uMono > 0.5 ? vec3(1.0) : vec3(1.0, 0.42, 0.08)) * pe.y * ke;
        col += (uMono > 0.5 ? vec3(1.0) : vec3(1.0, 0.82, 0.45)) * pe.x * ke;
        // v44.20 · brasas CERCANAS: pocas, grandes, desenfocadas y rápidas (profundidad).
        vec4 pn = particleFieldX(oriented(windWarp(P), uMotionEmbers) * 0.45 + vec2(0.21, 0.6), 17.0, uFxEmbers.z * 2.0, uFxEmbers.y * 1.4, 151.0, 0.0, 1.0);
        col += (uMono > 0.5 ? vec3(1.0) : vec3(1.0, 0.55, 0.18)) * (pn.x * 0.55 + pn.y * 0.6) * ke;
    }
    col = fxGrade(gb16, col, uGrade[16]);
#endif
#ifdef FX_DUST
    vec3 gb17 = col;   // v44.11: color propio del efecto
    if (uFxDust.x > 0.0) {
        vec2 pd = particleField(oriented(windWarp(P), uMotionDust), 14.0, uFxDust.z, uFxDust.y, 71.0, 2.0);
        col += vec3(1.0, 0.94, 0.8) * (pd.x + pd.y) * uFxDust.x * maskValue(uv, uMaskDust, z1, uFxDust.w) * emitArea(P, uMotionDust);
    }
    col = fxGrade(gb17, col, uGrade[17]);
#endif
#ifdef FX_BUBBLES
    vec3 gb18 = col;   // v44.11: color propio del efecto
    if (uFxBubbles.x > 0.0) {
        vec2 g = oriented(P, uMotionBubbles) * 10.0;
        g.y -= uT * uFxBubbles.z * 6.0;
        vec2 h = hash22(vec2(floor(g.x), mod(floor(g.y), 6.0)) + 81.0);
        float r = length(fract(g) - (0.2 + 0.6 * h));
        float rim = (1.0 - smoothstep(0.008, 0.025, abs(r - uFxBubbles.y * 0.10))) * step(0.5, h.x);
        col += vec3(0.62, 0.87, 1.0) * rim * uFxBubbles.x *
            maskValue(uv, uMaskBubbles, z1, uFxBubbles.w) * emitArea(P, uMotionBubbles);
    }
    col = fxGrade(gb18, col, uGrade[18]);
#endif
#ifdef FX_ELECTRIC
    vec3 gb19 = col;   // v44.11: color propio del efecto
    if (uFxElectric.x > 0.0) {
        float v = electricArc(P, uMotionElectric, uFxElectric.y, uFxElectric.z);
        float ke = uFxElectric.x * maskValue(uv, uMaskElectric, z1, uFxElectric.w);
        col += (uMono > 0.5 ? vec3(1.0) : vec3(0.35, 0.78, 1.0)) * v * ke;
        col = mix(col, vec3(0.92, 0.97, 1.0), clamp(smoothstep(0.9, 1.2, v) * ke, 0.0, 1.0));
    }
    col = fxGrade(gb19, col, uGrade[19]);
#endif
#ifdef FX_FOCUS
    vec3 gb20 = col;   // v44.11: color propio del efecto
    if (uFxFocus.x > 0.0) {
        float v = focusLines(P, uMotionFocus, uFxFocus.y, uFxFocus.z);
        col = mix(col, vec3(uMono > 0.5 ? 0.0 : 0.92), clamp(v*uFxFocus.x*maskValue(uv, uMaskFocus, z1, uFxFocus.w),0.0,1.0));
    }
    col = fxGrade(gb20, col, uGrade[20]);
#endif
#ifdef FX_SHOCK
    vec3 gb21 = col;   // v44.11: color propio del efecto
    if (uFxShock.x > 0.0) {
        float r = length(P - uMotionShock.zw * vec2(uAspect, 1.0));
        float phase = uEventPhase2.y >= 0.0 ? uEventPhase2.y : fract(uT * uFxShock.z);
        // Onda irregular: radio y grosor varían con el ángulo, borde de luz delante y
        // sombra detrás (parece aire comprimido, no un anillo dibujado con compás).
        vec2 sq = P - uMotionShock.zw * vec2(uAspect, 1.0);
        float sang = atan(sq.y, sq.x);
        float wob = (vnoise(vec2(sang * 3.2, 7.0)) - 0.5) * 0.08 * phase;
        float R = pow(phase, 0.75) * 0.9 + wob;
        float th = max(uFxShock.y, 0.004) * (0.55 + 0.9 * vnoise(vec2(sang * 5.0, 3.0))) * (0.6 + phase);
        float dd = r - R;
        float front = exp(-pow(dd / th, 2.0));
        float back = exp(-pow((dd + th * 1.6) / (th * 1.4), 2.0));
        float breakup = 0.45 + 0.55 * smoothstep(0.25, 0.65, vnoise(vec2(sang * 9.0, phase * 4.0)));
        float fade = pow(1.0 - phase, 1.5);
        float ks = uFxShock.x * maskValue(uv, uMaskShock, z1, uFxShock.w);
        col *= 1.0 - back * 0.22 * fade * ks;
        col += vec3(0.75, 0.9, 1.0) * front * breakup * fade * ks;
    }
    col = fxGrade(gb21, col, uGrade[21]);
#endif
#ifdef FX_FLASH
    vec3 gb22 = col;   // v44.11: color propio del efecto
    if (uFxFlash.x > 0.0) {
        float phase = uEventPhase2.x >= 0.0 ? uEventPhase2.x : fract(uT*uFxFlash.z);
        float burst = 1.0 - smoothstep(0.0, uFxFlash.y, phase);
        col = mix(col, vec3(1.0), clamp(burst*uFxFlash.x*maskValue(uv, uMaskFlash, z1, uFxFlash.w),0.0,1.0));
    }
    col = fxGrade(gb22, col, uGrade[22]);
#endif
#ifdef FX_TONE
    vec3 gb23 = col;   // v44.11: color propio del efecto
    if (uFxTone.x > 0.0) {
        vec2 grid = fract(P * uFxTone.y);
        float dotTone = 1.0 - smoothstep(0.1, 0.38, length(grid-0.5));
        float pulse = 0.9 + 0.1*sin(TAU*uT*uFxTone.z);
        float k = uFxTone.x*maskValue(uv, uMaskTone, z1, uFxTone.w);
        col = mix(col, vec3(dotTone*0.10 + (1.0-dotTone)*0.98)*pulse, k);
    }
    col = fxGrade(gb23, col, uGrade[23]);
#endif
#ifdef FX_GRAIN
    vec3 gb24 = col;   // v44.11: color propio del efecto
    if (uFxGrain.x > 0.0) {
        float n = hash12(floor(P*uFxGrain.y)+vec2(floor(mod(uT*uFxGrain.z*12.0,12.0)),0.0));
        col += (n-0.5)*uFxGrain.x*maskValue(uv, uMaskGrain, z1, uFxGrain.w);
    }
    col = fxGrade(gb24, col, uGrade[24]);
#endif
#ifdef FX_INK
    vec3 gb25 = col;   // v44.11: color propio del efecto
    if (uFxInk.x > 0.0) {
        float ink = smoothstep(uFxInk.y-0.035,uFxInk.y+0.035,luma(col));
        col = mix(col,vec3(ink),uFxInk.x*maskValue(uv, uMaskInk, z1, uFxInk.w));
    }
    col = fxGrade(gb25, col, uGrade[25]);
#endif

    // Instancias B; conserva el orden, las máscaras y los parámetros de cada copia.
#ifdef FX_ASH
    vec3 gb26 = col;   // v44.11: color propio del efecto
    if (uFxAshCopy.x > 0.0) {
        float v = particleField(oriented(P, uMotionAshCopy), 13.0, uFxAshCopy.z, uFxAshCopy.y, 13.0, 3.0).x;
        col = mix(col, vec3(0.66), v * uFxAshCopy.x * maskValue(uv, uMaskAshCopy, z1, uFxAshCopy.w) * emitArea(P, uMotionAshCopy));
    }
    col = fxGrade(gb26, col, uGrade[14]);
#endif
#ifdef FX_LEAVES
    vec3 gb27 = col;   // v44.11: color propio del efecto
    if (uFxLeavesCopy.x > 0.0) {
        float v = particleField(oriented(P, uMotionLeavesCopy), 7.0, uFxLeavesCopy.z, uFxLeavesCopy.y, 33.0, 1.0).x;
        col = mix(col, leafPalette(uLeavesStyle.y, P + vec2(0.17, 0.31)),
            v * uFxLeavesCopy.x * maskValue(uv, uMaskLeavesCopy, z1, uFxLeavesCopy.w) * emitArea(P, uMotionLeavesCopy));
    }
    col = fxGrade(gb27, col, uGrade[15]);
#endif
#ifdef FX_EMBERS
    vec3 gb28 = col;   // v44.11: color propio del efecto
    if (uFxEmbersCopy.x > 0.0) {
        float v = particleField(oriented(P, uMotionEmbersCopy), 17.0, uFxEmbersCopy.z, uFxEmbersCopy.y, 51.0, 0.0).x;
        col += (uMono > 0.5 ? vec3(1.0) : vec3(1.0, 0.48, 0.09)) * v * uFxEmbersCopy.x *
            maskValue(uv, uMaskEmbersCopy, z1, uFxEmbersCopy.w) * emitArea(P, uMotionEmbersCopy);
    }
    col = fxGrade(gb28, col, uGrade[16]);
#endif
#ifdef FX_DUST
    vec3 gb29 = col;   // v44.11: color propio del efecto
    if (uFxDustCopy.x > 0.0) {
        float v = particleField(oriented(P, uMotionDustCopy), 14.0, uFxDustCopy.z, uFxDustCopy.y, 71.0, 2.0).x;
        col += vec3(0.85, 0.81, 0.73) * v * uFxDustCopy.x * maskValue(uv, uMaskDustCopy, z1, uFxDustCopy.w) * emitArea(P, uMotionDustCopy);
    }
    col = fxGrade(gb29, col, uGrade[17]);
#endif
#ifdef FX_BUBBLES
    vec3 gb30 = col;   // v44.11: color propio del efecto
    if (uFxBubblesCopy.x > 0.0) {
        vec2 g = oriented(P, uMotionBubblesCopy) * 10.0;
        g.y -= uT * uFxBubblesCopy.z * 6.0;
        vec2 h = hash22(vec2(floor(g.x), mod(floor(g.y), 6.0)) + 81.0);
        float r = length(fract(g) - (0.2 + 0.6 * h));
        float rim = (1.0 - smoothstep(0.008, 0.025, abs(r - uFxBubblesCopy.y * 0.10))) * step(0.5, h.x);
        col += vec3(0.62, 0.87, 1.0) * rim * uFxBubblesCopy.x *
            maskValue(uv, uMaskBubblesCopy, z1, uFxBubblesCopy.w) * emitArea(P, uMotionBubblesCopy);
    }
    col = fxGrade(gb30, col, uGrade[18]);
#endif
#ifdef FX_ELECTRIC
    vec3 gb31 = col;   // v44.11: color propio del efecto
    if (uFxElectricCopy.x > 0.0) {
        float v = electricArc(P, uMotionElectricCopy, uFxElectricCopy.y, uFxElectricCopy.z);
        col += (uMono > 0.5 ? vec3(1.0) : vec3(0.27, 0.80, 1.0)) * v * uFxElectricCopy.x * maskValue(uv, uMaskElectricCopy, z1, uFxElectricCopy.w);
    }
    col = fxGrade(gb31, col, uGrade[19]);
#endif
#ifdef FX_FOCUS
    vec3 gb32 = col;   // v44.11: color propio del efecto
    if (uFxFocusCopy.x > 0.0) {
        float v = focusLines(P, uMotionFocusCopy, uFxFocusCopy.y, uFxFocusCopy.z);
        col = mix(col, vec3(uMono > 0.5 ? 0.0 : 0.92), clamp(v*uFxFocusCopy.x*maskValue(uv, uMaskFocusCopy, z1, uFxFocusCopy.w),0.0,1.0));
    }
    col = fxGrade(gb32, col, uGrade[20]);
#endif
#ifdef FX_SHOCK
    vec3 gb33 = col;   // v44.11: color propio del efecto
    if (uFxShockCopy.x > 0.0) {
        float r = length(P - uMotionShockCopy.zw * vec2(uAspect, 1.0));
        float phase = fract(uT * uFxShockCopy.z);
        float ring = (1.0 - smoothstep(uFxShockCopy.y * 0.4, uFxShockCopy.y, abs(r-phase*0.9)))*(1.0-phase);
        col += vec3(0.65, 0.86, 1.0) * ring*uFxShockCopy.x*maskValue(uv, uMaskShockCopy, z1, uFxShockCopy.w);
    }
    col = fxGrade(gb33, col, uGrade[21]);
#endif
#ifdef FX_FLASH
    vec3 gb34 = col;   // v44.11: color propio del efecto
    if (uFxFlashCopy.x > 0.0) {
        float phase = fract(uT*uFxFlashCopy.z);
        float burst = 1.0 - smoothstep(0.0, uFxFlashCopy.y, phase);
        col = mix(col, vec3(1.0), clamp(burst*uFxFlashCopy.x*maskValue(uv, uMaskFlashCopy, z1, uFxFlashCopy.w),0.0,1.0));
    }
    col = fxGrade(gb34, col, uGrade[22]);
#endif
#ifdef FX_TONE
    vec3 gb35 = col;   // v44.11: color propio del efecto
    if (uFxToneCopy.x > 0.0) {
        vec2 grid = fract(P * uFxToneCopy.y);
        float dotTone = 1.0 - smoothstep(0.1, 0.38, length(grid-0.5));
        float pulse = 0.9 + 0.1*sin(TAU*uT*uFxToneCopy.z);
        float k = uFxToneCopy.x*maskValue(uv, uMaskToneCopy, z1, uFxToneCopy.w);
        col = mix(col, vec3(dotTone*0.10 + (1.0-dotTone)*0.98)*pulse, k);
    }
    col = fxGrade(gb35, col, uGrade[23]);
#endif
#ifdef FX_GRAIN
    vec3 gb36 = col;   // v44.11: color propio del efecto
    if (uFxGrainCopy.x > 0.0) {
        float n = hash12(floor(P*uFxGrainCopy.y)+vec2(floor(mod(uT*uFxGrainCopy.z*12.0,12.0)),0.0));
        col += (n-0.5)*uFxGrainCopy.x*maskValue(uv, uMaskGrainCopy, z1, uFxGrainCopy.w);
    }
    col = fxGrade(gb36, col, uGrade[24]);
#endif
#ifdef FX_INK
    vec3 gb37 = col;   // v44.11: color propio del efecto
    if (uFxInkCopy.x > 0.0) {
        float ink = smoothstep(uFxInkCopy.y-0.035,uFxInkCopy.y+0.035,luma(col));
        col = mix(col,vec3(ink),uFxInkCopy.x*maskValue(uv, uMaskInkCopy, z1, uFxInkCopy.w));
    }
    col = fxGrade(gb37, col, uGrade[25]);
#endif


    // Segunda familia de efectos: máscara y dirección ajustables en el editor.
#ifdef FX_COMET
    vec3 gb38 = col;   // v44.11: color propio del efecto
    if (uFxComet.x > 0.0) {
        vec2 g = oriented(P, uMotionComet) * vec2(17.0, 7.0);
        g.y -= uT * uFxComet.z * 6.0;
        vec2 id = floor(g), f = fract(g);
        float presence = step(0.87, hash12(vec2(id.x, mod(id.y,6.0))));
        float x = 0.15 + 0.7*hash12(vec2(id.x, mod(id.y,6.0))+7.0);   // v40.1: periódico (sin salto al repetir)
        float trail = exp(-abs(f.x-x)*105.0) * smoothstep(0.05, 0.3, f.y) * (1.0-smoothstep(0.6,1.0,f.y));
        col += vec3(0.48,0.82,1.0)*trail*presence*uFxComet.y*uFxComet.x*
            maskValue(uv,uMaskComet,z1,uFxComet.w)*emitArea(P,uMotionComet);
    }
    col = fxGrade(gb38, col, uGrade[26]);
#endif
#ifdef FX_RUNES
    vec3 gb39 = col;   // v44.11: color propio del efecto
    if (uFxRunes.x > 0.0) {
        vec2 q = P-uMotionRunes.zw*vec2(uAspect,1.0);
        float radius = length(q), ang = atan(q.y,q.x)+TAU*uT*uFxRunes.z;
        float r = 0.17*uFxRunes.y;
        float ring = (1.0-smoothstep(0.002,0.009,abs(radius-r)));
        float marks = (1.0-smoothstep(0.01,0.045,abs(radius-r*0.84)))*step(0.7,fract(ang*12.0/TAU));
        float rim = ring + marks;
        col += vec3(0.56,0.79,1.0)*rim*uFxRunes.x*maskValue(uv,uMaskRunes,z1,uFxRunes.w);
    }
    col = fxGrade(gb39, col, uGrade[27]);
#endif
#ifdef FX_HATCH
    vec3 gb40 = col;   // v44.11: color propio del efecto
    if (uFxHatch.x > 0.0) {
        float lines = abs(fract((P.x+P.y)*uFxHatch.y + uT*uFxHatch.z)-0.5);
        float hatch = (1.0-smoothstep(0.02,0.045,lines));
        col = mix(col, vec3(uMono>0.5?0.0:0.13),hatch*uFxHatch.x*
            maskValue(uv,uMaskHatch,z1,uFxHatch.w));
    }
    col = fxGrade(gb40, col, uGrade[28]);
#endif
#ifdef FX_PULSE
    vec3 gb41 = col;   // v44.11: color propio del efecto
    if (uFxPulse.x > 0.0) {
        vec2 q=P-uMotionPulse.zw*vec2(uAspect,1.0);
        float d=length(q), phase=fract(uT*uFxPulse.z);
        float rings=0.0;
        for(int k=0;k<3;k++) {
            float r=fract(phase+float(k)/3.0)*0.65;
            rings+= (1.0-smoothstep(uFxPulse.y*0.25,uFxPulse.y,abs(d-r)))*(1.0-r);
        }
        col += vec3(0.52,0.88,1.0)*rings*uFxPulse.x*maskValue(uv,uMaskPulse,z1,uFxPulse.w);
    }
    col = fxGrade(gb41, col, uGrade[29]);
#endif
#ifdef FX_SMOKEBACK
    vec3 gb42 = col;   // v44.11: color propio del efecto (v44.15: solo si hay humo DETRÁS)
    if (uFxSmoke.x > 0.0 && uSmokeControl.w < 0.5)
        col = zetaSmoke(col, P, uv, z1);
#ifdef FX_SMOKE2
    if (uFxSmoke2.x > 0.0 && uSmokeControl2.w < 0.5)
        col = zetaSmoke2(col, P, uv, z1);
#endif
    col = fxGrade(gb42, col, uGrade[30]);
#endif
#ifdef FX_SHARDS
    vec3 gb43 = col;   // v44.11: color propio del efecto
    if (uFxShards.x > 0.0) {
        vec2 g=oriented(P,uMotionShards)*9.0;
        g.y-=uT*uFxShards.z*6.0;
        vec2 id=floor(g),f=fract(g);
        vec2 h=hash22(vec2(id.x,mod(id.y,6.0))+34.0);
        vec2 q=f-(0.2+0.6*h);
        float diamond=abs(q.x)+abs(q.y)*1.6;
        float v=1.0-smoothstep(uFxShards.y*0.035,uFxShards.y*0.06,diamond);
        col+=vec3(0.65,0.91,1.0)*v*step(0.62,h.x)*uFxShards.x*
            maskValue(uv,uMaskShards,z1,uFxShards.w)*emitArea(P,uMotionShards);
    }
    col = fxGrade(gb43, col, uGrade[31]);
#endif

    // Fuego por zona, ojos brillantes y cierre de sombra. Solo se calculan activos.
#ifdef FX_FLAME
    vec3 gb44 = col;   // v44.11: color propio del efecto
    if (uFxFlame.x > 0.0) col = zetaFlame(col, P, uv);
    col = fxGrade(gb44, col, uGrade[32]);
#endif
#ifdef FX_EYES
    vec3 gb45 = col;   // v44.11: color propio del efecto
    if (uFxEyes.x > 0.0) col = zetaEyes(col, uv);
    col = fxGrade(gb45, col, uGrade[33]);
#endif
#ifdef FX_BLINK
    // v44.20 · Parpadeo de verdad: el párpado BAJA. Dentro de la zona del ojo se mide la
    // distancia al borde de arriba y al de abajo; el párpado superior cubre hasta el 80 % y el
    // inferior sube un poco. Lo cubierto toma la piel de justo encima del ojo y en el borde
    // del párpado queda una línea de pestañas. Curva real: cierra rápido, abre más lento.
    if (uFxBlink.x > 0.0) {
        float em = maskValue(uv, uMaskBlink, z1, 1.0);
        if (em > 0.02) {
            float phase = fract(uT * uFxBlink.z);
            float width = max(0.02, uFxBlink.y);
            float x = (phase - 0.5) / width;
            float close = x < 0.05 ? smoothstep(-1.0, -0.35, x) : 1.0 - smoothstep(0.05, 1.0, x);
            close *= clamp(uFxBlink.x / 0.85, 0.0, 1.0);   // «Cierre» 0,85 (por defecto) = ojo cerrado del todo
            // capa de la zona del ojo (propia o la Zona 1 heredada); «toda la imagen» no
            // tiene bordes que medir: ahí queda el oscurecido rítmico de antes.
            int eyeLayer = uMaskBlink >= 64 ? uMaskBlink - 128 : uMaskBlink;
            if (eyeLayer == -1) eyeLayer = 1;
            if (close > 0.001 && eyeLayer < 0) col *= 1.0 - close * 0.85 * em;
            else if (close > 0.001) {
                // alcance 20 × 0,014 = 28 % del alto: sirve también para ojos de primer plano
                const float STEP = 0.014;
                float up = 20.0;
                float dn = 20.0;
                float pu = maskLayer(uv, eyeLayer);
                float pd = pu;
                for (int i = 1; i <= 20; i++) {
                    float fi = float(i);
                    // borde exacto entre dos pasos (interpolado): párpado liso, sin escalones
                    if (up > 19.5) {
                        float m = maskLayer(uv - vec2(0.0, fi * STEP), eyeLayer);
                        if (m < 0.5) up = fi - 1.0 + clamp((pu - 0.5) / max(pu - m, 1e-3), 0.0, 1.0);
                        pu = m;
                    }
                    if (dn > 19.5) {
                        float m = maskLayer(uv + vec2(0.0, fi * STEP), eyeLayer);
                        if (m < 0.5) dn = fi - 1.0 + clamp((pd - 0.5) / max(pd - m, 1e-3), 0.0, 1.0);
                        pd = m;
                    }
                    if (up < 19.5 && dn < 19.5) break;
                }
                float h = up + dn;
                float upperLid = close * h * 0.82;   // hasta dónde llega el párpado de arriba
                float lowerLid = close * h * 0.18;
                vec3 skin = sourcePixel(uv - vec2(0.0, (up + 1.5) * STEP));
                float covered = max(1.0 - smoothstep(upperLid - 0.18, upperLid + 0.18, up),
                                    1.0 - smoothstep(lowerLid - 0.18, lowerLid + 0.18, dn));
                float lash = exp(-pow((up - upperLid) / 0.25, 2.0)) * smoothstep(0.15, 0.5, close);
                vec3 lid = mix(col, skin * 0.97, covered);
                lid = mix(lid, skin * 0.25, lash * 0.85);
                col = mix(col, lid, em);
            }
        }
    }
#endif

    // v30: efectos de fondo localizados. Son baratos y solo calculan si están activos.
#ifdef FX_BOKEH
    vec3 gb46 = col;   // v44.11: color propio del efecto
    if (uFxBokeh.x > 0.0) {
        vec2 g = P * vec2(7.5, 9.5);
        vec2 id = floor(g), f = fract(g) - 0.5;
        vec2 h = hash22(id + 17.0);
        float bph = TAU * (uT * uFxBokeh.z + h.y);
        vec2 center = (h - 0.5) * 0.48 + vec2(sin(bph), cos(bph)) * 0.08;
        float r = length(f - center);
        float b = smoothstep(0.31, 0.02, r) * step(0.50, h.x);
        float tw = 0.55 + 0.45 * sin(TAU * (uT * uFxBokeh.z + h.y));
        float style = uFxBokeh.y;
        vec3 bc = vec3(1.0, 0.83, 0.58);
        if (style > 0.5 && style < 1.5) bc = vec3(1.0, 0.58, 0.77);
        else if (style > 1.5 && style < 2.5) bc = vec3(0.55, 0.78, 1.0);
        else if (style > 2.5) bc = mix(vec3(0.63,0.52,1.0), vec3(0.45,1.0,0.84), h.y);
        float a = b * tw * uFxBokeh.x * maskValue(uv, uMaskBokeh, z1, uFxBokeh.w) * 0.46;
        col += bc * a;
    }
    col = fxGrade(gb46, col, uGrade[34]);
#endif
#ifdef FX_SPARKLE
    vec3 gb47 = col;   // v44.11: color propio del efecto
    if (uFxSparkle.x > 0.0) {
        vec2 g = P * 13.0;
        vec2 id = floor(g), f = fract(g) - 0.5;
        vec2 h = hash22(id + 61.0);
        float on = step(0.78, h.x);
        float pulse = pow(0.5 + 0.5 * sin(TAU * (uT * uFxSparkle.z + h.y)), 5.0);
        float sx = exp(-abs(f.x - (h.x - 0.5) * 0.45) * (55.0 / max(0.3,uFxSparkle.y)));
        float sy = exp(-abs(f.y - (h.y - 0.5) * 0.45) * (55.0 / max(0.3,uFxSparkle.y)));
        float s = max(sx * exp(-abs(f.y)*8.0), sy * exp(-abs(f.x)*8.0));
        float a = s * on * pulse * uFxSparkle.x * maskValue(uv,uMaskSparkle,z1,uFxSparkle.w);
        col += vec3(1.0,0.96,0.82) * a * 0.72;
    }
    col = fxGrade(gb47, col, uGrade[35]);
#endif
#ifdef FX_LIGHTLEAK
    vec3 gb48 = col;   // v44.11: color propio del efecto
    if (uFxLightleak.x > 0.0) {
        float ph = 0.5 + 0.5 * sin(TAU * uT * uFxLightleak.z);
        float side = mix(-0.12, 1.12, ph);
        float band = exp(-abs(uv.x - side) * 4.2) * (0.55 + 0.45 * smoothstep(1.0,0.0,uv.y));
        float style = uFxLightleak.y;
        vec3 lc = vec3(1.0,0.55,0.30);
        if (style > 0.5 && style < 1.5) lc = vec3(1.0,0.43,0.72);
        else if (style > 1.5 && style < 2.5) lc = vec3(0.32,0.72,1.0);
        else if (style > 2.5) lc = vec3(0.62,0.48,1.0);
        float a = band * uFxLightleak.x * maskValue(uv,uMaskLightleak,z1,uFxLightleak.w) * 0.42;
        col += lc * a;
    }
    col = fxGrade(gb48, col, uGrade[36]);
#endif
#ifdef FX_WATERFALL
    if (uFxWaterfall.x > 0.0) {
        vec2 q = P * vec2(10.0 + uFxWaterfall.y, 5.0);
        float wph = TAU * uT * uFxWaterfall.z;
        float n = fbm(vec2(q.x*0.42 + sin(wph)*0.28, q.y*0.22 + cos(wph)*0.28));
        float streak = pow(0.5 + 0.5 * sin(q.x * 3.1 + n*8.0 + wph), 6.0);
        float foam = pow(0.5 + 0.5 * sin(q.y * 3.4 + q.x * 0.7 - wph), 8.0);
        float m = maskValue(uv,uMaskWaterfall,z1,uFxWaterfall.w);
        col = mix(col, mix(col, vec3(0.76,0.91,1.0), 0.42), clamp((streak*0.30+foam*0.24)*uFxWaterfall.x*m,0.0,0.62));
    }
#endif
#ifdef FX_REFLECT
    if (uFxReflect.x > 0.0) {
        float s = max(0.3, uFxReflect.y);
        float bands = pow(0.5 + 0.5 * sin(P.y * 125.0 / s + sin(P.x*24.0) * 1.4 - uT*uFxReflect.z*TAU), 7.0);
        float rph = TAU*uT*uFxReflect.z;
        float broken = smoothstep(0.42,0.76,vnoise(P*vec2(28.0,13.0)+vec2(sin(rph),cos(rph))*0.35));
        float a = bands * broken * uFxReflect.x * maskValue(uv,uMaskReflect,z1,uFxReflect.w) * 0.42;
        vec3 naturalHighlight = mix(col, vec3(1.0), 0.48);
        col = mix(col, naturalHighlight, clamp(a,0.0,0.55));
    }
#endif
#ifdef FX_BLACKAURA
    vec3 gb49 = col;   // v44.11: color propio del efecto
    if (uFxBlackaura.x > 0.0) {
        float ph = TAU * uT * uFxBlackaura.z;
        float n = fbm(P * max(0.5,uFxBlackaura.y) + vec2(sin(ph), cos(ph))*0.32);
        float wisps = smoothstep(0.42,0.79,n);
        float m = maskValue(uv,uMaskBlackaura,z1,uFxBlackaura.w);
        col = mix(col, tintOr(vec3(0.035,0.025,0.055), uTintBlackaura), clamp(wisps*uFxBlackaura.x*m*0.72,0.0,0.78));
    }
    col = fxGrade(gb49, col, uGrade[37]);
#endif
#ifdef FX_INKDRIP
    vec3 gb50 = col;   // v44.11: color propio del efecto
    if (uFxInkdrip.x > 0.0) {
        vec2 g = P * vec2(14.0/max(0.4,uFxInkdrip.y), 7.0);
        vec2 id = floor(g), f = fract(g);
        float h = hash12(vec2(id.x, 17.0));
        float len = 0.20 + 0.70*h;
        float iph = TAU * (uT*uFxInkdrip.z + h);
        float y = fract(f.y + 0.16*sin(iph) + h);
        float drip = step(y, len) * (1.0-smoothstep(0.04,0.20,abs(f.x-0.5))) * step(0.57,h);
        float m = maskValue(uv,uMaskInkdrip,z1,uFxInkdrip.w);
        col = mix(col, vec3(0.015), clamp(drip*uFxInkdrip.x*m*0.82,0.0,0.88));
    }
    col = fxGrade(gb50, col, uGrade[38]);
#endif
#ifdef FX_DRAMATIC
    vec3 gb51 = col;   // v44.11: color propio del efecto
    if (uFxDramatic.x > 0.0) {
        float width = clamp(uFxDramatic.y,0.2,1.0);
        float edge = 1.0 - smoothstep(0.0, width, length((uv-0.5)*vec2(0.86,1.0)));
        float pulse = 0.76 + 0.24*sin(TAU*uT*uFxDramatic.z);
        float m = maskValue(uv,uMaskDramatic,z1,uFxDramatic.w);
        float a = (1.0-edge) * pulse * uFxDramatic.x * m * 0.68;
        col = mix(col, vec3(0.03,0.02,0.04), clamp(a,0.0,0.75));
    }
    col = fxGrade(gb51, col, uGrade[39]);
#endif

#ifdef FX_TORNADO
    vec3 gb52 = col;   // v44.11: color propio del efecto
    if (uTornado.x > 0.0) col = tornadoLayer(col, P, maskValue(uv, uMaskTornado, 1.0, 0.0));
    col = fxGrade(gb52, col, uGrade[40]);
#endif
#ifdef FX_GUST
    vec3 gb53 = col;   // v44.11: color propio del efecto
    if (uGust.x > 0.0) col = gustLayer(col, P, windowEnv(uGust.y, uGust.z) * uGust.x, maskValue(uv, uMaskGust, 1.0, 0.0));
    col = fxGrade(gb53, col, uGrade[41]);
#endif
#ifdef FX_BOLT
    vec3 gb54 = col;   // v44.11: color propio del efecto
    if (uBolt.x > 0.0) col = boltLayer(col, P, maskValue(uv, uMaskBolt, 1.0, 0.0));
    col = fxGrade(gb54, col, uGrade[42]);
#endif
#ifdef FX_BLAST
    vec3 gb55 = col;   // v44.11: color propio del efecto
    if (uFxBlast.x > 0.0) col = zetaBlast(col, P, uv, z1);
#ifdef FX_BLAST2
    if (uFxBlast2.x > 0.0) col = zetaBlast2(col, P, uv, z1);
#endif
    col = fxGrade(gb55, col, uGrade[43]);
#endif
#ifdef FX_BLOOD
    vec3 gb56 = col;   // v44.11: color propio del efecto
    if (uFxBlood.x > 0.0) col = zetaBlood(col, P, uv, z1);
#ifdef FX_BLOOD2
    if (uFxBlood2.x > 0.0) col = zetaBlood2(col, P, uv, z1);
#endif
    col = fxGrade(gb56, col, uGrade[44]);
#endif
#ifdef FX_SLASH
    vec3 gb57 = col;   // v44.11: color propio del efecto
    if (uFxSlash.x > 0.0) col = zetaSlash(col, P, uv, z1);
#ifdef FX_SLASH2
    if (uFxSlash2.x > 0.0) col = zetaSlash2(col, P, uv, z1);
#endif
    col = fxGrade(gb57, col, uGrade[45]);
#endif
#ifdef FX_REVEAL
    vec3 gb58 = col;   // v44.11: color propio del efecto
    if (uFxReveal.x > 0.0) col = zetaReveal(col, P, uv, z1);
    col = fxGrade(gb58, col, uGrade[46]);
#endif
#ifdef FX_ELEMENTS
    col=studioElements(col,uvFrame);
#endif
#ifdef FX_APPEAR
    if (uAppear.x > 0.0) col = appearLayer(col, P);
#endif
#ifdef FX_SMOKEFRONT
    vec3 gb59 = col;   // v44.11: color propio del efecto (v44.15: solo si hay humo DELANTE)
    if (uFxSmoke.x > 0.0 && uSmokeControl.w >= 0.5)
        col = zetaSmoke(col, P, uv, z1);
#ifdef FX_SMOKE2
    if (uFxSmoke2.x > 0.0 && uSmokeControl2.w >= 0.5)
        col = zetaSmoke2(col, P, uv, z1);
#endif
    col = fxGrade(gb59, col, uGrade[30]);
#endif
#ifdef FX_IMPACT
    if (uImpact.x > 0.0 && uImpactMore.x > 0.0) col = mix(col, vec3(1.0), clamp(impactE * impactE * impactE * uImpactMore.x * 0.8, 0.0, 1.0));
#endif
#ifdef FX_LENSFLARE
    vec3 gb60 = col;   // v44.11: color propio del efecto
    if (uFxLensflare.x > 0.0) col = lensFlareLayer(col, P, maskValue(uv, uMaskLensflare, z1, uFxLensflare.w));
    col = fxGrade(gb60, col, uGrade[47]);
#endif
#ifdef FX_GLASSRAIN
    // v44 · lluvia en el cristal de la cámara: el fondo se empaña y cada gota es una lente.
    if (uFxGlassrain.x > 0.0) {
        float k = clamp(uFxGlassrain.x, 0.0, 1.5) * maskValue(uv, uMaskGlassrain, z1, uFxGlassrain.w);
        vec4 gd = glassDrops(P);
        vec3 misty = texture(uImage, clamp(uv, vec2(0.0), vec2(1.0)), 3.0).rgb;
        misty = mix(misty, vec3(luma(misty)), 0.25) * 0.92 + 0.06;
        col = mix(col, misty, clamp(uGlassrainMore.x, 0.0, 1.0) * (1.0 - gd.z) * min(k, 1.0) * 0.85);
        vec3 lens = sampleImg(clamp(uv - gd.xy * 0.035, vec2(0.0), vec2(1.0)));
        if (uMono > 0.5) lens = vec3(luma(lens));
        col = mix(col, lens * 1.04 + 0.02, clamp(gd.z * k, 0.0, 1.0));
        col += vec3(0.9, 0.95, 1.0) * gd.w * 0.18 * k;
        col -= vec3(0.05) * gd.w * k;
    }
#endif
#ifdef FX_GLOWUP
    vec3 gb61 = col;   // v44.11: color propio del efecto
    if (uFxGlowup.x > 0.0) col = glowupLayer(col, P, uv, maskValue(uv, uMaskGlowup, z1, uFxGlowup.w));
    col = fxGrade(gb61, col, uGrade[48]);
#endif
#ifdef FX_INKWEATHER
    vec3 gb62 = col;   // v44.11: color propio del efecto
    if (uFxInkweather.x > 0.0) col = inkweatherLayer(col, P, maskValue(uv, uMaskInkweather, z1, uFxInkweather.w));
    col = fxGrade(gb62, col, uGrade[49]);
#endif
#ifdef FX_RAINDROPS
    if (uFxRaindrops.x > 0.0) col = raindropsLayer(col, uv, P, maskValue(uv, uMaskRaindrops, z1, uFxRaindrops.w));
#endif
#ifdef FX_DEEPGLOW
    vec3 gb73 = col;   // v44.11: color propio del efecto
    if (uFxDeepglow.x > 0.0) col = deepglowLayer(col, uv, maskValue(uv, uMaskDeepglow, z1, 0.0));
    col = fxGrade(gb73, col, uGrade[59]);
#endif
#ifdef FX_FILMBURN
    vec3 gb74 = col;   // v44.11: color propio del efecto
    if (uFxFilmburn.x > 0.0) col = filmburnLayer(col, s, P, 1.0);
    col = fxGrade(gb74, col, uGrade[60]);
#endif
#ifdef FX_LIGHTDIR
    vec3 gb71 = col;   // v44.11: color propio del efecto
    if (uFxLightdir.x > 0.0) col = lightdirLayer(col, s, maskValue(uv, uMaskLightdir, z1, uFxLightdir.w));
    col = fxGrade(gb71, col, uGrade[57]);
#endif
#ifdef FX_RIMLIGHT
    vec3 gb72 = col;   // v44.11: color propio del efecto
    if (uFxRimlight.x > 0.0) col = rimlightLayer(col, uv, 1.0);
    col = fxGrade(gb72, col, uGrade[58]);
#endif
#ifdef FX_SUNLIGHT
    vec3 gb68 = col;   // v44.11: color propio del efecto
    if (uFxSunlight.x > 0.0) col = sunlightLayer(col, P, maskValue(uv, uMaskSunlight, z1, uFxSunlight.w));
    col = fxGrade(gb68, col, uGrade[55]);
#endif
#ifdef FX_DAPPLE
    vec3 gb64 = col;   // v44.11: color propio del efecto
    if (uFxDapple.x > 0.0) col = dappleLayer(col, P, maskValue(uv, uMaskDapple, z1, uFxDapple.w));
    col = fxGrade(gb64, col, uGrade[51]);
#endif
#ifdef FX_CAUSTICS
    vec3 gb65 = col;   // v44.11: color propio del efecto
    if (uFxCaustics.x > 0.0) col = causticsLayer(col, P, maskValue(uv, uMaskCaustics, z1, uFxCaustics.w));
    col = fxGrade(gb65, col, uGrade[52]);
#endif
#ifdef FX_STEAM
    vec3 gb66 = col;   // v44.11: color propio del efecto
    if (uFxSteam.x > 0.0) col = steamLayer(col, P, maskValue(uv, uMaskSteam, z1, uFxSteam.w));
    col = fxGrade(gb66, col, uGrade[53]);
#endif
#ifdef FX_FLICKER
    vec3 gb67 = col;   // v44.11: color propio del efecto
    if (uFxFlicker.x > 0.0) col = flickerLayer(col, maskValue(uv, uMaskFlicker, z1, uFxFlicker.w));
    col = fxGrade(gb67, col, uGrade[54]);
#endif
#ifdef FX_SYMBOLS
    vec3 gb63 = col;   // v44.11: color propio del efecto
    if (uFxSymbols.x > 0.0) col = symbolsLayer(col, P, maskValue(uv, uMaskSymbols, z1, uFxSymbols.w));
    col = fxGrade(gb63, col, uGrade[50]);
#endif
#ifdef FX_GLITCH
    if (uFxGlitch.x > 0.0) col = glitchLayer(col, uv);
#endif
#ifdef FX_VHS
    if (uFxVhs.x > 0.0) col = vhsLayer(col, uv, s);
#endif
#ifdef FX_FINISH
    if (uFxFinish.x > 0.0 || uFxFinish.y > 0.0 || uFxFinish.z > 0.0 || uFxFinish.w > 0.0) col = finishLayer(col, uv, s);
#endif
    // v44.19 · Calidad cine: cuánta LUZ añadieron los efectos (fuego, rayos, energía, chispas…).
    // Solo eso brilla de verdad; el papel blanco o un cielo claro de la imagen no se empañan.
    float cineEmit = 1.0;
    if (uEmitAlpha > 0.5) {
        vec3 dc = max(col - cineBase, 0.0);
        float dl = luma(col) - luma(cineBase);
        cineEmit = max(smoothstep(0.03, 0.42, dl), 0.8 * smoothstep(0.06, 0.55, max(dc.r, max(dc.g, dc.b))));
    }
    col=gradeColor(col,uvFrame);
    // Dither suave para evitar bandas en cielos y degradados (con cine lo pone la composición)
    if (uEmitAlpha < 0.5) col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(clamp(col, 0.0, 1.0), cineEmit);
}
