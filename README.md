# Geo Videos V9

Versión reconstruida para corregir los problemas visibles de V6 y ofrecer una navegación más cercana a una aplicación de videos real, conservando una identidad propia.

## Cambios principales

- Barra inferior: **Principal, Shorts, Buscar, Colección y Cuenta**.
- Inicio compacto con pestañas **Para ti, En vivo, Juegos y Música**.
- El apartado **Para ti** se arma con publicaciones recientes de canales suscritos, actividad disponible, videos con Me gusta, historial local y contenido popular de respaldo.
- Actualización mediante la flecha superior y deslizando hacia abajo.
- Sesión persistente: al reabrir la app muestra los datos guardados y trata de renovar el acceso silenciosamente; no abre Google de forma automática cuando se necesita una confirmación.
- Botón **Renovar acceso** en Cuenta para cuando Google solicite autorización nuevamente.
- Reproductor de YouTube reemplazado por un componente Android mantenido, con video, audio y controles oficiales integrados.
- Pantalla completa, velocidad, silencio, repetición, temporizador y ventana flotante.
- Minirreproductor sobre la barra inferior al minimizar un video.
- Historial y posición de reproducción guardados para continuar donde se dejó.
- Búsqueda, suscripciones, listas, videos con Me gusta, campana de actividad y perfil de Google.
- Descargas reales para enlaces directos propios o autorizados, con registro y gestión desde Colección.

## Configuración de Google

La aplicación conserva los datos ya registrados:

- Paquete: `com.geovideos.app`
- SHA-1: `61:39:FF:D0:D5:6B:DC:06:FA:13:AD:3D:7A:88:93:9F:6D:4A:52:7F`

No hace falta repetir la configuración de Google Cloud mientras no cambien el paquete ni la firma.

## Límites

La API pública de YouTube no entrega el feed privado exacto de Inicio, el historial oficial completo, Ver más tarde ni toda la bandeja privada de notificaciones. Geo Videos crea un feed personalizado aproximado usando los datos que la cuenta sí autoriza y el historial guardado por la propia aplicación.

Geo Videos no extrae archivos ni elimina anuncios de YouTube. Las descargas se limitan a enlaces directos de contenido propio o autorizado.

## Firma

La clave de desarrollo incluida mantiene el SHA-1 actual para las pruebas. No debe utilizarse para una publicación comercial; para distribución pública se debe migrar a una firma privada guardada en GitHub Secrets.


## V8

- Corrige el error de compilacion por uso nullable de `selectedVideo` en el reproductor expandido.
- Conserva el mismo paquete y la misma firma de desarrollo.


## V9

- Agrega la extension segura `Context.findActivity()` usada por pantalla completa y ventana flotante.
- Corrige el error de compilacion `Unresolved reference: findActivity`.
- Mantiene el mismo paquete y la misma firma de desarrollo para conservar el acceso de Google.
