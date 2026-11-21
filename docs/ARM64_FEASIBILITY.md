# ARM64 emulator feasibility (Milestone 2, prompt §5.4)

Question: **can a GitHub-hosted runner boot an ARM64 (`arm64-v8a`) Android emulator
and run PocketDev in it?** Measured — not assumed — with
`.github/workflows/arm64-spike.yml` (manual dispatch, not a merge gate).

## Runs

| Run | ID | Outcome |
| --- | --- | --- |
| 1 | `44762039967` | Workflow bugs produced false NOs (`yes \| sdkmanager` → SIGPIPE 141 under `pipefail`); fixed in PR #3 |
| 2 | `44768697070` | x86_64 leg: image + KVM OK, AVD created but not discoverable by the emulator; fixed in PR #4 |
| 3 | `44862773422` | Final measurement — verdicts below |

Evidence artifacts for run 3: `spike-ubuntu-24.04-arm`, `spike-ubuntu-latest`
(`sdk-*.log`, `host-probe.txt`, `emulator.log`, `verdict.md`).

## Results — `ubuntu-24.04-arm` (native ARM64 host)

| # | Question | Verdict |
| --- | --- | --- |
| 1 | Compatible ARM64 AVD/system image available? | **NO** — `sdkmanager` cannot install `system-images;…;arm64-v8a` because it resolves a dependency on the `emulator` package, which does not exist for the `linux-aarch64` host (`Warning: Failed to find package 'emulator'`) |
| 2 | Runner provides needed virtualization/KVM? | **NO** — no `/dev/kvm` on the runner |
| 3 | Emulator boots reliably headless? | **NO** — no emulator binary for this host; nothing to boot |
| 4–6 | App / Bionic / linker checks | **N/A** |

Host facts: `host_arch=aarch64`, `kvm_node=ls: cannot access '/dev/kvm'`,
`sdkmanager=/home/runner/.android/sdk/cmdline-tools/16.0/bin/sdkmanager`.

## Results — `ubuntu-latest` (x86_64 host, ARM64 guest)

| # | Question | Verdict |
| --- | --- | --- |
| 1 | Compatible ARM64 AVD/system image available? | **YES** — `system-images;android-30;google_apis;arm64-v8a` installs cleanly |
| 2 | Runner provides needed virtualization/KVM? | **YES for x86_64 guests** — `KVM (version 12) is installed and usable.` (after the udev permissions step). KVM is **not** usable for ARM64 guests on an x86_64 host |
| 3 | Emulator boots reliably headless? | **NO** — hard failure at launch: `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.` (emulator 37.3.10, build 16970787) |
| 4–6 | App / Bionic / linker checks | **N/A** — emulator refused to start |

AVD creation itself succeeded (`-list-avds` → `spike`), and `libpulse0` was needed
for the preinstalled emulator binary to run at all.

## Answer

**No GitHub-hosted runner can boot an ARM64 Android emulator today.**

- x86_64 runners run the Android emulator well (Milestone 1 gate proves API 30
  `x86_64` boots and passes instrumentation tests), but current emulator releases
  require the system image architecture to match the host — cross-architecture
  guests are rejected at startup.
- ARM64 runners cannot run the emulator at all: Google publishes no emulator host
  package for `linux-aarch64` and the runners expose no `/dev/kvm`.

Older emulator builds that still contained the cross-architecture QEMU/TCG path
were considered and **rejected as a CI gate**: an unpinned, obsolete, unpatchable
toolchain with very slow (tens of minutes) software-emulated boots is not a
reliable gate for a project whose CI is its only build/test environment.

## Consequences for the roadmap

1. **Functional runtime validation stays on `x86_64` API 30** (already proven
   green in `android-emulator.yml`). Milestone 3 bootstrap integration tests will
   run there.
2. **ARM64-specific behavior cannot be validated in CI.** Design must stay
   ABI-agnostic; anything ABI-sensitive (Bionic/linker assumptions in §5.4 Q5–Q6,
   Termux bootstrap binaries) is validated with static checks plus documented
   gaps, per risk register R1/R11.
3. If ARM64 device proof becomes mandatory later, the options are a physical
   device via a self-hosted runner (excluded by the master prompt's
   never-build-on-the-phone rule), a credentialed device farm (billing), or
   revisiting this file if Google ships `linux-aarch64` emulator packages or
   re-enables cross-architecture guests.

**Revisit trigger:** a new emulator release that lists `linux-aarch64` host
packages in the SDK repository, or docs restoring cross-arch support.
