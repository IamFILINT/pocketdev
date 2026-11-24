package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocketdev.core.runtime.RuntimeState
import dev.pocketdev.core.terminal.TerminalSnapshot
import dev.pocketdev.runtime.RuntimeLaunchPlan
import dev.pocketdev.terminal.PtyExitKind
import dev.pocketdev.terminal.TerminalSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class PtyReliabilityTest {

    @Before
    fun assumeBionicPty() {
        Assume.assumeTrue(
            "PTY reliability runs only with bionicPty=true (see runtime-spike.yml)",
            InstrumentationRegistry.getArguments().getString("bionicPty") == "true",
        )
    }

    @Test
    fun reportsNormalAndSignalExitThroughPty() {
        val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
        val runtime = app.container.runtime
        assertTrue("runtime provisioning failed", runtime.provision() is RuntimeState.Ready)
        val root = app.container.projectsRoot.apply { mkdirs() }

        val normal = TerminalSession(
            name = "exit-seven",
            plan = runtime.launchPlan(runtime.bionicShell(), "-c", "exit 7", workingDir = root),
        )
        try {
            val normalExit = normal.waitForExit(15_000)
            assertEquals(PtyExitKind.EXITED, normalExit.kind)
            assertEquals(7, normalExit.code)
        } finally {
            normal.close()
        }

        val signalled = TerminalSession(
            name = "signalled",
            plan = runtime.launchPlan(runtime.bionicShell(), "-c", "read -t 60", workingDir = root),
        )
        try {
            Thread.sleep(500)
            signalled.terminate()
            val signalExit = signalled.waitForExit(15_000)
            assertEquals(PtyExitKind.SIGNALED, signalExit.kind)
            assertEquals(9, signalExit.code)
        } finally {
            signalled.close()
        }
    }

    @Test
    fun reportsLaunchFailureToCaller() {
        val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
        val runtime = app.container.runtime
        assertTrue("runtime provisioning failed", runtime.provision() is RuntimeState.Ready)
        val missing = File(app.container.projectsRoot, "missing-binary").absolutePath

        var message: String? = null
        try {
            TerminalSession(
                name = "bad-executable",
                plan = RuntimeLaunchPlan(
                    executable = missing,
                    argv = listOf(missing),
                    environment = runtime.childEnv(),
                    workingDirectory = app.container.projectsRoot.absolutePath,
                ),
            )
        } catch (e: IllegalStateException) {
            message = e.message
        }
        assertNotNull(message)
        assertTrue(message!!.contains("PTY child setup failed"))
    }

    @Test
    fun survivesRapidLifecycleAndConcurrentListenerChanges() {
        val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
        val runtime = app.container.runtime
        assertTrue("runtime provisioning failed", runtime.provision() is RuntimeState.Ready)
        val root = app.container.projectsRoot.apply { mkdirs() }

        repeat(6) { index ->
            val session = TerminalSession(
                name = "stress-$index",
                plan = runtime.launchPlan(runtime.bionicShell(), "-c", "read -t 60", workingDir = root),
            )
            val listener: (TerminalSnapshot) -> Unit = { }
            val failures = Collections.synchronizedList(mutableListOf<Throwable>())
            val threads = (1..3).map {
                Thread {
                    repeat(20) {
                        runCatching {
                            session.addSnapshotListener(listener)
                            session.removeSnapshotListener(listener)
                        }.onFailure { error -> failures.add(error) }
                    }
                }
            }
            threads.forEach(Thread::start)
            session.resize(72, 24)
            session.send("echo stress-$index\r")
            threads.forEach(Thread::join)

            session.close()
            session.close()
            assertFalse(session.isRunning())
            assertTrue(failures.toString(), failures.isEmpty())
        }
    }
}
