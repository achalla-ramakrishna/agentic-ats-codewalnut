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
   environment. Apply the [migration runbook's frozen startup configuration](deploy-digitalocean.md)
   before starting a restored application; pending work stays preserved and must
   not resume against production providers during rehearsal. Check file checksums,
   document counts, staff/ID permissions, client
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
   bounded automatic retry when the bridge returns 503 busy, no insights left stuck
   on "Reading…", and acceptable latency, memory and connection usage. Provider
   failures reported by the bridge as 502 are not automatically retried within that
   request: after cleanup they reach the user as a generic 503. Explicitly rehearse
   this failure and recovery: the insight must become FAILED, the user must be able
   to retry it after storage recovers, and a subsequent download request must return
   the correct file. Exhausted busy retries also surface an error; they do not
   guarantee that the original request succeeds. Record
   the workload and measured results with the restore evidence. Unresolved 503s,
   stuck work, memory exhaustion or unacceptable latency block production cleanup;
   adjust capacity or resolve the failure and repeat the rehearsal.
6. Confirm live downloads and private provider access remain healthy. Do not proceed
   while transfers are failing, referenced objects are missing, backups are
   incomplete, or there is an unresolved incident. Healthy reads with retained
   database copies do not satisfy the Blob-only rehearsal gate.
7. Confirm the approved production Vercel project/store, then verify the running
   deployment binding with the automated identity preflight below. An operator
   statement that the store is correct, or a matching
   value in an edited environment file, is not a substitute for checking the
   running backend and bridge. Keep the approved production Vercel project, public
   ATS hostname and private-store hostname in the deployment change record; use
   that production ATS hostname for the authenticated cleanup request.

## Production identity preflight

Follow the [deployment runbook](deploy-digitalocean.md) to provision the root-owned
`/opt/ats/deploy/digitalocean/.production-blob-host` pin. Its value must be obtained
independently from the approved company production Vercel project's private Blob
store, not copied from the bridge environment merely to make the check pass.
Confirm the project and store against that approved record before pinning them.
The preflight rejects symlinks, nonregular files, nonroot ownership and any mode
other than `0600`; retain those protections rather than changing the pin to make
an unprivileged check succeed.

From an authorized root shell in the reviewed checkout on the target Droplet, run:

```sh
cd /opt/ats
./scripts/deploy/preflight.sh --require-production-identity --check-running-identity
```

Require a successful exit before the first cleanup dry-run, after recreating the
backend with cleanup enabled, and **immediately before every destructive batch**.
Repeat it after any container, routing, credential or environment change. The
check must verify the expected Compose project/service identities, compare the
effective configuration and running bridge host with the independent production
pin, and verify the running backend's internal bridge target and matching bridge
authentication configuration. This catches pending configuration edits that were never applied
to the containers. Do not proceed on a missing pin, mismatch or unavailable check;
investigate the selected project/store and deployment rather than weakening the
check or repinning from runtime values.

The hostname comparison verifies deployment binding to the independently approved
store; it does not query Vercel to establish company ownership or prove that backups
are complete. Keep the independent project/store approval, private-store smoke test
and recovery evidence as separate prerequisites. The cleanup API does not execute
this deployment preflight on the operator's behalf; fresh per-document integrity
checks and atomic audits remain its runtime protections.

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
with a unique nonsecret batch identifier linked to your verified restore record:

```json
{
  "confirmation": "REMOVE VERIFIED LEGACY DOCUMENT BYTES",
  "backupReference": "restore-20261029-01-dryrun-0001",
  "restoreVerified": true,
  "observedBefore": "2026-10-15T00:00:00Z",
  "limit": 1,
  "dryRun": true
}
```

The cutoff example is only valid on or after 2026-10-29 and must reflect the actual
completed migration. Omitted `dryRun` means true; omitted `limit` means one. **Use
`limit: 1` for both dry-run and destructive requests through Vercel.** The API's
1–10 validation range is not a recommendation to send ten-document HTTP batches.
Vercel's external proxy has a [120-second request timeout](https://vercel.com/docs/limits#proxied-request-timeout),
while each remote file read can consume its own 60-second deadline before the
database check/audit. A larger serial batch can continue on the backend after the
proxy returns 504. Even one document cannot guarantee a response before all
intermediary timeouts. `backupReference` is 3–80 ASCII letters, digits, dots, underscores or
hyphens, beginning with a letter/digit. It is recorded in the audit log: never use
a credential, signed URL, filename, candidate name or contact detail.

Assign a **new `backupReference` to every submitted batch**, including dry-runs,
and record its link to the verified restore evidence in the maintenance record.
For example, `restore-20261029-01-dryrun-0001` and
`restore-20261029-01-apply-0001` identify two distinct requests backed by the same
tested recovery set. Record the actor, submission time, cutoff and dry-run value
before sending each request. Never reuse a reference for concurrent requests or
blind retries; it is an audit correlation label, not an idempotency key.

The response contains only `scanned`, `eligible`, `cleaned`, `skipped`, and `failed`
counts. Dry-run freshly checks private bytes and current database bytes without
changing documents; a completed dry-run records a summary audit with `dryRun: true`. It does not
reserve a batch. A later destructive request reselects and re-verifies its batch.

Review the dry-run and recovery evidence, then rerun the production identity
preflight immediately before proceeding. To remove one verified copy, submit the
same cutoff and limit with `dryRun: false` and a new batch-specific
`backupReference`. Each removed copy receives a per-document
audit committed atomically with the change. Requests that reach normal completion
also have a count summary. Rejected configuration/confirmation/cutoff requests and
requests that fail before summary recording do not produce a cleanup summary;
permission denials follow the existing access-policy audit. The atomic per-document
audit remains the source of truth for destructive changes if a later summary or
HTTP response fails.
If `failed` or `skipped` is nonzero, stop and investigate before proceeding. Failures
keep their database bytes. Since the oldest eligible documents are selected first,
a failing document must be repaired before a small batch can progress past it.

After each batch, verify downloads and monitor storage errors. **A 504, disconnected
browser or other ambiguous response does not mean no bytes were removed.** Stop
sending cleanup requests and use the observable completion check below. Already-cleaned
documents are excluded, so a new request can select a different document and remove
another copy.
Documents verified after the cutoff remain in MySQL until a later eligible window.
Only document bytes are cleared; document IDs, metadata, client shares, task
manifests and remote objects are preserved. Intake staging is excluded.

## Observe completion after an ambiguous response

Using the normal authenticated admin API client with VIEW_AUDIT_LOG, read
`GET /api/v1/audit-log?page=0&size=100`. Entries are newest first; page through the
relevant submission window as needed. The endpoint supports pagination, not a
server-side action/reference filter. Examine `items` and parse each matching row's
`details` JSON string.

The **committed batch summary** is the supported signal that cleanup work for the
request has completed. Find a row with all of these properties:

- `action` is `DOCUMENT_STORAGE_CLEANUP` and both `entityType` and `entityId` are null.
- `actorEmail` matches the submitting administrator and `createdAt` is within the
  recorded request window, after submission.
- `details.backupReference` is the unique reference assigned to this request;
  `details.observedBefore` and `details.dryRun` match its recorded cutoff and mode.
- `details` contains the batch's `scanned`, `eligible`, `cleaned`, `skipped` and
  `failed` counts.

That summary is committed after all per-document work, even when the HTTP response
is subsequently lost. Reconcile it with the same reference's per-document audits
(`entityType: CandidateDocument`, document `entityId`) and current document data
state using authorized read-only checks. The atomic **per-document audits remain
the source of truth for which copies were removed**; summary completion alone is
not permission to ignore nonzero failures or skips.

If no matching summary appears, classify the batch as **indeterminate**, keep the
next cleanup batch blocked and escalate to the deployment operator. A missing
summary may mean work is still running, or that it stopped before recording the
summary after committing some removals. Elapsed time, unchanged logs, the 120-second
proxy limit, or a guessed sum of read/transaction deadlines cannot distinguish
those cases: eligibility queries and audit work do not share that overall deadline.
Do not restart the backend or retry blindly to clear the 504. Resume only after
the operator has established completion or resolved the indeterminate operation
through the controlled incident/recovery procedure and reconciled committed audits
with the database. This runbook supplies no automatic timeout-based failure signal.

## Disable maintenance access

Only when all submitted batches have completed and been reconciled, or an
indeterminate operation has been resolved, set `ATS_DOCUMENT_STORAGE_CLEANUP_ENABLED=false` and
`ATS_DOCUMENT_STORAGE_OPERATIONS_ENABLED=false` in `.env.backend`, then run the
same backend recreation command. Merely editing the file leaves the running
process enabled. After it is healthy, send an authenticated admin POST to the same
cleanup endpoint, with CSRF and this deliberately invalid confirmation:

```json
{
  "confirmation": "CHECK CLEANUP IS DISABLED",
  "dryRun": true
}
```

Expect 404 when the cleanup/operations gates are disabled. A 400 means the endpoint
is still enabled and rejected the invalid confirmation: correct the configuration,
recreate the backend and repeat this harmless probe. Other statuses do not verify
disablement. Never reuse the last destructive request for this check; the probe
cannot remove bytes even if the configuration change failed.

Keep `ATS_DOCUMENT_STORAGE_ENABLED=true`
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
