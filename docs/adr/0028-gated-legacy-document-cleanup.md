# ADR-0028: Explicit verified cleanup of legacy document bytes

- **Date:** 2026-10-08
- **Status:** Accepted
- **Extends:** [ADR-0026](0026-private-document-storage.md)

## Context

Private storage migration keeps database file bytes for rollback and temporary
provider outages. Removing that fallback is a separate destructive operation,
after the production hosting migration, observation period and recovery rehearsal.

## Decision

Keep cleanup disabled by default. An authenticated administrator with MANAGE_USERS
must enable both storage operations and the separate cleanup flag, supply the exact
confirmation phrase, attest a tested restore with a nonsecret backup reference,
and choose an observation cutoff at least fourteen days old. Dry-run is the default;
each explicit request examines at most ten documents (default one).

Only READY document manifests verified before the cutoff are eligible. Re-read
the immutable private object and verify its checksum/size outside a database
transaction. Then lock the document and manifest in a short transaction, compare
the entire captured manifest, verify the current database bytes, and set only the
legacy data column to NULL. The per-document cleanup audit commits in the same
transaction, so failure preserves both data and audit consistency. Any failure
retains the source; another cleanup request can safely retry.

Keep IDs, metadata, manifests, shares, schema, and remote objects unchanged. There
is no scheduled cleanup, generic deletion route or automatic disk compaction.
Intake staging and remote-object retention remain separate workflows.

## Consequences

The application cannot prove an operator actually restored a backup; its explicit
attestation and reference make that responsibility reviewable. Merging this code
or enabling Blob does not authorize production execution. The runbook requires a
coordinated database/manifest/object backup and tested restoration before cleanup.

Once bytes are removed, provider outages produce 503 instead of database fallback.
Rollback must use a Blob-aware application and retain all referenced objects.
Reverting the application commit cannot restore erased bytes. MySQL may reuse freed
space internally without shrinking its files; physical disk reclamation requires
a separately planned operation with space, locking and downtime evaluation.
