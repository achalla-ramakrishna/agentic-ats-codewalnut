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
5. Confirm live downloads and private provider access are healthy. Do not proceed
   while transfers are failing, referenced objects are missing, backups are
   incomplete, or there is an unresolved incident.

## Operator request

Temporarily set all three flags on the backend:

```text
ATS_DOCUMENT_STORAGE_ENABLED=true
ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=true
ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED=true
```

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
changing documents; it records a summary audit with `dryRun: true`. It does not
reserve a batch. A later destructive request reselects and re-verifies its batch.

Review the dry-run and recovery evidence. To remove one verified copy, submit the
same parameters with `dryRun: false`. Each removed copy receives a per-document
audit committed atomically with the change; every request also has a count summary.
If `failed` or `skipped` is nonzero, stop and investigate before proceeding. Failures
keep their database bytes. Since the oldest eligible documents are selected first,
a failing document must be repaired before a small batch can progress past it.

After each batch, verify downloads and monitor storage errors. Subsequent batches
skip already-cleaned documents; rerunning after an ambiguous response is safe.
Documents verified after the cutoff remain in MySQL until a later eligible window.
Only document bytes are cleared; document IDs, metadata, client shares, task
manifests and remote objects are preserved. Intake staging is excluded.

Disable `ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED` and
`ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED` immediately when the maintenance is done.
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
