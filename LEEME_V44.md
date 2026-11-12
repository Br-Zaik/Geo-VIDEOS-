# Zeta Motion v44.28.1 — arreglo de compilación

- **GitHub no compilaba** («Platform declaration clash»). Dos funciones nuevas del recorte, setLayerCropRect y setLayerCropRatio, tenían el mismo nombre que la JVM ya genera para las propiedades layerCropRect y layerCropRatio. Ahora se llaman moveLayerCropFrame y chooseLayerCropRatio. El funcionamiento no cambia.
- **Prueba nueva (V44281Test):** revisa todo el código y falla si vuelve a aparecer una función set/get/is con el nombre de una propiedad. Así este error se detecta en las pruebas, antes de compilar el APK.
- 273 pruebas JVM.

# Zeta Motion v44.28 — Texto (parte 2)

## Lo que cambia
- **Botón «Texto» en la barra principal**, justo después de Imagen: Animar · Proteger · Efectos · Imagen · Texto · Audio · Ajustes.
- **Al tocarlo:**
  - se abre el teclado y ves el texto tal cual saldrá;
  - abajo, pegado al teclado: lo escrito (Enter = otra línea), 7 fuentes, 10 colores, borde (sin / fino / medio / grueso) con su color, y sombra;
  - ✓ Listo lo pone al centro.
- **Los efectos pasan siempre por detrás de las letras** (fuego, luz, partículas…), en la vista previa y en el vídeo.
- **Con el texto tocado:** cajita flotante con ✎ Editar · ⟳ 90° · Detrás/Delante (del personaje pintado en Proteger). Abajo siguen Movimiento, Tiempo, Mezcla, Clave, 2.5D…
- **Al editarlo**, las letras mantienen su tamaño en pantalla: un texto más largo crece a lo ancho.
- **Optimización:** el texto se dibuja UNA vez a una imagen (lado ≤ 2048 px) y luego es una capa normal: nada se redibuja en cada cuadro. La vista previa del editor se rehace a los 0,09 s de dejar de escribir, no en cada letra.
- **Fuentes del propio teléfono:** no se agregan archivos de fuentes ni licencias.
- **Corrección de la parte 1:** la cajita flotante ya no tapa el punto rosa de girar cuando la imagen está volteada o girada.

## Pruebas hechas
- 272 pruebas JVM (4 nuevas de texto).

## No probado
- En un teléfono real.

# Zeta Motion v44.27 — Recortar capa (parte 1)

## Lo que cambia
- **Cajita flotante sobre la imagen:** al tocar una capa aparece justo debajo de su recuadro (o encima si no cabe) con **✂ Recortar · ⟳ 90° · ⇋ Voltear**. Sigue a la imagen y se esconde mientras la arrastras.
- **Recortar en el mismo lugar:** la imagen original se ve completa, lo de fuera se oscurece y hay un marco con esquinas, lados y cuadrícula. Arriba sale el tamaño en píxeles del resultado.
- **Barra del recorte:** ✕ · Libre · Cuadrado · 9:16 · 16:9 · 3:4 · 4:5 · Girar 90° · Original · ✓ Listo.
- **No se pierde nada:** el original de la capa queda guardado. «Original» la devuelve entera cuando quieras, y ↶ deshace el recorte.
- **Queda en su sitio:** la parte elegida se queda en el mismo lugar y del mismo tamaño en el lienzo, también con giro, espejo y claves.
- **Barra de la capa:** Recortar y Girar 90° van primeros; los demás botones siguen igual y en el mismo orden.
- **Aviso:** la primera vez que agregas una imagen, un aviso enseña dónde está Recortar (una sola vez).
- La vista previa y la exportación no cambian por dentro: la capa sigue dibujando una imagen normal.

## Pruebas hechas
- 268 pruebas JVM (8 nuevas de v44.27): el marco y el giro marcan el mismo trozo; las proporciones y los arrastres no se salen; lo recortado queda en el mismo lugar (con giro, espejo y claves); «Original» vuelve exacto; guardado y limpieza del original.

## No probado
- En un teléfono real: compílala en GitHub y prueba recortar, girar, «Original», ↶ y exportar.

# Zeta Motion v44.26 — mínimo Android 10

## Lo que cambia
- **Funciona desde Android 10** (antes desde Android 8). Llega al 94 % de los teléfonos en uso (octubre de 2026). Android 8 y 9 ya casi no se usan y eran los caminos más frágiles.
- **Imágenes:** siempre se decodifican al tamaño exacto, con el giro y el espejo de la foto aplicados. La vista previa usa la misma decodificación que exportar, con respaldo si un archivo raro falla y en 8 bits (un PNG de 16 bits ya no ocupa el doble).
- **Galería sin permisos:** se quitó el permiso de almacenamiento de Android 8 y 9. Exportar y Captura guardan en Movies/FondoZeta y Pictures/FondoZeta sin pedir nada.
- **Menos código viejo:** lupa al pintar, servicio de exportación, estado térmico y elección de codificador sin comprobaciones de versiones antiguas.
- **Gama baja se mantiene:** los límites para teléfonos con poca memoria (fotos a 2560 px, 4 imágenes por proyecto) dependen de la RAM, no de lo viejo del teléfono. Hay muchos teléfonos nuevos y baratos con 3 o 4 GB.

## Pruebas hechas
- 260 pruebas JVM (5 nuevas de v44.26).

## No probado
- En un teléfono real y la versión final con R8: compílala en GitHub y pruébala antes de subirla a Play.

# Zeta Motion v44.25 — sin fallos de memoria, lista para Google Play y varias imágenes a la vez

## Explosión y efectos «Detrás»
- **Por qué no se veía la explosión:** al activarla, la app creaba sola una máscara de "personaje" en Proteger. En un paisaje sin personaje marcaba casi toda la imagen y la explosión quedaba detrás, invisible (reproducido con el shader real).
- **Ahora todos los efectos empiezan «Delante».** Solo van «Detrás» si ya hay un personaje pintado en Proteger.
- **Al elegir «Detrás» sin personaje, la app pregunta:** «Buscar personaje solo», «Pintarlo yo» o «Dejarlo delante». Nunca crea la máscara a escondidas.
- **Proyectos viejos:** si Proteger solo tiene la máscara que creó la app (ningún trazo tuyo), se quita al abrir y se avisa. ↶ la devuelve.
- **La que pides tú no se toca:** el personaje marcado con «Buscar personaje solo» (o para la luz de contorno) se guarda con otro nombre y sigue ahí al volver a abrir.
- **Proteger automático** queda solo para los efectos que necesitan un personaje (luz de contorno y aura).
- **Detector más estricto:** el contorno de la silueta debe seguir bordes reales (tinta, cambios fuertes) y no puede tocar mucho los lados de la imagen. Tu paisaje ya no se confunde con un personaje; el personaje de espaldas del gimnasio se sigue detectando.

## Fallos graves corregidos
- **Fotos grandes de cámara (giradas):** se decodifican una sola vez, ya giradas y a 4096 px como máximo (2560 en gama baja). Antes: hasta 16K más una copia girada (~400 MB con 50 MP) y no abría.
- **Importar personaje (PNG):** se carga ya al tamaño final, sin doble toque, y un error de memoria ya no cierra la app.
- **Capas en Android 8:** se cargan al tamaño justo y con el giro de la foto.
- **Fotos en espejo (cámara frontal) y PNG/WebP/HEIC girados:** se guardan ya derechas al importarlas. Antes la vista previa y el vídeo podían salir distintos.
- **Guardado:** un valor inválido (NaN/infinito) hacía fallar TODOS los guardados. Ahora se guarda un valor seguro y esos valores tampoco entran al cambiar un ajuste.

## Google Play
- **ONNX Runtime 1.23.2:** la versión 1.20.0 no cumplía las páginas de 16 KB que exige Play.
- **El flujo de GitHub lo comprueba:** alineación de las .so (readelf) y zipalign -P 16, con el resultado en el resumen. Es solo un aviso: nunca detiene la compilación.
- **R8 en la versión final:** quita el código y los recursos que no se usan (miles de iconos), así el APK es más liviano y arranca más rápido. Sin ofuscar y conservando todo el código de la app.

## Toques rápidos y trabajos pesados
- **Abrir proyecto:** un doble toque, o tocar otro mientras se importa, ya no abre dos a la vez.
- **Un solo trabajo pesado a la vez:** Exportar, «Mejorar imagen» y Captura nunca corren juntos.
- **Audio:** se convierte solo lo necesario (60 s para sonidos y música de bloque, 10 min para el audio del proyecto) y se para ahí. Una canción de más de 10 minutos ya no se rechaza: se usan los primeros 10, y la app avisa del recorte.
- **Capas:** sus imágenes ya no se liberan mientras la GPU puede estar usándolas.

## Optimización
- **Imágenes a tamaño exacto:** en Android 9+ se decodifican directamente al tamaño final (antes hasta el doble). Así usan menos memoria al exportar, mejorar o cargar capas.
- **Combinaciones de efectos:** al tocar efectos rápido, las combinaciones que ya no se ven dejan de prepararse.
- **Combinaciones que quedaban en blanco:** la vista previa solo guarda las últimas 6. Si volvías a una que se había descartado, la app no la volvía a preparar y quedaba sin efectos hasta reiniciar. Arreglado.
- **Memoria:** cuando Android la pide, la app suelta miniaturas, sonidos y la comparación de «Mejorar».
- **Inicio:** las miniaturas quedan en memoria (sin recargar al desplazarte), se renuevan cuando editas un proyecto y hay más columnas en tabletas.

## Varias imágenes a la vez
- **«Imagen» permite elegir varias:** hasta 8, o 4 en gama baja. Se cargan una por una y entran como un solo paso (↶ las quita todas).
- **«Compartir» varias desde la Galería:** la primera abre el proyecto y las demás entran como capas. Si el proyecto no se pudo abrir, no se cuelan en el siguiente.

## Pruebas hechas
- 255 pruebas JVM (16 nuevas de v44.25).
- Revisión línea por línea de todo lo cambiado, con estas correcciones antes de entregar: en Android 8 (o si el decodificador moderno fallaba) importar capas, personaje o fotos giradas siempre daba error; la máscara pedida con «Buscar personaje solo» se habría quitado al volver a abrir; una escena podía dejar un efecto «Detrás» sin personaje.
- Detector con el paisaje y el personaje reales de las capturas.
- Explosión dibujada con el shader real con y sin la máscara automática.

## No probado
- En un teléfono real y la versión final con R8: compílala en GitHub y pruébala antes de subirla a Play.

# Zeta Motion v44.24 — licencias simples, capas visibles al abrir y 2.5D de verdad

## Lo que cambia
- **Licencias** (Ajustes › Licencias): lista simple con nombre, autor y tipo de licencia; al tocar una se abre su texto completo, como en WhatsApp o YouTube.
  - Real-ESRGAN (BSD-3), ONNX Runtime (MIT), lbpcascade_animeface (MIT), Android Jetpack y Kotlin (Apache 2.0).
  - Textos completos en assets/licenses. Se quitó el texto viejo de MI-GAN (no va en la app).
- **Capas y efectos visibles al abrir**: el proyecto ya no abre en «modo pintar». Antes ese modo ocultaba las capas de imagen (p. ej. un sol) y los efectos hasta tocar cualquier botón.
- **Profundidad 2.5D**:
  - Separa al personaje sin caras ni fondo liso (p. ej. de espaldas en un gimnasio), con código propio y sin IA: nitidez + color distinto del borde + modelo de color iterativo.
  - La silueta antigua por fondo liso solo se usa si es creíble. Antes, en fondos con detalle, marcaba el 80 % de la imagen como personaje: todo se movía igual y el 2.5D parecía quieto.
  - En paisajes y texturas no inventa un personaje: se usa el relieve general.
  - Mientras el mapa se calcula, el 2.5D ya se mueve (relieve provisional, abajo cerca y arriba lejos).
  - Más movimiento por defecto (0,035; Suave/Normal/Fuerte = 0,02/0,035/0,055).
  - Los mapas guardados de versiones anteriores se rehacen solos.
