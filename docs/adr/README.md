# Architecture decision records

Accepted records are historical and are not rewritten. When decisions evolve, read
the superseding record as well as the original. Feature requirements and deployment
runbooks describe current behavior.

## Partial supersessions for the hosting migration

| Original | Current decision | Scope |
| --- | --- | --- |
| [ADR-0026](0026-private-document-storage.md) | [ADR-0029](0029-temporary-intake-retention.md) | Temporary résumé intakes stay database-only; verified retained bytes are preferred for reads. The earlier intake-object retention decision no longer applies. |

Other numbered files in this directory document their decisions and status individually.
