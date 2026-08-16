package com.blurt.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.blurt.app.data.model.CaptureType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database backend — free on the Spark plan with **no
 * billing account required**. Captures live under `users/{uid}/captures`
 * (uid-scoped by security rules).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RtdbCaptureRemote(private val context: Context) : CaptureRemote {

    override val isConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private fun capturesRef(uid: String): DatabaseReference =
        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .child("captures")

    override suspend fun uploadCapture(uid: String, capture: RemoteCapture) {
        capturesRef(uid).child(capture.remoteId).setValue(
            mapOf(
                "content" to capture.content,
                "type" to capture.type.name,
                "category" to capture.category,
                "intent" to capture.intent,
                "reminderAt" to capture.reminderAt,
                "recurrence" to capture.recurrence,
                "isImportant" to capture.isImportant,
                "isArchived" to capture.isArchived,
                "completedAt" to capture.completedAt,
                "createdAt" to capture.createdAt,
                "updatedAt" to capture.updatedAt,
                "deleted" to false,
            )
        ).await()
    }

    override suspend fun deleteCapture(uid: String, remoteId: String) {
        // Tombstone, not removal: the node must stay visible to the user's
        // other devices so they can apply the deletion.
        capturesRef(uid).child(remoteId).updateChildren(mapOf("deleted" to true)).await()
    }

    override fun observeAll(uid: String): Flow<List<RemoteCapture>> = callbackFlow {
        val listener = capturesRef(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val docs = snapshot.children.mapNotNull { child ->
                    val data = child.value as? Map<*, *> ?: return@mapNotNull null
                    val typeName = data["type"] as? String ?: return@mapNotNull null
                    RemoteCapture(
                        remoteId = child.key ?: return@mapNotNull null,
                        content = data["content"] as? String ?: return@mapNotNull null,
                        // Unknown/legacy types (the removed IMAGE) degrade to TEXT.
                        type = runCatching { CaptureType.valueOf(typeName) }
                            .getOrDefault(CaptureType.TEXT),
                        category = data["category"] as? String,
                        intent = data["intent"] as? String,
                        reminderAt = (data["reminderAt"] as? Long)
                            ?: (data["reminderAt"] as? Number)?.toLong(),
                        recurrence = data["recurrence"] as? String,
                        isImportant = (data["isImportant"] as? Boolean) ?: false,
                        isArchived = (data["isArchived"] as? Boolean) ?: false,
                        completedAt = (data["completedAt"] as? Long)
                            ?: (data["completedAt"] as? Number)?.toLong(),
                        createdAt = (data["createdAt"] as? Long) ?: 0L,
                        updatedAt = (data["updatedAt"] as? Long) ?: 0L,
                        deleted = (data["deleted"] as? Boolean) ?: false,
                    )
                }
                trySend(docs)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { capturesRef(uid).removeEventListener(listener) }
    }
}
