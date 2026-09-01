import { defineWorkersConfig } from '@cloudflare/vitest-pool-workers/config';

export default defineWorkersConfig({
	test: {
		poolOptions: {
			workers: {
				main: './src/index.ts',
				wrangler: { configPath: './wrangler.jsonc' },
				miniflare: {
					// Auth-gate tests never reach the DB; a placeholder keeps the
					// binding present so `env` type-checks and the worker boots.
					bindings: {
						MONGODB_URI: 'mongodb://localhost:27017',
						FIREBASE_PROJECT_ID: 'test-project',
					},
				},
			},
		},
	},
});
