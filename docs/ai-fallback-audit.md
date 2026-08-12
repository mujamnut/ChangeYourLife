## AI Fallback Audit

Audit date: 2026-08-11

Last implementation update: 2026-08-12

Scope: backend AI completion, structured-action, vision, asynchronous job, fallback, retry, and observability flow after the direct Gemini and GLM integrations were removed.

This document is an architecture review. Implementation status is called out where a recommendation has since been completed.

## Executive summary

The current implementation is a good development and personal-use foundation, but it is not yet production-grade resilience. Routing Phases A and B and the shared AI remote-work deadline are now implemented. Phase B completes the bounded scanned/mixed-PDF attachment path and treats attachment-derived content as untrusted evidence. Failed visual extraction and provider failures are sanitized, so endpoint details, raw upstream response bodies, and visual payloads are not forwarded with the final prompt.

The configured provider roles are:

1. OpenRouter is the only normal text-chat and structured-action provider.
2. LM Studio is the only visual extraction/OCR provider.
3. Backend-native parsers handle readable text files and selectable text on each PDF page; only pages that need visual extraction are rendered for LM Studio.
4. Sandbox/mock remains the local-compatibility mode only when neither live provider is configured.

Plain-text requests skip LM Studio. Visual extraction completes before its bounded text context is passed to OpenRouter. There is no implicit cross-role provider fallback: OpenRouter failure does not fall back to LM Studio text completion, and LM Studio visual failure does not send the raw image to OpenRouter vision. An LM-Studio-only configuration reports degraded status because the final text/action provider is unavailable.

The shared AI remote-work deadline described below was implemented on 2026-08-11, and routing Phases A and B were implemented on 2026-08-12. The most important remaining production gaps are:

- no typed error classification for deciding retry, fallback, or fail-fast behavior;
- no `Retry-After` support, exponential backoff with jitter, or retry budget;
- no circuit breaker or concurrency bulkhead for the overall AI job, LM Studio request, or OpenRouter request paths (the local PDF worker itself is bounded);
- the default OpenRouter text/action model uses a `:free` variant;
- status and audit attribution can report the configured primary provider instead of the provider that actually answered;
- structured-output capabilities and privacy routing are not explicitly constrained for OpenRouter;
- provider response bodies are still read into unbounded in-memory strings before the accepted output is capped;
- PDFBox runs in-process, so deadline checkpoints and future cancellation are best-effort containment for a hostile parser workload rather than a hard process boundary;
- visual-output data-URI/base64 rejection is heuristic and should be replaced with a streaming, structured boundary;
- broader per-role retry, breaker, concurrency, and failure-policy behavior still needs dedicated automated coverage.

## Current implementation

### Completion provider selection

`AiService` now builds one role-specific OpenRouter text/action endpoint when `OPENROUTER_API_KEY` is present. LM Studio configuration is evaluated separately by the visual attachment path and is never inserted into the final completion route.

Relevant code:

