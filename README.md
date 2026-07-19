# Geo Videos V11

Versión enfocada en corregir la carga continua, la actualización real, los iconos de canal y la fluidez del feed, manteniendo la autenticación existente.

## Cambios aplicados

- Paginación independiente para **Principal, En vivo, Juegos y Música**. Una categoría ya no bloquea a las demás.
- Paginación automática añadida a **Shorts**.
- La actualización compara IDs y coloca primero el contenido realmente nuevo; ya no simula cambios rotando los mismos videos.
- Mensaje con la cantidad de videos nuevos, o aviso claro cuando la API devuelve el mismo contenido.
- Caché persistente de iconos de canales para conservarlos cuando una consulta temporal falla.
- Consulta de iconos por lotes de hasta 50 canales, sin descartar silenciosamente los canales posteriores.
- Miniaturas reducidas de 1280×720 a 640×360 para disminuir decodificación, memoria y trabas al desplazarse.
- Fallback visual cuando falla una miniatura o un avatar.
- Indicadores de carga y fin de resultados independientes por sección.
- `versionCode 11` y `versionName 11.0.0`.

## Inicio de sesión preservado

No se modificaron `MainActivity.kt`, el manifiesto, la llave de firma, el paquete, los permisos OAuth ni el flujo de cuenta de Google. La única modificación en `app/build.gradle.kts` es el número de versión.
