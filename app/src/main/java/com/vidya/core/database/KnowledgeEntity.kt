package com.vidya.core.database

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

/**
 * Represents a chunk of knowledge in the ObjectBox Vector Database.
 * This stores the "Pre-baked" PhD and Textbook chunks.
 */
@Entity
data class KnowledgeEntity(
    @Id var id: Long = 0,
    var textContent: String = "",
    var subject: String = "",
    var gradeMin: Int = 0,
    var gradeMax: Int = 0,
    var complexity: Float = 0f,
    var source: String = "", // "Expert" or "Textbook"
    
    // HNSW index for BGE-M3 384-dimensional embeddings for fast vector search
    @HnswIndex(dimensions = 384)
    var vector: FloatArray? = null
)
