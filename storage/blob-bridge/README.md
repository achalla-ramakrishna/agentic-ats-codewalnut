# Private document storage bridge

An internal Java-to-Node adapter for Vercel's official `@vercel/blob` SDK.
The SDK is pinned with a lockfile; the package repository was verified as
[`vercel/storage`](https://github.com/vercel/storage). See the official
[SDK reference](https://vercel.com/docs/vercel-blob/using-blob-sdk) and
[private storage guide](https://vercel.com/docs/vercel-blob/private-storage).

The Java ATS performs document authorization before calling this service. This
bridge has service authentication, **not end-user permissions**. Never expose its
port through a public Docker port binding, Railway public domain, Caddy route,
or Vercel rewrite. Use Railway private networking during migration and a private
Docker network on the Droplet. When crossing hosts, use an authenticated encrypted
private tunnel or HTTPS; the Bearer secret must not travel on a public HTTP link.
The process binds `::` (dual stack), supporting Railway private IPv6 networking
and the Droplet's IPv4 Docker network without a public port binding.

## Configuration

| Environment variable | Meaning |
| --- | --- |
| `ATS_BLOB_BRIDGE_SECRET` | Random secret of at least 32 non-whitespace characters, shared only with the Java backend |
| `ATS_BLOB_STORE_HOST` | Exact private hostname, e.g. `fakestore.private.blob.vercel-storage.com`; no scheme or path |
| `BLOB_READ_WRITE_TOKEN` | Server-side read/write token for that private store; bridge only |
| `PORT` | Internal listening port, default `3001` |

Create a **Private** store in Vercel, then obtain its hostname/token from the
dashboard. There is no public-storage mode. Every SDK call uses `access: private`;
all keys and returned locations are checked against the configured private host.
Production and development/rehearsal must use separate stores and credentials.
No token belongs in a `VITE_*` variable, frontend build, or source control.

## Internal HTTP contract

Every object operation requires `Authorization: Bearer <ATS_BLOB_BRIDGE_SECRET>`.
The application writes only `documents/<lowercase UUID>`. The bridge also recognizes
legacy `intakes/<lowercase UUID>` keys for compatibility, but intake uploads and
migration never create them (ADR-0029).
No filename, candidate identifier, URL, query string, path escape, listing, delete,
or browser-token endpoint is accepted.

* `PUT /objects/{key}`: raw bytes, `Content-Length` (1 through 10 MiB), and
  `X-Content-SHA256` (64 lowercase hex characters). Content type is stored as
  `application/octet-stream`; original metadata remains in MySQL. Returns `200 {}`
  only after reading the persisted private object without cache and verifying its
  length and SHA-256. A repeated identical upload succeeds without overwriting;
  different content under an existing key returns `409`. The caller retries with
  the **same** key after an ambiguous response.
* `GET /objects/{key}`: returns raw bytes with `Content-Length`,
  `X-Content-SHA256`, and `Cache-Control: private, no-store`. The Java adapter must
  also compare against the expected database length/checksum. Missing object: `404`.
* `GET /health`: unauthenticated process health, `200 {"status":"up"}`. This does
  not test external storage credentials or readiness for migration.

Errors: `400` invalid input/checksum, `401` invalid service secret, `411` missing
length, `413` too large, `409` conflicting stored bytes, `502` failed storage
operation/integrity check, `503` busy (retry after one second). Responses contain
generic codes only. Requests, file contents, storage URLs and provider error text
are never logged. A 60-second operation deadline may instead close the connection;
the Java durable task must retry without discarding its staged database bytes.

Reads consume bounded streams and buffer at most 10 MiB per object before sending
verified response headers. At most two operations run concurrently. Upload
verification temporarily holds both the incoming and persisted copies. Account
for this, SDK overhead and Node itself when setting container memory limits; this
is a document bridge, not a database backup uploader.

Objects are immutable. This first migration phase deliberately has no deletion
endpoint. Intake copies from an earlier unreleased build and abandoned object records need a separately
reviewed retention/reconciliation workflow; operators must retain referenced
objects and rollback copies. Object storage is not a backup by itself.

## Development and validation

```sh
npm ci --ignore-scripts
npm test
npm start
```

Tests use synthetic bytes, fake provider calls and loopback HTTP. They cover
authentication, traversal rejection, private configuration, idempotency, ambiguous
upload recovery, integrity failure, size bounds, provider error redaction and
concurrency limits. No live Vercel token is needed.

Build with `docker build -t ats-blob-bridge storage/blob-bridge` from the repository
root. The image runs as an unprivileged user and supports a read-only filesystem.

Before enabling storage in production, perform an authenticated PUT/GET roundtrip
with a synthetic document in an isolated private store, repeat the PUT, and check
that an unauthenticated direct Blob URL request cannot read it. Also verify that
the bridge cannot be reached from the public internet. Automated fake-SDK tests
cannot establish a live store's configuration or provider reachability.
