# Geo Videos

Proyecto Android para explorar videos y Shorts, buscar contenido, administrar colecciones e historial local y reproducir videos con un reproductor integrado.

## Descargar

- **Código fuente en un único archivo:** [Geo-VIDEOS-actual.zip](./Geo-VIDEOS-actual.zip). GitHub Actions actualiza este archivo después de una compilación exitosa. Si todavía no aparece, utiliza **Code → Download ZIP**.
- **APK para instalar:** abre la pestaña **Actions**, selecciona la última ejecución correcta de «Geo Videos - ZIP a APK» y descarga el archivo «Geo-Videos-APK-...» en **Artifacts** (disponible durante 30 días).

## Actualizar el proyecto

1. Sube **un solo ZIP** nuevo a la raíz del repositorio o a la carpeta `incoming/`.
2. GitHub Actions detectará ese ZIP, importará el proyecto Android, compilará la APK y actualizará `Geo-VIDEOS-actual.zip`.
3. Tras finalizar correctamente, GitHub conservará el código y **un solo ZIP del proyecto**. Los archivos antiguos de validación y los README duplicados no se incorporarán.

También puedes ejecutar el workflow manualmente desde **Actions → Geo Videos - ZIP a APK → Run workflow** para compilar el código que ya esté en el repositorio.

La versión de la app se define en [app/build.gradle.kts](./app/build.gradle.kts). Este README no necesita cambiar de nombre con cada versión.

## Estructura

- `app/`: código fuente y recursos de Android.
- `gradle/`, `build.gradle.kts` y `settings.gradle.kts`: configuración de compilación.
- `.github/workflows/zip-to-apk.yml`: importación de ZIP y generación de APK.
- `COPYING` y `THIRD_PARTY_NOTICES.md`: licencias y avisos de terceros.

**Nota:** la APK actual utiliza una firma de desarrollo. No utilices esa clave pública como firma definitiva para publicar en Google Play.
