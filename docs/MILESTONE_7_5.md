# PocketDev — Milestone 7.5: Editor Reliability, Data Integrity & Recovery

> **Status:** Complete (PR #41, merged 2026-11-28)
> **Depends on:** Milestone 7 — Code Editor  
> **Target:** Stabilize the editor introduced in PR #40 before expanding the workstation with Milestones 8 and 9.  
> **Scope:** Correctness, filesystem safety, editing fidelity, lifecycle recovery, performance evidence, and regression coverage. This milestone is not a feature expansion or a rewrite of the editor.

---

## 1. Purpose

Milestone 7 delivered the first integrated code editor: a pure-Kotlin/JVM editor core, Compose UI, multi-file tabs, syntax highlighting, search/replace, formatting, and Files integration.

Before the editor becomes a dependency for Git, package management, development servers, and deeper Agent workflows, its data-integrity guarantees must be explicit and tested.

Milestone 7.5 closes the correctness and reliability gaps around:

- Save ordering and dirty-state accounting.
- Stable document identity and project-root isolation.
- Safe writes and external modifications.
- Path and symbolic-link handling.
- Encoding and newline fidelity.
- IME composition, cursor, and selection behavior.
- Language-aware formatting.
- Recovery of unsaved edits after process death.
- Performance measurement on representative mobile-sized files.
- CI and documentation evidence.

**The milestone is complete only when behavior is demonstrated by regression tests and successful GitHub Actions runs.** A green build alone is not sufficient.

## 2. Non-negotiable engineering rules

1. Do not weaken, delete, skip, or loosen an assertion to make a test pass.
2. Reproduce each reported defect with a failing regression test before changing its implementation whenever automated reproduction is feasible.
3. Keep domain logic in `:core` where it can remain pure JVM and deterministic. Android lifecycle, Compose, and Android storage APIs belong in the appropriate Android-facing layer.
4. Preserve the existing one-APK, native Android architecture. Do not introduce a WebView editor, remote editor, VM, root requirement, or unrelated heavyweight dependency.
5. Do not assume that an Android API behaves identically on every supported filesystem/provider. Verify behavior on the project's minimum supported Android API (29) and current emulator target (API 36), and document provider limitations.
6. Do not silently overwrite external changes. Conflicts must be explicit and recoverable.
7. Do not silently replace invalid text bytes or normalize meaningful document formatting.
8. Every implementation commit must be pushed and its applicable CI checks inspected before proceeding to the next commit, following the repository's testing policy.
9. Do not claim ARM64 runtime validation from x86_64 emulator results.
10. Keep changes scoped to editor reliability. Do not begin Milestone 8 or 9 work as part of this milestone.

## 3. Phase 0 — Baseline audit and reproducible defect tests

### Work

- Re-read the merged Milestone 7 implementation on the current `main`; do not rely on an old local diff or assumptions from an earlier review.
- Trace the complete document lifecycle:
  - Open file → decode → create document/tab.
  - Edit → revision/dirty state → undo/redo.
  - Save → external-change validation → filesystem commit → saved state.
  - Project switch → tab retention → subsequent save.
  - Activity recreation/process death → draft recovery.
- Map the actual implementations and tests for:
  - `EditorViewModel`
  - `EditorBuffer`
  - `TextFileCodec`
  - `TextFileSnapshot` / `FileSignature`
  - `WorkspaceFiles`
  - `PathSafety`
  - `EditorScreen`
  - `EditorFormatter`
  - Existing editor unit and instrumentation tests.
- For every finding, record the exact source location, observed behavior, reproduction steps, expected behavior, and regression test.
- Separate confirmed defects from design risks and unverified hypotheses. Do not label a risk a confirmed bug without a reproducer or a code-level proof.

### Acceptance criteria

- [ ] A short audit note identifies the current implementation and test locations.
- [ ] Every confirmed defect in scope has a deterministic regression test or a documented reason why deterministic automation is not feasible.
- [ ] Existing Milestone 7 behavior is captured as a regression baseline.
- [ ] No implementation is changed merely to conform to an unverified assumption.

## 4. Phase 1 — Save revision correctness

### Problem contract

A save operation must persist a specific immutable snapshot of a document. It must never mark edits made after that snapshot as saved.

### Required behavior

- Give each document buffer a monotonically increasing revision (or an equivalent unambiguous version token).
- A save captures an immutable snapshot containing at least:
  - Document identity.
  - Text content.
  - Buffer revision.
  - Encoding and newline metadata.
  - The expected on-disk version/signature.
- On successful commit, record the exact revision that was persisted.
- If the user edits while the save is in flight, the newer revision remains dirty.
- Multiple save requests for one document must have defined ordering. An older completion must not overwrite newer state or incorrectly clear dirty status.
- Cancellation and failures must not mark the document clean.
- The saved signature must describe the bytes actually committed, not a later read or a newer in-memory buffer.

### Required tests

- [ ] Edit while save is suspended; complete the earlier save; assert the newer buffer remains dirty.
- [ ] Start two saves in quick succession; assert deterministic commit ordering and final contents.
- [ ] Complete an older save after a newer revision exists; assert the newer revision is not marked saved.
- [ ] Save failure and cancellation leave the document dirty.
- [ ] Undo back to the exact saved revision updates dirty state correctly.
- [ ] Save-as / document replacement (if supported by current design) cannot reuse an unrelated revision identity.

### Acceptance criteria

- [ ] Dirty state is derived from the relationship between current and persisted revisions, not from an unconditional `markSaved()`.
- [ ] All race tests are deterministic; use controllable fake storage/barriers rather than timing sleeps.
- [ ] No lost-update or false-clean-state regression remains.

## 5. Phase 2 — Stable document identity and workspace isolation

### Required behavior

A tab must remain bound to the workspace/document from which it was opened. A later change to the globally selected project must not retarget an existing tab's read, save, reload, or conflict-check operation.

Represent document identity explicitly. It should include a stable project/workspace identity and a normalized relative path; do not use a relative path alone as a globally unique document key.

The exact identity type must fit the existing project registry and workspace model. Do not invent a second project identity system.

### Required tests

- [ ] Open `src/main.kt` in Project A, edit it, switch the selected project to B, and save. Only A's file may change.
- [ ] Create same-relative-path files in two projects with identical size and modification time; prove they remain distinct documents.
- [ ] Open two files with the same display name from different directories; prove tab identity and save routing remain correct.
- [ ] Project deletion, relocation, or loss of access produces an explicit unavailable-workspace/conflict state; it must not silently fall back to the currently selected project.
- [ ] External-change detection and reload use the tab's bound workspace.

### Acceptance criteria

- [ ] Every open document has immutable workspace binding for its lifetime, unless an explicit move/rebind operation is implemented.
- [ ] No editor operation resolves a tab against the currently selected project as an implicit fallback.
- [ ] Project-switch regression tests run in Android instrumentation CI.

## 6. Phase 3 — Filesystem commit, concurrency, and external-change protection

### Required behavior

Saving must not leave a partially written or silently truncated document if the process or write operation fails.

Design a storage commit path that:
- Validates the target and expected on-disk version.
- Writes the complete encoded snapshot to a temporary/safe staging file.
- Flushes/synchronizes data where supported and required by the chosen implementation.
- Commits the replacement using the strongest atomic-replacement primitive supported by the actual target filesystem.
- Cleans up temporary artifacts on success and failure.
- Returns a signature for the exact committed bytes.
- Preserves the original file if staging or commit fails.
- Serializes competing writes made through PocketDev's own storage layer.
- Detects external modifications made by the Agent, terminal, or another process and reports a conflict rather than overwriting them.

### Design investigation required before implementation

- Evaluate AndroidX `AtomicFile` and the Java NIO temp-file + move approach against the app's actual storage location and supported API levels.
- Do not assume `AtomicFile` supplies locking; define concurrency control separately.
- Verify whether atomic move/replacement is supported by the target filesystem/provider. If it is not, define and document a safe fallback; do not describe a non-atomic fallback as atomic.
- Identify the exact semantics of `fsync`/sync and directory metadata durability on supported Android storage.
- SAF document providers are not interchangeable with app-private filesystem paths. If editor writes are currently limited to app-private workspace files, state that boundary. Do not claim atomic guarantees for arbitrary SAF providers.
- Avoid a check-then-use path race. Where a fully race-resistant primitive is unavailable on a supported provider, document the residual risk and fail closed where the security boundary cannot be maintained.

### Required tests

- [ ] Simulated failure during staging preserves the original file.
- [ ] Simulated commit failure preserves recoverable data and reports failure.
- [ ] Temporary files are cleaned up or recoverably identified after interruption.
- [ ] Concurrent saves through the PocketDev storage layer have deterministic behavior.
- [ ] External same-size content replacement is detected.
- [ ] External modification between validation and commit is handled according to the documented concurrency strategy.
- [ ] Disk-full / permission / missing-parent failures do not mark the document clean.
- [ ] Android instrumentation verifies the chosen implementation on API 29 and API 36.

### Acceptance criteria

- [ ] The implementation's atomicity and durability guarantees are stated precisely and match tested platform behavior.
- [ ] No failure path silently truncates or replaces the original with partial content.
- [ ] External changes are surfaced as conflicts, never silently overwritten.

## 7. Phase 4 — File signatures and conflict semantics

### Required behavior

The current signature implementation must be audited for false negatives. Metadata such as file size and modification time alone is not a content identity guarantee.

Use a content digest (for example SHA-256) or another verified content-version mechanism where appropriate for the current 1 MiB editor limit. The chosen mechanism must be calculated and compared consistently.

Define:
- What constitutes the baseline version captured on open.
- When the current disk version is revalidated.
- What signature is returned after a successful write.
- How timestamp granularity, same-size replacement, and metadata restoration are handled.
- How large/truncated files behave (they remain non-editable unless a separately designed safe editing mode is introduced).

### Required tests

- [ ] Same-size content replacement with restored modification time is detected.
- [ ] Timestamp granularity does not create a false clean result.
- [ ] A successful save returns a signature matching the bytes committed.
- [ ] A conflict does not mutate the document or disk contents.
- [ ] A clean document can reload an external change only through the documented reload path.
- [ ] A dirty document never silently reloads over local edits.

### Acceptance criteria

- [ ] Conflict detection has deterministic semantics and regression coverage.
- [ ] The signature strategy's CPU and I/O cost is measured against the current maximum editable file size.

## 8. Phase 5 — Path containment and symbolic-link policy

### Required behavior

Audit the order of path validation and canonicalization. If the product policy is to reject symbolic links, checking only a canonicalized target path is insufficient to establish that the original path components were not symbolic links.

- Validate the user/Agent-supplied relative path before filesystem access.
- Inspect the original path and each existing component without following links, where the platform API permits.
- Enforce canonical containment within the bound workspace.
- Protect against a link being swapped between validation and use to the extent supported by the storage API.
- Do not follow a symbolic link merely because its resolved target is still inside the workspace if the product policy says symlinks are unsupported.
- Preserve legitimate regular-file behavior.
- Document provider-specific limitations and fail closed if containment cannot be established.

### Design investigation required

Evaluate `NOFOLLOW_LINKS`, `SecureDirectoryStream` availability/provider support, and Android filesystem behavior on API 29 and API 36. Do not assume an API is supported by every filesystem provider merely because it exists in the Java API.

### Required tests

- [ ] Symlink to a file inside the workspace.
- [ ] Symlink to a file outside the workspace.
- [ ] Symlink to a directory inside/outside the workspace.
- [ ] Symlink in an intermediate path component.
- [ ] Link replacement between validation and use (deterministic test where feasible).
- [ ] Regular files and directories continue to work.
- [ ] Unsupported provider behavior is explicit and fail-closed where necessary.

### Acceptance criteria

- [ ] The implementation matches the documented symlink policy.
- [ ] Tests prove both containment and symlink rejection; comments alone are not evidence.

## 9. Phase 6 — Text encoding and newline fidelity

### UTF-8 decoding

- Decode UTF-8 strictly; malformed or unmappable input must be reported rather than silently replaced.
- Preserve UTF-8 BOM behavior.
- Keep binary/NUL detection explicit and tested.
- Do not open malformed text as a normal editable document unless the user explicitly chooses a documented lossy/recovery mode.
- A future hex/read-only viewer is outside this milestone unless required to avoid a regression.

### Newline behavior

- Detect LF, CRLF, and CR.
- Detect mixed line endings explicitly.
- Choose and document one of two valid policies:
  1. Preserve per-line ending metadata through edits; or
  2. Clearly disclose normalization and require an explicit user choice before converting a mixed-ending file.
- Never silently claim byte-preserving round-trip if only one newline style is retained.
- Preserve BOM and the selected newline policy on save.

### Required tests

- [ ] Valid UTF-8, UTF-8 BOM, LF, CRLF, and CR.
- [ ] Malformed UTF-8 at the beginning, middle, and end of a file.
- [ ] Mixed LF/CRLF/CR files.
- [ ] Round-trip tests compare exact bytes when preservation is promised.
- [ ] Explicit normalization tests verify the user-selected behavior.
- [ ] Binary/NUL rejection remains safe.

### Acceptance criteria

- [ ] Invalid input cannot be silently corrupted by open-and-save.
- [ ] Newline/BOM behavior is documented and covered by byte-level tests.

## 10. Phase 7 — Compose text input, IME, selection, and cursor correctness

### Required behavior

Audit the editor's use of `TextFieldValue`, `TextFieldState`, selection, and IME composition against the exact Compose BOM resolved by Gradle.

- Preserve active IME composition across recomposition and syntax styling updates.
- Do not reconstruct a value with a null composition on every render if that commits/cancels an active composition.
- Keep selection and cursor positions stable when syntax annotations update.
- Ensure programmatic text changes, undo/redo, search/replace, and external reload have explicit selection behavior.
- Verify backspace, newline insertion, paste, and multi-code-unit Unicode input.
- Support Persian/Arabic input and bidirectional text as part of ordinary Unicode correctness; do not infer visual cursor behavior from Latin-only tests.
- Avoid adopting a newer Compose API until its availability and behavior are verified against the project's resolved dependency version.

### Required instrumentation tests

- [ ] Compose/recomposition while an IME composition is active does not prematurely commit or discard it.
- [ ] Persian text entry and editing.
- [ ] Combining marks and supplementary Unicode characters.
- [ ] Cursor/selection survives syntax highlighting updates.
- [ ] Undo/redo and replace-all preserve valid selection bounds.
- [ ] Paste and multiline editing.
- [ ] Rotation/activity recreation restores the expected editor state or draft.

### Acceptance criteria

- [ ] Input behavior is verified with Android instrumentation, not only JVM tests.
- [ ] No selection index can point outside the current text after any supported edit operation.

## 11. Phase 8 — Language-aware formatting

### Required behavior

The current formatter must not apply language-agnostic whitespace transformations that alter document semantics.

In particular, Markdown trailing spaces can represent hard line breaks. A generic `trimEnd()` must not remove meaningful Markdown syntax.

- Separate generic editor actions from language-specific formatters.
- Treat whitespace normalization as a destructive transformation unless proven semantics-preserving for the selected language.
- For Markdown, preserve meaningful trailing spaces and line-break syntax.
- Do not advertise a formatter as a language formatter if it only normalizes tabs/trailing whitespace.
- If no safe formatter exists for a language, disable the action or label the limited behavior accurately.
- Formatting must be undoable as one coherent operation.

### Required tests

- [ ] Markdown hard line breaks remain intact.
- [ ] Code indentation behavior is tested per supported language.
- [ ] Strings, comments, and whitespace-sensitive constructs are not altered unexpectedly.
- [ ] Formatting is a single undo step.
- [ ] Reformatting an already formatted document is idempotent where that is a stated property.
- [ ] Unsupported languages do not receive unsafe generic transformations.

### Acceptance criteria

- [ ] Formatting behavior and supported languages are explicit.
- [ ] No known syntax-significant whitespace is removed silently.

## 12. Phase 9 — Unsaved-draft recovery

### Required behavior

Unsaved editor changes should survive Android process death where feasible, without confusing a recovered draft with the authoritative file on disk.

Persist a recoverable draft containing:
- Stable document/workspace identity.
- Unsaved text or a safe delta representation.
- Buffer revision and persisted revision.
- Baseline disk signature.
- Encoding/BOM/newline metadata.
- Draft schema version and update time.

Recovery rules:
- Draft writes are debounced and do not block the UI thread.
- Draft storage is app-private and excluded from accidental project export unless explicitly requested.
- On recovery, compare the baseline signature with the current file.
- If the file changed externally, show a conflict/recovery choice; never overwrite either version silently.
- Remove a draft only after a confirmed successful save or explicit discard.
- Corrupt/unknown-version drafts must not crash app startup; report and preserve recoverable bytes where possible.
- Bound draft storage and clean up orphaned drafts safely.

### Required tests

- [ ] Process recreation restores an unsaved draft.
- [ ] Simulated process death/relaunch recovers the last committed draft checkpoint.
- [ ] A newer external file produces a conflict, not an overwrite.
- [ ] Successful save clears only the matching draft revision.
- [ ] Explicit discard removes the draft.
- [ ] Corrupt and older-schema drafts fail safely.
- [ ] Multiple tabs/projects recover independently.

### Acceptance criteria

- [ ] Draft recovery has a documented conflict policy.
- [ ] Draft persistence never marks the main document as saved.
- [ ] Recovery is tested through Android lifecycle/instrumentation paths.

## 13. Phase 10 — Performance measurement and bounded optimization

Do not rewrite the editor data model based on intuition. Establish reproducible measurements first.

### Benchmark corpus

Generate deterministic fixtures at:
- 10 KiB
- 100 KiB
- 250 KiB
- 500 KiB
- 1 MiB (current editable limit)

Include representative Kotlin, JSON, Markdown, and plain-text content, plus long lines and Unicode.

### Measure

- Open/decode latency.
- Single-character insertion and deletion latency.
- Line-index update cost.
- Syntax tokenization cost.
- Search and replace latency.
- Recomposition/frame-jank behavior on Android.
- Save latency and peak memory.
- Undo/redo cost and memory growth.

Record device/emulator API, ABI, runner/device class, fixture generation method, warm-up, sample count, and percentile methodology. Do not compare results from unlike environments as if they were equivalent.

### Optimization rules

- Optimize only measured bottlenecks.
- Prefer incremental line indexing and incremental syntax work only where measurements justify them.
- Keep a correctness oracle that compares optimized output with a simple reference implementation.
- Do not reduce the file-size limit to hide performance problems without a product decision and documented rationale.

### Acceptance criteria

- [x] Baseline benchmark results are committed.
- [x] No optimization was applied, so before/after optimization evidence is not applicable.
- [x] No optimization changed editor semantics or weakened correctness tests.

## 14. Phase 11 — Documentation and test-gate reconciliation

Update the following documents to match the final implementation and actual CI configuration:

- `docs/ARCHITECTURE.md`
- `docs/TESTING.md`
- `docs/TEST_COVERAGE.md`
- `docs/TEST_AUDIT.md`
- `docs/IMPLEMENTATION_ROADMAP.md`
- `docs/RISK_REGISTER.md`
- `README.md` (only where milestone/status descriptions are affected)

Requirements:
- Replace stale Pending/Verified labels only when the corresponding tests actually run in the named workflow.
- Record exact workflow run IDs and commit SHA for milestone exit evidence.
- Distinguish required PR checks from manual/opt-in workflows.
- Do not describe x86_64 tests as ARM64 validation.
- Add/update risks for data loss, external modification, draft recovery, and provider-specific atomicity as appropriate.
- Keep limitations explicit.

### CI expectations

At minimum, every PR commit must run:
- `verify.yml`: core editor, codec, buffer/revision, signature, workspace-write, path-safety, search, syntax, formatter, and draft-domain tests.
- `android-emulator.yml`: editor ViewModel, project-switch isolation, save/input/IME, selection, lifecycle recovery, and relevant filesystem integration tests on API 29 and API 36.
- `native-runtime-ci.yml`: continue existing checks; editor-only changes must not be represented as native runtime execution evidence.

If a test cannot run on every PR because it requires a special environment, document the reason and provide an explicit separate gate. A manual-only test is not a required PR gate.

### Acceptance criteria

- [ ] Test inventory accurately maps each test to the workflow that runs it.
- [ ] Required CI executes the critical editor regression tests.
- [ ] All required checks pass on the final milestone commit.
- [ ] Documentation records actual evidence, not intended future coverage.

## 15. Commit and verification protocol

Work in small, reviewable commits. Suggested sequence:

1. `test(editor): reproduce save revision and workspace binding races`
2. `fix(editor): bind tabs to stable workspace identity`
3. `fix(editor): track persisted buffer revisions correctly`
4. `fix(workspace): commit editor saves safely`
5. `fix(workspace): strengthen signatures and symlink checks`
6. `fix(editor): reject malformed text and define newline fidelity`
7. `fix(editor): preserve IME composition and selection`
8. `fix(editor): make formatting language-aware`
9. `feat(editor): recover unsaved drafts safely`
10. `test(editor): add reproducible performance baselines`
11. `docs(editor): reconcile M7.5 evidence and risk register`

The sequence may be adjusted when dependency analysis justifies it, but each commit must have a coherent purpose.

For every commit:
1. Push the commit.
2. Wait for the workflows associated with that exact SHA.
3. Inspect every run and relevant job result.
4. Fix failures without weakening tests.
5. Only then proceed to the next commit.

**Historical execution directive (pre-merge):** Do not merge the PR automatically. Do not start M8/M9 automatically. Report blockers and evidence for review. Merge approval was given explicitly; PR #41 is now merged.

## 16. Milestone exit criteria

Milestone 7.5 may be marked **Complete** only when all of the following are true:

- [x] Save revision races are fixed and regression-tested.
- [x] Tabs are permanently bound to their originating workspace/document.
- [x] Save commits have explicit, tested atomicity/durability guarantees for supported storage.
- [x] External modifications are reliably detected under the documented signature/conflict model.
- [x] Symlink and path-containment behavior matches the documented security policy.
- [x] Malformed UTF-8 cannot be silently corrupted.
- [x] Mixed newline behavior is explicit and tested.
- [x] IME composition, Persian/Unicode input, cursor, and selection are instrumented and verified.
- [x] Formatting does not silently alter syntax-significant whitespace.
- [x] Unsaved drafts recover safely or a documented platform limitation is approved with a safe fallback.
- [x] Performance baselines exist; no optimization was applied.
- [x] Regression tests run in the intended CI workflows.
- [x] All required GitHub Actions checks pass on the merged commit.
- [x] Documentation and risk register reflect the actual implementation and remaining limitations.
- [x] No known Critical/High editor data-integrity defect remains open.

## 17. Out of scope

Unless a blocking dependency is demonstrated and approved, do not include:
- Git UI or Git operations (Milestone 8).
- Package manager UI or repository setup (Milestone 8).
- Development server management or preview (Milestone 9).
- Full LSP implementation, autocomplete engine, debugger, or language-server downloads.
- Large-file editing beyond the existing 1 MiB policy.
- Collaborative editing or cloud synchronization.
- A complete editor rewrite or replacement with a WebView/desktop editor.
- New AI-agent editing capabilities that expand filesystem permissions.

---

## 18. Reference material

Use these primary references during implementation; verify the exact API behavior against the project's resolved dependency versions and supported Android levels.

- AndroidX `AtomicFile`: https://developer.android.com/reference/androidx/core/util/AtomicFile
- Java `SecureDirectoryStream`: https://developer.android.com/reference/java/nio/file/SecureDirectoryStream
- Java `LinkOption.NOFOLLOW_LINKS`: https://developer.android.com/reference/java/nio/file/LinkOption
- Java `CharsetDecoder`: https://developer.android.com/reference/java/nio/charset/CharsetDecoder
- Compose text input and state-based text fields: https://developer.android.com/develop/ui/compose/text/user-input
- Compose `TextFieldValue`: https://developer.android.com/reference/kotlin/androidx/compose/ui/text/input/TextFieldValue
- CommonMark specification (hard line breaks): https://spec.commonmark.org/current/
- GitHub Actions workflow syntax: https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions

External references are design inputs, not proof that a particular implementation is supported by every PocketDev storage provider or Android version. The implementation must include platform-specific verification and tests.

---

**Definition of done:** PocketDev's editor must never tell the user that unsaved data is saved, must never redirect a document operation to another project, must never silently overwrite an external edit, and must never silently corrupt text because of encoding, newline, or formatting behavior.
