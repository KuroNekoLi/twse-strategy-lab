# Role Groups and Practical Crews

This is a selectable role library; it is not a requirement to start 33 agents for every request.

## New opportunity or feature assessment

Core: Product Manager + Idea Facilitator. Add User Researcher and/or Market Researcher when evidence can change the decision; Data Analyst when metrics/baselines matter; Business Analyst for workflow/rule complexity; Decision Challenger for consequential proposals. Include Marketing, Growth, Operations, Finance, Architect, UX, Security, Privacy, Database and Accessibility roles only when the decision warrants them.

## Web or mobile feature

Product Manager + Architect when necessary + one stack-matched developer (frontend, backend, or mobile). A separate Code Reviewer reads the resulting diff; QA maps accepted criteria to evidence. Dispatch Device & Browser Tester when actual browser, Android Emulator, iOS Simulator, or physical-device evidence is requested; verify that the runtime exists. Add test automation, Data/DB, Security/Privacy, Accessibility, Performance, DevOps/SRE, Release Manager, Integration Engineer and Technical Writer where relevant.

## Incident or service change

Incident Responder leads the evidence timeline. Add SRE, backend, database, integration, security/privacy specialists based on the affected boundary. Keep production changes with an authorized human owner.

## Boundaries

- PM owns requirements/acceptance; Facilitator expands options; Challenger stress-tests the leading option.
- Market Researcher studies category/competitors; User Researcher examines user evidence; Data Analyst studies telemetry/operational data. None invents validation.
- Marketing/Growth cover positioning and experiments; Operations covers process/service capacity; Finance covers input-based scenarios.
- Architect owns system-level design; specialist developers implement bounded code.
- Reviewer finds defects in an actual diff; QA checks requirements and test evidence; Device & Browser Tester operates available UI runtimes and labels simulator/emulation/physical-device evidence precisely.
- DevOps owns delivery automation/infrastructure; SRE owns service reliability and operational readiness.
- Database Engineer owns datastore/migration correctness; Privacy Engineer owns personal-data lifecycle controls; Security Reviewer examines threats and controls.
