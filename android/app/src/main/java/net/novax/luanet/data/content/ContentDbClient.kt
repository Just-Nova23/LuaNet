package net.novax.luanet.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ContentPackage(
    val author: String,
    val name: String,
    val title: String,
    val type: String,
    val release: Long? = null,
    @SerialName("short_description") val shortDescription: String = "",
    val thumbnail: String? = null,
    val compatible: Boolean = true,
    val badges: List<String> = emptyList(),
) {
    val key: String get() = "$author/$name"
}

@Serializable
data class ContentPackageDetail(
    val author: String = "",
    val name: String = "",
    val title: String = "",
    val type: String = "",
    val release: Long? = null,
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("long_description") val longDescription: String = "",
    val thumbnail: String? = null,
    val screenshots: List<String> = emptyList(),
    val downloads: Long? = null,
    val tags: List<String> = emptyList(),
    val repo: String? = null,
    val website: String? = null,
    @SerialName("forum_url") val forumUrl: String? = null,
    @SerialName("issue_tracker") val issueTracker: String? = null,
    val url: String? = null,
    val state: String? = null,
    @SerialName("dev_state") val devState: String? = null,
    @SerialName("content_warnings") val contentWarnings: List<String> = emptyList(),
    val license: String? = null,
    @SerialName("media_license") val mediaLicense: String? = null,
) {
    fun badges(): List<String> = buildList {
        if (contentWarnings.isNotEmpty()) add("Mature")
        when (devState) {
            "WIP" -> add("WIP")
            "DEPRECATED" -> add("Deprecated")
        }
        if (state != null && state != "APPROVED") add(state.lowercase().replaceFirstChar(Char::uppercase))
        if (listOfNotNull(license, mediaLicense).any(::looksNonFree)) add("Non-free")
    }.distinct()

    private fun looksNonFree(value: String): Boolean {
        val normalized = value.lowercase()
        return "nonfree" in normalized ||
            "non-free" in normalized ||
            "proprietary" in normalized ||
            "cc-by-nc" in normalized ||
            "cc-by-nd" in normalized
    }
}

data class ContentDependency(val name: String, val candidates: List<String>)
data class DownloadProgress(val bytesRead: Long, val totalBytes: Long?)
data class ContentHomeSection(val title: String, val subtitle: String, val items: List<ContentPackage>)

class ContentDbClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = HttpUrl.Builder().scheme("https").host("content.luanti.org").build(),
) {
    suspend fun search(type: String, query: String, engineVersion: String, game: String? = null): List<ContentPackage> {
        val apiType = apiType(type)
        return queryPackages(apiType, query, "score", "desc", 100, engineVersion, game)
    }

    suspend fun searchAll(query: String, engineVersion: String, game: String? = null): List<ContentHomeSection> = coroutineScope {
        val clean = query.trim()
        listOf(
            async {
                ContentHomeSection(
                    title = "Games",
                    subtitle = "",
                    items = queryPackages("game", clean, "score", "desc", SEARCH_SECTION_LIMIT, engineVersion, null),
                )
            },
            async {
                ContentHomeSection(
                    title = "Mods & modpacks",
                    subtitle = "",
                    items = queryPackages("mod", clean, "score", "desc", SEARCH_SECTION_LIMIT, engineVersion, game),
                )
            },
        ).awaitAll().filter { it.items.isNotEmpty() }
    }

    suspend fun home(engineVersion: String, game: String? = null): List<ContentHomeSection> = coroutineScope {
        listOf(
            async {
                ContentHomeSection(
                    title = "Top games",
                    subtitle = "Most trusted game packages on ContentDB",
                    items = queryPackages("game", "", "score", "desc", HOME_SECTION_LIMIT, engineVersion, null),
                )
            },
            async {
                ContentHomeSection(
                    title = "Recently updated games",
                    subtitle = "Fresh game releases and updates",
                    items = queryPackages("game", "", "last_release", "desc", HOME_SECTION_LIMIT, engineVersion, null),
                )
            },
            async {
                ContentHomeSection(
                    title = if (game == null) "Top mods" else "Top compatible mods",
                    subtitle = if (game == null) "Popular mods across ContentDB" else "Popular mods for the selected game",
                    items = queryPackages("mod", "", "score", "desc", HOME_SECTION_LIMIT, engineVersion, game),
                )
            },
            async {
                ContentHomeSection(
                    title = "Recently updated mods",
                    subtitle = "Latest mod and modpack releases",
                    items = queryPackages("mod", "", "last_release", "desc", HOME_SECTION_LIMIT, engineVersion, game),
                )
            },
        ).awaitAll().filter { it.items.isNotEmpty() }
    }

    suspend fun hardDependencies(packageKey: String): List<ContentDependency> {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/packages/$packageKey/dependencies/")
            .addQueryParameter("only_hard", "1")
            .build()
        val tree = json.parseToJsonElement(get(url))
        val root = tree as? JsonObject ?: return emptyList()
        val entries = root[packageKey] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return entries.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            if ((item["is_optional"] as? JsonPrimitive)?.content == "true") return@mapNotNull null
            val name = (item["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val packages = (item["packages"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.count { char -> char == '/' } == 1 }
            ContentDependency(name, packages)
        }
    }

    suspend fun packageByKey(packageKey: String): ContentPackage {
        require(packageKey.count { it == '/' } == 1) { "Invalid ContentDB package key" }
        val url = baseUrl.newBuilder().addPathSegments("api/packages/$packageKey/").build()
        return json.decodeFromString(get(url))
    }

    suspend fun details(packageKey: String): ContentPackageDetail {
        require(packageKey.count { it == '/' } == 1) { "Invalid ContentDB package key" }
        val url = baseUrl.newBuilder().addPathSegments("api/packages/$packageKey/").build()
        return json.decodeFromString(get(url))
    }

    fun downloadUrl(item: ContentPackage): HttpUrl {
        return baseUrl.newBuilder().addPathSegments("packages/${item.author}/${item.name}/download/").build()
    }

    suspend fun download(
        item: ContentPackage,
        destination: java.io.File,
        onProgress: (DownloadProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl(item)).header("User-Agent", "LuaNet/0.1 (+https://github.com/Just-Nova23/LuaNet)").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("ContentDB download failed: HTTP ${response.code}")
            val body = response.body ?: error("ContentDB returned an empty archive")
            val contentLength = body.contentLength().takeIf { it >= 0 }
            if (contentLength != null && contentLength > MAX_DOWNLOAD_BYTES) error("ContentDB archive exceeds 512 MB")
            onProgress(DownloadProgress(bytesRead = 0, totalBytes = contentLength))
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) error("ContentDB archive exceeds 512 MB")
                        output.write(buffer, 0, read)
                        onProgress(DownloadProgress(bytesRead = total, totalBytes = contentLength))
                    }
                }
            }
        }
    }

    private suspend fun queryPackages(
        type: String,
        query: String,
        sort: String,
        order: String,
        limit: Int,
        engineVersion: String?,
        game: String?,
    ): List<ContentPackage> {
        val all = packageQuery(type, query, null, game, sort, order, limit)
        val supported = packageQuery(type, query, engineVersion, game, sort, order, limit).mapTo(hashSetOf()) { it.key }
        val visible = all.map { it.copy(compatible = it.key in supported) }
        return enrichBadges(visible.take(MAX_BADGED_RESULTS)) + visible.drop(MAX_BADGED_RESULTS)
    }

    private suspend fun packageQuery(
        type: String,
        query: String,
        engineVersion: String?,
        game: String?,
        sort: String,
        order: String,
        limit: Int,
    ): List<ContentPackage> {
        val url = baseUrl.newBuilder().addPathSegments("api/packages/")
            .addQueryParameter("type", type)
            .addQueryParameter("fmt", "short")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("sort", sort)
            .addQueryParameter("order", order)
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query.trim())
                if (engineVersion != null) addQueryParameter("engine_version", engineVersion)
                if (game != null) addQueryParameter("game", game)
            }.build()
        return json.decodeFromString(get(url))
    }

    private fun apiType(type: String) = when (type) {
        "game", "mod", "txp" -> type
        // ContentDB exposes Luanti modpacks through the mod package channel.
        "modpack" -> "mod"
        else -> error("Unsupported ContentDB package type $type")
    }

    private suspend fun enrichBadges(items: List<ContentPackage>): List<ContentPackage> = coroutineScope {
        items.map { item ->
            async {
                runCatching { item.copy(badges = details(item.key).badges()) }.getOrDefault(item)
            }
        }.awaitAll()
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "LuaNet/0.1 (+https://github.com/Just-Nova23/LuaNet)").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("ContentDB request failed: HTTP ${response.code}")
            response.body?.string() ?: error("ContentDB returned an empty response")
        }
    }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
        private const val MAX_BADGED_RESULTS = 40
        private const val HOME_SECTION_LIMIT = 20
        private const val SEARCH_SECTION_LIMIT = 60
    }
}
