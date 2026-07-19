Geo Susu v161

Cambios aplicados en esta version:
- La busqueda JSON calcula una coincidencia real de 0 a 100 y solo responde cuando el resultado es seguro.
- Las frases cortas, incompletas o ambiguas ya no activan una respuesta solo por compartir palabras generales.
- En modo JSON, una coincidencia insegura muestra “No encontrado”.
- En modo AUTO, una coincidencia insegura o inexistente pasa automaticamente a NET.
- Google interno revisa todas las alternativas finales que devuelve el reconocedor.
- En JSON y AUTO, si una alternativa coincide de forma segura con el JSON cargado, se usa esa alternativa.
- En NET, o cuando ninguna alternativa coincide con JSON, se respeta la confianza entregada por Google y su orden original.
- El sistema no guarda preguntas ni incluye un banco fijo: trabaja exclusivamente con el JSON que el usuario cargue.
- Se conservaron los tiempos restaurados de Google interno: 4200 ms de minimo y 2600 ms de silencio.
- No se modificaron Vosk, las API, la interfaz, la voz de respuesta ni las demas funciones.

Geo Susu v162

Archivo de ejemplo agregado:
- `EJEMPLO_MINIMO_JSON_GEO_SUSU.json`
- Contiene 2 registros de muestra.
- Cada registro tiene exactamente 3 preguntas completas y 3 variantes breves.
- `contenido` contiene la unica respuesta que Geo Susu debe mostrar o leer.
- `aliases` y `keywords` ayudan a distinguir el tema, pero no reemplazan preguntas claras.
- Cada `id` debe ser unico.
- No se deben mezclar dos respuestas diferentes en un mismo registro.
- Evitar variantes demasiado generales como `porcentaje`, `tema` o `mineria` sin contexto.
- El ejemplo no se carga automaticamente ni agrega preguntas permanentes a la app; solo funciona si el usuario lo selecciona como cualquier otro JSON.
- No se modificaron el reconocimiento, JSON seguro, AUTO, NET, Vosk, API, interfaz ni las demas funciones.

Geo Susu v163

Gestión de APIs NET reforzada sin cambiar JSON, reconocimiento, Vosk ni la interfaz principal:
- NET y AUTO usan únicamente APIs que fueron probadas y quedaron en estado FUNCIONANDO.
- La última API que respondió bien se intenta primero en la siguiente consulta.
- Si una API llega a límite, se queda sin créditos, rechaza la clave, falla temporalmente, demora demasiado o devuelve una respuesta no útil, la app pasa a la siguiente API disponible.
- Los límites diarios, por minuto y temporales conservan sus tiempos de espera. Al terminar la espera, una API ya probada vuelve automáticamente a la rotación.
- Antes de recorrer las claves se comprueba que exista una conexión a Internet validada.
- Si se detectan fallos reales de red consecutivos, se detiene el recorrido para no hacer esperar con todas las APIs.
- Las respuestas claramente en inglés, vacías, incompletas, técnicas o con formato incorrecto se rechazan y se prueba otra API.
- El botón DETENER cancela la consulta NET activa e impide que una respuesta antigua aparezca o se lea después.
- Se redujeron los tiempos de espera de consultas normales a 6 segundos de conexión y 9 segundos de lectura. La prueba manual de APIs conserva sus tiempos anteriores para comprobar cada clave con mayor margen.
- PROBAR API individual y PROBAR APIS siguen verificando cada clave por separado y muestran FUNCIONANDO, LÍMITE, EN ESPERA, ERROR o NO PROBADA.
- Si no hay Internet durante una prueba, no se marca una clave válida como errónea ni se borra su estado anterior.
- Se añadió el permiso ACCESS_NETWORK_STATE para detectar la conexión antes de consultar.
- Versión: 16.3.0, código 163.

Geo Susu v170

Restauración del Google interno al comportamiento estable anterior:
- Se tomó Geo Susu v163 como base para conservar JSON seguro, AUTO, NET y la gestión resistente de APIs.
- Google interno vuelve al bucle original: inicia, termina o recibe error y vuelve a abrir otra sesión con el sonido normal de Google.
- Se restauraron los tiempos que funcionaban antes: 4200 ms de duración mínima y 2600 ms de silencio completo o posiblemente completo.
- Después de leer una respuesta, vuelve al bucle normal con la pausa original.
- Se eliminaron por completo los supervisores, AudioRecord, detectores previos, reinicios programados y controles de 3 segundos añadidos en v164-v169.
- Con micrófono USB-C conectado, Android usa esa entrada; sin USB-C, usa el micrófono del celular.
- Los auriculares Bluetooth siguen siendo salida de audio y no se activa Bluetooth SCO como micrófono.
- No se modificaron JSON, AUTO, NET, APIs, prueba de APIs, Vosk, velocidades ni interfaz.
- Versión: 17.0.0, código 170.


Geo Susu v171

