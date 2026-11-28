# PocketDev — Editor Performance Baseline

> M7.5 Phase 10 baseline evidence. These numbers establish a reproducible starting point; they are not performance thresholds and do not justify an optimization by themselves. The branch evidence below was preserved when PR #41 merged into `main` at `2424a4c1bc5e30d7837fe5fb8fdab07d7251a148`; post-merge verification remained green.

## Evidence

| Item | Value |
| --- | --- |
| Milestone commit | `ace8d97fc83b26b515f79b8c11ad17cb78d02eac` |
| Branch | `feat/m7-5-editor-reliability` |
| `verify` run | `46076579464` — success |
| `android-emulator` run | `46076579398` — success (API 29 and API 36) |
| `native-runtime-ci` run | `46076579391` — success |
| Core benchmark source | `core/src/test/kotlin/dev/pocketdev/core/editor/EditorPerformanceBaselineTest.kt` |
| Android benchmark source | `app/src/androidTest/java/dev/pocketdev/EditorInputUiTest.kt` |
| Verify report artifact | `unit-test-reports`, artifact ID `15235181477` |
| API 29 report artifact | `android-test-reports-API29`, artifact ID `15235001799` |
| API 36 report artifact | `android-test-reports-API36`, artifact ID `15235706690` |

Raw records are emitted as `M7_5_BENCH_META`, `M7_5_BENCH_CORE`, and `M7_5_BENCH_ANDROID` lines. JVM records are in the `verify` HTML test report; Android records are in the per-API `logcat-*.txt` files inside the instrumentation report artifacts.

## Methodology

### Core JVM baseline

- Runner: GitHub Actions `ubuntu-latest`, JDK 17, `osArch=amd64`; command `./gradlew --no-daemon --stacktrace lintDebug testDebugUnitTest :core:test assembleDebug`.
- Clock: `System.nanoTime()`; 1 warm-up and 5 measured samples per operation; p50/p95 use linear interpolation on sorted samples.
- Fixture generation: deterministic repeated UTF-8 chunks for Kotlin, JSON, Markdown, and plain text; Unicode (`café`, em dash, rocket) and periodic long lines are included. Target sizes are 10 KiB, 100 KiB, 250 KiB, 500 KiB, and 1 MiB; actual byte counts are slightly larger because chunks are appended whole until the target is reached.
- Operations: open/decode, save/encode, single-character insert, single-character delete, full line-index rebuild, syntax tokenization, search `findAll`, search `replaceAll`, and one undo/redo cycle.
- Correctness: every measured block asserts exact decoded text, buffer contents, line starts against a simple reference implementation, token bounds, search matches/replacement, or undo/redo state. A failure of an assertion fails the benchmark test; no timing threshold can fail it.
- Memory: `heapDeltaBytes` is `Runtime.totalMemory() - Runtime.freeMemory()` measured before and after one `saveEncode` call. It is a heap-delta signal, not peak RSS or Android PSS.

### Android instrumentation baseline

- Runner: `android-emulator.yml` on x86_64 API 29 and API 36 (`google_apis` target); command `./gradlew --no-daemon --stacktrace connectedDebugAndroidTest` with PTY classes excluded.
- Fixture: deterministic 100 KiB mixed Unicode/long-line text generated in the test; actual emitted size is 102,424 bytes.
- Method: 1 warm-up plus 8 state updates through `mutableStateOf`; each update adds one character at the midpoint and waits for Compose idle. `recomposeP50Ms`/`recomposeP95Ms` include the update and idle wait, so they are end-to-end instrumentation observations, not isolated recomposition cost.
- Frame behavior: a `Choreographer.FrameCallback` records up to 40 frame intervals; p50/p95 are linear-interpolated. Emulator animation is disabled and SwiftShader graphics are used, so frame values are not comparable to physical devices.
- ABI limitation: the runners are x86_64. These records are not ARM64 validation and are not comparable with the core JVM numbers.

## Core JVM results

All values are milliseconds unless noted. `actualBytes` is the encoded fixture size used by the measured call.

### `kotlin`

