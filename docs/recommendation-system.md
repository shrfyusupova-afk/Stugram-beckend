# Recommendation System Architecture

## Goal

Build a production-style, Instagram-like recommendation layer on top of the existing Node.js, Express, MongoDB, Redis backend. The system should personalize feed, reels, and people suggestions while staying stable, explainable, abuse-resistant, and operationally simple enough to ship in phases.

## Architecture

### Request path

1. Client fetches `GET /feed/me`, `GET /reels/me`, or `GET /profiles/suggestions`.
2. Recommendation controller calls a ranking service.
3. Ranking service checks Redis ranked-page cache first.
4. On cache miss, it pulls candidate pools from MongoDB:
   - follow graph candidates
   - topical candidates
   - trending candidates
   - fresh discovery candidates
5. Candidate pool is filtered by:
   - blocks
   - reports and quality penalties
   - hidden/not-interested content
   - already-followed profiles for people suggestions
6. Candidates are reranked with a weighted score.
7. A snapshot is stored for audit/debug and the page is cached in Redis.
8. Impression and interaction events update:
   - `RecommendationEvent`
   - `ContentWatchMetric`
   - `UserInterestProfile`
   - `CreatorAffinity`
   - cache invalidation for the acting user

### Components

- `recommendationService`
  Generates feed and reels candidate pools, scores them, injects exploration, persists snapshots.
- `profileSuggestionService`
  Builds graph-based profile suggestions with mutual follow, interest similarity, profile-visit intent, and creator similarity.
- `interactionTrackingService`
  Ingests app events, updates watch metrics and user preference state, invalidates caches, writes audit records.
- `recommendationCacheService`
  Stores per-user ranked pages in Redis with short TTL and user-scoped invalidation.
- `recommendationScoringService`
  Holds weighted formulas in one place so tuning is safe and auditable.

## MongoDB Collections

### `RecommendationEvent`

Append-only interaction log.

Tracks:
- `feed_impression`
- `reel_impression`
- `watch_start`
- `watch_progress`
- `watch_complete`
- `like`
- `save`
- `share`
- `comment`
- `profile_visit`
- `follow`
- `caption_open`
- `profile_open`
- `sound_on`
- `hide`
- `not_interested`
- `block`
- `report`
- `fast_skip`
- `rewatch`

Purpose:
- learning signals
- abuse investigation
- offline model training later
- impression-to-action attribution

### `ContentFeature`

Per-post feature record.

Stores:
- `contentType`: `post | reel`
- `topics`
- `topicWeights`
- `qualityScore`
- `spamScore`
- `popularityScore`
- region/language metadata

This decouples recommendation features from the core `Post` document.

### `UserInterestProfile`

User topic vector.

Stores:
- `interestScores`
- `negativeTopicScores`
- `onboardingTopics`
- `explorationRate`
- `confidenceScore`
- `localePreferences`

This is the main personalization state for topic preference learning.

### `CreatorAffinity`

User-to-creator affinity edge.

Stores:
- `score`
- `watchScore`
- `engagementScore`
- `visitScore`
- `followScore`
- `negativeScore`
- `lastInteractionAt`

Used to boost creators the user repeatedly finishes, rewatches, visits, or follows.

### `UserContentPreference`

Explicit feedback store.

Stores:
- `hide`
- `not_interested`
- optionally `seen`, `saved`, `liked`

Used for hard filtering and persistent suppression.

### `ContentWatchMetric`

Aggregated per-user per-post watch stats.

Stores:
- impressions
- watch starts
- total watch ms
- max watch ratio
- completions
- rewatches
- fast skips

### `ProfileSuggestionFeature`

Materialized feature row for profile candidates.

Stores:
- mutual follow score
- interest similarity score
- audience overlap score
- visit intent score
- creator similarity score
- locality score
- popularity score
- quality score
- final score

### `RecommendationSnapshot`

Short-lived ranked result snapshot for debugging and auditability.

Stores:
- user
- surface
- page
- candidates with scores and reasons
- expiration time

### `CreatorQuality`

Trust-and-safety quality layer.

Stores:
- quality score
- spam penalty
- abuse penalty
- suspicious engagement penalty
- report rate
- hide rate

## Event Tracking Design

### Event naming convention

Use noun-verb style with explicit surface metadata:

- `feed_impression`
- `reel_impression`
- `watch_progress`
- `watch_complete`
- `profile_visit`
- `not_interested`

Each event should include:
- `user`
- `surface`
- `contentId` or `targetProfileId`
- `creatorId`
- `sessionId`
- `requestId`
- `sourceImpressionId`
- watch metrics when relevant
- inferred or explicit topics

### Where tracking should live

- API endpoints:
  - `POST /interactions/impression`
  - `POST /interactions/watch-progress`
  - `POST /interactions/not-interested`
- Service-level hooks:
  - call `recordEvent(...)` inside like/save/share/comment/follow/profile visit flows
