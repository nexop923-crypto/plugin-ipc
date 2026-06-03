# SOW-0012 - Dependabot PR Maintenance

## Status

Status: completed

Sub-state: all three Dependabot PRs were handled on `main`, remote validation passed, and the PRs are closed as obsolete.

## Requirements

### Purpose

Keep repository dependency and workflow maintenance current without weakening the strong GitHub/Codacy static-analysis coverage now enabled on `main`.

### User Request

The user observed there are 3 open PRs and selected option A: process all 3 now, rebase/update, run validation, merge if green, and close only if obsolete.

### Assistant Understanding

Facts:

- `main` currently has zero open GitHub Code Scanning alerts.
- Open PR `#5` is a Dependabot GitHub Actions update touching workflow pinned SHAs.
- Open PR `#3` is a Dependabot Rust grouped update for `libc` and `proptest`.
- Open PR `#2` is a Dependabot Rust `rand` update and is currently dirty against `main`.
- The repository has no current SOW before this work.

Inferences:

- The dependency PRs should be processed one by one so validation failures can be attributed to a specific update.
- The dirty `rand` PR may need rebasing/recreation instead of direct merge.

Unknowns:

- Whether branch protection and CLA status will allow merging Dependabot PRs from this session; this will be verified with `gh`.

### Acceptance Criteria

- Each of PR `#2`, `#3`, and `#5` is either merged after validation or explicitly closed as obsolete with evidence.
- `main` remains green on required GitHub workflows after any merges.
- GitHub Code Scanning remains at zero open alerts after the final pushed state.
- SOW records validation, artifact impact, and final PR disposition.

## Analysis

Sources checked:

- `gh pr list --state open --json ...`
- `gh pr view 2`, `gh pr view 3`, `gh pr view 5`
- `gh pr diff 2`, `gh pr diff 3`
- `.github/workflows/*`
- `src/crates/netipc/Cargo.lock`

Current state:

- PR `#5`: `BLOCKED`, review required; CI mostly green, CLA pending; updates pinned GitHub Action SHAs.
- PR `#3`: `BLOCKED`, review required; old checks include failures from before scanner cleanup; updates only `src/crates/netipc/Cargo.lock`.
- PR `#2`: `DIRTY`, review required; updates only `rand` in `src/crates/netipc/Cargo.lock`.
- Current `main` still has older action SHAs and Rust lock versions, so the updates are not already merged.

Risks:

- Dependabot PR branches may be stale and need branch updates before checks reflect the current scanner configuration.
- Dependency updates can change generated lock contents and cause Rust tests or cargo-audit results to change.
- Workflow action updates can affect CI behavior even when local tests pass.
- CLA pending status may block merge even with successful validation.

## Pre-Implementation Gate

Status: ready

Problem / root-cause model:

- The repository has maintenance PRs open after scanner hardening. They are not code features, but they affect dependency and workflow supply-chain hygiene. They need to be validated against the current hard scanner baseline before merging or closing.

Evidence reviewed:

- GitHub PR metadata for PRs `#2`, `#3`, and `#5`.
- Current workflow pins in `.github/workflows/*`.
- Current Rust lock versions in `src/crates/netipc/Cargo.lock`.
- GitHub Code Scanning open-alert query returned zero alerts on `main` before this SOW.

Affected contracts and surfaces:

- CI workflow execution and pinned GitHub Actions.
- Rust dependency lockfile under `src/crates/netipc/Cargo.lock`.
- GitHub PR state, reviews, and merge/close disposition.
- SOW lifecycle records.

Existing patterns to reuse:

- Project validation commands in `AGENTS.md`.
- Existing GitHub Actions workflows and branch-protection checks.
- Codacy PR-review workflow for checking PR quality data.
- Exact-file staging only; no broad `git add`.

Risk and blast radius:

- Low product-runtime risk for workflow-only PR `#5`.
- Low-to-medium test/supply-chain risk for Rust lockfile PRs `#2` and `#3`.
- CI configuration risk if action version updates change behavior.
- No destructive operations are planned.

Sensitive data handling plan:

- Do not read or commit `.env`.
- Do not write tokens, credentials, account IDs, private endpoints, personal data, customer data, or raw sensitive values into durable artifacts.
- PR and CI evidence will cite public PR numbers, statuses, file paths, and tool results only.

Implementation plan:

