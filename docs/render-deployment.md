# Render Deployment

CYL backend can be deployed to Render as a Docker web service. The deployed backend connects to Aiven PostgreSQL through `DATABASE_URL`.

## Prerequisites

- The project is pushed to GitHub.
- Aiven PostgreSQL is running.
- You have the Aiven Service URI.

## Render Setup

1. Open Render and create a new **Blueprint** from this repository, or create a new **Web Service** using Docker.
2. If using Blueprint, Render reads `render.yaml`.
3. If using manual Web Service setup:
   - Runtime: Docker
   - Dockerfile path: `./Dockerfile`
   - Plan: Free
   - Health check path: `/health`

## Environment Variables

Set these in Render:

```txt
DATABASE_URL=postgresql://avnadmin:<password>@<host>:<port>/defaultdb?sslmode=require
JWT_SECRET=replace-with-a-long-random-secret
OPENROUTER_API_KEY=sk-or-v1-your-key-here
OPENROUTER_MODEL=openai/gpt-oss-20b:free
RESEND_API_KEY=re_your-key-here
EMAIL_FROM=ChangeYourLife <noreply@yourdomain.com>
KTOR_DEVELOPMENT=false
DATABASE_MAX_POOL_SIZE=5
```

`OPENROUTER_API_KEY` is optional, but AI will fall back to sandbox mode when no AI key is configured.
`RESEND_API_KEY` and `EMAIL_FROM` are required for production forgot-password emails. In Resend, verify your sending domain first, then use a sender from that domain.

## Android App Setup

After Render deploys, copy the public backend URL and update root `local.properties`:

```properties
cyl.api.base.url=https://cyl-backend.onrender.com/
```

Then rebuild and reinstall the Android app.

## Verify

Open:

```txt
https://cyl-backend.onrender.com/health
```

The backend logs should show:

```txt
PostgreSQL connection pool initialized.
```

If Render logs show the in-memory repository warning, `DATABASE_URL` is missing or invalid.
