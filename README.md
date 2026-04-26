# Stugram Backend

## Production deployment checklist

Required Render/environment variables:

- `NODE_ENV=production`
- `MONGODB_URI`
- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`
- `OTP_SECRET`
- `OTP_PROVIDER=sms` or `OTP_PROVIDER=email`
- SMS provider variables when `OTP_PROVIDER=sms`:
  - Twilio: `SMS_PROVIDER=twilio`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_PHONE`
  - Brevo: `SMS_PROVIDER=brevo`, `BREVO_API_KEY`, `BREVO_SMS_SENDER`
- Email provider variables when `OTP_PROVIDER=email` or password reset email is enabled:
  - Resend: `EMAIL_PROVIDER=resend`, `RESEND_API_KEY`, `RESEND_FROM_EMAIL`
  - Brevo: `EMAIL_PROVIDER=brevo`, `BREVO_API_KEY`, `BREVO_FROM_EMAIL`, `BREVO_FROM_NAME`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- Firebase Admin credentials via `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, or service-account config

Production must not use:

- `OTP_PROVIDER=mock`
- `ALLOW_MEMORY_DB_FALLBACK=true`
- `ENABLE_BOOTSTRAP_USER=true`

Recommended rate-limit values:

- `RATE_LIMIT_WINDOW_MS=900000`
- `RATE_LIMIT_MAX=100`
- `AUTHENTICATED_RATE_LIMIT_MAX=1000`

Before deploying chat changes, verify MongoDB idempotency indexes:

```bash
npm run verify:chat-indexes
```

If the verifier reports missing indexes, apply the safe idempotent migration:

```bash
npm run migrate:chat-indexes
npm run verify:chat-indexes
```

After redeploy:

1. Confirm authenticated users can load profiles, search, and open chats without premature `429`.
2. Confirm unauthenticated request bursts still receive JSON `429` responses.
3. Confirm `/readyz` is healthy and no production mock providers are active.