1. Inspect PR diffs and remote Codacy/CI state for all three PRs.
2. Refresh/rebase PR branches where supported or recreate equivalent local changes when Dependabot branches cannot be updated.
3. Validate each update with relevant local and remote checks.
4. Merge green PRs or close obsolete PRs with evidence.
5. Verify final `main` workflows and code scanning state.

Validation plan:

- GitHub PR status and mergeability checks.
- Local Rust validation for Cargo.lock changes: cargo test build, clippy hard gate, cargo audit, cargo deny.
- Workflow/static scanner validation via GitHub Actions after merges.
- Codacy PR data where available.
- Final open Code Scanning alert query.

Artifact impact plan:

- AGENTS.md: no expected update; workflow commands remain accurate.
- Runtime project skills: no expected update; no reusable repo workflow changes are anticipated.
- Specs: no expected update; no protocol/API behavior changes.
- End-user/operator docs: no expected update; maintenance only.
- End-user/operator skills: no expected update; no public integration guidance changes.
- SOW lifecycle: complete and move this SOW to `done/` in the final maintenance commit if a repo commit is needed; if only PR merges occur, record final state and complete SOW separately only when consistent with project rules.

Open-source reference evidence:

- No external repository source review needed; these are Dependabot version bumps with upstream release links in the PR descriptions and validation is local/CI-based.

Open decisions:

- User selected option A: process all three PRs now, update/rebase, validate, merge green PRs, and close only if obsolete.

## Implications And Decisions

1. PR maintenance strategy:

- A selected: Process all 3 now, refresh as needed, validate, merge if green, close only if obsolete.
- Implication: More work now, but dependency and scanner hygiene stays current.
- Risk: A PR may be blocked by CLA or branch protection and require a follow-up action outside this session.

## Plan

1. Review PR `#5`, `#3`, `#2` details, Codacy data, and CI state.
2. Refresh PR branches or apply equivalent updates safely.
3. Validate each update with local and remote checks.
4. Merge or close each PR based on evidence.
5. Verify final `main` workflows and code scanning state.

## Execution Log

### 2026-06-03

- Created SOW after user selected option A.
- Queried Codacy Cloud for PRs `#2`, `#3`, and `#5`; all reported up to
  standards with `+0 / -0` issues.
- Attempted to refresh PR branches:
  - PR `#3` updated cleanly.
  - PR `#2` could not be updated because of conflicts in `Cargo.lock`.
  - PR `#5` could not be updated because the current GitHub token cannot update
    workflow files without `workflow` scope.
- Applied the exact maintenance updates locally on `main` so the PRs can be
  closed as obsolete after validation and push:
  - `actions/checkout` pinned SHA for `v6.0.2` -> `v6.0.3`.
  - `actions/setup-node` pinned SHA for `v6.0.0` -> `v6.4.0`.
  - `github/codeql-action` pinned SHA for `v4.36.0` -> `v4.36.1`.
  - `libc` lock entry `0.2.183` -> `0.2.186`.
  - `proptest` lock entry `1.10.0` -> `1.11.0`.
  - `rand` lock entry `0.9.3` -> `0.9.4`.
- Committed and pushed `6e27ee6` (`Apply Dependabot maintenance updates`) to
  `main`.
- Verified GitHub Actions on pushed commit `6e27ee6`:
  - `Dependabot Updates`: success.
  - `Supply Chain Security`: success.
  - `Runtime Safety`: success.
  - `CodeQL`: success.
  - `Codacy Local Analysis`: success.
  - `Static Analysis`: success.
- Verified GitHub Code Scanning open-alert count is 0 after the pushed state.
- Closed PR `#2` as obsolete because `rand` `0.9.4` is already on `main`.
- Closed PR `#3` as obsolete because `libc` `0.2.186` and `proptest`
  `1.11.0` are already on `main`.
- Verified PR `#5` was already closed after the equivalent workflow updates
  landed on `main`; manual close with a comment was not accepted because the PR
  was already closed.
- Verified there are no open GitHub PRs after this maintenance.

## Validation

Acceptance criteria evidence:

- PR `#2` is closed, not merged, with `closedAt`
  `2026-06-03T07:55:37Z`; its `rand` `0.9.4` update is present on `main` in
  `6e27ee6`.
