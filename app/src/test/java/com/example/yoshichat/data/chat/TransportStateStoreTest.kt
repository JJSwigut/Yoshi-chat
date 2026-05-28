package com.example.yoshichat.data.chat

import com.example.yoshichat.data.model.Operation
import com.example.yoshichat.data.model.OperationPathSegment
import com.example.yoshichat.domain.ChatRole
import com.example.yoshichat.domain.MessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportStateStoreTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `applies messages set and maps roles`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages")),
                    value =
                        buildJsonArray {
                            add(messageNode(type = "human", text = "hello"))
                            add(messageNode(type = "ai", text = "hi there"))
                        },
                ),
            ),
        )

        val messages = store.toDomainMessages()
        assertEquals(2, messages.size)
        assertEquals(ChatRole.User, messages[0].role)
        assertEquals(ChatRole.Assistant, messages[1].role)
    }

    @Test
    fun `applies indexed message set`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(0)),
                    value = messageNode(type = "ai", text = "streamed answer"),
                ),
            ),
        )

        val messages = store.toDomainMessages()
        assertEquals(1, messages.size)
        assertEquals(ChatRole.Assistant, messages.single().role)
        assertEquals(MessagePart.Text("streamed answer"), messages.single().parts.single())
    }

    @Test
    fun `ignores negative message index`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(-1)),
                    value = messageNode(type = "ai", text = "bad index"),
                ),
            ),
        )

        assertEquals(emptyList<Any>(), store.toDomainMessages())
    }

    @Test
    fun `maps assistant text content array`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(0)),
                    value =
                        buildJsonObject {
                            put("type", "ai")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", "Try a 24-hour pause.")
                                        },
                                    )
                                },
                            )
                        },
                ),
            ),
        )

        assertEquals(
            MessagePart.Text("Try a 24-hour pause."),
            store.toDomainMessages().single().parts.single(),
        )
    }

    @Test
    fun `applies indexed message content set`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(0)),
                    value = messageNode(type = "ai", text = "placeholder"),
                ),
                setOperation(
                    path = listOf(key("messages"), index(0), key("content")),
                    value =
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", "streamed replacement")
                                },
                            )
                        },
                ),
            ),
        )

        assertEquals(
            MessagePart.Text("streamed replacement"),
            store.toDomainMessages().single().parts.single(),
        )
    }

    @Test
    fun `maps reasoning content intentionally as reasoning part`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(0)),
                    value =
                        buildJsonObject {
                            put("type", "ai")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "reasoning")
                                            put("text", "Checking the current state.")
                                        },
                                    )
                                },
                            )
                        },
                ),
            ),
        )

        assertEquals(
            MessagePart.Reasoning("Checking the current state."),
            store.toDomainMessages().single().parts.single(),
        )
    }

    @Test
    fun `maps reasoning summary array without crashing`() {
        val store = TransportStateStore(json)

        store.applyOperations(
            listOf(
                setOperation(
                    path = listOf(key("messages"), index(0)),
                    value =
                        buildJsonObject {
                            put("type", "ai")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "reasoning")
                                            put(
                                                "summary",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("text", "Reviewing recent activity.")
                                                        },
                                                    )
                                                    add(JsonPrimitive("Preparing a concise answer."))
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                ),
            ),
        )

        assertEquals(
            MessagePart.Reasoning("Reviewing recent activity.\nPreparing a concise answer."),
            store.toDomainMessages().single().parts.single(),
        )
    }

    private fun setOperation(
        path: List<OperationPathSegment>,
        value: JsonElement,
    ): Operation = Operation(type = "set", path = path, value = value)

    private fun key(value: String): OperationPathSegment = OperationPathSegment.Key(value)

    private fun index(value: Int): OperationPathSegment = OperationPathSegment.Index(value)

    private fun messageNode(type: String, text: String): JsonObject =
        buildJsonObject {
            put("type", type)
            put("content", JsonPrimitive(text))
        }
}
