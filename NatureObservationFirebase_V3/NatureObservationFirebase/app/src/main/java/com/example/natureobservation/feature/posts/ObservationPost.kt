package com.example.natureobservation.feature.posts

import com.google.firebase.Timestamp

/** A published observation. The picture is stored in Firebase Storage. */
data class ObservationPost(
    val id: String,
    val authorId: String,
    val title: String,
    val description: String,
    val storagePath: String,
    val createdAt: Timestamp?
)
