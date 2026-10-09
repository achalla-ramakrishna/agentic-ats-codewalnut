import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';

export const MAX_BYTES = 10 * 1024 * 1024;
const OBJECT_ROUTE = /^\/objects\/((?:documents|intakes)\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/;
const PRIVATE_HOST = /^[a-z0-9]+\.private\.blob\.vercel-storage\.com$/;
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');

class HttpError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
  }
}

export function readConfig(env) {
  const secret = env.ATS_BLOB_BRIDGE_SECRET;
  if (!secret || secret.length < 32 || /\s/.test(secret)) {
    throw new Error('ATS_BLOB_BRIDGE_SECRET must contain at least 32 non-whitespace characters');
  }
  const host = env.ATS_BLOB_STORE_HOST;
  if (!PRIVATE_HOST.test(host ?? '')) {
    throw new Error('ATS_BLOB_STORE_HOST must be one private Vercel Blob hostname');
  }
  const token = env.BLOB_READ_WRITE_TOKEN;
  if (!token) throw new Error('BLOB_READ_WRITE_TOKEN is required');
  const port = Number(env.PORT ?? 3001);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  return { secret, host, token, port };
}

function validateBlob(blob, key, host) {
  // Never follow URLs supplied by callers or returned by the provider. Reads use
  // a URL constructed from the configured private hostname and validated key.
  if (blob?.url !== `https://${host}/${key}` || blob.pathname !== key) {
    throw new HttpError(502, 'invalid_storage_response');
  }
}

async function readBounded(stream, status) {
  const chunks = [];
  let size = 0;
  for await (const chunk of stream) {
    size += chunk.length;
    if (size > MAX_BYTES) throw new HttpError(status, 'object_too_large');
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks, size);
}

function sendJson(res, status, value) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(value));
}

// sdk is injected so tests exercise failure/retry behavior without storage access.
export function buildServer(config, sdk) {
  const authorizationHash = createHash('sha256').update(`Bearer ${config.secret}`).digest();
  let active = 0;
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'private, no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    if (req.method === 'GET' && req.url === '/health') {
      sendJson(res, 200, { status: 'up' });
      return;
    }
    const presented = createHash('sha256').update(req.headers.authorization ?? '').digest();
    if (!timingSafeEqual(authorizationHash, presented)) {
      res.setHeader('Connection', 'close');
      sendJson(res, 401, { error: 'unauthorized' });
      return;
    }
    const key = OBJECT_ROUTE.exec(req.url ?? '')?.[1];
    if (!key || !['PUT', 'GET'].includes(req.method)) {
      res.setHeader('Connection', 'close');
      sendJson(res, 404, { error: 'not_found' });
      return;
    }
    // Bound the total number of 10 MiB buffers, including slow client responses.
    if (active >= 2) {
      res.setHeader('Retry-After', '1');
      res.setHeader('Connection', 'close');
      sendJson(res, 503, { error: 'busy' });
      return;
    }
    active++;
    const abort = new AbortController();
    const deadline = setTimeout(() => {
      abort.abort();
      req.destroy();
      res.destroy();
    }, 60_000);
    res.once('close', () => {
      clearTimeout(deadline);
      abort.abort();
      active--;
    });
    const options = { access: 'private', token: config.token, abortSignal: abort.signal };
    const readObject = async () => {
      const result = await sdk.get(`https://${config.host}/${key}`, { ...options, useCache: false });
      if (!result) return null;
      try {
        validateBlob(result.blob, key, config.host);
        if (result.statusCode !== 200 || !result.stream) {
          throw new HttpError(502, 'invalid_storage_response');
        }
        if (result.blob.size > MAX_BYTES) throw new HttpError(502, 'object_too_large');
      } catch (error) {
        await result.stream?.cancel().catch(() => {});
        throw error;
      }
      const bytes = await readBounded(result.stream, 502);
      const declaredLength = result.headers?.get('content-length');
      if (declaredLength !== null && declaredLength !== undefined &&
        (!/^\d+$/.test(declaredLength) || Number(declaredLength) !== bytes.length)) {
        throw new HttpError(502, 'invalid_storage_response');
      }
      return bytes;
    };
    try {
      if (req.method === 'GET') {
        const bytes = await readObject();
        if (bytes === null) throw new HttpError(404, 'not_found');
        res.writeHead(200, {
          'Content-Type': 'application/octet-stream',
          'Content-Length': bytes.length,
          'X-Content-SHA256': digest(bytes),
        });
        res.end(bytes);
        return;
      }
      const checksum = req.headers['x-content-sha256'];
      if (typeof checksum !== 'string' || !/^[a-f0-9]{64}$/.test(checksum)) {
        throw new HttpError(400, 'invalid_checksum');
      }
      const lengthHeader = req.headers['content-length'];
      if (!lengthHeader || !/^\d+$/.test(lengthHeader)) throw new HttpError(411, 'length_required');
      const length = Number(lengthHeader);
      if (length > MAX_BYTES) throw new HttpError(413, 'object_too_large');
      if (length === 0) throw new HttpError(400, 'empty_object');
      const bytes = await readBounded(req.iterator({ destroyOnReturn: false }), 413);
      if (bytes.length !== length || digest(bytes) !== checksum) throw new HttpError(400, 'checksum_mismatch');
      let stored = await readObject();
      if (stored === null) {
        let uploaded;
        try {
          uploaded = await sdk.put(key, bytes, {
            ...options,
            addRandomSuffix: false,
            allowOverwrite: false,
            contentType: 'application/octet-stream',
            cacheControlMaxAge: 60,
          });
        } catch {
          // A lost acknowledgement or concurrent retry may have created the key.
          // Only matching persisted bytes can turn an ambiguous error into success.
          stored = await readObject();
          if (stored === null) throw new HttpError(502, 'storage_unavailable');
        }
        if (uploaded) {
          validateBlob(uploaded, key, config.host);
          stored = await readObject();
        }
      }
      if (stored === null) throw new HttpError(502, 'storage_unavailable');
      if (stored.length !== bytes.length || digest(stored) !== checksum) {
        throw new HttpError(409, 'object_conflict');
      }
      sendJson(res, 200, {});
    } catch (error) {
      // Provider errors can contain URLs/tokens. Never log or return their text.
      if (!res.destroyed && !res.headersSent) {
        res.setHeader('Connection', 'close');
        sendJson(res, error instanceof HttpError ? error.status : 502,
          { error: error instanceof HttpError ? error.message : 'storage_unavailable' });
      } else {
        res.destroy();
      }
    }
  });
  server.requestTimeout = 30_000;
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5_000;
  server.maxConnections = 32;
  return server;
}
