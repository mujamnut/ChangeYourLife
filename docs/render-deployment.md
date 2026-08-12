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
LMSTUDIO_BASE_URL=https://lmstudio.yourdomain.com
LMSTUDIO_MODEL=qwen/qwen3.5-9b
LMSTUDIO_VISION_MODEL=qwen/qwen3.5-9b
OPENROUTER_API_KEY=sk-or-v1-your-key-here
OPENROUTER_MODEL=openai/gpt-oss-20b:free
OPENROUTER_VISION_MODELS=google/gemma-4-26b-a4b-it:free,google/gemma-3-4b-it:free
AI_JOB_DEADLINE_MS=180000
AI_CONNECT_TIMEOUT_MS=5000
LMSTUDIO_REQUEST_TIMEOUT_MS=90000
OPENROUTER_REQUEST_TIMEOUT_MS=60000
AI_FINALIZATION_RESERVE_MS=10000
RESEND_API_KEY=re_your-key-here
EMAIL_FROM=ChangeYourLife <noreply@yourdomain.com>
KTOR_DEVELOPMENT=false
DATABASE_MAX_POOL_SIZE=5
```

LM Studio local chat/image reading is not available from Render with `http://127.0.0.1:1234`, because that address points to the Render container, not your PC. Only set `LMSTUDIO_BASE_URL` on Render if you expose LM Studio through a reachable HTTPS URL, for example Cloudflare Tunnel.

`OPENROUTER_API_KEY` is optional. AI uses sandbox mode only when neither OpenRouter nor an LM Studio base URL is configured.
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
