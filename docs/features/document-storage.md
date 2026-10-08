# Private document storage

| | |
| --- | --- |
| **ID prefix** | DOCSTORE |
| **Status** | Opt-in; verify private-store smoke test before production enablement |
| **Related** | [candidate-profile-and-bgv.md](candidate-profile-and-bgv.md), [client-access.md](client-access.md), [ADR-0026](../adr/0026-private-document-storage.md) |

## Requirements

| ID | Requirement |
| --- | --- |
| DOCSTORE-01 | Files use a private Vercel Blob store. Staff/client download authorization remains in the API before any provider read; ID permission, client isolation, explicit versions, revocation, session/CSRF and read-only View as apply. Never expose storage credentials or storage references in API metadata. Responses remain private/no-store. |
| DOCSTORE-02 | Object keys contain only a type and opaque UUID. Immutable PUT retries succeed only for identical bytes. Validate uploads as before, cap content at 10 MiB, and verify SHA-256 and byte length after copying and when reading. Public stores, redirecting origins, oversized/corrupt provider responses fail closed. |
| DOCSTORE-03 | Accepted uploads persist legacy bytes and a BackgroundTask in the same transaction. A serial leased worker copies and verifies them outside database transactions. Failures retain the original bytes, retry with backoff, then remain visible as FAILED for operator retry. An enabled store never silently switches to public storage. |
| DOCSTORE-04 | Copy existing documents and pending bulk intakes in bounded, resumable batches without changing IDs/shares. Dual reads support verified Blob objects and retained database bytes. This release never deletes legacy document bytes. Completed/failed intake staging keeps its existing lifecycle. |
| DOCSTORE-05 | Storage is disabled by default. Operator status/migrate/retry endpoints require MANAGE_USERS and explicit operations enablement; writes require CSRF and refuse View as. GET status never queues or changes data. Audit migration counts without contents, credentials or contact details. |

## Behavior and limits

New uploads remain immediately available from durable database staging while copying.
READY means the remote copy has been verified, not that the local copy was removed.
A verified retained local copy can serve during a Blob outage. Once a later cleanup
removes local bytes, an unavailable/corrupt Blob returns a generic 503.

The worker does not assume an empty database or replay business actions. Copying a
résumé does not run AI, move candidates, send messages or create new document versions.
Temporary intake blobs may remain after processing; do not delete them automatically
while migration/rollback is active. Storage retention is separate from database-byte cleanup.

Transfers and reads currently buffer at most one 10 MiB file per operation. The bridge
caps concurrent operations at two. Test application concurrency on the target Droplet;
this change is not a claim of 2 GiB production capacity.

## Change log

| Date | Change |
| --- | --- |
| 2026-10-08 | Add opt-in private Blob bridge, durable transfers, authorized dual reads and resumable migration (DOCSTORE-01…05). |
