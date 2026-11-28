MANDATORY TESTING & CONTINUOUS VERIFICATION POLICY

1. Core Requirement

Testing is not an optional development phase. It is a mandatory part of implementing every feature, modifying every subsystem, completing every milestone, and producing every commit.

Your responsibility is not merely to implement PocketDev. You must continuously verify that:

- Every implemented feature works as intended.
- Every subsystem behaves correctly.
- Existing functionality does not break when new code is introduced.
- Previous milestones remain functional after future changes.
- Every commit passes the complete applicable test suite.
- No known regression is silently ignored.

Never consider a task, feature, milestone, or commit complete without appropriate automated tests and successful GitHub Actions verification.

This policy applies retroactively to all previously completed work, not just future development.

---

2. RETROACTIVE TESTING — ALL PREVIOUS MILESTONES

Before starting new feature development, perform a comprehensive testing audit of the entire existing repository.

Do not assume that a milestone is correctly implemented simply because it was previously marked as completed.

Review and test every previously completed milestone, including:

- M0 — Audit and CI Foundation
- M1 — Android Foundation
- M2 — Runtime Architecture and Feasibility
- M3 — Runtime Bootstrap
- ADR-0002 — Prefix and Exec Strategy
- Runtime Execution Spike
- All existing Kotlin components
- All existing Android components
- All existing GitHub Actions workflows
- All existing security and filesystem protections

Inspect the actual implementation, not just documentation.

For each existing component:

1. Identify its responsibilities.
2. Identify its expected behavior.
3. Identify its dependencies.
4. Identify possible failure conditions.
5. Review its existing tests.
6. Identify missing test coverage.
7. Write the missing tests.
8. Execute the tests through GitHub Actions.
9. Fix discovered defects.
10. Update the relevant documentation.

Previously completed milestones must not be excluded from regression testing.

If an existing feature has no automated tests, write them.

If existing tests are insufficient, expand them.

If an existing implementation is incorrect, fix it rather than writing tests that simply accept its incorrect behavior.

Do not rewrite Git history or create artificial commits just to make old milestones appear tested.

---

3. TEST EVERY FEATURE AND SUBSYSTEM

Every feature must have tests appropriate to its architecture and risk.

At minimum, consider:

Unit Tests

Test individual classes, functions, utilities, state transitions, validators, and business logic.

Integration Tests

Test interactions between modules and components, including filesystem operations, runtime management, process execution, and application services.

Regression Tests

Whenever a bug is discovered:

1. Reproduce the bug.
2. Write a test that fails because of that bug.
3. Fix the implementation.
4. Verify that the new test passes.
5. Run the complete regression suite.

Never fix a bug without adding a regression test when automated reproduction is reasonably possible.

Negative Tests

Test invalid inputs, missing files, corrupted archives, permission failures, unavailable resources, invalid configurations, interrupted downloads, process failures, and other expected error conditions.

Security Tests

Pay particular attention to:

- Path traversal.
- Zip Slip.
- Symlink validation.
- Unsafe filesystem operations.
- Secret leakage.
- Command execution boundaries.
- Project directory isolation.
- Runtime extraction safety.
- Agent permission boundaries.

End-to-End Tests

Where technically possible, verify complete workflows rather than testing isolated components only.

---

4. MILESTONE-SPECIFIC TEST REQUIREMENTS

Every milestone must have its own test plan and test suite.

Before implementing a milestone:

1. Identify all expected behaviors.
2. Define acceptance criteria.
3. Define the required tests.
4. Identify dependencies and possible regressions. During implementation:

1. Write tests alongside the implementation.
2. Do not postpone all testing until the milestone is finished.
3. Update existing tests whenever behavior changes.
4. Preserve backward compatibility where required.

Before marking a milestone complete:

1. Run all tests related to that milestone.
2. Run the entire existing regression suite.
3. Run relevant integration tests.
4. Run relevant emulator or native checks.
5. Verify that GitHub Actions passes.
6. Update the test documentation.
7. Record any remaining limitations.

A milestone cannot be marked complete merely because its code has been written.

---

5. EVERY COMMIT MUST BE VERIFIED

This is a strict requirement.

Every commit must be tested through GitHub Actions.

Configure CI so that:

- Every push triggers verification.
- Every pull request triggers verification.
- No important test workflow is restricted to manual execution only.
- No relevant source-code changes bypass CI.
- Existing regression tests run alongside newly introduced tests.

