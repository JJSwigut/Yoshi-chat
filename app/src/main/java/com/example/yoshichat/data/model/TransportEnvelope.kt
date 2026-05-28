package com.example.yoshichat.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * One JSON payload from the transport SSE stream.
 *
 * The only envelope type that currently drives UI is `update-state`, whose
 * operations mutate the canonical transport state. Unknown fields are ignored
 * by the shared Json configuration so backend metadata can evolve safely.
 */
@Serializable
data class TransportEnvelope(
    val type: String,
    val operations: List<Operation> = emptyList(),
)

@Serializable
data class Operation(
    val type: String,
    val path: List<OperationPathSegment>,
    val value: JsonElement = JsonNull,
)
