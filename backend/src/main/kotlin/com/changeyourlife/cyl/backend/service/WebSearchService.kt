package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.config.WebSearchConfig
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class WebSearchService(
    private val config: WebSearchConfig,
) {
    private val logger = LoggerFactory.getLogger(WebSearchService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()
    private val cache = ConcurrentHashMap<String, CachedWebSearch>()

    fun search(query: String, limit: Int = DefaultResultLimit): WebSearchContext {
        val normalizedQuery = query.normalizeWebSearchQuery()
        if (normalizedQuery.isBlank()) {
            return WebSearchContext(query = query, status = "skipped")
        }
        if (!config.enabled) {
            return WebSearchContext(query = normalizedQuery, status = "disabled")
        }

        val cappedLimit = limit.coerceIn(1, MaxResultLimit)
        val cacheKey = "${normalizedQuery.lowercase()}::$cappedLimit"
        val now = System.currentTimeMillis()
        cache[cacheKey]
            ?.takeIf { cached -> now - cached.createdAtMillis <= config.cacheTtlSeconds * 1000L }
            ?.let { cached ->
                return cached.context.copy(cached = true)
            }

        val failures = mutableListOf<String>()
        val providers = buildList {
            add("jina")
            add("duckduckgo_lite")
            if (!config.exaApiKey.isNullOrBlank()) add("exa")
            if (!config.tavilyApiKey.isNullOrBlank()) add("tavily")
        }

        providers.forEach { provider ->
            logger.info("Web search started: provider={}, query='{}'", provider, normalizedQuery.take(160))
            val context = runCatching {
                when (provider) {
                    "jina" -> searchJina(normalizedQuery, cappedLimit)
                    "duckduckgo_lite" -> searchDuckDuckGoLite(normalizedQuery, cappedLimit)
                    "exa" -> searchExa(normalizedQuery, cappedLimit)
                    "tavily" -> searchTavily(normalizedQuery, cappedLimit)
                    else -> WebSearchContext(query = normalizedQuery, status = "skipped")
                }
            }.onFailure { error ->
                val message = error.compactWebSearchError()
                failures += "$provider: $message"
                logger.warn("Web search provider failed: provider={}, error={}", provider, message)
            }.getOrNull()

            if (context != null && context.results.isNotEmpty()) {
                logger.info(
                    "Web search completed: provider={}, status={}, results={}, query='{}'",
                    context.provider,
                    context.status,
                    context.results.size,
                    normalizedQuery.take(160),
                )
                cache[cacheKey] = CachedWebSearch(
                    context = context.copy(cached = false),
                    createdAtMillis = now,
                )
                return context
            }
        }

        val warning = failures.joinToString(separator = " | ").take(MaxWarningChars)
        return WebSearchContext(
            query = normalizedQuery,
            provider = providers.joinToString(","),
            status = if (failures.isEmpty()) "no_results" else "failed",
            warning = warning,
        )
    }

    private fun searchJina(query: String, limit: Int): WebSearchContext {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://s.jina.ai/?q=${query.urlEncoded()}"))
            .timeout(Duration.ofMillis(minOf(config.timeoutMs, JinaSearchTimeoutMillis)))
            .header("Accept", "application/json")
            .apply {
                if (!config.jinaApiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer ${config.jinaApiKey}")
                }
            }
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} - ${response.body().take(MaxErrorBodyChars)}")
        }
        return WebSearchContext(
            query = query,
            provider = "jina",
            status = "succeeded",
            results = parseSearchResults(response.body(), provider = "jina", limit = limit),
        )
    }

    private fun searchDuckDuckGoLite(query: String, limit: Int): WebSearchContext {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://lite.duckduckgo.com/lite/?q=${query.urlEncoded()}"))
            .timeout(Duration.ofMillis(minOf(config.timeoutMs, DuckDuckGoSearchTimeoutMillis)))
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "CYLBot/1.0 (+https://changeyourlife.app)")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} - ${response.body().take(MaxErrorBodyChars)}")
        }
        return WebSearchContext(
            query = query,
            provider = "duckduckgo_lite",
            status = "succeeded",
            results = parseDuckDuckGoLiteResults(response.body(), limit),
        )
    }

    private fun searchExa(query: String, limit: Int): WebSearchContext {
        val body = buildJsonObject {
            put("query", query)
            put("numResults", limit)
            put(
                "contents",
                buildJsonObject {
                    put("text", true)
                    put("highlights", true)
                },
            )
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.exa.ai/search"))
            .timeout(Duration.ofMillis(config.timeoutMs))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("x-api-key", config.exaApiKey.orEmpty())
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(body)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} - ${response.body().take(MaxErrorBodyChars)}")
        }
        return WebSearchContext(
            query = query,
            provider = "exa",
            status = "succeeded",
            results = parseSearchResults(response.body(), provider = "exa", limit = limit),
        )
    }

    private fun searchTavily(query: String, limit: Int): WebSearchContext {
        val body = buildJsonObject {
            put("api_key", config.tavilyApiKey.orEmpty())
            put("query", query)
            put("max_results", limit)
            put("search_depth", "basic")
            put("include_answer", false)
            put("include_raw_content", false)
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.tavily.com/search"))
            .timeout(Duration.ofMillis(config.timeoutMs))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(body)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} - ${response.body().take(MaxErrorBodyChars)}")
        }
        return WebSearchContext(
            query = query,
            provider = "tavily",
            status = "succeeded",
            results = parseSearchResults(response.body(), provider = "tavily", limit = limit),
        )
    }

    private fun parseSearchResults(body: String, provider: String, limit: Int): List<WebSearchResult> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val candidates = when (root) {
            is JsonObject -> root.searchResultArrays()
            is JsonArray -> listOf(root)
            else -> emptyList()
        }
        val results = candidates
            .asSequence()
            .flatMap { array -> array.asSequence() }
            .mapNotNull { element -> element.jsonObjectOrNull() }
            .mapNotNull { item -> item.toWebSearchResult(provider) }
            .distinctBy { result -> result.url.ifBlank { result.title } }
            .take(limit)
            .toList()
        if (results.isNotEmpty()) return results

        val text = body.trim().take(MaxContentChars)
        return if (text.isBlank()) {
            emptyList()
        } else {
            listOf(
                WebSearchResult(
                    title = "Web result",
                    url = "",
                    snippet = text,
                    provider = provider,
                ),
            )
        }
    }

    private fun parseDuckDuckGoLiteResults(body: String, limit: Int): List<WebSearchResult> {
        val matches = DuckDuckGoResultLinkRegex.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexedNotNull { index, match ->
            val href = match.groupValues.getOrNull(1).orEmpty().htmlDecode()
            val title = match.groupValues.getOrNull(2).orEmpty()
                .stripHtml()
                .htmlDecode()
                .cleanWebField()
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: body.length
            val section = body.substring(match.range.last + 1, nextStart)
            val snippet = DuckDuckGoSnippetRegex.find(section)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .stripHtml()
                .htmlDecode()
                .cleanWebField()
            val url = href.resolveDuckDuckGoRedirectUrl()
            if (title.isBlank() && url.isBlank()) {
                null
            } else {
                WebSearchResult(
                    title = title.ifBlank { url.ifBlank { "Web result" } }.take(MaxTitleChars),
                    url = url.take(MaxUrlChars),
                    snippet = snippet.take(MaxSnippetChars),
                    provider = "duckduckgo_lite",
                )
            }
        }
            .distinctBy { result -> result.url.ifBlank { result.title } }
            .take(limit)
    }

    private fun JsonObject.searchResultArrays(): List<JsonArray> {
        val directKeys = listOf("data", "results", "organic_results", "items", "documents")
        val directArrays = directKeys.mapNotNull { key -> get(key)?.jsonArrayOrNull() }
        if (directArrays.isNotEmpty()) return directArrays
        return values.mapNotNull { value -> value.jsonArrayOrNull() }
    }

    private fun JsonObject.toWebSearchResult(provider: String): WebSearchResult? {
        val title = firstString("title", "name", "heading").cleanWebField()
        val url = firstString("url", "link", "href").cleanWebField()
        val highlights = get("highlights")
            ?.jsonArrayOrNull()
            ?.joinToString(separator = " ") { highlight -> highlight.stringOrBlank() }
            .orEmpty()
        val content = firstString("content", "text", "raw_content", "markdown", "body").cleanWebField()
        val snippet = firstString("description", "snippet", "summary", "extract")
            .ifBlank { highlights }
            .ifBlank { content }
            .cleanWebField()
        if (title.isBlank() && url.isBlank() && snippet.isBlank()) return null
        return WebSearchResult(
            title = title.ifBlank { url.ifBlank { "Web result" } }.take(MaxTitleChars),
            url = url.take(MaxUrlChars),
            snippet = snippet.take(MaxSnippetChars),
            content = content.take(MaxContentChars),
            provider = provider,
        )
    }

    private fun JsonObject.firstString(vararg keys: String): String {
        for (key in keys) {
            val value = get(key)?.stringOrBlank().orEmpty()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? =
        runCatching { jsonObject }.getOrNull()

    private fun JsonElement.jsonArrayOrNull(): JsonArray? =
        runCatching { jsonArray }.getOrNull()

    private fun JsonElement.stringOrBlank(): String =
        runCatching { jsonPrimitive.contentOrNull.orEmpty() }.getOrDefault("")

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private fun String.urlDecoded(): String =
        runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8) }.getOrDefault(this)

    private fun String.cleanWebField(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun String.stripHtml(): String =
        replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<[^>]+>"), " ")

    private fun String.htmlDecode(): String {
        val namedDecoded = replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        return HtmlNumericEntityRegex.replace(namedDecoded) { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                raw.drop(1).toIntOrNull(radix = 16)
            } else {
                raw.toIntOrNull()
            }
            codePoint
                ?.takeIf { value -> value > 0 }
                ?.let { value -> String(Character.toChars(value)) }
                ?.ifBlank { match.value }
                ?: match.value
        }
    }

    private fun String.resolveDuckDuckGoRedirectUrl(): String {
        val normalized = when {
            startsWith("//") -> "https:$this"
            startsWith("/") -> "https://duckduckgo.com$this"
            else -> this
        }
        val redirected = Regex("[?&]uddg=([^&]+)").find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.urlDecoded()
            .orEmpty()
        return redirected.ifBlank { normalized }
    }

    private fun String.normalizeWebSearchQuery(): String =
        replace(Regex("\\s+"), " ")
            .trim()
            .take(MaxQueryChars)

    private fun Throwable.compactWebSearchError(): String =
        (localizedMessage ?: message ?: this::class.simpleName.orEmpty())
            .replace(Regex("\\s+"), " ")
            .take(MaxErrorChars)

    private data class CachedWebSearch(
        val context: WebSearchContext,
        val createdAtMillis: Long,
    )

    private companion object {
        private const val DefaultResultLimit = 5
        private const val MaxResultLimit = 8
        private const val MaxQueryChars = 300
        private const val MaxTitleChars = 160
        private const val MaxUrlChars = 300
        private const val MaxSnippetChars = 700
        private const val MaxContentChars = 1_500
        private const val MaxWarningChars = 500
        private const val MaxErrorChars = 220
        private const val MaxErrorBodyChars = 500
        private const val JinaSearchTimeoutMillis = 5_000L
        private const val DuckDuckGoSearchTimeoutMillis = 8_000L
        private val DuckDuckGoResultLinkRegex =
            Regex("(?is)<a[^>]+class=[\"'][^\"']*result-link[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>")
        private val DuckDuckGoSnippetRegex =
            Regex("(?is)<(?:td|span|div)[^>]+class=[\"'][^\"']*(?:result-snippet|result__snippet)[^\"']*[\"'][^>]*>(.*?)</(?:td|span|div)>")
        private val HtmlNumericEntityRegex = Regex("&#(x?[0-9a-fA-F]+);")
    }
}

