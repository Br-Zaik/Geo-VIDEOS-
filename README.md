# Geo Videos V21 — reconstrucción ligera

Esta versión reemplaza en tiempo de ejecución las tres pantallas que más carga producían: Principal, Shorts y Reproductor.

## Cambios de rendimiento

- Principal nuevo y más simple, con un máximo visible controlado y carga manual por páginas.
- Carrusel de Shorts limitado y miniaturas solicitadas al tamaño real.
- Shorts con una sola superficie de video activa; las páginas vecinas muestran solo miniatura.
- Sin barra de progreso Compose en Shorts.
- Sin precarga automática de múltiples transmisiones.
- Reproductor nuevo y más pequeño, basado en controles nativos de Media3.
- La pantalla anterior deja de dibujarse detrás del reproductor expandido.
- Arrastrar el área del video hacia abajo minimiza el reproductor.
- Relacionados limitados a 12 elementos visibles y carga manual.
- El estado del progreso se consulta con menor frecuencia.
- Máximo de elementos remotos en memoria reducido de 50 a 24.
- Lotes iniciales de suscripciones reducidos para no saturar red, CPU ni memoria.
- Código antiguo de Home, Shorts y reproductor retirado de `GeoVideosApp.kt` para reducir el tamaño de la clase principal.

## Funciones conservadas

- inicio de sesión y OAuth
- paquete y firma
- Media3 en servicio
- Principal, Shorts, Buscar, Colección y Cuenta
- Me gusta local, No me gusta local, Compartir y Ver después
- videos relacionados

## Compilación

El workflow de GitHub compila la variante `release`.

No fue posible ejecutar una compilación Android completa en este entorno porque no puede descargar Gradle ni dependencias externas. La validación definitiva debe realizarse en GitHub Actions y en el teléfono.