- PR `#3` is closed, not merged, with `closedAt`
  `2026-06-03T07:55:37Z`; its `libc` `0.2.186` and `proptest` `1.11.0`
  updates are present on `main` in `6e27ee6`.
- PR `#5` is closed, not merged, with `closedAt`
  `2026-06-03T07:51:25Z`; its GitHub Actions updates are present on `main` in
  `6e27ee6`.
- `gh pr list --state open --limit 20` returned an empty list.
- Main workflow runs for `6e27ee6` all completed successfully:
  `Dependabot Updates`, `Supply Chain Security`, `Runtime Safety`, `CodeQL`,
  `Codacy Local Analysis`, and `Static Analysis`.
- GitHub Code Scanning open-alert query returned 0.

Tests or equivalent validation:

- `actionlint` passed.
- `git diff --check` passed.
- `osv-scanner scan --recursive --format sarif --output-file /tmp/plugin-ipc-osv-dependabot.sarif .`
  exited 0, and the SARIF result count was 0.
- `cargo test --manifest-path src/crates/netipc/Cargo.toml --all-targets --all-features --no-run`
  passed with the updated lockfile.
- `cargo clippy --manifest-path src/crates/netipc/Cargo.toml --all-targets --all-features -- -D clippy::correctness -D clippy::suspicious`
  passed. Clippy emitted the existing warning-only baseline.
- `cargo audit` passed in `src/crates/netipc`.
- `cargo deny check advisories bans sources` passed in `src/crates/netipc`.
- `make` passed.
- `codacy-analysis analyze . --install-dependencies --output-format json --output /tmp/plugin-ipc-codacy-dependabot.json --parallel-tools 2 --tool-timeout 900000`
  exited 0 with 0 issues and 0 tool errors.
- `/usr/bin/ctest --test-dir build --output-on-failure` passed all 46 tests.

Real-use evidence:

- GitHub Actions ran on commit `6e27ee6` after push and completed
  successfully for all scanner and supply-chain workflows.
- GitHub PR state now shows PRs `#2`, `#3`, and `#5` closed and zero open PRs.

Reviewer findings:

- Codacy Cloud PR summaries for `#2`, `#3`, and `#5` reported up to standards
  with 0 introduced issues.

Same-failure scan:

- Current local OSV and Codacy scans reported 0 issues after applying the
  updates.
- GitHub Code Scanning open-alert query reported 0 after the pushed state.

Sensitive data gate:

- No raw secrets, credentials, bearer tokens, private endpoints, customer data,
  personal data, or proprietary incident details were written to durable
  artifacts.

Artifact maintenance gate:

- AGENTS.md: no update needed; project validation commands remain accurate.
- Runtime project skills: no update needed; no reusable repo workflow changed.
- Specs: no update needed; dependency/workflow maintenance did not change
  protocol, API, transport, or data-format behavior.
- End-user/operator docs: no update needed; no user/operator workflow changed.
- End-user/operator skills: no update needed; no public integration guidance
  changed.
- SOW lifecycle: SOW is marked completed and moved to `done/` with the final
  lifecycle commit.

Specs update:

- No spec update needed; no product behavior changed.

Project skills update:

- No runtime project skill update needed; no new reusable local workflow was
  discovered beyond existing project commands.

End-user/operator docs update:

- No end-user/operator docs update needed; maintenance only.

End-user/operator skills update:

- No end-user/operator skill update needed; maintenance only.

Lessons:

- Dependabot workflow branches can be blocked by token scope when they modify
  workflow files; applying the exact updates on `main` is a valid maintenance
  path when the final workflow and code-scanning gates pass.
- Dirty lockfile PRs should not be merged blindly; applying the target lock
  update locally and validating the resolved lockfile keeps the final evidence
  attributable to the resulting `main` state.

Follow-up mapping:

- No follow-up SOW is needed; all three PRs are closed, `main` is green, and
  GitHub Code Scanning has 0 open alerts.

## Outcome

Completed. The three open Dependabot PRs were made obsolete by commit
`6e27ee6`, remote validation passed on `main`, GitHub Code Scanning has zero
open alerts, and no open PRs remain.

## Lessons Extracted

- Workflow-file update PRs may require a token with `workflow` scope for branch
  refresh operations.
- For scanner hygiene work, final evidence should be collected from both local
  tools and GitHub after push because local SARIF generation alone does not
  prove the repository-visible code-scanning state.

## Followup

None.

## Regression Log

None yet.
