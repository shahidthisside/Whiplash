package com.whiplash.music.playback.provider.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * [Downloader] implementation backed by OkHttp, as required by NewPipeExtractor
 * (Provider A, section 7). NewPipeExtractor has no HTTP client of its own —
 * callers must supply one.
 */
class OkHttpNewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val bodyBuilder = OkHttpRequest.Builder().url(url)

        val body: RequestBody? = dataToSend?.toRequestBody(null)

        when (httpMethod) {
            "GET" -> bodyBuilder.get()
            "HEAD" -> bodyBuilder.head()
            "POST" -> bodyBuilder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> bodyBuilder.method(httpMethod, body)
        }

        for ((name, values) in headers) {
            for (value in values) {
                bodyBuilder.addHeader(name, value)
            }
        }
        // NewPipeExtractor sets its own User-Agent header when needed; provide a
        // reasonable default only if not already set, so we don't look anomalous.
        if (headers["User-Agent"].isNullOrEmpty()) {
            bodyBuilder.addHeader("User-Agent", DEFAULT_USER_AGENT)
        }

        val httpRequest = bodyBuilder.build()

        client.newCall(httpRequest).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha required", url)
            }
            val responseBodyString = response.body?.string() ?: ""
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBodyString,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
