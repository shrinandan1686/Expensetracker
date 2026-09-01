import { Hono } from 'hono';
import { HTTPException } from 'hono/http-exception';
import { verifyFirebaseToken } from './auth';
import { MongoDataAPI } from './db';

type Env = {
  MONGODB_URI: string;
  MONGODB_DATABASE: string;
  FIREBASE_PROJECT_ID: string;
  // Rate limit bindings, declared in wrangler.jsonc. Optional in the type so the
  // Worker still runs where they are not provisioned (e.g. the test runtime).
  IP_RATE_LIMIT?: RateLimit;
  USER_RATE_LIMIT?: RateLimit;
};

type Variables = {
  userId: string;
};

const app = new Hono<{ Bindings: Env; Variables: Variables }>();

// ── Helpers ──────────────────────────────────────────────────────────────────

const getDb = (env: Env) => new MongoDataAPI(env.MONGODB_URI, env.MONGODB_DATABASE);

/**
 * Loads a group only if the caller is one of its members.
 *
 * Every /api/groups/:id/* route must go through this. Firebase auth proves *who*
 * the caller is; it says nothing about *what* they may read. Without this check
 * any signed-in user could pass an arbitrary group id and read or write another
 * group's splits, balances and settlements.
 */
async function requireMembership(
  db: MongoDataAPI,
  groupId: string,
  userId: string
): Promise<any | null> {
  const result = (await db.findOne('groups', {
    _id: groupId,
    'members.userId': userId,
  })) as any;
  return result?.document ?? null;
}

/** Rejects request-body values that are objects, which Mongo would read as query operators. */
function assertScalar(value: unknown, field: string): void {
  if (value !== null && typeof value === 'object') {
    throw new HTTPException(400, { message: `Invalid ${field}` });
  }
}

/** Parses a positive integer query param, clamped to `max` so a client cannot ask for the whole collection. */
function positiveInt(raw: string | undefined, fallback: number, max: number): number {
  const n = Number.parseInt(raw ?? '', 10);
  if (!Number.isFinite(n) || n < 1) return fallback;
  return Math.min(n, max);
}

/** Validates a currency amount: finite, positive, and within a sane ceiling. */
function assertAmount(value: unknown, field = 'amount'): number {
  const n = typeof value === 'number' ? value : Number.NaN;
  if (!Number.isFinite(n) || n <= 0 || n > 100_000_000) {
    throw new HTTPException(400, { message: `Invalid ${field}` });
  }
  return n;
}

const MAX_PAGE_SIZE = 200;
const MAX_SYNC_BATCH = 500;

/**
 * Consumes one token from a rate limiter, returning true when the request may
 * proceed. A missing binding is treated as "allowed" so the Worker stays usable
 * in environments where the limiter is not provisioned; the limiter itself is
 * best-effort by design, and failing open here matches Cloudflare's own guidance
 * that a limiter outage should not take the API down with it.
 */
async function withinLimit(limiter: RateLimit | undefined, key: string): Promise<boolean> {
  if (!limiter) return true;
  try {
    const { success } = await limiter.limit({ key });
    return success;
  } catch {
    return true;
  }
}

const tooManyRequests = (c: any, period: number) =>
  c.json({ error: 'Too many requests' }, 429, { 'Retry-After': String(period) });

// ── Auth Middleware ───────────────────────────────────────────────────────────
// All /api/* routes require a valid Firebase ID token in Authorization: Bearer <token>.
// The Firebase UID is extracted and stored in context as userId for route handlers.

app.use('/api/*', async (c, next) => {
  // Per-IP limit first: verifying an RS256 signature costs CPU, so an
  // unauthenticated flood must be turned away before it reaches the crypto path.
  const ip = c.req.header('CF-Connecting-IP') ?? 'unknown';
  if (!(await withinLimit(c.env.IP_RATE_LIMIT, ip))) {
    return tooManyRequests(c, 60);
  }

  const authHeader = c.req.header('Authorization');
  if (!authHeader?.startsWith('Bearer ')) {
    return c.json({ error: 'Unauthorized' }, 401);
  }

  const token = authHeader.split(' ')[1];
  let userId: string;
  try {
    const payload = await verifyFirebaseToken(token, c.env.FIREBASE_PROJECT_ID);
    userId = payload.uid;
  } catch (e) {
    return c.json({ error: 'Invalid or expired token' }, 401);
  }

  // Per-user limit second, so one account cannot exhaust the Worker or hammer
  // MongoDB on everyone else's behalf even with a perfectly valid token.
  if (!(await withinLimit(c.env.USER_RATE_LIMIT, userId))) {
    return tooManyRequests(c, 60);
  }

  c.set('userId', userId);
  await next();
});

