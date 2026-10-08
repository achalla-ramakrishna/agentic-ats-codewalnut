# Private document storage

| | |
| --- | --- |
| **ID prefix** | DOCSTORE |
| **Status** | Opt-in; verify private-store smoke test before production enablement |
| **Related** | [candidate-profile-and-bgv.md](candidate-profile-and-bgv.md), [client-access.md](client-access.md), [ADR-0026](../adr/0026-private-document-storage.md), [ADR-0029](../adr/0029-temporary-intake-retention.md) |

## Requirements

| ID | Requirement |
| --- | --- |
| DOCSTORE-01 | Files use a private Vercel Blob store. Staff/client download authorization remains in the API before any provider read; ID permission, client isolation, explicit versions, revocation, session/CSRF and read-only View as apply. Finish authorization/metadata transactions before opening a separate download-audit transaction and before remote reads or retry waits, including AI and branded résumé source reads. Never expose storage credentials or storage references in API metadata. Responses remain private/no-store. |
| DOCSTORE-02 | Object keys contain only a type and opaque UUID. Immutable PUT retries succeed only for identical bytes. Validate uploads as before, cap content at 10 MiB, and verify SHA-256 and byte length after copying and when reading. Public stores, redirecting origins, oversized/corrupt provider responses fail closed. |
| DOCSTORE-03 | Accepted attached-document uploads persist legacy bytes and a BackgroundTask in the same transaction. A serial leased worker copies and verifies them outside database transactions. Failures retain the original bytes, retry with backoff, then remain visible as FAILED for operator retry. An enabled store never silently switches to public storage. |
| DOCSTORE-04 | Copy existing attached documents in bounded, resumable batches without changing IDs/shares. Prefer verified retained database bytes, reading Blob only after those bytes are absent. Migration never deletes legacy document bytes; later explicit cleanup follows DOCSTORE-06. Temporary bulk intakes stay database-only and are cleared on completion/failure; neither uploads nor migration may create intake objects. |
| DOCSTORE-05 | Storage is disabled by default. Operator status/migrate/retry endpoints require MANAGE_USERS and explicit operations enablement; writes require CSRF and refuse View as. GET status never queues or changes data. Audit migration counts without contents, credentials or contact details. |
| DOCSTORE-06 | Legacy document-byte cleanup is separately disabled by default, admin/session/CSRF protected and denied during View as. Require an exact confirmation, nonsecret backup reference, restore attestation and cutoff at least 14 days old. Default to dry-run; bound batches to 1–10 READY documents verified before the cutoff. Verify remote checksum/size outside transactions, then lock/recheck the manifest and current source bytes before clearing only data. Commit each destructive change with its audit; preserve bytes on failure and keep IDs, shares, manifests, schema, intake staging and Blob objects unchanged. Record dry-run/count audits without contents or provider references. Operational approval requires a successful Blob-only concurrent-load rehearsal on target resources before the first destructive production batch. No scheduled cleanup. |

## Behavior and limits

New uploads remain immediately available from durable database staging while copying.
READY means the remote copy has been verified, not that the local copy was removed.
A verified retained local copy is preferred, avoiding unnecessary remote reads and database connection waits. Explicit 503 read responses are retried at most twice with bounded backoff inside one 60-second complete-call deadline. Other failures fail closed; upload retries remain outbox-managed. Once a later cleanup
removes local bytes, an unavailable/corrupt Blob returns a generic 503.

The worker does not assume an empty database or replay business actions. Copying a
résumé does not run AI, move candidates, send messages or create new document versions.
Temporary intakes are never transferred. Pending INTAKE tasks from an earlier unreleased build are marked SKIPPED without provider calls; previously copied objects require a separate inventory and reviewed removal. Attached-document retention is separate from database-byte cleanup.

Transfers and reads currently buffer at most one 10 MiB file per operation. The bridge
caps concurrent operations at two. Test application concurrency on the target Droplet;
this change is not a claim of 2 GiB production capacity.

## Change log

| Date | Change |
| --- | --- |
| 2026-10-09 | Record download audits after authorization snapshots release their connections; preserve audit-before-read behavior and test concurrent staff/client downloads with a two-connection pool (DOCSTORE-01). |
| 2026-10-08 | Separate authorized database snapshots from provider reads so storage waits do not hold database connections (DOCSTORE-01). |
| 2026-10-08 | Make the post-disable check explicitly non-destructive and distinguish automatic busy retries from subsequent-request recovery after provider failures in the cleanup rehearsal (DOCSTORE-06). |
| 2026-10-08 | Keep temporary intakes database-only, prefer verified local reads and bound busy-read retries (DOCSTORE-02…04, ADR-0029). |
| 2026-10-08 | Require a measured Blob-only concurrent-load rehearsal before cleanup; clarify Compose flag sources/recreation and audit scope. Add interrupted-run fixture recovery, separate operations-gate, View-as-specific denial and real MySQL lock-contention regression coverage (DOCSTORE-06). |
| 2026-10-08 | Add separately gated cleanup after backup restoration and observation, with fresh verification and atomic audit (DOCSTORE-06; [runbook](../deploy-document-cleanup.md), [ADR-0028](../adr/0028-gated-legacy-document-cleanup.md)). |
| 2026-10-08 | Add opt-in private Blob bridge, durable transfers, authorized dual reads and resumable migration (DOCSTORE-01…05). |
