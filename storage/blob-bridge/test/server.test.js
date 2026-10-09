import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { test } from 'node:test';
import { buildServer, MAX_BYTES, readConfig } from '../src/server.js';

const host = 'fakestore.private.blob.vercel-storage.com';
const secret = 'fake-bridge-secret-with-at-least-32-characters';
const config = readConfig({ ATS_BLOB_STORE_HOST: host, ATS_BLOB_BRIDGE_SECRET: secret,
  BLOB_READ_WRITE_TOKEN: 'fake-token-for-tests' });
const key = 'documents/12345678-1234-1234-1234-123456789abc';
const hash = (bytes) => createHash('sha256').update(bytes).digest('hex');
const fixture = Buffer.from('Obviously fake resume fixture');

function fakeSdk() {
  const objects = new Map();
  const calls = [];
  return {
    objects, calls,
    async put(pathname, bytes, options) {
      calls.push({ method: 'put', pathname, options });
      if (objects.has(pathname)) throw new Error('already exists');
      objects.set(pathname, Buffer.from(bytes));
      return { url: `https://${host}/${pathname}`, pathname };
    },
    async get(url, options) {
      calls.push({ method: 'get', url, options });
      const pathname = new URL(url).pathname.slice(1);
      const bytes = objects.get(pathname);
      if (!bytes) return null;
      return { statusCode: 200, blob: { url, pathname, size: bytes.length },
        stream: new Blob([bytes]).stream() };
    },
  };
}

async function start(t, sdk = fakeSdk()) {
  const server = buildServer(config, sdk);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const request = (method, path = `/objects/${key}`, body, extra = {}) => fetch(`${base}${path}`, {
    method, headers: { Authorization: `Bearer ${secret}`,
      ...(body ? { 'X-Content-SHA256': hash(body) } : {}), ...extra }, body,
  });
  return { server, sdk, request, base };
}

test('DOCSTORE-01 refuses public, ambiguous, or missing configuration', () => {
  for (const invalidHost of ['store.public.blob.vercel-storage.com', host + '.evil.test',
    'https://' + host, 'localhost', '', host + ':443', host + '/path']) {
    assert.throws(() => readConfig({ ATS_BLOB_STORE_HOST: invalidHost,
      ATS_BLOB_BRIDGE_SECRET: secret, BLOB_READ_WRITE_TOKEN: 'fake' }));
  }
  assert.throws(() => readConfig({ ATS_BLOB_STORE_HOST: host, ATS_BLOB_BRIDGE_SECRET: 'short',
    BLOB_READ_WRITE_TOKEN: 'fake' }));
  assert.throws(() => readConfig({ ATS_BLOB_STORE_HOST: host, ATS_BLOB_BRIDGE_SECRET: secret }));
});

test('DOCSTORE-01 allows authenticated immutable private upload and download', async (t) => {
  const { sdk, request } = await start(t);
  const uploaded = await request('PUT', undefined, fixture);
  assert.equal(uploaded.status, 200);
  assert.deepEqual(await uploaded.json(), {});
  const downloaded = await request('GET');
  assert.equal(downloaded.status, 200);
  assert.equal(downloaded.headers.get('cache-control'), 'private, no-store');
  assert.equal(downloaded.headers.get('x-content-sha256'), hash(fixture));
  assert.equal(downloaded.headers.get('content-length'), String(fixture.length));
  assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()), fixture);
  const upload = sdk.calls.find(call => call.method === 'put');
  assert.equal(upload.options.access, 'private');
  assert.equal(upload.options.allowOverwrite, false);
  assert.equal(upload.options.addRandomSuffix, false);
  assert.equal(upload.options.token, 'fake-token-for-tests');
  for (const call of sdk.calls.filter(call => call.method === 'get')) {
    assert.equal(call.options.useCache, false);
    assert.equal(call.url, `https://${host}/${key}`);
  }
});

test('DOCSTORE-01 denies missing and incorrect bridge authentication before provider access', async (t) => {
  const { sdk, request } = await start(t);
  for (const method of ['GET', 'PUT']) {
    for (const Authorization of ['', 'Bearer wrong', `Basic ${secret}`]) {
      const response = await request(method, undefined, method === 'PUT' ? fixture : undefined, { Authorization });
      assert.equal(response.status, 401);
      await response.arrayBuffer();
    }
  }
  assert.equal(sdk.calls.length, 0);
});

test('DOCSTORE-01 rejects path traversal, query strings and nonopaque keys', async (t) => {
  const { sdk, request } = await start(t);
  for (const path of ['/objects/https://evil.test/file', '/objects/documents/fake@example.test',
    '/objects/documents/%2e%2e%2fsecrets', `/objects/${key}?url=https://evil.test`,
    '/objects/backups/12345678-1234-1234-1234-123456789abc']) {
    const response = await request('GET', path);
    assert.equal(response.status, 404);
    await response.arrayBuffer();
  }
  assert.equal(sdk.calls.length, 0);
});

