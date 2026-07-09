# StuGram Production Readiness Freeze (Phase 0)

## Status
- Date: 2026-05-15
- Scope: Emergency Stabilization (Phase 0)
- Decision: Public beta is blocked. Reliability core work only.

## Branch Policy
- Required working branch: `production-readiness`
- Current blocker in this environment: `git` CLI is not available (`git_not_found`).
- Action required on developer machine with Git installed:
  1. `git checkout -b production-readiness`
  2. Push and protect this branch for reliability-only work.

## Feature Freeze (Effective Immediately)
The following work is frozen until Phase 0-6 gates are completed:
- New feed features
- New group features (except reliability/security fixes)
- New media features (except reliability/security fixes)
- Recommendation expansion work
- UI polish/animation/theme/layout changes
- Public beta rollout preparation

Allowed changes during freeze:
- Auth/session hardening
- Refresh-token reliability
- API contract unification
- Request correlation and observability
- Chat consistency (serverSequence/cursor/idempotency)
- Room transaction safety
- Socket reconnect/replay reliability
- Timeout detection and mitigation
- Kill switches, tests, runbooks

## Severity Backlog Categories

### P0 (must be zero before alpha progression)
- Data loss
- Message loss
- Message duplication
- Broken auth causing lockout
- Security/privacy leak
- Crash on core flow

### P1
- Auth refresh bug/storm
- Session restore bug
- Chat ordering bug
- Reconnect replay bug
- Timeout spike
- Socket instability
- API contract mismatch

### P2
- Performance issue
- UI inconsistency
- Media retry weakness
- Group reliability gap

### P3
- Cleanup/refactor
- Naming/docs
- Internal quality improvements

## Phase Order (Do Not Reorder)
1. Phase 0 — Emergency Stabilization
2. Phase 1 — Auth & Session Hardening
3. Phase 2 — API Contract Unification
4. Phase 3 — Chat Consistency Model
5. Phase 4 — Android Room-First Architecture
6. Phase 5 — Socket Reconnect & Replay Reliability
7. Phase 6 — Timeout Root Cause Elimination

## Release Gate Baseline (applies to every release candidate)
- Backend tests pass
- Android tests pass
- Contract tests pass
- Auth tests pass
- Chat consistency tests pass
- Rollback checklist complete
- Migration checklist complete
- Feature flag checklist complete

## Alpha Rollout Gates
- 20 users gate:
  - P0 = 0
  - auth bootstrap stable
  - single-flight refresh test passed
  - requestId end-to-end available
- 50 users gate:
  - confirmed message loss = 0
  - confirmed duplication = 0
  - replay/reconnect tests stable
- 100 users gate:
  - reconnect stability confirmed
  - Room consistency tests stable
  - no auth loop pattern
- 300 users gate:
  - observability dashboards active
  - incident runbook tested
  - unresolved P0/P1 explicitly accepted by owner

## Current Sprint-1 Ticket Mapping
- T1: production-readiness branch + freeze doc (completed, branch creation blocked by missing git CLI in this environment)
- T2: kill switches (implemented in backend runtime + documented in `docs/kill-switches.md`)
- T3: backend requestId middleware (implemented with correlation docs in `docs/request-correlation.md`)
- T4: Android request correlation logs
- T5-T14: continue in approved order from execution board

## Exit Criteria for Freeze
Freeze can be partially lifted only after:
- Phase 0-6 DoD reached
- No open P0 issues
- No confirmed message loss/duplication
- No refresh storm
- Timeout p95 within defined threshold
- Crash/ANR trend stable

## School Alpha Round 3 Status
- Profile alpha-visible path is backend-backed (`/api/v1/profiles/me`) with real empty content states.
- Messages alpha-visible path is backend-backed (`/api/v1/chats/conversations`) with real empty state.
- Requests are hidden for alpha until real Add-to-Chat/Block/Delete flow is fully wired and tested.
- Group chat entry is hidden from alpha messages path until reliable end-to-end flow is verified.
- Edit Profile remains hidden in alpha-visible path until backend-persistent update flow is completed and validated.

## School Alpha Round 4 Status
- Search alpha-visible path remains safe-disabled (no fake recommended/trending content shown).
- Camera/Create simulation path removed from alpha-visible behavior; create is disabled with explicit notice.
- Settings static dead catalog replaced with alpha-safe minimal state (logout + hidden unsafe settings).
- Build verification rerun completed for Android (:app:compileDebugKotlin, :app:assembleDebug).

## School Alpha Round 5 Status
- Edit Profile is now backend-persistent in alpha-visible flow via PATCH /api/v1/profiles/me.
- Added real requests backend API:
  - GET /api/v1/requests
  - POST /api/v1/requests/:requestId/add-to-chat
  - POST /api/v1/requests/:requestId/block
  - DELETE /api/v1/requests/:requestId
