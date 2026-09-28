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
GOOGLE_CLIENT_ID=<client id>
GOOGLE_CLIENT_SECRET=<client secret>
```

- `ATS_ALLOWED_DOMAINS` — staff domains. Staff must be added by an Admin; any
  other Google account signs in as a candidate.
- `ATS_BOOTSTRAP_ADMINS` — becomes Admin on first sign-in and adds everyone
  else under **Users**.

Never set `SPRING_PROFILES_ACTIVE=dev` on Railway.

### Check it worked

- Deploy logs show `Google sign-in: ENABLED` (if they say `DISABLED`, the two
  `GOOGLE_…` variables are missing or misspelt on the **app** service).
- The login page shows **Continue with Google**.
- If Google shows `redirect_uri_mismatch`, or you land back on the login page
  with an error, the login page prints the exact redirect URI to register —
  copy it into the Google client's **Authorised redirect URIs**.

## Troubleshooting (lessons from the first deploy)

| Symptom | Cause | Fix |
| --- | --- | --- |
| Variables changed but nothing happens | Railway **stages** variable edits ("Apply N changes") | Click the purple **Deploy** button |
| Deployment Details still shows old variables | That tab shows the *running* deployment's snapshot | Wait for the new deployment to become Active |
| Deploy fails right after editing variables | `MYSQL_URL` got deleted in the Raw Editor | Add `MYSQL_URL=${{MySQL.MYSQL_URL}}` back |
| Log says `Google sign-in: DISABLED` | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` missing on the **app** service | Add them, Deploy |
| Google: `invalid_client` / "OAuth client was not found" | Client ID mistyped, or client in another Google Cloud project | Use Google's **copy** button — never retype the ID |
| Secret contains `","javascript_origins"…` | Pasted from the downloaded JSON file | Paste only the `GOCSPX-…` value |
| Google: `redirect_uri_mismatch` | Redirect URI not registered exactly | Add `https://<domain>/login/oauth2/code/google` (no trailing slash); wait a few minutes |
| "Sign-in was refused" for a colleague | Staff must be added first | Admin adds them under **Users** |

## Reference

- Instead of `MYSQL_URL` you can set `DB_URL` (JDBC form), `DB_USERNAME` and
  `DB_PASSWORD`; `DB_URL` wins if both are set.
- Flyway creates and upgrades the schema on each start.
- Health check: `/api/v1/health` (configured in `railway.json`).
