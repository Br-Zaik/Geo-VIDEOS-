# Validacion tecnica - Geo Susu v172

## Alcance

Se corrigio unicamente el manejo de idioma de Google interno y la numeracion de version.
No se cambiaron JSON, AUTO, NET, APIs, prueba de APIs, Vosk, tiempos de escucha, voz, interfaz ni rutas de respuesta.

## Correccion aplicada

- El error 12 (`ERROR_LANGUAGE_NOT_SUPPORTED`) y el error 13 (`ERROR_LANGUAGE_UNAVAILABLE`) tienen manejo propio.
- Si Google rechaza un idioma, la siguiente sesion usa otra variante espanola en vez de repetir el mismo idioma.
- Orden de respaldo: idioma previamente funcional, `es-PE`, `es-419`, `es-ES`, idioma espanol del telefono y `es`.
- Las etiquetas duplicadas se eliminan de la lista.
- El idioma que llega correctamente a `onReadyForSpeech` se guarda para las sesiones posteriores.
- Si todas las variantes son rechazadas, el ciclo se limpia y reintenta despues de una pausa de 5 segundos, evitando un bucle rapido.

## Verificaciones

- `versionCode 172` y `versionName 17.2.0`.
- Los tiempos de Google interno siguen en 4200/2600/2600 ms.
- El resto del bloque de errores conserva sus tiempos y comportamiento anteriores.
- No se agrego `AudioRecord` ni se modifico el detector de coincidencias JSON.
- Kotlin fue analizado por el compilador: no se detectaron errores de sintaxis; los errores restantes de la prueba aislada corresponden a la ausencia del Android SDK y clases Android en este entorno.
- XML y JSON incluidos tienen sintaxis valida.
- Integridad del ZIP verificada con `unzip -t`.

## Limite de la validacion

La respuesta real de Speech Services y la disponibilidad de cada etiqueta de idioma dependen del telefono. La correccion evita que el error 12/13 repita indefinidamente `es-PE` y selecciona automaticamente una variante compatible.
