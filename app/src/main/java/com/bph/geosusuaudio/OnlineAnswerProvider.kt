package com.bph.geosusuaudio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class OnlineAnswerProvider(private val context: Context? = null) {

    private data class NetCallResult(
        val ok: Boolean,
        val text: String,
        val error: String,
        val provider: NetProvider,
        val model: String,
        val canTryNextApi: Boolean = false,
        val failureType: FailureType = FailureType.NONE
    )

    private enum class FailureType {
        NONE, LIMIT_DAILY, LIMIT_MINUTE, LIMIT_UNKNOWN, INVALID, TEMPORARY, NETWORK, CANCELLED, OTHER
    }

    private val runtimeRequestId = AtomicInteger(0)
    private val connectionLock = Any()
    @Volatile private var activeRuntimeConnection: HttpURLConnection? = null

    fun cancelActiveRequest() {
        runtimeRequestId.incrementAndGet()
        synchronized(connectionLock) {
            runCatching { activeRuntimeConnection?.disconnect() }
            activeRuntimeConnection = null
        }
    }

    fun fetchShortAnswer(rawQuery: String): String {
        val query = cleanQuery(rawQuery)
        if (query.length < 2) return "Pregunta muy corta. Repite la palabra completa."
        if (!hasValidatedInternet()) return "Sin conexión a Internet. Revisa tus datos o Wi-Fi."

        val requestId = beginRuntimeRequest()
        val settings = context?.let { GeminiSettings(it) }
        val configs = settings?.getApiConfigs().orEmpty()
        if (configs.isEmpty()) {
            return "Sin API configurada. Toca API, agrega Gemini, OpenRouter, Mistral o Cohere y prueba si funciona."
        }

        configs.forEach { settings?.shouldSkip(it) }
        val baseOrder = orderedConfigs(configs, settings?.getLastGoodIndex() ?: 0)
        val available = baseOrder.filter { (_, config) ->
            settings != null && settings.isReady(config) && !settings.shouldSkip(config)
        }
        if (available.isEmpty()) {
            return "No hay API probada y funcionando. Entra a APIs NET y prueba cada clave antes de usar NET/AUTO."
        }

        var lastError = ""
        var dailyLimitHit = false
        var minuteLimitHit = false
        var unknownLimitHit = false
        var invalidHit = false
        var waitHit = false
        var unhelpfulHit = false
        var networkFailures = 0

        for ((originalIndex, config) in available) {
            if (!isRuntimeRequestActive(requestId)) return "Consulta NET cancelada."

            val cleanConfig = config.clean(originalIndex)
            val formatError = getApiConfigError(cleanConfig)
            if (formatError != null) {
                invalidHit = true
                lastError = "${cleanConfig.name}: $formatError"
                settings?.markError(cleanConfig, formatError)
                continue
            }

            val result = askProvider(cleanConfig, query, runtimeRequestId = requestId)
            if (!isRuntimeRequestActive(requestId) || result.failureType == FailureType.CANCELLED) {
                return "Consulta NET cancelada."
            }

            if (result.ok) {
                val issue = netAnswerIssue(result.text, query)
                if (issue == null) {
                    settings?.saveLastGoodIndex(originalIndex)
                    settings?.markReady(cleanConfig)
                    return result.text
                }

                unhelpfulHit = true
                lastError = "${cleanConfig.provider.label}: $issue Se cambia a otra API."
                settings?.markWait(cleanConfig, "$issue Se cambia a otra API.", BAD_ANSWER_COOLDOWN_MS)
                continue
            }

            lastError = result.error
            when (result.failureType) {
                FailureType.LIMIT_DAILY -> {
                    dailyLimitHit = true
                    settings?.markLimit(cleanConfig, result.error.ifBlank { "Llegó al límite diario o no tiene créditos." })
                }
                FailureType.LIMIT_MINUTE -> {
                    minuteLimitHit = true
                    settings?.markMinuteLimit(cleanConfig, result.error.ifBlank { "Límite por minuto. Espera unos minutos." })
                }
                FailureType.LIMIT_UNKNOWN -> {
                    unknownLimitHit = true
                    settings?.markUnknownLimit(cleanConfig, result.error.ifBlank { "Límite temporal. Espera o usa otra API." })
                }
                FailureType.INVALID -> {
                    invalidHit = true
                    settings?.markError(cleanConfig, result.error.ifBlank { "API inválida, modelo incorrecto o sin permiso." })
                }
                FailureType.TEMPORARY -> {
                    waitHit = true
                    settings?.markWait(cleanConfig, result.error.ifBlank { "Error temporal del servidor." })
                }
                FailureType.NETWORK -> {
                    networkFailures += 1
                    lastError = result.error.ifBlank { "Conexión inestable. Revisa Internet y vuelve a intentar." }
                    if (!hasValidatedInternet() || networkFailures >= MAX_CONSECUTIVE_NETWORK_FAILURES) {
                        break
                    }
                }
                FailureType.OTHER -> {
                    waitHit = true
                    settings?.markWait(cleanConfig, result.error.ifBlank { "Respuesta NET no útil." })
                }
                else -> Unit
            }

            if (!result.canTryNextApi) break
        }

        clearRuntimeConnection(requestId)
        return when {
            networkFailures >= MAX_CONSECUTIVE_NETWORK_FAILURES || !hasValidatedInternet() ->
                "Sin conexión estable a Internet. Se detuvo el recorrido para no hacerte esperar con todas las APIs."
            unhelpfulHit && !dailyLimitHit && !minuteLimitHit && !unknownLimitHit && !invalidHit ->
                "Las APIs disponibles respondieron en otro idioma, incompleto o con un formato no válido. Prueba otra API o modelo."
            dailyLimitHit && !minuteLimitHit && !unknownLimitHit && !invalidHit && !waitHit ->
                "Las APIs disponibles llegaron al límite diario o se quedaron sin créditos. Agrega otra API o usa JSON."
            minuteLimitHit && !dailyLimitHit && !unknownLimitHit && !invalidHit && !waitHit ->
                "Límite por minuto. La API queda en espera 10 minutos y se probará otra disponible."
            unknownLimitHit && !dailyLimitHit && !minuteLimitHit && !invalidHit && !waitHit ->
                "Límite temporal de API. Queda en espera para no insistir y se usará otra disponible."
            invalidHit && !dailyLimitHit && !minuteLimitHit && !unknownLimitHit && !waitHit ->
                "Una o más APIs no se pueden usar. Revisa proveedor, modelo o clave."
            waitHit && !dailyLimitHit && !minuteLimitHit && !unknownLimitHit && !invalidHit ->
                "Las APIs están en espera temporal. Vuelve a intentar en unos minutos o agrega otra API."
            lastError.isNotBlank() -> lastError
            else -> "NET no devolvió respuesta. Revisa APIs probadas, Internet, modelo o límite de uso."
        }
    }

    fun testApiConfig(apiConfig: NetApiConfig): Pair<Boolean, String> {
        val clean = apiConfig.clean(0)
        val settings = context?.let { GeminiSettings(it) }
        val label = "${clean.name} (${clean.provider.label})"

        val formatError = getApiConfigError(clean)
        if (formatError != null) {
            settings?.markError(clean, formatError)
            return false to "$label: ERROR - $formatError"
        }

        if (!hasValidatedInternet()) {
            return false to "$label: NO PROBADA - Sin conexión a Internet. No se cambió el estado guardado."
        }

        val result = askProvider(clean, "Responde solo: OK", testing = true)
        if (result.ok) {
            settings?.markReady(clean)
            val savedConfigs = settings?.getApiConfigs().orEmpty()
            val activeIndex = savedConfigs.indexOfFirst { saved ->
                settings?.configId(saved) == settings?.configId(clean)
            }
            if (activeIndex >= 0) settings?.saveLastGoodIndex(activeIndex)
            return true to "$label: FUNCIONANDO\nLa API respondió correctamente y quedó disponible para NET/AUTO."
        }

        val detail = when (result.failureType) {
            FailureType.LIMIT_DAILY -> {
                val message = result.error.ifBlank { "Límite diario o sin créditos." }
                settings?.markLimit(clean, message)
                "LÍMITE / SIN CRÉDITOS - $message"
            }
            FailureType.LIMIT_MINUTE -> {
                val message = result.error.ifBlank { "Límite por minuto." }
                settings?.markMinuteLimit(clean, message)
                "EN ESPERA - $message"
            }
            FailureType.LIMIT_UNKNOWN -> {
                val message = result.error.ifBlank { "Límite temporal." }
                settings?.markUnknownLimit(clean, message)
                "EN ESPERA - $message"
            }
            FailureType.INVALID -> {
                val message = result.error.ifBlank { "API inválida, modelo incorrecto o sin permiso." }
                settings?.markError(clean, message)
                "ERROR - $message"
            }
            FailureType.TEMPORARY -> {
                val message = result.error.ifBlank { "Error temporal del proveedor." }
                settings?.markWait(clean, message)
                "EN ESPERA - $message"
            }
            FailureType.NETWORK -> {
                val message = result.error.ifBlank { "No se pudo comprobar por la conexión." }
                "NO PROBADA - $message. No se cambió el estado guardado."
            }
            else -> {
                val message = result.error.ifBlank { "No se puede usar esta API ahora." }
                settings?.markWait(clean, message)
                "EN ESPERA - $message"
            }
        }

        return false to "$label: $detail"
    }

    fun testApiConfigs(apiConfigs: List<NetApiConfig>): Pair<Boolean, String> {
        val cleanConfigs = apiConfigs
            .mapIndexed { index, config -> config.clean(index) }
            .filter { it.apiKey.isNotBlank() }
            .distinctBy { "${it.provider.id}|${it.apiKey}|${it.model}" }

        if (cleanConfigs.isEmpty()) return false to "Agrega por lo menos una API Key."
        if (!hasValidatedInternet()) {
            return false to "Sin conexión a Internet. No se probó ninguna API y no se cambió su estado."
        }

        val settings = context?.let { GeminiSettings(it) }
        var firstGoodIndex = -1
        var okCount = 0
        var networkCount = 0
        var testedCount = 0
        val lines = mutableListOf<String>()

        for ((index, config) in cleanConfigs.withIndex()) {
            val formatError = getApiConfigError(config)
            if (formatError != null) {
                testedCount += 1
                settings?.markError(config, formatError)
                lines.add("${config.name} (${config.provider.label}): ERROR - $formatError")
                continue
            }

            testedCount += 1
            val result = askProvider(config, "Responde solo: OK", testing = true)

            // Una respuesta HTTP exitosa, con JSON válido y contenido no vacío, demuestra
            // que la clave, el proveedor y el modelo pueden usarse. No exigimos una frase exacta.
            if (result.ok) {
                okCount += 1
                if (firstGoodIndex < 0) firstGoodIndex = index
                settings?.markReady(config)
                lines.add("${config.name} (${config.provider.label}): FUNCIONANDO")
                continue
            }

            when (result.failureType) {
                FailureType.LIMIT_DAILY -> {
                    settings?.markLimit(config, result.error)
                    lines.add("${config.name} (${config.provider.label}): LIMITE / SIN CREDITOS")
                }
                FailureType.LIMIT_MINUTE -> {
                    settings?.markMinuteLimit(config, result.error)
                    lines.add("${config.name} (${config.provider.label}): EN ESPERA - limite por minuto")
                }
                FailureType.LIMIT_UNKNOWN -> {
                    settings?.markUnknownLimit(config, result.error)
                    lines.add("${config.name} (${config.provider.label}): EN ESPERA - limite temporal")
                }
                FailureType.INVALID -> {
                    val message = result.error.ifBlank { "API invalida, modelo incorrecto o sin permiso." }
                    settings?.markError(config, message)
                    lines.add("${config.name} (${config.provider.label}): ERROR - $message")
                }
                FailureType.TEMPORARY -> {
                    val message = result.error.ifBlank { "Error temporal del servidor." }
                    settings?.markWait(config, message)
                    lines.add("${config.name} (${config.provider.label}): EN ESPERA - $message")
                }
                FailureType.NETWORK -> {
                    networkCount += 1
                    lines.add("${config.name} (${config.provider.label}): NO PROBADA - ${result.error.ifBlank { "conexion inestable" }}")
                }
                else -> {
                    val message = result.error.ifBlank { "No se puede usar esta API ahora." }
                    settings?.markWait(config, message)
                    lines.add("${config.name} (${config.provider.label}): EN ESPERA - $message")
                }
            }
        }

        if (firstGoodIndex >= 0) {
            settings?.saveLastGoodIndex(firstGoodIndex)
        }

        val report = settings?.getStatusReportForConfigs(cleanConfigs).orEmpty()
        val header = when {
            okCount > 0 -> "Listo: $okCount API(s) funcionando de $testedCount probadas."
            networkCount == testedCount && testedCount > 0 -> "No se pudo comprobar ninguna por la conexion. No se marcaron como claves invalidas."
            else -> "No hay API funcionando. Revisa las marcadas como ERROR, LIMITE o EN ESPERA."
        }

        val message = listOf(header, lines.joinToString("\n"), report)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return (okCount > 0) to message
    }

    private fun getApiConfigError(config: NetApiConfig): String? {
        val key = config.apiKey.trim()
        if (key.isBlank()) return "API vacia."
        if (key.length < 8) return "API demasiado corta o mal copiada."
        if (key.any { it.isWhitespace() }) return "API con espacios. Pegala completa sin espacios."
        if (!key.matches(Regex("^[A-Za-z0-9_\\-.]+$"))) return "API con caracteres no validos."
        if (config.model.isBlank()) return "Modelo vacio. Escribe el modelo o usa el recomendado."
        if (config.model.any { it.isWhitespace() }) return "Modelo con espacios. Copia el nombre exacto del modelo."
        return null
    }

    private fun askProvider(
        config: NetApiConfig,
        question: String,
        testing: Boolean = false,
        runtimeRequestId: Int? = null
    ): NetCallResult {
        return when (config.provider) {
            NetProvider.GEMINI -> callGemini(config, question, testing, runtimeRequestId)
            NetProvider.OPENROUTER -> callOpenAiCompatible(
                config = config,
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                question = question,
                testing = testing,
                runtimeRequestId = runtimeRequestId,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://geosusu.local",
                    "X-Title" to "Geo Susu JSON"
                )
            )
            NetProvider.MISTRAL -> callOpenAiCompatible(
                config = config,
                endpoint = "https://api.mistral.ai/v1/chat/completions",
                question = question,
                testing = testing,
                runtimeRequestId = runtimeRequestId
            )
            NetProvider.COHERE -> callCohere(config, question, testing, runtimeRequestId)
        }
    }

    private fun callGemini(
        config: NetApiConfig,
        question: String,
        testing: Boolean,
        runtimeRequestId: Int?
    ): NetCallResult {
        var connection: HttpURLConnection? = null
        val model = config.model.ifBlank { config.provider.defaultModel }

        return try {
            val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"
            )

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = if (testing) TEST_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
                readTimeout = if (testing) TEST_READ_TIMEOUT_MS else READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "GeoSusuJSON/163")
            }

            if (!registerRuntimeConnection(runtimeRequestId, connection)) {
                return cancelledResult(config.provider, model)
            }

            val body = buildGeminiBody(question, testing, model).toString()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                return cancelledResult(config.provider, model)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return parseProviderError(raw, code, config.provider, model)
            }

            val answer = parseGeminiText(raw)
            if (answer.isBlank()) {
                NetCallResult(false, "", "${config.provider.label} respondio vacio. API en espera temporal.", config.provider, model, true, FailureType.TEMPORARY)
            } else {
                NetCallResult(true, answer, "", config.provider, model)
            }
        } catch (e: Exception) {
            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                cancelledResult(config.provider, model)
            } else {
                networkResult(e, config.provider, model)
            }
        } finally {
            connection?.disconnect()
            clearRuntimeConnection(runtimeRequestId, connection)
        }
    }

    private fun callOpenAiCompatible(
        config: NetApiConfig,
        endpoint: String,
        question: String,
        testing: Boolean,
        runtimeRequestId: Int?,
        extraHeaders: Map<String, String> = emptyMap()
    ): NetCallResult {
        var connection: HttpURLConnection? = null
        val model = config.model.ifBlank { config.provider.defaultModel }

        return try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = if (testing) TEST_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
                readTimeout = if (testing) TEST_READ_TIMEOUT_MS else READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "GeoSusuJSON/163")
                extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            if (!registerRuntimeConnection(runtimeRequestId, connection)) {
                return cancelledResult(config.provider, model)
            }

            val body = buildChatBody(model, question, testing).toString()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                return cancelledResult(config.provider, model)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return parseProviderError(raw, code, config.provider, model)
            }

            val answer = parseChatCompletionsText(raw)
            if (answer.isBlank()) {
                NetCallResult(false, "", "${config.provider.label} respondio vacio. API en espera temporal.", config.provider, model, true, FailureType.TEMPORARY)
            } else {
                NetCallResult(true, answer, "", config.provider, model)
            }
        } catch (e: Exception) {
            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                cancelledResult(config.provider, model)
            } else {
                networkResult(e, config.provider, model)
            }
        } finally {
            connection?.disconnect()
            clearRuntimeConnection(runtimeRequestId, connection)
        }
    }

    private fun callCohere(
        config: NetApiConfig,
        question: String,
        testing: Boolean,
        runtimeRequestId: Int?
    ): NetCallResult {
        var connection: HttpURLConnection? = null
        val model = config.model.ifBlank { config.provider.defaultModel }

        return try {
            val url = URL("https://api.cohere.com/v2/chat")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = if (testing) TEST_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
                readTimeout = if (testing) TEST_READ_TIMEOUT_MS else READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "GeoSusuJSON/163")
            }

            if (!registerRuntimeConnection(runtimeRequestId, connection)) {
                return cancelledResult(config.provider, model)
            }

            val body = buildCohereBody(model, question, testing).toString()
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                return cancelledResult(config.provider, model)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return parseProviderError(raw, code, config.provider, model)
            }

            val answer = parseCohereText(raw)
            if (answer.isBlank()) {
                NetCallResult(false, "", "${config.provider.label} respondio vacio. API en espera temporal.", config.provider, model, true, FailureType.TEMPORARY)
            } else {
                NetCallResult(true, answer, "", config.provider, model)
            }
        } catch (e: Exception) {
            if (runtimeRequestId != null && !isRuntimeRequestActive(runtimeRequestId)) {
                cancelledResult(config.provider, model)
            } else {
                networkResult(e, config.provider, model)
            }
        } finally {
            connection?.disconnect()
            clearRuntimeConnection(runtimeRequestId, connection)
        }
    }

    private fun buildGeminiBody(question: String, testing: Boolean, model: String): JSONObject {
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", buildPrompt(question, testing)))
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", if (testing) 64 else 220)
                    put("temperature", if (testing) 0.0 else 0.2)
                    if (!testing && model.lowercase().contains("2.5")) {
                        put(
                            "thinkingConfig",
                            JSONObject().apply { put("thinkingBudget", 0) }
                        )
                    }
                }
            )
        }
    }

    private fun buildChatBody(model: String, question: String, testing: Boolean): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", buildSystemRules(testing, question)))
                    .put(JSONObject().put("role", "user").put("content", if (testing) question else cleanQuery(question)))
            )
            put("temperature", if (testing) 0.0 else 0.2)
            put("max_tokens", if (testing) 64 else 220)
            put("stream", false)
        }
    }

    private fun buildCohereBody(model: String, question: String, testing: Boolean): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", buildPrompt(question, testing))
                )
            )
            put("temperature", if (testing) 0.0 else 0.2)
            put("max_tokens", if (testing) 64 else 220)
        }
    }

    private fun buildPrompt(question: String, testing: Boolean): String {
        if (testing) return question
        return "${buildSystemRules(false, question)}\n\nPregunta: ${cleanQuery(question)}"
    }

    private fun buildSystemRules(testing: Boolean, question: String): String {
        if (testing) return "Responde solo: OK"

        val exerciseRule = when (classifyExercise(question)) {
            ExerciseType.TRUE_FALSE ->
                "La consulta es de verdadero o falso. Responde solamente: Verdadero o Falso. No agregues explicacion."
            ExerciseType.COMPLETE ->
                "La consulta pide completar una frase. Devuelve solamente la palabra o frase faltante, sin introduccion ni explicacion adicional."
            ExerciseType.MULTIPLE_CHOICE ->
                "La consulta tiene alternativas. Lee todas las opciones y responde solamente con la letra correcta, por ejemplo: B."
            ExerciseType.FORMULA ->
                "La consulta pide una formula o simbolo quimico. Responde solamente con la formula o simbolo, sin palabras adicionales."
            ExerciseType.DIRECT ->
                "La consulta es directa. Responde de forma breve y precisa."
        }

        return """
            Responde siempre en español, con una respuesta corta y directa. Contexto principal: minería, topografía, nivelación, cotas, BM, CP, VA, VI, VF, AI, levantamiento y replanteo.
            Fecha local del celular: ${currentDateForPrompt()}. Si preguntan "hoy", "este mes" o "este año", usa esa fecha local.

            Regla especial para esta consulta:
            $exerciseRule

            Reglas obligatorias:
            1. Entrega SOLO la respuesta final, sin análisis ni razonamiento interno.
            2. Nunca escribas THINK, THOUGHT, REASONING, ANALYSIS, reglas, pasos internos ni explicación del proceso.
            3. Si la pregunta es clara, responde aunque no sea de minería. No rechaces preguntas generales como fechas, días, cálculos simples o conceptos comunes.
            4. Nunca respondas "No entendí", "repite la pregunta" ni culpes al audio. Eso lo maneja la app antes de llamar a NET.
            5. Si no sabes con seguridad, di: No tengo una respuesta segura.
            6. Si preguntan fórmula química o símbolo químico, responde solo la fórmula o símbolo. Ejemplo: oro -> Au.
            7. Si preguntan tipos, clases, división, clasificación, lista, minerales o dicen "todos", responde solo los nombres principales separados por coma.
            8. Si preguntan "qué es", responde una sola oración corta.
            9. No saludes, no uses Markdown, no respondas en inglés y no termines con frases incompletas.
            10. Máximo 32 palabras, salvo listas de nombres o el texto indispensable de una alternativa.
        """.trimIndent()
    }

    private fun classifyExercise(rawQuestion: String): ExerciseType {
        val question = ResponseRepository.normalize(rawQuestion)
        return when {
            question.contains("verdadero o falso") ||
                question.startsWith("verdadero falso") ||
                question.contains("es verdadero o es falso") -> ExerciseType.TRUE_FALSE

            question.contains("completa la frase") ||
                question.contains("completar la frase") ||
                question.startsWith("completa ") ||
                question.startsWith("complete ") ||
                question.contains("palabra que falta") ||
                question.contains("frase que falta") -> ExerciseType.COMPLETE

            question.contains("alternativa correcta") ||
                question.contains("opcion correcta") ||
                question.contains("respuesta correcta") ||
                question.contains("inciso correcto") ||
                question.contains("cual alternativa") ||
                question.contains("cual opcion") ||
                question.contains("marca la respuesta") ||
                question.contains("elige la respuesta") -> ExerciseType.MULTIPLE_CHOICE

            question.contains("formula quimica") ||
                question.contains("simbolo quimico") ||
                question.contains("solo su formula") ||
                question.contains("solo la formula") ||
                question.contains("responde solo con la formula") -> ExerciseType.FORMULA

            else -> ExerciseType.DIRECT
        }
    }


    private fun currentDateForPrompt(): String {
        return try {
            SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "PE"))
                .format(Calendar.getInstance().time)
        } catch (_: Exception) {
            "fecha local no disponible"
        }
    }

    private fun parseGeminiText(raw: String): String {
        val obj = JSONObject(raw)
        val candidates = obj.optJSONArray("candidates") ?: return ""
        val partsText = StringBuilder()
        var finishReason = ""

        for (i in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(i) ?: continue
            finishReason = candidate.optString("finishReason").orEmpty()
            val content = candidate.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue

            for (j in 0 until parts.length()) {
                val text = parts.optJSONObject(j)?.optString("text").orEmpty().trim()
                appendCleanPart(partsText, text)
            }

            if (partsText.isNotBlank()) break
        }

        val clean = cleanAnswer(partsText.toString())
        return if (finishReason == "MAX_TOKENS" && looksIncompleteNetAnswer(clean)) {
            BAD_NET_ANSWER
        } else {
            clean
        }
    }

    private fun parseChatCompletionsText(raw: String): String {
        val obj = JSONObject(raw)
        val choices = obj.optJSONArray("choices") ?: return ""
        val partsText = StringBuilder()
        var finishReason = ""

        for (i in 0 until choices.length()) {
            val choice = choices.optJSONObject(i) ?: continue
            finishReason = choice.optString("finish_reason", choice.optString("finishReason"))
            val message = choice.optJSONObject("message") ?: choice.optJSONObject("delta") ?: continue
            val contentValue = message.opt("content") ?: continue
            appendJsonContent(partsText, contentValue)
            if (partsText.isNotBlank()) break
        }

        val clean = cleanAnswer(partsText.toString())
        return if ((finishReason == "length" || finishReason == "MAX_TOKENS") && looksIncompleteNetAnswer(clean)) {
            BAD_NET_ANSWER
        } else {
            clean
        }
    }

    private fun parseCohereText(raw: String): String {
        val obj = JSONObject(raw)
        val partsText = StringBuilder()
        val message = obj.optJSONObject("message")
        val contentValue = message?.opt("content") ?: obj.opt("text") ?: return ""
        appendJsonContent(partsText, contentValue)
        return cleanAnswer(partsText.toString())
    }

    private fun appendJsonContent(builder: StringBuilder, value: Any) {
        when (value) {
            is String -> appendCleanPart(builder, value.trim())
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    when (item) {
                        is String -> appendCleanPart(builder, item.trim())
                        is JSONObject -> appendCleanPart(
                            builder,
                            item.optString("text", item.optString("content")).trim()
                        )
                    }
                }
            }
            is JSONObject -> appendCleanPart(builder, value.optString("text", value.optString("content")).trim())
        }
    }

    private fun appendCleanPart(builder: StringBuilder, text: String) {
        if (text.isBlank()) return
        if (builder.isNotBlank()) {
            val current = builder.toString().trimEnd()
            val firstChar = text.firstOrNull()
            val joinsDirectly = current.endsWith(":") ||
                current.endsWith("-") ||
                firstChar == ',' ||
                firstChar == '.' ||
                firstChar == ';' ||
                firstChar == ':'
            builder.append(if (joinsDirectly) "" else " ")
        }
        builder.append(text)
    }

    private fun parseProviderError(raw: String, httpCode: Int, provider: NetProvider, model: String): NetCallResult {
        val message = extractErrorMessage(raw)
        val lower = "$message $raw".lowercase()

        return when {
            httpCode == 402 ||
                lower.contains("insufficient credits") || lower.contains("credit balance") ||
                lower.contains("no credits") || lower.contains("payment required") ->
                NetCallResult(false, "", "${provider.label}: sin creditos o pago requerido.", provider, model, true, FailureType.LIMIT_DAILY)

            lower.contains("organization has been restricted") ||
                lower.contains("organization is restricted") ||
                lower.contains("organization restricted") ->
                NetCallResult(
                    false,
                    "",
                    "${provider.label}: la organización de esta cuenta está restringida por el proveedor. La clave no puede usarse hasta que el proveedor quite la restricción.",
                    provider,
                    model,
                    true,
                    FailureType.INVALID
                )

            lower.contains("model_not_found") || lower.contains("model not found") ||
                lower.contains("invalid model") || lower.contains("does not exist") ||
                lower.contains("not a valid model") || lower.contains("unknown model") ->
                NetCallResult(false, "", "${provider.label}: modelo incorrecto o no disponible: $model", provider, model, true, FailureType.INVALID)

            httpCode == 401 || httpCode == 403 ||
                lower.contains("api key not valid") || lower.contains("invalid api key") ||
                lower.contains("incorrect api key") || lower.contains("missing api key") ||
                lower.contains("invalid key") || lower.contains("unauthorized") ||
                lower.contains("unauthenticated") || lower.contains("authentication failed") ||
                lower.contains("permission_denied") || lower.contains("permission denied") ||
                lower.contains("forbidden") ->
                NetCallResult(false, "", "${provider.label}: API Key invalida o sin permiso.", provider, model, true, FailureType.INVALID)

            lower.contains("per day") || lower.contains("perday") ||
                lower.contains("requestsperday") || lower.contains("rpd") ||
                lower.contains("daily quota") || lower.contains("daily limit") ->
                NetCallResult(false, "", "${provider.label}: limite diario. Se cambia a otra API.", provider, model, true, FailureType.LIMIT_DAILY)

            lower.contains("per minute") || lower.contains("perminute") ||
                lower.contains("requestsperminute") || lower.contains("tokensperminute") ||
                lower.contains("rpm") || lower.contains("tpm") ->
                NetCallResult(false, "", "${provider.label}: limite por minuto. Se cambia a otra API.", provider, model, true, FailureType.LIMIT_MINUTE)

            httpCode == 429 || lower.contains("quota") || lower.contains("rate limit") ||
                lower.contains("ratelimit") || lower.contains("too many requests") ||
                lower.contains("resource_exhausted") || lower.contains("limit exceeded") ->
                NetCallResult(false, "", "${provider.label}: limite temporal. Se cambia a otra API.", provider, model, true, FailureType.LIMIT_UNKNOWN)

            httpCode == 408 || httpCode == 409 || httpCode == 425 || httpCode in 500..599 ->
                NetCallResult(false, "", "${provider.label}: servidor ocupado o solicitud temporalmente no disponible.", provider, model, true, FailureType.TEMPORARY)

            httpCode == 400 || httpCode == 404 || httpCode == 405 || httpCode == 422 ->
                NetCallResult(
                    false,
                    "",
                    "${provider.label}: configuracion o modelo no aceptado. ${message.take(160)}".trim(),
                    provider,
                    model,
                    true,
                    FailureType.INVALID
                )

            httpCode in 400..499 ->
                NetCallResult(
                    false,
                    "",
                    "${provider.label}: solicitud rechazada. ${message.take(160)}".trim(),
                    provider,
                    model,
                    true,
                    FailureType.INVALID
                )

            message.isNotBlank() ->
                NetCallResult(false, "", "${provider.label}: ${message.take(220)}", provider, model, true, FailureType.OTHER)

            else -> NetCallResult(false, "", "${provider.label}: error de API. Revisa clave, modelo o internet.", provider, model, true, FailureType.OTHER)
        }
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            val error = obj.opt("error")
            when (error) {
                is JSONObject -> error.optString("message", error.optString("error", error.toString()))
                is String -> error
                else -> obj.optString("message", obj.optString("detail", raw.take(260)))
            }.trim()
        } catch (_: Exception) {
            raw.take(260).trim()
        }
    }

    private fun networkResult(e: Exception, provider: NetProvider, model: String): NetCallResult {
        val msg = (e.message ?: "error de conexión").lowercase(Locale.ROOT)
        return when (e) {
            is SocketTimeoutException -> NetCallResult(
                false, "", "${provider.label}: demoró demasiado. Queda en espera y se cambia a otra API.",
                provider, model, true, FailureType.TEMPORARY
            )
            is ConnectException -> NetCallResult(
                false, "", "${provider.label}: no aceptó la conexión. Queda en espera y se cambia a otra API.",
                provider, model, true, FailureType.TEMPORARY
            )
            is UnknownHostException -> NetCallResult(
                false, "", "Sin conexión estable a Internet. Revisa datos o Wi-Fi.",
                provider, model, true, FailureType.NETWORK
            )
            else -> when {
                msg.contains("timeout") || msg.contains("timed out") -> NetCallResult(
                    false, "", "${provider.label}: demoró demasiado. Se cambia a otra API.",
                    provider, model, true, FailureType.TEMPORARY
                )
                msg.contains("reset") -> NetCallResult(
                    false, "", "${provider.label}: conexión reiniciada. Se cambia a otra API.",
                    provider, model, true, FailureType.TEMPORARY
                )
                msg.contains("unable to resolve") || msg.contains("network is unreachable") -> NetCallResult(
                    false, "", "Sin conexión estable a Internet. Revisa datos o Wi-Fi.",
                    provider, model, true, FailureType.NETWORK
                )
                else -> NetCallResult(
                    false, "", "No se pudo conectar con ${provider.label}. Se probará otra API.",
                    provider, model, true, FailureType.TEMPORARY
                )
            }
        }
    }

    private fun beginRuntimeRequest(): Int {
        synchronized(connectionLock) {
            val id = runtimeRequestId.incrementAndGet()
            runCatching { activeRuntimeConnection?.disconnect() }
            activeRuntimeConnection = null
            return id
        }
    }

    private fun isRuntimeRequestActive(requestId: Int): Boolean {
        return runtimeRequestId.get() == requestId && !Thread.currentThread().isInterrupted
    }

    private fun registerRuntimeConnection(requestId: Int?, connection: HttpURLConnection?): Boolean {
        if (requestId == null || connection == null) return true
        synchronized(connectionLock) {
            if (!isRuntimeRequestActive(requestId)) {
                runCatching { connection.disconnect() }
                return false
            }
            activeRuntimeConnection = connection
            return true
        }
    }

    private fun clearRuntimeConnection(requestId: Int?, connection: HttpURLConnection? = null) {
        if (requestId == null) return
        synchronized(connectionLock) {
            if (runtimeRequestId.get() == requestId &&
                (connection == null || activeRuntimeConnection === connection)
            ) {
                activeRuntimeConnection = null
            }
        }
    }

    private fun cancelledResult(provider: NetProvider, model: String): NetCallResult {
        return NetCallResult(
            ok = false,
            text = "",
            error = "Consulta NET cancelada.",
            provider = provider,
            model = model,
            canTryNextApi = false,
            failureType = FailureType.CANCELLED
        )
    }

    private fun hasValidatedInternet(): Boolean {
        val appContext = context ?: return true
        return try {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    private fun netAnswerIssue(text: String, question: String): String? {
        if (!isUsefulNetAnswer(text, question)) return "Respuesta vacía, incompleta o con formato incorrecto."

        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("<html") || lower.contains("<!doctype") || lower.contains("http error")) {
            return "Respuesta técnica inválida."
        }

        val exerciseType = classifyExercise(question)
        if ((exerciseType == ExerciseType.DIRECT || exerciseType == ExerciseType.COMPLETE) &&
            looksClearlyEnglish(text)
        ) {
            return "Respuesta claramente en inglés."
        }

        return null
    }

    private fun looksClearlyEnglish(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-záéíóúüñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return false

        val words = normalized.split(" ").filter { it.isNotBlank() }
        val strongEnglishPhrases = listOf(
            "the answer is", "this is", "it is", "there are", "is defined as",
            "refers to", "according to", "in order to"
        )
        if (strongEnglishPhrases.any { normalized.contains(it) }) return true

        val englishMarkers = setOf(
            "the", "is", "are", "of", "to", "and", "in", "for", "with", "this", "that",
            "from", "by", "on", "as", "an", "its", "it", "can", "be", "which", "when"
        )
        val spanishMarkers = setOf(
            "el", "la", "los", "las", "es", "son", "de", "del", "que", "para", "por", "con",
            "una", "un", "en", "y", "se", "su", "al", "como", "cuando", "entre", "sobre"
        )

        val englishScore = words.count { it in englishMarkers }
        val spanishScore = words.count { it in spanishMarkers }
        return words.size >= 4 && englishScore >= 2 && englishScore > spanishScore + 1
    }

    private fun orderedConfigs(configs: List<NetApiConfig>, startIndex: Int): List<Pair<Int, NetApiConfig>> {
        if (configs.isEmpty()) return emptyList()
        val safeStart = startIndex.coerceIn(0, configs.size - 1)
        return configs.indices.map { offset ->
            val index = (safeStart + offset) % configs.size
            index to configs[index]
        }
    }

    private fun cleanQuery(raw: String): String {
        return raw.replace("\\s+".toRegex(), " ").trim()
    }

    private fun cleanAnswer(raw: String): String {
        var candidate = raw
            .replace("**", "")
            .replace("```", "")
            .trim()

        candidate = removeReasoningLeak(candidate)
            .replace("\\s+".toRegex(), " ")
            .trim()

        candidate = candidate
            .replace(Regex("^(Respuesta final|Respuesta|Final)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(Rpta|Resp)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        if (candidate.isBlank()) return BAD_NET_ANSWER
        if (looksLikeInternalLeak(candidate)) return BAD_NET_ANSWER

        candidate = trimToSafeNetAnswer(candidate)

        candidate = candidate.replace(Regex("\\s+"), " ").trim()
        if (candidate.length > MAX_SPOKEN_NET_CHARS) {
            candidate = candidate.take(MAX_SPOKEN_NET_CHARS).trimEnd(',', ';', ':', '-', ' ')
            if (candidate.lastOrNull() !in listOf('.', '!', '?')) candidate += "."
        }
        return candidate
    }


    private fun looksIncompleteNetAnswer(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return true
        if (clean == BAD_NET_ANSWER) return false
        if (clean.endsWith("...")) return true
        val words = clean.split(" ").filter { it.isNotBlank() }
        if (words.size <= 4 && clean.lastOrNull() !in listOf('.', '!', '?')) return true
        val lower = clean.lowercase()
        val badEndings = listOf(
            " es un", " es una", " es el", " es la", " son", " sirve para",
            " se usa para", " consiste en", " significa", " de", " para", " en"
        )
        return badEndings.any { lower.endsWith(it) }
    }

    private fun trimToSafeNetAnswer(text: String): String {
        var clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return BAD_NET_ANSWER

        val firstSentence = Regex("^(.+?[.!?])(?:\\s|$)").find(clean)?.groups?.get(1)?.value?.trim()
        if (!firstSentence.isNullOrBlank() && firstSentence.length >= 12) {
            clean = firstSentence
        }

        val words = clean.split(" ").filter { it.isNotBlank() }
        if (words.size > MAX_SPOKEN_NET_WORDS) {
            clean = words.take(MAX_SPOKEN_NET_WORDS).joinToString(" ").trimEnd(',', ';', ':', '-', ' ')
            if (clean.lastOrNull() !in listOf('.', '!', '?')) clean += "."
        }

        return clean
    }

    private fun removeReasoningLeak(raw: String): String {
        if (!looksLikeInternalLeak(raw)) return raw

        val finalLabel = Regex(
            "(?is)(respuesta final|respuesta|final|answer)\\s*:\\s*(.+)$"
        ).findAll(raw).lastOrNull()
        val possibleFinal = finalLabel?.groups?.get(2)?.value.orEmpty().trim()
        if (possibleFinal.isNotBlank() && !looksLikeInternalLeak(possibleFinal)) {
            return possibleFinal
        }

        return BAD_NET_ANSWER
    }

    private fun looksLikeInternalLeak(text: String): Boolean {
        val lower = text.lowercase()
        val badMarkers = listOf(
            "think:",
            "thought:",
            "reasoning:",
            "analysis:",
            "internal reasoning",
            "chain of thought",
            "the user is asking",
            "i need to",
            "given the strict rules",
            "given the context",
            "falls under",
            "rule #",
            "system rules",
            "reglas obligatorias",
            "contexto principal:",
            "pregunta:"
        )
        return badMarkers.any { lower.contains(it) }
    }

    private fun isUsefulNetAnswer(text: String, question: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        if (clean == BAD_NET_ANSWER) return false
        val lower = clean.lowercase()
            .replace("í", "i")
            .replace("é", "e")
        if (lower.contains("no entendi") || lower.contains("no entendi bien") || lower.contains("repite la pregunta")) return false
        if (looksLikeInternalLeak(clean)) return false

        return when (classifyExercise(question)) {
            ExerciseType.TRUE_FALSE -> {
                val normalized = ResponseRepository.normalize(clean)
                normalized == "verdadero" || normalized == "falso"
            }
            ExerciseType.COMPLETE -> {
                val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
                words.isNotEmpty() && words.size <= 20 && clean.length <= 140
            }
            ExerciseType.MULTIPLE_CHOICE -> {
                Regex("(?i)^\\s*[A-F]\\s*[.)]?\\s*$").matches(clean)
            }
            ExerciseType.FORMULA -> {
                val compact = clean.replace(" ", "")
                compact.length in 1..48 &&
                    Regex("^[A-Za-z0-9₀-₉⁰-⁹⁺⁻()\\[\\]{}.+−+\\-·•_/]+$").matches(compact)
            }
            ExerciseType.DIRECT -> !looksIncompleteNetAnswer(clean)
        }
    }

    private enum class ExerciseType {
        TRUE_FALSE, COMPLETE, MULTIPLE_CHOICE, FORMULA, DIRECT
    }

    companion object {
        fun requiresStructuredNetAnswer(rawQuestion: String): Boolean {
            val question = ResponseRepository.normalize(rawQuestion)
            return question.contains("verdadero o falso") ||
                question.startsWith("verdadero falso") ||
                question.contains("es verdadero o es falso") ||
                question.contains("completa la frase") ||
                question.contains("completar la frase") ||
                question.startsWith("completa ") ||
                question.startsWith("complete ") ||
                question.contains("palabra que falta") ||
                question.contains("frase que falta") ||
                question.contains("alternativa correcta") ||
                question.contains("opcion correcta") ||
                question.contains("respuesta correcta") ||
                question.contains("inciso correcto") ||
                question.contains("cual alternativa") ||
                question.contains("cual opcion") ||
                question.contains("marca la respuesta") ||
                question.contains("elige la respuesta") ||
                question.contains("formula quimica") ||
                question.contains("simbolo quimico") ||
                question.contains("solo su formula") ||
                question.contains("solo la formula") ||
                question.contains("responde solo con la formula")
        }

        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 9000
        private const val TEST_CONNECT_TIMEOUT_MS = 6000
        private const val TEST_READ_TIMEOUT_MS = 12000
        private const val BAD_ANSWER_COOLDOWN_MS = 10L * 60L * 1000L
        private const val MAX_CONSECUTIVE_NETWORK_FAILURES = 2
        private const val MAX_SPOKEN_NET_CHARS = 260
        private const val MAX_SPOKEN_NET_WORDS = 32
        private const val BAD_NET_ANSWER = "API_NO_UTIL"
    }
}
