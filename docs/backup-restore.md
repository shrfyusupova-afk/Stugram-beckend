# Backup And Restore Drill

StuGram must not enter closed alpha without a tested restore path. Atlas backups are the source of truth for MongoDB recovery.

## Minimum Backup Policy

- Enable Atlas backups for the production cluster.
- Keep at least 7 daily restore points during alpha.
- Do not rely on local exports as the primary backup.
- Do not run production with `memory-fallback`.

## Restore Drill

Run this once before inviting alpha users and after major schema changes.

1. Create a test restore from the latest Atlas backup into a separate temporary cluster or database.
2. Point a temporary backend environment at the restored database.
3. Start the backend and confirm `/readyz` succeeds.
4. Verify one known user, profile, post, story, conversation, and settings document exists.
5. Delete the temporary restore environment after verification.

## Recovery Notes To Record

- Date/time of drill
- Backup timestamp restored
- Restore target
- Backend commit used
- Verification result
- Any missing collections or index warnings

## Launch Blockers

- No Atlas backup enabled
- Restore never tested
- Production env has `ALLOW_MEMORY_DB_FALLBACK=true`
- Backend health reports `mongoMode` other than `atlas`