- **Capas con profundidad**: las capas de «Imagen» se mueven con el 2.5D. Movimiento › Profundidad 2.5D: Pegada (como lo que tiene debajo), Lejos, Medio, Cerca o Delante. Una capa sobre Proteger queda en el plano de foco.
- **Menú de la carpeta**: «Abrir imagen de Archivos» ahora se llama «Nuevo proyecto con otra imagen» (eso es lo que hace).
- **Pantallas**:
  - La muesca o cámara ya no tapa botones en horizontal.
  - Con la letra del sistema muy grande, la app la respeta hasta 1,15× para que los botones no se monten.
  - En tabletas los botones de abajo no se separan de más.

## Pruebas hechas
- 239 pruebas JVM (12 nuevas de v44.24).
- Silueta sin caras con la captura real de un personaje de espaldas: separa cuerpo y pantalón del fondo; en un paisaje y en ruido no detecta a nadie.
- Shader en Chromium/WebGL2: compila con los 101 efectos, solo con el 2.5D y el ligero.

## No probado
- En un teléfono real (no se puede compilar Android aquí): compílalo en GitHub y pruébalo.

# Zeta Motion v44.23.1 — miniaturas de mezcla con dibujo propio
- Las miniaturas de «Mezcla y opacidad» ya no usan la foto del proyecto: son un dibujo propio de la app (atardecer con lago y una esfera de color), una por modo, hechas con las mismas fórmulas de la app. Pesan 96 KB en total y no gastan CPU al abrir el panel.
- Cómo se hicieron: tools/blendthumbs/escena.py dibuja el fondo y la esfera; la mezcla la calcula el código de la app (BlendModes).

# Zeta Motion v44.23 — capas de imagen con 29 modos de mezcla y zonas con forma que se arrastran

Dos funciones grandes pedidas a partir de Alight Motion: poner más imágenes encima del fondo y mover los efectos arrastrando.

## v44.23 — Lo que cambia
- **Capas de imagen** (botón «Imagen» en la barra principal):
  - cualquier imagen de la galería, con o sin transparencia, hasta 8 (4 en teléfonos con poca memoria);
  - caja con tiradores sobre el lienzo: arrastrar mueve, las esquinas cambian el tamaño, los lados estiran, el punto rosa gira;
  - dos dedos sobre la imagen cambian el tamaño y el giro; dos dedos fuera hacen zoom;
  - imán al centro y a 0°/90°/180°/270° con vibración corta;
  - tocar otra imagen en el lienzo la selecciona.
- **Mezcla y opacidad** (como Alight Motion): 29 modos en sus grupos (Normal, Oscurecer, Aclarar, Contraste, Diferencia, Color, Enmascarar).
  - Cada modo dice qué hace.
  - Cada modo tiene su miniatura de ejemplo con un dibujo propio de la app (atardecer y esfera de color), hecha con las mismas fórmulas.
  - Opacidad con fotograma clave y curva.
- **Acciones de la capa**: Clave (posición, tamaño y giro animados), Movimiento (flotar, latir, balanceo, girar, parpadeo de luz, respirar luz, deriva; siempre en bucle perfecto), Delante/Detrás del personaje (usa Proteger), Subir/Bajar, Llenar, Centrar, Voltear, Tiempo, Ocultar, Duplicar y Eliminar.
- Las capas salen igual en la vista previa, el vídeo y las capturas. Con «Calidad cine» entran antes del brillo y el grano: una luz en «Pantalla» también brilla.
- **Zonas con forma**: en Ajustar › Zona del efecto se elige «◯ Círculo» o «▭ Rectángulo».
  - La forma se arrastra, agranda, estira y gira sobre la imagen, con borde suave.
  - Se puede retocar con el pincel.
  - Funciona con todos los efectos con zona.
- **Mover el efecto dentro de Ajustar**: los efectos con punto de origen (humo, explosión, rayos, sol, destello…) se arrastran directo en el lienzo mientras los ajustas, no solo desde la línea de tiempo.

## Pruebas hechas
- 228 pruebas JVM (16 nuevas de capas y zonas).
- Shader de capas en Chromium/SwiftShader con tu imagen y un destello:
  - los 29 modos coinciden con las fórmulas de referencia en Kotlin (diferencia ≤ 1/255);
  - la mezcla por hardware y la mezcla con copia dan lo mismo;
  - la tijera calculada en Kotlin da el mismo resultado que dibujar la pantalla entera;
  - «Detrás del personaje» oculta la capa donde está Proteger;
  - con «Calidad cine», Pantalla suma luz y Normal la tapa.
- La forma de las zonas en GLSL coincide con la de Kotlin.
- Lluvia en un círculo y luz del sol en un rectángulo girado se ven solo dentro de su forma.
- Los 101 efectos siguen compilando juntos; el shader ligero también.

## No probado
- No se ha compilado con Gradle ni se ha probado en un teléfono (la interfaz nueva solo se revisó a mano).

# Zeta Motion v44.22 — cambio de efecto rápido, sin parpadeos negros, efectos inútiles retirados

**No se ha compilado con Gradle Android ni probado en un teléfono.** **210 pruebas JVM OK** (2 nuevas en `V4422Test`).

## v44.22 — Lo que cambia
- **Cambio de efecto casi instantáneo:** la primera vez que se usaba una combinación de efectos el shader tardaba 1–3 s en compilar y mientras tanto se veía el efecto anterior. Ahora el compilador tiene una **cola de baja prioridad**: los efectos visibles de la categoría abierta (Efectos y Animar) se compilan en segundo plano y se guardan en el caché de disco (sin ocupar la GPU). Al tocar uno carga en milisegundos. Hasta 10 por categoría (6 en teléfonos modestos); un pedido real siempre pasa delante. Caché de binarios: 48 variantes (antes 24).
- **Parpadeos negros en la vista previa:**
  - Un error suelto en un cuadro dejaba la vista previa negra para siempre. Ahora ese cuadro muestra la imagen sola y sigue; solo tras 3 fallos seguidos se rinde.
  - La carga del libro de efectos ya no puede romper el dibujo (cualquier fallo → efectos procedurales).
  - «Calidad cine» ya no recrea sus búferes cada vez que la vista previa baja/sube de resolución al moverse (solo crece).
  - Diagnóstico muestra «Cuadros sin efectos» para saber si vuelve a pasar.
- **Efectos retirados del catálogo** (no aportaban): Parpadeo, Sombra dramática y Rayado manga. Los proyectos que ya los usan los siguen viendo y exportando igual (salen en «Activos» si están encendidos).

# Zeta Motion v44.21 — fuego SIMULADO y hojas/pétalos PINTADOS (assets propios, sin licencias)

**No se ha compilado con Gradle Android ni probado en un teléfono.** Probado aquí: **208 pruebas JVM OK** (4 nuevas en `V4421Test`); los **101 efectos juntos** compilan en GLSL ES 3.0 real (Chromium + SwiftShader) con el libro cargado; el bucle cierra (máx. 1/255) con fuego, humo, hojas y sakura.

## v44.21 — Lo que cambia
- **Libro de efectos** (`assets/fx/fxbook.webp`, 1024×896, WebP sin pérdida, **0,38 MB**): datos, no color.
  - **Fuego**: 32 cuadros de una **simulación de fluidos** propia (aire caliente que sube, se enrosca, se enfría y deja humo), en bucle perfecto (fundido f[i+N]→f[i]). R = calor, G = humo.
  - **8 sprites pintados por código**: 6 hojas (ovalada, arce, alargada, dentada, lanceolada, sauce) con nervio, nervaduras, textura y borde; 2 pétalos de sakura con muesca y venitas. R = detalle, G = forma, B = borde.
  - Todo sale de `tools/fxgen` (Python + numpy/scipy): `fire_sim.py` → `sprites.py` → `pack.py`. **Cero assets de terceros: sin problemas de licencia en la Play Store.**
- **Fuego**: las llamas son la simulación, en fila y en dos capas desfasadas (cada llama con su fase, altura y lado), velocidad entera por bucle. Mantiene variantes de color (normal, azul, verde, violeta), formas, «manga» (mono), zona pintada, chispas y el **humo de la simulación**. Fuego fantasma sigue funcionando.
- **Hojas** (efecto Hojas y sus copias): cada hoja es un sprite pintado con su nivel de detalle según el tamaño en pantalla; color según estilo (verdes, secas, otoño, Navidad) y luz de la escena; cara/revés.
- **Sakura**: pétalos pintados.
- **Paleta de hojas suave** (antes cambiaba por cuadros y se veían bloques dentro de las hojas grandes).
- **Optimización**: el libro solo se carga si fuego, hojas o sakura están encendidos, una vez por contexto, con mipmaps; se libera con el render. Se lee con nivel fijo (sin derivadas dentro de bucles). Si no carga → versión procedural de antes (`uFxReady = 0`).
- Miniaturas nuevas: fuego, hojas, sakura.

**Archivos:** `engine/FxBook.kt` (nuevo), `engine/FondoGl.kt` (unidad de textura 7), `MainActivity.kt` y `export/ExportService.kt` (init), `assets/shaders/fondo.frag`, `assets/fx/fxbook.webp`, `tools/fxgen/*`, miniaturas. versionCode 67, 44.21.0.

**Siguiente:** más simulaciones con el mismo método (humo, explosión, agua/salpicaduras, magia/energía, nieve con copos pintados, gotas de lluvia), e «Importar video de efecto» (fondo negro o verde).

# Zeta Motion v44.20 — partículas con profundidad, estela de cámara, luz sobre la escena, looks, franjas y parpadeo real

**No se ha compilado con Gradle Android ni probado en un teléfono.** Probado aquí: Kotlin del motor compilado y **204 pruebas JVM OK** (7 nuevas en `V4420Test`); los **101 efectos juntos** compilan en GLSL ES 3.0 real (Chromium + SwiftShader); bloque std140 = 6432 bytes igual que la app; el bucle cierra (máx. 1/255) con hojas, sakura, nieve, brasas, lluvia, cámara, parpadeo, look y franjas encendidos a la vez.

## v44.20 — Lo que cambia
- **Partículas (hojas, brasas, polvo, ceniza y sus 2.ª copias):** `particleFieldX` mira las 4 celdas más cercanas: cada partícula puede estar en cualquier lugar, cruzar el borde y solaparse (antes vivían encerradas en su cuadrito y se veían en cuadrícula).
  - **Hojas:** forma con punta, nervio, borde oscuro, cara/revés, luz que cambia al girar y **capa cercana** (grandes, algo desenfocadas, el doble de rápidas).
  - **Brasas:** capa cercana grande, desenfocada y rápida.
  - **Sakura:** pétalos sueltos que se solapan + **capa cercana desenfocada** (como con lente abierta).
  - **Nieve:** copos muy cercanos tipo **bokeh**.
  - **Lluvia:** sobre cielos claros la gota oscurece un poco (antes era invisible sobre blanco).
  - Hojas, sakura, nieve y ceniza toman **la luz de la escena** (`sceneTint`, mip alto de la imagen).
- **Calidad cine, controles nuevos:**
  - **Estela de cámara** (0,5): la cámara ahora es una función del tiempo (`zetaCamera`); se calcula dónde estaba medio cuadro antes y después y se promedian 8 muestras (obturador de 180°). Solo para Movimiento de cámara; sin memoria entre cuadros.
  - **Luz sobre la escena** (0,5): la luz de los efectos tiñe las superficies cercanas (el fuego pinta de naranja la cara y la ropa de al lado).
  - **Look de color** (sección Estilo): Natural, Kodak cálido, Fuji suave, Teal & Orange, Noche azul, B/N cine + **Fuerza del look**.
  - **Franjas de cine**: en horizontal llegan a 2,39:1; en vertical, 10 % por lado. Respeta la exportación girada.
- **Parpadeo (antes «Parpadeo de sombra»):** el párpado BAJA de verdad. Mide los bordes de la zona del ojo (interpolados: borde liso), cubre con la piel de justo encima y deja una línea de pestañas; cierra rápido, se queda cerrado un instante y abre más lento. Sirve para ojos de primer plano (alcance 28 % del alto). Con «toda la imagen» queda el oscurecido de antes.
- **Miniaturas nuevas:** hojas, sakura, nieve y parpadeo.
- **No cambiado a propósito:** el humo ya era volumétrico (bocanadas con luz y sombra propia); con «Luz sobre la escena» el fuego lo ilumina.

