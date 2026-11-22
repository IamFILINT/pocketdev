package dev.pocketdev.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimePreloadDeployerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun reportsMissingPreloadBeforeProvisioning() {
        val prefix = tmp.newFolder("usr")
        val deployer = RuntimePreloadDeployer(prefix)

        assertFalse(deployer.isDeployed())
        assertNull(deployer.preloadFile())
    }

    @Test
    fun reportsPreloadShippedByThePinnedBootstrap() {
        val prefix = tmp.newFolder("usr")
        val lib = File(prefix, "lib").apply { mkdirs() }
        val preload = File(lib, "libtermux-exec-ld-preload.so").apply { writeText("stub") }

        val deployer = RuntimePreloadDeployer(prefix)

        assertTrue(deployer.isDeployed())
        assertEquals(preload, deployer.preloadFile())
    }
}
