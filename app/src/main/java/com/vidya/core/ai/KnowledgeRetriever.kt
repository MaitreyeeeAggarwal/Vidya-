package com.vidya.core.ai

import com.vidya.core.ai.models.ChunkMetadata
import com.vidya.core.ai.models.KnowledgeChunk
import com.vidya.core.ai.models.SessionState
import com.vidya.core.database.KnowledgeEntity
import com.vidya.core.database.KnowledgeEntity_
import io.objectbox.Box
import io.objectbox.BoxStore

/**
 * Handles Step 3B: The "Expert" Retrieval (RAG) using ObjectBox HNSW Index.
 */
class KnowledgeRetriever(
    private val knowledgeBox: Box<KnowledgeEntity>,
    private val embeddingModel: EmbeddingModel // A mock/wrapper for BGE-M3
) {

    /**
     * Retrieves the top knowledge chunks matching the expanded query and session state.
     * Uses BGE-M3 (384-dimensional) embeddings internally via ObjectBox Vector Search.
     */
    fun retrieveKnowledge(
        expandedQuery: String,
        sessionState: SessionState
    ): List<KnowledgeChunk> {
        
        // Step 1: Embed the expandedQuery into a 384-dimensional vector
        val queryVector = embeddingModel.embed(expandedQuery)

        // Step 2: Partition / Filter by grade_category and subject using ObjectBox Box
        val query = knowledgeBox.query()
            .equal(KnowledgeEntity_.subject, sessionState.subject)
            .lessOrEqual(KnowledgeEntity_.gradeMin, sessionState.classGrade)
            .greaterOrEqual(KnowledgeEntity_.gradeMax, sessionState.classGrade)
            // Perform Nearest Neighbor Search on the HNSW index for the vector
            .nearestNeighbors(KnowledgeEntity_.vector, queryVector, 5) // Fetch top 5 nearest chunks
            .build()

        val results: List<KnowledgeEntity> = query.find()
        query.close()

        // Map the ObjectBox entities to our domain KnowledgeChunk
        return results.map { entity ->
            KnowledgeChunk(
                id = entity.id.toString(),
                text = entity.textContent,
                metadata = ChunkMetadata(
                    subject = entity.subject,
                    gradeMin = entity.gradeMin,
                    gradeMax = entity.gradeMax,
                    complexity = entity.complexity,
                    source = entity.source
                ),
                isExpert = entity.source.equals("Expert", ignoreCase = true)
            )
        }
    }
}

/**
 * Interface representing the Text Embedding Model (e.g., BGE-M3)
 */
interface EmbeddingModel {
    fun embed(text: String): FloatArray
}
