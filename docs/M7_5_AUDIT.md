# Milestone 7.5 baseline audit

Date: 2026-11-27
Base commit: `073a09d` (`docs: add milestone 7.5 to implementation roadmap`)
Merged editor baseline: PR #40, merge commit `9809ce3`

## Scope and method

This note records a source-level audit of the merged Milestone 7 editor on current
`main`. It follows the full document lifecycle: open/decode, edit/revision,
save/commit, external-change check, project switch, and process death recovery.
Findings marked **Confirmed** have a deterministic code-level proof and a
planned regression test. Findings marked **Risk** require platform/runtime
verification or a product decision before they can be called defects.

The audit deliberately does not modify implementation code. The next commits
will add failing regression tests before the corresponding fixes.

## Implementation map

| Concern | Current implementation |
| --- | --- |
| Buffer and history | `core/src/main/kotlin/dev/pocketdev/core/editor/EditorBuffer.kt:10-86` |
| Line indexing | `core/src/main/kotlin/dev/pocketdev/core/editor/TextLines.kt` |
| Text decode/encode | `core/src/main/kotlin/dev/pocketdev/core/editor/TextFileCodec.kt:8-62` |
| Text metadata and signature | `core/src/main/kotlin/dev/pocketdev/core/editor/TextFileSnapshot.kt:3-26` |
| Formatting | `core/src/main/kotlin/dev/pocketdev/core/editor/EditorFormatter.kt:12-22` |
| Workspace text IO | `core/src/main/kotlin/dev/pocketdev/core/workspace/WorkspaceFiles.kt:242-290` |
| Workspace path validation | `core/src/main/kotlin/dev/pocketdev/core/workspace/PathSafety.kt:20-46` |
| Android workspace/root selection | `app/src/main/java/dev/pocketdev/AppContainer.kt:47-95` |
| Editor state, tabs, save, conflicts | `app/src/main/java/dev/pocketdev/ui/editor/EditorViewModel.kt:80-487` |
| Compose text field and shortcuts | `app/src/main/java/dev/pocketdev/ui/editor/EditorScreen.kt:325-365` |

## Test and CI map

| Layer | Existing tests |
| --- | --- |
| Core buffer | `core/src/test/kotlin/dev/pocketdev/core/editor/EditorBufferTest.kt:9-58` |
| Core codec | `core/src/test/kotlin/dev/pocketdev/core/editor/TextFileCodecTest.kt:11-52` |
| Core formatter | `core/src/test/kotlin/dev/pocketdev/core/editor/EditorFormatterTest.kt:7-25` |
| Core syntax/search | `core/src/test/kotlin/dev/pocketdev/core/editor/SyntaxHighlighterTest.kt`, `TextSearchTest.kt` |
| Core workspace text IO | `core/src/test/kotlin/dev/pocketdev/core/workspace/WorkspaceFilesEditorTest.kt:20-88` |
| Core path safety | `core/src/test/kotlin/dev/pocketdev/core/workspace/PathSafetyTest.kt:19-75` |
| Editor ViewModel instrumentation | `app/src/androidTest/java/dev/pocketdev/EditorViewModelTest.kt:65-193` |
| Files-to-editor UI instrumentation | `app/src/androidTest/java/dev/pocketdev/EditorUiTest.kt:43-66` |
| `verify.yml` gate | `.github/workflows/verify.yml:49-50` runs `lintDebug testDebugUnitTest assembleDebug` |
| `android-emulator.yml` gate | `.github/workflows/android-emulator.yml:59-70` runs connected Android tests on API 29 and 36 |

Milestone 7 exit evidence: PR #40 verify `45964749210`/`45964755939`,
`android-emulator` `45964749127`/`45964755903`, and `native-runtime`
`45964749085`/`45964756085`, all green.

## Lifecycle trace

1. **Open/decode:** `EditorViewModel.open()` (`EditorViewModel.kt:96-104`) calls
   `readSnapshot()` (`:345-354`), which calls
   `WorkspaceFiles.readTextFile()` (`WorkspaceFiles.kt:242-270`). The snapshot is
   stored by relative path in `applyLoaded()` (`EditorViewModel.kt:377-396`).
2. **Edit:** `EditorScreen` maps a `TextFieldValue` to text plus selection
   (`EditorScreen.kt:325-332`). `onTextChanged()` diffs the text into
   `EditorBuffer.replace()` (`EditorViewModel.kt:147-167`).
3. **Save:** `save()` captures text, encoding, newline style and expected
   signature (`EditorViewModel.kt:173-205`), then calls
   `WorkspaceFiles.writeTextFile()` (`WorkspaceFiles.kt:272-290`). `applySaved()`
   unconditionally calls `buffer.markSaved()` (`EditorViewModel.kt:398-408`).
4. **External changes:** `checkExternalChanges()` reads the same relative path
   through `withWorkspace()` and compares signatures in `applyChecked()`
   (`EditorViewModel.kt:207-210`, `:345-362`, `:418-435`).
