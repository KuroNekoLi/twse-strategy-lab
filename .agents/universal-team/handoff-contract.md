# Handoff Contract

Every delegated assignment and return uses this record.

## Assignment
- `task_id`, role, owner/runtime identity
- objective and why independent
- context files/links; explicit assumptions
- allowed paths and tools; prohibited actions
- dependencies and shared-file coordination
- expected artifact and acceptance check
- status: queued / running / blocked

## Return
- `task_id`, runtime identity, status: complete / blocked / partial
- artifacts and changed paths (if any)
- evidence/checks actually performed and results
- decisions/assumptions, unresolved risks, next dependency

The orchestrator rejects untraceable completion claims. A blocked worker returns the blocker and useful partial evidence instead of waiting silently.

## Evidence trace fields for product assessment
- claim and claim type (observed/source/user input/estimate/assumption)
- source URI and date, population/sample/unit, recency, caveat
- assessment implication and confidence rationale
- research limitation or next validation step

## Review independence
A review worker receives requirements, relevant paths and the proposed diff/commit references, not implementer conversation history or the implementer's defense. Review findings and response are returned separately. The implementer must technically evaluate feedback against project behavior, fix supported issues, or document a reasoned unresolved disposition.
