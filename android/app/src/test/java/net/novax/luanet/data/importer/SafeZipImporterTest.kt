package net.novax.luanet.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeZipImporterTest {
    @Test fun importsWorldInsideTopLevelDirectory() {
        val root = Files.createTempDirectory("luanet-import").toFile()
        try {
            val archive = File(root, "world.zip")
            zip(archive, mapOf("my-world/world.mt" to "gameid = minetest", "my-world/map.sqlite" to "data"))
            val destination = File(root, "world")
            val result = SafeZipImporter().import(archive, destination, ImportKind.WORLD)
            assertEquals(ImportKind.WORLD, result.kind)
            assertTrue(File(destination, "world.mt").isFile)
        } finally { root.deleteRecursively() }
    }

    @Test(expected = UnsafeArchiveException::class)
    fun rejectsPathTraversal() {
        val root = Files.createTempDirectory("luanet-import").toFile()
        try {
            val archive = File(root, "bad.zip")
            zip(archive, mapOf("../world.mt" to "bad"))
            SafeZipImporter().import(archive, File(root, "world"))
        } finally { root.deleteRecursively() }
    }

    @Test(expected = UnsafeArchiveException::class)
    fun rejectsWrongContentType() {
        val root = Files.createTempDirectory("luanet-import").toFile()
        try {
            val archive = File(root, "mod.zip")
            zip(archive, mapOf("mod.conf" to "name = example", "init.lua" to ""))
            SafeZipImporter().import(archive, File(root, "world"), ImportKind.WORLD)
        } finally { root.deleteRecursively() }
    }

    private fun zip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
    }
}

