package com.cyrus.gallery.data.remote.graph

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Media3 [DataSource.Factory] backed by [OkHttpClient] so OneDrive `/content` video streams
 * receive Microsoft Graph Bearer auth and 401 refresh while safely dropping auth on CDN redirects.
 */
@OptIn(UnstableApi::class)
class GraphOkHttpDataSourceFactory(
    private val okHttpClient: OkHttpClient,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = GraphOkHttpDataSource(okHttpClient)
}

@OptIn(UnstableApi::class)
private class GraphOkHttpDataSource(
    private val okHttpClient: OkHttpClient,
) : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseStream: InputStream? = null
    private var opened = false
    private var bytesToRead = C.LENGTH_UNSET.toLong()
    private var bytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val requestBuilder = Request.Builder().url(dataSpec.uri.toString())
        dataSpec.httpRequestHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                (dataSpec.position + dataSpec.length - 1).toString()
            } else {
                ""
            }
            requestBuilder.header("Range", "bytes=${dataSpec.position}-$rangeEnd")
        }

        val execResponse = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }
        response = execResponse

        val code = execResponse.code
        val body = execResponse.body
        if (!execResponse.isSuccessful || body == null) {
            val headers = execResponse.headers.toMultimap()
            closeQuietly()
            throw HttpDataSource.InvalidResponseCodeException(
                code,
                execResponse.message,
                null,
                headers,
                dataSpec,
                ByteArray(0),
            )
        }

        responseStream = body.byteStream()
        val contentLength = body.contentLength()
        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (contentLength >= 0L) {
            contentLength
        } else {
            C.LENGTH_UNSET.toLong()
        }
        bytesRead = 0L
        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = responseStream ?: return C.RESULT_END_OF_INPUT
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
            val readLen = min(length.toLong(), remaining).toInt()
            val count = stream.read(buffer, offset, readLen)
            if (count == -1) return C.RESULT_END_OF_INPUT
            bytesRead += count
            bytesTransferred(count)
            return count
        }
        val count = stream.read(buffer, offset, length)
        if (count == -1) return C.RESULT_END_OF_INPUT
        bytesRead += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = response?.request?.url?.toString()?.let(Uri::parse) ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap() ?: emptyMap()

    override fun close() {
        dataSpec = null
        closeQuietly()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeQuietly() {
        runCatching { responseStream?.close() }
        runCatching { response?.close() }
        responseStream = null
        response = null
    }
}
