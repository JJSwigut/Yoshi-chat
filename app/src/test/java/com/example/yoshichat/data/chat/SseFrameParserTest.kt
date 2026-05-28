package com.example.yoshichat.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseFrameParserTest {
    @Test
    fun `parses data payload at blank line boundary`() {
        val parser = SseFrameParser()

        assertTrue(parser.acceptLine("""data: {"type":"update-state","operations":[]}""").isEmpty())

        assertEquals(
            listOf("""{"type":"update-state","operations":[]}"""),
            parser.acceptLine(""),
        )
        assertTrue(parser.finish().isEmpty())
    }

    @Test
    fun `emits done payload`() {
        val parser = SseFrameParser()

        parser.acceptLine("data: [DONE]")

        assertEquals(listOf("[DONE]"), parser.acceptLine(""))
    }

    @Test
    fun `joins multiple data lines in one frame`() {
        val parser = SseFrameParser()

        parser.acceptLine("data: first")
        parser.acceptLine("data: second")

        assertEquals(listOf("first\nsecond"), parser.acceptLine(""))
    }

    @Test
    fun `ignores non data lines`() {
        val parser = SseFrameParser()

        parser.acceptLine("event: update-state")
        parser.acceptLine(": keep-alive")
        parser.acceptLine("""data: {"ok":true}""")

        assertEquals(listOf("""{"ok":true}"""), parser.acceptLine(""))
    }
}
