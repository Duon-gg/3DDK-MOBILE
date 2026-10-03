# 3DDK Tasks — implementation ledger

Binding specification: user-approved 28-day plan in this chat (2026-10-03).
Scope: one group; verified Firebase email and allowlist; leader-managed tasks with multiple assignees and one representative; review workflow; Room cache and explicit draft submission; Supabase REST API and FCM; signed Android artifacts.

## Tasks
- [ ] 1. Transactional database, permission/state/idempotency tests.
- [ ] 2. Verified Firebase Edge API and notification worker.
- [ ] 3. Android build, model, Room, REST and session repositories.
- [ ] 4. Vietnamese XML screens and complete workflow.
- [ ] 5. CI, deployment/signing instructions, executable checks and artifacts.
- [ ] 6. Independent review and fixes.

## Pre-flight
- API/Android share camelCase JSON, UUID task IDs, UTC ISO timestamps, statuses TODO/IN_PROGRESS/IN_REVIEW/DONE, LOW/NORMAL/HIGH priority.
- Member UID is Firebase subject (text); invitations use normalized email; API validates verified Firebase token before calling the database.
- Mutations carry requestId; task updates carry expectedVersion. Draft requestId survives uncertain network responses.
- There is no application baseline to test: initial repository contains only README.
- Cloud identifiers and credentials have not been supplied. Build and local verification continue; live deployment is not claimed without cloud evidence.
- Ruling: use a local PGlite PostgreSQL engine for reproducible SQL tests without Docker; deployed SQL remains PostgreSQL. Cron/HTTP extensions get a separate deployment script.

## Verification
Pending implementation; no live-cloud or physical-device claim.
