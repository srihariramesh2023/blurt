package com.blurt.app.data.local

import androidx.room.TypeConverter
import com.blurt.app.data.model.CaptureType

class CaptureTypeConverter {
    @TypeConverter
    fun fromType(type: CaptureType): String = type.name

    @TypeConverter
    fun toType(name: String): CaptureType =
        // Unknown/legacy types (the removed IMAGE) degrade to TEXT instead of
        // crashing the app when an old database is opened.
        runCatching { CaptureType.valueOf(name) }.getOrDefault(CaptureType.TEXT)
}

class SyncStateConverter {
    @TypeConverter
    fun fromState(state: SyncState): String = state.name

    @TypeConverter
    fun toState(name: String): SyncState = SyncState.valueOf(name)
}

class EmbeddingConverter {
    /** 3072 floats ≈ 12 KB per row — a compact binary blob is the right shape. */
    @TypeConverter
    fun fromFloats(values: FloatArray): ByteArray = ByteArray(values.size * 4).also { bytes ->
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        values.forEach(buf::putFloat)
    }

    @TypeConverter
    fun toFloats(bytes: ByteArray): FloatArray {
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buf.getFloat()
        return out
    }
}
