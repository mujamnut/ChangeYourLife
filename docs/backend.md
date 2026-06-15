# CYL Backend

## Runtime Modes

The backend can run without PostgreSQL for local development and tests. If `DATABASE_URL` is missing, auth uses an in-memory repository.

When `DATABASE_URL` is set, the backend:

- creates a HikariCP PostgreSQL connection pool
- runs Flyway migrations from `backend/src/main/resources/db/migration`
- stores users in PostgreSQL

## Environment

Copy `backend/.env.example` values into your deployment environment.

Required for persistent auth:

```txt
DATABASE_URL=postgresql://user:password@host:5432/db?sslmode=require
JWT_SECRET=replace-with-a-long-random-secret
```

Required for production forgot-password emails:

```txt
RESEND_API_KEY=re_your-key-here
EMAIL_FROM=ChangeYourLife <noreply@yourdomain.com>
```

`EMAIL_REPLY_TO` is optional. If the email provider is not configured, local in-memory development still returns a debug reset code, but production does not expose reset codes in API responses.

For local development, the backend also reads these values from the root `local.properties` file:

```properties
DATABASE_URL=postgresql://avnadmin:<password>@<host>:<port>/defaultdb?sslmode=require
JWT_SECRET=replace-with-a-long-random-secret
```

Aiven for PostgreSQL requires TLS for connections. Use `sslmode=require` unless you are configuring certificate verification separately.

## Current Endpoints

```txt
GET  /health
POST /auth/register
POST /auth/login
POST /auth/forgot-password
POST /auth/reset-password
GET  /auth/me
```

`/auth/me` requires:

```txt
Authorization: Bearer <token>
```

## Initial Schema

The first migration creates:

- `users`
- `workspaces`
- `pages`
- `tasks`
- `reminders`

The second migration creates:

- `password_reset_codes`
