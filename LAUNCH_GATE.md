# StuGram Launch Gate

## Closed Beta
- Privacy, block, membership, replay, reliability, and rate-limit integration suites must pass.
- `verify:chat-indexes` must pass.
- `/health`, `/readyz`, `/health/chat-observability`, and `/metrics/chat` must be healthy.
- `load:smoke` must complete without 5xx spikes or abnormal 429s for authenticated normal usage.
- `chat_pending_oldest_age_ms` must stay below 10 minutes.
- Kill switches must remain documented and defaulted intentionally.

## Controlled Beta
- Closed beta gates pass.
- Chat p95 send latency under smoke/load validation stays below 1500ms.
- Replay request p95 stays below 1200ms.
- Terminal send failure rate stays below 1%.
- Replay sync failure rate stays below 1%.

## Wider Beta
- Controlled beta gates pass for multiple release candidates.
- Chat p95 send latency stays below 1000ms.
- Replay request p95 stays below 900ms.
- Terminal send failure rate stays below 0.5%.
- Replay sync failure rate stays below 0.5%.
- No privacy/access regressions across profile, follow, search, direct chat, and group replay routes.

## Public Launch
- Wider beta metrics stay stable for at least 7 days.
- No launch-blocking privacy, rate-limit, replay, or message-loss incidents remain open.
- Rollback path is confirmed:
  1. Enable `CHAT_RATE_LIMIT_STRICT_MODE` if abuse spikes.
  2. Disable `CHAT_REALTIME_ENABLED` if realtime instability spikes.
  3. Disable `CHAT_REPLAY_SYNC_ENABLED` only with incident acknowledgement and fallback monitoring.
  4. Disable `CHAT_MEDIA_SEND_ENABLED` if upload failures spike.
  5. Disable `CHAT_GROUP_SEND_ENABLED` if group abuse or instability spikes.
