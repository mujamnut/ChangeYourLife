## AI Fallback Audit

Audit date: 2026-08-11

Scope: backend AI completion, structured-action, vision, asynchronous job, fallback, retry, and observability flow after the direct Gemini and GLM integrations were removed.

This document is an architecture review. Implementation status is called out where a recommendation has since been completed.

## Executive summary

The current implementation is a good development and personal-use foundation, but it is not yet production-grade resilience.

The configured completion order is:

1. LM Studio
2. OpenRouter
3. Sandbox/mock only when no live provider is configured

Sandbox is not a runtime fallback when configured providers fail. If LM Studio and OpenRouter are configured and both fail, the live request fails. This is safer than silently returning a simulated answer, but sandbox should still be explicitly disabled in production.

The shared AI remote-work deadline described below was implemented on 2026-08-11. The most important remaining production gaps are:

- no typed error classification for deciding retry, fallback, or fail-fast behavior;
- no `Retry-After` support, exponential backoff with jitter, or retry budget;
- no circuit breaker or concurrency bulkhead per provider;
- vision does not fall back to OpenRouter when LM Studio is configured but fails;
- the default OpenRouter completion and vision models use `:free` variants;
- status and audit attribution can report the configured primary provider instead of the provider that actually answered;
- structured-output capabilities and privacy routing are not explicitly constrained for OpenRouter;
- provider error bodies can flow into internal job errors and, in the basic chat path, user-visible text;
- cross-provider failure behavior has little dedicated automated test coverage.

## Current implementation

### Completion provider selection

`AiService` builds an ordered list containing LM Studio when `LMSTUDIO_BASE_URL` is present and OpenRouter when `OPENROUTER_API_KEY` is present.

Relevant code:

