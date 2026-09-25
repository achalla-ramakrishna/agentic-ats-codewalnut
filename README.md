# CodeWalnut ATS

Applicant tracking system for CodeWalnut — one place to run every hire,
for CodeWalnut and for its clients, from requisition to signed offer or
placement.

Stack: Spring Boot 3 (Java 21) + React/TypeScript (Vite) + MySQL 8.
Status: **chunk 0 (foundations)** — sign-in, roles, users, audit log.

- [`docs/SPEC.md`](docs/SPEC.md) — overview: goals, roles, pipeline, non-functional requirements, build plan
- [`docs/features/`](docs/features/README.md) — one requirements file per feature, with stable requirement IDs
- [`docs/architecture.md`](docs/architecture.md) — architecture, modules, data model, integrations
- [`docs/adr/`](docs/adr/) — architecture decision records
- [`AGENTS.md`](AGENTS.md) — house rules for humans and coding agents

## Run it locally

Needs JDK 21, Node 22 and MySQL 8.

```bash
# once: databases and user
mysql -uroot -e "CREATE DATABASE ats; CREATE DATABASE ats_test;
  CREATE USER 'ats'@'localhost' IDENTIFIED BY 'ats';
  GRANT ALL ON ats.* TO 'ats'@'localhost'; GRANT ALL ON ats_test.* TO 'ats'@'localhost';"

cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # API on :8080
cd frontend && npm install && npm run dev                           # app on :5173
```

Open http://localhost:5173 and use **Dev login** to sign in as any seeded role
(admin, recruiter, hiring manager, account manager, interviewer, approver, or
a multi-role lead). Google sign-in needs the variables in `.env.example`.

## Deploy

One Docker image (React UI + API) plus MySQL. Railway steps:
[`docs/deploy-railway.md`](docs/deploy-railway.md).
