# Geo Videos V19 — rendimiento y fluidez

Proyecto Android en Kotlin, Jetpack Compose y Media3.

## Objetivo de esta versión

Esta versión no añade funciones grandes. Se concentra en reducir tirones, recomposiciones y consumo innecesario durante Principal, Shorts, minirreproductor y pantalla de reproducción.

## Cambios de rendimiento

- Reproductor cambiado de `TextureView` a `SurfaceView` para evitar la copia adicional de cada fotograma.
- Estado del reproductor separado en:
  - estado principal: reproducir, pausar, resolver, error y tamaño del video;
  - progreso: posición, duración y búfer.
- La barra de progreso ya no recompone toda la página del video.
- El progreso del minirreproductor se actualiza en un componente aislado.
- En Shorts solo la página activa mantiene el `PlayerView`; las demás muestran miniatura.
- El paginador de Shorts no conserva páginas extra fuera de pantalla.
- Precarga limitada únicamente al siguiente Short.
- Se eliminó la precarga automática de videos relacionados.
- Miniaturas del carrusel de Shorts reducidas al tamaño real de visualización.
- Cantidad máxima de elementos en memoria reducida de 180 a 80.
- Carga inicial de canales suscritos reducida para no saturar red, CPU y memoria.
- Búfer de Media3 ajustado para iniciar antes y mantener menos datos en memoria.
- Modelos visuales marcados como inmutables para ayudar a Compose a omitir recomposiciones.
- Errores técnicos de red se convierten en mensajes breves con opción de reintentar.

## Interfaz

- Eliminado `Mi mix`.
- Principal usa `Todos / En vivo / Juegos / Música`.
- Carrusel de Shorts limitado a ocho tarjetas visibles/cargadas.
- Shorts conserva acciones funcionales de Me gusta, No me gusta, Compartir, Abrir y Guardar.

## APK recomendada

Se incluye `.github/workflows/build-apk.yml`, que compila la variante `release`. La variante release debe usarse para evaluar fluidez; la variante debug tiene sobrecarga adicional de desarrollo.

## Inicio de sesión protegido

No se modificaron:

- `MainActivity.kt`
- `AndroidManifest.xml`
- paquete `com.geovideos.app`
- llave `app/geovideos-dev.jks`
- flujo OAuth ni permisos de Google

## Compilación

1. Extrae el ZIP.
2. Reemplaza el contenido del repositorio.
3. En GitHub Actions ejecuta **Compilar APK fluida**.
4. Descarga el artefacto **GeoVideos-release**.

Este entorno no pudo descargar Gradle, por lo que la compilación Android definitiva debe ejecutarse en GitHub Actions.

## Licencias

El reproductor utiliza NewPipe Extractor bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING`.
