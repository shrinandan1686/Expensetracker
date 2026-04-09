/**
 * HMAC-SHA256 JWT Implementation using Web Crypto API.
 * 
 * Provides signing and verification of JSON Web Tokens without external dependencies.
 */

interface JWTPayload {
  userId: string;
  phone: string;
  exp: number;
  [key: string]: any;
}

/**
 * Encodes a string or ArrayBuffer to Base64URL format.
 */
function base64UrlEncode(str: string | ArrayBuffer): string {
  const bytes = typeof str === 'string' ? new TextEncoder().encode(str) : new Uint8Array(str);
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Decodes a Base64URL string to an ArrayBuffer.
 */
function base64UrlDecode(str: string): Uint8Array {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const pad = base64.length % 4;
  const padded = pad ? base64 + '='.repeat(4 - pad) : base64;
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Signs a payload with HMAC-SHA256 using the provided secret.
 */
export async function sign(payload: JWTPayload, secret: string): Promise<string> {
  const encoder = new TextEncoder();
  
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const dataToSign = `${encodedHeader}.${encodedPayload}`;

  const keyData = encoder.encode(secret);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "HMAC",
    cryptoKey,
    encoder.encode(dataToSign)
  );

  return `${dataToSign}.${base64UrlEncode(signature)}`;
}

/**
 * Verifies a JWT and returns the decoded payload if valid.
 * Throws an error if the signature is invalid or the token is expired.
 */
export async function verify(token: string, secret: string): Promise<JWTPayload> {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Invalid token format');

  const [header, payload, signature] = parts;
  const dataToVerify = `${header}.${payload}`;
  
  const encoder = new TextEncoder();
  const keyData = encoder.encode(secret);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const sigBytes = base64UrlDecode(signature);
  const isValid = await crypto.subtle.verify(
    "HMAC",
    cryptoKey,
    sigBytes,
    encoder.encode(dataToVerify)
  );

  if (!isValid) throw new Error('Invalid signature');

  const decodedPayload = JSON.parse(new TextDecoder().decode(base64UrlDecode(payload))) as JWTPayload;
  
  if (Date.now() / 1000 > decodedPayload.exp) {
    throw new Error('Token expired');
  }

  return decodedPayload;
}
