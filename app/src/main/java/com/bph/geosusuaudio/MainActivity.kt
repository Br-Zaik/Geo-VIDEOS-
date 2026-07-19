package com.bph.geosusuaudio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bph.geosusuaudio.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var jsonRepository: JsonKnowledgeRepository
    private lateinit var questionRouter: QuestionRouter
    private lateinit var memory: MemoryStore
    private val onlineProvider by lazy { OnlineAnswerProvider(this) }
    private lateinit var modelManager: VoskModelManager
    private lateinit var modeSettings: AnswerModeSettings
    private lateinit var appSettings: AppSettings
    private var tts: TextToSpeech? = null
    private var pendingStartMode = ""
    private var googleLoopRunning = false
    @Volatile private var netRequestVersion = 0

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingStartMode) {
                "vosk" -> if (modelManager.isReady()) startVoskService() else downloadOffline(startAfter = true)
                "google" -> launchGooglePrecise()
                else -> startGoogleInternalService()
            }
        } else {
            binding.tvStatus.text = "Permiso de micrófono denegado"
            binding.tvAnswer.text = "Activa el permiso de micrófono."
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        when (pendingStartMode) {
            "vosk" -> if (modelManager.isReady()) startVoskService() else downloadOffline(startAfter = true)
            "google" -> launchGooglePrecise()
            else -> startGoogleInternalService()
        }
    }

    private val openJsonFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importJsonFromUri(uri)
    }

    private val googleVoiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!googleLoopRunning) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()

            val best = matches
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()

            if (best.isNotBlank()) {
                processQuestion(best)
            } else {
                binding.tvStatus.text = "Google no entendió. Reintentando..."
                reopenGooglePrecise(550)
            }
        } else {
            binding.tvStatus.text = "Google no entendió. Reintentando..."
            reopenGooglePrecise(700)
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(VoskListenService.EXTRA_STATUS)
                ?: intent?.getStringExtra(GoogleInternalListenService.EXTRA_STATUS)
                ?: ""
            val heard = intent?.getStringExtra(VoskListenService.EXTRA_HEARD)
                ?: intent?.getStringExtra(GoogleInternalListenService.EXTRA_HEARD)
                ?: ""
            val answer = intent?.getStringExtra(VoskListenService.EXTRA_ANSWER)
                ?: intent?.getStringExtra(GoogleInternalListenService.EXTRA_ANSWER)
                ?: ""

            if (status.isNotBlank()) binding.tvStatus.text = status
            if (heard.isNotBlank()) setHeardText("Pregunta: $heard")
            if (answer.isNotBlank()) {
                setAnswerText(answer)
                if (heard.isNotBlank()) memory.saveLast(heard, answer, "service", 0)
            }

            val level = intent?.getIntExtra(VoskListenService.EXTRA_LEVEL, -1) ?: -1
            if (level >= 0) {
                binding.pbVoiceLevel.progress = level
                binding.tvVoiceLevel.text = when {
                    level < 15 -> "Muy bajo: acerca el Zync o sube voz. Nivel $level%"
                    level > 85 -> "Muy fuerte: baja un poco. Nivel $level%"
                    else -> "Nivel correcto: $level%"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureScrollableTextBoxes()

        memory = MemoryStore(this)
        modelManager = VoskModelManager(this)
        modeSettings = AnswerModeSettings(this)
        appSettings = AppSettings(this)
        resetModeToTxtOnce()
        jsonRepository = JsonKnowledgeRepository(this)
        questionRouter = QuestionRouter(jsonRepository)
        tts = TextToSpeech(this, this)

        setupUi()
        warmJsonInBackground()
        binding.tvStatus.text = "Susurro Google + JSON/NET"
        binding.tvAnswer.text = "Ahora reconoce el susurro, corrige la pregunta y luego usa JSON o NET según el modo."
    }



    private fun configureScrollableTextBoxes() {
        binding.tvHeard.movementMethod = ScrollingMovementMethod.getInstance()
        binding.tvAnswer.movementMethod = ScrollingMovementMethod.getInstance()
        binding.tvHeard.isVerticalScrollBarEnabled = true
        binding.tvAnswer.isVerticalScrollBarEnabled = true
    }

    private fun setHeardText(text: String, scrollToEnd: Boolean = true) {
        binding.tvHeard.text = text
        if (scrollToEnd) scrollTextToEnd(binding.tvHeard)
    }

    private fun setAnswerText(text: String, scrollToEnd: Boolean = true) {
        binding.tvAnswer.text = text
        if (scrollToEnd) scrollTextToEnd(binding.tvAnswer)
    }

    private fun scrollTextToEnd(view: TextView) {
        view.post {
            val layout = view.layout ?: return@post
            val lastLine = (view.lineCount - 1).coerceAtLeast(0)
            val contentBottom = layout.getLineBottom(lastLine) + view.compoundPaddingBottom
            val visibleHeight = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
            view.scrollTo(0, (contentBottom - visibleHeight).coerceAtLeast(0))
        }
    }

    private fun resetModeToTxtOnce() {
        val prefs = getSharedPreferences("geo_susu_memory", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false

        if (!prefs.getBoolean("v19_txt_default_done", false)) {
            modeSettings.setMode(AnswerModeSettings.Mode.AUTO)
            editor.putBoolean("v19_txt_default_done", true)
            changed = true
        }

        if (!prefs.getBoolean("v110_auto_default_done", false)) {
            modeSettings.setMode(AnswerModeSettings.Mode.AUTO)
            editor.putBoolean("v110_auto_default_done", true)
            changed = true
        }

        if (changed) editor.apply()
    }




    private fun applyModeToUi() {
        when (modeSettings.getMode()) {
            AnswerModeSettings.Mode.JSON -> binding.rbJson.isChecked = true
            AnswerModeSettings.Mode.INTERNET -> binding.rbInternet.isChecked = true
            AnswerModeSettings.Mode.AUTO -> binding.rbAuto.isChecked = true
        }
        binding.tvModeMini.text = modeSettings.getLabel()
    }












    private fun showSettingsDialog() {
        showApiDialog()
    }

    private fun showApiDialog() {
        val settings = GeminiSettings(this)
        val draftConfigs = settings.getApiConfigs()
            .mapIndexed { index, config -> config.clean(index) }
            .toMutableList()

        lateinit var dialog: AlertDialog
        lateinit var summaryView: TextView
        lateinit var apiListContainer: LinearLayout
        lateinit var testButton: Button
        lateinit var saveButton: Button
        lateinit var clearStatusButton: Button
        val providerInputs = linkedMapOf<NetProvider, EditText>()
        val addButtons = mutableListOf<Button>()
        val individualTestButtons = mutableListOf<Button>()
        val deleteButtons = mutableListOf<Button>()
        val testingConfigIds = mutableSetOf<String>()
        lateinit var individualTestAction: (NetApiConfig) -> Unit
        var testingApis = false

        fun parseManualKeys(raw: String): List<String> {
            return raw
                .replace(",", "\n")
                .replace(";", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

        fun currentConfigs(): List<NetApiConfig> {
            val clean = draftConfigs
                .mapIndexed { index, config ->
                    config.copy(
                        name = "API ${index + 1}",
                        model = config.model.ifBlank { config.provider.defaultModel }
                    ).clean(index)
                }
                .filter { it.apiKey.isNotBlank() }
                .distinctBy { "${it.provider.id}|${it.apiKey}|${it.model}" }

            draftConfigs.clear()
            draftConfigs.addAll(clean)
            return clean
        }

        fun shortKey(value: String): String {
            val clean = value.trim()
            return when {
                clean.length <= 8 -> "••••"
                else -> clean.take(4) + "••••" + clean.takeLast(4)
            }
        }

        fun addKeysForProvider(provider: NetProvider): Int {
            val input = providerInputs[provider] ?: return 0
            val keys = parseManualKeys(input.text?.toString().orEmpty())
            if (keys.isEmpty()) return 0

            val existing = draftConfigs
                .map { "${it.provider.id}|${it.apiKey.trim()}" }
                .toMutableSet()
            var added = 0

            keys.forEach { key ->
                val id = "${provider.id}|$key"
                if (id !in existing) {
                    draftConfigs.add(
                        NetApiConfig(
                            name = "API ${draftConfigs.size + 1}",
                            provider = provider,
                            apiKey = key,
                            model = provider.defaultModel
                        ).clean(draftConfigs.size)
                    )
                    existing.add(id)
                    added += 1
                }
            }

            input.setText("")
            currentConfigs()
            return added
        }

        fun addAllPendingKeys(): Int {
            var added = 0
            NetProvider.entries.forEach { provider ->
                added += addKeysForProvider(provider)
            }
            return added
        }

        fun refreshApiPanel() {
            val configs = currentConfigs()
            val counts = NetProvider.entries.associateWith { provider ->
                configs.count { it.provider == provider }
            }
            val statusLine = settings.getStatusReportForConfigs(configs).substringBefore("\n")

            summaryView.text = if (configs.isEmpty()) {
                "No hay APIs agregadas. Pega una clave en su proveedor y toca AGREGAR."
            } else {
                "Total: ${configs.size} API(s)\n" +
                    "Gemini: ${counts[NetProvider.GEMINI]} | OpenRouter: ${counts[NetProvider.OPENROUTER]} | " +
                    "Mistral: ${counts[NetProvider.MISTRAL]} | Cohere: ${counts[NetProvider.COHERE]}\n$statusLine"
            }

            apiListContainer.removeAllViews()
            individualTestButtons.clear()
            deleteButtons.clear()

            if (configs.isEmpty()) {
                apiListContainer.addView(TextView(this).apply {
                    text = "Lista vacía."
                    textSize = 13f
                    setPadding(10, 8, 10, 12)
                })
                return
            }

            configs.forEachIndexed { index, config ->
                val configId = settings.configId(config)
                val isTestingThisApi = configId in testingConfigIds
                val fullStatus = settings.getStatusLabel(config, index)
                val savedStatus = fullStatus.substringAfter(": ", fullStatus)
                val status = if (isTestingThisApi) "PROBANDO... espera el resultado" else savedStatus

                val info = TextView(this).apply {
                    text = "${index + 1}. ${config.provider.label}  ${shortKey(config.apiKey)}\nEstado: $status"
                    textSize = 13f
                    setPadding(6, 8, 8, 8)
                }

                val testOneButton = Button(this).apply {
                    text = if (isTestingThisApi) "PROBANDO..." else "PROBAR"
                    setAllCaps(false)
                    isEnabled = !testingApis && !isTestingThisApi
                    setOnClickListener { individualTestAction(config) }
                }
                individualTestButtons.add(testOneButton)

                val deleteButton = Button(this).apply {
                    text = "BORRAR"
                    setAllCaps(false)
                    isEnabled = !testingApis
                    setOnClickListener {
                        val id = settings.configId(config)
                        draftConfigs.removeAll { settings.configId(it) == id }
                        settings.forgetConfigStatus(config)
                        settings.saveApiConfigs(currentConfigs())
                        refreshApiPanel()
                        binding.tvStatus.text = "API borrada"
                        binding.tvAnswer.text = "Se borró ${config.provider.label} ${shortKey(config.apiKey)}. Las demás APIs no cambiaron."
                    }
                }
                deleteButtons.add(deleteButton)

                val buttonColumn = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        testOneButton,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        deleteButton,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(4, 2, 4, 6)
                    addView(
                        info,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    addView(
                        buttonColumn,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                apiListContainer.addView(row)
            }
        }

        fun saveApis() {
            val added = addAllPendingKeys()
            val configs = currentConfigs()
            settings.saveApiConfigs(configs)
            refreshApiPanel()
            binding.tvStatus.text = "APIs guardadas"
            binding.tvAnswer.text = when {
                configs.isEmpty() -> "No hay APIs guardadas. JSON seguirá funcionando."
                added > 0 -> "Se agregaron $added API nueva(s). Total guardadas: ${configs.size}. Ahora toca PROBAR APIS."
                else -> "Guardadas ${configs.size} API. Toca PROBAR APIS para comprobar cada una."
            }
        }

        fun setTestingState(testing: Boolean) {
            testingApis = testing
            testButton.isEnabled = !testing
            saveButton.isEnabled = !testing
            clearStatusButton.isEnabled = !testing
            providerInputs.values.forEach { it.isEnabled = !testing }
            addButtons.forEach { it.isEnabled = !testing }
            individualTestButtons.forEach { it.isEnabled = !testing }
            deleteButtons.forEach { it.isEnabled = !testing }
        }

        individualTestAction = { config ->
            val configId = settings.configId(config)
            if (testingApis || configId in testingConfigIds) {
                Toast.makeText(this, "Ya hay una API en prueba. Espera el resultado.", Toast.LENGTH_SHORT).show()
            } else {
                val configs = currentConfigs()
                settings.saveApiConfigs(configs)
                testingConfigIds.add(configId)
                setTestingState(true)
                if (!isFinishing && dialog.isShowing) {
                    refreshApiPanel()
                }
                Toast.makeText(this, "Probando ${config.provider.label}...", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "Probando ${config.provider.label}..."
                binding.tvAnswer.text = "Comprobando solo ${config.name} (${config.provider.label}) ${shortKey(config.apiKey)}. Espera hasta 20 segundos."

                Thread {
                    val result = try {
                        onlineProvider.testApiConfig(config)
                    } catch (error: Exception) {
                        val message = error.message?.take(140).orEmpty().ifBlank { "fallo inesperado durante la prueba" }
                        settings.markUntested(config, "No se pudo completar la prueba: $message")
                        false to "${config.name} (${config.provider.label}): NO COMPROBADA - $message"
                    }

                    runOnUiThread {
                        testingConfigIds.remove(configId)
                        setTestingState(false)
                        if (!isFinishing && dialog.isShowing) {
                            refreshApiPanel()
                            Toast.makeText(
                                this,
                                if (result.first) "${config.provider.label}: FUNCIONANDO" else "${config.provider.label}: prueba terminada con error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        binding.tvStatus.text = if (result.first) "API FUNCIONANDO" else "API NO FUNCIONA"
                        binding.tvAnswer.text = result.second
                    }
                }.start()
            }
        }

        fun testApis() {
            addAllPendingKeys()
            val configs = currentConfigs()
            if (configs.isEmpty()) {
                binding.tvStatus.text = "API vacía"
                binding.tvAnswer.text = "Agrega por lo menos una API en el proveedor correcto."
                return
            }

            settings.saveApiConfigs(configs)
            setTestingState(true)
            testingConfigIds.clear()
            testingConfigIds.addAll(configs.map { settings.configId(it) })
            refreshApiPanel()
            binding.tvStatus.text = "Probando APIs..."
            binding.tvAnswer.text = "Probando ${configs.size} API(s), una por una. Espera el resultado; cada fila muestra PROBANDO mientras se comprueba."

            Thread {
                val result = try {
                    onlineProvider.testApiConfigs(configs)
                } catch (error: Exception) {
                    false to "La prueba general no pudo terminar: ${error.message?.take(180).orEmpty().ifBlank { "fallo inesperado" }}"
                }
                runOnUiThread {
                    testingConfigIds.clear()
                    setTestingState(false)
                    if (!isFinishing && dialog.isShowing) refreshApiPanel()
                    if (result.first) {
                        binding.tvStatus.text = "API OK"
                        binding.tvAnswer.text = result.second
                        Toast.makeText(this, "Prueba terminada: hay APIs funcionando", Toast.LENGTH_LONG).show()
                    } else {
                        binding.tvStatus.text = "API fallo"
                        binding.tvAnswer.text = result.second
                        Toast.makeText(this, "Prueba terminada: revisa el estado de cada API", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        val title = TextView(this).apply {
            text = "APIs NET"
            textSize = 22f
            setPadding(8, 6, 8, 4)
        }

        val help = TextView(this).apply {
            text = "Pega cada clave en su proveedor y toca AGREGAR. Las APIs aparecerán abajo por separado para probarlas o borrarlas. Puedes agregar más después."
            textSize = 13f
            setPadding(8, 0, 8, 10)
        }

        summaryView = TextView(this).apply {
            textSize = 14f
            setPadding(8, 4, 8, 10)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 6, 10, 6)
            addView(title)
            addView(help)
            addView(summaryView)
        }

        fun addProviderInput(provider: NetProvider, hintText: String) {
            val label = TextView(this).apply {
                text = provider.label
                textSize = 15f
                setPadding(8, 10, 8, 2)
            }
            val input = EditText(this).apply {
                hint = hintText
                minLines = 1
                maxLines = 3
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setSingleLine(false)
            }
            val addButton = Button(this).apply {
                text = "AGREGAR ${provider.label.uppercase()}"
                setAllCaps(false)
                setOnClickListener {
                    val added = addKeysForProvider(provider)
                    if (added <= 0) {
                        Toast.makeText(this@MainActivity, "Pega una API nueva de ${provider.label}", Toast.LENGTH_SHORT).show()
                    } else {
                        settings.saveApiConfigs(currentConfigs())
                        refreshApiPanel()
                        binding.tvStatus.text = "API agregada"
                        binding.tvAnswer.text = "Se agregaron $added API de ${provider.label}. Toca PROBAR APIS para verificar si funcionan."
                    }
                }
            }
            providerInputs[provider] = input
            addButtons.add(addButton)
            content.addView(label)
            content.addView(input)
            content.addView(addButton)
        }

        addProviderInput(NetProvider.GEMINI, "Pega API de Gemini: AIza...")
        addProviderInput(NetProvider.OPENROUTER, "Pega API de OpenRouter: sk-or-v1-...")
        addProviderInput(NetProvider.MISTRAL, "Pega API de Mistral")
        addProviderInput(NetProvider.COHERE, "Pega API de Cohere")

        val listTitle = TextView(this).apply {
            text = "APIs agregadas"
            textSize = 16f
            setPadding(8, 14, 8, 4)
        }
        content.addView(listTitle)

        apiListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 2, 4, 8)
        }
        content.addView(apiListContainer)

        saveButton = Button(this).apply {
            text = "GUARDAR"
            setAllCaps(false)
            setOnClickListener { saveApis() }
        }

        testButton = Button(this).apply {
            text = "PROBAR APIS"
            setAllCaps(false)
            setOnClickListener { testApis() }
        }

        val saveTestRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 4)
            addView(saveButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
            addView(testButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(saveTestRow)

        val clearInputsButton = Button(this).apply {
            text = "LIMPIAR ENTRADAS"
            setAllCaps(false)
            setOnClickListener {
                providerInputs.values.forEach { it.setText("") }
                binding.tvStatus.text = "Entradas limpias"
                binding.tvAnswer.text = "Solo se limpiaron los cuadros de entrada. Las APIs agregadas siguen guardadas abajo."
            }
        }

        clearStatusButton = Button(this).apply {
            text = "LIMPIAR ERROR/ESPERA"
            setAllCaps(false)
            setOnClickListener {
                val configs = currentConfigs()
                val cleared = settings.clearBlockedStatuses(configs)
                refreshApiPanel()
                binding.tvStatus.text = "Estados limpiados"
                binding.tvAnswer.text = if (cleared > 0) {
                    "Se limpiaron $cleared estados. Ahora toca PROBAR APIS para comprobarlas otra vez."
                } else {
                    "No había APIs en error, límite o espera."
                }
            }
        }

        val clearRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
            addView(clearInputsButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
            addView(clearStatusButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(clearRow)

        val closeButton = Button(this).apply {
            text = "CERRAR"
            setAllCaps(false)
            setOnClickListener { dialog.dismiss() }
        }
        content.addView(closeButton)

        val scroll = ScrollView(this).apply {
            addView(content)
        }

        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .create()

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.96).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        refreshApiPanel()
    }


    private fun openJsonFilePicker() {
        binding.tvStatus.text = "Selecciona un archivo JSON"
        binding.tvAnswer.text = "Elige el archivo JSON guardado en tu celular. No necesitas link ni internet."
        openJsonFileLauncher.launch(
            arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
        )
    }






    private fun importJsonFromUri(uri: Uri) {
        binding.tvStatus.text = "Importando JSON..."
        binding.tvAnswer.text = "Validando preguntas, respuestas y variantes antes de reemplazar el archivo actual."

        Thread {
            val result = try {
                val fileName = selectedFileName(uri)
                if (!fileName.isNullOrBlank() && !fileName.lowercase(Locale.ROOT).endsWith(".json")) {
                    false to "Selecciona un archivo con extensión .json."
                } else {
                    val raw = readJsonSafely(uri)
                    JsonKnowledgeStore(this).importJson(raw)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al importar JSON", e)
                false to (e.message?.takeIf { it.isNotBlank() } ?: "No se pudo leer el archivo JSON.")
            }

            val newRepository = if (result.first) JsonKnowledgeRepository(this) else null
            val loadedCount = try { newRepository?.totalItems ?: 0 } catch (e: Exception) {
                Log.e(TAG, "Error al abrir el JSON importado", e)
                0
            }

            runOnUiThread {
                if (result.first && newRepository != null && loadedCount > 0) {
                    jsonRepository = newRepository
                    questionRouter = QuestionRouter(jsonRepository)
                    modeSettings.setMode(AnswerModeSettings.Mode.AUTO)
                    applyModeToUi()
                    binding.tvStatus.text = "JSON importado. Elementos: $loadedCount"
                    binding.tvAnswer.text = "Archivo cargado correctamente. En AUTO busca primero en JSON y, si no encuentra, pasa a NET."
                } else if (result.first) {
                    binding.tvStatus.text = "JSON válido pero sin respuestas"
                    binding.tvAnswer.text = "El archivo no contiene registros utilizables. Se mantuvo el JSON anterior."
                } else {
                    binding.tvStatus.text = "No se importó el JSON"
                    binding.tvAnswer.text = result.second
                }
            }
        }.start()
    }

    private fun selectedFileName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }

    private fun readJsonSafely(uri: Uri): String {
        val declaredSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declaredSize > MAX_JSON_IMPORT_BYTES) {
            error("El archivo JSON supera el límite permitido de 10 MB.")
        }

        val input = contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el archivo seleccionado.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_JSON_IMPORT_BYTES) {
                    error("El archivo JSON supera el límite permitido de 10 MB.")
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun showVoiceSpeedDialog() {
        val options = arrayOf(
            "0.50x - Muy lenta",
            "0.75x - Lenta",
            "1.00x - Normal",
            "1.25x - Rápida",
            "1.50x - Muy rápida"
        )
        val checked = when (appSettings.getVoiceSpeed()) {
            AppSettings.VoiceSpeed.VERY_SLOW -> 0
            AppSettings.VoiceSpeed.SLOW -> 1
            AppSettings.VoiceSpeed.NORMAL -> 2
            AppSettings.VoiceSpeed.FAST -> 3
            AppSettings.VoiceSpeed.VERY_FAST -> 4
        }

        AlertDialog.Builder(this)
            .setTitle("Velocidad de la voz")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val value = when (which) {
                    0 -> AppSettings.VoiceSpeed.VERY_SLOW
                    1 -> AppSettings.VoiceSpeed.SLOW
                    3 -> AppSettings.VoiceSpeed.FAST
                    4 -> AppSettings.VoiceSpeed.VERY_FAST
                    else -> AppSettings.VoiceSpeed.NORMAL
                }
                appSettings.setVoiceSpeed(value)
                tts?.setSpeechRate(appSettings.getVoiceRate())
                binding.tvStatus.text = "Voz: ${appSettings.getVoiceSpeedLabel()}"
                binding.tvAnswer.text = "Velocidad guardada."
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startListeningSmart() {
        requestIgnoreBatteryOptimizationOnce()
        val engine = appSettings.getListenEngine()
        pendingStartMode = when (engine) {
            AppSettings.ListenEngine.GOOGLE_INTERNAL -> "google_internal"
            AppSettings.ListenEngine.GOOGLE -> "google"
            AppSettings.ListenEngine.VOSK -> "vosk"
        }

        when {
            !hasAudioPermission() -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission() ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            engine == AppSettings.ListenEngine.GOOGLE_INTERNAL -> startGoogleInternalService()
            engine == AppSettings.ListenEngine.GOOGLE -> launchGooglePrecise()
            else -> {
                if (modelManager.isReady()) startVoskService() else downloadOffline(startAfter = true)
            }
        }
    }

    private fun downloadOffline(startAfter: Boolean) {
        binding.tvStatus.text = "Preparando modelo offline..."
        binding.tvAnswer.text = "Se descargará una sola vez. Necesita internet estable."

        modelManager.downloadAndUnzip(
            onProgress = { msg -> runOnUiThread { binding.tvStatus.text = msg } },
            onDone = { ok, msg ->
                runOnUiThread {
                    binding.tvStatus.text = msg
                    binding.tvAnswer.text = if (ok) {
                        if (startAfter) "Modelo listo. Iniciando escucha..." else "Offline listo. Toca ESCUCHAR."
                    } else {
                        "No se pudo preparar offline. Revisa internet, espacio y vuelve a tocar ESCUCHAR."
                    }

                    if (ok && startAfter) {
                        startVoskService()
                    }
                }
            }
        )
    }

    private fun requestIgnoreBatteryOptimizationOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val prefs = getSharedPreferences("geo_susu_memory", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_request_shown", false)) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        prefs.edit().putBoolean("battery_request_shown", true).apply()

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            binding.tvStatus.text = "Permite batería sin restricciones para pantalla apagada."
        } catch (_: Exception) {
            binding.tvStatus.text = "Quita restricción de batería manualmente."
        }
    }

    private fun setupUi() {
        applyModeToUi()

        binding.rgAnswerMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbJson.id -> modeSettings.setMode(AnswerModeSettings.Mode.JSON)
                binding.rbInternet.id -> modeSettings.setMode(AnswerModeSettings.Mode.INTERNET)
                binding.rbAuto.id -> modeSettings.setMode(AnswerModeSettings.Mode.AUTO)
                else -> modeSettings.setMode(AnswerModeSettings.Mode.AUTO)
            }
            applyModeToUi()
            binding.tvStatus.text = "Modo: ${modeSettings.getLabel()}"
        }

        binding.btnImportText.setOnClickListener { showVoiceSpeedDialog() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }

        binding.btnDownloadModel.setOnClickListener {
            openJsonFilePicker()
        }


        binding.btnStart.setOnClickListener {
            startListeningSmart()
        }

        binding.btnStop.setOnClickListener {
            googleLoopRunning = false
            cancelPendingNet()
            stopService(Intent(this, GoogleInternalListenService::class.java))
            stopService(Intent(this, VoskListenService::class.java))
            binding.tvStatus.text = "Escucha detenida"
            binding.tvHeard.text = "Pregunta: ---"
            binding.tvAnswer.text = "Listo."
        }

    }

    private fun launchGooglePrecise() {
        googleLoopRunning = true
        stopService(Intent(this, GoogleInternalListenService::class.java))
        stopService(Intent(this, VoskListenService::class.java))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (Build.VERSION.SDK_INT >= 33) {
                val hints = jsonRepository.recognitionHints(40)
                if (hints.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints)
                }
            }
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla o susurra cerca del micrófono")
        }

        try {
            binding.tvStatus.text = "Escucha precisa de Google..."
            binding.tvAnswer.text = "Habla cuando aparezca el micrófono."
            googleVoiceLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir Google Speech", e)
            googleLoopRunning = false
            binding.tvStatus.text = "Google Speech no disponible"
            binding.tvAnswer.text = "Activa Speech Services by Google o cambia a Vosk."
        }
    }

    private fun reopenGooglePrecise(delayMs: Long = 650L) {
        if (!googleLoopRunning || appSettings.getListenEngine() != AppSettings.ListenEngine.GOOGLE) return
        binding.root.postDelayed({
            if (googleLoopRunning && appSettings.getListenEngine() == AppSettings.ListenEngine.GOOGLE) {
                launchGooglePrecise()
            }
        }, delayMs)
    }

    private fun startGoogleInternalService() {
        googleLoopRunning = false
        stopService(Intent(this, VoskListenService::class.java))
        val intent = Intent(this, GoogleInternalListenService::class.java)
        ContextCompat.startForegroundService(this, intent)
        binding.tvStatus.text = "Susurro Google activo"
        binding.tvAnswer.text = "Escucha, corrige la pregunta y responde con JSON o NET según el modo."
    }


    private fun startVoskService() {
        googleLoopRunning = false
        stopService(Intent(this, GoogleInternalListenService::class.java))
        val intent = Intent(this, VoskListenService::class.java)
        ContextCompat.startForegroundService(this, intent)
        binding.tvStatus.text = "Escuchando offline..."
        binding.tvAnswer.text = "Escuchando según el modo elegido."
    }



    private fun processQuestion(question: String) {
        cancelPendingNet()
        val cleanQuestion = VoiceQuestionNormalizer.normalize(question)
        binding.tvHeard.text = "Pregunta: $cleanQuestion"

        if (VoiceQuestionNormalizer.isTooUnclearForJsonOrNet(cleanQuestion)) {
            showAndSpeak("No se detectó pregunta completa. Repite la pregunta.", "No se procesó")
            return
        }

        val localAnswer = LocalAnswerProvider.findAnswer(cleanQuestion)
        if (localAnswer != null) {
            showAndSpeak(localAnswer, "Respuesta local")
            memory.saveLast(cleanQuestion, localAnswer, "local", 100)
            return
        }

        val mode = modeSettings.getMode()
        if (mode == AnswerModeSettings.Mode.INTERNET) {
            if (VoiceQuestionNormalizer.isUnsafeForNet(cleanQuestion)) {
                showAndSpeak("No hay una pregunta clara para enviar a NET.", "NET bloqueado por texto vacío")
            } else {
                searchOnline(cleanQuestion)
            }
            return
        }

        binding.tvStatus.text = "Buscando en JSON..."
        binding.tvAnswer.text = if (mode == AnswerModeSettings.Mode.AUTO) {
            "Si no encuentra, pasará a NET."
        } else {
            "Buscando solo en el JSON activo."
        }

        Thread {
            val route = questionRouter.resolve(mode, cleanQuestion)
            runOnUiThread {
                when (route) {
                    is QuestionRouter.Result.Json -> {
                        val match = route.match
                        showAndSpeak(match.answer, "Respuesta JSON")
                        memory.saveLast(cleanQuestion, match.answer, match.itemId, match.score)
                    }
                    QuestionRouter.Result.JsonNotFound -> {
                        showAndSpeak("No encontrado", "No encontrado en JSON")
                    }
                    QuestionRouter.Result.Net -> {
                        if (VoiceQuestionNormalizer.isUnsafeForNet(cleanQuestion)) {
                            showAndSpeak("No hay una pregunta clara para enviar a NET.", "NET bloqueado por texto vacío")
                        } else {
                            searchOnline(cleanQuestion)
                        }
                    }
                }
            }
        }.start()
    }

    private fun searchOnline(question: String, statusText: String = "Consultando NET...") {
        binding.tvStatus.text = statusText
        binding.tvAnswer.text = "Buscando una API probada y disponible. Si una se agotó, pasará a la siguiente."
        val requestVersion = ++netRequestVersion

        Thread {
            val answer = try {
                onlineProvider.fetchShortAnswer(question)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo inesperado al consultar NET", e)
                "NET no pudo responder. Revisa Internet y el estado de tus APIs."
            }

            runOnUiThread {
                if (requestVersion != netRequestVersion || isFinishing || isDestroyed) return@runOnUiThread
                showAndSpeak(answer, "Respuesta NET")
                memory.saveLast(question, answer, "net", 0)
            }
        }.start()
    }

    private fun cancelPendingNet() {
        netRequestVersion += 1
        onlineProvider.cancelActiveRequest()
    }

    private fun showAndSpeak(answer: String, status: String) {
        binding.tvStatus.text = status
        binding.tvAnswer.text = answer
        tts?.stop()
        tts?.setSpeechRate(appSettings.getVoiceRate())
        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "geo_susu_answer")

        if (googleLoopRunning && appSettings.getListenEngine() == AppSettings.ListenEngine.GOOGLE) {
            val waitMs = (answer.length * 55L).coerceIn(1200L, 6500L)
            reopenGooglePrecise(waitMs)
        }
    }






    private fun warmJsonInBackground() {
        Thread {
            try {
                val count = jsonRepository.totalItems
                if (count > 0) {
                    runOnUiThread {
                        if (binding.tvStatus.text.toString().startsWith("Listo") || binding.tvStatus.text.toString().startsWith("Sin API")) {
                            binding.tvModeMini.text = "${modeSettings.getLabel()} | JSON $count"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo precargar el JSON", e)
            }
        }.start()
    }


    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(VoskListenService.ACTION_UPDATE)
            addAction(GoogleInternalListenService.ACTION_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }

        restoreLastVisibleAnswer()
    }

    private fun restoreLastVisibleAnswer() {
        val answer = memory.getLastAnswer().trim()
        val heard = memory.getLastHeard().trim()
        if (answer.isBlank()) return

        // v148: al salir y volver, la pantalla queda como la dejó el usuario.
        // No pisa el servicio activo; solo restaura lo visible.
        setAnswerText(answer)
        if (heard.isNotBlank()) setHeardText("Pregunta: $heard")
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            Log.d(TAG, "El receptor ya no estaba registrado", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "PE")) ?: TextToSpeech.ERROR
            tts?.setSpeechRate(appSettings.getVoiceRate())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "La voz en español no está disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        cancelPendingNet()
        super.onDestroy()
        googleLoopRunning = false
        // v148: no detener el servicio solo por salir de la pantalla.
        // La escucha se detiene únicamente con el botón DETENER.
        tts?.stop()
        tts?.shutdown()
    }
    companion object {
        private const val MAX_JSON_IMPORT_BYTES = 10L * 1024L * 1024L
        private const val TAG = "GeoSusuMain"
    }

}
