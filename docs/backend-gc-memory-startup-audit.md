# Backend GC, Memory dan Startup Audit

Tarikh audit: 2026-08-13  
Skop: backend microservice sahaja; audit read-only, tiada perubahan `androidApp`.

## Ringkasan keputusan

GC JVM asas berada pada konfigurasi yang munasabah kerana runtime menggunakan G1GC dan container-aware ergonomics. Namun, keseluruhan pengurusan memory dan concurrency belum sepenuhnya production-best-practice.

Keutamaan tertinggi:

1. Web-search cache tidak mempunyai had saiz atau eviction sebenar.
2. AI job menggunakan `Dispatchers.IO` tanpa application-level concurrency limit/bulkhead.
3. Response provider masih dibaca menggunakan `BodyHandlers.ofString()` tanpa hard byte cap.
4. Runtime Docker tiada memory/GC policy eksplisit untuk instance kecil.
5. Scope `AiJobService` tidak ditutup secara eksplisit semasa application shutdown.

## JVM dan GC semasa

Docker menjalankan `eclipse-temurin:17-jre-jammy` dan memulakan service dengan `java -jar` tanpa `JAVA_TOOL_OPTIONS` atau flag GC khusus. Rujukan: `Dockerfile`.

Flag ergonomics yang diperiksa pada JVM yang tersedia:

- `UseG1GC=true`;
- `UseContainerSupport=true`;
- `MaxRAMPercentage=25.0`;
- `InitialRAMPercentage=1.5625`;
- `MaxGCPauseMillis=200` sebagai sasaran heuristik default;
- tiada panggilan `System.gc()` dalam source backend.

Java 17 menyediakan container memory/CPU detection dan G1GC sebagai pilihan standard. Rujukan rasmi: [Oracle Java 17 launcher documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).

