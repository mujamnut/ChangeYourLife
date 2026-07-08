# ChangeYourLife (CYL)

ChangeYourLife is an Android-first productivity app inspired by Notion, built around notes, tasks, workspaces, offline storage, and later AI-assisted planning.

## Repository Layout

```txt
.
|-- androidApp/   Android app built with Kotlin and Jetpack Compose
|-- backend/      Ktor backend service
|-- docs/         Technical notes and implementation planning
|-- gradle/       Gradle version catalog
`-- notion_like_app_roadmap.md
```

## Current Foundation

- Android app module with Compose, Navigation, Hilt, and Room dependencies.
- Ktor backend module with JSON serialization, logging, status handling, auth routes, and a health route.
- Backend PostgreSQL wiring through HikariCP and Flyway migrations.
- Android authentication flow with register, login, session persistence, auth-gated navigation, and logout.
- Workspace system with create/switch support, active workspace persistence, workspace-scoped notes/tasks, basic subpages, and subpage properties.
- Standalone task screens have been removed; task-like workflows are being folded into pages, tables, and blocks.
- Block editor MVP with text, heading, todo, bullet, quote, divider, and database table blocks.
- Database table block supports editable title, columns, rows, cells, add/delete controls, and Table/List/Board/Calendar/Gallery/Timeline/Dashboard views.
- Subpages are capped at one nested level in the UI for now, so subpages do not show another subpage creation button.
- Retrofit API client configured through `local.properties`, defaulting to the Android emulator backend at `http://10.0.2.2:8080/`.
- Shared Gradle version catalog for dependency management.
- Local Android SDK configured through `local.properties`.

## First Commands

Use Android Studio to sync the project, or run the included Gradle wrapper:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :backend:test
.\gradlew.bat :backend:run
```

The Android build uses AGP 8.10.1 with Gradle 8.11.1 for Android Studio compatibility.

The backend health endpoint will be:

```txt
GET http://localhost:8080/health
```

Backend auth endpoints are documented in `docs/backend.md`.

For local auth testing, start the backend first with `.\gradlew.bat :backend:run`, then run the Android app from Android Studio. For a physical phone, set `cyl.api.base.url=http://<your-computer-ip>:8080/` in `local.properties`.

For persistent auth/data with Aiven PostgreSQL, set `DATABASE_URL=postgresql://avnadmin:<password>@<host>:<port>/defaultdb?sslmode=require` and `JWT_SECRET` in `local.properties`, then restart the backend.

For cloud backend hosting, deploy the backend to Render using the root `Dockerfile` or `render.yaml`. See `docs/render-deployment.md`.

For AI via OpenRouter, set `OPENROUTER_API_KEY` in `local.properties`. The backend defaults to `OPENROUTER_MODEL=openai/gpt-oss-20b:free` when OpenRouter is configured. Image attachments use `OPENROUTER_VISION_MODEL` and default to `google/gemma-3-4b-it`, which supports image input.
