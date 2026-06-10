# SOW-0018 - Liveness-only stale endpoint recovery (remove run-dir permission guard)

## Status

Status: completed

Sub-state: implemented in C, Rust, Go; validated under umask 0002/0022; full CTest green; committed with this SOW.

## Requirements

### Purpose

After a crash, netdata must restart properly with all plugins functional. Stale IPC endpoints (UDS sockets, SHM regions) left behind by dead processes — and any junk file accidentally placed at an endpoint path — must be silently reclaimed. The only thing that must never be deleted is the live endpoint of a running server (accidental double start).

### User Request

User decisions, quoted/condensed from discussion on 2026-06-10:

1. "The key risk here is only one: if I accidentally start netdata twice, my IPC sockets of the running netdata should not break. Period. There is nothing else." — the run-dir permission/ownership guard addresses a threat (hostile writers in a shared directory) that does not exist for netdata's private run dir, and must be removed.
2. "If a user accidentally copies a file to netdata's run dir with a filename that overlaps one of netdata's IPC sockets, the right thing to do is delete that file and restore the socket functionality."
3. "Don't even log it then. It is ok. Silently delete that file and restore netdata's functionality. The key thing is: do not delete the socket of an existing running plugin that runs in parallel."

### Assistant Understanding

Facts:

- The guard (`run_dir_allows_stale_unlink` in Rust, `dir_fd_allows_stale_unlink` / `nipc_uds_run_dir_allows_stale_unlink` in C, `runDirAllowsStaleUnlink` in Go) refuses stale unlink unless the run dir is owned by the process euid and not group/other-writable.
- It was introduced in commit `8a23810` (2026-06-03, SOW-0010) to resolve CodeQL `cpp/toctou-race-condition` alerts under a zero-open-alerts policy — not in response to any product requirement or field issue.
- netdata's shipped systemd unit uses `RuntimeDirectoryMode=0775`, so the guard disables crash recovery on every standard systemd install: after an unclean server death, restart gets `AddrInUse` until manual cleanup or reboot.
- Double-start protection does not depend on the guard at all: SHM liveness is `owner_pid` alive + `owner_generation != 0`; UDS liveness is a successful connect probe. A live endpoint is reported as in-use without being touched.
- The C UDS path additionally refuses to unlink non-socket files (`S_ISSOCK` check), so a junk regular file at a socket path permanently blocks the service — contrary to user decision 2.
- No logging facility exists in any of the three transport libraries; they are pure libraries returning error codes. User decision 3 makes this moot.

Inferences:

- When the liveness check itself cannot run because of transient resource exhaustion (`EMFILE`/`ENFILE` while opening a region), deleting would risk removing a live endpoint — violating decision 3's invariant. Such errors must keep the endpoint and fail the new server with in-use, consistent with "never delete a live plugin's endpoint".

Unknowns:

- Whether the remote GitHub CodeQL run re-reports the two TOCTOU alerts once the gate is removed (descriptor-relative `fstatat`/`unlinkat` mechanics are kept, which is what the SOW-0010 regression record credits for resolving them). Only verifiable after push; handled in Followup.

### Acceptance Criteria

- A stale SHM region / UDS socket from a dead owner is reclaimed and the server starts, regardless of run-dir mode (verified by tests using a `0775` run dir).
- A foreign regular file at a UDS socket path or an invalid/unreadable file at a SHM path is silently deleted and the server starts.
- A live server's endpoint is never unlinked; a second create/listen returns address-in-use (existing tests).
- Behavior is identical across C, Rust, and Go, both transports; full test suites pass under `umask 0002` and `umask 0022`.

## Analysis

Sources checked:

