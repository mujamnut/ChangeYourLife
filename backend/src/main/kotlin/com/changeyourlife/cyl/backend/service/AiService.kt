*** Begin Patch
*** Update File: backend/src/main/kotlin/com/changeyourlife/cyl/backend/service/AiService.kt
@@
-        val requestBuilder = HttpRequest.newBuilder()
-            .uri(URI.create(completionsUrl))
-            .header("Content-Type", "application/json")
-            .header("HTTP-Referer", "https://changeyourlife.local")
-            .header("X-Title", "ChangeYourLife")
-            .POST(HttpRequest.BodyPublishers.ofString(body))
+        val requestBuilder = HttpRequest.newBuilder()
+            .uri(URI.create(completionsUrl))
+            .header("Content-Type", "application/json")
+            .header("HTTP-Referer", "https://changeyourlife.local")
+            .header("X-Title", "ChangeYourLife")
+            // enforce per-request timeout so slow/blocked AI providers don't hang the server
+            .timeout(AiRequestTimeout)
+            .POST(HttpRequest.BodyPublishers.ofString(body))
*** End Patch