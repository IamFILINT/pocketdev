package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Locale

class EditorPerformanceBaselineTest {
    private enum class Kind(val label: String, val language: EditorLanguage) {
        KOTLIN("kotlin", EditorLanguage.KOTLIN),
        JSON("json", EditorLanguage.JSON),
        MARKDOWN("markdown", EditorLanguage.MARKDOWN),
        PLAIN("plain", EditorLanguage.PLAIN_TEXT),
    }

    private data class Fixture(
        val kind: Kind,
        val targetBytes: Int,
        val text: String,
        val bytes: ByteArray,
        val newlineStyles: List<NewlineStyle>,
    )

    private data class Stats(val p50Ms: Double, val p95Ms: Double)

    companion object {
        private const val SAMPLES = 5
        private const val WARMUPS = 1
        private val SIZES = intArrayOf(10 * 1024, 100 * 1024, 250 * 1024, 500 * 1024, 1024 * 1024)
    }

    @Test
    fun recordsCorePerformanceBaselines() {
        emitMeta()
        for (targetBytes in SIZES) {
            for (kind in Kind.values()) {
                val fixture = buildFixture(kind, targetBytes)
                measureFixture(fixture)
            }
        }
    }

    private fun measureFixture(fixture: Fixture) {
        val query = "line"
        val decoded = measure {
            val snapshot = TextFileCodec.decode(
                bytes = fixture.bytes,
                sizeBytes = fixture.bytes.size.toLong(),
                lastModifiedEpochMs = 1L,
                truncated = false,
            )
            assertNotNull(snapshot)
            assertEquals(fixture.text, snapshot!!.text)
            snapshot
        }
        emit(fixture, "openDecode", decoded)

        val heapBefore = usedHeapBytes()
        val encoded = measure {
            TextFileCodec.encode(
                text = fixture.text,
                encoding = TextEncoding.UTF_8,
                newlineStyle = NewlineStyle.LF,
                newlineStyles = fixture.newlineStyles,
            )
        }
        emit(fixture, "saveEncode", encoded, "\"heapDeltaBytes\":${usedHeapBytes() - heapBefore}")

        val insertionOffset = fixture.text.length / 2
        val inserted = measure {
            val buffer = EditorBuffer(fixture.text)
            assertTrue(buffer.replace(insertionOffset, insertionOffset, "x"))
            assertEquals(
                fixture.text.substring(0, insertionOffset) + "x" + fixture.text.substring(insertionOffset),
                buffer.text,
            )
            assertEquals(TextLines.starts(buffer.text), buffer.lineStarts)
            buffer
        }
        emit(fixture, "singleCharInsert", inserted)

        val deleted = measure {
            val buffer = EditorBuffer(fixture.text)
            assertTrue(buffer.replace(insertionOffset, insertionOffset + 1, ""))
            assertEquals(
                fixture.text.substring(0, insertionOffset) + fixture.text.substring(insertionOffset + 1),
                buffer.text,
            )
            buffer
        }
        emit(fixture, "singleCharDelete", deleted)

        val lineIndex = measure {
            val starts = TextLines.starts(fixture.text)
            assertEquals(referenceLineStarts(fixture.text), starts)
            starts
        }
        emit(fixture, "lineIndexRebuild", lineIndex)

        val tokenized = measure {
            val tokens = SyntaxHighlighter.tokenize(fixture.text, fixture.kind.language)
            assertTrue(tokens.isNotEmpty())
            assertTrue(tokens.all { it.start <= it.end && it.end <= fixture.text.length })
            tokens
        }
        emit(fixture, "syntaxTokenize", tokenized)

        val found = measure {
            val matches = TextSearch.findAll(fixture.text, query)
            assertTrue(matches.isNotEmpty())
            matches
        }
        emit(fixture, "searchFindAll", found)

        val replaced = measure {
            val result = TextSearch.replaceAll(fixture.text, query, "target", false, false)
            assertTrue(result.second > 0)
            assertTrue(result.first.contains("target"))
            result
        }
        emit(fixture, "searchReplaceAll", replaced)

        val undoRedo = measure {
            val buffer = EditorBuffer(fixture.text)
            assertTrue(buffer.replace(insertionOffset, insertionOffset, "z"))
            assertTrue(buffer.undo())
            assertEquals(fixture.text, buffer.text)
            assertTrue(buffer.redo())
            assertTrue(buffer.text.length == fixture.text.length + 1)
            buffer
        }
        emit(fixture, "undoRedoCycle", undoRedo)
    }