**Archivos:** `assets/shaders/fondo.frag`, `engine/CinePost.kt`, `engine/FondoGl.kt`, `engine/RenderParams.kt`, `model/Effects.kt`, `model/EffectCatalog.kt`, `assets/thumbs/{leaves,sakura,snow,blink}.webp`, pruebas `V4420Test`/`V4419CineTest`. versionCode 66, 44.20.0.

**Pendiente (no hecho):** mirada (movimiento de iris), cabello con péndulo por mechones, enfoque por profundidad con bokeh real, agua 2D nueva, rayos volumétricos en pasada aparte, varita mágica IA (necesita descargar un modelo), «modo seguro» para drivers viejos.

# Zeta Motion v44.19 — «Calidad cine» (brillo real) y Viento con hojas de verdad

**No se ha compilado con Gradle Android ni probado en un teléfono.** Lo que sí se probó aquí:
- Kotlin del motor compilado (Kotlin 2.0.21) y **197 pruebas JVM OK** (10 nuevas en `V4419CineTest`).
- Shaders compilados y dibujados con un GLSL ES 3.0 real (Chromium + ANGLE/SwiftShader): los **101 efectos juntos** compilan; el bloque std140 mide 6432 bytes igual que lo calcula la app; el bucle cierra (diferencia máx. 1/255 entre el primer y el último cuadro).
- Los parámetros de las pruebas de imagen salen del propio código de la app (RenderParams + FrameUniforms), no copiados a mano.

## v44.19 — Lo que cambia
- **Calidad cine (nuevo, Acabado › primera tarjeta, encendido por defecto):** la escena se dibuja en un búfer y pasa por una cadena corta de post-proceso:
  - **Brillo real (bloom):** pirámide de 5-6 niveles con filtro «dual» de ARM (lo más barato en celulares) y subida acumulativa estilo Call of Duty. Mezcla en espacio lineal tipo «pantalla»: ilumina sin quemar ni lavar a gris.
  - **Solo brilla la LUZ que añaden los efectos** (fuego, brasas, rayos, sol, energía, chispas…). El shader principal la marca en el canal alfa (`uEmitAlpha`, `cineBase`). El papel blanco del manga y los cielos claros NO se empañan; las líneas negras conservan su contraste (probado).
  - **Luces de tu imagen** (0,25): las luces fuertes CON color de la imagen brillan un poco; las blancas casi nada.
  - **Halo cálido de película** (halation) y **Grano fino** (apagado por defecto; semilla = fase del bucle, determinista).
  - Ajustes: Calidad cine (sí/no), Brillo de las luces, Luces de tu imagen, Halo cálido, Grano fino. Es un ajuste global como «Estilo manga»: sin bloque ni claves en la línea de tiempo.
  - Igual en vista previa, exportación (MP4 y PNG) y captura: todo pasa por `FondoGl.draw`. Al pintar, en modo rápido, «solo efectos» o viendo la profundidad se dibuja directo como antes.
  - **Seguro:** RGBA16F solo si el driver anuncia `EXT_color_buffer_(half_)float` y el FBO queda completo (formato con tamaño `GL_RGBA16F`+`GL_HALF_FLOAT`, el estilo ES2 falla en Adreno). Si no, RGBA8 guardando el brillo ×4 (casi idéntico, medido). Si algo falla, se apaga solo y se dibuja como la v44.18. La vista previa que cambia de tamaño al moverse NO recrea nada (se usa la parte ocupada de cada textura).
  - **Diagnóstico** muestra «Calidad cine: Activa · RGBA16F · 6 niveles» (o el motivo del fallo) y el informe compartido lo incluye.
- **Viento (Ráfaga) rehecho:** las hojas eran óvalos planos color beige. Ahora: forma de hoja con punta, giro 3D (se ponen de canto y enseñan el revés más claro), nervio central, borde oscuro fino, sombra suave, 3 colores (verde, seca, otoño) y **dos capas de profundidad** (cerca grandes, suaves y rápidas; lejos pequeñas y nítidas). Las **líneas de viento** se afinan en las puntas y sobre fondo claro son un velo gris azulado (antes blancas e invisibles sobre blanco). Nunca se cortan en el borde de su celda.
- **Miniaturas nuevas** de Viento y Calidad cine (renderizadas con el shader).
- La caché de shaders compilados ya existía y funciona (no se tocó).

**Archivos:** `engine/CinePost.kt` (nuevo: CinePost, CinePlan, CineShaders), `engine/FondoGl.kt`, `engine/RenderParams.kt` (campo `post`), `assets/shaders/fondo.frag`, `model/Effects.kt`, `model/EffectCatalog.kt`, `AppViewModel.kt`, `engine/PreviewRenderer.kt`, `engine/RenderHub.kt`, `ui/DiagnosticsPanel.kt`, `ui/Panels.kt`, `ui/Glyphs.kt`, `assets/thumbs/gust.webp`, `assets/thumbs/cine.webp`. versionCode 65, 44.19.0.

**Sin probar en Android:** la creación real de FBOs en cada GPU, el rendimiento en gama baja y la UI del panel (mismo patrón que «Estilo manga»).

# Zeta Motion v44.18 — «Mejorar imagen» simple (Original / x2 / x4) con antes / después

**No se ha compilado con Gradle Android ni probado en un teléfono.** 186 pruebas JVM OK.

## v44.18
- **Mejorar imagen, simple como Ibis Paint:** Tamaño Original / x2 / x4 (se recuerda) + botón Mejorar. «Original» mantiene el tamaño pero con líneas más limpias (la salida x4 del modelo se promedia por bloques). Al terminar se aplica sola (vista previa y exportación); «Quitar mejora» vuelve al original. Fuera el interruptor y las imágenes de comparación de abajo.
- **Antes / después en el lienzo:** con el panel abierto, el lienzo muestra la imagen entera: izquierda antes, derecha después, separadas por una línea que se arrastra con el dedo.
- Arreglos de compilación de la 44.17.1: tarea `downloadZetaModels` que quedaba en Gradle y una variable repetida en autoScene.

# Zeta Motion v44.17.1 — sin IA de profundidad, «Mejorar imagen» se queda, música como bloque y luz real

**No se ha compilado con Gradle Android ni probado en un teléfono.** Shader compilado (108 variantes) y renderizado con GLSL ES 3.0 real sobre el atardecer de Brian. 184 pruebas JVM OK (las que usan ONNX Runtime se ejecutan solo en Gradle).

## v44.17.1
- **«Mejorar imagen» vuelve** (Ajustes › Mejorar): Real-ESRGAN anime x4, con el modelo DENTRO del APK (2,5 MB, sin descargas). La imagen mejorada de cada proyecto ya no se borra. Licencias muestra solo este modelo.

## v44.17 — Lo que cambia
- **IA local quitada (salvo «Mejorar imagen»):** fuera la profundidad con IA (Depth Anything / MiDaS), MI-GAN, las descargas de modelos (Ajustes › IA), el proceso aparte «:ia» y el permiso de internet. Al abrir la app se borran los modelos que ya se hubieran descargado (se conserva la copia del de «Mejorar»). El 2.5D sigue funcionando con el modo automático sin IA (al instante) y el relleno del fondo es el básico (MI-GAN solo se usaba si se importaba a mano).
- **Música como un efecto:** «+ Agregar música» es un botón igual que «+ Agregar efecto»: abre tus archivos y la canción entra como un BLOQUE desde 0 s con su duración real (máx. la del vídeo). Se mueve con pulsación larga, se recorta y se quita con ← como cualquier bloque. El bloque muestra el nombre del archivo.
- **Sin destello al cambiar de pestaña** (Animar y Efectos): si una miniatura existe, la tarjeta ya no muestra primero el icono provisional; las miniaturas de Animar también se preparan al abrir el editor; tocar una categoría cambia la página en el mismo cuadro.
- **Rayos de luz reales:** salen de la luz de la propia imagen (dispersión volumétrica en pantalla) y las nubes o montañas los cortan; al encenderlos se colocan solos en el sol o la luna de la imagen (también el destello de lente). Mezcla «pantalla»: iluminan sin quemar el cielo.
- **Animar más natural:** el fundido de las dos fases del flujo ya no ocurre a la vez en toda la imagen (fase desplazada por zonas): desaparece el «latido» de doble imagen.

## v44.16 — Lo que cambia
- **Línea de tiempo:** la pista de música va justo debajo del vídeo (mismo inicio y final) y se desliza con él. La flecha ← y el nombre de cada efecto van pegados al inicio de su bloque y se mueven con él (ya no se quedan flotando en el borde de la pantalla).
- **Proteger automático:** si un efecto necesita al personaje (Luz de contorno, Aura, efectos «detrás del personaje») y Proteger está vacío, la app detecta al personaje sola (fondo liso + caras, cierre de huecos) y lo pone en Proteger como una capa más: se ve, se corrige con el pincel y se deshace con ↶. Una vez por proyecto.
- **Zonas automáticas:** ojos, parpadeo, cielo, agua, reflejo, olas, ondas, destellos y cascada proponen su zona solos al encenderlos sin zona (la misma detección del botón «Detectar»).
- **Fuego sin zona:** arde a lo largo del borde inferior (como su miniatura) en vez de no hacer nada; el corte sobre el personaje ahora es suave.
- **Aura anime:** nace como Aura (antes como Rayos); se pinta encima, así se ve también sobre fondos blancos de manga; ya no deja manchas dentro del personaje. La escena «Despertar de energía» conserva los rayos.
- **Luz de contorno:** en zonas claras tiñe del color de la luz (antes en blanco no se notaba).
- **Captura:** botón de cámara arriba: guarda el cuadro actual con todos los efectos, a la calidad de exportación, en Galería › Pictures/ZetaMotion/Capturas.
- **Efectos:** deslizar a izquierda o derecha cambia de categoría (la pestaña se desplaza a la vista). Las miniaturas se preparan al abrir el editor y nacen ya dibujadas: sin parpadeo al entrar.

## v44.15 — Lo que cambia
- **Línea de tiempo:** la flecha ← para quitar un efecto ya no es texto: se dibuja centrada en su cuadro. «Agregar música» queda fijo (no se desplaza con la línea de tiempo).
- **Escenas:** tarjetas más pequeñas (rejilla de 78 dp).
- **Categorías sin repetir:** cada efecto está en UNA sola categoría; «Todos» muestra todos. Tinta y símbolos → Manga; acabado y bruma → Acabado.
- **Miniaturas hechas por código:** las de los efectos nuevos y las de cielo / acabado se generan con escenas procedurales (ya no se usan imágenes del usuario).
- **Gotas de lluvia** (`raindrops`, en Agua): Gotas cercanas (refractan la imagen), Goteo desde arriba y Salpicaduras en el suelo.
- **7 efectos nuevos de acabado:** Glitch RGB (`glitch`), Zoom radial (`radialblur`), Enfoque / profundidad (`focusblur`, franja o círculo), Resplandor intenso (`deepglow`), VHS (`vhs`), Quemadura de película (`filmburn`) y Gotas de lluvia.
- **Rendimiento:** la 2.ª instancia de humo, explosión, sangre y corte solo se compila si hay dos bloques de ese efecto; el humo solo compila su sitio (detrás O delante del personaje). En el banco de pruebas el humo pasó de ~2000 ms a ~300 ms por cuadro (SwiftShader, 270×480) con la imagen idéntica píxel a píxel; explosión ~210 → ~135 ms.
- Bloque std140: 6432 bytes; uGrade[61].

## v44.14 — Lo que cambia
- **Tono de color (degradado)** (`gradmap`): gradient map de 3 colores según la luz de la imagen. Paletas: Gris azulado (manga), Noche azul, Rosa y azul, Atardecer, Rojo intenso, Morado. Mezcla y contraste.
- **Bruma de ambiente** (`atmos`): niebla que toma el COLOR MEDIO de la imagen (o blanca, azul noche, cálida), más densa al fondo, en movimiento lento; nunca cubre Proteger.
- **Luz de color desde un lado** (`lightdir`): degradado de luz de color desde la dirección elegida (joystick), ilumina más lo claro, borde brillante en el lado de la fuente y sombra en el contrario; respira. 6 colores.
- **Luz de contorno** (`rimlight`): ilumina el borde del personaje (Proteger) que mira hacia la luz. Avisa si falta Proteger.
- **Escenas:** Edit manga (gris azulado), Edit manhwa (rosa y azul), Luz roja desde abajo, Bruma mágica.
- Bloque std140: 6128 bytes; uGrade[59].

