package com.bph.geosusuaudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

enum class NetProvider(
    val id: String,
    val label: String,
    val defaultModel: String
) {
    GEMINI("gemini", "Gemini", "gemini-2.5-flash"),
    OPENROUTER("openrouter", "OpenRouter", "openrouter/free"),
    MISTRAL("mistral", "Mistral", "mistral-small-latest"),
    COHERE("cohere", "Cohere", "command-r7b-12-2024");

    companion object {
        fun fromId(value: String?): NetProvider? {
            val clean = value.orEmpty().trim().lowercase()
            return entries.firstOrNull { it.id == clean || it.label.lowercase() == clean }
        }

        fun detectFromApiKey(value: String?): NetProvider? {
            val clean = value.orEmpty().trim()
            if (clean.isBlank()) return null
            val lower = clean.lowercase()
            return when {
                clean.startsWith("AIza") -> GEMINI
                lower.startsWith("sk-or-v1-") || lower.startsWith("sk-or-") -> OPENROUTER
                lower.startsWith("co_") || lower.startsWith("cohere_") -> COHERE
                else -> null
            }
        }
    }
}

data class NetApiConfig(
    val name: String,
    val provider: NetProvider,
    val apiKey: String,
    val model: String
) {
    fun clean(index: Int): NetApiConfig {
        val cleanProvider = provider
        val cleanName = name.trim().ifBlank { "API ${index + 1}" }
        val cleanModel = model.trim().ifBlank { cleanProvider.defaultModel }
        return copy(
            name = cleanName.take(40),
            provider = cleanProvider,
            apiKey = apiKey.trim(),
            model = cleanModel.take(80)
        )
    }
}

class GeminiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("geo_susu_net_apis", Context.MODE_PRIVATE)
    private val oldGeminiPrefs = context.getSharedPreferences("geo_susu_gemini", Context.MODE_PRIVATE)

    fun getProviders(): List<NetProvider> = NetProvider.entries

    fun getApiKey(): String {
        return getApiConfigs().firstOrNull()?.apiKey.orEmpty()
    }

    fun getApiKeys(): List<String> {
        return getApiConfigs().map { it.apiKey }
    }

    fun getApiConfigs(): List<NetApiConfig> {
        val stored = prefs.getString(KEY_CONFIGS, null)
        if (stored != null) {
            val parsed = parseConfigs(stored)
            // v156: Groq fue retirado. Si existia una clave guardada, se elimina
            // de la configuracion sin convertirla por error en otro proveedor.
            if (Regex("\"provider\"\\s*:\\s*\"groq\"", RegexOption.IGNORE_CASE).containsMatchIn(stored)) {
                prefs.edit()
                    .putString(KEY_CONFIGS, configsToJson(parsed))
                    .putInt(KEY_LAST_GOOD_INDEX, 0)
                    .apply()
            }
            return parsed
        }

        // Migracion limpia desde v132: las claves antiguas eran Gemini.
        val oldKeys = readOldGeminiKeys()
        if (oldKeys.isNotEmpty()) {
            val migrated = oldKeys.mapIndexed { index, key ->
                NetApiConfig(
                    name = "API ${index + 1}",
                    provider = NetProvider.GEMINI,
                    apiKey = key,
                    model = NetProvider.GEMINI.defaultModel
                )
            }
            saveApiConfigs(migrated)
            return migrated
        }

        return emptyList()
    }

    fun saveApiKey(value: String) {
        saveApiConfigs(
            listOf(
                NetApiConfig(
                    name = "API 1",
                    provider = NetProvider.GEMINI,
                    apiKey = value,
                    model = NetProvider.GEMINI.defaultModel
                )
            )
        )
    }

    fun saveApiKeys(values: List<String>) {
        saveApiConfigs(
            values.mapIndexed { index, key ->
                NetApiConfig(
                    name = "API ${index + 1}",
                    provider = NetProvider.GEMINI,
                    apiKey = key,
                    model = NetProvider.GEMINI.defaultModel
                )
            }
        )
    }

    fun saveApiConfigs(values: List<NetApiConfig>) {
        val oldConfigs = getApiConfigsWithoutMigration()
        val oldIds = oldConfigs.map { configId(it) }.toSet()
        val previousActiveIndex = if (oldConfigs.isNotEmpty()) {
            prefs.getInt(KEY_LAST_GOOD_INDEX, 0).coerceIn(0, oldConfigs.size - 1)
        } else {
            0
        }
        val previousActiveId = oldConfigs.getOrNull(previousActiveIndex)?.let { configId(it) }

        val clean = values
            .mapIndexed { index, config -> config.clean(index) }
            .filter { it.apiKey.isNotBlank() }
            .distinctBy { "${it.provider.id}|${it.apiKey}|${it.model}" }

        val newLastGoodIndex = when {
            previousActiveId != null && clean.indexOfFirst { configId(it) == previousActiveId } >= 0 ->
                clean.indexOfFirst { configId(it) == previousActiveId }
            clean.indexOfFirst { getStatus(it) == STATUS_READY } >= 0 ->
                clean.indexOfFirst { getStatus(it) == STATUS_READY }
            else -> 0
        }

        prefs.edit()
            .putString(KEY_CONFIGS, configsToJson(clean))
            .putInt(KEY_LAST_GOOD_INDEX, newLastGoodIndex.coerceAtLeast(0))
            .apply()

        // Las APIs nuevas quedan sin probar para que no parezcan activas antes de validarse.
        clean.filter { configId(it) !in oldIds }.forEach { markUntested(it) }
    }

    fun hasApiKey(): Boolean {
        return getApiConfigs().isNotEmpty()
    }

    fun getLastGoodIndex(): Int {
        val size = getApiConfigs().size
        if (size <= 0) return 0
        val saved = prefs.getInt(KEY_LAST_GOOD_INDEX, 0)
        return saved.coerceIn(0, size - 1)
    }

    fun saveLastGoodIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_GOOD_INDEX, index.coerceAtLeast(0)).apply()
    }

    fun markUntested(apiKey: String, message: String = "Sin probar. Toca PROBAR API para validar.") {
        markUntested(defaultGeminiConfig(apiKey), message)
    }

    fun markUntested(config: NetApiConfig, message: String = "Sin probar. Toca PROBAR API para validar.") {
        val id = configId(config)
        prefs.edit()
            .putString("${KEY_STATUS_PREFIX}$id", STATUS_UNTESTED)
            .remove("${KEY_UNTIL_PREFIX}$id")
            .putString("${KEY_MESSAGE_PREFIX}$id", message.take(180))
            .apply()
    }

    fun markReady(apiKey: String) {
        markReady(defaultGeminiConfig(apiKey))
    }

    fun markReady(config: NetApiConfig) {
        val id = configId(config)
        prefs.edit()
            .putString("${KEY_STATUS_PREFIX}$id", STATUS_READY)
            .remove("${KEY_UNTIL_PREFIX}$id")
            .remove("${KEY_MESSAGE_PREFIX}$id")
            .apply()
    }

    fun markLimit(apiKey: String, message: String = "Llego al limite diario de uso.") {
        markLimit(defaultGeminiConfig(apiKey), message)
    }

    fun markLimit(config: NetApiConfig, message: String = "Llego al limite diario de uso.") {
        markBlocked(config, STATUS_LIMIT, System.currentTimeMillis() + DAILY_LIMIT_COOLDOWN_MS, message)
    }

    fun markWait(apiKey: String, message: String = "En espera temporal.", cooldownMs: Long = WAIT_COOLDOWN_MS) {
        markWait(defaultGeminiConfig(apiKey), message, cooldownMs)
    }

    fun markWait(config: NetApiConfig, message: String = "En espera temporal.", cooldownMs: Long = WAIT_COOLDOWN_MS) {
        markBlocked(config, STATUS_WAIT, System.currentTimeMillis() + cooldownMs, message)
    }

    fun markMinuteLimit(apiKey: String, message: String = "Limite por minuto. Espera unos minutos.") {
        markMinuteLimit(defaultGeminiConfig(apiKey), message)
    }

    fun markMinuteLimit(config: NetApiConfig, message: String = "Limite por minuto. Espera unos minutos.") {
        markWait(config, message, MINUTE_LIMIT_COOLDOWN_MS)
    }

    fun markUnknownLimit(apiKey: String, message: String = "Limite temporal. Espera o usa otra API.") {
        markUnknownLimit(defaultGeminiConfig(apiKey), message)
    }

    fun markUnknownLimit(config: NetApiConfig, message: String = "Limite temporal. Espera o usa otra API.") {
        markWait(config, message, UNKNOWN_LIMIT_COOLDOWN_MS)
    }

    fun markError(apiKey: String, message: String = "API invalida o sin permiso.") {
        markError(defaultGeminiConfig(apiKey), message)
    }

    fun markError(config: NetApiConfig, message: String = "API invalida, modelo incorrecto o sin permiso.") {
        markBlocked(config, STATUS_ERROR, 0L, message)
    }

    private fun markBlocked(config: NetApiConfig, status: String, untilMs: Long, message: String) {
        val id = configId(config)
        prefs.edit()
            .putString("${KEY_STATUS_PREFIX}$id", status)
            .putLong("${KEY_UNTIL_PREFIX}$id", untilMs)
            .putString("${KEY_MESSAGE_PREFIX}$id", message.take(180))
            .apply()
    }

    fun shouldSkip(apiKey: String): Boolean {
        return shouldSkip(defaultGeminiConfig(apiKey))
    }

    fun shouldSkip(config: NetApiConfig): Boolean {
        val status = getStatus(config)
        if (status == STATUS_ERROR) return true

        if (status == STATUS_LIMIT || status == STATUS_WAIT) {
            val until = getUntil(config)
            if (until > System.currentTimeMillis()) return true
            // Ya fue probada antes. Al terminar la espera vuelve automáticamente
            // a la rotación para que las APIs gratuitas puedan reutilizarse.
            markReady(config)
            return false
        }

        return false
    }

    fun getStatus(apiKey: String): String {
        return getStatus(defaultGeminiConfig(apiKey))
    }

    fun getStatus(config: NetApiConfig): String {
        val id = configId(config)
        return prefs.getString("${KEY_STATUS_PREFIX}$id", STATUS_UNTESTED) ?: STATUS_UNTESTED
    }

    fun isReady(config: NetApiConfig): Boolean {
        return getStatus(config) == STATUS_READY
    }

    fun getUntil(apiKey: String): Long {
        return getUntil(defaultGeminiConfig(apiKey))
    }

    fun getUntil(config: NetApiConfig): Long {
        val id = configId(config)
        return prefs.getLong("${KEY_UNTIL_PREFIX}$id", 0L)
    }

    fun getMessage(apiKey: String): String {
        return getMessage(defaultGeminiConfig(apiKey))
    }

    fun getMessage(config: NetApiConfig): String {
        val id = configId(config)
        return prefs.getString("${KEY_MESSAGE_PREFIX}$id", "").orEmpty()
    }

    fun getStatusLabel(apiKey: String, index: Int): String {
        return getStatusLabel(defaultGeminiConfig(apiKey), index)
    }

    fun getStatusLabel(config: NetApiConfig, index: Int): String {
        val cleanConfig = config.clean(index)
        shouldSkip(cleanConfig)

        val status = getStatus(cleanConfig)
        val name = "${cleanConfig.name.ifBlank { "API ${index + 1}" }} - ${cleanConfig.provider.label}${maskedSuffix(cleanConfig.apiKey)}"
        return when (status) {
            STATUS_LIMIT -> "$name: LIMITE DIARIO (${remainingText(getUntil(cleanConfig))})"
            STATUS_WAIT -> "$name: EN ESPERA (${remainingText(getUntil(cleanConfig))})"
            STATUS_ERROR -> "$name: ERROR - ${getMessage(cleanConfig).ifBlank { "no se puede usar" }}"
            STATUS_UNTESTED -> {
                val message = getMessage(cleanConfig).trim()
                val defaultMessage = message.isBlank() || message.startsWith("Sin probar", ignoreCase = true)
                if (defaultMessage) "$name: SIN PROBAR" else "$name: NO COMPROBADA - $message"
            }
            else -> {
                val activeIndex = getLastGoodIndex()
                if (index == activeIndex) "$name: ACTIVA" else "$name: DISPONIBLE"
            }
        }
    }

    fun getStatusReport(keys: List<String> = getApiKeys()): String {
        val configs = keys.mapIndexed { index, key -> defaultGeminiConfig(key).copy(name = "API ${index + 1}") }
        return getStatusReportForConfigs(configs)
    }

    fun getStatusReportForConfigs(configs: List<NetApiConfig> = getApiConfigs()): String {
        if (configs.isEmpty()) return "Sin API guardada."

        configs.forEach { shouldSkip(it) }

        val ready = configs.count { getStatus(it) == STATUS_READY }
        val untested = configs.count { getStatus(it) == STATUS_UNTESTED }
        val wait = configs.count { getStatus(it) == STATUS_WAIT }
        val limit = configs.count { getStatus(it) == STATUS_LIMIT }
        val error = configs.count { getStatus(it) == STATUS_ERROR }

        val summary = "Resumen: disponibles $ready | sin probar $untested | en espera $wait | limite $limit | error $error"
        val detail = configs.mapIndexed { index, config -> getStatusLabel(config, index) }.joinToString("\n")
        return "$summary\n$detail"
    }

    fun clearBlockedStatuses(configs: List<NetApiConfig> = getApiConfigs()): Int {
        var cleared = 0
        configs.forEachIndexed { index, config ->
            val clean = config.clean(index)
            val status = getStatus(clean)
            if (status == STATUS_WAIT || status == STATUS_LIMIT || status == STATUS_ERROR) {
                markUntested(clean, "Estado limpiado. Toca PROBAR API para validar.")
                cleared += 1
            }
        }
        return cleared
    }

    fun forgetConfigStatus(config: NetApiConfig) {
        val id = configId(config)
        prefs.edit()
            .remove("${KEY_STATUS_PREFIX}$id")
            .remove("${KEY_UNTIL_PREFIX}$id")
            .remove("${KEY_MESSAGE_PREFIX}$id")
            .apply()
    }

    fun configId(config: NetApiConfig): String {
        val clean = config.clean(0)
        return "${clean.provider.id}|${clean.model}|${clean.apiKey}".hashCode().toString()
    }

    private fun defaultGeminiConfig(apiKey: String): NetApiConfig {
        return NetApiConfig("API", NetProvider.GEMINI, apiKey, NetProvider.GEMINI.defaultModel)
    }

    private fun getApiConfigsWithoutMigration(): List<NetApiConfig> {
        val stored = prefs.getString(KEY_CONFIGS, null)
        if (stored.isNullOrBlank()) return emptyList()
        return parseConfigs(stored)
    }

    private fun parseConfigs(raw: String): List<NetApiConfig> {
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<NetApiConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val providerId = obj.optString("provider").trim()
                val provider = NetProvider.fromId(providerId) ?: continue
                val apiKey = obj.optString("apiKey").trim()
                if (apiKey.isBlank()) continue
                result.add(
                    NetApiConfig(
                        name = obj.optString("name").ifBlank { "API ${i + 1}" },
                        provider = provider,
                        apiKey = apiKey,
                        model = obj.optString("model").ifBlank { provider.defaultModel }
                    ).clean(i)
                )
            }
            result.distinctBy { "${it.provider.id}|${it.apiKey}|${it.model}" }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun configsToJson(configs: List<NetApiConfig>): String {
        val array = JSONArray()
        configs.forEachIndexed { index, config ->
            val clean = config.clean(index)
            array.put(
                JSONObject().apply {
                    put("name", clean.name)
                    put("provider", clean.provider.id)
                    put("apiKey", clean.apiKey)
                    put("model", clean.model)
                }
            )
        }
        return array.toString()
    }

    private fun readOldGeminiKeys(): List<String> {
        val stored = oldGeminiPrefs.getString(OLD_KEY_API_KEYS, null)
        if (!stored.isNullOrBlank()) {
            return stored
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

        val oldKey = oldGeminiPrefs.getString(OLD_KEY_API_KEY, "").orEmpty().trim()
        return if (oldKey.isBlank()) emptyList() else listOf(oldKey)
    }

    private fun maskedSuffix(apiKey: String): String {
        val clean = apiKey.trim()
        if (clean.length < 6) return ""
        return " (...${clean.takeLast(4)})"
    }

    private fun remainingText(untilMs: Long): String {
        val diff = untilMs - System.currentTimeMillis()
        if (diff <= 0L) return "lista"
        val minutes = max(1L, diff / 60000L)
        val hours = minutes / 60L
        val mins = minutes % 60L
        return if (hours > 0L) "${hours}h ${mins}m" else "${minutes}m"
    }

    companion object {
        private const val KEY_CONFIGS = "net_api_configs_v133"
        private const val KEY_LAST_GOOD_INDEX = "net_last_good_index_v133"
        private const val KEY_STATUS_PREFIX = "net_api_status_"
        private const val KEY_UNTIL_PREFIX = "net_api_until_"
        private const val KEY_MESSAGE_PREFIX = "net_api_message_"

        private const val OLD_KEY_API_KEY = "gemini_api_key"
        private const val OLD_KEY_API_KEYS = "gemini_api_keys"

        private const val STATUS_READY = "ready"
        private const val STATUS_UNTESTED = "untested"
        private const val STATUS_LIMIT = "limit"
        private const val STATUS_WAIT = "wait"
        private const val STATUS_ERROR = "error"

        private const val DAILY_LIMIT_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        private const val MINUTE_LIMIT_COOLDOWN_MS = 10L * 60L * 1000L
        private const val UNKNOWN_LIMIT_COOLDOWN_MS = 60L * 60L * 1000L
        private const val WAIT_COOLDOWN_MS = 10L * 60L * 1000L
    }
}
