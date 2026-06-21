package net.novax.luanet.data.db

import androidx.room.TypeConverter
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.PackageSource
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState

class Converters {
    @TypeConverter fun serverState(value: ServerState) = value.name
    @TypeConverter fun serverState(value: String) = ServerState.valueOf(value)
    @TypeConverter fun accessMode(value: AccessMode) = value.name
    @TypeConverter fun accessMode(value: String) = AccessMode.valueOf(value)
    @TypeConverter fun packageType(value: PackageType) = value.name
    @TypeConverter fun packageType(value: String) = PackageType.valueOf(value)
    @TypeConverter fun packageSource(value: PackageSource) = value.name
    @TypeConverter fun packageSource(value: String) = PackageSource.valueOf(value)
}