# Zeta Motion v44.13 — animar paisajes pintados y acabado de cine

**No se ha compilado con Gradle Android ni probado en un teléfono.** El shader se compiló y renderizó con un compilador GLSL ES 3.0 real (Chromium + ANGLE/SwiftShader) sobre los fondos de Brian. 157 pruebas JVM OK.

## v44.13 — Lo que cambia
- **Nubes de tu imagen** (`skydrift`): mueve las nubes YA pintadas (flow map de dos fases con fase por zonas: sin saltos y bucle exacto). Perspectiva (cerca del horizonte se mueve menos), «nubes vivas» (deformación suave), el sol y las estrellas se quedan quietos y nunca toma píxeles por debajo del Horizonte. Proteger congela al personaje.
- **Mar en movimiento** (`seawaves`): olas en perspectiva bajo el Horizonte que rompen el reflejo en horizontal, valles algo más oscuros y destellos donde el agua ya brilla.
- **Luz del sol / luna** (`sunlight`): halo que respira, rayos desde la fuente (ruido sobre la dirección, sin costura), luz que tiñe la escena; Atardecer, Día, Luna llena. Se coloca sola sobre lo más brillante.
- **Acabado de cine** (`finish`): resplandor + halo cálido (halation), 5 looks (Natural+, Atardecer cálido, Noche azul, Anime pastel, Cine), viñeta, grano de película y aberración cromática en los bordes. Va al final de todo.
- **Colocación automática** (`model/SceneAnalysis.kt`, sin IA): horizonte = borde horizontal fuerte más alto entre el 25 % y el 88 %; sol = zona clara más grande. Se aplica la primera vez que se activa el efecto o una escena.
- **Escenas de un toque:** Atardecer en el mar, Cielo de anime, Noche de luna llena, Lluvia de noche, Mañana en el bosque (con sonidos).
- Catálogo: categorías nuevas «Cielo y mar» y «Acabado». Bloque std140: 5872 bytes (comprobado con un driver real).

# Zeta Motion v44.12 — línea de tiempo más cómoda, keyframes visibles, lluvia nueva y 4 efectos

**No se ha compilado con Gradle Android ni probado en un teléfono.** El shader se compiló y renderizó con un compilador GLSL ES 3.0 real (Chromium + ANGLE/SwiftShader). 151 pruebas JVM OK.

## v44.12 — Lo que cambia
- **Quitar un efecto:** botón pequeño **←** al inicio de cada bloque (con Deshacer). Se quitó el «deslizar fuerte».
- **Mover un bloque:** SIEMPRE manteniendo pulsado (con vibración), también si ya está seleccionado. Un toque solo selecciona.
- **Keyframes:** rombos del bloque más grandes, centrados y con borde; el que está en el cabezal, en amarillo. En el panel del efecto hay una **tira de claves** (todas las claves del bloque, el cabezal, ◀ ▶ para saltar entre claves; tocar o arrastrar mueve el cabezal). El rombo de cada ajuste: gris = sin animar; rosa tenue = animado (clave en otro momento); amarillo y más grande = estás sobre una clave.
- **Audio:** tocar un sonido lo PRUEBA; tocar otro lo cambia (ya no se suman); tocar el mismo lo quita; ✓ lo deja. Para sumar otro, se vuelve a abrir Audio.
- **Exportar:** la vista previa y su sonido se pausan al empezar y no se pueden reanudar hasta que termine.
- **Lluvia y nieve (antes «palos»):** lluvia de trazos finísimos con estela, 3 capas de profundidad y antialias por píxel; la de tinta es negra sobre lo claro y blanca sobre lo oscuro. Estilo «Lluvia realista» con estelas claras y ambiente húmedo. Nieve de copos suaves, los cercanos desenfocados.
- **Efectos nuevos:** Luz entre hojas (manchas de sol que se mecen), Reflejos de agua (cáusticas de piscina/mar), Vapor (taza, ramen, onsen; se coloca con Mover), Luz parpadeante (lámpara que falla, hoguera, neón). Con color propio (uGrade[55]) y miniaturas renderizadas con el shader.
- Bloque std140: 5648 bytes, desplazamientos comprobados contra un driver real.

# Zeta Motion v44.11 — audio sin cortes, color propio de cada efecto y efectos de manga

**No se ha compilado con Gradle Android ni probado en un teléfono.** El shader SÍ se compiló y se renderizó con un compilador GLSL ES 3.0 real (Chromium + ANGLE/SwiftShader, WebGL2), y las partes sin Android pasan las pruebas JVM.

## v44.11 — Lo que cambia

**1. Sonidos entrecortados (causa encontrada)**
- `Ambient.mix()` se llama en cada lectura de audio (~23 ms) y vaciaba la caché que también usaban los bloques de sonido: lluvia, viento, fuego… se volvían a fabricar una y otra vez y se oían a trozos. Ahora los bloques tienen su propia caché (`Ambient.loop`).
- Vista previa: búfer de ~0,25 s (antes ~0,02 s), lectura de 2048 cuadros, los sonidos se preparan ANTES de sonar (`SoundMixer.prewarm`) y un desfase pequeño ya no corta el audio (`DriftGuard`: solo si dura 4 lecturas; un salto grande, al instante).
- Bordes de cada bloque sin «clic» (12 ms de entrada/salida, `SoundEdges`), salvo el golpe de un evento y los ambientes que cubren el inicio o el final del vídeo.
- Un ambiente nuevo nace de 0 s al final (máx. 6 s), igual que los efectos. Los golpes cortos siguen en la línea blanca.

**2. Color propio de cada efecto** (pestaña «Color» del efecto)
- Original · Blanco y negro · Sepia, y Saturación, Brillo y Contraste. Solo cambia lo que el efecto añade, nunca la imagen.
- «Modo manga»: todos los efectos en blanco y negro con un interruptor (es `style.mono`, antes escondido en «Estilo manga»; ahora vale para todos).
- 51 efectos que dibujan encima (`model/EffectGrade.kt`). Los que mueven la imagen (cámara, 2.5D, viento, cabello, agua…) no tienen color.
- Shader: `fxGrade()` y `vec4 uGrade[51]` en el bloque std140 (5376 bytes; desplazamientos comprobados contra un driver real).

**3. Efectos nuevos**
- **Símbolos manga**: gota de sudor, vena de enojo, «!» y estrellas de mareo, dibujados con tinta. Se colocan con Mover.
- **Brillo manhwa**: resplandor de las zonas claras, luces que suben y destellos verticales. Dorado, Rosa o Azul.
- **Lluvia y nieve de tinta**: lluvia con trazos de tinta y borde blanco, nieve de bolitas con contorno, o lluvia blanca para páginas oscuras. Sigue el viento.
- Con miniaturas propias y en las categorías Manga, Anime, Luz y Clima.

**Pruebas:** `V4411Test` (11) + las anteriores; tamaños std140 actualizados (V41, V44Phase2) y la lluvia ya tiene pestaña Color (V38).

# Zeta Motion v44.10 — 2.5D automático, IA que ya no da el falso «sin memoria», Mejorar visible

**No se ha compilado con Gradle Android ni probado en un teléfono.** Las partes sin Android se compilaron y probaron aquí (ver Pruebas).

## v44.10 — Lo que cambia

**1. El aviso «tu teléfono no tiene memoria libre suficiente» (falso)**
- Causa: la app daba la IA por «cerrada por memoria» si a los 8 s no veía su proceso en la lista de procesos de Android, aunque la IA siguiera calculando. Tras 3 intentos mostraba el aviso.
- Ahora la app se CONECTA al proceso de la IA (`bindService` con `BIND_IMPORTANT`): Android lo trata como primer plano, cierra antes otras apps de fondo para darle RAM y avisa al instante si de verdad muere. Además, la IA escribe un latido cada 1,5 s.
- La decisión está en `engine/DepthJob.kt` (probada en la JVM).
- Nuevo último intento: solo MiDaS (la IA ligera), si está descargada.
- Si aun así no cabe, el 2.5D NO se queda quieto: pasa solo al 2.5D rápido y lo explica (es la RAM, no el almacenamiento).
- Solo se recuerda el nivel de memoria que FUNCIONÓ (antes un fallo puntual dejaba la IA en el nivel más bajo para siempre).
- Diagnóstico guarda la RAM libre antes y durante el cálculo.

**2. El 2.5D se mueve siempre, sin descargar nada**
- Antes: si la IA fallaba y no pintabas zonas, el 2.5D no tenía profundidad y solo se veía un acercamiento (capturas 2 y 3).
- Ahora, al activar el 2.5D se calcula solo el «2.5D rápido» (0,3-0,5 s). La IA nunca arranca sola.
- El 2.5D rápido separa al personaje del fondo SIN IA: inunda el fondo liso desde las esquinas de arriba (se para en las líneas de tinta) y suma las caras detectadas. En paisajes cargados no inventa silueta.
- Si pintas o cambias Proteger, el mapa rápido se rehace solo.
- «Borrar» el mapa pasa a zonas manuales (si no, el automático volvería al instante).

**3. Fuerza en un toque:** Suave (0,015) · Normal (0,025) · Fuerte (0,04).

**4. Mejorar imagen se ve en la vista previa** cuando está activa (antes solo al exportar). Se carga al tamaño de edición del teléfono: no gasta más RAM. Deshacer también la quita.

**5. Panel de Profundidad más sencillo:** estado, fuerza, «Pintar personaje», rápido / con IA, y debajo las correcciones. Fuera del panel: la tarjeta de descarga de 99 MB (queda en Ajustes › IA, marcada como opcional) y la importación de MI-GAN.

**6. Línea de tiempo (pedidos del 1-nov)**
- Tiradores FUERA del bloque, como en CapCut: el color llega exacto a la línea blanca cuando el imán pega el final (antes el tirador blanco quedaba dentro y parecía que el bloque terminaba antes).
- Deslizar FUERTE un bloque a la izquierda lo quita (con «Deshacer»). Mover despacio nunca borra; en un bloque sin seleccionar primero se mantiene pulsado (así desplazar la línea de tiempo no borra nada). `ui/Magnet.kt` › `SwipeDelete`.
- Efecto nuevo: desde 0 s hasta el final, o hasta 6 s si el vídeo es más largo (`ClipEdits.defaultSpan`). Los eventos cortos (explosión, rayo, impacto…) siguen naciendo en la línea blanca con su duración natural. Los proyectos antiguos no cambian.
- Eliminar quita también las ANCLAS desde Animar (antes solo las flechas; las anclas solo en Proteger).
- «Reemplazar» (antes «Sustituir», al final de la barra y fuera de la pantalla) ahora es el 2.º botón. En bloques de Animar (cámara, 2.5D, movimiento natural) abre el catálogo de Animar.
- Pruebas nuevas: `V4410TimelineTest` (5).

**Archivos:** `engine/DepthJob.kt` (nuevo), `engine/DepthService.kt`, `engine/DepthEstimator.kt`, `engine/DepthMath.kt`, `AppViewModel.kt`, `ui/DepthPanel.kt`, `ui/SuperResPanel.kt`, `ui/ModelsPanel.kt`, `app/build.gradle.kts` (versionCode 55, 44.10.0).

**Pruebas (aquí, no en un teléfono):**
- 13 pruebas nuevas en `V4410Test` (fin del cálculo IA, silueta del personaje, 2.5D automático, vista previa mejorada): OK.
- Con la imagen de las capturas: el detector clásico de caras NO encuentra esa cara (pelo sobre los ojos), pero la inundación del fondo sí separa al personaje; el movimiento entre extremos sube de 0 (sin mapa, lo que pasaba) a un desplazamiento claro del personaje frente al fondo.
- Sin probar en Android: la conexión `BIND_IMPORTANT`, el latido y la vista previa mejorada.

# Zeta Motion v44.9 — 2.5D rápido sin IA, IA que no cierra la app

**No se ha compilado con Gradle Android ni probado en un teléfono.** Lo que sí se probó está en cada sección.

## v44.9 — Lo que cambia