// ── Auth: Profile Bootstrap ───────────────────────────────────────────────────
// Called once after Google Sign-In to upsert the user record in MongoDB.
// Uses $setOnInsert for createdAt so it's only written on first login.

app.post('/api/auth/profile', async (c) => {
  const userId = c.get('userId');
  const { name, email, photoUrl } = await c.req.json();
  assertScalar(name, 'name');
  assertScalar(email, 'email');
  assertScalar(photoUrl, 'photoUrl');

  const db = getDb(c.env);
  await db.updateOne(
    'users',
    { _id: userId },
    {
      $setOnInsert: { _id: userId, createdAt: Date.now() },
      $set: { name: name ?? '', email: email ?? '', photoUrl: photoUrl ?? '', updatedAt: Date.now() }
    },
    true
  );

  return c.json({ userId, name, email });
});

// ── User Profile Routes ───────────────────────────────────────────────────────

app.get('/api/me', async (c) => {
  const userId = c.get('userId');
  const db = getDb(c.env);
  const user = await db.findOne('users', { _id: userId });
  if (!user) return c.json({ error: 'User not found' }, 404);
  return c.json(user);
});

app.put('/api/me', async (c) => {
  const userId = c.get('userId');
  const { name, upiId } = await c.req.json();
  const db = getDb(c.env);
  const update: Record<string, unknown> = { updatedAt: Date.now() };
  if (name !== undefined) {
    assertScalar(name, 'name');
    update.name = String(name).slice(0, 100);
  }
  if (upiId !== undefined) {
    assertScalar(upiId, 'upiId');
    update.upiId = String(upiId).slice(0, 100);
  }
  await db.updateOne('users', { _id: userId }, { $set: update }, false);
  return c.json({ success: true });
});

// ── Expense Routes ───────────────────────────────────────────────────────────

app.get('/api/expenses', async (c) => {
  const userId = c.get('userId');
  const month = c.req.query('month');
  const page = positiveInt(c.req.query('page'), 1, 10_000);
  const limit = positiveInt(c.req.query('limit'), 50, MAX_PAGE_SIZE);

  // Incremental pull watermark. The client sends the updatedAt of the last row it
  // successfully stored, so a routine sync transfers only what changed.
  const updatedSinceRaw = Number.parseInt(c.req.query('updated_since') ?? '', 10);
  const updatedSince = Number.isFinite(updatedSinceRaw) && updatedSinceRaw > 0 ? updatedSinceRaw : null;

  // Tombstones are only useful to a syncing client, which needs them to apply a
  // deletion made on another device. Anything else asking for a list wants the
  // live rows, so deleted ones are excluded by default.
  const includeDeleted = c.req.query('include_deleted') === 'true';

  const filter: any = { userId };
  if (month) filter.month = month;
  if (updatedSince !== null) filter.updatedAt = { $gt: updatedSince };
  if (!includeDeleted) filter.isDeleted = { $ne: true };

  const db = getDb(c.env);
  // Sorted by updatedAt for a pull so pagination is stable against the same
  // watermark the client is advancing; by transactionAt otherwise, which is the
  // order a list view wants.
  const result = await db.find('expenses', filter, {
    limit,
    skip: (page - 1) * limit,
    sort: updatedSince !== null ? { updatedAt: 1 } : { transactionAt: -1 }
  });

  return c.json(result);
});

app.post('/api/expenses', async (c) => {
  const userId = c.get('userId');
  const { _id, userId: _ignoredUserId, ...expense } = await c.req.json();

  assertAmount(expense.amount);
  assertScalar(expense.id, 'id');

  const db = getDb(c.env);
  // userId is spread last so a client cannot claim another user's records.
  const result = await db.insertOne('expenses', { ...expense, userId });

  return c.json(result);
});

