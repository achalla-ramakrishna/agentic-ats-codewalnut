# Enable private document storage on the existing Railway deployment

This runbook changes storage before moving hosting. Never export production data into
the repository, CI artifacts or a public preview. See [DOCSTORE requirements](features/document-storage.md)
and [ADR-0026](adr/0026-private-document-storage.md).

## Preconditions

1. Record the deployed commit, MySQL version, Flyway history, data/document sizes,
   pending intakes, active assessments and integrations. Restore a recent encrypted
   backup in an isolated environment; validate MySQL version compatibility.
2. Create a **private** Vercel Blob store for production. Create a different private
   store/token for staging. Never use a public store, even with random filenames.
3. Deploy `storage/blob-bridge/Dockerfile` as a private Railway service. Set its
   `BLOB_READ_WRITE_TOKEN`, `ATS_BLOB_STORE_HOST` (private hostname) and a random
   `ATS_BLOB_BRIDGE_SECRET` of at least 32 non-whitespace characters. See the bridge
   README for the fake-file PUT/GET/anonymous-denial smoke test. Do not enable real
   transfers until it passes. Do not create a public domain for the bridge.
4. Deploy the storage-compatible app release with storage disabled. V24 is additive
   except making document bytes nullable; it removes no bytes and performs no remote calls.
5. Set backend `ATS_BLOB_BRIDGE_URL=http://<private-bridge-host>:3001`, matching
   `ATS_BLOB_BRIDGE_SECRET`, and `ATS_DOCUMENT_STORAGE_ENABLED=true`. Keep
   `ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=false` until running migration.

The app default remains database storage. Enabling storage adds a durable task for
new uploads; files stay readable from staging during a provider outage. No UI changes
are required. The bridge token is **not** a VITE_* variable and never enters the frontend.
The application must keep storage enabled after legacy bytes have been removed.

## Copy existing data while Railway remains live

Temporarily enable `ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=true`. Use a signed-in
admin session and its XSRF token over HTTPS, via the normal app origin. Operator routes:

- `GET /api/v1/admin/document-storage`: counts per state and unqueued type. Read only.
- `POST /api/v1/admin/document-storage/migrate?limit=10`: queues up to 10 documents
  and 10 intakes, skipping already queued items. Valid limits 1–100; use 10 on small hosts.
- `POST /api/v1/admin/document-storage/retry`: explicitly retries FAILED transfers.

Use your authenticated API client (same cookie/CSRF flow as other admin actions).
Never create a secret bypass route, share session cookies or disable CSRF. Calls while
View as is active are refused. No provider keys, filenames or document bytes appear in
operator responses. New uploads enqueue automatically in the original DB transaction.

Repeat migrate batches until UNQUEUED_DOCUMENT and UNQUEUED_INTAKE reach zero. The
worker copies at most ten tasks serially per poll; initial/default interval is 30 seconds.
Transient failure backs off 30 seconds to one hour; after eight failures the task is
FAILED, keeping staged bytes. Check FAILED and PENDING counts, resolve configuration
or connectivity and use retry. SKIPPED is a staging intake processed before copying;
its final candidate document has its own task. Confirm no skipped DOCUMENT targets.

Every READY object was read back and checked against the recorded SHA-256 and size.
Sample permissions and download workflows, then reconcile counts again after ongoing
uploads. Recheck all referenced objects before any deletion; a historical READY marker
alone is not sufficient evidence for cleanup.

Disable operator access when finished. Keep backups, database bytes and the compatible
release through the hosting move and an agreed rollback observation period. After new
Blob-only files exist, rolling back to a pre-storage release is unsafe.

## Failure/recovery

- A restarted worker resumes durable due tasks; leases expire after five minutes.
- Immutable object keys make replay after provider success/DB failure safe.
- Copying does not replay emails, AI analysis or stage transitions.
- Before cleanup, a verified retained database copy can serve a Blob outage.
- After cleanup, missing/corrupt/unavailable Blob content returns 503, not a public URL.
- Do not delete READY manifests or rename object keys: shares refer to stable document IDs.
- Pending/failed transfers and retained bytes must be included in final Railway export.
- Retain all referenced objects plus the DB backup as a recovery set; storage itself
  does not replace backups. Temporary intake-object retention is not automated here.

## Verification before enabling production

Run backend MySQL tests, bridge `npm ci && npm test`, and existing frontend checks.
Use fake fixtures for CI, including explicit client-isolation, revoked-share and ID
permission tests. Live private-store smoke checks and live Railway inventory require
operator access and are not implied by local tests.