5. **Project switch:** `AppContainer.selectProject()` changes a process-wide
   `StateFlow` (`AppContainer.kt:47-48`, `:93-95`). Every editor operation calls
   `withWorkspace()`, which resolves the currently selected project at operation
   time, with a fallback to the shared projects root on failure
   (`EditorViewModel.kt:356-362`).
6. **Recreation/process death:** no draft store or restored editor state exists.
   `EditorViewModel` holds buffer/snapshots/selection only in memory
   (`EditorViewModel.kt:90-94`) and clears its operation job in `onCleared()`
   (`:483-486`).

## Confirmed defects

### D1 — Dirty state is text equality, not persisted revision

**Source:** `EditorBuffer.kt:11-19` and `:58-60`; `EditorViewModel.kt:398-408`.
**Observed:** `dirty` is `value != savedValue`; `markSaved()` stores the current
`value` without recording which revision was saved.
**Expected:** A save persists a specific revision and clears dirty only for that
exact revision. Edits made while that save is in flight must stay dirty.
**Reproducer plan:** Add core tests for revision creation and persisted-revision
comparison, then instrumentation tests with controllable fake storage for edit
during suspended save, two overlapping saves, failure, and cancellation.
**Phase:** 1.

### D2 — Save execution has one global operation generation

**Source:** `EditorViewModel.kt:93-94` and `:319-343`.
**Observed:** Any task cancels the previous task; `operationGeneration` is global,
not keyed by document. Two quick saves have no defined commit ordering, and an
unrelated task can cancel a save without per-document reconciliation.
**Expected:** Per-document save ordering; older completions cannot clear newer
dirty state or overwrite newer content.
**Reproducer plan:** Deterministic fake storage/barrier tests for two saves on
one tab and for save cancellation by a later operation.
**Phase:** 1.

### D3 — Tabs are keyed only by relative path and resolve against the current project

**Source:** `EditorViewModel.kt:90-94`, `:377-396`, `:356-362`; `AppContainer.kt:62-72`.
**Observed:** `buffers`, `snapshots`, `selections`, tab paths, and result paths are
all keyed by the relative path string. Reads, saves, checks, and reloads call
`withWorkspace()`, which uses `container.selectedProject.value` at call time.
**Expected:** A tab keeps immutable workspace identity for its lifetime. Same
relative paths in different projects are distinct documents.
**Reproducer plan:** Instrumentation test: open `src/main.kt` in project A,
edit, switch selection to project B, save; assert only A changes. Add same-path
and duplicate-display-name cases plus loss-of-workspace cases.
**Phase:** 2.

### D4 — Failed workspace validation silently falls back to the projects root

**Source:** `EditorViewModel.kt:356-362`.
**Observed:** `projectContext()` exceptions are swallowed; `rootPath` falls back
to `container.projectsRoot.absolutePath`.
**Expected:** Missing/deleted/relocated project produces explicit
unavailable-workspace/conflict state; no implicit root fallback.
**Reproducer plan:** Instrumentation test deletes or invalidates the bound
project and attempts save/check; assert explicit error and unchanged disk.
**Phase:** 2.

### D5 — Text writes are non-atomic and unprotected against concurrent writers

**Source:** `WorkspaceFiles.kt:272-290`.
**Observed:** `Files.write()` writes the target in place; no staging file, no
atomic replacement, no sync, no inter-process lock, and no PocketDev write
serialization. The signature is computed only after writing from current
metadata.
**Expected:** Complete staged bytes, strongest supported replacement primitive,
failure cleanup, serialized PocketDev writes, and a signature for exact
committed bytes.
**Reproducer plan:** Core tests inject staging/commit failures and concurrent
writes; instrumentation tests verify chosen strategy on API 29 and 36.
**Phase:** 3.

### D6 — File signature is size plus modification time only

**Source:** `TextFileSnapshot.kt:14-17`; `WorkspaceFiles.kt:284-289`; `WorkspaceFilesEditorTest.kt:53-74`.
**Observed:** Same-size external replacement with restored modification time is
indistinguishable from the baseline.
**Expected:** Content-derived version or equivalent deterministic mechanism.
**Reproducer plan:** Core test replaces bytes, restores length and timestamp,
then asserts write conflict and unchanged disk.
**Phase:** 4.

### D7 — Symlink validation happens after canonicalization

**Source:** `PathSafety.kt:36-46`; `WorkspaceFiles.kt:248-250` and `:281-283`.
**Observed:** `resolveWithin()` calls `canonicalFile`, resolving links before
`Files.isSymbolicLink()` checks the target. An intermediate symlink is invisible
to the final-target check.
**Expected:** Validate the supplied path first; inspect existing components with
`NOFOLLOW_LINKS`; enforce canonical containment; reject supported symlinks and
fail closed when containment cannot be established.
**Reproducer plan:** Core tests for symlink file/directory inside and outside
the workspace, intermediate-component symlink, and regular-file regression.
**Phase:** 5.

### D8 — Malformed UTF-8 is silently replaced