app.post('/api/sync', async (c) => {
  const userId = c.get('userId');
  const { expenses }: { expenses: any[] } = await c.req.json();
  if (!Array.isArray(expenses)) {
    return c.json({ error: 'expenses must be an array' }, 400);
  }
  if (expenses.length > MAX_SYNC_BATCH) {
    return c.json({ error: `Batch too large (max ${MAX_SYNC_BATCH})` }, 413);
  }
  const now = Date.now();

  const db = getDb(c.env);
  // Update pipeline: only replace the stored record when the incoming updatedAt
  // is >= the stored updatedAt. This prevents an older offline edit from
  // clobbering a newer edit made on another device.
  const upserts = expenses.map((raw: any) => {
    // `id` goes straight into a query filter, so it must be a scalar — an object
    // such as {"$ne": null} would otherwise be read as a query operator.
    if (typeof raw?.id !== 'string' || raw.id.length === 0) {
      throw new HTTPException(400, { message: 'Each expense needs a string id' });
    }
    const { _id, userId: _ignoredUserId, ...e } = raw;
    const incomingUpdatedAt =
      typeof e.updatedAt === 'number' && Number.isFinite(e.updatedAt) ? e.updatedAt : 0;
    // A tombstone is stored, not removed: other devices have to be able to learn
    // about the deletion on their next pull, which a hard delete would hide.
    e.isDeleted = e.isDeleted === true;

    return {
      filter: { id: e.id, userId },
      update: [
        {
          $replaceWith: {
            $mergeObjects: [
              '$$ROOT',
              {
                $cond: {
                  if: { $gte: [incomingUpdatedAt, { $ifNull: ['$updatedAt', 0] }] },
                  then: { ...e, userId, syncedAt: now },
                  else: {}
                }
              }
            ]
          }
        }
      ]
    };
  });

  const results = await db.bulkUpsert('expenses', upserts);

  // Field names match what the client actually reads (ExpenseDto.SyncResponseDto).
  // The previous `{ synced: N }` matched nothing, so the client fell back to
  // assuming every expense in the batch had persisted.
  return c.json({
    synced_ids: upserts.map((u) => u.filter.id as string),
    failed_ids: [],
    message: `Synced ${results.length} expense(s)`,
  });
});

// ── Groups ───────────────────────────────────────────────────────────────────

// Greedy min-transactions debt simplification. Runs on the server so the client
// just receives a flat list of { from, to, amount } pairs.
function simplifyDebts(
  net: Record<string, number>
): { from: string; to: string; amount: number }[] {
  const creditors = Object.entries(net).filter(([, v]) => v > 0.005).map(([id, amount]) => ({ id, amount }));
  const debtors   = Object.entries(net).filter(([, v]) => v < -0.005).map(([id, amount]) => ({ id, amount: -amount }));
  creditors.sort((a, b) => b.amount - a.amount);
  debtors.sort((a, b) => b.amount - a.amount);
  const result: { from: string; to: string; amount: number }[] = [];
  let ci = 0, di = 0;
  while (ci < creditors.length && di < debtors.length) {
    const c = creditors[ci], d = debtors[di];
    const amount = Math.min(c.amount, d.amount);
    result.push({ from: d.id, to: c.id, amount: Math.round(amount * 100) / 100 });
    c.amount -= amount;
    d.amount -= amount;
    if (c.amount < 0.005) ci++;
    if (d.amount < 0.005) di++;
  }
  return result;
}

app.post('/api/groups', async (c) => {
  const userId = c.get('userId');
  const { name, emoji } = await c.req.json();
  if (typeof name !== 'string' || name.trim().length === 0) {
    return c.json({ error: 'Group name is required' }, 400);
  }
  const db = getDb(c.env);

  const creatorResult = await db.findOne('users', { _id: userId }) as any;
  const creator = creatorResult?.document;
  const member = { userId, name: creator?.name ?? '', phone: '', upiId: creator?.upiId ?? null };

  const group = {
    _id: crypto.randomUUID(),
    name: name.trim().slice(0, 100),
    emoji: typeof emoji === 'string' ? emoji.slice(0, 8) : null,
    createdBy: userId,
    members: [member],
    createdAt: Date.now()
  };
  await db.insertOne('groups', group);
  return c.json({ ...group, id: group._id });
});

