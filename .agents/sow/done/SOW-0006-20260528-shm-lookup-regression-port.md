# SOW-0006 - SHM Lookup Regression Port

## Status

Status: completed

Sub-state: implementation, validation, Netdata vendoring, and follow-up mapping completed.

## Requirements

### Purpose

Port the downstream Netdata fixes for NetIPC SHM lookup regressions back into this repository, add regression tests that fail on the current upstream behavior, and validate the shared NetIPC stack across Linux and Windows before vendoring again.

### User Request

The user reported that the downstream `topology-containers` worker found several NetIPC issues while validating the live Agent:

- SHM client requests could be corrupted when typed lookup payloads reused the same buffer as the outer SHM message.
- Downstream worked around too-small request negotiation by exposing request payload sizing, but the user clarified that this is not the desired upstream API contract: Level 2+ callers use typed APIs and should not need to understand request buffer layouts.
- POSIX SHM server sessions could remain alive after the UDS control peer disconnected, leaving stale-looking sessions.

The user asked to check these findings, create a SOW, and come up with a plan to port the fixes back, add tests, and run the full test suite.

### Assistant Understanding

Facts:

- The downstream Netdata SOW records the SHM lookup failure as `NIPC_ERR_TRUNCATED` followed by `NIPC_ERR_BAD_LAYOUT` on normal lookup batches, not topology JSON generation.
- The downstream Netdata diff modifies the C NetIPC service layer in:
  - `src/libnetdata/netipc/include/netipc/netipc_service.h`
  - `src/libnetdata/netipc/src/service/netipc_service.c`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c`
- The same vulnerable C paths exist in this repository.
- Existing upstream Linux service tests pass today, which proves the current suite does not cover the reported SHM lookup path.
- The current public Level 2 docs and C/Rust/Go typed configs intentionally do not expose `max_request_payload_bytes`.
- The current C typed lookup implementation already computes the exact encoded request size before encoding and sending.

Inferences:

- The SHM buffer-aliasing bug is C-specific in the downstream report because C typed lookup calls encode variable-size requests into `ctx->send_buf` and then pass `ctx->send_buf` to the SHM send path.
- The downstream public request-capacity field should be treated as a downstream workaround, not as the upstream API direction.
- The upstream fix should preserve the typed API boundary: request byte capacity remains internal to Level 2+, and typed service calls own layout-aware sizing, internal capacity growth, and batching behavior.

Unknowns:

- Whether this SOW should implement transparent splitting/stitching when a single typed lookup request exceeds the hard request payload cap, or whether this SOW should limit itself to the downstream regression and track over-capacity splitting as a separate SOW.
- Whether native Windows validation can be completed immediately from the available `win11` environment during implementation; this should be checked before close-out.

### Acceptance Criteria

- The current SHM lookup corruption is reproduced by a regression test before the fix or by an equivalent fail-first test patch review.
- POSIX C `CGROUPS_LOOKUP` and `APPS_LOOKUP` typed calls pass over SHM/futex, not only baseline UDS.
- C SHM send handles overlapping payload/message buffers correctly.
- Public typed configs continue to hide request byte capacity from callers.
- Typed lookup calls compute request byte needs internally and grow/reconnect or split without caller-provided byte sizes.
- POSIX SHM server session workers close when the control socket peer disconnects while SHM receive is timing out.
- Terminal capacity/envelope/internal-error responses close the session consistently on POSIX and Windows service paths.
- Linux full validation passes.
- Windows validation is run or a concrete blocker is recorded with evidence.
- Relevant specs, docs, and output/reference skill guidance are updated.
- The SOW validation gate records tests, same-failure scans, reviewer findings if requested, artifact impact, lessons, and follow-up mapping before completion.

## Analysis

Sources checked:

- Downstream Netdata worker report supplied by the user.
- Downstream Netdata SOW evidence in `SOW-0036-20260526-network-viewer-topology-groupings.md:508`.
- Downstream Netdata diff for `src/libnetdata/netipc/include/netipc/netipc_service.h`.
- Downstream Netdata diff for `src/libnetdata/netipc/src/service/netipc_service.c`.
- Downstream Netdata diff for `src/libnetdata/netipc/src/service/netipc_service_win.c`.
- `src/libnetdata/netipc/include/netipc/netipc_service.h`
- `src/libnetdata/netipc/src/service/netipc_service.c`
- `src/libnetdata/netipc/src/service/netipc_service_win.c`
- `tests/fixtures/c/test_service.c`
- `tests/fixtures/c/test_service_payload_limits.c`
- `tests/fixtures/c/test_service_limit_helpers.h`
- `docs/level1-posix-shm.md`
- `docs/level2-typed-api.md`
- `docs/netipc-integrator-skill.md`
- `tests/run-windows-msys-validation.sh`
- `tests/run-coverage-c-windows.sh`
- `.agents/sow/done/SOW-0004-20260525-cgroups-apps-lookup-methods.md`
- `.agents/sow/done/SOW-0005-20260526-lookup-batch-one-benchmarks.md`

Current state:

- Public C typed service config does not expose request payload capacity, which matches the desired Level 2+ contract:
  - `src/libnetdata/netipc/include/netipc/netipc_service.h:83`
  - `src/libnetdata/netipc/include/netipc/netipc_service.h:99`
- Rust and Go typed lookup configs also do not expose request byte capacity:
  - `src/crates/netipc/src/service/cgroups_lookup.rs:23`
  - `src/go/pkg/netipc/service/cgroups_lookup/types.go:24`
- C service config conversion forwards response capacity but not request capacity, which is fine for public typed config but requires the typed layer to choose correct internal request proposals:
  - `src/libnetdata/netipc/src/service/netipc_service.c:244`
  - `src/libnetdata/netipc/src/service/netipc_service.c:262`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:254`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:272`
- The default typed C request payload is `16` bytes:
  - `src/libnetdata/netipc/src/service/netipc_service.c:226`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:236`
