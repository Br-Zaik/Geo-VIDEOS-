package com.bph.geosusuaudio

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class JsonKnowledgeRepository(private val context: Context) {

    data class KnowledgeItem(
        val id: String,
        val tema: String,
        val titulo: String,
        val contenido: String,
        val keywords: List<String>,
        val normalizedId: String,
        val normalizedTema: String,
        val normalizedTitulo: String,
        val normalizedContenido: String,
        val normalizedKeywords: List<String>,
        val normalizedQuestions: List<String>,
        val normalizedVariants: List<String>,
        val normalizedAliases: List<String>,
        val normalizedStrongPhrases: List<String>,
        val searchWords: Set<String>,
        val meta: String
    )

    private data class RepositoryData(
        val items: List<KnowledgeItem>,
        val exactIndex: Map<String, List<KnowledgeItem>>,
        val invertedIndex: Map<String, List<KnowledgeItem>>,
        val documentFrequency: Map<String, Int>
    )

    private data class ScoredItem(
        val item: KnowledgeItem,
        val score: Int,
        val phraseScore: Int,
        val subjectCoverage: Double,
        val hasDiscriminativeEvidence: Boolean
    )

    // Consulta la cache compartida en cada busqueda. Es barata cuando el archivo no
    // cambia y permite que MainActivity y los servicios vean un JSON recien actualizado.
    private fun currentData(): RepositoryData = loadDataCached()

    val totalItems: Int get() = currentData().items.size

    fun recognitionHints(limit: Int = 40): ArrayList<String> {
        if (limit <= 0) return arrayListOf()
        return try {
            val hints = linkedSetOf<String>()
            for (item in currentData().items) {
                if (item.titulo.isNotBlank()) hints.add(item.titulo)
                item.normalizedQuestions.take(2).forEach { if (it.isNotBlank()) hints.add(it) }
                item.keywords.take(3).forEach { if (it.isNotBlank()) hints.add(it) }
                if (hints.size >= limit) break
            }
            ArrayList(hints.take(limit))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron preparar sugerencias de voz desde el JSON", e)
            arrayListOf()
        }
    }

    fun findBest(rawQuestion: String): ResponseRepository.MatchResult {
        return try {
            findBestInternal(rawQuestion)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al resolver una pregunta desde el JSON", e)
            ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        }
    }

    /**
     * Devuelve un porcentaje real de coincidencia (0 a 100). Solo entrega una respuesta
     * cuando la coincidencia es suficientemente alta y no es ambigua. No aprende ni
     * guarda preguntas: siempre trabaja exclusivamente con el JSON activo.
     */
    private fun findBestInternal(rawQuestion: String): ResponseRepository.MatchResult {
        val question = normalizeForJson(rawQuestion)
        val data = currentData()
        if (question.length < 2 || data.items.isEmpty()) {
            return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        }

        val qWords = keyWords(question)
        if (qWords.isEmpty()) {
            return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        }
        if (qWords.size == 1 && qWords.first() in vagueSingleWords) {
            return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        }

        // Una frase exacta solo se acepta si identifica un unico registro. Si la misma
        // frase existe en varios registros, se considera ambigua en vez de elegir el primero.
        for (form in queryFormsForExact(question, qWords)) {
            val exactItems = data.exactIndex[form].orEmpty().distinctBy { it.id }
            if (exactItems.size == 1) {
                val item = exactItems.first()
                return ResponseRepository.MatchResult(buildAnswer(item), "json_${item.id}", 100)
            }
        }

        val candidates = candidateItems(qWords, data)
        if (candidates.isEmpty()) {
            return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        }

        val scored = candidates
            .map { scoreItem(question, qWords, it, data) }
            .sortedByDescending { it.score }

        val best = scored.firstOrNull()
            ?: return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", 0)
        val secondScore = scored.getOrNull(1)?.score ?: 0

        val minimum = when {
            qWords.size <= 1 -> 96
            qWords.size == 2 -> 88
            else -> 84
        }

        // Las frases cortas o genericas necesitan una coincidencia casi exacta. Esto evita
        // que una frase corta o incompleta active una respuesta cualquiera solo por
        // compartir una o dos palabras generales.
        val genericWithoutEvidence = !best.hasDiscriminativeEvidence && best.phraseScore < 92
        val weakCoverage = best.subjectCoverage < when {
            qWords.size <= 2 -> 0.90
            else -> 0.66
        }
        val ambiguous = best.score < 96 && best.score - secondScore < 8

        if (best.score < minimum || genericWithoutEvidence || weakCoverage || ambiguous) {
            return ResponseRepository.MatchResult(JSON_NOT_FOUND, "none", best.score.coerceIn(0, 100))
        }

        return ResponseRepository.MatchResult(
            buildAnswer(best.item),
            "json_${best.item.id}",
            best.score.coerceIn(0, 100)
        )
    }

    private fun scoreItem(
        question: String,
        qWords: List<String>,
        item: KnowledgeItem,
        data: RepositoryData
    ): ScoredItem {
        val phraseScores = item.normalizedStrongPhrases.map { phrase ->
            phraseSimilarity(question, qWords, phrase)
        }
        val phraseScore = phraseScores.maxOrNull() ?: 0
        val titleScore = phraseSimilarity(question, qWords, item.normalizedTitulo)
        val keywordScore = item.normalizedKeywords
            .maxOfOrNull { phraseSimilarity(question, qWords, it) }
            ?: 0

        val itemWords = item.searchWords
        val subjectWords = qWords.filter { it !in allIntentWords }
        val matchedSubjectWords = subjectWords.count { queryWord ->
            itemWords.any { itemWord -> wordsEquivalent(queryWord, itemWord) }
        }
        val subjectCoverage = if (subjectWords.isEmpty()) 0.0
        else matchedSubjectWords.toDouble() / subjectWords.size

        var score = max(phraseScore, max((titleScore * 0.96).roundToInt(), (keywordScore * 0.88).roundToInt()))
        score += intentAdjustment(question, item)

        if (subjectCoverage >= 0.99) score += 3
        if (subjectCoverage < 0.50) score -= 18
        else if (subjectCoverage < 0.66) score -= 8

        val discriminativeLimit = max(2, (data.items.size * 0.35).roundToInt())
        val hasDiscriminativeEvidence = subjectWords.any { word ->
            (data.documentFrequency[word] ?: Int.MAX_VALUE) <= discriminativeLimit &&
                itemWords.any { wordsEquivalent(word, it) }
        }

        return ScoredItem(
            item = item,
            score = score.coerceIn(0, 100),
            phraseScore = phraseScore,
            subjectCoverage = subjectCoverage,
            hasDiscriminativeEvidence = hasDiscriminativeEvidence
        )
    }

    private fun phraseSimilarity(question: String, qWords: List<String>, phrase: String): Int {
        val cleanPhrase = normalizeForJson(phrase)
        if (cleanPhrase.isBlank()) return 0
        if (question == cleanPhrase) return 100

        val pWords = keyWords(cleanPhrase)
        if (pWords.isEmpty()) return 0

        val remaining = pWords.toMutableList()
        var matchedWeight = 0.0
        for (qWord in qWords) {
            var bestIndex = -1
            var bestSimilarity = 0.0
            for (i in remaining.indices) {
                val similarity = tokenSimilarity(qWord, remaining[i])
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestIndex = i
                }
            }

            val threshold = when {
                qWord.length <= 3 -> 0.88
                qWord.length <= 5 -> 0.82
                else -> 0.76
            }
            if (bestIndex >= 0 && bestSimilarity >= threshold) {
                matchedWeight += if (bestSimilarity >= 0.999) 1.0 else bestSimilarity * 0.90
                remaining.removeAt(bestIndex)
            }
        }

        val qCoverage = (matchedWeight / qWords.size).coerceIn(0.0, 1.0)
        val pCoverage = (matchedWeight / pWords.size).coerceIn(0.0, 1.0)
        val f1 = if (qCoverage + pCoverage == 0.0) 0.0
        else (2.0 * qCoverage * pCoverage) / (qCoverage + pCoverage)
        val charSimilarity = normalizedStringSimilarity(question, cleanPhrase)

        var score = (f1 * 78.0 + charSimilarity * 12.0 + minOf(qCoverage, pCoverage) * 10.0).roundToInt()

        val queryCore = qWords.joinToString(" ")
        val phraseCore = pWords.joinToString(" ")
        if (queryCore.length >= 5 && (phraseCore.contains(queryCore) || queryCore.contains(phraseCore))) {
            score = max(score, (82 + minOf(qCoverage, pCoverage) * 14.0).roundToInt())
        }

        return score.coerceIn(0, 100)
    }

    private fun intentAdjustment(question: String, item: KnowledgeItem): Int {
        val itemText = listOf(
            item.normalizedTitulo,
            item.normalizedStrongPhrases.joinToString(" "),
            item.normalizedKeywords.joinToString(" "),
            item.normalizedContenido
        ).joinToString(" ")

        var adjustment = 0
        for (group in intentGroups) {
            val asksIntent = group.any { containsWholeWord(question, it) }
            if (!asksIntent) continue

            val itemHasIntent = group.any { containsWholeWord(itemText, it) }
            adjustment += if (itemHasIntent) 4 else -16
        }
        return adjustment.coerceIn(-28, 8)
    }

    private fun containsWholeWord(text: String, term: String): Boolean {
        val clean = normalizeForJson(term)
        if (clean.isBlank()) return false
        return " $text ".contains(" $clean ")
    }

    private fun wordsEquivalent(a: String, b: String): Boolean {
        if (a == b) return true
        val minLength = minOf(a.length, b.length)
        if (minLength < 4) return false
        val threshold = if (minLength <= 5) 0.84 else 0.78
        return tokenSimilarity(a, b) >= threshold
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val maxLength = max(a.length, b.length)
        return 1.0 - levenshteinDistance(a, b).toDouble() / maxLength
    }

    private fun normalizedStringSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val maxLength = max(a.length, b.length)
        return (1.0 - levenshteinDistance(a, b).toDouble() / maxLength).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val temp = previous
            previous = current
            current = temp
        }
        return previous[b.length]
    }

    private fun buildExactIndex(source: List<KnowledgeItem>): Map<String, List<KnowledgeItem>> {
        val map = LinkedHashMap<String, MutableList<KnowledgeItem>>()
        for (item in source) {
            for (phrase in item.normalizedStrongPhrases) {
                for (candidate in queryForms(phrase)) {
                    val coreWords = keyWords(candidate)
                    val compactCore = coreWords.joinToString(" ")
                    if (candidate.length >= 3 && candidate !in vagueSingleWords) {
                        map.getOrPut(candidate) { mutableListOf() }.add(item)
                    }
                    if (compactCore.length >= 3 && (coreWords.size >= 2 || compactCore !in vagueSingleWords)) {
                        map.getOrPut(compactCore) { mutableListOf() }.add(item)
                    }
                }
            }
        }
        return map.mapValues { (_, items) -> items.distinctBy { it.id } }
    }

    private fun buildInvertedIndex(source: List<KnowledgeItem>): Map<String, List<KnowledgeItem>> {
        val map = LinkedHashMap<String, LinkedHashSet<KnowledgeItem>>()
        for (item in source) {
            for (word in item.searchWords) {
                map.getOrPut(word) { linkedSetOf() }.add(item)
            }
        }
        return map.mapValues { it.value.toList() }
    }

    private fun buildDocumentFrequency(source: List<KnowledgeItem>): Map<String, Int> {
        val frequency = LinkedHashMap<String, Int>()
        for (item in source) {
            for (word in item.searchWords) {
                frequency[word] = (frequency[word] ?: 0) + 1
            }
        }
        return frequency
    }

    private fun candidateItems(qWords: List<String>, data: RepositoryData): List<KnowledgeItem> {
        val candidates = LinkedHashSet<KnowledgeItem>()
        for (word in qWords.distinct()) {
            data.invertedIndex[word]?.let { candidates.addAll(it) }
        }

        // Para errores pequenos de Google (por ejemplo una silaba cambiada), busca una
        // palabra cercana en el indice. No se compara contra todo el JSON.
        if (candidates.size < 3) {
            for (word in qWords) {
                if (word.length < 5) continue
                data.invertedIndex.keys
                    .asSequence()
                    .filter { indexed -> wordsEquivalent(word, indexed) }
                    .take(8)
                    .forEach { indexed -> data.invertedIndex[indexed]?.let { candidates.addAll(it) } }
            }
        }

        return candidates.toList()
    }

    private fun buildAnswer(item: KnowledgeItem): String {
        val clean = item.contenido
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        val limited = clean.take(540).trim()
        return if (clean.length > 540) "$limited..." else limited
    }

    private fun loadDataCached(): RepositoryData {
        val store = JsonKnowledgeStore(context)
        val cacheKey = store.cacheKey()
        synchronized(cacheLock) {
            cachedData?.let { cached ->
                if (cachedKey == cacheKey) return cached
            }
        }

        val items = loadItems()
        val built = RepositoryData(
            items = items,
            exactIndex = buildExactIndex(items),
            invertedIndex = buildInvertedIndex(items),
            documentFrequency = buildDocumentFrequency(items)
        )

        synchronized(cacheLock) {
            cachedKey = cacheKey
            cachedData = built
        }
        return built
    }

    private fun loadItems(): List<KnowledgeItem> {
        val store = JsonKnowledgeStore(context)
        val raw = store.readJson()
        if (raw.isBlank()) return emptyList()

        return try {
            val trimmed = raw.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("temas")
                        ?: obj.optJSONArray("items")
                        ?: obj.optJSONArray("data")
                        ?: JSONArray()
                }
            }

            val result = mutableListOf<KnowledgeItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val tema = obj.optString("tema", obj.optString("categoria", "General")).trim()
                val titulo = obj.optString(
                    "titulo",
                    obj.optString("pregunta", obj.optString("nombre", tema))
                ).trim()
                val contenido = obj.optString(
                    "contenido",
                    obj.optString("respuesta", obj.optString("answer", ""))
                ).trim()
                if (contenido.isBlank()) continue

                val id = obj.optString("id", "item_${i + 1}").trim().ifBlank { "item_${i + 1}" }
                val questions = readStringArray(obj, "preguntas") +
                    listOfNotNull(obj.optString("pregunta").trim().takeIf { it.isNotBlank() })
                val variants = readStringArray(obj, "variantes")
                val aliases = readStringArray(obj, "aliases")
                val keywords = readStringArray(obj, "keywords") + readStringArray(obj, "palabrasClave")

                val cleanKeywords = keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val normalizedId = normalizeForJson(id.replace("_", " "))
                val normalizedTema = normalizeForJson(tema)
                val normalizedTitulo = normalizeForJson(titulo)
                val normalizedContenido = normalizeForJson(contenido.take(1200))
                val normalizedQuestions = normalizeList(questions)
                val normalizedVariants = normalizeList(variants)
                val normalizedAliases = normalizeList(aliases)
                val normalizedKeywords = normalizeList(cleanKeywords).filter { it.length >= 2 }

                val strongSeed = listOf(normalizedTitulo) +
                    normalizedQuestions + normalizedVariants + normalizedAliases
                val normalizedStrongPhrases = strongSeed
                    .map { normalizeForJson(it) }
                    .filter { it.length >= 2 }
                    .distinct()

                val searchSeed = listOf(normalizedId, normalizedTitulo) +
                    normalizedStrongPhrases + normalizedKeywords
                val searchWords = searchSeed.flatMap { keyWords(it) }.toSet()
                val meta = listOf(
                    normalizedId,
                    normalizedTema,
                    normalizedTitulo,
                    normalizedStrongPhrases.joinToString(" "),
                    normalizedKeywords.joinToString(" ")
                ).joinToString(" ")

                result.add(
                    KnowledgeItem(
                        id = id,
                        tema = tema.ifBlank { "General" },
                        titulo = titulo.ifBlank { tema.ifBlank { "Tema ${i + 1}" } },
                        contenido = contenido,
                        keywords = cleanKeywords,
                        normalizedId = normalizedId,
                        normalizedTema = normalizedTema,
                        normalizedTitulo = normalizedTitulo,
                        normalizedContenido = normalizedContenido,
                        normalizedKeywords = normalizedKeywords,
                        normalizedQuestions = normalizedQuestions,
                        normalizedVariants = normalizedVariants,
                        normalizedAliases = normalizedAliases,
                        normalizedStrongPhrases = normalizedStrongPhrases,
                        searchWords = searchWords,
                        meta = meta
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar el JSON activo", e)
            emptyList()
        }
    }

    private fun normalizeList(values: List<String>): List<String> {
        return values
            .map { normalizeForJson(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun readStringArray(obj: JSONObject, key: String): List<String> {
        val value = obj.opt(key) ?: return emptyList()
        val list = mutableListOf<String>()
        if (value is JSONArray) {
            for (i in 0 until value.length()) {
                val text = value.optString(i).trim()
                if (text.isNotBlank()) list.add(text)
            }
            return list
        }

        val text = value.toString().trim()
        if (text.isBlank() || text == "null") return emptyList()
        return text.split("|", ";", ",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun queryFormsForExact(text: String, qWords: List<String>): List<String> {
        val result = linkedSetOf<String>()
        val clean = normalizeForJson(text)
        if (clean.isNotBlank()) result.add(clean)
        val core = qWords.joinToString(" ")
        if (core.isNotBlank()) result.add(core)
        return result.toList()
    }

    private fun queryForms(text: String): List<String> {
        val result = linkedSetOf<String>()
        val clean = normalizeForJson(text)
        if (clean.isNotBlank()) result.add(clean)
        val core = keyWords(clean).joinToString(" ")
        if (core.isNotBlank()) result.add(core)
        return result.toList()
    }

    private fun keyWords(text: String): List<String> {
        return text.split(" ")
            .map { canonicalWord(it.trim()) }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
            .take(16)
    }

    private fun normalizeForJson(text: String): String = ResponseRepository.normalize(text)

    private fun canonicalWord(word: String): String {
        val normalized = when (word) {
            "paga", "pagan", "pagar", "pagado", "pagada", "pague", "paguen" -> "pago"
            "usa", "usar", "usan", "usado", "utiliza", "utilizan", "utilizar", "utilizacion" -> "uso"
            "distribuye", "distribuyen", "distribuir", "distribucion", "reparte", "reparten", "repartir", "reparto" -> "distribucion"
            "reciben", "recibir", "recibido" -> "recibe"
            "corresponden", "corresponder" -> "corresponde"
            else -> word
        }
        return if (normalized.length > 6 && normalized.endsWith("s") && !normalized.endsWith("is")) {
            normalized.dropLast(1)
        } else {
            normalized
        }
    }

    companion object {
        private const val TAG = "GeoSusuJson"
        const val JSON_EMPTY = "No encontrado"
        const val JSON_NOT_FOUND = "No encontrado"

        private val cacheLock = Any()
        @Volatile private var cachedKey: String = ""
        @Volatile private var cachedData: RepositoryData? = null

        /** Libera solo los indices reconstruibles del JSON cuando Android pide memoria. */
        fun clearMemoryCache() {
            synchronized(cacheLock) {
                cachedKey = ""
                cachedData = null
            }
        }

        private val stopWords = setOf(
            "que", "q", "ke", "k", "es", "son", "ser", "un", "una", "el", "la", "los", "las",
            "de", "del", "en", "por", "para", "como", "cual", "cuales",
            "dime", "di", "explica", "explicame", "sobre", "pregunta", "oye",
            "al", "se", "si", "lo", "le", "me", "te", "pasa", "ocurre",
            "internet", "net", "json", "tema", "breve", "rapido", "respuesta", "habla", "susurra"
        )

        private val vagueSingleWords = setOf(
            "tipo", "tipos", "clase", "clases", "tema", "pregunta", "respuesta", "ejemplo", "ejemplos",
            "actividad", "proceso", "cosa", "cosas", "porcentaje", "tasa", "dias", "plazo"
        )

        private val intentGroups = listOf(
            setOf("define", "definicion", "concepto", "significa"),
            setOf("porcentaje", "porcentajes", "tasa", "tasas", "rango", "rangos", "tramo", "tramos"),
            setOf("dia", "dias", "plazo", "mensual", "trimestral", "anual"),
            setOf("quien", "quienes", "recibe", "reciben", "destinatario", "beneficiario", "beneficiarios"),
            setOf("donde", "lugar"),
            setOf("cuanto", "cuantos", "monto"),
            setOf("tipo", "tipos", "clase", "clases", "clasificacion"),
            setOf("uso", "usar", "usa", "utiliza", "utilizacion", "destina", "destino"),
            setOf("distribuye", "distribucion", "reparte", "reparto"),
            setOf("paga", "pagar", "pago", "abona", "abono"),
            setOf("articulo", "ley")
        )

        private val allIntentWords = setOf(
            "define", "definicion", "concepto", "significa",
            "porcentaje", "porcentajes", "tasa", "tasas", "rango", "rangos", "tramo", "tramos",
            "dia", "dias", "plazo", "mensual", "trimestral", "anual",
            "quien", "quienes", "recibe", "destinatario", "beneficiario", "beneficiarios",
            "donde", "lugar", "cuanto", "cuantos", "monto",
            "tipo", "tipos", "clase", "clases", "clasificacion",
            "uso", "destina", "destino", "distribucion",
            "pago", "abono", "articulo", "ley"
        )
    }
}

class JsonKnowledgeStore(private val context: Context) {

    fun cacheKey(): String {
        val jsonFile = File(context.filesDir, JSON_FILE)
        return listOf(
            jsonFile.length(),
            jsonFile.lastModified(),
            prefs().getLong(KEY_REVISION, 0L)
        ).joinToString(":")
    }

    fun readJson(): String {
        val file = File(context.filesDir, JSON_FILE)
        return if (file.exists()) file.readText() else ""
    }

    fun importJson(raw: String): Pair<Boolean, String> {
        val cleanRaw = raw.removePrefix("\uFEFF").trim()
        if (cleanRaw.isBlank()) return false to "El archivo JSON está vacío."

        val validation = validateItems(cleanRaw)
        if (!validation.ok) return false to validation.message

        val target = File(context.filesDir, JSON_FILE)
        val temp = File(context.filesDir, "$JSON_FILE.tmp")
        val backup = File(context.filesDir, "$JSON_FILE.bak")

        return try {
            temp.delete()
            backup.delete()
            temp.writeText(cleanRaw, Charsets.UTF_8)

            if (target.exists()) target.copyTo(backup, overwrite = true)
            if (target.exists() && !target.delete()) error("No se pudo reemplazar el JSON anterior.")

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val writtenValidation = validateItems(target.readText(Charsets.UTF_8))
            if (!writtenValidation.ok || writtenValidation.count != validation.count) {
                error("El JSON guardado no superó la verificación final.")
            }

            deleteLegacyRemoteFiles()
            val currentRevision = prefs().getLong(KEY_REVISION, 0L)
            val now = System.currentTimeMillis()
            val nextRevision = if (now > currentRevision) now else currentRevision + 1L
            prefs().edit()
                .remove(KEY_URL)
                .remove(KEY_VARIANTS_URL)
                .putLong(KEY_REVISION, nextRevision)
                .apply()
            backup.delete()
            true to "JSON importado. Elementos: ${validation.count}"
        } catch (e: Exception) {
            Log.e("GeoSusuJsonStore", "No se pudo guardar el JSON importado", e)
            runCatching { temp.delete() }
            runCatching {
                if (backup.exists()) {
                    target.delete()
                    backup.copyTo(target, overwrite = true)
                    backup.delete()
                }
            }
            false to "No se pudo guardar el archivo JSON. Se mantuvo el JSON anterior. ${e.message.orEmpty()}".trim()
        }
    }

    private data class ValidationResult(
        val ok: Boolean,
        val count: Int,
        val message: String
    )

    private fun validateItems(raw: String): ValidationResult {
        return try {
            val array = parseRootArray(raw)
            if (array.length() == 0) {
                return ValidationResult(false, 0, "El archivo no contiene registros.")
            }

            var validCount = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                    ?: return ValidationResult(false, validCount, "El registro ${i + 1} no es un objeto JSON válido.")

                val content = item.optString(
                    "contenido",
                    item.optString("respuesta", item.optString("texto", item.optString("descripcion", "")))
                ).trim()
                if (content.isBlank()) {
                    return ValidationResult(false, validCount, "El registro ${i + 1} no tiene contenido o respuesta.")
                }

                val searchableText = buildList {
                    add(item.optString("titulo", ""))
                    add(item.optString("pregunta", ""))
                    add(item.optString("nombre", ""))
                    addAll(readSearchValues(item, "preguntas"))
                    addAll(readSearchValues(item, "variantes"))
                    addAll(readSearchValues(item, "aliases"))
                    addAll(readSearchValues(item, "keywords"))
                    addAll(readSearchValues(item, "palabrasClave"))
                }.any { it.trim().isNotBlank() }

                if (!searchableText) {
                    return ValidationResult(
                        false,
                        validCount,
                        "El registro ${i + 1} tiene respuesta, pero no contiene pregunta, título, variantes, alias ni palabras clave."
                    )
                }
                validCount++
            }

            ValidationResult(true, validCount, "JSON válido. Elementos: $validCount")
        } catch (e: Exception) {
            ValidationResult(false, 0, "El archivo no contiene un JSON válido: ${e.message.orEmpty()}".trim())
        }
    }

    private fun parseRootArray(raw: String): JSONArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val obj = JSONObject(trimmed)
        return obj.optJSONArray("temas")
            ?: obj.optJSONArray("items")
            ?: obj.optJSONArray("data")
            ?: throw IllegalArgumentException("La raíz debe ser una lista o contener 'temas', 'items' o 'data'.")
    }

    private fun readSearchValues(obj: JSONObject, key: String): List<String> {
        val value = obj.opt(key) ?: return emptyList()
        if (value is JSONArray) {
            return (0 until value.length())
                .map { value.optString(it).trim() }
                .filter { it.isNotBlank() }
        }
        return value.toString()
            .split("|", ";", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "null" }
    }

    private fun deleteLegacyRemoteFiles() {
        File(context.filesDir, VARIANTS_FILE).delete()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "geo_susu_json"
        private const val KEY_URL = "json_url"
        private const val KEY_VARIANTS_URL = "json_variants_url"
        private const val KEY_REVISION = "json_revision"
        private const val JSON_FILE = "temas_github.json"
        private const val VARIANTS_FILE = "variantes_github.json"
    }
}