- Rust: `src/crates/netipc/src/transport/shm.rs` (guard at 976, call sites 250/745/770/780/797/807/817, check at 999-1066), `src/crates/netipc/src/transport/posix.rs` (guard at 922, listen call 605, check 932-970).
- C: `src/libnetdata/netipc/src/transport/posix/netipc_shm.c` (guard 104, unlink_same_file 199-217, check_shm_stale 219-276, server_create 338-349, cleanup 899-915), `netipc_uds_lifecycle.c` (guard 84-95, unlink_stale_socket_path 97-130 with `S_ISSOCK` restriction, check_and_recover 132-161, listen 191-193), `netipc_uds_internal.h` (28-32).
- Go: `transport/posix/uds_stale.go` (guard 26-37, checkAndRecoverStale 59-86), `uds_listener.go:27`, `shm_linux.go` (162, 745-761, removeStalePath 778-784, checkShmStale 786-841).
- Docs: `docs/level1-transport.md:525-540`, `docs/level1-posix-shm.md:245-265`, `docs/level1-posix-uds.md:118-135`, `docs/netipc-integrator-skill.md:283-296`.
- History: SOW-0010 (`.agents/sow/done/SOW-0010-20260602-static-analysis-finding-cleanup.md`, regression sections of 2026-06-03), commit `8a23810`; SOW-0017 (umask test fix this SOW partially supersedes).
- netdata deployment reality: `netdata/netdata` `system/systemd/netdata.service.in:14-15` (`RuntimeDirectoryMode=0775`) and `src/libnetdata/os/run_dir.c` (pre-existing dirs accepted as-is).

Current state:

- Stale unlink is gated on run-dir euid ownership and `mode & 0o022 == 0` in all three languages, both transports; with netdata's `0775` run dir the gate always refuses, so crash recovery is dead in production.

Risks:

