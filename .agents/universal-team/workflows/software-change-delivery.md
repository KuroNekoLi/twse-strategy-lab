# Software Change Delivery

1. PM or requester supplies scope, outcome and acceptance; clarify only blocking ambiguity.
2. Architect inspects current code and constraints when the change crosses modules, data or public contracts. Read-only research can run in parallel.
3. Assign a stack-matched developer for implementation and disjoint write scopes for independent work.
4. After a reviewable diff exists, a separate Code Reviewer inspects it against requirements. The reviewer returns findings with location, condition and impact.
5. Developer checks each finding against repository behavior; fix verified issues or document a reasoned disposition.
6. QA maps acceptance criteria to tests. For requested user-visible web/mobile behavior, dispatch Device & Browser Tester to operate an available browser or Android/iOS simulator and return runtime artifacts; add test automation engineer when the harness itself changes. Do not label source review or mobile viewport emulation as native device execution.
7. Add security, privacy, database, accessibility, performance, DevOps, SRE, release and documentation roles only where affected.
8. Report implementation, review, tests, runtime evidence and remaining unknowns separately.
