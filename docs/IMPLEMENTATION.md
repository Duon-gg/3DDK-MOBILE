# 3DDK Tasks — implementation ledger

Binding specification: user-approved 28-day plan in this chat (2026-10-03).
Scope: one group; verified Firebase email and allowlist; leader-managed tasks with multiple assignees and one representative; review workflow; Room cache and explicit draft submission; Supabase REST API and FCM; signed Android artifacts.

## Tasks
- [x] 1. Transactional database, permission/state/idempotency tests.
- [x] 2. Verified Firebase Edge API and notification worker (live FCM pending configuration).
- [x] 3. Android build, model, Room, REST and session repositories.
- [x] 4. Vietnamese XML screens and complete workflow source.
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
- PostgreSQL acceptance suite: 8/8 passed; initially failed before schema/functions existed.
- Signed-token tests: 2/2 passed after initial unimplemented verifier failure; Deno type checks pass.
- Android unit tests: 5/5 passed after 5/5 initial failures (task permissions/deadlines/link validation and 401 retry/409 handling).
- Gradle assembleDebug + lintDebug passed. Known deprecation: EncryptedSharedPreferences required by course.
- API35 emulator: 3 instrumentation tests completed, no reported failures; final Gradle completion pending at this log entry.
- User selected source and setup instructions first; Firebase/Supabase projects do not yet exist.
- Ruling: retain a single-group transactional write lock for correctness under concurrent mutations; throughput ceiling documented in API.md.
- Ruling: signed builds without cloud credentials are installation/configuration previews, not a production release. Live-cloud acceptance and user study are pending, not passed.
