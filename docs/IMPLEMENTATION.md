# 3DDK Tasks — implementation ledger

Binding specification: user-approved 28-day plan in this chat (2026-10-03).
Scope: one group; verified Firebase email and allowlist; leader-managed tasks with multiple assignees and one representative; review workflow; Room cache and explicit draft submission; Supabase REST API and FCM; signed Android artifacts.

## Tasks
- [x] 1. Transactional database, permission/state/idempotency tests.
- [x] 2. Verified Firebase Edge API and notification worker (live FCM pending configuration).
- [x] 3. Android build, model, Room, REST and session repositories.
- [x] 4. Vietnamese XML screens and complete workflow source.
- [x] 5. CI, deployment/signing instructions, executable checks and signed configuration-preview artifacts.
- [x] 6. Independent review and fixes.

## Pre-flight
- API/Android share camelCase JSON, UUID task IDs, UTC ISO timestamps, statuses TODO/IN_PROGRESS/IN_REVIEW/DONE, LOW/NORMAL/HIGH priority.
- Member UID is Firebase subject (text); invitations use normalized email; API validates verified Firebase token before calling the database.
- Mutations carry requestId; task updates carry expectedVersion. Draft requestId survives uncertain network responses.
- There is no application baseline to test: initial repository contains only README.
- Cloud identifiers and credentials have not been supplied. Build and local verification continue; live deployment is not claimed without cloud evidence.
- Ruling: use a local PGlite PostgreSQL engine for reproducible SQL tests without Docker; deployed SQL remains PostgreSQL. Cron/HTTP extensions get a separate deployment script.

## Verification
- PostgreSQL acceptance suite: 9/9 passed; initially failed before schema/functions existed. Worker lease regression also failed before its fix.
- Signed-token tests: 2/2 passed after initial unimplemented verifier failure; Deno type checks pass.
- Android unit tests: 7/7 passed (task permissions/deadlines/link validation, 401 retry/409 handling and editor retry/version regressions).
- Gradle assembleDebug + lintDebug passed. Known deprecation: EncryptedSharedPreferences required by course.
- API35 emulator: 4/4 instrumentation tests passed; combined unit/lint/debug/instrumentation/release build finished successfully.
- API24 emulator: 4/4 instrumentation tests passed via AndroidJUnitRunner (launch/recreation, draft persistence/isolation, authentication failure retention, transaction rollback).
- Signed APK: apksigner verified v2 signature; installed and cold-launched successfully on API35. Signed AAB: jarsigner reported jar verified (self-signed certificate/no timestamp and ZIP streaming-order warnings). Both are unconfigured previews with SHA-256 in artifacts/SHA256SUMS.txt.
- User selected source and setup instructions first; Firebase/Supabase projects do not yet exist.
- Ruling: retain a single-group transactional write lock for correctness under concurrent mutations; throughput ceiling documented in API.md.
- Ruling: signed builds without cloud credentials are installation/configuration previews, not a production release. Live-cloud acceptance and user study are pending, not passed.

## Independent review (whole branch)
Reviewer found no critical issues and four important issues; all four were accepted and fixed:
1. Editor must retain its original version. Removed automatic rebasing; saving against a newer cached version opens a comparison dialog with explicit use-new/keep-draft choices.
2. Rotation must retain the request identity. Identity now follows the complete outgoing payload (including priority), survives saved-instance restoration, and changes only when the payload changes or the user reconciles a conflict.
3. Recoverable UNAUTHORIZED clears visible cache but retains UID-isolated drafts for reauthentication. Revocation remains separate. Session observers are stopped before showing the authentication screen.
4. Worker claims one job immediately before sending, with concurrent device sends bounded by 10-second timeouts, then acknowledges before claiming another. The unclaimed tail does not consume attempts or lease time.
Regression checks: TaskEditSessionTest, OfflineDatabaseTest.expiredAuthenticationRetainsDraftUntilExplicitRevocation, database worker lease test. No minor findings were deferred. No second independent review was requested after these fixes.

Pending acceptance: cloud setup, real-device FCM, two-user workflow, complete configured-screen UI/large-font/Profiler checks, signed upgrade retention and student usability study. Source/build readiness does not imply those acceptance gates passed.
