# Production deployment and migration

The ATS may run its React frontend on Vercel and its API/MySQL on one DigitalOcean
Droplet. ADR-0027 describes the decision. The Railway deployment remains supported.

| ID | Requirement |
|---|---|
| DEPLOY-01 | Maintenance refuses every HTTP request, including GET, OAuth and webhooks, with 503 and Retry-After, except GET health. It also disables startup/background work. Health only proves process liveness. |
| DEPLOY-02 | Rehearsal forces maintenance and disables background work regardless of other flags. It refuses dev/demo profiles. Copied production data is never exercised with passwordless login or live provider credentials. |
| DEPLOY-03 | A disabled background-work flag prevents AI queue execution, code grading startup/events/sweep and built-in question startup writes. Async=false is not a pause switch. |
| DEPLOY-04 | The Droplet publishes only HTTPS/HTTP, keeps MySQL and the private storage bridge off public ports, limits container resources, and retains persistent data across image replacement. |
| DEPLOY-05 | Vercel proxies API and authentication on one public origin. The origin proxy overwrites forwarded identity with the configured public host and forbids caching API/provider responses. Proxy errors never log request objects or reflect request tokens. The company-owned Vercel project is CLI-only with no Git connection; previews use isolated data and credentials. |
| DEPLOY-06 | Deployment uses tested immutable image digests published from main through a protected GitHub environment, manual promotion, encrypted off-server backups and a rehearsed restore. Final cutover has one writer, preserved links and an explicit post-write rollback plan. |

Operations flags are read at restart, not live toggles. An HTTP freeze cannot cancel
already admitted requests or provider calls; stop and verify the old process is gone
before the final export. Flyway may still apply schema migrations in maintenance:
this is an application activity freeze, not a read-only database connection.

## Change log

- 2026-10-08: Add same-origin Vercel/Droplet deployment, rehearsal/maintenance controls,
  immutable image publication and the Railway migration runbook (DEPLOY-01–06).

- 2026-10-08: Verify forwarded-header handling and private upstream-failure logs against a real Caddy container (DEPLOY-05).

- 2026-10-08: Correct maintenance error envelopes and verify Spring filter/environment wiring; warn on open HTTP with paused workers and reject inconsistent Droplet cutover flags. Clarify CLI-only Vercel ownership/root, protect publication, and restrict backup credentials/SSH (DEPLOY-01–06).