- Android Messages alpha flow now supports real request actions (Add to Chat / Block / Delete).
- Backend integration tests added:
  - tests/requests/requests.integration.test.js
  - tests/profile/profile.integration.test.js
  - verified passing.
- Android build verification rerun completed for Round 5 (:app:compileDebugKotlin, :app:assembleDebug).

## School Alpha Round 6 Status
- Feed alpha path hardened:
  - local-only like/comment/share interaction controls removed from active clickable path.
  - comments bottom-sheet demo path removed from alpha-visible flow.
- Reels alpha path disabled:
  - Home tab now routes to disabled state instead of fake reels dataset.
- Stories alpha path hardened:
  - feed story surface uses real empty state only (`No stories yet`).
- Remaining fake/demo branches under legacy Story/Reels/Search component files are documented as unreachable from alpha navigation and scheduled for cleanup after alpha stabilization.
- Backend regression tests rerun and passing:
  - tests/requests/requests.integration.test.js
  - tests/profile/profile.integration.test.js

## School Alpha Round 7 Status
- Android alpha safety flags centralized in `AlphaFeatureFlags`.
- Navigation guards now block accidental re-exposure of disabled alpha features:
  - reels route
  - group chat route
  - story viewer open path
  - camera/create open path
  - feed interaction mutation path
- Alpha visibility docs now include a guard matrix mapping `feature -> flag -> guard`.
- 20-user alpha launch gate checklist added in `docs/school-alpha-20-user-gate.md`.

## 100-User Alpha Round 9 Status
- Direct chat reliability hardening execution started and verified with automated evidence.
- Backend chat coverage includes request correlation envelope checks (success + error include `requestId`).
- Backend regression suite is green for auth/profile/requests/chat.
- Android chat local sync tests expanded for:
  - pending message reconcile by `clientId`
  - socket-first race dedupe
  - duplicate replay + stale sequence ignore
  - tombstone resurrection prevention
- `docs/chat-reliability-matrix.md` now tracks scenario-by-scenario pass/fail evidence.
- Manual 2-user reconnect/restart chat QA is still required before 100-user approval.
- 100-user expansion remains blocked pending Round 10-12 gates.

## Production Baseline Audit (Phase 0)
- Full cross-repo feature inventory and fake-path risk map documented in:
  - `D:\android app\docs\production-baseline-audit-round0.md`
- This baseline must be treated as the source of truth for production execution order:
  1. Quarantine/remove fake legacy runtime paths.
  2. Wire disabled social features to real backend flows with tests.
  3. Run long stability/load and reconnect evidence gates before any broader launch claim.

## Phase 1 Fake Legacy Quarantine Status (2026-05-16)
- Runtime fake branches removed/quarantined from highest-risk Android surfaces:
  - `SearchScreen.kt`
  - `ReelsComponents.kt`
  - `StoryComponents.kt`
  - `ProfileScreen.kt`
  - `MessagesScreen.kt`
- Quarantine manifest and decision matrix documented in:
  - `D:\android app\docs\fake-legacy-quarantine.md`
- Android verification after cleanup:
  - `:app:testDebugUnitTest` PASS
  - `:app:compileDebugKotlin` PASS
  - `:app:assembleDebug` PASS
- Backend regression command in this run failed due test infrastructure timeout:
  - `MongoMemoryServer` instance startup exceeded 10s (environment/runtime issue), not functional API regression evidence.

## Phase 3 Profile/Follow/Search Status (2026-05-16)
- Search is now runtime-wired to real backend endpoint:
  - `GET /api/v1/search/users`
- Other-user profile route added in Android nav:
  - `profile/{username}` -> backend `GET /api/v1/profiles/:username`
- Follow action wired in Android profile/search surfaces:
  - `POST /api/v1/follows/:userId`
  - `DELETE /api/v1/follows/:userId`
- Backend follow rule hardened:
  - blocked-user follow attempts are denied (`403`).
- New backend integration suite added:
  - `tests/follow/follow.integration.test.js`

## Phase 4 Progress Note (2026-05-16)
- Backend post/feed is now production-real for text + media payloads:
  - `POST /api/v1/posts` accepts text-only posts when caption is provided.
  - Empty post (no media + blank caption) is rejected with `POST_VALIDATION_FAILED`.
  - `GET /api/v1/posts/feed/me` and `GET /api/v1/posts/user/:username` remain backend-backed.
- New/updated backend coverage:
  - `tests/posts/posts.integration.test.js` (unauth create denied, text-only create success, empty create rejected)
  - `tests/feed/feed.integration.test.js` (feed pagination + real items)
- Android home feed now renders real backend posts including text-only posts.
- Android create post is now text-only real wired (no fake media flow exposed).
- Profile posts tab now reads real backend user posts (`GET /api/v1/posts/user/:username`).
- Like/comment/share remain non-interactive/passive until Phase 5 backend+UI interaction wiring.
