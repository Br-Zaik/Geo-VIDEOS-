package com.bph.geosusuaudio

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

class VoskModelManager(private val context: Context) {

    private val modelName = "vosk-model-small-es-0.42"
    private val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"

    fun modelDir(): File = File(context.filesDir, modelName)

    fun isReady(): Boolean {
        val dir = modelDir()
        val finalMdl = File(dir, "am/final.mdl")
        val modelConf = File(dir, "conf/model.conf")
        val hclg = File(dir, "graph/HCLG.fst")
        val hclr = File(dir, "graph/HCLr.fst")
        val gr = File(dir, "graph/Gr.fst")
        val hasGraph = hclg.exists() || (hclr.exists() && gr.exists())

        return dir.exists() &&
            finalMdl.exists() && finalMdl.length() > 1024L * 1024L &&
            modelConf.exists() && modelConf.length() > 50L &&
            hasGraph
    }

    fun statusDetail(): String {
        val dir = modelDir()
        val finalMdl = File(dir, "am/final.mdl")
        val modelConf = File(dir, "conf/model.conf")
        val hclg = File(dir, "graph/HCLG.fst")
        val hclr = File(dir, "graph/HCLr.fst")
        val gr = File(dir, "graph/Gr.fst")

        return "dir=${dir.exists()}, final=${finalMdl.length()}, conf=${modelConf.length()}, HCLG=${hclg.exists()}, HCLr=${hclr.exists()}, Gr=${gr.exists()}"
    }

    fun deleteModel() {
        try {
            modelDir().deleteRecursively()
            File(context.cacheDir, "$modelName.zip").delete()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar completamente el modelo Vosk", e)
        }
    }

    fun downloadAndUnzip(onProgress: (String) -> Unit, onDone: (Boolean, String) -> Unit) {
        Thread {
            try {
                if (isReady()) {
                    onDone(true, "Modelo Vosk ya está listo.")
                    return@Thread
                }

                onProgress("Limpiando modelo anterior...")
                deleteModel()

                val zipFile = File(context.cacheDir, "$modelName.zip")
                if (zipFile.exists()) zipFile.delete()

                onProgress("Descargando modelo offline. Pesa aprox. 39 MB...")
                download(zipFile, onProgress)

                if (!zipFile.exists() || zipFile.length() < 10L * 1024L * 1024L) {
                    zipFile.delete()
                    onDone(false, "Descarga incompleta. Revisa internet y vuelve a tocar ESCUCHAR.")
                    return@Thread
                }

                onProgress("Descomprimiendo modelo...")
                unzipRobust(zipFile)
                zipFile.delete()

                if (isReady()) {
                    onDone(true, "Modelo Vosk listo. Toca ESCUCHAR.")
                } else {
                    val detail = statusDetail()
                    deleteModel()
                    onDone(false, "Modelo inválido y borrado: $detail. Toca ESCUCHAR para descargar de nuevo.")
                }
            } catch (e: Exception) {
                deleteModel()
                onDone(false, "Error preparando modelo: ${e.message ?: "sin detalle"}. Toca ESCUCHAR otra vez.")
            }
        }.start()
    }

    private fun download(target: File, onProgress: (String) -> Unit) {
        val connection = URL(modelUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "GeoSusuAudio/1.0 Android")
        connection.instanceFollowRedirects = true

        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code al descargar Vosk")
        }

        val total = connection.contentLengthLong.takeIf { it > 0 } ?: 1L
        var downloaded = 0L
        var lastPercent = -1

        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    if (percent != lastPercent && percent % 5 == 0) {
                        lastPercent = percent
                        onProgress("Descargando modelo: $percent%")
                    }
                }
            }
        }
    }

    private fun unzipRobust(zipFile: File) {
        val tempRoot = File(context.cacheDir, "${modelName}_extract")
        if (tempRoot.exists()) tempRoot.deleteRecursively()
        tempRoot.mkdirs()

        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val cleanName = entry.name.replace("\\", "/").trimStart('/')
                    if (cleanName.isBlank() || cleanName.contains("..")) continue

                    val outFile = File(tempRoot, cleanName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        }

                        if (entry.size > 0L && outFile.length() == 0L) {
                            throw IllegalStateException("Archivo vacío al descomprimir: $cleanName")
                        }
                    }
                }
            }

            val extractedRoot = findValidModelRoot(tempRoot)
                ?: throw IllegalStateException("No se encontró modelo válido dentro del ZIP")

            val target = modelDir()
            target.deleteRecursively()
            extractedRoot.copyRecursively(target, overwrite = true)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun findValidModelRoot(root: File): File? {
        return root.walkTopDown()
            .filter { it.isDirectory }
            .firstOrNull { dir ->
                val finalMdl = File(dir, "am/final.mdl")
                val modelConf = File(dir, "conf/model.conf")
                val hclg = File(dir, "graph/HCLG.fst")
                val hclr = File(dir, "graph/HCLr.fst")
                val gr = File(dir, "graph/Gr.fst")
                val hasGraph = hclg.exists() || (hclr.exists() && gr.exists())
                finalMdl.exists() && finalMdl.length() > 1024L * 1024L &&
                    modelConf.exists() && modelConf.length() > 50L &&
                    hasGraph
            }
    }

    companion object {
        private const val TAG = "GeoSusuVoskModel"
    }
}
