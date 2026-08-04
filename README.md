# Geo Videos V41

Versión centrada en música en segundo plano, Geo Mix, reproducción automática e historial, conservando las mejoras del reproductor V40.

## Música y reproducción continua

- Botón visible **Reproducir música** dentro del reproductor.
- En modo música se solicita una pista de audio compatible, evitando mantener el video activo innecesariamente.
- El audio continúa mediante el servicio multimedia al salir de Geo Videos o apagar la pantalla.
- La notificación conserva los controles Anterior, Reproducir/Pausar y Siguiente cuando existe una cola disponible.
- Al volver a modo video se conserva la posición de reproducción.

## Geo Mix y reproducción automática

- Acción **Geo Mix** y tarjeta visible antes de los videos relacionados.
- Cola de hasta 20 contenidos relacionados, sin duplicar el video actual.
- Geo Mix activa la reproducción continua y desactiva la repetición del mismo video.
- Interruptor **Reproducción automática ON/OFF** dentro de la página del reproductor.
- El botón Siguiente funciona con la misma cola en el reproductor y en la notificación.

## Ventanas de reproducción

- Minirreproductor dentro de Geo Videos.
- Picture-in-Picture sobre otras aplicaciones durante la reproducción de video, en Android compatible.
- El modo música continúa en segundo plano sin forzar una ventana flotante de video.
- Pantalla completa horizontal estable y controles mediante iconos.

## Historial

- Buscador visible permanentemente.
- Filtros Todo, Videos, Shorts, Podcasts y Música.
- Orden por reproducción más reciente, agrupación por fecha, progreso y eliminación individual.
- El historial se llena con lo reproducido dentro de Geo Videos; no importa automáticamente el historial privado de YouTube.

## Funciones conservadas

- Calidades reales disponibles y preferencia persistente.
- Descargas HD con tamaño aproximado y unión de video y audio en un único archivo final.
- Principal actualizable, feed variado de Shorts, canales completos, reproducción en segundo plano y cola multimedia.
- No se modificaron el acceso de Google, la sesión guardada, la huella, el paquete ni la firma de la aplicación respecto de V40.

## Compilación

El workflow de GitHub Actions compila la variante `release`.

No fue posible ejecutar la compilación Android completa en este entorno porque no pudo resolverse `services.gradle.org` para descargar Gradle 8.13. La validación definitiva debe realizarse en GitHub Actions y en un teléfono Android.
