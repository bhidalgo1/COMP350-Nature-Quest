package com.example.natureobservation.feature.posts

import com.google.firebase.Timestamp

/** A published observation or map pin observation. */
data class ObservationPost(
    val id: String,
    val authorId: String,
    val title: String,
    val description: String,
    val storagePath: String = "",
    val createdAt: Timestamp? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isMapPin: Boolean = false
)