| Target (KiB) | Actual bytes | Operation | p50 (ms) | p95 (ms) | Save heap delta (bytes) |
| ---: | ---: | --- | ---: | ---: | ---: |
| 10 | 10258 | `openDecode` | 6.047 | 8.544 |  |
| 10 | 10258 | `saveEncode` | 0.434 | 6.306 | 260016 |
| 10 | 10258 | `singleCharInsert` | 0.658 | 6.710 |  |
| 10 | 10258 | `singleCharDelete` | 0.442 | 4.193 |  |
| 10 | 10258 | `lineIndexRebuild` | 1.015 | 6.894 |  |
| 10 | 10258 | `syntaxTokenize` | 6.089 | 8.978 |  |
| 10 | 10258 | `searchFindAll` | 1.784 | 3.456 |  |
| 10 | 10258 | `searchReplaceAll` | 0.853 | 3.385 |  |
| 10 | 10258 | `undoRedoCycle` | 0.971 | 5.341 |  |
| 100 | 102423 | `openDecode` | 7.351 | 10.581 |  |
| 100 | 102423 | `saveEncode` | 1.001 | 2.844 | 5740736 |
| 100 | 102423 | `singleCharInsert` | 3.709 | 4.497 |  |
| 100 | 102423 | `singleCharDelete` | 1.080 | 2.902 |  |
| 100 | 102423 | `lineIndexRebuild` | 4.577 | 8.290 |  |
| 100 | 102423 | `syntaxTokenize` | 10.877 | 38.888 |  |
| 100 | 102423 | `searchFindAll` | 0.612 | 0.637 |  |
| 100 | 102423 | `searchReplaceAll` | 1.344 | 1.396 |  |
| 100 | 102423 | `undoRedoCycle` | 0.879 | 1.135 |  |
| 250 | 256005 | `openDecode` | 1.657 | 1.716 |  |
| 250 | 256005 | `saveEncode` | 1.367 | 1.998 | 17147376 |
| 250 | 256005 | `singleCharInsert` | 1.802 | 2.405 |  |
| 250 | 256005 | `singleCharDelete` | 1.260 | 1.417 |  |
| 250 | 256005 | `lineIndexRebuild` | 0.876 | 1.121 |  |
| 250 | 256005 | `syntaxTokenize` | 10.787 | 17.288 |  |
| 250 | 256005 | `searchFindAll` | 0.534 | 2.133 |  |
| 250 | 256005 | `searchReplaceAll` | 1.868 | 3.022 |  |
| 250 | 256005 | `undoRedoCycle` | 4.480 | 5.222 |  |
| 500 | 512020 | `openDecode` | 3.682 | 4.368 |  |
| 500 | 512020 | `saveEncode` | 3.992 | 4.173 | 34532720 |
| 500 | 512020 | `singleCharInsert` | 4.782 | 5.562 |  |
| 500 | 512020 | `singleCharDelete` | 3.388 | 3.683 |  |
| 500 | 512020 | `lineIndexRebuild` | 1.712 | 1.752 |  |
| 500 | 512020 | `syntaxTokenize` | 6.449 | 26.123 |  |
| 500 | 512020 | `searchFindAll` | 1.235 | 1.523 |  |
| 500 | 512020 | `searchReplaceAll` | 1.841 | 1.875 |  |
| 500 | 512020 | `undoRedoCycle` | 4.146 | 4.480 |  |
| 1024 | 1048605 | `openDecode` | 6.585 | 7.832 |  |
| 1024 | 1048605 | `saveEncode` | 5.495 | 7.278 | 28302248 |
| 1024 | 1048605 | `singleCharInsert` | 6.901 | 9.906 |  |
| 1024 | 1048605 | `singleCharDelete` | 5.807 | 8.910 |  |
| 1024 | 1048605 | `lineIndexRebuild` | 3.973 | 4.164 |  |
| 1024 | 1048605 | `syntaxTokenize` | 11.639 | 12.782 |  |
| 1024 | 1048605 | `searchFindAll` | 2.324 | 2.392 |  |
| 1024 | 1048605 | `searchReplaceAll` | 8.413 | 33.073 |  |
| 1024 | 1048605 | `undoRedoCycle` | 17.429 | 26.331 |  |

### `json`

