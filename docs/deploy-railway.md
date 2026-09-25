# Deploy to Railway

One Railway service runs the app (API + React UI from one Docker image), plus
Railway's MySQL. Railway builds from GitHub on every push to the chosen branch.

## 1. Create the project

1. In Railway: **New Project → Deploy from GitHub repo →**
   `achalla-ramakrishna/agentic-ats-codewalnut`. Pick the branch to deploy
   (`main`, or a feature branch for a preview).
2. Railway finds `railway.json` and builds the root `Dockerfile`.
3. In the same project: **New → Database → MySQL**.

## 2. Set the app service's variables

In the app service → **Variables** (the `${{MySQL.…}}` references link to the
MySQL service; rename `MySQL` if your database service has another name):

| Variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8` |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `ATS_ALLOWED_DOMAINS` | `codewalnut.com` — the *staff* domains; any other Google account signs in as a candidate |
| `ATS_BOOTSTRAP_ADMINS` | the email(s) that should become Admin on first sign-in |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | from step 3 |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | from step 3 |

Do **not** set `SPRING_PROFILES_ACTIVE=dev` on Railway: the dev profile adds a
password-less login and fake users.

Then **Settings → Networking → Generate Domain** to get a public URL such as
`https://ats-production-xxxx.up.railway.app`.

## 3a. Preview without Google (demo mode) — optional, for now

To try the app before Google sign-in is set up, add these to the app service
instead of the Google variables:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo` |
| `ATS_DEMO_ACCESS_CODE` | a long random code (12+ characters) you share with the team |

The login page then shows **Preview login**: pick a sample user (Admin,
Recruiter, … or a candidate) and enter the access code. Wrong codes are
refused and audited; the app won't start in demo mode without a code.
Demo mode seeds fake users only — **don't put real candidate data in a demo**.

When Google is ready: remove both variables, add the Google ones (step 3).

## 3. Google sign-in

1. Google Cloud Console → **APIs & Services → Credentials → Create
   credentials → OAuth client ID** → type **Web application**.
2. **Authorised redirect URI**:
   `https://<your-railway-domain>/login/oauth2/code/google`
3. OAuth consent screen: **User type: External**, because candidates sign in
   with personal Gmail accounts. Fill in the app name, support email, and the
   Railway domain under authorised domains. Scopes: just `openid`, `email`,
   `profile` (non-sensitive, so no Google verification review). Then
   **Publish app** (move it from *Testing* to *In production*), otherwise
   only listed test users can sign in. Staff access is still limited by the
   app: only provisioned `codewalnut.com` users get staff sessions.
4. Copy the client ID and secret into the variables above; Railway redeploys.

## 4. Check it

- `https://<domain>/api/v1/health` → `{"status":"ok"}`
- Open `https://<domain>/`, **Sign in with Google** with a bootstrap-admin
  email → you land on the dashboard as Admin; add colleagues under **Users**.

Flyway creates the schema on first start. Logs: service → **Deployments →
View logs**.