- [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `completionEndpoints`
- [`AppConfig.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/config/AppConfig.kt), provider environment settings
- [`Application.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/Application.kt), service wiring

The current defaults are:

- LM Studio completion: `qwen/qwen3.5-9b`
- OpenRouter completion: `openai/gpt-oss-20b:free`
- LM Studio vision: `qwen/qwen3.5-9b`
- OpenRouter vision: `google/gemma-4-26b-a4b-it:free`, then `google/gemma-3-4b-it:free`

Gemini and GLM are no longer present as direct providers or fallback models.

### Basic chat flow

Normal chat calls each configured completion endpoint sequentially. An empty result or exception causes the next endpoint to be attempted. When every endpoint fails, an aggregate exception is created.

The public `chat()` method catches that exception and converts it to a text response containing the exception message. This avoids crashing the route but is not a typed or sanitized production error contract.

### Structured action flow

For page/database actions, the backend prepares context and attempts these response transports for each provider:

1. tool/function call;
2. JSON Schema response;
3. JSON object response.

The result is then normalized, schema-validated, recovered when possible, and passed through the retrieval/action boundary before being returned. This layered validation is one of the strongest parts of the current design.

Relevant code:

- [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `chatWithActions`, `chatCompletionsForActions`, and `sendStructuredActionCompletion`
- [`AiActionSchemaValidator.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiActionSchemaValidator.kt)
- [`AiRetrievalActionBoundary.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiRetrievalActionBoundary.kt)

### Vision flow

Vision has per-model fallback within LM Studio and within OpenRouter. A retryable HTTP status can be attempted twice with a fixed delay.

However, when `LMSTUDIO_BASE_URL` is configured, a failed LM Studio vision result is returned immediately. OpenRouter vision is only considered when LM Studio is absent. Therefore the effective vision flow is currently:

```text
LM Studio configured -> try LM Studio models -> success or stop
LM Studio absent     -> try OpenRouter models -> success or fail
```

It is not currently:

```text
LM Studio -> OpenRouter -> fail
```

Relevant code: [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `analyzeImagesWithVisionFallback`.

### Asynchronous job and client polling flow

Android creates an asynchronous action job using an idempotency key, then polls until the job succeeds, fails, or reaches the client timeout.

Backend strengths:

- request fingerprint and idempotency-key claim;
- duplicate requests return the existing job;
- jobs are persisted when PostgreSQL is configured;
- active jobs are marked interrupted after a backend restart;
- job phases and attachment diagnostics are stored.

Current constraints:

- the Android polling budget is 10 minutes;
- AI remote work shares a 180-second deadline by default, with provider caps and a 10-second finalization reserve;
- the job service launches work on an unbounded `Dispatchers.IO` scope without a per-provider concurrency limit;
- the job scope is owned internally rather than being explicitly tied to application shutdown lifecycle.

Relevant code:

- [`AiJobService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiJobService.kt)
- [`AiRoutes.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/routes/AiRoutes.kt)
- [`AiRepositoryImpl.kt`](../androidApp/src/main/java/com/changeyourlife/cyl/data/repository/AiRepositoryImpl.kt)

## What is already good

The following behavior should be preserved:

- explicit primary and secondary provider order;
- sequential rather than parallel/hedged provider calls, which avoids duplicate inference cost;
- asynchronous action jobs instead of holding an Android HTTP request open for the full inference duration;
- idempotency at job creation and action execution boundaries;
- structured tool/JSON transport fallback;
- schema validation and normalization before action execution;
- retrieval privacy and target boundaries;
- destructive-action confirmation policy;
- bounded attachment sizes and image optimization;
- per-model vision attempts and basic diagnostics;
- mock mode when developing without credentials;
- OpenRouter's own upstream provider routing as a second resilience layer once a request reaches OpenRouter.

## Findings and recommendations

### P0: Add one total deadline budget

Status: implemented on 2026-08-11.

The backend now starts one monotonic remote-work deadline for each chat/action operation. Attachment vision, optional web search, completion-provider fallback, vision retries, and structured tool/JSON transport attempts all derive their request timeout from the same remaining budget. Each asynchronous HTTP future is awaited only for that budget and cancelled on expiry, so a response body that stalls after sending headers cannot bypass the limit. Deadline exhaustion is preserved as a typed internal exception so fallback loops stop instead of resetting the timeout.

Default policy:

- total job deadline: 180 seconds;
- LM Studio request cap: 90 seconds;
- OpenRouter request cap: 60 seconds;
- connection timeout: 5 seconds;
- finalization reserve: 10 seconds.

The effective timeout for a remote attempt is `min(provider cap, remaining deadline - finalization reserve)`. No new remote request or vision retry delay starts once the reserve boundary is reached. The values are configurable with `AI_JOB_DEADLINE_MS`, `AI_CONNECT_TIMEOUT_MS`, `LMSTUDIO_REQUEST_TIMEOUT_MS`, `OPENROUTER_REQUEST_TIMEOUT_MS`, and `AI_FINALIZATION_RESERVE_MS`.

This is not a hard cancellation boundary for local parsing, validation, or persistence already in progress; the 10-second reserve is the completion margin for those steps. A strict end-to-end wall-clock SLA would additionally need cancellation enforcement at job orchestration and persistence boundaries.

The 180-second starting value should still be benchmarked against actual LM Studio cold-start and p95 generation latency. Keep the configured deadline below the client polling deadline with enough margin to validate, persist, and return the result.

### P0: Classify errors before retrying or falling back

The current completion loop treats most exceptions as equivalent. Structured transport treats a broad group of HTTP responses as capability rejection, including generic `400` responses that may actually represent an invalid prompt or context-length problem.

Recommended starting policy:

| Failure | Same-provider retry | Next-provider fallback | Action |
|---|---:|---:|---|
| connection reset/temporary DNS/read timeout | once | yes | exponential backoff with jitter |
| HTTP 408 | once | yes | respect total deadline |
| HTTP 429 | once when budget permits | yes | honor bounded `Retry-After` |
| HTTP 500/502/503/504 | once | yes | backoff, then fallback |
| HTTP 400/413/422 | no | normally no | return typed request/context error |
| HTTP 401/402/403 | no | explicit policy only | alert configuration/billing/security issue |
| HTTP 404 model not found | no | explicit model fallback only | alert configuration issue |
| explicitly unsupported tool/schema parameter | no | try next structured transport | preserve current capability fallback |

OpenRouter exposes a canonical `error.metadata.error_type`; use it in addition to the HTTP status. Do not classify every `400` response as an unsupported structured-output feature.

### P0: Honor `Retry-After` and use bounded jittered backoff

OpenRouter may return `Retry-After` for `429` and `503`. The current raw Java HTTP client does not honor it.

Recommended behavior:

- at most one same-provider retry initially;
- honor `Retry-After`, capped by the remaining total deadline;
- otherwise use exponential backoff with full jitter;
- maintain a process-wide retry budget so concurrent requests cannot collectively overload a failing dependency;
- never sleep beyond the job deadline.

Vision currently uses `Thread.sleep` with a fixed delay. Prefer coroutine-aware delay and the same central retry policy.

### P0: Add a per-provider circuit breaker

When LM Studio or its tunnel is unavailable, every new request currently pays the connection/request failure cost again.

Recommended states:

- closed: normal calls;
- open: skip the unhealthy provider and immediately consider the next provider;
- half-open: allow a small number of probes after the cooldown;
- close again after successful probes.

Track breakers separately for:

- LM Studio completion;
- LM Studio vision;
- OpenRouter completion;
- OpenRouter vision.

The breaker should count only relevant dependency failures, not invalid user requests.

### P0: Add concurrency bulkheads

`AiJobService` can launch an arbitrary number of IO jobs. A burst can overload local inference, exhaust threads, increase latency, and cause cascading fallback traffic to OpenRouter.

Recommended behavior:

- bounded queue for AI jobs;
- semaphore/concurrency limit for LM Studio, usually based on actual GPU/model capacity;
- separate OpenRouter concurrency limit;
- return a typed busy/retry-later result when the queue is full;
- expose queue depth and active-job metrics.

### P0: Fix cross-provider vision fallback

If the intended policy is LM Studio first and OpenRouter second, vision must continue to OpenRouter after LM Studio exhausts its configured models and retry budget.

Do not fall through on invalid attachment data or non-vision-capable input. Only availability, retryable provider failures, or exhausted compatible LM Studio models should trigger the OpenRouter vision fallback.

This correction is backend-only.

### P0: Do not use `:free` models as the production reliability fallback

The default OpenRouter completion and vision models use free variants. OpenRouter documents that free models have lower rate limits, variable availability, and higher peak latency, and are generally intended for experimentation or low-volume use.

Recommended production policy:

- choose at least one paid model that supports the required tools and structured outputs;
- pin an explicit model/version where behavioral consistency matters;
- configure a second non-Gemini/non-GLM model only after it passes the action regression corpus;
- retain free variants only for local development or an explicitly low-volume environment;
- set spend and request budgets at the OpenRouter key/guardrail level.

### P0: Make sandbox development-only and fail closed in production

Mock responses are useful locally but risky when a production secret is missing. A production deployment with no provider configured should not appear healthy and return simulated content.

Recommended behavior:

- require an explicit `AI_SANDBOX_ENABLED=true` for mock mode;
- default it to false outside development/test;
- fail readiness or return a typed `503 AI_NOT_CONFIGURED` in production;
- keep `/health` for process liveness and add/read a separate readiness signal for required dependencies/configuration.

### P1: Return and record the provider that actually answered

`activeProvider` and `activeModel` currently describe the first configured endpoint. They are not per-request results. If LM Studio fails and OpenRouter succeeds, `/ai/status` can still say LM Studio and Android can write the wrong provider/model into its audit metadata.

The completion response parser also discards useful OpenRouter metadata such as response ID, returned model, provider, usage, and generation ID header.

Recommended backend result metadata:

- actual gateway: `lmstudio` or `openrouter`;
- requested model;
- returned/resolved model;
- upstream provider when OpenRouter supplies it;
- whether fallback was used;
- ordered attempt summaries;
- transport used: tool call, JSON Schema, or JSON object;
- duration and time-to-first-token where available;
- token usage and cost where available;
- correlation/request/generation ID;
- sanitized final error category.

Backend logs and job diagnostics can be improved without Android changes. Displaying this metadata in Android or using it in the existing Android-side action audit requires frontend DTO/orchestration changes.

Per the repository owner's instruction, `androidApp` must be treated as read-only for future tasks. If a requested improvement requires frontend changes, explain the required change first and do not edit it without explicit permission and an available phone test path.

### P1: Use provider-specific capability policies

LM Studio and OpenRouter expose compatible request shapes, but they do not have identical operational or feature behavior. A single generic endpoint structure currently hides those differences.

Recommended design:

```text
AiCompletionGateway
|- LmStudioGateway
|  |- local capability/health handling
|  `- LM Studio-specific retry and timeout policy
`- OpenRouterGateway
   |- typed OpenRouter errors
   |- Retry-After
   |- provider routing/privacy policy
   `- returned generation/provider metadata
```

Keep shared request/result contracts above these adapters.

### P1: Tighten structured-output routing

The current JSON Schema and tool definitions default to `strict = false`.

For OpenRouter:

- use `strict: true` when the selected model/provider supports it;
- set `provider.require_parameters: true` so routing selects endpoints that support the requested structured parameters;
- retain server-side schema validation even when strict mode is enabled;
- keep a controlled fallback to JSON object for LM Studio/models that lack strict structured-output support;
- test every production model against the existing Malay action regression corpus before adding it to fallback.

### P1: Make OpenRouter privacy routing explicit

Prompts may contain workspace pages, tasks, attachments, financial rows, or other personal data. OpenRouter can route a model request across different upstream providers unless routing restrictions are supplied.

Recommended policy decision:

- decide whether Zero Data Retention is required;
- when required, send `provider.zdr: true` or enforce it through account guardrails;
- use an upstream provider allowlist when business/privacy requirements demand it;
- explicitly configure training/data-collection preferences;
- do not enable prompt/output logging in production unless the retention and access policy is approved;
- avoid logging raw prompts or provider response bodies locally.

### P1: Sanitize provider failures

Raw provider response bodies are included in several exception messages. Job storage truncates errors, but truncation is not sanitization. The basic chat route can return the aggregate exception text as assistant content.

Recommended behavior:

- parse errors into stable internal codes;
- log only sanitized summaries plus correlation IDs;
- never return raw upstream bodies to users;
- redact URLs with query strings, credentials, headers, prompts, and attachment content;
- return a concise typed user message and a separate internal diagnostic record.

### P1: Improve health and observability

`/ai/status` currently reports configuration, not current dependency health.

Recommended metrics and signals:

- attempts, successes, failures, and fallback count per provider/model;
- latency distributions per provider/model/transport;
- rate-limit and timeout counters;
- circuit breaker state changes;
- queue depth and concurrent requests;
- schema-validation failure rate;
- actual provider/model attribution;
- OpenRouter generation ID and usage/cost;
- LM Studio model-loaded/available state when safely queryable;
- cached dependency readiness rather than a remote probe on every user request.

Alert on sustained fallback rate, breaker-open state, repeated authentication/billing errors, and schema-validation regression.

### P1: Add dedicated resilience tests

Current tests cover structured transport behavior and many action contracts, but there is little direct coverage of cross-provider error/fallback policy.

Implemented on 2026-08-11: deterministic tests for shared-budget allocation, reserve boundaries, provider-timeout classification, environment mapping, and cancellation of a response body that stalls after its headers. The broader matrix below remains recommended.

Use deterministic local HTTP servers; do not call live providers in normal CI.

Minimum test matrix:

1. LM Studio `200` -> LM Studio result, OpenRouter not called.
2. LM Studio connection timeout -> one bounded retry -> OpenRouter success.
3. LM Studio `400 invalid_request` -> no same-provider retry.
4. LM Studio explicit unsupported tool parameter -> next structured transport.
5. OpenRouter `429` with `Retry-After` -> bounded wait and retry.
6. OpenRouter `401/402` -> no retry and typed configuration/billing error.
7. repeated LM Studio transient failures -> breaker opens.
8. half-open success -> breaker closes.
9. queue/concurrency limit -> deterministic busy response.
10. LM Studio vision failure -> OpenRouter vision success.
11. both providers fail -> sanitized typed error, no mock response.
12. no providers in production -> readiness failure/`AI_NOT_CONFIGURED`.
13. response diagnostics identify the provider/model that actually answered.
14. overall deadline expires before Android polling timeout.
15. idempotent replay does not create another provider call or execute actions twice.

Keep live-provider regression tests opt-in and separate from the deterministic resilience suite.

## Recommended target flow

```text
Authenticated AI job
  -> validate retrieval scope and attachments
  -> establish total deadline and correlation ID
  -> acquire bounded AI-job capacity
  -> LM Studio circuit breaker
       -> attempt
       -> one transient retry with bounded jitter
       -> record actual outcome
  -> OpenRouter circuit breaker when eligible
       -> require supported structured parameters
       -> apply privacy/provider policy
       -> honor Retry-After within remaining deadline
       -> record returned model/provider/generation ID
  -> normalize and schema-validate result
  -> enforce retrieval/action boundary
  -> persist sanitized diagnostics and result
  -> Android polls and executes already-validated actions idempotently
```

Do not add parallel hedged inference initially. Sequential fallback is simpler, avoids double inference cost, and is sufficient after deadlines, circuit breakers, and concurrency limits are implemented.

## Suggested implementation order

### Phase 1: Backend safety

1. central typed provider error model;
2. **completed:** total deadline and per-attempt remaining-time calculation;
3. bounded retry with `Retry-After`, jitter, and retry budget;
4. circuit breakers;
5. concurrency bulkheads;
6. corrected vision fallback;
7. production sandbox fail-closed behavior;
8. deterministic resilience tests.

### Phase 2: Provider quality and policy

1. replace production `:free` completion and vision defaults;
2. introduce provider-specific gateways;
3. add OpenRouter `require_parameters`, strict structured output, ZDR/allowlist policy;
4. validate every fallback model against action regression tests.

### Phase 3: Observability

1. capture actual provider/model/generation metadata in backend results and jobs;
2. add metrics, health/readiness, and alerts;
3. sanitize public errors and internal logs;
4. separately propose the required Android DTO/audit changes without editing frontend.

## Definition of production-ready fallback

The fallback layer can be considered production-ready when:

- all remote work is bounded by one deadline shorter than the client deadline;
- only transient failures are retried;
- retry delays honor provider guidance and cannot create a retry storm;
- unhealthy providers are skipped by circuit breakers;
- local and remote concurrency is bounded;
- completion and vision both follow the documented fallback policy;
- production never silently enters mock mode;
- production OpenRouter models are suitable for the expected availability and volume;
- structured-action capabilities are required and outputs remain server-validated;
- privacy routing is explicit;
- actual provider/model and fallback usage are observable;
- user-facing errors contain no raw provider response;
- the deterministic resilience matrix passes in CI.

## External references

- [OpenRouter: API error handling and `Retry-After`](https://openrouter.ai/docs/api/reference/errors-and-debugging)
- [OpenRouter: provider routing](https://openrouter.ai/docs/guides/routing/provider-selection)
- [OpenRouter: model fallbacks](https://openrouter.ai/docs/guides/routing/model-fallbacks)
- [OpenRouter: structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs)
- [OpenRouter: free-model limitations](https://openrouter.ai/docs/faq)
- [OpenRouter: Zero Data Retention](https://openrouter.ai/docs/guides/features/zdr)
- [LM Studio: OpenAI-compatible endpoints](https://lmstudio.ai/docs/developer/openai-compat)
- [LM Studio: tool use](https://lmstudio.ai/docs/developer/openai-compat/tools)
- [Microsoft Azure Architecture Center: Circuit Breaker pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
- [Microsoft Azure Architecture Center: transient fault handling](https://learn.microsoft.com/en-us/azure/architecture/best-practices/transient-faults)
