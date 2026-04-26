# StuGram Closed-Alpha Runbook

This runbook turns the current codebase into a small real-user launch. It is scoped for 20-50 invited users, one backend instance, no paid background worker, managed Atlas, optional/managed Redis, Cloudinary, Firebase, and the Android app pointed at Render.

## Closed-Alpha Runtime Mode

Render API env must explicitly select the no-worker mode:

```env
QUEUE_ENABLED=false
WORKER_REQUIRED=false
RECOMMENDATION_WORKER_ENABLED=false
RECOMMENDATION_MODE=db-direct
CACHE_MODE=redis-optional
```

In this mode:

- Atlas is the source of truth.
- Feed and reels use direct MongoDB reads.
- Posts, stories, and reels appear immediately after DB persistence.
- Recommendation refresh jobs are not enqueued.
- Queue health must report `mode: disabled-for-closed-alpha`.
- `/readyz` must not require a worker.
- Redis may be connected for rate limits/token/cache support, but a paid BullMQ worker is not required.

## Launch Gate

Do not invite users until all items below are true.

- Render API service reaches `Startup phase: after_http_listen`.
- `npm run smoke:production -- https://stugram-beckend.onrender.com` passes from `backend/`.
- `/health` reports `mongoMode: atlas`, `recommendationMode: db-direct`, `queueHealth.queue.mode: disabled-for-closed-alpha`, `workerRequired: false`, `pushEnabled: true`, and `cloudinaryConfigured: true`.
- If `REDIS_REQUIRED=true`, `/health` must also report `redisMode: connected`.
- Android debug/internal build is installed on a physical phone and uses `https://stugram-beckend.onrender.com/api/v1/`.
- Register, login, profile refetch, image post, story, direct chat, logout/login, and push token registration are verified on device.
- Atlas backups are enabled and one restore drill has been documented.
- At least one admin/moderator account can list reports, hide/delete posts, ban/unban users, and view system health.

## Daily Alpha Checks

- Check Render API logs for startup failures, `Startup fatal error`, Mongo disconnects, Redis disconnects, unexpected queue enablement, and 5xx bursts.
- Run the production smoke check once per day and after every deploy.
- Review reports and support tickets daily.
- Check Cloudinary usage and failed upload complaints.
- Check Firebase push delivery manually with one backgrounded device.
- Track the alpha notes table: failed login, failed upload, failed message, empty feed, crash, report, and support issue.

## Incident Rules

- If `/readyz` fails, stop inviting users and inspect Mongo/Redis first.
- If health shows `queue.enabled=true` or `workerRequired=true`, fix Render env back to closed-alpha no-worker mode before inviting users.
- If Atlas fails with TLS/ReplicaSetNoPrimary on Render, temporarily allow `0.0.0.0/0` in Atlas Network Access to confirm IP allowlist as the cause, then move back to a safer IP/private networking option.
- If uploads fail, verify Cloudinary env values and usage limits before changing the Android upload flow.
- If chat sends fail with `Forbidden`, confirm follow/block/private-account state before treating it as a transport bug.

## Metrics To Record Manually

- Invited users
- Registered users
- Daily active users
- Posts created
- Stories created
- Reels/video posts created
- Direct messages sent
- Failed uploads
- Failed auth attempts
- Empty feed reports
- Crash reports
- User reports/moderation actions

## What Not To Build During Alpha

- Kubernetes or self-hosted infrastructure
- ML recommendations
- Multi-region deployments
- Automated moderation AI
- Complex analytics warehouse
- Multi-instance socket scaling unless one API instance becomes a real bottleneck