data class WebSearchContext(
    val query: String,
    val provider: String = "",
    val status: String = "",
    val results: List<WebSearchResult> = emptyList(),
    val warning: String = "",
    val cached: Boolean = false,
) {
    val isUsable: Boolean
        get() = results.isNotEmpty()

    fun toPromptContext(): String {
        if (results.isEmpty()) {
            return buildString {
                appendLine("CYL_WEB_CONTEXT:")
                appendLine("Search query: $query")
                appendLine("Status: ${status.ifBlank { "unavailable" }}")
                if (provider.isNotBlank()) appendLine("Provider: $provider")
                if (warning.isNotBlank()) appendLine("Warning: $warning")
                appendLine("No reliable web result is available. Do not invent current web facts; tell the user web source could not retrieve results if live facts are required.")
            }.trim()
        }
        return buildString {
            appendLine("CYL_WEB_CONTEXT:")
            appendLine("Search query: $query")
            appendLine("Provider: $provider${if (cached) " (cached)" else ""}")
            appendLine("Use these web results only when relevant. Cite URLs naturally when you use web facts.")
            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}")
                if (result.url.isNotBlank()) appendLine("   URL: ${result.url}")
                if (result.snippet.isNotBlank()) appendLine("   Snippet: ${result.snippet}")
                if (result.content.isNotBlank() && result.content != result.snippet) {
                    appendLine("   Content: ${result.content}")
                }
            }
        }.trim()
    }
}

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val content: String = "",
    val provider: String = "",
)