- Socket/async path:
  - optional future enqueue to Redis stream or job queue for heavier fanout work

### Production recommendation

Keep ingestion synchronous for Phase 1 and 2.
Move expensive recomputation to queue workers in Phase 3+:
- topic refresh
- quality recomputation
- candidate precompute
- embedding refresh

## Feed / Reels Ranking Formula

### Weighted score

```text
feed_score =
  0.22 * watch_time_score
  + 0.23 * strong_engagement_score
  + 0.18 * topic_match_score
  + 0.14 * creator_affinity_score
  + 0.08 * freshness_score
  + 0.07 * popularity_score
  + 0.06 * quality_score
  + 0.04 * diversity_score
  + 0.03 * exploration_boost
  - 0.28 * negative_penalty
```

### Why these weights exist

- `watch_time_score`
  Retention is the strongest implicit signal for reels/feed satisfaction.
- `strong_engagement_score`
  Likes, saves, shares, comments, follow-after-view, and profile-visit-after-view show explicit intent.
- `topic_match_score`
  Prevents recommendations from feeling random and builds topical consistency.
- `creator_affinity_score`
  Repeated creator engagement is a strong predictor of future interest.
- `freshness_score`
  Keeps the surface alive and responsive to recent content.
- `popularity_score`
  Captures social proof without letting viral content fully dominate.
- `quality_score`
  Penalizes low-trust or spammy creators/content.
- `diversity_score`
  Avoids repeating the same creator or same topic endlessly.
- `exploration_boost`
  Forces controlled discovery of new creators/topics.
- `negative_penalty`
  Hard correction for skips, hides, reports, and blocks.

### Signal mapping

Strong positives:
- like
- save
- share
- comment
- follow after viewing
- profile visit after viewing
- full watch
- rewatch

Medium positives:
- 50%+ watch
- caption open
- profile open
- sound on
- long dwell time

Negatives:
- fast skip
- hide
- not interested
- report
- block
- low dwell time

### Exploration policy

- Reserve about `15%` of ranked slots for exploration.
- Exploration candidates should come from:
  - adjacent topics
  - quality creators outside follow graph
  - trending reels not yet seen by the user
- Do not explore from creators with poor quality scores.

## Suggested Profiles Formula

```text
profile_score =
  0.24 * mutual_follow_score
  + 0.20 * interest_similarity_score
  + 0.14 * audience_overlap_score
  + 0.12 * visit_intent_score
  + 0.10 * creator_similarity_score
  + 0.06 * locality_score
  + 0.08 * popularity_score
  + 0.06 * quality_score
```

### Feature meaning

- `mutual_follow_score`
  Follow graph proof that the suggestion is socially relevant.
- `interest_similarity_score`
  Topic overlap between the user and candidate profile.
- `audience_overlap_score`
  Same audience or same follower neighborhood.
- `visit_intent_score`
  Profiles the user repeatedly opens or visits are high-intent suggestions.
- `creator_similarity_score`
  If the user prefers founder/startup creators, suggest similar profiles.
- `locality_score`
  Optional regional relevance for cold start or local discovery.
- `popularity_score`
  Prefer credible accounts with healthy demand.
- `quality_score`
  Demote accounts with reports, hides, or suspicious spikes.

## Cold Start Strategy

For new users with weak history:

1. Seed `UserInterestProfile.onboardingTopics`.
2. Mix:
   - trending content
   - fresh high-quality creators
   - local/regional content if available
   - controlled exploration
3. Default exploration slightly higher until confidence grows.
4. Drop exploration rate as:
   - watch history grows
   - topic confidence grows
   - creator affinity graph becomes denser

## Anti-Abuse / Quality Layer

### Protection rules

- fake engagement detection
  Detect creators whose strong engagement rate is unrealistically high relative to impressions.
- creator quality scoring
  Penalize based on report rate, hide rate, suspicious engagement patterns.
- spam penalties
  Repeated hides/not-interested events lower creator and content quality.
- report/block penalties
  Apply strong penalties to recommendation eligibility.
- suspicious engagement pattern penalties
  Use impression-to-like/save/share ratios and burst timing windows.

### Hard filters

- blocked users should never appear
- reported/hidden content should be suppressed immediately for the acting user
- suspended users should not appear in suggestions

## Redis / Performance Design

### What to cache

- ranked feed pages per user
- ranked reels pages per user
- profile suggestion pages per user

### TTL

- feed/reels: `90s`
- profiles: `180s`

### Invalidation

Invalidate user-scoped keys on:
- like/save/share/comment/follow
- watch complete / significant watch progress
- hide/not interested
- onboarding topic update

### Precomputation vs realtime

- realtime:
  - final rerank
  - hard filtering
  - cache lookup
- precompute later:
  - candidate pools
  - creator quality
  - graph features
  - embeddings

### Pagination

- keep stable page-size capping at 50
- oversample candidates then rerank
- snapshot the served page for debugging
- prefer cursor pagination in later phases if feed insertion churn grows

