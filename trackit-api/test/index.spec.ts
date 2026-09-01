import { env, createExecutionContext, waitOnExecutionContext, SELF } from 'cloudflare:test';
import { describe, it, expect } from 'vitest';
import worker from '../src';

/**
 * These tests cover the auth gate — the boundary that every other guarantee in the
 * API rests on. They deliberately do not touch MongoDB: a request that fails auth
 * must be rejected before any handler (and therefore any DB connection) runs.
 */

const call = async (path: string, init?: RequestInit) => {
	const request = new Request(`http://example.com${path}`, init);
	const ctx = createExecutionContext();
	const response = await worker.fetch(request, env, ctx);
	await waitOnExecutionContext(ctx);
	return response;
};

describe('auth gate on /api/*', () => {
	const protectedRoutes = [
		['GET', '/api/me'],
		['GET', '/api/expenses'],
		['POST', '/api/expenses'],
		['POST', '/api/sync'],
		['GET', '/api/groups'],
		['GET', '/api/groups/some-group-id'],
		['GET', '/api/groups/some-group-id/splits'],
		['GET', '/api/groups/some-group-id/balances'],
		['POST', '/api/groups/some-group-id/settle'],
		['GET', '/api/budgets'],
		['GET', '/api/analytics/summary'],
	] as const;

	it.each(protectedRoutes)('%s %s rejects a request with no Authorization header', async (method, path) => {
		const response = await call(path, { method });
		expect(response.status).toBe(401);
		expect(await response.json()).toEqual({ error: 'Unauthorized' });
	});

	it.each(protectedRoutes)('%s %s rejects a non-Bearer scheme', async (method, path) => {
		const response = await call(path, { method, headers: { Authorization: 'Basic dXNlcjpwYXNz' } });
		expect(response.status).toBe(401);
	});

	it('rejects a structurally invalid token', async () => {
		const response = await call('/api/me', { headers: { Authorization: 'Bearer not-a-jwt' } });
		expect(response.status).toBe(401);
		expect(await response.json()).toEqual({ error: 'Invalid or expired token' });
	});

	it('rejects an unsigned (alg: none) token - signature stripping must not pass', async () => {
		const b64 = (o: unknown) =>
			btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
		const forged = `${b64({ alg: 'none', typ: 'JWT' })}.${b64({
			sub: 'attacker',
			iss: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
			aud: env.FIREBASE_PROJECT_ID,
			exp: Math.floor(Date.now() / 1000) + 3600,
			iat: Math.floor(Date.now() / 1000),
		})}.`;

		const response = await call('/api/me', { headers: { Authorization: `Bearer ${forged}` } });
		expect(response.status).toBe(401);
	});

	it('does not leak internals in the rejection body', async () => {
		const response = await call('/api/me', { headers: { Authorization: 'Bearer a.b.c' } });
		const body = await response.text();
		expect(body).not.toMatch(/mongodb|stack|at Object|\.ts:/i);
	});
});

describe('unauthenticated surface', () => {
	it('serves the static landing page at /', async () => {
		const response = await SELF.fetch('http://example.com/');
		expect(response.status).toBe(200);
		expect(await response.text()).toContain('TrackIt API');
	});

	it('returns a JSON 404 for an unknown path', async () => {
		const response = await call('/nope');
		expect(response.status).toBe(404);
		expect(await response.json()).toEqual({ error: 'Not found' });
	});
});
