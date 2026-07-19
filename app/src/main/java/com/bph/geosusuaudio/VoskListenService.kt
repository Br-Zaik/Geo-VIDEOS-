package com.bph.geosusuaudio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class VoskListenService : Service(), TextToSpeech.OnInitListener {

    private lateinit var jsonRepository: JsonKnowledgeRepository
    private lateinit var questionRouter: QuestionRouter
    private lateinit var memory: MemoryStore
    private lateinit var modelManager: VoskModelManager
    private lateinit var modeSettings: AnswerModeSettings
    private lateinit var appSettings: AppSettings
    private val onlineProvider by lazy { OnlineAnswerProvider(this) }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var listenThread: Thread? = null
    private var tts: TextToSpeech? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizerLock = Any()

    @Volatile private var running = false
    @Volatile private var onlineRequestVersion = 0
    @Volatile private var loading = false
    @Volatile private var isSpeaking = false

    private var lastAnswered = ""
    private var lastAnswerTime = 0L
    private var lastLevelUpdate = 0L
    private var lastPartial = ""
    private var lastSpokenNormalized = ""
    private val preRollBuffers = ArrayDeque<ByteArray>()
    private var ignoreUntil = 0L
    private var highPassPrevInput = 0f
    private var highPassPrevOutput = 0f
    private var noiseFloor = 5f
    private var vadActive = false
    private var vadStartedAt = 0L
    private var lastVoiceAt = 0L
    private var vadVoiceFrames = 0
    private var requestedInputLabel = "Automático"
    private var routedInputLabel = "Sin verificar"

    override fun onCreate() {
        super.onCreate()
        jsonRepository = JsonKnowledgeRepository(this)
        questionRouter = QuestionRouter(jsonRepository)
        memory = MemoryStore(this)
        modelManager = VoskModelManager(this)
        modeSettings = AnswerModeSettings(this)
        appSettings = AppSettings(this)
        tts = TextToSpeech(this, this)
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Preparando Vosk continuo"))
        if (!running) startVosk()
        return START_NOT_STICKY
    }

    private fun startVosk() {
        if (loading || running) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendUpdate("Falta permiso de micrófono", "", "", 0)
            stopSelf()
            return
        }

        if (!modelManager.isReady()) {
            sendUpdate("Falta descargar modelo Vosk", "", "Toca ESCUCHAR para descargar automático.", 0)
            stopSelf()
            return
        }

        loading = true
        Thread {
            try {
                sendUpdate("Cargando modelo Vosk...", "", "", 0)
                model = Model(modelManager.modelDir().absolutePath)
                resetRecognizer()
                startAudioLoop()
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar Vosk", e)
                modelManager.deleteModel()
                sendUpdate("Modelo Vosk dañado", "", "Se borró. Toca ESCUCHAR para descargar de nuevo.", 0)
                stopSelf()
            } finally {
                loading = false
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun startAudioLoop() {
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, 4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            sendUpdate("No se pudo abrir el micrófono", "", "Revisa permisos o si otra app usa el micrófono.", 0)
            stopSelf()
            return
        }

        configureUsbInputIfPresent(audioRecord)
        setupAudioEffects(audioRecord?.audioSessionId ?: 0)

        try {
            running = true
            audioRecord?.startRecording()
            routedInputLabel = describeDevice(audioRecord?.routedDevice)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la grabación de Vosk", e)
            running = false
            sendUpdate("No inició micrófono", "", "Cierra otra app que use el micrófono y vuelve a intentar.", 0)
            stopSelf()
            return
        }

        sendUpdate(
            "Vosk activo. Escuchando preguntas.",
            "",
            "Entrada: $requestedInputLabel. Usando: $routedInputLabel. Salida: Bluetooth/media.",
            0
        )

        listenThread = Thread {
            val buffer = ByteArray(bufferSize)

            while (running) {
                try {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read <= 0) {
                        Thread.sleep(60)
                        continue
                    }

                    cleanAndBoostAudio(buffer, read)
                    val level = calculateLevel(buffer, read)
                    val currentChunk = buffer.copyOf(read)
                    rememberPreRoll(currentChunk)
                    sendLevel(level)

                    if (isSpeaking || System.currentTimeMillis() < ignoreUntil) continue

                    val now = System.currentTimeMillis()
                    val voiceDetected = detectVoiceFrame(level)
                    var startedNow = false

                    if (!vadActive && !voiceDetected) {
                        updateNoiseFloor(level)
                        continue
                    }

                    if (!vadActive && voiceDetected) {
                        vadActive = true
                        startedNow = true
                        vadStartedAt = now
                        lastVoiceAt = now
                        lastPartial = ""
                        resetRecognizer()
                        feedPreRollToRecognizer()
                        sendUpdate("Susurro detectado. Termina tu pregunta...", "", "", level)
                    }

                    if (voiceDetected) lastVoiceAt = now

                    synchronized(recognizerLock) {
                        val rec = recognizer
                        if (rec != null && !startedNow) {
                            val becameFinal = rec.acceptWaveForm(buffer, read)
                            val json = if (becameFinal) rec.getResult() else rec.getPartialResult()
                            val heard = extractText(json)
                            if (heard.isNotBlank() && ResponseRepository.normalize(heard).length >= ResponseRepository.normalize(lastPartial).length) {
                                lastPartial = heard
                            }
                        }
                    }

                    val duration = now - vadStartedAt
                    val silence = now - lastVoiceAt
                    val canFinish = duration >= MIN_QUESTION_MS && silence >= FINAL_SILENCE_MS
                    val mustFinish = duration >= MAX_QUESTION_MS

                    if (vadActive && (canFinish || mustFinish)) {
                        finishVadQuestion(level)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo en el bucle de audio Vosk; se intentará recuperar", e)
                    sendUpdate("Recuperando micrófono...", "", "", -1)
                    resetVadState()
                    resetRecognizer()
                    Thread.sleep(180)
                }
            }
        }.apply {
            name = "GeoSusuVoskLoop"
            start()
        }
    }


    @Suppress("DEPRECATION")
    private fun configureUsbInputIfPresent(record: AudioRecord?) {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo cambiar el modo de audio; se mantiene la ruta actual", e)
        }

        val preferred = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .sortedWith(compareBy<AudioDeviceInfo> {
                when (it.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> 0
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> 1
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3
                    else -> 2
                }
            })
            .firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }

        requestedInputLabel = if (preferred == null) "Automático" else describeDevice(preferred)
        try {
            preferred?.let { record?.setPreferredDevice(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo seleccionar el micrófono preferido", e)
        }
    }

    private fun describeDevice(device: AudioDeviceInfo?): String {
        if (device == null) return "No verificado"
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-C"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cable/headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth mic"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono del teléfono"
            else -> "Audio ${device.type}"
        }
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }.orEmpty()
        return if (name.isBlank()) type else "$type: $name"
    }

    private fun setupAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cancelación de eco no disponible", e)
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ganancia automática no disponible", e)
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Supresión de ruido no disponible", e)
        }
    }


    private fun detectVoiceFrame(level: Int): Boolean {
        val whisper = appSettings.getMicSensitivity() == AppSettings.MicSensitivity.WHISPER
        val baseThreshold = if (whisper) 8 else 12
        val dynamicThreshold = (noiseFloor + if (whisper) 7f else 10f).toInt()
        val threshold = maxOf(baseThreshold, dynamicThreshold).coerceAtMost(62)
        val candidate = if (vadActive) level >= threshold - 3 else level >= threshold

        vadVoiceFrames = if (candidate) {
            (vadVoiceFrames + 1).coerceAtMost(8)
        } else {
            (vadVoiceFrames - 1).coerceAtLeast(0)
        }

        return if (vadActive) vadVoiceFrames >= 1 else vadVoiceFrames >= 3
    }

    private fun updateNoiseFloor(level: Int) {
        if (level > 70) return
        noiseFloor = (noiseFloor * 0.96f + level.coerceIn(0, 55) * 0.04f).coerceIn(2f, 45f)
    }

    private fun finishVadQuestion(level: Int) {
        var finalJson: String? = null
        synchronized(recognizerLock) {
            try {
                finalJson = recognizer?.getFinalResult()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo obtener el resultado final de Vosk", e)
            }
        }

        val finalText = extractText(finalJson)
        val bestFinal = chooseBetterQuestion(lastPartial, finalText)
        resetVadState()
        lastPartial = ""
        processText(bestFinal)
        sendLevel(level)
    }

    private fun resetVadState() {
        vadActive = false
        vadStartedAt = 0L
        lastVoiceAt = 0L
        vadVoiceFrames = 0
    }

    private fun cleanAndBoostAudio(buffer: ByteArray, read: Int) {
        val gain = appSettings.getMicGain()
        val cleaner = appSettings.getNoiseCleaner()
        val whisper = appSettings.getMicSensitivity() == AppSettings.MicSensitivity.WHISPER

        // No cortar el susurro: la puerta es suave y baja.
        val softGate = when {
            cleaner == AppSettings.NoiseCleaner.STRONG && whisper -> 55
            cleaner == AppSettings.NoiseCleaner.STRONG -> 75
            whisper -> 45
            else -> 65
        }
        val gateFactor = when {
            cleaner == AppSettings.NoiseCleaner.STRONG && whisper -> 0.72f
            cleaner == AppSettings.NoiseCleaner.STRONG -> 0.60f
            else -> 0.82f
        }
        val highPassAlpha = if (cleaner == AppSettings.NoiseCleaner.STRONG) 0.982f else 0.988f

        var i = 0
        while (i + 1 < read) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()

            val highPassed = sample - highPassPrevInput + highPassAlpha * highPassPrevOutput
            highPassPrevInput = sample.toFloat()
            highPassPrevOutput = highPassed

            var cleaned = highPassed

            if (abs(cleaned) < softGate) {
                cleaned *= gateFactor
            }

            // Levanta voz baja sin destruir picos fuertes.
            var boosted = cleaned * gain
            val absBoosted = abs(boosted)
            if (absBoosted > 26000f) {
                boosted = if (boosted > 0) 26000f + (absBoosted - 26000f) * 0.18f
                else -26000f - (absBoosted - 26000f) * 0.18f
            }

            val out = boosted
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            buffer[i] = (out.toInt() and 0xFF).toByte()
            buffer[i + 1] = ((out.toInt() shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    private fun rememberPreRoll(chunk: ByteArray) {
        preRollBuffers.addLast(chunk)
        while (preRollBuffers.size > 5) {
            preRollBuffers.removeFirst()
        }
    }

    private fun feedPreRollToRecognizer() {
        synchronized(recognizerLock) {
            val rec = recognizer ?: return
            for (chunk in preRollBuffers) {
                try {
                    rec.acceptWaveForm(chunk, chunk.size)
                } catch (e: Exception) {
                    Log.d(TAG, "No se pudo añadir un bloque de preaudio a Vosk", e)
                }
            }
        }
    }



    private fun chooseBetterQuestion(previous: String, current: String): String {
        val old = previous.trim()
        val now = current.trim()
        if (old.isBlank()) return now
        if (now.isBlank()) return old
        return if (ResponseRepository.normalize(now).length >= ResponseRepository.normalize(old).length) now else old
    }


    private fun processText(rawText: String) {
        val question = VoiceQuestionNormalizer.normalize(normalizeQuestion(rawText))
        if (VoiceQuestionNormalizer.isTooUnclearForJsonOrNet(question) || isIncompleteQuestion(question)) {
            sendUpdate("No escuché la pregunta completa", question, "Repite la pregunta completa.", -1)
            resetRecognizer()
            return
        }

        if (shouldIgnoreOwnVoice(question)) {
            resetRecognizer()
            sendUpdate("Ignorando eco. Listo para otra pregunta.", "", "", -1)
            return
        }

        val now = System.currentTimeMillis()
        if (question == lastAnswered && now - lastAnswerTime < 6500) {
            resetRecognizer()
            return
        }

        lastAnswered = question
        lastAnswerTime = now

        val localAnswer = LocalAnswerProvider.findAnswer(question)
        if (localAnswer != null) {
            memory.saveLast(question, localAnswer, "local", 100)
            sendUpdate("Respuesta local", question, localAnswer, -1)
            speakAndReset(localAnswer)
            return
        }

        val mode = modeSettings.getMode()
        if (mode == AnswerModeSettings.Mode.INTERNET) {
            if (VoiceQuestionNormalizer.isUnsafeForNet(question)) {
                sendUpdate("NET bloqueado por texto vacío", question, "No hay una pregunta clara para enviar a NET.", -1)
                speakAndReset("No hay una pregunta clara para enviar a NET.")
            } else {
                answerOnline(question)
            }
            return
        }

        when (val route = questionRouter.resolve(mode, question)) {
            is QuestionRouter.Result.Json -> answerLocal(question, route.match)
            QuestionRouter.Result.JsonNotFound -> {
                sendUpdate("No encontrado en JSON", question, "No encontrado", -1)
                speakAndReset("No encontrado")
            }
            QuestionRouter.Result.Net -> {
                if (VoiceQuestionNormalizer.isUnsafeForNet(question)) {
                    sendUpdate("NET bloqueado por texto vacío", question, "No hay una pregunta clara para enviar a NET.", -1)
                    speakAndReset("No hay una pregunta clara para enviar a NET.")
                } else {
                    answerOnline(question)
                }
            }
        }
    }




    private fun isIncompleteQuestion(question: String): Boolean {
        val normalized = ResponseRepository.normalize(question)
        val words = questionKeywords(normalized)
        if (words.isEmpty()) return true

        val incomplete = setOf(
            "que", "que es", "q", "q es", "dime", "define", "defineme",
            "la palabra", "palabra", "que significa", "significa"
        )
        return normalized in incomplete
    }

    private fun questionKeywords(normalized: String): List<String> {
        val stop = setOf(
            "q", "que", "es", "son", "un", "una", "el", "la", "los", "las",
            "de", "del", "en", "por", "para", "dime", "di", "define",
            "defineme", "concepto", "significa", "significado", "palabra"
        )
        return normalized.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stop }
            .distinct()
    }

    private fun shouldIgnoreOwnVoice(question: String): Boolean {
        val q = ResponseRepository.normalize(question)
        val a = lastSpokenNormalized
        if (q.isBlank() || a.isBlank()) return false
        if (a.contains(q) && q.length >= 8) return true

        val qWords = q.split(" ").filter { it.length >= 4 }
        if (qWords.isEmpty()) return false
        val aWords = a.split(" ").toSet()
        val common = qWords.count { it in aWords }
        return common >= 2
    }


    private fun answerLocal(question: String, match: ResponseRepository.MatchResult) {
        memory.saveLast(question, match.answer, match.itemId, match.score)
        sendUpdate("Respuesta local. Reiniciando escucha.", question, match.answer, -1)
        speakAndReset(match.answer)
    }






    private fun answerOnline(question: String, statusText: String = "Buscando online...") {
        isSpeaking = true
        stopRecordingSafely()
        sendUpdate(statusText, question, "Buscando una API probada; si falla, pasa a la siguiente.", -1)
        val requestVersion = ++onlineRequestVersion

        Thread {
            val answer = try {
                onlineProvider.fetchShortAnswer(question)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo inesperado al consultar NET", e)
                "NET no pudo responder. Revisa Internet y el estado de tus APIs."
            }

            if (!running || requestVersion != onlineRequestVersion) return@Thread
            memory.saveLast(question, answer, "online", 0)
            handler.post {
                if (running && requestVersion == onlineRequestVersion) {
                    sendUpdate("Respuesta online. Reiniciando escucha.", question, answer, -1)
                    speakTracked(answer)
                }
            }
        }.start()
    }

    private fun speakAndReset(answer: String) {
        isSpeaking = true
        stopRecordingSafely()
        resetRecognizer()
        handler.post {
            speakTracked(answer)
        }
    }

    private fun speakTracked(answer: String) {
        lastSpokenNormalized = ResponseRepository.normalize(answer)
        ignoreUntil = System.currentTimeMillis() + 650L
        tts?.stop()
        tts?.setSpeechRate(appSettings.getVoiceRate())
        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun stopRecordingSafely() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.d(TAG, "La grabación ya estaba detenida", e)
        }
    }

    private fun startRecordingSafely() {
        try {
            if (running && audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.startRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reanudar la grabación Vosk", e)
            sendUpdate("No se pudo reanudar el micrófono", "", "Toca DETENER y luego ESCUCHAR.", -1)
        }
    }





    private fun normalizeQuestion(text: String): String {
        return ResponseRepository.normalize(text)
    }

    private fun resetRecognizer() {
        synchronized(recognizerLock) {
            val currentModel = model ?: return
            try {
                recognizer?.close()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar el reconocedor anterior", e)
            }

            recognizer = Recognizer(currentModel, 16000.0f).apply {
                try {
                    setWords(true)
                } catch (e: Exception) {
                    Log.w(TAG, "Vosk no permitió activar palabras con confianza", e)
                }
            }
        }
    }

    private fun extractText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            val obj = JSONObject(json)
            obj.optString("text").ifBlank { obj.optString("partial") }
        } catch (_: Exception) {
            ""
        }
    }


    private fun calculateLevel(buffer: ByteArray, read: Int): Int {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < read) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            sum += (sample * sample).toDouble()
            count++
            i += 2
        }
        if (count == 0) return 0
        val rms = sqrt(sum / count)
        return ((rms / 32768.0) * 100.0 * 7.5).toInt().coerceIn(0, 100)
    }

    private fun sendLevel(level: Int) {
        val now = System.currentTimeMillis()
        if (now - lastLevelUpdate < 180) return
        lastLevelUpdate = now
        sendUpdate("", "", "", level)
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
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Geo Susu Vosk", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Geo Susu Audio activo")
        .setContentText(text.take(90))
        .setOngoing(true)
        .build()

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoSusuAudio::VoskWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("es", "PE"))
            tts?.setSpeechRate(appSettings.getVoiceRate())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == UTTERANCE_ID) {
                        handler.postDelayed({
                            isSpeaking = false
                            ignoreUntil = System.currentTimeMillis() + 650L
                            resetRecognizer()
                            startRecordingSafely()
                            sendUpdate("Listo. Pregunta de nuevo.", "", "", -1)
                        }, 180L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    ignoreUntil = System.currentTimeMillis() + 650L
                    resetRecognizer()
                    startRecordingSafely()
                    sendUpdate("Listo. Pregunta de nuevo.", "", "", -1)
                }
            })
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        running = false
        onlineRequestVersion += 1
        onlineProvider.cancelActiveRequest()
        runCatching { audioRecord?.stop() }
            .onFailure { Log.d(TAG, "La grabación ya estaba detenida", it) }
        runCatching { audioRecord?.release() }
            .onFailure { Log.w(TAG, "No se pudo liberar AudioRecord", it) }
        runCatching { recognizer?.close() }
            .onFailure { Log.w(TAG, "No se pudo cerrar Vosk", it) }
        runCatching { model?.close() }
            .onFailure { Log.w(TAG, "No se pudo cerrar el modelo Vosk", it) }
        runCatching { acousticEchoCanceler?.release() }
        runCatching { automaticGainControl?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }.onFailure { Log.w(TAG, "No se pudo liberar WakeLock", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE = "com.bph.geosusuaudio.VOSK_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_HEARD = "heard"
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_LEVEL = "level"
        private const val CHANNEL_ID = "geo_susu_vosk_channel"
        private const val NOTIFICATION_ID = 2001
        private const val UTTERANCE_ID = "geo_susu_vosk_answer"
        private const val TAG = "GeoSusuVosk"
        private const val MIN_QUESTION_MS = 3000L
        private const val FINAL_SILENCE_MS = 1900L
        private const val MAX_QUESTION_MS = 9500L
    }
}
