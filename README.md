# Geo Videos V15 — reproductor y feed estabilizados

Proyecto Android en Kotlin y Jetpack Compose.

## Cambios principales

- Al salir de pantalla completa, la aplicación restaura la orientación vertical, las barras del sistema y el diseño normal.
- El reproductor queda fijo arriba mientras se consulta la información, descripción y videos relacionados.
- Controles superiores integrados: minimizar, calidad, velocidad, ajustes, siguiente y cerrar.
- Modos de pantalla **Ajustar** y **Rellenar**.
- Menú de reproducción ampliado con autoplay, repetición, silencio, temporizador, ventana flotante y apertura externa.
- El reproductor puede minimizarse arrastrándolo hacia abajo; el minirreproductor puede abrirse deslizando hacia arriba o cerrarse deslizando hacia abajo.
- Los videos relacionados se muestran con un diseño tipo YouTube: miniatura 16:9, duración, canal, fecha y menú de opciones.
- Los primeros relacionados se consultan al abrir el video y se combinan con la caché local; ya no se espera a llegar al final de la pantalla.
- La actualización de Principal usa un solo indicador al deslizar hacia abajo.
- Se eliminaron los dos indicadores duplicados del encabezado.
- Cada actualización consulta un grupo distinto de canales suscritos y coloca los videos realmente nuevos al principio.
- Si no existen videos nuevos, conserva el contenido y lo informa mediante un mensaje; no simula una actualización.
- Se eliminó la precarga de transmisiones mientras se navega por Principal, Buscar o Colección.
- En Shorts y relacionados solo se precarga el siguiente video, con retraso, para reducir consumo y trabas.
- El estado del reproductor se consulta con menor frecuencia para reducir recomposiciones.
- Versión de aplicación: `15.0.0` (`versionCode 17`).

## Inicio de sesión protegido

No se modificaron:

- `MainActivity.kt`
- `AndroidManifest.xml`
- paquete `com.geovideos.app`
- llave `app/geovideos-dev.jks`
- flujo OAuth ni permisos de Google

Los hashes de esos tres archivos fueron comparados antes y después y permanecen idénticos.

## Compilación

1. Extrae el ZIP.
2. Reemplaza los archivos del proyecto en GitHub; no subas el ZIP cerrado dentro del repositorio.
3. Conserva la carpeta `.github/workflows` que ya existe en tu repositorio.
4. Ejecuta **Compilar APK**.

El entorno de trabajo no pudo descargar Gradle porque no tiene resolución de red. Se verificó el balance estructural de los archivos Kotlin modificados y el compilador de Kotlin no detectó errores de sintaxis, pero la validación definitiva sigue siendo GitHub Actions.

## Limitación del feed

La API pública de YouTube no expone el algoritmo privado exacto de recomendaciones. Geo Videos construye el feed con suscripciones, actividad, Me gusta, historial y contenido popular, y rota los canales consultados al actualizar para ampliar resultados sin introducir búsquedas aleatorias.

## Licencias

El reproductor propio utiliza NewPipe Extractor bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING` antes de publicar la APK.
