# Geo Videos

Proyecto Android nativo en Kotlin y Jetpack Compose.

## Funciones incluidas

- Nombre e identidad visual original: **Geo Videos**.
- Creacion de cuenta e inicio de sesion local.
- Contrasena guardada como hash con sal aleatoria; no se almacena en texto plano.
- Sesion persistente en el dispositivo.
- Inicio, buscador, biblioteca, favoritos, historial y perfil.
- Reproduccion con Jetpack Media3 / ExoPlayer.
- Selector de videos locales mediante el explorador de Android.
- Reproduccion de enlaces directos `http` o `https` autorizados.
- Tres videos publicos de muestra para probar el reproductor.
- Eliminacion de videos agregados y borrado completo de cuenta/datos.

## Limite importante

La cuenta de esta version es local y funciona solo en el telefono donde se instala. No es inicio de sesion con Google, YouTube ni Firebase. Un inicio de sesion real entre varios dispositivos necesita un servidor o Firebase y credenciales propias del proyecto.

La app no bloquea anuncios, no extrae videos de YouTube y no descarga contenido protegido. Los enlaces admitidos deben apuntar directamente a archivos o transmisiones que el usuario tenga derecho a reproducir.

## Compilar

```bash
./gradlew assembleDebug
```

La APK queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Requisitos

- JDK 17
- Android SDK 35
- Gradle 8.13 (incluido mediante wrapper)
- Min SDK 23
