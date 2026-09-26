# Deploy to Railway

One Railway service runs the app (API + React UI from one Docker image), plus
Railway's MySQL. Railway rebuilds on every push to `main`.

## Quick start — preview with sample data (no Google setup)

1. **New Project → Deploy from GitHub repo →**
   `achalla-ramakrishna/agentic-ats-codewalnut`, branch `main`.
2. In the same project: **+ New → Database → Add MySQL**.
3. Click the **app** service (not MySQL) → **Variables** → **Raw Editor**,
   paste these three lines, and set your own access code:

   ```
   MYSQL_URL=${{MySQL.MYSQL_URL}}
   SPRING_PROFILES_ACTIVE=demo
   ATS_DEMO_ACCESS_CODE=choose-a-long-code-here
   ```

   `MySQL` must match your database service's name on the canvas (type `${{`
   and Railway suggests the right one). The access code needs 12+ characters.
4. App service → **Settings → Networking → Generate Domain**.
5. Wait for the deploy to turn green, open the domain, pick a sample user under
   **Preview login**, enter the access code.

That's it. Demo mode holds sample data only — don't add real candidates.

If a deploy fails: app service → **Deployments → View logs**. The last lines
say why (usually a typo in a variable). `https://<domain>/api/v1/health` shows
`{"status":"ok"}` when the app is up.

## Later — real sign-in with Google

When the app is ready for real use, replace demo mode with Google sign-in.

### Google Cloud Console

1. [console.cloud.google.com](https://console.cloud.google.com/) → create a
   project (e.g. "CodeWalnut ATS").
2. **APIs & Services → OAuth consent screen** → user type **External**
   (candidates use personal Gmail). App name, support email, and your Railway
   domain under authorised domains. Scopes: `openid`, `email`, `profile` only
   (non-sensitive, no Google review). Then **Publish app** — while it is in
   *Testing* only listed test users can sign in.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID** →
   **Web application** → authorised redirect URI:
   `https://<your-railway-domain>/login/oauth2/code/google`

### Railway variables (app service)

Remove `SPRING_PROFILES_ACTIVE` and `ATS_DEMO_ACCESS_CODE`, keep `MYSQL_URL`,
and add:

```
ATS_ALLOWED_DOMAINS=codewalnut.com
ATS_BOOTSTRAP_ADMINS=you@codewalnut.com
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<client id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<client secret>
```

- `ATS_ALLOWED_DOMAINS` — staff domains. Staff must be added by an Admin; any
  other Google account signs in as a candidate.
- `ATS_BOOTSTRAP_ADMINS` — becomes Admin on first sign-in and adds everyone
  else under **Users**.

Never set `SPRING_PROFILES_ACTIVE=dev` on Railway.

## Reference

- Instead of `MYSQL_URL` you can set `DB_URL` (JDBC form), `DB_USERNAME` and
  `DB_PASSWORD`; `DB_URL` wins if both are set.
- Flyway creates and upgrades the schema on each start.
- Health check: `/api/v1/health` (configured in `railway.json`).