Correccion de estabilidad de Google interno para uso prolongado:
- Mantiene el bucle directo de Google interno y sus sonidos normales; no usa AudioRecord ni detector previo.
- Cada ciclo crea un SpeechRecognizer nuevo y destruye completamente el anterior para evitar reutilizar una sesion dañada.
- Cada sesion tiene un identificador; los callbacks tardios de sesiones anteriores se ignoran.
- Si Google no cierra una sesion en 18 segundos, el servicio la destruye y abre otra automaticamente.
- Un supervisor revisa el bucle cada 5 segundos y lo recupera si queda sin sesion o sin eventos.
- Los cambios de micrófono se toman en la siguiente sesion: con USB-C Android usa la entrada externa y sin USB-C usa el microfono del celular.
- Se limita la frecuencia de actualizacion del nivel de voz para no saturar el hilo principal durante horas.
- El lector de voz tiene monitor propio: si Android no entrega onDone, detecta cuando dejo de hablar y reabre Google.
- Tras terminar la respuesta, la escucha se inicia normalmente en menos de un segundo y existe una guardia maxima de 2.8 segundos.
- El WakeLock dura 8 horas, suficiente para una prueba continua de 6 horas.
- Se conservaron los tiempos de Google: 4200 ms de minimo y 2600 ms de silencio.
- No se modificaron JSON, AUTO, NET, APIs, prueba de APIs, Vosk ni la interfaz.
- Version: 17.1.0, codigo 171.

Geo Susu v172

Correccion puntual del error 12/13 de idioma en Google interno:
- Se conserva sin cambios el bucle de escucha, los tiempos 4200/2600/2600, la recuperacion de sesiones, JSON, AUTO, NET, APIs, Vosk y la interfaz.
- Google interno intenta primero el idioma que funciono anteriormente.
- Si el telefono rechaza `es-PE`, cambia automaticamente a `es-419`, `es-ES`, el espanol configurado en el telefono y finalmente `es`.
- Los errores 12 y 13 ya no entran en un reintento infinito con el mismo idioma rechazado.
- Cuando una variante funciona, se guarda y se utiliza primero en las siguientes sesiones.
- Version: 17.2.0, codigo 172.

## v173 - margen de memoria y liberacion controlada

- Se activo `android:largeHeap="true"` como margen adicional de RAM mientras la app esta en uso.
- JSON y NET ahora usan un hilo reutilizable cada uno; ya no crean un hilo nuevo por cada pregunta.
- Al iniciar una nueva consulta NET, se cancela y libera la anterior.
- Al cerrar el servicio se cancelan tareas, conexiones y ejecutores pendientes.
- Si Android avisa memoria baja, se liberan solo los indices reconstruibles del JSON; el archivo y las preguntas no se borran.
- No se cambiaron reconocimiento, tiempos, AUTO, JSON, NET, APIs, Vosk ni interfaz.

## v174 - preguntas y respuestas desplazables + cierre de texto parcial

- Los cuadros de Pregunta y Respuesta ahora permiten desplazamiento vertical con el dedo.
- El cuadro de Pregunta muestra mas altura y conserva todo el texto reconocido; ya no esta limitado a dos lineas.
- Al llegar texto nuevo desde Google interno o Vosk, el cuadro correspondiente baja automaticamente al final y el usuario puede volver hacia arriba manualmente.
- Si Google interno entrega texto parcial pero nunca envia el resultado final, Geo Susu usa ese texto despues de 3 segundos sin cambios y continua con JSON o NET segun el modo.
- En preguntas largas de alternativas, el margen se amplia a 5.5 segundos y la sesion puede permanecer abierta hasta 60 segundos mientras siguen llegando fragmentos.
- Si Google termina con NO_MATCH o SPEECH_TIMEOUT despues de haber reconocido texto parcial, ese texto se procesa en vez de perder la pregunta.
- Los fragmentos parciales acumulativos se conservan y se combinan cuando tienen continuidad.
- No se cambiaron JSON seguro, AUTO, NET, APIs, prueba de APIs, Vosk, idiomas, tiempos base de Google, memoria ni voz de respuesta.
- Version: 17.4.0, codigo 174.


## v175 - pregunta larga completa sin corte prematuro

- Los ejercicios de alternativas, opciones, verdadero/falso, completar y formulas activan un modo de pregunta larga.
- Tambien se activa automaticamente cuando la transcripcion supera 18 palabras o 120 caracteres, para evitar cortar enunciados extensos antes de reconocer la palabra "opciones".
- La pregunta puede llegar dividida en varias sesiones de Google interno; los fragmentos se conservan y se unen sin borrar lo anterior.
- Un resultado final intermedio ya no envia la pregunta a JSON o NET: abre otra sesion y permite continuar leyendo.
- La pregunta se cierra al decir "fin de alternativas", "fin de opciones" o "fin de pregunta".
- Si no se usa un comando de cierre, se procesa despues de 8 segundos sin texto nuevo.
- El tiempo maximo total para leer una pregunta larga es de 90 segundos.
- Los cuadros desplazables de Pregunta y Respuesta se mantienen sin cambios.
- No se modificaron JSON, AUTO, NET, APIs, Vosk, idiomas, TTS, memoria ni los tiempos normales de preguntas cortas.
- Version: 17.5.0, codigo 175.


Geo Susu v177

Restauracion solicitada:
- Se elimino por completo el ajuste Sonido de Google y su boton de tuerca.
- Google interno conserva su sonido normal de inicio y reinicio.
- Se restauro exactamente el comportamiento de la v175 para la escucha, preguntas largas y ciclo automatico.
- No se modificaron JSON, AUTO, NET, APIs, Vosk, idioma, TTS, memoria, tiempos ni interfaz restante.
- Version: 17.7.0, codigo 177.
