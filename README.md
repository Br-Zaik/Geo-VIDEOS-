# Geo Videos V14.1 — final de reproducción normal

Proyecto Android en Kotlin y Jetpack Compose.

## Cambios principales

- Shorts rediseñados como un visor vertical de una página por video, con deslizamiento tipo TikTok.
- El Short visible se reproduce dentro del feed en formato vertical, sin abrir primero la pantalla normal.
- Precarga de los dos Shorts siguientes y cancelación de la transmisión anterior al deslizar.
- Los Shorts se forman primero con publicaciones cortas de suscripciones, Me gusta e historial; después se completa con una búsqueda basada en esos intereses.
- Se eliminó la consulta genérica fija `shorts español`, que era la causa principal del contenido ajeno al usuario.
- El visor de Shorts repite el clip visible y oculta el encabezado principal para aprovechar más pantalla.
- Al terminar un video normal, la reproducción automática pasa al primer relacionado disponible.
- Al terminar un video ya no aparece una pantalla adicional con botones de **Repetir** y **Siguiente**.
- Con reproducción automática encendida pasa directamente al siguiente relacionado; con repetición encendida vuelve a empezar el mismo video; con ambas apagadas termina normalmente.
- Se añadió un botón visible para activar o desactivar la repetición del video actual.
- Suscripciones aparece al comienzo de Colección en una tarjeta destacada con todos los canales autorizados y sus publicaciones recientes.
- Se redujeron recomposiciones grandes del reproductor: la pantalla completa ya no se reconstruye por cada actualización de posición.
- El estado del reproductor se actualiza con menor frecuencia y la barra de progreso de Shorts se aísla del resto del visor.
- Versión de aplicación: `14.1.0` (`versionCode 16`).

## Inicio de sesión protegido

No se modificaron:

- `MainActivity.kt`
- `AndroidManifest.xml`
- paquete `com.geovideos.app`
- llave `app/geovideos-dev.jks`
- flujo OAuth ni permisos de Google

Los hashes de esos tres archivos fueron comparados antes y después de los cambios y permanecen idénticos.

## Compilación

1. Extrae el ZIP.
2. Reemplaza los archivos del proyecto en GitHub; no subas el ZIP cerrado dentro del repositorio.
3. Conserva la carpeta `.github/workflows` que ya existe en tu repositorio.
4. Ejecuta **Compilar APK**.

Este entorno no contiene Android SDK ni las dependencias Gradle descargadas. Se verificó balance de sintaxis en todos los archivos Kotlin y no se detectaron errores de estructura, pero la comprobación definitiva sigue siendo la compilación de GitHub Actions.

## Limitación real del feed de Shorts

La API pública de YouTube no expone el algoritmo privado exacto de YouTube Shorts. Geo Videos crea un feed aproximado usando suscripciones, historial, Me gusta y términos frecuentes. Debe ser bastante más relevante que la búsqueda genérica anterior, pero no será idéntico al feed de la aplicación oficial.

## Licencias

El reproductor propio utiliza NewPipe Extractor bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING` antes de publicar la APK.
