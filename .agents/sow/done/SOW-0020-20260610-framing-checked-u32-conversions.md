# SOW-0020 - Checked u32 conversions in Go framing, Rust parity guards

## Status

Status: completed

Sub-state: implemented, validated (gosec clean, Go/Rust suites, cross-language interop), committed with this SOW.

## Requirements

### Purpose

Clear the last red CI workflow (Static Analysis / gosec G115 in `src/go`) with real bounds enforcement rather than scanner annotations, preserving cross-language wire-contract parity.

### User Request

Assistant proposed `#nosec` annotations (option 1) vs explicit guards (option 2). User: "are you sure that 1 is ok? check again. If not sure, the right is option 2." Re-verification showed option 1's proofs do not all hold; option 2 selected per the user's rule.

### Assistant Understanding

Facts:

- gosec reported 6 G115 `int → uint32` conversions in `transport/internal/framing/send.go:19,120,143` and `receive.go:188,235,296`, failing the Static Analysis workflow (verified locally: `gosec -quiet -exclude=G404 ./...` exit 1 with the same 6 findings).
- Re-verification of the claimed bounds:
  - Receive-side bounds depend on the negotiated `MaxPayload`, not a protocol constant — not unconditionally provable.
  - `receive.go:296` `chk.TotalMessageLen != uint32(totalMsg)` was a real defect: for `totalMsg` above the u32 range the truncated comparison could falsely match a crafted chunk header.
  - Go's `totalMessageLen` checked only the platform int bound; the C implementation (`nipc_uds_header_payload_len`, `netipc_uds.c:17`) already rejects totals above `UINT32_MAX` — Go and Rust lacked that parity bound.
  - Rust `posix.rs` shared the same theoretical truncation at `total_msg as u32` (receive chunk validation and send chunk header).
- gosec does not flag the existing `checkedU32` helper's internal conversion — the guard pattern is scanner-recognized, so routing conversions through it is both honest and sufficient.

Unknowns: none.

### Acceptance Criteria

- gosec exits 0 on `src/go` with CI flags; no `#nosec` annotations added.
- The u32 total-message bound matches C in both Go and Rust.
- All Go/Rust suites and cross-language interop pass.

## Analysis

Sources checked: `framing/send.go`, `framing/receive.go` (full send/receive paths), C `netipc_uds.c:11-21`, `netipc_uds_receive.c:126,244`, Rust `posix.rs:338,448,538`, CI `static-analysis.yml` gosec/clippy invocations.

Risks: new explicit error paths trigger only for totals above the u32 wire range — unrepresentable on the wire and unreachable through validated callers; behavior for all valid traffic is unchanged.

## Pre-Implementation Gate

Status: ready

Problem / root-cause model: six unchecked `int → uint32` conversions whose safety relied on cross-function or configuration-dependent invariants; one site could truncate-match a crafted chunk header. C already enforces the u32 total bound at the source; Go and Rust did not.

Evidence reviewed: see Analysis; gosec failure reproduced locally with CI flags before the change.

Affected contracts and surfaces: internal framing arithmetic in Go; UDS send/receive total-length validation in Go and Rust. No wire-format change — the u32 wire fields are unchanged; unrepresentable totals now fail explicitly instead of relying on upstream validation.

Existing patterns to reuse: the package's own `checkedU32` helper (already scanner-recognized); C's `nipc_uds_header_payload_len` bound as the parity reference.

Risk and blast radius: framing hot path; guards are compare-and-branch only. Validated by full suites plus cross-language interop (chunked pipelines).

Sensitive data handling plan: none involved.

Implementation plan: route all six Go conversions through `checkedU32` (with explicit error returns); tighten Go `totalMessageLen` to the u32 bound (C parity); add the same u32 total bound to Rust `send_inner` and the receive path.

Validation plan: gosec with CI flags; `go test ./...`, vet, staticcheck; `cargo test`, fmt, clippy hard gate; `tests/test_uds_interop.sh` for cross-language chunking.

Artifact impact plan: specs — `docs/level1-transport.md`/`level1-posix-uds.md` describe limits validation generically; the u32 total bound is inherent to the u32 wire field, no spec text contradicts it; no update needed. Other artifact classes unaffected.

