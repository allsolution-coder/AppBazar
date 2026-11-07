# Deploying AppBazaar for free

This puts the whole site online on free plans. You need four free accounts and no credit card.

| Part | Where | Why |
|---|---|---|
| Backend (Spring Boot) | Render, Web Service | Runs the Docker image from `backend/Dockerfile` |
| Frontend (React) | Render, Static Site | Serves the built site |
| Database | Neon | Free PostgreSQL that does not expire |
| Product files | Supabase Storage | Free S3-compatible storage. Free servers lose files saved on their own disk |
| Login code emails | Brevo | Render's free plan blocks SMTP, so email is sent through Brevo's HTTPS API |

Free plans change, so check each pricing page before you start. Render's free backend goes to sleep after about 15 minutes without visitors, and the first visit afterwards takes about a minute.

## 1. Database (Neon)
1. Create a project at neon.tech and copy its connection string. It looks like `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`.
2. Turn it into the three values the backend needs:
   - `DB_URL` = `jdbc:postgresql://HOST/DBNAME?sslmode=require`
   - `DB_USER` = `USER`
   - `DB_PASS` = `PASSWORD`

## 2. File storage (Supabase)
1. Create a project at supabase.com. Open **Storage**, create a **private** bucket named `products`.
2. Open the storage **S3 connection** settings. Copy the **endpoint** and **region**, and create an **S3 access key**. You get an access key id and a secret.
3. You will set `S3_BUCKET=products`, `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`.
4. Supabase's free plan allows uploads up to 50 MB, which matches the limit in this app.

## 3. Login emails (Brevo)
1. Create a free account at brevo.com.
2. Add a **sender** (Senders, Domains and dedicated IPs, then Senders) with your own email address and click the verification link Brevo emails you.
3. Create an **API key** (SMTP and API, then API keys).
4. You will set `BREVO_API_KEY` and `MAIL_FROM` (the verified sender address).

## 4. Backend (Render)
1. Push this repo to GitHub. Create a **Web Service** on render.com from the repo.
2. Settings: **Root Directory** `backend`, **Runtime** Docker, **Instance type** Free.
3. Add these environment variables:

| Variable | Value |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | From step 1 |
| `JWT_SECRET` | A long random text (40 or more characters). Never share it |
| `ADMIN_PASSWORD` | A strong password for the admin account |
| `S3_BUCKET`, `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | From step 2 |
| `BREVO_API_KEY`, `MAIL_FROM` | From step 3 |
| `CORS_ORIGIN`, `FRONTEND_URL` | The frontend address from step 5 (set after step 5, then redeploy) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_SECRET` | Optional. PayPal sandbox keys. Without them payments stay in demo mode |
| `SEED_DEMO_DATA` | `true` for sample products, `false` for an empty marketplace |

4. Deploy. When it is live, open `https://YOUR-BACKEND.onrender.com/api/products`. You should see JSON.

## 5. Frontend (Render)
1. Create a **Static Site** from the same repo. **Root Directory** `frontend`, **Build Command** `npm ci && npm run build`, **Publish Directory** `dist`.
2. Add the environment variable `VITE_API_URL` = your backend address from step 4 (no slash at the end).
3. Under **Redirects/Rewrites** add a **Rewrite**: source `/*`, destination `/index.html`. Without it, refreshing any page except the home page shows an error.
4. Deploy. Copy the site address, put it into `CORS_ORIGIN` and `FRONTEND_URL` on the backend, and redeploy the backend.

## 6. Check it
Open the site, enter an email, and you should get a login code by email. Log in as admin with `admin@appbazaar.com` and your `ADMIN_PASSWORD` on the hidden `/staff` page (for example `https://YOUR-SITE.onrender.com/staff`).

## Notes
- Anyone who gets your `JWT_SECRET`, database password, or Brevo/Supabase/PayPal secrets can act as your site. Keep them only in Render's environment settings, never in the repo.
- Before real money, use PayPal live credentials (`PAYPAL_BASE=https://api-m.paypal.com`) and add webhooks. Check PayPal's rules for your country first.
