import { MongoClient, Db, ObjectId } from 'mongodb';

// Worker-scoped connection cache — reused across requests within the same isolate.
let cachedClient: MongoClient | null = null;
let cachedDb: Db | null = null;

async function connect(uri: string, dbName: string): Promise<Db> {
  if (cachedClient && cachedDb) return cachedDb;
  cachedClient = new MongoClient(uri);
  await cachedClient.connect();
  cachedDb = cachedClient.db(dbName);
  return cachedDb;
}

export class MongoDataAPI {
  constructor(private uri: string, private dbName: string) {}

  private async db(): Promise<Db> {
    return connect(this.uri, this.dbName);
  }

  async findOne(collection: string, filter: Record<string, unknown>) {
    const db = await this.db();
    const doc = await db.collection(collection).findOne(filter);
    return { document: doc };
  }

  async find(
    collection: string,
    filter: Record<string, unknown>,
    options: { limit?: number; skip?: number; sort?: Record<string, unknown> } = {}
  ) {
    const db = await this.db();
    let cursor = db.collection(collection).find(filter);
    if (options.sort) cursor = cursor.sort(options.sort as any);
    if (options.skip)  cursor = cursor.skip(options.skip);
    if (options.limit) cursor = cursor.limit(options.limit);
    const documents = await cursor.toArray();
    return { documents };
  }

  async insertOne(collection: string, document: Record<string, unknown>) {
    const db = await this.db();
    const result = await db.collection(collection).insertOne(document as any);
    return { insertedId: result.insertedId.toString() };
  }

  async updateOne(
    collection: string,
    filter: Record<string, unknown>,
    update: Record<string, unknown>,
    upsert = false
  ) {
    const db = await this.db();
    const result = await db.collection(collection).updateOne(filter, update as any, { upsert });
    return { matchedCount: result.matchedCount, modifiedCount: result.modifiedCount, upsertedId: result.upsertedId?.toString() };
  }

  async aggregate(collection: string, pipeline: Record<string, unknown>[]) {
    const db = await this.db();
    const documents = await db.collection(collection).aggregate(pipeline).toArray();
    return { documents };
  }

  async bulkUpsert(collection: string, items: { filter: Record<string, unknown>; update: Record<string, unknown> }[]) {
    return Promise.all(items.map(item => this.updateOne(collection, item.filter, item.update, true)));
  }
}
