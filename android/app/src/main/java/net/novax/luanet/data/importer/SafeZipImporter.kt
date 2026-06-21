package net.novax.luanet.data.importer

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class ImportKind(val markers: Set<String>) {
    WORLD(setOf("world.mt")),
    GAME(setOf("game.conf")),
    MOD(setOf("mod.conf", "init.lua")),
    MODPACK(setOf("modpack.conf")),
}

data class ImportResult(val kind: ImportKind, val destination: File, val bytesWritten: Long)

class UnsafeArchiveException(message: String) : IOException(message)

class SafeZipImporter(
    private val maxEntries: Int = 10_000,
    private val maxUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
    private val maxSingleFileBytes: Long = 512L * 1024 * 1024,
    private val maxCompressionRatio: Long = 200,
) {
    fun import(archive: File, destination: File, expected: ImportKind? = null): ImportResult {
        require(archive.isFile) { "Archive does not exist" }
        require(!destination.exists()) { "Destination already exists" }
        destination.parentFile?.mkdirs()
        val staging = File(destination.parentFile, ".import-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Unable to create staging directory" }
        try {
            var total = 0L
            ZipFile.builder().setFile(archive).get().use { zip ->
                val entries = zip.entries.asSequence().toList()
                if (entries.size > maxEntries) throw UnsafeArchiveException("Archive has too many entries")
                entries.forEach { entry ->
                    validateEntry(entry, staging)
                    val output = safeOutput(staging, entry.name)
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            output.outputStream().use { stream ->
                                val copied = input.copyTo(stream)
                                if (copied > maxSingleFileBytes) throw UnsafeArchiveException("File exceeds size limit: ${entry.name}")
                                total += copied
                                if (total > maxUncompressedBytes) throw UnsafeArchiveException("Archive exceeds total size limit")
                            }
                        }
                    }
                }
            }
            val contentRoot = contentRoot(staging)
            val kind = detectKind(contentRoot)
            if (expected != null && kind != expected) {
                throw UnsafeArchiveException("Expected ${expected.name.lowercase()} archive, found ${kind.name.lowercase()}")
            }
            move(contentRoot, destination)
            if (contentRoot != staging) staging.deleteRecursively()
            return ImportResult(kind, destination, total)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun validateEntry(entry: ZipArchiveEntry, staging: File) {
        val normalized = entry.name.replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/') || normalized.contains("../") || normalized == "..") {
            throw UnsafeArchiveException("Unsafe archive path: ${entry.name}")
        }
        if (entry.isUnixSymlink) throw UnsafeArchiveException("Symbolic links are not allowed: ${entry.name}")
        if (entry.size > maxSingleFileBytes) throw UnsafeArchiveException("File exceeds size limit: ${entry.name}")
        if (entry.compressedSize > 0 && entry.size > entry.compressedSize * maxCompressionRatio) {
            throw UnsafeArchiveException("Suspicious compression ratio: ${entry.name}")
        }
        safeOutput(staging, normalized)
    }

    private fun safeOutput(root: File, name: String): File {
        val output = File(root, name).canonicalFile
        val prefix = root.canonicalPath + File.separator
        if (output.path != root.canonicalPath && !output.path.startsWith(prefix)) {
            throw UnsafeArchiveException("Archive path escapes destination: $name")
        }
        return output
    }

    private fun contentRoot(staging: File): File {
        val children = staging.listFiles()?.filterNot { it.name == "__MACOSX" } ?: emptyList()
        return if (children.size == 1 && children.single().isDirectory) children.single() else staging
    }

    private fun detectKind(root: File): ImportKind {
        val names = root.listFiles()?.map { it.name }?.toSet().orEmpty()
        return ImportKind.entries.firstOrNull { kind -> kind.markers.any(names::contains) }
            ?: throw UnsafeArchiveException("Archive is not a Luanti world, game, mod, or modpack")
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }
}