**Source:** `TextFileCodec.kt:18`.
**Observed:** `String(payload, UTF_8)` uses replacement decoding; invalid bytes
become U+FFFD. Saving can then overwrite the original bytes with corrupted text.
**Expected:** Strict decoding with malformed/unmappable input reported; no silent
lossy open-and-save.
**Reproducer plan:** Core tests for malformed bytes at beginning, middle, and
end; assert decode failure and original bytes unchanged after attempted save.
**Phase:** 6.

### D9 — Mixed line endings are normalized and reduced to one style

**Source:** `TextFileCodec.kt:20`, `:44-62`; `TextFileSnapshot.kt:8-12`.
**Observed:** Decode always normalizes to LF; `detectNewlineStyle()` keeps only
the first style. Round-trip cannot preserve mixed endings and does not disclose
the conversion to the user.
**Expected:** Explicit mixed-newline policy with byte-level tests and BOM
preservation.
**Reproducer plan:** Core tests for LF/CRLF/CR and mixed files; assert either
exact preservation through per-line metadata or explicit normalization choice.
**Phase:** 6.

### D10 — Formatter performs language-agnostic destructive whitespace edits

**Source:** `EditorFormatter.kt:12-22`.
**Observed:** Every language gets trailing-whitespace stripping and trailing LF
addition; indented languages also expand tabs. Markdown hard line breaks are
semantic trailing spaces and are destroyed.
**Expected:** Language-aware behavior, no silent alteration of syntax-significant
whitespace, accurate action labeling/disablement, and one undo step.
**Reproducer plan:** Core tests for Markdown hard breaks, supported code
indentation, plain text, no-op/unsupported languages, and idempotence where
claimed. Instrumentation test verifies single undo.
**Phase:** 8.

### D11 — Compose text field discards composition on every recomposition

**Source:** `EditorScreen.kt:325-332`.
**Observed:** A new `TextFieldValue` is constructed from only annotated text and
selection; `composition` and other state are dropped whenever syntax styling
or any other state update recomposes the field.
**Expected:** Preserve active IME composition, stable selection, and valid
bounds after all supported edits.
**Reproducer plan:** Instrumentation tests for Persian/Arabic input, combining
marks, supplementary Unicode, selection during highlighting updates,
undo/redo, replace-all, paste/multiline, and rotation/recreation.
**Phase:** 7.

### D12 — Unsaved drafts do not survive process death

**Source:** `EditorViewModel.kt:90-94`, `:483-486`; no draft store exists.
**Observed:** Buffer, snapshot, and selection state are in-memory only. Rotation
may retain the ViewModel, but process death loses all unsaved edits.
**Expected:** App-private, versioned, debounced draft store with stable document
identity, baseline signature, conflict-safe recovery, cleanup, and corruption
tolerance.
**Reproducer plan:** Core tests for draft-domain validation and instrumentation
tests for recreation/relaunch, newer external file conflict, successful-save
clear, explicit discard, corrupt schema, and multiple tabs/projects.
**Phase:** 9.

## Design risks and unverified hypotheses

- **R1 — Atomic replacement and durability on Android:** Java `Files.move`
  with `ATOMIC_MOVE`, `AtomicFile`, `NOFOLLOW_LINKS`, and
  `SecureDirectoryStream` must be evaluated against the app-private workspace
  filesystem on API 29 and 36. Provider limitations will be documented; SAF
  providers are outside the editor's current write boundary.
- **R2 — Concurrent external modification between validation and commit:**
  PocketDev-layer serialization can define ordering for its own writers;
  another process may still modify a file after validation. The final strategy
  and residual risk must be explicit and tested as far as the platform permits.
- **R3 — Performance:** No benchmark corpus exists. No editor data-model rewrite
  will be made before baseline measurements for 10 KiB through 1 MiB.
- **R4 — Minimum API behavior:** Emulator x86_64 results on API 29 and 36 are
  required evidence but are not ARM64 device validation. The accepted R1 device
  gap remains.
- **R5 — Shortcut parity:** `EditorScreen.kt:336-358` handles Ctrl+S/Z/Y/F only;
  alternate redo and platform-specific shortcuts need an explicit decision
  during Phase 7 rather than an assumption.

## Regression baseline to preserve

The following Milestone 7 behaviors currently pass in CI and must remain green:
replace/undo/redo and line helpers; BOM/CRLF round-trip; binary and truncation
handling; stale size+mtime signature rejection; path traversal rejection;
workspace open/edit/save; dirty external-change conflict; clean external reload;
dirty-close guard; search/replace/format; explicit binary and missing-file
errors; Files-to-editor navigation and syntax editor display.

## Commit sequence

1. Audit note (this document).
2. Reproduce save revision and workspace binding races with tests.
3. Bind tabs to stable workspace identity and fix fallback behavior.
4. Track persisted buffer revisions correctly.
5. Commit editor saves safely.
6. Strengthen signatures and symlink checks.
7. Reject malformed text and define newline fidelity.
8. Preserve IME composition and selection.
9. Make formatting language-aware.
10. Recover unsaved drafts safely.
11. Add reproducible performance baselines.
12. Reconcile M7.5 evidence, documentation, and risk register.
