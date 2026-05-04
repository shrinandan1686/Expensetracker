/**
 * Firebase ID Token verification using Web Crypto API.
 *
 * Firebase tokens are RS256 JWTs. Public keys are fetched from Google's JWKS
 * endpoint and cached via Cloudflare's Cache API (respects Cache-Control headers,
 * so keys rotate automatically every ~6 hours without any manual intervention).
 *
 * No Firebase Admin SDK required — pure Web Crypto, zero external dependencies.
 */

export interface FirebaseTokenPayload {
  uid: string;
  sub: string;
  email?: string;
  name?: string;
  picture?: string;
  iss: string;
  aud: string;
  iat: number;
  exp: number;
  [key: string]: any;
}

const FIREBASE_JWKS_URL =
  'https://www.googleapis.com/service_accounts/v1/jwks/securetoken@system.gserviceaccount.com';

function base64UrlDecode(str: string): Uint8Array {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const pad = base64.length % 4;
  const padded = pad ? base64 + '='.repeat(4 - pad) : base64;
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/**
 * Fetches and caches Firebase's RS256 public keys.
 *
 * Uses Cloudflare's default Cache API — the response is stored keyed on the
 * JWKS URL and Cloudflare respects the Cache-Control: max-age=21600 header
 * Google sets, so keys are automatically refreshed every 6 hours.
 */
async function getPublicKeys(): Promise<Record<string, CryptoKey>> {
  const cache = caches.default;
  const cacheRequest = new Request(FIREBASE_JWKS_URL);

  let response = await cache.match(cacheRequest);
  if (!response) {
    response = await fetch(FIREBASE_JWKS_URL);
    if (!response.ok) throw new Error('Failed to fetch Firebase public keys');
    await cache.put(cacheRequest, response.clone());
  }

  const jwks: { keys: any[] } = await response.json();

  const entries = await Promise.all(
    jwks.keys.map(async (jwk: any) => {
      const key = await crypto.subtle.importKey(
        'jwk',
        jwk,
        { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
        false,
        ['verify']
      );
      return [jwk.kid, key] as [string, CryptoKey];
    })
  );

  return Object.fromEntries(entries);
}

/**
 * Verifies a Firebase ID token and returns the decoded payload.
 *
 * Throws if:
 * - Token format is invalid
 * - Algorithm is not RS256
 * - No matching public key found for the token's kid
 * - Signature is invalid
 * - Token is expired
 * - Issuer or audience does not match the given projectId
 */
export async function verifyFirebaseToken(
  token: string,
  projectId: string
): Promise<FirebaseTokenPayload> {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Invalid token format');

  const [headerB64, payloadB64, signatureB64] = parts;

  const header = JSON.parse(
    new TextDecoder().decode(base64UrlDecode(headerB64))
  );
  if (header.alg !== 'RS256') throw new Error('Expected RS256 algorithm');

  const keys = await getPublicKeys();
  const publicKey = keys[header.kid];
  if (!publicKey) throw new Error(`No public key for kid: ${header.kid}`);

  const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = base64UrlDecode(signatureB64);

  const isValid = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    publicKey,
    signature,
    data
  );
  if (!isValid) throw new Error('Invalid token signature');

  const payload = JSON.parse(
    new TextDecoder().decode(base64UrlDecode(payloadB64))
  ) as FirebaseTokenPayload;

  const now = Math.floor(Date.now() / 1000);
  if (payload.exp < now) throw new Error('Token expired');
  if (payload.iat > now + 300) throw new Error('Token issued in the future');
  if (payload.iss !== `https://securetoken.google.com/${projectId}`)
    throw new Error('Invalid issuer');
  if (payload.aud !== projectId) throw new Error('Invalid audience');
  if (!payload.sub) throw new Error('Missing subject (uid)');

  payload.uid = payload.sub;
  return payload;
}
