package dev.pocketdev.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class BootstrapHealthTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun prefixWith(vararg entries: String): File {
        val prefix = tmp.newFolder("usr")
        entries.forEach { relative ->
            val file = File(prefix, relative)
            file.parentFile.mkdirs()
            file.writeText("x")
            file.setExecutable(true)
        }
        return prefix
    }

    @Test
    fun `healthy prefix passes every structural check`() {
        val prefix = prefixWith("bin/bash", "bin/pkg", "bin/apt", "etc/apt/sources.list")
        Files.createSymbolicLink(File(prefix, "bin/sh").toPath(), java.nio.file.Path.of("bash"))

        val report = BootstrapHealth().check(prefix)

        assertTrue(report.failureSummary(), report.ok)
    }

    @Test
    fun `missing required entry is reported`() {
        val prefix = prefixWith("bin/bash")

        val report = BootstrapHealth().check(prefix)

        assertFalse(report.ok)
        assertTrue(report.failureSummary().contains("bin/pkg"))
    }

    @Test
    fun `leftover SYMLINKS file is reported`() {
        val prefix = prefixWith("bin/bash", "bin/pkg", "bin/apt", "etc/apt/sources.list", "SYMLINKS.txt")

        val report = BootstrapHealth().check(prefix)

        assertFalse(report.ok)
        assertTrue(report.failureSummary().contains("symlinks-processed"))
    }

    @Test
    fun `symlink escaping the prefix is reported`() {
        val prefix = prefixWith("bin/bash", "bin/pkg", "bin/apt", "etc/apt/sources.list")
        Files.createSymbolicLink(
            File(prefix, "bin/evil").toPath(),
            java.nio.file.Path.of("../../../../etc/passwd"),
        )

        val report = BootstrapHealth().check(prefix)

        assertFalse(report.ok)
        assertTrue(report.failureSummary().contains("symlinks-contained"))
    }

    @Test
    fun `non-executable required binary is reported`() {
        val prefix = prefixWith("bin/pkg", "bin/apt", "etc/apt/sources.list")
        File(prefix, "bin/bash").apply { parentFile.mkdirs(); writeText("x") }

        val report = BootstrapHealth().check(prefix)

        assertFalse(report.ok)
        assertTrue(report.failureSummary().contains("exec:bin/bash"))
    }

    @Test
    fun `missing prefix fails cleanly`() {
        val report = BootstrapHealth().check(File(tmp.root, "nope"))

        assertFalse(report.ok)
        assertTrue(report.failureSummary().contains("prefix-exists"))
    }
}