| Función | Estado | Archivos |
|---|---|---|
| **2.5D rápido (sin IA)**, como el efecto de profundidad de CapCut: al instante y sin descargas | HECHO | `DepthMath.quick()`, `DepthEstimator.estimateQuick()` |
| La IA de profundidad corre en un **proceso aparte**: si falta memoria se cierra solo ese proceso, el editor sigue abierto y se reintenta con menos memoria (518 → 392 → 266 px) | HECHO | `engine/DepthService.kt` (`android:process=":ia"`) |
| Si ni en modo mínimo cabe, mensaje claro y propuesta de «2.5D rápido» | HECHO | `AppViewModel.computeDepth()` |
| El nivel de memoria que funcionó se recuerda para la próxima vez | HECHO | preferencia `depthLevel` |
| Recorte de personaje con IA **quitado** de Zeta Motion (botón, descarga y modelo) | HECHO | se entrega aparte en `RecorteIA_para_ZetaPaint.zip` |
| Proteger con pincel y «Rellenar detrás del personaje» se mantienen | HECHO | |
| La placa de fondo se rehace sola si cambia Proteger (una placa vieja no puede «borrar» al personaje) | HECHO | `schedulePlateRefresh()` |

**Cómo se usa:** Animar › Profundidad 2.5D › Ajustar › «2.5D rápido (sin IA)» o «Con IA (más preciso)».

**Cómo funciona el 2.5D rápido:**
- Abajo = cerca y arriba = lejos.
- Lo detallado y saturado se acerca; lo liso (cielo, niebla) se aleja.
- Proteger (el personaje) va delante.
- Funciona mucho mejor con el personaje pintado en Proteger.

**Pruebas (aquí, no en un teléfono):**
- Desplazamiento real con el shader de la app, cantidad 0,025, 3 capas, con el personaje en Proteger:
  - anime: cerca −23 px, lejos +15 px (con IA: −23 y +14; sin zonas: 0 y 0);
  - paisaje: −25 y +16;
  - manhwa: cerca −18 px.
- 2.5D rápido: 0,15–0,2 s por imagen en la JVM de este servidor.
- 167 pruebas JVM OK. Las nuevas comprueban:
  - abajo cerca / arriba lejos;
  - Proteger al frente;
  - el desenfoque;
  - que el recorte ya no esté en Gradle ni en el manifiesto.

**Sin probar:** que el proceso aparte se recupere de verdad en tu teléfono cuando falta memoria. El código lo detecta (proceso cerrado sin resultado), pero no lo he podido ejecutar en Android.

## v44.8 — IA descargable dentro de la app

| Función | Estado | Archivos |
|---|---|---|
| Los modelos grandes (profundidad, recorte, MiDaS) ya NO van en el APK | HECHO | `app/zeta-models.gradle.kts` (solo los incluye con `ZETA_BUNDLE_MODELS=1`) |
| Descarga dentro de la app desde la URL oficial de GitHub, con SHA-256 comprobado; si no coincide, se borra y no se usa | HECHO | `engine/ModelStore.kt` |
| Reanuda descargas cortadas (sin conexión, Cancelar, app cerrada) | HECHO | cabecera Range sobre el `.part` |
| Ajustes › IA: cada modelo con su tamaño, Descargar / progreso / Cancelar / Borrar y espacio ocupado | HECHO | `ui/ModelsPanel.kt` |
| Botón de descarga donde hace falta: 2.5D › Ajustes (profundidad) y «Personaje y fondo» (recorte) | HECHO | `ui/DepthPanel.kt` |
| Aviso si estás con datos móviles | HECHO | `onMeteredNetwork()` |
| Sin descargar nada, todo lo demás funciona: efectos, cámara, claves, audio, exportación, 2.5D manual, pincel Proteger, caras, cielo/agua y «Mejorar imagen» | HECHO | estos ya van en la app |
| Solo procesadores ARM (`arm64-v8a`, `armeabi-v7a`): quita las librerías de emulador x86 | HECHO | `app/build.gradle.kts` |
| Permisos nuevos: `INTERNET` y `ACCESS_NETWORK_STATE`, SOLO para bajar los modelos públicos; no se envía ningún dato | HECHO | `AndroidManifest.xml` |

**Tamaños:**
- **APK:** unos 40 MB. Es una estimación (aquí no se puede compilar): ~5 MB de la app más las librerías de ONNX Runtime para dos tipos de procesador.
- **Descargas opcionales:**
  - Profundidad: 99 MB.
  - Recorte de personaje: 176 MB.
  - MiDaS (opcional, más ligero): 67 MB.

**Pruebas:**
- **Descargador real contra GitHub** con el mismo código de la app (`tools/banco_v44/DownloadDemo.kt`), con los tres modelos:
  - se corta al ~33 %;
  - se reanuda desde ahí;
  - termina con el SHA-256 correcto.
- **Tarea de Gradle:** por defecto deja la carpeta de modelos vacía; con `ZETA_BUNDLE_MODELS=1` descarga y verifica los tres.
- **Pruebas JVM:** 164 en total, todas OK. Las nuevas comprueban:
  - que URL, hash, tamaño y nombre de cada modelo coinciden en la app, en Gradle y en `LICENSES.json`;
  - que un `.part` o un archivo de tamaño distinto nunca se usa.

**Por qué no se instalaba la v44.7:** Android necesita mucho espacio libre durante la instalación (el APK más la copia que instala), y con 430 MB es fácil quedarse corto. No pude ver el motivo exacto en tu teléfono.

## Estado general

| Fase | Estado | Lo que falta o está bloqueado |
|---|---|---|
| 0 · Estabilidad y medición | HECHO | Medir en teléfonos reales (Diagnóstico) |
| 1 · Animar como Efectos, Ocultar/Solo, imán, dirección | HECHO | — |
| 2 · Calidad de efectos y varias instancias | HECHO | Textura de ruido precalculada y `mediump`: PENDIENTE hasta medir en GPU real |
| 3 · Fotogramas clave | HECHO | Curvas Bézier editables: PENDIENTE |
| 4 · Audio y escenas | PARCIAL | Grabaciones reales con licencia: **BLOQUEADO** (sintetizados e importación propia funcionan) |
| 5 · Profundidad IA 2.5D | HECHO | Tiempos y memoria en un teléfono: sin medir |
| 6 · Recorte y relleno | PARCIAL | MI-GAN dentro del APK: **BLOQUEADO** (relleno básico + importación manual); ajuste automático al borde de tinta: PENDIENTE |
| 7 · Caras/ojos, cielo/agua, mejora | HECHO con límites | MobileSAM: **BLOQUEADO** (se usa heurística); caras de manga con tramas: no se detectan |

## Fase 7 — Caras y ojos, cielo y agua, tocar para seleccionar, mejora de imagen

| Función | Estado | Archivos |
|---|---|---|
| Detector de caras anime/manga (cascada LBP de nagadomi, MIT, 247 KB, dentro del APK), con el mismo algoritmo que OpenCV y sin OpenCV | HECHO | `engine/AnimeFaceDetector.kt`, `assets/detect/lbpcascade_animeface.xml` |
| Propuesta de ojos para «Ojos brillantes» y «Parpadeo» (zona editable, la cara no se deforma) | HECHO | `engine/FaceRegions.kt`; efecto › Zona › «Detectar ojos» |
| Propuesta de pecho para la respiración (Movimiento por zona › Respiración) | HECHO | «Respiración (pecho)» |
| Cielo automático (unido al borde superior, lejano si hay profundidad) | HECHO | `engine/RegionTools.kt`; «Detectar cielo» en nubes, cielo, aurora, estrellas, pájaros, rayos… |
| Agua automática (banda azul ancha en la mitad inferior; descarta cielo, césped y ropa oscura) | HECHO | «Detectar agua» en agua, olas, reflejos, camino de sol… |
| Tocar para seleccionar (crece por color parecido y misma profundidad; tolerancia ajustable) | HECHO | efecto › Zona › «Tocar para seleccionar» |
| Nada de esto entra en Proteger: los efectos de cielo y agua no se derraman sobre el personaje | HECHO | |
| Todo es una zona normal del efecto: se deshace con ↶ y se corrige con el pincel | HECHO | `applyAiZone()` (máscara de imagen, como el recorte de la Fase 6) |
| Sugerencias de escena por reglas (color, cielo, agua, noche, B/N, caras); sin LLM | HECHO | Efectos › Escenas › «Para esta imagen» |
| Mejorar imagen x4 (Real-ESRGAN animevideov3, BSD-3, 2,5 MB, dentro del APK), por baldosas de 128 px, cancelable | HECHO | `engine/SuperRes.kt`; Ajustes › Mejorar |
| Reversible: el original no se toca; la mejorada solo se usa al exportar si el interruptor está activo; botón para borrarla | HECHO | `image_sr.png` en el proyecto |
| Comparación de la línea fina antes/después en la app | HECHO | recorte de la misma zona |
| MobileSAM (selección por toque con IA) | **BLOQUEADO** | sus pesos están en Google Drive / Hugging Face, no verificables desde aquí; se usa la heurística |

**Modelo de mejora (va DENTRO del ZIP, no se descarga):**
- `models/realesr_animevideov3_x4.onnx`: 2 490 686 bytes, SHA-256 `758c71d1b4eccf3e30e82a57fad0b0a9076842962ddbe6aac664fe4e4cf12577`.
- Origen: https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth (SHA-256 `b8a8376811077954d82ca3fcf476f1ac3da3e8a68a4f4d71363008000a18b75d`). Licencia BSD-3; el texto va en `assets/licenses/Real-ESRGAN_BSD-3.txt`.
- **Conversión:** aquí no había PyTorch ni el paquete `onnx`. `tools/banco_v44/pth_to_onnx.py`:
  - lee el .pth con un lector restringido, que solo admite las funciones de reconstrucción de tensores;
  - escribe el ONNX a mano: 18 Conv + 17 PReLU + PixelShuffle + suma de la entrada ampliada;
  - comprueba el resultado con ONNX Runtime contra una implementación NumPy independiente: error máximo 3·10⁻⁶.
- Los pesos no se modifican; solo cambia el formato.

**Pruebas realizadas (aquí, no en un teléfono):**
- **Caras** (`docs/v47/caras.txt`, `docs/v47/cara_ojos_respiracion.jpg`):
  - Anime color: detecta la cara de la chica, y los dos ojos propuestos caen sobre los ojos.
  - **No detecta** la cara del chico (rasgos masculinos angulosos), la del manga con tramas ni la del manhwa con los parámetros normales.
  - Con 2 vecinos el manhwa sí aparece, y la app lo intenta así marcándolo como «detección dudosa».
  - 0 falsos positivos en paisaje y ciudad.
  - Tiempo: 0,1–0,36 s en la JVM de este servidor.
- **Cielo y agua** (`docs/v47/cielo_y_agua_automaticos.jpg`, `docs/v47/regiones.txt`):
  - Paisaje: cielo 42 % y agua 30 %, que es justo el lago.
  - Ciudad de noche: cielo 57 %, sin agua.
  - Anime: cielo 11 %, sin agua.
  - Manhwa: la pared azul de fondo sale como «cielo» (27 %). Es una propuesta y hay que corregirla.
- **Errores encontrados y corregidos durante las pruebas:**
  - el agua elegía el bosque o la ropa azul oscura;
  - se sugería «Mar» por la ropa azul del anime;
  - se sugerían cielo y nieve para el manga B/N.
- **Mejora x4** (`docs/v47/mejora_x4_antes_despues.jpg`, `tools/banco_v44/sr_test.py`):
  - 435×619 → 1740×2476 en 2,5 s en la CPU de este servidor, por baldosas.
  - Sin costuras: el salto en la unión de baldosas (2,4–2,9) no supera la variación normal del interior (2,5–3,7).
  - **En un teléfono será bastante más lento y no está medido.**
- **Pruebas JVM:** 11 nuevas (`V47AssistTest`), **162 en total, todas OK**. Cubren:
  - la cascada completa (20 etapas, 642 características) y que no detecte caras en imágenes planas o de ruido;
  - el ecualizado, la imagen integral y la agrupación como OpenCV;
  - los ojos sobre manchas oscuras;
  - cielo y agua en un paisaje sintético (lago sí, césped no, cielo no);
  - que Proteger nunca se seleccione y que la ropa oscura no cuente como agua;
  - las reglas de sugerencias;
  - la ampliación por baldosas sin costuras, comparada píxel a píxel;
  - el hash del modelo de mejora.

