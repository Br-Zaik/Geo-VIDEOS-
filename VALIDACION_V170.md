# Validación de Geo Susu v170

## Restauración realizada

- Base del proyecto: Geo Susu v163.
- `GoogleInternalListenService.kt` se conserva exactamente igual que en v163.
- No contiene `AudioRecord`, supervisores de sesión, watchdogs, reinicios preventivos ni retorno forzado de 3 segundos.
- El bucle vuelve a depender de los callbacks normales de Google SpeechRecognizer.

## Tiempos restaurados

- `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`: 4200 ms.
- `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`: 2600 ms.
- `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS`: 2600 ms.
- Reinicio tras ausencia de coincidencia o tiempo de voz: 600 ms.
- Reinicio cuando Google está ocupado: 900 ms.
- Reinicio tras otros errores: 1200 ms.
- Tras terminar la lectura TTS: pausa original de 350 ms y reinicio solicitado a 550 ms.

## Funciones conservadas

- Coincidencia segura de JSON.
- AUTO: JSON primero y NET como respaldo.
- Comandos especiales a NET.
- Gestión resistente y prueba individual de APIs de v163.
- Vosk, velocidad de voz e interfaz sin cambios funcionales.

## Límites de validación

- Se validó estructura, sintaxis básica de archivos, referencias de versión e integridad del ZIP.
- No se compiló el APK en este entorno porque no hay Android SDK ni Gradle instalados.
- La prueba física del micrófono y del sonido de reinicio corresponde al teléfono.
