package maestro.ai

import maestro.ai.cloud.AIResponse
import maestro.ai.cloud.Defect
import maestro.ai.cloud.ExtractPointValidationResponse
import maestro.ai.cloud.ExtractPointWithReasoningResponse
import maestro.ai.cloud.ExtractTextWithAiResponse
import maestro.ai.cloud.FindDefectsResponse
import maestro.ai.cloud.OpenAIClient

object Prediction {
    private val openApi = OpenAIClient()

    suspend fun findDefects(
        aiClient: AI?,
        screen: ByteArray,
    ): AIResponse<FindDefectsResponse> {
        if(aiClient !== null){
            return openApi.findDefects(aiClient, screen)
        }
        return AIResponse(result = FindDefectsResponse(defects = listOf()), durationMs = 0, model = "none")
    }

    suspend fun performAssertion(
        aiClient: AI?,
        screen: ByteArray,
        assertion: String,
    ): AIResponse<FindDefectsResponse> {
        if(aiClient !== null){
            return openApi.findDefects(aiClient, screen, assertion)
        }
        return AIResponse(result = FindDefectsResponse(defects = listOf()), durationMs = 0, model = "none")
    }

    suspend fun extractText(
        aiClient: AI?,
        query: String,
        screen: ByteArray,
    ): AIResponse<ExtractTextWithAiResponse> {
        if(aiClient !== null){
            return openApi.extractTextWithAi(aiClient, query, screen)
        }
        return AIResponse(result = ExtractTextWithAiResponse(text = ""), durationMs = 0, model = "none")
    }

    @Deprecated("Use extractPointWithReasoning for improved accuracy", replaceWith = ReplaceWith("extractPointWithReasoning(aiClient, query, screen)"))
    suspend fun extractPoint(
        aiClient: AI?,
        query: String,
        screen: ByteArray,
    ): String {
        if(aiClient !== null){
            val response = openApi.extractPointWithAi(aiClient, query, screen)
            return response.result.text
        }
        return ""
    }

    suspend fun extractPointWithReasoning(
        aiClient: AI?,
        query: String,
        screen: ByteArray,
        viewHierarchy: String? = null,
    ): AIResponse<ExtractPointWithReasoningResponse>? {
        if(aiClient !== null){
            return openApi.extractPointWithAi(aiClient, query, screen, viewHierarchy)
        }
        return null
    }

    suspend fun extractPointRefined(
        aiClient: AI?,
        query: String,
        croppedScreen: ByteArray,
        contextDescription: String,
    ): AIResponse<ExtractPointWithReasoningResponse>? {
        if(aiClient !== null){
            return openApi.extractPointRefined(aiClient, query, croppedScreen, contextDescription)
        }
        return null
    }

    suspend fun extractComponentPoint(
        aiClient: AI?,
        componentImage: ByteArray,
        screen: ByteArray,
        viewHierarchy: String? = null,
    ): AIResponse<ExtractPointWithReasoningResponse>? {
        if(aiClient !== null){
            return openApi.extractComponentPoint(aiClient, componentImage, screen, viewHierarchy)
        }
        return null
    }

    suspend fun validatePoint(
        aiClient: AI?,
        query: String,
        screen: ByteArray,
        pointXPercent: Int,
        pointYPercent: Int,
    ): AIResponse<ExtractPointValidationResponse>? {
        if(aiClient !== null){
            return openApi.validatePoint(aiClient, query, screen, pointXPercent, pointYPercent)
        }
        return null
    }
}
