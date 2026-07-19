# Validación técnica — Geo Susu v171

## Alcance

Se modificó únicamente `GoogleInternalListenService.kt` y la numeración visible de la versión.
No se cambiaron JSON, AUTO, NET, APIs, prueba de APIs, Vosk, velocidades ni la interfaz.

## Correcciones verificadas

- Cada ciclo de Google crea un `SpeechRecognizer` nuevo.
- El reconocedor anterior se cancela y destruye antes de abrir el siguiente.
- Cada sesión usa un identificador único; los callbacks tardíos se descartan.
- La sesión tiene un límite duro de 18 segundos para impedir estados congelados indefinidos.
- Un supervisor revisa cada 5 segundos que exista una escucha activa o un reinicio programado.
- El lector de voz tiene monitor de estado y watchdog independiente.
- Tras finalizar una respuesta se programa la nueva escucha a 350 ms.
- Existe una guardia adicional de 2.8 segundos si Android no reabre el micrófono.
- Las actualizaciones del medidor se limitan a 4 por segundo para evitar saturar el hilo principal.
- WakeLock configurado por 8 horas para cubrir una prueba continua de 6 horas.
- Se conservan los tiempos de Google interno: 4200 / 2600 / 2600 ms.
- No se añadió `AudioRecord` ni un detector previo de voz.

## Validaciones realizadas

- Balance léxico de llaves, paréntesis y corchetes: correcto.
- Compilación aislada de `GoogleInternalListenService.kt` con stubs tipados de Android y clases del proyecto: correcta.
- XML del manifiesto y recursos: parseo correcto.
- Archivos JSON incluidos: sintaxis correcta.
- Versión: `versionCode 171`, `versionName 17.1.0`.
- Integridad del ZIP: comprobada con `unzip -t`.

## Límite de la validación

El entorno no dispone del Android SDK/Gradle completo ni del teléfono, el micrófono USB-C y Speech Services reales. La estabilidad física durante seis horas debe confirmarse en el dispositivo. La versión incorpora recuperación automática para que un fallo de Google no deje la interfaz congelada indefinidamente.
