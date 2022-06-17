package com.lagradost.cloudstream3

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit


class DownloaderTestImpl private constructor(builder: OkHttpClient.Builder) : Downloader() {
    private val client: OkHttpClient
    override fun execute(request: Request): Response {
        val httpMethod: String = request.httpMethod()
        val url: String = request.url()
        val headers: Map<String, List<String>> = request.headers()
        val dataToSend: ByteArray? = request.dataToSend()
        var requestBody: RequestBody? = null
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend)
        }
        val requestBuilder: okhttp3.Request.Builder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody).url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }
        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }
        val body = response.body
        var responseBodyToReturn: String? = null
        if (body != null) {
            responseBodyToReturn = body.string()
        }
        val latestUrl = response.request.url.toString()
        return Response(
            response.code, response.message, response.headers.toMultimap(),
            responseBodyToReturn, latestUrl
        )
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
        private var instance: DownloaderTestImpl? = null

        /**
         * It's recommended to call exactly once in the entire lifetime of the application.
         *
         * @param builder if null, default builder will be used
         * @return a new instance of [DownloaderTestImpl]
         */
        fun init(builder: OkHttpClient.Builder?): DownloaderTestImpl? {
            instance = DownloaderTestImpl(
                builder ?: OkHttpClient.Builder()
            )
            return instance
        }

        fun getInstance(): DownloaderTestImpl? {
            if (instance == null) {
                init(null)
            }
            return instance
        }
    }

    init {
        client = builder.readTimeout(30, TimeUnit.SECONDS).build()
    }
}