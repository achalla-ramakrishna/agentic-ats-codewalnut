# ADR-0027: Split frontend hosting and retain one application origin

- Status: Accepted
- Date: 2026-10-08

## Context

The live Railway installation holds hiring records and binary documents. We want
Vercel frontend hosting and a small DigitalOcean Droplet running Spring Boot and
MySQL, with private object storage introduced independently. Sessions and CSRF
currently rely on one browser origin. Copied databases contain pending work that
startup handlers would resume without an operational pause.

## Decision

Use Vercel external rewrites to a TLS origin served by Caddy. Caddy permits only
backend paths, removes caller-supplied forwarded identity and writes a configured
public HTTPS host; backend and database ports are not published. Dynamic responses
are private/no-store at both proxy layers. No browser CORS or cross-site cookies
are introduced. Generated Vercel configuration is specific to a single project;
Git auto-deployments are disabled and previews require isolated origins/data.

Run MySQL 8 and the backend in persistent Docker Compose services. Build on CI,
promote immutable image digests manually, cap heap/pools/concurrency and validate
2 GB sizing with representative load. Coding sandboxes remain on another host.

Restart-scoped maintenance blocks all application HTTP, including mutating GETs,
and pauses startup/queued work. Rehearsal enforces both controls and prohibits
passwordless profiles. Flyway is explicitly outside this application freeze.

Migrate private document storage on Railway first, retain compatible rollback
code, rehearse a consistent database restore, then perform a planned final freeze.
Only one installation may accept writes or execute work. After new writes, rollback
requires preserving and transferring the current state, not just changing DNS.

## Consequences

One Droplet is a single point of failure and requires operator patching, capacity
monitoring and tested encrypted off-server backups. 2 GB is a trial sizing, not an
availability or throughput guarantee. Proxy upload/timeout limits and Google
cookies/redirects must be tested on real hosts. The original all-in-one Dockerfile
remains for Railway. Storage cleanup is delayed and independently reviewed.
