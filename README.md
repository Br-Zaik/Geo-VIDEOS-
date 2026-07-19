# Geo Videos V13.1 — corrección de compilación

Proyecto Android en Kotlin y Jetpack Compose.

## Cambios principales de V13

- Reproductor propio basado en AndroidX Media3/ExoPlayer; no vuelve al IFrame de YouTube.
- Contenedor adaptable: los videos se reproducen sin deformarse y con barras negras cuando la proporción lo requiere.
- Pantalla completa con orientación horizontal y botón para regresar al modo normal.
- Controles de reproducción menos invasivos, con retroceso, avance, calidad, velocidad y ocultamiento automático.
- La miniatura permanece visible durante la resolución de la transmisión.
- El indicador de carga aparece únicamente cuando la espera supera 450 ms; se eliminó el mensaje grande de preparación.
- Caché temporal de enlaces resueltos durante 20 minutos.
- Precarga de los dos primeros videos del feed y de los dos primeros relacionados.
- Búfer inicial reducido para comenzar antes sin eliminar la protección contra cortes.
- Información ampliada: visualizaciones, fecha, canal, avatar y suscriptores cuando YouTube los devuelve.
- Fila desplazable de acciones: reproducir/pausar, conteo de Me gusta, Ver después, segundo plano, compartir, ventana flotante, temporizador y transmitir externamente.
- Descripción contraíble.
- Videos relacionados debajo de la información, con eliminación de duplicados y carga progresiva.
- Los relacionados no reemplazan ni modifican el feed principal.

## Funciones conservadas de V12

- Un solo reproductor administrado por `PlaybackService` para pantalla completa, minirreproductor y controles del sistema.
- Caché de reproducción de 256 MB.
- Feed estable, paginación independiente y sección `Colección > Mis videos`.
- Resolución de transmisiones públicas mediante NewPipe Extractor.

## Inicio de sesión protegido

No se modificaron `MainActivity.kt`, `AndroidManifest.xml`, el paquete `com.geovideos.app` ni la llave `geovideos-dev.jks`. La autorización conserva los permisos existentes de solo lectura.

## Compilación

1. Extraer el ZIP.
2. Subir o reemplazar los archivos del proyecto, no el ZIP como archivo dentro del repositorio.
3. Usar JDK 17 y Android SDK 35.
4. Compilar `app` en modo Debug o Release.

La compilación completa no pudo ejecutarse en el entorno donde se preparó esta versión porque allí no está instalado Android SDK. Se realizaron revisiones estáticas de sintaxis, estructura, manifiesto e integridad de autenticación; la validación definitiva es la compilación de Gradle.

## Alcance pendiente

Esta versión no añade todavía la pestaña de comentarios ni acciones de escritura sobre YouTube, como suscribirse o dar Me gusta. Añadir escritura requeriría cambiar los permisos OAuth y volver a autorizar la cuenta, por lo que se mantuvo fuera de esta versión.

## Limitación importante

El reproductor propio no usa el IFrame oficial de YouTube. La obtención de transmisiones depende de NewPipe Extractor y puede requerir actualizaciones cuando YouTube cambie su entrega. Algunos videos con restricciones de edad, país, membresía, pago o DRM pueden necesitar abrirse externamente.

## Licencias

El proyecto incluye NewPipe Extractor, distribuido bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING` antes de publicar una APK.


## Corrección V13.1

- Corrige la referencia inválida `contentScale` en el avatar del canal.
- Respeta el modo `Fit` o `Crop` solicitado por cada miniatura.
