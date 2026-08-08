*** Begin Patch
*** Update File: androidApp/src/main/java/com/changeyourlife/cyl/core/di/NetworkModule.kt
@@
-private const val NetworkTimeoutSeconds = 300L
+// Reduce network timeouts so the UI stops waiting too long for hung requests.
+// 300s made the app appear to 'load forever' when the backend/provider hung.
+private const val NetworkTimeoutSeconds = 60L
*** End Patch