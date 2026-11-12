package com.geovideos.app.playback

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL

/** Lightweight HTTP implementation used only by NewPipe Extractor. */
class AndroidDownloader : Downloader() {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val requestUrl = URL(request.url())
        val requestUri = runCatching { requestUrl.toURI() }.getOrNull()
        val connection = (requestUrl.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = request.httpMethod()
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            doInput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            request.headers().forEach { (name, values) ->
                values.firstOrNull()?.let { first -> setRequestProperty(name, first) }
                values.drop(1).forEach { value -> addRequestProperty(name, value) }
            }
            // The response is read as text below, so request an uncompressed body.
            setRequestProperty("Accept-Encoding", "identity")
            requestUri?.let { uri ->
                cookieManager.cookieStore.get(uri)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "; ") { it.toString() }
                    ?.let { setRequestProperty("Cookie", it) }
            }
            request.dataToSend()?.let { body ->
                doOutput = true
                setFixedLengthStreamingMode(body.size)
                outputStream.use { it.write(body) }
            }
        }

        return try {
            val code = connection.responseCode
            val body = if (request.httpMethod().equals("HEAD", ignoreCase = true)) {
                ""
            } else {
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.orEmpty() }
            runCatching { cookieManager.put(connection.url.toURI(), headers) }
            Response(
                code,
                connection.responseMessage.orEmpty(),
                headers,
                body,
                connection.url.toString()
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
