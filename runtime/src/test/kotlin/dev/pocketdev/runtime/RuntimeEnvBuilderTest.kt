package dev.pocketdev.runtime

import dev.pocketdev.core.settings.AgentModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeEnvBuilderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun builder(): Pair<RuntimeEnvBuilder, File> {
        val files = tmp.newFolder("files")
        val prefix = File(files, "usr").apply { mkdirs() }
        val home = File(files, "home")
        return RuntimeEnvBuilder(
            prefix = prefix,
            home = home,
            dataDir = "/data/user/0/dev.pocketdev",
            legacyDataDir = "/data/data/dev.pocketdev",
        ) to prefix
    }

    @Test
    fun baseEnvFollowsBootstrapLayout() {
        val (envBuilder, prefix) = builder()
        val env = envBuilder.baseEnv()

        assertEquals(prefix.absolutePath, env["PREFIX"])
        assertEquals(File(prefix.parentFile, "home").absolutePath, env["HOME"])
        assertEquals(File(prefix, "bin").absolutePath, env["PATH"]?.split(":")?.first())
        assertTrue(env.getValue("PATH").endsWith("/system/bin"))
        assertEquals(File(prefix, "tmp").absolutePath, env["TMPDIR"])
        assertEquals(File(prefix, "lib").absolutePath, env["LD_LIBRARY_PATH"])
        assertEquals("xterm-256color", env["TERM"])
        assertEquals("en_US.UTF-8", env["LANG"])
    }

    @Test
    fun baseEnvCarriesTermuxOverridesForTheRealDataDirAndPrefix() {
        val (envBuilder, prefix) = builder()
        val env = envBuilder.baseEnv()

        assertEquals("/data/user/0/dev.pocketdev", env["TERMUX_APP__DATA_DIR"])
        assertEquals("/data/data/dev.pocketdev", env["TERMUX_APP__LEGACY_DATA_DIR"])
        assertEquals(prefix.absolutePath, env["TERMUX__PREFIX"])
        assertFalse("LD_PRELOAD must be child-only", env.containsKey("LD_PRELOAD"))
    }

    @Test
    fun childEnvAddsPreloadWithoutDroppingBaseValues() {
        val (envBuilder, prefix) = builder()
        val preload = File(prefix, "lib/libtermux-exec-ld-preload.so")

        val env = envBuilder.childEnv(preload)

        assertEquals(preload.absolutePath, env["LD_PRELOAD"])
        assertEquals(prefix.absolutePath, env["PREFIX"])
        assertEquals(prefix.absolutePath, env["TERMUX__PREFIX"])
    }

    @Test
    fun agentCredentialIsAddedToBaseAndChildEnvironment() {
        val secret = "sk-env-secret"
        val config = RuntimeAgentConfig(AgentModelSelection("openai", "gpt-5"), secret)
        val (baseBuilder, prefix) = builder()
        val envBuilder = baseBuilder.withAgentConfig(config)

        assertEquals(config.configContent, envBuilder.baseEnv()[RuntimeAgentConfig.CONFIG_ENV])
        assertEquals(secret, envBuilder.baseEnv()[RuntimeAgentConfig.CREDENTIAL_ENV])
        val childEnv = envBuilder.childEnv(File(prefix, "lib/libtermux-exec-ld-preload.so"))
        assertEquals(config.configContent, childEnv[RuntimeAgentConfig.CONFIG_ENV])
        assertEquals(secret, childEnv[RuntimeAgentConfig.CREDENTIAL_ENV])
        assertFalse("original builder must stay unchanged", baseBuilder.baseEnv().containsKey(RuntimeAgentConfig.CONFIG_ENV))
        assertFalse("original builder must stay unchanged", baseBuilder.baseEnv().containsKey(RuntimeAgentConfig.CREDENTIAL_ENV))
    }
}