**Limitaciones:**
- La cascada de caras es de 2011: falla con caras de perfil, masculinas angulosas o con tramas. Entonces la app lo dice y queda el pincel.
- Cielo y agua son heurísticas de color, conexión y profundidad. Con fondos azules lisos (paredes) pueden equivocarse; por eso son siempre propuestas.
- La vista previa sigue mostrando la imagen original; la mejorada solo se ve en la comparación y en la exportación.

## Fase 6 — Recorte de personaje y relleno del fondo

| Función | Estado | Archivos |
|---|---|---|
| Recorte del personaje en un toque con IA (isnetis de SkyTNT, Apache-2.0): el resultado va a **Proteger** | HECHO | `engine/CharacterSegmenter.kt`, `engine/CutoutMath.kt`; botón «Personaje IA» en Proteger y «Recortar personaje (IA)» en el 2.5D |
| El recorte es una máscara más: se deshace con ↶, se repasa con Pincel/Borrador y se guarda en el proyecto | HECHO | `MaskStroke.image`, `MaskLayers`, `ProjectStore` (`"img"`) |
| Aviso de recorte dudoso (manga B/N con tramas) o sin personaje | HECHO | `CutoutMath.uncertainty()/coverage()` |
| Relleno de lo que hay detrás del personaje (placa de fondo): al moverse, el fondo ya no arrastra el contorno | HECHO | relleno básico *pull-push* (`CutoutMath.fill`); shader `uPlate`/`gPlate` |
| La placa se guarda en el proyecto (`plate.png`) y avisa si Proteger cambió («Actualizar relleno») | HECHO | `AppViewModel.computePlate()/loadPlateCache()` |
| Misma placa en vista previa y exportación | HECHO | `ExportRequest.plate` |
| **MI-GAN** (relleno con IA) incluido en el APK | **BLOQUEADO** | motivo abajo |
| Importar MI-GAN a mano (archivo `.onnx` «pipeline») y usarlo en el relleno | HECHO, **sin probar con el modelo real** | 2.5D › Ajustes › «Importar MI-GAN (.onnx)»; se comprueba que tenga las entradas `image` y `mask` |
| Ajustes › Licencias: isnetis, MI-GAN (opcional) y botón para liberar las copias internas de los modelos | HECHO | `assets/models/LICENSES.json` |

**Modelo de recorte (se descarga al compilar, como los de profundidad):**

| Archivo en el APK | Licencia | Origen | Bytes | SHA-256 |
|---|---|---|---|---|
| `models/isnetis_anime.onnx` | Apache-2.0 (SkyTNT/anime-segmentation) | https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-anime.onnx | 176 069 933 | `f15622d853e8260172812b657053460e20806f04b9e05147d49af7bed31a6e99` |

- Es la exportación ONNX que publica rembg (MIT). Su código la verifica con md5 `6f184e756bb3bd901c8849220a83e38e`; aquí ese md5 coincidió.
- **Riesgo residual:** el conjunto de datos de entrenamiento de SkyTNT se hizo con ilustraciones anime de terceros, y su procedencia no está documentada archivo por archivo.
- El APK crece unos 176 MB más (unos 340 MB con los tres modelos).

**Por qué falta MI-GAN dentro del APK:**
- Sus pesos (licencia MIT) solo se publican en Hugging Face y Google Drive. Desde aquí no se podía acceder a ninguno de los dos.
- En GitHub solo encontré una versión TorchScript (`migan_traced.pt` de IOPaint), y convertirla a ONNX necesita PyTorch, que no se podía instalar.
- No invento un enlace ni lo doy por incluido. Si descargas tú `migan_pipeline_v2.onnx` (Hugging Face `andraniksargsyan/migan`), lo importas en la app.

**Pruebas realizadas (aquí, no en un teléfono):**
- **Recorte real** con el modelo y el mismo preprocesado que la app (`tools/banco_v44/seg_test.py`): ~1,9 s y ~1,1 GB de RAM en la CPU de este servidor. **En un teléfono no está medido;** ese pico de memoria puede ser un problema en móviles con poca RAM.
  - Anime color y manhwa: el recorte es limpio.
  - Manga B/N con tramas: el recorte es parcial (ver `docs/v46/recorte_anime_manga_manhwa.jpg`). Por eso la app lo avisa y pide repasarlo con el pincel.
- **Relleno real:** el mismo código Kotlin de la app (`tools/banco_v44/PlateDemo.kt`) tarda 46 ms en 435×619 en la JVM de este servidor.
- **Render 2.5D** con el shader de la app (IA 3 capas, cantidad 0,035), sin y con relleno (`docs/v46/comparativa_recorte_y_relleno.jpg`, `docs/v46/bordes_sin_y_con_relleno.jpg`):
  - el contorno doble y dentado alrededor del personaje desaparece;
  - la energía de bordes falsos en una franja de 12 px alrededor del personaje baja de 13,3 a 11,5 en el anime y de 12,8 a 8,1 en el manhwa (`docs/v46/bordes_laplaciano.json`).
- **Shader:** compilan las 75 variantes; el bloque coincide con el driver.
- **Pruebas JVM:** 8 nuevas (`V46CutoutTest`), 151 en total, todas OK. Cubren:
  - encuadre igual que `inference.py` de SkyTNT;
  - tensor con relleno negro;
  - recorte de la máscara sin colarse el relleno;
  - dilatación;
  - que el relleno no copie el color del personaje y respete los píxeles conocidos;
  - que el interruptor llegue al shader;
  - que el modelo esté en Gradle y en el manifiesto con su hash.

**Limitaciones:**
- El relleno básico es suave: no inventa detalle (ventanas, ramas). Basta para la franja que destapa el 2.5D, pero no para borrar objetos grandes. Para eso hace falta MI-GAN.
- La importación de MI-GAN está programada siguiendo `create_onnx_pipeline.py` (entradas `image` y `mask` uint8, salida `result`), pero **no se ha ejecutado con el archivo real**.

## Fase 5 — Profundidad IA para el 2.5D

| Función | Estado | Archivos |
|---|---|---|
| Motor de inferencia ONNX Runtime Android 1.20.0 (MIT), solo CPU, un modelo cargado a la vez y cerrado al terminar | HECHO | `app/build.gradle.kts`, `engine/DepthEstimator.kt` |
| Modelo principal Depth Anything V2 **Small** (Apache-2.0); alternativa MiDaS v2.1 small (MIT) si el principal falta o falla (también por falta de memoria) | HECHO | `engine/DepthEstimator.kt` |
| Pesos dentro del APK (funciona sin internet), descargados AL COMPILAR desde las publicaciones oficiales de GitHub y verificados con SHA-256 | HECHO | `app/zeta-models.gradle.kts` (probado aquí: descarga real, caché y fallo 404) |
| Si la descarga falla al compilar, el APK se genera igual y el 2.5D queda en modo manual | HECHO | aviso en el registro de Gradle, en Ajustes del 2.5D y en Ajustes › Licencias |
| Profundidad calculada **una vez por imagen**, en segundo plano, con Cancelar; se guarda en el proyecto (`depth.png` + `depth.txt`) y se reutiliza al abrirlo si la imagen no cambió | HECHO | `DepthEstimator.loadCached()`, `DepthMath.stamp()` |
| La animación solo lee el mapa (textura): la IA nunca corre por fotograma | HECHO | `fondo.frag` (`uDepth`), `engine/FondoGl.kt` |
| 2–4 capas de profundidad (o 1 = relieve continuo) | HECHO | `fondo.frag` (`depthQuant`), control «Capas de profundidad» |
| Corrección manual: pincel **Acercar** (Zona verde) y **Alejar** (Zona azul) sobre el mapa, con fuerza ajustable | HECHO | `parallaxDepth()`; control «Fuerza de Acercar/Alejar» |
| **Ver mapa de profundidad** (blanco = cerca; verde/azul/rojo = tus correcciones y Proteger) | HECHO | solo en la vista previa, nunca se exporta |
| Proteger sigue siendo el plano de foco: el personaje no se deforma | HECHO | |
| Sin modelo o si la IA falla → 2.5D manual con Zona azul / Zona verde, como en la v43 | HECHO | |
| Aviso de mapa plano (manga B/N, tramas, viñetas) en lugar de presentarlo como 2.5D listo | HECHO | `DepthMath.contrast()` < 0,12 |
| Misma profundidad en vista previa y exportación | HECHO | `ExportRequest.depth` |
| Ajustes › Licencias muestra cada modelo, su licencia, SHA-256 y si está o no dentro de ESTE APK | HECHO | `assets/models/LICENSES.json`, `ui/LicensesPanel.kt` |
| LiteRT / GPU / NNAPI | NO (a propósito) | la orden pide un solo runtime salvo que un benchmark en el teléfono lo justifique |

### Cómo se usa
1. Animar › Profundidad 2.5D. Al abrir sus Ajustes se calcula la profundidad (una sola vez por imagen).
2. «Ver mapa» para revisarlo.
3. Si algo está mal colocado: pincel **Acercar** (verde) o **Alejar** (azul).
4. «Capas de profundidad»: 1 = continuo; 2–4 = planos separados.
5. «Usar profundidad IA» apagado = el 2.5D manual de siempre.

### Modelos (se descargan al compilar; NO van dentro del ZIP)
| Archivo en el APK | Licencia | Origen | Bytes | SHA-256 |
|---|---|---|---|---|
| `models/depth_anything_v2_vits.onnx` | Apache-2.0 | https://github.com/fabio-sim/Depth-Anything-ONNX/releases/download/v2.0.0/depth_anything_v2_vits_dynamic.onnx | 99 092 268 | `46c4e8eeda3a27f34701831b6a2ec7753d7b38779b215acb5633424703deed8f` |
| `models/midas_v21_small_256.onnx` | MIT | https://github.com/isl-org/MiDaS/releases/download/v2_1/model-small.onnx | 66 764 249 | `2d8c6cb8f415229daf1eb041024208e2608c9f98e17c81cc7c6ecb449c56fd58` |

- Licencia de Depth Anything V2: su README dice «Depth-Anything-V2-Small model is under the Apache-2.0 license». Los modelos Base, Large y Giant son CC-BY-NC-4.0 y **no** se usan.
- Tamaño: el APK crece unos 165 MB.
- La primera vez que se usa un modelo, se copia al almacenamiento interno de la app (ONNX Runtime necesita una ruta de archivo).
- Para compilar sin red: `ZETA_MODELS_DIR=<carpeta con los .onnx>`.
- Para no incluirlos: `ZETA_SKIP_MODELS=1`.

### Pruebas realizadas (aquí, no en un teléfono)
- **Tareas de compilación:** la tarea Gradle de descarga se ejecutó con Gradle 8 fuera de Android: descarga real desde GitHub, SHA-256 correcto, caché en la 2.ª ejecución y fallo 404 que no rompe la compilación.
- **Profundidad real:** los dos modelos se ejecutaron con ONNX Runtime (CPU de este servidor) usando el mismo preprocesado que la app (`tools/banco_v44/depth_test.py`). Depth Anything V2 tarda ~0,6 s aquí y MiDaS ~0,05 s; **en un teléfono será más lento y no está medido**.
- **Desplazamiento real entre capas:** render con el shader de la app en Mesa, cantidad 0,025 (la de serie), estilo Planos, 3 capas, entre los extremos del vaivén (t = 0,25 y 0,75). Medido con emparejamiento de parches (`tools/banco_v44/depth_bench.py`, `docs/v45/desplazamientos.json`):

| Imagen | Sin zonas (v43) cerca / lejos | IA 1 capa (continua) cerca / lejos | IA 3 capas cerca / lejos |
|---|---|---|---|
| Anime color (435 px de ancho) | 0 / 0 px (solo zoom) | −18 / +7 px | −23 / +14 px |
| Manga B/N | 0 / 0 px | −16 / +14 px | −23 / +15 px |
| Paisaje sintético | — / 0 px | — / +15 px | — / +16 px |

  En el paisaje, la zona cercana (el agua) es lisa y no tiene textura que medir.
- **Corrección Acercar:** en el manga, la esquina superior derecha (la IA la puso lejos) pasa de +15 px a −4 px al pintarla de verde.
- **Imágenes:**
  - `docs/v45/comparativa_2_5d_ia.jpg`: original, mapa, sin zonas, IA t = 0,25 y 0,75, y diferencia.
  - `docs/v45/anime_ia_3capas_t025_t075.jpg`.
  - `docs/v45/manga_mapa_con_acercar.jpg`.