| Target (KiB) | Actual bytes | Operation | p50 (ms) | p95 (ms) | Save heap delta (bytes) |
| ---: | ---: | --- | ---: | ---: | ---: |
| 10 | 10250 | `openDecode` | 1.401 | 5.001 |  |
| 10 | 10250 | `saveEncode` | 0.726 | 5.771 | 553280 |
| 10 | 10250 | `singleCharInsert` | 0.099 | 0.118 |  |
| 10 | 10250 | `singleCharDelete` | 0.056 | 0.062 |  |
| 10 | 10250 | `lineIndexRebuild` | 0.797 | 3.912 |  |
| 10 | 10250 | `syntaxTokenize` | 4.265 | 4.708 |  |
| 10 | 10250 | `searchFindAll` | 0.117 | 0.189 |  |
| 10 | 10250 | `searchReplaceAll` | 0.209 | 0.216 |  |
| 10 | 10250 | `undoRedoCycle` | 0.110 | 0.119 |  |
| 100 | 102426 | `openDecode` | 1.585 | 1.624 |  |
| 100 | 102426 | `saveEncode` | 0.531 | 0.577 | 6215472 |
| 100 | 102426 | `singleCharInsert` | 0.862 | 3.259 |  |
| 100 | 102426 | `singleCharDelete` | 0.508 | 2.124 |  |
| 100 | 102426 | `lineIndexRebuild` | 0.954 | 2.066 |  |
| 100 | 102426 | `syntaxTokenize` | 4.799 | 9.219 |  |
| 100 | 102426 | `searchFindAll` | 0.759 | 0.844 |  |
| 100 | 102426 | `searchReplaceAll` | 1.952 | 4.564 |  |
| 100 | 102426 | `undoRedoCycle` | 5.533 | 7.913 |  |
| 250 | 256002 | `openDecode` | 2.810 | 4.645 |  |
| 250 | 256002 | `saveEncode` | 3.690 | 4.449 | 17290560 |
| 250 | 256002 | `singleCharInsert` | 3.785 | 4.641 |  |
| 250 | 256002 | `singleCharDelete` | 1.192 | 3.139 |  |
| 250 | 256002 | `lineIndexRebuild` | 1.868 | 3.780 |  |
| 250 | 256002 | `syntaxTokenize` | 4.114 | 6.002 |  |
| 250 | 256002 | `searchFindAll` | 0.550 | 2.945 |  |
| 250 | 256002 | `searchReplaceAll` | 1.086 | 3.850 |  |
| 250 | 256002 | `undoRedoCycle` | 4.255 | 7.397 |  |
| 500 | 512033 | `openDecode` | 3.207 | 3.758 |  |
| 500 | 512033 | `saveEncode` | 2.799 | 31.270 | -165919488 |
| 500 | 512033 | `singleCharInsert` | 3.246 | 3.934 |  |
| 500 | 512033 | `singleCharDelete` | 2.260 | 2.464 |  |
| 500 | 512033 | `lineIndexRebuild` | 1.701 | 1.843 |  |
| 500 | 512033 | `syntaxTokenize` | 5.355 | 11.501 |  |
| 500 | 512033 | `searchFindAll` | 1.248 | 4.779 |  |
| 500 | 512033 | `searchReplaceAll` | 4.166 | 5.506 |  |
| 500 | 512033 | `undoRedoCycle` | 6.414 | 64.917 |  |
| 1024 | 1048607 | `openDecode` | 12.220 | 14.955 |  |
| 1024 | 1048607 | `saveEncode` | 11.922 | 16.108 | -247152696 |
| 1024 | 1048607 | `singleCharInsert` | 13.707 | 18.175 |  |
| 1024 | 1048607 | `singleCharDelete` | 14.812 | 17.475 |  |
| 1024 | 1048607 | `lineIndexRebuild` | 5.041 | 6.128 |  |
| 1024 | 1048607 | `syntaxTokenize` | 8.522 | 9.682 |  |
| 1024 | 1048607 | `searchFindAll` | 2.511 | 2.585 |  |
| 1024 | 1048607 | `searchReplaceAll` | 9.537 | 15.659 |  |
| 1024 | 1048607 | `undoRedoCycle` | 20.074 | 25.756 |  |

### `markdown`

