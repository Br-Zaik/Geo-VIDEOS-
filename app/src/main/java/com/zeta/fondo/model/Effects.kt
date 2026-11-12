package com.zeta.fondo.model

enum class Kind { FLOAT, INT, TOGGLE }

data class ParamDef(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val def: Float,
    val kind: Kind = Kind.FLOAT
) {
    /** Los controles finos solo aparecen en Modo avanzado. */
    val advanced: Boolean get() = key in Effects.advancedKeys
}

data class EffectDef(
    val id: String,
    val emoji: String,
    val title: String,
    val hint: String,
    val params: List<ParamDef>,
    val toggleable: Boolean = true,
    val defaultOn: Boolean = false
)

object Effects {

    private fun p(key: String, label: String, min: Float, max: Float, def: Float, kind: Kind = Kind.FLOAT) =
        ParamDef(key, label, min, max, def, kind)

    private fun zone(def: Float = 0f) = p("zone", "Solo dentro de la Zona 1 (azul)", 0f, 1f, def, Kind.TOGGLE)

    val all: List<EffectDef> = listOf(
        EffectDef("zonemotion", "", "Movimiento por zona", "Pinta lo que se mueve", listOf(
            p("int", "Fuerza", 0f, .035f, .008f),p("cycles", "Velocidad", 1f, 8f, 1f, Kind.INT),
            p("variant", "Tipo", 0f, 2f, 0f, Kind.INT),p("direction", "Dirección", 0f, 360f, 0f, Kind.INT))),
        EffectDef("sky", "", "Cielo", "Pinta el cielo", listOf(
            p("int", "Intensidad", 0f, 1f, .7f), p("cycles", "Velocidad", 1f, 5f, 1f, Kind.INT),
            p("variant", "Estilo", 0f, 3f, 0f, Kind.INT))),
        EffectDef("water", "", "Agua", "Pinta el agua", listOf(
            p("int", "Intensidad", 0f, 1f, .4f), p("cycles", "Velocidad", 1f, 6f, 1f, Kind.INT),
            p("variant", "Estilo", 0f, 2f, 0f, Kind.INT), p("direction", "Dirección", 0f, 360f, 0f, Kind.INT))),
        EffectDef("dispersion", "", "Dispersión", "Pinta la zona que se fragmenta", listOf(
            p("int", "Intensidad", 0f, 1f, .7f), p("cycles", "Velocidad", 1f, 5f, 1f, Kind.INT),
            p("size", "Tamaño", 12f, 60f, 28f, Kind.INT), p("direction", "Dirección", 0f, 360f, 0f, Kind.INT),
            p("distance", "Distancia", 0f, 1f, .7f), p("variant", "Forma", 0f, 2f, 0f, Kind.INT))),
        EffectDef(
            "flow", "➡️", "Flechas de movimiento",
            "Mueve lo que marques con la herramienta Flechas. La velocidad también cambia la deriva de nubes.",
            listOf(
                p("amount", "Distancia del movimiento", 0.05f, 1f, 0.35f),
                p("cycles", "Velocidad (ciclos por loop)", 1f, 8f, 2f, Kind.INT),
                p("reach", "Alcance de cada flecha", 0.05f, 1f, 0.25f)
            ),
            defaultOn = true
        ),
        EffectDef(
            "drift", "☁️", "Deriva de nubes (Zona 1)",
            "Desplaza todo lo pintado en Zona 1 en una dirección. Ideal para cielos y nubes. 0° = derecha, 90° = arriba.",
            listOf(
                p("dir", "Dirección (grados)", 0f, 360f, 0f, Kind.INT),
                p("speed", "Distancia", 0.005f, 0.3f, 0.05f)
            )
        ),
        EffectDef(
            "ripple", "🌊", "Ondas de agua (Zona 1)",
            "Ondulación suave del agua pintada en Zona 1.",
            listOf(
                p("amp", "Fuerza", 0.0005f, 0.012f, 0.003f),
                p("freq", "Tamaño de ondas (más = pequeñas)", 5f, 120f, 40f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT)
            )
        ),
        EffectDef(
            "glint", "✨", "Brillos en el agua (Zona 1)",
            "Destellos que aparecen y desaparecen sobre el agua.",
            listOf(
                p("int", "Intensidad", 0f, 2f, 0.8f),
                p("dens", "Densidad", 10f, 200f, 70f),
                p("cycles", "Velocidad", 1f, 8f, 3f, Kind.INT)
            )
        ),
        EffectDef(
            "sway", "🌳", "Viento en árboles (Zona 2)",
            "Balanceo de follaje y hierba pintados en Zona 2. Congela troncos y personajes.",
            listOf(
                p("amp", "Fuerza", 0.0005f, 0.015f, 0.004f),
                p("freq", "Tamaño de ramas (más = pequeñas)", 1f, 30f, 6f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT)
            )
        ),
        EffectDef(
            "heat", "♨️", "Calor (Zona 2)",
            "Distorsión de aire caliente; para ver llamas usa Fuego.",
            listOf(
                p("amp", "Intensidad", 0.0005f, 0.01f, 0.002f),
                p("freq", "Detalle", 5f, 80f, 30f),
                p("cycles", "Velocidad", 1f, 8f, 3f, Kind.INT)
            )
        ),
        EffectDef(
            "rain", "🌧️", "Lluvia",
            "Tres capas de gotas a distinta velocidad. Usa los estilos Suave, Normal, Fuerte o Tormenta y luego ajusta a tu gusto.",
            listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.7f),
                p("angle", "Inclinación", -40f, 40f, 10f, Kind.INT),
                p("cycles", "Velocidad", 1f, 16f, 6f, Kind.INT),
                p("variant", "Estilo", 0f, 3f, 1f, Kind.INT),
                zone(),
                // v44
                p("near", "Gotas cercanas", 0f, 1f, 0.35f),
                p("puddles", "Charcos", 0f, 1f, 0f),
                p("haze", "Neblina lejana", 0f, 1f, 0f)
            )
        ),
        EffectDef(
            "snow", "❄️", "Nieve",
            "Copos con varios niveles: suave, normal, fuerte y ventisca.",
            listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.9f),
                p("size", "Tamaño de copos", 0.3f, 3f, 1f),
                p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT),
                p("variant", "Estilo", 0f, 3f, 1f, Kind.INT),
                zone(),
                // v44
                p("swirl", "Remolinos", 0f, 1f, 0f),
                p("haze", "Bruma de ventisca", 0f, 1f, 0f)
            )
        ),
        EffectDef(
            "sakura", "🌸", "Pétalos de sakura",
            "Pétalos rosados que caen girando.",
            listOf(
                p("int", "Intensidad", 0f, 1f, 0.9f),
                p("size", "Tamaño de pétalos", 0.3f, 3f, 1f),
                p("cycles", "Velocidad", 1f, 6f, 1f, Kind.INT),
                zone()
            )
        ),
        EffectDef(
            "stars", "⭐", "Estrellas que titilan",
            "Pinta el cielo en Zona 1 para que las estrellas solo salgan ahí.",
            listOf(
                p("int", "Intensidad", 0f, 2f, 1f),
                p("dens", "Cantidad", 10f, 150f, 45f),
                p("cycles", "Velocidad del titileo", 1f, 8f, 3f, Kind.INT),
                zone(1f),
                p("shooting", "Estrellas fugaces", 0f, 1f, 0f)
            )
        ),
        EffectDef(
            "flies", "💫", "Luciérnagas",
            "Puntos de luz que flotan y parpadean.",
            listOf(
                p("int", "Intensidad", 0f, 2f, 1f),
                p("dens", "Cantidad (más = más pequeñas)", 2f, 20f, 6f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                zone()
            )
        ),
        EffectDef(
            "fog", "🌫️", "Niebla",
            "Bancos que se desplazan, niebla baja pegada al suelo o bruma fina.",
            listOf(
                p("int", "Intensidad", 0f, 1f, 0.35f),
                p("scale", "Tamaño (más = más fina)", 0.5f, 10f, 3f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                zone(),
                p("shape", "Tipo", 0f, 2f, 0f, Kind.INT),
                p("turb", "Turbulencia", 0f, 1f, 0.3f),
                // v44
                p("height", "Altura (niebla baja)", 0.1f, 0.95f, 0.7f),
                p("veil", "Velo de fondo", 0f, 0.6f, 0f)
            )
        ),
        EffectDef(
            "rays", "☀️", "Rayos de luz",
            "Haces de luz que bajan desde arriba de la imagen.",
            listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.4f),
                p("x", "Origen horizontal", 0f, 1f, 0.3f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Estilo de luz", 0f, 5f, 0f, Kind.INT),
                zone()
            )
        ),
        EffectDef(
            "energy", "⚡", "Energía y auras",
            "Rayos, aura alrededor del personaje (pinta Proteger), anillos de poder o aura de batalla.",
            listOf(
                p("int", "Intensidad", 0f, 2f, 0.8f),
                p("dens", "Rayos", 4f, 40f, 14f, Kind.INT),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT),
                p("variant", "Color del aura", 0f, 3f, 0f, Kind.INT),
                zone(),
                // v44.16: el catálogo lo llama «Aura anime»: nace como aura alrededor del personaje
                // (antes nacía como Rayos y no hacía lo que muestra la miniatura).
                p("shape", "Forma", 0f, 3f, 1f, Kind.INT),
                p("size", "Tamaño", 0f, 1f, 0.5f),
                p("turb", "Turbulencia", 0f, 1f, 0.6f)
            )
        ),
        EffectDef(
            "speed", "💨", "Líneas de velocidad manga",
            "Líneas direccionales para carreras, golpes y escenas dinámicas.",
            listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.65f),
                p("angle", "Dirección", -180f, 180f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 10f, 4f, Kind.INT),
                zone()
            )
        ),
        EffectDef(
            "parallax", "◫", "Profundidad 2.5D",
            "Pinta el fondo en Zona azul y el primer plano en Zona verde. Se moverán a distinta profundidad sin separar la imagen en archivos.",
            listOf(
                p("amount", "Profundidad", 0f, 0.08f, 0.035f),   // v44.24: más visible (antes 0,025)
                p("direction", "Dirección", 0f, 360f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("depth", "Diferencia entre planos", 0.2f, 1f, 0.65f),
                p("variant", "Movimiento de profundidad", 0f, 2f, 0f, Kind.INT),
                p("focus", "Plano de foco", -1f, 1f, 0f),
                p("dilate", "Protección de bordes", 0f, 1f, 0.5f),
                // v45: profundidad por IA (se calcula una vez por imagen; sin modelo → manual)
                p("ai", "Profundidad automática", 0f, 1f, 1f, Kind.TOGGLE),
                p("layers", "Capas de profundidad", 1f, 4f, 3f, Kind.INT),
                p("fix", "Fuerza de Acercar/Alejar", 0f, 1f, 0.5f),
                // v46: fondo rellenado detrás del personaje (necesita Proteger pintado o recortado)
                p("plate", "Rellenar detrás del personaje", 0f, 1f, 1f, Kind.TOGGLE)
            )
        ),
        EffectDef(
            "mesh", "〰", "Deformación suave",
            "Ondula cabello, ropa, agua o humo dentro de una zona pintada. Está pensada para movimientos pequeños y suaves.",
            listOf(
                p("int", "Fuerza", 0f, 0.025f, 0.006f),
                p("size", "Tamaño de la onda", 2f, 30f, 9f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT),
                p("variant", "Tipo", 0f, 2f, 0f, Kind.INT),
                p("direction", "Dirección", 0f, 360f, 0f, Kind.INT)
            )
        ),
        EffectDef(
            "natural", "🍃", "Movimiento natural",
            "Anima árboles, banderas, pasto, cortinas, cabello y ropa con deformación localizada, anclaje y ráfagas. Pinta solo la parte flexible; el resto queda quieto.",
            listOf(
                p("int", "Fuerza", 0f, 0.03f, 0.006f),
                p("direction", "Dirección del viento", 0f, 360f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT),
                p("flex", "Flexibilidad", 0f, 1f, 0.7f),
                p("variation", "Variación natural", 0f, 1f, 0.6f),
                p("gust", "Ráfagas", 0f, 1f, 0.35f),
                p("weight", "Peso", 0f, 1f, 0.5f),
                p("spring", "Elasticidad", 0f, 1f, 0.5f),
                p("variant", "Material", 0f, 5f, 0f, Kind.INT),
                p("anchor", "Borde fijo", 0f, 4f, 3f, Kind.INT)
            )
        ),
        EffectDef(
            "cam", "🎥", "Movimiento de cámara",
            "Zoom, paneo o giro suave. El movimiento vuelve exactamente al inicio al terminar el bucle.",
            listOf(
                p("zoom", "Zoom", 0f, 0.3f, 0.06f),
                p("pan", "Recorrido", 0f, 1f, 0f),
                p("direction", "Dirección del paneo", 0f, 360f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                // v44 · cámara libre (estilo «Libre»): se anima con fotogramas clave.
                p("mzoom", "Zoom", 1f, 3f, 1.2f),
                p("mx", "Posición horizontal", -1f, 1f, 0f),
                p("my", "Posición vertical", -1f, 1f, 0f),
                p("mrot", "Giro", -30f, 30f, 0f)
            )
        ),

        // Nuevas capas procedurales anime/manga: trabajan sin IA ni descargas.
        EffectDef("comet", "☄", "Cometas de luz", "Estelas diagonales que atraviesan la escena.", listOf(
            p("int", "Intensidad", 0f, 1.5f, 0.8f), p("size", "Longitud", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT), zone()
        )),
        EffectDef("runes", "✧", "Círculo mágico", "Anillos y glifos geométricos giratorios para magia.", listOf(
            p("int", "Brillo", 0f, 2f, 0.8f), p("size", "Radio", 0.2f, 2f, 1f),
            p("cycles", "Giros", 1f, 6f, 1f, Kind.INT), zone()
        )),
        EffectDef("hatch", "▨", "Rayado manga", "Líneas diagonales de sombreado animado.", listOf(
            p("int", "Intensidad", 0f, 1f, 0.5f), p("size", "Densidad", 30f, 260f, 120f),
            p("cycles", "Desplazamiento", 1f, 8f, 1f, Kind.INT), zone()
        )),
        EffectDef("pulse", "◎", "Anillos de poder", "Varios anillos concéntricos desde un punto.", listOf(
            p("int", "Brillo", 0f, 2f, 0.8f), p("size", "Grosor", 0.005f, 0.06f, 0.02f),
            p("cycles", "Pulsos", 1f, 8f, 2f, Kind.INT), zone()
        )),
        EffectDef("smoke", "♨", "Humo estilizado", "Humo anime: ondulante, en hilos, de tinta o rastrero. Color libre.", listOf(
            p("int", "Opacidad", 0f, 1f, 0.4f), p("size", "Tamaño", 0.5f, 9f, 3f),
            p("variant", "Color del humo", 0f, 4f, 0f, Kind.INT),
            p("density", "Densidad", 0.2f, 2f, 0.95f),
            p("rise", "Deriva", 0f, 1.5f, 0.55f),
            p("layer", "Delante del personaje PNG", 0f, 1f, 1f, Kind.TOGGLE),
            p("cycles", "Velocidad", 1f, 6f, 1f, Kind.INT), zone(),
            p("shape", "Forma", 0f, 3f, 0f, Kind.INT),
            p("turb", "Turbulencia", 0f, 1f, 0.5f),
            p("soft", "Suavizado", 0f, 1f, 0.35f),
            p("fill", "Llenar toda la zona", 0f, 1f, 0f, Kind.TOGGLE)
        )),
        EffectDef("shards", "◇", "Fragmentos de cristal", "Fragmentos geométricos que caen y brillan.", listOf(
            p("int", "Intensidad", 0f, 1.5f, 0.8f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT), zone()
        )),

        // Efectos procedurales originales: sin stickers ni fotografías reales.
        EffectDef("ash", "◈", "Cenizas flotantes", "Partículas grises de combate o incendio.", listOf(
            p("int", "Cantidad", 0f, 1.5f, 0.7f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT), zone()
        )),
        EffectDef("leaves", "🍃", "Hojas al viento", "Hojas para fondos anime/manhwa. Cambia entre verdes, secas, otoño y Navidad.", listOf(
            p("int", "Cantidad", 0f, 1.5f, 0.85f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT),
            p("variant", "Tipo de hoja", 0f, 3f, 0f, Kind.INT), zone(),
            p("whirl", "Remolino de hojas", 0f, 1f, 0f)
        )),
        EffectDef("embers", "✴", "Brasas / chispas", "Partículas incandescentes que ascienden.", listOf(
            p("int", "Cantidad", 0f, 1.5f, 0.8f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 3f, Kind.INT), zone()
        )),
        EffectDef("dust", "·", "Polvo / motas", "Polvo iluminado para planos anime tranquilos.", listOf(
            p("int", "Cantidad", 0f, 1.5f, 0.5f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 1f, Kind.INT), zone()
        )),
        EffectDef("bubbles", "◌", "Burbujas", "Burbujas bajo agua y escenarios mágicos.", listOf(
            p("int", "Cantidad", 0f, 1.5f, 0.7f), p("size", "Tamaño", 0.3f, 2.5f, 1f),
            p("cycles", "Velocidad", 1f, 10f, 2f, Kind.INT), zone()
        )),
        EffectDef("electric", "ϟ", "Electricidad anime", "Rayos dibujados alrededor del punto elegido.", listOf(
            p("int", "Intensidad", 0f, 2f, 0.8f),
            p("dens", "Ramificaciones", 5f, 35f, 16f),
            p("cycles", "Velocidad", 1f, 12f, 4f, Kind.INT), zone()
        )),
        EffectDef("focus", "◎", "Líneas de enfoque", "Líneas manga convergentes al punto de fuga.", listOf(
            p("int", "Intensidad", 0f, 1.5f, 0.7f),
            p("dens", "Cantidad", 15f, 90f, 46f),
            p("cycles", "Velocidad", 1f, 10f, 3f, Kind.INT), zone()
        )),
        EffectDef("shock", "◉", "Onda de choque", "Anillo expansivo de impacto en loop.", listOf(
            p("int", "Intensidad", 0f, 2f, 0.75f),
            p("width", "Grosor", 0.005f, 0.10f, 0.024f),
            p("cycles", "Velocidad", 1f, 6f, 1f, Kind.INT), zone()
        )),
        EffectDef("flash", "✷", "Destello de impacto", "Flash rítmico de pelea o transición manga.", listOf(
            p("int", "Intensidad", 0f, 1f, 0.7f),
            p("width", "Duración del flash", 0.01f, 0.5f, 0.12f),
            p("cycles", "Repeticiones", 1f, 8f, 2f, Kind.INT), zone()
        )),
        EffectDef("tone", "▦", "Screentone animado", "Trama de semitonos para manga en blanco y negro.", listOf(
            p("int", "Intensidad", 0f, 1f, 0.65f),
            p("dens", "Densidad", 60f, 320f, 150f),
            p("cycles", "Pulsación", 1f, 8f, 1f, Kind.INT), zone()
        )),
        EffectDef("grain", "▥", "Papel manga", "Textura original de impresión, sin fotografía.", listOf(
            p("int", "Intensidad", 0f, 0.6f, 0.15f),
            p("dens", "Tamaño de grano", 100f, 700f, 360f),
            p("cycles", "Movimiento", 1f, 10f, 1f, Kind.INT), zone()
        )),
        EffectDef("ink", "◩", "Tinta / alto contraste", "Sombras duras estilo viñeta manga.", listOf(
            p("int", "Intensidad", 0f, 1f, 0.8f),
            p("threshold", "Umbral", 0.1f, 0.9f, 0.48f),
            p("cycles", "Pulsación", 1f, 8f, 1f, Kind.INT), zone()
        )),
        // Efectos nuevos: todos trabajan con las máscaras existentes, sin imágenes externas.
        EffectDef("flame", "🔥", "Fuego",
            "Pinta la base del fuego: las llamas suben por encima. Hoguera, vela, antorcha, mágico o fantasma.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.7f),
                p("size", "Tamaño", 0.3f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT),
                p("variant", "Color de las llamas", 0f, 3f, 0f, Kind.INT),
                p("shape", "Forma", 0f, 4f, 0f, Kind.INT),
                p("height", "Altura", 0f, 1f, 0.5f),
                p("turb", "Turbulencia", 0f, 1f, 0.5f),
                p("soft", "Suavizado", 0f, 1f, 0.25f),
                p("sparks", "Chispas", 0f, 1f, 0.5f),
                p("smoke", "Humo", 0f, 1f, 0f)
            )),
        EffectDef("eyes", "👁️", "Ojos brillantes",
            "Pinta solo los ojos o el objeto que debe brillar. Brillo, pulso o destello con color libre.", listOf(
                p("int", "Intensidad", 0f, 2f, 0.9f),
                p("size", "Pulso", 0.2f, 1.5f, 0.7f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT),
                p("shape", "Forma", 0f, 2f, 0f, Kind.INT),
                p("halo", "Halo", 0f, 1f, 0.5f)
            )),
        EffectDef("blink", "◉", "Parpadeo (Zona 1)",
            "Pinta en azul los ojos (o usa «Buscar caras»). El párpado baja de verdad con la piel de arriba y una línea de pestañas; cierra rápido y abre más lento.", listOf(
                p("int", "Cierre", 0f, 1f, 0.85f),
                p("size", "Duración", 0.05f, 0.4f, 0.14f),
                p("cycles", "Parpadeos", 1f, 6f, 1f, Kind.INT)
            )),
        EffectDef("cloth", "〰", "Pelo y ropa (Zona 2)",
            "Pinta en verde mechones o tela; congela rostro y cuerpo.", listOf(
                p("int", "Movimiento", 0f, 0.02f, 0.005f),
                p("size", "Ondulación", 2f, 30f, 9f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT)
            )),
        // v30: efectos localizados para fondos anime, manga y manhwa.
        EffectDef("bokeh", "◌", "Bokeh suave",
            "Círculos de luz desenfocados para escenas románticas, nocturnas o mágicas. Puede limitarse a una zona.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.55f),
                p("variant", "Color", 0f, 3f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 6f, 1f, Kind.INT), zone()
            )),
        EffectDef("sparkle", "✦", "Destellos suaves",
            "Destellos pequeños para joyas, luces, magia y fondos manhwa.", listOf(
                p("int", "Cantidad", 0f, 1.5f, 0.65f),
                p("size", "Tamaño", 0.3f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT), zone()
            )),
        EffectDef("lightleak", "◒", "Luz ambiental",
            "Baño de luz animado para atardecer, neón, escenas románticas o mágicas.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.42f),
                p("variant", "Color", 0f, 3f, 0f, Kind.INT),
                p("cycles", "Velocidad", 1f, 5f, 1f, Kind.INT), zone()
            )),
        EffectDef("waterfall", "≋", "Cascada",
            "Agua descendente localizada con espuma y reflejos. Pinta únicamente la cascada.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.75f),
                p("detail", "Detalle", 2f, 18f, 8f),
                p("cycles", "Velocidad", 1f, 10f, 4f, Kind.INT), zone()
            )),
        EffectDef("reflect", "⌁", "Reflejo de agua",
            "Brillo y ondulación visual que conserva los colores originales del agua.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.45f),
                p("size", "Escala", 0.3f, 3f, 1f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT), zone()
            )),
        EffectDef("blackaura", "◉", "Aura oscura",
            "Sombra animada localizada para escenas tensas, villanos y manga dramático.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.55f),
                p("scale", "Escala", 0.5f, 8f, 2.5f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT), zone()
            )),
        EffectDef("inkdrip", "▾", "Tinta en movimiento",
            "Goteos y manchas de tinta animadas para viñetas y transiciones manga.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.65f),
                p("size", "Tamaño", 0.4f, 3f, 1f),
                p("cycles", "Velocidad", 1f, 8f, 2f, Kind.INT), zone()
            )),
        EffectDef("dramatic", "◐", "Sombra dramática",
            "Oscurecimiento pulsante localizado para tensión, misterio o escenas de impacto.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.45f),
                p("width", "Extensión", 0.2f, 1f, 0.7f),
                p("cycles", "Pulso", 1f, 6f, 1f, Kind.INT), zone()
            )),

        // v35: efectos de composición estilizada para secuencias anime/manhwa.
        // Una sola herramienta por familia; las variantes cambian el comportamiento real.
        EffectDef("blast", "💥", "Explosión anime", "Explosión por fases: destello, bola de fuego, onda, chispas y humo.", listOf(
            p("int", "Intensidad", 0f, 1.6f, 0.9f),
            p("size", "Tamaño", 0.1f, 1.5f, 0.65f),
            p("cycles", "Explosiones por bucle", 1f, 4f, 1f, Kind.INT),
            p("variant", "Color", 0f, 3f, 0f, Kind.INT),
            p("smoke", "Humo posterior", 0f, 1f, 0.65f),
            p("sparks", "Chispas", 0f, 1f, 0.5f),
            p("shape", "Forma", 0f, 3f, 0f, Kind.INT),
            p("flash", "Destello", 0f, 1f, 0.8f),
            p("ring", "Onda de choque", 0f, 1f, 0.7f),
            p("push", "Fuerza en la dirección", 0f, 1f, 0f)
        )),
        EffectDef("blood", "◌", "Salpicaduras manga", "Spray, goteo o mancha de tinta estilo manga/manhwa desde el origen.", listOf(
            p("int", "Intensidad", 0f, 1.2f, 0.6f),
            p("size", "Tamaño", 0.2f, 2f, 0.85f),
            p("cycles", "Impactos por bucle", 1f, 4f, 1f, Kind.INT),
            p("variant", "Tinta", 0f, 2f, 0f, Kind.INT),
            p("shape", "Forma", 0f, 2f, 0f, Kind.INT),
            p("direction", "Dirección", 0f, 360f, 20f, Kind.INT),
            p("spread", "Apertura", 0f, 1f, 0.5f)
        )),
        EffectDef("slash", "⚡", "Corte de energía", "Trazo, media luna, cruz o cortes múltiples para escenas de acción.", listOf(
            p("int", "Brillo", 0f, 1.5f, 0.8f),
            p("size", "Tamaño", 0.2f, 2f, 0.9f),
            p("cycles", "Cortes por bucle", 1f, 4f, 1f, Kind.INT),
            p("variant", "Color", 0f, 3f, 0f, Kind.INT),
            p("direction", "Dirección", 0f, 360f, 35f, Kind.INT),
            p("shape", "Forma", 0f, 3f, 0f, Kind.INT),
            p("width", "Grosor", 0f, 1f, 0.5f)
        )),
        EffectDef("reveal", "◑", "Revelación entre humo", "Revela gradualmente la zona pintada entre humo. No recrea el fondo oculto: para un personaje separado utiliza Aparición con PNG.", listOf(
            p("int", "Intensidad", 0f, 1f, 0.75f),
            p("size", "Tamaño", 0.1f, 2f, 0.8f),
            p("cycles", "Revelaciones por bucle", 1f, 3f, 1f, Kind.INT),
            p("variant", "Ambiente", 0f, 2f, 0f, Kind.INT),
            p("shape", "Dirección", 0f, 2f, 0f, Kind.INT),
            p("edge", "Humo del borde", 0f, 1f, 0.6f)
        )),
        // v16: efectos de evento y escenas anime (todos en bucle perfecto).
        EffectDef("bolt", "ϟ", "Relámpago",
            "Rayo ramificado con destello de pantalla. El trueno del audio suena sincronizado.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.9f),
                p("x", "Posición del rayo", 0f, 1f, 0.55f),
                p("flash", "Destello de pantalla", 0f, 1f, 0.5f),
                p("cycles", "Rayos por bucle", 1f, 6f, 1f, Kind.INT)
            )),
        EffectDef("gust", "〜", "Ráfaga de viento",
            "Viento visual configurable: brisa suave, viento normal, fuerte o tormenta. El bloque se puede mover y estirar en la línea de tiempo.", listOf(
                p("int", "Fuerza", 0f, 1.5f, 0.9f),
                p("start", "Momento", 0f, 0.95f, 0.1f),
                p("dur", "Duración (1 = siempre)", 0.1f, 1f, 1f),
                p("debris", "Hojas y polvo", 0f, 1f, 0.6f),
                p("cycles", "Velocidad", 1f, 8f, 3f, Kind.INT),
                p("variant", "Estilo", 0f, 3f, 1f, Kind.INT)
            )),
        EffectDef("tornado", "◎", "Remolino / tornado",
            "El fondo gira en espiral alrededor del centro con polvo girando. Pinta la zona para limitarlo.", listOf(
                p("int", "Fuerza", 0f, 1.5f, 0.8f),
                p("size", "Radio", 0.1f, 1f, 0.35f),
                p("cycles", "Giros", 1f, 6f, 2f, Kind.INT),
                p("variant", "Tipo", 0f, 1f, 0f, Kind.INT)
            )),
        EffectDef("appear", "★", "Aparición de personaje",
            "Elige un PNG recortado (por ejemplo de Paint Zeta) y hazlo aparecer con humo, viento o destello.", listOf(
                p("start", "Momento", 0f, 0.9f, 0.3f),
                p("dur", "Duración de la transición", 0.05f, 0.6f, 0.18f),
                p("opacity", "Opacidad", 0.1f, 1f, 1f),
                p("variant", "Transición", 0f, 3f, 0f, Kind.INT)
            )),
        EffectDef("clouds3", "☁", "Nubes en capas",
            "Tres capas de nubes: las cercanas pasan más rápido que las lejanas. Pinta el cielo.", listOf(
                p("int", "Opacidad", 0f, 1f, 0.7f),
                p("scale", "Tamaño", 0.5f, 4f, 1.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Luz", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("shore", "≋", "Olas con espuma",
            "Olas localizadas: pinta solamente el agua/orilla. La espuma toma el color de la escena para no verse como franjas blancas pegadas.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.65f),
                p("freq", "Cantidad de olas", 1f, 10f, 3f),
                p("cycles", "Velocidad", 1f, 8f, 1f, Kind.INT),
                p("foam", "Espuma", 0f, 1f, 0.55f),
                p("tint", "Color", 0f, 4f, 0f, Kind.INT),
                p("variant", "Estilo", 0f, 3f, 1f, Kind.INT)
            )),
        EffectDef("sunpath", "✺", "Reflejo del sol en el mar",
            "Camino de destellos bajo el sol. Pinta el mar para limitarlo.", listOf(
                p("int", "Brillo", 0f, 2f, 1f),
                p("x", "Posición del sol", 0f, 1f, 0.5f),
                p("width", "Ancho", 0.05f, 0.6f, 0.2f),
                p("cycles", "Titileo", 1f, 8f, 3f, Kind.INT),
                p("variant", "Iluminación", 0f, 3f, 0f, Kind.INT)
            )),
        // ---------------- v44: efectos nuevos (procedurales, sin imágenes de terceros) ----------------
        EffectDef("sand", "≈", "Tormenta de arena",
            "Velo de arena que avanza con el viento, vetas rápidas y granos; tiñe la escena. Sigue el joystick o el viento global.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("grains", "Granos", 0f, 1f, 0.6f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT),
                p("veil", "Velo", 0f, 1f, 0.7f),
                p("variant", "Estilo", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("cloudshadow", "◐", "Sombras de nubes",
            "Sombras suaves de nubes que cruzan el paisaje; lo que queda al sol se aclara un poco.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.55f),
                p("size", "Tamaño", 0.3f, 2f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("soft", "Suavidad", 0f, 1f, 0.5f)
            )),
        EffectDef("glassrain", "◌", "Lluvia en el cristal",
            "Gotas sobre la lente: el fondo se empaña, cada gota actúa de lupa y algunas bajan dejando rastro.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.9f),
                p("size", "Tamaño de gota", 0.5f, 2f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("fog", "Vaho", 0f, 1f, 0.5f),
                p("trails", "Gotas que bajan", 0f, 1f, 0.6f)
            )),
        EffectDef("birds", "ᨐ", "Bandada de pájaros",
            "Pájaros que aletean y planean en bandada; entran y salen del cuadro para que el bucle no se note.", listOf(
                p("int", "Opacidad", 0f, 1f, 0.95f),
                p("count", "Pájaros", 1f, 12f, 7f, Kind.INT),
                p("cycles", "Pasadas", 1f, 3f, 1f, Kind.INT),
                p("size", "Tamaño", 0.4f, 2.5f, 1f),
                p("variant", "Tipo", 0f, 1f, 0f, Kind.INT)
            )),
        EffectDef("aurora", "≋", "Aurora boreal",
            "Dos cortinas de luz que ondulan con estrías. Se ve sobre cielos oscuros; pinta el cielo o usa Proteger.", listOf(
                p("int", "Brillo", 0f, 1.5f, 0.9f),
                p("height", "Altura de la cortina", 0.3f, 2f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("base", "Posición", 0.1f, 0.8f, 0.42f),
                p("variant", "Color", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("citylights", "▦", "Luces de ciudad",
            "Hace titilar las luces que YA hay en tu imagen y enciende o apaga ventanas. Estilos Neón y Tráfico.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 1f),
                p("blink", "Ventanas que cambian", 0f, 1f, 0.4f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT),
                p("halo", "Halo", 0f, 1f, 0.5f),
                p("variant", "Estilo", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("lensflare", "✺", "Destello de lente",
            "Brillo, estrella, halo y fantasmas desde la fuente de luz. Colócalo sobre el sol, una farola o un foco.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("size", "Tamaño", 0.4f, 2f, 1f),
                p("cycles", "Pulso", 1f, 3f, 1f, Kind.INT),
                p("variant", "Estilo", 0f, 2f, 0f, Kind.INT)
            )),
        // v44.11 · efectos para manga / manhwa
        EffectDef("symbols", "💧", "Símbolos manga",
            "Gota de sudor, vena de enojo, «!» o estrellas de mareo, dibujados con tinta. Colócalo junto a la cara con Mover.", listOf(
                p("int", "Intensidad", 0f, 1f, 1f),
                p("size", "Tamaño", 0.4f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 2f, Kind.INT),
                p("variant", "Símbolo", 0f, 3f, 0f, Kind.INT)
            )),
        EffectDef("glowup", "✧", "Brillo manhwa",
            "Resplandor suave de las luces, motas que suben y destellos verticales, como en los webtoons.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.9f),
                p("bloom", "Resplandor", 0f, 1f, 0.6f),
                p("motes", "Luces que suben", 0f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Estilo", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("inkweather", "╱", "Lluvia y nieve de tinta",
            "Lluvia o nieve dibujadas como en el manga: trazos de tinta con borde blanco. Se ven bien en páginas en blanco y negro.", listOf(
                p("int", "Intensidad", 0f, 1f, 1f),
                p("amount", "Cantidad", 0.05f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 6f, 2f, Kind.INT),
                p("thick", "Grosor", 0.5f, 2.5f, 1f),
                p("variant", "Estilo", 0f, 2f, 0f, Kind.INT)
            )),
        // v44.12 · más escenas: bosque, agua, cocina/onsen, interiores de noche
        EffectDef("dapple", "☀", "Luz entre hojas",
            "Manchas de sol que se mecen con el viento, como bajo un árbol o junto a una ventana.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.8f),
                p("size", "Tamaño de las manchas", 0.4f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("soft", "Suavidad", 0f, 1f, 0.5f),
                p("variant", "Luz", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("caustics", "≈", "Reflejos de agua (cáusticas)",
            "La red de luz que deja el agua en el fondo de una piscina, el mar o bajo el agua.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.7f),
                p("size", "Tamaño", 0.4f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Agua", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("steam", "♨", "Vapor",
            "Vapor que sube desde un punto: taza, ramen, aguas termales, aliento en el frío. Colócalo con Mover.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.8f),
                p("size", "Ancho", 0.4f, 3f, 1f),
                p("height", "Altura", 0f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT)
            )),
        EffectDef("flicker", "💡", "Luz parpadeante",
            "Una lámpara que falla, la luz de una hoguera o un neón que zumba. Pinta la zona iluminada o úsalo en toda la imagen.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.7f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Tipo", 0f, 2f, 0f, Kind.INT)
            )),
        // v44.13 · animar el cielo, el mar y la luz QUE YA ESTÁN PINTADOS + acabado de cine
        EffectDef("skydrift", "☁", "Nubes de tu imagen",
            "Mueve despacio las nubes que ya están pintadas en tu imagen (no añade nubes falsas). El sol y las estrellas se quedan quietos. Ajusta el Horizonte para que el suelo o el mar no se muevan.", listOf(
                p("int", "Intensidad", 0f, 1f, 1f),
                p("amount", "Distancia", 0.2f, 3f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("horizon", "Horizonte", 0.05f, 1f, 0.6f),
                p("life", "Nubes vivas", 0f, 1f, 0.5f),
                p("direction", "Dirección", 0f, 360f, 0f, Kind.INT)
            )),
        EffectDef("seawaves", "🌊", "Mar en movimiento",
            "Olas en perspectiva sobre el mar de tu imagen: pequeñas en el horizonte y grandes delante, con destellos donde el agua ya brilla.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 1f),
                p("size", "Tamaño de las olas", 0.4f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("horizon", "Horizonte", 0f, 0.95f, 0.6f),
                p("glitter", "Destellos", 0f, 1f, 0.6f)
            )),
        EffectDef("sunlight", "☀", "Luz del sol / luna",
            "Halo que respira, rayos que salen del sol y luz que tiñe la escena. Se coloca sola sobre lo más brillante; ajústala con Mover.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("size", "Tamaño", 0.4f, 2.5f, 1f),
                p("rays", "Rayos", 0f, 1f, 0.6f),
                p("warm", "Luz en la escena", 0f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Luz", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("finish", "🎬", "Acabado de cine",
            "El toque final de un vídeo hecho en PC: resplandor con halo cálido, look de color, viñeta, grano de película y aberración suave.", listOf(
                p("int", "Fuerza del look", 0f, 1f, 0.7f),
                p("bloom", "Resplandor", 0f, 1f, 0.5f),
                p("vignette", "Viñeta", 0f, 1f, 0.35f),
                p("grain", "Grano de película", 0f, 1f, 0.2f),
                p("aberration", "Aberración", 0f, 1f, 0.15f),
                p("variant", "Look", 0f, 4f, 0f, Kind.INT)
            )),
        // v44.14 · luz y color «de edit» de manga y manhwa
        EffectDef("gradmap", "◑", "Tono de color (degradado)",
            "Pinta tu imagen con 3 colores según su luz (sombras, medios y luces): gris azulado de manga, rosa y azul de manhwa, noche, atardecer…", listOf(
                p("int", "Mezcla", 0f, 1f, 0.8f),
                p("contrast", "Contraste", -0.5f, 1f, 0.15f),
                p("variant", "Paleta", 0f, 5f, 0f, Kind.INT)
            )),
        EffectDef("atmos", "🌫", "Bruma de ambiente",
            "Niebla suave que toma el color de tu imagen, más densa al fondo, que se mueve despacio. No cubre al personaje pintado en Proteger.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.55f),
                p("height", "Altura", 0.05f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Color", 0f, 3f, 0f, Kind.INT)
            )),
        EffectDef("lightdir", "◐", "Luz de color desde un lado",
            "Luz de color que entra por un lado (elige la dirección): ilumina más lo claro, con borde brillante y sombra en el lado contrario.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("spread", "Alcance", 0.1f, 1f, 0.5f),
                p("edge", "Borde brillante", 0f, 1f, 0.4f),
                p("shadow", "Sombra contraria", 0f, 1f, 0.3f),
                p("cycles", "Respiración", 1f, 4f, 1f, Kind.INT),
                p("variant", "Color", 0f, 5f, 0f, Kind.INT),
                p("direction", "Dirección", 0f, 360f, 45f, Kind.INT)
            )),
        EffectDef("rimlight", "✦", "Luz de contorno",
            "El borde del personaje que mira hacia la luz se ilumina de color. Pinta al personaje en Proteger.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.9f),
                p("size", "Grosor", 0.3f, 3f, 1f),
                p("cycles", "Pulso", 1f, 4f, 1f, Kind.INT),
                p("variant", "Color", 0f, 5f, 0f, Kind.INT),
                p("direction", "Dirección", 0f, 360f, 0f, Kind.INT)
            )),
        // v44.15 · efectos «de editor de PC»
        EffectDef("glitch", "▚", "Glitch digital",
            "Ráfagas de separación RGB y franjas desplazadas, como en los edits de acción.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("freq", "Frecuencia", 0.05f, 1f, 0.35f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Estilo", 0f, 2f, 2f, Kind.INT)
            )),
        EffectDef("radialblur", "◎", "Zoom radial",
            "Desenfoque hacia un punto, como un golpe de cámara. En pulsos o constante. Elige el centro con Mover.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.7f),
                p("cycles", "Pulsos", 1f, 6f, 2f, Kind.INT),
                p("variant", "Estilo", 0f, 1f, 0f, Kind.INT)
            )),
        EffectDef("focusblur", "◉", "Enfoque (profundidad)",
            "Nítido en una franja o en un círculo y desenfocado fuera, como una cámara de cine. Proteger nunca se desenfoca.", listOf(
                p("int", "Desenfoque", 0f, 1f, 0.7f),
                p("width", "Zona nítida", 0.05f, 1f, 0.35f),
                p("y", "Altura del foco", 0f, 1f, 0.55f),
                p("cycles", "Respiración", 1f, 4f, 1f, Kind.INT),
                p("variant", "Forma", 0f, 1f, 0f, Kind.INT)
            )),
        EffectDef("deepglow", "✺", "Resplandor intenso",
            "Las luces de tu imagen brillan con un halo grande y suave en varias capas.", listOf(
                p("int", "Intensidad", 0f, 1.5f, 0.8f),
                p("threshold", "Desde qué brillo", 0.2f, 0.95f, 0.6f),
                p("size", "Tamaño", 0.3f, 2f, 1f),
                p("variant", "Color", 0f, 3f, 0f, Kind.INT)
            )),
        EffectDef("vhs", "📼", "VHS retro",
            "Líneas de barrido, franja de tracking, color corrido y ruido de cinta.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.7f),
                p("lines", "Líneas", 0f, 1f, 0.5f),
                p("noise", "Ruido", 0f, 1f, 0.5f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT)
            )),
        EffectDef("filmburn", "🔥", "Quemadura de película",
            "Luz orgánica que entra por los bordes y parpadea, como una película quemada.", listOf(
                p("int", "Intensidad", 0f, 1.2f, 0.7f),
                p("size", "Tamaño", 0.3f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Color", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("raindrops", "💧", "Gotas de lluvia",
            "Gotas grandes delante de la cámara que deforman la imagen, goteo desde arriba o salpicaduras en el suelo.", listOf(
                p("int", "Intensidad", 0f, 1f, 0.9f),
                p("amount", "Cantidad", 0.05f, 1f, 0.5f),
                p("size", "Tamaño", 0.4f, 2.5f, 1f),
                p("cycles", "Velocidad", 1f, 4f, 1f, Kind.INT),
                p("variant", "Tipo", 0f, 2f, 0f, Kind.INT)
            )),
        EffectDef("impact", "✹", "Impacto (temblor y zoom)",
            "Zoom de golpe con temblor que se calma. Ideal para apariciones y golpes.", listOf(
                p("int", "Zoom del golpe", 0f, 0.4f, 0.12f),
                p("shake", "Temblor", 0f, 1f, 0.6f),
                p("start", "Momento", 0f, 0.95f, 0.3f),
                p("flash", "Destello", 0f, 1f, 0.4f),
                p("cycles", "Golpes por bucle", 1f, 4f, 1f, Kind.INT)
            )),
        // v44.19 · ajuste global (sin bloque en la línea de tiempo), como «Estilo manga».
        EffectDef(
            "cine", "🎞️", "Calidad cine",
            "Brillo real: la luz de los efectos (fuego, rayos, energía, chispas) se derrama e ilumina la escena como en una cámara de cine, con halo de película, estela al mover la cámara, looks de color y franjas. Se ve igual al exportar.",
            listOf(
                p("active", "Calidad cine", 0f, 1f, 1f, Kind.TOGGLE),
                p("bloom", "Brillo de las luces", 0f, 1.5f, 0.6f),
                p("image", "Luces de tu imagen", 0f, 1f, 0.25f),
                p("halo", "Halo cálido (película)", 0f, 1f, 0.3f),
                p("grain", "Grano fino", 0f, 1f, 0f),
                // v44.20
                p("mblur", "Estela de cámara", 0f, 1f, 0.5f),
                p("spill", "Luz sobre la escena", 0f, 1f, 0.5f),
                p("variant", "Look de color", 0f, 5f, 0f, Kind.INT),
                p("lookamt", "Fuerza del look", 0f, 1f, 0.8f),
                p("bars", "Franjas de cine", 0f, 1f, 0f)
            ),
            toggleable = false
        ),
        EffectDef(
            "style", "🖋️", "Estilo manga",
            "Para páginas en blanco y negro con tramas.",
            listOf(
                p("mono", "Efectos en blanco y negro", 0f, 1f, 0f, Kind.TOGGLE),
                p("snap", "Píxel exacto (protege las tramas)", 0f, 1f, 0f, Kind.TOGGLE)
            ),
            toggleable = false
        )
    )

    /**
     * Efectos de EVENTO: tienen inicio, desarrollo y final (explosión, golpe, corte…).
     * Pueden ir en modo Una vez, Repetir o Continuo (repetido N veces por bucle).
     * Todos los demás son AMBIENTALES y cubren por defecto todo el vídeo.
     */
    val eventEffects = setOf("blast", "blood", "slash", "reveal", "flash", "shock", "impact", "bolt")

    /** Duración natural de un evento al crearlo en modo Una vez (segundos). */
    fun eventDuration(id: String): Float = when (id) {
        "blast" -> 2.2f; "reveal" -> 3f; "blood" -> 1.6f; "slash" -> 1.1f
        "impact" -> .9f; "bolt" -> .8f; else -> 1.2f
    }

    /** Parámetros que el Modo fácil oculta (siguen activos con su valor). */
    val advancedKeys = setOf("turb", "soft", "density", "layer", "variation", "gust", "weight", "spring",
        "distance", "depth", "focus", "dilate", "spread", "debris", "foam", "detail",
        "flex", "reach", "edge", "halo", "ring", "push")

    /** Efectos con color libre (selector + HEX). */
    val colorable = listOf("smoke", "flame", "blast", "blood", "slash", "energy", "eyes", "fog", "blackaura", "reveal")

    /** Etiquetas de las formas: cada una cambia la figura y el comportamiento, no solo el color. */
    val shapeLabels: Map<String, List<String>> = mapOf(
        "smoke" to listOf("Ondulante", "Hilos", "Tinta manga", "Rastrero"),
        "flame" to listOf("Hoguera", "Vela", "Antorcha", "Mágico", "Fantasma"),
        "blast" to listOf("Clásica", "Hongo", "Estallido manga", "Energía"),
        "blood" to listOf("Spray", "Goteo", "Mancha"),
        "slash" to listOf("Trazo", "Media luna", "Cruz", "Múltiple"),
        "energy" to listOf("Rayos", "Aura", "Anillos", "Aura de batalla"),
        "eyes" to listOf("Brillo", "Pulso", "Destello"),
        "fog" to listOf("Bancos", "Niebla baja", "Bruma"),
        "reveal" to listOf("Desde el centro", "Desde abajo", "Lateral")
    )

    /**
     * Duplicados heredados: se ocultan del catálogo (siguen funcionando en proyectos
     * antiguos y reaparecen si están activos). Cada uno tiene un sustituto mejor:
     * sway/cloth -> Movimiento natural, pulse -> Energía (Anillos), drift -> Flechas o
     * Nubes en capas, ripple -> Agua (Ondas), glint -> Reflejo de agua / Destellos.
     */
    /** v44.19 · Ajustes globales: se abren con un toque, sin bloque ni claves en la línea de tiempo. */
    val globalSettings = setOf("style", "cine")

    /**
     * v44.22 · Efectos retirados: no aportaban (oscurecer o rayar sin dar vida). Ya no salen en
     * el catálogo; un proyecto que los usa los sigue viendo y exportando igual.
     */
    val retired = setOf("blink", "dramatic", "hatch")
    val legacyDuplicates = setOf("sway", "cloth", "pulse", "drift", "ripple", "glint") + retired

    private val defaultsMap: Map<String, Float> by lazy {
        val m = LinkedHashMap<String, Float>()
        for (e in all) {
            if (e.toggleable) m["${e.id}.on"] = if (e.defaultOn) 1f else 0f
            for (p in e.params) m["${e.id}.${p.key}"] = p.def
        }
        m
    }

    fun defaults(): Map<String, Float> = defaultsMap

    fun default(key: String): Float = defaultsMap[key] ?: 0f

    /** Apaga todos los efectos menos las flechas. */
    fun allOff(current: Map<String, Float>): Map<String, Float> {
        val m = HashMap(current)
        for (e in all) {
            if (e.toggleable && e.id != "flow") m["${e.id}.on"] = 0f
            m["${e.id}.copy.on"] = 0f
        }
        m["style.mono"] = 0f
        m["style.snap"] = 0f
        return m
    }
}
