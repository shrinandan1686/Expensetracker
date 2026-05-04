import { Hono } from 'hono';
import { verifyFirebaseToken } from './auth';
import { MongoDataAPI } from './db';

type Env = {
  MONGODB_URI: string;
  MONGODB_DATABASE: string;
  FIREBASE_PROJECT_ID: string;
};

type Variables = {
  userId: string;
};

const app = new Hono<{ Bindings: Env; Variables: Variables }>();

// ── Helpers ──────────────────────────────────────────────────────────────────

const getDb = (env: Env) => new MongoDataAPI(env.MONGODB_URI, env.MONGODB_DATABASE);

// ── Auth Middleware ───────────────────────────────────────────────────────────
// All /api/* routes require a valid Firebase ID token in Authorization: Bearer <token>.
// The Firebase UID is extracted and stored in context as userId for route handlers.

app.use('/api/*', async (c, next) => {
  const authHeader = c.req.header('Authorization');
  if (!authHeader?.startsWith('Bearer ')) {
    return c.json({ error: 'Unauthorized' }, 401);
  }

  const token = authHeader.split(' ')[1];
  try {
    const payload = await verifyFirebaseToken(token, c.env.FIREBASE_PROJECT_ID);
    c.set('userId', payload.uid);
    await next();
  } catch (e) {
    return c.json({ error: 'Invalid or expired token' }, 401);
  }
});

// ── Auth: Profile Bootstrap ───────────────────────────────────────────────────
// Called once after Google Sign-In to upsert the user record in MongoDB.
// Uses $setOnInsert for createdAt so it's only written on first login.

app.post('/api/auth/profile', async (c) => {
  const userId = c.get('userId');
  const { name, email, photoUrl } = await c.req.json();

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
  if (name !== undefined) update.name = name;
  if (upiId !== undefined) update.upiId = upiId;
  await db.updateOne('users', { _id: userId }, { $set: update }, false);
  return c.json({ success: true });
});

// ── Expense Routes ───────────────────────────────────────────────────────────

app.get('/api/expenses', async (c) => {
  const userId = c.get('userId');
  const month = c.req.query('month');
  const page = parseInt(c.req.query('page') || '1');
  const limit = parseInt(c.req.query('limit') || '50');

  const filter: any = { userId };
  if (month) filter.month = month;

  const db = getDb(c.env);
  const result = await db.find('expenses', filter, {
    limit,
    skip: (page - 1) * limit,
    sort: { transactionAt: -1 }
  });

  return c.json(result);
});

app.post('/api/expenses', async (c) => {
  const userId = c.get('userId');
  const expense = await c.req.json();

  const db = getDb(c.env);
  const result = await db.insertOne('expenses', { ...expense, userId });

  return c.json(result);
});

app.post('/api/sync', async (c) => {
  const userId = c.get('userId');
  const { expenses }: { expenses: any[] } = await c.req.json();
  const now = Date.now();

  const db = getDb(c.env);
  // Update pipeline: only replace the stored record when the incoming updatedAt
  // is >= the stored updatedAt. This prevents an older offline edit from
  // clobbering a newer edit made on another device.
  const upserts = expenses.map((e: any) => ({
    filter: { id: e.id, userId },
    update: [
      {
        $replaceWith: {
          $mergeObjects: [
            '$$ROOT',
            {
              $cond: {
                if: { $gte: [e.updatedAt ?? 0, { $ifNull: ['$updatedAt', 0] }] },
                then: { ...e, userId, syncedAt: now },
                else: {}
              }
            }
          ]
        }
      }
    ]
  }));

  const results = await db.bulkUpsert('expenses', upserts);
  return c.json({ synced: results.length });
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
  const db = getDb(c.env);

  const creatorResult = await db.findOne('users', { _id: userId }) as any;
  const creator = creatorResult?.document;
  const member = { userId, name: creator?.name ?? '', phone: '', upiId: creator?.upiId ?? null };

  const group = {
    _id: crypto.randomUUID(),
    name,
    emoji: emoji ?? null,
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
  const db = getDb(c.env);
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
  const split = {
    _id: crypto.randomUUID(),
    groupId,
    description: body.description,
    totalAmount: body.totalAmount,
    currency: 'INR',
    paidBy: body.paidBy ?? userId,
    participants: body.participants ?? [],
    expenseId: body.expenseId ?? null,
    createdAt: Date.now()
  };
  await db.insertOne('splits', split);
  return c.json({ ...split, id: split._id });
});

app.get('/api/groups/:id/splits', async (c) => {
  const groupId = c.req.param('id');
  const db = getDb(c.env);
  const result = await db.find('splits', { groupId }, { sort: { createdAt: -1 } }) as any;
  const splits = (result?.documents ?? []).map((s: any) => ({ ...s, id: s._id }));
  return c.json(splits);
});

app.get('/api/groups/:id/balances', async (c) => {
  const userId = c.get('userId');
  const groupId = c.req.param('id');
  const db = getDb(c.env);

  const [groupResult, splitsResult, settlementsResult] = await Promise.all([
    db.findOne('groups', { _id: groupId }) as Promise<any>,
    db.find('splits', { groupId }) as Promise<any>,
    db.find('settlements', { groupId, status: 'confirmed' }) as Promise<any>
  ]);

  const group = groupResult?.document;
  if (!group) return c.json({ error: 'Group not found' }, 404);

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
  const settlement = {
    _id: crypto.randomUUID(),
    groupId,
    fromUserId: userId,
    toUserId,
    amount,
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
    { $match: { userId, month } },
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

  const db = getDb(c.env);
  const result = await db.findOne('budgets', { userId, month }) as { document?: any };

  return c.json(result.document || {});
});

app.post('/api/budgets', async (c) => {
  const userId = c.get('userId');
  const budget = await c.req.json();

  const db = getDb(c.env);
  const result = await db.updateOne(
    'budgets',
    { userId, month: budget.month },
    { $set: { ...budget, userId, updatedAt: Date.now() } },
    true
  );

  return c.json(result);
});

export default app;
