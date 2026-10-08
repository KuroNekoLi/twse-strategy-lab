# Quality Gates

1. **Intent:** distinguish idea assessment, change proposal, and requested implementation. Capture constraints, expected outcome, acceptance, and non-goals.
2. **Evidence:** label observations, source-backed claims, estimates, assumptions and unknowns. Cite current external evidence; do not invent market/user/financial metrics.
3. **Decision:** record alternatives including status quo, user impact, technical/security/accessibility, marketing, operational and cost factors where relevant. Identify dissent and the evidence that could resolve it. Human/product owner retains product decision.
4. **Design:** inspect repository and contracts; document risky interface/data/security/operational decisions.
5. **Implementation:** keep writes in assigned scope; verify developer output against actual diff.
6. **Independent review:** separate reviewer, fresh bounded context, checks actual diff against acceptance; returns findings with location and impact. An implementer self-review does not meet independent review.
7. **Verification:** map criteria to QA evidence; report exact checks that ran. Keep automated tests, build, browser/device/runtime, accessibility, performance and user research evidence distinct.
8. **Release:** assess rollback, migrations, support, monitoring and owner; any deployment/publish/expense/external notification requires user authorization.
9. **Closure:** report participating runtime identities and fallback, artifacts, decision, evidence, unresolved risks and unverified behavior.
