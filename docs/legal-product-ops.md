# Legal And Product Ops Minimums

This is not legal advice. It is the minimum product-ops checklist needed before putting real users into StuGram.

## Must Exist Before Closed Alpha

- Privacy policy page or document
- Terms/community rules page or document
- Support contact email or form
- Data deletion request process
- Report abuse flow tested in app and backend
- Admin/moderator account able to act on reports

## Minimum Community Rules

- No harassment or threats
- No impersonation
- No sexual content involving minors
- No spam or scam behavior
- No posting private information without consent
- Reported content can be hidden or removed
- Banned users can lose access without notice for abuse

## Data Deletion Process

Until a fully automated in-app delete-account flow is implemented, use this manual process:

1. User contacts support from the registered identity.
2. Admin verifies identity ownership.
3. Admin exports the request ID and target account ID into the deletion log.
4. Admin disables or deletes the account according to the current backend capability.
5. Admin confirms completion to the user.

## Moderation SLA For Alpha

- Review reports daily.
- Remove clearly abusive content the same day.
- Ban spam or harassment accounts immediately.
- Keep audit notes for every ban/delete decision.

## Do Not Launch Public Beta Until

- Privacy policy and terms are public.
- Data deletion is documented and repeatable.
- Moderation actions are auditable.
- Support requests have an owner and response expectation.
