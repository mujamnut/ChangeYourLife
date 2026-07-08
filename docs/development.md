# CYL Development Notes

## Prerequisites

- Android Studio installed.
- Android SDK platform 36 installed.
- JDK 17 or newer. This machine currently has Java 21.
- The included Gradle 8.11.1 wrapper for command-line builds.

## Android

Open the repository root in Android Studio and sync Gradle.

Useful commands after Gradle is available:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

The debug Android build points API calls to `http://10.0.2.2:8080/` by default, which maps the Android emulator to the host machine. For a physical phone, set this in `local.properties` using the computer's Wi-Fi/LAN IPv4 address:

```properties
cyl.api.base.url=http://10.142.211.43:8080/
```

Start the backend before testing register/login, keep that terminal open, and verify `http://127.0.0.1:8080/health` works on the host. When testing from a phone, also verify `http://10.142.211.43:8080/health` opens from the phone browser.

Reminder notifications require notification permission on Android 13+. The app requests it on launch; allow it when testing task reminders.

## Backend

Useful commands after Gradle is available:

```powershell
.\gradlew.bat :backend:test
.\gradlew.bat :backend:run
```

The backend defaults to port `8080`, or reads `PORT` from the environment.

Without `DATABASE_URL`, the backend uses in-memory auth storage for local development. Set `DATABASE_URL` and `JWT_SECRET` for persistent PostgreSQL-backed auth.

For Aiven PostgreSQL, copy the Service URI from Aiven Console and add it to `local.properties` with TLS enabled:

```properties
DATABASE_URL=postgresql://avnadmin:<password>@<host>:<port>/defaultdb?sslmode=require
JWT_SECRET=replace-with-a-long-random-secret
```

Restart `.\gradlew.bat :backend:run` after changing these values. A successful persistent setup logs `PostgreSQL connection pool initialized.` instead of the in-memory repository warning.

For local chat and image reading through LM Studio, start the LM Studio local server, then add this to `local.properties` and restart the backend:

```properties
LMSTUDIO_BASE_URL=http://127.0.0.1:1234
LMSTUDIO_MODEL=qwen/qwen3.5-9b
LMSTUDIO_VISION_MODEL=qwen/qwen3.5-9b
# LMSTUDIO_API_KEY=
```

`127.0.0.1` works only when the backend runs on the same computer as LM Studio. A backend deployed to Render cannot call your local LM Studio unless you expose LM Studio through a reachable URL.

For OpenRouter fallback, add this to `local.properties` and restart the backend:

```properties
OPENROUTER_API_KEY=sk-or-v1-your-key-here
OPENROUTER_MODEL=openai/gpt-oss-20b:free
OPENROUTER_VISION_MODELS=google/gemma-4-26b-a4b-it:free,google/gemma-3-4b-it:free,google/gemini-2.0-flash-exp:free
```

The backend uses LM Studio first for chat/action generation and image reading when `LMSTUDIO_BASE_URL` is present, then falls back to OpenRouter, Gemini, GLM, and sandbox mode.

## Dependency Notes

- AGP 8.10.1 requires Gradle 8.11.1 and JDK 17+.
- AGP 8.x still uses the `org.jetbrains.kotlin.android` plugin for Android Kotlin support.
- Kotlin and the Compose compiler plugin are both pinned to 2.3.21.
- Hilt is pinned to 2.57.2 because Hilt 2.59+ requires AGP 9.
- Compose uses the BOM so Compose artifact versions stay aligned.
- Room 3 is still alpha, so CYL starts with Room 2.8.0 for the Android-only foundation.
- AGP can be upgraded later after Android Studio support is updated.