Render blueprint kini menggunakan plan `free`. Render menyenaraikan plan Free sebagai 512 MB RAM dan 0.1 CPU. Rujukan rasmi: [Render instance types](https://render.com/docs/compute-plans).

Dengan 512 MB container memory dan default heap percentage 25%, heap maksimum boleh berada sekitar 128 MB. Heap bukan satu-satunya penggunaan memory: Netty direct buffers, PDFBox raster, thread stacks, metaspace dan native libraries turut menggunakan RAM. Oleh itu, konfigurasi default mungkin terlalu ketat untuk workload PDF/AI burst walaupun collector-nya betul.

Menukar terus kepada ZGC tidak disarankan untuk instance 512 MB/0.1 CPU tanpa profiling. G1 ialah baseline yang sesuai; tuning memory dan backpressure lebih penting.

## Risiko memory dan allocation

### 1. Web-search cache tidak bounded — risiko tinggi

`WebSearchService` menyimpan result dalam `ConcurrentHashMap` di `backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/WebSearchService.kt`.

TTL hanya digunakan ketika cache dibaca. Entry expired tidak dibuang, dan tiada maksimum entry. Query unik yang banyak boleh mengekalkan object graph result dalam old generation secara berterusan.

Cadangan:

- tetapkan maksimum entry;
- buang expired entry secara opportunistic dan berkala;
- gunakan bounded LRU/TTL cache;
- ukur hit rate, entry count dan estimated bytes.

### 2. AI job concurrency tidak bounded — risiko tinggi

`AiJobService` mencipta `CoroutineScope(SupervisorJob() + Dispatchers.IO)` dan melancarkan job tanpa semaphore atau queue limit.

Ini membenarkan burst request menyimpan banyak prompt, JSON, attachment context, response buffer dan coroutine state serentak. PDF worker memang bounded kepada satu worker dengan queue kecil, tetapi normal AI text/action jobs masih tidak mempunyai bulkhead.

Cadangan:

- bounded queue untuk AI jobs;
- semaphore berasingan untuk LM Studio vision dan OpenRouter text/action;
- pulangkan status busy/429/503 apabila kapasiti penuh;
- tetapkan limit per provider dan per instance;
- cancel semua job aktif semasa shutdown.

### 3. Provider response buffering tidak bounded — risiko tinggi

AI dan web-search menggunakan `HttpResponse.BodyHandlers.ofString()` di beberapa path. Response dibuffer sepenuhnya sebelum parser atau `.take(...)` mengehadkan kandungan.

Ini masih menjadi residual memory/DoS risk untuk response provider yang terlalu besar atau tidak berhenti. Cap pada output selepas buffering tidak melindungi heap peak.

Cadangan: gunakan body handler/reader yang berhenti selepas hard byte limit, dengan cancellation yang menghormati shared deadline.

### 4. PDF allocation — baseline baik, tetapi masih in-process

PDF processing sudah mempunyai beberapa perlindungan:

- satu worker aktif;
- queue terhad;
- maksimum 3 PDF;
- maksimum 8 MiB per PDF dan 24 MiB aggregate;
- rendering satu page pada satu masa;
- maksimum 1,280px, 2MP dan 512 KiB JPEG;
- `MemoryUsageSetting.setupMixed(8 MiB, 64 MiB)`;
- deadline checkpoints dan cancellation best-effort.

Ini baik untuk normal input. Namun PDFBox masih berjalan dalam process backend; PDF yang benar-benar hostile masih boleh menggunakan CPU/memory ketika library call aktif. Process isolation/subprocess resource limit kekal sebagai hardening Phase C.

### 5. Cleanup scheduler dan lifecycle

`ChatAttachmentCleanupScheduler` dan `ContentAssetCleanupScheduler` mempunyai `close()`. Hikari datasource dan R2 storage juga ditutup pada lifecycle application.

Namun `AiJobService` tidak mempunyai `close()` atau subscription `ApplicationStopping`. `ContentAssetCleanupScheduler` menggunakan scheduled executor yang melancarkan coroutine IO; jika cleanup lebih lama daripada interval, kerja cleanup boleh bertindih.

Cadangan:

- tutup/cancel `AiJobService` secara eksplisit;
- gunakan mutex atau single-flight guard untuk cleanup;
- log shutdown drain dan job cancellation.

## Database dan HikariCP

Pool ditetapkan melalui `DATABASE_MAX_POOL_SIZE`, default 5, dan diberi nama `cyl-postgres`. Rujukan: `backend/src/main/kotlin/com/changeyourlife/cyl/backend/database/DatabaseFactory.kt`.

Perkara yang baik:

- HikariCP digunakan;
- datasource ditutup pada `ApplicationStopping`;
- Flyway migration dijalankan sebelum routing dibuka;
- pool kecil sesuai untuk instance kecil.

Perkara yang belum eksplisit:

- `connectionTimeout`;
- `validationTimeout`;
- `maxLifetime` yang diselaraskan dengan timeout database/infrastruktur;
- metrics active/idle/waiting connections.

Hikari menyatakan `minimumIdle` default biasanya sama dengan `maximumPoolSize`, dan `maxLifetime` patut lebih pendek daripada had lifetime di database/infrastruktur. Rujukan rasmi: [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby).

## Startup flow dan ukuran

Startup module mengikut urutan utama:

1. serialization, monitoring, HTTP dan authentication;
2. Hikari datasource jika `DATABASE_URL` ada;
3. Flyway migrations;
4. projection backfill untuk page yang pending;
5. AI, email, storage dan scheduler services;
6. routing dan Netty listener.

Tanpa PostgreSQL, ukuran local yang diperiksa:

- `Application started in 1.393 seconds` menggunakan `gradlew run`;
- listener menerima request sekitar 1.56 saat;
- `/health` membalas `200 OK`;
- fat JAR direct run mengambil kira-kira 1.7 saat dalam JVM diagnostic environment.

Ukuran ini tidak termasuk keadaan production sebenar kerana workspace tiada database production dan CPU/RAM local berbeza daripada Render Free. Dengan database aktif, startup turut menunggu 18 migration files dan projection backfill. Backfill berjalan dalam loop batch 100 page, jadi startup boleh menjadi lebih lama berdasarkan jumlah page pending.

Render Free boleh spin down selepas idle dan cold-start semula apabila request masuk. Rujukan: [Render free web services](https://render.com/docs/your-first-deploy).

## Health/readiness

`/health` mengembalikan status statik dan label database `configured` atau `in-memory`. Ia tidak melaporkan heap, GC pause, cache size, pool waiters atau readiness database ping.

Untuk production observability, asingkan:

- liveness: process masih hidup;
- readiness: database dan dependency kritikal sedia digunakan;
- metrics: heap used/committed/max, GC count/pause, thread count, direct memory, Hikari active/idle/waiting, AI queue depth dan web-cache entries.

## Penilaian akhir

| Kawasan | Status |
|---|---|
| G1GC dan container awareness | Baik |
| PDF memory cap | Baik dengan residual in-process risk |
| Web cache memory bound | Belum baik |
| AI concurrency/backpressure | Belum baik |
| Provider response cap | Belum lengkap |
| Hikari lifecycle | Baik, tuning timeout/metrics belum lengkap |
| Graceful shutdown semua scope | Belum lengkap |
| Startup tanpa DB | Baik, sekitar 1.4–1.7 saat local |
| Startup dengan DB/backfill | Belum diukur; berpotensi berubah mengikut data |
| GC metrics dan alerting | Belum ada |

**Kesimpulan:** pilihan GC asas sudah betul, tetapi backend belum boleh dilabel fully production-best-practice dari sudut memory lifecycle. Keutamaan seterusnya ialah bounded web cache, AI bulkhead/concurrency limit, capped response reader, explicit container memory policy, lifecycle cancellation dan runtime GC/memory metrics.

## Audit security dan keperluan Redis

Bahagian ini merakam semakan security dan keputusan seni bina cache/coordination. Ia ialah audit read-only; tiada perubahan pada `androidApp` diperlukan.

### Security yang sudah tersedia

- Ktor JWT menyemak signature HMAC, issuer dan audience.
- Token mengandungi subject user, email, issued-at dan expiry.
- Password menggunakan PBKDF2-HMAC-SHA256 dengan salt rawak dan perbandingan constant-time.
- Data utama, AI jobs dan idempotency disimpan dalam PostgreSQL apabila database dikonfigurasi.
- Route AI mempunyai had body 40 MiB, validasi attachment dan idempotency key untuk operasi async/mutation.
- Aliran attachment/PDF menggunakan framing sebagai untrusted evidence; raw image/PDF tidak dihantar kepada OpenRouter.
- Provider error body telah disanitasi supaya tidak menjadi kebocoran prompt atau data pengguna.

### Security gaps yang perlu ditutup sebelum production penuh

1. `JWT_SECRET` masih mempunyai fallback `dev-only-change-me` di `AppConfig.kt`. Production mesti menggunakan secret rawak melalui secret manager/environment dan sebaiknya gagal startup jika secret production tiada.
2. CORS masih menggunakan `anyHost()` di `plugins/HTTP.kt`. Hadkan kepada origin frontend yang diketahui; jangan benarkan semua browser origin.
3. Tiada application-level rate limit yang jelas untuk login, register, forgot/reset password, AI dan upload. Tambah limit berasingan mengikut endpoint dan user/IP.
4. JWT sah selama tujuh hari dan tiada refresh/revoke mechanism. Ini masih boleh diterima untuk MVP stateless, tetapi token yang dicuri tidak boleh dibatalkan sebelum expiry.
5. Jika database tidak dikonfigurasi, service menggunakan repository in-memory dan password-reset debug code. Mod ini hanya sesuai untuk local/test, bukan production.
6. `KTOR_DEVELOPMENT` perlu ditetapkan `false` secara eksplisit dalam production.
7. Teruskan hardening memory yang dinyatakan di atas: bounded cache, AI concurrency bulkhead dan capped provider response reader.

### Redis: perlu atau tidak untuk lajukan app?

Redis **belum diperlukan** untuk deployment semasa yang menggunakan satu backend instance dan PostgreSQL. Redis tidak akan mempercepat perkara yang paling lambat dalam projek ini, iaitu menunggu provider AI/web search atau memproses PDF/image. Ia juga tidak patut menggantikan PostgreSQL sebagai sumber data utama.

| Kegunaan | Redis sekarang? | Catatan |
|---|---:|---|
| Simpan user/content/job/idempotency | Tidak | Kekalkan dalam PostgreSQL supaya durable. |
| JWT validation biasa | Tidak | JWT boleh disahkan secara stateless. |
| Cache kecil pada satu instance | Tidak wajib | Cache memory yang bounded sudah memadai. |
| Rate limit merentas beberapa instance | Ya, sesuai | Perlu shared counter dengan TTL. |
| Token revoke/session merentas instance | Pilihan | Redis boleh menyimpan denylist atau session pendek. |
| Distributed lock/concurrency counter | Berguna | Terutama apabila worker lebih daripada satu. |
| AI queue merentas instance | Kemudian | Pertimbangkan Redis Streams atau queue managed. |

### Cadangan keputusan

- **Sekarang/P0:** jangan tambah Redis hanya untuk kelajuan. Betulkan secret production, CORS, rate limiting, fail-closed database mode, token lifecycle dan memory backpressure.
- **Apabila scale-out:** tambah managed Redis dengan TLS/auth, key namespace, TTL untuk semua key sementara, had memory/eviction, metrics dan polisi fail-open/fail-closed mengikut endpoint.
- Jangan simpan raw prompt, PDF, image atau token sensitif dalam Redis cache. Cache hanya data yang telah disanitasi dan benar-benar perlu.

Kesimpulan: Redis ialah alat untuk shared cache, rate limit, lock dan queue apabila sistem menjadi distributed; ia bukan keperluan asas dan bukan penyelesaian utama untuk latency AI atau GC backend sekarang.

## Master checklist: 10 domain audit backend dan microservice best practice

Senarai ini ialah rangka audit praktikal untuk sistem ini. Ia tidak bermaksud projek mempunyai sepuluh microservice deployable. Kod semasa ialah satu Ktor backend modular yang mengandungi beberapa service/domain dalaman. Jika sistem dipecahkan kemudian, setiap domain di bawah boleh menjadi boundary service yang berasingan hanya apabila skala dan ownership memerlukannya.

### 1. Architecture dan service boundaries

**Semakan semasa:** satu deployment Ktor dengan service dalaman, repository dan scheduler. Ini lebih mudah dioperasikan dan sesuai untuk skala semasa.

**Best practice:** kekalkan modular monolith sehingga terdapat bottleneck atau ownership yang jelas. Jika dipecahkan, setiap service mesti mempunyai API contract, owner, database boundary, timeout, retry policy dan observability sendiri. Elakkan memecahkan service hanya kerana nama class sudah berasingan.

**Status:** sesuai untuk sekarang; extraction ke microservice belum diperlukan.

### 2. HTTP API dan contract

**Semakan semasa:** Ktor routes, JSON serialization, status pages, authentication boundary dan AI request body limit sudah digunakan.

**Best practice:** version contract yang breaking, gunakan DTO typed untuk semua error, validate input di boundary, tetapkan request/response timeout, idempotency untuk mutation yang boleh diulang, dan contract test untuk client.

**Status:** asas baik; semak pagination, consistent error code, timeout dan content negotiation secara endpoint-by-endpoint.

### 3. Authentication, authorization dan identity

**Semakan semasa:** JWT issuer/audience/signature dan password hashing tersedia. Ownership checks wujud pada banyak route/repository.

**Best practice:** secret production wajib, least-privilege authorization di setiap mutation, generic auth/reset errors, rate limit login/reset, refresh-token rotation atau revoke strategy, audit login/security events dan password hashing yang boleh dinaik taraf ke Argon2id.

**Status:** baseline ada; production hardening masih diperlukan. Rujuk bahagian security di atas.

### 4. PostgreSQL dan data consistency

**Semakan semasa:** PostgreSQL + HikariCP + Flyway digunakan apabila `DATABASE_URL` tersedia; repository in-memory menjadi fallback.

**Best practice:** production fail-closed jika DB wajib, transaction boundary yang jelas, unique constraint/idempotency di database, query/index review, bounded pool, statement timeout, migration backward compatibility, backup/restore drill dan ownership filter di query.

**Status:** struktur baik; DB failure mode, index/slow-query metrics dan restore drill perlu disahkan.

### 5. Cache dan Redis

**Semakan semasa:** Redis belum digunakan. Web-search cache memory masih perlu bounded.

**Best practice:** cache hanya data derivable, TTL dan size limit wajib, cache key versioning, stampede protection, invalidation policy dan metrics. Gunakan Redis apabila rate limit/cache/lock mesti dikongsi antara replica; jangan jadikan Redis canonical database.

**Status:** Redis tidak diperlukan untuk single instance sekarang; bounded local cache perlu diperbaiki.

### 6. Async jobs, queue dan concurrency

**Semakan semasa:** `AiJobService` mengurus job async tetapi application-level bulkhead/queue limit masih perlu diperketatkan. PDF extractor sudah mempunyai worker dan queue yang bounded.

**Best practice:** queue depth limit, per-user/per-provider concurrency limit, deadline propagation, retry dengan exponential backoff + jitter, dead-letter handling, cancellation semasa shutdown dan idempotent job completion. Gunakan broker seperti Redis Streams atau managed queue hanya apabila worker perlu merentas instance.

**Status:** PDF path lebih terkawal; AI job global masih perlu bulkhead dan backpressure.

### 7. AI provider integration

**Semakan semasa:** Phase A menetapkan OpenRouter untuk text/action dan LM Studio untuk visual; shared deadline, no cross-role fallback dan sanitasi provider error tersedia.

**Best practice:** per-provider timeout/circuit breaker, retry hanya untuk error transient, budget token/cost, response byte cap, schema validation, prompt/data privacy boundary, model allowlist, request correlation ID dan fallback yang tidak menukar role provider.

**Status:** routing/privacy sudah kukuh; circuit breaker, cost budget dan bounded response reader masih residual.

### 8. File, image dan PDF processing

**Semakan semasa:** decoded size/body limits, PDF page/render caps, one-worker rendering, deadline checkpoints dan LM-only raw visual routing tersedia.

**Best practice:** MIME/content sniffing, decompression-bomb protection, aggregate quota, bounded raster dimensions, temp-file cleanup, malware scanning jika diperlukan, process isolation untuk parser hostile dan jangan forward raw attachment ke provider yang tidak memerlukannya.

**Status:** Phase B solid untuk bounded in-process processing; subprocess isolation kekal hardening lanjutan.

### 9. Observability, reliability dan operations

**Semakan semasa:** logging startup/provider routing, health route dan scheduler lifecycle tersedia.

**Best practice:** liveness/readiness berasingan, structured logs tanpa secret/raw prompt, correlation/trace ID, metrics latency/error/queue/GC/cache/pool, alert thresholds, graceful shutdown, dependency health checks dan runbook incident.

**Status:** logging asas ada; metrics, readiness sebenar, tracing dan alerting belum lengkap.

### 10. Performance, JVM memory dan deployment

**Semakan semasa:** G1GC/container awareness, startup local sekitar 1.4–1.7 saat tanpa DB, Hikari dan PDF caps telah diaudit.

**Best practice:** benchmark dengan workload sebenar, ukur p95/p99 bukan purata sahaja, set memory headroom, cap thread/queue/response, profile allocation dan GC sebelum tuning, gunakan rolling deploy, graceful drain, autoscaling berdasarkan metric dan cold-start budget.

**Status:** baseline munasabah; web cache, AI concurrency, provider body cap, GC metrics dan production startup dengan DB masih perlu diukur.

### Urutan pelaksanaan yang disyorkan

1. Security P0: production JWT secret, CORS allowlist, rate limit dan fail-closed database mode.
2. Reliability P0: AI bulkhead, bounded web cache, provider response byte cap dan graceful job shutdown.
3. Observability: readiness, metrics, correlation ID dan alerting.
4. Performance: benchmark p95/p99 dan ukur bottleneck sebenar.
5. Redis atau microservice extraction hanya selepas metric menunjukkan shared coordination atau scaling benar-benar diperlukan.

Audit ini ialah checklist dan status berdasarkan code semasa; setiap status “perlu diperbaiki” patut disahkan dengan test/metric sebelum perubahan production.
