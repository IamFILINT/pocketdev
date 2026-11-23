package dev.pocketdev.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionRequestTest {

    @Test
    fun parsesPermissionAskedPayload() {
        val event = ServerSentEvent(
            event = null,
            data = """{"type":"permission.asked","data":{"id":"per_1","sessionID":"ses_1","action":"bash","resources":["ls"],"message":"Allow ls?"}}""",
            id = null,
        )

        val request = PermissionRequest.fromEvent(event)

        assertEquals("per_1", request?.id)
        assertEquals("ses_1", request?.sessionId)
        assertEquals("bash", request?.action)
        assertEquals(listOf("ls"), request?.resources)
        assertEquals("Allow ls?", request?.message)
    }

    @Test
    fun ignoresOtherEvents() {
        val event = ServerSentEvent("permission.replied", """{"data":{"id":"per_1"}}""", null)
        assertNull(PermissionRequest.fromEvent(event))
    }

    @Test
    fun ignoresMalformedPayload() {
        val event = ServerSentEvent("permission.asked", "not json", null)
        assertNull(PermissionRequest.fromEvent(event))
    }
}