Required commit workflow

Follow this sequence:

1. Implement a logically complete change.
2. Write or update its tests.
3. Review the changed code and tests.
4. Commit the changes.
5. Push that commit to GitHub immediately.
6. Wait for the corresponding GitHub Actions workflow to finish.
7. Inspect the actual workflow results.
8. If anything fails, diagnose and fix it.
9. Commit and push the fix.
10. Wait for successful verification again.
11. Only then proceed to the next commit.

Do not accumulate multiple unverified commits.

Push each commit separately and wait for its corresponding CI result before creating the next commit; this ensures that every individual commit receives an independent verification run.

A successful workflow for the latest commit does not prove that every earlier commit passed independently.

Never claim that a commit passed testing without checking its actual GitHub Actions result.

---

6. FULL REGRESSION SUITE ON EVERY COMMIT

Every commit must trigger the complete applicable regression suite, not merely tests for the files changed in that commit.

For example, a change to the Code Editor must not skip Runtime, Bootstrap, Project Management, Filesystem, or Security regression tests.

The objective is to detect cross-component regressions as early as possible.

Organize tests into logical categories if necessary, but ensure that the complete required suite executes for every commit.

You may optimize execution time through parallel GitHub Actions jobs and caching, but you must not sacrifice test coverage.

Do not silently skip tests because they are slow.

Do not mark failed tests as successful.

Do not disable an existing test simply because a new implementation causes it to fail.

If a test genuinely cannot run in the available CI environment, document:

- Why it cannot run.
- Which environment or dependency is missing.
- What alternative verification is performed.
- What remains unverified.

Such a limitation must remain visible in the project documentation and must not be represented as a successful test.

---

7. GITHUB ACTIONS IS THE ONLY BUILD AND TEST ENVIRONMENT

This requirement from the master specification remains unchanged: all PocketDev build and test operations must run through GitHub Actions.

This includes:

- Gradle builds.
- Kotlin compilation.
- Android compilation.
- Native compilation.
- Unit tests.
- Integration tests.
- Emulator tests.
- Static analysis.
- Lint.
- APK packaging.
- Release validation.

Do not run heavy builds or tests locally on the Android phone.

Do not bypass failed CI by attempting to build or test locally.

The phone is used for repository editing, OpenCode operation, Git operations, and lightweight manual runtime checks only.

GitHub Actions is the source of truth for automated verification.

---

8. ARCHITECTURE-SPECIFIC TESTING

Use the appropriate testing strategy for each subsystem.

Android / Kotlin - Unit tests.
- State management tests.
- Repository tests.
- Lifecycle tests.
- UI tests where appropriate.
- Dependency integration tests.

Runtime Bootstrap

- ABI validation.
- Android API compatibility checks.
- Archive checksum verification.
- Corrupted archive handling.
- Interrupted download recovery.
- Resume behavior.
- Extraction safety.
- Zip Slip prevention.
- Symlink validation.
- Atomic replacement.
- Recovery from failed installation.
- Bootstrap health checks.
- Existing installation preservation.

Bionic Runtime

- ELF architecture verification.
- Dynamic dependency inspection.
- Linker invocation.
- Environment variable validation.
- Prefix correctness.
- HOME correctness.
- Shell execution.
- Shebang execution.
- Process exit codes.
- stdout/stderr handling.
- Signal handling where supported.

OpenCode Bionic

- Binary availability and integrity.
- Process startup and termination.
- Configuration loading.
- Session lifecycle.
- API connectivity.
- Streaming events.
- File read/write/edit operations.
- Shell tool execution.
- Error propagation.
- Permission handling.
- Process supervision.

Terminal

- PTY creation.
- Input/output handling.
- Interactive shell behavior.
- Terminal resize.
- Process termination.
- Exit code handling.
- Session recovery.

Filesystem and Projects

- Project creation.
- Project deletion.
- File operations.
- Path validation.
- Directory isolation.
- Invalid filenames.
- Large files.
- External file changes.
- Runtime/project separation.

Git

- Repository initialization.
- Status.
- Diff.
- Commit.
- Branch operations.
- Error handling.
- Authentication failures.

Package Manager

- Package search.
- Installation.
- Removal.
- Dependency handling.
- Repository failures.
- Interrupted operations.

Development Servers

- Server startup.
- Port detection.
- Localhost connectivity.
- Server termination.
- Multiple sessions.
- Preview availability.