app.get('/api/groups', async (c) => {
  const userId = c.get('userId');
  const db = getDb(c.env);
  const result = await db.find('groups', { 'members.userId': userId }, { sort: { createdAt: -1 } }) as any;
  const groups = (result?.documents ?? []).map((g: any) => ({ ...g, id: g._id }));
  return c.json(groups);
});

app.get('/api/groups/:id', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const db = getDb(c.env);
  const result = await db.findOne('groups', { _id: groupId, 'members.userId': userId }) as any;
  if (!result?.document) return c.json({ error: 'Group not found' }, 404);
  return c.json({ ...result.document, id: result.document._id });
});

app.post('/api/groups/:id/members', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const { memberId, memberName, memberPhone, memberUpiId } = await c.req.json();
  if (typeof memberId !== 'string' || memberId.length === 0) {
    return c.json({ error: 'memberId must be a string' }, 400);
  }
  const db = getDb(c.env);
  // The filter doubles as the authorization check: the update only applies to a
  // group the caller is already a member of.
  await db.updateOne(
    'groups',
    { _id: groupId, 'members.userId': userId },
    { $addToSet: { members: { userId: memberId, name: memberName ?? '', phone: memberPhone ?? '', upiId: memberUpiId ?? null } } },
    false
  );
  return c.json({ success: true });
});

app.post('/api/groups/:id/splits', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const body = await c.req.json();
  const db = getDb(c.env);

  const group = await requireMembership(db, groupId, userId);
  if (!group) return c.json({ error: 'Group not found' }, 404);

  const totalAmount = assertAmount(body.totalAmount, 'totalAmount');

  // paidBy and every participant must be a member of *this* group, otherwise a
  // split could assign a debt to an unrelated user and skew their balances.
  const memberIds = new Set<string>(group.members.map((m: any) => m.userId));
  const paidBy = body.paidBy ?? userId;
  if (!memberIds.has(paidBy)) {
    return c.json({ error: 'paidBy is not a member of this group' }, 400);
  }

  const rawParticipants = Array.isArray(body.participants) ? body.participants : [];
  const participants = rawParticipants.map((p: any) => {
    if (!memberIds.has(p?.userId)) {
      throw new HTTPException(400, { message: 'Participant is not a member of this group' });
    }
    return { userId: p.userId as string, amount: assertAmount(p.amount, 'participant amount') };
  });

  const split = {
    _id: crypto.randomUUID(),
    groupId,
    description: typeof body.description === 'string' ? body.description.slice(0, 500) : '',
    totalAmount,
    currency: 'INR',
    paidBy,
    participants,
    expenseId: typeof body.expenseId === 'string' ? body.expenseId : null,
    createdBy: userId,
    createdAt: Date.now()
  };
  await db.insertOne('splits', split);
  return c.json({ ...split, id: split._id });
});

app.get('/api/groups/:id/splits', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const db = getDb(c.env);

  if (!(await requireMembership(db, groupId, userId))) {
    return c.json({ error: 'Group not found' }, 404);
  }

  const result = await db.find('splits', { groupId }, { sort: { createdAt: -1 } }) as any;
  const splits = (result?.documents ?? []).map((s: any) => ({ ...s, id: s._id }));
  return c.json(splits);
});

app.get('/api/groups/:id/balances', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const db = getDb(c.env);

  // Membership is resolved first: the balances payload exposes every member's
  // uid and net position, so non-members must not reach the aggregation at all.
  const group = await requireMembership(db, groupId, userId);
  if (!group) return c.json({ error: 'Group not found' }, 404);

  const [splitsResult, settlementsResult] = await Promise.all([
    db.find('splits', { groupId }) as Promise<any>,
    db.find('settlements', { groupId, status: 'confirmed' }) as Promise<any>
  ]);

  const splits       = splitsResult?.documents ?? [];
  const settlements  = settlementsResult?.documents ?? [];

  const net: Record<string, number> = {};
  for (const m of group.members) net[m.userId] = 0;

  for (const split of splits) {
    net[split.paidBy] = (net[split.paidBy] || 0) + split.totalAmount;
    for (const p of split.participants) {
      net[p.userId] = (net[p.userId] || 0) - p.amount;
    }
  }
  for (const s of settlements) {
    net[s.fromUserId] = (net[s.fromUserId] || 0) + s.amount;
    net[s.toUserId]   = (net[s.toUserId]   || 0) - s.amount;
  }

  return c.json({ balances: simplifyDebts(net), net });
});

