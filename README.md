# Geo Videos V20 — núcleo de fluidez

Esta versión se concentra en rendimiento y estabilidad del reproductor. No añade secciones nuevas.

## Cambios principales

- El reproductor grande se puede arrastrar hacia abajo para minimizarlo.
- El movimiento sigue el dedo y el gesto queda limitado al área del video.
- Los controles personalizados pesados fueron reemplazados por los controles nativos de Media3 `PlayerView`.
- Los controles aparecen al tocar y se ocultan automáticamente.
- Principal, Shorts y Colección dejan de permanecer dibujados detrás del reproductor grande.
- El estado de desplazamiento se conserva mediante `SaveableStateHolder` al minimizar.
- Abrir o minimizar el mismo video ya no vuelve a ejecutar innecesariamente la preparación del stream.
- La barra de progreso personalizada que consultaba el reproductor cada 500 ms fue retirada.
- La precarga del siguiente Short se retrasa y se cancela al seguir deslizando.
- Se redujo la cantidad máxima de videos mantenidos en memoria.
- Los videos relacionados también tienen un límite de memoria.
- Se mantiene `SurfaceView`, una sola sesión Media3 y el servicio de reproducción.

## Archivos protegidos

No se modificaron:

- `MainActivity.kt`
- `AndroidManifest.xml`
- `app/geovideos-dev.jks`
- paquete `com.geovideos.app`
- configuración OAuth existente

## Compilación

El workflow de GitHub compila la variante `release`.

La compilación completa no pudo ejecutarse en este entorno porque Gradle y el Android SDK no están disponibles sin acceso a los repositorios externos. La validación final debe realizarse con GitHub Actions y luego en el teléfono.
