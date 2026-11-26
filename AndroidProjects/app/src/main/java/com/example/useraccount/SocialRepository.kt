package com.example.useraccount

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SocialRepository(val me: String) {
    val db = FirebaseFirestore.getInstance()
    val people = db.collection("publicProfiles")
    fun edges(owner: String, kind: String) = db.collection("social").document(owner).collection(kind)
    fun incoming(kind: String) = db.collectionGroup(kind).whereEqualTo("target", me)
    fun edge(owner: String, kind: String, target: String) = edges(owner, kind).document(target)

    suspend fun follow(peer: String, enable: Boolean) {
        val ref = edge(me, "following", peer)
        if (enable) ref.set(mapOf("target" to peer, "source" to me)).await()
        else ref.delete().await()
    }

    suspend fun block(peer: String, enable: Boolean) {
        val ref = edge(me, "blocks", peer)
        if (!enable) {
            ref.delete().await()
            return
        }
        db.runBatch { batch ->
            batch.set(ref, mapOf("target" to peer, "source" to me))
            batch.delete(edge(me, "following", peer))
            batch.delete(edge(peer, "following", me))
        }.await()
    }

    val messages = db.collection("directMessages")
    suspend fun send(peer: String, text: String) {
        messages.add(mapOf("sender" to me, "recipient" to peer, "text" to text.trim(),
            "createdAt" to FieldValue.serverTimestamp())).await()
    }
}
