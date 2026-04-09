import { Hono } from 'hono';
import { sign, verify } from './auth';
import { MongoDataAPI } from './db';

type Env = {
  SESSIONS: KVNamespace;
  MONGODB_API_URL: string;
  MONGODB_API_KEY: string;
  MONGODB_DATASOURCE: string;
  MONGODB_DATABASE: string;
  JWT_SECRET: string;
};

type Variables = {
  jwtPayload: {
    userId: string;
    phone: string;
    exp: number;
  };
};

const app = new Hono<{ Bindings: Env; Variables: Variables }>();

// ── Helpers ──────────────────────────────────────────────────────────────────

const getDb = (env: Env) => new MongoDataAPI({
  apiUrl: env.MONGODB_API_URL,
  apiKey: env.MONGODB_API_KEY,
  dataSource: env.MONGODB_DATASOURCE,
  database: env.MONGODB_DATABASE,
});

// ── Middleware ───────────────────────────────────────────────────────────────

app.use('/api/*', async (c, next) => {
  if (c.req.path.startsWith('/api/auth/')) {
    return next();
  }

  const authHeader = c.req.header('Authorization');
  if (!authHeader?.startsWith('Bearer ')) {
    return c.json({ error: 'Unauthorized' }, 401);
  }

  const token = authHeader.split(' ')[1];
  try {
    const payload = await verify(token, c.env.JWT_SECRET);
    c.set('jwtPayload', payload);
    await next();
  } catch (e) {
    return c.json({ error: 'Invalid or expired token' }, 401);
  }
});

// ── Auth Routes ──────────────────────────────────────────────────────────────

app.post('/api/auth/send-otp', async (c) => {
  const { phone } = await c.req.json();
  if (!phone) return c.json({ error: 'Phone required' }, 400);

  const otp = Math.floor(100000 + Math.random() * 900000).toString();
  await c.env.SESSIONS.put(`otp:${phone}`, otp, { expirationTtl: 300 });

  // In production, send via Twilio. For now, log to console.
  console.log(`[OTP for ${phone}]: ${otp}`);

  return c.json({ success: true, message: 'OTP sent' });
});

app.post('/api/auth/verify-otp', async (c) => {
  const { phone, otp } = await c.req.json();
  if (!phone || !otp) return c.json({ error: 'Phone and OTP required' }, 400);

  const storedOtp = await c.env.SESSIONS.get(`otp:${phone}`);
  if (storedOtp !== otp) {
    return c.json({ error: 'Invalid OTP' }, 401);
  }

  await c.env.SESSIONS.delete(`otp:${phone}`);

  const userId = `user_${btoa(phone).substring(0, 10)}`;
  const token = await sign({
    userId,
    phone,
    exp: Math.floor(Date.now() / 1000) + (30 * 24 * 60 * 60), // 30 days
  }, c.env.JWT_SECRET);

  return c.json({ token, userId });
});

// ── Expense Routes ───────────────────────────────────────────────────────────

app.get('/api/expenses', async (c) => {
  const { userId } = c.get('jwtPayload');
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
  const { userId } = c.get('jwtPayload');
  const expense = await c.req.json();

  const db = getDb(c.env);
  const result = await db.insertOne('expenses', { ...expense, userId });

  return c.json(result);
});

app.post('/api/sync', async (c) => {
  const { userId } = c.get('jwtPayload');
  const expenses: any[] = await c.req.json();

  const db = getDb(c.env);
  const upserts = expenses.map(e => ({
    filter: { id: e.id, userId },
    update: { $set: { ...e, userId, syncedAt: Date.now() } }
  }));

  const results = await db.bulkUpsert('expenses', upserts);
  return c.json({ synced: results.length });
});

// ── Analytics ───────────────────────────────────────────────────────────────

app.get('/api/analytics/summary', async (c) => {
  const { userId } = c.get('jwtPayload');
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
  
  // Post-process grouping in code to keep aggregation simple for Data API
  const summary = result.documents[0] || { total: 0, byCategory: [] };
  const categories: Record<string, number> = {};
  summary.byCategory.forEach((item: any) => {
    categories[item.category] = (categories[item.category] || 0) + item.amount;
  });

  return c.json({
    total: summary.total,
    byCategory: categories,
    month
  });
});

// ── Budget Routes ────────────────────────────────────────────────────────────

app.get('/api/budgets', async (c) => {
  const { userId } = c.get('jwtPayload');
  const month = c.req.query('month');

  const db = getDb(c.env);
  const result = await db.findOne('budgets', { userId, month }) as { document?: any };

  return c.json(result.document || {});
});

app.post('/api/budgets', async (c) => {
  const { userId } = c.get('jwtPayload');
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
