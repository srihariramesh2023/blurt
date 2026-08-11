package com.blurt.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureType
import java.time.Instant

/** Upload state of a capture relative to the backend. */
enum class SyncState {
    /** Needs to be pushed (or re-pushed) to the backend. */
    PENDING,

    /** The backend has the latest version of this capture. */
    SYNCED,
}

@Entity(
    tableName = "captures",
    indices = [Index("ownerId"), Index("syncState")],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The Firebase Auth UID this capture belongs to; null = legacy pre-auth row. */
    val ownerId: String? = null,
    /** Backend document id (UUID generated on-device so devices never collide). */
    val remoteId: String? = null,
    /** Upload state — PENDING rows are pushed to the backend by the sync engine. */
    val syncState: SyncState = SyncState.PENDING,
    val content: String,
    val type: CaptureType,
    /**
     * Set when the user deletes; the row is removed locally only after the
     * backend delete is confirmed, so deletes propagate across devices.
     */
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

fun CaptureEntity.toDomain(): Capture = Capture(
    id = id,
    ownerId = ownerId,
    remoteId = remoteId,
    content = content,
    type = type,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Capture.toEntity(): CaptureEntity = CaptureEntity(
    id = id,
    ownerId = ownerId,
    remoteId = remoteId,
    content = content,
    type = type,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