- **Shader:** compilan las 75 variantes en Mesa. El bloque `ZetaParams` (4384 bytes, 297 miembros) coincide con el driver.
- **Pruebas JVM:** 10 nuevas (`V45DepthTest`), 143 en total, todas OK. Cubren:
  - tamaño de entrada en múltiplos de 14 y normalización ImageNet;
  - percentiles robustos a valores atípicos y reescalado;
  - capas iguales que el shader;
  - aviso de mapa plano;
  - caché y huella de la imagen;
  - parámetros que llegan al uniform;
  - que los hashes del manifiesto coincidan con los de Gradle.

### Limitaciones (sobre todo en manga)
- **Manga B/N:** el mapa sale más borroso y «a manchas». Los dos personajes se funden en una sola masa, y las tramas pueden leerse como relieve. Revísalo con «Ver mapa» y corrige con Acercar y Alejar antes de exportar. Si el mapa es casi plano, la app lo avisa.
- **Bordes:** al mover mucho, en los bordes de las siluetas aparece un pequeño estiramiento, porque no hay nada pintado detrás del personaje. Con la cantidad de serie (0,025) es leve; con 0,05 se ven rayas. Rellenar lo que hay detrás (*inpainting*) es la Fase 6.
- **Imágenes planas** (ilustraciones sintéticas, colores lisos): la IA puede poner árboles y montañas en el mismo plano.
- **No probado en un teléfono:** el tiempo de cálculo, la memoria y la copia del modelo (unos 100 MB).
- **Errores:** si la memoria se agota, se intenta MiDaS; si también falla, queda el modo manual y el error se anota en el registro de Diagnóstico.
- **Aviso de .so en GitHub Actions:** ONNX Runtime trae librerías nativas (`.so`), así que el paso «Verificar firma» mostrará el aviso de alineación de 16 KB. Es un aviso, no un error. Las librerías vienen de ONNX Runtime y no las toqué.
- **Tamaño de la imagen:** el mapa se calcula sobre la imagen reducida a 518 px. El shader lo amplía con filtro lineal y dilata lo cercano para que el estiramiento caiga en el fondo.

## Fase 4 — Audio y escenas

| Función | Estado | Notas |
|---|---|---|
| Sonidos como bloques de la línea de tiempo: inicio, fin, arrastrar, recortar, dividir, duplicar, silenciar, Solo, tiempo exacto | HECHO | reutilizan los bloques de efectos (`snd_<sonido>`); `model/SoundClips.kt` |
| Volumen (0–150 %), panorama, fundido de entrada/salida por bloque; volumen y panorama animables con ◆ | HECHO | `ui/SoundPanels.kt` |
| Bucles sin clic | HECHO | ambientes generados para el bucle exacto; tus grabaciones, con fundido cruzado en el empalme |
| Misma mezcla en la vista previa y en el MP4 (AAC sincronizado) | HECHO | `SoundMixer` es el mismo código en las dos (sin probar en un teléfono) |
| Se mantiene la importación de música | HECHO | sin cambios |
| Biblioteca por escenas: Noche, Campo, Ciudad, Tormenta, Mar, Viento, Terror, Acción (eventos), Mis sonidos | HECHO | |
| 12 sonidos sintetizados nuevos: búho, goteras, latidos, crujidos, pasos, tren, claxon, gaviotas, whoosh, impacto, explosión, magia (27 en total) | HECHO | sintetizados por el código de la app, no son grabaciones |
| Importar grabaciones propias (hasta 60 s) como sonidos del proyecto | HECHO | Audio › Mis sonidos |
| **Grabaciones reales con licencia** (grillos, gallo, tráfico, truenos, olas…) | **BLOQUEADO** | motivo abajo; nada se inventa ni se da por incluido |
| `assets/sounds/LICENSES.json` + pantalla Ajustes › Licencias | HECHO | lista lo que se incluye y lo que falta |
| 11 escenas de un toque (Tormenta, Noche lluviosa, Desierto ventoso, Ciudad, Bosque, Mar, Batalla anime, Mañana tranquila, Terror, Nieve intensa, Atardecer) con efectos y sonidos | HECHO | cada capa se ajusta después; Efectos › Escenas |
| Beat Sync (análisis de la señal, sin IA): marca los golpes, el imán se pega a ellos y añade destello, temblor u onda en cada golpe | HECHO | Audio › Ritmo; `engine/BeatDetect.kt` |
| Ambientes de proyectos v43 | HECHO | pasan solos a bloques de sonido de todo el vídeo, con el mismo volumen |

**Por qué faltan las grabaciones reales:**
- Desde el entorno donde se programó esta versión solo se podía descargar de GitHub; Freesound, OpenGameArt, itch.io y Pixabay estaban bloqueados.
- En GitHub no encontré ninguna biblioteca de ambientes con licencia CC0 comprobable archivo por archivo.
- No incluyo nada dudoso: ni BBC RemArc, ni archivos NC, ni Sonniss, ni Pixabay.

Mientras tanto:
- Suenan los 27 sonidos sintetizados.
- Puedes importar tus propias grabaciones.

Si me pasas grabaciones propias, o enlaces a archivos CC0 concretos que pueda comprobar, las añado con su autor, URL, SHA-256 y atribución en `LICENSES.json`.

**Pruebas de audio:**
- `docs/v44/escena_tormenta.m4a` y `docs/v44/escena_noche_terror.m4a`, generados con el mismo mezclador de la app (`tools/banco_v44/AudioDemo.kt`).
- No se ha escuchado en un teléfono.

## Fase 3 — Fotogramas clave

| Función | Estado | Archivos |
|---|---|---|
| Rombo ◆ junto a cada control animable (intensidad, tamaño, posición, opacidad, brillo, zoom…) | HECHO | `ui/KeyframeControls.kt`, `ui/Panels.kt` |
| Claves por bloque y por parámetro, guardadas en el proyecto | HECHO | `model/Keyframes.kt`, `model/EffectClip.kt` (`keys`), `model/ProjectStore.kt` |
| Curvas: Lineal, Entrada suave, Salida suave, Entrada y salida | HECHO | mantener pulsado el rombo |
| Bézier editable | PENDIENTE | las 4 curvas cubren los casos habituales |
| Rombos de las claves dibujados sobre el bloque en la línea de tiempo | HECHO | `ui/Timeline.kt` |
| Cambiar un control ya animado crea o actualiza la clave del cabezal (como en CapCut) | HECHO | `AppViewModel.autoKey()` |
| Mover el bloque mueve sus claves; recortar por la izquierda las deja en su instante real; dividir reparte las claves sin salto; duplicar las copia; sustituir conserva las de posición/dirección | HECHO | `AppViewModel`, `Keyframes.split/shift` |
| Cámara real animada: estilo «Libre» con zoom, posición y giro animables | HECHO | `fondo.frag` (modo 9), `uCamManual` |
| Mismo cálculo en vista previa, al arrastrar el cabezal y al exportar | HECHO | `RenderParams.keyTracks` + `FrameUniforms` (código compartido) |
| Velocidad (ciclos), estilo, tipo o máscara animables | NO (a propósito) | cambiar la velocidad durante el vídeo rompe el bucle y da saltos |

### Cómo se usa
1. Selecciona un bloque y entra en **Ajustar**.
2. Pon el cabezal donde empieza el cambio y toca el **◆** del control: se crea la primera clave.
3. Mueve el cabezal a otro momento y cambia el valor: se crea otra clave sola.
4. Mantén pulsado el ◆ para elegir la curva o borrar la animación de ese control.
- Para la **cámara animada**: Animar › Cámara › «Cámara libre (◆)». Anima zoom, posición y giro del mismo modo.

### Notas
- Una animación con claves no vuelve sola al inicio. Si el vídeo se va a reproducir en bucle, pon la última clave igual que la primera.
- Proyectos v43: sin claves, todo se ve igual.
- La dirección se interpola entre vectores. Un giro de 180° entre dos claves pasa por el centro; para giros grandes, pon una clave intermedia.

**Render real:** `docs/v44/fase3_fotogramas_clave.jpg`.
- Cámara libre: zoom de 1 a 2,2 con paneo.
- Lluvia que crece con entrada suave.
- Explosión que cambia de sitio.

## Fase 2 — Efectos con calidad

| Función | Estado | Notas |
|---|---|---|
| 7 efectos nuevos: Tormenta de arena, Sombras de nubes, Lluvia en el cristal, Bandada de pájaros, Aurora boreal, Luces de ciudad (Ventanas/Neón/Tráfico), Destello de lente | HECHO | procedurales, sin imágenes de terceros; cierran el bucle (`tools/banco_v44/seam.py`) |
| Miniaturas animadas reales de los 7 | HECHO | renderizadas con el shader de la app sobre imágenes sintéticas |
| 5 escenas nuevas (Tormenta de arena, Noche con aurora, Ciudad de noche, Lluvia en la ventana, Pájaros al atardecer) | HECHO | Efectos › Escenas |
| Dos bloques del MISMO efecto a la vez, cada uno en su sitio | HECHO para explosión, salpicadura, corte y humo (2.ª instancia propia) y para ceniza, hojas, brasas, polvo, burbujas, electricidad, enfoque, onda, destello, trama, papel y tinta (ranura «Copia» si no la usas) | el resto sigue sumando intensidad, como en la v43; un 3.er bloque solapado no se dibuja aparte |
| Lluvia: gotas cercanas grandes, charcos que refractan, neblina lejana | HECHO | «Gotas cercanas» viene a 0,35; charcos y neblina a 0 |
| Nieve: remolinos y bruma de ventisca | HECHO | a 0 por defecto |
| Niebla: bancos alargados en horizontal, velo de fondo, altura de la niebla baja | HECHO | |
| Estrellas fugaces (2 por bucle) | HECHO | control «Estrellas fugaces» |
| Rayos de luz: estilo «Luz de luna» | HECHO | |
| Hojas: «Remolino de hojas» alrededor del origen | HECHO | |
| Viento global: ráfagas y turbulencia | HECHO | mueven lluvia, nieve, sakura, niebla, hojas, ceniza, brasas, polvo y Natural |
| La explosión ya no nace en la cara (74 %, 70 %) | HECHO | solo en efectos nuevos; un proyecto v43 conserva su posición |
| «Detrás del personaje» por defecto (niebla, nubes, aurora, estrellas, pájaros, explosión, sombras de nubes) | HECHO | solo actúa si pintaste Proteger |
| Textura de ruido precalculada en lugar de FBM por píxel | PENDIENTE | sin medir en una GPU Adreno/Mali no sé si compensa; Mesa por software no sirve para medirlo |
| `mediump` donde sea seguro | PENDIENTE | mismo motivo; sin medir es fácil crear bandas |
| No bajar la resolución de exportación | HECHO | no se tocó |

**Comparativas reales** (render con el shader de la app en Mesa):
- `docs/v44/comparacion_v43_v44.jpg`: v43 a la izquierda, v44 a la derecha.
  - Lluvia en anime y paisaje.
  - Ventisca en manga B/N.
  - Niebla en paisaje.
  - Explosión en anime.
- `docs/v44/efectos_nuevos.jpg`: los 7 efectos nuevos, 4 instantes cada uno.
- `docs/v44/dos_bloques_a_la_vez.jpg`: una explosión, luego dos a la vez, dos humos y dos bloques de hojas.

**Sobre las imágenes:**
- El anime en color es tu captura (solo se usa aquí, en `docs/`; no va en el APK).
- El manga B/N es esa misma imagen convertida a trama.
- El paisaje y la ciudad son sintéticos, hechos aquí.
- No se ha probado con Proteger pintado: «detrás del personaje» no aparece en las comparativas.

## Fase 1 — Interfaz y línea de tiempo

