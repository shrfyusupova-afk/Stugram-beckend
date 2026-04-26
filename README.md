# StuGram

StuGram is a Jetpack Compose Android social app with a Node.js/Express backend. The current product path is **closed alpha first**, then managed-services MVP.

## Current Production Shape

- Android app points at `https://stugram-beckend.onrender.com/api/v1/`.
- Backend uses MongoDB Atlas as the primary datastore.
- Redis is optional for closed alpha support paths; require it only when deliberately enabling Redis-backed behavior.
- Recommendation refresh uses direct DB mode for closed alpha; a paid background worker is not required.
- Cloudinary handles media storage.
- Firebase Admin handles push delivery.

## Required Services

- Render web service: `npm start`
- MongoDB Atlas
- Managed Redis, optional for closed alpha
- Cloudinary
- Firebase

## Launch Docs

- [Closed-alpha runbook](docs/closed-alpha-runbook.md)
- [Render deployment guide](docs/render-deployment.md)
- [Backup and restore drill](docs/backup-restore.md)
- [Legal and product ops minimums](docs/legal-product-ops.md)

## Backend Commands

```bash
cd backend
npm ci
npm start
npm run smoke:production -- https://stugram-beckend.onrender.com
```

## Android Commands

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Closed-Alpha Gate

Do not invite real users until:

- API reaches `after_http_listen`.
- Production smoke check passes.
- Android physical-device test passes for auth, profile, post, story, chat, and push token registration.
- Atlas backups are enabled and one restore drill is documented.
- Admin/moderation actions are verified.