    private fun buildFixture(kind: Kind, targetBytes: Int): Fixture {
        val builder = StringBuilder(targetBytes)
        var byteCount = 0
        var index = 0
        while (byteCount < targetBytes) {
            val chunk = chunk(kind, index)
            builder.append(chunk)
            byteCount += chunk.toByteArray(StandardCharsets.UTF_8).size
            index++
        }
        val text = builder.toString()
        val newlineStyles = Regex("\r\n|\n|\r").findAll(text).map { match ->
            when (match.value) {
                "\r\n" -> NewlineStyle.CRLF
                "\r" -> NewlineStyle.CR
                else -> NewlineStyle.LF
            }
        }.toList()
        val bytes = TextFileCodec.encode(
            text = text,
            encoding = TextEncoding.UTF_8,
            newlineStyle = NewlineStyle.LF,
            newlineStyles = newlineStyles,
        )
        return Fixture(kind, targetBytes, text, bytes, newlineStyles)
    }

    private fun chunk(kind: Kind, index: Int): String = when (kind) {
        Kind.KOTLIN -> "val café$index = \"line $index 🚀\"\n"
        Kind.JSON -> "{\"key\":\"line-$index\",\"value\":\"🚀\"},\n"
        Kind.MARKDOWN -> "## Heading $index  \nline $index with **bold** and 🚀  \n"
        Kind.PLAIN -> if (index % 8 == 0) {
            "plain long line 🚀 ${"x".repeat(256)}\n"
        } else {
            "plain line $index — 🚀\n"
        }
    }

    private fun measure(block: () -> Any?): Stats {
        repeat(WARMUPS) { block() }
        val samples = LongArray(SAMPLES) { measureOnce(block) }
        val sorted = samples.sorted()
        return Stats(percentileMs(sorted, 0.50), percentileMs(sorted, 0.95))
    }

    private fun measureOnce(block: () -> Any?): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun percentileMs(sorted: List<Long>, percentile: Double): Double {
        val rank = (percentile * (sorted.size - 1)).coerceIn(0.0, (sorted.size - 1).toDouble())
        val lower = rank.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        val value = sorted[lower] + (sorted[upper] - sorted[lower]) * (rank - lower)
        return value / 1_000_000.0
    }

    private fun referenceLineStarts(text: String): List<Int> = buildList {
        add(0)
        text.forEachIndexed { index, char ->
            if (char == '\n') add(index + 1)
        }
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun emitMeta() {
        println(
            "M7_5_BENCH_META {" +
                "\"runner\":\"core-jvm\"," +
                "\"osArch\":\"${System.getProperty("os.arch")}\"," +
                "\"jvm\":\"${System.getProperty("java.version")}\"," +
                "\"targetBytes\":${SIZES.joinToString(",")}," +
                "\"generation\":\"deterministic-utf8-chunks\"," +
                "\"warmups\":$WARMUPS," +
                "\"samples\":$SAMPLES," +
                "\"percentileMethod\":\"linear-interpolation\"," +
                "\"clock\":\"System.nanoTime\"" +
                "}",
        )
    }

    private fun emit(fixture: Fixture, operation: String, stats: Stats, extra: String = "") {
        println(
            "M7_5_BENCH_CORE {" +
                "\"fixture\":\"${fixture.kind.label}\"," +
                "\"targetBytes\":${fixture.targetBytes}," +
                "\"actualBytes\":${fixture.bytes.size}," +
                "\"language\":\"${fixture.kind.language.name}\"," +
                "\"op\":\"$operation\"," +
                "\"p50Ms\":${fmt(stats.p50Ms)}," +
                "\"p95Ms\":${fmt(stats.p95Ms)}" +
                (if (extra.isEmpty()) "" else ",$extra") +
                "}",
        )
    }
}
