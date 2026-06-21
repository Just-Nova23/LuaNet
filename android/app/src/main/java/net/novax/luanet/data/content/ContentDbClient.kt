package net.novax.luanet.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
) {
    val key: String get() = "$author/$name"
}

class ContentDbClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = HttpUrl.Builder().scheme("https").host("content.luanti.org").build(),
) {
    suspend fun search(type: String, query: String, engineVersion: String, game: String? = null): List<ContentPackage> {
        val all = packageQuery(type, query, null, game)
        val supported = packageQuery(type, query, engineVersion, game).mapTo(hashSetOf()) { it.key }
        return all.map { it.copy(compatible = it.key in supported) }
    }

    suspend fun hardDependencyCandidates(packageKey: String): List<String> {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/packages/$packageKey/dependencies/")
            .addQueryParameter("only_hard", "1")
            .build()
        val tree = json.parseToJsonElement(get(url))
        val candidates = linkedSetOf<String>()
        collectPackageKeys(tree, candidates)
        candidates.remove(packageKey)
        return candidates.toList()
    }

    fun downloadUrl(item: ContentPackage): HttpUrl {
        val builder = baseUrl.newBuilder().addPathSegments("packages/${item.author}/${item.name}/")
        if (item.release != null) builder.addPathSegments("releases/${item.release}/")
        return builder.addPathSegment("download").build()
    }

    suspend fun download(item: ContentPackage, destination: java.io.File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl(item)).header("User-Agent", "LuaNet/0.1 (+https://github.com/Just-Nova23/LuaNet)").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("ContentDB download failed: HTTP ${response.code}")
            val body = response.body ?: error("ContentDB returned an empty archive")
            destination.outputStream().use { body.byteStream().copyTo(it) }
        }
    }

    private suspend fun packageQuery(type: String, query: String, engineVersion: String?, game: String?): List<ContentPackage> {
        val url = baseUrl.newBuilder().addPathSegments("api/packages/")
            .addQueryParameter("type", type)
            .addQueryParameter("q", query)
            .addQueryParameter("fmt", "short")
            .addQueryParameter("limit", "100")
            .apply {
                if (engineVersion != null) addQueryParameter("engine_version", engineVersion)
                if (game != null) addQueryParameter("game", game)
            }.build()
        return json.decodeFromString(get(url))
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "LuaNet/0.1 (+https://github.com/Just-Nova23/LuaNet)").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("ContentDB request failed: HTTP ${response.code}")
            response.body?.string() ?: error("ContentDB returned an empty response")
        }
    }

    private fun collectPackageKeys(element: JsonElement, output: MutableSet<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key in setOf("package", "package_key", "key") && value is JsonPrimitive) {
                    val candidate = value.content
                    if (candidate.count { it == '/' } == 1) output += candidate
                }
                collectPackageKeys(value, output)
            }
            is kotlinx.serialization.json.JsonArray -> element.forEach { collectPackageKeys(it, output) }
            is JsonPrimitive -> if (element.isString && element.content.count { it == '/' } == 1) output += element.content
        }
    }
}

