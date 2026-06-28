package net.novax.luanet.data.content

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class ContentDbClientTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test fun incompatiblePackagesRemainVisible() = runTest {
        val body = """[{"author":"alice","name":"world","title":"World","type":"game","release":7}]"""
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("{}"))
        val results = client().search("game", "", "5.16.1")
        assertEquals(1, results.size)
        assertFalse(results.single().compatible)
    }

    @Test fun parsesOnlyMandatoryDependencyGroups() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"alice/mod":[
              {"is_optional":false,"name":"library","packages":["bob/library"]},
              {"is_optional":true,"name":"extras","packages":["bob/extras"]}
            ]}
        """.trimIndent()))
        assertEquals(listOf(ContentDependency("library", listOf("bob/library"))), client().hardDependencies("alice/mod"))
    }

    @Test fun modpackSearchUsesContentDbModChannel() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        client().search("modpack", "technic", "5.16.1")
        assertEquals("/api/packages/?type=mod&fmt=short&limit=100&sort=score&order=desc&q=technic", server.takeRequest().path)
        assertEquals("/api/packages/?type=mod&fmt=short&limit=100&sort=score&order=desc&q=technic&engine_version=5.16.1", server.takeRequest().path)
    }

    @Test fun homeLoadsFeaturedSectionsWithoutSearchText() = runTest {
        repeat(8) { server.enqueue(MockResponse().setBody("[]")) }
        val sections = client().home("5.16.1", "Luanti/minetest_game")
        val paths = (0 until 8).map { server.takeRequest().path.orEmpty() }

        assertTrue(sections.isEmpty())
        assertTrue(paths.any { it.contains("type=game") && it.contains("sort=score") })
        assertTrue(paths.any { it.contains("type=game") && it.contains("sort=last_release") })
        assertTrue(paths.any { it.contains("type=mod") && it.contains("sort=score") && it.contains("game=Luanti%2Fminetest_game") })
        assertTrue(paths.any { it.contains("type=mod") && it.contains("sort=last_release") && it.contains("game=Luanti%2Fminetest_game") })
        assertTrue(paths.any { it.contains("engine_version=5.16.1") })
    }

    @Test fun searchAddsVisibleRiskBadgesFromPackageDetails() = runTest {
        val body = """[{"author":"alice","name":"mod","title":"Mod","type":"mod","release":9}]"""
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody("""{"dev_state":"WIP","content_warnings":["gore"],"license":"MIT"}"""))
        val result = client().search("mod", "mod", "5.16.1").single()
        assertEquals(listOf("Mature", "WIP"), result.badges)
    }

    @Test fun downloadReportsProgress() = runTest {
        server.enqueue(MockResponse().setBody("0123456789"))
        val temp = Files.createTempFile("luanet-content", ".zip").toFile()
        val progress = mutableListOf<DownloadProgress>()
        try {
            client().download(
                ContentPackage(author = "alice", name = "mod", title = "Mod", type = "mod"),
                temp,
                progress::add,
            )
            assertEquals("0123456789", temp.readText())
            assertTrue(progress.isNotEmpty())
            assertEquals(10L, progress.last().bytesRead)
            assertEquals(10L, progress.last().totalBytes)
            assertEquals("/packages/alice/mod/download/", server.takeRequest().path)
        } finally {
            temp.delete()
        }
    }

    private fun client() = ContentDbClient(baseUrl = server.url("/"))
}
