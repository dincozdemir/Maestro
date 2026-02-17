package maestro.ai

import maestro.ai.cloud.AIResponse
import maestro.ai.cloud.Defect
import maestro.ai.cloud.ExtractPointValidationResponse
import maestro.ai.cloud.ExtractPointWithReasoningResponse
import maestro.ai.cloud.ExtractTextWithAiResponse
import maestro.ai.cloud.FindDefectsResponse

interface AIPredictionEngine {
    suspend fun findDefects(screen: ByteArray, aiClient: AI): AIResponse<FindDefectsResponse>
    suspend fun performAssertion(screen: ByteArray, aiClient: AI, assertion: String): AIResponse<FindDefectsResponse>
    suspend fun extractText(screen: ByteArray, aiClient: AI, query: String): AIResponse<ExtractTextWithAiResponse>
    suspend fun extractPoint(screen: ByteArray, aiClient: AI, query: String): String
    suspend fun extractPointWithReasoning(screen: ByteArray, aiClient: AI, query: String, viewHierarchy: String? = null): AIResponse<ExtractPointWithReasoningResponse>?
    suspend fun extractPointRefined(croppedScreen: ByteArray, aiClient: AI, query: String, contextDescription: String): AIResponse<ExtractPointWithReasoningResponse>?
    suspend fun validatePoint(screen: ByteArray, aiClient: AI, query: String, pointXPercent: Int, pointYPercent: Int): AIResponse<ExtractPointValidationResponse>?
    suspend fun extractComponentPoint(componentImage: ByteArray, screen: ByteArray, aiClient: AI, viewHierarchy: String? = null): AIResponse<ExtractPointWithReasoningResponse>?
}
