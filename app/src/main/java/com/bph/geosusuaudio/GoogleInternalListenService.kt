package com.bph.geosusuaudio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs

class GoogleInternalListenService : Service(), TextToSpeech.OnInitListener {

    private lateinit var jsonRepository: JsonKnowledgeRepository
    private lateinit var questionRouter: QuestionRouter
    private lateinit var memory: MemoryStore
    private lateinit var modeSettings: AnswerModeSettings
    private lateinit var appSettings: AppSettings
    private val onlineProvider by lazy { OnlineAnswerProvider(this) }

    private val handler = Handler(Looper.getMainLooper())
    private val backgroundLock = Any()
    private val routeExecutor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "GeoSusu-JSON").apply { isDaemon = true }
    }
    private val netExecutor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "GeoSusu-NET").apply { isDaemon = true }
    }
    @Volatile private var routeFuture: Future<*>? = null
    @Volatile private var netFuture: Future<*>? = null

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var running = false
    @Volatile private var isSpeaking = false
    @Volatile private var isListening = false
    @Volatile private var onlineRequestVersion = 0

    private var lastHeard = ""
    private var lastHeardTime = 0L
    private var lastSpokenNormalized = ""
    private var lastPartial = ""
    private var partialRunnable: Runnable? = null

    // Las preguntas largas (alternativas, verdadero/falso, completar y formulas)
    // pueden llegar divididas en varias sesiones de Google. Se acumulan hasta que
    // el usuario diga una frase de cierre o permanezca 8 segundos sin agregar texto.
    private var longQuestionActive = false
    private var longQuestionBuffer = ""
    private var longQuestionStartedAt = 0L
    private var longQuestionLastTextAt = 0L
    private var longQuestionSilenceRunnable: Runnable? = null
    private var longQuestionMaxRunnable: Runnable? = null

    // Cada sesion de Google tiene un identificador propio. Los callbacks tardios
    // de una sesion destruida se ignoran y no pueden bloquear la sesion nueva.
    private var sessionSerial = 0L
    private var activeSessionId = 0L
    private var pendingStartRunnable: Runnable? = null
    private var sessionWatchdogRunnable: Runnable? = null
    private var loopSupervisorRunnable: Runnable? = null
    private var lastRecognizerEventAt = 0L
    private var consecutiveRecognizerErrors = 0

    // Control independiente del lector de voz. Si Android no entrega onDone,
    // el servicio libera el estado y vuelve a escuchar por si solo.
    private var speechSerial = 0L
    private var activeUtteranceId: String? = null
    private var ttsWatchdogRunnable: Runnable? = null
    private var ttsStateMonitorRunnable: Runnable? = null
    private var speechStartedAt = 0L
    private var postSpeechGuardRunnable: Runnable? = null

    // Evita saturar el hilo principal con decenas de emisiones RMS por segundo.
    private var lastLevelDispatchAt = 0L
    private var lastLevelSent = -1

    // Compatibilidad de idioma para Google interno. Algunos dispositivos rechazan
    // es-PE con ERROR_LANGUAGE_NOT_SUPPORTED (12) o ERROR_LANGUAGE_UNAVAILABLE (13).
    // Se prueba una lista de variantes espanolas y se recuerda la que funciona.
    private val languagePreferences by lazy {
        getSharedPreferences(LANGUAGE_PREFS_NAME, MODE_PRIVATE)
    }
    private var recognitionLanguageCandidates: List<String> = emptyList()
    private var recognitionLanguageIndex = 0

    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        jsonRepository = JsonKnowledgeRepository(appContext)
        questionRouter = QuestionRouter(jsonRepository)
        memory = MemoryStore(appContext)
        modeSettings = AnswerModeSettings(appContext)
        appSettings = AppSettings(appContext)
        warmJsonInBackground()
        initializeRecognitionLanguages()
        tts = TextToSpeech(this, this)
        createChannel()
        acquireWakeLock()
        startLoopSupervisor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Susurro Google activo"))
        renewWakeLockIfNeeded()

        val wasRunning = running
        running = true

        // Una nueva pulsacion en ESCUCHAR tambien sirve como recuperacion manual,
        // pero normalmente el usuario solo debe pulsar una vez.
        if (!wasRunning || (!isSpeaking && !isListening)) {
            scheduleFreshSession(350L, "inicio del servicio")
        } else if (!isSpeaking) {
            scheduleFreshSession(180L, "reinicio solicitado")
        }

        return START_STICKY
    }

    private fun scheduleFreshSession(delayMs: Long = 300L, reason: String = "reinicio") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { scheduleFreshSession(delayMs, reason) }
            return
        }
        if (!running || isSpeaking) return

        pendingStartRunnable?.let { handler.removeCallbacks(it) }
        pendingStartRunnable = null
        cancelSessionWatchdog()
        cancelPartialRunnable()

        isListening = false
        activeSessionId = 0L
        destroyRecognizerOnly()

        val safeDelay = delayMs.coerceAtLeast(RECOGNIZER_SETTLE_DELAY_MS)
        val runnable = Runnable {
            pendingStartRunnable = null
            if (!running || isSpeaking || isListening) return@Runnable
            startFreshRecognizerSession(reason)
        }
        pendingStartRunnable = runnable
        handler.postDelayed(runnable, safeDelay)
    }

    private fun startFreshRecognizerSession(reason: String) {
        if (!running || isSpeaking || isListening) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendUpdate("Falta permiso de micrófono", "", "Activa permiso de micrófono.", 0)
            stopSelf()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendUpdate("Google interno no disponible", "", "Reintentando automáticamente.", 0)
            scheduleFreshSession(5000L, "servicio de voz no disponible")
            return
        }

        val sessionId = ++sessionSerial
        activeSessionId = sessionId
        lastRecognizerEventAt = SystemClock.elapsedRealtime()
        lastPartial = ""
        lastLevelSent = -1
        lastLevelDispatchAt = 0L

        try {
            prepareAudioRouteForUsbMic()
            val newRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            newRecognizer.setRecognitionListener(createRecognitionListener(sessionId))
            recognizer = newRecognizer

            isListening = true
            sendUpdate("Susurro Google: escuchando...", "", "", 0)
            newRecognizer.startListening(buildIntent())
            scheduleSessionWatchdog(sessionId)
            Log.d(TAG, "Sesion Google $sessionId iniciada: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la sesion Google $sessionId", e)
            if (activeSessionId == sessionId) {
                activeSessionId = 0L
                isListening = false
            }
            destroyRecognizerOnly()
            sendUpdate("Reiniciando Google interno...", "", "", -1)
            scheduleFreshSession(1200L, "fallo al iniciar")
        }
    }

    private fun buildIntent(): Intent {
        val languageTag = currentRecognitionLanguage()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2600L)
            if (Build.VERSION.SDK_INT >= 33) {
                val hints = jsonRepository.recognitionHints(40)
                if (hints.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints)
                }
            }
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun initializeRecognitionLanguages() {
        val saved = languagePreferences.getString(KEY_WORKING_LANGUAGE, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val phoneLanguage = Locale.getDefault().toLanguageTag()
            .trim()
            .takeIf { Locale.getDefault().language.equals("es", ignoreCase = true) }

        recognitionLanguageCandidates = listOfNotNull(
            saved,
            "es-PE",
            "es-419",
            "es-ES",
            phoneLanguage,
            "es"
        ).distinctBy { it.lowercase(Locale.ROOT) }

        recognitionLanguageIndex = 0
        Log.d(TAG, "Idiomas Google interno: $recognitionLanguageCandidates")
    }

    private fun currentRecognitionLanguage(): String {
        if (recognitionLanguageCandidates.isEmpty()) initializeRecognitionLanguages()
        return recognitionLanguageCandidates
            .getOrElse(recognitionLanguageIndex) { "es" }
    }

    private fun advanceRecognitionLanguage(): String? {
        if (recognitionLanguageCandidates.isEmpty()) initializeRecognitionLanguages()
        val nextIndex = recognitionLanguageIndex + 1
        if (nextIndex >= recognitionLanguageCandidates.size) return null
        recognitionLanguageIndex = nextIndex
        return currentRecognitionLanguage()
    }

    private fun resetRecognitionLanguageCycle() {
        languagePreferences.edit().remove(KEY_WORKING_LANGUAGE).apply()
        recognitionLanguageIndex = 0
    }

    private fun rememberWorkingRecognitionLanguage() {
        val languageTag = currentRecognitionLanguage()
        val saved = languagePreferences.getString(KEY_WORKING_LANGUAGE, null)
        if (!saved.equals(languageTag, ignoreCase = true)) {
            languagePreferences.edit().putString(KEY_WORKING_LANGUAGE, languageTag).apply()
            Log.i(TAG, "Idioma compatible recordado: $languageTag")
        }
    }

    private fun createRecognitionListener(sessionId: Long): RecognitionListener {
        return object : RecognitionListener {
            private fun isCurrent(): Boolean = running && activeSessionId == sessionId

            private fun markEvent() {
                if (isCurrent()) lastRecognizerEventAt = SystemClock.elapsedRealtime()
            }

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrent()) return
                markEvent()
                rememberWorkingRecognitionLanguage()
                sendUpdate("Susurro Google listo", "", "", 0)
            }

            override fun onBeginningOfSpeech() {
                if (!isCurrent()) return
                markEvent()
                sendUpdate("Te estoy escuchando...", "", "", 25)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isCurrent()) return
                markEvent()

                val level = ((rmsdB + 2f) * 8f).toInt().coerceIn(0, 100)
                val now = SystemClock.elapsedRealtime()
                if (
                    now - lastLevelDispatchAt >= LEVEL_UPDATE_INTERVAL_MS ||
                    lastLevelSent < 0 ||
                    abs(level - lastLevelSent) >= 8
                ) {
                    lastLevelDispatchAt = now
                    lastLevelSent = level
                    sendUpdate("", "", "", level)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                if (!isCurrent()) return
                markEvent()
            }

            override fun onEndOfSpeech() {
                if (!isCurrent()) return
                markEvent()
                sendUpdate("Procesando voz...", "", "", -1)
            }

            override fun onError(error: Int) {
                val partialFallback = lastPartial.trim()
                if (!finishSessionIfCurrent(sessionId)) return
                cancelPartialRunnable()

                if (longQuestionActive || isLongExerciseTranscript(partialFallback)) {
                    if (partialFallback.isNotBlank()) {
                        val accumulated = updateLongQuestion(partialFallback)
                        if (hasLongQuestionEndCommand(accumulated)) {
                            finalizeLongQuestion("comando de cierre")
                            return
                        }
                    }

                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        consecutiveRecognizerErrors = 0
                        sendUpdate(
                            "Esperando que continúes...",
                            longQuestionBuffer,
                            "Sigue leyendo o di fin de alternativas. Se cerrará tras 8 segundos sin texto nuevo.",
                            -1
                        )
                        scheduleFreshSession(450L, "continuar pregunta larga")
                        return
                    }
                }

                if (
                    partialFallback.isNotBlank() &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                ) {
                    consecutiveRecognizerErrors = 0
                    sendUpdate(
                        "Procesando texto reconocido...",
                        partialFallback,
                        "Google no entregó el cierre final; se usará el texto completo visible.",
                        -1
                    )
                    processQuestion(partialFallback)
                    return
                }

                if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                ) {
                    consecutiveRecognizerErrors = 0
                    val rejectedLanguage = currentRecognitionLanguage()
                    languagePreferences.edit().remove(KEY_WORKING_LANGUAGE).apply()
                    val nextLanguage = advanceRecognitionLanguage()
                    if (nextLanguage != null) {
                        Log.w(TAG, "Idioma $rejectedLanguage rechazado con error $error; probando $nextLanguage")
                        sendUpdate(
                            "Ajustando idioma de Google interno...",
                            "",
                            "Probando otra variante de espanol.",
                            -1
                        )
                        scheduleFreshSession(700L, "idioma $rejectedLanguage no compatible")
                    } else {
                        Log.e(TAG, "Google rechazo todas las variantes de espanol configuradas")
                        resetRecognitionLanguageCycle()
                        sendUpdate(
                            "Google interno sin idioma disponible",
                            "",
                            "Reintentando con el idioma compatible del telefono.",
                            -1
                        )
                        scheduleFreshSession(5000L, "reinicio de idiomas")
                    }
                    return
                }

                val delay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        consecutiveRecognizerErrors = 0
                        sendUpdate("No detecto voz", "", "", -1)
                        600L
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        consecutiveRecognizerErrors += 1
                        sendUpdate("Google interno ocupado", "", "Reabriendo escucha.", -1)
                        1400L
                    }

                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                        consecutiveRecognizerErrors += 1
                        sendUpdate("Google interno en pausa breve", "", "Reintentando automáticamente.", -1)
                        5000L
                    }

                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        consecutiveRecognizerErrors += 1
                        sendUpdate("Reconectando Google interno...", "", "", -1)
                        2500L
                    }

                    else -> {
                        consecutiveRecognizerErrors += 1
                        sendUpdate("Google interno error $error", "", "Reintentando escucha.", -1)
                        if (consecutiveRecognizerErrors >= 3) 2500L else 1200L
                    }
                }

                scheduleFreshSession(delay, "error $error")
            }

            override fun onResults(results: Bundle?) {
                if (!finishSessionIfCurrent(sessionId)) return
                cancelPartialRunnable()
                consecutiveRecognizerErrors = 0

                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()

                val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val finalCandidate = chooseBestSpeechCandidate(matches, confidenceScores)
                val best = finalCandidate.ifBlank { lastPartial.trim() }

                if (best.isBlank()) {
                    if (longQuestionActive) {
                        scheduleFreshSession(450L, "resultado vacio de pregunta larga")
                    } else {
                        scheduleFreshSession(500L, "resultado vacio")
                    }
                    return
                }

                if (longQuestionActive || isLongExerciseTranscript(best)) {
                    val accumulated = updateLongQuestion(best)
                    if (hasLongQuestionEndCommand(accumulated)) {
                        finalizeLongQuestion("comando de cierre")
                    } else {
                        sendUpdate(
                            "Pregunta larga en pausa...",
                            accumulated,
                            "Puedes continuar. Di fin de alternativas o espera 8 segundos al terminar.",
                            -1
                        )
                        scheduleFreshSession(350L, "continuar pregunta larga")
                    }
                    return
                }

                processQuestion(best)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent()) return
                markEvent()

                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()

                if (partial.isNotBlank()) {
                    lastPartial = mergePartialTranscript(lastPartial, partial)
                    val longExercise = longQuestionActive || isLongExerciseTranscript(lastPartial)
                    if (longExercise) {
                        val accumulated = updateLongQuestion(lastPartial)
                        scheduleSessionWatchdog(sessionId, LONG_EXERCISE_SESSION_TIMEOUT_MS)
                        sendUpdate(
                            "Escuchando pregunta larga...",
                            accumulated,
                            "Sigue leyendo. Di fin de alternativas o espera 8 segundos al terminar.",
                            -1
                        )
                        if (hasLongQuestionEndCommand(accumulated)) {
                            handler.post { finalizeLongQuestion("comando de cierre") }
                        }
                    } else {
                        sendUpdate(
                            "Escuchando sin responder todavía...",
                            lastPartial,
                            "Texto parcial. Se procesará si Google no entrega el resultado final.",
                            -1
                        )
                        schedulePartialFallback(sessionId, false)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                if (!isCurrent()) return
                markEvent()
            }
        }
    }

    private fun finishSessionIfCurrent(sessionId: Long): Boolean {
        if (activeSessionId != sessionId) return false
        activeSessionId = 0L
        isListening = false
        cancelSessionWatchdog()
        destroyRecognizerOnly()
        return true
    }

    private fun destroyRecognizerOnly() {
        val old = recognizer
        recognizer = null
        if (old != null) {
            runCatching { old.cancel() }
                .onFailure { Log.d(TAG, "El reconocedor ya estaba cancelado", it) }
            runCatching { old.destroy() }
                .onFailure { Log.w(TAG, "No se pudo destruir SpeechRecognizer", it) }
        }
    }

    private fun scheduleSessionWatchdog(sessionId: Long, timeoutMs: Long = SESSION_HARD_TIMEOUT_MS) {
        cancelSessionWatchdog()
        val watchdog = Runnable {
            sessionWatchdogRunnable = null
            if (!running || isSpeaking || activeSessionId != sessionId) return@Runnable

            val partialFallback = lastPartial.trim()
            Log.w(TAG, "Sesion Google $sessionId sin cierre; reinicio duro")
            finishSessionIfCurrent(sessionId)
            if (longQuestionActive || isLongExerciseTranscript(partialFallback)) {
                if (partialFallback.isNotBlank()) updateLongQuestion(partialFallback)
                sendUpdate(
                    "Recuperando pregunta larga...",
                    longQuestionBuffer,
                    "El texto se conserva. Puedes continuar o decir fin de alternativas.",
                    -1
                )
                scheduleFreshSession(700L, "watchdog de pregunta larga")
            } else if (partialFallback.isNotBlank()) {
                cancelPartialRunnable()
                sendUpdate(
                    "Procesando texto reconocido...",
                    partialFallback,
                    "La sesión no cerró; se usará el texto reconocido.",
                    -1
                )
                processQuestion(partialFallback)
            } else {
                sendUpdate("Reiniciando escucha congelada...", "", "", -1)
                scheduleFreshSession(700L, "watchdog de sesion")
            }
        }
        sessionWatchdogRunnable = watchdog
        handler.postDelayed(watchdog, timeoutMs)
    }

    private fun cancelSessionWatchdog() {
        sessionWatchdogRunnable?.let { handler.removeCallbacks(it) }
        sessionWatchdogRunnable = null
    }

    private fun startLoopSupervisor() {
        loopSupervisorRunnable?.let { handler.removeCallbacks(it) }
        val supervisor = object : Runnable {
            override fun run() {
                if (running && !isSpeaking) {
                    val now = SystemClock.elapsedRealtime()
                    val noSessionScheduled = !isListening && pendingStartRunnable == null
                    val staleSession = isListening &&
                        lastRecognizerEventAt > 0L &&
                        now - lastRecognizerEventAt > SUPERVISOR_STALE_EVENT_MS

                    when {
                        staleSession -> {
                            Log.w(TAG, "Supervisor detecto una sesion sin eventos")
                            sendUpdate("Recuperando Google interno...", "", "", -1)
                            scheduleFreshSession(700L, "supervisor sin eventos")
                        }

                        noSessionScheduled -> {
                            Log.w(TAG, "Supervisor detecto el bucle detenido")
                            scheduleFreshSession(250L, "supervisor sin sesion")
                        }
                    }
                }

                if (running) handler.postDelayed(this, LOOP_SUPERVISOR_INTERVAL_MS)
            }
        }
        loopSupervisorRunnable = supervisor
        handler.postDelayed(supervisor, LOOP_SUPERVISOR_INTERVAL_MS)
    }

    private fun schedulePartialFallback(sessionId: Long, longExercise: Boolean) {
        if (longExercise || longQuestionActive) return
        cancelPartialRunnable()
        val delayMs = PARTIAL_FALLBACK_MS
        val runnable = Runnable {
            partialRunnable = null
            if (!running || isSpeaking || activeSessionId != sessionId) return@Runnable

            val fallback = lastPartial.trim()
            if (fallback.isBlank() || !finishSessionIfCurrent(sessionId)) return@Runnable

            sendUpdate(
                "Procesando texto reconocido...",
                fallback,
                "Google no entregó el resultado final; continuando con el texto reconocido.",
                -1
            )
            processQuestion(fallback)
        }
        partialRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun mergePartialTranscript(previousRaw: String, currentRaw: String): String {
        val previous = previousRaw.trim()
        val current = currentRaw.trim()
        if (previous.isBlank()) return current
        if (current.isBlank()) return previous

        val previousNormalized = ResponseRepository.normalize(previous)
        val currentNormalized = ResponseRepository.normalize(current)
        return when {
            currentNormalized == previousNormalized -> if (current.length >= previous.length) current else previous
            currentNormalized.contains(previousNormalized) -> current
            previousNormalized.contains(currentNormalized) -> previous
            else -> {
                val previousWords = previous.split(Regex("\\s+")).filter { it.isNotBlank() }
                val currentWords = current.split(Regex("\\s+")).filter { it.isNotBlank() }
                val maxOverlap = minOf(previousWords.size, currentWords.size, 12)
                var overlap = 0
                for (size in maxOverlap downTo 1) {
                    val left = previousWords.takeLast(size).joinToString(" ").lowercase(Locale.ROOT)
                    val right = currentWords.take(size).joinToString(" ").lowercase(Locale.ROOT)
                    if (left == right) {
                        overlap = size
                        break
                    }
                }
                if (overlap >= 1) {
                    (previousWords + currentWords.drop(overlap)).joinToString(" ")
                } else {
                    val previousSet = previousWords.map { it.lowercase(Locale.ROOT) }.toSet()
                    val currentSet = currentWords.map { it.lowercase(Locale.ROOT) }.toSet()
                    val shared = previousSet.intersect(currentSet).size
                    val base = minOf(previousSet.size, currentSet.size).coerceAtLeast(1)
                    val similarity = shared.toDouble() / base.toDouble()
                    if (similarity >= 0.75) {
                        if (current.length >= previous.length) current else previous
                    } else {
                        (previousWords + currentWords).joinToString(" ")
                    }
                }
            }
        }
    }

    private fun isLongExerciseTranscript(raw: String): Boolean {
        val text = ResponseRepository.normalize(raw)
        val wordCount = raw.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return wordCount >= 18 || raw.length >= 120 ||
            text.contains("alternativa") ||
            text.contains("alternativas") ||
            text.contains("opcion") ||
            text.contains("opciones") ||
            text.contains("elige la respuesta") ||
            text.contains("marca la respuesta") ||
            text.contains("inciso correcto") ||
            text.contains("verdadero o falso") ||
            text.contains("completa la frase") ||
            text.contains("completar la frase") ||
            text.contains("formula quimica") ||
            text.contains("responde solo con la formula")
    }

    private fun updateLongQuestion(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return longQuestionBuffer

        val now = SystemClock.elapsedRealtime()
        if (!longQuestionActive) {
            longQuestionActive = true
            longQuestionStartedAt = now
            longQuestionBuffer = ""
            scheduleLongQuestionMaximum()
        }

        longQuestionBuffer = mergePartialTranscript(longQuestionBuffer, text)
        longQuestionLastTextAt = now
        scheduleLongQuestionSilence()
        return longQuestionBuffer
    }

    private fun scheduleLongQuestionSilence() {
        longQuestionSilenceRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            longQuestionSilenceRunnable = null
            if (!running || !longQuestionActive || longQuestionBuffer.isBlank()) return@Runnable

            val silentFor = SystemClock.elapsedRealtime() - longQuestionLastTextAt
            if (silentFor < LONG_QUESTION_SILENCE_MS) {
                scheduleLongQuestionSilence()
                return@Runnable
            }
            finalizeLongQuestion("silencio de 8 segundos")
        }
        longQuestionSilenceRunnable = runnable
        handler.postDelayed(runnable, LONG_QUESTION_SILENCE_MS)
    }

    private fun scheduleLongQuestionMaximum() {
        longQuestionMaxRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            longQuestionMaxRunnable = null
            if (!running || !longQuestionActive || longQuestionBuffer.isBlank()) return@Runnable
            finalizeLongQuestion("limite de 90 segundos")
        }
        longQuestionMaxRunnable = runnable
        handler.postDelayed(runnable, LONG_QUESTION_MAX_DURATION_MS)
    }

    private fun hasLongQuestionEndCommand(raw: String): Boolean {
        val text = ResponseRepository.normalize(raw)
        return text.contains("fin de alternativas") ||
            text.contains("fin de las alternativas") ||
            text.contains("fin de opciones") ||
            text.contains("fin de las opciones") ||
            text.contains("fin de pregunta") ||
            text.contains("fin de la pregunta")
    }

    private fun removeLongQuestionEndCommand(raw: String): String {
        return raw
            .replace(
                Regex("""(?i)\bfin\s+de\s+(?:las\s+)?(?:alternativas|opciones|pregunta)\b[\s.,;:!?]*"""),
                " "
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun finalizeLongQuestion(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { finalizeLongQuestion(reason) }
            return
        }
        if (!running || !longQuestionActive) return

        val completeQuestion = removeLongQuestionEndCommand(longQuestionBuffer)
        val currentSession = activeSessionId
        if (currentSession != 0L) {
            finishSessionIfCurrent(currentSession)
        } else {
            isListening = false
            destroyRecognizerOnly()
        }
        clearLongQuestionState()

        if (completeQuestion.isBlank()) {
            scheduleFreshSession(500L, "pregunta larga vacia")
            return
        }

        sendUpdate(
            "Procesando pregunta completa...",
            completeQuestion,
            "Pregunta larga cerrada por $reason.",
            -1
        )
        processQuestion(completeQuestion)
    }

    private fun clearLongQuestionState() {
        longQuestionSilenceRunnable?.let { handler.removeCallbacks(it) }
        longQuestionSilenceRunnable = null
        longQuestionMaxRunnable?.let { handler.removeCallbacks(it) }
        longQuestionMaxRunnable = null
        longQuestionActive = false
        longQuestionBuffer = ""
        longQuestionStartedAt = 0L
        longQuestionLastTextAt = 0L
    }

    private fun cancelPartialRunnable() {
        partialRunnable?.let { handler.removeCallbacks(it) }
        partialRunnable = null
    }

    private fun prepareAudioRouteForUsbMic() {
        try {
            val audioManager = getSystemService(AudioManager::class.java) ?: return
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo ajustar la ruta de audio; se usa la ruta actual", e)
        }
    }

    private data class SpeechCandidate(
        val text: String,
        val originalIndex: Int,
        val googleConfidence: Float
    )

    private fun chooseBestSpeechCandidate(
        matches: List<String>,
        confidenceScores: FloatArray?
    ): String {
        val candidates = matches
            .mapIndexedNotNull { index, raw ->
                val normalized = VoiceQuestionNormalizer.normalize(raw.trim())
                if (normalized.isBlank()) return@mapIndexedNotNull null
                SpeechCandidate(
                    text = normalized,
                    originalIndex = index,
                    googleConfidence = confidenceScores?.getOrNull(index) ?: -1f
                )
            }
            .filter { !VoiceQuestionNormalizer.isTooUnclearForJsonOrNet(it.text) }
            .distinctBy { it.text }

        if (candidates.isEmpty()) {
            return matches.firstNotNullOfOrNull { raw ->
                VoiceQuestionNormalizer.normalize(raw.trim()).takeIf { it.isNotBlank() }
            }.orEmpty()
        }

        val mode = modeSettings.getMode()

        if (mode != AnswerModeSettings.Mode.INTERNET && jsonRepository.totalItems > 0) {
            val jsonWinner = candidates
                .map { candidate -> candidate to jsonRepository.findBest(candidate.text) }
                .filter { (_, match) -> match.itemId != "none" }
                .maxWithOrNull(
                    compareBy<Pair<SpeechCandidate, ResponseRepository.MatchResult>> { it.second.score }
                        .thenBy { it.first.googleConfidence }
                        .thenBy { -it.first.originalIndex }
                )

            if (jsonWinner != null) return jsonWinner.first.text
        }

        val withConfidence = candidates.filter { it.googleConfidence >= 0f }
        return if (withConfidence.isNotEmpty()) {
            withConfidence.maxWithOrNull(
                compareBy<SpeechCandidate> { it.googleConfidence }
                    .thenBy { -it.originalIndex }
            )?.text.orEmpty()
        } else {
            candidates.minByOrNull { it.originalIndex }?.text.orEmpty()
        }
    }

    private fun processQuestion(raw: String) {
        val question = VoiceQuestionNormalizer.normalize(raw)

        if (VoiceQuestionNormalizer.isTooUnclearForJsonOrNet(question)) {
            sendUpdate("No se detectó pregunta completa", question, "Repite la pregunta.", -1)
            scheduleFreshSession(650L, "pregunta incompleta")
            return
        }

        if (shouldIgnoreOwnVoice(question)) {
            scheduleFreshSession(350L, "voz propia")
            return
        }

        val now = System.currentTimeMillis()
        if (question == lastHeard && now - lastHeardTime < 4500) {
            scheduleFreshSession(350L, "pregunta duplicada")
            return
        }

        lastHeard = question
        lastHeardTime = now

        val localAnswer = LocalAnswerProvider.findAnswer(question)
        if (localAnswer != null) {
            memory.saveLast(question, localAnswer, "local", 100)
            speakAnswer(question, localAnswer)
            return
        }

        val mode = modeSettings.getMode()
        if (mode == AnswerModeSettings.Mode.INTERNET) {
            if (VoiceQuestionNormalizer.isUnsafeForNet(question)) {
                speakAnswer(question, "No hay una pregunta clara para enviar a NET.")
            } else {
                answerOnline(question)
            }
            return
        }

        answerRoutedInBackground(question, mode)
    }

    private fun answerRoutedInBackground(question: String, mode: AnswerModeSettings.Mode) {
        val status = if (mode == AnswerModeSettings.Mode.AUTO) {
            "Buscando en JSON; si no encuentra, pasa a NET..."
        } else {
            "Buscando solo en JSON..."
        }
        sendUpdate(status, question, "Procesando sin bloquear la app.", -1)

        synchronized(backgroundLock) {
            routeFuture?.cancel(true)
            routeFuture = routeExecutor.submit {
                val route = questionRouter.resolve(mode, question)
                if (!running || Thread.currentThread().isInterrupted) return@submit

                when (route) {
                    is QuestionRouter.Result.Json -> {
                        val match = route.match
                        memory.saveLast(question, match.answer, match.itemId, match.score)
                        handler.post { if (running) speakAnswer(question, match.answer) }
                    }

                    QuestionRouter.Result.JsonNotFound -> {
                        handler.post { if (running) speakAnswer(question, "No encontrado") }
                    }

                    QuestionRouter.Result.Net -> {
                        if (VoiceQuestionNormalizer.isUnsafeForNet(question)) {
                            handler.post { if (running) speakAnswer(question, "No hay una pregunta clara para enviar a NET.") }
                        } else {
                            answerOnline(question)
                        }
                    }
                }
            }
        }
    }

    private fun warmJsonInBackground() {
        synchronized(backgroundLock) {
            routeFuture = routeExecutor.submit {
                try {
                    jsonRepository.totalItems
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo precargar el JSON", e)
                }
            }
        }
    }

    private fun answerOnline(question: String, statusText: String = "Buscando NET...") {
        sendUpdate(statusText, question, "Buscando una API probada; si falla, pasa a la siguiente.", -1)
        val requestVersion = ++onlineRequestVersion

        synchronized(backgroundLock) {
            netFuture?.cancel(true)
            onlineProvider.cancelActiveRequest()
            netFuture = netExecutor.submit {
                val answer = try {
                    onlineProvider.fetchShortAnswer(question)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo inesperado al consultar NET", e)
                    "NET no pudo responder. Revisa Internet y el estado de tus APIs."
                }

                if (!running || requestVersion != onlineRequestVersion || Thread.currentThread().isInterrupted) {
                    return@submit
                }
                memory.saveLast(question, answer, "online", 0)
                handler.post {
                    if (running && requestVersion == onlineRequestVersion) {
                        speakAnswer(question, answer)
                    }
                }
            }
        }
    }

    private fun speakAnswer(question: String, answer: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { speakAnswer(question, answer) }
            return
        }
        if (!running) return

        isSpeaking = true
        isListening = false
        pendingStartRunnable?.let { handler.removeCallbacks(it) }
        pendingStartRunnable = null
        cancelSessionWatchdog()
        cancelPartialRunnable()
        clearLongQuestionState()
        activeSessionId = 0L
        destroyRecognizerOnly()

        lastSpokenNormalized = ResponseRepository.normalize(answer)
        sendUpdate("Respuesta lista", question, answer, -1)

        val utteranceId = "${UTTERANCE_ID_PREFIX}_${++speechSerial}"
        activeUtteranceId = utteranceId
        speechStartedAt = SystemClock.elapsedRealtime()
        cancelTtsWatchdog()
        cancelTtsStateMonitor()
        postSpeechGuardRunnable?.let { handler.removeCallbacks(it) }
        postSpeechGuardRunnable = null

        tts?.stop()
        tts?.setSpeechRate(appSettings.getVoiceRate())

        val result = tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            finishSpeakingAndResume(utteranceId, "error al iniciar voz")
            return
        }

        scheduleTtsWatchdog(utteranceId, answer)
        scheduleTtsStateMonitor(utteranceId)
    }

    private fun scheduleTtsStateMonitor(utteranceId: String) {
        cancelTtsStateMonitor()
        val monitor = object : Runnable {
            override fun run() {
                if (!running || activeUtteranceId != utteranceId || !isSpeaking) {
                    ttsStateMonitorRunnable = null
                    return
                }

                val elapsed = SystemClock.elapsedRealtime() - speechStartedAt
                val engineStillSpeaking = runCatching { tts?.isSpeaking == true }.getOrDefault(false)
                if (elapsed >= TTS_START_GRACE_MS && !engineStillSpeaking) {
                    ttsStateMonitorRunnable = null
                    finishSpeakingAndResume(utteranceId, "lector de voz terminado")
                    return
                }

                handler.postDelayed(this, TTS_STATE_CHECK_INTERVAL_MS)
            }
        }
        ttsStateMonitorRunnable = monitor
        handler.postDelayed(monitor, TTS_STATE_CHECK_INTERVAL_MS)
    }

    private fun cancelTtsStateMonitor() {
        ttsStateMonitorRunnable?.let { handler.removeCallbacks(it) }
        ttsStateMonitorRunnable = null
    }

    private fun scheduleTtsWatchdog(utteranceId: String, answer: String) {
        cancelTtsWatchdog()
        val rate = appSettings.getVoiceRate().coerceIn(0.35f, 2.5f)
        val wordCount = answer.trim().split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        val estimatedMs = ((wordCount * 720L) / rate).toLong() + 8000L
        val timeoutMs = estimatedMs.coerceIn(12000L, 120000L)

        val watchdog = Runnable {
            ttsWatchdogRunnable = null
            if (!running || activeUtteranceId != utteranceId || !isSpeaking) return@Runnable

            Log.w(TAG, "TTS sin onDone; recuperando escucha")
            runCatching { tts?.stop() }
            finishSpeakingAndResume(utteranceId, "watchdog de voz")
        }
        ttsWatchdogRunnable = watchdog
        handler.postDelayed(watchdog, timeoutMs)
    }

    private fun cancelTtsWatchdog() {
        ttsWatchdogRunnable?.let { handler.removeCallbacks(it) }
        ttsWatchdogRunnable = null
    }

    private fun finishSpeakingAndResume(utteranceId: String, reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { finishSpeakingAndResume(utteranceId, reason) }
            return
        }
        if (activeUtteranceId != utteranceId) return

        cancelTtsWatchdog()
        cancelTtsStateMonitor()
        activeUtteranceId = null
        isSpeaking = false
        sendUpdate("Volviendo a escuchar...", "", "", -1)
        scheduleFreshSession(350L, reason)

        // Garantia adicional: tras terminar la respuesta, la escucha debe haberse
        // reabierto antes de tres segundos. Si no, se fuerza una sesion nueva.
        postSpeechGuardRunnable?.let { handler.removeCallbacks(it) }
        val guard = Runnable {
            postSpeechGuardRunnable = null
            if (running && !isSpeaking && !isListening) {
                Log.w(TAG, "La escucha no volvio tras la respuesta; forzando reinicio")
                scheduleFreshSession(180L, "guardia posterior a respuesta")
            }
        }
        postSpeechGuardRunnable = guard
        handler.postDelayed(guard, POST_SPEECH_MAX_RETURN_MS)
    }

    private fun shouldIgnoreOwnVoice(question: String): Boolean {
        val spoken = lastSpokenNormalized
        if (question.isBlank() || spoken.isBlank()) return false
        if (spoken.contains(question) && question.length >= 8) return true

        val qWords = question.split(" ").filter { it.length >= 4 }
        if (qWords.isEmpty()) return false
        val spokenWords = spoken.split(" ").toSet()
        val common = qWords.count { it in spokenWords }
        return common >= 3 && common >= qWords.size * 0.6
    }

    private fun restartRecognizer(delayMs: Long) {
        scheduleFreshSession(delayMs, "reinicio solicitado")
    }

    private fun sendUpdate(status: String, heard: String, answer: String, level: Int) {
        val intent = Intent(ACTION_UPDATE).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_HEARD, heard)
            putExtra(EXTRA_ANSWER, answer)
            putExtra(EXTRA_LEVEL, level)
        }
        sendBroadcast(intent)

        if (status.isNotBlank()) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(status))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geo Susu Google Interno",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Geo Susu")
        .setContentText(text.take(90))
        .setOngoing(true)
        .build()

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoSusuAudio::GoogleInternalWakeLock").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_DURATION_MS)
        }
    }

    private fun renewWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        runCatching { acquireWakeLock() }
            .onFailure { Log.w(TAG, "No se pudo renovar WakeLock", it) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("es", "PE"))
            tts?.setSpeechRate(appSettings.getVoiceRate())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null) {
                        handler.post { finishSpeakingAndResume(utteranceId, "respuesta terminada") }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != null) {
                        handler.post { finishSpeakingAndResume(utteranceId, "error de voz") }
                    }
                }
            })
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // El JSON se reconstruye cuando vuelva a necesitarse; no se pierde ningun dato.
            JsonKnowledgeRepository.clearMemoryCache()
            lastPartial = ""
            Log.i(TAG, "Memoria baja: se liberaron indices reconstruibles del JSON")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        JsonKnowledgeRepository.clearMemoryCache()
        lastPartial = ""
        Log.w(TAG, "Android solicito liberar memoria")
    }

    override fun onDestroy() {
        running = false
        onlineRequestVersion += 1
        onlineProvider.cancelActiveRequest()
        synchronized(backgroundLock) {
            routeFuture?.cancel(true)
            netFuture?.cancel(true)
            routeFuture = null
            netFuture = null
        }
        routeExecutor.shutdownNow()
        netExecutor.shutdownNow()

        pendingStartRunnable?.let { handler.removeCallbacks(it) }
        pendingStartRunnable = null
        cancelSessionWatchdog()
        loopSupervisorRunnable?.let { handler.removeCallbacks(it) }
        loopSupervisorRunnable = null
        cancelPartialRunnable()
        clearLongQuestionState()
        cancelTtsWatchdog()
        cancelTtsStateMonitor()
        postSpeechGuardRunnable?.let { handler.removeCallbacks(it) }
        postSpeechGuardRunnable = null

        activeSessionId = 0L
        isListening = false
        isSpeaking = false
        destroyRecognizerOnly()

        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }

        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }.onFailure { Log.w(TAG, "No se pudo liberar WakeLock", it) }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE = "com.bph.geosusuaudio.GOOGLE_INTERNAL_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_HEARD = "heard"
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_LEVEL = "level"

        private const val CHANNEL_ID = "geo_susu_google_internal_channel"
        private const val NOTIFICATION_ID = 6001
        private const val UTTERANCE_ID_PREFIX = "geo_susu_google_internal_answer"
        private const val TAG = "GeoSusuGoogle"

        private const val RECOGNIZER_SETTLE_DELAY_MS = 250L
        private const val SESSION_HARD_TIMEOUT_MS = 18000L
        private const val LOOP_SUPERVISOR_INTERVAL_MS = 5000L
        private const val SUPERVISOR_STALE_EVENT_MS = 20000L
        private const val LEVEL_UPDATE_INTERVAL_MS = 250L
        private const val PARTIAL_FALLBACK_MS = 3000L
        private const val LONG_QUESTION_SILENCE_MS = 8000L
        private const val LONG_QUESTION_MAX_DURATION_MS = 90000L
        private const val LONG_EXERCISE_SESSION_TIMEOUT_MS = 25000L
        private const val POST_SPEECH_MAX_RETURN_MS = 2800L
        private const val TTS_STATE_CHECK_INTERVAL_MS = 500L
        private const val TTS_START_GRACE_MS = 3000L
        private const val WAKE_LOCK_DURATION_MS = 8L * 60L * 60L * 1000L
        private const val LANGUAGE_PREFS_NAME = "google_internal_language_preferences"
        private const val KEY_WORKING_LANGUAGE = "working_language_tag"
    }
}
