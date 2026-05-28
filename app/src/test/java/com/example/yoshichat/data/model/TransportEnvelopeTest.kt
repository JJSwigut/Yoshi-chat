package com.example.yoshichat.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportEnvelopeTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `decodes operation path with string and numeric segments`() {
        val envelope =
            json.decodeFromString(
                TransportEnvelope.serializer(),
                """
                {
                  "type": "update-state",
                  "unknown": "ignored",
                  "operations": [
                    {
                      "type": "set",
                      "path": ["messages", "3", "content"],
                      "value": "updated"
                    }
                  ]
                }
                """.trimIndent(),
            )

        val operation = envelope.operations.single()
        assertEquals("update-state", envelope.type)
        assertEquals(OperationPathSegment.Key("messages"), operation.path[0])
        assertEquals(OperationPathSegment.Index(3), operation.path[1])
        assertEquals(OperationPathSegment.Key("content"), operation.path[2])
        assertEquals("updated", operation.value.jsonPrimitive.content)
    }
}