These lists are minimum testing considerations. Expand them as the implementation evolves.

---

9. ARM64 AND EMULATOR VERIFICATION

Do not confuse successful x86_64 emulator tests with ARM64 device compatibility.

Maintain separate verification records for:

- x86_64 Android Emulator.
- ARM64 native compilation.
- ARM64 ELF validation.
- Actual ARM64 runtime execution, when a suitable device or runner becomes available.

If ARM64 end-to-end execution cannot be performed in GitHub Actions, explicitly report that limitation.

Do not claim ARM64 runtime compatibility based only on successful cross-compilation or x86_64 emulator tests.

Keep the existing ARM64 feasibility limitation visible until genuine ARM64 execution is verified.

---

10. TEST COVERAGE AND TRACEABILITY

Create and maintain a test coverage matrix.

Recommended documentation:

- "docs/TESTING.md"
- "docs/TEST_COVERAGE.md"
- "docs/TESTING_HISTORY.md"

The coverage matrix should contain:

| Component | Expected Behavior | Test Type | Test Location | CI Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| BootstrapManager | Installation and recovery | Unit/Integration | Actual test path | GitHub Actions | Verified/Pending |
| RuntimePaths | Correct path resolution | Unit | Actual test path | GitHub Actions | Verified/Pending |
| ProjectStore | Project operations | Unit | Actual test path | GitHub Actions | Verified/Pending |
| OpenCode Integration | Agent lifecycle | Integration | Actual test path | GitHub Actions | Verified/Pending |

Use actual repository paths and actual test names. Do not invent test files or claim tests exist when they do not.

Every important requirement should be traceable to at least one automated test or an explicitly documented verification limitation.

Track test coverage for previously completed milestones as well as new milestones.

---

11. FAILURE HANDLING

When a test fails:

1. Stop progression to unrelated implementation work.
2. Inspect the failing GitHub Actions job.
3. Identify the root cause.
4. Determine whether the failure is caused by implementation, test logic, environment, or infrastructure.
5. Fix the actual problem.
6. Add or improve regression tests when appropriate.
7. Push the fix.
8. Wait for CI.
9. Confirm that the complete required suite passes.

Do not:

- Ignore failing tests.
- Delete tests to make CI green.
- Weaken assertions without technical justification.
- Use "continue-on-error" to hide important failures.
- Suppress errors without documentation.
- Claim success based on compilation alone.
- Mark a milestone complete while required verification is failing.

If infrastructure prevents verification, report the exact limitation and keep the affected status pending.

---

12. DOCUMENTATION AND PROGRESS REPORTING

Update testing documentation whenever a component, test, workflow, or verification requirement changes.

For each milestone, report:

- Implemented components.
- Added or modified tests.
- Test categories.
- GitHub Actions run URL.
- Actual test result.
- Failed tests and their resolution.
- Known limitations.
- Remaining unverified behavior.

For each commit, report:

- Commit SHA.
- Commit purpose.
- Tests added or changed.
- GitHub Actions run.
- Pass/fail status.

Never report a test as passed unless its result has been verified.

---

13. EXECUTION ORDER — IMPORTANT

Follow this order:

Phase A — Testing Audit

Audit the entire existing repository and all completed milestones.

Phase B — Historical Test Completion

Add missing tests for M0, M1, M2, M3, ADR-0002, and Runtime Execution Spike.

Do not assume existing tests are sufficient.

Phase C — CI Enforcement

Review and improve GitHub Actions so every individual pushed commit receives a full applicable regression run.

Phase D — Existing Defect Resolution

Fix defects discovered during the historical audit and verify every fix through CI.

Phase E — Future Development

Only after the existing foundation has been audited and verified, continue with M4 and subsequent milestones.

For every future milestone, follow this same testing-first, commit-by-commit verification process.

---

14. FINAL NON-NEGOTIABLE RULE

A feature is not complete because its code exists. A milestone is not complete because its implementation is finished. A commit is not verified because it was pushed.

Completion requires:

1. Correct implementation.
2. Appropriate automated tests.
3. Successful execution of the complete applicable regression suite.
4. Successful GitHub Actions verification.
5. Updated documentation.
6. Explicit disclosure of anything that remains unverified.

Apply this policy to the entire PocketDev repository, including all previously completed milestones and all future development.

Do not limit this policy to newly written code.
