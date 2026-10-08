# Remove retained database document copies

This is the **last** migration stage, after private storage and the hosting move.
Merging this PR does not authorize running cleanup against production. Keep
`ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED=false` until the deployment operator has
completed and recorded the checks below.

## Required recovery evidence

1. Complete the Railway-to-DigitalOcean cutover and its agreed observation period.
   Leave at least fourteen days between an object's verified transfer and cleanup.
   Use a longer period if your rollback agreement requires it.
2. Take a fresh off-server backup of MySQL, including the complete object manifest
   (`background_task`) and retained document bytes. Independently back up every
   referenced private object; a live Blob store alone is not a backup. Keep the
   restore credentials and the compatible application image/version available.
3. Rehearse restoring that database/manifest/object recovery set into an isolated
   environment. Disable all outbound business workers before starting a restored
   application. Check file checksums, document counts, staff/ID permissions, client
   isolation and revoked shares. Record the restore result in an internal change
   record with a short, nonsecret identifier (for example `restore-20261029-01`).
4. Verify the running application and rollback image both understand private Blob
   manifests. Once cleanup happens, a pre-storage application cannot serve those
   documents; reverting code alone cannot recover deleted bytes.
5. **Pass a Blob-only concurrent-load rehearsal before the first destructive
   production batch.** Use an isolated rehearsal with the exact deployment images,
   target Droplet resources, limits and worker settings, a separate private store,
   and representative synthetic documents whose database bytes are NULL. Never
   remove production bytes just to perform this test. Exercise staff and client
   downloads, including permission denials, while résumé reads and a cleanup
   dry-run compete for storage. Include bursts of at least three simultaneous
   reads (beyond the bridge's two-operation capacity) and the expected peak load,
   with realistic file sizes. Verify successful authorized downloads and checksums,
   bounded recovery after transient provider/busy failures, no insights left stuck
   on "Reading…", and acceptable latency, memory and connection usage. Record
   the workload and measured results with the restore evidence. Unresolved 503s,
   stuck work, memory exhaustion or unacceptable latency block production cleanup;
   adjust capacity or resolve the failure and repeat the rehearsal.
6. Confirm live downloads and private provider access remain healthy. Do not proceed
   while transfers are failing, referenced objects are missing, backups are
   incomplete, or there is an unresolved incident. Healthy reads with retained
   database copies do not satisfy the Blob-only rehearsal gate.

## Operator request

For the supplied DigitalOcean Compose deployment, the two environment files live
beside `compose.yml` in `/opt/ats/deploy/digitalocean`. Configure flags in their
actual sources:

| File | Setting for the maintenance window |
| --- | --- |
| `.env` | `ATS_DOCUMENT_STORAGE_ENABLED=true` |
| `.env.backend` | `ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=true` |
| `.env.backend` | `ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED=true` |

Compose's explicit `environment:` entry for `ATS_DOCUMENT_STORAGE_ENABLED`
overrides a value placed in `.env.backend`; the operations and cleanup flags
come from `.env.backend`. Keep both files private (`chmod 600`) and do not print
the expanded Compose configuration or secrets into shared logs.

These flags are read at application startup. After editing the files, recreate
the backend container from that directory so Compose loads the changed values:

```sh
cd /opt/ats/deploy/digitalocean
docker compose --env-file .env up -d --no-deps --force-recreate backend
```

A plain `docker compose restart backend` does not reload the container environment.
Plan the resulting brief backend interruption, wait for it to become healthy and
verify the authenticated operator status/dry-run before attempting a destructive
request. For a different deployment, set the same three variables in the backend
service's environment and redeploy it; they never belong in the frontend.

Use the normal authenticated admin session and CSRF header with
`POST /api/v1/admin/document-storage/cleanup`. MANAGE_USERS is required; View as
rejects the request. There is no command-line credential bypass or public storage
endpoint. Do not copy browser session cookies into shell history or shared logs.

Start with a dry-run body, replacing the example cutoff with the **actual agreed
observation cutoff**, at least fourteen days before the request, and the reference
with your real nonsecret restore record identifier:

```json
{
  "confirmation": "REMOVE VERIFIED LEGACY DOCUMENT BYTES",
  "backupReference": "restore-20261029-01",
  "restoreVerified": true,
  "observedBefore": "2026-10-15T00:00:00Z",
  "limit": 1,
  "dryRun": true
}
```

The cutoff example is only valid on or after 2026-10-29 and must reflect the actual
completed migration. Omitted `dryRun` means true; omitted `limit` means one. Batch
size is 1–10. `backupReference` is 3–80 ASCII letters, digits, dots, underscores or
hyphens, beginning with a letter/digit. It is recorded in the audit log: never use
a credential, signed URL, filename, candidate name or contact detail.

The response contains only `scanned`, `eligible`, `cleaned`, `skipped`, and `failed`
counts. Dry-run freshly checks private bytes and current database bytes without
changing documents; a completed dry-run records a summary audit with `dryRun: true`. It does not
reserve a batch. A later destructive request reselects and re-verifies its batch.

Review the dry-run and recovery evidence. To remove one verified copy, submit the
same parameters with `dryRun: false`. Each removed copy receives a per-document
audit committed atomically with the change. Requests that reach normal completion
also have a count summary. Rejected configuration/confirmation/cutoff requests and
requests that fail before summary recording do not produce a cleanup summary;
permission denials follow the existing access-policy audit. The atomic per-document
audit remains the source of truth for destructive changes if a later summary or
HTTP response fails.
If `failed` or `skipped` is nonzero, stop and investigate before proceeding. Failures
keep their database bytes. Since the oldest eligible documents are selected first,
a failing document must be repaired before a small batch can progress past it.

After each batch, verify downloads and monitor storage errors. Subsequent batches
skip already-cleaned documents; rerunning after an ambiguous response is safe.
Documents verified after the cutoff remain in MySQL until a later eligible window.
Only document bytes are cleared; document IDs, metadata, client shares, task
manifests and remote objects are preserved. Intake staging is excluded.

When maintenance is done, set `ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED=false` and
`ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=false` in `.env.backend`, then run the
same backend recreation command. Merely editing the file leaves the running
process enabled. After it is healthy, verify an authenticated admin cleanup request
with CSRF receives 404 without changing data. Keep `ATS_DOCUMENT_STORAGE_ENABLED=true`
in `.env`: documents whose database copies were removed still require Blob reads.
There is no automatic cleanup schedule.

## Recovery and disk usage

If a private object disappears after cleanup, recover it from the independent
object backup with the original key and checksum, or restore retained bytes from
the coordinated pre-cleanup database backup using a separately reviewed recovery
procedure. Do not overwrite current production records with an old whole-database
backup: reconcile writes accepted after that backup. Keep the Blob-aware rollback
application and referenced objects for the full retention period.

Setting LONGBLOB data to NULL may free space for MySQL reuse without reducing the
Droplet's filesystem usage. This feature runs no `OPTIMIZE TABLE`, schema rebuild,
column drop, remote deletion or intake retention. Plan any physical compaction
separately after checking available disk, table locks and downtime.
