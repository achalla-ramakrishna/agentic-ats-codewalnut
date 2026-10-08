# Private document storage

| | |
| --- | --- |
| **ID prefix** | DOCSTORE |
| **Status** | Opt-in; verify private-store smoke test before production enablement |
| **Related** | [candidate-profile-and-bgv.md](candidate-profile-and-bgv.md), [client-access.md](client-access.md), [ADR-0026](../adr/0026-private-document-storage.md), [ADR-0029](../adr/0029-temporary-intake-retention.md) |

## Requirements

| ID | Requirement |
| --- | --- |
| DOCSTORE-01 | Files use a private Vercel Blob store. Staff/client download authorization remains in the API before any provider read; ID permission, client isolation, explicit versions, revocation, session/CSRF and read-only View as apply. Never expose storage credentials or storage references in API metadata. Responses remain private/no-store. |
| DOCSTORE-02 | Object keys contain only a type and opaque UUID. Immutable PUT retries succeed only for identical bytes. Validate uploads as before, cap content at 10 MiB, and verify SHA-256 and byte length after copying and when reading. Public stores, redirecting origins, oversized/corrupt provider responses fail closed. |
| DOCSTORE-03 | Accepted attached-document uploads persist legacy bytes and a BackgroundTask in the same transaction. A serial leased worker copies and verifies them outside database transactions. Failures retain the original bytes, retry with backoff, then remain visible as FAILED for operator retry. An enabled store never silently switches to public storage. |
| DOCSTORE-04 | Copy existing attached documents in bounded, resumable batches without changing IDs/shares. Prefer verified retained database bytes, reading Blob only after those bytes are absent. This release never deletes legacy document bytes. Temporary bulk intakes stay database-only and are cleared on completion/failure; neither uploads nor migration may create intake objects. |
| DOCSTORE-05 | Storage is disabled by default. Operator status/migrate/retry endpoints require MANAGE_USERS and explicit operations enablement; writes require CSRF and refuse View as. GET status never queues or changes data. Audit migration counts without contents, credentials or contact details. |

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
| 2026-10-08 | Keep temporary intakes database-only, prefer verified local reads and bound busy-read retries (DOCSTORE-02…04, ADR-0029). |
| 2026-10-08 | Add opt-in private Blob bridge, durable transfers, authorized dual reads and resumable migration (DOCSTORE-01…05). |
