# Geo Videos V5

Aplicación Android con interfaz propia para consultar y reproducir contenido autorizado mediante YouTube Data API v3.

## Funciones incluidas

- Selector real de cuenta Google mediante Google Identity Services.
- Perfil y canal de YouTube.
- Inicio con tendencias, transmisiones en vivo y gaming.
- Búsqueda de videos.
- Clips cortos.
- Suscripciones, playlists y videos con Me gusta.
- Historial y Ver después guardados localmente.
- Campana de actividad disponible mediante la API pública.
- Reproductor incrustado de YouTube.
- Descarga de enlaces directos a archivos autorizados mediante DownloadManager.

## Configuración obligatoria de Google

Antes de que el inicio de sesión funcione:

1. Crea un proyecto en Google Cloud.
2. Activa `YouTube Data API v3`.
3. Configura la pantalla de consentimiento OAuth.
4. Crea un cliente OAuth de tipo Android con:
   - Paquete: `com.geovideos.app`
   - SHA-1: `61:39:FF:D0:D5:6B:DC:06:FA:13:AD:3D:7A:88:93:9F:6D:4A:52:7F`
5. Agrega tu cuenta como usuario de prueba mientras la aplicación esté en modo Testing.

La APK se firma con una clave de desarrollo incluida para mantener el mismo SHA-1 en GitHub Actions. No uses esta clave pública para una publicación comercial. Para producción se debe crear una firma privada y registrar su nueva huella SHA-1.

## Límites reales

La API pública no entrega el historial oficial completo, `Ver más tarde` ni toda la bandeja privada de notificaciones. Esas partes se gestionan localmente o con la actividad que la API sí expone.

Geo Videos no elimina anuncios ni extrae archivos de videos de YouTube. La descarga incluida funciona con enlaces directos autorizados.
