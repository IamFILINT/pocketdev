package dev.pocketdev.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeLauncherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun envBuilder(): RuntimeEnvBuilder {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr").apply { mkdirs() }
        return RuntimeEnvBuilder(
            prefix = prefix,
            home = File(files, "home"),
            dataDir = "/data/user/0/dev.pocketdev",
            legacyDataDir = "/data/data/dev.pocketdev",
        )
    }

    @Test
    fun withoutPreloadUsesLinker64AndBaseEnv() {
        val envBuilder = envBuilder()
        val launcher = RuntimeLauncher.withoutPreload(envBuilder)
        val program = File("/data/user/0/dev.pocketdev/files/usr/bin/bash")

        val command = launcher.command(program, "-c", "echo hi")
        val env = launcher.environment()

        assertEquals("/system/bin/linker64", command.first())
        assertEquals(program.absolutePath, command[1])
        assertEquals(listOf("-c", "echo hi"), command.drop(2))
        assertFalse("LD_PRELOAD must not be set without preload", env.containsKey("LD_PRELOAD"))
        assertEquals(envBuilder.baseEnv(), env)
    }

    @Test
    fun withPreloadAddsLdPreloadAndKeepsBaseEnv() {
        val envBuilder = envBuilder()
        val preload = File(envBuilder.baseEnv().getValue("PREFIX"), "lib/libtermux-exec-ld-preload.so")
        val launcher = RuntimeLauncher.withPreload(envBuilder, preload)
        val program = File("/data/user/0/dev.pocketdev/files/usr/bin/bash")

        val command = launcher.command(program)
        val env = launcher.environment()

        assertEquals("/system/bin/linker64", command.first())
        assertEquals(program.absolutePath, command[1])
        assertEquals(preload.absolutePath, env["LD_PRELOAD"])
        assertEquals(envBuilder.baseEnv()["PREFIX"], env["PREFIX"])
    }

    @Test
    fun planCarriesLinkerHopEnvironmentAndWorkingDirectory() {
        val envBuilder = envBuilder()
        val launcher = RuntimeLauncher.withoutPreload(envBuilder)
        val program = File(envBuilder.baseEnv().getValue("PREFIX"), "bin/bash")
        val workingDir = tmp.newFolder("project")

        val plan = launcher.plan(program, "-i", workingDir = workingDir)
        val home = envBuilder.baseEnv().getValue("HOME")

        assertEquals("/system/bin/linker64", plan.executable)
        assertEquals(launcher.command(program, "-i"), plan.argv)
        assertEquals(launcher.environment(), plan.environment)
        assertEquals(workingDir.absolutePath, plan.workingDirectory)
        assertTrue(plan.envArray.contains("HOME=$home"))
        assertFalse(plan.envArray.any { it.startsWith("LD_PRELOAD=") })
    }

    @Test
    fun planWithPreloadExposesLdPreload() {
        val envBuilder = envBuilder()
        val preload = File(envBuilder.baseEnv().getValue("PREFIX"), "lib/libtermux-exec-ld-preload.so")
        val launcher = RuntimeLauncher.withPreload(envBuilder, preload)
        val program = File(envBuilder.baseEnv().getValue("PREFIX"), "bin/bash")
        val workingDir = tmp.newFolder("preload-project")

        val plan = launcher.plan(program, workingDir = workingDir)

        assertEquals(preload.absolutePath, plan.environment["LD_PRELOAD"])
        assertTrue(plan.envArray.contains("LD_PRELOAD=${preload.absolutePath}"))
    }

    @Test
    fun customLinkerPathIsHonored() {
        val envBuilder = envBuilder()
        val launcher = RuntimeLauncher(envBuilder, linker64 = "/system/bin/linker", preload = null)
        val program = File("/tmp/prog")

        val command = launcher.command(program, "arg1")

        assertEquals("/system/bin/linker", command.first())
        assertEquals(program.absolutePath, command[1])
        assertEquals("arg1", command[2])
    }

    @Test
    fun execVarargRequiresNonEmptyArgv() {
        val envBuilder = envBuilder()
        val launcher = RuntimeLauncher.withoutPreload(envBuilder)
        try {
            launcher.exec()
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("argv"))
        }
    }
}
