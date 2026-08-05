# Geo Videos V43

Mejora del reproductor y la biblioteca basada en el comportamiento observado en DailyTube y YouTube, sin modificar el acceso de Google, la huella, la cuenta ni la sesión guardada.

## Reproductor

- Doble toque a la izquierda o derecha para retroceder o adelantar 10 segundos, con aviso acumulado de 10, 20, 30 segundos.
- Zoom con dos dedos de 100 % a 300 %, mostrando el porcentaje aplicado.
- Pantalla completa horizontal estable: solo se muestra el video, se ocultan las barras del sistema y se conserva el segundo al regresar a vertical.
- Engranaje con Calidad, Velocidad y Reproducción automática. No se muestra un panel grande debajo del video.
- Acción Reproducir música para usar solo audio y continuar con la pantalla apagada.
- Minirreproductor interno mientras se navega por la aplicación.
- Ventana emergente propia sobre otras aplicaciones, movible y redimensionable, con calidad, velocidad, anterior, pausa, siguiente, progreso y acceso a pantalla completa.
- Cola automática de videos relacionados para Anterior/Siguiente y notificación multimedia.
- Mi Mix se genera automáticamente a partir de los videos relacionados; no es una lista que el usuario deba construir manualmente.

## Biblioteca e historial

- Historial reciente en tarjetas y acceso a Ver todo.
- Videos que me gustan, Música, Ver más tarde, playlists y Suscripciones.
- Historial local con búsqueda, filtros Todo/Videos/Shorts/Podcasts/Música, fechas, duración, progreso y eliminación individual.
- El historial corresponde a lo visto dentro de Geo Videos; la API pública no entrega automáticamente el historial privado completo de YouTube.

## Shorts

- El descubrimiento público, tendencias e intereses generales tienen prioridad.
- Las suscripciones son solo una señal secundaria.
- Mayor variedad y menor repetición del mismo canal.

## Descargas

El flujo del descargador se conserva sin cambios. Las calidades HD continúan descargando video y audio y entregando un único archivo final cuando el origen los proporciona por separado.

## Permiso de ventana emergente

Android exige autorizar una sola vez “Mostrar sobre otras apps”. Geo Videos abre el ajuste correspondiente y, al regresar, inicia automáticamente la ventana flotante pendiente.

## Compilación

El workflow de GitHub Actions compila la variante release. En este entorno no fue posible descargar Gradle 8.13 desde services.gradle.org, por lo que la comprobación final debe realizarse en Actions y en un teléfono Android.
