package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.editor.EditorDocumentId
import dev.pocketdev.core.editor.EditorDraft
import dev.pocketdev.core.editor.EditorDraftRead
import dev.pocketdev.core.editor.EditorDraftStore
import dev.pocketdev.core.editor.EditorSaveSnapshot
import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.NewlineStyle
import dev.pocketdev.core.editor.TextFileCodec
import dev.pocketdev.core.editor.TextEncoding
import dev.pocketdev.core.editor.TextFileSnapshot
import dev.pocketdev.core.workspace.WorkspaceResult
import dev.pocketdev.ui.editor.DefaultEditorDraftRepository
import dev.pocketdev.ui.editor.DefaultEditorWorkspaceGateway
import dev.pocketdev.ui.editor.EditorDraftRepository
import dev.pocketdev.ui.editor.EditorViewModel
import dev.pocketdev.ui.editor.EditorWorkspaceGateway
import dev.pocketdev.ui.projects.CreateProjectResult
import dev.pocketdev.ui.projects.ProjectsViewModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var projectsViewModel: ProjectsViewModel
    private lateinit var editorViewModel: EditorViewModel
    private lateinit var projectsRoot: File
    private lateinit var projectRoot: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        projectsViewModel = ProjectsViewModel(app)
        projectsRoot = app.container.projectsRoot
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        app.container.paths.drafts.deleteRecursively()
        app.container.paths.drafts.mkdirs()
        val created = projectsViewModel.createProject("editor-test")
        assertTrue(created is CreateProjectResult.Created)
        val project = projectsViewModel.uiState.value.projects.first { it.name == "editor-test" }
        app.container.selectProject(project.id)
        projectRoot = File(project.rootPath)
        editorViewModel = EditorViewModel(app)
    }

    @After
    fun tearDown() {
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        app.container.paths.drafts.deleteRecursively()
    }

    private fun documentId(path: String): EditorDocumentId {
        val project = app.container.selectedProject.value!!
        return EditorDocumentId(project.id, project.rootPath, path)
    }

    private fun viewModelWith(
        repository: EditorDraftRepository,
        workspaceGateway: EditorWorkspaceGateway = DefaultEditorWorkspaceGateway(app.container),
    ) = EditorViewModel(
        application = app,
        ioDispatcher = Dispatchers.IO,
        mainDispatcher = Dispatchers.Main,
        workspaceGateway = workspaceGateway,
        draftRepository = repository,
    )

    private fun await(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(
            "Condition was not met within ${timeoutMs}ms: ${editorViewModel.uiState.value}",
            condition(),
        )
    }

    private fun awaitFor(
        viewModel: EditorViewModel,
        timeoutMs: Long = 10_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(
            "Condition was not met within ${timeoutMs}ms: ${viewModel.uiState.value}",
            condition(),
        )
    }

    private fun awaitIdle() {
        await { editorViewModel.uiState.value.busyLabel == null }
    }

    @Test
    fun opensEditsAndSavesTextFileThroughWorkspace() {
        val file = File(projectRoot, "main.kt")
        file.writeText("val a = 1\n")

        editorViewModel.open("main.kt")
        await {
            editorViewModel.uiState.value.activePath == "main.kt" &&
                editorViewModel.uiState.value.text == "val a = 1\n"
        }

        val updated = "val a = 1\nval b = 2\n"
        editorViewModel.onTextChanged(updated, 0..updated.length)
        assertTrue(editorViewModel.uiState.value.dirty)
        assertEquals("Kotlin", editorViewModel.uiState.value.tabs.first().language.label)

        editorViewModel.save()
        awaitIdle()
        assertFalse(editorViewModel.uiState.value.dirty)
        assertEquals(updated, file.readText())
    }

    @Test
    fun newerEditStaysDirtyWhenEarlierSaveCompletes() {
        val file = File(projectRoot, "save-race.txt")
        file.writeText("one")
        val gateway = SuspendedSaveGateway(file)
        editorViewModel = EditorViewModel(app, Dispatchers.IO, Dispatchers.Main, gateway)

        editorViewModel.open("save-race.txt")
        await { editorViewModel.uiState.value.activePath == "save-race.txt" }

        editorViewModel.onTextChanged("two", 0..3)
        editorViewModel.save()
        gateway.awaitWriteStarted()
        editorViewModel.onTextChanged("three", 0..5)
        gateway.releaseWrite()
        awaitIdle()

        assertTrue(editorViewModel.uiState.value.dirty)
        assertEquals("two", file.readText())
    }

    @Test
    fun projectSwitchDoesNotRetargetAnOpenTab() {
        val boundRoot = projectRoot
        File(boundRoot, "bound.txt").writeText("one")
        editorViewModel.open("bound.txt")
        await { editorViewModel.uiState.value.activePath == "bound.txt" }
        editorViewModel.onTextChanged("edited", 0..3)

        val created = projectsViewModel.createProject("editor-test-b")
        assertTrue(created is CreateProjectResult.Created)
        val second = projectsViewModel.uiState.value.projects.first { it.name == "editor-test-b" }
        app.container.selectProject(second.id)

        editorViewModel.save()
        awaitIdle()

        assertNull(editorViewModel.uiState.value.error)
        assertEquals("edited", File(boundRoot, "bound.txt").readText())
        assertFalse(File(second.rootPath, "bound.txt").exists())
    }

    @Test
    fun dirtyExternalChangeIsPresentedAsConflictUntilResolved() {
        val file = File(projectRoot, "notes.txt")
        file.writeText("local source")

        editorViewModel.open("notes.txt")
        await { editorViewModel.uiState.value.activePath == "notes.txt" }
        editorViewModel.onTextChanged("local edit", 0.."local edit".length)
        assertTrue(editorViewModel.uiState.value.dirty)

        file.writeText("agent changed this")
        file.setLastModified(file.lastModified() + 60_000L)
        editorViewModel.checkExternalChanges()
        await { editorViewModel.uiState.value.externalChange != null }

        val conflict = editorViewModel.uiState.value.externalChange
        assertNotNull(conflict)
        assertTrue(conflict!!.dirty)
        assertEquals("notes.txt", conflict.path)

        editorViewModel.keepLocalChanges()
        awaitIdle()
        assertNull(editorViewModel.uiState.value.externalChange)
        assertEquals("local edit", file.readText())
    }

    @Test
    fun sameSizeExternalReplacementIsPresentedAsConflict() {
        val file = File(projectRoot, "same-size.txt")
        file.writeText("aaaa")
        val originalModified = file.lastModified()

        editorViewModel.open("same-size.txt")
        await { editorViewModel.uiState.value.activePath == "same-size.txt" }
        editorViewModel.onTextChanged("aXaa", 0..4)
        assertTrue(editorViewModel.uiState.value.dirty)

        file.writeText("bbbb")
        file.setLastModified(originalModified)
        editorViewModel.checkExternalChanges()
        await { editorViewModel.uiState.value.externalChange != null }

        val conflict = editorViewModel.uiState.value.externalChange
        assertNotNull(conflict)
        assertTrue(conflict!!.dirty)
        assertEquals("bbbb", file.readText())
    }

    @Test
    fun cleanExternalChangeReloadsAutomatically() {
        val file = File(projectRoot, "shared.txt")
        file.writeText("first version")

        editorViewModel.open("shared.txt")
        await { editorViewModel.uiState.value.activePath == "shared.txt" }

        file.writeText("second version")
        file.setLastModified(file.lastModified() + 60_000L)
        editorViewModel.checkExternalChanges()
        await { editorViewModel.uiState.value.text == "second version" }

        assertNull(editorViewModel.uiState.value.externalChange)
        assertFalse(editorViewModel.uiState.value.dirty)
    }

    @Test
    fun tabsSupportActivationAndDirtyCloseGuard() {
        File(projectRoot, "one.txt").writeText("one")
        File(projectRoot, "two.txt").writeText("two")

        editorViewModel.open("one.txt")
        await { editorViewModel.uiState.value.activePath == "one.txt" }
        editorViewModel.open("two.txt")
        await { editorViewModel.uiState.value.activePath == "two.txt" && editorViewModel.uiState.value.tabs.size == 2 }

        editorViewModel.activateTab("one.txt")
        await { editorViewModel.uiState.value.activePath == "one.txt" }

        editorViewModel.onTextChanged("changed", 0.."changed".length)
        editorViewModel.closeTab("one.txt")
        assertEquals("one.txt", editorViewModel.uiState.value.activePath)
        assertNotNull(editorViewModel.uiState.value.error)

        editorViewModel.discardChanges()
        awaitIdle()
        editorViewModel.closeTab("one.txt")
        await { editorViewModel.uiState.value.tabs.none { tab -> tab.path == "one.txt" } }
        assertEquals("two.txt", editorViewModel.uiState.value.activePath)
    }

    @Test
    fun searchReplaceAndFormatAreEditableStateOperations() {
        val file = File(projectRoot, "search.txt")
        file.writeText("foo foo\n")

        editorViewModel.open("search.txt")
        await { editorViewModel.uiState.value.activePath == "search.txt" }

        editorViewModel.setSearchVisible(true)
        editorViewModel.setSearchQuery("foo")
        await { editorViewModel.uiState.value.matches.size == 2 }

        editorViewModel.setReplaceText("bar")
        editorViewModel.replaceAll()
        awaitIdle()
        assertEquals("bar bar\n", editorViewModel.uiState.value.text)

        editorViewModel.formatActive()
        awaitIdle()
        assertEquals("bar bar\n", editorViewModel.uiState.value.text)

        editorViewModel.save()
        awaitIdle()
        assertEquals("bar bar\n", file.readText())
    }

    @Test
    fun binaryAndMissingFilesReportExplicitFailures() {
        File(projectRoot, "binary.dat").writeBytes(byteArrayOf(0x00, 0x01))

        editorViewModel.open("binary.dat")
        await { editorViewModel.uiState.value.error != null }
        assertTrue(editorViewModel.uiState.value.error!!.contains("Binary"))

        editorViewModel.clearFeedback()
        editorViewModel.open("missing.txt")
        await { editorViewModel.uiState.value.error != null }
        assertTrue(editorViewModel.uiState.value.error!!.contains("Not found"))
    }

    @Test
    fun formattingIsOneUndoStepAndUnsupportedLanguagesAreRejected() {
        val code = File(projectRoot, "main.kt")
        val original = "fun main() {\n\tprintln(\"hi\")   \n}"
        code.writeText(original)
        editorViewModel.open("main.kt")
        await { editorViewModel.uiState.value.activePath == "main.kt" }

        editorViewModel.formatActive()
        val formatted = "fun main() {\n    println(\"hi\")\n}"
        assertEquals(formatted, editorViewModel.uiState.value.text)
        editorViewModel.undo()
        assertEquals(original, editorViewModel.uiState.value.text)
        editorViewModel.redo()
        assertEquals(formatted, editorViewModel.uiState.value.text)

        val markdown = File(projectRoot, "notes.md")
        val document = "first line  \nsecond line"
        markdown.writeText(document)
        editorViewModel.open("notes.md")
        await { editorViewModel.uiState.value.activePath == "notes.md" }
        editorViewModel.formatActive()
        assertEquals(document, editorViewModel.uiState.value.text)
        assertTrue(editorViewModel.uiState.value.error!!.contains("No safe formatter"))
    }

    @Test
    fun unicodeEditsUndoRedoKeepSelectionInBounds() {
        val file = File(projectRoot, "unicode.txt")
        file.writeText("")
        editorViewModel.open("unicode.txt")
        await { editorViewModel.uiState.value.activePath == "unicode.txt" }

        val text = "سلام 🌍\u0301a"
        editorViewModel.onTextChanged(text, text.length..text.length)
        assertEquals(text, editorViewModel.uiState.value.text)
        assertTrue(editorViewModel.uiState.value.selection.last <= editorViewModel.uiState.value.text.length)

        editorViewModel.undo()
        assertTrue(editorViewModel.uiState.value.selection.last <= editorViewModel.uiState.value.text.length)
        editorViewModel.redo()
        assertEquals(text, editorViewModel.uiState.value.text)
        assertTrue(editorViewModel.uiState.value.selection.last <= editorViewModel.uiState.value.text.length)
    }

    @Test
    fun replaceAllWithSupplementaryUnicodeKeepsSelectionInBounds() {
        val file = File(projectRoot, "replace.txt")
        file.writeText("a b a")
        editorViewModel.open("replace.txt")
        await { editorViewModel.uiState.value.activePath == "replace.txt" }

        editorViewModel.setSearchQuery("a")
        editorViewModel.setReplaceText("🚀")
        editorViewModel.replaceAll()

        assertEquals("🚀 b 🚀", editorViewModel.uiState.value.text)
        assertTrue(editorViewModel.uiState.value.selection.last <= editorViewModel.uiState.value.text.length)
    }

    @Test
    fun unsavedDraftPersistsAndRecoversAfterRestart() {
        val gateway = InMemoryWorkspaceGateway("original")
        val repository = InMemoryDraftRepository()
        val id = documentId("draft.txt")
        editorViewModel = viewModelWith(repository, gateway)

        editorViewModel.open("draft.txt")
        await { editorViewModel.uiState.value.activePath == "draft.txt" }
        editorViewModel.onTextChanged("draft body", 0.."draft body".length)
        awaitFor(editorViewModel) { repository.hasDraft(id) }
        assertEquals("original", gateway.text)

        val recovered = viewModelWith(repository, gateway)
        recovered.open("draft.txt")
        awaitFor(recovered) {
            recovered.uiState.value.text == "draft body" &&
                recovered.uiState.value.draftRecovery?.conflict == false
        }
        assertTrue(recovered.uiState.value.dirty)

        recovered.save()
        awaitFor(recovered) {
            recovered.uiState.value.busyLabel == null && !recovered.uiState.value.dirty
        }
        assertEquals("draft body", gateway.text)
        awaitFor(recovered) { !repository.hasDraft(id) }
    }

    @Test
    fun conflictingDraftBlocksSaveUntilExplicitChoice() {
        val gateway = InMemoryWorkspaceGateway("original")
        val repository = InMemoryDraftRepository()
        val id = documentId("conflict.txt")
        editorViewModel = viewModelWith(repository, gateway)

        editorViewModel.open("conflict.txt")
        await { editorViewModel.uiState.value.activePath == "conflict.txt" }
        editorViewModel.onTextChanged("local draft", 0.."local draft".length)
        awaitFor(editorViewModel) { repository.hasDraft(id) }
        assertEquals("original", gateway.text)

        gateway.overwrite("disk change")
        val recovered = viewModelWith(repository, gateway)
        recovered.open("conflict.txt")
        awaitFor(recovered) {
            recovered.uiState.value.draftRecovery?.conflict == true &&
                recovered.uiState.value.text == "disk change"
        }

        recovered.save()
        assertTrue(recovered.uiState.value.error!!.contains("Resolve the recovered draft conflict"))
        assertEquals("disk change", gateway.text)

        recovered.restoreDraft()
        awaitFor(recovered) {
            recovered.uiState.value.text == "local draft" &&
                recovered.uiState.value.draftRecovery?.conflict == false
        }
        recovered.save()
        awaitFor(recovered) {
            recovered.uiState.value.busyLabel == null && !recovered.uiState.value.dirty
        }
        assertEquals("local draft", gateway.text)
        awaitFor(recovered) { !repository.hasDraft(id) }
    }

    @Test
    fun keepingDiskFileDeletesConflictingDraft() {
        val gateway = InMemoryWorkspaceGateway("original")
        val repository = InMemoryDraftRepository()
        val id = documentId("keep-disk.txt")
        editorViewModel = viewModelWith(repository, gateway)

        editorViewModel.open("keep-disk.txt")
        await { editorViewModel.uiState.value.activePath == "keep-disk.txt" }
        editorViewModel.onTextChanged("local draft", 0.."local draft".length)
        awaitFor(editorViewModel) { repository.hasDraft(id) }
        assertEquals("original", gateway.text)

        gateway.overwrite("disk change")
        val recovered = viewModelWith(repository, gateway)
        recovered.open("keep-disk.txt")
        awaitFor(recovered) { recovered.uiState.value.draftRecovery?.conflict == true }

        recovered.keepDiskDraft()
        awaitFor(recovered) {
            recovered.uiState.value.draftRecovery == null &&
                recovered.uiState.value.text == "disk change"
        }
        assertEquals("disk change", gateway.text)
        awaitFor(recovered) { !repository.hasDraft(id) }
    }

    @Test
    fun unreadableDraftIsReportedAndRawBytesAreRetained() {
        val gateway = InMemoryWorkspaceGateway("original")
        val repository = InMemoryDraftRepository()
        val id = documentId("corrupt.txt")
        editorViewModel = viewModelWith(repository, gateway)

        editorViewModel.open("corrupt.txt")
        await { editorViewModel.uiState.value.activePath == "corrupt.txt" }
        editorViewModel.onTextChanged("draft body", 0.."draft body".length)
        awaitFor(editorViewModel) { repository.hasDraft(id) }
        assertEquals("original", gateway.text)

        val raw = "{\"schemaVersion\":99,\"text\":\"keep me\"}"
        repository.putRaw(id, raw)

        val recovered = viewModelWith(repository, gateway)
        recovered.open("corrupt.txt")
        awaitFor(recovered) {
            recovered.uiState.value.error?.contains("unreadable") == true
        }
        assertEquals("original", recovered.uiState.value.text)
        assertEquals(raw, repository.raw(id))
    }

    private class InMemoryWorkspaceGateway(
        initialText: String,
    ) : EditorWorkspaceGateway {
        @Volatile
        var text: String = initialText
            private set

        override suspend fun readTextFile(documentId: EditorDocumentId): WorkspaceResult<TextFileSnapshot> =
            WorkspaceResult.Success(snapshot())

        override suspend fun writeTextFile(request: EditorSaveSnapshot): WorkspaceResult<FileSignature> {
            text = request.text
            return WorkspaceResult.Success(snapshot().signature)
        }

        fun overwrite(value: String) {
            text = value
        }

        private fun snapshot(): TextFileSnapshot {
            val current = text
            val styles = Regex("\r\n|\n|\r").findAll(current).map { match ->
                when (match.value) {
                    "\r\n" -> NewlineStyle.CRLF
                    "\r" -> NewlineStyle.CR
                    else -> NewlineStyle.LF
                }
            }.toList()
            val bytes = TextFileCodec.encode(
                text = current,
                encoding = TextEncoding.UTF_8,
                newlineStyle = NewlineStyle.LF,
                newlineStyles = styles,
            )
            return TextFileSnapshot(
                text = current,
                encoding = TextEncoding.UTF_8,
                newlineStyle = NewlineStyle.LF,
                newlineStyles = styles,
                signature = FileSignature.fromBytes(bytes, 1L),
                truncated = false,
                sizeBytes = bytes.size.toLong(),
            )
        }
    }

    private class InMemoryDraftRepository : EditorDraftRepository {
        private val drafts = ConcurrentHashMap<String, EditorDraft>()
        private val raw = ConcurrentHashMap<String, String>()

        override suspend fun save(draft: EditorDraft) {
            drafts[draft.documentId.key] = draft
            raw[draft.documentId.key] = EditorDraftStore.serialize(draft)
        }

        override suspend fun load(documentId: EditorDocumentId): EditorDraftRead =
            raw[documentId.key]?.let(EditorDraftStore::read) ?: EditorDraftRead.Missing

        override suspend fun delete(documentId: EditorDocumentId) {
            drafts.remove(documentId.key)
            raw.remove(documentId.key)
        }

        fun hasDraft(documentId: EditorDocumentId) = drafts.containsKey(documentId.key)

        fun putRaw(documentId: EditorDocumentId, text: String) {
            drafts.remove(documentId.key)
            raw[documentId.key] = text
        }

        fun raw(documentId: EditorDocumentId) = raw[documentId.key]
    }

    private class SuspendedSaveGateway(
        private val target: File,
    ) : EditorWorkspaceGateway {
        private val writeStarted = CountDownLatch(1)
        private val releaseSave = CompletableDeferred<Unit>()

        override suspend fun readTextFile(documentId: EditorDocumentId): WorkspaceResult<TextFileSnapshot> {
            val bytes = target.readBytes()
            val snapshot = TextFileCodec.decode(
                bytes = bytes,
                sizeBytes = target.length(),
                lastModifiedEpochMs = target.lastModified(),
                truncated = false,
            ) ?: return WorkspaceResult.Failure("Binary file cannot be edited")
            return WorkspaceResult.Success(snapshot)
        }

        override suspend fun writeTextFile(request: EditorSaveSnapshot): WorkspaceResult<FileSignature> {
            writeStarted.countDown()
            releaseSave.await()
            val bytes = TextFileCodec.encode(
                text = request.text,
                encoding = request.encoding,
                newlineStyle = request.newlineStyle,
                newlineStyles = request.newlineStyles,
            )
            target.writeBytes(bytes)
            return WorkspaceResult.Success(FileSignature.fromBytes(bytes, target.lastModified()))
        }

        fun awaitWriteStarted() {
            assertTrue(
                "Save did not start within 10s: ${target.path}",
                writeStarted.await(10, TimeUnit.SECONDS),
            )
        }

        fun releaseWrite() {
            releaseSave.complete(Unit)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class EditorDraftRepositoryTest {
    private lateinit var app: PocketDevApplication
    private lateinit var projectsRoot: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        projectsRoot = app.container.projectsRoot
        ProjectsViewModel(app).clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        app.container.paths.drafts.deleteRecursively()
        app.container.paths.drafts.mkdirs()
    }

    @After
    fun tearDown() {
        projectsRoot.deleteRecursively()
        app.container.paths.drafts.deleteRecursively()
    }

    @Test
    fun storesDraftsOnlyInAppPrivateDirectoryAndRetainsUnreadableRawBytes() {
        val id = EditorDocumentId("workspace", "/pocketdev/projects", "nested/draft.txt")
        val repository = DefaultEditorDraftRepository(app.container)
        val draft = EditorDraft(
            schemaVersion = 1,
            documentId = id,
            text = "line one\nline two\r\n",
            bufferRevision = 3L,
            persistedRevision = 1L,
            baselineSignature = FileSignature(20L, 123L, "abc123"),
            encoding = TextEncoding.UTF_8,
            newlineStyle = NewlineStyle.LF,
            newlineStyles = listOf(NewlineStyle.LF, NewlineStyle.CRLF),
            updatedAtEpochMs = 456L,
        )
        val draftFile = File(app.container.paths.drafts, EditorDraftStore.fileName(id))
        val raw = "{\"schemaVersion\":99,\"text\":\"keep me\"}"

        runBlocking {
            repository.save(draft)
            val loaded = repository.load(id)
            assertTrue(loaded is EditorDraftRead.Usable)
            loaded as EditorDraftRead.Usable
            assertEquals(draft.text, loaded.draft.text)
            assertTrue(draftFile.exists())

            draftFile.writeText(raw)
            val unreadable = repository.load(id)
            assertTrue(unreadable is EditorDraftRead.Unreadable)
            assertEquals(raw, draftFile.readText())

            repository.delete(id)
            assertTrue(repository.load(id) is EditorDraftRead.Missing)
            assertFalse(draftFile.exists())
        }
    }
}