- [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `textCompletionEndpoint`, `requireTextCompletionEndpoint`, and `analyzeImagesWithLmStudioRouting`
- [`AppConfig.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/config/AppConfig.kt), provider environment settings
- [`Application.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/Application.kt), service wiring

The active defaults are:

- OpenRouter text/action: `openai/gpt-oss-20b:free`;
- LM Studio visual extraction: `qwen/qwen3.5-9b`.

`LMSTUDIO_MODEL`, `OPENROUTER_VISION_MODELS`, `OPENROUTER_VISION_MODEL`, and `AI_VISION_MODEL` remain loadable for configuration and constructor compatibility, but routing Phases A and B ignore them at runtime. They no longer enable LM Studio completion or OpenRouter vision and can be removed in a later compatibility cleanup.

Gemini and GLM are no longer present as direct providers or fallback models.

### Basic chat flow

Normal chat goes directly to the single OpenRouter text endpoint. It does not probe LM Studio first and does not cross-fallback to LM Studio after an OpenRouter error.

The public `chat()` method catches that exception and converts it to a bounded response. Raw provider error bodies are removed before they can enter user-visible text or persisted diagnostics. This avoids crashing the route, although the application still needs the typed production error contract planned for Phase C.

### Structured action flow

For page/database actions, the backend prepares context and attempts these response transports against OpenRouter only:

1. tool/function call;
2. JSON Schema response;
3. JSON object response.

The result is then normalized, schema-validated, recovered when possible, and passed through the retrieval/action boundary before being returned. This layered validation is one of the strongest parts of the current design.

Relevant code:

- [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `chatWithActions`, `chatCompletionsForActions`, and `sendStructuredActionCompletion`
- [`AiActionSchemaValidator.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiActionSchemaValidator.kt)
- [`AiRetrievalActionBoundary.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiRetrievalActionBoundary.kt)

### Vision flow

Vision has bounded per-model attempts within LM Studio. A retryable HTTP status can be attempted twice with a fixed delay, subject to the shared deadline.

The effective Phase A/B visual flow is:

```text
LM Studio configured -> try LM Studio visual models -> extracted text or explicit failure
LM Studio absent     -> mark visual extraction unavailable
Either result        -> OpenRouter performs the final text/action inference when configured
```

It deliberately does not use either cross-role failure flow:

```text
LM Studio visual failure -> OpenRouter vision fallback
OpenRouter text failure  -> LM Studio completion fallback
```

Relevant code: [`AiService.kt`](../backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt), `analyzeImagesWithLmStudioRouting`.

### Historical pre-Phase-A baseline

This subsection is retained so future audits can understand what Phase A changed. Before 2026-08-12, `AiService` built one ordered completion list with LM Studio before OpenRouter. Normal text therefore called LM Studio first, and an image request could call LM Studio once for vision and again for final completion. When LM Studio was absent, the older vision path could use configured OpenRouter vision models. When LM Studio was present but its visual models failed, that older path stopped rather than crossing to OpenRouter.

The historical completion order was:

1. LM Studio;
2. OpenRouter;
3. sandbox/mock only when no live provider was configured.

Those statements describe the archived baseline, not the current runtime.

### Architecture decision: route by input modality

Decision date: 2026-08-12

Status: Routing Phases A and B implemented on 2026-08-12.

The provider policy is no longer a single ordered completion fallback list for every request. Providers have separate roles:

- LM Studio is the visual extraction/OCR provider;
- OpenRouter is the text conversation, reasoning, and structured-action provider;
- backend-native parsers handle readable text files and PDFs with selectable text.

The routing matrix is:

| Input | Pre-processing | Final response/action | LM Studio skipped? |
|---|---|---|---:|
| plain text, no attachment | none | OpenRouter | yes |
| image | LM Studio vision/OCR | OpenRouter receives extracted text context | no |
| readable text file | local text extraction | OpenRouter | yes |
| PDF with selectable text | local PDF extraction | OpenRouter | yes |
| scanned/image-only PDF | render bounded pages as images, then LM Studio vision/OCR | OpenRouter receives extracted text context | no |
| optional web-search context | existing backend web-search service | OpenRouter | yes |

This avoids calling LM Studio for ordinary text, avoids a second LM Studio completion after it has already extracted an image, and reserves local inference capacity for work that requires vision. Raw image pixels and rendered PDF pages should remain within the LM Studio attachment stage; only bounded extracted text and diagnostics should be passed to OpenRouter.

Phase A introduced the separate role endpoints, skips LM Studio for requests without visual input, and never returns to LM Studio for final inference after image extraction. Phase B completes the PDF modality: selectable text is extracted per page, while scanned pages and blank/insufficient pages in mixed PDFs are rendered under bounded limits and sent to LM Studio for OCR. The sanitized, bounded result is then supplied to OpenRouter.

The implemented failure policy is strict role separation by default:

- if OpenRouter is missing or fails, text/action planning reports that the required provider is unavailable rather than silently changing to LM Studio;
- if LM Studio is missing or fails for visual input, the backend reports that visual extraction failed and does not send raw visual content to OpenRouter;
- local text/PDF extraction failures are explicit and never authorize invented attachment content;
- all stages continue sharing the existing remote-work deadline and finalization reserve.

An emergency cross-role fallback can be added later only as an explicit configuration policy. It is not present in the current routing because it would weaken the resource, privacy, and capability boundary recorded here.

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

- explicit provider selection rather than hidden or random routing;
- sequential attachment extraction followed by final inference, which avoids duplicate work and inference cost;
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

The backend now starts one monotonic remote-work deadline for each chat/action operation. LM Studio visual extraction, optional web search, the final OpenRouter request, vision retries, and OpenRouter structured tool/JSON transport attempts all derive their request timeout from the same remaining budget. Each asynchronous HTTP future is awaited only for that budget and cancelled on expiry, so a response body that stalls after sending headers cannot bypass the limit. Deadline exhaustion is preserved as a typed internal exception so retry/transport loops stop instead of resetting the timeout.

Default policy:

- total job deadline: 180 seconds;
- LM Studio visual extraction request cap: 90 seconds;
- OpenRouter text/action request cap: 60 seconds;
- connection timeout: 5 seconds;
- finalization reserve: 10 seconds.

The effective timeout for a remote attempt is `min(provider cap, remaining deadline - finalization reserve)`. No new remote request or vision retry delay starts once the reserve boundary is reached. The values are configurable with `AI_JOB_DEADLINE_MS`, `AI_CONNECT_TIMEOUT_MS`, `LMSTUDIO_REQUEST_TIMEOUT_MS`, `OPENROUTER_REQUEST_TIMEOUT_MS`, and `AI_FINALIZATION_RESERVE_MS`.

This is not a hard cancellation boundary for local parsing, validation, or persistence already in progress; the 10-second reserve is the completion margin for those steps. A strict end-to-end wall-clock SLA would additionally need cancellation enforcement at job orchestration and persistence boundaries.

The 180-second starting value should still be benchmarked against actual LM Studio cold-start and p95 generation latency. Keep the configured deadline below the client polling deadline with enough margin to validate, persist, and return the result.

### P0: Classify errors before retrying or falling back

The current provider calls still treat many exceptions as equivalent. Structured transport treats a broad group of HTTP responses as capability rejection, including generic `400` responses that may actually represent an invalid prompt or context-length problem.

Recommended starting policy:

| Failure | Same-role retry/fallback | Cross-role fallback | Action |
|---|---:|---:|---|
| connection reset/temporary DNS/read timeout | once | no | exponential backoff with jitter |
| HTTP 408 | once | no | respect total deadline |
| HTTP 429 | once when budget permits | no | honor bounded `Retry-After` |
| HTTP 500/502/503/504 | once | no | backoff, then same-role model/upstream fallback only |
| HTTP 400/413/422 | no | normally no | return typed request/context error |
| HTTP 401/402/403 | no | no | alert configuration/billing/security issue |
| HTTP 404 model not found | explicit same-role model only | no | alert configuration issue |
| explicitly unsupported tool/schema parameter | try next structured transport | no | preserve current capability fallback |

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
- open: skip the unhealthy role dependency and return a bounded typed failure;
- half-open: allow a small number of probes after the cooldown;
- close again after successful probes.

Track breakers separately for the two active role dependencies:

- LM Studio visual extraction;
- OpenRouter text/action inference.

The breaker should count only relevant dependency failures, not invalid user requests.

### P0: Add concurrency bulkheads

`AiJobService` can launch an arbitrary number of IO jobs. A burst can overload local inference, exhaust threads, increase latency, and cause cascading fallback traffic to OpenRouter. Phase B bounds the local PDF processing worker at one active task with a bounded queue, but that does not yet bound all AI jobs or remote provider calls.

Recommended behavior:

- bounded queue for AI jobs;
- semaphore/concurrency limit for LM Studio, usually based on actual GPU/model capacity;
- separate OpenRouter concurrency limit;
- return a typed busy/retry-later result when the queue is full;
- expose queue depth and active-job metrics.

### P0: Implement modality-based provider routing

Status: Routing Phases A and B implemented on 2026-08-12.

This supersedes the earlier proposal to make vision fall through from LM Studio to OpenRouter. The intended boundary is now:

```text
visual extraction -> LM Studio
text/action planning -> OpenRouter
```

The implementation now uses a role-specific OpenRouter text endpoint rather than the former LM Studio-first completion list. Requests with no visual attachment skip LM Studio entirely. After LM Studio extracts an image, the final text/action call goes directly to OpenRouter. Missing or failed providers do not trigger a cross-role fallback.

Readable text files and selectable PDF pages use local extraction. Scanned PDF pages, including only the pages without sufficient selectable text in a mixed PDF, use bounded rendering followed by LM Studio OCR. Direct images and rendered PDF pages share a four-frame ceiling. Only the sanitized text result crosses into OpenRouter.

This change is backend-only unless provider-role details are later displayed in the Android UI.

### P0: Do not use `:free` models as the production reliability fallback

The default OpenRouter text/action model uses a free variant. The old OpenRouter vision defaults remain accepted only as ignored compatibility settings after Phase A. OpenRouter documents that free models have lower rate limits, variable availability, and higher peak latency, and are generally intended for experimentation or low-volume use.

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

Before Phase A, `activeProvider` and `activeModel` described the first item in the generic completion list, so they could label an eventual OpenRouter response as LM Studio. Phase A corrects the configured-route semantics: an OpenRouter text/action route reports live OpenRouter status, an LM-Studio-only configuration reports degraded/unavailable final inference, and a configuration with neither provider reports sandbox.

These fields still describe configuration rather than a per-request result. They do not identify the actual OpenRouter upstream provider or returned model, and they do not separately attribute a completed LM Studio extraction on each request.

The completion response parser also discards useful OpenRouter metadata such as response ID, returned model, provider, usage, and generation ID header.

Recommended backend result metadata:

- extraction gateway/model when visual extraction was attempted;
- final text/action gateway/model;
- requested model;
- returned/resolved model;
- upstream provider when OpenRouter supplies it;
- whether a same-role retry or explicitly configured fallback was used;
- ordered attempt summaries;
- transport used: tool call, JSON Schema, or JSON object;
- duration and time-to-first-token where available;
- token usage and cost where available;
- correlation/request/generation ID;
- sanitized final error category.

Backend logs and job diagnostics can be improved without Android changes. Displaying this metadata in Android or using it in the existing Android-side action audit requires frontend DTO/orchestration changes.

Per the repository owner's instruction, `androidApp` must be treated as read-only for future tasks. If a requested improvement requires frontend changes, explain the required change first and do not edit it without explicit permission and an available phone test path.

### P1: Use provider-specific capability policies

LM Studio and OpenRouter expose compatible request shapes, but they do not have identical operational or feature behavior. Phase A separates their runtime roles and endpoint selection. Further gateway extraction would make their error, retry, health, concurrency, and privacy policies independently testable.

Recommended design:

```text
AiInputRouter
|- LmStudioVisualExtractionGateway
|  |- local capability/health handling
|  `- vision/OCR retry and timeout policy
|- LocalAttachmentExtractor
|  `- readable text and selectable-text PDF handling
`- OpenRouterTextActionGateway
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
- keep controlled tool-call -> JSON Schema -> JSON object transport fallback within the OpenRouter text/action stage;
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

Status: implemented for the current LM Studio visual and OpenRouter text/action paths on 2026-08-12.

Raw provider response bodies are no longer included in the public failure path or attachment context. Logs and persisted diagnostics receive bounded summaries instead of the upstream body. The remaining reliability work is to replace these summaries with stable typed error codes and correlation metadata.

Further recommended behavior:

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

Current tests cover structured transport behavior and many action contracts. Phase A adds deterministic local-server coverage for direct OpenRouter text/action routing, LM Studio visual extraction followed by OpenRouter final inference, raw-image isolation, missing-provider behavior, and shared-deadline continuity. Phase B adds deterministic selectable, scanned, mixed, encrypted, corrupt, and limit-oriented PDF fixtures without contacting a live provider.

Implemented on 2026-08-11: deterministic tests for shared-budget allocation, reserve boundaries, provider-timeout classification, environment mapping, and cancellation of a response body that stalls after its headers. Implemented on 2026-08-12: the Phase A modality-routing cases and Phase B PDF processing fixtures. The broader resilience matrix below remains recommended.

Use deterministic local HTTP servers; do not call live providers in normal CI.

Minimum test matrix:

1. plain text -> OpenRouter called once, LM Studio not called;
2. structured action prompt -> OpenRouter transport fallback works, LM Studio not called;
3. image -> LM Studio extraction followed by OpenRouter final inference;
4. image -> raw data URL/pixels absent from the OpenRouter request;
5. readable text file -> local extraction then OpenRouter, LM Studio not called;
6. selectable-text PDF -> local extraction then OpenRouter, LM Studio not called;
7. scanned PDF -> bounded page rendering, LM Studio extraction, then OpenRouter;
8. LM Studio visual connection timeout -> bounded retry or typed visual failure, never raw OpenRouter vision fallback;
9. OpenRouter `429` with `Retry-After` -> bounded wait and retry;
10. OpenRouter `401/402` -> no retry and typed configuration/billing error;
11. repeated LM Studio visual failures -> visual breaker opens without affecting OpenRouter text health;
12. repeated OpenRouter failures -> text/action breaker opens without marking LM Studio visual extraction unhealthy;
13. queue/concurrency limit -> deterministic busy response;
14. no required provider in production -> readiness failure/typed unavailable result;
15. diagnostics identify extraction and final providers/models separately;
16. shared deadline expires between extraction and final inference -> OpenRouter call is not started;
17. stalled response body is cancelled within the shared budget;
18. idempotent replay does not create another provider call or execute actions twice.

Keep live-provider regression tests opt-in and separate from the deterministic resilience suite.

## Recommended target flow

```text
Authenticated AI job
  -> validate retrieval scope and attachments
  -> establish shared remote-work deadline and correlation ID
  -> acquire bounded AI-job capacity
  -> classify input
       -> no attachment
            -> skip LM Studio
       -> image
            -> LM Studio vision/OCR
            -> bounded extracted text context
       -> readable text file or selectable-text PDF
            -> local extraction; skip LM Studio
       -> scanned/image-only PDF
            -> render bounded pages in the single-worker PDF processor
            -> LM Studio vision/OCR
            -> bounded extracted text context
  -> insert sanitized attachment evidence before the original latest user prompt
  -> reject attachment-derived mutation instructions unless the original user prompt authorizes the mutation
  -> OpenRouter text/action gateway
       -> require supported structured parameters
       -> apply privacy/provider policy
       -> honor Retry-After within remaining deadline
       -> record returned model/provider/generation ID
  -> normalize and schema-validate result
  -> enforce retrieval/action boundary
  -> persist sanitized diagnostics and result
  -> Android polls and executes already-validated actions idempotently
```

Do not call LM Studio and OpenRouter in parallel. Attachment extraction must complete before its bounded text context is sent to OpenRouter. For plain text, OpenRouter should start immediately without an LM Studio probe.

## Modality-routing implementation phases

### Routing Phase A: Separate provider roles

Status: implemented on 2026-08-12.

Completed scope:

1. replaced the generic LM Studio-first completion list with role-specific endpoint selection;
2. made OpenRouter the direct and only gateway for normal chat and structured-action planning;
3. made LM Studio the visual extraction gateway and removed OpenRouter vision from runtime routing;
4. made requests without visual input skip LM Studio completely;
5. preserved existing backend route/request/response contracts so Android requires no change;
6. retained one shared deadline across attachment extraction and final OpenRouter inference;
7. added deterministic tests proving plain text never calls LM Studio and final planning never returns to LM Studio after image extraction;
8. made an LM-Studio-only configuration report degraded status rather than pretending that its visual model can answer final text/action requests.
9. sanitized LM Studio failure context before sending the bounded failure notice to OpenRouter, including a regression test for upstream bodies that contain image-like sensitive data.

Configuration compatibility is intentionally broader than runtime capability. `LMSTUDIO_MODEL`, `OPENROUTER_VISION_MODELS`, `OPENROUTER_VISION_MODEL`, and `AI_VISION_MODEL` are retained and loadable but ignored. This prevents an abrupt configuration-schema break while keeping the provider boundary enforceable.

### Routing Phase B: Complete the attachment pipeline

Status: implemented on 2026-08-12; backend-only, with no Android/frontend contract change.

Implemented scope:

1. extracts selectable text per PDF page, and renders only scanned/blank/insufficient pages in scanned or mixed PDFs;
2. shares a maximum of four visual frames across direct images and rendered PDF pages, with at most three rendered pages from any one PDF;
3. renders at no more than 1,280 pixels on the longest edge, two megapixels, and 512 KiB per JPEG frame;
4. accepts at most three PDF attachments, 8 MiB decoded per PDF, 24 MiB decoded in aggregate, and inspects at most 40 pages per PDF;
5. runs local PDF work with concurrency one, a bounded queue, a 25-second batch cap, and checkpoints against the shared AI-job deadline before and after expensive stages;
6. keeps the combined attachment evidence under 48,000 characters; the visual prompt asks for roughly 3,000 characters per page and accepted visual output remains capped at approximately 12,000 characters total;
7. reports partial or failed extraction explicitly when pages, rendering, deadlines, or limits prevent complete coverage; encrypted, corrupt, or extraction-forbidden PDFs fail closed;
8. caps authenticated AI request bodies at 40 MiB using Ktor 3.5.1's body-limit plugin, then validates actual decoded per-file and aggregate sizes after deserialization instead of trusting declared metadata;
9. sends direct images and rendered PDF pages only to LM Studio, then passes only sanitized bounded text to OpenRouter; no raw visual payload crosses the role boundary and there is no cross-role provider fallback;
10. inserts attachment text as untrusted evidence before the original latest user prompt, and constrains every attachment-bearing mutation to the scope recovered deterministically from that original prompt; only a root `CREATE_PAGE` may retain model-derived attachment content after an exact scope match, while extra, different, or unverifiable mutations fall back to the deterministic action or are blocked;
11. sanitizes upstream provider failure bodies before user responses, job diagnostics, or downstream prompt construction.

The local PDF boundary is deliberately conservative, but it is not a hard sandbox. PDFBox executes in the backend process; cancellation and deadline checks are best effort while a library call is active. Strict containment for deliberately hostile PDFs requires moving parsing/rendering into a resource-limited subprocess.

### Routing Phase C: Reliability and observability

Status: pending.

1. add separate circuit breakers and concurrency limits for LM Studio vision and OpenRouter text/action work;
2. return typed failures for `VISUAL_PROVIDER_UNAVAILABLE`, `VISUAL_EXTRACTION_FAILED`, `TEXT_PROVIDER_UNAVAILABLE`, and deadline exhaustion;
3. record the actual extraction provider/model separately from the final response provider/model;
4. test LM Studio failure, OpenRouter failure, stalled responses, deadline exhaustion between stages, and idempotent replay beyond the deterministic PDF fixtures already added in Phase B;
5. continue adversarial verification that logs and persisted diagnostics contain no raw attachment data or unsanitized upstream response bodies;
6. replace unbounded `BodyHandlers.ofString()` buffering with a bounded response-body reader before parsing provider output;
7. strengthen the current fail-closed data-URI/base64 heuristic with a streaming, structured visual-output boundary and adversarial fixtures.

Phases A and B cap accepted visual context at approximately 12,000 characters, reject data-URI or long base64-like output from LM Studio, and do not log or forward raw provider-error bodies. The remaining items above harden memory use and detection against fragmented or deliberately obfuscated payloads; they do not change the provider-role routing contract. Provider responses still use unbounded `BodyHandlers.ofString()` buffering before output caps are applied, and the data-URI/base64 filter remains heuristic.

### Routing Phase D: Optional frontend visibility

Status: deferred; not required for backend routing.

The existing Android request flow can remain unchanged. If the product later needs to display extraction and final-provider attribution, propose the DTO/UI change separately. Per the repository-owner instruction, do not edit `androidApp` without explicit permission and an available phone compile/test path.

## Suggested implementation order

### Phase 1: Backend safety

1. central typed provider error model;
2. **completed:** total deadline and per-attempt remaining-time calculation;
3. bounded retry with `Retry-After`, jitter, and retry budget;
4. circuit breakers;
5. concurrency bulkheads;
6. **completed:** modality-routing Phases A and B, including scanned/mixed-PDF handling without cross-provider vision fallback;
7. production sandbox fail-closed behavior;
8. deterministic resilience tests.

### Phase 2: Provider quality and policy

1. replace the production OpenRouter `:free` completion default;
2. **completed for Phase A routing:** introduce role-specific LM Studio visual extraction and OpenRouter text/action selection; extract fuller provider gateway classes as reliability work requires;
3. add OpenRouter `require_parameters`, strict structured output, ZDR/allowlist policy;
4. validate the production OpenRouter model against action regression tests and the LM Studio model against attachment/OCR fixtures;
5. remove ignored LM Studio completion and OpenRouter vision compatibility settings after a documented deprecation window.

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
- text/action and visual-extraction requests follow the documented modality policy;
- plain text never waits for an LM Studio attempt;
- raw visual attachments are not forwarded to OpenRouter;
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
