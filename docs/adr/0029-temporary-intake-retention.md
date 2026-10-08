# ADR-0029: Keep temporary résumé intakes out of object storage

- **Date:** 2026-10-08
- **Status:** Accepted
- **Supersedes:** ADR-0026 only for temporary intake transfer/retention and read preference.

## Context

Bulk résumé uploads have a shorter lifecycle than attached candidate documents.
The processor clears intake bytes on both success and failure, including failures
that never create a candidate. Copying those bytes to immutable Blob objects would
retain personal data beyond that lifecycle without a corresponding deletion path.

## Decision

Only attached candidate documents enter the private-storage outbox and migration.
Temporary intakes remain in MySQL, preserving the existing clear-after-processing
behavior. Successfully attached documents receive their own durable transfer task.
Reject new INTAKE enqueue calls and skip previously queued intake tasks without a
provider call. Preserve the enum for reading older manifests; do not mutate applied
schema migrations. If an earlier unreleased build created intake objects, inventory
and review their removal separately, including backup and rollback implications.

While a candidate document has retained database bytes, verify them against its
READY manifest and use them without contacting Blob. After cleanup, validate Blob
reads as before. Retry explicit transient 503 reads at most twice, inside one
complete-call deadline, and mark failed AI source reads FAILED so a person can retry.
Do not retry authorization errors, redirects or invalid content. Upload retries
remain durable background tasks. The bridge's bounded concurrency remains unchanged.

## Consequences

Normal reads before cleanup no longer double-fetch files or compete with migration
for bridge slots. They also no longer prove live Blob availability: independent
read-back checks and a Blob-only concurrency rehearsal are required before cleanup.
Temporary intake bytes still belong in encrypted database backups until cleared;
backup retention is a separate lifecycle from live-table processing.
