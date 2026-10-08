# Delegation Protocol

For every assignment include an ID, assigned role, independent objective, decision context/source paths, scope, allowed tools/data, explicit write paths, dependencies, expected handoff, and acceptance check. Ask for a concise result and supporting evidence, not private reasoning or full transcripts.

Use the runtime's actual agent creation/delegation action. Capture the returned identity/thread, wait for completion or blocked status, and record the handoff. A role prompt is not a separate agent. If no true delegation action is available, report `SEQUENTIAL_SINGLE_AGENT`. Never invent identities, outcomes, or parallel execution.

The orchestrator checks results against assignments, inspects worker changes and verifies any required evidence. If the scope, ownership, data access, or authorization is unclear, return a useful partial handoff and blocker rather than widening access or performing an external side effect.
