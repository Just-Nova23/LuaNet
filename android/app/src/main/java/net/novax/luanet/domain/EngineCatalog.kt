package net.novax.luanet.domain

data class EngineRelease(
    val version: String,
    val protocolMin: Int,
    val protocolMax: Int,
    val libraryName: String,
    val featureModule: String?,
)

object EngineCatalog {
    val releases = listOf(
        EngineRelease("5.0.1", 37, 37, "luanet_engine_5_0", "engine_5_0"),
        EngineRelease("5.1.1", 37, 38, "luanet_engine_5_1", "engine_5_1"),
        EngineRelease("5.2.0", 37, 39, "luanet_engine_5_2", "engine_5_2"),
        EngineRelease("5.3.0", 37, 39, "luanet_engine_5_3", "engine_5_3"),
        EngineRelease("5.4.1", 37, 39, "luanet_engine_5_4", "engine_5_4"),
        EngineRelease("5.5.1", 37, 40, "luanet_engine_5_5", "engine_5_5"),
        EngineRelease("5.6.1", 37, 41, "luanet_engine_5_6", "engine_5_6"),
        EngineRelease("5.7.0", 37, 42, "luanet_engine_5_7", "engine_5_7"),
        EngineRelease("5.8.0", 37, 43, "luanet_engine_5_8", "engine_5_8"),
        EngineRelease("5.9.1", 37, 45, "luanet_engine_5_9", "engine_5_9"),
        EngineRelease("5.10.0", 37, 46, "luanet_engine_5_10", "engine_5_10"),
        EngineRelease("5.11.0", 37, 47, "luanet_engine_5_11", "engine_5_11"),
        EngineRelease("5.12.0", 37, 48, "luanet_engine_5_12", "engine_5_12"),
        EngineRelease("5.13.0", 37, 49, "luanet_engine_5_13", "engine_5_13"),
        EngineRelease("5.14.0", 37, 50, "luanet_engine_5_14", "engine_5_14"),
        EngineRelease("5.15.2", 37, 51, "luanet_engine_5_15", "engine_5_15"),
        EngineRelease("5.16.1", 37, 52, "luanet_engine_5_16", null),
    )

    val latest: EngineRelease = releases.last()

    fun find(version: String): EngineRelease? = releases.firstOrNull { it.version == version }

    fun canUpgrade(from: String, to: String): Boolean {
        val fromIndex = releases.indexOfFirst { it.version == from }
        val toIndex = releases.indexOfFirst { it.version == to }
        return fromIndex >= 0 && toIndex >= fromIndex
    }
}

