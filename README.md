# Geo Videos V17 — navegación modular y biblioteca compacta

Proyecto Android en Kotlin y Jetpack Compose.

## Cambios principales

- Inicio reorganizado por módulos: **Mi mix**, carrusel horizontal de Shorts y feed normal.
- Los Shorts se abren en la pestaña vertical 9:16 y no en la página normal del reproductor.
- Pantalla Shorts con un video por página, precarga limitada al siguiente y acciones laterales.
- Colección separada en pantallas independientes: Historial, Ver después, Videos que me gustan, Mis videos y Suscripciones.
- “Videos que me gustan” usa una lista compacta con miniatura 16:9, duración, progreso, canal, reproducción aleatoria y carga progresiva.
- Suscripciones cuenta con una lista dedicada de canales y acceso directo al contenido de cada canal.
- Me gusta y No me gusta responden localmente sin ampliar los permisos OAuth actuales.
- El botón Canal y las acciones inferiores quedan fuera de la capa de gestos del reproductor y responden al primer toque.
- Relacionados en filas compactas tipo YouTube y filtrados para excluir Shorts en videos normales y reducir resultados de escritura/idioma no relacionado.
- Minirreproductor compacto con miniatura, título, canal, reproducir/pausar, cerrar y barra fina de progreso.
- El reproductor usa una superficie `TextureView` persistente para reducir pantallas negras al cambiar entre vista normal y pantalla completa.
- Miniaturas sin animación de fundido y con tamaños limitados para reducir carga durante el desplazamiento.
- Versión de aplicación: `17.0.0` (`versionCode 19`).

## Inicio de sesión protegido

No se modificaron:

- `MainActivity.kt`
- `AndroidManifest.xml`
- paquete `com.geovideos.app`
- llave `app/geovideos-dev.jks`
- flujo OAuth ni permisos de Google

Los hashes de esos archivos se comparan antes de entregar el proyecto.

## Importante sobre Me gusta

El acceso actual de Google es de solo lectura. Para no arriesgar el inicio de sesión, los nuevos Me gusta/No me gusta se guardan dentro de Geo Videos y se muestran en la biblioteca local. Los videos que ya estaban marcados en YouTube se siguen leyendo desde la lista oficial, pero esta versión no modifica la cuenta de YouTube.

## Compilación

1. Extrae el ZIP.
2. Reemplaza los archivos del proyecto en GitHub; no subas el ZIP cerrado dentro del repositorio.
3. Conserva la carpeta `.github/workflows` que ya existe en tu repositorio.
4. Ejecuta **Compilar APK**.

Este entorno no dispone del Android SDK ni pudo descargar Gradle, por lo que la compilación definitiva debe ejecutarse en GitHub Actions. Se realizaron comprobaciones estructurales de Kotlin, XML, rutas, versión y archivos protegidos.

## Licencias

El reproductor propio utiliza NewPipe Extractor bajo GNU GPL v3. Consulta `THIRD_PARTY_NOTICES.md` y `COPYING` antes de publicar la APK.