| Target (KiB) | Actual bytes | Operation | p50 (ms) | p95 (ms) | Save heap delta (bytes) |
| ---: | ---: | --- | ---: | ---: | ---: |
| 10 | 10286 | `openDecode` | 0.731 | 2.462 |  |
| 10 | 10286 | `saveEncode` | 0.491 | 2.963 | 503696 |
| 10 | 10286 | `singleCharInsert` | 0.108 | 0.116 |  |
| 10 | 10286 | `singleCharDelete` | 0.062 | 0.068 |  |
| 10 | 10286 | `lineIndexRebuild` | 0.223 | 0.250 |  |
| 10 | 10286 | `syntaxTokenize` | 6.413 | 9.361 |  |
| 10 | 10286 | `searchFindAll` | 0.164 | 5.767 |  |
| 10 | 10286 | `searchReplaceAll` | 0.186 | 0.196 |  |
| 10 | 10286 | `undoRedoCycle` | 0.119 | 0.148 |  |
| 100 | 102402 | `openDecode` | 3.944 | 5.150 |  |
| 100 | 102402 | `saveEncode` | 0.980 | 3.569 | 6202976 |
| 100 | 102402 | `singleCharInsert` | 3.156 | 5.133 |  |
| 100 | 102402 | `singleCharDelete` | 1.026 | 3.794 |  |
| 100 | 102402 | `lineIndexRebuild` | 0.740 | 3.264 |  |
| 100 | 102402 | `syntaxTokenize` | 18.010 | 21.230 |  |
| 100 | 102402 | `searchFindAll` | 0.286 | 0.328 |  |
| 100 | 102402 | `searchReplaceAll` | 1.063 | 3.843 |  |
| 100 | 102402 | `undoRedoCycle` | 0.983 | 10.400 |  |
| 250 | 256049 | `openDecode` | 3.679 | 5.189 |  |
| 250 | 256049 | `saveEncode` | 2.022 | 2.168 | 17203448 |
| 250 | 256049 | `singleCharInsert` | 1.990 | 3.614 |  |
| 250 | 256049 | `singleCharDelete` | 1.306 | 1.389 |  |
| 250 | 256049 | `lineIndexRebuild` | 0.959 | 1.162 |  |
| 250 | 256049 | `syntaxTokenize` | 5.100 | 9.335 |  |
| 250 | 256049 | `searchFindAll` | 0.592 | 0.700 |  |
| 250 | 256049 | `searchReplaceAll` | 1.725 | 3.697 |  |
| 250 | 256049 | `undoRedoCycle` | 5.201 | 6.286 |  |
| 500 | 512039 | `openDecode` | 3.116 | 3.160 |  |
| 500 | 512039 | `saveEncode` | 2.902 | 2.974 | 34622408 |
| 500 | 512039 | `singleCharInsert` | 3.313 | 3.383 |  |
| 500 | 512039 | `singleCharDelete` | 2.334 | 22.517 |  |
| 500 | 512039 | `lineIndexRebuild` | 1.881 | 1.931 |  |
| 500 | 512039 | `syntaxTokenize` | 9.239 | 9.811 |  |
| 500 | 512039 | `searchFindAll` | 1.279 | 1.730 |  |
| 500 | 512039 | `searchReplaceAll` | 1.597 | 2.068 |  |
| 500 | 512039 | `undoRedoCycle` | 6.403 | 6.890 |  |
| 1024 | 1048630 | `openDecode` | 12.040 | 12.570 |  |
| 1024 | 1048630 | `saveEncode` | 11.524 | 13.140 | -40773544 |
| 1024 | 1048630 | `singleCharInsert` | 12.879 | 16.005 |  |
| 1024 | 1048630 | `singleCharDelete` | 10.541 | 14.773 |  |
| 1024 | 1048630 | `lineIndexRebuild` | 6.030 | 6.729 |  |
| 1024 | 1048630 | `syntaxTokenize` | 34.112 | 106.585 |  |
| 1024 | 1048630 | `searchFindAll` | 2.449 | 2.518 |  |
| 1024 | 1048630 | `searchReplaceAll` | 6.490 | 55.260 |  |
| 1024 | 1048630 | `undoRedoCycle` | 18.002 | 19.991 |  |

### `plain`

