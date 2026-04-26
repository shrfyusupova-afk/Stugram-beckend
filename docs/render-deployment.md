# Render Deployment Guide

StuGram closed alpha runs as one Render service:

- `stugram-api`: web service, `npm start`

The root `render.yaml` defines the API service only for closed alpha. It intentionally marks secrets as `sync: false`; configure them in Render, not in Git. A paid recommendation worker can be added later, but it is not required for Phase 1.

## Required Environment

- `NODE_ENV=production`
- `HOST=0.0.0.0`
- `MONGODB_URI=mongodb+srv://...`
- `REDIS_URL=redis://...` or `rediss://...` if Redis is available
- `REDIS_REQUIRED=false` for closed alpha unless Redis must be a hard launch gate
- `QUEUE_ENABLED=false`
- `WORKER_REQUIRED=false`
- `RECOMMENDATION_WORKER_ENABLED=false`
- `RECOMMENDATION_MODE=db-direct`
- `CACHE_MODE=redis-optional`
- `ALLOW_MEMORY_DB_FALLBACK=false`
- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`
- `OTP_SECRET`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `ENABLE_BOOTSTRAP_USER=false`

## Atlas Network Access

Render free/shared outbound IPs should not be treated as stable. For first deploy debugging, `0.0.0.0/0` in Atlas Network Access is acceptable temporarily if the database user has a strong password and least-privilege access. Replace it with static egress/private networking when available.

## Verification

From `backend/`:

```bash
npm run smoke:production -- https://stugram-beckend.onrender.com
```

Expected:

- `livez` succeeds
- `health` succeeds
- `readyz` succeeds
- `push` succeeds
- `mongoMode` is `atlas`
- `redisMode` is `connected` if Redis is configured, or Redis is explicitly optional
- `recommendationMode` is `db-direct`
- `queueHealth.queue.enabled` is `false`
- `queueHealth.queue.mode` is `disabled-for-closed-alpha`
- `workerRequired` is `false`
- `cacheMode` is `enabled` when Redis is connected, or `redis-optional-unavailable` when Redis is intentionally optional

## Deploy Checklist

- Deploy API.
- Confirm API logs reach `Startup phase: after_http_listen`.
- Confirm API logs show `recommendationMode: db-direct`, `queueEnabled: false`, and `workerRequired: false`.
- Run production smoke check.
- Install Android build and verify auth/profile/post/story/chat against the Render URL.