- C typed lookup calls compute exact request sizes before encoding:
  - `src/libnetdata/netipc/src/service/netipc_service.c:667`
  - `src/libnetdata/netipc/src/service/netipc_service.c:688`
- The C SHM send path encodes the outer header into `ctx->send_buf` before copying the payload, corrupting the request if `payload == ctx->send_buf`:
  - `src/libnetdata/netipc/src/service/netipc_service.c:432`
  - `src/libnetdata/netipc/src/service/netipc_service.c:437`
  - `src/libnetdata/netipc/src/service/netipc_service.c:438`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:446`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:451`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:452`
- C typed lookup calls create that alias:
  - `src/libnetdata/netipc/src/service/netipc_service.c:737`
  - `src/libnetdata/netipc/src/service/netipc_service.c:741`
  - `src/libnetdata/netipc/src/service/netipc_service.c:748`
  - `src/libnetdata/netipc/src/service/netipc_service.c:785`
  - `src/libnetdata/netipc/src/service/netipc_service.c:789`
  - `src/libnetdata/netipc/src/service/netipc_service.c:796`
- POSIX SHM server receive loops ignore the control socket on SHM receive timeout:
  - `src/libnetdata/netipc/src/service/netipc_service.c:1091`
  - `src/libnetdata/netipc/src/service/netipc_service.c:1095`
- Server sessions close after `NIPC_ERR_OVERFLOW` only, not after all terminal error responses:
  - `src/libnetdata/netipc/src/service/netipc_service.c:1193`
  - `src/libnetdata/netipc/src/service/netipc_service.c:1268`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:1098`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c:1171`
- Existing C lookup tests use baseline service config, not SHM:
  - `tests/fixtures/c/test_service.c:309`
  - `tests/fixtures/c/test_service.c:341`
  - `tests/fixtures/c/test_service.c:719`
  - `tests/fixtures/c/test_service.c:771`
- Existing C SHM service test covers snapshot, not lookup:
  - `tests/fixtures/c/test_service.c:1785`
- Existing C tests assert the too-small default:
  - `tests/fixtures/c/test_service.c:2799`
  - `tests/fixtures/c/test_service.c:2828`
- Current docs explicitly say typed callers do not provide `max_request_payload_bytes`, and the user confirmed this is the right contract:
  - `docs/level2-typed-api.md:249`
  - `docs/level2-typed-api.md:299`
  - `docs/level2-typed-api.md:475`
- POSIX SHM docs require complete outer message bytes to be written before publishing:
  - `docs/level1-posix-shm.md:109`
- Existing targeted validation passed despite the bug:
  - `/usr/bin/cmake --build build --target test_service test_service_payload_limits -j12`
  - `/usr/bin/ctest --test-dir build -R '^(test_service|test_service_payload_limits)$' --output-on-failure`
  - Result: 2/2 tests passed.
- The local `ctest` command resolves first to a broken user-local wrapper; `/usr/bin/ctest` works:
  - `which -a ctest` shows `/home/user/.local/bin/ctest` before `/usr/bin/ctest`.

Risks:

- Porting downstream `max_request_payload_bytes` into public typed configs would violate the Level 2+ boundary and make callers reason about method-internal byte layouts.
- Keeping request sizing internal requires stronger typed-layer tests, because callers cannot work around bad internal estimates.
- Setting a larger internal default increases per-session SHM reservation, but `NIPC_MAX_PAYLOAD_DEFAULT` is only 1024 bytes and the hard cap remains 1 MiB.
- Transparent splitting/stitching for requests above the hard cap may require additional response-buffer assembly logic and careful view lifetime tests.
- The reconnect drain delay in the downstream diff may hide a race rather than fully model peer closure; tests must prove it is needed and bounded.
- Windows changes cannot be trusted from Linux-only tests because the downstream diff touches `netipc_service_win.c`.
- Live Agent validation evidence may include process/container/customer details. Durable artifacts must keep only sanitized summaries.

## Decisions

### 2026-05-28 - Level 2+ request byte capacity remains internal

Decision:

- Level 2+ consumers must not provide `max_request_payload_bytes`.
- The typed API knows the request layout for its service kind and is responsible for request sizing, capacity growth, and batching behavior.
- Do not port the downstream public request-capacity field as an upstream public typed API.

Evidence and context:

- The public C typed config lacks `max_request_payload_bytes` at `src/libnetdata/netipc/include/netipc/netipc_service.h:83`.
- The public Rust/Go typed lookup configs also lack request byte capacity fields.
- The docs explicitly require this boundary at `docs/level2-typed-api.md:299` and test for it at `docs/level2-typed-api.md:475`.
- The C lookup implementation already has method-specific size helpers at `src/libnetdata/netipc/src/service/netipc_service.c:667` and `src/libnetdata/netipc/src/service/netipc_service.c:688`.

Implications:

- Request capacity fixes must be internal to the typed service layer.
- Tests must prove callers do not need to know request byte sizes.
- Downstream Netdata clients should not need `.max_request_payload_bytes = NIPC_MAX_PAYLOAD_CAP` once the vendored upstream fix is correct.

## Pre-Implementation Gate

Status: ready; implementation proceeded after the user accepted the focused regression scope.

Problem / root-cause model:

- SHM lookup request corruption: C lookup calls encode variable-size requests into `ctx->send_buf`, then pass that same buffer to `transport_send()`. The SHM path writes the outer envelope header into `ctx->send_buf` before copying the payload. If the payload pointer is the same buffer, the first `NIPC_HEADER_LEN` bytes of the typed lookup payload are overwritten before the copy. The server then decodes corrupted lookup bytes and can return `BAD_LAYOUT` or related failures.
- Request capacity under-sizing: typed C public config correctly hides request byte capacity, but the service layer maps zero to an internal default of `16` bytes for clients. Lookup requests with dynamic path batches or many PIDs can exceed this and force avoidable overflow/reconnect behavior. The fix must be internal, not a public caller knob.
- Stale SHM sessions: POSIX SHM workers wait on SHM receive timeouts and do not poll the UDS control fd. A disconnected peer can therefore leave a worker/session alive longer than expected, which downstream observed as accumulating stale lookup sessions.
- Terminal error session handling: after sending terminal capacity, bad-envelope, or internal-error responses, keeping the same session alive can make retry/reconnect behavior ambiguous. The downstream fix closes after those responses.

Evidence reviewed:

- Downstream Netdata SOW evidence records the live failure, root cause, and repair file locations in `SOW-0036-20260526-network-viewer-topology-groupings.md:508`.
- Downstream diff moves payload bytes with overlap-safe `memmove()` before SHM header encoding in C and Windows service files.
- This repository has the same vulnerable C SHM send order at `src/libnetdata/netipc/src/service/netipc_service.c:432` and `src/libnetdata/netipc/src/service/netipc_service_win.c:446`.
- This repository has the same typed lookup payload/send buffer alias at `src/libnetdata/netipc/src/service/netipc_service.c:737` and `src/libnetdata/netipc/src/service/netipc_service.c:785`.
- Current tests pass without exercising lookup over SHM, proving the regression gap.

Affected contracts and surfaces:

- C public service config structs as a compatibility boundary that must continue hiding request byte capacity:
  - `nipc_client_config_t`
  - `nipc_server_config_t`
- C POSIX service implementation.
- C Windows service implementation.
- Rust typed service configs as parity surfaces that must continue hiding request byte capacity unless a future SOW explicitly changes the Level 2+ contract.
- Go typed service configs as parity surfaces that must continue hiding request byte capacity unless a future SOW explicitly changes the Level 2+ contract.
- POSIX SHM session lifecycle behavior.
- Windows SHM/named-pipe service session error handling behavior.
- Docs:
  - `docs/level2-typed-api.md`
  - `docs/netipc-integrator-skill.md`
  - possibly `docs/level1-transport.md`
- Tests:
  - `tests/fixtures/c/test_service.c`
  - `tests/fixtures/c/test_service_payload_limits.c`
  - `tests/fixtures/c/test_service_limit_helpers.h`
  - Windows C service tests if API/default expectations change.
  - Rust and Go typed service tests if API parity is changed.

Existing patterns to reuse:

- Existing C lookup request-size helpers in `src/libnetdata/netipc/src/service/netipc_service.c`.
- Existing C lookup handlers in `tests/fixtures/c/test_service.c`.
- Existing POSIX SHM typed snapshot test in `tests/fixtures/c/test_service.c:1785`.
- Existing request/response overflow recovery tests in `tests/fixtures/c/test_service_payload_limits.c`.
- Existing Windows C service guard and payload-limit tests.
- Existing Rust/Go typed config conversion tests in service facade modules.
- Existing cross-language service SHM interop scripts.

Risk and blast radius:

- Runtime blast radius is Level 2 typed service calls over SHM and baseline transports.
- A too-large request-capacity default increases per-session SHM region size.
- A too-small default causes reconnect churn and can mask sizing bugs as performance regressions.
- Session-close behavior affects retry semantics; handlers must remain duplicate-safe per the existing at-least-once contract.
- Windows service code must compile and pass native Windows tests because the Windows service path mirrors most C changes.

Sensitive data handling plan:

- Tests will use synthetic paths, PIDs, labels, and service names.
- SOWs, specs, docs, skills, and code comments must not record raw live Agent PIDs, container names from real systems, user names, customer identifiers, private endpoints, secrets, bearer tokens, SNMP communities, or non-private customer-identifying IPs.
- Live validation, if any, must be summarized with redacted counts and generic service names only.

Implementation plan:

1. Add fail-first C POSIX SHM lookup regression coverage.
   - Add SHM-capable cgroups/apps lookup server variants or a configurable lookup server setup to `tests/fixtures/c/test_service.c`.
   - Add `CGROUPS_LOOKUP` and `APPS_LOOKUP` calls over SHM/futex and assert `client.shm != NULL`.
   - These tests should fail before the SHM send fix because the request payload aliases `ctx->send_buf`.
2. Repair C SHM send overlap.
   - In POSIX and Windows service send paths, move/copy overlapping payload bytes into the message payload slot before encoding the outer header.
   - Use `memmove()` for overlap safety.
3. Preserve the public typed API boundary.
   - Do not add `max_request_payload_bytes` to C/Rust/Go typed service configs.
   - Add or update tests/docs so the boundary stays explicit.
4. Repair internal typed request sizing.
   - Raise the internal typed client request proposal from `16` bytes to `NIPC_MAX_PAYLOAD_DEFAULT` or a method-specific equivalent.
   - Keep exact request-size preflight before sending lookup requests.
   - When exact size exceeds the current negotiated request capacity but fits within the hard cap, update the internal transport proposal, reconnect, and retry without caller involvement.
   - Update C and Windows default tests that currently assert `16`.
5. Decide over-capacity lookup batching scope before implementation.
   - Option A: implement transparent split/stitch for lookup calls whose encoded request would exceed `NIPC_MAX_PAYLOAD_CAP`, preserving caller-visible order and borrowed-view lifetime.
   - Option B: keep this SOW focused on the downstream regression, return `NIPC_ERR_OVERFLOW` for over-capacity semantic requests, and create a separate pending SOW for transparent split/stitch.
   - Recommendation: Option B for this SOW, because the reported regression is below the hard cap and split/stitch changes response assembly semantics beyond the downstream port. This is a product-quality gap and should be tracked, not forgotten.
6. Repair POSIX SHM stale-session detection.
   - Poll/peek the UDS control fd on SHM receive timeout.
   - Break the session loop when the control peer has disconnected.
   - Add a regression test that closes a SHM client and verifies the server session drains instead of accumulating active sessions/SHM files.
7. Repair terminal error session handling.
   - Close after sending capacity, bad-envelope, or internal-error responses on POSIX and Windows service paths.
   - Add or update tests for malformed SHM requests and overflow responses to prove reconnect/replacement-client behavior.
8. Update docs and output/reference skill guidance.
   - Update `docs/level2-typed-api.md` to reinforce that request byte capacity is internal to typed APIs.
   - Update `docs/netipc-integrator-skill.md` if public API guidance changes.
   - Update docs/spec wording for terminal session close behavior if needed.
9. Run formatting and full validation.
   - C, Rust, Go, POSIX interop, Windows validation, and same-failure scans.

Validation plan:

- Build:
  - `/usr/bin/cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug`
  - `/usr/bin/cmake --build build -j$(nproc)`
- Linux full C/Cross-language:
  - `/usr/bin/ctest --test-dir build --output-on-failure`
  - `bash tests/run-coverage-c.sh`
  - `bash tests/interop_codec.sh`
  - `bash tests/test_service_interop.sh`
  - `bash tests/test_service_shm_interop.sh`
  - `bash tests/test_uds_interop.sh`
  - `bash tests/test_shm_interop.sh`
- Linux Rust:
  - `bash tests/run-coverage-rust.sh`
  - targeted `cargo test --manifest-path src/crates/netipc/Cargo.toml` if service config API changes.
- Linux Go:
  - `bash tests/run-coverage-go.sh`
  - targeted `go test` for typed service packages if service config API changes.
- Windows:
  - Run native Windows scripts from the Windows environment:
    - `bash tests/run-coverage-c-windows.sh`
    - `bash tests/run-coverage-rust-windows.sh`
    - `bash tests/run-coverage-go-windows.sh`
    - `bash tests/run-windows-msys-validation.sh`
    - `bash tests/run-verifier-windows.sh` if the service lifecycle changes are accepted for final sign-off.
- Static checks:
  - `git diff --check`
  - same-failure scans for `memcpy(msg + NIPC_HEADER_LEN, payload`, `max_request_payload_bytes == 16u`, and typed service config conversions that omit request capacity.
- Vendoring check after upstream commit:
  - Use the existing vendor/diff scripts against the Netdata target worktree before any final vendored commit.

Artifact impact plan:

- AGENTS.md: likely unaffected; this is project behavior, not workflow policy.
- Runtime project skills: none exist; no update expected unless implementation uncovers reusable workflow knowledge.
- Specs: update `docs/level2-typed-api.md`; possibly update `docs/level1-transport.md` or SHM docs for session-close semantics.
- End-user/operator docs: update `docs/netipc-integrator-skill.md` if public typed config guidance changes.
- End-user/operator skills: `docs/netipc-integrator-skill.md` is an output/reference skill and must be checked.
- SOW lifecycle: this SOW was moved to `current/in-progress` before implementation; completion moves it to `.agents/sow/done/` in the same commit as the implementation.

Open-source reference evidence:

- None checked. The reported bug is in this repository's own NetIPC service orchestration and downstream Netdata integration path; external projects are not authoritative for this internal wire/service API.

Open decisions:

- None.

## Implications And Decisions

1. Over-capacity lookup batching scope.

   Option A - Include transparent split/stitch now.

   - Pros:
     - Fully satisfies the principle that typed APIs own batching and byte layout.
     - Lets callers pass large semantic lookup arrays without knowing where byte caps are.
     - Preserves caller-visible order if implemented correctly.
   - Cons:
     - Expands this SOW beyond the downstream regression.
     - Requires response stitching into an internal buffer while preserving borrowed-view lifetime.
     - Needs broader C/Rust/Go parity analysis if the same semantic guarantee should apply across languages.
   - Implications:
     - Add tests for order preservation, mixed known/unknown results, and response view lifetime after multiple internal subcalls.
     - Add same-behavior checks for Rust and Go typed lookup clients or explicitly scope them into follow-up work.
   - Risks:
     - More implementation surface in the same SOW can delay the regression port and vendoring.
     - Stitching bugs can create subtler view-lifetime regressions than the original issue.

   Option B - Track transparent split/stitch separately.

   - Pros:
     - Keeps this SOW tightly focused on the known downstream regression.
     - Fixes normal lookup batches below the hard cap with lower risk.
     - Allows transparent split/stitch to get its own fail-first tests and cross-language parity plan.
   - Cons:
     - Very large semantic lookup arrays can still return `NIPC_ERR_OVERFLOW` until the follow-up lands.
     - The follow-up must be real SOW work, not an informal TODO.
   - Implications:
     - This SOW must create a pending SOW for transparent split/stitch if Option B is selected.
     - This SOW still must prove callers do not provide request byte capacity for normal regression coverage.
   - Risks:
     - A workload above the hard cap remains a known limitation for one more SOW.

   Recommendation: Option B.

   Reasoning: the worker regression is a below-capacity SHM/capacity-negotiation bug. Transparent split/stitch is the right Level 2+ direction for oversized semantic batches, but it changes response assembly and deserves its own targeted tests instead of being mixed into the regression port.

### 2026-05-28 - Over-capacity lookup batching scope

Decision:

- Use Option B for this SOW.
- Fix the downstream SHM lookup regression now.
- Keep returning `NIPC_ERR_OVERFLOW` when a single semantic lookup request exceeds `NIPC_MAX_PAYLOAD_CAP`.
- Track transparent split/stitch separately if implementation or validation shows it is needed for production workloads.

Evidence and context:

- The reported downstream failure is below the hard cap and involves SHM buffer aliasing, too-small internal request negotiation, and stale session cleanup.
- Transparent split/stitch would change response assembly and borrowed-view lifetime behavior.

Implications:

- This SOW keeps the public typed API unchanged.
- Regression tests must prove normal lookup batches do not require caller-provided request byte sizes.

## Plan

1. Add fail-first C POSIX SHM lookup tests for both new lookup methods.
2. Port and tighten the C POSIX/Windows service fixes from downstream.
3. Preserve the public typed API boundary and implement internal request sizing/capacity recovery.
4. Update tests that encode old defaults or old API expectations.
5. Update specs/docs/reference skill.
6. Run Linux full validation.
7. Run Windows validation from the Windows environment.
8. Run same-failure scans and close validation gates.
9. Vendor into the correct Netdata target only after upstream validation is clean.

## Execution Log

### 2026-05-28

- Created SOW after reviewing current/pending SOWs. Existing pending SOW-0002 is unrelated root TODO classification work.
- Confirmed no runtime project skills exist under `.agents/skills/`.
- Reviewed relevant docs and source files.
- Compared downstream Netdata service-layer fixes against this repository.
- Ran current targeted upstream service validation:
  - `/usr/bin/cmake --build build --target test_service test_service_payload_limits -j12`
  - `/usr/bin/ctest --test-dir build -R '^(test_service|test_service_payload_limits)$' --output-on-failure`
  - Result: both tests passed, confirming the current suite misses the reported SHM lookup regression.
- Found that `/usr/bin/ctest` must be used because the user-local `ctest` wrapper fails to import its Python `cmake` module.
- Implemented SHM lookup regression coverage and service repairs:
  - POSIX and Windows SHM sends now move aliased payload bytes into the outer message payload slot before encoding the outer header.
  - The internal typed request default is now `NIPC_MAX_PAYLOAD_DEFAULT`, not `16`.
  - POSIX SHM server sessions now check the control fd on receive timeout and close stale sessions after peer disconnect.
  - POSIX and Windows managed servers close sessions after terminal service errors.
  - Public Level 2+ service config continues to hide request payload byte sizing.
- Found protocol drift while validating downstream Netdata:
  - Netdata intentionally allows `PID_KNOWN + APPS_CGROUP_UNKNOWN_RETRY_LATER` with an empty cgroup path for a known process whose cgroup path/cache is not available yet.
  - Ported that contract into C, Rust, Go, docs, and tests.
  - `UNKNOWN_PERMANENT` still requires a non-empty cgroup path.
- Created `.agents/sow/pending/SOW-0007-20260528-transparent-lookup-batch-splitting.md` for the explicitly scoped-out transparent split/stitch work.
- Vendored the SDK service files into `~/src/PRs/topology-containers` and removed the downstream caller workaround that set `.max_request_payload_bytes = NIPC_MAX_PAYLOAD_CAP`.

## Validation

Acceptance criteria evidence:

- SHM lookup corruption regression is covered by POSIX C lookup-over-SHM tests in `tests/fixtures/c/test_service.c`.
- C SHM send uses overlap-safe payload movement before envelope header encoding in POSIX and Windows service files.
- Public typed configs still do not expose request byte capacity in `src/libnetdata/netipc/include/netipc/netipc_service.h`.
- POSIX stale SHM session cleanup is covered by `test_shm_lookup_session_closes_after_client_disconnect`.
- Terminal service error session close is covered by `test_server_closes_after_terminal_error_response`.
- Apps lookup retry-later empty cgroup path is covered in C, Rust, Go, and downstream Netdata tests.
- Vendored Netdata SDK files match upstream plugin-ipc for:
  - `src/libnetdata/netipc/include/netipc/netipc_service.h`
  - `src/libnetdata/netipc/src/protocol/netipc_protocol.c`
  - `src/libnetdata/netipc/src/service/netipc_service.c`
  - `src/libnetdata/netipc/src/service/netipc_service_win.c`

Tests or equivalent validation:

- Focused Linux upstream:
  - `/usr/bin/cmake --build build --target test_protocol test_service -j$(nproc)`
  - `timeout 180 /usr/bin/ctest --test-dir build -R '^(test_protocol|test_service)$' --output-on-failure`
  - `timeout 300 go test ./pkg/netipc/protocol` from `src/go`
  - `timeout 300 cargo test --manifest-path src/crates/netipc/Cargo.toml protocol::lookup -- --test-threads=1`
  - Result: passed.
- Full Linux upstream:
  - `/usr/bin/cmake --build build -j$(nproc)`
  - `timeout 700 /usr/bin/ctest --test-dir build --output-on-failure`
  - Result: 46/46 passed.
- Linux coverage:
  - `timeout 1200 bash tests/run-coverage-c.sh`
  - Result: total line coverage 91.1%; `netipc_service.c` 90.1%.
  - `timeout 1200 bash tests/run-coverage-rust.sh`
  - Result: 329 Rust tests; total line coverage 92.81%.
  - `timeout 1200 bash tests/run-coverage-go.sh`
  - Result: total line coverage 90.0%.
- Linux interop:
  - `timeout 600 bash tests/interop_codec.sh`
  - `timeout 600 bash tests/test_uds_interop.sh`
  - `timeout 600 bash tests/test_shm_interop.sh`
  - `timeout 600 bash tests/test_service_interop.sh`
  - `timeout 600 bash tests/test_service_shm_interop.sh`
  - Result: passed.
- Windows full validation before the final protocol-drift port:
  - `timeout 1800 bash tests/run-coverage-c-windows.sh`
  - `timeout 1800 bash tests/run-coverage-rust-windows.sh`
  - `timeout 1800 bash tests/run-coverage-go-windows.sh`
  - `timeout 1800 bash tests/run-windows-msys-validation.sh /tmp/netipc-msys-validation-shm-lookup 2`
  - Result: passed; Windows C total 91.9%, Rust total 90.85%, Go total 90.1%, benchmark policy passed.
- Windows targeted validation after the final protocol-drift port:
  - `cmake --build build-windows-targeted-final --target test_protocol interop_codec_c test_win_service_extra -j4`
  - `ctest --test-dir build-windows-targeted-final --output-on-failure -R '^(test_protocol|interop_codec|test_win_service_extra)$'`
  - `go test ./pkg/netipc/protocol`
  - `cargo test --manifest-path src/crates/netipc/Cargo.toml protocol::lookup -- --test-threads=1`
  - Result: passed.
- Vendored Netdata focused validation:
  - `sudo -n /usr/bin/cmake --build build --target cgroup-lookup-netipc-test apps-lookup-protocol-test network-viewer-apps-lookup-client-test -j$(nproc)`
  - `sudo -n ./build/cgroup-lookup-netipc-test`
  - `sudo -n ./build/apps-lookup-protocol-test`
  - `sudo -n ./build/network-viewer-apps-lookup-client-test`
  - Result: passed.

Real-use evidence:

- Downstream Netdata worker reported live Agent validation after local downstream fixes.
- This SOW validated the upstream SDK directly and then validated the focused vendored Netdata binaries.

Reviewer findings:

- No external reviewer pass was requested for this SOW after implementation. Validation relied on fail-first-style regression tests, coverage gates, interop tests, Windows native tests, and vendored Netdata focused tests.

Same-failure scan:

- `rg 'memcpy\(msg \+ NIPC_HEADER_LEN, payload' src tests docs` no longer finds the vulnerable service send path; the remaining hit is a raw Windows test helper that constructs independent buffers.
- `rg 'max_request_payload_bytes == 16u' src tests docs` found no remaining old default assertions.
- Netdata vendored scan found no remaining `.max_request_payload_bytes = NIPC_MAX_PAYLOAD_CAP` caller workaround in the two lookup clients.

Sensitive data gate:

- This SOW records only sanitized synthetic paths, command evidence, and file evidence. It does not record raw live Agent process, container, customer, secret, personal, private endpoint, or customer-identifying IP data.

Artifact maintenance gate:

- AGENTS.md: no update needed; workflow policy did not change.
- Runtime project skills: none exist; no update needed.
- Specs: public protocol/API docs were updated in `docs/codec-apps-lookup.md` and `docs/level2-typed-api.md`.
- End-user/operator docs: no separate published operator doc was affected beyond the SDK docs.
- End-user/operator skills: `docs/netipc-integrator-skill.md` updated for terminal service error reconnect behavior.
- SOW lifecycle: SOW status is `completed`; this file is moved to `.agents/sow/done/` in the same commit as the implementation. SOW-0007 tracks the scoped-out transparent split/stitch work.

Specs update:

- Updated `docs/codec-apps-lookup.md` for apps retry-later empty cgroup path semantics.
- Updated `docs/level2-typed-api.md` for terminal service error session close behavior.

Project skills update:

- No runtime project skills exist, so no update was needed.

End-user/operator docs update:

- No separate end-user/operator docs were affected.

End-user/operator skills update:

- Updated `docs/netipc-integrator-skill.md`.

Lessons:

- Downstream bug fixes can include temporary public API workarounds. Upstream porting needs to preserve the intended typed API boundary instead of copying every downstream field.
- Protocol behavior discovered in downstream integration tests must be ported into all language codecs, not just the C vendored copy.

Follow-up mapping:

- Transparent split/stitch for semantic lookup batches above `NIPC_MAX_PAYLOAD_CAP` is tracked by `.agents/sow/pending/SOW-0007-20260528-transparent-lookup-batch-splitting.md`.

## Outcome

Implementation, validation, Netdata vendoring, and follow-up mapping are complete.

## Lessons Extracted

- Preserve the public typed API boundary during downstream ports; callers should not learn message byte layouts to work around typed API sizing.
- Validate protocol changes across C, Rust, and Go even when the production consumer is currently C-only.

## Followup

Tracked:

- `.agents/sow/pending/SOW-0007-20260528-transparent-lookup-batch-splitting.md`

## Regression Log

None yet.
