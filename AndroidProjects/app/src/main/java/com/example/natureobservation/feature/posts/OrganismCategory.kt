package com.example.natureobservation.feature.posts

val ORGANISM_CATEGORIES = listOf("Plant", "Mammal", "Bird", "Reptile", "Insect")

/** Stable query/navigation order, with duplicates removed and unknown categories rejected. */
fun normalizeOrganisms(values: List<String>): List<String> {
    require(values.all { it in ORGANISM_CATEGORIES }) { "Choose a supported category." }
    return ORGANISM_CATEGORIES.filter { it in values }
}