test('DOCSTORE-02 identical retry succeeds, conflicting content never overwrites', async (t) => {
  const { sdk, request } = await start(t);
  for (let i = 0; i < 2; i++) {
    const response = await request('PUT', undefined, fixture);
    assert.equal(response.status, 200);
    await response.arrayBuffer();
  }
  const conflict = await request('PUT', undefined, Buffer.from('Different fake resume'));
  assert.equal(conflict.status, 409);
  assert.deepEqual(sdk.objects.get(key), fixture);
  assert.equal(sdk.calls.filter(call => call.method === 'put').length, 1);
});

test('DOCSTORE-02 lost upload acknowledgement is verified before reporting success', async (t) => {
  const sdk = fakeSdk();
  const put = sdk.put.bind(sdk);
  sdk.put = async (...args) => { await put(...args); throw new Error('fake network timeout'); };
  const { request } = await start(t, sdk);
  const response = await request('PUT', undefined, fixture);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {});
});

test('DOCSTORE-02 cannot report success for corrupted persisted content', async (t) => {
  const sdk = fakeSdk();
  const put = sdk.put.bind(sdk);
  sdk.put = async (path, bytes, options) => {
    const result = await put(path, bytes, options);
    sdk.objects.set(path, Buffer.from('corrupt'));
    return result;
  };
  const { request } = await start(t, sdk);
  assert.equal((await request('PUT', undefined, fixture)).status, 409);
});

test('DOCSTORE-02 rejects incorrect checksum and oversize upload before provider writes', async (t) => {
  const { sdk, request } = await start(t);
  assert.equal((await request('PUT', undefined, fixture, { 'X-Content-SHA256': '0'.repeat(64) })).status, 400);
  assert.equal((await request('PUT', undefined, Buffer.alloc(MAX_BYTES + 1))).status, 413);
  assert.equal(sdk.calls.length, 0);
});

test('DOCSTORE-01 provider error messages and credentials never escape', async (t) => {
  const sdk = fakeSdk();
  sdk.get = async () => { throw new Error('SECRET_TOKEN https://fake.private.blob.vercel-storage.com/private'); };
  const { request } = await start(t, sdk);
  const response = await request('GET');
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: 'storage_unavailable' });
});

test('DOCSTORE-01 wrong host in upload result is rejected', async (t) => {
  const sdk = fakeSdk();
  sdk.put = async () => ({ pathname: key, url: `https://fakestore.public.blob.vercel-storage.com/${key}` });
  const { request } = await start(t, sdk);
  assert.equal((await request('PUT', undefined, fixture)).status, 502);
});

test('DOCSTORE-01 unexpected provider metadata and oversized streams fail closed', async (t) => {
  for (const mode of ['host', 'path', 'oversize', 'stream']) {
    const sdk = fakeSdk();
    sdk.get = async (url) => ({ statusCode: 200,
      blob: { url: mode === 'host' ? 'https://evil.test/file' : url,
        pathname: mode === 'path' ? 'wrong-path' : key, size: mode === 'oversize' ? MAX_BYTES + 1 : 1 },
      stream: new Blob([mode === 'stream' ? Buffer.alloc(MAX_BYTES + 1) : fixture]).stream() });
    const { request } = await start(t, sdk);
    const response = await request('GET');
    assert.equal(response.status, 502);
    await response.arrayBuffer();
  }
});

test('DOCSTORE-01 no delete, listing or token issuance interface', async (t) => {
  const { sdk, request } = await start(t);
  for (const method of ['DELETE', 'POST', 'OPTIONS']) {
    assert.equal((await request(method)).status, 404);
  }
  assert.equal(sdk.calls.length, 0);
});

test('DOCSTORE-02 missing object returns 404', async (t) => {
  const { request } = await start(t);
  assert.equal((await request('GET')).status, 404);
});

test('DOCSTORE-02 intake keys work and truncated provider responses fail', async (t) => {
  const sdk = fakeSdk();
  const { request } = await start(t, sdk);
  const path = `/objects/${key.replace('documents/', 'intakes/')}`;
  const uploaded = await request('PUT', path, fixture);
  assert.equal(uploaded.status, 200);
  await uploaded.arrayBuffer();
  const get = sdk.get.bind(sdk);
  sdk.get = async (...args) => {
    const result = await get(...args);
    result.headers = new Headers({ 'Content-Length': String(fixture.length + 1) });
    return result;
  };
  assert.equal((await request('GET', path)).status, 502);
});

test('DOCSTORE-02 bounded concurrency rejects excess work without provider calls', async (t) => {
  const sdk = fakeSdk();
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  let entered = 0;
  let signalEntered;
  const started = new Promise(resolve => { signalEntered = resolve; });
  sdk.get = async () => { if (++entered === 2) signalEntered(); await pending; return null; };
  const { request } = await start(t, sdk);
  const first = request('GET');
  const second = request('GET');
  await started;
  const excess = await request('GET');
  assert.equal(excess.status, 503);
  assert.equal(entered, 2);
  release();
  assert.equal((await first).status, 404);
  assert.equal((await second).status, 404);
});
