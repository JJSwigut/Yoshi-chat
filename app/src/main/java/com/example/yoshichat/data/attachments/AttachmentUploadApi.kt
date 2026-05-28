package com.example.yoshichat.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.yoshichat.data.chat.authHeaders
import com.example.yoshichat.data.config.DevServerConfig
import com.example.yoshichat.data.model.AuthSession
import com.example.yoshichat.domain.UploadedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

interface AttachmentUploadApi {
    suspend fun upload(
        uri: Uri,
        threadId: String?,
        session: AuthSession,
    ): UploadedAttachment
}

class OkHttpAttachmentUploadApi(
    context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val apiBaseUrl: String = DevServerConfig.apiBaseUrl,
) : AttachmentUploadApi {
    private val contentResolver = context.applicationContext.contentResolver

    override suspend fun upload(
        uri: Uri,
        threadId: String?,
        session: AuthSession,
    ): UploadedAttachment =
        withContext(Dispatchers.IO) {
            val metadata = contentResolver.metadataFor(uri)
            val requestId = UUID.randomUUID().toString()
            Log.d(
                TAG,
                "Uploading attachment requestId=$requestId filename=${metadata.filename} contentType=${metadata.contentType} size=${metadata.sizeBytes ?: -1} thread=${threadId != null}",
            )

            val bodyBuilder =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        name = "file",
                        filename = metadata.filename,
                        body =
                            ContentUriRequestBody(
                                contentResolver = contentResolver,
                                uri = uri,
                                mediaType = metadata.contentType.toMediaType(),
                                sizeBytes = metadata.sizeBytes,
                            ),
                    )
            if (!threadId.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("thread_id", threadId)
            }

            val request =
                Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/attachments/upload")
                    .headers(session.authHeaders())
                    .header("x-request-id", requestId)
                    .apply {
                        session.cookieHeader?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                    }
                    .post(bodyBuilder.build())
                    .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Attachment upload response requestId=$requestId code=${response.code} filename=${metadata.filename}")
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    Log.e(
                        TAG,
                        "Attachment upload failed requestId=$requestId code=${response.code} body=${responseBody.take(LOG_BODY_LIMIT)}",
                    )
                    error(uploadErrorMessage(response.code, responseBody, requestId))
                }

                val dto = json.decodeFromString(AttachmentUploadResponseDto.serializer(), responseBody)
                UploadedAttachment(
                    fileId = dto.fileId,
                    filename = dto.filename,
                    contentType = dto.contentType,
                    sizeBytes = dto.sizeBytes,
                )
            }
        }

    companion object {
        private const val TAG = "YoshiAttachments"
        private const val LOG_BODY_LIMIT = 800
    }

    private fun uploadErrorMessage(
        responseCode: Int,
        responseBody: String,
        requestId: String,
    ): String {
        val errorDto =
            runCatching {
                json.decodeFromString(AttachmentUploadErrorDto.serializer(), responseBody)
            }.getOrNull()
        val detail =
            listOfNotNull(
                errorDto?.message?.takeIf { it.isNotBlank() },
                errorDto?.upstreamStatus?.let { "upstream HTTP $it" },
                errorDto?.upstreamError?.takeIf { it.isNotBlank() }?.take(180),
            ).joinToString(" - ")

        return buildString {
            append("Attachment upload failed: HTTP ")
            append(responseCode)
            if (detail.isNotBlank()) {
                append(" - ")
                append(detail)
            }
            append(" (request ")
            append(errorDto?.requestId ?: requestId)
            append(")")
        }
    }
}

private data class LocalAttachmentMetadata(
    val filename: String,
    val contentType: String,
    val sizeBytes: Long?,
)

private fun ContentResolver.metadataFor(uri: Uri): LocalAttachmentMetadata {
    var filename: String? = null
    var sizeBytes: Long? = null

    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        filename = cursor.stringAt(OpenableColumns.DISPLAY_NAME)
        sizeBytes = cursor.longAt(OpenableColumns.SIZE)
    }

    return LocalAttachmentMetadata(
        filename = filename?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "attachment",
        contentType = getType(uri) ?: "application/octet-stream",
        sizeBytes = sizeBytes,
    )
}

private fun Cursor.stringAt(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst()) return null
    return getString(index)
}

private fun Cursor.longAt(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst() || isNull(index)) return null
    return getLong(index)
}

private class ContentUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType,
    private val sizeBytes: Long?,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = sizeBytes ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = contentResolver.openInputStream(uri) ?: error("Unable to open attachment")
        input.source().use { source ->
            sink.writeAll(source)
        }
    }
}

@Serializable
private data class AttachmentUploadResponseDto(
    @SerialName("file_id")
    val fileId: String,
    val filename: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long? = null,
)

@Serializable
private data class AttachmentUploadErrorDto(
    val message: String? = null,
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("upstream_status")
    val upstreamStatus: Int? = null,
    @SerialName("upstream_error")
    val upstreamError: String? = null,
)
