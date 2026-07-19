# Validación Geo Susu v175

## Alcance

Se modificó únicamente `GoogleInternalListenService.kt`, el número de versión y la documentación. No se cambiaron JSON, AUTO, NET, APIs, Vosk, TTS, memoria ni la interfaz desplazable.

## Comprobaciones realizadas

- Versión confirmada: `versionCode 175`, `versionName 17.5.0`.
- Archivos JSON analizados correctamente.
- Archivos XML analizados correctamente.
- Paréntesis, llaves y corchetes del archivo Kotlin equilibrados.
- `kotlinc` no reportó errores de sintaxis, tokens inesperados ni escapes ilegales; solo referencias Android no disponibles en este entorno.
- Preguntas largas: cierre por 8 segundos sin texto nuevo.
- Límite total de lectura: 90 segundos.
- Comandos de cierre reconocidos: `fin de alternativas`, `fin de opciones`, `fin de pregunta`.
- Resultados intermedios conservan el texto y abren otra sesión de Google, en vez de consultar JSON/NET prematuramente.
- El acumulador conserva fragmentos sucesivos y elimina el comando de cierre antes de consultar.
- Las preguntas cortas mantienen el cierre parcial de 3 segundos.

## Prueba lógica del acumulador

Se probaron tres fragmentos sucesivos de una pregunta con alternativas. El resultado conservó el enunciado inicial, añadió la continuación y las opciones, y eliminó `fin de alternativas` antes del envío.

## Limitación de la validación

No se compiló un APK completo porque este entorno no incluye Android SDK. La prueba final de Google SpeechRecognizer debe hacerse en el teléfono.
