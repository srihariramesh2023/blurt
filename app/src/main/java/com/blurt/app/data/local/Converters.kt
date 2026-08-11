package com.blurt.app.data.local

import androidx.room.TypeConverter
import com.blurt.app.data.model.CaptureType

class CaptureTypeConverter {
    @TypeConverter
    fun fromType(type: CaptureType): String = type.name

    @TypeConverter
    fun toType(name: String): CaptureType = CaptureType.valueOf(name)
}

class SyncStateConverter {
    @TypeConverter
    fun fromState(state: SyncState): String = state.name

    @TypeConverter
    fun toState(name: String): SyncState = SyncState.valueOf(name)
}
