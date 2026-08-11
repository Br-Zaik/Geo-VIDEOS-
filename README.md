# Geo Videos V50.7

Revision centrada en Principal, busqueda YouTube, historial y mini reproductor, manteniendo el acceso Google existente.

## Principal

- Shorts en la franja superior y videos normales debajo.
- El feed de Para ti se construye desde canales suscritos y usa historial/Me gusta como señales de orden, no como relleno directo.
- Tendencias no sustituye al feed de la cuenta.
- Pull-to-refresh con un solo indicador; videos y Shorts se actualizan en paralelo y la sincronizacion secundaria continua despues.

## Buscar

- Busqueda general de YouTube con videos, canales y listas de reproduccion.
- Paginacion de resultados.
- Acceso directo a canales y playlists desde los resultados.

## Historial

- Registra inmediatamente las reproducciones hechas dentro de Geo Videos.
- Guarda progreso y permite reanudar.
- Interfaz compacta con busqueda opcional.
- No se presenta como historial privado oficial de YouTube.

## Reproductor

- Mini reproductor en barra compacta sobre la navegacion inferior con video, titulo, canal, play/pausa y cerrar.
- Tocar la barra restaura el reproductor grande conservando la posicion.
- TextureView y transicion sin escalado agresivo para evitar que el video atraviese otras capas.

## Cuenta YouTube

- Lectura de perfil, suscripciones, playlists, Me gusta y subidos con la cuenta conectada.
- Las acciones de escritura siguen pidiendo el permiso adicional correspondiente cuando hace falta.

## Compilacion

El proyecto conserva el workflow de GitHub Actions. En este entorno no fue posible descargar Gradle 8.13 desde services.gradle.org, por lo que la compilacion Android final debe validarse en Actions.
