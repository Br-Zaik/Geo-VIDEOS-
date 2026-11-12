# Geo Videos

Proyecto Android de Geo Videos, separado de Zeta Motion y de otras aplicaciones.

## Descargar APK

En [Actions](https://github.com/Br-Zaik/Geo-VIDEOS-/actions), abre una compilacion correcta y descarga su artefacto ZIP. Al abrir ese ZIP encontraras directamente **Geo-Videos.apk**, sin carpetas internas.

Las APK compiladas desde ZIP nuevos tambien se guardan en [Releases](https://github.com/Br-Zaik/Geo-VIDEOS-/releases), con el nombre de la version. Los artefactos de Actions pueden caducar; los Releases se conservan hasta que los elimines.

## Subir versiones

1. Sube un archivo con nombre **Geo-VIDEOS-vXX.zip** a la raiz del repositorio (o a `incoming/`).
2. El workflow comprueba que sea un proyecto Android con `applicationId = "com.geovideos.app"` antes de reemplazar codigo.
3. Compila la APK y la publica dentro de un ZIP descargable desde Actions.
4. Conserva el **ZIP original con su nombre**, para poder revisar y comparar versiones antiguas. Ya no crea el archivo generico `Geo-VIDEOS-actual.zip`.

Si borras un ZIP antiguo, el workflow no intentara compilar por ese borrado.

**Nota sobre los titulos de Actions:** GitHub muestra el mensaje del commit (por ejemplo, «Add files via upload»). Al subir el ZIP, cambia ese mensaje por el nombre del archivo para que tambien aparezca la version en la lista de ejecuciones.

## Codigo

- `app/`: codigo y recursos Android
- `gradle/` y archivos `*.gradle.kts`: compilacion
- `.github/workflows/zip-to-apk.yml`: importar proyecto y compilar APK

La firma debug incluida es solo para pruebas, no para publicar en Google Play.
