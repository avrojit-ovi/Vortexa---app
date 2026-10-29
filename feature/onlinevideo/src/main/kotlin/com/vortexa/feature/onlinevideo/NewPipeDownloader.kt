package com.vortexa.feature.onlinevideo

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * NewPipe does not ship a network client; it asks for a [Downloader].
 *
 * This is a plain OkHttp bridge: no retries that would hide a service change, no
 * cookie juggling, and a reCAPTCHA page is surfaced as a distinct exception so
 * the UI can offer the web fallback instead of a generic error.
 */
class NewPipeDownloader(
    private val client: OkHttpClient = defaultClient(),
) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .method(
                request.httpMethod(),
                request.dataToSend()?.toRequestBody(),
            )
            .url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw e
        }

        response.use {
            val body = it.body?.string()
            if (it.code == 429 || body?.contains("g-recaptcha") == true) {
                throw ReCaptchaException("The service asked for a reCAPTCHA", request.url())
            }
            return Response(
                it.code,
                it.message,
                it.headers.toMultimap(),
                body,
                it.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    }
}