Open-source reference evidence: none needed; the parity reference is this repository's own C implementation.

Open decisions: none — user selected option 2.

## Implications And Decisions

1. Guards over annotations (user decision): explicit checked conversions; no `#nosec`.
2. Cross-language parity (project rule): the C u32 total bound is replicated in Go and Rust rather than left Go-only.

## Plan

1. Go framing checked conversions + total bound; Rust guards; validate; commit.

## Execution Log

### 2026-06-10

- `framing/send.go`: `HeaderPayloadLen` returns via `checkedU32`; chunk count and `TotalMessageLen` computed via `checkedU32` with `ErrBadParam` on overflow.
- `framing/receive.go`: `totalMessageLen` now rejects totals above the u32 range (C parity); `packedAreaLen` and `expectedReceiveChunkCount` via `checkedU32` (`ErrProtocol` on overflow); `validateReceiveChunk` compares `TotalMessageLen` via `checkedU32`, eliminating the truncate-match defect.
- Rust `posix.rs`: `send_inner` and the receive path reject `total_msg > u32::MAX` (`BadParam`/`Protocol`).
- Ride-along: removed duplicate inner `#![cfg(unix)]` in `service/raw/client_unix.rs` and `server_unix.rs` (module declarations in `raw.rs` already carry `#[cfg(unix)]`); newer clippy fails `-D clippy::suspicious` on `duplicated_attributes`, so this pre-empts CI breakage on the next runner toolchain update.

## Validation

Acceptance criteria evidence:

- gosec (CI flags) on `src/go`: exit 0, zero findings (was exit 1 with 6 G115).
- Go: `go test ./...` zero failures; `go vet` and `staticcheck` clean.
- Rust: `cargo test` 336 passed / 0 failed; `cargo fmt --check` clean; `cargo clippy --all-targets -- -D clippy::correctness -D clippy::suspicious` zero errors.
- Cross-language: `tests/test_uds_interop.sh` 18 passed / 0 failed / 0 skipped, including Rust↔Go chunked pipelines.

Reviewer findings: external reviewers not run; user did not request them. CI on push is the remote gate.

Same-failure scan: grepped both framing files for remaining bare `uint32(` conversions — all now flow through `checkedU32` or convert provably-bounded wire fields; C verified already-safe at `netipc_uds.c:17`; SHM transport carries no chunking (regions are pre-sized), so no equivalent sites.

Sensitive data gate: none involved.

Artifact maintenance gate:

- AGENTS.md: no update — no workflow change.
- Runtime project skills: none exist.
- Specs: no update — wire format unchanged; the u32 bound is inherent to the existing `total_message_len` u32 field. Docs describe limit validation generically and remain accurate.
- End-user/operator docs: no update — internal hardening.
- End-user/operator skills: no update.
- SOW lifecycle: completed and committed together with the change.

Specs update: not needed (see gate). Project skills update: not needed. End-user/operator docs update: not needed. End-user/operator skills update: not needed.

Lessons:

- A scanner finding can look like noise and still sit next to a real defect: re-verification of "obviously safe" conversions surfaced the truncate-match comparison. Check the bound, do not assert it.
- When one language implementation already enforces a bound (C here), parity is the fix for the others — not annotations.

Follow-up mapping:

- The 6 gosec code-scanning alerts auto-close on the next clean SARIF upload for the same category; verify after push (recorded in session, no separate SOW needed).

## Outcome

All six gosec G115 findings resolved with real checked conversions; the u32 total-message bound now matches C in Go and Rust, closing a theoretical truncate-match acceptance of crafted chunk headers. Static Analysis is expected green; behavior for valid traffic unchanged, confirmed by full suites and cross-language interop.

## Lessons Extracted

See Lessons under Validation.

## Followup

None beyond the auto-closing alerts verification noted above.

## Regression Log

None yet.

Append regression entries here only after this SOW was completed or closed and later testing or use found broken behavior. Use a dated `## Regression - YYYY-MM-DD` heading at the end of the file. Never prepend regression content above the original SOW narrative.
