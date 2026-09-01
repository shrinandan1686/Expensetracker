#!/usr/bin/env node
/**
 * Creates the MongoDB indexes the Worker's queries rely on.
 *
 * Run once against each environment, and again after adding a query that filters
 * or sorts on something new:
 *
 *   MONGODB_URI='mongodb+srv://…' node scripts/setup-indexes.mjs
 *
 * Index creation is idempotent — re-running is safe and does nothing if an index
 * with the same key pattern and name already exists.
 *
 * Why this is a script and not startup code: a Worker isolate handles requests
 * concurrently and is recycled constantly, so ensuring indexes on each cold start
 * would add latency to real requests and hammer the free-tier connection limit for
 * no benefit.
 */

import { MongoClient } from 'mongodb';

const uri = process.env.MONGODB_URI;
const dbName = process.env.MONGODB_DATABASE ?? 'trackit';

if (!uri) {
  console.error('MONGODB_URI is not set.');
  console.error("Usage: MONGODB_URI='mongodb+srv://…' node scripts/setup-indexes.mjs");
  process.exit(1);
}

/**
 * Each entry mirrors a filter/sort the Worker actually issues. `unique` is used
 * where duplicate documents would be a correctness bug, not just a slow query.
 */
const INDEXES = {
  users: [
    // _id is the Firebase uid and is indexed by default; nothing else to add.
  ],
  expenses: [
    {
      // The sync upsert filters on { id, userId }. Without a unique index two
      // concurrent syncs of the same expense can each miss the other's insert and
      // create duplicate rows.
      key: { userId: 1, id: 1 },
      options: { name: 'userId_id_unique', unique: true },
    },
    {
      // GET /api/expenses default listing: filter userId, sort transactionAt desc.
      key: { userId: 1, transactionAt: -1 },
      options: { name: 'userId_transactionAt' },
    },
    {
      // The sync pull: filter userId + updatedAt > watermark, sort updatedAt asc.
      key: { userId: 1, updatedAt: 1 },
      options: { name: 'userId_updatedAt' },
    },
    {
      // GET /api/expenses?month= and the analytics $match on { userId, month }.
      key: { userId: 1, month: 1 },
      options: { name: 'userId_month' },
    },
  ],
  budgets: [
    {
      // POST /api/budgets upserts on { userId, month }; a duplicate would mean two
      // competing budgets for the same month.
      key: { userId: 1, month: 1 },
      options: { name: 'userId_month_unique', unique: true },
    },
  ],
  groups: [
    {
      // Every group route resolves membership via { 'members.userId': uid }, and
      // GET /api/groups sorts by createdAt.
      key: { 'members.userId': 1, createdAt: -1 },
      options: { name: 'membersUserId_createdAt' },
    },
  ],
  splits: [
    {
      key: { groupId: 1, createdAt: -1 },
      options: { name: 'groupId_createdAt' },
    },
  ],
  settlements: [
    {
      // GET /api/groups/:id/balances filters { groupId, status: 'confirmed' }.
      key: { groupId: 1, status: 1 },
      options: { name: 'groupId_status' },
    },
  ],
};

const client = new MongoClient(uri);

try {
  await client.connect();
  const db = client.db(dbName);
  console.log(`Connected to "${dbName}".\n`);

  for (const [collection, specs] of Object.entries(INDEXES)) {
    if (specs.length === 0) continue;
    for (const { key, options } of specs) {
      const label = `${collection}.${options.name}`;
      try {
        await db.collection(collection).createIndex(key, options);
        console.log(`  ok    ${label}  ${JSON.stringify(key)}${options.unique ? ' (unique)' : ''}`);
      } catch (err) {
        // A unique index cannot be built while duplicates already exist. Say so
        // plainly instead of failing with a raw driver error.
        if (err.code === 11000 || err.codeName === 'DuplicateKey') {
          console.error(`  FAIL  ${label}: existing duplicate documents block this unique index.`);
          console.error(`        Remove the duplicates, then re-run. Key: ${JSON.stringify(key)}`);
          process.exitCode = 1;
        } else {
          console.error(`  FAIL  ${label}: ${err.message}`);
          process.exitCode = 1;
        }
      }
    }
  }
  console.log('\nDone.');
} finally {
  await client.close();
}
