# Geo Videos V6

Aplicación Android con interfaz propia para consultar y reproducir contenido autorizado mediante YouTube Data API v3.

## Mejoras de esta versión

- Reproductor de YouTube reconstruido con IFrame Player API desde un origen HTTPS interno identificado.
- Detección de errores del reproductor y botón para abrir el video en YouTube cuando no admite inserción.
- Pantalla completa del reproductor incrustado y ventana flotante de Android cuando el dispositivo lo permite.
- Controles de reproducción: reproducir/pausar, silencio, repetición, velocidad y temporizador.
- Historial con posición de reproducción para continuar donde se dejó el video.
- Flecha de actualización real con bloqueo de toques repetidos y conservación de datos si falla la red.
- Preferencias persistentes de reproducción automática, ahorro de datos y avisos.
- Descargas directas con opción de solo Wi-Fi, progreso, estado, eliminación y acceso al gestor de descargas del teléfono.
- Indicadores de progreso en los videos empezados.

## Funciones principales

- Selector real de cuenta Google mediante Google Identity Services.
- Perfil y canal de YouTube.
- Inicio con tendencias, transmisiones en vivo y gaming.
- Búsqueda de videos y Shorts.
- Suscripciones, playlists y videos con Me gusta.
- Historial y Ver después guardados localmente.
- Campana de actividad disponible mediante la API pública.
- Reproductor incrustado de YouTube.
- Descarga de enlaces directos a archivos autorizados mediante el gestor de descargas de Android.

## Configuración de Google ya realizada

El cliente OAuth de Android debe conservar estos datos:

- Paquete: `com.geovideos.app`
- SHA-1: `61:39:FF:D0:D5:6B:DC:06:FA:13:AD:3D:7A:88:93:9F:6D:4A:52:7F`

La APK mantiene la misma clave de desarrollo para que el inicio de sesión configurado continúe funcionando. No cambies el paquete ni la firma sin registrar el nuevo SHA-1 en Google Cloud.

## Límites reales

La API pública no entrega el historial oficial completo, `Ver más tarde` ni toda la bandeja privada de notificaciones. Esas partes se gestionan localmente o con la actividad que la API sí expone.

Geo Videos no elimina anuncios ni extrae archivos de videos de YouTube. El botón de descarga para YouTube muestra la limitación; las descargas reales funcionan con URLs directas de archivos propios o autorizados.

La clave de desarrollo incluida conserva el SHA-1 actual, pero no debe utilizarse para una publicación comercial. Antes de distribuir la app públicamente se debe migrar a una firma privada guardada en GitHub Secrets y registrar su nueva huella.
