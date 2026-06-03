# SOW-0014 - Maintainability Hotspots

## Status

Status: open

Sub-state: pending after Codacy metrics investigation; user selected keeping complexity and duplication metrics active and fixing real source hotspots.

## Requirements

### Purpose

Improve repository hygiene by reducing real source complexity and duplication without weakening useful Codacy maintainability signals.

### User Request

The user selected keeping complexity and duplication metrics active and treating real hotspots as source remediation work.

### Assistant Understanding

Facts:

- Codacy reports repository-level complexity and duplication percentages even when the current issue backlog is zero.
- Local Lizard and JSCPD approximations found real maintainability pressure in source code, not only test or fixture scope.
- Coverage upload work is tracked separately in `SOW-0013`.

Inferences:

- Some duplication is intentional cross-language parity or POSIX/Windows parity and should not be removed mechanically.
- The useful remediation target is high-risk source duplication and complexity that obscures protocol, transport, or service behavior.

Unknowns:

- Which hotspots are worth changing after maintainers evaluate protocol clarity, parity, and performance tradeoffs.

### Acceptance Criteria

- Identify source complexity and duplication hotspots with file-level evidence.
- Separate intentional parity duplication from avoidable implementation duplication.
- Propose user decisions before refactoring protocol, transport, or service code.
- Reduce selected real hotspots without changing public protocol/API behavior.
- Validate C, Rust, and Go tests and relevant interoperability scripts after changes.

## Analysis

Sources checked:

- `SOW-0013` local Lizard and JSCPD findings.

Current state:

- Highest local source complexity appeared in lookup builders/decoders, service session loops, and transport send/receive paths.
- Largest local source duplicate groups included POSIX/Windows wrapper parity, typed lookup service wrappers, and language/service parity.

Risks:

- Refactoring protocol and transport code can break wire compatibility.
- Removing intentional duplication can make cross-language parity harder to audit.
- Performance-sensitive hot paths need benchmark evidence before accepting structural changes.

## Pre-Implementation Gate

Status: needs-user-decision

Problem / root-cause model:

- Working theory: Codacy's maintainability percentages include real complexity and duplication, but not all duplication is bad. Some repeated code preserves explicit cross-language or cross-platform protocol parity.

Evidence reviewed:

- `SOW-0013` Codacy metrics investigation.
- Local Lizard and JSCPD approximations recorded in `SOW-0013`.

Affected contracts and surfaces:

- C, Rust, and Go protocol, transport, and service implementations.
- Cross-language interoperability tests and benchmarks.
- Public protocol/API documentation if any behavior changes are proposed.

Existing patterns to reuse:

- Preserve layer boundaries documented in `docs/code-organization.md`.
- Preserve cross-language parity tests and benchmark scripts.

Risk and blast radius:

- Medium to high depending on selected hotspots.
- Protocol and transport refactors can affect all SDK consumers.
- Performance changes need benchmark validation.

Sensitive data handling plan:

- Do not read `.env`.
- Do not write raw secrets, credentials, tokens, customer data, personal data, private endpoints, or proprietary details into durable artifacts.

Implementation plan:

1. Reproduce local complexity and duplication reports with current tools and exact thresholds.
2. Present hotspot groups with file/line evidence and user decisions.
3. Refactor only selected hotspots.
4. Run unit, interoperability, and benchmark validation appropriate to touched code.

Validation plan:

- Lizard and JSCPD before/after comparison.
- CTest and language-specific tests for touched areas.
- Relevant interop scripts for touched protocol/transport/service paths.
- Benchmarks if hot-path structure changes.

Artifact impact plan:

- AGENTS.md: likely unaffected unless workflow rules change.
- Runtime project skills: likely unaffected unless reusable maintainability workflow emerges.
- Specs: update only if behavior/contract changes.
- End-user/operator docs: update only if public workflow or API guidance changes.
- End-user/operator skills: update only if public/operator guidance changes.
- SOW lifecycle: remain pending until explicitly started.

Open-source reference evidence:

- Not checked yet; future implementation should use local mirrored repositories only where comparable refactor patterns are relevant.

Open decisions:

- Decide which hotspot groups to remediate first after evidence is refreshed.

## Implications And Decisions

- User decision from `SOW-0013`: keep complexity and duplication metrics active; fix real source hotspots instead of weakening useful rules.

## Plan

1. Refresh metric evidence.
2. Present hotspot options.
3. Implement selected source remediation.
4. Validate behavior and metrics.

## Execution Log

### 2026-06-03

- Created as follow-up from `SOW-0013`.

## Validation

Pending.

## Outcome

Pending.

## Lessons Extracted

Pending.

## Followup

None yet.

## Regression Log

None yet.
