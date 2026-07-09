# StuGram Kill Switches (Phase 0 / Ticket 2)

## Purpose
Kill switches allow high-risk runtime behavior to be disabled without changing Android UI and without code rollback.

## Environment Flags

### `CHAT_REALTIME_ENABLED` (default: `true`)
- `false`: backend stops emitting live chat socket events.
- REST chat APIs continue to work.
- Replay behavior still follows `CHAT_REPLAY_ENABLED`/`CHAT_REPLAY_SYNC_ENABLED`.

### `CHAT_GROUP_SEND_ENABLED` (default: `true`)
- `false`: blocks group send and group forward message mutations.
- Read/list/group detail endpoints remain available.
- Response code: `FEATURE_GROUP_SEND_DISABLED`.

### `CHAT_MEDIA_SEND_ENABLED` (default: `true`)
- `false`: blocks media message send attempts.
- Non-media text send remains available.
- Response code: `FEATURE_MEDIA_SEND_DISABLED`.

### `RECOMMENDATION_MODE` (`disabled` | `db-direct` | `full`)
- `disabled`: recommendation endpoints return disabled envelope and skip recommendation processing.
- `db-direct`: recommendation endpoints use low-cost DB-direct mode.
- `full`: alias of weighted-cache mode (internally normalized for compatibility).

### `CHAT_REPLAY_ENABLED` / `CHAT_REPLAY_SYNC_ENABLED` (default: `true`)
- `CHAT_REPLAY_ENABLED` is accepted as alias.
- `false`: replay endpoints are blocked.
- Response code: `FEATURE_CHAT_REPLAY_DISABLED`.

### `SOCKET_JOIN_CONVERSATION_ENABLED` (default: `true`)
- `false`: socket `conversation:join` and `group_chat:join` are rejected safely.
- User socket connection remains active.
- Ack code: `FEATURE_SOCKET_JOIN_CONVERSATION_DISABLED`.

## Disabled Feature Envelope
```json
{
  "success": false,
  "message": "This feature is temporarily disabled.",
  "data": null,
  "meta": {
    "requestId": "req_...",
    "timestamp": "2026-05-15T20:00:00.000Z"
  },
  "error": {
    "code": "FEATURE_*",
    "details": {
      "feature": "feature_name"
    }
  }
}
```

## Where Implemented
- `src/config/featureFlags.js`
- `src/middlewares/featureGate.js`
- `src/middlewares/chatSecurity.js`
- `src/middlewares/recommendationSecurity.js`
- `src/routes/recommendationRoutes.js`
- `src/socket/chatSocket.js`

## Operational Notes
- Keep kill switches `true` in normal operation.
- For incident mitigation, disable one subsystem at a time and monitor errors/latency.
- No secrets/tokens are included in disabled responses.
