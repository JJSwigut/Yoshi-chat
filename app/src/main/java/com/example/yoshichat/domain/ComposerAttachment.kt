package com.example.yoshichat.domain

data class ComposerAttachment(
    val id: String,
    val uri: String,
    val filename: String,
    val contentType: String,
    val sizeBytes: Long?,
    val status: AttachmentUploadStatus,
    val fileId: String? = null,
    val error: String? = null,
)

enum class AttachmentUploadStatus {
    Uploading,
    Uploaded,
    Failed,
}

data class UploadedAttachment(
    val fileId: String,
    val filename: String,
    val contentType: String,
    val sizeBytes: Long?,
)
