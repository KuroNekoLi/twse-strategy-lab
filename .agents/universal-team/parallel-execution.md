# Parallel Execution

Parallelize independent, bounded work only when workers will not contend for the same files, mutable data, decision dependency or live resource. Respect runtime concurrency and project resource limits.

| Work | Parallel? | Coordination |
|---|---|---|
| Market, user, and operations research | Usually | Separate questions and sources; synthesize after all handoffs |
| Brainstorming | Yes | Give workers the same problem brief; collect independent options before sharing |
| Architecture and read-only discovery | Often | Incorporate findings before implementation |
| Same feature implementation and code review | No | Review begins after a concrete diff exists, with independent context |
| Different implementation files | Sometimes | Agree on contracts and file ownership first |
| Shared config, database migrations, release, production | Usually serialize | One owner, ordered checks and rollback boundary |
| QA planning and development | Often | Plan from accepted criteria; final behavior check waits for buildable change |