| Target (KiB) | Actual bytes | Operation | p50 (ms) | p95 (ms) | Save heap delta (bytes) |
| ---: | ---: | --- | ---: | ---: | ---: |
| 10 | 10441 | `openDecode` | 0.801 | 4.862 |  |
| 10 | 10441 | `saveEncode` | 0.086 | 0.107 | 553552 |
| 10 | 10441 | `singleCharInsert` | 0.106 | 0.111 |  |
| 10 | 10441 | `singleCharDelete` | 0.061 | 0.079 |  |
| 10 | 10441 | `lineIndexRebuild` | 0.183 | 0.254 |  |
| 10 | 10441 | `syntaxTokenize` | 3.996 | 7.743 |  |
| 10 | 10441 | `searchFindAll` | 0.170 | 0.178 |  |
| 10 | 10441 | `searchReplaceAll` | 0.267 | 1.425 |  |
| 10 | 10441 | `undoRedoCycle` | 0.205 | 0.218 |  |
| 100 | 102592 | `openDecode` | 1.086 | 2.699 |  |
| 100 | 102592 | `saveEncode` | 0.499 | 0.500 | 6239136 |
| 100 | 102592 | `singleCharInsert` | 0.660 | 0.813 |  |
| 100 | 102592 | `singleCharDelete` | 0.442 | 0.450 |  |
| 100 | 102592 | `lineIndexRebuild` | 0.341 | 0.346 |  |
| 100 | 102592 | `syntaxTokenize` | 1.956 | 2.059 |  |
| 100 | 102592 | `searchFindAll` | 0.213 | 0.236 |  |
| 100 | 102592 | `searchReplaceAll` | 0.582 | 0.742 |  |
| 100 | 102592 | `undoRedoCycle` | 0.883 | 1.660 |  |
| 250 | 256159 | `openDecode` | 5.004 | 5.841 |  |
| 250 | 256159 | `saveEncode` | 1.335 | 3.018 | 16941440 |
| 250 | 256159 | `singleCharInsert` | 1.571 | 2.712 |  |
| 250 | 256159 | `singleCharDelete` | 1.034 | 1.097 |  |
| 250 | 256159 | `lineIndexRebuild` | 0.925 | 0.963 |  |
| 250 | 256159 | `syntaxTokenize` | 2.884 | 9.283 |  |
| 250 | 256159 | `searchFindAll` | 0.517 | 1.114 |  |
| 250 | 256159 | `searchReplaceAll` | 0.757 | 0.769 |  |
| 250 | 256159 | `undoRedoCycle` | 2.033 | 2.256 |  |
| 500 | 512104 | `openDecode` | 3.373 | 5.454 |  |
| 500 | 512104 | `saveEncode` | 5.917 | 32.890 | -79875392 |
| 500 | 512104 | `singleCharInsert` | 3.406 | 5.102 |  |
| 500 | 512104 | `singleCharDelete` | 2.373 | 4.140 |  |
| 500 | 512104 | `lineIndexRebuild` | 1.818 | 1.981 |  |
| 500 | 512104 | `syntaxTokenize` | 5.802 | 12.342 |  |
| 500 | 512104 | `searchFindAll` | 1.085 | 1.172 |  |
| 500 | 512104 | `searchReplaceAll` | 1.425 | 1.631 |  |
| 500 | 512104 | `undoRedoCycle` | 3.665 | 7.648 |  |
| 1024 | 1048584 | `openDecode` | 6.302 | 7.081 |  |
| 1024 | 1048584 | `saveEncode` | 5.328 | 10.590 | -287976432 |
| 1024 | 1048584 | `singleCharInsert` | 12.308 | 12.982 |  |
| 1024 | 1048584 | `singleCharDelete` | 7.728 | 10.685 |  |
| 1024 | 1048584 | `lineIndexRebuild` | 6.410 | 9.075 |  |
| 1024 | 1048584 | `syntaxTokenize` | 22.616 | 23.235 |  |
| 1024 | 1048584 | `searchFindAll` | 2.065 | 2.074 |  |
| 1024 | 1048584 | `searchReplaceAll` | 3.076 | 3.168 |  |
| 1024 | 1048584 | `undoRedoCycle` | 16.578 | 20.140 |  |

## Android instrumentation results

| API | ABI | Fixture bytes | Update samples | Warm-ups | Frame samples | Recompose p50 (ms) | Recompose p95 (ms) | Frame p50 (ms) | Frame p95 (ms) |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 29 | `x86_64` | 102424 | 8 | 1 | 40 | 1633.500 | 1800.300 | 33.000 | 856.650 |
| 36 | `x86_64` | 102424 | 8 | 1 | 40 | 2349.000 | 5377.700 | 16.000 | 1619.350 |

## Interpretation limits

- No performance threshold is asserted; a slower future run does not by itself fail CI.
- The core and Android runners differ in CPU, OS, allocator, and measurement scope; their numbers must not be compared as if equivalent.
- API 29 and API 36 x86_64 emulator values are not physical-device or ARM64 values.
- `saveEncode` heap delta is a single-run signal affected by GC timing; repeated samples are needed before drawing memory conclusions.
- No optimization was applied in Phase 10. Any future optimization must include before/after evidence and preserve the correctness assertions above.

## Acceptance status

- [x] Baseline benchmark results are committed with CI run IDs and artifact IDs.
- [x] No optimization changes editor semantics or weakens correctness tests.
- [ ] Any optimization includes before/after evidence and regression tests; no optimization has been attempted yet.
