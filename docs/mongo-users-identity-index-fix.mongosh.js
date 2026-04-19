/**
 * Fix users.identity index mismatch (unique+sparse).
 *
 * Goal:
 * - migrate existing index on users.identity from:
 *     unique: true, sparse: false
 *   to:
 *     unique: true, sparse: true
 *
 * Safety:
 * - does NOT delete any documents
 * - only drops/recreates the single users.identity index
 *
 * Usage (mongosh):
 *   mongosh "mongodb://USER:PASS@HOST:PORT/DBNAME" --file backend/docs/mongo-users-identity-index-fix.mongosh.js
 *
 * Or inside mongosh:
 *   load("backend/docs/mongo-users-identity-index-fix.mongosh.js")
 */

print("\n--- users.identity index audit ---");
print("DB:", db.getName());

const idx = db.users.getIndexes();
print("users indexes:", JSON.stringify(idx, null, 2));

const identityIndexes = idx.filter((i) => JSON.stringify(i.key) === JSON.stringify({ identity: 1 }));
print("identity indexes:", JSON.stringify(identityIndexes, null, 2));

print("\n--- data safety audit (identity duplicates / empties) ---");

const dupNonEmpty = db.users
  .aggregate([
    { $match: { identity: { $type: "string", $ne: "" } } },
    { $group: { _id: "$identity", count: { $sum: 1 }, ids: { $push: "$_id" } } },
    { $match: { count: { $gt: 1 } } },
    { $limit: 50 },
  ])
  .toArray();

print("duplicate non-empty identities (showing up to 50):", JSON.stringify(dupNonEmpty, null, 2));

const countMissing = db.users.countDocuments({ identity: { $exists: false } });
const countNull = db.users.countDocuments({ identity: null });
const countEmpty = db.users.countDocuments({ identity: "" });
print("identity missing:", countMissing);
print("identity null:", countNull);
print("identity empty string:", countEmpty);

const types = db.users
  .aggregate([{ $group: { _id: { $type: "$identity" }, count: { $sum: 1 } } }, { $sort: { count: -1 } }])
  .toArray();
print("identity types:", JSON.stringify(types, null, 2));

if (dupNonEmpty.length > 0) {
  print("\nABORT: duplicate non-empty identities exist. Resolve duplicates before creating a unique index.");
  quit(2);
}

if (countEmpty > 1) {
  print(
    "\nWARN: multiple users have identity == \"\". Empty string is indexed and will break unique index builds. Consider unsetting identity for empty-string docs before continuing."
  );
  // Not aborting automatically: operator decides if "" is valid in this system.
}

print("\n--- index migration plan ---");

const identityIndex = identityIndexes[0] || null;
if (!identityIndex) {
  print("No {identity:1} index found. Will create unique+sparse index.");
} else {
  print("Existing identity index name:", identityIndex.name);
  print("Existing unique:", Boolean(identityIndex.unique));
  print("Existing sparse:", Boolean(identityIndex.sparse));
}

print("\n--- applying changes ---");

// Drop existing identity index (if present)
if (identityIndex && identityIndex.name) {
  print("Dropping index:", identityIndex.name);
  db.users.dropIndex(identityIndex.name);
}

print("Creating index: { identity: 1 } unique:true sparse:true (name: identity_1)");
db.users.createIndex({ identity: 1 }, { name: "identity_1", unique: true, sparse: true });

print("\n--- verification ---");
const after = db.users.getIndexes().filter((i) => i.name === "identity_1" || JSON.stringify(i.key) === JSON.stringify({ identity: 1 }));
print("identity index after:", JSON.stringify(after, null, 2));

print("\nDONE");

