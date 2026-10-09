# ADR-0030: Keep production Vercel hosting disconnected from Git

- Status: Accepted
- Date: 2026-10-08
- Supersedes: ADR-0027 only for the mechanism preventing Vercel Git deployments.

## Context

ADR-0027 says Git auto-deployments are disabled. Deployment configuration is generated
locally for an explicit origin and ignored by Git. A Vercel Git integration would
therefore build a commit without the required routing configuration; the generated
file cannot disable a build which never receives it.

## Decision

Use a CLI-only Vercel project owned by the company's Pro team, with **no connected
Git repository**. Before each release, verify this in the Vercel dashboard together
with the intended team/project. This is an operator-enforced platform configuration,
not a setting in the generated file. Do not import or connect the Git repository to
this production project.

Generate routing before upload and upload `frontend/` with `vercel --cwd frontend`.
The project Root Directory setting is blank because `frontend/` is already the
uploaded root. Previews require separate projects, data, provider credentials and
origins. The company team also owns the private Blob stores.

Keep maintenance enabled while checking the proxied health endpoint and frontend
static routing. Authenticated checks happen only after the planned reopening; all
backend endpoints except GET health return 503 during maintenance. Other decisions
in ADR-0027, including same-origin cookies, private services, immutable images and a
single-writer migration, remain in force.

## Consequences

There are no Git-triggered releases. Operators must include the generated local
configuration in every explicit upload and verify the deployment through the public
hostname. Reconnecting Git changes this decision and requires a reviewed deployment
workflow with configuration present in Git builds. Follow the current
[cutover runbook](../deploy-digitalocean.md), not the superseded linkage mechanism.
