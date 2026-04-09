/**
 * MongoDB Atlas Data API Client for Cloudflare Workers.
 * 
 * Uses the native fetch API to interact with Atlas Data API.
 */

export interface MongoConfig {
  apiUrl: string;
  apiKey: string;
  dataSource: string;
  database: string;
}

export class MongoDataAPI {
  constructor(private config: MongoConfig) {}

  /**
   * Performs a request to the Atlas Data API.
   */
  private async request(action: string, collection: string, body: any) {
    const url = `${this.config.apiUrl}/action/${action}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'api-key': this.config.apiKey,
      },
      body: JSON.stringify({
        dataSource: this.config.dataSource,
        database: this.config.database,
        collection: collection,
        ...body,
      }),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`MongoDB Data API Error: ${error}`);
    }

    return await response.json();
  }

  async findOne(collection: string, filter: any) {
    return this.request('findOne', collection, { filter });
  }

  async find(collection: string, filter: any, options: { limit?: number; skip?: number; sort?: any } = {}) {
    return this.request('find', collection, { filter, ...options });
  }

  async insertOne(collection: string, document: any) {
    return this.request('insertOne', collection, { document });
  }

  async updateOne(collection: string, filter: any, update: any, upsert = false) {
    return this.request('updateOne', collection, { filter, update, upsert });
  }

  async aggregate(collection: string, pipeline: any[]) {
    return this.request('aggregate', collection, { pipeline });
  }

  /**
   * Helper for bulk upsert.
   * Note: The Data API doesn't have a direct 'bulkWrite', so we perform
   * multiple updateOne operations sequentially or concurrently.
   * For simplicity and to avoid Worker limits, we'll do them in chunks.
   */
  async bulkUpsert(collection: string, items: { filter: any; update: any }[]) {
    const results = [];
    // Data API doesn't support bulk write in a single call for updateOne with different filters.
    // We iterate and execute. In a real production app, you'd use a more sophisticated batching approach.
    for (const item of items) {
      results.push(this.updateOne(collection, item.filter, item.update, true));
    }
    return Promise.all(results);
  }
}
