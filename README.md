# Geo Videos V12 — reproductor propio

Proyecto Android en Kotlin y Jetpack Compose.

## Cambios principales de V12

- Reemplazo del reproductor IFrame de YouTube por un reproductor propio basado en AndroidX Media3/ExoPlayer.
- Un solo reproductor administrado por `PlaybackService`, compartido por la pantalla completa, el minirreproductor y los controles del sistema.
- Resolución de transmisiones públicas mediante NewPipe Extractor.
- Caché de reproducción de 256 MB y búfer ajustado para reducir cortes.
- El progreso del video ya no recompone todo el feed cada 750 ms.
- Protección contra respuestas atrasadas: si se abre otro video mientras el anterior se resuelve, el anterior ya no puede reemplazarlo.
- Actualización estable: conserva el feed previo y coloca primero el contenido nuevo, sin búsquedas aleatorias.
- Paginación independiente para Principal, En vivo, Juegos, Música, Shorts y Mis videos.
- Sección `Colección > Mis videos`, obtenida de la playlist de subidos del canal autenticado.
- Caché local de avatares de canales y miniaturas ajustadas a la pantalla.

## Inicio de sesión

No se modificaron `MainActivity.kt`, el paquete `com.geovideos.app` ni la llave de firma incluida en el proyecto. La autorización continúa usando Google Identity Services y los permisos existentes de solo lectura.

## Compilación

1. Abrir la carpeta raíz en Android Studio.
2. Usar JDK 17.
3. Permitir que Gradle descargue las dependencias desde Google, Maven Central y JitPack.
4. Compilar `app` en modo Debug o Release.

Requisitos principales:

- Android SDK 35.
- Android Gradle Plugin 8.13.2.
- Kotlin 2.3.21.
- Conexión a Internet en la primera sincronización de Gradle.

## Limitación importante

El reproductor propio no usa el IFrame oficial de YouTube. La obtención de transmisiones depende de mecanismos públicos analizados por NewPipe Extractor y puede dejar de funcionar temporalmente cuando YouTube cambie su entrega. Algunos videos con restricciones de edad, país, membresía, pago o DRM pueden requerir abrirse externamente.

## Licencias

Este proyecto incluye NewPipe Extractor, distribuido bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING` antes de publicar una APK.
