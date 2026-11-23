package dev.pocketdev.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSentEventTest {

    @Test
    fun parsesSingleEvent() {
        val text = "event: message\ndata: hello world\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals(1, events.size)
        assertEquals("message", events[0].event)
        assertEquals("hello world", events[0].data)
        assertNull(events[0].id)
    }

    @Test
    fun parsesMultipleEvents() {
        val text = "data: first\n\ndata: second\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals(2, events.size)
        assertEquals("first", events[0].data)
        assertEquals("second", events[1].data)
    }

    @Test
    fun joinsMultilineData() {
        val text = "data: line1\ndata: line2\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals("line1\nline2", events[0].data)
    }

    @Test
    fun capturesIdAndEvent() {
        val text = "id: 42\nevent: update\ndata: payload\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals("42", events[0].id)
        assertEquals("update", events[0].event)
        assertEquals("payload", events[0].data)
    }

    @Test
    fun ignoresCommentsAndUnknownFields() {
        val text = ": this is a comment\nretry: 1000\nfoo: bar\ndata: ok\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals(1, events.size)
        assertEquals("ok", events[0].data)
    }

    @Test
    fun dropsEventsWithoutData() {
        val text = "event: ping\n\n"
        val events = ServerSentEvents.parse(text)
        assertEquals(0, events.size)
    }

    @Test
    fun parsesTrailingEventWithoutBlankLine() {
        val text = "data: trailing"
        val events = ServerSentEvents.parse(text)
        assertEquals(1, events.size)
        assertEquals("trailing", events[0].data)
    }

    @Test
    fun emptyInputYieldsNoEvents() {
        assertEquals(0, ServerSentEvents.parse("").size)
    }

    @Test
    fun typeFallsBackToJsonPayload() {
        val event = ServerSentEvent(null, """{"type":"permission.asked","data":{}}""", null)
        assertEquals("permission.asked", event.type)
    }

    @Test
    fun genericMessageEventUsesJsonPayloadType() {
        val event = ServerSentEvent("message", """{"type":"permission.asked","data":{}}""", null)
        assertEquals("permission.asked", event.type)
    }
}
