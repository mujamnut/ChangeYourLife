*** Begin Patch
*** Update File: backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt
@@
-import java.time.Duration
+import java.time.Duration
@@
-    private val httpClient = HttpClient.newBuilder()
-        .connectTimeout(Duration.ofSeconds(30))
-        .build()
+    // Timeouts for AI provider requests
+    private val AiConnectTimeout: Duration = Duration.ofSeconds(10)
+    private val AiRequestTimeout: Duration = Duration.ofSeconds(60)
+
+    private val httpClient = HttpClient.newBuilder()
+        .connectTimeout(AiConnectTimeout)
+        .build()
*** End Patch