package maestro.ai

import maestro.ai.cloud.AIResponse
import maestro.ai.cloud.Defect
import maestro.ai.cloud.ExtractPointValidationResponse
import maestro.ai.cloud.ExtractPointWithReasoningResponse
import maestro.ai.cloud.ExtractTextWithAiResponse
import maestro.ai.cloud.FindDefectsResponse

class CloudAIPredictionEngine() : AIPredictionEngine {
    override suspend fun findDefects(screen: ByteArray, aiClient: AI): AIResponse<FindDefectsResponse> {
        return Prediction.findDefects(aiClient, screen)
    }

    override suspend fun performAssertion(screen: ByteArray, aiClient: AI, assertion: String): AIResponse<FindDefectsResponse> {
        return Prediction.performAssertion(aiClient, screen, assertion)
    }

    override suspend fun extractText(screen: ByteArray, aiClient: AI, query: String): AIResponse<ExtractTextWithAiResponse> {
        return Prediction.extractText(aiClient, query, screen)
    }

    @Suppress("DEPRECATION")
    override suspend fun extractPoint(screen: ByteArray, aiClient: AI, query: String): String {
        return Prediction.extractPoint(aiClient, query, screen)
    }

    override suspend fun extractPointWithReasoning(screen: ByteArray, aiClient: AI, query: String, viewHierarchy: String?): AIResponse<ExtractPointWithReasoningResponse>? {
        return Prediction.extractPointWithReasoning(aiClient, query, screen, viewHierarchy)
    }

    override suspend fun extractPointRefined(croppedScreen: ByteArray, aiClient: AI, query: String, contextDescription: String): AIResponse<ExtractPointWithReasoningResponse>? {
        return Prediction.extractPointRefined(aiClient, query, croppedScreen, contextDescription)
    }

    override suspend fun validatePoint(screen: ByteArray, aiClient: AI, query: String, pointXPercent: Int, pointYPercent: Int): AIResponse<ExtractPointValidationResponse>? {
        return Prediction.validatePoint(aiClient, query, screen, pointXPercent, pointYPercent)
    }

    override suspend fun extractComponentPoint(componentImage: ByteArray, screen: ByteArray, aiClient: AI, viewHierarchy: String?): AIResponse<ExtractPointWithReasoningResponse>? {
        return Prediction.extractComponentPoint(aiClient, componentImage, screen, viewHierarchy)
    }
}
