# ADR-0026: Private Vercel Blob with durable database staging

- **Date:** 2026-10-08
- **Status:** Accepted

## Context

The live Railway application stores candidate documents and pending résumé intakes
as MySQL LONGBLOBs. Hosting is moving to Vercel plus a small DigitalOcean Droplet.
Document storage must remain private, preserve client-share permissions and allow
rehearsal/rollback without losing uploads accepted during copying.

## Decision

Use a private Vercel Blob store behind the existing Java API authorization. An
internal, authenticated Node bridge uses the official @vercel/blob SDK, pinned by
lockfile. The bridge has no public route; it owns the provider token and accepts only
fixed UUID paths, bounded bytes and immutable idempotent operations. This avoids an
undocumented Java provider-upload protocol. Keep bridge and backend on a private
network; use HTTPS if crossing hosts.

The original insert and a BackgroundTask outbox row commit together. That row also
serves as the immutable object manifest (type, source ID, key, SHA-256, length).
A serial leased worker copies files outside database transactions, independently
checks remote bytes and marks READY. Failed copies retry and then require an
explicit operator retry; retained local bytes keep the accepted document usable.

Existing download permissions run before reading storage. HTTP returns a download
DTO's bytes with no-store, never persistence entities, credentials, public URLs or
bearer download links. AI reading and generated résumés use the same content service.

Copy old data with an admin-only, opt-in operator API in small batches. Do not delete
legacy bytes in this PR. Removing bytes later requires an independent gated cleanup
and a Blob-aware rollback version. Production and rehearsal use separate stores;
no production provider tokens in ordinary CI.

## Consequences

Database and object storage do not share a transaction; staging and the durable task
make retries recoverable. Keeping bytes initially means disk usage does not fall
until cleanup. Temporary intake objects are retained until a separate retention
policy safely deletes them. Backups must cover the DB manifest and referenced objects.

The bridge adds one small process. Downloads and uploads remain bounded in memory
rather than a new direct-browser upload flow. A live fake-file smoke test must verify
private PUT/GET and unauthenticated denial before production enablement. Providers
remain replaceable behind PrivateDocumentStore.
