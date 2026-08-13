package com.blurt.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
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
     * AI-assigned topic from the fixed CaptureCategory list, stored as the
     * enum name; null until the analyzer has classified this blurt.
     */
    val category: String? = null,
    /**
     * AI-assigned intent (note/task/idea/reminder) from the fixed
     * CaptureIntent list, stored as the enum name; null until classified.
     */
    val intent: String? = null,
    /** When a priority Blurt notification was scheduled; null otherwise. */
    val reminderAt: Long? = null,
    /** User/AI-marked important blurt — gold star in the lists. */
    val isImportant: Boolean = false,
    /** Hidden from the main lists; browsable in Library → Archived. */
    val isArchived: Boolean = false,
    /**
     * Set when the user marks a reminder blurt done (from the notification
     * shade or the detail screen). Cancels the alarm and hides it from the
     * Reminders collection; null until then.
     */
    val completedAt: Long? = null,
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
    // null stays null (not yet classified); an unknown stored name degrades to OTHER.
    category = category?.let(CaptureCategory::fromName),
    // null stays null; an unknown stored name is ignored (not classified).
    intent = CaptureIntent.fromName(intent),
    reminderAt = reminderAt?.let(Instant::ofEpochMilli),
    isImportant = isImportant,
    isArchived = isArchived,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Capture.toEntity(): CaptureEntity = CaptureEntity(
    id = id,
    ownerId = ownerId,
    remoteId = remoteId,
    content = content,
    type = type,
    category = category?.name,
    intent = intent?.name,
    reminderAt = reminderAt?.toEpochMilli(),
    isImportant = isImportant,
    isArchived = isArchived,
    completedAt = completedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
