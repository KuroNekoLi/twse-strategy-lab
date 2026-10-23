# TWSE Strategy Lab VPS backend deployment
Pushing backend changes to `main` runs the existing Pages workflow and backend checks, then builds and deploys the API container to the VPS when `VPS_DEPLOY_ENABLED` is `true`. MySQL data remains in the existing database service.
