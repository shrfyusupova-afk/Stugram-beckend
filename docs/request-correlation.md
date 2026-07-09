# Request Correlation (Ticket 3)

## Purpose
Request correlation provides one stable ID per API request to trace failures across middleware, controllers, and logs.

## Header
- Incoming: `x-request-id` (optional)
- Outgoing: `x-request-id` (always present)

## Rules
- If client sends valid `x-request-id`, backend preserves it.
- If missing/invalid, backend generates a safe ID.
- Max incoming length: 128.
- Allowed chars: letters, numbers, `_`, `-`, `.`, `:`.
- Invalid values are replaced, not rejected.

## Response Contract
All JSON responses include:

```json
"meta": {
  "requestId": "req_...",
  "timestamp": "2026-05-15T20:00:00.000Z"
}
```

Error responses include stable `error.code`.

## Logging Fields
- `requestId`
- `method`
- `path`
- `statusCode`
- `durationMs`
- `userId` (if available)
- `errorCode` (if failed)
- `result` (`success` or `error`)

## Security Rules
Never log:
- access tokens
- refresh tokens
- authorization header
- passwords
- OTP codes
- cookies
- raw secret env values

## Android Integration Note
Android should send `x-request-id` per operation (`auth`, `chat send`, `replay`, `media`) so device logs can correlate directly with backend logs.