- Removing the gate restores pre-guard reclaim semantics; the residual TOCTOU window applies only to writers that already share the private run dir (netdata's own processes) — accepted by user decision 1.
- Cross-uid edge: a live region owned by a different uid whose file we cannot open would be unlinked on EACCES. This requires the same service name operated by two different uids concurrently — a deployment misconfiguration; accepted under decision 2 (junk at our endpoint path is deleted).

## Pre-Implementation Gate

Status: ready

Problem / root-cause model:

- See Facts. Scanner-driven hardening added a directory-permission precondition that netdata's real deployment violates, silently disabling crash recovery; the user has rejected the underlying threat model for this product.

Evidence reviewed:

- See Analysis sources; all guard call sites enumerated above.

Affected contracts and surfaces:

- POSIX UDS and SHM stale recovery behavior in C, Rust, Go (signatures of internal helpers change; public APIs unchanged).
- Specs: `docs/level1-transport.md`, `docs/level1-posix-shm.md`, `docs/level1-posix-uds.md`.
- Operator/integrator skill: `docs/netipc-integrator-skill.md` (drop the "keep run dir private" requirement).
- Tests in all three languages; SOW-0017's test-side umask chmod becomes unnecessary and is reverted.

Existing patterns to reuse:

- Keep descriptor-relative `fstatat`/`unlinkat` cleanup mechanics (C) introduced by SOW-0010 — they resolved the CodeQL full-path pattern and are harmless.
- Keep PID + generation liveness (SHM) and connect-probe liveness (UDS) as the sole reclaim criterion.

Risk and blast radius:

- Behavioral change confined to stale recovery paths; live-endpoint protection is untouched and covered by existing tests. Windows transports unaffected (no POSIX permission semantics).

Sensitive data handling plan:

- No secrets, customer, or personal data involved; SOW cites only repo paths, public commits, and test names.

Implementation plan:

1. Rust: remove guards and gating params in `shm.rs`/`posix.rs`; junk (unreadable/invalid/non-live) is unlinked; `EMFILE`/`ENFILE` open failures keep the endpoint.
2. Rust tests: drop gating args, rewrite the conservative-policy test to the new policy, revert SOW-0017 `ensure_run_dir` chmod, add `0775`-run-dir stale recovery tests and a foreign-file-at-socket-path test.
3. C: remove both guards and params; drop the UDS `S_ISSOCK` unlink restriction; same `EMFILE`/`ENFILE` keep-alive exception; directory junk removed via `AT_REMOVEDIR` fallback.
4. Go: remove guard and params in `uds_stale.go`/`uds_listener.go`/`shm_linux.go`; non-live dial errors reclaim; same resource-error exception.
5. Docs/specs/skill updates to describe liveness-only recovery.
6. SOW lifecycle: complete and commit together.

Validation plan:

- `cargo test` under `umask 0002` and `umask 0022`; `go test ./...`; `make` + `ctest --test-dir build --output-on-failure`; same-failure scan for any remaining permission-gating in stale paths.

Artifact impact plan:

- AGENTS.md: unaffected (no workflow change).
- Runtime project skills: none exist.
- Specs: update the three level1 docs (stale recovery sections).
- End-user/operator docs: the level1 docs are the public docs; integrator skill updated.
- End-user/operator skills: `docs/netipc-integrator-skill.md` updated (run-dir privacy requirement removed).
- SOW lifecycle: SOW-0017 outcome remains true (tests umask-independent — now inherently); its chmod scaffolding is reverted here as superseded, recorded below.

Open-source reference evidence:

- None needed; behavior change is driven by explicit user decisions and local evidence.

Open decisions:

- None. Decisions 1-3 recorded under User Request. The `EMFILE`/`ENFILE` keep-alive exception is an implementation detail enforcing decision 3's invariant (cannot prove liveness ⇒ do not delete).

## Implications And Decisions

1. Guard removal (decided: remove entirely; liveness is the only criterion).
2. Junk files at endpoint paths (decided: silently delete, no logging).
3. Double-start invariant (decided: live endpoints are never touched; second server gets address-in-use).
4. CodeQL consequence (decided in discussion: if the remote run re-flags the TOCTOU rule after push, dismiss as won't-fix with the documented threat model instead of changing behavior).

## Plan

1. Rust transports + tests.
2. C transports.
3. Go transports + tests.
4. Docs/specs/skill.
5. Full validation, SOW completion, single commit.

## Execution Log

### 2026-06-10

- Rust: removed `run_dir_allows_stale_unlink` and gating params from `shm.rs` and `posix.rs`; added `stale_check_unavailable` (EMFILE/ENFILE keep-alive); junk at endpoint names (unreadable files, symlinks, empty directories) is now reclaimed; UDS probe-socket failure keeps the endpoint.
- Rust tests: dropped gating args; reverted SOW-0017 `ensure_run_dir` chmod (superseded); rewrote `test_stale_open_failure_policies_are_conservative` as `test_stale_check_unavailable_only_for_fd_exhaustion`; flipped unreadable-file and self-referential-symlink expectations to reclaim; obstruction in the baseline-fallback test made non-empty (non-reclaimable); added `test_stale_recovery_in_group_writable_run_dir` (SHM + UDS) and `test_foreign_file_at_socket_path_is_reclaimed`.
- C: removed `dir_fd_allows_stale_unlink` and `nipc_uds_run_dir_allows_stale_unlink`; added `stale_check_unavailable` + `unlink_stale_name` (with `AT_REMOVEDIR` fallback for directory junk); dropped the UDS `S_ISSOCK` unlink restriction; kept descriptor-relative `fstatat`/`unlinkat` mechanics.
- C fixtures: `test_uds.c` regular-file test flipped to reclaim; `test_shm.c` symlink test flipped to reclaim-link-keep-target; `test_service.c` SHM obstruction made non-empty to preserve the baseline-fallback scenario.
- Go: removed `runDirAllowsStaleUnlink` and gating params from `uds_stale.go`, `uds_listener.go`, `shm_linux.go`; EMFILE/ENFILE keep-alive added in both transports; `ShmCleanupStale` no longer skips non-regular junk entries; `TestShmCleanupStaleMixedEntries` empty-dir expectation flipped to reclaim; added group-writable run-dir recovery tests (SHM + UDS).
- Docs: `level1-transport.md`, `level1-posix-shm.md`, `level1-posix-uds.md`, `netipc-integrator-skill.md` updated to liveness-only recovery.

## Validation

Acceptance criteria evidence:

- Group-writable (0775) run dir crash recovery: new tests pass in Rust (`test_stale_recovery_in_group_writable_run_dir` x2), Go (`TestStaleRecoveryInGroupWritableRunDir`, `TestShmStaleRecoveryInGroupWritableRunDir`).
- Foreign file reclaim: Rust `test_foreign_file_at_socket_path_is_reclaimed`, Go `TestStaleRecovery` (pre-existing, regular file), C `test_uds.c` "reclaims a foreign regular file" + `test_shm.c` symlink-reclaim checks.
- Live endpoint never deleted: pre-existing double-start tests (`AddrInUse`) pass in all three languages; C `test_service` 304/304 checks pass including live-region preservation in mixed cleanup.

Tests or equivalent validation:

- Rust: `cargo test` 336 passed / 0 failed under `umask 0002` AND `umask 0022` (fresh `/tmp` state each run); `cargo fmt --check` clean; `cargo clippy -D clippy::correctness -D clippy::suspicious` clean.
- Go: `go test ./...` all packages pass; `go vet` clean.
- C + interop: `make` clean; `/usr/bin/ctest --test-dir build --output-on-failure` 46/46 passed (includes UDS/SHM/service interop fixtures).

Real-use evidence:

- The CTest interop fixtures exercise real server/client processes over UDS and SHM, including the stale-recovery paths changed here.

Reviewer findings:

- External reviewers not run; user did not request them this session. Scanner follow-up (remote CodeQL) tracked in Followup.

Same-failure scan:

- `grep -rn "allowStaleUnlink|runDirAllowsStaleUnlink|allow_stale_unlink|allows_stale_unlink" src/` → no matches; no other permission-gated unlink paths exist. Windows transports have no POSIX permission semantics (named pipes / windows SHM unaffected).
- Pre-existing, unrelated scanner findings on main (not introduced here, last touched by `f5eca1e`): 6 gosec G115 int→uint32 conversions in `transport/internal/framing/{send,receive}.go` and 1 staticcheck U1000 (`sendRejection` unused) in `uds_handshake.go`. Out of scope for this SOW; noted for a future hygiene pass.

Sensitive data gate:

- No secrets, credentials, customer or personal data in the SOW, diff, docs, or comments; only repo paths, public commit hashes, errno names, and test names.

Artifact maintenance gate:

- AGENTS.md: no update — no workflow/guardrail change.
- Runtime project skills: none exist; nothing to update.
- Specs: updated `docs/level1-transport.md` (stale endpoint recovery), `docs/level1-posix-shm.md` (stale region detection), `docs/level1-posix-uds.md` (socket/SHM stale rules).
- End-user/operator docs: the level1 docs above are the public docs; updated.
- End-user/operator skills: `docs/netipc-integrator-skill.md` updated — run-dir privacy requirement replaced with liveness-only recovery statement.
- SOW lifecycle: `Status: completed`, moved to `.agents/sow/done/`, committed together with the implementation. SOW-0017 remains valid (tests are umask-independent — now inherently); its chmod scaffolding was reverted here as superseded, which is recorded in both SOWs' context. No regression reopening needed: SOW-0017's claimed outcome still holds.

Specs update:

- Done (three level1 docs); see artifact maintenance gate.

Project skills update:

- Not needed — no runtime project skills exist.

End-user/operator docs update:

- Done; see artifact maintenance gate.

End-user/operator skills update:

- Done (`docs/netipc-integrator-skill.md`).

Lessons:

- Scanner-driven hardening that changes runtime behavior must be validated against the real deployment of the primary consumer before adoption. The removed guard was correct in the scanner's generic threat model but wrong for the product: netdata ships `RuntimeDirectoryMode=0775`, so the guard disabled crash recovery on every standard install — silently.
- When a recovery mechanism cannot evaluate its criterion (here: liveness under fd exhaustion), fail toward keeping the endpoint, not deleting it. That preserves the product invariant without permission heuristics.

Follow-up mapping:

- Remote CodeQL verification after push → tracked in Followup (cannot be validated locally; no CodeQL CLI).
- Pre-existing gosec G115 / staticcheck U1000 findings in framing/handshake code → rejected for this SOW (unrelated, pre-existing on main); flag for a separate hygiene SOW if they surface in CI gates.

## Outcome

Stale IPC endpoint recovery is now liveness-only in C, Rust, and Go, for both UDS and SHM: a live server's endpoint is never deleted (double-start protection via PID+generation / connect probe), and anything else found at an endpoint name — dead server leftovers, foreign files, symlinks, unreadable entries, empty directories — is silently reclaimed so netdata restarts properly with all plugins functional after a crash, regardless of run-directory permissions. The only keep-alive exception is fd exhaustion during the liveness check itself. Public specs and the integrator skill describe the new contract.

## Lessons Extracted

See Lessons under Validation.

## Followup

- After the next push, check GitHub Code Scanning; if `cpp/toctou-race-condition` re-flags the stale unlink paths, dismiss as won't-fix citing this SOW's threat model (private run dir, liveness-gated reclaim, double-start protected by liveness).

## Regression Log

None yet.

Append regression entries here only after this SOW was completed or closed and later testing or use found broken behavior. Use a dated `## Regression - YYYY-MM-DD` heading at the end of the file. Never prepend regression content above the original SOW narrative.