app.post('/api/groups/:id/settle', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const { toUserId, amount } = await c.req.json();
  const db = getDb(c.env);

  const group = await requireMembership(db, groupId, userId);
  if (!group) return c.json({ error: 'Group not found' }, 404);

  const validAmount = assertAmount(amount);
  const memberIds = new Set<string>(group.members.map((m: any) => m.userId));
  if (!memberIds.has(toUserId)) {
    return c.json({ error: 'toUserId is not a member of this group' }, 400);
  }
  if (toUserId === userId) {
    return c.json({ error: 'Cannot settle with yourself' }, 400);
  }

  const settlement = {
    _id: crypto.randomUUID(),
    groupId,
    fromUserId: userId,
    toUserId,
    amount: validAmount,
    status: 'pending',
    createdAt: Date.now(),
    confirmedAt: null
  };
  await db.insertOne('settlements', settlement);
  return c.json({ ...settlement, id: settlement._id });
});

app.put('/api/settlements/:id/confirm', async (c) => {
  const userId = c.get('userId');
  const settlementId = c.req.param('id');
  const db = getDb(c.env);
  await db.updateOne(
    'settlements',
    { _id: settlementId, toUserId: userId },
    { $set: { status: 'confirmed', confirmedAt: Date.now() } },
    false
  );
  return c.json({ success: true });
});

// ── Analytics ───────────────────────────────────────────────────────────────

app.get('/api/analytics/summary', async (c) => {
  const userId = c.get('userId');
  const month = c.req.query('month');
  if (!month) return c.json({ error: 'Month required' }, 400);

  const db = getDb(c.env);
  const pipeline = [
    // isDeleted is excluded here too, or a deleted expense would keep showing up
    // in the month's total after the client has stopped displaying it.
    { $match: { userId, month, isDeleted: { $ne: true } } },
    {
      $group: {
        _id: null,
        total: { $sum: "$amount" },
        byCategory: { $push: { category: "$category", amount: "$amount" } }
      }
    }
  ];

  const result = await db.aggregate('expenses', pipeline) as { documents: any[] };

  const summary = result.documents[0] || { total: 0, byCategory: [] };
  const categories: Record<string, number> = {};
  summary.byCategory.forEach((item: any) => {
    categories[item.category] = (categories[item.category] || 0) + item.amount;
  });

  return c.json({ total: summary.total, byCategory: categories, month });
});

// ── Budget Routes ────────────────────────────────────────────────────────────

app.get('/api/budgets', async (c) => {
  const userId = c.get('userId');
  const month = c.req.query('month');
  if (!month) return c.json({ error: 'Month required' }, 400);

  const db = getDb(c.env);
  const result = await db.findOne('budgets', { userId, month }) as { document?: any };

  return c.json(result.document || {});
});

app.post('/api/budgets', async (c) => {
  const userId = c.get('userId');
  const { _id, userId: _ignoredUserId, ...budget } = await c.req.json();
  if (typeof budget.month !== 'string') {
    return c.json({ error: 'month must be a string' }, 400);
  }

  const db = getDb(c.env);
  const result = await db.updateOne(
    'budgets',
    { userId, month: budget.month },
    { $set: { ...budget, userId, updatedAt: Date.now() } },
    true
  );

  return c.json(result);
});

// ── Error Handling ───────────────────────────────────────────────────────────
// Validation failures surface as a clean 400. Anything unexpected is logged
// server-side (visible in `wrangler tail`) but never echoed to the client —
// stack traces and driver errors can disclose schema and connection details.

app.onError((err, c) => {
  if (err instanceof HTTPException) {
    return c.json({ error: err.message }, err.status);
  }
  console.error('Unhandled error:', err);
  return c.json({ error: 'Internal server error' }, 500);
});

app.notFound((c) => c.json({ error: 'Not found' }, 404));

export default app;