## API Endpoints

### `GET /feed/me?page=1&limit=20`

Example response:

```json
{
  "success": true,
  "message": "Feed fetched successfully",
  "data": [
    {
      "_id": "6616e8d2f1b7c11b0d3bc101",
      "caption": "How we validated our SaaS idea",
      "hashtags": ["startup", "saas", "founder"],
      "recommendation": {
        "score": 0.781233,
        "reasons": ["topic_match", "watch_retention", "creator_affinity"],
        "exploration": false
      }
    }
  ],
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 120,
    "totalPages": 6,
    "strategy": "weighted-ranking-v1",
    "explorationRatio": 0.15
  }
}
```

### `GET /reels/me?page=1&limit=20`

Same envelope, but only video candidates and shorter freshness half-life.

### `GET /profiles/suggestions?page=1&limit=20`

```json
{
  "success": true,
  "message": "Profile suggestions fetched successfully",
  "data": [
    {
      "_id": "6616e8d2f1b7c11b0d3bc202",
      "username": "founderjam",
      "fullName": "Founder Jam",
      "recommendation": {
        "score": 0.712451,
        "reasons": ["mutual_follows", "interest_similarity", "profile_visit_intent"]
      }
    }
  ],
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 48,
    "totalPages": 3,
    "strategy": "people-you-may-follow-v1"
  }
}
```

### `POST /interactions/impression`

```json
{
  "contentId": "6616e8d2f1b7c11b0d3bc101",
  "surface": "reels",
  "sessionId": "sess_abc123",
  "requestId": "req_abc123",
  "sourceImpressionId": "imp_abc123"
}
```

### `POST /interactions/watch-progress`

```json
{
  "contentId": "6616e8d2f1b7c11b0d3bc101",
  "surface": "reels",
  "watchedMs": 18500,
  "totalDurationMs": 20000,
  "dwellMs": 19000,
  "soundOn": true,
  "rewatchCount": 1,
  "sourceImpressionId": "imp_abc123"
}
```

### `POST /interactions/not-interested`

```json
{
  "contentId": "6616e8d2f1b7c11b0d3bc101",
  "surface": "reels",
  "reason": "too_repetitive",
  "sourceImpressionId": "imp_abc123"
}
```

## Recommended Folder Structure

```text
backend/src/
  controllers/
    recommendationController.js
  models/
    RecommendationEvent.js
    ContentFeature.js
    UserInterestProfile.js
    CreatorAffinity.js
    UserContentPreference.js
    ContentWatchMetric.js
    ProfileSuggestionFeature.js
    RecommendationSnapshot.js
    CreatorQuality.js
  routes/
    recommendationRoutes.js
  services/
    interactionTrackingService.js
    recommendationCacheService.js
    recommendationScoringService.js
    recommendationService.js
    profileSuggestionService.js
  validators/
    recommendationValidators.js
backend/docs/
  recommendation-system.md
```

## Product Behavior Examples

- If a user repeatedly likes and completes startup/business reels, the topic vector and creator affinity both increase, so more startup/business reels rise in rank.
- If a user fast-skips comedy reels, negative topic scores increase and future comedy candidates lose rank.
- If a user keeps visiting founder profiles, visit intent and creator similarity increase, so founder/entrepreneur suggestions rise.
- If a user fully watches one creator and then rewatches similar creators, creator affinity and topic similarity raise those creators together.
- Suggestions feel useful because they combine social graph, behavior graph, and content similarity rather than random popularity alone.

## Phased Rollout Plan

### Phase 1

Simple weighted scoring MVP.

- event ingestion endpoints
- hidden/not-interested support
- follow graph + trending + freshness candidates
- weighted ranking
- Redis page cache
- snapshots and auditability

### Phase 2

Interest vector + creator affinity.

- `UserInterestProfile`
- `CreatorAffinity`
- update vectors on every strong/negative interaction
- profile visits and follow-after-view become meaningful features

### Phase 3

Candidate generation + reranking + exploration.

- precomputed candidate pools
- better diversity constraints
- dynamic exploration budget
- offline jobs for popular topic pools

### Phase 4

Anti-abuse quality scoring.

- creator quality materialization
- suspicious engagement heuristics
- stronger report/block/hide penalties
- eligibility rules for exploration

### Phase 5

Optional advanced ranking.

- embeddings
- collaborative filtering
- graph ranking
- ANN retrieval
- session-aware reranking
- feature store or Redis streams / queues

## Integration Notes

- Hook `recordEvent(...)` into like/save/share/comment/follow/profile visit flows for richer learning.
- If throughput grows, move heavy updates behind Redis streams or BullMQ workers.
- Keep formulas versioned; expose version in `meta.strategy`.
- Add internal dashboards for:
  - completion rate
  - hide/not-interested rate
  - profile suggestion follow-through
  - exploration success rate
  - creator quality drift
