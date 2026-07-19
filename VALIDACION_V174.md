# Validacion Geo Susu v174

## Alcance del cambio

1. Desplazamiento vertical dentro de los cuadros `tvHeard` y `tvAnswer`.
2. Eliminacion del limite visual de dos lineas en la pregunta.
3. Respaldo automatico del texto parcial cuando Google no entrega resultado final.
4. Margen mayor para lectura de preguntas con alternativas.

## Comprobaciones realizadas

- `activity_main.xml` es XML valido.
- `AndroidManifest.xml` es XML valido.
- El JSON de ejemplo conserva sintaxis valida.
- `versionCode` es 174 y `versionName` es 17.4.0.
- Los archivos Kotlin modificados tienen delimitadores balanceados.
- El analizador de Kotlin no reporto errores de sintaxis; las referencias Android no pueden resolverse aqui porque este entorno no incluye Android SDK.
- Se confirmo que el texto parcial se cancela cuando llega un resultado final.
- Se confirmo que un error `NO_MATCH` o `SPEECH_TIMEOUT` reutiliza el parcial disponible.
- Se confirmo que el temporizador normal es de 3 segundos y el de alternativas de 5.5 segundos.
- Se confirmo que la sesion larga de alternativas puede extenderse hasta 60 segundos.

## Limite de la validacion

No fue posible instalar ni probar el APK en un telefono real desde este entorno. Debe comprobarse en el dispositivo que el gesto de desplazamiento y el comportamiento de Google Speech coincidan con la version instalada del servicio de voz.
