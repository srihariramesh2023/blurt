package com.blurt.app.auth

/**
 * The basic profile of a signed-in user. Blurt deliberately keeps this
 * minimal — just what the product needs to personalize and scope data.
 */
data class AuthUser(
    /** Firebase Auth UID — the stable, unique identifier for this user. */
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)