| Función | Estado | Archivos |
|---|---|---|
| Animar con catálogo visual como Efectos (Cámara, Profundidad 2.5D, Viento y tela, Agua y cielo, Rutas, Personaje) | HECHO | `model/AnimCatalog.kt`, `ui/AnimCatalogPanel.kt` |
| Tocar = probar sin confirmar; tocar otra vez = Ajustar; ✓ confirma, ✕ descarta | HECHO | `AppViewModel.previewAnim()/discardAnim()` |
| Ya no se abre el pincel antes de ver la animación (la zona se pinta en Ajustar › Zona) | HECHO | `AppViewModel.editEffect(autoPaint)` |
| Ojo por bloque (Ocultar/Mostrar): se guarda y se respeta al exportar | HECHO | `ui/Workspace.kt`, `model/EffectClip.kt` |
| Solo (temporal, no se guarda) + «Mostrar todos» | HECHO | `AppViewModel.toggleSolo()`, `ui/TimelineExtras.kt` |
| Acciones del bloque: Ajustar, Ocultar, Solo, Tiempo, Mover, Dividir, Duplicar, Sustituir, Eliminar | HECHO | `ui/Workspace.kt` |
| Imán ON/OFF, ~8 dp ajustable (4–16), histéresis 1,5×, aviso «Imán +0,12 s», vibración opcional | HECHO | `ui/Magnet.kt`, `ui/Timeline.kt` |
| Tiempo exacto al 0,01 s (botones ±0,01 / ±0,1 o escribir el número) | HECHO | `model/PreciseTime.kt`, `ui/TimelineExtras.kt` |
| Joystick 2D: ángulo + fuerza, con flecha y nombre de la fuerza | HECHO | `ui/WindControls.kt`, `model/Wind.kt` |
| La fuerza llega al shader (más fuerza = más rápido y con estela) | HECHO | `fondo.frag` (`windOf`, `oriented`) |
| Viento global (Ajustes › Viento) + «Seguir el viento global» por efecto | HECHO (dirección y fuerza) | `ui/WindControls.kt` |
| Ráfagas y turbulencia del viento global | PENDIENTE (Fase 2) | — |
| Origen en la imagen + flecha de trayectoria | PARCIAL | ya existía «Mover» para humo, explosión, energía…; lluvia y nieve usan el joystick |
| Modo Fácil (máx. 4 controles) / Avanzado (todos), sin perder ajustes | HECHO | `ui/SimpleEditorPanels.kt` |

### Cómo se usa
- **Animar:** al tocarlo se abre el catálogo.
  - Toca una miniatura para verla en tu imagen; su bloque aparece en la línea de tiempo.
  - Tócala otra vez para **Ajustar** (Ajustes, Estilo, Tiempo, Zona).
  - **✓** guarda; **✕** deja todo como estaba.
  - «Catálogo» en la barra de Animar lo vuelve a abrir.
  - Ruta, Ancla y Congelar siguen en la barra y también están en la categoría «Rutas».
- **Bloque seleccionado:**
  - **Ocultar** lo apaga sin borrarlo. No sale en el vídeo y se ve rayado en la línea de tiempo.
  - **Solo** muestra únicamente ese bloque mientras miras. La música sigue sonando y al exportar salen todos los bloques visibles; el panel de Exportar lo avisa.
  - Para salir de Solo: la pastilla rosa «Solo · Mostrar todos».
- **Imán** (pastilla arriba a la izquierda de la línea de tiempo):
  - tocar = ON/OFF;
  - mantener pulsado = distancia y vibración.
  - Con el imán apagado, el bloque queda exactamente donde lo sueltas.
- **Tiempo** (en las acciones del bloque): inicio, duración y final al 0,01 s.
- **Viento:**
  - En los efectos de partículas (lluvia, nieve, hojas, humo…) el dial de dirección es ahora un joystick.
  - El punto marca hacia dónde va y, cuanto más lejos del centro, más fuerza.
  - «Seguir el viento global» hace que el efecto use el viento de **Ajustes › Viento**.

### Compatibilidad con proyectos v43
- Los bloques sin el campo `hidden` se abren visibles.
- Sin las claves nuevas (`force`, `followWind`, `wind.*`) todo se ve igual que en la v43: fuerza 1 = el mismo dibujo.

## Fase 0 — Estabilidad y medición

## Qué se implementó (y dónde)

| Función | Estado | Archivos |
|---|---|---|
| Ajustes › Diagnóstico: FPS, cuadro medio/p95, CPU, GPU, escala, RAM, temperatura | HECHO | `ui/DiagnosticsPanel.kt`, `diag/FrameStats.kt`, `diag/DeviceInfo.kt` |
| GPU por cuadro con EXT_disjoint_timer_query (sin bloquear) | HECHO | `engine/GpuTimer.kt` |
| Prueba de 1, 3 y 6 efectos, 60 s cada una, con informe TXT | HECHO | `diag/Benchmark.kt`, `AppViewModel.startBenchmark()` |
| Registro local de errores (GL, shader, MediaCodec, cierres, poca memoria) | HECHO | `diag/ZetaLog.kt` |
| Exportación en servicio con notificación de progreso y Cancelar | HECHO | `export/ExportService.kt`, `AndroidManifest.xml` |
| Vista previa sin glFinish ni glReadPixels | HECHO | `engine/PreviewRenderer.kt`, `diag/AdaptiveScale.kt` |
| Base de datos de Ocultar y Solo (guardado, vista previa y exportación) | HECHO | `model/EffectClip.kt`, `engine/RenderParams.kt` |
| R8 / minify | PENDIENTE (desactivado a propósito) | — |

### Diagnóstico
- Está en **Ajustes › Diagnóstico** del editor. No aparece en ningún otro sitio.
- Si el teléfono no puede medir algo, pone **«No disponible»**; nunca se inventa un número.
  - La GPU solo se mide si el teléfono tiene `GL_EXT_disjoint_timer_query`.
  - La temperatura necesita Android 10 o superior.
- **Prueba de rendimiento:**
  - solo empieza al tocar «Iniciar prueba»;
  - se ejecuta sobre tu imagen y **no cambia el proyecto**: al terminar o detenerla todo vuelve como estaba.
- **«Compartir informe»** crea un TXT con:
  - el teléfono;
  - los resultados de cada etapa;
  - el registro de errores.

### Registro de errores
- Se guarda en la carpeta privada de la app, como máximo 2 × 256 KB.
- No se envía a ningún servidor.
- Anota:
  - shaders que no compilan;
  - fallos de OpenGL;
  - perfiles que el codificador rechaza;
  - exportaciones fallidas;
  - avisos de poca memoria;
  - cierres inesperados (la app se cierra igual que antes, pero queda anotado).

### Exportación en segundo plano
- La exportación ya no se corta si sales del editor o de la app.
- Mientras exporta aparece una notificación con el porcentaje y un botón **Cancelar**.
- En Android 13+ se pide una sola vez el permiso de notificaciones. Si lo rechazas, se exporta igual pero sin la notificación visible.
- Al terminar fuera de la app, una notificación avisa de si se guardó, falló o se canceló.
- Tipo de servicio:
  - «mediaProcessing» en Android 15+;
  - «dataSync» en Android 10–14.
- EGL, MediaCodec y MediaMuxer se siguen liberando en el `finally` del exportador.

### Vista previa sin glFinish
- Antes, cada 4 cuadros se paraba el hilo hasta que la GPU terminaba.
- Ahora la resolución adaptativa se ajusta así:
  - con el intervalo real entre cuadros;
  - con el temporizador de GPU, cuando el Diagnóstico está abierto y el teléfono lo admite.
- La exportación sigue a la resolución elegida.
- El único `glFinish` que queda está en el hilo que compila shaders en segundo plano, no en el dibujo de la vista previa.

### R8
- Sigue **desactivado**: activarlo sin compilar ni probar el APK podría romper la carga de shaders y la serialización de proyectos.
- Para activarlo hace falta:
  - compilar con `isMinifyEnabled = true`;
  - probar abrir y guardar proyectos v43 y exportar.

## Pruebas realizadas
- **Kotlin:** comprobación de tipos de todo el código contra stubs de Android/Compose escritos a mano (aquí no hay acceso a Maven). Resultado: 0 errores.
- **133 pruebas JVM**, todas superadas.
- **Fase 4** (7 pruebas nuevas, en `V44Phase4Test`):
  - fundidos, volumen con claves y panorama de igual potencia;
  - silenciar y Solo en un sonido (calla la música, pero no si el Solo es de un efecto);
  - los ambientes v43 pasan a bloques;
  - un evento suena solo dentro de su bloque;
  - todos los sonidos del catálogo se generan y empalman sin clic;
  - remuestreo y bucle de tus grabaciones;
  - Beat Sync encuentra los 7 golpes de una prueba con un error menor de 40 ms;
  - el manifiesto de licencias coincide con lo que se incluye.
- **Fase 3** (7 pruebas nuevas, en `V44Phase3Test`):
  - las 4 curvas, y el valor antes y después de las claves;
  - una clave cercana sustituye a la anterior;
  - dividir el bloque no hace saltar la animación;
  - recortar por la izquierda conserva el instante real de las claves;
  - el formato de guardado lee y escribe lo mismo e ignora datos rotos;
  - la lluvia sigue sus claves cuadro a cuadro;
  - la cámara libre;
  - sustituir el efecto conserva las claves de posición.
- **Fase 2** (6 pruebas nuevas, en `V44Phase2Test`):
  - los 7 efectos tienen su bloque en el shader y sus uniforms;
  - la distribución del bloque de parámetros (4368 bytes) coincide miembro a miembro (296) con la que da Mesa;
  - dos explosiones a la vez, cada una con su sitio y su visibilidad;
  - las hojas usan la Copia libre y respetan la Copia del usuario;
  - los valores iniciales no cambian proyectos existentes;
  - el viento global solo actúa si está activado.
- **Shader en Mesa** (OpenGL ES 3.0 por software): compilan las 73 variantes, más «todas» y «ninguna».
- **Bucle:** en todos los efectos nuevos y mejorados, el último cuadro empalma con el primero (`seam.py`).
- **Render real** con el shader de la app (`docs/v44/fase1_ocultar_y_viento.jpg`):
  - la nieve oculta desaparece;
  - la fuerza 0,5 y la 2 cambian la estela;
  - seguir el viento global cambia la dirección.
- **Fase 1** (8 pruebas nuevas, en `V44Phase1Test`):
  - el imán se pega a 15 px, sigue pegado a 28 px, se suelta a 34 px y no se mueve si está apagado;
  - tiempo exacto, con sus límites y la lectura de «1,25» o «,5»;
  - la fuerza del joystick es la longitud del vector y los ajustes por defecto dan lo mismo que la v43;
  - el viento global y el bloque con fuerza propia;
  - todas las entradas del catálogo de Animar existen y tienen miniatura.
- **Fase 0** (7 pruebas nuevas, en `V44Phase0Test`):
  - FPS y p95 a partir de intervalos reales;
  - la GPU queda vacía si no se mide;
  - la escala baja rápido y sube despacio;
  - el informe dice «No disponible»;
  - **los 66 efectos se apagan de verdad al ocultar su bloque**;
  - «Solo» apaga los efectos sin bloque.

**No probado:**
- No se ha compilado con Gradle ni se ha probado en un teléfono.
- Las miniaturas de cámara (`assets/thumbs/anim_cam*.webp`) se renderizaron con el shader de la app sobre un paisaje sintético hecho aquí; no contienen imágenes de terceros.
- Falta comprobar en el teléfono:
  - el catálogo de Animar, el imán, la vibración y el diálogo de Tiempo;
  - el joystick con el dedo;
  - el rendimiento de la lluvia y la nieve con las capas nuevas, y de dos explosiones a la vez (si va lento, baja «Gotas cercanas», «Neblina», «Remolinos» o «Bruma»);
  - si alguna variante no compila en tu GPU, aparecerá en Ajustes › Diagnóstico › registro;
  - la notificación;
  - Cancelar desde la notificación;
  - exportar con la app en segundo plano;
  - las lecturas de Diagnóstico.

## Seguridad
- Sin secretos ni claves de producción.
- Se mantiene la firma beta de pruebas `app/zeta-beta-public-only.jks` (no sirve para Play Store).
- El workflow solo cambia de nombre.

## Banco de pruebas incluido (no forma parte del APK)
`tools/banco_v44/`:
- `zeta_render.py`: render con Mesa del shader real.
- `Dump.kt`: vuelca el bloque de uniforms desde el código Kotlin.
- `rs.py`: escenas y cuadrículas.
- `make_land.py`: imágenes de prueba sintéticas.
- `make_anim_thumbs.py` y `make_fx_thumbs.py`: miniaturas.
- `seam.py`: comprueba que el bucle empalma.
- `compare_layout.py`: compara el bloque de parámetros con el driver.
