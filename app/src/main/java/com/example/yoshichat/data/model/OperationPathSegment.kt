package com.example.yoshichat.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Path segment used by backend state operations.
 *
 * Captured streams have used both numeric JSON values and numeric strings for
 * message indexes, so the serializer preserves true JSON numbers as [Index]
 * and keeps all other segments as [Key]. Store code can still coerce numeric
 * keys when matching known paths.
 */
@Serializable(with = OperationPathSegmentSerializer::class)
sealed interface OperationPathSegment {
    data class Key(val value: String) : OperationPathSegment

    data class Index(val value: Int) : OperationPathSegment
}

object OperationPathSegmentSerializer : KSerializer<OperationPathSegment> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OperationPathSegment", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OperationPathSegment {
        val input = decoder as? JsonDecoder ?: error("Operation paths must be decoded from JSON")
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: error("Operation path segment must be primitive")
        primitive.intOrNull?.let { return OperationPathSegment.Index(it) }
        return OperationPathSegment.Key(primitive.content)
    }

    override fun serialize(encoder: Encoder, value: OperationPathSegment) {
        val output = encoder as? JsonEncoder ?: error("Operation paths must be encoded as JSON")
        val element =
            when (value) {
                is OperationPathSegment.Index -> JsonPrimitive(value.value)
                is OperationPathSegment.Key -> JsonPrimitive(value.value)
            }
        output.encodeJsonElement(element)
    }
}
