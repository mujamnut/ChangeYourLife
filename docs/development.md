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

For local image and scanned-PDF extraction/OCR through LM Studio, start the LM Studio local server, then add this to `local.properties` and restart the backend:

```properties
LMSTUDIO_BASE_URL=http://127.0.0.1:1234
LMSTUDIO_VISION_MODEL=qwen/qwen3.5-9b
# LMSTUDIO_API_KEY=
# Legacy compatibility only; ignored by the Phase A/B runtime:
# LMSTUDIO_MODEL=qwen/qwen3.5-9b
```

LM Studio is not used for normal chat or action planning. `127.0.0.1` works only when the backend runs on the same computer as LM Studio. A backend deployed to Render cannot call your local LM Studio unless you expose LM Studio through a reachable URL.

For text chat and structured-action planning through OpenRouter, add this to `local.properties` and restart the backend:

```properties
OPENROUTER_API_KEY=sk-or-v1-your-key-here
OPENROUTER_MODEL=openai/gpt-oss-20b:free
# Legacy compatibility only; ignored by the Phase A/B runtime:
# OPENROUTER_VISION_MODELS=google/gemma-4-26b-a4b-it:free,google/gemma-3-4b-it:free
```

Routing Phases A and B route by provider role and attachment modality:

- plain text, local text-file extraction, selectable-text PDF extraction, and web-search context go directly to OpenRouter;
- direct images are read only by LM Studio, then the bounded extracted text is passed to OpenRouter for the final response/action;
- PDF text is extracted page by page; only scanned/blank/insufficient pages in scanned or mixed PDFs are rendered and sent to LM Studio for OCR;
- a request without visual input never probes or waits for LM Studio;
- direct images and rendered PDF pages are not a cross-role fallback to OpenRouter, and LM Studio is never a fallback for final text/action inference.

With OpenRouter configured, AI status is live; LM Studio may additionally provide visual extraction. With only LM Studio configured, status is degraded because the required final text/action provider is unavailable. With neither provider configured, the current local-compatibility behavior is sandbox mode. The existing backend API contracts remain unchanged, so Phases A and B require no Android/frontend change.

The legacy keys `LMSTUDIO_MODEL`, `OPENROUTER_VISION_MODELS`, `OPENROUTER_VISION_MODEL`, and `AI_VISION_MODEL` remain accepted by the configuration loader for compatibility but are ignored by Phase A/B routing. Do not rely on them to enable LM Studio text completion or OpenRouter vision.

### AI attachment and PDF policy

The authenticated AI request body is capped at 40 MiB with the Ktor 3.5.1 body-limit plugin. After JSON deserialization, the backend validates the actual inline payload and decoded aggregate instead of trusting the client-declared size.

The Phase B PDF limits are:

- at most three PDFs, 8 MiB decoded per PDF, and 24 MiB decoded across PDFs;
- at most 40 inspected pages per PDF;
- selectable text is extracted per page; only pages that need OCR are rendered;
- direct images and rendered PDF pages share a maximum of four visual frames, with at most three rendered pages from one PDF;
- each rendered page is a JPEG bounded to 1,280 pixels on its longest edge, two megapixels, and 512 KiB;
- local PDF processing uses one worker, a bounded queue, a 25-second batch cap, and checkpoints against the shared AI-job deadline;
- combined attachment evidence is capped at 48,000 characters; the visual prompt requests roughly 3,000 characters per page and accepted visual output is capped at approximately 12,000 characters total.

Encrypted, corrupt, and extraction-forbidden PDFs fail closed. Incomplete page coverage is reported as partial or failed rather than silently treated as complete. PDFBox still runs inside the backend process, so cancellation is best effort while a parser/render call is active; strict containment for hostile PDFs is deferred to a resource-limited subprocess in Phase C.

Attachment-derived text is inserted as untrusted evidence before the original latest user prompt. For requests containing attachments, every mutation must match a scope recovered deterministically from the original prompt. Only a root `CREATE_PAGE` may preserve model-derived attachment content after an exact scope match; extra, different, or unverifiable mutations are replaced by the recovered action or blocked. Only LM Studio receives direct images or rendered PDF pages; OpenRouter receives sanitized bounded text, and raw provider response bodies are excluded from downstream context and public errors.

### AI timeout policy

Remote AI work uses one shared deadline instead of restarting a full timeout for every provider or structured-output attempt. The default policy is:

```properties
AI_JOB_DEADLINE_MS=180000
AI_CONNECT_TIMEOUT_MS=5000
LMSTUDIO_REQUEST_TIMEOUT_MS=90000
OPENROUTER_REQUEST_TIMEOUT_MS=60000
AI_FINALIZATION_RESERVE_MS=10000
```

Before each remote request, the backend calculates `min(provider request limit, remaining job time - finalization reserve)`. LM Studio visual extraction, optional web search, the final OpenRouter request, and OpenRouter structured tool/JSON transport attempts therefore consume the same budget. `LMSTUDIO_REQUEST_TIMEOUT_MS` caps LM Studio vision/OCR requests only; `OPENROUTER_REQUEST_TIMEOUT_MS` caps OpenRouter text/action requests only. The backend also cancels an asynchronous HTTP future when this budget expires, which bounds the full response body even if a provider sends headers and then stalls. Once only the reserve remains, no new remote attempt starts. The reserve leaves time to validate and persist the result before the Android polling deadline.

This is not a hard process kill: local PDF work uses its own 25-second batch cap and shared-deadline checkpoints, while validation and persistence use the reserved margin. A PDFBox call already in progress cannot be forcibly isolated in-process. The configuration loader clamps the total deadline to 30–600 seconds, connect timeout to 1–30 seconds, provider caps to 5 seconds–the total deadline, and reserve to 1–60 seconds while keeping at least one second for work. It also accepts `LM_STUDIO_REQUEST_TIMEOUT_MS` as an alias for `LMSTUDIO_REQUEST_TIMEOUT_MS`. Restart the backend after changing any timeout value.

### Live AI action regression

Normal builds and tests never contact an AI provider. The deterministic provider-boundary corpus runs locally with recorded responses.

To validate the configured real model against the critical Malay prompt-to-action and multi-turn corpus, opt in explicitly:

```powershell
$env:CYL_RUN_LIVE_AI_REGRESSION="true"
$env:CYL_LIVE_AI_REGRESSION_ATTEMPTS="2"
.\gradlew.bat :backend:test --tests "com.changeyourlife.cyl.backend.AiLivePromptToActionRegressionTest"
Remove-Item Env:CYL_RUN_LIVE_AI_REGRESSION
Remove-Item Env:CYL_LIVE_AI_REGRESSION_ATTEMPTS
```

The live suite only plans and validates actions; it does not execute page mutations or require a database. It fails if prompt recovery produces the action instead of the configured model.

## Dependency Notes

- AGP 8.10.1 requires Gradle 8.11.1 and JDK 17+.
- AGP 8.x still uses the `org.jetbrains.kotlin.android` plugin for Android Kotlin support.
- Kotlin and the Compose compiler plugin are both pinned to 2.3.21.
- Hilt is pinned to 2.57.2 because Hilt 2.59+ requires AGP 9.
- Compose uses the BOM so Compose artifact versions stay aligned.
- Room 3 is still alpha, so CYL starts with Room 2.8.0 for the Android-only foundation.
- The backend uses Ktor 3.5.1, including `ktor-server-body-limit` for the authenticated AI payload ceiling.
- AGP can be upgraded later after Android Studio support is updated.
