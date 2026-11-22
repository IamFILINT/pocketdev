package dev.pocketdev.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun readsServerStyleObject() {
        val value = Json.parse(
            """{"version":"2.3.2","pid":42,"hostname":null,"urls":["http://127.0.0.1:4096"],"nested":{"a":[1,2,3]}}""",
        ) as Map<*, *>

        assertEquals("2.3.2", value["version"])
        assertEquals(42L, value["pid"])
        assertNull(value["hostname"])
        assertTrue(value["urls"] is List<*>)
        assertEquals(3, (value["nested"] as Map<*, *>)["a"]?.let { (it as List<*>).size })
    }

    @Test
    fun readsEscapesAndUnicode() {
        val value = Json.parse("""{"text":"line\nquote\" slash\\ unicodeA"}""") as Map<*, *>
        assertEquals("line\nquote\" slash\\ unicodeA", value["text"])
    }

    @Test
    fun readsDoublesAndBooleans() {
        val value = Json.parse("""{"cost":1.5,"ok":true,"bad":false}""") as Map<*, *>
        assertEquals(1.5, value["cost"])
        assertEquals(true, value["ok"])
        assertEquals(false, value["bad"])
    }

    @Test
    fun parsesEmptyObjectAndArray() {
        assertTrue(Json.parse("{}") is Map<*, *>)
        assertTrue(Json.parse("[]") is List<*>)
    }

    @Test(expected = Json.JsonException::class)
    fun rejectsTrailingContent() {
        Json.parse("""{"a":1} trailing""")
    }

    @Test(expected = Json.JsonException::class)
    fun rejectsMalformedObject() {
        Json.parse("""{"a": }""")
    }
}
